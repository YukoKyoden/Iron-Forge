# v0.4.27.1 — Placeable resource markers

Place a Mine Marker or Woodcutter Marker inside your claims with BUILD permission. Placement creates the producer and prints its initial survey and potential output. Right-click reads the cached survey and countdown. Production and automatic rescans happen every six minutes, with no additional world scans from right-clicking or production-screen refreshes. Breaking an automatic marker unregisters that producer. Trees, crops and tunnel blocks are not harvested or changed.

## Mines
The existing Mine Marker appearance and recipe are retained (one rail plus four cobblestone). New markers find the nearest loaded rail within seven blocks in X/Z and up to 35 blocks below, including the marker's own Y level. Place beside the mine opening, outside its required 3x3 clear tunnel. The marker still requires open sky. The connected-track survey retains its 512-rail and 128-block horizontal bounds, owned-claim checks, Y<=32 productive depth, and half an iron per productive section every six minutes. The readout shows connected rails, productive sections and potential iron output. Fractions persist across save/reload. Tracks already used by an earlier registered mine cannot pay out twice.

Existing wand-registered mines keep their entrance, clock and fractional balance. They continue to function and their marker can be right-clicked for the latest cached information. New registrations use the placed-marker flow.

## Woodcutters
Craft a Woodcutter Marker with an iron axe and four planks, or obtain it from the creative tab. Its footprint is centered on the marker: seven blocks in each horizontal direction, 15x15 in total. It surveys exactly 35 blocks above the marker, Y+1 through Y+35. Only loaded blocks in the same kingdom's territory count.

The existing balance is retained: at least eight logs and eight leaves, then one wood per eight detected logs, capped at 32 wood per cycle. The readout shows detected logs, leaves and wood per six minutes. Existing trees remain standing. Earlier registered lumber camps reserve their log areas; overlapping new woodcutters cannot count the same logs twice. Unregister an old camp if replacing it with a marker.

## Houses, wheat and warehouses
House and wheat registration are unchanged. Warehouse behavior is unchanged: one shared kingdom stockpile, 1,024 base spaces, plus 576 per Warehouse Core/connected shelf. Shelves connect in hops of at most ten blocks, within fifty blocks of their core. A registered warehouse area's chests/barrels add capacity, but their vanilla inventory contents are not automatically imported. Goods can be deposited and withdrawn through warehouse interfaces. Production credits the shared stockpile directly; local warehouse inventories and mandatory inter-town transportation are not implemented in this shared-stockpile build.

## Validation
The workflow builds/reobfuscates Forge 1.20.1 and checks saved marker positions, automatic registration flags, cached counts, fractional iron and production deadlines. Pure tests check exact 15x15x35 bounds and retained wood balance, alongside existing rail and squad tests. In-game placement, claim edges, overlap and six-minute timing still need playtesting.
