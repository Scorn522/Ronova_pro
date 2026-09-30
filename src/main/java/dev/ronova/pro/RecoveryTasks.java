package dev.ronova.pro;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Worker side only captures bounded identities. The server thread owns task qualification and disposal. */
final class RecoveryTasks implements AutoCloseable {
    private static final Set<RecoveryTasks> LISTENERS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Consumer<Object[]> SINK=RecoveryTasks::receive;
    private static Method currentTask,currentExecution,delivery,coverage,observerMatches,droppedEvents,uninstall,boundarySnapshot,withBoundaries,pendingDeliveries,inlineDetails,inlineClosed,
            fenceTask,admissionState,releaseFence,requestOwnedQueueRelease,cancelOwnedQueueRelease,wakeOwnedQueue,waiterCount,waiterNodeObserved,activeInvocations,sourceOrigin,invocationState,submissionClosed,helperReferences;
    private static final String PREFIX="TASK/6";
    private static final Set<String> CF_COMPLETIONS=Set.of("UniApply","UniAccept","UniRun","UniWhenComplete","UniHandle",
            "UniExceptionally","UniComposeExceptionally","UniCompose","UniRelay","BiApply","BiAccept","BiRun",
            "BiRelay","OrApply","OrAccept","OrRun","CoCompletion","AsyncSupply","AsyncRun","AnyOf");
    private static Class<?> bridge;
    private static volatile String backend="TASK_AGENT_NOT_ATTACHED";
    private static final int LIMIT=8192, DRAIN=256, STABLE_TICKS=20, MAX_SAMPLE_GAP=10;
    /** Definition-local metadata only; actual field values, owners and policy are still read on every event. */
    private static final ClassValue<Field[]> CAPTURE_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            var result=new ArrayList<Field>();
            for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass())
                for(Field field:current.getDeclaredFields()) {
                    // Preserve the original declared-slot budget, including non-reference
                    // slots, while avoiding repeated reflection on those slots in hot callbacks.
                    // Null is metadata for an irrelevant slot, never a cached field value.
                    if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())field=null;
                    else try {field.trySetAccessible();}catch(RuntimeException unavailable) { /* Retry at the real observation. */ }
                    result.add(field);if(result.size()==96)return result.toArray(Field[]::new);
                }
            return result.toArray(Field[]::new);
        }
    };
    private final ProRuntime runtime;
    private final ArrayBlockingQueue<Capture> events=new ArrayBlockingQueue<>(LIMIT);
    private final Map<Object,UUID> origins=Collections.synchronizedMap(new WeakIdentityMap<>());
    private final Map<Object,Task> tasks=new IdentityHashMap<>();
    private final Map<Object,Set<Task>> producers=new IdentityHashMap<>();
    private final Map<Object,Set<Task>> dependents=new IdentityHashMap<>();
    private final Map<Object,Set<Task>> childrenByParent=new IdentityHashMap<>();
    private static final int EDGE_LIMIT=32768;
    private int indexEdges,taskEdges;
    private final Map<Object,Set<UUID>> workerBindings=Collections.synchronizedMap(new WeakIdentityMap<>());
    private final Map<Object,UUID> retiredObjects=new WeakIdentityMap<>();
    private record CloneLink(java.lang.ref.WeakReference<net.minecraft.world.entity.Entity> result,java.lang.ref.WeakReference<Class<?>> holder) { }
    private final Map<Object,List<CloneLink>> clones=new WeakIdentityMap<>();
    private final Map<UUID,Settlement> settlements=new LinkedHashMap<>();
    private record PriorTask(UUID id,UUID operation,UUID session) { }
    private final Map<UUID,PriorTask> priorTasks=new LinkedHashMap<>();
    private final Set<UUID> priorReleased=new HashSet<>(),priorObserversReleased=new HashSet<>();
    private final Map<UUID,CompletableFuture<Void>> priorProcessFacts=new HashMap<>();
    private static final class Settlement {
        final UUID task,operation,subject;
        final long generation;
        final String[] boundaries;
        final long backendEpoch;
        String record;
        CompletableFuture<Void> ack;
        boolean released;
        Settlement(Task task) {
            this.task=task.id;operation=task.operation;subject=task.authority.id;generation=task.qualification;
            boundaries=task.requiredBoundaries.clone();backendEpoch=task.backendEpoch;
        }
    }
    private final AtomicLong changes=new AtomicLong();
    // Accessed only under LISTENERS, the same short publication gate used by receive().
    private final Map<UUID,Long> captureEpochs=new HashMap<>();
    private volatile String gap="";
    private volatile boolean closing;
    private volatile boolean detached;
    private String retirementIntent,retirementFact;
    private CompletableFuture<Void> retirementAck,retirementFactAck;
    private long retirementEpoch=-1;
    // Admit connected work together. Large components receive a bounded larger share of sampling,
    // rather than expiring every child receipt while a parent holds the only active lease.
    private static final int WINDOW_TARGET=128, SAMPLE_BUDGET=32, MAX_SAMPLE_BUDGET=1024, LEASE_TICKS=96;
    private long leaseStarted=-1;
    private final ArrayDeque<Task> windows=new ArrayDeque<>();
    private final ArrayDeque<Task> waiting=new ArrayDeque<>();
    private record Capture(int kind,Object task,Object scheduler,Object parent,Object invocation,
            ClassLoader loader,Set<UUID> subjects,long dropped,Object dependency,Object otherDependency,Object output,
            boolean inline,Class<?> callbackDefinition,boolean hasExecutor) { }
    private static final class Task {
        final UUID id=UUID.randomUUID();
        final Object object;
        final Class<?> definition;
        final ClassLoader loader;
        final boolean inline;
        final boolean completion;
        final boolean forkJoin;
        final boolean result;
        final Set<Object> waiters=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean waiterCoverageGap;
        boolean delegated,dispatchObserved,dispatchExitObserved;
        final Map<Object,Boolean> adoptedInvocations=new IdentityHashMap<>();
        final Set<Object> delegateOwners=Collections.newSetFromMap(new IdentityHashMap<>());
        long waiterExits,waiterExitsRecorded;
        String waiterFactRecord;
        CompletableFuture<Void> waiterFactAck;
        final Class<?> callbackDefinition;
        final Set<Object> completionSources=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean creationObserved,asynchronousCompletion,completionModeObserved;
        boolean admissionFenced,admissionReleased,blockedEntryObserved,callbackFieldsReleased;
        /** The current policy already decided about this task object; the engine fence is asked once. */
        boolean policyFenced;
        boolean dependentResultPending;
        boolean ownedResult,resultCancellationAttempted;
        String resultCancelRecord;
        CompletableFuture<Void> resultCancelAck;
        final Set<Object> completionOutputs=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Field,Object> forkCaptureEdges=new LinkedHashMap<>();
        boolean forkCaptureBound;
        String preclusionRecord;
        CompletableFuture<Void> preclusionAck;
        final Map<String,CompletableFuture<Void>> queueRecords=new LinkedHashMap<>();
        long nextQueueReceipt,queueReceiptsDurable;
        final Map<Object,Object> ownedQueueRequests=new IdentityHashMap<>();
        final Map<Object,Long> ownedQueueEpochs=new IdentityHashMap<>();
        final Set<Object> servicedOwnerQueues=Collections.newSetFromMap(new IdentityHashMap<>());
        int ownerQueueCursor;
        String admissionRecord,callbackReleaseRecord,fenceReleaseRecord;
        CompletableFuture<Void> admissionAck,callbackReleaseAck,fenceReleaseAck;
        final Set<UUID> subjects=new HashSet<>();
        final Set<Object> executions=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> dispatches=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> workerReferences=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Object> submissions=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Task> children=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Task> parents=Collections.newSetFromMap(new IdentityHashMap<>());
        Object scheduler,queue;
        ProRuntime.Subject authority;
        UUID operation;
        long qualification,retryAt,revision,lastMutation,settledRevision=-1;
        long stableSince=-1,lastSample=-1,windowRevision=-1,windowCaptureEpoch=-1,settledCaptureEpoch=-1,settledWindowStart=-1;
        long backendEpoch=-1,windowBackendEpoch=-1,settledBackendEpoch=-1;
        boolean coverageConfirmed;
        boolean callableReleased;
        String[] requiredBoundaries=new String[0];
        String state="OBSERVED",reason="",gateReason="",intent,releaseIntent,factRecord;
        CompletableFuture<Void> ack,releaseAck,fact;
        String localRecord;
        CompletableFuture<Void> localAck;
        long localRevision=-1,localCaptureEpoch=-1,localWindowStart=-1,localBackendEpoch=-1;
        boolean entered,body,cancelAccepted,queueRemoved,exitObserved;
        boolean queued;
        String retirementRecord;
        CompletableFuture<Void> retirementAck;
        long retiredRevision=-1;
        Task(Object object,ClassLoader loader,boolean inline,Class<?> callbackDefinition) {
            this.object=object;definition=object.getClass();this.loader=loader;this.inline=inline;this.callbackDefinition=callbackDefinition;
            completion=exactCompletion(object);
            forkJoin=object instanceof ForkJoinTask<?>;
            result=standardCompletionSource(object);
        }
    }
    RecoveryTasks(ProRuntime runtime) {
        this.runtime=runtime;
        synchronized(LISTENERS) {
            attach();LISTENERS.add(this);
        }
        for(String line:runtime.journal.history())if(line.startsWith("TASK/")) {
            if(!line.startsWith("TASK/1\t")&&!line.startsWith("TASK/2\t")&&!line.startsWith("TASK/3\t")&&!line.startsWith("TASK/4\t")&&!line.startsWith("TASK/5\t")&&!line.startsWith(PREFIX+"\t"))runtime.journal.refuseWrites("FUTURE_TASK_DESCRIPTOR_QUERY_ONLY");
            if(!line.startsWith("TASK/1\t")&&!validHistory(line))runtime.journal.refuseWrites("INVALID_TASK_DESCRIPTOR_QUERY_ONLY");
            try {
                String[] row=line.split("\t",-1);
                if(row[1].equals("INTENT")&&row.length==10)
                    priorTasks.put(UUID.fromString(row[2]),new PriorTask(UUID.fromString(row[2]),UUID.fromString(row[3]),UUID.fromString(row[6])));
                else if(row[1].equals("CORE_RELEASED"))priorReleased.add(UUID.fromString(row[2]));
                else if(row[1].equals("OBSERVER_RELEASED"))priorObserversReleased.add(UUID.fromString(row[2]));
                else if(row[0].equals("TASK/1"))gap="LEGACY_TASK_OBLIGATIONS_QUERY_ONLY";
            } catch(RuntimeException invalid) { runtime.journal.refuseWrites("INVALID_TASK_HISTORY_QUERY_ONLY"); }
        }
    }
    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String intent(Task task) {
        return PREFIX+"\tINTENT\t"+task.id+"\t"+task.operation+"\t"+task.authority.id+"\t"+task.qualification+
                "\t"+runtime.session+"\t"+(task.inline?"CF_STAGE_INVOCATION":task.result?"CF_RESULT_OBSERVATION":task.completion?"CF_COMPLETION_OBSERVATION":task.forkJoin?"FORKJOIN_ADMISSION":task.delegated&&!exactFuture(task.object)?"DELEGATED_INVOCATION":"EXACT_FUTURE")+"\t"+encode(task.definition.getName())+
                "\t"+encode(task.callbackDefinition==null?"":task.callbackDefinition.getName());
    }
    private static boolean validHistory(String line) {
        String[] row=line.split("\t",-1);
        try {
            if(row.length<4)return false;
            boolean retirement=row[0].equals(PREFIX)||row[0].equals("TASK/5");
            boolean modern=retirement||row[0].equals("TASK/4");
            UUID.fromString(row[2]);
            return switch(row[1]) {
                case "INTENT" -> {
                    if(row.length!=10||!Set.of("CF_STAGE_INVOCATION","CF_RESULT_OBSERVATION","DELEGATED_INVOCATION","CF_COMPLETION_OBSERVATION","EXACT_FUTURE","FORKJOIN_ADMISSION").contains(row[7]))yield false;
                    if(row[0].equals("TASK/2")&&row[7].equals("FORKJOIN_ADMISSION"))yield false;
                    if(!modern&&row[7].equals("CF_RESULT_OBSERVATION"))yield false;
                    if(!modern&&row[7].equals("DELEGATED_INVOCATION"))yield false;
                    UUID.fromString(row[3]);UUID.fromString(row[4]);UUID.fromString(row[6]);
                    if(Long.parseLong(row[5])<0)yield false;
                    boolean valid=true;
                    for(int i=8;i<10;i++)valid&=encode(new String(Base64.getUrlDecoder().decode(row[i]),java.nio.charset.StandardCharsets.UTF_8)).equals(row[i]);
                    yield valid;
                }
                case "FACT" -> row.length==9;
                case "LOCAL_FACT" -> modern&&row.length==9;
                case "RESOURCE_RELEASE" -> row.length==5;
                case "CAPTURE_RELEASE_OBSERVED","RETIREMENT_OBSERVED" -> row.length==6;
                case "ADMISSION_FENCED","START_PRECLUDED","CALLBACK_FIELDS_RELEASED","FENCE_RELEASE_INTENT","FENCE_RELEASED","QUEUE_REFERENCE_REMOVED" -> !row[0].equals("TASK/2")&&row.length==6;
                case "OWNED_QUEUE_ABSENCE_OBSERVED","OWNED_QUEUE_REQUEST_RETIRED","WAITER_EXITS_OBSERVED","RESULT_WAITERS_RELEASE_OBSERVED" -> modern&&row.length==6;
                case "CORE_RELEASE_INTENT","CORE_RELEASED","REOBSERVED","PRIOR_PROCESS_ENDED","RESULT_CANCEL_INTENT","RESULT_CANCELLED" -> retirement&&row.length==6;
                case "OBSERVER_RELEASE_INTENT" -> row.length==5;
                case "OBSERVER_RELEASED" -> row.length==4;
                default -> false;
            };
        } catch(IllegalArgumentException malformed) { return false; }
    }
    private static void attach() {
        if(bridge!=null)return;
        try {
            bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
            if(bridge.getClassLoader()!=null)throw new IllegalStateException("NON_BOOTSTRAP_TASK_BRIDGE");
            if(!Integer.valueOf(25).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("TASK_BRIDGE_ABI_MISMATCH");
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            if(!Boolean.TRUE.equals(agent.getMethod("openTaskAccess",Class.class).invoke(null,RecoveryTasks.class)))
                throw new IllegalStateException("TASK_ACCESS_UNAVAILABLE");
            currentTask=bridge.getMethod("currentTask");currentExecution=bridge.getMethod("currentExecution");
            delivery=bridge.getMethod("deliveringTo",Object.class);coverage=bridge.getMethod("coverage");
            observerMatches=bridge.getMethod("observerMatches",Object.class);
            droppedEvents=bridge.getMethod("droppedEvents");uninstall=bridge.getMethod("uninstall",Object.class);
            boundarySnapshot=bridge.getMethod("boundarySnapshot",String[].class);
            withBoundaries=bridge.getMethod("withBoundaries",String[].class,long.class,Runnable.class);
            pendingDeliveries=bridge.getMethod("pendingDeliveries");
            inlineDetails=bridge.getMethod("inlineDetails",Object.class);inlineClosed=bridge.getMethod("inlineClosed",Object.class);
            fenceTask=bridge.getMethod("fenceTask",Object.class,Object.class);admissionState=bridge.getMethod("admissionState",Object.class,Object.class);
            releaseFence=bridge.getMethod("releaseFence",Object.class,Object.class);
            requestOwnedQueueRelease=bridge.getMethod("requestOwnedQueueRelease",Object.class,Object.class,Object.class,java.util.function.Predicate.class);
            cancelOwnedQueueRelease=bridge.getMethod("cancelOwnedQueueRelease",Object.class,Object.class);
            wakeOwnedQueue=bridge.getMethod("wakeOwnedQueue",Object.class,Object.class);
            waiterCount=bridge.getMethod("waiterCount",Object.class);waiterNodeObserved=bridge.getMethod("waiterNodeObserved",Object.class,Object.class);
            activeInvocations=bridge.getMethod("activeInvocations",Object.class);
            helperReferences=bridge.getMethod("helperReferences",Object.class);
            submissionClosed=bridge.getMethod("submissionClosed",Object.class,Object.class);
            sourceOrigin=bridge.getMethod("sourceOrigin",Object.class,Object.class,Object.class);invocationState=bridge.getMethod("invocationState",Object.class);
            bridge.getMethod("install",Consumer.class).invoke(null,SINK);
            backend=String.valueOf(coverage.invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            bridge=null;backend="TASK_AGENT_UNAVAILABLE:"+unavailable.getClass().getSimpleName();
        }
    }
    private static void receive(Object[] row) {
        synchronized(LISTENERS) {
            try {
                if(row==null||row.length!=8||delivery==null||!Boolean.TRUE.equals(delivery.invoke(null,SINK)))return;
                for(RecoveryTasks owner:LISTENERS)owner.capture(row);
            } catch(ReflectiveOperationException|RuntimeException unavailable) {
                for(RecoveryTasks owner:LISTENERS)owner.recordGap("TASK_CAPTURE_FAILED");
            }
        }
    }
    void observed(Object object,UUID subject) {
        Objects.requireNonNull(object);Objects.requireNonNull(subject);
        if(detached)return;
        boolean first;
        synchronized(origins) {
            UUID prior=origins.get(object);
            if(prior!=null&&!prior.equals(subject)) {
                recordGap("CONFLICTING_TASK_ORIGIN");return;
            }
            origins.put(object,subject);
            first=prior==null;
        }
        if(first)synchronized(LISTENERS) {
            for(CloneLink link:clones.getOrDefault(object,List.of())) {
                var clone=link.result.get();if(clone!=null)runtime.recoverySources.cloned(clone,subject,link.holder.get());
            }
        }
        // Real save/load activity, not a guessed wrapper field, supplies this causal relation.
        try { if(sourceOrigin!=null)sourceOrigin.invoke(null,SINK,this,subject); }
        catch(ReflectiveOperationException failure) { recordGap("EXECUTING_SOURCE_BINDING_UNAVAILABLE"); }
    }
    UUID subjectOf(Object object) { return origins.get(object); }
    private void recordGap(String reason) {
        if(!reason.equals(gap)) { gap=reason;changes.incrementAndGet(); }
    }
    private void checkBridge() {
        try {
            if(coverage!=null)backend=String.valueOf(coverage.invoke(null));
            if(observerMatches==null||!Boolean.TRUE.equals(observerMatches.invoke(null,SINK))) {
                recordGap("TASK_OBSERVER_NOT_OWNED");return;
            }
            // Poll even when no later relevant event exists to carry the loss counter.
            if(droppedEvents!=null&&((Long)droppedEvents.invoke(null))!=0L)recordGap("BOOTSTRAP_CAPTURE_LOSS");
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            recordGap("TASK_BRIDGE_COVERAGE_UNAVAILABLE");
        }
    }
    private long captureEpoch(Task task) {
        return task.authority==null?-1:captureEpochs.getOrDefault(task.authority.id,0L);
    }
    private static final List<String> CF_BOUNDARIES=List.of(cfBoundaries());
    private static final String[] FORK_BOUNDARIES={"java/util/concurrent/ForkJoinTask","java/util/concurrent/ForkJoinTask$Aux",
            "java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue","java/util/concurrent/ForkJoinWorkerThread"};
    private static final String[] DELEGATE_BOUNDARIES={"java/util/concurrent/FutureTask","java/util/concurrent/Executors$RunnableAdapter",
            "java/util/concurrent/ForkJoinTask","java/util/concurrent/ForkJoinTask$AdaptedRunnable",
            "java/util/concurrent/ForkJoinTask$AdaptedRunnableAction","java/util/concurrent/ForkJoinTask$AdaptedCallable",
            "java/util/concurrent/ForkJoinTask$RunnableExecuteAction"};
    private static final String[] EXECUTOR_BOUNDARIES={"java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ThreadPoolExecutor$Worker",
            "java/util/concurrent/locks/ReentrantLock","java/util/concurrent/locks/ReentrantLock$Sync",
            "java/util/concurrent/locks/ReentrantLock$FairSync","java/util/concurrent/locks/ReentrantLock$NonfairSync",
            "java/util/concurrent/locks/AbstractQueuedSynchronizer","java/util/concurrent/locks/AbstractOwnableSynchronizer",
            "java/util/concurrent/locks/AbstractQueuedSynchronizer$ConditionObject"};
    private static final String[] FUTURE_BOUNDARIES={"java/util/concurrent/FutureTask","java/util/concurrent/FutureTask$WaitNode",
            "java/util/concurrent/Executors$RunnableAdapter","java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy"};
    // Names only. Each observation gets a fresh array; no task can mutate another task's requirements.
    private static final Map<String,List<String>> CF_EXECUTOR_BOUNDARIES=queueBoundaries(CF_BOUNDARIES.toArray(String[]::new),false);
    private static final Map<String,List<String>> FUTURE_QUEUE_BOUNDARIES=queueBoundaries(FUTURE_BOUNDARIES,false);
    private static final Map<String,List<String>> SCHEDULED_QUEUE_BOUNDARIES=queueBoundaries(FUTURE_BOUNDARIES,true);
    private static String[] cfBoundaries() {
        var required=new TreeSet<String>();required.add("java/util/concurrent/CompletableFuture");
        for(String name:CF_COMPLETIONS)required.add("java/util/concurrent/CompletableFuture$"+name);
        Collections.addAll(required,"java/util/concurrent/CompletableFuture$Completion","java/util/concurrent/CompletableFuture$UniCompletion",
                "java/util/concurrent/CompletableFuture$BiCompletion","java/util/concurrent/CompletableFuture$MinimalStage","java/util/concurrent/ForkJoinTask",
                "java/util/concurrent/CompletableFuture$AltResult","java/util/concurrent/CompletableFuture$Timeout",
                "java/util/concurrent/CompletableFuture$DelayedCompleter","java/util/concurrent/CompletableFuture$TaskSubmitter",
                "java/util/concurrent/CompletableFuture$DelayedExecutor","java/util/concurrent/CompletableFuture$Canceller",
                "java/util/concurrent/ForkJoinTask$Aux","java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue",
                "java/util/concurrent/ForkJoinWorkerThread","java/util/concurrent/CompletableFuture$Signaller");
        return required.toArray(String[]::new);
    }
    private static Map<String,List<String>> queueBoundaries(String[] base,boolean scheduled) {
        var result=new HashMap<String,List<String>>();
        for(String queue:List.of("java.util.concurrent.ArrayBlockingQueue","java.util.concurrent.LinkedBlockingQueue",
                "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue")) {
            var names=new TreeSet<String>();Collections.addAll(names,base);Collections.addAll(names,EXECUTOR_BOUNDARIES);
            names.add(queue.replace('.','/'));
            if(queue.equals("java.util.concurrent.LinkedBlockingQueue"))Collections.addAll(names,
                    "java/util/concurrent/LinkedBlockingQueue$Node","java/util/concurrent/atomic/AtomicInteger");
            if(scheduled)Collections.addAll(names,"java/util/concurrent/ScheduledThreadPoolExecutor",
                    "java/util/concurrent/ScheduledThreadPoolExecutor$ScheduledFutureTask");
            result.put(queue,List.copyOf(names));
        }
        return Map.copyOf(result);
    }
    private boolean observeBackend(Task task)throws ReflectiveOperationException {
        task.coverageConfirmed=false;
        if(task.inline||task.completion||task.result) {
            if(boundarySnapshot==null||inlineClosed==null)return false;
            if(task.scheduler instanceof ThreadPoolExecutor) {
                Object queue=queue(task.scheduler);if(queue==null||!supportedQueue(queue))return false;
                task.requiredBoundaries=CF_EXECUTOR_BOUNDARIES.get(queue.getClass().getName()).toArray(String[]::new);
            } else task.requiredBoundaries=CF_BOUNDARIES.toArray(String[]::new);
        } else if(task.forkJoin) {
            if(boundarySnapshot==null||fenceTask==null||task.definition.getMethod("cancel",boolean.class).getDeclaringClass()!=ForkJoinTask.class)return false;
            task.requiredBoundaries=FORK_BOUNDARIES.clone();
        } else if(task.delegated&&!exactFuture(task.object)) {
            if(boundarySnapshot==null)return false;
            task.requiredBoundaries=DELEGATE_BOUNDARIES.clone();
        } else {
            if(boundarySnapshot==null||!exactFuture(task.object))return false;
            Object queue=queue(task.scheduler);if(queue==null||!supportedQueue(queue))return false;
            boolean scheduled=task.object.getClass()!=FutureTask.class;
            if(scheduled&&(task.scheduler==null||task.scheduler.getClass()!=ScheduledThreadPoolExecutor.class))return false;
            task.requiredBoundaries=(scheduled?SCHEDULED_QUEUE_BOUNDARIES:FUTURE_QUEUE_BOUNDARIES).get(queue.getClass().getName()).toArray(String[]::new);
        }
        long[] state=(long[])boundarySnapshot.invoke(null,(Object)task.requiredBoundaries);
        return boundaryState(task,state);
    }
    private static boolean boundaryState(Task task,long[] state) {
        if(task.backendEpoch!=state[0])task.servicedOwnerQueues.clear();
        task.backendEpoch=state[0];task.coverageConfirmed=state[1]==1;return task.coverageConfirmed;
    }
    private boolean guarded(Task task,Runnable action)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            checkBridge();
            task.gateReason="";
            if(closing)return gateFailure(task,"RUNTIME_CLOSING");
            if(!runtime.journal.healthy())return gateFailure(task,"DURABLE_WRITER_NOT_HEALTHY");
            if(!gap.isEmpty())return gateFailure(task,"CAPTURE_GAP:"+gap);
            if(!observeBackend(task))return gateFailure(task,"REQUIRED_BOUNDARY_UNAVAILABLE");
            if(!events.isEmpty()||pendingDeliveries==null||((Integer)pendingDeliveries.invoke(null))!=0)return gateFailure(task,"CAPTURE_PUBLICATION_OR_MERGE_PENDING");
            Set<UUID> published=workerBindings.get(task.object);
            if(task.authority==null||published==null||published.size()!=1||!published.contains(task.authority.id))return gateFailure(task,"PUBLISHED_TASK_OWNERSHIP_CHANGED");
            for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task.object))
                return gateFailure(task,"TASK_SHARED_ACROSS_RUNTIME_OWNERS");
            if(!task.authority.commitGate.tryLock())return gateFailure(task,"QUALIFICATION_GATE_BUSY");
            try {
                if(!task.authority.terminal||task.authority.generation!=task.qualification)return gateFailure(task,"QUALIFICATION_CHANGED");
                if(!Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,action)))return gateFailure(task,"BOUNDARY_CERTIFICATE_CHANGED");
                return true;
            } finally { task.authority.commitGate.unlock(); }
        }
    }
    private static boolean gateFailure(Task task,String reason) { task.gateReason=reason;return false; }
    private void capture(Object[] row) {
        // During shutdown keep observing accepted work and its late descendants, without granting writes.
        if(detached)return;
        Object object=row[1],parent=row[3];if(object==null)return;
        if((Integer)row[0]==25) {
            if(object instanceof net.minecraft.world.entity.Entity&&row[2] instanceof net.minecraft.world.entity.Entity clone) {
                Class<?> holder=row[4] instanceof Class<?> type?type:null;
                if(clones.size()>=LIMIT&&!clones.containsKey(object)) { recordGap("CLONE_PROVENANCE_CAPACITY");return; }
                var children=clones.computeIfAbsent(object,key->new ArrayList<>());
                children.removeIf(link->link.result.get()==null);
                if(children.size()>=512) { recordGap("CLONE_PROVENANCE_FANOUT_LIMIT");return; }
                children.add(new CloneLink(new java.lang.ref.WeakReference<>(clone),new java.lang.ref.WeakReference<>(holder)));
                UUID subject=origins.get(object);
                if(subject!=null)runtime.recoverySources.cloned(clone,subject,holder);
            }
            return;
        }
        if((Integer)row[0]==24&&(!(row[4] instanceof Object[] origin)||origin.length!=3||origin[0]!=this))return;
        // Before any real provenance exists there is no task relation to resolve. Bootstrap
        // still retains actual running frames; a later source event adopts them normally.
        if(origins.isEmpty()&&workerBindings.isEmpty()&&(Integer)row[0]!=24)return;
        if(internalDelegate(object))return;
        Object[] inline=null;
        try { if(inlineDetails!=null)inline=(Object[])inlineDetails.invoke(null,object); }
        catch(ReflectiveOperationException failure) { recordGap("INLINE_CAPTURE_UNAVAILABLE");return; }
        // Temporary subject IDs are not recovery sources. Avoid routing this bookkeeping
        // through the global HashMap writer observer for every ordinary JDK callback.
        Set<UUID> related=new TreeSet<>();
        if((Integer)row[0]==24&&row[2] instanceof UUID subject)related.add(subject);
        Set<UUID> existing=workerBindings.get(object),ancestor=workerBindings.get(parent);
        if(existing!=null)related.addAll(existing);
        if(ancestor!=null)related.addAll(ancestor);
        // Direct captured provenance is evidence of a reference, not evidence of delegation.
        // Only registered JDK wrapper edges may be traversed; third-party delegation is learned
        // at the actual call site or from real save/copy/load activity on its execution frame.
        captureSubjects(object,inline,related);
        if(related.isEmpty())return;
        synchronized(workerBindings) {
            if(!workerBindings.containsKey(object)&&workerBindings.size()>=LIMIT) { gap="TASK_CAPTURE_CAPACITY";changes.incrementAndGet();return; }
            workerBindings.put(object,Set.copyOf(related));
        }
        Object base=inline==null&&object.getClass().getClassLoader()==null
                &&object.getClass().getName().equals("java.util.concurrent.CompletableFuture$CoCompletion")?completionField(object,"base"):null;
        Object edge=base==null?object:base;
        Object dependency=inline==null?completionField(edge,"src"):inline[0];
        Object other=inline==null?completionField(edge,"snd"):inline[1],output=inline==null?completionField(edge,"dep"):inline[3];
        Object inputs=inline==null?completionField(object,"srcs"):null;
        if(inputs instanceof CompletableFuture<?>[] array) {
            if(array.length>512) { recordGap("CF_AGGREGATE_CAPTURE_BUDGET");return; }
            dependency=array.clone();
        }
        // A waiter consumes this result. It does not produce the result it is waiting for.
        if((Integer)row[0]>=18&&(Integer)row[0]<=20&&standardCompletionSource(object)) { dependency=object;output=null; }
        if(output!=null)synchronized(workerBindings) {
            Set<UUID> prior=workerBindings.get(output);Set<UUID> merged=new HashSet<>(related);if(prior!=null)merged.addAll(prior);
            if(workerBindings.size()<LIMIT||prior!=null)workerBindings.put(output,Set.copyOf(merged));else recordGap("CF_OUTPUT_CAPTURE_CAPACITY");
        }
        for(UUID subject:related) {
            long prior=captureEpochs.getOrDefault(subject,0L);
            if(prior==Long.MAX_VALUE)recordGap("TASK_CAPTURE_EPOCH_EXHAUSTED");
            else captureEpochs.put(subject,prior+1);
        }
        changes.incrementAndGet();
        Object invocation=(Integer)row[0]==24?((Object[])row[4])[1]:row[4];
        Object scheduler=(Integer)row[0]==24?((Object[])row[4])[2]:row[2];
        Capture event=new Capture((Integer)row[0],object,scheduler,parent,invocation,object.getClass().getClassLoader(),Set.copyOf(related),(Long)row[7],dependency,other,output,
                inline!=null,inline==null||inline[2]==null?null:inline[2].getClass(),completionField(object,"executor")!=null);
        if(!events.offer(event))gap="TASK_EVENT_OVERFLOW_OBLIGATIONS_RETAINED";
    }
    private boolean captureSubjects(Object object,Object[] inline,Set<UUID> related) {
        boolean complete=true;
        ArrayDeque<Object> pending=new ArrayDeque<>();
        if(inline==null)pending.add(object);else for(int i=0;i<3;i++)if(inline[i]!=null)pending.add(inline[i]);
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());int fields=0;
        while(!pending.isEmpty()&&seen.size()<32&&fields<96) {
            Object current=pending.removeFirst();if(!seen.add(current))continue;
            UUID origin=capturedOrigin(current);if(origin!=null) { related.add(origin);continue; }
            Set<UUID> known=workerBindings.get(current);if(known!=null)related.addAll(known);
            Class<?> type=current.getClass();
            if(type==CompletableFuture[].class) {
                Object[] inputs=(Object[])current;if(inputs.length>512) { complete=false;recordGap("CF_AGGREGATE_CAPTURE_BUDGET");continue; }
                for(Object input:inputs)if(input!=null)pending.addLast(input);
                continue;
            }
            Class<?> nest=type.getNestHost();
            if(nest==ProRuntime.class||nest==RecoveryStorage.class
                    ||nest==IntentJournal.class||nest==RecoveryTasks.class
                    ||nest==RecoverySources.class||nest==RecoveryRecords.class
                    ||nest==RecoveryReferences.class||nest==RecoveryChain.class)continue;
            if(type.isArray()||type.isEnum()||current instanceof Class<?>||current instanceof Thread||current instanceof Executor)continue;
            if(type.getName().startsWith("java.")&&!knownDelegate(type))continue;
            boolean delegate=knownDelegate(type);
            Field[] captureFields=CAPTURE_FIELDS.get(type);if(captureFields.length>=96)complete=false;
            for(Field field:captureFields) {
                if(++fields>96) { complete=false;break; }
                if(field==null)continue;
                try { if(field.trySetAccessible()) {
                    Object value=field.get(current);if(value==null)continue;
                    UUID direct=capturedOrigin(value);if(direct!=null)related.add(direct);
                    Set<UUID> bound=workerBindings.get(value);if(bound!=null)related.addAll(bound);
                    if(delegate&&jdkDelegateField(field.getDeclaringClass(),field))pending.addLast(value);
                } }
                catch(IllegalAccessException|RuntimeException unavailable) { /* No reference was proven. */ }
            }
        }
        if(!pending.isEmpty()) { complete=false;gap="TASK_REFERENCE_CAPTURE_BUDGET"; }
        return complete;
    }
    private UUID capturedOrigin(Object value) {
        UUID direct=origins.get(value);if(direct!=null)return direct;
        Object target=RecoveryReferences.wrapperTarget(value,runtime,true);
        return target instanceof net.minecraft.world.entity.Entity?origins.get(target):
                target instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):null;
    }
    /** Real Entity allocation inside an observed invocation, including a plain constructor with no NBT load. */
    public static void constructed(net.minecraft.world.entity.Entity entity,net.minecraft.world.level.Level constructionLevel) {
        if(currentTask==null||currentExecution==null)return;
        try {
            Object task=currentTask.invoke(null),invocation=currentExecution.invoke(null);
            Object[] producerInputs=ProRuntime.currentProducerInputs();
            if((task==null||invocation==null)&&producerInputs.length==0)return;
            synchronized(LISTENERS) {
                RecoveryTasks selected=null;UUID subject=null;
                for(RecoveryTasks owner:LISTENERS) {
                    if(owner.detached||owner.origins.isEmpty()&&owner.workerBindings.isEmpty())continue;
                    Object[] inline=task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task);
                    Set<UUID> related=new HashSet<>();
                    if(!owner.producerSubjects(task,inline,producerInputs,related)) {
                        for(UUID id:related) { var authority=owner.runtime.subjects.get(id);if(authority!=null)authority.unresolvedSources.add("CONSTRUCTOR_CAPTURE_INCOMPLETE"); }
                        owner.changes.incrementAndGet();return;
                    }
                    if(related.isEmpty())continue;
                    if(related.size()!=1||selected!=null) {
                        if(selected!=null) {
                            ProRuntime.Subject prior=selected.runtime.subjects.get(subject);
                            if(prior!=null)prior.unresolvedSources.add("MIXED_TASK_CONSTRUCTOR_OUTPUT");
                            selected.changes.incrementAndGet();
                        }
                        for(UUID id:related) {
                            ProRuntime.Subject authority=owner.runtime.subjects.get(id);
                            if(authority!=null)authority.unresolvedSources.add("MIXED_TASK_CONSTRUCTOR_OUTPUT");
                        }
                        owner.changes.incrementAndGet();return;
                    }
                    selected=owner;subject=related.iterator().next();
                }
                if(selected!=null&&constructionLevel instanceof net.minecraft.server.level.ServerLevel level
                        &&level.getServer()==selected.runtime.server)
                    selected.runtime.recoverySources.produced(entity,subject,null,"TASK_CONSTRUCTOR_BINDING_CAPACITY");
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            synchronized(LISTENERS) { for(RecoveryTasks owner:LISTENERS)owner.recordGap("CONSTRUCTOR_CAPTURE_UNAVAILABLE"); }
        }
    }
    static boolean deniesCurrentPublication(net.minecraft.world.entity.Entity entity) {
        if(currentTask==null||currentExecution==null)return false;
        try {
            Object task=currentTask.invoke(null);Object[] producerInputs=ProRuntime.currentProducerInputs();
            if((task==null||currentExecution.invoke(null)==null)&&producerInputs.length==0)return false;
            synchronized(LISTENERS) {
                RecoveryTasks selected=null;UUID id=null;
                for(RecoveryTasks owner:LISTENERS) {
                    if(owner.detached||owner.origins.isEmpty()&&owner.workerBindings.isEmpty())continue;
                    Object[] inline=task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task);
                    Set<UUID> related=new HashSet<>();if(!owner.producerSubjects(task,inline,producerInputs,related))return false;
                    if(related.isEmpty())continue;
                    if(related.size()!=1||selected!=null)return false;
                    selected=owner;id=related.iterator().next();
                }
                if(selected==null||!(((dev.ronova.pro.mixin.Access.EntityState)entity).pro$level() instanceof net.minecraft.server.level.ServerLevel realm)||realm.getServer()!=selected.runtime.server)return false;
                UUID existing=selected.subjectOf(entity);
                return (existing==null||existing.equals(id))&&selected.runtime.terminalSource(id);
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) { return false; }
    }
    private boolean producerSubjects(Object task,Object[] inline,Object[] inputs,Set<UUID> subjects) {
        boolean complete=task==null||captureSubjects(task,inline,subjects);
        Set<UUID> directSubjects=new HashSet<>(subjects);
        for(Object input:inputs)if(input instanceof ProRuntime.ProducerOrigin origin&&origin.runtime()==runtime&&origin.complete())
            directSubjects.add(origin.subject());
        for(Object input:inputs) {
            if(input==Boolean.FALSE)complete=false;
            if(input instanceof ProRuntime.ProducerOrigin origin&&origin.runtime()==runtime) {
                subjects.add(origin.subject());
                if(!origin.complete()&&!directSubjects.contains(origin.subject()))complete=false;
            }
        }
        return complete;
    }
    static void constructionFailed() {
        if(currentTask==null)return;
        try {
            Object task=currentTask.invoke(null);Object[] producerInputs=ProRuntime.currentProducerInputs();
            if(task==null&&producerInputs.length==0)return;
            synchronized(LISTENERS) { for(RecoveryTasks owner:LISTENERS) {
                Set<UUID> related=new HashSet<>();owner.producerSubjects(task,task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task),producerInputs,related);
                for(UUID id:related) {
                    var subject=owner.runtime.subjects.get(id);
                    if(subject!=null)subject.unresolvedSources.add("CONSTRUCTOR_SIDE_EFFECTS_UNKNOWN");
                }
                if(!related.isEmpty())owner.changes.incrementAndGet();
            } }
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            synchronized(LISTENERS) { for(RecoveryTasks owner:LISTENERS)owner.recordGap("CONSTRUCTOR_FAILURE_OBSERVER_UNAVAILABLE"); }
        }
    }
    /** Only an actual JDK delegate chain can identify our worker; a random field pointing at Core cannot. */
    private static boolean internalDelegate(Object object) {
        Object current=object;
        for(int depth=0;depth<4&&current!=null;depth++) {
            Class<?> type=current.getClass(),host=type.getNestHost();
            if((current instanceof Runnable||current instanceof Callable<?>)&&(host==ProRuntime.class||host==IntentJournal.class
                    ||host==RecoveryStorage.class||host==RecoveryTasks.class||host==RecoveryRecords.class||host==RecoveryReferences.class))return true;
            try {
                Field delegate;
                if(exactFuture(current))delegate=FutureTask.class.getDeclaredField("callable");
                else if(type.getClassLoader()==null&&type.getName().equals("java.util.concurrent.Executors$RunnableAdapter"))
                    delegate=type.getDeclaredField("task");
                else return false;
                if(!delegate.trySetAccessible())return false;
                current=delegate.get(current);
            } catch(ReflectiveOperationException|RuntimeException unavailable) { return false; }
        }
        return false;
    }
    private static Object completionField(Object object,String name) {
        Class<?> type=object.getClass();
        if(type.getClassLoader()!=null||!type.getName().startsWith("java.util.concurrent.CompletableFuture$"))return null;
        for(Class<?> c=type;c!=null&&c.getName().startsWith("java.util.concurrent.CompletableFuture$");c=c.getSuperclass()) {
            try { Field field=c.getDeclaredField(name);if(field.trySetAccessible())return field.get(object); }
            catch(NoSuchFieldException absent) { }
            catch(ReflectiveOperationException|RuntimeException unavailable) { return null; }
        }
        return null;
    }
    private static boolean knownDelegate(Class<?> type) {
        if(type.getClassLoader()!=null||!"java.base".equals(type.getModule().getName()))return false;
        String name=type.getName();
        return name.equals("java.util.concurrent.FutureTask")||name.equals("java.util.concurrent.Executors$RunnableAdapter")
                ||name.equals("java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask")
                ||name.startsWith("java.util.concurrent.ForkJoinTask$")||name.startsWith("java.util.concurrent.CompletableFuture$");
    }
    private static boolean jdkDelegateField(Class<?> declaring,Field field) {
        if(declaring.getClassLoader()!=null)return false;
        String name=declaring.getName();
        if(name.equals("java.util.concurrent.FutureTask"))return field.getName().equals("callable");
        if(name.equals("java.util.concurrent.Executors$RunnableAdapter"))return field.getName().equals("task");
        if(name.startsWith("java.util.concurrent.ForkJoinTask$"))return field.getName().equals("runnable")||field.getName().equals("callable");
        return name.startsWith("java.util.concurrent.CompletableFuture$")&&switch(field.getName()) {
            case "src","snd","dep","fn","base","srcs" -> true;
            default -> false;
        };
    }
    void tick(long tick) {
        synchronized(LISTENERS) { checkBridge(); }
        int historicalBudget=16;
        for(PriorTask prior:priorTasks.values())if(!priorReleased.contains(prior.id)&&historicalBudget-->0) {
            if(priorObserversReleased.contains(prior.session)||runtime.journal.processEnded(prior.session)) {
                String fact=PREFIX+"\tPRIOR_PROCESS_ENDED\t"+prior.id+"\t"+prior.operation+"\t"+prior.session+"\tRUNTIME_REFERENCES_ONLY_CALLBACK_EFFECTS_UNCHANGED";
                var ack=priorProcessFacts.get(prior.id);
                ack=ack==null?runtime.journal.append(fact):runtime.journal.retryRejected(fact,ack);
                priorProcessFacts.put(prior.id,ack);if(confirmed(ack))priorReleased.add(prior.id);
            }
        }
        drain(tick);
        if(!windows.isEmpty()&&(tick<leaseStarted||tick-leaseStarted>=LEASE_TICKS)) {
            if(waiting.isEmpty())leaseStarted=tick;else yieldWindow(tick);
        }
        // No task qualification or graph mutation occurs between these admission calls.
        Map<UUID,List<Task>> operations=new HashMap<>();
        for(Task task:tasks.values())if(task.operation!=null)operations.computeIfAbsent(task.operation,key->new ArrayList<>()).add(task);
        // Newly discovered children join the existing lease before its next observation.
        admitComponents(List.copyOf(windows),operations);
        for(int budget=0;budget<SAMPLE_BUDGET&&windows.size()<WINDOW_TARGET&&!waiting.isEmpty();budget++) {
            Task task=waiting.removeFirst();task.queued=false;
            if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification)continue;
            if(tick<task.retryAt) { enqueue(task);continue; }
            if(windows.isEmpty())leaseStarted=tick;
            admitComponents(List.of(task),operations);
        }
        int visits=Math.min(windows.size(),Math.min(MAX_SAMPLE_BUDGET,Math.max(SAMPLE_BUDGET,(windows.size()+7)/8)));
        for(int budget=0;budget<visits;budget++) {
            Task task=windows.removeFirst();windows.addLast(task);
            if(tick<task.retryAt)continue;
            task.retryAt=tick+4;
            try { advance(task,tick); }
            catch(ReflectiveOperationException|RuntimeException failure) { task.reason="TASK_DISPOSITION_UNRESOLVED:"+failure.getClass().getSimpleName()+":"+failure.getMessage(); }
            if(task.fact!=null&&task.fact.isCompletedExceptionally())resetWindow(task,"TASK_FACT_ACK_UNCONFIRMED");
        }
        settleScopes(tick);
        retireCoreReferences(tick);
        // Keep completed children sampled while any linked parent is building its current fact.
        boolean done=!windows.isEmpty(),progressing=false;
        synchronized(LISTENERS) {
            for(Task task:windows) {
                done&=currentFact(task,tick,captureEpoch(task));
                progressing|=task.stableSince>=0||task.ack!=null&&!task.ack.isDone()||task.releaseAck!=null&&!task.releaseAck.isDone();
            }
        }
        if(done&&!waiting.isEmpty()||!progressing&&!windows.isEmpty())yieldWindow(tick);
    }
    private void admitComponents(Collection<Task> roots,Map<UUID,List<Task>> operations) {
        var pending=new ArrayDeque<Task>();pending.addAll(roots);
        Set<Task> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Task> active=Collections.newSetFromMap(new IdentityHashMap<>());active.addAll(windows);
        Set<UUID> includedOperations=new HashSet<>();
        while(!pending.isEmpty()) {
            Task task=pending.removeFirst();if(!seen.add(task))continue;
            if(task.operation!=null&&includedOperations.add(task.operation))pending.addAll(operations.getOrDefault(task.operation,List.of()));
            if(task.authority!=null&&task.authority.terminal&&task.authority.generation==task.qualification&&active.add(task)) {
                waiting.remove(task);task.queued=false;task.retryAt=0;windows.addLast(task);
            }
            pending.addAll(task.children);pending.addAll(task.parents);
        }
    }
    private void yieldWindow(long tick) {
        while(!windows.isEmpty()) {
            Task task=windows.removeFirst();task.retryAt=tick+20;enqueue(task);
        }
        leaseStarted=-1;
    }
    private void enqueue(Task task) {
        if(!task.queued&&!windows.contains(task)) { task.queued=true;waiting.addLast(task); }
    }
    private void drain(long tick) {
        for(int budget=0;budget<DRAIN;budget++) {
            Capture event=events.poll();if(event==null)break;
            Task task=tasks.get(event.task);
            if(task==null) {
                if(tasks.size()>=LIMIT) { gap="TASK_ACTIVE_CAPACITY_OBLIGATION_NOT_SETTLED";continue; }
                task=new Task(event.task,event.loader,event.inline,event.callbackDefinition);tasks.put(event.task,task);
                UUID prior=retiredObjects.remove(event.task);
                if(prior!=null)runtime.journal.append(PREFIX+"\tREOBSERVED\t"+task.id+"\t"+prior+"\t"+runtime.session+"\tNEW_EXECUTION_OBLIGATION");
                for(Task child:childrenByParent.getOrDefault(event.task,Set.of()))link(task,child,tick);
            }
            if(task.loader!=event.loader||task.definition!=event.task.getClass()) { task.reason="DEFINITION_BINDING_CHANGED";continue; }
            if(task.inline!=event.inline||task.callbackDefinition!=event.callbackDefinition) { task.reason="CALLBACK_DEFINITION_BINDING_CHANGED";continue; }
            // The policy is read against the subjects this event actually brings, before they are merged, so a
            // task whose ownership is only now established is decided at this real entry instead of a later tick.
            policyGate(task,event,tick);
            task.subjects.addAll(event.subjects);
            changed(task,tick);
            if(task.authority!=null)enqueue(task);
            if(event.scheduler!=null&&event.kind!=13&&event.kind!=15&&event.kind!=16&&event.kind!=17&&event.kind!=21) {
                if(task.scheduler!=null&&task.scheduler!=event.scheduler)task.reason="MULTIPLE_SCHEDULER_OWNERS";
                else task.scheduler=event.scheduler;
            }
            Task parent=tasks.get(event.parent);
            if(event.parent!=null&&event.parent!=event.task&&index(childrenByParent,event.parent,task)&&parent!=null)link(parent,task,tick);
            for(Object dependency:dependencies(event.dependency,event.otherDependency)) {
                if(dependency==null)continue;
                if(task.completion)task.completionSources.add(dependency);
                if(!index(dependents,dependency,task))continue;
                for(Task producer:producers.getOrDefault(dependency,Set.of()))link(producer,task,tick);
            }
            if(event.output!=null) {
                if(task.completion)task.completionOutputs.add(event.output);
                if(index(producers,event.output,task)) {
                    for(Task dependent:dependents.getOrDefault(event.output,Set.of()))link(task,dependent,tick);
                }
                if(standardCompletionSource(event.output)) {
                    Task result=tasks.get(event.output);
                    if(result==null&&tasks.size()<LIMIT) {
                        result=new Task(event.output,event.output.getClass().getClassLoader(),false,null);tasks.put(event.output,result);
                    }
                    if(result!=null&&result.result) {
                        result.subjects.addAll(event.subjects);
                        result.ownedResult|=event.inline&&dependencies(event.dependency,event.otherDependency).stream().noneMatch(input->input==event.output);
                        link(task,result,tick);changed(result,tick);
                    } else if(result==null)recordGap("CF_RESULT_OBLIGATION_CAPACITY");
                }
            }
            switch(event.kind) {
                case 1 -> { task.entered=true;task.executions.add(event.invocation); }
                case 2 -> {
                    boolean entered=task.executions.remove(event.invocation);
                    boolean adopted=Boolean.FALSE.equals(task.adoptedInvocations.remove(event.invocation));
                    if(!entered&&!adopted)task.reason="UNMATCHED_EXECUTION_EXIT";else task.exitObserved=true;
                }
                case 5 -> { task.submissions.add(event.invocation);task.queueRemoved=false;task.servicedOwnerQueues.clear(); }
                case 6 -> {
                    if(!task.submissions.remove(event.invocation))try {
                        if(!Boolean.TRUE.equals(submissionClosed.invoke(null,event.invocation,task.object)))task.reason="UNMATCHED_SUBMISSION_EXIT";
                    } catch(ReflectiveOperationException unavailable) { task.reason="SUBMISSION_EXIT_QUERY_UNAVAILABLE"; }
                }
                case 7 -> { task.dispatchObserved=true;task.dispatches.add(event.invocation);task.workerReferences.add(event.invocation); }
                case 8 -> {
                    if(!task.dispatches.remove(event.invocation)&&!Boolean.TRUE.equals(task.adoptedInvocations.get(event.invocation)))
                        task.reason="UNMATCHED_DISPATCH_EXIT";
                    else task.dispatchExitObserved=true;
                }
                case 9 -> task.body=true;
                case 10 -> { /* A normal caller's cancel is not our disposal receipt. */ }
                case 12 -> {
                    boolean observed=task.workerReferences.remove(event.invocation);
                    boolean adopted=Boolean.TRUE.equals(task.adoptedInvocations.remove(event.invocation));
                    if(!observed&&!adopted)task.reason="UNMATCHED_WORKER_REFERENCE_RETIREMENT";
                }
                case 11 -> { task.creationObserved=true;task.asynchronousCompletion|=event.hasExecutor; }
                case 13 -> {
                    task.completionModeObserved=true;
                    if(!(event.scheduler instanceof Integer mode)||mode!=0&&mode!=-1)task.asynchronousCompletion=true;
                }
                case 14 -> task.blockedEntryObserved=true;
                case 15,17 -> {
                    if(task.ownedQueueRequests.get(event.scheduler)!=event.invocation) { task.reason="OWNER_QUEUE_RECEIPT_BINDING_MISMATCH";break; }
                    task.servicedOwnerQueues.add(event.scheduler);
                    if(event.kind==15)task.queueRemoved=true;
                    queueReceipt(task,event.kind==15?"QUEUE_REFERENCE_REMOVED":"OWNED_QUEUE_ABSENCE_OBSERVED",
                            event.kind==15?"ACTUAL_QUEUE_OWNER_REMOVED_EXACT_TASK":"AT_OWNER_SAFE_POINT_ONLY");
                }
                case 16 -> {
                    if(task.ownedQueueRequests.remove(event.scheduler,event.invocation)) {
                        task.ownedQueueEpochs.remove(event.scheduler);queueReceipt(task,"OWNED_QUEUE_REQUEST_RETIRED","BOOTSTRAP_REQUEST_REGISTRY_REFERENCE_REMOVED");
                    }
                    else task.reason="OWNER_QUEUE_RETIREMENT_BINDING_MISMATCH";
                }
                case 18 -> task.waiters.add(event.invocation);
                case 19,20 -> {
                    task.waiters.remove(event.invocation);
                    if(task.waiterExits==Long.MAX_VALUE)recordGap("WAITER_EXIT_SEQUENCE_EXHAUSTED");else task.waiterExits++;
                }
                case 21 -> { task.delegated=true;if(event.scheduler!=null)task.delegateOwners.add(event.scheduler); }
                case 24 -> {
                    try {
                        long[] state=(long[])invocationState.invoke(null,event.invocation);
                        if(state[0]<0) { task.reason="SOURCE_INVOCATION_TOKEN_INVALID";break; }
                        boolean dispatch=state[0]==1;task.adoptedInvocations.put(event.invocation,dispatch);
                        if(dispatch)task.dispatchObserved=true;else task.entered=true;
                    } catch(ReflectiveOperationException failure) { task.reason="SOURCE_INVOCATION_QUERY_UNAVAILABLE"; }
                }
                case 4 -> task.reason="TASK_BOUNDARY_DEPTH_OVERFLOW";
                default -> { }
            }
            if(event.dropped!=0)gap="BOOTSTRAP_CAPTURE_LOSS";
        }
    }
    /**
     * Current-policy connection for the task control chain. When the exact subject of an observed task is already
     * under a terminal policy of a newer generation, that task object must not acquire fresh execution scope:
     * the observation bridge is asked to fence it at the real enter point. Tasks that never re-enter still exit,
     * publish their facts and settle their resources normally, and every unrelated task is untouched.
     */
    private void policyGate(Task task,Capture event,long tick) {
        if(task.policyFenced||event.subjects.isEmpty())return;
        // The subjects this event establishes. A shared task is left alone: one subject's policy cannot decide
        // for another subject that shares the same task object.
        Set<UUID> established=new HashSet<>(task.subjects);established.addAll(event.subjects);
        if(established.size()!=1)return;
        UUID bound=established.iterator().next();
        ProRuntime.Subject subject=runtime.subjects.get(bound);
        if(subject==null||!subject.terminal)return;
        // A task that was never qualified against this precise subject cannot be called an old generation.
        boolean oldGeneration=task.authority==subject&&task.qualification>0&&task.qualification<subject.generation;
        boolean engineDenied=engineDenied(task);
        if(!oldGeneration&&!engineDenied)return;
        task.policyFenced=true;
        if(fenceTask==null)return;
        try {
            Boolean fenced=(Boolean)fenceTask.invoke(null,SINK,task.object);
            runtime.journal.append("TASK/4\tADMISSION_FENCED\t"+runtime.session+"\t"+encode(task.definition.getName())
                    +"\t"+subject.id+"\t"+(oldGeneration?"POLICY_TERMINAL_NEWER_GENERATION":"EXACT_ENGINE_DENIAL")
                    +"\t"+(Boolean.TRUE.equals(fenced)?"FENCE_INSTALLED":"FENCE_NOT_INSTALLED"));
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            // The attempt is recorded; the task keeps its own state machine untouched.
            runtime.journal.append("TASK/4\tADMISSION_FENCE_UNAVAILABLE\t"+runtime.session+"\t"
                    +encode(task.definition.getName())+"\t"+subject.id+"\t"+unavailable.getClass().getSimpleName());
        }
    }
    /** Read at the real JDK entry/result writer. Capture uses only proven object relations. */
    boolean deniesEffect(Object object) {
        Set<UUID> related;
        synchronized(workerBindings) { related=workerBindings.get(object); }
        UUID direct=origins.get(object);
        if(direct!=null) {
            if(related!=null && (related.size()!=1 || !related.contains(direct)))return false;
            var subject=runtime.subjects.get(direct);
            return subject!=null&&subject.terminal;
        }
        if(related==null||related.size()!=1)return false;
        var subject=runtime.subjects.get(related.iterator().next());
        return subject!=null&&subject.terminal;
    }
    /** Exact single-subject relation used only to serialize a result writer with policy changes. */
    ProRuntime.Subject publicationSubject(Object object) {
        if(object==null)return null;
        Set<UUID> related;
        synchronized(workerBindings) { related=workerBindings.get(object); }
        UUID direct=origins.get(object);
        if(direct!=null) {
            if(related!=null&&(related.size()!=1||!related.contains(direct)))return null;
            return runtime.subjects.get(direct);
        }
        return related!=null&&related.size()==1?runtime.subjects.get(related.iterator().next()):null;
    }
    /** The engine already refuses this exact task object; the current policy agrees with that refusal. */
    private boolean engineDenied(Task task) {
        if(admissionState==null)return false;
        try {
            long[] state=(long[])admissionState.invoke(null,SINK,task.object);
            return state!=null&&state.length>=3&&state[0]==1&&state[2]>0;
        } catch(ReflectiveOperationException|RuntimeException unavailable) { return false; }
    }
    void qualify(ProRuntime.Subject subject,UUID operation) {
        for(Task task:tasks.values())if(task.subjects.contains(subject.id)) {
            if(task.authority!=null&&task.authority!=subject) { task.reason="SHARED_TASK_MULTIPLE_SUBJECTS";continue; }
            if(task.ack!=null&&task.qualification!=subject.generation) { task.reason="OLD_TASK_INTENT_QUERY_ONLY";continue; }
            if(task.authority==subject&&task.operation!=null&&task.qualification==subject.generation)continue;
            task.authority=subject;task.operation=operation;task.qualification=subject.generation;
            enqueue(task);
        }
    }
    private void advance(Task task,long tick)throws ReflectiveOperationException {
        pollAdoptedInvocations(task);
        task.fact=runtime.journal.retryRejected(task.factRecord,task.fact);
        task.localAck=runtime.journal.retryRejected(task.localRecord,task.localAck);
        refreshQueueReceipts(task);
        flushWaiterFacts(task);
        if(task.result) { advanceResult(task,tick);return; }
        if(task.inline) { advanceObserved(task,tick);return; }
        if(task.forkJoin) { advanceForkJoin(task,tick);return; }
        if(task.delegated&&!exactFuture(task.object)) { advanceDelegate(task,tick);return; }
        if(!exactFuture(task.object)) { resetWindow(task,"UNSUPPORTED_TASK_CANCEL");return; }
        synchronized(LISTENERS) {
            checkBridge();
            if(closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"REQUIRED_TASK_BOUNDARY_UNAVAILABLE");return; }
        }
        if(task.subjects.size()!=1||!task.reason.isEmpty())return;
        if(!task.authority.terminal||task.authority.generation!=task.qualification) { resetWindow(task,"FENCED");return; }
        if(task.ack==null) {
            task.intent=intent(task);
            task.ack=runtime.journal.append(task.intent);
        }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        Future<?> future=(Future<?>)task.object;
        waitersReleased(task); // Observe original waiter-node bindings before cancellation unlinks them.
        if(!task.cancelAccepted) {
            // false is deliberate: never interrupt a shared worker.
            if(!queueGuarded(task,()->task.cancelAccepted=future.cancel(false)||future.isCancelled())) {
                resetWindow(task,"CANCEL_GATE_REVOKED");return;
            }
            if(!task.cancelAccepted&&!future.isDone()) { resetWindow(task,"CANCEL_UNCONFIRMED");return; }
        }
        if(!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()||!task.workerReferences.isEmpty()) {
            resetWindow(task,"WAIT_ACTUAL_EXECUTION_AND_CALLBACK_EXIT");return;
        }
        if(((Integer)activeInvocations.invoke(null,task.object))!=0) { resetWindow(task,"WAIT_BOOTSTRAP_INVOCATION_RETIREMENT");return; }
        if(task.releaseAck==null) {
            task.releaseIntent=PREFIX+"\tRESOURCE_RELEASE\t"+task.id+"\t"+task.operation+"\t"+runtime.session;
            task.releaseAck=runtime.journal.append(task.releaseIntent);
        }
        task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);
        if(!confirmed(task.releaseAck)) { resetWindow(task,"WAIT_RESOURCE_RELEASE_ACK");return; }
        final boolean[] queueConfirmed={false};
        if(!queueGuarded(task,()->{
            try {
            Object queue=queue(task.scheduler);
            if(queue==null) { resetWindow(task,"QUEUE_OWNER_UNOBSERVED");return; }
            if(task.queue!=null&&task.queue!=queue) { task.reason="QUEUE_OWNER_REPLACED";return; }
            task.queue=queue;
            if(!supportedQueue(queue)) { resetWindow(task,"QUEUE_BACKEND_UNSUPPORTED");return; }
            // Exact JDK Future classes inherit Object.equals. No third-party equals is invoked by remove.
            boolean removed=((BlockingQueue<?>)queue).remove(task.object);
            if(removed)task.queueRemoved=true;
            for(Object value:((BlockingQueue<?>)queue).toArray())if(value==task.object) { resetWindow(task,"QUEUE_REFERENCE_PRESENT");return; }
            task.callableReleased=callableReleased(task.object);
            if(!task.callableReleased) { resetWindow(task,"FUTURE_CALLABLE_REFERENCE_PRESENT");return; }
            queueConfirmed[0]=true;
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException("QUEUE_OWNER_UNOBSERVED",failure); }
        })||!queueConfirmed[0])return;
        if(task.entered&&!task.exitObserved) { resetWindow(task,"WAIT_ACTUAL_EXIT");return; }
        if(!task.entered&&!task.queueRemoved&&!task.dispatchExitObserved) { resetWindow(task,"DEQUEUE_DISPATCH_GAP_UNRESOLVED");return; }
        if(!waitersReleased(task)) { resetWindow(task,task.waiterCoverageGap?"WAITER_CAPTURE_UNPROVEN":"WAIT_ACTUAL_WAITER_EXIT");return; }
        finishScope(task,tick,(task.entered?"EXIT_OBSERVED":task.dispatchExitObserved?"DISPATCH_EXIT_OBSERVED":"START_PRECLUDED")+
                "\tQUEUE_REFERENCE_ABSENT_CALLABLE_CLEARED_WORKER_LOCAL_RETIRED");
    }
    private void pollAdoptedInvocations(Task task)throws ReflectiveOperationException {
        var pending=task.adoptedInvocations.entrySet().iterator();
        while(pending.hasNext()) {
            var item=pending.next();long[] state=(long[])invocationState.invoke(null,item.getKey());
            if(state[0]<0) { task.reason="SOURCE_INVOCATION_TOKEN_INVALID";continue; }
            // Its EXIT/worker-retirement event may still be behind the bounded merge cursor.
            // Only old-observer or deliberately coalesced frames are completed by polling.
            if(state[3]==1)continue;
            if(state[1]==1&&(!item.getValue()||state[2]==1)) {
                if(item.getValue())task.dispatchExitObserved=true;else task.exitObserved=true;
                pending.remove();
            }
        }
    }
    private boolean queueGuarded(Task task,Runnable action)throws ReflectiveOperationException {
        return queueGuarded(task,action,false);
    }
    private boolean queueGuarded(Task task,Runnable action,boolean observationOnly)throws ReflectiveOperationException {
        Object queue=queue(task.scheduler);if(queue==null||!supportedQueue(queue))return gateFailure(task,"QUEUE_BACKEND_UNSUPPORTED");
        if(exactFuture(task.object)&&task.object.getClass()!=FutureTask.class) {
            Class<?> type=task.object.getClass();
            if(readField(type,"this$0",task.object)!=task.scheduler||readField(type,"outerTask",task.object)!=task.object)
                return gateFailure(task,"SCHEDULED_TASK_DELEGATE_OR_OWNER_CHANGED");
        }
        List<java.util.concurrent.locks.ReentrantLock> acquired=new ArrayList<>();
        try {
            for(String name:queue.getClass()==LinkedBlockingQueue.class?List.of("putLock","takeLock"):List.of("lock")) {
                Object value=readField(queue.getClass(),name,queue);
                if(value==null||value.getClass()!=java.util.concurrent.locks.ReentrantLock.class)return gateFailure(task,"QUEUE_LOCK_LAYOUT_UNSUPPORTED");
                var lock=(java.util.concurrent.locks.ReentrantLock)value;
                if(!lock.tryLock())return gateFailure(task,"QUEUE_LOCK_BUSY");acquired.add(lock);
            }
            if(((BlockingQueue<?>)queue).size()>4096)return gateFailure(task,"QUEUE_REFERENCE_SCAN_BUDGET");
            boolean[] invoked={false};
            Runnable bound=()->{
                try { if(queue(task.scheduler)==queue) { action.run();invoked[0]=true; }else gateFailure(task,"QUEUE_OWNER_REPLACED"); }
                catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            boolean accepted;
            if(observationOnly)synchronized(LISTENERS) {
                checkBridge();accepted=gap.isEmpty()&&events.isEmpty()&&((Integer)pendingDeliveries.invoke(null))==0&&observeBackend(task)
                        &&Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,bound));
            } else accepted=guarded(task,bound);
            return accepted&&invoked[0];
        } finally { for(int i=acquired.size()-1;i>=0;i--)acquired.get(i).unlock(); }
    }
    /** Observing a shared callback's invocation never cancels or clears somebody else's callback object. */
    private void advanceDelegate(Task task,long tick)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            checkBridge();if(closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"DELEGATE_BOUNDARY_UNAVAILABLE");return; }
        }
        if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification)return;
        if(task.ack==null) { task.intent=intent(task);task.ack=runtime.journal.append(task.intent); }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        if(!task.entered||!task.exitObserved||!task.executions.isEmpty()||!task.adoptedInvocations.isEmpty()
                ||((Integer)activeInvocations.invoke(null,task.object))!=0||!waitersReleased(task)) {
            resetWindow(task,"WAIT_DELEGATED_INVOCATION_EXIT");return;
        }
        if(task.delegateOwners.isEmpty()) { resetWindow(task,"ACTUAL_DELEGATE_OWNER_UNOBSERVED");return; }
        task.callableReleased=true; // this invocation's execution reservations, not the shared callback object
        if(task.releaseAck==null) {
            task.releaseIntent=PREFIX+"\tCAPTURE_RELEASE_OBSERVED\t"+task.id+"\t"+task.operation+"\t"+runtime.session+
                    "\tDELEGATE_INVOCATION_RESERVATIONS_RETIRED_SHARED_OBJECT_UNMODIFIED";
            task.releaseAck=runtime.journal.append(task.releaseIntent);
        } else task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);
        if(!confirmed(task.releaseAck)) { resetWindow(task,"WAIT_DELEGATE_RESERVATION_ACK");return; }
        finishScope(task,tick,"DELEGATED_INVOCATION_EXIT_OBSERVED\tEXECUTION_RESERVATIONS_RETIRED_SHARED_CALLBACK_NOT_DISPOSED");
    }
    private void advanceForkJoin(Task task,long tick)throws ReflectiveOperationException {
        for(var request:task.ownedQueueRequests.entrySet()) {
            if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification||task.subjects.size()!=1)
                cancelOwnedQueueRelease.invoke(null,SINK,request.getValue());
            else wakeOwnedQueue.invoke(null,SINK,request.getValue());
        }
        refreshQueueReceipts(task);
        if(task.blockedEntryObserved) {
            if(task.preclusionRecord==null) {
                task.preclusionRecord=PREFIX+"\tSTART_PRECLUDED\t"+task.id+"\t"+runtime.session+"\tEXACT_EXECUTION_ENTRY\tOTHER_INVOCATIONS_REQUIRE_EXIT";
                task.preclusionAck=runtime.journal.append(task.preclusionRecord);
            } else task.preclusionAck=runtime.journal.retryRejected(task.preclusionRecord,task.preclusionAck);
        }
        if(task.admissionReleased) {
            task.fenceReleaseAck=runtime.journal.retryRejected(task.fenceReleaseRecord,task.fenceReleaseAck);
            if(task.scheduler instanceof ThreadPoolExecutor)releaseCompletionExecutorQueue(task);
            if(task.completion)finishReleasedCompletion(task,tick);else finishReleasedForkJoin(task,tick);return;
        }
        if(!task.admissionFenced&&task.completion&&completionReleased(task)) { advanceObserved(task,tick);return; }
        if(task.subjects.size()!=1||!task.reason.isEmpty())return;
        if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification) { resetWindow(task,"FENCED");return; }
        if(task.ack==null) { task.intent=intent(task);task.ack=runtime.journal.append(task.intent); }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        if(!task.admissionFenced) {
            waitersReleased(task);
            if(task.definition.getMethod("cancel",boolean.class).getDeclaringClass()!=ForkJoinTask.class) {
                resetWindow(task,"CUSTOM_FORKJOIN_CANCEL_UNSUPPORTED");return;
            }
            if(!guarded(task,()->{
                try {
                    task.admissionFenced=Boolean.TRUE.equals(fenceTask.invoke(null,SINK,task.object));
                    if(task.admissionFenced)task.cancelAccepted=((ForkJoinTask<?>)task.object).cancel(false)||((ForkJoinTask<?>)task.object).isCancelled();
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            })||!task.admissionFenced) { resetWindow(task,"TASK_ADMISSION_GATE_UNAVAILABLE");return; }
        }
        if(task.admissionRecord==null) {
            task.admissionRecord=PREFIX+"\tADMISSION_FENCED\t"+task.id+"\t"+runtime.session+"\tEXACT_OBJECT\tRUNNING_INVOCATIONS_NOT_INTERRUPTED";
            task.admissionAck=runtime.journal.append(task.admissionRecord);
        } else task.admissionAck=runtime.journal.retryRejected(task.admissionRecord,task.admissionAck);
        if(!confirmed(task.admissionAck)) { resetWindow(task,"WAIT_ADMISSION_FACT_ACK");return; }
        long[] admissions=(long[])admissionState.invoke(null,SINK,task.object);
        if(!task.admissionReleased&&(admissions[0]!=1||admissions[1]!=0)) { resetWindow(task,"WAIT_ADMITTED_INVOCATIONS_EXIT");return; }
        if(!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()||!task.workerReferences.isEmpty()) {
            resetWindow(task,"WAIT_ACTUAL_EXECUTION_AND_CALLBACK_EXIT");return;
        }
        if(!waitersReleased(task)) { resetWindow(task,task.waiterCoverageGap?"WAITER_CAPTURE_UNPROVEN":"WAIT_ACTUAL_WAITER_EXIT");return; }
        if(((Integer)activeInvocations.invoke(null,task.object))!=0) { resetWindow(task,"WAIT_EXECUTION_OR_PERIODIC_RESUBMISSION_RETIREMENT");return; }
        if(task.releaseAck==null) {
            task.releaseIntent=PREFIX+"\tRESOURCE_RELEASE\t"+task.id+"\t"+task.operation+"\t"+runtime.session;
            task.releaseAck=runtime.journal.append(task.releaseIntent);
        } else task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);
        if(!confirmed(task.releaseAck)) { resetWindow(task,"WAIT_RESOURCE_RELEASE_ACK");return; }
        if(task.scheduler instanceof ForkJoinPool)releaseSharedForkQueues(task);
        if(task.scheduler instanceof ForkJoinPool)requestOwnerQueues(task);
        if(task.completion&&task.scheduler instanceof ThreadPoolExecutor)releaseCompletionExecutorQueue(task);
        if(!task.completion) { advanceForkJoinReferences(task,tick);return; }
        if(!task.creationObserved) { resetWindow(task,"CF_CONSTRUCTOR_BINDING_UNOBSERVED");return; }
        if(!task.callbackFieldsReleased) {
            Map<Field,Object> expected=new LinkedHashMap<>();
            for(Field field:completionFields(task))if(!field.getName().equals("next"))expected.put(field,field.get(task.object));
            if(!guarded(task,()->{
                try {
                    long[] state=(long[])admissionState.invoke(null,SINK,task.object);
                    if(state[0]!=1||state[1]!=0)return;
                    for(var entry:expected.entrySet())if(entry.getKey().get(task.object)!=entry.getValue())return;
                    for(var entry:expected.entrySet())if(entry.getKey().getName().equals("dep")&&entry.getValue()!=null) {
                        Object result=entry.getValue();
                        task.dependentResultPending=!standardCompletionSource(result)||readField(CompletableFuture.class,"result",result)==null;
                    }
                    for(Field field:expected.keySet())field.set(task.object,null);
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }))return;
            if(!completionFieldsAbsent(task,false)) { resetWindow(task,"CF_CALLBACK_REFERENCE_RELEASE_UNCONFIRMED");return; }
            task.callbackFieldsReleased=true;
        }
        if(!releaseCompletionStacks(task)) { resetWindow(task,"CF_SOURCE_STACK_RELEASE_PENDING");return; }
        if(!completionFieldsAbsent(task,true)||!completionStacksAbsent(task)) { resetWindow(task,"CF_REFERENCE_READBACK_PENDING");return; }
        // Exact callbacks were removed from every observed publication stack, under a fenced entry.
        // This is start-preclusion evidence even when no invocation ever existed to emit EXIT.
        if(!task.entered&&task.executions.isEmpty()&&task.dispatches.isEmpty())task.queueRemoved=true;
        if(task.callbackReleaseRecord==null) {
            task.callbackReleaseRecord=PREFIX+"\tCALLBACK_FIELDS_RELEASED\t"+task.id+"\t"+runtime.session+"\tEXACT_NODE_FIELDS_AND_SOURCE_STACKS\t"+
                    (task.dependentResultPending?"DEPENDENT_RESULT_UNCHANGED_PENDING":"DEPENDENT_RESULT_NOT_MUTATED");
            task.callbackReleaseAck=runtime.journal.append(task.callbackReleaseRecord);
        } else task.callbackReleaseAck=runtime.journal.retryRejected(task.callbackReleaseRecord,task.callbackReleaseAck);
        if(!confirmed(task.callbackReleaseAck)) { resetWindow(task,"WAIT_CF_REFERENCE_FACT_ACK");return; }
        if(!releaseAdmissionResource(task,false)) { resetWindow(task,"WAIT_ADMISSION_RESOURCE_RELEASE");return; }
        finishReleasedCompletion(task,tick);
    }
    private void releaseCompletionExecutorQueue(Task task)throws ReflectiveOperationException {
        if(task.queueRecords.size()>=64) { task.gateReason="WAIT_QUEUE_RECEIPT_BUDGET";return; }
        queueGuarded(task,()->{
            try {
                long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                if(((Integer)activeInvocations.invoke(null,task.object))!=0)return;
                if(task.admissionReleased) { if(!completionFieldsAbsent(task,true)||!completionStacksAbsent(task))return; }
                else if(admission[0]!=1||admission[1]!=0)return;
                Object observed=queue(task.scheduler);
                if(task.queue!=null&&task.queue!=observed) { task.reason="QUEUE_OWNER_REPLACED";return; }
                task.queue=observed;
                int removed=0;
                while(removed<4096&&((BlockingQueue<?>)observed).remove(task.object))removed++;
                if(removed!=0) {
                    task.queueRemoved=true;queueReceipt(task,"QUEUE_REFERENCE_REMOVED","EXACT_CF_NODE_FROM_EXECUTOR_QUEUE_COUNT="+removed);
                }
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        });
    }
    private void advanceForkJoinReferences(Task task,long tick)throws ReflectiveOperationException {
        if(!(task.scheduler instanceof ForkJoinPool)||!forkQueuesAbsent(task)
                ||((Integer)helperReferences.invoke(null,task.object))!=0||!task.ownedQueueRequests.isEmpty()) {
            resetWindow(task,"FORKJOIN_QUEUE_OR_HELPER_REFERENCE_PRESENT");return;
        }
        if(!task.forkCaptureBound) {
            for(Class<?> owner=task.definition;owner!=null&&owner!=ForkJoinTask.class;owner=owner.getSuperclass())
                for(Field field:owner.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
                    if(!field.trySetAccessible()) { resetWindow(task,"FORKJOIN_CAPTURE_FIELD_INACCESSIBLE");return; }
                    Object value=field.get(task.object);if(value==null)continue;
                    UUID direct=origins.get(value);Set<UUID> related=workerBindings.get(value);
                    boolean platform=knownDelegate(owner)&&jdkDelegateField(owner,field);
                    if(platform||task.authority.id.equals(direct)||related!=null&&related.size()==1&&related.contains(task.authority.id))
                        task.forkCaptureEdges.put(field,value);
                }
            task.forkCaptureBound=true;
        }
        if(!task.callbackFieldsReleased) {
            if(!guarded(task,()->{
                try {
                    long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                    if(admission[0]!=1||admission[1]!=0||((Integer)helperReferences.invoke(null,task.object))!=0||!forkQueuesAbsent(task))return;
                    if(!forkTerminal(task)||task.object.getClass()!=task.definition)return;
                    for(var edge:task.forkCaptureEdges.entrySet())if(edge.getKey().get(task.object)!=edge.getValue())return;
                    for(Field field:task.forkCaptureEdges.keySet())field.set(task.object,null);
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }))return;
            if(!forkCapturesAbsent(task)) { resetWindow(task,"FORKJOIN_CAPTURE_READBACK_PENDING");return; }
            task.callbackFieldsReleased=true;
        }
        if(task.callbackReleaseRecord==null) {
            task.callbackReleaseRecord=PREFIX+"\tCALLBACK_FIELDS_RELEASED\t"+task.id+"\t"+runtime.session+
                    "\tEXACT_OWNED_CAPTURE_EDGES_UNLINKED\tSHARED_OBJECTS_UNMODIFIED";
            task.callbackReleaseAck=runtime.journal.append(task.callbackReleaseRecord);
        } else task.callbackReleaseAck=runtime.journal.retryRejected(task.callbackReleaseRecord,task.callbackReleaseAck);
        if(!confirmed(task.callbackReleaseAck))return;
        task.forkCaptureEdges.replaceAll((field,value)->null);
        if(!releaseAdmissionResource(task,false))return;
        finishReleasedForkJoin(task,tick);
    }
    private static boolean forkCapturesAbsent(Task task)throws IllegalAccessException {
        if(!task.forkCaptureBound||task.object.getClass()!=task.definition||!forkTerminal(task))return false;
        for(Field field:task.forkCaptureEdges.keySet())if(field.get(task.object)!=null)return false;
        return true;
    }
    private static boolean forkTerminal(Task task) {
        ForkJoinTask<?> future=(ForkJoinTask<?>)task.object;
        return future.isCancelled()||future.isDone()&&task.entered&&task.exitObserved;
    }
    /** Read the exact registered pool under the publication gate; helper locals are a separate counter. */
    private boolean forkQueuesAbsent(Task task)throws ReflectiveOperationException {
        if(!(task.scheduler instanceof ForkJoinPool)||!task.ownedQueueRequests.isEmpty())return false;
        Object[] queues=(Object[])readField(ForkJoinPool.class,"queues",task.scheduler);
        if(queues==null)return ((ForkJoinPool)task.scheduler).isTerminated();
        if(queues.length>32768)return false;
        int budget=65536;
        Class<?> type=Class.forName("java.util.concurrent.ForkJoinPool$WorkQueue",false,null);
        for(Object queue:queues)if(queue!=null) {
            if(queue.getClass()!=type)return false;
            Object[] entries=(Object[])readField(type,"array",queue);
            if(entries==null)continue;
            if((budget-=entries.length)<0)return false;
            for(Object value:entries)if(value==task.object)return false;
        }
        return readField(ForkJoinPool.class,"queues",task.scheduler)==queues;
    }
    private void finishReleasedForkJoin(Task task,long tick)throws ReflectiveOperationException {
        if(!confirmed(task.fenceReleaseAck)||!confirmed(task.callbackReleaseAck)||!task.queueRecords.isEmpty())return;
        if(!forkCapturesAbsent(task)||!forkQueuesAbsent(task)||((Integer)helperReferences.invoke(null,task.object))!=0
                ||!waitersReleased(task)) { resetWindow(task,"FORKJOIN_REFERENCE_REASSERTION_OR_EXIT_PENDING");return; }
        if(task.entered&&!task.exitObserved||!task.entered&&!task.queueRemoved&&!task.blockedEntryObserved) {
            resetWindow(task,"FORKJOIN_START_OR_EXIT_UNCONFIRMED");return;
        }
        task.callableReleased=true;
        finishScope(task,tick,(task.entered?"EXIT_OBSERVED":"START_PRECLUDED")+"\tFORKJOIN_QUEUES_HELPER_LOCALS_CAPTURE_SHARES_RELEASED");
    }
    /** Query the known owner's share independently. Never extrapolate a worker-deque snapshot
     * to ForkJoin helper-stack retirement or to arbitrary third-party callback aliases. */
    private boolean completionOwnerReleased(Task task)throws ReflectiveOperationException {
        if(!task.creationObserved||!completionFieldsAbsent(task,true)||!completionStacksAbsent(task)
                ||!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()
                ||!task.workerReferences.isEmpty()||!task.adoptedInvocations.isEmpty()
                ||((Integer)activeInvocations.invoke(null,task.object))!=0||((Integer)helperReferences.invoke(null,task.object))!=0||!waitersReleased(task))return false;
        if(task.scheduler instanceof ForkJoinPool)return forkQueuesAbsent(task);
        if(task.scheduler==null) {
            if(task.completionModeObserved&&!task.asynchronousCompletion&&task.entered&&task.exitObserved)return true;
            // A callback fenced before dispatch has no worker EXIT. Its observed factory
            // must have returned, and every publication edge must actually be detached.
            boolean factory=false;
            for(Task parent:task.parents)if(parent.inline) {
                factory=true;if(!parent.exitObserved||!Boolean.TRUE.equals(inlineClosed.invoke(null,parent.object)))return false;
            }
            return factory&&task.queueRemoved&&confirmed(task.callbackReleaseAck)&&task.admissionReleased;
        }
        if(!(task.scheduler instanceof ThreadPoolExecutor)||!task.queueRemoved&&!task.dispatchExitObserved)return false;
        boolean[] absent={false};
        if(!queueGuarded(task,()->{
            try {
                Object observed=queue(task.scheduler);
                if(task.queue!=null&&task.queue!=observed)return;
                for(Object entry:((BlockingQueue<?>)observed).toArray())if(entry==task.object)return;
                absent[0]=true;
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        },true))return false;
        return absent[0];
    }
    private void finishReleasedCompletion(Task task,long tick)throws ReflectiveOperationException {
        refreshDependentResult(task);
        if(!confirmed(task.fenceReleaseAck)) { resetWindow(task,"WAIT_FENCE_RELEASE_FACT_ACK");return; }
        if(task.dependentResultPending) { resetWindow(task,"CF_CALLBACK_PRECLUDED_DEPENDENT_RESULT_PENDING");return; }
        if(!task.queueRecords.isEmpty()||!confirmed(task.callbackReleaseAck)) { resetWindow(task,"WAIT_CF_RESOURCE_FACT_ACK");return; }
        if(!completionOwnerReleased(task)) { resetWindow(task,"CF_CALLBACK_CLEARED_QUEUE_AND_WORKER_OWNERS_PENDING");return; }
        task.callableReleased=true;
        finishScope(task,tick,"CF_CALLBACK_FIELDS_AND_SOURCE_STACKS_RELEASED\tKNOWN_EXECUTOR_QUEUE_WORKER_AND_WAITERS_RETIRED");
    }
    private void advanceResult(Task task,long tick)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            checkBridge();if(closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"RESULT_OBSERVER_UNAVAILABLE");return; }
        }
        if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification||task.subjects.size()!=1)return;
        if(task.ack==null) { task.intent=intent(task);task.ack=runtime.journal.append(task.intent); }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        if(readField(CompletableFuture.class,"result",task.object)==null) {
            cancelOwnedResult(task);
            if(readField(CompletableFuture.class,"result",task.object)==null) { resetWindow(task,"DEPENDENT_RESULT_NOT_TERMINAL");waitersReleased(task);return; }
        }
        if(task.resultCancelRecord!=null&&!confirmed(task.resultCancelAck)) { resetWindow(task,"RESULT_CANCEL_FACT_ACK_PENDING");return; }
        if(!waitersReleased(task)) { resetWindow(task,task.waiterCoverageGap?"WAITER_CAPTURE_UNPROVEN":"WAIT_RESULT_WAITER_EXIT");return; }
        task.callableReleased=true;
        if(task.releaseAck==null) {
            task.releaseIntent=PREFIX+"\tRESULT_WAITERS_RELEASE_OBSERVED\t"+task.id+"\t"+runtime.session+"\tACTUAL_WAIT_EXITS_AND_NODE_READBACK\tRESULT_NOT_MUTATED";
            task.releaseAck=runtime.journal.append(task.releaseIntent);
        } else task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);
        if(!confirmed(task.releaseAck)) { resetWindow(task,"WAIT_RESULT_WAITER_FACT_ACK");return; }
        finishScope(task,tick,"RESULT_TERMINAL_OBSERVED\tWAITER_INTERVALS_EXITED_NO_RESULT_EFFECT_CLAIM");
    }
    private void cancelOwnedResult(Task task)throws ReflectiveOperationException {
        if(!task.ownedResult||task.object.getClass()!=CompletableFuture.class)return;
        Set<Task> actual=producers.getOrDefault(task.object,Set.of());
        if(actual.isEmpty())return;
        for(Task producer:actual)if(producer.authority!=task.authority||producer.qualification!=task.qualification
                ||!producer.executions.isEmpty()||!producer.dispatches.isEmpty()
                ||!(producer.inline&&Boolean.TRUE.equals(inlineClosed.invoke(null,producer.object))
                    ||producer.completion&&producer.admissionReleased&&producer.callbackFieldsReleased))return;
        if(task.resultCancelRecord==null)task.resultCancelRecord=PREFIX+"\tRESULT_CANCEL_INTENT\t"+task.id+"\t"+task.operation+"\t"+runtime.session+"\tEXACT_OBSERVED_RESULT_ALL_PRODUCERS_CLOSED";
        task.resultCancelAck=task.resultCancelAck==null?runtime.journal.append(task.resultCancelRecord):runtime.journal.retryRejected(task.resultCancelRecord,task.resultCancelAck);
        if(!confirmed(task.resultCancelAck)||task.resultCancellationAttempted)return;
        var lookup=java.lang.invoke.MethodHandles.privateLookupIn(CompletableFuture.class,java.lang.invoke.MethodHandles.lookup());
        var result=lookup.findVarHandle(CompletableFuture.class,"result",Object.class);
        Class<?> alt=Class.forName("java.util.concurrent.CompletableFuture$AltResult",false,null);
        var constructor=alt.getDeclaredConstructor(Throwable.class);if(!constructor.trySetAccessible())return;
        Object cancellation=constructor.newInstance(new CancellationException("Ronova original recovery operation "+task.operation));
        boolean[] changed={false};
        if(!guarded(task,()->{
            for(Task producer:actual)if(!producer.executions.isEmpty()||!producer.dispatches.isEmpty())return;
            changed[0]=result.compareAndSet(task.object,null,cancellation);
            if(changed[0])task.resultCancellationAttempted=true;
        })||!changed[0])return;
        // Invoke the platform's completion drain outside Root and publication locks. Successors are observed normally.
        Method drain=CompletableFuture.class.getDeclaredMethod("postComplete");if(!drain.trySetAccessible())throw new IllegalAccessException("CF_POST_COMPLETE_UNAVAILABLE");
        drain.invoke(task.object);
        task.resultCancelRecord=PREFIX+"\tRESULT_CANCELLED\t"+task.id+"\t"+task.operation+"\t"+runtime.session+"\tEXACT_RESULT_CAS_OBSERVED_SUCCESSORS_SEPARATE";
        task.resultCancelAck=runtime.journal.append(task.resultCancelRecord);
    }
    private void flushWaiterFacts(Task task) {
        task.waiterFactAck=runtime.journal.retryRejected(task.waiterFactRecord,task.waiterFactAck);
        if(task.waiterFactAck!=null&&!confirmed(task.waiterFactAck))return;
        if(task.waiterExitsRecorded==task.waiterExits)return;
        task.waiterFactRecord=PREFIX+"\tWAITER_EXITS_OBSERVED\t"+task.id+"\t"+runtime.session+"\tfrom="+
                (task.waiterExitsRecorded+1)+"\tthrough="+task.waiterExits;
        task.waiterExitsRecorded=task.waiterExits;task.waiterFactAck=runtime.journal.append(task.waiterFactRecord);
    }
    private static boolean waiterFactsCurrent(Task task) {
        return task.waiterExits==task.waiterExitsRecorded&&(task.waiterExits==0||confirmed(task.waiterFactAck));
    }
    private static final ClassValue<ConcurrentMap<String,Field>> READ_FIELDS=new ClassValue<>() {
        @Override protected ConcurrentMap<String,Field> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };
    private static Object readField(Class<?> type,String name,Object object)throws ReflectiveOperationException {
        var fields=READ_FIELDS.get(type);Field field=fields.get(name);
        if(field==null)field=type.getDeclaredField(name);
        if(!field.trySetAccessible())throw new IllegalAccessException("FIELD_UNAVAILABLE:"+type.getName()+"."+name);
        fields.putIfAbsent(name,field);
        return field.get(object);
    }
    private boolean waitersReleased(Task task)throws ReflectiveOperationException {
        if(task.waiterCoverageGap||waiterCount==null||waiterNodeObserved==null)return false;
        Object head;Class<?> nodeType;boolean completion=false,fork=false;
        if(exactFuture(task.object)) {
            head=readField(FutureTask.class,"waiters",task.object);nodeType=Class.forName("java.util.concurrent.FutureTask$WaitNode",false,null);
        } else if(task.forkJoin) {
            head=readField(ForkJoinTask.class,"aux",task.object);nodeType=Class.forName("java.util.concurrent.ForkJoinTask$Aux",false,null);fork=true;
        } else if(task.result) {
            head=readField(CompletableFuture.class,"stack",task.object);nodeType=Class.forName("java.util.concurrent.CompletableFuture$Completion",false,null);completion=true;
        } else return task.waiters.isEmpty();
        boolean live=false;Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Object node=head;node!=null;node=readField(nodeType,"next",node)) {
            if(!nodeType.isInstance(node)||!seen.add(node)||seen.size()>4096) { task.waiterCoverageGap=true;return false; }
            if(completion&&!node.getClass().getName().equals("java.util.concurrent.CompletableFuture$Signaller"))continue;
            if(fork&&readField(nodeType,"ex",node)!=null)continue;
            Object thread=readField(completion?node.getClass():nodeType,"thread",node);
            if(thread!=null) {
                live=true;
                if(!Boolean.TRUE.equals(waiterNodeObserved.invoke(null,task.object,node)))task.waiterCoverageGap=true;
            }
        }
        return !task.waiterCoverageGap&&!live&&task.waiters.isEmpty()&&((Integer)waiterCount.invoke(null,task.object))==0;
    }
    private void refreshQueueReceipts(Task task) {
        var pending=task.queueRecords.entrySet().iterator();
        while(pending.hasNext()) {
            var entry=pending.next();entry.setValue(runtime.journal.retryRejected(entry.getKey(),entry.getValue()));
            if(confirmed(entry.getValue())) { task.queueReceiptsDurable++;pending.remove(); }
        }
        // Only the in-memory ACK cache is evicted. The original resource facts stay in the durable journal.
    }
    private void queueReceipt(Task task,String kind,String fact) {
        if(task.nextQueueReceipt==Long.MAX_VALUE) { recordGap("TASK_RESOURCE_RECEIPT_SEQUENCE_EXHAUSTED");return; }
        String record=PREFIX+"\t"+kind+"\t"+task.id+"\t"+runtime.session+"\t"+fact+"\t"+task.nextQueueReceipt++;
        task.queueRecords.put(record,runtime.journal.append(record));
    }
    private void requestOwnerQueues(Task task)throws ReflectiveOperationException {
        Field queues=ForkJoinPool.class.getDeclaredField("queues");if(!queues.trySetAccessible())return;
        Object[] observed=(Object[])queues.get(task.scheduler);if(observed==null||observed.length==0||observed.length>32768)return;
        Class<?> type=Class.forName("java.util.concurrent.ForkJoinPool$WorkQueue",false,null);
        Field owner=type.getDeclaredField("owner");if(!owner.trySetAccessible())return;
        for(int visited=0;visited<Math.min(64,observed.length);visited++) {
            Object queue=observed[Math.floorMod(task.ownerQueueCursor++,observed.length)];
            if(queue==null||queue.getClass()!=type||owner.get(queue)==null)continue;
            if(task.ownedQueueRequests.containsKey(queue)) {
                if(!Objects.equals(task.ownedQueueEpochs.get(queue),task.backendEpoch))cancelOwnedQueueRelease.invoke(null,SINK,task.ownedQueueRequests.get(queue));
                continue;
            }
            if(task.servicedOwnerQueues.contains(queue))continue;
            if(task.queueRecords.size()+2*task.ownedQueueRequests.size()>=62)return;
            guarded(task,()->{
                try {
                    String[] boundaries=task.requiredBoundaries.clone();long epoch=task.backendEpoch;
                    ProRuntime.Subject authority=task.authority;long generation=task.qualification;
                    java.util.function.Predicate<Runnable> permit=action->ownerQueueAction(task.object,authority,generation,boundaries,epoch,action);
                    Object request=requestOwnedQueueRelease.invoke(null,SINK,queue,task.object,permit);
                    if(request!=null) { task.ownedQueueRequests.put(queue,request);task.ownedQueueEpochs.put(queue,epoch);wakeOwnedQueue.invoke(null,SINK,request); }
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
        }
    }
    private boolean ownerQueueAction(Object task,ProRuntime.Subject authority,long generation,String[] boundaries,long epoch,Runnable action) {
        synchronized(LISTENERS) {
            try {
                checkBridge();Set<UUID> published=workerBindings.get(task);
                if(closing||!runtime.journal.healthy()||!gap.isEmpty()||!events.isEmpty()||((Integer)pendingDeliveries.invoke(null))!=0
                        ||published==null||published.size()!=1||!published.contains(authority.id))return false;
                for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task))return false;
                if(!authority.commitGate.tryLock())return false;
                try {
                    if(!authority.terminal||authority.generation!=generation)return false;
                    return Boolean.TRUE.equals(withBoundaries.invoke(null,boundaries,epoch,action));
                } finally { authority.commitGate.unlock(); }
            } catch(ReflectiveOperationException|RuntimeException unavailable) { recordGap("OWNER_QUEUE_EXECUTION_UNCONFIRMED");return false; }
        }
    }
    private static void refreshDependentResult(Task task)throws ReflectiveOperationException {
        if(task.completionOutputs.isEmpty())return;
        for(Object output:task.completionOutputs)
            if(!standardCompletionSource(output)||readField(CompletableFuture.class,"result",output)==null) { task.dependentResultPending=true;return; }
        task.dependentResultPending=false;
    }
    private boolean releaseAdmissionResource(Task task,boolean retiring)throws ReflectiveOperationException {
        if(!task.ownedQueueRequests.isEmpty())return false;
        if(task.fenceReleaseRecord==null) {
            task.fenceReleaseRecord=PREFIX+"\tFENCE_RELEASE_INTENT\t"+task.id+"\t"+runtime.session+"\tCLEARED_OWNED_CAPTURE_EDGES\tNO_RUNNING_ADMISSIONS";
            task.fenceReleaseAck=runtime.journal.append(task.fenceReleaseRecord);
        }
        task.fenceReleaseAck=runtime.journal.retryRejected(task.fenceReleaseRecord,task.fenceReleaseAck);
        if(!confirmed(task.fenceReleaseAck))return false;
        if(!task.admissionReleased) {
            Runnable release=()->{
                try {
                    if((task.completion?completionFieldsAbsent(task,true)&&completionStacksAbsent(task):forkCapturesAbsent(task))
                            &&((Integer)helperReferences.invoke(null,task.object))==0)
                        task.admissionReleased=Boolean.TRUE.equals(releaseFence.invoke(null,SINK,task.object));
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            boolean accepted;
            if(retiring) {
                // Only our registry reference is released here. No cancelled qualification authorizes field/queue writes.
                synchronized(LISTENERS) {
                    checkBridge();
                    accepted=gap.isEmpty()&&events.isEmpty()&&((Integer)pendingDeliveries.invoke(null))==0&&observeBackend(task)
                            &&Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,release));
                }
            } else accepted=guarded(task,release);
            if(!accepted||!task.admissionReleased)return false;
            task.fenceReleaseRecord=PREFIX+"\tFENCE_RELEASED\t"+task.id+"\t"+runtime.session+"\tREGISTRY_REFERENCE_REMOVED\tOWNED_CAPTURE_EDGES_CLEARED";
            task.fenceReleaseAck=runtime.journal.append(task.fenceReleaseRecord);
        }
        return confirmed(task.fenceReleaseAck);
    }
    /** Only external submission queues use this queue lock; worker-owned deques are never treated as externally owned. */
    private void releaseSharedForkQueues(Task task)throws ReflectiveOperationException {
        Field queues=ForkJoinPool.class.getDeclaredField("queues");
        if(!queues.trySetAccessible())return;
        Object[] observed=(Object[])queues.get(task.scheduler);if(observed==null||observed.length>32768)return;
        Class<?> type=Class.forName("java.util.concurrent.ForkJoinPool$WorkQueue",false,null);
        Field owner=type.getDeclaredField("owner"),array=type.getDeclaredField("array"),top=type.getDeclaredField("top"),base=type.getDeclaredField("base");
        Method remove=type.getDeclaredMethod("tryRemove",ForkJoinTask.class,boolean.class);
        if(!owner.trySetAccessible()||!array.trySetAccessible()||!top.trySetAccessible()||!base.trySetAccessible()||!remove.trySetAccessible())return;
        var lookup=java.lang.invoke.MethodHandles.privateLookupIn(type,java.lang.invoke.MethodHandles.lookup());
        var source=lookup.findVarHandle(type,"source",int.class);
        int visited=0;
        for(int index=0;index<observed.length;index++) {
            Object queue=observed[index];int slot=index;
            if(queue==null||queue.getClass()!=type||owner.get(queue)!=null)continue;
            if(++visited>64||task.queueRecords.size()+2*task.ownedQueueRequests.size()>=64||task.nextQueueReceipt==Long.MAX_VALUE)return;
            // Never wait for a shared queue while holding Root, publication or certificate gates.
            if(!(boolean)source.compareAndSet(queue,0,1))continue;
            try {
                int span=top.getInt(queue)-base.getInt(queue);
                Object image=array.get(queue);
                if(span<0||span>4096||image==null)continue;
                guarded(task,()->{
                    try {
                        long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                        if(admission[0]!=1||admission[1]!=0||queues.get(task.scheduler)!=observed||observed[slot]!=queue||owner.get(queue)!=null||array.get(queue)!=image)return;
                        // We hold the real shared-queue lock. The verified helper's owned path skips reacquiring it.
                        if(Boolean.TRUE.equals(remove.invoke(queue,task.object,true))) {
                            task.queueRemoved=true;
                            queueReceipt(task,"QUEUE_REFERENCE_REMOVED","SHARED_FORKJOIN_SUBMISSION_QUEUE_ONLY");
                        }
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                });
            } finally { source.setRelease(queue,0); }
        }
    }
    private static List<Field> completionFields(Task task)throws ReflectiveOperationException {
        if(!exactCompletion(task.object)||task.definition!=task.object.getClass())throw new IllegalAccessException("CF_DEFINITION_CHANGED");
        var result=new ArrayList<Field>();
        for(Class<?> type=task.definition;type!=null&&type.getName().startsWith("java.util.concurrent.CompletableFuture$");type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                if(Modifier.isFinal(field.getModifiers())||!Set.of("src","snd","dep","fn","executor","base","next","srcs").contains(field.getName())
                        ||!field.trySetAccessible())throw new IllegalAccessException("CF_REFERENCE_LAYOUT_UNSUPPORTED:"+type.getName()+"#"+field.getName());
                result.add(field);
            }
        }
        return result;
    }
    private static boolean completionFieldsAbsent(Task task,boolean includeNext)throws ReflectiveOperationException {
        for(Field field:completionFields(task))if((includeNext||!field.getName().equals("next"))&&field.get(task.object)!=null)return false;
        return true;
    }
    private record CompletionLinks(Class<?> node,java.lang.invoke.VarHandle stack,java.lang.invoke.VarHandle next) { }
    private static volatile CompletionLinks cachedCompletionLinks;
    private static CompletionLinks completionLinks()throws ReflectiveOperationException {
        CompletionLinks cached=cachedCompletionLinks;if(cached!=null)return cached;
        Class<?> node=Class.forName("java.util.concurrent.CompletableFuture$Completion",false,null);
        var lookup=java.lang.invoke.MethodHandles.lookup();
        CompletionLinks resolved=new CompletionLinks(node,java.lang.invoke.MethodHandles.privateLookupIn(CompletableFuture.class,lookup).findVarHandle(CompletableFuture.class,"stack",node),
                java.lang.invoke.MethodHandles.privateLookupIn(node,lookup).findVarHandle(node,"next",node));
        cachedCompletionLinks=resolved;return resolved;
    }
    private static boolean standardCompletionSource(Object value) {
        Class<?> type=value.getClass();return type==CompletableFuture.class||type.getClassLoader()==null&&type.getName().equals("java.util.concurrent.CompletableFuture$MinimalStage");
    }
    private static List<Object> dependencies(Object first,Object second) {
        List<Object> result=new ArrayList<>();
        for(Object input:new Object[]{first,second}) {
            if(input instanceof CompletableFuture<?>[] array)Collections.addAll(result,array);
            else if(input!=null)result.add(input);
        }
        return result;
    }
    private static boolean completionStacksAbsent(Task task)throws ReflectiveOperationException {
        CompletionLinks links=completionLinks();
        if(!links.node.isInstance(task.object))return true; // AsyncSupply/AsyncRun have no completion-stack link.
        if(task.completionSources.isEmpty())return false;
        for(Object source:task.completionSources) {
            if(!standardCompletionSource(source))return false;
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Object node=links.stack.getVolatile(source);node!=null;node=links.next.getVolatile(node))
                if(node==task.object||!links.node.isInstance(node)||seen.size()>=4096||!seen.add(node))return false;
        }
        return true;
    }
    private boolean releaseCompletionStacks(Task task)throws ReflectiveOperationException {
        CompletionLinks links=completionLinks();
        if(!links.node.isInstance(task.object))return true;
        if(task.completionSources.isEmpty())return false;
        boolean[] released={false};
        if(!guarded(task,()->{
            try {
                long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                if(admission[0]!=1||admission[1]!=0||!completionFieldsAbsent(task,false))return;
                for(Object source:task.completionSources) {
                    if(!standardCompletionSource(source))return;
                    Object previous=null,node=links.stack.getVolatile(source);
                    for(int budget=0;node!=null&&budget<128;budget++) {
                        if(!links.node.isInstance(node))return;
                        Object next=links.next.getVolatile(node);
                        if(node==task.object) {
                            if(previous==null)links.stack.compareAndSet(source,node,next);
                            else links.next.compareAndSet(previous,node,next);
                            previous=null;node=links.stack.getVolatile(source); // recheck reachability after every CAS
                        } else { previous=node;node=next; }
                    }
                }
                if(completionStacksAbsent(task)) { links.next.setVolatile(task.object,null);released[0]=true; }
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }))return false;
        return released[0];
    }
    /** Confirm naturally completed calls without replaying callbacks or inventing cancellation authority. */
    private void advanceObserved(Task task,long tick)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            checkBridge();
            if(closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"REQUIRED_INLINE_BOUNDARY_UNAVAILABLE");return; }
        }
        if(task.subjects.size()!=1||!task.reason.isEmpty())return;
        if(task.authority==null||!task.authority.terminal||task.authority.generation!=task.qualification) { resetWindow(task,"FENCED");return; }
        if(task.ack==null) {
            task.intent=intent(task);
            task.ack=runtime.journal.append(task.intent);
        }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        task.callableReleased=task.inline?Boolean.TRUE.equals(inlineClosed.invoke(null,task.object)):completionReleased(task);
        if(!task.entered||!task.exitObserved||!task.executions.isEmpty()||!task.adoptedInvocations.isEmpty()||!task.callableReleased) {
            resetWindow(task,task.inline?"WAIT_INLINE_INVOCATION_EXIT_AND_CAPTURE_RELEASE":
                    task.asynchronousCompletion||task.scheduler!=null?"CF_ASYNC_QUEUE_AND_WORKER_OWNERS_PENDING":"WAIT_CF_DIRECT_EXIT_REFERENCES_AND_STACK_UNLINK");return;
        }
        if(task.releaseAck==null) {
            task.releaseIntent=PREFIX+"\tCAPTURE_RELEASE_OBSERVED\t"+task.id+"\t"+task.operation+"\t"+runtime.session+
                    "\t"+(task.inline?"INLINE_TOKEN_DIRECT_FIELDS_CLEARED_OTHER_OWNERS_UNPROVEN":"CF_DIRECT_FIELDS_CLEARED_SOURCE_STACK_ABSENT_OTHER_OWNERS_UNPROVEN");
            task.releaseAck=runtime.journal.append(task.releaseIntent);
        }
        task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);
        if(!confirmed(task.releaseAck)) { resetWindow(task,"WAIT_CAPTURE_FACT_ACK");return; }
        finishScope(task,tick,task.inline?"INLINE_INVOCATION_EXIT_OBSERVED\tTOKEN_DIRECT_FIELDS_CLEARED_NO_CALLBACK_EFFECT_CLAIM":
                "CF_DIRECT_EXIT_OBSERVED\tCALLBACK_FIELDS_CLEARED_SOURCE_STACK_ABSENT_NO_CALLBACK_EFFECT_CLAIM");
    }
    private void finishScope(Task task,long tick,String evidence)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            checkBridge();
            if(closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"TASK_EVIDENCE_INCOMPLETE");return; }
            if(!task.adoptedInvocations.isEmpty()||((Integer)activeInvocations.invoke(null,task.object))!=0
                    ||((Integer)helperReferences.invoke(null,task.object))!=0) {
                resetWindow(task,"WAIT_INVOCATION_AND_SUBMISSION_RESERVATIONS");return;
            }
            if(!waiterFactsCurrent(task)) { resetWindow(task,"WAIT_WAITER_FACT_ACK");return; }
            // Queue publication and the subject epoch are observed atomically. A worker event
            // invalidates a receipt even before that event reaches the server-side merge.
            if(!events.isEmpty()) { resetWindow(task,"TASK_EVIDENCE_PENDING_MERGE");return; }
            long observed=captureEpoch(task);
            if(!observeStable(task,tick,observed)) { task.state="SUCCESSOR_WINDOW_PENDING";return; }
            // Every task proves its own exit and resources first. Requiring a child's aggregate
            // receipt before starting this local window creates circular waits in joined CF graphs.
            if(task.localRevision!=task.revision||task.localCaptureEpoch!=observed||task.localWindowStart!=task.stableSince
                    ||task.localBackendEpoch!=task.backendEpoch) {
                task.localRecord=PREFIX+"\tLOCAL_FACT\t"+task.id+"\t"+evidence+"\t"+task.revision+
                        "\tcapture="+observed+"\twindow="+task.stableSince+"\tbackend="+task.backendEpoch;
                task.localAck=runtime.journal.append(task.localRecord);
                task.localRevision=task.revision;task.localCaptureEpoch=observed;task.localWindowStart=task.stableSince;
                task.localBackendEpoch=task.backendEpoch;
            }
            task.state="LOCAL_TASK_SCOPE";
        }
        // This is scoped evidence, never a claim that arbitrary callbacks or all resource owners are closed.
    }
    /** One graph pass after local sampling, rather than a complete descendant traversal per task.
     * Cycles cannot manufacture success: every member must have its own current, durable local fact. */
    private void settleScopes(long tick) {
        synchronized(LISTENERS) {
            checkBridge();if(closing||!gap.isEmpty()||!events.isEmpty())return;
            try { if(((Integer)pendingDeliveries.invoke(null))!=0)return; }
            catch(ReflectiveOperationException unavailable) { recordGap("TASK_PUBLICATION_QUERY_UNAVAILABLE");return; }
            Set<Task> active=Collections.newSetFromMap(new IdentityHashMap<>());active.addAll(windows);
            Set<Task> unresolved=Collections.newSetFromMap(new IdentityHashMap<>());
            var pending=new ArrayDeque<Task>();
            for(Task task:windows) {
                boolean ready;
                try { ready=observeBackend(task)&&localFactCurrent(task,tick,captureEpoch(task)); }
                catch(ReflectiveOperationException unavailable) { ready=false; }
                if(ready)for(Task child:task.children)if(!active.contains(child)) { ready=false;break; }
                if(!ready&&unresolved.add(task))pending.addLast(task);
            }
            while(!pending.isEmpty())for(Task parent:pending.removeFirst().parents)
                if(active.contains(parent)&&unresolved.add(parent))pending.addLast(parent);
            int writes=128;
            for(Task task:windows) {
                if(unresolved.contains(task)) {
                    if(localFactCurrent(task,tick,captureEpoch(task)))task.state="SUCCESSORS_PENDING";
                    continue;
                }
                long observed=captureEpoch(task);
                if(task.settledRevision!=task.revision||task.settledCaptureEpoch!=observed||task.settledWindowStart!=task.stableSince
                        ||task.settledBackendEpoch!=task.backendEpoch) {
                    if(writes--<=0) { task.state="WAIT_SCOPE_FACT_BUDGET";continue; }
                    task.factRecord=PREFIX+"\tFACT"+task.localRecord.substring((PREFIX+"\tLOCAL_FACT").length());
                    task.fact=runtime.journal.append(task.factRecord);
                    task.settledRevision=task.revision;task.settledCaptureEpoch=observed;task.settledWindowStart=task.stableSince;
                    task.settledBackendEpoch=task.backendEpoch;
                }
                task.state="QUIESCENT_TASK_SCOPE";
            }
        }
    }
    private boolean index(Map<Object,Set<Task>> index,Object value,Task task) {
        Set<Task> existing=index.get(value);
        if(existing!=null&&existing.contains(task))return true;
        if(indexEdges>=EDGE_LIMIT||existing==null&&index.size()>=LIMIT) { recordGap("CF_DEPENDENCY_INDEX_CAPACITY_OBLIGATION_UNRESOLVED");return false; }
        if(existing==null) { existing=Collections.newSetFromMap(new IdentityHashMap<>());index.put(value,existing); }
        existing.add(task);indexEdges++;return true;
    }
    /** Release our strong graph only after the whole connected scope has actual durable exit facts.
     * Weak provenance survives for late callbacks. Its collection is never used as release evidence. */
    private void retireCoreReferences(long tick) {
        synchronized(LISTENERS) {
            for(Settlement settled:settlements.values())if(settled.released) {
                if(settled.record==null)settled.record=PREFIX+"\tCORE_RELEASED\t"+settled.task+"\t"+settled.operation+"\t"+runtime.session+"\tOWNED_STRONG_GRAPH_CLEARED";
                settled.ack=settled.ack==null?runtime.journal.append(settled.record):runtime.journal.retryRejected(settled.record,settled.ack);
            }
            if(closing||!gap.isEmpty()||!events.isEmpty())return;
            try {
                if(((Integer)pendingDeliveries.invoke(null))!=0)return;
                Map<UUID,List<Task>> groups=new LinkedHashMap<>();
                for(Task task:windows)if(task.operation!=null)groups.computeIfAbsent(task.operation,k->new ArrayList<>()).add(task);
                for(var group:groups.values()) {
                    Set<Task> members=Collections.newSetFromMap(new IdentityHashMap<>());members.addAll(group);
                    boolean ready=true;
                    for(Task task:group)if(!currentFact(task,tick,captureEpoch(task))||!observeBackend(task)
                            ||!members.containsAll(task.children)||!members.containsAll(task.parents)
                            ||((Integer)helperReferences.invoke(null,task.object))!=0) { ready=false;break; }
                    if(!ready)continue;
                    for(Task task:group) {
                        Settlement settled=settlements.computeIfAbsent(task.id,k->new Settlement(task));
                        if(settled.record==null)settled.record=PREFIX+"\tCORE_RELEASE_INTENT\t"+task.id+"\t"+task.operation+"\t"+runtime.session+"\tOWNED_STRONG_GRAPH_ONLY";
                        settled.ack=settled.ack==null?runtime.journal.append(settled.record):runtime.journal.retryRejected(settled.record,settled.ack);
                        ready&=confirmed(settled.ack);
                    }
                    if(!ready)continue;
                    Task leader=group.get(0);
                    if(!guarded(leader,()->{
                        for(Task task:group)if(task.authority!=leader.authority||!currentFact(task,tick,captureEpoch(task)))return;
                        for(Task task:group) {
                            retiredObjects.put(task.object,task.id);tasks.remove(task.object);
                            windows.remove(task);waiting.remove(task);
                            task.scheduler=null;task.queue=null;task.completionSources.clear();task.completionOutputs.clear();
                            task.delegateOwners.clear();task.servicedOwnerQueues.clear();task.forkCaptureEdges.clear();
                            taskEdges-=task.children.size();task.children.clear();task.parents.clear();
                            Settlement settled=settlements.get(task.id);settled.released=true;settled.record=null;settled.ack=null;
                        }
                        for(var index:List.of(producers,dependents,childrenByParent)) {
                            for(var iterator=index.entrySet().iterator();iterator.hasNext();) {
                                var entry=iterator.next();int before=entry.getValue().size();entry.getValue().removeAll(members);
                                indexEdges-=before-entry.getValue().size();if(entry.getValue().isEmpty())iterator.remove();
                            }
                        }
                    }))continue;
                    // No release fact is emitted from this stack while 'group' still retains Task/object.
                    // The following server tick writes metadata-only receipts after this frame returned.
                }
            } catch(ReflectiveOperationException failure) { recordGap("CORE_TASK_REFERENCE_RELEASE_QUERY_UNAVAILABLE"); }
        }
    }
    List<String> remaining(UUID operation) {return remaining(operation,null);}
    List<String> remaining(UUID operation,UUID subject) {
        synchronized(LISTENERS) {
            checkBridge();List<String> out=new ArrayList<>();
            // Installation history is diagnostic, not a veto on a known invocation whose
            // real entry, exit, successors and release are observed below. Actual gaps remain.
            boolean installedHistory=backend.equals("AGENT_LATE_ATTACH_COVERAGE_GAP:PRE_ATTACH_ACTIVITY_NOT_OBSERVED:INSTALLED_FULL")
                    ||backend.equals("AGENT_EXISTING_STATE:INSTALLED_FULL");
            if(bridge==null||!installedHistory&&(backend.startsWith("AGENT_")||backend.startsWith("TASK_AGENT_")||backend.equals("PARTIAL_TASK_BOUNDARIES")))out.add("TASK_BACKEND:"+backend);
            if(!gap.isEmpty())out.add(gap);
            if(subject==null?!events.isEmpty():events.stream().anyMatch(event->event.subjects.contains(subject)))out.add("TASK_EVIDENCE_PENDING_MERGE");
            for(Task task:tasks.values())if(operation.equals(task.operation))out.add("TASK:"+task.id+":"+task.state+":"+task.reason);
            for(Settlement settled:settlements.values())if(operation.equals(settled.operation)) {
                if(!settled.released||!confirmed(settled.ack))out.add("CORE_REFERENCE_RELEASE_ACK:"+settled.task);
                try {
                    long[] state=(long[])boundarySnapshot.invoke(null,(Object)settled.boundaries);
                    if(state[1]!=1||state[0]!=settled.backendEpoch)out.add("RETIRED_TASK_BOUNDARY_CHANGED:"+settled.task);
                } catch(ReflectiveOperationException unavailable) { out.add("RETIRED_TASK_BOUNDARY_UNAVAILABLE"); }
            }
            for(PriorTask prior:priorTasks.values())if(operation.equals(prior.operation)&&!priorReleased.contains(prior.id))
                out.add("PRIOR_TASK_PROCESS_OR_RELEASE_UNPROVEN:"+prior.id);
            return List.copyOf(out);
        }
    }
    private void link(Task parent,Task child,long tick) {
        if(parent==child||parent.children.contains(child))return;
        if(taskEdges>=EDGE_LIMIT) { recordGap("TASK_SUCCESSOR_EDGE_CAPACITY_OBLIGATION_UNRESOLVED");return; }
        taskEdges++;linkObserved(parent,child,tick);
    }
    private static void linkObserved(Task parent,Task child,long tick) {
        if(parent==child||!parent.children.add(child))return;
        child.parents.add(parent);changed(parent,tick);
    }
    /** A late descendant invalidates every affected ancestor, never just the immediate parent. */
    private static void changed(Task root,long tick) {
        var pending=new ArrayDeque<Task>();pending.add(root);
        Set<Task> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty()) {
            Task task=pending.removeFirst();if(!seen.add(task))continue;
            if(task.revision==Long.MAX_VALUE)task.reason="TASK_REVISION_EXHAUSTED";else task.revision++;
            task.lastMutation=tick;
            resetWindow(task,task.state.equals("QUIESCENT_TASK_SCOPE")?"OBSERVED_CHANGED":task.state);
            pending.addAll(task.parents);
        }
    }
    private static void resetWindow(Task task,String state) {
        task.stableSince=-1;task.lastSample=-1;task.windowRevision=-1;task.windowCaptureEpoch=-1;task.windowBackendEpoch=-1;
        task.state=state;
        // The old fact/ACK is retained as history. It cannot match the new current window.
    }
    private static boolean observeStable(Task task,long tick,long captureEpoch) {
        if(task.stableSince<0||task.lastSample<0||tick<task.lastSample||tick-task.lastSample>MAX_SAMPLE_GAP
                ||task.windowRevision!=task.revision||task.windowCaptureEpoch!=captureEpoch||task.windowBackendEpoch!=task.backendEpoch) {
            task.stableSince=tick;task.windowRevision=task.revision;task.windowCaptureEpoch=captureEpoch;
            task.windowBackendEpoch=task.backendEpoch;
        }
        task.lastSample=tick;
        return tick-task.stableSince>=STABLE_TICKS;
    }
    private static boolean localCurrent(Task task,long tick,long captureEpoch) {
        return task.reason.isEmpty()&&task.authority!=null
                &&task.coverageConfirmed&&task.windowBackendEpoch==task.backendEpoch
                &&task.authority.terminal&&task.qualification==task.authority.generation
                &&(task.subjects.size()==1||task.delegated&&!task.forkJoin&&!exactFuture(task.object))&&task.subjects.contains(task.authority.id)
                &&task.executions.isEmpty()&&task.dispatches.isEmpty()&&task.submissions.isEmpty()
                &&task.workerReferences.isEmpty()&&task.adoptedInvocations.isEmpty()&&task.waiters.isEmpty()&&!task.waiterCoverageGap&&waiterFactsCurrent(task)&&task.callableReleased
                &&confirmed(task.ack)&&confirmed(task.releaseAck)&&(task.inline||task.forkJoin||task.result||task.delegated||task.queue!=null)
                &&(task.result|| (task.entered?task.exitObserved:task.queueRemoved||task.dispatchExitObserved||task.forkJoin&&task.blockedEntryObserved))
                &&task.stableSince>=0&&task.lastSample>=0&&tick>=task.lastSample
                &&tick-task.lastSample<=MAX_SAMPLE_GAP&&task.lastSample-task.stableSince>=STABLE_TICKS
                &&task.windowRevision==task.revision&&task.windowCaptureEpoch==captureEpoch;
    }
    private static boolean currentFact(Task task,long tick,long captureEpoch) {
        return task.state.equals("QUIESCENT_TASK_SCOPE")&&localCurrent(task,tick,captureEpoch)&&task.settledRevision==task.revision
                &&task.settledCaptureEpoch==captureEpoch&&task.settledWindowStart==task.stableSince
                &&task.settledBackendEpoch==task.backendEpoch&&confirmed(task.fact);
    }
    private static boolean localFactCurrent(Task task,long tick,long observed) {
        return localCurrent(task,tick,observed)&&task.localRevision==task.revision&&task.localCaptureEpoch==observed
                &&task.localWindowStart==task.stableSince&&task.localBackendEpoch==task.backendEpoch&&confirmed(task.localAck);
    }
    /** Query-local only. No result from this graph walk authorizes a resource mutation. */
    private Set<Task> currentScopes(Collection<Task> roots,long tick) {
        Set<Task> reachable=Collections.newSetFromMap(new IdentityHashMap<>());
        if(closing||!gap.isEmpty())return reachable;
        Map<Task,Set<Task>> parents=new IdentityHashMap<>();
        var pending=new ArrayDeque<Task>(roots);
        while(!pending.isEmpty()) {
            Task task=pending.removeFirst();if(!reachable.add(task))continue;
            for(Task child:task.children) {
                parents.computeIfAbsent(child,key->Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
                pending.addLast(child);
            }
        }
        Set<Task> invalid=Collections.newSetFromMap(new IdentityHashMap<>());
        long epoch=-1;
        for(Task task:reachable) {
            boolean observed=false,ready;
            try { observed=observeBackend(task);ready=observed&&localFactCurrent(task,tick,captureEpoch(task)); }
            catch(ReflectiveOperationException unavailable) { ready=false; }
            // A failed observation can leave an old epoch (or -1). It invalidates only
            // this task and its ancestors, not independent roots in the same query.
            if(observed) {
                if(epoch<0)epoch=task.backendEpoch;
                else if(epoch!=task.backendEpoch)return Set.of(); // Actual mixed-version observations are stale.
            }
            if(!ready&&invalid.add(task))pending.addLast(task);
        }
        while(!pending.isEmpty())for(Task parent:parents.getOrDefault(pending.removeFirst(),Set.of()))
            if(invalid.add(parent))pending.addLast(parent);
        reachable.removeAll(invalid);return reachable;
    }
    private static boolean confirmed(CompletableFuture<Void> future) { return future!=null&&future.isDone()&&!future.isCompletedExceptionally()&&!future.isCancelled(); }
    private static boolean exactFuture(Object value) {
        Class<?> type=value.getClass();String name=type.getName();
        return type.getClassLoader()==null&&(name.equals("java.util.concurrent.FutureTask")
                ||name.equals("java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask"));
    }
    private static boolean exactCompletion(Object value) {
        if(value==null||value.getClass().getClassLoader()!=null)return false;
        String name=value.getClass().getName(),prefix="java.util.concurrent.CompletableFuture$";
        return name.startsWith(prefix)&&CF_COMPLETIONS.contains(name.substring(prefix.length()));
    }
    private static boolean completionReleased(Task task)throws ReflectiveOperationException {
        // A synchronous JDK completion has no executor admission. Async queues/worker locals need separate evidence.
        if(!task.completion||!task.creationObserved||!task.completionModeObserved||task.asynchronousCompletion
                ||task.scheduler!=null||!task.submissions.isEmpty()||task.completionSources.isEmpty())return false;
        for(Class<?> type=task.definition;type!=null&&type.getName().startsWith("java.util.concurrent.CompletableFuture$");type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                if(!field.trySetAccessible()||field.get(task.object)!=null)return false;
            }
        }
        Field stack=CompletableFuture.class.getDeclaredField("stack");
        Class<?> completion=Class.forName("java.util.concurrent.CompletableFuture$Completion",false,null);
        Field next=completion.getDeclaredField("next");
        if(!stack.trySetAccessible()||!next.trySetAccessible())return false;
        for(Object source:task.completionSources) {
            Class<?> type=source.getClass();
            if(type!=CompletableFuture.class&&!(type.getClassLoader()==null&&type.getName().equals("java.util.concurrent.CompletableFuture$MinimalStage")))return false;
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Object node=stack.get(source);node!=null;node=next.get(node)) {
                if(node==task.object||!completion.isInstance(node)||seen.size()>=4096||!seen.add(node))return false;
            }
        }
        return true;
    }
    private static Object queue(Object scheduler)throws ReflectiveOperationException {
        if(!(scheduler instanceof ThreadPoolExecutor))return null;
        Field field=ThreadPoolExecutor.class.getDeclaredField("workQueue");
        return field.trySetAccessible()?field.get(scheduler):null;
    }
    private static boolean callableReleased(Object task)throws ReflectiveOperationException {
        if(!exactFuture(task))return false;
        Field field=FutureTask.class.getDeclaredField("callable");
        return field.trySetAccessible()&&field.get(task)==null;
    }
    private static boolean supportedQueue(Object queue) {
        Class<?> type=queue.getClass();return type.getClassLoader()==null&&Set.of("java.util.concurrent.ArrayBlockingQueue",
                "java.util.concurrent.LinkedBlockingQueue","java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue").contains(type.getName());
    }
    List<String> status(UUID operation) {
        synchronized(LISTENERS) {
            checkBridge();long now=Integer.toUnsignedLong(runtime.server.getTickCount());
            var out=new ArrayList<String>();out.add("TASK_BACKEND:"+backend);
            if(!events.isEmpty())out.add("CURRENT_TASK_EVIDENCE_PENDING_SERVER_MERGE");
            if(!gap.isEmpty())out.add("TASK_GAP:"+gap);
            var selected=new ArrayList<Task>();
            for(Task task:tasks.values())if(operation.equals(task.operation))selected.add(task);
            Set<Task> currentScopes=currentScopes(selected,now);
            for(Task task:selected) {
                boolean current=currentScopes.contains(task);
                String visible=task.state.equals("QUIESCENT_TASK_SCOPE")&&!current?"QUIESCENCE_STALE":task.state;
                out.add(task.id+":"+visible+":"+task.reason+":kind="+(task.inline?"CF_STAGE_INVOCATION":task.result?"CF_RESULT":task.completion?"CF_COMPLETION":task.delegated?"DELEGATED_INVOCATION":"TASK")+":active="+task.executions.size()+
                        ":callbacks="+task.dispatches.size()+":children="+task.children.size()+
                        ":currentScope="+current+":factDurable="+(current&&currentFact(task,now,captureEpoch(task)))+
                        ":historicalFactDurable="+confirmed(task.fact)+":admissionFenced="+task.admissionFenced+
                        ":startPrecludedDurable="+confirmed(task.preclusionAck)+":callbackFieldsCleared="+task.callbackFieldsReleased+
                        ":sharedForkQueueReceipts="+(task.queueReceiptsDurable+task.queueRecords.values().stream().filter(RecoveryTasks::confirmed).count())+
                        ":admissionRegistryReleased="+(task.admissionReleased&&confirmed(task.fenceReleaseAck))+
                        ":dependentResultPending="+task.dependentResultPending+":ownerQueueRequests="+task.ownedQueueRequests.size()+
                        ":adoptedInvocations="+task.adoptedInvocations.size()+":waiters="+task.waiters.size()+
                        ":waiterExitsDurable="+(waiterFactsCurrent(task)?task.waiterExitsRecorded:0)+
                        ":localFactCurrent="+localFactCurrent(task,now,captureEpoch(task))+":gate="+task.gateReason+
                        ":definition="+task.definition.getName()+":scheduler="+(task.scheduler==null?"UNOBSERVED":task.scheduler.getClass().getName()));
            }
            for(Settlement settled:settlements.values())if(operation.equals(settled.operation))out.add(settled.task+":CORE_REFERENCES_RELEASED="+(settled.released&&confirmed(settled.ack)));
            out.addAll(remaining(operation));return List.copyOf(out);
        }
    }
    String dependencies(UUID operation,UUID subject) {
        synchronized(LISTENERS) {
            StringBuilder key=new StringBuilder(gap).append(':').append(captureEpochs.getOrDefault(subject,0L));
            var rows=new java.util.TreeSet<String>();
            for(Task task:tasks.values())if(operation.equals(task.operation)||task.subjects.contains(subject))
                rows.add(task.id+":"+task.revision+":"+task.operation+":"+task.qualification+":"+task.state+":"+task.children.size());
            for(String row:rows)key.append('|').append(row);
            return key.toString();
        }
    }
    long epoch() { return changes.get(); }
    @Override public void close()throws IOException {
        closing=true;
        if(detached) {
            retirementFactAck=runtime.journal.retryRejected(retirementFact,retirementFactAck);
            if(!confirmed(retirementFactAck))throw new IOException("TASK_OBSERVER_RELEASE_FACT_ACK_PENDING");
            return;
        }
        // Ownership of this merge transfers from the stopped server to its retirement worker.
        drain(System.nanoTime()/50_000_000L);
        synchronized(LISTENERS) {
            if(!events.isEmpty())throw new IOException("TASK_RETIREMENT_EVENTS_PENDING");
            try {
                if(bridge!=null&&((Integer)pendingDeliveries.invoke(null))!=0)throw new IOException("TASK_CAPTURE_PUBLICATION_IN_FLIGHT");
            } catch(ReflectiveOperationException failure) { throw new IOException("TASK_CAPTURE_PUBLICATION_QUERY_UNAVAILABLE",failure); }
            checkBridge();
            for(Task task:tasks.values()) {
                try { pollAdoptedInvocations(task); }
                catch(ReflectiveOperationException failure) { throw new IOException("SOURCE_INVOCATION_RETIREMENT_UNAVAILABLE",failure); }
                if(!task.adoptedInvocations.isEmpty())throw new IOException("SOURCE_INVOCATIONS_PENDING:"+task.id);
                flushWaiterFacts(task);
                if(!waiterFactsCurrent(task))throw new IOException("TASK_WAITER_FACT_ACK_PENDING:"+task.id);
                for(Object request:List.copyOf(task.ownedQueueRequests.values())) {
                    try { cancelOwnedQueueRelease.invoke(null,SINK,request); }
                    catch(ReflectiveOperationException failure) { throw new IOException("OWNER_QUEUE_REQUEST_RETIREMENT_UNAVAILABLE",failure); }
                }
                if(!task.ownedQueueRequests.isEmpty())throw new IOException("OWNER_QUEUE_REQUEST_RETIREMENT_PENDING:"+task.id);
                task.preclusionAck=runtime.journal.retryRejected(task.preclusionRecord,task.preclusionAck);
                task.admissionAck=runtime.journal.retryRejected(task.admissionRecord,task.admissionAck);
                task.callbackReleaseAck=runtime.journal.retryRejected(task.callbackReleaseRecord,task.callbackReleaseAck);
                task.fenceReleaseAck=runtime.journal.retryRejected(task.fenceReleaseRecord,task.fenceReleaseAck);
                if(task.admissionFenced&&!task.admissionReleased&&task.completion) {
                    try {
                        if(completionFieldsAbsent(task,true)&&completionStacksAbsent(task))releaseAdmissionResource(task,true);
                    } catch(ReflectiveOperationException failure) { throw new IOException("TASK_FENCE_RETIREMENT_QUERY_UNAVAILABLE",failure); }
                }
                if(task.admissionFenced&&!task.admissionReleased)throw new IOException("TASK_ADMISSION_FENCE_RESOURCE_PENDING:"+task.id);
                if(task.admissionReleased&&!confirmed(task.fenceReleaseAck))throw new IOException("TASK_ADMISSION_RELEASE_ACK_PENDING:"+task.id);
                if(task.preclusionRecord!=null&&!confirmed(task.preclusionAck)||task.admissionRecord!=null&&!confirmed(task.admissionAck)
                        ||task.callbackReleaseRecord!=null&&!confirmed(task.callbackReleaseAck))throw new IOException("TASK_PARTIAL_EFFECT_FACT_ACK_PENDING:"+task.id);
                refreshQueueReceipts(task);
                if(!task.queueRecords.isEmpty())throw new IOException("TASK_QUEUE_RELEASE_ACK_PENDING:"+task.id);
                if(!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()||!task.workerReferences.isEmpty())
                    throw new IOException("TASK_ACTUAL_EXIT_PENDING:"+task.id);
                try { if(((Integer)activeInvocations.invoke(null,task.object))!=0)throw new IOException("TASK_BOOTSTRAP_INVOCATIONS_PENDING:"+task.id); }
                catch(ReflectiveOperationException failure) { throw new IOException("TASK_BOOTSTRAP_INVOCATION_QUERY_UNAVAILABLE",failure); }
                try { if(!waitersReleased(task))throw new IOException("TASK_WAITER_EXIT_OR_REFERENCE_PENDING:"+task.id); }
                catch(ReflectiveOperationException failure) { throw new IOException("TASK_WAITER_QUERY_UNAVAILABLE",failure); }
                if(task.result) {
                    try { if(!observeBackend(task)||readField(CompletableFuture.class,"result",task.object)==null)throw new IOException("TASK_RESULT_PENDING:"+task.id); }
                    catch(ReflectiveOperationException failure) { throw new IOException("TASK_RESULT_QUERY_UNAVAILABLE",failure); }
                    continue;
                }
                if(task.inline||task.completion) {
                    try {
                        boolean released;
                        if(task.inline)released=task.entered&&task.exitObserved&&Boolean.TRUE.equals(inlineClosed.invoke(null,task.object));
                        else if(task.admissionReleased) {
                            refreshDependentResult(task);
                            released=!task.dependentResultPending&&completionOwnerReleased(task);
                        } else released=task.entered&&task.exitObserved&&completionReleased(task);
                        if(!observeBackend(task)||!released)
                            throw new IOException("INLINE_RETIREMENT_EXIT_OR_CAPTURE_PENDING:"+task.id);
                    } catch(ReflectiveOperationException failure) { throw new IOException("INLINE_RETIREMENT_QUERY_UNAVAILABLE",failure); }
                    continue;
                }
                if(task.delegated&&!task.forkJoin&&!exactFuture(task.object)) {
                    if(!task.entered||!task.exitObserved||!task.adoptedInvocations.isEmpty())throw new IOException("DELEGATE_INVOCATION_RETIREMENT_PENDING:"+task.id);
                    continue;
                }
                if(task.forkJoin&&!task.completion) {
                    try {
                        if(!task.admissionReleased||!forkCapturesAbsent(task)||!forkQueuesAbsent(task)
                                ||((Integer)helperReferences.invoke(null,task.object))!=0)
                            throw new IOException("FORKJOIN_RETIREMENT_REFERENCES_PENDING:"+task.id);
                    } catch(ReflectiveOperationException failure) { throw new IOException("FORKJOIN_RETIREMENT_QUERY_UNAVAILABLE",failure); }
                    continue;
                }
                if(!exactFuture(task.object))throw new IOException("TASK_RETIREMENT_SEMANTICS_UNSUPPORTED:"+task.id);
                try { if(!observeBackend(task)||!callableReleased(task.object))throw new IOException("TASK_RETIREMENT_COVERAGE_OR_CALLABLE_UNRESOLVED:"+task.id); }
                catch(ReflectiveOperationException failure) { throw new IOException("TASK_RETIREMENT_COVERAGE_UNAVAILABLE",failure); }
                Future<?> future=(Future<?>)task.object;
                if(!future.isDone()||(task.entered?!task.exitObserved:!task.queueRemoved&&!task.dispatchExitObserved))
                    throw new IOException("TASK_EXIT_OR_START_PRECLUSION_PENDING:"+task.id);
            }
            if(!tasks.isEmpty()&&!gap.isEmpty())throw new IOException("TASK_RETIREMENT_GAP:"+gap);
            int writes=32;boolean receiptsReady=true;
            for(Task task:tasks.values()) {
                if(task.retiredRevision!=task.revision) {
                    if(writes--<=0) { receiptsReady=false;continue; }
                    task.retirementRecord=PREFIX+"\tRETIREMENT_OBSERVED\t"+task.id+"\t"+runtime.session+
                            "\t"+(task.result?"RESULT_AND_WAITERS_OBSERVED":task.entered?"EXIT_OBSERVED":task.dispatchExitObserved?"DISPATCH_EXIT_OBSERVED":"START_PRECLUDED")+"\trevision="+task.revision;
                    task.retirementAck=runtime.journal.append(task.retirementRecord);task.retiredRevision=task.revision;
                } else task.retirementAck=runtime.journal.retryRejected(task.retirementRecord,task.retirementAck);
                if(!confirmed(task.retirementAck))receiptsReady=false;
            }
            if(!receiptsReady)throw new IOException("TASK_RETIREMENT_FACTS_ACK_PENDING");
            long epoch=changes.get();
            if(retirementAck==null||retirementEpoch!=epoch) {
                // This intent authorizes releasing our own observer references, never a shared pool or task.
                retirementEpoch=epoch;
                retirementIntent=PREFIX+"\tOBSERVER_RELEASE_INTENT\t"+runtime.session+"\t"+epoch+"\tcount="+tasks.size();
                retirementAck=runtime.journal.append(retirementIntent);
            } else retirementAck=runtime.journal.retryRejected(retirementIntent,retirementAck);
            if(!confirmed(retirementAck))throw new IOException("TASK_OBSERVER_RELEASE_INTENT_ACK_PENDING");
            // Publication is excluded here: a late ENTER/SUBMIT before this point changes the epoch above.
            LISTENERS.remove(this);
            if(LISTENERS.isEmpty()&&bridge!=null) {
                try {
                    if(uninstall==null||!Boolean.TRUE.equals(uninstall.invoke(null,SINK))) {
                        LISTENERS.add(this);throw new IOException("TASK_OBSERVER_DETACH_UNCONFIRMED");
                    }
                } catch(ReflectiveOperationException|RuntimeException unavailable) {
                    LISTENERS.add(this);throw new IOException("TASK_OBSERVER_DETACH_UNCONFIRMED",unavailable);
                }
                bridge=null;
            }
            detached=true;
            // Every collection is owned by this observer. External queues, delegates and pools are untouched.
            events.clear();tasks.clear();producers.clear();dependents.clear();childrenByParent.clear();origins.clear();workerBindings.clear();captureEpochs.clear();
            windows.clear();waiting.clear();
            retirementFact=PREFIX+"\tOBSERVER_RELEASED\t"+runtime.session+"\t"+epoch;
            retirementFactAck=runtime.journal.append(retirementFact);
            if(!confirmed(retirementFactAck))throw new IOException("TASK_OBSERVER_RELEASE_FACT_ACK_PENDING");
        }
    }
}
