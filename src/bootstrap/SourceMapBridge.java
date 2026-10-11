package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Synchronization for exact observed HashMap sources; no policy or write permission is issued here. */
public final class SourceMapBridge {
    private static final Object MONITOR=new Object();
    private static final ThreadLocal<Boolean> INTERNAL=new ThreadLocal<>();
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    // Registration is called while resource/source gates can already be held.
    // A monitored concurrent map would issue a guarded Unsafe counter CAS here,
    // taking a receiver gate whose permission check can acquire the resource lock.
    // Exact weak identities in fixed chains. Birth and retirement only link one
    // entry: no tree rotations, JDK map callbacks or index growth during startup.
    // The root array remains protected control state.
    private static final Key[] scopes=new Key[65536];
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final StackWalker ANALYSIS_CALLER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> ANALYSIS=new AnalysisCaller();
    private static final class AnalysisCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean>{
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();
                if(type.getNestHost()==SourceMapBridge.class)continue;
                String name=type.getName();
                if(type.getClassLoader()==null&&(name.startsWith("java.")||name.startsWith("jdk.")||name.startsWith("sun.")))continue;
                // The first actual application caller must be our admitted
                // agent. A foreign callback (including hidden code) stops here;
                // an agent farther down the stack supplies no such authority.
                return ControlBridge.owns(type);
            }
            return false;
        }
    }
    private static volatile boolean enabled;
    private static volatile String failure="";
    private static volatile java.lang.invoke.MethodHandle writeGuard;
    private static volatile java.lang.invoke.MethodHandle changeObserver;
    private static final ThreadLocal<Boolean> QUERY=new ThreadLocal<>();
    private static final class Key extends WeakReference<Object> {
        final int hash;
        volatile Scope scope;volatile boolean born;int active;Key next;
        Key(Object object) {
            super(object,DEAD);hash=System.identityHashCode(object);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            Object value=get();return value!=null&&other instanceof Key key&&value==key.get();
        }
        @Override public void clear(){if(CALLER.getCallerClass().getNestHost()==SourceMapBridge.class)super.clear();}
        @Override public boolean enqueue(){return CALLER.getCallerClass().getNestHost()==SourceMapBridge.class&&super.enqueue();}
    }
    private static final class OwnerRef extends WeakReference<Object> {
        OwnerRef(Object owner){super(owner);}
        @Override public void clear(){if(CALLER.getCallerClass().getNestHost()==SourceMapBridge.class)super.clear();}
        @Override public boolean enqueue(){return CALLER.getCallerClass().getNestHost()==SourceMapBridge.class&&super.enqueue();}
    }
    private static final class Gate {
        final ReentrantLock lock=new ReentrantLock();
        final WeakReference<Object> owner;
        volatile boolean ready,source;
        int readers;
        volatile long revision;
        OwnerRef bindingTable,bindingNext;
        long bindingRevision=-1;
        int bindingBucket;
        final List<Key> draining=new ArrayList<>();
        Gate(Object owner) {
            this.owner=new OwnerRef(owner);
            CodeSourceBridge.fieldGateControls(lock);
        }
    }
    static boolean gateConstructor(Class<?> caller) { return caller==Gate.class; }
    private static final class HashNodes {
        static final Class<?> NODE=nodeType();
        static final java.lang.reflect.Field TABLE=field(HashMap.class,"table"),NEXT=field(nodeType(),"next");
        static final java.lang.reflect.Field ACCESS_ORDER=field(LinkedHashMap.class,"accessOrder"),TAIL=field(LinkedHashMap.class,"tail");
        static final java.lang.reflect.Method GET=getNode();
        private static java.lang.reflect.Method getNode(){
            try{var method=HashMap.class.getDeclaredMethod("getNode",Object.class);method.setAccessible(true);return method;}
            catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
        }
        private static Class<?> nodeType(){try{return Class.forName("java.util.HashMap$Node",false,null);}catch(ClassNotFoundException missing){throw new ExceptionInInitializerError(missing);}}
        private static java.lang.reflect.Field field(Class<?> type,String name){
            try{var field=type.getDeclaredField(name);field.setAccessible(true);return field;}
            catch(ReflectiveOperationException missing){throw new ExceptionInInitializerError(missing);}
        }
    }
    /** Per receiver: publishing a gate and entering an unguarded writer share this monitor. */
    private static final class Scope {
        volatile Gate gate;
        volatile boolean concurrent,holder,carrier;
        java.util.concurrent.locks.ReentrantReadWriteLock concurrentGate;
    }
    private static final class Frame {
        Object receiver;Key key;Gate gate;final Thread thread=Thread.currentThread();
        boolean active,affects,pooled;Object publication;Class<?> caller;Frame next;
        Frame(Object receiver,Key key,Gate gate) { reset(receiver,key,gate); }
        void reset(Object receiver,Key key,Gate gate){this.receiver=receiver;this.key=key;this.gate=gate;active=true;affects=true;}
    }
    private static final ThreadLocal<Frame> FREE_FRAMES=new ThreadLocal<>();
    private static final ThreadLocal<ConcurrentFrame> FREE_CONCURRENT=new ThreadLocal<>();
    // Only an actual woven JDK caller can borrow and return an ordinary-operation
    // frame. Controller/public carrier tokens remain unique for their lifetime.
    private static Frame operationFrame(Object receiver,Key key,Gate gate,Class<?> caller){
        boolean pooled=gate==null&&(caller==HashMap.class||caller==LinkedHashMap.class||caller==ArrayList.class||caller==HashNodes.NODE);
        Frame frame=pooled?FREE_FRAMES.get():null;
        if(frame==null)frame=new Frame(receiver,key,gate);
        else {FREE_FRAMES.set(frame.next);frame.next=null;frame.reset(receiver,key,gate);}
        frame.pooled=pooled;frame.caller=caller;return frame;
    }
    private static void releaseFrame(Frame frame){
        frame.receiver=null;frame.key=null;frame.gate=null;frame.publication=null;frame.caller=null;
        if(frame.pooled){frame.next=FREE_FRAMES.get();FREE_FRAMES.set(frame);}
    }
    private SourceMapBridge() { }
    public static int version() { return 1; }
    // Every INTERNAL region uses only our identity keys and exact JDK bookkeeping;
    // it must end before invoking any Core observer or application callback.
    static boolean internal() { return INTERNAL.get()!=null; }
    static boolean controlled(Object value) { return value==scopes; }
    static void install(java.lang.invoke.MethodHandle guard,java.lang.invoke.MethodHandle changed) {
        writeGuard=guard.asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
        changeObserver=changed.asType(java.lang.invoke.MethodType.methodType(void.class,Object.class));
    }
    public static final class Refused extends RuntimeException {
        private final Object map,retained;private final Thread thread=Thread.currentThread();
        private Refused(Object map,Object key) {
            super("SOURCE_VALUE_REFUSED",null,false,false);this.map=map;this.retained=((Map<?,?>)map).get(key);
        }
    }
    public static Object publicResult(Object result,boolean conditional) {
        return result instanceof Refused refusal&&refusal.thread==Thread.currentThread()?(conditional?null:refusal.retained):result;
    }
    public static boolean ownsRefusal(Throwable failure,Object map) { return failure instanceof Refused refusal&&refusal.map==map&&refusal.thread==Thread.currentThread(); }
    public static void checkIncoming(Object map,Object key,Object value) {
        if(!valueAllowed(map,key,value)&&CALLER.getCallerClass()==java.util.concurrent.ConcurrentHashMap.class)throw new Refused(map,key);
    }
    public static void observeConcurrent(Object map) {
        if(map==null||map.getClass()!=java.util.concurrent.ConcurrentHashMap.class)return;
        INTERNAL.set(true);
        try { scope(map).concurrent=true; }
        finally { INTERNAL.remove(); }
    }
    public static void changed(Object map) {
        var observer=changeObserver;
        if(!enabled||observer==null||executionMetadata(map)||INTERNAL.get()!=null||QUERY.get()!=null)return;
        boolean known;
        INTERNAL.set(true);
        try { Scope scope=scope(map,false);known=scope!=null&&(scope.gate!=null||scope.concurrent); }
        finally { INTERNAL.remove(); }
        if(!known)return;
        QUERY.set(true);
        try { observer.invokeExact(map); }
        catch(Throwable failure) { SourceMapBridge.failure="SOURCE_CHANGE_OBSERVER_UNAVAILABLE"; }
        finally { QUERY.remove(); }
    }
    private static final class ConcurrentFrame {
        Key key;java.util.concurrent.locks.Lock gate;Object map;
        final Thread thread=Thread.currentThread();boolean closed,pooled;Object publication;ConcurrentFrame next;
        ConcurrentFrame(Object map,Key key,java.util.concurrent.locks.Lock gate){reset(map,key,gate);}
        void reset(Object map,Key key,java.util.concurrent.locks.Lock gate){this.map=map;this.key=key;this.gate=gate;closed=false;}
    }
    static void registerHolder(Object holder){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_HOLDER_WRITER_REQUIRED");
        INTERNAL.set(true);try{Key key=key(holder,true);synchronized(key){controlScope(key).holder=true;if(CodeSourceBridge.analysisContainer(holder))key.born=true;}}finally{INTERNAL.remove();}
    }
    static void registerCarrier(Object carrier){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_CARRIER_WRITER_REQUIRED");
        INTERNAL.set(true);try{
            Key key=key(carrier,true);synchronized(key){Scope scope=controlScope(key);scope.carrier=true;if(CodeSourceBridge.analysisContainer(carrier))key.born=true;
                if(scope.gate==null)scope.gate=new Gate(carrier);scope.gate.source=true;}
        }finally{INTERNAL.remove();}
    }
    public static boolean carrierRegistered(Object carrier){
        if(!enabled||carrier==null||INTERNAL.get()!=null)return false;
        INTERNAL.set(true);try{Scope scope=scope(carrier,false);return scope!=null&&scope.carrier;}finally{INTERNAL.remove();}
    }
    static void recoveredChange(Object map){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_RECOVERY_WRITER_REQUIRED");
        Scope scope=scope(map,false);Gate gate=scope==null?null:scope.gate;
        if(gate==null||!gate.lock.isHeldByCurrentThread())throw new IllegalMonitorStateException("SOURCE_RECOVERY_GATE_REQUIRED");
        gate.revision++;changed(map);
    }
    private static boolean scalarCarrier(Object value){return value.getClass()==java.util.concurrent.atomic.AtomicReference.class||value.getClass()==java.util.concurrent.CopyOnWriteArrayList.class;}
    static boolean holderRegistered(Object holder){
        if(!enabled||holder==null||INTERNAL.get()!=null)return false;
        INTERNAL.set(true);try{Scope scope=scope(holder,false);return scope!=null&&scope.holder;}finally{INTERNAL.remove();}
    }
    private static boolean analysisContainer(Object receiver){
        // Only the exact constructor-observed private collection has this
        // role. It is protected control state, so a foreign callback cannot
        // mutate it by borrowing the compiler's deeper stack frame. A later
        // business holder/carrier registration keeps its ordinary observation.
        if(!CodeSourceBridge.analysisContainer(receiver))return false;
        Scope scope=scope(receiver,false);
        return scope==null||!scope.holder&&!scope.carrier&&scope.gate==null&&!scope.concurrent;
    }
    /** Concurrent writers keep their original parallel bin semantics. Only publication takes a write lock. */
    public static Object enterConcurrent(Object map){
        if(!enabled||INTERNAL.get()!=null)return null;
        if(CALLER.getCallerClass()!=java.util.concurrent.ConcurrentHashMap.class)throw new SecurityException("ACTUAL_CONCURRENT_WRITER_REQUIRED");
        if(analysisContainer(map))return null;
        Key key;Scope scope;java.util.concurrent.locks.Lock gate;
        INTERNAL.set(true);
        try{key=key(map,true);synchronized(key){scope=key.scope;gate=scope==null||scope.concurrentGate==null?null:scope.concurrentGate.readLock();if(gate==null)key.active++;}}
        finally{INTERNAL.remove();}
        if(gate!=null)gate.lock();
        boolean pooled=gate==null&&(scope==null||!scope.concurrent);ConcurrentFrame frame=pooled?FREE_CONCURRENT.get():null;
        if(frame==null)frame=new ConcurrentFrame(map,key,gate);
        else{FREE_CONCURRENT.set(frame.next);frame.next=null;frame.reset(map,key,gate);}
        frame.pooled=pooled;
        try{if(scope!=null&&scope.concurrent)frame.publication=ControlBridge.beginSourcePublication(map);return frame;}
        catch(RuntimeException|Error failure){if(gate!=null)gate.unlock();else synchronized(key){key.active--;}releaseConcurrent(frame);throw failure;}
    }
    public static void exitConcurrent(Object token){
        if(token==null)return;
        if(!(token instanceof ConcurrentFrame frame)||frame.thread!=Thread.currentThread())throw new IllegalMonitorStateException("CONCURRENT_WRITER_THREAD_REQUIRED");
        if(frame.pooled&&CALLER.getCallerClass()!=java.util.concurrent.ConcurrentHashMap.class)throw new SecurityException("ACTUAL_CONCURRENT_WRITER_REQUIRED");
        if(frame.closed)return;frame.closed=true;
        try{changed(frame.map);}finally{
            try{if(frame.publication!=null)ControlBridge.endSourcePublication(frame.publication);}
            finally{try{if(frame.gate!=null)frame.gate.unlock();else synchronized(frame.key){frame.key.active--;}}finally{releaseConcurrent(frame);}}
        }
    }
    private static void releaseConcurrent(ConcurrentFrame frame){
        frame.map=null;frame.key=null;frame.gate=null;frame.publication=null;
        if(frame.pooled){frame.next=FREE_CONCURRENT.get();FREE_CONCURRENT.set(frame);}
    }
    static java.util.concurrent.locks.Lock publicationGate(Object incoming){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_PUBLICATION_WRITER_REQUIRED");
        if(!enabled||!failure.isEmpty()||incoming==null)return null;
        if(hashMap(incoming)){
            Gate gate=ready(incoming);if(gate==null||!gate.lock.tryLock())return null;
            if(gate.readers!=0){gate.lock.unlock();return null;}return gate.lock;
        }
        Key key=key(incoming,false);if(key==null||!key.born)return null;
        Scope scope;synchronized(key){scope=controlScope(key);}
        if(incoming.getClass()==ArrayList.class||scalarCarrier(incoming)){
            Gate gate;synchronized(key){gate=scope.gate;if(gate==null)scope.gate=gate=new Gate(incoming);}
            if(!gate.lock.tryLock())return null;
            synchronized(key){if(key.active!=0||gate.readers!=0){gate.lock.unlock();return null;}}
            gate.ready=true;return gate.lock;
        }
        if(incoming.getClass()==java.util.concurrent.ConcurrentHashMap.class){
            java.util.concurrent.locks.Lock gate;
            synchronized(key){if(scope.concurrentGate==null)scope.concurrentGate=new java.util.concurrent.locks.ReentrantReadWriteLock();gate=scope.concurrentGate.writeLock();}
            if(!gate.tryLock())return null;
            synchronized(key){if(key.active!=0){gate.unlock();return null;}}
            return gate;
        }
        return null;
    }
    /** A completed field/Unsafe scope reports the real holder while its receiver gate is held. */
    static void holderChanged(Object holder) {
        var observer=changeObserver;
        if(!enabled||observer==null||holder==null||INTERNAL.get()!=null||QUERY.get()!=null)return;
        // Before the real observer is installed this is an effect-free no-op.
        // Do not walk every vanilla array-store stack merely to return. Actual
        // revision updates/callbacks below still require the exact writer.
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_HOLDER_WRITE_REQUIRED");
        if(ControlBridge.primitiveSourceIndex(holder)){
            if(ControlBridge.sourceIndexWriting(holder))return;
            Scope scope=scope(holder,false);if(scope!=null&&scope.gate!=null)scope.gate.revision++;
        }
        QUERY.set(true);
        try {observer.invokeExact(holder);}
        catch(Throwable unavailable){failure="SOURCE_HOLDER_OBSERVER_UNAVAILABLE";}
        finally {QUERY.remove();}
    }
    static long indexRevision(Object map){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_INDEX_WRITER_REQUIRED");
        Scope scope=scope(map,false);return scope==null||scope.gate==null?-1:scope.gate.revision;
    }
    public static boolean valueAllowed(Object receiver,Object key,Object value) {
        // INTERNAL is set only while this bridge handles its private registration state.
        // Re-entering the general backing policy there can take the world ledger lock
        // while MONITOR is held, reversing the external writer's lock order.
        if(INTERNAL.get()!=null)return true;
        if(!enabled)return true;
        // The backing entry checks this receiver's control guard before its element policy.
        if(!BackingBridge.incomingPairAllowed(receiver,key,value))return false;
        if(executionMetadata(receiver))return true;
        var guard=writeGuard;
        if(!enabled||guard==null||receiver==null||value==null||INTERNAL.get()!=null||QUERY.get()!=null)return true;
        Gate gate;boolean concurrent;
        INTERNAL.set(true);
        try {
            Scope scope=scope(receiver,false);concurrent=scope!=null&&scope.concurrent;gate=scope==null?null:scope.gate;
        }
        finally { INTERNAL.remove(); }
        if(gate==null&&!concurrent)return true;
        Object map=concurrent?receiver:gate.owner.get();if(map==null)return true;
        if(receiver!=map) {
            boolean live=false;for(Object node:((HashMap<?,?>)map).entrySet())if(node==receiver) { live=true;break; }
            if(!live)return true;
        }
        QUERY.set(true);
        try { return !(boolean)guard.invokeExact(map,key,value); }
        catch(Throwable unavailable) { failure="SOURCE_WRITE_GUARD_UNAVAILABLE";return false; }
        finally { QUERY.remove(); }
    }
    /** Called only after both JDK components have been successfully retransformed. */
    public static void activate() { enabled=true; }
    public static String state() { return !failure.isEmpty()?failure:enabled?"HASHMAP_SCOPES_INSTALLED":"NOT_INSTALLED"; }
    private static void sweep(int budget) {
        indexWriter(CALLER.getCallerClass());
        // MONITOR is held. A cleared referent cannot identify its old record;
        // remove the exact queued Key without disturbing a live hash collision.
        for(int remaining=budget;remaining>0;remaining--) {
            Key dead=(Key)DEAD.poll();if(dead==null)break;
            int bucket=rootIndex(dead.hash);
            Key previous=null,entry=scopes[bucket];
            while(entry!=null&&entry!=dead){previous=entry;entry=entry.next;}
            if(entry==null)continue;
            if(previous!=null)previous.next=entry.next;
            else scopes[bucket]=entry.next;
            entry.next=null;
        }
    }
    private static void indexWriter(Class<?> caller) {
        if(caller!=SourceMapBridge.class||!Thread.holdsLock(MONITOR))throw new SecurityException("SOURCE_SCOPE_INDEX_WRITER_REQUIRED");
    }
    private static int rootIndex(int hash) { return (hash^(hash>>>16))&(scopes.length-1); }
    /** Exact current scope owner for a HashMap node; the policy and node store use one gate. */
    public static Object ownerOf(Object node) {
        if(!enabled||node==null||INTERNAL.get()!=null)return null;
        INTERNAL.set(true);
        try {
            Scope scope=scope(node,false);
            Gate gate=scope==null?null:scope.gate;
            return gate==null?null:gate.owner.get();
        } finally { INTERNAL.remove(); }
    }
    private static Scope scope(Object receiver) {
        return scope(receiver,true);
    }
    private static Scope scope(Object receiver,boolean create) {
        Key key=key(receiver,create);if(key==null)return null;
        if(!create)return key.scope;
        synchronized(key){return controlScope(key);}
    }
    /** Only a selected holder/carrier or an actual snapshot allocates control state. */
    private static Scope controlScope(Key key){
        Scope scope=key.scope;if(scope==null)key.scope=scope=new Scope();return scope;
    }
    private static Key key(Object receiver,boolean create) {
        synchronized(MONITOR) {
            int hash=System.identityHashCode(receiver),bucket=rootIndex(hash);
            for(Key entry=scopes[bucket];entry!=null;entry=entry.next)
                if(entry.hash==hash&&entry.get()==receiver)return entry;
            if(!create)return null;
            indexWriter(CALLER.getCallerClass());Key added=new Key(receiver);
            added.next=scopes[bucket];scopes[bucket]=added;
            // Charge retirement to every actual insertion, including node scopes.
            // A cleared key cannot match a live receiver while it awaits this work.
            sweep(2);
            return added;
        }
    }
    private static boolean hashMap(Object map){return map!=null&&(map.getClass()==HashMap.class||map.getClass()==java.util.LinkedHashMap.class);}
    public static void born(Object map) {
        if(!enabled||!hashMap(map)&&map.getClass()!=ArrayList.class&&!scalarCarrier(map)&&map.getClass()!=java.util.concurrent.ConcurrentHashMap.class||INTERNAL.get()!=null)return;
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=map.getClass()&&!(map.getClass()==java.util.LinkedHashMap.class&&caller==HashMap.class))return;
        boolean analysis=ANALYSIS_CALLER.walk(ANALYSIS);
        INTERNAL.set(true);
        try {
            if(analysis){CodeSourceBridge.analysisContainerControl(map);return;}
            key(map,true).born=true;
        }
        finally { INTERNAL.remove(); }
    }
    private static boolean executionMetadata(Object receiver){
        // Roles belong only to the exact private constructor-registered object.
        // Plain JDK containers cannot acquire them through protection or copying.
        return receiver instanceof ArrayList<?> &&receiver.getClass()!=ArrayList.class&&CodeSourceBridge.executionList(receiver)
                ||receiver instanceof HashMap<?,?> &&receiver.getClass()!=HashMap.class&&CodeSourceBridge.executionMap(receiver);
    }
    public static Object enter(Object receiver,boolean mutation) {
        if(!enabled||executionMetadata(receiver)||INTERNAL.get()!=null)return null;
        // Compiler work tables are private even before an image is published
        // to the control directory. Existing business holders/carriers retain
        // their observation; this never bypasses a field/index/array write gate.
        if(analysisContainer(receiver))return null;
        Key key;Gate gate;
        INTERNAL.set(true);
        try {
            key=key(receiver,true);
            synchronized(key) { Scope scope=key.scope;gate=scope==null?null:scope.gate;if(gate==null)key.active++; }
        } finally { INTERNAL.remove(); }
        Frame frame=operationFrame(receiver,key,gate,CALLER.getCallerClass());frame.affects=mutation;
        if(gate!=null) {
            gate.lock.lock();
            try {
                if(!mutation)gate.readers++;
                if(!hashMap(receiver)&&receiver.getClass()!=ArrayList.class&&!scalarCarrier(receiver)) {
                    frame.affects=false;
                    Object map=gate.owner.get();
                    if(map!=null)for(Object node:((HashMap<?,?>)map).entrySet())if(node==receiver) { frame.affects=true;break; }
                }
                if(frame.affects&&gate.source)frame.publication=ControlBridge.beginSourcePublication(gate.owner.get());
            } catch(RuntimeException|Error unavailable) { if(!mutation)gate.readers--;gate.lock.unlock();frame.active=false;releaseFrame(frame);throw unavailable; }
        }
        return frame;
    }
    public static void exit(Object token) {
        if(token==null)return;
        if(!(token instanceof Frame frame)||frame.thread!=Thread.currentThread()||!frame.active) {
            failure="HASHMAP_SCOPE_MISMATCH";return;
        }
        if(frame.pooled&&CALLER.getCallerClass()!=frame.caller)throw new SecurityException("ACTUAL_SCOPE_WRITER_REQUIRED");
        frame.active=false;
        try{if(frame.gate!=null) {
            if(frame.affects)frame.gate.revision++;else if(hashMap(frame.receiver)||frame.receiver.getClass()==ArrayList.class||scalarCarrier(frame.receiver))frame.gate.readers--;
            try { if(frame.affects) { Object map=frame.gate.owner.get();if(map!=null)changed(map); } }
            finally {try{if(frame.publication!=null)ControlBridge.endSourcePublication(frame.publication);}finally{frame.gate.lock.unlock();}}
        } else synchronized(frame.key) { frame.key.active--; }}finally{releaseFrame(frame);}
    }
    public static Object enterNodeAccess(Object map,Object node) {
        if(CALLER.getCallerClass()!=LinkedHashMap.class)throw new SecurityException("ACTUAL_LINKED_MAP_ACCESS_REQUIRED");
        if(map==null||map.getClass()!=LinkedHashMap.class)return enter(map,true);
        Object token=enter(map,false);
        if(!(token instanceof Frame frame)||frame.gate==null)return token;
        try {
            if(HashNodes.ACCESS_ORDER.getBoolean(map)&&HashNodes.TAIL.get(map)!=node) {
                if(frame.gate.source)frame.publication=ControlBridge.beginSourcePublication(map);
                frame.gate.readers--;frame.affects=true;
            }
            return token;
        }catch(IllegalAccessException unavailable){exit(token);throw new IllegalStateException("LINKED_MAP_ORDER_UNAVAILABLE",unavailable);}
        catch(RuntimeException|Error failure){exit(token);throw failure;}
    }
    public static void node(Object map,Object node) {
        if(!enabled||node==null||executionMetadata(map)||INTERNAL.get()!=null)return;
        if(analysisContainer(map)){
            Class<?> caller=CALLER.getCallerClass();
            if(caller!=HashMap.class&&caller!=java.util.LinkedHashMap.class)return;
            // The exact node still becomes protected control state. It needs
            // no business source Gate/Scope until the actual parent is later
            // registered as a holder/carrier; ready() then binds all live nodes.
            INTERNAL.set(true);
            try{CodeSourceBridge.analysisNodeControl(map,node);}finally{INTERNAL.remove();}
            return;
        }
        INTERNAL.set(true);
        try {
            Scope owner=scope(map,false);Gate gate=owner==null?null:owner.gate;
            // No registration or authorization happens for an unrelated map.
            // Late source registration still walks and binds its existing nodes in ready().
            boolean controlled=gate==null&&ControlBridge.controlled(map);
            if(gate==null&&!controlled)return;
            Class<?> caller=CALLER.getCallerClass();
            boolean actualFactory=caller==HashMap.class||caller==java.util.LinkedHashMap.class;
            if(actualFactory&&controlled) {
                Key key=key(map,true);synchronized(key){owner=controlScope(key);if(owner.gate==null)owner.gate=new Gate(map);gate=owner.gate;}
            }
            if(gate!=null&&actualFactory) {
                Key entry=key(node,true);synchronized(entry) {controlScope(entry).gate=gate;}
            }
        } finally { INTERNAL.remove(); }
    }
    /** Late registration first drains operations which entered before the map was registered. */
    private static Gate ready(Object object) {
        if(!enabled||!failure.isEmpty()||!hashMap(object))return null;
        Key owner=key(object,false);if(owner==null||!owner.born)return null;
        Scope scope=owner.scope;Gate gate=scope==null?null:scope.gate;if(gate!=null&&gate.ready)return gate;
        INTERNAL.set(true);
        try {
            synchronized(owner) {
                scope=controlScope(owner);gate=scope.gate;if(gate==null)scope.gate=gate=new Gate(object);
            }
            if(!gate.lock.tryLock())return null;
            try {
                if(gate.ready)return gate;
                synchronized(owner) {if(owner.active!=0)return null;}
                Object[] table=(Object[])HashNodes.TABLE.get(object);
                if(table==null){gate.ready=true;return gate;}
                if(gate.bindingTable==null||gate.bindingTable.get()!=table||gate.bindingRevision!=gate.revision){
                    gate.bindingTable=new OwnerRef(table);gate.bindingNext=null;gate.bindingBucket=0;
                    gate.bindingRevision=gate.revision;gate.draining.clear();
                }
                int budget=128;
                while(gate.bindingBucket<table.length&&budget-->0){
                    Object entry=gate.bindingNext==null?table[gate.bindingBucket]:gate.bindingNext.get();
                    if(entry==null){gate.bindingBucket++;gate.bindingNext=null;continue;}
                    Object next=HashNodes.NEXT.get(entry);Key node=key(entry,true);
                    synchronized(node){controlScope(node).gate=gate;if(node.active!=0)gate.draining.add(node);}
                    gate.bindingNext=next==null?null:new OwnerRef(next);
                    if(next==null)gate.bindingBucket++;
                }
                if(gate.bindingBucket<table.length)return null;
                for(var cursor=gate.draining.iterator();cursor.hasNext();){Key node=cursor.next();synchronized(node){if(node.active==0)cursor.remove();}}
                if(!gate.draining.isEmpty())return null;
                gate.ready=true;return gate;
            } catch(IllegalAccessException unavailable){throw new IllegalStateException("SOURCE_MAP_ACTUAL_NODES_UNAVAILABLE",unavailable);
            } finally { gate.lock.unlock(); }
        } finally { INTERNAL.remove(); }
    }
    public static Object[][] entries(Object map) {
        Gate gate=ready(map);if(gate==null||!gate.lock.tryLock())return null;
        try {
            int size=((HashMap<?,?>)map).size();
            Object[][] rows=new Object[size][];int index=0;
            for(var entry:((HashMap<?,?>)map).entrySet())rows[index++]=new Object[]{entry.getKey(),entry.getValue()};
            return rows;
        } finally { gate.lock.unlock(); }
    }
    /** null means busy/unobserved, a one-element array contains the actual value, including null. */
    public static Object[] peek(Object map,Object key) {
        Gate gate=ready(map);if(gate==null||!gate.lock.tryLock())return null;
        try {
            // Query the stored node without LinkedHashMap.get moving access-order links.
            var entry=(java.util.Map.Entry<?,?>)HashNodes.GET.invoke(map,key);
            return new Object[]{entry!=null,entry==null?null:entry.getValue()};
        } catch(ReflectiveOperationException unavailable){throw new IllegalStateException("SOURCE_MAP_NODE_QUERY_UNAVAILABLE",unavailable);}
        finally { gate.lock.unlock(); }
    }
    public static long revision(Object map) {
        Gate gate=ready(map);return gate==null?-1:gate.revision;
    }
    public static boolean withMap(Object map,Runnable action) {
        Gate gate=ready(map);if(gate==null||!gate.lock.tryLock())return false;
        try { if(gate.readers!=0)return false;action.run();return true; }
        finally { gate.lock.unlock(); }
    }
    public static boolean withList(Object list,Runnable action) {
        return withList(list,action,true);
    }
    public static boolean withListRead(Object list,Runnable action) {
        return withList(list,action,false);
    }
    private static boolean withList(Object list,Runnable action,boolean mutation) {
        if(!enabled||!failure.isEmpty()||list==null||list.getClass()!=ArrayList.class)return false;
        Key key=key(list,false);if(key==null||!key.born)return false;Scope scope;
        Gate gate;
        synchronized(key) {scope=controlScope(key);gate=scope.gate;if(gate==null)scope.gate=gate=new Gate(list);}
        if(!gate.lock.tryLock())return false;
        try {
            synchronized(key) {if(key.active!=0)return false;}
            gate.ready=true;action.run();if(mutation)gate.revision++;return true;
        }finally {gate.lock.unlock();}
    }
    /** Only a caller inside the actual list gate can observe its mutation revision. */
    public static long listRevision(Object list) {
        if(!enabled||!failure.isEmpty()||list==null||list.getClass()!=ArrayList.class)return -1;
        Scope scope=scope(list,false);Gate gate=scope==null?null:scope.gate;
        return gate!=null&&gate.lock.isHeldByCurrentThread()?gate.revision:-1;
    }
}
