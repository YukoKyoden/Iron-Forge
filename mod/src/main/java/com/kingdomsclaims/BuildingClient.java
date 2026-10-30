package com.kingdomsclaims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
public final class BuildingClient {
    public static final Item[] ICONS={Items.RED_BED,Items.WHEAT,Items.CHEST,Items.IRON_PICKAXE,Items.IRON_AXE,Items.IRON_SWORD};
    public static void accept(BuildingNetwork.Reply reply){var mc=Minecraft.getInstance();if(mc.screen instanceof RegisterScreen s){s.message=reply.message();s.valid=reply.count()>0;if(reply.done()){WorldSelectionClient.stop();mc.setScreen(null);if(mc.player!=null)mc.player.displayClientMessage(Component.literal(reply.message()),false);}}}
    public static final class RegisterScreen extends Screen {
        private final BlockPos a,b;private int type=0;private boolean valid=false;private String message="Choose a type, then scan your selection.";
        public RegisterScreen(BlockPos a,BlockPos b){super(Component.literal("Register Building"));this.a=a;this.b=b;}
        private void send(boolean confirm){BuildingNetwork.CHANNEL.sendToServer(new BuildingNetwork.Request(a,b,RegisteredBuildings.TYPES[type],confirm));}
        @Override protected void init(){int x=width/2-150,y=height/2-100;
            for(int i=0;i<6;i++){final int t=i;addRenderableWidget(Button.builder(Component.literal(RegisteredBuildings.TYPES[i]),v->{type=t;valid=false;send(false);}).bounds(x+(i%2)*152,y+(i/2)*30,148,24).build());}
            addRenderableWidget(Button.builder(Component.literal("Scan building"),v->send(false)).bounds(x,y+137,145,22).build());
            addRenderableWidget(Button.builder(Component.literal("Confirm registration"),v->{if(valid)send(true);}).bounds(x+152,y+137,148,22).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"),v->onClose()).bounds(x+90,y+169,120,20).build());
        }
        @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);int x=width/2-150,y=height/2-100;g.drawCenteredString(font,"REGISTER "+RegisteredBuildings.TYPES[type].toUpperCase(),width/2,y-28,0xFFFFDB91);super.render(g,mx,my,pt);
            for(int i=0;i<6;i++)g.renderItem(new ItemStack(ICONS[i]),x+4+(i%2)*152,y+4+(i/2)*30);
            var lines=font.split(Component.literal(message),300);for(int i=0;i<Math.min(4,lines.size());i++)g.drawString(font,lines.get(i),x,y+96+i*10,valid?0xFF8DEA9A:0xFFFFCCA1,false);
        }
        @Override public void onClose(){WorldSelectionClient.stop();Minecraft.getInstance().setScreen(null);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
