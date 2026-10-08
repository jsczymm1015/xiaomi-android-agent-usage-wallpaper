#!/bin/bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 -X utf8 "$project_dir/desktop/github_relay.py" "$@"
