package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Every GUI operation is checked against persistent server state; client never assigns ownership. */
public final class TownHallNetwork {
    private TownHallNetwork(){}
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(KingdomsClaims.MODID,"townhall"),()->VERSION,VERSION::equals,VERSION::equals);
    public static void register(){int id=0;
        CHANNEL.registerMessage(id++,Open.class,Open::write,Open::read,Open::handle);
        CHANNEL.registerMessage(id++,Action.class,Action::write,Action::read,Action::handle);
        CHANNEL.registerMessage(id++,Snapshot.class,Snapshot::write,Snapshot::read,Snapshot::handle);
    }
    public record Open(){
        public static void write(Open p,FriendlyByteBuf b){}
        public static Open read(FriendlyByteBuf b){return new Open();}
        public static void handle(Open p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();var player=ctx.getSender();if(player!=null)ctx.enqueueWork(()->open(player));ctx.setPacketHandled(true);}
    }
    public record Action(String type,String value,int number,boolean flag){
        public static void write(Action a,FriendlyByteBuf b){b.writeUtf(a.type,24);b.writeUtf(a.value,64);b.writeVarInt(a.number);b.writeBoolean(a.flag);}
        public static Action read(FriendlyByteBuf b){return new Action(b.readUtf(24),b.readUtf(64),b.readVarInt(),b.readBoolean());}
        public static void handle(Action a,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();var player=ctx.getSender();if(player!=null)ctx.enqueueWork(()->apply(player,a));ctx.setPacketHandled(true);}
    }
    public record Member(String id,String name,int rank,int grants){
        static void write(Member m,FriendlyByteBuf b){b.writeUtf(m.id,36);b.writeUtf(m.name,40);b.writeVarInt(m.rank);b.writeVarInt(m.grants);}
        static Member read(FriendlyByteBuf b){return new Member(b.readUtf(36),b.readUtf(40),b.readVarInt(),b.readVarInt());}
    }
    public record TownInfo(String id,String name,boolean mayor,int chunks,int members,String kingdom){
        static void write(TownInfo t,FriendlyByteBuf b){b.writeUtf(t.id,36);b.writeUtf(t.name,32);b.writeBoolean(t.mayor);b.writeVarInt(t.chunks);b.writeVarInt(t.members);b.writeUtf(t.kingdom,36);}
        static TownInfo read(FriendlyByteBuf b){return new TownInfo(b.readUtf(36),b.readUtf(32),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readUtf(36));}
    }
    public record KingdomInfo(String name,boolean king,int chunks,long treasury,String banner,boolean canRelocate){
        static void write(KingdomInfo k,FriendlyByteBuf b){b.writeUtf(k.name,32);b.writeBoolean(k.king);b.writeVarInt(k.chunks);b.writeVarLong(k.treasury);b.writeUtf(k.banner,100);b.writeBoolean(k.canRelocate);}
        static KingdomInfo read(FriendlyByteBuf b){return new KingdomInfo(b.readUtf(32),b.readBoolean(),b.readVarInt(),b.readVarLong(),b.readUtf(100),b.readBoolean());}
    }
    public record Snapshot(boolean open,boolean kingdomOpen,String message,String selectedId,List<TownInfo> towns,List<Member> members,List<String> onlinePlayers,String banner,String nearbyKingdom,KingdomInfo kingdom,boolean ownsBoth,boolean canMergeOwn,long ownMergeCost){
        public static void write(Snapshot s,FriendlyByteBuf b){
            b.writeBoolean(s.open);b.writeBoolean(s.kingdomOpen);b.writeUtf(s.message,180);b.writeUtf(s.selectedId,36);
            b.writeVarInt(s.towns.size());for(TownInfo t:s.towns)TownInfo.write(t,b);
            b.writeVarInt(s.members.size());for(Member m:s.members)Member.write(m,b);
            b.writeVarInt(s.onlinePlayers.size());for(String name:s.onlinePlayers)b.writeUtf(name,40);
            b.writeUtf(s.banner,100);b.writeUtf(s.nearbyKingdom,40);KingdomInfo.write(s.kingdom,b);b.writeBoolean(s.ownsBoth);b.writeBoolean(s.canMergeOwn);b.writeVarLong(s.ownMergeCost);
        }
        public static Snapshot read(FriendlyByteBuf b){
            boolean open=b.readBoolean(),kingdomOpen=b.readBoolean();String msg=b.readUtf(180),selected=b.readUtf(36);
            int n=b.readVarInt();if(n<0||n>15)throw new IllegalArgumentException("Town list out of range");
            List<TownInfo> ts=new ArrayList<>();for(int i=0;i<n;i++)ts.add(TownInfo.read(b));
            n=b.readVarInt();if(n<0||n>128)throw new IllegalArgumentException("Member list out of range");
            List<Member> ms=new ArrayList<>();for(int i=0;i<n;i++)ms.add(Member.read(b));
            n=b.readVarInt();if(n<0||n>128)throw new IllegalArgumentException("Online list out of range");
            List<String> online=new ArrayList<>();for(int i=0;i<n;i++)online.add(b.readUtf(40));
            return new Snapshot(open,kingdomOpen,msg,selected,ts,ms,online,b.readUtf(100),b.readUtf(40),KingdomInfo.read(b),b.readBoolean(),b.readBoolean(),b.readVarLong());
        }
        public static void handle(Snapshot s,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TownHallClient.accept(s)));ctx.setPacketHandled(true);}
    }
    public static void open(ServerPlayer player){send(player,true,false,"Town Hall ready.");}
    public static void openKingdom(ServerPlayer player){send(player,true,true,"Kingdom management ready.");}
    public static void openKingdomBanner(ServerPlayer player,KingdomData.Kingdom kingdom){
        if(kingdom==null||!kingdom.members.containsKey(player.getUUID())){
            player.displayClientMessage(Component.literal("Only kingdom members can manage this banner."),true);return;
        }
        send(player,true,true,"");
    }
    public static void openBanner(ServerPlayer player,KingdomData.Town town){
        KingdomData data=KingdomData.get(player.getServer());
        if(town==null||!town.members.containsKey(player.getUUID())){player.displayClientMessage(Component.literal("Only town members can manage this banner."),true);return;}
        data.selectTown(player.getUUID(),town.id);send(player,true,false,"");
    }
    private static void send(ServerPlayer player,boolean opening,boolean kingdomOpen,String msg){
        KingdomData d=KingdomData.get(player.getServer());KingdomData.Town selected=d.townOf(player.getUUID());
        List<TownInfo> ts=new ArrayList<>();for(KingdomData.Town t:d.townsOf(player.getUUID())){
            KingdomData.Kingdom k=t.parentKingdom==null?null:d.kingdoms.get(t.parentKingdom);
            ts.add(new TownInfo(t.id.toString(),t.name,t.mayor.equals(player.getUUID()),d.townClaims(t.id),t.members.size(),k==null?"Independent":k.name));
        }
        List<Member> members=new ArrayList<>();String banner="No banner placed";String nearby="";
        if(selected!=null){
            for(var m:selected.members.entrySet()){
                ServerPlayer online=player.getServer().getPlayerList().getPlayer(m.getKey());
                String name=online!=null?online.getGameProfile().getName():player.getServer().getProfileCache().get(m.getKey()).map(g->g.getName()).orElse(m.getKey().toString().substring(0,8));
                if(members.size()<128)members.add(new Member(m.getKey().toString(),name,m.getValue(),selected.grants.getOrDefault(m.getKey(),0)));
            }
            KingdomData.Banner b=d.townBanner(selected.id);
            if(b!=null)banner="At "+b.pos.getX()+", "+b.pos.getY()+", "+b.pos.getZ();
            if(selected.parentKingdom==null){
                for(KingdomData.Kingdom k:d.kingdoms.values())if(KingdomService.bordersTouch(d,k.id,selected.id)){nearby=k.name;break;}
            }
        }
        List<String> onlinePlayers=new ArrayList<>();
        for(ServerPlayer other:player.getServer().getPlayerList().getPlayers())if(onlinePlayers.size()<128)onlinePlayers.add(other.getGameProfile().getName());
        onlinePlayers.sort(String.CASE_INSENSITIVE_ORDER);
        KingdomData.Kingdom owned=d.ofPlayer(player.getUUID());
        KingdomInfo kingdom=new KingdomInfo("",false,0,0,"Not in a kingdom",false);
        if(owned!=null){
            KingdomData.Banner capital=d.capital(owned.id);
            String position=capital==null?"No Kingdom Banner placed":"At "+capital.pos.getX()+", "+capital.pos.getY()+", "+capital.pos.getZ()+" ("+capital.dimension+")";
            kingdom=new KingdomInfo(owned.name,owned.king.equals(player.getUUID()),d.kingdomClaims(owned.id),owned.treasuryIron,position,owned.allowed(player.getUUID(),KingdomData.BANNER));
        }
        boolean ownsBoth=selected!=null&&selected.parentKingdom==null&&selected.mayor.equals(player.getUUID())
            &&owned!=null&&owned.king.equals(player.getUUID());
        boolean canMergeOwn=ownsBoth&&KingdomService.bordersTouch(d,owned.id,selected.id);
        long ownMergeCost=canMergeOwn?KingdomService.annexCost(d,owned.id,selected.id):0L;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Snapshot(opening,kingdomOpen,msg,selected==null?"":selected.id.toString(),ts,members,onlinePlayers,banner,nearby,kingdom,ownsBoth,canMergeOwn,ownMergeCost));
    }
    private static boolean valid(String s){return s.matches("[a-zA-Z0-9_-]{3,24}");}
    private static UUID parse(String s){try{return UUID.fromString(s);}catch(IllegalArgumentException e){return null;}}
    private static void apply(ServerPlayer p,Action a){
        KingdomData d=KingdomData.get(p.getServer());KingdomData.Town t=d.townOf(p.getUUID());
        String msg="Action unavailable.";
        try{
            switch(a.type){
                case "next"->{var next=d.nextTown(p.getUUID());msg=next==null?"No towns yet.":"Selected "+next.name;}
                case "previous"->{var towns=d.townsOf(p.getUUID());
                    if(towns.isEmpty())msg="No towns yet.";
                    else {int index=towns.indexOf(d.townOf(p.getUUID()));var old=towns.get(Math.floorMod(index-1,towns.size()));d.selectTown(p.getUUID(),old.id);msg="Selected "+old.name;}
                }
                case "select"->{UUID id=parse(a.value);msg=id!=null&&d.selectTown(p.getUUID(),id)?"Active town changed.":"You cannot select that town.";}
                case "create"->{
                    if(!valid(a.value)||d.townByName(a.value)!=null)msg="Town name invalid or already taken (3-24 letters/numbers).";
                    else if(d.ownedTowns(p.getUUID())>=15||d.memberTowns(p.getUUID())>=15)msg="Maximum of 15 towns reached.";
                    else {var newTown=new KingdomData.Town(UUID.randomUUID(),a.value,p.getUUID());d.towns.put(newTown.id,newTown);d.selectTown(p.getUUID(),newTown.id);d.setDirty();msg="Founded "+newTown.name+"! Craft and place its Town Banner.";}
                }
                case "accept"->{
                    UUID invitation=d.townInvites.get(p.getUUID());var invited=invitation==null?null:d.towns.get(invitation);
                    if(invited==null)msg="No pending invitation.";
                    else if(d.memberTowns(p.getUUID())>=15)msg="Already in 15 towns.";
                    else {d.townInvites.remove(p.getUUID());invited.members.put(p.getUUID(),KingdomData.MEMBER);d.selectTown(p.getUUID(),invited.id);d.setDirty();msg="Joined "+invited.name;}
                }
                case "rename"->{
                    if(t==null||!t.mayor.equals(p.getUUID()))msg="Mayor permission required.";
                    else if(!valid(a.value)||(d.townByName(a.value)!=null&&d.townByName(a.value)!=t))msg="Invalid or duplicate town name.";
                    else{t.name=a.value;d.setDirty();msg="Renamed town to "+t.name;}
                }
                case "invite"->{
                    if(t==null||t.rank(p.getUUID())<KingdomData.OFFICER)msg="Mayor or officer required.";
                    else{ServerPlayer target=p.getServer().getPlayerList().getPlayerByName(a.value);
                        if(target==null)msg="Player must be online to receive an invitation.";
                        else if(t.members.containsKey(target.getUUID()))msg="Already a town member.";
                        else if(d.memberTowns(target.getUUID())>=15)msg="Player already belongs to 15 towns.";
                        else {d.townInvites.put(target.getUUID(),t.id);d.setDirty();target.sendSystemMessage(Component.literal("Town invite: "+t.name+". Open Town Hall or type /town accept."));msg="Invited "+a.value;}
                    }
                }
                case "kick"->{
                    UUID who=parse(a.value);
                    if(t==null||who==null||t.rank(p.getUUID())<KingdomData.OFFICER||who.equals(t.mayor)||t.rank(who)>=t.rank(p.getUUID()))msg="Cannot remove this member.";
                    else if(t.members.remove(who)==null)msg="Not a town member.";
                    else{t.grants.remove(who);d.selectedTowns.remove(who,t.id);d.setDirty();msg="Member removed.";}
                }
                case "transfer"->{
                    UUID who=parse(a.value);
                    if(t==null||!t.mayor.equals(p.getUUID())||who==null||who.equals(p.getUUID())||!t.members.containsKey(who))msg="Mayor can transfer only to another current member.";
                    else {t.members.put(p.getUUID(),KingdomData.OFFICER);t.mayor=who;t.members.put(who,KingdomData.KING);d.setDirty();msg="Mayorship transferred.";}
                }
                case "permit"->{
                    UUID who=parse(a.value);int mask=a.number;
                    if(t==null||!t.mayor.equals(p.getUUID())||who==null||!t.members.containsKey(who)||who.equals(t.mayor)||Integer.bitCount(mask)!=1||(mask&~63)!=0)msg="Invalid permission change.";
                    else {int old=t.grants.getOrDefault(who,0);t.grants.put(who,a.flag?(old|mask):(old&~mask));d.setDirty();msg="Member permissions updated.";}
                }
                case "leave"->{
                    if(t==null)msg="No active town.";
                    else if(t.mayor.equals(p.getUUID()))msg="Transfer mayorship or disband before leaving.";
                    else {t.members.remove(p.getUUID());t.grants.remove(p.getUUID());d.selectedTowns.remove(p.getUUID(),t.id);d.setDirty();msg="Left town "+t.name;}
                }
                case "claim"->{msg=t==null?"No active town.":KingdomService.claimAt(p,p.serverLevel(),p.blockPosition().getX()>>4,p.blockPosition().getZ()>>4,true);}
                case "mergeOwn"->{
                    KingdomData.Kingdom kingdom=d.ofPlayer(p.getUUID());
                    if(t==null||kingdom==null||!t.mayor.equals(p.getUUID())||!kingdom.king.equals(p.getUUID()))
                        msg="Only a player who is both Mayor and King can merge directly.";
                    else msg=KingdomService.annex(p.getServer(),d,kingdom,t);
                }
                case "approve"->{
                    KingdomData.Kingdom k=d.byName(a.value);
                    if(t==null||!t.mayor.equals(p.getUUID())||t.parentKingdom!=null||k==null||!KingdomService.bordersTouch(d,k.id,t.id))msg="Requires independent town, touching kingdom, and mayor approval.";
                    else {d.townMergerApprovals.put(t.id,k.id);d.setDirty();msg="Approved "+k.name+". Kingdom must pay "+KingdomService.annexCost(d,k.id,t.id)+" iron.";}
                }
                case "kingdomRelocate"->{
                    KingdomData.Kingdom kingdom=d.ofPlayer(p.getUUID());
                    KingdomData.Banner capital=kingdom==null?null:d.capital(kingdom.id);
                    if(kingdom==null||!kingdom.allowed(p.getUUID(),KingdomData.BANNER))msg="Kingdom banner permission required.";
                    else if(capital==null)msg="Place your Kingdom Banner first.";
                    else if(!capital.dimension.equals(KingdomData.dim(p.serverLevel())))msg="Travel to your capital's dimension first.";
                    else {
                        HitResult hit=p.pick(6.0D,0.0F,false);
                        if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)msg="Look at the block where the new Kingdom Banner should stand.";
                        else msg=KingdomService.relocate(p,p.serverLevel(),capital.pos,block.getBlockPos().relative(block.getDirection()));
                    }
                }
                case "relocate"->{
                    if(t==null||!t.allowed(p.getUUID(),KingdomData.BANNER))msg="Banner permission required.";
                    else {KingdomData.Banner b=d.townBanner(t.id);
                        if(b==null)msg="Place this town's banner first.";
                        else if(!b.dimension.equals(KingdomData.dim(p.serverLevel())))msg="Travel to the banner's dimension first.";
                        else{HitResult hit=p.pick(6.0D,0.0F,false);
                            if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)msg="Look at the block where the new banner should stand.";
                            else msg=KingdomService.relocate(p,p.serverLevel(),b.pos,block.getBlockPos().relative(block.getDirection()));
                        }
                    }
                }
                case "disband"->{msg=t==null||!t.mayor.equals(p.getUUID())?"Only the mayor may disband.":KingdomService.disbandTown(p.getServer(),t);}
            }
        }catch(Exception ex){msg="Action rejected: "+ex.getClass().getSimpleName();}
        p.sendSystemMessage(Component.literal("[Town Hall] "+msg));send(p,false,false,msg);
    }
}
