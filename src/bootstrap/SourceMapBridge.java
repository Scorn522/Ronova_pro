package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.util.*;

/** Exact source-holder registration for the explicit field and Fastutil writer gates. */
public final class SourceMapBridge {
    private static final Object MONITOR=new Object();
    private static final ThreadLocal<Boolean> INTERNAL=new ThreadLocal<>();
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    // Only authenticated holder/carrier registration allocates these weak identities.
    // Registration can run while a receiver gate is held, so the directory does
    // not invoke a JDK map writer or take another receiver gate.
    private static final Key[] scopes=new Key[65536];
    private static volatile Key[] publishedScopes=scopes;
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static volatile String failure="";
    private static volatile java.lang.invoke.MethodHandle writeGuard;
    private static volatile java.lang.invoke.MethodHandle changeObserver;
    private static final ThreadLocal<Boolean> QUERY=new ThreadLocal<>();
    private static final class Key extends WeakReference<Object> {
        final int hash;
        volatile Scope scope;volatile Key next;
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
    private static final class Scope {
        volatile boolean holder,carrier;
        volatile long revision;
    }
    private SourceMapBridge() { }
    public static int version() { return 2; }
    // Registration bookkeeping ends before any Core observer or application callback.
    static boolean internal() { return INTERNAL.get()!=null; }
    static boolean controlled(Object value) { return value==scopes; }
    static void install(java.lang.invoke.MethodHandle guard,java.lang.invoke.MethodHandle changed) {
        writeGuard=guard.asType(java.lang.invoke.MethodType.methodType(boolean.class,Object.class,Object.class,Object.class));
        changeObserver=changed.asType(java.lang.invoke.MethodType.methodType(void.class,Object.class));
    }
    static void registerHolder(Object holder){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_HOLDER_WRITER_REQUIRED");
        INTERNAL.set(true);
        try{Key key=key(holder,true);synchronized(key){controlScope(key).holder=true;}}
        finally{INTERNAL.remove();}
    }
    static void registerCarrier(Object carrier){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_CARRIER_WRITER_REQUIRED");
        INTERNAL.set(true);
        try{Key key=key(carrier,true);synchronized(key){controlScope(key).carrier=true;}}
        finally{INTERNAL.remove();}
    }
    public static boolean carrierRegistered(Object carrier){
        if(carrier==null||INTERNAL.get()!=null)return false;
        Scope scope=scope(carrier);return scope!=null&&scope.carrier;
    }
    static boolean holderRegistered(Object holder){
        if(holder==null||INTERNAL.get()!=null)return false;
        Scope scope=scope(holder);return scope!=null&&scope.holder;
    }
    // The read branch of key() only compares existing weak identities under
    // MONITOR. It invokes no writer/observer and needs no ThreadLocal mutation.
    static boolean registered(Object value){
        if(value==null||INTERNAL.get()!=null)return false;
        Scope scope=scope(value);return scope!=null&&(scope.holder||scope.carrier);
    }
    /** A completed field/Unsafe scope reports the real holder while its receiver gate is held. */
    static void holderChanged(Object holder) {
        var observer=changeObserver;
        if(observer==null||holder==null||INTERNAL.get()!=null||QUERY.get()!=null)return;
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_HOLDER_WRITE_REQUIRED");
        if(ControlBridge.primitiveSourceIndex(holder)){
            if(ControlBridge.sourceIndexWriting(holder))return;
            Scope scope=scope(holder);if(scope!=null&&scope.carrier)scope.revision++;
        }
        QUERY.set(true);
        try {observer.invokeExact(holder);}
        catch(Throwable unavailable){failure="SOURCE_HOLDER_OBSERVER_UNAVAILABLE";}
        finally {QUERY.remove();}
    }
    static long indexRevision(Object map){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("SOURCE_INDEX_WRITER_REQUIRED");
        Scope scope=scope(map);return scope==null||!scope.carrier?-1:scope.revision;
    }
    public static boolean valueAllowed(Object receiver,Object key,Object value) {
        // The exact directory never re-enters a business backing policy while
        // its monitor is held. All ordinary writers still use their real gate.
        if(INTERNAL.get()!=null)return true;
        var guard=writeGuard;
        if(!BackingBridge.incomingPairAllowed(receiver,key,value))return false;
        if(executionMetadata(receiver)||guard==null||receiver==null||value==null||QUERY.get()!=null)return true;
        boolean registered;
        INTERNAL.set(true);
        try{Scope scope=scope(receiver);registered=scope!=null&&scope.carrier;}
        finally{INTERNAL.remove();}
        if(!registered)return true;
        QUERY.set(true);
        try{return !(boolean)guard.invokeExact(receiver,key,value);}
        catch(Throwable unavailable){failure="SOURCE_WRITE_GUARD_UNAVAILABLE";return false;}
        finally{QUERY.remove();}
    }
    private static boolean executionMetadata(Object receiver){
        return receiver instanceof ArrayList<?> &&receiver.getClass()!=ArrayList.class&&CodeSourceBridge.executionList(receiver)
                ||receiver instanceof HashMap<?,?> &&receiver.getClass()!=HashMap.class&&CodeSourceBridge.executionMap(receiver);
    }
    public static String state() {
        if(!failure.isEmpty())return "SOURCE_FIELDS_UNAVAILABLE:"+failure+":JDK_CONTAINER_HOOKS_REMOVED";
        return (writeGuard!=null&&changeObserver!=null?"SOURCE_FIELDS_INSTALLED":"SOURCE_FIELDS_NOT_INSTALLED")+":JDK_CONTAINER_HOOKS_REMOVED";
    }
    private static void sweep(int budget) {
        indexWriter(CALLER.getCallerClass());
        for(int remaining=budget;remaining>0;remaining--) {
            Key dead=(Key)DEAD.poll();if(dead==null)break;
            int bucket=rootIndex(dead.hash);
            Key previous=null,entry=scopes[bucket];
            while(entry!=null&&entry!=dead){previous=entry;entry=entry.next;}
            if(entry==null)continue;
            if(previous!=null)previous.next=entry.next;
            else {scopes[bucket]=entry.next;publishedScopes=scopes;}
            // A reader may still be at this dead node. Retain its published
            // successor; unlinking a dead weak identity grants no permission.
        }
    }
    private static void indexWriter(Class<?> caller) {
        if(caller!=SourceMapBridge.class||!Thread.holdsLock(MONITOR))throw new SecurityException("SOURCE_SCOPE_INDEX_WRITER_REQUIRED");
    }
    private static int rootIndex(int hash) { return (hash^(hash>>>16))&(scopes.length-1); }
    private static Scope scope(Object receiver) {
        Key key=key(receiver,false);return key==null?null:key.scope;
    }
    private static Scope controlScope(Key key){
        Scope scope=key.scope;if(scope==null)key.scope=scope=new Scope();return scope;
    }
    private static Key key(Object receiver,boolean create) {
        int hash=System.identityHashCode(receiver),bucket=rootIndex(hash);
        if(!create){
            // Read the actual current registration, never a cached verdict.
            // Roots/successors publish with release; only dead keys are unlinked.
            Key[] published=publishedScopes;
            for(Key entry=published[bucket];entry!=null;entry=entry.next)
                if(entry.hash==hash&&entry.get()==receiver)return entry;
            return null;
        }
        synchronized(MONITOR) {
            for(Key entry=scopes[bucket];entry!=null;entry=entry.next)
                if(entry.hash==hash&&entry.get()==receiver)return entry;
            if(!create)return null;
            indexWriter(CALLER.getCallerClass());Key added=new Key(receiver);
            added.next=scopes[bucket];scopes[bucket]=added;publishedScopes=scopes;sweep(2);return added;
        }
    }
    // Compatibility with existing Core/Client lookups. Removed JDK writer hooks
    // cannot provide a consistent snapshot or authorize an action to run.
    public static Object[][] entries(Object map) { return null; }
    public static Object[] peek(Object map,Object key) { return null; }
    public static long revision(Object map) { return -1; }
    public static boolean withMap(Object map,Runnable action) { return false; }
    public static boolean withList(Object list,Runnable action) { return false; }
    public static boolean withListRead(Object list,Runnable action) { return false; }
    public static long listRevision(Object list) { return -1; }
}
