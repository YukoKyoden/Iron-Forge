package com.kingdomsclaims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Each player may participate in multiple towns and select one active town for commands. */
public final class TownCommands {
    private TownCommands(){}
    private static int ok(CommandContext<CommandSourceStack> c,String s){c.getSource().sendSuccess(()->Component.literal(s),false);return 1;}
    private static int err(CommandContext<CommandSourceStack> c,String s){c.getSource().sendFailure(Component.literal(s));return 0;}
    private static ServerPlayer p(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return c.getSource().getPlayerOrException();}
    private static KingdomData d(CommandContext<CommandSourceStack> c){return KingdomData.get(c.getSource().getServer());}
    private static KingdomData.Town t(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return d(c).townOf(p(c).getUUID());}
    public static void register(CommandDispatcher<CommandSourceStack> d){
        d.register(Commands.literal("town")
            .then(Commands.literal("help").executes(c->ok(c,"/town create <name>, my, select <name>, next, info, list, rename <name>, disband confirm, transfer <player>, claim, invite <player>, accept, leave, permit, annex approve, merge (if you are King and Mayor)")))
            .then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.word()).executes(TownCommands::create)))
            .then(Commands.literal("menu").executes(c->{TownHallNetwork.open(p(c));return 1;}))
            .then(Commands.literal("list").executes(TownCommands::list))
            .then(Commands.literal("my").executes(TownCommands::my))
            .then(Commands.literal("select").then(Commands.argument("name",StringArgumentType.word()).executes(TownCommands::select)))
            .then(Commands.literal("next").executes(TownCommands::next))
            .then(Commands.literal("rename").then(Commands.argument("name",StringArgumentType.word()).executes(TownCommands::rename)))
            .then(Commands.literal("disband").then(Commands.literal("confirm").executes(TownCommands::disband)))
            .then(Commands.literal("transfer").then(Commands.argument("player",EntityArgument.player()).executes(TownCommands::transfer)))
            .then(Commands.literal("info").executes(TownCommands::info))
            .then(Commands.literal("claim").executes(c->{String result=KingdomService.claimAt(p(c),p(c).serverLevel(),p(c).blockPosition().getX()>>4,p(c).blockPosition().getZ()>>4,true);return result.startsWith("Claimed ")?ok(c,result):err(c,result);} ))
            .then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(TownCommands::invite)))
            .then(Commands.literal("accept").executes(TownCommands::accept))
            .then(Commands.literal("leave").executes(TownCommands::leave))
            .then(Commands.literal("permit").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("permission",StringArgumentType.word())
                .then(Commands.argument("allowed",BoolArgumentType.bool()).executes(TownCommands::permit)))))
            .then(Commands.literal("merge").executes(TownCommands::merge))
            .then(Commands.literal("annex").then(Commands.literal("approve").then(Commands.argument("kingdom",StringArgumentType.word()).executes(TownCommands::approve))))
            .executes(c->ok(c,"Town commands: /town help")));
    }
    private static int create(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        String name=StringArgumentType.getString(c,"name");
        if(!name.matches("[a-zA-Z0-9_-]{3,24}")||d(c).townByName(name)!=null)return err(c,"Town name is taken or invalid (3-24 letters, numbers, _ or -).");
        if(d(c).ownedTowns(p(c).getUUID())>=KingdomData.MAX_TOWNS_PER_PLAYER)return err(c,"You already own the maximum of 15 towns.");
        if(d(c).memberTowns(p(c).getUUID())>=KingdomData.MAX_TOWNS_PER_PLAYER)return err(c,"You belong to the maximum of 15 towns.");
        KingdomData.Town town=new KingdomData.Town(UUID.randomUUID(),name,p(c).getUUID());d(c).towns.put(town.id,town);
        d(c).selectTown(p(c).getUUID(),town.id);d(c).setDirty();
        return ok(c,"Founded "+name+" (active town). Place its Town Banner on unclaimed land. Use /town select <name> to switch.");
    }
    private static int list(CommandContext<CommandSourceStack> c){StringJoiner j=new StringJoiner(", ");for(var t:d(c).towns.values())j.add(t.name);return ok(c,"Towns: "+j);}
    private static int my(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var towns=d(c).townsOf(p(c).getUUID());var active=t(c);
        if(towns.isEmpty())return err(c,"You belong to no towns. /town create <name> to found one.");
        StringJoiner names=new StringJoiner(", ");for(var town:towns)names.add(town.name+(town==active?" [ACTIVE]":"")+(town.mayor.equals(p(c).getUUID())?" [MAYOR]":""));
        return ok(c,"Your towns ("+towns.size()+"/15): "+names+". Switch: /town select <name>");
    }
    private static int select(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=d(c).townByName(StringArgumentType.getString(c,"name"));
        if(town==null||!d(c).selectTown(p(c).getUUID(),town.id))return err(c,"Town not found or you're not a member.");
        return ok(c,"Active town set to "+town.name+". /town commands and K-key Town mode now target it.");
    }
    private static int next(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=d(c).nextTown(p(c).getUUID());return town==null?err(c,"You belong to no towns."):ok(c,"Active town: "+town.name);
    }
    private static int rename(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);String name=StringArgumentType.getString(c,"name");
        if(town==null||!town.mayor.equals(p(c).getUUID()))return err(c,"Only the active town's Mayor can rename it.");
        if(!name.matches("[a-zA-Z0-9_-]{3,24}")||(d(c).townByName(name)!=null&&d(c).townByName(name)!=town))return err(c,"Invalid or taken name.");
        town.name=name;d(c).setDirty();return ok(c,"Town renamed to "+name+".");
    }
    private static int disband(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);if(town==null||!town.mayor.equals(p(c).getUUID()))return err(c,"Only the active town's Mayor can disband it.");
        return ok(c,KingdomService.disbandTown(c.getSource().getServer(),town));
    }
    private static int transfer(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);var next=EntityArgument.getPlayer(c,"player");var current=p(c);
        if(town==null||!town.mayor.equals(current.getUUID()))return err(c,"Only the active town's Mayor can transfer leadership.");
        if(next.getUUID().equals(current.getUUID())||!town.members.containsKey(next.getUUID()))return err(c,"New mayor must be a different existing town member.");
        town.mayor=next.getUUID();town.members.put(current.getUUID(),KingdomData.OFFICER);
        town.members.put(next.getUUID(),KingdomData.KING);d(c).setDirty();
        return ok(c,"Mayorship of "+town.name+" transferred to "+next.getGameProfile().getName()+".");
    }
    private static int info(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);if(town==null)return err(c,"Not in a town.");
        var k=town.parentKingdom==null?null:d(c).kingdoms.get(town.parentKingdom);
        return ok(c,"Town "+town.name+" | "+d(c).townClaims(town.id)+"/81 chunks | 20 iron/chunk | Members "+town.members.size()+" | Kingdom: "+(k==null?"Independent":k.name));
    }
    private static int invite(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);ServerPlayer target=EntityArgument.getPlayer(c,"player");if(town==null||town.rank(p(c).getUUID())<KingdomData.OFFICER)return err(c,"Town Mayor or officer required.");
        if(town.members.containsKey(target.getUUID()))return err(c,"Target is already in this town.");
        if(d(c).memberTowns(target.getUUID())>=KingdomData.MAX_TOWNS_PER_PLAYER)return err(c,"Target is already in 15 towns.");
        d(c).townInvites.put(target.getUUID(),town.id);d(c).setDirty();target.sendSystemMessage(Component.literal("Invitation to "+town.name+"; type /town accept."));return ok(c,"Town invitation sent.");
    }
    private static int accept(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(d(c).memberTowns(p(c).getUUID())>=KingdomData.MAX_TOWNS_PER_PLAYER)return err(c,"Already belong to 15 towns.");
        UUID id=d(c).townInvites.remove(p(c).getUUID());var town=id==null?null:d(c).towns.get(id);
        if(town==null)return err(c,"No pending town invitation.");
        if(town.members.containsKey(p(c).getUUID()))return err(c,"Already in that town.");
        town.members.put(p(c).getUUID(),KingdomData.MEMBER);
        d(c).selectTown(p(c).getUUID(),town.id);d(c).setDirty();return ok(c,"Joined and selected town "+town.name);
    }
    private static int leave(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);if(town==null)return err(c,"Not in town.");if(town.mayor.equals(p(c).getUUID()))return err(c,"Mayor cannot leave. Use /town transfer <player> or /town disband confirm.");
        town.members.remove(p(c).getUUID());town.grants.remove(p(c).getUUID());
        d(c).selectedTowns.remove(p(c).getUUID());d(c).setDirty();return ok(c,"Left town "+town.name+". Select another using /town my.");
    }
    private static int permit(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);ServerPlayer target=EntityArgument.getPlayer(c,"player");int flag=KingdomCommands.permission(StringArgumentType.getString(c,"permission"));boolean enabled=BoolArgumentType.getBool(c,"allowed");
        if(town==null||!town.mayor.equals(p(c).getUUID())||town.rank(target.getUUID())==0||flag==0)return err(c,"Mayor may set permissions for town members.");
        int bits=town.grants.getOrDefault(target.getUUID(),0);town.grants.put(target.getUUID(),enabled?bits|flag:bits&~flag);d(c).setDirty();return ok(c,"Town permission updated.");
    }
    /** No self-approval: the King already leads this independent town. */
    private static int merge(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);var kingdom=d(c).ofPlayer(p(c).getUUID());
        if(town==null||kingdom==null||!town.mayor.equals(p(c).getUUID())||!kingdom.king.equals(p(c).getUUID()))
            return err(c,"You must be both the town Mayor and Kingdom King.");
        String result=KingdomService.annex(c.getSource().getServer(),d(c),kingdom,town);
        return result.startsWith("Annexed ")?ok(c,result):err(c,result);
    }
    private static int approve(CommandContext<CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var town=t(c);var kingdom=d(c).byName(StringArgumentType.getString(c,"kingdom"));
        if(town==null||!town.mayor.equals(p(c).getUUID())||kingdom==null||town.parentKingdom!=null)return err(c,"Only independent town mayor may approve an existing kingdom.");
        if(!KingdomService.bordersTouch(d(c),kingdom.id,town.id))return err(c,"Town and Kingdom must share a chunk edge.");
        d(c).townMergerApprovals.put(town.id,kingdom.id);d(c).setDirty();return ok(c,"Approved "+kingdom.name+" to annex "+town.name+". King runs /kingdom annex "+town.name+". Cost: "+KingdomService.annexCost(d(c),kingdom.id,town.id)+" iron.");
    }
}
