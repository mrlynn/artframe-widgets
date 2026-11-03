#!/usr/bin/env python3
"""The pull request rules that sit around build_index.py. CI runs this copy from the base
branch against the pull request's files, so a pull request can't change the rules it's held to.

    tools/check_pr.py --base-dir . --head-dir pr --author LOGIN \\
        [--base-report base.json --head-report head.json] [--out result.json]

--base-dir and --head-dir are git checkouts of the base branch and the pull request. A
contributor's pull request may only:
  - touch one folder in widgets/, whose prefix owners.json gives to them, and
  - add their own new prefix to owners.json (and change nothing else there).
Maintainers (owners.json "maintainers") may change anything; CODEOWNERS review covers them.
With both reports from build_index.py --report, it also lists sites the pull request adds.
Standard library only.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

PREFIX = re.compile(r"^[a-z0-9][a-z0-9-]{1,38}$")
LOGIN = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$")


def changed_paths(base_dir, head_dir):
    """Paths that differ between the two checkouts' HEAD commits (added, changed, removed or renamed)."""
    base_sha = subprocess.run(["git", "rev-parse", "HEAD"], cwd=base_dir, capture_output=True, text=True, check=True).stdout.strip()
    # The base commit has to be reachable from the head checkout; CI fetches it there.
    r = subprocess.run(["git", "diff", "--no-renames", "--name-only", "-z", f"{base_sha}...HEAD"],
                       cwd=head_dir, capture_output=True, check=True)
    return sorted({p.decode("utf-8", "replace") for p in r.stdout.split(b"\0") if p})


def load_owners(path):
    try:
        j = json.loads(Path(path).read_text())
    except (OSError, ValueError) as e:
        return None, f"owners.json can't be read: {e}"
    if not isinstance(j, dict) or not isinstance(j.get("prefixes"), dict) or not isinstance(j.get("maintainers", []), list):
        return None, 'owners.json must be {"maintainers": [...], "prefixes": {"prefix": ["login"]}}'
    for k, v in j["prefixes"].items():
        if not PREFIX.match(k) or not isinstance(v, list) or not v or not all(isinstance(x, str) and LOGIN.match(x) for x in v):
            return None, f'owners.json: "{k}" must map to a list of GitHub logins'
    return j, None


def lower(logins):
    return {x.lower() for x in logins}


def check(base_dir, head_dir, author, paths):
    problems = []
    base_owners, err = load_owners(Path(base_dir) / "owners.json")
    if err:
        return [f"base branch: {err}"]
    maintainer = author.lower() in lower(base_owners.get("maintainers", []))

    widget_dirs = sorted({p.split("/")[1] for p in paths if p.startswith("widgets/") and p.count("/") >= 2})
    stray_widgets = [p for p in paths if p.startswith("widgets/") and p.count("/") < 2 and p != "widgets/.gitkeep"]
    other = [p for p in paths if not p.startswith("widgets/") and p != "owners.json"]

    owners = base_owners
    if "owners.json" in paths:
        head_owners, err = load_owners(Path(head_dir) / "owners.json")
        if err:
            problems.append(err)
        elif not maintainer:
            if lower(head_owners.get("maintainers", [])) != lower(base_owners.get("maintainers", [])):
                problems.append("owners.json: only a maintainer can change \"maintainers\"")
            for k, v in base_owners["prefixes"].items():
                if head_owners["prefixes"].get(k) != v:
                    problems.append(f'owners.json: "{k}" belongs to {", ".join(v)}; only a maintainer can change it')
            added = {k: v for k, v in head_owners["prefixes"].items() if k not in base_owners["prefixes"]}
            for k, v in added.items():
                if lower(v) != {author.lower()}:
                    problems.append(f'owners.json: a new prefix "{k}" can only list you ({author})')
                if k.startswith("artframe") or k in {"test", "example", "local", "my"}:
                    problems.append(f'owners.json: "{k}" is reserved')
            if len(added) > 1:
                problems.append("owners.json: add one prefix at a time")
            owners = {**base_owners, "prefixes": {**base_owners["prefixes"], **added}}
        else:
            owners = head_owners

    if maintainer:
        return problems

    if other:
        problems.append("this pull request changes files outside widgets/ (" + ", ".join(other[:10])
                        + "); tools/, checker/, spec/ and .github/ changes come from maintainers")
    if stray_widgets:
        problems.append("put each widget in its own folder: " + ", ".join(stray_widgets))
    if len(widget_dirs) > 1:
        problems.append("one widget per pull request, please (this one touches " + ", ".join(widget_dirs) + ")")
    for wid in widget_dirs:
        prefix = wid.split(".")[0] if "." in wid else wid
        allowed = lower(owners["prefixes"].get(prefix, []))
        if prefix.startswith("artframe"):
            allowed = lower(base_owners["prefixes"].get("artframe", []))
        if not allowed:
            problems.append(f'{wid}: nobody owns the prefix "{prefix}." yet; add "{prefix}": ["{author}"] to owners.json in this pull request')
        elif author.lower() not in allowed:
            problems.append(f'{wid}: the prefix "{prefix}." belongs to {", ".join(sorted(allowed))}, not {author}')
    return problems


def new_sites(base_report, head_report):
    """{widget id: [sites it reaches now that it didn't on the base branch]}"""
    def sites(path):
        j = json.loads(Path(path).read_text())
        return {k: set(v.get("sites", [])) for k, v in j.get("widgets", {}).items()}
    before, after = sites(base_report), sites(head_report)
    known = set().union(*before.values()) if before else set()
    out = {}
    for wid, hosts in sorted(after.items()):
        added = sorted(hosts - before.get(wid, set()))
        if added:
            out[wid] = [{"site": h, "newToGallery": h not in known} for h in added]
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-dir", required=True)
    ap.add_argument("--head-dir", required=True)
    ap.add_argument("--author", required=True)
    ap.add_argument("--base-report")
    ap.add_argument("--head-report")
    ap.add_argument("--out")
    args = ap.parse_args()
    if not LOGIN.match(args.author):
        sys.exit(f"✗ odd author login {args.author!r}")

    paths = changed_paths(args.base_dir, args.head_dir)
    problems = check(args.base_dir, args.head_dir, args.author, paths)
    sites = new_sites(args.base_report, args.head_report) if args.base_report and args.head_report else {}
    if args.out:
        Path(args.out).write_text(json.dumps({"problems": problems, "newSites": sites}, indent=2) + "\n")
    for wid, hosts in sites.items():
        print(f"! {wid} now reaches " + ", ".join(h["site"] + (" (new to the gallery)" if h["newToGallery"] else "") for h in hosts))
    for p in problems:
        print(f"✗ {p}", file=sys.stderr)
    if problems:
        sys.exit(1)
    print(f"✓ pull request rules pass ({len(paths)} file(s) changed)")


if __name__ == "__main__":
    main()
