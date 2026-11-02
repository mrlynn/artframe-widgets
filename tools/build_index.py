#!/usr/bin/env python3
"""Checks every widget in widgets/ and writes the published index to dist/.

    tools/build_index.py                     # check, then write dist/
    tools/build_index.py --check             # check only (what CI runs on a pull request)
    tools/build_index.py --check --base origin/main   # also insist changed widgets bump their version

Each widget is a folder named after its id, holding widget.json, screenshot.png and
optionally README.md. The widget itself is checked by the TV's own parser (checker/);
this script adds the rules for publishing it. Standard library only.
"""

import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WIDGETS = ROOT / "widgets"
CHECKER = ROOT / "checker"
CHECKER_BIN = CHECKER / "build" / "install" / "checker" / "bin" / "checker"

INDEX_FORMAT = 1
ALLOWED_FILES = {"widget.json", "screenshot.png", "README.md"}
# Prefixes people already use for local widgets, so a published one never collides with them.
RESERVED_PREFIXES = {"test", "example", "local", "my"}
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
MAX_WIDGET_BYTES = 64 * 1024  # the TV skips bigger files
MAX_SCREENSHOT_BYTES = 1024 * 1024


def run_checker(files):
    if not CHECKER_BIN.exists():
        subprocess.run(["./gradlew", "-q", "installDist"], cwd=CHECKER, check=True)
    out = subprocess.run([str(CHECKER_BIN), *map(str, files)], capture_output=True, text=True, check=True).stdout
    return {Path(r["file"]).resolve(): r for r in json.loads(out)}


def png_size(path):
    """(width, height) of a PNG, or None when it isn't one."""
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", head[16:24])


def version_key(v):
    return tuple(int(x) for x in v.split("."))


def published_before(base, rel):
    """The file's text at git ref [base], or None when it isn't there."""
    r = subprocess.run(["git", "show", f"{base}:{rel}"], cwd=ROOT, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def check(base=None):
    """Returns (entries, problems): index entries for good widgets, and 'id: problem' lines."""
    folders = sorted(p for p in WIDGETS.iterdir() if p.is_dir()) if WIDGETS.exists() else []
    stray = sorted(p.name for p in WIDGETS.iterdir() if p.is_file() and p.name != ".gitkeep") if WIDGETS.exists() else []
    problems = [f"widgets/{n}: put each widget in its own folder" for n in stray]
    manifests = [f / "widget.json" for f in folders if (f / "widget.json").is_file()]
    reports = run_checker(manifests) if manifests else {}
    entries = []

    for folder in folders:
        name = folder.name
        bad = []
        files = {p.name for p in folder.iterdir()}
        bad += [f"unexpected file {n} (only {', '.join(sorted(ALLOWED_FILES))})" for n in sorted(files - ALLOWED_FILES)]
        manifest = folder / "widget.json"
        if not manifest.is_file():
            problems.append(f"{name}: no widget.json")
            continue

        report = reports[manifest.resolve()]
        if not report["ok"]:
            bad += [f"widget.json: {p}" for p in report["problems"]]
        raw = manifest.read_bytes()
        if len(raw) > MAX_WIDGET_BYTES:
            bad.append(f"widget.json is {len(raw) // 1024} KB; the TV skips files over 64 KB")
        dupes = []

        def no_dupes(pairs):
            keys = [k for k, _ in pairs]
            dupes.extend(sorted({k for k in keys if keys.count(k) > 1}))
            return dict(pairs)

        try:
            j = json.loads(raw, object_pairs_hook=no_dupes)
        except ValueError:
            j = {}
        # JSON lets a key appear twice and the last one wins, which hides mistakes.
        bad += [f'widget.json has "{k}" twice' for k in dupes]

        wid = j.get("id", "")
        if wid != name:
            bad.append(f'the folder must be named after the id ("{wid}")')
        prefix = wid.split(".")[0] if "." in wid else None
        if prefix is None:
            bad.append('the id needs your own prefix, like "yourname.weather" (ids without one are the built-ins\')')
        elif prefix in RESERVED_PREFIXES:
            bad.append(f'"{prefix}." is kept for people\'s own local widgets; use your name or handle')
        for key in ("author", "description"):
            if not str(j.get(key, "")).strip():
                bad.append(f'"{key}" is required here')
        version = str(j.get("version", ""))
        if not SEMVER.match(version):
            bad.append('"version" must look like 1.0.0')

        if base and SEMVER.match(version):
            old = published_before(base, f"widgets/{name}/widget.json")
            if old is not None and old != raw.decode("utf-8", "replace"):
                try:
                    old_version = str(json.loads(old).get("version", ""))
                except ValueError:
                    old_version = ""
                if SEMVER.match(old_version) and version_key(version) <= version_key(old_version):
                    bad.append(f"widget.json changed, so bump \"version\" past {old_version}")

        shot = folder / "screenshot.png"
        if not shot.is_file():
            bad.append("no screenshot.png")
        else:
            size = png_size(shot)
            if size is None:
                bad.append("screenshot.png isn't a PNG")
            elif not (300 <= size[0] <= 1920 and size[1] <= 1080):
                bad.append(f"screenshot.png is {size[0]}x{size[1]}; make it 300-1920 wide and at most 1080 high")
            if shot.stat().st_size > MAX_SCREENSHOT_BYTES:
                bad.append("screenshot.png is over 1 MB")

        if bad:
            problems += [f"{name}: {b}" for b in bad]
            continue
        settings = j.get("settings", [])
        entries.append({
            "id": wid,
            "name": j["name"],
            "description": j["description"],
            "icon": j.get("icon", "square.dashed"),
            "size": j.get("size", "small"),
            "author": j["author"],
            "version": version,
            "homepage": j.get("homepage"),
            "spec": j["spec"],
            "sites": report["hosts"],
            "choosesSite": report["chosenSite"],
            "needsKey": any(s.get("secret") for s in settings),
            "file": f"{wid}/{version}.widget.json",
            "sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(raw),
            "screenshot": f"{wid}/screenshot.png",
        })
    return entries, problems


def write(entries, out):
    if out.exists():
        shutil.rmtree(out)
    for e in entries:
        folder = out / e["id"]
        folder.mkdir(parents=True)
        shutil.copyfile(WIDGETS / e["id"] / "widget.json", out / e["file"])
        shutil.copyfile(WIDGETS / e["id"] / "screenshot.png", out / e["screenshot"])
    # Published beside the index, so a manifest's "$schema" can point at artframe.mlynn.dev.
    shutil.copyfile(ROOT / "spec" / "widget.schema.json", out / "widget.schema.json")
    parser = (CHECKER / "src/main/kotlin/vendor/FROM.txt").read_text().split(" at ")[1].split(" ")[0]
    index = {"format": INDEX_FORMAT, "checkedWith": parser, "widgets": sorted(entries, key=lambda e: e["name"].lower())}
    (out / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="check only; don't write dist/")
    ap.add_argument("--base", help="git ref of what's published, to insist changed widgets bump their version")
    ap.add_argument("--out", default=str(ROOT / "dist"))
    args = ap.parse_args()

    entries, problems = check(args.base)
    for p in problems:
        print(f"✗ {p}", file=sys.stderr)
    if problems:
        sys.exit(1)
    print(f"✓ {len(entries)} widget(s) pass")
    if not args.check:
        write(entries, Path(args.out))
        print(f"Wrote {args.out}/index.json")


if __name__ == "__main__":
    main()
