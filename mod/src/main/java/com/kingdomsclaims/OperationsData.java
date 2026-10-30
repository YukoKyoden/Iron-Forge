package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persisted operation locations, competing occupiers, caravans and delivered faction inventories. */
public final class OperationsData extends SavedData {
    public static final String[] TYPES={"MINE","FOREST","FARM","QUARRY","RUINS"};
    public static final String[] GOODS={"IRON","COAL","LOGS","WHEAT","STONE","EMERALDS","GOLD","GOLD_NUGGETS"};
    public static final int SITE_CAP=5000;
    public static final class Site {
        public UUID id=UUID.randomUUID(), owner, army, attacker, attackerFaction;
        public String dim="",type="MINE",stage="IDLE";
        public int x,z,total=100,progress=0,stock=0,workingSeconds=0,raidSeconds=0,outpostSeconds=0,passiveSeconds=0;
        public final Set<UUID> scouted=new HashSet<>();
        public String good(){return switch(type){case "MINE"->"IRON";case "FOREST"->"LOGS";case "FARM"->"WHEAT";case "QUARRY"->"STONE";default->"EMERALDS";};}
        public int duration(){return switch(type){case "RUINS"->90;case "MINE"->180;case "FOREST"->150;case "FARM"->120;default->140;};}
    }
    public static final class Caravan {
        public UUID id=UUID.randomUUID(), faction, destinationFaction, army, attacker, attackerFaction;
        public String dim="",good="IRON",stage="OUTBOUND";
        public int amount,x,z,raidSeconds;
        public double locationX,locationZ;
    }
    public final Map<UUID,Site> sites=new LinkedHashMap<>();
    public final Map<UUID,Caravan> caravans=new LinkedHashMap<>();
    public static OperationsData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(OperationsData::load,OperationsData::new,"kingdomsclaims_operations");}
    public int warehouse(UUID faction,String good){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();return server==null?0:EconomyService.balance(server,faction,good);}
    public void credit(UUID faction,String good,int amount){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server!=null)EconomyService.credit(server,faction,good,amount);}
    public boolean debit(UUID faction,String good,int amount){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();return server!=null&&EconomyService.debit(server,faction,good,amount);}
    public boolean committed(UUID id){if(id==null)return false;for(Site s:sites.values())if(id.equals(s.army)||id.equals(s.attacker))return true;for(Caravan c:caravans.values())if(id.equals(c.army)||id.equals(c.attacker))return true;return false;}
    public int outposts(UUID faction){int n=0;for(Site s:sites.values())if("OUTPOST".equals(s.stage)&&faction.equals(s.owner))n++;return n;}
    public Site nearest(String dim,int x,int z,int radius){Site nearest=null;double d=radius*radius;for(Site s:sites.values())if(s.dim.equals(dim)){double q=(double)(x-s.x)*(x-s.x)+(double)(z-s.z)*(z-s.z);if(q<d){d=q;nearest=s;}}return nearest;}
    public static OperationsData load(CompoundTag root){
        OperationsData d=new OperationsData();
        for(Tag entry:root.getList("Sites",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;Site s=new Site();if(!t.hasUUID("Id"))continue;s.id=t.getUUID("Id");s.dim=t.getString("Dim");s.type=t.getString("Type");s.stage=t.getString("Stage");s.x=t.getInt("X");s.z=t.getInt("Z");s.total=Math.max(1,t.getInt("Total"));s.progress=Math.max(0,Math.min(100,t.getInt("Progress")));s.stock=Math.max(0,t.getInt("Stock"));s.workingSeconds=t.getInt("Worked");s.raidSeconds=t.getInt("Raid");s.outpostSeconds=t.getInt("OutpostSeconds");s.passiveSeconds=Math.max(0,Math.min(59,t.getInt("PassiveSeconds")));
            if(t.hasUUID("Owner"))s.owner=t.getUUID("Owner");if(t.hasUUID("Army"))s.army=t.getUUID("Army");if(t.hasUUID("Attacker"))s.attacker=t.getUUID("Attacker");if(t.hasUUID("AttackerFaction"))s.attackerFaction=t.getUUID("AttackerFaction");
            for(Tag id:t.getList("Scouted",Tag.TAG_INT_ARRAY)){int[] a=((IntArrayTag)id).getAsIntArray();if(a.length==4){CompoundTag u=new CompoundTag();u.putIntArray("U",a);int[] v=a;s.scouted.add(new UUID(((long)v[0]<<32)|(v[1]&0xffffffffL),((long)v[2]<<32)|(v[3]&0xffffffffL)));}}
            d.sites.put(s.id,s);
        }
        for(Tag entry:root.getList("Caravans",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;Caravan c=new Caravan();if(!t.hasUUID("Id"))continue;c.id=t.getUUID("Id");c.dim=t.getString("Dim");c.good=t.getString("Good");c.stage=t.getString("Stage");c.amount=t.getInt("Amount");c.x=t.getInt("X");c.z=t.getInt("Z");c.locationX=t.getDouble("LocationX");c.locationZ=t.getDouble("LocationZ");c.raidSeconds=t.getInt("Raid");
            if(t.hasUUID("Faction"))c.faction=t.getUUID("Faction");if(t.hasUUID("Destination"))c.destinationFaction=t.getUUID("Destination");if(t.hasUUID("Army"))c.army=t.getUUID("Army");if(t.hasUUID("Attacker"))c.attacker=t.getUUID("Attacker");if(t.hasUUID("AttackerFaction"))c.attackerFaction=t.getUUID("AttackerFaction");d.caravans.put(c.id,c);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("Schema",1);ListTag siteList=new ListTag();for(Site s:sites.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",s.id);t.putString("Dim",s.dim);t.putString("Type",s.type);t.putString("Stage",s.stage);t.putInt("X",s.x);t.putInt("Z",s.z);t.putInt("Total",s.total);t.putInt("Progress",s.progress);t.putInt("Stock",s.stock);t.putInt("Worked",s.workingSeconds);t.putInt("Raid",s.raidSeconds);t.putInt("OutpostSeconds",s.outpostSeconds);t.putInt("PassiveSeconds",s.passiveSeconds);
            if(s.owner!=null)t.putUUID("Owner",s.owner);if(s.army!=null)t.putUUID("Army",s.army);if(s.attacker!=null)t.putUUID("Attacker",s.attacker);if(s.attackerFaction!=null)t.putUUID("AttackerFaction",s.attackerFaction);
            ListTag scouts=new ListTag();for(UUID u:s.scouted){scouts.add(new IntArrayTag(new int[]{(int)(u.getMostSignificantBits()>>>32),(int)u.getMostSignificantBits(),(int)(u.getLeastSignificantBits()>>>32),(int)u.getLeastSignificantBits()}));}t.put("Scouted",scouts);siteList.add(t);
        }root.put("Sites",siteList);
        ListTag travel=new ListTag();for(Caravan c:caravans.values()){
            CompoundTag t=new CompoundTag();t.putUUID("Id",c.id);t.putString("Dim",c.dim);t.putString("Good",c.good);t.putString("Stage",c.stage);t.putInt("Amount",c.amount);t.putInt("X",c.x);t.putInt("Z",c.z);t.putDouble("LocationX",c.locationX);t.putDouble("LocationZ",c.locationZ);t.putInt("Raid",c.raidSeconds);
            if(c.faction!=null)t.putUUID("Faction",c.faction);if(c.destinationFaction!=null)t.putUUID("Destination",c.destinationFaction);if(c.army!=null)t.putUUID("Army",c.army);if(c.attacker!=null)t.putUUID("Attacker",c.attacker);if(c.attackerFaction!=null)t.putUUID("AttackerFaction",c.attackerFaction);travel.add(t);
        }root.put("Caravans",travel);
        return root;
    }
}
