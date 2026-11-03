#!/usr/bin/env python3
"""Checks every widget in widgets/ and writes the published index to dist/.

    tools/build_index.py                     # check, then write dist/
    tools/build_index.py --check             # check only (what CI runs on a pull request)
    tools/build_index.py --check --base origin/main   # also insist changed widgets bump their version
    tools/build_index.py --check --widgets pr/widgets --base-widgets widgets --report r.json
                                             # CI: check someone else's tree with this copy's rules
    tools/build_index.py --require-signature          # publishing: no key, no index

The index is signed (dist/index.json.sig: Ed25519 over index.json's exact bytes, base64) with the
key in ARTFRAME_INDEX_SIGNING_KEY: the base64 key itself (the CI secret) or the path of a file
holding it (local builds). Without it the index is written unsigned, with a warning; the Mac app
won't trust an unsigned index. It carries "issuedAt" and "expires" (30 days on) so an old copy
can't be passed off as current, and "revoked" from revoked.json, which turns those widgets off
wherever they're installed.

Each widget is a folder named after its id, holding widget.json, screenshot.png and
optionally README.md. The widget itself is checked by the TV's own parser (checker/);
this script adds the rules for publishing it. Standard library only.
"""

import argparse
import base64
import datetime
import ipaddress
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import zlib
from urllib.parse import urlsplit
from pathlib import Path

import ed25519

ROOT = Path(__file__).resolve().parent.parent
WIDGETS = ROOT / "widgets"
CHECKER = ROOT / "checker"
# The checker's text warnings (phone numbers, web addresses, "scan"/"call"): shown, never fatal.
WARNINGS: list[str] = []
CHECKER_BIN = CHECKER / "build" / "install" / "checker" / "bin" / "checker"

INDEX_FORMAT = 2
VALID_DAYS = 30
KEY_ENV = "ARTFRAME_INDEX_SIGNING_KEY"
REVOKED = ROOT / "revoked.json"
ALLOWED_FILES = {"widget.json", "screenshot.png", "README.md"}
# Prefixes people already use for local widgets, so a published one never collides with them.
RESERVED_PREFIXES = {"test", "example", "local", "my"}
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
MAX_WIDGET_BYTES = 64 * 1024  # the TV skips bigger files
MAX_SCREENSHOT_BYTES = 1024 * 1024
MAX_SOURCES = 6
DEFAULT_REFRESH = 900
# A URL built from the viewer's settings or HUD context sends that data to the site on every
# fetch, so it mustn't do so more than every 5 minutes.
MIN_PERSONAL_REFRESH = 300
PERSONAL = re.compile(r"\b(hud|settings)\.")
LOCATION = re.compile(r"\bhud\.(location|lat|lon|latitude|longitude|city)|\bsettings\.(lat|lon|latitude|longitude|city|zip|postcode|address|location)\b", re.I)
SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "buff.ly", "rebrand.ly",
              "cutt.ly", "shorturl.at", "rb.gy", "tiny.cc", "s.id", "v.gd", "lnkd.in", "t.ly"}


def find_java_home():
    """A JDK 17 for the checker: $JAVA_HOME, else macOS's java_home, else Homebrew's openjdk@17,
    else whatever java is on PATH (None, so the checker's own script finds it)."""
    env = os.environ.get("JAVA_HOME")
    if env and (Path(env) / "bin" / "java").exists():
        return env
    if sys.platform == "darwin" and Path("/usr/libexec/java_home").exists():
        r = subprocess.run(["/usr/libexec/java_home", "-v", "17"], capture_output=True, text=True)
        if r.returncode == 0 and r.stdout.strip():
            return r.stdout.strip()
    for brew in ("/opt/homebrew/opt/openjdk@17", "/usr/local/opt/openjdk@17"):
        if (Path(brew) / "bin" / "java").exists():
            return brew
    return None


def checker_env():
    env = dict(os.environ)
    home = find_java_home()
    if home:
        env["JAVA_HOME"] = home
        env["PATH"] = f"{home}/bin{os.pathsep}{env.get('PATH', '')}"
    elif not shutil.which("java"):
        fail("No Java found. The checker needs JDK 17: install it (brew install openjdk@17) or set JAVA_HOME.")
    return env


def fail(message, output=""):
    print(f"✗ {message}", file=sys.stderr)
    if output.strip():
        print(output.rstrip(), file=sys.stderr)
    sys.exit(1)


def run_checker(files):
    env = checker_env()
    if not CHECKER_BIN.exists():
        r = subprocess.run(["./gradlew", "-q", "installDist"], cwd=CHECKER, env=env, capture_output=True, text=True)
        if r.returncode != 0:
            fail("Couldn't build the checker (checker/gradlew installDist):", r.stderr + r.stdout)
    r = subprocess.run([str(CHECKER_BIN), *map(str, files)], capture_output=True, text=True, env=env)
    if r.returncode != 0:
        fail(f"The checker failed (exit {r.returncode}):", r.stderr + r.stdout)
    try:
        reports = json.loads(r.stdout)
    except ValueError:
        fail("The checker's output wasn't JSON:", r.stdout + r.stderr)
    return {Path(x["file"]).resolve(): x for x in reports}


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


