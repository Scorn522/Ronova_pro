#!/usr/bin/env python3
"""Focused task-commit/retirement regression. Not a Forge or full-Core test.

Compiles verbatim affected RecoveryTasks methods and its close fence block with
explicit runtime/journal/coverage fixtures. Uses the actual Bootstrap admission,
authority, publication-monitor and emit implementations, real threads and real
ForkJoinTask/Pool objects. The result-cancel-only mode compiles the actual complete
IntentJournal and JournalLineage, extracts actual result/authority methods, and
uses real CompletableFuture cancellation with bounded durable-writer rejection.
The task runtime/qualification/closure and final scope are explicit stand-ins.
"""
from pathlib import Path
import argparse
import subprocess
import tempfile
import uuid
from contextlib import nullcontext


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def program(task_source, bridge_source, baseline):
    methods = [
        method(task_source, "private boolean guarded("),
        method(task_source, "private static boolean gateFailure("),
        method(task_source, "private boolean ownerQueueAction("),
        method(task_source, "private boolean releaseAdmissionResource("),
        method(task_source, "private static boolean forkCapturesAbsent("),
        method(task_source, "private static boolean forkTerminal("),
        method(task_source, "private boolean forkQueuesAbsent("),
        method(task_source, "private static boolean confirmed("),
    ]
    if "private boolean forkRetirementReady(" in task_source:
        methods.append(method(task_source, "private boolean forkRetirementReady("))
    close = method(task_source, "@Override public void close(")
    start = close.index("if(task.admissionFenced&&!task.admissionReleased")
    end = close.index("if(task.preclusionRecord!=null", start)
    methods.append("void closeFence(Task task)throws IOException {synchronized(LISTENERS){" + close[start:end] + "}}")
    bridge_methods = [method(bridge_source, signature) for signature in (
        "public static synchronized boolean withBoundaries(",
        "public static synchronized boolean fenceTask(",
        "public static synchronized int activeInvocations(",
        "public static synchronized long[] admissionState(",
        "public static synchronized boolean releaseFence(",
        "public static synchronized int pendingDeliveries(",
        "private static void emit(",
    )]
    return HEADER + "\n".join(methods) + RUNTIME + "\n".join(bridge_methods) + TESTS.replace("BASELINE", str(baseline).lower())


