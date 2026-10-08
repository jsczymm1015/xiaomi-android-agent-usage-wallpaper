import os,tempfile,unittest
from pathlib import Path
from runtime_paths import find_codex,data_dir
from build_android import sdk_default,verify_assets,variant_manifest
import xml.etree.ElementTree as ET

class PlatformTests(unittest.TestCase):
    def test_mac_data_path(self):self.assertEqual(data_dir('Darwin',{},'/Users/example'),Path('/Users/example/Library/Application Support/CodeXUsageSync'))
    def test_mac_sdk_path(self):self.assertEqual(sdk_default('Darwin',{},'/Users/example'),Path('/Users/example/Library/Android/sdk'))
    def test_windows_data_without_environment(self):self.assertEqual(data_dir('Windows',{},'/test'),Path('/test/AppData/Local/CodeXUsageSync'))
    def test_sdk_override(self):self.assertEqual(sdk_default('Darwin',{'ANDROID_HOME':'/custom-sdk'},'/test'),Path('/custom-sdk'))
    def test_data_override(self):self.assertEqual(data_dir('Darwin',{'CODEX_USAGE_DATA_DIR':'/custom-data'},'/test'),Path('/custom-data'))
    def test_explicit_binary(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe=Path(tmp)/'codex';exe.touch();self.assertEqual(find_codex('Darwin',{'CODEX_BINARY':str(exe)},tmp,lambda _:None),exe)
    def test_bad_override_fails_without_fallback(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(RuntimeError):find_codex('Darwin',{'CODEX_BINARY':str(Path(tmp)/'absent')},tmp,lambda _: 'wrong')
    def test_mac_user_app(self):
        with tempfile.TemporaryDirectory() as tmp:
            exe=Path(tmp)/'Applications/Codex.app/Contents/Resources/codex';exe.parent.mkdir(parents=True);exe.touch()
            self.assertEqual(find_codex('Darwin',{},tmp,lambda _:None),exe)
    def test_path_fallback(self):self.assertEqual(find_codex('Linux',{},'/test',lambda _: '/usr/local/bin/codex'),Path('/usr/local/bin/codex'))
    def test_missing_runtime_is_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(RuntimeError):find_codex('Windows',{},tmp,lambda _:None)
    def test_runtime_assets(self):
        assets=verify_assets()
        self.assertIn('android/assets/setup/agent-tools.zip',assets)
        self.assertEqual(len([p for p in assets if not p.startswith('android/assets/setup/')]),28)
    def test_isolated_manifest_keeps_java_components_and_private_provider(self):
        android='{http://schemas.android.com/apk/res/android}'
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'AndroidManifest.xml';variant_manifest(path,'local.codex.wallpapers.qa',True)
            manifest=ET.parse(path).getroot();app=manifest.find('application')
            self.assertEqual(manifest.get('package'),'local.codex.wallpapers.qa')
            self.assertEqual(app.get(android+'debuggable'),'true')
            self.assertEqual(app.find('provider').get(android+'authorities'),'local.codex.wallpapers.qa.usage')
            self.assertEqual(app.find('provider').get(android+'exported'),'false')
            for item in app:
                name=item.get(android+'name')
                if name:self.assertTrue(name.startswith('local.codex.wallpapers.'))
            variant_manifest(path)
            self.assertEqual(ET.parse(path).getroot().find('application').get(android+'debuggable'),'false')

if __name__=='__main__':unittest.main()
