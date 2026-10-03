package dev.ronova.pro.agent;

import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/** Installs control inside this JVM; Forge's bundled bootstrap supports ordinary no-argument installation. */
public final class RecoveryAgent {
    private static JarFile bootstrap;
    private static final Set<String> installed=ConcurrentHashMap.newKeySet();
    /** True once the reflection field-write entry points carry the policy guard. */
    private static volatile boolean reflectionGuarded,unsafeGuarded,indexGuarded;
    private static volatile boolean eventFaultGuarded;
    public static boolean eventFaultGuarded() { return eventFaultGuarded; }

    private static volatile Instrumentation instrumentation;
    private static Class<?> bridge;
    private static Class<?> networkBridge;
    private static Class<?> clientBridge;
    private static final ControlImages controlImages=new ControlImages();
    private static volatile String controlFailure;
    private static final Map<Class<?>,String> definitions=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> watching=Collections.newSetFromMap(new WeakHashMap<>());
    private static long definitionEpoch;
    private RecoveryAgent() { }
    public static void premain(String argument,Instrumentation api) { start(argument,api,"PREMAIN"); }
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
        if(instrumentation!=null) { report("AGENT_EXISTING_STATE:"+installState);return; }
        try {
            install(argument,api);
            installState=installed.containsAll(JdkTaskBoundaryContract.targets())&&indexGuarded&&reflectionGuarded&&unsafeGuarded
                    &&HandleWriteBoundary.complete()&&BackingBoundary.coreInstalled()&&HashMapBoundary.sourceInstalled()
                    &&ConcurrentSourceBoundary.installed()&&FieldWriteBoundary.sourceCarriersInstalled()&&controlReady()?"INSTALLED_FULL":"INSTALLED_PARTIAL";
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
        String failure=controlFailure;
        return failure==null?installState:"INSTALLED_PARTIAL:TRANSFORM_FAILED:"+failure;
    }
    public static String sourceMapState() { return HashMapBoundary.sourceInstalled()&&ConcurrentSourceBoundary.installed()?"INSTALLED":"SOURCE_MAP_SCOPE_UNAVAILABLE"; }
    public static String sourcePublicationState(){
        return sourceMapState().equals("INSTALLED")&&reflectionGuarded&&unsafeGuarded&&HandleWriteBoundary.complete()
                &&FieldWriteBoundary.sourceCarriersInstalled()&&installState().equals("INSTALLED_FULL")?"INSTALLED":"SOURCE_PUBLICATION_SCOPE_UNAVAILABLE";
    }
    private static volatile String installState="NOT_ATTEMPTED";
    private static synchronized void install(String argument,Instrumentation api) throws Exception {
        if(!api.isRetransformClassesSupported())throw new IllegalStateException("RETRANSFORM_UNSUPPORTED");
            if(argument==null||argument.isBlank())throw new IllegalArgumentException("BOOTSTRAP_JAR_PATH_REQUIRED");
            if(argument.startsWith("base64:"))argument=new String(Base64.getUrlDecoder().decode(argument.substring(7)),java.nio.charset.StandardCharsets.UTF_8);
            bootstrap=new JarFile(Path.of(argument).toAbsolutePath().normalize().toFile());
            api.appendToBootstrapClassLoaderSearch(bootstrap);
            bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null);
            if(!Integer.valueOf(48).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("TASK_BRIDGE_ABI_MISMATCH");
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null);
            // A guarded Map operation must not first define its own frame while
            // libinstrument is filling that frame's class bytes through JNI.
            for(String helper:List.of("Key","Bucket","OwnerRef","Gate","Scope","Frame","ConcurrentFrame","Refused"))
                Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge$"+helper,true,null);
            for(String helper:List.of("SourcePublication","SourceIndexMutation","FastListView","FieldMutation"))
                Class.forName("dev.ronova.pro.bootstrap.TaskBridge$"+helper,true,null);
            // This new map is used from a JNI array-observation upcall. Define
            // it before that upcall can itself fill class bytes through JNI.
            Class.forName("dev.ronova.pro.bootstrap.ExecutionFlow$TraceMap",false,null);
            Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge$LedgerMap",false,null);
            Class.forName("dev.ronova.pro.bootstrap.EventFaultBridge",true,null);
            Class<?> control=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
            Module base=Object.class.getModule(),ours=RecoveryAgent.class.getModule();
            api.redefineModule(base,Set.of(bridge.getModule()),Map.of(
                    "jdk.internal.org.objectweb.asm",Set.of(ours),"jdk.internal.org.objectweb.asm.commons",Set.of(ours),
                    "jdk.internal.org.objectweb.asm.tree",Set.of(ours),"jdk.internal.org.objectweb.asm.tree.analysis",Set.of(ours)),
                    Map.of("java.util.concurrent",Set.of(bridge.getModule()),"java.lang",Set.of(bridge.getModule(),ours),
                            "java.util",Set.of(bridge.getModule()),"java.io",Set.of(bridge.getModule()),"java.net",Set.of(bridge.getModule()),
                            "java.nio",Set.of(bridge.getModule()),"jdk.internal.ref",Set.of(bridge.getModule()),
                            "sun.nio.cs",Set.of(bridge.getModule()),"sun.nio.ch",Set.of(bridge.getModule()),"jdk.internal.loader",Set.of(bridge.getModule())),Set.of(),Map.of());
            // A Forge scanning thread may run newly transformed code immediately after addTransformer.
            // Finish the bootstrap helper's access and initialization before publishing any such call site.
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge$HashNodes",true,null);
            Class.forName("dev.ronova.pro.bootstrap.BackingBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$BufferPlatform",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$CharacterWrappers",true,null);
            Class.forName("dev.ronova.pro.bootstrap.ResourceBridge$WrapperRecord",true,null);
            Class.forName("dev.ronova.pro.bootstrap.IoBridge",true,null);
            report(String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null).getMethod("install",Path.class).invoke(null,Path.of(argument))));
            instrumentation=api;
            Module instrument=Instrumentation.class.getModule();
            api.redefineModule(instrument,Set.of(bridge.getModule()),Map.of(),
                    Map.of("sun.instrument",Set.of(bridge.getModule(),ours)),Set.of(),Map.of());
            JdkTaskBoundaryContract.enable();
            ClassFileTransformer taskTransformer=new ClassFileTransformer() {
                @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
                        ProtectionDomain domain,byte[] bytes) {
                    if(loader!=null||module!=base||!JdkTaskBoundaryContract.registered(name))return null;
                    boundary(name,false);
                    byte[] transformed=JdkTaskBoundaryContract.transform(name,bytes);
                    boolean valid=JdkTaskBoundaryContract.verify(name,transformed==null?bytes:transformed);
                    if(valid)installed.add(name);else { installed.remove(name);report("PLATFORM_IMAGE_CHANGED:"+name); }
                    return transformed;
                }
            };
            ClassFileTransformer objectTransformer=new ClassFileTransformer() {
                @Override public byte[] transform(Module module,ClassLoader loader,String name,Class<?> type,
                        ProtectionDomain domain,byte[] bytes) {
                    Module origin=definitionOrigin(type,loader,name);
                    try {
                    if(origin!=null&&module!=null&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    byte[] external=ExternalCodeImages.transform(loader,name,type,bytes);
                    ExternalCodeRuntime.Source source=ExternalCodeRuntime.prepare(origin==null?module:origin,loader,name,type,external==null?bytes:external);
                    byte[] result=apply(origin==null?module:origin,loader,name,type,domain,source.bytes(),source.present());
                    if(result==null)result=source.bytes();
                    if(origin!=null)result=DefinitionBoundary.generated(loader,result,true);
                    if(source.present()||source.image()!=null)result=ExternalCodeRuntime.finish(loader,name,type,source,result);
                    recordDefinition(type,result==null?bytes:result);return result;
                    }catch(RuntimeException|LinkageError failure){
                        if(origin!=null){ModGroupBoundary.failed(origin,String.valueOf(name),failure);return new byte[]{0,0,0,0};}
                        throw failure;
                    }
                }
                private byte[] apply(Module module,ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes,boolean externalSources) {
                    try {
                    // Forge's discovery loader is active before its module resources and frame types exist.
                    // It does not own world effects; touching it here creates a false partial install.
                    byte[] stopped=ModGroupBoundary.transform(loader,module,name,type,bytes);
                    if(stopped!=null) {registerProducer(module);publishStoppedModule(module);return stopped;}
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
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(targetPackage,Set.of(bridge.getModule())),Set.of(),Map.of());}
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
                        boundary(name,false);return ThreadBoundary.transform(loader,name,bytes);
                    }
                    if("java/util/Timer".equals(name)||"java/util/TimerThread".equals(name))boundary(name,false);
                    byte[] defining=DefinitionBoundary.platform(loader,name,bytes);
                    if(defining!=null){byte[] handle=HandleWriteBoundary.transform(loader,name,defining);return handle==null?defining:handle;}
                    byte[] eventBus=EventBusBoundary.transform(name,bytes);
                    if(eventBus!=null) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return eventBus;
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
                    if(EventFaultBoundary.TARGET.equals(name)) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        byte[] guardedEvent=EventFaultBoundary.transform(loader,bytes);
                        byte[] declaredEvent=DispatchBoundary.transform(loader,name,type,guardedEvent);
                        eventFaultGuarded=true;return declaredEvent==null?guardedEvent:declaredEvent;
                    }
                    byte[] handles=HandleWriteBoundary.transform(loader,name,bytes);if(handles!=null)return handles;
                    if(loader==null&&("java/util/concurrent/ThreadPoolExecutor".equals(name)
                            ||"java/util/concurrent/ScheduledThreadPoolExecutor".equals(name)))return BackingBoundary.transform(loader,name,bytes);
                    if(loader==null&&(name.startsWith("java/util/ArrayList")||name.equals("java/util/AbstractCollection")||name.startsWith("java/util/IdentityHashMap"))) {
                        // Exact collection operations are guarded by BackingBoundary. Generic JDK
                        // field hooks also ran on lazy view caches and internal bookkeeping while
                        // TaskBridge held its helper lock, reversing the source-ledger lock order.
                        return BackingBoundary.transform(loader,name,bytes);
                    }
                    if(loader==null&&module==base&&("java/util/HashMap".equals(name)||"java/util/LinkedHashMap".equals(name)||"java/util/HashMap$Node".equals(name)||"java/util/Map".equals(name))) {
                        return HashMapBoundary.transform(loader,name,bytes);
                    }
                    if(loader==null&&"java/util/concurrent/ConcurrentHashMap".equals(name))return ConcurrentSourceBoundary.transform(loader,name,bytes);
                    if(loader==null&&FieldWriteBoundary.sourceCarrier(name))return FieldWriteBoundary.transform(loader,module,bytes);
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
                    byte[] dispatch=DispatchBoundary.transform(loader,name,type,initial);
                    byte[] current=dispatch==null?initial:dispatch;
                    if(loader==null||name==null||module==RecoveryAgent.class.getModule()
                            &&loader==RecoveryAgent.class.getClassLoader()
                            &&domain!=null&&java.util.Objects.equals(domain.getCodeSource(),RecoveryAgent.class.getProtectionDomain().getCodeSource()))return null;
                    byte[] nativeClone=CloneBoundary.transform(loader,current);
                    if(nativeClone!=null)current=nativeClone;
                    boolean declaredSource=type!=null&&watching.contains(type);
                    boolean application=name.startsWith("net/minecraft/")
                            ||name.startsWith("dev/ronova/pro/validation/")||producerModule(module)
                            ||declaredSource||externalSources||directEntityReference(bytes);
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
                        throw new IllegalStateException("CODE_SOURCE_BOUNDARY_BINDING_FAILED:"+name,failure);
                    }catch(RuntimeException|LinkageError failure) {
                        if(controlFailure==null)controlFailure=String.valueOf(name)+":"+failure.getClass().getSimpleName();
                        System.err.println("RONOVA_CONTROL_TRANSFORM_FAILED:"+controlFailure);
                        if(Boolean.getBoolean("ronova.pro.validation"))failure.printStackTrace(System.err);
                        if(ModGroupBoundary.stopped(module)) {
                            ModGroupBoundary.failed(module,String.valueOf(name),failure);
                            // Transformer exceptions alone are ignored by Instrumentation. Invalid bytes refuse
                            // this selected definition; a failed retransform retains and reports the old body.
                            return new byte[]{0,0,0,0};
                        }
                        throw failure;
                    }
                }
            };
            control.getMethod("install",Class.class,ClassFileTransformer[].class).invoke(null,RecoveryAgent.class,
                    new ClassFileTransformer[]{controlImages,taskTransformer,objectTransformer});
            definitionBridge=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",true,null);
            definitionBridge.getMethod("install",Class.class).invoke(null,RecoveryAgent.class);
            networkBridge=Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",true,null);
            Object[] networkControls=(Object[])networkBridge.getMethod("controlObjects").invoke(null);
            bridge.getMethod("registerAgentControls",Object[].class).invoke(null,(Object)networkControls);
            clientBridge=Class.forName("dev.ronova.pro.bootstrap.ClientBridge",true,null);
            Object[] clientControls=(Object[])clientBridge.getMethod("controlObjects").invoke(null);
            bridge.getMethod("registerAgentControls",Object[].class).invoke(null,(Object)clientControls);
            codeSourceBridge=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);
            codeSourceBridge.getMethod("install",Class.class).invoke(null,RecoveryAgent.class);
            Set<ModuleLayer> sourceLayers=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Class<?> loaded:loadedClasses())if(loaded.getModule().getLayer()!=null)sourceLayers.add(loaded.getModule().getLayer());
            for(ModuleLayer layer:sourceLayers)codeSourceBridge.getMethod("seedLayer",ModuleLayer.class).invoke(null,layer);
            bridge.getMethod("prepareThreadBoundary").invoke(null);
            Set<String> characterWrappers=ResourceBoundary.CHARACTER_WRAPPERS;
            api.addTransformer(controlImages,true);
            api.addTransformer(taskTransformer,true);
            api.addTransformer(objectTransformer,true);
            for(String wrapper:characterWrappers)api.retransformClasses(Class.forName(wrapper.replace('/','.'),false,null));
            api.retransformClasses(ModuleLayer.class,ClassLoader.class,Thread.class,java.lang.invoke.MethodHandles.class,java.lang.invoke.MethodHandles.Lookup.class,java.lang.ref.Reference.class,
                    java.util.Timer.class,Class.forName("java.util.TimerThread",false,null),java.io.FileDescriptor.class,
                    java.io.FileInputStream.class,java.io.FileOutputStream.class,java.io.RandomAccessFile.class,
                    java.nio.channels.spi.AbstractInterruptibleChannel.class,Class.forName("sun.nio.ch.FileChannelImpl",false,null),
                    Class.forName("sun.nio.fs.WindowsChannelFactory",false,null),
                    Class.forName("java.lang.ProcessImpl",false,null),java.net.URL.class,java.io.InputStreamReader.class,
                    Class.forName("jdk.internal.loader.NativeLibraries$NativeLibraryImpl",false,null),
                    Class.forName("jdk.internal.loader.NativeLibraries$Unloader",false,null));
            Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("libraryBoundaryInstalled").invoke(null);
            Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("processBoundaryInstalled").invoke(null);
            report(String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("threadBoundaryInstalled").invoke(null)));
            for(Class<?> loaded:loadedClasses())if(loaded.getClassLoader()==null&&BufferBoundary.TARGETS.contains(loaded.getName().replace('.','/'))&&api.isModifiableClass(loaded))api.retransformClasses(loaded);
            for(Class<?> loaded:loadedClasses())if(loaded.getClassLoader()==null&&IoBoundary.TARGETS.contains(loaded.getName().replace('.','/'))&&api.isModifiableClass(loaded))api.retransformClasses(loaded);
            report(String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("fileBoundaryInstalled").invoke(null)));
            if(ThreadBoundary.installed())boundary("java/lang/Thread",true);
            for(String timer:List.of("java/util/Timer","java/util/TimerThread"))if(TimerBoundary.installed(timer))boundary(timer,true);
            for(Class<?> loaded:loadedClasses())
                if((loaded.getName().replace('.','/').equals(CommandBoundary.TARGET)
                        ||loaded.getClassLoader()!=null&&(CodeSourceBoundary.TARGETS.contains(loaded.getName().replace('.','/'))||NetworkBoundary.TARGETS.contains(loaded.getName().replace('.','/'))||ClientBoundary.TARGETS.contains(loaded.getName().replace('.','/')))&&!producerModule(loaded.getModule()))
                        &&api.isModifiableClass(loaded))
                    api.retransformClasses(loaded);
            Class<?> manager=Class.forName("sun.instrument.TransformerManager",false,null);
            api.retransformClasses(manager,api.getClass());
            protectInstrumentation(api);
            control.getMethod("installed").invoke(null);
            ExternalCodeDefinitions.start(api);
            // The agent/bridge were loaded before our transformer, so seed their actual images once.
            for(Class<?> loaded:loadedClasses())if((loaded.getName().startsWith("dev.ronova.pro.agent.")
                    ||loaded.getName().startsWith("dev.ronova.pro.bootstrap."))&&api.isModifiableClass(loaded))api.retransformClasses(loaded);
            report("INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:"+controlReady());
            for(String name:JdkTaskBoundaryContract.certificationTargets()) {
                Class<?> type=Class.forName(name.replace('/','.'),false,null);
                if(!api.isModifiableClass(type))throw new IllegalStateException("UNMODIFIABLE_BOUNDARY:"+name);
                api.retransformClasses(type);
                if(installed.contains(name))boundary(name,true);
            }
            try {
                if(api.isModifiableClass(HashMap.class)) {
                    api.retransformClasses(HashMap.class);
                    indexGuarded=HashMapBoundary.installed();
                }
                Class<?> node=Class.forName("java.util.HashMap$Node",false,null);
                if(api.isModifiableClass(node))api.retransformClasses(node);
                if(api.isModifiableClass(java.util.LinkedHashMap.class))api.retransformClasses(java.util.LinkedHashMap.class);
                if(api.isModifiableClass(Map.class))api.retransformClasses(Map.class);
                api.retransformClasses(java.util.concurrent.ConcurrentHashMap.class);
                api.retransformClasses(java.util.concurrent.atomic.AtomicReference.class,java.util.concurrent.CopyOnWriteArrayList.class);
                // Both executor classes already received the backing guard in the certified
                // pass above. A second transform would revoke that completed task installation.
                api.retransformClasses(java.util.AbstractCollection.class,java.util.ArrayList.class,Class.forName("java.util.ArrayList$Itr",false,null),
                    Class.forName("java.util.ArrayList$ListItr",false,null),Class.forName("java.util.ArrayList$SubList",false,null),Class.forName("java.util.ArrayList$SubList$1",false,null),java.util.IdentityHashMap.class,
                    Class.forName("java.util.IdentityHashMap$IdentityHashMapIterator",false,null),
                    Class.forName("java.util.IdentityHashMap$EntryIterator$Entry",false,null));
                for(String target:HandleWriteBoundary.targets()) {Class<?> type=Class.forName(target.replace('/','.'),false,null);if(api.isModifiableClass(type))api.retransformClasses(type);}
                if(HashMapBoundary.sourceInstalled())Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null).getMethod("activate").invoke(null);
            } catch(ClassNotFoundException|UnmodifiableClassException|RuntimeException|LinkageError unavailable) {
                indexGuarded=false;
                report("INDEX_GUARD_RETRANSFORM_UNAVAILABLE:"+unavailable.getClass().getSimpleName());
            }
            report(indexGuardState());
            installIndexBoundaries();
            for(Class<?> type:loadedClasses())if(type.getName().replace('.','/').equals(ConsoleBoundary.TARGET)) {
                try {
                    if(api.isModifiableClass(type))api.retransformClasses(type);
                    else ConsoleBoundary.unavailable("UNMODIFIABLE");
                }
                catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                    ConsoleBoundary.unavailable(failure.getClass().getSimpleName());
                    report("CONSOLE_BOUNDARY_UNAVAILABLE:"+failure.getClass().getSimpleName());
                }
            }
            report("CONSOLE_BOUNDARY:"+ConsoleBoundary.state());
            // Reflection Field is loaded long before an attach-time install, so its guard must be applied by an
            // explicit retransformation here; the transformer alone only covers classes that load later.
            try {
                Class<?> field=Class.forName("java.lang.reflect.Field",false,null);
                if(api.isModifiableClass(field)) { api.retransformClasses(field);reflectionGuarded=ReflectionWriteBoundary.guardedCount()==9; }
            } catch(ClassNotFoundException|RuntimeException|LinkageError|UnmodifiableClassException unavailable) {
                report("REFLECTION_FIELD_GUARD_RETRANSFORM_UNAVAILABLE:"+unavailable.getClass().getSimpleName());
            }
            report(ReflectionWriteBoundary.state());
            try {
                Class<?> unsafe=Class.forName("sun.misc.Unsafe",false,ClassLoader.getPlatformClassLoader());
                if(api.isModifiableClass(unsafe)) { api.retransformClasses(unsafe);unsafeGuarded=UnsafeWriteBoundary.entries()==31; }
                Class<?> internalUnsafe=Class.forName("jdk.internal.misc.Unsafe",false,null);
                if(api.isModifiableClass(internalUnsafe))api.retransformClasses(internalUnsafe);
            } catch(ClassNotFoundException|UnmodifiableClassException|RuntimeException|LinkageError failure) {
                report("UNSAFE_GUARD_UNAVAILABLE:"+failure.getClass().getSimpleName());
            }
            report("UNSAFE_OBJECT_PUT_CAS_ATOMIC_OBJECT_BULK:"+unsafeGuarded+":UNOBSERVED_RAW_NATIVE_AND_UNWOVEN_COMPILER_INTRINSIC_WRITERS_UNSUPPORTED");
            report("BACKING_JDK:"+BackingBoundary.coreInstalled()+":HANDLE_FACTORIES:"+HandleWriteBoundary.installedCount()+"/"+HandleWriteBoundary.targets().size());
            DispatchBoundary.retransformDeclared(api);
            for(Class<?> type:loadedClasses())if(type.getName().replace('.','/').equals(EventFaultBoundary.TARGET)) {
                try {
                    bridge.getMethod("installEventBoundary",Class.class).invoke(null,type);
                    eventFaultGuarded=false;api.retransformClasses(type);
                }
                catch(ReflectiveOperationException failure){throw new IllegalStateException("EVENT_CONTROL_BINDING",failure);}
                catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                    eventFaultGuarded=false;report("CLIENT_FAULT_DISPATCH_UNAVAILABLE:"+failure.getClass().getSimpleName());
                }
            }
            report("CLIENT_FAULT_DISPATCH:"+eventFaultGuarded);
            report(installed.containsAll(JdkTaskBoundaryContract.targets())?
                    "JDK_FUTURE_WORKER_SCHEDULED_FORKJOIN_CF_DEFERRED_AND_STAGE_INVOCATIONS":"PARTIAL_TASK_BOUNDARIES");
            if(!NativeBindingBoundary.observedBindings().isEmpty())
                report("NATIVE_ENTRIES_OBSERVED:"+NativeBindingBoundary.observedBindings().size()
                        +":"+NativeBindingBoundary.backendState());
    }
    /** The honest native backend state for callers that report capability. */
    public static String nativeBindingState() {
        try { return String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("state").invoke(null)); }
        catch(ReflectiveOperationException unavailable) { return NativeBindingBoundary.backendState(); }
    }
    /** Whether the reflection field-write guard is installed on this JVM. */
    public static boolean unsafeFieldWriteGuarded() { return unsafeGuarded; }
    public static boolean reflectionFieldWriteGuarded() { return reflectionGuarded; }
    /** Exact guard state, including how many write entries carry it. */
    public static String reflectionGuardState() { return ReflectionWriteBoundary.state(); }
    public static String indexGuardState() {
        return (indexGuarded?"INSTALLED:":"UNAVAILABLE:")+HashMapBoundary.state()+";NODE="+HashMapBoundary.nodeState()+";DEFAULT="+HashMapBoundary.defaultState()+";FASTUTIL="+FastutilIndexBoundary.states();
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
        boolean added=DispatchBoundary.declare(owner,methodName,descriptor);
        Instrumentation api=instrumentation;
        // Installation is batched after all exact defining classes have been declared.
        return added;
    }
    /** The declaration state, so a caller can tell "covered" from "nothing declared yet". */
    public static String dispatchState() {
        return "DECLARED_TARGETS:"+DispatchBoundary.declaredTargets()+":TRANSFORMED:"+DispatchBoundary.transformedEntries().size();
    }
    private static volatile Class<?> modGroupRuntime;
    /** Binds control entry points to the exact classes loaded by Forge for this Ronova instance. */
    public static synchronized void bindModGroupCallers(Class<?> mod,Class<?> runtime) {
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if(caller!=mod||mod==null||runtime==null||!mod.getName().equals("dev.ronova.pro.ProMod")
                ||!runtime.getName().equals("dev.ronova.pro.ProRuntime")
                ||mod.getClassLoader()!=runtime.getClassLoader()||mod.getModule()!=runtime.getModule()
                ||!"ronova_pro".equals(mod.getModule().getName())
                ||!java.util.Objects.equals(mod.getProtectionDomain().getCodeSource(),runtime.getProtectionDomain().getCodeSource())
                ||modGroupRuntime!=null&&modGroupRuntime!=runtime)throw new SecurityException("MOD_GROUP_OWNER_BINDING");
        modGroupRuntime=runtime;
    }
    /** Fences task and callback publication before current body cleanup starts. */
    public static String prepareModGroup(String[] ids,Module[] modules) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        if(instrumentation==null)return "AGENT_UNAVAILABLE";
        String validation=ModGroupBoundary.validate(ids,modules);
        if(!validation.equals("READY"))return validation;
        for(Module module:modules)publishStoppedModule(module);
        return "READY";
    }
    /** Stop selected Forge modules, including already loaded methods and future definitions. */
    public static String[] modGroupIds(Module module) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        return ModGroupBoundary.ids(module).toArray(String[]::new);
    }
    public static String stopModGroup(String[] ids,Module[] modules) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        String result=ModGroupBoundary.stop(instrumentation,ids,modules,"default");
        if(result.startsWith("TARGETS="))for(Module module:modules)publishStoppedModule(module);
        return result;
    }
    public static String stopModGroup(String[] ids,Module[] modules,String returns) {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");
        String result=ModGroupBoundary.stop(instrumentation,ids,modules,returns);
        if(result.startsWith("TARGETS="))for(Module module:modules)publishStoppedModule(module);
        return result;
    }
    public static void beginModGroupBatch() {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");ModGroupBoundary.beginBatch(instrumentation);
    }
    public static void endModGroupBatch() {
        requireModGroupCaller("dev.ronova.pro.ProRuntime");ModGroupBoundary.endBatch();
    }
    public static String stopModIds(String[] ids) {
        requireModGroupCaller("dev.ronova.pro.ClientPresence");
        String result=ModGroupBoundary.stopByIds(instrumentation,ids,"default");
        if(result.startsWith("TARGETS="))for(Class<?> type:loadedClasses())
            if(ModGroupBoundary.stopped(type.getModule()))publishStoppedModule(type.getModule());
        return result;
    }
    public static String stopModIds(String[] ids,String returns) {
        requireModGroupCaller("dev.ronova.pro.ClientPresence");
        String result=ModGroupBoundary.stopByIds(instrumentation,ids,returns);
        if(result.startsWith("TARGETS="))for(Class<?> type:loadedClasses())
            if(ModGroupBoundary.stopped(type.getModule()))publishStoppedModule(type.getModule());
        return result;
    }
    public static String modGroupState(Module module) { return ModGroupBoundary.state(module); }
    public static String modGroupState() { return ModGroupBoundary.state()+";"+EventBusBoundary.state()
            +";COMMAND_GATE="+(CommandBoundary.installed()?"INSTALLED":"NOT_INSTALLED"); }
    private static void requireModGroupCaller(String expected) {
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
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
    private static void publishStoppedModule(Module module) {
        try {
            bridge.getMethod("stopModules",Module[].class).invoke(null,(Object)new Module[]{module});
            Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null).getMethod("policy",Module.class,String.class).invoke(null,module,ModGroupBoundary.mode(module));
            Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("module",Module.class,boolean.class).invoke(null,module,true);
        }
        catch(ReflectiveOperationException failure) {throw new IllegalStateException("MOD_TASK_GATE_PUBLICATION_FAILED",failure);}
    }
    public static byte[] transformDefined(Module module,ClassLoader loader,byte[] bytes,boolean hidden) {
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        try {if(caller!=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null))throw new SecurityException("ACTUAL_DEFINITION_BRIDGE_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        if(!producerModule(module))return bytes;
        if(!module.canRead(bridge.getModule()))instrumentation.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
        if(loader!=null&&!loader.getUnnamedModule().canRead(bridge.getModule()))
            instrumentation.redefineModule(loader.getUnnamedModule(),Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
        String name=new jdk.internal.org.objectweb.asm.ClassReader(bytes).getClassName();
        ExternalCodeRuntime.Source source=ExternalCodeRuntime.prepare(module,loader,name,null,bytes,hidden);
        byte[] current=DefinitionBoundary.generated(loader,source.bytes());
        byte[] fields=FieldWriteBoundary.transform(loader,module,current);if(fields!=null)current=fields;
        NativeBindingBoundary.observe(name,bytes);
        return ExternalCodeRuntime.finish(loader,name,null,source,current);
    }
    public static void externalDefinitionAccepted(Class<?> actual,byte[] bytes){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        try{if(caller!=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",false,null))throw new SecurityException("ACTUAL_DEFINITION_RESULT_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        if(actual!=null&&producerModule(logicalModule(actual)))ExternalCodeDefinitions.defined(actual,bytes);
    }
    private static volatile Class<?> definitionBridge;
    private static volatile Class<?> codeSourceBridge;
    /** Called only when the real module layer or reader was observed by the bootstrap bridge. */
    public static void sourceModuleRead(Module module) {
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if(caller!=codeSourceBridge)throw new SecurityException("ACTUAL_CODE_SOURCE_BRIDGE_REQUIRED");
        if(producerModule(module)) {registerProducer(module);if(ModGroupBoundary.stopped(module))publishStoppedModule(module);}
    }
    private static void requireCodeSourceCaller(){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames.skip(2).findFirst().map(StackWalker.StackFrame::getDeclaringClass).orElse(null));
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
    public static void externalClassPrepared(Class<?> actual){requireCodeSourceCaller();ExternalCodeDefinitions.prepared(actual);}
    public static void externalDefinitionsChanged(Class<?>[] actuals){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        try{if(caller!=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null))throw new SecurityException("ACTUAL_INSTRUMENTATION_COMPLETION_REQUIRED");}
        catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}
        for(Class<?> actual:actuals)ExternalCodeDefinitions.changed(actual);
    }
    static String[] externalCodeVersion(Class<?> actual,byte[] bytes){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{return (String[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeVersion",Class.class,byte[].class).invoke(null,actual,bytes);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_VERSION_UNAVAILABLE",failure);}
    }
    static String[] externalCodeDeclarations(Class<?> actual){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{return (String[])Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeDeclarations",Class.class).invoke(null,actual);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_CODE_DECLARATIONS_UNAVAILABLE",failure);}
    }
    static void publishExternalDefinition(ClassLoader loader,String name,byte[] bytes,boolean hidden){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ExternalCodeRuntime.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_WEAVER_REQUIRED");
        try{Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeLayout",ClassLoader.class,String.class,byte[].class,String[].class,Module[][][].class,boolean.class)
                .invoke(null,loader,name,bytes,new String[0],new Module[0][][],hidden);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_DEFINITION_VM_PUBLICATION_FAILED",failure);}
    }
    static void protectExternalCode(Object[] objects){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        if(caller.getNestHost()!=ExternalCodeImages.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_LEDGER_REQUIRED");
        try{codeSourceBridge.getMethod("codeControls",Object[].class).invoke(null,(Object)objects);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_CODE_LEDGER_PROTECTION_FAILED",failure);}
    }
    static Object externalExecutionPlan(Object[][] rows){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ExternalCodeRuntime.class)throw new SecurityException("ACTUAL_EXTERNAL_CODE_WEAVER_REQUIRED");
        try{return codeSourceBridge.getMethod("executionPlan",Object[][].class).invoke(null,(Object)rows);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_EXECUTION_LAYOUT_PUBLICATION_FAILED",failure);}
    }
    static void publishExternalCode(ClassLoader loader,String name,Class<?> actual,byte[] bytes,String[] methods,Module[][][] owners,Map<String,Module[]> fields,Object[][] values,boolean hidden,Object execution,Module declaration){
        Class<?> caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
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
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ExternalCodeDefinitions.class)throw new SecurityException("ACTUAL_CODE_DEFINITION_REGISTRY_REQUIRED");
        try{
            boolean installed=Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeDefinition",Class.class,byte[].class).invoke(null,actual,bytes));
            codeSourceBridge.getMethod("codeMethods",Class.class,String[].class).invoke(null,actual,methods);
            codeSourceBridge.getMethod("codeFields",Class.class,String[].class,Module[][].class).invoke(null,actual,fields.keySet().toArray(String[]::new),fields.values().toArray(Module[][]::new));
            codeSourceBridge.getMethod("codeValues",Class.class,Object[][].class).invoke(null,actual,(Object)values);
            bridge.getMethod("registerDefinitionCreationSites",Class.class,String[].class).invoke(null,actual,(Object)creationSites);
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
    /** A helper's module metadata is not an authority for skipping its direct Minecraft writes. */
    private static boolean directEntityReference(byte[] bytes) {
        return containsAscii(bytes,"net/minecraft/world/entity/")
                ||containsAscii(bytes,"net/minecraft/world/level/entity/")
                ||containsAscii(bytes,"net/minecraft/network/syncher/SynchedEntityData")
                ||containsAscii(bytes,"net/minecraft/server/level/ServerLevel");
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
        return producerModules.computeIfAbsent(module,key->{
            Set<String> owners=ModGroupBoundary.ids(key);
            return !owners.isEmpty()&&java.util.Collections.disjoint(owners,Set.of("ronova_pro","minecraft","forge","java"));
        });
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
            try {
                String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(JdkTaskBoundaryContract.canonical(image)));
                definitions.put(type,hash+":"+(++definitionEpoch));
            } catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
        }
    }
    private static void registerProducer(Module module) {
        try {
            bridge.getMethod("registerProducerModule",Module.class).invoke(null,module);
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
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass().getNestHost()!=ExternalCodeDefinitions.class)
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
                &&Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null)
                        .getMethod("controlCaptureHealthy").invoke(null));}
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
        bridge.getMethod("registerAgentControls",Object[].class).invoke(null,(Object)roots.toArray());
    }
}
