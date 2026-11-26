package com.hearthhunger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public record HungerSync(int activity,int seconds,int mealTicks,String meal,int tier,int style) implements CustomPacketPayload {
    public static final Type<HungerSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("hearthhunger","status"));
    public static volatile HungerSync latest;
    public static final StreamCodec<RegistryFriendlyByteBuf,HungerSync> CODEC=new StreamCodec<>() {
        public HungerSync decode(RegistryFriendlyByteBuf b){return new HungerSync(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(256),b.readVarInt(),b.readVarInt());}
        public void encode(RegistryFriendlyByteBuf b,HungerSync s){b.writeVarInt(s.activity);b.writeVarInt(s.seconds);b.writeVarInt(s.mealTicks);b.writeUtf(s.meal,256);b.writeVarInt(s.tier);b.writeVarInt(s.style);}
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(payload,ctx)->{ctx.enqueueWork(()->{latest=payload;});});}
}
