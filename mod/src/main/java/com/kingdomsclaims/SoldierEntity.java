package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.UUID;

/** First-release militia guard. No player-vs-player attacks or world-crossing teleportation. */
public final class SoldierEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SKIN=SynchedEntityData.defineId(SoldierEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> UNIT_TYPE=SynchedEntityData.defineId(SoldierEntity.class,EntityDataSerializers.INT);
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(UNIT_TYPE,0);entityData.define(SKIN,-1);}
    private UUID factionId;
    private String factionType="";
    private BlockPos barracks=BlockPos.ZERO;
    private UUID captain;
    private boolean following=false;
    private boolean rosterBound=false;
    private UUID armyId; private String formation="column"; private int slot=0;

    public SoldierEntity(EntityType<? extends SoldierEntity> type, Level world){super(type,world);}
    public static AttributeSupplier.Builder createAttributes(){
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24.0D)
            .add(Attributes.MOVEMENT_SPEED,0.29D).add(Attributes.ATTACK_DAMAGE,5.0D)
            .add(Attributes.FOLLOW_RANGE,18D).add(Attributes.ARMOR,3D);
    }
    public void assign(UUID faction,String kind,BlockPos home){assign(faction,kind,home,false);}
    public void assign(UUID faction,String kind,BlockPos home,boolean debitedReserve){
        factionId=faction;factionType=kind;entityData.set(UNIT_TYPE,"town".equals(kind)?1:0);barracks=home.immutable();following=false;rosterBound=debitedReserve;
        if(getMainHandItem().isEmpty())setItemSlot(EquipmentSlot.MAINHAND,new ItemStack("town".equals(kind)?Items.STONE_SWORD:Items.IRON_SWORD));
        setPersistenceRequired();
    }
    public boolean isRosterBound(){return rosterBound;}
    public int getUniform(){return entityData.get(SKIN);}
    public void setUniform(int uniform){entityData.set(SKIN,Math.max(-1,Math.min(3,uniform)));}
    public UUID getArmyId(){return armyId;}
    public void assignArmy(UUID army,int uniform,String form,int position,ServerPlayer commander){
        armyId=army;rosterBound=true;slot=position;formation=form;setUniform(uniform);command(commander,true);
    }
    public void changeFormation(String form){formation=form;}
    @Override public void die(net.minecraft.world.damagesource.DamageSource source){
        if(!level().isClientSide&&armyId!=null&&level() instanceof ServerLevel server){
            MilitaryData d=MilitaryData.get(server.getServer());MilitaryData.Army a=d.armies.get(armyId);
            if(a!=null&&a.fielded>0){a.fielded--;d.setDirty();}
            armyId=null; // Never restore a casualty to reserve.
        }
        super.die(source);
    }
    public String getFactionType(){return entityData.get(UNIT_TYPE)==1?"town":"kingdom";}
    public boolean belongs(UUID faction,String kind,BlockPos home){
        return faction!=null&&faction.equals(factionId)&&kind.equals(factionType)&&home.equals(barracks);
    }
    public boolean homeIs(BlockPos pos){return barracks.equals(pos);}
    public void command(ServerPlayer player,boolean follow){
        following=follow;captain=follow?player.getUUID():null;
        getNavigation().stop();
    }
    public boolean isFollowing(){return following;}
    @Override public boolean isAlliedTo(Entity other){
        if(other instanceof SoldierEntity s && factionId!=null && factionId.equals(s.factionId)&&factionType.equals(s.factionType))return true;
        return super.isAlliedTo(other);
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new MeleeAttackGoal(this,1.12D,true));
        goalSelector.addGoal(2,new FollowCaptain());
        goalSelector.addGoal(3,new ReturnHome());
        goalSelector.addGoal(6,new WaterAvoidingRandomStrollGoal(this,0.75D,0.005F));
        goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,9f));
        goalSelector.addGoal(8,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new NearestAttackableTargetGoal<>(this,Monster.class,true));
    }
    @Override public void aiStep(){
        super.aiStep();
        // Home patrol: no free cross-country wandering while defending.
        if(!level().isClientSide&&!following&&tickCount%100==0 && distanceToSqr(Vec3.atCenterOf(barracks))>20*20){
            getNavigation().moveTo(barracks.getX()+.5,barracks.getY()+1,barracks.getZ()+.5,1.12);
        }
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        if(factionId!=null)tag.putUUID("KC_Faction",factionId);
        tag.putString("KC_FactionType",factionType);
        tag.putInt("KC_HomeX",barracks.getX());tag.putInt("KC_HomeY",barracks.getY());tag.putInt("KC_HomeZ",barracks.getZ());
        if(captain!=null)tag.putUUID("KC_Captain",captain);
        tag.putBoolean("KC_Follow",following);
        tag.putBoolean("KC_RosterBound",rosterBound);
        if(armyId!=null)tag.putUUID("KC_Army",armyId);
        tag.putInt("KC_Uniform",getUniform());tag.putString("KC_Formation",formation);tag.putInt("KC_Slot",slot);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        factionId=tag.hasUUID("KC_Faction")?tag.getUUID("KC_Faction"):null;
        factionType=tag.getString("KC_FactionType");entityData.set(UNIT_TYPE,"town".equals(factionType)?1:0);
        barracks=new BlockPos(tag.getInt("KC_HomeX"),tag.getInt("KC_HomeY"),tag.getInt("KC_HomeZ"));
        captain=tag.hasUUID("KC_Captain")?tag.getUUID("KC_Captain"):null;
        following=tag.getBoolean("KC_Follow")&&captain!=null;
        rosterBound=tag.getBoolean("KC_RosterBound");
        armyId=tag.hasUUID("KC_Army")?tag.getUUID("KC_Army"):null;
        setUniform(tag.contains("KC_Uniform")?tag.getInt("KC_Uniform"):-1);
        String form=tag.getString("KC_Formation");formation=java.util.List.of("line","column","wedge").contains(form)?form:"column";
        slot=tag.getInt("KC_Slot");
        if(getMainHandItem().isEmpty())setItemSlot(EquipmentSlot.MAINHAND,new ItemStack("town".equals(factionType)?Items.STONE_SWORD:Items.IRON_SWORD));
        setPersistenceRequired();
    }
    private final class FollowCaptain extends Goal {
        private ServerPlayer leader;
        FollowCaptain(){setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            leader=following&&captain!=null&&level() instanceof ServerLevel s?s.getServer().getPlayerList().getPlayer(captain):null;
            return leader!=null&&leader.level()==level()&&distanceToSqr(formationTarget(leader))>2.5*2.5;
        }
        @Override public boolean canContinueToUse(){return canUse();}
        private Vec3 formationTarget(ServerPlayer p){
            if(armyId==null)return p.position();
            double x=(slot%5)-2,z=2+(slot/5)*1.8;
            if("line".equals(formation)){x=(slot%10)-4.5;z=3+(slot/10)*2;}
            else if("wedge".equals(formation)){int row=(int)Math.floor(Math.sqrt(slot));int col=slot-row*row; x=(col-row)*2;z=3+row*2;}
            // Vector behind player and perpendicular to the direction they face.
            double a=Math.toRadians(p.getYRot());
            return p.position().add(Math.cos(a)*x+Math.sin(a)*z,0,Math.sin(a)*x-Math.cos(a)*z);
        }
        @Override public void tick(){if(leader!=null){Vec3 t=formationTarget(leader);getNavigation().moveTo(t.x,t.y,t.z,1.1D);}}
        @Override public void stop(){getNavigation().stop();leader=null;}
    }
    private final class ReturnHome extends Goal {
        ReturnHome(){setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){return !following&&distanceToSqr(Vec3.atCenterOf(barracks))>12*12;}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void tick(){getNavigation().moveTo(barracks.getX()+.5,barracks.getY()+1,barracks.getZ()+.5,1.05D);}
        @Override public void stop(){getNavigation().stop();}
    }
}
