# Iron Forge v0.8.0 — First in-game construction playtest
Date: 2026-10-24
Branch: reboot/kingdom-warfare-core

## Player report
The user reported **"ok that all worked"** in response to the v0.8.0 construction-chest test instructions. This is a positive in-game smoke test of the construction-chest experience as described: registered Mine Controller, 27-slot construction inventory, deposit required vanilla resources, sneak-right-click to construct a 3x3x8 tunnel, and worker assignment.

This is a **user-reported pass**, not independently observed telemetry. Don't overclaim that unmentioned exploit, persistence, multiplayer or performance tests passed.

## Current acceptance
- Chest-driven construction and automatic physical mine blueprint: **reported working in-game**.
- Worker assignment and user-facing controls: **reported working**.
- Forge client/server startup and prior automated economy tests: **passed CI**.
- Claim-free mining prerequisite: shipped in v0.7.4 and retained in v0.8.0.

## Still needs validation
- Restart during construction, item duplication/refund policy, obstruction introduced mid-build, concurrent/chunk-boundary cases.
- Exact resource balance after repeated production payout; reserve exhaustion/warehouse cap.
- GUI button, preview/rotation and tier upgrade structure (not yet implemented).
- Legacy /war commands remain reported broken in v0.7.4; nothing here establishes a fix.
- Real warehouse withdrawal, automatic town borders and Recruits-style warfare are not implemented.

## Next development milestone (suggested)
Build Level 2 automatic mine expansion funded through the same persistent construction chest, an inventory GUI with clear construction/upgrade state, and a visible footprint preview, reusing the blueprint infrastructure without bringing back manual timber support placement.
