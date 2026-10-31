# Kingdoms & Claims v0.4.13 - Better housing capacity estimates

Instead of automatically assigning four residents to every finished blueprint, Architect now estimates residential capacity from the copied structure's **walkable floor tiles**, **exterior enclosure**, **roof coverage**, and **complete, accessible beds**.

- A roofed, enclosed cottage needs at least nine usable indoor floor tiles to count as housing.
- Base housing is roughly one resident per 12 usable floor tiles (minimum one for a qualifying cottage).
- Complete bed pairs located beside accessible indoor space can increase capacity, subject to a maximum density of one resident per six floor tiles and an overall 20-person cap.
- Open pavilions, roofs without walls, empty shells without a roof, and other uninhabitable structures no longer automatically add housing.
- Each valid storey contributes its area, but levels within two blocks of each other are not double-counted.
- The Buildings menu and `/architect list` now show housing estimates. `/architect save` explains the calculated slots.
- On upgrading worlds, completed homes that still have a saved blueprint are migrated once from the old formula to this formula. Builds whose blueprint has been deleted keep their last saved number.
- The estimator is based on the saved blueprint, not live periodic scans of the placed world. Demolition does not automatically unregister a construction record in this release.

## Standalone unit tests

`javac --release 17 -d /tmp/kc-housing src/main/java/com/kingdomsclaims/HousingMath.java tests/HousingMathTest.java && java -cp /tmp/kc-housing HousingMathTest`

Minecraft Forge 1.20.1. Test in a backup of your world before upgrading.
