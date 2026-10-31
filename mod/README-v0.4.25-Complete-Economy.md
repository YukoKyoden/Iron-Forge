# Kingdoms & Claims v0.4.25 — Complete Physical Economy
Forge 1.20.1; upgrade from v0.4.24. Back up existing world before installing. Do not run both jars simultaneously.

## Production
Staffed, friendly War Map resource OUTPOSTS now generate materials every 60 server seconds: Mines +4 iron, farms +8 wheat, forestry +6 logs, quarries +6 stone, ruins +2 emeralds. A garrison must remain at the site and the faction must have funded upkeep. Generated goods unload to a loaded same-faction modular warehouse within 192 horizontal blocks; warehouses in other towns cannot receive it. Unloaded or missing destinations do not trigger chunk loads. Excess stays at the operation site (up to 256 units) until storage becomes available; production pauses when backlog is full. One-off resource expeditions continue to deliver their cargo physically.

## Local spending
Barracks recruitment spends iron and wheat (3 iron and 18 wheat per recruit) from a physical warehouse within 96 blocks; the 18 wheat replaces the old six-bread cost and is not a free conversion. Upkeep every five minutes needs iron and wheat at the warehouse within 192 blocks of the settlement's banner. Both goods are checked before any deduction; Crown payroll is checked first. Construction continues using nearby warehouse materials plus carried specialty items. The War Map's resource collection, deposit and caravan dispatch now withdraw from a warehouse near the War Table rather than a virtual ledger; town annexation pays from the capital warehouse. Claim purchases still use carried vanilla blocks.

## Legacy save migration
Historic OperationsData stockpiles and KingdomData iron/wheat balances are still read for one-way, capacity-bounded migration. Whenever players open or use a warehouse, or every five-minute upkeep cycle, as much old resource inventory as can fit is moved into the physical depot and removed from the old ledger in the same server-thread operation. If there isn't enough room, the remainder stays in the saved NBT for later migration. Existing kingdom/town IDs, permissions, blueprints, claims, warehouses, and military rosters are not reset. Crowns remain the separate non-material currency.

## Monitoring and performance
The Warehouse Core GUI shows physical storage fullness, current material count, carried material, staffed outpost production rate by type, and warning when storage is full. The War Map shows the stock local to its War Table and the next upkeep charge. Production advances only on the existing one-second server heartbeat and grants on minute intervals; no entity renderers, new NPCs, chunk force-loads, or per-tick warehouse scans were added. Shelf models and 3D map markers are unchanged from v0.4.24.

## Suggested gameplay test
1. Back up the world; install only v0.4.25.
2. Open an old core and confirm old stored goods are present or partially migrated (add shelves if necessary).
3. Staff a Mine/Farm/Forest OUTPOST within 192 blocks of a warehouse. After 60+ seconds, reopen the warehouse to confirm output.
4. Fill warehouse completely and confirm new site production waits rather than discarding supplies.
5. Recruit at barracks within 96 blocks, checking that iron/wheat counts fall by 3/18 per unit.
6. Start a blueprint, confirm physical materials are consumed; test insufficient stock.
7. Test 5-minute upkeep with ample food/iron and Crowns, then insufficient resources.
8. Dispatch a 16-item War Map caravan, confirming that the source warehouse loses 16 units and receiving warehouse receives them.
9. Open the 3D Claims tab to confirm the v0.4.24 null-crash regression remains fixed.

Automated source tests and CI compilation cannot substitute for live Minecraft tests.
