"""Build and optionally run a separate, sanitized documentation screenshot test APK.

The installed application is not rebuilt or replaced. The test APK must be signed
with the same certificate as the application. Only five fixed PNG files are read
back; credentials, snapshots, preferences and logs are never exported.
"""
import argparse
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import tempfile
import threading
import time
import zipfile

from build_android import ROOT, java_tool, run, sdk_default

TEST_PACKAGE = 'local.codex.wallpapers.documentation'
TARGET_PACKAGE = 'local.codex.wallpapers'
RUNNER = TEST_PACKAGE + '/local.codex.wallpapers.DocumentationInstrumentation'
IMAGES = ('theme-m5.png', 'theme-l1d.png', 'theme-l3d.png',
          'desktop-widget.png', 'cloud-sync.png')
LAUNCH_COMPONENTS = {
    'launch_main': TARGET_PACKAGE + '/local.codex.wallpapers.MainActivity',
    'launch_widget': TARGET_PACKAGE + '/local.codex.wallpapers.UsageWidgetConfigActivity',
    'launch_cloud': TARGET_PACKAGE + '/local.codex.wallpapers.UsageCloudActivity',
}
STAGES = frozenset((*LAUNCH_COMPONENTS, 'launch', 'main_ready', 'theme_0', 'theme_1',
                    'theme_2', 'widget_ready', 'cloud_ready', 'finished'))
MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="local.codex.wallpapers.documentation" android:versionCode="1" android:versionName="1">
 <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="35" />
 <application android:label="CodeX documentation test" android:allowBackup="false" android:debuggable="true" />
 <instrumentation android:name="local.codex.wallpapers.DocumentationInstrumentation"
 android:targetPackage="local.codex.wallpapers" android:functionalTest="true" />
