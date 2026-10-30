package com.kingdomsclaims;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WarTableNetwork {
    private WarTableNetwork(){}
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"war_table"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){CHANNEL.registerMessage(0,Action.class,Action::write,Action::read,Action::handle);CHANNEL.registerMessage(1,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);}
    public record Action(BlockPos pos,String order,String value){
        static void write(Action p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeUtf(p.order,18);b.writeUtf(p.value,60);}
        static Action read(FriendlyByteBuf b){return new Action(b.readBlockPos(),b.readUtf(18),b.readUtf(60));}
        static void handle(Action p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer sender=ctx.getSender();
            if(sender!=null)ctx.enqueueWork(()->open(sender,p.pos,WarTableService.execute(sender,p.pos,p.order,p.value)));
            ctx.setPacketHandled(true);
        }
    }
    public record ArmyInfo(UUID id,String name,int strength,int fielded,String formation,int uniform,
        int x,int z,int rallyX,int rallyZ,boolean marching,int secondsRemaining){}
    public record Snapshot(BlockPos pos,String faction,String type,int reserves,int assigned,boolean authorized,String message,List<ArmyInfo> armies){
        static void write(Snapshot s,FriendlyByteBuf b){
            b.writeBlockPos(s.pos);b.writeUtf(s.faction,80);b.writeUtf(s.type,12);b.writeVarInt(s.reserves);b.writeVarInt(s.assigned);b.writeBoolean(s.authorized);b.writeUtf(s.message,160);
            b.writeVarInt(s.armies.size());for(var a:s.armies){b.writeUUID(a.id);b.writeUtf(a.name,30);b.writeVarInt(a.strength);b.writeVarInt(a.fielded);b.writeUtf(a.formation,12);b.writeVarInt(a.uniform);
                b.writeInt(a.x);b.writeInt(a.z);b.writeInt(a.rallyX);b.writeInt(a.rallyZ);b.writeBoolean(a.marching);b.writeVarInt(a.secondsRemaining);}
        }
        static Snapshot read(FriendlyByteBuf b){
            BlockPos pos=b.readBlockPos();String faction=b.readUtf(80),type=b.readUtf(12);int reserve=b.readVarInt(),assigned=b.readVarInt();boolean authorized=b.readBoolean();String message=b.readUtf(160);
            int n=Math.min(15,Math.max(0,b.readVarInt()));List<ArmyInfo> armies=new ArrayList<>();for(int i=0;i<n;i++)armies.add(new ArmyInfo(b.readUUID(),b.readUtf(30),b.readVarInt(),b.readVarInt(),b.readUtf(12),b.readVarInt(),
                b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean(),b.readVarInt()));
            return new Snapshot(pos,faction,type,reserve,assigned,authorized,message,armies);
        }
        static void handle(Snapshot s,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WarTableClient.accept(s)));ctx.setPacketHandled(true);}
    }
    public static void open(ServerPlayer player,BlockPos pos,String message){
        if(!player.serverLevel().getBlockState(pos).is(KingdomsClaims.WAR_TABLE.get())||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return;
        var faction=BarracksService.faction(player.serverLevel(),pos);boolean authorized=BarracksService.canManage(player,pos);
        MilitaryData m=MilitaryData.get(player.getServer());List<ArmyInfo> armies=new ArrayList<>();
        if(faction!=null)for(var a:m.armiesOf(faction.id()))armies.add(new ArmyInfo(a.id,a.name,a.strength,a.fielded,a.formation,a.uniform,
            (int)Math.round(a.locationX),(int)Math.round(a.locationZ),a.rallyX,a.rallyZ,a.marching,
            (int)Math.ceil(Math.hypot(a.rallyX-a.locationX,a.rallyZ-a.locationZ)/1.5)));
        WarMapNetwork.send(player,pos,pos.getX()>>4,pos.getZ()>>4,message);
    }
}
