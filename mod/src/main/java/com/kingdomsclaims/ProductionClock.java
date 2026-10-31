package com.kingdomsclaims;
/** One resource batch every six minutes; validation has its own one-minute clock. */
public final class ProductionClock {
    private ProductionClock(){}
    public static final int SECONDS=360,TICKS=SECONDS*20,VALIDATION_TICKS=1200;
    public static long upgradeDeadline(long deadline,int previousTicks){long delta=Math.max(0,(long)TICKS-Math.max(20,previousTicks));return Math.min(Long.MAX_VALUE-delta,Math.max(0,deadline))+delta;}
}