HEADER = r'''
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

final class RecoveryTasks {
    static final Set<RecoveryTasks> LISTENERS=Collections.newSetFromMap(new IdentityHashMap<>());
    static final Consumer<Object[]> SINK=RecoveryTasks::receive;
    static Method withBoundaries,pendingDeliveries,activeInvocations,helperReferences,releaseFence;
    static final String PREFIX="TASK/6";
    final ProRuntime runtime=new ProRuntime();
    final Queue<Object> events=new ConcurrentLinkedQueue<>();
    final Map<Object,Set<UUID>> workerBindings=new IdentityHashMap<>();
    boolean closing;
    String gap="";
    Runnable beforeBoundary;
    static class Task {
        final UUID id=UUID.randomUUID();
        final Object object;
        final Class<?> definition;
        final ProRuntime.Subject authority;
        final String[] requiredBoundaries={"test/exact-boundary"};
        long backendEpoch=7,qualification=1;
        String gateReason="",fenceReleaseRecord;
        boolean forkJoin=true,completion,policyFenced,admissionFenced,admissionReleased;
        boolean callbackFieldsReleased=true,forkCaptureBound=true,entered,exitObserved;
        Object scheduler;
        CompletableFuture<Void> callbackReleaseAck=CompletableFuture.completedFuture(null),fenceReleaseAck;
        final Set<Object> executions=new HashSet<>(),dispatches=new HashSet<>(),submissions=new HashSet<>(),workerReferences=new HashSet<>();
        final Map<Object,Boolean> adoptedInvocations=new IdentityHashMap<>();
        final Map<Object,Object> ownedQueueRequests=new IdentityHashMap<>();
        final Map<Field,Object> forkCaptureEdges=new LinkedHashMap<>();
        boolean waitersAbsent=true;
        Task(Object object,ProRuntime.Subject authority)throws Exception {
            this.object=object;definition=object.getClass();this.authority=authority;
            scheduler=new ForkJoinPool(1);
            ((ForkJoinPool)scheduler).submit(()->{}).join();
            forkCaptureEdges.put(Job.class.getDeclaredField("payload"),null);
        }
    }
    RecoveryTasks()throws Exception {
        synchronized(LISTENERS){LISTENERS.clear();LISTENERS.add(this);}
        withBoundaries=TaskBridge.class.getMethod("withBoundaries",String[].class,long.class,Runnable.class);
        pendingDeliveries=TaskBridge.class.getMethod("pendingDeliveries");
        activeInvocations=TaskBridge.class.getMethod("activeInvocations",Object.class);
        helperReferences=TaskBridge.class.getMethod("helperReferences",Object.class);
        releaseFence=TaskBridge.class.getMethod("releaseFence",Object.class,Object.class);
        TaskBridge.reset(SINK);
    }
    static void receive(Object[] row) {
        synchronized(LISTENERS) {
            for(RecoveryTasks observer:LISTENERS) {
                Set<UUID> prior=observer.workerBindings.get(row[1]);
                Set<UUID> updated=new HashSet<>(prior);updated.add((UUID)row[2]);
                observer.workerBindings.put(row[1],Set.copyOf(updated));
            }
        }
    }
    void checkBridge(){}
    void recordGap(String value){gap=value;}
    boolean observeBackend(Task task){if(beforeBoundary!=null){Runnable run=beforeBoundary;beforeBoundary=null;run.run();}return true;}
    boolean qualified(Task task){return task.authority.terminal&&task.qualification==task.authority.generation;}
    boolean waitersReleased(Task task){return task.waitersAbsent;}
    boolean policyReleaseReady(Task task){throw new AssertionError("Policy lifecycle is outside this focused check");}
    boolean completionFieldsAbsent(Task task,boolean next){throw new AssertionError("CF lifecycle is outside this focused check");}
    boolean completionStacksAbsent(Task task){throw new AssertionError("CF lifecycle is outside this focused check");}
    Object readField(Class<?> type,String name,Object object)throws ReflectiveOperationException {
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    Task task()throws Exception {
        Task task=new Task(new Job(),runtime.subject);workerBindings.put(task.object,Set.of(runtime.subject.id));return task;
    }
    boolean guardedAction(Task task,Runnable action)throws Exception{return guarded(task,action);}
    boolean queueAction(Task task,Runnable action){return ownerQueueAction(task.object,task.authority,task.qualification,task.requiredBoundaries,task.backendEpoch,action);}
    void fence(Task task)throws Exception {
        boolean[] result={false};
        boolean gate=(Boolean)withBoundaries.invoke(null,task.requiredBoundaries,task.backendEpoch,(Runnable)()->result[0]=TaskBridge.fenceTask(SINK,task.object));
        if(!gate||!result[0])throw new AssertionError("Real Bootstrap authority did not install fence");
        task.admissionFenced=true;((Job)task.object).cancel(false);
    }
'''

