# Iron Forge v0.7.4 — user playtest audit
Date: 2026-10-24
Branch: reboot/kingdom-warfare-core
Build tested/referred to: v0.7.4-no-claims, Forge 1.20.1
Status: BLOCKED — NOT a gameplay acceptance pass.

## Ground rules
The interactive checklist was rendered in chat state and the assistant cannot read the user's individual ticked/unticked choices from it. This audit records ONLY explicitly reported failures, source-confirmed constraints, and test dependencies. Not reported != passed.

## Confirmed user-reported failures
- Mine Supports "makes no sense." Usability and building mechanics require redesign.
- `/war` commands "don't work." Exact failing subcommand(s) and error text not yet provided; investigate command registration, root exposure, permissions, client integration, autocomplete and invocation, not only pure WarState method tests.

## Source-confirmed support implementation weaknesses
- The timber support is a full cube block (`Blocks.OAK_PLANKS` properties), not a walk-through timber frame. It is difficult to place meaningfully in a walkable supported mine tunnel.
- Placement is accepted only when horizontal neighbor air pattern is correct, one block above is air, four of eight rock samples at distance 2 and 3 in cardinal directions match an allowlist, a linked mine exists within 48 blocks, height delta <=18, and other supports >=8 blocks away. This is hidden, brittle validation and is not conveyed visually to players.
- Placing an invalid support still places a block, but does not register productive capacity. Misleading appearance is a usability defect.
- Mine Economy automated tests (28 assertions) use model `addSupport(...rockFaces,tunnelOpen)`, not actual Minecraft placement/geometry. CI passing does not establish an understandable in-game workflow.
- The old War Table `/war` branch is patched from successive alpha archives; build checks assert command *strings*, not Brigadier execution in a Minecraft client. Its behavior is not accepted without in-game proof.
- v0.7.4 removed chunk-claim restrictions only for Mine Controllers, not all older war mechanics.

## Checklist mapping
1. Startup and War Table: partially verified startup; War Table interactive founding/map/kingdom save UNKNOWN. `/war` commands reported broken.
2. Mine Controller: claim-free restriction implemented, actual mine placement/inspection UNKNOWN; dependency on founding may BLOCK testing.
3. Supports: usability FAILED BY USER REPORT; all support subtests NOT ACCEPTED and require redesign.
4. Workers/iron production: BLOCKED unless supports and kingdom work; current in-game success UNKNOWN.
5. Saving/loading: NOT YET VERIFIED in-game; do not assume working because simulated unit tests passed.
6. Army/Wartable commands: `/war` failure reported. Recruitment, orders, practice battle, diplomacy and multiplayer testing BLOCKED/UNKNOWN. War Table GUI may be separately testable; no confirmed results.
7. Stability/performance: Forge automated client/server startup checks passed. Real player experience and runtime TPS/FPS/multiple mines/multiplayer UNVERIFIED.

## Priority fixes and acceptance gates
P0: Make command workflows discoverable and working in actual Minecraft: `/war` root, subcommands/autocomplete, no silent failures, GUI response feedback. Add Forge integration tests beyond string checks, preferably live client test.
P0: Replace hidden support rockFaces and openAir pattern with player-visible, deterministic and easy-to-explain mine expansion. A proposed MVP: Mine Controller + 3x3 clear tunnel section + one interactive shaft marker per 8-block stretch. Marker should be low-obstruction/walkable and display valid/invalid reason; no obscure cardinal 2-3-block rock scans. Require a visibly connected tunnel to prevent cheap spam.
P1: Run in-game founding -> registered mine -> assigned worker -> iron payout -> save/reload acceptance test.
P1: Restore full end-to-end War Table strategic controls after P0 commands work.
P2: Retest multiplayer, economy edge cases and TPS/FPS under load.

Until P0 is addressed, DO NOT label v0.7.4 "fully playable" or ask player to retest the same obscure supports without changes.
