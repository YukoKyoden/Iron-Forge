# v0.4.4 Kingdom Planner camera fix

- First-person hand canceled with Forge RenderHandEvent while Kingdom Planner is active; HUD/crosshair hidden, restored on exit.
- Frame-rate camera movement and Q/E rotation, with key event tracking and raw input fallback. WASD and arrow movement; Shift speed.
- Increased nearby view radius to 128 blocks, still only within client-loaded chunks; camera stops at their boundary instead of snapping to origin.
- Original camera entity and view mode restored on exit/transition; normal player remains in the world.
- All legacy War Table, zoning, treasury, soldiers, and persisted game data preserved from v0.4.3.

**Tests**: Back up a test world and replace old mod JAR, test movement in all directions, right-drag, scroll, hand absence, War Map return, and exiting. CI success is not a Minecraft runtime gameplay test.
