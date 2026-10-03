package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Real pool and server ticks. It only creates work, invokes clear, and reads production diagnostics. */
final class TaskBurstFixture {
    private final ExecutorService pool=Executors.newSingleThreadExecutor(r->new Thread(r,"pro-fixture-owned-worker"));
    private final CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
    private final AtomicInteger queuedBodies=new AtomicInteger(),controlBodies=new AtomicInteger(),interrupts=new AtomicInteger();
    private final Set<String> receipts=new HashSet<>();
    private UUID operation;
    private Cow target;
    private int tick,phase;
    private boolean finished;
    TaskBurstFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    private static final class EmptyData extends net.minecraft.world.level.saveddata.SavedData {
        @Override public CompoundTag save(CompoundTag tag) { return tag; }
    }
    private static final class Business implements Runnable {
        final Cow target;
        final CountDownLatch started,release;
        final AtomicInteger bodies,interrupts;
        Business(Cow target,CountDownLatch started,CountDownLatch release,AtomicInteger bodies,AtomicInteger interrupts) {
            this.target=target;this.started=started;this.release=release;this.bodies=bodies;this.interrupts=interrupts;
        }
        @Override public void run() {
            if(started!=null)started.countDown();
            if(release!=null) {
                boolean done=false;while(!done)try { release.await();done=true; }catch(InterruptedException unexpected){interrupts.incrementAndGet();}
            } else bodies.incrementAndGet();
        }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(tick>2400)throw new AssertionError("TASK_BURST_TIMEOUT phase="+phase+" receipts="+receipts.size());
            if(phase==0) {
                ServerLevel level=event.getServer().overworld();
                // Ordinary absent read -> real data replacement. Forge inserts DUMMY for the miss.
                // A new business key exercises that transition even when this isolated world is reused.
                level.getDataStorage().computeIfAbsent(tag->new EmptyData(),EmptyData::new,"fixture-cache-miss-"+UUID.randomUUID());
                level.setChunkForced(0,0,true);
                target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(2,80,2);
                if(!level.addFreshEntity(target))throw new AssertionError("ordinary spawn failed");
                target.saveWithoutId(new CompoundTag()); // Ordinary business serialization, not a test binding.
                pool.submit(new Business(target,started,release,queuedBodies,interrupts));
                for(int i=0;i<352;i++)pool.submit(new Business(target,null,null,queuedBodies,interrupts));
                AtomicInteger unrelated=controlBodies;pool.submit((Runnable)unrelated::incrementAndGet);
                phase=1;
            } else if(phase==1&&started.getCount()==0) {
                operation=FixtureCommands.clear(runtime,target);phase=2;
            } else if(phase==2) {
                if(pool.isShutdown()||interrupts.get()!=0)throw new AssertionError("shared fixture pool/worker was affected");
                for(String row:runtime.recoveryStatus(operation))if(row.contains(":currentScope=true:factDurable=true"))receipts.add(row.substring(0,row.indexOf(':')));
                if(receipts.size()>=352) { release.countDown();phase=3; }
            } else if(phase==3&&controlBodies.get()==1&&runtime.recoveryStatus(operation).contains("已知恢复链已处置")) {
                if(queuedBodies.get()!=0||interrupts.get()!=0)throw new AssertionError("queued body ran or shared worker interrupted");
                finished=true;pool.shutdown();
                Files.writeString(Path.of("task-burst-result.txt"),"LOCAL_TASK_BURST_SCOPES_OBSERVED="+receipts.size()+
                        "\nQUEUED_BODIES=0\nUNRELATED_BODY=1\nWORKER_INTERRUPTS=0\nTASK_BURST_KNOWN_CHAIN_SETTLED\n"+String.join("\n",runtime.recoveryStatus(operation)));
                event.getServer().halt(false);
            }
        } catch(Throwable failure) {
            finished=true;release.countDown();pool.shutdown();
            try { Files.writeString(Path.of("task-burst-result.txt"),"FAILED\n"+failure+"\n"+String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
            catch(java.io.IOException writeFailure) { failure.addSuppressed(writeFailure); }
            failure.printStackTrace();
            event.getServer().halt(false);
        }
    }
}
