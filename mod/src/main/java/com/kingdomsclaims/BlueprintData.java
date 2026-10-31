package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, server-owned blueprints and paid construction sites. No entities or inventories are captured. */
public final class BlueprintData extends SavedData {
    public static final int MAX_WIDTH=20, MAX_HEIGHT=20, MAX_DEPTH=20, MAX_BLOCKS=4096, MAX_VOLUME=4096, MAX_PER_FACTION=24, MAX_FOLDERS=16;
    public static final List<String> DEFAULT_FOLDERS=List.of("Houses","Roads","Farms","Workshops","Defenses","Decor","Other");
    public static final class Piece {
        public int x,y,z;
        public CompoundTag state;
        public Piece(int x,int y,int z,CompoundTag state) {this.x=x;this.y=y;this.z=z;this.state=state;}
    }
    public static final class Plan {
        public UUID id,owner;
        public String dimension,name,folder="Other";
        public int width,height,depth;
        public final List<Piece> blocks=new ArrayList<>();
        public Plan(UUID id,UUID owner,String dimension,String name,int w,int h,int d) {
            this.id=id;this.owner=owner;this.dimension=dimension;this.name=name;this.width=w;this.height=h;this.depth=d;
        }
        private transient HousingMath.Result housingCache;
        public HousingMath.Result housingReport(){
            if(housingCache==null)housingCache=HousingCapacity.evaluate(this);
            return housingCache;
        }
        public int housingSlots(){return housingReport().capacity();}
        public int seconds(){return 30+(blocks.size()+3)/4;} // roughly one block per four seconds of construction budget
    }
    public static final class Site {
        public UUID id,planId,owner,player;
        public String dimension;public BlockPos anchor;
        public int turns,secondsRemaining,placedBlocks;
        public Site(UUID id,Plan p,UUID player,String dimension,BlockPos anchor,int turns) {
            this.id=id;this.planId=p.id;this.owner=p.owner;this.player=player;this.dimension=dimension;
            this.anchor=anchor;this.turns=turns;this.secondsRemaining=p.seconds();this.placedBlocks=0;
        }
    }
    public static final class Built {
        public final UUID id,owner,planId;
        public final String dimension;
        public final BlockPos anchor;
        public final int slots;
        public Built(UUID id,UUID owner,String dimension,BlockPos anchor,UUID planId,int slots){
            this.id=id;this.owner=owner;this.dimension=dimension;this.anchor=anchor;this.planId=planId;this.slots=slots;
        }
    }
    public final Map<UUID,Built> built=new LinkedHashMap<>();
    public int homes(UUID owner){int count=0;for(Built b:built.values())if(b.owner.equals(owner))count++;return count;}
    public int housing(UUID owner){int cap=0;for(Built b:built.values())if(b.owner.equals(owner))cap=Math.min(1_000_000,cap+b.slots);return cap;}
    public final Map<UUID,Plan> plans=new LinkedHashMap<>();
    private final Map<UUID,LinkedHashSet<String>> customFolders=new LinkedHashMap<>();
    public static String cleanFolder(String raw){
        if(raw==null)return null;
        String s=raw.strip();
        return s.length()>=2&&s.length()<=22&&s.matches("[\\p{L}0-9 _-]+")?s:null;
    }
    public List<String> folders(UUID owner){
        LinkedHashSet<String> out=new LinkedHashSet<>(DEFAULT_FOLDERS);
        out.addAll(customFolders.getOrDefault(owner,new LinkedHashSet<>()));
        for(Plan p:owned(owner))if(p.folder!=null&&!p.folder.isBlank())out.add(p.folder);
        return new ArrayList<>(out);
    }
    public boolean createFolder(UUID owner,String folder){
        String valid=cleanFolder(folder);if(valid==null)return false;
        if(folders(owner).stream().anyMatch(x->x.equalsIgnoreCase(valid)))return false;
        var set=customFolders.computeIfAbsent(owner,x->new LinkedHashSet<>());
        if(set.size()>=MAX_FOLDERS)return false;
        set.add(valid);setDirty();return true;
    }
    public boolean move(UUID owner,UUID planId,String folder){
        Plan p=plans.get(planId);if(p==null||!p.owner.equals(owner))return false;
        String valid=folders(owner).stream().filter(x->x.equalsIgnoreCase(folder)).findFirst().orElse(null);
        if(valid==null)return false;
        p.folder=valid;setDirty();return true;
    }
    public static String suggestedFolder(Plan p){
        String s=p.name.toLowerCase(Locale.ROOT);
        if(s.contains("road")||s.contains("path")||s.contains("bridge")||s.contains("street"))return "Roads";
        if(s.contains("farm")||s.contains("barn")||s.contains("stable")||s.contains("mill"))return "Farms";
        if(s.contains("tower")||s.contains("wall")||s.contains("gate")||s.contains("fort")||s.contains("guard"))return "Defenses";
        if(s.contains("shop")||s.contains("forge")||s.contains("workshop")||s.contains("market"))return "Workshops";
        if(s.contains("tree")||s.contains("statue")||s.contains("fountain")||s.contains("garden"))return "Decor";
        return p.housingSlots()>0?"Houses":"Other";
    }
    public final Map<UUID,Site> sites=new LinkedHashMap<>();
    public static BlueprintData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BlueprintData::load,BlueprintData::new,"kingdomsclaims_blueprints");
    }
    public Plan named(UUID faction,String name){for(Plan p:plans.values())if(p.owner.equals(faction)&&p.name.equalsIgnoreCase(name))return p;return null;}
    public List<Plan> owned(UUID faction){List<Plan> out=new ArrayList<>();for(Plan p:plans.values())if(p.owner.equals(faction))out.add(p);return out;}
    public int active(UUID faction){int n=0;for(Site s:sites.values())if(s.owner.equals(faction))n++;return n;}
    @Override public CompoundTag save(CompoundTag tag){
        ListTag records=new ListTag();for(Plan p:plans.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",p.id);n.putUUID("Owner",p.owner);
            n.putString("Dimension",p.dimension);n.putString("Name",p.name);n.putString("Folder",p.folder);
            n.putInt("W",p.width);n.putInt("H",p.height);n.putInt("D",p.depth);
            ListTag blocks=new ListTag();for(Piece piece:p.blocks){
                CompoundTag b=new CompoundTag();b.putInt("X",piece.x);b.putInt("Y",piece.y);b.putInt("Z",piece.z);b.put("State",piece.state);blocks.add(b);
            }n.put("Pieces",blocks);records.add(n);
        }tag.put("Plans",records);
        ListTag savedFolders=new ListTag();for(var entry:customFolders.entrySet())for(String folder:entry.getValue()){
            CompoundTag n=new CompoundTag();n.putUUID("Owner",entry.getKey());n.putString("Folder",folder);savedFolders.add(n);
        }tag.put("BuildFolders",savedFolders);
        ListTag work=new ListTag();for(Site s:sites.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",s.id);n.putUUID("Plan",s.planId);n.putUUID("Owner",s.owner);n.putUUID("Player",s.player);
            n.putString("Dimension",s.dimension);n.putInt("X",s.anchor.getX());n.putInt("Y",s.anchor.getY());n.putInt("Z",s.anchor.getZ());
            n.putInt("Turns",s.turns);n.putInt("Seconds",s.secondsRemaining);n.putInt("Placed",s.placedBlocks);work.add(n);
        }tag.put("Sites",work);
        ListTag homes=new ListTag();for(Built b:built.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Id",b.id);n.putUUID("Owner",b.owner);
            n.putUUID("Plan",b.planId);n.putString("Dimension",b.dimension);
            n.putInt("X",b.anchor.getX());n.putInt("Y",b.anchor.getY());n.putInt("Z",b.anchor.getZ());n.putInt("Slots",b.slots);homes.add(n);
        }tag.put("BuiltHomes",homes);tag.putInt("HousingVersion",2);return tag;
    }
    public static BlueprintData load(CompoundTag tag){
        BlueprintData data=new BlueprintData();
        for(Tag element:tag.getList("Plans",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)element;
            if(!n.hasUUID("Id")||!n.hasUUID("Owner"))continue;
            int w=n.getInt("W"),h=n.getInt("H"),d=n.getInt("D");
            if(w<1||w>MAX_WIDTH||h<1||h>MAX_HEIGHT||d<1||d>MAX_DEPTH)continue;
            Plan p=new Plan(n.getUUID("Id"),n.getUUID("Owner"),n.getString("Dimension"),n.getString("Name"),w,h,d);
            String folder=cleanFolder(n.getString("Folder"));p.folder=folder==null?"Other":folder;
            ListTag pieces=n.getList("Pieces",Tag.TAG_COMPOUND);if(pieces.isEmpty()||pieces.size()>MAX_BLOCKS)continue;
            for(Tag entry:pieces){CompoundTag b=(CompoundTag)entry;int x=b.getInt("X"),y=b.getInt("Y"),z=b.getInt("Z");
                if(x<0||y<0||z<0||x>=w||y>=h||z>=d||!b.contains("State",Tag.TAG_COMPOUND)){p.blocks.clear();break;}
                p.blocks.add(new Piece(x,y,z,b.getCompound("State").copy()));
            }if(!p.blocks.isEmpty()){
                if(!n.contains("Folder",Tag.TAG_STRING))p.folder=suggestedFolder(p);
                data.plans.put(p.id,p);
            }
        }
        for(Tag entry:tag.getList("BuildFolders",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)entry;
            String folder=cleanFolder(n.getString("Folder"));
            if(n.hasUUID("Owner")&&folder!=null){
                var set=data.customFolders.computeIfAbsent(n.getUUID("Owner"),x->new LinkedHashSet<>());
                if(set.size()<MAX_FOLDERS)set.add(folder);
            }
        }
        for(Tag entry:tag.getList("Sites",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)entry;
            if(!n.hasUUID("Id")||!n.hasUUID("Plan")||!n.hasUUID("Owner")||!n.hasUUID("Player"))continue;
            Plan p=data.plans.get(n.getUUID("Plan"));if(p==null||!p.owner.equals(n.getUUID("Owner")))continue;
            Site s=new Site(n.getUUID("Id"),p,n.getUUID("Player"),n.getString("Dimension"),new BlockPos(n.getInt("X"),n.getInt("Y"),n.getInt("Z")),Math.floorMod(n.getInt("Turns"),4));
            s.secondsRemaining=Math.max(0,Math.min(86400,n.getInt("Seconds")));
            // Older 0.4.11 sites contain no Placed key and safely start at zero.
            s.placedBlocks=Math.max(0,Math.min(p.blocks.size(),n.getInt("Placed")));
            data.sites.put(s.id,s);
        }
        for(Tag entry:tag.getList("BuiltHomes",Tag.TAG_COMPOUND)){
            CompoundTag n=(CompoundTag)entry;
            if(!n.hasUUID("Id")||!n.hasUUID("Owner")||!n.hasUUID("Plan"))continue;
            UUID id=n.getUUID("Id");data.built.put(id,new Built(id,n.getUUID("Owner"),n.getString("Dimension"),
                new BlockPos(n.getInt("X"),n.getInt("Y"),n.getInt("Z")),n.getUUID("Plan"),Math.max(0,Math.min(20,n.getInt("Slots")))));
        }
        // One-time v0.4.13 migration: replace the old unconditional four-resident
        // estimate with geometry-based values for completed structures whose plans survive.
        // Legacy builds with deleted blueprints keep their recorded slots.
        if(tag.getInt("HousingVersion")<2){
            for(var entry:new ArrayList<>(data.built.entrySet())){
                Built old=entry.getValue();Plan plan=data.plans.get(old.planId);
                if(plan!=null&&plan.owner.equals(old.owner))
                    data.built.put(entry.getKey(),new Built(old.id,old.owner,old.dimension,old.anchor,old.planId,plan.housingSlots()));
            }
            data.setDirty();
        }
        return data;
    }
}
