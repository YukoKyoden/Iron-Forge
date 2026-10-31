package com.kingdomsclaims;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
public final class ProductionData extends SavedData {
    public final Map<UUID,ProductionWindow> windows=new HashMap<>();
    public static ProductionData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(ProductionData::load,ProductionData::new,"kingdomsclaims_production");}
    public static void record(MinecraftServer s,UUID owner,String good,int accepted){if(accepted<=0)return;int index=Arrays.asList(OperationsData.GOODS).indexOf(good);if(index<0)return;var data=get(s);data.windows.computeIfAbsent(SharedStockpile.owner(s,owner),id->new ProductionWindow()).add(s.overworld().getGameTime()/20,index,accepted);data.setDirty();}
    public int[] recent(MinecraftServer s,UUID owner){int[] out=new int[8];var w=windows.get(SharedStockpile.owner(s,owner));if(w!=null)for(int i=0;i<8;i++)out[i]=w.total(s.overworld().getGameTime()/20,i);return out;}
    public static ProductionData load(CompoundTag root){ProductionData d=new ProductionData();for(Tag tag:root.getList("Kingdoms",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;if(!row.hasUUID("Owner"))continue;ProductionWindow w=new ProductionWindow();for(Tag sample:row.getList("Samples",Tag.TAG_COMPOUND)){var t=(CompoundTag)sample;w.add(t.getLong("Second"),t.getInt("Good"),t.getInt("Amount"));}d.windows.put(row.getUUID("Owner"),w);}return d;}
    @Override public CompoundTag save(CompoundTag root){ListTag kingdoms=new ListTag();for(var e:windows.entrySet()){CompoundTag row=new CompoundTag();row.putUUID("Owner",e.getKey());ListTag samples=new ListTag();for(var s:e.getValue().samples()){CompoundTag t=new CompoundTag();t.putLong("Second",s.second());t.putInt("Good",s.good());t.putInt("Amount",s.amount());samples.add(t);}row.put("Samples",samples);kingdoms.add(row);}root.put("Kingdoms",kingdoms);return root;}
}
