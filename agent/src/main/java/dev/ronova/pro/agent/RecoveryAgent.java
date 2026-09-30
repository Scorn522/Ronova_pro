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
                    &&ConcurrentSourceBoundary.installed()&&controlReady()?"INSTALLED_FULL":"INSTALLED_PARTIAL";
        } catch(Throwable unavailable) {
            installState="FAILED:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage();
            report("AGENT_"+mode+"_UNAVAILABLE:"+installState);
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
    private static volatile String installState="NOT_ATTEMPTED";
    private static synchronized void install(String argument,Instrumentation api) throws Exception {
        if(!api.isRetransformClassesSupported())throw new IllegalStateException("RETRANSFORM_UNSUPPORTED");
            if(argument==null||argument.isBlank())throw new IllegalArgumentException("BOOTSTRAP_JAR_PATH_REQUIRED");
            if(argument.startsWith("base64:"))argument=new String(Base64.getUrlDecoder().decode(argument.substring(7)),java.nio.charset.StandardCharsets.UTF_8);
            bootstrap=new JarFile(Path.of(argument).toAbsolutePath().normalize().toFile());
            api.appendToBootstrapClassLoaderSearch(bootstrap);
            bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null);
            if(!Integer.valueOf(25).equals(bridge.getMethod("abiVersion").invoke(null)))throw new IllegalStateException("TASK_BRIDGE_ABI_MISMATCH");
            Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null);
            Class.forName("dev.ronova.pro.bootstrap.EventFaultBridge",true,null);
            Class<?> control=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
            Module base=Object.class.getModule(),ours=RecoveryAgent.class.getModule();
            api.redefineModule(base,Set.of(bridge.getModule()),Map.of(
                    "jdk.internal.org.objectweb.asm",Set.of(ours),"jdk.internal.org.objectweb.asm.tree",Set.of(ours),"jdk.internal.org.objectweb.asm.tree.analysis",Set.of(ours)),
                    Map.of("java.util.concurrent",Set.of(bridge.getModule()),"java.lang",Set.of(bridge.getModule()),
                            "java.util",Set.of(bridge.getModule())),Set.of(),Map.of());
            // A Forge scanning thread may run newly transformed code immediately after addTransformer.
            // Finish the bootstrap helper's access and initialization before publishing any such call site.
            Class.forName("dev.ronova.pro.bootstrap.BackingBridge",true,null);
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
                    try {
                    // Forge's discovery loader is active before its module resources and frame types exist.
                    // It does not own world effects; touching it here creates a false partial install.
                    if(name!=null&&name.startsWith("net/minecraftforge/fml/loading/"))return null;
                    byte[] stopped=ModGroupBoundary.transform(loader,module,name,bytes);
                    if(stopped!=null) {publishStoppedModule(module);return stopped;}
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
                    if(loader==null&&module==base&&("java/util/HashMap".equals(name)||"java/util/HashMap$Node".equals(name)||"java/util/Map".equals(name))) {
                        return HashMapBoundary.transform(loader,name,bytes);
                    }
                    if(loader==null&&"java/util/concurrent/ConcurrentHashMap".equals(name))return ConcurrentSourceBoundary.transform(loader,name,bytes);
                    if(name!=null&&FastutilIndexBoundary.targets().contains(name)) {
                        if(module!=null&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        byte[] index=FastutilIndexBoundary.transform(loader,name,bytes);
                        byte[] fields=FieldWriteBoundary.transform(loader,index);return fields==null?index:fields;
                    }
                    // Reflection is one of the two real paths to a field write that bypasses the public setter.
                    byte[] guarded=ReflectionWriteBoundary.transform(loader,name,bytes);
                    if(guarded!=null)return guarded;
                    byte[] unsafe=UnsafeWriteBoundary.transform(loader,name,bytes);
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
                    if(loader==null||name==null||name.startsWith("dev/ronova/pro/agent/")||name.startsWith("dev/ronova/pro/bootstrap/")||name.startsWith("java/"))return null;
                    byte[] nativeClone=CloneBoundary.transform(loader,current);
                    if(nativeClone!=null)current=nativeClone;
                    boolean declaredSource=type!=null&&watching.contains(type);
                    boolean application=name.startsWith("net/minecraft/")
                            ||name.startsWith("dev/ronova/pro/validation/")||producerModule(module)
                            ||declaredSource||directEntityReference(bytes);
                    if(!application) {
                        if(current!=bytes&&module!=null&&module.isNamed()&&!module.canRead(bridge.getModule()))
                            api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                        return current==bytes?null:current;
                    }
                    if(module!=null&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    // Native entries are enumerated, never rewritten: swapping a body would change behaviour
                    // without a real backend, which is worse than reporting the gap honestly.
                    NativeBindingBoundary.observe(name,bytes);
                    byte[] fields=FieldWriteBoundary.transform(loader,current);
                    if(fields!=null)current=fields;
                    byte[] backings=BackingBoundary.transform(loader,name,current);if(backings!=null)current=backings;
                    byte[] creations=CreationBoundary.transform(loader,name,type,current,producerModule(module));
                    if(creations!=null)current=creations;
                    byte[] transformed=current==bytes?null:current;
                    if(transformed!=null&&module!=null&&module.isNamed()&&!module.canRead(bridge.getModule()))
                        api.redefineModule(module,Set.of(bridge.getModule()),Map.of(),Map.of(),Set.of(),Map.of());
                    if(type!=null)synchronized(definitions) {
                        if(watching.contains(type))try {
                            byte[] observed=transformed==null?bytes:transformed;
                            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JdkTaskBoundaryContract.canonical(observed)));
                            if(Boolean.getBoolean("ronova.pro.validation")) {
                                try {
                                    Path folder=Path.of("ronova","definition-evidence");Files.createDirectories(folder);
                                    String prefix=type.getName().replace('.','_');
                                    Files.write(folder.resolve(prefix+"-observed.class"),observed);
                                    Files.write(folder.resolve(prefix+"-canonical.class"),JdkTaskBoundaryContract.canonical(observed));
                                } catch(java.io.IOException unavailable) { System.err.println("RONOVA_DEFINITION_DIAGNOSTIC_UNAVAILABLE "+unavailable.getClass().getSimpleName()); }
                            }
                            definitions.put(type,hash+":"+(++definitionEpoch));
                        } catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                    }
                    return transformed;
                    }catch(RuntimeException|LinkageError failure) {
                        if(controlFailure==null)controlFailure=String.valueOf(name)+":"+failure.getClass().getSimpleName();
                        System.err.println("RONOVA_CONTROL_TRANSFORM_FAILED:"+controlFailure);
                        if(Boolean.getBoolean("ronova.pro.validation"))failure.printStackTrace(System.err);
                        throw failure;
                    }
                }
            };
            control.getMethod("install",Class.class,ClassFileTransformer[].class).invoke(null,RecoveryAgent.class,
                    new ClassFileTransformer[]{controlImages,taskTransformer,objectTransformer});
            api.addTransformer(controlImages,true);
            api.addTransformer(taskTransformer,true);
            api.addTransformer(objectTransformer,true);
            for(Class<?> loaded:api.getAllLoadedClasses())
                if(loaded.getName().replace('.','/').equals(CommandBoundary.TARGET)&&api.isModifiableClass(loaded))
                    api.retransformClasses(loaded);
            Class<?> manager=Class.forName("sun.instrument.TransformerManager",false,null);
            api.retransformClasses(manager,api.getClass());
            protectInstrumentation(api);
            control.getMethod("installed").invoke(null);
            // The agent/bridge were loaded before our transformer, so seed their actual images once.
            for(Class<?> loaded:api.getAllLoadedClasses())if((loaded.getName().startsWith("dev.ronova.pro.agent.")
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
                if(api.isModifiableClass(Map.class))api.retransformClasses(Map.class);
                api.retransformClasses(java.util.concurrent.ConcurrentHashMap.class);
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
            for(Class<?> type:api.getAllLoadedClasses())if(type.getName().replace('.','/').equals(ConsoleBoundary.TARGET)) {
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
            } catch(ClassNotFoundException|UnmodifiableClassException|RuntimeException|LinkageError failure) {
                report("UNSAFE_GUARD_UNAVAILABLE:"+failure.getClass().getSimpleName());
            }
            report("UNSAFE_OBJECT_PUT_CAS_ATOMIC_OBJECT_BULK:"+unsafeGuarded+":RAW_ADDRESS_INTERNAL_UNSUPPORTED");
            report("BACKING_JDK:"+BackingBoundary.coreInstalled()+":HANDLE_FACTORIES:"+HandleWriteBoundary.installedCount()+"/"+HandleWriteBoundary.targets().size());
            DispatchBoundary.retransformDeclared(api);
            for(Class<?> type:api.getAllLoadedClasses())if(type.getName().replace('.','/').equals(EventFaultBoundary.TARGET)) {
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
        for(Class<?> type:api.getAllLoadedClasses())if(FastutilIndexBoundary.targets().contains(type.getName().replace('.','/'))
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
        String result=ModGroupBoundary.stop(instrumentation,ids,modules);
        if(result.startsWith("TARGETS="))for(Module module:modules)publishStoppedModule(module);
        return result;
    }
    public static String stopModIds(String[] ids) {
        requireModGroupCaller("dev.ronova.pro.ClientPresence");
        String result=ModGroupBoundary.stopByIds(instrumentation,ids);
        if(result.startsWith("TARGETS="))for(Class<?> type:instrumentation.getAllLoadedClasses())
            if(ModGroupBoundary.stopped(type.getModule()))publishStoppedModule(type.getModule());
        return result;
    }
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
        try {bridge.getMethod("stopModules",Module[].class).invoke(null,(Object)new Module[]{module});}
        catch(ReflectiveOperationException failure) {throw new IllegalStateException("MOD_TASK_GATE_PUBLICATION_FAILED",failure);}
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
    private static boolean producerModule(Module module) {
        if(module==null)return false;
        return producerModules.computeIfAbsent(module,key->{
            try(var resource=key.getResourceAsStream("META-INF/mods.toml")){return resource!=null;}
            catch(java.io.IOException unavailable){return false;}
        });
    }
    public static void openBackingAccess(Class<?> core,Class<?>[] carriers) {
        Instrumentation api=instrumentation;if(api==null)throw new IllegalStateException("AGENT_NOT_INSTALLED");
        api.redefineModule(Object.class.getModule(),Set.of(),Map.of(),Map.of("java.util",Set.of(core.getModule())),Set.of(),Map.of());
        Map<Module,Map<String,Set<Module>>> openings=new IdentityHashMap<>();
        for(Class<?> type:carriers)if(type!=null&&type.getModule()!=core.getModule())
            openings.computeIfAbsent(type.getModule(),ignored->new HashMap<>()).put(type.getPackageName(),Set.of(core.getModule()));
        for(var entry:openings.entrySet())if(entry.getKey().isNamed())
            api.redefineModule(entry.getKey(),Set.of(),Map.of(),entry.getValue(),Set.of(),Map.of());
    }
    public static String watchDefinition(Class<?> type) {
        Instrumentation api=instrumentation;if(api==null||!api.isModifiableClass(type))return null;
        controlImages.declare(type);
        synchronized(definitions) { if(definitions.containsKey(type))return definitions.get(type);watching.add(type); }
        try { api.retransformClasses(type); }
        catch(UnmodifiableClassException|RuntimeException|LinkageError unavailable) { synchronized(definitions) { definitions.remove(type); }return null; }
        synchronized(definitions) { return definitions.get(type); }
    }
    public static String definition(Class<?> type) { synchronized(definitions) { return definitions.get(type); } }
    public static Class<?> initializedSource(String name) {
        Instrumentation api=instrumentation;if(api==null)return null;
        try {
            Class<?> unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");
            if(!field.trySetAccessible())return null;Object access=field.get(null);
            var pending=unsafe.getMethod("shouldBeInitialized",Class.class);Class<?> found=null;
            for(Class<?> type:api.getAllLoadedClasses())if(type.getName().equals(name)&&!Boolean.TRUE.equals(pending.invoke(access,type))) {
                if(found!=null)return null;found=type;
            }
            return found;
        } catch(ReflectiveOperationException|RuntimeException unavailable) { return null; }
    }
    public static Class<?> loadedSource(String name) {
        Instrumentation api=instrumentation;if(api==null)return null;Class<?> found=null;
        for(Class<?> type:api.getAllLoadedClasses())if(type.getName().equals(name)) {
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
