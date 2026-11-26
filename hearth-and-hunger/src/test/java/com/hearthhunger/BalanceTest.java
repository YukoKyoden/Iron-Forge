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
        Balance.ActivityHold h=new Balance.ActivityHold();
        check(h.update(Balance.Activity.SPRINT,24)==Balance.Activity.SPRINT,"sprint immediate");
        for(int i=0;i<23;i++)check(h.update(i%2==0?Balance.Activity.WALK:Balance.Activity.REST,24)==Balance.Activity.SPRINT,"hold through sprint jitter "+i);
        check(h.update(Balance.Activity.SPRINT,24)==Balance.Activity.SPRINT,"sprint resumes");
        for(int i=0;i<23;i++)h.update(Balance.Activity.WALK,24);
        check(h.update(Balance.Activity.WALK,24)==Balance.Activity.WALK,"release after 1.2s");
        check(h.update(Balance.Activity.FIGHT,24)==Balance.Activity.FIGHT,"higher effort immediate");
        check(h.update(Balance.Activity.REST,0)==Balance.Activity.REST,"immediate rest when seated");
        check(Balance.passiveHealInterval(6)==0&&Balance.passiveHealInterval(0)==0,"no passive heal at three bars");
        check(Balance.passiveHealInterval(18)==0&&Balance.passiveHealInterval(20)==0,"vanilla owns high-hunger heal");
        check(Balance.passiveHealInterval(17)==120,"8.5 bars six seconds");
        check(Balance.passiveHealInterval(12)==270,"six bars 13.5 seconds");
        check(Balance.passiveHealInterval(7)==420,"3.5 bars 21 seconds");
        check(Balance.passiveHealProgress(12,.5,1)>Balance.passiveHealProgress(12,0,1),"meal accelerates passive regen");
        check(Balance.passiveHealProgress(6,1,1)==0&&Balance.passiveHealProgress(12,0,0)==0,"regen disabled when low or configured off");
        System.out.println("PASS: "+checks+" balance assertions");
    }
}