</manifest>'''


def build(sdk, keystore, key_alias=None, target_package='local.codex.wallpapers', runner_class='DocumentationInstrumentation'):
    tools = sdk.expanduser().resolve() / 'build-tools/35.0.0'
    android = sdk.expanduser().resolve() / 'platforms/android-35/android.jar'
    suffix = '.exe' if os.name == 'nt' else ''
    required = [tools / ('aapt2' + suffix), tools / ('zipalign' + suffix),
                tools / 'lib/d8.jar', tools / 'lib/apksigner.jar', android]
    if any(not path.is_file() for path in required):
        raise RuntimeError('Android 35 platform and build-tools 35.0.0 are required')
    if not keystore.is_file():
        raise RuntimeError('Existing application signing keystore is required')
    if not os.environ.get('CODEX_KEYSTORE_PASSWORD'):
        raise RuntimeError('Set CODEX_KEYSTORE_PASSWORD without putting it in command arguments')
    out = ROOT / 'build'
    out.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='documentation-', dir=out) as temporary:
        staging = Path(temporary)
        production, classes, dex = (staging / name for name in ('production', 'classes', 'dex'))
        for directory in (production, classes, dex):
            directory.mkdir()
        # Compile application sources as a classpath, but never package them.
        run([java_tool('javac'), '-encoding', 'UTF-8', '--release', '8', '-classpath', android,
             '-d', production, *sorted((ROOT / 'android/src').rglob('*.java'))])
        run([java_tool('javac'), '-encoding', 'UTF-8', '--release', '8',
             '-classpath', str(android) + os.pathsep + str(production), '-d', classes,
             ROOT / ('tests/android/'+runner_class+'.java')])
        run([java_tool('java'), '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8',
             '--min-api', '30', '--lib', android, '--classpath', production,
             '--output', dex, *sorted(classes.rglob('*.class'))])
        manifest = staging / 'AndroidManifest.xml'
        test_manifest = MANIFEST.replace('local.codex.wallpapers.documentation', target_package+'.documentation').replace('android:targetPackage="local.codex.wallpapers"', 'android:targetPackage="'+target_package+'"').replace('local.codex.wallpapers.DocumentationInstrumentation', 'local.codex.wallpapers.'+runner_class)
        manifest.write_text(test_manifest, encoding='utf-8')
        unsigned, aligned, signed = (staging / name for name in ('unsigned.apk', 'aligned.apk', 'signed.apk'))
        run([tools / ('aapt2' + suffix), 'link', '-I', android, '--manifest', manifest, '-o', unsigned])
        with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(dex.glob('*.dex')):
                archive.write(file, file.name)
        run([tools / ('zipalign' + suffix), '-f', '4', unsigned, aligned])
        command = [java_tool('java'), '-jar', tools / 'lib/apksigner.jar', 'sign',
                   '--ks', keystore.resolve(), '--ks-pass', 'env:CODEX_KEYSTORE_PASSWORD']
        if key_alias:
            command += ['--ks-key-alias', key_alias]
        if os.environ.get('CODEX_KEY_PASSWORD'):
            command += ['--key-pass', 'env:CODEX_KEY_PASSWORD']
        run([*command, '--out', signed, aligned])
        run([java_tool('java'), '-jar', tools / 'lib/apksigner.jar', 'verify', signed])
        destination = out / (runner_class+'.apk')
        shutil.copy2(signed, destination)
    return destination


def capture(apk, adb, serial, output):
    if not serial:
        raise RuntimeError('--serial is required when running on a device')
    base = [str(adb), '-s', serial]
    subprocess.run([*base, 'install', '-r', str(apk)], check=True)
    try:
        # Delete only previous documentation output to prevent stale captures.
        subprocess.run([*base, 'exec-out', 'run-as', TARGET_PACKAGE, 'rm', '-f',
                        *('files/documentation/' + name for name in IMAGES)], check=True)
        instrument(base)
        output.mkdir(parents=True, exist_ok=True)
        for name in IMAGES:
            image = subprocess.run([*base, 'exec-out', 'run-as', TARGET_PACKAGE, 'cat',
                                    'files/documentation/' + name], check=True, capture_output=True).stdout
            if not image.startswith(b'\x89PNG\r\n\x1a\n'):
                raise RuntimeError('Documentation capture is not a PNG: ' + name)
            (output / name).write_bytes(image)
    finally:
        subprocess.run([*base, 'exec-out', 'am', 'start', '-n', TARGET_PACKAGE + '/local.codex.wallpapers.MainActivity'],
                       check=False, stdout=subprocess.DEVNULL)
        subprocess.run([*base, 'uninstall', TEST_PACKAGE], check=False)
    print('Captured five sanitized screenshots: ' + str(output))


def instrument(base):
    """Launch fixed components only after the device runner registers its monitor."""
    process = subprocess.Popen([*base, 'exec-out', 'am', 'instrument', '-w', RUNNER],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8', errors='replace')
    lines = queue.Queue()

    def read_stdout():
        try:
            for line in process.stdout:
                lines.put(line)
        except (OSError, ValueError):
            pass
        finally:
            lines.put(None)

    reader = threading.Thread(target=read_stdout, name='documentation-status', daemon=True)
    reader.start()
    deadline = time.monotonic() + 180
    stage, failure_type = 'launch', None
    captured, successful_code = False, False
    launched = set()
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise subprocess.TimeoutExpired('documentation instrumentation', 180)
            try:
                line = lines.get(timeout=min(1, remaining))
            except queue.Empty:
                continue
            if line is None:
                break
            line = line.strip()
            match = re.fullmatch(r'INSTRUMENTATION_(?:STATUS|RESULT): (stage|failureType)=([A-Za-z0-9_]+)', line)
            if match:
                key, value = match.groups()
                if key == 'stage' and value in STAGES:
                    stage = value
                    if value in LAUNCH_COMPONENTS and value not in launched:
                        launched.add(value)
                        # Neither component names nor launch flags come from user input.
                        started = subprocess.run([*base, 'exec-out', 'am', 'start', '-W',
                                                  '-f', '0x18000000', '-n', LAUNCH_COMPONENTS[value]],
                                                 check=False, capture_output=True,
                                                 timeout=min(15, max(.1, deadline - time.monotonic())))
                        if started.returncode:
                            raise subprocess.CalledProcessError(started.returncode, 'documentation activity launch')
                elif key == 'failureType':
                    failure_type = value
            elif line == 'INSTRUMENTATION_RESULT: captured=5':
                captured = True
            elif line == 'INSTRUMENTATION_CODE: -1':
                successful_code = True
        process.wait(timeout=max(.1, deadline - time.monotonic()))
        if process.returncode or failure_type or not captured or not successful_code:
            raise RuntimeError('InstrumentationResultError')
    except BaseException as failure:
        if process.poll() is None:
            process.kill()
        process.wait(timeout=5)
        exception_type = failure_type or type(failure).__name__
        raise RuntimeError('Documentation capture failed; stage=' + stage +
                           ', exceptionType=' + exception_type) from None
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=5)
        reader.join(timeout=1)
        process.stdout.close()


def diagnostic(output):
    """Return only fixed stage names and exception classes, never raw device output."""
    if isinstance(output, bytes):
        output = output.decode('utf-8', errors='replace')
    safe = []
    for line in (output or '').splitlines():
        match = re.fullmatch(r'INSTRUMENTATION_(?:STATUS|RESULT): (stage|failureType)=([A-Za-z0-9_]+)', line.strip())
        if match and (match.group(1) != 'stage' or match.group(2) in STAGES):
            safe.append(match.group(1) + '=' + match.group(2))
    return ', '.join(safe[-16:]) or 'no stage or failureType reported'


def configure_target(package):
    global TARGET_PACKAGE, TEST_PACKAGE, RUNNER, LAUNCH_COMPONENTS
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', package):
        raise ValueError('Invalid target package')
    TARGET_PACKAGE=package
    TEST_PACKAGE=package+'.documentation'
    RUNNER=TEST_PACKAGE+'/local.codex.wallpapers.DocumentationInstrumentation'
    LAUNCH_COMPONENTS={stage:package+'/local.codex.wallpapers.'+name for stage,name in (('launch_main','MainActivity'),('launch_widget','UsageWidgetConfigActivity'),('launch_cloud','UsageCloudActivity'))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=sdk_default())
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--key-alias')
    parser.add_argument('--adb', type=Path)
    parser.add_argument('--serial')
    parser.add_argument('--output', type=Path, default=ROOT / '.local/documentation')
    parser.add_argument('--target-package', default=TARGET_PACKAGE)
    parser.add_argument('--run', action='store_true', help='Install, capture and remove only the test APK')
    args = parser.parse_args()
    configure_target(args.target_package)
    apk = build(args.sdk, args.keystore.expanduser(), args.key_alias, args.target_package)
    print('Built separate documentation test APK: ' + str(apk))
    if args.run:
        adb = args.adb or shutil.which('adb') or args.sdk / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
        capture(apk, adb, args.serial, args.output.expanduser().resolve())


if __name__ == '__main__':
    main()
