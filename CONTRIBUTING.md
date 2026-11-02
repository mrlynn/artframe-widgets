# Adding a widget

1. Write it to the [spec](spec/SPEC.md), and try it on your own TV first (the spec's
   "Installing and testing" section says how).
2. Add a folder named after its id, in `widgets/`:

   ```
   widgets/yourname.thing/
     widget.json       the widget
     screenshot.png    what it looks like on the TV
     README.md         optional: what it shows, where the data comes from, any setup
   ```

3. Open a pull request. CI runs `tools/build_index.py --check --base origin/main`; run
   it yourself first to save a round trip.

## The rules

CI checks all of these except the last section.

**The widget has to load.** It's checked by the TV's own parser, so anything the spec
calls an error fails here too. It must be under 64 KB.

**Ids.** The id is `yourname.thing`: your GitHub name or a handle, a dot, then the
widget's name. The folder has the same name. An id without a dot is a built-in's, and
published widgets can't replace built-ins. `test.`, `example.`, `local.` and `my.` are
kept for people's own widgets. `artframe.` is for widgets the project maintains.

**Required here** (optional in the spec): `author`, `description`, and `version` as
`major.minor.patch`. Any change to `widget.json` must raise `version`.

**Screenshot.** A PNG of the widget on a TV, 300-1920 pixels wide, at most 1080 high,
under 1 MB. Crop to the card with a little of the art around it. The TV can take one:
`adb exec-out screencap -p > shot.png`.

**What reviewers check by hand:**

- The data source is a public, documented API that allows this use. No scraping, and
  nothing that needs a login beyond an API key the person supplies.
- Every site in `data` is needed, and sends only what the widget needs. The checker
  lists the sites in the index; the Mac app shows people what goes to each.
- A source with a person's key or location refreshes no more often than the data
  changes, and well within the API's free limits for one TV.
- It reads well at TV distance, in all four themes, and handles loading and errors.
- Nothing hateful, deceptive or for adults, and no ads.

Widgets can be removed if their API goes away or they stop meeting these rules.