RUNTIME = r'''
}
final class Job extends ForkJoinTask<Void> {
    Object payload;
    protected boolean exec(){return true;}
    public Void getRawResult(){return null;}
    protected void setRawResult(Void ignored){}
}
final class ProRuntime {
    final UUID session=UUID.randomUUID();
    final Subject subject=new Subject();
    final Journal journal=new Journal();
    static final class TestGate extends ReentrantLock {
        Runnable afterAcquire;
        @Override public boolean tryLock(){boolean acquired=super.tryLock();if(acquired&&afterAcquire!=null){Runnable run=afterAcquire;afterAcquire=null;run.run();}return acquired;}
    }
    static final class Subject {
        final UUID id=UUID.randomUUID();final TestGate commitGate=new TestGate();boolean terminal=true;long generation=1;
    }
    Subject canonicalSubject(UUID id){return subject.id.equals(id)?subject:null;}
    boolean sameGroup(Set<UUID> ids,Subject owner){return ids!=null&&ids.equals(Set.of(owner.id));}
    static final class Journal {
        boolean healthy(){return true;}
        CompletableFuture<Void> append(String ignored){return CompletableFuture.completedFuture(null);}
        CompletableFuture<Void> retryRejected(String ignored,CompletableFuture<Void> prior){return prior;}
    }
}
final class TaskBridge {
    static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    static final Set<String> certified=Set.of("test/exact-boundary");
    static final long certificationEpoch=7;
    static final int ADMISSION_LIMIT=4096,MAX_DEPTH=64;
    static final ThreadLocal<Registration> AUTHORITY=new ThreadLocal<>(),DELIVERING=new ThreadLocal<>();
    static final ThreadLocal<ArrayDeque<Object[]>> SOURCE_DELIVERIES=new ThreadLocal<>();
    static final AtomicLong DROPPED=new AtomicLong();
    static final Map<Object,Admission> ADMISSIONS=new IdentityHashMap<>();
    static final Map<Object,Integer> SUBMISSIONS=new IdentityHashMap<>();
    static volatile Registration registration;
    static int helpers;
    static class Registration {final Consumer<Object[]> sink;int delivering;Registration(Consumer<Object[]> sink){this.sink=sink;}}
    static class Admission {Registration owner;int active;boolean fenced;long denied;Admission(Registration owner){this.owner=owner;}}
    static synchronized void reset(Consumer<Object[]> sink){registration=new Registration(sink);ADMISSIONS.clear();SUBMISSIONS.clear();helpers=0;DROPPED.set(0);}
    static void lost(){DROPPED.incrementAndGet();}
    static void lost(Throwable ignored){lost();}
    public static synchronized int helperReferences(Object ignored){return helpers;}
    static Thread startDelivery(Object task,UUID other){
        Thread emitter=new Thread(()->emit(registration,24,task,other,null,null),"real-source-delivery");emitter.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(pendingDeliveries()==0){if(System.nanoTime()>deadline)throw new AssertionError("emit failed to start");Thread.onSpinWait();}
        return emitter;
    }
'''

