"""Export official Codex account statistics. No credentials leave app-server."""
from pathlib import Path
import argparse, datetime, json, math, os, queue, subprocess, threading, time

from runtime_paths import data_dir, find_codex
ROOT = data_dir()

MAX_COUNT = 2147483647
MAX_TIMESTAMP = 253402300799  # Last Unix second representable in year 9999.


def _integer(value, label, minimum, maximum):
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ValueError(f'Invalid {label}; preserving previous export')
    return value


def normalize_reset_cards(limits, observed):
    """Export earned resets, never workspace credit balances or opaque credit IDs.

    The service's count is authoritative. Detail rows may be absent or capped,
    so a deadline from an incomplete list is explicitly marked as partial.
    """
    result = dict(availableCount=None, expiresAt=None, expiryStatus='unknown',
                  observedAt=observed, source='account/rateLimits/read')
    summary = limits.get('rateLimitResetCredits')
    if summary is None:
        return result
    if not isinstance(summary, dict):
        raise ValueError('Invalid reset-card summary; preserving previous export')
    count = _integer(summary.get('availableCount'), 'reset-card count', 0, MAX_COUNT)
    result['availableCount'] = count
    details = summary.get('credits')
    if details is not None and not isinstance(details, list):
        raise ValueError('Invalid reset-card details; preserving previous export')
    deadlines = []
    eligible = 0
    expiry_known = True
    for row in details or []:
        if not isinstance(row, dict):
            raise ValueError('Invalid reset-card detail; preserving previous export')
        for key in ('resetType', 'status'):
            if key in row and (not isinstance(row[key], str) or not row[key]):
                raise ValueError('Invalid reset-card detail type; preserving previous export')
        expiry = row.get('expiresAt')
        if expiry is not None:
            _integer(expiry, 'reset-card expiry', 1, MAX_TIMESTAMP)
        if row.get('resetType') != 'codexRateLimits' or row.get('status') != 'available':
            continue
        eligible += 1
        if 'expiresAt' not in row:
            expiry_known = False
        elif expiry is not None:
            deadlines.append(expiry)
    if count == 0:
        result['expiryStatus'] = 'none'
        return result
    complete = details is not None and len(details) == count and eligible == count and expiry_known
    if deadlines:
        result['expiresAt'] = min(deadlines)
        result['expiryStatus'] = 'earliest' if complete else 'partial'
    elif complete:
        # Explicit null is documented as no expiration, unlike missing details.
        result['expiryStatus'] = 'no_expiry'
    return result


def normalize(raw):
    for key in ('usage', 'limits'):
        if 'error' in raw[key]:
            raise ValueError(f'{key} API failed; preserving previous export')
    usage = raw['usage']['result']
    limits = raw['limits']['result']
    mapping = limits.get('rateLimitsByLimitId')
    rate = mapping.get('codex') if mapping is not None else limits.get('rateLimits')
    windows = []
    for name in ('primary', 'secondary'):
        w = (rate or {}).get(name)
        if not w:
            continue
        used = w.get('usedPercent')
        if isinstance(used, bool) or not isinstance(used, (int, float)) or not math.isfinite(used):
            raise ValueError('Invalid quota percentage')
        windows.append(dict(name=name, usedPercent=used, remaining=max(0, min(100, 100-used)),
                            windowDurationMins=w.get('windowDurationMins'), resetsAt=w.get('resetsAt')))
    weekly = next((w for w in windows if w['windowDurationMins'] == 10080), None)
    buckets = usage.get('dailyUsageBuckets')
    rows = None
    if buckets is not None:
        rows, seen = [], set()
        for b in buckets:
            date, tokens = b['startDate'], b['tokens']
            datetime.date.fromisoformat(date)
            if date in seen or isinstance(tokens, bool) or not isinstance(tokens, int) or tokens < 0:
                raise ValueError('Invalid daily bucket')
            seen.add(date)
            rows.append({'date': date, 'tokens': tokens})
        rows.sort(key=lambda b: b['date'])
    summary = {k: usage.get('summary', {}).get(k) for k in (
        'lifetimeTokens', 'peakDailyTokens', 'longestRunningTurnSec', 'currentStreakDays', 'longestStreakDays')}
    observed = _integer(raw['observedAt'], 'observation time', 1, MAX_TIMESTAMP)
    return dict(schemaVersion=1, source='agent_usage', provider='Codex', observedAt=observed,
                chartMode='live', quota=dict(remaining=weekly['remaining'] if weekly else None,
                resetsAt=weekly['resetsAt'] if weekly else None, window='weekly',
                observedAt=observed, source='desktop_codex_app_server'), rateLimits=windows,
                daily=dict(observedAt=observed, source='account/usage/read',
                           dateSemantics='upstream startDate unchanged; missing dates unknown', buckets=rows),
                summary=summary, resetCards=normalize_reset_cards(limits, observed))

def fetch():
    exe = find_codex()
    proc = subprocess.Popen([str(exe), 'app-server', '--stdio'], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding='utf-8')
    inbox = queue.Queue()
    def reader():
        for line in proc.stdout:
            try: inbox.put(json.loads(line))
            except ValueError: pass
    threading.Thread(target=reader, daemon=True).start()
    def request(i, method, params=None):
        msg = dict(id=i, method=method)
        if params is not None: msg['params'] = params
        proc.stdin.write(json.dumps(msg)+'\n'); proc.stdin.flush()
        deadline = time.monotonic()+45
        while time.monotonic() < deadline:
            try: response = inbox.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None: raise RuntimeError('Codex app-server exited')
                continue
            if response.get('id') == i: return response
        raise TimeoutError(method)
    try:
        initialized = request(1, 'initialize', {'clientInfo':{'name':'codex_widget_export', 'version':'1.0'}, 'capabilities':{'experimentalApi':True}})
        if 'error' in initialized: raise RuntimeError('Codex initialize failed')
        proc.stdin.write('{"method":"initialized"}\n'); proc.stdin.flush()
        return dict(observedAt=int(time.time()), usage=request(2,'account/usage/read'), limits=request(3,'account/rateLimits/read'))
    finally:
        proc.terminate()
        try: proc.wait(timeout=3)
        except subprocess.TimeoutExpired: proc.kill(); proc.wait()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture', type=Path, help='Offline validation only; never scheduled')
    parser.add_argument('--output', type=Path, help='Local normalized JSON destination')
    args = parser.parse_args()
    result = normalize(json.loads(args.fixture.read_text(encoding='utf-8')) if args.fixture else fetch())
    if args.fixture and args.output:
        parser.error('--fixture cannot be combined with --output; test data stays in fixture-usage.json')
    destination = args.output.expanduser() if args.output else ROOT/('fixture-usage.json' if args.fixture else 'usage-latest.json')
    from snapshot_schema import write_snapshot_atomic
    write_snapshot_atomic(destination, result)
    print(json.dumps({'validated':True, 'delivery':'local_only_not_sent'}))

if __name__ == '__main__': main()
