package dev.ronova.pro.agent;

import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/** Installs control inside this JVM; Forge's bundled bootstrap supports ordinary no-argument installation. */
public final class RecoveryAgent {
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static JarFile bootstrap;
    private static final Set<String> installed=ConcurrentHashMap.newKeySet();
    /** Returned Java images and their actual VM completion, never class names alone. */
    private static final class PlatformImage {
        private final Class<?> actual;
        private final boolean valid,accepted;
        PlatformImage(Class<?> actual,boolean valid,boolean accepted){this.actual=actual;this.valid=valid;this.accepted=accepted;}
        Class<?> actual(){return actual;}
        boolean valid(){return valid;}
        boolean accepted(){return accepted;}
    }
    private static final Map<String,PlatformImage> platformImages=new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<String,PlatformImage>> platformReturns=ThreadLocal.withInitial(HashMap::new);
    /** True once the reflection field-write entry points carry the policy guard. */
    private static volatile boolean reflectionGuarded,unsafeGuarded;
    private static volatile boolean eventFaultGuarded;
    private record EventFaultImage(java.lang.ref.WeakReference<Class<?>> actual,boolean initial,boolean valid,boolean accepted){}
    private static final Map<ClassLoader,EventFaultImage> eventFaultImages=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Map<ClassLoader,EventFaultImage>> eventFaultReturns=ThreadLocal.withInitial(WeakHashMap::new);
    private static volatile Class<?> clientEventBus;
    public static boolean eventFaultGuarded() {Class<?> actual=clientEventBus;return actual==null?eventFaultGuarded:eventFaultGuarded(actual);}
    public static boolean eventFaultGuarded(Class<?> actual) {
        if(actual==null||!actual.getName().equals("net.minecraftforge.eventbus.EventBus"))return false;
        synchronized(eventFaultImages){EventFaultImage image=eventFaultImages.get(actual.getClassLoader());
            return image!=null&&image.valid()&&image.accepted()&&image.actual().get()==actual;}
    }
    public static String eventFaultState(){
        Class<?> actual=clientEventBus;if(actual!=null)return eventFaultGuarded(actual)?"DISPATCH_READY":"DISPATCH_PENDING";
        synchronized(eventFaultImages){return eventFaultImages.isEmpty()?"WAITING_FOR_EVENT_BUS":eventFaultGuarded?"DISPATCH_READY:CORE_NOT_BOUND":"DISPATCH_PENDING";}
    }

