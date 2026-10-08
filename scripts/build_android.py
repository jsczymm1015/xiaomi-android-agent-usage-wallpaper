"""Build the Android app on macOS/Windows/Linux using an external Android SDK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import zipfile
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def sdk_default(system=None, env=None, home=None):
    env = os.environ if env is None else env
    home = Path.home() if home is None else Path(home)
    if env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT'):
        return Path(env.get('ANDROID_HOME') or env['ANDROID_SDK_ROOT']).expanduser()
    system = system or platform.system()
    if system == 'Darwin':
        return home/'Library/Android/sdk'
    if system == 'Windows':
        return Path(env.get('LOCALAPPDATA', str(home/'AppData/Local')))/'Android/Sdk'
    return home/'Android/Sdk'


def java_tool(name):
    suffix = '.exe' if os.name == 'nt' else ''
    if os.environ.get('JAVA_HOME'):
        candidate = Path(os.environ['JAVA_HOME'])/'bin'/(name+suffix)
        if candidate.is_file():
            return str(candidate)
    found = shutil.which(name)
    if not found:
        raise RuntimeError('JDK 17+ required: missing '+name)
    return found


def run(args, env=None):
    # Windows SDK native tools cannot reliably open absolute Unicode paths.
    # Relative paths also keep the command usable from a checkout with spaces.
    command = [str(args[0])]
    for arg in args[1:]:
        if isinstance(arg, Path):
            try:
                arg = os.path.relpath(arg, ROOT)
            except ValueError:  # An external key/JDK on a different Windows drive.
                arg = str(arg)
        command.append(str(arg))
    subprocess.run(command, check=True, cwd=ROOT, env=env)


def verify_assets():
    expected = json.loads((ROOT/'android/assets-manifest.json').read_text(encoding='utf-8'))
    actual = {p.relative_to(ROOT).as_posix() for p in (ROOT/'android/assets').rglob('*') if p.is_file()}
    if actual != {item['path'] for item in expected}:
        raise RuntimeError('Asset list mismatch')
    for item in expected:
        if hashlib.sha256((ROOT/item['path']).read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError('Asset hash mismatch: '+item['path'])
    return actual


def variant_manifest(destination, application_id=None, debuggable=False):
    """Change only package identity for an isolated QA build; Java names stay fixed."""
    android = '{http://schemas.android.com/apk/res/android}'
    ET.register_namespace('android', android[1:-1])
    tree = ET.parse(ROOT/'android/AndroidManifest.xml')
    manifest = tree.getroot()
    original = manifest.attrib['package']
    package = application_id or original
    if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*)+', package):
        raise ValueError('Invalid application ID')
    manifest.set('package', package)
    application = manifest.find('application')
    application.set(android+'debuggable', str(bool(debuggable)).lower())
    if package != original:
        for element in application:
            name = element.get(android+'name', '')
            if name.startswith('.'):
                element.set(android+'name', original+name)
            if element.tag == 'provider':
                element.set(android+'authorities', package+'.usage')
    tree.write(destination, encoding='utf-8', xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=sdk_default())
    parser.add_argument('--build-tools', default='35.0.0')
    parser.add_argument('--build-tools-dir', type=Path, help='Optional unpacked SDK tools directory')
    parser.add_argument('--android-jar', type=Path, help='Optional external platform android.jar')
    parser.add_argument('--keystore', type=Path, help='Existing private key for compatible upgrades')
    parser.add_argument('--key-alias', help='Optional alias in an existing key store')
    parser.add_argument('--application-id', help='Separate package identity for QA only')
    parser.add_argument('--debuggable', action='store_true', help='Opt-in debug build; release defaults to false')
    parser.add_argument('--output', type=Path, default=ROOT/'build/CodeX.apk')
    parser.add_argument('--check', action='store_true', help='Validate prerequisites only')
    args = parser.parse_args()
    tools = (args.build_tools_dir or args.sdk/'build-tools'/args.build_tools).expanduser().resolve()
    android = (args.android_jar or args.sdk/'platforms/android-35/android.jar').expanduser().resolve()
    suffix = '.exe' if os.name == 'nt' else ''
    required = [tools/('aapt2'+suffix), tools/('zipalign'+suffix), tools/'lib/d8.jar', tools/'lib/apksigner.jar', android]
    for path in required:
        if not path.is_file():
            raise RuntimeError('Missing SDK file: '+str(path))
    java, javac = java_tool('java'), java_tool('javac')
    assets = verify_assets()
    if args.check:
        print('Prerequisites and '+str(len(assets))+' assets verified. Host: '+platform.system())
        return
    out = ROOT/'build'
    out.mkdir(exist_ok=True)
    key = args.keystore.expanduser().resolve() if args.keystore else ROOT/'.local/debug.keystore'
    signing_env = os.environ.copy()
    if args.keystore:
        if not key.is_file():
            raise RuntimeError('Existing keystore not found')
        if not signing_env.get('CODEX_KEYSTORE_PASSWORD'):
            raise RuntimeError('Set CODEX_KEYSTORE_PASSWORD for the existing private key')
    else:
        key.parent.mkdir(exist_ok=True)
        signing_env['CODEX_KEYSTORE_PASSWORD'] = 'android'
        if not key.exists():
            run([java_tool('keytool'), '-genkeypair', '-keystore', key, '-alias', 'androiddebugkey',
                 '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048',
                 '-validity', '10000', '-dname', 'CN=Android Debug,O=Android,C=US'])
    # A new temporary workspace prevents stale classes/resources from entering the APK.
    with tempfile.TemporaryDirectory(prefix='android-', dir=out) as staging:
        staging = Path(staging)
        classes, dex = staging/'classes', staging/'dex'
        classes.mkdir(); dex.mkdir()
        sources = sorted((ROOT/'android/src').rglob('*.java'))
        run([javac, '-encoding', 'UTF-8', '--release', '8', '-classpath', android, '-d', classes, *sources])
        run([java, '-cp', tools/'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '30',
             '--lib', android, '--output', dex, *sorted(classes.rglob('*.class'))])
        resources, unsigned, aligned = staging/'resources.zip', staging/'unsigned.apk', staging/'aligned.apk'
        manifest = staging/'AndroidManifest.xml'
        variant_manifest(manifest, args.application_id, args.debuggable)
        run([tools/('aapt2'+suffix), 'compile', '--dir', ROOT/'android/res', '-o', resources])
        run([tools/('aapt2'+suffix), 'link', '-I', android, '--manifest', manifest,
             '-A', ROOT/'android/assets', resources, '-o', unsigned])
        with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(dex.glob('*.dex')):
                archive.write(file, file.name)
        run([tools/('zipalign'+suffix), '-f', '4', unsigned, aligned])
        candidate = staging/'CodeX.apk'
        command = [java, '-jar', tools/'lib/apksigner.jar', 'sign', '--ks', key,
                   '--ks-pass', 'env:CODEX_KEYSTORE_PASSWORD']
        if args.key_alias:
            command += ['--ks-key-alias', args.key_alias]
        if signing_env.get('CODEX_KEY_PASSWORD'):
            command += ['--key-pass', 'env:CODEX_KEY_PASSWORD']
        run([*command, '--out', candidate, aligned], signing_env)
        run([java, '-jar', tools/'lib/apksigner.jar', 'verify', '--verbose', candidate])
        with zipfile.ZipFile(candidate) as archive:
            packaged = {n for n in archive.namelist() if n.startswith('assets/')}
            assert packaged == {p.removeprefix('android/') for p in assets}
        destination = args.output.expanduser().resolve()
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(candidate, destination)
    print('Built and verified: '+str(destination))


if __name__ == '__main__':
    main()
