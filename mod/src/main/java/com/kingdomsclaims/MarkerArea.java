package com.kingdomsclaims;
/** Shared survey bounds and wood balance; no world access. */
public final class MarkerArea {
    public static final int RADIUS=7,WOOD_HEIGHT=35,MINE_DEPTH=35;
    public static boolean containsWood(int mx,int my,int mz,int x,int y,int z){return Math.abs((long)x-mx)<=RADIUS&&Math.abs((long)z-mz)<=RADIUS&&y>my&&(long)y-my<=WOOD_HEIGHT;}
    public static int woodOutput(int logs,int leaves){return logs>=8&&leaves>=8?Math.min(32,logs/8):0;}
}
