package dev.ronova.pro.validation;

import dev.ronova.pro.ProRuntime;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Own hostile virtual getters and separate loaders. It never loads the original comparison mods. */
final class StrengthFixture {
    public static final class AliasCow extends Cow {
        final UUID publicUuid=UUID.randomUUID();int ticks;
        AliasCow(net.minecraft.world.level.Level level) { super(EntityType.COW,level); }
        public UUID getUUID() { return publicUuid==null?super.getUUID():publicUuid; }
        public int getId() { return super.getId()+1000000; }
        public void remove(RemovalReason reason) { }
        public void tick() { ticks++;super.tick(); }
    }
    public static final class Shadow implements Runnable {
        public final Object target;public final AtomicInteger bodies;
        public Shadow(Object target,AtomicInteger bodies) { this.target=target;this.bodies=bodies; }
        public void run() { bodies.incrementAndGet(); }
    }
    private static final class DefinitionLoader extends ClassLoader {
        final byte[] definition;
        DefinitionLoader(byte[] definition) { super(Shadow.class.getClassLoader());this.definition=definition; }
        protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(!name.equals(Shadow.class.getName()))return super.loadClass(name,resolve);
            Class<?> loaded=findLoadedClass(name);if(loaded==null)loaded=defineClass(name,definition,0,definition.length);
            if(resolve)resolveClass(loaded);return loaded;
        }
    }
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private final CountDownLatch release=new CountDownLatch(1);
    private final AtomicInteger targeted=new AtomicInteger(),unrelated=new AtomicInteger(),interruptions=new AtomicInteger();
    private AliasCow target;private Cow neighbor;
    private Future<?> targetedHandle;
    private UUID operation,publicUuid;private int publicId,tick,phase,stoppedTicks,entered;
    private boolean finished;
    StrengthFixture() { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::tick); }
    private void tick(TickEvent.ServerTickEvent event) {
        if(finished||event.phase!=TickEvent.Phase.END)return;
        var runtime=ProRuntime.get(event.getServer());if(runtime==null)return;
        tick++;
        try {
            if(tick>1200)throw new AssertionError("STRENGTH_TIMEOUT phase="+phase);
            var level=event.getServer().overworld();
            if(phase==0) {
                level.setChunkForced(0,0,true);target=new AliasCow(level);target.setNoAi(true);target.setPos(4,80,4);
                neighbor=new Cow(EntityType.COW,level);neighbor.setNoAi(true);neighbor.setPos(8,80,4);
                require(level.addFreshEntity(target)&&level.addFreshEntity(neighbor),"SPAWN");
                publicUuid=target.getUUID();publicId=target.getId();target.saveWithoutId(new CompoundTag());
                CountDownLatch started=new CountDownLatch(1);CountDownLatch local=release;AtomicInteger interrupts=interruptions;
                pool.submit(()->{started.countDown();try { local.await(); }catch(InterruptedException failure) { interrupts.incrementAndGet(); }});
                require(started.await(3,TimeUnit.SECONDS),"POOL_BLOCKER");
                byte[] bytes;try(InputStream input=Shadow.class.getResourceAsStream("/"+Shadow.class.getName().replace('.','/')+".class")) { bytes=Objects.requireNonNull(input).readAllBytes(); }
                Class<?> first=new DefinitionLoader(bytes).loadClass(Shadow.class.getName()),second=new DefinitionLoader(bytes).loadClass(Shadow.class.getName());
                require(first!=second&&first.getName().equals(second.getName()),"SEPARATE_DEFINING_LOADERS");
                targetedHandle=pool.submit((Runnable)first.getConstructor(Object.class,AtomicInteger.class).newInstance(target,targeted));
                pool.submit((Runnable)second.getConstructor(Object.class,AtomicInteger.class).newInstance(neighbor,unrelated));
                operation=FixtureCommands.clear(runtime,target);phase=1;
            } else if(phase==1&&runtime.query(operation).complete()&&targetedHandle.isCancelled()) {
                require(level.getEntity(publicId)==null&&level.getEntity(publicUuid)==null,"PUBLIC_ALIAS_INDEX_REMAINED");
                require(level.getEntity(neighbor.getId())==neighbor,"UNRELATED_ENTITY_REMOVED");
                stoppedTicks=target.ticks;release.countDown();phase=2;entered=tick;
            } else if(phase==2&&tick-entered>=50&&unrelated.get()==1&&runtime.recoveryStatus(operation).contains("已知恢复链已处置")) {
                require(target.ticks==stoppedTicks&&targeted.get()==0&&interruptions.get()==0&&!pool.isShutdown(),"BODY_OR_TASK_CONTINUED_OR_SHARED_POOL_AFFECTED");
                finish(event,runtime,"STRENGTH_PRODUCTION_PASS\nVIRTUAL_REMOVE_UUID_ID_BYPASSED_BY_ACTUAL_REGISTRATION\nSAME_NAME_DIFFERENT_LOADERS_ISOLATED\nNEIGHBOR_AND_UNRELATED_TASK_PRESERVED\n");
            }
        } catch(Throwable failure) { failure.printStackTrace();finish(event,runtime,"FAILED\n"+failure+"\n"); }
    }
    private void finish(TickEvent.ServerTickEvent event,ProRuntime runtime,String result) {
        finished=true;release.countDown();pool.shutdown();
        try { Files.writeString(Path.of("strength-result.txt"),result+String.join("\n",operation==null?List.of():runtime.recoveryStatus(operation))); }
        catch(IOException failure) { failure.printStackTrace(); }
        event.getServer().halt(false);
    }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
