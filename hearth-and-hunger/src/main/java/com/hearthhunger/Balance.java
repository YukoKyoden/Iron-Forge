package com.hearthhunger;
import java.util.Locale;
public final class Balance {
    private Balance() {}
    public enum Activity {
        REST("Resting", .5, 0x82d77c), WALK("Walking", 1, 0xb6d97a),
        WORK("Working", 1.5, 0xe6d071), FIGHT("Fighting", 2.5, 0xe7a153),
        SWIM("Swimming", 4, 0xe77e54), SPRINT("Sprinting", 3.5, 0xf06c65);
        public final String label; public final double rate; public final int color;
        Activity(String l,double r,int c){label=l;rate=r;color=c;}
    }
    public enum Tier {
        BASIC("Basic food",0,0,0,0,0), LIGHT("Light meal",5,.05,0,0,1.5),
        FULL("Full meal",10,.25,.05,0,3), HEARTY("Hearty meal",14,.5,.05,.10,4),
        FEAST("Feast",18,.5,.10,.10,5);
        public final String label; public final int minutes; public final double heal,speed,mining,reserve;
        Tier(String l,int m,double h,double s,double b,double r){label=l;minutes=m;heal=h;speed=s;mining=b;reserve=r;}
    }
    public enum Style {
        NONE("",0,0,0,0,0), MEAT("Meat",.10,0,0,0,0), VEGETABLE("Vegetable",0,0,.25,0,0),
        GRAIN("Grain",0,0,0,.20,0), FISH("Fish",0,.10,0,0,0), BALANCED("Balanced",0,0,0,.15,.10);
        public final String label; public final double damage,attack,heal,saving,mining;
        Style(String l,double d,double a,double h,double s,double m){label=l;damage=d;attack=a;heal=h;saving=s;mining=m;}
    }
    public record Profile(Tier tier,Style style,int stack,boolean special){}
    static boolean has(String path,String... keys){for(String k:keys)if(path.contains(k))return true;return false;}
    public static Profile profile(String id,int nutrition){
        String p=id.toLowerCase(Locale.ROOT),n=p.substring(p.indexOf(':')+1);
        boolean vanilla=p.startsWith("minecraft:");
        boolean special=has(n,"golden_apple","golden_carrot","chorus_fruit","pufferfish","suspicious_stew","rotten_flesh","spider_eye","poisonous_potato");
        if(special)return new Profile(Tier.BASIC,Style.NONE,64,true);
        if(has(n,"jerky","dried_","cookie","berries","raisins","trail_mix"))return new Profile(Tier.BASIC,Style.NONE,32,false);
        if(vanilla){
            int stack=has(n,"cooked_")?8:16;
            if(has(n,"carrot","potato","beetroot","melon_slice","kelp"))stack=64;
            if(n.equals("mushroom_stew")||n.equals("rabbit_stew")||n.equals("beetroot_soup"))stack=1;
            return new Profile(Tier.BASIC,Style.NONE,stack,false);
        }
        Tier tier=Tier.BASIC;
        if(has(n,"roast_chicken","stuffed_pumpkin","honey_glazed_ham","shepherds_pie","feast","roast_dinner"))tier=Tier.FEAST;
        else if(has(n,"pasta","risotto","fried_rice","steak_and_potatoes","roasted_mutton_chops","rice_with","dumplings","curry"))tier=Tier.HEARTY;
        else if(has(n,"stew","soup","salad","noodle","ratatouille","meatball","cod_roll","salmon_roll","cooked_rice","pie_slice"))tier=Tier.FULL;
        else if(has(n,"sandwich","wrap","burger","toast","omelet","omelette","taco","burrito"))tier=Tier.LIGHT;
        Style style=Style.NONE;
        if(tier!=Tier.BASIC){
            if(has(n,"fish","cod","salmon","tuna","shrimp","sushi"))style=Style.FISH;
            else if(has(n,"pasta","rice","noodle","bread","dumpling"))style=Style.GRAIN;
            else if(has(n,"beef","meat","pork","chicken","mutton","bacon","ham"))style=Style.MEAT;
            else if(has(n,"vegetable","salad","mushroom","ratatouille","pumpkin"))style=Style.VEGETABLE;
            else style=Style.BALANCED;
        }
        return new Profile(tier,style,tier==Tier.FEAST?1:tier==Tier.BASIC?64:tier==Tier.LIGHT?8:4,false);
    }
    public static double movement(int food){return food>=18?.10:food>=12?0:food>=8?-.05:food>0?-.10:-.20;}
    public static double mining(int food){return food>=18?.10:0;}
    public static double attack(int food){return food<=6?-1.0/3:0;}
    public static boolean mealActive(int food,int ticks){return food>6&&ticks>0;}
    public static double rate(Activity a,Style style,boolean active,double scale){return a.rate*(active?1-style.saving:1)*scale;}
    public static double estimateSeconds(int food,float saturation,float exhaustion,double reserve,double rate,double base){
        if(food<=12)return 0;
        if(rate<=0||base<=0)return Double.POSITIVE_INFINITY;
        double units=Math.max(0,(food-12+Math.ceil(Math.max(0,saturation)))*4-exhaustion);
        return reserve*60/rate+units/(base*rate*20);
    }
    public static double estimateWithVanilla(int food,float saturation,float exhaustion,double reserve,double rate,double base,double vanillaPerTick){
        if(food<=12)return 0;
        if(rate<=0||base<=0)return Double.POSITIVE_INFINITY;
        double units=Math.max(0,(food-12+Math.ceil(Math.max(0,saturation)))*4-exhaustion);
        double reserveSeconds=reserve*60/rate,vanillaPerSecond=Math.max(0,vanillaPerTick)*20;
        if(vanillaPerSecond>0&&units<=reserveSeconds*vanillaPerSecond)return units/vanillaPerSecond;
        return reserveSeconds+Math.max(0,units-reserveSeconds*vanillaPerSecond)/(base*rate*20+vanillaPerSecond);
    }
    public static int reducedNutrition(int n,double multiplier){return n<=1?n:Math.max(1,(int)Math.round(n*multiplier));}
    /** Higher activity is immediate; a continuous quiet window is required to step down. */
    public static final class ActivityHold {
        private Activity current=Activity.REST;
        private int quietTicks;
        public Activity update(Activity raw,int holdTicks){
            if(raw==current){quietTicks=0;return current;}
            if(raw.ordinal()>current.ordinal()||holdTicks<=0||++quietTicks>=holdTicks){
                current=raw;quietTicks=0;
            }
            return current;
        }
    }
    /** One half-heart per interval; 18+ food points use native healing instead. */
    public static int passiveHealInterval(int food){
        if(food<=6||food>=18)return 0;
        return 120+(17-food)*30;
    }
    public static double passiveHealProgress(int food,double bonus,double scale){
        int interval=passiveHealInterval(food);
        return interval==0?0:(1+Math.max(0,bonus))*Math.max(0,scale)/interval;
    }
    /** Upgrade the previous default only, leaving other explicit settings alone. */
    public static String migrateSprintRate(String configured,boolean alreadyMigrated){
        if(!alreadyMigrated){
            try{if(Double.parseDouble(configured)==5.0)return "3.5";}catch(Exception ignored){}
        }
        return configured;
    }
    public static boolean advanceDay(int phase){return phase<5;}
}
