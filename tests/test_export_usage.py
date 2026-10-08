import copy, io, json, tempfile, unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch
import export_usage
from export_usage import normalize

class ExportTests(unittest.TestCase):
    def setUp(self):
        self.raw={'observedAt':1791309810,'usage':{'result':{'summary':{'lifetimeTokens':4},'dailyUsageBuckets':[{'startDate':'2026-10-05','tokens':0},{'startDate':'2026-10-07','tokens':4}]}},'limits':{'result':{'rateLimitsByLimitId':{'codex':{'primary':{'usedPercent':61,'windowDurationMins':10080,'resetsAt':1791728674},'credits':{'secret':'not-exported'}}}}}}
    def test_real_quota_and_reset(self):
        data=normalize(self.raw);self.assertEqual(data['quota']['remaining'],39);self.assertEqual(data['quota']['resetsAt'],1791728674)
    def test_missing_day_not_zero(self):
        rows=normalize(self.raw)['daily']['buckets'];self.assertEqual(len(rows),2);self.assertEqual(rows[0]['tokens'],0)
    def test_unknown_daily(self):
        self.raw['usage']['result']['dailyUsageBuckets']=None;self.assertIsNone(normalize(self.raw)['daily']['buckets'])
    def test_no_weekly_does_not_use_short_window(self):
        self.raw['limits']['result']['rateLimitsByLimitId']['codex']['primary']['windowDurationMins']=300;self.assertIsNone(normalize(self.raw)['quota']['remaining'])
    def test_private_extras_not_exported(self):self.assertNotIn('secret',json.dumps(normalize(self.raw)))
    def test_upstream_error(self):
        self.raw['usage']={'error':{'message':'unauthorized'}}
        with self.assertRaises(ValueError):normalize(self.raw)
    def test_bad_day(self):
        self.raw['usage']['result']['dailyUsageBuckets'][0]['tokens']=-1
        with self.assertRaises(ValueError):normalize(self.raw)
    def test_duplicate_day(self):
        rows=self.raw['usage']['result']['dailyUsageBuckets'];rows.append(copy.deepcopy(rows[0]))
        with self.assertRaises(ValueError):normalize(self.raw)
    def test_nan_quota(self):
        self.raw['limits']['result']['rateLimitsByLimitId']['codex']['primary']['usedPercent']=float('nan')
        with self.assertRaises(ValueError):normalize(self.raw)


