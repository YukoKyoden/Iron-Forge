package com.kingdomsclaims;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
/** Shared inventory-style resource display for manager, construction and warehouses. */
public final class ResourceSlots {
    public static String compact(int n){return n>=1000000?String.format(Locale.ROOT,"%.1fm",n/1000000.0):n>=10000?String.format(Locale.ROOT,"%.0fk",n/1000.0):n>=1000?String.format(Locale.ROOT,"%.1fk",n/1000.0):""+n;}
    public static void slot(GuiGraphics g,int x,int y,String good,int count,int rate,int cost,int mx,int my){var font=Minecraft.getInstance().font;
        MinecraftMenu.inset(g,x,y,x+40,y+40);
        var item=EconomyService.goodsItem(good);if(item!=null)g.renderItem(new ItemStack(item),x+12,y+3);
        g.drawCenteredString(font,compact(count),x+20,y+23,cost>count?0xFFFF7777:0xFFFFFFFF);
        if(rate!=0)g.drawString(font,rate>0?"+":"-",x+3,y+3,rate>0?0xFF6EE98A:0xFFFF7777,false);
        if(mx>=x&&mx<x+40&&my>=y&&my<y+40){List<Component> lines=new ArrayList<>();lines.add(Component.literal((item==null?good:item.getDescription().getString())+" stored: "+ProductionText.count(count)));if(rate>0)lines.add(Component.literal("Production: +"+rate+" every 6 minutes"));if(cost>0)lines.add(Component.literal("Required: "+ProductionText.count(cost)));g.renderComponentTooltip(font,lines,mx,my);}
    }
}
