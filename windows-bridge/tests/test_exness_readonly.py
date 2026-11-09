import base64
import json
import unittest
from unittest.mock import patch
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives import serialization
from jarvis_bridge.exness_readonly import (
    b64url, validated_base_url,validate_account_id,signing_key,
    signed_get_headers,sanitized_summary, ExnessConfig,ExnessReadOnly,
)

class ExnessSafetyTests(unittest.TestCase):
    def setUp(self):
        self.key=Ed25519PrivateKey.generate()
        seed=self.key.private_bytes(serialization.Encoding.Raw, serialization.PrivateFormat.Raw, serialization.NoEncryption())
        pub=self.key.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        self.hex=(seed+pub).hex()

    def test_key_hex_roundtrip(self):
        secret=signing_key(self.hex)
        self.assertEqual(secret.public_key().public_bytes(serialization.Encoding.Raw,serialization.PublicFormat.Raw),bytes.fromhex(self.hex[64:]))
    def test_reject_tampered_secret(self):
        with self.assertRaises(ValueError): signing_key(self.hex[:64]+'00'*32)
    def test_correct_signed_get(self):
        headers=signed_get_headers('exnsk-test', self.key,'/v1/trading/access-point?account_id=123',timestamp_ms=1700000000000)
        raw=base64.urlsafe_b64decode(headers['EXN-DATA']+'==')
        self.key.public_key().verify(base64.urlsafe_b64decode(headers['EXN-SIGN']+'=='),raw)
        data=json.loads(raw)
        self.assertEqual(data['path'],'/v1/trading/access-point?account_id=123')
        self.assertEqual(data['idempotency_key'],'')
        self.assertEqual(data['body_hash'],b64url(__import__('hashlib').sha256(b'').digest()))
    def test_validate_hosts(self):
        self.assertEqual(validated_base_url('https://api.exness.com'),'https://api.exness.com')
        for x in ('http://api.exness.com','https://api.exness.com.evil.tld','https://127.0.0.1','https://api.exness.com/path','https://user:pass@api.exness.com'):
            with self.assertRaises(ValueError):validated_base_url(x)
    def test_validate_account(self):
        self.assertEqual(validate_account_id('123456'),'123456')
        for x in ('0','-2','a','1?foo','999999999999999999999999999'):
            with self.assertRaises(ValueError):validate_account_id(x)
    def test_account_discovery_then_get(self):
        cfg=ExnessConfig('exnsk-test',self.key,'1234','https://gateway.exness.com')
        ex=ExnessReadOnly(cfg)
        calls=[]
        def mock(host,path):
            calls.append((host,path))
            return {'access_point':'https://ap-abc.exness.com'} if len(calls)==1 else {'balance':'1000'}
        ex._get=mock
        self.assertEqual(ex.account(),{'balance':'1000'})
        self.assertEqual(calls,[('https://gateway.exness.com','/v1/trading/access-point?account_id=1234'),('https://ap-abc.exness.com','/v1/configuration/accounts/1234/account')])
    def test_account_summary_never_leaks_secret(self):
        obj=sanitized_summary({'balance':1234.0,'account_id':'123456','private_key':'SECRET','other':'SENSITIVE'},'123456')
        self.assertEqual(obj['metrics']['balance'],1234.0)
        self.assertEqual(obj['account_suffix'],'3456')
        self.assertNotIn('SECRET',json.dumps(obj))
        self.assertNotIn('123456',json.dumps(obj))
    def test_missing_keys_explicit(self):
        with patch.dict('os.environ',{},clear=True):
            with self.assertRaises(ValueError):ExnessConfig.from_env()

if __name__=='__main__':unittest.main()
