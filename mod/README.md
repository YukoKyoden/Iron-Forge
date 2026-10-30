# Kingdoms & Claims v0.2.3 — Scrollable Directories & Kingdom Banner GUI (Forge 1.20.1)

**Minecraft 1.20.1 | Forge 47.4.28 | Java 17 | Mod ID `kingdomsclaims`**

This version includes the existing 15-town and one-kingdom-banner rules, **a GUI-based Town Hall**, and discoverable documentation for the **already implemented crafting recipes**.

## Town Hall

- Press **H** anywhere, use `/town menu`, or **right-click your own Town Banner** to open Town Hall.
- **Overview:** selected town, population, claim count, connected kingdom status; create another town, accept a pending invitation, or open claim map.
- **Claims:** view town's size, open the clickable K-key claim survey, or claim the chunk you're standing in.
- **Members:** invite online players by entering their name; select existing members; remove members, hand over mayorship, or leave a town. Mayors cannot leave until they transfer or disband.
- **Permissions:** select a member and toggle individual Build, Break, Storage, Claim, Banner, and Gates permissions. Mayors retain full access.
- **Banner:** rename, relocate using the block you're looking at as the new supporting base, approve annexation when next to a kingdom, or disband with a confirmation click. Disband permanently releases town claims.
- Open **All towns (N)** to see a clickable town directory. Mouse wheel or up/down buttons scroll through all 15 memberships; selecting a town updates active ownership and saved selection.
- On **Members** and **Perms**, click any listed member in the scrolling roster (not just Next/Previous). In Members, **Browse online** shows an online player list: click one to autofill the invitation field, then press Invite.
- **Kingdom** tab shows your capital location, treasury and claim count. Relocate Kingdom Banner by looking at the desired block face and clicking **Relocate Kingdom Banner**; existing 8 iron cost, 30-minute cooldown, clear open-sky 3×3 plaza, must land in owned kingdom territory, no active siege. It is server-authorized.
- **Right-click Kingdom Banner** or run `/kingdom menu` to open directly on the Kingdom tab. Town Banner continues opening town controls.
- All permission-sensitive actions are rechecked on the server. UI state is synchronized from the saved kingdom data; no fake-client claim action is trusted.
- Commands remain fully supported for advanced use and recovery.

## Crafting both banner blocks — survival crafting table

**Kingdom Banner (capital):** 3 red wool across the top; middle row iron block + gold block + iron block; bottom row iron block + stick + iron block.

```
Red Wool     Red Wool      Red Wool
Iron Block   Gold Block    Iron Block
Iron Block   Stick         Iron Block
```

**Town Banner:** 3 blue wool across the top; middle row iron block + emerald block + iron block; bottom row iron block + stick + iron block.

```
Blue Wool    Blue Wool     Blue Wool
Iron Block   Emerald Block Iron Block
Iron Block   Stick         Iron Block
```

These shaped recipes are included under `data/kingdomsclaims/recipes/` and were already present in v0.2.1. Crafting a banner does not itself claim land: first create a kingdom or town, then place it in a valid open-sky 3×3 plaza. Only one Kingdom Banner per kingdom; one Town Banner per town.

## Installation

Back up your test world. Remove prior `kingdomsclaims-*.jar` files from client and server `mods` folders, install only `kingdomsclaims-0.2.3.jar`, and start with Minecraft 1.20.1 / Forge 47.4.28. Existing SavedData from v0.2.1 is preserved.

## Test plan

1. Open H in a world with zero towns. Create a town, get its crafted banner, place it in an unclaimed, valid plaza.
2. Right-click your Town Banner: verify Town Hall opens with the correct town. Open `/town menu` and compare.
3. Create several towns, open **All towns**, scroll with mouse wheel or up/down, and select a town. Verify the menu and K survey target that town.
4. Claim a chunk from the Claims tab and from the K map; verify resources and ownership change.
5. With two players: use the **Browse online** list to choose and invite a player, use the scrollable member roster to toggle permissions, and verify non-OP protections.
6. Rename town, relocate banner with 8 iron by looking at the new foundation, and verify both persist after restarting world.
7. Test disband with a disposable town only; other towns must remain unaffected.
8. Verify both crafting recipes in a crafting table in Survival. Place at most one Kingdom Banner. Open `/kingdom menu` or right-click your capital, move it via the GUI, then verify no extra banner was created and claims stay intact.

