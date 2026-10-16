package dev.ronova.pro;

import dev.ronova.pro.persistence.JournalLineage;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Workers retain actual observations; bounded server batches own qualification and disposal. */
final class RecoveryTasks implements AutoCloseable {
    private static final Set<RecoveryTasks> LISTENERS=Collections.newSetFromMap(new IdentityHashMap<>());
    private record OwnerRegistration(RecoveryTasks owner,UUID session,Object registration,long epoch) { }
    private static volatile OwnerRegistration[] publishedOwners=new OwnerRegistration[0];
    private static final Object LOSS_ROUTING=new Object();
    private static boolean routingLosses;
    private static int capturePublications;
    private static Object bridgeRegistration;
    private static long bridgeRegistrationEpoch=-1;
    private static String unknownCaptureGap="";
    private static final Consumer<Object[]> SINK=RecoveryTasks::receive;
    private static Method currentTask,currentExecution,delivery,coverage,observerMatches,droppedEvents,captureState,acknowledgeCaptureLoss,uninstall,boundarySnapshot,boundaryCapabilities,withBoundaries,pendingDeliveries,inlineDetails,inlineClosed,
            fenceTask,admissionState,releaseFence,requestOwnedQueueRelease,cancelOwnedQueueRelease,wakeOwnedQueue,waiterCount,waiterNodeObserved,activeInvocations,sourceOrigin,invocationState,submissionClosed,helperReferences,
            forkQueueSnapshot,forkQueueEpoch;
    private static final String PREFIX="TASK/6";
    private static final Set<String> CF_COMPLETIONS=Set.of("UniApply","UniAccept","UniRun","UniWhenComplete","UniHandle",
            "UniExceptionally","UniComposeExceptionally","UniCompose","UniRelay","BiApply","BiAccept","BiRun",
            "BiRelay","OrApply","OrAccept","OrRun","CoCompletion","AsyncSupply","AsyncRun","AnyOf");
    private static Class<?> bridge;
    private static volatile ResourceMethods cachedResourceMethods;
    private static final class ResourceMethods {
        final Class<?> definition;
        final Method retireTimer,timerEnded,schedulerDisposition,retireScheduler,schedulerEnded,realScheduler,drainAccepted,timerTaskRetired;
        ResourceMethods(Class<?> definition)throws ReflectiveOperationException {
            this.definition=definition;
            retireTimer=definition.getMethod("retireTimer",Object.class,Module[].class);timerEnded=definition.getMethod("timerResourceEnded",Object.class,Module[].class);
            schedulerDisposition=definition.getMethod("schedulerDisposition",Object.class,Module[].class);retireScheduler=definition.getMethod("retireScheduler",Object.class,Module[].class);
            schedulerEnded=definition.getMethod("schedulerResourceEnded",Object.class);realScheduler=definition.getMethod("realScheduler",Object.class);
            drainAccepted=definition.getMethod("schedulerDrainAccepted",Object.class,Object.class);timerTaskRetired=definition.getMethod("timerTaskRetired",Object.class);
        }
    }
    private static ResourceMethods resourceMethods()throws ReflectiveOperationException {
        ResourceMethods cached=cachedResourceMethods;
        if(cached!=null&&(bridge==null||cached.definition==bridge))return cached;
        Class<?> actual=bridge==null?Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null):bridge;
        ResourceMethods resolved=new ResourceMethods(actual);cachedResourceMethods=resolved;return resolved;
    }
    private static volatile String backend="TASK_AGENT_NOT_ATTACHED";
    private static final int DRAIN=256;
    /** Definition-local metadata only; actual field values, owners and policy are still read on every event. */
    private static final ClassValue<Field[]> CAPTURE_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            var result=new ArrayList<Field>();
            for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass())
                for(Field field:current.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
                    try {field.trySetAccessible();}catch(RuntimeException unavailable) { /* Retry at the real observation. */ }
                    result.add(field);
                }
            return result.toArray(Field[]::new);
        }
    };
    /** Only platform operands which the JDK clears before a later server tick. */
    private static final ClassValue<Field[]> TRANSIENT_TASK_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            var result=new ArrayList<Field>();
            for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass()) {
                if(!knownDelegate(current))continue;
                for(Field field:current.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())
                        &&!field.getType().isPrimitive()&&(jdkDelegateField(current,field)
                        ||current.getName().startsWith("java.util.concurrent.CompletableFuture$")&&field.getName().equals("executor"))) {
                    try {field.trySetAccessible();}catch(RuntimeException unavailable) { /* Retried at the real operand. */ }
                    result.add(field);
                }
            }
            return result.toArray(Field[]::new);
        }
    };
    /** Shallow callback operands only; no wrapper/source graph is followed on the execution thread. */
    private static final ClassValue<Field[]> CALLBACK_TASK_FIELDS=new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            if(type.getClassLoader()==null||type.isArray()||type.isEnum()||type==Class.class
                    ||Thread.class.isAssignableFrom(type)||Executor.class.isAssignableFrom(type))return new Field[0];
            var result=new ArrayList<Field>();
            for(Field field:CAPTURE_FIELDS.get(type))if(field.getDeclaringClass().getClassLoader()!=null)result.add(field);
            return result.toArray(Field[]::new);
        }
    };
    private final ProRuntime runtime;
    // Only clone metadata uses this monitor; source callbacks may already hold TAGS/GRAPH.
    private final Object captureLock=new Object();
    private OwnerRegistration ownerRegistration;
    // Reserved before leaving the publication gate, including Core's direct observations.
    private int capturesInFlight;
    private final ConcurrentLinkedQueue<Capture> events=new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<PendingCapture> pendingCaptures=new ConcurrentLinkedQueue<>();
    // Exact root identities only. Publication and removal share LISTENERS with destructive gates.
    private final Map<Object,Integer> pendingRoots=new IdentityHashMap<>();
    private final Map<Object,UUID> origins=Collections.synchronizedMap(new WeakIdentityMap<>());
    // A first causal origin remains useful for task lineage, but a conflicting
    // observation cannot also serve as exclusive ownership for destructive graph writes.
    private final Set<Object> sharedOrigins=Collections.newSetFromMap(new WeakIdentityMap<>());
    private final Map<Object,Task> tasks=new IdentityHashMap<>();
    private final Map<UUID,Set<Task>> operationTasks=new HashMap<>();
    private final Map<Object,Set<Task>> producers=new IdentityHashMap<>();
    private final Map<Object,Set<Task>> dependents=new IdentityHashMap<>();
    private final Map<Object,Set<Task>> childrenByParent=new IdentityHashMap<>();
    private final Map<Object,Set<UUID>> workerBindings=Collections.synchronizedMap(new WeakIdentityMap<>());
    private final Map<Object,UUID> retiredObjects=new WeakIdentityMap<>();
    private final Map<Object,ProRuntime.Subject> drainedTasks=new WeakIdentityMap<>();
    private final Map<UUID,Set<Object>> retiringSchedulers=new HashMap<>();
    private static final Object SCHEDULER_DRAIN=new Object();
    private record CloneLink(java.lang.ref.WeakReference<net.minecraft.world.entity.Entity> result,java.lang.ref.WeakReference<Class<?>> holder) { }
    private final Map<Object,List<CloneLink>> clones=new WeakIdentityMap<>();
    private final Map<UUID,Settlement> settlements=new LinkedHashMap<>();
    private record PriorTask(UUID id,UUID operation,UUID session) { }
    private final Map<UUID,PriorTask> priorTasks=new LinkedHashMap<>();
    private final ArrayDeque<PriorTask> priorPending=new ArrayDeque<>();
    private final Set<UUID> priorReleased=new HashSet<>(),priorObserversReleased=new HashSet<>();
    private final Map<UUID,CompletableFuture<Void>> priorProcessFacts=new HashMap<>();
    private static final class Settlement {
        final UUID task,operation,subject;
        final long generation;
        String record;
        CompletableFuture<Void> ack;
        boolean released;
        Settlement(Task task) {
            this.task=task.id;operation=task.operation;subject=task.authority.id;generation=task.qualification;
        }
    }
    private final AtomicLong changes=new AtomicLong();
    // Accessed only under LISTENERS, the same short publication gate used by receive().
    private final Map<UUID,Long> captureEpochs=new HashMap<>();
    private record CaptureGap(Object registration,long epoch,UUID session,Set<UUID> subjects,String reason) { }
    // Missing event obligations retain their exact task until this observer can retire.
    private final Map<Object,CaptureGap> captureGaps=new IdentityHashMap<>();
    private volatile String gap="",transientIssue="";
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
    // The bounded drain may end in the middle of a Bootstrap delivery. Retry its exact
    // candidates after publication, even when no later task event arrives.
    private final ArrayDeque<Task> policyPending=new ArrayDeque<>();
    private record Capture(int kind,Object task,Object scheduler,Object parent,Object invocation,
            ClassLoader loader,Set<UUID> subjects,long dropped,Object dependency,Object otherDependency,Object output,
            boolean inline,Class<?> callbackDefinition,boolean hasExecutor,UUID session,Object registration,long registrationEpoch,long unknownLoss) { }
    private record TaskOperands(Object object,Field[] fields,Object[] values,boolean complete,boolean callback) {
        Object value(String name) {
            for(int i=0;i<fields.length;i++)if(fields[i].getName().equals(name))return values[i];
            return null;
        }
        int index(Field field) {
            for(int i=0;i<fields.length;i++)if(fields[i].equals(field))return i;
            return -1;
        }
    }
    private record CaptureRoots(Object[] inline,TaskOperands task,TaskOperands base,List<TaskOperands> callbacks) {
        TaskOperands operands(Object object) {
            if(task!=null&&task.object==object)return task;if(base!=null&&base.object==object)return base;
            for(TaskOperands retained:callbacks)if(retained.object==object)return retained;return null;
        }
        TaskOperands retained(Object object,Field field) {
            if(task!=null&&task.object==object&&task.index(field)>=0)return task;
            if(base!=null&&base.object==object&&base.index(field)>=0)return base;
            for(TaskOperands retained:callbacks)if(retained.object==object&&retained.index(field)>=0)return retained;return null;
        }
        boolean callback(Object object) {for(TaskOperands retained:callbacks)if(retained.object==object&&retained.callback)return true;return false;}
        boolean complete() {
            if(task!=null&&!task.complete||base!=null&&!base.complete)return false;
            for(TaskOperands retained:callbacks)if(!retained.complete)return false;return true;
        }
    }
    /** A failed resolution becomes unknown only after every actual owner has tried this delivery. */
    private static final class CaptureDelivery {
        int remaining;
        boolean located,failed;
        CaptureDelivery(int remaining) {this.remaining=remaining;}
        void resolved(boolean located,boolean failed) {
            boolean unknown;
            synchronized(this) {
                this.located|=located;this.failed|=failed;
                unknown=--remaining==0&&this.failed&&!this.located;
            }
            if(unknown)unknownCapture("TASK_CAPTURE_SOURCE_UNLOCATABLE");
        }
    }
    private record PendingCapture(Capture event,OwnerRegistration owner,CaptureRoots roots,
            Object[] identities,CaptureDelivery delivery,String failure) { }
    /** Handles and actual resource identities only. No queue absence or completion result is cached here. */
    private record ResourceBinding(Object scheduler,Field queueField,Object queue,List<Field> lockFields,
            List<java.util.concurrent.locks.ReentrantLock> locks,Field scheduledOwner,Field scheduledDelegate,
            ProRuntime.Subject authority,ProRuntime.Subject scope,long qualification,long generation,Object backendImage,long backendEpoch) {
        boolean current(Task task)throws IllegalAccessException {
            if(task.scheduler!=scheduler||queueField!=null&&queueField.get(scheduler)!=queue)return false;
            if(scheduledOwner!=null&&(scheduledOwner.get(task.object)!=scheduler||scheduledDelegate.get(task.object)!=task.object))return false;
            for(int i=0;i<lockFields.size();i++)if(lockFields.get(i).get(queue)!=locks.get(i))return false;
            return true;
        }
        boolean sameScope(Task task,ProRuntime.Subject actual) {
            return task.authority==authority&&actual==scope&&task.qualification==qualification
                    &&(actual==null?-1:actual.generation)==generation&&task.backendImage==backendImage&&task.backendEpoch==backendEpoch;
        }
    }
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
        final Map<Object,CompletionCursor> completionReleaseCursors=new IdentityHashMap<>();
        final Map<Object,CompletionStackImage> completionReadbacks=new IdentityHashMap<>();
        boolean creationObserved,asynchronousCompletion,completionModeObserved;
        boolean admissionFenced,admissionReleased,blockedEntryObserved,callbackFieldsReleased;
        /** A policy-only fence is owned here until adopted by disposal or explicitly released. */
        boolean policyFenced,policyQueued;
        ProRuntime.Subject policyAuthority;
        boolean dependentResultPending;
        boolean ownedResult,resultCancellationAttempted;
        boolean resultDrainPending;
        Object resultCancellationValue;
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
        long forkQueueReadbackEpoch=-1;
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
        ResourceBinding resources;
        ProRuntime.Subject authority;
        UUID operation;
        long qualification,retryAt,revision,lastMutation;
        long backendEpoch=-1;
        Object backendImage;
        List<String> backendRequirements=List.of();
        boolean coverageConfirmed,backendImageReady;
        boolean callableReleased;
        String[] requiredBoundaries=new String[0];
        String state="OBSERVED",reason="",gateReason="",intent,releaseIntent;
        CompletableFuture<Void> ack,releaseAck;
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
            attach();ownerRegistration=new OwnerRegistration(this,runtime.session,bridgeRegistration,bridgeRegistrationEpoch);
            LISTENERS.add(this);publishOwners();
        }
        List<String> history=runtime.journal.history();
        for(String line:history)if(line.startsWith("TASK/")) {
            if(!line.startsWith("TASK/1\t")&&!line.startsWith("TASK/2\t")&&!line.startsWith("TASK/3\t")&&!line.startsWith("TASK/4\t")&&!line.startsWith("TASK/5\t")&&!line.startsWith(PREFIX+"\t")) {
                runtime.journal.refuseWrites("FUTURE_TASK_DESCRIPTOR_QUERY_ONLY");continue;
            }
            if(!line.startsWith("TASK/1\t")&&!validHistory(line)) {
                runtime.journal.refuseWrites("INVALID_TASK_DESCRIPTOR_QUERY_ONLY");continue;
            }
            try {
                String[] row=line.split("\t",-1);
                if(row[1].equals("INTENT")&&row.length==10) {
                    PriorTask prior=new PriorTask(UUID.fromString(row[2]),UUID.fromString(row[3]),UUID.fromString(row[6]));
                    PriorTask previous=priorTasks.putIfAbsent(prior.id,prior);
                    if(previous!=null&&!previous.equals(prior))runtime.journal.refuseWrites("CONFLICTING_TASK_HISTORY_QUERY_ONLY");
                } else if(row[1].equals("OBSERVER_RELEASED"))priorObserversReleased.add(UUID.fromString(row[2]));
                else if(row[0].equals("TASK/1"))gap="LEGACY_TASK_OBLIGATIONS_QUERY_ONLY";
            } catch(RuntimeException invalid) { runtime.journal.refuseWrites("INVALID_TASK_HISTORY_QUERY_ONLY"); }
        }
        for(String line:history)if(line.startsWith("TASK/")&&validHistory(line)) {
            String[] row=line.split("\t",-1);
            if(!row[1].equals("CORE_RELEASED")&&!row[1].equals("PRIOR_PROCESS_ENDED"))continue;
            try {
                PriorTask prior=priorTasks.get(UUID.fromString(row[2]));
                // A process-end fact closes only the original process's reference obligation.
                // CORE_RELEASED is emitted by the same session as the task's INTENT.
                if(prior==null||!prior.operation.equals(UUID.fromString(row[3]))||!prior.session.equals(UUID.fromString(row[4]))) {
                    runtime.journal.refuseWrites("TASK_RELEASE_IDENTITY_MISMATCH_QUERY_ONLY");continue;
                }
                priorReleased.add(prior.id);
            } catch(RuntimeException invalid) { runtime.journal.refuseWrites("INVALID_TASK_HISTORY_QUERY_ONLY"); }
        }
        for(PriorTask prior:priorTasks.values())if(!priorReleased.contains(prior.id))priorPending.addLast(prior);
    }
    private static String encode(String value) {
        return JournalLineage.encodeText(value);
    }
    private String intent(Task task) {
        return PREFIX+"\tINTENT\t"+task.id+"\t"+task.operation+"\t"+task.authority.id+"\t"+task.qualification+
                "\t"+runtime.session+"\t"+(task.inline?"CF_STAGE_INVOCATION":task.result?"CF_RESULT_OBSERVATION":task.completion?"CF_COMPLETION_OBSERVATION":task.forkJoin?"FORKJOIN_ADMISSION":task.delegated&&!exactFuture(task.object)?"DELEGATED_INVOCATION":exactFuture(task.object)?"EXACT_FUTURE":"PLAIN_QUEUE_DRAIN")+"\t"+encode(task.definition.getName())+
                "\t"+encode(task.callbackDefinition==null?"":task.callbackDefinition.getName());
    }
    private static boolean encoded(String value) {
        return encode(JournalLineage.decodeText(value)).equals(value);
    }
    private static boolean validHistory(String line) {
        String[] row=line.split("\t",-1);
        try {
            if(row.length<4)return false;
            boolean retirement=row[0].equals(PREFIX)||row[0].equals("TASK/5");
            boolean modern=retirement||row[0].equals("TASK/4");
            UUID.fromString(row[2]);
            // The old policy gate wrote diagnostics under TASK/4. Preserve those bytes,
            // but never interpret them as an admission/release fact for a Task id.
            if(row[0].equals("TASK/4")&&row[1].equals("ADMISSION_FENCED")&&row.length==7) {
                UUID.fromString(row[4]);
                return encoded(row[3])&&Set.of("POLICY_TERMINAL_NEWER_GENERATION","EXACT_ENGINE_DENIAL").contains(row[5])
                        &&Set.of("FENCE_INSTALLED","FENCE_NOT_INSTALLED").contains(row[6]);
            }
            if(row[0].equals("TASK/4")&&row[1].equals("ADMISSION_FENCE_UNAVAILABLE")&&row.length==6) {
                UUID.fromString(row[4]);return encoded(row[3])&&!row[5].isEmpty();
            }
            return switch(row[1]) {
                case "INTENT" -> {
                    if(row.length!=10||!Set.of("CF_STAGE_INVOCATION","CF_RESULT_OBSERVATION","DELEGATED_INVOCATION","CF_COMPLETION_OBSERVATION","EXACT_FUTURE","FORKJOIN_ADMISSION","PLAIN_QUEUE_DRAIN").contains(row[7]))yield false;
                    if(row[0].equals("TASK/2")&&row[7].equals("FORKJOIN_ADMISSION"))yield false;
                    if(!modern&&row[7].equals("CF_RESULT_OBSERVATION"))yield false;
                    if(!modern&&row[7].equals("DELEGATED_INVOCATION"))yield false;
                    UUID.fromString(row[3]);UUID.fromString(row[4]);UUID.fromString(row[6]);
                    if(Long.parseLong(row[5])<0)yield false;
                    boolean valid=true;
                    for(int i=8;i<10;i++)valid&=encode(JournalLineage.decodeText(row[i])).equals(row[i]);
                    yield valid;
                }
                case "FACT" -> row.length==9;
                case "LOCAL_FACT" -> modern&&row.length==9;
                case "RESOURCE_RELEASE" -> row.length==5;
                case "CAPTURE_RELEASE_OBSERVED","RETIREMENT_OBSERVED" -> row.length==6;
                case "ADMISSION_FENCED","START_PRECLUDED","CALLBACK_FIELDS_RELEASED","FENCE_RELEASE_INTENT","FENCE_RELEASED","QUEUE_REFERENCE_REMOVED" -> !row[0].equals("TASK/2")&&row.length==6;
                case "OWNED_QUEUE_ABSENCE_OBSERVED","OWNED_QUEUE_REQUEST_RETIRED","WAITER_EXITS_OBSERVED","RESULT_WAITERS_RELEASE_OBSERVED" -> modern&&row.length==6;
                case "CORE_RELEASED","PRIOR_PROCESS_ENDED" -> {
                    if(!retirement||row.length!=6)yield false;
                    UUID.fromString(row[3]);UUID.fromString(row[4]);
                    yield row[5].equals(row[1].equals("CORE_RELEASED")?"OWNED_STRONG_GRAPH_CLEARED":"RUNTIME_REFERENCES_ONLY_CALLBACK_EFFECTS_UNCHANGED");
                }
                case "CORE_RELEASE_INTENT","REOBSERVED","RESULT_CANCEL_INTENT","RESULT_CANCELLED" -> retirement&&row.length==6;
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
            if(!Integer.valueOf(95).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("TASK_BRIDGE_ABI_MISMATCH");
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            if(!Boolean.TRUE.equals(agent.getMethod("openTaskAccess",Class.class).invoke(null,RecoveryTasks.class)))
                throw new IllegalStateException("TASK_ACCESS_UNAVAILABLE");
            currentTask=bridge.getMethod("currentTask");currentExecution=bridge.getMethod("currentExecution");
            delivery=bridge.getMethod("deliveringTo",Object.class);coverage=bridge.getMethod("coverage");
            observerMatches=bridge.getMethod("observerMatches",Object.class);
            droppedEvents=bridge.getMethod("droppedEvents");uninstall=bridge.getMethod("uninstall",Object.class);
            captureState=bridge.getMethod("captureState",Object.class);
            acknowledgeCaptureLoss=bridge.getMethod("acknowledgeCaptureLoss",Object.class,Object.class,long.class,long.class);
            boundarySnapshot=bridge.getMethod("boundarySnapshot",String[].class);
            boundaryCapabilities=bridge.getMethod("boundaryCapabilities");
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
            forkQueueSnapshot=bridge.getMethod("forkQueueSnapshot",Object.class,Object.class);forkQueueEpoch=bridge.getMethod("forkQueueEpoch",Object.class);
            submissionClosed=bridge.getMethod("submissionClosed",Object.class,Object.class);
            sourceOrigin=bridge.getMethod("sourceOrigin",Object.class,Object.class,Object.class);invocationState=bridge.getMethod("invocationState",Object.class);
            bridge.getMethod("install",Consumer.class).invoke(null,SINK);
            Object[] capture=(Object[])captureState.invoke(null,SINK);
            if(capture==null||capture.length!=5||!(capture[1] instanceof Long epoch))throw new IllegalStateException("TASK_CAPTURE_SCOPE_UNAVAILABLE");
            bridgeRegistration=capture[0];bridgeRegistrationEpoch=epoch;
            backend=String.valueOf(coverage.invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) {
            bridge=null;bridgeRegistration=null;bridgeRegistrationEpoch=-1;backend="TASK_AGENT_UNAVAILABLE:"+unavailable.getClass().getSimpleName();
        }
    }
    private static void publishOwners() {
        publishedOwners=LISTENERS.stream().map(owner->owner.ownerRegistration).toArray(OwnerRegistration[]::new);
    }
    private static boolean rowScope(Object[] row,OwnerRegistration owner) {
        return row!=null&&row.length>=11&&row[8]==owner.registration&&row[9] instanceof Long epoch&&epoch==owner.epoch;
    }
    private static OwnerRegistration[] reserveOwners(Object[] row) {
        synchronized(LISTENERS) {
            if(row!=null&&(row[8]!=bridgeRegistration||!Long.valueOf(bridgeRegistrationEpoch).equals(row[9]))) {
                unknownCapture("TASK_CAPTURE_REGISTRATION_CHANGED");return new OwnerRegistration[0];
            }
            var selected=new ArrayList<OwnerRegistration>();
            for(OwnerRegistration owner:publishedOwners) {
                if(owner.owner.detached||row!=null&&!rowScope(row,owner))continue;
                if(row!=null&&Integer.valueOf(24).equals(row[0])&&row[4] instanceof Object[] source
                        &&source.length==3&&source[0]!=owner.owner)continue;
                owner.owner.capturesInFlight++;capturePublications++;selected.add(owner);
            }
            return selected.toArray(OwnerRegistration[]::new);
        }
    }
    private OwnerRegistration reserveOwner() {
        synchronized(LISTENERS) {
            if(detached)return null;
            capturesInFlight++;capturePublications++;return ownerRegistration;
        }
    }
    private static void releaseOwners(OwnerRegistration[] owners) {
        synchronized(LISTENERS) {for(OwnerRegistration owner:owners) {owner.owner.capturesInFlight--;capturePublications--;}}
    }
    private static void unknownCapture(String reason) {
        synchronized(LISTENERS) {
            unknownCaptureGap=reason;
            for(OwnerRegistration owner:publishedOwners)owner.owner.recordGap(reason);
        }
    }
    private static void receive(Object[] row) {
        try {
            if(delivery==null||!Boolean.TRUE.equals(delivery.invoke(null,SINK)))return;
        } catch(ReflectiveOperationException|RuntimeException unavailable) {unknownCapture("TASK_DELIVERY_IDENTITY_UNAVAILABLE");return;}
        if(row==null||row.length!=11||!(row[0] instanceof Integer)||!(row[7] instanceof Long)||!(row[10] instanceof Long)) {
            unknownCapture("TASK_CAPTURE_IDENTITY_UNAVAILABLE");return;
        }
        OwnerRegistration[] owners=reserveOwners(row);
        CaptureDelivery captured=new CaptureDelivery(owners.length);
        try {
            if(Thread.holdsLock(LISTENERS)) {
                // Our fenced cancellation/request retirement can synchronously emit while
                // the caller owns publication. These receipts add no new source relation.
                if(!Integer.valueOf(10).equals(row[0])&&!Integer.valueOf(16).equals(row[0]))
                    throw new IllegalStateException("TASK_CAPTURE_REQUIRES_OUTSIDE_PUBLICATION_GATE");
                for(OwnerRegistration published:owners)published.owner.captureReceipt(row,published,captured);
                return;
            }
            for(OwnerRegistration published:owners) {
                RecoveryTasks owner=published.owner;
                try {owner.capture(row,published,captured);}
                catch(RuntimeException|LinkageError unavailable) {owner.deferCaptureFailure(row,published,captured,"TASK_CAPTURE_FAILED");}
            }
        } finally {releaseOwners(owners);}
    }
    private void captureReceipt(Object[] row,OwnerRegistration published,CaptureDelivery captured) {
        Task task=tasks.get(row[1]);Set<UUID> related=workerBindings.get(row[1]);
        if(task==null||related==null||related.isEmpty()) {captured.resolved(false,false);return;}
        Capture event=new Capture((Integer)row[0],row[1],row[2],row[3],row[4],task.loader,related,(Long)row[7],null,null,null,
                task.inline,task.callbackDefinition,false,published.session,published.registration,published.epoch,(Long)row[10]);
        publishPending(event,published,null,captured,"");
    }
    /** Bootstrap retains failed rows until every current owner has received the scoped gap. */
    private static void pollCaptureLosses() {
        synchronized(LOSS_ROUTING) {if(routingLosses)return;routingLosses=true;}
        try {routeCaptureLosses();}
        finally {synchronized(LOSS_ROUTING) {routingLosses=false;}}
    }
    private static void routeCaptureLosses() {
        if(captureState==null)return;
        Object[] state;
        try {state=(Object[])captureState.invoke(null,SINK);}
        catch(ReflectiveOperationException|RuntimeException unavailable) {unknownCapture("TASK_CAPTURE_LOSS_QUERY_UNAVAILABLE");return;}
        if(state==null||state.length!=5||!(state[4] instanceof Object[][] losses)) {unknownCapture("TASK_CAPTURE_SCOPE_UNAVAILABLE");return;}
        for(Object[] row:losses) {
            if(row==null||row.length!=12||row[1]==null||!(row[11] instanceof Long sequence)) {
                unknownCapture("BOOTSTRAP_CAPTURE_LOSS_UNKNOWN");continue;
            }
            OwnerRegistration[] owners=reserveOwners(row);
            try {
                boolean located=false;
                for(OwnerRegistration published:owners) {
                    located|=published.owner.captureFailed(row,published,"BOOTSTRAP_CAPTURE_LOSS");
                }
                if(owners.length!=0&&!located)unknownCapture("BOOTSTRAP_CAPTURE_SOURCE_UNLOCATABLE");
                if(acknowledgeCaptureLoss==null||!Boolean.TRUE.equals(acknowledgeCaptureLoss.invoke(null,SINK,state[0],state[1],sequence)))
                    unknownCapture("TASK_CAPTURE_LOSS_ACK_UNAVAILABLE");
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {unknownCapture("TASK_CAPTURE_LOSS_ROUTING_UNAVAILABLE");}
            finally {releaseOwners(owners);}
        }
    }
    private boolean captureFailed(Object[] row,OwnerRegistration published,String reason) {
        if(!rowScope(row,published)||row[1]==null) {recordGap("TASK_CAPTURE_IDENTITY_UNAVAILABLE");return false;}
        Set<UUID> related=new HashSet<>();Object object=row[1],parent=row[3];
        Set<UUID> direct,ancestor;
        synchronized(LISTENERS) {direct=workerBindings.get(object);ancestor=workerBindings.get(parent);}
        if(direct!=null)related.addAll(direct);if(ancestor!=null)related.addAll(ancestor);
        UUID origin=origins.get(object);if(origin!=null)related.add(origin);
        if(Integer.valueOf(24).equals(row[0])&&row[2] instanceof UUID subject)related.add(subject);
        try {
            ProRuntime.Subject group=runtime.groupSubject(object);if(group!=null)related.add(group.id);
            // A preceding valid receipt may still own the only callback reference after
            // a platform finally cleared the task. Known loss keeps that real scope.
            for(PendingCapture pending:pendingCaptures)if(pending.event.task==object||pending.event.task==parent) {
                related.addAll(pending.event.subjects);
                CaptureRoots roots=pending.roots;
                if(roots!=null)captureSubjects(pending.event.task,roots.inline,related,roots);
            }
            Object[] inline=inlineDetails==null?null:(Object[])inlineDetails.invoke(null,object);
            captureSubjects(object,inline,related);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { /* The real task identity still scopes this gap. */ }
        synchronized(LISTENERS) {
            scopedGap(object,related,reason,published);
            // A lost child row must also block its known ancestor before a merge can create that edge.
            if(parent!=null&&parent!=object)scopedGap(parent,related,"DESCENDANT_"+reason,published);
        }
        return !related.isEmpty();
    }
    private void scopedGap(Object object,Set<UUID> related,String reason,OwnerRegistration published) {
        if(detached||ownerRegistration!=published)return;
        CaptureGap prior=captureGaps.get(object);Set<UUID> subjects=new HashSet<>(related);
        if(prior!=null)subjects.addAll(prior.subjects);
        CaptureGap next=new CaptureGap(published.registration,published.epoch,published.session,Set.copyOf(subjects),reason);
        if(!next.equals(prior)) {captureGaps.put(object,next);changes.incrementAndGet();}
    }
    private boolean taskGap(Object object) {
        CaptureGap loss=captureGaps.get(object);
        return loss!=null&&ownerRegistration!=null&&loss.registration==ownerRegistration.registration&&loss.epoch==ownerRegistration.epoch
                &&Objects.equals(loss.session,ownerRegistration.session);
    }
    void observed(Object object,UUID subject) {
        Objects.requireNonNull(object);Objects.requireNonNull(subject);
        OwnerRegistration published=reserveOwner();if(published==null)return;
        try {
            boolean first;
            synchronized(origins) {
                UUID prior=origins.get(object);
                if(prior!=null&&!prior.equals(subject)) {
                    sharedOrigins.add(object);
                    recordGap("CONFLICTING_TASK_ORIGIN");return;
                }
                origins.put(object,subject);first=prior==null;
            }
            List<CloneLink> links;
            synchronized(captureLock) {links=first?List.copyOf(clones.getOrDefault(object,List.of())):List.of();}
            for(CloneLink link:links) {
                var clone=link.result.get();if(clone!=null)runtime.recoverySources.cloned(clone,subject,link.holder.get());
            }
            // Real save/load activity, not a guessed wrapper field, supplies this causal relation.
            try { if(sourceOrigin!=null)sourceOrigin.invoke(null,SINK,this,subject); }
            catch(ReflectiveOperationException failure) {recordGap("EXECUTING_SOURCE_BINDING_UNAVAILABLE");}
        } finally {releaseOwners(new OwnerRegistration[]{published});}
    }
    UUID subjectOf(Object object) { return origins.get(object); }
    Object originLock(){return origins;}
    UUID exclusiveSubjectOf(Object object) {synchronized(origins){return sharedOrigins.contains(object)?null:origins.get(object);}}
    void groupObserved(Object object,Object scheduler,UUID subject) {
        groupObserved(object,scheduler,subject,null);
    }
    private void groupObserved(Object object,Object scheduler,UUID subject,Object observation) {
        if(object==null)return;
        OwnerRegistration published=reserveOwner();if(published==null)return;
        try {
            synchronized(origins){
                UUID previous=origins.get(object);
                if(previous!=null&&runtime.canonicalSubject(previous)!=runtime.canonicalSubject(subject)) {sharedOrigins.add(object);recordGap("CONFLICTING_GROUP_TASK_ORIGIN");return;}
                origins.put(object,subject);
            }
            Object[] row=new Object[]{11,object,scheduler,null,observation,Thread.currentThread(),System.nanoTime(),0L,
                    published.registration,published.epoch,0L};
            CaptureDelivery captured=new CaptureDelivery(1);
            try {capture(row,published,captured);}
            catch(RuntimeException|LinkageError failure) {deferCaptureFailure(row,published,captured,"GROUP_TASK_CAPTURE_FAILED");}
        } finally {releaseOwners(new OwnerRegistration[]{published});}
    }
    void groupScheduler(Object scheduler,ProRuntime.Subject group){
        if(!(scheduler instanceof ExecutorService)&&!(scheduler instanceof java.util.Timer))return;
        synchronized(LISTENERS){retiringSchedulers.computeIfAbsent(group.id,ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(scheduler);}
        group.unresolvedSources.add("GROUP_SCHEDULER_RESOURCES_PENDING");
    }
    void retireGroupSchedulers(ProRuntime.Subject group){
        ResourceMethods methods;
        try{methods=resourceMethods();}
        catch(ReflectiveOperationException unavailable){recordGap("GROUP_SCHEDULER_BRIDGE_UNAVAILABLE");return;}
        Set<Object> pending;
        synchronized(LISTENERS){
            pending=retiringSchedulers.computeIfAbsent(group.id,ignored->Collections.newSetFromMap(new IdentityHashMap<>()));
            // A real carrier can merge two previously separate groups. Keep the
            // original pool obligations even after their last Task is released.
            for(var at=retiringSchedulers.entrySet().iterator();at.hasNext();){
                var entry=at.next();
                if(!entry.getKey().equals(group.id)&&runtime.canonicalSubject(entry.getKey())==group){pending.addAll(entry.getValue());at.remove();}
            }
            for(Task task:tasks.values())if(runtime.sameGroup(task.subjects,group)
                    &&(task.scheduler instanceof ExecutorService||task.scheduler instanceof java.util.Timer&&drainedTasks.get(task.object)!=group))pending.add(task.scheduler);
        }
        Module[] modules=runtime.groupModules(group);
        for(Object scheduler:new ArrayList<>(pending)){
            try{
                boolean ended;
                if(scheduler instanceof java.util.Timer){
                    @SuppressWarnings("unchecked") List<Runnable> removed=(List<Runnable>)methods.retireTimer.invoke(null,scheduler,(Object)modules);
                    schedulerDrained(scheduler,removed,group);
                    ended=Boolean.TRUE.equals(methods.timerEnded.invoke(null,scheduler,(Object)modules));
                }else{
                    String disposition=(String)methods.schedulerDisposition.invoke(null,scheduler,(Object)modules);
                    if(disposition.equals("SHARED")){pending.remove(scheduler);continue;}
                    if(!disposition.equals("PRIVATE"))continue;
                    @SuppressWarnings("unchecked") List<Runnable> removed=(List<Runnable>)methods.retireScheduler.invoke(null,scheduler,(Object)modules);
                    schedulerDrained(scheduler,removed,group);
                    ended=Boolean.TRUE.equals(methods.schedulerEnded.invoke(null,scheduler));
                }
                if(ended)pending.remove(scheduler);
            }catch(ReflectiveOperationException|RuntimeException unavailable){
                // Retain this exact scheduler and continue the other members.
            }
        }
        if(pending.isEmpty()){retiringSchedulers.remove(group.id);group.unresolvedSources.remove("GROUP_SCHEDULER_RESOURCES_PENDING");}
        else group.unresolvedSources.add("GROUP_SCHEDULER_RESOURCES_PENDING");
    }
    void schedulerDrained(Object scheduler,List<Runnable> removed,ProRuntime.Subject group) {
        try {
            Object actual=resourceMethods().realScheduler.invoke(null,scheduler);
            if(actual!=null)scheduler=actual;
        }catch(ReflectiveOperationException unavailable){recordTransient("SCHEDULER_DELEGATE_UNAVAILABLE");return;}
        for(Runnable object:removed) {
            ProRuntime.Subject owner=runtime.groupSubject(object);
            if(owner!=group) {recordGap("SCHEDULER_DRAIN_SHARED_TASK");continue;}
            boolean drained;
            synchronized(LISTENERS) {drained=drainedTasks.get(object)==group;}
            if(drained){
                if(!acknowledgeSchedulerDrain(scheduler,object))group.unresolvedSources.add("GROUP_SCHEDULER_RESOURCES_PENDING");
                continue;
            }
            // The bootstrap retains the returned tasks until this event reaches
            // an actual Task below, so a full capture queue can be retried.
            groupObserved(object,scheduler,group.id,SCHEDULER_DRAIN);
        }
    }
    private boolean acknowledgeSchedulerDrain(Object scheduler,Object object){
        if(!(scheduler instanceof ExecutorService))return true;
        try{resourceMethods().drainAccepted.invoke(null,scheduler,object);return true;}
        catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    private void recordGap(String reason) {
        if(!reason.equals(gap)) { gap=reason;changes.incrementAndGet(); }
    }
    private void recordTransient(String reason) { transientIssue=reason; }
    private boolean checkBridge() {
        try {
            if(!unknownCaptureGap.isEmpty())recordGap(unknownCaptureGap);
            if(coverage!=null)backend=String.valueOf(coverage.invoke(null));
            if(observerMatches==null||!Boolean.TRUE.equals(observerMatches.invoke(null,SINK))) {
                recordGap("TASK_OBSERVER_NOT_OWNED");return false;
            }
            // DROPPED is cumulative diagnostic history. Only unidentified loss is global.
            if(captureState==null) { recordTransient("TASK_CAPTURE_LOSS_QUERY_UNAVAILABLE");return false; }
            Object[] state=(Object[])captureState.invoke(null,SINK);
            if(state==null||state.length!=5||ownerRegistration==null||state[0]!=ownerRegistration.registration
                    ||!Long.valueOf(ownerRegistration.epoch).equals(state[1])) {recordGap("TASK_CAPTURE_REGISTRATION_CHANGED");return false;}
            if(((Long)state[2])!=0L)recordGap("BOOTSTRAP_CAPTURE_LOSS_UNKNOWN");
            if(capturePublications!=0) {recordTransient("TASK_CAPTURE_PUBLICATION_IN_FLIGHT");return false;}
            if(((Object[][])state[4]).length!=0) {recordTransient("TASK_CAPTURE_LOSS_SCOPE_PENDING");return false;}
            return gap.isEmpty();
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            recordTransient("TASK_BRIDGE_COVERAGE_UNAVAILABLE");return false;
        }
    }
    private boolean checkBridge(Object task) {
        synchronized(LISTENERS) {
            if(!checkBridge()||taskGap(task))return false;
            // Until this owner's arbitrary callback fields are resolved, its source set
            // may still acquire another edge. Exact root absence cannot qualify a write.
            if(!pendingCaptures.isEmpty()) {recordTransient("TASK_SOURCE_RESOLUTION_PENDING");return false;}
            // A different runtime may not have resolved this shared root yet. Its raw
            // receipt must not disappear from the ownership gate while its graph is read.
            if(rootPending(task)) {
                recordTransient("TASK_ROOT_RESOLUTION_PENDING");return false;
            }
            return true;
        }
    }
    private static boolean rootPending(Object task) {
        synchronized(LISTENERS) {
            for(RecoveryTasks owner:LISTENERS)if(owner.pendingRoots.containsKey(task))return true;
            return false;
        }
    }
    private boolean pendingTask(Object task) {synchronized(LISTENERS){return pendingRoots.containsKey(task);}}
    private static final List<String> CF_BOUNDARIES=List.of(cfBoundaries());
    private static final String[] FORK_BOUNDARIES={"java/util/concurrent/ForkJoinTask","java/util/concurrent/ForkJoinTask$Aux",
            "java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue","java/util/concurrent/ForkJoinWorkerThread"};
    private static final String[] DELEGATE_BOUNDARIES={"java/util/concurrent/FutureTask","java/util/concurrent/Executors$RunnableAdapter",
            "java/util/concurrent/ForkJoinTask","java/util/concurrent/ForkJoinTask$AdaptedRunnable",
            "java/util/concurrent/ForkJoinTask$AdaptedRunnableAction","java/util/concurrent/ForkJoinTask$AdaptedCallable",
            "java/util/concurrent/ForkJoinTask$RunnableExecuteAction"};
    private static final List<String> FORK_REQUIREMENTS=List.of(FORK_BOUNDARIES),DELEGATE_REQUIREMENTS=List.of(DELEGATE_BOUNDARIES),
            TIMER_REQUIREMENTS=List.of("java/util/Timer","java/util/TimerThread");
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
        ResourceBinding resources=resourceBinding(task);if(resources==null)return false;
        List<String> required;
        if(task.inline||task.completion||task.result) {
            if(boundarySnapshot==null||inlineClosed==null)return false;
            if(task.scheduler instanceof ThreadPoolExecutor) {
                Object queue=resources.queue;if(queue==null||!supportedQueue(queue))return false;
                required=CF_EXECUTOR_BOUNDARIES.get(queue.getClass().getName());
            } else required=CF_BOUNDARIES;
        } else if(task.forkJoin) {
            if(boundarySnapshot==null||fenceTask==null)return false;
            forkCancellation();
            required=FORK_REQUIREMENTS;
        } else if(task.delegated&&!exactFuture(task.object)) {
            if(boundarySnapshot==null)return false;
            required=DELEGATE_REQUIREMENTS;
        } else if(task.authority!=null&&!exactFuture(task.object)&&drainedTasks.get(task.object)==runtime.canonicalSubject(task.authority.id)) {
            if(boundarySnapshot==null)return false;
            if(task.scheduler instanceof java.util.Timer)required=TIMER_REQUIREMENTS;
            else {Object queue=resources.queue;if(queue==null||!supportedQueue(queue))return false;required=FUTURE_QUEUE_BOUNDARIES.get(queue.getClass().getName());}
        } else {
            if(boundarySnapshot==null||!exactFuture(task.object))return false;
            Object queue=resources.queue;if(queue==null||!supportedQueue(queue))return false;
            boolean scheduled=task.object.getClass()!=FutureTask.class;
            if(scheduled&&(task.scheduler==null||task.scheduler.getClass()!=ScheduledThreadPoolExecutor.class))return false;
            required=(scheduled?SCHEDULED_QUEUE_BOUNDARIES:FUTURE_QUEUE_BOUNDARIES).get(queue.getClass().getName());
        }
        boolean same=task.backendRequirements.equals(required);
        if(!same) {task.backendRequirements=required;task.requiredBoundaries=required.toArray(String[]::new);task.backendImage=null;}
        // Only installation capability is cached. Live queue ownership, exits and fields are never inferred from it.
        if(boundaryCapabilities!=null) {
            Object actual=boundaryCapabilities.invoke(null);
            if(same&&actual==task.backendImage)return task.coverageConfirmed=task.backendImageReady;
            if(!(actual instanceof Map.Entry<?,?> image)||!(image.getKey() instanceof Long epoch)
                    ||!(image.getValue() instanceof Set<?> usable))return false;
            task.backendImage=actual;
            task.backendImageReady=!required.isEmpty()&&usable.containsAll(required);
            return boundaryState(task,new long[]{epoch,task.backendImageReady?1:0});
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
            task.gateReason="";
            if(!checkBridge())return gateFailure(task,"TASK_OBSERVER_QUERY_UNAVAILABLE");
            if(closing)return gateFailure(task,"RUNTIME_CLOSING");
            if(!runtime.journal.healthy())return gateFailure(task,"DURABLE_WRITER_NOT_HEALTHY");
            if(!gap.isEmpty())return gateFailure(task,"CAPTURE_GAP:"+gap);
            if(taskGap(task.object))return gateFailure(task,"TASK_CAPTURE_GAP:"+captureGaps.get(task.object).reason);
            if(!observeBackend(task))return gateFailure(task,"REQUIRED_BOUNDARY_UNAVAILABLE");
            if(capturePending()||pendingDeliveries==null||((Integer)pendingDeliveries.invoke(null))!=0)return gateFailure(task,"CAPTURE_PUBLICATION_OR_MERGE_PENDING");
            Set<UUID> published=workerBindings.get(task.object);
            if(published==null||!runtime.sameGroup(published,task.authority))return gateFailure(task,"PUBLISHED_TASK_OWNERSHIP_CHANGED");
            for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task.object))
                return gateFailure(task,"TASK_SHARED_ACROSS_RUNTIME_OWNERS");
            ProRuntime.Subject scope=runtime.canonicalSubject(task.authority.id);
            if(scope==null||!scope.commitGate.tryLock())return gateFailure(task,"QUALIFICATION_GATE_BUSY");
            try {
                if(!qualified(task))return gateFailure(task,"QUALIFICATION_CHANGED");
                boolean[] invoked={false};
                Runnable commit=()->{
                    try {
                        // A delivery may start after the outer check and reserve publication.
                        // Recheck while the Bootstrap publication monitor excludes new deliveries.
                        if(!checkBridge(task.object)||capturePending()||((Integer)pendingDeliveries.invoke(null))!=0) {
                            gateFailure(task,"CAPTURE_PUBLICATION_OR_MERGE_PENDING");return;
                        }
                        action.run();invoked[0]=true;
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException("TASK_PUBLICATION_QUERY_UNAVAILABLE",failure); }
                };
                if(!Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,commit)))return gateFailure(task,"REQUIRED_TASK_BOUNDARY_UNAVAILABLE");
                return invoked[0];
            } finally { scope.commitGate.unlock(); }
        }
    }
    private static boolean gateFailure(Task task,String reason) { task.gateReason=reason;return false; }
    private void capture(Object[] row,OwnerRegistration published,CaptureDelivery captured) {
        // During shutdown keep observing accepted work and its late descendants, without granting writes.
        if(detached||ownerRegistration!=published||!rowScope(row,published)||row[1]==null) {captured.resolved(false,false);return;}
        Object object=row[1],parent=row[3];
        if((Integer)row[0]==25) {
            if(object instanceof net.minecraft.world.entity.Entity&&row[2] instanceof net.minecraft.world.entity.Entity clone) {
                Class<?> holder=row[4] instanceof Class<?> type?type:null;
                synchronized(captureLock) {
                    var children=clones.computeIfAbsent(object,key->new ArrayList<>());
                    children.removeIf(link->link.result.get()==null);
                    children.add(new CloneLink(new java.lang.ref.WeakReference<>(clone),new java.lang.ref.WeakReference<>(holder)));
                }
                UUID subject=origins.get(object);
                if(subject!=null)runtime.recoverySources.cloned(clone,subject,holder);
            }
            captured.resolved(false,false);
            return;
        }
        if((Integer)row[0]==24&&(!(row[4] instanceof Object[] origin)||origin.length!=3||origin[0]!=this)) {captured.resolved(false,false);return;}
        if(internalRoot(object)) {captured.resolved(false,false);return;}
        // Before any real provenance exists there is no task relation to resolve. Bootstrap
        // still retains actual running frames; a later source event adopts them normally.
        ProRuntime.Subject group=runtime.groupSubject(object);
        if(origins.isEmpty()&&workerBindings.isEmpty()&&group==null&&(Integer)row[0]!=24) {captured.resolved(false,false);return;}
        Object[] inline=null;
        String failure="";
        try { if(inlineDetails!=null)inline=(Object[])inlineDetails.invoke(null,object); }
        catch(ReflectiveOperationException unavailable) {failure="INLINE_CAPTURE_UNAVAILABLE";}
        // Temporary subject IDs are not recovery sources. Avoid routing this bookkeeping
        // through the global HashMap writer observer for every ordinary JDK callback.
        Set<UUID> related=new TreeSet<>();
        if(group!=null)related.add(group.id);
        if((Integer)row[0]==24&&row[2] instanceof UUID subject)related.add(subject);
        UUID origin=origins.get(object);if(origin!=null)related.add(origin);
        Set<UUID> existing=workerBindings.get(object),ancestor=workerBindings.get(parent);
        if(existing!=null)related.addAll(existing);
        if(ancestor!=null)related.addAll(ancestor);
        // Retain platform operands and each actual callback's shallow reference fields:
        // a callback can move a source to a local and clear its field before the tick.
        // Wrapper targets and subject/source graphs remain owned by the server drain.
        TaskOperands operands=inline==null?taskOperands(object):null;
        Object base=operands==null?null:operands.value("base");
        TaskOperands baseOperands=base==null?null:taskOperands(base);
        TaskOperands edge=baseOperands==null?operands:baseOperands;
        Object dependency=inline==null?edge==null?null:edge.value("src"):inline[0];
        Object other=inline==null?edge==null?null:edge.value("snd"):inline[1],output=inline==null?edge==null?null:edge.value("dep"):inline[3];
        Object inputs=operands==null?null:operands.value("srcs");
        if(inputs instanceof CompletableFuture<?>[])dependency=inputs;
        // A waiter consumes this result. It does not produce the result it is waiting for.
        if((Integer)row[0]>=18&&(Integer)row[0]<=20&&standardCompletionSource(object)) { dependency=object;output=null; }
        Object invocation=(Integer)row[0]==24?((Object[])row[4])[1]:row[4];
        Object scheduler=(Integer)row[0]==24?((Object[])row[4])[2]:row[2];
        ClassLoader loader=object.getClass().getClassLoader();
        Class<?> callbackDefinition=inline==null||inline[2]==null?null:inline[2].getClass();
        boolean hasExecutor=operands!=null&&operands.value("executor")!=null;
        Capture event=new Capture((Integer)row[0],object,scheduler,parent,invocation,loader,Set.copyOf(related),(Long)row[7],dependency,other,output,
                inline!=null,callbackDefinition,hasExecutor,published.session,published.registration,published.epoch,(Long)row[10]);
        publishPending(event,published,captureRoots(object,inline,operands,baseOperands),captured,failure);
    }
    private static TaskOperands taskOperands(Object object) {
        Field[] fields=TRANSIENT_TASK_FIELDS.get(object.getClass());if(fields.length==0)return null;
        return readOperands(object,fields,false);
    }
    private static TaskOperands readOperands(Object object,Field[] fields,boolean callback) {
        Object[] values=new Object[fields.length];boolean complete=true;
        for(int i=0;i<fields.length;i++)try {
            Field field=fields[i];
            if(field.canAccess(object)||field.trySetAccessible())values[i]=field.get(object);else complete=false;
        } catch(IllegalAccessException|RuntimeException unavailable) {complete=false;}
        return new TaskOperands(object,fields,values,complete,callback);
    }
    private static CaptureRoots captureRoots(Object object,Object[] inline,TaskOperands task,TaskOperands base) {
        var retained=new ArrayList<TaskOperands>();var pending=new ArrayDeque<Object>();
        if(inline==null)pending.add(object);else if(inline[2]!=null)pending.add(inline[2]);
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty()) {
            Object current=pending.removeFirst();if(!seen.add(current)||internalRoot(current))continue;
            TaskOperands platform=task!=null&&task.object==current?task:base!=null&&base.object==current?base:taskOperands(current);
            if(platform!=null) {
                if(platform!=task&&platform!=base)retained.add(platform);
                for(int i=0;i<platform.fields.length;i++)if(platform.values[i]!=null&&switch(platform.fields[i].getName()) {
                    case "callable","task","runnable","fn","base" -> true;
                    default -> false;
                })pending.addLast(platform.values[i]);
            }
            Field[] fields=CALLBACK_TASK_FIELDS.get(current.getClass());
            if(fields.length!=0)retained.add(readOperands(current,fields,true));
            // A custom field's value is retained as one operand. Never descend into it here.
        }
        return new CaptureRoots(inline,task,base,List.copyOf(retained));
    }
    private void deferCaptureFailure(Object[] row,OwnerRegistration published,CaptureDelivery captured,String reason) {
        Object object=row[1];
        if(object==null||!rowScope(row,published)) {captured.resolved(false,true);return;}
        Set<UUID> related=new TreeSet<>();UUID origin=origins.get(object);if(origin!=null)related.add(origin);
        if(Integer.valueOf(24).equals(row[0])&&row[2] instanceof UUID subject)related.add(subject);
        Set<UUID> known=workerBindings.get(object),ancestor=workerBindings.get(row[3]);
        if(known!=null)related.addAll(known);if(ancestor!=null)related.addAll(ancestor);
        Object invocation=row[4],scheduler=row[2];
        if(Integer.valueOf(24).equals(row[0])&&row[4] instanceof Object[] source&&source.length==3) {invocation=source[1];scheduler=source[2];}
        Capture event=new Capture((Integer)row[0],object,scheduler,row[3],invocation,object.getClass().getClassLoader(),Set.copyOf(related),(Long)row[7],null,null,null,
                false,null,false,published.session,published.registration,published.epoch,(Long)row[10]);
        publishPending(event,published,new CaptureRoots(null,null,null,List.of()),captured,reason);
    }
    private void publishPending(Capture event,OwnerRegistration published,CaptureRoots roots,CaptureDelivery captured,String failure) {
        Set<Object> identities=Collections.newSetFromMap(new IdentityHashMap<>());
        identities.add(event.task);if(event.parent!=null)identities.add(event.parent);if(event.output!=null)identities.add(event.output);
        if(roots!=null) {
            if(roots.inline!=null)for(Object value:roots.inline)if(value!=null)identities.add(value);
            for(TaskOperands operands:new TaskOperands[]{roots.task,roots.base})if(operands!=null)
                for(Object value:operands.values)if(value!=null)identities.add(value);
            for(TaskOperands operands:roots.callbacks)for(Object value:operands.values)if(value!=null)identities.add(value);
        }
        synchronized(LISTENERS) {
            if(detached||ownerRegistration!=published) {captured.resolved(false,false);return;}
            for(Object identity:identities)pendingRoots.merge(identity,1,Integer::sum);
            for(UUID subject:event.subjects)advanceCaptureEpoch(event.task,event.subjects,subject,published);
            pendingCaptures.add(new PendingCapture(event,published,roots,identities.toArray(),captured,failure));
            changes.incrementAndGet();
        }
    }
    private void advanceCaptureEpoch(Object object,Set<UUID> related,UUID subject,OwnerRegistration published) {
        long prior=captureEpochs.getOrDefault(subject,0L);
        if(prior==Long.MAX_VALUE)scopedGap(object,related,"TASK_CAPTURE_EPOCH_EXHAUSTED",published);
        else captureEpochs.put(subject,prior+1);
    }
    private boolean capturePending() {return pendingCaptures.peek()!=null||events.peek()!=null;}
    /** Root resolution never runs on the task callback or under TAGS/GRAPH/LISTENERS. */
    private void resolveCaptures(int budget) {
        for(int count=0;count<budget;count++) {
            PendingCapture pending=pendingCaptures.peek();if(pending==null)return;
            boolean located=false,failed=!pending.failure.isEmpty();
            try {located=resolveCapture(pending);}
            catch(RuntimeException|LinkageError unavailable) {
                Capture event=pending.event;
                located=captureFailed(new Object[]{event.kind,event.task,event.scheduler,event.parent,event.invocation,null,0L,event.dropped,
                        event.registration,event.registrationEpoch,event.unknownLoss},pending.owner,"TASK_CAPTURE_FAILED");failed=true;
            } finally {
                synchronized(LISTENERS) {
                    for(Object identity:pending.identities) {
                        Integer countAt=pendingRoots.get(identity);
                        if(countAt!=null) {if(countAt==1)pendingRoots.remove(identity);else pendingRoots.put(identity,countAt-1);}
                    }
                    pendingCaptures.poll();
                }
                pending.delivery.resolved(located,failed);
            }
        }
    }
    private boolean resolveCapture(PendingCapture pending) {
        Capture event=pending.event;OwnerRegistration published=pending.owner;Object object=event.task;
        if(detached||ownerRegistration!=published) {recordGap("TASK_CAPTURE_REGISTRATION_CHANGED");return false;}
        CaptureRoots roots=pending.roots;
        if(roots!=null&&internalDelegate(object,roots))return false;
        Set<UUID> related=new TreeSet<>(event.subjects);
        Set<UUID> known=workerBindings.get(object),ancestor=workerBindings.get(event.parent);
        if(known!=null)related.addAll(known);if(ancestor!=null)related.addAll(ancestor);
        boolean complete=roots==null||captureSubjects(object,roots.inline,related,roots)&&roots.complete();
        if(related.isEmpty()) {
            if(!complete)throw new IllegalStateException("TASK_CAPTURE_SOURCE_UNLOCATABLE");
            return false;
        }
        synchronized(LISTENERS) {
            if(detached||ownerRegistration!=published)return false;
            synchronized(workerBindings) {
                Set<UUID> prior=workerBindings.get(object);if(prior!=null)related.addAll(prior);
                workerBindings.put(object,Set.copyOf(related));
                if(event.output!=null) {
                    prior=workerBindings.get(event.output);Set<UUID> merged=new HashSet<>(related);if(prior!=null)merged.addAll(prior);
                    workerBindings.put(event.output,Set.copyOf(merged));
                }
            }
            if(!complete)scopedGap(object,related,"TASK_REFERENCE_CAPTURE_UNAVAILABLE",published);
            if(!pending.failure.isEmpty())scopedGap(object,related,pending.failure,published);
            CaptureGap loss=captureGaps.get(object);
            if(loss!=null)scopedGap(object,related,loss.reason,published);
            for(UUID subject:related)if(!event.subjects.contains(subject))advanceCaptureEpoch(object,related,subject,published);
            changes.incrementAndGet();
            events.add(new Capture(event.kind,object,event.scheduler,event.parent,event.invocation,event.loader,Set.copyOf(related),event.dropped,
                    event.dependency,event.otherDependency,event.output,event.inline,event.callbackDefinition,event.hasExecutor,
                    event.session,event.registration,event.registrationEpoch,event.unknownLoss));
        }
        return true;
    }
    private boolean captureSubjects(Object object,Object[] inline,Set<UUID> related) {
        return captureSubjects(object,inline,related,null);
    }
    private boolean captureSubjects(Object object,Object[] inline,Set<UUID> related,CaptureRoots roots) {
        boolean complete=true;
        ArrayDeque<Object> pending=new ArrayDeque<>();
        if(inline==null)pending.add(object);else for(int i=0;i<3;i++)if(inline[i]!=null)pending.add(inline[i]);
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty()) {
            Object current=pending.removeFirst();if(!seen.add(current))continue;
            UUID origin=roots!=null&&roots.callback(current)?origins.get(current):capturedOrigin(current);
            if(origin!=null) { related.add(origin);continue; }
            Set<UUID> known=workerBindings.get(current);if(known!=null)related.addAll(known);
            Class<?> type=current.getClass();
            if(type==CompletableFuture[].class) {
                Object[] inputs=(Object[])current;
                for(Object input:inputs)if(input!=null)pending.addLast(input);
                continue;
            }
            Class<?> nest=type.getNestHost();
            if(nest==ProRuntime.class||nest==RecoveryStorage.class
                    ||nest==IntentJournal.class||nest==RecoveryTasks.class
                    ||nest==RecoverySources.class||nest==RecoveryRecords.class
                    ||nest==RecoveryRecords.class||nest==RecoveryChain.class)continue;
            if(type.isArray()||type.isEnum()||current instanceof Class<?>||current instanceof Thread||current instanceof Executor)continue;
            if(type.getName().startsWith("java.")&&!knownDelegate(type))continue;
            boolean delegate=knownDelegate(type);
            Field[] captureFields=CAPTURE_FIELDS.get(type);
            for(Field field:captureFields) {
                try {
                    TaskOperands observed=roots==null?null:roots.retained(current,field);
                    int saved=observed==null?-1:observed.index(field);
                    if(saved>=0||field.canAccess(current)||field.trySetAccessible()) {
                        Object value=saved>=0?observed.values[saved]:field.get(current);if(value==null)continue;
                        UUID direct=capturedOrigin(value);if(direct!=null)related.add(direct);
                        Set<UUID> bound=workerBindings.get(value);if(bound!=null)related.addAll(bound);
                        if(delegate&&jdkDelegateField(field.getDeclaringClass(),field))pending.addLast(value);
                    } else complete=false;
                }
                catch(IllegalAccessException|RuntimeException unavailable) { complete=false; }
            }
        }
        return complete;
    }
    private UUID capturedOrigin(Object value) {
        UUID direct=origins.get(value);if(direct!=null)return direct;
        Object target=RecoveryRecords.wrapperTarget(value,runtime,true);
        return target instanceof net.minecraft.world.entity.Entity?origins.get(target):
                target instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(runtime.recoverySources,tag):null;
    }
    /** Real Entity allocation inside an observed invocation, including a plain constructor with no NBT load. */
    public static void constructed(net.minecraft.world.entity.Entity entity,net.minecraft.world.level.Level constructionLevel) {
        if(currentTask==null||currentExecution==null)return;
        OwnerRegistration[] owners=new OwnerRegistration[0];Object task=null;
        try {
            task=currentTask.invoke(null);Object invocation=currentExecution.invoke(null);
            Object[] producerInputs=ProRuntime.currentProducerInputs();
            if((task==null||invocation==null)&&producerInputs.length==0)return;
            owners=reserveOwners(null);
            RecoveryTasks selected=null;UUID subject=null;
            for(OwnerRegistration published:owners) {
                RecoveryTasks owner=published.owner;
                    if(owner.detached||owner.origins.isEmpty()&&owner.workerBindings.isEmpty()&&!owner.pendingTask(task))continue;
                    Object[] inline=task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task);
                    Set<UUID> related=new HashSet<>();
                    if(!owner.producerSubjects(task,inline,producerInputs,related)) {
                        for(UUID id:related) { var authority=owner.runtime.subjects.get(id);if(authority!=null)authority.unresolvedSources.add("CONSTRUCTOR_CAPTURE_INCOMPLETE"); }
                        owner.changes.incrementAndGet();return;
                    }
                    if(related.isEmpty())continue;
                    ProRuntime.Subject authority=owner.runtime.groupAuthority(related);
                    if(authority==null||selected!=null) {
                        if(selected!=null) {
                            ProRuntime.Subject prior=selected.runtime.subjects.get(subject);
                            if(prior!=null)prior.unresolvedSources.add("MIXED_TASK_CONSTRUCTOR_OUTPUT");
                            selected.changes.incrementAndGet();
                        }
                        for(UUID id:related) {
                            ProRuntime.Subject affected=owner.runtime.subjects.get(id);
                            if(affected!=null)affected.unresolvedSources.add("MIXED_TASK_CONSTRUCTOR_OUTPUT");
                        }
                        owner.changes.incrementAndGet();return;
                    }
                    selected=owner;subject=authority.id;
            }
            if(selected!=null&&constructionLevel instanceof net.minecraft.server.level.ServerLevel level
                    &&level.getServer()==selected.runtime.server) {
                    selected.runtime.recoverySources.produced(entity,subject,null,"TASK_CONSTRUCTOR_BINDING_CAPACITY");
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
            constructorCaptureFailed(owners,task,"CONSTRUCTOR_CAPTURE_UNAVAILABLE");
        } finally {releaseOwners(owners);}
    }
    static boolean deniesCurrentPublication(net.minecraft.world.entity.Entity entity) {
        if(currentTask==null||currentExecution==null)return false;
        OwnerRegistration[] owners=new OwnerRegistration[0];
        try {
            Object task=currentTask.invoke(null);Object[] producerInputs=ProRuntime.currentProducerInputs();
            if((task==null||currentExecution.invoke(null)==null)&&producerInputs.length==0)return false;
            owners=reserveOwners(null);
            RecoveryTasks selected=null;UUID id=null;
            for(OwnerRegistration published:owners) {
                RecoveryTasks owner=published.owner;
                    if(owner.detached||owner.origins.isEmpty()&&owner.workerBindings.isEmpty()&&!owner.pendingTask(task))continue;
                    Object[] inline=task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task);
                    Set<UUID> related=new HashSet<>();if(!owner.producerSubjects(task,inline,producerInputs,related))return false;
                    if(related.isEmpty())continue;
                    ProRuntime.Subject authority=owner.runtime.groupAuthority(related);
                    if(authority==null||selected!=null)return false;
                    selected=owner;id=authority.id;
            }
            if(selected==null||!(((dev.ronova.pro.mixin.Access.EntityState)entity).pro$level() instanceof net.minecraft.server.level.ServerLevel realm)||realm.getServer()!=selected.runtime.server)return false;
            UUID existing=selected.subjectOf(entity);
            return (existing==null||selected.runtime.canonicalSubject(existing)==selected.runtime.canonicalSubject(id))&&selected.runtime.terminalSource(id);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) { return false; }
        finally {releaseOwners(owners);}
    }
    private boolean producerSubjects(Object task,Object[] inline,Object[] inputs,Set<UUID> subjects) {
        boolean complete=true,captured=false;
        if(task!=null) {
            // Constructor/publication enforcement happens before its original effect. A
            // queued ENTER must still supply its actual Inline/JDK operands at this point.
            for(PendingCapture pending:pendingCaptures)if(pending.event.task==task&&pending.roots!=null) {
                CaptureRoots roots=pending.roots;subjects.addAll(pending.event.subjects);
                complete&=captureSubjects(task,inline==null?roots.inline:inline,subjects,roots)&&roots.complete();captured=true;
            }
            if(!captured)complete=captureSubjects(task,inline,subjects);
        }
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
        OwnerRegistration[] owners=new OwnerRegistration[0];Object task=null;
        try {
            task=currentTask.invoke(null);Object[] producerInputs=ProRuntime.currentProducerInputs();
            if(task==null&&producerInputs.length==0)return;
            owners=reserveOwners(null);
            for(OwnerRegistration published:owners) {
                RecoveryTasks owner=published.owner;
                    Set<UUID> related=new HashSet<>();owner.producerSubjects(task,task==null||inlineDetails==null?null:(Object[])inlineDetails.invoke(null,task),producerInputs,related);
                    for(UUID id:related) {
                        var subject=owner.runtime.subjects.get(id);
                        if(subject!=null)subject.unresolvedSources.add("CONSTRUCTOR_SIDE_EFFECTS_UNKNOWN");
                    }
                    if(!related.isEmpty())owner.changes.incrementAndGet();
            }
        } catch(ReflectiveOperationException|RuntimeException unavailable) {
            constructorCaptureFailed(owners,task,"CONSTRUCTOR_FAILURE_OBSERVER_UNAVAILABLE");
        } finally {releaseOwners(owners);}
    }
    private static void constructorCaptureFailed(OwnerRegistration[] owners,Object task,String reason) {
        if(task==null) {unknownCapture(reason);return;}
        boolean located=false;
        for(OwnerRegistration published:owners) {
            located|=published.owner.captureFailed(new Object[]{11,task,null,null,null,Thread.currentThread(),System.nanoTime(),0L,
                    published.registration,published.epoch,0L},published,reason);
        }
        if(owners.length!=0&&!located)unknownCapture(reason+"_SOURCE_UNLOCATABLE");
    }
    /** Only an actual JDK delegate chain can identify our worker; a random field pointing at Core cannot. */
    private static boolean internalRoot(Object object) {
        Class<?> host=object.getClass().getNestHost();
        return (object instanceof Runnable||object instanceof Callable<?>)&&(host==ProRuntime.class||host==IntentJournal.class
                ||host==RecoveryStorage.class||host==RecoveryTasks.class||host==RecoveryRecords.class);
    }
    private static boolean internalDelegate(Object object,CaptureRoots roots) {
        Object current=object;
        for(int depth=0;depth<4&&current!=null;depth++) {
            Class<?> type=current.getClass();if(internalRoot(current))return true;
            {
                String delegate;
                if(exactFuture(current))delegate="callable";
                else if(type.getClassLoader()==null&&type.getName().equals("java.util.concurrent.Executors$RunnableAdapter"))
                    delegate="task";
                else return false;
                TaskOperands operands=roots.operands(current);
                if(operands==null)operands=taskOperands(current);
                if(operands==null||!operands.complete)return false;
                current=operands.value(delegate);
            }
        }
        return false;
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
        transientIssue="";
        pollCaptureLosses();
        synchronized(LISTENERS) { checkBridge(); }
        // Rotate only unresolved metadata; a permanently unknown old process must
        // not consume the first sixteen positions on every server tick.
        for(int budget=Math.min(16,priorPending.size());budget>0;budget--) {
            PriorTask prior=priorPending.removeFirst();
            if(priorReleased.contains(prior.id))continue;
            if(priorObserversReleased.contains(prior.session)||runtime.journal.processEnded(prior.session)) {
                String fact=PREFIX+"\tPRIOR_PROCESS_ENDED\t"+prior.id+"\t"+prior.operation+"\t"+prior.session+"\tRUNTIME_REFERENCES_ONLY_CALLBACK_EFFECTS_UNCHANGED";
                var ack=priorProcessFacts.get(prior.id);
                ack=ack==null?runtime.journal.append(fact):runtime.journal.retryRejected(fact,ack);
                priorProcessFacts.put(prior.id,ack);if(confirmed(ack))priorReleased.add(prior.id);
            }
            if(!priorReleased.contains(prior.id))priorPending.addLast(prior);
        }
        drain(tick);
        if(!windows.isEmpty()&&(tick<leaseStarted||tick-leaseStarted>=LEASE_TICKS)) {
            if(waiting.isEmpty())leaseStarted=tick;else yieldWindow(tick);
        }
        // Newly discovered children join the existing lease before its next observation.
        admitComponents(List.copyOf(windows));
        for(int budget=0;budget<SAMPLE_BUDGET&&windows.size()<WINDOW_TARGET&&!waiting.isEmpty();budget++) {
            Task task=waiting.removeFirst();task.queued=false;
            if(!qualified(task))continue;
            if(tick<task.retryAt) { enqueue(task);continue; }
            if(windows.isEmpty())leaseStarted=tick;
            admitComponents(List.of(task));
        }
        int visits=Math.min(windows.size(),Math.min(MAX_SAMPLE_BUDGET,Math.max(SAMPLE_BUDGET,(windows.size()+7)/8)));
        for(int budget=0;budget<visits;budget++) {
            Task task=windows.removeFirst();windows.addLast(task);
            if(tick<task.retryAt)continue;
            task.retryAt=tick+4;
            try { advance(task,tick); }
            catch(ReflectiveOperationException|RuntimeException failure) { task.reason="TASK_DISPOSITION_UNRESOLVED:"+failure.getClass().getSimpleName()+":"+failure.getMessage(); }
        }
        settleScopes(tick);
        retireCoreReferences(tick);
        // Policy owns a separate tick budget; receiving and resolving roots only enqueue it.
        advancePolicyFences();
        // Keep completed children sampled while any linked parent is building its current fact.
        boolean done=!windows.isEmpty(),progressing=false;
        synchronized(LISTENERS) {
            for(Task task:windows) {
                done&=currentFact(task,tick,0L);
                progressing|=task.ack!=null&&!task.ack.isDone()||task.releaseAck!=null&&!task.releaseAck.isDone();
            }
        }
        if(done&&!waiting.isEmpty()||!progressing&&!windows.isEmpty())yieldWindow(tick);
    }
    private void admitComponents(Collection<Task> roots) {
        var pending=new ArrayDeque<Task>();pending.addAll(roots);
        Set<Task> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Task> active=Collections.newSetFromMap(new IdentityHashMap<>());active.addAll(windows);
        Set<UUID> includedOperations=new HashSet<>();
        while(!pending.isEmpty()) {
            Task task=pending.removeFirst();if(!seen.add(task))continue;
            if(task.operation!=null&&includedOperations.add(task.operation))pending.addAll(operationTasks.getOrDefault(task.operation,Set.of()));
            if(qualified(task)&&active.add(task)) {
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
        resolveCaptures(DRAIN);
        for(int budget=0;budget<DRAIN;budget++) {
            Capture event=events.poll();if(event==null)break;
            OwnerRegistration published=ownerRegistration;
            if(published==null||!Objects.equals(event.session,published.session)||event.registration!=published.registration||event.registrationEpoch!=published.epoch) {
                recordGap("TASK_CAPTURE_REGISTRATION_CHANGED");continue;
            }
            Task task=tasks.get(event.task);
            if(task==null) {
                task=new Task(event.task,event.loader,event.inline,event.callbackDefinition);tasks.put(event.task,task);
                UUID prior=retiredObjects.remove(event.task);
                if(prior!=null)runtime.journal.append(PREFIX+"\tREOBSERVED\t"+task.id+"\t"+prior+"\t"+runtime.session+"\tNEW_EXECUTION_OBLIGATION");
                for(Task child:childrenByParent.getOrDefault(event.task,Set.of()))link(task,child,tick);
            }
            if(task.loader!=event.loader||task.definition!=event.task.getClass()) { task.reason="DEFINITION_BINDING_CHANGED";continue; }
            if(task.inline!=event.inline||task.callbackDefinition!=event.callbackDefinition) { task.reason="CALLBACK_DEFINITION_BINDING_CHANGED";continue; }
            // Merge the event first. A queued source delivery can still add another owner;
            // an exact-object fence is decided only after the publication batch is complete.
            if(task.subjects.addAll(event.subjects))task.resources=null;
            queuePolicy(task);
            if(drainedTasks.get(task.object)==runtime.groupAuthority(task.subjects))task.queueRemoved=true;
            changed(task,tick);
            if(task.authority!=null)enqueue(task);
            if(event.scheduler!=null&&event.kind!=13&&event.kind!=15&&event.kind!=16&&event.kind!=17&&event.kind!=21) {
                if(task.scheduler!=null&&task.scheduler!=event.scheduler) {task.reason="MULTIPLE_SCHEDULER_OWNERS";invalidateResources(task);}
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
                    if(result==null) {
                        result=new Task(event.output,event.output.getClass().getClassLoader(),false,null);tasks.put(event.output,result);
                    }
                    if(result!=null&&result.result) {
                        result.subjects.addAll(event.subjects);
                        result.ownedResult|=event.inline&&dependencies(event.dependency,event.otherDependency).stream().noneMatch(input->input==event.output);
                        link(task,result,tick);changed(result,tick);
                    }
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
                case 11 -> {
                    task.creationObserved=true;task.asynchronousCompletion|=event.hasExecutor;
                    if(event.invocation==SCHEDULER_DRAIN){
                        ProRuntime.Subject owner=runtime.groupAuthority(event.subjects);
                        if(owner==null||runtime.groupSubject(task.object)!=owner)recordGap("SCHEDULER_DRAIN_GROUP_CHANGED");
                        else{
                            drainedTasks.put(task.object,owner);task.queueRemoved=true;
                            if(!acknowledgeSchedulerDrain(event.scheduler,task.object))owner.unresolvedSources.add("GROUP_SCHEDULER_RESOURCES_PENDING");
                        }
                    }
                }
                case 12 -> {
                    boolean observed=task.workerReferences.remove(event.invocation);
                    boolean adopted=Boolean.TRUE.equals(task.adoptedInvocations.remove(event.invocation));
                    if(!observed&&!adopted)task.reason="UNMATCHED_WORKER_REFERENCE_RETIREMENT";
                }
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
            if(event.unknownLoss!=0)recordGap("BOOTSTRAP_CAPTURE_LOSS_UNKNOWN");
        }
    }
    private void queuePolicy(Task task) {
        if(task.forkJoin&&!task.policyQueued) { task.policyQueued=true;policyPending.addLast(task); }
    }
    private void advancePolicyFences() {
        for(int budget=Math.min(SAMPLE_BUDGET,policyPending.size());budget>0;budget--) {
            Task task=policyPending.removeFirst();task.policyQueued=false;
            if(tasks.get(task.object)!=task)continue;
            try {
                if(task.policyFenced?!retirePolicyFence(task):policyGate(task))queuePolicy(task);
            } catch(ReflectiveOperationException|RuntimeException unavailable) {
                task.gateReason="POLICY_FENCE_PENDING:"+unavailable.getClass().getSimpleName();queuePolicy(task);
            }
        }
    }
    /** True means retry this exact candidate; no task/body is executed by this policy pass. */
    private boolean policyGate(Task task) {
        if(task.admissionFenced&&!task.admissionReleased||!task.forkJoin||task.subjects.isEmpty()||closing)return false;
        if(fenceTask==null||withBoundaries==null)return true;
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||!runtime.journal.healthy()||!gap.isEmpty())return true;
            try {
                if(pendingDeliveries==null||((Integer)pendingDeliveries.invoke(null))!=0) {
                    task.gateReason="CAPTURE_PUBLICATION_PENDING";return true;
                }
            } catch(ReflectiveOperationException unavailable) { return true; }
            // Resolved roots have already published ownership, even if their merge rows
            // are beyond this drain budget. checkBridge(task) excludes unresolved aliases.
            Set<UUID> published=workerBindings.get(task.object);
            if(published==null)return true;
            Set<UUID> established=new HashSet<>(task.subjects);established.addAll(published);
            ProRuntime.Subject subject=runtime.groupAuthority(established);
            if(subject==null||!subject.terminal||runtime.groupAuthority(published)!=subject)return false;
            for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task.object))return false;
            if(!subject.commitGate.tryLock()){task.gateReason="QUALIFICATION_GATE_BUSY";return true;}
            try {
                if(!subject.terminal||runtime.groupAuthority(established)!=subject||runtime.groupAuthority(published)!=subject)return true;
                boolean oldGeneration=task.authority==subject&&task.qualification>0&&task.qualification<subject.generation;
                boolean engineDenied=engineDenied(task);
                if(!oldGeneration&&!engineDenied)return false;
                if(!observeBackend(task)){task.gateReason="REQUIRED_BOUNDARY_UNAVAILABLE";return true;}
                boolean[] fenced={false};task.gateReason="";
                boolean authorized=Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,(Runnable)()->{
                    try {
                        // withBoundaries holds the real Bootstrap publication monitor. Checking
                        // only before entering it leaves a new emit/source tail race.
                        if(!checkBridge(task.object)||((Integer)pendingDeliveries.invoke(null))!=0) {
                            task.gateReason="CAPTURE_PUBLICATION_PENDING";return;
                        }
                        Set<UUID> live=workerBindings.get(task.object);
                        if(live==null||runtime.groupAuthority(live)!=subject||!subject.terminal)return;
                        for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task.object))return;
                        fenced[0]=Boolean.TRUE.equals(fenceTask.invoke(null,SINK,task.object));
                        if(fenced[0]) {
                            task.policyFenced=true;task.policyAuthority=subject;task.admissionFenced=true;
                            if(task.admissionReleased) { task.fenceReleaseRecord=null;task.fenceReleaseAck=null; }
                            task.admissionReleased=false;
                        }
                    } catch(ReflectiveOperationException failure){throw new IllegalStateException("POLICY_FENCE_UNAVAILABLE",failure);}
                }));
                if(!authorized)task.gateReason="REQUIRED_TASK_BOUNDARY_UNAVAILABLE";
                if(fenced[0])runtime.journal.append("TASK_POLICY/1\tADMISSION_FENCED\t"+runtime.session+"\t"+encode(task.definition.getName())
                        +"\t"+subject.id+"\t"+(oldGeneration?"POLICY_TERMINAL_NEWER_GENERATION":"EXACT_ENGINE_DENIAL")+"\tFENCE_INSTALLED");
                return true;
            } catch(ReflectiveOperationException|RuntimeException unavailable) {
                task.gateReason="POLICY_FENCE_PENDING:"+unavailable.getClass().getSimpleName();return true;
            } finally {subject.commitGate.unlock();}
        }
    }
    /** Source loss is not revocation. A fully delivered, known mixed/revoked scope is. */
    private boolean policyWithdrawn(Task task) {
        Set<UUID> live=workerBindings.get(task.object);if(live==null||live.isEmpty())return false;
        Set<UUID> related=new HashSet<>(task.subjects);related.addAll(live);
        for(UUID id:related)if(runtime.canonicalSubject(id)==null)return false;
        ProRuntime.Subject scope=runtime.groupAuthority(related);
        if(scope==null||!scope.terminal)return true;
        for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task.object))return true;
        return false;
    }
    /** Read-only retirement for an old-generation policy fence. Never cancels/clears with its old intent. */
    private boolean policyReleaseReady(Task task)throws ReflectiveOperationException {
        if(!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()
                ||!task.workerReferences.isEmpty()||!task.adoptedInvocations.isEmpty()
                ||((Integer)activeInvocations.invoke(null,task.object))!=0)return false;
        if(policyWithdrawn(task))return true; // registry only: shared callback/queue objects are untouched
        if(task.completion)return completionOwnerReleased(task);
        if(task.authority==null||!forkTerminal(task)||!forkQueuesAbsent(task))return false;
        if(((Integer)helperReferences.invoke(null,task.object))!=0||!waitersReleased(task))return false;
        if(!bindForkCaptures(task))return false;
        return forkCapturesAbsent(task)&&forkQueueReadbackCurrent(task);
    }
    private boolean retirePolicyFence(Task task)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||!gap.isEmpty()||capturePending()||pendingDeliveries==null||((Integer)pendingDeliveries.invoke(null))!=0
                    ||!observeBackend(task))return false;
            pollAdoptedInvocations(task);
            ProRuntime.Subject scope=task.policyAuthority==null?null:runtime.canonicalSubject(task.policyAuthority.id);
            if(scope==null||!scope.commitGate.tryLock())return false;
            try {
                if(runtime.canonicalSubject(task.policyAuthority.id)!=scope)return false;
                if(!task.admissionReleased&&!policyReleaseReady(task))return false;
                if(!releaseAdmissionResource(task,true))return false;
                task.policyFenced=false;task.policyAuthority=null;task.admissionFenced=false;return true;
            } finally {scope.commitGate.unlock();}
        }
    }
    /** Read at the real JDK entry/result writer. Capture uses only proven object relations. */
    boolean deniesEffect(Object object) {
        Set<UUID> related;
        synchronized(workerBindings) { related=workerBindings.get(object); }
        UUID direct=origins.get(object);
        Set<UUID> established=new HashSet<>();if(related!=null)established.addAll(related);if(direct!=null)established.add(direct);
        ProRuntime.Subject subject=runtime.groupAuthority(established);
        return subject!=null&&subject.terminal;
    }
    /** Exact single-subject relation used only to serialize a result writer with policy changes. */
    ProRuntime.Subject publicationSubject(Object object) {
        if(object==null)return null;
        Set<UUID> related;
        synchronized(workerBindings) { related=workerBindings.get(object); }
        UUID direct=origins.get(object);
        Set<UUID> established=new HashSet<>();if(related!=null)established.addAll(related);if(direct!=null)established.add(direct);
        return runtime.groupAuthority(established);
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
        ProRuntime.Subject canonical=runtime.canonicalSubject(subject.id);
        if(canonical!=null&&canonical!=subject) {qualify(canonical,runtime.groupOperation(canonical.id,operation));return;}
        for(Task task:tasks.values())if(!Collections.disjoint(task.subjects,subject.members)) {
            if(!runtime.sameGroup(task.subjects,subject)) {task.reason="SHARED_TASK_MULTIPLE_GROUPS";continue;}
            if(task.reason.equals("SHARED_TASK_MULTIPLE_GROUPS"))task.reason="";
            if(task.authority!=null&&task.authority!=subject) {
                if(runtime.canonicalSubject(task.authority.id)==subject&&task.operation!=null){
                    // Keep the original operation, generation, intent and ACK. The durable group
                    // membership authorizes continuing that same retirement, rather than recoding it.
                    if(task.reason.equals("SHARED_TASK_MULTIPLE_SUBJECTS")||task.reason.equals("OLD_TASK_INTENT_QUERY_ONLY"))task.reason="";
                    enqueue(task);continue;
                }
                task.reason="SHARED_TASK_MULTIPLE_SUBJECTS";continue;
            }
            if(task.ack!=null&&task.qualification!=subject.generation) { task.reason="OLD_TASK_INTENT_QUERY_ONLY";continue; }
            if(task.authority==subject&&task.operation!=null&&task.qualification==subject.generation)continue;
            task.authority=subject;assignOperation(task,operation);task.qualification=subject.generation;task.resources=null;
            enqueue(task);
        }
    }
    private void assignOperation(Task task,UUID operation) {
        if(!Objects.equals(task.operation,operation))removeOperation(task);
        task.operation=operation;
        if(operation!=null)operationTasks.computeIfAbsent(operation,ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(task);
    }
    private void removeOperation(Task task) {
        Set<Task> members=operationTasks.get(task.operation);
        if(members!=null&&members.remove(task)&&members.isEmpty())operationTasks.remove(task.operation);
    }
    private boolean exclusive(Task task) {
        return !task.subjects.isEmpty()&&runtime.sameGroup(task.subjects,task.authority);
    }
    private boolean qualified(Task task){
        if(task.authority==null)return false;
        ProRuntime.Subject scope=runtime.canonicalSubject(task.authority.id);
        return scope!=null&&scope.terminal&&(scope==task.authority?scope.generation==task.qualification:runtime.groupDurable(scope.id)&&runtime.sameGroup(task.subjects,scope));
    }
    private void advance(Task task,long tick)throws ReflectiveOperationException {
        pollAdoptedInvocations(task);
        refreshQueueReceipts(task);
        flushWaiterFacts(task);
        if(task.result) { advanceResult(task,tick);return; }
        if(task.inline) { advanceObserved(task,tick);return; }
        if(task.forkJoin) { advanceForkJoin(task,tick);return; }
        if(task.delegated&&!exactFuture(task.object)) { advanceDelegate(task,tick);return; }
        if(!exactFuture(task.object)) {advanceDrained(task,tick);return;}
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"REQUIRED_TASK_BOUNDARY_UNAVAILABLE");return; }
        }
        if(!exclusive(task)||!task.reason.isEmpty())return;
        if(!qualified(task)) { resetWindow(task,"FENCED");return; }
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
            Object queue=taskQueue(task);
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
        if(!observeBackend(task))return gateFailure(task,"REQUIRED_QUEUE_BOUNDARY_UNAVAILABLE");
        ResourceBinding resources=resourceBinding(task);
        if(resources==null||resources.queue==null||!supportedQueue(resources.queue))return gateFailure(task,"QUEUE_BACKEND_UNSUPPORTED");
        List<java.util.concurrent.locks.ReentrantLock> acquired=new ArrayList<>();
        try {
            for(var lock:resources.locks) {
                if(!lock.tryLock())return gateFailure(task,"QUEUE_LOCK_BUSY");acquired.add(lock);
            }
            boolean[] invoked={false};
            Runnable bound=()->{
                try {
                    if(resourceBinding(task)!=resources) { gateFailure(task,"QUEUE_OWNER_REPLACED");return; }
                    action.run();invoked[0]=resourceBinding(task)==resources;
                }
                catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            boolean accepted;
            if(observationOnly)synchronized(LISTENERS) {
                accepted=checkBridge(task.object)&&gap.isEmpty()&&!capturePending()&&((Integer)pendingDeliveries.invoke(null))==0&&observeBackend(task)
                        &&Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,bound));
            } else accepted=guarded(task,bound);
            return accepted&&invoked[0];
        } finally { for(int i=acquired.size()-1;i>=0;i--)acquired.get(i).unlock(); }
    }
    /** Observing a shared callback's invocation never cancels or clears somebody else's callback object. */
    private void advanceDelegate(Task task,long tick)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"DELEGATE_BOUNDARY_UNAVAILABLE");return; }
        }
        if(!qualified(task))return;
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
    /** The actual JDK state transition also signals join/get waiters and cannot dispatch to a Mod override. */
    private static volatile Method cachedForkCancellation;
    private static Method forkCancellation()throws ReflectiveOperationException {
        Method cached=cachedForkCancellation;if(cached!=null)return cached;
        Method cancel=ForkJoinTask.class.getDeclaredMethod("trySetCancelled");
        if(cancel.getReturnType()!=int.class||!Modifier.isPrivate(cancel.getModifiers())||!cancel.trySetAccessible())
            throw new IllegalAccessException("FORKJOIN_CANCELLATION_STATE_UNAVAILABLE");
        cachedForkCancellation=cancel;return cancel;
    }
    private void advanceForkJoin(Task task,long tick)throws ReflectiveOperationException {
        for(var request:task.ownedQueueRequests.entrySet()) {
            if(!qualified(task)||!exclusive(task))
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
        if(task.admissionReleased&&task.admissionFenced) {
            task.fenceReleaseAck=runtime.journal.retryRejected(task.fenceReleaseRecord,task.fenceReleaseAck);
            if(task.scheduler instanceof ThreadPoolExecutor)releaseCompletionExecutorQueue(task);
            if(task.completion)finishReleasedCompletion(task,tick);else finishReleasedForkJoin(task,tick);return;
        }
        if(!task.admissionFenced&&task.completion&&completionReleased(task)) { advanceObserved(task,tick);return; }
        if(!exclusive(task)||!task.reason.isEmpty())return;
        if(!qualified(task)) { resetWindow(task,"FENCED");return; }
        if(task.ack==null) { task.intent=intent(task);task.ack=runtime.journal.append(task.intent); }
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);
        if(!confirmed(task.ack)) { resetWindow(task,"WAIT_ACK");return; }
        if(!task.admissionFenced||!task.cancelAccepted) {
            waitersReleased(task);
            Method cancel=forkCancellation();
            if(!guarded(task,()->{
                try {
                    task.admissionFenced=Boolean.TRUE.equals(fenceTask.invoke(null,SINK,task.object));
                    if(task.admissionFenced) {
                        // A previous policy-only registry release is not this new disposal cycle.
                        if(task.admissionReleased) {task.admissionReleased=false;task.fenceReleaseRecord=null;task.fenceReleaseAck=null;}
                        cancel.invoke(task.object);
                        task.cancelAccepted=((ForkJoinTask<?>)task.object).isCancelled();
                        task.policyFenced=false;task.policyAuthority=null; // existing qualified disposal now owns this fence
                    }
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
                Object observed=taskQueue(task);
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
                ||((Integer)helperReferences.invoke(null,task.object))!=0||!task.ownedQueueRequests.isEmpty()||!forkQueueReadbackCurrent(task)) {
            resetWindow(task,"FORKJOIN_QUEUE_OR_HELPER_REFERENCE_PRESENT");return;
        }
        if(!bindForkCaptures(task)) { resetWindow(task,"FORKJOIN_CAPTURE_FIELD_INACCESSIBLE");return; }
        if(!task.callbackFieldsReleased) {
            if(!guarded(task,()->{
                try {
                    long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                    if(admission[0]!=1||admission[1]!=0||!forkQueuesAbsent(task)||((Integer)helperReferences.invoke(null,task.object))!=0)return;
                    if(!forkTerminal(task)||task.object.getClass()!=task.definition)return;
                    for(var edge:task.forkCaptureEdges.entrySet())if(edge.getKey().get(task.object)!=edge.getValue())return;
                    if(!forkQueueReadbackCurrent(task))return;
                    for(Field field:task.forkCaptureEdges.keySet())field.set(task.object,null);
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }))return;
            if(!forkCapturesAbsent(task)||!forkQueueReadbackCurrent(task)) { resetWindow(task,"FORKJOIN_CAPTURE_READBACK_PENDING");return; }
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
    private boolean bindForkCaptures(Task task)throws ReflectiveOperationException {
        if(!task.forkCaptureBound||task.policyFenced) {
            for(Class<?> owner=task.definition;owner!=null&&owner!=ForkJoinTask.class;owner=owner.getSuperclass())
                for(Field field:owner.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive())continue;
                    if(!field.trySetAccessible())return false;
                    Object value=field.get(task.object);if(value==null)continue;
                    UUID direct=origins.get(value);Set<UUID> related=workerBindings.get(value);
                    boolean platform=knownDelegate(owner)&&jdkDelegateField(owner,field);
                    if(platform||runtime.groupTargets(task.authority.id).contains(direct)||related!=null&&runtime.sameGroup(related,task.authority))
                        task.forkCaptureEdges.put(field,value);
                }
            task.forkCaptureBound=true;
        }
        return true;
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
    /** One pool seed plus actual queue receipts; no pool/deque scan is repeated for each task. */
    private boolean forkQueuesAbsent(Task task)throws ReflectiveOperationException {
        task.forkQueueReadbackEpoch=-1;
        if(!(task.scheduler instanceof ForkJoinPool)||!task.ownedQueueRequests.isEmpty()||forkQueueSnapshot==null||forkQueueEpoch==null)return false;
        Object[] snapshot=(Object[])forkQueueSnapshot.invoke(null,task.scheduler,task.object);
        if(snapshot==null||snapshot.length!=3||!(snapshot[0] instanceof Long epoch)||epoch<0||!Integer.valueOf(1).equals(snapshot[1])) {
            task.gateReason="FORKJOIN_QUEUE_LOCATION_UNOBSERVED_OR_PRESENT";return false;
        }
        if(readField(ForkJoinPool.class,"queues",task.scheduler)==null&&!Boolean.TRUE.equals(resourceMethods().schedulerEnded.invoke(null,task.scheduler)))return false;
        task.forkQueueReadbackEpoch=epoch;return forkQueueReadbackCurrent(task);
    }
    private static boolean forkQueueReadbackCurrent(Task task)throws ReflectiveOperationException {
        return task.forkQueueReadbackEpoch>=0&&forkQueueEpoch!=null
                &&Long.valueOf(task.forkQueueReadbackEpoch).equals(forkQueueEpoch.invoke(null,task.scheduler));
    }
    private static Object[] locatedForkQueues(Task task)throws ReflectiveOperationException {
        if(forkQueueSnapshot==null||forkQueueEpoch==null)return null;
        Object[] snapshot=(Object[])forkQueueSnapshot.invoke(null,task.scheduler,task.object);
        if(snapshot==null||snapshot.length!=3||!(snapshot[0] instanceof Long epoch)||epoch<0
                ||!(snapshot[1] instanceof Integer state)||state<0||!(snapshot[2] instanceof Object[] queues)
                ||!Long.valueOf(epoch).equals(forkQueueEpoch.invoke(null,task.scheduler)))return null;
        return queues;
    }
    private void finishReleasedForkJoin(Task task,long tick)throws ReflectiveOperationException {
        if(!confirmed(task.fenceReleaseAck)||!confirmed(task.callbackReleaseAck)||!task.queueRecords.isEmpty())return;
        if(!forkQueuesAbsent(task)||!forkCapturesAbsent(task)||((Integer)helperReferences.invoke(null,task.object))!=0
                ||!waitersReleased(task)||!forkQueueReadbackCurrent(task)) { resetWindow(task,"FORKJOIN_REFERENCE_REASSERTION_OR_EXIT_PENDING");return; }
        if(task.entered&&!task.exitObserved||!task.entered&&!task.queueRemoved&&!task.blockedEntryObserved) {
            resetWindow(task,"FORKJOIN_START_OR_EXIT_UNCONFIRMED");return;
        }
        task.callableReleased=true;
        finishScope(task,tick,(task.entered?"EXIT_OBSERVED":"START_PRECLUDED")+"\tFORKJOIN_QUEUES_HELPER_LOCALS_CAPTURE_SHARES_RELEASED");
    }
    /** Query the known owner's share independently. Never extrapolate a worker-deque snapshot
     * to ForkJoin helper-stack retirement or to arbitrary third-party callback aliases. */
    private boolean completionOwnerReleased(Task task)throws ReflectiveOperationException {
        if(task.scheduler instanceof ForkJoinPool&&!forkQueuesAbsent(task))return false;
        if(!task.creationObserved||!completionFieldsAbsent(task,true)||!completionStacksAbsent(task)
                ||!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()
                ||!task.workerReferences.isEmpty()||!task.adoptedInvocations.isEmpty()
                ||((Integer)activeInvocations.invoke(null,task.object))!=0||((Integer)helperReferences.invoke(null,task.object))!=0||!waitersReleased(task))return false;
        if(task.scheduler instanceof ForkJoinPool)return forkQueueReadbackCurrent(task);
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
                Object observed=taskQueue(task);
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
        // A result can become terminal before its saved cancellation receipt is admitted.
        // Retry that exact record even when no further cancellation is needed or allowed.
        task.resultCancelAck=runtime.journal.retryRejected(task.resultCancelRecord,task.resultCancelAck);
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"RESULT_OBSERVER_UNAVAILABLE");return; }
        }
        finishResultCancellation(task);
        if(!qualified(task)||!exclusive(task))return;
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
        for(Task producer:actual)if(producer.authority==null||runtime.canonicalSubject(producer.authority.id)!=runtime.canonicalSubject(task.authority.id)||!qualified(producer)
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
            if(changed[0]) {
                task.resultCancellationAttempted=true;task.resultDrainPending=true;
                task.resultCancellationValue=cancellation;
                task.resultCancelRecord=PREFIX+"\tRESULT_CANCELLED\t"+task.id+"\t"+task.operation+"\t"+runtime.session+"\tEXACT_RESULT_CAS_OBSERVED_SUCCESSORS_SEPARATE";
                task.resultCancelAck=null;
            }
        })||!changed[0])return;
        finishResultCancellation(task);
    }
    /** Continue the already-completed CAS; never acquire a new cancellation grant. */
    private void finishResultCancellation(Task task)throws ReflectiveOperationException {
        if(!task.resultCancellationAttempted)return;
        task.resultCancelAck=task.resultCancelAck==null?runtime.journal.append(task.resultCancelRecord):runtime.journal.retryRejected(task.resultCancelRecord,task.resultCancelAck);
        if(!task.resultDrainPending)return;
        if(task.object.getClass()!=CompletableFuture.class||readField(CompletableFuture.class,"result",task.object)!=task.resultCancellationValue)
            throw new IllegalStateException("RESULT_CANCELLED_VALUE_REPLACED");
        // No Root/publication monitor is held here, including during shutdown.
        // On failure retain the exact value and retry its remaining completion stack.
        Method drain=CompletableFuture.class.getDeclaredMethod("postComplete");if(!drain.trySetAccessible())throw new IllegalAccessException("CF_POST_COMPLETE_UNAVAILABLE");
        drain.invoke(task.object);
        task.resultDrainPending=false;task.resultCancellationValue=null;
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
    private static Field readField(Class<?> type,String name)throws ReflectiveOperationException {
        var fields=READ_FIELDS.get(type);Field field=fields.get(name);
        if(field==null) {
            field=type.getDeclaredField(name);
            if(!field.trySetAccessible())throw new IllegalAccessException("FIELD_UNAVAILABLE:"+type.getName()+"."+name);
            Field prior=fields.putIfAbsent(name,field);if(prior!=null)field=prior;
        }
        return field;
    }
    private static Object readField(Class<?> type,String name,Object object)throws ReflectiveOperationException {
        return readField(type,name).get(object);
    }
    private ResourceBinding resourceBinding(Task task)throws ReflectiveOperationException {
        ProRuntime.Subject scope=task.authority==null?null:runtime.canonicalSubject(task.authority.id);
        ResourceBinding prior=task.resources;
        if(prior!=null) {
            if(!prior.current(task)) {invalidateResources(task);return null;}
            if(prior.sameScope(task,scope))return prior;
            task.resources=null;
        }
        Field queueField=task.scheduler instanceof ThreadPoolExecutor?readField(ThreadPoolExecutor.class,"workQueue"):
                task.scheduler instanceof java.util.Timer?readField(java.util.Timer.class,"queue"):null;
        Object queue=queueField==null?null:queueField.get(task.scheduler);
        if(task.queue!=null&&task.queue!=queue) {invalidateResources(task);return null;}
        var lockFields=new ArrayList<Field>();var locks=new ArrayList<java.util.concurrent.locks.ReentrantLock>();
        if(queue!=null&&supportedQueue(queue))for(String name:queue.getClass()==LinkedBlockingQueue.class?List.of("putLock","takeLock"):List.of("lock")) {
            Field field=readField(queue.getClass(),name);Object actual=field.get(queue);
            if(actual==null||actual.getClass()!=java.util.concurrent.locks.ReentrantLock.class) {
                task.gateReason="QUEUE_LOCK_LAYOUT_UNSUPPORTED";return null;
            }
            lockFields.add(field);locks.add((java.util.concurrent.locks.ReentrantLock)actual);
        }
        Field owner=null,delegate=null;
        if(exactFuture(task.object)&&task.object.getClass()!=FutureTask.class) {
            owner=readField(task.definition,"this$0");delegate=readField(task.definition,"outerTask");
        }
        ResourceBinding bound=new ResourceBinding(task.scheduler,queueField,queue,List.copyOf(lockFields),List.copyOf(locks),owner,delegate,
                task.authority,scope,task.qualification,scope==null?-1:scope.generation,task.backendImage,task.backendEpoch);
        if(!bound.current(task)) {invalidateResources(task);return null;}
        task.resources=bound;return bound;
    }
    private static void invalidateResources(Task task) {
        task.resources=null;task.coverageConfirmed=false;task.queue=null;task.queueRemoved=false;task.callableReleased=false;
        task.servicedOwnerQueues.clear();task.forkQueueReadbackEpoch=-1;
        task.gateReason="RESOURCE_OWNER_OR_LAYOUT_CHANGED";resetWindow(task,"RESOURCE_BINDING_CHANGED");
    }
    private Object taskQueue(Task task)throws ReflectiveOperationException {
        ResourceBinding bound=resourceBinding(task);return bound==null?null:bound.queue;
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
            if(!nodeType.isInstance(node)||!seen.add(node)) { task.waiterCoverageGap=true;return false; }
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
        Object[] observed=locatedForkQueues(task);
        if(observed==null){task.gateReason="FORKJOIN_QUEUE_LOCATION_PENDING";return;}
        Set<Object> locations=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(locations,observed);
        for(var pending:List.copyOf(task.ownedQueueRequests.entrySet()))
            if(!locations.contains(pending.getKey())||!Objects.equals(task.ownedQueueEpochs.get(pending.getKey()),task.backendEpoch))
                cancelOwnedQueueRelease.invoke(null,SINK,pending.getValue());
        for(Object queue:observed) {
            if(queue==null||queue.getClass()!=ForkQueueAccess.TYPE||ForkQueueAccess.OWNER.get(queue)==null)continue;
            if(task.ownedQueueRequests.containsKey(queue)) {
                if(!Objects.equals(task.ownedQueueEpochs.get(queue),task.backendEpoch))cancelOwnedQueueRelease.invoke(null,SINK,task.ownedQueueRequests.get(queue));
                continue;
            }
            if(task.queueRecords.size()+2*task.ownedQueueRequests.size()>=62)return;
            guarded(task,()->{
                try {
                    String[] boundaries=task.requiredBoundaries.clone();long epoch=task.backendEpoch;
                    ProRuntime.Subject authority=runtime.canonicalSubject(task.authority.id);long generation=authority.generation;
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
                boolean observerCurrent=checkBridge(task);Set<UUID> published=workerBindings.get(task);
                if(!observerCurrent||closing||!runtime.journal.healthy()||!gap.isEmpty()||capturePending()||((Integer)pendingDeliveries.invoke(null))!=0
                        ||!runtime.sameGroup(published,authority))return false;
                for(RecoveryTasks owner:LISTENERS)if(owner!=this&&owner.workerBindings.containsKey(task))return false;
                if(!authority.commitGate.tryLock())return false;
                try {
                    if(!authority.terminal||authority.generation!=generation)return false;
                    boolean[] invoked={false};
                    Runnable commit=()->{
                        try {
                            if(!checkBridge(task)||capturePending()||((Integer)pendingDeliveries.invoke(null))!=0)return;
                            action.run();invoked[0]=true;
                        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("TASK_PUBLICATION_QUERY_UNAVAILABLE",failure); }
                    };
                    return Boolean.TRUE.equals(withBoundaries.invoke(null,boundaries,epoch,commit))&&invoked[0];
                } finally { authority.commitGate.unlock(); }
            } catch(ReflectiveOperationException|RuntimeException unavailable) { recordTransient("OWNER_QUEUE_EXECUTION_UNCONFIRMED");return false; }
        }
    }
    private static void refreshDependentResult(Task task)throws ReflectiveOperationException {
        if(task.completionOutputs.isEmpty())return;
        for(Object output:task.completionOutputs)
            if(!standardCompletionSource(output)||readField(CompletableFuture.class,"result",output)==null) { task.dependentResultPending=true;return; }
        task.dependentResultPending=false;
    }
    /** Shutdown may finish a durable, already-cleared disposal without authorizing another mutation. */
    private boolean forkRetirementReady(Task task)throws ReflectiveOperationException {
        return task.forkJoin&&!task.completion&&forkQueuesAbsent(task)&&task.callbackFieldsReleased&&confirmed(task.callbackReleaseAck)
                &&task.executions.isEmpty()&&task.dispatches.isEmpty()&&task.submissions.isEmpty()
                &&task.workerReferences.isEmpty()&&task.adoptedInvocations.isEmpty()
                &&((Integer)activeInvocations.invoke(null,task.object))==0
                &&((Integer)helperReferences.invoke(null,task.object))==0&&waitersReleased(task)
                &&forkCapturesAbsent(task)&&forkQueueReadbackCurrent(task);
    }
    private boolean releaseAdmissionResource(Task task,boolean retiring)throws ReflectiveOperationException {
        if(!task.ownedQueueRequests.isEmpty())return false;
        if(task.fenceReleaseRecord==null) {
            task.fenceReleaseRecord=PREFIX+"\tFENCE_RELEASE_INTENT\t"+task.id+"\t"+runtime.session+"\t"+
                    (task.policyFenced?"POLICY_FENCE_REGISTRY_ONLY":"CLEARED_OWNED_CAPTURE_EDGES")+"\tNO_RUNNING_ADMISSIONS";
            task.fenceReleaseAck=runtime.journal.append(task.fenceReleaseRecord);
        }
        task.fenceReleaseAck=runtime.journal.retryRejected(task.fenceReleaseRecord,task.fenceReleaseAck);
        if(!confirmed(task.fenceReleaseAck))return false;
        if(!task.admissionReleased) {
            Runnable release=()->{
                try {
                    if(capturePending()||pendingDeliveries==null||((Integer)pendingDeliveries.invoke(null))!=0)return;
                    boolean ready=task.policyFenced?policyReleaseReady(task):retiring&&!task.completion?forkRetirementReady(task):
                            (task.completion?completionFieldsAbsent(task,true)&&completionStacksAbsent(task):forkCapturesAbsent(task))
                                    &&((Integer)helperReferences.invoke(null,task.object))==0;
                    if(ready)task.admissionReleased=Boolean.TRUE.equals(releaseFence.invoke(null,SINK,task.object));
                } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            boolean accepted;
            if(retiring) {
                // Only our registry reference is released here. No cancelled qualification authorizes field/queue writes.
                synchronized(LISTENERS) {
                    accepted=checkBridge(task.object)&&gap.isEmpty()&&!capturePending()&&((Integer)pendingDeliveries.invoke(null))==0&&observeBackend(task)
                            &&Boolean.TRUE.equals(withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,release));
                }
            } else accepted=guarded(task,release);
            if(!accepted||!task.admissionReleased)return false;
            task.fenceReleaseRecord=PREFIX+"\tFENCE_RELEASED\t"+task.id+"\t"+runtime.session+"\tREGISTRY_REFERENCE_REMOVED\t"+
                    (task.policyFenced?"POLICY_FENCE_ONLY_TASK_OBLIGATIONS_UNCHANGED":"OWNED_CAPTURE_EDGES_CLEARED");
            task.fenceReleaseAck=runtime.journal.append(task.fenceReleaseRecord);
        }
        return confirmed(task.fenceReleaseAck);
    }
    /** Only external submission queues use this queue lock; worker-owned deques are never treated as externally owned. */
    private void releaseSharedForkQueues(Task task)throws ReflectiveOperationException {
        Object[] observed=locatedForkQueues(task);
        if(observed==null){task.gateReason="FORKJOIN_QUEUE_LOCATION_PENDING";return;}
        for(Object queue:observed) {
            if(task.queueRecords.size()+2*task.ownedQueueRequests.size()>=64||task.nextQueueReceipt==Long.MAX_VALUE)return;
            if(queue==null||queue.getClass()!=ForkQueueAccess.TYPE||ForkQueueAccess.OWNER.get(queue)!=null)continue;
            // Never wait for a shared queue while holding Root, publication or certificate gates.
            if(!(boolean)ForkQueueAccess.SOURCE.compareAndSet(queue,0,1))continue;
            try {
                int span=ForkQueueAccess.TOP.getInt(queue)-ForkQueueAccess.BASE.getInt(queue);
                Object image=ForkQueueAccess.ARRAY.get(queue);
                if(!(image instanceof Object[] entries)||span<0||span>entries.length)continue;
                guarded(task,()->{
                    try {
                        long[] admission=(long[])admissionState.invoke(null,SINK,task.object);
                        if(admission[0]!=1||admission[1]!=0||ForkQueueAccess.OWNER.get(queue)!=null||ForkQueueAccess.ARRAY.get(queue)!=image)return;
                        Object[] current=locatedForkQueues(task);if(current==null)return;
                        boolean located=false;for(Object candidate:current)if(candidate==queue){located=true;break;}
                        if(!located)return;
                        // We hold the real shared-queue lock. The verified helper's owned path skips reacquiring it.
                        if(Boolean.TRUE.equals(ForkQueueAccess.REMOVE.invoke(queue,task.object,true))) {
                            task.queueRemoved=true;
                            queueReceipt(task,"QUEUE_REFERENCE_REMOVED","SHARED_FORKJOIN_SUBMISSION_QUEUE_ONLY");
                        }
                    } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                });
            } finally { ForkQueueAccess.SOURCE.setRelease(queue,0); }
        }
    }
    private static final class ForkQueueAccess {
        static final Class<?> TYPE=type();
        static final Field OWNER=field("owner"),ARRAY=field("array"),TOP=field("top"),BASE=field("base");
        static final Method REMOVE=remove();
        static final java.lang.invoke.VarHandle SOURCE=source();
        private static Class<?> type(){try{return Class.forName("java.util.concurrent.ForkJoinPool$WorkQueue",false,null);}catch(ClassNotFoundException unavailable){throw new ExceptionInInitializerError(unavailable);}}
        private static Field field(String name){try {Field field=TYPE.getDeclaredField(name);field.setAccessible(true);return field;}catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}}
        private static Method remove(){try {Method method=TYPE.getDeclaredMethod("tryRemove",ForkJoinTask.class,boolean.class);method.setAccessible(true);return method;}catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}}
        private static java.lang.invoke.VarHandle source(){try{return java.lang.invoke.MethodHandles.privateLookupIn(TYPE,java.lang.invoke.MethodHandles.lookup()).findVarHandle(TYPE,"source",int.class);}catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}}
    }
    private static final ConcurrentMap<Class<?>,List<Field>> COMPLETION_FIELDS=new ConcurrentHashMap<>();
    private static List<Field> completionFields(Task task)throws ReflectiveOperationException {
        if(!exactCompletion(task.object)||task.definition!=task.object.getClass())throw new IllegalAccessException("CF_DEFINITION_CHANGED");
        List<Field> cached=COMPLETION_FIELDS.get(task.definition);if(cached!=null)return cached;
        var result=new ArrayList<Field>();
        for(Class<?> type=task.definition;type!=null&&type.getName().startsWith("java.util.concurrent.CompletableFuture$");type=type.getSuperclass()) {
            for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                if(Modifier.isFinal(field.getModifiers())||!Set.of("src","snd","dep","fn","executor","base","next","srcs").contains(field.getName())
                        ||!field.trySetAccessible())throw new IllegalAccessException("CF_REFERENCE_LAYOUT_UNSUPPORTED:"+type.getName()+"#"+field.getName());
                result.add(field);
            }
        }
        List<Field> resolved=List.copyOf(result),prior=COMPLETION_FIELDS.putIfAbsent(task.definition,resolved);
        return prior==null?resolved:prior;
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
    private static final class CompletionCursor {
        Object head,previous,node;
        boolean initialized;
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        void reset(Object actualHead) {
            head=actualHead;previous=null;node=actualHead;initialized=true;seen.clear();
        }
    }
    /** Weak graph image: every reuse rereads the actual root and every observed volatile edge.
     * An equal head alone is insufficient: a node can be inserted in the middle of the same stack. */
    private static final class CompletionStackImage {
        final java.lang.ref.WeakReference<Object>[] nodes;
        @SuppressWarnings("unchecked") CompletionStackImage(List<Object> chain) {
            nodes=(java.lang.ref.WeakReference<Object>[])new java.lang.ref.WeakReference<?>[chain.size()];
            for(int i=0;i<nodes.length;i++)nodes[i]=new java.lang.ref.WeakReference<>(chain.get(i));
        }
        boolean current(CompletionLinks links,Object source) {
            Object head=links.stack.getVolatile(source),node=head;
            for(var reference:nodes) {
                Object observed=reference.get();
                if(observed==null||node!=observed)return false;
                node=links.next.getVolatile(observed);
            }
            return node==null&&links.stack.getVolatile(source)==head;
        }
    }
    private static boolean completionStacksAbsent(Task task)throws ReflectiveOperationException {
        CompletionLinks links=completionLinks();
        if(!links.node.isInstance(task.object))return true; // AsyncSupply/AsyncRun have no completion-stack link.
        if(task.completionSources.isEmpty())return false;
        for(Object source:task.completionSources) {
            if(!standardCompletionSource(source))return false;
            CompletionStackImage cached=task.completionReadbacks.get(source);
            if(cached!=null&&cached.current(links,source))continue;
            task.completionReadbacks.remove(source);
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            List<Object> chain=new ArrayList<>();
            for(Object node=links.stack.getVolatile(source);node!=null;node=links.next.getVolatile(node))
                if(node==task.object||!links.node.isInstance(node)||!seen.add(node))return false;else chain.add(node);
            CompletionStackImage image=new CompletionStackImage(chain);
            if(!image.current(links,source))return false;
            task.completionReadbacks.put(source,image);
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
                    CompletionCursor cursor=task.completionReleaseCursors.computeIfAbsent(source,k->new CompletionCursor());
                    Object head=links.stack.getVolatile(source);
                    if(!cursor.initialized||cursor.head!=head||cursor.node==null
                            ||cursor.previous!=null&&links.next.getVolatile(cursor.previous)!=cursor.node)cursor.reset(head);
                    for(int budget=0;cursor.node!=null&&budget<128;budget++) {
                        Object node=cursor.node;
                        if(!links.node.isInstance(node)||!cursor.seen.add(node))return;
                        Object next=links.next.getVolatile(node);
                        if(node==task.object) {
                            if(cursor.previous==null)links.stack.compareAndSet(source,node,next);
                            else links.next.compareAndSet(cursor.previous,node,next);
                            cursor.reset(links.stack.getVolatile(source)); // reread the actual chain after every CAS
                        } else { cursor.previous=node;cursor.node=next; }
                    }
                }
                // A saved cursor only advances the search. Release still requires
                // a fresh full walk of every actual source, including concurrent edits.
                if(completionStacksAbsent(task)) {
                    links.next.setVolatile(task.object,null);task.completionReleaseCursors.clear();released[0]=true;
                }
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }))return false;
        return released[0];
    }
    private void advanceDrained(Task task,long tick)throws ReflectiveOperationException {
        boolean timer=task.scheduler instanceof java.util.Timer;
        if(!drainedDispositionCurrent(task)) {resetWindow(task,"WAIT_ACTUAL_QUEUE_DRAIN_AND_RESOURCE_EXIT");return;}
        if(!qualified(task)) {resetWindow(task,"FENCED");return;}
        if(!task.executions.isEmpty()||!task.dispatches.isEmpty()||!task.submissions.isEmpty()||!task.workerReferences.isEmpty()
                ||!task.adoptedInvocations.isEmpty()||((Integer)activeInvocations.invoke(null,task.object))!=0) {resetWindow(task,"WAIT_DRAINED_RUNNABLE_ACTUAL_EXIT");return;}
        if(task.ack==null) {task.intent=intent(task);task.ack=runtime.journal.append(task.intent);}
        task.ack=runtime.journal.retryRejected(task.intent,task.ack);if(!confirmed(task.ack)) {resetWindow(task,"WAIT_ACK");return;}
        if(task.releaseAck==null) {task.releaseIntent=PREFIX+"\tCAPTURE_RELEASE_OBSERVED\t"+task.id+"\t"+task.operation+"\t"+runtime.session+"\t"+(timer?"TIMER_QUEUE_REMOVED_CANCELLED_STATE_ACTUAL_INVOCATION_EXIT":"PRIVATE_EXECUTOR_ACTUAL_DRAIN_AND_TERMINATION");task.releaseAck=runtime.journal.append(task.releaseIntent);}
        task.releaseAck=runtime.journal.retryRejected(task.releaseIntent,task.releaseAck);if(!confirmed(task.releaseAck)) {resetWindow(task,"WAIT_DRAIN_RESOURCE_ACK");return;}
        if(task.queue==null)task.queue=taskQueue(task);
        if(task.queue==null){resetWindow(task,"DRAINED_RUNNABLE_QUEUE_OWNER_UNOBSERVED");return;}
        task.callableReleased=true;finishScope(task,tick,(task.entered?"EXIT_OBSERVED":"START_PRECLUDED")+"\t"+(timer?"ACTUAL_TIMER_QUEUE_REMOVAL_AND_CANCELLED_STATE":"ACTUAL_PRIVATE_QUEUE_DRAIN_AND_POOL_TERMINATION"));
    }
    private boolean drainedDispositionCurrent(Task task)throws ReflectiveOperationException{
        if(task.authority==null||!exclusive(task)||!task.queueRemoved||drainedTasks.get(task.object)!=runtime.canonicalSubject(task.authority.id)
                ||task.entered&&!task.exitObserved)return false;
        if(task.scheduler instanceof java.util.Timer)return Boolean.TRUE.equals(resourceMethods().timerTaskRetired.invoke(null,task.object));
        return task.scheduler instanceof ExecutorService&&Boolean.TRUE.equals(resourceMethods().schedulerEnded.invoke(null,task.scheduler));
    }
    /** Confirm naturally completed calls without replaying callbacks or inventing cancellation authority. */
    private void advanceObserved(Task task,long tick)throws ReflectiveOperationException {
        synchronized(LISTENERS) {
            if(!checkBridge(task.object)||closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"REQUIRED_INLINE_BOUNDARY_UNAVAILABLE");return; }
        }
        if(!exclusive(task)||!task.reason.isEmpty())return;
        if(!qualified(task)) { resetWindow(task,"FENCED");return; }
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
            if(!checkBridge(task.object)||closing||!gap.isEmpty()||!observeBackend(task)) { resetWindow(task,"TASK_EVIDENCE_INCOMPLETE");return; }
            if(!task.adoptedInvocations.isEmpty()||((Integer)activeInvocations.invoke(null,task.object))!=0
                    ||((Integer)helperReferences.invoke(null,task.object))!=0) {
                resetWindow(task,"WAIT_INVOCATION_AND_SUBMISSION_RESERVATIONS");return;
            }
            if(!waiterFactsCurrent(task)) { resetWindow(task,"WAIT_WAITER_FACT_ACK");return; }
            if(capturePending()) { resetWindow(task,"TASK_EVIDENCE_PENDING_MERGE");return; }
            // Live facts are sufficient; do not emit a LOCAL_FACT proof record.
            task.state="LOCAL_TASK_SCOPE";
        }
    }
    /** One graph pass after local sampling, rather than a complete descendant traversal per task.
     * Cycles cannot manufacture success: every member must have current exit/reference facts and the required durable action records. */
    private void settleScopes(long tick) {
        synchronized(LISTENERS) {
            if(!checkBridge()||closing||!gap.isEmpty()||capturePending())return;
            try { if(((Integer)pendingDeliveries.invoke(null))!=0)return; }
            catch(ReflectiveOperationException unavailable) { return; }
            Set<Task> active=Collections.newSetFromMap(new IdentityHashMap<>());active.addAll(windows);
            Set<Task> unresolved=Collections.newSetFromMap(new IdentityHashMap<>());
            var pending=new ArrayDeque<Task>();
            for(Task task:windows) {
                boolean ready;
                try { ready=observeBackend(task)&&localCurrent(task,tick,0L); }
                catch(ReflectiveOperationException unavailable) { ready=false; }
                if(ready)for(Task child:task.children)if(!active.contains(child)) { ready=false;break; }
                if(!ready&&unresolved.add(task))pending.addLast(task);
            }
            while(!pending.isEmpty())for(Task parent:pending.removeFirst().parents)
                if(active.contains(parent)&&unresolved.add(parent))pending.addLast(parent);
            for(Task task:windows)task.state=unresolved.contains(task)?"SUCCESSORS_PENDING":"QUIESCENT_TASK_SCOPE";
        }
    }
    private boolean index(Map<Object,Set<Task>> index,Object value,Task task) {
        Set<Task> existing=index.get(value);
        if(existing!=null&&existing.contains(task))return true;
        if(existing==null) { existing=Collections.newSetFromMap(new IdentityHashMap<>());index.put(value,existing); }
        existing.add(task);return true;
    }
    /** Release our strong graph only after the whole connected scope has actual durable exit facts.
     * Weak provenance survives for late callbacks. Its collection is never used as release evidence. */
    private void retireCoreReferences(long tick) {
        synchronized(LISTENERS) {
            for(Settlement settled:settlements.values())if(settled.released) {
                if(settled.record==null)settled.record=PREFIX+"\tCORE_RELEASED\t"+settled.task+"\t"+settled.operation+"\t"+runtime.session+"\tOWNED_STRONG_GRAPH_CLEARED";
                settled.ack=settled.ack==null?runtime.journal.append(settled.record):runtime.journal.retryRejected(settled.record,settled.ack);
            }
            if(!checkBridge()||closing||!gap.isEmpty()||capturePending())return;
            try {
                if(((Integer)pendingDeliveries.invoke(null))!=0)return;
                Map<UUID,List<Task>> groups=new LinkedHashMap<>();
                for(Task task:windows)if(task.operation!=null&&task.authority!=null){
                    ProRuntime.Subject authority=runtime.canonicalSubject(task.authority.id);
                    if(authority!=null)groups.computeIfAbsent(authority.id,k->new ArrayList<>()).add(task);
                }
                for(var group:groups.values()) {
                    Set<Task> members=Collections.newSetFromMap(new IdentityHashMap<>());members.addAll(group);
                    boolean ready=true;
                    for(Task task:group)if(!currentFact(task,tick,0L)||!observeBackend(task)
                            ||!members.containsAll(task.children)||!members.containsAll(task.parents)) { ready=false;break; }
                    if(!ready)continue;
                    for(Task task:group)settlements.computeIfAbsent(task.id,k->new Settlement(task));
                    Task leader=group.get(0);
                    if(!guarded(leader,()->{
                        for(Task task:group) {
                            if(runtime.canonicalSubject(task.authority.id)!=runtime.canonicalSubject(leader.authority.id)||!currentFact(task,tick,0L))return;
                            if(exactFuture(task.object))try {if(!executorQueueAbsent(task)||!((Future<?>)task.object).isDone())return;}
                            catch(ReflectiveOperationException failure) {throw new IllegalStateException("QUEUE_RETIREMENT_READBACK_UNAVAILABLE",failure);}
                        }
                        for(Task task:group) {
                            retiredObjects.put(task.object,task.id);tasks.remove(task.object);removeOperation(task);
                            windows.remove(task);waiting.remove(task);policyPending.remove(task);task.policyQueued=false;
                            task.scheduler=null;task.queue=null;task.resources=null;task.completionSources.clear();task.completionOutputs.clear();task.completionReleaseCursors.clear();task.completionReadbacks.clear();
                            task.delegateOwners.clear();task.servicedOwnerQueues.clear();task.forkCaptureEdges.clear();
                            task.forkQueueReadbackEpoch=-1;
                            task.children.clear();task.parents.clear();
                            Settlement settled=settlements.get(task.id);settled.released=true;settled.record=null;settled.ack=null;
                        }
                        for(var index:List.of(producers,dependents,childrenByParent)) {
                            for(var iterator=index.entrySet().iterator();iterator.hasNext();) {
                                var entry=iterator.next();entry.getValue().removeAll(members);
                                if(entry.getValue().isEmpty())iterator.remove();
                            }
                        }
                    }))continue;
                    // No release fact is emitted from this stack while 'group' still retains Task/object.
                    // The following server tick writes metadata-only receipts after this frame returned.
                }
            } catch(ReflectiveOperationException failure) { recordTransient("CORE_TASK_REFERENCE_RELEASE_QUERY_UNAVAILABLE"); }
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
            if(!transientIssue.isEmpty())out.add("TASK_TRANSIENT:"+transientIssue);
            Set<UUID> targets=subject==null?Set.of():runtime.groupTargets(subject);
            if(!pendingCaptures.isEmpty()||(subject==null?!events.isEmpty():events.stream().anyMatch(event->!Collections.disjoint(event.subjects,targets))))out.add("TASK_EVIDENCE_PENDING_MERGE");
            for(var entry:captureGaps.entrySet()) {
                CaptureGap loss=entry.getValue();Task task=tasks.get(entry.getKey());
                if(task!=null&&operation.equals(task.operation)||!Collections.disjoint(loss.subjects,targets))
                    out.add("TASK_CAPTURE_GAP:"+(task==null?entry.getKey().getClass().getName():task.id)+":"+loss.reason);
            }
            for(Task task:tasks.values())if(operation.equals(task.operation)||!Collections.disjoint(task.subjects,targets))out.add("TASK:"+task.id+":"+task.state+":"+task.reason);
            for(Settlement settled:settlements.values())if(operation.equals(settled.operation)) {
                if(!settled.released||!confirmed(settled.ack))out.add("CORE_REFERENCE_RELEASE_ACK:"+settled.task);
                // The durable release is a past fact. New activity creates new work;
                // unrelated changes to the global boundary inventory cannot undo it.
            }
            for(PriorTask prior:priorTasks.values())if(operation.equals(prior.operation)&&!priorReleased.contains(prior.id))
                out.add("PRIOR_TASK_PROCESS_OR_RELEASE_UNPROVEN:"+prior.id);
            return List.copyOf(out);
        }
    }
    private void link(Task parent,Task child,long tick) {
        if(parent==child||parent.children.contains(child))return;
        linkObserved(parent,child,tick);
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
        task.state=state;
    }
    private boolean localCurrent(Task task,long tick,long captureEpoch) {
        return !rootPending(task.object)&&!taskGap(task.object)&&task.reason.isEmpty()&&task.authority!=null
                &&(!task.admissionFenced||task.admissionReleased&&confirmed(task.fenceReleaseAck))
                &&capabilityCurrent(task)&&qualified(task)&&exclusive(task)
                &&task.executions.isEmpty()&&task.dispatches.isEmpty()&&task.submissions.isEmpty()
                &&task.workerReferences.isEmpty()&&task.adoptedInvocations.isEmpty()&&task.waiters.isEmpty()&&!task.waiterCoverageGap&&waiterFactsCurrent(task)&&task.callableReleased
                &&confirmed(task.ack)&&confirmed(task.releaseAck)&&(task.inline||task.forkJoin||task.result||task.delegated||task.queue!=null)
                &&(!task.resultCancellationAttempted||!task.resultDrainPending&&confirmed(task.resultCancelAck))
                &&(task.result||(task.entered?task.exitObserved:task.queueRemoved||task.dispatchExitObserved||task.forkJoin&&task.blockedEntryObserved))
                &&invocationReferencesReleased(task)&&retirementReferencesCurrent(task);
    }
    private boolean capabilityCurrent(Task task) {
        try {
            return observeBackend(task);
        } catch(ReflectiveOperationException|RuntimeException unavailable) {return false;}
    }
    /** A prior successful release is history, not proof that an actual owner has not republished a reference. */
    private boolean retirementReferencesCurrent(Task task) {
        try {
            if(task.inline)return inlineClosed!=null&&Boolean.TRUE.equals(inlineClosed.invoke(null,task.object));
            if(task.result)return readField(CompletableFuture.class,"result",task.object)!=null&&waitersReleased(task);
            if(task.delegated&&!task.admissionFenced)return true;
            if(task.completion)return task.admissionFenced?completionOwnerReleased(task):completionReleased(task);
            if(task.forkJoin)return task.callbackFieldsReleased&&forkQueuesAbsent(task)&&forkCapturesAbsent(task)
                    &&waitersReleased(task)&&forkQueueReadbackCurrent(task);
            if(exactFuture(task.object))return ((Future<?>)task.object).isDone()&&callableReleased(task.object)&&waitersReleased(task);
            return true; // Delegated scopes claim their observed invocation, not disposal of the shared callable.
        } catch(ReflectiveOperationException|RuntimeException unavailable) {return false;}
    }
    private boolean invocationReferencesReleased(Task task) {
        if(activeInvocations==null||helperReferences==null)return false;
        try { return ((Integer)activeInvocations.invoke(null,task.object))==0
                &&((Integer)helperReferences.invoke(null,task.object))==0; }
        catch(ReflectiveOperationException|RuntimeException unavailable) { return false; }
    }
    private boolean currentFact(Task task,long tick,long captureEpoch) {
        return task.state.equals("QUIESCENT_TASK_SCOPE")&&localCurrent(task,tick,captureEpoch);
    }
    private boolean localFactCurrent(Task task,long tick,long observed) {
        return localCurrent(task,tick,observed);
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
            try { observed=observeBackend(task);ready=observed&&localFactCurrent(task,tick,0L); }
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
        return completionFieldsAbsent(task,true)&&completionStacksAbsent(task);
    }
    private boolean executorQueueAbsent(Task task)throws ReflectiveOperationException {
        boolean[] absent={false};
        return queueGuarded(task,()->{
            try {
                Object queue=taskQueue(task);if(queue==null||task.queue!=null&&task.queue!=queue)return;
                for(Object value:((BlockingQueue<?>)queue).toArray())if(value==task.object)return;
                absent[0]=true;
            } catch(ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
        },true)&&absent[0];
    }
    private static boolean callableReleased(Object task)throws ReflectiveOperationException {
        if(!exactFuture(task))return false;
        return readField(FutureTask.class,"callable",task)==null;
    }
    private static boolean supportedQueue(Object queue) {
        Class<?> type=queue.getClass();return type.getClassLoader()==null&&Set.of("java.util.concurrent.ArrayBlockingQueue",
                "java.util.concurrent.LinkedBlockingQueue","java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue").contains(type.getName());
    }
    List<String> status(UUID operation) {
        synchronized(LISTENERS) {
            checkBridge();long now=Integer.toUnsignedLong(runtime.server.getTickCount());
            var out=new ArrayList<String>();out.add("TASK_BACKEND:"+backend);
            if(capturePending())out.add("CURRENT_TASK_EVIDENCE_PENDING_SERVER_MERGE");
            if(!gap.isEmpty())out.add("TASK_GAP:"+gap);
            if(!transientIssue.isEmpty())out.add("TASK_TRANSIENT:"+transientIssue);
            var selected=new ArrayList<Task>();
            for(Task task:tasks.values())if(operation.equals(task.operation))selected.add(task);
            Set<Task> currentScopes=currentScopes(selected,now);
            for(Task task:selected) {
                boolean current=currentScopes.contains(task);
                String visible=task.state.equals("QUIESCENT_TASK_SCOPE")&&!current?"QUIESCENCE_STALE":task.state;
                out.add(task.id+":"+visible+":"+task.reason+":kind="+(task.inline?"CF_STAGE_INVOCATION":task.result?"CF_RESULT":task.completion?"CF_COMPLETION":task.delegated?"DELEGATED_INVOCATION":"TASK")+":active="+task.executions.size()+
                        ":callbacks="+task.dispatches.size()+":children="+task.children.size()+
                        ":currentScope="+current+":admissionFenced="+task.admissionFenced+
                        ":startPrecludedDurable="+confirmed(task.preclusionAck)+":callbackFieldsCleared="+task.callbackFieldsReleased+
                        ":sharedForkQueueReceipts="+(task.queueReceiptsDurable+task.queueRecords.values().stream().filter(RecoveryTasks::confirmed).count())+
                        ":admissionRegistryReleased="+(task.admissionReleased&&confirmed(task.fenceReleaseAck))+
                        ":dependentResultPending="+task.dependentResultPending+":ownerQueueRequests="+task.ownedQueueRequests.size()+
                        ":adoptedInvocations="+task.adoptedInvocations.size()+":waiters="+task.waiters.size()+
                        ":waiterExitsDurable="+(waiterFactsCurrent(task)?task.waiterExitsRecorded:0)+
                        ":gate="+task.gateReason+
                        ":captureGap="+(taskGap(task.object)?captureGaps.get(task.object).reason:"")+
                        ":definition="+task.definition.getName()+":scheduler="+(task.scheduler==null?"UNOBSERVED":task.scheduler.getClass().getName()));
            }
            for(Settlement settled:settlements.values())if(operation.equals(settled.operation))out.add(settled.task+":CORE_REFERENCES_RELEASED="+(settled.released&&confirmed(settled.ack)));
            out.addAll(remaining(operation));return List.copyOf(out);
        }
    }
    String dependencies(UUID operation,UUID subject) {
        synchronized(LISTENERS) {
            StringBuilder key=new StringBuilder(gap).append(':').append(captureEpochs.getOrDefault(subject,0L));
            if(!pendingCaptures.isEmpty())key.append("|ROOTS_PENDING:").append(changes.get());
            Set<UUID> targets=runtime.groupTargets(subject);
            for(var entry:captureGaps.entrySet())if(!Collections.disjoint(entry.getValue().subjects,targets))
                key.append("|CAPTURE_GAP:").append(System.identityHashCode(entry.getKey())).append(':').append(entry.getValue().reason);
            var rows=new java.util.TreeSet<String>();
            for(Task task:tasks.values())if(operation.equals(task.operation)||task.subjects.contains(subject))
                rows.add(task.id+":"+task.revision+":"+task.operation+":"+task.qualification+":"+task.state+":"+task.children.size());
            for(String row:rows)key.append('|').append(row);
            return key.toString();
        }
    }
    long epoch() { return changes.get(); }
    private void finishResultCancellationsForClose()throws IOException {
        for(Task task:List.copyOf(tasks.values()))if(task.result&&task.resultCancellationAttempted) {
            try{finishResultCancellation(task);}
            catch(ReflectiveOperationException|RuntimeException failure){throw new IOException("TASK_RESULT_COMPLETION_DRAIN_PENDING:"+task.id,failure);}
        }
    }
    @Override public void close()throws IOException {
        closing=true;
        if(detached) {
            retirementFactAck=runtime.journal.retryRejected(retirementFact,retirementFactAck);
            if(!confirmed(retirementFactAck))throw new IOException("TASK_OBSERVER_RELEASE_FACT_ACK_PENDING");
            return;
        }
        // Ownership of this merge transfers from the stopped server to its retirement worker.
        pollCaptureLosses();
        drain(System.nanoTime()/50_000_000L);
        advancePolicyFences();
        finishResultCancellationsForClose();
        synchronized(LISTENERS) {
            if(capturePublications!=0)throw new IOException("TASK_CAPTURE_PUBLICATION_IN_FLIGHT");
            if(capturePending())throw new IOException("TASK_RETIREMENT_EVENTS_PENDING");
            try {
                if(bridge!=null&&((Integer)pendingDeliveries.invoke(null))!=0)throw new IOException("TASK_CAPTURE_PUBLICATION_IN_FLIGHT");
                if(bridge!=null) {
                    Object[] state=captureState==null?null:(Object[])captureState.invoke(null,SINK);
                    if(state==null||state.length!=5)throw new IOException("TASK_CAPTURE_SCOPE_UNAVAILABLE");
                    if(((Object[][])state[4]).length!=0)throw new IOException("TASK_CAPTURE_LOSS_SCOPE_PENDING");
                }
            } catch(ReflectiveOperationException failure) { throw new IOException("TASK_CAPTURE_PUBLICATION_QUERY_UNAVAILABLE",failure); }
            checkBridge();
            for(Task task:tasks.values()) {
                if(rootPending(task.object))throw new IOException("TASK_ROOT_RESOLUTION_PENDING:"+task.id);
                if(taskGap(task.object))throw new IOException("TASK_RETIREMENT_GAP:"+captureGaps.get(task.object).reason);
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
                if(task.policyFenced) {
                    try { if(!retirePolicyFence(task))throw new IOException("TASK_POLICY_FENCE_RETIREMENT_PENDING:"+task.id); }
                    catch(ReflectiveOperationException failure) { throw new IOException("TASK_POLICY_FENCE_RETIREMENT_UNAVAILABLE",failure); }
                }
                if(task.admissionFenced&&!task.admissionReleased) {
                    try {
                        boolean ready=task.completion?completionFieldsAbsent(task,true)&&completionStacksAbsent(task):forkRetirementReady(task);
                        if(ready)releaseAdmissionResource(task,true);
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
                    task.resultCancelAck=runtime.journal.retryRejected(task.resultCancelRecord,task.resultCancelAck);
                    if(task.resultCancelRecord!=null&&!confirmed(task.resultCancelAck))throw new IOException("TASK_RESULT_CANCEL_FACT_ACK_PENDING:"+task.id);
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
                        if(!task.admissionReleased||!forkQueuesAbsent(task)||!forkCapturesAbsent(task)
                                ||((Integer)helperReferences.invoke(null,task.object))!=0||!forkQueueReadbackCurrent(task))
                            throw new IOException("FORKJOIN_RETIREMENT_REFERENCES_PENDING:"+task.id);
                    } catch(ReflectiveOperationException failure) { throw new IOException("FORKJOIN_RETIREMENT_QUERY_UNAVAILABLE",failure); }
                    continue;
                }
                if(!exactFuture(task.object)){
                    try{
                        if(!drainedDispositionCurrent(task)||!observeBackend(task)||!task.callableReleased||!confirmed(task.releaseAck))
                            throw new IOException("TASK_DRAIN_RETIREMENT_PENDING:"+task.id);
                    }catch(ReflectiveOperationException failure){throw new IOException("TASK_DRAIN_RETIREMENT_QUERY_UNAVAILABLE",failure);}
                    continue;
                }
                try { if(!observeBackend(task)||!executorQueueAbsent(task)||!callableReleased(task.object))throw new IOException("TASK_RETIREMENT_COVERAGE_OR_CALLABLE_UNRESOLVED:"+task.id); }
                catch(ReflectiveOperationException failure) { throw new IOException("TASK_RETIREMENT_COVERAGE_UNAVAILABLE",failure); }
                Future<?> future=(Future<?>)task.object;
                if(!future.isDone()||(task.entered?!task.exitObserved:!task.queueRemoved&&!task.dispatchExitObserved))
                    throw new IOException("TASK_EXIT_OR_START_PRECLUSION_PENDING:"+task.id);
            }
            if(!tasks.isEmpty()&&!gap.isEmpty())throw new IOException("TASK_RETIREMENT_GAP:"+gap);
            for(CaptureGap loss:captureGaps.values())if(!loss.subjects.isEmpty())throw new IOException("TASK_RETIREMENT_GAP:"+loss.reason);
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
            publishOwners();
            if(LISTENERS.isEmpty()&&bridge!=null) {
                try {
                    if(uninstall==null||!Boolean.TRUE.equals(uninstall.invoke(null,SINK))) {
                        LISTENERS.add(this);publishOwners();throw new IOException("TASK_OBSERVER_DETACH_UNCONFIRMED");
                    }
                } catch(ReflectiveOperationException|RuntimeException unavailable) {
                    LISTENERS.add(this);publishOwners();throw new IOException("TASK_OBSERVER_DETACH_UNCONFIRMED",unavailable);
                }
                bridge=null;bridgeRegistration=null;bridgeRegistrationEpoch=-1;
            }
            detached=true;
            // Every collection is owned by this observer. External queues, delegates and pools are untouched.
            events.clear();pendingCaptures.clear();pendingRoots.clear();tasks.clear();operationTasks.clear();producers.clear();dependents.clear();childrenByParent.clear();synchronized(origins){origins.clear();sharedOrigins.clear();}workerBindings.clear();captureEpochs.clear();captureGaps.clear();
            windows.clear();waiting.clear();policyPending.clear();
            retirementFact=PREFIX+"\tOBSERVER_RELEASED\t"+runtime.session+"\t"+epoch;
            retirementFactAck=runtime.journal.append(retirementFact);
            if(!confirmed(retirementFactAck))throw new IOException("TASK_OBSERVER_RELEASE_FACT_ACK_PENDING");
        }
    }
}
