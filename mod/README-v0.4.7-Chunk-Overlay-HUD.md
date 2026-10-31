# Kingdoms & Claims v0.4.7 — Full-Chunk Zoning Overlay & City HUD

Based on the user-tested v0.4.6 Forge 1.20.1 City Planner.

## Improvements
- Translucent terrain-following fill on the ENTIRE 16x16 chunk for hovered and selected tiles.
- Four color-coded district paints: Residential (blue), Commercial (gold), Industrial (salmon), Agricultural (green).
- Existing zoning gets a faint full-chunk color overlay; hover/selected areas are much brighter.
- Unclaimed chunks preview in red and cannot be zoned; no server claim rules changed.
- Strong terrain-following chunk perimeter selection border, including sloped ground.
- Four direct district type buttons, named districts, existing-district targeting, Create/Expand, Clear.
- New original dark medieval strategy HUD: bottom-center build category tray, left resources/settlement dashboard, top kingdom name, camera guide, contextual chunk info. No copyrighted textures/assets from Kingdoms and Castles.
- Preserve existing camera controls, chunk limits, server validation, districts/economy/war map screens.

## Build & test
A built JAR is not included; Minecraft Forge/Gradle dependencies are not installed in this execution environment.
Use a Java 17 JDK and ForgeGradle 6-compatible Gradle, then `gradle build` from project root.
Backup your world; use matching client/server versions. In game: open Manager → Zone Land, hover an owned chunk, drag multiple chunks, select a type, Create; repeat with Existing District Expand. Test hills, rooftops, unclaimed land, and a modest GUI-scale window.
