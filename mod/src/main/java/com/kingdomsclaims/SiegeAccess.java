package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/**
 * Temporary, nonpersistent, server-authoritative siege BUILD permit.
 * Call updateFromArmy(...) repeatedly with ACTUAL troop strength in the enemy claim.
 * 10+ refreshes the permit; fewer than 10 revoke it. Never grants claim ownership.
 * If the army system is absent, no siege permits exist except the explicit OP test command.
 */
public final class SiegeAccess {
    private SiegeAccess() {}
    private record Permit(UUID attacker,long expires) {}
    private static final Map<MinecraftServer, Map<String,Permit>> PERMITS = new WeakHashMap<>();
    private static String key(String dim,int cx,int cz,UUID attacker) {
        return KingdomData.key(dim,cx,cz)+"|"+attacker;
    }
    public static void updateFromArmy(ServerLevel level,UUID attacker,BlockPos target,int troopsPresent) {
        MinecraftServer server=level.getServer();KingdomData d=KingdomData.get(server);
        int cx=target.getX()>>4,cz=target.getZ()>>4;String dim=KingdomData.dim(level);
        String key=key(dim,cx,cz,attacker);
        Map<String,Permit> p=PERMITS.computeIfAbsent(server,s->new HashMap<>());
        KingdomData.Kingdom enemy=d.ownerAt(dim,cx,cz);
        if(troopsPresent<10||d.claimAt(dim,cx,cz)==null||(enemy!=null&&enemy.id.equals(attacker))||!d.kingdoms.containsKey(attacker)) {
            p.remove(key);return;
        }
        p.put(key,new Permit(attacker,level.getGameTime()+100L));
    }
    public static boolean activeNear(ServerLevel level,BlockPos pos,int chunkRadius) {
        var permits=PERMITS.get(level.getServer());if(permits==null)return false;
        String dim=KingdomData.dim(level);int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        for(int x=cx-chunkRadius;x<=cx+chunkRadius;x++)for(int z=cz-chunkRadius;z<=cz+chunkRadius;z++) {
            String prefix=KingdomData.key(dim,x,z)+"|";
            for(var e:permits.entrySet())if(e.getKey().startsWith(prefix)&&e.getValue().expires>=level.getGameTime())return true;
        }return false;
    }
    public static boolean mayBuild(ServerLevel level,UUID attacker,BlockPos target) {
        var permits=PERMITS.get(level.getServer());if(permits==null)return false;
        Permit p=permits.get(key(KingdomData.dim(level),target.getX()>>4,target.getZ()>>4,attacker));
        return p!=null&&p.expires>=level.getGameTime();
    }
}
