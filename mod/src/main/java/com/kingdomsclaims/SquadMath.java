package com.kingdomsclaims;
public final class SquadMath {
    public static int weight(int troops,int slots){return troops<=0||slots<=0?0:Math.min(10,Math.max(1,(int)Math.ceil(troops/(double)slots)));}
}
