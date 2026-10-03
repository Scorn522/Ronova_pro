package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Business tasks only. All qualification, cancellation, exits and release receipts are production-owned. */
final class TaskMatrixFixture {
    private final ExecutorService pool=Executors.newFixedThreadPool(4);
    private final ScheduledThreadPoolExecutor scheduled=new ScheduledThreadPoolExecutor(1);
    private final ForkJoinPool forks=new ForkJoinPool(2);
    private final CountDownLatch running=new CountDownLatch(4),release=new CountDownLatch(1),childRelease=new CountDownLatch(1);
    private final AtomicInteger interruptions=new AtomicInteger(),periods=new AtomicInteger(),parentEnded=new AtomicInteger(),childEnded=new AtomicInteger(),unrelated=new AtomicInteger();
    private final List<Future<?>> handles=new ArrayList<>();
    private Cow target;
    private UUID operation;
    private int tick,phase,entered,stoppedPeriods=-1;
    private boolean finished;
    private final boolean aOnly;
    TaskMatrixFixture() {this(false);}
    TaskMatrixFixture(boolean aOnly) {this.aOnly=aOnly;MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick);}
    private static void await(CountDownLatch latch,AtomicInteger interruptions) {
        boolean done=false;while(!done)try { latch.await();done=true; }catch(InterruptedException failure) { interruptions.incrementAndGet(); }
    }
    private static final class Business implements Runnable {
        final Cow target;final CountDownLatch started,release;final AtomicInteger ended,interruptions;
        final ExecutorService successorPool;final boolean parent;
        Business(Cow target,CountDownLatch started,CountDownLatch release,AtomicInteger ended,AtomicInteger interruptions,ExecutorService successorPool,boolean parent) {
            this.target=target;this.started=started;this.release=release;this.ended=ended;this.interruptions=interruptions;this.successorPool=successorPool;this.parent=parent;
        }
        public void run() {
            started.countDown();await(release,interruptions);
            try { if(parent)successorPool.submit(new Late(target)); }
            finally { ended.incrementAndGet(); }
        }
    }
    private static final class Late implements Runnable {
        final Cow target;Late(Cow target) { this.target=target; }
        public void run() { target.saveWithoutId(new CompoundTag()).copy(); }
    }
    private static final class Periodic implements Runnable {
        final Cow target;final AtomicInteger periods;Periodic(Cow target,AtomicInteger periods) { this.target=target;this.periods=periods; }
        public void run() { periods.incrementAndGet(); }
    }
    private static final class ForkBusiness extends RecursiveAction {
        final Cow target;final CountDownLatch started,release;final AtomicInteger interruptions;
        ForkBusiness(Cow target,CountDownLatch started,CountDownLatch release,AtomicInteger interruptions) { this.target=target;this.started=started;this.release=release;this.interruptions=interruptions; }
        protected void compute() { started.countDown();await(release,interruptions);new ForkLeaf(target).fork(); }
    }
    private static final class ForkLeaf extends RecursiveAction {
        final Cow target;ForkLeaf(Cow target) { this.target=target; }
        protected void compute() { target.saveWithoutId(new CompoundTag()).copy(); }
    }
    private static final class Produce implements Supplier<Integer> {
        final Cow target;final CountDownLatch started,release;final AtomicInteger interruptions;
        Produce(Cow target,CountDownLatch started,CountDownLatch release,AtomicInteger interruptions) { this.target=target;this.started=started;this.release=release;this.interruptions=interruptions; }
        public Integer get() { started.countDown();await(release,interruptions);return 5; }
    }
    private static final class Transform implements Function<Integer,Integer> {
        final Cow target;Transform(Cow target) { this.target=target; }
        public Integer apply(Integer value) { return value+1; }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(tick>1800)throw new AssertionError("TASK_MATRIX_TIMEOUT phase="+phase+" STATUS="+(operation==null?"none":String.join(" | ",runtime.recoveryStatus(operation))));
            if(phase==0) {
                var level=event.getServer().overworld();level.setChunkForced(0,0,true);
                target=new Cow(EntityType.COW,level);target.setNoAi(true);target.setPos(3,80,3);
                require(level.addFreshEntity(target),"SPAWN");target.saveWithoutId(new CompoundTag());
                handles.add(pool.submit(new Business(target,running,release,parentEnded,interruptions,pool,true)));
                handles.add(pool.submit(new Business(target,running,childRelease,childEnded,interruptions,pool,false)));
                handles.add(forks.submit(new ForkBusiness(target,running,release,interruptions)));
                var first=CompletableFuture.supplyAsync(new Produce(target,running,release,interruptions),pool);
                var second=first.thenApplyAsync(new Transform(target),pool);
                handles.add(first);handles.add(second);handles.add(CompletableFuture.allOf(first,second));handles.add(CompletableFuture.anyOf(first,second));
                handles.add(scheduled.scheduleAtFixedRate(new Periodic(target,periods),0,20,TimeUnit.MILLISECONDS));
                handles.add(scheduled.schedule(new Late(target),1,TimeUnit.DAYS));
                phase=1;
            } else if(phase==1&&running.getCount()==0&&periods.get()>=2) {
                operation=FixtureCommands.clear(runtime,target);phase=2;entered=tick;
            } else if(phase==2&&tick-entered>80) {
                require(!settled(runtime),"RUNNING_TASKS_CANNOT_SETTLE");
                require(parentEnded.get()==0&&childEnded.get()==0,"CANCEL_IS_NOT_ACTUAL_EXIT");
                require(interruptions.get()==0,"SHARED_WORKER_INTERRUPTED");
                release.countDown();phase=3;entered=tick;
            } else if(phase==3&&tick-entered>40&&parentEnded.get()==1) {
                require(!settled(runtime)&&childEnded.get()==0,"PARENT_EXIT_CANNOT_PAY_CHILD");
                childRelease.countDown();phase=4;
            } else if(phase==4&&(aOnly?taskPathClosed(runtime):settled(runtime))) {
                require(childEnded.get()==1&&interruptions.get()==0,"ACTUAL_EXIT_AND_NO_INTERRUPT");
                require(!pool.isShutdown()&&!scheduled.isShutdown()&&!forks.isShutdown(),"SHARED_POOL_CLOSED");
                pool.submit(unrelated::incrementAndGet);scheduled.execute(unrelated::incrementAndGet);forks.submit(unrelated::incrementAndGet);
                stoppedPeriods=periods.get();phase=5;entered=tick;
            } else if(phase==5&&tick-entered>=30&&unrelated.get()==3) {
                require(periods.get()==stoppedPeriods,"PERIODIC_RESCHEDULE_REMAINS");
                require(aOnly?taskPathClosed(runtime):settled(runtime),"STABLE_WINDOW_LOST");
                finish(event,runtime,aOnly?
                        "TASK_A_PATH_PASS\nFULL_RECOVERY_CHAIN_PENDING_NOT_CERTIFIED\n":
                        "TASK_MATRIX_PRODUCTION_PASS\nFUTURE_RUNNING_PERIODIC_FORKJOIN_CF_ALL_ANY_LATE_SUCCESSOR_SHARED_POOLS\n");
            }
        } catch(Throwable failure) { failure.printStackTrace();finish(event,runtime,"FAILED\n"+failure+"\n"); }
    }
    private boolean settled(ProRuntime runtime) { return runtime.recoveryStatus(operation).contains("已知恢复链已处置"); }
    private boolean taskPathClosed(ProRuntime runtime) {
        if(operation==null)return false;
        var status=runtime.recoveryStatus(operation);
        boolean backendReady=status.stream().anyMatch(row->row.equals(
                "TASK_BACKEND:AGENT_LATE_ATTACH_COVERAGE_GAP:PRE_ATTACH_ACTIVITY_NOT_OBSERVED:INSTALLED_FULL")
                ||row.equals("TASK_BACKEND:AGENT_EXISTING_STATE:INSTALLED_FULL"));
        if(!backendReady||status.stream().anyMatch(row->row.startsWith("TASK_GAP:")||row.startsWith("TASK:")
                ||row.startsWith("TASK_EVIDENCE_PENDING")||row.startsWith("CORE_REFERENCE_RELEASE_ACK:")))return false;
        var history=runtime.durableHistory();
        return history.stream().filter(row->row.startsWith("TASK/6\tINTENT\t")).count()>=8
                &&history.stream().anyMatch(row->row.contains("START_PRECLUDED"))
                &&history.stream().anyMatch(row->row.contains("EXIT_OBSERVED"));
    }
    private void finish(TickEvent.ServerTickEvent event,ProRuntime runtime,String result) {
        finished=true;
        // Save the production result before fixture cleanup changes task state.
        try { Files.writeString(Path.of(aOnly?"task-a-result.txt":"task-matrix-result.txt"),
                result+String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
        catch(Exception failure) { failure.printStackTrace(); }
        finally {
            release.countDown();childRelease.countDown();
            // The fixture owns these pools. Do not leave the one-day delayed task alive
            // after a failed run, and never interrupt tasks as a substitute for their exit.
            for(Future<?> handle:handles)handle.cancel(false);
            scheduled.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            scheduled.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            pool.shutdown();scheduled.shutdown();forks.shutdown();
        }
        event.getServer().halt(false);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
