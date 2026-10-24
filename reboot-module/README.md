# Iron Forge — Geological Mining Preview (0.7.0 alpha)
**Development branch**: `reboot/kingdom-warfare-core`. This preview temporarily includes the legacy v0.6 warfare features while the Recruits-style reboot is built. It is NOT the finished reboot game.

## In-game test
1. Back up your Minecraft Forge 1.20.1 world.
2. Remove the older `kingdomsatwar-*.jar` before placing the new compiled JAR in `mods`. Never install both.
3. Create a kingdom with the existing War Table or `/war found MyKingdom`.
4. Dig down into a **claimed chunk**, between Y -55 and Y 55.
5. Craft or get a **Mine Controller**: `/give @s kingdomsatwar:mine_controller`. Place it underground.
6. Dig a walkable underground tunnel with **stone/deepslate/other rock around it**. Create a 3-block-high passage.
7. Place **Timber Supports**: `/give @s kingdomsatwar:mine_support`. Supports must have 4+ natural-looking rock cells sampled at 2–3 blocks away on horizontal sides, open tunnel passage along an axis, and 8 blocks of spacing from other supports. Limit 24 supports, within 48 blocks of controller and 18 vertical.
8. Right-click the Mine Controller to see deposit richness (1–5), remaining reserves, support count, assigned workers and iron/min.
9. **Sneak-right-click** the controller to assign one more worker. Each registered support allows two workers; max 24.
10. Wait one minute of server runtime; open the original War Table and verify the kingdom's **iron resource count** increases. Resources are accounting units for the kingdom; **physical inventory withdrawal is planned for the warehouse milestone**, and is NOT in this preview.
11. Break a registered support; its bonus worker slots disappear. Break the controller to remove the registered mine.

## Limitations
- This first pass samples local rock only when a support is placed; it does not scan huge parts of the world.
- No path connectivity test between support and controller yet; the range and spacing prevent compact support spam, but elaborate tunnel shapes are not audited.
- Region-level reserve/richness uses seed+dimension+128-block region, not literal nearby vanilla ore counts. You can't increase reserves by placing ore blocks.
- One registered mine per 64-block neighborhood; max 8 per kingdom. Mine supplies are capped by warehouse iron room of 10,000.
- Mines don't operate when the server is shut down. Unloaded mines continue **data-only** production every minute, with no forced chunk loading.
- Supports are currently simple placeable blocks, without custom wooden beam visuals.
- No playable Recruits-style army reboot or watchtower/warehouse UI yet. Do not present this preview as the fully completed mod.
- Older abstract **mine** upgrades in the War Table are disabled with an explanatory message so they cannot consume resources while producing nothing.

## Build and QA
The `.github/workflows/reboot-mining.yml` GitHub Action assembles the last compatible codebase, installs this module and resources, runs 28 geological tests plus the inherited combat regression suite, builds a reobfuscated Forge JAR, checks for the new classes, and starts a headless dedicated server for a smoke test. In-game tests are still required. 
