# v0.4.12: Architect fixes and progressive construction

- Rotation key cancellation is guarded for noncancelable Forge keyboard events (a likely rotation crash cause; confirm with crash log if any crash persists).
- Rotated hologram model rendering is isolated per block, so one unsupported block model does not close Minecraft.
- First-person blueprint placement and visual selection both raycast up to 96 blocks; server allows a placement anchor up to 96 blocks away and checks all claims and obstruction data.
- Ghost previews cap block models rendered to 1500 per frame to protect performance.
- After starting construction, the paid building now appears bottom-up in batches roughly once per second with modest work particles and sounds, instead of popping into existence at the end.
- Construction progress is persisted (`Placed` per site), resumes after world/server restart, and pauses on unloaded/unclaimed/obstructed land without overwriting existing work. Existing v0.4.11 unfinished projects start at zero placed. Only completed homes count toward housing.
- Replace prior mod JARs and back up your world. Test rotation using a small test blueprint, and inspect actual Minecraft crash logs if the issue persists.