def secret_settings(j):
    return {s.get("key") for s in j.get("settings", []) or [] if isinstance(s, dict) and s.get("secret")}


def lint_sources(j):
    """(problems, warnings) about what the widget's sources send where."""
    bad, warn = [], []
    data = j.get("data") or {}
    if not isinstance(data, dict):
        return bad, warn
    if len(data) > MAX_SOURCES:
        bad.append(f"{len(data)} data sources; at most {MAX_SOURCES}")
    secrets = secret_settings(j)
    for name, src in data.items():
        if not isinstance(src, dict):
            continue
        url = str(src.get("url", ""))
        refresh = src.get("refresh", DEFAULT_REFRESH)
        if PERSONAL.search(url) and isinstance(refresh, (int, float)) and refresh < MIN_PERSONAL_REFRESH:
            bad.append(f'data.{name}: the URL uses settings or hud values, so "refresh" must be at least {MIN_PERSONAL_REFRESH}')
        if LOCATION.search(url):
            warn.append(f"data.{name}: the URL sends the viewer's location to the site")
        for key in secrets:
            if key and re.search(r"\bsettings\." + re.escape(key) + r"\b", url):
                warn.append(f"data.{name}: the URL carries the secret setting {key!r}; send it in a header if the API allows")
        host = (urlsplit(url.split("{{")[0]).hostname or "").lower().rstrip(".")
        if host:
            try:
                ipaddress.ip_address(host)
                bad.append(f"data.{name}: use the site's name, not a raw IP address ({host})")
            except ValueError:
                pass
            if host in SHORTENERS:
                bad.append(f"data.{name}: {host} is a link shortener; use the real site")
    return bad, warn


def check(base=None, base_widgets=None):
    """Returns (entries, problems, warnings): index entries for good widgets, and 'id: problem' lines."""
    folders = sorted(p for p in WIDGETS.iterdir() if p.is_dir()) if WIDGETS.exists() else []
    stray = sorted(p.name for p in WIDGETS.iterdir() if p.is_file() and p.name != ".gitkeep") if WIDGETS.exists() else []
    problems = [f"widgets/{n}: put each widget in its own folder" for n in stray]
    manifests = [f / "widget.json" for f in folders if (f / "widget.json").is_file()]
    reports = run_checker(manifests) if manifests else {}
    entries = []
    warnings = []

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
        WARNINGS.extend(f"{name}: widget.json: {w}" for w in report.get("warnings", []))
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
        lint_bad, lint_warn = lint_sources(j) if isinstance(j, dict) else ([], [])
        bad += lint_bad
        warnings += [f"{name}: {w}" for w in lint_warn]

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

        if (base or base_widgets) and SEMVER.match(version):
            if base_widgets:
                prev = Path(base_widgets) / name / "widget.json"
                old = prev.read_text("utf-8", "replace") if prev.is_file() else None
            else:
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
            # What of the person's each site gets, worded as the Mac app shows it; the Mac checks the file against it.
            "sends": report["sends"],
            "needsKey": any(s.get("secret") for s in settings),
            "file": f"{wid}/{version}.widget.json",
            "sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(raw),
            "screenshot": f"{wid}/screenshot.png",
        })
    return entries, problems, warnings


PNG_KEEP = {b"IHDR", b"PLTE", b"tRNS", b"IDAT", b"IEND"}


def clean_png(src, dst):
    """Copies a PNG keeping only the chunks needed to draw it, so text, EXIF and other
    metadata (or anything hidden after IEND) never reaches the gallery."""
    data = Path(src).read_bytes()
    out, pos = [data[:8]], 8
    while pos + 8 <= len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos:pos + 12 + length]
        if len(chunk) < 12 + length:
            fail(f"{src}: truncated PNG")
        if zlib.crc32(chunk[4:8 + length]) != struct.unpack(">I", chunk[8 + length:])[0]:
            fail(f"{src}: corrupt PNG chunk {kind!r}")
        if kind in PNG_KEEP:
            out.append(chunk)
        pos += 12 + length
        if kind == b"IEND":
            break
    Path(dst).write_bytes(b"".join(out))


def load_revoked():
    """(revocations, problems) from revoked.json: [{"id", "versions": "*" or ["1.0.0"], "reason"}]."""
    if not REVOKED.exists():
        return [], []
    try:
        items = json.loads(REVOKED.read_text())["revoked"]
    except (ValueError, KeyError, TypeError) as e:
        return [], [f"revoked.json: can't read it ({e})"]
    out, problems = [], []
    for i, r in enumerate(items):
        where = f"revoked.json: entry {i + 1}"
        if not isinstance(r, dict) or not isinstance(r.get("id"), str) or not r["id"].strip():
            problems.append(f'{where} needs an "id"')
            continue
        versions = r.get("versions", "*")
        if versions != "*" and not (isinstance(versions, list) and versions and all(isinstance(v, str) and SEMVER.match(v) for v in versions)):
            problems.append(f'{where} ({r["id"]}): "versions" is "*" or a list like ["1.0.0"]')
            continue
        if not str(r.get("reason", "")).strip():
            problems.append(f'{where} ({r["id"]}): say why in "reason"; people see it')
            continue
        out.append({"id": r["id"].strip().lower(), "versions": versions, "reason": r["reason"].strip()})
    return out, problems


