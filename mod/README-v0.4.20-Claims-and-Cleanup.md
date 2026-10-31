# v0.4.20: 3D claims and clean modular warehouse interface

- K now opens the 3D planner from owned territory; Claims tool selects real terrain chunks. Server still enforces normal 8-chunk reach, adjacency, claims permissions and inventory payments.
- Warehouse GUI no longer displays or imports virtual legacy balances. Existing ledger data remains saved only for world compatibility/rollback; taxes/upkeep and old operations still use it internally for now.
- Architect hologram checks the owned footprint and shows invalid (red) when any chunk is not owned. Server still validates every placement before consuming items. The preview claim snapshot is limited to 19x19 chunks from the player's starting position and can become stale. Reopen the blueprint after large movements or border changes.
- Back up worlds and remove the older mod JAR before updating.
