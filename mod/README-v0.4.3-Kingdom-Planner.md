# Kingdoms & Claims v0.4.3 — Kingdom Planner prototype

- Right-click an authorized **Kingdom Manager** in faction land to open the 3D Kingdom Planner by default.
- The camera views **the real, currently loaded Minecraft world**, limited to 55 blocks from the Planner; your character stays in-world.
- WASD pans, Q/E rotates, wheel adjusts height, right-drag rotates/tilts, Shift pans faster, Esc exits.
- **Districts** opens existing 2D zoning editor, **Economy** opens existing treasury/tax editor, **War Map** opens the existing 2D strategic map with armies/operations/treasury, **Exit** restores camera.
- War Map has **City View** return button when entered from Kingdom Planner; physical War Table is still usable as before.
- Army/operation commands still verify server-side faction, proximity, and leadership; physical deployment and recall only from War Table.
- Optional /builtblocks and /builtblocks look track player-placed blocks from this version onward (no house detection, entity scanning or per-tick cost).

## Known limitations
- 3D camera only covers already-loaded chunks. It cannot inspect the entire kingdom at once, unlike a future strategic overview.
- There are no click-to-build, hologram blueprints, on-world district outlines, 3D building selection or reworked gold/town politics in this version. Those remain future milestones.
- World-camera appearance and UI need in-game multiplayer validation; CI only compiles.
- Uses v0.4.0 faction/district/economy formats. Take a world backup before changing mod versions.
