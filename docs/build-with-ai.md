# Build a widget with AI

A widget is one JSON file written to a spec, which is exactly the kind of thing
an AI assistant is good at. Tell it what you want on your TV and point it at the
spec, and it can write the file, find an API for it and check its own work.

You still try it on your TV, and if you share it, the gallery reviews it like any
other widget. The prompts below are written so the assistant reads the spec
before it writes anything, uses the API's real field names, and stays inside the
rules the TV enforces.

## Before you start

- **Know what you want on the card**, in a sentence: "the next three departures
  from my train station", "my Strava distance this month", "the surf at my
  beach". If you know the API, name it. If not, the assistant can find one.
- **Pick a handle** for the widget's id: your GitHub name or a nickname. The id
  becomes `yourhandle.thing`.
- **Have ArtFrameRemote** on your Mac, set up with your TV, so you can try what
  the assistant writes.

In each prompt, replace the parts in `[square brackets]`.

## With Claude Code, Codex or Cursor

Coding agents do this best. They can fetch the API to see its real responses,
and run the same check the gallery runs, which uses the TV's own parser. Work in
a copy of this repository: its `AGENTS.md` tells the agent the rules, and
Claude Code, Codex and Cursor all read it without being asked.

```bash
git clone https://github.com/mrlynn/artframe-widgets
cd artframe-widgets
```

Clone your fork instead if you mean to send the widget to the gallery. The check
needs JDK 17 and Python 3; without them the agent can still write the widget,
but can't run it through the TV's parser.

Then start the agent in that folder:

- **Claude Code**: run `claude` and paste the prompt.
- **Codex**: run `codex` and paste the prompt. In Codex on the web, connect
  your fork and paste it as a task.
- **Cursor**: open the folder, open the chat in Agent mode, and paste the
  prompt.

```text
Build an ArtFrame widget that shows [what you want on the TV]. Get the data from [the API you have in mind, or: a free public API you find]. Use [yourhandle] as the id prefix.

1. Read AGENTS.md, then all of spec/SPEC.md. The spec is the only source of truth: use only the keys, node types, filters and data providers it lists. Look at two or three widgets in widgets/ and spec/examples/ for style.
2. Choose the API: public and documented, https, no login (an API key I paste into the widget's settings is fine), and its terms allow this use. Fetch a real response with curl and build every expression from the field names you actually see.
3. Write widgets/[yourhandle].[thing]/widget.json, and a short README.md beside it: what the widget shows, where the data comes from, and any setup.
4. Run tools/build_index.py --check and fix every problem it reports for this widget until "no screenshot.png" is the only one left. The screenshot is mine to take, on the TV.
5. Tell me which sites the widget contacts and what it sends each one, which settings I'll need to fill in, and anything you weren't sure of.
```

## With ChatGPT, Claude or Gemini in the browser

A chat assistant can write the widget too. It can't run the check, so the
prompt carries the rules it's most likely to break. If it can't open links,
download [SPEC.md](https://raw.githubusercontent.com/mrlynn/artframe-widgets/main/spec/SPEC.md)
and attach it to the chat.

It helps a lot to paste a real response from the API: open the API's URL in your
browser and copy what comes back. Otherwise the assistant has to guess the field
names, and guesses are the most common reason a widget shows nothing.

```text
Write an ArtFrame widget that shows [what you want on the TV]. Get the data from [the API you have in mind, or: a free public API you find]. Use [yourhandle] as the id prefix.

An ArtFrame widget is one JSON file that a Google TV draws as a card over art. It's data, not code, and the TV refuses a file that breaks its rules. Read the whole spec before you write anything; it's the only source of truth:
https://raw.githubusercontent.com/mrlynn/artframe-widgets/main/spec/SPEC.md
Widgets that work: https://github.com/mrlynn/artframe-widgets/tree/main/widgets
If you can't open the spec, say so and stop, and I'll attach it.

[Optional: a real response from the API, pasted here.]

The rules people get wrong:
- "spec": 1, an id like yourhandle.thing, "$schema": "https://artframe.mlynn.dev/widgets/widget.schema.json", and only keys the spec lists. An unknown key is an error; notes go under keys starting "x-".
- Data sources: https only, at most 6. Write the site out in full before any {{ }}, as in https://api.example.com/v1?q={{ settings.city | url }}. Never build the host from a template. No IP addresses, localhost or link shorteners.
- "refresh" is in seconds, 60 to 86400, and at least 300 when the URL or headers use any settings. or hud. value. No faster than the data changes.
- An API key is a text setting with "secret": true and no default. Send it in "headers" if the API allows that.
- Use only the node types, filters and colours the spec lists; don't invent any. Put parentheses around a filter used inside ? :.
- Use field names from a real response. Tell me any you had to guess.
- Cards don't scroll, and small cards are 330 dp wide. Give every list a "max" and long text "lines". Drop optional rows when layout.compact or layout.tight is true.
- Explain missing setup with "needs", and give each source "loading" and "error" text.
- Text is only ever shown: no web addresses, phone numbers, or asking viewers to scan or call anything.

Reply with the complete file, named [yourhandle].[thing].widget.json, then one line per site it contacts saying what it sends there, then anything you weren't sure of.
```

## Try it on your TV

1. Save the file as `something.widget.json`.
2. In ArtFrameRemote, open **Screen › Cards** and click **Add Widget…**. Choose
   the file.
3. Fill in its settings, then click **Apply to TV**. The card shows the next
   time the information screen does.

Look at it from the sofa, next to the other cards you use. If something's cut
off or hard to read, tell the assistant what you see.

## When it won't load

The Mac app lists a widget that didn't load with every problem spelled out. Give
those to the assistant:

```text
ArtFrameRemote won't load my ArtFrame widget. It reports:

[the problems listed under the widget in Screen › Cards]

The file:

[the whole widget.json]

Fix each problem using only what the widget spec allows:
https://raw.githubusercontent.com/mrlynn/artframe-widgets/main/spec/SPEC.md
Change nothing else. Reply with the whole fixed file and one line per change.
```

A card that loads but says `Couldn't load`, or shows blanks, usually has the
wrong field names. Paste a real response from the API along with the file, and
ask the assistant to match the expressions to it.

## Sending it to the gallery

Widgets written with an assistant are welcome. You open the pull request, so you
answer for it: you've run it on your TV, the screenshot is from your TV, and
you've read the API's terms. Everything else is in
[CONTRIBUTING.md](https://github.com/mrlynn/artframe-widgets/blob/main/CONTRIBUTING.md):
one folder, one widget, and CI tells you within minutes whether the TV will
load it.

A coding agent can open the pull request for you once you've added
`screenshot.png`. Ask it to follow CONTRIBUTING.md, and read what it writes
before you send it.
