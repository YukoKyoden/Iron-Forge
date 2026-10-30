Kingdoms & Claims v0.4.2 — 3D Kingdom Overview + Player-Built Block Tracking
Minecraft Java 1.20.1 · Forge 47.4.28 · Java 17

Install one JAR into the mods folder. Back up your world before testing. Remove older Kingdoms & Claims JARs; do not install both.

TEST THE OVERVIEW:
1. Place or use an existing Kingdom Manager in your own claimed town or kingdom.
2. Right-click Manager -> click '3D Overview' near the top right.
3. View the actual loaded 3D Minecraft world from a bounded elevated camera.
4. WASD pan, Q/E rotate, mouse wheel adjust height, right-drag change yaw/tilt, Shift to pan faster.
5. 'Back to Manager' or Esc to leave; your player body never teleports.
6. Limited to ~55 horizontal blocks from the Manager; no forced server chunk loads or remote scouting.

TEST PLAYER-MADE BLOCK TRACKING:
1. /builtblocks shows tracked player-placed blocks in current chunk, 3x3 nearby, and entire dimension.
2. Place several common blocks -> /builtblocks count should rise.
3. Look directly at a new block (within 5 blocks) and run /builtblocks look -> should report player-placed.
4. Mine that block -> /builtblocks count should fall.
5. Restart the world -> records should remain.

IMPORTANT LIMITATIONS:
- The recorder STARTS with v0.4.2; old structures and blocks placed with /fill or WorldEdit aren't retroactively recognized.
- Tracks player-placed positions (not who placed them, and not type/material); no civilian population effect.
- Not an anti-cheat/proof of completed house: stone spam still counts as player-placed.
- Pistons, structure edits, and some modded or non-player changes can make historical records stale.
- Capped at 300,000 tracked positions per dimension and 4,096 per chunk.
- The 3D camera shows loaded terrain only and does not provide server-side aerial chunk loading; this is an early visual prototype without 3D polygon zoning overlays or direct building selection yet.
- v0.4.2 is based on previously working v0.4.0. The experimental v0.4.1 housing survey is deliberately not included; housing registration will be redesigned separately.
- A successful Forge compilation does not replace testing the mod in Minecraft.
