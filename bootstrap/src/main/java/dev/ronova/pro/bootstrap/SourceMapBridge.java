package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Synchronization for exact observed HashMap sources; no policy or write permission is issued here. */
public final class SourceMapBridge {
    private static final Object MONITOR=new Object();
    private static final ThreadLocal<Boolean> INTERNAL=new ThreadLocal<>();
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,Scope> SCOPES=new ConcurrentHashMap<>();
    private static final Map<Key,Boolean> CONCURRENT=new ConcurrentHashMap<>();
    private static final AtomicInteger BIRTHS=new AtomicInteger();
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile boolean enabled;
    private static volatile String failure="";
    private static volatile java.lang.invoke.MethodHandle writeGuard;
    private static volatile java.lang.invoke.MethodHandle changeObserver;
    private static final ThreadLocal<Boolean> QUERY=new ThreadLocal<>();
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object object) { super(object,DEAD);hash=System.identityHashCode(object); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            Object value=get();return value!=null&&other instanceof Key key&&value==key.get();
        }
    }
    private static final class Gate {
        final ReentrantLock lock=new ReentrantLock();
        final WeakReference<Object> owner;
        volatile boolean ready;
        int readers;
        volatile long revision;
        Gate(Object owner) { this.owner=new WeakReference<>(owner); }
    }
    /** Per receiver: publishing a gate and entering an unguarded writer share this monitor. */
    private static final class Scope {
        volatile Gate gate;
        volatile boolean born;
        int active;
    }
    private static final class Frame {
        final Object receiver;final Scope scope;final Gate gate;final Thread thread=Thread.currentThread();
        boolean active=true,affects=true;
        Frame(Object receiver,Scope scope,Gate gate) { this.receiver=receiver;this.scope=scope;this.gate=gate; }
    }
    private SourceMapBridge() { }
    public static int version() { return 1; }
    // Every INTERNAL region uses only our identity keys and exact JDK bookkeeping;
    // it must end before invoking any Core observer or application callback.
    static boolean internal() { return INTERNAL.get()!=null; }
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
        try { CONCURRENT.put(new Key(map),Boolean.TRUE); }
        finally { INTERNAL.remove(); }
    }
    public static void changed(Object map) {
        var observer=changeObserver;
        if(!enabled||observer==null||INTERNAL.get()!=null||QUERY.get()!=null)return;
        boolean known;
        INTERNAL.set(true);
        try { Key key=new Key(map);Scope scope=SCOPES.get(key);known=scope!=null&&scope.gate!=null||CONCURRENT.containsKey(key); }
        finally { INTERNAL.remove(); }
        if(!known)return;
        QUERY.set(true);
        try { observer.invokeExact(map); }
        catch(Throwable failure) { SourceMapBridge.failure="SOURCE_CHANGE_OBSERVER_UNAVAILABLE"; }
        finally { QUERY.remove(); }
    }
    public static boolean valueAllowed(Object receiver,Object key,Object value) {
        // INTERNAL is set only while this bridge updates its private registration maps.
        // Re-entering the general backing policy there can take the world ledger lock
        // while MONITOR is held, reversing the external writer's lock order.
        if(INTERNAL.get()!=null)return true;
        if(!enabled)return true;
        if(!TaskBridge.controlMutationAllowed(receiver)||!BackingBridge.incomingAllowed(receiver,value))return false;
        var guard=writeGuard;
        if(!enabled||guard==null||receiver==null||value==null||INTERNAL.get()!=null||QUERY.get()!=null)return true;
        Gate gate;boolean concurrent;
        INTERNAL.set(true);
        try {
            Key id=new Key(receiver);concurrent=CONCURRENT.containsKey(id);
            Scope scope=SCOPES.get(id);gate=scope==null?null:scope.gate;
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
    private static void sweep() {
        Key dead;while((dead=(Key)DEAD.poll())!=null) { SCOPES.remove(dead);CONCURRENT.remove(dead); }
    }
    /** Exact current scope owner for a HashMap node; the policy and node store use one gate. */
    public static Object ownerOf(Object node) {
        if(!enabled||node==null||INTERNAL.get()!=null)return null;
        INTERNAL.set(true);
        try {
            Scope scope=SCOPES.get(new Key(node));
            Gate gate=scope==null?null:scope.gate;
            return gate==null?null:gate.owner.get();
        } finally { INTERNAL.remove(); }
    }
    private static Scope scope(Object receiver) {
        return SCOPES.computeIfAbsent(new Key(receiver),key->new Scope());
    }
    public static void born(Object map) {
        if(!enabled||INTERNAL.get()!=null||map.getClass()!=HashMap.class)return;
        if(CALLER.getCallerClass()!=HashMap.class)return;
        INTERNAL.set(true);
        try {
            scope(map).born=true;
            if((BIRTHS.incrementAndGet()&1023)==0)synchronized(MONITOR) { sweep(); }
        }
        finally { INTERNAL.remove(); }
    }
    public static Object enter(Object receiver,boolean mutation) {
        if(!enabled||INTERNAL.get()!=null)return null;
        Scope scope;Gate gate;
        INTERNAL.set(true);
        try {
            scope=scope(receiver);
            synchronized(scope) { gate=scope.gate;if(gate==null)scope.active++; }
        } finally { INTERNAL.remove(); }
        Frame frame=new Frame(receiver,scope,gate);frame.affects=mutation;
        if(gate!=null) {
            gate.lock.lock();
            try {
                if(!mutation)gate.readers++;
                if(receiver.getClass()!=HashMap.class) {
                    frame.affects=false;
                    Object map=gate.owner.get();
                    if(map!=null)for(Object node:((HashMap<?,?>)map).entrySet())if(node==receiver) { frame.affects=true;break; }
                }
            } catch(RuntimeException|Error unavailable) { gate.lock.unlock();throw unavailable; }
        }
        return frame;
    }
    public static void exit(Object token) {
        if(token==null)return;
        if(!(token instanceof Frame frame)||frame.thread!=Thread.currentThread()||!frame.active) {
            failure="HASHMAP_SCOPE_MISMATCH";return;
        }
        frame.active=false;
        if(frame.gate!=null) {
            if(frame.affects)frame.gate.revision++;else if(frame.receiver.getClass()==HashMap.class)frame.gate.readers--;
            try { if(frame.affects) { Object map=frame.gate.owner.get();if(map!=null)changed(map); } }
            finally { frame.gate.lock.unlock(); }
        } else synchronized(frame.scope) { frame.scope.active--; }
    }
    public static void node(Object map,Object node) {
        if(!enabled||node==null||INTERNAL.get()!=null)return;
        INTERNAL.set(true);
        try {
            Scope owner=SCOPES.get(new Key(map));Gate gate=owner==null?null:owner.gate;
            if(gate!=null&&CALLER.getCallerClass()==HashMap.class) {
                Scope entry=scope(node);synchronized(entry) {entry.gate=gate;}
            }
        } finally { INTERNAL.remove(); }
    }
    /** Late registration first drains operations which entered before the map was registered. */
    private static Gate ready(Object object) {
        if(!enabled||!failure.isEmpty()||object==null||object.getClass()!=HashMap.class||((HashMap<?,?>)object).size()>8192)return null;
        Scope owner=SCOPES.get(new Key(object));if(owner==null||!owner.born)return null;
        Gate gate=owner.gate;if(gate!=null&&gate.ready)return gate;
        INTERNAL.set(true);
        try {
            synchronized(owner) {
                gate=owner.gate;if(gate==null)owner.gate=gate=new Gate(object);
            }
            if(!gate.lock.tryLock())return null;
            try {
                if(gate.ready)return gate;
                synchronized(owner) {if(owner.active!=0)return null;}
                boolean pending=false;
                for(Object entry:((HashMap<?,?>)object).entrySet()) {
                    Scope node=scope(entry);
                    synchronized(node) {node.gate=gate;pending|=node.active!=0;}
                }
                if(pending)return null;
                gate.ready=true;return gate;
            } finally { gate.lock.unlock(); }
        } finally { INTERNAL.remove(); }
    }
    public static Object[][] entries(Object map) {
        Gate gate=ready(map);if(gate==null||!gate.lock.tryLock())return null;
        try {
            int size=((HashMap<?,?>)map).size();if(size>8192)return null;
            Object[][] rows=new Object[size][];int index=0;
            for(var entry:((HashMap<?,?>)map).entrySet())rows[index++]=new Object[]{entry.getKey(),entry.getValue()};
            return rows;
        } finally { gate.lock.unlock(); }
    }
    /** null means busy/unobserved, a one-element array contains the actual value, including null. */
    public static Object[] peek(Object map,Object key) {
        Gate gate=ready(map);if(gate==null||!gate.lock.tryLock())return null;
        try { return new Object[]{((HashMap<?,?>)map).containsKey(key),((HashMap<?,?>)map).get(key)}; }
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
}
