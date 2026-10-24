package com.kingdomsatwar;
import java.nio.file.*;
/** Headless model tests: blueprint construction requires a Forge startup + client playtest. */
public final class MineEconomyTest{
    private static int tests=0;
    private static void ok(boolean condition,String what){tests++;if(!condition)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception{
        String d="minecraft:overworld";
        MineEconomy e=new MineEconomy();
        ok(e.quality(1234,d,10,10)==e.quality(1234,d,10,10),"deposit seed stable");
        ok(e.quality(1234,d,10,10)>=1&&e.quality(1234,d,10,10)<=5,"quality bounded");
        ok(!e.register("knights",d,1,0,80,0).ok(),"no above-ground mine");
        ok(e.register("knights",d,100L,0,30,0).ok(),"underground controller");
        ok(!e.register("knights",d,100L,0,30,0).ok(),"no duplicate");
        MineEconomy.Mine m=e.nearest("knights",d,0,30,0);
        ok(m!=null,"nearest mine");
        ok(e.nearest("outsiders",d,0,30,0)==null,"owner isolation");
        ok(!m.built&&m.maxWorkers()==0,"no production capacity before construction");
        ok(!e.addWorker(m.id).ok(),"can't hire before construction");
        ok(e.payout(m,10000)==0,"unbuilt mine can't produce");
        ok(e.completeBuild(m.id).ok(),"finish physical blueprint");
        ok(m.built&&m.maxWorkers()==2,"level one unlocked");
        ok(!e.completeBuild(m.id).ok(),"no double completion");
        ok(e.addWorker(m.id).ok(),"first worker");
        ok(e.addWorker(m.id).ok(),"second worker");
        ok(!e.addWorker(m.id).ok(),"staff cap");
        int reserve=m.reserve;
        int produced=e.payout(m,10000);
        ok(produced>0&&produced<=40,"finite output");
        ok(reserve-m.reserve==produced,"reserve decreases");
        ok(e.payout(m,0)==0,"full warehouse stops output");
        ok(e.payout(m,1)==1,"warehouse remaining room");
        ok(e.removeWorker(m.id).ok()&&m.workers==1,"unassign worker");
        Path path=Files.createTempDirectory("ironforge-construction-test").resolve("mines.properties");
        e.save(path);MineEconomy loaded=MineEconomy.load(path);
        MineEconomy.Mine saved=loaded.get(m.id);
        ok(saved!=null&&saved.built,"built status persists");
        ok(saved.reserve==m.reserve,"reserve persists");
        ok(saved.workers==1,"assigned worker persists");
        ok(loaded.removeMine(d,0,30,0),"controller removal");
        ok(loaded.all().isEmpty(),"no ghost payouts");
        ok(MineEconomy.load(Path.of("/tmp/missing-iron-forge-"+System.nanoTime())).all().isEmpty(),"fresh save");
        System.out.println("PASS: "+tests+" construction mine economy assertions.");
    }
}
