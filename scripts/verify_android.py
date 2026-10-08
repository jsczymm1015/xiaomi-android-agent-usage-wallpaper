"""Run portable media/sync checks in local.codex.wallpapers.qa only."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
from capture_documentation import build
from build_android import ROOT, sdk_default


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',type=Path,default=sdk_default())
    parser.add_argument('--keystore',type=Path,required=True)
    parser.add_argument('--adb',type=Path)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--source-url',default='')
    args=parser.parse_args()
    package='local.codex.wallpapers.qa'
    apk=build(args.sdk,args.keystore, target_package=package,runner_class='PortableVerificationInstrumentation')
    adb=args.adb or shutil.which('adb') or args.sdk/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
    base=[str(adb),'-s',args.serial]
    subprocess.run([*base,'install','-r',str(apk)],check=True)
    try:
        command=[*base,'exec-out','am','instrument','-w']
        if args.source_url:command+=['-e','sourceUrl',args.source_url]
        command+=[package+'.documentation/local.codex.wallpapers.PortableVerificationInstrumentation']
        result=subprocess.run(command,capture_output=True,text=True,timeout=180)
        safe=[]
        for line in result.stdout.splitlines():
            if re.fullmatch(r'INSTRUMENTATION_(?:STATUS|RESULT): (?:stage|failureType|assertions|networkVerified|gifFiles|isolatedTarget|secondHttpCode)=[A-Za-z0-9_]+',line):
                safe.append(line)
        print('\n'.join(safe))
        if result.returncode or 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'failureType=' in result.stdout:
            raise RuntimeError('Isolated Android verification failed')
        output=ROOT/'.local/portable-verification';output.mkdir(parents=True,exist_ok=True)
        for name in ('media-ambient.gif','media-rear.gif'):
            data=subprocess.run([*base,'exec-out','run-as',package,'cat','files/portable-verification/'+name],capture_output=True,check=True).stdout
            if not data.startswith(b'GIF89a'):raise RuntimeError('Missing generated GIF')
            (output/name).write_bytes(data)
    finally:
        subprocess.run([*base,'uninstall',package+'.documentation'],check=False)


if __name__=='__main__':main()
