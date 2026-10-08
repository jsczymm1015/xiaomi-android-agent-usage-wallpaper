"""Publish one validated JSON file to a user-configured Git data repository.

Uses the user's existing Git authentication. Never creates repositories, changes
visibility, configures credentials, rewrites history or publishes app source.
"""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import urllib.parse

from snapshot_schema import load_snapshot, snapshot_bytes, write_snapshot_atomic

APP_ROOT = Path(__file__).resolve().parents[1]


def _git(directory, *args, allowed=(0,)):
    env = os.environ.copy()
    env['GIT_TERMINAL_PROMPT'] = '0'
    env['GCM_INTERACTIVE'] = 'never'
    try:
        result = subprocess.run(['git', '-C', str(directory), *args], capture_output=True,
                                text=True, encoding='utf-8', env=env, timeout=120)
    except (OSError, subprocess.TimeoutExpired):
        raise RuntimeError('Git could not complete; check Git installation, connectivity and authorization') from None
    if result.returncode not in allowed:
        # Git stderr can include credential-bearing remote URLs. Keep it local and
        # provide a safe operation label rather than emitting raw process output.
        action = args[0] if args else 'operation'
        raise RuntimeError('Git ' + action + ' failed; check authorization, branch state and repository configuration')
    return result


def _target(root, name):
    if not isinstance(name, str) or not name or len(name) > 1024 or '\\' in name or any(ord(c) < 32 or ord(c) == 127 for c in name):
        raise ValueError('The destination must be a relative JSON path inside the data repository')
    path = PurePosixPath(name)
    if path.is_absolute() or name.startswith('/') or any(part in ('', '.', '..', '.git') for part in name.split('/')) or path.suffix.lower() != '.json':
        raise ValueError('The destination must be a relative JSON path inside the data repository')
    destination = root.joinpath(*path.parts)
    current = root
    for part in path.parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Symbolic links are not allowed in the destination path')
    if not destination.resolve().is_relative_to(root):
        raise ValueError('The destination escapes the data repository')
    if destination.exists() and not destination.is_file():
        raise ValueError('The destination is not a regular JSON file')
    return destination, path.as_posix()


def _remote_url(url):
    """Validate without returning or displaying a credential-bearing address."""
    if not url or any(ord(c) < 32 or ord(c) == 127 for c in url):
        raise ValueError('The Git remote address is invalid')
    if '://' not in url:
        # Local paths (including Windows drive paths) and scp-style SSH remotes.
        if '@' in url:
            if not re.fullmatch(r'[A-Za-z0-9._-]+@[A-Za-z0-9._-]+:[^\s]+', url):
                raise ValueError('Git remote credentials must use Git authorization, never an embedded password')
        elif url.startswith('ext::'):
            raise ValueError('External command Git transports are not supported')
        return
    try:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme not in {'https', 'ssh', 'file'} or parsed.fragment or parsed.password is not None:
            raise ValueError('unsafe remote')
        if parsed.scheme != 'file' and not parsed.hostname:
            raise ValueError('unsafe remote')
        if parsed.scheme != 'ssh' and parsed.username is not None:
            raise ValueError('unsafe remote')
        if parsed.port == 0:
            raise ValueError('unsafe remote')
        for key, _ in urllib.parse.parse_qsl(parsed.query, keep_blank_values=True):
            key = key.lower().replace('-', '_')
            if any(part in key for part in ('token', 'password', 'secret', 'signature', 'credential')) or key in {'key', 'api_key', 'apikey', 'sig', 'auth', 'authorization'}:
                raise ValueError('unsafe remote')
    except (ValueError, UnicodeError):
        raise ValueError('Git remote credentials must use Git authorization; embedded passwords or sensitive URL parameters are refused') from None


def _remote(root, name):
    fetch = _git(root, 'remote', 'get-url', '--all', name).stdout.splitlines()
    push = _git(root, 'remote', 'get-url', '--push', '--all', name).stdout.splitlines()
    if len(fetch) != 1 or len(push) != 1:
        raise ValueError('Use one fetch and one push address for the data repository')
    _remote_url(fetch[0]); _remote_url(push[0])
    if fetch[0] != push[0]:
        raise ValueError('The data repository fetch and push addresses must match')


def _clean(root):
    if _git(root, 'status', '--porcelain=v1', '--untracked-files=all').stdout:
        raise RuntimeError('The data repository has local changes; commit or move them before publishing')


def _ref(branch):
    if not isinstance(branch, str) or not branch or branch.startswith('-') or any(ord(c) < 32 or ord(c) == 127 for c in branch):
        raise ValueError('Invalid Git branch')
    return 'refs/heads/' + branch


def _reject_app_repository(root):
    if root == APP_ROOT or root.is_relative_to(APP_ROOT):
        raise RuntimeError('Use a separate data repository; publishing statistics to this app source repository is refused')
    # The same app can be cloned elsewhere under any directory/repository name.
    for marker in ('android/src/local/codex/wallpapers/UsageCloud.java', 'android/AndroidManifest.xml'):
        if _git(root, 'ls-files', '--error-unmatch', '--', marker, allowed=(0, 1)).returncode == 0:
            raise RuntimeError('Use a separate data repository; publishing statistics to app source is refused')