TESTS = r'''
}
public final class TaskCommitCheck {
    static int assertions;
    static final boolean baseline=BASELINE;
    static void check(boolean good,String label){assertions++;if(!good)throw new AssertionError(label);}
    static void rejectedClose(RecoveryTasks owner,RecoveryTasks.Task task,String label)throws Exception {
        boolean rejected=false;try{owner.closeFence(task);}catch(IOException expected){rejected=true;}
        check(rejected&&!task.admissionReleased&&TaskBridge.admissionState(RecoveryTasks.SINK,task.object)[0]==1,label);
    }
    static void race(boolean queue)throws Exception {
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.task();
        Thread[] emitter={null};owner.runtime.subject.commitGate.afterAcquire=()->emitter[0]=TaskBridge.startDelivery(task.object,UUID.randomUUID());
        Queue<Object> originalQueue=new ArrayDeque<>();originalQueue.add(task.object);
        boolean accepted=queue?owner.queueAction(task,()->originalQueue.remove(task.object)):owner.guardedAction(task,()->((Job)task.object).cancel(false));
        emitter[0].join(5000);
        check(!emitter[0].isAlive()&&TaskBridge.pendingDeliveries()==0&&TaskBridge.DROPPED.get()==0,"actual emit completed without capture loss");
        check(owner.workerBindings.get(task.object).size()==2,"second real delivery published shared ownership");
        check(accepted==baseline,(queue?"owner queue":"ordinary disposal")+" rejects commit-window source race");
        check(queue?originalQueue.contains(task.object)!=baseline:((Job)task.object).isCancelled()==baseline,"shared task remains unchanged");
        ((ForkJoinPool)task.scheduler).shutdown();
    }
    public static void main(String[] ignored)throws Exception {
        race(false);race(true);
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.task();
        check(!TaskBridge.fenceTask(RecoveryTasks.SINK,task.object),"unqualified direct fence still denied");
        check(owner.guardedAction(task,()->((Job)task.object).cancel(false))&&((Job)task.object).isCancelled(),"clean same-group disposal still executes");
        AtomicBoolean removed=new AtomicBoolean();check(owner.queueAction(task,()->removed.set(true))&&removed.get(),"clean owner action still executes");
        owner.fence(task);
        CompletableFuture<Void> delayed=new CompletableFuture<>();task.callbackReleaseAck=delayed;
        rejectedClose(owner,task,"pending callback receipt prevents close release");
        delayed.complete(null);
        if(baseline){
            rejectedClose(owner,task,"R3 reproduces stranded ordinary ForkJoin fence after durable callback receipt");
        }else{
            task.executions.add(new Object());rejectedClose(owner,task,"running execution prevents release");task.executions.clear();
            task.dispatches.add(new Object());rejectedClose(owner,task,"dispatch prevents release");task.dispatches.clear();
            task.submissions.add(new Object());rejectedClose(owner,task,"captured submission prevents release");task.submissions.clear();
            task.workerReferences.add(new Object());rejectedClose(owner,task,"worker reference prevents release");task.workerReferences.clear();
            task.adoptedInvocations.put(new Object(),false);rejectedClose(owner,task,"adopted invocation prevents release");task.adoptedInvocations.clear();
            TaskBridge.ADMISSIONS.get(task.object).active=1;rejectedClose(owner,task,"Bootstrap admission prevents release");TaskBridge.ADMISSIONS.get(task.object).active=0;
            TaskBridge.SUBMISSIONS.put(task.object,1);rejectedClose(owner,task,"Bootstrap submission prevents release");TaskBridge.SUBMISSIONS.clear();
            TaskBridge.helpers=1;rejectedClose(owner,task,"helper reference prevents release");TaskBridge.helpers=0;
            task.waitersAbsent=false;rejectedClose(owner,task,"waiter prevents release");task.waitersAbsent=true;
            ((Job)task.object).payload=new Object();rejectedClose(owner,task,"capture reference prevents release");((Job)task.object).payload=null;
            CountDownLatch started=new CountDownLatch(1),resume=new CountDownLatch(1);
            ForkJoinPool pool=(ForkJoinPool)task.scheduler;
            ForkJoinTask<?> blocker=pool.submit(()->{started.countDown();try{resume.await();}catch(InterruptedException failure){throw new AssertionError(failure);}});
            if(!started.await(5,TimeUnit.SECONDS))throw new AssertionError("queue holder did not start");
            try{pool.execute((Job)task.object);rejectedClose(owner,task,"actual original pool queue prevents release");}
            finally{resume.countDown();blocker.join();if(!pool.awaitQuiescence(5,TimeUnit.SECONDS))throw new AssertionError("original pool did not drain");}
            task.callbackFieldsReleased=false;rejectedClose(owner,task,"uncleared disposal cannot use close release");task.callbackFieldsReleased=true;
            // A Bootstrap submission reservation arrives after close's first readiness check.
            owner.beforeBoundary=()->TaskBridge.SUBMISSIONS.put(task.object,1);
            rejectedClose(owner,task,"inner-monitor readiness catches new submission");TaskBridge.SUBMISSIONS.clear();
            owner.closeFence(task);
            check(task.admissionReleased&&TaskBridge.admissionState(RecoveryTasks.SINK,task.object)[0]==0,"close releases only the completed ordinary fence");
            check(RecoveryTasks.SINK!=null&&((Job)task.object).payload==null,"release does not restore captures");
        }
        ((ForkJoinPool)task.scheduler).shutdown();
        System.out.println((baseline?"R3 defect reproduction":"R4 focused regression")+": "+assertions+" assertions passed");
    }
}
'''


