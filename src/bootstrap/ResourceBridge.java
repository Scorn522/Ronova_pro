package dev.ronova.pro.bootstrap;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.*;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Real file-constructor and descriptor-attachment facts, retained without owning the resources. */
public final class ResourceBridge {
    private static final StackWalker WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,
            StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final class PlatformBoundary implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,StackWalker.StackFrame>{
        public StackWalker.StackFrame apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();
            while(cursor.hasNext()){var frame=cursor.next();if(frame.getDeclaringClass().getNestHost()!=ResourceBridge.class)return frame;}
            throw new NoSuchElementException();
        }
    }
    private static final PlatformBoundary PLATFORM_BOUNDARY=new PlatformBoundary();
    private static final ReferenceQueue<Object> RETIRED=new ReferenceQueue<>();
    private static final HashMap<Key,StreamRecord> STREAMS=new HashMap<>();
    private static final HashMap<Key,WrapperRecord> WRAPPERS=new HashMap<>();
    private static final HashMap<Key,ChannelRecord> CHANNELS=new HashMap<>();
    private static final HashMap<Key,DescriptorRecord> ATTACHMENTS=new HashMap<>();
    private static final HashMap<Key,BufferView> BUFFERS=new HashMap<>();
    private static final HashMap<Key,BufferStorage> BUFFER_ACTIONS=new HashMap<>(),BUFFER_CLEANERS=new HashMap<>();
    private static final ThreadLocal<BufferOperation> BUFFER_OPERATIONS=new ThreadLocal<>();
    private static final ThreadLocal<BufferStorage> BUFFER_CLEANING=new ThreadLocal<>();
    private static final ThreadLocal<BufferAllocation> BUFFER_ALLOCATING=new ThreadLocal<>();
    private static final ThreadLocal<BufferRegistration> BUFFER_REGISTERING=new ThreadLocal<>();
    private static final ThreadLocal<BufferRestore> BUFFER_RESTORING=new ThreadLocal<>();
    private static final ThreadLocal<IoBufferWrite> IO_BUFFER_WRITE=new ThreadLocal<>();
    private static final HashMap<Class<?>,Layout> LAYOUTS=new HashMap<>();
    private static final HashMap<Key,ProcessRecord> PROCESSES=new HashMap<>();
    private static final HashMap<Key,WeakReference<PipeRecord>> PROCESS_VIEWS=new HashMap<>();
    private static final ThreadLocal<ProcessRecord> PROCESS_CLEANUP=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FILE_OBSERVING=new ThreadLocal<>();
    private static final ThreadLocal<Set<Module>> FILE_SELECTION=new ThreadLocal<>();
    private static final Class<?> PROCESS_TYPE=platformType("java.lang.ProcessImpl"),PIPE_INPUT=platformType("java.lang.Process$PipeInputStream");
    private static final Class<?> PIPE_REDIRECT=platformType("java.lang.ProcessBuilder$RedirectPipeImpl");
    private static final Class<?> FILE_CHANNEL=platformType("sun.nio.ch.FileChannelImpl"),FILE_CLOSER=platformType("sun.nio.ch.FileChannelImpl$Closer");
    private static final Class<?> FILE_FACTORY=platformType("sun.nio.fs.WindowsChannelFactory");
    private static final Field CHANNEL_FD=field(FILE_CHANNEL,"fd"),CHANNEL_PARENT=field(FILE_CHANNEL,"parent"),CHANNEL_PATH=field(FILE_CHANNEL,"path"),CHANNEL_CLOSER=field(FILE_CHANNEL,"closer");
    private static final Field CLOSER_FD=field(FILE_CLOSER,"fd");
    private static final class BufferPlatform {
        static final Class<?> DIRECT=platformType("java.nio.DirectByteBuffer"),DEALLOCATOR=platformType("java.nio.DirectByteBuffer$Deallocator"),
                UNMAPPER=platformType("sun.nio.ch.FileChannelImpl$Unmapper"),CLEANER=platformType("jdk.internal.ref.Cleaner"),UNSAFE=platformType("jdk.internal.misc.Unsafe");
        static final Field ADDRESS=field(Buffer.class,"address"),CAPACITY=field(Buffer.class,"capacity"),POSITION=field(Buffer.class,"position"),LIMIT=field(Buffer.class,"limit"),CLEANUP=field(DIRECT,"cleaner"),
                DEALLOC_ADDRESS=field(DEALLOCATOR,"address"),DEALLOC_SIZE=field(DEALLOCATOR,"size"),
                MAP_ADDRESS=field(UNMAPPER,"address"),MAP_SIZE=field(UNMAPPER,"size"),MAP_FD=field(UNMAPPER,"fd");
        static final Field STRING_VALUE=field(String.class,"value"),STRING_CODER=field(String.class,"coder");
        static final Set<Class<?>> TYPES=types();
        static final Set<String> READS=Set.of("get","_get","getChar","getShort","getInt","getLong","getFloat","getDouble","getArray","charAt");
        private static Set<Class<?>> types(){
            Set<Class<?>> result=Collections.newSetFromMap(new IdentityHashMap<>());
            Collections.addAll(result,Buffer.class,ByteBuffer.class,MappedByteBuffer.class,DIRECT,platformType("java.nio.DirectByteBufferR"),platformType("java.nio.HeapByteBuffer"),platformType("java.nio.HeapByteBufferR"),DEALLOCATOR,UNMAPPER,CLEANER);
            for(String kind:List.of("Char","Short","Int","Long","Float","Double")){
                result.add(platformType("java.nio."+kind+"Buffer"));
                result.add(platformType("java.nio.Heap"+kind+"Buffer"));result.add(platformType("java.nio.Heap"+kind+"BufferR"));
                for(String suffix:List.of("U","S","RU","RS"))result.add(platformType("java.nio.Direct"+kind+"Buffer"+suffix));
                for(String suffix:List.of("B","L","RB","RL"))result.add(platformType("java.nio.ByteBufferAs"+kind+"Buffer"+suffix));
            }
            return result;
        }
        static final ClassValue<Field> PARENT=new ClassValue<>(){
            protected Field computeValue(Class<?> type){
                for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())if(TYPES.contains(owner))for(String name:List.of("att","bb"))
                    try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException absent){}
                return null;
            }
        };
        static final ClassValue<List<Field>> ARRAYS=new ClassValue<>(){
            protected List<Field> computeValue(Class<?> type){
                List<Field> fields=new ArrayList<>();
                for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())if(TYPES.contains(owner))
                    try{Field field=owner.getDeclaredField("hb");field.setAccessible(true);fields.add(field);}catch(NoSuchFieldException absent){}
                return List.copyOf(fields);
            }
        };
    }
    private record Span(Field field,long offset,int bytes) { }
    private static final Object UNSAFE=unsafe();
    private static final Method ARRAY_BASE=layoutMethod("arrayBaseOffset",Class.class),ARRAY_SCALE=layoutMethod("arrayIndexScale",Class.class),
            INSTANCE_OFFSET=layoutMethod("objectFieldOffset",Field.class),STATIC_OFFSET=layoutMethod("staticFieldOffset",Field.class),
            STATIC_BASE=layoutMethod("staticFieldBase",Field.class);
    private static final ClassValue<List<Span>> INSTANCE_SPANS=new ClassValue<>() {
        protected List<Span> computeValue(Class<?> type){return spans(type,false);}
    };
    private static final ClassValue<List<Span>> STATIC_SPANS=new ClassValue<>() {
        protected List<Span> computeValue(Class<?> type){return spans(type,true);}
    };
    private static final Field FD_NUMBER=field(FileDescriptor.class,"fd"),FD_HANDLE=field(FileDescriptor.class,"handle");
    private static final Field FD_CLOSED=field(FileDescriptor.class,"closed"),FD_PARENT=field(FileDescriptor.class,"parent"),FD_PEERS=field(FileDescriptor.class,"otherParents");
    private static final ThreadPoolExecutor CLOSER=new ThreadPoolExecutor(0,2,10,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),work->{Thread thread=new Thread(work);thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object value,boolean registered){super(value,registered?RETIRED:null);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
        public void clear(){Class<?> caller=WALKER.getCallerClass();if(caller.getNestHost()==ResourceBridge.class&&DefinitionBridge.module(caller)==ResourceBridge.class.getModule())super.clear();}
        public boolean enqueue(){Class<?> caller=WALKER.getCallerClass();return caller.getNestHost()==ResourceBridge.class&&DefinitionBridge.module(caller)==ResourceBridge.class.getModule()&&super.enqueue();}
    }
    private record Layout(Class<?> type,Field descriptor,Field path,Field closed,Field channel) { }
    private static final class DescriptorRecord extends FileRecord {
        final List<WeakReference<Object>> aliases=new ArrayList<>();
        final List<WeakReference<Object>> channels=new ArrayList<>();
        final Set<Module> nativeSources=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean allocated;
    }
    static class FileRecord {
        final java.util.concurrent.locks.ReentrantLock observation=new java.util.concurrent.locks.ReentrantLock();
        Future<String> release;
        boolean retired,sealed,unknownUse,preserved;
        int active,observing;
    }
    private static final class WrapperRecord extends FileRecord {
        final WeakReference<Object> lock;
        final Map<Field,WeakReference<Object>> delegates=new LinkedHashMap<>();
        Object[] children;long exposureVersion;
        WrapperRecord(Object lock){this.lock=new WeakReference<>(lock);}
    }
    private static final class CharacterWrappers {
        static final Set<Class<?>> TYPES=Set.of(BufferedReader.class,BufferedWriter.class,PushbackReader.class,LineNumberReader.class,
                InputStreamReader.class,OutputStreamWriter.class,FileReader.class,FileWriter.class,
                platformType("sun.nio.cs.StreamDecoder"),platformType("sun.nio.cs.StreamEncoder"));
        static final Field READER_LOCK=field(Reader.class,"lock"),WRITER_LOCK=field(Writer.class,"lock");
    }
    public static boolean characterWrapper(Object value){return value!=null&&CharacterWrappers.TYPES.contains(value.getClass());}
    private static boolean wrapperSlot(Field field){return Set.of("in","out","sd","se","ch","cb","buf","bb","lcb").contains(field.getName())&&!java.lang.reflect.Modifier.isStatic(field.getModifiers());}
    public static void wrapperConstructed(Object value,Class<?> declaring){
        platform(declaring,"<init>");if(!characterWrapper(value))return;
        Module[] sources=TaskBridge.resourceOperationSources(null);if(sources.length==0)return;
        try{
            Field lock=value instanceof Reader?CharacterWrappers.READER_LOCK:CharacterWrappers.WRITER_LOCK;
            WrapperRecord record=new WrapperRecord(lock.get(value));
            for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(Field member:type.getDeclaredFields())if(wrapperSlot(member)){
                member.setAccessible(true);Object child=member.get(value);record.delegates.put(member,new WeakReference<>(child));
            }
            synchronized(STREAMS){reap();WRAPPERS.putIfAbsent(new Key(value,true),record);}
            TaskBridge.resourceCreated(value,sources);
            for(var entry:record.delegates.entrySet())if(Set.of("in","out","sd","se","ch").contains(entry.getKey().getName()))exposeWrapperGraph(entry.getValue().get(),sources);
            // These are freshly allocated private buffers, not caller-supplied encoders or delegates.
            for(var entry:record.delegates.entrySet())if(Set.of("cb","buf","bb").contains(entry.getKey().getName())){
                Object child=entry.getValue().get();if(child!=null)TaskBridge.resourceCreated(child,sources);
            }
        }catch(ReflectiveOperationException failed){throw new IllegalStateException("CHARACTER_WRAPPER_CONSTRUCTOR_LAYOUT",failed);}
    }
    public static void wrapperOperation(Object value,Class<?> declaring,String method)throws IOException{
        platform(declaring,method);WrapperRecord record;
        synchronized(STREAMS){record=WRAPPERS.get(new Key(value,false));if(record==null)return;if(record.sealed)throw new IOException("CHARACTER_WRAPPER_RETIRED");record.observing++;record.exposureVersion++;}
        try{exposeWrapperGraph(value,TaskBridge.resourceOperationSources(null));}
        finally{synchronized(STREAMS){record.observing--;}}
    }
    private static void exposeWrapperGraph(Object value,Module[] sources){
        if(value==null||sources.length==0)return;
        ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());pending.add(value);
        while(!pending.isEmpty()){
            Object current=pending.removeFirst();if(!seen.add(current))continue;
            TaskBridge.resourceExposed(current,sources);
            synchronized(STREAMS){WrapperRecord child=WRAPPERS.get(new Key(current,false));if(child!=null)for(var reference:child.delegates.values()){
                Object next=reference.get();if(next!=null)pending.add(next);
            }}
        }
    }
    /** The core consumes captured delegates only after the asynchronous detach is confirmed. */
    public static Object[] wrapperChildren(Object value){
        if(!TaskBridge.recoveryWriter())throw new SecurityException("RESOURCE_RECOVERY_CORE_REQUIRED");
        synchronized(STREAMS){WrapperRecord record=WRAPPERS.get(new Key(value,false));
            if(record==null||!record.retired)throw new IllegalStateException("CHARACTER_WRAPPER_DISPOSITION_PENDING");
            Object[] children=record.children;record.children=null;return children==null?new Object[0]:children;
        }
    }
    private static String releaseWrapper(Object value,Predicate<Object> selected)throws ReflectiveOperationException{
        WrapperRecord record;synchronized(STREAMS){record=WRAPPERS.get(new Key(value,false));}
        if(record==null)return "CHARACTER_WRAPPER_CONSTRUCTOR_UNOBSERVED";
        Object lock=record.lock.get();if(lock==null)return "CHARACTER_WRAPPER_LOCK_UNOBSERVED";
        synchronized(lock){
            long version;synchronized(STREAMS){if(record.observing!=0)return "CHARACTER_WRAPPER_EXPOSURE_PENDING";version=record.exposureVersion;}
            if(!selected.test(value))return "CHARACTER_WRAPPER_OWNERSHIP_CHANGED";
            Field lockField=value instanceof Reader?CharacterWrappers.READER_LOCK:CharacterWrappers.WRITER_LOCK;
            if(lockField.get(value)!=lock)return "CHARACTER_WRAPPER_LOCK_CHANGED";
            ArrayList<Object> children=new ArrayList<>();if(record.children!=null)Collections.addAll(children,record.children);
            for(var entry:record.delegates.entrySet()){
                Field member=entry.getKey();Object actual=member.get(value),original=entry.getValue().get();
                // A platform close may already have cleared the slot; lazy encoder lcb is internal.
                if(actual!=null&&actual!=original&&!member.getName().equals("lcb"))return "CHARACTER_WRAPPER_DELEGATE_CHANGED";
                if(actual!=null)children.add(actual);
            }
            synchronized(STREAMS){if(record.observing!=0||record.exposureVersion!=version)return "CHARACTER_WRAPPER_EXPOSURE_CHANGED";record.sealed=true;record.children=children.toArray();}
            // Never call close/flush: a delegate may also serve a surviving source.
            for(Field member:record.delegates.keySet()){member.set(value,null);if(member.get(value)!=null)return "CHARACTER_WRAPPER_DETACH_READBACK_FAILED";}
            for(Field member:value.getClass().getDeclaredFields())if(member.getName().equals("closed")&&member.getType()==boolean.class){member.setAccessible(true);member.setBoolean(value,true);}
            // Reader/Writer.lock commonly is the delegate itself. Retain no hidden endpoint alias.
            lockField.set(value,value);if(lockField.get(value)!=value)return "CHARACTER_WRAPPER_LOCK_DETACH_FAILED";
            return "";
        }
    }
    private static final class BufferStorage extends FileRecord {
        Runnable original;
        final Field addressField;
        final long address,bytes;
        final FileDescriptor descriptor;
        final Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<WeakReference<Buffer>> views=new ArrayList<>();
        WeakReference<Object> cleaner=new WeakReference<>(null);
        boolean created,cleaned,cleaning,escaped,failed,runtimeManaged,restoring;long viewsVersion,memoryToken,memoryGeneration,memoryCursor;
        final Set<Module> memorySelection=Collections.newSetFromMap(new IdentityHashMap<>());
        String gap="BUFFER_CONSTRUCTION_PENDING";
        BufferStorage(Runnable action,boolean created)throws IllegalAccessException {
            original=action;this.created=created;
            addressField=action==null?null:action.getClass()==BufferPlatform.DEALLOCATOR?BufferPlatform.DEALLOC_ADDRESS:BufferPlatform.MAP_ADDRESS;
            address=action==null?0:(Long)CodeSourceBridge.controlField(addressField,action);
            bytes=action==null?0:(Long)CodeSourceBridge.controlField(addressField==BufferPlatform.DEALLOC_ADDRESS?BufferPlatform.DEALLOC_SIZE:BufferPlatform.MAP_SIZE,action);
            descriptor=addressField==BufferPlatform.MAP_ADDRESS?(FileDescriptor)BufferPlatform.MAP_FD.get(action):null;
        }
    }
    private static final class BufferView extends FileRecord {
        final BufferStorage storage;
        final WeakReference<Object> parent;
        final long address,bytes;
        boolean constructed;
        BufferView(Buffer buffer,BufferStorage storage,Object parent)throws IllegalAccessException {
            this.storage=storage;this.parent=new WeakReference<>(parent);address=(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer);
            int shift=buffer instanceof ByteBuffer?0:buffer instanceof CharBuffer||buffer instanceof ShortBuffer?1:
                    buffer instanceof IntBuffer||buffer instanceof FloatBuffer?2:3;
            bytes=((long)(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer))<<shift;
        }
    }
    private static final class BufferUse {
        final Buffer buffer;final BufferView view;boolean returned;
        BufferUse(Buffer buffer,BufferView view){this.buffer=buffer;this.view=view;}
    }
    private static final class BufferAllocation {
        final Buffer buffer;final BufferAllocation previous=BUFFER_ALLOCATING.get();final Thread caller=Thread.currentThread();boolean closed;
        BufferAllocation(Buffer buffer){this.buffer=buffer;}
    }
    private record BufferRegistration(Buffer buffer,BufferStorage storage){}
    private record BufferSpan(Object holder,BufferStorage storage,long address,long bytes){}
    private record BufferWrite(BufferSpan span,Object receipt){}
    private record BufferRead(Object receipt,Module[] before){}
    private record BufferRestore(BufferStorage storage,Module[] modules,long cursor,int budget){}
    private static final class IoBufferLease {
        final List<BufferUse> uses;int remaining;boolean released;
        IoBufferLease(List<BufferUse> uses){this.uses=uses;remaining=uses.size();}
    }
    private static final class IoBufferWrite {
        final BufferUse use;final long address,bytes;final Module[] sources;final Object group;
        long token,written;boolean ending,completed,closed;
        IoBufferWrite(BufferUse use,long address,long bytes,Module[] sources,Object group){this.use=use;this.address=address;this.bytes=bytes;this.sources=sources;this.group=group;}
    }
    private static final class BufferOperation {
        final Thread caller=Thread.currentThread();final Class<?> declaring;final String method;
        final List<BufferUse> uses;final BufferOperation previous=BUFFER_OPERATIONS.get();boolean closed;
        BufferWrite write;BufferRead read;Object execution;Module[] sources;
        Set<Module> observed;
        BufferOperation(Class<?> declaring,String method,List<BufferUse> uses){this.declaring=declaring;this.method=method;this.uses=uses;}
        void observe(Module[] sources){
            if(sources.length==0)return;
            if(observed==null)observed=Collections.newSetFromMap(new IdentityHashMap<>());
            Collections.addAll(observed,sources);
        }
        void observe(Set<Module> sources){
            if(sources==null||sources.isEmpty())return;
            if(observed==null)observed=Collections.newSetFromMap(new IdentityHashMap<>());
            observed.addAll(sources);
        }
    }
    private static final class BufferAction {
        final Thread caller=Thread.currentThread();final BufferStorage storage,previous=BUFFER_CLEANING.get();final Class<?> declaring;boolean closed;
        BufferAction(BufferStorage storage,Class<?> declaring){this.storage=storage;this.declaring=declaring;}
    }
    private static final class BufferCleanup implements Runnable {
        final BufferStorage storage;
        BufferCleanup(BufferStorage storage){this.storage=storage;}
        public void run(){
            if(!bufferCleanupAllowed(storage))return;
            BufferStorage previous=BUFFER_CLEANING.get();
            synchronized(STREAMS){if(storage.cleaned||storage.cleaning||storage.failed)return;storage.cleaning=true;}
            BUFFER_CLEANING.set(storage);
            try{storage.original.run();bufferCleanupResult(storage,true);}
            catch(Throwable failure){synchronized(STREAMS){storage.failed=true;storage.gap="BUFFER_CLEANUP_FAILED:"+failure.getClass().getSimpleName();}}
            finally{synchronized(STREAMS){storage.cleaning=false;}if(previous==null)BUFFER_CLEANING.remove();else BUFFER_CLEANING.set(previous);}
        }
    }
    private static final class StreamRecord extends FileRecord {
        final Layout layout;
        final WeakReference<FileDescriptor> descriptor;
        final String path;
        final int number;
        final long handle;
        WeakReference<Object> channel=new WeakReference<>(null);
        volatile boolean channelSeen;
        volatile String gap="";
        StreamRecord(Layout layout,Object stream)throws IllegalAccessException {
            this.layout=layout;FileDescriptor actual=(FileDescriptor)layout.descriptor.get(stream);
            descriptor=new WeakReference<>(actual);path=(String)layout.path.get(stream);
            number=actual==null?-1:FD_NUMBER.getInt(actual);handle=actual==null?-1:FD_HANDLE.getLong(actual);
        }
    }
    private static final class ChannelRecord extends FileRecord {
        final WeakReference<Object> channel;
        final WeakReference<FileDescriptor> descriptor;
        final WeakReference<Object> parent;
        final String path;
        final int number;
        final long handle;
        final boolean parentPresent;
        WeakReference<Cleaner.Cleanable> cleaner=new WeakReference<>(null);
        WeakReference<ChannelCleanup> cleanup=new WeakReference<>(null);
        volatile String gap="FILE_CHANNEL_CONSTRUCTION_PENDING";
        volatile boolean detachDescriptor,closeCompleted,cleanupFinished;
        ChannelRecord(Object channel,FileDescriptor descriptor,String path,Object parent)throws IllegalAccessException {
            this.channel=new WeakReference<>(channel);this.descriptor=new WeakReference<>(descriptor);this.parent=new WeakReference<>(parent);this.path=path;parentPresent=parent!=null;
            number=FD_NUMBER.getInt(descriptor);handle=FD_HANDLE.getLong(descriptor);
        }
    }
    private static final class ChannelCleanup implements Runnable {
        final ChannelRecord record;
        final Runnable original;
        ChannelCleanup(ChannelRecord record,Runnable original){this.record=record;this.original=original;}
        public void run(){
            if(!record.detachDescriptor)original.run();
            synchronized(STREAMS){record.cleanupFinished=true;releaseChannelAlias(record);}
        }
    }
    private static final class FileOperation {
        final FileRecord record;
        final Thread caller=Thread.currentThread();
        final Class<?> declaring;
        final String method;
        boolean closed;
        FileOperation(FileRecord record,Class<?> declaring,String method){this.record=record;this.declaring=declaring;this.method=method;}
    }
    private record DescriptorView(FileDescriptor descriptor,List<Object> streams,List<Object> channels,boolean allocated) { }
    static {
        for(Class<?> type:List.of(FileInputStream.class,FileOutputStream.class,RandomAccessFile.class))
            LAYOUTS.put(type,new Layout(type,field(type,"fd"),field(type,"path"),field(type,"closed"),field(type,"channel")));
    }
    private ResourceBridge(){ }
    private static Field field(Class<?> type,String name){
        try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static Class<?> platformType(String name){
        try{return Class.forName(name,false,null);}catch(ClassNotFoundException absent){throw new ExceptionInInitializerError(absent);}
    }
    private static final class ProcessRecord {
        final long handle;final Runnable originalAction;long hostToken;
        Runnable cleanupAction;Cleaner.Cleanable cleaner;
        Object processHandle;PipeRecord[] pipes;long pipelineHandle=-1;FileDescriptor pipelineDescriptor;
        String gap="PROCESS_CONSTRUCTION_PENDING";
        volatile boolean handleClosed;
        boolean retired;Future<String> release;
        final java.util.concurrent.locks.ReentrantLock observation=new java.util.concurrent.locks.ReentrantLock();
        ProcessRecord(long handle,Runnable action){this.handle=handle;originalAction=action;}
    }
    private static final class PipeRecord {
        final Object view,stream;final FileDescriptor descriptor;final byte[] buffer;
        volatile boolean unknownExposure;boolean released;
        PipeRecord(Object view,Object stream,FileDescriptor descriptor,byte[] buffer){this.view=view;this.stream=stream;this.descriptor=descriptor;this.buffer=buffer;}
    }
    public static void processStart()throws IOException {
        platform(PROCESS_TYPE,"start");
        if(!TaskBridge.taskEffectAllowed(null,"process-start"))throw new IOException("RONOVA_TERMINAL_PROCESS_START_REFUSED");
    }
    public static long processCreateBegin(long[] handles)throws IOException {
        platform(PROCESS_TYPE,"<init>");return NativeControl.processCreateBegin(handles);
    }
    public static void processCreateEnd(long token,long handle,long[] handles){
        platform(PROCESS_TYPE,"<init>");NativeControl.processCreateEnd(token,handle,handles);
    }
    /** Keep the JDK's actual close action and its actual invocation, rather than looking up a recycled HANDLE. */
    public static Runnable processCleanupAction(Process process,Runnable action){
        platform(PROCESS_TYPE,"<init>");
        try{
            ProcessRecord record=new ProcessRecord(field(PROCESS_TYPE,"handle").getLong(process),Objects.requireNonNull(action));
            record.hostToken=NativeControl.processConstructed(process,record.handle);
            record.cleanupAction=()->{
                ProcessRecord previous=PROCESS_CLEANUP.get();PROCESS_CLEANUP.set(record);
                try{record.originalAction.run();}finally{if(previous==null)PROCESS_CLEANUP.remove();else PROCESS_CLEANUP.set(previous);}
            };
            synchronized(STREAMS){PROCESSES.put(new Key(process,true),record);}
            return record.cleanupAction;
        }catch(IllegalAccessException failure){throw new IllegalStateException("PROCESS_ORIGINAL_HANDLE_UNOBSERVED",failure);}
    }
    public static void processCleaner(Process process,Cleaner.Cleanable cleaner,Runnable action){
        platform(PROCESS_TYPE,"<init>");synchronized(STREAMS){
            ProcessRecord record=PROCESSES.get(new Key(process,false));
            if(record==null||record.cleanupAction!=action)throw new IllegalStateException("PROCESS_CLEANER_ASSOCIATION_CHANGED");
            record.cleaner=Objects.requireNonNull(cleaner);
        }
    }
    public static void processHandleClosed(long handle,boolean closed,String method){
        platform(PROCESS_TYPE,method);ProcessRecord record=PROCESS_CLEANUP.get();
        if(record!=null&&record.handle==handle&&closed)record.handleClosed=true;
    }
    public static void processConstructed(Process process,long[] handles,boolean forceNullOutput){
        platform(PROCESS_TYPE,"<init>");
        synchronized(STREAMS){
            ProcessRecord record=PROCESSES.get(new Key(process,false));if(record==null)return;
            try{
                if(handles==null||handles.length!=3||field(PROCESS_TYPE,"handle").getLong(process)!=record.handle||record.cleaner==null){record.gap="PROCESS_CONSTRUCTOR_ASSOCIATION_CHANGED";return;}
                record.processHandle=field(PROCESS_TYPE,"processHandle").get(process);record.pipes=new PipeRecord[3];
                String[] fields={"stdin_stream","stdout_stream","stderr_stream"};record.gap="";
                for(int i=0;i<3;i++){
                    Object view=field(PROCESS_TYPE,fields[i]).get(process);
                    if(handles[i]==-1)continue;
                    if(i==1&&forceNullOutput){record.pipelineHandle=handles[i];continue;}
                    Object stream=view;byte[] buffer=null;
                    if(view!=null&&view.getClass()==BufferedInputStream.class){stream=field(FilterInputStream.class,"in").get(view);buffer=(byte[])field(BufferedInputStream.class,"buf").get(view);}
                    else if(view!=null&&view.getClass()==BufferedOutputStream.class){stream=field(FilterOutputStream.class,"out").get(view);buffer=(byte[])field(BufferedOutputStream.class,"buf").get(view);}
                    StreamRecord original=STREAMS.get(new Key(stream,false));
                    if(original==null||(stream.getClass()!=FileOutputStream.class&&stream.getClass()!=PIPE_INPUT)||original.handle!=handles[i]){record.gap="PROCESS_ORIGINAL_PIPE_UNOBSERVED";continue;}
                    FileDescriptor descriptor=original.descriptor.get();DescriptorRecord attachment=ATTACHMENTS.get(new Key(descriptor,false));
                    if(descriptor==null||attachment==null||FD_HANDLE.getLong(descriptor)!=handles[i]){record.gap="PROCESS_ORIGINAL_PIPE_DESCRIPTOR_CHANGED";continue;}
                    attachment.allocated=true;PipeRecord pipe=new PipeRecord(view,stream,descriptor,buffer);record.pipes[i]=pipe;
                    PROCESS_VIEWS.put(new Key(view,true),new WeakReference<>(pipe));PROCESS_VIEWS.put(new Key(stream,true),new WeakReference<>(pipe));
                    TaskBridge.resourceCreated(stream);if(view!=stream)TaskBridge.resourceCreated(view);TaskBridge.resourceCreated(descriptor);
                }
                TaskBridge.resourceCreated(process);TaskBridge.resourceCreated(record.processHandle);
            }catch(IllegalAccessException failure){record.gap="PROCESS_ORIGINAL_LAYOUT_UNOBSERVED";}
        }
    }
    /** start() has now published the exact upstream pipe FD into the actual pipeline redirect. */
    public static void processStarted(Process process,ProcessBuilder.Redirect[] redirects){
        platform(PROCESS_TYPE,"start");synchronized(STREAMS){
            ProcessRecord record=PROCESSES.get(new Key(process,false));if(record==null||record.pipelineHandle==-1)return;
            try{
                if(redirects==null||redirects.length!=3||redirects[1]==null||redirects[1].getClass()!=PIPE_REDIRECT){record.gap="PROCESS_PIPELINE_PUBLICATION_UNOBSERVED";return;}
                FileDescriptor descriptor=(FileDescriptor)field(PIPE_REDIRECT,"fd").get(redirects[1]);
                if(descriptor==null||FD_HANDLE.getLong(descriptor)!=record.pipelineHandle){record.gap="PROCESS_PIPELINE_DESCRIPTOR_CHANGED";return;}
                record.pipelineDescriptor=descriptor;TaskBridge.resourceCreated(descriptor);
            }catch(IllegalAccessException failed){record.gap="PROCESS_PIPELINE_PUBLICATION_UNOBSERVED";}
        }
    }
    public static void processPipeExposed(Process process,Object view,String method){
        platform(PROCESS_TYPE,method);PipeRecord pipe;ProcessRecord record;
        synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));WeakReference<PipeRecord> link=PROCESS_VIEWS.get(new Key(view,false));pipe=link==null?null:link.get();}
        if(record!=null){
            Module[] sources=TaskBridge.invokingSources();NativeControl.processExposed(process,record.hostToken,sources);
            TaskBridge.resourceExposed(process);if(view==record.processHandle)TaskBridge.resourceExposed(view);
        }
        if(pipe!=null&&!TaskBridge.resourceExposed(view))pipe.unknownExposure=true;
    }
    public static boolean retireProcess(Process process,Callable<String> action)throws IOException {
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(action))throw new SecurityException("PROCESS_RECOVERY_CORE_REQUIRED");
        ProcessRecord record;synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));}
        if(record==null)throw new IOException("PROCESS_CONSTRUCTOR_ASSOCIATION_UNOBSERVED");
        if(!record.observation.tryLock())return false;
        try{
            if(record.retired)return true;
            if(record.release==null){
                try{FutureTask<String> future=new FutureTask<>(action);record.release=future;CLOSER.execute(future);}
                catch(RejectedExecutionException full){record.release=null;throw new IOException("RESOURCE_DISPOSITION_CAPACITY",full);}
                return false;
            }
            if(!record.release.isDone())return false;
            Future<String> result=record.release;record.release=null;
            try{String gap=result.get();if(!gap.isEmpty())throw new IOException(gap);record.retired=true;return true;}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("PROCESS_RELEASE_OBSERVATION_INTERRUPTED",interrupted);}
            catch(ExecutionException failed){throw new IOException("PROCESS_RELEASE_FAILED",failed.getCause());}
        }finally{record.observation.unlock();}
    }
    public static String releaseProcess(Process process,Predicate<Object> selected){
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(selected))throw new SecurityException("PROCESS_RELEASE_CORE_REQUIRED");
        ProcessRecord record;synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));}
        if(record==null)return "PROCESS_CONSTRUCTOR_ASSOCIATION_UNOBSERVED";
        try{
            if(!selected.test(process))return "PROCESS_QUALIFICATION_CHANGED";
            if(field(PROCESS_TYPE,"handle").getLong(process)!=record.handle||field(PROCESS_TYPE,"processHandle").get(process)!=record.processHandle)return "PROCESS_ORIGINAL_ASSOCIATION_CHANGED";
            String hostGap=NativeControl.retireProcess(process,record.hostToken);if(!hostGap.isEmpty())return hostGap;
            if(record.handleClosed)return record.retired?"":"PROCESS_HANDLE_CLOSED_BEFORE_PIPE_RELEASE";
            process.destroyForcibly();if(process.isAlive())return "PROCESS_EXIT_PENDING";
            String gap=record.gap;
            if(record.pipelineHandle!=-1&&(record.pipelineDescriptor==null||record.pipelineDescriptor.valid()||!FD_CLOSED.getBoolean(record.pipelineDescriptor)||FD_HANDLE.getLong(record.pipelineDescriptor)!=-1))gap="PROCESS_PIPELINE_PARENT_ENDPOINT_RELEASE_PENDING";
            if(record.pipes!=null)for(PipeRecord pipe:record.pipes){
                if(pipe==null||pipe.released)continue;
                if(pipe.unknownExposure||!selected.test(pipe.view)||!selected.test(pipe.stream)){gap="PROCESS_PIPE_SHARED_OR_UNQUALIFIED";continue;}
                String remaining=release(pipe.stream,selected);if(!remaining.isEmpty()){gap=remaining;continue;}
                if(pipe.view.getClass()==BufferedInputStream.class){
                    Object current=field(FilterInputStream.class,"in").get(pipe.view);
                    if(current!=null&&current!=pipe.stream){gap="PROCESS_BUFFERED_PIPE_ASSOCIATION_CHANGED";continue;}
                    ((BufferedInputStream)pipe.view).close();
                    if(field(FilterInputStream.class,"in").get(pipe.view)!=null||field(BufferedInputStream.class,"buf").get(pipe.view)!=null){gap="PROCESS_BUFFERED_INPUT_RELEASE_PENDING";continue;}
                }else if(pipe.view.getClass()==BufferedOutputStream.class){
                    synchronized(pipe.view){
                        if(field(FilterOutputStream.class,"out").get(pipe.view)!=pipe.stream||field(BufferedOutputStream.class,"buf").get(pipe.view)!=pipe.buffer){gap="PROCESS_BUFFERED_PIPE_ASSOCIATION_CHANGED";continue;}
                        field(BufferedOutputStream.class,"count").setInt(pipe.view,0);((BufferedOutputStream)pipe.view).close();
                        if(!field(FilterOutputStream.class,"closed").getBoolean(pipe.view)){gap="PROCESS_BUFFERED_OUTPUT_RELEASE_PENDING";continue;}
                    }
                }
                if(pipe.descriptor.valid()){gap="PROCESS_ORIGINAL_PIPE_RELEASE_PENDING";continue;}
                if(pipe.buffer!=null)Arrays.fill(pipe.buffer,(byte)0);pipe.released=true;
            }
            if(!gap.isEmpty())return gap;
            if(record.cleaner==null)return "PROCESS_CLEANER_ASSOCIATION_UNOBSERVED";
            record.cleaner.clean();if(record.handleClosed){record.retired=true;return "";}return "PROCESS_NATIVE_HANDLE_RELEASE_PENDING";
        }catch(ReflectiveOperationException|IOException|RuntimeException failed){return "PROCESS_RELEASE_FAILED:"+failed.getClass().getSimpleName()+":"+failed.getMessage();}
    }
    private static void reap(){
        for(int i=0;i<32;i++){Key key=(Key)RETIRED.poll();if(key==null)return;STREAMS.remove(key);WRAPPERS.remove(key);CHANNELS.remove(key);ATTACHMENTS.remove(key);PROCESSES.remove(key);PROCESS_VIEWS.remove(key);BUFFERS.remove(key);BUFFER_CLEANERS.remove(key);BUFFER_ACTIONS.remove(key);}
    }
    public static void descriptorAttaching(FileDescriptor descriptor)throws IOException {
        platform(FileDescriptor.class,"attach");
        synchronized(STREAMS){DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));
            if(record!=null&&record.sealed)throw new IOException("RONOVA_RETIRED_FILE_DESCRIPTOR");}
    }
    public static void descriptorClosing(FileDescriptor descriptor)throws IOException {
        platform(FileDescriptor.class,"close");
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{if(!TaskBridge.recoveryWriter()&&(!TaskBridge.taskEffectAllowed(null,"file-close")||!descriptorReleaseAllowed(descriptor)))throw new IOException("RONOVA_FILE_DESCRIPTOR_CLOSE_REFUSED");}
        finally{FILE_OBSERVING.remove();}
    }
    private static void platform(Class<?> expected,String method){
        var caller=WALKER.walk(PLATFORM_BOUNDARY);
        if(caller.getDeclaringClass()!=expected||!caller.getMethodName().equals(method))
            throw new SecurityException("ACTUAL_FILE_RESOURCE_BOUNDARY_REQUIRED");
    }
    /** Called after the actual synchronized FileDescriptor.attach has published its parent. */
    public static void attached(FileDescriptor descriptor,Closeable stream){
        platform(FileDescriptor.class,"attach");
        synchronized(STREAMS){
            reap();var aliases=ATTACHMENTS.computeIfAbsent(new Key(descriptor,true),ignored->new DescriptorRecord()).aliases;
            for(var alias:aliases)if(alias.get()==stream)return;
            aliases.add(new WeakReference<>(stream));
        }
    }
    /** Base constructors run before a subclass or the caller can substitute its fields. */
    public static void constructed(Object stream,Class<?> declaring){
        platform(declaring,"<init>");Layout layout=LAYOUTS.get(declaring);
        if(layout==null||!declaring.isInstance(stream))throw new SecurityException("ACTUAL_FILE_CONSTRUCTOR_REQUIRED");
        synchronized(STREAMS){
            reap();Key key=new Key(stream,true);StreamRecord previous=STREAMS.get(key);
            if(previous!=null)return; // this(...) constructors must not recapture a changed descriptor.
            try{
                StreamRecord record=new StreamRecord(layout,stream);STREAMS.put(key,record);
                FileDescriptor descriptor=record.descriptor.get();
                // These exact platform file constructors allocate a new descriptor;
                // their FileDescriptor overloads have a null path and borrow instead.
                if(descriptor!=null&&record.path!=null){
                    DescriptorRecord attachment=ATTACHMENTS.get(new Key(descriptor,false));
                    if(attachment!=null)attachment.allocated=true;
                }
            }
            catch(IllegalAccessException failure){throw new IllegalStateException("FILE_CONSTRUCTOR_CAPTURE_FAILED",failure);}
        }
        resourceCreated(stream);
    }
    public static void channel(Object stream,Object actual,Class<?> declaring){
        platform(declaring,"getChannel");
        synchronized(STREAMS){
            StreamRecord record=STREAMS.get(new Key(stream,false));if(record==null)return;
            try{
                if(actual==null||record.layout.channel.get(stream)!=actual||!fileChannel(actual)
                        ||field(actual.getClass(),"parent").get(actual)!=stream
                        ||field(actual.getClass(),"fd").get(actual)!=record.descriptor.get()){
                    record.gap="STREAM_CHANNEL_ASSOCIATION_CHANGED";return;
                }
                if(record.channelSeen&&record.channel.get()!=actual){record.gap="STREAM_CHANNEL_ASSOCIATION_CHANGED";return;}
                record.channel=new WeakReference<>(actual);record.channelSeen=true;
            }catch(IllegalAccessException failure){record.gap="STREAM_CHANNEL_ASSOCIATION_UNOBSERVED";}
        }
    }
    private static void resourceCreated(Object value){
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{Module[] sources=IoBridge.currentSources();if(sources==null)TaskBridge.resourceCreated(value);else TaskBridge.resourceCreated(value,sources);}finally{FILE_OBSERVING.remove();}
    }
    /** The Windows factory has completed CreateFile and installed this exact handle in the returned descriptor. */
    public static void descriptorOpened(FileDescriptor descriptor){
        platform(FILE_FACTORY,"open");
        synchronized(descriptor){synchronized(STREAMS){
            reap();ATTACHMENTS.computeIfAbsent(new Key(descriptor,true),ignored->new DescriptorRecord()).allocated=true;
        }}
    }
    public static void fileOpening(Class<?> declaring,String method)throws IOException {
        platform(declaring,method);
        if(declaring!=FILE_FACTORY&&!LAYOUTS.containsKey(declaring))throw new SecurityException("ACTUAL_FILE_OPEN_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{if(!TaskBridge.recoveryWriter()&&!TaskBridge.taskEffectAllowed(null,"file-open")){
            if(LAYOUTS.containsKey(declaring))throw new FileNotFoundException("RONOVA_TERMINAL_FILE_OPEN_REFUSED");
            throw new IOException("RONOVA_TERMINAL_FILE_OPEN_REFUSED");
        }}
        finally{FILE_OBSERVING.remove();}
    }
    /** Reserve the actual descriptor immediately after the channel's superclass constructor. */
    public static void fileChannelConstructing(Object channel,FileDescriptor descriptor,String path,Object parent)throws IOException {
        platform(FILE_CHANNEL,"<init>");
        if(!fileChannel(channel)||descriptor==null)throw new IOException("FILE_CHANNEL_ORIGINAL_DESCRIPTOR_REQUIRED");
        synchronized(descriptor){synchronized(STREAMS){
            reap();DescriptorRecord attachment=ATTACHMENTS.computeIfAbsent(new Key(descriptor,true),ignored->new DescriptorRecord());
            if(attachment.sealed)throw new IOException("RONOVA_RETIRED_FILE_DESCRIPTOR");
            Key key=new Key(channel,true);if(CHANNELS.containsKey(key))throw new IllegalStateException("FILE_CHANNEL_CONSTRUCTOR_REENTERED");
            try{ChannelRecord record=new ChannelRecord(channel,descriptor,path,parent);CHANNELS.put(key,record);attachment.channels.add(record.channel);}
            catch(IllegalAccessException failure){throw new IOException("FILE_CHANNEL_HANDLE_CAPTURE_FAILED",failure);}
        }}
    }
    public static Runnable fileChannelCleanup(Object channel,Runnable action){
        platform(FILE_CHANNEL,"<init>");
        synchronized(STREAMS){
            ChannelRecord record=CHANNELS.get(new Key(channel,false));
            try{
                if(record==null||record.parentPresent||action==null||action.getClass()!=FILE_CLOSER||CLOSER_FD.get(action)!=record.descriptor.get())
                    throw new IllegalStateException("FILE_CHANNEL_CLEANUP_ASSOCIATION_CHANGED");
                ChannelCleanup cleanup=new ChannelCleanup(record,action);record.cleanup=new WeakReference<>(cleanup);return cleanup;
            }catch(IllegalAccessException failure){throw new IllegalStateException("FILE_CHANNEL_CLEANUP_UNOBSERVED",failure);}
        }
    }
    public static void fileChannelConstructed(Object channel){
        platform(FILE_CHANNEL,"<init>");
        synchronized(STREAMS){
            ChannelRecord record=CHANNELS.get(new Key(channel,false));
            if(record==null)throw new IllegalStateException("FILE_CHANNEL_CONSTRUCTION_UNOBSERVED");
            try{
                if(CHANNEL_FD.get(channel)!=record.descriptor.get()||CHANNEL_PARENT.get(channel)!=record.parent.get()
                        ||!Objects.equals(CHANNEL_PATH.get(channel),record.path))throw new IllegalStateException("FILE_CHANNEL_ORIGINAL_ASSOCIATION_CHANGED");
                Cleaner.Cleanable cleaner=(Cleaner.Cleanable)CHANNEL_CLOSER.get(channel);
                if(!record.parentPresent&&(cleaner==null||record.cleanup.get()==null))throw new IllegalStateException("FILE_CHANNEL_CLEANUP_UNOBSERVED");
                record.cleaner=new WeakReference<>(cleaner);record.gap="";
                Object parent=record.parent.get();StreamRecord stream=parent==null?null:STREAMS.get(new Key(parent,false));
                if(stream!=null){stream.channel=new WeakReference<>(channel);stream.channelSeen=true;}
            }catch(IllegalAccessException failure){throw new IllegalStateException("FILE_CHANNEL_CONSTRUCTION_CAPTURE_FAILED",failure);}
        }
        resourceCreated(channel);
    }
    /** These scopes include real Java I/O calls and lazy channel publication, not object names or thread ownership. */
    public static Object fileOperation(Object value,Class<?> declaring,String method)throws IOException {
        platform(declaring,method);
        if(declaring!=FILE_CHANNEL&&!LAYOUTS.containsKey(declaring))throw new SecurityException("ACTUAL_FILE_OPERATION_REQUIRED");
        return fileOperation(value,declaring,method,null);
    }
    private static Object nativeIoOperation(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources)throws IOException {
        if(!NativeControl.ioOperationBoundary(declaring,method,arguments,references,sources))throw new SecurityException("ACTUAL_NATIVE_IO_OPERATION_REQUIRED");
        return IoBridge.nativeInvoking(declaring,method,arguments,references,sources);
    }
    private static void nativeIoFinished(Object token,boolean completed){
        if(!NativeControl.fileOperationFinishing(token))throw new SecurityException("ACTUAL_NATIVE_IO_RETURN_REQUIRED");
        IoBridge.nativeReturned(token,completed);
    }
    /** Entered only while the actual JNI binding keeps this receiver and source tuple live. */
    public static Object nativeFileOperation(Object value,Class<?> declaring,String method,Module[] sources,int operation,long address,long length)throws IOException {
        if(!NativeControl.fileOperationBoundary(value,declaring,method,sources,operation,address,length))throw new SecurityException("ACTUAL_NATIVE_FILE_OPERATION_REQUIRED");
        if(operation==7){IoBridge.nativeSocketClosing(declaring,method,address,sources);return null;}
        if(operation==6){
            synchronized(STREAMS){for(BufferStorage storage:BUFFER_ACTIONS.values())
                if(storage.descriptor!=null&&!storage.cleaned&&storage.address==address&&BUFFER_CLEANING.get()!=storage)
                    throw new IOException("MAPPING_ORIGINAL_CLEANUP_REQUIRED");}
            return null;
        }
        if(operation==2){
            if(!TaskBridge.recoveryWriter()){
                if(!TaskBridge.taskEffectAllowed(null,"file-open"))throw new FileNotFoundException("RONOVA_TERMINAL_FILE_OPEN_REFUSED");
                synchronized(STREAMS){if(fileRecord(value)!=null)throw new FileNotFoundException("FILE_DESCRIPTOR_REOPEN_REFUSED");}
            }
            return null;
        }
        if(operation==4&&IoBridge.closingDescriptor((FileDescriptor)value))return null;
        if(operation==4&&!TaskBridge.recoveryWriter())for(Module source:sources)if(source!=null&&TaskBridge.modStopped(source))throw new IOException("RONOVA_TERMINAL_FILE_CLOSE_REFUSED");
        if(operation==4&&!TaskBridge.recoveryWriter()&&(!TaskBridge.taskEffectAllowed(value,"file-close")||!descriptorReleaseAllowed((FileDescriptor)value)))throw new IOException("RONOVA_FILE_DESCRIPTOR_CLOSE_REFUSED");
        try{return fileOperation(value,declaring,method,sources);}
        catch(IOException failure){
            if(operation!=5)throw failure;
            SyncFailedException sync=new SyncFailedException(failure.getMessage());sync.initCause(failure);throw sync;
        }
    }
    private static Object fileOperation(Object value,Class<?> declaring,String method,Module[] nativeSources)throws IOException {
        if(FILE_OBSERVING.get()!=null)return null;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{
            if(TaskBridge.recoveryWriter())return null;
            if(!TaskBridge.taskEffectAllowed(null,"file-io"))throw new IOException("RONOVA_TERMINAL_FILE_IO_REFUSED");
            FileRecord record;
            synchronized(STREAMS){
                record=fileRecord(value);
                if(record==null&&nativeSources!=null&&value instanceof FileDescriptor)
                    record=ATTACHMENTS.computeIfAbsent(new Key(value,true),ignored->new DescriptorRecord());
                if(record==null)return null; // Pre-install resources retain ordinary behaviour and remain unobserved for release.
                if(record.sealed)throw new ClosedChannelException();
                record.observing++;
            }
            try{
                boolean known;
                if(record instanceof DescriptorRecord descriptor){
                    Module[] sources=TaskBridge.resourceOperationSources(nativeSources);
                    known=sources.length!=0;
                    synchronized(STREAMS){for(Module source:sources){if(source==null)known=false;else descriptor.nativeSources.add(source);}}
                }else known=nativeSources==null?TaskBridge.resourceExposed(value):TaskBridge.resourceExposed(value,nativeSources);
                FileOperation token=new FileOperation(record,declaring,method);
                synchronized(STREAMS){if(!known)record.unknownUse=true;record.active++;return token;}
            }finally{synchronized(STREAMS){record.observing--;}}
        }finally{FILE_OBSERVING.remove();}
    }
    public static void fileOperationFinished(Object value){
        if(value==null)return;
        if(!(value instanceof FileOperation token)||token.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_FILE_OPERATION_SCOPE_REQUIRED");
        platform(token.declaring,token.method);
        synchronized(STREAMS){if(!token.closed){token.closed=true;token.record.active--;if(token.record instanceof ChannelRecord record)releaseChannelAlias(record);}}
    }
    public static void nativeFileOperationFinished(Object value){
        if(!NativeControl.fileOperationFinishing(value)||!(value instanceof FileOperation token)||token.caller!=Thread.currentThread())
            throw new SecurityException("ACTUAL_NATIVE_FILE_OPERATION_SCOPE_REQUIRED");
        synchronized(STREAMS){if(!token.closed){token.closed=true;token.record.active--;if(token.record instanceof ChannelRecord record)releaseChannelAlias(record);}}
    }
    public static void fileClosing(Object value,Class<?> declaring,String method)throws IOException {
        platform(declaring,method);
        if(declaring==AbstractInterruptibleChannel.class){if(!fileChannel(value))return;}
        else if(!LAYOUTS.containsKey(declaring))throw new SecurityException("ACTUAL_FILE_CLOSE_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{if(!TaskBridge.recoveryWriter()&&(!TaskBridge.taskEffectAllowed(null,"file-close")||!TaskBridge.resourceReleaseAllowed(value)))throw new IOException("RONOVA_FILE_CLOSE_REFUSED");}
        finally{FILE_OBSERVING.remove();}
    }
    public static void fileChannelClosed(Object channel){
        platform(FILE_CHANNEL,"implCloseChannel");
        synchronized(STREAMS){ChannelRecord record=CHANNELS.get(new Key(channel,false));if(record!=null){record.closeCompleted=true;releaseChannelAlias(record);}}
    }
    /** STREAMS is held; the final operation exit, explicit close and cleaner share this removal. */
    private static void releaseChannelAlias(ChannelRecord record){
        if(record.active!=0||record.observing!=0||!record.closeCompleted&&!(record.cleanupFinished&&record.channel.get()==null))return;
        FileDescriptor descriptor=record.descriptor.get();DescriptorRecord attachment=descriptor==null?null:ATTACHMENTS.get(new Key(descriptor,false));
        if(attachment!=null)attachment.channels.remove(record.channel);
    }
    private static FileRecord fileRecord(Object value){
        Key key=new Key(value,false);FileRecord record=STREAMS.get(key);if(record==null)record=CHANNELS.get(key);
        if(record==null)record=BUFFERS.get(key);if(record==null)record=BUFFER_ACTIONS.get(key);
        if(record==null)record=WRAPPERS.get(key);
        if(record==null)record=IoBridge.record(value);
        return record!=null?record:value instanceof FileDescriptor?ATTACHMENTS.get(key):null;
    }
    private static boolean descriptorReleaseAllowed(FileDescriptor descriptor){
        if(!TaskBridge.resourceReleaseAllowed(descriptor))return false;
        List<Object> aliases=new ArrayList<>();
        synchronized(STREAMS){
            DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));
            if(record!=null){
                for(var reference:record.aliases){Object value=reference.get();if(value!=null)aliases.add(value);}
                for(var reference:record.channels){Object value=reference.get();if(value!=null)aliases.add(value);}
            }
        }
        synchronized(STREAMS){for(BufferStorage storage:BUFFER_ACTIONS.values())if(storage.descriptor==descriptor)
            for(var reference:storage.views){Buffer buffer=reference.get();if(buffer!=null)aliases.add(buffer);}}
        aliases.addAll(IoBridge.descriptorAliases(descriptor));
        for(Object alias:aliases)if(!TaskBridge.resourceReleaseAllowed(alias))return false;return true;
    }
    static Object ioLock(){return STREAMS;}
    static boolean ioDescriptorCloseAllowed(FileDescriptor descriptor){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_DESCRIPTOR_CLOSE_REQUIRED");
        return IoBridge.closingDescriptor(descriptor)||descriptorReleaseAllowed(descriptor);
    }
    static void sealIoDescriptor(FileDescriptor descriptor,Set<Module> modules){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_RETIREMENT_REQUIRED");
        synchronized(STREAMS){DescriptorRecord record=ATTACHMENTS.computeIfAbsent(new Key(descriptor,true),ignored->new DescriptorRecord());
            if(record.active!=0||record.observing!=0)throw new IllegalStateException("IO_DESCRIPTOR_OPERATION_PENDING");
            if(record.unknownUse)throw new IllegalStateException("IO_DESCRIPTOR_SOURCE_UNOBSERVED");
            for(Module source:record.nativeSources)if(!modules.contains(source))throw new IllegalStateException("IO_DESCRIPTOR_SHARED");record.sealed=true;}
    }
    public static boolean ioResource(Object value){return IoBridge.resource(value);}
    static String ioDescriptorRetirement(FileDescriptor descriptor,Predicate<Object> selected,Set<Module> modules){
        List<Object> views=new ArrayList<>();
        synchronized(STREAMS){
            DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));
            if(record!=null){
                if(record.active!=0||record.observing!=0)return "IO_DESCRIPTOR_OPERATION_PENDING";
                if(record.unknownUse)return "IO_DESCRIPTOR_SOURCE_UNOBSERVED";
                for(Module source:record.nativeSources)if(!modules.contains(source))return "SHARED";
                for(var reference:record.aliases){Object value=reference.get();if(value!=null)views.add(value);}
                for(var reference:record.channels){Object value=reference.get();if(value!=null)views.add(value);}
            }
            for(BufferStorage storage:BUFFER_ACTIONS.values())if(storage.descriptor==descriptor&&!storage.cleaned)return "IO_MAPPING_RELEASE_PENDING";
        }
        for(Object view:views)if(!selected.test(view))return "SHARED";
        return "";
    }
    private static void bufferSources(BufferStorage storage,Module[] sources){
        if(sources.length==0)storage.unknownUse=true;
        for(Module source:sources){if(source==null)storage.unknownUse=true;else storage.sources.add(source);}
    }
    public static void mappingExposed(Object action,String method){
        platform(BufferPlatform.UNMAPPER,method);
        if(!method.equals("address")&&!method.equals("fileDescriptor"))throw new SecurityException("ACTUAL_MAPPING_ACCESSOR_REQUIRED");
        if(FILE_OBSERVING.get()!=null||TaskBridge.recoveryWriter())return;
        if(!TaskBridge.taskEffectAllowed(action,"mapping-address"))throw new IllegalStateException("RONOVA_TERMINAL_MAPPING_ADDRESS");
        synchronized(STREAMS){BufferStorage storage=BUFFER_ACTIONS.get(new Key(action,false));if(storage==null)return;
            if(storage.sealed||storage.cleaned)throw new IllegalStateException("RONOVA_RETIRED_MAPPING_ADDRESS");
            storage.escaped=true;}
    }
    public static void mappingCreated(Object value,Object channel){
        platform(FILE_CHANNEL,"mapInternal");if(value==null||FILE_OBSERVING.get()!=null)return;
        if(!fileChannel(channel)||!(value instanceof Runnable action)||!BufferPlatform.UNMAPPER.isInstance(value))throw new SecurityException("ACTUAL_MAPPING_RESULT_REQUIRED");
        FILE_OBSERVING.set(Boolean.TRUE);
        try{
            Module[] sources=TaskBridge.resourceOperationSources(null);
            synchronized(STREAMS){
                Key key=new Key(action,true);BufferStorage storage=BUFFER_ACTIONS.get(key);
                if(storage==null){storage=new BufferStorage(action,true);storage.gap="";BUFFER_ACTIONS.put(key,storage);}
                bufferSources(storage,sources);
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("MAPPING_ORIGINAL_ASSOCIATION_UNAVAILABLE",failure);}
        finally{FILE_OBSERVING.remove();}
        resourceCreated(value);
    }
    public static Runnable bufferCleanup(Object value,Runnable action){
        platform(BufferPlatform.DIRECT,"<init>");if(action==null||FILE_OBSERVING.get()!=null)return action;
        if(!(value instanceof Buffer buffer))throw new SecurityException("ACTUAL_BUFFER_REFERENT_REQUIRED");
        if(action.getClass()!=BufferPlatform.DEALLOCATOR&&!BufferPlatform.UNMAPPER.isInstance(action))return action;
        FILE_OBSERVING.set(Boolean.TRUE);BufferStorage storage;
        try{
            Module[] sources=TaskBridge.resourceOperationSources(null);
            synchronized(STREAMS){
                Key key=new Key(action,true);storage=BUFFER_ACTIONS.get(key);
                if(storage==null){storage=new BufferStorage(action,action.getClass()==BufferPlatform.DEALLOCATOR);BUFFER_ACTIONS.put(key,storage);}
                if(storage.sealed||storage.cleaned)throw new IllegalStateException("BUFFER_STORAGE_ALREADY_RETIRED");
                bufferSources(storage,sources);BufferView view=new BufferView(buffer,storage,null);
                BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_ORIGINAL_CLEANUP_UNAVAILABLE",failure);}
        finally{FILE_OBSERVING.remove();}
        if(action.getClass()==BufferPlatform.DEALLOCATOR){
            BufferRegistration previous=BUFFER_REGISTERING.get();BUFFER_REGISTERING.set(new BufferRegistration(buffer,storage));
            try{long[] binding=NativeControl.bindBufferStorage(buffer,action,storage.address,storage.bytes);
                synchronized(STREAMS){storage.memoryToken=binding[0];storage.memoryGeneration=binding[1];}}
            finally{if(previous==null)BUFFER_REGISTERING.remove();else BUFFER_REGISTERING.set(previous);}
        }
        resourceCreated(action);return new BufferCleanup(storage);
    }
    public static Object bufferAllocationEnter(Buffer buffer){
        platform(BufferPlatform.DIRECT,"<init>");BufferAllocation scope=new BufferAllocation(buffer);BUFFER_ALLOCATING.set(scope);return scope;
    }
    public static void bufferAllocationExit(Object value,Throwable failure){
        if(!(value instanceof BufferAllocation scope)||scope.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_BUFFER_CONSTRUCTION_SCOPE_REQUIRED");
        platform(BufferPlatform.DIRECT,"<init>");if(scope.closed)return;
        if(BUFFER_ALLOCATING.get()!=scope)throw new SecurityException("ACTUAL_BUFFER_CONSTRUCTION_ORDER_REQUIRED");scope.closed=true;
        if(scope.previous==null)BUFFER_ALLOCATING.remove();else BUFFER_ALLOCATING.set(scope.previous);
    }
    static boolean bufferBindingActive(Buffer buffer,Object action,long address,long bytes){
        if(WALKER.getCallerClass()!=NativeControl.class)return false;BufferRegistration scope=BUFFER_REGISTERING.get();
        return scope!=null&&scope.buffer==buffer&&scope.storage.original==action&&scope.storage.address==address&&scope.storage.bytes==bytes;
    }
    static boolean bufferRestoreActive(Object action,long token,long generation,Module[] modules,long cursor,int budget){
        if(WALKER.getCallerClass()!=NativeControl.class||!TaskBridge.recoveryWriter())return false;BufferRestore scope=BUFFER_RESTORING.get();
        return scope!=null&&scope.storage.restoring&&scope.storage.original==action&&scope.storage.memoryToken==token
                &&scope.storage.memoryGeneration==generation&&scope.modules==modules&&scope.cursor==cursor&&scope.budget==budget;
    }
    static boolean bufferCleanupOwner(Object owner){BufferStorage storage=BUFFER_CLEANING.get();return storage!=null&&storage.cleaning&&storage.original!=null&&owner==BufferPlatform.UNSAFE;}
    public static void bufferCleaner(Object cleaner,Buffer buffer){
        platform(BufferPlatform.DIRECT,"<init>");if(cleaner==null)return;
        if(cleaner.getClass()!=BufferPlatform.CLEANER)throw new SecurityException("ACTUAL_BUFFER_CLEANER_REQUIRED");
        synchronized(STREAMS){BufferView view=BUFFERS.get(new Key(buffer,false));if(view==null)return;
            view.storage.cleaner=new WeakReference<>(cleaner);BUFFER_CLEANERS.put(new Key(cleaner,true),view.storage);}
    }
    public static void bufferConstructed(Buffer buffer,Class<?> declaring){
        platform(declaring,"<init>");if(!BufferPlatform.TYPES.contains(declaring))throw new SecurityException("ACTUAL_BUFFER_CONSTRUCTOR_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return;FILE_OBSERVING.set(Boolean.TRUE);
        try{
            if(!buffer.isDirect())return;
            Field parentField=BufferPlatform.PARENT.get(buffer.getClass());Object parent=parentField==null?null:CodeSourceBridge.controlField(parentField,buffer);
            Module[] sources=TaskBridge.resourceOperationSources(null);
            synchronized(STREAMS){
                reap();BufferView view=BUFFERS.get(new Key(buffer,false));
                if(view==null){
                    BufferView owner=parent instanceof Buffer?BUFFERS.get(new Key(parent,false)):null;
                    BufferStorage storage=owner==null?new BufferStorage(null,(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer)==0&&(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)==0):owner.storage;
                    awaitBufferRestore(storage);
                    if(storage.sealed||storage.cleaned)throw new IllegalStateException("BUFFER_PARENT_RETIRED");
                    view=new BufferView(buffer,storage,parent);BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;
                }
                BufferStorage storage=view.storage;
                if(view.address!=(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)||view.parent.get()!=parent)throw new IllegalStateException("BUFFER_VIEW_ASSOCIATION_CHANGED");
                if(storage.original!=null&&(view.address<storage.address||view.bytes<0||view.bytes>storage.bytes||view.address-storage.address>storage.bytes-view.bytes))
                    throw new IllegalStateException("BUFFER_VIEW_OUTSIDE_ORIGINAL_STORAGE");
                if(parent==null&&storage.original!=null&&CodeSourceBridge.controlField(BufferPlatform.CLEANUP,buffer)!=storage.cleaner.get())throw new IllegalStateException("BUFFER_CLEANER_ASSOCIATION_CHANGED");
                view.constructed=true;bufferSources(storage,sources);
                storage.gap=storage.created?"":"BUFFER_ORIGINAL_STORAGE_UNOBSERVED";
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_VIEW_CAPTURE_FAILED",failure);}
        finally{FILE_OBSERVING.remove();}
        resourceCreated(buffer);
    }
    private static void addBuffer(List<Buffer> values,Object value){
        if(value instanceof Buffer buffer){for(Buffer seen:values)if(seen==buffer)return;values.add(buffer);}
        else if(value instanceof Buffer[] buffers)for(Buffer buffer:buffers)if(buffer!=null)addBuffer(values,buffer);
    }
    private static boolean bufferBorrowed(BufferStorage storage){
        for(BufferOperation operation=BUFFER_OPERATIONS.get();operation!=null;operation=operation.previous)
            for(BufferUse use:operation.uses)if(use.view.storage==storage)return true;
        return findIoBufferUse(IoBridge.currentBufferLeases(),storage,0,0)!=null;
    }
    private static BufferUse findIoBufferUse(IoBridge.BufferLeases leases,BufferStorage storage,long address,long bytes){
        for(IoBridge.BufferLeases group=leases;group!=null;group=group.next){
            BufferUse found=ioBufferUse(group.lease,storage,address,bytes);if(found!=null)return found;
            for(IoBridge.TemporaryLease entry=group.first;entry!=null;entry=entry.next){
                found=ioBufferUse(entry.lease,storage,address,bytes);if(found!=null)return found;
                if(entry==group.last)break;
            }
        }
        return null;
    }
    private static BufferUse ioBufferUse(Object value,BufferStorage storage,long address,long bytes){
        if(value instanceof IoBufferLease lease&&!lease.released)for(int index=0;index<lease.uses.size();index++){
            BufferUse use=lease.uses.get(index);
            if(use.returned)continue;
            BufferView view=use.view;
            if(storage!=null?view.storage==storage:address>=view.address&&bytes>0&&bytes<=view.bytes&&address-view.address<=view.bytes-bytes)return use;
        }
        return null;
    }
    private static void awaitBufferRestore(BufferStorage storage){
        boolean interrupted=false;
        try{while(storage.restoring)try{STREAMS.wait();}catch(InterruptedException retry){interrupted=true;}}
        finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    private static int elementBytes(Buffer buffer){
        return buffer instanceof ByteBuffer?1:buffer instanceof CharBuffer||buffer instanceof ShortBuffer?2:buffer instanceof IntBuffer||buffer instanceof FloatBuffer?4:8;
    }
    private static Object bufferArray(Buffer buffer)throws IllegalAccessException {
        for(Field field:BufferPlatform.ARRAYS.get(buffer.getClass())){Object array=CodeSourceBridge.controlField(field,buffer);if(array!=null)return array;}
        Field parent=BufferPlatform.PARENT.get(buffer.getClass());Object value=parent==null?null:CodeSourceBridge.controlField(parent,buffer);
        return value instanceof Buffer owner&&owner!=buffer?bufferArray(owner):null;
    }
    private static BufferSpan bufferSpan(Buffer buffer,long index,long bytes)throws IllegalAccessException {
        int width=elementBytes(buffer);long limit=(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,buffer);
        if(index<0||index>limit||bytes<=0||bytes>(limit-index)*width)return null;
        BufferView view;synchronized(STREAMS){view=BUFFERS.get(new Key(buffer,false));}
        if(view!=null){long offset=index*width;if(bytes>view.bytes||offset>view.bytes-bytes)return null;return new BufferSpan(null,view.storage,view.address+offset,bytes);}
        Object array=bufferArray(buffer);if(array==null)return null;long[] layout=arrayLayout(array.getClass());
        long address=(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)+index*width,available=(long)java.lang.reflect.Array.getLength(array)*layout[1];
        if(address<layout[0]||bytes>available||address-layout[0]>available-bytes)return null;
        return new BufferSpan(array,null,address,bytes);
    }
    private static int valueBytes(Buffer buffer,String method){
        if(!(buffer instanceof ByteBuffer))return elementBytes(buffer);
        return switch(method){case "getChar","getShort","putChar","putShort"->2;case "getInt","getFloat","putInt","putFloat"->4;
            case "getLong","getDouble","putLong","putDouble"->8;default->1;};
    }
    private static boolean contains(BufferSpan outer,BufferSpan inner){
        return outer.holder==inner.holder&&outer.storage==inner.storage&&inner.address>=outer.address&&inner.bytes<=outer.bytes&&inner.address-outer.address<=outer.bytes-inner.bytes;
    }
    private static Module[] bufferOperationSources(BufferOperation operation){
        // This snapshot belongs only to this entering operation. Storage use
        // consumes it immediately; a data-only path asks only after finding a
        // real span that will have a receipt. No later operation inherits it.
        if(operation.sources==null)operation.sources=TaskBridge.resourceOperationSources(IoBridge.currentSources());
        return operation.sources;
    }
    private static BufferRead bufferRead(Buffer buffer,Object[] arguments,String method,BufferOperation operation){
        if(!BufferPlatform.READS.contains(method))return null;
        try{
            long position=(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,buffer),index=position,bytes=valueBytes(buffer,method);int width=elementBytes(buffer);
            if(arguments.length>0&&arguments[0] instanceof Integer absolute)index=method.equals("charAt")?position+absolute:absolute;
            else if(arguments.length>0&&arguments[0] instanceof Long address&&buffer instanceof ByteBuffer)index=address-(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer);
            Object destination=null;long destinationOffset=0;
            for(int at=0;at<arguments.length;at++)if(arguments[at]!=null&&arguments[at].getClass().isArray()){
                Object array=arguments[at];long count=java.lang.reflect.Array.getLength(array),offset=0;
                if(arguments.length>at+2&&arguments[at+1] instanceof Integer&&arguments[at+2] instanceof Integer){offset=(Integer)arguments[at+1];count=(Integer)arguments[at+2];}
                if(offset<0||count<0||offset>java.lang.reflect.Array.getLength(array)-count)return null;
                long[] layout=arrayLayout(array.getClass());if(layout==null||layout[1]!=width)return null;
                destination=array;destinationOffset=layout[0]+offset*width;bytes=count*width;break;
            }
            BufferSpan source=bufferSpan(buffer,index,bytes);if(source==null)return null;
            Module[] sources=bufferOperationSources(operation);
            Object receipt=destination==null?TaskBridge.bufferReadBefore(source.holder,source.address,source.bytes,sources)
                    :TaskBridge.bufferDataBefore(destination,destinationOffset,bytes,source.holder,source.address,source.bytes,true,sources);
            try{return new BufferRead(receipt,TaskBridge.bufferReadSources(receipt));}
            catch(RuntimeException|Error failure){try{TaskBridge.bufferDataFinished(receipt,false);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_READ_LAYOUT_UNAVAILABLE",failure);}
    }
    private static BufferWrite bufferWrite(Buffer buffer,Object[] arguments,String method,BufferOperation operation){
        if(!method.startsWith("put")&&!method.equals("_put")&&!method.equals("compact"))return null;
        try{
            int width=elementBytes(buffer);long index=(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,buffer),limit=(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,buffer),count=1;
            Object source=null;long sourceOffset=0,sourceBytes=0;boolean copy=false,unknownSource=false;Module[] valueSources=null;
            if(method.equals("compact")){
                count=limit-index;BufferSpan input=bufferSpan(buffer,index,count*width);if(input==null)return null;
                source=input.holder;sourceOffset=input.address;sourceBytes=input.bytes;copy=true;index=0;
            }
            else{
                if(arguments.length>1&&arguments[0] instanceof Integer absolute)index=absolute;
                else if(arguments.length>1&&arguments[0] instanceof Long address&&buffer instanceof ByteBuffer){
                    index=address-(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer);
                    if(index<0||index>(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer))throw new IllegalStateException("BUFFER_WRITE_OUTSIDE_ACTUAL_STORAGE");
                }
                for(int at=0;at<arguments.length;at++){
                    Object value=arguments[at];
                    if(value instanceof String text&&buffer instanceof CharBuffer){
                        long from=0,end=text.length();if(arguments.length>at+2){from=(Integer)arguments[at+1];end=(Integer)arguments[at+2];}
                        if(from<0||end<from||end>text.length())return null;count=end-from;
                        source=BufferPlatform.STRING_VALUE.get(text);int inputWidth=1<<BufferPlatform.STRING_CODER.getByte(text);long[] layout=arrayLayout(source.getClass());
                        sourceOffset=layout[0]+from*inputWidth;sourceBytes=count*inputWidth;copy=true;
                        valueSources=TaskBridge.creationSources(text);break;
                    }
                    if(!(value instanceof Buffer)&&!(value!=null&&value.getClass().isArray()))continue;
                    long from=0;boolean explicit=arguments.length>at+2&&arguments[at+1] instanceof Integer&&arguments[at+2] instanceof Integer;
                    if(explicit){from=(Integer)arguments[at+1];count=(Integer)arguments[at+2];}
                    if(value instanceof Buffer input){
                        int inputWidth=elementBytes(input);long inputLimit=(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,input);
                        if(!explicit){from=(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,input);count=inputLimit-from;}
                        if(inputWidth!=width||from<0||count<0||from>inputLimit-count)return null;
                        BufferSpan span=bufferSpan(input,from,count*width);
                        if(span!=null){source=span.holder;sourceOffset=span.address;sourceBytes=span.bytes;copy=true;}
                        else unknownSource=true;
                    }else{
                        long available=java.lang.reflect.Array.getLength(value);if(!explicit)count=available;
                        if(from<0||count<0||from>available-count)return null;
                        long[] layout=arrayLayout(value.getClass());if(layout==null||layout[1]!=width)return null;
                        source=value;sourceOffset=layout[0]+from*width;sourceBytes=count*width;copy=true;
                    }
                    break;
                }
            }
            long bytes=count*width;
            if(!copy&&count==1)bytes=valueBytes(buffer,method);
            BufferSpan destination=bufferSpan(buffer,index,bytes);if(destination==null)return null;
            for(BufferOperation outer=BUFFER_OPERATIONS.get();outer!=null;outer=outer.previous){BufferWrite write=outer.write;
                if(write!=null&&contains(write.span,destination))return null;}
            Module[] sources=bufferOperationSources(operation);
            if(valueSources!=null&&valueSources.length!=0){
                Set<Module> combined=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(combined,sources);Collections.addAll(combined,valueSources);sources=combined.toArray(Module[]::new);
            }
            if(unknownSource)sources=Arrays.copyOf(sources,sources.length+1);
            Object receipt=TaskBridge.bufferDataBefore(destination.holder,destination.address,bytes,source,sourceOffset,sourceBytes,copy,sources);
            return new BufferWrite(destination,receipt);
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_WRITE_LAYOUT_UNAVAILABLE",failure);}
    }
    /** Detached leases follow the actual I/O request across its submission and completion threads. */
    static Object borrowIoBuffers(Object[] arguments,Module[] sources){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_BUFFER_BORROW_REQUIRED");
        return borrowIoBuffers(arguments,sources,false);
    }
    static Object borrowIoTemporary(Buffer buffer,Module[] sources){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_TEMPORARY_BUFFER_REQUIRED");
        return borrowIoBuffers(new Object[]{buffer},sources,true);
    }
    private static BufferView borrowedBufferView(Buffer buffer){
        BufferView view=BUFFERS.get(new Key(buffer,false));if(view!=null||!buffer.isDirect())return view;
        try{
            Field field=BufferPlatform.PARENT.get(buffer.getClass());Object parent=field==null?null:CodeSourceBridge.controlField(field,buffer);
            BufferView owner=parent instanceof Buffer?BUFFERS.get(new Key(parent,false)):null;
            BufferStorage storage=owner==null?new BufferStorage(null,false):owner.storage;
            view=new BufferView(buffer,storage,parent);view.unknownUse=true;
            if(owner==null){storage.unknownUse=true;storage.gap="BUFFER_ORIGINAL_STORAGE_UNOBSERVED";}
            BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;return view;
        }catch(IllegalAccessException failure){throw new IllegalStateException("IO_BUFFER_VIEW_UNAVAILABLE",failure);}
    }
    private static List<Buffer> ioBuffers(List<Buffer> values,Object value){
        if(value instanceof Buffer buffer){if(values==null)values=new ArrayList<>(1);addBuffer(values,buffer);}
        else if(value instanceof Buffer[] buffers)for(Buffer buffer:buffers)if(buffer!=null)values=ioBuffers(values,buffer);
        return values;
    }
    private static Object borrowIoBuffers(Object[] arguments,Module[] sources,boolean runtimeManaged){
        List<Buffer> values=null;for(Object argument:arguments)values=ioBuffers(values,argument);if(values==null)return null;
        List<BufferUse> uses=null;Boolean previous=FILE_OBSERVING.get();FILE_OBSERVING.set(Boolean.TRUE);
        try{
            synchronized(STREAMS){
                // Validate the whole request before reserving any storage.
                for(Buffer buffer:values)borrowedBufferView(buffer);
                boolean waiting;
                do{waiting=false;for(Buffer buffer:values){BufferView view=BUFFERS.get(new Key(buffer,false));if(view!=null&&view.storage.restoring){awaitBufferRestore(view.storage);waiting=true;break;}}}while(waiting);
                for(Buffer buffer:values){BufferView view=BUFFERS.get(new Key(buffer,false));if(view==null)continue;
                    if(view.sealed||view.storage.sealed||view.storage.cleaned)throw new IllegalStateException("RONOVA_RETIRED_IO_BUFFER");
                    if(uses==null)uses=new ArrayList<>(values.size());
                    uses.add(new BufferUse(buffer,view));}
                if(uses==null)return null;
                for(BufferUse use:uses){use.view.observing++;use.view.storage.observing++;if(runtimeManaged)use.view.storage.runtimeManaged=true;}
            }
            try{
                // These views belong to this one borrow. Collect its actual
                // current context once, then record every selected identity.
                Module[] exposed=TaskBridge.resourceOperationSources(sources);
                for(BufferUse use:uses){boolean known=TaskBridge.resourceExposedByOperation(use.buffer,exposed);
                    synchronized(STREAMS){if(!known)use.view.unknownUse=true;bufferSources(use.view.storage,sources);}}
                IoBufferLease lease=new IoBufferLease(uses);
                synchronized(STREAMS){for(BufferUse use:uses){use.view.active++;use.view.storage.active++;}}
                return lease;
            }finally{synchronized(STREAMS){for(BufferUse use:uses){use.view.observing--;use.view.storage.observing--;}}}
        }finally{if(previous==null)FILE_OBSERVING.remove();else FILE_OBSERVING.set(previous);}
    }
    static void releaseIoBuffers(Object value){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_BUFFER_RELEASE_REQUIRED");
        if(value==null)return;if(!(value instanceof IoBufferLease lease))throw new SecurityException("ACTUAL_IO_BUFFER_LEASE_REQUIRED");
        synchronized(STREAMS){
            if(lease.released)return;
            for(int index=0;index<lease.uses.size();index++)releaseIoUse(lease,lease.uses.get(index));
            lease.released=true;lease.uses.clear();
        }
    }
    static boolean releaseIoBuffer(Object value,Buffer buffer){
        if(WALKER.getCallerClass()!=IoBridge.class||!(value instanceof IoBufferLease lease))throw new SecurityException("ACTUAL_IO_BUFFER_RETURN_REQUIRED");
        synchronized(STREAMS){if(lease.released)return true;
            for(int index=0;index<lease.uses.size();index++){BufferUse use=lease.uses.get(index);if(use.buffer==buffer)releaseIoUse(lease,use);}
            if(lease.remaining==0){lease.released=true;lease.uses.clear();}
            return lease.released;
        }
    }
    private static void releaseIoUse(IoBufferLease lease,BufferUse use){
        if(use.returned)return;
        use.returned=true;use.view.active--;use.view.storage.active--;lease.remaining--;
    }
    static Object ioBufferWriting(IoBridge.BufferLeases leases,long[] spans,Module[] sources){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_BUFFER_WRITE_REQUIRED");
        List<IoBufferWrite> writes=new ArrayList<>();IoBufferWrite previous=IO_BUFFER_WRITE.get();
        try{
            for(int at=0;at<spans.length;at+=2){long address=spans[at],bytes=spans[at+1];if(bytes==0)continue;BufferUse found;IoBufferWrite write;
                synchronized(STREAMS){
                    found=findIoBufferUse(leases,null,address,bytes);
                    if(found==null)throw new IllegalStateException("IO_WRITE_OUTSIDE_ACTUAL_BUFFER_LEASE");
                    if(found.view.storage.sealed||found.view.storage.cleaned||found.view.storage.restoring)throw new IllegalStateException("IO_BUFFER_STORAGE_RETIRED");
                    write=new IoBufferWrite(found,address,bytes,sources,writes);
                    found.view.active++;found.view.storage.active++;
                }
                try{writes.add(write);}catch(RuntimeException|Error failure){
                    synchronized(STREAMS){write.closed=true;found.view.active--;found.view.storage.active--;}throw failure;
                }
                IO_BUFFER_WRITE.set(write);
                BufferStorage storage=found.view.storage;
                if(storage.memoryToken!=0&&storage.original!=null)write.token=NativeControl.ioBufferWrite(write,storage.original,storage.memoryToken,storage.memoryGeneration,address,bytes,sources);
                if(write.token==0)synchronized(STREAMS){storage.unknownUse=true;}
            }
            return writes;
        }catch(RuntimeException|Error failure){
            try{finishIoBufferWrites(writes,0,false);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }finally{if(previous==null)IO_BUFFER_WRITE.remove();else IO_BUFFER_WRITE.set(previous);}
    }
    static void ioBufferWritten(Object value,long bytes,boolean completed){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_BUFFER_WRITE_COMPLETION_REQUIRED");
        if(value==null)return;
        @SuppressWarnings("unchecked") List<IoBufferWrite> writes=(List<IoBufferWrite>)value;
        finishIoBufferWrites(writes,Math.max(0,bytes),completed);
    }
    private static void finishIoBufferWrites(List<IoBufferWrite> writes,long bytes,boolean completed){
        IoBufferWrite previous=IO_BUFFER_WRITE.get();Throwable failure=null;
        try{for(IoBufferWrite write:writes){
            synchronized(STREAMS){if(write.closed)continue;write.closed=true;}
            write.written=Math.min(bytes,write.bytes);bytes-=write.written;write.completed=completed;write.ending=true;IO_BUFFER_WRITE.set(write);
            try{if(write.token!=0)NativeControl.ioBufferWritten(write,write.token,write.written,completed);}
            catch(RuntimeException|Error unavailable){synchronized(STREAMS){write.use.view.storage.unknownUse=true;}if(failure==null)failure=unavailable;else failure.addSuppressed(unavailable);}
            finally{synchronized(STREAMS){write.use.view.active--;write.use.view.storage.active--;}}
        }}finally{if(previous==null)IO_BUFFER_WRITE.remove();else IO_BUFFER_WRITE.set(previous);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
    static boolean ioBufferWriteActive(Object value,Object action,long allocation,long generation,long address,long bytes,Module[] sources){
        if(WALKER.getCallerClass()!=NativeControl.class||!(value instanceof IoBufferWrite write)||IO_BUFFER_WRITE.get()!=write||write.ending)return false;
        BufferStorage storage=write.use.view.storage;
        return storage.original==action&&storage.memoryToken==allocation&&storage.memoryGeneration==generation&&write.address==address&&write.bytes==bytes&&write.sources==sources;
    }
    static boolean ioBufferWrittenActive(Object value,long token,long bytes,boolean completed){
        return WALKER.getCallerClass()==NativeControl.class&&value instanceof IoBufferWrite write&&IO_BUFFER_WRITE.get()==write&&write.ending
                &&write.token==token&&write.written==bytes&&write.completed==completed;
    }
    static Object ioBufferWriteGroup(Object value){
        return WALKER.getCallerClass()==NativeControl.class&&value instanceof IoBufferWrite write&&IO_BUFFER_WRITE.get()==write&&!write.ending?write.group:null;
    }
    static Module[] currentBufferSources(){
        if(WALKER.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_BUFFER_READ_CONTEXT_REQUIRED");
        Set<Module> sources=null;
        for(BufferOperation operation=BUFFER_OPERATIONS.get();operation!=null;operation=operation.previous)if(!operation.closed&&operation.observed!=null&&!operation.observed.isEmpty()){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.addAll(operation.observed);
        }
        return sources==null?new Module[0]:sources.toArray(Module[]::new);
    }
    /** Called under STREAMS; only live storage uses need an entry in the operation. */
    private static void observeBufferUse(List<BufferUse> uses,Object value){
        if(value instanceof Buffer buffer){
            for(BufferUse use:uses)if(use.buffer==buffer)return;
            BufferView view=BUFFERS.get(new Key(buffer,false));if(view==null)return;
            awaitBufferRestore(view.storage);
            if(view.sealed||view.storage.sealed||view.storage.cleaned)throw new IllegalStateException("RONOVA_RETIRED_BUFFER");
            uses.add(new BufferUse(buffer,view));view.observing++;view.storage.observing++;
        }else if(value instanceof Buffer[] buffers)for(Buffer buffer:buffers)if(buffer!=null)observeBufferUse(uses,buffer);
    }
    public static Object bufferOperation(Object receiver,Object[] arguments,Class<?> declaring,String method){
        platform(declaring,method);if(declaring!=FILE_CHANNEL&&!BufferPlatform.TYPES.contains(declaring))throw new SecurityException("ACTUAL_BUFFER_OPERATION_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return null;FILE_OBSERVING.set(Boolean.TRUE);
        // This private per-call list is never a business source. Avoid registering
        // two temporary ArrayLists in the global source directory for every read.
        List<BufferUse> uses=new LinkedList<>();
        try{
            if(TaskBridge.recoveryWriter())return null;
            synchronized(STREAMS){
                observeBufferUse(uses,receiver);for(Object argument:arguments)observeBufferUse(uses,argument);
            }
            if(uses.isEmpty()&&!(receiver instanceof Buffer))return null;
            if(!TaskBridge.recoveryWriter()&&!(receiver instanceof Buffer buffer&&IoBridge.finishingBuffer(buffer))&&!TaskBridge.taskEffectAllowed(receiver,"buffer-io"))throw new IllegalStateException("RONOVA_TERMINAL_BUFFER_IO");
            boolean read=BufferPlatform.READS.contains(method),write=method.startsWith("put")||method.equals("_put")||method.equals("compact");
            BufferOperation operation=new BufferOperation(declaring,method,uses);
            Module[] sources=uses.isEmpty()?null:bufferOperationSources(operation);
            for(BufferUse use:uses){
                boolean known=TaskBridge.resourceExposedByOperation(use.buffer,sources);
                synchronized(STREAMS){
                    if(!known)use.view.unknownUse=true;bufferSources(use.view.storage,sources);
                    if((method.equals("address")||method.equals("fileDescriptor"))&&receiver==use.buffer&&!bufferBorrowed(use.view.storage))use.view.storage.escaped=true;
                }
            }
            synchronized(STREAMS){for(BufferUse use:uses){use.view.active++;use.view.storage.active++;}}
            BUFFER_OPERATIONS.set(operation);
            try{if(receiver instanceof Buffer buffer){
                operation.execution=CodeSourceBridge.executionBufferCall(buffer,declaring,method);
                if(read)operation.read=bufferRead(buffer,arguments,method,operation);
                if(write)operation.write=bufferWrite(buffer,arguments,method,operation);
            }}catch(RuntimeException|Error failure){
                try{finishBufferOperation(operation,false);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
            }
            return operation;
        }finally{
            synchronized(STREAMS){for(BufferUse use:uses){use.view.observing--;use.view.storage.observing--;}}
            FILE_OBSERVING.remove();
        }
    }
    public static void bufferOperationFinished(Object value){
        finishBufferOperation(value,true);
    }
    private static void finishBufferOperation(Object value,boolean written){
        if(value==null)return;
        if(!(value instanceof BufferOperation operation)||operation.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_BUFFER_OPERATION_TOKEN_REQUIRED");
        platform(operation.declaring,operation.method);if(operation.closed)return;
        if(BUFFER_OPERATIONS.get()!=operation)throw new SecurityException("ACTUAL_BUFFER_OPERATION_ORDER_REQUIRED");
        Throwable failed=null;
        try{
            if(operation.read!=null){
                operation.observe(operation.read.before);operation.observe(TaskBridge.bufferReadSources(operation.read.receipt));
            }
            if(operation.observed!=null&&!operation.observed.isEmpty()){
                CodeSourceBridge.executionBufferObserved(operation.execution,operation.observed.toArray(Module[]::new));
                if(operation.previous!=null&&!operation.previous.closed)operation.previous.observe(operation.observed);
            }
        }catch(RuntimeException|Error failure){failed=failure;}
        finally{
            try{if(operation.write!=null)TaskBridge.bufferDataFinished(operation.write.receipt,written);}
            catch(RuntimeException|Error failure){if(failed==null)failed=failure;else if(failed!=failure)failed.addSuppressed(failure);}
            try{if(operation.read!=null)TaskBridge.bufferDataFinished(operation.read.receipt,written);}
            catch(RuntimeException|Error failure){if(failed==null)failed=failure;else if(failed!=failure)failed.addSuppressed(failure);}
            synchronized(STREAMS){operation.closed=true;operation.sources=null;for(BufferUse use:operation.uses){use.view.active--;use.view.storage.active--;}}
            if(operation.previous==null)BUFFER_OPERATIONS.remove();else BUFFER_OPERATIONS.set(operation.previous);
        }
        if(failed instanceof RuntimeException exception)throw exception;if(failed instanceof Error error)throw error;
    }
    public static void bufferOperationFailed(Object value,Throwable original){
        try{finishBufferOperation(value,false);}catch(Throwable cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}
    }
    private static boolean bufferCleanupAllowed(BufferStorage storage){
        List<Buffer> views=new ArrayList<>();long version;
        synchronized(STREAMS){
            awaitBufferRestore(storage);
            if(storage.cleaned)return true;
            if(storage.cleaning||storage.failed||storage.active!=0||storage.observing!=0)return false;
            version=storage.viewsVersion;
            for(var reference:storage.views){Buffer buffer=reference.get();if(buffer!=null)views.add(buffer);}
        }
        if(!TaskBridge.recoveryWriter()){
            if(!TaskBridge.taskEffectAllowed(null,"buffer-cleanup"))return false;
            for(Buffer buffer:views)if(!TaskBridge.resourceReleaseAllowed(buffer))return false;
        }
        synchronized(STREAMS){
            if(storage.restoring||storage.cleaning||storage.active!=0||storage.observing!=0||version!=storage.viewsVersion)return false;
            storage.sealed=true;return true;
        }
    }
    public static boolean bufferCleanerAllowed(Object cleaner){
        platform(BufferPlatform.CLEANER,"clean");BufferStorage storage;
        synchronized(STREAMS){storage=BUFFER_CLEANERS.get(new Key(cleaner,false));}
        return storage==null||bufferCleanupAllowed(storage);
    }
    public static Object bufferAction(Runnable action,Class<?> declaring){
        if(declaring!=BufferPlatform.DEALLOCATOR&&declaring!=BufferPlatform.UNMAPPER)throw new SecurityException("ACTUAL_BUFFER_CLEANUP_REQUIRED");
        platform(declaring,declaring==BufferPlatform.DEALLOCATOR?"run":"unmap");BufferStorage storage;
        synchronized(STREAMS){storage=BUFFER_ACTIONS.get(new Key(action,false));}
        if(storage==null||BUFFER_CLEANING.get()==storage)return null;
        if(!bufferCleanupAllowed(storage))return Boolean.FALSE;
        BufferAction token=new BufferAction(storage,declaring);
        synchronized(STREAMS){if(storage.cleaning)return Boolean.FALSE;storage.cleaning=true;}
        BUFFER_CLEANING.set(storage);return token;
    }
    public static void bufferActionFinished(Object value,boolean completed){
        if(value==null)return;
        if(!(value instanceof BufferAction token)||token.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_BUFFER_CLEANUP_TOKEN_REQUIRED");
        platform(token.declaring,token.declaring==BufferPlatform.DEALLOCATOR?"run":"unmap");if(token.closed)return;
        if(BUFFER_CLEANING.get()!=token.storage)throw new SecurityException("ACTUAL_BUFFER_CLEANUP_ORDER_REQUIRED");
        try{bufferCleanupResult(token.storage,completed);}
        finally{synchronized(STREAMS){token.closed=true;token.storage.cleaning=false;}if(token.previous==null)BUFFER_CLEANING.remove();else BUFFER_CLEANING.set(token.previous);}
    }
    private static void bufferCleanupResult(BufferStorage storage,boolean completed){
        synchronized(STREAMS){
            try{
                if(!completed){storage.failed=true;storage.gap="BUFFER_ORIGINAL_CLEANUP_INCOMPLETE";return;}
                if(storage.original!=null&&(Long)CodeSourceBridge.controlField(storage.addressField,storage.original)!=0){storage.gap="BUFFER_ORIGINAL_RELEASE_PENDING";return;}
                if(storage.descriptor!=null&&storage.descriptor.valid()){storage.gap="BUFFER_MAPPING_DESCRIPTOR_RETAINED";return;}
                storage.cleaned=true;storage.retired=true;storage.gap="";
                storage.original=null;
            }catch(IllegalStateException unavailable){storage.failed=true;storage.gap="BUFFER_RELEASE_RESULT_UNOBSERVED";}
        }
    }
    public static boolean bufferResource(Object value){
        if(!TaskBridge.recoveryWriter())throw new SecurityException("BUFFER_RESOURCE_CORE_REQUIRED");
        synchronized(STREAMS){return BUFFERS.containsKey(new Key(value,false))||BUFFER_ACTIONS.containsKey(new Key(value,false));}
    }
    private static String preserveBuffer(BufferStorage storage,FileRecord requested,Set<Module> selection,long version){
        BufferRestore previous=BUFFER_RESTORING.get(),scope;
        synchronized(STREAMS){
            if(storage.cleaned)return "";
            if(storage.sealed||storage.cleaning||storage.restoring||storage.active!=0||storage.observing!=0)return "BUFFER_OPERATIONS_EXIT_PENDING";
            if(version!=storage.viewsVersion)return "BUFFER_VIEW_OBSERVATION_PENDING";
            if(storage.failed||storage.escaped||storage.unknownUse)return "BUFFER_STORAGE_LIFETIME_UNRESOLVED";
            if(storage.bytes==0){requested.preserved=true;return "";}
            if(storage.memoryToken==0||storage.original==null)return "BUFFER_NATIVE_STORAGE_ASSOCIATION_UNAVAILABLE";
            if(!storage.memorySelection.equals(selection)){storage.memorySelection.clear();storage.memorySelection.addAll(selection);storage.memoryCursor=0;}
            scope=new BufferRestore(storage,selection.toArray(Module[]::new),storage.memoryCursor,8);storage.restoring=true;requested.preserved=false;
        }
        BUFFER_RESTORING.set(scope);
        try{
            long[] result=NativeControl.restoreBufferStorage(storage.original,storage.memoryToken,storage.memoryGeneration,scope.modules,scope.cursor,scope.budget);
            synchronized(STREAMS){
                storage.memoryCursor=result[0];
                if(result[3]!=0&&result[1]==0){requested.preserved=true;return "";}
                return result[3]==0?"BUFFER_SOURCE_RESTORE_PENDING":"BUFFER_SOURCE_RESTORE_UNRESOLVED";
            }
        }finally{
            if(previous==null)BUFFER_RESTORING.remove();else BUFFER_RESTORING.set(previous);
            synchronized(STREAMS){storage.restoring=false;STREAMS.notifyAll();}
        }
    }
    private static String releaseBuffer(Object value,Predicate<Object> selected)throws ReflectiveOperationException {
        BufferStorage storage;FileRecord requested;List<Buffer> views=new ArrayList<>();Set<Module> selection=FILE_SELECTION.get();long version;boolean preserve;
        synchronized(STREAMS){
            requested=fileRecord(value);storage=requested instanceof BufferView view?view.storage:(BufferStorage)requested;
            if(storage.cleaned)return "";
            if(!storage.created||!storage.gap.isEmpty()&&!storage.gap.equals("BUFFER_MAPPING_DESCRIPTOR_RETAINED"))return storage.gap;
            if(storage.failed||storage.escaped||storage.unknownUse||storage.observing!=0)return "BUFFER_STORAGE_LIFETIME_UNRESOLVED";
            if(selection==null||storage.sources.isEmpty())return "BUFFER_STORAGE_SOURCES_UNOBSERVED";
            version=storage.viewsVersion;
            for(var reference:storage.views){Buffer buffer=reference.get();if(buffer!=null){
                BufferView view=BUFFERS.get(new Key(buffer,false));if(view==null||!view.constructed||view.unknownUse||view.observing!=0)return "BUFFER_VIEW_OBSERVATION_PENDING";views.add(buffer);
            }}
            // A real outside view or Util cache keeps the allocation alive;
            // it does not keep the stopped source's recorded byte revisions.
            preserve=storage.runtimeManaged||!selection.containsAll(storage.sources);
        }
        if(!preserve)for(Buffer buffer:views)if(!selected.test(buffer)){preserve=true;break;}
        synchronized(STREAMS){
            if(storage.observing!=0||version!=storage.viewsVersion)return "BUFFER_VIEW_OBSERVATION_PENDING";
            preserve|=storage.runtimeManaged||!selection.containsAll(storage.sources);
            if(storage.active!=0||storage.cleaning||storage.restoring)return "BUFFER_OPERATIONS_EXIT_PENDING";
            if(!preserve){storage.sealed=true;requested.preserved=false;}
        }
        if(preserve)return preserveBuffer(storage,requested,selection,version);
        if(storage.original==null){bufferCleanupResult(storage,true);return storage.gap;}
        if((Long)CodeSourceBridge.controlField(storage.addressField,storage.original)==0&&storage.descriptor!=null&&storage.descriptor.valid()){
            var close=FileDescriptor.class.getDeclaredMethod("close");close.setAccessible(true);close.invoke(storage.descriptor);bufferCleanupResult(storage,true);return storage.gap;
        }
        Object cleaner=storage.cleaner.get();
        if(cleaner!=null)BufferPlatform.CLEANER.getMethod("clean").invoke(cleaner);
        else new BufferCleanup(storage).run();
        synchronized(STREAMS){return storage.cleaned?"":storage.gap.isEmpty()?"BUFFER_ORIGINAL_RELEASE_PENDING":storage.gap;}
    }
    private static boolean nativeBufferAllocation(){
        if(!NativeControl.bufferAllocationBoundary())throw new SecurityException("ACTUAL_BUFFER_ALLOCATION_REQUIRED");
        return WALKER.walk(frames->frames.filter(frame->frame.getDeclaringClass().getNestHost()!=ResourceBridge.class
                &&frame.getDeclaringClass()!=NativeControl.class&&frame.getDeclaringClass()!=BufferPlatform.UNSAFE)
                .findFirst().map(frame->frame.getDeclaringClass()==BufferPlatform.DIRECT&&frame.getMethodName().equals("<init>")).orElse(false));
    }
    private static Object nativeBufferAllocationObject(){
        if(!NativeControl.bufferAllocationBoundary())throw new SecurityException("ACTUAL_BUFFER_ALLOCATION_REQUIRED");
        BufferAllocation scope=BUFFER_ALLOCATING.get();return scope==null?null:scope.buffer;
    }
    private static boolean nativeBufferRelease(Object action){
        if(!NativeControl.bufferAllocationBoundary())throw new SecurityException("ACTUAL_BUFFER_DEALLOCATION_REQUIRED");
        BufferStorage storage=BUFFER_CLEANING.get();return storage!=null&&storage.original==action&&storage.cleaning&&!storage.restoring;
    }
    private static boolean nativeBufferInitializing(Object buffer){
        if(!NativeControl.bufferAllocationBoundary())throw new SecurityException("ACTUAL_BUFFER_INITIALIZATION_REQUIRED");
        BufferAllocation scope=BUFFER_ALLOCATING.get();if(scope==null||scope.closed||scope.buffer!=buffer)return false;
        return WALKER.walk(frames->frames.filter(frame->{Class<?> actual=frame.getDeclaringClass(),nest=actual.getNestHost();
            return nest!=ResourceBridge.class&&nest!=NativeControl.class&&nest!=TaskBridge.class&&nest!=CodeSourceBridge.class&&nest!=BackingBridge.class&&nest!=ExecutionFlow.class&&actual!=BufferPlatform.UNSAFE;
        }).findFirst().map(frame->frame.getDeclaringClass()==BufferPlatform.DIRECT&&frame.getMethodName().equals("<init>")).orElse(false));
    }
    private static void nativeBufferAddress(Object value,Module[] sources){
        if(!NativeControl.bufferAddressBoundary(value,sources))throw new SecurityException("ACTUAL_BUFFER_NATIVE_ADDRESS_REQUIRED");
        if(!(value instanceof Buffer))return;
        if(!TaskBridge.taskEffectAllowed(value,"buffer-native-address"))throw new IllegalStateException("RONOVA_TERMINAL_BUFFER_ADDRESS");
        Module[] contributors=TaskBridge.resourceOperationSources(sources);
        synchronized(STREAMS){BufferView view=BUFFERS.get(new Key(value,false));if(view==null)return;
            if(view.storage.sealed||view.storage.cleaned)throw new IllegalStateException("RONOVA_RETIRED_BUFFER_ADDRESS");
            view.storage.escaped=true;bufferSources(view.storage,contributors);}
    }
    private static boolean fileChannel(Object value){return value!=null&&value.getClass()==FILE_CHANNEL;}
    private static boolean platformLayout(Class<?> type){return CharacterWrappers.TYPES.contains(type)||type==PROCESS_TYPE||type==PIPE_REDIRECT||type==BufferedInputStream.class||type==BufferedOutputStream.class||type==FilterInputStream.class||type==FilterOutputStream.class||type==FileDescriptor.class||type==FileInputStream.class
            ||type==FileOutputStream.class||type==RandomAccessFile.class||NativeControl.libraryMetadataType(type)||type==FILE_CHANNEL||type==FILE_CLOSER||type==AbstractInterruptibleChannel.class||BufferPlatform.TYPES.contains(type)||IoBridge.platformLayout(type);}
    static boolean critical(Object value){if(value!=null&&(value.getClass()==PROCESS_TYPE||value.getClass()==PIPE_REDIRECT))return true;synchronized(STREAMS){WeakReference<PipeRecord> link=value==null?null:PROCESS_VIEWS.get(new Key(value,false));if(link!=null&&link.get()!=null)return true;}
        return characterWrapper(value)||value instanceof FileDescriptor||value instanceof FileInputStream
            ||value instanceof FileOutputStream||value instanceof RandomAccessFile||fileChannel(value)||value instanceof Buffer
            ||value!=null&&(BufferPlatform.UNMAPPER.isInstance(value)||value.getClass()==BufferPlatform.DEALLOCATOR||value.getClass()==BufferPlatform.CLEANER)
            ||IoBridge.platformObject(value)||NativeControl.libraryMetadataValue(value)||value instanceof Class<?> type&&platformLayout(type);}
    private static Object unsafe(){
        try{
            Class<?> type=Class.forName("sun.misc.Unsafe",true,ClassLoader.getPlatformClassLoader());
            Field singleton=type.getDeclaredField("theUnsafe");singleton.setAccessible(true);return singleton.get(null);
        }catch(ReflectiveOperationException failed){throw new ExceptionInInitializerError(failed);}
    }
    private static Method layoutMethod(String name,Class<?> parameter){
        try{
            Method method=UNSAFE.getClass().getMethod(name,parameter);
            if(!method.trySetAccessible())throw new IllegalStateException("RESOURCE_LAYOUT_METHOD_UNAVAILABLE:"+name);
            CodeSourceBridge.resourceLayoutControls(method);
            return method;
        }catch(ReflectiveOperationException failed){throw new ExceptionInInitializerError(failed);}
    }
    private static List<Span> spans(Class<?> actual,boolean statik){
        try{
            var offset=statik?STATIC_OFFSET:INSTANCE_OFFSET;
            int referenceBytes=((Number)ARRAY_SCALE.invoke(UNSAFE,Object[].class)).intValue();
            List<Span> result=new ArrayList<>();
            for(Class<?> type=actual;type!=null;type=type.getSuperclass())if(platformLayout(type))for(Field field:type.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=statik)continue;
                Class<?> kind=field.getType();int bytes=kind.isPrimitive()?kind==long.class||kind==double.class?8:
                        kind==int.class||kind==float.class?4:kind==short.class||kind==char.class?2:1:referenceBytes;
                result.add(new Span(field,((Number)offset.invoke(UNSAFE,field)).longValue(),bytes));
            }
            return List.copyOf(result);
        }catch(ReflectiveOperationException failed){throw new IllegalStateException("RESOURCE_FIELD_LAYOUT_UNOBSERVED",failed);}
    }
    static boolean fieldMutationAllowed(Object receiver,Class<?> declaring){
        if(NativeControl.libraryMetadataType(declaring))return TaskBridge.recoveryWriter()||NativeControl.libraryImplementationCaller();
        return !platformLayout(declaring)||mutationAllowed(receiver);
    }
    static boolean unsafeMutationAllowed(Object receiver,long offset,long length){
        if(!critical(receiver)||length<=0)return true;
        if(offset<0||length>Long.MAX_VALUE-offset)return false;
        if(overlapsFields(receiver,offset,length,INSTANCE_SPANS.get(receiver.getClass())))return mutationAllowed(receiver);
        if(receiver instanceof Class<?> type&&overlapsFields(receiver,offset,length,STATIC_SPANS.get(type)))return mutationAllowed(receiver);
        return true;
    }
    private static boolean overlapsFields(Object receiver,long offset,long length,List<Span> spans){
        try{
            for(Span span:spans)if(offset<span.offset+span.bytes&&span.offset<offset+length&&fieldHolder(span.field,receiver))return true;
            return false;
        }catch(ReflectiveOperationException failed){throw new IllegalStateException("RESOURCE_FIELD_LAYOUT_UNOBSERVED",failed);}
    }
    static int unsafeWidth(String kind){
        return switch(kind){case "Boolean","Byte"->1;case "Short","Char"->2;case "Int","Float"->4;case "Long","Double"->8;
            case "Object","Reference"->{try{yield ((Number)ARRAY_SCALE.invoke(UNSAFE,Object[].class)).intValue();}
                catch(ReflectiveOperationException failed){throw new IllegalStateException("RESOURCE_REFERENCE_LAYOUT_UNOBSERVED",failed);}}
            default->8;};
    }
    static long[] arrayLayout(Class<?> actual){
        if(actual==null||!actual.isArray())return null;
        try{
            int base=((Number)ARRAY_BASE.invoke(UNSAFE,actual)).intValue();
            int scale=((Number)ARRAY_SCALE.invoke(UNSAFE,actual)).intValue();
            return base>=0&&scale>0?new long[]{base,scale}:null;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_ARRAY_LAYOUT_UNAVAILABLE",unavailable);}
    }
    private static boolean fieldHolder(Field field,Object receiver)throws ReflectiveOperationException{
        return java.lang.reflect.Modifier.isStatic(field.getModifiers())
                ?STATIC_BASE.invoke(UNSAFE,field)==receiver
                :receiver!=null&&field.getDeclaringClass().isInstance(receiver);
    }
    /** Resolve storage on the actual receiver before considering a Class mirror's static storage. */
    static Field exactField(Object receiver,long offset,long length){
        if(receiver==null)return null;
        Field instance=exactField(receiver,receiver.getClass(),false,offset,length);
        // A complete instance field occupies its own VM slot even when its receiver
        // is a Class mirror. Its bytes cannot also be a represented static field.
        if(instance!=null)return instance;
        return receiver instanceof Class<?> type?exactField(receiver,type,true,offset,length):null;
    }
    private static Field exactField(Object receiver,Class<?> actual,boolean statik,long offset,long length){
        Field exact=null;
        for(Class<?> type=actual;type!=null;type=statik?null:type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=statik)continue;
            long[] span=fieldSpan(field,receiver);
            if(span!=null&&span[0]==offset&&span[1]==length){
                if(exact!=null)throw new IllegalStateException("OVERLAPPING_FIELD_LAYOUT");exact=field;
            }
        }
        return exact;
    }
    /** Arbitrary JNI/reflection/Unsafe setters may not forge the platform resource associations. */
    static long[] fieldSpan(java.lang.reflect.Field field,Object receiver){
        try{
            boolean statik=java.lang.reflect.Modifier.isStatic(field.getModifiers());
            if(!fieldHolder(field,receiver))return null;
            long at=((Number)(statik?STATIC_OFFSET:INSTANCE_OFFSET).invoke(UNSAFE,field)).longValue();
            Class<?> type=field.getType();int width=type.isPrimitive()?type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1:unsafeWidth("Reference");
            return new long[]{at,width};
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_FIELD_LAYOUT_UNAVAILABLE",unavailable);}
    }
    static boolean mutationAllowed(Object value){
        if(NativeControl.libraryMetadataValue(value))return TaskBridge.recoveryWriter()||NativeControl.libraryImplementationCaller();
        if(!critical(value)||TaskBridge.recoveryWriter())return true;
        return WALKER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();String name=type.getName();
                if(DefinitionBridge.module(type)!=type.getModule())return false;
                if(type.getNestHost()==ResourceBridge.class||type.getNestHost()==IoBridge.class||type.getNestHost()==TaskBridge.class
                        ||type.getNestHost()==BackingBridge.class||type.getNestHost()==NativeControl.class)continue;
                if(type.getClassLoader()==null&&(name.startsWith("java.lang.invoke.")||name.startsWith("java.lang.reflect.")
                        ||name.startsWith("jdk.internal.reflect.")||name.equals("jdk.internal.misc.Unsafe"))
                        ||name.equals("sun.misc.Unsafe")&&type.getClassLoader()==ClassLoader.getPlatformClassLoader())continue;
                if(type.getClassLoader()==null&&DefinitionBridge.module(type)!=Object.class.getModule())return false;
                if(type.getNestHost()==FileDescriptor.class){
                    if(type==FileDescriptor.class&&frame.getMethodName().equals("<init>"))return true;
                    continue; // A direct reflective call to FileDescriptor.set is not an I/O implementation.
                }
                return type.getClassLoader()==null&&DefinitionBridge.module(type)==Object.class.getModule()
                        &&(name.startsWith("java.io.")||name.startsWith("sun.nio.cs.")||name.startsWith("java.net.")||name.startsWith("sun.nio.ch.")||BufferPlatform.TYPES.contains(type)||type==FILE_FACTORY||type.getNestHost()==PROCESS_TYPE||type.getNestHost()==ProcessBuilder.class||type==PIPE_INPUT);
            }
            return false;
        });
    }
    /** The server only polls; a target holding a Java monitor cannot freeze its control loop. */
    public static boolean retire(Object stream,Callable<String> action)throws IOException {
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(action))throw new SecurityException("RESOURCE_RECOVERY_CORE_REQUIRED");
        FileRecord record;
        synchronized(STREAMS){record=fileRecord(stream);}
        if(record==null)throw new IOException("FILE_RESOURCE_CONSTRUCTOR_ASSOCIATION_UNOBSERVED");
        if(!record.observation.tryLock())return false;
        try{
            if(record.retired)return true;
            if(record.release==null){
                try{
                    FutureTask<String> future=new FutureTask<>(action);record.release=future;CLOSER.execute(future);
                }
                catch(RejectedExecutionException full){record.release=null;throw new IOException("RESOURCE_DISPOSITION_CAPACITY",full);}
                return false;
            }
            if(!record.release.isDone())return false;
            try{
                String remaining=record.release.get();record.release=null;
                if(!remaining.isEmpty())throw new IOException(remaining);
                record.retired=!record.preserved;return true;
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("RESOURCE_RELEASE_OBSERVATION_INTERRUPTED",interrupted);}
            catch(ExecutionException failed){record.release=null;throw new IOException("RESOURCE_RELEASE_FAILED:"+failed.getCause().getClass().getSimpleName(),failed.getCause());}
        }finally{record.observation.unlock();}
    }
    public static String releaseProcess(Process process,Predicate<Object> selected,Module[] modules){
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        Set<Module> previous=selectFiles(modules);
        try{return releaseProcess(process,selected);}finally{restoreFileSelection(previous);}
    }
    public static String release(Object stream,Predicate<Object> selected,Module[] modules){
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        Set<Module> previous=selectFiles(modules);
        try{return release(stream,selected);}finally{restoreFileSelection(previous);}
    }
    private static Set<Module> selectFiles(Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Module module:modules){if(module==null||!TaskBridge.modStopped(module))throw new IllegalStateException("STOPPED_FILE_SOURCE_GROUP_REQUIRED");selected.add(module);}
        if(selected.isEmpty())throw new IllegalStateException("FILE_SOURCE_GROUP_REQUIRED");
        Set<Module> previous=FILE_SELECTION.get();FILE_SELECTION.set(selected);return previous;
    }
    private static void restoreFileSelection(Set<Module> previous){if(previous==null)FILE_SELECTION.remove();else FILE_SELECTION.set(previous);}
    public static String release(Object stream,Predicate<Object> selected){
        if(!TaskBridge.recoveryWriter()||!TaskBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        try{
            boolean buffer;synchronized(STREAMS){buffer=BUFFERS.containsKey(new Key(stream,false))||BUFFER_ACTIONS.containsKey(new Key(stream,false));}
            if(characterWrapper(stream))return releaseWrapper(stream,selected);
            if(buffer)return releaseBuffer(stream,selected);
            if(IoBridge.resource(stream))return IoBridge.release(stream,selected,FILE_SELECTION.get());
            return fileChannel(stream)?releaseChannel(stream,selected):releaseStream(stream,selected);
        }catch(ReflectiveOperationException|IOException|RuntimeException failure){return "RESOURCE_RELEASE_FAILED:"+failure.getClass().getSimpleName()+":"+failure.getMessage();}
    }
    private static DescriptorView descriptorView(FileDescriptor descriptor)throws ReflectiveOperationException,IOException {
        List<Object> streams=new ArrayList<>(),channels=new ArrayList<>();boolean allocated;
        synchronized(STREAMS){
            DescriptorRecord attachment=ATTACHMENTS.get(new Key(descriptor,false));
            if(attachment==null)throw new IOException("DESCRIPTOR_ATTACHMENTS_UNOBSERVED");
            allocated=attachment.allocated;
            for(var reference:attachment.aliases){Object alias=reference.get();if(alias==null)throw new IOException("DESCRIPTOR_ALIAS_RELEASE_UNOBSERVED");streams.add(alias);}
            for(var reference:attachment.channels){Object channel=reference.get();if(channel==null)throw new IOException("FILE_CHANNEL_LIFETIME_UNOBSERVED");channels.add(channel);}
        }
        Object parent=FD_PARENT.get(descriptor),peers=FD_PEERS.get(descriptor);
        if(parent!=null&&!identityContains(streams,parent))throw new IOException("DESCRIPTOR_PARENT_ASSOCIATION_CHANGED");
        if(peers!=null){
            if(peers.getClass()!=ArrayList.class)throw new IOException("DESCRIPTOR_PARENT_LAYOUT_CHANGED");
            for(Object peer:(List<?>)peers)if(!identityContains(streams,peer))throw new IOException("DESCRIPTOR_PARENT_ASSOCIATION_CHANGED");
        }
        for(Object channel:channels){
            ChannelRecord record;synchronized(STREAMS){record=CHANNELS.get(new Key(channel,false));}
            if(record==null)throw new IOException("FILE_CHANNEL_CONSTRUCTOR_ASSOCIATION_UNOBSERVED");
            channelAssociation(channel,record,descriptor);
        }
        return new DescriptorView(descriptor,streams,channels,allocated);
    }
    private static void channelAssociation(Object channel,ChannelRecord record,FileDescriptor descriptor)throws ReflectiveOperationException,IOException {
        if(!record.gap.isEmpty())throw new IOException(record.gap);
        if(!fileChannel(channel)||record.descriptor.get()!=descriptor||CHANNEL_FD.get(channel)!=descriptor
                ||record.parentPresent&&record.parent.get()==null||CHANNEL_PARENT.get(channel)!=record.parent.get()
                ||!Objects.equals(CHANNEL_PATH.get(channel),record.path))throw new IOException("FILE_CHANNEL_ORIGINAL_ASSOCIATION_CHANGED");
        if(CHANNEL_CLOSER.get(channel)!=record.cleaner.get())throw new IOException("FILE_CHANNEL_CLEANER_ASSOCIATION_CHANGED");
        if(descriptor.valid()&&(FD_NUMBER.getInt(descriptor)!=record.number||FD_HANDLE.getLong(descriptor)!=record.handle))
            throw new IOException("FILE_CHANNEL_ORIGINAL_HANDLE_CHANGED");
    }
    private static boolean privateDescriptor(DescriptorView view,Predicate<Object> selected)throws IOException {
        if(!view.allocated)return false;
        for(Object stream:view.streams)if(!selected.test(stream))return false;
        for(Object channel:view.channels)if(!selected.test(channel))return false;
        synchronized(STREAMS){
            DescriptorRecord descriptor=ATTACHMENTS.get(new Key(view.descriptor,false));
            if(descriptor==null||descriptor.unknownUse)throw new IOException("FILE_DESCRIPTOR_USE_SOURCE_UNOBSERVED");
            if(descriptor.observing!=0)throw new IOException("FILE_DESCRIPTOR_SOURCE_OBSERVATION_PENDING");
            if(!descriptor.nativeSources.isEmpty()){
                Set<Module> sources=FILE_SELECTION.get();
                if(sources==null)throw new IOException("FILE_DESCRIPTOR_SELECTED_SOURCES_REQUIRED");
                if(!sources.containsAll(descriptor.nativeSources))return false;
            }
            for(Object value:views(view)){
                FileRecord record=fileRecord(value);
                if(record==null)throw new IOException("FILE_RESOURCE_CONSTRUCTION_UNOBSERVED");
                if(record.unknownUse)throw new IOException("FILE_RESOURCE_USE_SOURCE_UNOBSERVED");
                if(record.observing!=0)throw new IOException("FILE_RESOURCE_SOURCE_OBSERVATION_PENDING");
            }
        }
        return true;
    }
    private static List<Object> views(DescriptorView view){List<Object> all=new ArrayList<>(view.streams);all.addAll(view.channels);return all;}
    private static String preserve(FileRecord record){synchronized(STREAMS){record.preserved=true;}return "";}
    private static String releaseStream(Object stream,Predicate<Object> selected)throws ReflectiveOperationException,IOException {
        StreamRecord record;synchronized(STREAMS){record=STREAMS.get(new Key(stream,false));}
        if(record==null)return "STREAM_CONSTRUCTOR_ASSOCIATION_UNOBSERVED";
        FileDescriptor descriptor=record.descriptor.get();if(descriptor==null)return "STREAM_DESCRIPTOR_RELEASE_UNOBSERVED";
        synchronized(descriptor){
            Layout layout=record.layout;
            if(record.retired&&layout.closed.getBoolean(stream))return "";
            record.preserved=false;
            if(!record.gap.isEmpty())return record.gap;
            if(layout.descriptor.get(stream)!=descriptor||!Objects.equals(layout.path.get(stream),record.path))return "STREAM_ORIGINAL_ASSOCIATION_CHANGED";
            Object channel=layout.channel.get(stream);
            if(channel!=null&&(!record.channelSeen||record.channel.get()!=channel))return "STREAM_CHANNEL_ASSOCIATION_UNOBSERVED";
            if(descriptor.valid()&&(FD_NUMBER.getInt(descriptor)!=record.number||FD_HANDLE.getLong(descriptor)!=record.handle))return "STREAM_ORIGINAL_HANDLE_CHANGED";
            DescriptorView view=descriptorView(descriptor);
            if(privateDescriptor(view,selected))return closePrivateDescriptor(descriptor,view,selected);
            // A parent stream must remain usable while an outside participant still owns its same channel.
            if(channel!=null&&!selected.test(channel))return preserve(record);
            synchronized(STREAMS){
                if(record.unknownUse)return "STREAM_USE_SOURCE_UNOBSERVED";
                if(record.observing!=0)return "STREAM_SOURCE_OBSERVATION_PENDING";
                if(!selected.test(stream))return preserve(record);
                if(channel!=null){
                    ChannelRecord attached=CHANNELS.get(new Key(channel,false));
                    if(attached==null)return "STREAM_CHANNEL_CONSTRUCTION_UNOBSERVED";
                    if(attached.unknownUse||attached.observing!=0)return "STREAM_CHANNEL_USE_SOURCE_UNOBSERVED";
                    if(!selected.test(channel))return preserve(record);
                    attached.sealed=true;
                }
                record.sealed=true;
                if(record.active!=0)return "STREAM_IO_EXIT_PENDING";
            }
            // Closing the channel may call back into its parent. Close the view before that callback,
            // then remove only this parent from the original descriptor's closeAll list.
            layout.closed.setBoolean(stream,true);
            if(channel!=null){
                ChannelRecord attached;synchronized(STREAMS){attached=CHANNELS.get(new Key(channel,false));}
                String pending=closeChannel(channel,attached);if(!pending.isEmpty())return pending;
                layout.channel.set(stream,null);
            }
            Object parent=FD_PARENT.get(descriptor),peers=FD_PEERS.get(descriptor);
            if(peers instanceof ArrayList<?> aliases)aliases.removeIf(peer->peer==stream);
            if(parent==stream)FD_PARENT.set(descriptor,peers instanceof List<?> aliases&&!aliases.isEmpty()?aliases.get(0):null);
            layout.descriptor.set(stream,null);
            synchronized(STREAMS){
                DescriptorRecord attachment=ATTACHMENTS.get(new Key(descriptor,false));
                attachment.aliases.removeIf(alias->alias.get()==stream);
                if(channel!=null)attachment.channels.removeIf(alias->alias.get()==channel);
                record.retired=true;
            }
            return layout.descriptor.get(stream)==null&&layout.closed.getBoolean(stream)?"":"BORROWED_STREAM_DETACH_READBACK_FAILED";
        }
    }
    private static String releaseChannel(Object channel,Predicate<Object> selected)throws ReflectiveOperationException,IOException {
        ChannelRecord record;synchronized(STREAMS){record=CHANNELS.get(new Key(channel,false));}
        if(record==null)return "FILE_CHANNEL_CONSTRUCTOR_ASSOCIATION_UNOBSERVED";
        FileDescriptor descriptor=record.descriptor.get();if(descriptor==null)return "FILE_CHANNEL_DESCRIPTOR_RELEASE_UNOBSERVED";
        synchronized(descriptor){
            record.preserved=false;channelAssociation(channel,record,descriptor);
            DescriptorView view=descriptorView(descriptor);
            if(privateDescriptor(view,selected))return closePrivateDescriptor(descriptor,view,selected);
            Object parent=record.parent.get();
            if(record.parentPresent){
                if(!selected.test(parent))return preserve(record);
                if(!LAYOUTS.containsKey(parent.getClass())&&parent.getClass()!=PIPE_INPUT)return "FILE_CHANNEL_PARENT_DELEGATION_UNRESOLVED";
                String pending=releaseStream(parent,selected);
                if(!pending.isEmpty())return pending;
                return ((Channel)channel).isOpen()?preserve(record):"";
            }
            synchronized(STREAMS){
                if(record.unknownUse)return "FILE_CHANNEL_USE_SOURCE_UNOBSERVED";
                if(record.observing!=0)return "FILE_CHANNEL_SOURCE_OBSERVATION_PENDING";
                if(!selected.test(channel))return preserve(record);
                if(!record.cleanupFinished&&record.cleanup.get()==null)return "FILE_CHANNEL_CLEANUP_UNOBSERVED";
                record.sealed=true;record.detachDescriptor=true;
            }
            // Cleanable.clean still unregisters the actual cleaner; its captured action now leaves
            // the borrowed descriptor alive. The channel's own locks and blocked operations close normally.
            String pending=closeChannel(channel,record);if(!pending.isEmpty())return pending;
            synchronized(STREAMS){ATTACHMENTS.get(new Key(descriptor,false)).channels.removeIf(alias->alias.get()==channel);}
            return "";
        }
    }
    private static String closePrivateDescriptor(FileDescriptor descriptor,DescriptorView view,Predicate<Object> selected)throws ReflectiveOperationException,IOException {
        for(Object stream:view.streams)if(!LAYOUTS.containsKey(stream.getClass())&&stream.getClass()!=PIPE_INPUT)return "DESCRIPTOR_USER_CLOSE_DELEGATION_UNRESOLVED";
        synchronized(STREAMS){
            // Admission and the retirement seal meet at the same resource records.
            DescriptorRecord descriptorRecord=ATTACHMENTS.get(new Key(descriptor,false));
            if(descriptorRecord==null||descriptorRecord.unknownUse||descriptorRecord.observing!=0)return "FILE_DESCRIPTOR_SOURCE_OBSERVATION_PENDING";
            if(!descriptorRecord.nativeSources.isEmpty()&&(FILE_SELECTION.get()==null||!FILE_SELECTION.get().containsAll(descriptorRecord.nativeSources)))return "FILE_DESCRIPTOR_SHARED_DURING_RETIREMENT";
            for(Object value:views(view)){
                FileRecord record=fileRecord(value);
                if(record==null||record.unknownUse||record.observing!=0)return "FILE_RESOURCE_SOURCE_OBSERVATION_PENDING";
            }
            // Re-evaluate after any source publication which preceded the seal.
            for(Object value:views(view))if(!selected.test(value))return "FILE_RESOURCE_SHARED_DURING_RETIREMENT";
            ATTACHMENTS.get(new Key(descriptor,false)).sealed=true;
            for(Object value:views(view)){FileRecord record=fileRecord(value);record.sealed=true;record.preserved=false;}
        }
        IOException failure=null;
        for(Object stream:view.streams)try{((Closeable)stream).close();}catch(IOException thrown){if(failure==null)failure=thrown;else failure.addSuppressed(thrown);}
        for(Object channel:view.channels)try{
            ChannelRecord record;synchronized(STREAMS){record=CHANNELS.get(new Key(channel,false));}
            String pending=closeChannel(channel,record);if(!pending.isEmpty()&&failure==null)failure=new IOException(pending);
        }catch(IOException thrown){if(failure==null)failure=thrown;else failure.addSuppressed(thrown);}
        if(descriptor.valid()){
            var close=FileDescriptor.class.getDeclaredMethod("close");close.setAccessible(true);
            var all=FileDescriptor.class.getDeclaredMethod("closeAll",Closeable.class);all.setAccessible(true);
            Closeable releaser=()->{try{close.invoke(descriptor);}catch(ReflectiveOperationException thrown){throw new IOException("DESCRIPTOR_NATIVE_CLOSE_FAILED",thrown);}};
            all.invoke(descriptor,releaser);
        }
        if(failure!=null)throw failure;
        synchronized(STREAMS){
            if(ATTACHMENTS.get(new Key(descriptor,false)).active!=0)return "FILE_DESCRIPTOR_IO_EXIT_PENDING";
            for(Object value:views(view))if(fileRecord(value).active!=0)return "FILE_RESOURCE_IO_EXIT_PENDING";
        }
        for(Object stream:view.streams){StreamRecord record;synchronized(STREAMS){record=STREAMS.get(new Key(stream,false));}if(!record.layout.closed.getBoolean(stream))return "STREAM_CLOSE_PENDING";}
        return !descriptor.valid()?"":"FILE_DESCRIPTOR_NATIVE_RELEASE_PENDING";
    }
    private static String closeChannel(Object channel,ChannelRecord record)throws IOException {
        ((Channel)channel).close();
        if(((Channel)channel).isOpen())return "FILE_CHANNEL_CLOSE_PENDING";
        if(!record.closeCompleted)return "FILE_CHANNEL_CLOSE_INCOMPLETE";
        synchronized(STREAMS){return record.active==0?"":"FILE_CHANNEL_IO_EXIT_PENDING";}
    }
    private static boolean identityContains(List<Object> values,Object target){for(Object value:values)if(value==target)return true;return false;}
}
