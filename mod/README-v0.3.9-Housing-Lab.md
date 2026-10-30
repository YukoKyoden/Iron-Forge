# Kingdoms & Claims v0.3.9 — HOUSING LAB (experimental)

This is a **small, safe housing-detection prototype**, not the complete Kingdom Manager or civilian economy. It contains all gameplay from v0.3.8.

## How to test quickly

1. Back up your world, remove old `kingdomsclaims` JARs, install only `kingdomsclaims-0.3.9.jar` in Forge 1.20.1 client/server.
2. Enter a closed-roof custom house in the Overworld. Stand inside a normal open space.
3. Type `/housing scan` (no OP required). The chat shows PASS/FAILED, interior floor blocks, floors, sky-exposed walls and estimated resident capacity. No data is modified.
4. Test a 7x7 or 9x9 **aboveground** enclosed cottage.
5. Test a two-story apartment, standing downstairs. Check if stairs/open access allow both floors to be recognized.
6. Test carved underground rooms/door spam: it should fail due to insufficient sky-exposed outer walls.
7. Test a roof-only pavilion: should fail due to insufficient exterior wall exposure.
8. Test a home with glass windows: should pass, provided exterior walls are exposed.

## Important limitations

- Only the **connected interior around where you stand** is scanned; this is not a citywide house census.
- The scanner is deliberately **read-only** and works only on loaded chunks to avoid server lag or world writes.
- Doesn't create actual civilians, apply taxes, build a Kingdom Manager block, or modify upkeep. The numeric resident figure is an initial balancing estimate **only**.
- Rooms behind closed solid non-door walls may not count together; upstairs floors require connected interior air through stairs/shafts.
- Very large interiors > 49-block width or 10,000 cells will be rejected to protect performance.
- Complex modded roof/window blocks, open exterior arches and cliffside houses may need algorithm improvements after testing.
- Detection is experimental, not a promise of 95% accuracy.

## Report results

Share screenshots of the house exterior and `/housing scan` message. Especially useful: failed aboveground house and underground room that incorrectly passes.
