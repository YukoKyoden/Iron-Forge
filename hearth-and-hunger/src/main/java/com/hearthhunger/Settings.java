package com.hearthhunger;
import java.nio.file.*;
import java.util.Properties;
import java.io.*;
import net.neoforged.fml.loading.FMLPaths;
final class Settings {
    static final Properties P=new Properties();
    static void load(){
        P.setProperty("longDay","true"); P.setProperty("activityHunger","true");
        P.setProperty("hungerScale","1.0"); P.setProperty("baseExhaustionPerTick","0.0045");
        P.setProperty("mealBuffs","true"); P.setProperty("mealDurationScale","1.0");
        P.setProperty("vanillaFoodNerf","true"); P.setProperty("vanillaNutritionMultiplier","0.88");
        P.setProperty("vanillaSaturationMultiplier","0.80"); P.setProperty("stackLimits","true");
        P.setProperty("replaceNourishment","true"); P.setProperty("hud","true");
        P.setProperty("hudOffsetX","0"); P.setProperty("hudOffsetY","0");
        P.setProperty("activityHoldSeconds","1.2");
        P.setProperty("extendedNaturalRegen","true"); P.setProperty("passiveRegenScale","1.0");
        P.setProperty("passiveHealExhaustion","1.5");
        P.setProperty("growl","true"); P.setProperty("performanceModifiers","true");
        P.setProperty("mealCooldownSeconds","30"); P.setProperty("gradualDigestion","true");
        P.setProperty("restRate","0.5"); P.setProperty("walkRate","1.0"); P.setProperty("workRate","1.5");
        P.setProperty("fightRate","2.5"); P.setProperty("swimRate","4.0"); P.setProperty("sprintRate","3.5");
        Path f=FMLPaths.CONFIGDIR.get().resolve("hearthhunger.properties");
        try {Files.createDirectories(f.getParent());if(Files.exists(f))try(InputStream in=Files.newInputStream(f)){P.load(in);}
            P.setProperty("sprintRate",Balance.migrateSprintRate(P.getProperty("sprintRate","3.5"),Boolean.parseBoolean(P.getProperty("sprintBalance011Migrated","false"))));
            P.setProperty("sprintBalance011Migrated","true");
            try(OutputStream out=Files.newOutputStream(f)){P.store(out,"Hearth & Hunger - restart after changes. Server and client should match food/stack options.");}
        }catch(IOException e){System.err.println("[Hearth & Hunger] Config: "+e);}
    }
    static boolean on(String k){return Boolean.parseBoolean(P.getProperty(k,"true"));}
    static double num(String k,double fallback,double min,double max){try{double d=Double.parseDouble(P.getProperty(k));return Double.isFinite(d)?Math.max(min,Math.min(max,d)):fallback;}catch(Exception e){return fallback;}}
    static Balance.Profile profile(String id,int nutrition){
        Balance.Profile defaults=Balance.profile(id,nutrition);
        String override=P.getProperty("food."+id);if(override==null)return defaults;
        try{String[] v=override.split(",");return new Balance.Profile(Balance.Tier.valueOf(v[0].trim().toUpperCase(java.util.Locale.ROOT)),Balance.Style.valueOf(v[1].trim().toUpperCase(java.util.Locale.ROOT)),Math.clamp(Integer.parseInt(v[2].trim()),1,64),defaults.special());}catch(Exception ex){return defaults;}
    }
    static double rate(Balance.Activity a){return num(a.name().toLowerCase(java.util.Locale.ROOT)+"Rate",a.rate,0,20);}
    static double scale(){return num("hungerScale",1,0,10);}
    static double base(){return num("baseExhaustionPerTick",.0045,.0001,.2);}
}
