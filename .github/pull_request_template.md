**Widget:** `yourname.thing` (new / update)

What it shows, and where the data comes from:

**Author**

- [ ] I've run it on my own TV
- [ ] `tools/build_index.py --check` passes
- [ ] This pull request touches one widget folder (plus `owners.json` if I'm claiming my prefix)
- [ ] The API's terms allow this use, and the refresh rate is within its free limits
- [ ] The screenshot is from a TV, cropped to the card

<details><summary><b>Reviewer checklist</b></summary>

- [ ] "Pull request rules" passed (base-branch tools: ownership, one folder, lints)
- [ ] Every site is the real, documented API for this data. Any `new-host` site checked by hand (who runs it, terms, HTTPS, not a redirector)
- [ ] URLs carry only what the widget needs: no location, keys or settings sent to a site that doesn't need them; secrets go in headers where the API allows
- [ ] On an update: no new site, setting or `hud.` value slipped in without a reason
- [ ] Refresh intervals are reasonable for the API and the data
- [ ] Text, links and the screenshot are honest: no phishing ("scan this code", "call this number"), impersonation, ads, or adult or hateful content
- [ ] `author` and `homepage` match the contributor
- [ ] The screenshot matches what the widget really shows

</details>
