"""Fetch an HTTPS usage snapshot without forwarding authorization through redirects."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

from snapshot_schema import MAX_BYTES, loads_snapshot, write_snapshot_atomic

AUTH_MODES = {'none', 'bearer', 'private_token', 'token'}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def request_for(url, auth='none', token_env='AGENT_USAGE_TOKEN', environ=None):
    if not isinstance(url, str) or len(url) > 4096 or any(ord(c) < 32 or ord(c) == 127 for c in url):
        raise ValueError('Use a final HTTPS JSON URL without embedded credentials')
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme.lower() != 'https' or not parsed.hostname or parsed.username is not None or parsed.password is not None or '#' in url or parsed.port == 0:
            raise ValueError('Invalid HTTPS statistics URL')
        for key, _ in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True):
            key = key.lower().replace('-', '_')
            if any(part in key for part in ('token', 'password', 'secret', 'signature', 'credential')) or key in {'key', 'api_key', 'apikey', 'sig', 'auth', 'authorization'}:
                raise ValueError('Read-only credentials must use the token environment variable, never the URL')
    except (ValueError, UnicodeError):
        raise ValueError('Use a final HTTPS JSON URL without embedded credentials') from None
    if not isinstance(auth, str) or auth not in AUTH_MODES:
        raise ValueError('Unsupported authorization mode')
    headers = {'Accept': 'application/json', 'User-Agent': 'Agent-Usage-Wallpaper'}
    if auth != 'none':
        if not isinstance(token_env, str) or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', token_env):
            raise ValueError('Invalid token environment variable name')
        token = (os.environ if environ is None else environ).get(token_env, '')
        if not isinstance(token, str) or not token or len(token) > 4096 or any(c.isspace() or ord(c) < 33 or ord(c) > 126 for c in token):
            raise ValueError('Read-only token is missing or invalid')
        headers['PRIVATE-TOKEN' if auth == 'private_token' else 'Authorization'] = token if auth == 'private_token' else ('token ' if auth == 'token' else 'Bearer ') + token
    return urllib.request.Request(url, headers=headers, method='GET')


def pull_snapshot(url, output, auth='none', token_env='AGENT_USAGE_TOKEN', environ=None, opener=None):
    request = request_for(url, auth, token_env, environ)
    transport = urllib.request.build_opener(NoRedirect()) if opener is None else opener
    try:
        with transport.open(request, timeout=30) as response:
            if response.getcode() != 200 or response.geturl() != request.full_url:
                raise ValueError('The data source must return HTTP 200 directly; redirects are refused')
            length = response.headers.get('Content-Length')
            if length is not None and (not length.isdigit() or int(length) > MAX_BYTES):
                raise ValueError('Statistics JSON exceeds the 1 MiB limit')
            content = response.read(MAX_BYTES + 1)
        data = loads_snapshot(content)
        write_snapshot_atomic(output, data)
    except (urllib.error.URLError, OSError):
        raise RuntimeError('HTTPS pull failed; check the final JSON URL, connectivity and read-only authorization') from None
    return {'observedAt': data['observedAt'], 'bytes': len(content), 'validated': True, 'delivery': 'local_only'}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='Final HTTPS raw JSON URL; never include credentials')
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--auth', choices=sorted(AUTH_MODES), default='none')
    parser.add_argument('--token-env', default='AGENT_USAGE_TOKEN', help='Environment variable containing a read-only token')
    args = parser.parse_args(argv)
    try:
        report = pull_snapshot(args.url, args.output, args.auth, args.token_env)
    except (ValueError, RuntimeError) as failure:
        raise SystemExit(str(failure)) from None
    except OSError:
        raise SystemExit('Pull failed while reading or writing local data; existing statistics were preserved') from None
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
