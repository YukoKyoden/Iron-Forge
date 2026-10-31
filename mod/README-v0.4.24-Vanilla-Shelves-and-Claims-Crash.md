# Kingdoms & Claims v0.4.24
- Corrects a confirmed client crash opening 3D Claims (NullPointerException on nullable `selectedClaimChunk` in `KingdomOverviewClient.renderOwnedClaims`).
- Previously, Java's conditional typing unboxed a nullable `Long` while evaluating a ternary expression. The selected-chunk state is now copied as an explicitly boxed nullable `Long`, and hovering overwrites it only when present.
- Replaces seven flat 32x32 resource shelf fronts with hand-painted 16x16 pixel-art textures influenced by vanilla chiseled bookshelves, featuring oak wood grain and rough resource-color thumbnails.
- Side/top textures are similarly 16x16, using a consistent oak-plank palette.
- Uses the existing opaque cube block models, with no custom renderer, animation or per-tick shelf scans. Warehouse layout limits and resource accounting remain unchanged.
- Automated source/asset checks and Forge compilation do not replace a real Minecraft client test. Back up worlds before upgrading.
