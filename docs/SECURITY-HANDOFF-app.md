# Security handoff: changes for the ArtFrame app and spec

The gallery repo now holds submissions to strict rules (see [SECURITY.md](../SECURITY.md)),
but a widget installed some other way, or one that gets past review, is limited only by
what the TV and the Mac app enforce. These are the app-side and spec-side changes that
would back the gallery's rules up. File references are to the app repo as of this review.

## What's already enforced (no change needed)

- HTTPS only, and only the declared host (`Providers.kt:39-41`).
- Responses capped at 512 KB, 10 s connect and 20 s read timeouts (`hud/Sources.kt`, `Http.get`).
- Headers (so API keys) go only to the original host, not to redirect targets.
- Layout capped at 400 nodes, depth 20 (`WidgetSpec.kt`); files over 64 KB skipped (`WidgetCatalog.kt`).
- The Mac app checks the gallery file's SHA-256 before installing (`WidgetGallery.swift`)
  and shows the sites a widget contacts (`WidgetBrowser.swift`).

## Recommended changes, most important first

1. **Redirects stay on the declared host.** `Http.get` follows up to 5 redirects to any
   HTTPS host. A widget's declared site can bounce requests (with the URL's query, which
   may hold location or settings) anywhere. Follow redirects only to the same host (or the
   same registrable domain), and treat anything else as an error.
2. **Cap the number of sources.** `WidgetSpec.kt` has no limit. Match the gallery: at most
   6, and ignore or reject the rest. Put the number in the spec.
3. **Refresh floor for personal data.** Refresh is clamped to 60-86400 s. When a URL
   template uses `hud.*` or `settings.*`, clamp to at least 300 s so a widget can't stream
   a viewer's location or settings to its site every minute. Put the rule in the spec.
4. **Re-ask when an update adds sites.** When the Mac app updates a widget, or the TV
   picks up a changed file, and the new version contacts a site the old one didn't (or
   uses a setting or `hud.` value the old one didn't), show the change and ask again
   rather than updating silently.
5. **Show what each URL sends.** Next to each site in the install sheet, list which HUD
   values (especially location) and settings go to it, and flag secrets sent in a URL
   rather than a header.
6. **Raw IPs and shorteners.** Reject a declared host that is an IP literal, `localhost`,
   a `.local` name, or a known link shortener, so a widget can't reach the local network
   or hide where it goes.
7. **Per-widget fetch budget.** Beyond the refresh clamp, cap total requests per widget
   per hour, so many sources or errors that retry can't turn a TV into a traffic source.
8. **Text is just text.** Make sure templated text is never turned into a link, QR code or
   anything actionable, and consider a spec note that widgets mustn't ask viewers to scan,
   call or visit something (phishing on a big screen is the main content risk).
9. **Sign the index.** The SHA-256 in `index.json` protects the widget file, but not the
   index itself, which comes over HTTPS from the site. Signing `index.json` (a key the Mac
   app pins) would stop a compromised host or CDN from serving a different widget with a
   matching hash.
10. **Pin the gallery host.** The Mac app should only accept index and widget URLs on
    `artframe.mlynn.dev` (and reject any `file` path in the index that escapes `<id>/`).

## Spec text to add

- "A widget has at most 6 data sources."
- "If a source's URL uses `hud.` or `settings.` values, its refresh is at least 300 seconds."
- "Redirects are followed only within the source's host."
- "Hosts must be DNS names; IP addresses and link shorteners are not allowed."
- "Text in a widget is shown, never acted on. Don't ask viewers to scan, call or visit anything."