def result_program(task_source, bridge_source, baseline):
    """Actual cancellation methods/authority, actual IntentJournal, real result and writer."""
    header = HEADER.replace("final ProRuntime runtime=new ProRuntime();", """final ProRuntime runtime=new ProRuntime();
    final Map<Object,Set<Task>> producers=new IdentityHashMap<>();
    final Map<Object,Task> tasks=new IdentityHashMap<>();
    static Method inlineClosed;
    static void resetWindow(Task task,String state){task.state=state;}
    static void finishScope(Task task,long tick,String evidence){task.finished=true;}
    Task resultTask()throws Exception {
        Task task=new Task(new CompletableFuture<Object>(),runtime.subject);
        task.result=true;task.forkJoin=false;task.ownedResult=true;
        workerBindings.put(task.object,Set.of(runtime.subject.id));
        task.intent=intent(task);task.ack=runtime.journal.append(task.intent);task.ack.get(5,TimeUnit.SECONDS);
        Task producer=new Task(new Object(),runtime.subject);producer.inline=true;producer.forkJoin=false;
        producers.put(task.object,Set.of(producer));tasks.put(task.object,task);return task;
    }
    void advanceResultForCheck(Task task)throws Exception{advanceResult(task,1);}
    """)
    header = header.replace('String gateReason="",fenceReleaseRecord;', '''String gateReason="",fenceReleaseRecord;
        final UUID operation=UUID.randomUUID();
        String intent,releaseIntent,resultCancelRecord,state="";
        CompletableFuture<Void> ack,releaseAck,resultCancelAck;
        boolean inline,result,ownedResult,resultCancellationAttempted,resultDrainPending,finished,waiterCoverageGap,delegated,callableReleased;
        Object resultCancellationValue;
        Class<?> callbackDefinition;
        final Set<UUID> subjects=new HashSet<>();''')
    header = header.replace('this.object=object;definition=object.getClass();this.authority=authority;', 'this.object=object;definition=object.getClass();this.authority=authority;subjects.add(authority.id);')
    header = header.replace('scheduler=new ForkJoinPool(1);\n            ((ForkJoinPool)scheduler).submit(()->{}).join();\n            forkCaptureEdges.put(Job.class.getDeclaredField("payload"),null);', 'scheduler=null;')
    header = header.replace('    boolean queueAction(Task task,Runnable action){return ownerQueueAction(task.object,task.authority,task.qualification,task.requiredBoundaries,task.backendEpoch,action);}', '')
    header = header.replace('TaskBridge.reset(SINK);', 'TaskBridge.reset(SINK);\n        inlineClosed=TaskBridge.class.getMethod("inlineClosed",Object.class);')
    methods = [method(task_source, signature) for signature in (
        'private void advanceResult(', 'private void cancelOwnedResult(',
        'private boolean guarded(', 'private static boolean gateFailure(',
        'private static boolean confirmed(', 'private String intent(',
        'private static String encode(', 'private static boolean exactFuture(', 'private boolean exclusive(',
    )]
    continuation = 'private void finishResultCancellation('
    close_continuation = 'private void finishResultCancellationsForClose('
    if continuation in task_source:
        methods.append(method(task_source, continuation))
        methods.append(method(task_source, close_continuation))
    close = method(task_source, '@Override public void close(')
    result_branch = method(close, 'if(task.result) {')
    methods.append('void closeResult(Task selected)throws IOException {' + ('finishResultCancellationsForClose();' if continuation in task_source else '') + 'for(Task task:List.of(selected)){' + result_branch + '}}')
    runtime = RUNTIME.replace('final Journal journal=new Journal();', '''final java.nio.file.Path journalDirectory;
    final IntentJournal journal;
    ProRuntime(){try{journalDirectory=java.nio.file.Files.createTempDirectory("result-journal-");journal=new IntentJournal(journalDirectory);}catch(IOException failure){throw new UncheckedIOException(failure);}}''')
    # The old Journal fixture is removed; this mode compiles the complete production class.
    journal_start = runtime.index('    static final class Journal {')
    journal_end = runtime.index('\n}\nfinal class TaskBridge', journal_start)
    runtime = runtime[:journal_start] + runtime[journal_end:]
    runtime = runtime.replace('static int helpers;', 'static int helpers;\n    public static boolean inlineClosed(Object ignored){return true;}')
    bridge_methods = [method(bridge_source, signature) for signature in (
        'public static synchronized boolean withBoundaries(',
        'public static synchronized boolean fenceTask(',
        'public static synchronized int activeInvocations(',
        'public static synchronized long[] admissionState(',
        'public static synchronized boolean releaseFence(',
        'public static synchronized int pendingDeliveries(',
        'private static void emit(',
    )]
    return ('package dev.ronova.pro;\nimport dev.ronova.pro.persistence.JournalLineage;\n' + header
            + '\n'.join(methods) + runtime + '\n'.join(bridge_methods)
            + RESULT_TESTS.replace('BASELINE', str(baseline).lower()))


