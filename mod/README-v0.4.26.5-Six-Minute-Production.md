# Kingdoms & Claims 0.4.26.5

Farms, mines, lumber camps and passive military outposts now create their existing resource batch every SIX minutes (360 server seconds / 7200 ticks). Batch quantities are unchanged, reducing long-term resource production to one sixth of the previous one-minute cycle. Taxes and upkeep retain their five-minute billing cycle; the live Production screen still refreshes every two seconds.

Building validity checks remain on a separate one-minute schedule, plus invalidation on relevant block edits. Resource production does not harvest/reset wheat. Production stops for destroyed, invalid, unloaded or foreign structures.

Existing building deadlines are extended once from their saved one-minute deadline to the corresponding six-minute deadline. Saving/reloading does not add another delay. Partial outpost production counters persist up to 359 seconds.

Resource slots and Production display batch capacity as Max/6m, show per-source next-cycle countdowns, and retain actual deliveries in the previous 60 game seconds. Seeing zero actual deliveries between six-minute batches is normal.

Validation includes scheduling arithmetic, one-time SavedData deadline conversion, counter persistence, production-history expiry, stockpile, architect input, housing and construction-price regressions, and Forge compilation/reobfuscation. In-game timing/visual verification remains separate.
