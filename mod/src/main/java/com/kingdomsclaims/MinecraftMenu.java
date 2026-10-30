package com.kingdomsclaims;
import net.minecraft.client.gui.GuiGraphics;
/** Vanilla inventory palette and pixel bevels. Buttons/tooltips use Minecraft's own widgets. */
public final class MinecraftMenu {
    private MinecraftMenu(){}
    public static final int TEXT=0xFF404040,MUTED=0xFF555555,GREEN=0xFF246B26,RED=0xFFA01919;
    public static void panel(GuiGraphics g,int x,int y,int right,int bottom){
        g.fill(x,y,right,bottom,0xFF101010);
        g.fill(x+1,y+1,right-1,bottom-1,0xFF555555);
        g.fill(x+1,y+1,right-2,bottom-2,0xFFFFFFFF);
        g.fill(x+3,y+3,right-3,bottom-3,0xFFC6C6C6);
        g.fill(right-3,y+3,right-1,bottom-1,0xFF555555);
        g.fill(x+3,bottom-3,right-1,bottom-1,0xFF555555);
    }
    public static void inset(GuiGraphics g,int x,int y,int right,int bottom){
        g.fill(x,y,right,bottom,0xFFFFFFFF);
        g.fill(x,y,right-1,bottom-1,0xFF373737);
        g.fill(x+1,y+1,right-1,bottom-1,0xFF8B8B8B);
    }
    public static void selection(GuiGraphics g,int x,int y,int right,int bottom,boolean selected){
        inset(g,x,y,right,bottom);if(selected){g.fill(x+1,y+1,right-1,bottom-1,0xFFB0B0B0);g.renderOutline(x,y,right-x,bottom-y,0xFFFFFFFF);}
    }
}
