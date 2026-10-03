package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** A retained, registered wrapper invokes its own constructor method after its map row is unlinked. */
public final class AliasMethodFixture {
    private static final class Recipe {
        private final CompoundTag snapshot;
        private int attempts;
        Recipe(Cow source){snapshot=source.saveWithoutId(new CompoundTag());}
        Cow create(ServerLevel level) {
            attempts++;
            Cow result=new Cow(EntityType.COW,level);
            result.setPos(snapshot.getList("Pos",6).getDouble(0),90,15);
            result.setNoAi(true);return result;
        }
        Cow mixed(ServerLevel level,Cow other) {
            attempts++;
            Cow result=new Cow(EntityType.COW,level);result.setPos(other.getX()+2,90,15);return result;
        }
    }
    private static Cow create(Recipe recipe,ServerLevel level) {
        recipe.attempts++;
        Cow result=new Cow(EntityType.COW,level);result.setPos(recipe.snapshot.getList("Pos",6).getDouble(0),90,15);return result;
    }
    private static final class Data extends SavedData {
        private final Map<String,Recipe> recipes=new HashMap<>();
        @Override public CompoundTag save(CompoundTag root) {
            for(var entry:recipes.entrySet())root.put(entry.getKey(),entry.getValue().snapshot.copy());
            return root;
        }
    }
    private final Data data=new Data();
    private final List<String> facts=new ArrayList<>();
    private Recipe retained,other;
    private Cow target,neighbor;
    private UUID operation;
    private int age;
    private boolean finished;
    AliasMethodFixture(){MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Cow spawn(ServerLevel level,int x) {
        Cow value=new Cow(EntityType.COW,level);value.setPos(x,90,15);value.setNoAi(true);
        require(level.addFreshEntity(value),"ordinary spawn");return value;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        ProRuntime runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        try {
            if(++age>350)throw new AssertionError("ALIAS_METHOD_TIMEOUT:"+runtime.recoveryStatus(operation));
            ServerLevel level=event.getServer().overworld();
            if(age==20) {
                target=spawn(level,30);neighbor=spawn(level,36);
                retained=new Recipe(target);other=new Recipe(neighbor);data.recipes.put("target",retained);data.recipes.put("neighbor",other);
                level.getDataStorage().set("alias_method_fixture",data);
                operation=FixtureCommands.clear(runtime,target);
            }
            if(operation==null||age<40||data.recipes.containsKey("target"))return;
            require(level.getEntity(target.getUUID())==null,"old body disposed");
            Cow candidate=retained.create(level);
            require(candidate==null&&retained.attempts==0,"exact retained factory must be refused before its first business instruction");
            require(create(retained,level)==null&&retained.attempts==0,"static factory source input must be refused before its first business instruction");
            facts.add("BOUND_RETAINED_INSTANCE_AND_STATIC_FACTORIES_REFUSED_BEFORE_BODY");
            Cow ordinary=other.create(level);
            require(ordinary!=null&&other.attempts==1&&level.addFreshEntity(ordinary),"same factory with another source remains usable");
            Cow mixed=retained.mixed(level,neighbor);
            require(mixed!=null&&retained.attempts==1&&level.addFreshEntity(mixed),"mixed-source invocation must not be refused as a single target");
            facts.add("SAME_FACTORY_NEIGHBOUR_AND_MIXED_INPUTS_PRESERVED");
            // An ambient read in the same outer fixture method cannot claim an unrelated allocation.
            double harmless=retained.snapshot.getList("Pos",6).getDouble(0);
            Cow independent=spawn(level,(int)harmless+10);
            require(level.getEntity(independent.getUUID())==independent,"ambient read captured unrelated construction");
            facts.add("AMBIENT_READ_DOES_NOT_CLAIM_NEIGHBOUR");
            require(level.getEntity(neighbor.getUUID())==neighbor,"pre-existing neighbour preserved");
            require(runtime.recoveryStatus(operation).stream().anyMatch(line->line.contains("RETAINED_EXTERNAL_ALIAS_METHOD_STILL_LIVE")),
                    "live detached alias falsely counted as disposed");
            facts.add("RETAINED_ALIAS_OBLIGATION_STAYS_OPEN");
            FixtureCommands.revoke(runtime,target);
            Cow released=retained.create(level);
            require(released!=null&&retained.attempts==2&&level.addFreshEntity(released),"current revoke must immediately release the factory entry");
            facts.add("FACTORY_GATE_RELEASES_ON_CURRENT_REVOKE");
            finish(event,"ALIAS_METHOD_PASS\n"+String.join("\n",facts));
        }catch(Throwable failure){failure.printStackTrace();finish(event,"FAILED\n"+failure+"\n"+
                (operation==null?"":String.join("\n",runtime.recoveryStatus(operation))));}
    }
    private void finish(TickEvent.ServerTickEvent event,String result) {
        finished=true;try {Files.writeString(Path.of("alias-method-result.txt"),result);}catch(Exception unavailable){unavailable.printStackTrace();}
        event.getServer().halt(false);
    }
}
