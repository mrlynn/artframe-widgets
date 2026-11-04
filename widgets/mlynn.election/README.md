# Election results

Who's leading the 2026 Senate, House or Governor races, from official state counts.
Every number is a *lead*, not a result: the feed never calls a race.

The default card shows how many races each party leads with a split bar, how many races
are fully counted and how many are still counting, then up to five races with each one's
leader, the top two vote shares as party-colored bars, the gap in points, and how much is
counted.

## Settings

- **Races**: Senate, House or Governor.
- **Show**: *Closest races* (smallest gap between the top two first), *Still counting*
  (least counted first, so the least settled races lead), or *Every race, by state*.
- **One race in full**: type a race key such as `TX` (Senate, Governor) or `TX-07`
  (House) to show that race alone: total votes, how much is counted, the lead in votes,
  and every candidate with party, incumbent marker, votes, share and a bar. Leave it blank
  for the overview. This is the drill-down: the TV is a screensaver, so it's picked in
  settings rather than with the remote.

## Data

[Open America's election results feed](https://openamerica.io/elections/api/),
`openamerica.io` (public, no key), which reads official state election offices. It covers
22 states, so races elsewhere aren't in the tally. Its terms ask for the attribution line
and the as-of time beside the numbers; the card always shows both. Refreshes every
5 minutes. The only thing sent to `openamerica.io` is the chosen office; the view and
race settings never leave the TV.

The feed sometimes reports a counted share above 100% (some Connecticut races read in
the thousands). The card shows anything at or above 100% as "counted" rather than the
raw number.

Needs an ArtFrame TV app with the `entries` filter and list `sortBy`; an older one turns
the widget away instead of drawing it wrong.

The screenshot is a placeholder drawn from live Senate data, not a capture from a TV.
