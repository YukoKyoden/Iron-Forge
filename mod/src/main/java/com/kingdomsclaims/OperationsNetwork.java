package com.kingdomsclaims;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Lightweight operation icons and signed-in mission actions layered on the regular War Table. */
public final class OperationsNetwork {
    private OperationsNetwork(){}
    private static final String VERSION="2";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"operations"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){
        CHANNEL.registerMessage(0,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);
        CHANNEL.registerMessage(1,Action.class,Action::write,Action::read,Action::handle);
    }
    public record SiteInfo(UUID id,String type,String stage,int x,int z,boolean scouted,boolean friendly,boolean rival,int total,int progress,int stock,int duration,boolean alert){}
    public record CaravanInfo(UUID id,String good,int amount,int x,int z,boolean friendly,boolean rival){}
    public record Snapshot(BlockPos table,int[] warehouse,List<SiteInfo> sites,List<CaravanInfo> caravans,int ironDue,int wheatDue,int population,int secondsLeft,boolean funded,boolean canPayNext,int paidCycles,int missedCycles,String upkeepStatus){
        static void write(Snapshot s,FriendlyByteBuf b){
            b.writeBlockPos(s.table);for(int q:s.warehouse)b.writeVarInt(q);
            b.writeVarInt(s.sites.size());for(var site:s.sites){
                b.writeUUID(site.id);b.writeUtf(site.type,18);b.writeUtf(site.stage,20);b.writeInt(site.x);b.writeInt(site.z);b.writeBoolean(site.scouted);b.writeBoolean(site.friendly);b.writeBoolean(site.rival);b.writeVarInt(site.total);b.writeVarInt(site.progress);b.writeVarInt(site.stock);b.writeVarInt(site.duration);b.writeBoolean(site.alert);
            }
            b.writeVarInt(s.caravans.size());for(var c:s.caravans){b.writeUUID(c.id);b.writeUtf(c.good,18);b.writeVarInt(c.amount);b.writeInt(c.x);b.writeInt(c.z);b.writeBoolean(c.friendly);b.writeBoolean(c.rival);}
            b.writeVarInt(s.ironDue);b.writeVarInt(s.wheatDue);b.writeVarInt(s.population);b.writeVarInt(s.secondsLeft);b.writeBoolean(s.funded);b.writeBoolean(s.canPayNext);b.writeVarInt(s.paidCycles);b.writeVarInt(s.missedCycles);b.writeUtf(s.upkeepStatus,140);
        }
        static Snapshot read(FriendlyByteBuf b){
            BlockPos table=b.readBlockPos();int[] warehouse=new int[OperationsData.GOODS.length];for(int i=0;i<warehouse.length;i++)warehouse[i]=b.readVarInt();
            int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Too many site markers");List<SiteInfo> sites=new ArrayList<>();
            for(int i=0;i<n;i++)sites.add(new SiteInfo(b.readUUID(),b.readUtf(18),b.readUtf(20),b.readInt(),b.readInt(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
            int k=b.readVarInt();if(k<0||k>96)throw new IllegalArgumentException("Too many caravans");List<CaravanInfo> caravans=new ArrayList<>();
            for(int i=0;i<k;i++)caravans.add(new CaravanInfo(b.readUUID(),b.readUtf(18),b.readVarInt(),b.readInt(),b.readInt(),b.readBoolean(),b.readBoolean()));
            return new Snapshot(table,warehouse,sites,caravans,b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(140));
        }
        static void handle(Snapshot s,Supplier<NetworkEvent.Context> source){var ctx=source.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WarMapClient.acceptOperations(s)));ctx.setPacketHandled(true);}
    }
    public record Action(BlockPos table,String action,String payload,int cx,int cz){
        static void write(Action p,FriendlyByteBuf b){b.writeBlockPos(p.table);b.writeUtf(p.action,22);b.writeUtf(p.payload,110);b.writeInt(p.cx);b.writeInt(p.cz);}
        static Action read(FriendlyByteBuf b){return new Action(b.readBlockPos(),b.readUtf(22),b.readUtf(110),b.readInt(),b.readInt());}
        static void handle(Action p,Supplier<NetworkEvent.Context> source){var ctx=source.get();ServerPlayer sender=ctx.getSender();if(sender!=null)ctx.enqueueWork(()->{
            String result;
            if(p.action.equals("sendcaravan")){
                try{String[] a=p.payload.split("\\|",-1);result=OperationsService.sendCaravan(sender,p.table,UUID.fromString(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]),a[3],Integer.parseInt(a[4]));}catch(Exception e){result="Select an army, cargo and a claimed destination.";}
            }else result=OperationsService.execute(sender,p.table,p.action,p.payload);
            WarMapNetwork.send(sender,p.table,p.cx,p.cz,result,false);
        });ctx.setPacketHandled(true);}
    }
    public static void send(ServerPlayer p,BlockPos table,int cx,int cz){
        var faction=BarracksService.faction(p.serverLevel(),table);if(faction==null||!BarracksService.canManage(p,table))return;
        OperationsData ops=OperationsData.get(p.getServer());MilitaryData mil=MilitaryData.get(p.getServer());WarMapData map=WarMapData.get(p.getServer());String dim=KingdomData.dim(p.serverLevel());
        int[] inventory=new int[OperationsData.GOODS.length];for(int i=0;i<inventory.length;i++)inventory[i]=PhysicalWarehouses.localStock(p.serverLevel(),faction.id(),table,96,OperationsData.GOODS[i]);
        List<SiteInfo> sites=new ArrayList<>();
        for(var site:ops.sites.values()){
            if(sites.size()>=160)break;if(!site.dim.equals(dim)||Math.abs((site.x>>4)-cx)>32||Math.abs((site.z>>4)-cz)>32||map.tile(dim,site.x>>4,site.z>>4)==null)continue;
            boolean friendly=faction.id().equals(site.owner),rival=site.owner!=null&&!friendly;
            boolean seen=site.scouted.contains(faction.id()),alert=false;
            if("OUTPOST".equals(site.stage)&&friendly)for(var a:mil.armies.values())if(a.dimension.equals(dim)&&!a.faction.equals(faction.id())&&Math.hypot(a.locationX-site.x,a.locationZ-site.z)<128){alert=true;break;}
            sites.add(new SiteInfo(site.id,site.type,site.stage,site.x,site.z,seen,friendly,rival,seen?site.total:0,site.progress,site.stock,seen?site.duration():0,alert));
        }
        List<CaravanInfo> caravans=new ArrayList<>();for(var c:ops.caravans.values()){
            if(caravans.size()>=64)break;if(!c.dim.equals(dim)||Math.abs(((int)c.locationX>>4)-cx)>32||Math.abs(((int)c.locationZ>>4)-cz)>32)continue;
            boolean friendly=c.faction.equals(faction.id()),visible=friendly;
            if(!friendly){for(var a:mil.armiesOf(faction.id()))if(Math.hypot(a.locationX-c.locationX,a.locationZ-c.locationZ)<128){visible=true;break;}
                if(!visible)for(var s:ops.sites.values())if("OUTPOST".equals(s.stage)&&faction.id().equals(s.owner)&&Math.hypot(s.x-c.locationX,s.z-c.locationZ)<128){visible=true;break;}}
            if(visible)caravans.add(new CaravanInfo(c.id,c.good,c.amount,(int)c.locationX,(int)c.locationZ,friendly,!friendly));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(table,inventory,sites,caravans,EconomyService.ironDue(p.getServer(),faction.id()),EconomyService.wheatDue(p.getServer(),faction.id()),EconomyService.population(p.getServer(),faction.id()),EconomyService.untilPayment(p.getServer(),faction.id()),EconomyService.funded(p.getServer(),faction.id()),EconomyService.canPayNext(p.getServer(),faction.id()),EconomyService.paidCycles(p.getServer(),faction.id()),EconomyService.missedCycles(p.getServer(),faction.id()),EconomyService.upkeepStatus(p.getServer(),faction.id())));
    }
}
