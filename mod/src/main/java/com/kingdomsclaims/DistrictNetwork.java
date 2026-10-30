package com.kingdomsclaims;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server validated, bounded zoning/treasury protocol; no client authority over ownership or money. */
public final class DistrictNetwork {
    private DistrictNetwork(){}
    public static final int SIZE=32;
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"districts"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle);
        CHANNEL.registerMessage(1,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);
        CHANNEL.registerMessage(2,Action.class,Action::write,Action::read,Action::handle);
        CHANNEL.registerMessage(3,OpenHere.class,OpenHere::write,OpenHere::read,OpenHere::handle);
        CHANNEL.registerMessage(4,ClaimChunk.class,ClaimChunk::write,ClaimChunk::read,ClaimChunk::handle);
    }
    /** Remote access from any owned claim: server derives the anchor from the sender, not the packet. */
    public record OpenHere(){
        static void write(OpenHere a,FriendlyByteBuf b){}
        static OpenHere read(FriendlyByteBuf b){return new OpenHere();}
        static void handle(OpenHere a,Supplier<NetworkEvent.Context> supplier){
            var ctx=supplier.get();ServerPlayer p=ctx.getSender();
            if(p!=null)ctx.enqueueWork(()->openHere(p));ctx.setPacketHandled(true);
        }
    }
    public static void openHere(ServerPlayer player){
        if(BlueprintService.atPlayer(player)==null){
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Planner] Stand in your claimed territory with BUILD permission."));return;
        }
        BlockPos here=player.blockPosition();
        send(player,here,here.getX()>>4,here.getZ()>>4,"");
    }
    /** A 3D selection is advisory; the server always validates reach, adjacency,
     * banner limits, permission and inventory with the existing claim service. */
    public record ClaimChunk(BlockPos manager,int cx,int cz,boolean town){
        static void write(ClaimChunk m,FriendlyByteBuf b){b.writeBlockPos(m.manager);b.writeInt(m.cx);b.writeInt(m.cz);b.writeBoolean(m.town);}
        static ClaimChunk read(FriendlyByteBuf b){return new ClaimChunk(b.readBlockPos(),b.readInt(),b.readInt(),b.readBoolean());}
        static void handle(ClaimChunk m,Supplier<NetworkEvent.Context> source){
            var context=source.get();ServerPlayer p=context.getSender();
            if(p!=null)context.enqueueWork(()->{
                var faction=authorized(p,m.manager);
                String status=faction==null?"Claim access denied: reopen your planner while standing in owned land.":
                    KingdomService.claimAt(p,p.serverLevel(),m.cx,m.cz,m.town);
                if(faction!=null)send(p,m.manager,m.manager.getX()>>4,m.manager.getZ()>>4,status);
                else p.sendSystemMessage(net.minecraft.network.chat.Component.literal(status));
            });context.setPacketHandled(true);
        }
    }
    public record Request(BlockPos manager,int cx,int cz){
        static void write(Request a,FriendlyByteBuf b){b.writeBlockPos(a.manager);b.writeInt(a.cx);b.writeInt(a.cz);}
        static Request read(FriendlyByteBuf b){return new Request(b.readBlockPos(),b.readInt(),b.readInt());}
        static void handle(Request a,Supplier<NetworkEvent.Context> src){var ctx=src.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->send(p,a.manager,a.cx,a.cz,""));ctx.setPacketHandled(true);}
    }
    public record Action(BlockPos manager,String operation,UUID zone,String name,int type,long[] positions,int cx,int cz){
        static void write(Action a,FriendlyByteBuf b){b.writeBlockPos(a.manager);b.writeUtf(a.operation,16);b.writeUUID(a.zone);b.writeUtf(a.name,32);b.writeVarInt(a.type);b.writeVarInt(a.positions.length);for(long key:a.positions)b.writeLong(key);b.writeInt(a.cx);b.writeInt(a.cz);}
        static Action read(FriendlyByteBuf b){return new Action(b.readBlockPos(),b.readUtf(16),b.readUUID(),b.readUtf(32),b.readVarInt(),readPositions(b),b.readInt(),b.readInt());}
        static void handle(Action a,Supplier<NetworkEvent.Context> src){var ctx=src.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->send(p,a.manager,a.cx,a.cz,execute(p,a)));ctx.setPacketHandled(true);}
    }
    private static long[] readPositions(FriendlyByteBuf b){
        int count=b.readVarInt();if(count<0||count>DistrictData.MAX_PAINT)throw new IllegalArgumentException("Invalid painted chunk count");
        long[] result=new long[count];for(int i=0;i<count;i++)result[i]=b.readLong();return result;
    }
    public record ZoneInfo(UUID id,String name,int type,int level,int chunks){}
    /** Bounded server-sent map pins, derived only from this faction's stored sites. */
    public record Marker(String kind,int x,int y,int z){}

    public record Snapshot(BlockPos manager,int cx,int cz,String faction,String factionType,short[] terrain,byte[] claims,byte[] zoning,
            List<ZoneInfo> districts, int gold,int iron,int wheat,int population,int capacity,int jobs,int food,int tax,int income,int payroll,int happy,
            boolean funded,int seconds,String message,int[] stockpile,int[] carried,int ironDue,int wheatDue,List<Marker> markers){
        static void write(Snapshot a,FriendlyByteBuf b){
            b.writeBlockPos(a.manager);b.writeInt(a.cx);b.writeInt(a.cz);b.writeUtf(a.faction,72);b.writeUtf(a.factionType,10);
            for(short t:a.terrain)b.writeShort(t&0xffff);
            b.writeByteArray(a.claims);b.writeByteArray(a.zoning);
            b.writeVarInt(a.districts.size());for(ZoneInfo z:a.districts){b.writeUUID(z.id);b.writeUtf(z.name,32);b.writeVarInt(z.type);b.writeVarInt(z.level);b.writeVarInt(z.chunks);}
            for(int n:new int[]{a.gold,a.iron,a.wheat,a.population,a.capacity,a.jobs,a.food,a.tax,a.income,a.payroll,a.happy,a.seconds})b.writeVarInt(n);
            b.writeBoolean(a.funded);b.writeUtf(a.message,160);
            for(int n:a.stockpile)b.writeVarInt(n);
            for(int n:a.carried)b.writeVarInt(n);
            b.writeVarInt(a.ironDue);b.writeVarInt(a.wheatDue);
            b.writeVarInt(a.markers.size());
            for(Marker m:a.markers){b.writeUtf(m.kind,12);b.writeInt(m.x);b.writeInt(m.y);b.writeInt(m.z);}
        }
        static Snapshot read(FriendlyByteBuf b){
            BlockPos pos=b.readBlockPos();int cx=b.readInt(),cz=b.readInt();String name=b.readUtf(72),type=b.readUtf(10);
            short[] terrain=new short[SIZE*SIZE*WarMapData.SUB*WarMapData.SUB];for(int i=0;i<terrain.length;i++)terrain[i]=b.readShort();
            byte[] claims=b.readByteArray(SIZE*SIZE),zones=b.readByteArray(SIZE*SIZE);
            if(claims.length!=SIZE*SIZE||zones.length!=SIZE*SIZE)throw new IllegalArgumentException("Bad zoning map");
            int count=b.readVarInt();if(count<0||count>DistrictData.MAX_DISTRICTS)throw new IllegalArgumentException("Invalid zone count");
            List<ZoneInfo> list=new ArrayList<>();for(int i=0;i<count;i++)list.add(new ZoneInfo(b.readUUID(),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readVarInt()));
            int[] nums=new int[12];for(int i=0;i<nums.length;i++)nums[i]=b.readVarInt();boolean funded=b.readBoolean();String msg=b.readUtf(160);
            int[] stock=new int[OperationsData.GOODS.length],carry=new int[OperationsData.GOODS.length];
            for(int i=0;i<stock.length;i++)stock[i]=b.readVarInt();
            for(int i=0;i<carry.length;i++)carry[i]=b.readVarInt();
            int ironDue=b.readVarInt(),wheatDue=b.readVarInt();
            int markerCount=b.readVarInt();if(markerCount<0||markerCount>96)throw new IllegalArgumentException("Invalid building marker count");
            List<Marker> markers=new ArrayList<>();
            for(int i=0;i<markerCount;i++)markers.add(new Marker(b.readUtf(12),b.readInt(),b.readInt(),b.readInt()));
            return new Snapshot(pos,cx,cz,name,type,terrain,claims,zones,list,nums[0],nums[1],nums[2],nums[3],nums[4],nums[5],nums[6],nums[7],nums[8],nums[9],nums[10],funded,nums[11],msg,stock,carry,ironDue,wheatDue,markers);
        }
        static void handle(Snapshot a,Supplier<NetworkEvent.Context> src){var ctx=src.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->KingdomOverviewClient.accept(a)));ctx.setPacketHandled(true);}
    }
    private static BarracksService.Faction authorized(ServerPlayer p,BlockPos manager){
        if(manager.equals(p.blockPosition()))return BlueprintService.atPlayer(p);
        if(!p.serverLevel().getBlockState(manager).is(KingdomsClaims.KINGDOM_MANAGER.get()))return null;
        if(p.distanceToSqr(manager.getX()+.5,manager.getY()+.5,manager.getZ()+.5)>64)return null;
        if(!BarracksService.canManage(p,manager))return null;
        return BarracksService.faction(p.serverLevel(),manager);
    }
    private static boolean canMoveResources(ServerPlayer p,UUID faction){
        KingdomData d=KingdomData.get(p.getServer());
        var k=d.kingdoms.get(faction);var t=d.towns.get(faction);
        return k!=null&&k.allowed(p.getUUID(),KingdomData.STORAGE)
            ||t!=null&&t.allowed(p.getUUID(),KingdomData.STORAGE);
    }
    private static String execute(ServerPlayer p,Action a){
        var faction=authorized(p,a.manager);if(faction==null)return "Access denied: stand by your faction's Kingdom Manager.";
        DistrictData store=DistrictData.get(p.getServer());var city=store.city(faction.id());var land=KingdomData.get(p.getServer());String dimension=KingdomData.dim(p.serverLevel());
        if(a.operation.equals("tax")){
            if(a.type<0||a.type>25||a.type%5!=0)return "Tax rate must be 0-25%, in 5% steps.";
            city.tax=a.type;store.setDirty();return "Tax rate set to "+a.type+"%. Changes apply at the next five-minute cycle.";
        }
        if(a.operation.equals("gold")){
            if(!canMoveResources(p,faction.id()))return "STORAGE permission is required to deposit physical gold.";
            String resource=a.type==0?"GOLD_NUGGETS":a.type==1?"GOLD":null;
            if(resource==null)return "Invalid gold resource.";
            int moved=PhysicalWarehouses.storeFromPlayer(p,faction.id(),p.blockPosition(),resource,64);
            return moved>0?"Stored "+moved+" "+(a.type==0?"gold nuggets":"gold ingots")+" at a nearby Warehouse Core.":
                "Carry gold to a Warehouse Core within 96 blocks; make sure it has free capacity.";
        }
        // These obsolete resource commands are deliberately disabled. Modular
        // Warehouses are the only player-facing material transfer system.
        if(List.of("depositiron","depositwheat","store64","take64","storeall").contains(a.operation))
            return "Old stockpile transfers are disabled. Use a local Warehouse Core.";
        // Legacy district records are retained for backwards-compatible world saves,
        // but zone editing no longer affects the economy or building permissions.
        if(List.of("upgrade","delete","create","paint","erase").contains(a.operation))
            return "District zoning has been retired. Build freely inside your claims.";
        DistrictData.Zone zone=store.zones.get(a.zone);
        if(a.operation.equals("upgrade")){
            if(zone==null||!zone.owner.equals(faction.id())||!zone.dimension.equals(dimension))return "Select your own district.";
            if(zone.level>=4)return "Maximum development level is 4.";
            int iron=8*zone.chunks.size()*(zone.level+1),wheat=16*zone.chunks.size()*(zone.level+1);
            if(EconomyService.balance(p.getServer(),faction.id(),"IRON")<iron||EconomyService.balance(p.getServer(),faction.id(),"WHEAT")<wheat)return "Development needs "+iron+" iron and "+wheat+" wheat in treasury.";
            EconomyService.debit(p.getServer(),faction.id(),"IRON",iron);EconomyService.debit(p.getServer(),faction.id(),"WHEAT",wheat);
            zone.level++;store.setDirty();return zone.name+" upgraded to level "+zone.level+".";
        }
        if(a.operation.equals("delete")){
            if(zone==null||!zone.owner.equals(faction.id())||!zone.dimension.equals(dimension))return "Unknown district.";
            store.zones.remove(zone.id);store.setDirty();return "District removed. Development costs are not refunded.";
        }
        if(!a.operation.equals("create")&&!a.operation.equals("paint")&&!a.operation.equals("erase"))return "District zoning has been retired.";
        // Legacy district commands remain server-readable for old saves but are inaccessible from the new planner.
        if(a.positions.length<1||a.positions.length>DistrictData.MAX_PAINT)return "Select 1-128 chunks first.";
        Set<Long> chunks=new LinkedHashSet<>();
        for(long key:a.positions){int x=ChunkPos.getX(key),z=ChunkPos.getZ(key);
            if(Math.abs((long)x-(a.manager.getX()>>4))>128||Math.abs((long)z-(a.manager.getZ()>>4))>128)return "Too far from the Manager.";
            if(!land.identity(dimension,x,z).equals(faction.type()+":"+faction.id()))return "All painted chunks must belong directly to this faction.";
            chunks.add(key);
        }
        if(a.operation.equals("create")){
            if(a.type<0||a.type>=DistrictData.TYPES.length)return "Select a district type.";
            String name=a.name.strip().replaceAll("[\\p{Cntrl}]","");if(name.isEmpty()||name.length()>28)return "Enter a name, 1-28 characters.";
            if(store.of(faction.id(),dimension).size()>=DistrictData.MAX_DISTRICTS)return "Maximum 48 districts per faction in this dimension.";
            for(long key:chunks)if(store.at(faction.id(),dimension,ChunkPos.getX(key),ChunkPos.getZ(key))!=null)return "Selected chunks already belong to a district. Use Paint on an existing district.";
            DistrictData.Zone created=new DistrictData.Zone(UUID.randomUUID(),faction.id(),dimension,name,a.type);created.chunks.addAll(chunks);
            store.zones.put(created.id,created);store.setDirty();return "Created "+name+" ("+chunks.size()+" chunks). Upgrade it to develop population/jobs.";
        }
        if(zone==null||!zone.owner.equals(faction.id())||!zone.dimension.equals(dimension))return "Select your own district.";
        if(a.operation.equals("erase")){
            zone.chunks.removeAll(chunks);if(zone.chunks.isEmpty())store.zones.remove(zone.id);store.setDirty();return "Removed "+chunks.size()+" selected chunks from district.";
        }
        if(zone.chunks.size()+chunks.size()>256)return "Maximum 256 chunks per district.";
        int added=0;for(long key:chunks)if(!zone.chunks.contains(key)){
            if(store.at(faction.id(),dimension,ChunkPos.getX(key),ChunkPos.getZ(key))!=null)return "Some chunks belong to another district.";
            added++;
        }
        int iron=added*8*zone.level,wheat=added*16*zone.level;
        if(EconomyService.balance(p.getServer(),faction.id(),"IRON")<iron||EconomyService.balance(p.getServer(),faction.id(),"WHEAT")<wheat)return "Expansion needs "+iron+" iron and "+wheat+" wheat.";
        if(iron>0)EconomyService.debit(p.getServer(),faction.id(),"IRON",iron);
        if(wheat>0)EconomyService.debit(p.getServer(),faction.id(),"WHEAT",wheat);
        zone.chunks.addAll(chunks);store.setDirty();return "Painted "+added+" additional chunks into "+zone.name+".";
    }
    public static void send(ServerPlayer p,BlockPos pos,int cx,int cz,String message){
        var faction=authorized(p,pos);if(faction==null){p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Kingdom Manager requires a claimed faction and leadership permission."));return;}
        if(Math.abs((long)cx-(pos.getX()>>4))>128||Math.abs((long)cz-(pos.getZ()>>4))>128)return;
        DistrictData data=DistrictData.get(p.getServer());KingdomData land=KingdomData.get(p.getServer());String dim=KingdomData.dim(p.serverLevel());
        WarMapData map=WarMapData.get(p.getServer());map.exploreAround(p.serverLevel(),p.blockPosition(),8);
        List<DistrictData.Zone> zones=data.of(faction.id(),dim);int total=SIZE*SIZE,sub=WarMapData.SUB;
        short[] terrain=new short[total*sub*sub];Arrays.fill(terrain,(short)WarMapData.UNKNOWN);byte[] claims=new byte[total],zoning=new byte[total];
        for(int dz=0;dz<SIZE;dz++)for(int dx=0;dx<SIZE;dx++){
            int x=cx+dx-SIZE/2,z=cz+dz-SIZE/2,i=dz*SIZE+dx;
            short[] tile=map.tile(dim,x,z);if(tile!=null)System.arraycopy(tile,0,terrain,i*sub*sub,sub*sub);
            String ownership=land.identity(dim,x,z);
            claims[i]=(byte)(ownership.equals(faction.type()+":"+faction.id())?1:ownership.isEmpty()?0:2);
            for(int j=0;j<zones.size();j++)if(zones.get(j).chunks.contains(ChunkPos.asLong(x,z))){zoning[i]=(byte)(j+1);break;}
        }
        List<ZoneInfo> summary=new ArrayList<>();for(DistrictData.Zone z:zones)summary.add(new ZoneInfo(z.id,z.name,z.type,z.level,z.chunks.size()));
        int[] stock=new int[OperationsData.GOODS.length],carry=new int[OperationsData.GOODS.length];
        for(int i=0;i<stock.length;i++){
            stock[i]=EconomyService.balance(p.getServer(),faction.id(),OperationsData.GOODS[i]);
            carry[i]=EconomyService.carried(p,OperationsData.GOODS[i]);
        }
        // No world scanning or chunk forcing: use SavedData indexes only.
        List<Marker> markers=new ArrayList<>();
        var warehouses=WarehouseData.get(p.serverLevel());
        for(var entry:warehouses.depots.entrySet()){
            if(markers.size()>=48)break;
            if(!entry.getValue().faction.equals(faction.id()))continue;
            BlockPos warehousePos=BlockPos.of(entry.getKey());
            if(Math.abs(warehousePos.getX()-(cx<<4))>256||Math.abs(warehousePos.getZ()-(cz<<4))>256)continue;
            markers.add(new Marker("WAREHOUSE",warehousePos.getX(),warehousePos.getY(),warehousePos.getZ()));
        }
        for(var site:OperationsData.get(p.getServer()).sites.values()){
            if(markers.size()>=64)break;
            if(site.owner==null||!site.owner.equals(faction.id())||!site.dim.equals(dim))continue;
            if(!site.type.equals("MINE")&&!site.type.equals("FARM")&&!site.type.equals("QUARRY")&&!site.type.equals("FOREST"))continue;
            if(Math.abs(site.x-(cx<<4))>256||Math.abs(site.z-(cz<<4))>256)continue;
            markers.add(new Marker(site.type,site.x,p.serverLevel().getSeaLevel(),site.z));
        }
        var city=data.city(faction.id());CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(pos,cx,cz,faction.name(),faction.type(),terrain,claims,zoning,summary,
                EconomyService.balance(p.getServer(),faction.id(),"GOLD"),EconomyService.balance(p.getServer(),faction.id(),"IRON"),EconomyService.balance(p.getServer(),faction.id(),"WHEAT"),
                city.population,RegisteredBuildings.get(p.server).housing(p.server,faction.id()),RegisteredBuildings.get(p.server).of(p.server,faction.id()).size(),0,
                city.tax,data.estimatedTaxes(faction.id()),data.payroll(p.getServer(),faction.id()),city.happiness,EconomyService.funded(p.getServer(),faction.id()),EconomyService.untilPayment(p.getServer(),faction.id()),message,stock,carry,
                EconomyService.ironDue(p.getServer(),faction.id()),EconomyService.wheatDue(p.getServer(),faction.id()),markers));
    }
}
