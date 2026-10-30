# Kingdoms & Claims 0.4.26 — Forge 1.20.1 / Java 17

Use a new world. This release replaces local depot inventories with one server-owned kingdom stockpile. Attached towns use the parent kingdom balance; independent towns retain their own balance. Old economy balances are not migrated.

## Getting started

1. Create a kingdom and place its banner as before.
2. Right-click a Kingdom Manager, or use `/manager`, inside your territory.
3. The five sections are Kingdom, Stockpile, Build, Army and Map. The existing 3D planner and blueprint library remain under Build and Map.
4. Craft a Kingdom Architect's Wand with one gold ingot and two diagonal sticks, or choose Register a building under Build. Left-click two corners; Shift+wheel adjusts height. Choose a type, scan, then confirm.
5. Include the full building, its foundation, roof, beds and door. Farms must include farmland and crops in the selection.
6. Put a Warehouse Core anywhere inside your claim to deposit/withdraw actual items. Shift-click a resource to deposit a stack; right-click to withdraw. Deposit all moves all supported carried stacks. Starting shared capacity is 1024 items; cores/shelves and registered warehouses expand it.

## Registration rules

- House: sheltered beds over a solid floor with clear space above, a roof within eight blocks, and an entrance door. One bed supplies one resident slot.
- Farm: hydrated farmland with planted wheat. Each minute, ripe plants are harvested into shared wheat and reset to age zero. No simulated duplicate harvest. Full storage leaves crops unharvested.
- Warehouse: chests/barrels add 1728 capacity; standalone custom shelves add 576. Linked shelves are counted by their core, never twice. Vanilla contents stay vanilla contents; use a core to move stockpile items.
- Mine: at least 24 natural stone/ore blocks and 12 excavated air blocks below sea level. Each minute supplies iron, coal and stone, scaled to stone count (capped).
- Lumber camp: at least eight tree logs and eight leaves. Wood output scales to logs (capped).
- Barracks: sheltered beds and a crafting/smithing/fletching table. Each bed supports 50 stored troops, within the existing troop limit. Recruitment costs 3 iron and 18 wheat per troop.

Selections cannot overlap, cross foreign/wilderness claims or exceed 32768 blocks/64 blocks per side. Cached surveys are invalidated on player edits/explosions. Surveys are distributed at most one per server second; foreign regions do not contribute capacity or production. Unloaded buildings keep their persisted capacity, while production pauses until their chunks are loaded. Production never catches up missed unloaded time. Remove obsolete registrations through Build.

## Economy and construction

Resources, recruitment and construction use the shared ledger. Upkeep runs once per kingdom every five minutes, includes attached town troops and consumes iron, wheat and gold atomically. An unpaid cycle retries each second, then starts a fresh five-minute interval. Gold is physical-value backed: one ingot equals nine nuggets, with change retained. Tax income enters the same stockpile.

Blueprints retain folders, holograms, rotations, repeated placement and progressive construction. Basic materials come from shared stock; specialty blocks use exact carried items. Metal/coal/emerald/hay blocks cost nine material units. Completion attempts the same building registration as manual structures; nonfunctional/decorative designs remain ordinary builds.

The existing army map, commands, sieges, watchtowers, towns and permissions remain available. Ordinary wilderness mining and harvesting are unchanged.

## Validation

GitHub Actions runs transaction arithmetic regressions, housing geometry tests, resource validation, and Forge compilation/reobfuscation. Compilation is not an in-game test. Before using a long-term world, check claims, stockpile save/reload, transfers, farmland harvest, building destruction, town stock sharing, overdue upkeep recovery, planner controls, construction, recruitment and siege behavior in Minecraft.
