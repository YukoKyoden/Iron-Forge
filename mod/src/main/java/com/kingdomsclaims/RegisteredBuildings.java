package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Bounded, cached building surveys. At most one 32768-block survey per second; no chunk loads. */
public final class RegisteredBuildings extends SavedData {
    public static final String[] TYPES={"House","Farm","Warehouse","Mine","Lumber Camp","Barracks"};
    public static final int MAX_VOLUME=32768,MAX_BUILDINGS=512;
    public static final class Building {
        public UUID id=UUID.randomUUID(),owner;public String dimension,type;public BlockPos min,max;
        public int capacity=0;public boolean dirty=true;public long next=0;
        Building(UUID owner,String dim,String type,BlockPos min,BlockPos max){this.owner=owner;dimension=dim;this.type=type;this.min=min;this.max=max;}
        boolean contains(BlockPos p){return p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getY()>=min.getY()&&p.getY()<=max.getY()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    }
    public record Survey(int capacity,String detail){}
    public final List<Building> buildings=new ArrayList<>();private int cursor=0;
    public static RegisteredBuildings get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(RegisteredBuildings::load,RegisteredBuildings::new,"kingdomsclaims_registered_buildings");}
    public static RegisteredBuildings load(CompoundTag root){
        RegisteredBuildings d=new RegisteredBuildings();
        for(Tag entry:root.getList("Buildings",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;if(!t.hasUUID("Owner")||d.buildings.size()>=MAX_BUILDINGS)continue;
            Building b=new Building(t.getUUID("Owner"),t.getString("Dimension"),t.getString("Type"),BlockPos.of(t.getLong("Min")),BlockPos.of(t.getLong("Max")));
            if(t.hasUUID("Id"))b.id=t.getUUID("Id");b.next=Math.max(0,t.getLong("Next"));d.buildings.add(b);
        }return d;
    }
    @Override public CompoundTag save(CompoundTag root){ListTag list=new ListTag();for(Building b:buildings){
        CompoundTag t=new CompoundTag();t.putUUID("Id",b.id);t.putUUID("Owner",b.owner);t.putString("Dimension",b.dimension);t.putString("Type",b.type);
        t.putLong("Min",b.min.asLong());t.putLong("Max",b.max.asLong());t.putLong("Next",b.next);list.add(t);
    }root.put("Buildings",list);return root;}
    public static String type(String raw){String t=raw.toLowerCase(Locale.ROOT).replace('_',' ');return t.equals("lumber")?"lumber camp":t;}
    private static boolean known(String type){for(String t:TYPES)if(t.equalsIgnoreCase(type))return true;return false;}
    public static ServerLevel world(MinecraftServer server,String dim){for(ServerLevel w:server.getAllLevels())if(KingdomData.dim(w).equals(dim))return w;return null;}
    public static boolean owned(ServerLevel w,Building b){
        KingdomData land=KingdomData.get(w.getServer());UUID owner=SharedStockpile.owner(w.getServer(),b.owner);
        for(int x=b.min.getX()>>4;x<=b.max.getX()>>4;x++)for(int z=b.min.getZ()>>4;z<=b.max.getZ()>>4;z++){
            if(!w.hasChunk(x,z))return false;
            var f=BarracksService.faction(w,new BlockPos(x*16,b.min.getY(),z*16));
            if(f==null||!owner.equals(SharedStockpile.owner(w.getServer(),f.id())))return false;
        }return true;
    }
    public static boolean canRegister(ServerPlayer p,Building b){
        if(!owned(p.serverLevel(),b))return false;
        KingdomData land=KingdomData.get(p.server);
        for(int x=b.min.getX()>>4;x<=b.max.getX()>>4;x++)for(int z=b.min.getZ()>>4;z<=b.max.getZ()>>4;z++){
            var c=land.claimAt(b.dimension,x,z);var town=land.townFor(c);var k=land.ownerAt(b.dimension,x,z);
            if(!(town!=null&&town.allowed(p.getUUID(),KingdomData.BUILD)||k!=null&&k.allowed(p.getUUID(),KingdomData.BUILD)))return false;
        }return true;
    }
    public static Building selection(UUID owner,String dim,BlockPos a,BlockPos c,String raw){return new Building(owner,dim,type(raw),
        new BlockPos(Math.min(a.getX(),c.getX()),Math.min(a.getY(),c.getY()),Math.min(a.getZ(),c.getZ())),
        new BlockPos(Math.max(a.getX(),c.getX()),Math.max(a.getY(),c.getY()),Math.max(a.getZ(),c.getZ())));}
    private static boolean bounded(Building b){long x=(long)b.max.getX()-b.min.getX()+1,y=(long)b.max.getY()-b.min.getY()+1,z=(long)b.max.getZ()-b.min.getZ()+1;return x>0&&x<=64&&y>0&&y<=64&&z>0&&z<=64&&x*y*z<=MAX_VOLUME;}
    private String issue(ServerLevel w,Building b){
        if(!known(b.type))return "Choose a building type.";
        if(!bounded(b)||b.min.getY()<w.getMinBuildHeight()||b.max.getY()>=w.getMaxBuildHeight())return "Select at most 32768 blocks, 64 per side, within world height.";
        if(buildings.size()>=MAX_BUILDINGS)return "Building limit reached (512).";
        if(!owned(w,b))return "Every selected chunk must be loaded and owned by this kingdom.";
        for(Building old:buildings)if(old.dimension.equals(b.dimension)&&b.min.getX()<=old.max.getX()&&b.max.getX()>=old.min.getX()&&b.min.getY()<=old.max.getY()&&b.max.getY()>=old.min.getY()&&b.min.getZ()<=old.max.getZ()&&b.max.getZ()>=old.min.getZ())return "Selection overlaps a registered structure.";
        return null;
    }
    public Survey preview(ServerPlayer p,BlockPos a,BlockPos c,String type){var f=BlueprintService.atPlayer(p);if(f==null)return new Survey(0,"BUILD permission required in your claim.");
        Building b=selection(f.id(),KingdomData.dim(p.serverLevel()),a,c,type);String issue=issue(p.serverLevel(),b);
        if(issue!=null)return new Survey(0,issue);if(!canRegister(p,b))return new Survey(0,"BUILD permission required throughout selection.");return scan(p.serverLevel(),b,false);
    }
    public String register(ServerPlayer p,BlockPos a,BlockPos c,String type){Survey survey=preview(p,a,c,type);if(survey.capacity<=0)return survey.detail;
        var f=BlueprintService.atPlayer(p);Building b=selection(f.id(),KingdomData.dim(p.serverLevel()),a,c,type);b.capacity=survey.capacity;b.dirty=false;b.next=p.server.overworld().getGameTime()+1200;
        buildings.add(b);setDirty();return "Registered "+b.type+": "+survey.detail;
    }
    public String register(ServerLevel w,UUID owner,BlockPos a,BlockPos c,String type){Building b=selection(owner,KingdomData.dim(w),a,c,type);String issue=issue(w,b);if(issue!=null)return issue;Survey survey=scan(w,b,false);if(survey.capacity<=0)return survey.detail;b.capacity=survey.capacity;b.dirty=false;b.next=w.getServer().overworld().getGameTime()+1200;buildings.add(b);setDirty();return "Registered "+b.type+": "+survey.detail;}
    private static boolean roof(ServerLevel w,BlockPos p,Building b){for(int dy=2;dy<=Math.min(8,b.max.getY()-p.getY());dy++)if(w.getBlockState(p.above(dy)).isSolidRender(w,p.above(dy)))return true;return false;}
    private static Survey scan(ServerLevel w,Building b,boolean harvest){
        int beds=0,plots=0,storage=0,logs=0,leaves=0,stone=0,air=0,doors=0,stations=0,ripe=0;
        for(BlockPos p:BlockPos.betweenClosed(b.min,b.max)){
            BlockState s=w.getBlockState(p);
            if(s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD&&roof(w,p,b)&&w.getBlockState(p.above()).isAir()&&w.getBlockState(p.below()).isSolidRender(w,p.below()))beds++;
            if(s.getBlock() instanceof DoorBlock)doors++;
            if(s.is(Blocks.CHEST)||s.is(Blocks.BARREL)||s.is(Blocks.TRAPPED_CHEST))storage+=1728;
            // Linked shelves are already counted by their core; count only standalone shelves here.
            if(s.is(KingdomsClaims.STORAGE_EXPANSION.get())&&PhysicalWarehouses.findCore(w,p)==null)storage+=576;
            if(s.is(BlockTags.LOGS))logs++;if(s.is(BlockTags.LEAVES))leaves++;
            if(s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.IRON_ORE)||s.is(Blocks.DEEPSLATE_IRON_ORE))stone++;
            if(s.isAir()&&p.getY()<w.getSeaLevel())air++;
            if(s.is(Blocks.CRAFTING_TABLE)||s.is(Blocks.SMITHING_TABLE)||s.is(Blocks.FLETCHING_TABLE))stations++;
            if(s.is(Blocks.FARMLAND)&&s.getValue(FarmBlock.MOISTURE)>0&&p.getY()<b.max.getY()){
                BlockPos crop=p.above();BlockState cs=w.getBlockState(crop);
                if(cs.is(Blocks.WHEAT)){plots++;
                    if(cs.getValue(CropBlock.AGE)==7){ripe++;
                        if(harvest){int accepted=SharedStockpile.get(w.getServer()).add(b.owner,"WHEAT",1,PhysicalWarehouses.sharedCapacity(w.getServer(),b.owner));
                            if(accepted>0)w.setBlock(crop,cs.setValue(CropBlock.AGE,0),2);}
                    }
                }
            }
        }
        return switch(b.type){
            case "house"->new Survey(doors>0?beds:0,beds+" sheltered beds; an entrance door is required");
            case "farm"->new Survey(plots,plots+" hydrated wheat plots; "+ripe+" ripe (up to "+plots+" wheat/min)");
            case "warehouse"->new Survey(storage,storage+" storage slots (chests, barrels, standalone shelves)");
            case "mine"->new Survey(stone>=24&&air>=12?Math.min(16,stone/24):0,"Needs 24 natural stone/ore blocks and 12 excavated underground spaces; +iron/coal each minute");
            case "lumber camp"->new Survey(logs>=8&&leaves>=8?Math.min(32,logs/8):0,"Needs 8 tree logs and 8 leaves; +wood each minute");
            case "barracks"->new Survey(beds>0&&stations>0?beds*50:0,"Needs sheltered beds and a crafting, smithing or fletching table; "+(beds*50)+" troop capacity");
            default->new Survey(0,"Unknown building type");
        };
    }
    private boolean active(MinecraftServer s,Building b){ServerLevel w=world(s,b.dimension);return !b.dirty&&b.capacity>0&&w!=null&&owned(w,b);}
    public int sum(MinecraftServer s,UUID owner,String type){UUID id=SharedStockpile.owner(s,owner);long n=0;for(Building b:buildings)if(id.equals(SharedStockpile.owner(s,b.owner))&&b.type.equals(type)&&active(s,b))n+=b.capacity;return (int)Math.min(1_000_000_000L,n);}
    public int housing(MinecraftServer s,UUID owner){return sum(s,owner,"house");}
    public int warehouseCapacity(MinecraftServer s,UUID owner){return sum(s,owner,"warehouse");}
    public int barracksCapacity(MinecraftServer s,UUID owner){return sum(s,owner,"barracks");}
    public int[] rates(MinecraftServer s,UUID owner){int[] rates=new int[OperationsData.GOODS.length];rates[0]=sum(s,owner,"mine");rates[1]=rates[0];rates[2]=sum(s,owner,"lumber camp");rates[3]=sum(s,owner,"farm");rates[4]=rates[0]*2;return rates;}
    public List<Building> of(MinecraftServer s,UUID owner){UUID id=SharedStockpile.owner(s,owner);return buildings.stream().filter(b->id.equals(SharedStockpile.owner(s,b.owner))).toList();}
    public boolean remove(ServerPlayer p,String id){Building b=buildings.stream().filter(x->x.id.toString().equals(id)).findFirst().orElse(null);if(b==null||!b.dimension.equals(KingdomData.dim(p.serverLevel()))||!canRegister(p,b))return false;buildings.remove(b);setDirty();return true;}
    public void tick(MinecraftServer s){
        if(buildings.isEmpty())return;long now=s.overworld().getGameTime();
        for(int n=0;n<buildings.size();n++){
            Building b=buildings.get((cursor++)%buildings.size());ServerLevel w=world(s,b.dimension);
            if(w==null||!owned(w,b)){b.capacity=0;b.dirty=true;continue;}
            if(!b.dirty&&now<b.next)continue;
            boolean produce=now>=b.next;Survey survey=scan(w,b,produce&&b.type.equals("farm"));b.capacity=survey.capacity;b.dirty=false;
            if(produce&&b.capacity>0){int cap=PhysicalWarehouses.sharedCapacity(s,b.owner);SharedStockpile stock=SharedStockpile.get(s);
                if(b.type.equals("mine")){stock.add(b.owner,"IRON",b.capacity,cap);stock.add(b.owner,"COAL",b.capacity,cap);stock.add(b.owner,"STONE",b.capacity*2,cap);}
                if(b.type.equals("lumber camp"))stock.add(b.owner,"LOGS",b.capacity,cap);
            }
            if(produce)b.next=now+1200;setDirty();break;
        }
        if(cursor>1000000)cursor=0;
    }
    public void changed(ServerLevel w,BlockPos p){boolean found=false;for(Building b:buildings)if(b.dimension.equals(KingdomData.dim(w))&&b.contains(p)){b.capacity=0;b.dirty=true;found=true;}if(found)setDirty();}
    @SubscribeEvent public static void exploded(net.minecraftforge.event.level.ExplosionEvent.Detonate e){if(e.getLevel() instanceof ServerLevel w)for(BlockPos p:e.getAffectedBlocks())get(w.getServer()).changed(w,p);}
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e){if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel w)get(w.getServer()).changed(w,e.getPos());}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel w)get(w.getServer()).changed(w,e.getPos());}
}