**Known limits:** invitee must be online; GUI supports a single annexable bordering kingdom at a time. Forge compilation and JAR validation do not replace Minecraft integration playtesting. Combat, advanced roles, and injuries are not included.

## v0.2.4 — Banner visuals + menu usability

- Town/Kingdom Banners are now three-block structures, registered and protected by their base block. Custom 16x32 pixel cloth textures keep a Minecraft-style look.
- Existing registered one-block banners are extended lazily if the upper two spaces are clear; no world wipe or force-loading chunks. If something blocks the two spaces, clear the obstruction and give the server a few seconds.
- Right-click any part of the three-block banner to open its management menu. Relocation moves all sections and retains original ownership.
- New tooltip explanations and confirmations for dangerous Town Hall actions (member removal, transferring mayorship, leaving/disbanding, and banner relocation).
- Better screen header statuses for territory and treasury.

### Test before public release
1. **Back up your world**, remove the old mod JAR, and install v0.2.4 only.
2. Check a previously placed banner grows to 3 blocks if it has air overhead.
3. Test crafting/placing one banner, clicking top/middle/bottom, and relocating it.
4. Verify a second kingdom capital is still forbidden.
5. Test Town Hall scroll selection, button tooltips, permission toggles, leadership transfer confirmation, and disband cancellation with another player.
6. Restart the world and verify claims, capital, and town data persist.

This source was only validated for compilability and static checks; Minecraft playtesting is required.


## v0.2.5 – Texture and self-merge corrections
- Corrected the banner model textures to reference the actual packaged `town_banner_cloth.png` and `kingdom_banner_cloth.png` files. Existing 3-block banners render correctly without replacing them.
- If the same player is both the Mayor of a town and the King of a kingdom, the mayor's approval is implicit; the King may merge directly via Town Hall > Banner > **Merge into Kingdom** or `/town merge` / `/kingdom annex <town>`.
- The same-owner merger still requires a shared chunk border and sufficient kingdom treasury iron at the existing 50%-discounted annexation cost. It retains the town name, banner and identity.
- If the King and Mayor are different players, the Mayor must approve first as before. Independent towns remain fully valid without any kingdom.

## v0.3.1 — War Table & Numbered Military Rosters

**Minecraft 1.20.1 / Forge 47.4.28**

- Existing kingdoms, towns, claims, banners and barracks remain stored in their original save files. This update adds `kingdomsclaims_military` SavedData for numerical soldiers and armies.
- Right-click Barracks Controller to **Hire 1, Hire 10 or Hire 50** to the shared faction reserve. Cost is 3 iron ingots + 6 bread **per unit**. No mobs are spawned by hiring.
- The faction comes from the claim **under that barracks**: town-controlled land hires **Town Militia**, while direct kingdom land hires **Kingdom Soldiers**. An annexed town retains its town militia identity.
- Place a craftable **War Table** in faction territory: top row three oak planks, middle iron ingot / empty map / iron ingot, bottom oak plank / compass / oak plank. Right-click to create named armies (15 per faction) and assign or return troops in batches of 10 or 50. Assigned troops are deducted from unassigned reserves and live in the same saved faction military ledger. No simulated battle or teleportation occurs in this release.
- **Deploy 1 / Deploy 5** from a Barracks spends real reserve troop counts to spawn player-shaped armed NPCs. Limit: 12 physically loaded near each controller. **Return survivors** credits only surviving roster-bound NPCs back to reserve and discards the entities to prevent duplication. Dead troops are lost.
- Previous v0.3 physical militia are treated as *legacy guards* and **not automatically added** to the numerical ledger. They remain functional; recall does not reward free troops for them.
- Troop NPCs render with original 64x64 player-shaped textures, different outfits for town and kingdom factions. They still use simple mob AI (guard/follow/attack hostile monsters); kingdom vs kingdom PvP, unit levels, army marching, War Table combat, siege and compression are planned later.
- Stored limits: 500 reserves+assigned troops per town, 2,000 per kingdom. Physically deployed soldiers are not included in this stored-count cap in v0.3.1; address global army caps before PvP release.
- **Server is authoritative** for troop recruitment, deployment, resources, world position, assignments and leadership permissions. Back up the world before updating.

