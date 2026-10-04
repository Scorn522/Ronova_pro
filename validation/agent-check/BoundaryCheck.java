package dev.ronova.pro.validation;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.ronova.pro.bootstrap.TaskBridge;
import isolated.Fields;
public final class BoundaryCheck {
    public static class Target { public int calls;public void write(float value){calls++;} }
    public static class Child extends Target { @Override public void write(float value){calls++;} }
    public static final class CloneableValue implements Cloneable { public int value=17;public Object copy()throws CloneNotSupportedException{return super.clone();} }
    private static volatile Object guardedIndexMap;
    private static volatile Object protectedMapValue;
    private static final class CollidingKey {
        final int id;
        CollidingKey(int id) { this.id=id; }
        @Override public int hashCode() { return 73; }
        @Override public boolean equals(Object other) { return other instanceof CollidingKey key&&key.id==id; }
    }
    private static Object policy;
    private static Class<?> owner;
    static void require(boolean value,String text) { if(!value)throw new AssertionError(text);System.out.println("PASS "+text); }
    static void deny(Object o)throws Exception { owner.getField("denied").set(null,o); }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("b-adapter-linkage")){bAdapterLinkage();return;}
        if(args.length>0&&args[0].equals("mod-group-returns")) {modGroupReturns(args[1],args[2]);return;}
        if(args.length>0&&args[0].equals("creation")) { creations();return; }
        if(args.length>0&&args[0].equals("source-map")) { sourceMaps();return; }
        if(args.length>0&&args[0].equals("fastutil-index")) { fastutilIndex();return; }
        if(args.length>0&&args[0].equals("index-writes")) { indexWrites();return; }
        if(args.length>0&&args[0].equals("abi-mismatch")) {
            Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
            require(String.valueOf(agent.getMethod("installState").invoke(null)).contains("TASK_BRIDGE_ABI_MISMATCH"),"mismatched bridge rejected before installing JDK hooks");
            var map=new HashMap<Integer,String>();map.put(1,"first");map.compute(1,(k,v)->"second");
            require(map.get(1).equals("second"),"ordinary JDK map remains usable after refused installation");
            System.out.println("ABI_MISMATCH_CHECK_PASS");return;
        }
        if(args.length>0&&args[0].equals("hashmap-index")) { hashmapIndex();return; }
        if(args.length>0&&args[0].equals("client-policy")) { clientPolicy();return; }
        if(args.length>0&&args[0].equals("guards")) { guards();return; }
        boolean taskInstallation=args.length>0&&args[0].equals("task-installation");
        if(args.length>0&&(args[0].equals("attach")||taskInstallation)) {
            try(URLClassLoader helper=new URLClassLoader(new URL[]{Path.of(args[1]).toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
                Class<?> installer=Class.forName("dev.ronova.pro.agent.SelfBootstrap",true,helper);
                require(Boolean.TRUE.equals(installer.getMethod("install",Path.class,Path.class,long.class).invoke(null,Path.of(args[1]),Path.of(args[2]),30L)),"zero-argument helper reads actual system agent state");
            }
        }
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        require(String.valueOf(agent.getMethod("installState").invoke(null)).startsWith("INSTALLED"),"agent installed");
        String[] scheduledBoundaries={"java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ScheduledThreadPoolExecutor",
                "java/util/concurrent/ScheduledThreadPoolExecutor$ScheduledFutureTask","java/util/concurrent/ScheduledThreadPoolExecutor$DelayedWorkQueue"};
        require(TaskBridge.boundarySnapshot(scheduledBoundaries)[1]==1,"scheduled task boundaries remain certified after backing installation");
        if(taskInstallation) {
            var timer=new ScheduledThreadPoolExecutor(1);
            try {
                require(timer.schedule(()->37,0,TimeUnit.MILLISECONDS).get(5,TimeUnit.SECONDS)==37,"installed scheduled task executes normally");
                var delayed=timer.schedule(()->{throw new AssertionError("cancelled delayed body ran");},1,TimeUnit.DAYS);
                require(delayed.cancel(false),"exact delayed task can be cancelled without interrupting its worker");
                require(TaskBridge.boundarySnapshot(scheduledBoundaries)[1]==1,"scheduled task use retains the installed boundaries");
            } finally {
                timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);timer.shutdown();
                require(timer.awaitTermination(5,TimeUnit.SECONDS),"test-owned scheduled pool exits");
            }
            System.out.println("TASK_INSTALLATION_CHECK_PASS");return;
        }
        try(URLClassLoader separate=new URLClassLoader(new URL[]{Path.of(args[3]).toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            owner=Class.forName("isolated.Policy",true,separate);
            try { Class.forName(owner.getName(),false,ClassLoader.getSystemClassLoader());throw new AssertionError("policy leaked"); } catch(ClassNotFoundException expected) {}
            var lookup=MethodHandles.publicLookup();
            TaskBridge.installGuards(owner,lookup.findStatic(owner,"field",MethodType.methodType(boolean.class,Object.class,Class.class,String.class,Object.class)),
                    lookup.findStatic(owner,"dispatch",MethodType.methodType(boolean.class,Object.class,String.class,Object.class)),
                    lookup.findStatic(owner,"task",MethodType.methodType(boolean.class,Object.class,String.class)));
            Object forgedTask=new Object();
            require(TaskBridge.enter(forgedTask)==null&&TaskBridge.enterDelegate(forgedTask,forgedTask)==null
                    &&TaskBridge.enterDispatch(forgedTask,forgedTask)==null,"external calls cannot fabricate an actual task entry");
            require(TaskBridge.helperEnter(forgedTask)==null&&TaskBridge.beginInline(forgedTask,null,null)==null,
                    "external calls cannot fabricate helper or inline frames");
            require(((Integer)owner.getMethod("ownReflectiveWrite").invoke(null))==17,"caller-sensitive private write keeps actual owner authority");
            Field privateField=owner.getDeclaredField("privateValue");
            try { privateField.setInt(null,21);throw new AssertionError("private access escaped"); }
            catch(IllegalAccessException expected) { require(!String.valueOf(expected.getMessage()).contains("RONOVA"),"unrelated caller retains JDK access denial"); }
            privateField.setAccessible(true);privateField.setInt(null,23);
            require(privateField.getInt(null)==23,"explicitly accessible unprotected private field remains writable");
            Fields protectedFields=new Fields(),neighbour=new Fields();deny(protectedFields);
            String[] names={"object","bool","b","c","s","i","l","f","d"};
            String[] methods={"set","setBoolean","setByte","setChar","setShort","setInt","setLong","setFloat","setDouble"};
            Class<?>[] types={Object.class,boolean.class,byte.class,char.class,short.class,int.class,long.class,float.class,double.class};
            Object[] values={"new",true,(byte)1,'a',(short)2,3,4L,5F,6D};
            for(int n=0;n<names.length;n++) {
                Field field=Fields.class.getField(names[n]);Method setter=Field.class.getMethod(methods[n],Object.class,types[n]);
                try { setter.invoke(field,protectedFields,values[n]);throw new AssertionError("write escaped "+names[n]); }
                catch(InvocationTargetException expected) { require(expected.getCause() instanceof IllegalAccessException,"reflection "+methods[n]+" refused at writer"); }
                setter.invoke(field,neighbour,values[n]);require(field.get(neighbour).equals(values[n]),"neighbour "+methods[n]+" unchanged semantics");
            }
            require(Boolean.TRUE.equals(agent.getMethod("reflectionFieldWriteGuarded").invoke(null)),"all reflection setters installed");
            Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");Field access=unsafeClass.getDeclaredField("theUnsafe");access.setAccessible(true);Object unsafe=access.get(null);
            Field integer=Fields.class.getField("i");long offset=(Long)unsafeClass.getMethod("objectFieldOffset",Field.class).invoke(unsafe,integer);
            TaskBridge.installUnsafeFields(owner,new Field[]{integer},new long[]{offset});
            for(String name:new String[]{"putInt","putIntVolatile","putOrderedInt"}) {
                unsafeClass.getMethod(name,Object.class,long.class,int.class).invoke(unsafe,protectedFields,offset,41);
                require(protectedFields.i==0,"Unsafe "+name+" refuses exact protected field");
                unsafeClass.getMethod(name,Object.class,long.class,int.class).invoke(unsafe,neighbour,offset,41);
                require(neighbour.i==41,"Unsafe "+name+" permits neighbour");
            }
            require(!((Boolean)unsafeClass.getMethod("compareAndSwapInt",Object.class,long.class,int.class,int.class).invoke(unsafe,protectedFields,offset,0,42)),"Unsafe CAS returns actual refusal");
            require(Boolean.TRUE.equals(agent.getMethod("unsafeFieldWriteGuarded").invoke(null)),"Unsafe object-put/CAS backend installed");
            Target target=new Child(),other=new Child();deny(target);
            agent.getMethod("declareDispatchTarget",Class.class,String.class,String.class).invoke(null,Target.class,"write","(F)V");
            agent.getMethod("installDeclaredDispatch").invoke(null);
            target.write(-1);other.write(-1);target.write(1);
            require(target.calls==1&&other.calls==1,"dispatch overrides refuse harmful argument and preserve neighbour/healing");
            try(URLClassLoader independent=new URLClassLoader(new URL[]{Path.of("checks").toAbsolutePath().toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
                Class<?> sameName=Class.forName("dev.ronova.pro.validation.BoundaryCheck$Target",true,independent);Object separateTarget=sameName.getConstructor().newInstance();
                deny(separateTarget);sameName.getMethod("write",float.class).invoke(separateTarget,-1F);
                require(sameName.getField("calls").getInt(separateTarget)==1,"same name in unrelated defining loader is not intercepted");
            }
            deny(null);
            TaskBridge.registerControlObjects(owner,new Object[0]);
            Field submissions=TaskBridge.class.getDeclaredField("SUBMISSIONS");submissions.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object,Object> ledger=(Map<Object,Object>)submissions.get(null);
            Object forged=new Object();Fields.putForeign(ledger,forged,1);
            require(!ledger.containsKey(forged),"external direct task ledger mutation refused");
            require(!Fields.callForeignGuard(ledger),"external guard call cannot authorize the same ledger");
            Field held=TaskBridge.class.getDeclaredField("HELD_TASKS");held.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Object,Object> heldLedger=(Map<Object,Object>)held.get(null);
            Fields.putForeign(heldLedger,forged,1);
            require(!heldLedger.containsKey(forged),"external helper-reference ledger mutation refused");
            ExecutorService pool=Executors.newSingleThreadExecutor();
            try {
                AtomicInteger ran=new AtomicInteger();Callable<Integer> blocked=()->ran.incrementAndGet();deny(blocked);
                Future<Integer> queued=pool.submit(blocked);
                expectFailure(queued);require(ran.get()==0,"queued callback never begins and Future settles");
                deny(null);require(pool.submit(()->19).get(5,TimeUnit.SECONDS)==19,"shared worker remains usable");
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                FutureTask<Integer> active=new FutureTask<>(()->{entered.countDown();release.await();return 27;});
                pool.execute(active);require(entered.await(5,TimeUnit.SECONDS),"in-flight task entered");deny(active);release.countDown();expectFailure(active);
                require(active.isDone(),"in-flight result refused and waiter released");
                CompletableFuture<Integer> source=new CompletableFuture<>();var next=source.thenApply(v->v+1);deny(next);source.complete(4);expectFailure(next);
                require(next.isCompletedExceptionally(),"CompletableFuture successor closes exceptionally");
                CompletableFuture<Integer> direct=new CompletableFuture<>();deny(direct);direct.complete(3);expectFailure(direct);
                require(direct.isDone(),"direct CompletableFuture result is refused");
            } finally { pool.shutdownNow();require(pool.awaitTermination(5,TimeUnit.SECONDS),"test-owned pool settled"); }
            deny(null);
            ForkJoinPool fork=new ForkJoinPool(1);
            try {
                RecursiveTask<Integer> task=new RecursiveTask<>() { protected Integer compute(){throw new AssertionError("forbidden fork body");} };
                deny(task);fork.execute(task);expectFailure(task);
                require(task.isDone(),"ForkJoin refused callback settles");deny(null);
                require(fork.submit(()->31).get(5,TimeUnit.SECONDS)==31,"ForkJoin neighbour preserved");
            } finally { fork.shutdown();require(fork.awaitTermination(5,TimeUnit.SECONDS),"test ForkJoin pool settled"); }
            ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
            try {
                AtomicInteger calls=new AtomicInteger();Runnable repeated=()->calls.incrementAndGet();deny(repeated);
                ScheduledFuture<?> periodic=timer.scheduleAtFixedRate(repeated,0,1,TimeUnit.MILLISECONDS);expectFailure(periodic);
                require(calls.get()==0&&periodic.isDone(),"periodic refused callback stops rescheduling");deny(null);
                require(timer.schedule(()->37,0,TimeUnit.MILLISECONDS).get(5,TimeUnit.SECONDS)==37,"scheduled neighbour preserved");
            } finally { timer.shutdown();require(timer.awaitTermination(5,TimeUnit.SECONDS),"test scheduler settled"); }
            CloneableValue clone=new CloneableValue();deny(null);Object copied=clone.copy();require(copied!=clone&&((CloneableValue)copied).value==17,"native clone retains real shallow-copy behavior");
            deny(clone);try { clone.copy();throw new AssertionError("clone escaped"); } catch(CloneNotSupportedException expected) { require(true,"exact terminal clone refused"); }
            String nativeState=String.valueOf(agent.getMethod("nativeBindingState").invoke(null));
            require(nativeState.startsWith("OBJECT_CLONE_JNI_READY"),"real JNI backend loaded");
            Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
            Method exactNative=nativeControl.getDeclaredMethod("clone0",Object.class);exactNative.setAccessible(true);
            try { exactNative.invoke(null,clone);throw new AssertionError("native gate escaped"); }
            catch(InvocationTargetException expected) { require(expected.getCause() instanceof CloneNotSupportedException,"JNI boundary itself rejects terminal source"); }
            System.out.println("NATIVE_STATE "+nativeState);
            require(TaskBridge.droppedEvents()==0,"internal task ledger writes retain their real counts under control guard");
            deny(null);System.out.println("BOUNDARY_CHECK_PASS");
        }
    }
    private static boolean fieldA(Object receiver,Class<?> declaring,String name,Object value) { return name.equals("i"); }
    private static boolean fieldB(Object receiver,Class<?> declaring,String name,Object value) { return name.equals("l"); }
    private static boolean dispatchA(Object receiver,String operation,Object value) { return operation.equals("A"); }
    private static boolean dispatchB(Object receiver,String operation,Object value) { return operation.equals("B"); }
    private static boolean taskA(Object task,String effect) { return effect.equals("A"); }
    private static boolean taskB(Object task,String effect) { return effect.equals("B"); }
    private static boolean allowField(Object receiver,Class<?> declaring,String name,Object value) { return false; }
    private static boolean allowDispatch(Object receiver,String operation,Object value) { return false; }
    private static boolean allowTask(Object task,String effect) { return false; }
    /** True means refuse this exact resolved entry; HashMapBoundary's bridge must invert it to allow=false. */
    private static boolean denyIndex(Object map,Object key,Object value) {
        if(map==guardedIndexMap)return (key==null&&value==null)||value==protectedMapValue;
        if(value!=null&&value==protectedMapValue&&guardedIndexMap instanceof HashMap<?,?> owner)
            for(Object entry:owner.entrySet())if(entry==map)return true;
        return false;
    }
    private static void sourceMaps()throws Exception {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null);
        Method entries=bridge.getMethod("entries",Object.class),peek=bridge.getMethod("peek",Object.class,Object.class),
                run=bridge.getMethod("withMap",Object.class,Runnable.class),revision=bridge.getMethod("revision",Object.class);
        require("HASHMAP_SCOPES_INSTALLED".equals(bridge.getMethod("state").invoke(null)),"source writer scopes installed");
        HashMap<String,Object> map=new HashMap<>();Object target=new Object(),neighbor=new Object();
        map.put("target",target);map.put("neighbor",neighbor);
        require(((Object[][])entries.invoke(null,map)).length==2,"exact source snapshot");
        CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1),attempt=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        var node=map.entrySet().stream().filter(e->e.getKey().equals("target")).findFirst().orElseThrow();
        Thread owner=new Thread(()->{try {run.invoke(null,map,(Runnable)()->{held.countDown();await(release);});}catch(Throwable x){error.set(x);}});
        owner.start();require(held.await(5,TimeUnit.SECONDS),"map scope held");
        Object replacement=new Object();Thread writer=new Thread(()->{attempt.countDown();node.setValue(replacement);});writer.start();
        require(attempt.await(5,TimeUnit.SECONDS),"entry writer attempted");
        require(entries.invoke(null,map)==null,"snapshot never waits for a locked source");
        release.countDown();owner.join(5000);writer.join(5000);
        require(!owner.isAlive()&&!writer.isAlive()&&error.get()==null&&map.get("target")==replacement,"entry writer resumes after actual source frame exit");
        require((boolean)run.invoke(null,map,(Runnable)()->map.computeIfPresent("target",(k,v)->v==target?null:v)),"source final comparison executes");
        require(map.get("target")==replacement&&map.get("neighbor")==neighbor,"replacement and unrelated slot preserved");
        HashMap<String,Object> late=new HashMap<>();late.put("x",target);
        CountDownLatch inside=new CountDownLatch(1),resume=new CountDownLatch(1);
        Thread old=new Thread(()->late.compute("x",(k,v)->{inside.countDown();await(resume);return replacement;}));old.start();
        try {
            require(inside.await(5,TimeUnit.SECONDS),"pre-registration writer entered");
            require(entries.invoke(null,late)==null,"in-flight old writer prevents a false synchronized snapshot");
            HashMap<String,Object> unrelated=new HashMap<>();unrelated.put("neighbor",neighbor);
            require(((Object[][])entries.invoke(null,unrelated)).length==1,"unrelated long callback does not block exact source registration");
            require((boolean)run.invoke(null,unrelated,(Runnable)()->unrelated.remove("neighbor")),"unrelated source can settle while old writer remains active");
        } finally {resume.countDown();old.join(5000);}
        require(((Object[])peek.invoke(null,late,"x"))[1]==replacement,"registration resumes after old writer exits");
        RuntimeException expected=new RuntimeException("expected callback");
        try {map.compute("neighbor",(k,v)->{throw expected;});throw new AssertionError("exception lost");}
        catch(RuntimeException actual) {require(actual==expected,"callback exception preserved");}
        require((boolean)run.invoke(null,map,(Runnable)()->map.remove("target")),"exception releases writer scope");
        long before=(long)revision.invoke(null,map);node.setValue(target);
        require((long)revision.invoke(null,map)==before,"detached node does not invalidate the live source");
        AtomicBoolean reentrant=new AtomicBoolean(true);
        map.forEach((k,v)->{try {reentrant.set((boolean)run.invoke(null,map,(Runnable)()->map.clear()));}catch(Exception x){throw new AssertionError(x);}});
        require(!reentrant.get()&&map.get("neighbor")==neighbor,"disposition defers during an actual forEach callback");
        HashMap<String,Object> clone=(HashMap<String,Object>)map.clone();
        require(entries.invoke(null,clone)!=null,"clone has its own observed map birth");clone.clear();
        require(map.get("neighbor")==neighbor,"clone does not clear the original source");
        require("HASHMAP_SCOPES_INSTALLED".equals(bridge.getMethod("state").invoke(null)),"all source scopes exited consistently");
        HashMap<Integer,Object> oversized=new HashMap<>();for(int i=0;i<8193;i++)oversized.put(i,target);
        require(entries.invoke(null,oversized)==null,"oversized source snapshot remains unavailable");oversized.clear();oversized.put(1,target);
        require(((Object[][])entries.invoke(null,oversized)).length==1,"source snapshot resumes after capacity is released");
        sourceIncoming();
        System.out.println("SOURCE_MAP_CHECK_PASS");
    }
    private static final AtomicInteger creations=new AtomicInteger(),creationFailures=new AtomicInteger();
    private static volatile Object observedCreation,observedFailure;
    public static void created(Object object) { observedCreation=object;creations.incrementAndGet(); }
    public static void creationError(Object object) { observedFailure=object;creationFailures.incrementAndGet(); }
    public static final class Constructed extends net.minecraft.world.entity.Entity {
        public final String initialized;
        public Constructed(long value,double fraction) {super(value,fraction);initialized="complete";}
        public Constructed(RuntimeException fail) {super(7,2.0);throw fail;}
    }
    private static Constructed create(long value,double fraction) {return new Constructed(value,fraction);}
    private static void creations()throws Exception {
        fastutilPolicy(null,null);
        TaskBridge.installCreationObserver(BoundaryCheck.class,handle("created",void.class,Object.class),handle("creationError",void.class,Object.class));
        Constructed first=create(987654321L,3.5);
        require(creations.get()==1&&observedCreation==first&&first.value==987654321L&&first.fraction==3.5&&first.initialized.equals("complete"),"actual completed constructor with wide arguments observed once");
        new Constructed(2,7.0);require(creations.get()==2,"discarded constructor return still observed");
        RuntimeException failure=new RuntimeException("constructor");
        try {new Constructed(failure);throw new AssertionError("lost constructor exception");}
        catch(RuntimeException actual) {require(actual==failure,"enclosing constructor catch keeps original exception");}
        require(creationFailures.get()==1&&observedFailure==failure&&creations.get()==2,"failed constructor never supplies an initialized body");
        TaskBridge.class.getMethod("creationObserved",Object.class).invoke(null,first);
        TaskBridge.class.getMethod("creationFailed",Throwable.class).invoke(null,failure);
        require(creations.get()==2&&creationFailures.get()==1,"caller-invoked observer cannot provide production evidence");
        System.out.println("CREATION_CHECK_PASS");
    }
    private static volatile Object sourceMap,refusedValue;
    public static boolean denySource(Object map,Object key,Object value) { return map==sourceMap&&value==refusedValue; }
    public static void sourceChanged(Object map) { }
    private static void sourceIncoming()throws Exception {
        fastutilPolicy(null,null);
        TaskBridge.installSourceWriteObserver(BoundaryCheck.class,
                handle("denySource",boolean.class,Object.class,Object.class,Object.class),handle("sourceChanged",void.class,Object.class));
        var maps=List.<Map<Object,Object>>of(new HashMap<>(128),new ConcurrentHashMap<>(128));
        for(Map<Object,Object> map:maps) {
            Object allowed=new Object(),other=new Object(),bad=new Object();sourceMap=map;refusedValue=bad;
            map.put("old",allowed);map.put("neighbor",other);
            if(map instanceof HashMap)dev.ronova.pro.bootstrap.SourceMapBridge.entries(map);
            else dev.ronova.pro.bootstrap.SourceMapBridge.observeConcurrent(map);
            require(map.put("new",bad)==null&&!map.containsKey("new"),"source absent put refused: "+map.getClass().getSimpleName());
            require(map.putIfAbsent("new",bad)==null&&!map.containsKey("new"),"source putIfAbsent refused");
            AtomicInteger calls=new AtomicInteger();
            require(map.computeIfAbsent("new",k->{calls.incrementAndGet();return bad;})==null&&!map.containsKey("new"),"absent compute releases reservation on refusal");
            require(map.compute("new",(k,v)->{calls.incrementAndGet();return bad;})==null&&!map.containsKey("new"),"absent compute refused");
            require(map.computeIfPresent("old",(k,v)->{calls.incrementAndGet();return bad;})==allowed&&map.get("old")==allowed,"existing compute retains actual source slot");
            require(map.merge("old",other,(a,c)->{calls.incrementAndGet();return bad;})==allowed&&map.get("old")==allowed,"existing merge refusal");
            require(calls.get()==4,"source callbacks called once");
            require(map.merge("new",bad,(a,c)->c)==null&&!map.containsKey("new"),"absent merge refusal");
            require(!map.replace("old",allowed,bad)&&map.replace("old",bad)==allowed&&map.get("old")==allowed,"source replace returns actual outcome");
            map.replaceAll((k,v)->k.equals("old")?bad:allowed);
            require(map.get("old")==allowed&&map.get("neighbor")==allowed,"source replaceAll continues unrelated rows");
            map.putAll(Map.of("bad",bad,"ok",other));require(!map.containsKey("bad")&&map.get("ok")==other,"bulk source writes selective");
            for(var entry:map.entrySet())if(entry.getKey().equals("old"))entry.setValue(bad);
            require(map.get("old")==allowed,"entry source refill refused");
            for(int i=0;i<20;i++)map.put(new CollidingKey(i),allowed);
            Object missing=new CollidingKey(99),existing=new CollidingKey(3);
            require(map.computeIfAbsent(missing,k->bad)==null&&!map.containsKey(missing),"tree absent compute refusal");
            require(map.compute(missing,(k,v)->bad)==null&&!map.containsKey(missing),"tree absent remap refusal");
            require(map.merge(missing,bad,(a,c)->c)==null&&!map.containsKey(missing),"tree absent merge refusal");
            require(!map.replace(existing,allowed,bad)&&map.get(existing)==allowed,"tree existing replacement refused");
            RuntimeException expected=new RuntimeException("source callback");
            try {map.compute("exception",(k,v)->{throw expected;});throw new AssertionError("exception lost");}
            catch(RuntimeException actual) {require(actual==expected,"source callback exception identity");}
            Thread later=new Thread(()->map.put("exception",other));later.start();later.join(5000);
            require(!later.isAlive()&&map.get("exception")==other,"refusal and exception leave bin and map unlocked");
            map.compute("old",(k,v)->{refusedValue=null;return bad;});require(map.get("old")==bad,"revocation observed inside source callback");
            map.compute("old",(k,v)->{refusedValue=other;return other;});require(map.get("old")==bad,"new fence observed inside source callback");
            Map<Object,Object> unrelated=new HashMap<>();unrelated.put("x",other);require(unrelated.get("x")==other,"unregistered source map unaffected");
        }
        sourceMap=null;refusedValue=null;
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("latch timeout");}
        catch(InterruptedException error) {Thread.currentThread().interrupt();throw new AssertionError(error);}
    }
    private static void indexWrites()throws Exception {
        var maps=new ArrayList<Map<Object,Object>>();maps.add(new HashMap<>());
        maps.add(boxedMap(newFastutilMap("it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap")));
        maps.add(boxedMap(newFastutilMap("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap")));
        for(Map<Object,Object> map:maps) {
            Object target=new Object(),replacement=new Object(),other=new Object();int key=17;
            map.put(key,target);map.put(18,other);fastutilPolicy(map,target);
            require(map.put(key,replacement)==target&&map.get(key)==target,"put preserves protected current value: "+map.getClass().getSimpleName());
            require(map.replace(key,replacement)==target&&map.get(key)==target,"replace returns retained value");
            require(!map.replace(key,target,replacement)&&map.get(key)==target,"conditional replace reports refusal");
            AtomicInteger calls=new AtomicInteger();
            require(map.compute(key,(k,v)->{calls.incrementAndGet();return replacement;})==target&&map.get(key)==target,"compute result remains actual value");
            require(map.computeIfPresent(key,(k,v)->{calls.incrementAndGet();return null;})==target&&map.get(key)==target,"null compute does not erase protected value");
            require(map.merge(key,replacement,(a,c)->{calls.incrementAndGet();return replacement;})==target&&map.get(key)==target,"merge cannot replace exact protected value");
            require(map.merge(key,replacement,(a,c)->{calls.incrementAndGet();return null;})==target&&map.get(key)==target,"null merge returns retained actual value");
            require(calls.get()==4,"remapping callbacks execute exactly once");
            var entry=map.entrySet().stream().filter(e->e.getKey().equals(key)).findFirst().orElseThrow();
            require(entry.setValue(replacement)==target&&map.get(key)==target,"entry direct value store preserves protected row");
            map.replaceAll((k,v)->replacement);
            require(map.get(key)==target&&map.get(18)==replacement,"replaceAll continues processing the unrelated row");
            map.putAll(Map.of(key,replacement,18,other));
            require(map.get(key)==target&&map.get(18)==other,"putAll selectively preserves protected slot");
            require(map.compute(key,(k,v)->{protectedMapValue=null;return replacement;})==replacement&&map.get(key)==replacement,"callback revocation takes effect before actual store");
            map.put(key,target);protectedMapValue=null;
            require(map.compute(key,(k,v)->{protectedMapValue=target;return replacement;})==target&&map.get(key)==target,"callback new protection takes effect before actual store");
            Map<Object,Object> foreign=new HashMap<>();foreign.put(key,target);
            foreign.entrySet().iterator().next().setValue(replacement);
            require(foreign.get(key)==replacement,"same body in unrelated entry remains mutable");
            if(!(map instanceof HashMap)) {
                require(invokeFastutil(map,"replace",new Class<?>[]{int.class,Object.class,Object.class},key,target,replacement).equals(false),"primitive conditional replace refuses");
                Object result=invokeFastutil(map,"compute",new Class<?>[]{int.class,java.util.function.BiFunction.class},key,(java.util.function.BiFunction<Integer,Object,Object>)(k,v)->null);
                require(result==target&&map.get(key)==target,"primitive null compute retains actual protected value");
            }
            protectedMapValue=null;map.clear();
        }
        guardedIndexMap=null;
        System.out.println("INDEX_WRITES_CHECK_PASS");
    }
    private static int invalidTask(Object task,String effect) { return 0; }
    private static MethodHandle handle(String name,Class<?> result,Class<?>... arguments)throws Exception {
        return MethodHandles.lookup().findStatic(BoundaryCheck.class,name,MethodType.methodType(result,arguments));
    }
    private static void hashmapIndex()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        String state=String.valueOf(agent.getMethod("indexGuardState").invoke(null));
        require(state.startsWith("INSTALLED:TRANSFORMED_JDK17_HASHMAP_RESOLVED_NODE_CLEAR")
                ||state.startsWith("INSTALLED:ALREADY_TRANSFORMED"),"actual premain installed both resolved-node and mixed-clear HashMap guards: "+state);
        TaskBridge.installPolicy(BoundaryCheck.class,
                handle("allowField",boolean.class,Object.class,Class.class,String.class,Object.class),
                handle("allowDispatch",boolean.class,Object.class,String.class,Object.class),
                handle("allowTask",boolean.class,Object.class,String.class),new Field[0],new long[0],
                handle("denyIndex",boolean.class,Object.class,Object.class,Object.class));

        HashMap<Object,Object> map=new HashMap<>();Object target=new Object(),neighbour=new Object();
        guardedIndexMap=map;protectedMapValue=target;
        map.put("target",target);map.put("neighbour",neighbour);
        require(!TaskBridge.indexRemovalAllowed(map,"target",target),"index callback true is inverted to a bridge refusal");
        require(TaskBridge.indexRemovalAllowed(map,"neighbour",neighbour),"neighbour value remains allowed in the guarded map");
        require(map.remove("target")==null&&map.get("target")==target,"HashMap.remove(key) keeps exact target");
        require(!map.remove("target",target)&&map.get("target")==target,"HashMap.remove(key,value) keeps exact target");
        require(!map.keySet().remove("target")&&map.get("target")==target,"keySet.remove keeps exact target");
        map.values().remove(target);
        require(map.get("target")==target,"values.remove physically keeps exact target; view return is not release evidence");
        require(!map.entrySet().remove(new AbstractMap.SimpleEntry<>("target",target))&&map.get("target")==target,
                "entrySet.remove keeps exact target");
        Iterator<Object> keys=map.keySet().iterator();while(keys.hasNext())if(keys.next().equals("target")) { keys.remove();break; }
        require(map.get("target")==target,"keySet iterator removal keeps exact target");
        Iterator<Object> values=map.values().iterator();while(values.hasNext())if(values.next()==target) { values.remove();break; }
        require(map.get("target")==target,"values iterator removal keeps exact target");
        Iterator<Map.Entry<Object,Object>> entries=map.entrySet().iterator();while(entries.hasNext())if(entries.next().getValue()==target) { entries.remove();break; }
        require(map.get("target")==target,"entrySet iterator removal keeps exact target");

        map.put("neighbour-2",new Object());map.put("neighbour-3",new Object());map.clear();
        require(map.size()==1&&map.get("target")==target,"mixed HashMap.clear removes neighbours but preserves target");

        HashMap<Object,Object> external=new HashMap<>();external.put("same-entity",target);
        require(TaskBridge.indexRemovalAllowed(external,"same-entity",target),"same value in unrelated map has no authority");
        require(external.remove("same-entity")==target&&map.get("target")==target,"external map removal leaves guarded slot intact");

        HashMap<Object,Object> collisionMap=new HashMap<>();CollidingKey protectedKey=new CollidingKey(1),collisionKey=new CollidingKey(2);
        Object collisionTarget=new Object(),collisionNeighbour=new Object();
        guardedIndexMap=collisionMap;protectedMapValue=collisionTarget;
        collisionMap.put(protectedKey,collisionTarget);collisionMap.put(collisionKey,collisionNeighbour);
        require(collisionMap.remove(collisionKey)==collisionNeighbour&&collisionMap.get(protectedKey)==collisionTarget,
                "colliding neighbour key resolves and removes only its own node");
        require(collisionMap.remove(protectedKey)==null&&collisionMap.get(protectedKey)==collisionTarget,
                "colliding protected key is matched against its actual resolved value");

        protectedMapValue=null;
        require(collisionMap.remove(protectedKey)==collisionTarget&&collisionMap.isEmpty(),"revocation allows the exact HashMap slot removal");
        guardedIndexMap=null;protectedMapValue=null;
        System.out.println("HASHMAP_INDEX_BOUNDARY_CHECK_PASS");
    }
    private static Object newFastutilMap(String className)throws Exception {
        return Class.forName(className).getConstructor().newInstance();
    }
    private static Object invokeFastutil(Object map,String name,Class<?>[] parameters,Object... values)throws Exception {
        try { return map.getClass().getMethod(name,parameters).invoke(map,values); }
        catch(InvocationTargetException failure) {
            Throwable cause=failure.getCause();
            if(cause instanceof Exception exception)throw exception;
            if(cause instanceof Error error)throw error;
            throw failure;
        }
    }
    @SuppressWarnings("unchecked") private static Map<Object,Object> boxedMap(Object map) { return (Map<Object,Object>)map; }
    private static Object fastutilGet(Object map,int key)throws Exception { return invokeFastutil(map,"get",new Class<?>[]{int.class},key); }
    private static void fastutilPut(Object map,int key,Object value)throws Exception { invokeFastutil(map,"put",new Class<?>[]{int.class,Object.class},key,value); }
    private static void fastutilPolicy(Object map,Object target)throws Exception {
        guardedIndexMap=map;protectedMapValue=target;
        TaskBridge.installPolicy(BoundaryCheck.class,
                handle("allowField",boolean.class,Object.class,Class.class,String.class,Object.class),
                handle("allowDispatch",boolean.class,Object.class,String.class,Object.class),
                handle("allowTask",boolean.class,Object.class,String.class),new Field[0],new long[0],
                handle("denyIndex",boolean.class,Object.class,Object.class,Object.class));
    }
    private static Object fastutilFixture(String className,int targetKey,Object target,int neighbourKey,Object neighbour)throws Exception {
        Object map=newFastutilMap(className);fastutilPut(map,targetKey,target);fastutilPut(map,neighbourKey,neighbour);fastutilPolicy(map,target);return map;
    }
    private static void fastutilViewRemoval(String className,String view)throws Exception {
        Object target=new Object(),neighbour=new Object();Object map=fastutilFixture(className,0,target,1,neighbour);
        Map<Object,Object> boxed=boxedMap(map);
        switch(view) {
            case "keySet" -> {
                boxed.keySet().remove(Integer.valueOf(0));
                require(fastutilGet(map,0)==target,"fastutil "+className+" keySet protects key zero");
                boxed.keySet().remove(Integer.valueOf(1));
                require(fastutilGet(map,1)==null&&fastutilGet(map,0)==target,"fastutil keySet still removes neighbour");
            }
            case "values" -> {
                boxed.values().remove(target);
                require(fastutilGet(map,0)==target,"fastutil "+className+" values protects target");
                boxed.values().remove(neighbour);
                require(fastutilGet(map,1)==null&&fastutilGet(map,0)==target,"fastutil values still removes neighbour");
            }
            case "entrySet" -> {
                boxed.entrySet().remove(new AbstractMap.SimpleEntry<>(0,target));
                require(fastutilGet(map,0)==target,"fastutil "+className+" entrySet protects target");
                boxed.entrySet().remove(new AbstractMap.SimpleEntry<>(1,neighbour));
                require(fastutilGet(map,1)==null&&fastutilGet(map,0)==target,
                        "fastutil entrySet still removes neighbour");
            }
            default -> throw new IllegalArgumentException(view);
        }
    }
    private static void fastutilIteratorRemoval(String className,String view)throws Exception {
        Object target=new Object(),neighbour=new Object();Object map=fastutilFixture(className,0,target,1,neighbour);
        Map<Object,Object> boxed=boxedMap(map);Set<Integer> visited=new HashSet<>();
        switch(view) {
            case "keySet" -> {
                Iterator<Object> iterator=boxed.keySet().iterator();
                while(iterator.hasNext()) { Integer key=(Integer)iterator.next();visited.add(key);iterator.remove(); }
            }
            case "values" -> {
                Iterator<Object> iterator=boxed.values().iterator();
                while(iterator.hasNext()) { Object value=iterator.next();visited.add(value==target?0:1);iterator.remove(); }
            }
            case "entrySet" -> {
                Iterator<Map.Entry<Object,Object>> iterator=boxed.entrySet().iterator();
                while(iterator.hasNext()) { Map.Entry<Object,Object> entry=iterator.next();visited.add((Integer)entry.getKey());iterator.remove(); }
            }
            default -> throw new IllegalArgumentException(view);
        }
        require(visited.contains(0)&&visited.contains(1),"fastutil "+className+" "+view+" iterator visits all entries despite a refused removal");
        require(fastutilGet(map,0)==target&&fastutilGet(map,1)==null,
                "fastutil "+className+" "+view+" iterator keeps target and removes its adjacent neighbour");
    }
    /** Real installed methods only; this does not install a substitute policy owner. */
    @SuppressWarnings("unchecked") private static void bAdapterLinkage()throws Exception{
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        require(String.valueOf(agent.getMethod("installState").invoke(null)).startsWith("INSTALLED"),"actual agent installation for B adapters");
        Class.forName("io.netty.channel.AbstractChannelHandlerContext",true,ClassLoader.getSystemClassLoader());
        System.out.println("PASS: actual Netty write boundary verifies with its void return");
        Class<?> sources=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null);
        Field roots=sources.getDeclaredField("scopes");roots.setAccessible(true);
        controlArrayWriteRefused(roots.get(null),"source directory roots");
        Class<?> code=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",false,null);
        Field controls=code.getDeclaredField("CONTROLS");controls.setAccessible(true);Object registry=controls.get(null);
        Field table=registry.getClass().getDeclaredField("table");table.setAccessible(true);
        controlArrayWriteRefused(table.get(registry),"actual control table");
        Class<?> tasks=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Field gates=tasks.getDeclaredField("FIELD_GATES");gates.setAccessible(true);Object gateRoots=gates.get(null);
        controlArrayWriteRefused(gateRoots,"actual field gate roots");
        boolean liveGate=false;
        for(int i=0;i<Array.getLength(gateRoots)&&!liveGate;i++){
            Object bucket=Array.get(gateRoots,i);Field entries=bucket.getClass().getDeclaredField("table");entries.setAccessible(true);
            Object nodes=entries.get(bucket);
            for(int j=0;j<Array.getLength(nodes);j++)if(Array.get(nodes,j)!=null){
                controlArrayWriteRefused(nodes,"actual field gate bucket");
                Object node=Array.get(nodes,j);Field gateField=node.getClass().getDeclaredField("gate");gateField.setAccessible(true);
                Object gate=gateField.get(node);Field syncField=java.util.concurrent.locks.ReentrantLock.class.getDeclaredField("sync");syncField.setAccessible(true);
                Object sync=syncField.get(gate);Field state=java.util.concurrent.locks.AbstractQueuedSynchronizer.class.getDeclaredField("state");state.setAccessible(true);
                int before=state.getInt(sync),proposed=before==37?38:37;
                try{state.setInt(sync,proposed);}catch(SecurityException|IllegalAccessException refused){}
                require(state.getInt(sync)==before,"foreign reflective field gate state overwrite refused");
                Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
                long offset=(long)unsafeType.getMethod("objectFieldOffset",Field.class).invoke(unsafe,state);
                unsafeType.getMethod("putInt",Object.class,long.class,int.class).invoke(unsafe,sync,offset,proposed);
                require(state.getInt(sync)==before,"foreign Unsafe field gate state overwrite refused");
                liveGate=true;break;
            }
        }
        require(liveGate,"actual installed field gate has a protected receiver");
        Class<?> execution=Class.forName("dev.ronova.pro.bootstrap.ExecutionFlow",false,null);
        Method memoryEnd=execution.getDeclaredMethod("memoryAfter",Object.class,boolean.class);memoryEnd.setAccessible(true);
        try{memoryEnd.invoke(null,new Object(),true);throw new AssertionError("foreign execution memory entry admitted");}
        catch(InvocationTargetException expected){require(expected.getCause() instanceof SecurityException,"foreign reflective memory entry refused");}
        var foreignEnd=MethodHandles.lookup().unreflect(memoryEnd);
        try{foreignEnd.invokeExact((Object)new Object(),true);throw new AssertionError("foreign handle memory entry admitted");}
        catch(SecurityException expected){require(true,"foreign handle memory entry refused");}
        catch(Throwable failure){throw new AssertionError("foreign handle memory entry failed for another reason",failure);}
        Field heapField=execution.getDeclaredField("HEAP");heapField.setAccessible(true);Map<Object,Object> heap=(Map<Object,Object>)heapField.get(null);
        synchronized(heap){
            require(!heap.isEmpty(),"actual installed source heap has live entries");
            var entry=heap.entrySet().iterator().next();Object key=entry.getKey(),value=entry.getValue();heap.remove(key);
            require(heap.get(key)==value,"foreign removal of exact execution metadata map entry refused");
        }
        String listType="it.unimi.dsi.fastutil.objects.ObjectArrayList",setType="it.unimi.dsi.fastutil.objects.ObjectOpenHashSet";
        List<Object> list=(List<Object>)Class.forName(listType).getConstructor().newInstance();Object first=new Object(),second=new Object();
        list.add(first);list.add(second);list.subList(0,1).addAll(List.of(second));list.listIterator().add(first);list.subList(0,1).listIterator().add(second);list.remove(0);list.remove(0);
        require(list.size()==3&&list.remove(0)==first,"object list and view writer scopes link to the actual bridge");
        Set<Object> set=(Set<Object>)Class.forName(setType).getConstructor().newInstance();set.add(first);set.add(null);set.add(second);
        var iterator=set.iterator();while(iterator.hasNext()){if(iterator.next()==first)iterator.remove();}
        require(set.size()==2&&set.contains(null)&&set.contains(second),"object set iterator keeps its actual null slot and neighbour");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.FastutilIndexBoundary");
        for(String name:List.of(listType,listType+"$1",setType,setType+"$SetIterator","it.unimi.dsi.fastutil.objects.AbstractObjectList$ObjectSubList","it.unimi.dsi.fastutil.objects.ObjectIterators$AbstractIndexBasedListIterator")){
            Object state=boundary.getMethod("state",String.class).invoke(null,name.replace('.','/'));
            require("INSTALLED".equals(state),"B adapter installed: "+name+" = "+state);
        }
        var bytes=new java.io.ByteArrayOutputStream();
        try(var writer=new java.io.BufferedWriter(new java.io.OutputStreamWriter(bytes,java.nio.charset.StandardCharsets.UTF_8))){writer.write("delegate-linkage");}
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(bytes.toByteArray()),java.nio.charset.StandardCharsets.UTF_8))){require(reader.readLine().equals("delegate-linkage"),"character wrapper constructors and operations link without altering a live delegate");}
        // The layout reader is used while real buffer data receipts are open.
        // Exercise the actual bootstrap heap/direct views after Agent install.
        var heapBuffer=java.nio.ByteBuffer.wrap(new byte[]{11,22,33});
        var directBuffer=java.nio.ByteBuffer.allocateDirect(3);
        directBuffer.put(heapBuffer).flip();
        var readView=directBuffer.asReadOnlyBuffer();
        require(readView.get()==11&&readView.get()==22&&readView.get()==33,
                "actual buffer layout reads keep heap-to-direct data and the shared read-only view");
        require(heapBuffer.get(0)==11&&heapBuffer.get(2)==33,"actual buffer transfer leaves its source bytes unchanged");
        System.out.println("B_ADAPTER_LINKAGE_PASS");
    }
    private static void controlArrayWriteRefused(Object array,String name)throws Exception{
        for(int index=0;index<Array.getLength(array);index++){
            Object original=Array.get(array,index);if(original==null)continue;
            try{Array.set(array,index,null);}catch(SecurityException refused){}
            require(Array.get(array,index)==original,name+" refuses a foreign reflective array store");return;
        }
        throw new AssertionError(name+" has no live entry to exercise");
    }
    private static void fastutilIndex()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        require(String.valueOf(agent.getMethod("installState").invoke(null)).startsWith("INSTALLED"),"actual premain agent is installed");
        String[] mapTypes={"it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap","it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap"};
        for(String type:mapTypes) {
            Object zeroTarget=new Object(),zeroNeighbour=new Object();Object zero=fastutilFixture(type,0,zeroTarget,1,zeroNeighbour);
            invokeFastutil(zero,"remove",new Class<?>[]{int.class},0);
            require(fastutilGet(zero,0)==zeroTarget,"fastutil "+type+" exact int zero/nullslot removal is blocked");
            invokeFastutil(zero,"remove",new Class<?>[]{int.class},1);
            require(fastutilGet(zero,1)==null&&fastutilGet(zero,0)==zeroTarget,
                    "fastutil "+type+" removes ordinary neighbour beside nullslot");

            Object ordinaryTarget=new Object(),ordinaryNeighbour=new Object();Object ordinary=fastutilFixture(type,17,ordinaryTarget,18,ordinaryNeighbour);
            boxedMap(ordinary).remove(Integer.valueOf(17));
            require(fastutilGet(ordinary,17)==ordinaryTarget,"fastutil "+type+" boxed ordinary key removal is blocked");
            boxedMap(ordinary).remove(Integer.valueOf(18));
            require(fastutilGet(ordinary,18)==null&&fastutilGet(ordinary,17)==ordinaryTarget,
                    "fastutil "+type+" boxed ordinary neighbour removal succeeds");

            fastutilViewRemoval(type,"keySet");fastutilViewRemoval(type,"values");fastutilViewRemoval(type,"entrySet");
            fastutilIteratorRemoval(type,"keySet");fastutilIteratorRemoval(type,"values");fastutilIteratorRemoval(type,"entrySet");

            Object clearTarget=new Object(),clearNeighbour=new Object(),clearOther=new Object();
            Object mixed=fastutilFixture(type,0,clearTarget,1,clearNeighbour);fastutilPut(mixed,2,clearOther);
            boxedMap(mixed).clear();
            require(boxedMap(mixed).size()==1&&fastutilGet(mixed,0)==clearTarget,
                    "fastutil "+type+" mixed clear keeps target and removes neighbours");

            Object externalTarget=new Object();Object guarded=fastutilFixture(type,0,externalTarget,1,new Object());
            Object external=newFastutilMap(type);fastutilPut(external,0,externalTarget);
            require(TaskBridge.indexRemovalAllowed(external,0,externalTarget),"fastutil external map with same object has no authority");
            boxedMap(external).remove(Integer.valueOf(0));
            require(fastutilGet(external,0)==null&&fastutilGet(guarded,0)==externalTarget,
                    "fastutil external map removal leaves guarded source intact");

            protectedMapValue=null;
            boxedMap(guarded).remove(Integer.valueOf(0));
            require(fastutilGet(guarded,0)==null,
                    "fastutil revocation permits removal of the exact slot");
        }

        String linkedType=mapTypes[0];
        Object firstTarget=new Object(),lastNeighbour=new Object();Object first=fastutilFixture(linkedType,0,firstTarget,1,lastNeighbour);
        invokeFastutil(first,"removeFirst",new Class<?>[0]);
        require(fastutilGet(first,0)==firstTarget&&fastutilGet(first,1)==lastNeighbour,"linked removeFirst protects target node");
        invokeFastutil(first,"removeLast",new Class<?>[0]);
        require(fastutilGet(first,1)==null&&fastutilGet(first,0)==firstTarget,
                "linked removeLast still removes neighbour");

        Object firstNeighbour=new Object(),lastTarget=new Object();Object last=fastutilFixture(linkedType,1,firstNeighbour,0,lastTarget);
        fastutilPolicy(last,lastTarget);
        invokeFastutil(last,"removeLast",new Class<?>[0]);
        require(fastutilGet(last,0)==lastTarget&&fastutilGet(last,1)==firstNeighbour,"linked removeLast protects target node");
        invokeFastutil(last,"removeFirst",new Class<?>[0]);
        require(fastutilGet(last,1)==null&&fastutilGet(last,0)==lastTarget,
                "linked removeFirst still removes neighbour");
        protectedMapValue=null;
        invokeFastutil(last,"removeLast",new Class<?>[0]);
        require(fastutilGet(last,0)==null&&boxedMap(last).isEmpty(),"linked revocation permits removeLast");
        try { invokeFastutil(last,"removeFirst",new Class<?>[0]);throw new AssertionError("empty deque accepted"); }
        catch(NoSuchElementException expected) { require(true,"empty linked deque retains native exception"); }
        Object clustered=newFastutilMap(mapTypes[1]);
        Field mask=clustered.getClass().getDeclaredField("mask");mask.setAccessible(true);
        int bucketMask=mask.getInt(clustered);
        Method mix=Class.forName("it.unimi.dsi.fastutil.HashCommon").getMethod("mix",int.class);
        List<Integer> keys=new ArrayList<>();
        for(int k=1;keys.size()<8;k++)if((((Integer)mix.invoke(null,k))&bucketMask)==bucketMask)keys.add(k);
        Object wrappedTarget=new Object();
        for(int i=0;i<keys.size();i++)fastutilPut(clustered,keys.get(i),i==1?wrappedTarget:new Object());
        fastutilPolicy(clustered,wrappedTarget);
        Set<Object> visited=new HashSet<>();var cursor=boxedMap(clustered).entrySet().iterator();
        while(cursor.hasNext()) {var entry=cursor.next();require(visited.add(entry.getKey()),"clustered iterator does not revisit entries");cursor.remove();}
        require(visited.size()==keys.size()&&boxedMap(clustered).size()==1&&fastutilGet(clustered,keys.get(1))==wrappedTarget,
                "open iterator wrapped shift branch preserves exact target and removes every neighbour");
        guardedIndexMap=null;protectedMapValue=null;
        System.out.println("FASTUTIL_INDEX_BOUNDARY_CHECK_PASS");
    }
    private static void policyA(Class<?> owner,Field i,long offset)throws Exception {
        TaskBridge.installPolicy(owner,handle("fieldA",boolean.class,Object.class,Class.class,String.class,Object.class),
                handle("dispatchA",boolean.class,Object.class,String.class,Object.class),
                handle("taskA",boolean.class,Object.class,String.class),new Field[]{i},new long[]{offset});
    }
    private static void policyB(Class<?> owner,Field l,long offset)throws Exception {
        TaskBridge.installPolicy(owner,handle("fieldB",boolean.class,Object.class,Class.class,String.class,Object.class),
                handle("dispatchB",boolean.class,Object.class,String.class,Object.class),
                handle("taskB",boolean.class,Object.class,String.class),new Field[]{l},new long[]{offset});
    }
    private static void expectPolicyA(Fields receiver)throws Exception {
        require(!TaskBridge.fieldWriteAllowed(receiver,Fields.class,"i",1),"field A policy remains installed");
        require(TaskBridge.fieldWriteAllowed(receiver,Fields.class,"l",1L),"field A does not deny l");
        require(!TaskBridge.dispatchAllowed(receiver,"A",null)&&TaskBridge.dispatchAllowed(receiver,"B",null),"dispatch A policy remains installed");
        require(!TaskBridge.taskEffectAllowed(receiver,"A")&&TaskBridge.taskEffectAllowed(receiver,"B"),"task A policy remains installed");
        require(!TaskBridge.unsafeWriteAllowed(receiver,77123L,1),"Unsafe field map A remains paired with its callback");
    }
    private static void guards()throws Exception {
        Class<?> owner=BoundaryCheck.class;
        Field i=Fields.class.getField("i"),l=Fields.class.getField("l");
        long sharedOffset=77123L;
        Fields receiver=new Fields();
        policyA(owner,i,sharedOffset);
        expectPolicyA(receiver);
        try {
            TaskBridge.installGuards(owner,handle("fieldB",boolean.class,Object.class,Class.class,String.class,Object.class),
                    handle("dispatchB",boolean.class,Object.class,String.class,Object.class),
                    handle("invalidTask",int.class,Object.class,String.class));
            throw new AssertionError("invalid handle installation accepted");
        } catch(WrongMethodTypeException expected) { require(true,"failed legacy handle conversion preserves prior policy"); }
        expectPolicyA(receiver);
        try {
            TaskBridge.installPolicy(owner,handle("fieldB",boolean.class,Object.class,Class.class,String.class,Object.class),
                    handle("dispatchB",boolean.class,Object.class,String.class,Object.class),
                    handle("taskB",boolean.class,Object.class,String.class),new Field[]{l},new long[0]);
            throw new AssertionError("invalid mapping accepted");
        } catch(IllegalArgumentException expected) { require(true,"failed complete mapping validation preserves prior policy"); }
        expectPolicyA(receiver);
        try {
            TaskBridge.installGuards(Fields.class,handle("fieldB",boolean.class,Object.class,Class.class,String.class,Object.class),
                    handle("dispatchB",boolean.class,Object.class,String.class,Object.class),
                    handle("taskB",boolean.class,Object.class,String.class));
            throw new AssertionError("foreign owner callback replacement accepted");
        } catch(IllegalStateException expected) { require(true,"foreign owner cannot replace callbacks"); }
        try {
            TaskBridge.installUnsafeFields(Fields.class,new Field[]{l},new long[]{sharedOffset});
            throw new AssertionError("foreign owner mapping accepted");
        } catch(IllegalArgumentException expected) { require(true,"foreign owner cannot replace mapping"); }
        expectPolicyA(receiver);
        require(TaskBridge.unsafeWriteAllowed(new Object(),sharedOffset,1),"unrelated receiver remains allowed");
        require(!TaskBridge.unsafeWriteAllowed(receiver,sharedOffset+1,1),"overlapping byte within protected field refused");
        require(TaskBridge.unsafeWriteAllowed(receiver,sharedOffset+8,1),"non-overlapping offset remains allowed");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        AtomicBoolean done=new AtomicBoolean(),mixedAllowed=new AtomicBoolean();
        try {
            Future<?> publisher=pool.submit(()->{
                try { start.await();for(int n=0;n<20000;n++) { if((n&1)==0)policyA(owner,i,sharedOffset);else policyB(owner,l,sharedOffset); } }
                catch(Throwable failure) { throw new CompletionException(failure); }
                finally { done.set(true); }
            });
            Future<?> reader=pool.submit(()->{
                try { start.await();do { if(TaskBridge.unsafeWriteAllowed(receiver,sharedOffset,1))mixedAllowed.set(true); } while(!done.get());
                    for(int n=0;n<1000;n++)if(TaskBridge.unsafeWriteAllowed(receiver,sharedOffset,1))mixedAllowed.set(true);
                } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            start.countDown();publisher.get(10,TimeUnit.SECONDS);reader.get(10,TimeUnit.SECONDS);
            require(!mixedAllowed.get(),"concurrent complete policy publication never allows a mixed-generation Unsafe decision");
        } finally { pool.shutdownNow();require(pool.awaitTermination(5,TimeUnit.SECONDS),"guards test threads settled"); }
        System.out.println("GUARDS_CHECK_PASS");
    }
    static void expectFailure(Future<?> future)throws Exception {
        try { future.get(5,TimeUnit.SECONDS);throw new AssertionError("published forbidden result"); }
        catch(ExecutionException|CancellationException expected) {}
    }
    private static Object policyCall(Object state,String name,Class<?>[] types,Object... values)throws Exception {
        Method method=state.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        return method.invoke(state,values);
    }
    private static boolean resetPolicy(Object state,Object connection,java.util.UUID session,long scope,String dimension)throws Exception {
        return (boolean)policyCall(state,"reset",new Class<?>[]{Object.class,java.util.UUID.class,long.class,String.class},connection,session,scope,dimension);
    }
    private static boolean acceptPolicy(Object state,Object connection,java.util.UUID session,long scope,String dimension,long revision,int id,java.util.UUID uuid,boolean protect)throws Exception {
        return (boolean)policyCall(state,"accept",new Class<?>[]{Object.class,java.util.UUID.class,long.class,String.class,long.class,int.class,java.util.UUID.class,boolean.class},connection,session,scope,dimension,revision,id,uuid,protect);
    }
    private static boolean deniedPolicy(Object state,Object connection,String dimension,int id,java.util.UUID uuid)throws Exception {
        return (boolean)policyCall(state,"denies",new Class<?>[]{Object.class,String.class,int.class,java.util.UUID.class},connection,dimension,id,uuid);
    }
    private static void clientPolicy()throws Exception {
        var type=Class.forName("dev.ronova.pro.ClientPolicyState");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        Object state=constructor.newInstance(),a=new Object(),b=new Object();
        var session=java.util.UUID.randomUUID();var newerSession=java.util.UUID.randomUUID();
        var first=java.util.UUID.randomUUID();var second=java.util.UUID.randomUUID();String dim="minecraft:overworld";
        policyCall(state,"connection",new Class<?>[]{Object.class},a);
        require(!acceptPolicy(state,a,session,1,dim,1,7,first,true),"policy requires an actual scope reset");
        require(resetPolicy(state,a,session,1,dim)&&acceptPolicy(state,a,session,1,dim,1,7,first,true),"new connection policy accepted");
        require(deniedPolicy(state,a,dim,7,first)&&!deniedPolicy(state,a,dim,7,second),"exact UUID identity enforced");
        require(!deniedPolicy(state,a,"minecraft:the_nether",7,first),"wrong dimension has no authority");
        require(acceptPolicy(state,a,session,1,dim,2,7,first,false)&&!deniedPolicy(state,a,dim,7,first),"revocation is applied");
        require(!acceptPolicy(state,a,session,1,dim,1,7,first,true),"old protect cannot resurrect revoked entry");
        require(resetPolicy(state,a,session,1,dim)&&!acceptPolicy(state,a,session,1,dim,1,7,first,true),"same-scope reset retains tombstone");
        require(acceptPolicy(state,a,session,1,dim,3,7,second,true)&&!acceptPolicy(state,a,session,1,dim,2,7,first,false),"old identity revoke cannot replace newer id reuse");
        require(deniedPolicy(state,a,dim,7,second)&&!deniedPolicy(state,a,dim,7,first),"only reused id's current UUID is protected");
        require(!resetPolicy(state,a,newerSession,99,dim),"foreign session cannot replace a live connection session");
        require(resetPolicy(state,a,session,2,"minecraft:the_nether")&&!acceptPolicy(state,a,session,1,dim,99,7,first,true),"old scope rejected after dimension rotation");
        policyCall(state,"connection",new Class<?>[]{Object.class},(Object)null);
        require(!deniedPolicy(state,a,"minecraft:the_nether",7,second)&&!resetPolicy(state,a,session,3,dim),"disconnect immediately invalidates old transport");
        policyCall(state,"connection",new Class<?>[]{Object.class},b);
        require(!resetPolicy(state,a,session,4,dim)&&resetPolicy(state,b,newerSession,1,dim),"reconnect rejects old transport and accepts new session");
        Field limit=type.getDeclaredField("CAPACITY");limit.setAccessible(true);int capacity=limit.getInt(null);
        for(int id=0;id<capacity;id++)if(!acceptPolicy(state,b,newerSession,1,dim,id+1L,id,first,false))throw new AssertionError("premature capacity refusal "+id);
        require(!acceptPolicy(state,b,newerSession,1,dim,capacity+1L,capacity,first,true),"capacity refuses new entries without forgetting tombstone ordering");
        require((boolean)policyCall(state,"overflowed",new Class<?>[0]),"capacity gap is explicit");
        require(!resetPolicy(state,b,newerSession,1,dim)&&resetPolicy(state,b,newerSession,2,dim),"only a newer scope recovers capacity exhaustion");
        require(!acceptPolicy(state,b,newerSession,1,dim,Long.MAX_VALUE,1,first,true),"retired scope remains invalid after capacity recovery");
        System.out.println("CLIENT_POLICY_STATE_CHECK_PASS; METADATA_ONLY_NOT_CLIENT_RUNTIME_ACCEPTANCE");
    }

    private static void modGroupReturns(String jar,String profile)throws Exception {
        var finder=java.lang.module.ModuleFinder.of(Path.of(jar));
        String name=finder.findAll().iterator().next().descriptor().name();
        var configuration=ModuleLayer.boot().configuration().resolve(finder,java.lang.module.ModuleFinder.of(),Set.of(name));
        var layer=ModuleLayer.boot().defineModulesWithOneLoader(configuration,ClassLoader.getPlatformClassLoader());
        Class<?> target=Class.forName("dev.ronova.pro.validation.WorldFixture$GroupReturns",true,layer.findLoader(name));
        java.util.function.Function<String,Object> call=method->{
            try {return target.getMethod(method).invoke(null);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        };
        call.apply("action");
        require(Boolean.FALSE.equals(call.apply("bool"))&&Byte.valueOf((byte)0).equals(call.apply("bytes"))
                &&Character.valueOf((char)0).equals(call.apply("chars"))&&Short.valueOf((short)0).equals(call.apply("shorts"))
                &&Integer.valueOf(profile.equals("invalid-id")?-1:0).equals(call.apply("ints"))
                &&Long.valueOf(0).equals(call.apply("longs"))&&Float.valueOf(0).equals(call.apply("floats"))
                &&Double.valueOf(0).equals(call.apply("doubles")),"all primitive and void entries suppress business effects");
        require(call.apply("object")==null,"reference business object is not published");
        Object array=call.apply("array"),matrix=call.apply("matrix");
        require(profile.equals("empty")?array instanceof int[] a&&a.length==0&&matrix instanceof String[][] m&&m.length==0
                :array==null&&matrix==null,"primitive and multidimensional arrays honor the chosen policy");
        Object identity=call.apply("identity"),text=call.apply("text");
        if(profile.startsWith("uuid-")) {
            require(identity instanceof java.util.UUID&&text instanceof String,"UUID and String policies return valid identities");
            java.util.UUID.fromString((String)text);
            require(profile.equals("uuid-fixed")?identity.equals(call.apply("identity"))&&!identity.toString().equals("live")
                    :!identity.equals(call.apply("identity")),"fixed and per-call UUID semantics differ as selected");
        } else require(identity==null&&text==null,"default identity queries are empty");
        if(profile.equals("null"))require(call.apply("optional")==null&&call.apply("future")==null,"forced null covers reference contracts");
        else {
            require(((java.util.OptionalInt)call.apply("optional")).isEmpty()&&!((Iterator<?>)call.apply("iterator")).hasNext()
                    &&((java.util.stream.IntStream)call.apply("stream")).count()==0,"typed empty consumers stay usable");
            Future<?> future=(Future<?>)call.apply("future");
            require(future.isDone(),"Future consumer has no pending business work");
            try {require(future.get(1,TimeUnit.SECONDS)==null,"Future consumer returns an empty result");}
            catch(CancellationException|ExecutionException refused) {require(future.isDone(),"refused Future releases its consumer");}
        }
        try {target.getConstructor().newInstance();throw new AssertionError("stopped constructor returned a half object");}
        catch(InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalStateException
                    &&"RONOVA_MOD_GROUP_CREATION_REFUSED".equals(expected.getCause().getMessage()),"business constructor is refused before its prefix executes");
        }
        require(target.getField("effects").getInt(null)==0,"raw business effect count remains zero");
        require(new java.util.ArrayList<>(List.of(7)).get(0)==7,"outside shared framework remains active");
        System.out.println("MOD_GROUP_RETURN_EFFECTS_PASS profile="+profile);
    }
}
