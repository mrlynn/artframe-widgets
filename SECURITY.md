# Security

Widgets in this gallery run on people's TVs and fetch data from the sites they name. This
page says how submissions are reviewed and how to report a problem.

## Reporting a problem

Please don't open a public issue for a security problem. Use GitHub's private reporting:
**Security → Report a vulnerability** on this repository. Include the widget id (or the
file in `tools/`, `checker/` or `.github/`), what happens, and how to reproduce it.

To report a widget that's misbehaving (sending more data than it should, phishing text, an
API that's gone bad), the same route works, or open an ordinary issue if it isn't sensitive.
A widget can be pulled from the gallery by removing its folder; the next publish drops it
from the index.

## How a widget is reviewed

1. **Automated rules, from the base branch.** The required check, "Pull request rules"
   (`.github/workflows/policy.yml`), runs `main`'s own `tools/` and `checker/` against the
   pull request's `widgets/`. The pull request's files are read as data; none of its code
   is run in that workflow. It enforces:
   - the widget loads in the TV's own parser, is under 64 KB, and has the required fields;
   - `owners.json` gives the widget's id prefix to the pull request's author (`artframe.`
     and `mlynn.` belong to the maintainer); a contributor can claim one new prefix for
     themselves but can't change anyone else's;
   - one widget folder per pull request, and nothing outside `widgets/` and `owners.json`
     unless the author is a maintainer;
   - at most 6 sources; a URL built from settings or HUD values refreshes no faster than
     every 300 s; no raw IP addresses or link shorteners; warnings when a URL carries the
     viewer's location or a secret;
   - a changed widget raises its version.
2. **New sites are flagged.** When a pull request makes a widget reach a site it didn't
   before, a comment lists the sites and the `new-host` label is added. That comment job
   can write to the pull request but never checks out its code.
3. **A maintainer reviews by hand** (CODEOWNERS), using the checklist in the pull request
   template: is each site the real API, does each URL send only what it needs, is the text
   honest.
4. **Publishing** runs only from `main`. It checks everything again, strips metadata from
   screenshots, and records each widget's SHA-256 in `index.json`. The Mac app checks that
   hash before installing.

`check.yml` also runs on pull requests with the pull request's own tools. It's for quick
feedback and has a read-only token, no secrets, and no say over merging.

GitHub Actions are pinned to commit SHAs and updated by Dependabot.

## What the TV does at run time

A widget can only fetch over HTTPS from the sites it declares, with a response cap and
timeouts, and the Mac app shows people which sites a widget contacts before they install
it. What the app could tighten further is tracked in
[docs/SECURITY-HANDOFF-app.md](docs/SECURITY-HANDOFF-app.md).
