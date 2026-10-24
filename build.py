#!/usr/bin/env python3
"""Local compile-only build. Dependencies never enter the output JAR."""
import argparse, os, pathlib, shutil, subprocess, re, zipfile
ROOT = pathlib.Path(__file__).resolve().parent
NAME = 'NomiQuestExtras' if (ROOT/'src/main/java/org/nomiteam/quests/NomiQuestExtras.java').exists() else 'NomiHeads'
p = argparse.ArgumentParser()
p.add_argument('--deps-dir', default=os.environ.get('NOMI_DEPS_DIR'))
p.add_argument('--test', action='store_true', help='Run real Java domain assertions before packaging')
a = p.parse_args()
if not a.deps_dir:
    p.error('Provide --deps-dir or NOMI_DEPS_DIR (directory containing private dependency JARs)')
deps = pathlib.Path(a.deps_dir).resolve()
jars = sorted(deps.rglob('*.jar'))
# Select the current Purpur API explicitly; old API versions must not win classpath resolution.
api = [j for j in jars if 'purpur-api-26.3' in j.name and 'stable' in str(j)]
if len(api) != 1:
    p.error('Exactly one stable Purpur 26.3 API JAR is required')
def natural(name):
    return tuple((1, int(part)) if part.isdigit() else (0, part) for part in re.split(r'(\d+)', name))
# Archives can contain libraries from several server generations. Use the newest
# version of each library, instead of accidentally loading an older duplicate.
latest = {}
plugin_apis = []
for jar in jars:
    if jar in api or 'purpur-api-' in jar.name or jar.name.endswith(('-sources.jar', '-javadoc.jar')):
        continue
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        if 'plugin.yml' in names or 'paper-plugin.yml' in names:
            needed = 'net/milkbowl/vault/economy/Economy.class' in names
            needed |= NAME == 'NomiQuestExtras' and 'com/ordwen/odailyquests/ODailyQuests.class' in names
            if needed: plugin_apis.append(jar)
            continue
    key = re.split(r'-(?=\d)', jar.name, maxsplit=1)[0]
    if key not in latest or natural(jar.name) > natural(latest[key].name):
        latest[key] = jar
cp = os.pathsep.join(map(str, api + sorted(latest.values()) + plugin_apis))
classes = ROOT/'build/classes'
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(parents=True)
def run(args): subprocess.run(args, check=True, cwd=ROOT)
run(['javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, sorted((ROOT/'src/main/java').rglob('*.java')))])
if a.test:
    tests = ROOT/'build/test-classes'
    if tests.exists(): shutil.rmtree(tests)
    tests.mkdir(parents=True)
    run(['javac', '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes)+os.pathsep+cp, '-d', str(tests), *map(str, sorted((ROOT/'tests').glob('*.java')))])
    for test in sorted((ROOT/'tests').glob('*Test.java')):
        run(['java', '-ea', '-cp', os.pathsep.join([str(tests), str(classes), cp]), ('org.nomiteam.quests.' if NAME=='NomiQuestExtras' else 'org.nomiteam.heads.')+test.stem])
artifact = ROOT/'build'/f'{NAME}-1.0.0.jar'
run(['jar', '--create', '--file', str(artifact), '-C', str(classes), '.', '-C', str(ROOT/'src/main/resources'), '.'])
print('Built', artifact.name)
