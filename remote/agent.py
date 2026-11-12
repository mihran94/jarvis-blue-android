"""JARVIS BLUE private remote agent. Python 3.10+, standard library only.

Run on Windows, then expose privately with Tailscale Serve (not Funnel).
Never expose this HTTP service directly to the public Internet.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import hmac
import json
import os
import secrets
import subprocess
import sys
import threading
import time
import urllib.parse
import webbrowser

ROOT = Path(__file__).resolve().parent.parent / "pwa"
HOST = "127.0.0.1"
PORT = int(os.environ.get("JARVIS_PORT", "8765"))
TOKEN = os.environ.get("JARVIS_REMOTE_TOKEN", "")
MAX_BODY = 4096
LOCK = threading.Lock()
LAST_REQUEST = {}

def perform(command):
    if command == "status":
        return "Windows agent online. Computer: " + os.environ.get("COMPUTERNAME", "Windows")
    if command == "chrome":
        webbrowser.open("https://www.google.com")
        return "Opened browser"
    if command == "downloads":
        path = Path.home() / "Downloads"
        if not path.is_dir():
            raise RuntimeError("Downloads folder not found")
        os.startfile(str(path))
        return "Opened Downloads"
    if command == "after-effects":
        paths = []
        for base in (os.environ.get("ProgramFiles"), os.environ.get("ProgramFiles(x86)")):
            if base:
                paths.extend(Path(base).glob("Adobe/Adobe After Effects */Support Files/AfterFX.exe"))
        if not paths:
            raise RuntimeError("After Effects not found")
        subprocess.Popen([str(sorted(paths)[-1])], shell=False)
        return "Started After Effects"
    raise ValueError("Command not permitted")

class Handler(BaseHTTPRequestHandler):
    def _reply(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; connect-src 'self' https://generativelanguage.googleapis.com; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urllib.parse.urlsplit(self.path).path
        if path not in ("/", "/index.html", "/manifest.webmanifest", "/remote.html"):
            return self._reply(404, {"error": "Not found"})
        filename = "index.html" if path == "/" else path.lstrip("/")
        file = ROOT / filename
        if not file.is_file():
            return self._reply(404, {"error": "File missing"})
        data = file.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "application/manifest+json" if filename.endswith(".webmanifest") else "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; connect-src 'self' https://generativelanguage.googleapis.com; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path != "/api/command":
            return self._reply(404, {"error": "Not found"})
        if self.headers.get("Origin") not in (None, "https://" + self.headers.get("Host", "")):
            return self._reply(403, {"error": "Invalid origin"})
        auth = self.headers.get("Authorization", "")
        if not hmac.compare_digest(auth, "Bearer " + TOKEN):
            return self._reply(401, {"error": "Invalid access token"})
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= MAX_BODY:
                return self._reply(413, {"error": "Invalid request size"})
            data = json.loads(self.rfile.read(size))
            command = data.get("command")
            if not isinstance(command, str):
                return self._reply(400, {"error": "Invalid command"})
            now = time.monotonic()
            with LOCK:
                last = LAST_REQUEST.get(auth, 0)
                if now - last < 1.0:
                    return self._reply(429, {"error": "Please wait before sending another command"})
                LAST_REQUEST[auth] = now
            result = perform(command)
            return self._reply(200, {"ok": True, "result": result})
        except (ValueError, RuntimeError, OSError) as exc:
            return self._reply(400, {"error": str(exc)})
        except (json.JSONDecodeError, TypeError):
            return self._reply(400, {"error": "Invalid JSON"})

    def log_message(self, format, *args):
        # Avoid logging access tokens or request bodies.
        print("%s - %s" % (self.address_string(), format % args))

if __name__ == "__main__":
    if not TOKEN or len(TOKEN) < 32:
        sys.exit("Set JARVIS_REMOTE_TOKEN to a random secret of at least 32 characters.")
    print("JARVIS private agent listening at http://127.0.0.1:%s" % PORT)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
