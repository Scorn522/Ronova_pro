package dev.ronova.pro.bootstrap;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
/** Win64 JVMTI binding wrappers and JNI field/effect boundaries. */
public final class NativeControl {
    private static volatile boolean available;
    private static volatile boolean libraryBoundaryReady;
    private static volatile boolean libraryBoundaryPresent;
    private static volatile String state="NOT_ATTEMPTED";
    private static final Object INSTALL_LOCK=new Object();
    private static volatile String installMode="NOT_ATTEMPTED";
    private static volatile boolean installRetryable;
    // Set by initialize0 only for an allocation failure before controller prep.
    private static volatile boolean nativeHandoffRetryable;
    private static boolean installing,nativeLoadAttempted,nativeLoaded,nativeBoundaryInstalled;
    private static java.lang.reflect.Method nativeImageSelector,nativeImageFinalizer;
    private static Class<?> definitionDirectoryOwner;
    private NativeControl() {}
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static boolean controlCaller() {
        Class<?> caller=CALLER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type!=NativeControl.class).findFirst().orElse(null));
        if(ControlBridge.owns(caller))return true;
        try {return caller==Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());}
        catch(ClassNotFoundException missing){return false;}
    }
    public static String install(Path bootstrapJar) {
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        synchronized(INSTALL_LOCK){
            if(installing||available||!installMode.equals("NOT_ATTEMPTED")&&!installRetryable)return state;
            installing=true;installRetryable=false;installMode="INSTALLING";
        }
        try{return installAttempt(bootstrapJar);}
        finally{synchronized(INSTALL_LOCK){installing=false;}}
    }
    private static String installAttempt(Path bootstrapJar){
        try{
            if(nativeImageSelector==null||nativeImageFinalizer==null){
                Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
                definitionDirectoryOwner=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions",false,agent.getClassLoader());
                nativeImageSelector=agent.getMethod("nativeImageCandidate",Module.class,ClassLoader.class,String.class,Class.class,Module[].class);
                nativeImageFinalizer=agent.getMethod("nativeFinalImage",Module.class,ClassLoader.class,String.class,Class.class,ProtectionDomain.class,byte[].class,byte[].class,Module[].class,boolean.class);
            }
            if(!System.getProperty("os.name","").startsWith("Windows")||!System.getProperty("os.arch","").equals("amd64"))
                return installFailure("OBJECT_CLONE_JNI_UNSUPPORTED_PLATFORM",false);
            if(!nativeLoaded){
                Path dll=nativeImage(bootstrapJar);
                if(dll==null)return installFailure("OBJECT_CLONE_JNI_PAYLOAD_ABSENT",false);
                nativeLoadAttempted=true;
                System.load(dll.toString());nativeLoaded=true;
                if(abi()!=94)throw new UnsatisfiedLinkError("NATIVE_CONTROL_ABI");
            }
            if(!nativeBoundaryInstalled){
                nativeHandoffRetryable=false;
                if(!initialize0(ControlBridge.class,DefinitionBridge.class))
                    return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:ACTUAL_NATIVE_BOUNDARY_INSTALL_FAILED",nativeHandoffRetryable);
                nativeBoundaryInstalled=true;
            }
            // This stage publishes one immutable CAS directory. Its native
            // implementation cleans an unsuccessful record and accepts the
            // already-published exact owner, so only this stage is resumed.
            if(!initializeDefinitionDirectory0(definitionDirectoryOwner))
                return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:ACTUAL_DEFINITION_DIRECTORY_CAS_PENDING",true);
            libraryBoundaryPresent=libraryPresent0();state="NATIVE_BIND_AND_JNI_FIELD_READY";
            installMode="FULL";available=true;return state;
        }catch(ClassNotFoundException|NoSuchMethodException failure){
            return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:"+failure.getClass().getSimpleName()+":"+failure.getMessage(),false);
        }catch(IOException failure){
            return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:"+failure.getClass().getSimpleName()+":"+failure.getMessage(),
                    !nativeLoadAttempted&&!(failure instanceof java.util.zip.ZipException));
        }catch(RuntimeException|LinkageError failure){
            return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:"+failure.getClass().getSimpleName()+":"+failure.getMessage(),false);
        }catch(OutOfMemoryError failure){
            return installFailure("OBJECT_CLONE_JNI_UNAVAILABLE:OutOfMemoryError",!nativeLoadAttempted||nativeBoundaryInstalled||nativeHandoffRetryable);
        }
    }
    private static String installFailure(String reason,boolean retryable){
        available=false;state=reason;installMode=nativeLoadAttempted?"DEGRADED":"FAILED";installRetryable=retryable;return state;
    }
    private static Path nativeImage(Path bootstrapJar)throws IOException{
        try(java.util.jar.JarFile payload=new java.util.jar.JarFile(bootstrapJar.toFile())) {
            var entry=payload.getJarEntry("ronova-native/windows-x86_64/ronova-pro-control.dll");
            if(entry==null)return null;
            byte[] bytes;try(InputStream input=payload.getInputStream(entry)) { bytes=input.readAllBytes(); }
            String digest=hash(bytes);
            Path directory=Path.of(System.getProperty("user.dir","."),"ronova-pro","native",digest).toAbsolutePath();Files.createDirectories(directory);
            Path dll=directory.resolve("ronova-pro-control.dll");
            if(!Files.isRegularFile(dll)||!hash(Files.readAllBytes(dll)).equals(digest)) {
                Path temporary=Files.createTempFile(directory,"control-",".tmp");
                try { Files.write(temporary,bytes);Files.move(temporary,dll,StandardCopyOption.REPLACE_EXISTING); }
                finally { Files.deleteIfExists(temporary); }
            }
            String early=System.getProperty("ronova.pro.control.native","");
            if(!early.isEmpty()) {
                Path actual=Path.of(early).toAbsolutePath();if(!hash(Files.readAllBytes(actual)).equals(digest))throw new UnsatisfiedLinkError("EARLY_NATIVE_IMAGE_MISMATCH");dll=actual;
            }
            return dll;
        }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean available() { return available; }
    public static String installMode(){return installMode;}
    public static boolean installRetryable(){return installRetryable;}
    static java.util.UUID returnIdentifier(){
        if(CALLER.getCallerClass()!=DefinitionBridge.class)throw new SecurityException("DEFINITION_RETURN_FACTORY_REQUIRED");
        if(!available)throw new IllegalStateException("NATIVE_RETURN_IDENTIFIER_UNAVAILABLE:"+state);
        return returnIdentifier0();
    }
    static java.util.concurrent.CompletableFuture<?> returnFuture(){
        if(CALLER.getCallerClass()!=DefinitionBridge.class)throw new SecurityException("DEFINITION_RETURN_FACTORY_REQUIRED");
        if(!available)throw new IllegalStateException("NATIVE_RETURN_FUTURE_UNAVAILABLE:"+state);
        return returnFuture0();
    }
    /** Revoke only a current native heap callback's saved target permission. */
    static void heapPolicyChanged(Object object){
        if(!available)return;
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=ControlBridge.class&&caller!=TaskBridge.class&&caller!=DefinitionBridge.class
                &&!CodeSourceBridge.heapPolicyWriter(caller))
            throw new SecurityException("ACTUAL_HEAP_POLICY_PUBLICATION_REQUIRED");
        heapPolicyChanged0(object);
    }
    static Class<?> rawMemoryWriterCaller(){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_RAW_MEMORY_WRITER_QUERY_REQUIRED");
        return rawMemoryWriter0();
    }
    static Class<?> controlWriterCaller(){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_CONTROL_WRITER_READER_REQUIRED");
        return controlWriter0();
    }
    private static Class<?> definitionDirectoryCaller(){
        Class<?> caller=CALLER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type->type!=NativeControl.class).findFirst().orElse(null));
        return caller!=null&&caller==definitionDirectoryOwner?caller:null;
    }
    public static boolean compareDefinitionReference(java.util.concurrent.atomic.AtomicReference<?> slot,Object expected,Object next){
        if(definitionDirectoryCaller()==null)throw new SecurityException("ACTUAL_DEFINITION_DIRECTORY_WRITER_REQUIRED");
        if(available)return compareDefinitionReference0(slot,expected,next);
        // The unsupported backend retains its ordinary Java publication path.
        @SuppressWarnings("unchecked") var reference=(java.util.concurrent.atomic.AtomicReference<Object>)slot;
        return reference.compareAndSet(expected,next);
    }
    static Object controlTable(){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CONTROL_TABLE_READER_REQUIRED");
        if(!available)throw new IllegalStateException("ACTUAL_CONTROL_TABLE_NATIVE_UNAVAILABLE");
        return controlTable0();
    }
    public static boolean codeLayout(ClassLoader loader,String name,byte[] bytes,String[] methods,Module[][][] owners){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_LAYOUT_AGENT_REQUIRED");
        return available&&codeLayout0(loader,name,bytes,methods,owners,false,null);
    }
    public static boolean codeLayout(ClassLoader loader,String name,byte[] bytes,String[] methods,Module[][][] owners,boolean hidden){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_LAYOUT_AGENT_REQUIRED");
        return available&&codeLayout0(loader,name,bytes,methods,owners,hidden,null);
    }
    public static boolean codeLayout(ClassLoader loader,String name,byte[] bytes,String[] methods,Module[][][] owners,boolean hidden,Object execution){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_LAYOUT_AGENT_REQUIRED");
        return available&&codeLayout0(loader,name,bytes,methods,owners,hidden,execution);
    }
    public static boolean codeDefinition(Class<?> actual,byte[] bytes){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_DEFINITION_AGENT_REQUIRED");
        return available&&codeDefinition0(actual,bytes);
    }
    public static String[] codeVersion(Class<?> actual,byte[] bytes){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_VERSION_AGENT_REQUIRED");
        return available?codeVersion0(actual,bytes):null;
    }
    public static String[] codeMethodVersion(Class<?> actual,byte[] bytes,String method){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_VERSION_AGENT_REQUIRED");
        if(method==null)throw new NullPointerException("method");
        return available?codeMethodVersion0(actual,bytes,method):null;
    }
    public static String[] codeDeclarations(Class<?> actual){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_DECLARATIONS_AGENT_REQUIRED");
        return available?codeDeclarations0(actual):null;
    }
    public static Class<?>[] bootstrapClasses(){
        if(!controlCaller())throw new SecurityException("ACTUAL_BOOTSTRAP_CLASS_QUERY_REQUIRED");
        if(!available)throw new IllegalStateException("ACTUAL_BOOTSTRAP_CLASS_QUERY_UNAVAILABLE");
        return loadedClasses0(true);
    }
    public static Class<?>[] bootstrapLookupClasses(){
        if(!controlCaller())throw new SecurityException("ACTUAL_BOOTSTRAP_CLASS_QUERY_REQUIRED");
        if(!available)throw new IllegalStateException("ACTUAL_BOOTSTRAP_CLASS_QUERY_UNAVAILABLE");
        return bootstrapLookupClasses0();
    }
    /** Exact late bootstrap classes whose returned installation image awaits a real prepare event. */
    public static boolean platformPrepareTargets(String[] names){
        if(!controlCaller())throw new SecurityException("ACTUAL_PLATFORM_PREPARATION_AGENT_REQUIRED");
        return available&&names!=null&&platformPrepareTargets0(names);
    }
    public static boolean platformQueueQuiescent(Class<?> pool,Class<?> queue){
        if(CALLER.getCallerClass()!=TaskBridge.class)throw new SecurityException("ACTUAL_PLATFORM_QUEUE_READER_REQUIRED");
        return available&&pool!=null&&queue!=null&&platformQueueQuiescent0(pool,queue);
    }
    public static Class<?>[] loadedClasses(){
        if(!controlCaller())throw new SecurityException("ACTUAL_LOADED_CLASS_QUERY_REQUIRED");
        // A Java-only installation retains its original Instrumentation query.
        return available?loadedClasses0(false):null;
    }
    static Module[] codeFrame(StackWalker.StackFrame frame){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");
        return available?codeFrame0(frame.getDeclaringClass(),frame.getMethodName(),frame.getDescriptor(),frame.getByteCodeIndex()):new Module[0];
    }
    // Enter native code before creating a StackWalker result or a Java guard value:
    // either allocation can itself invoke the instrumented Object constructor.
    static boolean executionRootAllowed(){return available&&executionRootAllowed0();}
    private static boolean executionRootCaller(){
        return CALLER.walk(frames->{
            var entries=frames.dropWhile(frame->frame.getDeclaringClass()==NativeControl.class).iterator();
            if(!entries.hasNext())return false;StackWalker.StackFrame entry=entries.next();
            if(entry.getDeclaringClass()!=CodeSourceBridge.class||!entry.getMethodName().equals("executionRootEnter"))return false;
            if(!entries.hasNext())return false;StackWalker.StackFrame root=entries.next();
            if(root.getDeclaringClass()!=Object.class||!root.getMethodName().equals("<init>"))return false;
            while(entries.hasNext()){
                Class<?> actual=entries.next().getDeclaringClass().getNestHost();
                if(actual==CodeSourceBridge.class||actual==ExecutionFlow.class)return false;
            }
            return true;
        });
    }
    static Object executionPlan(StackWalker.StackFrame frame){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");
        return available?executionPlan0(frame.getDeclaringClass(),frame.getMethodName(),frame.getDescriptor(),frame.getByteCodeIndex()):null;
    }
    static boolean executionWatch(StackWalker.StackFrame frame,Object plan,Object token){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");
        return available&&executionWatch0(frame.getDeclaringClass(),frame.getMethodName(),frame.getDescriptor(),frame.getByteCodeIndex(),plan,token);
    }
    static long executionSite(Object token){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");
        return available&&token!=null?executionSite0(token):0;
    }
    static long executionFrameSite(Object token){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FRAME_BRIDGE_REQUIRED");
        return available&&token!=null?executionFrameSite0(token):0;
    }
    private static boolean executionPopped(Object token,boolean normal){return CodeSourceBridge.executionPopped(token,normal);}
    private static Object[] codeRetirementFrame(Class<?> declaring,String selector,int location,Object plan,Module owner){
        return CodeSourceBridge.executionRetirementFrame(declaring,selector,location,plan,owner);
    }
    static boolean codeRetirementFramePermit(Class<?> declaring,int location,Object plan,Module owner){
        return CALLER.getCallerClass()==CodeSourceBridge.class&&available&&codeRetirementFramePermit0(declaring,location,plan,owner);
    }
    static int executionPopPermit(Object token){return CALLER.getCallerClass()==CodeSourceBridge.class&&available?executionPopPermit0(token):0;}
    static Class<?> codeFieldOwner(Class<?> symbolic,String name,String descriptor){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FIELD_BRIDGE_REQUIRED");
        return available?codeFieldOwner0(symbolic,name,descriptor):null;
    }
    static boolean heapWatch(Object holder,Class<?> declaring,String name,String descriptor){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FIELD_BRIDGE_REQUIRED");
        return available&&heapWatch0(holder,declaring,name,descriptor);
    }
    static Object[] heapReadField(Object holder,Class<?> declaring,String name,String descriptor){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_FIELD_BRIDGE_REQUIRED");
        return available?heapReadField0(holder,declaring,name,descriptor):null;
    }
    static byte[] codeDigest(byte[] bytes){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_IMAGE_BRIDGE_REQUIRED");
        if(available){
            byte[] digest=codeDigest0(bytes);
            if(digest==null||digest.length!=32)throw new IllegalStateException("ACTUAL_CODE_IMAGE_DIGEST_UNAVAILABLE");
            return digest;
        }
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static byte[] agentCodeDigest(byte[] bytes){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_IMAGE_AGENT_REQUIRED");
        if(available){
            byte[] digest=codeDigest0(bytes);
            if(digest==null||digest.length!=32)throw new IllegalStateException("ACTUAL_CODE_IMAGE_DIGEST_UNAVAILABLE");
            return digest;
        }
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static byte[] heapArrayImage(Object array,long start,int length){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_ARRAY_BRIDGE_REQUIRED");
        return available?heapArrayImage0(array,start,length):null;
    }
    static final class HeapImageUnavailable extends RuntimeException {
        private HeapImageUnavailable(String reason){super(reason,null,false,false);}
    }
    static long heapArrayBits(Object array,long start,int length){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_CODE_ARRAY_BRIDGE_REQUIRED");
        if(!available)throw new HeapImageUnavailable("HEAP_ARRAY_IMAGE_UNAVAILABLE");
        return heapArrayBits0(array,start,length);
    }
    private static void heapModified(Object holder,Class<?> declaring,String name,String descriptor,Class<?> writer,String method,String methodDescriptor,int instruction,Object plan,Module[] contributors){
        CodeSourceBridge.executionFieldModified(holder,declaring,name,descriptor,writer,method,methodDescriptor,instruction,plan,contributors);
    }
    static boolean heapEventPermit(Object holder,Class<?> declaring){return CALLER.getCallerClass()==CodeSourceBridge.class&&available&&heapEventPermit0(holder,declaring);}
    private static void codePrepared(Class<?> actual){CodeSourceBridge.classPrepared(actual);}
    static boolean preparedClass(Class<?> actual){return CALLER.getCallerClass()==CodeSourceBridge.class&&available&&preparedClass0(actual);}
    static boolean sourceCapture(){return CALLER.getCallerClass()==ControlBridge.class&&sourceCapture0();}
    static boolean sourceQueryStopped(){
        if(CALLER.getCallerClass()!=ControlBridge.class||!ControlBridge.sourceQueryActive())throw new SecurityException("ACTUAL_SOURCE_QUERY_REQUIRED");
        if(!available)throw new IllegalStateException("SOURCE_QUERY_STACK_UNAVAILABLE");
        return sourceQueryStopped0();
    }
    private static Module[] sourceQueryFrame(Class<?> declaring,String name,String descriptor,int location,int depth){
        if(!ControlBridge.sourceQueryActive())throw new SecurityException("ACTUAL_SOURCE_QUERY_FRAME_REQUIRED");
        return ExecutionFlow.sourceQueryFrame(declaring,name,descriptor,location,depth);
    }
    static int networkBoundaryDepth(Class<?> boundary,String method){
        if(CALLER.getCallerClass()!=NetworkBridge.class)throw new SecurityException("ACTUAL_NETWORK_BOUNDARY_QUERY_REQUIRED");
        return available&&boundary!=null&&method!=null?networkBoundaryDepth0(boundary,method):0;
    }
    static boolean networkCleanupBoundary(Class<?> boundary,String method,int phase,int bottomDepth){
        if(CALLER.getCallerClass()!=NetworkCleanup.class)throw new SecurityException("ACTUAL_NETWORK_CLEANUP_QUERY_REQUIRED");
        return available&&boundary!=null&&method!=null&&bottomDepth>0&&networkCleanupBoundary0(boundary,method,phase,bottomDepth);
    }
    static Object networkReadField(Object receiver,Class<?> declaring,String name,boolean statik){
        if(CALLER.getCallerClass()!=NetworkCleanup.class)throw new SecurityException("ACTUAL_NETWORK_FIELD_READER_REQUIRED");
        return available&&declaring!=null&&name!=null?networkReadField0(receiver,declaring,name,statik):null;
    }
    static java.lang.reflect.Field networkField(Class<?> declaring,String name){
        if(CALLER.getCallerClass()!=NetworkBridge.class)throw new SecurityException("ACTUAL_NETWORK_FIELD_QUERY_REQUIRED");
        return available&&declaring!=null&&name!=null?networkField0(declaring,name):null;
    }
    static Object[][] carrierFields(Object receiver){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierFields0(receiver):null;
    }
    static Object[] carrierRead(Object receiver,java.lang.reflect.Field field){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierRead0(receiver,field):null;
    }
    static Class<?> carrierMethod(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierMethod0(receiver,symbolic,name,descriptor,opcode,caller):null;
    }
    static long[] carrierMethodImage(Class<?> declaring,String name,String descriptor){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierMethodImage0(declaring,name,descriptor):null;
    }
    static long[] carrierFrame(Class<?> declaring,String name,String descriptor,int bottomDepth){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierFrame0(declaring,name,descriptor,bottomDepth):null;
    }
    static long[] carrierCallSite(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,String callerMethod,String callerDescriptor,int bottomDepth){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierCallSite0(receiver,symbolic,name,descriptor,opcode,caller,callerMethod,callerDescriptor,bottomDepth):null;
    }
    static Object[] carrierStaticValue(Class<?> declaring,String name,String descriptor){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierStaticValue0(declaring,name,descriptor):null;
    }
    static int carrierCallbackDepth(Class<?> declaring,String name,String descriptor){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available?carrierCallbackDepth0(declaring,name,descriptor):0;
    }
    static boolean carrierCallback(Object callback,Class<?> callee,String name,String descriptor,int calleeBottomDepth,
                                   Class<?> boundary,String boundaryMethod,String boundaryDescriptor,int boundaryBottomDepth){
        if(CALLER.getCallerClass()!=CarrierBridge.class)throw new SecurityException("ACTUAL_CARRIER_BRIDGE_REQUIRED");
        return available&&carrierCallback0(callback,callee,name,descriptor,calleeBottomDepth,boundary,boundaryMethod,boundaryDescriptor,boundaryBottomDepth);
    }
    private static boolean carrierReader(){return ControlBridge.coreWriter();}
    private static boolean carrierAdapter(Object plan,String selector,int location,int opcode,String owner,String name,String descriptor){
        return ExecutionFlow.callbackAdapter(plan,selector,location,opcode,owner,name,descriptor);
    }
    private static boolean carrierInstruction(Object plan,String selector,int location,int instruction,int opcode,int local,String owner,String name,String descriptor){
        return ExecutionFlow.carrierInstruction(plan,selector,location,instruction,opcode,local,owner,name,descriptor);
    }
    static boolean arrayCopyInternalBoundary(){return CALLER.getCallerClass()==ControlBridge.class&&arrayCopyInternalBoundary0();}
    static boolean arrayCopyReadBoundary(Object source,int from,int length){return CALLER.getCallerClass()==ControlBridge.class&&arrayCopyReadBoundary0(source,from,length);}
    static boolean arrayCopyBoundary(Object source,int from,int length,Object target,int to,int prefix,Object read){return CALLER.getCallerClass()==ControlBridge.class&&arrayCopyBoundary0(source,from,length,target,to,prefix,read);}
    static boolean fileOperationBoundary(Object receiver,Class<?> declaring,String method,Module[] sources,int operation,long address,long length){
        return CALLER.getCallerClass()==ResourceBridge.class&&fileOperationBoundary0(receiver,declaring,method,sources,operation,address,length);
    }
    static boolean fileOperationFinishing(Object token){return CALLER.getCallerClass()==ResourceBridge.class&&fileOperationFinishing0(token);}
    static boolean mappingDuplicateFrame(long[] identity,int phase){
        if(CALLER.getCallerClass()!=ResourceBridge.class)throw new SecurityException("ACTUAL_MAPPING_DUPLICATE_QUERY_REQUIRED");
        return available&&identity!=null&&identity.length==5&&phase>=0&&phase<=2&&mappingDuplicateFrame0(identity,phase);
    }
    static boolean ioOperationBoundary(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources){
        return CALLER.getCallerClass()==ResourceBridge.class&&ioOperationBoundary0(declaring,method,arguments,references,sources);
    }
    static boolean bufferAllocationBoundary(){return CALLER.getCallerClass()==ResourceBridge.class&&bufferAllocationBoundary0();}
    static boolean bufferAddressBoundary(Object buffer,Module[] sources){return CALLER.getCallerClass()==ResourceBridge.class&&bufferAddressBoundary0(buffer,sources);}
    static long[] bindBufferStorage(java.nio.Buffer buffer,Object action,long address,long bytes){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.bufferBindingActive(buffer,action,address,bytes))throw new SecurityException("ACTUAL_BUFFER_STORAGE_BINDING_REQUIRED");
        return available?bindBufferStorage0(buffer,action,address,bytes):new long[]{0,0};
    }
    private static boolean bufferBindingCaller(java.nio.Buffer buffer,Object action,long address,long bytes){return ResourceBridge.bufferBindingActive(buffer,action,address,bytes);}
    static long[] observeBufferStorage(java.nio.Buffer buffer,Object action,long address,long bytes){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.bufferStorageActive(buffer,action,address,bytes))throw new SecurityException("ACTUAL_BUFFER_STORAGE_OBSERVATION_REQUIRED");
        return available?observeBufferStorage0(buffer,action,address,bytes):new long[]{0,0};
    }
    private static boolean bufferStorageCaller(java.nio.Buffer buffer,Object action,long address,long bytes){return ResourceBridge.bufferStorageActive(buffer,action,address,bytes);}
    static boolean bufferStorageUse(java.nio.Buffer buffer,long token,long generation,long address,long bytes,boolean retain){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.bufferStorageUseActive(buffer,token,generation,address,bytes,retain))throw new SecurityException("ACTUAL_BUFFER_STORAGE_USE_REQUIRED");
        return available&&bufferStorageUse0(buffer,token,generation,address,bytes,retain);
    }
    private static boolean bufferStorageUseCaller(java.nio.Buffer buffer,long token,long generation,long address,long bytes,boolean retain){
        return ResourceBridge.bufferStorageUseActive(buffer,token,generation,address,bytes,retain);
    }
    static void exposeBufferStorage(java.nio.Buffer buffer,long token,long generation,long address,long bytes,Module[] sources){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.bufferStorageExposureActive(buffer,token,generation,address,bytes,sources))throw new SecurityException("ACTUAL_BUFFER_STORAGE_EXPOSURE_REQUIRED");
        if(available)exposeBufferStorage0(buffer,token,generation,address,bytes,sources);
    }
    private static boolean bufferStorageExposureCaller(java.nio.Buffer buffer,long token,long generation,long address,long bytes,Module[] sources){
        return ResourceBridge.bufferStorageExposureActive(buffer,token,generation,address,bytes,sources);
    }
    static long[] restoreBufferStorage(Object action,long token,long generation,Module[] modules,long cursor,int budget){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.bufferRestoreActive(action,token,generation,modules,cursor,budget))
            throw new SecurityException("ACTUAL_BUFFER_SOURCE_RESTORE_REQUIRED");
        if(!available)return new long[]{0,1,0,1};
        return restoreBufferStorage0(action,token,generation,modules,cursor,budget);
    }
    private static boolean bufferRestoreCaller(Object action,long token,long generation,Module[] modules,long cursor,int budget){
        return ResourceBridge.bufferRestoreActive(action,token,generation,modules,cursor,budget);
    }
    static long ioBufferWrite(Object receipt,Object action,long allocation,long generation,long address,long bytes,Module[] sources){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.ioBufferWriteActive(receipt,action,allocation,generation,address,bytes,sources))throw new SecurityException("ACTUAL_IO_BUFFER_WRITE_REQUIRED");
        return available?ioBufferWrite0(receipt,action,allocation,generation,address,bytes,sources):0;
    }
    private static boolean ioBufferWriteCaller(Object receipt,Object action,long allocation,long generation,long address,long bytes,Module[] sources){
        return ResourceBridge.ioBufferWriteActive(receipt,action,allocation,generation,address,bytes,sources);
    }
    private static Object ioBufferWriteGroup(Object receipt){return ResourceBridge.ioBufferWriteGroup(receipt);}
    static void ioBufferWritten(Object receipt,long token,long bytes,boolean completed){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.ioBufferWrittenActive(receipt,token,bytes,completed))throw new SecurityException("ACTUAL_IO_BUFFER_WRITE_COMPLETION_REQUIRED");
        ioBufferWritten0(receipt,token,bytes,completed);
    }
    private static boolean ioBufferWrittenCaller(Object receipt,long token,long bytes,boolean completed){return ResourceBridge.ioBufferWrittenActive(receipt,token,bytes,completed);}
    static long[] ioReadVector(Object operation,long address,int count,boolean windows){
        if(CALLER.getCallerClass()!=IoBridge.class||!IoBridge.ioVectorActive(operation,address,count,windows))throw new SecurityException("ACTUAL_IO_VECTOR_REQUIRED");
        if(!available)throw new IllegalStateException("NATIVE_IO_VECTOR_UNAVAILABLE");return ioReadVector0(operation,address,count,windows);
    }
    private static boolean ioReadVectorCaller(Object operation,long address,int count,boolean windows){return IoBridge.ioVectorActive(operation,address,count,windows);}
    public static String fileBoundaryInstalled(){
        if(!controlCaller())throw new SecurityException("NATIVE_FILE_INSTALL_AGENT_REQUIRED");
        if(!available)return "NATIVE_FILE_BINDINGS_UNAVAILABLE";
        long[] bindings=fileBindingState0();
        if(bindings==null||bindings.length!=4)return "NATIVE_FILE_BINDINGS_UNAVAILABLE";
        String state=":BOUND="+bindings[0]+":ARMED_VM_ENTRY="+bindings[1]+":PENDING="+bindings[2]+":TOTAL="+bindings[3];
        return (bindings[2]==0?"NATIVE_FILE_BOUNDARY_READY":"NATIVE_FILE_CURRENT_BINDINGS_PENDING")+state;
    }
    static boolean threadOperationBoundary(Thread thread,Module[] sources,int operation){
        return CALLER.getCallerClass()==ControlBridge.class&&available&&threadOperationBoundary0(thread,sources,operation);
    }
    static boolean threadOperationFinishing(Object token,boolean completed){
        return CALLER.getCallerClass()==ControlBridge.class&&available&&threadOperationFinishing0(token,completed);
    }
    public static String threadBoundaryInstalled(){
        if(!controlCaller())throw new SecurityException("NATIVE_THREAD_INSTALL_AGENT_REQUIRED");
        if(!available)return "NATIVE_THREAD_BINDINGS_UNAVAILABLE";
        int remaining=threadBindings0();
        return remaining==0?"NATIVE_THREAD_CURRENT_BINDINGS_READY":"NATIVE_THREAD_CURRENT_BINDINGS_PENDING:"+remaining;
    }
    public static String codeRetirementGap(Module module){
        if(!controlCaller())throw new SecurityException("ACTUAL_CODE_STATE_AGENT_REQUIRED");
        if(available)retireCode0(module);
        String creation=ControlBridge.creationSourceGap(module);if(!creation.isEmpty())return creation;
        String heap=CodeSourceBridge.executionRetirementGap(module);if(!heap.isEmpty())return heap;
        if(!available)return "EXTERNAL_CODE_VM_BACKEND_UNAVAILABLE";long[] frames=codeState0(module);
        long[] memory=memoryState0(module);
        if(memory==null||memory.length<5)return "EXTERNAL_NATIVE_MEMORY_BACKEND_UNAVAILABLE";
        if(memory[0]!=0||memory[1]!=0||memory[2]!=0||memory[3]!=0)return "EXTERNAL_NATIVE_MEMORY_PENDING:allocations="+memory[0]+":ranges="+memory[1]+":active="+memory[2]+":unmapped="+memory[3]+":sharedReads="+memory[4];
        long[] heapState=heapState0();if(heapState==null||heapState.length<2||heapState[0]==0)return "EXTERNAL_HEAP_EVENT_BACKEND_UNAVAILABLE";
        if(heapState[1]!=0)return "EXTERNAL_HEAP_EVENTS_UNOBSERVED:events="+heapState[1];
        if(frames==null||frames.length<5||frames[0]==0)return "EXTERNAL_CODE_VM_BYTECODES_UNAVAILABLE";
        if(frames.length<5||frames[3]==0)return "EXTERNAL_EXECUTION_POP_BACKEND_UNAVAILABLE";
        if(frames[4]!=0)return "EXTERNAL_EXECUTION_POP_UNOBSERVED:events="+frames[4];
        if(frames.length<19)return "EXTERNAL_CODE_RETIREMENT_BACKEND_UNAVAILABLE";
        if(frames[16]!=0)return "EXTERNAL_CODE_OWN_SUSPENSION_PENDING:threads="+frames[16];
        boolean active=frames[1]!=0||frames[2]!=0||frames[9]!=0||frames[18]!=0;
        if(frames[15]!=0&&(active||frames[10]!=frames[11]))return "EXTERNAL_CODE_RETIREMENT_VM_FAILURES:operations="+frames[15];
        if(active&&(frames[5]==0||frames[6]==0||frames[7]==0))
            return "EXTERNAL_CODE_RETIREMENT_CAPABILITIES_UNAVAILABLE:suspend="+frames[5]+":earlyReturn="+frames[6]+":step="+frames[7]+":onLoad="+frames[8];
        return active?"EXTERNAL_CODE_FRAMES_PENDING:active="+frames[1]+":unmapped="+frames[2]+":requests="+frames[9]
                +":cleanup="+frames[12]+":framework="+frames[13]+":nativeScopes="+frames[14]+":sourceScopes="+frames[17]
                +":returnsRequested="+frames[10]+":popped="+frames[11]+":awaitingPop="+frames[18]:"";
    }
    private static Class<?> platformType(String name){
        try{return Class.forName(name,false,null);}catch(ClassNotFoundException unavailable){throw new ExceptionInInitializerError(unavailable);}
    }
    private static final Class<?> LIBRARIES=platformType("jdk.internal.loader.NativeLibraries");
    private static final Class<?> LIBRARY=platformType("jdk.internal.loader.NativeLibraries$NativeLibraryImpl");
    private static final Class<?> UNLOADER=platformType("jdk.internal.loader.NativeLibraries$Unloader");
    private static final Class<?> CLEANER=platformType("jdk.internal.ref.CleanerImpl");
    private static final Class<?> CLEANABLE=platformType("jdk.internal.ref.PhantomCleanable");
    private static final Class<?> INNOCUOUS=platformType("jdk.internal.misc.InnocuousThread");
    static boolean libraryMetadataType(Class<?> type){return type==LIBRARIES||type==LIBRARY||type==UNLOADER;}
    static boolean libraryMetadataValue(Object value){return value instanceof Class<?> type?libraryMetadataType(type):value!=null&&libraryMetadataType(value.getClass());}
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean> LIBRARY_IMPLEMENTATION_QUERY=new LibraryImplementationQuery();
    private static final class LibraryImplementationQuery implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Boolean>{
        public Boolean apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();boolean proxy=false;
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();String name=type.getName();
                if(DefinitionBridge.module(type)!=type.getModule())return false;
                if(type.getNestHost()==NativeControl.class||type.getNestHost()==TaskBridge.class||type.getNestHost()==ControlBridge.class||type.getNestHost()==ResourceBridge.class
                        ||type.getNestHost()==SourceMapBridge.class||type.getNestHost()==BackingBridge.class)continue;
                if(proxy&&java.lang.reflect.Proxy.isProxyClass(type)){proxy=false;continue;}
                if(type.getClassLoader()==null&&(name.startsWith("java.util.")||name.startsWith("java.lang.invoke.")
                        ||name.startsWith("jdk.internal.reflect.")||type==java.lang.reflect.Method.class||type==java.lang.reflect.Field.class)){
                    if(name.equals("java.lang.invoke.MethodHandleProxies$1")&&frame.getMethodName().equals("invoke"))proxy=true;
                    continue;
                }
                return type==LIBRARIES||type==LIBRARY||type==UNLOADER;
            }
            return false;
        }
    }
    static boolean libraryImplementationCaller(){return LIBRARY_CALLER.walk(LIBRARY_IMPLEMENTATION_QUERY);}
    public static void libraryBoundaryInstalled(){
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        try{
            var context=LIBRARIES.getDeclaredField("nativeLibraryContext");context.setAccessible(true);
            var names=LIBRARIES.getDeclaredField("loadedLibraryNames");names.setAccessible(true);
            ControlBridge.controlPublication(LIBRARIES,new Object[]{context.get(null),names.get(null)});
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("NATIVE_LIBRARY_REGISTRY_UNAVAILABLE",failure);}
        libraryBoundaryReady=available&&libraryBoundaryPresent&&libraryActivate0();
    }
    private static final StackWalker LIBRARY_CALLER=StackWalker.getInstance(java.util.Set.of(
            StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static Class<?> libraryCaller(String member,String method){
        return LIBRARY_CALLER.walk(frames->{
            var cursor=frames.iterator();boolean operation=false,proxy=false;
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
                if(type==NativeControl.class)continue;
                if(!operation){
                    if(type!=(member.equals("NativeLibraryImpl")?LIBRARY:UNLOADER)
                            ||!frame.getMethodName().equals(method))throw new SecurityException("ACTUAL_NATIVE_LIBRARY_OPERATION_REQUIRED");
                    operation=true;continue;
                }
                if(DefinitionBridge.module(type)!=type.getModule())return type;
                if(proxy&&java.lang.reflect.Proxy.isProxyClass(type)){proxy=false;continue;}
                if(type.getClassLoader()==null&&(type==System.class||type==Runtime.class||type==ClassLoader.class
                        ||type.getName().equals("jdk.internal.loader.NativeLibraries")
                        ||type.getName().startsWith("jdk.internal.loader.NativeLibraries$")
                        ||type==java.lang.reflect.Method.class||type.getName().startsWith("jdk.internal.reflect.")
                        ||type.getName().startsWith("java.lang.invoke."))){
                    if(type.getName().equals("java.lang.invoke.MethodHandleProxies$1")&&frame.getMethodName().equals("invoke"))proxy=true;
                    continue;
                }
                if(frame.isNativeMethod()){Class<?> actual=activeClass();if(actual!=null)return actual;}
                return type;
            }
            return null;
        });
    }
    public static long libraryBegin(Object library,Class<?> fromClass){
        boolean registered=LIBRARY_CALLER.walk(frames->{
            var cursor=frames.iterator();while(cursor.hasNext()){
                var frame=cursor.next();if(frame.getDeclaringClass()==NativeControl.class)continue;
                if(frame.getDeclaringClass()!=LIBRARY||!frame.getMethodName().equals("open")||!cursor.hasNext())return false;
                frame=cursor.next();return frame.getDeclaringClass()==LIBRARIES&&frame.getMethodName().equals("loadLibrary");
            }
            return false;
        });
        if(!registered)throw new SecurityException("ACTUAL_JDK_NATIVE_LOAD_CALL_REQUIRED");
        Class<?> actor=libraryCaller("NativeLibraryImpl","open");
        return available&&libraryBoundaryPresent?libraryBegin0(library,fromClass,actor):0;
    }
    public static long libraryUnloadBegin(Object unloader,long handle){
        LibraryConsumer consumer=unloadConsumer();
        return available&&libraryBoundaryPresent?libraryUnloadBegin0(unloader,handle,consumer.type(),consumer.automatic(),ControlBridge.recoveryWriter()):0;
    }
    private record LibraryConsumer(Class<?> type,boolean automatic){}
    private static LibraryConsumer unloadConsumer(){
        return LIBRARY_CALLER.walk(frames->{
            var cursor=frames.iterator();boolean operation=false,cleaner=false,proxy=false;
            while(cursor.hasNext()){
                var frame=cursor.next();Class<?> type=frame.getDeclaringClass();
                if(type.getNestHost()==NativeControl.class)continue;
                if(!operation){if(type!=UNLOADER||!frame.getMethodName().equals("run"))throw new SecurityException("ACTUAL_NATIVE_LIBRARY_UNLOAD_REQUIRED");operation=true;continue;}
                if(DefinitionBridge.module(type)!=type.getModule())return new LibraryConsumer(type,false);
                if(type.getNestHost()==CLEANER||type==CLEANABLE){if(type==CLEANER&&frame.getMethodName().equals("run"))cleaner=true;continue;}
                if(type==LIBRARIES||type==LIBRARY||type==Thread.class||type==INNOCUOUS)continue;
                if(proxy&&java.lang.reflect.Proxy.isProxyClass(type)){proxy=false;continue;}
                if(type.getClassLoader()==null&&(type==java.lang.reflect.Method.class||type.getName().startsWith("jdk.internal.reflect.")||type.getName().startsWith("java.lang.invoke."))){
                    if(type.getName().equals("java.lang.invoke.MethodHandleProxies$1")&&frame.getMethodName().equals("invoke"))proxy=true;
                    continue;
                }
                if(frame.isNativeMethod()){Class<?> actual=activeClass();if(actual!=null)type=actual;}
                return new LibraryConsumer(type,false);
            }
            return new LibraryConsumer(null,cleaner);
        });
    }
    public static void libraryUnloader(Object library,Object unloader,long handle){
        libraryCaller("NativeLibraryImpl","unloader");if(available&&libraryBoundaryPresent)libraryUnloader0(library,unloader,handle);
    }
    public static void libraryEnd(long token,long handle,boolean success){
        var frame=LIBRARY_CALLER.walk(frames->frames.filter(f->f.getDeclaringClass()!=NativeControl.class).findFirst().orElse(null));
        if(frame==null||frame.getDeclaringClass().getClassLoader()!=null
                ||!(frame.getDeclaringClass()==LIBRARY&&frame.getMethodName().equals("open")
                ||frame.getDeclaringClass()==UNLOADER&&frame.getMethodName().equals("run")))
            throw new SecurityException("ACTUAL_NATIVE_LIBRARY_OPERATION_REQUIRED");
        if(available&&token!=0)libraryEnd0(token,handle,success);
    }
    private static boolean libraryBoundaryCaller(String operation){
        return LIBRARY_CALLER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()){
                var frame=cursor.next();
                if(frame.getDeclaringClass()==NativeControl.class&&frame.getMethodName().equals("libraryBoundaryCaller"))continue;
                if(frame.getDeclaringClass()!=NativeControl.class||!frame.getMethodName().equals(operation+"0"))return false;
                if(!cursor.hasNext())return false;frame=cursor.next();
                if(frame.getDeclaringClass()!=NativeControl.class||!frame.getMethodName().equals(operation)||!cursor.hasNext())return false;
                frame=cursor.next();Class<?> actual=frame.getDeclaringClass();
                if(actual.getClassLoader()!=null||DefinitionBridge.module(actual)!=Object.class.getModule())return false;
                return switch(operation){
                    case "libraryBegin"->actual==LIBRARY&&frame.getMethodName().equals("open");
                    case "libraryUnloadBegin"->actual==UNLOADER&&frame.getMethodName().equals("run");
                    case "libraryUnloader"->actual==LIBRARY&&frame.getMethodName().equals("unloader");
                    case "libraryEnd"->actual==LIBRARY&&frame.getMethodName().equals("open")||actual==UNLOADER&&frame.getMethodName().equals("run");
                    default->false;
                };
            }
            return false;
        });
    }
    private static boolean libraryLoadCaller(){
        return LIBRARY_CALLER.walk(frames->{
            var cursor=frames.iterator();
            while(cursor.hasNext()){
                var frame=cursor.next();if(frame.getDeclaringClass()==NativeControl.class&&frame.getMethodName().equals("libraryLoadCaller"))continue;
                if(frame.getDeclaringClass()!=LIBRARIES||!frame.getMethodName().equals("load")||!frame.isNativeMethod()||!cursor.hasNext())return false;
                frame=cursor.next();if(frame.getDeclaringClass()!=LIBRARY||!frame.getMethodName().equals("open")||!cursor.hasNext())return false;
                frame=cursor.next();return frame.getDeclaringClass()==LIBRARIES&&frame.getMethodName().equals("loadLibrary");
            }
            return false;
        });
    }
    private static boolean processCaller(String operation){
        var caller=CALLER.walk(frames->frames.filter(frame->frame.getDeclaringClass()!=NativeControl.class).findFirst().orElse(null));
        if(caller==null||caller.getDeclaringClass()!=ResourceBridge.class)return false;
        return switch(operation){
            case "begin"->caller.getMethodName().equals("processCreateBegin");
            case "end"->caller.getMethodName().equals("processCreateEnd");
            case "construct"->caller.getMethodName().equals("processCleanupAction");
            case "expose"->caller.getMethodName().equals("processPipeExposed");
            case "retire"->caller.getMethodName().equals("releaseProcess")&&ControlBridge.recoveryWriter();
            default->false;
        };
    }
    public static void processBoundaryInstalled(){
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        if(available)processPrepare0(platformType("java.lang.ProcessImpl"));
    }
    static long processCreateBegin(long[] handles)throws IOException {
        if(!processCaller("begin"))throw new SecurityException("ACTUAL_PROCESS_CREATE_CALL_REQUIRED");
        return available&&hostReady0()?processCreateBegin0(handles):0;
    }
    static void processCreateEnd(long token,long handle,long[] handles){
        if(!processCaller("end"))throw new SecurityException("ACTUAL_PROCESS_CREATE_RETURN_REQUIRED");
        if(available&&token!=0)processCreateEnd0(token,handle,handles);
    }
    static long processConstructed(Process process,long handle){
        if(!processCaller("construct"))throw new SecurityException("ACTUAL_PROCESS_CONSTRUCTOR_REQUIRED");
        return available&&hostReady0()?processConstructed0(process,handle):0;
    }
    static void processExposed(Process process,long token,Module[] sources){
        if(!processCaller("expose"))throw new SecurityException("ACTUAL_PROCESS_ACCESSOR_REQUIRED");
        if(available&&token!=0)processExposed0(process,token,sources);
    }
    static String retireProcess(Process process,long token){
        if(!processCaller("retire"))throw new SecurityException("PROCESS_RECOVERY_CORE_REQUIRED");
        if(!available||token==0)return "HOST_PROCESS_CREATION_UNOBSERVED";
        String gap=processRetire0(process,token);return gap==null?"HOST_PROCESS_DISPOSITION_UNAVAILABLE":gap;
    }
    public static String state() { return state+":INSTALL_MODE="+installMode+":INSTALL_RETRYABLE="+installRetryable+":NATIVE_LIBRARY_BOUNDARY="+(libraryBoundaryReady?"READY":"UNAVAILABLE")
            +":HOST_PROCESS_BOUNDARY="+(available&&hostReady0()?"JDK_PROCESSIMPL_CREATEPROCESSW_BOUND":"AWAITING_ACTUAL_JDK_PROCESSIMPL_CREATEPROCESSW_BIND")
            +":HOST_NATIVE_CREATE_SCOPE=REGISTERED_EXACT_CREATEPROCESSA_W_NORMAL_OR_RESOLVED_DELAY_IMPORTS_AND_OBSERVED_GETPROCADDRESS_RESULTS_AFTER_OS_LOAD_RETURN"
            +":VERIFIED_RIP_RELATIVE_DELAY_IAT_CALLS_USE_CURRENT_BINDING_DYNAMIC_POINTERS_RETAIN_RESOLUTION_SOURCES"
            +":HOST_JNI_ONLOAD_BOUNDARY="+(available&&hostLibraryLoadReady0()?"EXACT_JDK_OS_LOAD_RETURN_BEFORE_SAME_THREAD_JNI_ONLOAD":"AWAITING_ACTUAL_JDK_LOAD_UNLOAD_IMPORT_BIND")
            +":HOST_CHILD_DLL=ACTUAL_LOADLIBRARY_RETURN_SOURCES_IMPORTS_RESOLVER_BINDINGS_AND_WORKER_LEASES;PUBLIC_WINDOWS_CARRIERS_RETAIN_SHARED_EXECUTION"
            +":HOST_CHILD_PIN=OWN_EXTRA_REFERENCE_ONLY;APPLICATION_LOADER_REFERENCE_OWNERSHIP_AND_FINAL_UNMAP_UNOBSERVED"
            +":DLLMAIN_AND_TLS_CALLBACKS_BEFORE_OS_LOAD_RETURN_UNOBSERVED"
            +":UNRESOLVED_DELAY_IMPORTS_AND_RESOLUTION_OUTSIDE_REGISTERED_GETPROCADDRESS_ENTRIES_UNOBSERVED"
            +":HOST_CREATION=OBSERVED_CREATEPROCESS_A_W_ASUSER_A_W_WITHTOKEN_W_WITHLOGON_W"
            +":HOST_HANDLES=OBSERVED_OPEN_DUPLICATE_CLOSE_AND_INHERITANCE;EXTERNAL_TRANSFERS_AND_SAVED_ORIGINAL_POINTERS_REMAIN_GAPS"
            +":JVMTI=OBSERVED_GETENV_ENVIRONMENT_CALLBACK_AND_DISPOSAL_CONTROL;OBSERVER_FAILURES_PRESERVE_ORIGINAL_RESULTS_AND_RECORD_COVERAGE_GAPS;PREEXISTING_ENVIRONMENTS_AND_SAVED_ORIGINAL_POINTERS_UNOBSERVED"
            +":RAW_NATIVE_MEMORY_AND_LOADER_PRE_RETURN_CONTROL_REQUIRE_SELECTED_LOWER_BACKEND:"+hostKernelSummary(null); }
    private static boolean hostKernelCoreCaller(){
        Class<?> caller=CALLER.walk(frames->frames.map(StackWalker.StackFrame::getDeclaringClass).filter(type->type!=NativeControl.class).findFirst().orElse(null));
        return caller!=null&&caller.getName().equals("dev.ronova.pro.ProRuntime")&&ControlBridge.coreWriter();
    }
    public static String hostKernelProtect(Module module,boolean protect){
        Class<?> caller=CALLER.getCallerClass();
        if(!ControlBridge.coreWriter()||!caller.getName().equals("dev.ronova.pro.ProRuntime"))throw new SecurityException("HOST_KERNEL_ACTUAL_CORE_REQUIRED");
        if(!available)return "HOST_KERNEL_NATIVE_UNAVAILABLE";
        hostKernelProtect0(module,protect);long[] actual=hostKernelState0(module);
        return "HOST_KERNEL_SESSION="+actual[0]+";WIN32="+actual[4]+";NTSTATUS="+actual[5]+";CAPABILITIES="+actual[6]+";UNSERVED="+actual[7]+";POLICY_PENDING="+actual[19]+";SHARED_UNSERVED="+actual[20];
    }
    public static String hostKernelOwnerProtect(boolean protect){
        Class<?> caller=CALLER.getCallerClass();
        if(!ControlBridge.coreWriter()||!caller.getName().equals("dev.ronova.pro.ProRuntime"))throw new SecurityException("HOST_KERNEL_OWNER_ACTUAL_CORE_REQUIRED");
        if(!available)return "HOST_KERNEL_NATIVE_UNAVAILABLE";
        hostKernelOwnerProtect0(protect);return hostKernelOwnerSummary(hostKernelState0(null));
    }
    private static String hostKernelOwnerSummary(long[] actual){
        return "HOST_JVM_OWNER_DESIRED="+actual[21]+";HOST_JVM_OWNER_ACTIVE="+actual[22]+";HOST_JVM_OWNER_TARGET="+actual[23]
                +";HOST_JVM_OWNER_RIGHTS_REDUCED="+actual[24]+";HOST_JVM_OWNER_WIN32="+actual[25]+";HOST_JVM_OWNER_NTSTATUS="+actual[26]
                +";HOST_JVM_OWNER_STATE="+actual[27]+";HOST_JVM_OWNER_OBSERVED_PID="+actual[28]+";HOST_JVM_OWNER_PENDING="+actual[29];
    }
    static String hostKernelFile(FileDescriptor descriptor,Module[] selected){
        if(CALLER.getCallerClass()!=ResourceBridge.class||!ResourceBridge.kernelFileActive(descriptor,selected))throw new SecurityException("HOST_KERNEL_ACTUAL_FILE_SCOPE_REQUIRED");
        return available?hostKernelFile0(descriptor,selected):"HOST_KERNEL_NATIVE_UNAVAILABLE";
    }
    private static boolean hostKernelFileCaller(FileDescriptor descriptor,Module[] selected){return ResourceBridge.kernelFileActive(descriptor,selected);}
    private static String hostKernelSummary(Module module){
        if(!available)return "HOST_KERNEL_NATIVE_UNAVAILABLE";long[] actual=hostKernelState0(module);
        return "HOST_KERNEL_SESSION="+actual[0]+";HOST_KERNEL_WIN32="+actual[4]+";HOST_KERNEL_NTSTATUS="+actual[5]+";HOST_KERNEL_CAPABILITIES="+actual[6]+";HOST_KERNEL_UNSERVED="+actual[7]
                +";HOST_KERNEL_PROCESS_RECEIPTS="+actual[17]+";HOST_KERNEL_FILE_RECEIPTS="+actual[18]+";HOST_KERNEL_POLICY_PENDING="+actual[19]+";HOST_KERNEL_SHARED_UNSERVED="+actual[20]
                +";"+hostKernelOwnerSummary(actual);
    }
    public static void module(Module module,boolean stopped) {
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        if(available){module0(module,stopped);if(stopped)retireCode0(module);}
    }
    /** Invoked by the native wrapper only after the actual VM API returned NONE. */
    private static boolean jvmtiDefinitionsChanged(Class<?>[] actuals){
        if(!jvmtiDefinitionCompletion0(actuals))throw new SecurityException("ACTUAL_JVMTI_DEFINITION_COMPLETION_SCOPE_REQUIRED");
        return ControlBridge.nativeDefinitionsChanged(actuals);
    }
    private static boolean jvmtiImageCandidate(Module module,ClassLoader loader,String name,Class<?> actual,Module[] writers){
        java.lang.reflect.Method selector=nativeImageSelector;if(selector==null)return true;
        try{return Boolean.TRUE.equals(selector.invoke(null,module,loader,name,actual,writers));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("NATIVE_IMAGE_SELECTOR_UNAVAILABLE",failure);}
    }
    private static byte[] jvmtiImage(Module module,ClassLoader loader,String name,Class<?> actual,ProtectionDomain domain,byte[] original,byte[] bytes,Module[] writers,boolean changed){
        if(!jvmtiBoundary0(module,loader,name,actual,domain,original,bytes,writers,changed))throw new SecurityException("ACTUAL_NATIVE_CLASS_FILE_CALLBACK_SCOPE_REQUIRED");
        try{
            java.lang.reflect.Method finalizer=nativeImageFinalizer;
            if(finalizer==null)throw new IllegalStateException("NATIVE_FINAL_IMAGE_UNBOUND");
            return (byte[])finalizer.invoke(null,module,loader,name,actual,domain,original,bytes,writers,changed);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("NATIVE_FINAL_IMAGE_UNAVAILABLE",failure);}
    }
    public static String moduleState(Module module) {
        if(!available)return state();long[] actual=status0(module);
        long[] host=hostState0(module);long[] jvmti=jvmtiState0(module);
        // BIND_FAILURES includes unavailable JNI slot observations. JVMTI_FAILURES
        // includes observer/control failures even when the original API succeeded.
        return "NATIVE_BIND_EVENTS="+actual[0]+";JNI_FIELD_TABLE="+actual[1]+";WRAPPED_BINDINGS="+actual[2]+";ACTIVE_NATIVE="+actual[3]+";BIND_FAILURES="+actual[4]+";NATIVE_POINTERS="+actual[5]+";LOADED_NATIVE_IMAGES="+actual[6]
                +";HOST_PROCESS_FAMILIES="+host[0]+";HOST_SHARED_FAMILIES="+host[1]+";HOST_UNPUBLISHED_FAMILIES="+host[2]+";HOST_HANDLE_ERRORS="+host[3]+";HOST_ACTIVE_PROCESSES="+host[4]
                +";HOST_NATIVE_HANDLE_PUBLICATIONS_UNOBSERVED="+host[5]+";HOST_NATIVE_CREATEPROCESS_IMPORTS="+host[6]+";HOST_NATIVE_CREATEPROCESS_COVERAGE_GAP_IMAGES="+host[7]
                +";HOST_NATIVE_CREATEPROCESS_LOAD_WINDOWS_UNOBSERVED="+host[8]+";HOST_NATIVE_CREATE_OR_RESOLVER_DELAY_IMPORT_IMAGES_UNOBSERVED="+host[9]
                +";HOST_NATIVE_GETPROCADDRESS_IMPORTS="+host[10]+";HOST_NATIVE_CREATEPROCESS_RESOLVED_ENTRIES="+host[11]+";HOST_NATIVE_GETPROCADDRESS_RESOLVED_ENTRIES="+host[12]
                +";HOST_PROCESS_ALIASES="+host[13]+";HOST_PRIMARY_THREAD_ALIASES="+host[14]+";HOST_EXTERNAL_DUPLICATE_TRANSFERS="+host[15]+";HOST_INHERITED_EXPOSURES="+host[16]
                +";HOST_LOST_HANDLE_EVIDENCE="+host[17]+";HOST_OBJECT_IDENTITY_BACKEND="+host[18]+";HOST_HANDLE_IMPORTS="+host[19]+";HOST_HANDLE_RESOLVED_ENTRIES="+host[20]
                +";HOST_NATIVE_WORKER_HOLDS="+host[21]+";HOST_NATIVE_WORKER_EXIT_PENDING="+host[22]+";HOST_NATIVE_WORKER_LIFECYCLE_GAPS="+host[23]
                +";HOST_CHILD_LOAD_RECEIPTS="+host[24]+";HOST_CHILD_OWN_PINS="+host[25]+";HOST_CHILD_SOURCE_HOLDS="+host[26]
                +";HOST_CHILD_SHARED_OR_UNKNOWN_PENDING="+host[27]+";HOST_CHILD_IMPORT_GAPS="+host[28]+";HOST_CHILD_RESOURCE_OR_NONEXEC_LOADS="+host[29]
                +";HOST_CHILD_OWN_PIN_RELEASES="+host[30]+";HOST_CHILD_APPLICATION_LOADER_LIFETIMES_UNOBSERVED="+host[31]
                +";HOST_TLS_FLS_SLOTS="+host[32]+";HOST_TLS_FLS_VALUES="+host[33]+";HOST_TLS_FLS_ACTIVE="+host[34]
                +";HOST_TLS_FLS_LIFECYCLE_GAPS="+host[35]+";HOST_TLS_FLS_SOURCE_HOLDS="+host[36]
                +";HOST_STATIC_TLS_INSTANCES="+host[37]+";HOST_STATIC_TLS_GAPS="+host[38]
                +";HOST_CPP_OBJECTS="+host[39]+";HOST_CPP_OBJECT_ACTIVE="+host[40]+";HOST_CPP_OBJECT_GAPS="+host[41]
                +";JVMTI_GETENV="+jvmti[0]+";JVMTI_OBSERVED_ENVIRONMENTS="+jvmti[1]+";JVMTI_CALLBACK_SLOTS="+jvmti[2]+";JVMTI_ACTIVE_CALLBACKS="+jvmti[3]
                +";JVMTI_DISPOSED_ENVIRONMENTS="+jvmti[4]+";JVMTI_FAILURES="+jvmti[5]+";JVMTI_OWN_TABLE="+jvmti[6]+";JVMTI_UNATTRIBUTED_CALLBACKS="+jvmti[7]+";"+hostKernelSummary(module);
    }
    public static String retirementGap(Module module){
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        if(!available)return "";
        if(jvmtiDefinitionsPending0(module))return "JVMTI_DEFINITION_CONTINUATION_PENDING";
        long[] host=hostState0(module);long[] jvmti=jvmtiState0(module);
        if(host[37]!=0||host[38]!=0)return "HOST_STATIC_TLS_RETIREMENT_PENDING:instances="+host[37]+":gaps="+host[38];
        if(host[39]!=0||host[40]!=0||host[41]!=0)return "HOST_CPP_OBJECT_RETIREMENT_PENDING:objects="+host[39]+":active="+host[40]+":gaps="+host[41];
        if(host[32]!=0||host[33]!=0||host[34]!=0||host[35]!=0||host[36]!=0)
            return "HOST_TLS_FLS_RETIREMENT_PENDING:slots="+host[32]+":values="+host[33]+":active="+host[34]+":gaps="+host[35]+":sourceHolds="+host[36];
        if(host[21]!=0)return "HOST_NATIVE_WORKER_RETIREMENT_PENDING:holds="+host[21]+":exitPending="+host[22]+":lifecycleGaps="+host[23];
        if(host[25]!=0||host[26]!=0)return "HOST_CHILD_OWN_RETIREMENT_PENDING:pins="+host[25]+":sourceHolds="+host[26]+":sharedOrUnknown="+host[27]+":importGaps="+host[28];
        if(host[28]!=0)return "HOST_CHILD_IMPORT_CONTROL_UNAVAILABLE:images="+host[28];
        if(host[27]!=0)return "HOST_CHILD_SHARED_OR_UNKNOWN_SOURCE_PENDING:records="+host[27];
        if(host[31]!=0)return "HOST_CHILD_APPLICATION_LOADER_LIFETIME_UNOBSERVED:receipts="+host[31]+":ownPinReleases="+host[30]+":resourceOrNonexec="+host[29];
        if(jvmti[2]!=0||jvmti[3]!=0)return "JVMTI_CALLBACK_RETIREMENT_PENDING:slots="+jvmti[2]+":active="+jvmti[3]+":environments="+jvmti[1];
        if(jvmti[1]!=0||jvmti[7]!=0)return "JVMTI_ENVIRONMENT_DISPOSITION_PENDING:environments="+jvmti[1]+":unattributedCallbacks="+jvmti[7];
        long[] kernel=hostKernelState0(module);
        if(kernel[17]!=0||kernel[18]!=0||kernel[19]!=0)return "HOST_KERNEL_DISPOSITION_PENDING:processReceipts="+kernel[17]+":fileReceipts="+kernel[18]+":policyPending="+kernel[19]+":win32="+kernel[4]+":ntstatus="+kernel[5];
        if(host[5]!=0)return "HOST_NATIVE_HANDLE_TRANSFER_OR_CLOSE_UNOBSERVED:records="+host[5]+":liveFamilies="+host[0]+":handleErrors="+host[3]+":active="+host[4];
        if(host[0]!=0)return "HOST_PROCESS_RETIREMENT_PENDING:families="+host[0]+":shared="+host[1]+":unpublished="+host[2]+":handleErrors="+host[3]+":active="+host[4];
        if(host[7]!=0)return "HOST_NATIVE_CREATEPROCESS_COVERAGE_GAP:images="+host[7]+":loadWindowsUnobserved="+host[8]+":createOrResolverDelayImagesUnobserved="+host[9];
        if(!libraryBoundaryReady)return "NATIVE_LIBRARY_SCOPE_UNAVAILABLE";
        long[] actual=status0(module);
        return actual[3]!=0||actual[5]!=0||actual[6]!=0?"NATIVE_RETIREMENT_PENDING:active="+actual[3]+":nativePointers="+actual[5]+":loadedImages="+actual[6]:"";
    }
    public static boolean bindingControlled(java.lang.reflect.Method method){
        if(!controlCaller())throw new SecurityException("NATIVE_CONTROL_AGENT_REQUIRED");
        return available&&bindingControlled0(method);
    }
    public static boolean clearRecoveredField(java.lang.reflect.Field field,Object receiver,Object expected){
        if(!ControlBridge.recoveryWriter())throw new SecurityException("RECOVERY_FIELD_WRITER_REQUIRED");
        return available&&clearField0(field,receiver,expected);
    }
    static Class<?> activeClass() {return available?activeClass0():null;}
    static boolean definedClass(Class<?> declaring,Class<?> actual) {return available&&definedClass0(declaring,actual);}
    static boolean beginDefinition(Class<?> declaring,ClassLoader loader,byte[] bytes){return available&&definitionBegin0(declaring,loader,bytes);}
    static boolean endDefinition(Class<?> declaring){return available&&definitionEnd0(declaring);}
    static boolean createdObject(Class<?> declaring,Object actual){return available&&createdObject0(declaring,actual);}
    static boolean memoryMutationBoundary(Object receiver){
        if(CALLER.getCallerClass()!=ControlBridge.class)return false;
        return memoryMutationBoundary0(receiver);
    }
    static boolean unsafeMutationBoundary(Object receiver,long offset,long length,String kind,boolean bulk){
        if(CALLER.getCallerClass()!=ControlBridge.class)return false;
        return unsafeMutationBoundary0(receiver,offset,length,kind,bulk,false);
    }
    private static boolean unsafeMetadata(Object receiver){return CodeSourceBridge.fieldGateMetadata(receiver);}
    static boolean unsafeReadBoundary(Object receiver,long offset,String kind){
        return CALLER.getCallerClass()==ControlBridge.class&&unsafeMutationBoundary0(receiver,offset,0,kind,false,true);
    }
    static boolean unsafeCopyBoundary(Object source,long sourceOffset,Object receiver,long offset,long length){
        return CALLER.getCallerClass()==ControlBridge.class&&unsafeCopyBoundary0(source,sourceOffset,receiver,offset,length);
    }
    static boolean unsafeControlScope(){return CALLER.getCallerClass()==ControlBridge.class&&available&&unsafeControlScope0();}
    static long memoryWriteBegin(long address,long bytes,Module[] contributors,boolean reading){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        return available?memoryWriteBegin0(address,bytes,contributors,reading,false):0;
    }
    static long memoryCopyReadBegin(long address,long bytes,Module[] contributors){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_BOUNDARY_REQUIRED");
        return available?memoryWriteBegin0(address,bytes,contributors,true,true):0;
    }
    static void memoryWriteContributors(long token,Module[] contributors){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        memoryWriteContributors0(token,contributors);
    }
    static void memoryWriteEnd(long token,boolean written){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_MEMORY_MUTATION_BOUNDARY_REQUIRED");
        memoryWriteEnd0(token,written);
    }
    static Module[] memoryReadSources(long token){
        if(CALLER.getCallerClass()!=ControlBridge.class)throw new SecurityException("ACTUAL_MEMORY_READ_BOUNDARY_REQUIRED");
        return memoryReadSources0(token);
    }
    private static boolean memoryRecoveryCaller(){return ControlBridge.recoveryWriter();}
    public static long[] restoreMemory(Module[] modules,long cursor,long offset,int budget){
        if(!ControlBridge.recoveryWriter())throw new SecurityException("NATIVE_MEMORY_RECOVERY_CORE_REQUIRED");
        if(modules==null||offset<0||budget<=0)throw new IllegalArgumentException("NATIVE_MEMORY_RECOVERY_SCOPE_REQUIRED");
        for(Module module:modules)if(module==null||!ControlBridge.modStopped(module))throw new SecurityException("ACTUAL_STOPPED_MEMORY_SOURCES_REQUIRED");
        if(modules.length==0)return new long[]{0,0,0,0,1};
        if(!available)throw new IllegalStateException("NATIVE_MEMORY_RECOVERY_UNAVAILABLE");
        return restoreMemory0(modules,cursor,offset,budget);
    }
    public static boolean restoreRecoveredField(java.lang.reflect.Field field,Object receiver,Object expected,Object incoming){
        if(!ControlBridge.recoveryWriter())throw new SecurityException("RECOVERY_FIELD_WRITER_REQUIRED");
        return available&&restoreField0(field,receiver,expected,incoming);
    }
    static boolean restoreHeapArray(Object array,int index,Object expected,Object incoming){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class||!ControlBridge.recoveryWriter())throw new SecurityException("HEAP_RECOVERY_CORE_REQUIRED");
        return available&&restoreArray0(array,index,expected,incoming);
    }
    static boolean restoreHeapArrayBytes(Object array,long start,byte[] expected,byte[] incoming){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class||!ControlBridge.recoveryWriter())throw new SecurityException("HEAP_RECOVERY_CORE_REQUIRED");
        return available&&restoreHeapArrayBytes0(array,start,expected,incoming);
    }
    static Object cloneExact(Object source)throws CloneNotSupportedException {
        if(!available)throw new IllegalStateException(state);
        return clone0(source);
    }
    private static native int abi();
    private static native java.util.UUID returnIdentifier0();
    private static native java.util.concurrent.CompletableFuture<?> returnFuture0();
    private static native Class<?> controlWriter0();
    private static native Class<?> rawMemoryWriter0();
    private static native boolean initialize0(Class<?> tasks,Class<?> definitions);
    private static native boolean initializeDefinitionDirectory0(Class<?> owner);
    private static native boolean compareDefinitionReference0(Object slot,Object expected,Object next);
    private static native boolean codeLayout0(ClassLoader loader,String name,byte[] bytes,String[] methods,Module[][][] owners,boolean hidden,Object execution);
    private static native boolean codeDefinition0(Class<?> actual,byte[] bytes);
    private static native String[] codeVersion0(Class<?> actual,byte[] bytes);
    private static native String[] codeMethodVersion0(Class<?> actual,byte[] bytes,String method);
    private static native String[] codeDeclarations0(Class<?> actual);
    private static native Class<?>[] loadedClasses0(boolean bootstrapOnly);
    private static native Class<?>[] bootstrapLookupClasses0();
    private static native Object controlTable0();
    private static native Module[] codeFrame0(Class<?> declaring,String method,String descriptor,int location);
    private static native boolean executionRootAllowed0();
    private static native Object executionPlan0(Class<?> declaring,String method,String descriptor,int location);
    private static native boolean executionWatch0(Class<?> declaring,String method,String descriptor,int location,Object plan,Object token);
    private static native long executionSite0(Object token);
    private static native long executionFrameSite0(Object token);
    private static native boolean platformPrepareTargets0(String[] names);
    private static native boolean platformQueueQuiescent0(Class<?> pool,Class<?> queue);
    private static native int executionPopPermit0(Object token);
    private static native Class<?> codeFieldOwner0(Class<?> symbolic,String name,String descriptor);
    private static native boolean heapWatch0(Object holder,Class<?> declaring,String name,String descriptor);
    private static native void heapPolicyChanged0(Object object);
    private static native Object[] heapReadField0(Object holder,Class<?> declaring,String name,String descriptor);
    private static native byte[] codeDigest0(byte[] bytes);
    private static native byte[] heapArrayImage0(Object array,long start,int length);
    private static native long heapArrayBits0(Object array,long start,int length);
    private static native boolean restoreHeapArrayBytes0(Object array,long start,byte[] expected,byte[] incoming);
    private static native long[] heapState0();
    private static native boolean heapEventPermit0(Object holder,Class<?> declaring);
    private static native long[] codeState0(Module module);
    private static native void retireCode0(Module module);
    private static native boolean codeRetirementFramePermit0(Class<?> declaring,int location,Object plan,Module owner);
    private static native boolean preparedClass0(Class<?> actual);
    private static native boolean sourceCapture0();
    private static native boolean sourceQueryStopped0();
    private static native int networkBoundaryDepth0(Class<?> boundary,String method);
    private static native boolean networkCleanupBoundary0(Class<?> boundary,String method,int phase,int bottomDepth);
    private static native Object networkReadField0(Object receiver,Class<?> declaring,String name,boolean statik);
    private static native java.lang.reflect.Field networkField0(Class<?> declaring,String name);
    private static native Object[][] carrierFields0(Object receiver);
    private static native Object[] carrierRead0(Object receiver,java.lang.reflect.Field field);
    private static native Class<?> carrierMethod0(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller);
    private static native long[] carrierMethodImage0(Class<?> declaring,String name,String descriptor);
    private static native long[] carrierFrame0(Class<?> declaring,String name,String descriptor,int bottomDepth);
    private static native long[] carrierCallSite0(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,String callerMethod,String callerDescriptor,int bottomDepth);
    private static native Object[] carrierStaticValue0(Class<?> declaring,String name,String descriptor);
    private static native int carrierCallbackDepth0(Class<?> declaring,String name,String descriptor);
    private static native boolean carrierCallback0(Object callback,Class<?> callee,String name,String descriptor,int calleeBottomDepth,
                                                  Class<?> boundary,String boundaryMethod,String boundaryDescriptor,int boundaryBottomDepth);
    private static native boolean arrayCopyInternalBoundary0();
    private static native boolean arrayCopyReadBoundary0(Object source,int from,int length);
    private static native boolean arrayCopyBoundary0(Object source,int from,int length,Object target,int to,int prefix,Object read);
    private static native boolean fileOperationBoundary0(Object receiver,Class<?> declaring,String method,Module[] sources,int operation,long address,long length);
    private static native boolean fileOperationFinishing0(Object token);
    private static native boolean mappingDuplicateFrame0(long[] identity,int phase);
    private static native boolean ioOperationBoundary0(Class<?> declaring,String method,long[] arguments,Object[] references,Module[] sources);
    private static native int fileBindings0();
    private static native long[] fileBindingState0();
    private static native boolean bufferAllocationBoundary0();
    private static native boolean bufferAddressBoundary0(Object buffer,Module[] sources);
    private static native long[] bindBufferStorage0(java.nio.Buffer buffer,Object action,long address,long bytes);
    private static native long[] observeBufferStorage0(java.nio.Buffer buffer,Object action,long address,long bytes);
    private static native boolean bufferStorageUse0(java.nio.Buffer buffer,long token,long generation,long address,long bytes,boolean retain);
    private static native void exposeBufferStorage0(java.nio.Buffer buffer,long token,long generation,long address,long bytes,Module[] sources);
    private static native long[] restoreBufferStorage0(Object action,long token,long generation,Module[] modules,long cursor,int budget);
    private static native long ioBufferWrite0(Object receipt,Object action,long allocation,long generation,long address,long bytes,Module[] sources);
    private static native void ioBufferWritten0(Object receipt,long token,long bytes,boolean completed);
    private static native long[] ioReadVector0(Object operation,long address,int count,boolean windows);
    private static native boolean threadOperationBoundary0(Thread thread,Module[] sources,int operation);
    private static native boolean threadOperationFinishing0(Object token,boolean completed);
    private static native int threadBindings0();
    private static native long libraryBegin0(Object library,Class<?> fromClass,Class<?> actor);
    private static native long libraryUnloadBegin0(Object unloader,long handle,Class<?> actor,boolean automatic,boolean recovery);
    private static native void libraryUnloader0(Object library,Object unloader,long handle);
    private static native boolean libraryActivate0();
    private static native boolean libraryPresent0();
    private static native void libraryEnd0(long token,long handle,boolean success);
    private static native void module0(Module module,boolean stopped);
    private static native boolean processPrepare0(Class<?> process);
    private static native long processCreateBegin0(long[] handles)throws IOException;
    private static native void processCreateEnd0(long token,long handle,long[] handles);
    private static native long processConstructed0(Process process,long handle);
    private static native void processExposed0(Process process,long token,Module[] sources);
    private static native String processRetire0(Process process,long token);
    private static native boolean hostReady0();
    private static native boolean hostLibraryLoadReady0();
    private static native void hostKernelProtect0(Module module,boolean protect);
    private static native void hostKernelOwnerProtect0(boolean protect);
    private static native String hostKernelFile0(FileDescriptor descriptor,Module[] selected);
    private static native long[] hostKernelState0(Module module);
    private static native long[] hostState0(Module module);
    private static native long[] status0(Module module);
    private static native boolean jvmtiBoundary0(Module module,ClassLoader loader,String name,Class<?> actual,ProtectionDomain domain,byte[] original,byte[] bytes,Module[] writers,boolean changed);
    private static native boolean jvmtiDefinitionCompletion0(Class<?>[] actuals);
    private static native boolean jvmtiDefinitionsPending0(Module module);
    private static native long[] jvmtiState0(Module module);
    private static native boolean bindingControlled0(java.lang.reflect.Method method);
    private static native boolean clearField0(java.lang.reflect.Field field,Object receiver,Object expected);
    private static native boolean restoreField0(java.lang.reflect.Field field,Object receiver,Object expected,Object incoming);
    private static native boolean restoreArray0(Object array,int index,Object expected,Object incoming);
    private static native Class<?> activeClass0();
    private static native boolean definedClass0(Class<?> declaring,Class<?> actual);
    private static native boolean definitionBegin0(Class<?> declaring,ClassLoader loader,byte[] bytes);
    private static native boolean definitionEnd0(Class<?> declaring);
    private static native boolean createdObject0(Class<?> declaring,Object actual);
    private static native boolean memoryMutationBoundary0(Object receiver);
    private static native boolean unsafeMutationBoundary0(Object receiver,long offset,long length,String kind,boolean bulk,boolean reading);
    private static native boolean unsafeControlScope0();
    private static native long memoryWriteBegin0(long address,long bytes,Module[] contributors,boolean reading,boolean snapshot);
    private static native void memoryWriteEnd0(long token,boolean written);
    private static native Module[] memoryReadSources0(long token);
    private static native void memoryWriteContributors0(long token,Module[] contributors);
    private static native long[] restoreMemory0(Module[] modules,long cursor,long offset,int budget);
    private static native boolean unsafeCopyBoundary0(Object source,long sourceOffset,Object receiver,long offset,long length);
    private static native long[] memoryState0(Module module);
    private static native Object clone0(Object source)throws CloneNotSupportedException;
}
