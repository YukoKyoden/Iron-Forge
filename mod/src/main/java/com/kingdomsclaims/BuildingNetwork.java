package com.kingdomsclaims;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class BuildingNetwork {
    private BuildingNetwork(){}
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"buildings"),()->"1","1"::equals,"1"::equals);
    private static final Map<UUID,Integer> cooldown=new HashMap<>();
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Reply.class,Reply::write,Reply::read,Reply::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Request(BlockPos a,BlockPos b,String type,boolean confirm){
        static void write(Request r,FriendlyByteBuf b){b.writeBlockPos(r.a);b.writeBlockPos(r.b);b.writeUtf(r.type,20);b.writeBoolean(r.confirm);}
        static Request read(FriendlyByteBuf b){return new Request(b.readBlockPos(),b.readBlockPos(),b.readUtf(20),b.readBoolean());}
        static void handle(Request r,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            int now=p.server.getTickCount();if(now-cooldown.getOrDefault(p.getUUID(),-100)<10)return;cooldown.put(p.getUUID(),now);
            String result;int count=0;boolean done=false;
            if(p.distanceToSqr(r.a.getX(),r.a.getY(),r.a.getZ())>128*128||p.distanceToSqr(r.b.getX(),r.b.getY(),r.b.getZ())>128*128)result="Select a building within 128 blocks.";
            else if(r.confirm){result=RegisteredBuildings.get(p.server).register(p,r.a,r.b,r.type);done=result.startsWith("Registered ");}
            else {var s=RegisteredBuildings.get(p.server).preview(p,r.a,r.b,r.type);result=s.detail();count=s.capacity();}
            CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Reply(result,count,done));
        });ctx.setPacketHandled(true);}
    }
    public record Reply(String message,int count,boolean done){
        static void write(Reply r,FriendlyByteBuf b){b.writeUtf(r.message,300);b.writeVarInt(r.count);b.writeBoolean(r.done);}
        static Reply read(FriendlyByteBuf b){return new Reply(b.readUtf(300),b.readVarInt(),b.readBoolean());}
        static void handle(Reply r,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BuildingClient.accept(r)));c.setPacketHandled(true);}
    }
}
