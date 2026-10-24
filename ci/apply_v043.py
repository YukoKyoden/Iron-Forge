#!/usr/bin/env python3
"""Apply tested Iron Forge v0.4.3 command & training lifecycle fixes."""
from pathlib import Path
src = Path("src/main/java/com/kingdomsatwar")
p = src / "WarCommands.java"
s = p.read_text()
old = '''          .then(Commands.literal("endtest").requires(source->source.hasPermission(2)).executes(c->
              message(c.getSource(),mod.get(c.getSource().getServer()).endTest(player(c.getSource()).getUUID()))))
          .then(Commands.literal("skirmish").requires(source->source.hasPermission(2)).executes(c->{
              ServerPlayer p=player(c.getSource());
              return message(c.getSource(),mod.get(c.getSource().getServer()).testBattle(p.getUUID(),dim(p),p.getX(),p.getZ()));
          }))'''
new = '''          // Brigadier must expose the command so denied users get a real explanation.
          .then(Commands.literal("endtest").executes(c->{
              ServerPlayer p=player(c.getSource());
              if(!canPractice(c.getSource(),p))return message(c.getSource(),"Training requires Creative mode or operator permission.");
              return message(c.getSource(),mod.get(c.getSource().getServer()).endTest(p.getUUID()));
          }))
          .then(Commands.literal("skirmish").executes(c->{
              ServerPlayer p=player(c.getSource());
              if(!canPractice(c.getSource(),p))return message(c.getSource(),"Training requires Creative mode or operator permission.");
              return message(c.getSource(),mod.get(c.getSource().getServer()).testBattle(p.getUUID(),dim(p),p.getX(),p.getZ()));
          }))'''
assert s.count(old) == 1, "Expected v0.4.2 test-command registration"
s = s.replace(old,new)
needle = '    private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {return source.getPlayerOrException();}'
assert s.count(needle)==1
s = s.replace(needle, '''    private static boolean canPractice(CommandSourceStack source,ServerPlayer player){
        return source.hasPermission(2) || player.isCreative();
    }
'''+needle)
s=s.replace('/war endtest (OP tests)','/war endtest (Creative or OP)')
p.write_text(s)

p = src / "WarMapNetwork.java"
s=p.read_text()
for old,new in [
 ('player.hasPermissions(2)?state.testBattle','(player.hasPermissions(2)||player.isCreative())?state.testBattle'),
 ('player.hasPermissions(2)?state.endTest','(player.hasPermissions(2)||player.isCreative())?state.endTest'),
 ('Operator permission required for test battles.','Training requires Creative mode or operator permission.'),
 ('Operator permission required for training cleanup.','Training requires Creative mode or operator permission.')
]:
    assert s.count(old)==1, old
    s=s.replace(old,new)
p.write_text(s)

p=src/"WarState.java";s=p.read_text()
old='        if(armies.size()>490)return "Too many existing armies for a test battle.";'
new='''        if(armies.size()>498)return "Too many existing armies for a test battle.";
        if(armies.values().stream().anyMatch(a->a.owner.equals("training_legion")))
            return "A training battle is already active. Use /war endtest before starting another.";'''
assert s.count(old)==1
s=s.replace(old,new)
old='''        own.wars.remove(enemy.id);own.peaceOffers.remove(enemy.id);
        own.allies.remove(enemy.id);own.allianceOffers.remove(enemy.id);
        enemy.wars.remove(own.id);
        armies.values().removeIf(a->a.owner.equals(enemy.id));
        kingdoms.remove(enemy.id);'''
new='''        if(!own.wars.contains(enemy.id))return "Your kingdom has no active training battle.";
        for(Kingdom k:kingdoms.values()){
            k.wars.remove(enemy.id);k.peaceOffers.remove(enemy.id);
            k.allies.remove(enemy.id);k.allianceOffers.remove(enemy.id);
        }
        armies.values().removeIf(a->a.owner.equals(enemy.id));
        kingdoms.remove(enemy.id);'''
assert s.count(old)==1
s=s.replace(old,new);p.write_text(s)
p=Path("build.gradle");s=p.read_text();assert s.count("0.4.2-alpha")==1
p.write_text(s.replace("0.4.2-alpha","0.4.3-alpha"))
print("Applied 0.4.3 command, UI, training cleanup and version changes.")
