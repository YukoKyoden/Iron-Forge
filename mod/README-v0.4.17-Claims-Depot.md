# v0.4.17 - Claims Overlay, Architect Save Improvements, Supply Depot

- In 3D planner, Claims: ON shows nearby owned chunk boundaries (C toggles).
- Blueprint capture skips unsupported block entities/fluids and reports skipped blocks; no item inventories are serialized.
- New craftable Supply Depot block: right-click inside owned claims with STORAGE permission to deposit/withdraw resources.
- Six progress bars show stockpile values against a 4096-item visual reference. The historical saved ledger and production rates are retained to protect existing saves. This is a first physical access point, not a per-block physical inventory system.
- Finance and production logic remain unchanged. Back up your world before updating.
