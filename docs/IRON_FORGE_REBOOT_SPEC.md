# IRON FORGE — Kingdom Warfare Reboot
**Target:** Minecraft Java 1.20.1 / Forge 47.x. **Branch:** `reboot/kingdom-warfare-core`.
**Status:** DESIGN BASELINE. This is not an installable release.

## Core promise
A Minecraft-native, Recruits-inspired kingdom defense and warfare game. Players build a settlement, store real resources, hire **physical** troops, man towers, receive actionable alerts, and call reinforcements during cinematic, low-entity-count fights. The old chat-command strategy prototype is not the foundation of the new user experience.

We are **inspired** by Villager Recruits; we will not copy its proprietary code or assets. Initially build independent Forge entities rather than require or modify the third-party mod. Compatibility can be evaluated separately.

## One-minute gameplay loop
1. Place **Kingdom Banner** to found and name a kingdom.
2. Place one **Kingdom Warehouse** to store shared building, food, and recruitment resources.
3. Use a **Barracks** to recruit 3 combat troop types: Swordsman, Archer, and Mounted Rider.
4. Assign a **Watchman** to a **Watchtower**. A hostile player or enemy army in detection range sends an alert to kingdom members and emits a redstone pulse.
5. Use the **War Horn** (hotkey HUD) to choose an available squad, point at a rally location in the world/map, and request reinforcements.
6. Fight alongside recruited soldiers as they defend gates, contest objectives, and respond to squad-level orders.
7. Recover supplies, restock surviving troops, replace losses, improve defenses.

## Troops
| Type | Niche | Defaults |
|---|---|---|
| Swordsman | Shield-and-sword frontline | hold, follow, guard, charge |
| Archer | Ranged support | hold, focus-fire, follow |
| Cavalry | Fast flank and reserve | patrol, flank, rush, withdraw |
| Watchman | Tower guard & threat sensor | assigned to a tower; not freely recruitable as an attack squad |

All units have owner kingdom, group ID, home/rally position, morale/health, gear tier, target, and simple states (idle, forming, traveling, defending, attacking, retreating). Watchmen share a unit system but are always tower-assigned.

**Explicit requirement:** Units must be genuine in-world entities for close-up combat (not stationary invisible simulations with decorative zombies). Server owns damage, death, orders, permissions, inventory costs and targeting.

## Watchtower / alarm logic
- Place tower controller inside a player-built tower. Assign at least one watchman, or the alarm is inactive.
- Detection defaults: 96-block radius around tower; server-configurable with 32–192 suggested range.
- Triggers: non-member players (if not whitelisted/allied), hostile kingdom recruits or squads. Config flags distinguish "unknown nearby" from "confirmed hostile".
- Optional LOS restriction and player stealth counterplay later.
- On threshold entry, server produces a **one-time alert** with approximate direction/location and timestamp, plus a **2-second vanilla redstone pulse** at the controller. No per-tick spam.
- Cooldown default 30 seconds per tower × threat, with tracked state so a continuously present enemy does not retrigger on cooldown alone. Alert again after leaving and re-entering, or new threat.
- HUD map marker displays tower + sector + threat class. Notify online authorized kingdom members; no hostile-player tracking for strangers.
- Never force-load map chunks solely for tower detection. Towers only scan when their chunk is ticking; at-distance strategic army threats can be forwarded by saved-data approach once implemented.
- Tower can be upgraded with resources; not required for the first playable loop.

## Kingdom Warehouse
- **One warehouse controller per kingdom**, placeable block, locked to kingdom role permissions. Additional access terminals can connect to its shared contents later.
- Massive capacity without thousands of Minecraft chest blocks/ticking block entities.
- Design: a 27/54-slot **physical intake/output buffer**, plus one server-side **virtual ledger** aggregating approved fungible resources by registry ID and count, with configurable per-resource caps.
- Start with food, logs/planks, cobblestone/stone, iron ingots, arrows, leather, and emeralds. Non-stackable equipment remains physical inventory; never silently merge different NBT gear.
- Hoppers can insert/extract only through the limited buffer with throttled transactions and correct Forge item handler capability.
- Atomic, persistent deposits and withdraws; UI shows counts + access rights. Recruitment costs consume ledger stock exactly once with server validation.
- No duplication via concurrent GUI access, hopper transfers, chunk unload or reboot; tests cover all four.

