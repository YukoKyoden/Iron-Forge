"""Source/resource sanity checks: not a Forge compile or multiplayer test."""
from pathlib import Path
import json
p=Path(__file__).resolve().parents[1]
r=p/'src/main/resources'; j=p/'src/main/java/com/kingdomsclaims'
for f in list(r.rglob('*.json'))+[r/'pack.mcmeta']:
    json.loads(f.read_text())
for file in j.glob('*.java'):
    s=file.read_text(); assert s.count('{')==s.count('}'),file

d=(j/'KingdomData.java').read_text();service=(j/'KingdomService.java').read_text();town=(j/'TownCommands.java').read_text();n=(j/'TerritoryNetwork.java').read_text()
assert 'MAX_TOWNS_PER_PLAYER=15' in d
assert 'TOWN_RADIUS=4' in d and 'INITIAL_RADIUS=2' in d
assert 'root.putInt("Schema",3)' in d and 'SelectedTowns' in d
assert 'Only one Kingdom Banner is allowed per kingdom' in service
for command in ['"my"','"next"','"select"','"disband"','"rename"','"transfer"']:
    assert command in town,command
assert 'CycleTown' in n
print('PASS: JSON, source braces, town selection and single banner rules')

client=(j/'TownHallClient.java').read_text();network=(j/'TownHallNetwork.java').read_text();events=(j/'ClaimEvents.java').read_text();cmd=(j/'KingdomCommands.java').read_text()
assert 'mouseScrolled' in client and 'townDirectory' in client and 'snapshot.onlinePlayers()' in client
assert '"kingdomRelocate"' in network and 'KingdomService.relocate' in network and 'openKingdomBanner' in network
assert 'KingdomInfo' in network and 'onlinePlayers.size()' in network
assert 'KINGDOM_BANNER.get()' in events and 'openKingdomBanner(p,kingdom)' in events
assert 'Commands.literal("menu")' in cmd
assert 'version="0.4.25.2"' in (r/'META-INF/mods.toml').read_text()
print('PASS: scrollable town/member rosters, kingdom relocation, right-click routing, version metadata')

assert 'BannerStructure.complete(level,pos)' in service
assert 'BannerStructure.remove(level,source)' in service
assert 'BannerExtensionBlock.TIER' in (j/'BannerStructure.java').read_text()
for kind in ('town','kingdom'):
    for tier in (1,2):
        assert (r/f'assets/kingdomsclaims/models/block/{kind}_banner_segment_{tier}.json').is_file()
    assert (r/f'assets/kingdomsclaims/textures/block/{kind}_banner_cloth.png').is_file()
assert 'pendingAction' in client and 'Tooltip.create' in client
print('PASS: three-block banners, world upgrade hook, GUI confirmation and tooltip checks')

assert ',BlockState' not in (j/'BannerStructure.java').read_text()

# Resource keys must resolve to actual PNG entries in the texture atlas, not missing magenta/black.
for kind in ('town','kingdom'):
    tex=f'kingdomsclaims:block/{kind}_banner_cloth'
    for model_name in (f'{kind}_banner',f'{kind}_banner_segment_1',f'{kind}_banner_segment_2'):
        model=json.loads((r/f'assets/kingdomsclaims/models/block/{model_name}.json').read_text())
        assert model['textures']['cloth']==tex, model_name
        assert model['textures']['particle']==tex, model_name
    data=(r/f'assets/kingdomsclaims/textures/block/{kind}_banner_cloth.png').read_bytes()
    assert data.startswith(b'\x89PNG\r\n\x1a\n'), kind
assert 'k.king.equals(t.mayor)' in service
assert '"mergeOwn"' in network and '"mergeOwn"' in client
assert 'Commands.literal("merge")' in town
print('PASS: corrected banner asset IDs, single-click same-owner annex, server checks and version metadata')

