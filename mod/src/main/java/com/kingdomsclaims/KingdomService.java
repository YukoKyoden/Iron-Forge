package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Server-side validations. Never trust client ownership, costs, or coordinates. */
public final class KingdomService {
    private KingdomService() {}
    public static final int COBBLE=16,TOWN_IRON=20,RELOCATION_IRON=8;
    public static final long MOVE_COOLDOWN=20L*60*30;
    public static boolean authorized(ServerPlayer p,ServerLevel level,BlockPos pos,int permission){
        if(p.hasPermissions(2))return true;
        KingdomData d=KingdomData.get(level.getServer());KingdomData.Claim claim=d.claimAt(KingdomData.dim(level),pos.getX()>>4,pos.getZ()>>4);
        if(claim==null)return true;
        KingdomData.Town t=d.townFor(claim);
        if(t!=null){
            if(t.allowed(p.getUUID(),permission))return true;
            if(t.parentKingdom!=null){KingdomData.Kingdom k=d.kingdoms.get(t.parentKingdom);if(k!=null&&k.allowed(p.getUUID(),permission))return true;}
        }else{
            KingdomData.Kingdom k=d.ownerAt(KingdomData.dim(level),pos.getX()>>4,pos.getZ()>>4);
            if(k!=null&&k.allowed(p.getUUID(),permission))return true;
        }
        KingdomData.Kingdom attack=d.ofPlayer(p.getUUID());
        return (permission==KingdomData.BUILD||permission==KingdomData.BREAK) && attack!=null
            && SiegeAccess.mayBuild(level,attack.id,pos);
    }
    public static boolean canEdit(ServerPlayer p,ServerLevel level,BlockPos pos){return authorized(p,level,pos,KingdomData.BUILD);}
    public static boolean canClaim(ServerPlayer p,KingdomData.Kingdom k){return k!=null&&k.allowed(p.getUUID(),KingdomData.CLAIM);}
    public static boolean canTownClaim(ServerPlayer p,KingdomData.Town t){return t!=null&&t.allowed(p.getUUID(),KingdomData.CLAIM);}
    public static int ironCost(KingdomData d,UUID kingdom){return Math.min(2048,4+Math.max(0,d.kingdomClaims(kingdom)-25));}
    private static boolean bannerSpace(ServerLevel level,BlockPos pos){
        if(!level.canSeeSky(pos.above()))return false;
        if(!level.getBlockState(pos.below()).isSolidRender(level,pos.below()))return false;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=0;dy<=2;dy++){
            BlockPos p=pos.offset(dx,dy,dz);
            if(p.equals(pos))continue;
            if(!level.getBlockState(p).isAir())return false;
        }
        return true;
    }
    public static String validateBanner(ServerPlayer player,ServerLevel level,BlockPos pos,String kind){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        int cx=pos.getX()>>4,cz=pos.getZ()>>4;
        // The central banner block is already placed while EntityPlaceEvent fires; do not test it as air.
        if(!bannerSpace(level,pos))return "Banner needs a solid base, a clear 3x3 plaza, and open sky.";
        if(d.bannerAt(dim,pos)!=null)return "A banner already exists here.";
        if(d.claimAt(dim,cx,cz)!=null){
            // Expansion banners can be placed on existing friendly kingdom claims.
            if(!kind.equals("KINGDOM"))return "Town banner needs a free central chunk.";
        }
        if(kind.equals("TOWN")){
            KingdomData.Town t=d.townOf(player.getUUID());
            if(t==null||!t.allowed(player.getUUID(),KingdomData.BANNER))return "Create a town with /town create <name> first (Mayor can place banner).";
            if(d.townBanner(t.id)!=null)return "Town already has a banner.";
            if(d.claimAt(dim,cx,cz)!=null)return "That central chunk is already claimed.";
            return null;
        }
        KingdomData.Kingdom k=d.ofPlayer(player.getUUID());
        if(k==null||!k.allowed(player.getUUID(),KingdomData.BANNER))return "Only authorized kingdom leaders may place Kingdom Banners.";
        KingdomData.Banner cap=d.capital(k.id);
        if(cap!=null)return "Only one Kingdom Banner is allowed per kingdom. Relocate your capital instead.";
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++)if(d.claimAt(dim,x,z)!=null)
            return "The initial 5x5 capital area must be unclaimed.";
        return null;
    }
    public static void registerBanner(ServerPlayer player,ServerLevel level,BlockPos pos,String kind){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        if(kind.equals("TOWN")){
            KingdomData.Town t=d.townOf(player.getUUID());
            KingdomData.Banner b=new KingdomData.Banner(UUID.randomUUID(),null,"TOWN",dim,pos);
            b.town=t.id;d.banners.put(b.id,b);t.bannerId=b.id;
            d.put(new KingdomData.Claim(dim,b.cx,b.cz,b.id));
        }else{
            KingdomData.Kingdom k=d.ofPlayer(player.getUUID());KingdomData.Banner b=new KingdomData.Banner(UUID.randomUUID(),k.id,"KINGDOM",dim,pos);
            b.capital=true;d.banners.put(b.id,b);
            for(int x=b.cx-2;x<=b.cx+2;x++)for(int z=b.cz-2;z<=b.cz+2;z++)d.put(new KingdomData.Claim(dim,x,z,b.id));
        }
        d.setDirty();
        BannerStructure.complete(level,pos);
    }
    public static boolean claimable(KingdomData d,UUID owner,String dim,int cx,int cz,boolean town){
        if(d.claimAt(dim,cx,cz)!=null)return false;
        if(town){KingdomData.Banner b=d.townBanner(owner);return b!=null&&b.contains(dim,cx,cz)&&d.adjacent(dim,cx,cz,owner,true)&&d.townClaims(owner)<81;}
        return d.capital(owner)!=null&&d.adjacent(dim,cx,cz,owner,false);
    }
    public static String claim(ServerPlayer player,ServerLevel level){return claimAt(player,level,player.blockPosition().getX()>>4,player.blockPosition().getZ()>>4,false);}
    public static String claimAt(ServerPlayer p,ServerLevel level,int cx,int cz,boolean town){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        if(!near(p,cx,cz))return "Move within eight chunks of your target to claim it.";
        if(OperationsService.claimedSite(level.getServer(),dim,cx,cz))return "This wilderness chunk hosts an active contested operation or outpost; it cannot be claimed.";
        if(town){
            KingdomData.Town t=d.townOf(p.getUUID());
            if(!canTownClaim(p,t))return "Only town members with claim permission may expand their town.";
            if(!claimable(d,t.id,dim,cx,cz,true))return "Town claim invalid: must be adjacent, unowned, and inside its 9x9 boundary.";
            if(!pay(p,Items.COBBLESTONE,COBBLE,Items.IRON_INGOT,TOWN_IRON))return "Town claim requires 16 cobblestone and 20 iron ingots.";
            d.put(new KingdomData.Claim(dim,cx,cz,d.townBanner(t.id).id));return "Claimed town chunk "+cx+", "+cz+" for "+t.name+".";
        }
        KingdomData.Kingdom k=d.ofPlayer(p.getUUID());if(!canClaim(p,k))return "You need kingdom claim permission.";
        if(!claimable(d,k.id,dim,cx,cz,false))return "Kingdom claim invalid: must be adjacent to your territory and unowned.";
        int iron=ironCost(d,k.id);
        if(!pay(p,Items.COBBLESTONE,COBBLE,Items.IRON_INGOT,iron))return "Claim requires 16 cobblestone and "+iron+" iron ingots.";
        d.put(new KingdomData.Claim(dim,cx,cz,d.capital(k.id).id));return "Claimed kingdom chunk "+cx+", "+cz+" for "+k.name+" ("+iron+" iron).";
    }
    public static boolean near(ServerPlayer p,int cx,int cz){return Math.abs((p.blockPosition().getX()>>4)-cx)<=8&&Math.abs((p.blockPosition().getZ()>>4)-cz)<=8;}
    public static String unclaim(ServerPlayer p,ServerLevel level){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level),ignored="";
        int cx=p.blockPosition().getX()>>4,cz=p.blockPosition().getZ()>>4;
        KingdomData.Claim c=d.claimAt(dim,cx,cz);if(c==null)return "This chunk is already wilderness.";
        KingdomData.Banner b=d.bannerFor(c);
        if(b==null||b.town!=null)return "Town claims cannot be individually released.";
        KingdomData.Kingdom k=d.kingdoms.get(b.kingdom);
        if(k==null||!k.allowed(p.getUUID(),KingdomData.CLAIM))return "No claim permission.";
        for(KingdomData.Banner other:d.banners.values())if(other.dimension.equals(dim)&&other.cx==cx&&other.cz==cz)return "Cannot release a banner's central chunk.";
        // Do not disconnect a surviving claim network; prevents isolated unreachable land.
        d.delete(c);if(!connected(d,k.id,dim)) {d.put(c);return "Cannot unclaim: this would disconnect your kingdom's chunks.";}
        return "Released chunk "+cx+", "+cz+".";
    }
    private static boolean connected(KingdomData d,UUID owner,String dim){
        Set<String> cells=new HashSet<>();for(KingdomData.Claim c:d.claims.values())if(c.dimension.equals(dim)&&d.controlled(owner,dim,c.cx,c.cz)&&d.townFor(c)==null)cells.add(c.cx+","+c.cz);
        if(cells.isEmpty())return true;
        String start=cells.iterator().next();Set<String> seen=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();seen.add(start);queue.add(start);
        while(!queue.isEmpty()){String[] v=queue.remove().split(",");int x=Integer.parseInt(v[0]),z=Integer.parseInt(v[1]);for(int[] n:new int[][]{{x+1,z},{x-1,z},{x,z+1},{x,z-1}}){String key=n[0]+","+n[1];if(cells.contains(key)&&seen.add(key))queue.add(key);}}
        return seen.size()==cells.size();
    }
    public static boolean bannerProtected(ServerLevel level,BlockPos pos){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        for(KingdomData.Banner b:d.banners.values())if(b.dimension.equals(dim)){
            if(pos.equals(b.pos))return true;
            if(Math.abs(pos.getX()-b.pos.getX())<=1&&Math.abs(pos.getZ()-b.pos.getZ())<=1
                    &&pos.getY()>=b.pos.getY()&&pos.getY()<=b.pos.getY()+2)return true;
            if(pos.getX()==b.pos.getX()&&pos.getZ()==b.pos.getZ()&&pos.getY()>b.pos.getY())return true;
        }
        return false;
    }
    public static String relocate(ServerPlayer p,ServerLevel level,BlockPos source,BlockPos target){
        KingdomData d=KingdomData.get(level.getServer());String dim=KingdomData.dim(level);
        KingdomData.Banner b=d.bannerAt(dim,source);if(b==null)return "No registered banner at old position.";
        if(!near(p,target.getX()>>4,target.getZ()>>4))return "Stand within eight chunks of the relocation site.";
        if(b.town!=null){KingdomData.Town t=d.towns.get(b.town);if(t==null||!t.allowed(p.getUUID(),KingdomData.BANNER))return "Town banner permission required.";
            for(KingdomData.Claim c:d.claims.values())if(d.townControlled(t.id,c.dimension,c.cx,c.cz)&&(!c.dimension.equals(dim)||Math.abs(c.cx-(target.getX()>>4))>4||Math.abs(c.cz-(target.getZ()>>4))>4))return "Existing town claims would fall outside the new 9x9 boundary.";
        }else{KingdomData.Kingdom k=d.kingdoms.get(b.kingdom);if(k==null||!k.allowed(p.getUUID(),KingdomData.BANNER))return "Kingdom banner permission required.";
            if(!d.controlled(k.id,dim,target.getX()>>4,target.getZ()>>4))return "Relocate inside your kingdom's owned land.";
        }
        if(level.getGameTime()-b.lastMove<MOVE_COOLDOWN)return "Banner is on its 30-minute relocation cooldown.";
        if(SiegeAccess.activeNear(level,source,8)||SiegeAccess.activeNear(level,target,8))return "Cannot relocate during an active siege.";
        if(!level.getBlockState(target).isAir())return "Destination must be empty.";
        if(!bannerSpace(level,target))return "New site requires open sky, solid base and clear 3x3 plaza.";
        for(KingdomData.Banner other:d.banners.values())if(other!=b&&other.dimension.equals(dim)&&other.pos.equals(target))return "Another banner is there.";
        if(!level.getBlockState(source).is(b.town!=null?KingdomsClaims.TOWN_BANNER.get():KingdomsClaims.KINGDOM_BANNER.get()))return "Original banner block is missing; relocation canceled.";
        if(!pay(p,Items.IRON_INGOT,RELOCATION_IRON,Items.AIR,0))return "Banner relocation costs 8 iron ingots.";
        var state=level.getBlockState(source);
        BannerStructure.remove(level,source);
        level.setBlock(target,state,3);
        BannerStructure.complete(level,target);
        b.move(target);b.lastMove=level.getGameTime();d.setDirty();return "Relocated banner without changing existing claims.";
    }
    public static String removeBanner(MinecraftServer server,KingdomData.Banner b){
        if(b.capital)return defeat(server,b,"Capital captured");
        KingdomData d=KingdomData.get(server);
        if(b.town!=null){KingdomData.Town t=d.towns.remove(b.town);if(t!=null){d.townMergerApprovals.remove(t.id);d.clearTownSelection(t.id);d.townInvites.values().removeIf(id->id.equals(t.id));}}
        else{KingdomData.Banner cap=d.capital(b.kingdom);if(cap!=null)for(KingdomData.Claim c:d.claims.values())if(c.bannerId.equals(b.id))c.bannerId=cap.id;}
        d.claims.entrySet().removeIf(e->e.getValue().bannerId.equals(b.id));d.banners.remove(b.id);d.setDirty();removePhysical(server,b);
        return b.town!=null?"Town dissolved; its territory is wilderness.":"Expansion banner removed; kingdom land preserved.";
    }
    /** Fully dissolves an independent or annexed town; callers must verify mayor/admin permission. */
    public static String disbandTown(MinecraftServer server,KingdomData.Town town){
        KingdomData d=KingdomData.get(server);
        KingdomData.Banner banner=d.townBanner(town.id);
        if(banner!=null){
            d.claims.entrySet().removeIf(e->e.getValue().bannerId.equals(banner.id));
            d.banners.remove(banner.id);removePhysical(server,banner);
        }
        d.towns.remove(town.id);
        d.townInvites.values().removeIf(id->id.equals(town.id));
        d.townMergerApprovals.remove(town.id);
        d.clearTownSelection(town.id);d.setDirty();
        return "Town "+town.name+" disbanded. Its territory is now wilderness.";
    }
    private static void removePhysical(MinecraftServer server,KingdomData.Banner b){
        ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(b.dimension)));
        if(level!=null&&level.hasChunkAt(b.pos))BannerStructure.remove(level,b.pos);
    }
    public static String defeat(MinecraftServer server,KingdomData.Banner capital,String reason){
        KingdomData d=KingdomData.get(server);if(!capital.capital)return "Only a capital can defeat a kingdom.";
        KingdomData.Kingdom k=d.kingdoms.get(capital.kingdom);if(k==null)return "Kingdom no longer exists.";
        d.restartCredit.merge(k.king,k.treasuryIron/5,Long::sum);
        for(KingdomData.Town t:d.towns.values())if(k.id.equals(t.parentKingdom))t.parentKingdom=null;
        List<KingdomData.Banner> removed=new ArrayList<>();for(KingdomData.Banner b:d.banners.values())if("KINGDOM".equals(b.kind)&&k.id.equals(b.kingdom))removed.add(b);
        Set<UUID> bannerIds=new HashSet<>();for(KingdomData.Banner b:removed)bannerIds.add(b.id);
        d.claims.entrySet().removeIf(e->bannerIds.contains(e.getValue().bannerId));
        for(KingdomData.Banner b:removed){d.banners.remove(b.id);removePhysical(server,b);}
        d.invites.values().removeIf(id->id.equals(k.id));d.townMergerApprovals.values().removeIf(id->id.equals(k.id));d.kingdoms.remove(k.id);d.setDirty();
        return reason+": "+k.name+" permanently defeated. Kingdom land is neutral; independent towns survive. 20% treasury credit reserved for former king.";
    }
    public static String captureTown(MinecraftServer server,KingdomData.Banner b,UUID conqueror){
        KingdomData d=KingdomData.get(server);KingdomData.Town t=b.town==null?null:d.towns.get(b.town);
        if(t==null)return "Target isn't a town.";if(!d.kingdoms.containsKey(conqueror))return "Attacker kingdom doesn't exist.";
        t.parentKingdom=conqueror;d.townMergerApprovals.remove(t.id);d.setDirty();return "Town "+t.name+" captured and placed under "+d.kingdoms.get(conqueror).name+" control. Its claims survive.";
    }
    public static String collapseKingdomBanner(MinecraftServer server,KingdomData.Banner b){return b.capital?defeat(server,b,"Capital conquered"):removeBanner(server,b);}
    public static String disband(MinecraftServer server,KingdomData.Kingdom k){KingdomData d=KingdomData.get(server);
        KingdomData.Banner cap=d.capital(k.id);return cap!=null?defeat(server,cap,"Disbanded"):disbandUnlanded(d,k);
    }
    private static String disbandUnlanded(KingdomData d,KingdomData.Kingdom k){d.kingdoms.remove(k.id);d.setDirty();return "Disbanded "+k.name;}
    public static boolean bordersTouch(KingdomData d,UUID kingdom,UUID town){
        for(KingdomData.Claim c:d.claims.values())if(d.townControlled(town,c.dimension,c.cx,c.cz)){
            int[][] n={{c.cx+1,c.cz},{c.cx-1,c.cz},{c.cx,c.cz+1},{c.cx,c.cz-1}};
            for(int[] q:n)if(d.controlled(kingdom,c.dimension,q[0],q[1])&&d.townFor(d.claimAt(c.dimension,q[0],q[1]))==null)return true;
        }return false;
    }
    public static long annexCost(KingdomData d,UUID kingdom,UUID town){
        long cost=0;int base=d.kingdomClaims(kingdom),total=d.townClaims(town);
        for(int i=0;i<total;i++)cost+=(4L+Math.max(0,base+i-25));
        return (cost+1)/2; // 50% discount on progressively priced iron chunks
    }
    public static String annex(MinecraftServer server,KingdomData d,KingdomData.Kingdom k,KingdomData.Town t){
        if(!k.king.equals(t.mayor)&&!k.id.equals(d.townMergerApprovals.get(t.id)))return "The mayor must approve this kingdom first with /town annex approve <kingdom>.";
        if(t.parentKingdom!=null)return "This town is already annexed.";
        if(!bordersTouch(d,k.id,t.id))return "The kingdom and town claims must touch along an edge.";
        long cost=annexCost(d,k.id,t.id);
        if(cost>Integer.MAX_VALUE||!PhysicalWarehouses.payHeadquarters(server,k.id,Map.of("IRON",(int)cost)))
            return "Annexation requires "+cost+" iron in your capital's physical warehouse.";
        t.parentKingdom=k.id;d.townMergerApprovals.remove(t.id);d.setDirty();
        return "Annexed "+t.name+" for "+cost+" iron (50% discount). Town identity and rally use retained.";
    }
    private static int amount(ServerPlayer p,Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(s.is(item))n+=s.getCount();}return n;}
    private static void consume(ServerPlayer p,Item item,int count){for(int i=0;i<p.getInventory().getContainerSize()&&count>0;i++){
        ItemStack s=p.getInventory().getItem(i);if(s.is(item)){int take=Math.min(count,s.getCount());s.shrink(take);count-=take;}
    }p.getInventory().setChanged();}
    public static boolean pay(ServerPlayer p,Item one,int a,Item two,int b){if(p.getAbilities().instabuild)return true;if(amount(p,one)<a||amount(p,two)<b)return false;consume(p,one,a);consume(p,two,b);return true;}
    public static boolean deposit(ServerPlayer p,KingdomData.Kingdom k,int iron){
        if(iron<1||EconomyService.carried(p,"IRON")<iron)return false;
        return PhysicalWarehouses.storeFromPlayer(p,k.id,p.blockPosition(),"IRON",iron)==iron;
    }
}
