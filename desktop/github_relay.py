"""Collect Codex usage and publish to an explicitly selected Git clone."""
import argparse
import json
import tempfile
from pathlib import Path
from export_usage import fetch, normalize
from snapshot_schema import write_snapshot_atomic
from publish_snapshot import publish_snapshot


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-dir', type=Path, required=True)
    parser.add_argument('--input', type=Path, help='Existing snapshot; otherwise read local Codex')
    parser.add_argument('--file', default='usage-latest.json')
    parser.add_argument('--remote', default='origin')
    parser.add_argument('--branch')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='agent-usage-') as staging:
        source = args.input
        if source is None:
            source = Path(staging)/'snapshot.json'
            write_snapshot_atomic(source, normalize(fetch()))
        report = publish_snapshot(args.repo_dir, source, file=args.file, remote=args.remote, branch=args.branch)
        print(json.dumps(report))


if __name__ == '__main__':
    main()
