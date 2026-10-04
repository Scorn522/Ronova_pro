package dev.ronova.pro.bootstrap;

import java.lang.module.ModuleReference;
import java.lang.ref.*;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.*;

/** Carries actual module-reader provenance through class bytes and ASM parsing. */
public final class CodeSourceBridge {
    private static final Module[] NO_FRAME_SOURCES=new Module[0];
    private static final StackWalker WALKER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final StackWalker AUTHORITY=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private record BufferFrames(StackWalker.StackFrame callee,StackWalker.StackFrame caller,int depth){}
    private static final class BufferCallFrames implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,BufferFrames>{
        public BufferFrames apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            StackWalker.StackFrame callee=null,caller=null;int count=0;
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();
                if(count==0){Class<?> host=frame.getDeclaringClass().getNestHost();if(host==CodeSourceBridge.class||host==ResourceBridge.class)continue;callee=frame;}
                else if(count==1)caller=frame;
                count++;
            }
            // Depth still includes every actual remaining frame. Only the
            // first two frames are consumed by the platform-call comparison.
            return count<2?null:new BufferFrames(callee,caller,count-1);
        }
    }
    private static final BufferCallFrames BUFFER_CALL_FRAMES=new BufferCallFrames();
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final ReferenceQueue<Object> DEAD_CONTROLS=new ReferenceQueue<>();
    private static final ControlRegistry CONTROLS=new ControlRegistry();
    private static final class LedgerMap<K,V> extends HashMap<K,V>{
        LedgerMap(){
            if(AUTHORITY.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_LEDGER_CONSTRUCTOR_REQUIRED");
            ledgerMap(this);
        }
        LedgerMap(Map<? extends K,? extends V> values){
            if(AUTHORITY.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_LEDGER_CONSTRUCTOR_REQUIRED");
            ledgerMap(this);putAll(values);
        }
    }
    private static final Map<Key,WeakReference<Module>> MODULES=new LedgerMap<>();
    private static final Map<Key,Image> IMAGES=new LedgerMap<>();
    private static final Map<Key,Module[]> BYTE_CONTRIBUTORS=new LedgerMap<>();
    private static final Map<Key,Reader> READERS=new LedgerMap<>();
    private static final Map<Key,Image> TREES=new LedgerMap<>();
    private static final Map<Key,byte[]> TREE_IMAGES=new LedgerMap<>();
    private static final Map<Key,Image> MIXINS=new LedgerMap<>();
    private static final Map<Key,WeakReference<Object>> MIXIN_TREES=new LedgerMap<>();
    private static final Map<Key,Boolean> MIXIN_OPTIONS=new LedgerMap<>();
    private static final Map<Key,TrackerReceipt> MIXIN_RESTRICTIONS=new LedgerMap<>();
    private static final Map<Key,TrackerReceipt> TRACKER_RESTRICTIONS=new LedgerMap<>();
    private static final Map<Key,Image> METADATA=new LedgerMap<>();
    private static final Map<Key,WeakReference<Object>> METADATA_TREES=new LedgerMap<>();
    private static final Map<Key,Map<String,Image>> METADATA_ROWS=new LedgerMap<>();
    private static final Map<Key,Image> MEMBER_IMAGES=new LedgerMap<>();
    private static final Map<Key,Module[]> CONTEXT_READS=new LedgerMap<>();
    private static final Map<Key,Module[]> STATE_READS=new LedgerMap<>();
    private static final Map<Key,Map<Key,Image>> SELECTIONS=new LedgerMap<>();
    private static final Map<Key,Module[]> RESOURCE_SOURCES=new LedgerMap<>();
    private static final Map<Key,Module[]> CONFIGURATIONS=new LedgerMap<>();
    private static final Map<Key,Image> CONTEXTS=new LedgerMap<>();
    private static final Map<Key,Holder> HOLDERS=new LedgerMap<>();
    private static final Map<Key,Image> WRITERS=new LedgerMap<>();
    private static final Map<Thread,Integer> COPYING=new IdentityHashMap<>();
    private static final ThreadLocal<Set<Object>> PREPARING_CONFIGS=ThreadLocal.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ThreadLocal<Registration> REGISTRATIONS=new ThreadLocal<>();
    private static final ThreadLocal<MetadataUse> METADATA_USE=new ThreadLocal<>();
    private static final List<Role> ROLES=new ArrayList<>();
    private static final Map<String,List<CodeMethods>> CODE_METHODS=new LedgerMap<>();
    private static final List<CodeFields> CODE_FIELDS=new ArrayList<>();
    private static final List<CodeValues> CODE_VALUES=new ArrayList<>();
    private static final Map<Key,Set<FieldHold>> FIELD_HOLDERS=new LedgerMap<>();
    private static final List<WeakReference<Class<?>>> FIELD_CLASSES=new ArrayList<>();
    private static volatile Class<?> agent;
    private static volatile Method producer,encoder,begin,end,copied,encoded,observed,dependency,payload,selection,metadataImages,memberImage,prepared;
    private record Role(String name,WeakReference<ClassLoader> loader,boolean bootstrap,WeakReference<Module> module){}
    private record CodeMethods(String name,WeakReference<ClassLoader> loader,boolean bootstrap,Set<String> methods,WeakReference<Class<?>> actual){}
    private record CodeField(String name,String descriptor,Module[] owners){}
    private record CodeFields(String name,WeakReference<ClassLoader> loader,boolean bootstrap,Map<String,CodeField> fields,WeakReference<Class<?>> actual){}
    private record CodeValues(String name,WeakReference<ClassLoader> loader,boolean bootstrap,List<CodeValue> fields,WeakReference<Class<?>> actual){}
    private static final class CodeValue {
        final String name,descriptor;final Object expected,baseline;final Module[] sources;volatile boolean completed;
        CodeValue(String name,String descriptor,Object expected,Object baseline,Module[] sources){this.name=name;this.descriptor=descriptor;this.expected=expected;this.baseline=baseline;this.sources=sources;}
    }
    private record FieldHold(Class<?> declaring,String name,String descriptor){}
    private record Image(Module module,byte[] digest,Module[] sources){}
    private record Replayed(Object tree,boolean metadataChanged,boolean scheduleChanged){}
    private record CodeCapture(Object token,Image original,Module[] sources,Ref<Object> context){}
    private record MetadataUse(Object context,Object mixin,Object state,MetadataUse previous){}
    private record MemberChange(Object member,Object[] values,Image original,Module[] sources){}
    private record Reader(Image image,WeakReference<byte[]> bytes,Class<?> declaring){}
    private static final class TrackerReceipt {
        final Ref<Object> tracker,set,node,key;final Set<Key> mixins=new HashSet<>();boolean outside;
        TrackerReceipt(Object tracker,Object set,Object node,Object key,boolean outside){
            this.tracker=new Ref<>(tracker);this.set=new Ref<>(set);this.node=new Ref<>(node);this.key=new Ref<>(key);this.outside=outside;
            protect(this,this.tracker,this.set,this.node,this.key,mixins);
        }
    }
    private static final class Registration {
        final Object mixin,tracker;final String name;final Registration previous;TrackerReceipt receipt;
        Registration(Object mixin,Object tracker,String name,Registration previous){this.mixin=mixin;this.tracker=tracker;this.name=name;this.previous=previous;protect(this);}
    }
    private record LoadingChange(Runnable undo){void rollback(){undo.run();}}
    private record Holder(WeakReference<Object> implementation,WeakReference<Object> service,Module[] sources){}
    private static final class Ref<T> extends WeakReference<T>{
        Ref(T value){super(value);}
        private static boolean writer(Class<?> caller){return caller.getNestHost()==CodeSourceBridge.class&&DefinitionBridge.module(caller)==CodeSourceBridge.class.getModule()||ControlBridge.owns(caller);}
        public void clear(){if(writer(AUTHORITY.getCallerClass()))super.clear();}
        public boolean enqueue(){return writer(AUTHORITY.getCallerClass())&&super.enqueue();}
    }
    private static final class Key extends WeakReference<Object>{
        final int hash;
        Key(Object value,boolean stored){this(value,stored?DEAD:null);}
        Key(Object value,ReferenceQueue<Object> queue){super(value,queue);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
        private static boolean writer(Class<?> caller){return caller.getNestHost()==CodeSourceBridge.class&&DefinitionBridge.module(caller)==CodeSourceBridge.class.getModule()||ControlBridge.owns(caller);}
        public void clear(){if(writer(WALKER.getCallerClass()))super.clear();}
        public boolean enqueue(){return writer(WALKER.getCallerClass())&&super.enqueue();}
    }
    private static final class ControlKey extends WeakReference<Object>{
        final int hash;ControlKey previous,next;boolean executionList,executionMap,fieldGate;
        ControlKey(Object value,ControlKey next){super(value,DEAD_CONTROLS);hash=System.identityHashCode(value);this.next=next;if(next!=null)next.previous=this;}
        public void clear(){if(Key.writer(WALKER.getCallerClass()))super.clear();}
        public boolean enqueue(){return Key.writer(WALKER.getCallerClass())&&super.enqueue();}
    }
    /** Used only under its own monitor; registration never enters a monitored JDK map. */
    private static final class ControlRegistry {
        private ControlKey[] table=new ControlKey[16];private int size;
        ControlRegistry(){
            if(AUTHORITY.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CONTROL_REGISTRY_OWNER_REQUIRED");
            table[bucket(System.identityHashCode(table),table.length)]=new ControlKey(table,null);size=1;
        }
        private void writer(Class<?> caller){
            if(CONTROLS!=null&&this!=CONTROLS||caller!=CodeSourceBridge.class&&caller!=ControlRegistry.class)
                throw new SecurityException("ACTUAL_CONTROL_REGISTRY_WRITER_REQUIRED");
        }
        private static int bucket(int hash,int length){return (hash^(hash>>>16))&(length-1);}
        private ControlKey find(Object value){
            int hash=System.identityHashCode(value);
            for(ControlKey key=table[bucket(hash,table.length)];key!=null;key=key.next)
                if(key.hash==hash&&key.get()==value)return key;
            return null;
        }
        boolean contains(Object value){return find(value)!=null;}
        boolean executionList(Object value){ControlKey key=find(value);return key!=null&&key.executionList;}
        boolean executionMap(Object value){ControlKey key=find(value);return key!=null&&key.executionMap;}
        boolean fieldGate(Object value){ControlKey key=find(value);return key!=null&&key.fieldGate;}
        ControlKey add(Object value){
            writer(AUTHORITY.getCallerClass());
            ControlKey known=find(value);if(known!=null)return known;
            if(size>=table.length-(table.length>>>2)&&table.length<(1<<30)){
                ControlKey[] prior=table,next=new ControlKey[prior.length*2];
                for(ControlKey first:prior)for(ControlKey key=first;key!=null;){
                    ControlKey following=key.next;int at=bucket(key.hash,next.length);
                    key.previous=null;key.next=next[at];if(key.next!=null)key.next.previous=key;next[at]=key;key=following;
                }
                table=next;
                // Every new backing keeps its own weak protection entry; old
                // tables remain protected while held and do not stay alive here.
                int at=bucket(System.identityHashCode(next),next.length);
                next[at]=new ControlKey(next,next[at]);size++;
            }
            int at=bucket(System.identityHashCode(value),table.length);
            ControlKey added=new ControlKey(value,table[at]);table[at]=added;size++;return added;
        }
        void reap(ControlKey first){
            writer(AUTHORITY.getCallerClass());
            for(ControlKey retired=first;retired!=null;retired=(ControlKey)DEAD_CONTROLS.poll()){
                int at=bucket(retired.hash,table.length);ControlKey previous=retired.previous,next=retired.next;
                // The queued key is the actual registered node. Unlink it under
                // this monitor without rescanning the same bucket for every
                // object collected in a large metadata batch.
                if(previous==null){if(table[at]!=retired)continue;table[at]=next;}
                else {if(previous.next!=retired)continue;previous.next=next;}
                if(next!=null)next.previous=previous;
                retired.previous=null;retired.next=null;size--;
            }
        }
    }
    private CodeSourceBridge(){}
    private static Class<?> caller(){return AUTHORITY.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type.getNestHost()!=CodeSourceBridge.class).findFirst().orElse(null));}
    private static void requireAgent(){Class<?> caller=caller();if(agent==null||caller==null||caller.getNestHost()!=agent||!ControlBridge.owns(caller))throw new SecurityException("CODE_SOURCE_AGENT_REQUIRED");}
    public static void install(Class<?> actualAgent)throws ReflectiveOperationException{
        Class<?> caller=caller();if(!ControlBridge.owns(caller)||caller!=actualAgent||agent!=null&&agent!=actualAgent)throw new SecurityException("CODE_SOURCE_AGENT_REQUIRED");
        TaskBridge.controlPublication(DEAD_CONTROLS,new Object[]{DEAD_CONTROLS});
        agent=actualAgent;producer=actualAgent.getMethod("sourceModuleRead",Module.class);seed(ModuleLayer.boot());
        encoder=actualAgent.getMethod("externalTreeImage",Object.class);
        begin=actualAgent.getMethod("externalCodeBegin",Object.class,Module[].class,Module[].class);
        end=actualAgent.getMethod("externalCodeEnd",Object.class,Object.class,Module[].class);
        copied=actualAgent.getMethod("externalTreeCopied",Object.class,Object.class);
        encoded=actualAgent.getMethod("externalCodeEncoded",Object.class,byte[].class);
        observed=actualAgent.getMethod("externalTreeObserved",Object.class,Module.class);
        dependency=actualAgent.getMethod("externalMixinDependency",Object.class,Module.class,Module[].class);
        payload=actualAgent.getMethod("externalMixinPayload",Object.class,Module.class,Module[].class,boolean.class);
        selection=actualAgent.getMethod("externalMixinSelection",Object.class,Module.class,Module[].class);
        metadataImages=actualAgent.getMethod("externalMixinMetadata",Object.class,Module.class,Module[].class);
        memberImage=actualAgent.getMethod("externalMemberMetadata",Object.class);
        prepared=actualAgent.getMethod("externalClassPrepared",Class.class);
        TaskBridge.controlPublication(MODULES,new Object[]{IMAGES,BYTE_CONTRIBUTORS,READERS,TREES,TREE_IMAGES,MIXINS,MIXIN_TREES,MIXIN_OPTIONS,MIXIN_RESTRICTIONS,TRACKER_RESTRICTIONS,METADATA,METADATA_TREES,METADATA_ROWS,MEMBER_IMAGES,CONTEXT_READS,STATE_READS,SELECTIONS,RESOURCE_SOURCES,CONFIGURATIONS,CONTEXTS,HOLDERS,WRITERS,COPYING,PREPARING_CONFIGS,REGISTRATIONS,METADATA_USE,CONTROLS,ROLES,CODE_METHODS,CODE_FIELDS,CODE_VALUES,FIELD_HOLDERS,FIELD_CLASSES,agent,producer,encoder,begin,end,copied,encoded,observed,dependency,payload,selection,metadataImages,memberImage,prepared});
    }
    public static void codeMethods(ClassLoader loader,String name,String[] methods){
        requireAgent();codeMethods(loader,name,methods,null);
    }
    public static void codeMethods(Class<?> actual,String[] methods){
        requireAgent();Objects.requireNonNull(actual);codeMethods(actual.getClassLoader(),actual.getName(),methods,actual);
    }
    private static void codeMethods(ClassLoader loader,String name,String[] methods,Class<?> actual){
        Set<String> selected=Set.copyOf(Arrays.asList(methods));
        synchronized(CODE_METHODS){
            for(var buckets=CODE_METHODS.values().iterator();buckets.hasNext();){
                List<CodeMethods> entries=buckets.next();
                entries.removeIf(entry->!entry.bootstrap()&&entry.loader().get()==null||entry.actual()!=null&&entry.actual().get()==null);
                if(entries.isEmpty())buckets.remove();
            }
            List<CodeMethods> entries=CODE_METHODS.get(name);
            if(entries==null){entries=new ArrayList<>();protect(entries);CODE_METHODS.put(name,entries);}
            for(int i=0;i<entries.size();i++){
                CodeMethods prior=entries.get(i);
                if(codeSelector(prior.name(),prior.loader(),prior.bootstrap(),prior.actual(),loader,name,actual)){
                    Set<String> merged=new HashSet<>(prior.methods());merged.addAll(selected);
                    CodeMethods entry=new CodeMethods(name,prior.loader(),prior.bootstrap(),Set.copyOf(merged),prior.actual());protect(entry,entry.methods());entries.set(i,entry);return;
                }
            }
            CodeMethods entry=new CodeMethods(name,new Ref<>(loader),loader==null,selected,actual==null?null:new Ref<>(actual));protect(entry,entry.loader(),entry.methods(),entry.actual());entries.add(entry);
        }
    }
    static Module[] frameSources(StackWalker.StackFrame frame){
        Module[] execution=ExecutionFlow.frameSources(frame);if(execution!=null)return execution;
        Class<?> type=frame.getDeclaringClass();boolean tracked=false;
        synchronized(CODE_METHODS){
            List<CodeMethods> entries=CODE_METHODS.get(type.getName());
            if(entries!=null)for(CodeMethods entry:entries)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),type)
                    &&entry.methods().contains(frame.getMethodName()+frame.getDescriptor())){tracked=true;break;}
        }
        if(!tracked||frame.getByteCodeIndex()<0)return NO_FRAME_SOURCES;
        Module[] actual=NativeControl.codeFrame(frame);return actual==null?NO_FRAME_SOURCES:actual;
    }
    public static Object executionPlan(Object[][] rows){requireAgent();return ExecutionFlow.plan(rows);}
    private static List<StackWalker.StackFrame> executionStack(){
        return WALKER.walk(frames->frames.dropWhile(frame->frame.getDeclaringClass().getNestHost()==CodeSourceBridge.class).toList());
    }
    public static Object executionRootEnter(){
        return NativeControl.executionRootAllowed()?executionEnter():null;
    }
    public static Object executionEnter(){
        List<StackWalker.StackFrame> frames=executionStack();if(frames.isEmpty())return null;
        StackWalker.StackFrame current=frames.get(0);
        Object plan=NativeControl.executionPlan(current),token=ExecutionFlow.enter(plan,current,frames.size(),frames.size()>1?frames.get(1):null);
        if(ExecutionFlow.nativeExitRequired(token)&&!NativeControl.executionWatch(current,plan,token)){ExecutionFlow.watchFailed(token);return null;}
        return token;
    }
    public static void executionBefore(Object token,int instruction){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.before(token,instruction,frames.get(0),frames.size());
    }
    public static void executionAfterCall(Object token,int instruction){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.afterCall(token,instruction,frames.get(0),frames.size());
    }
    public static void executionCaught(Object token,int instruction){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.caught(token,instruction,frames.get(0),frames.size());
    }
    public static void executionExit(Object token,boolean normal){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.exit(token,normal,frames.get(0),frames.size());
    }
    public static void executionArgument(Object token,int slot,Object actual){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.argument(token,slot,actual,frames.get(0),frames.size());
    }
    public static void executionConstructor(Object token,Object receiver){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.constructor(token,receiver,frames.get(0),frames.size());
    }
    public static void executionUninitializedWrite(Object token,int instruction){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.uninitializedWrite(token,instruction,frames.get(0),frames.size());
    }
    static boolean executionPopped(Object token,boolean normal){
        if(AUTHORITY.getCallerClass()!=NativeControl.class||!NativeControl.executionPopPermit(token))throw new SecurityException("ACTUAL_EXECUTION_POP_REQUIRED");
        return ExecutionFlow.popped(token,normal);
    }
    public static void executionReference(Object token,int instruction,Object actual){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.reference(token,instruction,actual,frames.get(0),frames.size());
    }
    public static void executionInitialized(Object token,int instruction,Object actual,Class<?> declaring){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.initialized(token,instruction,actual,declaring,frames.get(0),frames.size());
    }
    public static void executionHeapBefore(Object token,int instruction,Class<?> symbolic,Object receiver,int index){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.heapBefore(token,instruction,symbolic,receiver,index,frames.get(0),frames.size());
    }
    public static Object executionUnsafeBefore(Object token,int instruction,Object accessor,Object receiver,long offset,long length,Object proposed,Object source,long sourceOffset){
        List<StackWalker.StackFrame> frames=executionStack();if(frames.isEmpty())throw new IllegalStateException("ACTUAL_UNSAFE_CALL_SITE_REQUIRED");
        return ExecutionFlow.unsafeBefore(token,instruction,accessor,receiver,offset,length,proposed,source,sourceOffset,frames.get(0),frames.size());
    }
    public static void executionUnsafeAfter(Object token,int instruction,Object receipt,boolean written){
        List<StackWalker.StackFrame> frames=executionStack();if(frames.isEmpty())throw new IllegalStateException("ACTUAL_UNSAFE_CALL_SITE_REQUIRED");
        ExecutionFlow.unsafeAfter(token,instruction,receipt,written,frames.get(0),frames.size());
    }
    public static void executionUnsafeFailed(Object token,int instruction,Object receipt,Throwable original){
        try{executionUnsafeAfter(token,instruction,receipt,false);}catch(RuntimeException|Error cleanup){if(cleanup!=original)original.addSuppressed(cleanup);}
    }
    static Object executionUnsafeMutation(Object accessor,Object receiver,long offset,long length,Object proposed,Object source,long sourceOffset,String owner,String operation,String kind,boolean bulk,Module[] contributors){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return TaskBridge.beginExecutedUnsafeMutation(accessor,receiver,offset,length,proposed,source,sourceOffset,owner,operation,kind,bulk,contributors);
    }
    static void executionUnsafeMutationEnd(Object receipt,boolean written){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        TaskBridge.finishFieldMutation(receipt,written);
    }
    static Module[] executionUnsafeReadSources(Object receipt){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return TaskBridge.executedUnsafeReadSources(receipt);
    }
    static Module[] executionByteMemorySources(Object holder,long offset,long length){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_BOUNDARY_REQUIRED");
        return ExecutionFlow.byteMemorySources(holder,offset,length);
    }
    static Object executionBufferCall(Object receiver,Class<?> declaring,String method){
        if(AUTHORITY.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_VALUE_CALL_REQUIRED");
        if(!ExecutionFlow.hasFrame())return null;
        BufferFrames frames=WALKER.walk(BUFFER_CALL_FRAMES);
        if(frames==null||frames.callee().getDeclaringClass()!=declaring||!frames.callee().getMethodName().equals(method))return null;
        return ExecutionFlow.platformCall(receiver,frames.callee(),frames.caller(),frames.depth());
    }
    static Object executionByteMemoryReadBefore(Object carrier,long offset,long length){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_BOUNDARY_REQUIRED");
        return ExecutionFlow.byteMemoryReadBefore(carrier,offset,length);
    }
    static Module[] executionByteMemoryReadSources(Object token){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_RESULT_REQUIRED");
        return ExecutionFlow.byteMemoryReadSources(token);
    }
    static void executionByteMemoryReadEnd(Object token){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_COMPLETION_REQUIRED");
        ExecutionFlow.byteMemoryReadEnd(token);
    }
    static void executionBufferObserved(Object token,Module[] sources){
        if(AUTHORITY.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_BUFFER_VALUE_RESULT_REQUIRED");
        ExecutionFlow.platformObserved(token,sources);
    }
    public static void executionHeapAfter(Object token,int instruction,Object reference){
        if(token==null)return;List<StackWalker.StackFrame> frames=executionStack();if(!frames.isEmpty())ExecutionFlow.heapAfter(token,instruction,reference,frames.get(0),frames.size());
    }
    static Class<?> executionFieldOwner(Class<?> symbolic,String name,String descriptor){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.codeFieldOwner(symbolic,name,descriptor);
    }
    static boolean executionWatchField(Object holder,Class<?> declaring,String name,String descriptor){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.heapWatch(holder,declaring,name,descriptor);
    }
    static void executionFieldModified(Object holder,Class<?> declaring,String name,String descriptor,Class<?> writer,String method,String methodDescriptor,int instruction,Object plan,Module[] contributors){
        if(AUTHORITY.getCallerClass()!=NativeControl.class||!NativeControl.heapEventPermit(holder,declaring))throw new SecurityException("ACTUAL_HEAP_WRITE_EVENT_REQUIRED");
        ExecutionFlow.fieldModified(holder,declaring,name,descriptor,writer,method,methodDescriptor,instruction,plan,contributors);
    }
    static Object executionMemoryBefore(Object holder,Module[] contributors){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.memoryBefore(holder,contributors);
    }
    static Object executionFieldMemoryBefore(Object holder,Class<?> declaring,String name,String descriptor,Module[] contributors,boolean instruction){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.fieldMemoryBefore(holder,declaring,name,descriptor,contributors,instruction);
    }
    static Object[] executionReadField(Object holder,Class<?> declaring,String name,String descriptor){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.heapReadField(holder,declaring,name,descriptor);
    }
    static byte[] executionArrayImage(Object array,long start,int length){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.heapArrayImage(array,start,length);
    }
    static long executionArrayBits(Object array,long start,int length){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.heapArrayBits(array,start,length);
    }
    static boolean executionRestoreArrayBytes(Object array,long start,byte[] expected,byte[] incoming){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.restoreHeapArrayBytes(array,start,expected,incoming);
    }
    public static Object[][] groupHeapValues(Module[] selected){requireAgent();return ExecutionFlow.groupValues(selected);}
    public static boolean restoreHeapValue(Object token){
        if(!TaskBridge.recoveryWriter())throw new SecurityException("HEAP_RECOVERY_CORE_REQUIRED");return ExecutionFlow.restoreValue(token);
    }
    static java.util.concurrent.locks.ReentrantLock executionRecoveryGate(Object holder){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return TaskBridge.heapRecoveryGate(holder);
    }
    static boolean executionRestoreField(java.lang.reflect.Field field,Object receiver,Object expected,Object incoming){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.restoreRecoveredField(field,receiver,expected,incoming);
    }
    static boolean executionRestoreArray(Object array,int index,Object expected,Object incoming){
        if(AUTHORITY.getCallerClass().getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return NativeControl.restoreHeapArray(array,index,expected,incoming);
    }
    static Object executionArrayMemoryBefore(Object holder,int start,int length,Module[] contributors){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.arrayMemoryBefore(holder,start,length,contributors);
    }
    static long executionArrayRevision(Object holder){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.arrayRevision(holder);
    }
    static Object executionByteMemoryBefore(Object holder,long offset,long length,Module[] contributors){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.byteMemoryBefore(holder,offset,length,contributors);
    }
    static Object executionByteMemoryBefore(Object holder,long offset,long length,Module[] contributors,boolean reference){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return ExecutionFlow.byteMemoryBefore(holder,offset,length,contributors,reference);
    }
    static void executionMemoryAfter(Object token,boolean written){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        ExecutionFlow.memoryAfter(token,written);
    }
    static void executionMemoryContributors(Object token,Module[] contributors){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        ExecutionFlow.memoryContributors(token,contributors);
    }
    static String executionRetirementGap(Module module){
        if(AUTHORITY.getCallerClass()!=NativeControl.class)throw new SecurityException("ACTUAL_CODE_STATE_AGENT_REQUIRED");
        return ExecutionFlow.retirementGap(module);
    }
    static Module[] executionSources(){return ExecutionFlow.sources();}
    static boolean executionUnknown(){return ExecutionFlow.unknown();}
    static Module frameModule(StackWalker.StackFrame frame){
        Module[] actual=frameSources(frame);return actual.length==1?actual[0]:actual.length==0?DefinitionBridge.module(frame.getDeclaringClass()):null;
    }
    public static void codeFields(ClassLoader loader,String name,String[] keys,Module[][] owners){
        requireAgent();codeFields(loader,name,keys,owners,null);
    }
    public static void codeFields(Class<?> actual,String[] keys,Module[][] owners){
        requireAgent();Objects.requireNonNull(actual);codeFields(actual.getClassLoader(),actual.getName(),keys,owners,actual);
    }
    private static void codeFields(ClassLoader loader,String name,String[] keys,Module[][] owners,Class<?> actual){
        if(keys.length!=owners.length)throw new IllegalArgumentException("ACTUAL_CODE_FIELD_LAYOUT_REQUIRED");if(keys.length==0)return;
        Map<String,CodeField> fields=new LedgerMap<>();
        for(int i=0;i<keys.length;i++){
            int separator=keys[i].indexOf('\u0000');if(separator<1)throw new IllegalArgumentException("ACTUAL_CODE_FIELD_LAYOUT_REQUIRED");
            Module[] sources=owners[i].clone();CodeField field=new CodeField(keys[i].substring(0,separator),keys[i].substring(separator+1),sources);fields.put(field.name(),field);
            protect(sources,field);
        }
        synchronized(CODE_FIELDS){
            for(int i=0;i<CODE_FIELDS.size();i++){
                CodeFields prior=CODE_FIELDS.get(i);if(codeSelector(prior.name(),prior.loader(),prior.bootstrap(),prior.actual(),loader,name,actual)){
                    Map<String,CodeField> merged=new LedgerMap<>(prior.fields());merged.putAll(fields);CodeFields entry=new CodeFields(name,prior.loader(),prior.bootstrap(),Map.copyOf(merged),prior.actual());protect(entry,entry.fields());CODE_FIELDS.set(i,entry);return;
                }
            }CodeFields entry=new CodeFields(name,new Ref<>(loader),loader==null,Map.copyOf(fields),actual==null?null:new Ref<>(actual));protect(entry,entry.loader(),entry.fields(),entry.actual());CODE_FIELDS.add(entry);
        }
    }
    public static void codeValues(ClassLoader loader,String name,Object[][] rows){
        requireAgent();codeValues(loader,name,rows,null);
    }
    public static void codeValues(Class<?> actual,Object[][] rows){
        requireAgent();Objects.requireNonNull(actual);codeValues(actual.getClassLoader(),actual.getName(),rows,actual);
    }
    private static void codeValues(ClassLoader loader,String name,Object[][] rows,Class<?> actual){
        if(rows.length==0)return;
        synchronized(CODE_VALUES){
            int position=-1;List<CodeValue> values=new ArrayList<>();
            for(int i=0;i<CODE_VALUES.size();i++){CodeValues prior=CODE_VALUES.get(i);if(codeSelector(prior.name(),prior.loader(),prior.bootstrap(),prior.actual(),loader,name,actual)){position=i;values.addAll(prior.fields());break;}}
            for(Object[] row:rows){
                String key=(String)row[0];int separator=key.indexOf('\u0000');if(separator<1)throw new IllegalArgumentException("ACTUAL_FIELD_VALUE_LAYOUT_REQUIRED");
                String field=key.substring(0,separator),descriptor=key.substring(separator+1);Object expected=constantValue(descriptor,row[1]),baseline=constantValue(descriptor,row[2]);Module[] sources=((Module[])row[3]).clone();
                Set<Module> owners=new HashSet<>(Arrays.asList(sources));
                if(values.stream().anyMatch(value->value.name.equals(field)&&value.descriptor.equals(descriptor)&&constantEquals(value.expected,expected)&&constantEquals(value.baseline,baseline)&&new HashSet<>(Arrays.asList(value.sources)).equals(owners)))continue;
                CodeValue value=new CodeValue(field,descriptor,expected,baseline,sources);values.add(value);protect(value,sources);
            }
            CodeValues entry=new CodeValues(name,new Ref<>(loader),loader==null,List.copyOf(values),actual==null?null:new Ref<>(actual));protect(entry,entry.loader(),entry.fields(),entry.actual());
            if(position<0)CODE_VALUES.add(entry);else CODE_VALUES.set(position,entry);
        }
    }
    private static boolean codeMatches(String name,WeakReference<ClassLoader> loader,boolean bootstrap,WeakReference<Class<?>> exact,Class<?> actual){
        return exact!=null?exact.get()==actual:!actual.isHidden()&&name.equals(actual.getName())&&bootstrap==(actual.getClassLoader()==null)&&loader.get()==actual.getClassLoader();
    }
    private static boolean codeSelector(String previousName,WeakReference<ClassLoader> previousLoader,boolean bootstrap,WeakReference<Class<?>> exact,ClassLoader loader,String name,Class<?> actual){
        return exact!=null?actual!=null&&exact.get()==actual:actual==null&&previousName.equals(name)&&bootstrap==(loader==null)&&previousLoader.get()==loader;
    }
    private static Object constantValue(String descriptor,Object value){
        return switch(descriptor){case "Z"->((Number)value).intValue()!=0;case "B"->((Number)value).byteValue();case "C"->(char)((Number)value).intValue();case "S"->((Number)value).shortValue();
            case "I"->((Number)value).intValue();case "J"->((Number)value).longValue();case "F"->((Number)value).floatValue();case "D"->((Number)value).doubleValue();case "Ljava/lang/String;"->((String)value).intern();
            default->throw new IllegalArgumentException("ACTUAL_CONSTANT_VALUE_TYPE_REQUIRED");};
    }
    private static boolean constantEquals(Object first,Object second){
        if(first instanceof Float a&&second instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(first instanceof Double a&&second instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
        return first instanceof String||second instanceof String?first==second:Objects.equals(first,second);
    }
    public static void constantRestored(Object token,java.lang.reflect.Field field)throws IllegalAccessException {
        if(!TaskBridge.recoveryWriter())throw new SecurityException("FIELD_CONSTANT_RECOVERY_CORE_REQUIRED");
        synchronized(CODE_VALUES){
            for(CodeValues entry:CODE_VALUES)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),field.getDeclaringClass()))
                for(CodeValue value:entry.fields())if(value==token&&value.name.equals(field.getName())&&value.descriptor.equals(field.getType().descriptorString())&&java.lang.reflect.Modifier.isStatic(field.getModifiers())){
                    if(!constantEquals(field.get(null),value.baseline))throw new IllegalStateException("FIELD_CONSTANT_RESTORE_READBACK_CHANGED");value.completed=true;return;
                }
        }
        throw new SecurityException("ACTUAL_FIELD_CONSTANT_RECEIPT_REQUIRED");
    }
    private static void reapControls(){ControlKey first=(ControlKey)DEAD_CONTROLS.poll();if(first!=null)CONTROLS.reap(first);}
    private static void protect(Object... objects){
        synchronized(CONTROLS){
            reapControls();
            for(Object value:objects)if(value!=null)CONTROLS.add(value);
        }
    }
    private static void protectOne(Object object){
        synchronized(CONTROLS){reapControls();if(object!=null)CONTROLS.add(object);}
    }
    static void executionControls(Object object){
        Class<?> caller=AUTHORITY.getCallerClass();if(caller.getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        protectOne(object);
    }
    static void executionControls(Object... objects){
        Class<?> caller=AUTHORITY.getCallerClass();if(caller.getNestHost()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        protect(objects);
    }
    static void executionMetadataList(Object object){
        Class<?> creator=AUTHORITY.getCallerClass();
        if(creator.getNestHost()!=ExecutionFlow.class||!(object instanceof ArrayList<?>)||object.getClass()!=creator)
            throw new SecurityException("ACTUAL_EXECUTION_METADATA_LIST_REQUIRED");
        synchronized(CONTROLS){reapControls();CONTROLS.add(object).executionList=true;}
    }
    static boolean executionList(Object object){
        if(!(object instanceof ArrayList<?>))return false;
        // The role belongs to this exact constructor-registered object. A clone
        // or another list of the same class cannot inherit it through its fields.
        // Reading this bit grants no registration, access or mutation authority.
        synchronized(CONTROLS){return CONTROLS.executionList(object);}
    }
    static void executionMetadataMap(Object object){
        Class<?> creator=AUTHORITY.getCallerClass();
        if(creator!=ExecutionFlow.metadataMapType()||!(object instanceof HashMap<?,?>)||object.getClass()!=creator)
            throw new SecurityException("ACTUAL_EXECUTION_METADATA_MAP_REQUIRED");
        synchronized(CONTROLS){reapControls();CONTROLS.add(object).executionMap=true;}
    }
    private static void ledgerMap(Object object){
        if(AUTHORITY.getCallerClass()!=LedgerMap.class||object.getClass()!=LedgerMap.class)
            throw new SecurityException("ACTUAL_CODE_LEDGER_MAP_REQUIRED");
        synchronized(CONTROLS){reapControls();CONTROLS.add(object).executionMap=true;}
    }
    static boolean executionMap(Object object){
        if(!(object instanceof HashMap<?,?>))return false;
        synchronized(CONTROLS){return CONTROLS.executionMap(object);}
    }
    static void fieldGateControls(java.util.concurrent.locks.ReentrantLock gate){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class||gate.getClass()!=java.util.concurrent.locks.ReentrantLock.class)
            throw new SecurityException("ACTUAL_FIELD_GATE_OWNER_REQUIRED");
        Object sync;
        if(NativeControl.available()){
            Object[] read=NativeControl.heapReadField(gate,java.util.concurrent.locks.ReentrantLock.class,"sync",
                    "Ljava/util/concurrent/locks/ReentrantLock$Sync;");
            if(read==null||read.length!=1)throw new IllegalStateException("FIELD_GATE_SYNC_UNAVAILABLE");
            sync=read[0];
        }else try{
            var field=java.util.concurrent.locks.ReentrantLock.class.getDeclaredField("sync");field.setAccessible(true);sync=field.get(gate);
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("FIELD_GATE_SYNC_UNAVAILABLE",unavailable);}
        if(!(sync instanceof java.util.concurrent.locks.AbstractQueuedSynchronizer))throw new IllegalStateException("FIELD_GATE_SYNC_UNAVAILABLE");
        // Bind both exact identities before the gate is published. Its JDK
        // synchronizer is controller state, never a business heap carrier.
        synchronized(CONTROLS){reapControls();CONTROLS.add(gate).fieldGate=true;CONTROLS.add(sync).fieldGate=true;}
    }
    static boolean fieldGateMetadata(Object object){
        if(!(object instanceof java.util.concurrent.locks.ReentrantLock||object instanceof java.util.concurrent.locks.AbstractQueuedSynchronizer))return false;
        synchronized(CONTROLS){return CONTROLS.fieldGate(object);}
    }
    static void mutationControls(Object... objects){
        if(AUTHORITY.getCallerClass().getNestHost()!=TaskBridge.class)throw new SecurityException("ACTUAL_MUTATION_SOURCE_STATE_REQUIRED");
        protect(objects);
    }
    private static CodeField codeField(Class<?> declaring,String name){
        synchronized(CODE_FIELDS){for(CodeFields entry:CODE_FIELDS)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),declaring))return entry.fields().get(name);}return null;
    }
    static boolean fieldAllowed(Object receiver,Class<?> declaring,String name){
        CodeField source=codeField(declaring,name);if(source==null)return true;
        try{
            var field=declaring.getDeclaredField(name);if(!field.getType().descriptorString().equals(source.descriptor()))return true;
            boolean statik=java.lang.reflect.Modifier.isStatic(field.getModifiers());Object holder=statik?declaring:receiver;
            if(holder!=null)synchronized(MODULES){
                reap();if(statik){boolean known=false;for(var reference:FIELD_CLASSES)if(reference.get()==declaring){known=true;break;}if(!known)FIELD_CLASSES.add(new Ref<>(declaring));}
                else FIELD_HOLDERS.computeIfAbsent(new Key(holder,true),ignored->new HashSet<>()).add(new FieldHold(declaring,name,source.descriptor()));
            }
        }catch(NoSuchFieldException absent){return true;}
        for(Module module:source.owners())if(TaskBridge.modStopped(module))return false;return true;
    }
    public static Object[][] groupFields(Module[] selected,Class<?>[] initialized){
        
        requireAgent();Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());modules.addAll(Arrays.asList(selected));List<Object[]> rows=new ArrayList<>();
        synchronized(MODULES){
            Set<Class<?>> ready=Collections.newSetFromMap(new IdentityHashMap<>());ready.addAll(Arrays.asList(initialized));
            reap();for(Class<?> type:initialized){
                boolean known=false;for(WeakReference<Class<?>> reference:FIELD_CLASSES)if(reference.get()==type){known=true;break;}
                if(!known){synchronized(CODE_FIELDS){for(CodeFields entry:CODE_FIELDS)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),type)){FIELD_CLASSES.add(new Ref<>(type));break;}}}
                if(!known)synchronized(CODE_VALUES){for(CodeValues entry:CODE_VALUES)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),type)){
                    boolean present=false;for(var reference:FIELD_CLASSES)if(reference.get()==type){present=true;break;}if(!present)FIELD_CLASSES.add(new Ref<>(type));break;
                }}
            }
            FIELD_CLASSES.removeIf(reference->reference.get()==null);
            for(WeakReference<Class<?>> reference:FIELD_CLASSES){Class<?> type=reference.get();if(type==null)continue;
                for(var field:type.getDeclaredFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())){
                    CodeField source=codeField(type,field.getName());if(selectedField(source,field,modules))rows.add(new Object[]{field,null,ready.contains(type)});
                }
            }
            for(var entry:FIELD_HOLDERS.entrySet()){
                Object holder=entry.getKey().get();if(holder==null)continue;
                for(Class<?> type=holder.getClass();type!=null;type=type.getSuperclass())for(var field:type.getDeclaredFields()){
                    if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;CodeField source=codeField(type,field.getName());
                    if(source!=null&&entry.getValue().contains(new FieldHold(type,field.getName(),source.descriptor()))&&selectedField(source,field,modules))rows.add(new Object[]{field,holder});
                }
            }
            synchronized(CODE_VALUES){for(CodeValues entry:CODE_VALUES)for(var reference:FIELD_CLASSES){Class<?> type=reference.get();if(type==null)continue;
                if(!codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),type))continue;
                for(CodeValue value:entry.fields())if(!value.completed&&Arrays.stream(value.sources).anyMatch(modules::contains))try{
                    var field=type.getDeclaredField(value.name);if(field.getType().descriptorString().equals(value.descriptor)&&java.lang.reflect.Modifier.isStatic(field.getModifiers()))rows.add(new Object[]{field,null,ready.contains(type),value.expected,value.baseline,value});
                }catch(NoSuchFieldException absent){/* The removed declaration is still a schema gap, not a fabricated value reset. */}
            }}
        }return rows.toArray(Object[][]::new);
    }
    private static boolean selectedField(CodeField source,java.lang.reflect.Field field,Set<Module> modules){
        return source!=null&&source.owners().length!=0&&field.getType().descriptorString().equals(source.descriptor())&&modules.containsAll(Arrays.asList(source.owners()));
    }
    static boolean fieldRangeAllowed(Object receiver,long offset,long length){
        if(receiver==null||length<=0)return true;
        if(!fieldRangeAllowed(receiver,receiver.getClass(),false,offset,length))return false;
        return !(receiver instanceof Class<?> type)||fieldRangeAllowed(receiver,type,true,offset,length);
    }
    private static boolean fieldRangeAllowed(Object receiver,Class<?> actual,boolean statik,long offset,long length){
        for(Class<?> type=actual;type!=null;type=statik?null:type.getSuperclass()){
            CodeFields entry=null;synchronized(CODE_FIELDS){for(CodeFields candidate:CODE_FIELDS)if(codeMatches(candidate.name(),candidate.loader(),candidate.bootstrap(),candidate.actual(),type)){entry=candidate;break;}}
            if(entry==null)continue;
            for(CodeField source:entry.fields().values())try{
                var field=type.getDeclaredField(source.name());if(java.lang.reflect.Modifier.isStatic(field.getModifiers())!=statik||!field.getType().descriptorString().equals(source.descriptor()))continue;
                long[] span=ResourceBridge.fieldSpan(field,receiver);if(span==null)continue;
                if(offset<0||length>Long.MAX_VALUE-offset)return false;
                if(offset<span[0]+span[1]&&span[0]<offset+length&&!fieldAllowed(receiver,type,source.name()))return false;
            }catch(NoSuchFieldException absent){/* A changed declaration is not this observed field. */}
        }return true;
    }
    public static Class<?>[] fieldClasses(){
        requireAgent();synchronized(MODULES){return FIELD_CLASSES.stream().map(WeakReference::get).filter(Objects::nonNull).toArray(Class<?>[]::new);}
    }
    public static void codeClass(Class<?> actual){
        requireAgent();if(actual==null)return;synchronized(MODULES){for(var reference:FIELD_CLASSES)if(reference.get()==actual)return;FIELD_CLASSES.add(new Ref<>(actual));}
    }
    static void classPrepared(Class<?> actual){
        if(!NativeControl.preparedClass(actual))throw new SecurityException("ACTUAL_VM_CLASS_PREPARATION_REQUIRED");
        Method callback=prepared;if(callback!=null)call(callback,actual);
        boolean fields=false;synchronized(CODE_FIELDS){for(CodeFields entry:CODE_FIELDS)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),actual)){fields=true;break;}}
        if(!fields)synchronized(CODE_VALUES){for(CodeValues entry:CODE_VALUES)if(codeMatches(entry.name(),entry.loader(),entry.bootstrap(),entry.actual(),actual)){fields=true;break;}}
        if(fields)synchronized(MODULES){for(var reference:FIELD_CLASSES)if(reference.get()==actual)return;FIELD_CLASSES.add(new Ref<>(actual));}
    }
    public static void seedLayer(ModuleLayer layer){requireAgent();seed(layer);}
    private static void seed(ModuleLayer layer){
        List<Module> observed=new ArrayList<>();
        synchronized(MODULES){
            reap();for(Module module:layer.modules()){
                var resolved=layer.configuration().findModule(module.getName()).orElse(null);
                if(resolved==null||resolved.configuration()!=layer.configuration()||layer.findLoader(module.getName())!=module.getClassLoader())continue;
                MODULES.put(new Key(resolved.reference(),true),new Ref<>(module));protect(resolved.reference());observed.add(module);
            }
        }
        for(Module module:observed)notifyProducer(module);
    }
    private static void notifyProducer(Module module){
        Method callback=producer;if(callback==null)return;
        try{callback.invoke(null,module);}catch(ReflectiveOperationException failure){throw new IllegalStateException("CODE_SOURCE_MODULE_PUBLICATION_FAILED",failure);}
    }
    public static void layerDefined(Object result){
        var frame=WALKER.walk(frames->frames.filter(f->f.getDeclaringClass().getNestHost()!=CodeSourceBridge.class).findFirst().orElse(null));
        if(frame==null||frame.getDeclaringClass()!=ModuleLayer.class||!frame.getMethodName().startsWith("defineModules"))throw new SecurityException("ACTUAL_MODULE_LAYER_DEFINITION_REQUIRED");
        ModuleLayer layer=result instanceof ModuleLayer value?value:result instanceof ModuleLayer.Controller controller?controller.layer():null;
        if(layer==null)throw new IllegalArgumentException("DEFINED_MODULE_LAYER_REQUIRED");seed(layer);
    }
    public static void expect(String name,ClassLoader loader,Module module){
        requireAgent();synchronized(ROLES){
            for(Role role:ROLES)if(role.name().equals(name)&&role.bootstrap()==(loader==null)&&role.loader().get()==loader){
                if(role.module().get()!=module)throw new IllegalStateException("CODE_SOURCE_ROLE_MODULE_CHANGED");return;
            }
            ROLES.add(new Role(name,new Ref<>(loader),loader==null,new Ref<>(module)));
        }
    }
    private static Class<?> boundary(String method){
        var frame=WALKER.walk(frames->frames.filter(f->f.getDeclaringClass().getNestHost()!=CodeSourceBridge.class).findFirst().orElse(null));
        if(frame==null||!frame.getMethodName().equals(method))throw new SecurityException("ACTUAL_CODE_SOURCE_BOUNDARY_REQUIRED");
        Class<?> type=frame.getDeclaringClass();Module module=DefinitionBridge.module(type);
        synchronized(ROLES){for(Role role:ROLES)if(role.name().equals(type.getName())&&role.bootstrap()==(type.getClassLoader()==null)
                &&role.loader().get()==type.getClassLoader()&&role.module().get()==module)return type;}
        throw new SecurityException("CODE_SOURCE_ROLE_UNREGISTERED");
    }
    public static byte[] moduleBytes(ClassLoader loader,Object reader,ModuleReference reference,byte[] bytes){
        boundary("getClassBytes");Module module;
        synchronized(MODULES){reap();WeakReference<Module> actual=MODULES.get(new Key(reference,false));module=actual==null?null:actual.get();}
        if(module==null||module.getClassLoader()!=loader||bytes==null||bytes.length==0||Arrays.stream(resourceSources(reader)).noneMatch(source->source==module))return bytes;
        Image image=source(module,bytes,new Module[]{module});synchronized(MODULES){IMAGES.put(new Key(bytes,true),image);}return bytes;
    }
    private static Module[] resourceSources(Object object){synchronized(MODULES){reap();Module[] sources=RESOURCE_SOURCES.get(new Key(object,false));return sources==null?new Module[0]:sources;}}
    private static void resourceSource(Object object,Module[] sources){if(object==null||sources.length==0)return;Module[] copy=sources.clone();protect((Object)copy);synchronized(MODULES){RESOURCE_SOURCES.put(new Key(object,true),copy);}}
    public static void moduleProvider(Object reference,Object provider){
        boundary("jar");Module module;synchronized(MODULES){WeakReference<Module> actual=MODULES.get(new Key(reference,false));module=actual==null?null:actual.get();}
        if(module!=null)resourceSource(provider,merge(resourceSources(provider),new Module[]{module}));
    }
    public static void moduleReader(Object reference,Object reader){
        boundary("open");Module module;synchronized(MODULES){WeakReference<Module> actual=MODULES.get(new Key(reference,false));module=actual==null?null:actual.get();}
        if(module!=null){resourceSource(reader,new Module[]{module});protect(reader);}
    }
    public static void resourceLocated(Object provider,Object optional){
        boundary("findFile");if(optional instanceof Optional<?> location&&location.isPresent())resourceSource(location.get(),resourceSources(provider));
    }
    public static void providerOpened(Object provider,Object optional){
        boundary("open");if(optional instanceof Optional<?> stream&&stream.isPresent())resourceSource(stream.get(),resourceSources(provider));
    }
    public static void moduleResource(Object reference,Object url){
        boundary("readerToURL");Module module;synchronized(MODULES){WeakReference<Module> actual=MODULES.get(new Key(reference,false));module=actual==null?null:actual.get();}
        if(module!=null){resourceSource(url,new Module[]{module});protect(url);}
    }
    public static void resourceURL(Object optional,Object url){
        boundary("toURL");if(optional instanceof Optional<?> location&&location.isPresent()){Module[] sources=resourceSources(location.get());resourceSource(url,sources);if(sources.length!=0)protect(url);}
    }
    public static void resourceOpened(Object url,Object stream){boundary("openStream");resourceSource(stream,resourceSources(url));}
    public static void resourceReader(Object reader,Object stream){boundary("<init>");resourceSource(reader,resourceSources(stream));}
    private static Module[] configurationSources(Object config){synchronized(MODULES){reap();Module[] sources=CONFIGURATIONS.get(new Key(config,false));return sources==null?new Module[0]:sources;}}
    private static void configurationSource(Object config,Module[] sources){if(config==null||sources.length==0)return;Module[] copy=sources.clone();protect((Object)copy);synchronized(MODULES){CONFIGURATIONS.put(new Key(config,true),copy);}}
    public static void configurationParsed(Object config,Object reader){
        Class<?> type=boundary("create");if(config==null||config.getClass()!=type)return;
        configurationSource(config,merge(resourceSources(reader),TaskBridge.invokingSources()));
    }
    public static void configurationHandle(Object config,Object handle){
        boundary("getHandle");if(handle!=null&&field(handle,"config")==config)configurationSource(handle,configurationSources(config));
    }
    public static void configurationParent(boolean assigned,Object config,Object handle){
        boundary("assignParent");if(assigned)configurationSource(config,merge(configurationSources(config),configurationSources(handle)));
    }
    public static boolean configurationInstalling(Object handle){
        boundary("registerConfiguration");if(handle==null)return true;
        Object config=field(handle,"config");Module[] sources=merge(merge(configurationSources(config),configurationSources(handle)),TaskBridge.invokingSources());
        if(sources.length!=0){configurationSource(config,sources);configurationSource(handle,sources);}
        return Arrays.stream(sources).noneMatch(TaskBridge::modStopped);
    }
    public static boolean configurationAllowed(Object config){
        String method=callerMethod();if(!Set.of("onLoad","prepare","postInitialise","select").contains(method))throw new SecurityException("ACTUAL_CONFIG_CONSUMER_REQUIRED");boundary(method);
        return Arrays.stream(configurationSources(config)).noneMatch(TaskBridge::modStopped);
    }
    public static void classReader(Object reader,byte[] bytes){
        Class<?> declaring=boundary("<init>");Image image;
        synchronized(MODULES){reap();image=IMAGES.get(new Key(bytes,false));}
        if(image==null||!MessageDigest.isEqual(image.digest(),hash(bytes)))return;
        synchronized(MODULES){READERS.put(new Key(reader,true),new Reader(image,new Ref<>(bytes),declaring));}
    }
    public static void classTree(Object reader,Object visitor){
        Class<?> declaring=boundary("accept");Reader record;
        synchronized(MODULES){reap();record=READERS.get(new Key(reader,false));}
        if(record==null||record.declaring()!=declaring)return;
        byte[] bytes=record.bytes().get();if(bytes==null||!MessageDigest.isEqual(record.image().digest(),hash(bytes)))return;
        try{
            // Verify the actual parser still holds this observed byte array.
            var field=declaring.getDeclaredField("classFileBuffer");field.setAccessible(true);if(field.get(reader)!=bytes)return;
            Class<?> tree=Class.forName("org.objectweb.asm.tree.ClassNode",false,declaring.getClassLoader());if(!tree.isInstance(visitor))return;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ASM_SOURCE_BUFFER_UNOBSERVED",failure);}
        byte[] treeImage=image(visitor);
        synchronized(MODULES){TREES.put(new Key(visitor,true),record.image());TREE_IMAGES.put(new Key(visitor,true),hash(treeImage));}
        call(observed,visitor,record.image().module());
    }
    public static Module treeModule(Object tree){synchronized(MODULES){reap();Image image=TREES.get(new Key(tree,false));return image==null?null:image.module();}}
    private static Object call(Method method,Object... arguments){
        boolean serializing=method!=producer;Thread thread=Thread.currentThread();if(serializing)synchronized(COPYING){COPYING.merge(thread,1,Integer::sum);}
        try{return method.invoke(null,arguments);}catch(java.lang.reflect.InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException runtime)throw runtime;if(cause instanceof Error error)throw error;
            throw new IllegalStateException("EXTERNAL_CODE_OBSERVATION_FAILED",cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_CODE_OBSERVATION_FAILED",failure);}
        finally{if(serializing)synchronized(COPYING){int count=COPYING.get(thread);if(count==1)COPYING.remove(thread);else COPYING.put(thread,count-1);}}
    }
    private static boolean copying(){synchronized(COPYING){return COPYING.containsKey(Thread.currentThread());}}
    private static byte[] image(Object tree){
        Thread thread=Thread.currentThread();synchronized(COPYING){COPYING.merge(thread,1,Integer::sum);}
        try{return (byte[])call(encoder,tree);}finally{synchronized(COPYING){int count=COPYING.get(thread);if(count==1)COPYING.remove(thread);else COPYING.put(thread,count-1);}}
    }
    private static Image unchangedTree(Object tree){
        Image source;byte[] digest;synchronized(MODULES){reap();source=TREES.get(new Key(tree,false));digest=TREE_IMAGES.get(new Key(tree,false));}
        return source!=null&&digest!=null&&MessageDigest.isEqual(digest,hash(image(tree)))?source:null;
    }
    public static void mixinLoaded(Object mixin,Object tree){
        boundary("loadMixinClass");Image source=unchangedTree(tree);
        if(source!=null){Module[] sources=merge(source.sources(),configurationSources(field(mixin,"parent")));protect((Object)sources);
            synchronized(MODULES){MIXINS.put(new Key(mixin,true),new Image(source.module(),source.digest(),sources));MIXIN_TREES.put(new Key(mixin,true),new Ref<>(tree));}}
    }
    private static Replayed replayedTree(Object tree,Image source,boolean bodyOnly){
        if(source==null||TaskBridge.modStopped(source.module())||Arrays.stream(source.sources()).noneMatch(module->module!=source.module()&&TaskBridge.modStopped(module)))return new Replayed(tree,false,false);
        Object[] replay=(Object[])call(payload,tree,source.module(),source.sources(),bodyOnly);if(replay==null)return new Replayed(tree,false,false);
        Object replacement=replay[0];Module[] sources=((Module[])replay[1]).clone();protect((Object)sources);byte[] digest=hash(image(replacement));
        Image restored=new Image(source.module(),digest,sources);synchronized(MODULES){TREES.put(new Key(replacement,true),restored);TREE_IMAGES.put(new Key(replacement,true),digest);}
        return new Replayed(replacement,Boolean.TRUE.equals(replay[3]),Boolean.TRUE.equals(replay[4]));
    }
    public static Object mixinBytes(Object mixin,Object tree){
        boundary("loadMixinClass");Object replacement=replayedTree(tree,unchangedTree(tree),false).tree();mixinLoaded(mixin,replacement);return replacement;
    }
    public static void mixinPrepare(Object mixin){
        Class<?> type=boundary("createContextFor");
        prepareMixin(type,mixin);
    }
    public static void mixinConstructed(Object mixin,boolean ignorePlugin){
        Class<?> type=boundary("<init>");if(mixin==null||mixin.getClass()!=type)throw new SecurityException("ACTUAL_MIXIN_CONSTRUCTOR_REQUIRED");
        synchronized(MODULES){MIXIN_OPTIONS.put(new Key(mixin,true),ignorePlugin);}
    }
    public static Object mixinRegistrationBegin(Object mixin,Object tracker,String name){
        Class<?> type=boundary("<init>");if(mixin==null||mixin.getClass()!=type||field(mixin,"className")!=name||tracker==null)
            throw new SecurityException("ACTUAL_MIXIN_REGISTRATION_REQUIRED");
        return registration(mixin,tracker,name);
    }
    private static Registration registration(Object mixin,Object tracker,String name){
        Registration current=new Registration(mixin,tracker,name,REGISTRATIONS.get());REGISTRATIONS.set(current);return current;
    }
    public static void mixinRegistrationEnd(Object token){
        boundary("<init>");if(!(token instanceof Registration current)||!controlled(current)||REGISTRATIONS.get()!=current)
            throw new SecurityException("ACTUAL_MIXIN_REGISTRATION_SCOPE_REQUIRED");
        registrationEnd(current);
    }
    private static void registrationEnd(Registration current){if(current.previous==null)REGISTRATIONS.remove();else REGISTRATIONS.set(current.previous);}
    public static void trackerRegistered(Object tracker,Object set,Object name,boolean added){
        Class<?> type=boundary("registerInvalidClass");if(tracker==null||tracker.getClass()!=type||field(tracker,"invalidClasses")!=set||!(name instanceof String))
            throw new SecurityException("ACTUAL_MIXIN_TRACKER_SET_REQUIRED");
        synchronized(set){
            Object entry=trackerEntry(set,name);if(!(entry instanceof Map.Entry<?,?> actual))throw new IllegalStateException("MIXIN_TRACKER_ENTRY_UNOBSERVED");
            Registration scope=REGISTRATIONS.get();boolean owned=scope!=null&&scope.tracker==tracker&&scope.name==name&&field(scope.mixin,"className")==name;
            synchronized(MODULES){
                TrackerReceipt receipt=TRACKER_RESTRICTIONS.get(new Key(entry,false));
                if(receipt==null){receipt=new TrackerReceipt(tracker,set,entry,actual.getKey(),!added||!owned);TRACKER_RESTRICTIONS.put(new Key(entry,true),receipt);}
                else if(receipt.tracker.get()!=tracker||receipt.set.get()!=set||receipt.key.get()!=actual.getKey())throw new IllegalStateException("MIXIN_TRACKER_RECEIPT_CHANGED");
                if(owned){receipt.mixins.add(new Key(scope.mixin,false));MIXIN_RESTRICTIONS.put(new Key(scope.mixin,true),receipt);scope.receipt=receipt;}
                else receipt.outside=true;
            }
        }
    }
    private static Object trackerEntry(Object set,Object key){
        if(set==null||set.getClass()!=HashSet.class)throw new IllegalStateException("MIXIN_TRACKER_BACKING_UNAVAILABLE");
        Object map=field(set,"map");if(map==null||map.getClass()!=HashMap.class)throw new IllegalStateException("MIXIN_TRACKER_BACKING_UNAVAILABLE");
        for(var entry:((Map<?,?>)map).entrySet())if(Objects.equals(entry.getKey(),key))return entry;return null;
    }
    @SuppressWarnings("unchecked") private static LoadingChange changeLoading(Class<?> type,Object mixin,boolean before,boolean after)throws ReflectiveOperationException{
        if(before==after)return null;
        Object service=field(mixin,"service");Class<?> serviceType=Class.forName("org.spongepowered.asm.service.IMixinService",false,type.getClassLoader());
        Object tracker=serviceType.getMethod("getClassTracker").invoke(service);if(tracker==null)return null;
        Class<?> trackerType=Class.forName("org.spongepowered.asm.service.modlauncher.ModLauncherClassTracker",false,type.getClassLoader());
        if(tracker.getClass()!=trackerType)throw new IllegalStateException("MIXIN_CLASS_TRACKER_UNSUPPORTED");
        String name=(String)field(mixin,"className");
        if(Boolean.TRUE.equals(trackerType.getMethod("isClassLoaded",String.class).invoke(tracker,name)))throw new IllegalStateException("MIXIN_CLASS_LOADING_ALREADY_COMMITTED");
        Set<Object> set=(Set<Object>)field(tracker,"invalidClasses");
        synchronized(set){
            TrackerReceipt prior;synchronized(MODULES){prior=MIXIN_RESTRICTIONS.get(new Key(mixin,false));}
            if(after){
                Object entry=trackerEntry(set,name);
                if(prior==null||prior.tracker.get()!=tracker||prior.set.get()!=set||prior.node.get()!=entry||entry==null
                        ||((Map.Entry<?,?>)entry).getKey()!=prior.key.get()||prior.outside||!prior.mixins.contains(new Key(mixin,false))
                        ||prior.mixins.stream().anyMatch(owner->owner.get()!=null&&owner.get()!=mixin))throw new IllegalStateException("MIXIN_CLASS_LOADING_SHARED_OR_UNOBSERVED");
                Object key=prior.key.get();if(!set.remove(key)||trackerEntry(set,key)!=null)throw new IllegalStateException("MIXIN_CLASS_LOADING_REMOVAL_FAILED");
                synchronized(MODULES){prior.mixins.remove(new Key(mixin,false));MIXIN_RESTRICTIONS.remove(new Key(mixin,false));TRACKER_RESTRICTIONS.remove(new Key(entry,false));}
                LoadingChange change=new LoadingChange(()->{synchronized(set){
                    if(trackerEntry(set,key)!=null||!set.add(key))return;
                    Object restored=trackerEntry(set,key);TrackerReceipt receipt=new TrackerReceipt(tracker,set,restored,key,false);receipt.mixins.add(new Key(mixin,false));
                    synchronized(MODULES){TRACKER_RESTRICTIONS.put(new Key(restored,true),receipt);MIXIN_RESTRICTIONS.put(new Key(mixin,true),receipt);}
                }});protect(change);return change;
            }
            Registration scope=registration(mixin,tracker,name);
            try{trackerType.getMethod("registerInvalidClass",String.class).invoke(tracker,name);}
            finally{registrationEnd(scope);}
            TrackerReceipt receipt=scope.receipt;if(receipt==null)throw new IllegalStateException("MIXIN_CLASS_LOADING_REGISTRATION_UNOBSERVED");
            LoadingChange change=new LoadingChange(()->{synchronized(set){synchronized(MODULES){
                if(MIXIN_RESTRICTIONS.get(new Key(mixin,false))!=receipt||receipt==prior)return;
                receipt.mixins.remove(new Key(mixin,false));MIXIN_RESTRICTIONS.remove(new Key(mixin,false));
                Object entry=trackerEntry(set,name);
                if(!receipt.outside&&receipt.mixins.stream().noneMatch(owner->owner.get()!=null)&&entry!=null&&receipt.node.get()==entry&&receipt.key.get()==((Map.Entry<?,?>)entry).getKey()){
                    if(set.remove(receipt.key.get()))TRACKER_RESTRICTIONS.remove(new Key(entry,false));
                }
            }}});protect(change);return change;
        }
    }
    public static void configurationPrepare(Object configuration){
        String method=callerMethod();if(!Set.of("hasMixinsFor","getMixinsFor","getTargetsSet","getTargets","getUnhandledTargets").contains(method))
            throw new SecurityException("ACTUAL_MIXIN_CONFIGURATION_USE_REQUIRED");
        Class<?> type=boundary(method);if(configuration==null||configuration.getClass()!=type||!Boolean.TRUE.equals(field(configuration,"prepared")))return;
        Set<Object> active=PREPARING_CONFIGS.get();if(!active.add(configuration))return;
        try{
            synchronized(configuration){
                Object entries=field(configuration,"mixins");if(!(entries instanceof List<?> mixins))throw new IllegalStateException("MIXIN_CONFIGURATION_LIST_UNAVAILABLE");
                Class<?> mixinType=Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo",false,type.getClassLoader());
                for(Object mixin:mixins.toArray())if(mixin!=null&&mixin.getClass()==mixinType&&field(mixin,"parent")==configuration)prepareMixin(mixinType,mixin);
            }
        }catch(ClassNotFoundException unavailable){throw new IllegalStateException("MIXIN_CONFIGURATION_LAYOUT_UNAVAILABLE",unavailable);}
        finally{active.remove(configuration);if(active.isEmpty())PREPARING_CONFIGS.remove();}
    }
    private static void prepareMixin(Class<?> type,Object mixin){
        synchronized(mixin){
            WeakReference<Object> reference;synchronized(MODULES){reference=MIXIN_TREES.get(new Key(mixin,false));}
            Object tree=reference==null?null:reference.get();if(tree==null)return;Image source=unchangedTree(tree);
            if(source==null||TaskBridge.modStopped(source.module())||Arrays.stream(source.sources()).noneMatch(module->module!=source.module()&&TaskBridge.modStopped(module)))return;
            Object state=field(mixin,"state");if(state==null||field(mixin,"pendingState")!=null||field(state,"classNode")!=tree)return;
            Replayed replay=replayedTree(tree,source,false);Object replacement=replay.tree();if(replacement==tree)return;
            try{
                Class<?> node=Class.forName("org.objectweb.asm.tree.ClassNode",false,type.getClassLoader());
                rebuildPreparedMixin(type,node,mixin,state,replacement,replay.scheduleChanged());
                Object current=field(mixin,"state");if(current==state||field(mixin,"pendingState")!=null||field(current,"classNode")!=replacement)throw new IllegalStateException("ACTUAL_MIXIN_RELOAD_PUBLICATION_UNOBSERVED");
                Image restored=unchangedTree(replacement);if(restored==null)throw new IllegalStateException("ACTUAL_MIXIN_RELOAD_PAYLOAD_CHANGED");
                Module[] sources=merge(restored.sources(),configurationSources(field(mixin,"parent")));protect((Object)sources);
                synchronized(MODULES){MIXINS.put(new Key(mixin,true),new Image(restored.module(),restored.digest(),sources));MIXIN_TREES.put(new Key(mixin,true),new Ref<>(replacement));}
            }catch(ReflectiveOperationException|RuntimeException unavailable){
                // Reload rejection keeps the old source image; the pending contribution remains denied at application.
                call(dependency,tree,source.module(),source.sources());
            }
        }
    }
    /** Rebuild through the installed library's own metadata constructor and normal validation.
     * Existing contexts retain their prior selection and ClassInfo objects. */
    private static void rebuildPreparedMixin(Class<?> type,Class<?> node,Object mixin,Object previous,Object replacement,boolean scheduleChanged)throws ReflectiveOperationException {
        ClassLoader loader=type.getClassLoader();Class<?> stateType=Class.forName(type.getName()+"$State",false,loader);
        Class<?> subtype=Class.forName(type.getName()+"$SubType",false,loader);
        Object oldInfo=field(mixin,"info"),oldType=field(mixin,"type");Class<?> infoType=oldInfo.getClass();
        if(field(previous,"classInfo")!=oldInfo)throw new IllegalStateException("MIXIN_PREPARED_CLASS_INFO_CHANGED");
        var stateConstructor=stateType.getDeclaredConstructor(type,node,infoType);stateConstructor.setAccessible(true);
        Object preview=stateConstructor.newInstance(mixin,replacement,oldInfo),validation=field(preview,"validationClassNode");
        var infoConstructor=infoType.getDeclaredConstructor(node);infoConstructor.setAccessible(true);
        Object incoming=infoConstructor.newInstance(validation),next=stateConstructor.newInstance(mixin,replacement,incoming);
        var cacheField=access(infoType,"cache");Object rawCache=cacheField.get(null);
        if(!(rawCache instanceof Map<?,?>))throw new IllegalStateException("MIXIN_CLASS_INFO_CACHE_UNAVAILABLE");
        @SuppressWarnings("unchecked") Map<Object,Object> cache=(Map<Object,Object>)rawCache;
        Object key=field(oldInfo,"name");if(!Objects.equals(key,field(incoming,"name")))throw new IllegalStateException("MIXIN_CLASS_IDENTITY_CHANGED");
        java.lang.reflect.Field infoField=access(type,"info"),typeField=access(type,"type"),pendingField=access(type,"pendingState"),stateField=access(type,"state");
        java.lang.reflect.Field priorityField=access(type,"priority"),virtualField=access(type,"virtual"),declaredField=access(type,"declaredTargets");
        int oldPriority=priorityField.getInt(mixin);boolean oldVirtual=virtualField.getBoolean(mixin);Object oldDeclared=declaredField.get(mixin),nextDeclared=null;
        @SuppressWarnings("unchecked") List<Object> targets=(List<Object>)field(mixin,"targetClasses");
        @SuppressWarnings("unchecked") List<Object> names=(List<Object>)field(mixin,"targetClassNames");
        List<Object> oldTargets=new ArrayList<>(targets),oldNames=new ArrayList<>(names),nextTargets=null,nextNames=null;
        Object nextType=null,targetPreview=null;Integer nextPriority=null;Boolean nextVirtual=null;IndexChanges indexing=null;LoadingChange loading=null;boolean committed=false;
        synchronized(cache){
            if(cache.get(key)!=oldInfo||infoField.get(mixin)!=oldInfo||typeField.get(mixin)!=oldType
                    ||stateField.get(mixin)!=previous||pendingField.get(mixin)!=null)throw new IllegalStateException("MIXIN_PREPARED_ASSOCIATION_CHANGED");
            try{
                infoField.set(mixin,incoming);
                Method choose=subtype.getDeclaredMethod("getTypeFor",type);choose.setAccessible(true);nextType=choose.invoke(null,mixin);
                Method loadable=subtype.getDeclaredMethod("isLoadable");loadable.setAccessible(true);
                boolean oldLoadable=Boolean.TRUE.equals(loadable.invoke(oldType)),nextLoadable=Boolean.TRUE.equals(loadable.invoke(nextType));
                typeField.set(mixin,nextType);cache.put(key,incoming);pendingField.set(mixin,next);
                if(scheduleChanged){
                    Boolean ignorePlugin;synchronized(MODULES){ignorePlugin=MIXIN_OPTIONS.get(new Key(mixin,false));}
                    if(ignorePlugin==null)throw new IllegalStateException("MIXIN_ORIGINAL_PLUGIN_MODE_UNOBSERVED");
                    Method priority=type.getDeclaredMethod("readPriority",node),pseudo=type.getDeclaredMethod("readPseudo",node);
                    priority.setAccessible(true);pseudo.setAccessible(true);nextPriority=(Integer)priority.invoke(mixin,replacement);nextVirtual=(Boolean)pseudo.invoke(mixin,validation);
                    priorityField.setInt(mixin,nextPriority);virtualField.setBoolean(mixin,nextVirtual);
                    Class<?> reloaded=Class.forName(type.getName()+"$Reloaded",false,loader);
                    var reloadConstructor=reloaded.getDeclaredConstructor(type,stateType,node);reloadConstructor.setAccessible(true);
                    targetPreview=reloadConstructor.newInstance(mixin,previous,replacement);pendingField.set(mixin,targetPreview);
                    Method declared=type.getDeclaredMethod("readDeclaredTargets",validation.getClass(),boolean.class);declared.setAccessible(true);
                    nextDeclared=declared.invoke(mixin,field(targetPreview,"validationClassNode"),ignorePlugin);
                    if(!(nextDeclared instanceof List<?> declarations))throw new IllegalStateException("MIXIN_DECLARED_TARGETS_UNAVAILABLE");
                    nextTargets=new ArrayList<>();nextNames=new ArrayList<>();
                    for(Object declaration:declarations){
                        Method resolve=type.getDeclaredMethod("getTargetClass",declaration.getClass());resolve.setAccessible(true);
                        Object target=resolve.invoke(mixin,declaration);if(target!=null){nextTargets.add(target);nextNames.add(target.toString());}
                    }
                    declaredField.set(mixin,nextDeclared);targets.clear();targets.addAll(nextTargets);names.clear();names.addAll(nextNames);pendingField.set(mixin,next);
                }
                Method validate=type.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(mixin);
                if(stateField.get(mixin)!=next||pendingField.get(mixin)!=null||infoField.get(mixin)!=incoming
                        ||typeField.get(mixin)!=nextType||cache.get(key)!=incoming||field(next,"classInfo")!=incoming
                        ||field(next,"classNode")!=replacement)throw new IllegalStateException("MIXIN_METADATA_PUBLICATION_CHANGED");
                if(scheduleChanged)indexing=IndexChanges.publish(mixin,oldTargets,targets,names);
                loading=changeLoading(type,mixin,oldLoadable,nextLoadable);
                committed=true;
            }finally{
                if(!committed){
                    if(loading!=null)loading.rollback();
                    if(indexing!=null)indexing.rollback();
                    if(nextNames!=null&&sameObjects(names,nextNames)){names.clear();names.addAll(oldNames);}
                    if(nextTargets!=null&&sameObjects(targets,nextTargets)){targets.clear();targets.addAll(oldTargets);}
                    if(nextDeclared!=null&&declaredField.get(mixin)==nextDeclared)declaredField.set(mixin,oldDeclared);
                    if(nextVirtual!=null&&virtualField.getBoolean(mixin)==nextVirtual)virtualField.setBoolean(mixin,oldVirtual);
                    if(nextPriority!=null&&priorityField.getInt(mixin)==nextPriority)priorityField.setInt(mixin,oldPriority);
                    if(stateField.get(mixin)==next)stateField.set(mixin,previous);
                    if(pendingField.get(mixin)==next||targetPreview!=null&&pendingField.get(mixin)==targetPreview)pendingField.set(mixin,null);
                    if(typeField.get(mixin)==nextType)typeField.set(mixin,oldType);
                    if(infoField.get(mixin)==incoming)infoField.set(mixin,oldInfo);
                    if(cache.get(key)==incoming)cache.put(key,oldInfo);
                }
            }
        }
    }
    private static boolean sameObjects(List<?> first,List<?> second){
        if(first.size()!=second.size())return false;for(int i=0;i<first.size();i++)if(first.get(i)!=second.get(i))return false;return true;
    }
    private static final class IndexChanges {
        private final List<Runnable> undo=new ArrayList<>();
        @SuppressWarnings("unchecked") static IndexChanges publish(Object mixin,List<Object> before,List<Object> after,List<Object> names){
            IndexChanges changes=new IndexChanges();Object parent=field(mixin,"parent");
            Map<String,List<Object>> mapping=(Map<String,List<Object>>)field(parent,"mixinMapping");Set<String> unhandled=(Set<String>)field(parent,"unhandledTargets");
            Set<String> wanted=new HashSet<>();for(Object name:names)wanted.add(((String)name).replace('/','.'));
            synchronized(parent){try{
                for(var entry:new ArrayList<>(mapping.entrySet()))if(!wanted.contains(entry.getKey())){
                    String key=entry.getKey();List<Object> values=entry.getValue();boolean removed=false;
                    for(int i=values.size()-1;i>=0;i--)if(values.get(i)==mixin){
                        int position=i;values.remove(i);removed=true;changes.undo.add(()->{if(mapping.get(key)==values&&values.stream().noneMatch(value->value==mixin))values.add(Math.min(position,values.size()),mixin);});
                    }
                    if(removed&&values.isEmpty()&&mapping.get(key)==values){
                        mapping.remove(key);changes.undo.add(()->{if(!mapping.containsKey(key))mapping.put(key,values);});
                        if(unhandled.remove(key))changes.undo.add(()->unhandled.add(key));
                    }
                }
                for(String key:wanted){
                    List<Object> values=mapping.get(key);
                    if(values==null){values=new ArrayList<>();mapping.put(key,values);List<Object> created=values;changes.undo.add(()->{if(mapping.get(key)==created&&created.isEmpty())mapping.remove(key);});}
                    if(values.stream().noneMatch(value->value==mixin)){
                        values.add(mixin);List<Object> actual=values;changes.undo.add(()->{if(mapping.get(key)==actual)actual.removeIf(value->value==mixin);});
                        if(unhandled.add(key))changes.undo.add(()->unhandled.remove(key));
                    }
                }
                Set<Object> next=Collections.newSetFromMap(new IdentityHashMap<>());next.addAll(after);
                for(Object target:before)if(!next.contains(target)){
                    Set<Object> associated=(Set<Object>)field(target,"mixins");if(associated.remove(mixin))changes.undo.add(()->associated.add(mixin));
                }
                for(Object target:next){
                    Set<Object> associated=(Set<Object>)field(target,"mixins");if(associated.add(mixin))changes.undo.add(()->associated.remove(mixin));
                }
                return changes;
            }catch(RuntimeException failure){changes.rollback();throw failure;}}
        }
        void rollback(){for(int i=undo.size()-1;i>=0;i--)undo.get(i).run();undo.clear();}
    }
    public static void classInfoCreated(Object info,Object tree){
        Class<?> type=boundary("<init>");if(info==null||info.getClass()!=type)throw new SecurityException("ACTUAL_CLASS_INFO_CONSTRUCTOR_REQUIRED");
        Image source=unchangedTree(tree);if(source==null)return;
        Module[] sources=source.sources().clone();protect((Object)sources);
        @SuppressWarnings("unchecked") Map<String,Module[]> raw=(Map<String,Module[]>)call(metadataImages,tree,source.module(),sources);
        Map<String,Image> rows=new LedgerMap<>();for(var entry:raw.entrySet()){Module[] owners=entry.getValue().clone();protect((Object)owners);rows.put(entry.getKey(),new Image(source.module(),source.digest(),owners));}rows=Map.copyOf(rows);protect(rows);
        synchronized(MODULES){
            METADATA.put(new Key(info,true),new Image(source.module(),source.digest(),sources));METADATA_TREES.put(new Key(info,true),new Ref<>(tree));METADATA_ROWS.put(new Key(info,true),rows);
            for(String set:List.of("methods","initialisers","fields"))for(Object member:(Set<?>)field(info,set)){
                String key=(set.equals("fields")?"F":"M")+field(member,"memberName")+'\u0000'+field(member,"memberDesc");
                Image image=rows.getOrDefault(key,rows.get("*"));if(image!=null)MEMBER_IMAGES.put(new Key(member,true),image);
            }
        }
    }
    public static Object metadataContextBegin(Object context){
        Class<?> type=boundary(callerMethod());if(context==null||context.getClass()!=type)throw new SecurityException("ACTUAL_MIXIN_CONTEXT_REQUIRED");
        Image source;synchronized(MODULES){source=CONTEXTS.get(new Key(context,false));}
        if(source!=null&&Arrays.stream(source.sources()).anyMatch(TaskBridge::modStopped))throw new IllegalStateException("MIXIN_PRIOR_CONTEXT_SOURCE_STOPPED");
        MetadataUse scope=new MetadataUse(context,null,null,METADATA_USE.get());protect(scope);METADATA_USE.set(scope);return scope;
    }
    public static Object metadataMixinBegin(Object mixin){
        Class<?> type=boundary(callerMethod());if(mixin==null||mixin.getClass()!=type)throw new SecurityException("ACTUAL_MIXIN_STATE_USE_REQUIRED");
        Object pending=field(mixin,"pendingState"),state=pending==null?field(mixin,"state"):pending;
        MetadataUse scope=new MetadataUse(null,mixin,state,METADATA_USE.get());protect(scope);METADATA_USE.set(scope);return scope;
    }
    public static void metadataContextEnd(Object token){
        boundary(callerMethod());if(!(token instanceof MetadataUse scope)||!controlled(scope)||METADATA_USE.get()!=scope)throw new SecurityException("ACTUAL_MIXIN_METADATA_SCOPE_REQUIRED");
        if(scope.previous()==null)METADATA_USE.remove();else METADATA_USE.set(scope.previous());
    }
    public static void metadataMemberCopied(Object member,Object original){
        Class<?> type=boundary("<init>");if(!type.isInstance(member))throw new SecurityException("ACTUAL_MIXIN_MEMBER_COPY_REQUIRED");
        synchronized(MODULES){Image source=MEMBER_IMAGES.get(new Key(original,false));if(source!=null)MEMBER_IMAGES.put(new Key(member,true),source);}
    }
    public static void metadataMemberCreated(Object member,Object info,Object node){
        Class<?> type=boundary("<init>");if(!type.isInstance(member)||field(member,"this$0")!=info)throw new SecurityException("ACTUAL_MIXIN_MEMBER_CONSTRUCTOR_REQUIRED");
        Object[] actual=(Object[])call(memberImage,node);Image source=actual==null?null:new Image((Module)actual[0],new byte[0],((Module[])actual[1]).clone());
        MetadataUse scope=METADATA_USE.get();Image context=null;
        if(scope!=null){
            Object mixin=scope.context()==null?scope.mixin():field(scope.context(),"mixin");synchronized(MODULES){context=scope.context()==null?null:CONTEXTS.get(new Key(scope.context(),false));if(context==null&&mixin!=null)context=MIXINS.get(new Key(mixin,false));}
        }
        if(source==null){if(context==null)return;source=context;}
        Module[] writers=TaskBridge.invokingSources();if(context!=null)writers=merge(writers,context.sources());
        Module[] sources=merge(source.sources(),writers);protect((Object)sources);Image created=new Image(source.module(),source.digest(),sources);
        synchronized(MODULES){MEMBER_IMAGES.put(new Key(member,true),created);}
    }
    public static void metadataHeaderChanged(Object info){
        boundary("addInterface");Module[] writers=TaskBridge.invokingSources();MetadataUse scope=METADATA_USE.get();
        synchronized(MODULES){
            Map<String,Image> rows=METADATA_ROWS.get(new Key(info,false));if(rows==null)return;Image original=rows.getOrDefault("H",rows.get("*"));if(original==null)return;
            if(scope!=null){Image context=scope.context()==null?MIXINS.get(new Key(scope.mixin(),false)):CONTEXTS.get(new Key(scope.context(),false));if(context!=null)writers=merge(writers,context.sources());}
            Module[] sources=merge(original.sources(),writers);protect((Object)sources);Map<String,Image> next=new LedgerMap<>(rows);next.put("H",new Image(original.module(),original.digest(),sources));next=Map.copyOf(next);protect(next);METADATA_ROWS.put(new Key(info,true),next);
        }
    }
    public static void metadataMemberRead(Object info,String name,String descriptor,Object kind,Object member){
        boundary("findMember");MetadataUse scope=METADATA_USE.get();if(scope==null)return;
        String key=(String.valueOf(kind).equals("FIELD")?"F":"M")+name+'\u0000'+descriptor;Image source;
        synchronized(MODULES){
            Map<String,Image> rows=METADATA_ROWS.get(new Key(info,false));source=member==null?null:MEMBER_IMAGES.get(new Key(member,false));
            if(source==null&&rows!=null)source=rows.getOrDefault(key,rows.get("*"));
        }
        metadataConsumed(scope,source,info);
    }
    public static void metadataHeaderRead(Object info){
        boundary(callerMethod());MetadataUse scope=METADATA_USE.get();if(scope==null)return;Image source;
        synchronized(MODULES){Map<String,Image> rows=METADATA_ROWS.get(new Key(info,false));source=rows==null?null:rows.getOrDefault("H",rows.get("*"));}
        metadataConsumed(scope,source,info);
    }
    public static void metadataMemberUsed(Object member){
        boundary(callerMethod());MetadataUse scope=METADATA_USE.get();if(scope==null)return;Image source;
        synchronized(MODULES){source=MEMBER_IMAGES.get(new Key(member,false));}metadataConsumed(scope,source,null);
    }
    public static Object metadataMemberChangeBegin(Object member){
        Class<?> type=boundary(callerMethod());if(!type.isInstance(member))throw new SecurityException("ACTUAL_MIXIN_MEMBER_MUTATION_REQUIRED");Image original;
        synchronized(MODULES){original=MEMBER_IMAGES.get(new Key(member,false));}if(original==null)return null;
        Module[] sources=TaskBridge.invokingSources();MetadataUse scope=METADATA_USE.get();
        if(scope!=null){
            Object mixin=scope.context()==null?scope.mixin():field(scope.context(),"mixin");Image context;
            synchronized(MODULES){context=scope.context()==null?null:CONTEXTS.get(new Key(scope.context(),false));if(context==null&&mixin!=null)context=MIXINS.get(new Key(mixin,false));}
            if(context!=null)sources=merge(sources,context.sources());
        }
        Object[] values=memberValues(member,type.getClassLoader());MemberChange change=new MemberChange(member,values,original,sources);protect(change,values,sources);return change;
    }
    public static void metadataMemberChangeEnd(Object member,Object token){
        Class<?> type=boundary(callerMethod());if(token==null)return;
        if(!(token instanceof MemberChange change)||!controlled(change)||change.member()!=member||!type.isInstance(member))throw new SecurityException("ACTUAL_MIXIN_MEMBER_MUTATION_CAPTURE_REQUIRED");
        Object[] currentValues=memberValues(member,type.getClassLoader());if(Arrays.equals(change.values(),currentValues))return;
        synchronized(MODULES){
            Image current=MEMBER_IMAGES.get(new Key(member,false));if(current==null)current=change.original();Module[] sources=merge(current.sources(),change.sources());protect((Object)sources);
            Image changed=new Image(current.module(),current.digest(),sources);MEMBER_IMAGES.put(new Key(member,true),changed);
            try{
                Class<?> method=Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo$Method",false,type.getClassLoader());
                Class<?> declaration=method.isInstance(member)?method:Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo$Field",false,type.getClassLoader());
                var outer=declaration.getDeclaredField("this$0");outer.setAccessible(true);Object info=outer.get(member);Map<String,Image> rows=METADATA_ROWS.get(new Key(info,false));
                if(rows!=null){
                    String kind=method.isInstance(member)?"M":"F";Map<String,Image> next=new LedgerMap<>(rows);
                    for(Object[] values:List.of(change.values(),currentValues)){
                        String key=kind+values[0]+'\u0000'+values[1];Image previous=rows.getOrDefault(key,rows.get("*"));
                        Module[] owners=previous==null?sources:merge(previous.sources(),sources);protect((Object)owners);next.put(key,new Image(changed.module(),changed.digest(),owners));
                    }
                    next=Map.copyOf(next);protect(next);METADATA_ROWS.put(new Key(info,true),next);
                }
            }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_MIXIN_MEMBER_DECLARATION_UNAVAILABLE",unavailable);}
        }
    }
    private static Object[] memberValues(Object member,ClassLoader loader){
        try{
            Class<?> base=Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo$Member",false,loader);List<Object> values=new ArrayList<>();
            for(String name:List.of("currentName","currentDesc","decoratedFinal","decoratedMutable","unique")){var slot=base.getDeclaredField(name);slot.setAccessible(true);values.add(slot.get(member));}
            Class<?> method=Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo$Method",false,loader);
            if(method.isInstance(member)){var conformed=method.getDeclaredField("conformed");conformed.setAccessible(true);values.add(conformed.get(member));}return values.toArray();
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_MIXIN_MEMBER_STATE_UNAVAILABLE",unavailable);}
    }
    private static void metadataConsumed(MetadataUse scope,Image source,Object info){
        if(source==null)return;Module[] sources;Object consumer=scope.context()==null?scope.state():scope.context();if(consumer==null)return;
        Map<Key,Module[]> reads=scope.context()==null?STATE_READS:CONTEXT_READS;
        synchronized(MODULES){
            Module[] previous=reads.get(new Key(consumer,false));sources=merge(previous==null?new Module[0]:previous,source.sources());protect((Object)sources);reads.put(new Key(consumer,true),sources);
            Image context=scope.context()==null?null:CONTEXTS.get(new Key(scope.context(),false));if(context!=null){Module[] merged=merge(context.sources(),sources);protect((Object)merged);CONTEXTS.put(new Key(scope.context(),true),new Image(context.module(),context.digest(),merged));}
        }
        if(Arrays.stream(source.sources()).anyMatch(TaskBridge::modStopped)){
            WeakReference<Object> raw;synchronized(MODULES){raw=info==null?null:METADATA_TREES.get(new Key(info,false));}
            Object tree=raw==null?null:raw.get();if(tree!=null)call(dependency,tree,source.module(),source.sources());
            throw new IllegalStateException("MIXIN_SELECTED_METADATA_SOURCE_STOPPED");
        }
    }
    public static void targetSelection(Object target,Object mixins){
        Class<?> type=boundary("<init>");if(target==null||target.getClass()!=type||field(target,"mixins")!=mixins||!(mixins instanceof SortedSet<?>))
            throw new SecurityException("ACTUAL_MIXIN_SELECTION_REQUIRED");
        Map<Key,Image> selected=new LedgerMap<>();
        for(Object mixin:(SortedSet<?>)mixins){
            Image source;WeakReference<Object> raw;synchronized(MODULES){source=MIXINS.get(new Key(mixin,false));raw=MIXIN_TREES.get(new Key(mixin,false));}
            if(source==null)continue;Object tree=raw==null?null:raw.get();
            Module[] contributors=tree==null?source.sources():(Module[])call(selection,tree,source.module(),source.sources());
            Module[] sources=merge(contributors,configurationSources(field(mixin,"parent")));protect((Object)sources);
            selected.put(new Key(mixin,false),new Image(source.module(),source.digest(),sources));
        }
        protect(selected);synchronized(MODULES){SELECTIONS.put(new Key(target,true),selected);}
    }
    public static void treeCopied(Object tree,Object visitor){
        boundary("accept");if(copying())return;
        call(copied,tree,visitor);Image source=unchangedTree(tree);if(source==null)return;
        try{
            Class<?> writer=Class.forName("org.objectweb.asm.ClassWriter",false,tree.getClass().getClassLoader());
            if(writer.isInstance(visitor)){synchronized(MODULES){WRITERS.put(new Key(visitor,true),source);}return;}
            Class<?> node=Class.forName("org.objectweb.asm.tree.ClassNode",false,tree.getClass().getClassLoader());
            if(node.isInstance(visitor)){byte[] digest=hash(image(visitor));synchronized(MODULES){TREES.put(new Key(visitor,true),source);TREE_IMAGES.put(new Key(visitor,true),digest);}}
        }catch(ClassNotFoundException failure){throw new IllegalStateException("ASM_COPY_ROLE_UNAVAILABLE",failure);}
    }
    public static byte[] writerBytes(Object writer,byte[] bytes){
        boundary("toByteArray");if(copying())return bytes;
        Module[] contributors=(Module[])call(encoded,writer,bytes);Image source;synchronized(MODULES){reap();source=WRITERS.get(new Key(writer,false));BYTE_CONTRIBUTORS.put(new Key(bytes,true),contributors);}
        protect((Object)contributors);
        if(source!=null)synchronized(MODULES){IMAGES.put(new Key(bytes,true),source(source.module(),bytes,merge(source.sources(),contributors)));}return bytes;
    }
    private static Image source(Module module,byte[] bytes,Module[] owners){Module[] copy=owners.clone();protect((Object)copy);return new Image(module,hash(bytes),copy);}
    private static Module[] merge(Module[] first,Module[] second){Set<Module> owners=Collections.newSetFromMap(new IdentityHashMap<>());owners.addAll(Arrays.asList(first));owners.addAll(Arrays.asList(second));return owners.toArray(Module[]::new);}
    public static byte[] pipelineBytes(Object loader,byte[] input,byte[] output){
        boundary("maybeTransformClassBytes");if(!(loader instanceof ClassLoader actual))throw new SecurityException("ACTUAL_CLASS_LOADER_REQUIRED");
        Image original,transformed;synchronized(MODULES){original=IMAGES.get(new Key(input,false));transformed=IMAGES.get(new Key(output,false));}
        if(original==null||original.module().getClassLoader()!=actual||!MessageDigest.isEqual(original.digest(),hash(input)))return output;
        Module[] contributors;synchronized(MODULES){contributors=BYTE_CONTRIBUTORS.get(new Key(output,false));}
        Module[] sources=transformed==null?original.sources():merge(original.sources(),transformed.sources());if(contributors!=null)sources=merge(sources,contributors);
        synchronized(MODULES){IMAGES.put(new Key(output,true),source(original.module(),output,sources));}return output;
    }
    public static void mixinContext(Object context,Object mixin,Object tree){
        boundary("<init>");Image expected,actual;synchronized(MODULES){expected=MIXINS.get(new Key(mixin,false));actual=TREES.get(new Key(tree,false));}
        if(expected!=null&&actual!=null&&expected.module()==actual.module()){
            Module[] sources=merge(expected.sources(),actual.sources());
            Object target=field(context,"targetClass");
            synchronized(MODULES){
                Map<Key,Image> selected=SELECTIONS.get(new Key(target,false));Image selection=selected==null?null:selected.get(new Key(mixin,false));
                if(selection!=null)sources=merge(sources,selection.sources());
                Object info=field(mixin,"info");Image metadata=info==null?null:METADATA.get(new Key(info,false));
                if(metadata!=null)sources=merge(sources,metadata.sources());
                Module[] reads=CONTEXT_READS.get(new Key(context,false));if(reads!=null)sources=merge(sources,reads);
                Object state=field(mixin,"state");Module[] prepared=state==null?null:STATE_READS.get(new Key(state,false));if(prepared!=null)sources=merge(sources,prepared);
            }
            protect((Object)sources);
            synchronized(MODULES){CONTEXTS.put(new Key(context,true),new Image(expected.module(),actual.digest(),sources));}
        }
    }
    public static boolean mixinAllowed(Object context){boundary("applyMixin");Image source;synchronized(MODULES){source=CONTEXTS.get(new Key(context,false));}return source==null||Arrays.stream(source.sources()).noneMatch(TaskBridge::modStopped);}
    public static Object mixinBegin(Object tree,Object context){
        boundary("applyMixin");Image source;synchronized(MODULES){reap();source=CONTEXTS.get(new Key(context,false));}
        if(source!=null)call(dependency,tree,source.module(),source.sources());
        Image author;synchronized(MODULES){author=MIXINS.get(new Key(field(context,"mixin"),false));}
        return capture(tree,source==null?new Module[0]:source.sources(),author==null?new Module[0]:new Module[]{author.module()},context);
    }
    public static Object pluginBegin(Object mixin,Object tree){
        Class<?> boundary=boundary(callerMethod());Object handle=field(mixin,"plugin"),plugin=field(handle,"plugin");
        Module owner=plugin==null?null:DefinitionBridge.module(plugin.getClass());
        return capture(tree,merge(configurationSources(field(mixin,"parent")),owner==null?new Module[0]:new Module[]{owner}),owner==null?new Module[0]:new Module[]{owner},null);
    }
    public static Object transformerBegin(Object holder,Object tree){
        boundary("transform");Holder source;synchronized(MODULES){source=HOLDERS.get(new Key(holder,false));}
        Object implementation=source==null?null:source.implementation().get();Module[] writers=implementation==null?new Module[0]:TaskBridge.objectSources(implementation);
        if(writers.length==0&&implementation!=null)writers=new Module[]{DefinitionBridge.module(implementation.getClass())};
        return capture(tree,source==null?new Module[0]:source.sources(),writers,null);
    }
    private static Object capture(Object tree,Module[] sources,Module[] writers,Object context){
        Image original;synchronized(MODULES){original=TREES.get(new Key(tree,false));}
        Module[] actual=sources.clone(),authors=writers.clone();Ref<Object> consumer=new Ref<>(context);CodeCapture record=new CodeCapture(call(begin,tree,actual,authors),original,actual,consumer);protect(record,actual,authors,consumer);return record;
    }
    public static void transformerContext(Object holder,Object implementation,Object service){
        boundary("<init>");
        Set<Module> owners=Collections.newSetFromMap(new IdentityHashMap<>());
        if(implementation!=null)owners.addAll(Arrays.asList(TaskBridge.objectSources(implementation)));
        if(service!=null)owners.addAll(Arrays.asList(TaskBridge.objectSources(service)));
        owners.addAll(Arrays.asList(TaskBridge.invokingSources()));
        Holder record=new Holder(new Ref<>(implementation),new Ref<>(service),owners.toArray(Module[]::new));
        protect(record.implementation(),record.service(),record.sources());
        synchronized(MODULES){HOLDERS.put(new Key(holder,true),record);}
    }
    public static boolean transformerAllowed(Object holder){
        String method=callerMethod();if(!Set.of("transform","castVote").contains(method))throw new SecurityException("ACTUAL_TRANSFORMER_DISPATCH_REQUIRED");boundary(method);
        Holder record;synchronized(MODULES){record=HOLDERS.get(new Key(holder,false));}
        return record==null||record.implementation().get()==field(holder,"wrapped")&&record.service().get()==field(holder,"owner")&&Arrays.stream(record.sources()).noneMatch(TaskBridge::modStopped);
    }
    public static void codeEnd(Object token,Object tree){
        String method=callerMethod();if(!Set.of("applyMixin","preApply","postApply","transform").contains(method))throw new SecurityException("ACTUAL_EXTERNAL_APPLY_REQUIRED");
        boundary(method);if(!(token instanceof CodeCapture record)||!controlled(record))throw new SecurityException("ACTUAL_EXTERNAL_CAPTURE_REQUIRED");
        Module[] used=record.sources();Object context=record.context().get();
        if(context!=null)synchronized(MODULES){Module[] reads=CONTEXT_READS.get(new Key(context,false));if(reads!=null)used=merge(used,reads);}
        call(end,record.token(),tree,used);Image source;synchronized(MODULES){source=TREES.get(new Key(tree,false));}
        if(source==null)source=record.original();if(source==null)return;
        byte[] digest=hash(image(tree));
        Module[] sources=merge(source.sources(),used);protect((Object)sources);
        synchronized(MODULES){
            Image current=new Image(source.module(),digest,sources);TREES.put(new Key(tree,true),current);TREE_IMAGES.put(new Key(tree,true),digest);
            for(var entry:METADATA_TREES.entrySet())if(entry.getValue().get()==tree){
                Image previous=METADATA.get(entry.getKey());if(previous!=null){Module[] merged=merge(previous.sources(),record.sources());protect((Object)merged);METADATA.put(entry.getKey(),new Image(previous.module(),digest,merged));}
            }
        }
    }
    public static void codeControls(Object[] objects){requireAgent();protectControls(objects);}
    public static Object[] imageControlFields(Object holder,java.lang.reflect.Field[] fields){
        requireAgent();Object[] values=new Object[fields.length];
        for(int i=0;i<fields.length;i++){
            var field=fields[i];Class<?> owner=field.getDeclaringClass();
            if(owner.getClassLoader()!=null||!owner.getName().startsWith("jdk.internal.org.objectweb.asm.")
                    ||java.lang.reflect.Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive()||!owner.isInstance(holder))
                throw new IllegalArgumentException("ACTUAL_ASM_REFERENCE_FIELD_REQUIRED");
            // Read our metadata through the existing exact native field reader.
            // A reflective Unsafe getter would observe this traversal itself.
            values[i]=controlField(field,holder);
        }
        return values;
    }
    static Object controlField(java.lang.reflect.Field field,Object holder){
        Class<?> caller=AUTHORITY.getCallerClass();
        if(caller!=CodeSourceBridge.class&&caller!=TaskBridge.class)throw new SecurityException("ACTUAL_CONTROL_FIELD_READER_REQUIRED");
        if(NativeControl.available()){
            Object receiver=java.lang.reflect.Modifier.isStatic(field.getModifiers())?field.getDeclaringClass():holder;
            Object[] read=NativeControl.heapReadField(receiver,field.getDeclaringClass(),field.getName(),field.getType().descriptorString());
            if(read==null||read.length!=1)throw new IllegalStateException("CONTROL_FIELD_UNAVAILABLE:"+field.getName());
            return read[0];
        }
        try{return field.get(holder);}
        catch(IllegalAccessException failure){throw new IllegalStateException("CONTROL_FIELD_UNAVAILABLE:"+field.getName(),failure);}
    }
    static void resourceLayoutControls(Object method){
        if(AUTHORITY.getCallerClass()!=ResourceBridge.class)throw new SecurityException("CONTROL_REGISTRATION_OWNER");
        protectOne(method);
    }
    static void agentControls(Object[] objects){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("CONTROL_REGISTRATION_OWNER");
        protectControls(objects);
    }
    private static void protectControls(Object[] objects){
        // Capture the supplied root references before discovering backings;
        // registration must not re-read a caller's array after that traversal.
        Object[] roots=objects.clone();
        Set<Object> found=null;ArrayDeque<Object> pending=null;
        // Ordinary roots, including arrays, are leaves in this closure. Only
        // containers need the work queue and the per-call identity visited set.
        for(Object object:roots)if(object instanceof Map<?,?>||object instanceof Collection<?>){
            if(pending==null){found=Collections.newSetFromMap(new IdentityHashMap<>());pending=new ArrayDeque<>();}
            pending.add(object);
        }
        if(pending!=null)while(!pending.isEmpty()){
            Object value=pending.removeFirst();if(!found.add(value)||!(value instanceof Map<?,?>||value instanceof Collection<?>))continue;
            for(Class<?> type=value.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(var field:type.getDeclaredFields())
                if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(field.getType().isArray()||Map.class.isAssignableFrom(field.getType())||Collection.class.isAssignableFrom(field.getType()))){
                    if(!field.trySetAccessible())throw new IllegalStateException("CONTROL_BACKING_FIELD_UNAVAILABLE:"+field.getName());
                    Object child=controlField(field,value);if(child!=null)pending.add(child);
                }
        }
        // Keep registration after successful discovery. The registry already
        // de-duplicates leaf identities; no expanded flattened result array is
        // needed, and every actual discovered backing is still registered.
        synchronized(CONTROLS){
            reapControls();
            for(Object object:roots)if(object!=null&&!(object instanceof Map<?,?>||object instanceof Collection<?>))CONTROLS.add(object);
            if(found!=null)for(Object value:found)CONTROLS.add(value);
        }
    }
    static void controlBacking(Object parent,Object next){
        if(AUTHORITY.getCallerClass()!=TaskBridge.class)throw new SecurityException("CONTROL_BACKING_OWNER");
        if(parent!=CONTROLS){
            // protectControls treats an array as a leaf; its elements are not
            // control objects. Publish that same exact backing without making
            // a temporary identity set, queue and result array for one leaf.
            if(next!=null&&next.getClass().isArray())protectOne(next);
            else protectControls(new Object[]{next});
        }
    }
    static boolean controlled(Object object){
        if(object==null)return false;
        synchronized(CONTROLS){
            if(object==CONTROLS)return true;
            // Only this exact component type can be the registry's backing.
            // Other arrays still undergo the ordinary control-identity lookup.
            if(object instanceof ControlKey[])try{
                Object backing=NativeControl.available()?NativeControl.controlTable():CONTROLS.table;
                if(backing==object)return true;
            }
            catch(Throwable unavailable){throw new IllegalStateException("CONTROL_TABLE_UNAVAILABLE",unavailable);}
            return CONTROLS.contains(object);
        }
    }
    private static String callerMethod(){return WALKER.walk(frames->frames.filter(frame->frame.getDeclaringClass().getNestHost()!=CodeSourceBridge.class).findFirst().orElseThrow()).getMethodName();}
    private static Object field(Object receiver,String name){
        if(receiver==null)return null;
        for(Class<?> type=receiver.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(receiver);}catch(NoSuchFieldException ignored){}catch(IllegalAccessException failure){throw new IllegalStateException("EXTERNAL_ROLE_FIELD_UNAVAILABLE:"+name,failure);}
        throw new IllegalStateException("EXTERNAL_ROLE_FIELD_UNAVAILABLE:"+name);
    }
    private static java.lang.reflect.Field access(Class<?> type,String name)throws NoSuchFieldException {
        for(Class<?> current=type;current!=null;current=current.getSuperclass())try{var field=current.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(type.getName()+":"+name);
    }
    private static byte[] hash(byte[] bytes){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);protect((Object)digest);return digest;}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){MODULES.remove(key);IMAGES.remove(key);BYTE_CONTRIBUTORS.remove(key);READERS.remove(key);TREES.remove(key);TREE_IMAGES.remove(key);MIXINS.remove(key);MIXIN_TREES.remove(key);MIXIN_OPTIONS.remove(key);MIXIN_RESTRICTIONS.remove(key);TRACKER_RESTRICTIONS.remove(key);METADATA.remove(key);METADATA_TREES.remove(key);METADATA_ROWS.remove(key);MEMBER_IMAGES.remove(key);CONTEXT_READS.remove(key);STATE_READS.remove(key);SELECTIONS.remove(key);RESOURCE_SOURCES.remove(key);CONFIGURATIONS.remove(key);CONTEXTS.remove(key);HOLDERS.remove(key);WRITERS.remove(key);FIELD_HOLDERS.remove(key);}}
}
