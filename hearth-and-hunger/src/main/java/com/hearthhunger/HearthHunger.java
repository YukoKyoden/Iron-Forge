package com.hearthhunger;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@Mod(HearthHunger.ID)
public final class HearthHunger {
    public static final String ID="hearthhunger";
    static final String KEY="HearthHunger";
    private static final ResourceLocation MOVE=id("movement"), MINE=id("mining"), ATTACK=id("attack_speed"), DAMAGE=id("attack_damage");
    private final Map<UUID,Motion> motions=new HashMap<>();
    private long beforeDay;
    private int phase;
    private MinecraftServer lastServer;
    static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath(ID,s);}
    public HearthHunger(IEventBus modBus){
        Settings.load();
        modBus.addListener(this::components);modBus.addListener(HungerSync::register);
        IEventBus bus=NeoForge.EVENT_BUS;
        bus.addListener(this::tick);bus.addListener(this::eaten);bus.addListener(this::startEating);bus.addListener(this::heal);
        bus.addListener(this::attack);bus.addListener(this::breakBlock);bus.addListener(this::placed);
        bus.addListener(this::speed);bus.addListener(this::beforeServer);bus.addListener(this::afterServer);bus.addListener(this::logout);
    }
    static CompoundTag state(ServerPlayer p){CompoundTag root=p.getPersistentData();if(!root.contains(KEY))root.put(KEY,new CompoundTag());return root.getCompound(KEY);}
    static Balance.Profile profile(ItemStack stack,FoodProperties f){return Settings.profile(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),f.nutrition());}
    private void components(ModifyDefaultComponentsEvent e){
        e.getAllItems().forEach(item->{
            FoodProperties f=item.components().get(DataComponents.FOOD);if(f==null)return;
            String name=BuiltInRegistries.ITEM.getKey(item).toString();Balance.Profile p=Settings.profile(name,f.nutrition());
            int current=item.components().getOrDefault(DataComponents.MAX_STACK_SIZE,64);
            if(Settings.on("stackLimits")&&p.stack()<current)e.modify(item,b->b.set(DataComponents.MAX_STACK_SIZE,p.stack()));
            if(Settings.on("vanillaFoodNerf")&&name.startsWith("minecraft:")&&!p.special()){
                int n=Balance.reducedNutrition(f.nutrition(),Settings.num("vanillaNutritionMultiplier",.88,.1,1));
                float sat=(float)(f.saturation()*Settings.num("vanillaSaturationMultiplier",.8,0,1));
                e.modify(item,b->b.set(DataComponents.FOOD,new FoodProperties(n,sat,f.canAlwaysEat(),f.eatSeconds(),f.usingConvertsTo(),f.effects())));
            }
        });
    }
    static final class Motion {
        int work,fight,prevFood=20,preEatFood=-1;double x,z;boolean first=true;Balance.Activity activity=Balance.Activity.REST;
    }
    Motion motion(ServerPlayer p){return motions.computeIfAbsent(p.getUUID(),u->new Motion());}
    private void logout(PlayerEvent.PlayerLoggedOutEvent e){motions.remove(e.getEntity().getUUID());}
    private void attack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p)motion(p).fight=60;}
    private void breakBlock(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p)motion(p).work=60;}
    private void placed(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p)motion(p).work=40;}
    private void speed(PlayerEvent.BreakSpeed e){if(e.getEntity() instanceof ServerPlayer p)motion(p).work=12;}
    static Balance.Tier tier(CompoundTag d){return Balance.Tier.values()[Math.clamp(d.getInt("Tier"),0,Balance.Tier.values().length-1)];}
    static Balance.Style style(CompoundTag d){return Balance.Style.values()[Math.clamp(d.getInt("Style"),0,Balance.Style.values().length-1)];}
    private void startEating(LivingEntityUseItemEvent.Start e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative()||p.isSpectator())return;
        FoodProperties f=e.getItem().get(DataComponents.FOOD);if(f==null)return;
        Motion m=motion(p);m.preEatFood=p.getFoodData().getFoodLevel();
        if(!Settings.on("mealBuffs")||profile(e.getItem(),f).tier()==Balance.Tier.BASIC)return;
        if(state(p).getInt("Cooldown")>0&&m.preEatFood>6){
            e.setCanceled(true);p.displayClientMessage(Component.literal("Still digesting your last meal. Snacks are fine."),true);
        }
    }
    private void eaten(LivingEntityUseItemEvent.Finish e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative()||p.isSpectator())return;
        ItemStack stack=e.getItem();FoodProperties f=stack.get(DataComponents.FOOD);if(f==null)return;
        Balance.Profile pr=profile(stack,f);if(pr.tier()==Balance.Tier.BASIC||!Settings.on("mealBuffs"))return;
        CompoundTag d=state(p);
        d.putInt("Tier",pr.tier().ordinal());d.putInt("Style",pr.style().ordinal());
        String meal=stack.getHoverName().getString();d.putString("Meal",meal.substring(0,Math.min(meal.length(),256)));
        d.putInt("Ticks",(int)(pr.tier().minutes*1200*Settings.num("mealDurationScale",1,.1,10)));
        d.putDouble("Reserve",pr.tier().reserve);
        d.putInt("Cooldown",(int)(20*Settings.num("mealCooldownSeconds",30,0,300)));
        Motion m=motion(p);
        if(Settings.on("gradualDigestion")&&m.preEatFood>=0){
            FoodData fd=p.getFoodData();int gained=Math.max(0,fd.getFoodLevel()-m.preEatFood);
            int delayed=gained/3;
            if(delayed>0){fd.setFoodLevel(fd.getFoodLevel()-delayed);fd.setSaturation(Math.min(fd.getSaturationLevel(),fd.getFoodLevel()));d.putInt("Digest",Math.min(6,d.getInt("Digest")+delayed));}
        }
        m.preEatFood=-1;
    }
    private void tick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        Motion m=motion(p);CompoundTag d=state(p);FoodData food=p.getFoodData();int level=food.getFoodLevel();
        int ticks=d.getInt("Ticks");Balance.Tier t=tier(d);Balance.Style s=style(d);
        double dx=p.getX()-m.x,dz=p.getZ()-m.z;boolean moving=!m.first&&dx*dx+dz*dz>.0004;
        m.x=p.getX();m.z=p.getZ();m.first=false;
        Balance.Activity a=p.isSleeping()||p.isPassenger()?Balance.Activity.REST:
            p.isSwimming()?Balance.Activity.SWIM:p.isSprinting()&&moving?Balance.Activity.SPRINT:
            m.fight>0?Balance.Activity.FIGHT:m.work>0?Balance.Activity.WORK:moving?Balance.Activity.WALK:Balance.Activity.REST;
        m.activity=a;if(m.work>0)m.work--;if(m.fight>0)m.fight--;
        boolean exempt=p.isCreative()||p.isSpectator();
        if(!exempt&&d.getInt("Cooldown")>0)d.putInt("Cooldown",d.getInt("Cooldown")-1);
        if(!exempt&&Settings.on("gradualDigestion")&&d.getInt("Digest")>0&&p.tickCount%200==0&&level<20){
            food.setFoodLevel(++level);d.putInt("Digest",d.getInt("Digest")-1);
        }
        boolean active=!exempt&&Settings.on("mealBuffs")&&Balance.mealActive(level,ticks);
        if(!exempt&&ticks>0){d.putInt("Ticks",--ticks);if(ticks==0)d.putDouble("Reserve",0);}
        if(!exempt&&Settings.on("replaceNourishment")){
            var nourishment=BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.fromNamespaceAndPath("farmersdelight","nourishment"));
            nourishment.ifPresent(h->{if(p.hasEffect(h))p.removeEffect(h);});
        }
        double rate=Settings.rate(a)*(active?1-s.saving:1)*Settings.scale();
        if(!exempt&&Settings.on("activityHunger")&&p.level().getDifficulty()!=Difficulty.PEACEFUL&&!p.isSleeping()){
            double reserve=active?Math.max(0,d.getDouble("Reserve")):0;
            double use=rate/1200;
            if(reserve>0)d.putDouble("Reserve",Math.max(0,reserve-use));
            else food.addExhaustion((float)(Settings.base()*rate));
        }
        if(!exempt&&Settings.on("performanceModifiers")&&level<=6&&p.isSprinting())p.setSprinting(false);
        boolean perf=!exempt&&Settings.on("performanceModifiers");
        update(p,Attributes.MOVEMENT_SPEED,MOVE,(perf?Balance.movement(level):0)+(active?t.speed:0));
        update(p,Attributes.BLOCK_BREAK_SPEED,MINE,(perf?Balance.mining(level):0)+(active?t.mining+s.mining:0));
        update(p,Attributes.ATTACK_SPEED,ATTACK,(perf?Balance.attack(level):0)+(active?s.attack:0));
        update(p,Attributes.ATTACK_DAMAGE,DAMAGE,active?s.damage:0);
        if(!exempt&&Settings.on("growl")&&m.prevFood>=12&&level<12){
            p.level().playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.PLAYER_BURP,SoundSource.PLAYERS,.3f,.5f);
        }
        m.prevFood=level;
        if(p.tickCount%5==0){
            int secs=-1;
            if(!exempt&&Settings.on("activityHunger")&&rate>0&&p.level().getDifficulty()!=Difficulty.PEACEFUL){
                double vanilla=switch(a){case SPRINT -> .028;case SWIM -> .004;case FIGHT -> .004;case WORK -> .002;default -> 0;};
                double estimate=Balance.estimateWithVanilla(level,food.getSaturationLevel(),food.getExhaustionLevel(),active?d.getDouble("Reserve"):0,rate,Settings.base(),vanilla);
                secs=(int)Math.min(35999,Math.ceil(estimate));
            }
            PacketDistributor.sendToPlayer(p,new HungerSync(a.ordinal(),secs,ticks,d.getString("Meal"),t.ordinal(),s.ordinal()));
        }
    }
    static void update(ServerPlayer p,Holder<Attribute> attr,ResourceLocation key,double amount){
        AttributeInstance i=p.getAttribute(attr);if(i==null)return;
        AttributeModifier old=i.getModifier(key);
        if(old!=null&&Math.abs(old.amount()-amount)<.00001)return;
        if(old!=null)i.removeModifier(key);
        if(amount!=0)i.addTransientModifier(new AttributeModifier(key,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private void heal(LivingHealEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative()||p.isSpectator()||!Settings.on("mealBuffs"))return;
        CompoundTag d=state(p);
        if(p.getFoodData().getFoodLevel()>=18&&Balance.mealActive(p.getFoodData().getFoodLevel(),d.getInt("Ticks"))&&!p.hasEffect(MobEffects.REGENERATION))
            e.setAmount((float)(e.getAmount()*(1+tier(d).heal+style(d).heal)));
    }
    private void beforeServer(ServerTickEvent.Pre e){
        MinecraftServer s=e.getServer();if(lastServer!=s){lastServer=s;phase=0;motions.clear();}
        beforeDay=s.overworld().getDayTime();
    }
    private void afterServer(ServerTickEvent.Post e){
        if(!Settings.on("longDay"))return;
        ServerLevel l=e.getServer().overworld();
        if(!l.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))return;
        if(l.getDayTime()==beforeDay+1){if(!Balance.advanceDay(phase))l.setDayTime(beforeDay);phase=(phase+1)%8;}
    }
}
