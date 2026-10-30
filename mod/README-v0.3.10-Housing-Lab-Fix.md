# Housing Lab v0.3.10 — enclosure and floor fixes

- Run `/housing scan` while inside a house. This is a non-persistent, read-only scanner.
- Roofed pavilions are rejected unless >=80% of walkable floor perimeter is structurally enclosed.
- Ladders and scaffolding count as traversable for the scanner so multi-storey structures can be surveyed.
- Tiny floor-level bumps (furniture surfaces) are not separate housing floors. Full floors must be at least 3 blocks apart.
- Capacity is still 1 hypothetical civilian per 12 validated usable floor blocks, capped at 500 per scan.
- Cave/underground designs still fail the sky-exposed outer-wall check.

Quickly re-run `/function housinglab:cottage`, `/function housinglab:apartment`, `/function housinglab:pavilion`, `/function housinglab:buried_rooms` using the previously supplied test datapack and check `/housing scan` in each one.

NOTE: Does not add civilian simulation, manager blocks, taxes, or modify saved kingdom data.
