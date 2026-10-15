package dev.ronova.pro.bootstrap;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;

/** Real channel registrations, codec objects and transport queue shares. */
public final class NetworkBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,Module[]> CHANNELS=new HashMap<>(),HANDLERS=new HashMap<>(),PACKETS=new HashMap<>(),BUFFERS=new HashMap<>();
    private static final Map<Key,Ref<Object>> WRAPPERS=new HashMap<>();
    private static final Map<Key,PacketEntry> HOLDERS=new HashMap<>();
    private static final Map<Key,QueueRef> QUEUES=new HashMap<>();
    private static final Map<Key,Write> WRITES=new HashMap<>();
    private static final List<Role> ROLES=new ArrayList<>();
    private static final List<Registry> REGISTRIES=new ArrayList<>();
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Cleanup> CLEANING=new ThreadLocal<>();
    private static final Set<Scope> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Scope{
        final Scope parent;Module[] modules;final Class<?> declaring;final String method;final Thread thread;
        boolean ended;QueueRef queue;Object connection;PacketEntry entry;Object writeTask;Write write;int boundaryDepth;Object retirementGraph;
        Scope(Scope parent,Module[] modules,Class<?> declaring,String method){this.parent=parent;this.modules=modules;this.declaring=declaring;this.method=method;thread=Thread.currentThread();}
    }
    private static long dropped,unobservedQueues;
    private record Role(String name,Ref<ClassLoader> loader,boolean bootstrap,Ref<Module> module){}
    private record Registry(Class<?> type,Ref<Map<?,?>> instances){}
    private static final String CONNECTION="net.minecraft.network.Connection",HOLDER=CONNECTION+"$PacketHolder";
    private static final String SEND="(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V";
    private record QueueRef(Class<?> type,Ref<Object> connection,String field,Ref<Queue<?>> queue,
                            String submit,String drain,String send,String packetField,String listenerField){}
    /** One actual queue publication, never a source union on the shared packet. */
    private static final class PacketEntry{
        final Ref<Object> identity,packet,listener;final Class<?> type;final Field packetField,listenerField;
        Module[] modules;QueueRef queue;boolean published,claimed,closed,deferred;
        PacketEntry(Object value,Class<?> type,Field packetField,Field listenerField,Object packet,Object listener,Module[] modules){
            Class<?> caller=WALKER.getCallerClass();
            if(caller!=NetworkBridge.class&&caller!=Deferred.class)throw new SecurityException("ACTUAL_PACKET_ENTRY_FACTORY_REQUIRED");
            identity=new Ref<>(value);this.type=type;this.packetField=packetField;this.listenerField=listenerField;
            this.packet=new Ref<>(packet);this.listener=new Ref<>(listener);this.modules=modules;
            protect(this,identity,this.packet,this.listener,(Object)modules);
        }
    }
    /** A real Connection EventLoop submission; this bootstrap body always owns its finally. */
    private static final class Deferred implements Runnable{
        final PacketEntry entry;final Ref<Object> executor;private Runnable action;
        Deferred(QueueRef queue,Object executor,Runnable action,Object packet,Object listener,Module[] modules){
            if(WALKER.getCallerClass()!=NetworkBridge.class)throw new SecurityException("ACTUAL_PACKET_DEFERRED_FACTORY_REQUIRED");
            this.action=action;this.executor=new Ref<>(executor);
            entry=new PacketEntry(this,Deferred.class,null,null,packet,listener,modules);entry.queue=queue;entry.deferred=true;entry.published=true;
            protect(this,this.executor);
        }
        public void run(){
            Scope scope;Runnable call;
            synchronized(CHANNELS){
                if(entry.closed||entry.claimed||entry.identity.get()!=this||HOLDERS.get(new Key(this,false))!=entry||action==null||executor.get()==null)
                    throw new SecurityException("ACTUAL_PACKET_EVENTLOOP_ENTRY_REQUIRED");
                scope=scope(Deferred.class,"run",entry.modules);scope.entry=entry;scope.queue=entry.queue;scope.connection=entry.queue.connection().get();
                entry.claimed=true;HOLDERS.remove(new Key(this,false));call=action;
            }
            try{
                Module[] actual=union(packetSources(entry.packet.get()),origins(entry.listener.get()));
                synchronized(CHANNELS){entry.modules=union(entry.modules,actual);scope.modules=entry.modules;protect((Object)scope.modules);}
                if(!stopped(scope.modules)&&!ControlBridge.stoppedEffects())call.run();else synchronized(CHANNELS){dropped++;}
            }
            finally{try{finishPacket(scope);}finally{action=null;}}
        }
    }
    private record Write(Module[] modules,Ref<Object> context,Ref<Object> message,Ref<Object> promise){}
    private static final class Cleanup{
        final Cleanup parent;final Thread thread=Thread.currentThread();final Object context,message,promise;
        final Class<?> declaring;final String method;final int boundaryDepth;final OutboundRejected failure=new OutboundRejected();
        int phase=-1;boolean closed;Object graph;
        Cleanup(Object context,Object message,Object promise,StackWalker.StackFrame frame,int boundaryDepth){
            parent=CLEANING.get();this.context=context;this.message=message;this.promise=promise;
            declaring=frame.getDeclaringClass();method=frame.getMethodName();this.boundaryDepth=boundaryDepth;
        }
    }
    private static final class OutboundRejected extends java.util.concurrent.CancellationException{
        private static final long serialVersionUID=1L;
        private OutboundRejected(){super("NETWORK_OUTBOUND_REJECTED");}
    }
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
    private static void protect(Object... objects){
        Class<?> caller=WALKER.getCallerClass();
        if(caller!=NetworkBridge.class&&caller!=PacketEntry.class&&caller!=Deferred.class)throw new SecurityException("ACTUAL_PACKET_METADATA_FACTORY_REQUIRED");
        for(Object object:objects)CodeSourceBridge.contributionControl(object);
    }
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
        Set<Module> result=null;for(Module[] group:groups)if(group!=null)for(Module module:group){
            if(result==null)result=Collections.newSetFromMap(new IdentityHashMap<>());result.add(module);
        }
        return result==null?new Module[0]:result.toArray(Module[]::new);
    }
    private static boolean unknown(Module[] modules){for(Module module:modules)if(module==null)return true;return false;}
    private static Module[] origins(Object object){
        if(object==null)return new Module[0];Module[] modules=Arrays.stream(ControlBridge.objectSources(object)).filter(module->module==null||ControlBridge.producer(module)).distinct().toArray(Module[]::new);
        return ControlBridge.objectSourceUnknown(object)?union(modules,new Module[]{null}):modules;
    }
    private static Module[] submission(Object packet,Object listener){
        Module[] modules=union(ControlBridge.invokingSources(),currentSources(),packetSources(packet),origins(listener));
        return ControlBridge.sourceUnknown()?union(modules,new Module[]{null}):modules;
    }
    private static boolean stopped(Module[] modules){if(modules!=null)for(Module module:modules)if(ControlBridge.modStopped(module))return true;return false;}
    private static void record(Map<Key,Module[]> map,Object object,Module[] modules){if(object==null)return;if(modules.length==0){map.remove(new Key(object,false));return;}Module[] copy=modules.clone();map.put(new Key(object,true),copy);CodeSourceBridge.contributionControl(copy);}
    private static Module[] channel(Object object){
        Module[] direct=CHANNELS.get(new Key(object,false));if(direct!=null)return direct;Ref<Object> parent=WRAPPERS.get(new Key(object,false));return parent==null?new Module[0]:CHANNELS.getOrDefault(new Key(parent.get(),false),new Module[0]);
    }
    public static void channelCreated(Object channel,Object address){
        var frame=boundary();if(!frame.getMethodName().equals("<init>"))throw new SecurityException("ACTUAL_CHANNEL_CREATION_REQUIRED");
        Module[] owners=ControlBridge.invokingSources();synchronized(CHANNELS){reap();record(CHANNELS,channel,owners);}
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
        Module[] owners=union(ControlBridge.invokingSources(),origins(messageClass),origins(encoder),origins(decoder),origins(consumer));
        synchronized(CHANNELS){reap();record(HANDLERS,handler,owners);WRAPPERS.put(new Key(handler,true),new Ref<>(codec));}
    }
    private static Module[] handler(Object object){return HANDLERS.getOrDefault(new Key(object,false),new Module[0]);}
    private static Object messageHandler(Object wrapper,Object message){
        if(message==null)return null;Object codec=field(wrapper,"indexedCodec"),types=field(codec,"types");
        return types instanceof Map<?,?> map?map.get(message.getClass()):null;
    }
    private static Module[] message(Object wrapper,Object handler,Module[] origins){return union(channel(wrapper),handler(handler),origins);}
    public static boolean channelAllowed(Object instance){boundary();synchronized(CHANNELS){return !stopped(channel(instance));}}
    public static boolean messageAllowed(Object wrapper,Object message){boundary();Module[] origins=origins(message);Object handler=messageHandler(wrapper,message);boolean stopped;synchronized(CHANNELS){stopped=stopped(message(wrapper,handler,origins));}return !stopped&&!ControlBridge.stoppedEffects();}
    public static boolean codecAllowed(Object handler){boundary();boolean stopped;synchronized(CHANNELS){Ref<Object> codec=WRAPPERS.get(new Key(handler,false));stopped=stopped(handler(handler))||stopped(codec==null?null:channel(codec.get()));}return !stopped&&!ControlBridge.stoppedEffects();}
    public static Object enter(Object owner){
        var frame=boundary();Module[] modules;synchronized(CHANNELS){Ref<Object> parent=WRAPPERS.get(new Key(owner,false));modules=union(channel(owner),handler(owner),parent==null?null:channel(parent.get()));}
        return scope(frame,modules);
    }
    private static Scope scope(StackWalker.StackFrame frame,Module[] modules){return scope(frame.getDeclaringClass(),frame.getMethodName(),modules);}
    private static Scope scope(Class<?> declaring,String method,Module[] modules){
        Scope scope=new Scope(CURRENT.get(),modules,declaring,method);synchronized(CHANNELS){ACTIVE.add(scope);protect(scope,(Object)modules);CURRENT.set(scope);}return scope;
    }
    private static Module[] packetSources(Object message){Module[] origins=origins(message),sources;synchronized(CHANNELS){reap();sources=union(PACKETS.get(new Key(message,false)),BUFFERS.get(new Key(message,false)),origins);}return union(sources,routed(message));}
    public static Object encoderEnter(Object message){return scope(boundary(),packetSources(message));}
    public static void encodedBuffer(Object buffer){boundary();synchronized(CHANNELS){record(BUFFERS,buffer,currentSources());}}
    public static void writeCreated(Object task,Object context,Object message,Object promise){
        if(!boundary().getMethodName().equals("init"))throw new SecurityException("ACTUAL_NETTY_WRITE_ITEM_REQUIRED");
        // A shared message does not identify the sender whose accepted write
        // crosses to the EventLoop. Capture that invocation before publication.
        Module[] sender=ControlBridge.invokingSources();
        Module[] modules=union(sender,currentSources(),packetSources(message));if(ControlBridge.sourceUnknown())modules=union(modules,new Module[]{null});synchronized(CHANNELS){reap();WRITES.put(new Key(task,true),new Write(modules,new Ref<>(context),new Ref<>(message),new Ref<>(promise)));CodeSourceBridge.contributionControl(modules);}
    }
    public static Object writeEnter(Object task){
        var frame=boundary();Write write;synchronized(CHANNELS){write=WRITES.get(new Key(task,false));}
        Object message=field(task,"msg");
        if(write==null)return writeScope(frame,task,null,union(currentSources(),packetSources(message),new Module[]{null}));
        if(field(task,"ctx")!=write.context().get()||message!=write.message().get()||field(task,"promise")!=write.promise().get()){
            // A changed tuple loses its exact association, not the already
            // captured sender. Keep that source through the real write gate;
            // the current message and unknown marker add no replacement proof.
            Module[] modules=union(write.modules(),currentSources(),packetSources(message),new Module[]{null});
            synchronized(CHANNELS){unobservedQueues++;}return writeScope(frame,task,null,modules);
        }return writeScope(frame,task,write,write.modules());
    }
    private static Scope writeScope(StackWalker.StackFrame frame,Object task,Write write,Module[] modules){
        Scope scope=scope(frame,modules);scope.writeTask=task;scope.write=write;
        try{
            scope.boundaryDepth=NativeControl.networkBoundaryDepth(frame.getDeclaringClass(),frame.getMethodName());
            if(write!=null)scope.retirementGraph=NetworkCleanup.capture(write.context().get(),write.message().get(),write.promise().get(),task,-1);
            return scope;
        }catch(RuntimeException|Error failure){exitScope(scope);throw failure;}
    }
    public static void writeRetired(Object task){if(!boundary().getMethodName().equals("recycle"))throw new SecurityException("ACTUAL_NETTY_WRITE_RETIREMENT_REQUIRED");synchronized(CHANNELS){WRITES.remove(new Key(task,false));}}
    public static boolean writeRecycleAllowed(Object task){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals("io.netty.channel.AbstractChannelHandlerContext$WriteTask")
                ||!frame.getMethodName().equals("recycle")||!frame.getDescriptor().equals("()V"))throw new SecurityException("ACTUAL_NETTY_WRITE_RETIREMENT_REQUIRED");
        Scope scope=CURRENT.get();
        // At the original finally only: clearing the tuple retires the task.
        // A stopped task is discarded instead of lending its source to the shared pool.
        return scope==null||scope.writeTask!=task||!stopped(scope.modules)&&!ControlBridge.stoppedEffects();
    }
    public static void exit(Object token){
        var frame=boundary();if(!(token instanceof Scope scope)||scope.thread!=Thread.currentThread()||scope.declaring!=frame.getDeclaringClass()||!scope.method.equals(frame.getMethodName()))throw new SecurityException("ACTUAL_NETWORK_DISPATCH_SCOPE_REQUIRED");
        exitScope(scope);
    }
    private static void exitScope(Scope scope){
        synchronized(CHANNELS){
            if(scope.ended)return;if(scope.thread!=Thread.currentThread()||CURRENT.get()!=scope)throw new IllegalStateException("NETWORK_DISPATCH_SCOPE_ORDER");
            scope.ended=true;ACTIVE.remove(scope);
        }if(scope.parent==null)CURRENT.remove();else CURRENT.set(scope.parent);
    }
    static Module[] currentSources(){
        if(CURRENT==null)return new Module[0];Set<Module> sources=null;
        for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(!scope.ended)for(Module module:scope.modules){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.add(module);
        }
        return sources==null?new Module[0]:sources.toArray(new Module[0]);
    }
    static boolean sourceUnknown(){if(CURRENT==null)return false;for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(!scope.ended&&unknown(scope.modules))return true;return false;}
    static boolean stoppedSource(){if(CURRENT==null)return false;for(Scope scope=CURRENT.get();scope!=null;scope=scope.parent)if(!scope.ended&&stopped(scope.modules))return true;return false;}
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
    public static boolean packetAllowed(Object message){boundary();return packet(message)&&!ControlBridge.stoppedEffects();}
    public static boolean outboundAllowed(Object message){boundary();boolean allowed=packet(message)&&!ControlBridge.stoppedEffects();if(!allowed)synchronized(CHANNELS){dropped++;}return allowed;}
    public static Throwable outboundRejected(){
        var frame=rejectedBoundary();Cleanup cleanup=CLEANING.get();
        if(cleanup!=null&&cleanup.thread==Thread.currentThread()&&!cleanup.closed&&cleanup.phase==1
                &&cleanup.declaring==frame.getDeclaringClass()&&cleanup.method.equals(frame.getMethodName()))return cleanup.failure;
        throw new SecurityException("ACTUAL_NETTY_REJECTION_COMPLETION_REQUIRED");
    }
    private static StackWalker.StackFrame rejectedBoundary(){
        var frame=boundary();String method=frame.getMethodName(),descriptor=frame.getDescriptor();
        if(!frame.getDeclaringClass().getName().equals("io.netty.channel.AbstractChannelHandlerContext")
                ||!(method.equals("write")&&descriptor.equals("(Ljava/lang/Object;ZLio/netty/channel/ChannelPromise;)V")
                ||(method.equals("invokeWrite")||method.equals("invokeWriteAndFlush"))&&descriptor.equals("(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V")))throw new SecurityException("ACTUAL_REJECTED_NETTY_WRITE_REQUIRED");
        return frame;
    }
    public static Object outboundCleanupEnter(Object context,Object message,Object promise){
        var frame=rejectedBoundary();if(context==null||!frame.getDeclaringClass().isInstance(context)||message==null||promise==null)
            throw new SecurityException("ACTUAL_NETTY_REJECTION_TUPLE_REQUIRED");
        if(CLEANING.get()!=null)throw new SecurityException("NETWORK_REJECTION_CALLBACK_REENTRY");
        Scope scope=CURRENT.get();if(scope!=null&&scope.writeTask!=null&&(scope.write==null||scope.write.context().get()!=context
                ||scope.write.message().get()!=message||scope.write.promise().get()!=promise))throw new SecurityException("NETWORK_REJECTION_TASK_TUPLE_CHANGED");
        int depth=NativeControl.networkBoundaryDepth(frame.getDeclaringClass(),frame.getMethodName());
        Cleanup cleanup=new Cleanup(context,message,promise,frame,depth);protect(cleanup,cleanup.failure);CLEANING.set(cleanup);return cleanup;
    }
    private static Cleanup cleanup(Object token){
        var frame=rejectedBoundary();
        if(!(token instanceof Cleanup cleanup)||cleanup.closed||cleanup.thread!=Thread.currentThread()||CLEANING.get()!=cleanup
                ||cleanup.declaring!=frame.getDeclaringClass()||!cleanup.method.equals(frame.getMethodName()))throw new SecurityException("ACTUAL_NETTY_REJECTION_COMPLETION_REQUIRED");
        return cleanup;
    }
    public static void outboundCleanupPhase(Object token,int phase){
        Cleanup cleanup=cleanup(token);if(phase!=cleanup.phase+1||phase>2)throw new IllegalStateException("NETWORK_REJECTION_PHASE_ORDER");cleanup.phase=phase;
        Scope scope=CURRENT.get();cleanup.graph=NetworkCleanup.capture(cleanup.context,cleanup.message,cleanup.promise,scope==null?null:scope.writeTask,phase);
    }
    public static Throwable outboundCleanupExit(Object token,Throwable release,Throwable promise,Throwable flush){
        Cleanup cleanup=cleanup(token);
        try{Throwable failure=release!=null?release:promise!=null?promise:flush;
            if(failure!=null){if(promise!=null&&promise!=failure)failure.addSuppressed(promise);if(flush!=null&&flush!=failure&&flush!=promise)failure.addSuppressed(flush);}
            return failure;
        }finally{cleanup.closed=true;if(cleanup.parent==null)CLEANING.remove();else CLEANING.set(cleanup.parent);}
    }
    static boolean retirementFieldAllowed(Object receiver,Class<?> declaring,String name,Object value){
        if(receiver==null||declaring==null)return false;
        Field field=NativeControl.networkField(declaring,name);return field!=null&&retirementAllowed(receiver,field,value);
    }
    static boolean retirementWriteAllowed(Object receiver,long offset,Object value,int width){
        if(receiver==null||CLEANING.get()==null&&CURRENT.get()==null)return false;
        if(receiver.getClass().isArray()){
            long[] layout=ResourceBridge.arrayLayout(receiver.getClass());if(layout==null||layout[1]!=width||offset<layout[0]||(offset-layout[0])%layout[1]!=0)return false;
            long index=(offset-layout[0])/layout[1];return index<=Integer.MAX_VALUE&&retirementArrayAllowed(receiver,(int)index,value);
        }
        Field field=ResourceBridge.exactField(receiver,offset,width);return field!=null&&retirementAllowed(receiver,field,value);
    }
    static boolean retirementArrayAllowed(Object array,int index,Object value){
        Cleanup cleanup=CLEANING.get();if(cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread())
            return NetworkCleanup.boundaryAllowed(cleanup.declaring,cleanup.method,cleanup.phase)&&NetworkCleanup.arrayAllowed(array,index,value);
        Scope scope=CURRENT.get();return scope!=null&&!scope.ended&&scope.thread==Thread.currentThread()&&scope.writeTask!=null&&stopped(scope.modules)
                &&NetworkCleanup.boundaryAllowed(scope.declaring,scope.method,-1)&&NetworkCleanup.arrayAllowed(array,index,value);
    }
    private static boolean retirementAllowed(Object receiver,Field field,Object value){
        Cleanup cleanup=CLEANING.get();Scope scope=CURRENT.get();
        if(cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread()){
            return NetworkCleanup.allowed(cleanup.context,cleanup.message,cleanup.promise,scope==null?null:scope.writeTask,
                    cleanup.declaring,cleanup.method,cleanup.phase,receiver,field,value);
        }
        if(scope==null||scope.ended||scope.thread!=Thread.currentThread()||scope.writeTask==null||!stopped(scope.modules))return false;
        Write write=scope.write;
        return NetworkCleanup.allowed(write==null?null:write.context().get(),write==null?null:write.message().get(),write==null?null:write.promise().get(),
                scope.writeTask,scope.declaring,scope.method,-1,receiver,field,value);
    }
    static boolean rejectionCause(Object value){
        Cleanup cleanup=CLEANING.get();return cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread()&&cleanup.phase==1&&value==cleanup.failure;
    }
    static boolean retirementActive(){
        Cleanup cleanup=CLEANING.get();if(cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread())return true;
        Scope scope=CURRENT.get();return scope!=null&&!scope.ended&&scope.thread==Thread.currentThread()&&scope.writeTask!=null&&stopped(scope.modules);
    }
    static int retirementBoundaryDepth(){
        Cleanup cleanup=CLEANING.get();if(cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread())return cleanup.boundaryDepth;
        Scope scope=CURRENT.get();return scope!=null&&!scope.ended&&scope.thread==Thread.currentThread()?scope.boundaryDepth:0;
    }
    static Object retirementGraph(){
        Cleanup cleanup=CLEANING.get();if(cleanup!=null&&!cleanup.closed&&cleanup.thread==Thread.currentThread())return cleanup.graph;
        Scope scope=CURRENT.get();return scope!=null&&!scope.ended&&scope.thread==Thread.currentThread()?scope.retirementGraph:null;
    }
    /** The ordinary failure listener also delivers rejected writes to the shared game connection. */
    public static boolean outboundFailureUnhandled(Object failure){
        var frame=boundary();
        if(!frame.getDeclaringClass().getName().equals("net.minecraft.network.Connection")||!frame.getMethodName().equals("exceptionCaught")
                ||!frame.getDescriptor().equals("(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V"))throw new SecurityException("ACTUAL_CONNECTION_FAILURE_BOUNDARY_REQUIRED");
        return !(failure instanceof OutboundRejected);
    }
    public static void packetEncoded(Object packet,Object buffer){
        boundary();Module[] origins=origins(packet),owners;synchronized(CHANNELS){owners=union(PACKETS.get(new Key(packet,false)),origins);}
        owners=union(owners,routed(packet));synchronized(CHANNELS){record(BUFFERS,buffer,owners);}
    }
    private static Field packetField(Class<?> type,String name,String contract){
        try{Field field=type.getDeclaredField(name);if(Modifier.isStatic(field.getModifiers())||!field.getType().getName().equals(contract))throw new SecurityException("ACTUAL_PACKET_QUEUE_FIELD_REQUIRED");field.setAccessible(true);return field;}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("PACKET_QUEUE_FIELD_UNAVAILABLE",unavailable);}
    }
    private static Object read(Field field,Object receiver){try{return field.get(receiver);}catch(IllegalAccessException unavailable){throw new IllegalStateException("PACKET_QUEUE_FIELD_UNAVAILABLE",unavailable);}}
    private static void member(StackWalker.StackFrame frame,QueueRef queue,String method,String descriptor){
        if(frame.getDeclaringClass()!=queue.type()||!frame.getMethodName().equals(method)||!frame.getDescriptor().equals(descriptor))throw new SecurityException("ACTUAL_CONNECTION_QUEUE_CALL_REQUIRED");
    }
    private static QueueRef connection(Object connection,Object queue){
        QueueRef actual=QUEUES.get(new Key(connection,false));
        if(actual==null||actual.connection().get()!=connection||actual.queue().get()!=queue||field(connection,actual.type(),actual.field())!=queue
                ||queue==null||queue.getClass()!=java.util.concurrent.ConcurrentLinkedQueue.class)throw new SecurityException("ACTUAL_CONNECTION_QUEUE_REQUIRED");return actual;
    }
    private static boolean paired(PacketEntry entry,Object holder,QueueRef queue){
        return !entry.deferred&&holder!=null&&entry.identity.get()==holder&&entry.type==holder.getClass()
                &&entry.packetField.getName().equals(queue.packetField())&&entry.listenerField.getName().equals(queue.listenerField())
                &&read(entry.packetField,holder)==entry.packet.get()&&read(entry.listenerField,holder)==entry.listener.get();
    }
    public static void holderCreated(Object holder,Object packet,Object listener,String packetField,String listenerField){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals(HOLDER)||!frame.getMethodName().equals("<init>")||!frame.getDescriptor().equals(SEND)||holder==null||holder.getClass()!=frame.getDeclaringClass())throw new SecurityException("ACTUAL_PACKET_QUEUE_ITEM_REQUIRED");
        Field packetSlot=packetField(frame.getDeclaringClass(),packetField,"net.minecraft.network.protocol.Packet"),listenerSlot=packetField(frame.getDeclaringClass(),listenerField,"net.minecraft.network.PacketSendListener");
        if(read(packetSlot,holder)!=packet||read(listenerSlot,holder)!=listener)throw new SecurityException("ACTUAL_PACKET_QUEUE_ITEM_REQUIRED");
        PacketEntry entry=new PacketEntry(holder,frame.getDeclaringClass(),packetSlot,listenerSlot,packet,listener,submission(packet,listener));
        synchronized(CHANNELS){reap();HOLDERS.put(new Key(holder,true),entry);}
    }
    public static void connectionCreated(Object connection,Object queue,String name,String submit,String drain,String send,String packetField,String listenerField){
        var frame=boundary();if(!frame.getDeclaringClass().getName().equals(CONNECTION)||!frame.getMethodName().equals("<init>")||connection==null||!frame.getDeclaringClass().isInstance(connection)||queue==null||queue.getClass()!=java.util.concurrent.ConcurrentLinkedQueue.class||field(connection,frame.getDeclaringClass(),name)!=queue)throw new SecurityException("ACTUAL_PACKET_QUEUE_REQUIRED");
        QueueRef actual=new QueueRef(frame.getDeclaringClass(),new Ref<>(connection),name,new Ref<>((Queue<?>)queue),submit,drain,send,packetField,listenerField);protect(actual,actual.connection(),actual.queue());
        synchronized(CHANNELS){reap();QUEUES.put(new Key(connection,true),actual);}
    }
    /** Publish and poll use the same short lock; a consumer cannot outrun its receipt. */
    public static boolean packetQueued(Object queue,Object holder,Object connection){
        var frame=boundary();PacketEntry entry;synchronized(CHANNELS){entry=HOLDERS.get(new Key(holder,false));}
        if(entry==null)throw new SecurityException("ACTUAL_PACKET_QUEUE_ITEM_REQUIRED");
        Module[] sources=submission(entry.packet.get(),entry.listener.get());
        synchronized(CHANNELS){
            QueueRef actual=connection(connection,queue);member(frame,actual,actual.submit(),SEND);
            if(HOLDERS.get(new Key(holder,false))!=entry||entry.published||entry.claimed||entry.closed||!paired(entry,holder,actual))throw new SecurityException("ACTUAL_PACKET_QUEUE_PUBLICATION_REQUIRED");
            entry.modules=union(entry.modules,sources);protect((Object)entry.modules);entry.queue=actual;
            if(stopped(entry.modules)||ControlBridge.stoppedEffects()){HOLDERS.remove(new Key(holder,false));entry.closed=true;dropped++;return true;}
            entry.published=true;
            try{
                @SuppressWarnings("unchecked") boolean accepted=((Queue<Object>)queue).add(holder);
                if(!accepted){entry.closed=true;HOLDERS.remove(new Key(holder,false));}return accepted;
            }catch(RuntimeException|Error failure){entry.modules=union(entry.modules,new Module[]{null});protect((Object)entry.modules);throw failure;}
        }
    }
    public static Object packetQueueEntering(Object queue,Object connection){
        var frame=boundary();synchronized(CHANNELS){
            QueueRef actual=connection(connection,queue);member(frame,actual,actual.drain(),"()V");Scope scope=scope(frame,new Module[0]);scope.queue=actual;scope.connection=connection;return scope;
        }
    }
    public static Object packetPoll(Object queue,Object connection){
        var frame=boundary();Scope drain=CURRENT.get();PacketEntry entry;Object holder;Scope scope;
        synchronized(CHANNELS){
            QueueRef actual=connection(connection,queue);member(frame,actual,actual.drain(),"()V");
            if(drain==null||drain.ended||drain.entry!=null||drain.queue!=actual||drain.connection!=connection||drain.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_PACKET_QUEUE_POLL_REQUIRED");
            holder=((Queue<?>)queue).poll();if(holder==null)return null;
            entry=HOLDERS.get(new Key(holder,false));
            if(entry==null){
                Class<?> type=holder.getClass();boolean known=false;
                for(Role role:ROLES)if(role.name().equals(HOLDER)&&role.name().equals(type.getName())&&role.loader().get()==type.getClassLoader()&&role.module().get()==DefinitionBridge.module(type)){known=true;break;}
                if(!known)throw new SecurityException("ACTUAL_PACKET_QUEUE_ITEM_REQUIRED");
                Field packet=packetField(type,actual.packetField(),"net.minecraft.network.protocol.Packet"),listener=packetField(type,actual.listenerField(),"net.minecraft.network.PacketSendListener");
                entry=new PacketEntry(holder,type,packet,listener,read(packet,holder),read(listener,holder),new Module[]{null});entry.queue=actual;entry.published=true;
            }else if(entry.closed||entry.claimed||entry.deferred||entry.type!=holder.getClass())throw new SecurityException("PACKET_QUEUE_ENTRY_ALREADY_CONSUMED");
            if(entry.queue!=actual||!entry.published||!paired(entry,holder,actual)){entry.modules=union(entry.modules,new Module[]{null});entry.queue=actual;}
            scope=scope(frame,entry.modules);scope.queue=actual;scope.connection=connection;scope.entry=entry;entry.claimed=true;
            HOLDERS.remove(new Key(holder,false));
        }
        Module[] refreshed=union(packetSources(read(entry.packetField,holder)),origins(read(entry.listenerField,holder)));
        synchronized(CHANNELS){entry.modules=union(entry.modules,refreshed);scope.modules=entry.modules;protect((Object)scope.modules);}return holder;
    }
    private static void finishPacket(Scope scope){
        synchronized(CHANNELS){
            if(scope.ended)return;if(scope.thread!=Thread.currentThread()||CURRENT.get()!=scope||scope.entry==null)throw new IllegalStateException("PACKET_QUEUE_SCOPE_ORDER");
            scope.entry.closed=true;
        }exitScope(scope);
    }
    public static void packetQueueSend(Object connection,Object packet,Object listener,Object holder){
        var frame=boundary();Scope scope=CURRENT.get();
        if(scope==null||scope.entry==null||scope.entry.deferred||scope.connection!=connection||scope.entry.identity.get()!=holder||scope.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_PACKET_QUEUE_DISPATCH_REQUIRED");
        member(frame,scope.queue,scope.queue.drain(),"()V");
        try{
            PacketEntry entry=scope.entry;
            if(!paired(entry,holder,scope.queue)||read(entry.packetField,holder)!=packet||read(entry.listenerField,holder)!=listener){
                synchronized(CHANNELS){scope.modules=union(scope.modules,new Module[]{null});entry.modules=scope.modules;protect((Object)scope.modules);}
                throw new SecurityException("ACTUAL_PACKET_QUEUE_DISPATCH_PAIR_REQUIRED");
            }
            if(stopped(scope.modules)||ControlBridge.stoppedEffects()){synchronized(CHANNELS){dropped++;}return;}
            try{
                Method send=scope.queue.type().getDeclaredMethod(scope.queue.send(),entry.packetField.getType(),entry.listenerField.getType());send.setAccessible(true);send.invoke(connection,packet,listener);
            }catch(InvocationTargetException failure){NetworkBridge.<RuntimeException>raise(failure.getCause());}
            catch(ReflectiveOperationException unavailable){throw new IllegalStateException("PACKET_QUEUE_DISPATCH_UNAVAILABLE",unavailable);}
        }finally{finishPacket(scope);}
    }
    public static void packetQueueExiting(Object token){
        var frame=boundary();if(!(token instanceof Scope drain)||drain.queue==null||drain.entry!=null||drain.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_PACKET_QUEUE_DRAIN_REQUIRED");
        member(frame,drain.queue,drain.queue.drain(),"()V");if(drain.ended)return;
        Scope current=CURRENT.get();if(current!=drain){
            if(current==null||current.parent!=drain||current.entry==null)throw new IllegalStateException("PACKET_QUEUE_SCOPE_ORDER");finishPacket(current);
        }exitScope(drain);
    }
    public static void packetDeferred(Object executor,Runnable action,Object connection,Object packet,Object listener){
        var frame=boundary();QueueRef actual;synchronized(CHANNELS){actual=QUEUES.get(new Key(connection,false));
            if(actual==null)throw new SecurityException("ACTUAL_PACKET_EVENTLOOP_HANDOFF_REQUIRED");connection(connection,actual.queue().get());member(frame,actual,actual.send(),SEND);}
        if(!(executor instanceof java.util.concurrent.Executor)||action==null)throw new SecurityException("ACTUAL_PACKET_EVENTLOOP_HANDOFF_REQUIRED");
        Deferred deferred=new Deferred(actual,executor,action,packet,listener,submission(packet,listener));
        synchronized(CHANNELS){HOLDERS.put(new Key(deferred,true),deferred.entry);}
        // execute may accept before throwing or run before it returns. Its tail
        // must not erase a live receipt or revive an already completed one.
        ((java.util.concurrent.Executor)executor).execute(deferred);
    }
    @SuppressWarnings("unchecked") private static <T extends Throwable> void raise(Throwable failure)throws T{throw (T)failure;}
    public static String retire(Module[] selected){
        agent();List<QueueRef> queues;synchronized(CHANNELS){reap();queues=List.copyOf(QUEUES.values());}
        long removed=0,pending=0;for(QueueRef association:queues){Queue<?> queue=association.queue().get();Object connection=association.connection().get();if(queue==null||connection==null)continue;
            if(queue.getClass()!=java.util.concurrent.ConcurrentLinkedQueue.class||field(connection,association.type(),association.field())!=queue){pending++;continue;}
            var cursor=queue.iterator();while(cursor.hasNext()){
                Object holder=cursor.next();PacketEntry entry;synchronized(CHANNELS){entry=HOLDERS.get(new Key(holder,false));}
                if(entry==null){pending++;continue;}
                Module[] refreshed=union(packetSources(entry.packet.get()),origins(entry.listener.get()));
                synchronized(CHANNELS){
                    if(HOLDERS.get(new Key(holder,false))!=entry||entry.claimed||entry.closed)continue;
                    entry.modules=union(entry.modules,refreshed);protect((Object)entry.modules);
                    boolean paired=entry.published&&entry.queue==association&&QUEUES.get(new Key(connection,false))==association&&field(connection,association.type(),association.field())==queue&&paired(entry,holder,association);
                    if(!paired||unknown(entry.modules)){pending++;continue;}
                    boolean exclusive=entry.modules.length!=0;for(Module source:entry.modules){boolean chosen=false;for(Module module:selected)if(source==module){chosen=true;break;}if(!chosen){exclusive=false;break;}}
                    if(exclusive&&stopped(entry.modules)){
                        // CLQ iterator.remove targets its actual node. remove(holder)
                        // could delete a different equal PacketHolder publication.
                        cursor.remove();entry.closed=true;HOLDERS.remove(new Key(holder,false));removed++;
                    }
                }
            }
        }synchronized(CHANNELS){dropped+=removed;unobservedQueues=pending;return "DROPPED="+dropped+";QUEUED_UNOBSERVED="+unobservedQueues;}
    }
    public static String state(){synchronized(CHANNELS){return "NETWORK_DROPPED="+dropped+";NETWORK_QUEUED_UNOBSERVED="+unobservedQueues;}}
    public static String retirementGap(Module module){agent();synchronized(CHANNELS){
        reap();long active=ACTIVE.stream().filter(scope->Arrays.stream(scope.modules).anyMatch(source->source==module)).count();
        long writes=WRITES.values().stream().filter(write->Arrays.stream(write.modules()).anyMatch(source->source==module)).count();
        long queued=HOLDERS.values().stream().filter(entry->entry.published&&!entry.claimed&&!entry.closed&&(unknown(entry.modules)||Arrays.stream(entry.modules).anyMatch(source->source==module))).count();
        long unknownActive=ACTIVE.stream().filter(scope->unknown(scope.modules)).count();
        return active!=0||writes!=0||queued!=0||unknownActive!=0||unobservedQueues!=0?"NETWORK_RETIREMENT_PENDING:active="+active+":writes="+writes+":queued="+queued+":unknownActive="+unknownActive+":queueUnobserved="+unobservedQueues:"";
    }}
    public static Object[] controlObjects(){agent();return new Object[]{CHANNELS,HANDLERS,PACKETS,BUFFERS,WRAPPERS,HOLDERS,QUEUES,WRITES,ROLES,REGISTRIES,DEAD,CURRENT,ACTIVE,CLEANING};}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){CHANNELS.remove(key);HANDLERS.remove(key);PACKETS.remove(key);BUFFERS.remove(key);WRAPPERS.remove(key);HOLDERS.remove(key);QUEUES.remove(key);WRITES.remove(key);}}
}
