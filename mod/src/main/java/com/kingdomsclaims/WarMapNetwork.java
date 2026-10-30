package com.kingdomsclaims;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Unified War Table: terrain and server-authorized army actions on one screen. */
public final class WarMapNetwork {
    private WarMapNetwork(){}
    private static final String VERSION="2";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"war_map"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle);
        CHANNEL.registerMessage(1,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);
        CHANNEL.registerMessage(2,Order.class,Order::write,Order::read,Order::handle);
        CHANNEL.registerMessage(3,Action.class,Action::write,Action::read,Action::handle);
    }
    public record Request(BlockPos table,int cx,int cz){
        static void write(Request p,FriendlyByteBuf b){b.writeBlockPos(p.table);b.writeInt(p.cx);b.writeInt(p.cz);}
        static Request read(FriendlyByteBuf b){return new Request(b.readBlockPos(),b.readInt(),b.readInt());}
        static void handle(Request p,Supplier<NetworkEvent.Context> source){var ctx=source.get();ServerPlayer sender=ctx.getSender();
            if(sender!=null)ctx.enqueueWork(()->send(sender,p.table,p.cx,p.cz,""));ctx.setPacketHandled(true);
        }
    }
    public record Order(BlockPos table,UUID army,int x,int z,int cx,int cz){
        static void write(Order p,FriendlyByteBuf b){b.writeBlockPos(p.table);b.writeUUID(p.army);b.writeInt(p.x);b.writeInt(p.z);b.writeInt(p.cx);b.writeInt(p.cz);}
        static Order read(FriendlyByteBuf b){return new Order(b.readBlockPos(),b.readUUID(),b.readInt(),b.readInt(),b.readInt(),b.readInt());}
        static void handle(Order p,Supplier<NetworkEvent.Context> source){var ctx=source.get();ServerPlayer sender=ctx.getSender();
            if(sender!=null)ctx.enqueueWork(()->{
                String message=WarTableService.execute(sender,p.table,"march",p.army+"|"+p.x+"|"+p.z);
                send(sender,p.table,p.cx,p.cz,message);
            });ctx.setPacketHandled(true);
        }
    }
    public record Action(BlockPos table,String action,String value,int cx,int cz){
        static void write(Action p,FriendlyByteBuf b){b.writeBlockPos(p.table);b.writeUtf(p.action,18);b.writeUtf(p.value,60);b.writeInt(p.cx);b.writeInt(p.cz);}
        static Action read(FriendlyByteBuf b){return new Action(b.readBlockPos(),b.readUtf(18),b.readUtf(60),b.readInt(),b.readInt());}
        static void handle(Action p,Supplier<NetworkEvent.Context> source){var ctx=source.get();ServerPlayer sender=ctx.getSender();
            if(sender!=null)ctx.enqueueWork(()->send(sender,p.table,p.cx,p.cz,WarTableService.execute(sender,p.table,p.action,p.value)));
            ctx.setPacketHandled(true);
        }
    }
    public record Snapshot(BlockPos table,int cx,int cz,short[] terrain,byte[] claims,
        String faction,String type,int reserves,int assigned,boolean authorized,String message,List<WarTableNetwork.ArmyInfo> armies){
        static void write(Snapshot s,FriendlyByteBuf b){
            b.writeBlockPos(s.table);b.writeInt(s.cx);b.writeInt(s.cz);
            for(short c:s.terrain)b.writeShort(c&0xFFFF);
            b.writeByteArray(s.claims);
            b.writeUtf(s.faction,80);b.writeUtf(s.type,12);b.writeVarInt(s.reserves);b.writeVarInt(s.assigned());b.writeBoolean(s.authorized);b.writeUtf(s.message,160);
            b.writeVarInt(s.armies.size());
            for(var a:s.armies){b.writeUUID(a.id());b.writeUtf(a.name(),30);b.writeVarInt(a.strength());b.writeVarInt(a.fielded());
                b.writeUtf(a.formation(),12);b.writeVarInt(a.uniform());b.writeInt(a.x());b.writeInt(a.z());b.writeInt(a.rallyX());b.writeInt(a.rallyZ());b.writeBoolean(a.marching());b.writeVarInt(a.secondsRemaining());}
        }
        static Snapshot read(FriendlyByteBuf b){
            BlockPos table=b.readBlockPos();int cx=b.readInt(),cz=b.readInt();
            short[] terrain=new short[WarMapData.SIZE*WarMapData.SIZE*WarMapData.SUB*WarMapData.SUB];
            for(int i=0;i<terrain.length;i++)terrain[i]=b.readShort();
            byte[] claims=b.readByteArray(WarMapData.SIZE*WarMapData.SIZE);
            String faction=b.readUtf(80),type=b.readUtf(12);int reserves=b.readVarInt(),assigned=b.readVarInt();boolean authorized=b.readBoolean();String message=b.readUtf(160);
            int n=b.readVarInt();if(n<0||n>15)throw new IllegalArgumentException("Invalid army list size");
            List<WarTableNetwork.ArmyInfo> armies=new ArrayList<>();
            for(int i=0;i<n;i++)armies.add(new WarTableNetwork.ArmyInfo(b.readUUID(),b.readUtf(30),b.readVarInt(),b.readVarInt(),b.readUtf(12),b.readVarInt(),
                b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean(),b.readVarInt()));
            return new Snapshot(table,cx,cz,terrain,claims,faction,type,reserves,assigned,authorized,message,armies);
        }
        static void handle(Snapshot s,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WarMapClient.accept(s)));ctx.setPacketHandled(true);}
    }
    public static boolean isAuthorizedControl(ServerPlayer p,BlockPos control){
        var block=p.serverLevel().getBlockState(control);
        boolean compatible=block.is(KingdomsClaims.WAR_TABLE.get())||block.is(KingdomsClaims.KINGDOM_MANAGER.get());
        return compatible && p.distanceToSqr(control.getX()+.5,control.getY()+.5,control.getZ()+.5)<=64
            && BarracksService.faction(p.serverLevel(),control)!=null && BarracksService.canManage(p,control);
    }
    public static void send(ServerPlayer p,BlockPos table,int cx,int cz,String message){
        if(!isAuthorizedControl(p,table))return;
        if(Math.abs((long)cx-(table.getX()>>4))>128||Math.abs((long)cz-(table.getZ()>>4))>128)return;
        var faction=BarracksService.faction(p.serverLevel(),table);
        if(faction==null||!BarracksService.canManage(p,table))return;
        WarMapData map=WarMapData.get(p.getServer());map.exploreAround(p.serverLevel(),p.blockPosition(),8);
        KingdomData data=KingdomData.get(p.getServer());String dimension=KingdomData.dim(p.serverLevel());
        int n=WarMapData.SIZE,sub=WarMapData.SUB;
        short[] terrain=new short[n*n*sub*sub];byte[] claims=new byte[n*n];
        java.util.Arrays.fill(terrain,(short)WarMapData.UNKNOWN);
        for(int dz=0;dz<n;dz++)for(int dx=0;dx<n;dx++){
            int x=cx+dx-n/2,z=cz+dz-n/2,index=dz*n+dx;
            short[] tile=map.tile(dimension,x,z);
            if(tile==null)continue;
            System.arraycopy(tile,0,terrain,index*sub*sub,sub*sub);
            var claim=data.claimAt(dimension,x,z);if(claim==null)continue;
            var town=data.townFor(claim);
            boolean friendly=town!=null?town.id.equals(faction.id()):data.controlled(faction.id(),dimension,x,z);
            claims[index]=(byte)(town!=null?(friendly?2:4):(friendly?1:3));
        }
        MilitaryData military=MilitaryData.get(p.getServer());List<WarTableNetwork.ArmyInfo> armies=new ArrayList<>();
        for(var a:military.armiesOf(faction.id()))if(a.dimension.equals(dimension)){
            armies.add(new WarTableNetwork.ArmyInfo(a.id,a.name,a.strength,a.fielded,a.formation,a.uniform,
                (int)Math.round(a.locationX),(int)Math.round(a.locationZ),a.rallyX,a.rallyZ,a.marching,
                (int)Math.ceil(Math.hypot(a.rallyX-a.locationX,a.rallyZ-a.locationZ)/1.5)));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Snapshot(table,cx,cz,terrain,claims,faction.name(),faction.type(),military.reserve(faction.id()),military.assigned(faction.id()),true,message,armies));
        OperationsNetwork.send(p,table,cx,cz);
    }
}
