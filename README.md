# ArtFrame widgets

Widgets for [ArtFrame](https://artframe.mlynn.dev)'s information HUD, the cards that sit
over the art on your TV. Each one is a single JSON file written to the
[widget spec](spec/SPEC.md): data, not code, so a widget can only show text, bars and
lines, and can only contact the sites it names.

## Installing one

In ArtFrameRemote on your Mac, open **Display › Widgets**, find the widget and add it.
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
| `tools/` | `build_index.py` checks every widget and writes the published index; `sync-from-app.sh` refreshes `spec/` and `checker/` from the app |

Every pull request is checked by CI (`.github/workflows/check.yml`): the widget must load
on the TV, and it must follow the rules in [CONTRIBUTING.md](CONTRIBUTING.md).

## How a merged widget reaches people

1. On every push to `main`, `.github/workflows/publish.yml` checks the widgets, builds
   `dist/` and deploys it to GitHub Pages at `https://mrlynn.github.io/artframe-widgets/`.
2. The site rewrites `artframe.mlynn.dev/widgets/index.json`, `widget.schema.json` and
   `/widgets/<id>/…` to that copy, so the published index is current within minutes, with
   no manual sync.
3. ArtFrameRemote fetches the index when someone opens **Display › Widgets › Browse
   Gallery…**. It doesn't poll, and nothing installs itself: a person picks a widget (or an
   update, when the gallery has a newer `version`), and it goes to the TV with the next
   **Save**. The TV never reads the index.

## Building the index

Needs JDK 17 and Python 3; nothing else. The script finds JDK 17 itself (`JAVA_HOME`,
then `/usr/libexec/java_home -v 17` on macOS, then Homebrew's `openjdk@17`, then `java`
on `PATH`).

```bash
tools/build_index.py --check          # check every widget
tools/build_index.py                  # check, then write dist/
```

`dist/` holds `index.json`, each widget at `<id>/<version>.widget.json`, its
screenshot, and `widget.schema.json`. You only need to build it locally to preview; CI
publishes it.

### index.json

```json
{
  "format": 1,
  "checkedWith": "d4c793d",
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