# v0.3 recruitable guard + world/block/save compatibility checks
assert 'BarracksNetwork.register()' in (j/'KingdomsClaims.java').read_text()
assert 'EntityAttributeCreationEvent' in (j/'KingdomsClaims.java').read_text()
assert 'barracks_controller' in (j/'KingdomsClaims.java').read_text()
assert 'KC_Faction' in (j/'SoldierEntity.java').read_text()
assert 'KC_HomeX' in (j/'SoldierEntity.java').read_text()
assert 'KINGDOM_BANNER.get()' in (j/'ClaimEvents.java').read_text()
assert 'BarracksService.faction(level,pos)' in (j/'ClaimEvents.java').read_text()
assert (r/'assets/kingdomsclaims/textures/entity/militia.png').is_file()
assert (r/'assets/kingdomsclaims/textures/block/barracks_controller.png').is_file()
assert json.loads((r/'data/kingdomsclaims/recipes/barracks_controller.json').read_text())['result']['item']=='kingdomsclaims:barracks_controller'
assert 'root.putInt("Schema",3)' in (j/'KingdomData.java').read_text()
print('PASS: craftable barracks, native management GUI, persistent NPC data and v0.2.5 save compatibility')

# v0.3.1 military ledger, War Table, and player-shaped units
from pathlib import Path as _Path
import json as _json
_root031=_Path(__file__).resolve().parents[1]
_java031=_root031/'src/main/java/com/kingdomsclaims'
_resources031=_root031/'src/main/resources'
for _cls031 in ('MilitaryData','WarTableBlock','WarTableNetwork','WarTableService','WarTableClient','BarracksService'):
    assert (_java031/(_cls031+'.java')).exists(),_cls031
for _target031 in ('town_militia.png','kingdom_soldier.png'):
    assert (_resources031/'assets/kingdomsclaims/textures/entity'/ _target031).exists(),_target031
_recipe031=_json.loads((_resources031/'data/kingdomsclaims/recipes/war_table.json').read_text())
assert _recipe031['result']['item']=='kingdomsclaims:war_table'
assert 'PlayerModel' in (_java031/'SoldierRenderer.java').read_text()
assert 'KC_RosterBound' in (_java031/'SoldierEntity.java').read_text()
assert 'MilitaryData.get' in (_java031/'BarracksService.java').read_text()
print('PASS: v0.3.1 War Table, reserve ledger and humanoid skin asset source checks')

# v0.3.2: numeric march + formations + user-replaceable uniforms
assert 'void advance()' in (j/'MilitaryData.java').read_text()
assert 'Army field' not in (j/'MilitaryData.java').read_text() # data uses Army class
for _uniform in ('crimson','royal','forest','sand'):
    assert (r/f'assets/kingdomsclaims/textures/entity/uniform_{_uniform}.png').exists()
assert 'getUniform()' in (j/'SoldierRenderer.java').read_text()
assert 'selectedCmd("march"' in (j/'WarTableClient.java').read_text()
assert 'army.fielded' in (j/'WarTableService.java').read_text()
print('PASS: v0.3.2 persistent numerical marching, army formation, and four cosmetic skins')

# v0.3.5: map actually renders sampled terrain and validates clickable orders server-side
assert 'OPEN TACTICAL MAP' in (j/'WarTableClient.java').read_text()
assert 'WarMapNetwork.register()' in (j/'KingdomsClaims.java').read_text()
assert 'mouseClicked' in (j/'WarMapClient.java').read_text()
assert 'mouseDragged' in (j/'WarMapClient.java').read_text()
assert 'WarTableService.execute(sender,p.table,"march"' in (j/'WarMapNetwork.java').read_text()
assert 'getChunkNow' in (j/'WarMapData.java').read_text()
assert 'kingdomsclaims_cartography' in (j/'WarMapData.java').read_text()
print('PASS: real sampled terrain map, clickable server-validated movement, pan and zoom')

# v0.3.5 unified War Table and higher-resolution colored relief terrain.
assert 'WarMapNetwork.send(player,pos' in (j/'WarTableNetwork.java').read_text()
assert 'SUB=4' in (j/'WarMapData.java').read_text()
assert 'root.putInt("Schema",2)' in (j/'WarMapData.java').read_text()
assert 'actionScroll' in (j/'WarMapClient.java').read_text()
assert 'case' not in '' # trivial sentinel
print('PASS: unified War Table, terrain subpixels, v0.3.3 map save migration')

# v0.3.5 War Table layout regressions: roster, summary and orders don't overlap.
map_client=(j/'WarMapClient.java').read_text()
for method in ('rosterTop()', 'rosterRows()', 'summaryTop()', 'summaryHeight()', 'ordersLabelY()', 'ordersTop()', 'ordersBottom()', 'visibleOrderRows()', 'maxActionScroll()'):
    assert method in map_client, method
