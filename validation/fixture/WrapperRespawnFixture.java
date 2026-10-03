package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Unsaved ordinary SavedData, a POJO-wrapped snapshot and a constructor-only respawn.
 * This negative test must not certify termination while that unsupported source remains live. */
public final class WrapperRespawnFixture {
    private static final class Record {
        private final CompoundTag snapshot;
        private UUID current;
        private int missing;
        Record(Cow cow) { snapshot=cow.saveWithoutId(new CompoundTag());current=cow.getUUID(); }
    }
    private static final class Data extends SavedData {
        private final Map<UUID,Record> records=new HashMap<>();
        @Override public CompoundTag save(CompoundTag root) {
            ListTag list=new ListTag();
            for(Record record:records.values()) {
                CompoundTag entry=new CompoundTag();entry.putUUID("key",record.current);
                entry.put("snapshot",record.snapshot.copy());entry.putString("unknown","preserve");list.add(entry);
            }
            root.put("entries",list);return root;
        }
    }
    private final Data data=new Data();
    private Cow target,neighbor;
    private UUID operation,original;
    private Record record;
    private int age,started,respawns;
    private boolean finished;
    public WrapperRespawnFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    private Cow spawn(ServerLevel level,double x) {
        Cow cow=new Cow(EntityType.COW,level);cow.setPos(x,100,0);cow.setNoAi(true);
        if(!level.addFreshEntity(cow))throw new AssertionError("business spawn rejected");return cow;
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        try {
            if(++age==20) {
                var level=event.getServer().overworld();target=spawn(level,2);neighbor=spawn(level,6);
                original=target.getUUID();record=new Record(target);data.records.put(original,record);
                level.getDataStorage().set("wrapper_respawn_fixture",data);
                // Deliberately no file save: the normal in-memory source already exists.
                operation=FixtureCommands.clear(runtime,target);started=age;
            }
            if(operation==null)return;
            var level=event.getServer().overworld();
            if(level.getEntity(record.current)==null&&++record.missing>=60) {
                target=spawn(level,record.snapshot.getList("Pos",6).getDouble(0));
                record.current=target.getUUID();record.missing=0;respawns++;
            }
            var status=runtime.recoveryStatus(operation);
            if(status.contains("已知恢复链已处置"))throw new AssertionError("FALSE_CHAIN_RECEIPT_BEFORE_DELAYED_RESPAWN age="+(age-started));
            if(age-started<100)return;
            if(respawns==0||record.current.equals(original)||level.getEntity(record.current)!=target)
                throw new AssertionError("constructor-only changed-UUID respawn not exercised");
            if(level.getEntity(neighbor.getUUID())!=neighbor)throw new AssertionError("unrelated entity affected");
            if(data.records.containsKey(original))throw new AssertionError("wrapped HashMap source was not physically unlinked");
            if(status.stream().noneMatch(s->s.contains("UNCLASSIFIED_NEW_BODY_SAME_DEFINITION")))
                throw new AssertionError("actual new body gap missing");
            Files.writeString(Path.of("wrapper-respawn-result.txt"),"NEGATIVE_PASS\nUNSAVED_WRAPPED_SOURCE_DISCOVERED\n"+
                    "NO_FALSE_CHAIN_RECEIPT\nCONSTRUCTOR_ONLY_CHANGED_UUID_RESPAWN_OBSERVED\nUNRELATED_ENTITY_PRESERVED\n"+
                    "RETAINED_EXTERNAL_ALIAS_CONSTRUCTOR_REMAINS_UNRESOLVED\n"+String.join("\n",status));
            finished=true;event.getServer().halt(false);
        } catch(Throwable error) {
            finished=true;
            try { Files.writeString(Path.of("wrapper-respawn-result.txt"),"FAILED\n"+error+"\n"+
                    String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
            catch(Exception failed) { error.addSuppressed(failed); }
            error.printStackTrace();event.getServer().halt(false);
        }
    }
}
