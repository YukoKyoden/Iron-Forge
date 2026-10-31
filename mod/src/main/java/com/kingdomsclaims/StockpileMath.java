package com.kingdomsclaims;
import java.util.Map;
/** Pure transaction rules shared by the saved ledger and standalone regression tests. */
public final class StockpileMath {
    private StockpileMath(){}
    public static int accepted(int current,long used,int requested,int capacity){if(current<0||used<0||requested<=0)return 0;return (int)Math.max(0,Math.min((long)requested,Math.min((long)Integer.MAX_VALUE-current,(long)capacity-used)));}
    public static boolean canPay(Map<String,Integer> balance,Map<String,Integer> costs){for(var e:costs.entrySet())if(e.getValue()==null||e.getValue()<0||balance.getOrDefault(e.getKey(),0)<e.getValue())return false;return true;}
    public static boolean pay(Map<String,Integer> balance,Map<String,Integer> costs){if(!canPay(balance,costs))return false;for(var e:costs.entrySet())if(e.getValue()>0)balance.put(e.getKey(),balance.getOrDefault(e.getKey(),0)-e.getValue());return true;}
    public static Map<String,Integer> goldPayment(int ingots,int nuggets,int due){if(ingots<0||nuggets<0||due<0||9L*ingots+nuggets<due)return null;int take=Math.min(nuggets,due);int bars=(int)(((long)due-take+8)/9);return Map.of("GOLD",bars,"GOLD_NUGGETS",take,"CHANGE",bars*9-(due-take));}
}
