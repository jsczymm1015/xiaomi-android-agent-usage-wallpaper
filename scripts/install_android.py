"""Install the built APK on one explicitly chosen Android device."""
import argparse,os,shutil,subprocess
from pathlib import Path
from build_android import ROOT,sdk_default

def main():
    p=argparse.ArgumentParser();p.add_argument('--serial');p.add_argument('--adb',type=Path);p.add_argument('--apk',type=Path,default=ROOT/'build/CodeX.apk');args=p.parse_args()
    adb=args.adb or shutil.which('adb') or sdk_default()/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
    if not args.apk.is_file():raise SystemExit('Build first: APK missing')
    subprocess.run([str(adb),*(['-s',args.serial] if args.serial else []),'install','-r',str(args.apk)],check=True)
    print('Installed. Open CodeX on the phone.')
if __name__=='__main__':main()
