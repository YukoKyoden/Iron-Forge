package com.kingdomsclaims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Commands are an alternative to the K-key territory preview and necessary for server tests. */
public final class KingdomCommands {
    private KingdomCommands(){}
    private static int ok(CommandContext<CommandSourceStack> c,String msg){c.getSource().sendSuccess(()->Component.literal(msg),false);return 1;}
    private static int err(CommandContext<CommandSourceStack> c,String msg){c.getSource().sendFailure(Component.literal(msg));return 0;}
    private static int reply(CommandContext<CommandSourceStack> c,String msg){return msg.startsWith("Claimed ")||msg.startsWith("Released ")||msg.startsWith("Relocated ")||msg.startsWith("Annexed ")||msg.startsWith("Disbanded")||msg.startsWith("Capital ")||msg.startsWith("Town ")||msg.startsWith("Expansion ")?ok(c,msg):err(c,msg);}
    private static KingdomData data(CommandContext<CommandSourceStack> c){return KingdomData.get(c.getSource().getServer());}
    private static ServerPlayer player(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return c.getSource().getPlayerOrException();}
    private static KingdomData.Kingdom own(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return data(c).ofPlayer(player(c).getUUID());}
    private static boolean valid(String name){return name.matches("[a-zA-Z0-9_-]{3,24}");}
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        CommandDispatcher<CommandSourceStack> d=e.getDispatcher();
        d.register(Commands.literal("planner").executes(c->{DistrictNetwork.openHere(player(c));return 1;}));
        d.register(Commands.literal("kingdom")
            .then(Commands.literal("planner").executes(c->{DistrictNetwork.openHere(player(c));return 1;}))
            .then(Commands.literal("help").executes(c->ok(c,"/kingdom create/list/info/members/invite/accept/decline/leave/kick/officer/member/transfer/rename/disband/claim/unclaim/here/grid/costs/expansion/banner/permit/treasury/annex | K = map")))
            .then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.word()).executes(KingdomCommands::create)))
            .then(Commands.literal("list").executes(KingdomCommands::list))
            .then(Commands.literal("info").executes(KingdomCommands::info))
            .then(Commands.literal("menu").executes(c->{TownHallNetwork.openKingdom(player(c));return 1;}))
            .then(Commands.literal("members").executes(KingdomCommands::members))
            .then(Commands.literal("banners").executes(KingdomCommands::banners))
            .then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(KingdomCommands::invite)))
            .then(Commands.literal("accept").executes(KingdomCommands::accept))
            .then(Commands.literal("decline").executes(c->{boolean result=data(c).invites.remove(player(c).getUUID())!=null;data(c).setDirty();return result?ok(c,"Invitation declined."):err(c,"No invitation.");}))
            .then(Commands.literal("leave").executes(KingdomCommands::leave))
            .then(Commands.literal("disband").executes(KingdomCommands::disband))
            .then(Commands.literal("kick").then(Commands.argument("player",EntityArgument.player()).executes(c->rank(c,"kick"))))
            .then(Commands.literal("officer").then(Commands.argument("player",EntityArgument.player()).executes(c->rank(c,"officer"))))
            .then(Commands.literal("member").then(Commands.argument("player",EntityArgument.player()).executes(c->rank(c,"member"))))
            .then(Commands.literal("transfer").then(Commands.argument("player",EntityArgument.player()).executes(c->rank(c,"transfer"))))
            .then(Commands.literal("rename").then(Commands.argument("name",StringArgumentType.word()).executes(KingdomCommands::rename)))
            .then(Commands.literal("claim").executes(c->reply(c,KingdomService.claim(player(c),player(c).serverLevel()))))
            .then(Commands.literal("unclaim").executes(c->reply(c,KingdomService.unclaim(player(c),player(c).serverLevel()))))
            .then(Commands.literal("here").executes(KingdomCommands::here))
            .then(Commands.literal("grid").executes(KingdomCommands::grid))
            .then(Commands.literal("costs").executes(KingdomCommands::costs))
            .then(Commands.literal("expansion").executes(KingdomCommands::expansion))
            .then(Commands.literal("permit").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("permission",StringArgumentType.word())
                .then(Commands.argument("allowed",BoolArgumentType.bool()).executes(KingdomCommands::permit)))))
            .then(Commands.literal("treasury")
                .then(Commands.literal("balance").executes(KingdomCommands::treasury))
                .then(Commands.literal("deposit").then(Commands.argument("iron",IntegerArgumentType.integer(1,2304)).executes(KingdomCommands::deposit)))
                .then(Commands.literal("depositwheat").then(Commands.argument("wheat",IntegerArgumentType.integer(1,2304)).executes(KingdomCommands::depositWheat))))
            .then(Commands.literal("annex").then(Commands.argument("town",StringArgumentType.word()).executes(KingdomCommands::annex)))
            .then(Commands.literal("banner")
                .then(Commands.literal("remove").then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(KingdomCommands::removeBanner)))
                .then(Commands.literal("relocate").then(Commands.argument("pos",BlockPosArgument.blockPos())
                    .then(Commands.argument("destination",BlockPosArgument.blockPos()).executes(KingdomCommands::relocate)))))
            .then(Commands.literal("admin").requires(s->s.hasPermission(2))
                .then(Commands.literal("kit").executes(KingdomCommands::kit))
                .then(Commands.literal("captureTown").then(Commands.argument("pos",BlockPosArgument.blockPos())
                    .then(Commands.argument("kingdom",StringArgumentType.word()).executes(KingdomCommands::captureTown))))
                .then(Commands.literal("collapse").then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(KingdomCommands::collapse)))
                .then(Commands.literal("siegePermit").then(Commands.argument("kingdom",StringArgumentType.word())
                    .then(Commands.argument("pos",BlockPosArgument.blockPos())
                        .then(Commands.argument("strength",IntegerArgumentType.integer(0)).executes(KingdomCommands::siegePermit))))))
            .executes(c->ok(c,"Kingdoms & Claims v0.2.3. Use /kingdom help.")));
        TownCommands.register(d);
    }
    private static int create(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        KingdomData d=data(c);ServerPlayer p=player(c);String name=StringArgumentType.getString(c,"name");
        if(!valid(name)||d.byName(name)!=null||d.ofPlayer(p.getUUID())!=null)return err(c,"Invalid/taken name or already in a kingdom.");
        var k=new KingdomData.Kingdom(UUID.randomUUID(),name,p.getUUID());k.treasuryIron=d.restartCredit.getOrDefault(p.getUUID(),0L);d.restartCredit.remove(p.getUUID());
        d.kingdoms.put(k.id,k);d.setDirty();return ok(c,"Kingdom "+name+" created. Place Capital Banner to claim your starting 5x5. Treasury: "+k.treasuryIron+" iron.");
    }
    private static int list(CommandContext<CommandSourceStack> c){StringJoiner j=new StringJoiner(", ");for(var k:data(c).kingdoms.values())j.add(k.name);return ok(c,"Kingdoms: "+j);}
    private static int info(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null)return err(c,"Not in a kingdom.");
        return ok(c,k.name+" | Rank "+k.rank(player(c).getUUID())+" | Members "+k.members.size()+" | Chunks "+data(c).kingdomClaims(k.id)+" | Next claim "+KingdomService.ironCost(data(c),k.id)+" iron | Stored iron "+EconomyService.balance(c.getSource().getServer(),k.id,"IRON"));
    }
    private static int members(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null)return err(c,"Not in a kingdom.");StringJoiner j=new StringJoiner(", ");
        for(var e:k.members.entrySet()){ServerPlayer p=c.getSource().getServer().getPlayerList().getPlayer(e.getKey());j.add((p==null?e.getKey().toString().substring(0,8):p.getGameProfile().getName())+" ("+e.getValue()+")");}return ok(c,j.toString());
    }
    private static int banners(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null)return err(c,"Not in a kingdom.");int n=0;
        for(var b:data(c).banners.values())if(k.id.equals(b.kingdom)){n++;c.getSource().sendSuccess(()->Component.literal((b.capital?"CAPITAL":"LEGACY EXTRA — remove via /kingdom banner remove")+" "+b.pos+" | "+b.dimension),false);}
        return n==0?ok(c,"No kingdom banners."):1;
    }
    private static int invite(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);ServerPlayer actor=player(c),target=EntityArgument.getPlayer(c,"player");
        if(k==null||k.rank(actor.getUUID())<KingdomData.OFFICER)return err(c,"Officer or King required.");
        if(data(c).ofPlayer(target.getUUID())!=null)return err(c,"Player already in a kingdom.");
        data(c).invites.put(target.getUUID(),k.id);data(c).setDirty();target.sendSystemMessage(Component.literal("Invite from "+k.name+": /kingdom accept."));return ok(c,"Invited player.");
    }
    private static int accept(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        KingdomData d=data(c);ServerPlayer p=player(c);if(d.ofPlayer(p.getUUID())!=null)return err(c,"Already in a kingdom.");
        UUID id=d.invites.remove(p.getUUID());var k=d.kingdoms.get(id);if(k==null)return err(c,"No pending valid invitation.");
        k.members.put(p.getUUID(),KingdomData.MEMBER);d.setDirty();return ok(c,"Joined "+k.name+". Ask King for building permissions.");
    }
    private static int leave(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null)return err(c,"Not in a kingdom.");if(k.king.equals(player(c).getUUID()))return err(c,"King must transfer or disband.");
        k.members.remove(player(c).getUUID());k.grants.remove(player(c).getUUID());data(c).setDirty();return ok(c,"Left kingdom.");
    }
    private static int disband(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null||!k.king.equals(player(c).getUUID()))return err(c,"King required.");return ok(c,KingdomService.disband(c.getSource().getServer(),k));
    }
    private static int rank(CommandContext<CommandSourceStack> c,String action)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);ServerPlayer actor=player(c),target=EntityArgument.getPlayer(c,"player");
        if(k==null||k.rank(actor.getUUID())!=KingdomData.KING||k.rank(target.getUUID())==0)return err(c,"King and target membership required.");
        if(target.getUUID().equals(actor.getUUID()))return err(c,"Cannot target yourself.");
        switch(action){
            case "transfer"->{k.members.put(actor.getUUID(),KingdomData.OFFICER);k.king=target.getUUID();k.members.put(target.getUUID(),KingdomData.KING);}
            case "officer"->k.members.put(target.getUUID(),KingdomData.OFFICER);
            case "member"->k.members.put(target.getUUID(),KingdomData.MEMBER);
            case "kick"->{k.members.remove(target.getUUID());k.grants.remove(target.getUUID());}
        }data(c).setDirty();return ok(c,"Member changed: "+action);
    }
    private static int rename(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);String name=StringArgumentType.getString(c,"name");if(k==null||!k.king.equals(player(c).getUUID())||!valid(name)||data(c).byName(name)!=null)return err(c,"King required; use an unused valid name.");
        k.name=name;data(c).setDirty();return ok(c,"Kingdom renamed "+name);
    }
    private static int here(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(c);String dim=KingdomData.dim(p.serverLevel());int cx=p.blockPosition().getX()>>4,cz=p.blockPosition().getZ()>>4;
        return ok(c,"Chunk "+cx+","+cz+": "+data(c).ownerName(dim,cx,cz));
    }
    private static int grid(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(c);KingdomData d=data(c);String dim=KingdomData.dim(p.serverLevel());int x=p.blockPosition().getX()>>4,z=p.blockPosition().getZ()>>4;
        c.getSource().sendSuccess(()->Component.literal("9x9 around you: K kingdom, T town, . wilderness, Y valid your kingdom claim"),false);
        for(int dz=-4;dz<=4;dz++){StringBuilder line=new StringBuilder();for(int dx=-4;dx<=4;dx++){
            int cx=x+dx,cz=z+dz;KingdomData.Claim claim=d.claimAt(dim,cx,cz);
            if(dx==0&&dz==0)line.append('@');else if(claim!=null)line.append(d.townFor(claim)!=null?'T':'K');
            else{var k=d.ofPlayer(p.getUUID());line.append(k!=null&&KingdomService.claimable(d,k.id,dim,cx,cz,false)?'Y':'.');}line.append(' ');
        }c.getSource().sendSuccess(()->Component.literal(line.toString()),false);}return 1;
    }
    private static int costs(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);return ok(c,"Kingdom additional chunk: 16 cobble + "+(k==null?4:KingdomService.ironCost(data(c),k.id))+" iron (rises per claim). Town: 16 cobble + fixed 20 iron. Town cap 81 chunks. K = visual claims.");
    }
    private static int expansion(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);if(k==null)return err(c,"Not in kingdom.");return ok(c,"Kingdom "+k.name+": "+data(c).kingdomClaims(k.id)+" owned chunks. Unlimited connected expansion. Next iron "+KingdomService.ironCost(data(c),k.id));
    }
    public static int permission(String p){return switch(p.toLowerCase(Locale.ROOT)){case "build"->KingdomData.BUILD;case "break"->KingdomData.BREAK;case "storage"->KingdomData.STORAGE;case "claim"->KingdomData.CLAIM;case "banner"->KingdomData.BANNER;case "gate"->KingdomData.GATE;default->0;};}
    private static int permit(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);ServerPlayer p=player(c),target=EntityArgument.getPlayer(c,"player");int flag=permission(StringArgumentType.getString(c,"permission"));boolean enabled=BoolArgumentType.getBool(c,"allowed");
        if(k==null||!k.king.equals(p.getUUID())||k.rank(target.getUUID())==0||flag==0)return err(c,"King must grant permission to a kingdom member. Try build/break/storage/claim/banner/gate.");
        int bits=k.grants.getOrDefault(target.getUUID(),0);k.grants.put(target.getUUID(),enabled?bits|flag:bits&~flag);data(c).setDirty();return ok(c,"Kingdom permission updated.");
    }
    private static int treasury(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);return k==null?err(c,"Not in kingdom."):ok(c,"Physical warehouse supplies: "+EconomyService.balance(c.getSource().getServer(),k.id,"IRON")+" iron, "+EconomyService.balance(c.getSource().getServer(),k.id,"WHEAT")+" wheat. 5min upkeep: "+EconomyService.ironDue(c.getSource().getServer(),k.id)+" iron + "+EconomyService.wheatDue(c.getSource().getServer(),k.id)+" wheat.");
    }
    private static int deposit(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);int count=IntegerArgumentType.getInteger(c,"iron");if(k==null||!k.allowed(player(c).getUUID(),KingdomData.CLAIM))return err(c,"Kingdom claim permission required.");
        String message=EconomyService.deposit(player(c),k.id,"IRON",count);return message.startsWith("Deposited")?ok(c,message):err(c,message);
    }
    private static int depositWheat(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);int count=IntegerArgumentType.getInteger(c,"wheat");if(k==null||!k.allowed(player(c).getUUID(),KingdomData.CLAIM))return err(c,"Kingdom claim permission required.");
        if(count>64)return err(c,"Deposit wheat in 64-item batches at your War Table.");
        String result=EconomyService.deposit(player(c),k.id,"WHEAT",count);
        return result.startsWith("Deposited")?ok(c,result):err(c,result);
    }
    private static int annex(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=own(c);var t=data(c).townByName(StringArgumentType.getString(c,"town"));
        if(k==null||!k.king.equals(player(c).getUUID())||t==null)return err(c,"Only King may annex an existing town.");
        return reply(c,KingdomService.annex(c.getSource().getServer(),data(c),k,t));
    }
    private static int removeBanner(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(c);BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"pos");var b=data(c).bannerAt(KingdomData.dim(p.serverLevel()),pos);
        if(b==null)return err(c,"Banner not found.");if(b.capital)return err(c,"Cannot remove capital using this command; siege defeat or disband only.");
        if(!p.hasPermissions(2)){
            boolean ownTown=b.town!=null&&data(c).towns.containsKey(b.town)&&data(c).towns.get(b.town).allowed(p.getUUID(),KingdomData.BANNER);
            boolean ownK=b.kingdom!=null&&data(c).kingdoms.containsKey(b.kingdom)&&data(c).kingdoms.get(b.kingdom).allowed(p.getUUID(),KingdomData.BANNER);
            if(!ownTown&&!ownK)return err(c,"Banner permission required.");
        }return reply(c,KingdomService.removeBanner(c.getSource().getServer(),b));
    }
    private static int relocate(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(c);BlockPos from=BlockPosArgument.getLoadedBlockPos(c,"pos"),to=BlockPosArgument.getLoadedBlockPos(c,"destination");
        return reply(c,KingdomService.relocate(p,p.serverLevel(),from,to));
    }
    private static int kit(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        ServerPlayer p=player(c);p.addItem(new net.minecraft.world.item.ItemStack(KingdomsClaims.KINGDOM_BANNER_ITEM.get(),3));
        p.addItem(new net.minecraft.world.item.ItemStack(KingdomsClaims.TOWN_BANNER_ITEM.get(),3));
        p.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        p.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT,64));
        return ok(c,"Banner test kit granted.");
    }
    private static int captureTown(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"pos");ServerLevel level=c.getSource().getLevel();var b=data(c).bannerAt(KingdomData.dim(level),pos);
        var k=data(c).byName(StringArgumentType.getString(c,"kingdom"));if(b==null||k==null)return err(c,"Need town banner and target kingdom.");
        return reply(c,KingdomService.captureTown(c.getSource().getServer(),b,k.id));
    }
    private static int collapse(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"pos");ServerLevel level=c.getSource().getLevel();var b=data(c).bannerAt(KingdomData.dim(level),pos);
        return b==null?err(c,"Banner not found."):reply(c,KingdomService.collapseKingdomBanner(c.getSource().getServer(),b));
    }
    private static int siegePermit(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var k=data(c).byName(StringArgumentType.getString(c,"kingdom"));if(k==null)return err(c,"Attacking kingdom not found.");
        BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"pos");int strength=IntegerArgumentType.getInteger(c,"strength");
        SiegeAccess.updateFromArmy(c.getSource().getLevel(),k.id,pos,strength);
        return ok(c,"Siege permit updated for "+strength+" troops (5s TTL; 10 required).");
    }
}