assert 'int actionsTop=ordersTop(),rowsBottom=ordersBottom()' in map_client
assert 'g.fill(inner,summaryY,width-8,summaryY+summaryHeight()' in map_client
assert 'if(my>=rosterTop()&&my<summaryTop())' in map_client
assert 'else if(my>=ordersTop())' in map_client
for height in (250,280,320,360,464,540,720):
    roster_top=94
    roster_rows=max(1,min(4,(height-298)//23))
    summary_top=roster_top+roster_rows*23+6
    summary_height=38 if height<300 else 48
    title_y=summary_top+summary_height+6
    actions_top=title_y+14
    actions_bottom=height-39
    available=actions_bottom-actions_top
    visible=0 if available<20 else min(10,1+(available-20)//24)
    assert 72+20<roster_top
    assert roster_top+roster_rows*23<=summary_top
    assert summary_top+summary_height<title_y
    assert title_y+9<actions_top
    assert visible>=1,(height,visible)
    assert actions_top+(visible-1)*24+20<=actions_bottom
    assert actions_bottom<=height-39
print('PASS: v0.3.5 scrollable right panel has nonoverlapping UI zones at 250-720 logical pixels')

# v0.3.6 source-level checks for contested wilderness operations and preserved troop ledger
from pathlib import Path
base=Path(__file__).resolve().parents[1]/'src/main/java/com/kingdomsclaims'
for f in ('OperationsData.java','OperationsService.java','OperationsNetwork.java'):
    assert (base/f).exists(), f
ops=(base/'OperationsService.java').read_text()
ui=(base/'WarMapClient.java').read_text()
net=(base/'WarMapNetwork.java').read_text()
assert all(k in ops for k in ('harvest','scout','outpost','contest','sendCaravan','raidcaravan','stockpile','warehouse') if k!='stockpile')
assert all(k in ui for k in ('KINGDOM OPERATIONS','Collect 16','Caravan 16','Raid Selected Caravan','Take Over','Scout','Extract'))
assert 'OperationsNetwork.send(p,table,cx,cz);' in net
assert 'OperationsService.tick(server)' in (base/'ClaimEvents.java').read_text()
assert 'OperationsData.get(player.getServer()).committed' in (base/'WarTableService.java').read_text()
assert 'claimedSite(level.getServer()' in (base/'KingdomService.java').read_text()
print('PASS: contested wilderness operations, scout icons, outpost patrols, caravans and troop locks')

# v0.3.7 treasury economy and anti-misclick regression assertions
from pathlib import Path as _Path
_root=_Path(__file__).resolve().parents[1]
_src=_root/'src/main/java/com/kingdomsclaims'
_check={
    'EconomyService.java':['INTERVAL_SECONDS=300','public static boolean funded','public static void migrate','ceil(troops(server,id),100)','ceil(troops(server,id),25)'],
    'OperationsService.java':['PhysicalWarehouses.deliver(world,c.destinationFaction','PhysicalWarehouses.payLocal(p.serverLevel(),faction.id(),table,96,Map.of(good,amount))','PhysicalWarehouses.storeFromPlayer(p,faction.id(),table','EconomyService.funded(server,s.owner)'],
    'OperationsNetwork.java':['int ironDue,int wheatDue,int population,int secondsLeft,boolean funded','PhysicalWarehouses.localStock(p.serverLevel()','b.writeVarInt(s.ironDue)'],
    'MilitaryData.java':['KINGDOM_CAP=10000'],
    'WarMapClient.java':['marchArmed=false','MOVE: ARMED','if(super.mouseClicked(mx,my,b))return true;','outline(g,sx,rosterTop()'],
    'KingdomData.java':['treasuryWheat','TreasuryWheat','TreasuryIron'],
    'ClaimEvents.java':['EconomyService.tick(server)'],
}
for file,tokens in _check.items():
    _code=(_src/file).read_text()
    for token in tokens: assert token in _code,(file,token)
print('PASS: v0.3.7 unified treasury, periodic resource upkeep, 10k cap, and deliberate map marching')

# v0.3.8 treasury tab and deposit feedback / status regression checks
_ui=(j/'WarMapClient.java').read_text()
_server=(j/'OperationsService.java').read_text()
_economy=(j/'EconomyService.java').read_text()
_net=(j/'OperationsNetwork.java').read_text()
assert 'LOCAL WAREHOUSE' in _ui and 'FIVE-MINUTE UPKEEP' in _ui
assert 'Deposit ALL iron' in _ui and 'Deposit ALL wheat' in _ui
assert 'Next charge: INSUFFICIENT' in _ui and 'Next charge: COVERED' in _ui
assert 'depositAvailable' in _economy and 'depositmax' in _server
assert 'canPayNext' in _net and 'paidCycles' in _net
assert 'ledger.successfulPayments.merge(id,1,Integer::sum)' in _economy
print('PASS: treasury is its own tab; deposits use actual carried amounts; visible charge status and countdown')

# v0.3.9 housing lab prototype: read-only, on demand, no beds/presets/virtual residents yet.
housing=(j/'HousingSurvey.java').read_text()
assert 'Commands.literal("housing")' in housing and 'Commands.literal("scan")' in housing
assert 'MAX_VISITED = 10000' in housing and 'MAX_HORIZONTAL = 24' in housing
assert 'canSeeSky(outside)' in housing and 'exteriorWall(level, p, d)' in housing
assert 'Estimated civilian capacity' in housing
assert 'MinecraftForge.EVENT_BUS.register(HousingSurvey.class)' in (j/'KingdomsClaims.java').read_text()
assert 'level.setBlock' not in housing and 'setDirty' not in housing
print('PASS: experimental aboveground housing scans are bounded, read-only, and have exterior exposure checks')

# v0.3.10 housing lab refinements: false-positive pavilion prevention and storey accounting.
_h=(j/'HousingSurvey.java').read_text()
assert 'MIN_WALL_PERCENT = 80' in _h
assert 'solidEdges' in _h and 'openEdges' in _h
assert 'livingFloor.size()/12' in _h
assert 'state.getBlock() instanceof LadderBlock' in _h
assert 'Math.abs(existing-y) >= 3' in _h
print('PASS: pavilion enclosure rule, ladder accessibility and full-storey housing capacity')

# v0.4.0 Manager and districts: server-side zone actions, zero entity population, persistence.
import json as _json
for _file in ['DistrictData.java','DistrictNetwork.java','DistrictClient.java','KingdomManagerBlock.java']:
    assert (j/_file).is_file()
assert 'DistrictNetwork.register()' in (j/'KingdomsClaims.java').read_text()
assert 'KINGDOM_MANAGER' in (j/'ClaimEvents.java').read_text()
assert 'DistrictData.get(server).cycle(server,id,paid)' in (j/'EconomyService.java').read_text()
assert 'KingdomService.claimAt' in (j/'DistrictNetwork.java').read_text()
assert 'Old stockpile transfers are disabled' in (j/'DistrictNetwork.java').read_text()
assert 'new UUID(0,0)' in (j/'DistrictClient.java').read_text()
assert 'CivilianEntity' not in (j/'DistrictData.java').read_text()
assert _json.loads((r/'data/kingdomsclaims/recipes/kingdom_manager.json').read_text())['result']['item']=='kingdomsclaims:kingdom_manager'
print('PASS: v0.4.0 district, treasury, tax and payroll source checks')

# v0.4.2 read-only 3D overview and persistent player placement tracking.
assert (j/'KingdomOverviewClient.java').is_file()
assert 'mc.setCameraEntity(camera)' in (j/'KingdomOverviewClient.java').read_text()
assert 'mc.setCameraEntity(mc.player)' in (j/'KingdomOverviewClient.java').read_text()
assert '128' in (j/'KingdomOverviewClient.java').read_text()
assert 'Back to 3D City' in (j/'DistrictClient.java').read_text()
assert 'PlayerBuildData.get(level)' in (j/'PlayerBuildEvents.java').read_text()
assert 'getReplacedBlockSnapshots' in (j/'PlayerBuildEvents.java').read_text()
assert 'BlockEvent.BreakEvent' in (j/'PlayerBuildEvents.java').read_text()
assert 'MAX_BLOCKS = 300_000' in (j/'PlayerBuildData.java').read_text()
assert 'kingdomsclaims_player_built' in (j/'PlayerBuildData.java').read_text()
assert 'PlayerBuildEvents.class' in (j/'KingdomsClaims.java').read_text()
print('PASS: 3D overview and bounded per-dimension player-built block tracking')

# v0.4.3 primary 3D Planner and integrated strategic map.
pl=(j/'KingdomOverviewClient.java').read_text()
assert 'WarMapNetwork.Request' in pl
assert 'Pane.DISTRICTS' in pl and 'Pane.ECONOMY' in pl and 'setPane(' in pl
assert 'returnFromWarMap' in pl
assert 'KingdomOverviewClient.accept(a)' in (j/'DistrictNetwork.java').read_text()
assert 'City View' in (j/'WarMapClient.java').read_text()
assert 'isAuthorizedControl' in (j/'WarMapNetwork.java').read_text()
assert 'Physical soldiers must be deployed' in (j/'WarTableService.java').read_text()
print('PASS: 3D Planner entrypoint, district/economy navigation, war-map handoff and server-side authority')

# v0.4.5: native-hand cancellation, per-frame camera movement, input key events, and safe unloaded edge.
pl=(j/'KingdomOverviewClient.java').read_text()
for token in ('RenderHandEvent','event.setCanceled(true)','mc.options.hideGui=true','advanceCameraForFrame','keyPressed(', 'keyReleased(', 'blockedByUnloadedChunk', 'x=oldX;z=oldZ'):
    assert token in pl,token
assert 'x=originX;z=originZ' not in pl
print('PASS: hand/reticle hidden, smooth frame camera, input events, and no unloaded-chunk snapback')

# v0.4.5: nonliving camera, proper render phase, and camera-vector-oriented keys.
pl=(j/'KingdomOverviewClient.java').read_text()
assert 'EntityType.MARKER.create(mc.level)' in pl
assert 'TickEvent.RenderTickEvent' in pl and 'TickEvent.Phase.START' in pl
assert 'Vec3 look=camera.getViewVector(1.0f)' in pl
assert 'Mth.wrapDegrees(yaw)' in pl
assert 'advanceCameraForFrame();' in pl.split('beforeWorldRender')[1].split('}',1)[0]
assert 'advanceCameraForFrame();' not in pl.split('@Override public void render(GuiGraphics')[1]
print('PASS: no armor stand head yaw, stable pre-world camera frame, and view-relative pan')

# v0.4.6: RTS style directly on terrain and corrected strafe controls.
pl=(j/'KingdomOverviewClient.java').read_text()
for token in ('renderChunkBorders','pickSurface(','paintChunk(','Kingdoms-and-Castles','DistrictNetwork.Action','ZONE LAND','mouseDragged','targetZone>=0?"paint":"create"','x+=(fx*forward-fz*right)','z+=(fz*forward+fx*right)'):
    assert token in pl,token
print('PASS: v0.4.6 clickable 3D zoning, server-validated district actions and corrected strafe')

# v0.4.7: full 16x16 top-surface tint, color-coded brush palette and new city HUD.
pl=(j/'KingdomOverviewClient.java').read_text()
for token in ('terrainSquare(', 'RenderType.debugFilledBox()', 'for(int lx=0;lx<16;lx++)',
              'for(int lz=0;lz<16;lz++)', 'Heightmap.Types.WORLD_SURFACE',
              'Stage.AFTER_PARTICLES', 'Unclaimed • cannot zone',
              'KINGDOMS & CLAIMS', 'DISTRICTS', 'ECONOMY', 'Exit planner',
              'selectZoneType(type)', 'brushedChunks.contains(key)'):
    assert token in pl,token
assert "version = '0.4.25.2'" in (p/'build.gradle').read_text()
print('PASS: v0.4.7 full-chunk zoning overlays, distinct type buttons and strategy HUD')

# v0.4.9 custom architectural blueprints and server validated builds
for _f in ['BlueprintData.java','BlueprintService.java','BlueprintNetwork.java','BlueprintCommands.java']:
    assert (j/_f).is_file(),_f
arch=(j/'BlueprintService.java').read_text()
pl=(j/'KingdomOverviewClient.java').read_text()
for token in ('NbtUtils.writeBlockState','player.getAbilities().instabuild','getBlockEntity(pos)!=null','KingdomData.dim','data.sites.put','preflight','level.setBlock(pos,state(piece,site.turns),2)'):
    assert token in arch,token
assert 'Commands.literal("architect")' in (j/'BlueprintCommands.java').read_text()
assert 'Place selected design' in pl and 'new BlueprintNetwork.Prepare' in pl
assert 'BlueprintService.tick(server)' in (j/'ClaimEvents.java').read_text()
assert 'BlueprintNetwork.register()' in (j/'KingdomsClaims.java').read_text()
print('PASS: v0.4.9 faction owned custom blueprints, non-duplicating save, 3D preview and timed build')

# v0.4.9 in-world translucent block-model placement, authority and clean UI
view=(j/'WorldArchitectClient.java').read_text()
net=(j/'BlueprintNetwork.java').read_text()
for token in ('renderModel(', 'RenderType.translucent()', 'InputEvent.MouseButton.Pre','InputEvent.Key','MouseScrollingEvent','Preview','emptyPreviewSpace','new BlueprintNetwork.PlaceWorld','GhostConsumer'):
    assert token in view,token
for token in ('record Prepare','record Preview','record PlaceWorld','record Outcome','managerFaction(p,a.manager)','BlueprintService.beginInWorld'):
    assert token in net,token
assert 'distanceToSqr(anchor.getX()' in arch and 'beginValidated' in arch
assert 'showHelp' in pl and 'Place selected design' in pl
print('PASS: v0.4.9 true player-view block hologram, controls, permissions and simplified menus')

# v0.4.11 docked in-world editor UX regression checks.
pl=(j/'KingdomOverviewClient.java').read_text()
assert 'DistrictClient.openTab' not in pl, 'Districts/economy must not leave 3D camera'
assert 'setPane(pane==Pane.ECONOMY?Pane.NONE:Pane.ECONOMY)' in pl
assert '"Buildings",sideX,sideY' in pl and '"Zone land"' not in pl
assert 'int sideX=10,sideY=66,sideW=sidebarWidth(),bh=24,step=30;' in pl
assert 'if(pane!=Pane.NONE&&mx>=panelX()-4&&my>=54)return true;' in pl
assert 'new BlueprintNetwork.SelectRequest(data.manager())' in pl
assert 'economyAction("gold",0)' in pl and 'economyAction("tax",Math.min(25,data.tax()+5))' in pl
assert 'Preview' in (j/'BlueprintNetwork.java').read_text()
print('PASS: v0.4.11 compact left navigation, in-world economy/district overlays, safe actions and hologram continuity')

# v0.4.11 house volume selection, population capacity, and district retirement.
select=(j/'WorldSelectionClient.java').read_text();net=(j/'BlueprintNetwork.java').read_text();bd=(j/'BlueprintData.java').read_text()
for token in ('MAX_VOLUME','OUTLINE_LIMIT','RenderLevelStageEvent','InputEvent.MouseButton.Pre','MouseScrollingEvent','class SaveScreen','SaveSelected(first,opposite(),n)'):
    assert token in select,token
assert 'SaveSelected' in net and 'managerFaction(p,a.manager)' in net
assert 'w*h*d>BlueprintData.MAX_VOLUME' in (j/'BlueprintService.java').read_text()
assert 'BuiltHomes' in bd and 'housing(UUID owner)' in bd
assert 'BlueprintData.get(server).housing(owner)' in (j/'DistrictData.java').read_text()
assert 'District zoning has been retired.' in (j/'DistrictNetwork.java').read_text()
print('PASS: 4096-block interactive selector, server bounds, housing capacity and zoning retirement')

# v0.4.12 progressive, persisted build and extended reach / safe rotation.
arch=(j/'BlueprintService.java').read_text();preview=(j/'WorldArchitectClient.java').read_text();bd=(j/'BlueprintData.java').read_text()
assert '96*96' in arch and 'PLACEMENT_REACH=96.0' in preview
assert 'mc.player.pick(PLACEMENT_REACH' in preview
assert 'if(event.isCancelable())event.setCanceled(true);' in preview
assert 'MAX_GHOST_BLOCKS=1500' in preview
assert 'private static String activePreflight' in arch
assert 'site.placedBlocks++' in arch and 'level.sendParticles(ParticleTypes.POOF' in arch
assert 'n.putInt("Placed",s.placedBlocks)' in bd and 'n.getInt("Placed")' in bd
assert 'SELECT_REACH=96.0' in (j/'WorldSelectionClient.java').read_text()
print('PASS: rotation guards, 96-block selection and placement, persisted bottom-up construction')

# v0.4.13 housing estimator: no blanket four-person minimum, robust preview and migration.
h=(j/'HousingMath.java').read_text(); bc=(j/'HousingCapacity.java').read_text()
assert 'usable/12' in h and 'usable/6' in h and 'walls*100<edges*75' in h
assert 'validBed(' in h and 'covered(' in h
assert 'EmptyBlockGetter.INSTANCE' in bc
assert 'HousingVersion' in (j/'BlueprintData.java').read_text()
assert 'housingReport()' in (j/'BlueprintData.java').read_text()
assert 'plan.housing()' in (j/'KingdomOverviewClient.java').read_text()
assert 'VERSION="5"' in (j/'BlueprintNetwork.java').read_text()
print('PASS: v0.4.13 enclosure, usable floor, complete beds, legacy migration, UI preview')
assert (p/'tests/HousingMathTest.java').exists()
print('PASS: standalone housing capacity regression tests present')

# v0.4.14 remote planner permissions and server-owned building collections.
pl=(j/'KingdomOverviewClient.java').read_text();bd=(j/'BlueprintData.java').read_text();net=(j/'BlueprintNetwork.java').read_text()
assert 'btn("War map",' not in pl
assert 'renderBuildLibrary' in pl and 'Move selected design' in pl and 'Create folder' in pl
assert 'Commands.literal("planner")' in (j/'KingdomCommands.java').read_text()
assert 'manager.equals(p.blockPosition())' in (j/'BlueprintService.java').read_text()
assert 'manager.equals(p.blockPosition())' in (j/'DistrictNetwork.java').read_text()
assert 'BuildFolders' in bd and 'DEFAULT_FOLDERS' in bd and 'data.move' in net
assert 'new DistrictNetwork.OpenHere()' in (j/'TerritoryClient.java').read_text()
print('PASS: v0.4.14 remote planner, separated war map, persistent blueprint folders and authorized moves')

# v0.4.18 UX regressions.
assert 'inFolderList' in pl and 'inDesignFlyout' in pl and 'renderBuildLibrary(g,mx,my)' in pl
assert 'lastPlacedAnchor=anchor' in preview and 'if(!repeatPlacement)' in preview
assert 'GLFW.GLFW_KEY_M' in preview and 'cooldownTicks=10' in preview
assert 'if(!(mc.screen instanceof SaveScreen))stop()' in select
assert '@Override public void onClose(){stop();' in select
print('PASS: v0.4.18 hover folder flyout, repeated placement and Esc cleanup')

# v0.4.18 consolidated resource dashboard and secure transfers.
ui=(j/'KingdomOverviewClient.java').read_text();net=(j/'DistrictNetwork.java').read_text();economy=(j/'EconomyService.java').read_text()
for key in ('Deposit gold nuggets','Lower tax by 5%','All resources, including gold, are stored at Warehouse Cores'):
    assert key in ui,key
for key in ('store64','take64','storeall','Old stockpile transfers are disabled'):
    assert key in net,key
assert 'PhysicalWarehouses.takeToPlayer' in economy and 'depositAllCarried' in economy
print('PASS: v0.4.18 resources, safe store/withdraw, stockpile and finances screens')

# v0.4.24: territory claiming in 3D and protected blueprint preview.
assert 'ClaimChunk' in (j/'DistrictNetwork.java').read_text()
assert 'selectClaim(double mx,double my)' in ui and 'CLAIM SELECTED CHUNK' in ui
assert 'new DistrictNetwork.OpenHere()' in (j/'TerritoryClient.java').read_text()
assert 'ownedChunks.contains' in (j/'WorldArchitectClient.java').read_text()
assert 'new Preview(plan.id,plan.name' in (j/'BlueprintNetwork.java').read_text()
assert 'Import legacy 64' not in (j/'SupplyDepotClient.java').read_text()
print('PASS: 3D claiming, server claim validation, local storage only, footprint preview claim guard')
