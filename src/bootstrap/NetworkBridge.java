package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

/** Real channel registrations, codec objects and transport queue shares. */
public final class NetworkBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,Module[]> CHANNELS=new HashMap<>(),HANDLERS=new HashMap<>(),PACKETS=new HashMap<>(),BUFFERS=new HashMap<>();
    private static final Map<Key,Ref<Object>> WRAPPERS=new HashMap<>(),HOLDERS=new HashMap<>();
    private static final Map<Key,QueueRef> QUEUES=new HashMap<>();
    private static final Map<Key,Write> WRITES=new HashMap<>();
    private static final List<Role> ROLES=new ArrayList<>();
    private static final List<Registry> REGISTRIES=new ArrayList<>();
    private static final Map<Key,Boolean> CONTROLS=new HashMap<>();
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final Set<Scope> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Scope{
        final Scope parent;final Module[] modules;final Class<?> declaring;final String method;final Thread thread;
        boolean ended;
        Scope(Scope parent,Module[] modules,Class<?> declaring,String method){this.parent=parent;this.modules=modules;this.declaring=declaring;this.method=method;thread=Thread.currentThread();}
    }
    private static long dropped,unobservedQueues;
    private record Role(String name,Ref<ClassLoader> loader,boolean bootstrap,Ref<Module> module){}
    private record Registry(Class<?> type,Ref<Map<?,?>> instances){}
    private record QueueRef(Class<?> type,String field,Ref<Queue<?>> queue){}
    private record Write(Module[] modules,Ref<Object> context,Ref<Object> message,Ref<Object> promise){}
    private static final class Ref<T> extends WeakReference<T>{
        Ref(T value){super(value);}
        public void clear(){if(writer())super.clear();}
        public boolean enqueue(){return writer()&&super.enqueue();}
    }
    private static final class Key extends WeakReference<Object>{
        final int hash;
        Key(Object value,boolean stored){super(value,stored?DEAD:null);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object value){return this==value||value instanceof Key key&&get()!=null&&get()==key.get();}
        public void clear(){if(writer())super.clear();}
        public boolean enqueue(){return writer()&&super.enqueue();}
    }
    private NetworkBridge(){}
    private static Class<?> caller(){return WALKER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type.getNestHost()!=NetworkBridge.class).findFirst().orElse(null));}
    private static boolean writer(){Class<?> type=caller();return type!=null&&(ControlBridge.owns(type)||type.getNestHost()==TaskBridge.class);}
    private static void agent(){if(!ControlBridge.owns(caller()))throw new SecurityException("NETWORK_AGENT_REQUIRED");}
    public static void expect(String name,ClassLoader loader,Module module){
        agent();synchronized(CHANNELS){
            for(Role role:ROLES)if(role.name().equals(name)&&role.bootstrap()==(loader==null)&&role.loader().get()==loader){if(role.module().get()!=module)throw new IllegalStateException("NETWORK_ROLE_MODULE_CHANGED");return;}
            ROLES.add(new Role(name,new Ref<>(loader),loader==null,new Ref<>(module)));
        }
    }
    private static StackWalker.StackFrame boundary(){
        var frame=WALKER.walk(frames->frames.filter(value->value.getDeclaringClass().getNestHost()!=NetworkBridge.class).findFirst().orElse(null));
        if(frame!=null)synchronized(CHANNELS){for(Role role:ROLES)if(role.name().equals(frame.getDeclaringClass().getName())&&role.bootstrap()==(frame.getDeclaringClass().getClassLoader()==null)
                &&role.loader().get()==frame.getDeclaringClass().getClassLoader()&&role.module().get()==DefinitionBridge.module(frame.getDeclaringClass()))return frame;}
        throw new SecurityException("ACTUAL_NETWORK_BOUNDARY_REQUIRED");
    }
    private static Object field(Object object,Class<?> declaring,String name){
        try{Field field=declaring.getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("NETWORK_FIELD_UNAVAILABLE:"+name,unavailable);}
    }
    private static Object field(Object object,String name){
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{return field(object,type,name);}catch(IllegalStateException absent){if(!(absent.getCause() instanceof NoSuchFieldException))throw absent;}
        throw new IllegalStateException("NETWORK_FIELD_UNAVAILABLE:"+name);
    }
    private static Module[] union(Module[]... groups){
        Set<Module> result=null;for(Module[] group:groups)if(group!=null)for(Module module:group)if(module!=null){
            if(result==null)result=Collections.newSetFromMap(new IdentityHashMap<>());result.add(module);
        }
        return result==null?new Module[0]:result.toArray(Module[]::new);
    }
    private static Module[] origins(Object object){if(object==null)return new Module[0];return Arrays.stream(TaskBridge.objectSources(object)).filter(Objects::nonNull).filter(TaskBridge::producer).distinct().toArray(Module[]::new);}
    private static boolean stopped(Module[] modules){if(modules!=null)for(Module module:modules)if(TaskBridge.modStopped(module))return true;return false;}
    private static void record(Map<Key,Module[]> map,Object object,Module[] modules){if(object==null)return;if(modules.length==0){map.remove(new Key(object,false));return;}Module[] copy=modules.clone();map.put(new Key(object,true),copy);CONTROLS.put(new Key(copy,true),Boolean.TRUE);}
    private static Module[] channel(Object object){
        Module[] direct=CHANNELS.get(new Key(object,false));if(direct!=null)return direct;Ref<Object> parent=WRAPPERS.get(new Key(object,false));return parent==null?new Module[0]:CHANNELS.getOrDefault(new Key(parent.get(),false),new Module[0]);
    }
    public static void channelCreated(Object channel,Object address){
        var frame=boundary();if(!frame.getMethodName().equals("<init>"))throw new SecurityException("ACTUAL_CHANNEL_CREATION_REQUIRED");
        Module[] owners=TaskBridge.invokingSources();synchronized(CHANNELS){reap();record(CHANNELS,channel,owners);}
    }
    public static void routePublished(Object instance){
        var frame=boundary();if(!frame.getMethodName().equals("createInstance"))throw new SecurityException("ACTUAL_CHANNEL_REGISTRATION_REQUIRED");
        @SuppressWarnings("unchecked") Map<?,?> registry=(Map<?,?>)field(null,frame.getDeclaringClass(),"instances");
        synchronized(CHANNELS){for(Registry prior:REGISTRIES)if(prior.type()==frame.getDeclaringClass()&&prior.instances().get()==registry)return;REGISTRIES.add(new Registry(frame.getDeclaringClass(),new Ref<>(registry)));}
    }
    public static void wrapperCreated(Object wrapper,Object instance){
        if(!boundary().getMethodName().equals("<init>"))throw new SecurityException("ACTUAL_CHANNEL_WRAPPER_REQUIRED");
        synchronized(CHANNELS){reap();WRAPPERS.put(new Key(wrapper,true),new Ref<>(instance));}
    }
    public static void handlerCreated(Object handler,Object codec,Object messageClass,Object encoder,Object decoder,Object consumer){
        if(!boundary().getMethodName().equals("<init>"))throw new SecurityException("ACTUAL_MESSAGE_REGISTRATION_REQUIRED");
        Module[] owners=union(TaskBridge.invokingSources(),origins(messageClass),origins(encoder),origins(decoder),origins(consumer));
        synchronized(CHANNELS){reap();record(HANDLERS,handler,owners);WRAPPERS.put(new Key(handler,true),new Ref<>(codec));}
    }
    private static Module[] handler(Object object){return HANDLERS.getOrDefault(new Key(object,false),new Module[0]);}
    private static Object messageHandler(Object wrapper,Object message){
        if(message==null)return null;Object codec=field(wrapper,"indexedCodec"),types=field(codec,"types");
        return types instanceof Map<?,?> map?map.get(message.getClass()):null;
    }
    private static Module[] message(Object wrapper,Object handler,Module[] origins){return union(channel(wrapper),handler(handler),origins);}
    public static boolean channelAllowed(Object instance){boundary();synchronized(CHANNELS){return !stopped(channel(instance));}}
    public static boolean messageAllowed(Object wrapper,Object message){boundary();Module[] origins=origins(message);Object handler=messageHandler(wrapper,message);boolean stopped;synchronized(CHANNELS){stopped=stopped(message(wrapper,handler,origins));}return !stopped&&!TaskBridge.stoppedEffects();}
    public static boolean codecAllowed(Object handler){boundary();boolean stopped;synchronized(CHANNELS){Ref<Object> codec=WRAPPERS.get(new Key(handler,false));stopped=stopped(handler(handler))||stopped(codec==null?null:channel(codec.get()));}return !stopped&&!TaskBridge.stoppedEffects();}
    public static Object enter(Object owner){
        var frame=boundary();Module[] modules;synchronized(CHANNELS){Ref<Object> parent=WRAPPERS.get(new Key(owner,false));modules=union(channel(owner),handler(owner),parent==null?null:channel(parent.get()));}
        return scope(frame,modules);
    }
    private static Scope scope(StackWalker.StackFrame frame,Module[] modules){Scope scope=new Scope(CURRENT.get(),modules,frame.getDeclaringClass(),frame.getMethodName());synchronized(CHANNELS){ACTIVE.add(scope);CONTROLS.put(new Key(modules,true),Boolean.TRUE);}CURRENT.set(scope);return scope;}
    private static Module[] packetSources(Object message){Module[] origins=origins(message),sources;synchronized(CHANNELS){reap();sources=union(PACKETS.get(new Key(message,false)),BUFFERS.get(new Key(message,false)),origins);}return union(sources,routed(message));}
    public static Object encoderEnter(Object message){return scope(boundary(),packetSources(message));}
    public static void encodedBuffer(Object buffer){boundary();synchronized(CHANNELS){record(BUFFERS,buffer,currentSources());}}
    public static void writeCreated(Object task,Object context,Object message,Object promise){
        if(!boundary().getMethodName().equals("init"))throw new SecurityException("ACTUAL_NETTY_WRITE_ITEM_REQUIRED");
        Module[] modules=union(currentSources(),packetSources(message));synchronized(CHANNELS){reap();WRITES.put(new Key(task,true),new Write(modules,new Ref<>(context),new Ref<>(message),new Ref<>(promise)));CONTROLS.put(new Key(modules,true),Boolean.TRUE);}
    }
    public static Object writeEnter(Object task){
        var frame=boundary();Write write;synchronized(CHANNELS){write=WRITES.get(new Key(task,false));}
        if(write==null)return scope(frame,new Module[0]);
        if(field(task,"ctx")!=write.context().get()||field(task,"msg")!=write.message().get()||field(task,"promise")!=write.promise().get()){
            synchronized(CHANNELS){unobservedQueues++;}return scope(frame,new Module[0]);
        }return scope(frame,write.modules());
    }
    public static void writeRetired(Object task){if(!boundary().getMethodName().equals("recycle"))throw new SecurityException("ACTUAL_NETTY_WRITE_RETIREMENT_REQUIRED");synchronized(CHANNELS){WRITES.remove(new Key(task,false));}}
    public static void exit(Object token){
        var frame=boundary();if(!(token instanceof Scope scope)||scope.thread!=Thread.currentThread()||scope.declaring!=frame.getDeclaringClass()||!scope.method.equals(frame.getMethodName()))throw new SecurityException("ACTUAL_NETWORK_DISPATCH_SCOPE_REQUIRED");
        if(scope.ended)return;if(CURRENT.get()!=scope)throw new IllegalStateException("NETWORK_DISPATCH_SCOPE_ORDER");scope.ended=true;synchronized(CHANNELS){ACTIVE.remove(scope);}if(scope.parent==null)CURRENT.remove();else CURRENT.set(scope.parent);
    }
    static Module[] currentSources(){
        if(CURRENT==null)return new Module[0];Set<Module> sources=null;
        for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)for(Module module:scope.modules)if(module!=null){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(module);
        }
        return sources==null?new Module[0]:sources.toArray(new Module[0]);
    }
    static boolean stoppedSource(){if(CURRENT==null)return false;for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(stopped(scope.modules))return true;return false;}
    public static void skippedDecode(Object context){
        if(!boundary().getMethodName().equals("tryDecode"))throw new SecurityException("ACTUAL_MESSAGE_DISPATCH_REQUIRED");
        Object actual=context instanceof java.util.function.Supplier<?> supplier?supplier.get():null;
        if(actual!=null)try{actual.getClass().getMethod("setPacketHandled",boolean.class).invoke(actual,true);}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("NETWORK_HANDLED_STATE_UNAVAILABLE",unavailable);}
    }
    public static void packetProduced(Object wrapper,Object message,Object packet){
        boundary();Module[] origins=origins(message);Object handler=messageHandler(wrapper,message);synchronized(CHANNELS){reap();record(PACKETS,packet,message(wrapper,handler,origins));}
    }
    public static void bufferProduced(Object wrapper,Object message,Object pair){
        boundary();if(pair==null)return;
        try{Object buffer=pair.getClass().getMethod("getLeft").invoke(pair);Module[] origins=origins(message);Object handler=messageHandler(wrapper,message);synchronized(CHANNELS){reap();record(BUFFERS,buffer,message(wrapper,handler,origins));}}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("NETWORK_ENCODED_BUFFER_UNAVAILABLE",unavailable);}
    }
    private static Module[] routed(Object packet){
        if(packet==null)return new Module[0];Class<?> custom=null;
        synchronized(CHANNELS){for(Registry registry:REGISTRIES)try{
            Class<?> candidate=Class.forName("net.minecraftforge.network.ICustomPacket",false,registry.type().getClassLoader());
            if(candidate.getModule()==registry.type().getModule()&&candidate.isInstance(packet)){custom=candidate;break;}
        }catch(ClassNotFoundException absent){/* This registry has no custom packet contract. */}}
        if(custom==null)return new Module[0];
        try{
            Object address=custom.getMethod("getName").invoke(packet),data=custom.getMethod("getInternalData").invoke(packet);Module[] sources;
            synchronized(CHANNELS){sources=BUFFERS.getOrDefault(new Key(data,false),new Module[0]);
                for(Registry registry:REGISTRIES)if(registry.type().getModule()==custom.getModule()&&registry.type().getClassLoader()==custom.getClassLoader()){
                    Object current=field(null,registry.type(),"instances");if(current instanceof Map<?,?> map){Object instance=map.get(address);sources=union(sources,channel(instance));}
                }
            }return sources;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_CUSTOM_PACKET_ROUTE_UNAVAILABLE",unavailable);}
    }
    private static boolean packet(Object message){
        return !stopped(packetSources(message));
    }
    public static boolean packetAllowed(Object message){boundary();return packet(message)&&!TaskBridge.stoppedEffects();}
    public static boolean outboundAllowed(Object message){boundary();boolean allowed=packet(message)&&!TaskBridge.stoppedEffects();if(!allowed)synchronized(CHANNELS){dropped++;}return allowed;}
    public static void packetEncoded(Object packet,Object buffer){
        boundary();Module[] origins=origins(packet),owners;synchronized(CHANNELS){owners=union(PACKETS.get(new Key(packet,false)),origins);}
        owners=union(owners,routed(packet));synchronized(CHANNELS){record(BUFFERS,buffer,owners);}
    }
    public static void holderCreated(Object holder,Object packet){
        if(!boundary().getMethodName().equals("<init>"))throw new SecurityException("ACTUAL_PACKET_QUEUE_ITEM_REQUIRED");
        synchronized(CHANNELS){reap();HOLDERS.put(new Key(holder,true),new Ref<>(packet));}
    }
    public static void connectionCreated(Object connection,Object queue,String name){
        var frame=boundary();if(!frame.getMethodName().equals("<init>")||!(queue instanceof Queue<?>))throw new SecurityException("ACTUAL_PACKET_QUEUE_REQUIRED");
        synchronized(CHANNELS){reap();QUEUES.put(new Key(connection,true),new QueueRef(frame.getDeclaringClass(),name,new Ref<>((Queue<?>)queue)));}
    }
    public static String retire(Module[] selected){
        agent();List<QueueRef> queues;synchronized(CHANNELS){reap();queues=List.copyOf(QUEUES.values());}
        long removed=0,pending=0;for(QueueRef association:queues){Queue<?> queue=association.queue().get();if(queue==null)continue;
            if(queue.getClass()!=java.util.concurrent.ConcurrentLinkedQueue.class){pending++;continue;}
            var cursor=queue.iterator();while(cursor.hasNext()){
                Object holder=cursor.next(),message;synchronized(CHANNELS){Ref<Object> actual=HOLDERS.get(new Key(holder,false));message=actual==null?null:actual.get();}
                if(message==null){pending++;continue;}if(!packet(message)){cursor.remove();removed++;}
            }
        }synchronized(CHANNELS){dropped+=removed;unobservedQueues=pending;return "DROPPED="+dropped+";QUEUED_UNOBSERVED="+unobservedQueues;}
    }
    public static String state(){synchronized(CHANNELS){return "NETWORK_DROPPED="+dropped+";NETWORK_QUEUED_UNOBSERVED="+unobservedQueues;}}
    public static String retirementGap(Module module){agent();synchronized(CHANNELS){
        reap();long active=ACTIVE.stream().filter(scope->Arrays.stream(scope.modules).anyMatch(source->source==module)).count();
        long writes=WRITES.values().stream().filter(write->Arrays.stream(write.modules()).anyMatch(source->source==module)).count();
        return active!=0||writes!=0||unobservedQueues!=0?"NETWORK_RETIREMENT_PENDING:active="+active+":writes="+writes+":queueUnobserved="+unobservedQueues:"";
    }}
    static boolean controlled(Object value){if(CONTROLS==null)return false;synchronized(CHANNELS){return CONTROLS.containsKey(new Key(value,false));}}
    public static Object[] controlObjects(){agent();return new Object[]{CHANNELS,HANDLERS,PACKETS,BUFFERS,WRAPPERS,HOLDERS,QUEUES,WRITES,ROLES,REGISTRIES,CONTROLS,DEAD,CURRENT,ACTIVE};}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){CHANNELS.remove(key);HANDLERS.remove(key);PACKETS.remove(key);BUFFERS.remove(key);WRAPPERS.remove(key);HOLDERS.remove(key);QUEUES.remove(key);WRITES.remove(key);CONTROLS.remove(key);}}
}