## For Honor-style battle feel
- A few **high-quality actual fighters** at the center of each encounter; players matter tactically.
- Contested **gate / banner / lane** objectives and short waves of reinforcements create the feeling of an enormous clash without spawning hundreds of entities.
- Avoid infinite free troops: every physically spawned recruit draws from an actual squad roster and warehouse supply. Deaths reduce roster; revival only by explicitly recruiting.
- Example **test target**: 8–16 active recruits per side on one battlefield; 32–48 global physical troop budget as a tunable starting cap. These are NOT benchmark guarantees.
- Nearby squads share routes, formation slots, target selection and limited path recomputation. Reduce N+1 AI target searches.
- Distant army groups are data-only and update infrequently; transition into physical troops without duplicates or resurrected losses.

## Reinforcement HUD
- `R` configurable bind opens a small screen/HUD over gameplay: available squads (sword/bow/cavalry), troop count, distance, ETA estimate as simulation result (not invented), mission status, recent watchtower alerts.
- Choose squad -> mouse-target ground position -> issue **Rally**, **Defend**, **Attack**, or **Retreat**. Reinforcements travel from real barracks/rally point and cannot teleport unless in developer testing mode.
- Small rotating map/HUD uses Minecraft terrain samples only from explored/cached chunks where permitted; no all-world forced loads.
- Server validates order issuer rank, ownership, availability, same dimension, cooldown, walkable target, and supplies. Acknowledgments/errors are visible.
- Friendly players see only their authorized kingdom armies/towers; enemy intel limited by actual watchtower sighting.

## Kingdom permissions and safety
- Roles: Founder, Officer, Captain, Member; permissions separately for recruiting, storage withdrawal, tower assignment, war orders, and administration.
- Allies never trigger as invaders. Member sneaking/creative and other edge cases handled deliberately.
- Entity ownership and world save are server-side; clients never send raw troop IDs without validation.
- Dedicated-server compatible: common code must not load client-only screens/classes.

## UI
- Gameplay uses blocks and hotkeys, not chat commands.
- Banner: create/inspect kingdom.
- Warehouse: list resources, deposit/withdraw, upkeep.
- Barracks: recruit + squad composition and orders.
- Tower: watchman assignment, redstone mode, alert radius.
- HUD: wave reinforcements, alert indicators, tactical placement.
- Admin commands ONLY for diagnostics; not required for ordinary gameplay.

## Implementation milestones and acceptance gates
**M1: Playable defense vertical slice.** Banner + warehouse + barracks + three unit types + basic combat + watchtower + alert/redstone + HUD rally + save persistence. Must compile, boot server, pass unit tests, work in one real client world. No claim of full game before this.
**M2: Tactical war loop.** Squad formations, patrol/hold/retreat, ranged attacks, cavalry mounts, enemy squads, battle objectives, casualty roster persistence.
**M3: Kingdom logistics + AI enemy.** Supply/upkeep, troop recruitment limits, kingdom roles, automatic raid AI, defensible settlements.
**M4: Siege + multiplayer.** Castle breach/capture mechanics, arrows/gates/objectives, permissions, multiple players ordering squads, hostile intelligence/whitelists.
**M5: Performance, polish and release.** Profiling with Spark, sync budgets, chunk/unload tests, save migration strategy, balanced economics, usable textures/audio, client/server soak tests, public gameplay checklist.

Each release requires compilation, automated simulation and protocol tests, Forge dedicated-server smoke test, manual one-player and two-player tests. Benchmark target: 20 TPS on a stated reference server with a representative encounter, not a universal guarantee.

## Explicit non-goals for M1
No gigantic full-world real-time terrain renderer, no bespoke 3D unit models, no unlimited troop spawning, no simulated battles presented as if physically fought, no UI-only facades, no reliance on operator permissions for core gameplay.

## Migration
Old v0.x worlds must be backed up. Reboot changes data models; **do not promise backward compatibility** unless a migration has been implemented and tested. Use a distinct build/release channel until the core passes its acceptance gates.
