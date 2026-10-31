# v0.4.5 – Kingdom Planner camera stabilization

Changes relative to v0.4.4:
- Nonliving client-only marker camera instead of armor stand. Avoids divergent living-entity head yaw (preventing Q/E rotation).
- RenderTick START camera movement before the 3D world is rendered. Prevents the previous frame-lag mismatch.
- WASD movement derived from camera actual horizontal view vector (not a separate assumed angle).
- Clamp large right-drag deltas; wrap yaw to ±180°.
- Keep v0.4.4 hand suppression, player-restoration and loaded-terrain limits.
- Debug camera yaw in HUD to help diagnose remaining gameplay-only issues.

**Test:** open Planner, keep hands off keyboard for 10 seconds; press W/A/S/D separately; Q then E; hold right drag; return to War Map and exit. Please report whether camera yaw text changes without rotation input.
