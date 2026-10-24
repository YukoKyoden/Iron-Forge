# IRON FORGE — Kingdom Warfare Reboot
**Target:** Minecraft Java 1.20.1 / Forge 47.x. **Branch:** `reboot/kingdom-warfare-core`.
**Status:** DESIGN BASELINE. This is not an installable release.

## Core promise
A Minecraft-native, Recruits-inspired kingdom defense and warfare game. Players build a settlement, store real resources, hire **physical** troops, man towers, receive actionable alerts, and call reinforcements during cinematic, low-entity-count fights. The old chat-command strategy prototype is not the foundation of the new user experience.

We are **inspired** by Villager Recruits; we will not copy its proprietary code or assets. Initially build independent Forge entities rather than require or modify the third-party mod. Compatibility can be evaluated separately.

## Mine construction chest and automatic blueprints (new design decision)
**Replace the broken v0.7.4 timber-support placement rules.** This section describes the desired next implementation, NOT features available in the current alpha.

### Player loop
1. Locate a promising rocky site with a prospector / controller survey; the location determines a persistent, finite geological deposit based on world seed and dimension. Placed ore cannot change it.
2. Place a **Mine Controller**. Right-click to open a **construction chest GUI** with material storage, a simple blueprint preview, deposit rating, and **Build** button. This is the primary interaction; do not require manually placing or validating invisible support blocks.
3. Choose and rotate a supported template (first MVP template: compact starter iron mine, e.g. 9x9 entrance building with safe bounded underground tunnel). Show a **ghost outline and footprint before starting** so the player can reposition it and know exactly what will be built.
4. Deposit actual vanilla materials into the controller (initial placeholder: 64 logs, 128 stone, 16 iron ingots); show `64/64` etc. Surplus remains recoverable. Use a persistent, server-owned inventory with hopper/capability rules rather than fake chest UI; support normal item transfer.
5. **Build only when funded and site preflight succeeds.** Preflight must check complete blueprint footprint for solid/protected structures and block entities, enforce terrain/ground rules and land ownership, and use low bounded per-tick changes to avoid freezes. No auto-force-loaded chunks, no accidental overwriting of player blocks, chests, or other structures. For excavation, limit to natural stone/deepslate and never silently destroy containers, ore, or protected blocks. If unsafe, refuse with clear obstruction position or require player excavation.
6. Atomically reserve/deduct required materials once. Save construction job with blueprint, orientation, funded status and next block cursor. Construct visible frames, supports, equipment and building over a short simulated period. Resume safely after reload, no double-charge; refund remaining materials when canceling a job if that is supported.
7. Completing a mine automatically activates its tier-1 production. The **generated timber supports are visual construction elements**, not manually placeable production exploits. Assign workers in the same menu; production bounded by level, deposit quality, staff, upkeep and remaining reserve, not the number of support blocks someone places.
8. For each upgrade, deposit more materials in the controller to unlock and automatically build a new safe **planned mine section**. Confirm preview and terrain feasibility. Output rises according to tier and staff, within the deposit's available yield and a configurable production cap. Mining deposits can eventually exhaust; players must expand or establish a new site.
9. Output flows to the persistent kingdom resource ledger/warehouse. The construction input chest and the warehouse stock are **separate**. In the complete warehouse release allow real ingot withdrawal; do not claim withdrawal exists in v0.7.4.

### Example first-tier blueprint (balancing placeholders, not final)
- `Iron Mine I`: 64 logs + 128 stone + 16 iron ingots; 9x9 entrance, 3x3 walkable tunnel for a bounded length; max 2 workers; production `2 * workers * geological_quality_factor` per minute, limited by reserve and warehouse space.
- `Iron Mine II`: 96 logs + 192 stone + 32 iron ingots; extends an adjacent surveyed section; max 4 workers. Costs and rates require playtesting.
- Never create vanilla items out of thin air during construction or take materials from arbitrary nearby chests without explicit designation/permission.