    private static volatile Instrumentation instrumentation;
    private static Class<?> bridge;
    private static java.lang.reflect.Method imageControlReader;
    private static Class<?> networkBridge;
    private static Class<?> clientBridge;
    private static Class<?> resourceBridge;
    private static final ControlImages controlImages=new ControlImages();
    private static volatile ClassFileTransformer nativeFinalTransformer;
    private static volatile String controlFailure;
    private static volatile Path nativeBootstrapPath;
    private static volatile boolean javaTransformersPublished,javaInstallationFinished;
    private static volatile boolean platformPreparationTracked;
    private static final Map<Class<?>,String> definitions=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> watching=Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<Class<?>> externalPromotion=new ThreadLocal<>();
    private record CandidateContract(Class<?> owner,String method,String descriptor){}
    private static volatile CandidateContract[] candidateContracts=new CandidateContract[0];
    private static long definitionEpoch;
    private RecoveryAgent() { }
    public static void premain(String argument,Instrumentation api) {
        start(argument,api,"PREMAIN");
        String actual=installState();
        if(!actual.equals("INSTALLED_FULL")) { report("AGENT_PREMAIN_BACKEND_UNAVAILABLE:"+actual);return; }
        report("AGENT_PREMAIN_READY:"+actual);
    }
    /**
     * Attach-time entry, used by the zero-argument bootstrap the mod starts after Forge is up.
     *
     * The transformer is installed for future class loads and for every target the platform still allows to be
     * retransformed. Classes that loaded before this call keep running their already-loaded bytes: the JDK cannot
     * retrofit a frame that is already executing, so anything that happened before this point is reported as a
     * coverage gap, never as early observation. The installation result is published so the caller can tell a
     * successful backend install from a successful attach call.
     */
    public static void agentmain(String argument,Instrumentation api) {
        start(argument,api,"ATTACH");
    }
    private static synchronized void start(String argument,Instrumentation api,String mode) {
        if(!installState.equals("NOT_ATTEMPTED")) {
            boolean retryNative=nativeRetryable();
            boolean retryPlatform=javaTransformersPublished&&javaInstallationFinished&&"FULL".equals(nativeInstallMode())
                    &&(!platformPreparationTracked||platformImages.values().stream().anyMatch(image->!image.accepted()));
            if(!retryNative&&!retryPlatform){report("AGENT_EXISTING_STATE:"+installState());return;}
            if(javaTransformersPublished){
                // The Native handoff can resume only its documented safe
                // phase. An existing Java chain is never installed twice.
                try{
                    Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
                    if(retryNative)report(String.valueOf(nativeControl.getMethod("install",Path.class).invoke(null,nativeBootstrapPath)));
                    if(javaInstallationFinished&&"FULL".equals(nativeInstallMode()))resumePlatformBoundaries(nativeControl);
                    installState=installationComplete()?"INSTALLED_FULL":"INSTALLED_PARTIAL:NATIVE_RETRY_JAVA_CHAIN_RETAINED";
                }catch(ReflectiveOperationException|UnmodifiableClassException|RuntimeException|LinkageError unavailable){report("AGENT_NATIVE_RETRY_UNAVAILABLE:"+unavailable.getClass().getSimpleName());}
                report("AGENT_EXISTING_STATE:"+installState());return;
            }
            // No transformer was published: a safe Native prepublication
            // failure may resume installation without duplicating a chain.
        }
        // Claim the installation before bootstrap/native publication, not only once Instrumentation is saved.
        // Even a failure before addTransformer leaves this attempt's exact bootstrap classes in the JVM.
        installState="INSTALLING:"+mode;
        try {
            if(Boolean.getBoolean("ronova.pro.startupTrace")){
                Thread starting=Thread.currentThread();
                Thread trace=new Thread(()->{
                    for(int sample=0;sample<3;sample++){
                        try{Thread.sleep(90000);}catch(InterruptedException done){return;}
                        System.err.println("RONOVA_STARTUP_THREAD_SAMPLE "+sample);
                        for(StackTraceElement frame:starting.getStackTrace())System.err.println("  at "+frame);
                    }
                },"Ronova startup failure trace");
                trace.setDaemon(true);trace.start();
            }
            install(argument,api);
            installState=installationComplete()?"INSTALLED_FULL":"INSTALLED_PARTIAL";
        } catch(Throwable unavailable) {
            while(unavailable instanceof java.lang.reflect.InvocationTargetException&&unavailable.getCause()!=null)unavailable=unavailable.getCause();
            installState="FAILED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage();
            report("AGENT_"+mode+"_UNAVAILABLE:"+installState);
            unavailable.printStackTrace(System.err);
            return;
        }
        if(mode.equals("ATTACH")) {
            // Earlier activity is not retroactively observable; say so instead of implying full coverage.
            report("AGENT_LATE_ATTACH_COVERAGE_GAP:PRE_ATTACH_ACTIVITY_NOT_OBSERVED:"+installState);
        }
    }
    /** The real result of the last installation attempt; an attach call returning is not this. */
    public static String installState() {
        String actual=installState;
        if(actual.startsWith("FAILED:"))return actual+":NATIVE="+nativeInstallMode()+":NATIVE_RETRYABLE="+nativeRetryable();
        String failure=controlFailure;
        if(failure!=null)return actual.startsWith("INSTALLING:")?actual+":TRANSFORM_FAILED:"+failure:"INSTALLED_PARTIAL:TRANSFORM_FAILED:"+failure;
        if(actual.equals("INSTALLED_FULL")&&!installationComplete())return "INSTALLED_PARTIAL:CURRENT_BOUNDARY_UNAVAILABLE:NATIVE="+nativeInstallMode();
        if(actual.startsWith("INSTALLED_PARTIAL"))return installationComplete()?"INSTALLED_FULL":actual+":NATIVE="+nativeInstallMode()+":NATIVE_RETRYABLE="+nativeRetryable();
        return actual;
    }
    private static String nativeInstallMode(){
        if(nativeBootstrapPath==null)return "NOT_ATTEMPTED";
        try{return String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("installMode").invoke(null));}
        catch(ReflectiveOperationException|LinkageError unavailable){return "UNAVAILABLE";}
    }
    private static boolean nativeRetryable(){
        if(nativeBootstrapPath==null)return false;
        try{return Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("installRetryable").invoke(null));}
        catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    private static boolean installationComplete() {
        try {
            return javaInstallationFinished&&platformPreparationTracked&&"FULL".equals(nativeInstallMode())&&platformImages.values().stream().allMatch(PlatformImage::accepted)
                    &&reflectionGuarded&&unsafeGuarded&&controlReady();
        } catch(RuntimeException|LinkageError unavailable) { return false; }
    }
    public static String sourceMapState() { return "REMOVED:JDK_CONTAINER_HOOKS_UNAVAILABLE"; }
    public static String sourcePublicationState(){return "UNAVAILABLE:JDK_CONTAINER_HOOKS_REMOVED";}
    /** Remaining exact field writers; this does not certify ordinary JDK collection mutations. */
    public static String sourceFieldState(){return reflectionGuarded&&unsafeGuarded&&loadedHandleBoundariesReady()
            &&installState().equals("INSTALLED_FULL")?"INSTALLED":"SOURCE_FIELD_SCOPE_UNAVAILABLE";}
    private static volatile String installState="NOT_ATTEMPTED";
    private static synchronized void install(String argument,Instrumentation api) throws Exception {
        if(api==null)throw new IllegalArgumentException("INSTRUMENTATION_REQUIRED");
        if(!api.isRetransformClassesSupported())throw new IllegalStateException("RETRANSFORM_UNSUPPORTED");
            if(argument==null||argument.isBlank())throw new IllegalArgumentException("BOOTSTRAP_JAR_PATH_REQUIRED");
            if(argument.startsWith("base64:"))argument=new String(Base64.getUrlDecoder().decode(argument.substring(7)),java.nio.charset.StandardCharsets.UTF_8);
            nativeBootstrapPath=Path.of(argument).toAbsolutePath().normalize();
            bootstrap=new JarFile(nativeBootstrapPath.toFile());
            api.appendToBootstrapClassLoaderSearch(bootstrap);
            bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null);
            if(!Integer.valueOf(99).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("TASK_BRIDGE_ABI_MISMATCH");
            // This classfile was first requested beneath a stopped empty-stream return.
            Class.forName("java.util.stream.IntPipeline$Head",false,null);
            for(String helper:List.of("SourcePublication","SourceIndexMutation","FastListView","FieldMutation","EquipmentInvocations"))
                Class.forName("dev.ronova.pro.bootstrap.ControlBridge$"+helper,true,null);
            // A JNI observation uses this exact family's mutually referring metadata.
            // Define its direct member classes without initializing them before that
            // observation can recursively request its own ArrayChange/receipt bytes.
            Class.forName("dev.ronova.pro.bootstrap.ExecutionFlow",false,null).getDeclaredClasses();
            // Array-change completion iterates this exact TreeMap tail view.
            // Define its view/value/entry iterator chain before JNI can fill
            // those class bytes through the same guarded array-completion path.
            for(String helper:List.of("java.util.TreeMap$NavigableSubMap","java.util.TreeMap$AscendingSubMap",
                    "java.util.TreeMap$NavigableSubMap$EntrySetView","java.util.TreeMap$AscendingSubMap$AscendingEntrySetView",
                    "java.util.TreeMap$NavigableSubMap$SubMapIterator","java.util.TreeMap$NavigableSubMap$SubMapEntryIterator",
                    "java.util.AbstractMap$2","java.util.AbstractMap$2$1"))
                Class.forName(helper,false,null);
            // These are the metadata carried by one actual RenderSystem queue
            // publication and its poll-to-finally handoff, never a Mod wrapper.
            for(String helper:List.of("RenderQueueFields","RenderCallEntry","RenderSubmission","RenderReplay","RenderConsumption"))
                Class.forName("dev.ronova.pro.bootstrap.ClientBridge$"+helper,false,null);
            for(String helper:List.of("PacketEntry","Deferred","Cleanup","Submission"))
                Class.forName("dev.ronova.pro.bootstrap.NetworkBridge$"+helper,false,null);
            Class.forName("dev.ronova.pro.bootstrap.NetworkCleanup",false,null).getDeclaredClasses();
            for(String helper:List.of("Helper","EquipmentAsync","AsyncBinding","EquipmentAsyncFields","EquipmentDefined"))
                Class.forName("dev.ronova.pro.bootstrap.TaskBridge$"+helper,false,null);
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge$LedgerMap",false,null);
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge$FrameMethods",false,null);
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge$MethodBucket",false,null);
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge$ProducerFrame",false,null);
            Class.forName("dev.ronova.pro.bootstrap.EventFaultBridge",true,null);
            Class<?> control=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
            if(!Integer.valueOf(99).equals(control.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("CONTROL_BRIDGE_ABI_MISMATCH");
            Module base=Object.class.getModule(),ours=RecoveryAgent.class.getModule();
            api.redefineModule(base,Set.of(bridge.getModule()),Map.of(
                    "jdk.internal.org.objectweb.asm",Set.of(ours),"jdk.internal.org.objectweb.asm.commons",Set.of(ours),
                    "jdk.internal.org.objectweb.asm.tree",Set.of(ours),"jdk.internal.org.objectweb.asm.tree.analysis",Set.of(ours)),
                    Map.ofEntries(Map.entry("java.util.concurrent",Set.of(bridge.getModule())),Map.entry("java.util.concurrent.atomic",Set.of(bridge.getModule())),Map.entry("java.lang.ref",Set.of(bridge.getModule())),Map.entry("java.lang",Set.of(bridge.getModule(),ours)),
                            Map.entry("java.util",Set.of(bridge.getModule())),Map.entry("java.util.concurrent.locks",Set.of(bridge.getModule(),ours)),Map.entry("java.io",Set.of(bridge.getModule())),Map.entry("java.net",Set.of(bridge.getModule())),
                            Map.entry("java.nio",Set.of(bridge.getModule())),Map.entry("jdk.internal.ref",Set.of(bridge.getModule())),
                            Map.entry("sun.nio.cs",Set.of(bridge.getModule())),Map.entry("sun.nio.ch",Set.of(bridge.getModule())),Map.entry("jdk.internal.loader",Set.of(bridge.getModule()))),Set.of(),Map.of());
            // A Forge scanning thread may run newly transformed code immediately after addTransformer.
            // Finish the bootstrap helper's access and initialization before publishing any such call site.
            Class.forName("dev.ronova.pro.bootstrap.BackingBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$BufferPlatform",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$CharacterWrappers",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$WrapperRecord",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$MappingDuplicate",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$MappingDuplicateNative",true,null);
            Class.forName("dev.ronova.pro.bootstrap.CarrierBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.IoBridge",true,null);
            // Resolve the observed field-write and completed-result dependencies
            // before Native/JDK hooks can encounter their own class bytes.
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge$HandleWriters",true,null);
            Class.forName("java.util.concurrent.CompletableFuture",true,null);
            // Native source queries enter these bridges immediately, including
            // while another bootstrap initializer performs a guarded Unsafe CAS.
            Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ClientBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null);
            Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
            report(String.valueOf(nativeControl.getMethod("install",Path.class).invoke(null,nativeBootstrapPath)));
            instrumentation=api;
            Module instrument=Instrumentation.class.getModule();
            api.redefineModule(instrument,Set.of(bridge.getModule()),Map.of(),
                    Map.of("sun.instrument",Set.of(bridge.getModule(),ours)),Set.of(),Map.of());
            JdkTaskBoundaryContract.enable();
            ClassFileTransformer taskTransformer=new ClassFileTransformer() {
                @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
                        ProtectionDomain domain,byte[] bytes) {
                    if(loader!=null||module!=base||name==null||!JdkTaskBoundaryContract.registered(name))return null;
                    platformTransforming(loader,name,type);
                    boundary(name,false);
                    installed.remove(name);
                    byte[] transformed=JdkTaskBoundaryContract.transform(name,bytes);
                    boolean valid=JdkTaskBoundaryContract.verify(name,transformed==null?bytes:transformed);
                    if(valid)installed.add(name);else { installed.remove(name);report("PLATFORM_IMAGE_CHANGED:"+name); }
                    return transformed;
                }
            };
            ClassFileTransformer objectTransformer=new ClassFileTransformer() {
                @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
                        ProtectionDomain domain,byte[] bytes) {
                    // The same exact Agent image was already excluded by apply().
                    // Exclude it before building an external graph as well: our private
                    // implementation is not an external definition to be taken over.
                    if(module==ours&&loader==RecoveryAgent.class.getClassLoader()&&domain!=null
                            &&java.util.Objects.equals(domain.getCodeSource(),RecoveryAgent.class.getProtectionDomain().getCodeSource()))return null;
                    if(name==null||!JdkTaskBoundaryContract.registered(name))platformTransforming(loader,name,type);
                    Module origin=definitionOrigin(type,loader,name),declaration=origin==null?module:origin;
                    boolean producer=producerModule(declaration),watched=watchedDefinition(type);
                    boolean recorded=ExternalCodeImages.recorded(loader,name);
                    boolean promoted=type!=null&&externalPromotion.get()==type;
                    ExternalCodeDefinitions.ReferenceHeader references=producer||recorded||promoted?null:ExternalCodeDefinitions.references(name,bytes);
                    boolean sourceCandidate=producer||origin!=null||recorded||watched||promoted
                            ||ExternalCodeDefinitions.executionSourcesObserved(declaration,loader,name,references);
                    boolean control=controlImages.critical(module,loader,name,type);
                    boolean contract=contractCandidate(loader,name,type,references);
                    boolean candidate=sourceCandidate||control||contract||boundaryCandidate(loader,name,bytes);
                    if(!candidate){ExternalCodeDefinitions.referenceOnly(loader,name,type,declaration,bytes,bytes,references);platformReturned(loader,name,type,null);return null;}
                    try {
                    if(origin!=null&&module!=null&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    byte[] external=recorded?ExternalCodeImages.transform(loader,name,type,bytes):null;
                    byte[] input=external==null?bytes:external;
                    ExternalCodeRuntime.Source source=sourceCandidate?ExternalCodeRuntime.prepare(declaration,loader,name,type,input,false,true):null;
                    byte[] result=apply(declaration,loader,name,type,domain,source==null?input:source.bytes(),source!=null&&source.present());
                    if(result==null)result=source==null?input:source.bytes();
                    if(origin!=null)result=DefinitionBoundary.generated(loader,result,true);
                    if(source!=null&&(source.present()||source.image()!=null))result=ExternalCodeRuntime.finish(loader,name,type,source,result);
                    else ExternalCodeDefinitions.referenceOnly(loader,name,type,declaration,result,input,references==null?ExternalCodeDefinitions.references(name,input):references);
                    recordDefinition(type,result==null?bytes:result);
                    platformReturned(loader,name,type,result==null?bytes:result);return result;
                    }catch(RuntimeException|LinkageError failure){
                        platformReturned(loader,name,type,null);
                        return failedTransform(declaration,name,bytes,failure,control,
                                control||ModGroupBoundary.stopped(declaration)||watched||promoted||confirmedContract(type));
                    }
                }
                private byte[] apply(Module module,ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes,boolean externalSources) {
                    try {
                    // Forge's discovery loader is active before its module resources and frame types exist.
                    // It does not own world effects; touching it here creates a false partial install.
                    byte[] stopped=ModGroupBoundary.transform(loader,module,name,type,bytes);
                    if(stopped!=null) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        registerProducer(module);publishStoppedModule(module);return stopped;
                    }
                    if(loader==null&&module==base&&"java/util/Optional".equals(name)){
                        if(!module.canRead(bridge.getModule()))api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return EquipmentOptionalBoundary.transform(loader,name,bytes);
                    }
                    if(loader==null&&module==base&&EquipmentFunctionBoundary.target(name)){
                        if(!module.canRead(bridge.getModule()))api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return EquipmentFunctionBoundary.transform(loader,name,bytes);
                    }
                    if(BufferBoundary.wrapTarget(name)&&loader!=null&&!producerModule(module)){
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        resourceBridge.getMethod("expectBufferWrap",String.class,ClassLoader.class,Module.class)
                                .invoke(null,name.replace('/','.'),loader,module);
                        byte[] wrapped=BufferBoundary.transformWrap(loader,name,bytes);
                        if(wrapped!=null)return wrapped;
                    }
                    if(name!=null&&ClientBoundary.TARGETS.contains(name)&&!producerModule(module)){
                        if(module!=null){int slash=name.lastIndexOf('/');String targetPackage=name.substring(0,slash).replace('/','.');
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(targetPackage,Set.of(bridge.getModule())),Set.of(),Map.of());}
                        clientBridge.getMethod("expect",String.class,ClassLoader.class,Module.class).invoke(null,name.replace('/','.'),loader,module);
                        byte[] client=ClientBoundary.transform(loader,name,bytes);if(client!=null){
                            byte[] fields=FieldWriteBoundary.transform(loader,module,client);if(fields!=null)client=fields;
                            byte[] creations=CreationBoundary.transform(loader,name,type,client,producerModule(module)||externalSources);return creations==null?client:creations;
                        }
                    }
                    if(name!=null&&NetworkBoundary.TARGETS.contains(name)&&!producerModule(module)){
                        if(module!=null){int slash=name.lastIndexOf('/');String targetPackage=name.substring(0,slash).replace('/','.');
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(targetPackage,Set.of(bridge.getModule())),Set.of(),Map.of());
                            if(name.equals("io/netty/channel/AbstractChannelHandlerContext")&&module.getLayer()!=null)
                                for(String library:List.of("io.netty.buffer","io.netty.common"))module.getLayer().findModule(library).ifPresent(actual->{
                                    if(!producerModule(actual)){Map<String,Set<Module>> openings=new HashMap<>();for(String part:actual.getPackages())openings.put(part,Set.of(bridge.getModule()));
                                        api.redefineModule(actual,Set.of(bridge.getModule()),Map.of(),openings,Set.of(),Map.of());}
                                });}
                        networkBridge.getMethod("expect",String.class,ClassLoader.class,Module.class).invoke(null,name.replace('/','.'),loader,module);
                        return NetworkBoundary.transform(loader,name,bytes);
                    }
                    if(name!=null&&CodeSourceBoundary.TARGETS.contains(name)&&(loader==null?module==base:!producerModule(module))) {
                        if(module!=null) {
                            Map<String,Set<Module>> openings=new HashMap<>();
                            int slash=name.lastIndexOf('/');String targetPackage=slash<0?"":name.substring(0,slash).replace('/','.');
                            if(!targetPackage.isEmpty())openings.put(targetPackage,Set.of(bridge.getModule(),ours));
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),openings,Set.of(),Map.of());
                        }
                        ExternalCodeImages.expect(name.replace('/','.'),loader,module);
                        codeSourceBridge.getMethod("expect",String.class,ClassLoader.class,Module.class)
                                .invoke(null,name.replace('/','.'),loader,module);
                        return CodeSourceBoundary.transform(loader,name,bytes);
                    }
                    if(name!=null&&name.startsWith("net/minecraftforge/fml/loading/")&&!producerModule(module))return null;
                    if(loader==null&&"java/lang/Thread".equals(name)){
                        return ThreadBoundary.transform(loader,name,bytes);
                    }
                    byte[] defining=DefinitionBoundary.platform(loader,name,bytes);
                    if(defining!=null){byte[] handle=HandleWriteBoundary.transform(loader,name,defining);return handle==null?defining:handle;}
                    if(EventBusBoundary.TARGET.equals(name)) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return eventImage(loader,name,type,bytes);
                    }
                    byte[] command=CommandBoundary.transform(loader,name,bytes);
                    if(command!=null) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return command;
                    }
                    byte[] instrumented=InstrumentationBoundary.transform(loader,name,bytes);
                    if(instrumented!=null)return instrumented;
                    byte[] fileResources=ResourceBoundary.transform(loader,name,bytes);
                    if(fileResources!=null)return fileResources;
                    byte[] nativeLibraries=NativeLibraryBoundary.transform(loader,name,bytes);
                    if(nativeLibraries!=null)return nativeLibraries;
                    byte[] sectionKeys=SectionKeyBoundary.transform(loader,name,bytes);
                    if(sectionKeys!=null)return sectionKeys;
                    byte[] handles=HandleWriteBoundary.transform(loader,name,bytes);if(handles!=null)return handles;
                    if(loader==null&&("java/util/concurrent/ThreadPoolExecutor".equals(name)
                            ||"java/util/concurrent/ScheduledThreadPoolExecutor".equals(name)))return BackingBoundary.transform(loader,name,bytes);
                    if(name!=null&&FastutilIndexBoundary.targets().contains(name)) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        byte[] index=FastutilIndexBoundary.transform(loader,name,bytes);
                        if(name.startsWith("it/unimi/dsi/fastutil/objects/")){byte[] backing=BackingBoundary.transform(loader,module,name,index);if(backing!=null)index=backing;}
                        byte[] fields=FieldWriteBoundary.transform(loader,index);return fields==null?index:fields;
                    }
                    // Reflection is one of the two real paths to a field write that bypasses the public setter.
                    byte[] guarded=ReflectionWriteBoundary.transform(loader,name,bytes);
                    if(guarded!=null)return guarded;
                    byte[] unsafe=UnsafeWriteBoundary.transform(loader,name,bytes);
                    if(unsafe==null)unsafe=InternalUnsafeBoundary.transform(loader,name,bytes);
                    if(unsafe!=null) {
                        if(module!=null&&!module.canRead(bridge.getModule()))api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return unsafe;
                    }
                    // A declared entry point carries a receiver-level policy check, so a target that returns early
                    // or swallows its normal entry still cannot execute against a protected exact object.
                    byte[] console=ConsoleBoundary.transform(loader,name,bytes);
                    byte[] initial=console==null?bytes:console;
                    if(console!=null&&module!=null&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    byte[] slots=EquipmentSlotBoundary.transform(loader,name,type,initial);if(slots!=null)initial=slots;
                    byte[] dispatch=DispatchBoundary.transform(loader,name,type,initial);
                    byte[] current=dispatch==null?initial:dispatch;
                    byte[] equipment=EquipmentActionBoundary.transform(loader,name,type,current);
                    if(equipment!=null)current=equipment;
                    if(loader==null) {
                        if(current!=bytes&&module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return current==bytes?null:current;
                    }
                    if(name==null||module==RecoveryAgent.class.getModule()
                            &&loader==RecoveryAgent.class.getClassLoader()
                            &&domain!=null&&java.util.Objects.equals(domain.getCodeSource(),RecoveryAgent.class.getProtectionDomain().getCodeSource()))return null;
                    byte[] nativeClone=CloneBoundary.transform(loader,current);
                    if(nativeClone!=null)current=nativeClone;
                    boolean declaredSource=type!=null&&watching.contains(type);
                    boolean application=name.startsWith("net/minecraft/")
                            ||producerModule(module)
                            ||declaredSource||externalSources||directEntityReference(bytes)||directMutationReference(bytes);
                    if(!application) {
                        if(current!=bytes&&module!=null&&module.isNamed()&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return current==bytes?null:current;
                    }
                    if(module!=null&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    if(producerModule(module))registerProducer(module);
                    // Native entries are enumerated, never rewritten: swapping a body would change behaviour
                    // without a real backend, which is worse than reporting the gap honestly.
                    NativeBindingBoundary.observe(name,bytes);
                    byte[] fields=FieldWriteBoundary.transform(loader,module,current);
                    if(fields!=null)current=fields;
                    byte[] backings=BackingBoundary.transform(loader,module,name,current);if(backings!=null)current=backings;
                    byte[] creations=CreationBoundary.transform(loader,name,type,current,producerModule(module)||externalSources);
                    if(creations!=null)current=creations;
                    byte[] transformed=current==bytes?null:current;
                    if(transformed!=null&&module!=null&&module.isNamed()&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    return transformed;
                    }catch(ReflectiveOperationException failure) {
                        EquipmentActionBoundary.transformFailed(type);
                        throw new IllegalStateException("CODE_SOURCE_BOUNDARY_BINDING_FAILED:"+name,failure);
                    }catch(RuntimeException|LinkageError failure) {
                        EquipmentActionBoundary.transformFailed(type);
                        throw failure;
                    }
                }
            };
            nativeFinalTransformer=new ClassFileTransformer(){
                @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> actual,ProtectionDomain domain,byte[] bytes)throws java.lang.instrument.IllegalClassFormatException{
                    byte[] tasks=taskTransformer.transform(module,loader,name,actual,domain,bytes);
                    byte[] objects=objectTransformer.transform(module,loader,name,actual,domain,tasks==null?bytes:tasks);
                    return objects==null?tasks:objects;
                }
            };
            javaTransformersPublished=true;
            control.getMethod("install",Class.class,ClassFileTransformer[].class).invoke(null,RecoveryAgent.class,
                    new ClassFileTransformer[]{controlImages,taskTransformer,objectTransformer});
            ControlImages.protect(nativeFinalTransformer);
            resourceBridge=Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",true,null);
            resourceBridge.getMethod("installBufferWrap",Class.class).invoke(null,RecoveryAgent.class);
            definitionBridge=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",true,null);
            definitionBridge.getMethod("install",Class.class).invoke(null,RecoveryAgent.class);
            networkBridge=Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",true,null);
            Object[] networkControls=(Object[])networkBridge.getMethod("controlObjects").invoke(null);
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("registerAgentControls",Object[].class).invoke(null,(Object)networkControls);
            clientBridge=Class.forName("dev.ronova.pro.bootstrap.ClientBridge",true,null);
            Object[] clientControls=(Object[])clientBridge.getMethod("controlObjects").invoke(null);
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("registerAgentControls",Object[].class).invoke(null,(Object)clientControls);
            codeSourceBridge=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);
            codeSourceBridge.getMethod("install",Class.class).invoke(null,RecoveryAgent.class);
            imageControlReader=codeSourceBridge.getMethod("imageControlFields",Object[].class,java.lang.reflect.Field[][].class);
            ControlImages.protect(imageControlReader);
            bridge.getMethod("prepareThreadBoundary").invoke(null);
            Set<String> futureTasks=futureTaskBoundaries();
            platformPreparationTracked=preparePlatformBoundaries(nativeControl,futureTasks);
            api.addTransformer(controlImages,true);
            api.addTransformer(taskTransformer,true);
            api.addTransformer(objectTransformer,true);
            // Publish future-load coverage first. One real snapshot then closes every
            // class already present; classes racing after it use the same transformers.
            Class<?>[] startup=loadedClasses();
            Set<ModuleLayer> sourceLayers=Collections.newSetFromMap(new IdentityHashMap<>());
            List<Class<?>> platform=new ArrayList<>(),sources=new ArrayList<>(),instrumentationClasses=new ArrayList<>(),
                    controls=new ArrayList<>(),tasks=new ArrayList<>(),writers=new ArrayList<>(),indices=new ArrayList<>(),
                    consoles=new ArrayList<>(),events=new ArrayList<>();
            for(Class<?> loaded:startup){
                ModuleLayer layer=loaded.getModule().getLayer();if(layer!=null)sourceLayers.add(layer);
                if(loaded.isArray()||loaded.isPrimitive())continue;
                String name=loaded.getName().replace('.','/');ClassLoader loader=loaded.getClassLoader();
                if(platformGuardTarget(loader,name)){
                    PlatformImage observed=platformImages.putIfAbsent(name,new PlatformImage(loaded,false,false));
                    if(taskReadinessTarget(loader,name)&&(observed==null||!observed.accepted()))boundary(name,false);
                }
                if(!api.isModifiableClass(loaded)){
                    if(loader==null&&JdkTaskBoundaryContract.registered(name))throw new IllegalStateException("UNMODIFIABLE_BOUNDARY:"+name);
                    if(name.equals(ConsoleBoundary.TARGET))ConsoleBoundary.unavailable("UNMODIFIABLE");
                    continue;
                }
                if(loader==null&&(name.equals(InstrumentationBoundary.MANAGER)||name.equals(InstrumentationBoundary.API)))instrumentationClasses.add(loaded);
                else if(name.startsWith("dev/ronova/pro/agent/")||name.startsWith("dev/ronova/pro/bootstrap/"))controls.add(loaded);
                else if(loader==null&&JdkTaskBoundaryContract.registered(name))tasks.add(loaded);
                else if(name.equals("java/lang/reflect/Field")||name.equals("sun/misc/Unsafe")||name.equals("jdk/internal/misc/Unsafe")
                        ||HandleWriteBoundary.targets().contains(name)&&!name.equals("java/lang/invoke/MethodHandles")&&!name.equals("java/lang/invoke/MethodHandles$Lookup"))writers.add(loaded);
                else if(loader==null&&startupPlatformTarget(name))platform.add(loaded);
                else if(name.equals(EventFaultBoundary.TARGET))events.add(loaded);
                else if(name.equals(CommandBoundary.TARGET)||loader!=null&&!producerModule(loaded.getModule())
                        &&(CodeSourceBoundary.TARGETS.contains(name)||NetworkBoundary.TARGETS.contains(name)||ClientBoundary.TARGETS.contains(name)||BufferBoundary.wrapTarget(name)))sources.add(loaded);
                else if(FastutilIndexBoundary.targets().contains(name)||SectionKeyBoundary.targets().contains(name))indices.add(loaded);
                else if(name.equals(ConsoleBoundary.TARGET))consoles.add(loaded);
            }
            bridge.getMethod("boundaryFutureTargets",String[].class).invoke(null,(Object)futureTasks.toArray(String[]::new));
            for(ModuleLayer layer:sourceLayers)codeSourceBridge.getMethod("seedLayer",ModuleLayer.class).invoke(null,layer);
            retransformStartup(api,platform,"PLATFORM",true);
            nativeControl.getMethod("libraryBoundaryInstalled").invoke(null);
            nativeControl.getMethod("processBoundaryInstalled").invoke(null);
            report(String.valueOf(nativeControl.getMethod("threadBoundaryInstalled").invoke(null)));
            report(String.valueOf(nativeControl.getMethod("fileBoundaryInstalled").invoke(null)));
            retransformStartup(api,sources,"SOURCE_BOUNDARIES",true);
            retransformStartup(api,instrumentationClasses,"INSTRUMENTATION",true);
            protectInstrumentation(api);
            control.getMethod("installed").invoke(null);
            ExternalCodeDefinitions.start(api);
            // The agent/bridge were loaded before our transformer, so seed their actual images once.
            retransformStartup(api,controls,"CONTROL_IMAGES",true);
            report("INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:"+controlReady());
            retransformStartup(api,tasks,"TASK_BOUNDARIES",true);
            report(indexGuardState());
            report(sourceMapState());
            report(sourcePublicationState());
            retransformStartup(api,indices,"FASTUTIL_INDEX",false);
            retransformStartup(api,consoles,"CONSOLE_BOUNDARY",false);
            report("CONSOLE_BOUNDARY:"+ConsoleBoundary.state());
            // Field/Unsafe and the already-used handle variants need the real loaded
            // bodies. Unused variants are woven when first defined, without forcing them.
            retransformStartup(api,writers,"FIELD_AND_HANDLE_GUARDS",false);
            report(ReflectionWriteBoundary.state());
            report("UNSAFE_OBJECT_PUT_CAS_ATOMIC_OBJECT_BULK:"+unsafeGuarded+":UNOBSERVED_RAW_NATIVE_AND_UNWOVEN_COMPILER_INTRINSIC_WRITERS_UNSUPPORTED");
            report("EXECUTOR_RELEASE_BOUNDARY:"+loadedExecutorBoundariesReady()+":JDK_CONTAINER_HOOKS:REMOVED:HANDLE_FACTORIES:"+HandleWriteBoundary.installedCount()+"/"+HandleWriteBoundary.targets().size());
            DispatchBoundary.retransformDeclared(api,startup);
            for(Class<?> type:events) {
                try {
                    api.retransformClasses(type);eventFaultAccepted(type);
                }
                catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                    eventFaultGuarded=false;report("CLIENT_FAULT_DISPATCH_UNAVAILABLE:"+failure.getClass().getSimpleName());
                }
            }
            report("CLIENT_FAULT_DISPATCH:"+eventFaultState());
            report(loadedTaskBoundariesReady()?
                    "JDK_FUTURE_WORKER_SCHEDULED_FORKJOIN_CF_DEFERRED_AND_STAGE_INVOCATIONS":"PARTIAL_TASK_BOUNDARIES");
            if(!NativeBindingBoundary.observedBindings().isEmpty())
                report("NATIVE_ENTRIES_OBSERVED:"+NativeBindingBoundary.observedBindings().size()
                        +":"+NativeBindingBoundary.backendState());
            javaInstallationFinished=true;
    }
    private static Set<String> futureTaskBoundaries(){
        Set<String> names=new HashSet<>(JdkTaskBoundaryContract.certificationTargets());
        Collections.addAll(names,"java/lang/Thread","java/util/Timer","java/util/TimerThread");return names;
    }
    private static boolean preparePlatformBoundaries(Class<?> nativeControl,Set<String> futureTasks)throws ReflectiveOperationException{
        Set<String> names=new HashSet<>(futureTasks);names.addAll(HandleWriteBoundary.targets());names.add("java/lang/reflect/Field");
        return Boolean.TRUE.equals(nativeControl.getMethod("platformPrepareTargets",String[].class).invoke(null,(Object)names.toArray(String[]::new)));
    }
    /** Resume only the old chain's pending classes after a Native-degraded install. */
    private static void resumePlatformBoundaries(Class<?> nativeControl)throws ReflectiveOperationException,UnmodifiableClassException{
        platformPreparationTracked=preparePlatformBoundaries(nativeControl,futureTaskBoundaries());
        if(!platformPreparationTracked)return;
        Instrumentation api=instrumentation;if(api==null)return;
        List<Class<?>> pending=new ArrayList<>();
        for(Class<?> actual:loadedClasses()){
            String name=actual.getName().replace('.','/');
            if(!platformGuardTarget(actual.getClassLoader(),name))continue;
            PlatformImage image=platformImages.get(name);
            if(image!=null&&!image.accepted()&&api.isModifiableClass(actual))pending.add(actual);
        }
        retransformStartup(api,pending,"NATIVE_RETRY_PLATFORM",true);
    }
    /** All protections for the shared Forge dispatcher compose on the same returned body. */
    private static byte[] eventImage(ClassLoader loader,String name,Class<?> actual,byte[] bytes) {
        if(!EventBusBoundary.TARGET.equals(name))return null;
        byte[] registration=EventBusBoundary.transform(name,bytes),current=registration==null?bytes:registration;
        byte[] fault=EventFaultBoundary.transform(loader,current);if(fault!=null)current=fault;
        byte[] declared=DispatchBoundary.transform(loader,name,actual,current);if(declared!=null)current=declared;
        if(!EventFaultBoundary.verify(current))throw new IllegalStateException("EVENT_DISPATCH_IMAGE_INCOMPLETE");
        return current==bytes?null:current;
    }
    private static byte[] failedTransform(Module module,String name,byte[] input,Throwable failure,boolean control,boolean required) {
        ModGroupBoundary.failed(module,String.valueOf(name),failure);
        if(control&&controlFailure==null) {
            controlFailure=String.valueOf(name)+":"+failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());
            System.err.println("RONOVA_CONTROL_TRANSFORM_FAILED:"+controlFailure);
        }
        // Observation failure in an unselected source remains a scoped gap. Only a selected
        // definition or an actual control contract refuses the VM's attempted image.
        return required?new byte[]{0,0,0,0}:input;
    }
    private static boolean taskReadinessTarget(ClassLoader loader,String name){
        return loader==null&&(JdkTaskBoundaryContract.registered(name)||name.equals("java/lang/Thread")
                ||name.equals("java/util/Timer")||name.equals("java/util/TimerThread"));
    }
    private static boolean platformGuardTarget(ClassLoader loader,String name){
        if(name==null)return false;
        return loader==null&&(taskReadinessTarget(loader,name)||HandleWriteBoundary.targets().contains(name)
                ||name.equals("java/lang/reflect/Field"))
                ||name.equals("sun/misc/Unsafe")&&(loader==null||loader==ClassLoader.getPlatformClassLoader());
    }
    private static boolean startupPlatformTarget(String name){
        return ResourceBoundary.CHARACTER_WRAPPERS.contains(name)||ResourceBoundary.TARGETS.contains(name)
                ||BufferBoundary.TARGETS.contains(name)||IoBoundary.TARGETS.contains(name)||NativeLibraryBoundary.TARGETS.contains(name)
                ||CodeSourceBoundary.TARGETS.contains(name)||EquipmentFunctionBoundary.target(name)
                ||Set.of("java/lang/ClassLoader","java/lang/Thread","java/lang/ref/Reference","java/util/Optional",
                    "java/util/Timer","java/util/TimerThread","java/lang/invoke/MethodHandles","java/lang/invoke/MethodHandles$Lookup").contains(name);
    }
    private static void platformTransforming(ClassLoader loader,String name,Class<?> actual){
        if(EventFaultBoundary.TARGET.equals(name)){
            eventFaultImages.put(loader,new EventFaultImage(new java.lang.ref.WeakReference<>(actual),actual==null,false,false));
            eventFaultReturns.get().remove(loader);eventFaultGuarded=false;
        }
        if(!platformGuardTarget(loader,name))return;
        platformImages.put(name,new PlatformImage(actual,false,false));platformReturns.get().remove(name);
        if(name.equals("java/lang/reflect/Field"))reflectionGuarded=false;
        if(name.equals("sun/misc/Unsafe"))unsafeGuarded=false;
        if(loader==null&&(name.equals("java/lang/Thread")||name.equals("java/util/Timer")||name.equals("java/util/TimerThread")))boundary(name,false);
    }
    /** This receipt describes only the image our last Java transformer actually returned. */
    private static void platformReturned(ClassLoader loader,String name,Class<?> actual,byte[] returned){
        if(EventFaultBoundary.TARGET.equals(name)){
            boolean valid=false;
            try{valid=returned!=null&&EventFaultBoundary.verify(returned);}catch(RuntimeException|LinkageError unavailable){}
            EventFaultImage image=new EventFaultImage(new java.lang.ref.WeakReference<>(actual),actual==null,valid,false);
            eventFaultImages.put(loader,image);eventFaultReturns.get().put(loader,image);
        }
        if(!platformGuardTarget(loader,name))return;
        boolean valid=returned!=null&&returned.length>=4&&returned[0]==(byte)0xca&&returned[1]==(byte)0xfe
                &&returned[2]==(byte)0xba&&returned[3]==(byte)0xbe;
        if(valid){
            if(loader==null&&JdkTaskBoundaryContract.registered(name))valid=installed.contains(name)
                    &&(!name.equals("java/util/concurrent/ThreadPoolExecutor")&&!name.equals("java/util/concurrent/ScheduledThreadPoolExecutor")||BackingBoundary.installed(name));
            else if(HandleWriteBoundary.targets().contains(name))valid=HandleWriteBoundary.installed(name);
            else if(name.equals("java/lang/Thread"))valid=ThreadBoundary.installed();
            else if(name.equals("java/util/Timer")||name.equals("java/util/TimerThread"))valid=TimerBoundary.installed(name);
            else if(name.equals("java/lang/reflect/Field"))valid=ReflectionWriteBoundary.guardedCount()==9;
            else if(name.equals("sun/misc/Unsafe"))valid=UnsafeWriteBoundary.entries()==31;
        }
        PlatformImage image=new PlatformImage(actual,valid,false);
        platformImages.put(name,image);platformReturns.get().put(name,image);
    }
    private static void eventFaultAccepted(Class<?> actual){
        if(actual==null||!actual.getName().equals("net.minecraftforge.eventbus.EventBus"))return;
        ClassLoader loader=actual.getClassLoader();EventFaultImage returned=eventFaultReturns.get().remove(loader);
        synchronized(eventFaultImages){
            EventFaultImage current=eventFaultImages.get(loader);
            // A first definition may prepare on another thread. An existing
            // Class's replacement always needs that API thread's own return.
            if(returned==null&&current!=null&&current.initial())returned=current;
            if(returned==null||returned!=current||!returned.valid()
                    ||!returned.initial()&&returned.actual().get()!=actual)return;
            eventFaultImages.put(loader,new EventFaultImage(new java.lang.ref.WeakReference<>(actual),false,true,true));
            if(clientEventBus==null||clientEventBus==actual)eventFaultGuarded=true;
        }
    }
    /** A successful API return or exact ClassPrepare consumes this thread's returned-image receipt. */
    private static void platformAccepted(Class<?> actual){
        if(actual==null)return;String name=actual.getName().replace('.','/');ClassLoader loader=actual.getClassLoader();
        if(EventFaultBoundary.TARGET.equals(name))eventFaultAccepted(actual);
        if(!platformGuardTarget(loader,name))return;
        PlatformImage returned=platformReturns.get().remove(name);
        // ClassPrepare can run on another thread after a load returned. Only a
        // first-definition receipt may cross threads: this exact platform loader
        // and name can define one Class, and an unprepared class cannot be
        // retransformed. A retransform receipt always needs its own API thread.
        if(returned==null){
            PlatformImage initial=platformImages.get(name);
            if(initial!=null&&initial.actual()==null)returned=initial;
        }
        if(returned==null||!returned.valid()||returned.actual()!=null&&returned.actual()!=actual)return;
        PlatformImage accepted=new PlatformImage(actual,true,true);
        if(!platformImages.replace(name,returned,accepted))return;
        if(name.equals("java/lang/reflect/Field"))reflectionGuarded=true;
        if(name.equals("sun/misc/Unsafe"))unsafeGuarded=true;
        if(taskReadinessTarget(loader,name))boundary(name,true);
    }
    private static boolean loadedTaskBoundariesReady(){
        for(var entry:platformImages.entrySet())if(JdkTaskBoundaryContract.registered(entry.getKey())&&!entry.getValue().accepted())return false;
        return true;
    }
    private static boolean loadedHandleBoundariesReady(){
        for(String name:HandleWriteBoundary.targets()){
            PlatformImage image=platformImages.get(name);if(image!=null&&!image.accepted())return false;
        }return true;
    }
    private static boolean loadedExecutorBoundariesReady(){
        for(String name:List.of("java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ScheduledThreadPoolExecutor")){
            PlatformImage image=platformImages.get(name);if(image!=null&&!image.accepted())return false;
        }return true;
    }
    private static void platformRejected(Class<?> actual){
        if(actual!=null&&actual.getName().equals("net.minecraftforge.eventbus.EventBus")){
            ClassLoader loader=actual.getClassLoader();EventFaultImage returned=eventFaultReturns.get().remove(loader);
            synchronized(eventFaultImages){if(returned!=null&&eventFaultImages.get(loader)==returned)
                eventFaultImages.put(loader,new EventFaultImage(new java.lang.ref.WeakReference<>(actual),false,false,false));}
            eventFaultGuarded=false;
        }
        String name=actual.getName().replace('.','/');if(!platformGuardTarget(actual.getClassLoader(),name))return;
        PlatformImage returned=platformReturns.get().remove(name);
        if(returned!=null)platformImages.replace(name,returned,new PlatformImage(actual,false,false));
    }
    private static void retransformStartup(Instrumentation api,List<Class<?>> targets,String phase,boolean required)throws UnmodifiableClassException{
        if(targets.isEmpty())return;
        Class<?>[] actuals=targets.toArray(Class<?>[]::new);
        try{
            api.retransformClasses(actuals);for(Class<?> actual:actuals)platformAccepted(actual);
        }catch(UnmodifiableClassException|RuntimeException|LinkageError failure){
            for(Class<?> actual:actuals)platformRejected(actual);
            if(required)throw failure;
            // A rejected batch installs none of its images. Isolate only that real
            // failure so the other already-loaded targets still receive their guards.
            if(actuals.length>1){
                for(Class<?> actual:actuals)retransformStartup(api,List.of(actual),phase,false);
            }else{
                if(actuals[0].getName().replace('.','/').equals(ConsoleBoundary.TARGET))ConsoleBoundary.unavailable(failure.getClass().getSimpleName());
                report(phase+"_RETRANSFORM_UNAVAILABLE:"+actuals[0].getName()+":"+failure.getClass().getSimpleName());
            }
        }
    }
    /** The honest native backend state for callers that report capability. */
    public static String nativeBindingState() {
        try { return String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("state").invoke(null)); }
        catch(ReflectiveOperationException unavailable) { return NativeBindingBoundary.backendState(); }
    }
    public static String fileBoundaryState(){
        try{return String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("fileBoundaryInstalled").invoke(null));}
        catch(ReflectiveOperationException unavailable){return "NATIVE_FILE_BINDINGS_UNAVAILABLE";}
    }
    /** Whether the reflection field-write guard is installed on this JVM. */
    public static boolean unsafeFieldWriteGuarded() { return unsafeGuarded; }
    public static boolean reflectionFieldWriteGuarded() { return reflectionGuarded; }
    /** Exact guard state, including how many write entries carry it. */
    public static String reflectionGuardState() { return ReflectionWriteBoundary.state(); }
    public static String indexGuardState() {
        return "JDK_CONTAINER_HOOKS_REMOVED;FASTUTIL="+FastutilIndexBoundary.states();
    }
    public static void installIndexBoundaries() {
        Instrumentation api=instrumentation;if(api==null)return;
        for(Class<?> type:loadedClasses())if(FastutilIndexBoundary.targets().contains(type.getName().replace('.','/'))
                ||SectionKeyBoundary.targets().contains(type.getName().replace('.','/'))) {
            try { if(api.isModifiableClass(type))api.retransformClasses(type); }
            catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                report("FASTUTIL_INDEX_RETRANSFORM_UNAVAILABLE:"+type.getName()+":"+failure.getClass().getSimpleName());
            }
        }
    }
    /** Declares an entry point whose receiver must be checked before the original body runs. */
    public static boolean declareDispatchTarget(Class<?> owner,String methodName,String descriptor) {
        controlImages.declare(owner);
        rememberContract(owner,methodName,descriptor);
        boolean added=DispatchBoundary.declare(owner,methodName,descriptor);
        Instrumentation api=instrumentation;
        // Installation is batched after all exact defining classes have been declared.
        return added;
    }
    /** Keeps the actual argument tuple for equipment slots and extraction results. */
    public static boolean declareDispatchArgumentsTarget(Class<?> owner,String methodName,String descriptor) {
        controlImages.declare(owner);
        rememberContract(owner,methodName,descriptor);
        return DispatchBoundary.declare(owner,methodName,descriptor,true);
    }
    /** Full invocation lifetime for exact equipment action contracts and their real overrides. */
    public static boolean declareEquipmentActionTarget(Class<?> owner,String methodName,String descriptor,String operation) {
        Class<?> caller=CALLER.getCallerClass(),runtime=modGroupRuntime;
        if(runtime==null||!caller.getName().equals("dev.ronova.pro.EquipmentPolicy")||caller.getClassLoader()!=runtime.getClassLoader()
                ||caller.getModule()!=runtime.getModule()||!Objects.equals(caller.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource()))throw new SecurityException("EQUIPMENT_ACTION_DECLARATION_OWNER");
        controlImages.declare(owner);
        rememberContract(owner,methodName,descriptor);
        return EquipmentActionBoundary.declare(owner,methodName,descriptor,operation);
    }
    /** Called only for a class reached through the current equipment data graph. */
    public static void declareEquipmentCarrier(Class<?> actual) {
        Class<?> caller=CALLER.getCallerClass(),runtime=modGroupRuntime;
        if(runtime==null||!(caller.getName().equals("dev.ronova.pro.EquipmentPolicy")||caller.getName().equals("dev.ronova.pro.EquipmentCarrierGraph"))
                ||caller.getClassLoader()!=runtime.getClassLoader()||caller.getModule()!=runtime.getModule()
                ||!Objects.equals(caller.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource()))
            throw new SecurityException("EQUIPMENT_CARRIER_DECLARATION_OWNER");
        Objects.requireNonNull(actual);Instrumentation api=instrumentation;
        if(api==null)throw new IllegalStateException("EQUIPMENT_CARRIER_AGENT_UNAVAILABLE");
        controlImages.declare(actual);
        EquipmentActionBoundary.installCarrier(api,actual);
    }
    public static void declareEquipmentSlotGetter(Class<?> actual,String method,String descriptor){
        Class<?> caller=CALLER.getCallerClass(),runtime=modGroupRuntime;
        if(runtime==null||!caller.getName().equals("dev.ronova.pro.EquipmentPolicy")||caller.getClassLoader()!=runtime.getClassLoader()
                ||caller.getModule()!=runtime.getModule()||!Objects.equals(caller.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource()))
            throw new SecurityException("EQUIPMENT_SLOT_DECLARATION_OWNER");
        Objects.requireNonNull(actual);controlImages.declare(actual);EquipmentSlotBoundary.install(instrumentation,actual,method,descriptor);
    }
    public static void declareEquipmentSlotHelper(Class<?> actual,String method,String descriptor){
        Class<?> caller=CALLER.getCallerClass(),runtime=modGroupRuntime;
        if(runtime==null||!caller.getName().equals("dev.ronova.pro.EquipmentPolicy")||caller.getClassLoader()!=runtime.getClassLoader()
                ||caller.getModule()!=runtime.getModule()||!Objects.equals(caller.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource()))
            throw new SecurityException("EQUIPMENT_SLOT_HELPER_DECLARATION_OWNER");
        Objects.requireNonNull(actual);controlImages.declare(actual);EquipmentSlotBoundary.installHelper(instrumentation,actual,method,descriptor);
    }
    public static void declareEquipmentSlotKey(Class<?> actual,String method,String descriptor){
        Class<?> caller=CALLER.getCallerClass(),runtime=modGroupRuntime;
        if(runtime==null||!caller.getName().equals("dev.ronova.pro.EquipmentPolicy")||caller.getClassLoader()!=runtime.getClassLoader()
                ||caller.getModule()!=runtime.getModule()||!Objects.equals(caller.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource()))
            throw new SecurityException("EQUIPMENT_SLOT_KEY_DECLARATION_OWNER");
        Objects.requireNonNull(actual);controlImages.declare(actual);EquipmentSlotBoundary.installKey(instrumentation,actual,method,descriptor);
    }
    /** The declaration state, so a caller can tell "covered" from "nothing declared yet". */
    public static String dispatchState() {
        return "DECLARED_TARGETS:"+DispatchBoundary.declaredTargets()+":TRANSFORMED:"+DispatchBoundary.transformedEntries().size();
    }
    private static volatile Class<?> modGroupRuntime;
    /** Binds control entry points to the exact classes loaded by Forge for this Ronova instance. */
    public static synchronized void bindModGroupCallers(Class<?> mod,Class<?> runtime) {
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=mod||mod==null||runtime==null||!mod.getName().equals("dev.ronova.pro.ProMod")
                ||!runtime.getName().equals("dev.ronova.pro.ProRuntime")
                ||mod.getClassLoader()!=runtime.getClassLoader()||mod.getModule()!=runtime.getModule()
                ||!"ronova_pro".equals(mod.getModule().getName())
                ||!java.util.Objects.equals(mod.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource())
                ||modGroupRuntime!=null&&modGroupRuntime!=runtime)throw new SecurityException("MOD_GROUP_OWNER_BINDING");
        modGroupRuntime=runtime;
        try{
            Class<?> fault=Class.forName("dev.ronova.pro.bootstrap.EventFaultBridge",true,null);
            fault.getMethod("bindCore",Class.class).invoke(null,runtime);
            Class<?> bus=Class.forName("net.minecraftforge.eventbus.EventBus",false,runtime.getClassLoader());
            clientEventBus=bus;
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("installEventBoundary",Class.class).invoke(null,bus);
            Instrumentation api=instrumentation;
            if(api!=null&&!eventFaultGuarded(bus)&&api.isModifiableClass(bus)){api.retransformClasses(bus);eventFaultAccepted(bus);}
        }catch(ReflectiveOperationException|UnmodifiableClassException unavailable){throw new IllegalStateException("CLIENT_EVENT_DISPATCH_BINDING",unavailable);}
    }
    /** Fences task and callback publication before current body cleanup starts. */
    public static String prepareModGroup(String[] ids,Module[] modules) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.prepare(instrumentation,ids,modules,"default");
    }
    public static String prepareModGroup(String[] ids,Module[] modules,String returns) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.prepare(instrumentation,ids,modules,returns);
    }
    /** Stop selected Forge modules, including already loaded methods and future definitions. */
    public static String[] modGroupIds(Module module) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.ids(module).toArray(String[]::new);
    }
    public static String stopModGroup(String[] ids,Module[] modules) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.stop(instrumentation,ids,modules,"default");
    }
    public static String stopModGroup(String[] ids,Module[] modules,String returns) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.stop(instrumentation,ids,modules,returns);
    }
    public static void beginModGroupBatch() {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");ModGroupBoundary.beginBatch(instrumentation);
    }
    public static void endModGroupBatch() {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");ModGroupBoundary.endBatch();
    }
    public static String stopModIds(String[] ids) {
        requireModGroupCaller("dev.ronova.pro.ClientPresence");
        return ModGroupBoundary.stopByIds(instrumentation,ids,"default");
    }
    public static String stopModIds(String[] ids,String returns) {
        requireModGroupCaller("dev.ronova.pro.ClientPresence");
        return ModGroupBoundary.stopByIds(instrumentation,ids,returns);
    }
    public static String modGroupState(Module module) { return ModGroupBoundary.state(module); }
    public static String modGroupState() { return ModGroupBoundary.state()+";"+EventBusBoundary.state()
            +";COMMAND_GATE="+(CommandBoundary.installed()?"INSTALLED":"NOT_INSTALLED"); }
    private static void requireModGroupCaller(String expected) {
        Class<?> caller=CALLER
                .walk(frames->frames.skip(2).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        Class<?> runtime=modGroupRuntime;
        if(runtime==null||caller==null)throw new SecurityException("MOD_GROUP_CONTROL_UNBOUND");
        Class<?> authorized;
        try {
            authorized=expected.equals("dev.ronova.pro.ProRuntime")?runtime:
                    Class.forName("dev.ronova.pro.ClientPresence",false,runtime.getClassLoader());
        } catch(ClassNotFoundException unavailable) {throw new SecurityException("MOD_GROUP_CLIENT_UNAVAILABLE",unavailable);}
        if(caller!=authorized||!authorized.getName().equals(expected)||authorized.getModule()!=runtime.getModule())
            throw new SecurityException("MOD_GROUP_CONTROL_CALLER");
    }
    static void publishStoppedModule(Module module) {
        ModGroupBoundary.Returns policy=ModGroupBoundary.beginPublication(module);if(policy==null)return;
        try {
            do {
                // Only this module's publisher enters Native. Reentrant callbacks leave it to drain a newer policy.
                Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null).getMethod("policy",Module.class,String.class,String.class)
                        .invoke(null,module,policy.mode(),policy.fixedUuid());
                Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("stopModules",Module[].class).invoke(null,(Object)new Module[]{module});
                Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("module",Module.class,boolean.class).invoke(null,module,true);
                policy=ModGroupBoundary.finishPublication(module,policy);
            }while(policy!=null);
        }
        catch(ReflectiveOperationException failure) {throw new IllegalStateException("MOD_TASK_GATE_PUBLICATION_FAILED",failure);}
        finally{ModGroupBoundary.failedPublication(module);}
    }
    /** Called only through the private bootstrap callback after its exact native TLS proof. */
    public static boolean nativeImageCandidate(Module module,ClassLoader loader,String name,Class<?> actual,Module[] writers){
        requireNativeImageCaller("jvmtiImageCandidate");
        if(name==null)return true;
        if(writers!=null)for(Module writer:writers)if(writer==null||producerModule(writer))return true;
        if(producerModule(module)||controlImages.critical(module,loader,name,actual)||ExternalCodeImages.recorded(loader,name)
                ||ExternalCodeDefinitions.executionSourcesObserved(loader,name)||watchedDefinition(actual)||confirmedContract(actual))return true;
        return actual!=null&&definitionOrigin(actual,loader,name)!=null;
    }
    private static void requireNativeImageCaller(String method){
        Class<?> nativeControl;
        try{nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        var caller=CALLER.walk(frames->frames.filter(frame->frame.getDeclaringClass()!=RecoveryAgent.class).findFirst().orElse(null));
        if(caller==null||caller.getDeclaringClass()!=nativeControl||!caller.getMethodName().equals(method))throw new SecurityException("ACTUAL_NATIVE_FINAL_IMAGE_CALLBACK_REQUIRED");
    }
    public static byte[] nativeFinalImage(Module module,ClassLoader loader,String name,Class<?> actual,ProtectionDomain domain,byte[] original,byte[] bytes,Module[] writers,boolean changed){
        requireNativeImageCaller("jvmtiImage");
        ClassFileTransformer transformer=nativeFinalTransformer;if(transformer==null||!changed)return bytes;
        if(original==null||bytes==null||writers==null)throw new SecurityException("ACTUAL_NATIVE_IMAGE_PAIR_REQUIRED");
        // Ordinary group-external images keep the original chain. Control carriers must
        // retain their actual guards even when a changed callback has unknown/non-Mod writers.
        boolean producer=Arrays.stream(writers).anyMatch(RecoveryAgent::producerModule);
        Module origin=actual==null?null:definitionOrigin(actual,loader,name),declaration=origin==null?module:origin;
        if(name==null&&actual!=null&&!actual.isHidden())name=actual.getName().replace('.','/');
        boolean control=controlImages.critical(module,loader,name,actual);
        boolean registered=ExternalCodeImages.recorded(loader,name)||ExternalCodeDefinitions.executionSourcesObserved(loader,name)||watchedDefinition(actual)||confirmedContract(actual);
        boolean declared=origin!=null||producerModule(declaration);
        if(!producer&&!control&&!registered&&!declared)return bytes;
        // Source ownership selects observation. Hard refusal needs an actual control contract or stopped source.
        boolean confirmed=control||ModGroupBoundary.stopped(declaration)||watchedDefinition(actual)||confirmedContract(actual)
                ||Arrays.stream(writers).anyMatch(ModGroupBoundary::stopped);
        try{
            if(name==null)name=new jdk.internal.org.objectweb.asm.ClassReader(bytes).getClassName();
            if(!name.equals(new jdk.internal.org.objectweb.asm.ClassReader(original).getClassName())||!name.equals(new jdk.internal.org.objectweb.asm.ClassReader(bytes).getClassName())){
                ExternalCodeImages.failed(writers,name+":NATIVE_FINAL_IMAGE:CLASS_NAME_CHANGED");
                return confirmed?new byte[]{0,0,0,0}:bytes;
            }
            if(producer)ExternalCodeImages.nativeEncoded(loader,name,original,bytes,writers);
            byte[] baseline=controlImages.transform(module,loader,name,actual,domain,original);
            if(baseline==null)baseline=controlImages.transform(module,loader,name,actual,domain,bytes);
            byte[] current=baseline==null?bytes:baseline;
            byte[] result=transformer.transform(module,loader,name,actual,domain,current);
            return result==null?current:result;
        }catch(Throwable failure){
            ExternalCodeImages.failed(writers,name+":NATIVE_FINAL_IMAGE:"+failure.getClass().getSimpleName());
            if(confirmed&&declaration!=null)ModGroupBoundary.failed(declaration,String.valueOf(name),failure);
            return confirmed?new byte[]{0,0,0,0}:bytes;
        }
    }
    public static byte[] transformDefined(Module module,ClassLoader loader,byte[] bytes,boolean hidden) {
        Class<?> caller=CALLER.getCallerClass();
        try {if(caller!=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null))throw new SecurityException("ACTUAL_DEFINITION_BRIDGE_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        if(!producerModule(module))return bytes;
        String name=null;
        try {
        if(!module.canRead(bridge.getModule()))instrumentation.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
        if(loader!=null&&!loader.getUnnamedModule().canRead(bridge.getModule()))
            instrumentation.redefineModule(loader.getUnnamedModule(),Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
        name=new jdk.internal.org.objectweb.asm.ClassReader(bytes).getClassName();
        ExternalCodeRuntime.Source source=ExternalCodeRuntime.prepare(module,loader,name,null,bytes,hidden);
        byte[] current=DefinitionBoundary.generated(loader,source.bytes());
        byte[] fields=FieldWriteBoundary.transform(loader,module,current);if(fields!=null)current=fields;
        NativeBindingBoundary.observe(name,bytes);
        return ExternalCodeRuntime.finish(loader,name,null,source,current);
        } catch(RuntimeException|LinkageError failure) {
            boolean control=controlImages.critical(module,loader,name,null);
            return failedTransform(module,name,bytes,failure,control,control||ModGroupBoundary.stopped(module));
        }
    }
    public static void externalDefinitionAccepted(Class<?> actual,byte[] bytes){
        Class<?> caller=CALLER.getCallerClass();
        try{if(caller!=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null))throw new SecurityException("ACTUAL_DEFINITION_RESULT_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        if(actual!=null&&producerModule(logicalModule(actual)))ExternalCodeDefinitions.defined(actual,bytes);
    }
    private static volatile Class<?> definitionBridge;
    private static volatile Class<?> codeSourceBridge;
    /** Called only when the real module layer or reader was observed by the bootstrap bridge. */
    public static void sourceModuleRead(Module module) {
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=codeSourceBridge)throw new SecurityException("ACTUAL_CODE_SOURCE_BRIDGE_REQUIRED");
        if(producerModule(module)) {registerProducer(module);ExternalCodeDefinitions.moduleObserved(module);if(ModGroupBoundary.stopped(module))publishStoppedModule(module);}
    }
    private static void requireCodeSourceCaller(){
        Class<?> caller=CALLER.walk(frames->frames.skip(2).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
        if(caller!=codeSourceBridge)throw new SecurityException("ACTUAL_CODE_SOURCE_BRIDGE_REQUIRED");
    }
    public static byte[] externalTreeImage(Object tree){requireCodeSourceCaller();return ExternalCodeImages.image(tree);}
    public static void externalTreeObserved(Object tree,Module module){requireCodeSourceCaller();ExternalCodeImages.observed(tree,module);}
    public static Object externalCodeBegin(Object tree,Module[] owners,Module[] writers){requireCodeSourceCaller();return ExternalCodeImages.begin(tree,owners,writers);}
    public static void externalCodeEnd(Object token,Object tree,Module[] consumers){requireCodeSourceCaller();ExternalCodeImages.end(token,tree,consumers);}
    public static void externalTreeCopied(Object tree,Object visitor){requireCodeSourceCaller();ExternalCodeImages.copied(tree,visitor);}
    public static Module[] externalCodeEncoded(Object writer,byte[] bytes){requireCodeSourceCaller();return ExternalCodeImages.encoded(writer,bytes);}
    public static void externalMixinDependency(Object tree,Module origin,Module[] contributors){requireCodeSourceCaller();ExternalCodeImages.mixinDependency(tree,origin,contributors);}
    public static Object[] externalMixinPayload(Object tree,Module origin,Module[] contributors,boolean bodyOnly){requireCodeSourceCaller();return ExternalCodeImages.mixinPayload(tree,origin,contributors,bodyOnly);}
    public static Module[] externalMixinSelection(Object tree,Module origin,Module[] contributors){requireCodeSourceCaller();return ExternalCodeImages.mixinSelection(tree,origin,contributors);}
    public static Map<String,Module[]> externalMixinMetadata(Object tree,Module origin,Module[] contributors){requireCodeSourceCaller();return ExternalCodeImages.mixinMetadata(tree,origin,contributors);}
    public static Object[] externalMemberMetadata(Object member){requireCodeSourceCaller();return ExternalCodeImages.memberMetadata(member);}
    public static void externalClassPrepared(Class<?> actual){requireCodeSourceCaller();platformAccepted(actual);ExternalCodeDefinitions.prepared(actual);}
    public static void externalDefinitionsChanged(Class<?>[] actuals){
        Class<?> caller=CALLER.getCallerClass();
        try{if(caller!=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null))throw new SecurityException("ACTUAL_INSTRUMENTATION_COMPLETION_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        for(Class<?> actual:actuals){platformAccepted(actual);ExternalCodeDefinitions.changed(actual);}
    }
    static String[] externalCodeVersion(Class<?> actual,byte[] bytes){
        if(CALLER.getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{return (String[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeVersion",Class.class,byte[].class).invoke(null,actual,bytes);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_VERSION_UNAVAILABLE",failure);}
    }
    static String[] externalCodeMethodVersion(Class<?> actual,byte[] bytes,String method){
        if(CALLER.getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{return (String[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeMethodVersion",Class.class,byte[].class,String.class).invoke(null,actual,bytes,method);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_VERSION_UNAVAILABLE",failure);}
    }
    static byte[] externalCodeDigest(byte[] bytes){
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=ExternalCodeImages.class&&caller!=RecoveryAgent.class)throw new SecurityException("ACTUAL_CODE_IMAGE_REGISTRY_REQUIRED");
        try{return (byte[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("agentCodeDigest",byte[].class).invoke(null,(Object)bytes);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_IMAGE_DIGEST_UNAVAILABLE",failure);}
    }
    static String[] externalCodeDeclarations(Class<?> actual){
        if(CALLER.getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{return (String[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeDeclarations",Class.class).invoke(null,actual);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_DECLARATIONS_UNAVAILABLE",failure);}
    }
    static void publishExternalDefinition(ClassLoader loader,String name,byte[] bytes,boolean hidden){
        if(CALLER.getCallerClass()!=ExternalCodeRuntime.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_WEAVER_REQUIRED");
        try{Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeLayout",ClassLoader.class,String.class,byte[].class,String[].class,Module[][][].class,boolean.class)
                .invoke(null,loader,name,bytes,new String[0],new Module[0][][],hidden);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_DEFINITION_VM_PUBLICATION_FAILED",failure);}
    }
    static void protectExternalCode(Object[] objects){
        Class<?> caller=CALLER.getCallerClass();
        if(caller.getNestHost()!=ExternalCodeImages.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_LEDGER_REQUIRED");
        try{codeSourceBridge.getMethod("codeControls",Object[].class).invoke(null,(Object)objects);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_CODE_LEDGER_PROTECTION_FAILED",failure);}
    }
    static Object[][] imageControlFields(Object[] holders,java.lang.reflect.Field[][] fields){
        if(CALLER.getCallerClass()!=ExternalCodeImages.class)
            throw new SecurityException("ACTUAL_EXTERNAL_CODE_LEDGER_REQUIRED");
        try{return (Object[][])imageControlReader.invoke(null,holders,fields);}
        catch(java.lang.reflect.InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException error)throw error;if(cause instanceof Error error)throw error;
            throw new IllegalStateException("EXTERNAL_IMAGE_CONTROL_CAPTURE_FAILED",cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_IMAGE_CONTROL_CAPTURE_FAILED",failure);}
    }
    static Object externalExecutionPlan(Object[][] rows){
        if(CALLER.getCallerClass()!=ExternalCodeRuntime.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_WEAVER_REQUIRED");
        try{return codeSourceBridge.getMethod("executionPlan",Object[][].class).invoke(null,(Object)rows);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_EXECUTION_LAYOUT_PUBLICATION_FAILED",failure);}
    }
    static void publishExternalCode(ClassLoader loader,String name,Class<?> actual,byte[] bytes,String[] methods,Module[][][] owners,Map<String,Module[]> fields,Object[][] values,boolean hidden,Object execution,Module declaration){
        Class<?> caller=CALLER.getCallerClass();
        if(caller!=ExternalCodeRuntime.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_WEAVER_REQUIRED");
        try{
            Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
            boolean installed=Boolean.TRUE.equals(nativeControl.getMethod("codeLayout",ClassLoader.class,String.class,byte[].class,String[].class,Module[][][].class,boolean.class,Object.class).invoke(null,loader,name,bytes,methods,owners,hidden,execution));
            if(!hidden){
                codeSourceBridge.getMethod("codeMethods",ClassLoader.class,String.class,String[].class).invoke(null,loader,name.replace('/','.'),methods);
                codeSourceBridge.getMethod("codeFields",ClassLoader.class,String.class,String[].class,Module[][].class).invoke(null,loader,name.replace('/','.'),fields.keySet().toArray(String[]::new),fields.values().toArray(Module[][]::new));
                codeSourceBridge.getMethod("codeValues",ClassLoader.class,String.class,Object[][].class).invoke(null,loader,name.replace('/','.'),(Object)values);
            }
            if(actual!=null){controlImages.declare(actual);if(!fields.isEmpty()||values.length!=0)codeSourceBridge.getMethod("codeClass",Class.class).invoke(null,actual);}
            if(!installed)for(Module[][] method:owners)for(Module[] site:method)for(Module module:site)ModGroupBoundary.externalGap(module,name+":EXTERNAL_CODE_VM_BACKEND_UNAVAILABLE");
            if(!installed&&execution!=null&&producerModule(declaration))ModGroupBoundary.externalGap(declaration,name+":EXTERNAL_EXECUTION_VM_BACKEND_UNAVAILABLE");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_CODE_VM_LAYOUT_PUBLICATION_FAILED",failure);}
    }
    static void bindExternalDefinition(Class<?> actual,byte[] bytes,String[] methods,Map<String,Module[]> fields,Object[][] values,String[] creationSites){
        if(CALLER.getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{
            boolean installed=Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeDefinition",Class.class,byte[].class).invoke(null,actual,bytes));
            codeSourceBridge.getMethod("codeMethods",Class.class,String[].class).invoke(null,actual,methods);
            codeSourceBridge.getMethod("codeFields",Class.class,String[].class,Module[][].class).invoke(null,actual,fields.keySet().toArray(String[]::new),fields.values().toArray(Module[][]::new));
            codeSourceBridge.getMethod("codeValues",Class.class,Object[][].class).invoke(null,actual,(Object)values);
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("registerDefinitionCreationSites",Class.class,String[].class).invoke(null,actual,(Object)creationSites);
            controlImages.declare(actual);if(!fields.isEmpty()||values.length!=0)codeSourceBridge.getMethod("codeClass",Class.class).invoke(null,actual);
            if(!installed)ModGroupBoundary.externalGap(logicalModule(actual),actual.getName()+":EXTERNAL_HIDDEN_CODE_VM_BACKEND_UNAVAILABLE");
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_HIDDEN_DEFINITION_PUBLICATION_FAILED",failure);}
    }
    public static Object[][] externalGroupFields(Class<?> core,Module[] modules){
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        if(core!=modGroupRuntime)throw new SecurityException("GROUP_FIELD_CORE_REQUIRED");
        Class<?>[] loaded=loadedClasses();
        try{
            List<Class<?>> ready=new java.util.ArrayList<>();Class<?> unsafeType=Class.forName("sun.misc.Unsafe");var singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
            var pending=unsafeType.getMethod("shouldBeInitialized",Class.class);for(Class<?> type:loaded)if(!type.isArray()&&!type.isPrimitive()&&!Boolean.TRUE.equals(pending.invoke(unsafe,type)))ready.add(type);
            Object[][] rows=(Object[][])codeSourceBridge.getMethod("groupFields",Module[].class,Class[].class).invoke(null,(Object)modules,(Object)ready.toArray(Class[]::new));
            openBackingAccess(core,Arrays.stream(rows).map(row->((java.lang.reflect.Field)row[0]).getDeclaringClass()).distinct().toArray(Class[]::new));return rows;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_GROUP_FIELDS_UNAVAILABLE",unavailable);}
    }
    public static Object[][] externalObservedGroupFields(Module[] modules){
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        try{
            Class<?>[] classes=(Class<?>[])codeSourceBridge.getMethod("fieldClasses").invoke(null);List<Class<?>> ready=new ArrayList<>();
            Class<?> unsafeType=Class.forName("sun.misc.Unsafe");var singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);var pending=unsafeType.getMethod("shouldBeInitialized",Class.class);
            for(Class<?> type:classes)if(!Boolean.TRUE.equals(pending.invoke(unsafe,type)))ready.add(type);
            return (Object[][])codeSourceBridge.getMethod("groupFields",Module[].class,Class[].class).invoke(null,(Object)modules,(Object)ready.toArray(Class[]::new));
        }
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_GROUP_FIELDS_UNAVAILABLE",unavailable);}
    }
    public static Object[][] externalObservedGroupHeapValues(Module[] modules){
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        try{return (Object[][])codeSourceBridge.getMethod("groupHeapValues",Module[].class).invoke(null,(Object)modules);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("EXTERNAL_GROUP_HEAP_VALUES_UNAVAILABLE",unavailable);}
    }
    private static Module definitionOrigin(Class<?> type,ClassLoader loader,String name) {
        Class<?> actual=definitionBridge;if(actual==null)return null;
        try {return (Module)(type==null?actual.getMethod("definingModule",ClassLoader.class,String.class).invoke(null,loader,name)
                :actual.getMethod("origin",Class.class).invoke(null,type));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_DEFINITION_ORIGIN_UNAVAILABLE",failure);}
    }
    public static String consoleBoundaryState() {return ConsoleBoundary.state();}
    public static boolean dispatchReady() {return DispatchBoundary.primaryCoverageComplete();}
    public static void installDeclaredDispatch() {
        Instrumentation api=instrumentation;
        if(api==null)throw new IllegalStateException("AGENT_NOT_INSTALLED");
        DispatchBoundary.retransformDeclared(api);
        EquipmentActionBoundary.retransformDeclared(api);
        var missing=DispatchBoundary.missingPrimaryEntries();
        if(!missing.isEmpty())report("DISPATCH_PRIMARY_UNCOVERED:"+String.join(",",missing));
    }
    /** Opens only task introspection packages to the actual Core module, including named Forge modules. */
    public static boolean openTaskAccess(Class<?> core) {
        Instrumentation api=instrumentation;if(api==null)return false;
        api.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of(
                "java.util.concurrent",Set.of(core.getModule())),Set.of(),Map.of());
        return true;
    }
    private static final Map<Module,Boolean> producerModules=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static boolean watchedDefinition(Class<?> type){synchronized(definitions){return type!=null&&watching.contains(type);}}
    private static void rememberContract(Class<?> owner,String method,String descriptor){
        Objects.requireNonNull(owner);Objects.requireNonNull(method);Objects.requireNonNull(descriptor);
        for(;;){
            CandidateContract[] before=candidateContracts;
            for(CandidateContract entry:before)if(entry.owner()==owner&&entry.method().equals(method)&&entry.descriptor().equals(descriptor))return;
            CandidateContract entry=new CandidateContract(owner,method,descriptor);CandidateContract[] next=Arrays.copyOf(before,before.length+1);next[before.length]=entry;
            ControlImages.protect(next,entry);
            synchronized(RecoveryAgent.class){if(candidateContracts!=before)continue;candidateContracts=next;return;}
        }
    }
    private static boolean confirmedContract(Class<?> actual){
        if(actual!=null)for(CandidateContract entry:candidateContracts)if(entry.owner().isAssignableFrom(actual))return true;
        return false;
    }
    private static boolean contractCandidate(ClassLoader loader,String name,Class<?> actual,ExternalCodeDefinitions.ReferenceHeader references){
        if(confirmedContract(actual))return true;
        for(CandidateContract entry:candidateContracts){
            if(entry.owner().getClassLoader()==loader&&entry.owner().getName().replace('.','/').equals(name))return true;
            if(references!=null&&references.declarations().containsKey(entry.method()+entry.descriptor()))return true;
        }
        return false;
    }
    /** Eligibility only: exact boundaries and cheap constant-pool byte screens precede ASM. */
    private static boolean boundaryCandidate(ClassLoader loader,String name,byte[] bytes){
        if(name==null)return false;
        if(EventBusBoundary.TARGET.equals(name)||CommandBoundary.TARGET.equals(name)||EventFaultBoundary.TARGET.equals(name)
                ||CodeSourceBoundary.TARGETS.contains(name)||NetworkBoundary.TARGETS.contains(name)||ClientBoundary.TARGETS.contains(name)
                ||BufferBoundary.wrapTarget(name)||FastutilIndexBoundary.targets().contains(name)||SectionKeyBoundary.targets().contains(name)
                ||HandleWriteBoundary.targets().contains(name)||"sun/misc/Unsafe".equals(name)||"java/lang/reflect/Field".equals(name))return true;
        if(loader==null)return ResourceBoundary.TARGETS.contains(name)||ResourceBoundary.CHARACTER_WRAPPERS.contains(name)
                ||IoBoundary.TARGETS.contains(name)||BufferBoundary.TARGETS.contains(name)||NativeLibraryBoundary.TARGETS.contains(name)
                ||EquipmentFunctionBoundary.target(name)||Set.of("java/util/Optional","java/lang/Thread","java/util/Timer","java/util/TimerThread",
                    "java/lang/ClassLoader","java/lang/ref/Reference","jdk/internal/misc/Unsafe","sun/instrument/TransformerManager","sun/instrument/InstrumentationImpl",
                    "java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ScheduledThreadPoolExecutor").contains(name);
        return name.startsWith("net/minecraft/")||directEntityReference(bytes)||mutationCandidate(bytes)
                ||containsAscii(bytes,"clone")&&containsAscii(bytes,"()Ljava/lang/Object;");
    }
    private static boolean mutationCandidate(byte[] bytes){
        return containsAscii(bytes,"java/lang/reflect/Array")||containsAscii(bytes,"java/lang/reflect/Field")
                ||containsAscii(bytes,"java/lang/reflect/Method")||containsAscii(bytes,"sun/misc/Unsafe")||containsAscii(bytes,"jdk/internal/misc/Unsafe");
    }
    static void promoteExternalDefinition(Class<?> actual)throws UnmodifiableClassException{
        if(CALLER.getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        Instrumentation api=instrumentation;
        if(api==null||!api.isModifiableClass(actual))throw new UnmodifiableClassException(actual.getName());
        Class<?> previous=externalPromotion.get();externalPromotion.set(actual);
        try{api.retransformClasses(actual);}
        finally{if(previous==null)externalPromotion.remove();else externalPromotion.set(previous);}
    }
    /** A helper's module metadata is not an authority for skipping its direct Minecraft writes. */
    private static boolean directEntityReference(byte[] bytes) {
        return containsAscii(bytes,"net/minecraft/world/entity/")
                ||containsAscii(bytes,"net/minecraft/world/level/entity/")
                ||containsAscii(bytes,"net/minecraft/network/syncher/SynchedEntityData")
                ||containsAscii(bytes,"net/minecraft/server/level/ServerLevel");
    }
    /** Auxiliary writers need their real write boundary even when their module
     * has no mod metadata and their arguments are declared only as Object. This
     * is an eligibility screen, never evidence of ownership or a new policy. */
    private static boolean directMutationReference(byte[] bytes) {
        boolean candidate=mutationCandidate(bytes);
        if(!candidate)return false;
        // Load the ASM visitor only after install has exported its package.
        // Premain's own declared-method lookup must remain free of ASM parents.
        return BackingBoundary.actualMutationCalls(bytes);
    }
    private static boolean containsAscii(byte[] bytes,String value) {
        byte[] needle=value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:for(int i=0;i<=bytes.length-needle.length;i++) {
            if(bytes[i]!=needle[0])continue;
            for(int j=1;j<needle.length;j++)if(bytes[i+j]!=needle[j])continue outer;
            return true;
        }
        return false;
    }
    static boolean producerModule(Module module) {
        if(module==null)return false;
        if(Boolean.TRUE.equals(producerModules.get(module)))return true;
        // Forge may expose a module before its reader/metadata is ready. Only a
        // positive observation is durable; a missing early resource is retried
        // after the real layer/reader callback instead of becoming a lifetime veto.
        Set<String> owners=ModGroupBoundary.ids(module);
        if(owners.isEmpty()||!java.util.Collections.disjoint(owners,Set.of("ronova_pro","minecraft","forge","java")))return false;
        producerModules.put(module,Boolean.TRUE);return true;
    }
    public static void openBackingAccess(Class<?> core,Class<?>[] carriers) {
        Instrumentation api=instrumentation;if(api==null)throw new IllegalStateException("AGENT_NOT_INSTALLED");
        api.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of("java.util",Set.of(core.getModule()),"java.util.concurrent",Set.of(core.getModule()),"java.io",Set.of(core.getModule()),"sun.nio.ch",Set.of(core.getModule())),Set.of(),Map.of());
        Map<Module,Map<String,Set<Module>>> openings=new IdentityHashMap<>();
        for(Class<?> type:carriers)if(type!=null&&type.getModule()!=core.getModule())
            openings.computeIfAbsent(type.getModule(),ignored->new HashMap<>()).put(type.getPackageName(),Set.of(core.getModule()));
        for(var entry:openings.entrySet())if(entry.getKey().isNamed())
            api.redefineModule(entry.getKey(),Set.of(),Map.of(),entry.getValue(),Set.of(),Map.of());
    }
    public static String watchDefinition(Class<?> type) {
        String hidden=acceptedHiddenImage(type);if(hidden!=null)return hidden;
        Instrumentation api=instrumentation;if(api==null||!api.isModifiableClass(type))return null;
        controlImages.declare(type);
        synchronized(definitions) { if(definitions.containsKey(type))return definitions.get(type);watching.add(type); }
        try { api.retransformClasses(type); }
        catch(UnmodifiableClassException|RuntimeException|LinkageError unavailable) { synchronized(definitions) { definitions.remove(type); }return null; }
        synchronized(definitions) { return definitions.get(type); }
    }
    public static String definition(Class<?> type) {String hidden=acceptedHiddenImage(type);if(hidden!=null)return hidden;synchronized(definitions) { return definitions.get(type); } }
    private static String acceptedHiddenImage(Class<?> type){
        if(type==null||!type.isHidden()||definitionBridge==null)return null;
        try{return (String)definitionBridge.getMethod("acceptedImage",Class.class).invoke(null,type);}
        catch(ReflectiveOperationException unavailable){return null;}
    }
    private static void recordDefinition(Class<?> type,byte[] image) {
        if(type==null)return;
        synchronized(definitions) {
            if(!watching.contains(type))return;
            String hash=HexFormat.of().formatHex(externalCodeDigest(JdkTaskBoundaryContract.canonical(image)));
            definitions.put(type,hash+":"+(++definitionEpoch));
        }
    }
    private static void registerProducer(Module module) {
        try {
            Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("registerProducerModule",Module.class).invoke(null,module);
            Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("module",Module.class,boolean.class).invoke(null,module,false);
        }
        catch(ReflectiveOperationException unavailable) {throw new IllegalStateException("PRODUCER_MODULE_REGISTRY",unavailable);}
    }
    /** Actual loaded classes only. Reading static holders must never cause their initialization. */
    public static Class<?>[] initializedGroupClasses(Module[] modules) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(modules));
        var found=new ArrayList<Class<?>>();
        try {
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
            Object access=field.get(null);var pending=unsafe.getMethod("shouldBeInitialized",Class.class);
            for(Class<?> type:loadedClasses())if(!type.isArray()&&!type.isPrimitive()
                    &&selected.contains(logicalModule(type))&&!Boolean.TRUE.equals(pending.invoke(access,type)))found.add(type);
        } catch(ReflectiveOperationException unavailable) {throw new IllegalStateException("GROUP_INITIALIZATION_STATE_UNAVAILABLE",unavailable);}
        return found.toArray(Class<?>[]::new);
    }
    static Module logicalModule(Class<?> type){
        Module origin=definitionOrigin(type,type.getClassLoader(),type.getName().replace('.','/'));return origin==null?type.getModule():origin;
    }
    static Class<?>[] loadedClasses(){
        Instrumentation api=instrumentation;if(api==null)return new Class<?>[0];
        try{
            Class<?>[] snapshot=(Class<?>[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("loadedClasses").invoke(null);
            return snapshot==null?api.getAllLoadedClasses():snapshot;
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_LOADED_CLASS_QUERY_UNAVAILABLE",unavailable);}
    }
    static Class<?>[] bootstrapClasses(){
        if(CALLER.getCallerClass().getNestHost()!=ExternalCodeDefinitions.class)
            throw new SecurityException("ACTUAL_BOOTSTRAP_CLASS_QUERY_REQUIRED");
        try{return (Class<?>[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("bootstrapLookupClasses").invoke(null);}
        catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_BOOTSTRAP_CLASS_QUERY_UNAVAILABLE",unavailable);}
    }
    public static Class<?> initializedSource(String name) {
        Instrumentation api=instrumentation;if(api==null)return null;
        try {
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");
            if(!field.trySetAccessible())return null;Object access=field.get(null);
            var pending=unsafe.getMethod("shouldBeInitialized",Class.class);Class<?> found=null;
            for(Class<?> type:loadedClasses())if(type.getName().equals(name)&&!Boolean.TRUE.equals(pending.invoke(access,type))) {
                if(found!=null)return null;found=type;
            }
            return found;
        } catch(ReflectiveOperationException|RuntimeException unavailable) { return null; }
    }
    public static Class<?> loadedSource(String name) {
        Instrumentation api=instrumentation;if(api==null)return null;Class<?> found=null;
        for(Class<?> type:loadedClasses())if(type.getName().equals(name)) {
            if(found!=null)return null;found=type;
        }
        return found;
    }
    private static void report(String value) {
        System.err.println("RONOVA_PRO_TASK_AGENT "+value);
        try { if(bridge!=null)bridge.getMethod("coverage",String.class).invoke(null,value); }
        catch(ReflectiveOperationException unavailable) { System.err.println("RONOVA_PRO_TASK_BRIDGE_UNAVAILABLE "+value); }
    }
    private static void boundary(String name,boolean ready) {
        try { bridge.getMethod("boundaryState",String.class,boolean.class).invoke(null,name,ready); }
        catch(ReflectiveOperationException unavailable) { throw new IllegalStateException("TASK_BOUNDARY_PUBLICATION_FAILED",unavailable); }
    }
    public static boolean controlReady() {
        try{return controlFailure==null&&controlImages.healthy()
                &&Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("ready").invoke(null))
                &&Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("controlCaptureHealthy").invoke(null));}
        catch(ReflectiveOperationException unavailable){return false;}
    }
    private static void protectInstrumentation(Instrumentation api)throws ReflectiveOperationException {
        var roots=new ArrayList<Object>();roots.add(api);
        for(var field:api.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                &&field.getType().getName().equals("sun.instrument.TransformerManager")) {
            field.setAccessible(true);Object manager=field.get(api);if(manager==null)continue;
            var list=manager.getClass().getDeclaredField("mTransformerList");list.setAccessible(true);
            Object[] entries=(Object[])list.get(manager);boolean owned=false;
            for(Object entry:entries) {
                var transformer=entry.getClass().getDeclaredField("mTransformer");transformer.setAccessible(true);
                if(!Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null)
                        .getMethod("removalAllowed",ClassFileTransformer.class).invoke(null,transformer.get(entry)))) {roots.add(entry);owned=true;}
            }
            if(owned){roots.add(manager);roots.add(entries);}
        }
        Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("registerAgentControls",Object[].class).invoke(null,(Object)roots.toArray());
    }
}
