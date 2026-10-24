#!/usr/bin/env python3
"""Compare real legacy and rebuilt expansions without starting a server."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from build import dependencies

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--deps-dir', default=os.environ.get('NOMI_DEPS_DIR'), required=not os.environ.get('NOMI_DEPS_DIR'))
parser.add_argument('--baseline-jar', type=Path, required=True)
args = parser.parse_args()
jars = dependencies(args.deps_dir)
new = ROOT / 'build/Expansion-nomidaily.jar'
with zipfile.ZipFile(new) as z:
    assert set(z.namelist()) == {'META-INF/MANIFEST.MF', 'NomiDailyExpansion.class'}
    assert z.read('META-INF/MANIFEST.MF') == b'Manifest-Version: 1.0\r\n\r\n'
(ROOT / '.work').mkdir(exist_ok=True, mode=0o700)
outputs = []
for artifact in [args.baseline_jar, new]:
    cp = os.pathsep.join(map(str, [artifact, *jars]))
    with tempfile.TemporaryDirectory(dir=ROOT / '.work') as temp:
        subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', cp,
                        '-d', temp, str(ROOT / 'tests/ExpansionRegression.java')], check=True)
        result = subprocess.check_output(['java', '-cp', temp + os.pathsep + cp,
                                          'ExpansionRegression'], text=True)
        print(('baseline' if artifact == args.baseline_jar else 'rebuilt') + ': ' + result.strip())
        outputs.append(result)
assert outputs[0] == outputs[1]
# Includes every actual method, instruction, constant-pool reference and expression.
def disassemble(p):
    return subprocess.check_output(['javap', '-c', '-p', '-s', '-classpath', str(p),
                                    'NomiDailyExpansion'], text=True)
assert disassemble(args.baseline_jar) == disassemble(new), 'Bytecode methods differ'
print('PASS identical javap methods/instructions/descriptors; own class + manifest only')
