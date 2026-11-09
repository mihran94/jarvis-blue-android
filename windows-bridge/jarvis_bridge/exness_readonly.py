"""Exness Trader API account integration (READ-ONLY; NO TRADE ROUTES).

The private Ed25519 signing key stays on the trusted Windows backend.
References: https://www.exness-api.com/trading/guides/code-examples
           https://www.exness-api.com/documentation/authentication

EXN_API_KEY: key ID. EXN_PRIVATE_KEY: 128-char hex / PKCS#8 PEM.
EXN_ACCOUNT_ID: numeric account. EXN_DISCOVERY_BASE_URL: from Exness DNS widget.
Requires: cryptography. Never put secrets in APKs, Git or logs.
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import time
import urllib.request
import urllib.error
from dataclasses import dataclass
from urllib.parse import urlsplit
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

MAX_BYTES = 1024 * 1024


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode('ascii').rstrip('=')


def validated_base_url(value: str) -> str:
    """No credentials, query, fragments, embedded paths, IP literals or insecure schemes."""
    url = urlsplit(value)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path not in ('', '/') or url.query or url.fragment or url.port not in (None, 443)):
        raise ValueError('Exness host must be a clean HTTPS base URL from its DNS widget')
    host = url.hostname.lower().rstrip('.')
    if not (host.endswith('.exness.com') or host == 'exness.com'
            or host.endswith('.exness-api.com') or host == 'exness-api.com'):
        raise ValueError('Exness gateway or Access Point host not in approved Exness domains')
    return 'https://' + host


def validate_account_id(value: str) -> str:
    if not re.fullmatch(r'[1-9][0-9]*', value) or int(value) > (2**64 - 1):
        raise ValueError('Expected a valid positive Exness trading account ID')
    return value


def signing_key(secret: str) -> Ed25519PrivateKey:
    secret=secret.strip()
    if secret.startswith('-----BEGIN PRIVATE KEY-----'):
        key=serialization.load_pem_private_key(secret.encode(), password=None)
        if not isinstance(key, Ed25519PrivateKey): raise ValueError('Expected Ed25519 private key')
        return key
    if not re.fullmatch(r'[0-9a-fA-F]{128}', secret):
        raise ValueError('Expected Exness-generated 128-digit Ed25519 private key or PKCS#8 PEM')
    raw=bytes.fromhex(secret)
    key=Ed25519PrivateKey.from_private_bytes(raw[:32])
    pub=key.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw)
    if pub != raw[32:]: raise ValueError('Private key public component mismatch')
    return key


def signed_get_headers(api_key: str, key: Ed25519PrivateKey, path: str, timestamp_ms: int | None=None) -> dict[str,str]:
    if not path.startswith('/v1/') or '://' in path or '#' in path:
        raise ValueError('Only known Exness API v1 paths are allowed')
    timestamp = timestamp_ms if timestamp_ms is not None else int(time.time()*1000)
    payload=json.dumps({
        'api_key':api_key, 'idempotency_key':'', 'timestamp':timestamp,
        'sign_version':1, 'method':'GET', 'path':path,
        'body_hash':b64url(hashlib.sha256(b'').digest()),
    },separators=(',',':'),ensure_ascii=False).encode()
    return {'EXN-API-KEY':api_key,'EXN-IDEMPOTENCY-KEY':'',
            'EXN-TIMESTAMP':str(timestamp),'EXN-SIGN-VERSION':'1',
            'EXN-DATA':b64url(payload),'EXN-SIGN':b64url(key.sign(payload))}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): return None


@dataclass(frozen=True)
class ExnessConfig:
    key_id: str
    private_key: Ed25519PrivateKey
    account_id: str
    discovery_host: str

    @classmethod
    def from_env(cls) -> 'ExnessConfig':
        names=['EXN_API_KEY','EXN_PRIVATE_KEY','EXN_ACCOUNT_ID','EXN_DISCOVERY_BASE_URL']
        missing=[n for n in names if not os.environ.get(n,'').strip()]
        if missing: raise ValueError('Broker not configured: missing '+', '.join(missing))
        return cls(
            key_id=os.environ['EXN_API_KEY'].strip(),
            private_key=signing_key(os.environ['EXN_PRIVATE_KEY']),
            account_id=validate_account_id(os.environ['EXN_ACCOUNT_ID'].strip()),
            discovery_host=validated_base_url(os.environ['EXN_DISCOVERY_BASE_URL'].strip()),
        )

class ExnessReadOnly:
    def __init__(self, cfg:ExnessConfig): self.cfg=cfg

    def _get(self, host: str, path: str) -> dict:
        request=urllib.request.Request(
            validated_base_url(host)+path,
            headers=signed_get_headers(self.cfg.key_id,self.cfg.private_key,path),
            method='GET',
        )
        opener=urllib.request.build_opener(NoRedirect())
        try:
            with opener.open(request,timeout=15) as resp:
                if resp.status != 200: raise ValueError('Broker HTTP '+str(resp.status))
                size=int(resp.headers.get('Content-Length','0') or '0')
                if size > MAX_BYTES: raise ValueError('Broker response too large')
                raw=resp.read(MAX_BYTES+1)
                if len(raw) > MAX_BYTES: raise ValueError('Broker response too large')
                result=json.loads(raw)
                if not isinstance(result,dict): raise ValueError('Invalid broker response')
                return result
        except urllib.error.HTTPError as exc:
            # Never include full response bodies or signed headers in error logs.
            raise ValueError(f'Broker request returned HTTP {exc.code}') from None
        except urllib.error.URLError:
            raise ValueError('Broker connection unavailable') from None

    def access_point(self)->str:
        # Discovery must occur before requesting account-scoped resources.
        data=self._get(self.cfg.discovery_host,f'/v1/trading/access-point?account_id={self.cfg.account_id}')
        access=data.get('access_point')
        if not isinstance(access,str): raise ValueError('Broker discovery did not return an access point')
        return validated_base_url(access)

    def account(self)->dict:
        host=self.access_point()
        return self._get(host,f'/v1/configuration/accounts/{self.cfg.account_id}/account')

    def limits(self)->dict:
        host=self.access_point()
        return self._get(host,f'/v1/configuration/accounts/{self.cfg.account_id}/limits')


def sanitized_summary(payload:dict, account_id: str)->dict:
    """Allowlist only public-facing account metrics. Never return raw API response."""
    # Exness schemas can evolve: take only numeric values in known nested containers.
    containers = [payload] + [payload[k] for k in ('account','trading_account','data') if isinstance(payload.get(k),dict)]
    safe={'broker':'Exness','connection':'read_only','account_suffix':account_id[-4:],
          'as_of_unix_ms':int(time.time()*1000),'metrics':{}}
    for attr in ('balance','equity','margin','free_margin','margin_level','leverage'):
        for obj in containers:
            x=obj.get(attr)
            if isinstance(x,(int,float,str)) and not isinstance(x,bool):
                try:
                    val=float(x)
                    if val==val and abs(val)<1e18: safe['metrics'][attr]=val
                except (ValueError,OverflowError): pass
                if attr in safe['metrics']:break
    for obj in containers:
        c=obj.get('currency')
        if isinstance(c,str) and re.fullmatch(r'[A-Z]{3}',c): safe['currency']=c;break
    return safe


def run_check() -> dict:
    cfg=ExnessConfig.from_env()
    return sanitized_summary(ExnessReadOnly(cfg).account(),cfg.account_id)


if __name__=='__main__':
    # Never print API key or access point, or the raw account response.
    try: print(json.dumps(run_check(),indent=2))
    except Exception as exc:
        print('Exness read-only check failed:',str(exc))
        raise SystemExit(1)
