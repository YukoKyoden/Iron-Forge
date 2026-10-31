package com.kingdomsclaims;
/** Size-based house construction supplies, independent of decorative block choices. */
public final class HouseBuildCost {
    private HouseBuildCost(){}
    public record Cost(int wood,int iron,int gold){}
    public static Cost forBlocks(int blocks){if(blocks<=0)return new Cost(0,0,0);int n=Math.min(4096,blocks);return new Cost((n+3)/4,(n+31)/32,(n+255)/256);}
}
