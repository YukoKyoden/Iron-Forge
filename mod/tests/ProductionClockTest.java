import com.kingdomsclaims.ProductionClock;
public final class ProductionClockTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){check(ProductionClock.SECONDS==360&&ProductionClock.TICKS==7200,"six real server minutes");check(ProductionClock.VALIDATION_TICKS==1200,"validation remains separate");check(ProductionClock.upgradeDeadline(11200,1200)==17200,"old deadline extended to six minutes after last batch");check(ProductionClock.upgradeDeadline(17200,7200)==17200,"reload never extends migrated deadline again");check(ProductionClock.upgradeDeadline(Long.MAX_VALUE,1200)==Long.MAX_VALUE,"deadline math cannot overflow");int batches=0;long next=ProductionClock.TICKS;for(int tick=0;tick<=72000;tick++){if(tick>=next){batches++;next=tick+ProductionClock.TICKS;}}check(batches==10,"ten unchanged batches per game hour");System.out.println("Six-minute production scheduling and upgrade regressions passed.");}
}
