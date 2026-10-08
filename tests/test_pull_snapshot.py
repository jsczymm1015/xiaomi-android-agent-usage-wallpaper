import io
from pathlib import Path
import tempfile
import unittest
import urllib.error

from pull_snapshot import NoRedirect, pull_snapshot, request_for
from snapshot_schema import MAX_BYTES, load_snapshot, snapshot_bytes
from test_snapshot_schema import example


class FakeResponse(io.BytesIO):
    def __init__(self, payload, url='https://data.example/usage.json', code=200, headers=None):
        super().__init__(payload); self.url = url; self.code = code; self.headers = headers or {}
    def geturl(self): return self.url
    def getcode(self): return self.code


class FakeOpener:
    def __init__(self, response): self.response = response; self.request = None
    def open(self, request, timeout): self.request = request; return self.response


class PullSnapshotTests(unittest.TestCase):
    def test_public_json_needs_no_token_and_writes_validated_stats(self):
        payload = example(); opener = FakeOpener(FakeResponse(snapshot_bytes(payload)))
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'usage.json'
            report = pull_snapshot('https://data.example/usage.json', path, environ={}, opener=opener)
            self.assertTrue(report['validated'])
            self.assertEqual(load_snapshot(path), payload)
            self.assertIsNone(opener.request.get_header('Authorization'))

    def test_authentication_uses_only_named_environment_variable_and_fixed_headers(self):
        for auth, header, value in [('bearer', 'Authorization', 'Bearer test-only'), ('private_token', 'Private-token', 'test-only'), ('token', 'Authorization', 'token test-only')]:
            with self.subTest(auth=auth):
                request = request_for('https://data.example/usage.json', auth, environ={'AGENT_USAGE_TOKEN': 'test-only'})
                self.assertEqual(request.get_header(header), value)
                self.assertNotIn('test-only', request.full_url)
        for token in ('', 'has space', 'test\nHeader: value', 'test\rHeader:value', '\u0000', '中文'):
            with self.assertRaises(ValueError): request_for('https://data.example/usage.json', 'bearer', environ={'AGENT_USAGE_TOKEN': token})

    def test_url_credentials_http_and_fragments_are_refused(self):
        for url in ('http://data.example/usage.json', 'https://user:pass@data.example/usage.json', 'https://data.example/usage.json#', 'https://data.example/usage.json#fragment', 'https://data.example:70000/usage.json', 'https://data.example/usage.json?access_token=test-only', 'https://data.example/usage.json?apiKey=test-only', 'https://data.example/usage.json?X-Amz-Signature=test-only'):
            with self.subTest(url=url):
                with self.assertRaises(ValueError): request_for(url)
        request_for('https://data.example/usage.json?ref=main')

    def test_redirect_handler_never_forwards_authorization(self):
        request = request_for('https://data.example/usage.json', 'bearer', environ={'AGENT_USAGE_TOKEN': 'test-only'})
        self.assertIsNone(NoRedirect().redirect_request(request, None, 302, 'Found', {'Location': 'https://other.example/'}, 'https://other.example/'))

    def test_invalid_or_redirected_or_oversized_response_preserves_last_good_file(self):
        cases = [FakeResponse(b'not JSON'), FakeResponse(b'{"schemaVersion":1,"schemaVersion":1}'),
                 FakeResponse(snapshot_bytes(example()), code=302),
                 FakeResponse(snapshot_bytes(example()), url='https://other.example/usage.json'),
                 FakeResponse(b' ' * (MAX_BYTES + 1)),
                 FakeResponse(b'', headers={'Content-Length': str(MAX_BYTES + 1)})]
        for response in cases:
            with self.subTest(code=response.code, url=response.url, length=len(response.getvalue())), tempfile.TemporaryDirectory() as temp:
                path = Path(temp) / 'usage.json'; path.write_bytes(b'last-good')
                with self.assertRaises((ValueError, RuntimeError)):
                    pull_snapshot('https://data.example/usage.json', path, opener=FakeOpener(response))
                self.assertEqual(path.read_bytes(), b'last-good')

    def test_http_failure_does_not_echo_url_or_token(self):
        class Failure:
            def open(self, request, timeout):
                raise urllib.error.HTTPError('https://test-only.example/private', 401, 'test-only-private-message', {}, None)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'usage.json'; path.write_bytes(b'last-good')
            with self.assertRaises(RuntimeError) as caught:
                pull_snapshot('https://data.example/usage.json', path, 'bearer', environ={'AGENT_USAGE_TOKEN': 'test-only'}, opener=Failure())
            self.assertNotIn('test-only', str(caught.exception))
            self.assertEqual(path.read_bytes(), b'last-good')


if __name__ == '__main__': unittest.main()
