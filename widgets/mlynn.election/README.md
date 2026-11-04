# Election results

Who's leading the 2026 Senate, House or Governor races (pick one in its settings): seats by
leading party, how many races are fully counted, and the three closest races by the gap
between the top two candidates, with how much is counted while counting goes on.

Data: [Open America's election results feed](https://openamerica.io/elections/api/),
`openamerica.io` (public, no key), which reads official state election offices. It covers
22 states, so seats elsewhere aren't counted, and it never calls a race: the card says
who leads, not who won. Its terms ask for the attribution line and the as-of time beside
the numbers, which the card shows. Refreshes every 5 minutes; the only thing sent is the
chosen office.

Needs an ArtFrame TV app with the `entries` filter and list `sortBy`; an older one turns
the widget away instead of drawing it wrong.
