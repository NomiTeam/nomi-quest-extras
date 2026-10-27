#!/usr/bin/env python3
"""Compile and test with local real dependencies; never embed dependency JARs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
NAME = "NomiQuestExtras"
parser = argparse.ArgumentParser()
parser.add_argument("--deps-dir", default=os.environ.get("NOMI_DEPS_DIR"))
parser.add_argument("--test", action="store_true", help="Run Java assertions with real ODQ/Paper types")
args = parser.parse_args()
if not args.deps_dir:
    parser.error("Provide --deps-dir or NOMI_DEPS_DIR (local dependency JAR directory)")

jars = sorted(Path(args.deps_dir).resolve().rglob("*.jar"))
api = [jar for jar in jars if "purpur-api-26.3" in jar.name and "stable" in str(jar)]
if len(api) != 1:
    parser.error("Exactly one stable Purpur 26.3 API JAR is required")


def natural(name):
    return tuple((1, int(part)) if part.isdigit() else (0, part)
                 for part in re.split(r"(\d+)", name))


# Local archives contain several server generations; old duplicate APIs must not win.
latest = {}
odq = []
for jar in jars:
    if jar in api or "purpur-api-" in jar.name or jar.name.endswith(("-sources.jar", "-javadoc.jar")):
        continue
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        if "plugin.yml" in names or "paper-plugin.yml" in names:
            if "com/ordwen/odailyquests/ODailyQuests.class" in names:
                odq.append(jar)
            continue
    key = re.split(r"-(?=\d)", jar.name, maxsplit=1)[0]
    if key not in latest or natural(jar.name) > natural(latest[key].name):
        latest[key] = jar
if len(odq) != 1:
    parser.error("Exactly one real ODailyQuests dependency JAR is required")

dependencies = api + sorted(latest.values()) + odq
classpath = os.pathsep.join(map(str, dependencies))
classes = ROOT / "build/classes"
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)


def run(command):
    subprocess.run(command, check=True, cwd=ROOT)


run(["javac", "--release", "21", "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes),
     *map(str, sorted((ROOT / "src/main/java").rglob("*.java")))])
if args.test:
    tests = ROOT / "build/test-classes"
    if tests.exists():
        shutil.rmtree(tests)
    tests.mkdir(parents=True)
    run(["javac", "--release", "21", "-encoding", "UTF-8", "-cp",
         str(classes) + os.pathsep + classpath, "-d", str(tests),
         *map(str, sorted((ROOT / "tests").glob("*.java")))])
    for test in sorted((ROOT / "tests").glob("*Test.java")):
        run(["java", "-ea", "-cp", os.pathsep.join([str(tests), str(classes), classpath]),
             "org.nomiteam.quests." + test.stem])

artifact = ROOT / "build" / f"{NAME}-1.1.0.jar"
run(["jar", "--create", "--file", str(artifact), "-C", str(classes), ".",
     "-C", str(ROOT / "src/main/resources"), "."])
(ROOT / "build/dependencies.json").write_text(json.dumps([
    {"path": str(jar), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest()}
    for jar in dependencies
], indent=2) + "\n")
print("Built", artifact.name)
