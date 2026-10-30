# Kingdoms & Claims v0.4.19 — Modular Warehouse

- Existing Supply Depot is now the **Warehouse Core**, with 9 inventory-equivalent slots of resource capacity (576 resource items).
- Craft **Warehouse Storage Expansions** (8 spruce planks + chest, recipe visible through recipe mods) and place them face-adjacent to the core or other expansions. Each grants 9 more slot equivalents (576 resource items); limit 24 expansions per connected warehouse.
- Right-click the core OR a connected expansion to manage local resources. Deposit or withdraw 64 at once, or import up to 64 old virtual ledger resources, without duplication. Items are accounted within the core's persisted storage, not copied into vanilla chests. Use separate warehouses for separate towns.
- Expansion blocks show four static built-in cargo levels based on stored items. The server updates visual blockstate only when inventory changes or modules are placed/removed. No animated renderers, per-tick scanning, or block entities.
- You cannot break a core with any stored resources or while it still has attached expansions. You cannot break an expansion if removing it would strand more stored resources than the remaining capacity. Cores and expansions resist explosions and piston movement.
- Existing v0.4.18 linked chests retain their vanilla inventories untouched. Chest connections are no longer used; carry items from old linked chests to the new warehouse core to migrate physical items. The legacy virtual balance remains importable through the core.
- Existing farms, mines, caravans, military upkeep, and blueprint costs still follow their prior logic; fully automated local pickup, delivery and construction consumption are future upgrades.
- Each warehouse is tied to its own core and current claim ownership. Up to 24 connected expansions are counted per core; unconnected extras are merely decorative until a path to the core exists.
- **Back up your world before upgrading.** Regression tests and compilation do not replace an in-game multiplayer test.
