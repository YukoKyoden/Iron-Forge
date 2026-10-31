package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension stockpiles. The actual core position identifies a warehouse; modules only add capacity.
 * Old chest links are retained in world data for rollback, but are not accessed by the new system. */
public final class WarehouseData extends SavedData {
    public static final int MAX_MODULES=100, SLOTS_PER_BLOCK=9, ITEMS_PER_SLOT=64;
    public static final class Depot {
        public final UUID faction;
        public final LinkedHashSet<Long> shelves=new LinkedHashSet<>();
        public boolean shelvesMigrated=false;
        public final LinkedHashSet<Long> storage=new LinkedHashSet<>(); // v0.4.18 rollback metadata
        public final HashMap<String,Integer> goods=new HashMap<>();
        Depot(UUID faction){this.faction=faction;}
        int count(String good){return Math.max(0,goods.getOrDefault(good,0));}
        int total(){long n=0;for(String good:OperationsData.GOODS)n+=count(good);return (int)Math.min(Integer.MAX_VALUE,n);}
    }
    public final Map<Long,Depot> depots=new HashMap<>();
    public static WarehouseData get(ServerLevel world){return world.getDataStorage().computeIfAbsent(WarehouseData::load,WarehouseData::new,"kingdomsclaims_physical_warehouses");}
    public static WarehouseData load(CompoundTag root){
        WarehouseData data=new WarehouseData();
        for(Tag entry:root.getList("Depots",Tag.TAG_COMPOUND)){
            CompoundTag d=(CompoundTag)entry;
            if(!d.hasUUID("Faction"))continue;
            Depot depot=new Depot(d.getUUID("Faction"));
            long[] linked=d.getLongArray("Storage");
            for(int i=0;i<Math.min(24,linked.length);i++)depot.storage.add(linked[i]);
            depot.shelvesMigrated=d.getBoolean("ShelvesMigrated");
            for(long shelf:d.getLongArray("Shelves"))if(depot.shelves.size()<MAX_MODULES)depot.shelves.add(shelf);
            CompoundTag items=d.getCompound("ModularGoods");
            for(String good:OperationsData.GOODS)depot.goods.put(good,Math.max(0,items.getInt(good)));
            data.depots.put(d.getLong("Pos"),depot);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag list=new ListTag();
        for(var e:depots.entrySet()){
            CompoundTag d=new CompoundTag();d.putLong("Pos",e.getKey());d.putUUID("Faction",e.getValue().faction);
            d.putLongArray("Storage",e.getValue().storage.stream().mapToLong(Long::longValue).toArray());
            d.putLongArray("Shelves",e.getValue().shelves.stream().mapToLong(Long::longValue).toArray());
            d.putBoolean("ShelvesMigrated",e.getValue().shelvesMigrated);
            CompoundTag items=new CompoundTag();for(String good:OperationsData.GOODS)items.putInt(good,e.getValue().count(good));
            d.put("ModularGoods",items);list.add(d);
        }
        root.put("Depots",list);return root;
    }
    public Depot at(BlockPos pos){return depots.get(pos.asLong());}
    public Depot ensure(BlockPos pos,UUID faction){
        Depot existing=at(pos);
        if(existing!=null)return existing.faction.equals(faction)?existing:null;
        Depot d=new Depot(faction);depots.put(pos.asLong(),d);setDirty();return d;
    }
    public boolean alreadyLinked(BlockPos target,BlockPos except){
        for(var entry:depots.entrySet())if(entry.getKey()!=except.asLong()&&entry.getValue().storage.contains(target.asLong()))return true;
        return false;
    }
}
