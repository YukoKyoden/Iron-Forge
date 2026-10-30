package com.kingdomsclaims;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

/** Virtual residents and chunk-based districts. No citizen entities, scans or chunk loads. */
public final class DistrictData extends SavedData {
    public static final String[] TYPES={"Residential","Commercial","Industrial","Agricultural"};
    public static final int MAX_DISTRICTS=48, MAX_PAINT=128;
    public static final class Zone {
        public UUID id,owner;
        public String dimension,name;public int type,level;
        public final Set<Long> chunks=new LinkedHashSet<>();
        public Zone(UUID id,UUID owner,String dimension,String name,int type){this.id=id;this.owner=owner;this.dimension=dimension;this.name=name;this.type=type;this.level=0;}
    }
    public static final class Civics {
        public long crowns=0,pendingTaxNuggets=0;public int population=0,tax=10,happiness=85,lastTaxes=0,lastPayroll=0,paidCycles=0,missedCycles=0;
        public boolean paid=true;
    }
    public final Map<UUID,Zone> zones=new LinkedHashMap<>();
    public final Map<UUID,Civics> civics=new HashMap<>();
    public static DistrictData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(DistrictData::load,DistrictData::new,"kingdomsclaims_districts");}
    public Civics city(UUID id){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null)id=SharedStockpile.owner(server,id);return civics.computeIfAbsent(id,k->new Civics());}
    public List<Zone> of(UUID owner,String dimension){List<Zone> list=new ArrayList<>();for(Zone z:zones.values())if(z.owner.equals(owner)&&z.dimension.equals(dimension))list.add(z);return list;}
    public Zone at(UUID owner,String dimension,int cx,int cz){long pos=ChunkPos.asLong(cx,cz);for(Zone z:zones.values())if(z.owner.equals(owner)&&z.dimension.equals(dimension)&&z.chunks.contains(pos))return z;return null;}
    public int capacity(UUID owner,int type){int amount=0;for(Zone z:zones.values())if(z.owner.equals(owner)&&z.type==type&&z.level>0){int factor=type==0?8:type==1?8:type==2?12:12;amount+=z.chunks.size()*z.level*factor;}return Math.min(1000000,amount);}
    public int payroll(MinecraftServer server,UUID owner){int soldiers=EconomyService.troops(server,owner);return (soldiers+49)/50;}
    public int estimatedTaxes(UUID owner){Civics c=city(owner);
        return Math.max(0,(c.population*c.tax+9)/10);
    }
    /** Per-cycle population and physical tax income: no spendable virtual Crowns.
     * Tax nuggets are pending only while no warehouse has capacity; never usable for payroll until stored. */
    public boolean cycle(MinecraftServer server,UUID owner,boolean essentialsPaid){
        Civics c=city(owner);
        int maxPopulation=RegisteredBuildings.get(server).housing(server,owner);
        c.population=Math.min(c.population,maxPopulation);
        if(essentialsPaid&&c.population<maxPopulation)c.population=Math.min(maxPopulation,c.population+Math.max(1,maxPopulation/12));
        else if(!essentialsPaid&&c.population>0)c.population=Math.max(0,c.population-Math.max(1,c.population/12));
        int earned=essentialsPaid?estimatedTaxes(owner):0;
        c.lastTaxes=earned;
        if(earned>0)c.pendingTaxNuggets=Math.min(2_000_000_000L,c.pendingTaxNuggets+earned);
        int wages=payroll(server,owner);c.lastPayroll=wages;
        c.paid=essentialsPaid;
        if(essentialsPaid)c.paidCycles++;else c.missedCycles++;
        c.happiness=Math.max(15,Math.min(95,90-Math.max(0,c.tax-10)*3+(essentialsPaid?0:-25)));
        setDirty();
        if(essentialsPaid&&c.pendingTaxNuggets>0){
            int accepted=SharedStockpile.get(server).add(owner,"GOLD_NUGGETS",(int)Math.min(Integer.MAX_VALUE,c.pendingTaxNuggets),PhysicalWarehouses.sharedCapacity(server,owner));
            if(accepted>0){c.pendingTaxNuggets-=accepted;setDirty();}
        }
        return essentialsPaid;
    }
    public static DistrictData load(CompoundTag root){
        DistrictData d=new DistrictData();for(Tag el:root.getList("Districts",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)el;if(!t.hasUUID("Id")||!t.hasUUID("Owner"))continue;
            Zone z=new Zone(t.getUUID("Id"),t.getUUID("Owner"),t.getString("Dimension"),t.getString("Name"),t.getInt("Type"));
            if(z.type<0||z.type>=TYPES.length)continue;
            z.level=Math.max(0,Math.min(4,t.getInt("Level")));
            for(long key:t.getLongArray("Chunks"))if(z.chunks.size()<4096)z.chunks.add(key);
            d.zones.put(z.id,z);
        }
        for(Tag el:root.getList("Civics",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)el;if(!t.hasUUID("Owner"))continue;
            Civics c=new Civics();c.crowns=Math.max(0,t.getLong("Crowns"));c.pendingTaxNuggets=Math.max(0,t.getLong("PendingTaxNuggets"));c.population=Math.max(0,t.getInt("People"));c.tax=Math.max(0,Math.min(25,t.getInt("Tax")));
            c.happiness=t.getInt("Happy");c.paid=t.getBoolean("Paid");c.lastTaxes=t.getInt("TaxIncome");c.lastPayroll=t.getInt("Wages");c.paidCycles=t.getInt("PaidCycles");c.missedCycles=t.getInt("MissedCycles");
            d.civics.put(t.getUUID("Owner"),c);
        }return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag zs=new ListTag();for(Zone z:zones.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",z.id);t.putUUID("Owner",z.owner);t.putString("Dimension",z.dimension);t.putString("Name",z.name);t.putInt("Type",z.type);t.putInt("Level",z.level);
            long[] keys=new long[z.chunks.size()];int i=0;for(long key:z.chunks)keys[i++]=key;t.putLongArray("Chunks",keys);zs.add(t);
        }root.put("Districts",zs);ListTag stats=new ListTag();for(var e:civics.entrySet()){
            var c=e.getValue();CompoundTag t=new CompoundTag();t.putUUID("Owner",e.getKey());t.putLong("Crowns",c.crowns);t.putLong("PendingTaxNuggets",c.pendingTaxNuggets);t.putInt("People",c.population);t.putInt("Tax",c.tax);t.putInt("Happy",c.happiness);
            t.putBoolean("Paid",c.paid);t.putInt("TaxIncome",c.lastTaxes);t.putInt("Wages",c.lastPayroll);t.putInt("PaidCycles",c.paidCycles);t.putInt("MissedCycles",c.missedCycles);stats.add(t);
        }root.put("Civics",stats);return root;
    }
}
