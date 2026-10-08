"""Validated, privacy-limited protocol shared by every exporter and transport.

Only aggregate statistics are accepted. Unknown JSON keys, duplicate keys,
coerced integers and non-finite values are rejected before any destination is
replaced. This module has no network or agent-specific dependency.
"""
import copy
import datetime
import json
import math
import os
from pathlib import Path
import tempfile
import time

MAX_BYTES = 1024 * 1024
MAX_TIMESTAMP = 253402300799
MAX_SAFE_INTEGER = 9007199254740991
MAX_COUNT = 2147483647
SUMMARY_FIELDS = {'lifetimeTokens', 'peakDailyTokens', 'longestRunningTurnSec',
                  'currentStreakDays', 'longestStreakDays'}
DATE_SEMANTICS = {'upstream startDate unchanged; missing dates unknown',
                  'upstream dates unchanged; missing dates unknown'}


def _object(value, allowed, required=()):
    if not isinstance(value, dict) or not set(required) <= value.keys() or not value.keys() <= allowed:
        raise ValueError('Invalid statistics object or unrecognized fields')
    return value


def _integer(value, minimum=0, maximum=MAX_SAFE_INTEGER):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError('Statistics integers are invalid or out of range')
    return value


def _number(value, minimum=0, maximum=100):
    if type(value) not in (int, float) or not minimum <= value <= maximum or not math.isfinite(value):
        raise ValueError('Statistics numeric value is invalid or out of range')
    return value


def _timestamp(value, ceiling=MAX_TIMESTAMP):
    return _integer(value, 1, min(ceiling, MAX_TIMESTAMP))


def _one_of(value, choices):
    if not isinstance(value, str) or value not in choices:
        raise ValueError('Unrecognized statistics value')
    return value


def _reset_cards(cards, observed):
    _object(cards, {'availableCount', 'expiresAt', 'expiryStatus', 'observedAt', 'source'},
            {'availableCount', 'expiresAt', 'expiryStatus', 'observedAt', 'source'})
    count, expiry, status = cards['availableCount'], cards['expiresAt'], cards['expiryStatus']
    if count is not None:
        _integer(count, 0, MAX_COUNT)
    if expiry is not None:
        _timestamp(expiry)
    _timestamp(cards['observedAt'], observed)
    _one_of(cards['source'], {'account/rateLimits/read', 'agent_report'})
    _one_of(status, {'unknown', 'none', 'no_expiry', 'earliest', 'partial'})
    if count == 0 and status != 'none':
        raise ValueError('Zero reset cards must use the none state')
    if status == 'none' and (count != 0 or expiry is not None):
        raise ValueError('Invalid empty reset-card state')
    if status == 'no_expiry' and (count is None or count <= 0 or expiry is not None):
        raise ValueError('Invalid reset-card no-expiry state')
    if status == 'earliest' and (count is None or count <= 0 or expiry is None):
        raise ValueError('Invalid reset-card earliest-expiry state')
    if status == 'unknown' and expiry is not None:
        raise ValueError('Unknown reset-card expiry cannot contain a deadline')


