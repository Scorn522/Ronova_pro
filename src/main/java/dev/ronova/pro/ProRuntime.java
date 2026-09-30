package dev.ronova.pro;

import dev.ronova.pro.mixin.Access;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread authority: one work map, current bindings, shared SOFT guards and HARD final gate. */
public final class ProRuntime implements AutoCloseable {
    private static final Map<MinecraftServer,ProRuntime> SERVERS=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<net.minecraft.world.level.storage.DimensionDataStorage,ProRuntime> SOURCE_OWNERS=
            Collections.synchronizedMap(new IdentityHashMap<>());
    // Cross-thread policy snapshot: exact object -> published protection state. Readers never touch world state.
    private static final Map<Entity,ProtectionSnapshot> PROTECTION=
            Collections.synchronizedMap(new WeakIdentityMap<>());
    // Real-owner associations: a data item and an attribute instance do not point back to their entity, so the
    // association is captured where the game state is already being walked. No extra traversal is added.
    private static final Map<Object,java.lang.ref.WeakReference<Entity>> SYNC_OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    // Only actual world containers observed while installing protection enter this weak identity index.
    private record IndexSlot(java.lang.ref.WeakReference<Entity> entity,
            java.lang.ref.WeakReference<Object> value,Object key,long generation) { }
    private static final Map<Object,List<IndexSlot>> INDEX_CONTAINERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    // Conservative identity filter: collisions only cause an extra exact lookup, never grant a write.
    private static volatile long indexContainerMask;
    private static volatile String indexGuardState="NOT_INSTALLED";
    public static String indexGuardState() { return indexGuardState; }
    private record ProtectionSnapshot(ProRuntime runtime,Binding binding,UUID identity,long generation,EntityRecords records) { }
    private record PolicyAddress(int entityId,UUID entityUuid,String dimension) { }
    private final Map<PolicyAddress,Subject> terminalAddresses=new LinkedHashMap<>();
    private record SentPolicy(long revision,boolean protectedNow) { }
    private static final class ClientPolicy {
        long lastScope,scope,revision;
        String dimension="";
        boolean active,resetPending;
        final Map<UUID,Integer> hudSent=new HashMap<>();
        final Map<PolicyAddress,SentPolicy> sent=new HashMap<>(),terminals=new HashMap<>();
    }
    private static final java.util.concurrent.ScheduledExecutorService RETIREMENTS=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{
        Thread thread=new Thread(r,"ronova-pro-retirement");thread.setDaemon(true);return thread;
    });
    private boolean retirementScheduled;
    private int retirementFailures;
    private static final int CAPACITY=65536;
    public static ProRuntime get(MinecraftServer s) { return SERVERS.get(s); }
    public static void start(MinecraftServer s) throws IOException {
        lifecycleAction("starting");
        synchronized(SERVERS) {
            if(SERVERS.containsKey(s))throw new IOException("RUNTIME_ALREADY_STARTED");
            SERVERS.put(s,new ProRuntime(s));
        }
    }
    public static void stop(MinecraftServer s) throws IOException {
        lifecycleAction("stopped");
        ProRuntime r=SERVERS.get(s);
        if(r==null)return;
        try { r.close();synchronized(SERVERS) { if(SERVERS.get(s)==r)SERVERS.remove(s); } }
        catch(IOException pending) {
            // Durable shutdown receipts are asynchronous. Give ordinary ACKs a bounded
            // drain window before the dedicated server exits; live tasks remain unresolved.
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while(System.nanoTime()<deadline&&(pending.getMessage().contains("ACK_PENDING")||pending.getMessage().contains("EVENTS_PENDING"))) {
                try { Thread.sleep(10);r.finishClose();synchronized(SERVERS) { if(SERVERS.get(s)==r)SERVERS.remove(s); }return; }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt();break; }
                catch(IOException next) { pending=next; }
            }
            r.scheduleRetirement();throw pending;
        }
    }
    private synchronized void scheduleRetirement() {
        if(retirementScheduled)return;retirementScheduled=true;
        RETIREMENTS.schedule(this::retireStoppedRuntime,250,java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private void retireStoppedRuntime() {
        try {
            finishClose();
            synchronized(SERVERS) { if(SERVERS.get(server)==this)SERVERS.remove(server); }
        } catch(IOException|RuntimeException pending) {
            long delay=Math.min(5000,250L<<Math.min(5,retirementFailures++));
            RETIREMENTS.schedule(this::retireStoppedRuntime,delay,java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
    final MinecraftServer server;
    final IntentJournal journal;
    final RecoverySources recoverySources;
    final RecoveryStorage recoveryStorage;
    final RecoveryTasks recoveryTasks;
    final RecoveryRecords recoveryRecords;
    final RecoveryChain recoveryChain;
    final UUID session=UUID.randomUUID();
    private final Set<String> stoppedMods=new LinkedHashSet<>();
    private final Set<Module> stoppedModules=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> protectedMods=new LinkedHashSet<>();
    private final Set<Module> protectedModules=Collections.newSetFromMap(new IdentityHashMap<>());
    boolean groupClosing() { return closing; }
    boolean modStopped(Module module) { return !closing&&stoppedModules.contains(module); }
    boolean modProtected(Module module) { return !closing&&protectedModules.contains(module); }
    private final Set<Entity> pendingGroupAdoptions=Collections.newSetFromMap(new WeakIdentityMap<Entity,Boolean>());
    final Map<Entity,Binding> bindings=new WeakIdentityMap<>();
    final Map<CompoundTag,UUID> saved=new WeakIdentityMap<>();
    final Map<UUID,Work> work=new LinkedHashMap<>();
    final Map<UUID,Subject> subjects=new java.util.concurrent.ConcurrentHashMap<>();
    private long tick;
    private boolean coverageGap;
    private Entity removing;
    private static final java.lang.StackWalker LIFECYCLE_WALKER=java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final class LifecyclePermit {
        final ProRuntime runtime;final Thread thread;final ServerLevel destination;final Set<Entity> entities=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean closed;
        LifecyclePermit(Entity entity,ProRuntime runtime,Thread thread,ServerLevel destination) { this.runtime=runtime;this.thread=thread;this.destination=destination;entities.add(entity); }
        boolean allows(Entity entity) { return entities.contains(entity); }
    }
    private record PendingRespawnProtection(Binding binding,UUID playerUuid,LifecyclePermit permit,long generation) { }
    private static final ThreadLocal<ArrayDeque<LifecyclePermit>> PLAYER_LIFECYCLE=new ThreadLocal<>();
    private volatile boolean closing;
    /** Per actual network connection; sent rows are committed only after ProNetwork accepts the send. */
    private final Map<net.minecraft.server.network.ServerGamePacketListenerImpl,ClientPolicy> clientPolicies=new IdentityHashMap<>();
    /** Clone-derived SOFT protection waits for PlayerList's post-ID-assignment respawn event. */
    private final Map<ServerPlayer,PendingRespawnProtection> pendingRespawnProtections=new IdentityHashMap<>();
    private final Set<ServerPlayer> unverifiedRespawnPolicies=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String,List<String>> previousSessionTargets=new HashMap<>();

    static final class CommitGate extends java.util.concurrent.locks.ReentrantLock implements AutoCloseable {
        CommitGate enter() { lock();return this; }
        @Override public void close() { unlock(); }
    }
    static final class Subject {
        final UUID id;
        final CommitGate commitGate=new CommitGate();
        volatile long generation;
        volatile boolean terminal;
        final java.util.concurrent.atomic.AtomicLong sourceRevision=new java.util.concurrent.atomic.AtomicLong();
        final Set<String> unresolvedSources=new java.util.concurrent.ConcurrentSkipListSet<>();
        Subject() { this(UUID.randomUUID()); }
        Subject(UUID id) { this.id=id; }
    }
    static final class Binding {
        private Entity ownedEntity;
        private final java.lang.ref.WeakReference<Entity> entityIdentity;
        final Subject subject;
        final UUID incarnation=UUID.randomUUID();
        volatile PolicyAddress address;
        volatile EntityRecords records;
        volatile boolean protection;
        volatile float healthFloor;
        volatile long protectionGeneration;
        String protectionGap="";
        Work latest;
        UUID predecessor;
        Binding(Entity e,Subject s,EntityRecords r) {
            ownedEntity=e;entityIdentity=new java.lang.ref.WeakReference<>(e);subject=s;records=r;
            address=new PolicyAddress(r.id,r.uuid,r.level.dimension().location().toString());
        }
        Entity entity() { return entityIdentity.get(); }
        void releaseOwnedReference() { ownedEntity=null; }
    }
    private static final class Work {
        public final UUID id=UUID.randomUUID();
        final Binding binding;
        final BlockRecords block;
        final EntityRecords records;
        final long generation;
        final String action;
        final Map<UUID,ClientFact> clients=new HashMap<>();
        CompletableFuture<Void> ack;
        CompletableFuture<Void> factAck;
        CompletableFuture<Void> participantAck;
        String intentRecord, participantRecord, factRecord, receiptRecord;
        long retryAt;
        public String state="WAIT_ACK", reason="";
        UUID protectionSuccessor;
        public final Map<String,String> obligations=new LinkedHashMap<>();
        public final String recoveryScope="SERVER_THREAD_SAVE_COPY_LOAD_ONLY;ASYNC_AND_NO_NBT_UNKNOWN";
        public String lifecycleEffects="NOT_EXECUTED";
        long observedSourceRevision=-1;
        long stableSince=-1, lastSample=-1;
        boolean executed;
        boolean cancelled;
        CompletableFuture<Void> receiptAck;
        boolean dispatching;
        String resourceRecord;
        CompletableFuture<Void> resourceAck;
        boolean referencesReleased;
        Work(Binding b,BlockRecords block,long generation,String action) {
            this.binding=b; this.block=block; this.generation=generation; this.action=action;
            this.records=b==null?null:b.records;
        }
        public boolean complete() { return state.equals("BODY_CLEAR_STABLE"); }
    }
    public record Status(UUID id,String action,String state,String reason,boolean complete,
                         Map<String,String> obligations,String lifecycleEffects,String recoveryScope,boolean receiptDurable) {
        public Status { obligations=Map.copyOf(obligations); }
    }
    static final class ClientFact {
        UUID challenge=UUID.randomUUID();
        net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        long last=-1;
        boolean absent;
        long sequence=-1;
        boolean required=true;
    }

    private ProRuntime(MinecraftServer s) throws IOException {
        server=s;
        String bootSelection=System.getProperty("ronova.pro.bootStop","")+","+System.getenv().getOrDefault("RONOVA_PRO_BOOT_STOP","");
        for(String id:bootSelection.split(","))if(!id.isBlank()) {
            String normalized=id.trim().toLowerCase(Locale.ROOT);stoppedMods.add(normalized);
            net.minecraftforge.fml.ModList.get().getModContainerById(normalized).ifPresent(container->{
                Object instance=container.getMod();if(instance!=null)stoppedModules.add(instance.getClass().getModule());
            });
        }
        String protectSelection=System.getProperty("ronova.pro.bootProtect","")+","+System.getenv().getOrDefault("RONOVA_PRO_BOOT_PROTECT","");
        for(String id:protectSelection.split(","))if(!id.isBlank()) {
            String normalized=id.trim().toLowerCase(Locale.ROOT);protectedMods.add(normalized);
            net.minecraftforge.fml.ModList.get().getModContainerById(normalized).ifPresent(container->{
                Object instance=container.getMod();if(instance!=null)protectedModules.add(instance.getClass().getModule());
            });
        }
        journal=new IntentJournal(s.getWorldPath(LevelResource.ROOT).resolve("ronova-pro"));
        journal.registerSession(session); // Ordered before every new work intent by the single durable writer.
        recoverySources=new RecoverySources(this);
        recoveryStorage=new RecoveryStorage(this,recoverySources,s.getWorldPath(LevelResource.ROOT).resolve("ronova-pro"));
        recoveryTasks=new RecoveryTasks(this);
        recoveryRecords=new RecoveryRecords(this,recoverySources);
        recoveryChain=new RecoveryChain(this);
        // These are conservative location barriers, not inferred identity or fresh write permissions.
        for(String line:journal.history()) {
            String[] fields=line.split("\t",-1);
            if(!fields[0].equals("INTENT"))continue;
            try {
                if(fields.length<7)throw new IllegalArgumentException("short intent");
                String key;
                if(fields[2].equals("CLEAR")) {
                    var match=java.util.regex.Pattern.compile("(.+):([0-9a-fA-F-]{36}):([0-9a-fA-F-]{36})").matcher(fields[6]);
                    if(!match.matches())throw new IllegalArgumentException("unknown entity locator");
                    key="ENTITY|"+match.group(1)+"|"+UUID.fromString(match.group(2));
                } else if(fields[2].equals("BLOCK_CLEAR")) {
                    int at=fields[6].lastIndexOf(':');
                    key="BLOCK|"+fields[6].substring(0,at)+"|"+Long.parseLong(fields[6].substring(at+1));
                } else if(fields[2].equals("PROTECT"))continue;
                else throw new IllegalArgumentException("unknown action");
                previousSessionTargets.computeIfAbsent(key,k->new ArrayList<>()).add(fields[1]);
            } catch(RuntimeException unknown) { journal.refuseWrites("UNRECOGNIZED_PRIOR_INTENT_QUERY_ONLY"); }
        }
    }
    private void thread() { if(!server.isSameThread()) throw new IllegalStateException("SERVER_THREAD_REQUIRED"); }
    private static final java.lang.StackWalker ACTION_CALLER=
            java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static void lifecycleAction(String expected) {
        boolean authentic=ACTION_CALLER.walk(frames->frames
                .filter(frame->frame.getDeclaringClass()!=ProRuntime.class)
                .findFirst().map(frame->frame.getDeclaringClass()==ProMod.class
                        &&frame.getMethodName().equals(expected)).orElse(false));
        if(!authentic)throw new SecurityException("RONOVA_LIFECYCLE_AUTHORITY_REQUIRED:"+expected);
    }
    private static final class FieldPolicyGate implements AutoCloseable {
        private static final java.lang.reflect.Method ENTER,EXIT;
        static {
            try {
                Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
                ENTER=bridge.getMethod("beginPolicyMutation",Object.class);
                EXIT=bridge.getMethod("endPolicyMutation",Object.class);
            } catch(ReflectiveOperationException unavailable) {throw new ExceptionInInitializerError(unavailable);}
        }
        private final Object token;
        private FieldPolicyGate(Object receiver) {
            try {token=ENTER.invoke(null,receiver);}
            catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FIELD_POLICY_GATE_UNAVAILABLE",unavailable);}
        }
        static FieldPolicyGate enter(Object receiver){return new FieldPolicyGate(receiver);}
        @Override public void close() {
            try {EXIT.invoke(null,token);}
            catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FIELD_POLICY_GATE_RELEASE_FAILED",unavailable);}
        }
    }
    static <T> T withBlockIndexGate(Object container,java.util.function.Supplier<T> action) {
        try(var gate=FieldPolicyGate.enter(container)) {return action.get();}
    }
    /** Public request methods are command entrypoints, not an authority granted to every in-process mod. */
    private void commandAction() {
        thread();
        boolean ownCommand=ACTION_CALLER.walk(frames->{
            var stack=frames.toList();
            boolean ownLambda=stack.stream().filter(frame->frame.getDeclaringClass()!=ProRuntime.class)
                    .findFirst().map(frame->frame.getDeclaringClass()==ProMod.class
                            &&frame.getMethodName().startsWith("lambda$commands$")).orElse(false);
            if(!ownLambda)return false;
            // A mod can call Brigadier with a forged level-4 CommandSourceStack. Require
            // the actual player packet or dedicated console dispatch on this stack.
            boolean player=stack.stream().anyMatch(frame->{
                Class<?> owner=frame.getDeclaringClass();String method=frame.getMethodName();
                return owner==net.minecraft.server.network.ServerGamePacketListenerImpl.class
                        &&(method.equals("performChatCommand")||method.equals("m_246958_"));
            });
            if(player)return true;
            boolean console=stack.stream().anyMatch(frame->frame.getDeclaringClass()==net.minecraft.server.dedicated.DedicatedServer.class
                    &&(frame.getMethodName().equals("handleConsoleInputs")||frame.getMethodName().equals("m_139665_")));
            if(!console)return false;
            try {
                Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
                return Boolean.TRUE.equals(bridge.getMethod("consoleDispatchTrusted").invoke(null));
            } catch(ReflectiveOperationException unavailable) {return false;}
        });
        if(!ownCommand)throw new SecurityException("RONOVA_COMMAND_AUTHORITY_REQUIRED");
    }
    private void writable() {
        thread();
        if(closing || !journal.healthy() || journal.realm==null)
            throw new IllegalStateException("DURABLE_WRITES_UNAVAILABLE:"+journal.readOnlyReason());
    }
    private void ensureWorkCapacity() {
        if(work.size()<CAPACITY)return;
        var iterator=work.values().iterator();
        while(iterator.hasNext()) {
            Work old=iterator.next();
            boolean durable=old.ack!=null&&old.ack.isDone()&&!old.ack.isCompletedExceptionally()
                    &&(old.factAck==null||old.factAck.isDone()&&!old.factAck.isCompletedExceptionally());
            if(!durable||old.dispatching||old.state.equals("EFFECT_UNKNOWN"))continue;
            if(old.action.equals("PROTECT")&&!current(old)&&old.state.equals("FENCED")&&old.referencesReleased) {
                if(old.binding.latest==old&&!old.binding.protection&&!old.binding.subject.terminal) {
                    old.binding.releaseOwnedReference();old.binding.latest=null;
                }
                old.clients.clear();iterator.remove();
            } else if(old.action.equals("BLOCK_CLEAR")&&((old.cancelled&&!old.executed)
                    ||old.complete()&&old.receiptAck!=null&&old.receiptAck.isDone()&&!old.receiptAck.isCompletedExceptionally()
                        &&old.block.remaining().isEmpty())) {
                // O01 observations have a live lease; eviction ends that lease. Its durable
                // historic receipt remains queryable without claiming a current world result.
                old.clients.clear();iterator.remove();
            } else if(old.action.equals("CLEAR")&&old.complete()&&old.referencesReleased&&old.binding.entity()==null
                    &&old.resourceAck!=null&&old.resourceAck.isDone()&&!old.resourceAck.isCompletedExceptionally()
                    &&recoverySettled(old.id)) {
                recoveryChain.retireBody(old.id);old.clients.clear();iterator.remove();
            }
            if(work.size()<CAPACITY)return;
        }
        throw new IllegalStateException("WORK_CAPACITY_NO_ACCEPTANCE_UNSETTLED_DUTIES_RETAINED");
    }
    private Binding bind(Entity e) {
        return bind(e,false);
    }
    private Binding bind(Entity e,boolean allowPlayer) {
        thread();
        if(!(((Access.EntityState)e).pro$level() instanceof ServerLevel l) || l.getServer()!=server || !allowPlayer&&e instanceof ServerPlayer)
            throw new IllegalArgumentException("UNSUPPORTED_TARGET");
        Binding b=bindings.get(e);
        if(b!=null) return b;
        if(bindings.size()>=CAPACITY) { coverageGap=true; throw new IllegalStateException("BINDING_CAPACITY"); }
        b=new Binding(e,new Subject(),new EntityRecords(l,e)); bindings.put(e,b);
        subjects.put(b.subject.id,b.subject);
        if(!BackingPolicy.capture(b.records,e))b.protectionGap="BACKING_CAPTURE_INCOMPLETE";
        publishProtection(this,b,e);
        recoveryTasks.observed(e,b.subject.id);
        recoveryRecords.creationBound(e,b.subject.id);return b;
    }
    public UUID clear(Entity e) {
        commandAction();
        return clearBody(e);
    }
    private UUID clearBody(Entity e) {
        hudScanTick=-1;
        Binding known=bindings.get(e);
        if(known==null||known.latest==null||!known.latest.action.equals("CLEAR")||known.latest.generation!=known.subject.generation
                ||known.latest.state.equals("FENCED"))ensureWorkCapacity();
        Binding b=bind(e);
        Work existing=null;
        try(var fieldGate=FieldPolicyGate.enter(e);var authorityGate=b.subject.commitGate.enter()) {
            if(b.latest!=null && b.latest.action.equals("CLEAR") && b.latest.generation==b.subject.generation
                    && !b.latest.state.equals("FENCED")) {
                existing=b.latest;
            } else {
            resolveUnknownIntent(b);
            b.records=new EntityRecords((ServerLevel)((Access.EntityState)e).pro$level(),e);
            if(!BackingPolicy.capture(b.records,e))b.subject.unresolvedSources.add("TERMINAL_BACKING_CAPTURE_INCOMPLETE");
            if(!b.subject.terminal) { b.subject.generation++; b.subject.terminal=true; b.protection=false; }
            publishProtection(this,b,e);
            }
        }
        if(existing!=null) { requestClear(existing);return existing.id; }
        publishPolicy();
        UUID id=queue(b,null,"CLEAR");
        Work accepted=work.get(id);
        if(accepted!=null)requestClear(accepted);
        return id;
    }
    /** Fence publication first, clear current bodies, then suppress their defining methods. */
    public String stopModGroup(String selection) {
        commandAction();
        var groups=resolveGroups(selection);
        if(groups.size()==1&&groups.get(0).error()==null)return stopResolved(groups.get(0).targets());
        List<String> results=new ArrayList<>();int failures=0;
        for(var group:groups) {
            if(group.error()!=null) {results.add(group.error());failures++;continue;}
            try {
                String result=stopResolved(group.targets());
                results.add(group.targets().keySet()+":"+result);
                if(!result.contains("BODY_FAILURES=0")||!result.contains(";FAILED=0"))failures++;
            } catch(RuntimeException|LinkageError unavailable) {
                coverageGap=true;failures++;
                results.add(group.targets().keySet()+":"+unavailable.getClass().getSimpleName());
            }
        }
        return "GROUP_FAILURES="+failures+";GROUPS="+results;
    }
    private String stopResolved(LinkedHashMap<String,Module> targets) {
        Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());modules.addAll(targets.values());
        stoppedModules.addAll(modules);
        String prepared;
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            prepared=String.valueOf(agent.getMethod("prepareModGroup",String[].class,Module[].class).invoke(null,
                    (Object)targets.keySet().toArray(String[]::new),(Object)targets.values().toArray(Module[]::new)));
        } catch(ReflectiveOperationException unavailable) {
            coverageGap=true;return "BODIES_CLEARED=0;BLOCKS_QUEUED=0;BODY_FAILURES=1;PREPARE_UNAVAILABLE";
        }
        if(!prepared.equals("READY")) {
            coverageGap=true;return "BODIES_CLEARED=0;BLOCKS_QUEUED=0;BODY_FAILURES=1;PREPARE="+prepared;
        }
        ArrayList<Entity> bodies=new ArrayList<>();
        for(ServerLevel level:server.getAllLevels())for(Entity entity:level.getAllEntities())
            if(modules.contains(entity.getClass().getModule())&&!(entity instanceof ServerPlayer))bodies.add(entity);
        record BlockTarget(ServerLevel level,net.minecraft.world.level.block.entity.BlockEntity entity) { }
        ArrayList<BlockTarget> blocks=new ArrayList<>();
        for(ServerLevel level:server.getAllLevels())for(var block:BlockGroupPolicy.loaded(level))
            if(modules.contains(block.getClass().getModule()))blocks.add(new BlockTarget(level,block));
        int cleared=0,failed=0;
        for(Entity entity:bodies) {
            try {clear(entity);cleared++;}
            catch(RuntimeException|LinkageError unavailable) {failed++;coverageGap=true;}
        }
        int blockQueued=0;
        for(var block:blocks)try {
            BlockGroupPolicy.release(block.entity());
            clearBlock(block.level(),block.entity().getBlockPos());blockQueued++;
        } catch(RuntimeException|LinkageError unavailable) {
            if(protectedModules.contains(block.entity().getClass().getModule()))BlockGroupPolicy.protect(this,block.entity());
            failed++;coverageGap=true;
        }
        String result;
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            result=String.valueOf(agent.getMethod("stopModGroup",String[].class,Module[].class).invoke(null,
                    (Object)targets.keySet().toArray(String[]::new),(Object)targets.values().toArray(Module[]::new)));
        } catch(ReflectiveOperationException unavailable) {
            throw new IllegalStateException("MOD_GROUP_AGENT_UNAVAILABLE",unavailable);
        }
        if(result.startsWith("TARGETS=")) {
            stoppedMods.addAll(targets.keySet());
            stoppedModules.addAll(targets.values());
            protectedMods.removeAll(targets.keySet());protectedModules.removeAll(targets.values());
            for(ServerPlayer player:server.getPlayerList().getPlayers())sendModGroup(player);
        }
        return "BODIES_CLEARED="+cleared+";BLOCKS_QUEUED="+blockQueued+";BODY_FAILURES="+failed+";"+result;
    }
    public String protectModGroup(String selection) {
        commandAction();
        var groups=resolveGroups(selection);
        if(groups.size()==1&&groups.get(0).error()==null)return protectResolved(groups.get(0).targets());
        List<String> results=new ArrayList<>();int failures=0;
        for(var group:groups) {
            if(group.error()!=null) {results.add(group.error());failures++;continue;}
            try {
                String result=protectResolved(group.targets());
                results.add(group.targets().keySet()+":"+result);
                if(!result.contains("FAILED=0"))failures++;
            } catch(RuntimeException|LinkageError unavailable) {
                coverageGap=true;failures++;
                results.add(group.targets().keySet()+":"+unavailable.getClass().getSimpleName());
            }
        }
        return "GROUP_FAILURES="+failures+";GROUPS="+results;
    }
    private String protectResolved(LinkedHashMap<String,Module> targets) {
        Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());modules.addAll(targets.values());
        for(Module module:targets.values())if(stoppedModules.contains(module))
            throw new IllegalStateException("MOD_GROUP_ALREADY_STOPPED");
        protectedMods.addAll(targets.keySet());protectedModules.addAll(targets.values());
        int protectedCount=0,failed=0;
        for(ServerLevel level:server.getAllLevels())for(Entity entity:level.getAllEntities())
            if(modules.contains(entity.getClass().getModule())) {
                try {protect(entity,true);protectedCount++;}
                catch(RuntimeException|LinkageError unavailable) {failed++;coverageGap=true;}
            }
        int blocks=0;
        for(ServerLevel level:server.getAllLevels())for(var block:BlockGroupPolicy.loaded(level))
            if(modules.contains(block.getClass().getModule())) {
                if(BlockGroupPolicy.protect(this,block))blocks++;
                else {failed++;coverageGap=true;}
            }
        return "MODS="+protectedMods+";PROTECTED="+protectedCount+";BLOCKS="+blocks+";FAILED="+failed;
    }
    private static LinkedHashMap<String,Module> selectedModules(String selection) {
        LinkedHashMap<String,Module> targets=new LinkedHashMap<>();
        for(String raw:selection.split(",")) {
            String id=raw.trim().toLowerCase(Locale.ROOT);
            if(!id.matches("[a-z0-9_.-]{2,64}")||id.equals("ronova_pro")||id.equals("minecraft")||id.equals("forge"))
                throw new IllegalArgumentException("INVALID_MOD_ID:"+id);
            var container=net.minecraftforge.fml.ModList.get().getModContainerById(id)
                    .orElseThrow(()->new IllegalArgumentException("MOD_NOT_LOADED:"+id));
            Object instance=container.getMod();
            if(instance==null)throw new IllegalArgumentException("MOD_INSTANCE_UNAVAILABLE:"+id);
            targets.put(id,instance.getClass().getModule());
        }
        if(targets.isEmpty())throw new IllegalArgumentException("EMPTY_MOD_GROUP");
        return targets;
    }
    private record GroupSelection(LinkedHashMap<String,Module> targets,String error) { }
    private static List<GroupSelection> resolveGroups(String selection) {
        Map<Module,LinkedHashMap<String,Module>> grouped=new LinkedHashMap<>();
        List<GroupSelection> results=new ArrayList<>();
        for(String raw:selection.split(",",-1)) {
            try {
                var entry=selectedModules(raw).entrySet().iterator().next();
                grouped.computeIfAbsent(entry.getValue(),ignored->new LinkedHashMap<>()).put(entry.getKey(),entry.getValue());
            } catch(IllegalArgumentException invalid) {
                results.add(new GroupSelection(new LinkedHashMap<>(),invalid.getMessage()));
            }
        }
        for(var entry:grouped.entrySet()) {
            String mismatch=null;
            try {
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                Set<String> declared=Set.of((String[])agent.getMethod("modGroupIds",Module.class).invoke(null,entry.getKey()));
                if(declared.isEmpty()||!entry.getValue().keySet().containsAll(declared))
                    mismatch="MULTI_MOD_MODULE_REQUIRES_ALL_IDS:"+declared;
            } catch(ReflectiveOperationException unavailable) {mismatch="MOD_GROUP_AGENT_UNAVAILABLE";}
            results.add(new GroupSelection(entry.getValue(),mismatch));
        }
        if(results.isEmpty())results.add(new GroupSelection(new LinkedHashMap<>(),"EMPTY_MOD_GROUP"));
        return results;
    }
    public void sendModGroup(ServerPlayer player) {
        if(!stoppedMods.isEmpty())ProNetwork.modGroup(player,session,String.join(",",stoppedMods));
    }
    public String modGroupState() {
        try {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            return String.valueOf(agent.getMethod("modGroupState").invoke(null));
        } catch(ReflectiveOperationException unavailable) {return "AGENT_UNAVAILABLE";}
    }
    public String modGroupState(String selection) {
        commandAction();
        List<String> states=new ArrayList<>();
        for(var group:resolveGroups(selection)) {
            if(group.error()!=null) {states.add(group.error());continue;}
            for(var entry:group.targets().entrySet())states.add(entry.getKey()+"="+
                    (stoppedModules.contains(entry.getValue())?
                            stoppedMods.contains(entry.getKey())?"STOPPED":"FENCED_PARTIAL":
                    protectedModules.contains(entry.getValue())?"PROTECTED":"ACTIVE"));
        }
        return "GROUPS="+states+";"+modGroupState();
    }
    private void requestClear(Work accepted) {
        try {
            recoveryTasks.qualify(accepted.binding.subject,accepted.id);
            recoveryRecords.qualify(accepted.binding.subject,accepted.id);
        } catch(RuntimeException|LinkageError unavailable) {
            coverageGap=true;accepted.obligations.put("SOURCE_PREPARATION","UNRESOLVED:"+unavailable.getClass().getSimpleName());
        }
        executeMemoryAction(accepted);
        if(!current(accepted)||!accepted.binding.subject.terminal)return;
        try {
            // Bounded discovery and worker dispatch; never wait here for a holder bin lock or durable ACK.
            recoverySources.maintainResources();
            recoveryRecords.tick(tick);
        } catch(RuntimeException|LinkageError unavailable) {
            coverageGap=true;accepted.obligations.put("SOURCE_PREPARATION","UNRESOLVED:"+unavailable.getClass().getSimpleName());
        }
    }
    public UUID clearBlock(ServerLevel level,BlockPos pos) {
        commandAction();
        if(level.getServer()!=server)throw new IllegalArgumentException("OTHER_REALM");
        for(Work old:work.values()) if(old.block!=null && old.block.level==level && old.block.pos.equals(pos)) {
            if(old.state.equals("EFFECT_UNKNOWN")) { old.state="ACK_FAILED"; old.reason="PRIOR_EFFECT_UNKNOWN_QUERY_ONLY"; }
            if(!old.executed && !old.state.equals("FENCED") && !old.state.equals("ACK_FAILED")) return old.id;
        }
        return queue(null,new BlockRecords(level,pos),"BLOCK_CLEAR");
    }
    public UUID protect(Entity e) {
        commandAction();
        return protect(e,true);
    }
    private UUID protect(Entity e,boolean publishClientPolicy) {
        thread();
        hudScanTick=-1;
        Binding b=bind(e,true);
        if(b.latest!=null && b.latest.action.equals("PROTECT") && b.latest.generation==b.subject.generation
                && !b.latest.state.equals("FENCED"))return b.latest.id;
        ensureWorkCapacity();
        b.records=new EntityRecords((ServerLevel)((Access.EntityState)e).pro$level(),e);
        if(!BackingPolicy.capture(b.records,e))b.protectionGap="BACKING_CAPTURE_INCOMPLETE";
        if(e instanceof LivingEntity l) {
            float floor=healthItem(l).getValue();
            if(!Float.isFinite(floor) || floor<=0)throw new IllegalArgumentException("PROTECTION_REQUIRES_LIVE_HEALTH_STATE");
            try(var fieldGate=FieldPolicyGate.enter(e);var healthGate=FieldPolicyGate.enter(healthItem(l));
                    var authorityGate=b.subject.commitGate.enter()) {commitProtection(b,floor);}
        } else try(var fieldGate=FieldPolicyGate.enter(e);var authorityGate=b.subject.commitGate.enter()) {
            commitProtection(b,0);
        }
        if(publishClientPolicy)publishPolicy();
        return queue(b,null,"PROTECT");
    }
    private void commitProtection(Binding binding,float floor) {
        binding.subject.generation++;binding.subject.terminal=false;binding.protection=false;
        binding.healthFloor=floor;
        activate(binding);
    }
    /**
     * Publishes the current protection policy for an exact object. Server-thread callers and worker-thread
     * guard readers see the same snapshot; the durable record is a separate, later obligation.
     */
    private void activate(Binding b) {
        BackingPolicy.resources(this,journal,recoveryTasks,recoveryStorage,recoveryRecords);
        Entity entity=b.entity();
        if(entity==null)return;
        b.protection=true;
        b.protectionGeneration=b.subject.generation;
        publishProtection(this,b,entity);
        // Publish the policy before registering each exact slot. A writer already inside that
        // container's short gate finishes first; later writers see both the slot and policy.
        b.protectionGap=publishIndexOwners(b,entity);
        publishOwners(entity);
    }
    private String publishIndexOwners(Binding binding,Entity entity) {
        EntityRecords records=binding.records;
        if(!records.bound())return "INDEX_CONTAINER_BINDING_UNAVAILABLE";
        String backingGap=BackingPolicy.capture(records,entity)?"":"BACKING_CAPTURE_CAPACITY";
        String slotGap="";
        try {
            var field=HashSet.class.getDeclaredField("map");field.setAccessible(true);Object backing=field.get(records.manager.pro$known());
            if(backing instanceof Map<?,?> map)for(UUID uuid:records.indexUuids())if(map.containsKey(uuid)
                    &&!addIndexSlot(map,uuid,map.get(uuid),entity,binding.subject.generation))slotGap="KNOWN_UUID_SLOT_CHANGED";
        } catch(ReflectiveOperationException unavailable) {coverageGap=true;slotGap="KNOWN_UUID_CONTAINER_UNAVAILABLE";}
        for(var entry:records.sections.pro$all().long2ObjectEntrySet())if(entry.getValue() instanceof Access.Section section
                &&((Access.Group)section.pro$storage()).pro$all().stream().anyMatch(value->value==entity))
            addIndexSlot(records.sections.pro$all(),entry.getLongKey(),entry.getValue(),entity,binding.subject.generation);

        boolean liveIdSlot=addIndexSlot(records.lookup.pro$ids(),records.id,entity,entity,binding.subject.generation);
        for(UUID alias:records.indexUuids())if(records.lookup.pro$uuids().get(alias)==entity)
            addIndexSlot(records.lookup.pro$uuids(),alias,entity,entity,binding.subject.generation);
        addIndexSlot(records.ticks.pro$active(),records.id,entity,entity,binding.subject.generation);
        addIndexSlot(records.ticks.pro$passive(),records.id,entity,entity,binding.subject.generation);
        Object tracked=records.tracking.pro$tracked().get(records.id);
        if(tracked instanceof Access.Tracked row&&row.pro$entity()==entity)
            addIndexSlot(records.tracking.pro$tracked(),records.id,tracked,entity,binding.subject.generation);
        return !backingGap.isEmpty()?backingGap:!slotGap.isEmpty()?slotGap:!liveIdSlot?"LOOKUP_ID_SLOT_NOT_CURRENT"
                :indexGuardState.startsWith("INSTALLED")?"":"BACKING_INDEX_GUARD_UNAVAILABLE:"+indexGuardState;
    }
    private static boolean addIndexSlot(Object map,Object key,Object value,Entity entity,long generation) {
        if(map==null)return false;
        Class<?> type=map.getClass();
        if(type!=it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap.class&&type!=HashMap.class&&type!=it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.class
                &&type!=it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap.class)return false;
        if(type==HashMap.class) {
            boolean[] registered={false};
            try {
                return SourceMaps.with(map,()->registered[0]=recordIndexSlot(map,key,value,entity,generation))&&registered[0];
            } catch(ReflectiveOperationException unavailable) {
                return false;
            }
        }
        return recordIndexSlot(map,key,value,entity,generation);
    }
    private static boolean recordIndexSlot(Object map,Object key,Object value,Entity entity,long generation) {
        try(var gate=FieldPolicyGate.enter(map)) {
            // Re-read in the same gate used by the final value writer. A stale scan cannot
            // register an old value as though it were still the protected live slot.
            Map<?,?> slots=(Map<?,?>)map;
            if(slots.get(key)!=value) {
                // Optional active/passive and alias slots may be absent after a legitimate
                // handoff. The caller decides whether this particular slot was required.
                return false;
            }
            if(value==null)return false;
            synchronized(INDEX_CONTAINERS) {
                indexContainerMask|=1L<<(System.identityHashCode(map)&63);
                List<IndexSlot> prior=INDEX_CONTAINERS.get(map);var next=new ArrayList<IndexSlot>();
                if(prior!=null)for(IndexSlot old:prior) {
                    Entity owner=old.entity().get();
                    if(owner!=null&&old.value().get()!=null&&!(owner==entity&&old.key().equals(key)))next.add(old);
                }
                next.add(new IndexSlot(new java.lang.ref.WeakReference<>(entity),new java.lang.ref.WeakReference<>(value),key,generation));
                INDEX_CONTAINERS.put(map,List.copyOf(next));
            }
            return true;
        }
    }
    /** Observed at the actual vanilla copy publication, before its caller can use the replacement tick maps. */
    public static void tickContainersPublished(Object owner) {
        if(!(owner instanceof Access.Ticks ticks))return;
        List<ProtectionSnapshot> published;
        synchronized(PROTECTION) { published=List.copyOf(PROTECTION.values()); }
        for(ProtectionSnapshot snapshot:published) {
            ProRuntime runtime=snapshot.runtime();Binding binding=snapshot.binding();Entity entity=binding.entity();
            if(runtime.closing||!runtime.server.isSameThread()||entity==null||snapshot.records().ticks!=owner
                    ||!binding.protection||binding.protectionGeneration!=binding.subject.generation)continue;
            if(!BackingPolicy.capture(binding.records,entity))binding.protectionGap="TICK_BACKING_CAPTURE_INCOMPLETE";
            addIndexSlot(ticks.pro$active(),snapshot.records().id,entity,entity,binding.subject.generation);
            addIndexSlot(ticks.pro$passive(),snapshot.records().id,entity,entity,binding.subject.generation);
        }
    }
    /** Null key/value asks only whether this proven container currently has protection to preserve on clear. */
    public static boolean preventIndexSlotRemoval(Object container,Object key,Object value) {
        if(BlockGroupPolicy.containerMutationDenied(container,key,value))return true;
        // A detached HashMap Node can retain an old backing-owner association. Its actual
        // membership must win over that stale association, or a legal replacement keeps
        // blocking writes to an unrelated retired node.
        if(container!=null&&container.getClass().getClassLoader()==null
                &&(container.getClass().getName().equals("java.util.HashMap$Node")
                    ||container.getClass().getName().equals("java.util.HashMap$TreeNode"))) {
            if(BlockGroupPolicy.nodeMutationDenied(container,key,value))return true;
            return preventIndexEntryWrite(container,key,value);
        }
        if(key==null&&value==null&&BackingPolicy.protectedSectionKeys(container))return true;
        if(key instanceof Long&&BackingPolicy.elementDenied(container,key,null))return true;
        if(BackingPolicy.elementDenied(container,value,null))return true;
        if((indexContainerMask&(1L<<(System.identityHashCode(container)&63)))==0)return false;
        List<IndexSlot> slots=INDEX_CONTAINERS.get(container);
        if(slots==null)return false;
        for(IndexSlot slot:slots) {
            Entity entity=slot.entity().get();
            if(entity==null)continue;
            if(key!=null||value!=null) {
                if(value!=slot.value().get()||!slot.key().equals(key))continue;
            }
            Binding binding=protectedBinding(entity);
            if(binding!=null&&binding.subject.generation==slot.generation()&&preventIndexRemoval(entity))return true;
        }
        return false;
    }
    private static boolean preventIndexEntryWrite(Object node,Object key,Object value) {
        List<Map.Entry<Object,List<IndexSlot>>> owners;
        synchronized(INDEX_CONTAINERS) {owners=new ArrayList<>(INDEX_CONTAINERS.entrySet());}
        for(var owner:owners) {
            Object map=owner.getKey();if(map==null||map.getClass()!=HashMap.class)continue;boolean registered=false;
            for(IndexSlot slot:owner.getValue())if(slot.value().get()==value&&slot.key().equals(key)) {
                Entity entity=slot.entity().get();Binding binding=entity==null?null:protectedBinding(entity);
                if(binding!=null&&binding.subject.generation==slot.generation()&&preventIndexRemoval(entity)) {registered=true;break;}
            }
            if(registered)for(Object actual:((HashMap<?,?>)map).entrySet())if(actual==node)return true;
        }
        return false;
    }
    private static void publishProtection(ProRuntime runtime,Binding binding,Entity entity) {
        PROTECTION.put(entity,new ProtectionSnapshot(runtime,binding,binding.address.entityUuid(),
                binding.protectionGeneration,binding.records));
    }
    /** Exact object lookup works even after its UUID or raw level has been corrupted. */
    static ProRuntime policyRuntime(Entity e) {
        ProtectionSnapshot snapshot=PROTECTION.get(e);
        return snapshot==null?runtime(e):snapshot.runtime();
    }
    private static boolean snapshotProtected(Entity e) { return protectedBinding(e)!=null; }
    private static void clearProtection(Entity e) { PROTECTION.remove(e); }
    /**
     * Captures the real-owner associations used by the write guards. Runs where the state is already walked:
     * the synchronized-data items of a body we just protected, and its maximum-health attribute instance.
     */
    private void publishOwners(Entity entity) {
        try {
            var data=((Access.EntityState)entity).pro$data();
            if(entity instanceof LivingEntity living)SYNC_OWNERS.put(healthItem(living),new java.lang.ref.WeakReference<>(entity));
        } catch(RuntimeException unavailable) { coverageGap=true; }
        if(entity instanceof LivingEntity living)try {
            var instance=((Access.Living)living).pro$attributes()
                    .getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if(instance!=null)SYNC_OWNERS.put(instance,new java.lang.ref.WeakReference<>(entity));
        } catch(RuntimeException unavailable) { coverageGap=true; }
    }
    /** The living body that really owns this attribute instance, if it has been observed. */
    public static LivingEntity attributeOwner(Object instance) {
        var identity=SYNC_OWNERS.get(instance);Entity owner=identity==null?null:identity.get();
        return owner instanceof LivingEntity living?living:null;
    }
    /**
     * An earlier unresolved callback is recorded as a gap, not used as a global gate. Its unknown effects stay
     * unresolved for their own outcome, while the exact object in front of us can still be acted on.
     */
    private void resolveUnknownIntent(Binding b) {
        for(Work old:work.values())
            if(old.binding!=null && old.binding.subject==b.subject && old.state.equals("EFFECT_UNKNOWN")) {
                old.reason="PRIOR_EFFECT_UNKNOWN_QUERY_ONLY";
                coverageGap=true;
            }
    }
    public void revoke(Entity e) {
        commandAction();
        revokeBound(e);
    }
    private void revokeBound(Entity e) {
        thread(); Binding b=bindings.get(e);
        if(b==null) return;
        try(var fieldGate=FieldPolicyGate.enter(e);var authorityGate=b.subject.commitGate.enter()) {
            b.subject.generation++; b.subject.terminal=false; b.protection=false;
        }
        for(Binding other:bindings.values()) if(other.subject==b.subject) other.protection=false;
        for(Entity bound:List.copyOf(bindings.keySet()))
            if(bindings.get(bound)!=null && bindings.get(bound).subject==b.subject) clearProtection(bound);
        clearProtection(e);
        journal.append("REVOKE\t"+b.subject.id+"\t"+b.subject.generation+"\t"+session);
        // Same short gate as Native final commit; preparation and readback never hold it.
        for(Work w:work.values()) if(w.binding!=null && w.binding.subject==b.subject) fence(w,"QUALIFICATION_CHANGED");
        retireProtectionSnapshots(b.subject);
        publishPolicy();
    }
    private UUID queue(Binding b,BlockRecords block,String action) {
        if(action.equals("CLEAR")||action.equals("PROTECT")) {
            thread();if(closing)throw new IllegalStateException("RUNTIME_CLOSING");
        } else writable();
        if(b!=null&&b.entity()!=null&&action.equals("CLEAR"))retainTerminalHud(b.entity(),b.subject);
        if(b!=null)resolveUnknownIntent(b);
        for(Work existing:work.values())
            if(existing.binding==b && existing.block==block && existing.action.equals(action)
                && (b==null || existing.generation==b.subject.generation)) return existing.id;
        ensureWorkCapacity();
        Work w=new Work(b,block,b==null?0:b.subject.generation,action);
        w.obligations.put("DURABLE_INTENT","DURABLE_ACK_PENDING");
        if(b!=null && action.equals("CLEAR")) {
            for(UUID id:b.records.participants()) w.clients.put(id,new ClientFact());
            for(String obligation:List.of("BODY","LOOKUP","SECTION","TICK","TRACKING","RELATIONS")) w.obligations.put(obligation,"REQUIRED_PENDING");
            w.obligations.put("CLIENT_PRESENCE",w.clients.isEmpty()?"NOT_APPLICABLE_NO_CURRENT_REQUIRED_PARTICIPANT":"REQUIRED_PENDING");
        } else if(block!=null) {
            w.obligations.put("BLOCK_BODY","REQUIRED_PENDING");
            w.obligations.put("BLOCK_RECORD","REQUIRED_PENDING");
            w.obligations.put("BLOCK_TICK","REQUIRED_PENDING");
            w.obligations.put("ENTITY_LOOKUP","NOT_APPLICABLE_BLOCK_ENTITY_BINDING");
            w.obligations.put("CLIENT_PRESENCE","NOT_APPLICABLE_SERVER_BLOCK_BINDING_ONLY");
        }
        String location=b==null ? block.level.dimension().location()+":"+block.pos.asLong()
                : w.records.level.dimension().location()+":"+w.records.uuid+":"+b.incarnation;
        w.intentRecord="INTENT\t"+w.id+"\t"+action+"\t"+(b==null?"WORLD":b.subject.id)
                +"\t"+w.generation+"\t"+session+"\t"+location+"\tparent="+(b==null?"none":b.predecessor)
                +"\trealm="+journal.realm+"\tcontract=O01-v1\trequired="+new TreeSet<>(w.clients.keySet());
        w.ack=journal.append(w.intentRecord);
        if(b!=null&&action.equals("CLEAR"))recoveryChain.accept(w.id,b.subject,w.records.level.dimension().location().toString(),
                w.records.uuid,b.incarnation,w.intentRecord);
        work.put(w.id,w);
        if(b!=null) {b.latest=w;retireProtectionSnapshots(b.subject);}
        return w.id;
    }
    public Status query(UUID id) {
        thread(); Work w=work.get(id); if(w==null)return null;
        refreshIntentStatus(w);
        if(w.complete() && (Integer.toUnsignedLong(server.getTickCount())-w.lastSample>10 || !current(w)
                || w.records!=null && (!w.records.bound() || !w.records.remaining().isEmpty()
                    || w.observedSourceRevision!=w.binding.subject.sourceRevision.get())))
            invalidate(w,"CURRENT_OBSERVATION_EXPIRED");
        if(w.complete())for(var entry:w.clients.entrySet())if(entry.getValue().required) {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null || player.connection!=entry.getValue().connection || player.serverLevel()!=w.records.level) {
                invalidate(w,"REQUIRED_PARTICIPANT_CHANGED");break;
            }
        }
        return new Status(w.id,w.action,w.state,w.reason,w.complete(),w.obligations,w.lifecycleEffects,w.recoveryScope,
                w.receiptAck!=null && w.receiptAck.isDone() && !w.receiptAck.isCompletedExceptionally());
    }
    public void cancel(UUID id) {
        commandAction(); Work w=work.get(id); if(w==null)return;
        if(w.binding!=null && w.binding.latest==w && w.generation==w.binding.subject.generation) {
            if(w.binding.entity()!=null)revokeBound(w.binding.entity());
            else try(var gate=w.binding.subject.commitGate.enter()) {
                w.binding.subject.generation++;w.binding.subject.terminal=false;
                journal.append("REVOKE\t"+w.binding.subject.id+"\t"+w.binding.subject.generation+"\t"+session);
            }
        }
        w.cancelled=true; fence(w,"OPERATION_CANCELLED");
        journal.append("CANCEL\t"+w.id+"\t"+session);
    }
    /** Callers may add requirements, but cannot downgrade clients found by production tracking. */
    public void participant(UUID operation,UUID player,boolean required) {
        commandAction();
        writable(); Work w=work.get(operation);
        if(w==null || w.binding==null || !w.action.equals("CLEAR") || !current(w))
            throw new IllegalArgumentException("NO_CURRENT_ENTITY_OPERATION");
        ClientFact f=w.clients.get(player);
        if(f!=null && (!required || f.required))return;
        if(f==null) { f=new ClientFact(); f.required=required;w.clients.put(player,f); }
        else f.required=true;
        if(f.required)w.obligations.put("CLIENT_PRESENCE","REQUIRED_PENDING");
        invalidate(w,"PARTICIPANT_CONTRACT_CHANGED");
        recordParticipants(w);
        if(w.executed)sendProbe(w,player);
    }
    public List<UUID> operations() { thread(); return List.copyOf(work.keySet()); }
    public List<String> durableHistory() { return journal.history(); }
    public boolean recoverySettled(UUID operation) {
        thread();return recoveryChain.status(operation).contains("已知恢复链已处置");
    }
    public List<String> recoveryStatus(UUID operation) {
        thread();var rows=new ArrayList<String>();
        if(!recoverySources.gap().isEmpty())rows.add("SOURCE_GAP:"+recoverySources.gap());
        if(!recoveryStorage.gap().isEmpty())rows.add("STORAGE_GAP:"+recoveryStorage.gap());
        recoveryStorage.status(operation).forEach(status->rows.add(status.toString()));
        rows.addAll(recoveryTasks.status(operation));
        rows.addAll(recoveryRecords.status(operation));
        rows.addAll(recoveryChain.status(operation));
        return List.copyOf(rows);
    }
    public boolean observedSaveCopyLoadCovered() { return !coverageGap; }
    public String protectionStatus(Entity e) {
        thread(); Binding b=bindings.get(e);
        if(b==null||!b.protection||b.protectionGeneration!=b.subject.generation)return "INACTIVE";
        var raw=(Access.EntityState)e;
        if(raw.pro$id()!=b.address.entityId()||!Objects.equals(raw.pro$uuid(),b.address.entityUuid()))return "ACTIVE_WITH_GAP:IDENTITY_CHANGED";
        return b.protectionGap.isEmpty()?"ACTIVE":"ACTIVE_WITH_GAP:"+b.protectionGap;
    }
    public String operationSummary(UUID operation) {
        Status status=query(operation);if(status==null)return "未找到当前会话工作；历史记录可用 history 查询";
        Work w=work.get(operation);
        if(w.action.equals("PROTECT")) {
            // A real respawn/dimension handoff retires the old operation, not the player's continued policy.
            for(int hops=0;w.protectionSuccessor!=null&&hops<work.size();hops++) {
                Work successor=work.get(w.protectionSuccessor);if(successor==null||!successor.action.equals("PROTECT"))break;
                w=successor;
            }
            Entity entity=w.binding==null?null:w.binding.entity();
            String protection=entity==null?"INACTIVE":protectionStatus(entity);
            if(!current(w)||protection.equals("INACTIVE"))return "防护策略当前未启用；"+w.state+(w.reason.isEmpty()?"":"："+w.reason);
            return protection.startsWith("ACTIVE_WITH_GAP:")?"防护策略已启用，但存在覆盖缺口："+protection.substring("ACTIVE_WITH_GAP:".length()):
                    "防护策略已启用；当前已登记入口可用（不代表所有攻击均已拦住）";
        }
        String body=status.complete()?(w.action.equals("BLOCK_CLEAR")?"方块实体已清除":"身体已清除"):"处置尚未确认";
        String recovery=w.action.equals("CLEAR")?(recoverySettled(operation)?"；已知恢复链已处置":"；恢复源仍待处理"):"";
        return body+recovery+(status.complete()?"":"；"+status.state()+(status.reason().isEmpty()?"":"："+status.reason()));
    }
    public String presentationSupport() { return "UNSUPPORTED_PRODUCTION_PURE_PRESENTATION_OWNER_ADAPTER_REQUIRED"; }
    /** Reconcile the scoped client mirrors from immutable entity addresses. */
    private void publishPolicy() {
        if(closing)return;
        Map<PolicyAddress,Boolean> desired=new HashMap<>();
        for(Binding binding:List.copyOf(bindings.values())) {
            Entity entity=binding.entity();
            if(binding.protection&&!binding.subject.terminal&&binding.protectionGeneration==binding.subject.generation
                    &&entity!=null&&entity!=removing&&!(entity instanceof ServerPlayer player
                    &&(pendingRespawnProtections.containsKey(player)||unverifiedRespawnPolicies.contains(player))))
                desired.put(binding.address,true);
        }
        for(ServerPlayer participant:server.getPlayerList().getPlayers())publishPolicy(participant,desired,false);
    }
    private ClientPolicy clientPolicy(ServerPlayer player) {
        if(player==null||player.connection==null)return null;
        return clientPolicies.computeIfAbsent(player.connection,key->new ClientPolicy());
    }
    private boolean resetPolicyScope(ServerPlayer player,ClientPolicy state,String dimension) {
        state.active=false;
        long scope=++state.lastScope;
        if(scope<=0||!ProNetwork.reset(player,session,scope,dimension))return false;
        state.scope=scope;state.dimension=dimension;state.revision=0;state.sent.clear();state.terminals.clear();state.hudSent.clear();state.active=true;
        return true;
    }
    private void publishPolicy(ServerPlayer player,Map<PolicyAddress,Boolean> desired,boolean forceReset) {
        if(player==null||player.connection==null)return;
        ClientPolicy state=clientPolicy(player);if(state==null)return;
        if(forceReset)state.resetPending=true;
        if(!ProNetwork.present(player))return;
        String dimension=player.serverLevel().dimension().location().toString();
        if(state.resetPending||!state.active||!state.dimension.equals(dimension)) {
            state.resetPending=true;
            if(!resetPolicyScope(player,state,dimension))return;
            state.resetPending=false;
        }
        if(state.sent.size()>=131072||state.terminals.size()>=131072||state.hudSent.size()>=131072) {
            if(!resetPolicyScope(player,state,dimension))return;
        }
        // Revoke stale exact identities first, so an id-reused protected neighbour receives the newer revision.
        for(var entry:List.copyOf(state.sent.entrySet())) {
            PolicyAddress address=entry.getKey();SentPolicy sent=entry.getValue();
            if(!address.dimension().equals(dimension)||!sent.protectedNow()||desired.containsKey(address))continue;
            long revision=state.revision+1;
            if(ProNetwork.protection(player,session,state.scope,dimension,revision,address.entityId(),address.entityUuid(),false)) {
                state.revision=revision;state.sent.put(address,new SentPolicy(revision,false));
            }
        }
        for(var entry:desired.entrySet()) {
            PolicyAddress address=entry.getKey();if(!address.dimension().equals(dimension))continue;
            SentPolicy sent=state.sent.get(address);
            if(sent!=null&&sent.protectedNow())continue;
            long revision=state.revision+1;
            if(ProNetwork.protection(player,session,state.scope,dimension,revision,address.entityId(),address.entityUuid(),true)) {
                state.revision=revision;state.sent.put(address,new SentPolicy(revision,true));
            }
        }
        publishTerminalPolicy(player,state);publishHudPolicy(player,state);
    }
    private record BossFields(java.lang.reflect.Field[] fields,boolean unavailable) { }
    private static final ClassValue<BossFields> BOSS_FIELDS=new ClassValue<>() {
        protected BossFields computeValue(Class<?> type) {
            var result=new ArrayList<java.lang.reflect.Field>();
            boolean unavailable=false;
            for(Class<?> current=type;current!=null&&current!=Entity.class;current=current.getSuperclass())for(var field:current.getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&ServerBossEvent.class.isAssignableFrom(field.getType())) {
                    try {if(field.trySetAccessible())result.add(field);else unavailable=true;}
                    catch(RuntimeException denied){unavailable=true;}
                }
            return new BossFields(result.toArray(java.lang.reflect.Field[]::new),unavailable);
        }
    };
    private record HudAddress(String dimension,UUID id) { }
    private final Map<HudAddress,Subject> retiredHudSubjects=new HashMap<>();
    private long hudScanTick=-1;
    private Map<ServerBossEvent,Entity> hudOwners=Map.of();
    private Map<ServerBossEvent,Entity> exclusiveHudOwners() {
        if(hudScanTick==tick)return hudOwners;
        Map<ServerBossEvent,Entity> owners=new IdentityHashMap<>();Set<ServerBossEvent> shared=Collections.newSetFromMap(new IdentityHashMap<>());
        for(ServerLevel level:server.getAllLevels())for(Entity entity:level.getAllEntities()) {
            BossFields fields=BOSS_FIELDS.get(entity.getClass());if(fields.unavailable())coverageGap=true;
            for(var field:fields.fields())try {
                ServerBossEvent event=(ServerBossEvent)field.get(entity);if(event==null)continue;Entity prior=owners.putIfAbsent(event,entity);if(prior!=null&&prior!=entity)shared.add(event);
            }catch(IllegalAccessException unavailable){coverageGap=true;}
        }
        for(ServerBossEvent event:shared)owners.remove(event);
        hudOwners=Collections.unmodifiableMap(new IdentityHashMap<>(owners));hudScanTick=tick;return hudOwners;
    }
    static void sectionLeft(Entity entity,Object oldSection) {
        BackingPolicy.sectionLeft(oldSection,entity);
        synchronized(INDEX_CONTAINERS) {
            for(var row:INDEX_CONTAINERS.entrySet())
                INDEX_CONTAINERS.put(row.getKey(),row.getValue().stream().filter(slot->slot.entity().get()!=entity||slot.value().get()!=oldSection).toList());
        }
    }
    /** Observe a completed real callback transition before any third-party tick can see the new section. */
    public static void sectionMoved(Object owner) {
        if(!(owner instanceof Access.Callback callback)||!(callback.pro$entity() instanceof Entity entity))return;
        Binding binding=protectedBinding(entity);ProRuntime runtime=policyRuntime(entity);
        if(binding==null||runtime==null||!runtime.server.isSameThread()||((Access.EntityState)entity).pro$callback()!=owner)return;
        if(binding.records.section==callback.pro$sectionKey())return;
        EntityRecords next=new EntityRecords(binding.records.level,entity);
        if(!next.bound()||!next.registered()) {binding.protectionGap="SECTION_TRANSITION_INCOMPLETE";return;}
        binding.records=next;
        binding.protectionGap=runtime.publishIndexOwners(binding,entity);
        publishProtection(runtime,binding,entity);runtime.publishOwners(entity);
    }
    private void publishHudPolicy(ServerPlayer player,ClientPolicy state) {
        Map<UUID,Integer> desired=new HashMap<>();
        for(var entry:exclusiveHudOwners().entrySet()) {
            Binding binding=bindings.get(entry.getValue());if(binding==null||!binding.address.dimension().equals(state.dimension))continue;
            int mode=binding.subject.terminal?2:binding.protection&&binding.protectionGeneration==binding.subject.generation?1:0;
            if(mode!=0)desired.put(entry.getKey().getId(),mode);
        }
        for(var entry:retiredHudSubjects.entrySet())if(entry.getKey().dimension().equals(state.dimension)&&entry.getValue().terminal)
            desired.putIfAbsent(entry.getKey().id(),2);
        Set<UUID> ids=new HashSet<>(state.hudSent.keySet());ids.addAll(desired.keySet());
        for(UUID id:ids) {
            int mode=desired.getOrDefault(id,0);if(Objects.equals(state.hudSent.get(id),mode))continue;
            long revision=state.revision+1;
            if(ProNetwork.hud(player,session,state.scope,state.dimension,revision,id,mode==1,mode==2)) {state.revision=revision;state.hudSent.put(id,mode);}
        }
    }
    private void retainTerminalHud(Entity entity,Subject subject) {
        Binding binding=bindings.get(entity);if(binding==null)return;
        for(var entry:exclusiveHudOwners().entrySet())if(entry.getValue()==entity) {
            HudAddress address=new HudAddress(binding.address.dimension(),entry.getKey().getId());
            if(retiredHudSubjects.size()<CAPACITY||retiredHudSubjects.containsKey(address))retiredHudSubjects.put(address,subject);else coverageGap=true;
        }
    }
    private void publishTerminalPolicy(ServerPlayer player,ClientPolicy state) {
        for(Binding binding:List.copyOf(bindings.values()))if(binding.subject.terminal) {
            if(terminalAddresses.size()<CAPACITY||terminalAddresses.containsKey(binding.address))terminalAddresses.put(binding.address,binding.subject);
            else coverageGap=true;
        }
        for(var entry:terminalAddresses.entrySet()) {
            PolicyAddress address=entry.getKey();if(!address.dimension().equals(state.dimension))continue;
            boolean terminal=entry.getValue().terminal;SentPolicy previous=state.terminals.get(address);
            if(previous!=null&&previous.protectedNow()==terminal)continue;
            long revision=state.revision+1;
            if(ProNetwork.terminal(player,session,state.scope,state.dimension,revision,address.entityId(),address.entityUuid(),terminal)) {
                state.revision=revision;state.terminals.put(address,new SentPolicy(revision,terminal));
            }
        }
    }
    private Map<PolicyAddress,Boolean> currentPolicies() {
        Map<PolicyAddress,Boolean> desired=new HashMap<>();
        for(Binding binding:List.copyOf(bindings.values())) {
            Entity entity=binding.entity();
            if(binding.protection&&!binding.subject.terminal&&binding.protectionGeneration==binding.subject.generation
                    &&entity!=null&&entity!=removing&&!(entity instanceof ServerPlayer player
                    &&(pendingRespawnProtections.containsKey(player)||unverifiedRespawnPolicies.contains(player))))
                desired.put(binding.address,true);
        }
        return desired;
    }
    private void publishPolicy(ServerPlayer player,boolean forceReset) {
        publishPolicy(player,currentPolicies(),forceReset);
    }
    /** Login, dimension and same-dimension respawn each get a fresh scope before a full snapshot. */
    public void playerPolicyChanged(ServerPlayer player) {
        lifecycleAction("participant");
        thread();if(closing||player==null||player.connection==null)return;
        ClientPolicy prior=clientPolicies.get(player.connection);
        String dimension=player.serverLevel().dimension().location().toString();
        if(prior!=null&&prior.active&&!prior.resetPending&&prior.dimension.equals(dimension))return;
        publishPolicy(player,true);
    }
    public void playerDisconnected(ServerPlayer player) {
        lifecycleAction("participant");
        thread();if(!closing&&player!=null) {
            ArrayDeque<LifecyclePermit> scopes=PLAYER_LIFECYCLE.get();
            LifecyclePermit scope=scopes==null||scopes.isEmpty()?null:scopes.peek();
            if(scope!=null&&scope.runtime==this&&scope.thread==Thread.currentThread()&&scope.allows(player)
                    &&player.connection!=null&&!player.connection.connection.isConnected()) {
                Binding departed=bindings.get(player);
                if(departed!=null) {
                    revokeBound(player);departed.releaseOwnedReference();bindings.remove(player);
                }
            }
            pendingRespawnProtections.remove(player);unverifiedRespawnPolicies.remove(player);
            if(player.connection!=null)clientPolicies.remove(player.connection);
        }
    }
    /** Same player object moved dimensions: renew exact record ownership and fence all old HARD work. */
    public void playerChangedDimension(ServerPlayer player) {
        lifecycleAction("participant");
        thread();if(closing||player==null)return;
        Binding binding=bindings.get(player);
        if(binding!=null) {
            Work priorProtection=binding.latest;
            PolicyAddress prior=binding.address;
            var state=(Access.EntityState)player;
            if(state.pro$id()!=prior.entityId()||!prior.entityUuid().equals(state.pro$uuid())
                    ||!(state.pro$level() instanceof ServerLevel level)||level.getServer()!=server) {
                coverageGap=true;
            } else {
                if(prior.dimension().equals(level.dimension().location().toString()))return;
                boolean transfer=binding.protection&&binding.protectionGeneration==binding.subject.generation&&!binding.subject.terminal;
                float floor=binding.healthFloor;
                EntityRecords records=new EntityRecords(level,player);
                float health=transfer?healthItem(player).getValue():0;
                boolean retained=transfer&&Float.isFinite(health)&&health>0;
                try(var fieldGate=FieldPolicyGate.enter(player);var dataGate=FieldPolicyGate.enter(healthItem(player));
                        var gate=binding.subject.commitGate.enter()) {
                    binding.subject.generation++;
                    binding.records=records;
                    binding.address=new PolicyAddress(prior.entityId(),prior.entityUuid(),level.dimension().location().toString());
                    binding.protection=false;clearProtection(player);
                    if(retained) {
                        binding.healthFloor=Math.min(floor,health);binding.subject.terminal=false;activate(binding);
                    }
                }
                for(Work workItem:work.values())if(workItem.binding==binding)fence(workItem,"PLAYER_DIMENSION_SCOPE_CHANGED");
                if(retained) {UUID next=queue(binding,null,"PROTECT");if(priorProtection!=null)priorProtection.protectionSuccessor=next;}
                else if(transfer)coverageGap=true;
            }
        }
        publishPolicy(player,true);
    }
    /** Transfer only a currently installed SOFT protection through Forge's actual Clone relation. */
    public void playerCloned(ServerPlayer original,ServerPlayer replacement) {
        thread();if(closing||original==null||replacement==null||replacement.server!=server)return;
        var caller=LIFECYCLE_WALKER.walk(frames->frames.skip(1).findFirst().orElse(null));
        if(caller==null||caller.getDeclaringClass()!=ProMod.class||!caller.getMethodName().equals("playerClone"))return;
        Binding prior=bindings.get(original);
        if(prior==null||!prior.protection||prior.protectionGeneration!=prior.subject.generation||prior.subject.terminal)return;
        ArrayDeque<LifecyclePermit> scopes=PLAYER_LIFECYCLE.get();
        LifecyclePermit permit=scopes==null||scopes.isEmpty()?null:scopes.peek();
        if(permit==null||!permit.allows(original)||permit.runtime!=this||permit.thread!=Thread.currentThread()
                ||!server.isSameThread()) { coverageGap=true;return; }
        permit.entities.add(replacement);
        boolean transfer=prior!=null&&prior.protection&&prior.protectionGeneration==prior.subject.generation&&!prior.subject.terminal;
        UUID stablePlayerUuid=prior==null?null:prior.address.entityUuid();
        if(prior!=null) {
            revokeBound(original);
            prior.releaseOwnedReference();bindings.remove(original);
        }
        if(transfer)try {
            UUID next=protect(replacement,false);
            if(prior.latest!=null)prior.latest.protectionSuccessor=next;
            Binding replacementBinding=bindings.get(replacement);
            if(replacementBinding!=null&&replacementBinding.protection
                    &&replacementBinding.protectionGeneration==replacementBinding.subject.generation)
                pendingRespawnProtections.put(replacement,new PendingRespawnProtection(replacementBinding,stablePlayerUuid,
                        permit,replacementBinding.subject.generation));
            else { coverageGap=true;unverifiedRespawnPolicies.add(replacement); }
        } catch(RuntimeException unavailable) { coverageGap=true;unverifiedRespawnPolicies.add(replacement); }
        participantChanged(replacement);
        publishPolicy(original,true);
        if(replacement.connection!=original.connection)publishPolicy(replacement,true);
    }
    /** Rebinds a Clone-derived SOFT policy only after PlayerList has assigned the final respawn identity. */
    public void playerRespawned(ServerPlayer player) {
        lifecycleAction("participant");
        thread();if(closing||player==null)return;
        PendingRespawnProtection pending=pendingRespawnProtections.get(player);
        if(pending!=null) {
            ArrayDeque<LifecyclePermit> scopes=PLAYER_LIFECYCLE.get();
            LifecyclePermit active=scopes==null||scopes.isEmpty()?null:scopes.peek();
            Binding binding=pending.binding();
            var state=(Access.EntityState)player;
            boolean valid=active==pending.permit()&&active.runtime==this&&active.thread==Thread.currentThread()
                    &&active.allows(player)&&server.isSameThread()&&bindings.get(player)==binding&&binding.entity()==player
                    &&binding.protection&&!binding.subject.terminal&&binding.subject.generation==pending.generation()
                    &&player.getUUID().equals(pending.playerUuid())&&state.pro$uuid().equals(pending.playerUuid())
                    &&state.pro$level() instanceof ServerLevel level&&level.getServer()==server;
            if(valid) {
                ServerLevel level=(ServerLevel)state.pro$level();
                try {
                    EntityRecords records=new EntityRecords(level,player);
                    Work priorProtection=binding.latest;
                    PolicyAddress address=new PolicyAddress(state.pro$id(),state.pro$uuid(),
                            level.dimension().location().toString());
                    try(var fieldGate=FieldPolicyGate.enter(player);var dataGate=FieldPolicyGate.enter(healthItem(player));
                            var gate=binding.subject.commitGate.enter()) {
                        binding.subject.generation++;
                        binding.records=records;binding.address=address;
                        pendingRespawnProtections.remove(player);unverifiedRespawnPolicies.remove(player);
                        activate(binding);
                    }
                    for(Work old:work.values())if(old.binding==binding)fence(old,"PLAYER_RESPAWN_FINAL_IDENTITY_CHANGED");
                    UUID next=queue(binding,null,"PROTECT");if(priorProtection!=null)priorProtection.protectionSuccessor=next;
                } catch(RuntimeException unavailable) {
                    pendingRespawnProtections.remove(player);unverifiedRespawnPolicies.add(player);coverageGap=true;
                }
            } else {
                pendingRespawnProtections.remove(player);unverifiedRespawnPolicies.add(player);coverageGap=true;
            }
        }
        publishPolicy(player,true);
    }
    /** Pure presentation without an installed owner adapter is reported, never fabricated into an Entity duty. */
    public UUID clearPresentation(UUID owner,UUID entry) {
        thread();Objects.requireNonNull(owner);Objects.requireNonNull(entry);
        coverageGap=true;
        return null;
    }
    public List<String> historicalOperation(UUID id) {
        thread();String needle="\t"+id+"\t";
        return journal.history().stream().filter(line->line.contains(needle)).toList();
    }
    public void participantChanged(ServerPlayer player) {
        boolean authentic=ACTION_CALLER.walk(frames->frames
                .filter(frame->frame.getDeclaringClass()!=ProRuntime.class)
                .findFirst().map(frame->frame.getDeclaringClass()==ProMod.class
                        &&(frame.getMethodName().equals("participant")||frame.getMethodName().equals("playerClone"))).orElse(false));
        if(!authentic)throw new SecurityException("RONOVA_PARTICIPANT_AUTHORITY_REQUIRED");
        thread();
        for(Work w:work.values()) {
            ClientFact f=w.clients.get(player.getUUID());
            if(f!=null) {
                boolean changed=f.connection!=player.connection||player.connection==null
                        ||!player.connection.connection.isConnected()
                        ||w.records!=null&&w.records.level!=player.serverLevel()
                        ||w.binding!=null&&w.binding.entity()!=null&&w.binding.entity()!=player;
                if(!changed)continue;
                f.absent=false;f.last=-1;f.sequence=-1;f.challenge=UUID.randomUUID();
                if(f.required)invalidate(w,"PARTICIPANT_LIFECYCLE_CHANGED");
            }
        }
    }

    public void tick() {
        thread(); if(closing)return;
        long now=Integer.toUnsignedLong(server.getTickCount());
        if(now==tick)return;
        tick=now;
        for(Entity entity:List.copyOf(pendingGroupAdoptions))try {adoptGroupBody(entity);}
        catch(RuntimeException|LinkageError unavailable) {coverageGap=true;}
        publishPolicy();
        recoverySources.maintainResources();
        recoveryTasks.tick(tick);
        for(Work pending:work.values())if(pending.binding!=null&&pending.action.equals("CLEAR")&&pending.executed&&current(pending)
                &&!pending.state.equals("EFFECT_UNKNOWN")) {
            recoveryStorage.discover(pending.binding.subject,pending.id);
            recoveryTasks.qualify(pending.binding.subject,pending.id);
            recoveryRecords.qualify(pending.binding.subject,pending.id);
        }
        recoveryStorage.tick(tick);
        recoveryRecords.tick(tick);
        recoveryChain.tick(tick);
        for(Binding b:List.copyOf(bindings.values())) {
            try {
                if(b.protection && b.protectionGeneration==b.subject.generation) maintain(b);
                if(b.subject.terminal) advanceTerminal(b);
            } catch(RuntimeException ex) {
                coverageGap=true; b.protectionGap="MAINTENANCE_UNRESOLVED:"+ex.getClass().getSimpleName();
                if(b.latest!=null)invalidate(b.latest,"MAINTENANCE_UNRESOLVED");
            }
        }
        for(Work w:List.copyOf(work.values())) {
            if(w.action.equals("PROTECT")&&!current(w))retireProtectionSnapshots(w.binding.subject);
            retryPendingRecords(w);
            refreshIntentStatus(w);
            // Only a genuinely unknown effect, or a cancelled/fenced operation, stops this exact work.
            // A durability failure never freezes the memory action it was supposed to record.
            boolean direct=w.action.equals("CLEAR");
            if(w.state.equals("FENCED") || w.state.equals("EFFECT_UNKNOWN")
                    || w.state.equals("ACK_FAILED") && w.executed && !direct) continue;
            try {
                if(!w.executed) {
                    if(w.action.equals("PROTECT")) {
                        if(!w.ack.isDone()) continue;
                        if(w.ack.isCompletedExceptionally()) {
                            if(journal.backpressured(w.ack)) { w.reason="DURABLE_QUEUE_BUDGET_WAIT";continue; }
                            w.state="ACK_FAILED";w.reason="DURABLE_ACK_NOT_CONFIRMED";continue;
                        }
                        if(!current(w)) { fence(w,"FINAL_BINDING_OR_GENERATION");continue; }
                        w.executed=true;w.dispatching=true;
                        try { w.state="PROTECTION_ACTIVE";recordFact(w,"PROTECTION_ACTIVE"); }
                        finally { w.dispatching=false; }
                    } else if(w.action.equals("CLEAR")||w.action.equals("BLOCK_CLEAR"))executeMemoryAction(w);
                    if(!w.executed)continue;
                }
                if(w.action.equals("PROTECT")) {
                    if(!current(w))retireProtectionSnapshots(w.binding.subject);
                    continue;
                }
                if(w.state.equals("FENCED")||w.state.equals("EFFECT_UNKNOWN"))continue;
                if(!current(w)) { fence(w,"QUALIFICATION_OR_BINDING_CHANGED");continue; }
                if(tick%5==0) sample(w);
                releaseBodyReferences(w);
            } catch(Throwable ex) {
                w.state=w.dispatching?"EFFECT_UNKNOWN":w.executed?"VERIFYING":"FENCED";
                w.dispatching=false;
                w.reason=ex.getClass().getSimpleName()+":"+String.valueOf(ex.getMessage());
                recordFact(w,w.state);
            }
        }
    }
    private void refreshIntentStatus(Work w) {
        if(w.ack==null||!w.ack.isDone())w.obligations.put("DURABLE_INTENT","DURABLE_ACK_PENDING");
        else if(!w.ack.isCompletedExceptionally())w.obligations.put("DURABLE_INTENT","DURABLE_ACK_CONFIRMED");
        else {
            boolean retryable=journal.backpressured(w.ack);
            w.obligations.put("DURABLE_INTENT",retryable?"DURABLE_ACK_PENDING_RETRY":"DURABLE_ACK_UNCONFIRMED");
            if(!retryable)coverageGap=true;
        }
    }
    /** One non-replaying path for command-time and tick-time memory actions. */
    private void executeMemoryAction(Work w) {
        if(w.executed||w.cancelled||w.state.equals("FENCED")||w.state.equals("EFFECT_UNKNOWN")
                ||!(w.action.equals("CLEAR")||w.action.equals("BLOCK_CLEAR")))return;
        boolean entityClear=w.action.equals("CLEAR");
        if(!entityClear) {
            if(w.ack==null||!w.ack.isDone())return;
            if(w.ack.isCompletedExceptionally()) {
                if(journal.backpressured(w.ack)) { w.reason="DURABLE_QUEUE_BUDGET_WAIT";return; }
                w.state="ACK_FAILED";w.reason="DURABLE_ACK_NOT_CONFIRMED";return;
            }
        } else if(w.ack!=null&&w.ack.isCompletedExceptionally()&&!journal.backpressured(w.ack))coverageGap=true;
        if(!current(w)||(w.records!=null&&(!w.records.bound()||w.records.conflict()))
                ||(w.block!=null&&!w.block.bound())) { fence(w,"FINAL_BINDING_OR_GENERATION");return; }
        if(w.records!=null)mergeTrackedParticipants(w);
        if(w.participantAck!=null&&!w.participantAck.isDone())
            w.obligations.put("CLIENT_PRESENCE","PARTICIPANT_RECORD_PENDING_ACTION_CONTINUED");
        w.executed=true; // Set before callbacks: re-entry and uncertain outcomes must never replay this effect.
        w.dispatching=true;
        try {
            w.lifecycleEffects="OPAQUE_CALLBACK_EFFECTS_NOT_RECOVERY_COMPLETION";
            if(w.binding!=null) {
                Entity previousRemoving=removing;
                removing=w.binding.entity();
                try {
                    w.records.clear(()->current(w));
                    if(w.records.lifecycleUnresolved())w.obligations.put("LIFECYCLE","EFFECT_UNKNOWN_QUERY_ONLY");
                    if(w.records.currentCheckUnresolved()) {
                        coverageGap=true;w.state="EFFECT_UNKNOWN";w.reason="CURRENT_AUTHORITY_CHECK_UNRESOLVED";
                        w.obligations.put("CALLBACK_AUTHORITY","EFFECT_UNKNOWN_QUERY_ONLY");recordFact(w,w.state);
                    }
                } finally { removing=previousRemoving; }
            } else w.block.clear();
            if(!current(w))fence(w,"QUALIFICATION_OR_BINDING_CHANGED_DURING_CALLBACK");
            if(!w.state.equals("FENCED")&&!w.state.equals("EFFECT_UNKNOWN")) {
                w.state="VERIFYING";
                recordFact(w,"EXECUTED_QUERY_REQUIRED\t"+w.lifecycleEffects);
                for(UUID id:w.clients.keySet())sendProbe(w,id);
            }
        } catch(Throwable ex) {
            w.state="EFFECT_UNKNOWN";w.reason=ex.getClass().getSimpleName()+":"+String.valueOf(ex.getMessage());
            coverageGap=true;
            recordFact(w,w.state);
        } finally { w.dispatching=false; }
    }
    private boolean current(Work w) {
        return !closing && !w.cancelled && (w.binding==null ||
                w.generation==w.binding.subject.generation && (w.binding.entity()==null?w.referencesReleased:
                    bindings.get(w.binding.entity())==w.binding));
    }
    private boolean reserveAutomaticWork(Binding binding,Work prior) {
        try {ensureWorkCapacity();return true;}
        catch(IllegalStateException capacity) {
            if(!"WORK_CAPACITY_NO_ACCEPTANCE_UNSETTLED_DUTIES_RETAINED".equals(capacity.getMessage()))throw capacity;
            if(prior!=null) {invalidate(prior,"REASSERTION_BUDGET_WAIT");prior.reason="REASSERTION_BUDGET_WAIT";}
            return false; // The binding remains terminal; maintenance retries after reclamation.
        }
    }
    private void advanceTerminal(Binding b) {
        if(b.entity()==null)return;
        if(bindings.get(b.entity())!=b)return;
        Work prior=b.latest;
        if(prior==null || prior.generation!=b.subject.generation) {
            if(reserveAutomaticWork(b,prior))queue(b,null,"CLEAR");
            return;
        }
        if(!prior.executed || prior.cancelled || !prior.action.equals("CLEAR"))return;
        if(((Access.EntityState)b.entity()).pro$removal()==null) {
            if(!reserveAutomaticWork(b,prior))return;
            EntityRecords fresh=new EntityRecords((ServerLevel)((Access.EntityState)b.entity()).pro$level(),b.entity());
            // A real revived incarnation is a new exact operation, never a replay of an uncertain callback.
            Binding revived=new Binding(b.entity(),b.subject,fresh);revived.predecessor=prior.id;
            bindings.put(b.entity(),revived);b.subject.sourceRevision.incrementAndGet();
            fence(prior,"INCARNATION_REVIVED");
            queue(revived,null,"CLEAR");
            return;
        }
        Set<String> left=prior.records.remaining();
        if(left.stream().anyMatch(x->Set.of("LOOKUP_ID","LOOKUP_UUID","SECTION","TICK","TRACKING","KNOWN_UUID").contains(x))) {
            mergeTrackedParticipants(prior);
            invalidate(prior,"EXACT_RECORD_REINSERTED");
            try {
                prior.records.clearRecords();
                recordFact(prior,"EXACT_RECORDS_REASSERTED");
            } catch(RuntimeException ex) {
                prior.reason="REASSERTION:"+ex;
                recordFact(prior,"REASSERTION_UNRESOLVED");
            }
        }
    }
    private void maintain(Binding b) {
        Entity entity=b.entity();if(entity==null) { b.protectionGap="PROTECTED_BODY_UNAVAILABLE";return; }
        EntityRecords repaired=b.records.repairProtectedCarrier();
        if(repaired==null)b.protectionGap="PROTECTED_CARRIER_REPAIR_UNAVAILABLE";
        if(repaired!=null) {
            b.records=repaired;
            // A legitimate move may advance the local snapshot to the independently intact current records.
            EntityRecords current=new EntityRecords((ServerLevel)((Access.EntityState)b.entity()).pro$level(),b.entity());
            if(current.section!=b.records.section && current.registered()) b.records=current;
            b.protectionGap=b.records.repair()?(indexGuardState.startsWith("INSTALLED")?"":"BACKING_INDEX_GUARD_UNAVAILABLE:"+indexGuardState):"CONTAINER_SEMANTICS_OR_RECORD_OWNER_UNCONFIRMED";
        }
        if(b.entity() instanceof LivingEntity living) {
            var raw=(Access.EntityState)living;
            float floor=b.healthFloor;
            var maximum=((Access.Living)living).pro$attributes().getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if(maximum!=null && Float.isFinite((float)maximum.getValue())) floor=Math.min(floor,(float)maximum.getValue());
            try {
                var item=healthItem(living);
                if(Float.isFinite(floor) && floor>0 && (!Float.isFinite(item.getValue())||item.getValue()<floor)) {
                    item.setValue(floor); item.setDirty(true); ((Access.Data)raw.pro$data()).pro$dirty(true);
                }
            } catch(RuntimeException unavailable) {
                b.protectionGap="HEALTH_WRITE_BACKEND_UNAVAILABLE";
            }
            if(raw.pro$fire()>0) raw.pro$fire(0);
        }
    }
    private static net.minecraft.network.syncher.SynchedEntityData.DataItem<Float> healthItem(LivingEntity living) {
        // The exact accessor is used directly; a replaced backing map is reported by the caller, not by refusing
        // the write the game itself would have performed.
        return ((Access.Data)((Access.EntityState)living).pro$data()).pro$item(Access.Living.pro$healthKey());
    }
    private void sample(Work w) {
        Set<String> remaining=w.records==null?w.block.remaining():w.records.remaining();
        if(w.binding!=null && w.observedSourceRevision!=w.binding.subject.sourceRevision.get()) {
            w.observedSourceRevision=w.binding.subject.sourceRevision.get();
            invalidate(w,"SOURCE_REVISION_CHANGED");
        }
        if(w.records!=null && w.records.bound())mergeTrackedParticipants(w);
        for(var entry:w.clients.entrySet()) {
            ClientFact f=entry.getValue();
            ServerPlayer participant=server.getPlayerList().getPlayer(entry.getKey());
            String clientGap="";
            if(participant==null) clientGap="CLIENT_DISCONNECTED";
            else if(f.connection!=participant.connection) {
                f.connection=participant.connection; f.challenge=UUID.randomUUID(); f.sequence=-1; f.last=-1; f.absent=false;
                clientGap="PARTICIPANT_SESSION_CHANGED"; sendProbe(w,entry.getKey());
            } else if(participant.serverLevel()!=w.records.level) clientGap="CLIENT_WORLD_CHANGED";
            else if(!f.absent || f.last<0 || tick<f.last || tick-f.last>10) {
                clientGap="CLIENT_PRESENCE_UNCONFIRMED"; sendProbe(w,entry.getKey());
            }
            if(!clientGap.isEmpty() && f.required)remaining.add("REQUIRED_"+clientGap);
            w.obligations.put("CLIENT:"+entry.getKey(),clientGap.isEmpty()?"CONFIRMED":
                    (f.required?"REQUIRED_UNKNOWN:":"OPTIONAL_UNOBSERVED:")+clientGap);
        }
        if(w.ack==null||!w.ack.isDone())remaining.add("DURABLE_INTENT_ACK_PENDING");
        else if(w.ack.isCompletedExceptionally())remaining.add(journal.backpressured(w.ack)
                ?"DURABLE_INTENT_ACK_PENDING":"DURABLE_INTENT_ACK_UNCONFIRMED");
        if(w.factAck==null || !w.factAck.isDone() || w.factAck.isCompletedExceptionally()) remaining.add("RESULT_ACK_PENDING");
        if(w.participantAck!=null && (!w.participantAck.isDone() || w.participantAck.isCompletedExceptionally()))remaining.add("PARTICIPANT_ACK_PENDING");
        // O01 proves this body. Incomplete source coverage remains explicit for O02, not silently claimed.
        for(var entry:w.obligations.entrySet()) {
            if(entry.getValue().startsWith("NOT_APPLICABLE") || entry.getKey().startsWith("CLIENT:")
                    || entry.getKey().equals("DURABLE_INTENT") || entry.getValue().contains("OPAQUE")
                    || entry.getValue().contains("UNKNOWN"))continue;
            String key=entry.getKey();
            boolean unknown=remaining.stream().anyMatch(g->g.contains("UNOBSERVED") || g.contains("OWNER") || g.contains("CHUNK"));
            boolean pending=unknown || remaining.stream().anyMatch(g->g.startsWith(key)
                    || key.equals("LOOKUP") && g.equals("KNOWN_UUID")
                    || key.equals("RELATIONS") && g.endsWith("_EDGE")
                    || key.equals("CLIENT_PRESENCE") && (g.contains("CLIENT") || g.contains("PARTICIPANT")));
            entry.setValue(unknown?"REQUIRED_UNKNOWN":pending?"REQUIRED_PENDING":"REQUIRED_CONFIRMED");
        }
        if(w.lastSample>=0 && (tick<w.lastSample || tick-w.lastSample>10)) w.stableSince=-1;
        w.lastSample=tick;
        if(!remaining.isEmpty()) { invalidate(w,String.join(",",remaining)); return; }
        if(w.stableSince<0) w.stableSince=tick;
        if(tick-w.stableSince>=20 && !w.complete()) {
            w.state="BODY_CLEAR_STABLE"; w.reason=w.clients.values().stream().noneMatch(f->f.required)
                    ?"SERVER_SCOPE_NO_REQUIRED_CLIENT":"REQUIRED_CLIENTS_CONFIRMED";
            w.receiptRecord="RECEIPT\t"+w.id+"\tO01\t"+w.generation+"\t"+tick+"\t"+w.reason;
            w.receiptAck=journal.append(w.receiptRecord);
        }
    }
    private void mergeTrackedParticipants(Work w) {
        boolean changed=false;
        for(UUID id:w.records.participants()) {
            ClientFact f=w.clients.get(id);
            if(f==null) { w.clients.put(id,new ClientFact());changed=true; }
            else if(!f.required) { f.required=true;changed=true; }
        }
        if(changed) {
            w.obligations.put("CLIENT_PRESENCE","REQUIRED_PENDING");
            recordParticipants(w);
            invalidate(w,"NEW_REQUIRED_TRACKING");
        }
    }
    private void recordParticipants(Work w) {
        var required=new TreeSet<UUID>();var optional=new TreeSet<UUID>();
        w.clients.forEach((id,f)->{if(f.required)required.add(id);else optional.add(id);});
        w.participantRecord="PARTICIPANTS\t"+w.id+"\trequired="+required+"\toptional="+optional;
        w.participantAck=journal.append(w.participantRecord);
    }
    private void recordFact(Work w,String fact) {
        w.factRecord="FACT\t"+w.id+"\t"+fact;
        w.factAck=journal.append(w.factRecord);
    }
    private void retryPendingRecords(Work w) {
        if(tick<w.retryAt)return;
        w.retryAt=tick+20;
        if((!w.executed||journal.backpressured(w.ack))&&current(w)&&!w.state.equals("FENCED"))
            w.ack=journal.retryRejected(w.intentRecord,w.ack);
        w.participantAck=journal.retryRejected(w.participantRecord,w.participantAck);
        w.factAck=journal.retryRejected(w.factRecord,w.factAck);
        w.receiptAck=journal.retryRejected(w.receiptRecord,w.receiptAck);
    }
    private void fence(Work w,String reason) {
        w.stableSince=-1;
        if(w.state.equals("EFFECT_UNKNOWN"))return;
        w.state="FENCED";w.reason=reason;
        if(!closing)for(var entry:w.clients.entrySet()) {
            ServerPlayer p=server.getPlayerList().getPlayer(entry.getKey());
            if(p!=null)ProNetwork.forget(p,session,w.id,entry.getValue().challenge);
        }
    }
    private void invalidate(Work w,String reason) {
        w.stableSince=-1;
        if(w.state.equals("EFFECT_UNKNOWN") || w.state.equals("ACK_FAILED") || w.state.equals("FENCED"))return;
        if(w.executed && !w.action.equals("PROTECT")) w.state="VERIFYING";
        w.reason=reason;
    }
    private void sendProbe(Work w,UUID id) {
        ServerPlayer p=server.getPlayerList().getPlayer(id);
        if(p!=null && w.binding!=null) {
            ClientFact f=w.clients.get(id);
            if(f.connection!=p.connection) {
                f.connection=p.connection;f.challenge=UUID.randomUUID();f.sequence=-1;f.last=-1;f.absent=false;
                if(f.required)invalidate(w,"PARTICIPANT_SESSION_CHANGED");
            } else if(f.last>=0 && (tick<f.last || tick-f.last>10)) {
                // A watcher may have expired. A fresh challenge makes its sequence reset unambiguous.
                f.challenge=UUID.randomUUID();f.sequence=-1;f.last=-1;f.absent=false;
                if(f.required)invalidate(w,"CLIENT_OBSERVATION_EXPIRED");
            }
            ProNetwork.probe(p,session,w.id,f.challenge,w.records.level.dimension().location().toString(),
                    w.records.id,w.records.uuid);
        }
    }
    void reply(ServerPlayer player,UUID session,UUID op,UUID challenge,long sequence,boolean absent) {
        thread(); if(!this.session.equals(session)) return;
        Work w=work.get(op); if(w==null || !w.executed || w.binding==null || !current(w)) return;
        ClientFact f=w.clients.get(player.getUUID()); if(f==null || f.connection!=player.connection || !f.challenge.equals(challenge)) return;
        if(sequence<=f.sequence) return;
        f.sequence=sequence;
        if(player.serverLevel()!=w.records.level) { f.absent=false;if(f.required)invalidate(w,"CLIENT_WORLD_CHANGED");return; }
        f.last=tick; f.absent=absent;
        if(!absent && f.required) invalidate(w,"CLIENT_ENTITY_PRESENT");
    }

    public static void joined(Entity e) {
        ProRuntime r=runtime(e); if(r==null)return;
        if(r.stoppedModules.contains(e.getClass().getModule())) {
            try {r.clearBody(e);}catch(RuntimeException|LinkageError unavailable){r.coverageGap=true;}
            return;
        }
        if(r.protectedModules.contains(e.getClass().getModule())) {
            try {r.protect(e,true);r.pendingGroupAdoptions.add(e);}
            catch(RuntimeException|LinkageError unavailable){r.coverageGap=true;}
        }
        Binding b=r.bindings.get(e);
        if(b==null) {
            UUID origin=r.recoveryTasks.subjectOf(e);
            if(origin!=null) { r.linkLoaded(e,origin);b=r.bindings.get(e); }
            else for(Work item:r.work.values())if(item.records!=null&&item.binding!=null&&item.binding.subject.terminal
                    &&item.action.equals("CLEAR")&&item.executed&&item.records.definition==e.getClass()) {
                String reason="UNCLASSIFIED_NEW_BODY_SAME_DEFINITION:"+((Access.EntityState)e).pro$uuid();
                Subject subject=item.binding.subject;
                if(subject.unresolvedSources.add(reason)) {
                    subject.sourceRevision.incrementAndGet();
                    r.journal.append("SOURCE_UNKNOWN/1\t"+subject.id+"\t"+item.id+"\t"+r.session+"\t"+reason);
                }
            }
        }
        if(b!=null && b.latest==null) {
            try { b.records=new EntityRecords((ServerLevel)((Access.EntityState)e).pro$level(),e);
                if(!BackingPolicy.capture(b.records,e))r.coverageGap=true; }
            catch(RuntimeException ex) { r.coverageGap=true; }
        }
    }

    private static ProRuntime runtime(Entity e) {
        return ((Access.EntityState)e).pro$level() instanceof ServerLevel l && l.getServer().isSameThread()?get(l.getServer()):null;
    }
    static ProRuntime sourceRuntime(net.minecraft.world.level.storage.DimensionDataStorage manager) {
        ProRuntime known=SOURCE_OWNERS.get(manager);if(known!=null)return known;
        synchronized(SERVERS) {
            for(ProRuntime runtime:SERVERS.values())if(runtime.server.isSameThread())
                for(ServerLevel level:runtime.server.getAllLevels())if(level.getDataStorage()==manager) {
                    SOURCE_OWNERS.put(manager,runtime);return runtime;
                }
        }
        return null;
    }
    static List<ProRuntime> sourceRuntimes() { synchronized(SERVERS) { return List.copyOf(SERVERS.values()); } }
    private static boolean playerLifecycleAllows(Entity entity) {
        ArrayDeque<LifecyclePermit> stack=PLAYER_LIFECYCLE.get();
        if(stack==null||stack.isEmpty())return false;
        LifecyclePermit permit=stack.peek();
        return permit.allows(entity)&&permit.thread==Thread.currentThread()&&permit.runtime.server.isSameThread()
                &&vanillaLifecycleMutation();
    }
    private static boolean vanillaLifecycleMutation() {
        return LIFECYCLE_WALKER.walk(frames->{
            for(var frame:frames.toList()) {
                Class<?> type=frame.getDeclaringClass();String name=type.getName(),method=frame.getMethodName();
                if(type==ProRuntime.class||type==BackingPolicy.class||type==EntityRecords.class&&method.equals("registeredUuid")||name.startsWith("dev.ronova.pro.bootstrap.")
                        ||type.getClassLoader()==null||name.startsWith("it.unimi.dsi.fastutil."))continue;
                if(validLifecycleCaller(frame,null)||validLifecycleCaller(frame,((LifecyclePermit)PLAYER_LIFECYCLE.get().peek()).destination))return true;
                if(type==Entity.class||type==LivingEntity.class||type==net.minecraft.world.entity.player.Player.class||type==ServerPlayer.class||type==ServerLevel.class
                        ||type==net.minecraft.server.level.ServerChunkCache.class||type==net.minecraft.server.level.DistanceManager.class
                        ||type==net.minecraft.world.level.entity.PersistentEntitySectionManager.class
                        ||name.equals("net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
                        ||name.equals("net.minecraft.server.level.ServerLevel$EntityCallbacks")
                        ||type==net.minecraft.world.level.entity.EntityLookup.class||type==net.minecraft.world.level.entity.EntitySection.class
                        ||type==net.minecraft.world.level.entity.EntityTickList.class||type==net.minecraft.server.level.ChunkMap.class
                        ||name.equals("net.minecraft.server.level.ChunkMap$TrackedEntity")||type==net.minecraft.util.ClassInstanceMultiMap.class) {
                    // Merged Mixin callbacks are not vanilla lifecycle mutation bodies.
                    if(method.contains("$")&&!method.endsWith("$guard")&&!method.endsWith("$removed")&&!method.endsWith("$removePlayer")
                            &&!(type==net.minecraft.world.level.entity.PersistentEntitySectionManager.class&&method.endsWith("$uuid")))return false;
                    continue;
                }
                return false;
            }
            return false;
        });
    }
    public static boolean preventRemoval(Entity e,Entity.RemovalReason reason) {
        if(clientDispatchDenied(e,"remove",reason))return true;
        if(playerLifecycleAllows(e)&&e instanceof ServerPlayer
                &&(reason==Entity.RemovalReason.CHANGED_DIMENSION||reason==Entity.RemovalReason.DISCARDED
                    ||reason==Entity.RemovalReason.UNLOADED_WITH_PLAYER))return false;
        return protectedBinding(e)!=null;
    }
    /** Exact current protection. Live binding on the server thread, published snapshot on any other thread. */
    private static Binding protectedBinding(Entity entity) {
        ProtectionSnapshot snapshot=PROTECTION.get(entity);
        if(snapshot==null||snapshot.runtime().closing)return null;
        Binding binding=snapshot.binding();
        return binding.entity()==entity&&binding.protection&&!binding.subject.terminal
                &&binding.protectionGeneration==binding.subject.generation?binding:null;
    }
    /**
     * Registration removal guard. Illegal deletion of an exact protected object's slots is rejected in the
     * actual backing container, not only at the API entry. Legitimate neighbours pass unchanged.
     */
    public static boolean preventIndexRemoval(Object container,Object key) {
        if(container==null)return false;
        Object value=container instanceof Map<?,?> map?map.get(key):null;
        if(!(value instanceof Entity entity))return false;
        return preventIndexRemoval(entity);
    }
    public static boolean preventIndexRemoval(Entity entity) {
        if(entity==null)return false;
        Binding binding=protectedBinding(entity);if(binding==null)return false;
        return !playerLifecycleAllows(entity);
    }
    /** Called only by bytecode woven at validated vanilla lifecycle entries; ordinary callers cannot open a scope. */
    public static Object beginPlayerLifecycle(Entity entity,ServerLevel destination) {
        if(!(entity instanceof ServerPlayer)||entity==null)return null;
        var caller=LIFECYCLE_WALKER.walk(frames->frames.skip(1).findFirst().orElse(null));
        if(!validLifecycleCaller(caller,destination))return null;
        if(caller.getDeclaringClass()==net.minecraft.server.players.PlayerList.class
                &&(caller.getMethodName().equals("remove")||caller.getMethodName().equals("m_11286_"))
                &&preventLiveLogout((ServerPlayer)entity))return null;
        ProRuntime runtime=runtime(entity);
        if(runtime==null||!runtime.server.isSameThread()||runtime.closing)return null;
        Binding binding=runtime.bindings.get(entity);
        if(binding==null||!binding.protection||binding.subject.terminal
                ||binding.protectionGeneration!=binding.subject.generation)return null;
        if(destination!=null) {
            net.minecraft.world.level.Level current=((Access.EntityState)entity).pro$level();
            if(current==destination||!(current instanceof ServerLevel from)||destination.getServer()!=from.getServer())return null;
        }
        LifecyclePermit permit=new LifecyclePermit(entity,runtime,Thread.currentThread(),destination);
        ArrayDeque<LifecyclePermit> stack=PLAYER_LIFECYCLE.get();
        if(stack==null) { stack=new ArrayDeque<>();PLAYER_LIFECYCLE.set(stack); }
        stack.push(permit);return permit;
    }
    private static boolean validLifecycleCaller(java.lang.StackWalker.StackFrame caller,ServerLevel destination) {
        if(caller==null)return false;
        Class<?> type=caller.getDeclaringClass();String method=caller.getMethodName(),descriptor=caller.getDescriptor();
        boolean respawn=(method.equals("respawn")||method.equals("m_11236_"))
                &&descriptor.equals("(Lnet/minecraft/server/level/ServerPlayer;Z)Lnet/minecraft/server/level/ServerPlayer;");
        boolean logout=(method.equals("remove")||method.equals("m_11286_"))
                &&descriptor.equals("(Lnet/minecraft/server/level/ServerPlayer;)V");
        if(type==net.minecraft.server.players.PlayerList.class&&(respawn||logout))return destination==null;
        if(destination==null)return false;
        boolean change=method.equals("changeDimension")||method.equals("m_5489_");
        if(type==Entity.class&&change&&(descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;")
                ||descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;")))return true;
        if(type==ServerPlayer.class&&change
                &&descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;"))return true;
        boolean teleport=method.equals("teleportTo")||method.equals("m_264318_")||method.equals("m_8999_");
        return type==ServerPlayer.class&&teleport
                &&(descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z")
                ||descriptor.equals("(Lnet/minecraft/server/level/ServerLevel;DDDFF)V"));
    }
    public static void endPlayerLifecycle(Object token) {
        if(!(token instanceof LifecyclePermit permit)||permit.closed)return;
        ArrayDeque<LifecyclePermit> stack=PLAYER_LIFECYCLE.get();
        if(stack==null||stack.isEmpty()) {
            permit.runtime.coverageGap=true;finishPendingRespawns(permit);permit.closed=true;return;
        }
        if(stack.peek()!=permit) {
            permit.runtime.coverageGap=true;
            if(!stack.remove(permit)) { finishPendingRespawns(permit);permit.closed=true;return; }
        } else stack.pop();
        finishPendingRespawns(permit);
        permit.closed=true;if(stack.isEmpty())PLAYER_LIFECYCLE.remove();
    }
    private static void finishPendingRespawns(LifecyclePermit permit) {
        for(var entry:List.copyOf(permit.runtime.pendingRespawnProtections.entrySet()))if(entry.getValue().permit()==permit) {
            permit.runtime.pendingRespawnProtections.remove(entry.getKey());
            permit.runtime.unverifiedRespawnPolicies.add(entry.getKey());permit.runtime.coverageGap=true;
        }
    }
    /** The exact registration of a currently protected object was refused; the fact is recorded, not inferred. */
    public static void admissionRefused(Entity entity,UUID uuid) {
        ProRuntime runtime=policyRuntime(entity);
        if(runtime==null)return;
        runtime.coverageGap=true;
        runtime.journal.append("ADMISSION_REFUSED/1\t"+uuid+"\t"+runtime.session+"\tEXACT_OBJECT_UNDER_CURRENT_PROTECTION");
    }
    public static boolean preventDamage(Entity entity) { return protectedBinding(entity)!=null||clientDispatchDenied(entity,"hurt",null); }
    public static boolean preventHealthLoss(LivingEntity entity,float value) {
        Binding binding=protectedBinding(entity);if(binding==null)return false;
        if(!Float.isFinite(value)||value<binding.healthFloor)return true;
        try { float current=healthItem(entity).getValue();return Float.isFinite(current)&&value<current; }
        catch(RuntimeException unavailable) { binding.protectionGap="HEALTH_WRITE_BACKEND_UNAVAILABLE";return true; }
    }
    /**
     * Fire is a state writer the target can drive either through the method or through a direct field write.
     * Both are refused on a protected body when they would set a positive value; clearing fire to zero stays
     * legitimate, including our own disposal path. This is a pre-write refusal, not a value repaired afterwards.
     */
    public static boolean preventAbsorptionLoss(LivingEntity entity,float value) {
        if(protectedBinding(entity)==null)return false;
        return !Float.isFinite(value)||value<0||value<entity.getAbsorptionAmount();
    }
    public static boolean preventFireWrite(Entity entity,int ticks) { return ticks>0&&protectedBinding(entity)!=null; }
    /**
     * The exact original fields this build protects. They are matched by name against the declaring class, so a
     * same-named field on an unrelated class is never treated as protected state.
     */
    /**
     * Reflection field write on an exact protected object. The object in front of us is already established, so
     * the decision is immediate: a write to a protected state field of a protected body is refused. Unrelated
     * objects, unrelated fields and unprotected neighbours are never affected.
     */
    private static volatile java.lang.invoke.MethodHandle clientFields,clientDispatch,clientLifeResults;
    private static volatile boolean clientBasePolicyInstalled;
    static void installClientControls(java.lang.invoke.MethodHandle fields,java.lang.invoke.MethodHandle dispatch,java.lang.invoke.MethodHandle results) {
        Class<?> caller=LIFECYCLE_WALKER.getCallerClass();
        if(!caller.getName().equals("dev.ronova.pro.ClientPresence")||caller.getClassLoader()!=ProRuntime.class.getClassLoader())throw new SecurityException("CLIENT_CONTROL_OWNER");
        clientFields=fields;clientDispatch=dispatch;clientLifeResults=results;
        if(!productionControlsInstalled&&!clientBasePolicyInstalled)try {
            var lookup=java.lang.invoke.MethodHandles.lookup();
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            var field=lookup.findStatic(ProRuntime.class,"preventFieldWrite",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Class.class,String.class,Object.class));
            var method=lookup.findStatic(ProRuntime.class,"preventDispatch",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class,Object.class));
            var task=lookup.findStatic(ProRuntime.class,"preventTaskEffect",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class));
            bridge.getMethod("installGuards",Class.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class,
                    java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,field,method,task);
            installLifeResults(bridge,lookup);
            installEntityDispatch(Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader()));
            clientBasePolicyInstalled=true;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_BASE_POLICY_UNAVAILABLE",unavailable);}
    }
    static void registerClientControlObjects(Object... roots) {
        Class<?> caller=LIFECYCLE_WALKER.getCallerClass();
        if(!caller.getName().equals("dev.ronova.pro.ClientPresence")||caller.getClassLoader()!=ProRuntime.class.getClassLoader())
            throw new SecurityException("CLIENT_CONTROL_OWNER");
        try {Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null)
                .getMethod("registerControlObjects",Class.class,Object[].class).invoke(null,ProRuntime.class,roots);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("CLIENT_CONTROL_REGISTRATION_UNAVAILABLE",unavailable);}
    }
    private static boolean clientFieldDenied(Object receiver,Class<?> declaring,String field,Object value) {
        var guard=clientFields;if(guard==null)return false;
        try {return (boolean)guard.invokeExact(receiver,declaring,field,value);}catch(Throwable unavailable){return false;}
    }
    public static boolean preventFieldWrite(Object receiver,Class<?> declaring,String fieldName,Object value) {
        if(value instanceof Entity entity&&preventAdmission(entity))return true;
        if(BlockGroupPolicy.fieldDenied(receiver,declaring,fieldName,value))return true;
        for(ProRuntime runtime:sourceRuntimes())if(!runtime.closing&&runtime.recoveryRecords.fieldWriteDenied(receiver,declaring,fieldName,value))return true;
        if(receiver==declaring&&receiver instanceof Class<?> type&&SourceMaps.staticHolderDenied(type,fieldName,value))return true;
        if(receiver!=null&&!(receiver instanceof Class<?>)&&SourceMaps.instanceHolderDenied(receiver,declaring,fieldName,value))return true;
        if(clientFieldDenied(receiver,declaring,fieldName,value)||BackingPolicy.fieldDenied(receiver,declaring,fieldName,value))return true;
        if(declaring==net.minecraft.world.entity.ai.attributes.AttributeInstance.class&&receiver instanceof net.minecraft.world.entity.ai.attributes.AttributeInstance) {
            LivingEntity owner=attributeOwner(receiver);
            if(owner!=null)try {if(declaring.getDeclaredField(fieldName).getType()==double.class)return !(value instanceof Number number)||preventMaxHealthLoss(owner,number.doubleValue());}
            catch(NoSuchFieldException unavailable) {return true;}
        }
        if(declaring==net.minecraft.network.syncher.SynchedEntityData.DataItem.class&&receiver instanceof net.minecraft.network.syncher.SynchedEntityData.DataItem<?>)
            return (fieldName.equals("value")||fieldName.equals("f_135391_"))&&preventDataWrite(receiver,value);
        if(!(receiver instanceof Entity entity)||declaring==null||!declaring.isInstance(receiver))return false;
        if(declaring!=Entity.class&&declaring!=LivingEntity.class)return false;
        Binding binding=protectedBinding(entity);
        if(binding==null)return false;
        if(playerLifecycleAllows(entity)&&entity instanceof ServerPlayer) {
            if((fieldName.equals("canUpdate")||fieldName.equals("isAddedToWorld"))&&Boolean.FALSE.equals(value))return false;
            if((fieldName.equals("removalReason")||fieldName.equals("f_146795_"))
                    &&(value==Entity.RemovalReason.CHANGED_DIMENSION||value==Entity.RemovalReason.DISCARDED
                        ||value==Entity.RemovalReason.UNLOADED_WITH_PLAYER))return false;
        }
        return switch(fieldName) {
            case "id","f_19848_" -> !(value instanceof Number number)||number.intValue()!=binding.address.entityId()&&!respawnIdentityAssignment(entity);
            case "uuid","f_19820_" -> !Objects.equals(value,binding.address.entityUuid());
            case "stringUUID","f_19821_" -> !Objects.equals(value,binding.address.entityUuid().toString());
            case "remainingFireTicks","f_19831_" -> !(value instanceof Integer n)||n>0;
            case "removalReason","f_146795_" -> value!=null&&(!(value instanceof Entity.RemovalReason reason)||preventRemoval(entity,reason));
            case "canUpdate","isAddedToWorld" -> !Boolean.TRUE.equals(value);
            case "dead","f_20890_" -> !Boolean.FALSE.equals(value);
            case "deathTime","f_20919_" -> !(value instanceof Integer n)||n>0;
            default -> false;
        };
    }
    private static boolean respawnIdentityAssignment(Entity entity) {
        if(!(entity instanceof ServerPlayer player))return false;
        ProRuntime runtime=runtime(entity);
        if(runtime==null||!runtime.pendingRespawnProtections.containsKey(player)||!playerLifecycleAllows(entity))return false;
        return LIFECYCLE_WALKER.walk(frames->frames.anyMatch(frame->frame.getDeclaringClass()==net.minecraft.server.players.PlayerList.class
                &&(frame.getMethodName().equals("respawn")||frame.getMethodName().equals("m_11236_"))));
    }
    public static boolean preventDispatch(Object receiver,String operation,Object value) {
        if(clientDispatchDenied(receiver,operation,value))return true;
        if(!(receiver instanceof Entity entity))return false;
        ProRuntime groupRuntime=runtime(entity);
        if(groupRuntime!=null&&groupRuntime.modStopped(entity.getClass().getModule())&&switch(operation) {
            case "tick","m_8119_","baseTick","m_6061_","aiStep","m_8107_",
                 "serverAiStep","customServerAiStep","onAddedToWorld","onRemovedFromWorld",
                 "hurt","m_6469_","actuallyHurt","m_6475_","die","m_6667_" -> true;
            default -> false;
        })return true;
        Binding binding=protectedBinding(entity);
        if(operation.equals("tick")||operation.equals("m_8119_")||operation.equals("aiStep")||operation.equals("m_8107_")) {
            var snapshot=PROTECTION.get(entity);
            return snapshot!=null&&!snapshot.runtime().closing&&snapshot.binding().subject.terminal;
        }
        if(binding==null)return false;
        if(playerLifecycleAllows(entity)&&entity instanceof ServerPlayer
                &&(operation.equals("setRemoved")||operation.equals("m_142687_")||operation.equals("remove")||operation.equals("m_142467_"))
                &&(value==Entity.RemovalReason.CHANGED_DIMENSION||value==Entity.RemovalReason.DISCARDED
                    ||value==Entity.RemovalReason.UNLOADED_WITH_PLAYER))return false;
        return switch(operation) {
            case "setHealth","m_21153_" -> entity instanceof LivingEntity living&&value instanceof Number n&&preventHealthLoss(living,n.floatValue());
            case "setRemoved","m_142687_","remove","m_142467_" -> value instanceof Entity.RemovalReason reason&&preventRemoval(entity,reason);
            case "discard","m_146870_","kill","m_6074_" -> true;
            case "hurt","m_6469_","actuallyHurt","m_6475_","die","m_6667_" -> true;
            default -> false;
        };
    }
    public static boolean preventLiveLogout(ServerPlayer player) {
        return protectedBinding(player)!=null&&player.connection!=null&&player.connection.connection.isConnected();
    }
    public static boolean preventForcedRespawn(ServerPlayer player,boolean keepAllData) {
        if(protectedBinding(player)==null)return false;
        // Normal end-return is driven by the real client command handler; a direct mod call cannot replace a live protected body.
        if(keepAllData&&player.connection!=null&&player.connection.connection.isConnected()) {
            var caller=LIFECYCLE_WALKER.walk(frames->frames.filter(frame->frame.getDeclaringClass()!=ProRuntime.class
                    &&frame.getDeclaringClass()!=net.minecraft.server.players.PlayerList.class).findFirst().orElse(null));
            if(caller!=null&&caller.getDeclaringClass()==net.minecraft.server.network.ServerGamePacketListenerImpl.class
                    &&caller.getDescriptor().equals("(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V"))return false;
        }
        return true;
    }
    private static boolean clientDispatchDenied(Object receiver,String operation,Object value) {
        var client=clientDispatch;if(client==null)return false;
        try {return (boolean)client.invokeExact(receiver,operation,value);}catch(Throwable unavailable) {return false;}
    }
    public static boolean preventTaskEffect(Object object,String effect) {
        if(effect.equals("source-result")&&object instanceof Entity entity)return preventAdmission(entity);
        if(effect.equals("producer")) {
            ProducerOrigin selected=null;
            for(Object input:currentProducerInputs(false)) {
                if(!(input instanceof ProducerOrigin origin)||!origin.complete())return false;
                if(selected!=null&&(selected.runtime()!=origin.runtime()||!selected.subject().equals(origin.subject())))return false;
                selected=origin;
            }
            return selected!=null&&selected.runtime().terminalSource(selected.subject());
        }
        if(object instanceof Entity entity) {
            var snapshot=PROTECTION.get(entity);
            if(snapshot!=null&&!snapshot.runtime().closing&&snapshot.binding().subject.terminal)return true;
        }
        for(ProRuntime runtime:sourceRuntimes())
            if(!runtime.closing&&runtime.recoveryTasks.deniesEffect(object))return true;
        return false;
    }
    /** The event precedes index publication; adopt exact carriers only after the manager accepted the body. */
    public static void entityPublished(Entity entity) {
        ProRuntime runtime=runtime(entity);if(runtime==null)return;
        runtime.adoptGroupBody(entity);
    }
    static boolean allowInitialGroupCallback(Entity entity,Access.Callback callback) {
        ProRuntime runtime=runtime(entity);
        if(runtime==null||!runtime.pendingGroupAdoptions.contains(entity)||protectedBinding(entity)==null
                ||((Access.EntityState)entity).pro$callback() instanceof Access.Callback
                ||callback.pro$entity()!=entity)return false;
        return LIFECYCLE_WALKER.walk(frames->frames.anyMatch(frame->
                frame.getDeclaringClass()==net.minecraft.world.level.entity.PersistentEntitySectionManager.class));
    }
    private void adoptGroupBody(Entity entity) {
        if(!pendingGroupAdoptions.contains(entity))return;
        Binding binding=bindings.get(entity);
        if(binding==null||!binding.protection||binding.subject.terminal)return;
        EntityRecords records=new EntityRecords((ServerLevel)((Access.EntityState)entity).pro$level(),entity);
        if(!records.bound()||!records.registered()) {binding.protectionGap="NEW_BODY_REGISTRATION_INCOMPLETE";return;}
        try(var fieldGate=FieldPolicyGate.enter(entity);var authorityGate=binding.subject.commitGate.enter()) {
            if(binding.entity()!=entity||!binding.protection||binding.subject.terminal)return;
            binding.records=records;
            binding.protectionGap=publishIndexOwners(binding,entity);
        }
        if(((Access.EntityState)entity).pro$callback() instanceof Access.Callback)pendingGroupAdoptions.remove(entity);
        publishPolicy();
    }
    /** The JDK writer holds this gate only through its result field/CAS, before any completion callback. */
    public static AutoCloseable taskPublicationGate(Object result,Object task) {
        var selected=new ArrayList<Subject>();
        for(ProRuntime runtime:sourceRuntimes())if(!runtime.closing) {
            Subject fromResult=runtime.recoveryTasks.publicationSubject(result);
            Subject fromTask=runtime.recoveryTasks.publicationSubject(task);
            if(fromResult!=null&&!selected.contains(fromResult))selected.add(fromResult);
            if(fromTask!=null&&!selected.contains(fromTask))selected.add(fromTask);
        }
        if(selected.isEmpty())return null;
        selected.sort(Comparator.comparing(subject->subject.id));
        var held=new ArrayList<CommitGate>(selected.size());
        try {for(Subject subject:selected)held.add(subject.commitGate.enter());}
        catch(RuntimeException failure) {for(int i=held.size()-1;i>=0;i--)held.get(i).close();throw failure;}
        return ()->{for(int i=held.size()-1;i>=0;i--)held.get(i).close();};
    }
    private record UnsafeMapping(java.lang.reflect.Field[] fields,long[] offsets,String gap) { }
    private static volatile String dispatchState="NOT_DECLARED";
    private static volatile boolean productionControlsInstalled;
    public static String dispatchState() { return dispatchState; }
    public static boolean productionControlsInstalled() {
        if(!productionControlsInstalled)return false;
        try {return Boolean.TRUE.equals(Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,
                ClassLoader.getSystemClassLoader()).getMethod("controlReady").invoke(null));}
        catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    static void declareDispatchTargets() {
        try {
            // Establish the policy's own data classes before a newly guarded world writer can call into them.
            Class.forName(SourceMaps.class.getName(),true,ProRuntime.class.getClassLoader());
            Class.forName(BackingPolicy.class.getName(),true,ProRuntime.class.getClassLoader());
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            if(!Integer.valueOf(25).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("INDEX_BRIDGE_ABI_MISMATCH");
            var lookup=java.lang.invoke.MethodHandles.lookup();
            var field=lookup.findStatic(ProRuntime.class,"preventFieldWrite",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Class.class,String.class,Object.class));
            var dispatch=lookup.findStatic(ProRuntime.class,"preventDispatch",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class,Object.class));
            var task=lookup.findStatic(ProRuntime.class,"preventTaskEffect",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,String.class));
            var publication=lookup.findStatic(ProRuntime.class,"taskPublicationGate",
                    java.lang.invoke.MethodType.methodType(AutoCloseable.class,Object.class,Object.class));
            UnsafeMapping mapping=unsafeFields();
            var index=lookup.findStatic(ProRuntime.class,"preventIndexSlotRemoval",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
            bridge.getMethod("installPolicy",Class.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class,
                    java.lang.invoke.MethodHandle.class,java.lang.reflect.Field[].class,long[].class,java.lang.invoke.MethodHandle.class)
                .invoke(null,ProRuntime.class,field,dispatch,task,mapping.fields(),mapping.offsets(),index);
            bridge.getMethod("installTaskPublicationGate",Class.class,java.lang.invoke.MethodHandle.class)
                    .invoke(null,ProRuntime.class,publication);
            installLifeResults(bridge,lookup);
            BackingPolicy.initialize(bridge);
            var release=lookup.findStatic(BackingPolicy.class,"resourceDenied",java.lang.invoke.MethodType.methodType(boolean.class,Object.class));
            bridge.getMethod("installResourceGuard",Class.class,java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,release);
            var creation=lookup.findStatic(ProRuntime.class,"constructed",java.lang.invoke.MethodType.methodType(void.class,Object.class));
            var constructionFailure=lookup.findStatic(ProRuntime.class,"constructionFailed",java.lang.invoke.MethodType.methodType(void.class,Object.class));
            bridge.getMethod("installCreationObserver",Class.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,creation,constructionFailure);
            producerInputs=bridge.getMethod("producerInputs",boolean.class);producerDelivery=bridge.getMethod("deliveringProducerTo",Class.class);
            producerFields=bridge.getMethod("producerFields");
            producerReceiverDelivery=bridge.getMethod("deliveringProducerReceiverTo",Class.class);
            var producer=lookup.findStatic(ProRuntime.class,"producerInput",java.lang.invoke.MethodType.methodType(Object.class,Object.class,Object.class));
            bridge.getMethod("installProducerObserver",Class.class,java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,producer);
            var sourceWrite=lookup.findStatic(SourceMaps.class,"denies",java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
            var sourceChanged=lookup.findStatic(SourceMaps.class,"changed",java.lang.invoke.MethodType.methodType(void.class,Object.class));
            bridge.getMethod("installSourceWriteObserver",Class.class,java.lang.invoke.MethodHandle.class,java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,sourceWrite,sourceChanged);
            BackingPolicy.controllers(bridge);
            for(ProRuntime runtime:sourceRuntimes())BackingPolicy.runtimeControllers(runtime,runtime,runtime.journal,
                    runtime.recoverySources,runtime.recoveryStorage,runtime.recoveryTasks,runtime.recoveryRecords,runtime.recoveryChain);
            if(!mapping.gap().isEmpty())System.err.println("RONOVA_UNSAFE_POLICY_UNAVAILABLE:"+mapping.gap());
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            installEntityDispatch(agent);
            agent.getMethod("installIndexBoundaries").invoke(null);
            dispatchState=String.valueOf(agent.getMethod("dispatchState").invoke(null));
            indexGuardState=String.valueOf(agent.getMethod("indexGuardState").invoke(null));
            productionControlsInstalled=Boolean.TRUE.equals(agent.getMethod("dispatchReady").invoke(null))
                    &&indexGuardState.startsWith("INSTALLED")
                    &&Boolean.TRUE.equals(agent.getMethod("controlReady").invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            productionControlsInstalled=false;
            Throwable cause=unavailable;
            while(cause instanceof java.lang.reflect.InvocationTargetException call&&call.getCause()!=null)cause=call.getCause();
            dispatchState="DISPATCH_DECLARATION_FAILED:"+cause.getClass().getSimpleName()+":"+String.valueOf(cause.getMessage());
            System.err.println("RONOVA_"+dispatchState);
        }
    }
    /** The same method-entry protection is installed for a remote client, without starting server state. */
    private static void installLifeResults(Class<?> bridge,java.lang.invoke.MethodHandles.Lookup lookup)throws ReflectiveOperationException {
        var result=lookup.findStatic(ProRuntime.class,"protectedLifeResult",java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class));
        bridge.getMethod("installLifeResults",Class.class,java.lang.invoke.MethodHandle.class).invoke(null,ProRuntime.class,result);
    }
    public static Object protectedLifeResult(Object receiver,String method) {
        if(receiver instanceof LivingEntity living) {
            Binding binding=protectedBinding(living);
            if(binding!=null)return switch(method) {
                case "getHealth","m_21223_" -> {
                    float actual=healthItem(living).getValue(),floor=binding.healthFloor;
                    yield Float.valueOf(Float.isFinite(actual)?Math.max(actual,floor):floor);
                }
                case "isAlive","m_6084_" -> Boolean.TRUE;
                case "isDeadOrDying","m_21224_" -> Boolean.FALSE;
                default -> null;
            };
        }
        var client=clientLifeResults;
        if(client==null)return null;
        try {return (Object)client.invokeExact(receiver,method);}
        catch(Throwable failure){throw new IllegalStateException("CLIENT_LIFE_RESULT_FAILED",failure);}
    }
    private static void installEntityDispatch(Class<?> agent)throws ReflectiveOperationException {
        var declare=agent.getMethod("declareDispatchTarget",Class.class,String.class,String.class);
        String[][] entries={
            {"getHealth","m_21223_","()F"},{"isAlive","m_6084_","()Z"},{"isDeadOrDying","m_21224_","()Z"},
            {"remove","m_142467_","(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
            {"setRemoved","m_142687_","(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
            {"discard","m_146870_","()V"},{"kill","m_6074_","()V"},
            {"setHealth","m_21153_","(F)V"},{"tick","m_8119_","()V"},{"baseTick","m_6061_","()V"},
            {"aiStep","m_8107_","()V"},{"serverAiStep","","()V"},{"customServerAiStep","","()V"},
            {"onAddedToWorld","","()V"},{"onRemovedFromWorld","","()V"},
            {"hurt","m_6469_","(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
            {"actuallyHurt","m_6475_","(Lnet/minecraft/world/damagesource/DamageSource;F)V"},
            {"die","m_6667_","(Lnet/minecraft/world/damagesource/DamageSource;)V"}};
        for(Class<?> type:List.of(Entity.class,LivingEntity.class))for(String[] entry:entries)
            for(var method:type.getDeclaredMethods())
                if(method.getName().equals(entry[0])||method.getName().equals(entry[1]))
                    declare.invoke(null,type,method.getName(),entry[2]);
        declare.invoke(null,net.minecraftforge.eventbus.api.Event.class,"setCanceled","(Z)V");
        agent.getMethod("installDeclaredDispatch").invoke(null);
    }
    private static UnsafeMapping unsafeFields() {
        try {
            Class<?> type=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());
            var access=type.getDeclaredField("theUnsafe");access.setAccessible(true);Object unsafe=access.get(null);
            var offset=type.getMethod("objectFieldOffset",java.lang.reflect.Field.class);
            var fields=new ArrayList<java.lang.reflect.Field>();var offsets=new ArrayList<Long>();
            Set<String> names=Set.of("remainingFireTicks","f_19831_","removalReason","f_146795_","canUpdate","isAddedToWorld",
                    "dead","f_20890_","deathTime","f_20919_","value","f_135391_",
                    "remove","f_58859_","level","f_58857_","worldPosition","f_58858_","blockEntities","f_187610_");
            for(Class<?> owner:List.of(Entity.class,LivingEntity.class,net.minecraft.network.syncher.SynchedEntityData.DataItem.class,
                    net.minecraft.world.entity.ai.attributes.AttributeInstance.class,net.minecraft.world.level.block.entity.BlockEntity.class,
                    net.minecraft.world.level.chunk.ChunkAccess.class))
                for(var field:owner.getDeclaredFields())if((names.contains(field.getName())||owner==net.minecraft.world.entity.ai.attributes.AttributeInstance.class&&field.getType()==double.class)&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);offsets.add((Long)offset.invoke(unsafe,field));
                }
            long[] values=new long[offsets.size()];for(int i=0;i<values.length;i++)values[i]=offsets.get(i);
            return new UnsafeMapping(fields.toArray(java.lang.reflect.Field[]::new),values,"");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            return new UnsafeMapping(new java.lang.reflect.Field[0],new long[0],unavailable.getClass().getSimpleName());
        }
    }
    /**
     * Maximum-health writer on the real attribute instance. Only the maximum-health attribute of a protected
     * living body is considered, and only when it would drop the ceiling below the protected floor.
     */
    public static boolean preventMaxHealthLoss(LivingEntity entity,double value) {
        Binding binding=protectedBinding(entity);if(binding==null)return false;
        return !Double.isFinite(value)||value<binding.healthFloor;
    }
    /**
     * Direct synchronized-data write. The entity is resolved from the data item's real owner, so a field write
     * that never passes the public accessor is still decided at the actual mutation.
     */
    public static boolean preventDataWrite(Object dataItem,Object value) {
        var identity=SYNC_OWNERS.get(dataItem);Entity owner=identity==null?null:identity.get();
        if(!(owner instanceof LivingEntity living))return false;
        if(!(value instanceof Float written))return protectedBinding(living)!=null;
        return preventHealthLoss(living,written);
    }
    public static boolean preventAdmission(Entity e) {
        ProRuntime current=runtime(e);
        if(current!=null&&current.modStopped(e.getClass().getModule()))return true;
        var snapshot=PROTECTION.get(e);
        if(snapshot!=null&&!snapshot.runtime().closing&&snapshot.binding().subject.terminal)return true;
        // A real load/clone publishes its origin on the producing thread. Do not leave an
        // admission window while the server is still waiting to adopt that observation.
        for(ProRuntime runtime:sourceRuntimes()) {
            if(runtime.closing)continue;
            UUID origin=runtime.recoveryTasks.subjectOf(e);
            Subject subject=origin==null?null:runtime.subjects.get(origin);
            if(subject!=null&&subject.terminal)return true;
        }
        return RecoveryTasks.deniesCurrentPublication(e);
    }
    record ProducerOrigin(ProRuntime runtime,UUID subject,boolean complete) { }
    private static volatile java.lang.reflect.Method producerInputs,producerDelivery,producerReceiverDelivery,producerFields;
    public static Object producerInput(Object receiver,Object value) {
        try {if(producerDelivery==null||!Boolean.TRUE.equals(producerDelivery.invoke(null,ProRuntime.class)))return null;}
        catch(ReflectiveOperationException unavailable){return null;}
        if(value instanceof Object[] selector&&selector.length==7&&selector[0]==receiver&&receiver instanceof Entity entity) {
            if(((Access.EntityState)entity).pro$level() instanceof ServerLevel level) {
                ProRuntime runtime=get(level.getServer());
                if(runtime!=null&&!runtime.closing)runtime.recoveryRecords.identityAssigned(entity,selector);
            }
            return null;
        }
        if(value instanceof Object[] field&&field.length==5&&field[0]==receiver) {
            ProducerOrigin selected=null;
            for(ProRuntime runtime:sourceRuntimes())if(!runtime.closing) {
                Object origin=runtime.recoveryRecords.observedField(receiver,field);
                if(origin instanceof ProducerOrigin found) {
                    if(selected!=null&&(selected.runtime()!=found.runtime()||!selected.subject().equals(found.subject())))return null;
                    selected=found;
                }
            }
            return selected;
        }
        boolean invocationInput=false;
        try {invocationInput=receiver==value&&producerReceiverDelivery!=null
                &&Boolean.TRUE.equals(producerReceiverDelivery.invoke(null,ProRuntime.class));}
        catch(ReflectiveOperationException unavailable) {return null;}
        for(ProRuntime runtime:sourceRuntimes()) {
            if(runtime.closing)continue;
            Object target=RecoveryReferences.wrapperTarget(value,runtime,true);
            if(target==null&&invocationInput&&(value instanceof Entity||value!=null&&value.getClass()==CompoundTag.class))target=value;
            if(target==null&&RecoveryReferences.wrapperTarget(receiver,runtime,true)==value)target=value;
            if(target==null&&(receiver instanceof Map<?,?>||receiver instanceof Class<?>))target=RecoveryReferences.ownedTarget(value,runtime,true);
            UUID subject=target instanceof Entity?runtime.recoveryTasks.subjectOf(target):
                    target instanceof CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):null;
            if(subject==null)continue;
            if(receiver instanceof Map<?,?>&&!SourceMaps.currentSource(receiver,runtime.recoverySources,subject))continue;
            if(receiver instanceof Class<?> type&&runtime.recoverySources.registered().stream().noneMatch(source->source.holder==type&&source.subjects.contains(subject)&&runtime.recoverySources.liveRegistration(source)))continue;
            boolean complete=invocationInput;
            if(complete&&target instanceof CompoundTag tag) {
                complete=RecoverySources.tryRecordGraph();
                if(complete)try {complete=RecoverySources.recordGraphGap(runtime.recoverySources,tag,subject).isEmpty();}
                finally {RecoverySources.releaseRecordGraph();}
            }
            if(complete) {
                runtime.recoveryTasks.observed(target,subject);
                var authority=runtime.subjects.get(subject);
                if(authority!=null&&authority.terminal
                        &&authority.unresolvedSources.add("RETAINED_EXTERNAL_ALIAS_METHOD_STILL_LIVE"))authority.sourceRevision.incrementAndGet();
            }
            // Ambient reads are real observations, but they do not prove causality for a later allocation.
            return new ProducerOrigin(runtime,subject,complete);
        }
        return null;
    }
    static Object[] currentProducerInputs() {
        return currentProducerInputs(true);
    }
    private static Object[] currentProducerInputs(boolean includeParents) {
        try {return producerInputs==null?new Object[0]:(Object[])producerInputs.invoke(null,includeParents);}
        catch(ReflectiveOperationException unavailable) {return new Object[]{Boolean.FALSE};}
    }
    public static void constructed(Object object) {
        if(!(object instanceof Entity entity))return;
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            if(!Boolean.TRUE.equals(bridge.getMethod("deliveringCreationTo",Class.class).invoke(null,ProRuntime.class)))return;
            RecoveryTasks.constructed(entity,((Access.EntityState)entity).pro$level());
            Object[][] fields=producerFields==null?new Object[0][]:(Object[][])producerFields.invoke(null);
            // Capturing real constructor inputs is bounded and thread-safe. Authority is established
            // only later on the server's actual object binding; workers do not create a write permit.
            ProRuntime current=((Access.EntityState)entity).pro$level() instanceof ServerLevel level?get(level.getServer()):null;
            if(current!=null&&!current.closing)current.recoveryRecords.creationFields(entity,fields);
            for(ProRuntime runtime:sourceRuntimes())if(!runtime.closing) {
                UUID subject=runtime.recoveryTasks.subjectOf(entity);
                if(subject!=null)runtime.recoveryRecords.consumedFields(subject,fields);
            }
        } catch(ReflectiveOperationException|LinkageError unavailable) { /* No asserted result becomes an observation. */ }
    }
    public static void constructionFailed(Object error) {
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            if(Boolean.TRUE.equals(bridge.getMethod("deliveringCreationTo",Class.class).invoke(null,ProRuntime.class)))RecoveryTasks.constructionFailed();
        } catch(ReflectiveOperationException|LinkageError unavailable) { }
    }
    boolean resourcesClosing() {return closing;}
    boolean terminalSource(UUID id) {
        Subject subject=subjects.get(id);
        return !closing&&subject!=null&&subject.terminal;
    }
    public static void saved(Entity e,CompoundTag tag) {
        ProRuntime r=runtime(e);if(tag==null)return;
        if(r==null) {
            for(ProRuntime candidate:sourceRuntimes()) {
                UUID subject=candidate.recoveryTasks.subjectOf(e);
                if(subject!=null)candidate.recoverySources.observed(tag,subject);
            }
            return;
        }
        if(r.saved.size()>=CAPACITY) { r.coverageGap=true; return; }
        try {
            Binding binding=r.bind(e);r.saved.put(tag,binding.subject.id);r.recoverySources.observed(tag,binding.subject.id);
        }
        catch(IllegalArgumentException ignored) { }
        catch(RuntimeException unavailable) { r.coverageGap=true; }
    }
    public static void copied(CompoundTag from,CompoundTag to) {
        RecoverySources.copied(from,to);
        synchronized(SERVERS) {
            for(ProRuntime r:SERVERS.values()) if(r.server.isSameThread()) {
                UUID subject=r.saved.get(from);
                if(subject!=null && r.saved.size()<CAPACITY) r.saved.put(to,subject);
                else if(subject!=null)r.coverageGap=true;
            }
        }
    }
    public static void loaded(Entity e,CompoundTag tag) {
        ProRuntime r=runtime(e); if(r==null) { RecoverySources.loadedOffThread(e,tag);return; }
        // The source observer owns ambiguity handling; an older local tag alias must not
        // bypass a conflicting-origin decision made by that observer.
        UUID parent=RecoverySources.subject(r.recoverySources,tag);
        if(parent!=null)r.linkLoaded(e,parent);
    }
    void linkLoaded(Entity e,UUID subject) {
        thread();
        if(!(((Access.EntityState)e).pro$level() instanceof ServerLevel level)||level.getServer()!=server) {
            coverageGap=true;return;
        }
        Binding parent=null;
        for(Binding known:bindings.values())if(known.subject.id.equals(subject)) { parent=known;break; }
        Subject authority=subjects.get(subject);
        if(authority==null) { coverageGap=true;return; }
        if(parent!=null&&parent.entity()==e)return;
        Binding prior=bindings.get(e);
        if(prior!=null && prior.subject!=authority) { coverageGap=true; return; }
        if(prior==null) {
            if(bindings.size()>=CAPACITY) { coverageGap=true;return; }
            try {
                Binding child=new Binding(e,authority,new EntityRecords((ServerLevel)((Access.EntityState)e).pro$level(),e));
                child.predecessor=parent==null?recoveryChain.operationFor(subject):parent.latest==null?null:parent.latest.id;
                bindings.put(e,child);
                if(!BackingPolicy.capture(child.records,e))coverageGap=true;
                publishProtection(this,child,e);
                recoveryTasks.observed(e,authority.id);
            } catch(RuntimeException ex) { coverageGap=true;return; }
        }
        authority.sourceRevision.incrementAndGet();
        for(Work w:work.values()) if(w.binding!=null && w.binding.subject==authority) invalidate(w,"NEW_SOURCE_BINDING");
    }
    List<String> recoveryBodyGaps(UUID operation,UUID subject) {
        List<String> remaining=new ArrayList<>();
        if(coverageGap)remaining.add("ENTITY_SOURCE_CAPTURE_GAP");
        Subject authority=subjects.get(subject);if(authority!=null)remaining.addAll(authority.unresolvedSources);
        for(Work item:work.values())if(item.binding!=null&&item.binding.subject.id.equals(subject)&&item.action.equals("CLEAR")) {
            if(item.state.equals("FENCED")&&item.reason.equals("INCARNATION_REVIVED"))continue;
            Status state=query(item.id);
            if(!state.complete()||!state.receiptDurable())remaining.add("BODY:"+item.id+":"+state.state());
            if(!item.referencesReleased||item.resourceAck==null||!item.resourceAck.isDone()||item.resourceAck.isCompletedExceptionally())
                remaining.add("BODY_OWNED_REFERENCES_PENDING:"+item.id);
        }
        return remaining;
    }
    private void retireProtectionSnapshots(Subject subject) {
        Set<EntityRecords> inUse=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Work owner:work.values())if(owner.binding!=null&&owner.binding.subject==subject&&!owner.referencesReleased&&!owner.cancelled
                &&(!owner.action.equals("PROTECT")&&!owner.state.equals("FENCED")||owner.action.equals("PROTECT")&&current(owner)))inUse.add(owner.records);
        for(Work old:work.values())if(old.binding!=null&&old.binding.subject==subject&&old.action.equals("PROTECT")&&!current(old)) {
            fence(old,"QUALIFICATION_OR_BINDING_CHANGED");
            if(!inUse.contains(old.records)&&!(old.binding.protection&&old.binding.records==old.records)) {
                old.records.releaseOwnedReferences();old.referencesReleased=old.records.ownedReferencesReleased();
            }
        }
    }
    private void releaseBodyReferences(Work item) {
        if(item.binding==null||!item.action.equals("CLEAR"))return;
        if(item.referencesReleased) {
            if(!item.resourceRecord.startsWith("ROOT_RESOURCE/1\tRELEASED")) {
                item.resourceRecord="ROOT_RESOURCE/1\tRELEASED\t"+item.id+"\t"+session+"\tENTITY_AND_RELATION_OWNED_FIELDS_CLEARED";
                item.resourceAck=journal.append(item.resourceRecord);
            } else item.resourceAck=journal.retryRejected(item.resourceRecord,item.resourceAck);
            return;
        }
        if(!item.complete()||item.receiptAck==null||!item.receiptAck.isDone()||item.receiptAck.isCompletedExceptionally())return;
        if(item.resourceRecord==null) {
            item.resourceRecord="ROOT_RESOURCE/1\tINTENT\t"+item.id+"\t"+session+"\tOWNED_ENTITY_AND_RELATION_REFERENCES";
            item.resourceAck=journal.append(item.resourceRecord);return;
        }
        item.resourceAck=journal.retryRejected(item.resourceRecord,item.resourceAck);
        if(!item.resourceAck.isDone()||item.resourceAck.isCompletedExceptionally())return;
        try(var gate=item.binding.subject.commitGate.enter()) {
            if(!current(item)||!item.complete()||!item.records.bound()||!item.records.remaining().isEmpty())return;
            item.records.releaseOwnedReferences();item.binding.releaseOwnedReference();
            item.referencesReleased=item.records.ownedReferencesReleased();
        }
        // A later tick writes the release fact, after temporary Entity locals have left this frame.
    }
    /** Actual Forge shutdown starts before world unload; final resource/journal settlement stays in close(). */
    void beginShutdown() {lifecycleAction("stopping");thread();closing=true;}
    @Override public void close() throws IOException {
        lifecycleAction("stopped");
        closing=true;
        recoverySources.beginStop();
        for(Binding binding:bindings.values())try(var authorityGate=binding.subject.commitGate.enter()) { binding.subject.terminal=false; }
        for(Work w:work.values())fence(w,"RUNTIME_STOPPED_HISTORY_RETAINED");
        finishClose();
    }
    private synchronized void finishClose()throws IOException {
        // Stop new writes immediately, but keep the old read-only task observer until real exit receipts exist.
        IOException pending=null;
        try { recoveryStorage.close(); } catch(IOException failure) { pending=failure; }
        try { recoveryTasks.close(); } catch(IOException failure) { if(pending==null)pending=failure;else pending.addSuppressed(failure); }
        try { recoveryRecords.close(); } catch(IOException failure) { if(pending==null)pending=failure;else pending.addSuppressed(failure); }
        try { recoverySources.close(); } catch(IOException failure) { if(pending==null)pending=failure;else pending.addSuppressed(failure); }
        if(pending!=null)throw pending;
        journal.close();
        synchronized(SOURCE_OWNERS) { SOURCE_OWNERS.entrySet().removeIf(entry->entry.getValue()==this); }
        bindings.clear();saved.clear();work.clear();clientPolicies.clear();
    }
}
