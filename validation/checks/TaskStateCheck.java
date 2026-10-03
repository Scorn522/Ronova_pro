package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Pure metadata regression against the actual executor helpers. Reflection supplies test state,
 * not production evidence. This does NOT exercise JDK transformations, Forge, ABI, or O02/O05.
 * Execute only in the separate stateChecks JVM; never run it inside an attached game process.
 */
public final class TaskStateCheck {
    private static final Class<?> TASK;
    private static final List<String> CASES=new ArrayList<>();
    static {
        try { TASK=Class.forName("dev.ronova.pro.RecoveryTasks$Task"); }
        catch(ClassNotFoundException missing) { throw new ExceptionInInitializerError(missing); }
    }
    private TaskStateCheck() { }
    public static void main(String[] arguments)throws Exception {
        lateDescendant();duplicateEdge();freshnessAndReceipts();qualificationAndExecution();scopeIsolation();bridgeOwnership();boundaryGate();inFlightDetach();
        for(String name:CASES)System.out.println("TASK_STATE_METADATA_CASE_PASS "+name);
        System.out.println("TASK_STATE_METADATA_CHECKS_PASS count="+CASES.size()+"; NO_PRODUCTION_OUTCOME_EVIDENCE");
    }
    private static ProRuntime.Subject subject() {
        var subject=new ProRuntime.Subject();subject.generation=1;subject.terminal=true;return subject;
    }
    private static Object task(ProRuntime.Subject subject)throws Exception {
        var constructor=TASK.getDeclaredConstructor(Object.class,ClassLoader.class,boolean.class,Class.class);constructor.setAccessible(true);
        var object=new FutureTask<Void>(()->null);Object task=constructor.newInstance(object,object.getClass().getClassLoader(),false,null);
        set(task,"authority",subject);set(task,"qualification",1L);set(task,"revision",1L);
        values(task,"subjects").add(subject.id);set(task,"queue",new LinkedBlockingQueue<>());set(task,"queueRemoved",true);
        set(task,"ack",CompletableFuture.completedFuture(null));set(task,"releaseAck",CompletableFuture.completedFuture(null));
        set(task,"coverageConfirmed",true);set(task,"backendEpoch",1L);set(task,"callableReleased",true);
        return task;
    }
    private static Object field(Object task,String name)throws Exception {
        Field field=TASK.getDeclaredField(name);field.setAccessible(true);return field.get(task);
    }
    private static void set(Object task,String name,Object value)throws Exception {
        Field field=TASK.getDeclaredField(name);field.setAccessible(true);field.set(task,value);
    }
    @SuppressWarnings("unchecked")
    private static Set<Object> values(Object task,String name)throws Exception { return (Set<Object>)field(task,name); }
    private static Object invoke(String name,Class<?>[] types,Object... arguments)throws Exception {
        Method method=RecoveryTasks.class.getDeclaredMethod(name,types);method.setAccessible(true);
        try { return method.invoke(null,arguments); }
        catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof Exception exception)throw exception;
            if(failure.getCause() instanceof Error error)throw error;
            throw failure;
        }
    }
    private static boolean sampled(Object task,long tick,long epoch)throws Exception {
        return (Boolean)invoke("observeStable",new Class<?>[]{TASK,long.class,long.class},task,tick,epoch);
    }
    private static boolean current(Object task,long tick,long epoch)throws Exception {
        return (Boolean)invoke("localCurrent",new Class<?>[]{TASK,long.class,long.class},task,tick,epoch);
    }
    private static boolean durable(Object task,long tick,long epoch)throws Exception {
        return (Boolean)invoke("currentFact",new Class<?>[]{TASK,long.class,long.class},task,tick,epoch);
    }
    private static void link(Object parent,Object child,long tick)throws Exception {
        invoke("linkObserved",new Class<?>[]{TASK,TASK,long.class},parent,child,tick);
    }
    private static void settled(Object task,long start,long epoch)throws Exception {
        require(!sampled(task,start,epoch),"a new window is not stable");
        require(!sampled(task,start+10,epoch),"ten ticks is not twenty");
        require(sampled(task,start+20,epoch),"continuous twenty-tick window");
        set(task,"state","QUIESCENT_TASK_SCOPE");receipt(task,epoch,CompletableFuture.completedFuture(null));
        require(durable(task,start+20,epoch),"matching current receipt");
    }
    private static void receipt(Object task,long epoch,CompletableFuture<Void> ack)throws Exception {
        set(task,"settledRevision",field(task,"revision"));set(task,"settledCaptureEpoch",epoch);
        set(task,"settledWindowStart",field(task,"stableSince"));set(task,"fact",ack);
        set(task,"settledBackendEpoch",field(task,"backendEpoch"));
    }
    private static void lateDescendant()throws Exception {
        var subject=subject();Object grand=task(subject),parent=task(subject),child=task(subject),unrelated=task(subject());
        link(grand,parent,0);settled(parent,0,0);settled(grand,0,0);settled(unrelated,0,0);
        Object oldFact=field(grand,"fact");link(parent,child,21);
        require(!current(parent,21,0)&&!current(grand,21,0),"late child invalidates all ancestors");
        require(field(grand,"fact")==oldFact&&((CompletableFuture<?>)oldFact).isDone(),"historical ACK retained");
        require(!durable(grand,21,0),"old ACK is not a current fact");
        require(current(unrelated,21,0),"unrelated task graph is not invalidated");
        CASES.add("LATE_DESCENDANT_INVALIDATES_ANCESTORS_AND_RETAINS_HISTORY");
    }
    private static void duplicateEdge()throws Exception {
        var subject=subject();Object parent=task(subject),child=task(subject);
        link(parent,child,1);long revision=(Long)field(parent,"revision");
        link(parent,child,2);require((Long)field(parent,"revision")==revision,"duplicate edge is not a new source revision");
        // Defensive identity traversal must terminate even for an unresolved cycle.
        link(child,parent,3);invoke("changed",new Class<?>[]{TASK,long.class},child,4L);
        require((Long)field(parent,"stableSince")==-1L,"cyclic relation cannot retain a stable window");
        CASES.add("IDEMPOTENT_EDGE_AND_CYCLE_SAFE_INVALIDATION");
    }
    private static void freshnessAndReceipts()throws Exception {
        Object task=task(subject());settled(task,0,0);
        require(current(task,30,0),"maximum ten-tick observation interval");
        require(!current(task,31,0),"expired observation cannot be current");
        require(!sampled(task,31,0),"gap restarts the window");
        require(!sampled(task,41,0)&&sampled(task,51,0),"new full window after gap");
        set(task,"state","QUIESCENT_TASK_SCOPE");
        require(!durable(task,51,0),"old window receipt cannot become current again");
        var ack=new CompletableFuture<Void>();receipt(task,0,ack);
        require(current(task,51,0)&&!durable(task,51,0),"current state is distinct from delayed durable ACK");
        ack.complete(null);require(durable(task,51,0),"matching ACK can confirm the current fact");
        require(!current(task,51,1)&&!durable(task,51,1),"capture invalidates before server merge");
        require(!sampled(task,50,1),"backward time cannot extend a window");
        CASES.add("SAMPLE_GAP_WINDOW_IDENTITY_DELAYED_ACK_AND_CAPTURE_EPOCH");
    }
    private static void qualificationAndExecution()throws Exception {
        var subject=subject();Object task=task(subject);settled(task,0,0);
        subject.terminal=false;require(!current(task,20,0),"revoked qualification");subject.terminal=true;
        subject.generation++;require(!current(task,20,0),"new generation is not old authority");subject.generation--;
        Object invocation=new Object();values(task,"executions").add(invocation);
        require(!current(task,20,0),"actual running invocation is not closed");values(task,"executions").clear();
        values(task,"workerReferences").add(invocation);
        require(!current(task,20,0),"callback exit does not clear worker locals");values(task,"workerReferences").clear();
        set(task,"callableReleased",false);require(!current(task,20,0),"callable remains a separate obligation");set(task,"callableReleased",true);
        set(task,"coverageConfirmed",false);require(!current(task,20,0),"missing boundaries revoke facts");set(task,"coverageConfirmed",true);
        set(task,"backendEpoch",2L);require(!current(task,20,0),"old backend generation cannot qualify new facts");set(task,"backendEpoch",1L);
        set(task,"entered",true);set(task,"exitObserved",false);
        require(!current(task,20,0),"cancel acceptance cannot replace an observed exit");
        CASES.add("QUALIFICATION_AND_ACTUAL_EXIT_ARE_REQUIRED");
    }
    /** Only the query is exercised; these metadata fixtures never authorize a production write. */
    private static void scopeIsolation()throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        RecoveryTasks owner=(RecoveryTasks)unsafe.allocateInstance(RecoveryTasks.class);
        Field gap=RecoveryTasks.class.getDeclaredField("gap");gap.setAccessible(true);gap.set(owner,"");
        Field epochs=RecoveryTasks.class.getDeclaredField("captureEpochs");epochs.setAccessible(true);epochs.set(owner,new HashMap<UUID,Long>());
        Field boundary=RecoveryTasks.class.getDeclaredField("boundarySnapshot");boundary.setAccessible(true);
        Object original=boundary.get(null);
        Method query=RecoveryTasks.class.getDeclaredMethod("currentScopes",Collection.class,long.class);query.setAccessible(true);
        var scheduler=new ThreadPoolExecutor(0,1,1,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
        try {
            boundary.set(null,QueryBoundary.class.getMethod("snapshot",String[].class));
            var subject=subject();Object independent=task(subject),parent=task(subject),missing=task(subject);
            link(parent,missing,0);
            for(Object item:List.of(independent,parent)) {
                set(item,"scheduler",scheduler);set(item,"backendEpoch",7L);settled(item,0,0);
                set(item,"localRevision",field(item,"revision"));set(item,"localCaptureEpoch",0L);
                set(item,"localWindowStart",0L);set(item,"localBackendEpoch",7L);set(item,"localAck",CompletableFuture.completedFuture(null));
            }
            for(long stale:new long[]{6L,-1L}) {
                set(missing,"backendEpoch",stale);QueryBoundary.epoch=7;QueryBoundary.advance=false;
                Set<?> current=(Set<?>)query.invoke(owner,List.of(independent,parent),20L);
                require(current.contains(independent),"a missing backend must not stale an independent root");
                require(!current.contains(parent)&&!current.contains(missing),"missing evidence still propagates to actual ancestors");
            }
            QueryBoundary.epoch=7;QueryBoundary.advance=true;
            require(((Set<?>)query.invoke(owner,List.of(independent,parent),20L)).isEmpty(),"a real backend change during the query remains stale");
        } finally { QueryBoundary.advance=false;boundary.set(null,original);scheduler.shutdown(); }
        CASES.add("QUERY_FAILURE_ISOLATION_AND_ACTUAL_BACKEND_CHANGE");
    }
    public static final class QueryBoundary {
        static long epoch=7;
        static boolean advance;
        public static long[] snapshot(String[] required) { return new long[]{advance?epoch++:epoch,1}; }
    }
    private static void boundaryGate() {
        // This bridge is app-loaded in a separate metadata JVM, never the production bootstrap instance.
        var calls=new AtomicInteger();String[] required={"test/Future","test/Worker"};
        Consumer<Object[]> sink=event->{};dev.ronova.pro.bootstrap.TaskBridge.install(sink);
        dev.ronova.pro.bootstrap.TaskBridge.boundaryState(required[0],true);
        long[] partial=dev.ronova.pro.bootstrap.TaskBridge.boundarySnapshot(required);
        require(partial[1]==0&&!dev.ronova.pro.bootstrap.TaskBridge.withBoundaries(required,partial[0],calls::incrementAndGet),"partial coverage blocks effects");
        dev.ronova.pro.bootstrap.TaskBridge.boundaryState(required[1],true);
        long[] ready=dev.ronova.pro.bootstrap.TaskBridge.boundarySnapshot(required);
        require(ready[1]==1&&dev.ronova.pro.bootstrap.TaskBridge.withBoundaries(required,ready[0],calls::incrementAndGet),"exact required coverage allows gate");
        dev.ronova.pro.bootstrap.TaskBridge.boundaryState(required[1],false);
        require(!dev.ronova.pro.bootstrap.TaskBridge.withBoundaries(required,ready[0],calls::incrementAndGet)&&calls.get()==1,"revoked coverage blocks old token");
        for(String name:required)dev.ronova.pro.bootstrap.TaskBridge.boundaryState(name,false);
        require(dev.ronova.pro.bootstrap.TaskBridge.uninstall(sink),"gate observer retirement");
        CASES.add("PARTIAL_AND_REVOKED_BACKEND_CANNOT_EXECUTE");
    }
    private static void inFlightDetach()throws Exception {
        Class<?> type=dev.ronova.pro.bootstrap.TaskBridge.class;
        Field registration=type.getDeclaredField("registration");registration.setAccessible(true);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Consumer<Object[]> sink=row->{entered.countDown();try { release.await(); }catch(InterruptedException ex){Thread.currentThread().interrupt();}};
        dev.ronova.pro.bootstrap.TaskBridge.install(sink);
        Object owner=registration.get(null);
        Method emit=type.getDeclaredMethod("emit",owner.getClass(),int.class,Object.class,Object.class,Object.class,Object.class);emit.setAccessible(true);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread publisher=new Thread(()->{try { emit.invoke(null,owner,2,new Object(),null,null,new Object()); }catch(Throwable ex){failure.set(ex);}},"metadata-event-publication");
        try {
            publisher.start();require(entered.await(2,TimeUnit.SECONDS),"publisher entered");
            require(!dev.ronova.pro.bootstrap.TaskBridge.uninstall(sink),"in-flight event prevents detach");
            require(dev.ronova.pro.bootstrap.TaskBridge.observerMatches(sink),"same owner retained until delivery finishes");
        } finally { release.countDown();publisher.join(2000); }
        require(!publisher.isAlive()&&failure.get()==null,"publication exited normally");
        require(dev.ronova.pro.bootstrap.TaskBridge.uninstall(sink),"owner can detach after delivery");
        CASES.add("IN_FLIGHT_EXIT_PUBLICATION_PREVENTS_EARLY_DETACH");
    }
    private static void bridgeOwnership()throws Exception {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge");
        Method install=bridge.getMethod("install",Consumer.class),uninstall=bridge.getMethod("uninstall",Object.class);
        Method matches=bridge.getMethod("observerMatches",Object.class),delivering=bridge.getMethod("deliveringTo",Object.class);
        Field registered=bridge.getDeclaredField("registration");registered.setAccessible(true);
        require(registered.get(null)==null,"metadata check requires its own unattached JVM");
        AtomicInteger seen=new AtomicInteger();
        @SuppressWarnings("unchecked") Consumer<Object[]>[] owner=new Consumer[1];
        owner[0]=event->{
            try { require(Boolean.TRUE.equals(delivering.invoke(null,owner[0])),"delivery belongs to this registration");seen.incrementAndGet(); }
            catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        };
        Consumer<Object[]> other=event->{throw new AssertionError("foreign observer must not run");};
        try {
            install.invoke(null,owner[0]);Object old=registered.get(null);
            install.invoke(null,owner[0]);require(registered.get(null)==old,"same installation is idempotent");
            try { install.invoke(null,other);throw new AssertionError("foreign replacement accepted"); }
            catch(InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException,"explicit owner conflict"); }
            require(Boolean.TRUE.equals(matches.invoke(null,owner[0])),"owner preserved after conflict");
            require(Boolean.FALSE.equals(uninstall.invoke(null,other)),"foreign owner cannot detach");
            Class<?> frame=Class.forName(bridge.getName()+"$Frame");
            Constructor<?> constructor=frame.getDeclaredConstructor(Object.class,frame,boolean.class,old.getClass());constructor.setAccessible(true);
            Object token=constructor.newInstance(new Object(),null,false,old);
            Field current=bridge.getDeclaredField("CURRENT");current.setAccessible(true);
            @SuppressWarnings("unchecked") ThreadLocal<Object> local=(ThreadLocal<Object>)current.get(null);
            local.set(token);
            require(Boolean.TRUE.equals(uninstall.invoke(null,owner[0])),"exact owner detached");
            install.invoke(null,owner[0]);Object fresh=registered.get(null);
            require(fresh!=old,"same sink after restart still has a new lifetime");
            require(bridge.getMethod("currentTask").invoke(null)==null&&bridge.getMethod("currentExecution").invoke(null)==null,"stale frame not inherited");
            require(local.get()==token,"physical frame retained solely for its real finally exit");
            local.remove(); // This metadata token has no real JVM invocation; do not leave it on the test thread.
            Method emit=bridge.getDeclaredMethod("emit",old.getClass(),int.class,Object.class,Object.class,Object.class,Object.class);emit.setAccessible(true);
            emit.invoke(null,old,2,new Object(),null,null,token);
            require(seen.get()==0,"old lifetime cannot deliver an exit to the new observer");
            emit.invoke(null,fresh,1,new Object(),null,null,new Object());
            require(seen.get()==1,"current registration delivers with owner identity");
            require(Boolean.FALSE.equals(delivering.invoke(null,owner[0])),"delivery context is cleared");
        } finally { uninstall.invoke(null,owner[0]); }
        CASES.add("BOOTSTRAP_OWNER_AND_REGISTRATION_LIFETIME_ISOLATION");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
