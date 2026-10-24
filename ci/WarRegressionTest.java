package com.kingdomsatwar;
import java.nio.file.*;
import java.util.*;
/** Automated strategy, security, persistence, skirmish and load regressions. */
public final class WarRegressionTest {
  private static int checks=0;
  private static void check(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
  public static void main(String[] unused) throws Exception {
    UUID a=UUID.randomUUID(),b=UUID.randomUUID(),member=UUID.randomUUID(),stranger=UUID.randomUUID();
    WarState g=new WarState();
    check(g.createKingdom(a,"Oakland","minecraft:overworld",0,0).startsWith("Founded"),"found");
    check(g.createKingdom(b,"Riverton","minecraft:overworld",64,0).startsWith("Founded"),"second");
    check(g.createKingdom(stranger,"Invaders","minecraft:overworld",0,0).contains("already belongs"),"initial claim protected");
    check(g.joinKingdom(member,"Oakland").contains("invitation"),"invite required");
    check(g.invite(a,member).contains("Invitation"),"invite");
    check(g.joinKingdom(member,"Oakland").contains("Joined"),"join");
    check(g.declare(member,"Riverton").contains("founder"),"member war denied");
    check(g.declare(a,"Riverton").contains("WAR"),"founder war");
    check(g.peace(a,"Riverton").contains("offered"),"one-way peace");
    check(g.kingdom(a).wars.contains("riverton"),"war remains active");
    check(g.peace(b,"Oakland").contains("Peace accepted"),"mutual peace");
    check(g.alliance(a,"Riverton").contains("offered"),"alliance offer");
    check(g.alliance(b,"Oakland").contains("signed"),"mutual alliance");
    check(g.declare(a,"Riverton").contains("End your alliance"),"no allied war");
    check(g.endAlliance(a,"Riverton").contains("ended"),"end alliance");
    check(g.recruit(a,"minecraft:overworld",0,20,0,0).contains("Army #1"),"recruit");
    check(g.recruit(b,"minecraft:the_nether",0,20,0,0).contains("Army #2"),"nether recruit");
    check(g.recruit(a,"minecraft:overworld",2,501,0,0).contains("Recruit 1"),"recruit limit");
    check(g.march(b,1,100,0,false).contains("isn't your army"),"cross-kingdom orders denied");
    check(g.march(a,1,100,0,false).contains("marching"),"march");
    g.markClean();g.step();check(g.army(1).x>0,"march progress");check(g.dirty(),"save dirty");
    check(g.halt(a,1).contains("holding"),"halt");
    double x=g.army(1).x;g.step();check(g.army(1).x==x,"halted army stays still");
    check(g.declare(a,"Riverton").contains("WAR"),"declare again");
    check(g.battles().isEmpty(),"dimension isolation");
    check(g.testBattle(a,"minecraft:overworld",0,0).contains("Training battle"),"test battle");
    int count=g.armies.size();
    check(g.testBattle(a,"minecraft:overworld",0,0).contains("already active"),"no duplicate training");
    check(g.armies.size()==count,"no added duplicate armies");
    check(g.endTest(b).contains("no active training"),"nonparticipant cannot end test");
    check(g.endTest(a).contains("removed"),"training cleanup");
    for(WarState.Kingdom k:g.kingdoms.values()){
      check(!k.wars.contains("training_legion"),"no stale war");
      check(!k.peaceOffers.contains("training_legion"),"no stale offer");
      check(!k.allies.contains("training_legion"),"no stale ally");
    }
    check(g.fortify(a,"minecraft:overworld",64,0).contains("own claimed"),"no enemy fort");
    check(g.fortify(a,"minecraft:overworld",0,0).contains("level 1"),"fortify");
    Path save=Files.createTempDirectory("ironforge-ci").resolve("war.properties");
    g.save(save);WarState r=WarState.load(save);
    check(r.army(1)!=null && r.army(1).troops==20,"army persists");
    check(r.kingdom(a)!=null && r.kingdom(b)!=null,"kingdoms persist");
    check(r.claimAt("minecraft:overworld",0,0).fortLevel==1,"fort persists");
    check(!r.kingdoms.containsKey("training_legion"),"training cleanup persists");
    WarState stress=new WarState();
    stress.createKingdom(UUID.randomUUID(),"Eastland","minecraft:overworld",0,0);
    for(int i=0;i<250;i++)stress.armies.put(i+1,new WarState.Army(i+1,"eastland","minecraft:overworld",i%3,50,i*40,0));
    long start=System.nanoTime();
    for(int i=0;i<300;i++)stress.step();
    long elapsed=(System.nanoTime()-start)/1_000_000;
    check(elapsed<30000,"headless strategy stress test over 30s");
    System.out.println("PASS: "+checks+" regression assertions. 250 armies / 300 simulated seconds: "+elapsed+" ms (NOT TPS).");
  }
}