import subprocess,sys,tempfile,unittest
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(root/'desktop'),str(root/'scripts')]
if not unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover(str(root/'tests'))).wasSuccessful():raise SystemExit(1)

# Run the same time/count rules used by the widget on the host JVM.
from build_android import java_tool
with tempfile.TemporaryDirectory(prefix='codex-widget-rules-') as staging:
    rules=['UsagePolicy','UsageResetCards','UsageEndpoint','PortableGif','WidgetBitmapBudget']
    sources=[root/'android/src/local/codex/wallpapers'/f'{name}.java' for name in rules]
    tests=[root/'tests'/f'{name}Test.java' for name in rules]
    subprocess.run([java_tool('javac'),'-encoding','UTF-8','--release','8','-d',staging,
                    *map(str,sources+tests)],check=True)
    for name in rules:
        subprocess.run([java_tool('java'),'-cp',staging,f'local.codex.wallpapers.{name}Test'],check=True)
