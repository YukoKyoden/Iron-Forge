# Iron Forge — Kingdoms at War

Early alpha Minecraft Forge **1.20.1** multiplayer warfare mod with strategic army movement, simulated combat, territory claims and sieges, and nearby NPC battle proxies.

## Build online

Open **Actions → Build Kingdoms at War Forge Mod**. Commits to `main` automatically start a build, or select **Run workflow** to retry. Open the completed run to review logs. If it succeeds, download the `kingdoms-at-war-forge-1.20.1` artifact ZIP, extract the `.jar`, and place the JAR in your Minecraft Forge 1.20.1 `mods` folder (Forge build target **47.4.28**).

The source is stored in `iron-forge-source.tar.xz` and extracted during GitHub Actions. The build runs the standalone army simulation test, Forge Gradle compilation/reobfuscation, and basic JAR integrity checks.

**Status: alpha / Forge compilation and Minecraft playtesting not yet confirmed.** Use a backup test world. The downloadable GitHub Actions artifact is a ZIP containing the JAR, not a CurseForge modpack.
