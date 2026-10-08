"""Locate a locally installed Codex runtime without reading credentials."""
import os
from pathlib import Path
import platform
import shutil


def data_dir(system=None, env=None, home=None):
    env = os.environ if env is None else env
    home = Path.home() if home is None else Path(home)
    if env.get('CODEX_USAGE_DATA_DIR'):
        return Path(env['CODEX_USAGE_DATA_DIR']).expanduser()
    system = system or platform.system()
    if system == 'Darwin':
        return home/'Library/Application Support/CodeXUsageSync'
    if system == 'Windows':
        return Path(env.get('LOCALAPPDATA', str(home/'AppData/Local')))/'CodeXUsageSync'
    return Path(env.get('XDG_DATA_HOME', str(home/'.local/share')))/'CodeXUsageSync'


def find_codex(system=None, env=None, home=None, which=None):
    env = os.environ if env is None else env
    home = Path.home() if home is None else Path(home)
    which = which or shutil.which
    if env.get('CODEX_BINARY'):
        binary = Path(env['CODEX_BINARY']).expanduser()
        if not binary.is_file():
            raise RuntimeError('CODEX_BINARY does not point to a file')
        return binary
    system = system or platform.system()
    candidates = []
    if system == 'Darwin':
        candidates = [base/'Codex.app/Contents/Resources/codex'
                      for base in (Path('/Applications'), home/'Applications')]
        candidates += [base/'ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex'
                       for base in (Path('/Applications'), home/'Applications')]
    elif system == 'Windows':
        local = Path(env.get('LOCALAPPDATA', str(home/'AppData/Local')))
        candidates = sorted((local/'OpenAI/Codex/bin').glob('*/codex.exe'),
                            key=lambda path: path.stat().st_mtime, reverse=True)
    for binary in candidates:
        if binary.is_file():
            return binary
    installed = which('codex')
    if installed:
        return Path(installed)
    raise RuntimeError('Codex runtime not found; install/login to Codex or set CODEX_BINARY')
