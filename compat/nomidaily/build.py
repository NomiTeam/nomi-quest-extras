#!/usr/bin/env python3
"""Build the standalone expansion using local, real API jars only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def dependencies(directory):
    jars = sorted(Path(directory).resolve().rglob('*.jar'))
    required = ['me/clip/placeholderapi/expansion/PlaceholderExpansion.class',
                'org/bukkit/OfflinePlayer.class',
                'com/ordwen/odailyquests/quests/player/PlayerQuests.class']
    found = {name: [] for name in required}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in required:
                if name in archive.namelist():
                    found[name].append(jar)
    for name, matches in found.items():
        if len(matches) != 1:
            raise SystemExit(f'Expected exactly one provider of {name}; found {len(matches)}. Use a curated dependency directory.')
    return jars

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps-dir', default=os.environ.get('NOMI_DEPS_DIR'))
    args = parser.parse_args()
    if not args.deps_dir:
        parser.error('--deps-dir or NOMI_DEPS_DIR is required')
    jars = dependencies(args.deps_dir)
    out = ROOT / 'build'
    out.mkdir(exist_ok=True)
    sources = sorted((ROOT / 'src').rglob('*.java'))
    with tempfile.TemporaryDirectory(dir=out) as temp:
        subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-proc:none',
                        '-classpath', os.pathsep.join(map(str, jars)), '-d', temp,
                        *map(str, sources)], check=True)
        target = out / 'Expansion-nomidaily.jar'
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED) as archive:
            entries = {'META-INF/MANIFEST.MF': b'Manifest-Version: 1.0\r\n\r\n'}
            entries.update({p.relative_to(temp).as_posix(): p.read_bytes()
                            for p in sorted(Path(temp).rglob('*.class'))})
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
    digest = {
        'artifact': {'path': target.name, 'sha256': sha(target)},
        'sources': {p.relative_to(ROOT).as_posix(): sha(p) for p in
                    [*sources, ROOT / 'build.py']},
        'dependencies': [{'name': p.name, 'sha256': sha(p)} for p in jars],
        'javac': subprocess.check_output(['javac', '-version'], text=True).strip(),
        'release': 21,
    }
    (out / 'source-artifact.json').write_text(json.dumps(digest, indent=2) + '\n')
    print(f'{target.name} sha256={sha(target)}')

if __name__ == '__main__':
    main()
