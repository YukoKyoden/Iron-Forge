# Kingdoms & Claims v0.3.3 — Cartographer Tactical Map
Minecraft 1.20.1 / Forge 47.4.28 / Java 17.

BACK UP YOUR WORLD. Replace the old mod JAR; do not install two versions.

## How to use the tactical map
1. Place a War Table in owned kingdom or town territory (same as v0.3.2).
2. Right-click the War Table and click **OPEN TACTICAL MAP**.
3. Select one of your previously recruited, named armies in the left roster.
4. **Left click the terrain** to issue a march order. Troops take time to arrive: no teleportation.
5. **Scroll over the map** to zoom. **Right-drag** to pan, **Center** to return to headquarters.
6. **Refresh** to update army positions and refresh the terrain snapshot.
7. **Borders** toggles claim overlays; **Back to War Table** returns to recruitment/formations.

## Gameplay rules
- Terrain colors are sampled from explored/loaded chunks and saved in a world cartography cache.
- Dark regions are unknown terrain, not solid terrain. Exploring them gradually fills in the map.
- Existing worlds gradually get surveyed as players traverse them; older exploration isn't automatically imported.
- Only your own faction's armies are shown; opponent army intelligence/scouting comes in a later update.
- Orders are validated server-side against kingdom/town permissions and the existing 2048-block-per-order travel limit.
- Marching uses the same numerical troop ledger as v0.3.2; no added mobs or forced chunk loading.
- Movement is still direct numerical movement; actual roads, terrain hazards and battles are future work.

## First smoke tests
- Open map, see vanilla-like grass/water/stone colored terrain around the War Table.
- Select army, click a valid destination within 2048 blocks, verify order appears and ETA updates.
- Refresh later; pin moves toward destination. Reload world; order and discovered tiles persist.
- Right-drag away, explore area, return to table; previously discovered terrain remains colored.
- Try marching while fielded troops exist; server refuses movement and map displays why.

## Preserved systems
Existing kingdoms, 15 towns, annexation, tall banners, NPC soldier skins, reserves, named armies, formations, rally coordinates, and saved data continue to use their old schemas. The new cartography save is separate.
