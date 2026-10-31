# Kingdoms & Claims 0.4.26.3

Removes warehouse markers and the floating marker-label list from the Shift+K planner. Existing storage blocks and balances are unaffected.

Open **Production** from the planner sidebar or a Warehouse Core, or **Production breakdown** under Manager > Stockpile.

Each resource has a visible row with:
- Last 60s: actual goods delivered into the shared kingdom stockpile over sixty server/game seconds. Inventory deposits, withdrawals and spending are excluded.
- Max/min: potential output from currently loaded/valid producers. Farms still require ripe wheat; this is an upper bound, not a guaranteed harvest.
- Upkeep: consumption per five-minute payment, including gold nugget payroll.

Click a resource to see its producers, next processing time, and paused reasons: unloaded chunks, invalid structures, validation pending, lost claims, full storage, or inactive outpost garrisons/upkeep. Taxes are shown separately as gold nugget income every five minutes. The view refreshes every two seconds and uses cached surveys/indexed locations; it does not scan blocks or load chunks.

Accepted farm, mine, lumber, outpost and tax deliveries are tracked. History starts with this update and persists through saving/reloading. The rolling window uses game time. A large production queue may delay a scheduled cycle; this does not fabricate deliveries or count unloaded catch-up output.

Existing worlds retain their data format. Build validation includes production-window expiry/overflow/resource separation, actual NBT production-history round trips, input, pricing, stockpile and housing regressions, plus Forge compilation/reobfuscation. In-game visual testing remains separate.
