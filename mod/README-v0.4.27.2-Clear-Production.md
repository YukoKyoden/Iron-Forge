# v0.4.27.2 — Clear production displays

The Kingdom Manager Stockpile tab now shows one named row per resource, with separate Stored and Expected gain columns. Amounts are exact, comma-separated counts rather than compact item-slot values. The Kingdom tab separately labels upkeep costs every five minutes. The Build tab reports meaningful units (beds, storage spaces, troop capacity, or material per cycle), rather than an unexplained capacity number.

Production details show resource cards with stored stock and expected gain in plain language. Selecting a resource shows each producer's output, location, next-cycle countdown, and paused state. Recent 60-second deliveries are secondary information in source tooltips, so a producer between cycles no longer appears to produce nothing. Taxes are explicitly five-minute income; resource producers remain six-minute production. A cycle yielding half an item carries the fraction forward as before.

Rates are transmitted in half-item units to preserve mine production such as 0.5 and 12.5 iron. The manager and production screen now use the same cached source list, including military outposts, and exclude paused sources from expected totals. This changes display precision only; stored inventory and actual resource generation remain unchanged. Marker right-clicks use the same plain production labels and separate detected counts from output.

Manager and production packet versions have changed; install the same JAR on client and server. Automated checks cover fractional rates, unit labels and time formatting, alongside the existing Forge build, saved-data, mine, marker and squad regressions. In-game GUI verification is still needed.
