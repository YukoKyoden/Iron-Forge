package com.hearthhunger;
public final class BalanceTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        check(Balance.movement(18)==.10,"9 bars boost");check(Balance.movement(12)==0,"6 bars normal");
        check(Balance.movement(8)==-.05,"4 bars penalty");check(Balance.movement(6)==-.10,"3 bars penalty");
        check(Math.abs(1/(1+Balance.attack(6))-1.5)<1e-9,"cooldown 1.5x");check(Balance.attack(7)==0,"no early attack penalty");
        check(!Balance.mealActive(6,100)&&Balance.mealActive(7,100)&&!Balance.mealActive(20,0),"buff thresholds");
        check(Balance.profile("minecraft:cooked_beef",8).stack()==8,"steak stack");
        check(Balance.profile("minecraft:cooked_beef",8).tier()==Balance.Tier.BASIC,"vanilla no buffs");
        check(Balance.profile("minecraft:golden_apple",4).special(),"special food unchanged");
        check(Balance.profile("farmersdelight:beef_stew",10).tier()==Balance.Tier.FULL,"stew full meal");
        check(Balance.profile("farmersdelight:chicken_sandwich",8).tier()==Balance.Tier.LIGHT,"sandwich light");
        check(Balance.profile("farmersdelight:pasta_with_meatballs",12).tier()==Balance.Tier.HEARTY,"pasta hearty");
        check(Balance.profile("othermod:unknown_fruit",4).tier()==Balance.Tier.BASIC,"unknown food safe");
        check(Balance.profile("othermod:jerky",3).stack()==32,"snack stack");
        double rest=Balance.estimateSeconds(20,2,0,0,.5,.0045),sprint=Balance.estimateSeconds(20,2,0,0,5,.0045);
        check(Math.abs(rest/sprint-10)<1e-9,"activity countdown ratio");
        check(Balance.estimateSeconds(20,2,0,3,1,.0045)>Balance.estimateSeconds(20,2,0,0,1,.0045),"meal reserve extends timer");
        check(Balance.estimateSeconds(12,20,0,3,1,.0045)==0,"threshold countdown");
        check(Balance.reducedNutrition(8,.88)==7&&Balance.reducedNutrition(1,.88)==1,"vanilla nerf");
        check(Balance.estimateWithVanilla(20,2,0,0,5,.0045,.028)<sprint,"vanilla movement included");
        check(Balance.estimateWithVanilla(20,2,0,3,5,.0045,.028)>Balance.estimateWithVanilla(20,2,0,0,5,.0045,.028),"reserve under sprinting");
        int advanced=0;for(int i=0;i<38400;i++)if(Balance.advanceDay(i%8))advanced++;
        check(advanced==24000,"32 minute day");
        System.out.println("PASS: "+checks+" balance assertions");
    }
}
