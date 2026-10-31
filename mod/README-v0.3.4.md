# Kingdoms & Claims v0.3.4 — Unified War Table + Improved Terrain
Forge 1.20.1, 47.4.28. Replace the previous v0.3.3 JAR (do not keep two versions).
Back up your world first.

- **One War Table screen:** Map fills the LEFT side, army roster and command buttons are on the RIGHT.
- **One click to march:** Select an army in the right roster and left-click a map destination.
- Mouse wheel over the map zooms; right-drag pans; wheel over army roster scrolls armies; wheel over the orders scrolls the controls.
- Includes Create Army, Assign/Return troops, Change Formation/Uniform, Hold, Return HQ, Split, Disband, Deploy, Recall and Refresh.
- **Higher terrain detail:** Samples 16 surface colors per 16x16 Minecraft chunk instead of the earlier single solid color, with basic relief shading; uses vanilla Minecraft MapColor values.
- Existing saved coarse maps are migrated and refined as the player visits their areas again. Only explored/loaded chunks reveal terrain. No automatic Xaero integration or Xaero code/assets are bundled.
- This is a Minecraft-style standalone map and should look markedly better than v0.3.3, but it is not an identical clone of Xaero's World Map (which has an independent proprietary renderer).
- All movement, losses and troop counts continue to be server-validated using the existing military ledger.

## Smoke tests
1. Right-click War Table. The entire screen should show map LEFT and orders RIGHT, with no separate map-open button.
2. Assign an army using right panel, then click the map left. Refresh should show movement.
3. Change uniform / formation on right without leaving the map.
4. Pan, zoom, and scroll army roster and order controls separately.
5. Restart and verify old world army data and existing revealed terrain persist.
