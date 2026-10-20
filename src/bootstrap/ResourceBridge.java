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
    private static final ThreadLocal<FileOperation> FILE_OPERATIONS=new ThreadLocal<>();
    private record PlannedFileMutation(Class<?> owner,java.lang.invoke.MethodHandle begin,java.lang.invoke.MethodHandle end){}
    private static volatile PlannedFileMutation plannedFileMutation;
    private static final ThreadLocal<MappingDuplicate> MAPPING_DUPLICATING=new ThreadLocal<>();
    private static final IdentityHashMap<FileDescriptor,MappingDuplicateNative> MAPPING_DUPLICATE_PENDING=new IdentityHashMap<>();
    private static final ThreadLocal<BufferStorage> BUFFER_CLEANING=new ThreadLocal<>();
    private static final ThreadLocal<BufferAllocation> BUFFER_ALLOCATING=new ThreadLocal<>();
    private static final ThreadLocal<BufferWrapScope> BUFFER_WRAPPING=new ThreadLocal<>();
    private static final StackWalker WRAP_AUTHORITY=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final String BUFFER_WRAP_NAME="org.lwjgl.system.MemoryUtil",LWJGL_JNI_NAME="org.lwjgl.system.ThreadLocalUtil",BUFFER_WRAP_DESCRIPTOR="(Ljava/lang/Class;JI)Ljava/nio/Buffer;",
            BUFFER_SLICE_DESCRIPTOR="(Ljava/nio/ByteBuffer;JI)Ljava/nio/ByteBuffer;";
    private static final List<BufferWrapRole> BUFFER_WRAP_ROLES=new ArrayList<>();
    private static volatile Class<?> bufferWrapAgent;
    private static final ThreadLocal<BufferRegistration> BUFFER_REGISTERING=new ThreadLocal<>();
    private static final ThreadLocal<BufferObservation> BUFFER_OBSERVING_STORAGE=new ThreadLocal<>();
    private static final ThreadLocal<BufferUse> BUFFER_USING_STORAGE=new ThreadLocal<>();
    private static final ThreadLocal<BufferRestore> BUFFER_RESTORING=new ThreadLocal<>();
    private static final ThreadLocal<IoBufferWrite> IO_BUFFER_WRITE=new ThreadLocal<>();
    private static final HashMap<Class<?>,Layout> LAYOUTS=new HashMap<>();
    private static final HashMap<Key,ProcessRecord> PROCESSES=new HashMap<>();
    private static final IdentityHashMap<Thread,ReleasePhase> RELEASE_PHASES=new IdentityHashMap<>();
    private static final HashMap<Key,WeakReference<PipeRecord>> PROCESS_VIEWS=new HashMap<>();
    private static final ThreadLocal<ProcessRecord> PROCESS_CLEANUP=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FILE_OBSERVING=new ThreadLocal<>();
    private static final ThreadLocal<Set<Module>> FILE_SELECTION=new ThreadLocal<>();
    private static final ThreadLocal<FileKernelScope> FILE_KERNEL=new ThreadLocal<>();
    private static final ThreadLocal<FileKernelCandidate> IO_KERNEL_SELECTION=new ThreadLocal<>();
    private static final Class<?> PROCESS_TYPE=platformType("java.lang.ProcessImpl"),PIPE_INPUT=platformType("java.lang.Process$PipeInputStream");
    private static final Class<?> PIPE_REDIRECT=platformType("java.lang.ProcessBuilder$RedirectPipeImpl");
    private static final Class<?> FILE_CHANNEL=platformType("sun.nio.ch.FileChannelImpl"),FILE_CLOSER=platformType("sun.nio.ch.FileChannelImpl$Closer");
    private static final Class<?> FILE_FACTORY=optionalPlatformType("sun.nio.fs.WindowsChannelFactory"),SOCKET_NET=platformType("sun.nio.ch.Net");
    private static final Class<?> FILE_DISPATCHER=platformType("sun.nio.ch.FileDispatcherImpl"),FILE_CLEANABLE=platformType("java.io.FileCleanable"),FD_ACCESS=platformType("java.io.FileDescriptor$1");
    private static final Field CHANNEL_FD=field(FILE_CHANNEL,"fd"),CHANNEL_PARENT=field(FILE_CHANNEL,"parent"),CHANNEL_PATH=field(FILE_CHANNEL,"path"),CHANNEL_CLOSER=field(FILE_CHANNEL,"closer");
    private static final Field CLOSER_FD=field(FILE_CLOSER,"fd");
    private static final class BufferPlatform {
        static final Class<?> DIRECT=platformType("java.nio.DirectByteBuffer"),DEALLOCATOR=platformType("java.nio.DirectByteBuffer$Deallocator"),
                UNMAPPER=platformType("sun.nio.ch.FileChannelImpl$Unmapper"),CLEANER=platformType("jdk.internal.ref.Cleaner"),UNSAFE=platformType("jdk.internal.misc.Unsafe");
        static final Field ADDRESS=field(Buffer.class,"address"),MARK=field(Buffer.class,"mark"),CAPACITY=field(Buffer.class,"capacity"),POSITION=field(Buffer.class,"position"),LIMIT=field(Buffer.class,"limit"),CLEANUP=field(DIRECT,"cleaner"),
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
                    try{Field field=PlatformMetadata.declaredField(owner,name);field.setAccessible(true);return field;}catch(NoSuchFieldException absent){}
                return null;
            }
        };
        static final ClassValue<List<Field>> ARRAYS=new ClassValue<>(){
            protected List<Field> computeValue(Class<?> type){
                List<Field> fields=new ArrayList<>();
                for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass())if(TYPES.contains(owner))
                    try{Field field=PlatformMetadata.declaredField(owner,"hb");field.setAccessible(true);fields.add(field);}catch(NoSuchFieldException absent){}
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
    // JVM retransformation preserves the field schema and offsets. Cache only
    // each actual declaring Class's layout; source/value/holder decisions stay live.
    private static final ClassValue<Span[]> INSTANCE_FIELDS=new ClassValue<>() {
        protected Span[] computeValue(Class<?> type){return declaredSpans(type,false);}
    };
    private static final ClassValue<Span[]> STATIC_FIELDS=new ClassValue<>() {
        protected Span[] computeValue(Class<?> type){return declaredSpans(type,true);}
    };
    private static final Field FD_NUMBER=field(FileDescriptor.class,"fd"),FD_HANDLE=field(FileDescriptor.class,"handle");
    private static final Field FD_CLOSED=field(FileDescriptor.class,"closed"),FD_PARENT=field(FileDescriptor.class,"parent"),FD_PEERS=field(FileDescriptor.class,"otherParents");
    private static final Field FD_CLEANUP=field(FileDescriptor.class,"cleanup"),CLEANABLE_HANDLE=field(FILE_CLEANABLE,"handle"),CLEANABLE_NUMBER=field(FILE_CLEANABLE,"fd"),CLEANABLE_REFERENT=field(Reference.class,"referent");
    private static final Field DISPATCHER_FD_ACCESS=field(FILE_DISPATCHER,"fdAccess");
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
        String kernelResult="HOST_KERNEL_FILE_UNSERVED";
    }
    private static final class FileKernelCandidate {
        final FileDescriptor descriptor;final DescriptorRecord record;final Set<Module> selection;final Predicate<Object> selected;final List<Object> aliases;final Thread thread=Thread.currentThread();final int number;final long handle;
        FileKernelCandidate(FileDescriptor descriptor,DescriptorRecord record,Set<Module> selection,Predicate<Object> selected,List<Object> aliases)throws IllegalAccessException{this.descriptor=descriptor;this.record=record;this.selection=selection;this.selected=selected;this.aliases=List.copyOf(aliases);this.number=FD_NUMBER.getInt(descriptor);this.handle=FD_HANDLE.getLong(descriptor);}
    }
    private static final class FileKernelScope {
        final FileKernelCandidate candidate;final Module[] actualSelection;final Thread thread=Thread.currentThread();boolean closed;
        FileKernelScope(FileKernelCandidate candidate){this.candidate=candidate;this.actualSelection=candidate.selection.toArray(Module[]::new);}
    }
    static class FileRecord {
        final java.util.concurrent.locks.ReentrantLock observation=new java.util.concurrent.locks.ReentrantLock();
        Future<String> release;
        ReleasePhase releasePhase;
        boolean retired,sealed,unknownUse,preserved,releaseStarted;
        int active,observing;
    }
    /** The phase of the one existing closer Future, never release authority. */
    private static final class ReleasePhase {boolean effects,pending,uncertain;}
    private static FutureTask<String> releaseTask(Callable<String> action,ReleasePhase phase){
        ControlBridge.controlPublication(STREAMS,new Object[]{RELEASE_PHASES,phase});
        return new FutureTask<>(()->{
            Thread thread=Thread.currentThread();ReleasePhase previous;
            synchronized(STREAMS){previous=RELEASE_PHASES.put(thread,phase);}
            try{return action.call();}
            catch(Exception|Error failure){releaseUncertain();throw failure;}
            finally{synchronized(STREAMS){if(previous==null)RELEASE_PHASES.remove(thread);else RELEASE_PHASES.put(thread,previous);}}
        });
    }
    static void releaseEffectStarted(){synchronized(STREAMS){ReleasePhase phase=RELEASE_PHASES.get(Thread.currentThread());if(phase!=null){phase.effects=true;phase.pending=false;}}}
    static String releasePending(String gap){synchronized(STREAMS){ReleasePhase phase=RELEASE_PHASES.get(Thread.currentThread());if(phase!=null&&!phase.effects&&!phase.uncertain)phase.pending=true;}return gap;}
    private static void releaseUncertain(){synchronized(STREAMS){ReleasePhase phase=RELEASE_PHASES.get(Thread.currentThread());if(phase!=null){phase.effects=true;phase.pending=false;phase.uncertain=true;}}}
    private static boolean releaseMayContinue(){synchronized(STREAMS){ReleasePhase phase=RELEASE_PHASES.get(Thread.currentThread());return phase!=null&&phase.pending&&!phase.effects;}}
    static void releaseStepCompleted(){synchronized(STREAMS){ReleasePhase phase=RELEASE_PHASES.get(Thread.currentThread());if(phase!=null&&!phase.uncertain){phase.effects=false;phase.pending=false;}}}
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
        Module[] sources=ControlBridge.resourceOperationSources(null);if(sources.length==0)return;
        try{
            Field lock=value instanceof Reader?CharacterWrappers.READER_LOCK:CharacterWrappers.WRITER_LOCK;
            WrapperRecord record=new WrapperRecord(lock.get(value));
            for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())for(Field member:PlatformMetadata.declaredFields(type))if(wrapperSlot(member)){
                member.setAccessible(true);Object child=member.get(value);record.delegates.put(member,new WeakReference<>(child));
            }
            synchronized(STREAMS){reap();WRAPPERS.putIfAbsent(new Key(value,true),record);}
            ControlBridge.resourceCreated(value,sources);
            for(var entry:record.delegates.entrySet())if(Set.of("in","out","sd","se","ch").contains(entry.getKey().getName()))exposeWrapperGraph(entry.getValue().get(),sources);
            // These are freshly allocated private buffers, not caller-supplied encoders or delegates.
            for(var entry:record.delegates.entrySet())if(Set.of("cb","buf","bb").contains(entry.getKey().getName())){
                Object child=entry.getValue().get();if(child!=null)ControlBridge.resourceCreated(child,sources);
            }
        }catch(ReflectiveOperationException failed){throw new IllegalStateException("CHARACTER_WRAPPER_CONSTRUCTOR_LAYOUT",failed);}
    }
    public static void wrapperOperation(Object value,Class<?> declaring,String method)throws IOException{
        platform(declaring,method);WrapperRecord record;
        synchronized(STREAMS){record=WRAPPERS.get(new Key(value,false));if(record==null)return;if(record.sealed)throw new IOException("CHARACTER_WRAPPER_RETIRED");record.observing++;record.exposureVersion++;}
        try{exposeWrapperGraph(value,ControlBridge.resourceOperationSources(null));}
        finally{synchronized(STREAMS){record.observing--;}}
    }
    private static void exposeWrapperGraph(Object value,Module[] sources){
        if(value==null||sources.length==0)return;
        ArrayDeque<Object> pending=new ArrayDeque<>();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());pending.add(value);
        while(!pending.isEmpty()){
            Object current=pending.removeFirst();if(!seen.add(current))continue;
            ControlBridge.resourceExposed(current,sources);
            synchronized(STREAMS){WrapperRecord child=WRAPPERS.get(new Key(current,false));if(child!=null)for(var reference:child.delegates.values()){
                Object next=reference.get();if(next!=null)pending.add(next);
            }}
        }
    }
    /** The core consumes captured delegates only after the asynchronous detach is confirmed. */
    public static Object[] wrapperChildren(Object value){
        if(!ControlBridge.recoveryWriter())throw new SecurityException("RESOURCE_RECOVERY_CORE_REQUIRED");
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
            long version;synchronized(STREAMS){if(record.observing!=0)return releasePending("CHARACTER_WRAPPER_EXPOSURE_PENDING");version=record.exposureVersion;}
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
            synchronized(STREAMS){if(record.observing!=0||record.exposureVersion!=version)return releasePending("CHARACTER_WRAPPER_EXPOSURE_CHANGED");record.sealed=true;record.children=children.toArray();}
            // Never call close/flush: a delegate may also serve a surviving source.
            releaseEffectStarted();
            for(Field member:record.delegates.keySet()){member.set(value,null);if(member.get(value)!=null)return "CHARACTER_WRAPPER_DETACH_READBACK_FAILED";}
            for(Field member:PlatformMetadata.declaredFields(value.getClass()))if(member.getName().equals("closed")&&member.getType()==boolean.class){member.setAccessible(true);member.setBoolean(value,true);}
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
        boolean constructed,memoryObserved;long memoryToken,memoryGeneration;
        BufferView(Buffer buffer,BufferStorage storage,Object parent)throws IllegalAccessException {
            this.storage=storage;this.parent=new WeakReference<>(parent);address=(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer);
            int shift=buffer instanceof ByteBuffer?0:buffer instanceof CharBuffer||buffer instanceof ShortBuffer?1:
                    buffer instanceof IntBuffer||buffer instanceof FloatBuffer?2:3;
            bytes=((long)(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer))<<shift;
        }
    }
    private static final class BufferUse {
        final Buffer buffer;final BufferView view;boolean returned,memoryActive,memoryRetaining;Module[] memoryExposure;
        BufferUse(Buffer buffer,BufferView view){this.buffer=buffer;this.view=view;}
    }
    private static final class BufferAllocation {
        final Buffer buffer;final BufferAllocation previous=BUFFER_ALLOCATING.get();final Thread caller=Thread.currentThread();boolean closed;
        BufferAllocation(Buffer buffer){this.buffer=buffer;}
    }
    private static final class BufferWrapRole {
        final String name;final Key loader,module;final boolean bootstrap;Key actual;
        BufferWrapRole(String name,ClassLoader loader,Module module){this.name=name;this.loader=new Key(loader,false);this.module=new Key(module,false);bootstrap=loader==null;}
    }
    private static final class BufferWrapScope {
        final Buffer buffer;final Class<?> requested,declaring;final BufferWrapRole role;final long address;final int capacity;final String method,descriptor;
        final ByteBuffer source;final Object sourceParent,parent;final long sourceAddress;final int sourceCapacity;
        final BufferView sourceView;final BufferStorage storage;final Span parentSpan;
        final Thread caller=Thread.currentThread();final BufferWrapScope previous=BUFFER_WRAPPING.get();boolean closed;
        BufferWrapScope(Buffer buffer,Class<?> requested,long address,int capacity,Class<?> declaring,BufferWrapRole role){
            this.buffer=buffer;this.requested=requested;this.address=address;this.capacity=capacity;this.declaring=declaring;this.role=role;
            method="wrap";descriptor=BUFFER_WRAP_DESCRIPTOR;source=null;sourceParent=null;parent=null;sourceAddress=0;sourceCapacity=0;sourceView=null;storage=null;parentSpan=null;
        }
        BufferWrapScope(Buffer buffer,Class<?> requested,long address,int capacity,Class<?> declaring,BufferWrapRole role,ByteBuffer source,
                Object sourceParent,long sourceAddress,int sourceCapacity,BufferView sourceView,BufferStorage storage,Span parentSpan){
            this.buffer=buffer;this.requested=requested;this.address=address;this.capacity=capacity;this.declaring=declaring;this.role=role;
            method="slice";descriptor=BUFFER_SLICE_DESCRIPTOR;this.source=source;this.sourceParent=sourceParent;parent=sourceParent==null?source:sourceParent;
            this.sourceAddress=sourceAddress;this.sourceCapacity=sourceCapacity;this.sourceView=sourceView;this.storage=storage;this.parentSpan=parentSpan;
        }
    }
    private static final class BufferWrapLayout {
        static final Span ADDRESS=bufferWrapSpan(BufferPlatform.ADDRESS,8),MARK=bufferWrapSpan(BufferPlatform.MARK,4),
                LIMIT=bufferWrapSpan(BufferPlatform.LIMIT,4),CAPACITY=bufferWrapSpan(BufferPlatform.CAPACITY,4);
    }
    private static final class BufferWrapWriter implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,StackWalker.StackFrame> {
        public StackWalker.StackFrame apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass(),nest=type.getNestHost();
                if(nest==ResourceBridge.class||nest==TaskBridge.class||nest==ControlBridge.class||nest==BackingBridge.class
                        ||nest==NativeControl.class||nest==CodeSourceBridge.class||nest==ExecutionFlow.class
                        ||type==BufferPlatform.UNSAFE||type==UNSAFE.getClass())continue;
                return frame;
            }return null;
        }
    }
    private static final BufferWrapWriter BUFFER_WRAP_WRITER=new BufferWrapWriter();
    private record BufferRegistration(Buffer buffer,BufferStorage storage){}
    private record BufferObservation(Buffer buffer,BufferView view,Object action){}
    private record BufferSpan(Object holder,BufferStorage storage,long address,long bytes){}
    private record BufferWrite(BufferSpan span,Object receipt){}
    private record BufferRead(Object receipt,Module[] before){}
    private record BufferRestore(BufferStorage storage,Module[] modules,long cursor,int budget){}
    private static final class IoBufferLease {
        final List<BufferUse> uses;boolean released;
        IoBufferLease(List<BufferUse> uses){this.uses=uses;}
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
        final FileOperation previous=FILE_OPERATIONS.get();
        final Class<?> declaring;
        final String method;
        final Module[] sources;
        PlannedFileMutation planned;Object plannedToken;
        boolean closed;
        FileOperation(FileRecord record,Class<?> declaring,String method,Module[] sources){
            this.record=record;this.declaring=declaring;this.method=method;this.sources=sources;
        }
    }
    /** Core installs its existing Capture/Subject final mutation boundary. */
    public static void installPlannedFileMutation(Class<?> owner,java.lang.invoke.MethodHandle begin,java.lang.invoke.MethodHandle end){
        if(WALKER.getCallerClass()!=owner||owner!=ControlBridge.policyInstaller||ControlBridge.guards==null||ControlBridge.guards.owner()!=owner)
            throw new SecurityException("PLANNED_FILE_MUTATION_CORE_REQUIRED");
        if(!begin.type().equals(java.lang.invoke.MethodType.methodType(Object.class,Object.class,String.class,int.class))
                ||!end.type().equals(java.lang.invoke.MethodType.methodType(void.class,Object.class)))throw new IllegalArgumentException("PLANNED_FILE_MUTATION_SIGNATURE");
        PlannedFileMutation replacement=new PlannedFileMutation(owner,begin,end);
        ControlBridge.controlPublication(STREAMS,new Object[]{replacement,owner,begin,end});plannedFileMutation=replacement;
    }
    public static boolean plannedFileMutationReady(){
        return plannedFileMutation!=null&&NativeControl.fileBoundaryInstalled().startsWith("NATIVE_FILE_BOUNDARY_READY:");
    }
    private static Object plannedFileOperation(Object original,Object endpoint,Class<?> declaring,String method,Module[] sources,int operation,String path)throws IOException{
        PlannedFileMutation guard=plannedFileMutation;if(guard==null)return original;
        FileOperation token=original instanceof FileOperation actual?actual:new FileOperation(null,declaring,method,sources);
        boolean temporary=original==null;
        // Publish/allocate the existing scope before Core takes any commit
        // gates. Only the one actual JNI effect retains the returned token.
        if(temporary)FILE_OPERATIONS.set(token);
        try{
            Object held=(Object)guard.begin.invokeExact(endpoint,path,operation);
            if(held==null){if(temporary)finishFileOperation(token);return original;}
            token.planned=guard;token.plannedToken=held;return token;
        }catch(Throwable failure){
            try{finishFileOperation(token);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            if(failure instanceof IOException io)throw io;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
            throw new IOException("PLANNED_FILE_MUTATION_UNAVAILABLE",failure);
        }
    }
    private static final class NativeFilePair {
        final Thread caller=Thread.currentThread();
        final FileOperation source,target;
        NativeFilePair(FileOperation source,FileOperation target){this.source=source;this.target=target;}
    }
    /** Only one actual duplicateForMapping frame may consume its captured parent. */
    private static final class MappingDuplicate {
        final Thread caller=Thread.currentThread();final MappingDuplicate previous=MAPPING_DUPLICATING.get();
        final Object dispatcher;final FileDescriptor parent;final FileOperation operation;final Module[] sources;
        final long handle;final long[] frame;
        FileDescriptor target;MappingDuplicateNative nativeCall;int phase;boolean published;volatile boolean closed;
        MappingDuplicate(Object dispatcher,FileDescriptor parent,FileOperation operation,Module[] sources,long handle,long[] frame){
            this.dispatcher=dispatcher;this.parent=parent;this.operation=operation;this.sources=sources;this.handle=handle;this.frame=frame;
        }
    }
    private static final class MappingDuplicateNative {
        final MappingDuplicate scope;final FileOperation operation;final Module[] sources;
        volatile long returned;volatile boolean nativeSucceeded;boolean completed,closed;
        MappingDuplicateNative(MappingDuplicate scope,FileOperation operation,Module[] sources){this.scope=scope;this.operation=operation;this.sources=sources;}
    }
    private record DescriptorView(FileDescriptor descriptor,List<Object> streams,List<Object> channels,boolean allocated) { }
    static {
        for(Class<?> type:List.of(FileInputStream.class,FileOutputStream.class,RandomAccessFile.class))
            LAYOUTS.put(type,new Layout(type,field(type,"fd"),field(type,"path"),field(type,"closed"),field(type,"channel")));
    }
    private ResourceBridge(){ }
    private static Field field(Class<?> type,String name){
        try{Field field=PlatformMetadata.declaredField(type,name);field.setAccessible(true);return field;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static Class<?> optionalPlatformType(String name){
        try{return Class.forName(name,false,null);}catch(ClassNotFoundException absent){return null;}
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
        boolean retired,destroyRequested,cleanerStarted,cleanerFinished;Future<String> release;ReleasePhase releasePhase;
        final java.util.concurrent.locks.ReentrantLock observation=new java.util.concurrent.locks.ReentrantLock();
        ProcessRecord(long handle,Runnable action){this.handle=handle;originalAction=action;}
    }
    private static final class PipeRecord {
        final Object view,stream;final FileDescriptor descriptor;final byte[] buffer;
        volatile boolean unknownExposure;boolean released,viewCloseStarted;
        PipeRecord(Object view,Object stream,FileDescriptor descriptor,byte[] buffer){this.view=view;this.stream=stream;this.descriptor=descriptor;this.buffer=buffer;}
    }
    public static void processStart()throws IOException {
        platform(PROCESS_TYPE,"start");
        if(!ControlBridge.taskEffectAllowed(null,"process-start"))throw new IOException("RONOVA_TERMINAL_PROCESS_START_REFUSED");
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
                    ControlBridge.resourceCreated(stream);if(view!=stream)ControlBridge.resourceCreated(view);ControlBridge.resourceCreated(descriptor);
                }
                ControlBridge.resourceCreated(process);ControlBridge.resourceCreated(record.processHandle);
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
                record.pipelineDescriptor=descriptor;ControlBridge.resourceCreated(descriptor);
            }catch(IllegalAccessException failed){record.gap="PROCESS_PIPELINE_PUBLICATION_UNOBSERVED";}
        }
    }
    public static void processPipeExposed(Process process,Object view,String method){
        platform(PROCESS_TYPE,method);PipeRecord pipe;ProcessRecord record;
        synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));WeakReference<PipeRecord> link=PROCESS_VIEWS.get(new Key(view,false));pipe=link==null?null:link.get();}
        if(record!=null){
            Module[] sources=ControlBridge.invokingSources();NativeControl.processExposed(process,record.hostToken,sources);
            ControlBridge.resourceExposed(process);if(view==record.processHandle)ControlBridge.resourceExposed(view);
        }
        if(pipe!=null&&!ControlBridge.resourceExposed(view))pipe.unknownExposure=true;
    }
    public static boolean retireProcess(Process process,Callable<String> action)throws IOException {
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(action))throw new SecurityException("PROCESS_RECOVERY_CORE_REQUIRED");
        ProcessRecord record;synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));}
        if(record==null)throw new IOException("PROCESS_CONSTRUCTOR_ASSOCIATION_UNOBSERVED");
        if(!record.observation.tryLock())return false;
        try{
            if(record.retired)return true;
            if(record.release==null){
                try{record.releasePhase=new ReleasePhase();FutureTask<String> future=releaseTask(action,record.releasePhase);record.release=future;CLOSER.execute(future);}
                catch(RejectedExecutionException full){record.release=null;throw new IOException("RESOURCE_DISPOSITION_CAPACITY",full);}
                return false;
            }
            if(!record.release.isDone())return false;
            // Once called, retain this exact result for later observation.
            // An exit/close may already have happened even when its outcome is
            // pending or exceptional; a later tick cannot submit it anew.
            Future<String> result=record.release;
            try{String gap=result.get();if(!gap.isEmpty()){
                    if(record.releasePhase.pending&&!record.releasePhase.effects)record.release=null;
                    throw new IOException(gap);
                }record.retired=true;return true;}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("PROCESS_RELEASE_OBSERVATION_INTERRUPTED",interrupted);}
            catch(ExecutionException failed){throw new IOException("PROCESS_RELEASE_FAILED",failed.getCause());}
        }finally{record.observation.unlock();}
    }
    public static String releaseProcess(Process process,Predicate<Object> selected){
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(selected))throw new SecurityException("PROCESS_RELEASE_CORE_REQUIRED");
        ProcessRecord record;synchronized(STREAMS){record=PROCESSES.get(new Key(process,false));}
        if(record==null)return "PROCESS_CONSTRUCTOR_ASSOCIATION_UNOBSERVED";
        try{
            if(!selected.test(process))return "PROCESS_QUALIFICATION_CHANGED";
            if(field(PROCESS_TYPE,"handle").getLong(process)!=record.handle||field(PROCESS_TYPE,"processHandle").get(process)!=record.processHandle)return "PROCESS_ORIGINAL_ASSOCIATION_CHANGED";
            // This advances/queries the same captured Host token. A pending
            // Host result never manufactures another Java process handle.
            String hostGap=NativeControl.retireProcess(process,record.hostToken);if(!hostGap.isEmpty())return releasePending(hostGap);
            if(record.handleClosed){
                if(record.cleanerFinished&&record.pipes!=null&&Arrays.stream(record.pipes).allMatch(pipe->pipe==null||pipe.released)){record.retired=true;return "";}
                return record.retired?"":"PROCESS_HANDLE_CLOSED_BEFORE_PIPE_RELEASE";
            }
            if(!record.destroyRequested){record.destroyRequested=true;releaseEffectStarted();process.destroyForcibly();releaseStepCompleted();}
            if(process.isAlive())return releasePending("PROCESS_EXIT_PENDING");
            String gap=record.gap;
            if(!gap.isEmpty())return gap;
            if(record.pipelineHandle!=-1&&(record.pipelineDescriptor==null||record.pipelineDescriptor.valid()||!FD_CLOSED.getBoolean(record.pipelineDescriptor)||FD_HANDLE.getLong(record.pipelineDescriptor)!=-1))return releasePending("PROCESS_PIPELINE_PARENT_ENDPOINT_RELEASE_PENDING");
            if(record.pipes!=null)for(PipeRecord pipe:record.pipes){
                if(pipe==null||pipe.released)continue;
                if(pipe.unknownExposure||!selected.test(pipe.view)||!selected.test(pipe.stream))return "PROCESS_PIPE_SHARED_OR_UNQUALIFIED";
                String remaining=release(pipe.stream,selected);if(!remaining.isEmpty())return remaining;
                if(pipe.view.getClass()==BufferedInputStream.class){
                    Object current=field(FilterInputStream.class,"in").get(pipe.view);
                    if(current!=null&&current!=pipe.stream)return "PROCESS_BUFFERED_PIPE_ASSOCIATION_CHANGED";
                    if(!pipe.viewCloseStarted){pipe.viewCloseStarted=true;releaseEffectStarted();((BufferedInputStream)pipe.view).close();releaseStepCompleted();}
                    if(field(FilterInputStream.class,"in").get(pipe.view)!=null||field(BufferedInputStream.class,"buf").get(pipe.view)!=null)return releasePending("PROCESS_BUFFERED_INPUT_RELEASE_PENDING");
                }else if(pipe.view.getClass()==BufferedOutputStream.class){
                    synchronized(pipe.view){
                        if(!pipe.viewCloseStarted){
                            if(field(FilterOutputStream.class,"out").get(pipe.view)!=pipe.stream||field(BufferedOutputStream.class,"buf").get(pipe.view)!=pipe.buffer)return "PROCESS_BUFFERED_PIPE_ASSOCIATION_CHANGED";
                            pipe.viewCloseStarted=true;releaseEffectStarted();field(BufferedOutputStream.class,"count").setInt(pipe.view,0);((BufferedOutputStream)pipe.view).close();releaseStepCompleted();
                        }
                        if(!field(FilterOutputStream.class,"closed").getBoolean(pipe.view))return releasePending("PROCESS_BUFFERED_OUTPUT_RELEASE_PENDING");
                    }
                }
                if(pipe.descriptor.valid())return releasePending("PROCESS_ORIGINAL_PIPE_RELEASE_PENDING");
                if(pipe.buffer!=null){releaseEffectStarted();Arrays.fill(pipe.buffer,(byte)0);}pipe.released=true;releaseStepCompleted();
            }
            if(record.cleaner==null)return "PROCESS_CLEANER_ASSOCIATION_UNOBSERVED";
            if(!record.cleanerStarted){record.cleanerStarted=true;releaseEffectStarted();record.cleaner.clean();record.cleanerFinished=true;releaseStepCompleted();}
            if(record.handleClosed){record.retired=true;return "";}return releasePending("PROCESS_NATIVE_HANDLE_RELEASE_PENDING");
        }catch(ReflectiveOperationException|IOException|RuntimeException failed){releaseUncertain();return "PROCESS_RELEASE_FAILED:"+failed.getClass().getSimpleName()+":"+failed.getMessage();}
    }
    private static void reap(){
        for(int i=0;i<32;i++){Key key=(Key)RETIRED.poll();if(key==null)return;STREAMS.remove(key);WRAPPERS.remove(key);CHANNELS.remove(key);ATTACHMENTS.remove(key);PROCESSES.remove(key);PROCESS_VIEWS.remove(key);BUFFERS.remove(key);BUFFER_CLEANERS.remove(key);BUFFER_ACTIONS.remove(key);}
    }
    /** STREAMS is held; descriptor publication must not link a callback under the resource lock. */
    private static DescriptorRecord descriptorRecord(FileDescriptor descriptor){
        Key key=new Key(descriptor,true);DescriptorRecord record=ATTACHMENTS.get(key);
        if(record==null){record=new DescriptorRecord();ATTACHMENTS.put(key,record);}
        return record;
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
        try{if(!ControlBridge.recoveryWriter()&&(!ControlBridge.taskEffectAllowed(null,"file-close")||!descriptorReleaseAllowed(descriptor)))throw new IOException("RONOVA_FILE_DESCRIPTOR_CLOSE_REFUSED");}
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
            reap();var aliases=descriptorRecord(descriptor).aliases;
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
        try{Module[] sources=IoBridge.currentSources();if(sources==null)ControlBridge.resourceCreated(value);else ControlBridge.resourceCreated(value,sources);}finally{FILE_OBSERVING.remove();}
    }
    /** The Windows factory has completed CreateFile and installed this exact handle in the returned descriptor. */
    public static void descriptorOpened(FileDescriptor descriptor){
        platform(FILE_FACTORY,"open");
        synchronized(descriptor){synchronized(STREAMS){
            reap();descriptorRecord(descriptor).allocated=true;
        }}
    }
    public static void fileOpening(Class<?> declaring,String method)throws IOException {
        platform(declaring,method);
        if(declaring!=FILE_FACTORY&&!LAYOUTS.containsKey(declaring))throw new SecurityException("ACTUAL_FILE_OPEN_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{if(!ControlBridge.recoveryWriter()&&!ControlBridge.taskEffectAllowed(null,"file-open")){
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
            reap();DescriptorRecord attachment=descriptorRecord(descriptor);
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
    public static Object mappingDuplicateBegin(Object dispatcher,FileDescriptor parent)throws IOException {
        if(dispatcher==null||dispatcher.getClass()!=FILE_DISPATCHER||parent==null)throw new IOException("MAPPING_DUPLICATE_PARENT_REQUIRED");
        long[] frame=new long[5];
        if(!NativeControl.mappingDuplicateFrame(frame,0))throw new IOException("MAPPING_DUPLICATE_ORIGINAL_FRAME_REQUIRED");
        if(!ControlBridge.taskEffectAllowed(parent,"file-duplicate"))throw new ClosedChannelException();
        FileOperation operation=null;
        try{
            long handle;
            synchronized(parent){handle=FD_HANDLE.getLong(parent);if(handle==-1||FD_CLOSED.getBoolean(parent))throw new ClosedChannelException();}
            operation=(FileOperation)fileOperation(parent,FILE_DISPATCHER,"duplicateForMapping",null);
            Module[] sources=operation==null?ControlBridge.resourceOperationSources(null):operation.sources;
            MappingDuplicate scope=new MappingDuplicate(dispatcher,parent,operation,sources,handle,frame);
            mappingDuplicateParent(scope);
            CodeSourceBridge.resourceLayoutControls(scope);CodeSourceBridge.resourceLayoutControls(frame);CodeSourceBridge.resourceLayoutControls(sources);
            CodeSourceBridge.resourceLayoutControls(MAPPING_DUPLICATE_PENDING);
            MAPPING_DUPLICATING.set(scope);return scope;
        }catch(IllegalAccessException failure){
            IOException unavailable=new IOException("MAPPING_DUPLICATE_PARENT_UNAVAILABLE",failure);
            try{if(operation!=null)finishFileOperation(operation);}catch(RuntimeException|Error cleanup){unavailable.addSuppressed(cleanup);}throw unavailable;
        }catch(IOException|RuntimeException|Error failure){
            try{if(operation!=null)finishFileOperation(operation);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private static MappingDuplicate mappingDuplicate(Object value)throws IOException {
        if(!(value instanceof MappingDuplicate scope)||scope.caller!=Thread.currentThread()||scope.closed||MAPPING_DUPLICATING.get()!=scope)
            throw new IOException("MAPPING_DUPLICATE_SCOPE_REQUIRED");
        return scope;
    }
    private static void mappingDuplicateParent(MappingDuplicate scope)throws IllegalAccessException,IOException {
        Object access=DISPATCHER_FD_ACCESS.get(null);
        if(access==null||access.getClass()!=FD_ACCESS)throw new IOException("MAPPING_DUPLICATE_ORIGINAL_ACCESSOR_REQUIRED");
        synchronized(scope.parent){
            if(FD_HANDLE.getLong(scope.parent)!=scope.handle||FD_CLOSED.getBoolean(scope.parent))throw new IOException("MAPPING_DUPLICATE_PARENT_CHANGED");
        }
        synchronized(STREAMS){FileRecord record=fileRecord(scope.parent);if(record!=null&&record.sealed)throw new ClosedChannelException();}
    }
    private static boolean mappingDuplicateFresh(FileDescriptor target)throws IllegalAccessException {
        return FD_HANDLE.getLong(target)==-1&&FD_NUMBER.getInt(target)==-1&&!FD_CLOSED.getBoolean(target)
                &&FD_PARENT.get(target)==null&&FD_PEERS.get(target)==null&&FD_CLEANUP.get(target)==null;
    }
    public static void mappingDuplicateAllocated(Object value,FileDescriptor target)throws IOException {
        MappingDuplicate scope=mappingDuplicate(value);
        if(scope.phase!=0||target==null||target==scope.parent||!NativeControl.mappingDuplicateFrame(scope.frame,2))
            throw new IOException("MAPPING_DUPLICATE_NEW_DESCRIPTOR_REQUIRED");
        try{
            synchronized(target){if(!mappingDuplicateFresh(target))throw new IOException("MAPPING_DUPLICATE_NEW_DESCRIPTOR_CHANGED");}
            scope.target=target;scope.phase=1;
        }catch(IllegalAccessException failure){throw new IOException("MAPPING_DUPLICATE_DESCRIPTOR_UNAVAILABLE",failure);}
    }
    public static void mappingDuplicateCalling(Object value,long handle)throws IOException {
        MappingDuplicate scope=mappingDuplicate(value);
        if(scope.phase!=1||handle!=scope.handle||!NativeControl.mappingDuplicateFrame(scope.frame,2))
            throw new IOException("MAPPING_DUPLICATE_ORIGINAL_HANDLE_REQUIRED");
        try{
            mappingDuplicateParent(scope);
            synchronized(scope.target){if(!mappingDuplicateFresh(scope.target))throw new IOException("MAPPING_DUPLICATE_NEW_DESCRIPTOR_CHANGED");}
            // There is no Java call between this return and the woven actual native invocation.
            scope.phase=2;
        }catch(IllegalAccessException failure){throw new IOException("MAPPING_DUPLICATE_DESCRIPTOR_UNAVAILABLE",failure);}
    }
    private static Object nativeMappingDuplicate(Object receiver,Class<?> declaring,String method,Module[] sources,long handle)throws IOException {
        MappingDuplicate scope=mappingDuplicate(MAPPING_DUPLICATING.get());
        if(receiver!=FILE_DISPATCHER||declaring!=FILE_DISPATCHER||!method.equals("duplicateHandle")||scope.phase!=2
                ||handle!=scope.handle||!NativeControl.mappingDuplicateFrame(scope.frame,1))
            throw new IOException("MAPPING_DUPLICATE_ACTUAL_NATIVE_CALL_REQUIRED");
        for(Module source:sources)if(source!=null&&ControlBridge.modStopped(source))throw new ClosedChannelException();
        FileOperation operation=null;
        try{
            mappingDuplicateParent(scope);
            synchronized(scope.target){if(!mappingDuplicateFresh(scope.target))throw new IOException("MAPPING_DUPLICATE_NEW_DESCRIPTOR_CHANGED");}
            operation=(FileOperation)fileOperation(scope.parent,declaring,method,sources);
            Module[] actual=operation==null?ControlBridge.resourceOperationSources(sources):operation.sources;
            MappingDuplicateNative token=new MappingDuplicateNative(scope,operation,actual);
            CodeSourceBridge.resourceLayoutControls(token);CodeSourceBridge.resourceLayoutControls(actual);
            // Reserve before entering the OS. A later Java publication failure
            // retains this exact new target/return receipt without claiming it allocated.
            synchronized(STREAMS){descriptorRecord(scope.target);MAPPING_DUPLICATE_PENDING.put(scope.target,token);}
            scope.nativeCall=token;scope.phase=3;return token;
        }catch(IllegalAccessException failure){
            IOException unavailable=new IOException("MAPPING_DUPLICATE_DESCRIPTOR_UNAVAILABLE",failure);
            try{if(operation!=null)finishFileOperation(operation);}catch(RuntimeException|Error cleanup){unavailable.addSuppressed(cleanup);}throw unavailable;
        }catch(IOException|RuntimeException|Error failure){
            try{if(operation!=null)finishFileOperation(operation);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    /** The real JNI return register is captured before the original Java setHandle. */
    private static void nativeMappingDuplicateFinished(Object value,long returned,boolean completed){
        if(!NativeControl.fileOperationFinishing(value)||!(value instanceof MappingDuplicateNative token)||token.closed)
            throw new SecurityException("MAPPING_DUPLICATE_ACTUAL_RETURN_REQUIRED");
        MappingDuplicate scope=token.scope;
        try{
            token.completed=completed&&token.nativeSucceeded&&token.returned==returned&&scope.caller==Thread.currentThread()&&!scope.closed&&MAPPING_DUPLICATING.get()==scope
                    &&scope.nativeCall==token&&scope.phase==3;
        }finally{
            token.closed=true;
            if(token.operation!=null)finishFileOperation(token.operation);
        }
    }
    private static void mappingDuplicateResult(MappingDuplicateNative call,FileDescriptor result)throws IllegalAccessException,IOException {
        Object cleanup=FD_CLEANUP.get(result);
        if(FD_HANDLE.getLong(result)!=call.returned||FD_NUMBER.getInt(result)!=-1||FD_CLOSED.getBoolean(result)
                ||FD_PARENT.get(result)!=null||FD_PEERS.get(result)!=null||cleanup==null||cleanup.getClass()!=FILE_CLEANABLE
                ||CLEANABLE_REFERENT.get(cleanup)!=result||CLEANABLE_HANDLE.getLong(cleanup)!=call.returned||CLEANABLE_NUMBER.getInt(cleanup)!=-1)
            throw new IOException("MAPPING_DUPLICATE_ORIGINAL_PUBLICATION_CHANGED");
    }
    public static void mappingDuplicateExit(Object value,FileDescriptor result,Throwable original)throws IOException {
        if(!(value instanceof MappingDuplicate scope)||scope.caller!=Thread.currentThread())throw new SecurityException("MAPPING_DUPLICATE_SCOPE_REQUIRED");
        if(scope.closed)return;
        Throwable primary=original;
        try{
            if(MAPPING_DUPLICATING.get()!=scope)throw new IOException("MAPPING_DUPLICATE_SCOPE_ORDER_REQUIRED");
            if(original==null){
                MappingDuplicateNative call=scope.nativeCall;
                if(scope.phase!=3||result!=scope.target||call==null||!call.closed||!call.completed||!call.nativeSucceeded||call.returned==-1
                        ||!NativeControl.mappingDuplicateFrame(scope.frame,2))throw new IOException("MAPPING_DUPLICATE_NATIVE_RESULT_UNOBSERVED");
                mappingDuplicateParent(scope);
                synchronized(result){
                    mappingDuplicateResult(call,result);
                    // Finish the parent's operation before publishing allocation;
                    // a receipt-exit failure must leave this copy unpublished.
                    if(scope.operation!=null&&!scope.operation.closed)finishFileOperation(scope.operation);
                    // Establish allocation only from this new object and the captured actual JNI result.
                    // A reusable OS number alone never creates a descriptor or release record.
                    ControlBridge.resourceCreated(result,call.sources);
                    // Creation delivery can run its existing observer; it cannot
                    // substitute either endpoint before allocation publication.
                    mappingDuplicateParent(scope);mappingDuplicateResult(call,result);
                    if(!NativeControl.mappingDuplicateFrame(scope.frame,2))throw new IOException("MAPPING_DUPLICATE_ORIGINAL_FRAME_CHANGED");
                    synchronized(STREAMS){
                        DescriptorRecord record=descriptorRecord(result);record.allocated=true;
                        if(call.sources.length==0)record.unknownUse=true;
                        for(Module source:call.sources){if(source==null)record.unknownUse=true;else record.nativeSources.add(source);}
                        scope.published=true;MAPPING_DUPLICATE_PENDING.remove(result);
                    }
                }
            }
        }catch(IllegalAccessException failure){
            IOException unavailable=new IOException("MAPPING_DUPLICATE_PUBLICATION_UNAVAILABLE",failure);
            if(original==null){primary=unavailable;throw unavailable;}original.addSuppressed(unavailable);
        }catch(IOException|RuntimeException|Error failure){
            if(original==null){primary=failure;throw failure;}if(failure!=original)original.addSuppressed(failure);
        }finally{
            try{
                // A JNI global-reference failure can occur after the Java entry
                // acquired its nested receipt but before Native retained its token.
                MappingDuplicateNative call=scope.nativeCall;
                if(call!=null){call.closed=true;if(call.operation!=null&&!call.operation.closed)finishFileOperation(call.operation);}
                if(scope.operation!=null&&!scope.operation.closed)finishFileOperation(scope.operation);
            }
            catch(RuntimeException|Error cleanup){if(primary==null)throw cleanup;if(cleanup!=primary)primary.addSuppressed(cleanup);}
            finally{
                MappingDuplicateNative call=scope.nativeCall;
                synchronized(STREAMS){if(call!=null&&(!call.nativeSucceeded||scope.published))MAPPING_DUPLICATE_PENDING.remove(scope.target);}
                scope.closed=true;if(scope.previous==null)MAPPING_DUPLICATING.remove();else MAPPING_DUPLICATING.set(scope.previous);
            }
        }
    }
    private static Object nativeIoOperation(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources)throws IOException {
        if(!NativeControl.ioOperationBoundary(declaring,method,arguments,references,sources))throw new SecurityException("ACTUAL_NATIVE_IO_OPERATION_REQUIRED");
        if(declaring==FILE_CHANNEL&&method.equals("transferTo0"))return nativeFileTransfer(declaring,method,arguments,references,sources);
        if(declaring==SOCKET_NET&&method.equals("accept"))return nativeSocketAccept(declaring,method,arguments,references,sources);
        return IoBridge.nativeInvoking(declaring,method,arguments,references,sources);
    }
    /** Direct transfer consumes both captured descriptors without calling the target's write method. */
    private static Object nativeFileTransfer(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources)throws IOException {
        if(arguments.length!=4||references.length!=4||!(references[0] instanceof FileDescriptor source)
                ||!(references[3] instanceof FileDescriptor target)||references[1]!=null||references[2]!=null)
            throw new IOException("NATIVE_FILE_TRANSFER_DESCRIPTORS_REQUIRED");
        return nativeFilePair(source,target,declaring,method,sources);
    }
    /** The accepted descriptor is an output endpoint; observation grants no allocation or close authority. */
    private static Object nativeSocketAccept(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources)throws IOException {
        if(arguments.length!=3||references.length!=3||!(references[0] instanceof FileDescriptor source)
                ||!(references[1] instanceof FileDescriptor target)||!(references[2] instanceof java.net.InetSocketAddress[]))
            throw new IOException("NATIVE_SOCKET_ACCEPT_DESCRIPTORS_REQUIRED");
        return nativeFilePair(source,target,declaring,method,sources);
    }
    private static Object nativeFilePair(FileDescriptor source,FileDescriptor target,Class<?> declaring,String method,Module[] sources)throws IOException {
        if(ControlBridge.recoveryWriter())return null;
        for(Module module:sources)if(module!=null&&ControlBridge.modStopped(module))throw new ClosedChannelException();
        FileOperation sourceScope=null,targetScope=null;
        try{
            sourceScope=(FileOperation)fileOperation(source,declaring,method,sources);
            if(target!=source)targetScope=(FileOperation)fileOperation(target,declaring,method,sources);
            return new NativeFilePair(sourceScope,targetScope);
        }catch(IOException|RuntimeException|Error failure){
            try{if(targetScope!=null)finishFileOperation(targetScope);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            try{if(sourceScope!=null)finishFileOperation(sourceScope);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    private static void nativeIoFinished(Object token,boolean completed){
        if(!NativeControl.fileOperationFinishing(token))throw new SecurityException("ACTUAL_NATIVE_IO_RETURN_REQUIRED");
        if(token instanceof NativeFilePair pair){
            if(pair.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_NATIVE_FILE_PAIR_SCOPE_REQUIRED");
            if(pair.target!=null)finishFileOperation(pair.target);
            if(pair.source!=null)finishFileOperation(pair.source);
            return;
        }
        IoBridge.nativeReturned(token,completed);
    }
    /** Entered only while the actual JNI binding keeps this receiver and source tuple live. */
    public static Object nativeFileOperation(Object value,Class<?> declaring,String method,Module[] sources,int operation,long address,long length,String path)throws IOException {
        if(!NativeControl.fileOperationBoundary(value,declaring,method,sources,operation,address,length,path))throw new SecurityException("ACTUAL_NATIVE_FILE_OPERATION_REQUIRED");
        if(operation==10)return nativeMappingDuplicate(value,declaring,method,sources,address);
        if(operation==9){
            if(!ControlBridge.recoveryWriter()){
                for(Module source:sources)if(source!=null&&ControlBridge.modStopped(source))throw new java.net.SocketException("RONOVA_TERMINAL_SOCKET_OPEN_REFUSED");
                if(!ControlBridge.taskEffectAllowed(null,"socket-open"))throw new java.net.SocketException("RONOVA_TERMINAL_SOCKET_OPEN_REFUSED");
            }
            return null;
        }
        if(operation==7){IoBridge.nativeSocketClosing(declaring,method,address,sources);return null;}
        if(operation==6){
            synchronized(STREAMS){for(BufferStorage storage:BUFFER_ACTIONS.values())
                if(storage.descriptor!=null&&!storage.cleaned&&storage.address==address&&BUFFER_CLEANING.get()!=storage)
                    throw new IOException("MAPPING_ORIGINAL_CLEANUP_REQUIRED");}
            return null;
        }
        if(operation==2){
            if(!ControlBridge.recoveryWriter()){
                if(!ControlBridge.taskEffectAllowed(null,"file-open"))throw new FileNotFoundException("RONOVA_TERMINAL_FILE_OPEN_REFUSED");
                synchronized(STREAMS){if(fileRecord(value)!=null)throw new FileNotFoundException("FILE_DESCRIPTOR_REOPEN_REFUSED");}
            }
            try{return plannedFileOperation(null,value,declaring,method,sources,operation,path);}
            catch(FileNotFoundException failure){throw failure;}
            catch(IOException failure){FileNotFoundException denied=new FileNotFoundException(failure.getMessage());denied.initCause(failure);throw denied;}
        }
        if(operation==4&&IoBridge.closingDescriptor((FileDescriptor)value))return null;
        if(operation==4&&!ControlBridge.recoveryWriter())for(Module source:sources)if(source!=null&&ControlBridge.modStopped(source))throw new IOException("RONOVA_TERMINAL_FILE_CLOSE_REFUSED");
        if(operation==4&&!ControlBridge.recoveryWriter()&&(!ControlBridge.taskEffectAllowed(value,"file-close")||!descriptorReleaseAllowed((FileDescriptor)value)))throw new IOException("RONOVA_FILE_DESCRIPTOR_CLOSE_REFUSED");
        try{
            Object token=fileOperation(value,declaring,method,sources);
            return operation==1||operation==3?plannedFileOperation(token,value,declaring,method,sources,operation,null):token;
        }
        catch(IOException failure){
            if(operation!=5)throw failure;
            SyncFailedException sync=new SyncFailedException(failure.getMessage());sync.initCause(failure);throw sync;
        }
    }
    private static Object fileOperation(Object value,Class<?> declaring,String method,Module[] nativeSources)throws IOException {
        if(FILE_OBSERVING.get()!=null)return null;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{
            if(ControlBridge.recoveryWriter())return null;
            if(!ControlBridge.taskEffectAllowed(null,"file-io"))throw new IOException("RONOVA_TERMINAL_FILE_IO_REFUSED");
            FileRecord record;
            synchronized(STREAMS){
                record=fileRecord(value);
                if(record==null&&nativeSources!=null&&value instanceof FileDescriptor)
                    record=descriptorRecord((FileDescriptor)value);
                // An unobserved endpoint still has this real call's source scope;
                // it gains no release record or authority from that scope.
                if(record!=null){if(record.sealed)throw new ClosedChannelException();record.observing++;}
            }
            try{
                Module[] sources=ControlBridge.resourceOperationSources(nativeSources);
                boolean known=sources.length!=0;
                if(record instanceof DescriptorRecord descriptor){
                    synchronized(STREAMS){for(Module source:sources){if(source==null)known=false;else descriptor.nativeSources.add(source);}}
                }else if(record!=null)known=ControlBridge.resourceExposedByOperation(value,sources);
                FileOperation token=new FileOperation(record,declaring,method,sources);
                synchronized(STREAMS){if(record!=null){if(!known)record.unknownUse=true;record.active++;}}
                FILE_OPERATIONS.set(token);return token;
            }finally{synchronized(STREAMS){if(record!=null)record.observing--;}}
        }finally{FILE_OBSERVING.remove();}
    }
    public static void fileOperationFinished(Object value){
        if(value==null)return;
        if(!(value instanceof FileOperation token)||token.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_FILE_OPERATION_SCOPE_REQUIRED");
        platform(token.declaring,token.method);finishFileOperation(token);
    }
    public static void nativeFileOperationFinished(Object value){
        if(!NativeControl.fileOperationFinishing(value)||!(value instanceof FileOperation token)||token.caller!=Thread.currentThread())
            throw new SecurityException("ACTUAL_NATIVE_FILE_OPERATION_SCOPE_REQUIRED");
        finishFileOperation(token);
    }
    private static void finishFileOperation(FileOperation token){
        if(token.closed)return;
        boolean current=FILE_OPERATIONS.get()==token;
        try{
            if(!current)throw new SecurityException("ACTUAL_FILE_OPERATION_ORDER_REQUIRED");
            synchronized(STREAMS){
                token.closed=true;
                if(token.record!=null){token.record.active--;if(token.record instanceof ChannelRecord record)releaseChannelAlias(record);}
            }
        }finally{
            if(current){if(token.previous==null)FILE_OPERATIONS.remove();else FILE_OPERATIONS.set(token.previous);}
            if(token.plannedToken!=null){
                Object held=token.plannedToken;PlannedFileMutation guard=token.planned;token.plannedToken=null;
                try{guard.end.invokeExact(held);}
                catch(RuntimeException|Error failure){throw failure;}
                catch(Throwable failure){throw new IllegalStateException("PLANNED_FILE_MUTATION_FINISH",failure);}
            }
        }
    }
    static Module[] currentFileSources(){
        FileOperation token=FILE_OPERATIONS.get();return token==null||token.closed?null:token.sources;
    }
    public static void fileClosing(Object value,Class<?> declaring,String method)throws IOException {
        platform(declaring,method);
        if(declaring==AbstractInterruptibleChannel.class){if(!fileChannel(value))return;}
        else if(!LAYOUTS.containsKey(declaring))throw new SecurityException("ACTUAL_FILE_CLOSE_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return;
        FILE_OBSERVING.set(Boolean.TRUE);
        try{if(!ControlBridge.recoveryWriter()&&(!ControlBridge.taskEffectAllowed(null,"file-close")||!ControlBridge.resourceReleaseAllowed(value)))throw new IOException("RONOVA_FILE_CLOSE_REFUSED");}
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
        if(!ControlBridge.resourceReleaseAllowed(descriptor))return false;
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
        for(Object alias:aliases)if(!ControlBridge.resourceReleaseAllowed(alias))return false;return true;
    }
    static Object ioLock(){return STREAMS;}
    static boolean ioDescriptorCloseAllowed(FileDescriptor descriptor){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_DESCRIPTOR_CLOSE_REQUIRED");
        return IoBridge.closingDescriptor(descriptor)||descriptorReleaseAllowed(descriptor);
    }
    /** STREAMS is held: the kernel sees only the same live aliases already admitted by Java retirement. */
    private static List<Object> kernelFileAliases(DescriptorRecord record,FileDescriptor descriptor){
        List<Object> aliases=new ArrayList<>();for(var reference:record.aliases){Object value=reference.get();if(value==null)return null;aliases.add(value);}for(var reference:record.channels){Object value=reference.get();if(value==null)return null;aliases.add(value);}
        for(BufferStorage storage:BUFFER_ACTIONS.values())if(storage.descriptor==descriptor&&!storage.cleaned){if(storage.unknownUse||storage.active!=0||storage.observing!=0||storage.views.isEmpty())return null;for(var reference:storage.views){Buffer value=reference.get();if(value==null)return null;if(!identityContains(aliases,value))aliases.add(value);}}
        for(Object value:IoBridge.descriptorAliases(descriptor))if(!identityContains(aliases,value))aliases.add(value);return aliases;
    }
    private static boolean kernelFileQualified(FileKernelCandidate candidate)throws ReflectiveOperationException,IOException{
        if(candidate==null||candidate.thread!=Thread.currentThread()||FILE_SELECTION.get()!=candidate.selection||candidate.selection.isEmpty()||ATTACHMENTS.get(new Key(candidate.descriptor,false))!=candidate.record
                ||candidate.record.unknownUse||candidate.record.active!=0||candidate.record.observing!=0||!candidate.descriptor.valid()||candidate.handle==0||candidate.handle==-1
                ||FD_HANDLE.getLong(candidate.descriptor)!=candidate.handle||FD_NUMBER.getInt(candidate.descriptor)!=candidate.number)return false;
        for(Module source:candidate.selection)if(source==null||!ControlBridge.modStopped(source))return false;for(Module source:candidate.record.nativeSources)if(source==null||!candidate.selection.contains(source))return false;
        List<Object> aliases=kernelFileAliases(candidate.record,candidate.descriptor);if(aliases==null)return false;for(Object alias:aliases){if(!identityContains(candidate.aliases,alias)||!candidate.selected.test(alias))return false;FileRecord record=fileRecord(alias);if(record==null||record.unknownUse||record.active!=0||record.observing!=0)return false;
            if(record instanceof StreamRecord stream&&(stream.descriptor.get()!=candidate.descriptor||stream.layout.descriptor.get(alias)!=candidate.descriptor||!Objects.equals(stream.layout.path.get(alias),stream.path)||stream.number!=candidate.number||stream.handle!=candidate.handle))return false;
            if(record instanceof ChannelRecord channel)channelAssociation(alias,channel,candidate.descriptor);
        }
        for(Object alias:candidate.aliases)if(!identityContains(aliases,alias))return false;
        Object parent=FD_PARENT.get(candidate.descriptor),peers=FD_PEERS.get(candidate.descriptor);if(parent!=null&&!identityContains(aliases,parent))return false;if(peers!=null){if(peers.getClass()!=ArrayList.class)return false;for(Object peer:(List<?>)peers)if(!identityContains(aliases,peer))return false;}
        return true;
    }
    public static boolean kernelFileActive(FileDescriptor descriptor,Module[] actualSelection){
        if(WALKER.getCallerClass()!=NativeControl.class)throw new SecurityException("ACTUAL_KERNEL_FILE_NATIVE_CALLBACK_REQUIRED");FileKernelScope scope=FILE_KERNEL.get();if(scope==null||scope.closed||scope.thread!=Thread.currentThread()||scope.candidate.descriptor!=descriptor||scope.actualSelection!=actualSelection)return false;
        synchronized(STREAMS){try{if(actualSelection.length!=scope.candidate.selection.size()||!kernelFileQualified(scope.candidate))return false;Set<Module> distinct=Collections.newSetFromMap(new IdentityHashMap<>());for(Module source:actualSelection)if(source==null||!scope.candidate.selection.contains(source)||!distinct.add(source))return false;return true;}catch(ReflectiveOperationException|IOException|RuntimeException changed){return false;}}
    }
    private static void kernelFileBeforeSeal(FileKernelCandidate candidate){
        try{if(!kernelFileQualified(candidate)){candidate.record.kernelResult="HOST_KERNEL_FILE_SCOPE_UNQUALIFIED";return;}FileKernelScope previous=FILE_KERNEL.get(),scope=new FileKernelScope(candidate);FILE_KERNEL.set(scope);
            try{String result=NativeControl.hostKernelFile(candidate.descriptor,scope.actualSelection);candidate.record.kernelResult=result==null?"HOST_KERNEL_FILE_RESPONSE_UNAVAILABLE":result;}
            catch(RuntimeException|LinkageError unavailable){candidate.record.kernelResult="HOST_KERNEL_FILE_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage());}
            finally{scope.closed=true;if(previous==null)FILE_KERNEL.remove();else FILE_KERNEL.set(previous);}
        }catch(ReflectiveOperationException|IOException|RuntimeException changed){candidate.record.kernelResult="HOST_KERNEL_FILE_SCOPE_UNAVAILABLE:"+changed.getClass().getSimpleName()+":"+String.valueOf(changed.getMessage());}
    }
    static void sealIoDescriptor(FileDescriptor descriptor,Set<Module> modules){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_RETIREMENT_REQUIRED");
        synchronized(STREAMS){DescriptorRecord record=descriptorRecord(descriptor);
            if(record.active!=0||record.observing!=0)throw new IllegalStateException("IO_DESCRIPTOR_OPERATION_PENDING");
            if(record.unknownUse)throw new IllegalStateException("IO_DESCRIPTOR_SOURCE_UNOBSERVED");
            for(Module source:record.nativeSources)if(!modules.contains(source))throw new IllegalStateException("IO_DESCRIPTOR_SHARED");
            FileKernelCandidate candidate=IO_KERNEL_SELECTION.get();IO_KERNEL_SELECTION.remove();if(candidate!=null&&candidate.descriptor==descriptor&&candidate.record==record&&candidate.selection==modules)kernelFileBeforeSeal(candidate);record.sealed=true;}
    }
    public static boolean ioResource(Object value){return IoBridge.resource(value);}
    static String ioDescriptorRetirement(FileDescriptor descriptor,Predicate<Object> selected,Set<Module> modules){
        IO_KERNEL_SELECTION.remove();
        List<Object> views=new ArrayList<>();
        synchronized(STREAMS){
            DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));
            if(record!=null){
                if(record.active!=0||record.observing!=0)return releasePending("IO_DESCRIPTOR_OPERATION_PENDING");
                if(record.unknownUse)return "IO_DESCRIPTOR_SOURCE_UNOBSERVED";
                for(Module source:record.nativeSources)if(!modules.contains(source))return "SHARED";
                for(var reference:record.aliases){Object value=reference.get();if(value!=null)views.add(value);}
                for(var reference:record.channels){Object value=reference.get();if(value!=null)views.add(value);}
            }
            for(BufferStorage storage:BUFFER_ACTIONS.values())if(storage.descriptor==descriptor&&!storage.cleaned)return releasePending("IO_MAPPING_RELEASE_PENDING");
        }
        for(Object view:views)if(!selected.test(view))return "SHARED";
        synchronized(STREAMS){DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));Set<Module> selection=FILE_SELECTION.get();if(record!=null&&selection==modules&&selection!=null&&ControlBridge.coreResourceCallback(selected))try{List<Object> aliases=kernelFileAliases(record,descriptor);if(aliases!=null){FileKernelCandidate candidate=new FileKernelCandidate(descriptor,record,selection,selected,aliases);if(kernelFileQualified(candidate))IO_KERNEL_SELECTION.set(candidate);}}catch(ReflectiveOperationException|IOException|RuntimeException unavailable){record.kernelResult="HOST_KERNEL_FILE_SCOPE_UNAVAILABLE:"+unavailable.getClass().getSimpleName();}}
        return "";
    }
    private static void bufferSources(BufferStorage storage,Module[] sources){
        if(sources.length==0)storage.unknownUse=true;
        for(Module source:sources){if(source==null)storage.unknownUse=true;else storage.sources.add(source);}
    }
    public static void mappingExposed(Object action,String method){
        platform(BufferPlatform.UNMAPPER,method);
        if(!method.equals("address")&&!method.equals("fileDescriptor"))throw new SecurityException("ACTUAL_MAPPING_ACCESSOR_REQUIRED");
        if(FILE_OBSERVING.get()!=null||ControlBridge.recoveryWriter())return;
        if(!ControlBridge.taskEffectAllowed(action,"mapping-address"))throw new IllegalStateException("RONOVA_TERMINAL_MAPPING_ADDRESS");
        synchronized(STREAMS){BufferStorage storage=BUFFER_ACTIONS.get(new Key(action,false));if(storage==null)return;
            if(storage.sealed||storage.cleaned)throw new IllegalStateException("RONOVA_RETIRED_MAPPING_ADDRESS");
            storage.escaped=true;}
    }
    public static void mappingCreated(Object value,Object channel){
        platform(FILE_CHANNEL,"mapInternal");if(value==null||FILE_OBSERVING.get()!=null)return;
        if(!fileChannel(channel)||!(value instanceof Runnable action)||!BufferPlatform.UNMAPPER.isInstance(value))throw new SecurityException("ACTUAL_MAPPING_RESULT_REQUIRED");
        FILE_OBSERVING.set(Boolean.TRUE);
        try{
            Module[] sources=ControlBridge.resourceOperationSources(null);
            synchronized(STREAMS){
                Key key=new Key(action,true);BufferStorage storage=BUFFER_ACTIONS.get(key);
                if(storage==null){storage=new BufferStorage(action,true);storage.gap="";BUFFER_ACTIONS.put(key,storage);}
                bufferSources(storage,sources);
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("MAPPING_ORIGINAL_ASSOCIATION_UNAVAILABLE",failure);}
        finally{FILE_OBSERVING.remove();}
        resourceCreated(value);
    }
    // Native already tracks the actual DirectByteBuffer allocation even when
    // the constructor re-enters a file observation. Keep its cleanup binding
    // in that case; only recursive policy delivery is suppressed. An absent
    // captured I/O origin remains unknown, never a fabricated source receipt.
    private static Module[] bufferConstructionSources(boolean nested){
        if(!nested)return ControlBridge.resourceOperationSources(null);
        Module[] captured=IoBridge.currentSources();return captured==null?new Module[0]:captured;
    }
    public static Runnable bufferCleanup(Object value,Runnable action){
        platform(BufferPlatform.DIRECT,"<init>");if(action==null)return action;
        if(!(value instanceof Buffer buffer))throw new SecurityException("ACTUAL_BUFFER_REFERENT_REQUIRED");
        if(action.getClass()!=BufferPlatform.DEALLOCATOR&&!BufferPlatform.UNMAPPER.isInstance(action))return action;
        Boolean observing=FILE_OBSERVING.get();FILE_OBSERVING.set(Boolean.TRUE);BufferStorage storage;
        try{
            Module[] sources=bufferConstructionSources(observing!=null);
            synchronized(STREAMS){
                Key key=new Key(action,true);storage=BUFFER_ACTIONS.get(key);
                if(storage==null){storage=new BufferStorage(action,action.getClass()==BufferPlatform.DEALLOCATOR);BUFFER_ACTIONS.put(key,storage);}
                if(storage.sealed||storage.cleaned)throw new IllegalStateException("BUFFER_STORAGE_ALREADY_RETIRED");
                bufferSources(storage,sources);BufferView view=new BufferView(buffer,storage,null);
                BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_ORIGINAL_CLEANUP_UNAVAILABLE",failure);}
        finally{if(observing==null)FILE_OBSERVING.remove();else FILE_OBSERVING.set(observing);}
        resourceCreated(action);return new BufferCleanup(storage);
    }
    public static void installBufferWrap(Class<?> actualAgent){
        Class<?> caller=WRAP_AUTHORITY.getCallerClass();
        if(caller!=actualAgent||!ControlBridge.owns(caller)||!caller.getName().equals("dev.ronova.pro.agent.RecoveryAgent"))
            throw new SecurityException("BUFFER_WRAP_AGENT_REQUIRED");
        synchronized(BUFFER_WRAP_ROLES){
            if(bufferWrapAgent!=null&&bufferWrapAgent!=actualAgent)throw new SecurityException("BUFFER_WRAP_AGENT_CHANGED");
            bufferWrapAgent=actualAgent;CodeSourceBridge.resourceLayoutControls(BUFFER_WRAP_ROLES);
            CodeSourceBridge.resourceLayoutControls(BUFFER_WRAPPING);CodeSourceBridge.resourceLayoutControls(BUFFER_WRAP_WRITER);
        }
    }
    public static void expectBufferWrap(String name,ClassLoader loader,Module module){
        Class<?> caller=WRAP_AUTHORITY.walk(PLATFORM_BOUNDARY).getDeclaringClass(),agent=bufferWrapAgent;
        if(agent==null||caller.getNestHost()!=agent||!ControlBridge.owns(caller))throw new SecurityException("BUFFER_WRAP_AGENT_REQUIRED");
        if((!BUFFER_WRAP_NAME.equals(name)&&!LWJGL_JNI_NAME.equals(name))||module==null)throw new IllegalArgumentException("BUFFER_WRAP_ROLE_REQUIRED");
        synchronized(BUFFER_WRAP_ROLES){
            for(BufferWrapRole role:BUFFER_WRAP_ROLES)if(role.name.equals(name)&&role.bootstrap==(loader==null)&&role.loader.get()==loader){
                if(role.module.get()!=module)throw new IllegalStateException("BUFFER_WRAP_ROLE_MODULE_CHANGED");return;
            }
            BufferWrapRole role=new BufferWrapRole(name,loader,module);
            CodeSourceBridge.resourceLayoutControls(role);CodeSourceBridge.resourceLayoutControls(role.loader);CodeSourceBridge.resourceLayoutControls(role.module);
            BUFFER_WRAP_ROLES.add(role);
        }
    }
    private static BufferWrapRole bufferWrapBoundary(Class<?> declaring,String method,String descriptor){
        var frame=WRAP_AUTHORITY.walk(PLATFORM_BOUNDARY);Class<?> type=frame.getDeclaringClass();Module module=DefinitionBridge.module(type);
        boolean factory=method.equals("wrap")&&descriptor.equals(BUFFER_WRAP_DESCRIPTOR)||method.equals("slice")&&descriptor.equals(BUFFER_SLICE_DESCRIPTOR);
        if(!factory||type!=declaring||!type.getName().equals(BUFFER_WRAP_NAME)||!frame.getMethodName().equals(method)
                ||!frame.getDescriptor().equals(descriptor)||module!=type.getModule())throw new SecurityException("ACTUAL_BUFFER_FACTORY_REQUIRED");
        return bufferPlatformRole(type,module);
    }
    private static BufferWrapRole bufferPlatformRole(Class<?> type,Module module){
        synchronized(BUFFER_WRAP_ROLES){
            for(BufferWrapRole role:BUFFER_WRAP_ROLES)if(role.name.equals(type.getName())&&role.bootstrap==(type.getClassLoader()==null)&&role.loader.get()==type.getClassLoader()&&role.module.get()==module){
                if(role.actual==null){role.actual=new Key(type,false);CodeSourceBridge.resourceLayoutControls(role.actual);}
                if(role.actual.get()!=type)throw new SecurityException("BUFFER_WRAP_ACTUAL_CLASS_CHANGED");return role;
            }
        }
        throw new SecurityException("BUFFER_WRAP_ROLE_UNREGISTERED");
    }
    private static void lwjglPointerBoundary(Class<?> declaring){
        var frame=WRAP_AUTHORITY.walk(PLATFORM_BOUNDARY);Class<?> type=frame.getDeclaringClass();
        boolean method=frame.getMethodName().equals("<clinit>")&&frame.getDescriptor().equals("()V")
                ||frame.getMethodName().equals("setCapabilities")&&frame.getDescriptor().equals("(J)V")
                ||frame.getMethodName().equals("setFunctionMissingAddresses")&&frame.getDescriptor().equals("(I)V");
        if(type!=declaring||!LWJGL_JNI_NAME.equals(type.getName())||!method||DefinitionBridge.module(type)!=type.getModule())
            throw new SecurityException("ACTUAL_LWJGL_JNI_POINTER_CALL_REQUIRED");
        bufferPlatformRole(type,type.getModule());
        if(!ControlBridge.taskEffectAllowed(declaring,"lwjgl-jni-state")||ControlBridge.stoppedInvocation())
            throw new IllegalStateException("RONOVA_STOPPED_LWJGL_JNI_STATE");
    }
    private static final class LwjglPointers {
        static final Method GET=layoutMethod("getAddress",long.class),PUT=layoutMethod("putAddress",long.class,long.class);
    }
    public static long lwjglPointerRead(long address,Class<?> declaring){
        lwjglPointerBoundary(declaring);Long value=NativeControl.lwjglJniPointer(address,0,false);
        if(value!=null)return value;
        try{return ((Number)LwjglPointers.GET.invoke(UNSAFE,address)).longValue();}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("LWJGL_POINTER_READ_FAILED",failure);}
    }
    public static void lwjglPointerWrite(long address,long value,Class<?> declaring){
        lwjglPointerBoundary(declaring);
        if(NativeControl.lwjglJniPointer(address,value,true)!=null)return;
        try{LwjglPointers.PUT.invoke(UNSAFE,address,value);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("LWJGL_POINTER_WRITE_FAILED",failure);}
    }
    private static Span bufferWrapSpan(Field field,int bytes){
        for(Span span:INSTANCE_FIELDS.get(Buffer.class))if(span.field.equals(field)&&span.bytes==bytes)return span;
        throw new IllegalStateException("BUFFER_WRAP_FIELD_LAYOUT_UNAVAILABLE:"+field.getName());
    }
    /** The factory hook receives only the actual allocateInstance result, before its original checkcast. */
    public static Object bufferWrapEnter(Object fresh,Class<?> requested,long address,int capacity,Class<?> declaring){
        BufferWrapRole role=bufferWrapBoundary(declaring,"wrap",BUFFER_WRAP_DESCRIPTOR);
        if(!(fresh instanceof Buffer buffer)||buffer.getClass()!=requested||!BufferPlatform.TYPES.contains(requested)
                ||requested.getClassLoader()!=null||DefinitionBridge.module(requested)!=Object.class.getModule())
            throw new SecurityException("ACTUAL_ALLOCATED_PLATFORM_BUFFER_REQUIRED");
        if((Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)!=0L||(Integer)CodeSourceBridge.controlField(BufferPlatform.MARK,buffer)!=0
                ||(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,buffer)!=0||(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,buffer)!=0
                ||(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer)!=0)throw new SecurityException("FRESH_ALLOCATED_BUFFER_REQUIRED");
        synchronized(STREAMS){if(BUFFERS.containsKey(new Key(buffer,false)))throw new SecurityException("FRESH_UNREGISTERED_BUFFER_REQUIRED");}
        BufferWrapScope scope=new BufferWrapScope(buffer,requested,address,capacity,declaring,role);
        CodeSourceBridge.resourceLayoutControls(scope);BUFFER_WRAPPING.set(scope);return scope;
    }
    /** This second real factory also retains the original source attachment and byte order. */
    public static Object bufferSliceEnter(Object fresh,Class<?> requested,ByteBuffer source,long address,int capacity,Class<?> declaring){
        BufferWrapRole role=bufferWrapBoundary(declaring,"slice",BUFFER_SLICE_DESCRIPTOR);
        if(!(fresh instanceof ByteBuffer buffer)||buffer.getClass()!=requested||requested!=BufferPlatform.DIRECT||source==null
                ||source.getClass().getClassLoader()!=null||!BufferPlatform.TYPES.contains(source.getClass()))throw new SecurityException("ACTUAL_ALLOCATED_BYTE_BUFFER_SLICE_REQUIRED");
        if((Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)!=0L||(Integer)CodeSourceBridge.controlField(BufferPlatform.MARK,buffer)!=0
                ||(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,buffer)!=0||(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,buffer)!=0
                ||(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer)!=0)throw new SecurityException("FRESH_ALLOCATED_BUFFER_REQUIRED");
        Field parentField=BufferPlatform.PARENT.get(requested),sourceParentField=BufferPlatform.PARENT.get(source.getClass());
        if(parentField==null||sourceParentField==null||!parentField.equals(sourceParentField)||parentField.getDeclaringClass()!=BufferPlatform.DIRECT
                ||parentField.getType()!=Object.class||!parentField.getName().equals("att"))throw new SecurityException("ACTUAL_BYTE_BUFFER_PARENT_LAYOUT_REQUIRED");
        Span parentSpan=null;
        for(Span span:INSTANCE_FIELDS.get(parentField.getDeclaringClass()))if(span.field.equals(parentField)&&span.bytes==unsafeWidth("Object")){parentSpan=span;break;}
        if(parentSpan==null||CodeSourceBridge.controlField(parentField,buffer)!=null)throw new SecurityException("FRESH_BYTE_BUFFER_PARENT_REQUIRED");
        Object sourceParent=CodeSourceBridge.controlField(sourceParentField,source);
        long sourceAddress=(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,source);int sourceCapacity=(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,source);
        if(capacity<0||sourceCapacity<0||address<sourceAddress||capacity>sourceCapacity||address-sourceAddress>sourceCapacity-capacity)
            throw new SecurityException("BUFFER_SLICE_OUTSIDE_SOURCE_VIEW");
        BufferView sourceView;BufferStorage storage;
        synchronized(STREAMS){
            reap();if(BUFFERS.containsKey(new Key(buffer,false)))throw new SecurityException("FRESH_UNREGISTERED_BUFFER_REQUIRED");
            sourceView=BUFFERS.get(new Key(source,false));
            if(sourceView==null)throw new SecurityException("BUFFER_SLICE_SOURCE_VIEW_UNOBSERVED");
            if(sourceView.address!=sourceAddress||sourceView.bytes!=sourceCapacity||sourceView.parent.get()!=sourceParent)
                throw new SecurityException("BUFFER_SLICE_SOURCE_ASSOCIATION_CHANGED");
            storage=sourceView.storage;
            if(storage.sealed||storage.cleaned)throw new IllegalStateException("BUFFER_SLICE_SOURCE_RETIRED");
        }
        BufferWrapScope scope=new BufferWrapScope(buffer,requested,address,capacity,declaring,role,source,sourceParent,sourceAddress,sourceCapacity,sourceView,storage,parentSpan);
        CodeSourceBridge.resourceLayoutControls(scope);BUFFER_WRAPPING.set(scope);return scope;
    }
    /** Repeated Java/native guard queries do not consume or broaden this allowance. */
    static boolean bufferWrapMutationAllowed(Object receiver,long offset,Object value,int width){
        if(WALKER.getCallerClass()!=ControlBridge.class)return false;
        BufferWrapScope scope=BUFFER_WRAPPING.get();
        if(scope==null||scope.closed||scope.caller!=Thread.currentThread()||receiver!=scope.buffer||scope.buffer.getClass()!=scope.requested)return false;
        var frame=WALKER.walk(BUFFER_WRAP_WRITER);if(frame==null)return false;Class<?> type=frame.getDeclaringClass();
        if(type!=scope.declaring||scope.role.actual==null||scope.role.actual.get()!=type||scope.role.loader.get()!=type.getClassLoader()
                ||scope.role.bootstrap!=(type.getClassLoader()==null)||scope.role.module.get()!=DefinitionBridge.module(type)
                ||type.getModule()!=scope.role.module.get()||!frame.getMethodName().equals(scope.method)||!frame.getDescriptor().equals(scope.descriptor))return false;
        if(scope.parentSpan!=null&&offset==scope.parentSpan.offset&&width==scope.parentSpan.bytes)return value==scope.parent;
        if(offset==BufferWrapLayout.ADDRESS.offset&&width==BufferWrapLayout.ADDRESS.bytes)return value instanceof Long actual&&actual.longValue()==scope.address;
        if(!(value instanceof Integer actual)||width!=4)return false;
        return offset==BufferWrapLayout.MARK.offset&&width==BufferWrapLayout.MARK.bytes&&actual.intValue()==-1
                ||offset==BufferWrapLayout.LIMIT.offset&&width==BufferWrapLayout.LIMIT.bytes&&actual.intValue()==scope.capacity
                ||offset==BufferWrapLayout.CAPACITY.offset&&width==BufferWrapLayout.CAPACITY.bytes&&actual.intValue()==scope.capacity;
    }
    public static void bufferWrapExit(Object value,Throwable failure){
        if(value==null)return;
        if(!(value instanceof BufferWrapScope scope)||scope.caller!=Thread.currentThread())throw new SecurityException("ACTUAL_BUFFER_WRAP_SCOPE_REQUIRED");
        if(bufferWrapBoundary(scope.declaring,scope.method,scope.descriptor)!=scope.role)throw new SecurityException("ACTUAL_BUFFER_WRAP_ROLE_REQUIRED");
        if(scope.closed)return;
        if(BUFFER_WRAPPING.get()!=scope)throw new SecurityException("ACTUAL_BUFFER_WRAP_ORDER_REQUIRED");scope.closed=true;
        if(scope.previous==null)BUFFER_WRAPPING.remove();else BUFFER_WRAPPING.set(scope.previous);
        if(failure!=null)return;
        Boolean observing=FILE_OBSERVING.get();FILE_OBSERVING.set(Boolean.TRUE);
        try{
            Buffer buffer=scope.buffer;
            if((Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)!=scope.address||(Integer)CodeSourceBridge.controlField(BufferPlatform.MARK,buffer)!=-1
                    ||(Integer)CodeSourceBridge.controlField(BufferPlatform.POSITION,buffer)!=0||(Integer)CodeSourceBridge.controlField(BufferPlatform.LIMIT,buffer)!=scope.capacity
                    ||(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer)!=scope.capacity)throw new IllegalStateException("BUFFER_WRAP_INITIALIZATION_INCOMPLETE");
            Module[] sources=bufferConstructionSources(observing!=null);
            synchronized(STREAMS){
                reap();BufferStorage storage;Object parent;
                if(scope.source==null){
                    if(BUFFERS.containsKey(new Key(buffer,false)))throw new IllegalStateException("BUFFER_WRAP_VIEW_ALREADY_REGISTERED");
                    storage=new BufferStorage(null,false);storage.unknownUse=true;storage.escaped=true;
                    storage.gap="BUFFER_BORROWED_NATIVE_ORIGINAL_AND_CLEANUP_UNOBSERVED";parent=null;
                }else{
                    Field sourceParentField=BufferPlatform.PARENT.get(scope.source.getClass());
                    if(CodeSourceBridge.controlField(scope.parentSpan.field,buffer)!=scope.parent
                            ||CodeSourceBridge.controlField(sourceParentField,scope.source)!=scope.sourceParent
                            ||(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,scope.source)!=scope.sourceAddress
                            ||(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,scope.source)!=scope.sourceCapacity
                            ||BUFFERS.get(new Key(scope.source,false))!=scope.sourceView)throw new IllegalStateException("BUFFER_SLICE_PARENT_ASSOCIATION_CHANGED");
                    storage=scope.storage;parent=scope.parent;
                    if(storage.sealed||storage.cleaned)throw new IllegalStateException("BUFFER_SLICE_SOURCE_RETIRED");
                }
                BufferView view=BUFFERS.get(new Key(buffer,false));
                // The original order(source.order()) observes this view through the real JDK operation.
                if(view!=null){
                    if(scope.source==null||view.storage!=storage||view.parent.get()!=parent||view.address!=scope.address||view.bytes!=scope.capacity)
                        throw new IllegalStateException("BUFFER_SLICE_PROVISIONAL_VIEW_CHANGED");
                }else{
                    view=new BufferView(buffer,storage,parent);view.unknownUse=true;
                    BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;
                }
                view.constructed=true;bufferSources(storage,sources);observeBufferStorage(buffer,view);
            }
            // Observing this borrowed view creates neither storage ownership nor native release rights.
            ControlBridge.resourceExposedByOperation(buffer,sources);
        }catch(IllegalAccessException thrown){throw new IllegalStateException("BUFFER_WRAP_VIEW_CAPTURE_FAILED",thrown);}
        finally{if(observing==null)FILE_OBSERVING.remove();else FILE_OBSERVING.set(observing);}
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
    /** An observed view can borrow an existing allocation, never create one from its address. STREAMS is held. */
    private static void observeBufferStorage(Buffer buffer,BufferView view){
        view.memoryObserved=true;
    }
    private static boolean currentBufferView(Buffer buffer,BufferView view){
        try{
            Field parent=BufferPlatform.PARENT.get(buffer.getClass());
            return view.address==(Long)CodeSourceBridge.controlField(BufferPlatform.ADDRESS,buffer)
                    &&view.bytes==((long)(Integer)CodeSourceBridge.controlField(BufferPlatform.CAPACITY,buffer))*elementBytes(buffer)
                    &&view.parent.get()==(parent==null?null:CodeSourceBridge.controlField(parent,buffer));
        }catch(RuntimeException unavailable){return false;}
    }
    static boolean bufferStorageActive(Buffer buffer,Object action,long address,long bytes){
        if(WALKER.getCallerClass()!=NativeControl.class)return false;BufferObservation scope=BUFFER_OBSERVING_STORAGE.get();
        return scope!=null&&scope.buffer==buffer&&scope.action==action&&scope.view.storage.original==action
                &&scope.view.address==address&&scope.view.bytes==bytes&&BUFFERS.get(new Key(buffer,false))==scope.view&&currentBufferView(buffer,scope.view);
    }
    static boolean bufferStorageUseActive(Buffer buffer,long token,long generation,long address,long bytes,boolean retain){
        if(WALKER.getCallerClass()!=NativeControl.class)return false;BufferUse use=BUFFER_USING_STORAGE.get();
        return use!=null&&use.buffer==buffer&&use.memoryRetaining==retain&&use.memoryActive!=retain
                &&use.view.memoryToken==token&&use.view.memoryGeneration==generation&&use.view.address==address&&use.view.bytes==bytes
                &&(!retain||BUFFERS.get(new Key(buffer,false))==use.view&&currentBufferView(buffer,use.view));
    }
    static boolean bufferStorageExposureActive(Buffer buffer,long token,long generation,long address,long bytes,Module[] sources){
        if(WALKER.getCallerClass()!=NativeControl.class)return false;BufferUse use=BUFFER_USING_STORAGE.get();
        return use!=null&&use.buffer==buffer&&use.memoryActive&&use.memoryExposure==sources
                &&use.view.memoryToken==token&&use.view.memoryGeneration==generation&&use.view.address==address&&use.view.bytes==bytes;
    }
    private static void exposeBufferUse(BufferUse use,Module[] sources){
        if(!use.memoryActive)return;BufferUse previous=BUFFER_USING_STORAGE.get();use.memoryExposure=sources;BUFFER_USING_STORAGE.set(use);
        try{NativeControl.exposeBufferStorage(use.buffer,use.view.memoryToken,use.view.memoryGeneration,use.view.address,use.view.bytes,sources);}
        finally{use.memoryExposure=null;if(previous==null)BUFFER_USING_STORAGE.remove();else BUFFER_USING_STORAGE.set(previous);}
    }
    private static void retainBufferUse(BufferUse use){
        if(use.memoryActive)return;observeBufferStorage(use.buffer,use.view);if(use.view.memoryToken==0)return;
        BufferUse previous=BUFFER_USING_STORAGE.get();use.memoryRetaining=true;BUFFER_USING_STORAGE.set(use);
        try{
            if(!NativeControl.bufferStorageUse(use.buffer,use.view.memoryToken,use.view.memoryGeneration,use.view.address,use.view.bytes,true))
                throw new IllegalStateException("BUFFER_NATIVE_STORAGE_GENERATION_CHANGED");
            use.memoryActive=true;
        }finally{if(previous==null)BUFFER_USING_STORAGE.remove();else BUFFER_USING_STORAGE.set(previous);}
    }
    private static void releaseBufferUse(BufferUse use){
        if(!use.memoryActive)return;BufferUse previous=BUFFER_USING_STORAGE.get();use.memoryRetaining=false;BUFFER_USING_STORAGE.set(use);
        try{
            if(!NativeControl.bufferStorageUse(use.buffer,use.view.memoryToken,use.view.memoryGeneration,use.view.address,use.view.bytes,false))
                throw new IllegalStateException("BUFFER_NATIVE_STORAGE_LEASE_CHANGED");
            use.memoryActive=false;
        }finally{if(previous==null)BUFFER_USING_STORAGE.remove();else BUFFER_USING_STORAGE.set(previous);}
    }
    static boolean bufferRestoreActive(Object action,long token,long generation,Module[] modules,long cursor,int budget){
        if(WALKER.getCallerClass()!=NativeControl.class||!ControlBridge.recoveryWriter())return false;BufferRestore scope=BUFFER_RESTORING.get();
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
        Boolean observing=FILE_OBSERVING.get();FILE_OBSERVING.set(Boolean.TRUE);
        try{
            if(!buffer.isDirect())return;
            Field parentField=BufferPlatform.PARENT.get(buffer.getClass());Object parent=parentField==null?null:CodeSourceBridge.controlField(parentField,buffer);
            Module[] sources=bufferConstructionSources(observing!=null);
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
                view.constructed=true;bufferSources(storage,sources);observeBufferStorage(buffer,view);
                storage.gap=storage.created?"":"BUFFER_ORIGINAL_STORAGE_UNOBSERVED";
            }
        }catch(IllegalAccessException failure){throw new IllegalStateException("BUFFER_VIEW_CAPTURE_FAILED",failure);}
        finally{if(observing==null)FILE_OBSERVING.remove();else FILE_OBSERVING.set(observing);}
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
        if(operation.sources==null){
            Module[] captured=IoBridge.currentSources();if(captured==null)captured=currentFileSources();
            operation.sources=ControlBridge.resourceOperationSources(captured);
        }
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
            Object receipt=destination==null?ControlBridge.bufferReadBefore(source.holder,source.address,source.bytes,sources)
                    :ControlBridge.bufferDataBefore(destination,destinationOffset,bytes,source.holder,source.address,source.bytes,true,sources);
            try{return new BufferRead(receipt,ControlBridge.bufferReadSources(receipt));}
            catch(RuntimeException|Error failure){try{ControlBridge.bufferDataFinished(receipt,false);}catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
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
                        valueSources=ControlBridge.creationSources(text);break;
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
                Set<Module> combined=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(combined,sources);Collections.addAll(combined,valueSources);sources=combined.toArray(new Module[0]);
            }
            if(unknownSource)sources=Arrays.copyOf(sources,sources.length+1);
            Object receipt=ControlBridge.bufferDataBefore(destination.holder,destination.address,bytes,source,sourceOffset,sourceBytes,copy,sources);
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
            BufferWrapScope factory=BUFFER_WRAPPING.get();
            BufferStorage captured=factory!=null&&!factory.closed&&factory.source!=null&&factory.buffer==buffer?factory.storage:null;
            BufferStorage storage=captured!=null?captured:owner==null?new BufferStorage(null,false):owner.storage;
            view=new BufferView(buffer,storage,parent);view.unknownUse=true;
            if(owner==null&&captured==null){storage.unknownUse=true;storage.gap="BUFFER_ORIGINAL_STORAGE_UNOBSERVED";}
            BUFFERS.put(new Key(buffer,true),view);storage.views.add(new WeakReference<>(buffer));storage.viewsVersion++;observeBufferStorage(buffer,view);return view;
        }catch(IllegalAccessException failure){throw new IllegalStateException("IO_BUFFER_VIEW_UNAVAILABLE",failure);}
    }
    private static List<Buffer> ioBuffers(List<Buffer> values,Object value){
        if(value instanceof Buffer buffer){if(values==null)values=new ArrayList<>(1);addBuffer(values,buffer);}
        else if(value instanceof Buffer[] buffers)for(Buffer buffer:buffers)if(buffer!=null)values=ioBuffers(values,buffer);
        return values;
    }
    private static Object borrowIoBuffers(Object[] arguments,Module[] sources,boolean runtimeManaged){
        List<Buffer> values=null;for(Object argument:arguments)values=ioBuffers(values,argument);if(values==null)return null;
        List<BufferUse> uses=null;boolean delivered=false;Boolean previous=FILE_OBSERVING.get();FILE_OBSERVING.set(Boolean.TRUE);
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
                synchronized(STREAMS){for(BufferUse use:uses)retainBufferUse(use);}
                // These views belong to this one borrow. Collect its actual
                // current context once, then record every selected identity.
                // IoBridge captured these sources at this actual synchronous
                // entry or detached request. Rewalking here can attribute an
                // async borrow to its completion worker instead of its request.
                for(BufferUse use:uses){boolean known=ControlBridge.resourceExposedByOperation(use.buffer,sources);
                    synchronized(STREAMS){if(!known)use.view.unknownUse=true;bufferSources(use.view.storage,sources);}}
                IoBufferLease lease=new IoBufferLease(uses);
                synchronized(STREAMS){for(BufferUse use:uses){use.view.active++;use.view.storage.active++;}}
                delivered=true;
                return lease;
            }finally{synchronized(STREAMS){for(BufferUse use:uses){use.view.observing--;use.view.storage.observing--;if(!delivered)releaseBufferUse(use);}}}
        }finally{if(previous==null)FILE_OBSERVING.remove();else FILE_OBSERVING.set(previous);}
    }
    static void releaseIoBuffers(Object value){
        if(WALKER.getCallerClass()!=IoBridge.class)throw new SecurityException("ACTUAL_IO_BUFFER_RELEASE_REQUIRED");
        if(value==null)return;if(!(value instanceof IoBufferLease lease))throw new SecurityException("ACTUAL_IO_BUFFER_LEASE_REQUIRED");
        synchronized(STREAMS){
            if(lease.released)return;
            for(int index=0;index<lease.uses.size();index++)releaseIoUse(lease.uses.get(index));
            lease.released=true;lease.uses.clear();
        }
    }
    private static void releaseIoUse(BufferUse use){
        if(use.returned)return;
        releaseBufferUse(use);
        use.returned=true;use.view.active--;use.view.storage.active--;
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
                synchronized(STREAMS){bufferSources(storage,sources);}
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
        if(WALKER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_BUFFER_READ_CONTEXT_REQUIRED");
        Set<Module> sources=null;
        for(BufferOperation operation=BUFFER_OPERATIONS.get();operation!=null;operation=operation.previous)if(!operation.closed&&operation.observed!=null&&!operation.observed.isEmpty()){
            if(sources==null)sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.addAll(operation.observed);
        }
        return sources==null?new Module[0]:sources.toArray(new Module[0]);
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
    private static final class ClientPayloadLease {
        final BufferView view;final ByteBuffer buffer;final BufferUse use;final Thread thread=Thread.currentThread();boolean closed;
        ClientPayloadLease(BufferView view,ByteBuffer buffer){this.view=view;this.buffer=buffer;use=new BufferUse(buffer,view);}
    }
    private static void requireClientPayloadCore(){
        Class<?> caller=WALKER.walk(ControlBridge.CORE_CALLER);
        if(!ControlBridge.coreWriter()||!caller.getName().equals("dev.ronova.pro.ProRuntime"))throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_CORE_REQUIRED");
    }
    /** Keep the actual observed storage alive while the core erases one recorded vertex. */
    public static Object beginClientPayload(ByteBuffer buffer,int offset,int length){
        requireClientPayloadCore();
        if(buffer==null||!buffer.isDirect()||buffer.isReadOnly()||offset<0||length<=0||offset>buffer.limit()-length)
            throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_RANGE_REQUIRED");
        synchronized(STREAMS){
            BufferView view=BUFFERS.get(new Key(buffer,false));
            if(view==null||!view.constructed)throw new IllegalStateException("CLIENT_PAYLOAD_STORAGE_UNOBSERVED");
            BufferStorage storage=view.storage;
            if(view.sealed||storage.sealed||storage.cleaned||storage.cleaning||storage.failed||storage.restoring)
                throw new IllegalStateException("CLIENT_PAYLOAD_STORAGE_RETIRED");
            if(offset>view.bytes-length)throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_RANGE_REQUIRED");
            ClientPayloadLease lease=new ClientPayloadLease(view,buffer);
            retainBufferUse(lease.use);
            view.active++;storage.active++;return lease;
        }
    }
    public static void endClientPayload(Object token){
        requireClientPayloadCore();
        if(!(token instanceof ClientPayloadLease lease)||lease.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_CLIENT_PAYLOAD_LEASE_REQUIRED");
        synchronized(STREAMS){if(lease.closed)throw new SecurityException("CLIENT_PAYLOAD_LEASE_ALREADY_CLOSED");releaseBufferUse(lease.use);lease.closed=true;lease.view.active--;lease.view.storage.active--;STREAMS.notifyAll();}
    }

    public static Object bufferOperation(Object receiver,Object[] arguments,Class<?> declaring,String method){
        platform(declaring,method);if(declaring!=FILE_CHANNEL&&!BufferPlatform.TYPES.contains(declaring))throw new SecurityException("ACTUAL_BUFFER_OPERATION_REQUIRED");
        if(FILE_OBSERVING.get()!=null)return null;FILE_OBSERVING.set(Boolean.TRUE);
        // This private per-call list is never a business source. Avoid registering
        // two temporary ArrayLists in the global source directory for every read.
        List<BufferUse> uses=new LinkedList<>();boolean delivered=false;
        try{
            if(ControlBridge.recoveryWriter())return null;
            synchronized(STREAMS){
                observeBufferUse(uses,receiver);for(Object argument:arguments)observeBufferUse(uses,argument);
                for(BufferUse use:uses)retainBufferUse(use);
            }
            if(uses.isEmpty()&&!(receiver instanceof Buffer))return null;
            if(!ControlBridge.recoveryWriter()&&!(receiver instanceof Buffer buffer&&IoBridge.finishingBuffer(buffer))&&!ControlBridge.taskEffectAllowed(receiver,"buffer-io"))throw new IllegalStateException("RONOVA_TERMINAL_BUFFER_IO");
            boolean read=BufferPlatform.READS.contains(method),write=method.startsWith("put")||method.equals("_put")||method.equals("compact");
            BufferOperation operation=new BufferOperation(declaring,method,uses);
            Module[] sources=uses.isEmpty()?null:bufferOperationSources(operation);
            for(BufferUse use:uses){
                boolean known=ControlBridge.resourceExposedByOperation(use.buffer,sources);
                synchronized(STREAMS){
                    if(!known)use.view.unknownUse=true;bufferSources(use.view.storage,sources);
                    if((method.equals("address")||method.equals("fileDescriptor"))&&receiver==use.buffer&&!bufferBorrowed(use.view.storage)){
                        use.view.storage.escaped=true;if(method.equals("address"))exposeBufferUse(use,sources);
                    }
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
            delivered=true;
            return operation;
        }finally{
            synchronized(STREAMS){for(BufferUse use:uses){use.view.observing--;use.view.storage.observing--;if(!delivered)releaseBufferUse(use);}}
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
                operation.observe(operation.read.before);operation.observe(ControlBridge.bufferReadSources(operation.read.receipt));
            }
            if(operation.observed!=null&&!operation.observed.isEmpty()){
                CodeSourceBridge.executionBufferObserved(operation.execution,operation.observed.toArray(new Module[0]));
                if(operation.previous!=null&&!operation.previous.closed)operation.previous.observe(operation.observed);
            }
        }catch(RuntimeException|Error failure){failed=failure;}
        finally{
            try{if(operation.write!=null)ControlBridge.bufferDataFinished(operation.write.receipt,written);}
            catch(RuntimeException|Error failure){if(failed==null)failed=failure;else if(failed!=failure)failed.addSuppressed(failure);}
            try{if(operation.read!=null)ControlBridge.bufferDataFinished(operation.read.receipt,written);}
            catch(RuntimeException|Error failure){if(failed==null)failed=failure;else if(failed!=failure)failed.addSuppressed(failure);}
            synchronized(STREAMS){operation.closed=true;operation.sources=null;for(BufferUse use:operation.uses){
                try{releaseBufferUse(use);}catch(RuntimeException|Error failure){if(failed==null)failed=failure;else if(failed!=failure)failed.addSuppressed(failure);}
                use.view.active--;use.view.storage.active--;
            }}
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
        if(!ControlBridge.recoveryWriter()){
            if(!ControlBridge.taskEffectAllowed(null,"buffer-cleanup"))return false;
            for(Buffer buffer:views)if(!ControlBridge.resourceReleaseAllowed(buffer))return false;
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
        if(!ControlBridge.recoveryWriter())throw new SecurityException("BUFFER_RESOURCE_CORE_REQUIRED");
        synchronized(STREAMS){return BUFFERS.containsKey(new Key(value,false))||BUFFER_ACTIONS.containsKey(new Key(value,false));}
    }
    private static String preserveBuffer(BufferStorage storage,FileRecord requested,Set<Module> selection,long version){
        synchronized(STREAMS){
            if(storage.cleaned)return "";
            if(storage.sealed||storage.cleaning||storage.restoring||storage.active!=0||storage.observing!=0)return releasePending("BUFFER_OPERATIONS_EXIT_PENDING");
            if(version!=storage.viewsVersion)return releasePending("BUFFER_VIEW_OBSERVATION_PENDING");
            if(storage.failed||storage.escaped||storage.unknownUse)return "BUFFER_SHARED_STORAGE_LIFETIME_UNKNOWN_RETAINED";
            // A known live outside view retains its real allocation and original Cleaner.
            // Preserved means its lifetime was retained, never that its bytes were restored.
            requested.preserved=true;
            return "";
        }
    }
    /** Actual Java view/operation lifetimes govern the original Cleaner release. */
    private static String prepareBufferRelease(BufferStorage storage,Set<Module> selection){
        return storage.active!=0||storage.observing!=0?releasePending("BUFFER_OPERATIONS_EXIT_PENDING"):"";
    }
    private static String releaseBuffer(Object value,Predicate<Object> selected)throws ReflectiveOperationException {
        BufferStorage storage;FileRecord requested;List<Buffer> views=new ArrayList<>();Set<Module> selection=FILE_SELECTION.get();long version;boolean preserve;
        synchronized(STREAMS){
            requested=fileRecord(value);storage=requested instanceof BufferView view?view.storage:(BufferStorage)requested;
            if(storage.cleaned)return "";
            if(!storage.created||!storage.gap.isEmpty()&&!storage.gap.equals("BUFFER_MAPPING_DESCRIPTOR_RETAINED"))return storage.gap;
            if(storage.failed||storage.escaped||storage.unknownUse)return "BUFFER_STORAGE_LIFETIME_UNRESOLVED";
            if(storage.observing!=0)return releasePending("BUFFER_STORAGE_LIFETIME_UNRESOLVED");
            if(selection==null||storage.sources.isEmpty())return "BUFFER_STORAGE_SOURCES_UNOBSERVED";
            version=storage.viewsVersion;
            for(var reference:storage.views){Buffer buffer=reference.get();if(buffer!=null){
                BufferView view=BUFFERS.get(new Key(buffer,false));if(view==null||!view.constructed||view.unknownUse)return "BUFFER_VIEW_OBSERVATION_PENDING";if(view.observing!=0)return releasePending("BUFFER_VIEW_OBSERVATION_PENDING");views.add(buffer);
            }}
            // A real outside view or Util cache keeps the allocation alive;
            // it does not keep the stopped source's recorded byte revisions.
            preserve=storage.runtimeManaged||!selection.containsAll(storage.sources);
        }
        if(!preserve)for(Buffer buffer:views)if(!selected.test(buffer)){preserve=true;break;}
        synchronized(STREAMS){
            if(storage.observing!=0||version!=storage.viewsVersion)return releasePending("BUFFER_VIEW_OBSERVATION_PENDING");
            preserve|=storage.runtimeManaged||!selection.containsAll(storage.sources);
            if(storage.active!=0||storage.cleaning||storage.restoring)return releasePending("BUFFER_OPERATIONS_EXIT_PENDING");
            if(!preserve){String pending=prepareBufferRelease(storage,selection);if(!pending.isEmpty())return pending;storage.sealed=true;requested.preserved=false;}
        }
        if(preserve)return preserveBuffer(storage,requested,selection,version);
        releaseEffectStarted();
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
            return nest!=ResourceBridge.class&&nest!=NativeControl.class&&nest!=TaskBridge.class&&nest!=ControlBridge.class&&nest!=CodeSourceBridge.class&&nest!=BackingBridge.class&&nest!=ExecutionFlow.class&&actual!=BufferPlatform.UNSAFE;
        }).findFirst().map(frame->frame.getDeclaringClass()==BufferPlatform.DIRECT&&frame.getMethodName().equals("<init>")).orElse(false));
    }
    private static boolean nativeBufferAddress(Object value,Module[] sources){
        if(!NativeControl.bufferAddressBoundary(value,sources))throw new SecurityException("ACTUAL_BUFFER_NATIVE_ADDRESS_REQUIRED");
        if(!(value instanceof Buffer buffer))return true;
        if(!ControlBridge.taskEffectAllowed(value,"buffer-native-address"))return false;
        Module[] contributors=ControlBridge.resourceOperationSources(sources);
        synchronized(STREAMS){BufferView view=borrowedBufferView(buffer);if(view==null)return true;
            if(view.storage.sealed||view.storage.cleaned||!currentBufferView(buffer,view))return false;
            // Check the recorded lifetime even when this operation only exposes
            // the address and does not enter a Java get/put body.
            BufferUse use=new BufferUse(buffer,view);
            try{retainBufferUse(use);}catch(IllegalStateException changed){
                if("BUFFER_NATIVE_STORAGE_GENERATION_CHANGED".equals(changed.getMessage())||"BUFFER_NATIVE_STORAGE_OBSERVATION_CHANGED".equals(changed.getMessage()))return false;
                throw changed;
            }
            try{view.storage.escaped=true;bufferSources(view.storage,contributors);}finally{releaseBufferUse(use);}}
        return true;
    }
    private static void nativeBufferCreated(Object value,Module[] sources){
        if(!NativeControl.bufferAddressBoundary(value,sources))throw new SecurityException("ACTUAL_NATIVE_BUFFER_RESULT_REQUIRED");
        if(!(value instanceof ByteBuffer buffer)||buffer.getClass()!=BufferPlatform.DIRECT)throw new SecurityException("ACTUAL_NATIVE_DIRECT_BUFFER_REQUIRED");
        if(!nativeBufferAddress(value,sources))return;
        synchronized(STREAMS){BufferView view=BUFFERS.get(new Key(value,false));if(view!=null)view.constructed=true;}
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
            Field singleton=PlatformMetadata.declaredField(type,"theUnsafe");singleton.setAccessible(true);return singleton.get(null);
        }catch(ReflectiveOperationException failed){throw new ExceptionInInitializerError(failed);}
    }
    private static Method layoutMethod(String name,Class<?>... parameters){
        try{
            Method method=UNSAFE.getClass().getMethod(name,parameters);
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
            for(Class<?> type=actual;type!=null;type=type.getSuperclass())if(platformLayout(type))for(Field field:PlatformMetadata.declaredFields(type)){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=statik)continue;
                Class<?> kind=field.getType();int bytes=kind.isPrimitive()?kind==long.class||kind==double.class?8:
                        kind==int.class||kind==float.class?4:kind==short.class||kind==char.class?2:1:referenceBytes;
                result.add(new Span(field,((Number)offset.invoke(UNSAFE,field)).longValue(),bytes));
            }
            return List.copyOf(result);
        }catch(ReflectiveOperationException failed){throw new IllegalStateException("RESOURCE_FIELD_LAYOUT_UNOBSERVED",failed);}
    }
    static boolean fieldMutationAllowed(Object receiver,Class<?> declaring){
        if(NativeControl.libraryMetadataType(declaring))return ControlBridge.recoveryWriter()||NativeControl.libraryImplementationCaller();
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
    /** Class-owned cache lookup must not resolve the represented class's field types. */
    static Field mirrorInstanceField(Object receiver,long offset,long length){
        return receiver instanceof Class<?>?exactField(receiver,Class.class,false,offset,length):null;
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
        try{
            for(Class<?> type=actual;type!=null;type=statik?null:type.getSuperclass())
                for(Span span:(statik?STATIC_FIELDS:INSTANCE_FIELDS).get(type))
                    if(span.offset==offset&&span.bytes==length&&fieldHolder(span.field,receiver)){
                        if(exact!=null)throw new IllegalStateException("OVERLAPPING_FIELD_LAYOUT");exact=span.field;
                    }
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_FIELD_LAYOUT_UNAVAILABLE",unavailable);}
        return exact;
    }
    /** Only actual represented static storage is writable through a Class mirror. */
    static boolean mirrorStaticRange(Class<?> mirror,long offset,long length){
        if(offset<0||length<=0||length>Long.MAX_VALUE-offset)return false;
        long end=offset+length,cursor=offset;
        // A mirror instance slot cannot also be represented static storage.
        // Reject external cache/instance writes without loading business fields.
        for(Span span:INSTANCE_FIELDS.get(Class.class))
            if(offset<span.offset+span.bytes&&span.offset<end)return false;
        Span[] spans=STATIC_FIELDS.get(mirror);
        try {
            // Cover the complete write. Header bytes and alignment holes do not
            // become static storage merely because a later field exists.
            while(cursor<end){
                long next=cursor;
                for(Span span:spans)if(span.offset<=cursor&&cursor<span.offset+span.bytes
                        &&fieldHolder(span.field,mirror))next=Math.max(next,Math.min(end,span.offset+span.bytes));
                if(next==cursor)return false;
                cursor=next;
            }
            return true;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("MIRROR_STATIC_LAYOUT_UNAVAILABLE",failure);}
    }
    private static Span[] declaredSpans(Class<?> actual,boolean statik){
        try{
            List<Span> result=new ArrayList<>();
            Method offset=statik?STATIC_OFFSET:INSTANCE_OFFSET;
            for(Field field:PlatformMetadata.declaredFields(actual)){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=statik)continue;
                Class<?> kind=field.getType();int bytes=kind.isPrimitive()?kind==long.class||kind==double.class?8:
                        kind==int.class||kind==float.class?4:kind==short.class||kind==char.class?2:1:unsafeWidth("Reference");
                Span span=new Span(field,((Number)offset.invoke(UNSAFE,field)).longValue(),bytes);
                CodeSourceBridge.resourceLayoutControls(field);CodeSourceBridge.resourceLayoutControls(span);result.add(span);
            }
            Span[] layout=result.toArray(Span[]::new);CodeSourceBridge.resourceLayoutControls(layout);return layout;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_FIELD_LAYOUT_UNAVAILABLE",unavailable);}
    }
    /** Arbitrary JNI/reflection/Unsafe setters may not forge the platform resource associations. */
    static long[] fieldSpan(java.lang.reflect.Field field,Object receiver){
        try{
            boolean statik=java.lang.reflect.Modifier.isStatic(field.getModifiers());
            if(!fieldHolder(field,receiver))return null;
            for(Span span:(statik?STATIC_FIELDS:INSTANCE_FIELDS).get(field.getDeclaringClass()))
                if(span.field.equals(field))return new long[]{span.offset,span.bytes};
            throw new IllegalStateException("EXTERNAL_FIELD_LAYOUT_UNAVAILABLE:"+field.getName());
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_FIELD_LAYOUT_UNAVAILABLE",unavailable);}
    }
    static boolean mutationAllowed(Object value){
        if(NativeControl.libraryMetadataValue(value))return ControlBridge.recoveryWriter()||NativeControl.libraryImplementationCaller();
        if(!critical(value)||ControlBridge.recoveryWriter())return true;
        return WALKER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();String name=type.getName();
                if(DefinitionBridge.module(type)!=type.getModule())return false;
                if(type.getNestHost()==ResourceBridge.class||type.getNestHost()==IoBridge.class||type.getNestHost()==TaskBridge.class||type.getNestHost()==ControlBridge.class
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
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(action))throw new SecurityException("RESOURCE_RECOVERY_CORE_REQUIRED");
        FileRecord record;
        synchronized(STREAMS){record=fileRecord(stream);}
        if(record==null)throw new IOException("FILE_RESOURCE_CONSTRUCTOR_ASSOCIATION_UNOBSERVED");
        if(!record.observation.tryLock())return false;
        try{
            if(record.retired)return true;
            if(record.release==null){
                try{
                    record.releasePhase=new ReleasePhase();FutureTask<String> future=releaseTask(action,record.releasePhase);record.release=future;CLOSER.execute(future);
                }
                catch(RejectedExecutionException full){record.release=null;throw new IOException("RESOURCE_DISPOSITION_CAPACITY",full);}
                return false;
            }
            if(!record.release.isDone())return false;
            try{
                String remaining=record.release.get();
                if(!remaining.isEmpty()){
                    if(record.releasePhase.pending&&!record.releasePhase.effects)record.release=null;
                    throw new IOException(remaining);
                }
                // A known preservation result may be reconsidered by a later
                // group selection; a real close remains recorded as retired.
                record.release=null;
                record.retired=!record.preserved;return true;
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("RESOURCE_RELEASE_OBSERVATION_INTERRUPTED",interrupted);}
            catch(ExecutionException failed){throw new IOException("RESOURCE_RELEASE_FAILED:"+failed.getCause().getClass().getSimpleName(),failed.getCause());}
        }finally{record.observation.unlock();}
    }
    public static String releaseProcess(Process process,Predicate<Object> selected,Module[] modules){
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        Set<Module> previous=selectFiles(modules);
        try{return releaseProcess(process,selected);}finally{restoreFileSelection(previous);}
    }
    public static String release(Object stream,Predicate<Object> selected,Module[] modules){
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        Set<Module> previous=selectFiles(modules);
        try{return release(stream,selected);}finally{restoreFileSelection(previous);}
    }
    private static Set<Module> selectFiles(Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Module module:modules){if(module==null||!ControlBridge.modStopped(module))throw new IllegalStateException("STOPPED_FILE_SOURCE_GROUP_REQUIRED");selected.add(module);}
        if(selected.isEmpty())throw new IllegalStateException("FILE_SOURCE_GROUP_REQUIRED");
        Set<Module> previous=FILE_SELECTION.get();FILE_SELECTION.set(selected);return previous;
    }
    private static void restoreFileSelection(Set<Module> previous){FileKernelCandidate candidate=IO_KERNEL_SELECTION.get();if(candidate!=null&&candidate.selection==FILE_SELECTION.get())IO_KERNEL_SELECTION.remove();if(previous==null)FILE_SELECTION.remove();else FILE_SELECTION.set(previous);}
    public static String release(Object stream,Predicate<Object> selected){
        if(!ControlBridge.recoveryWriter()||!ControlBridge.coreResourceCallback(selected))throw new SecurityException("RESOURCE_RELEASE_CORE_REQUIRED");
        try{
            boolean buffer;synchronized(STREAMS){buffer=BUFFERS.containsKey(new Key(stream,false))||BUFFER_ACTIONS.containsKey(new Key(stream,false));}
            if(stream instanceof FileDescriptor descriptor){
                MappingDuplicateNative pending;synchronized(STREAMS){pending=MAPPING_DUPLICATE_PENDING.get(descriptor);}
                if(pending!=null)return releaseMappingDuplicate(descriptor,pending,selected);
            }
            if(characterWrapper(stream))return releaseWrapper(stream,selected);
            if(buffer)return releaseBuffer(stream,selected);
            if(IoBridge.resource(stream))return IoBridge.release(stream,selected,FILE_SELECTION.get());
            return fileChannel(stream)?releaseChannel(stream,selected):releaseStream(stream,selected);
        }catch(ReflectiveOperationException|IOException|RuntimeException failure){releaseUncertain();return "RESOURCE_RELEASE_FAILED:"+failure.getClass().getSimpleName()+":"+failure.getMessage();}
    }
    /** An unpublished copy never borrows the parent's close or allocation authority. */
    private static String releaseMappingDuplicate(FileDescriptor descriptor,MappingDuplicateNative call,Predicate<Object> selected)throws ReflectiveOperationException,IOException {
        MappingDuplicate scope=call.scope;
        if(scope.target!=descriptor)return "MAPPING_DUPLICATE_FRAME_EXIT_PENDING";
        if(!scope.closed)return releasePending("MAPPING_DUPLICATE_FRAME_EXIT_PENDING");
        if(!call.nativeSucceeded||call.returned==-1)return "MAPPING_DUPLICATE_NATIVE_RESULT_UNOBSERVED";
        Set<Module> selection=FILE_SELECTION.get();
        if(selection==null||call.sources.length==0)return "MAPPING_DUPLICATE_SOURCE_GROUP_UNOBSERVED";
        for(Module source:call.sources)if(source==null||!selection.contains(source))return "MAPPING_DUPLICATE_SOURCE_GROUP_UNOBSERVED";
        if(!selected.test(descriptor))return "MAPPING_DUPLICATE_SHARED_TARGET_PENDING";
        synchronized(descriptor){
            // If setHandle was rejected there is no safe original object close.
            // Keep the actual native receipt instead of manufacturing an FD from its number.
            if(FD_HANDLE.getLong(descriptor)!=call.returned||FD_NUMBER.getInt(descriptor)!=-1||FD_CLOSED.getBoolean(descriptor))
                return "MAPPING_DUPLICATE_HANDLE_INSTALL_OR_TARGET_CHANGED";
            Object cleanup=FD_CLEANUP.get(descriptor);
            if(cleanup!=null&&(cleanup.getClass()!=FILE_CLEANABLE||CLEANABLE_REFERENT.get(cleanup)!=descriptor
                    ||CLEANABLE_HANDLE.getLong(cleanup)!=call.returned||CLEANABLE_NUMBER.getInt(cleanup)!=-1))
                return "MAPPING_DUPLICATE_CLEANUP_ASSOCIATION_CHANGED";
            descriptorView(descriptor);
            if(!descriptorReleaseAllowed(descriptor))return "MAPPING_DUPLICATE_SHARED_TARGET_PENDING";
            String pending=ioDescriptorRetirement(descriptor,selected,selection);if(!pending.isEmpty())return pending;
            sealIoDescriptor(descriptor,selection);
            Method close=FileDescriptor.class.getDeclaredMethod("close");close.setAccessible(true);releaseEffectStarted();close.invoke(descriptor);
            if(FD_HANDLE.getLong(descriptor)!=-1)return "MAPPING_DUPLICATE_ORIGINAL_CLOSE_PENDING";
            synchronized(STREAMS){
                DescriptorRecord record=ATTACHMENTS.get(new Key(descriptor,false));if(record!=null)record.retired=true;
                if(MAPPING_DUPLICATE_PENDING.get(descriptor)==call)MAPPING_DUPLICATE_PENDING.remove(descriptor);
            }
            return "";
        }
    }
    /** Existing retirement/report callers can see copies which never reached a Java return. */
    public static Object[][] mappingDuplicatePending(Module[] modules){
        if(!ControlBridge.coreWriter())throw new SecurityException("MAPPING_DUPLICATE_RECOVERY_CALLER_REQUIRED");
        Objects.requireNonNull(modules);List<Object[]> rows=new ArrayList<>();
        synchronized(STREAMS){
            for(var entry:MAPPING_DUPLICATE_PENDING.entrySet()){
                MappingDuplicateNative call=entry.getValue();boolean included=call.sources.length==0;
                for(Module source:call.sources){
                    if(source==null)included=true;
                    else for(Module module:modules)if(source==module){included=true;break;}
                }
                if(included)rows.add(new Object[]{entry.getKey(),call.sources.clone(),mappingDuplicatePendingReason(call)});
            }
        }
        return rows.toArray(Object[][]::new);
    }
    private static String mappingDuplicatePendingReason(MappingDuplicateNative call){
        if(!call.scope.closed)return "MAPPING_DUPLICATE_FRAME_EXIT_PENDING";
        if(!call.nativeSucceeded)return "MAPPING_DUPLICATE_NATIVE_RESULT_UNOBSERVED";
        try{
            FileDescriptor target=call.scope.target;
            if(FD_HANDLE.getLong(target)!=call.returned||FD_NUMBER.getInt(target)!=-1||FD_CLOSED.getBoolean(target))
                return "MAPPING_DUPLICATE_HANDLE_INSTALL_OR_TARGET_CHANGED";
            Object cleanup=FD_CLEANUP.get(target);
            if(cleanup!=null&&(cleanup.getClass()!=FILE_CLEANABLE||CLEANABLE_REFERENT.get(cleanup)!=target
                    ||CLEANABLE_HANDLE.getLong(cleanup)!=call.returned||CLEANABLE_NUMBER.getInt(cleanup)!=-1))
                return "MAPPING_DUPLICATE_CLEANUP_ASSOCIATION_CHANGED";
            return "MAPPING_DUPLICATE_ORIGINAL_CLOSE_PENDING";
        }catch(IllegalAccessException unavailable){return "MAPPING_DUPLICATE_PUBLICATION_UNAVAILABLE";}
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
                if(record.observing!=0)return releasePending("STREAM_SOURCE_OBSERVATION_PENDING");
                if(!selected.test(stream))return preserve(record);
                if(channel!=null){
                    ChannelRecord attached=CHANNELS.get(new Key(channel,false));
                    if(attached==null)return "STREAM_CHANNEL_CONSTRUCTION_UNOBSERVED";
                    if(attached.unknownUse||attached.observing!=0)return "STREAM_CHANNEL_USE_SOURCE_UNOBSERVED";
                    if(!selected.test(channel))return preserve(record);
                    attached.sealed=true;
                }
                record.sealed=true;
                if(record.active!=0)return releasePending("STREAM_IO_EXIT_PENDING");
            }
            // Closing the channel may call back into its parent. Close the view before that callback,
            // then remove only this parent from the original descriptor's closeAll list.
            if(!layout.closed.getBoolean(stream)){releaseEffectStarted();layout.closed.setBoolean(stream,true);}
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
                if(record.observing!=0)return releasePending("FILE_CHANNEL_SOURCE_OBSERVATION_PENDING");
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
        boolean close;
        synchronized(STREAMS){
            // Admission and the retirement seal meet at the same resource records.
            DescriptorRecord descriptorRecord=ATTACHMENTS.get(new Key(descriptor,false));
            if(descriptorRecord==null||descriptorRecord.unknownUse)return "FILE_DESCRIPTOR_SOURCE_OBSERVATION_PENDING";
            if(descriptorRecord.observing!=0)return releasePending("FILE_DESCRIPTOR_SOURCE_OBSERVATION_PENDING");
            if(!descriptorRecord.nativeSources.isEmpty()&&(FILE_SELECTION.get()==null||!FILE_SELECTION.get().containsAll(descriptorRecord.nativeSources)))return "FILE_DESCRIPTOR_SHARED_DURING_RETIREMENT";
            for(Object value:views(view)){
                FileRecord record=fileRecord(value);
                if(record==null||record.unknownUse)return "FILE_RESOURCE_SOURCE_OBSERVATION_PENDING";
                if(record.observing!=0)return releasePending("FILE_RESOURCE_SOURCE_OBSERVATION_PENDING");
            }
            // Re-evaluate after any source publication which preceded the seal.
            for(Object value:views(view))if(!selected.test(value))return "FILE_RESOURCE_SHARED_DURING_RETIREMENT";
            Set<Module> selection=FILE_SELECTION.get();if(selection!=null&&ControlBridge.coreResourceCallback(selected))try{List<Object> aliases=kernelFileAliases(descriptorRecord,descriptor);if(aliases!=null)kernelFileBeforeSeal(new FileKernelCandidate(descriptor,descriptorRecord,selection,selected,aliases));}
            catch(IllegalAccessException|RuntimeException unavailable){descriptorRecord.kernelResult="HOST_KERNEL_FILE_SCOPE_UNAVAILABLE:"+unavailable.getClass().getSimpleName();}
            ATTACHMENTS.get(new Key(descriptor,false)).sealed=true;
            for(Object value:views(view)){FileRecord record=fileRecord(value);record.sealed=true;record.preserved=false;}
            if(descriptorRecord.active!=0)return releasePending("FILE_DESCRIPTOR_IO_EXIT_PENDING");
            for(Object value:views(view))if(fileRecord(value).active!=0)return releasePending("FILE_RESOURCE_IO_EXIT_PENDING");
            close=!descriptorRecord.releaseStarted;descriptorRecord.releaseStarted=true;
        }
        if(close){releaseEffectStarted();
        IOException failure=null;String remaining="";
        for(Object stream:view.streams)try{((Closeable)stream).close();}catch(IOException thrown){releaseUncertain();if(failure==null)failure=thrown;else failure.addSuppressed(thrown);}
        for(Object channel:view.channels)try{
            ChannelRecord record;synchronized(STREAMS){record=CHANNELS.get(new Key(channel,false));}
            String pending=closeChannel(channel,record);if(!pending.isEmpty()&&remaining.isEmpty())remaining=pending;
        }catch(IOException thrown){releaseUncertain();if(failure==null)failure=thrown;else failure.addSuppressed(thrown);}
        if(descriptor.valid()){
            var originalClose=FileDescriptor.class.getDeclaredMethod("close");originalClose.setAccessible(true);
            var all=FileDescriptor.class.getDeclaredMethod("closeAll",Closeable.class);all.setAccessible(true);
            Closeable releaser=()->{try{originalClose.invoke(descriptor);}catch(ReflectiveOperationException thrown){throw new IOException("DESCRIPTOR_NATIVE_CLOSE_FAILED",thrown);}};
            all.invoke(descriptor,releaser);
        }
        if(failure!=null)throw failure;
        releaseStepCompleted();
        if(!remaining.isEmpty())return releasePending(remaining);
        }
        synchronized(STREAMS){
            if(ATTACHMENTS.get(new Key(descriptor,false)).active!=0)return releasePending("FILE_DESCRIPTOR_IO_EXIT_PENDING");
            for(Object value:views(view))if(fileRecord(value).active!=0)return releasePending("FILE_RESOURCE_IO_EXIT_PENDING");
        }
        for(Object stream:view.streams){StreamRecord record;synchronized(STREAMS){record=STREAMS.get(new Key(stream,false));}if(!record.layout.closed.getBoolean(stream))return releasePending("STREAM_CLOSE_PENDING");}
        return !descriptor.valid()?"":releasePending("FILE_DESCRIPTOR_NATIVE_RELEASE_PENDING");
    }
    private static String closeChannel(Object channel,ChannelRecord record)throws IOException {
        boolean close;synchronized(STREAMS){close=!record.releaseStarted;record.releaseStarted=true;}
        if(close){releaseEffectStarted();((Channel)channel).close();releaseStepCompleted();}
        if(((Channel)channel).isOpen())return releasePending("FILE_CHANNEL_CLOSE_PENDING");
        if(!record.closeCompleted)return releasePending("FILE_CHANNEL_CLOSE_INCOMPLETE");
        synchronized(STREAMS){return record.active==0?"":releasePending("FILE_CHANNEL_IO_EXIT_PENDING");}
    }
    private static boolean identityContains(List<Object> values,Object target){for(Object value:values)if(value==target)return true;return false;}
}
