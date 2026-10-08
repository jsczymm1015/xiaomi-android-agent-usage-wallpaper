"""Bundle the allowlisted, credential-free desktop tools inside the APK."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    'desktop/export_usage.py', 'desktop/runtime_paths.py',
    'desktop/snapshot_schema.py', 'desktop/publish_snapshot.py', 'desktop/pull_snapshot.py',
    'docs/USAGE.md', 'docs/REPOSITORY_SETUP.md', 'docs/FEATURES.md',
    'docs/COMPATIBILITY.md', 'docs/MACOS.md', 'docs/VERIFICATION.md', 'docs/SCREENSHOTS.md',
    'docs/screenshots/theme-m5.png', 'docs/screenshots/theme-l1d.png',
    'docs/screenshots/theme-l3d.png', 'docs/screenshots/desktop-widget.png',
    'docs/screenshots/cloud-sync.png',
    'prompts/scheduled-usage-task.txt', 'examples/usage-example.json',
)


def package():
    target = ROOT/'android/assets/setup/agent-tools.zip'
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in FILES:
            path = ROOT/name
            if not path.is_file() or path.is_symlink():
                raise RuntimeError('Missing or unsafe setup resource: '+name)
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, path.read_bytes())
    assets = [{'path': p.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
              for p in sorted((ROOT/'android/assets').rglob('*')) if p.is_file()]
    (ROOT/'android/assets-manifest.json').write_text(json.dumps(assets, indent=2)+'\n', encoding='utf-8')
    print('Bundled '+str(len(FILES))+' setup resources; '+str(len(assets))+' assets recorded')


if __name__ == '__main__':
    package()
