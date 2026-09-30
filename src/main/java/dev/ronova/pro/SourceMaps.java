package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** The two exact memory-map backends. An unavailable HashMap observer never becomes an ordinary overwrite. */
final class SourceMaps {
    private record Owner(java.lang.ref.WeakReference<RecoverySources.Source> source,Field field,
                         java.lang.ref.WeakReference<Object> map) { }
    private static final Map<Object,List<Owner>> OWNERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static final Map<Object,List<Owner>> HOLDERS=Collections.synchronizedMap(new WeakIdentityMap<>());
    private static volatile Method entries,peek,withMap,revision,state,bridgeState;
    private SourceMaps() { }
    static boolean supported(Object map) { return map!=null&&(map.getClass()==HashMap.class||map.getClass()==ConcurrentHashMap.class); }
    static void observe(Object map,RecoverySources.Source source,Field field) {
        if(!supported(map))return;
        if(map.getClass()==ConcurrentHashMap.class)try {
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null).getMethod("observeConcurrent",Object.class).invoke(null,map);
        } catch(ReflectiveOperationException unavailable) { /* Disposition remains available; publication coverage is reported below. */ }
        synchronized(OWNERS) {
            var previous=OWNERS.getOrDefault(map,List.of());var next=new ArrayList<Owner>();
            for(Owner owner:previous) {
                var prior=owner.source.get();if(prior==source&&owner.field.equals(field))return;
                if(prior!=null)next.add(owner);
            }
            Owner registered=new Owner(new java.lang.ref.WeakReference<>(source),field,new java.lang.ref.WeakReference<>(map));
            next.add(registered);OWNERS.put(map,List.copyOf(next));
            var holders=new ArrayList<Owner>();for(Owner owner:HOLDERS.getOrDefault(source.holder,List.of()))if(owner.source.get()!=null)holders.add(owner);
            holders.add(registered);HOLDERS.put(source.holder,List.copyOf(holders));
        }
    }
    static boolean currentSource(Object map,RecoverySources sources,UUID subject) {
        List<Owner> owners=OWNERS.get(map);if(owners==null)return false;
        for(Owner owner:owners) {
            var source=owner.source.get();if(source==null||source.owner!=sources||!source.subjects.contains(subject)||!sources.liveRegistration(source))continue;
            try {if(owner.field.get(source.holder instanceof Class<?>?null:source.holder)==map)return true;}
            catch(IllegalAccessException unavailable) { }
        }
        return false;
    }
    static boolean staticHolderDenied(Class<?> holder,String name,Object next) {
        return holderDenied(holder,null,name,null,next,null,null,true);
    }
    static boolean staticHolderOffsetDenied(Class<?> holder,long offset,Object next,Object unsafe,Method offsetMethod) {
        return holderDenied(holder,null,null,offset,next,unsafe,offsetMethod,true);
    }
    static boolean instanceHolderDenied(Object holder,Class<?> declaring,String name,Object next) {
        return holderDenied(holder,declaring,name,null,next,null,null,false);
    }
    static boolean instanceHolderOffsetDenied(Object holder,long offset,Object next,Object unsafe,Method offsetMethod) {
        return holderDenied(holder,null,null,offset,next,unsafe,offsetMethod,false);
    }
    static boolean instanceHolderRangeDenied(Object holder,long offset,long length,Object unsafe,Method offsetMethod) {
        if(length<=0)return false;
        final long end;try {end=Math.addExact(offset,length);}catch(ArithmeticException overflow){return true;}
        for(Owner owner:HOLDERS.getOrDefault(holder,List.of())) {
            var source=owner.source.get();Field field=owner.field;
            if(source==null||source.holder!=holder||Modifier.isStatic(field.getModifiers()))continue;
            long at;try {at=(Long)offsetMethod.invoke(unsafe,field);}catch(ReflectiveOperationException unavailable){source.gap="SOURCE_FIELD_OFFSET_UNAVAILABLE";return true;}
            if(offset<at+8&&end>at&&holderDenied(holder,field.getDeclaringClass(),field.getName(),null,null,null,null,false))return true;
        }
        return false;
    }
    private static boolean holderDenied(Object holder,Class<?> declaring,String name,Long offset,Object next,Object unsafe,Method offsetMethod,boolean statik) {
        for(Owner owner:HOLDERS.getOrDefault(holder,List.of())) {
            var source=owner.source.get();Field field=owner.field;
            if(source==null||source.holder!=holder||Modifier.isStatic(field.getModifiers())!=statik)continue;
            if(statik&&field.getDeclaringClass()!=holder)continue;
            if(declaring!=null&&field.getDeclaringClass()!=declaring)continue;
            if(name!=null&&!field.getName().equals(name))continue;
            if(offset!=null)try {if((Long)offsetMethod.invoke(unsafe,field)!=offset.longValue())continue;}
                catch(ReflectiveOperationException unavailable){source.gap="SOURCE_STATIC_OFFSET_UNAVAILABLE";return true;}
            boolean terminal=false;ProRuntime runtime=source.owner.runtimeOwner();
            for(UUID subject:source.subjects)if(runtime.terminalSource(subject)) {terminal=true;break;}
            if(!terminal)continue;
            Object map=owner.map.get();
            try {if(map!=null&&field.get(statik?null:holder)==map&&next!=map)return true;}
            catch(IllegalAccessException unavailable){source.gap="SOURCE_STATIC_HOLDER_READ_UNAVAILABLE";return true;}
        }
        return false;
    }
    static void changed(Object map) {
        List<Owner> owners=OWNERS.get(map);if(owners==null)return;
        for(Owner owner:owners) {
            var source=owner.source.get();
            if(source!=null&&source.owner.liveRegistration(source))source.owner.mapChanged(source);
        }
    }
    static String observerGap(RecoverySources sources,UUID subject) {
        boolean needed=false;
        synchronized(OWNERS) { for(var row:OWNERS.values())for(Owner owner:row) {
            var source=owner.source.get();if(source!=null&&source.owner==sources&&source.subjects.contains(subject))needed=true;
        } }
        return !needed||available()?"":"SOURCE_WRITE_OBSERVER_UNAVAILABLE";
    }
    static void close(RecoverySources sources) {
        synchronized(OWNERS) {
            var iterator=OWNERS.entrySet().iterator();
            while(iterator.hasNext()) {
                var row=iterator.next();var keep=row.getValue().stream().filter(owner->{var source=owner.source.get();return source!=null&&source.owner!=sources;}).toList();
                if(keep.isEmpty())iterator.remove();else row.setValue(keep);
            }
        }
        synchronized(HOLDERS) {
            var iterator=HOLDERS.entrySet().iterator();
            while(iterator.hasNext()) {
                var row=iterator.next();var keep=row.getValue().stream().filter(owner->{var source=owner.source.get();return source!=null&&source.owner!=sources;}).toList();
                if(keep.isEmpty())iterator.remove();else row.setValue(keep);
            }
        }
    }
    static boolean denies(Object map,Object key,Object value) {
        List<Owner> owners=OWNERS.get(map);if(owners==null)return false;
        for(Owner owner:owners) {
            var source=owner.source.get();
            if(source==null||!source.owner.liveRegistration(source))continue;
            try {
                if(owner.field.get(source.holder instanceof Class<?>?null:source.holder)!=map)continue;
                ProRuntime runtime=source.owner.runtimeOwner();Object target=RecoveryReferences.ownedTarget(value,runtime,true);
                UUID subject=target instanceof net.minecraft.world.entity.Entity?runtime.recoveryTasks.subjectOf(target):
                    target instanceof net.minecraft.nbt.CompoundTag tag?RecoverySources.subject(source.owner,tag):null;
                if(subject!=null&&source.subjects.contains(subject)&&runtime.terminalSource(subject)) {
                    if(!(target instanceof net.minecraft.nbt.CompoundTag tag))return true;
                    if(!RecoverySources.tryRecordGraph())return true;
                    try { return RecoverySources.recordGraphGap(source.owner,tag,subject).isEmpty(); }
                    finally { RecoverySources.releaseRecordGraph(); }
                }
            } catch(IllegalAccessException unavailable) { /* This owner no longer supplies authority. */ }
        }
        return false;
    }
    private static synchronized boolean available() {
        try {
            if(entries==null) {
                Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",false,null);
                if(!Integer.valueOf(1).equals(bridge.getMethod("version").invoke(null)))return false;
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                Method ready=agent.getMethod("sourceMapState"),status=bridge.getMethod("state");
                Method read=bridge.getMethod("entries",Object.class),get=bridge.getMethod("peek",Object.class,Object.class),
                        run=bridge.getMethod("withMap",Object.class,Runnable.class),rev=bridge.getMethod("revision",Object.class);
                state=ready;bridgeState=status;peek=get;withMap=run;revision=rev;entries=read;
            }
            return "INSTALLED".equals(state.invoke(null))&&"HASHMAP_SCOPES_INSTALLED".equals(bridgeState.invoke(null));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError missing) { return false; }
    }
    static String unavailable(Object map) {
        return map instanceof Map<?,?> values&&values.size()>8192?"SOURCE_MAP_SNAPSHOT_CAPACITY":"SOURCE_MAP_BUSY_OR_UNOBSERVED";
    }
    static List<Map.Entry<Object,Object>> entries(Object map) {
        if(!supported(map))return null;
        if(map.getClass()==ConcurrentHashMap.class) {
            var result=new ArrayList<Map.Entry<Object,Object>>();
            var cursor=((Map<?,?>)map).entrySet().iterator();
            while(cursor.hasNext()) { if(result.size()>=8192)return null;var row=cursor.next();result.add(new AbstractMap.SimpleImmutableEntry<>(row.getKey(),row.getValue())); }
            return result;
        }
        if(!available())return null;
        try {
            Object[][] rows=(Object[][])entries.invoke(null,map);if(rows==null)return null;
            var result=new ArrayList<Map.Entry<Object,Object>>(rows.length);
            for(Object[] row:rows)result.add(new AbstractMap.SimpleImmutableEntry<>(row[0],row[1]));return result;
        } catch(ReflectiveOperationException failure) { return null; }
    }
    static Object[] peek(Object map,Object key) {
        if(!supported(map))return null;
        if(map.getClass()==ConcurrentHashMap.class)return new Object[]{((Map<?,?>)map).containsKey(key),((Map<?,?>)map).get(key)};
        if(!available())return null;
        try { return (Object[])peek.invoke(null,map,key); }catch(ReflectiveOperationException failure) { return null; }
    }
    static long revision(Object map) {
        if(map.getClass()!=HashMap.class)return 0;
        if(!available())return -1;
        try { return (long)revision.invoke(null,map); }catch(ReflectiveOperationException failure) { return -1; }
    }
    @FunctionalInterface interface Action { void run() throws ReflectiveOperationException; }
    static boolean with(Object map,Action action)throws ReflectiveOperationException {
        if(map.getClass()==ConcurrentHashMap.class) { action.run();return true; }
        if(!available())return false;
        ReflectiveOperationException[] thrown={null};
        Runnable run=()->{try { action.run(); }catch(ReflectiveOperationException failure) { thrown[0]=failure; }};
        try {
            boolean ran=(boolean)withMap.invoke(null,map,run);if(thrown[0]!=null)throw thrown[0];return ran;
        } catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof RuntimeException error)throw error;
            if(failure.getCause() instanceof Error error)throw error;
            throw failure;
        }
    }
}
