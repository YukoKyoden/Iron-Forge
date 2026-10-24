# Iron Forge v0.9.0 — Physical Recruits Preview

**Minecraft Java 1.20.1 · Forge 47.x.** Separate experimental reboot branch. Independent Iron Forge implementation inspired by the concept of Villager Recruits, with original code and vanilla assets. This is not a distribution or modification of the original Recruits mod.

## New army playtest — physical villagers into soldiers
1. Use an experimental/backed-up Forge 1.20.1 world with **only one Iron Forge JAR**.
2. Create a kingdom in the War Table (existing `/war` commands remain reported unreliable).
3. Find a **normal adult Minecraft villager**. Get 16 emeralds into your hand. In Survival, right-click the villager with 16 emeralds to hire one soldier. In Creative, holding an emerald is still required but the emerald cost is waived.
4. The villager should be replaced with an **Iron Recruit** entity, wearing villager-like vanilla visuals, with a visible label. No chunk claim is necessary.
5. Walk away. It should **follow** you while navigating loaded terrain. This is AI navigation, not teleportation.
6. **Empty-handed right-click:** toggle between Follow and Hold. **Sneak + empty-hand right-click:** Guard at the soldier's current spot. Empty-handed right-click again to resume Follow.
7. **Right-click while holding a sword** (stone, iron or diamond) to equip. The sword is transferred from your hand into the entity's equipment server-side; in the MVP the held sword might not render visually on the villager model.
8. Spawn a zombie or other hostile creature close enough to the recruit. It should fight and take real damage, and be able to die. Avoid testing during a natural village raid on your main world.
9. Save, exit, reopen, and inspect the recruit: owner/kingdom, location, stance and sword should persist.
10. A player outside your kingdom should be denied command access and friendly kingdom members should not be valid combat targets.
11. Recruit a second soldier; check it does not attack the first. Try a recruit from an enemy kingdom *during a declared war* and check hostility.
12. Recruitment caps for this alpha: max 16 recruits **in the 96-block hiring area** per kingdom. This is not yet a global kingdom roster cap; expand it using a global saved soldier ledger in a later version.

### Known limitations
- This preview focuses on one physical **swordsman** class; archers, cavalry, squad GUI, group formation, larger battles and war table integration are not yet complete.
- This is a fresh independent recruit entity and **does not depend on the copyrighted/all-rights-reserved Villager Recruits implementation**.
- There is no user-facing Barracks build or warehouse recruitment cost yet; cost is 16 emeralds held by the hiring player.
- AI is basic follow/hold/guard + melee on nearby monsters; there is no inventory GUI, smart eating, shields or advanced patrol controls yet.
- Recruitment deletes the original villager after the recruit is spawned. Test in a backed-up world, particularly if you care about that villager's trades. Villager appearance may differ because it is a new custom entity.
- Nearby defender entities are capped only at hiring; this first preview is not a production-ready performance guarantee. Only loaded soldiers tick; chunks are not forced loaded.
- Legacy strategic armies and `/war` commands are inherited from the prototype and not yet merged with the new physically spawned squads.
- **Do not install the original Villager Recruits mod to test this**: the current preview is standalone. Other mods may interfere with villager interactions.
- Use Minecraft Creative test commands for spawning monsters, or test on a controlled Survival world.

---
# Mining system (retained from v0.8.0)
Existing mining construction functionality remains available in this recruit preview.

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
