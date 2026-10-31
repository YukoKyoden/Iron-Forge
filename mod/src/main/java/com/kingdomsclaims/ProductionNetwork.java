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
/** Read-only production snapshot. Uses indexes/cached surveys; never scans or loads building chunks. */
public final class ProductionNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"production"),()->"1","1"::equals,"1"::equals);
    private static final Map<UUID,Integer> cooldown=new HashMap<>();
    public static void register(){CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(1,View.class,View::write,View::read,View::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public static void request(){DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ProductionClient.expect());CHANNEL.sendToServer(new Request());}
    public record Request(){
        static void write(Request r,FriendlyByteBuf b){}static Request read(FriendlyByteBuf b){return new Request();}
        static void handle(Request r,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var p=c.getSender();if(p!=null)c.enqueueWork(()->{int now=p.server.getTickCount();int last=cooldown.getOrDefault(p.getUUID(),-100);if(now>=last&&now-last<20)return;cooldown.put(p.getUUID(),now);open(p);});c.setPacketHandled(true);}
    }
    public record Source(String name,String location,int mask,int[] maximum,String status,int seconds,boolean running){
        static void write(Source r,FriendlyByteBuf b){b.writeUtf(r.name,32);b.writeUtf(r.location,90);b.writeVarInt(r.mask);for(int n:r.maximum)b.writeVarInt(n);b.writeUtf(r.status,140);b.writeVarInt(r.seconds);b.writeBoolean(r.running);}
        static Source read(FriendlyByteBuf b){String name=b.readUtf(32),location=b.readUtf(90);int mask=b.readVarInt();int[] maximum=new int[8];for(int i=0;i<8;i++)maximum[i]=b.readVarInt();return new Source(name,location,mask,maximum,b.readUtf(140),b.readVarInt(),b.readBoolean());}
    }
    public record View(String name,int[] stock,int[] actual,int[] maximum,int[] upkeep,List<Source> sources,boolean full){
        static void write(View r,FriendlyByteBuf b){b.writeUtf(r.name,40);for(int[] a:new int[][]{r.stock,r.actual,r.maximum,r.upkeep})for(int n:a)b.writeVarInt(n);b.writeVarInt(r.sources.size());for(Source s:r.sources)Source.write(s,b);b.writeBoolean(r.full);}
        static View read(FriendlyByteBuf b){String name=b.readUtf(40);int[][] a=new int[4][8];for(int[] row:a)for(int i=0;i<8;i++)row[i]=b.readVarInt();int n=b.readVarInt();if(n<0||n>600)throw new IllegalArgumentException("Production sources too large");List<Source> sources=new ArrayList<>();for(int i=0;i<n;i++)sources.add(Source.read(b));return new View(name,a[0],a[1],a[2],a[3],sources,b.readBoolean());}
        static void handle(View r,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ProductionClient.accept(r)));c.setPacketHandled(true);}
    }
    private static int mask(String type){return switch(type){case "farm"->8;case "mine"->1|2|16;case "lumber camp"->4;default->0;};}
    public static void open(ServerPlayer p){
        var f=BarracksService.faction(p.serverLevel(),p.blockPosition());if(f==null)return;var server=p.server;var land=KingdomData.get(server);UUID owner=SharedStockpile.owner(server,f.id());var k=land.kingdoms.get(owner);var t=land.towns.get(f.id());
        if(!(k!=null&&k.rank(p.getUUID())>0||t!=null&&t.rank(p.getUUID())>0)){p.displayClientMessage(Component.literal("View production inside your own kingdom or town."),true);return;}
        var stock=SharedStockpile.get(server);boolean full=stock.used(owner)>=PhysicalWarehouses.sharedCapacity(server,owner);List<Source> sources=new ArrayList<>();long now=server.overworld().getGameTime();
        for(var b:RegisteredBuildings.get(server).of(server,owner)){
            int mask=mask(b.type);if(mask==0)continue;var world=RegisteredBuildings.world(server,b.dimension);String status;
            if(world==null||!RegisteredBuildings.ownsClaims(world,b,false))status="Paused: territory no longer owned";
            else if(!RegisteredBuildings.owned(world,b))status="Paused: building chunks are unloaded";
            else if(b.dirty)status="Paused: waiting for building validation";
            else if(b.capacity<=0)status="Paused: invalid building; check its requirements";
            else if(full)status="Paused: stockpile full";
            else status=b.type.equals("farm")?"Passive wheat output; visible crops stay planted":"Producing automatically";
            boolean running=!status.startsWith("Paused");int[] maximum=new int[8];if(b.type.equals("farm"))maximum[3]=b.capacity;if(b.type.equals("mine")){maximum[0]=b.capacity;maximum[1]=b.capacity;maximum[4]=b.capacity*2;}if(b.type.equals("lumber camp"))maximum[2]=b.capacity;
            sources.add(new Source(b.type,b.dimension+" "+b.min.toShortString(),mask,maximum,status,(int)Math.min(3600,Math.max(0,(b.next-now+19)/20)),running));
        }
        // Optional military outposts still supply resources; display them separately from registered structures.
        for(var site:OperationsData.get(server).sites.values()){
            if(sources.size()>=599)break;if(site.owner==null||!owner.equals(SharedStockpile.owner(server,site.owner))||!site.stage.equals("OUTPOST"))continue;
            var army=MilitaryData.get(server).armies.get(site.army);boolean stationed=army!=null&&!army.marching&&Math.hypot(army.locationX-site.x,army.locationZ-site.z)<2.5;
            String status=full?"Paused: stockpile full":!EconomyService.funded(server,owner)?"Paused: upkeep unpaid":!stationed?"Paused: garrison is away":"Producing at military outpost";
            int index=Arrays.asList(OperationsData.GOODS).indexOf(site.good());if(index<0)continue;int[] maximum=new int[8];maximum[index]=OperationsService.producedPerCycle(site.type);
            sources.add(new Source("Outpost "+site.type,site.dim+" "+site.x+", "+site.z,1<<index,maximum,status,Math.max(0,ProductionClock.SECONDS-site.passiveSeconds),!status.startsWith("Paused")));
        }
        var city=DistrictData.get(server);sources.add(new Source("Population taxes","Kingdom income",128,new int[8],"Income: "+city.estimatedTaxes(owner)+" gold nuggets / 5 min",EconomyService.untilPayment(server,owner),EconomyService.funded(server,owner)));
        int[] maximum=new int[8],balances=new int[8],upkeep=new int[8];for(Source source:sources)if(source.running)for(int i=0;i<8;i++)maximum[i]=(int)Math.min(Integer.MAX_VALUE,(long)maximum[i]+source.maximum[i]);for(int i=0;i<8;i++)balances[i]=stock.amount(owner,OperationsData.GOODS[i]);upkeep[0]=EconomyService.ironDue(server,owner);upkeep[3]=EconomyService.wheatDue(server,owner);upkeep[7]=EconomyService.goldDue(server,owner);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(f.name(),balances,ProductionData.get(server).recent(server,owner),maximum,upkeep,sources,full));
    }
}