RESULT_TESTS = r'''
}
public final class TaskCommitCheck {
    static int assertions;
    static final boolean baseline=BASELINE;
    static void check(boolean good,String label){assertions++;if(!good)throw new AssertionError(label);}
    static boolean confirmed(CompletableFuture<Void> ack){return ack!=null&&ack.isDone()&&!ack.isCompletedExceptionally()&&!ack.isCancelled();}
    static final class WriterHold implements AutoCloseable {
        final CountDownLatch release=new CountDownLatch(1);
        final List<CompletableFuture<Void>> fillers=new ArrayList<>();
        final ThreadPoolExecutor writer;
        WriterHold(IntentJournal journal,boolean fill)throws Exception {
            Field field=IntentJournal.class.getDeclaredField("writer");field.setAccessible(true);writer=(ThreadPoolExecutor)field.get(journal);
            CountDownLatch entered=new CountDownLatch(1);
            writer.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new AssertionError(failure);}});
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("actual writer did not pause");
            if(fill){for(int i=0;i<256;i++)fillers.add(journal.append("FILLER\t"+i));check(writer.getQueue().remainingCapacity()==0,"actual bounded journal queue filled");}
        }
        public void close()throws Exception {release.countDown();for(var ack:fillers)ack.get(5,TimeUnit.SECONDS);}
    }
    static boolean closeBlocked(RecoveryTasks owner,RecoveryTasks.Task task)throws Exception {
        try{owner.closeResult(task);return false;}catch(IOException failure){
            if(!failure.getMessage().startsWith("TASK_RESULT_CANCEL_FACT_ACK_PENDING:"))throw failure;return true;
        }
    }
    static void primeCancellationIntent(RecoveryTasks owner,RecoveryTasks.Task task)throws Exception {
        task.resultCancelRecord="TASK/6\tRESULT_CANCEL_INTENT\t"+task.id+"\t"+task.operation+"\t"+owner.runtime.session+"\tEXACT_OBSERVED_RESULT_ALL_PRODUCERS_CLOSED";
        task.resultCancelAck=owner.runtime.journal.append(task.resultCancelRecord);task.resultCancelAck.get(5,TimeUnit.SECONDS);
    }
    static long count(IntentJournal journal,String record){return journal.history().stream().filter(record::equals).count();}
    static void afterCas()throws Exception {
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.resultTask();
        try {
            primeCancellationIntent(owner,task);String intent=task.resultCancelRecord;
            Object cancelled;
            try(WriterHold full=new WriterHold(owner.runtime.journal,true)) {
                owner.advanceResultForCheck(task);
                check(((CompletableFuture<?>)task.object).isCancelled()&&task.resultCancellationAttempted,"real result CAS cancelled under original Bootstrap authority");
                cancelled=owner.readField(CompletableFuture.class,"result",task.object);
                check(task.resultCancelRecord.startsWith("TASK/6\tRESULT_CANCELLED\t")&&owner.runtime.journal.backpressured(task.resultCancelAck),"actual post-CAS fact append rejected by bounded writer");
                check(!task.finished&&task.state.equals("RESULT_CANCEL_FACT_ACK_PENDING"),"cancellation without fact ACK cannot settle");
                check(closeBlocked(owner,task)!=baseline,"shutdown waits for rejected cancellation fact");
            }
            String fact=task.resultCancelRecord;
            owner.runtime.subject.terminal=false; // Retrying a receipt must not borrow a fresh disposal grant.
            owner.advanceResultForCheck(task);
            if(baseline)check(owner.runtime.journal.backpressured(task.resultCancelAck)&&count(owner.runtime.journal,fact)==0,"R5 reproduces permanent post-CAS rejected receipt");
            else {
                task.resultCancelAck.get(5,TimeUnit.SECONDS);
                check(count(owner.runtime.journal,fact)==1&&count(owner.runtime.journal,intent)==1,"same original records durably accepted once after pressure clears");
                check(owner.readField(CompletableFuture.class,"result",task.object)==cancelled&&!task.finished,"receipt retry neither recancels nor requalifies revoked result");
                check(!closeBlocked(owner,task),"shutdown continues after actual result-cancellation ACK");
                owner.runtime.journal.close();
                try(IntentJournal reopened=new IntentJournal(owner.runtime.journalDirectory)){check(count(reopened,fact)==1,"cancellation receipt survives actual journal reopen");}
            }
        } finally {owner.runtime.journal.close();}
    }
    static void naturalCompletion()throws Exception {
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.resultTask();
        try {
            try(WriterHold full=new WriterHold(owner.runtime.journal,true)) {
                owner.advanceResultForCheck(task);
                check(owner.runtime.journal.backpressured(task.resultCancelAck)&&!task.resultCancellationAttempted&&!((CompletableFuture<?>)task.object).isDone(),"actual rejected intent cannot authorize result CAS");
                @SuppressWarnings("unchecked") CompletableFuture<Object> future=(CompletableFuture<Object>)task.object;
                future.complete("natural");
            }
            String intent=task.resultCancelRecord;
            owner.advanceResultForCheck(task);
            if(baseline)check(owner.runtime.journal.backpressured(task.resultCancelAck)&&!task.finished,"R5 reproduces rejected intent stranded by natural completion");
            else {
                task.resultCancelAck.get(5,TimeUnit.SECONDS);owner.advanceResultForCheck(task);
                if(task.releaseAck!=null)task.releaseAck.get(5,TimeUnit.SECONDS);owner.advanceResultForCheck(task);
                check(task.finished&&"natural".equals(((CompletableFuture<?>)task.object).join())&&!task.resultCancellationAttempted,"natural completion settles after original intent ACK without cancellation");
                check(count(owner.runtime.journal,intent)==1&&owner.runtime.journal.history().stream().noneMatch(row->row.startsWith("TASK/6\tRESULT_CANCELLED\t")),"natural completion preserves intent without inventing cancellation fact");
            }
        } finally {owner.runtime.journal.close();}
    }
    static void closeContinuation()throws Exception {
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.resultTask();
        try {
            primeCancellationIntent(owner,task);
            try(WriterHold full=new WriterHold(owner.runtime.journal,true)){owner.advanceResultForCheck(task);}
            String fact=task.resultCancelRecord;
            try(WriterHold delayed=new WriterHold(owner.runtime.journal,false)) {
                check(closeBlocked(owner,task)!=baseline,"shutdown independently retries the saved result fact");
                if(!baseline)check(!task.resultCancelAck.isDone()&&count(owner.runtime.journal,fact)==0,"shutdown cannot substitute queue admission for writer ACK");
            }
            if(!baseline){task.resultCancelAck.get(5,TimeUnit.SECONDS);check(!closeBlocked(owner,task)&&count(owner.runtime.journal,fact)==1,"shutdown finishes only after exact fact is durable");}
            CompletableFuture<Void> failed=new CompletableFuture<>();failed.completeExceptionally(new IOException("non-retryable I/O"));task.resultCancelAck=failed;
            check(closeBlocked(owner,task)!=baseline,"non-rejection failures remain unresolved during shutdown");
            if(!baseline)check(task.resultCancelAck==failed,"receipt continuation does not retry unproven I/O failure");
        } finally {owner.runtime.journal.close();}
    }
    static void drainFailure(boolean shutdown)throws Exception {
        RecoveryTasks owner=new RecoveryTasks();RecoveryTasks.Task task=owner.resultTask();
        CompletableFuture<?> future=(CompletableFuture<?>)task.object;
        AtomicInteger callbacks=new AtomicInteger();
        CompletableFuture<?> successor=future.whenComplete((value,error)->{
            if(Thread.holdsLock(RecoveryTasks.LISTENERS))throw new AssertionError("callback under publication monitor");
            callbacks.incrementAndGet();
        });
        CountDownLatch waiting=new CountDownLatch(1),released=new CountDownLatch(1);
        Thread waiter=new Thread(()->{waiting.countDown();try{future.join();}catch(CancellationException expected){}finally{released.countDown();}});
        waiter.start();waiting.await();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(future.getNumberOfDependents()<2&&System.nanoTime()<deadline)Thread.yield();
        check(future.getNumberOfDependents()>=2,"actual waiter and dependent on result stack");
        java.util.concurrent.DrainFaultNode.prepend(future);
        try {
            primeCancellationIntent(owner,task);
            try{owner.advanceResultForCheck(task);throw new AssertionError("drain fault did not escape");}
            catch(InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"actual postComplete failure escaped after CAS");}
            check(future.isCancelled()&&task.resultDrainPending&&!task.finished,"failed drain remains pending after exact cancellation");
            check(task.resultCancelRecord.startsWith("TASK/6\tRESULT_CANCELLED\t"),"cancellation fact captured before drain error");
            task.resultCancelAck.get(5,TimeUnit.SECONDS);
            check(count(owner.runtime.journal,task.resultCancelRecord)==1,"actual cancellation fact durable despite drain error");
            Object exact=owner.readField(CompletableFuture.class,"result",future);
            owner.runtime.subject.terminal=false;
            if(shutdown)owner.closeResult(task);else owner.advanceResultForCheck(task);
            check(released.await(5,TimeUnit.SECONDS)&&successor.isDone()&&callbacks.get()==1,"original waiter and successor drained without fresh cancellation grant");
            check(!task.resultDrainPending&&task.resultCancellationValue==null&&owner.readField(CompletableFuture.class,"result",future)==exact,"same cancelled value retained and continuation released");
            check(java.util.concurrent.DrainFaultNode.calls==1,"faulting completion not executed a second time");
        }finally {waiter.join(5000);owner.runtime.journal.close();}
    }
    public static void main(String[] ignored)throws Exception {
        afterCas();naturalCompletion();closeContinuation();
        if(!baseline){drainFailure(false);drainFailure(true);}
        System.out.println((baseline?"R5 result cancellation defect reproduction":"R6 result cancellation regression")+": "+assertions+" assertions passed; actual IntentJournal and bounded writer, real CompletableFuture CAS/postComplete; extracted task/result and Bootstrap authority methods; runtime/qualification/closure and final scope stand-ins");
    }
}
'''


