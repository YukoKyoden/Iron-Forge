package com.hearthhunger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import java.util.Locale;
@EventBusSubscriber(modid=HearthHunger.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class ClientHud {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){HungerSync.latest=null;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){
        Minecraft mc=Minecraft.getInstance();HungerSync s=HungerSync.latest;
        if(!Settings.on("hud")||s==null||mc.player==null||mc.options.hideGui||mc.player.isCreative()||mc.player.isSpectator())return;
        GuiGraphics g=e.getGuiGraphics();
        int right=g.guiWidth()/2+91+(int)Settings.num("hudOffsetX",0,-500,500);
        int x=right-82,y=g.guiHeight()-53+(int)Settings.num("hudOffsetY",0,-500,500);
        if(mc.player.isPassenger())y-=20;
        if(mc.player.isUnderWater()||mc.player.getAirSupply()<mc.player.getMaxAirSupply())y-=10;
        Balance.Activity a=Balance.Activity.values()[Math.clamp(s.activity(),0,Balance.Activity.values().length-1)];
        int color=0xff000000|a.color,food=mc.player.getFoodData().getFoodLevel();
        String label=switch(a){case REST->"Rest";case WALK->"Walk";case WORK->"Work";case FIGHT->"Fight";case SWIM->"Swim";case SPRINT->"Run";};
        String timer=s.seconds()<0?"--:--":s.seconds()==0?(food<=6?"Low":"Hungry"):time(s.seconds());
        icon(g,x,y,a,color);
        g.drawString(mc.font,label,x+11,y,0xffededed,true);
        g.drawString(mc.font,timer,right-mc.font.width(timer),y,food<=6?0xffff7777:color,true);
        if(s.mealTicks()>0&&!s.meal().isEmpty()){
            String remaining=time((s.mealTicks()+19)/20);
            int room=Math.max(8,82-mc.font.width(remaining)-5);
            String name=s.meal();
            if(mc.font.width(name)>room)name=mc.font.plainSubstrByWidth(name,Math.max(1,room-mc.font.width("…")))+"…";
            int mealColor=food<=6?0xff999999:0xffe9ca82;
            g.drawString(mc.font,name,x,y-11,mealColor,true);
            g.drawString(mc.font,remaining,right-mc.font.width(remaining),y-11,mealColor,true);
        }
    }
    static String time(int s){return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    static void icon(GuiGraphics g,int x,int y,Balance.Activity a,int c){
        String[] pixels=switch(a){
            case REST -> new String[]{"........","..####..",".######.",".######.","########","#......#","#......#","........"};
            case WALK -> new String[]{"...##...","...##...","..###...",".#.#.#..","...#....","..#.#...",".#...#..","........"};
            case WORK -> new String[]{".#####..","....##..","...###..","..#..#..",".#......","#.......","........","........"};
            case FIGHT -> new String[]{"#.....#.",".#...#..","..#.#...","...#....","..#.#...",".#...#..","#.....#.","........"};
            case SWIM -> new String[]{"....##..","...###..",".###....","#...####",".##..##.","#..##..#","........","........"};
            case SPRINT -> new String[]{"....##..","....##..","..###...",".#.###..","...#....","..###...","##...##.","........"};};
        for(int row=0;row<pixels.length;row++)for(int col=0;col<pixels[row].length();col++)if(pixels[row].charAt(col)=='#')g.fill(x+col,y+row,x+col+1,y+row+1,c);
    }
    @SubscribeEvent public static void tooltip(ItemTooltipEvent e){
        FoodProperties f=e.getItemStack().get(DataComponents.FOOD);if(f==null)return;
        Balance.Profile p=HearthHunger.profile(e.getItemStack(),f);Balance.Tier t=p.tier();Balance.Style s=p.style();
        e.getToolTip().add(Component.literal("Hearth & Hunger • "+t.label).withColor(0xdab778));
        e.getToolTip().add(Component.literal("Hunger: +"+(f.nutrition()/2.0)+" bars | Stack: "+e.getItemStack().getMaxStackSize()).withColor(0xbfbfbf));
        double energy=(4*(f.nutrition()+f.saturation()))/(Settings.base()*20*60*Math.max(.01,Settings.scale()*Settings.rate(Balance.Activity.WALK)));
        e.getToolTip().add(Component.literal(String.format(Locale.ROOT,"Energy: ~%.1f min walking",energy+t.reserve)).withColor(0xbfbfbf));
        if(t==Balance.Tier.BASIC){e.getToolTip().add(Component.literal("No extra meal perks").withColor(0xaaaaaa));return;}
        e.getToolTip().add(Component.literal("Meal: "+Math.round(t.minutes*Settings.num("mealDurationScale",1,.1,10))+" min • "+s.label).withColor(0x91cf83));
        perk(e,"Healing",t.heal+s.heal);perk(e,"Movement",t.speed);perk(e,"Mining",t.mining+s.mining);perk(e,"Damage",s.damage);perk(e,"Attack speed",s.attack);
        if(s.saving>0)e.getToolTip().add(Component.literal("-"+Math.round(s.saving*100)+"% extra energy use").withColor(0x91cf83));
    }
    static void perk(ItemTooltipEvent e,String n,double d){if(d>0)e.getToolTip().add(Component.literal("+"+Math.round(d*100)+"% "+n).withColor(0x91cf83));}
}
