# Word of the day

A new word each day: the word, how to say it, its part of speech, what it means and a
sentence using it. As a wide card the word sits on the left and its meaning on the right;
as a card or tile it stacks, and drops the example when space is tight.

**Data:** [Merriam-Webster's Word of the Day](https://www.merriam-webster.com/word-of-the-day)
RSS feed, `www.merriam-webster.com`, fetched every three hours. Nothing about you or your
settings is sent; the address is fixed.

**Setup:** none.

**How it reads the feed:** Merriam-Webster puts everything about the word in each item's
description, as `word • \pronunciation\ • part of speech`, then the meaning, then an example
starting `//`. The widget takes those apart with the `after` and `before` filters, so if
the feed's wording changes, those are the expressions to fix.

Needs an ArtFrame with RSS `description` and the `after` and `before` filters.
