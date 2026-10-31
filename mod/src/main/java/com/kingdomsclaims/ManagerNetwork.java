package com.kingdomsclaims;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class ManagerNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"manager"),()->"2","2"::equals,"2"::equals);
    private static final Map<UUID,Integer> cooldown=new HashMap<>();
    public static void register(){CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(1,View.class,View::write,View::read,View::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public record Request(String action,String target){
        static void write(Request r,FriendlyByteBuf b){b.writeUtf(r.action,20);b.writeUtf(r.target,40);}
        static Request read(FriendlyByteBuf b){return new Request(b.readUtf(20),b.readUtf(40));}
        static void handle(Request r,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            int now=p.server.getTickCount();if(now-cooldown.getOrDefault(p.getUUID(),-100)<4)return;cooldown.put(p.getUUID(),now);
            var f=BlueprintService.atPlayer(p);if(f==null)return;String msg="";
            switch(r.action){
                case "planner"-> {DistrictNetwork.openHere(p);return;}
                case "map"-> {WarMapNetwork.send(p,p.blockPosition(),p.getBlockX()>>4,p.getBlockZ()>>4,"");return;}
                case "military"-> {WarTableNetwork.open(p,p.blockPosition(),"");return;}
                case "towns"-> {TownHallNetwork.open(p);return;}
                case "kingdom"-> {TownHallNetwork.openKingdom(p);return;}
                case "remove"->msg=RegisteredBuildings.get(p.server).remove(p,r.target)?"Building unregistered.":"Cannot unregister that building.";
                case "hire1","hire10","hire50"->msg=BarracksService.execute(p,p.blockPosition(),r.action);
                case "refresh"-> {}
                default->{return;}
            }open(p,msg);
        });ctx.setPacketHandled(true);}
    }
    public record Row(String id,String type,String location,int capacity,boolean active){
        static void write(Row r,FriendlyByteBuf b){b.writeUtf(r.id,36);b.writeUtf(r.type,20);b.writeUtf(r.location,90);b.writeVarInt(r.capacity);b.writeBoolean(r.active);}
        static Row read(FriendlyByteBuf b){return new Row(b.readUtf(36),b.readUtf(20),b.readUtf(90),b.readVarInt(),b.readBoolean());}
    }
    public record View(String name,int population,int housing,int towns,int troops,int reserve,int barracks,int storage,int used,int seconds,boolean funded,int taxes,int[] stock,int[] rates,int[] upkeep,List<Row> buildings,String message){
        static void write(View r,FriendlyByteBuf b){b.writeUtf(r.name,40);for(int n:new int[]{r.population,r.housing,r.towns,r.troops,r.reserve,r.barracks,r.storage,r.used,r.seconds})b.writeVarInt(n);b.writeBoolean(r.funded);b.writeVarInt(r.taxes);for(int n:r.stock)b.writeVarInt(n);for(int n:r.rates)b.writeVarInt(n);for(int n:r.upkeep)b.writeVarInt(n);b.writeVarInt(r.buildings.size());for(Row row:r.buildings)Row.write(row,b);b.writeUtf(r.message,300);}
        static View read(FriendlyByteBuf b){String name=b.readUtf(40);int[] n=new int[9];for(int i=0;i<9;i++)n[i]=b.readVarInt();boolean funded=b.readBoolean();int taxes=b.readVarInt();int[] stock=new int[8],rates=new int[8],upkeep=new int[8];for(int i=0;i<8;i++)stock[i]=b.readVarInt();for(int i=0;i<8;i++)rates[i]=b.readVarInt();for(int i=0;i<8;i++)upkeep[i]=b.readVarInt();int count=b.readVarInt();if(count<0||count>512)throw new IllegalArgumentException("Building list too large");List<Row> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(Row.read(b));return new View(name,n[0],n[1],n[2],n[3],n[4],n[5],n[6],n[7],n[8],funded,taxes,stock,rates,upkeep,rows,b.readUtf(300));}
        static void handle(View r,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ManagerClient.accept(r)));c.setPacketHandled(true);}
    }
    public static void open(ServerPlayer p,String message){
        var f=BlueprintService.atPlayer(p);if(f==null){p.displayClientMessage(Component.literal("Open the manager inside your claim with BUILD permission."),true);return;}
        var server=p.server;UUID owner=SharedStockpile.owner(server,f.id());var land=KingdomData.get(server);var registry=RegisteredBuildings.get(server);int[] stock=new int[8],upkeep=new int[8];
        for(int i=0;i<8;i++)stock[i]=EconomyService.balance(server,owner,OperationsData.GOODS[i]);upkeep[0]=EconomyService.ironDue(server,owner);upkeep[3]=EconomyService.wheatDue(server,owner);upkeep[7]=EconomyService.goldDue(server,owner);
        List<Row> rows=new ArrayList<>();for(var b:registry.of(server,owner)){var w=RegisteredBuildings.world(server,b.dimension);boolean active=!b.dirty&&b.capacity>0&&w!=null&&RegisteredBuildings.owned(w,b);rows.add(new Row(b.id.toString(),b.type,b.min.toShortString(),active?b.capacity:0,active));}
        int towns=0;for(var t:land.towns.values())if(owner.equals(t.parentKingdom))towns++;
        var sources=ProductionNetwork.sources(server,owner,SharedStockpile.get(server).used(owner)>=PhysicalWarehouses.sharedCapacity(server,owner));
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(f.name(),EconomyService.population(server,owner),registry.housing(server,owner),towns,EconomyService.troops(server,owner),MilitaryData.get(server).reserve(f.id()),registry.barracksCapacity(server,owner),PhysicalWarehouses.sharedCapacity(server,owner),SharedStockpile.get(server).used(owner),EconomyService.untilPayment(server,owner),EconomyService.funded(server,owner),ProductionNetwork.expected(sources,300)[7]/2,stock,ProductionNetwork.expected(sources,ProductionClock.SECONDS),upkeep,rows,message));
    }
}
