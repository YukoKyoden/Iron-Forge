package com.kingdomsatwar;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Original Iron Forge recruit mob: real health, melee AI, saved allegiance and orders. */
public final class IronRecruit extends PathfinderMob {
    public static final int FOLLOW=0, HOLD=1, GUARD=2;
    @Nullable private UUID captain;
    private String kingdom="";
    private int stance=FOLLOW;
    private BlockPos post=BlockPos.ZERO;

    public IronRecruit(EntityType<? extends PathfinderMob> type, Level level) {
        super(type,level);
        setPersistenceRequired();
        this.xpReward=3;
    }
    public static AttributeSupplier.Builder createRecruitAttributes(){
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH,26.0D)
            .add(Attributes.MOVEMENT_SPEED,0.30D)
            .add(Attributes.ATTACK_DAMAGE,4.0D)
            .add(Attributes.FOLLOW_RANGE,24.0D)
            .add(Attributes.ARMOR,2.0D);
    }
    @Override protected void registerGoals(){
        this.goalSelector.addGoal(0,new FloatGoal(this));
        this.goalSelector.addGoal(1,new MeleeAttackGoal(this,1.13D,true){
            @Override public boolean canUse(){return IronRecruit.this.canEngage() && super.canUse();}
            @Override public boolean canContinueToUse(){return IronRecruit.this.canEngage() && super.canContinueToUse();}
        });
        this.goalSelector.addGoal(2,new SoldierMoveGoal(this));
        this.targetSelector.addGoal(1,new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Monster.class,12,true,false,
            target->target instanceof Monster && target.isAlive()));
        this.targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,IronRecruit.class,12,true,false,
            target->target instanceof IronRecruit soldier && isEnemy(soldier)));
    }
    private boolean canEngage(){
        if(getTarget()==null)return false;
        if(stance==FOLLOW)return true;
        return getTarget().distanceToSqr(post.getX()+0.5,post.getY(),post.getZ()+0.5) <= 16*16;
    }
    public void assign(ServerPlayer owner,String kingdomId){
        this.captain=owner.getUUID();this.kingdom=kingdomId;
        this.stance=FOLLOW;this.post=blockPosition();
        setPersistenceRequired();setCustomName(Component.literal("Iron Recruit"));
        setCustomNameVisible(true);
    }
    public String kingdom(){return kingdom;}
    public int stance(){return stance;}
    private boolean isEnemy(IronRecruit other) {
        if(kingdom.isEmpty()||other.kingdom.isEmpty()||kingdom.equals(other.kingdom))return false;
        if(level().isClientSide)return false;
        if(level().getServer()==null)return false;
        WarState state=KingdomsAtWar.instance().get(level().getServer());
        WarState.Kingdom mine=state.kingdoms.get(kingdom);
        return mine!=null && mine.wars.contains(other.kingdom);
    }
    private boolean permitted(ServerPlayer player) {
        if(kingdom.isBlank())return false;
        WarState.Kingdom k=KingdomsAtWar.instance().get(player.server).kingdom(player.getUUID());
        return k!=null && kingdom.equals(k.id);
    }
    @Nullable private ServerPlayer captainOnline() {
        if(captain==null || level().getServer()==null) return null;
        ServerPlayer p=level().getServer().getPlayerList().getPlayer(captain);
        return p!=null && p.level()==level()?p:null;
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(level().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer sp))return InteractionResult.PASS;
        if(!permitted(sp)){sp.sendSystemMessage(Component.literal("[Iron Forge] This soldier belongs to another kingdom."));return InteractionResult.CONSUME;}
        ItemStack held=sp.getItemInHand(hand);
        if(held.is(Items.IRON_SWORD)||held.is(Items.STONE_SWORD)||held.is(Items.DIAMOND_SWORD)){
            ItemStack old=getItemBySlot(EquipmentSlot.MAINHAND).copy();
            if(!old.isEmpty())sp.getInventory().placeItemBackInInventory(old);
            ItemStack given=held.copy();given.setCount(1);
            setItemSlot(EquipmentSlot.MAINHAND,given);
            setDropChance(EquipmentSlot.MAINHAND,1.0F);
            if(!sp.getAbilities().instabuild) held.shrink(1);
            sp.sendSystemMessage(Component.literal("[Iron Forge] Sword equipped."));
            return InteractionResult.CONSUME;
        }
        if(!held.isEmpty())return InteractionResult.PASS;
        if(sp.isShiftKeyDown()) {
            stance=GUARD;post=blockPosition();getNavigation().stop();setTarget(null);
        }else{
            stance=stance==FOLLOW?HOLD:FOLLOW;
            if(stance==HOLD){post=blockPosition();getNavigation().stop();setTarget(null);}
            if(stance==FOLLOW)captain=sp.getUUID();
        }
        String label=stance==FOLLOW?"Following "+sp.getName().getString():stance==HOLD?"Holding this position":"Guarding this position";
        sp.sendSystemMessage(Component.literal("[Iron Forge] "+label+"."));
        return InteractionResult.CONSUME;
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(source.getEntity() instanceof ServerPlayer p && permitted(p))return false;
        return super.hurt(source,amount);
    }
    @Override public boolean isAlliedTo(Entity other) {
        if(other instanceof IronRecruit r && !kingdom.isBlank() && kingdom.equals(r.kingdom))return true;
        if(other instanceof ServerPlayer p && permitted(p))return true;
        return super.isAlliedTo(other);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        if(captain!=null)tag.putUUID("IronForgeCaptain",captain);
        tag.putString("IronForgeKingdom",kingdom);
        tag.putInt("IronForgeStance",stance);
        tag.putInt("IronForgePostX",post.getX());
        tag.putInt("IronForgePostY",post.getY());
        tag.putInt("IronForgePostZ",post.getZ());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        if(tag.hasUUID("IronForgeCaptain"))captain=tag.getUUID("IronForgeCaptain");
        kingdom=tag.getString("IronForgeKingdom");
        stance=Math.max(FOLLOW,Math.min(GUARD,tag.getInt("IronForgeStance")));
        post=new BlockPos(tag.getInt("IronForgePostX"),tag.getInt("IronForgePostY"),tag.getInt("IronForgePostZ"));
        setPersistenceRequired();
    }
    /** Navigation evaluated in short bursts; no per-tick world scans. */
    private static final class SoldierMoveGoal extends Goal {
        private final IronRecruit soldier;
        private int delay=0;
        SoldierMoveGoal(IronRecruit entity){soldier=entity;setFlags(EnumSet.of(Goal.Flag.MOVE));}
        @Override public boolean canUse(){
            if(soldier.getTarget()!=null)return false;
            if(soldier.stance==FOLLOW){ServerPlayer p=soldier.captainOnline();
                return p!=null&&soldier.distanceToSqr(p)>12;}
            return soldier.distanceToSqr(soldier.post.getX()+0.5,soldier.post.getY(),soldier.post.getZ()+0.5)>9;
        }
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void tick(){
            if(++delay%10!=0)return;
            if(soldier.stance==FOLLOW){
                ServerPlayer p=soldier.captainOnline();
                if(p!=null&&soldier.distanceToSqr(p)>12)
                    soldier.getNavigation().moveTo(p,1.08D);
            }else{
                soldier.getNavigation().moveTo(soldier.post.getX()+0.5,
                    soldier.post.getY(),soldier.post.getZ()+0.5,1.05D);
            }
        }
    }
}
