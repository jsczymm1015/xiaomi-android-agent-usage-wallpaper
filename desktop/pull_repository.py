"""Fast-forward an existing, user-configured code checkout without running its code.

This computer-side helper is suitable for a user-created scheduled task. It does
not clone a default repository, install dependencies, build an APK, run scripts,
configure credentials or resolve local changes/divergence.
"""
import argparse
import json
from pathlib import Path
import re
import tempfile

from publish_snapshot import _git, _remote, _clean, _ref


def pull_repository(repo_dir, remote='origin', branch=None):
    root = Path(repo_dir).expanduser().resolve()
    if not root.is_dir():
        raise ValueError('The code repository directory does not exist')
    if Path(_git(root, 'rev-parse', '--show-toplevel').stdout.strip()).resolve() != root:
        raise ValueError('Pass the root directory of the existing code repository')
    if not isinstance(remote, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,99}', remote):
        raise ValueError('Pass a configured Git remote name, not a URL or credential')
    _remote(root, remote)
    # status/checkout can invoke user-configured content filters. Refuse those
    # checkouts instead of running an LFS/custom clean/smudge/process program.
    filters = _git(root, 'config', '--get-regexp', r'^filter\..*\.(process|smudge|clean)$', allowed=(0, 1)).stdout
    if any(len(line.split(None, 1)) == 2 and line.split(None, 1)[1] for line in filters.splitlines()):
        raise RuntimeError('This checkout uses Git content filters; review and pull it manually without this no-code-execution helper')
    monitor = _git(root, 'config', '--get', 'core.fsmonitor', allowed=(0, 1)).stdout.strip().lower()
    if monitor and monitor not in {'true', 'false', '0', '1'}:
        raise RuntimeError('This checkout uses an external file monitor; review and pull it manually')
    try:
        _clean(root)
    except RuntimeError:
        raise RuntimeError('The code checkout must be clean before pulling; existing local files were preserved') from None
    current = _git(root, 'symbolic-ref', '--quiet', '--short', 'HEAD').stdout.strip()
    branch = current if branch is None else branch
    reference = _ref(branch)
    _git(root, 'check-ref-format', '--branch', branch)
    if branch != current:
        raise RuntimeError('Check out the requested code branch before pulling')
    before = _git(root, 'rev-parse', '--verify', 'HEAD').stdout.strip()
    # An empty hook directory is a temporary per-command setting, never a change
    # to the user's repository config. Do not invoke post-merge/reference hooks.
    with tempfile.TemporaryDirectory(prefix='agent-pull-no-hooks-') as hooks:
        options = ('-c', 'core.hooksPath=' + hooks, '-c', 'submodule.recurse=false')
        _git(root, *options, 'fetch', '--no-tags', remote, reference)
        if _git(root, 'merge-base', '--is-ancestor', 'HEAD', 'FETCH_HEAD', allowed=(0, 1)).returncode != 0:
            raise RuntimeError('The code branch has unpublished commits or diverged; reconcile it yourself before pulling')
        fetched = _git(root, 'rev-parse', 'FETCH_HEAD').stdout.strip()
        if before != fetched:
            _git(root, *options, 'merge', '--ff-only', 'FETCH_HEAD')
    after = _git(root, 'rev-parse', 'HEAD').stdout.strip()
    _clean(root)
    rows = _git(root, 'ls-remote', '--exit-code', remote, reference).stdout.splitlines()
    if len(rows) != 1 or rows[0].split() != [after, reference]:
        raise RuntimeError('Remote code commit verification failed; inspect the branch before retrying')
    return {'updated': before != after, 'commit': after, 'branch': branch,
            'remoteVerified': True, 'codeExecuted': False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-dir', required=True, type=Path, help='Existing code repository clone chosen by the user')
    parser.add_argument('--remote', default='origin', help='Existing Git remote name')
    parser.add_argument('--branch', help='Current checked-out branch; defaults to its name')
    args = parser.parse_args(argv)
    try:
        report = pull_repository(args.repo_dir, args.remote, args.branch)
    except (ValueError, RuntimeError) as failure:
        raise SystemExit(str(failure)) from None
    except OSError:
        raise SystemExit('Code pull failed while accessing local files; no force update was used') from None
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
