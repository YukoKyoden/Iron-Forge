package com.kingdomsclaims;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent server-owned kingdom, town and claim state. Version 2 can read version 1 saves. */
public final class KingdomData extends SavedData {
    public static final int MEMBER=1,OFFICER=2,KING=3;
    public static final int BUILD=1,BREAK=2,STORAGE=4,CLAIM=8,BANNER=16,GATE=32;
    public static final int TOWN_RADIUS=4, INITIAL_RADIUS=2, MAX_TOWNS_PER_PLAYER=15;

    public static final class Kingdom {
        public UUID id, king; public String name;
        public final Map<UUID,Integer> members=new LinkedHashMap<>(), grants=new HashMap<>();
        public long treasuryIron=0, treasuryWheat=0;
        public Kingdom(UUID id,String name,UUID king){this.id=id;this.name=name;this.king=king;members.put(king,KING);}
        public int rank(UUID p){return members.getOrDefault(p,0);}
        public boolean allowed(UUID p,int permission){return rank(p)>=OFFICER || (rank(p)>0&&(grants.getOrDefault(p,0)&permission)!=0);}
    }
    public static final class Town {
        public UUID id,mayor,parentKingdom; public String name;
        public final Map<UUID,Integer> members=new LinkedHashMap<>(),grants=new HashMap<>();
        public long treasuryIron=0, treasuryWheat=0;
        public UUID bannerId;
        public Town(UUID id,String name,UUID mayor){this.id=id;this.name=name;this.mayor=mayor;members.put(mayor,KING);}
        public int rank(UUID p){return members.getOrDefault(p,0);}
        public boolean allowed(UUID p,int permission){return rank(p)>=OFFICER||(rank(p)>0&&(grants.getOrDefault(p,0)&permission)!=0);}
    }
    public static final class Banner {
        public UUID id,kingdom,town; public String kind,dimension;public BlockPos pos;public int cx,cz;
        public boolean capital;public long lastMove=Long.MIN_VALUE/2;
        public Banner(UUID id,UUID kingdom,String kind,String dimension,BlockPos pos){this.id=id;this.kingdom=kingdom;this.kind=kind;this.dimension=dimension;this.pos=pos;this.cx=pos.getX()>>4;this.cz=pos.getZ()>>4;}
        public boolean contains(String dim,int x,int z){return dimension.equals(dim)&&Math.abs(cx-x)<=TOWN_RADIUS&&Math.abs(cz-z)<=TOWN_RADIUS;}
        public void move(BlockPos destination){this.pos=destination;this.cx=destination.getX()>>4;this.cz=destination.getZ()>>4;}
    }
    public static final class Claim {
        public String dimension;public int cx,cz;
        public UUID bannerId;
        public Claim(String dimension,int cx,int cz,UUID bannerId){this.dimension=dimension;this.cx=cx;this.cz=cz;this.bannerId=bannerId;}
    }
    public final Map<UUID,Kingdom> kingdoms=new LinkedHashMap<>();
    public final Map<UUID,Town> towns=new LinkedHashMap<>();
    public final Map<UUID,Banner> banners=new LinkedHashMap<>();
    public final Map<String,Claim> claims=new HashMap<>();
    public final Map<UUID,UUID> invites=new HashMap<>(),townInvites=new HashMap<>(),townMergerApprovals=new HashMap<>(),selectedTowns=new HashMap<>();
    public final Map<UUID,Long> restartCredit=new HashMap<>();
    public static KingdomData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(KingdomData::load,KingdomData::new,"kingdomsclaims");}
    public static String dim(ServerLevel level){return level.dimension().location().toString();}
    public static String key(String dim,int x,int z){return dim+"|"+x+"|"+z;}
    public Claim claimAt(String dim,int x,int z){return claims.get(key(dim,x,z));}
    public Banner bannerAt(String dim,BlockPos pos){for(Banner b:banners.values())if(b.dimension.equals(dim)&&b.pos.equals(pos))return b;return null;}
    public Banner bannerFor(Claim c){return c==null?null:banners.get(c.bannerId);}
    public Kingdom byName(String n){for(Kingdom k:kingdoms.values())if(k.name.equalsIgnoreCase(n))return k;return null;}
    public Town townByName(String n){for(Town t:towns.values())if(t.name.equalsIgnoreCase(n))return t;return null;}
    public Kingdom ofPlayer(UUID p){for(Kingdom k:kingdoms.values())if(k.members.containsKey(p))return k;return null;}
    /** Selected town drives /town commands, placement and territory survey for players in multiple towns. */
    public Town townOf(UUID p){
        UUID id=selectedTowns.get(p);
        Town selected=id==null?null:towns.get(id);
        if(selected!=null&&selected.members.containsKey(p))return selected;
        for(Town t:towns.values())if(t.members.containsKey(p))return t;
        return null;
    }
    public List<Town> townsOf(UUID player){
        List<Town> result=new ArrayList<>();
        for(Town t:towns.values())if(t.members.containsKey(player))result.add(t);
        return result;
    }
    public int ownedTowns(UUID player){int count=0;for(Town t:towns.values())if(t.mayor.equals(player))count++;return count;}
    public int memberTowns(UUID player){return townsOf(player).size();}
    public boolean selectTown(UUID player,UUID id){
        Town t=towns.get(id);
        if(t==null||!t.members.containsKey(player))return false;
        selectedTowns.put(player,id);setDirty();return true;
    }
    public Town nextTown(UUID player){
        List<Town> list=townsOf(player);
        if(list.isEmpty())return null;
        Town current=townOf(player);int i=current==null?-1:list.indexOf(current);
        Town next=list.get((i+1)%list.size());selectTown(player,next.id);return next;
    }
    public void clearTownSelection(UUID town){selectedTowns.entrySet().removeIf(e->e.getValue().equals(town));setDirty();}

    public Town townFor(Claim c){Banner b=bannerFor(c);return b==null||b.town==null?null:towns.get(b.town);}
    public Kingdom ownerAt(String dim,int x,int z){
        Banner b=bannerFor(claimAt(dim,x,z));if(b==null)return null;
        if(b.town!=null){Town t=towns.get(b.town);return t==null||t.parentKingdom==null?null:kingdoms.get(t.parentKingdom);}
        return kingdoms.get(b.kingdom);
    }
    public String ownerName(String dim,int x,int z){
        Claim c=claimAt(dim,x,z);Town t=townFor(c);if(t!=null)return "Town: "+t.name;
        Kingdom k=ownerAt(dim,x,z);return k==null?"Wilderness": "Kingdom: "+k.name;
    }
    public String identity(String dim,int x,int z){Claim c=claimAt(dim,x,z);if(c==null)return "";Town t=townFor(c);if(t!=null)return "town:"+t.id;Kingdom k=ownerAt(dim,x,z);return k==null?"":"kingdom:"+k.id;}
    public boolean controlled(UUID kingdom,String dim,int x,int z){Kingdom k=ownerAt(dim,x,z);return k!=null&&k.id.equals(kingdom);}
    public boolean townControlled(UUID town,String dim,int x,int z){Town t=townFor(claimAt(dim,x,z));return t!=null&&t.id.equals(town);}
    public boolean adjacent(String dim,int cx,int cz,UUID owner,boolean town){
        int[][] neighbors={{cx+1,cz},{cx-1,cz},{cx,cz+1},{cx,cz-1}};
        for(int[] p:neighbors)if(town?townControlled(owner,dim,p[0],p[1]):controlled(owner,dim,p[0],p[1]))return true;
        return false;
    }
    public int kingdomClaims(UUID k){int n=0;for(Claim c:claims.values())if(controlled(k,c.dimension,c.cx,c.cz))n++;return n;}
    public int townClaims(UUID t){int n=0;for(Claim c:claims.values())if(townControlled(t,c.dimension,c.cx,c.cz))n++;return n;}
    public Banner capital(UUID kingdom){for(Banner b:banners.values())if("KINGDOM".equals(b.kind)&&b.capital&&kingdom.equals(b.kingdom))return b;return null;}
    public Banner townBanner(UUID town){for(Banner b:banners.values())if(town.equals(b.town))return b;return null;}
    public void put(Claim c){claims.put(key(c.dimension,c.cx,c.cz),c);setDirty();}
    public void delete(Claim c){claims.remove(key(c.dimension,c.cx,c.cz));setDirty();}
    @Override public CompoundTag save(CompoundTag root){
        root.putInt("Schema",3);
        ListTag ks=new ListTag();for(Kingdom k:kingdoms.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",k.id);n.putUUID("King",k.king);n.putString("Name",k.name);n.putLong("Treasury",k.treasuryIron);n.putLong("TreasuryWheat",k.treasuryWheat);
            n.put("Members",members(k.members));n.put("Grants",members(k.grants));ks.add(n);
        }root.put("Kingdoms",ks);
        ListTag ts=new ListTag();for(Town t:towns.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",t.id);n.putUUID("Mayor",t.mayor);n.putString("Name",t.name);
            n.putLong("TreasuryIron",t.treasuryIron);n.putLong("TreasuryWheat",t.treasuryWheat);
            if(t.parentKingdom!=null)n.putUUID("Parent",t.parentKingdom);
            if(t.bannerId!=null)n.putUUID("Banner",t.bannerId);
            n.put("Members",members(t.members));n.put("Grants",members(t.grants));ts.add(n);
        }root.put("Towns",ts);
        ListTag bs=new ListTag();for(Banner b:banners.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",b.id);if(b.kingdom!=null)n.putUUID("Kingdom",b.kingdom);
            if(b.town!=null)n.putUUID("Town",b.town);
            n.putString("Kind",b.kind);n.putString("Dimension",b.dimension);
            n.putInt("X",b.pos.getX());n.putInt("Y",b.pos.getY());n.putInt("Z",b.pos.getZ());
            n.putBoolean("Capital",b.capital);n.putLong("LastMove",b.lastMove);bs.add(n);
        }root.put("Banners",bs);
        ListTag cs=new ListTag();for(Claim c:claims.values()){
            CompoundTag n=new CompoundTag();n.putString("Dimension",c.dimension);n.putInt("CX",c.cx);n.putInt("CZ",c.cz);n.putUUID("Banner",c.bannerId);cs.add(n);
        }root.put("Claims",cs);
        root.put("Invites",uuidMap(invites,"Player","Kingdom"));root.put("TownInvites",uuidMap(townInvites,"Player","Town"));
        root.put("MergeApprovals",uuidMap(townMergerApprovals,"Town","Kingdom"));
        root.put("SelectedTowns",uuidMap(selectedTowns,"Player","Town"));
        ListTag credits=new ListTag();for(var e:restartCredit.entrySet()){CompoundTag n=new CompoundTag();n.putUUID("Player",e.getKey());n.putLong("Iron",e.getValue());credits.add(n);}root.put("RestartCredit",credits);
        return root;
    }
    private static ListTag members(Map<UUID,Integer> members){ListTag a=new ListTag();for(var e:members.entrySet()){
        CompoundTag n=new CompoundTag();n.putUUID("Player",e.getKey());n.putInt("Rank",e.getValue());a.add(n);
    }return a;}
    private static ListTag uuidMap(Map<UUID,UUID> map,String a,String b){ListTag ls=new ListTag();for(var e:map.entrySet()){
        CompoundTag n=new CompoundTag();n.putUUID(a,e.getKey());n.putUUID(b,e.getValue());ls.add(n);
    }return ls;}
    private static void readMembers(CompoundTag n,String key,Map<UUID,Integer> to){for(Tag el:n.getList(key,Tag.TAG_COMPOUND)){
        CompoundTag e=(CompoundTag)el;if(e.hasUUID("Player"))to.put(e.getUUID("Player"),e.getInt("Rank"));
    }}
    private static void readMap(CompoundTag n,String key,String a,String b,Map<UUID,UUID> to){for(Tag el:n.getList(key,Tag.TAG_COMPOUND)){
        CompoundTag e=(CompoundTag)el;if(e.hasUUID(a)&&e.hasUUID(b))to.put(e.getUUID(a),e.getUUID(b));
    }}
    public static KingdomData load(CompoundTag root){
        KingdomData d=new KingdomData();int schema=root.getInt("Schema");
        for(Tag el:root.getList("Kingdoms",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)el;if(!n.hasUUID("Id")||!n.hasUUID("King"))continue;
            Kingdom k=new Kingdom(n.getUUID("Id"),n.getString("Name"),n.getUUID("King"));k.members.clear();
            readMembers(n,"Members",k.members);readMembers(n,"Grants",k.grants);k.members.put(k.king,KING);k.treasuryIron=n.getLong("Treasury");k.treasuryWheat=n.getLong("TreasuryWheat");d.kingdoms.put(k.id,k);
        }
        for(Tag el:root.getList("Towns",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)el;if(!n.hasUUID("Id")||!n.hasUUID("Mayor"))continue;
            Town t=new Town(n.getUUID("Id"),n.getString("Name"),n.getUUID("Mayor"));t.members.clear();
            readMembers(n,"Members",t.members);readMembers(n,"Grants",t.grants);t.members.put(t.mayor,KING);t.treasuryIron=n.getLong("TreasuryIron");t.treasuryWheat=n.getLong("TreasuryWheat");
            if(n.hasUUID("Parent"))t.parentKingdom=n.getUUID("Parent");if(n.hasUUID("Banner"))t.bannerId=n.getUUID("Banner");d.towns.put(t.id,t);
        }
        for(Tag el:root.getList("Banners",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)el;if(!n.hasUUID("Id"))continue;
            UUID k=n.hasUUID("Kingdom")?n.getUUID("Kingdom"):null;
            Banner b=new Banner(n.getUUID("Id"),k,n.getString("Kind"),n.getString("Dimension"),new BlockPos(n.getInt("X"),n.getInt("Y"),n.getInt("Z")));
            if(n.hasUUID("Town"))b.town=n.getUUID("Town");b.capital=n.getBoolean("Capital");b.lastMove=n.contains("LastMove")?n.getLong("LastMove"):Long.MIN_VALUE/2;
            if(k==null||d.kingdoms.containsKey(k)||b.town!=null)d.banners.put(b.id,b);
        }
        if(schema<2){
            // Legacy 5x5 'TOWN' zones become separately owned towns; do not delete any existing claims.
            for(Banner b:d.banners.values())if("TOWN".equals(b.kind)&&b.town==null&&b.kingdom!=null){
                Kingdom k=d.kingdoms.get(b.kingdom);if(k==null)continue;
                Town t=new Town(UUID.randomUUID(),k.name+"Town"+d.towns.size(),k.king);t.bannerId=b.id;t.parentKingdom=k.id;
                d.towns.put(t.id,t);b.town=t.id;b.kingdom=null;
            }
            for(Kingdom k:d.kingdoms.values()){
                boolean found=false;
                for(Banner b:d.banners.values())if("KINGDOM".equals(b.kind)&&k.id.equals(b.kingdom)){
                    if(!found){b.capital=true;found=true;}
                }
            }
        }
        for(Tag el:root.getList("Claims",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)el;if(!n.hasUUID("Banner")||!d.banners.containsKey(n.getUUID("Banner")))continue;
            Claim c=new Claim(n.getString("Dimension"),n.getInt("CX"),n.getInt("CZ"),n.getUUID("Banner"));d.claims.put(key(c.dimension,c.cx,c.cz),c);
        }
        readMap(root,"Invites","Player","Kingdom",d.invites);readMap(root,"TownInvites","Player","Town",d.townInvites);
        readMap(root,"MergeApprovals","Town","Kingdom",d.townMergerApprovals);
        readMap(root,"SelectedTowns","Player","Town",d.selectedTowns);
        for(Tag el:root.getList("RestartCredit",Tag.TAG_COMPOUND)){CompoundTag n=(CompoundTag)el;if(n.hasUUID("Player"))d.restartCredit.put(n.getUUID("Player"),n.getLong("Iron"));}
        if(schema<3)d.setDirty();return d;
    }
}