### Acceptance criteria
- Create kingdom -> find suitable site -> place controller -> open a real inventory UI -> deposit materials -> confirm preview -> click Build -> watch physical blocks get placed -> assign workers -> observe iron -> save/reload -> keep progress.
- With insufficient materials, no build and no item loss. With obstruction, refuse before charging; never damage unrelated structures.
- Duplication tests: simultaneous UI access, shift-click, hoppers, reload mid-build, cancellation, and breaking the controller.
- The old support placement / eight-cardinal-rock-sample system is deprecated, as are all instructions that require players to place support blocks by hand.
- Distinguish **design** from **shipped**: current v0.7.4 still implements old supports. Use a new version once block entity, GUI, and blueprint builder have actually compiled and passed live Forge checks.

## Territory without manual chunk claiming (design decision Oct 2026)
- **No clicking or manually selecting chunks for normal Iron Forge gameplay.** The old claim-grid workflow is deprecated. In the live mining preview, registering a Mine Controller requires kingdom membership and valid geology, **not any claimed chunk**.
- In the full reboot, **placing a Town Hall / Town Banner creates one automatically managed, bounded territory** (initial proposal 96 block radius, configurable). Show its border and settlement status on the War Table, not individual claim squares.
- Nearby mines, farms, barracks, watchtowers and resource sites register themselves to the relevant town by position and kingdom ownership. New towns and land extensions require a real settlement/outpost and a cost or captured banner, never hand-painting a map.
- Territory ownership, capture, and production are stored in lightweight Iron Forge records. They do not require loading terrain/chunks, per-block resource scans, or force-loading chunks.
- Use a compact server-side ownership-index of town shapes for placement/security checks. Resolve overlaps by explicit contest/capture rules; never silently overwrite another kingdom's center. Add grief protection independently with forge events or optional integrations.
- **Optional mod compatibility**, not a hard dependency: Villager Recruits may supply physical soldier gameplay if supported via explicit adapters; Open Parties and Claims or FTB Chunks may supply additional protection/party integration; Towns and Towers may provide discoverable settlement structures; MineColonies is a heavy alternative colony experience, not the default economy core. Verify same-version APIs, licenses, permissions, performance and player choice before binding to any external mod.
- Keep a standalone core: War Table strategy, strategic army simulation, production ledger, town capture and territory remain ours; do not copy other mods' licensed code/assets.
- Acceptance criterion: new player founds a kingdom and places a Town Hall, registers and runs a mine, recruits defenders and sees territory on the War Table **without ever invoking a claim-chunk command or menu**.
- Current v0.7.4 mining preview only removes mine claim requirements; it **does not yet implement automatic town territories or third-party adapters**, and older v0.x gameplay may still expose legacy claim mechanics.

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

## Hybrid War Table, battlefield rewards and strict performance rules
**User experience:** Keep the existing idea of armies represented by troop counts and icons on a War Table. Orders are tactical/strategic on the map, while a nearby player can see a much smaller, real in-world battle.

### One army, two representations
- Each squad/army has exactly one server-authoritative record: ownership, units by type, real surviving troop roster/count, morale, supplies, current orders, position, and battle ID.
- **No nearby player / unloaded chunks:** update strategic position and combat at a low configurable cadence (starting target 1 calculation/sec for active battles, even slower when idle). Do NOT force-load chunks or spawn NPCs just to keep a war progressing.
- **Player enters a live conflict:** select a bounded subset of the soldiers to spawn as live physical fighters. Additional troops stay as simulated reserves, can appear as reinforcements, and are reflected in the War Table counts.
- **Player leaves:** persist health, deaths, supplies, active orders and location before despawning representations. No reset of casualties, free army reappearance, multiple active versions of the same unit, or mixing simulated and physical damage for the same soldier.
- Never promise a physical thousand-NPC battle. Use formations, actual objectives, coordinated attacks, audio/particles, and finite reinforcement waves to make 12–24 nearby recruits feel substantial.

