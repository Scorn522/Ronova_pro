package dev.ronova.pro.bootstrap;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.Buffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/** Real JDK connection objects, descriptor aliases and asynchronous request lifetimes. */
public final class IoBridge {
    private static final Object LOCK=ResourceBridge.ioLock();
    private static final StackWalker WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final class PlatformBoundary implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,StackWalker.StackFrame>{
        public StackWalker.StackFrame apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();if(frame.getDeclaringClass().getNestHost()!=IoBridge.class)return frame;
            }throw new NoSuchElementException();
        }
    }
    private static final PlatformBoundary PLATFORM_BOUNDARY=new PlatformBoundary();
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final Map<Key,Connection> CONNECTIONS=new HashMap<>();
    private static final Map<Key,Request> REQUESTS=new HashMap<>();
    private static final ThreadLocal<Boolean> OBSERVING=new ThreadLocal<>();
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Connection> CLOSING=new ThreadLocal<>();
    private static final ThreadLocal<DescriptorClose> DESCRIPTOR_CLOSE=new ThreadLocal<>();
    private static final ThreadLocal<Request> FAILURE_DELIVERY=new ThreadLocal<>();
    private static final ThreadLocal<NativeCall> NATIVE_CALL=new ThreadLocal<>();
    private static final ThreadLocal<VectorRead> VECTOR_READ=new ThreadLocal<>();
    private static final Class<?> NIO_SOCKET=type("sun.nio.ch.NioSocketImpl"),SOCKET_BASE=type("java.net.SocketImpl"),
            DELEGATING=type("java.net.DelegatingSocketImpl"),ASYNC_SOCKET=type("sun.nio.ch.AsynchronousSocketChannelImpl"),
            PENDING=type("sun.nio.ch.PendingFuture"),INVOKER=type("sun.nio.ch.Invoker"),IOCP=type("sun.nio.ch.Iocp"),IO_CACHE=type("sun.nio.ch.PendingIoCache");
    private static final Set<Class<?>> ROOTS=types("sun.nio.ch.SocketChannelImpl","sun.nio.ch.ServerSocketChannelImpl","sun.nio.ch.DatagramChannelImpl",
            "sun.nio.ch.WindowsAsynchronousSocketChannelImpl","sun.nio.ch.WindowsAsynchronousServerSocketChannelImpl","sun.nio.ch.WindowsAsynchronousFileChannelImpl","sun.nio.ch.NioSocketImpl");
    private static final Set<Class<?>> TASKS=types("sun.nio.ch.WindowsAsynchronousSocketChannelImpl$ReadTask","sun.nio.ch.WindowsAsynchronousSocketChannelImpl$WriteTask",
            "sun.nio.ch.WindowsAsynchronousSocketChannelImpl$ConnectTask","sun.nio.ch.WindowsAsynchronousServerSocketChannelImpl$AcceptTask",
            "sun.nio.ch.WindowsAsynchronousFileChannelImpl$ReadTask","sun.nio.ch.WindowsAsynchronousFileChannelImpl$WriteTask","sun.nio.ch.WindowsAsynchronousFileChannelImpl$LockTask");
    private static final Set<Class<?>> READ_TASKS=types("sun.nio.ch.WindowsAsynchronousSocketChannelImpl$ReadTask","sun.nio.ch.WindowsAsynchronousFileChannelImpl$ReadTask");
    private static final Class<?> IO_UTIL=type("sun.nio.ch.IOUtil");
    private static final Set<Class<?>> TYPES=allTypes();
    private static final Field SOCKET_IMPL=field(Socket.class,"impl"),SERVER_IMPL=field(ServerSocket.class,"impl"),DATAGRAM_DELEGATE=field(DatagramSocket.class,"delegate"),
            IMPL_DELEGATE=field(DELEGATING,"delegate"),FD_NUMBER=field(FileDescriptor.class,"fd");
    private static final Method IMPL_CLOSE=method(NIO_SOCKET,"close"),SET_FAILURE=method(PENDING,"setFailure",Throwable.class),
            NOTIFY_FAILURE=method(INVOKER,"invoke",PENDING),ENABLE_READ=method(ASYNC_SOCKET,"enableReading"),ENABLE_WRITE=method(ASYNC_SOCKET,"enableWriting");
    private static final ClassValue<Layout> LAYOUTS=new ClassValue<>() {
        protected Layout computeValue(Class<?> actual){
            if(ROOTS.contains(actual))return new Layout(find(actual,"fdObj","fd"),null,null,null,null,null);
            if(TASKS.contains(actual))return new Layout(null,find(actual,"this$0"),find(actual,"result"),find(actual,"bufs","dst","src"),find(actual,"buf","shadow"),find(actual,"channel"));
            return new Layout(null,null,null,null,null,null);
        }
    };
    private record Layout(Field fd,Field outer,Field result,Field buffers,Field temporary,Field child){}
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object value,boolean queued){super(value,queued?DEAD:null);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
        public void clear(){Class<?> caller=WALKER.getCallerClass();if(caller.getNestHost()==IoBridge.class&&DefinitionBridge.module(caller)==IoBridge.class.getModule())super.clear();}
        public boolean enqueue(){Class<?> caller=WALKER.getCallerClass();return caller.getNestHost()==IoBridge.class&&DefinitionBridge.module(caller)==IoBridge.class.getModule()&&super.enqueue();}
    }
    private static final class Connection extends ResourceBridge.FileRecord {
        final WeakReference<Object> root;
        final FileDescriptor descriptor;
        final Field descriptorField;
        final List<WeakReference<Object>> aliases=new ArrayList<>();
        final Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        long version;int requests;
        boolean closed,closeStarted;
        Connection(Object value,FileDescriptor fd,Field actualField){root=new WeakReference<>(value);descriptor=fd;descriptorField=actualField;}
    }
    private static final class Request {
        final Connection connection;final Layout layout;final Module[] sources;
        TemporaryLease temporaryLeases,temporaryTail;
        Object buffers,lease,writes;boolean started,finished,accounted,released,releasing,enteredNative,writesFinishing;
        Request(Connection connection,Layout layout,Module[] sources,Object buffers){this.connection=connection;this.layout=layout;this.sources=sources;this.buffers=buffers;}
    }
    private static final class Scope {
        final Thread caller=Thread.currentThread();final Scope previous=CURRENT.get();
        final Object receiver;final Class<?> declaring;final String method;final Connection connection;final Request request;
        TemporaryLease temporaryLeases,temporaryTail;
        final Module[] sources;Object lease,writes;Throwable refused;boolean closed,ownsLaunch;
        Scope(Object receiver,Class<?> declaring,String method,Connection connection,Request request,Module[] sources){
            this.receiver=receiver;this.declaring=declaring;this.method=method;this.connection=connection;this.request=request;this.sources=sources;
        }
    }
    static final class TemporaryLease {
        // next is assigned only when appending. Captured endpoints remain valid
        // after a return clears its ticket or a later borrow extends the chain.
        Object lease;TemporaryLease next;
        TemporaryLease(){}
    }
    /** One borrow boundary snapshots chain endpoints, not a second list of its tickets. */
    static final class BufferLeases {
        final Object lease;final TemporaryLease first,last;BufferLeases next;
        BufferLeases(Object lease,TemporaryLease first,TemporaryLease last){this.lease=lease;this.first=first;this.last=last;}
    }
    private static final class CloseScope {
        final Connection previous=CLOSING.get();final Thread caller=Thread.currentThread();final Class<?> declaring;final String method;boolean closed;
        CloseScope(Class<?> declaring,String method){this.declaring=declaring;this.method=method;}
    }
    private static final class DescriptorClose {
        final DescriptorClose previous=DESCRIPTOR_CLOSE.get();final Thread caller=Thread.currentThread();
        final FileDescriptor descriptor;final int number;final Class<?> declaring;final String method;boolean closed;
        DescriptorClose(FileDescriptor descriptor,Class<?> declaring,String method)throws IllegalAccessException{
            this.descriptor=descriptor;this.declaring=declaring;this.method=method;number=FD_NUMBER.getInt(descriptor);
        }
    }
    private static final class NativeCall {
        final NativeCall previous=NATIVE_CALL.get();final Thread caller=Thread.currentThread();
        final Object actor,owner;final FileDescriptor descriptor;final Connection connection;
        final Class<?> declaring,site;final String method,where;final long[] arguments;final Object[] references;
        final boolean cleanup;boolean claimed,returned,closed;
        NativeCall(Object actor,Object owner,FileDescriptor descriptor,Connection connection,Class<?> declaring,String method,Class<?> site,String where,long[] arguments,Object[] references,boolean cleanup){
            this.actor=actor;this.owner=owner;this.descriptor=descriptor;this.connection=connection;this.declaring=declaring;this.method=method;this.site=site;this.where=where;
            this.arguments=arguments;this.references=references;this.cleanup=cleanup;
        }
    }
    private record VectorRead(Object operation,long address,int count,boolean windows){}
    private static final class DataRead {
        final Thread caller=Thread.currentThread();final Class<?> declaring;final String method;Object writes;boolean closed;
        DataRead(Class<?> declaring,String method){this.declaring=declaring;this.method=method;}
    }
    private static Class<?> type(String name){try{return Class.forName(name,false,null);}catch(ClassNotFoundException failure){throw new ExceptionInInitializerError(failure);}}
    private static Set<Class<?>> types(String...names){Set<Class<?>> values=Collections.newSetFromMap(new IdentityHashMap<>());for(String name:names)values.add(type(name));return values;}
    private static Set<Class<?>> allTypes(){
        Set<Class<?>> values=types("java.net.Socket","java.net.ServerSocket","java.net.DatagramSocket","java.net.SocketImpl","java.net.DelegatingSocketImpl",
                "sun.nio.ch.SocketAdaptor","sun.nio.ch.ServerSocketAdaptor","sun.nio.ch.DatagramSocketAdaptor","sun.nio.ch.IOUtil","sun.nio.ch.Util","sun.nio.ch.SocketDispatcher","sun.nio.ch.FileDispatcherImpl",
                "sun.nio.ch.AsynchronousSocketChannelImpl","sun.nio.ch.AsynchronousServerSocketChannelImpl","sun.nio.ch.AsynchronousFileChannelImpl",
                "java.nio.channels.spi.AbstractInterruptibleChannel");
        values.addAll(ROOTS);values.addAll(TASKS);return values;
    }
    private static Field field(Class<?> owner,String name){try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    private static Field find(Class<?> actual,String...names){for(Class<?> owner=actual;owner!=null;owner=owner.getSuperclass())for(String name:names)
        try{return field(owner,name);}catch(ExceptionInInitializerError missing){if(!(missing.getCause() instanceof NoSuchFieldException))throw missing;}return null;}
    private static Method method(Class<?> owner,String name,Class<?>...parameters){try{Method value=owner.getDeclaredMethod(name,parameters);value.setAccessible(true);return value;}catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}}
    private static void platform(Class<?> declaring,String name){
        var caller=WALKER.walk(PLATFORM_BOUNDARY);
        if(!TYPES.contains(declaring)||caller.getDeclaringClass()!=declaring||!caller.getMethodName().equals(name))throw new SecurityException("ACTUAL_IO_BOUNDARY_REQUIRED");
    }
    private static void reap(){for(int i=0;i<32;i++){Key key=(Key)DEAD.poll();if(key==null)return;CONNECTIONS.remove(key);REQUESTS.remove(key);}}
    static boolean platformLayout(Class<?> actual){return TYPES.contains(actual);}
    static boolean platformObject(Object value){if(value==null)return false;for(Class<?> actual=value.getClass();actual!=null;actual=actual.getSuperclass())if(TYPES.contains(actual))return true;return false;}
    static boolean resource(Object value){return value!=null&&(ROOTS.contains(value.getClass())||record(value)!=null);}
    static ResourceBridge.FileRecord record(Object value){synchronized(LOCK){return CONNECTIONS.get(new Key(value,false));}}
    static Module[] currentSources(){Scope scope=CURRENT.get();return scope==null?null:scope.sources;}
    static boolean finishingBuffer(Buffer buffer){
        Scope scope=CURRENT.get();if(scope==null||scope.request==null)return false;
        Request request=scope.request;
        synchronized(LOCK){
            if(request.finished||!request.releasing&&!scope.method.equals("completed")&&!scope.method.equals("failed"))return false;
            if(request.buffers==buffer)return true;
            if(request.buffers instanceof Buffer[] buffers)for(Buffer value:buffers)if(value==buffer)return true;
        }
        try{Object temporary=request.layout.temporary==null?null:request.layout.temporary.get(scope.receiver);
            if(temporary==buffer)return true;if(temporary instanceof Buffer[] buffers)for(Buffer value:buffers)if(value==buffer)return true;
        }catch(IllegalAccessException failure){throw new IllegalStateException("ASYNC_BUFFER_ASSOCIATION_UNAVAILABLE",failure);}
        return false;
    }
    static BufferLeases currentBufferLeases(){
        synchronized(LOCK){
            BufferLeases first=null,last=null;
            for(Scope scope=CURRENT.get();scope!=null;scope=scope.previous){
                if(scope.lease!=null||scope.temporaryLeases!=null){
                    BufferLeases entry=new BufferLeases(scope.lease,scope.temporaryLeases,scope.temporaryTail);
                    if(last==null)first=entry;else last.next=entry;last=entry;
                }
                Request request=scope.request;
                if(request!=null&&(request.lease!=null||request.temporaryLeases!=null)){
                    BufferLeases entry=new BufferLeases(request.lease,request.temporaryLeases,request.temporaryTail);
                    if(last==null)first=entry;else last.next=entry;last=entry;
                }
            }
            return first;
        }
    }
    private static long[] readVector(Object operation,long address,int count,boolean windows){
        VectorRead previous=VECTOR_READ.get();VECTOR_READ.set(new VectorRead(operation,address,count,windows));
        try{return NativeControl.ioReadVector(operation,address,count,windows);}
        finally{if(previous==null)VECTOR_READ.remove();else VECTOR_READ.set(previous);}
    }
    static boolean ioVectorActive(Object operation,long address,int count,boolean windows){
        if(WALKER.getCallerClass()!=NativeControl.class)return false;VectorRead scope=VECTOR_READ.get();
        return scope!=null&&scope.operation==operation&&scope.address==address&&scope.count==count&&scope.windows==windows;
    }
    public static Object dataReadOpen(FileDescriptor descriptor,long address,int length,boolean vector,Class<?> declaring,String where){
        platform(declaring,where);if(declaring!=IO_UTIL)throw new SecurityException("ACTUAL_IO_DISPATCHER_CALL_REQUIRED");
        Scope scope=CURRENT.get();if(scope==null||TaskBridge.recoveryWriter()||!NativeControl.available())return null;
        if(scope.connection!=null&&scope.connection.descriptor!=descriptor)throw new SecurityException("ACTUAL_IO_READ_DESCRIPTOR_REQUIRED");
        DataRead read=new DataRead(declaring,where);long[] spans=vector?readVector(read,address,length,false):new long[]{address,length};
        read.writes=ResourceBridge.ioBufferWriting(currentBufferLeases(),spans,scope.sources);return read;
    }
    public static void dataReadFinished(Object value,long transferred,Throwable original){
        if(value==null)return;
        if(!(value instanceof DataRead read)||read.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_IO_READ_RETURN_REQUIRED");
        platform(read.declaring,read.method);if(read.closed)return;read.closed=true;
        try{ResourceBridge.ioBufferWritten(read.writes,transferred,original==null);}
        catch(RuntimeException|Error failure){if(original==null)throw failure;if(failure!=original)original.addSuppressed(failure);}
    }
    public static void temporaryAcquired(Buffer buffer,Class<?> declaring,String where){
        platform(declaring,where);if(buffer==null)return;
        Scope scope=CURRENT.get();if(scope==null||scope.closed)return;
        TemporaryLease entry=new TemporaryLease();entry.lease=ResourceBridge.borrowIoTemporary(buffer,scope.sources);if(entry.lease==null)return;
        synchronized(LOCK){
            Request request=scope.request;
            if(request!=null&&!request.finished){
                if(request.temporaryTail==null)request.temporaryLeases=entry;else request.temporaryTail.next=entry;request.temporaryTail=entry;
            }else{
                if(scope.temporaryTail==null)scope.temporaryLeases=entry;else scope.temporaryTail.next=entry;scope.temporaryTail=entry;
            }
        }
    }
    public static void temporaryReturning(Buffer buffer,Class<?> declaring,String where){
        platform(declaring,where);
        for(Scope scope=CURRENT.get();scope!=null;scope=scope.previous){
            TemporaryLease first,last;synchronized(LOCK){first=scope.temporaryLeases;last=scope.temporaryTail;}
            try{returnTemporaryBuffers(first,last,buffer);}finally{synchronized(LOCK){pruneTemporaryBuffers(scope);}}
            Request request=scope.request;
            if(request!=null){
                synchronized(LOCK){first=request.temporaryLeases;last=request.temporaryTail;}
                try{returnTemporaryBuffers(first,last,buffer);}finally{synchronized(LOCK){pruneTemporaryBuffers(request);accountRequest(request);}}
            }
        }
    }
    private static void returnTemporaryBuffers(TemporaryLease first,TemporaryLease last,Buffer buffer){
        for(TemporaryLease entry=first;entry!=null;entry=entry.next){
            Object lease;synchronized(LOCK){lease=entry.lease;}
            if(lease!=null&&ResourceBridge.releaseIoBuffer(lease,buffer))synchronized(LOCK){entry.lease=null;}
            if(entry==last)break;
        }
    }
    private static void releaseTemporaryBuffers(TemporaryLease first,TemporaryLease last){
        Throwable failure=null;
        for(TemporaryLease entry=first;entry!=null;entry=entry.next){
            Object lease;synchronized(LOCK){lease=entry.lease;}
            if(lease!=null)try{ResourceBridge.releaseIoBuffers(lease);synchronized(LOCK){entry.lease=null;}}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            if(entry==last)break;
        }
        throwCleanup(failure);
    }
    private static void pruneTemporaryBuffers(Scope scope){
        while(scope.temporaryLeases!=null&&scope.temporaryLeases.lease==null)scope.temporaryLeases=scope.temporaryLeases.next;
        if(scope.temporaryLeases==null)scope.temporaryTail=null;
    }
    private static void pruneTemporaryBuffers(Request request){
        while(request.temporaryLeases!=null&&request.temporaryLeases.lease==null)request.temporaryLeases=request.temporaryLeases.next;
        if(request.temporaryLeases==null)request.temporaryTail=null;
    }
    private static void releaseTemporaryBuffers(Scope scope){
        TemporaryLease first,last;synchronized(LOCK){first=scope.temporaryLeases;last=scope.temporaryTail;}
        try{releaseTemporaryBuffers(first,last);}finally{synchronized(LOCK){pruneTemporaryBuffers(scope);}}
    }
    private static void releaseScopeLease(Scope scope){
        Object lease;synchronized(LOCK){lease=scope.lease;}
        if(lease!=null){ResourceBridge.releaseIoBuffers(lease);synchronized(LOCK){if(scope.lease==lease)scope.lease=null;}}
    }
    public static Buffer[] bufferRange(Buffer[] buffers,int offset,int length){
        // Invalid ranges remain the original JDK method's error; unused array elements are never observed.
        if(buffers==null||offset<0||length<0||offset>buffers.length-length)return null;
        return Arrays.copyOfRange(buffers,offset,offset+length);
    }
    private static void addSources(Connection connection,Module[] sources){
        if(sources.length==0)connection.unknownUse=true;
        for(Module source:sources){if(source==null)connection.unknownUse=true;else connection.sources.add(source);}connection.version++;
    }
    private static void alias(Connection connection,Object value){
        if(value==null)return;Connection old=CONNECTIONS.get(new Key(value,false));if(old==connection)return;
        if(old!=null)throw new IllegalStateException("IO_OBJECT_CONNECTION_CHANGED");
        CONNECTIONS.put(new Key(value,true),connection);connection.aliases.add(new WeakReference<>(value));connection.version++;
    }
    private static Connection resolve(Object value)throws IllegalAccessException {
        if(value==null)return null;
        synchronized(LOCK){Connection known=CONNECTIONS.get(new Key(value,false));if(known!=null)return known;}
        // Read only the actual platform delegation fields. No user accessor or identity by handle value.
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());Object current=value;
        while(current!=null&&seen.add(current)){
            synchronized(LOCK){Connection known=CONNECTIONS.get(new Key(current,false));if(known!=null){alias(known,value);return known;}}
            Class<?> actual=current.getClass();
            if(actual==Socket.class)current=SOCKET_IMPL.get(current);
            else if(actual==ServerSocket.class)current=SERVER_IMPL.get(current);
            else if(actual==DatagramSocket.class||actual==MulticastSocket.class)current=DATAGRAM_DELEGATE.get(current);
            else if(DELEGATING.isInstance(current)&&actual.getClassLoader()==null)current=IMPL_DELEGATE.get(current);
            else if(TYPES.contains(actual)&&!ROOTS.contains(actual)){
                Field channel=find(actual,"sc","dc","ssc");current=channel==null?null:channel.get(current);
            }else return null;
        }
        return null;
    }
    public static void constructed(Object value,Class<?> declaring,String where){
        platform(declaring,where);if(OBSERVING.get()!=null||!ROOTS.contains(value.getClass()))return;
        OBSERVING.set(Boolean.TRUE);
        try{
            Layout layout=LAYOUTS.get(value.getClass());FileDescriptor fd=(FileDescriptor)layout.fd.get(value);if(fd==null)return;
            Module[] sources=TaskBridge.resourceOperationSources(currentSources());
            synchronized(LOCK){reap();Connection record=CONNECTIONS.get(new Key(value,false));
                if(record==null){record=new Connection(value,fd,layout.fd);alias(record,value);}
                else if(record.descriptor!=fd)throw new IllegalStateException("IO_DESCRIPTOR_ASSOCIATION_CHANGED");
                addSources(record,sources);
            }
            TaskBridge.resourceCreated(value,sources);
        }catch(IllegalAccessException failure){throw new IllegalStateException("IO_CONSTRUCTION_UNAVAILABLE",failure);}finally{OBSERVING.remove();}
    }
    public static void exposed(Object owner,Object value,Class<?> declaring,String where){
        platform(declaring,where);if(value==null||OBSERVING.get()!=null)return;OBSERVING.set(Boolean.TRUE);
        try{
            Connection connection=resolve(owner);if(connection==null)connection=resolve(value);if(connection==null)return;
            Module[] sources=TaskBridge.resourceOperationSources(currentSources());
            synchronized(LOCK){alias(connection,owner);alias(connection,value);addSources(connection,sources);}
            TaskBridge.resourceCreated(owner,sources);TaskBridge.resourceCreated(value,sources);
        }catch(IllegalAccessException failure){throw new IllegalStateException("IO_DELEGATION_UNAVAILABLE",failure);}finally{OBSERVING.remove();}
    }
    public static Object enter(Object receiver,Object[] arguments,Class<?> declaring,String where)throws IOException {
        platform(declaring,where);if(OBSERVING.get()!=null||TaskBridge.recoveryWriter())return null;
        OBSERVING.set(Boolean.TRUE);Connection connection=null;boolean admitted=false;
        try{
            if(!TaskBridge.taskEffectAllowed(receiver,"connection-io"))throw new ClosedChannelException();
            connection=resolve(receiver);
            if(connection==null)for(Object argument:arguments){
                if(argument instanceof FileDescriptor descriptor){synchronized(LOCK){for(Connection candidate:CONNECTIONS.values())if(candidate.descriptor==descriptor){connection=candidate;break;}}}
                if(connection!=null)break;
            }
            Module[] sources=TaskBridge.resourceOperationSources(currentSources());
            if(connection!=null)synchronized(LOCK){
                if(connection.sealed||connection.closed)throw new ClosedChannelException();
                connection.active++;admitted=true;addSources(connection,sources);
            }
            if(connection!=null&&receiver!=null&&!TaskBridge.resourceExposedByOperation(receiver,sources))synchronized(LOCK){connection.unknownUse=true;}
            Scope scope=new Scope(receiver,declaring,where,connection,null,sources);
            scope.lease=ResourceBridge.borrowIoBuffers(arguments,sources);CURRENT.set(scope);return scope;
        }catch(IllegalAccessException failure){throw new IOException("IO_DESCRIPTOR_ASSOCIATION_UNAVAILABLE",failure);}
        catch(Throwable failure){if(admitted)synchronized(LOCK){connection.active--;}throw failure;}
        finally{OBSERVING.remove();}
    }
    public static void exit(Object value,Throwable failure){
        if(value==null)return;if(!(value instanceof Scope scope)||scope.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_IO_SCOPE_REQUIRED");
        platform(scope.declaring,scope.method);finishScope(scope,failure);
    }
    private static void finishScope(Scope scope,Throwable original){
        if(scope.closed)return;if(CURRENT.get()!=scope)throw new SecurityException("ACTUAL_IO_SCOPE_ORDER_REQUIRED");
        try{
            Throwable failure=null;
            try{if(scope.writes!=null){ResourceBridge.ioBufferWritten(scope.writes,0,false);scope.writes=null;}}catch(RuntimeException|Error cleanup){failure=cleanup;}
            try{releaseScopeLease(scope);}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            try{releaseTemporaryBuffers(scope);}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            if(failure!=null){if(original!=null){if(failure!=original)original.addSuppressed(failure);}else throwCleanup(failure);}
        }
        finally{
            synchronized(LOCK){scope.closed=true;if(scope.connection!=null)scope.connection.active--;}
            if(scope.previous==null)CURRENT.remove();else CURRENT.set(scope.previous);
        }
    }
    private static void throwCleanup(Throwable failure){
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
    public static void closing(Object value,Class<?> declaring,String where)throws IOException {
        platform(declaring,where);if(OBSERVING.get()!=null||TaskBridge.recoveryWriter())return;OBSERVING.set(Boolean.TRUE);
        try{
            Connection connection=resolve(value);if(connection==null)return;
            if(!TaskBridge.taskEffectAllowed(value,"connection-close")||!TaskBridge.resourceReleaseAllowed(value))throw new IOException("RONOVA_CONNECTION_CLOSE_REFUSED");
            for(Object alias:aliases(connection))if(!TaskBridge.resourceReleaseAllowed(alias))throw new IOException("RONOVA_SHARED_CONNECTION_CLOSE_REFUSED");
        }catch(IllegalAccessException failure){throw new IOException("IO_CLOSE_ASSOCIATION_UNAVAILABLE",failure);}finally{OBSERVING.remove();}
    }
    public static void descriptorClosed(FileDescriptor descriptor,Class<?> declaring,String where){
        platform(declaring,where);closedDescriptor(descriptor);
    }
    public static void descriptorClosing(FileDescriptor descriptor,Class<?> declaring,String where)throws IOException {
        platform(declaring,where);if(TaskBridge.recoveryWriter()||closingDescriptor(descriptor))return;
        if(!TaskBridge.taskEffectAllowed(descriptor,"connection-close")||!ResourceBridge.ioDescriptorCloseAllowed(descriptor))throw new IOException("RONOVA_CONNECTION_DESCRIPTOR_CLOSE_REFUSED");
    }
    public static Object descriptorCloseEnter(FileDescriptor descriptor,Class<?> declaring,String where)throws IOException {
        platform(declaring,where);descriptorClosing(descriptor,declaring,where);
        try{DescriptorClose scope=new DescriptorClose(descriptor,declaring,where);DESCRIPTOR_CLOSE.set(scope);return scope;}
        catch(IllegalAccessException failure){throw new IOException("IO_ORIGINAL_DESCRIPTOR_UNAVAILABLE",failure);}
    }
    public static void descriptorCloseExit(Object value,Throwable failure){
        if(value==null)return;if(!(value instanceof DescriptorClose scope)||scope.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_DESCRIPTOR_CLOSE_SCOPE_REQUIRED");
        platform(scope.declaring,scope.method);if(scope.closed)return;
        if(DESCRIPTOR_CLOSE.get()!=scope)throw new SecurityException("ACTUAL_DESCRIPTOR_CLOSE_ORDER_REQUIRED");scope.closed=true;
        if(failure==null)closedDescriptor(scope.descriptor);
        if(scope.previous==null)DESCRIPTOR_CLOSE.remove();else DESCRIPTOR_CLOSE.set(scope.previous);
    }
    static void nativeSocketClosing(Class<?> declaring,String method,long number,Module[] sources)throws IOException {
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_NATIVE_DESCRIPTOR_CLOSE_REQUIRED");
        DescriptorClose scope=DESCRIPTOR_CLOSE.get();
        if(scope==null||scope.closed||scope.declaring!=declaring||!method.equals("close0")||scope.number!=number)
            throw new IOException("IO_ORIGINAL_DESCRIPTOR_CLOSE_UNOBSERVED");
        if(closingDescriptor(scope.descriptor)||TaskBridge.recoveryWriter())return;
        for(Module source:sources)if(source!=null&&TaskBridge.modStopped(source))throw new IOException("RONOVA_TERMINAL_CONNECTION_CLOSE");
        if(!TaskBridge.taskEffectAllowed(scope.descriptor,"connection-close")||!ResourceBridge.ioDescriptorCloseAllowed(scope.descriptor))throw new IOException("RONOVA_CONNECTION_DESCRIPTOR_CLOSE_REFUSED");
    }
    static void nativeDescriptorClosed(FileDescriptor descriptor){
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_NATIVE_IO_COMPLETION_REQUIRED");closedDescriptor(descriptor);
    }
    private static void closedDescriptor(FileDescriptor descriptor){synchronized(LOCK){for(Connection connection:CONNECTIONS.values())if(connection.descriptor==descriptor)connection.closed=true;}}
    public static Object closeEnter(Object value,Class<?> declaring,String where){
        platform(declaring,where);CloseScope scope=new CloseScope(declaring,where);
        synchronized(LOCK){Connection connection=CONNECTIONS.get(new Key(value,false));if(connection!=null&&connection.sealed)CLOSING.set(connection);}
        return scope;
    }
    public static void closeExit(Object value,Throwable failure){
        if(value==null)return;if(!(value instanceof CloseScope scope)||scope.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_IO_CLOSE_SCOPE_REQUIRED");
        platform(scope.declaring,scope.method);if(scope.closed)return;scope.closed=true;
        if(scope.previous==null)CLOSING.remove();else CLOSING.set(scope.previous);
    }
    static boolean closingDescriptor(FileDescriptor descriptor){Connection connection=CLOSING.get();return connection!=null&&connection.descriptor==descriptor&&connection.sealed;}
    static boolean retirementCloseOwner(Object owner){
        DescriptorClose scope=DESCRIPTOR_CLOSE.get();if(scope!=null&&!scope.closed&&scope.declaring==owner&&closingDescriptor(scope.descriptor))return true;
        NativeCall call=NATIVE_CALL.get();return call!=null&&!call.closed&&call.cleanup&&call.declaring==owner&&closingDescriptor(call.descriptor);
    }
    public static Object nativeCallOpen(Object actor,Class<?> site,String where,Class<?> declaring,String method,long[] arguments,Object[] references)throws IOException {
        platform(site,where);
        if(!ROOTS.contains(declaring)||arguments.length==0||references.length!=arguments.length)throw new SecurityException("ACTUAL_ASYNC_NATIVE_CALL_REQUIRED");
        try{
            Object owner=TASKS.contains(actor.getClass())?LAYOUTS.get(actor.getClass()).outer.get(actor):actor;
            if(owner.getClass()!=declaring)throw new SecurityException("ACTUAL_ASYNC_NATIVE_OWNER_REQUIRED");
            FileDescriptor descriptor=(FileDescriptor)LAYOUTS.get(declaring).fd.get(owner);
            Field handle=find(declaring,"handle");if(descriptor==null||handle==null)throw new IOException("ASYNC_NATIVE_DESCRIPTOR_UNAVAILABLE");
            long actual=where.equals("<init>")?FD_NUMBER.getInt(descriptor):handle.getLong(owner);
            if(arguments[0]!=actual)throw new IOException("ASYNC_NATIVE_HANDLE_ASSOCIATION_CHANGED");
            Connection connection=resolve(owner);boolean cleanup=method.equals("closesocket0");
            if(cleanup){
                if(!TaskBridge.recoveryWriter()&&!closingDescriptor(descriptor)&&(!TaskBridge.taskEffectAllowed(owner,"connection-close")||!ResourceBridge.ioDescriptorCloseAllowed(descriptor)))
                    throw new IOException("RONOVA_ASYNC_CLOSE_REFUSED");
            }else{
                if(!TaskBridge.recoveryWriter()){
                    if(!TaskBridge.taskEffectAllowed(owner,"async-io"))throw new ClosedChannelException();
                    Module[] sources=TaskBridge.resourceOperationSources(currentSources());for(Module source:sources)if(source!=null&&TaskBridge.modStopped(source))throw new ClosedChannelException();
                }
                if(connection!=null)synchronized(LOCK){if(connection.sealed||connection.closed)throw new ClosedChannelException();}
            }
            // Accept carries two real channel handles; the second comes from this exact AcceptTask's child.
            if(method.equals("accept0")||method.equals("updateAcceptContext")){
                Layout task=LAYOUTS.get(actor.getClass());Object child=task.child==null?null:task.child.get(actor);
                if(child==null||arguments.length<2||find(child.getClass(),"handle").getLong(child)!=arguments[1])throw new IOException("ASYNC_ACCEPT_CHILD_ASSOCIATION_CHANGED");
            }
            NativeCall call=new NativeCall(actor,owner,descriptor,connection,declaring,method,site,where,arguments,references,cleanup);
            synchronized(LOCK){if(connection!=null)connection.active++;}NATIVE_CALL.set(call);return call;
        }catch(IllegalAccessException failure){throw new IOException("ASYNC_NATIVE_ASSOCIATION_UNAVAILABLE",failure);}
    }
    static Object nativeInvoking(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources)throws IOException {
        if(WALKER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_NATIVE_IO_BINDING_REQUIRED");
        NativeCall call=NATIVE_CALL.get();
        if(call==null||call.closed||call.claimed||call.declaring!=declaring||!call.method.equals(method)||!Arrays.equals(call.arguments,arguments)||call.references.length!=references.length)
            throw new IOException("ASYNC_ORIGINAL_CALL_UNOBSERVED");
        for(int i=0;i<references.length;i++)if(references[i]!=call.references[i])throw new IOException("ASYNC_NATIVE_REFERENCE_CHANGED");
        boolean retirement=call.cleanup&&closingDescriptor(call.descriptor);
        Module[] actualSources=null;
        if(!retirement&&!TaskBridge.recoveryWriter()){
            actualSources=TaskBridge.resourceOperationSources(sources);
            for(Module source:actualSources)if(source!=null&&TaskBridge.modStopped(source))throw new ClosedChannelException();
        }
        synchronized(LOCK){
            if(call.connection!=null){
                if(!call.cleanup&&(call.connection.sealed||call.connection.closed))throw new ClosedChannelException();
                if(!retirement){
                    if(actualSources==null)actualSources=TaskBridge.resourceOperationSources(sources);
                    addSources(call.connection,actualSources);
                }
            }
            call.claimed=true;
        }
        return call;
    }
    static void nativeReturned(Object value,boolean completed){
        if(WALKER.getCallerClass()!=ResourceBridge.class||!(value instanceof NativeCall call)||call.caller!=Thread.currentThread()||NATIVE_CALL.get()!=call||!call.claimed)
            throw new SecurityException("ACTUAL_ASYNC_NATIVE_RETURN_REQUIRED");
        call.returned=true;if(completed&&call.cleanup)closedDescriptor(call.descriptor);
    }
    public static void nativeCallClose(Object value,Throwable failure){
        if(!(value instanceof NativeCall call)||call.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_ASYNC_NATIVE_SCOPE_REQUIRED");
        platform(call.site,call.where);if(call.closed)return;
        if(NATIVE_CALL.get()!=call)throw new SecurityException("ACTUAL_ASYNC_NATIVE_ORDER_REQUIRED");
        synchronized(LOCK){call.closed=true;if(call.connection!=null)call.connection.active--;}
        if(call.previous==null)NATIVE_CALL.remove();else NATIVE_CALL.set(call.previous);
        if(failure==null&&call.cleanup&&TaskBridge.recoveryWriter())closedDescriptor(call.descriptor);
    }
    public static void socketClosed(Object owner,Class<?> declaring,String where){
        platform(declaring,where);synchronized(LOCK){Connection connection=CONNECTIONS.get(new Key(owner,false));if(connection!=null)connection.closed=true;}
    }
    static List<Object> descriptorAliases(FileDescriptor descriptor){List<Object> result=new ArrayList<>();synchronized(LOCK){
        Set<Connection> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Connection connection:CONNECTIONS.values())if(connection.descriptor==descriptor&&seen.add(connection))result.addAll(aliases(connection));}return result;}
    private static List<Object> aliases(Connection connection){List<Object> result=new ArrayList<>();synchronized(LOCK){for(var reference:connection.aliases){Object value=reference.get();if(value!=null)result.add(value);}}return result;}
    static String release(Object value,Predicate<Object> selected,Set<Module> modules)throws ReflectiveOperationException,IOException {
        Connection connection;List<Object> aliases;long version;Object root;
        synchronized(LOCK){connection=CONNECTIONS.get(new Key(value,false));if(connection==null)return "IO_CONSTRUCTION_UNOBSERVED";
            root=connection.root.get();if(root==null)return connection.closed&&connection.requests==0?"":"IO_ORIGINAL_OWNER_UNAVAILABLE";
            if(connection.descriptorField.get(root)!=connection.descriptor)return "IO_DESCRIPTOR_ASSOCIATION_CHANGED";
            if(connection.unknownUse)return "IO_SOURCE_UNOBSERVED";
            for(Module source:connection.sources)if(!modules.contains(source)){connection.preserved=true;return "";}
            aliases=aliases(connection);version=connection.version;
        }
        for(Object alias:aliases)if(!selected.test(alias)){synchronized(LOCK){connection.preserved=true;}return "";}
        String descriptor=ResourceBridge.ioDescriptorRetirement(connection.descriptor,selected,modules);
        if(descriptor.equals("SHARED")){synchronized(LOCK){connection.preserved=true;}return "";}
        if(!descriptor.isEmpty())return descriptor;
        synchronized(LOCK){if(version!=connection.version)return "IO_SOURCE_CHANGED";ResourceBridge.sealIoDescriptor(connection.descriptor,modules);connection.sealed=true;connection.preserved=false;}
        Connection previous=CLOSING.get();CLOSING.set(connection);
        try{
            if(!connection.closeStarted){
                if(root instanceof Channel channel)channel.close();
                else if(root.getClass()==NIO_SOCKET)invoke(IMPL_CLOSE,root);
                else return "IO_ORIGINAL_CLOSE_UNAVAILABLE";
                synchronized(LOCK){connection.closeStarted=true;}
            }
        }finally{if(previous==null)CLOSING.remove();else CLOSING.set(previous);}
        synchronized(LOCK){return !connection.closed?"IO_NATIVE_CLOSE_PENDING":connection.active!=0||connection.requests!=0?"IO_OPERATIONS_EXIT_PENDING":"";}
    }
    public static void taskCreated(Object task,Class<?> declaring){
        platform(declaring,"<init>");if(!TASKS.contains(task.getClass()))throw new SecurityException("ACTUAL_ASYNC_TASK_REQUIRED");
        if(OBSERVING.get()!=null)return;OBSERVING.set(Boolean.TRUE);
        try{
            Layout layout=LAYOUTS.get(task.getClass());Object owner=layout.outer.get(task);Connection connection=resolve(owner);
            Module[] sources=TaskBridge.resourceOperationSources(currentSources());Object buffers=layout.buffers==null?null:layout.buffers.get(task);
            Field count=find(task.getClass(),"numBufs");if(buffers instanceof Buffer[] array&&count!=null)buffers=bufferRange(array,0,count.getInt(task));
            synchronized(LOCK){reap();Key key=new Key(task,true);if(REQUESTS.containsKey(key))return;
                REQUESTS.put(key,new Request(connection,layout,sources,buffers));
                if(connection!=null){connection.requests++;addSources(connection,sources);}
            }
            TaskBridge.resourceCreated(task,sources);
        }catch(IllegalAccessException failure){throw new IllegalStateException("ASYNC_REQUEST_ASSOCIATION_UNAVAILABLE",failure);}finally{OBSERVING.remove();}
    }
    public static Object taskEnter(Object task,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}
        if(request==null)return null;
        if((where.equals("completed")||where.equals("failed"))&&FAILURE_DELIVERY.get()!=request&&!completionDispatcher(declaring))throw new SecurityException("ACTUAL_ASYNC_COMPLETION_REQUIRED");
        Scope scope=new Scope(task,declaring,where,request.connection,request,request.sources);
        synchronized(LOCK){if(request.connection!=null)request.connection.active++;}
        CURRENT.set(scope);
        if(where.equals("run"))try{
            Object buffers;
            synchronized(LOCK){if(request.started)throw new IOException("ASYNC_REQUEST_ALREADY_STARTED");request.started=true;scope.ownsLaunch=true;
                if(request.finished||request.connection!=null&&(request.connection.sealed||request.connection.closed))throw new ClosedChannelException();buffers=request.buffers;}
            for(Module source:request.sources)if(source!=null&&TaskBridge.modStopped(source))throw new ClosedChannelException();
            // Until publication succeeds, the actual submitting scope owns the
            // borrow. Completion cannot detach it before this assignment.
            scope.lease=ResourceBridge.borrowIoBuffers(new Object[]{buffers},request.sources);
            synchronized(LOCK){if(request.finished)throw new ClosedChannelException();request.lease=scope.lease;scope.lease=null;}
        }catch(Throwable failure){scope.refused=failure;}
        return scope;
    }
    private static boolean completionDispatcher(Class<?> task){
        return WALKER.walk(frames->{var iterator=frames.iterator();while(iterator.hasNext()){
            Class<?> actual=iterator.next().getDeclaringClass();
            if(actual.getNestHost()==IoBridge.class||actual.getNestHost()==task.getNestHost())continue;
            if(actual.getClassLoader()==null&&(actual.getName().startsWith("java.lang.reflect.")||actual.getName().startsWith("jdk.internal.reflect.")||actual.getName().startsWith("java.lang.invoke.")))continue;
            return actual.getNestHost()==IOCP||actual.getNestHost()==IO_CACHE;
        }return false;});
    }
    public static boolean taskReady(Object value){
        if(value==null)return true;Scope scope=(Scope)value;platform(scope.declaring,scope.method);return scope.refused==null;
    }
    public static void taskRejected(Object value){
        Scope scope=(Scope)value;platform(scope.declaring,scope.method);Throwable failure=scope.refused;
        if(scope.ownsLaunch)try{finishRequest(scope.request);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
        finishScope(scope,failure);
        if(scope.ownsLaunch)failUnsubmitted(scope.receiver,scope.request,failure,true);
    }
    public static void taskNative(Object task,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}if(request==null)return;
        requireRequestScope(task,request);
        synchronized(LOCK){if(request.finished)throw new IllegalStateException("ASYNC_REQUEST_ALREADY_FINISHED");}
        if(READ_TASKS.contains(declaring)&&NativeControl.available()){
            NativeCall call=NATIVE_CALL.get();if(call==null||call.actor!=task||call.closed)throw new SecurityException("ACTUAL_ASYNC_READ_CALL_REQUIRED");
            try{
                long[] spans;
                if(call.method.equals("readFile"))spans=new long[]{call.arguments[1],call.arguments[2]};
                else if(call.method.equals("read0")){
                    Object shadow=request.layout.temporary.get(task);int count=find(declaring,"numBufs").getInt(task);
                    if(!(shadow instanceof Buffer[] buffers)||count<0||count>buffers.length||call.arguments[1]!=count
                            ||find(call.declaring,"readBufferArray").getLong(call.owner)!=call.arguments[2])throw new SecurityException("ACTUAL_ASYNC_READ_VECTOR_REQUIRED");
                    spans=readVector(call,call.arguments[2],count,true);
                }else throw new SecurityException("ACTUAL_ASYNC_READ_METHOD_REQUIRED");
                Scope scope=CURRENT.get();scope.writes=ResourceBridge.ioBufferWriting(currentBufferLeases(),spans,request.sources);
                synchronized(LOCK){if(request.finished)throw new IllegalStateException("ASYNC_REQUEST_ALREADY_FINISHED");request.writes=scope.writes;scope.writes=null;}
            }catch(IllegalAccessException failure){throw new IllegalStateException("ASYNC_READ_LAYOUT_UNAVAILABLE",failure);}
        }
        synchronized(LOCK){if(request.finished)throw new IllegalStateException("ASYNC_REQUEST_ALREADY_FINISHED");request.enteredNative=true;}
    }
    public static void taskReadCompleted(Object task,int transferred,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}
        if(request!=null&&READ_TASKS.contains(declaring)){requireRequestScope(task,request);finishRequestWrites(request,transferred,true);}
    }
    private static void finishRequestWrites(Request request,long transferred,boolean completed){
        Object writes;synchronized(LOCK){if(request.writesFinishing)return;writes=request.writes;if(writes==null)return;request.writesFinishing=true;}
        boolean released=false;
        try{ResourceBridge.ioBufferWritten(writes,transferred,completed);released=true;}
        finally{synchronized(LOCK){if(released&&request.writes==writes)request.writes=null;request.writesFinishing=false;accountRequest(request);}}
    }
    public static void taskReleased(Object task,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}
        if(request!=null){requireRequestScope(task,request);synchronized(LOCK){request.released=true;}finishRequest(request);}
    }
    public static void taskReleasing(Object task,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}
        // The JDK calls this only after native completion or a failed submission, before returning cached buffers.
        if(request!=null){
            requireRequestScope(task,request);synchronized(LOCK){request.releasing=true;}
            Throwable failure=null;
            try{finishRequestWrites(request,0,false);}catch(RuntimeException|Error cleanup){failure=cleanup;}
            try{releaseTemporaryBuffers(request);}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throwCleanup(failure);
        }
    }
    public static void taskResult(Object task,Class<?> declaring,String where){
        platform(declaring,where);Request request;synchronized(LOCK){request=REQUESTS.get(new Key(task,false));}
        if(request!=null&&request.layout.buffers==null){requireRequestScope(task,request);finishRequest(request);}
    }
    private static void requireRequestScope(Object task,Request request){Scope scope=CURRENT.get();if(scope==null||scope.receiver!=task||scope.request!=request||scope.closed)throw new SecurityException("ACTUAL_ASYNC_REQUEST_SCOPE_REQUIRED");}
    public static void taskExit(Object value,Throwable failure){
        if(value==null)return;Scope scope=(Scope)value;platform(scope.declaring,scope.method);
        boolean unsubmitted,finished;synchronized(LOCK){finished=scope.request.finished;unsubmitted=failure!=null&&scope.ownsLaunch&&scope.method.equals("run")&&!scope.request.enteredNative&&!finished;}
        if(failure!=null&&!finished&&scope.request.layout.buffers!=null&&(scope.method.equals("completed")||scope.method.equals("failed"))){
            try{
                Method release=method(scope.declaring,scope.request.layout.buffers.getName().equals("bufs")?"releaseBuffers":"releaseBufferIfSubstituted");
                invoke(release,scope.receiver);
            }catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
        }
        synchronized(LOCK){finished=scope.request.finished;}
        if(unsubmitted||failure!=null&&finished)try{finishRequest(scope.request);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
        finishScope(scope,failure);
        synchronized(LOCK){finished=scope.request.finished;}
        if(unsubmitted||failure!=null&&finished&&scope.request.layout.buffers!=null)failUnsubmitted(scope.receiver,scope.request,failure,unsubmitted);
    }
    private static void finishRequest(Request request){
        synchronized(LOCK){request.finished=true;request.buffers=null;}
        releaseRequestBuffers(request);
    }
    private static void accountRequest(Request request){
        if(request.finished&&!request.accounted&&!request.writesFinishing&&request.writes==null&&request.lease==null&&request.temporaryLeases==null){
            request.accounted=true;if(request.connection!=null)request.connection.requests--;
        }
    }
    private static void releaseRequestBuffers(Request request){
        Throwable failure=null;
        try{finishRequestWrites(request,0,false);}catch(RuntimeException|Error cleanup){failure=cleanup;}
        Object lease;synchronized(LOCK){lease=request.lease;}
        try{
            if(lease!=null)ResourceBridge.releaseIoBuffers(lease);
            synchronized(LOCK){if(request.lease==lease)request.lease=null;accountRequest(request);}
        }
        catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        try{releaseTemporaryBuffers(request);}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        throwCleanup(failure);
    }
    private static void releaseTemporaryBuffers(Request request){
        TemporaryLease first,last;synchronized(LOCK){first=request.temporaryLeases;last=request.temporaryTail;}
        try{releaseTemporaryBuffers(first,last);}finally{synchronized(LOCK){pruneTemporaryBuffers(request);accountRequest(request);}}
    }
    private static void failUnsubmitted(Object task,Request request,Throwable failure,boolean reset){
        try{
            Object future=request.layout.result.get(task);if(!reset&&((Future<?>)future).isDone())return;
            if(request.layout.buffers==null){
                Request previous=FAILURE_DELIVERY.get();FAILURE_DELIVERY.set(request);
                try{invoke(method(task.getClass(),"failed",int.class,IOException.class),task,0,failure instanceof IOException?failure:new IOException("ASYNC_REQUEST_NOT_SUBMITTED",failure));}
                finally{if(previous==null)FAILURE_DELIVERY.remove();else FAILURE_DELIVERY.set(previous);}return;
            }
            Object owner=request.layout.outer.get(task);String name=task.getClass().getSimpleName();
            if(ASYNC_SOCKET.isInstance(owner)){
                if(name.equals("ReadTask"))invoke(ENABLE_READ,owner);
                else if(name.equals("WriteTask"))invoke(ENABLE_WRITE,owner);
            }
            if(!((Future<?>)future).isDone()){
                invoke(SET_FAILURE,future,failure instanceof IOException?failure:new IOException("ASYNC_REQUEST_NOT_SUBMITTED",failure));
                invoke(NOTIFY_FAILURE,null,future);
            }
        }catch(Throwable secondary){if(secondary!=failure)failure.addSuppressed(secondary);}
    }
    private static Object invoke(Method method,Object receiver,Object...arguments)throws IOException,ReflectiveOperationException {
        try{return method.invoke(receiver,arguments);}catch(InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;throw failure;
        }
    }
    private IoBridge(){}
}
