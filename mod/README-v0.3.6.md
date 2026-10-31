# Kingdoms & Claims v0.3.6 — Expeditions & Kingdom Operations

Minecraft Forge 1.20.1 (47.4.28), Java 17. Replaces v0.3.5. BACK UP YOUR WORLD.

## Features

- Discoverable wilderness map icons for mines, forests, farms, quarries and ruins; candidates only appear in explored terrain near settlements and at least 2 chunks outside claimed land.
- Right-hand **OPERATIONS** panel on the existing War Table, retaining army controls in the **ARMIES** tab. Click a map site to inspect it. Before scouting you see its type and mission state; send scouts to reveal exact capacity and operation duration.
- Numerical troops physically march as saved coordinates. `Scout` requires 5 troops, `Extract` 10, `Outpost` 10, `Take Over` 5. They are locked out of regular army orders for the duration of the operation.
- Loot expeditions (ruins) and renewable-until-exhausted resource operations accumulate progress and stockpiled items **at the wilderness site**. An attacking team can contest the mission (30-second simplified numerical battle), preserve its existing progress and take over its stockpile. The combat outcome compares troop strength (outposts get a defensive bonus); casualties permanently reduce numerical troops. This is not yet the full tactical combat engine.
- Surviving troops escort completed loads home. Only delivered goods enter the faction warehouse. Click **Cargo** to cycle goods and **Collect 16/64** to receive the stored physical item stacks. Withdrawing checks inventory space first.
- Wilderness outposts commit a permanent garrison, warn their owning faction about rival armies within approximately 128 blocks, and can themselves be contested. Outposts influence surrounding wilderness and block nearby rival outposts; they do **not** create building-protected claims.
- Supply caravans: select an army, select destination chunks on the map (must be your claimed territory or affiliated settlement), cycle cargo type, and send 16/32 items from the faction warehouse. The accompanying army marches across the map. Rivals with a nearby army or outpost can spot the shipment and order an interception, risking the cargo. Transport returns home after delivery.
- Server-owned saved operations data, troop commitment validation, explored-map discovery, permission checks and treasury-safe resource inventories. No forced chunk loading.

## How to play

1. Install only v0.3.6 with a backup of your existing world. Your kingdom, towns, armies and terrain map are migrated by reuse of their existing save files.
2. Explore wilderness near a town or kingdom; map icons will gradually populate around claimed territory. Clicking a mine-like icon switches to the **OPERATIONS** tab.
3. Create a 10+ soldier army at the War Table, click **Scout** or **Extract** on the selected site. Armies move numerically at 1.5 blocks/second.
4. While occupied, a rival faction can select the same site and click **Take Over**, sending its free army. The site pauses during conflict. The victor keeps the stockpile and progress.
5. Once an operation completes, its load is escorted home to the warehouse. Cycle cargo and collect stacks with the Operations panel.
6. To build an outpost, use an idle wilderness site and click **Outpost**; ten or more troops remain committed there until withdrawn, or until a rival takes over.
7. To send a caravan, select an available army at headquarters; click **empty map space on your own/affiliated claimed destination chunk** while in Operations mode; cycle cargo and click **Caravan 16/32**. Cargo must exist in your faction warehouse first. Rival armies can raid visible caravan markers.

## Useful first tests

- Start with 50 recruits and one empty roster army; assign 20 and scout a mine. Confirm nothing is spawned until you use manual physical deployment.
- Send a 10-troop expedition; verify its controls are locked, the icon progress increases on refresh and the goods appear only after the army returns.
- In a test world with two kingdoms, send an enemy army to take over the operation and verify accumulated stock remains intact; casualties decrease army manpower.
- After a delivery, collect 16 items and check the warehouse count drops by 16; repeat with a full inventory to ensure nothing is created or lost.
- Build and raid an outpost; test the 8-outpost cap and nearby enemy-activity warning.
- Send a 16-item shipment between affiliated factions; intercept it with an enemy army near the route and check the cargo is credited only to the survivor.
- Save and reload all site progress, caravans, armies and warehouse totals.

## Known boundaries

- Mission battles use **server-side numerical resolution**, not Minecraft entity-vs-entity siege battles yet.
- The map uses native Minecraft terrain samples, not Xaero's copyrighted map renderer.
- No animated outpost construction blocks or moving caravan entities; both are deliberate lightweight War Table operations.
- GUI state, mission timing, dimension restrictions, multiplayer sync, contested capture and saving still require gameplay testing. Passing compilation does not prove gameplay correctness.