def publish_snapshot(repo_dir, input_path, file='usage-latest.json', remote='origin', branch=None):
    """Fast-forward, write/commit only the requested JSON, push and verify its SHA.

    A clean, initialized branch is required. Existing local unpublished commits
    are refused so this tool cannot push unrelated work on the user's behalf.
    A push race leaves the new local commit available for normal reconciliation.
    """
    data = load_snapshot(input_path)
    root = Path(repo_dir).expanduser().resolve()
    if not root.is_dir():
        raise ValueError('The data repository directory does not exist')
    actual = Path(_git(root, 'rev-parse', '--show-toplevel').stdout.strip()).resolve()
    if actual != root:
        raise ValueError('Pass the root directory of the data repository')
    _reject_app_repository(root)
    destination, relative = _target(root, file)
    if not isinstance(remote, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,99}', remote):
        raise ValueError('Pass a configured Git remote name, not a URL or credential')
    _remote(root, remote)
    _clean(root)
    current = _git(root, 'symbolic-ref', '--quiet', '--short', 'HEAD').stdout.strip()
    branch = current if branch is None else branch
    reference = _ref(branch)
    _git(root, 'check-ref-format', '--branch', branch)
    if branch != current:
        raise RuntimeError('Check out the requested data branch before publishing')
    if _git(root, 'rev-parse', '--verify', 'HEAD', allowed=(0, 128)).returncode != 0:
        raise RuntimeError('Initialize and push the data repository with at least one commit before publishing')
    _git(root, 'fetch', '--no-tags', remote, reference)
    if _git(root, 'merge-base', '--is-ancestor', 'HEAD', 'FETCH_HEAD', allowed=(0, 1)).returncode != 0:
        raise RuntimeError('The data branch contains unpublished commits or diverged; reconcile it before publishing')
    _git(root, 'merge', '--ff-only', 'FETCH_HEAD')
    _clean(root)
    # Recheck after the fetch: a fast-forward may have introduced a symlink or app source.
    _reject_app_repository(root)
    destination, relative = _target(root, file)
    if destination.exists():
        try:
            previous = load_snapshot(destination)
        except (ValueError, OSError):
            raise ValueError('The existing data file is invalid; review it before publishing a replacement') from None
        if previous['provider'] == data['provider'] and data['observedAt'] < previous['observedAt']:
            raise ValueError('The new statistics are older than the existing provider snapshot; the previous data was preserved')
    content = snapshot_bytes(data)
    changed = not destination.exists() or destination.read_bytes() != content
    if changed:
        write_snapshot_atomic(destination, data)
        _git(root, 'add', '--', relative)
        staged = _git(root, 'diff', '--cached', '--name-only', '-z').stdout.split('\0')
        if [name for name in staged if name] != [relative]:
            raise RuntimeError('Unexpected staged files; publishing stopped without a commit')
        _git(root, 'commit', '-m', 'Update agent usage snapshot')
        changed_files = _git(root, 'diff-tree', '--no-commit-id', '--name-only', '-r', '-z', 'HEAD').stdout.split('\0')
        if [name for name in changed_files if name] != [relative]:
            raise RuntimeError('Unexpected commit contents; publishing stopped before pushing')
        if _git(root, 'show', 'HEAD:' + relative).stdout.encode('utf-8') != content:
            raise RuntimeError('Committed statistics differ from validated content; publishing stopped before pushing')
        _git(root, 'push', '--porcelain', remote, 'HEAD:' + reference)
    local_sha = _git(root, 'rev-parse', 'HEAD').stdout.strip()
    remote_rows = _git(root, 'ls-remote', '--exit-code', remote, reference).stdout.splitlines()
    if len(remote_rows) != 1 or remote_rows[0].split() != [local_sha, reference]:
        raise RuntimeError('Remote commit verification failed; inspect the data branch before retrying')
    return {'observedAt': data['observedAt'], 'commit': local_sha, 'branch': branch,
            'file': relative, 'changed': changed, 'remoteVerified': True,
            'phoneReceiptVerified': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-dir', required=True, type=Path, help='Separate, initialized data repository clone')
    parser.add_argument('--input', required=True, type=Path, help='Validated aggregate usage JSON')
    parser.add_argument('--file', default='usage-latest.json', help='Relative JSON path inside the data repository')
    parser.add_argument('--remote', default='origin', help='Existing Git remote name')
    parser.add_argument('--branch', help='Current checked-out branch; defaults to its name')
    args = parser.parse_args(argv)
    try:
        report = publish_snapshot(args.repo_dir, args.input, args.file, args.remote, args.branch)
    except (ValueError, RuntimeError) as failure:
        raise SystemExit(str(failure)) from None
    except OSError:
        raise SystemExit('Publish failed while reading or writing local data; no force push was used') from None
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
