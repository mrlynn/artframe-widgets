# ArtFrame widgets

Widgets for [ArtFrame](https://artframe.mlynn.dev)'s information HUD, the cards that sit
over the art on your TV. Each one is a single JSON file written to the
[widget spec](spec/SPEC.md): data, not code, so a widget can only show text, bars and
lines, and can only contact the sites it names.

## Installing one

In ArtFrameRemote on your Mac, open **Screen › Cards**, click **Browse Gallery…**, find the widget and add it.
The app shows which sites the widget contacts, and what of yours it sends there, before
you turn it on.

Until the app has a browser for this collection, download a widget's `widget.json`
and use **Add Widget…**.

## What's here

| Folder | What it is |
| --- | --- |
| `widgets/<id>/` | One widget: `widget.json`, `screenshot.png` and an optional `README.md` |
| `spec/` | The widget spec, its JSON Schema and examples, copied from the app |
| `checker/` | The TV's own widget parser, run on a desktop JVM to check widgets |
| `tools/` | `build_index.py` checks every widget and writes the published index; `check_pr.py` holds pull requests to the ownership rules; `sync-from-app.sh` refreshes `spec/` and `checker/` from the app |

Every pull request is checked by CI: the widget must load on the TV, and it must follow
the rules in [CONTRIBUTING.md](CONTRIBUTING.md). The check that guards merging
(`.github/workflows/policy.yml`) runs the base branch's tools against the pull request's
widgets, so a pull request can't change the rules it's held to. `owners.json` maps each
id prefix to the GitHub logins that own it. See [SECURITY.md](SECURITY.md).

## How a merged widget reaches people

1. On every push to `main`, `.github/workflows/publish.yml` checks the widgets, builds
   `dist/` and deploys it to GitHub Pages at `https://mrlynn.github.io/artframe-widgets/`.
2. The site rewrites `artframe.mlynn.dev/widgets/index.json`, `widget.schema.json` and
   `/widgets/<id>/…` to that copy, so the published index is current within minutes, with
   no manual sync.
3. ArtFrameRemote fetches the index when someone opens **Screen › Cards › Browse
   Gallery…**. It doesn't poll, and nothing installs itself: a person picks a widget (or an
   update, when the gallery has a newer `version`), and it goes to the TV with the next
   **Apply to TV**. The TV never reads the index.

## Building the index

Needs JDK 17 and Python 3; nothing else. The script finds JDK 17 itself (`JAVA_HOME`,
then `/usr/libexec/java_home -v 17` on macOS, then Homebrew's `openjdk@17`, then `java`
on `PATH`).

```bash
tools/build_index.py --check          # check every widget
tools/build_index.py                  # check, then write dist/ (unsigned, with a warning)
ARTFRAME_INDEX_SIGNING_KEY=~/.artframe-keys/index-signing-current.key tools/build_index.py
                                      # signed, as CI does it
python3 -m unittest discover -s tools # the index tools' own tests
```

`dist/` holds `index.json`, its signature `index.json.sig`, each widget at `<id>/<version>.widget.json`, its
screenshot, and `widget.schema.json`. You only need to build it locally to preview; CI
publishes it.

### index.json

```json
{
  "format": 2,
  "checkedWith": "d4c793d",
  "issuedAt": "2026-11-03T15:00:00Z",
  "expires": "2026-12-03T15:00:00Z",
  "revoked": [{"id": "someone.widget", "versions": "*", "reason": "…"}],
  "widgets": [{
    "id": "artframe.github", "name": "GitHub repository", "description": "…",
    "icon": "star", "size": "small", "author": "ArtFrame", "version": "1.0.0",
    "homepage": null, "spec": 1,
    "sites": ["api.github.com"], "choosesSite": false, "needsKey": false,
    "file": "artframe.github/1.0.0.widget.json", "sha256": "…", "bytes": 1583,
    "screenshot": "artframe.github/screenshot.png"
  }]
}
```

- `sites`: every host the widget can contact; the TV refuses any other.
- `choosesSite`: one of its sources contacts a site the person types into its settings.
- `needsKey`: it has a secret setting, such as an API key.
- `file` and `screenshot` are relative to the index. Check `sha256` after downloading.
- `checkedWith`: the app commit whose parser checked these widgets.
- `issuedAt`, `expires`: when it was built, and 30 days on. ArtFrameRemote still shows an
  expired index but won't install from it, and refuses one older than the last it saw.
- `revoked`: from `revoked.json`. ArtFrameRemote turns those widgets off on the TV (matched
  by id, so sideloaded copies too; `versions` is `"*"` or a list) and offers to remove them.
  A revoked version is also left out of `widgets`.

### Signing

`index.json.sig` is the base64 Ed25519 signature of `index.json`'s exact bytes.
ArtFrameRemote checks it against two public keys built into the app (current and backup)
and rejects an index that fails, so changing anything in it, or serving it from anywhere
but `artframe.mlynn.dev`, stops it being used. `publish.yml` signs with the
`ARTFRAME_INDEX_SIGNING_KEY` repository secret (the base64 private key) and fails rather
than publish unsigned. Locally the variable can be the key or a path to a file holding it.
If the current key is lost or leaks, sign with the backup key and ship an app with a new
backup.