### Acceptance tests
1. Upgrade an existing v0.3 world; confirm claims, towns, banners and existing militia survive.
2. Place Barracks on direct kingdom land. Hire 10 and confirm 30 iron + 60 bread removed, reserve +10, **no entities spawned**.
3. Place Barracks on an independent town claim. Confirm it says Town Militia and kingdom one says Kingdom Soldiers.
4. Deploy 5: reserve drops by 5 and five player-shaped armed NPCs appear. Return survivors: reserve increases by exactly how many returned. Kill one before recalling and confirm it doesn't return.
5. Craft/place War Table, create an army, assign 10 troops, confirm reserve drops by 10 and the army becomes 10. Return them, and disband empty army.
6. Restart world: stored reserve, army names, assigned troop numbers, physical roster-bound NPCs and existing territories persist.
7. Non-OP outsiders cannot recruit, create armies, or use controllers in protected territory.


## v0.3.2: Military movement & uniforms
- Open War Table, select army. Enter X and Z rally point, choose **March**. Numerical army travels 1.5 blocks/sec of server time; no forced chunk loads. Straight-line strategic movement, **not** physical pathfinding, collision, roads, combat or interception yet. Pause with Hold, Return HQ, or split a stopped army. No teleporting reserves or double-counting.
- Select Column, Line, or Wedge. Sets formation for fielded soldiers following a commander.
- Select Crimson / Royal / Forest / Sand 64x64 player-style skin. Physical army soldiers use the chosen full-body skin, no separate armor item model needed; combat armor still an entity attribute. PNGs may be overridden with a resource pack.
- Deploy 1/5 from stationary army within 20 blocks of a friendly War Table. Max 30 local physical soldiers. Physical units are deducted from the assigned army; recall survivors near the table. Killed units are not restored. Older Barracks troops remain standalone garrisons.
- Back up your world before upgrading. v0.3.1 military ledgers load with default position/skin and formations.


## v0.4.0 — Kingdom Manager (district zoning and virtual economy)

Place the new Kingdom Manager inside territory owned directly by a kingdom or independent/incorporated town. Right-click within eight blocks, with Banner leadership permission. One screen has a sampled terrain map on the left and controls on the right.

DISTRICTS: click or drag across your own claimed chunks (up to 128 per operation), type a custom district name and choose Residential, Commercial, Industrial or Agricultural. Press Create. Then select your district and press Develop +1 to spend 8 iron and 16 wheat per chunk per development level. Level zero is undeveloped and creates no free residents. Paint/Erase zones; selecting a zone and deleting it refunds nothing. Each district has a maximum of 256 chunks and each faction has 48 districts per dimension. Claim ownership is checked on the server.

ECONOMY: deposit gold nuggets (1 Crown each) or gold ingots (9 Crowns each) into the civic treasury. Adjust taxation 0–25%, in 5% steps. Every five server minutes a district economy tick grows virtual population toward the minimum of housing, jobs and agricultural supply capacity, if previous upkeep is funded. Simulated employed citizens pay tax in Crowns; 1 Crown per 50 numerical soldiers goes to payroll. Existing iron and wheat upkeep remains; missions are suspended for failed upkeep. This is numerical simulation: no civilian entities or per-tick block scans. Populations and districts are saved.

This release is a first-playable zoning economy prototype and may require balancing. New Manager block has a temporary texture similar to the War Table. It does not attempt to detect or validate individual custom houses, and district zoning by itself cannot verify the presence of real buildings.
