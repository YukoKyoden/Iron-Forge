package com.kingdomsatwar;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic, data-only mining economy. Never scans or loads Minecraft chunks. */
public final class MineEconomy {
    public static final int MAX_SUPPORTS = 24, MAX_WORKERS = 24, MAX_MINES_PER_KINGDOM=8, WAREHOUSE_IRON_CAP=10000;
    public static final int MAX_DISTANCE=48, MIN_SPACING=8;
    public static final class Mine {
        public final String id,owner,dimension; public final int x,y,z,quality;
        public int reserve,workers; public boolean built=false; public final Set<String> supports=new LinkedHashSet<>();
        Mine(String id,String owner,String dim,int x,int y,int z,int quality,int reserve){
            this.id=id;this.owner=owner;this.dimension=dim;this.x=x;this.y=y;this.z=z;
            this.quality=quality;this.reserve=reserve;
        }
        public int maxWorkers(){return built ? 2 : 0;}
    }
    public record Result(boolean ok,String message){}
    private final Map<String,Mine> mines=new LinkedHashMap<>();
    public Collection<Mine> all(){return Collections.unmodifiableCollection(mines.values());}
    public Mine get(String id){return mines.get(id);}
    public static String key(String dim,int x,int y,int z){return dim+"|"+x+"|"+y+"|"+z;}
    public static int[] position(String key){
        String[] p=key.split("\\|");
        return new int[]{Integer.parseInt(p[p.length-3]),Integer.parseInt(p[p.length-2]),Integer.parseInt(p[p.length-1])};
    }
    private static long mix(long v){v^=v>>>30;v*=0xbf58476d1ce4e5b9L;v^=v>>>27;v*=0x94d049bb133111ebL;return v^(v>>>31);}
    /** Geology is independent of placed stone/ore blocks: no block-spoofing exploit. */
    public static int quality(long seed,String dimension,int x,int z){
        long d=mix(dimension.hashCode());
        long h=mix(seed^d ^(Math.floorDiv(x,128)*0x9e3779b97f4a7c15L)^(Math.floorDiv(z,128)*0xc2b2ae3d27d4eb4fL));
        return 1+(int)Math.floorMod(h,5);
    }
    public static int initialReserve(long seed,String dim,int x,int z) {
        long h=mix(seed^dim.hashCode()^((long)Math.floorDiv(x,128)*0x632be59bd9b4e019L)^((long)Math.floorDiv(z,128)*0x9e3779b97f4a7c15L));
        return 1600+(int)Math.floorMod(h,65)*100;
    }
    public Result register(String owner,String dim,long seed,int x,int y,int z) {
        if(owner==null||owner.isBlank())return new Result(false,"Join a kingdom first.");
        if(y>55||y< -55)return new Result(false,"Build your mine underground (Y -55 to 55).");
        String id=key(dim,x,y,z);
        if(mines.containsKey(id))return new Result(false,"That mine is already registered.");
        if(mines.values().stream().filter(m->m.owner.equals(owner)).count()>=MAX_MINES_PER_KINGDOM) return new Result(false,"Your kingdom has reached its mine limit.");
        for(Mine m:mines.values()) if(m.dimension.equals(dim)&&Math.abs(m.x-x)<64&&Math.abs(m.z-z)<64) return new Result(false,"Another mine is too close (64 blocks).");
        mines.put(id,new Mine(id,owner,dim,x,y,z,quality(seed,dim,x,z),initialReserve(seed,dim,x,z)));
        return new Result(true,"Mine controller registered. Open the construction chest, fill it, and sneak-right-click to construct.");
    }
    public Mine nearest(String owner,String dim,int x,int y,int z) {
        Mine best=null;double distance=Double.MAX_VALUE;
        for(Mine m:mines.values()){
            if(!m.owner.equals(owner)||!m.dimension.equals(dim))continue;
            double d=dist2(m.x,m.y,m.z,x,y,z);
            if(d<=MAX_DISTANCE*MAX_DISTANCE&&d<distance){best=m;distance=d;}
        }return best;
    }
    private static double dist2(int x,int y,int z,int ax,int ay,int az){return Math.pow(x-ax,2)+Math.pow(y-ay,2)+Math.pow(z-az,2);}
    /** rockFaces samples 8 rock cells just beyond a 3x3 tunnel, NOT a world scan. */
    public Result addSupport(String mineId,int x,int y,int z,int rockFaces,boolean tunnelOpen){
        Mine m=mines.get(mineId);
        if(m==null)return new Result(false,"No nearby registered mine.");
        if(m.supports.size()>=MAX_SUPPORTS)return new Result(false,"Upgrade limit: maximum 24 supports per mine.");
        if(dist2(m.x,m.y,m.z,x,y,z)>MAX_DISTANCE*MAX_DISTANCE || Math.abs(y-m.y)>18)
            return new Result(false,"Support is outside the mine's working area.");
        if(!tunnelOpen||rockFaces<4)return new Result(false,"Support needs a walkable rocky tunnel (at least four rock faces).");
        String pos=key(m.dimension,x,y,z);
        if(m.supports.contains(pos))return new Result(false,"This support is already registered.");
        for(String p:m.supports){int[] c=position(p);if(dist2(c[0],c[1],c[2],x,y,z)<MIN_SPACING*MIN_SPACING)
            return new Result(false,"Separate supports by at least eight blocks.");}
        m.supports.add(pos);return new Result(true,"Mine support linked ("+m.supports.size()+"/"+MAX_SUPPORTS+").");
    }
    public boolean removeSupport(String dim,int x,int y,int z){
        String id=key(dim,x,y,z);boolean removed=false;
        for(Mine m:mines.values()) if(m.supports.remove(id)){m.workers=Math.min(m.workers,m.maxWorkers());removed=true;}
        return removed;
    }
    public boolean removeMine(String dim,int x,int y,int z) {return mines.remove(key(dim,x,y,z))!=null;}
    public Result completeBuild(String mineId){
        Mine m=mines.get(mineId);if(m==null)return new Result(false,"Mine not registered.");
        if(m.built)return new Result(false,"Mine already constructed.");
        m.built=true;return new Result(true,"Level 1 iron mine completed. Assign workers to begin production.");
    }
    public Result addWorker(String mineId){
        Mine m=mines.get(mineId);if(m==null)return new Result(false,"Mine not found.");
        if(!m.built)return new Result(false,"Construct the mine first.");
        if(m.workers>=m.maxWorkers())return new Result(false,"Level 1 mine supports a maximum of 2 workers.");
        m.workers++;return new Result(true,"Workers: "+m.workers+"/"+m.maxWorkers());
    }
    public Result removeWorker(String mineId){
        Mine m=mines.get(mineId);if(m==null)return new Result(false,"Mine not found.");
        if(m.workers<=0)return new Result(false,"No workers assigned.");
        m.workers--;return new Result(true,"Workers: "+m.workers+"/"+m.maxWorkers());
    }
    /** Once-per-minute payout. The caller supplies current warehouse capacity and adds result atomically. */
    public int payout(Mine m,int remainingWarehouseRoom){
        if(m==null||!m.built||m.workers<=0||m.reserve<=0||remainingWarehouseRoom<=0)return 0;
        int perMinute=Math.min(40,Math.min(m.workers,m.maxWorkers())*(m.quality+1));
        int out=Math.max(0,Math.min(Math.min(m.reserve,remainingWarehouseRoom),perMinute));
        m.reserve-=out;return out;
    }
    public void save(Path file)throws IOException{
        Properties p=new Properties();p.setProperty("version","1");p.setProperty("count",""+mines.size());
        int i=0;for(Mine m:mines.values()){
            String b="mine."+(i++)+".";
            p.setProperty(b+"id",m.id);p.setProperty(b+"owner",m.owner);p.setProperty(b+"dim",m.dimension);
            p.setProperty(b+"x",""+m.x);p.setProperty(b+"y",""+m.y);p.setProperty(b+"z",""+m.z);
            p.setProperty(b+"quality",""+m.quality);p.setProperty(b+"reserve",""+m.reserve);p.setProperty(b+"workers",""+m.workers);p.setProperty(b+"built",Boolean.toString(m.built));
            p.setProperty(b+"supports",String.join(";",m.supports));
        }
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try(OutputStream o=Files.newOutputStream(tmp)){p.store(o,"Iron Forge geological mines");}
        try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
    private static int number(Properties p,String k,int fallback){try{return Integer.parseInt(p.getProperty(k));}catch(Exception e){return fallback;}}
    public static MineEconomy load(Path file)throws IOException{
        MineEconomy e=new MineEconomy();if(!Files.exists(file))return e;
        Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){p.load(in);}
        int count=Math.min(number(p,"count",0),2048);
        for(int i=0;i<count;i++){
            String b="mine."+i+".",dim=p.getProperty(b+"dim",""),owner=p.getProperty(b+"owner","");
            int x=number(p,b+"x",0),y=number(p,b+"y",0),z=number(p,b+"z",0);
            if(dim.isBlank()||owner.isBlank())continue;
            Mine m=new Mine(key(dim,x,y,z),owner,dim,x,y,z,
                 Math.max(1,Math.min(5,number(p,b+"quality",1))),Math.max(0,number(p,b+"reserve",0)));
            for(String v:p.getProperty(b+"supports","").split(";"))if(!v.isBlank()&&m.supports.size()<MAX_SUPPORTS){
                try{int[] a=position(v);m.supports.add(key(dim,a[0],a[1],a[2]));}catch(Exception ignore){}
            }
            m.built=Boolean.parseBoolean(p.getProperty(b+"built","false"));
            m.workers=Math.max(0,Math.min(m.maxWorkers(),number(p,b+"workers",0)));
            e.mines.put(m.id,m);
        }
        return e;
    }
}