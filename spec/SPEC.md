# ArtFrame widget spec, version 1

A widget is a card on ArtFrame's information HUD. Every card the HUD shows, the
built-in clock, weather, calendar and the rest included, is a single JSON file
written to this spec. Yours are drawn by the same code, in the same theme,
font and accent colour as ours, and drift and dim with the art like ours do.

A widget is data, not code. It says where its data comes from and how to lay
it out; the TV fetches the data and draws it. There are no scripts, so a
widget can't do anything on the TV except show text, bars and lines.

- [The file](#the-file)
- [Settings](#settings)
- [Data](#data)
- [Expressions](#expressions)
- [Layout](#layout)
- [Where the HUD puts it](#where-the-hud-puts-it)
- [Loading, errors and missing settings](#loading-errors-and-missing-settings)
- [Limits](#limits)
- [Installing and testing](#installing-and-testing)

The examples in [`examples/`](examples) all work, and
[`widget.schema.json`](widget.schema.json) lets an editor check a file as you
type: add `"$schema": "widget.schema.json"` (or its path) at the top.

## The file

One widget, one file, named `<anything>.widget.json`:

```json
{
  "spec": 1,
  "id": "example.iss",
  "name": "Space station",
  "description": "Where the International Space Station is right now",
  "icon": "globe.americas",
  "size": "small",
  "title": "Space station",
  "data": {
    "iss": { "source": "http", "url": "https://api.wheretheiss.at/v1/satellites/25544", "refresh": 120 }
  },
  "layout": {
    "type": "text",
    "text": "{{ data.iss.latitude | fixed:1 }}°, {{ data.iss.longitude | fixed:1 }}°",
    "size": 20
  }
}
```

| Key | Required | What it is |
| --- | --- | --- |
| `spec` | yes | The spec version the file is written to: `1`. A TV that only knows older versions skips the file and says why. |
| `id` | yes | 1-64 characters: lowercase letters, digits, `.`, `_`, `-`. It's how `hud.json` lists the widget. Use a dotted prefix of your own (`yourname.thing`) so it can't clash with anyone else's. |
| `name` | yes | Shown in the Mac app. |
| `description` | | One line for the Mac app. |
| `icon` | | An [SF Symbol](https://developer.apple.com/sf-symbols/) name for the Mac app, such as `cloud.sun`. |
| `size` | | `small` (the default), `large` or `wide`. See [Where the HUD puts it](#where-the-hud-puts-it). |
| `card` | | `true` (the default) draws a card in the theme's style. `false` puts the widget straight on the blurred art, like the big clock. |
| `weight` | | Its share of a row next to other widgets, 0.25-4. Default 1. |
| `title` | | The card's label, in small capitals in the accent colour. A [template](#templates). |
| `settings` | | Things the person sets for this widget. See [Settings](#settings). |
| `data` | | Named data sources. See [Data](#data). |
| `needs` | | Messages shown instead of the widget until something is set up. See [below](#loading-errors-and-missing-settings). |
| `layout` | yes | What's inside the card: one [layout node](#layout). |
| `author`, `version`, `homepage` | | For people; the TV ignores them. |

Keys starting with `x-` are ignored, for your own notes. Any other unknown key
is an error, so typos don't fail silently.

**Replacing a built-in.** A file whose `id` is a built-in's (`clock`,
`weather`, `sun`, `air`, `calendar`, `markets`, `headlines`) replaces it. The
built-in files are in
[`built-in`](built-in):
copy one, change it, and install it under the same id.

## Settings

A widget can ask for settings, which the Mac app shows as a form and saves in
`hud.json`:

```json
"settings": [
  { "key": "url", "label": "Feed URL", "type": "text", "default": "https://www.nasa.gov/feed/" },
  { "key": "count", "label": "Stories", "type": "number", "default": 6, "min": 1, "max": 10 },
  { "key": "compact", "label": "Hide the times", "type": "toggle", "default": false },
  { "key": "units", "label": "Units", "type": "choice", "default": "km",
    "options": [{ "value": "km", "label": "Kilometres" }, { "value": "mi", "label": "Miles" }] }
]
```

| Key | What it is |
| --- | --- |
| `key` | The name expressions use: `settings.url`. A letter, then letters, digits or `_`. |
| `label` | The form's label. |
| `type` | `text` (default), `number`, `toggle` or `choice`. |
| `default` | The value until the person changes it. |
| `options` | For `choice`: `{ "value", "label" }` pairs. |
| `min`, `max` | For `number`. |
| `help` | A line under the field. |
| `secret` | `true` shows a password-style field, for API keys. Only a `text` setting can be secret, and it can't have a `default`. The real value only goes into the `url` and `headers` of a source with a [fixed site](#which-sites-a-widget-contacts); anywhere else, `settings.<key>` reads `"•••"` once it's set and `""` until then, enough for a `needs` check. The value is still stored in `hud.json` on the Mac and the TV, like calendar links are. |

In `hud.json` the values sit under the widget's id. Settings not saved fall
back to their `default`:

```json
"widgetSettings": {
  "example.one-feed": { "url": "https://hnrss.org/frontpage", "count": 5 }
}
```

The HUD's own settings are readable too, as `hud.*`:

| Name | Value |
| --- | --- |
| `hud.location` | The city, or null when none is set |
| `hud.units` | `"metric"` or `"imperial"` |
| `hud.metric` | `true` for metric |
| `hud.clock24h` | `true` for a 24-hour clock |
| `hud.calendars` | The calendars' names |
| `hud.calendarDays` | How many days of calendar to show |
| `hud.markets` | The market symbols |
| `hud.feeds` | The feeds' names |
| `hud.headlines` | How many headlines to show |
| `hud.theme` | `glass`, `gallery`, `minimal` or `paper` |

## Data

`data` names each source. Its result is `data.<name>` in expressions:

```json
"data": {
  "iss": { "source": "http", "url": "https://api.wheretheiss.at/v1/satellites/25544", "refresh": 120 },
  "wx": { "source": "weather" }
}
```

Every source takes these:

| Key | What it is |
| --- | --- |
| `source` | `http`, or one of the built-in providers below. |
| `loading` | Shown while there's no data yet. Default `Loading…` |
| `error` | Shown when the fetch failed and there's no earlier data. Default `Couldn't load` |
| `optional` | `true` draws the layout even without this source's data, for widgets that handle that themselves. |

Widgets that ask for the same thing share one fetch, so two widgets using the
weather cost one request.

### HTTP

| Key | What it is |
| --- | --- |
| `url` | `https://`, with the site written out (Android turns down plain `http://`). A [template](#templates) after that, so it can use settings: `"https://example.com/q?city={{ settings.city \| url }}"`. See [Which sites a widget contacts](#which-sites-a-widget-contacts). |
| `format` | `json` (the default), `rss` (RSS 2.0 or Atom) or `text`. |
| `refresh` | Seconds between fetches, 60-86400. Default 900 (15 minutes). |
| `headers` | Request headers, as templates: `{ "Authorization": "Bearer {{ settings.token }}" }` |

What you get:

- `json`: the response, as is. Objects, arrays, strings, numbers.
- `rss`: `{ "items": [{ "title", "source", "published" }] }`, newest first as the feed lists them, up to 50. `published` is ISO-8601.
- `text`: the body as a string.

Responses over 512 KB are refused. Requests give up after 10 seconds to connect or 20 to read. The
TV has no cookies and no login, so the API has to work with a URL and headers
alone. Fetches start when the HUD is about to show, so the first look after a
change can say `Loading…`.

#### Which sites a widget contacts

A widget can put your settings, city or calendar names into a URL, so it has
to say up front where that URL goes. The Mac app shows these sites before you
turn a widget on, and the TV refuses to contact any other.

- **A fixed site.** The scheme and host are written out, followed by `/`, `?`
  or the end of the URL, before any `{{ }}`:
  `https://api.example.com/v1/{{ settings.city | url }}` contacts
  `api.example.com` and nothing else.
  `https://{{ settings.region }}.example.com/` and
  `https://api.example.com{{ settings.path }}` are both errors, because a
  template there could change the site.
- **A site the person picks.** A URL that is a single text setting and
  nothing else, `"{{ settings.feed }}"`, contacts whatever the person types
  (it too has to be `https://`).
  None of their other data goes with it: the `headers` must be plain text, and
  [secret](#settings) settings stay hidden.

Redirects are followed, up to 5, as long as they stay on `https`. The
`headers` go only to the site the URL names. Widgets can't load
images or anything else from the web: all a widget contacts are its sources.

### Built-in providers

These use the HUD's own settings (the city, calendars, symbols and feeds set
in the Mac app's Information page), so a widget built on them follows those
settings. Times are the TV's local time, as ISO-8601 text such as
`2026-11-02T06:34`.

**`weather`** (Open-Meteo; needs `hud.location`)

```
place, temp, feelsLike, code, isDay, icon, summary, humidity, wind, windUnit, metric,
sun:  { sunrise, sunset, daylight ("9h 51m"), change ("2 min less tomorrow" or "") } or null
days: [{ date, code, icon, summary, high, low, precip, sunrise, sunset }]   (5 days, today first)
```

`icon` is an emoji and `summary` a word or two ("Partly cloudy"), from the
WMO weather `code`. `precip` is the chance of rain, 0-100. Temperatures and
`wind` are in the HUD's units.

**`air`** (Open-Meteo air quality; needs `hud.location`)

```
aqi, european, index ("US AQI" or "European AQI"), category ("Good"...), color ("#4ADE80"),
pm25 (µg/m³, or null), pollen: { kind ("Grass"), count (grains/m³) } or null
```

The European index is used when the units are metric. Pollen is only reported in Europe.

**`moon`** (computed on the TV; never fails)

```
phase ("Waxing crescent"), icon ("🌒"), age (0 = new, 0.5 = full)
```

**`markets`** (Yahoo Finance; needs `hud.markets`)

```
quotes: [{ symbol, name, price, prevClose, change, percent, up, points }]
```

`points` is the day's prices every 5 minutes, for a `sparkline`.

**`calendar`** (your iCal links; needs `hud.calendars`)

```
events: [{ title, calendar, color, start, end, allDay, day, live }]
```

Sorted by start. `day` is the date the event is listed under (today, for one
that started earlier and is still on); `live` is true while it's happening.

**`headlines`** (your feeds; needs `hud.feeds`)

```
items: [{ title, source, published }]
```

Taken in turn from each feed, `hud.headlines` of them.

## Expressions

Expressions read values and shape them for display. They appear bare in a
few attributes (`when`, `items`, `groupBy`, `points`, `baseline`, and a
need's `unless`) and inside `{{ }}` everywhere else.

```
data.wx.temp | round                          59
data.q.quotes[0].name                         S&P 500
data.wx.days.0.high                           the same as data.wx.days[0].high
q.up ? 'up' : 'down'
data.wx.place || 'Weather'                    the first that's set
d.precip >= 30 && !layout.compact
'PM2.5 ' + (data.air.pm25 | round) + ' µg'
```

- **Names**: `data`, `status`, `settings`, `hud`, `layout`, `now` (the local
  time, ISO-8601), and inside a list the item (see `as`), `index` and, in a
  group header, `group`.
- **Paths**: `a.b`, `a[0]`, `a.0`, `a['some key']`. `.length` on a list or text.
  A path that doesn't exist is `null`, never an error, and shows as nothing.
- **Literals**: numbers, `'text'` or `"text"`, `true`, `false`, `null`.
- **Operators**, loosest first: `x ? y : z`, `||`, `&&`, `== != < <= > >=`,
  `+ -`, `* / %`, `!` and unary `-`. `+` joins text when either side is text.
  `||` and `&&` give back one of their sides, so `a || 'default'` works.
- **Truth**: `null`, `false`, `0`, `''`, an empty list and an empty object are false.
- **Filters** go on the end with `|`, take arguments after `:`, and chain left
  to right: `q.percent | abs | fixed:2`. A filter applies to everything before
  it, so use parentheses to filter part of an expression, and around a filter
  inside `? :`.

### Filters

| Filter | Does | Example → result |
| --- | --- | --- |
| `round`, `round:n` | Rounds (to `n` places) | `58.6 \| round` → `59` |
| `fixed:n` | Exactly `n` decimals, as text | `1.5 \| fixed:2` → `1.50` |
| `int` | Drops the fraction | `5.9 \| int` → `5` |
| `abs` | Without its sign | `-2 \| abs` → `2` |
| `number`, `number:n` | Thousands separators, up to `n` decimals (2) | `27600.4 \| number:0` → `27,600` |
| `price` | Like a stock price: no decimals from 10,000, 4 below 1 | `1234.5 \| price` → `1,234.50` |
| `signed` | `+` in front of positive numbers | `1.5 \| signed` → `+1.5` |
| `default:x` | `x` when the value is null or empty | `data.x \| default:'—'` |
| `upper`, `lower`, `capitalize`, `trim` | Text case and spacing | |
| `truncate:n` | At most `n` characters, ending in `…` | |
| `url` | Encoded for a URL query | `'New York' \| url` → `New+York` |
| `count` | Items in a list, characters in text | |
| `first`, `last` | One end of a list | |
| `take:n`, `skip:n`, `reverse` | Part of a list | |
| `join:sep` | A list as text, `, ` by default | |
| `map:'key'` | Each item's `key` | `quotes \| map:'price'` |
| `where:'key'` | Items whose `key` is true | `quotes \| where:'up' \| count` |
| `sum`, `min`, `max` | Of a list of numbers | |
| `time`, `time:true` | `9:30 AM`, or `09:30` with a 24-hour clock (the HUD's setting unless given) | |
| `date:'pattern'` | Any [date pattern](https://developer.android.com/reference/java/time/format/DateTimeFormatter#patterns); `EEE, MMM d` by default | `date:'d MMMM'` → `2 November` |
| `day` | `Today`, `Tomorrow`, `Yesterday`, else `Wednesday, Nov 4` | |
| `weekday` | `Today`, else `Mon` | |
| `daysUntil` | Whole days from today to the date (negative once past) | |
| `ago` | `just now`, `5 min ago`, `3 h ago`, `2 days ago` | |
| `wmoIcon`, `wmoText` | A WMO weather code (Open-Meteo's `weather_code`) as an emoji or words | |

Dates can be ISO-8601 text (with or without a time or offset), RSS-style
dates, or Unix time in seconds or milliseconds.

### Templates

Text attributes are templates: text with expressions in `{{ }}`.
`"Feels {{ data.wx.feelsLike | round }}°"`. When an attribute is exactly one
`{{ }}`, the value keeps its type, which is how numbers and colours can be
computed: `"size": "{{ layout.tight ? 76 : 104 }}"`.

## Layout

`layout` is a tree of nodes. Each has a `type`, and any of these:

| Attribute | What it does |
| --- | --- |
| `when` | An expression. The node (and everything in it) is left out when it's false. |
| `flex` | Takes a share of the leftover space along its parent's direction. |
| `width`, `height` | Fixed size in dp. The HUD is 960×540 dp. |
| `before` | Space in dp before this node, along its parent's direction. Overrides the parent's `gap`. |

### Containers

**`column`**, **`row`**: children stacked down or across.

| Attribute | |
| --- | --- |
| `children` | The nodes inside. |
| `gap` | dp between children. |
| `align` | Column: `start` (children full width, the default), `center` or `end`. Row: `center` (the default), `start`, `end` or `baseline` (text lined up on its baseline). |

**`list`**: a node repeated for each item in a list.

| Attribute | |
| --- | --- |
| `items` | An expression giving the list: `"data.q.quotes"` |
| `item` | The node drawn for each one. |
| `as` | The item's name in `item`, `item` by default. `index` counts from 0. |
| `max` | At most this many items (and never more than 200). |
| `direction` | `column` (the default) or `row`. |
| `gap` | dp between items. |
| `empty` | A muted line shown when the list is empty. Without it, an empty list takes no space. |
| `groupBy` | An expression per item. When it changes, `header` is drawn first, with the value as `group`. A header is never the last thing in a list. |
| `header` | The node drawn at the start of each group. |
| `groupGap` | dp before each header after the first. Default 6. |

### Content

**`text`**

| Attribute | |
| --- | --- |
| `text` | A template. |
| `size` | In sp, 15 by default. Labels are 11, body text 14-15, the big clock 104. |
| `font` | `thin`, `light`, `regular` (default) or `medium`, in the person's chosen font. Serif and condensed fonts have no thin cut and use light. |
| `color` | See [Colours](#colours). `text` by default. |
| `lines` | Most lines before `…`. 1 by default; 0 for no limit. |
| `tabular` | `true` for digits of equal width, so numbers don't jiggle. |
| `tracking` | Letter spacing in ems: `0.12`. |
| `opacity` | 0-1. |
| `align` | `start`, `center` or `end`, within the text's own width. |

**`label`**: the small accent capitals a card's `title` uses, for a second
heading inside a card. Takes `text` and `color`.

**`clock`**: a live time, updated every minute without refetching anything.
Takes the text attributes (except `text` and `lines`), plus:

| Attribute | |
| --- | --- |
| `format` | A [date pattern](https://developer.android.com/reference/java/time/format/DateTimeFormatter#patterns). `h:mm` by default. |
| `format24` | Used instead when the HUD is set to a 24-hour clock. |

**`sparkline`**: a small line chart, filled below.

| Attribute | |
| --- | --- |
| `points` | An expression giving a list of numbers. |
| `baseline` | An expression for a faint reference line (a market's previous close). |
| `color` | `accent` by default. |

52×22 dp unless `width`/`height` say otherwise.

**`bar`**: a rounded block of colour, 4×16 dp by default (the calendar's
colour stripe). Takes `color` and `radius` (dp).

**`spacer`**: empty space. `size` in dp, or with no `size` it takes all the
leftover space (`flex: 1`).

### Colours

`color` takes one of the theme's colours, so the widget fits every theme:

| Name | |
| --- | --- |
| `text` | The main text colour: light on glass and gallery cards, dark on paper. |
| `muted` | Secondary text. |
| `accent` | The person's accent, or the colour taken from the painting. |
| `up`, `down` | Green and red, tuned for the theme. |

Or `#RRGGBB` / `#AARRGGBB`. On paper's light cards, hex colours are deepened so
they stay readable. A colour can come from the data:
`"color": "{{ data.air.color }}"`.

On a `card: false` widget, `text`, `muted` and `accent` switch to the colours
meant for text straight on the art.

## Where the HUD puts it

`hud.json`'s `widgets` lists widgets by id, in order:

```json
"widgets": ["clock", "weather", "example.iss", "calendar", "markets", "headlines"]
```

- **`small`** widgets stack in a 330 dp column on the left. A `card: false`
  widget (the clock) sits at the top with the cards gathered at the bottom.
- **`large`** widgets share one row to the right, by `weight`.
- **`wide`** widgets get a full-width row each, above the large row if listed
  before every large widget, below it otherwise.
- With no large or wide widgets, `card: false` widgets go at the top and the
  small cards share a row along the bottom, by `weight`.

A widget is told where it landed, as `layout.*`, so it can fit:

| Name | |
| --- | --- |
| `layout.area` | `column`, `top`, `row` or `wide` |
| `layout.size` | Its `size` |
| `layout.compact` | The left column is crowded (three cards, or a clock and two): drop optional rows. The weather card drops its forecast. |
| `layout.tight` | The left column is very crowded (a clock and three cards): drop more. |

Ids with no installed widget are skipped, so a `hud.json` that lists a widget
still works on a TV without it.

## Loading, errors and missing settings

Before drawing `layout`, the card shows its `title` and then the first of:

1. A **need**: each `needs` entry is `{ "unless": <expression>, "message": <template> }`.
   The first whose `unless` is false is shown, muted, instead of the widget.
   `{ "unless": "hud.location", "message": "Add a location to hud.json" }`
2. A source with no data yet, unless it's `optional`: its `error` text if the
   fetch failed, its `loading` text otherwise.

So `layout` only has to handle the data being there. A failed refresh keeps
the last good data on screen.

`status.<name>` is `ok`, `loading`, `error` or `off` (nothing to fetch, such as
the weather with no city), for `optional` sources.

## Limits

- A widget file is at most 64 KB, with at most 400 layout nodes, 20 deep.
- HTTP sources: `https` only, 512 KB per response, at most one fetch a
  minute per source.
- Lists draw at most 200 items.
- A card doesn't scroll or shrink its text: what doesn't fit is cut off at
  the card's edge. Large cards are about 230 dp tall next to each other, and
  less with a wide row; use `max` on lists and `lines` on text to fit.
- No scripts, no images (yet), no tapping: the TV is a screensaver.

A file that breaks a rule isn't loaded. The TV logs why (`adb logcat -s
WidgetCatalog`), and the Mac app shows it next to the widget.

## Installing and testing

**With the Mac app**: Display › Widgets › Add Widget… copies a
`.widget.json` into the data folder's `widgets` folder, checks it, and lists
it with the built-ins. Turn it on, fill in its settings, and Save: the
widgets folder goes to the TV with `hud.json`.

**From the command line**:

```bash
cp my.widget.json artframe/widgets/   # next to hud.json
./tv.sh push-widgets                  # copies the folder to the TV
```

then add its id to `widgets` in `hud.json` and `./tv.sh push-hud`. The HUD
picks up changes to either within a second.

**Versioning.** `spec` only goes up when a change would make old TVs draw a
widget wrongly. New node types, filters and providers within a version are
added so that a file using them fails validation on an older TV rather than
drawing half a card; check the TV app version noted in the changelog.
