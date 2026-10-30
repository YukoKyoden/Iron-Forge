# Kingdoms & Claims 0.4.26.1

Forge 1.20.1 / Forge 47.4.28 / Java 17. Install in place of 0.4.26; the existing world data format is unchanged.

## Architect input fix

The selector no longer swallows mouse-release events. Activation and cleanup explicitly clear Minecraft's attack/use bindings. Repeated activation while selecting is ignored, and a mouse press only selects one corner until that button is released. The wand has a short activation cooldown. Blueprint placement also passes mouse releases through, so leaving that tool cannot leave attack/use held.

Regression tests cover click/hold/release, repeated activation, exiting and reopening the tool. Live Minecraft verification: right-click the wand once, wait, click two distinct corners, confirm/cancel, then verify ordinary block interactions work. Holding either mouse button must not reset or advance the selection repeatedly.

## Minecraft menu theme

Every mod menu now uses inventory-style gray pixel bevels and recessed slots, with Minecraft's native button renderer and tooltips. This includes the Kingdom Manager, stockpile, building registration, blueprint save dialog, Town Hall and permissions, barracks, army control, kingdom/war maps, and 3D planner docks and blueprint folders. Resource icons remain actual Minecraft items. Text uses the inventory palette with readable red/green status colors. World terrain, territory colors, selection boxes and army markers retain their functional colors.

The economy, claims, armies and building registration rules are unchanged. The existing transaction, housing and actual NBT persistence regressions run alongside the new architect input tests. Compilation/automated checks do not substitute for in-game visual/input testing.
