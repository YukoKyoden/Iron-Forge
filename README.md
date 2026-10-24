# Iron Forge — Kingdoms at War
Minecraft **Forge 1.20.1** kingdom warfare mod (early alpha).
## Development build 0.3.0
The GitHub Actions workflow extracts the original game source and applies `iron-forge-v030.patch.xz` before compiling the downloadable mod JAR.
Adds a placeable **War Table**, an interactive world map sampled from already-loaded Minecraft chunks, army and capital markers, kingdom founding through the menu, recruiting, territory claiming, invite-only membership, kick/leave, and army hold orders. Distant army simulation and simple siege/casualties remain.
## How to install and test
Open Actions > latest **Build Iron Forge Mod** run > Artifacts > download `iron-forge-v0.3.0-forge-1.20.1`.
Unzip, remove older Kingdoms At War JARs, and put `kingdomsatwar-0.3.0-alpha.jar` into your Minecraft Forge 1.20.1 profile's mods folder. Back up a test world before upgrading.
Open via **M**, or use `/give @s kingdomsatwar:war_table` with cheats, place the table and right-click. The terrain preview only shows chunks that were already loaded; unvisited regions are intentionally dark. This is not yet a persisted global cartography system.
This is an **early alpha**, not a finished complete game. Minecraft playtesting and further systems are still required.
