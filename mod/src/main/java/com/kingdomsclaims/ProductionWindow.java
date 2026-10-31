package com.kingdomsclaims;
import java.util.*;
/** Actual delivered goods in the last sixty server seconds; deposits are never recorded here. */
public final class ProductionWindow {
    public record Sample(long second,int good,int amount){}
    private final NavigableMap<Long,int[]> buckets=new TreeMap<>();
    public void add(long second,int good,int amount){
        if(second<0||good<0||good>=8||amount<=0)return;
        buckets.headMap(Math.max(0,second-59),false).clear();
        int[] bucket=buckets.computeIfAbsent(second,k->new int[8]);bucket[good]=(int)Math.min(Integer.MAX_VALUE,(long)bucket[good]+amount);
        while(buckets.size()>60)buckets.pollFirstEntry();
    }
    public int total(long now,int good){if(good<0||good>=8)return 0;long total=0;for(var e:buckets.subMap(Math.max(0,now-59),true,Math.max(0,now),true).entrySet())total+=e.getValue()[good];return (int)Math.min(Integer.MAX_VALUE,total);}
    public List<Sample> samples(){List<Sample> list=new ArrayList<>();for(var e:buckets.entrySet())for(int i=0;i<8;i++)if(e.getValue()[i]>0)list.add(new Sample(e.getKey(),i,e.getValue()[i]));return list;}
}
