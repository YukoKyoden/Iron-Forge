package com.kingdomsclaims;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Survey any actual player-built home; save a reusable custom building instead of a preset. */
public final class BlueprintCommands {
    private BlueprintCommands(){}
    private static int report(ServerPlayer p,BlueprintService.Result r){p.sendSystemMessage(Component.literal("[Kingdom Architect] "+r.message()));return r.success()?1:0;}
    private static int mark(ServerPlayer p,boolean first){
        HitResult ray=p.pick(7,0,false);
        if(!(ray instanceof BlockHitResult hit))return report(p,new BlueprintService.Result(false,"Look at a house block within 7 blocks."));
        BlockPos pos=hit.getBlockPos();BlueprintService.Selection s=BlueprintService.selection(p);
        String dimension=KingdomData.dim(p.serverLevel());
        if(s.dimension!=null&&!s.dimension.equals(dimension)){s.a=null;s.b=null;}
        s.dimension=dimension;if(first)s.a=pos;else s.b=pos;
        return report(p,new BlueprintService.Result(true,(first?"First":"Second")+" blueprint corner: "+pos.toShortString()+". "+(s.a!=null&&s.b!=null?"Both corners marked — /architect save <name>.":"Mark the other corner.")));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("architect")
            .executes(c->help(c.getSource().getPlayerOrException()))
            .then(Commands.literal("help").executes(c->help(c.getSource().getPlayerOrException())))
            .then(Commands.literal("select").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();
                if(BlueprintService.atPlayer(p)==null)return report(p,new BlueprintService.Result(false,"Stand inside your faction claim with BUILD permission."));
                BlueprintNetwork.sendSelector(p);return 1;}))
            .then(Commands.literal("pos1").executes(c->mark(c.getSource().getPlayerOrException(),true)))
            .then(Commands.literal("pos2").executes(c->mark(c.getSource().getPlayerOrException(),false)))
            .then(Commands.literal("place").then(Commands.argument("name",StringArgumentType.greedyString())
                .executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();String name=StringArgumentType.getString(c,"name");
                    if(BlueprintNetwork.prepareAtPlayer(p,name))return 1;
                    return report(p,new BlueprintService.Result(false,"Stand in your own claimed land with BUILD permission and use an existing blueprint name."));})))
            .then(Commands.literal("cancel").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();BlueprintService.Selection s=BlueprintService.selection(p);s.a=null;s.b=null;return report(p,new BlueprintService.Result(true,"Cleared selection."));}))
            .then(Commands.literal("save").then(Commands.argument("name",StringArgumentType.greedyString())
                .executes(c->report(c.getSource().getPlayerOrException(),BlueprintService.save(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"name"))))))
            .then(Commands.literal("delete").then(Commands.argument("name",StringArgumentType.greedyString())
                .executes(c->report(c.getSource().getPlayerOrException(),BlueprintService.delete(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"name"))))))
            .then(Commands.literal("list").executes(c->{
                ServerPlayer p=c.getSource().getPlayerOrException();var f=BlueprintService.atPlayer(p);
                if(f==null)return report(p,new BlueprintService.Result(false,"Stand inside your faction's claims with BUILD permission."));
                var plans=BlueprintData.get(p.getServer()).owned(f.id());
                if(plans.isEmpty())return report(p,new BlueprintService.Result(true,"No blueprints yet. Use /architect select, click two corners, then name and save the highlighted house."));
                for(var plan:plans)p.sendSystemMessage(Component.literal("[Architect] "+plan.name+" | "+plan.width+"x"+plan.height+"x"+plan.depth+" | "+plan.blocks.size()+" blocks | housing "+plan.housingSlots()+" ("+plan.housingReport().usableFloor()+" floor tiles, "+plan.housingReport().beds()+" beds)"));
                return 1;
            })));
    }
    private static int help(ServerPlayer p){
        p.sendSystemMessage(Component.literal("[Kingdom Architect] Build a house in claimed land. Use /architect select, click the two corners, check the highlight, and save with a name. Legacy /architect pos1 and pos2 still work."));
        p.sendSystemMessage(Component.literal("Open your Kingdom Manager > ARCHITECT > PLACE IN WORLD, or /architect place <name>. Aim to move the hologram, R rotate, Shift+wheel raise/lower, left-click construct, Esc cancel. Materials are taken from your inventory. /architect list | delete <name> | cancel."));
        return 1;
    }
}
