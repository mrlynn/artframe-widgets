# Notes for AI agents

This repository is the gallery of widgets for ArtFrame's information HUD, the
cards a Google TV draws over art. Each widget is one JSON file. People mostly
come here with an agent to build a widget, so that's what these notes cover.
People's prompts for it are in [docs/build-with-ai.md](docs/build-with-ai.md).

## The spec is the only source of truth

Read all of [spec/SPEC.md](spec/SPEC.md) before writing a widget. A widget is
data, not code: the TV parses it strictly, and a file that breaks a rule isn't
loaded. Use only the keys, node types, attributes, filters, colours and data
providers the spec lists. Don't carry over ideas from other widget systems; if
the spec doesn't say it, it doesn't exist. [spec/widget.schema.json](spec/widget.schema.json)
is a JSON Schema for editors, and [spec/examples/](spec/examples) and
[widgets/](widgets) are working widgets to copy patterns from.

## Building a widget

1. Ask for the person's handle if you don't know it. The id is
   `handle.thing`, lowercase. `artframe.` is the project's own; `test.`,
   `example.`, `local.` and `my.` are for widgets people keep to themselves,
   and the gallery refuses them.
2. Choose the API: public and documented, `https`, no login beyond an API key
   the person pastes into the widget's settings, and terms that allow this use.
   No scraping.
3. Fetch a real response (`curl -s <url> | head -c 4000`) and write every
   expression from the field names you see. Wrong field names are the most
   common reason a widget shows nothing.
4. Write `widgets/<id>/widget.json` and a short `README.md` beside it: what it
   shows, where the data comes from, and any setup.
5. Run `tools/build_index.py --check` (JDK 17 and Python 3) and fix every
   problem it reports for this widget. Until the person adds a screenshot,
   `no screenshot.png` is expected; nothing else is. If JDK 17 isn't there, say
   so, check the file against the schema by hand, and tell the person it hasn't
   been through the TV's parser.
6. Tell the person which sites it contacts and what it sends each, and which
   settings they'll fill in.

You can't take `screenshot.png`: it has to come from the person's TV
(`adb exec-out screencap -p > shot.png`, cropped to the card, 300-1920 px wide,
at most 1080 high, under 1 MB). Say so rather than making one.

## Rules that are easy to miss

- `"spec": 1` and `"$schema": "https://artframe.mlynn.dev/widgets/widget.schema.json"`.
  An unknown key is an error; notes go under keys starting `x-`.
- In the gallery, `author`, `description` and a `version` like `1.0.0` are
  required, and any change to `widget.json` must raise `version`.
- At most 6 HTTP sources. `https` only, with the site written out before any
  `{{ }}`: `https://api.example.com/v1?q={{ settings.city | url }}`. Never
  build the host from a template. No IP addresses, `localhost`, `.local` or
  link shorteners.
- `refresh` is seconds, 60-86400, and at least 300 when the `url` or `headers`
  use `settings.` or `hud.` values. Don't refresh faster than the data
  changes, and stay well within the API's free limits for one TV.
- An API key is a `text` setting with `"secret": true` and no `default`. Send
  it in `headers` when the API allows; in the URL it can end up in logs.
- Put parentheses around a filter used inside `? :`. A missing path is `null`,
  not an error, so a typo shows as nothing.
- Cards don't scroll or shrink text. Small cards are 330 dp wide; large ones
  about 230 dp tall. Give lists `max` and text `lines`, and drop optional rows
  when `layout.compact` or `layout.tight` is true.
- Use `needs` to explain missing setup, and `loading` and `error` on sources.
- Colours are `text`, `muted`, `accent`, `up`, `down` or hex, so the widget
  works in all four themes.
- Text is shown, never acted on. Don't put web addresses, phone numbers, or
  "scan" or "call" wording in a widget's text.

## What not to touch

A pull request adds or changes one widget, in `widgets/<id>/`, plus `owners.json`
the first time a person claims their prefix. `tools/`, `checker/`, `spec/` and
`.github/` belong to the maintainers; `spec/` and `checker/` are copied from the
app and get overwritten. Don't change other people's widgets. The full rules are
in [CONTRIBUTING.md](CONTRIBUTING.md).
