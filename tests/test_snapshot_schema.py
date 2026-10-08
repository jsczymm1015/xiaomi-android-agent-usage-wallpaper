import copy
import json
from pathlib import Path
import tempfile
import time
import contextlib
import io
import snapshot_schema
import unittest

from snapshot_schema import (MAX_BYTES, MAX_SAFE_INTEGER, load_snapshot,
                             loads_snapshot, snapshot_bytes, validate_snapshot,
                             write_snapshot_atomic)


def example(now=None):
    now = int(time.time()) if now is None else now
    return {'schemaVersion': 1, 'source': 'agent_usage', 'provider': 'Demo Agent',
            'observedAt': now, 'chartMode': 'live',
            'quota': {'remaining': None, 'window': 'unknown', 'observedAt': now, 'source': 'agent_report', 'resetsAt': None},
            'daily': {'observedAt': now, 'source': 'agent_report', 'dateSemantics': 'upstream dates unchanged; missing dates unknown', 'buckets': None},
            'resetCards': {'availableCount': None, 'expiresAt': None, 'expiryStatus': 'unknown', 'observedAt': now, 'source': 'agent_report'}}


class SnapshotSchemaTests(unittest.TestCase):
    def test_excessive_json_nesting_is_a_safe_validation_failure(self):
        with self.assertRaises(ValueError):
            loads_snapshot('['*2000+'0'+']'*2000)

    def test_unknown_values_remain_unknown_and_no_private_fields_added(self):
        data = validate_snapshot(example())
        self.assertIsNone(data['quota']['remaining'])
        self.assertIsNone(data['daily']['buckets'])
        self.assertIsNone(data['resetCards']['availableCount'])
        self.assertNotIn('summary', data)
        self.assertNotIn('rateLimits', data)

    def test_old_format_defaults_do_not_mutate_input(self):
        data = example()
        data['source'] = 'codex_app_server'
        del data['provider']
        data['quota'] = {'remaining': 72}
        data['daily'] = {'buckets': []}
        del data['resetCards']
        result = validate_snapshot(data)
        self.assertEqual(result['provider'], 'Codex')
        self.assertEqual(result['quota']['window'], 'unknown')
        self.assertEqual(result['quota']['observedAt'], data['observedAt'])
        self.assertNotIn('provider', data)
        self.assertEqual(data['quota'], {'remaining': 72})

    def test_codex_legacy_aggregate_whitelist(self):
        data = example()
        data['source'] = 'codex_app_server'
        data['daily']['dateSemantics'] = 'upstream startDate unchanged; missing dates unknown'
        data['summary'] = {'lifetimeTokens': 42, 'longestRunningTurnSec': None}
        data['rateLimits'] = [{'name': 'primary', 'usedPercent': 15.5, 'remaining': 84.5, 'windowDurationMins': 300, 'resetsAt': data['observedAt'] + 300}]
        self.assertEqual(validate_snapshot(data)['summary']['lifetimeTokens'], 42)

    def test_reject_unknown_keys_at_every_protocol_level_without_echoing_values(self):
        for location in ('top', 'quota', 'daily', 'cards', 'row', 'summary', 'limit'):
            with self.subTest(location=location):
                data = example()
                target = data
                if location in ('quota', 'daily'): target = data[location]
                elif location == 'cards': target = data['resetCards']
                elif location == 'row':
                    data['daily']['buckets'] = [{'date': '2026-10-01', 'tokens': 2}]
                    target = data['daily']['buckets'][0]
                elif location == 'summary':
                    data['summary'] = {}; target = data['summary']
                elif location == 'limit':
                    data['rateLimits'] = [{'name': 'primary'}]; target = data['rateLimits'][0]
                target['private-test-only'] = 'not-for-output'
                with self.assertRaises(ValueError) as caught: validate_snapshot(data)
                self.assertNotIn('private-test-only', str(caught.exception))
                self.assertNotIn('not-for-output', str(caught.exception))

    def test_strict_schema_and_observation_numbers(self):
        for key, value in [('schemaVersion', True), ('schemaVersion', 1.0), ('schemaVersion', '1'), ('observedAt', True), ('observedAt', 1.1), ('observedAt', '123'), ('observedAt', 0), ('observedAt', int(time.time()) + 601)]:
            with self.subTest(key=key, value=value):
                data = example(); data[key] = value
                with self.assertRaises(ValueError): validate_snapshot(data)

    def test_child_observations_cannot_be_newer_than_snapshot(self):
        for key in ('quota', 'daily', 'resetCards'):
            for value in (True, '123', 0, 1.5, int(time.time()) + 1):
                with self.subTest(key=key, value=value):
                    data = example(); data[key]['observedAt'] = value
                    with self.assertRaises(ValueError): validate_snapshot(data)

    def test_quota_strict_numeric_percentages_and_known_windows(self):
        for value in (True, '73', -1, 100.1, float('nan'), float('inf'), 10 ** 1000):
            with self.subTest(value=str(value)[:40]):
                data = example(); data['quota']['remaining'] = value
                with self.assertRaises(ValueError): validate_snapshot(data)
        for window in ('weekly', 'daily', 'monthly', 'other', 'unknown'):
            data = example(); data['quota']['window'] = window
            self.assertEqual(validate_snapshot(data)['quota']['window'], window)
        for window in ('annual', None, [], 1):
            data = example(); data['quota']['window'] = window
            with self.assertRaises(ValueError): validate_snapshot(data)

    def test_provider_boundaries(self):
        for provider in ('', 'x' * 33, ' Agent', 'Agent ', 'Agent\n', '\u0085', None, 3):
            data = example(); data['provider'] = provider
            with self.assertRaises(ValueError): validate_snapshot(data)
        data = example(); data['provider'] = '机' * 32
        self.assertEqual(validate_snapshot(data)['provider'], '机' * 32)

    def test_daily_tokens_and_dates_are_strict_and_sorted(self):
        data = example(); data['daily']['buckets'] = [{'date': '2026-10-02', 'tokens': MAX_SAFE_INTEGER}, {'date': '2026-10-01', 'tokens': 0}]
        self.assertEqual(validate_snapshot(data)['daily']['buckets'][0]['date'], '2026-10-01')
        for tokens in (True, 1.0, '2', -1, MAX_SAFE_INTEGER + 1):
            data = example(); data['daily']['buckets'] = [{'date': '2026-10-01', 'tokens': tokens}]
            with self.assertRaises(ValueError): validate_snapshot(data)
        for date in ('2026-02-29', '2026-1-01', '20261001', '0000-01-01', '2026-10-01T00:00:00Z', None):
            data = example(); data['daily']['buckets'] = [{'date': date, 'tokens': 0}]
            with self.assertRaises(ValueError): validate_snapshot(data)
        data = example(); data['daily']['buckets'] = [{'date': '2026-10-01', 'tokens': 0}] * 2
        with self.assertRaises(ValueError): validate_snapshot(data)

    def test_reset_card_unknown_zero_and_expiry_consistency(self):
        for count, expiry, state in [(None, None, 'unknown'), (0, None, 'none'), (2, None, 'no_expiry'), (2, int(time.time()) + 300, 'earliest'), (None, int(time.time()) + 300, 'partial')]:
            data = example(); data['resetCards'].update(availableCount=count, expiresAt=expiry, expiryStatus=state)
            self.assertEqual(validate_snapshot(data)['resetCards']['availableCount'], count)
        for count, expiry, state in [(0, None, 'unknown'), (0, None, 'partial'), (None, None, 'none'), (2, None, 'none'), (2, None, 'earliest'), (0, None, 'no_expiry'), (2, 0, 'earliest'), (2, int(time.time()), 'unknown'), (2.0, None, 'unknown'), (True, None, 'unknown')]:
            data = example(); data['resetCards'].update(availableCount=count, expiresAt=expiry, expiryStatus=state)
            with self.assertRaises(ValueError): validate_snapshot(data)

    def test_parser_rejects_duplicates_nonfinite_and_oversize(self):
        for content in (b'{"schemaVersion":1,"schemaVersion":1}', b'{"quota": NaN}', b'{"quota": Infinity}', b'\xff', b' ' * (MAX_BYTES + 1)):
            with self.assertRaises(ValueError): loads_snapshot(content)


    def test_official_fractional_duration_and_overused_percentage(self):
        data = example()
        data['summary'] = {'longestRunningTurnSec': 12.75, 'lifetimeTokens': 1}
        data['rateLimits'] = [{'name': 'primary', 'usedPercent': 125.5, 'remaining': 0}]
        result = validate_snapshot(data)
        self.assertEqual(result['summary']['longestRunningTurnSec'], 12.75)
        self.assertEqual(result['rateLimits'][0]['usedPercent'], 125.5)
        for duration in (True, -1, float('inf'), float('nan'), '12.5'):
            data['summary']['longestRunningTurnSec'] = duration
            with self.assertRaises(ValueError): validate_snapshot(data)

    def test_cli_really_validates_and_invalid_input_exits_nonzero(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'usage.json'
            write_snapshot_atomic(path, example())
            output = io.StringIO()
            with contextlib.redirect_stdout(output): snapshot_schema.main([str(path)])
            self.assertTrue(json.loads(output.getvalue())['validated'])
            path.write_text('{"unexpected-private-test":true}')
            with self.assertRaises(SystemExit) as caught: snapshot_schema.main([str(path)])
            self.assertNotEqual(caught.exception.code, 0)
            self.assertNotIn('unexpected-private-test', str(caught.exception))

    def test_atomic_write_preserves_old_file_on_failed_validation(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'usage.json'; path.write_bytes(b'last-good')
            data = example(); data['quota']['remaining'] = 'incorrect'
            with self.assertRaises(ValueError): write_snapshot_atomic(path, data)
            self.assertEqual(path.read_bytes(), b'last-good')
            good = example()
            write_snapshot_atomic(path, good)
            self.assertEqual(load_snapshot(path), loads_snapshot(snapshot_bytes(good)))
            self.assertEqual(list(Path(temp).glob('*.tmp')), [])


if __name__ == '__main__': unittest.main()
