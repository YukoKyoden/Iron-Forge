# Iron Forge v0.8.0 — construction-chest mining alpha
**Minecraft Java 1.20.1 · Forge 47.x · reboot branch.** This remains an experimental mod, **not the finished kingdom warfare reboot**.

## What changed since v0.7.4
The confusing manual Timber Support detection has been removed from mine production. Each new Mine Controller has a real 27-slot Minecraft chest inventory, a starter mine building blueprint, and a construction action. Mine production starts **only after construction completes and workers are assigned**. No manual chunk claiming is required for mines.

## Playtest: constructing a mine
1. Back up your test world. Replace, don't combine, older `kingdomsatwar-*.jar` files.
2. In Creative, get a War Table with `/give @s kingdomsatwar:war_table`. Found a kingdom **using the War Table menu**; the inherited `/war` command set has user-reported bugs and is still under investigation.
3. Travel below Y55 but not below Y-55. Find a site near natural stone/deepslate. Set the front of the Mine Controller toward the rock you want to excavate. Hold an empty hand when interacting.
4. Get `/give @s kingdomsatwar:mine_controller` and place it; you should receive a **registered** message.
5. **Right-click** the controller to open a chest-like **27-slot construction inventory**. Deposit **64 Oak Logs, 128 Cobblestone and 16 Iron Ingots**. Other wood/stone items do not count in this prototype.
6. Close the chest; **sneak-right-click with an empty hand** to start the build. The blueprint builds an approximately **3×3×8 tunnel** in front of the controller over a few seconds, adding cobblestone floor and spruce-log support frames.
7. If you see an obstruction warning, move the controller/clear the indicated position. The builder only automatically replaces **air or natural stone/deepslate/tuff/andesite/diorite/granite**. It refuses ores, containers, liquids, player-built structures, and unloaded footprint chunks. It does not take materials until the whole blueprint passes a preflight check.
8. Once built, **sneak-right-click** to assign one worker (repeat for second worker). Right-click for geological reserve/quality and iron/min. Iron is credited to your **kingdom resource balance**, not your player inventory. Maximum 2 workers for this first blueprint.
9. Exit/reload your world and verify the chest inventory, construction, workers and reserve persist. Construction progress should save if the chunk unloads.
10. Try an insufficient-material or blocked-site construction in another location. It should refuse without deducting any materials.

## Known limitations, not yet implemented
- Build interaction uses **sneak-right-click**, not a custom on-screen **Construct** button yet; the chest GUI itself is vanilla.
- Only **Level 1 iron mine** is available; further blueprints, farms, farms and resource withdrawals are future work.
- The old decorative Mine Support item still exists to avoid removing a block ID from existing worlds, but placing it has no production effect.
- The mine builder never fabricates ore items or scans huge terrain areas.
- There is no strong refund/recovery path for a mid-build obstruction introduced by another player, and no cross-mod land protection integration yet; test on backed-up worlds.
- The older War Table has legacy claim and `/war` command mechanics. They are **not** the future design; the Town Hall, role-based army simulation, Recruits integrations, capturable towns and warehouse are still pending.
- User-facing gameplay still requires verification on Windows even when GitHub's automated Forge client/server startup tests pass.

## Verification
`.github/workflows/reboot-mining.yml` builds and checks the mod JAR, runs model regressions and checks client and dedicated-server startup. This is not a substitute for in-world construction testing.
