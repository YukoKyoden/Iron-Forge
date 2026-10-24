package com.kingdomsatwar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.MenuProvider;

/** Persisted, vanilla 27-slot chest UI; a funded project resumes after world reload. */
public final class MineControllerBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final DeferredRegister<BlockEntityType<?>> TYPES=
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,KingdomsAtWar.MOD_ID);
    public static final RegistryObject<BlockEntityType<MineControllerBlockEntity>> TYPE=
        TYPES.register("mine_controller",()->
            BlockEntityType.Builder.of(MineControllerBlockEntity::new,WarTable.MINE_CONTROLLER.get()).build(null));
    public static void init(IEventBus bus){TYPES.register(bus);}
    private NonNullList<ItemStack> inventory=NonNullList.withSize(27,ItemStack.EMPTY);
    private boolean building=false;
    private int progress=0;
    private transient List<MineBlueprint.Step> cachedLayout;
    public MineControllerBlockEntity(BlockPos pos,BlockState state){super(TYPE.get(),pos,state);}
    @Override public int getContainerSize(){return inventory.size();}
    @Override public boolean isEmpty(){for(ItemStack item:inventory)if(!item.isEmpty())return false;return true;}
    @Override public ItemStack getItem(int slot){return inventory.get(slot);}
    @Override public ItemStack removeItem(int slot,int count){ItemStack out=ContainerHelper.removeItem(inventory,slot,count);if(!out.isEmpty())setChanged();return out;}
    @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(inventory,slot);}
    @Override public void setItem(int slot,ItemStack stack){
        inventory.set(slot,stack);if(stack.getCount()>getMaxStackSize())stack.setCount(getMaxStackSize());setChanged();
    }
    @Override public boolean stillValid(Player player){return Container.stillValidBlockEntity(this,player);}
    @Override public void clearContent(){inventory.clear();inventory=NonNullList.withSize(27,ItemStack.EMPTY);setChanged();}
    @Override public Component getDisplayName(){return Component.literal("Mine Construction Chest");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player player){
        return ChestMenu.threeRows(id,inv,this);
    }
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,inventory);
        tag.putBoolean("Building",building);tag.putInt("BuildProgress",progress);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);inventory=NonNullList.withSize(27,ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag,inventory);
        building=tag.getBoolean("Building");progress=tag.getInt("BuildProgress");cachedLayout=null;
    }
    public boolean isBuilding(){return building;}
    public int progress(){return progress;}
    private int count(net.minecraft.world.item.Item item) {
        int total=0;for(ItemStack stack:inventory)if(stack.is(item))total+=stack.getCount();return total;
    }
    public String needs(){return "Oak Logs "+count(Items.OAK_LOG)+"/64, Cobblestone "+
        count(Items.COBBLESTONE)+"/128, Iron "+count(Items.IRON_INGOT)+"/16";}
    private boolean funded(){return count(Items.OAK_LOG)>=64 && count(Items.COBBLESTONE)>=128 && count(Items.IRON_INGOT)>=16;}
    private void deduct(net.minecraft.world.item.Item item,int required) {
        for(int i=0;i<inventory.size()&&required>0;i++){
            ItemStack stack=inventory.get(i);if(!stack.is(item))continue;
            int amount=Math.min(required,stack.getCount());stack.shrink(amount);required-=amount;
            if(stack.isEmpty())inventory.set(i,ItemStack.EMPTY);
        }
    }
    private List<MineBlueprint.Step> layout(){
        if(cachedLayout==null){Direction facing=getBlockState().getValue(MineControllerBlock.FACING);
            cachedLayout=MineBlueprint.layout(getBlockPos(),facing);}
        return cachedLayout;
    }
    /** Called only by validated kingdom member on server, with current controller registered. */
    public String beginBuild(ServerLevel level) {
        if(building)return "Construction already underway: "+progress+"/"+layout().size()+" blocks.";
        if(!funded())return "Deposit missing materials first: "+needs();
        String obstruction=MineBlueprint.check(level,layout());
        if(obstruction!=null)return obstruction+" No materials taken.";
        // No inventory deduction until the entire safe footprint has been checked.
        deduct(Items.OAK_LOG,64);deduct(Items.COBBLESTONE,128);deduct(Items.IRON_INGOT,16);
        building=true;progress=0;setChanged();
        return "Construction started! Mine entrance and supports are now being built.";
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,MineControllerBlockEntity be) {
        if(level.isClientSide || !be.building || !(level instanceof ServerLevel sl))return;
        List<MineBlueprint.Step> plan=be.layout();
        for(int i=0;i<2 && be.progress<plan.size();i++) {
            MineBlueprint.Step step=plan.get(be.progress);
            // Do not overwrite another structure inserted during construction.
            if(level.getBlockEntity(step.pos())!=null || (!level.getBlockState(step.pos()).equals(step.state())
                    && !safeToReplace(level.getBlockState(step.pos())))) {
                be.building=false;be.setChanged();
                MineRuntime.instance().constructionInterrupted(sl,pos,"Obstruction at "+step.pos().toShortString());
                return;
            }
            level.setBlock(step.pos(),step.state(),3);
            be.progress++;
        }
        be.setChanged();
        if(be.progress>=plan.size()){
            be.building=false;be.setChanged();
            MineRuntime.instance().constructionComplete(sl,pos);
        }
    }
    private static boolean safeToReplace(BlockState state){
        return state.isAir()||state.is(net.minecraft.world.level.block.Blocks.STONE)
            ||state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)
            ||state.is(net.minecraft.world.level.block.Blocks.TUFF)
            ||state.is(net.minecraft.world.level.block.Blocks.ANDESITE)
            ||state.is(net.minecraft.world.level.block.Blocks.DIORITE)
            ||state.is(net.minecraft.world.level.block.Blocks.GRANITE);
    }
}
