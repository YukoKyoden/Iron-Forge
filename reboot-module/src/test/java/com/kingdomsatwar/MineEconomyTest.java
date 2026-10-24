package com.kingdomsatwar;
import java.nio.file.*;
/** Run headlessly with javac/java, no Minecraft classes. */
public final class MineEconomyTest{
    private static int tests=0;
    private static void ok(boolean condition,String what){tests++;if(!condition)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String d="minecraft:overworld";
        MineEconomy e=new MineEconomy();
        ok(e.quality(1234,d,10,10)==e.quality(1234,d,10,10),"seed stable");
        ok(e.quality(1234,d,10,10)>=1&&e.quality(1234,d,10,10)<=5,"quality range");
        ok(!e.register("knights",d,1,0,80,0).ok(),"above-ground mine denied");
        ok(e.register("knights",d,100L,0,30,0).ok(),"underground mine");
        ok(!e.register("knights",d,100L,0,30,0).ok(),"duplicate mine");
        MineEconomy.Mine m=e.nearest("knights",d,0,30,0);
        ok(m!=null,"nearest mine");
        ok(e.nearest("enemies",d,0,30,0)==null,"owner isolation");
        ok(!e.addSupport(m.id,8,30,0,0,true).ok(),"stone check");
        ok(!e.addSupport(m.id,8,30,0,6,false).ok(),"tunnel check");
        ok(e.addSupport(m.id,8,30,0,6,true).ok(),"first support");
        ok(!e.addSupport(m.id,12,30,0,6,true).ok(),"spacing restriction");
        ok(!e.addSupport(m.id,80,30,0,6,true).ok(),"range restriction");
        ok(e.addSupport(m.id,18,30,0,7,true).ok(),"second support");
        ok(e.addWorker(m.id).ok(),"hire first");
        ok(e.addWorker(m.id).ok(),"hire second");
        int initial=m.reserve,normal=e.payout(m,1000);
        ok(normal>0&&normal<=40,"real payout");
        ok(initial-m.reserve==normal,"reserve consumed exactly");
        ok(e.payout(m,0)==0,"full warehouse");
        ok(e.payout(m,1)==1,"warehouse caps payout");
        int previous=m.reserve;ok(e.payout(m,50)<=50 && m.reserve<previous,"capped flow");
        while(e.addWorker(m.id).ok()){}
        ok(m.workers==m.maxWorkers(),"support limits workers");
        Path path=Files.createTempDirectory("ironforge-geology-tests").resolve("mines.properties");
        e.save(path);MineEconomy loaded=MineEconomy.load(path);
        MineEconomy.Mine saved=loaded.get(m.id);
        ok(saved!=null&&saved.reserve==m.reserve,"deposit saved");
        ok(saved.supports.size()==2&&saved.workers==m.workers,"staff and supports saved");
        ok(loaded.removeSupport(d,8,30,0),"break registered support");
        ok(loaded.get(m.id).workers<=loaded.get(m.id).maxWorkers(),"worker cap after support break");
        ok(loaded.removeMine(d,0,30,0),"controller removal");
        ok(loaded.all().isEmpty(),"no ghost mine payout");
        ok(MineEconomy.load(Path.of("/tmp/not-a-real-iron-forge-save-"+System.nanoTime())).all().isEmpty(),"fresh save");
        System.out.println("PASS: "+tests+" geological mine and persistence assertions");
    }
}