class ResetCardExportTests(unittest.TestCase):
    def setUp(self):
        self.raw = {'observedAt': 1791309810,
                    'usage': {'result': {'summary': {}, 'dailyUsageBuckets': None}},
                    'limits': {'result': {'rateLimits': None}}}

    def set_cards(self, count, details):
        self.raw['limits']['result']['rateLimitResetCredits'] = {
            'availableCount': count, 'credits': details}

    def card(self, expiry, **extra):
        return dict(resetType='codexRateLimits', status='available',
                    expiresAt=expiry, **extra)

    def exported(self):
        return normalize(self.raw)['resetCards']

    def test_old_server_missing_field_is_unknown_not_zero(self):
        result = self.exported()
        self.assertIsNone(result['availableCount'])
        self.assertIsNone(result['expiresAt'])
        self.assertEqual(result['expiryStatus'], 'unknown')
        self.assertEqual(result['observedAt'], self.raw['observedAt'])
        self.assertEqual(result['source'], 'account/rateLimits/read')

    def test_null_summary_is_unknown(self):
        self.raw['limits']['result']['rateLimitResetCredits'] = None
        self.assertIsNone(self.exported()['availableCount'])

    def test_zero_cards_is_known_none(self):
        for details in (None, []):
            with self.subTest(details=details):
                self.set_cards(0, details)
                result = self.exported()
                self.assertEqual(result['availableCount'], 0)
                self.assertIsNone(result['expiresAt'])
                self.assertEqual(result['expiryStatus'], 'none')

    def test_count_only_retains_authoritative_count_and_unknown_expiry(self):
        self.set_cards(3, None)
        result = self.exported()
        self.assertEqual(result['availableCount'], 3)
        self.assertEqual(result['expiryStatus'], 'unknown')
        self.assertIsNone(result['expiresAt'])

    def test_complete_cards_export_earliest_deadline(self):
        self.set_cards(2, [self.card(1794007908), self.card(1793300502)])
        result = self.exported()
        self.assertEqual(result['availableCount'], 2)
        self.assertEqual(result['expiresAt'], 1793300502)
        self.assertEqual(result['expiryStatus'], 'earliest')

    def test_capped_details_do_not_change_count_or_claim_complete_deadline(self):
        self.set_cards(4, [self.card(1793300502)])
        result = self.exported()
        self.assertEqual(result['availableCount'], 4)
        self.assertEqual(result['expiresAt'], 1793300502)
        self.assertEqual(result['expiryStatus'], 'partial')

    def test_unknown_and_unavailable_cards_do_not_supply_deadlines(self):
        for key, value in (('resetType', 'unknown'), ('status', 'redeemed'),
                           ('status', 'redeeming'), ('status', 'unknown')):
            with self.subTest(key=key, value=value):
                row = self.card(1792000000)
                row[key] = value
                self.set_cards(2, [self.card(1793300502), row])
                result = self.exported()
                self.assertEqual(result['expiresAt'], 1793300502)
                self.assertEqual(result['expiryStatus'], 'partial')
                self.assertEqual(result['availableCount'], 2)

    def test_no_expiration_requires_complete_explicit_null_details(self):
        self.set_cards(2, [self.card(None), self.card(None)])
        self.assertEqual(self.exported()['expiryStatus'], 'no_expiry')
        self.set_cards(2, [self.card(None)])
        self.assertEqual(self.exported()['expiryStatus'], 'unknown')
        self.set_cards(1, [])
        self.assertEqual(self.exported()['expiryStatus'], 'unknown')
        row = self.card(None)
        del row['expiresAt']
        self.set_cards(1, [row])
        self.assertEqual(self.exported()['expiryStatus'], 'unknown')

    def test_mixed_expiring_and_non_expiring_cards_have_earliest_deadline(self):
        self.set_cards(2, [self.card(None), self.card(1793300502)])
        self.assertEqual(self.exported()['expiryStatus'], 'earliest')

    def test_elapsed_deadline_is_retained_for_receiver_staleness_check(self):
        self.set_cards(1, [self.card(self.raw['observedAt'] - 1)])
        self.assertEqual(self.exported()['availableCount'], 1)
        self.assertEqual(self.exported()['expiresAt'], self.raw['observedAt'] - 1)

    def test_workspace_credits_are_never_used_as_reset_cards(self):
        self.raw['limits']['result']['rateLimits'] = {'credits': {'balance': '12345'}}
        self.assertIsNone(self.exported()['availableCount'])

    def test_only_whitelisted_statistics_leave_export(self):
        self.set_cards(1, [self.card(1793300502, id='private-credit-id',
                                   title='private-title', description='private-description',
                                   grantedAt=1790708502, accountId='private-account')])
        self.raw['limits']['result'].update(accountId='private-account',
                                           accessToken='private-token',
                                           rateLimitUpsell={'email': 'private@example.test'})
        exported = json.dumps(normalize(self.raw))
        for private in ('private-credit-id', 'private-title', 'private-description',
                        'private-account', 'private-token', 'private@example.test',
                        'grantedAt', 'accessToken'):
            self.assertNotIn(private, exported)
        self.assertEqual(set(self.exported()),
                         {'availableCount', 'expiresAt', 'expiryStatus', 'observedAt', 'source'})

    def test_invalid_counts_reject_snapshot(self):
        for count in (True, False, -1, 1.0, '2', None, 2147483648, float('nan')):
            with self.subTest(count=count):
                self.set_cards(count, None)
                with self.assertRaises(ValueError):
                    self.exported()

    def test_invalid_expiry_rejects_snapshot(self):
        for expiry in (True, False, -1, 0, 1.0, '1793300502', 253402300800,
                       float('nan'), float('inf'), [], {}):
            with self.subTest(expiry=expiry):
                self.set_cards(1, [self.card(expiry)])
                with self.assertRaises(ValueError):
                    self.exported()

    def test_invalid_shapes_reject_snapshot(self):
        for summary in ([], 'unknown', {}, {'availableCount': 1, 'credits': {}},
                        {'availableCount': 1, 'credits': [None]},
                        {'availableCount': 1, 'credits': [{'status': True}]},
                        {'availableCount': 1, 'credits': [{'resetType': []}]}):
            with self.subTest(summary=summary):
                self.raw['limits']['result']['rateLimitResetCredits'] = summary
                with self.assertRaises(ValueError):
                    self.exported()

    def test_failed_read_or_invalid_data_preserves_last_good_export(self):
        for failure in ('fetch', 'invalid_count', 'invalid_expiry'):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                destination = root / 'usage-latest.json'
                destination.write_text('previous good data', encoding='utf-8')
                self.set_cards(-1 if failure == 'invalid_count' else 1,
                               [self.card('bad' if failure == 'invalid_expiry' else 1793300502)])
                fetch_args = {'side_effect': RuntimeError('read failed')} if failure == 'fetch' else {'return_value': self.raw}
                with patch.object(export_usage, 'ROOT', root), \
                     patch.object(export_usage, 'fetch', **fetch_args), \
                     patch('sys.argv', ['export_usage.py']), redirect_stdout(io.StringIO()):
                    with self.assertRaises((RuntimeError, ValueError)):
                        export_usage.main()
                self.assertEqual(destination.read_text(encoding='utf-8'), 'previous good data')
                self.assertFalse(destination.with_suffix('.tmp').exists())

if __name__=='__main__':unittest.main()
