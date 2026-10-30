# Kingdoms & Claims v0.4.18 — Local physical warehouses

- Supply Depots now read and transfer **real items inside linked vanilla chests/barrels**, not a globally shared resource balance. Place each depot and its containers inside the same kingdom/town claim.
- Sneak + right-click a Supply Depot to toggle **link mode**. Right-click a chest/barrel within 16 blocks to link it. Right-click again to unlink it. Up to 24 containers per depot. Each container belongs to only one depot.
- Right-click the Depot normally to see local resource bars showing actual inventory contents and used/total slots. Deposit/withdraw material with STORAGE permission, or manage the linked container directly as vanilla.
- Move resources by carrying them between town depots. No cross-town instant transfer.
- **Legacy transition:** Existing virtual economy balances are preserved, shown separately and migratable in batches of 64 using *Import legacy 64*. This consumes the legacy balance and inserts physical items so it doesn't duplicate resources. Existing War Table operations, expedition caravans, automated production, and periodic upkeep still use the legacy economy; these older systems have **not yet been converted** to physical warehouse deliveries. Their output must be imported to become physically stored.
- Buildings continue to use the pre-existing blueprint resource cost checks, which generally draw from the player's carried inventory; depot auto-consumption for construction is future work.
- Back up the world first. Links persist per dimension. The vanilla container items remain authoritative: breaking a chest drops its contents normally.
