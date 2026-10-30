package com.kingdomsclaims;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One persistent, server-authoritative resource balance per faction. All writes run on the server thread. */
public final class SharedStockpile extends SavedData {
    private final Map<UUID,Map<String,Integer>> balances=new HashMap<>();
    private MinecraftServer server;
    public static UUID owner(MinecraftServer server,UUID id){
        var t=KingdomData.get(server).towns.get(id);
        return t!=null&&t.parentKingdom!=null&&KingdomData.get(server).kingdoms.containsKey(t.parentKingdom)?t.parentKingdom:id;
    }
    private UUID resolve(UUID id){return server==null?id:owner(server,id);}
    public static boolean validGood(String good){return Arrays.asList(OperationsData.GOODS).contains(good);}

    public static SharedStockpile get(MinecraftServer server){
        SharedStockpile s=server.overworld().getDataStorage().computeIfAbsent(SharedStockpile::load,SharedStockpile::new,"kingdomsclaims_shared_stockpile");s.server=server;return s;
    }
    public static SharedStockpile load(CompoundTag root){
        SharedStockpile d=new SharedStockpile();
        for(Tag tag:root.getList("Factions",Tag.TAG_COMPOUND)){
            CompoundTag entry=(CompoundTag)tag;
            if(!entry.hasUUID("Id"))continue;
            Map<String,Integer> goods=new HashMap<>();CompoundTag stored=entry.getCompound("Goods");
            for(String name:OperationsData.GOODS)goods.put(name,Math.max(0,stored.getInt(name)));
            d.balances.put(entry.getUUID("Id"),goods);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root){
        ListTag list=new ListTag();
        for(var entry:balances.entrySet()){
            CompoundTag tag=new CompoundTag(),goods=new CompoundTag();tag.putUUID("Id",entry.getKey());
            for(String name:OperationsData.GOODS)goods.putInt(name,entry.getValue().getOrDefault(name,0));
            tag.put("Goods",goods);list.add(tag);
        }
        root.put("Factions",list);return root;
    }
    /** Pool only an independent town's own balance, never its former parent's kingdom stock. */
    public boolean mergeTown(UUID town,UUID kingdom){
        var source=balances.get(town);if(source==null||town.equals(kingdom))return true;
        var target=balances.computeIfAbsent(kingdom,k->new HashMap<>());
        for(String g:OperationsData.GOODS)if((long)source.getOrDefault(g,0)+target.getOrDefault(g,0)>Integer.MAX_VALUE)return false;
        for(String g:OperationsData.GOODS)target.merge(g,source.getOrDefault(g,0),Integer::sum);
        balances.remove(town);setDirty();return true;
    }
    public int amount(UUID owner,String good){owner=resolve(owner);return Math.max(0,balances.getOrDefault(owner,Map.of()).getOrDefault(good,0));}
    public int used(UUID owner){long sum=0;for(String good:OperationsData.GOODS)sum+=amount(owner,good);return (int)Math.min(Integer.MAX_VALUE,sum);}
    public int add(UUID owner,String good,int amount,int maxTotal){owner=resolve(owner);
        if(owner==null||amount<=0||!Arrays.asList(OperationsData.GOODS).contains(good))return 0;
        int existing=amount(owner,good);
        int accepted=StockpileMath.accepted(existing,used(owner),amount,maxTotal);
        if(accepted>0){balances.computeIfAbsent(owner,id->new HashMap<>()).put(good,existing+accepted);setDirty();}
        return accepted;
    }
    public boolean canPay(UUID owner,Map<String,Integer> required){owner=resolve(owner);if(owner==null)return false;
        for(var entry:required.entrySet())if(!validGood(entry.getKey())||entry.getValue()<0||amount(owner,entry.getKey())<entry.getValue())return false;
        return true;
    }
    public boolean payWithGoldChange(UUID id,Map<String,Integer> costs,int change){
        UUID owner=resolve(id);if(change<0||change>8||!canPay(owner,costs))return false;
        long after=(long)amount(owner,"GOLD_NUGGETS")-costs.getOrDefault("GOLD_NUGGETS",0)+change;
        if(after>Integer.MAX_VALUE)return false;
        if(!pay(owner,costs))return false;
        // Change returns existing gold value, even if a warehouse was removed and storage is over capacity.
        balances.computeIfAbsent(owner,k->new HashMap<>()).put("GOLD_NUGGETS",(int)after);setDirty();return true;
    }
    public boolean pay(UUID owner,Map<String,Integer> required){owner=resolve(owner);
        if(!canPay(owner,required))return false;
        if(!StockpileMath.pay(balances.computeIfAbsent(owner,k->new HashMap<>()),required))return false;
        if(required.values().stream().anyMatch(v->v>0))setDirty();
        return true;
    }
}
