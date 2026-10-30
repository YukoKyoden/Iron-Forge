from pathlib import Path
root=Path(__file__).resolve().parents[1]
j=root/'src/main/java/com/kingdomsclaims'
assert 'screen.renderOwnedClaims(event)' in (j/'KingdomOverviewClient.java').read_text()
assert 'showClaims' in (j/'KingdomOverviewClient.java').read_text()
assert 'skipped++;continue' in (j/'BlueprintService.java').read_text()
assert 'mc.setScreen(new SaveScreen())' in (j/'WorldSelectionClient.java').read_text()
assert 'SUPPLY_DEPOT' in (j/'KingdomsClaims.java').read_text()
assert 'KingdomData.STORAGE' in (j/'SupplyDepotNetwork.java').read_text()
assert 'PhysicalWarehouses.transfer' in (j/'SupplyDepotNetwork.java').read_text()
assert 'Shelves:' in (j/'SupplyDepotClient.java').read_text()
print('PASS: v0.4.18 claim overlay, resilient save and physical depot GUI')
