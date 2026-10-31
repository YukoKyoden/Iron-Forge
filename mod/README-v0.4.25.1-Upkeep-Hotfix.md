# Kingdoms & Claims v0.4.25.1 — Immediate Upkeep Recovery

This is a bug-fix update to v0.4.25, not the future v0.4.26 caravans release. Keep only one Kingdoms & Claims JAR installed and back up your world before swapping versions.

## Immediate upkeep recovery
- Upkeep now checks an **unfunded** kingdom or town on each 1-second server economy heartbeat. Depositing sufficient iron/wheat and ensuring Crown payroll is available starts an immediate payment and resumes operations, without waiting for the next five-minute interval.
- The successful payment starts that settlement's own fresh 300-second countdown. It will not bill again immediately on the old kingdom-wide timer.
- Different settlements may have different next-charge times, and the remaining time is saved in world data. Upgrading the old shared timer preserves remaining time for funded settlements and allows unpaid settlements to retry immediately.
- Failed charges are counted once per missed cycle, rather than repeatedly penalizing population while waiting for resources.

## Better feedback and stocked warehouse selection
- The War Map **STOCK** tab displays the exact upkeep blockage (missing nearby warehouse, insufficient iron/wheat in one warehouse, or insufficient Crowns for payroll). After adding goods/gold, press Refresh to update the on-screen snapshot.
- Upkeep can now use **any one sufficiently stocked warehouse within 192 horizontal blocks of the appropriate capital/town banner**, rather than only the absolutely closest warehouse. Physical materials still cannot be pooled magically across separate warehouses or settlements.
- There must be sufficient iron, wheat, **and Crown payroll** (if troops require payroll). Deposit gold in the planner's Finances screen for Crowns.
- An unpaid settlement resumes resource outposts and missions automatically after a successful payment; a one-time chat message confirms recovery.

## Performance and compatibility
- No forced chunk loading, world block scanning, or extra entity renderers. Unpaid checks use the existing indexed, loaded Warehouse Cores and run only once per server second. Funded settlements are charged only when their countdown ends.
- Existing claims, depots, shelves, gold, warehouses, outposts, armies, and old saved-resource migrations are preserved.
- Verified with static source regression checks and Forge CI build; test in Minecraft before relying on it in a long-running world.

## Test
1. Set up your settlement banner and a Warehouse Core within 192 blocks. Put iron and wheat in that same warehouse; ensure enough Crowns for payroll.
2. Wait for one failed upkeep cycle or load a save where upkeep is marked unpaid, then replenish missing materials.
3. Within one server second, it should mark upkeep funded, deduct one payment, resume operations, and reset the timer to five minutes.
4. Remove material before the next charge, verify it becomes unpaid, and put it back to observe an immediate recovery, with only one successful charge.
5. Check War Map > STOCK > Refresh for the shortage reason and accurate countdown.