def run_results(root, source, bridge_source, baseline):
    directory_path=root / ".work" / ("result-cancel-" + uuid.uuid4().hex)
    directory_path.mkdir(parents=True)
    with nullcontext(str(directory_path)) as directory:
        path = Path(directory)
        patch_source = path / "patch/java/util/concurrent/DrainFaultNode.java"
        patch_source.parent.mkdir(parents=True)
        patch_source.write_text('''package java.util.concurrent;
public final class DrainFaultNode extends CompletableFuture.Completion {
    public static int calls;
    public static void prepend(CompletableFuture<?> future) {
        calls=0;DrainFaultNode node=new DrainFaultNode();node.next=future.stack;future.stack=node;
    }
    CompletableFuture<?> tryFire(int mode) {calls++;throw new IllegalStateException("isolated completion fault");}
    boolean isLive() {return true;}
}
''', encoding="utf-8")
        patch_classes = path / "patch-classes"
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "--patch-module", "java.base=" + str(path / "patch"),
                        "-d", str(patch_classes), str(patch_source)], check=True)
        java = path / "TaskCommitCheck.java"
        java.write_text(result_program(source, bridge_source, baseline), encoding="utf-8")
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "--patch-module", "java.base=" + str(patch_classes),
                        "-encoding", "UTF-8", "-source", "17", "-target", "17", "-d", directory,
                        str(root / "src/journal/JournalLineage.java"), str(root / "src/core/IntentJournal.java"), str(java)], check=True)
        subprocess.run(["java", "--patch-module", "java.base=" + str(patch_classes),
                        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "-Djava.io.tmpdir=" + directory,
                        "-cp", directory, "dev.ronova.pro.TaskCommitCheck"], check=True)


