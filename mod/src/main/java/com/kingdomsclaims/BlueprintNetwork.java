package com.kingdomsclaims;

import java.util.*;
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

/** Server-authorized blueprint preview: only block states and coordinates cross the wire, never inventories. */
public final class BlueprintNetwork {
    private BlueprintNetwork(){}
    private static final String VERSION="5";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"architect"),()->VERSION,VERSION::equals,VERSION::equals);
    public record Summary(UUID id,String name,int width,int height,int depth,int blocks,int seconds,int housing,int floor,int beds,String folder){}
    public record Request(BlockPos manager){
        static void write(Request a,FriendlyByteBuf b){b.writeBlockPos(a.manager);}
        static Request read(FriendlyByteBuf b){return new Request(b.readBlockPos());}
        static void handle(Request a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->send(p,a.manager,""));ctx.setPacketHandled(true);}
    }
    public record Place(BlockPos manager,UUID blueprint,BlockPos anchor,int turns){
        static void write(Place a,FriendlyByteBuf b){b.writeBlockPos(a.manager);b.writeUUID(a.blueprint);b.writeBlockPos(a.anchor);b.writeVarInt(a.turns);}
        static Place read(FriendlyByteBuf b){return new Place(b.readBlockPos(),b.readUUID(),b.readBlockPos(),b.readVarInt());}
        static void handle(Place a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            BlueprintService.Result result=BlueprintService.begin(p,a.manager,a.blueprint,a.anchor,a.turns);
            send(p,a.manager,result.message());
        });ctx.setPacketHandled(true);}
    }
    public record Panel(BlockPos manager,List<Summary> plans,List<String> folders,int active,String message){
        static void write(Panel a,FriendlyByteBuf b){
            b.writeBlockPos(a.manager);b.writeVarInt(a.plans.size());for(Summary s:a.plans){
                b.writeUUID(s.id);b.writeUtf(s.name,28);b.writeVarInt(s.width);b.writeVarInt(s.height);b.writeVarInt(s.depth);b.writeVarInt(s.blocks);b.writeVarInt(s.seconds);b.writeVarInt(s.housing);b.writeVarInt(s.floor);b.writeVarInt(s.beds);b.writeUtf(s.folder,22);
            }b.writeVarInt(a.folders.size());for(String folder:a.folders)b.writeUtf(folder,22);
            b.writeVarInt(a.active);b.writeUtf(a.message,250);
        }
        static Panel read(FriendlyByteBuf b){
            BlockPos manager=b.readBlockPos();int count=b.readVarInt();if(count<0||count>BlueprintData.MAX_PER_FACTION)throw new IllegalArgumentException("Too many blueprints");
            List<Summary> plans=new ArrayList<>();for(int i=0;i<count;i++)plans.add(new Summary(b.readUUID(),b.readUtf(28),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(22)));
            int folderCount=b.readVarInt();if(folderCount<0||folderCount>64)throw new IllegalArgumentException("Too many folders");
            List<String> folders=new ArrayList<>();for(int i=0;i<folderCount;i++)folders.add(b.readUtf(22));
            return new Panel(manager,plans,folders,b.readVarInt(),b.readUtf(250));
        }
        static void handle(Panel a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->KingdomOverviewClient.acceptBlueprints(a)));ctx.setPacketHandled(true);}
    }

    /** Return a design only when the requester has BUILD permission at a real Manager. */
    public record Prepare(BlockPos manager,UUID blueprint){
        static void write(Prepare a,FriendlyByteBuf b){b.writeBlockPos(a.manager);b.writeUUID(a.blueprint);}
        static Prepare read(FriendlyByteBuf b){return new Prepare(b.readBlockPos(),b.readUUID());}
        static void handle(Prepare a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            var faction=BlueprintService.managerFaction(p,a.manager);
            if(faction==null){p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Architect] Open your own Kingdom Manager to select a design."));return;}
            var plan=BlueprintData.get(p.getServer()).plans.get(a.blueprint);
            if(plan==null||!plan.owner.equals(faction.id())||!plan.dimension.equals(KingdomData.dim(p.serverLevel()))){
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Architect] That blueprint is not available here."));return;
            }
            sendPreview(p,plan);
        });ctx.setPacketHandled(true);}
    }
    /** World command entrypoint; permissions are verified against the player's current claim. */
    public static boolean prepareAtPlayer(ServerPlayer p,String name){
        var faction=BlueprintService.atPlayer(p);if(faction==null)return false;
        var plan=BlueprintData.get(p.getServer()).named(faction.id(),name);
        if(plan==null||!plan.dimension.equals(KingdomData.dim(p.serverLevel())))return false;
        sendPreview(p,plan);return true;
    }
    private static void sendPreview(ServerPlayer player,BlueprintData.Plan plan){
        var blocks=new ArrayList<BlueprintData.Piece>(plan.blocks);
        // Bounded nearby owned-claim IDs let the hologram flag out-of-claim
        // footprints before clicking; the server always validates again.
        var land=KingdomData.get(player.getServer());
        var dimension=KingdomData.dim(player.serverLevel());
        int originX=player.blockPosition().getX()>>4,originZ=player.blockPosition().getZ()>>4;
        java.util.ArrayList<Long> owned=new java.util.ArrayList<>();
        String identity=(land.kingdoms.containsKey(plan.owner)?"kingdom:":"town:")+plan.owner;
        for(int dz=-9;dz<=9;dz++)for(int dx=-9;dx<=9;dx++){
            int cx=originX+dx,cz=originZ+dz;
            if(identity.equals(land.identity(dimension,cx,cz)))owned.add(net.minecraft.world.level.ChunkPos.asLong(cx,cz));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Preview(plan.id,plan.name,plan.width,plan.height,plan.depth,blocks,owned.stream().mapToLong(Long::longValue).toArray()));
    }
    /** Bounded 4096 block positions + serialized BlockStates. Client does not control these. */
    public record Preview(UUID id,String name,int width,int height,int depth,List<BlueprintData.Piece> pieces,long[] ownedChunks){
        static void write(Preview a,FriendlyByteBuf b){
            b.writeUUID(a.id);b.writeUtf(a.name,28);b.writeVarInt(a.width);b.writeVarInt(a.height);b.writeVarInt(a.depth);
            b.writeVarInt(a.pieces.size());for(var piece:a.pieces){b.writeByte(piece.x);b.writeByte(piece.y);b.writeByte(piece.z);b.writeNbt(piece.state);}
            b.writeVarInt(a.ownedChunks.length);for(long key:a.ownedChunks)b.writeLong(key);
        }
        static Preview read(FriendlyByteBuf b){
            UUID id=b.readUUID();String name=b.readUtf(28);int w=b.readVarInt(),h=b.readVarInt(),d=b.readVarInt(),count=b.readVarInt();
            if(w<1||h<1||d<1||w>20||h>20||d>20||count<1||count>BlueprintData.MAX_BLOCKS)throw new IllegalArgumentException("Invalid architect preview dimensions");
            var pieces=new ArrayList<BlueprintData.Piece>();for(int i=0;i<count;i++){
                int x=b.readUnsignedByte(),y=b.readUnsignedByte(),z=b.readUnsignedByte();var nbt=b.readNbt();
                if(x>=w||y>=h||z>=d||nbt==null)throw new IllegalArgumentException("Invalid architect block data");
                pieces.add(new BlueprintData.Piece(x,y,z,nbt));
            }
            int ownedCount=b.readVarInt();if(ownedCount<0||ownedCount>361)throw new IllegalArgumentException("Invalid claim preview size");
            long[] chunks=new long[ownedCount];for(int i=0;i<ownedCount;i++)chunks[i]=b.readLong();
            return new Preview(id,name,w,h,d,pieces,chunks);
        }
        static void handle(Preview a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldArchitectClient.activate(a)));ctx.setPacketHandled(true);}
    }
    public record PlaceWorld(UUID blueprint,BlockPos anchor,int turns){
        static void write(PlaceWorld a,FriendlyByteBuf b){b.writeUUID(a.blueprint);b.writeBlockPos(a.anchor);b.writeVarInt(a.turns);}
        static PlaceWorld read(FriendlyByteBuf b){return new PlaceWorld(b.readUUID(),b.readBlockPos(),b.readVarInt());}
        static void handle(PlaceWorld a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
            var result=BlueprintService.beginInWorld(p,a.blueprint,a.anchor,a.turns);
            CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Outcome(result.success(),result.message()));
        });ctx.setPacketHandled(true);}
    }
    public record Outcome(boolean success,String message){
        static void write(Outcome a,FriendlyByteBuf b){b.writeBoolean(a.success);b.writeUtf(a.message,250);}
        static Outcome read(FriendlyByteBuf b){return new Outcome(b.readBoolean(),b.readUtf(250));}
        static void handle(Outcome a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldArchitectClient.outcome(a)));ctx.setPacketHandled(true);}
    }
    /** Only activates the selector for a player authorized at this manager. */
    public record SelectRequest(BlockPos manager){
        static void write(SelectRequest a,FriendlyByteBuf b){b.writeBlockPos(a.manager);}
        static SelectRequest read(FriendlyByteBuf b){return new SelectRequest(b.readBlockPos());}
        static void handle(SelectRequest a,Supplier<NetworkEvent.Context> supplier){
            var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
                if(BlueprintService.managerFaction(p,a.manager)==null){p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Architect] BUILD permission and nearby manager required."));return;}
                sendSelector(p);
            });ctx.setPacketHandled(true);
        }
    }
    public record OpenSelector(){
        static void write(OpenSelector a,FriendlyByteBuf b){}
        static OpenSelector read(FriendlyByteBuf b){return new OpenSelector();}
        static void handle(OpenSelector a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldSelectionClient.activate()));ctx.setPacketHandled(true);}
    }
    public static void sendSelector(ServerPlayer p){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new OpenSelector());}
    /** The server repeats all bounds, land and permission checks before copying any blocks. */
    public record SaveSelected(BlockPos a,BlockPos b,String name){
        static void write(SaveSelected a,FriendlyByteBuf b){b.writeBlockPos(a.a);b.writeBlockPos(a.b);b.writeUtf(a.name,28);}
        static SaveSelected read(FriendlyByteBuf b){return new SaveSelected(b.readBlockPos(),b.readBlockPos(),b.readUtf(28));}
        static void handle(SaveSelected a,Supplier<NetworkEvent.Context> supplier){
            var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
                long x=Math.abs((long)a.a.getX()-a.b.getX())+1;
                long y=Math.abs((long)a.a.getY()-a.b.getY())+1;
                long z=Math.abs((long)a.a.getZ()-a.b.getZ())+1;
                BlueprintService.Result result;
                if(x>BlueprintData.MAX_WIDTH||y>BlueprintData.MAX_HEIGHT||z>BlueprintData.MAX_DEPTH||x*y*z>BlueprintData.MAX_VOLUME)
                    result=new BlueprintService.Result(false,"Selection exceeds the 20/side or 4096 block maximum.");
                else{
                    var selection=BlueprintService.selection(p);selection.a=a.a;selection.b=a.b;selection.dimension=KingdomData.dim(p.serverLevel());
                    result=BlueprintService.save(p,a.name);
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new SelectionOutcome(result.success(),result.message()));
            });ctx.setPacketHandled(true);
        }
    }
    public record SelectionOutcome(boolean success,String message){
        static void write(SelectionOutcome a,FriendlyByteBuf b){b.writeBoolean(a.success);b.writeUtf(a.message,250);}
        static SelectionOutcome read(FriendlyByteBuf b){return new SelectionOutcome(b.readBoolean(),b.readUtf(250));}
        static void handle(SelectionOutcome a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldSelectionClient.outcome(a)));ctx.setPacketHandled(true);}
    }
    public static void register(){
        CHANNEL.registerMessage(0,Request.class,Request::write,Request::read,Request::handle);
        CHANNEL.registerMessage(1,Place.class,Place::write,Place::read,Place::handle);
        CHANNEL.registerMessage(2,Panel.class,Panel::write,Panel::read,Panel::handle);
        CHANNEL.registerMessage(3,Prepare.class,Prepare::write,Prepare::read,Prepare::handle);
        CHANNEL.registerMessage(4,Preview.class,Preview::write,Preview::read,Preview::handle);
        CHANNEL.registerMessage(5,PlaceWorld.class,PlaceWorld::write,PlaceWorld::read,PlaceWorld::handle);
        CHANNEL.registerMessage(6,Outcome.class,Outcome::write,Outcome::read,Outcome::handle);
        CHANNEL.registerMessage(7,SelectRequest.class,SelectRequest::write,SelectRequest::read,SelectRequest::handle);
        CHANNEL.registerMessage(8,OpenSelector.class,OpenSelector::write,OpenSelector::read,OpenSelector::handle);
        CHANNEL.registerMessage(9,SaveSelected.class,SaveSelected::write,SaveSelected::read,SaveSelected::handle);
        CHANNEL.registerMessage(10,SelectionOutcome.class,SelectionOutcome::write,SelectionOutcome::read,SelectionOutcome::handle);
        CHANNEL.registerMessage(11,CatalogAction.class,CatalogAction::write,CatalogAction::read,CatalogAction::handle);
    }
    /** Folder mutation is validated server-side; the client only selects from its own library. */
    public record CatalogAction(BlockPos manager,String operation,UUID blueprint,String folder){
        static void write(CatalogAction a,FriendlyByteBuf b){b.writeBlockPos(a.manager);b.writeUtf(a.operation,12);b.writeUUID(a.blueprint);b.writeUtf(a.folder,28);}
        static CatalogAction read(FriendlyByteBuf b){return new CatalogAction(b.readBlockPos(),b.readUtf(12),b.readUUID(),b.readUtf(28));}
        static void handle(CatalogAction a,Supplier<NetworkEvent.Context> supplier){
            var ctx=supplier.get();ServerPlayer p=ctx.getSender();if(p!=null)ctx.enqueueWork(()->{
                var faction=BlueprintService.managerFaction(p,a.manager);
                if(faction==null){send(p,a.manager,"No BUILD permission in your claimed land.");return;}
                BlueprintData data=BlueprintData.get(p.getServer());boolean ok=switch(a.operation){
                    case "newfolder"->data.createFolder(faction.id(),a.folder);
                    case "move"->data.move(faction.id(),a.blueprint,a.folder);
                    default->false;
                };
                send(p,a.manager,ok?(a.operation.equals("move")?"Moved design to ":"Created folder ")+a.folder:
                    "Folder action failed. Use 2-22 letters, numbers, spaces, - or _. Max 16 custom folders.");
            });ctx.setPacketHandled(true);
        }
    }
    private static void send(ServerPlayer p,BlockPos manager,String msg){
        var faction=BlueprintService.managerFaction(p,manager);
        List<Summary> summaries=new ArrayList<>();int jobs=0;
        if(faction!=null){BlueprintData data=BlueprintData.get(p.getServer());jobs=data.active(faction.id());for(var plan:data.owned(faction.id()))
            summaries.add(new Summary(plan.id,plan.name,plan.width,plan.height,plan.depth,plan.blocks.size(),plan.seconds(),
                plan.housingSlots(),plan.housingReport().usableFloor(),plan.housingReport().beds(),plan.folder));}
        else msg="Architect access denied: stand beside your Kingdom Manager with BUILD permission.";
        CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Panel(manager,summaries,faction==null?List.of():BlueprintData.get(p.getServer()).folders(faction.id()),jobs,msg));
    }
}