### War Table battle display
- Show owner/attacker, army composition, strengths, morale, supplies, approximate map position, fort/tower warning markers, reinforcement routes and battle status.
- Include **Battle Report** after resolution: victor, casualties by troop class, retreats, destroyed equipment, and recovered spoils (food, iron, arrows, wood, stone, equipment if applicable).
- All War Table orders require server-side ownership/rank checks and are acknowledged, so clicking controls never silently fails.

### Winner takes physical battlefield spoils, not magically created rewards
- Soldiers carry **real allocated supplies** from the kingdom warehouse: food, arrows, iron/equipment where applicable. Those goods are subtracted from the source warehouse on dispatch.
- Upon victory, recover a configurable portion of the **losing force's unconsumed, physically carried supplies and salvageable gear**. Losing or retreating armies keep any goods they successfully escape with. Battle destruction reduces the recoverable total.
- Initial balancing target: up to 50–75% of remaining losing-side stores salvageable, capped by victory unit capacity; tune in playtests. No fabricated loot from a map calculation, no transfer of all city warehouse contents, no guaranteed equipment drops.
- **MVP collection:** award recovered stackable materials to a one-time server-side **Battle Spoils ledger**, shown on the War Table. An authorized player claims the ledger into the kingdom warehouse; warehouse cap overflow remains in the ledger until space is available. For later realism, introduce caravans/battlefield collection that can be intercepted; no teleportation claim of delivered goods in that mode.
- Resolve transfer atomically using battle ID + claim status, including concurrent multiplayer requests and crash/reload. A battle can generate its spoils only once; ownership can never be forged by the client.
- An active war or battlefield can affect kingdom economy without a player being present, but reward amounts must correspond to actually supplied units/resources.

### Performance guardrails and profiling
- Start with configurable **24 close physical soldiers per battle** and **48 across a loaded world/server**, not promises of guaranteed capacity. Dynamically throttle nearby special effects/AI planning; never bypass the real entity cap by repeatedly spawning reserves.
- Squad-level path plans with limited individual separation, infrequent strategic update, shared threat scans, pooled temporary render effects, compact network deltas and conditional UI refresh. Avoid full-world tick scans.
- Watchtowers only scan their loaded, ticking regions at a fixed interval (starting target every 20 ticks), use spatial entity queries for a bounded radius, and use edge-triggered alerts instead of repeat per-tick messages.
- Virtual warehouse aggregates resources server-side. No thousands of chest block entities, individual item stacks ticking, or global hopper searches. Transactions use capped per-tick budgets.
- Set explicit reference-machine and encounter benchmarks; target **20 TPS**, measure tick time, active pathfinding count, heap/GC, memory, client FPS, and packets. Downshift graceful fidelity under pressure without changing casualties or item accounting.
- Require soak tests for 2+ simultaneous battles, cross-dimension armies, player joins/logouts, chunk loading/unloading, concurrent reward claims, repeated map openings, and world restarts.
- Never label a build "lag-free" without in-game performance evidence.

## Capturable towns, control points and territory conquest
**Player fantasy:** Medieval capture-the-flag warfare with actual Minecraft settlements. Towns are defended, raided, captured, and counterattacked; the War Table shows progress, and physical fighters contest a town-square banner nearby. The capture point is a `Town Banner` block/control volume, **not an item that must be carried**.

### Settlement and ownership model
- Placeable `Town Hall` / `Town Banner` designates the center of a town and its bounded territory. Register a player-built settlement. Later support opt-in, claimable **vanilla villages** and authored neutral towns rather than automatically taking over all villagers/builds.
- Town data contains persistent ID, controlling kingdom, original founder, chunk set, central banner position, eligible buildings (barracks/warehouse access/tower), garrison army reference, defense tier, control status and loyalty/occupation state.
- Starting version: **one capture zone per town** (radius about 8–12 blocks); a larger fortified city can later require 2–3 sequential points (outer gate, market, town hall). Avoid per-block ownership calculations.
- Towns provide strategic advantages: a periodic capped supply output/trade access, a rally location, defensible location and watchtower network. Do not invent rewards unrelated to actual town production or supplies. Personal chests and builds do not become automatically lootable merely because territory changes hands; use explicit resource-ledger governance and configurable grief-protection rules.

