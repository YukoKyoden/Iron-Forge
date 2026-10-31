package com.kingdomsclaims;
import java.text.NumberFormat;
import java.util.Locale;
/** Exact quantities for production rates stored as half-items. Stored inventory remains whole items. */
public final class ProductionText {
    public static final String[] NAMES={"Iron","Coal","Wood","Wheat","Stone","Emeralds","Gold ingots","Gold nuggets"};
    public static String count(int n){return NumberFormat.getIntegerInstance(Locale.US).format(Math.max(0,n));}
    public static String quantity(int halves){return count(Math.max(0,halves)/2)+(Math.max(0,halves)%2==1?".5":"");}
    public static String rate(int halves,int seconds){return "+"+quantity(halves)+" every "+seconds/60+" minutes";}
    public static String time(int seconds){int n=Math.max(0,seconds);return n/60+":"+String.format(Locale.ROOT,"%02d",n%60);}
    public static String name(String type){return switch(type){case "mine"->"Mine";case "lumber camp"->"Woodcutter";case "farm"->"Wheat farm";case "house"->"House";case "warehouse"->"Warehouse";case "barracks"->"Barracks";default->type;};}
    public static String building(String type,int capacity,boolean active){if(!active)return name(type)+" - Paused";return name(type)+": "+switch(type){case "mine"->quantity(capacity)+" iron / 6 min";case "lumber camp"->count(capacity)+" wood / 6 min";case "farm"->count(capacity)+" wheat / 6 min";case "house"->count(capacity)+" beds";case "warehouse"->count(capacity)+" storage spaces";case "barracks"->count(capacity)+" troop capacity";default->count(capacity);};}
}
