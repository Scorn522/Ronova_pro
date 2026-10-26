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
    private static RecoveryTasks model;
    private static ProRuntime runtime;
    private static void model()throws Exception {
        if(model!=null)return;
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)f.get(null);
        runtime=(ProRuntime)unsafe.allocateInstance(ProRuntime.class);
        assign(ProRuntime.class,runtime,"subjects",new HashMap<>());
        assign(ProRuntime.class,runtime,"groupMembers",new HashMap<>());
        model=(RecoveryTasks)unsafe.allocateInstance(RecoveryTasks.class);
        assign(RecoveryTasks.class,model,"runtime",runtime);
        for(String name:List.of("pendingRoots","workerBindings","captureGaps","drainedTasks"))assign(RecoveryTasks.class,model,name,new IdentityHashMap<>());
        assign(RecoveryTasks.class,model,"events",new ConcurrentLinkedQueue<>());
        assign(RecoveryTasks.class,model,"pendingCaptures",new ConcurrentLinkedQueue<>());
        assign(RecoveryTasks.class,model,"gap","");
        assign(RecoveryTasks.class,null,"boundarySnapshot",QueryBoundary.class.getMethod("snapshot",String[].class));
        assign(RecoveryTasks.class,null,"boundaryCapabilities",null);
        assign(RecoveryTasks.class,null,"activeInvocations",QueryBoundary.class.getMethod("references",Object.class));
        assign(RecoveryTasks.class,null,"helperReferences",QueryBoundary.class.getMethod("references",Object.class));
    }
    private static void assign(Class<?> type,Object receiver,String name,Object value)throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(receiver,value);
    }
    private static ProRuntime.Subject subject()throws Exception {
        model();var subject=new ProRuntime.Subject();subject.generation=1;subject.terminal=true;
        runtime.subjects.put(subject.id,subject);return subject;
    }
    private static Object task(ProRuntime.Subject subject)throws Exception {
        var constructor=TASK.getDeclaredConstructor(Object.class,ClassLoader.class,boolean.class,Class.class);constructor.setAccessible(true);
        // A delegated invocation claims its observed execution, not disposal of
        // an invented FutureTask/callable or a synthetic durable ACK.
        Object task=constructor.newInstance(new Object(),null,false,null);
        set(task,"authority",subject);set(task,"qualification",1L);set(task,"revision",1L);
        values(task,"subjects").add(subject.id);set(task,"delegated",true);set(task,"queueRemoved",true);
        set(task,"callableReleased",true);set(task,"state","QUIESCENT_TASK_SCOPE");return task;
    }
    private static Object field(Object task,String name)throws Exception {
        Field field=TASK.getDeclaredField(name);field.setAccessible(true);return field.get(task);
    }
    private static void set(Object task,String name,Object value)throws Exception {assign(TASK,task,name,value);}
    @SuppressWarnings("unchecked") private static Set<Object> values(Object task,String name)throws Exception {return (Set<Object>)field(task,name);}
    private static Object invoke(String name,Class<?>[] types,Object...arguments)throws Exception {
        Method method=RecoveryTasks.class.getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(Modifier.isStatic(method.getModifiers())?null:model,arguments);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    private static boolean current(Object task,long tick,long epoch)throws Exception {return (Boolean)invoke("localCurrent",new Class<?>[]{TASK,long.class,long.class},task,tick,epoch);}
    private static boolean durable(Object task,long tick,long epoch)throws Exception {return (Boolean)invoke("currentFact",new Class<?>[]{TASK,long.class,long.class},task,tick,epoch);}
    private static void link(Object parent,Object child,long tick)throws Exception {invoke("linkObserved",new Class<?>[]{TASK,TASK,long.class},parent,child,tick);}
    private static void lateDescendant()throws Exception {
        var subject=subject();Object grand=task(subject),parent=task(subject),child=task(subject),unrelated=task(subject());
        link(grand,parent,0);set(grand,"state","QUIESCENT_TASK_SCOPE");set(parent,"state","QUIESCENT_TASK_SCOPE");
        long revision=(Long)field(grand,"revision");link(parent,child,21);
        require((Long)field(grand,"revision")==revision+1,"late child invalidates ancestor revision");
        require(!durable(grand,21,0)&&!durable(parent,21,0),"late child invalidates settled state");
        require(durable(unrelated,21,0),"unrelated graph retains live facts");
        CASES.add("LATE_DESCENDANT_INVALIDATES_ANCESTORS_WITHOUT_TOUCHING_INDEPENDENT_ROOT");
    }
    private static void duplicateEdge()throws Exception {
        var subject=subject();Object parent=task(subject),child=task(subject);
        link(parent,child,1);long revision=(Long)field(parent,"revision");link(parent,child,2);
        require((Long)field(parent,"revision")==revision,"duplicate edge is not new source revision");
        link(child,parent,3);revision=(Long)field(parent,"revision");invoke("changed",new Class<?>[]{TASK,long.class},child,4L);
        require((Long)field(parent,"revision")==revision+1,"cycle must invalidate each task once");
        CASES.add("IDEMPOTENT_EDGE_AND_CYCLE_SAFE_INVALIDATION");
    }
    private static void freshnessAndReceipts()throws Exception {
        Object task=task(subject());require(durable(task,20,0),"live obligations permit fact");
        QueryBoundary.references=1;
        try{require(!durable(task,20,0),"republished live reference revokes old fact");}finally{QueryBoundary.references=0;}
        set(task,"admissionFenced",true);require(!durable(task,20,0),"unreleased admission remains obligation");
        set(task,"admissionReleased",true);require(durable(task,20,0),"released admission rechecks all live facts");
        CASES.add("LIVE_REFERENCES_AND_ADMISSION_RECHECKED_WITHOUT_HISTORICAL_ACK");
    }
    private static void qualificationAndExecution()throws Exception {
        var subject=subject();Object task=task(subject());set(task,"authority",subject);values(task,"subjects").clear();values(task,"subjects").add(subject.id);
        require(current(task,20,0),"qualified live task");subject.generation++;
        require(!current(task,20,0),"generation change revokes authority");subject.generation--;
        for(String name:List.of("executions","dispatches","submissions","workerReferences","waiters")){
            values(task,name).add(new Object());require(!current(task,20,0),name+" remains a separate obligation");values(task,name).clear();
        }
        set(task,"callableReleased",false);require(!current(task,20,0),"callable release required");set(task,"callableReleased",true);
        QueryBoundary.ready=false;try{require(!current(task,20,0),"revoked capability blocks fact");}finally{QueryBoundary.ready=true;}
        set(task,"entered",true);set(task,"exitObserved",false);require(!current(task,20,0),"cancel cannot replace observed exit");
        CASES.add("QUALIFICATION_COVERAGE_AND_ACTUAL_EXIT_REQUIRED");
    }
    private static void scopeIsolation()throws Exception {
        var subject=subject();Object independent=task(subject),parent=task(subject),missing=task(subject);
        link(parent,missing,0);set(parent,"state","QUIESCENT_TASK_SCOPE");
        set(missing,"callableReleased",false);
        Set<?> current=(Set<?>)invoke("currentScopes",new Class<?>[]{Collection.class,long.class},List.of(independent,parent),20L);
        require(current.contains(independent)&&!current.contains(parent)&&!current.contains(missing),"missing obligation propagates only to connected ancestors");
        set(missing,"callableReleased",true);QueryBoundary.advance=true;
        try{require(((Set<?>)invoke("currentScopes",new Class<?>[]{Collection.class,long.class},List.of(independent,parent),20L)).isEmpty(),"mixed live backend generations are stale");}
        finally{QueryBoundary.advance=false;}
        CASES.add("QUERY_FAILURE_ISOLATION_AND_ACTUAL_BACKEND_CHANGE");
    }
    public static final class QueryBoundary {
        static long epoch=7;static boolean advance,ready=true;static int references;
        public static long[] snapshot(String[] required){return new long[]{advance?epoch++:epoch,ready?1:0};}
        public static int references(Object task){return references;}
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
