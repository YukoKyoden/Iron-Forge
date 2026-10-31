package com.kingdomsclaims;

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
import java.util.function.Supplier;

public final class BarracksNetwork {
    private BarracksNetwork(){}
    private static final String VERSION="2";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"barracks"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){CHANNEL.registerMessage(0,Action.class,Action::write,Action::read,Action::handle);CHANNEL.registerMessage(1,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);}
    public record Action(BlockPos home,String command){
        static void write(Action p,FriendlyByteBuf b){b.writeBlockPos(p.home);b.writeUtf(p.command,16);}
        static Action read(FriendlyByteBuf b){return new Action(b.readBlockPos(),b.readUtf(16));}
        static void handle(Action p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer sender=ctx.getSender();
            if(sender!=null)ctx.enqueueWork(()->open(sender,p.home,BarracksService.execute(sender,p.home,p.command)));
            ctx.setPacketHandled(true);
        }
    }
    public record Snapshot(BlockPos home,String faction,String type,int active,int maxPhysical,int reserve,int assigned,int maxReserve,boolean authorized,String message){
        static void write(Snapshot s,FriendlyByteBuf b){
            b.writeBlockPos(s.home);b.writeUtf(s.faction,80);b.writeUtf(s.type,12);b.writeVarInt(s.active);b.writeVarInt(s.maxPhysical);b.writeVarInt(s.reserve);b.writeVarInt(s.assigned);b.writeVarInt(s.maxReserve);b.writeBoolean(s.authorized);b.writeUtf(s.message,200);
        }
        static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readBlockPos(),b.readUtf(80),b.readUtf(12),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(200));}
        static void handle(Snapshot s,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BarracksClient.accept(s)));ctx.setPacketHandled(true);}
    }
    public static void open(ServerPlayer player,BlockPos home,String message){
        if(!player.serverLevel().getBlockState(home).is(KingdomsClaims.BARRACKS.get())||player.distanceToSqr(home.getX()+.5,home.getY()+.5,home.getZ()+.5)>64)return;
        var faction=BarracksService.faction(player.serverLevel(),home);MilitaryData m=MilitaryData.get(player.getServer());
        int physically=0;
        if(faction!=null)for(var soldier:BarracksService.localSoldiers(player.serverLevel(),home))if(soldier.belongs(faction.id(),faction.type(),home))physically++;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(home,faction==null?"Unclaimed land":faction.name(),faction==null?"none":faction.type(),physically,BarracksService.MAX_LOCAL,faction==null?0:m.reserve(faction.id()),faction==null?0:m.assigned(faction.id()),faction==null?0:m.max(faction.type()),BarracksService.canManage(player,home),message));
    }
}
