package com.kingdomsclaims;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** All blueprint copying, safety, claim, material and completion logic executes on the dedicated server. */
public final class BlueprintService {
    private BlueprintService(){}
    public static final class Selection {public BlockPos a,b;public String dimension;}
    private static final Map<UUID,Selection> selections=new HashMap<>();
    public static Selection selection(ServerPlayer player){return selections.computeIfAbsent(player.getUUID(),x->new Selection());}
    public record Result(boolean success,String message){}
    private static Result no(String s){return new Result(false,s);}
    private static Result yes(String s){return new Result(true,s);}
    public static BarracksService.Faction managerFaction(ServerPlayer p,BlockPos manager){
        // Remote planner requests use the player's exact standing block, never an arbitrary distant coordinate.
        if(manager.equals(p.blockPosition()))return atPlayer(p);
        if(!p.serverLevel().getBlockState(manager).is(KingdomsClaims.KINGDOM_MANAGER.get())||
           p.distanceToSqr(manager.getX()+.5,manager.getY()+.5,manager.getZ()+.5)>64)return null;
        BarracksService.Faction f=BarracksService.faction(p.serverLevel(),manager);
        return f!=null&&canBuildFaction(p,f)?f:null;
    }
    private static boolean canBuildFaction(ServerPlayer p,BarracksService.Faction f){
        KingdomData data=KingdomData.get(p.getServer());
        KingdomData.Kingdom k=data.kingdoms.get(f.id());KingdomData.Town t=data.towns.get(f.id());
        return k!=null&&k.allowed(p.getUUID(),KingdomData.BUILD)||t!=null&&t.allowed(p.getUUID(),KingdomData.BUILD);
    }
    /** Permissions are checked on every covered chunk, not only the target anchor. */
    public static boolean canUse(ServerLevel level,UUID faction,String dimension,BlockPos pos){
        KingdomData data=KingdomData.get(level.getServer());
        return data.identity(dimension,pos.getX()>>4,pos.getZ()>>4).equals(
            (data.kingdoms.containsKey(faction)?"kingdom:":"town:")+faction)
            &&!KingdomService.bannerProtected(level,pos);
    }
    public static BarracksService.Faction atPlayer(ServerPlayer p){
        BarracksService.Faction f=BarracksService.faction(p.serverLevel(),p.blockPosition());
        return f!=null&&canBuildFaction(p,f)?f:null;
    }
    public static Result save(ServerPlayer player,String rawName){
        String name=rawName.strip();if(name.length()<2||name.length()>28||name.chars().anyMatch(c->c<32))return no("Blueprint name must be 2-28 printable characters.");
        Selection sel=selection(player);if(sel.a==null||sel.b==null||!KingdomData.dim(player.serverLevel()).equals(sel.dimension))return no("Use /architect select to highlight a house, or /architect pos1 and pos2.");
        ServerLevel level=player.serverLevel();BarracksService.Faction faction=atPlayer(player);
        if(faction==null)return no("Stand in claimed land where your faction granted BUILD permission.");
        BlueprintData data=BlueprintData.get(player.getServer());if(data.named(faction.id(),name)!=null)return no("That blueprint name already exists. Use another name or /architect delete first.");
        if(data.owned(faction.id()).size()>=BlueprintData.MAX_PER_FACTION)return no("Maximum 24 blueprints per faction.");
        BlockPos min=new BlockPos(Math.min(sel.a.getX(),sel.b.getX()),Math.min(sel.a.getY(),sel.b.getY()),Math.min(sel.a.getZ(),sel.b.getZ()));
        BlockPos max=new BlockPos(Math.max(sel.a.getX(),sel.b.getX()),Math.max(sel.a.getY(),sel.b.getY()),Math.max(sel.a.getZ(),sel.b.getZ()));
        int w=max.getX()-min.getX()+1,h=max.getY()-min.getY()+1,d=max.getZ()-min.getZ()+1;
        if(w>BlueprintData.MAX_WIDTH||h>BlueprintData.MAX_HEIGHT||d>BlueprintData.MAX_DEPTH||w*h*d>BlueprintData.MAX_VOLUME)return no("Selection exceeds 20 blocks per side or 4096 total positions. Resize the highlighted area.");
        BlueprintData.Plan plan=new BlueprintData.Plan(UUID.randomUUID(),faction.id(),KingdomData.dim(level),name,w,h,d);
        int skipped=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int z=0;z<d;z++){
            BlockPos pos=min.offset(x,y,z);
            if(!level.hasChunkAt(pos))return no("Load all chunks containing the building before saving.");
            if(!canUse(level,faction.id(),plan.dimension,pos))return no("The whole selection must be owned by your faction and outside banner plazas.");
            BlockState state=level.getBlockState(pos);if(state.isAir())continue;
            // Exclude inventories, signs, chests, fluids and unsupported blocks rather than
            // rejecting the entire building. This is safe: none of their data is copied.
            if(level.getBlockEntity(pos)!=null || !state.getFluidState().isEmpty() || state.getBlock().asItem()==Items.AIR){skipped++;continue;}
            if(plan.blocks.size()>=BlueprintData.MAX_BLOCKS)return no("Maximum 4096 non-air blocks in a blueprint.");
            plan.blocks.add(new BlueprintData.Piece(x,y,z,NbtUtils.writeBlockState(state)));
        }
        if(plan.blocks.isEmpty())return no("No supported building blocks found. Containers, signs, fluids and other block entities cannot be copied.");
        plan.folder=BlueprintData.suggestedFolder(plan);
        data.plans.put(plan.id,plan);data.setDirty();sel.a=null;sel.b=null;
        HousingMath.Result housing=plan.housingReport();
        return yes("Saved '"+name+"' ("+w+"x"+h+"x"+d+", "+plan.blocks.size()+" blocks"+(skipped>0?", "+skipped+" unsupported blocks skipped":"")+"). " +
                (housing.qualifies()?"Housing: "+housing.capacity()+" residents ("+housing.usableFloor()+" indoor floor tiles, "+housing.beds()+" complete beds).":
                    "Housing: 0 (needs at least 9 usable enclosed, roofed floor tiles).") + " Open Buildings to place it.");
    }
    public static Result delete(ServerPlayer p,String name){
        BarracksService.Faction f=atPlayer(p);if(f==null)return no("BUILD permission required inside your claim.");
        BlueprintData data=BlueprintData.get(p.getServer());BlueprintData.Plan plan=data.named(f.id(),name);
        if(plan==null)return no("Blueprint not found.");
        for(BlueprintData.Site site:data.sites.values())if(site.planId.equals(plan.id))return no("Blueprint has active construction sites; finish them first.");
        data.plans.remove(plan.id);data.setDirty();return yes("Removed blueprint '"+plan.name+"'.");
    }
    public static int width(BlueprintData.Plan p,int turns){return (turns&1)==0?p.width:p.depth;}
    public static int depth(BlueprintData.Plan p,int turns){return (turns&1)==0?p.depth:p.width;}
    public static BlockPos target(BlueprintData.Plan p,BlockPos anchor,int turns,BlueprintData.Piece piece){
        return switch(turns&3){
            case 1->anchor.offset(p.depth-1-piece.z,piece.y,piece.x);
            case 2->anchor.offset(p.width-1-piece.x,piece.y,p.depth-1-piece.z);
            case 3->anchor.offset(piece.z,piece.y,p.width-1-piece.x);
            default->anchor.offset(piece.x,piece.y,piece.z);
        };
    }
    private static BlockState state(BlueprintData.Piece piece,int turns){
        BlockState b=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),piece.state);
        try {
            return b.rotate(new Rotation[]{Rotation.NONE,Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.COUNTERCLOCKWISE_90}[turns&3]);
        } catch (RuntimeException incompatibleRotation) {
            // Exotic blocks may not implement a valid rotation. Preserve the stored state.
            return b;
        }
    }
    /** Refuse collisions instead of deleting existing builds or copying any inventories. */
    private static String preflight(ServerLevel level,BlueprintData.Plan plan,BlockPos anchor,int turns){
        if(anchor.getY()<level.getMinBuildHeight()||anchor.getY()+plan.height>level.getMaxBuildHeight())return "House would exceed the world height limits.";
        String dimension=KingdomData.dim(level);
        for(int x=0;x<width(plan,turns);x++)for(int z=0;z<depth(plan,turns);z++){
            BlockPos pos=anchor.offset(x,0,z);
            if(!level.hasChunkAt(pos))return "Construction chunks must be loaded.";
            if(!canUse(level,plan.owner,dimension,pos))return "Entire footprint must be directly claimed by this faction.";
        }
        for(BlueprintData.Piece piece:plan.blocks){
            BlockPos pos=target(plan,anchor,turns,piece);
            if(!level.hasChunkAt(pos))return "Construction chunks must be loaded.";
            if(!canUse(level,plan.owner,dimension,pos))return "Construction crosses another claim or protected banner plaza.";
            BlockState old=level.getBlockState(pos);
            if(level.getBlockEntity(pos)!=null||!old.getFluidState().isEmpty()||(!old.isAir()&&!old.canBeReplaced()))
                return "Clear obstructing blocks at "+pos.toShortString()+" before construction.";
        }
        return null;
    }
    private static Map<Item,Integer> requirements(BlueprintData.Plan plan){
        Map<Item,Integer> cost=new LinkedHashMap<>();
        for(BlueprintData.Piece piece:plan.blocks){
            BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),piece.state);
            // Double-height structures are one inventory item (door/tall plant), not two.
            if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    &&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                    ==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)continue;
            if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                    &&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                    ==net.minecraft.world.level.block.state.properties.BedPart.HEAD)continue;
            cost.merge(state.getBlock().asItem(),1,Integer::sum);
        }return cost;
    }
    private static int count(ServerPlayer p,Item item){int count=0;for(var stack:p.getInventory().items)if(stack.is(item))count+=stack.getCount();return count;}
    /** Player-view placement checks actual physical proximity, rather than distance to the manager. */
    public static Result beginInWorld(ServerPlayer player,UUID planId,BlockPos anchor,int turns){
        BarracksService.Faction faction=atPlayer(player);
        if(faction==null)return no("Stand inside your faction's claims with BUILD permission.");
        if(player.distanceToSqr(anchor.getX()+.5,anchor.getY()+.5,anchor.getZ()+.5)>96*96)
            return no("Move closer to the house placement (within 96 blocks).");
        return beginValidated(player,faction,planId,anchor,turns);
    }
    public static Result begin(ServerPlayer player,BlockPos manager,UUID planId,BlockPos anchor,int turns){
        BarracksService.Faction faction=managerFaction(player,manager);if(faction==null)return no("Stand within 8 blocks of your Kingdom Manager with BUILD permission.");
        if(Math.abs(anchor.getX()-manager.getX())>128||Math.abs(anchor.getZ()-manager.getZ())>128)return no("Place buildings within 128 blocks of your Manager.");
        return beginValidated(player,faction,planId,anchor,turns);
    }
    private static Result beginValidated(ServerPlayer player,BarracksService.Faction faction,UUID planId,BlockPos anchor,int turns){
        BlueprintData data=BlueprintData.get(player.getServer());BlueprintData.Plan plan=data.plans.get(planId);
        if(plan==null||!plan.owner.equals(faction.id()))return no("Blueprint unavailable to your faction.");
        if(!plan.dimension.equals(KingdomData.dim(player.serverLevel())))return no("Blueprint belongs to a different dimension.");
        if(turns<0||turns>3)return no("Invalid rotation.");
        if(data.active(faction.id())>=12)return no("Maximum 12 active construction sites per faction.");
        for(BlueprintData.Site existing:data.sites.values())if(existing.dimension.equals(plan.dimension)){
            BlueprintData.Plan e=data.plans.get(existing.planId);if(e==null)continue;
            AABB a=new AABB(anchor.getX(),anchor.getY(),anchor.getZ(),anchor.getX()+width(plan,turns),anchor.getY()+plan.height,anchor.getZ()+depth(plan,turns));
            AABB b=new AABB(existing.anchor.getX(),existing.anchor.getY(),existing.anchor.getZ(),existing.anchor.getX()+width(e,existing.turns),existing.anchor.getY()+e.height,existing.anchor.getZ()+depth(e,existing.turns));
            if(a.intersects(b))return no("That location overlaps an active construction site.");
        }
        String issue=preflight(player.serverLevel(),plan,anchor,turns);if(issue!=null)return no(issue);
        Map<Item,Integer> costs=requirements(plan);
        if(!player.getAbilities().instabuild){
            String paymentIssue=PhysicalWarehouses.payConstruction(player,faction.id(),anchor,costs);
            if(paymentIssue!=null)return no(paymentIssue);
        }
        BlueprintData.Site site=new BlueprintData.Site(UUID.randomUUID(),plan,player.getUUID(),plan.dimension,anchor,turns);
        data.sites.put(site.id,site);data.setDirty();
        return yes("Construction started: "+plan.name+" ("+plan.blocks.size()+" blocks, "+site.secondsRemaining+" seconds). Materials paid from local warehouse and carried items.");
    }
    /** Stable ordering makes construction visibly grow from its foundation, even after a restart. */
    private static List<BlueprintData.Piece> constructionOrder(BlueprintData.Plan plan){
        List<BlueprintData.Piece> pieces=new ArrayList<>(plan.blocks);
        pieces.sort(Comparator.comparingInt((BlueprintData.Piece p)->p.y)
            .thenComparingInt(p->p.x).thenComparingInt(p->p.z));
        return pieces;
    }
    /**
     * Active construction cannot reuse the initial preflight verbatim: finished portions are
     * deliberately non-air. Verify each previously laid block and each unbuilt location.
     * If land changes hands, is unloaded, or a site is damaged, pause without overwriting.
     */
    private static String activePreflight(ServerLevel level,BlueprintData.Plan plan,BlueprintData.Site site,
                                           List<BlueprintData.Piece> ordered){
        if(site.anchor.getY()<level.getMinBuildHeight()||site.anchor.getY()+plan.height>level.getMaxBuildHeight())
            return "World height limit";
        String dimension=KingdomData.dim(level);
        for(int x=0;x<width(plan,site.turns);x++)for(int z=0;z<depth(plan,site.turns);z++){
            BlockPos pos=site.anchor.offset(x,0,z);
            if(!level.hasChunkAt(pos)||!canUse(level,plan.owner,dimension,pos))return "Unloaded or unclaimed construction land";
        }
        for(int i=0;i<ordered.size();i++){
            BlueprintData.Piece part=ordered.get(i);
            BlockPos pos=target(plan,site.anchor,site.turns,part);
            if(!level.hasChunkAt(pos)||!canUse(level,plan.owner,dimension,pos))return "Construction land unavailable";
            BlockState current=level.getBlockState(pos);
            if(i<site.placedBlocks){
                if(!current.equals(state(part,site.turns)))return "Previously built blocks were changed";
            } else if(level.getBlockEntity(pos)!=null||!current.getFluidState().isEmpty()
                    ||(!current.isAir()&&!current.canBeReplaced()))return "New obstruction";
        }
        return null;
    }
    public static void tick(MinecraftServer server){
        BlueprintData data=BlueprintData.get(server);if(data.sites.isEmpty())return;
        boolean modified=false;
        for(var iterator=data.sites.entrySet().iterator();iterator.hasNext();){
            BlueprintData.Site site=iterator.next().getValue();BlueprintData.Plan plan=data.plans.get(site.planId);
            if(plan==null){iterator.remove();modified=true;continue;}
            ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(site.dimension)));
            if(level==null)continue;
            List<BlueprintData.Piece> ordered=constructionOrder(plan);
            site.placedBlocks=Math.min(Math.max(0,site.placedBlocks),ordered.size());
            if(activePreflight(level,plan,site,ordered)!=null)continue; // safely pause, never overwrite
            if(site.secondsRemaining>0)site.secondsRemaining--;
            // Each second reveals another part of the actual structure, from floor to roof.
            int totalSeconds=Math.max(1,plan.seconds());
            int elapsed=Math.max(1,totalSeconds-site.secondsRemaining);
            int targetPlaced=Math.min(ordered.size(),Math.max(site.placedBlocks,
                (int)(((long)ordered.size()*elapsed+totalSeconds-1)/totalSeconds)));
            if(site.secondsRemaining==0)targetPlaced=ordered.size();
            BlockPos effect=null;
            while(site.placedBlocks<targetPlaced){
                BlueprintData.Piece piece=ordered.get(site.placedBlocks);
                BlockPos pos=target(plan,site.anchor,site.turns,piece);
                if(!level.setBlock(pos,state(piece,site.turns),2))break;
                PlayerBuildData.get(level).add(pos);
                site.placedBlocks++;
                effect=pos;
            }
            if(effect!=null){
                level.sendParticles(ParticleTypes.POOF,effect.getX()+.5,effect.getY()+.7,effect.getZ()+.5,
                    5,.35,.3,.35,.025);
                level.playSound(null,effect,SoundEvents.WOOD_PLACE,SoundSource.BLOCKS,.32f,1.0f);
            }
            modified=true;
            if(site.placedBlocks<ordered.size())continue;
            // Only completed houses supply residents; construction sites do not.
            data.built.put(site.id,new BlueprintData.Built(site.id,plan.owner,plan.dimension,site.anchor,plan.id,plan.housingSlots()));
            iterator.remove();
            ServerPlayer player=server.getPlayerList().getPlayer(site.player);
            if(player!=null)player.sendSystemMessage(Component.literal("[Kingdom Architect] Finished "+plan.name+
                    " ("+site.placedBlocks+" blocks) at "+site.anchor.toShortString()+
                    ". Housing capacity +"+plan.housingSlots()+"."));
        }
        if(modified)data.setDirty();
    }
}
