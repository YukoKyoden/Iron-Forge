package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned troop ledger. Reserves, assigned armies, and fielded NPCs are mutually exclusive. */
public final class MilitaryData extends SavedData {
    public static final int TOWN_CAP=500, KINGDOM_CAP=10000, MAX_ARMIES=15;
    public record ArmySummary(UUID id,String name,int troops,String type){}
    public static final class Army {
        public final UUID id,faction; public String name,type,dimension; public int strength,fielded,uniform=0; public String formation="column"; public int rallyX,rallyZ; public double locationX,locationZ; public boolean marching=false;
        public BlockPos origin;
        public Army(UUID id,UUID faction,String type,String name,String dimension,BlockPos origin){
            this.id=id;this.faction=faction;this.type=type;this.name=name;this.dimension=dimension;this.origin=origin;this.locationX=origin.getX();this.locationZ=origin.getZ();this.rallyX=origin.getX();this.rallyZ=origin.getZ();
        }
    }
    private final Map<UUID,Integer> reserves=new HashMap<>();
    public final Map<UUID,Army> armies=new LinkedHashMap<>();
    public static MilitaryData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(MilitaryData::load,MilitaryData::new,"kingdomsclaims_military");
    }
    public int reserve(UUID id){return Math.max(0,reserves.getOrDefault(id,0));}
    public int assigned(UUID id){int n=0;for(Army a:armies.values())if(a.faction.equals(id))n+=a.strength+a.fielded;return n;}
    public int armyCount(UUID id){int n=0;for(Army a:armies.values())if(a.faction.equals(id))n++;return n;}
    public int max(String type){return "town".equals(type)?TOWN_CAP:KINGDOM_CAP;}
    public boolean addReserve(UUID id,int amount){
        if(amount<0)return removeReserve(id,-amount);
        if(amount==0)return true;
        long total=(long)reserve(id)+assigned(id)+amount;
        // Deployed entities already left the ledger; they must return through the controlled recall action.
        if(total>Integer.MAX_VALUE)return false;
        reserves.put(id,reserve(id)+amount);setDirty();return true;
    }
    public boolean removeReserve(UUID id,int amount){
        if(amount<0||reserve(id)<amount)return false;
        reserves.put(id,reserve(id)-amount);setDirty();return true;
    }
    public List<Army> armiesOf(UUID faction){List<Army> list=new ArrayList<>();for(Army a:armies.values())if(a.faction.equals(faction))list.add(a);return list;}
    public Army create(UUID faction,String type,String name,String dim,BlockPos origin){
        if(armyCount(faction)>=MAX_ARMIES)return null;
        Army a=new Army(UUID.randomUUID(),faction,type,name,dim,origin);armies.put(a.id,a);setDirty();return a;
    }
    public boolean assign(Army a,int n){if(n<=0||a.fielded>0||a.marching||!removeReserve(a.faction,n))return false;a.strength+=n;setDirty();return true;}
    public boolean withdraw(Army a,int n){if(n<=0||a.strength<n||a.fielded>0||a.marching)return false;a.strength-=n;addReserve(a.faction,n);setDirty();return true;}
    /** Server-side numerical movement, 1.5 blocks/second, never forces any Minecraft chunks. */
    public void advance(){
        boolean changed=false;
        for(Army a:armies.values())if(a.marching){
            double dx=a.rallyX-a.locationX,dz=a.rallyZ-a.locationZ,dist=Math.hypot(dx,dz);
            if(dist<=1.5){a.locationX=a.rallyX;a.locationZ=a.rallyZ;a.marching=false;}
            else{a.locationX+=dx/dist*1.5;a.locationZ+=dz/dist*1.5;}
            changed=true;
        }
        if(changed)setDirty();
    }
    public static MilitaryData load(CompoundTag root){
        MilitaryData d=new MilitaryData();
        for(Tag el:root.getList("Reserves",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)el;if(t.hasUUID("Faction"))d.reserves.put(t.getUUID("Faction"),Math.max(0,t.getInt("Count")));
        }
        for(Tag el:root.getList("Armies",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)el;if(!t.hasUUID("Id")||!t.hasUUID("Faction"))continue;
            Army a=new Army(t.getUUID("Id"),t.getUUID("Faction"),t.getString("Type"),t.getString("Name"),t.getString("Dimension"),new BlockPos(t.getInt("X"),t.getInt("Y"),t.getInt("Z")));
            a.strength=Math.max(0,t.getInt("Strength"));
            a.fielded=Math.max(0,t.getInt("Fielded"));a.uniform=Math.max(0,Math.min(3,t.getInt("Uniform")));
            String f=t.getString("Formation");a.formation=List.of("line","column","wedge").contains(f)?f:"column";
            if(t.contains("LocationX",Tag.TAG_DOUBLE))a.locationX=t.getDouble("LocationX");
            if(t.contains("LocationZ",Tag.TAG_DOUBLE))a.locationZ=t.getDouble("LocationZ");
            a.rallyX=t.contains("RallyX")?t.getInt("RallyX"):a.origin.getX();
            a.rallyZ=t.contains("RallyZ")?t.getInt("RallyZ"):a.origin.getZ();
            a.marching=t.getBoolean("Marching");d.armies.put(a.id,a);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("Schema",1);ListTag res=new ListTag();
        for(var entry:reserves.entrySet()){
            CompoundTag t=new CompoundTag();t.putUUID("Faction",entry.getKey());t.putInt("Count",entry.getValue());res.add(t);
        }root.put("Reserves",res);
        ListTag all=new ListTag();for(Army a:armies.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",a.id);t.putUUID("Faction",a.faction);t.putString("Type",a.type);t.putString("Name",a.name);
            t.putString("Dimension",a.dimension);t.putInt("X",a.origin.getX());t.putInt("Y",a.origin.getY());t.putInt("Z",a.origin.getZ());t.putInt("Strength",a.strength);t.putInt("Fielded",a.fielded);t.putInt("Uniform",a.uniform);
            t.putString("Formation",a.formation);t.putDouble("LocationX",a.locationX);t.putDouble("LocationZ",a.locationZ);
            t.putInt("RallyX",a.rallyX);t.putInt("RallyZ",a.rallyZ);t.putBoolean("Marching",a.marching);all.add(t);
        }root.put("Armies",all);return root;
    }
}