def validate_snapshot(data, now=None):
    """Return an independent, validated snapshot, filling only explicit defaults.

    Legacy child observation/source fields may be absent. A missing window stays
    unknown; missing counts or quotas are never interpreted as zero. No account,
    token, transcript or opaque credit detail field is accepted.
    """
    _object(data, {'schemaVersion', 'source', 'provider', 'observedAt', 'chartMode',
                   'quota', 'daily', 'resetCards', 'rateLimits', 'summary'},
            {'schemaVersion', 'source', 'observedAt', 'chartMode', 'quota', 'daily'})
    result = copy.deepcopy(data)
    if _integer(result['schemaVersion'], 1, 1) != 1:
        raise ValueError('Unsupported statistics schema')
    source = _one_of(result['source'], {'agent_usage', 'codex_app_server'})
    _one_of(result['chartMode'], {'live'})
    observed = _timestamp(result['observedAt'], int(time.time() if now is None else now) + 600)
    provider = result.setdefault('provider', 'Codex' if source == 'codex_app_server' else 'Agent')
    if not isinstance(provider, str) or not 1 <= len(provider) <= 32 or provider != provider.strip() or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in provider):
        raise ValueError('Provider must be a short, non-empty display name')
    quota = _object(result['quota'], {'remaining', 'window', 'observedAt', 'source', 'resetsAt'}, {'remaining'})
    if quota['remaining'] is not None:
        _number(quota['remaining'])
    _one_of(quota.setdefault('window', 'unknown'), {'weekly', 'daily', 'monthly', 'other', 'unknown'})
    _timestamp(quota.setdefault('observedAt', observed), observed)
    _one_of(quota.setdefault('source', 'desktop_codex_app_server' if source == 'codex_app_server' else 'agent_report'),
            {'desktop_codex_app_server', 'phone_chatgpt_usage_page', 'agent_report'})
    if quota.get('resetsAt') is not None:
        _timestamp(quota['resetsAt'])
    daily = _object(result['daily'], {'buckets', 'observedAt', 'source', 'dateSemantics'}, {'buckets'})
    _timestamp(daily.setdefault('observedAt', observed), observed)
    _one_of(daily.setdefault('source', 'account/usage/read' if source == 'codex_app_server' else 'agent_report'),
            {'account/usage/read', 'agent_report'})
    if 'dateSemantics' in daily:
        _one_of(daily['dateSemantics'], DATE_SEMANTICS)
    buckets = daily['buckets']
    if buckets is not None:
        if not isinstance(buckets, list) or len(buckets) > 10000:
            raise ValueError('Invalid daily statistics list')
        seen = set()
        for row in buckets:
            _object(row, {'date', 'tokens'}, {'date', 'tokens'})
            date = row['date']
            if not isinstance(date, str) or len(date) != 10:
                raise ValueError('Invalid daily statistics date')
            try:
                parsed = datetime.date.fromisoformat(date)
            except (ValueError, TypeError):
                raise ValueError('Invalid daily statistics date') from None
            if parsed.isoformat() != date or date in seen:
                raise ValueError('Daily dates must be unique ISO calendar dates')
            seen.add(date)
            _integer(row['tokens'])
        buckets.sort(key=lambda row: row['date'])
    if 'resetCards' in result:
        _reset_cards(result['resetCards'], observed)
    if 'summary' in result:
        summary = _object(result['summary'], SUMMARY_FIELDS)
        for key, value in summary.items():
            if value is not None:
                if key == 'longestRunningTurnSec':
                    _number(value, 0, MAX_SAFE_INTEGER)
                else:
                    _integer(value)
    if 'rateLimits' in result:
        limits = result['rateLimits']
        if not isinstance(limits, list) or len(limits) > 32:
            raise ValueError('Invalid rate-limit statistics list')
        names = set()
        for limit in limits:
            _object(limit, {'name', 'usedPercent', 'remaining', 'windowDurationMins', 'resetsAt'}, {'name'})
            name = _one_of(limit['name'], {'primary', 'secondary'})
            if name in names:
                raise ValueError('Duplicate rate-limit window')
            names.add(name)
            for key in ('usedPercent', 'remaining'):
                if limit.get(key) is not None:
                    _number(limit[key], 0, MAX_SAFE_INTEGER if key == 'usedPercent' else 100)
            if limit.get('windowDurationMins') is not None:
                _integer(limit['windowDurationMins'], 1)
            if limit.get('resetsAt') is not None:
                _timestamp(limit['resetsAt'])
    return result


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON fields are not accepted')
        result[key] = value
    return result


def loads_snapshot(content, now=None):
    if isinstance(content, str):
        content = content.encode('utf-8')
    if not isinstance(content, bytes) or len(content) > MAX_BYTES:
        raise ValueError('Statistics JSON exceeds the 1 MiB limit')
    try:
        data = json.loads(content.decode('utf-8'), object_pairs_hook=_pairs,
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Non-finite JSON numbers are not accepted')))
    except (UnicodeError, json.JSONDecodeError, RecursionError):
        raise ValueError('Statistics must be valid UTF-8 JSON') from None
    return validate_snapshot(data, now)


def load_snapshot(path, now=None):
    with Path(path).open('rb') as stream:
        return loads_snapshot(stream.read(MAX_BYTES + 1), now)


def snapshot_bytes(data, now=None):
    content = (json.dumps(validate_snapshot(data, now), ensure_ascii=False, indent=2, allow_nan=False) + '\n').encode('utf-8')
    if len(content) > MAX_BYTES:
        raise ValueError('Statistics JSON exceeds the 1 MiB limit')
    return content


def write_snapshot_atomic(path, data, now=None):
    """Validate first, then replace one file; failures preserve existing contents."""
    content = snapshot_bytes(data, now)
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(prefix='.' + destination.name + '-', suffix='.tmp', dir=destination.parent, delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    return validate_snapshot(data, now)


def main(argv=None):
    import argparse
    parser = argparse.ArgumentParser(description='Validate aggregate usage JSON without displaying private payloads')
    parser.add_argument('file', type=Path)
    args = parser.parse_args(argv)
    try:
        data = load_snapshot(args.file)
    except ValueError as failure:
        raise SystemExit(str(failure)) from None
    except OSError:
        raise SystemExit('Could not read statistics JSON; file was not changed') from None
    print(json.dumps({'validated': True, 'schemaVersion': data['schemaVersion'],
                      'observedAt': data['observedAt'],
                      'dailyBuckets': len(data['daily']['buckets'] or [])}))


if __name__ == '__main__':
    main()
