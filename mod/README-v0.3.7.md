# Kingdoms & Claims v0.3.7 — Treasury, Upkeep, and Map Safety

Forge 1.20.1 / Java 17. Install only one Kingdoms Claims version at a time, and back up your world first. This is a source/build-tested release; verify gameplay separately.

## Changed

- Selecting armies now shows a bright white outline and a much more visible double-arrow. The selected ARMY/OPERATIONS tab has a white outline.
- Button click events are consumed before map hit-testing. Manual bare-map marches require enabling `MOVE: OFF` near the map toolbar; it changes to `MOVE: ARMED`. One click orders one march, then automatically disarms. Resource icons remain clickable without arming. This prevents accidental moves when navigating controls.
- Kingdom troop reserve plus roster cap increased from 2,000 to **10,000**. Independent towns remain capped at 500, and physical deployment limits are unchanged.
- Returning mission loot of **iron** credits the existing kingdom iron treasury (also used for annexation). **Wheat** credits a new wheat treasury. Towns each now have independent iron and wheat treasuries. Remaining resource types use their warehouse inventories. Legacy v0.3.6 warehouse iron/wheat balances migrate automatically once, without duplication.
- 5-minute operating upkeep for **each** kingdom and town. `iron = ceil((reserve + assigned troops) / 100)` and `wheat = ceil(troops / 25) + ceil((members + ceil(claimed chunks / 8)) / 5)`. There is currently no civilian NPC system, so actual members and claimed land act as a settlement-size proxy. Upkeep is atomic; if both resources are not in treasury, neither is charged and the faction enters an **unfunded** state until a subsequent successful five-minute upkeep payment.
- Unfunded factions cannot start scout/extraction/outpost operations; existing extraction and outpost construction pause, and outposts lose their combat defense bonus. Caravans en route can still travel and deliver resources. Military movement and troop numbers are not erased for a missed payment.
- Operations mode shows estimated five-minute iron/wheat upkeep and next payment timer in the footer, with red text if unpaid. Deposit 16 or 64 of the selected good from your inventory at your War Table; iron and wheat are placed in treasury automatically. The `Cargo` balance is the spendable treasury balance for these two goods.
- Existing `/kingdom treasury balance` now includes wheat and upkeep rates. `/kingdom treasury depositwheat <1..64>` accepts extra wheat. Kingdom iron deposits work as before.

## Quick test

1. Open War Table. Click ARMY/OPERATIONS buttons and verify no accidental march; white active outlines remain visible.
2. Select an army, then deliberately enable MOVE and click bare terrain. Confirm exactly one move order and automatic disarm.
3. Use Operations > Cargo: IRON/WHEAT and Deposit 16 or 64. Verify treasury display changes and `/kingdom treasury balance` matches.
4. Complete a mine/farm and return cargo. Verify iron/wheat deposit in treasury rather than separate storage.
5. Wait five server-active minutes. Refresh War Table and verify iron/wheat charge and next countdown.
6. Starve a treasury to test unfunded state; confirm extraction pauses, refill both goods, then verify the next payment unlocks it.
7. Recruit above the old kingdom cap, up to 10,000 total. Town cap is still 500.
8. Reload the saved world and verify all troop, treasury, and operation state persists.