def is_revoked(revoked, wid, version):
    return any(r["id"] == wid and (r["versions"] == "*" or version in r["versions"]) for r in revoked)


def load_key():
    """The signing key's 32 bytes from ARTFRAME_INDEX_SIGNING_KEY, or None when it isn't set.
    Never printed: errors name the variable, not its value."""
    value = os.environ.get(KEY_ENV, "").strip()
    if not value:
        return None
    if os.path.isfile(os.path.expanduser(value)):
        value = Path(os.path.expanduser(value)).read_text().strip()
    try:
        seed = base64.b64decode(value, validate=True)
    except ValueError:
        seed = b""
    if len(seed) != 32:
        sys.exit(f"✗ {KEY_ENV} isn't a base64 Ed25519 key (32 bytes) or a file holding one")
    return seed


def signed_index(entries, revoked, parser, key, now):
    """(index bytes, signature or None). The bytes are signed exactly as written."""
    index = {
        "format": INDEX_FORMAT,
        "checkedWith": parser,
        "issuedAt": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "expires": (now + datetime.timedelta(days=VALID_DAYS)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "revoked": revoked,
        "widgets": sorted(entries, key=lambda e: e["name"].lower()),
    }
    data = (json.dumps(index, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    if key is None:
        return data, None
    sig = ed25519.sign(key, data)
    assert ed25519.verify(ed25519.public_key(key), data, sig)
    return data, base64.b64encode(sig) + b"\n"


def write(entries, revoked, out, key, now=None):
    if out.exists():
        shutil.rmtree(out)
    for e in entries:
        folder = out / e["id"]
        folder.mkdir(parents=True)
        shutil.copyfile(WIDGETS / e["id"] / "widget.json", out / e["file"])
        clean_png(WIDGETS / e["id"] / "screenshot.png", out / e["screenshot"])
    # Published beside the index, so a manifest's "$schema" can point at artframe.mlynn.dev.
    shutil.copyfile(ROOT / "spec" / "widget.schema.json", out / "widget.schema.json")
    parser = (CHECKER / "src/main/kotlin/vendor/FROM.txt").read_text().split(" at ")[1].split(" ")[0]
    data, sig = signed_index(entries, revoked, parser, key, now or datetime.datetime.now(datetime.timezone.utc))
    (out / "index.json").write_bytes(data)
    if sig:
        (out / "index.json.sig").write_bytes(sig)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="check only; don't write dist/")
    ap.add_argument("--base", help="git ref of what's published, to insist changed widgets bump their version")
    ap.add_argument("--base-widgets", help="a folder of the published widgets, instead of --base")
    ap.add_argument("--widgets", help="check this widgets folder instead of the one beside this script")
    ap.add_argument("--report", help="write {id: {sites, version}} for passing widgets to this file")
    ap.add_argument("--out", default=str(ROOT / "dist"))
    ap.add_argument("--require-signature", action="store_true", help=f"fail when {KEY_ENV} isn't set")
    args = ap.parse_args()

    global WIDGETS
    if args.widgets:
        WIDGETS = Path(args.widgets).resolve()
    entries, problems, warnings = check(args.base, args.base_widgets)
    revoked, bad = load_revoked()
    problems += bad
    # A withdrawn version is never published again.
    for e in [e for e in entries if is_revoked(revoked, e["id"], e["version"])]:
        warnings.append(f"{e['id']} {e['version']}: revoked, so it's left out of the index")
        entries.remove(e)
    # The checker's text warnings (TextLint) join the publishing ones.
    warnings += [f"{w} (review; doesn't fail the check)" for w in WARNINGS]
    for w in warnings:
        print(f"! {w}", file=sys.stderr)
    if args.report:
        Path(args.report).write_text(json.dumps({
            "widgets": {e["id"]: {"sites": e["sites"], "version": e["version"]} for e in entries},
            "problems": problems,
            "warnings": warnings,
        }, indent=2) + "\n")
    for p in problems:
        print(f"✗ {p}", file=sys.stderr)
    if problems:
        sys.exit(1)
    print(f"✓ {len(entries)} widget(s) pass")
    if revoked:
        print(f"✓ {len(revoked)} revoked widget(s) listed")
    if not args.check:
        key = load_key()
        if key is None:
            if args.require_signature:
                sys.exit(f"✗ {KEY_ENV} isn't set, so the index can't be signed")
            print(f"! {KEY_ENV} isn't set: writing an unsigned index, which the Mac app won't trust", file=sys.stderr)
        write(entries, revoked, Path(args.out), key)
        print(f"Wrote {args.out}/index.json" + (f" (signed by key {base64.b64encode(ed25519.public_key(key)).decode()})" if key else ""))


if __name__ == "__main__":
    main()
