#!/usr/bin/env python3
"""Audit test server for Fullscreen Web (download + WebView scenarios).

The app only talks https with SYSTEM-trusted certificates, so either
  * pass --cert/--key with a real certificate (e.g. Let's Encrypt), or
  * run plain HTTP here and expose it through an HTTPS tunnel/reverse proxy you control.
Optional --alt-host is a second hostname that reaches this same server (for cross-origin tests).
Every request is printed with timestamp, path, and the Cookie/Referer it carried, so the SERVER LOG is the
observation point for 'did the app send this / did traffic stop when the VPN dropped'.
"""
import argparse, json, ssl, sys, time, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

ARGS = None
PAGE = open(__file__.replace('server.py', 'webview_tests.html'), encoding='utf-8').read() if __file__ else ''

class H(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, fmt, *a):
        sys.stdout.write('%s %s %s\n' % (time.strftime('%H:%M:%S'), self.client_address[0], fmt % a)); sys.stdout.flush()
    def _log(self, extra=''):
        sys.stdout.write('   cookie=%r referer=%r ua=%r %s\n' % (self.headers.get('Cookie'), self.headers.get('Referer'),
                         (self.headers.get('User-Agent') or '')[:60], extra)); sys.stdout.flush()
    def _send(self, code, body=b'', ctype='text/plain; charset=utf-8', headers=()):
        self.send_response(code)
        self.send_header('Content-Type', ctype); self.send_header('Content-Length', str(len(body)))
        for k, v in headers: self.send_header(k, v)
        self.end_headers(); self.wfile.write(body)
    def _host(self): return self.headers.get('Host', 'localhost')
    def _stream(self, total, per_sec=None, name='sample.bin', fail_at=None, extra=()):
        self.send_response(200)
        self.send_header('Content-Type', 'application/octet-stream'); self.send_header('Content-Length', str(total))
        self.send_header('Content-Disposition', 'attachment; filename="%s"' % name)
        for k, v in extra: self.send_header(k, v)
        self.end_headers()
        sent, chunk = 0, 64 * 1024
        try:
            while sent < total:
                n = min(chunk, total - sent)
                self.wfile.write(b'A' * n); self.wfile.flush(); sent += n
                if fail_at and sent >= fail_at: self.connection.close(); return
                if per_sec: time.sleep(n / per_sec)
        except (BrokenPipeError, ConnectionResetError):
            sys.stdout.write('   CLIENT DISCONNECTED after %d/%d bytes at %s\n' % (sent, total, time.strftime('%H:%M:%S')))
            return
        sys.stdout.write('   stream complete %d bytes\n' % sent)
    def do_POST(self):
        n = int(self.headers.get('Content-Length') or 0); body = self.rfile.read(n).decode('utf-8', 'replace')
        sys.stdout.write('   REPORT %s\n' % body[:500]); sys.stdout.flush(); self._send(204)
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query); p = u.path; self._log()
        alt = ARGS.alt_host or self._host()
        if p == '/': return self._send(200, PAGE.replace('__ALT__', alt).encode(), 'text/html; charset=utf-8')
        if p == '/sw.js': return self._send(200, SW_JS.encode(), 'application/javascript', [('Service-Worker-Allowed', '/')])
        if p in ('/sw-ping', '/popup', '/frame', '/page2'):
            return self._send(200, ('<html><body>%s ok</body></html>' % p).encode() if p != '/frame' else FRAME.encode(), 'text/html; charset=utf-8')
        if p == '/file': return self._stream(1 << 20)
        if p == '/dupe': return self._stream(4096, name='report.pdf')
        if p == '/mime':
            return self._send(200, b'hello', 'text/plain; charset=utf-8', [('Content-Disposition', 'attachment; filename="note.txt"')])
        if p == '/evil-name':
            return self._send(200, b'x', 'application/octet-stream', [('Content-Disposition',
                'attachment; filename="../../etc/passwd"; filename*=UTF-8\'\'..%2F..%2Fevil%E2%80%AEgpj.exe')])
        if p == '/slow': return self._stream(int(q.get('mb', ['64'])[0]) << 20, per_sec=256 * 1024, name='slow.bin')
        if p == '/big': return self._stream(int(q.get('mb', ['512'])[0]) << 20, name='big.bin')
        if p == '/fail': return self._stream(8 << 20, fail_at=2 << 20, name='fail.bin')
        if p == '/echo':
            body = json.dumps({'cookie': self.headers.get('Cookie'), 'referer': self.headers.get('Referer'), 'host': self._host()}).encode()
            return self._send(200, body, 'application/json', [('Content-Disposition', 'attachment; filename="echo.json"')])
        if p == '/cookie-set': return self._send(200, b'cookie set', headers=[('Set-Cookie', 'sid=abc123; Secure; Path=/; Max-Age=3600')])
        red = {'/redirect-https': '/file', '/redirect-http': 'http://%s/file' % self._host(),
               '/redirect-cross': 'https://%s/echo' % alt, '/redirect-private': 'https://192.168.1.1/x',
               '/redirect-localhost': 'https://localhost/x', '/redirect-ipv6': 'https://[::1]/x',
               '/redirect-metadata': 'https://169.254.169.254/latest/meta-data', '/redirect-loop': '/redirect-loop',
               '/redirect-page-http': 'http://%s/page2' % self._host(), '/redirect-page-private': 'https://192.168.1.1/'}
        if p in red: return self._send(302, b'', headers=[('Location', red[p])])
        self._send(404, b'not found')

SW_JS = """
self.addEventListener('install', e => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', e => {
  if (new URL(e.request.url).pathname === '/sw-ping') {
    fetch('https://doubleclick.net/sw-probe').catch(() => {});            // tracker host: shield must refuse
    fetch('https://192.168.1.1/sw-probe').catch(() => {});                // private target: must be refused
  }
});
"""
FRAME = "<html><body><button id=b onclick=\"navigator.mediaDevices.getUserMedia({video:true}).then(()=>document.title='granted',e=>document.title='denied:'+e.name)\">frame camera</button></body></html>"

if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--port', type=int, default=8080)
    ap.add_argument('--cert'); ap.add_argument('--key'); ap.add_argument('--alt-host', default='')
    ARGS = ap.parse_args()
    srv = ThreadingHTTPServer(('0.0.0.0', ARGS.port), H)
    if ARGS.cert and ARGS.key:
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); ctx.load_cert_chain(ARGS.cert, ARGS.key)
        srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
    print('listening on :%d (%s)' % (ARGS.port, 'HTTPS' if ARGS.cert else 'plain HTTP - put an HTTPS front in front of it'))
    srv.serve_forever()
