# Kingdoms & Claims v0.4.11 — Free Building and Visual Blueprint Selection

Minecraft Forge 1.20.1 / Java 17

## What's changed
- Zoning is retired from the 3D kingdom sidebar and server rejects new zoning/edit requests. Previously saved district data remains untouched for compatibility, but no longer controls housing, jobs, or food.
- Homes completed using the Architect construction system automatically provide housing capacity; the server persists the completed-home ledger. Each new house provides at least four resident slots, and saved beds can raise capacity up to twenty per house.
- Population grows on the usual 5-minute economy cycle if treasury/upkeep are funded; no zoning is required.
- The Buildings overlay opens interactive selection in player view: two left clicks define a selection, aim to resize, Shift+mousewheel adjusts the far corner vertically. The selection shows a colored 3D frame and individual exposed-block outlines, then opens a Save Blueprint dialog for name and confirmation.
- R restarts the selection; Esc cancels. Red outline indicates >20 blocks on any side or >4096 selected positions. Selection rendering is capped to 700 visible non-air outlines per frame, and performs no chunk loading.
- Server independently validates dimensions, total volume, ownership, permissions, loaded chunks, blocks and contents. Existing `/architect pos1`, `/architect pos2` and `/architect save` commands remain as fallback.
- Completed homes created before v0.4.11 are not automatically counted retroactively; only new construction completions add housing ledger entries.

## Try it
1. Claim the land and build a simple house with normal Minecraft blocks.
2. Open the Kingdom Manager > Buildings > Capture a house (or `/architect select`).
3. Aim at the first corner block and left click. Move around and aim at the opposite corner; the outline updates live.
4. Shift + scroll to adjust vertical reach. If red, make the region smaller.
5. Click again, enter a name in the prompt, and select Save Blueprint.
6. Return to the Buildings overlay to place the saved house via the existing first-person hologram. After construction finishes, the house adds resident slots. See Economy for the new population capacity.

## Safe installation
Remove older versions of the same mod from the mods folder before adding the new JAR. Back up your test world. Build and source checks are not a substitute for in-game testing.