### Capture-the-flag combat rules
1. Captures only start during a declared war (or on deliberately neutral, claimable settlements); require attacker-owned participating troop roster or an authorized kingdom player in capture radius.
2. A town's defending garrison, watchmen, and fortification strength contribute to defense. Towers can detect the attack and issue kingdom alerts + redstone pulses, letting defenders call reinforcements via `R` HUD.
3. Defenders physically present in capture zone **contest/pause progress**; when attackers control the zone, a visible progress bar rises (initial balancing target 90 seconds of uncontested control), with values configurable after playtests.
4. Capture progress persists across player movement and chunk unload. When no player is nearby, a **strategic army objective simulation** advances/pushes back the same control state based on supplies, garrison, morale, and casualties; no extra entities/chunk force-loading.
5. When 100% complete, flip the town once through an atomic server transaction; surviving garrison retreats or remains as defeated units according to war outcome, fortification state updates, and the new controller gets the designated town benefits.
6. A short **occupation/stabilization period** delays full income and resists instant back-and-forth recapture. A town can be liberated in a later war or counterattack; no instant repeated flip exploits.
7. The War Table shows owner color, contested icon, capture percentage, available defenders, known enemy forces and supply routes. Authorized kingdoms may send squads to the point using map clicks; the on-foot player sees capture feedback and reinforcement arrivals.

### Low-lag capture implementation
- One server-side territory objective record per town. Scan a bounded AABB around the banner at a configurable interval (e.g. 20 ticks), not every tick and never the whole world. Group soldiers by authoritative squad roster instead of counting visual proxies.
- Enforce the shared active troop cap and squad AI budgets. A 250-man simulated army may attack a town defended by 100 strategically, while only 12–24 real NPCs are displayed at the immediate front when players are nearby.
- Avoid spawning fresh physical soldiers for each progress update. No duplicated capture progress across players, worlds or simulation modes. Save the objective state and reconcile after restarts.
- Security: server validates kingdom, declared war, troop ownership, world/dimension, proximity, and capture conditions. Client progress bar is presentation only.
- Test cases: contested banner, reinforced defense, watchtower alarm pulse, offline/online transition, player disconnect, two attackers racing to capture, save/load at 99%, recapture cooldown, protection of personal storage, and two simultaneous town sieges.

### Town conquest release gate
A **real client playtest** must demonstrate: build a town → defend with watchmen → trigger an invasion alert → use a reinforcement order → fight near the banner → see capture progress and War Table status → capture and receive legitimate town production → save/reload ownership. Add both one-player AI and two-kingdom multiplayer integration tests before release.

## Town economy without terrain scanning
**Decision:** A town's resource production is a lightweight, server-side settlement simulation, NOT a count of farmland, ore, trees, or blocks found by scanning huge map areas.

### Registration and source of truth
- A Town Hall uniquely registers a bounded settlement with a persistent town ID, tier, owners, and approved production sites (farm, mine, quarry, lumberyard, trade post) as **building records**. Each site is registered by player action, with location and level. Render real structures in Minecraft, but do not require ongoing multichunk block scans.
- Towns may have a designated specialization (farming, mining, trade, garrison). A captured or neutral town retains its building records when ownership changes.
- Building slots, workers, supply costs and upgrades limit production to prevent unlimited output by spamming controllers. Income is not computed from claimed chunk area.
- The kingdom warehouse holds item quantities in a durable virtual resource ledger. A town may have its own capacity/stock and transfer to the kingdom warehouse by an explicit supply route or settlement logistics rule; avoid silently teleporting supplies in an optional caravan mode.
- Mines represent finite or renewable **abstract deposits** according to balance settings. If using finite reserves, reduce deposit values atomically per payout; never mine actual world ore blocks invisibly. Vanilla players may still physically gather items and deposit them into the ledger.

