package com.kingdomsclaims;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

/** Tiny request/response network for a clickable 13x13 local survey. Claims always validated server-side. */
public final class TerritoryNetwork {
    private TerritoryNetwork(){}
    private static final String VERSION="3";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"survey"),()->VERSION,VERSION::equals,VERSION::equals);
    private static int id=0;
    public static void register(){CHANNEL.registerMessage(id++,Request.class,Request::write,Request::read,Request::handle);
        CHANNEL.registerMessage(id++,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);
        CHANNEL.registerMessage(id++,Claim.class,Claim::write,Claim::read,Claim::handle);
        CHANNEL.registerMessage(id++,CycleTown.class,CycleTown::write,CycleTown::read,CycleTown::handle);
    }
    public record Request(boolean town){
        public static void write(Request m,FriendlyByteBuf b){b.writeBoolean(m.town);}
        public static Request read(FriendlyByteBuf b){return new Request(b.readBoolean());}
        public static void handle(Request m,Supplier<NetworkEvent.Context> context){var ctx=context.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->send(p,m.town));ctx.setPacketHandled(true);}
    }
    public record Snapshot(boolean town,int centerX,int centerZ,byte[] cells,int iron,String label){
        public static void write(Snapshot m,FriendlyByteBuf b){b.writeBoolean(m.town);b.writeInt(m.centerX);b.writeInt(m.centerZ);b.writeByteArray(m.cells);b.writeVarInt(m.iron);b.writeUtf(m.label,80);}
        public static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readBoolean(),b.readInt(),b.readInt(),b.readByteArray(169),b.readVarInt(),b.readUtf(80));}
        public static void handle(Snapshot m,Supplier<NetworkEvent.Context> context){var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TerritoryClient.accept(m)));ctx.setPacketHandled(true);}
    }
    public record Claim(boolean town,int cx,int cz){
        public static void write(Claim m,FriendlyByteBuf b){b.writeBoolean(m.town);b.writeInt(m.cx);b.writeInt(m.cz);}
        public static Claim read(FriendlyByteBuf b){return new Claim(b.readBoolean(),b.readInt(),b.readInt());}
        public static void handle(Claim m,Supplier<NetworkEvent.Context> context){var ctx=context.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            String response=KingdomService.claimAt(p,p.serverLevel(),m.cx,m.cz,m.town);
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(response));send(p,m.town);
        });ctx.setPacketHandled(true);}
    }
    public record CycleTown(){
        public static void write(CycleTown m,FriendlyByteBuf b){}
        public static CycleTown read(FriendlyByteBuf b){return new CycleTown();}
        public static void handle(CycleTown m,Supplier<NetworkEvent.Context> supplier){
            var ctx=supplier.get();var p=ctx.getSender();
            if(p!=null)ctx.enqueueWork(()->{KingdomData.get(p.getServer()).nextTown(p.getUUID());send(p,true);});
            ctx.setPacketHandled(true);
        }
    }
    public static void send(ServerPlayer p,boolean town){
        KingdomData d=KingdomData.get(p.getServer());String dim=KingdomData.dim(p.serverLevel());
        var k=d.ofPlayer(p.getUUID());var t=d.townOf(p.getUUID());int centerX=p.blockPosition().getX()>>4,centerZ=p.blockPosition().getZ()>>4;
        byte[] cells=new byte[169];
        for(int dz=-6;dz<=6;dz++)for(int dx=-6;dx<=6;dx++){
            int cx=centerX+dx,cz=centerZ+dz;var claim=d.claimAt(dim,cx,cz);var ownerTown=d.townFor(claim);
            byte code;
            if(ownerTown!=null)code=(t!=null&&t.id.equals(ownerTown.id))?(byte)2:(byte)4;
            else if(claim!=null)code=(k!=null&&d.controlled(k.id,dim,cx,cz))?(byte)1:(byte)3;
            else if(town)code=(t!=null&&KingdomService.claimable(d,t.id,dim,cx,cz,true))?(byte)6:(byte)0;
            else code=(k!=null&&KingdomService.claimable(d,k.id,dim,cx,cz,false))?(byte)5:(byte)0;
            cells[(dz+6)*13+dx+6]=code;
        }
        int iron=town?KingdomService.TOWN_IRON:(k==null?0:KingdomService.ironCost(d,k.id));
        String label=town?(t==null?"No town membership":t.name+" "+d.townClaims(t.id)+"/81"):(k==null?"No kingdom membership":k.name+" "+d.kingdomClaims(k.id)+" chunks");
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new Snapshot(town,centerX,centerZ,cells,iron,label));
    }
}
