package com.kingdomsclaims;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server-validated physical warehouse inventory, production, and shortage snapshot. */
public final class SupplyDepotNetwork {
    private SupplyDepotNetwork(){}
    private static final String V="4";
    public static final int DISPLAY_CAPACITY=4096; // Visual capacity reference; does not erase legacy stock.
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"supply_depot"),()->V,V::equals,V::equals);
    public static void register(){
        CHANNEL.registerMessage(0,View.class,View::write,View::read,View::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Transfer.class,Transfer::write,Transfer::read,Transfer::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
    public static boolean canOpen(ServerPlayer p,BlockPos pos){return faction(p,pos)!=null;}
    private static UUID faction(ServerPlayer p,BlockPos pos){
        if(!p.serverLevel().hasChunkAt(pos)||!p.serverLevel().getBlockState(pos).is(KingdomsClaims.SUPPLY_DEPOT.get())
          ||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return null;
        var f=BarracksService.faction(p.serverLevel(),pos);
        if(f==null)return null;
        KingdomData data=KingdomData.get(p.getServer());
        var k=data.kingdoms.get(f.id());var t=data.towns.get(f.id());
        boolean access=k!=null&&k.allowed(p.getUUID(),KingdomData.STORAGE)
          ||t!=null&&t.allowed(p.getUUID(),KingdomData.STORAGE);
        if(t!=null&&t.parentKingdom!=null){var parent=data.kingdoms.get(t.parentKingdom);access|=parent!=null&&parent.allowed(p.getUUID(),KingdomData.STORAGE);}
        return access?f.id():null;
    }
    public static void open(ServerPlayer p,BlockPos pos,String message){
        UUID owner=faction(p,pos);
        if(owner==null){p.sendSystemMessage(Component.literal("[Depot] Stand near a depot in your claim with STORAGE permission."));return;}
        PhysicalWarehouses.InventoryView view=PhysicalWarehouses.view(p,pos,owner);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new View(pos,view.keys(),view.stock(),view.carried(),
            view.capacity(),view.used(),view.modules(),rates(p,owner,view.keys()),message));
    }
    private static int[] rates(ServerPlayer p,UUID owner,String[] keys){int[] base=PhysicalWarehouses.rates(p.serverLevel(),owner,p.blockPosition()),result=new int[keys.length];for(int i=0;i<keys.length;i++)for(int n=0;n<OperationsData.GOODS.length;n++)if(keys[i].equals(OperationsData.GOODS[n]))result[i]=base[n];return result;}
    public record View(BlockPos pos,String[] keys,int[] stored,int[] carried,int capacity,int used,int modules,int[] rates,String message){
        static void write(View a,FriendlyByteBuf b){
            b.writeBlockPos(a.pos);b.writeVarInt(a.keys.length);for(String key:a.keys)b.writeUtf(key,128);for(int n:a.stored)b.writeVarInt(n);for(int n:a.carried)b.writeVarInt(n);
            b.writeVarInt(a.capacity);b.writeVarInt(a.used);b.writeVarInt(a.modules);for(int n:a.rates)b.writeVarInt(n);b.writeUtf(a.message,200);
        }
        static View read(FriendlyByteBuf b){
            BlockPos pos=b.readBlockPos();int size=b.readVarInt();if(size<8||size>4096)throw new IllegalArgumentException("Stockpile too large");String[] keys=new String[size];for(int i=0;i<size;i++)keys[i]=b.readUtf(128);
            int[] stock=new int[size],carried=new int[size];
            for(int i=0;i<size;i++)stock[i]=b.readVarInt();for(int i=0;i<size;i++)carried[i]=b.readVarInt();
            int cap=b.readVarInt(),used=b.readVarInt(),modules=b.readVarInt();int[] rates=new int[size];for(int i=0;i<size;i++)rates[i]=b.readVarInt();
            return new View(pos,keys,stock,carried,cap,used,modules,rates,b.readUtf(200));
        }
        static void handle(View a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SupplyDepotClient.accept(a)));ctx.setPacketHandled(true);}
    }
    public record Transfer(BlockPos pos,String action,String good){
        static void write(Transfer a,FriendlyByteBuf b){b.writeBlockPos(a.pos);b.writeUtf(a.action,12);b.writeUtf(a.good,128);}
        static Transfer read(FriendlyByteBuf b){return new Transfer(b.readBlockPos(),b.readUtf(12),b.readUtf(128));}
        static void handle(Transfer a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            UUID owner=faction(p,a.pos);if(owner==null){p.sendSystemMessage(Component.literal("[Depot] Not authorized at this depot."));return;}
            String result;
            if(a.action.equals("refresh")){open(p,a.pos,"");return;}
            if(a.action.equals("all")){
                int moved=PhysicalWarehouses.storeAll(p,owner,a.pos);
                result=moved==0?"Nothing deposited; check your carried resources or add expansion blocks.":"Deposited "+moved+" resource items.";
            }else if(!SharedStockpile.validGood(a.good))result="Invalid material.";
            else result=PhysicalWarehouses.transfer(p,a.pos,owner,a.good,a.action);

            open(p,a.pos,result);
        });ctx.setPacketHandled(true);}
    }
}