### Calculation and cadence
- Every 60 seconds of server simulation, compute resources for towns in one cheap pass without loading chunks:
  `gross_output(resource) = sum_over_registered_buildings(base_rate(level,resource) * worker_utilization * town_efficiency)`.
  `net_output = gross_output - explicitly modeled upkeep/consumption`.
- `worker_utilization` is 0..1 derived from capped assigned staffed slots; `town_efficiency` includes supply shortages, occupation penalty, damage, and morale if enabled. Use integer fixed-point accounting / fractional carry to avoid rounding exploits.
- A maximum timestamp catch-up window after server restarts avoids infinite offline production; by default progress only during server runtime. Never tick resource-producing block entities per item or continuously search containers.
- Handle warehouse capacity, concurrent UI withdrawals and building payouts with server-authoritative, atomic transactions; avoid item duplication on save/load and conquest.
- Illustrative example only: Level 2 farm +12 food/min; Level 1 iron mine +3 iron/min; Level 1 quarry +5 stone/min; Level 1 lumberyard +8 wood/min; town upkeep -4 food/min. Final balancing needs gameplay tests.
- Player-facing UI shows current stored amounts, gross production, upkeep, net per minute, staffed slots, blocked production reasons, warehouse capacity and town occupation state.

### Capture economy rules
- Conquest transfers town control and its future production/registered structures once the Town Banner capture transaction resolves. Apply temporary occupation efficiency penalty until stabilization.
- Battle loot is separate from new town production. A winning army recovers a controlled fraction of defeated army's actually allocated supplies, not automatically all the old kingdom's warehouse.
- Test towns registered across chunk unloads, repeated captures, 100+ towns simultaneous, server restarts, zero workers, empty food, finite mine exhaustion, fractional rates, and concurrent warehouse claims.

## Implementation milestones and acceptance gates
**M1: Playable defense vertical slice.** Banner + warehouse + barracks + three unit types + basic combat + watchtower + alert/redstone + HUD rally + save persistence. Must compile, boot server, pass unit tests, work in one real client world. No claim of full game before this.
**M2: Tactical war loop.** Squad formations, patrol/hold/retreat, ranged attacks, cavalry mounts, enemy squads, battle objectives, casualty roster persistence, War Table battlefield reports, non-duplicating battle-spoils claims, and a physically contested Town Banner capture objective.
**M3: Kingdom logistics + AI enemy.** Supply/upkeep, troop recruitment limits, kingdom roles, automatic raid AI, defensible settlements.
**M4: Siege + multiplayer.** Castle breach/capture mechanics, arrows/gates/objectives, capturable player-built and opt-in vanilla towns, occupation/conquest rules, permissions, multiple players ordering squads, hostile intelligence/whitelists.
**M5: Performance, polish and release.** Profiling with Spark, sync budgets, chunk/unload tests, save migration strategy, balanced economics, usable textures/audio, client/server soak tests, public gameplay checklist.

Each release requires compilation, automated simulation and protocol tests, Forge dedicated-server smoke test, manual one-player and two-player tests. Benchmark target: 20 TPS on a stated reference server with a representative encounter, not a universal guarantee.

## Explicit non-goals for M1
No gigantic full-world real-time terrain renderer, no bespoke 3D unit models, no unlimited troop spawning, no simulated battles presented as if physically fought, no UI-only facades, no reliance on operator permissions for core gameplay.

## Migration
Old v0.x worlds must be backed up. Reboot changes data models; **do not promise backward compatibility** unless a migration has been implemented and tested. Use a distinct build/release channel until the core passes its acceptance gates.