def run(source, bridge_source, baseline):
    with tempfile.TemporaryDirectory(prefix="ronova-task-commit-") as directory:
        path = Path(directory)
        java = path / "TaskCommitCheck.java"
        java.write_text(program(source, bridge_source, baseline), encoding="utf-8")
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-encoding", "UTF-8", "-source", "17", "-target", "17", str(java)], check=True)
        subprocess.run(["java", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "-cp", directory, "TaskCommitCheck"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, help="Optional exact R3 RecoveryTasks.java for defect reproduction")
    parser.add_argument("--result-cancel-only", action="store_true", help="Run only the R6 result receipt retry/retirement path with the actual journal")
    parser.add_argument("--result-baseline", type=Path, help="Optional exact R5 RecoveryTasks.java for result receipt defect reproduction")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    bridge = (root / "src/bootstrap/TaskBridge.java").read_text(encoding="utf-8")
    if args.result_cancel_only:
        if args.result_baseline:
            run_results(root, args.result_baseline.read_text(encoding="utf-8"), bridge, True)
        run_results(root, (root / "src/core/RecoveryTasks.java").read_text(encoding="utf-8"), bridge, False)
        return
    if args.baseline:
        run(args.baseline.read_text(encoding="utf-8"), bridge, True)
    run((root / "src/core/RecoveryTasks.java").read_text(encoding="utf-8"), bridge, False)


if __name__ == "__main__":
    main()
