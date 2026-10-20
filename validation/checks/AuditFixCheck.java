package dev.ronova.pro;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Focused source-list regression and historical metadata checks; neither certifies gameplay. */
public final class AuditFixCheck {
    private static native long rangeContentionFinish0();
    private static void rangeContention(String bootstrap)throws Exception {
        long calls;
        try {
            bootstrapSearch(bootstrap);
            long address=U.allocateMemory(16);
            try {
                for(int i=0;i<1024;i++){
                    long expected=0x1234abc000L+i;
                    U.putLong(address,expected);
                    require(U.getLong(address)==expected,"ordinary native memory changed during JNI contention");
                }
            } finally {U.freeMemory(address);}
        } finally {calls=rangeContentionFinish0();}
        require(calls>0,"real attached JNI workers failed or did not overlap premain");
        System.out.println("RANGE_CONTENTION_PASS actual_premain_retransformation ordinary_Unsafe_read_write_preserved real_GetEnv_calls="+calls+" no_supplied_controller_metadata");
    }
    private static native boolean jniLiveTable0();
    private static native boolean jniStoppedHookObserve0(long hook,long original);
    private static int jniLiveBoundary(){return 73;}
    private static void jniLiveTable()throws Exception {
        Class<?> control=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
        require(Boolean.TRUE.equals(control.getMethod("available").invoke(null)),"actual premain NativeControl installation required");
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        require(jniLiveTable0(),"actual live JNI table protection or restoration failed");
        var archive=java.nio.file.Path.of(System.getProperty("ronova.pro.audit.modJar")).toAbsolutePath();
        var finder=java.lang.module.ModuleFinder.of(archive);
        var configuration=ModuleLayer.boot().configuration().resolve(finder,java.lang.module.ModuleFinder.of(),Set.of("audit.jni.retirement"));
        var layer=ModuleLayer.boot().defineModulesWithOneLoader(configuration,ClassLoader.getSystemClassLoader());
        Module module=layer.findModule("audit.jni.retirement").orElseThrow();
        Class<?> hook=Class.forName("audit.jni.retirement.HookTarget",true,layer.findLoader(module.getName()));
        require(hook.getModule()==module&&java.nio.file.Files.isSameFile(archive,
                java.nio.file.Path.of(hook.getProtectionDomain().getCodeSource().getLocation().toURI())),"hook did not load from its real Mod archive");
        long[] installed=(long[])hook.getMethod("install",String.class).invoke(null,
                java.nio.file.Path.of(System.getProperty("ronova.pro.audit.hook.native")).toAbsolutePath().toString());
        require(installed!=null&&installed.length==2&&installed[0]!=0&&installed[1]!=0&&installed[0]!=installed[1],"real Mod hook publication failed");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.ModGroupBoundary");
        Method prepare=boundary.getDeclaredMethod("prepare",java.lang.instrument.Instrumentation.class,String[].class,Module[].class,String.class);
        prepare.setAccessible(true);
        require("READY".equals(prepare.invoke(null,actualInstrumentation(),new String[]{"audit_jni_retire"},new Module[]{module},"default")),
                "real Mod stop publication failed");
        Method stopped=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getDeclaredMethod("modStopped",Module.class);
        stopped.setAccessible(true);require((boolean)stopped.invoke(null,module),"hook module was not stopped");
        require(jniStoppedHookObserve0(installed[0],installed[1]),"stopped Mod JNI hook survived retirement");
        System.out.println("JNI_LIVE_TABLE_PASS real_premain full_table_protection controlled_and_uncontrolled_raw_damage_repaired legal_live_hook_preserved actual_Mod_stop_restored_original no_supplied_owner_metadata");
    }
    private static final sun.misc.Unsafe U;
    static {try {var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);U=(sun.misc.Unsafe)field.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void put(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean yes,String message) {if(!yes)throw new AssertionError(message);}
    private static Object invoke(Object receiver,String name)throws Exception {var m=receiver.getClass().getDeclaredMethod(name);m.setAccessible(true);try{return m.invoke(receiver);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("range-contention")){rangeContention(args[1]);return;}
        if(args.length==2&&args[0].equals("bootstrap-search")){bootstrapSearch(args[1]);return;}
        if(args.length==1&&args[0].equals("jni-live-table")){jniLiveTable();return;}
        if(args.length==1&&args[0].equals("platform-control-writes")){platformControlWrites();return;}
        if(args.length==1&&args[0].equals("installed-platform-writes")){installedPlatformWrites();return;}
        if(args.length==1&&args[0].equals("known-container-cleanup")){knownContainerCleanup();return;}
        if(args.length==2&&args[0].equals("core-native-state")){coreNativeState(args[1]);return;}
        if(args.length==1&&args[0].equals("core-native-bytes")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
            require(hostCrtBytes0(System.getProperty("ronova.pro.audit.allocatorFields")),"actual CRT byte operations failed");return;
        }
        if(args.length==1&&args[0].equals("core-native-nt-memory")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
            require(hostNtMemory0(System.getProperty("ronova.pro.audit.allocatorFields")),"actual NT and Win32 memory endpoints failed");return;
        }
        if(args.length==1&&args[0].equals("core-native-transactions")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
            hostTransactionState();return;
        }
        if(args.length==1&&args[0].equals("core-native-async")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
            hostTransactionAsync();return;
        }
        if(args.length==1&&args[0].equals("core-native-consumers")){
            hostThreadCreate();require(hostCngHash0(),"actual BCrypt lifecycle failed");hostTransactionState();storageNative();return;
        }
        if(args.length==1&&args[0].equals("unknown-carrier-graph")){unknownCarrierGraph();return;}
        if(args.length==1&&args[0].equals("unknown-map-key-semantics")){unknownMapKeySemantics();return;}
        if(args.length==1&&args[0].equals("event-image-composition")){eventImageComposition();return;}
        if(args.length==1&&args[0].equals("file-client-entries")){fileClientEntries();return;}
        if(args.length==1&&args[0].equals("client-dispatch-entry")){clientDispatchEntry();return;}
        if(args.length==1&&args[0].equals("scoped-transform-failure")){scopedTransformFailure();return;}
        if(args.length==1&&args[0].equals("startup-batches")){startupBatch();return;}
        if(args.length==1&&args[0].equals("retirement-readback")){retirementReadback();return;}
        if(args.length==1&&args[0].equals("physical-token")){physicalToken();return;}
        if(args.length==1&&args[0].equals("carrier-call-sites")){carrierCallSites();return;}
        if(args.length==1&&args[0].equals("queue-locations")){queueLocations();return;}
        if(args.length==1&&args[0].equals("root-retry-lifecycle-fixes")){definitionRetryLifecycle();nativeInstallLifecycle();return;}
        if(args.length==1&&args[0].equals("definition-retry")){definitionRetryLifecycle();return;}
        if(args.length==1&&args[0].equals("native-install-lifecycle")){nativeInstallLifecycle();return;}
        if(args.length==1&&args[0].equals("stop-policy-concurrency")){stopPolicyConcurrency();return;}
        if(args.length==1&&args[0].equals("external-root-candidates")){externalRootCandidates();return;}
        if(args.length==1&&args[0].equals("policy-native-capture-fixes")){
            stopPolicyPublication();taskCaptureScope();
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
            require(nativeVmResults0(),"Native VM original-result regression failed");
            System.out.println("NATIVE_VM_RESULTS_PASS actual_production_wrappers observer_failure_preserves_result exact_output_rejection_retained fixture_only");return;
        }
        if(args.length==1&&args[0].equals("task-lazy-capture")){taskLazyCapture();return;}
        if(args.length==1&&args[0].equals("container-scope-removal")){containerScopeRemoval();return;}
        if(args.length==1&&args[0].equals("startup-protection-bytecode")){startupReturnReplay();protectionSetterBytecode();return;}
        if(args.length==1&&args[0].equals("network-bytecode")){networkBytecode();return;}
        if(args.length==1&&args[0].equals("resource-bytecode")){resourceBytecode();return;}
        if(args.length==1&&args[0].equals("custom-carrier-bytecode")){carrierBytecode();capabilityBytecode();return;}
        if(args.length==1&&args[0].equals("equipment-carrier-delegate-bytecode")){carrierDelegateBytecode();capabilityBytecode();return;}
        if(args.length==1&&args[0].equals("equipment-slot-bytecode")){equipmentSlotBytecode();capabilityBytecode();return;}
        if(args.length==1&&args[0].equals("capability-callback-bytecode")){capabilityCallbackBytecode();return;}
        if(args.length==1&&args[0].equals("equipment-helper-composition-bytecode")){equipmentSlotBytecode();equipmentSlotHelperBytecode();equipmentFunctionBytecode();return;}
        if(args.length==1&&args[0].equals("equipment-defined-bi-bytecode")){equipmentDefinedCallbackBytecode();equipmentBiCompositionBytecode();return;}
        if(args.length==1&&args[0].equals("equipment-async-carrier-bytecode")){equipmentAsyncCarrierBytecode();return;}
        if(args.length==1&&args[0].equals("protection-setter-bytecode")){protectionSetterBytecode();return;}
        if(args.length==3&&args[0].equals("mod-group-returns")){modGroupReturns(args[1],args[2]);return;}
        if(args.length==2&&args[0].equals("network-preconnect")){networkPreconnect(args[1]);return;}
        if(args.length==1&&args[0].equals("source-list-fixes")){sourceListFixes();return;}
        if(args.length==1&&args[0].equals("live-control-writer")){liveControlWriter();return;}
        if(args.length==1&&args[0].equals("system-arraycopy")){systemArraycopy();return;}
        if(args.length==1&&args[0].equals("clone-boundary")){cloneBoundary();return;}
        if(args.length==1&&args[0].equals("buffer-release")){bufferRelease();return;}
        if(args.length==1&&args[0].equals("opaque-native-state")){opaqueNativeState();return;}
        if(args.length==1&&args[0].equals("private-native-state")){privateNativeState();return;}
        if(args.length==1&&args[0].equals("private-native-state-seams")){privateNativeStateSeams(true);return;}
        if(args.length==1&&args[0].equals("private-object-state-seam")){privateNativeStateSeams(false);return;}
        if(args.length==1&&args[0].equals("private-other-state-seams")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
            require(privateOtherState0(),"allocator context or C++ original-call lifetime failed");return;
        }
        if(args.length==1&&args[0].equals("private-custom-allocator-fields-seam")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
            require(privateCustomAllocatorState0(System.getProperty("ronova.pro.audit.allocatorFields")),"custom allocator or typed field lifetime failed");return;
        }
        if(args.length==1&&args[0].equals("buffer-wrap")){bufferWrap();return;}
        if(args.length==1&&args[0].equals("buffer-font")){bufferFont();return;}
        if(args.length==1&&args[0].equals("native-image-srg")){nativeImageSrg();return;}
        if(args.length==1&&args[0].equals("file-transfer")){fileTransfer();return;}
        if(args.length==1&&args[0].equals("socket-accept")){socketAccept();return;}
        if(args.length==1&&args[0].equals("host-load")){hostLoad();return;}
        if(args.length==1&&args[0].equals("jvmti-dispatch")){jvmtiDispatch();return;}
        if(args.length==1&&args[0].equals("host-thread-create")){hostThreadCreate();return;}
        if(args.length==1&&args[0].equals("world-held-save")){worldHeldSave();return;}
        if(args.length==1&&args[0].equals("storage-native")){storageNative();return;}
        if(args.length==1&&args[0].equals("host-process")){System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());hostProcess0();System.out.println("HOST_PROCESS_PASS actual_windows_children A_W_imports A_W_dynamic suspended_preserved stopped_source_refused cached_result_refused unrelated_original_preserved retired_image_refused handles_retired");return;}
        ProRuntime runtime=(ProRuntime)U.allocateInstance(ProRuntime.class);
        Map<UUID,Object> work=new LinkedHashMap<>();put(runtime,"work",work);
        var subject=new ProRuntime.Subject(UUID.randomUUID());var records=(EntityRecords)U.allocateInstance(EntityRecords.class);put(records,"originalPassengers",List.of());
        var binding=(ProRuntime.Binding)U.allocateInstance(ProRuntime.Binding.class);put(binding,"subject",subject);put(binding,"records",records);
        Class<?> rootWork=Class.forName("dev.ronova.pro.ProRuntime$Work");var rootConstructor=rootWork.getDeclaredConstructor(ProRuntime.Binding.class,BlockRecords.class,long.class,String.class);rootConstructor.setAccessible(true);Object retired=rootConstructor.newInstance(binding,null,0L,"PROTECT");put(retired,"referencesReleased",true);put(retired,"cancelled",true);put(retired,"state","FENCED");put(retired,"ack",CompletableFuture.completedFuture(null));
        for(int i=0;i<65536;i++)work.put(new UUID(1,i),retired);
        invoke(runtime,"ensureWorkCapacity");require(work.size()==65535,"retired history did not free an admission slot");
        Object pending=rootConstructor.newInstance(binding,null,0L,"CLEAR");put(pending,"ack",new CompletableFuture<>());work.clear();
        for(int i=0;i<65536;i++)work.put(new UUID(2,i),pending);
        try {invoke(runtime,"ensureWorkCapacity");throw new AssertionError("unsettled budget accepted");}
        catch(IllegalStateException expected){require(work.size()==65536&&!subject.terminal&&subject.generation==0,"budget rejection changed duties or authority");}
        System.out.println("CAPACITY_METADATA_PASS retired_slot_reclaimed pending_duties_retained authority_unchanged");

        Class<?> descriptor=Class.forName("dev.ronova.pro.RecoveryStorage$Descriptor");var constructor=descriptor.getDeclaredConstructors()[0];constructor.setAccessible(true);
        Object d=constructor.newInstance("STORAGE/2",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1L,"source","code",1L,"identity","0".repeat(64),"1".repeat(64),"2".repeat(64),UUID.randomUUID(),"win32-ntfs-txf/2",null);
        Class<?> storageWork=Class.forName("dev.ronova.pro.RecoveryStorage$Work");var restoredConstructor=storageWork.getDeclaredConstructor(descriptor);restoredConstructor.setAccessible(true);Object restored=restoredConstructor.newInstance(d);
        var storage=(RecoveryStorage)U.allocateInstance(RecoveryStorage.class);
        put(storage,"work",new java.util.concurrent.ConcurrentSkipListMap<>(Map.of(UUID.randomUUID(),restored)));
        var worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1));put(storage,"worker",worker);
        storage.close();var nativeReleased=storageWork.getDeclaredField("nativeReleased");nativeReleased.setAccessible(true);var resourcesConfirmed=storageWork.getDeclaredField("resourcesConfirmed");resourcesConfirmed.setAccessible(true);
        require(worker.isTerminated()&&!nativeReleased.getBoolean(restored)&&!resourcesConfirmed.getBoolean(restored),"shutdown fabricated a historic transaction settlement");
        System.out.println("STORAGE_SHUTDOWN_METADATA_PASS no_current_handle historic_unknown_preserved worker_closed");
    }

    public static final class UnknownMapKey implements Comparable<UnknownMapKey> {
        public int id;public String label="collision";public byte[] salt={0};
        public UnknownMapKey(int id){this.id=id;}
        public int hashCode(){return label.hashCode()+salt[0];}
        public boolean equals(Object other){return other instanceof UnknownMapKey key&&id==key.id;}
        public int compareTo(UnknownMapKey other){return Integer.compare(id,other.id);}
    }
    private static EquipmentSlotGraph.QueryKeys UNKNOWN_KEY_QUERY;private static Object UNKNOWN_KEY_OTHER,UNKNOWN_KEY_MUTATE_RECEIVER;
    private static Runnable UNKNOWN_KEY_MUTATE;private static int UNKNOWN_KEY_EXECUTIONS,UNKNOWN_KEY_HELPERS,UNKNOWN_KEY_BUILTINS;
    private static final List<EquipmentSlotGraph.KeySemantics> UNKNOWN_KEY_RESULTS=new ArrayList<>();
    private static final List<Boolean> UNKNOWN_KEY_EQUAL_RESULTS=new ArrayList<>();
    private static UnknownKeyCapture UNKNOWN_KEY_CAPTURE;private static UnknownKeyCall UNKNOWN_KEY_CALL;
    private static final class UnknownKeyCapture {
        final Object receiver;final String name,descriptor;final Object[] arguments;
        final List<EquipmentSlotGraph.Read> reads=new ArrayList<>();final List<EquipmentSlotGraph.MethodRead> methods=new ArrayList<>();
        UnknownKeyCapture(Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments){
            this.receiver=receiver;this.name=name;this.descriptor=descriptor;this.arguments=arguments.clone();
            methods.add(EquipmentSlotGraph.methodRead(declaring,name,descriptor,carrierSiteImage0(declaring,name,descriptor)));
        }
    }
    private static final class UnknownKeyCall {
        final UnknownKeyCall previous=UNKNOWN_KEY_CALL;
        final UnknownKeyCapture key;final Object receiver;final Class<?> symbolic,caller;final String name,descriptor;final int opcode;final Object[] arguments;final boolean builtin;Object result;
        UnknownKeyCall(UnknownKeyCapture key,Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Object[] arguments,boolean builtin){
            this.key=key;this.receiver=receiver;this.symbolic=symbolic;this.caller=key.receiver.getClass();this.name=name;this.descriptor=descriptor;this.opcode=opcode;this.arguments=arguments.clone();this.builtin=builtin;
        }
    }
    private static Object unknownKeyBegin(Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments){
        UNKNOWN_KEY_EXECUTIONS++;if(UNKNOWN_KEY_QUERY==null)return null;
        require(UNKNOWN_KEY_CAPTURE==null,"fixture key callback reentered without its original helper edge");
        List<StackWalker.StackFrame> frames=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->stream.dropWhile(frame->frame.getDeclaringClass()!=declaring||!frame.getMethodName().equals(name)||!frame.getDescriptor().equals(descriptor)).toList());
        require(EquipmentSlotGraph.keyCall(UNKNOWN_KEY_QUERY,receiver,declaring,name,descriptor,arguments,frames),"actual natural JDK map-to-key path was unresolved");
        require(!EquipmentSlotGraph.keyCall(UNKNOWN_KEY_QUERY,UNKNOWN_KEY_OTHER,declaring,name,descriptor,arguments,frames),"different same-Class key receiver borrowed the query");
        return UNKNOWN_KEY_CAPTURE=new UnknownKeyCapture(receiver,declaring,name,descriptor,arguments);
    }
    private static void unknownKeyEnd(Object token,Object result){
        require(token==UNKNOWN_KEY_CAPTURE&&UNKNOWN_KEY_CALL==null,"fixture key result lost its original method");UnknownKeyCapture key=UNKNOWN_KEY_CAPTURE;
        UNKNOWN_KEY_RESULTS.add(EquipmentSlotGraph.keySemantics(key.receiver,key.name,key.descriptor,key.arguments,result,key.reads,key.methods));
        if(key.name.equals("equals"))UNKNOWN_KEY_EQUAL_RESULTS.add((Boolean)result);UNKNOWN_KEY_CAPTURE=null;
    }
    private static void unknownKeyFailed(Object token,Throwable original){throw new AssertionError("fixture natural key unexpectedly failed",original);}
    private static void unknownKeyField(Object token,Object holder,Class<?> symbolic,String name,String descriptor,Object value){
        require(token==UNKNOWN_KEY_CAPTURE,"fixture field read left its natural key");UNKNOWN_KEY_CAPTURE.reads.add(EquipmentSlotGraph.fieldRead(holder==null?symbolic:holder,symbolic,name,descriptor,value));
    }
    private static void unknownKeyArray(Object token,Object array,int index,Object value){
        require(token==UNKNOWN_KEY_CAPTURE,"fixture array read left its natural key");Object normalized=array instanceof byte[]&&value instanceof Integer number?Byte.valueOf((byte)number.intValue()):value;
        UNKNOWN_KEY_CAPTURE.reads.add(new EquipmentSlotGraph.ArrayRead(array,index,normalized));
    }
    private static void unknownKeyUnresolved(Object token){throw new AssertionError("pure fixture key trace became unresolved");}
    private static Object unknownKeyCallBegin(Object token,Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Object[] arguments){
        require(token==UNKNOWN_KEY_CAPTURE,"fixture helper nested outside its actual key");
        boolean builtin=EquipmentSlotGraph.keyReadSupported(receiver,symbolic,name,descriptor,opcode,UNKNOWN_KEY_CAPTURE.receiver.getClass(),arguments);
        require(builtin||receiver==UNKNOWN_KEY_CAPTURE.receiver&&name.equals("compareTo"),"fixture encountered an unselected helper");
        if(builtin)UNKNOWN_KEY_BUILTINS++;return UNKNOWN_KEY_CALL=new UnknownKeyCall(UNKNOWN_KEY_CAPTURE,receiver,symbolic,name,descriptor,opcode,arguments,builtin);
    }
    private static void unknownKeyCallEnd(Object token,Object result){
        require(token==UNKNOWN_KEY_CALL,"fixture helper result changed its call");UnknownKeyCall call=UNKNOWN_KEY_CALL;
        if(call.builtin)call.key.reads.add(EquipmentSlotGraph.keyRead(call.receiver,call.symbolic,call.name,call.descriptor,call.opcode,call.caller,call.arguments,result));
        else require(call.result instanceof Integer actual&&result instanceof Integer returned&&actual.intValue()==returned.intValue(),"fixture typed bridge changed original return");UNKNOWN_KEY_CALL=call.previous;
    }
    private static Object unknownKeyHelperBegin(Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments){
        if(UNKNOWN_KEY_CAPTURE==null)return null;UnknownKeyCall call=UNKNOWN_KEY_CALL;
        require(call!=null&&!call.builtin&&call.receiver==receiver&&call.name.equals(name)&&call.descriptor.equals(descriptor),"fixture typed helper did not follow its original invoke");
        UNKNOWN_KEY_CAPTURE.methods.add(EquipmentSlotGraph.methodRead(declaring,name,descriptor,carrierSiteImage0(declaring,name,descriptor)));UNKNOWN_KEY_HELPERS++;return UNKNOWN_KEY_CAPTURE;
    }
    private static void unknownKeyHelperEnd(Object token,Object result){require(token==UNKNOWN_KEY_CAPTURE&&UNKNOWN_KEY_CALL!=null,"fixture typed helper lost its key");UNKNOWN_KEY_CALL.result=result;}
    private static Object unknownMapRead(Object receiver,Field field){
        Object result=unknownCarrierRead(receiver,field);
        if(receiver==UNKNOWN_KEY_MUTATE_RECEIVER&&field.getName().equals("value")){Runnable mutation=UNKNOWN_KEY_MUTATE;UNKNOWN_KEY_MUTATE_RECEIVER=null;UNKNOWN_KEY_MUTATE=null;mutation.run();}
        return result;
    }
    private static Object unknownMapStatic(Class<?> declaring,String name,String descriptor)throws Exception{
        Field field=declaring.getDeclaredField(name);require(Modifier.isStatic(field.getModifiers())&&field.getType().descriptorString().equals(descriptor),"fixture static tuple mismatch");
        Object base=U.staticFieldBase(field);long at=U.staticFieldOffset(field);Class<?> type=field.getType();
        if(!type.isPrimitive())return U.getObjectVolatile(base,at);if(type==boolean.class)return U.getBooleanVolatile(base,at);if(type==int.class)return U.getIntVolatile(base,at);
        if(type==byte.class)return U.getByteVolatile(base,at);if(type==short.class)return U.getShortVolatile(base,at);if(type==char.class)return U.getCharVolatile(base,at);
        if(type==long.class)return U.getLongVolatile(base,at);if(type==float.class)return U.getFloatVolatile(base,at);return U.getDoubleVolatile(base,at);
    }
    private static Object[] unknownMapKeyBody()throws Exception{
        String original=UnknownMapKey.class.getName().replace('.','/'),alias="dev.ronova.pro.CheckedUnknownMapKey";
        var node=new org.objectweb.asm.tree.ClassNode();try(var input=UnknownMapKey.class.getResourceAsStream("/"+original+".class")){new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(node,0);}
        node.nestHostClass=null;node.nestMembers=null;node.innerClasses.clear();node.outerClass=null;node.outerMethod=null;node.outerMethodDesc=null;
        var remapped=new org.objectweb.asm.ClassWriter(0);node.accept(new org.objectweb.asm.commons.ClassRemapper(remapped,new org.objectweb.asm.commons.SimpleRemapper(original,alias.replace('.','/'))));
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode"),methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode");
        Object transformed=nodeType.getConstructor().newInstance(),reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader").getConstructor(byte[].class).newInstance((Object)remapped.toByteArray());
        reader.getClass().getMethod("accept",visitor,int.class).invoke(reader,transformed,0);
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentSlotBoundary");Method key=boundary.getDeclaredMethod("weaveKey",String.class,methodType),helper=boundary.getDeclaredMethod("weave",String.class,methodType,boolean.class);key.setAccessible(true);helper.setAccessible(true);
        for(Object method:(List<?>)nodeType.getField("methods").get(transformed)){
            String name=(String)methodType.getField("name").get(method),descriptor=(String)methodType.getField("desc").get(method);if(name.startsWith("<"))continue;
            if(name.equals("hashCode")||name.equals("equals")||name.equals("compareTo")&&descriptor.equals("(Ljava/lang/Object;)I"))key.invoke(null,alias.replace('.','/'),method);
            else helper.invoke(null,alias.replace('.','/'),method,true);
        }
        var constructor=Class.forName("dev.ronova.pro.agent.ControlClassWriter").getDeclaredConstructor(ClassLoader.class,nodeType);constructor.setAccessible(true);Object writer=constructor.newInstance(AuditFixCheck.class.getClassLoader(),transformed);nodeType.getMethod("accept",visitor).invoke(transformed,writer);
        byte[] bytes=(byte[])writer.getClass().getMethod("toByteArray").invoke(writer);
        Class<?> result=new ClassLoader(AuditFixCheck.class.getClassLoader()){Class<?> body(){Class<?> type=defineClass(alias,bytes,0,bytes.length);resolveClass(type);return type;}}.body();return new Object[]{result,bytes};
    }
    private static Object unknownMapCurrent(Object held)throws Exception{
        Class<?> uses=Class.forName("dev.ronova.pro.EquipmentSlotGraph$Uses");var constructor=uses.getDeclaredConstructor(int.class);constructor.setAccessible(true);
        Method current=held.getClass().getDeclaredMethod("current",uses,boolean.class);current.setAccessible(true);
        try{return current.invoke(held,constructor.newInstance(0),false);}catch(InvocationTargetException failure){if(failure.getCause() instanceof EquipmentSlotGraph.Unresolved unresolved)throw unresolved;throw failure;}
    }
    private static Object unknownMapHeld(EquipmentSlotGraph.Read read)throws Exception{
        var constructor=Class.forName("dev.ronova.pro.EquipmentSlotGraph$HeldRead").getDeclaredConstructor(EquipmentSlotGraph.Read.class);constructor.setAccessible(true);return constructor.newInstance(read);
    }
    private static void unknownMapStale(Object held,String reason)throws Exception{
        try {unknownMapCurrent(held);throw new AssertionError(reason);}catch(EquipmentSlotGraph.Unresolved expected){ }
    }
    private static Object unknownMapNode(HashMap<Object,Object> map,Object value)throws Exception{
        Object[] table=(Object[])unknownCarrierRead(map,HashMap.class.getDeclaredField("table"));Class<?> node=Class.forName("java.util.HashMap$Node");Field next=node.getDeclaredField("next"),stored=node.getDeclaredField("value");
        for(Object head:table)for(Object at=head;at!=null;at=unknownCarrierRead(at,next))if(unknownCarrierRead(at,stored)==value)return at;throw new AssertionError("fixture actual node unavailable");
    }
    private static void unknownMapKeySemantics()throws Exception{
        slotKeyBytecode();System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),sources=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);Field slots=controls.getDeclaredField("equipmentSlots");slots.setAccessible(true);Object previousSlots=slots.get(null);
        Map<Field,Object> readers=new LinkedHashMap<>();var lookup=MethodHandles.lookup();
        MethodHandle fields=lookup.findStatic(AuditFixCheck.class,"unknownCarrierFields",MethodType.methodType(Object[][].class,Object.class));
        MethodHandle read=lookup.findStatic(AuditFixCheck.class,"unknownMapRead",MethodType.methodType(Object.class,Object.class,Field.class));
        Map<String,MethodHandle> graph=Map.of("READ",read,"STATIC",lookup.findStatic(AuditFixCheck.class,"unknownMapStatic",MethodType.methodType(Object.class,Class.class,String.class,String.class)),
                "METHOD",lookup.findStatic(AuditFixCheck.class,"carrierSiteMethod0",MethodType.methodType(Class.class,Object.class,Class.class,String.class,String.class,int.class,Class.class)),
                "IMAGE",lookup.findStatic(AuditFixCheck.class,"carrierSiteImage0",MethodType.methodType(long[].class,Class.class,String.class,String.class)),
                "FRAME",lookup.findStatic(AuditFixCheck.class,"carrierSiteFrame0",MethodType.methodType(long[].class,Class.class,String.class,String.class,int.class)),
                "DEPTH",lookup.findStatic(AuditFixCheck.class,"carrierSiteDepth0",MethodType.methodType(int.class,Class.class,String.class,String.class)));
        Class.forName(EquipmentCarrierLayouts.class.getName(),true,EquipmentCarrierLayouts.class.getClassLoader());Class.forName(EquipmentSlotGraph.class.getName(),true,EquipmentSlotGraph.class.getClassLoader());
        for(Class<?> type:List.of(EquipmentCarrierGraph.class,EquipmentCarrierLayouts.class))for(String name:List.of("FIELDS","READ")){Field field=type.getDeclaredField(name);field.setAccessible(true);readers.put(field,field.get(null));}
        for(String name:graph.keySet()){Field field=EquipmentSlotGraph.class.getDeclaredField(name);field.setAccessible(true);readers.put(field,field.get(null));}
        CARRIER_SITE_ACTIVE=true;carrierSiteSetup0(controls,sources);
        try {
            for(Field field:readers.keySet()){MethodHandle replacement=field.getDeclaringClass()==EquipmentSlotGraph.class?graph.get(field.getName()):field.getName().equals("FIELDS")?fields:read;U.putObjectVolatile(U.staticFieldBase(field),U.staticFieldOffset(field),replacement);}
            MethodType keyType=MethodType.methodType(Object.class,Object.class,Class.class,String.class,String.class,Object[].class),endType=MethodType.methodType(void.class,Object.class,Object.class),failedType=MethodType.methodType(void.class,Object.class,Throwable.class);
            MethodType[] types={MethodType.methodType(Object.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Class.class,String.class,String.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,int.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,boolean.class),MethodType.methodType(void.class,Object.class,Object.class,String.class,String.class,Object.class),MethodType.methodType(void.class,Object.class),endType,failedType,MethodType.methodType(Object.class,Object.class,Object.class,Class.class,String.class,String.class,int.class,Object[].class),endType,failedType,keyType,endType,failedType,keyType,endType,failedType};
            MethodHandle[] callbacks=new MethodHandle[types.length];for(int at=0;at<types.length;at++)callbacks[at]=MethodHandles.empty(types[at]);
            String[] names={null,"unknownKeyField","unknownKeyArray",null,null,"unknownKeyUnresolved",null,"unknownKeyFailed","unknownKeyCallBegin","unknownKeyCallEnd","unknownKeyFailed","unknownKeyHelperBegin","unknownKeyHelperEnd","unknownKeyFailed","unknownKeyBegin","unknownKeyEnd","unknownKeyFailed"};
            for(int at=0;at<types.length;at++)if(names[at]!=null)callbacks[at]=lookup.findStatic(AuditFixCheck.class,names[at],types[at]);slots.set(null,callbacks);
            Object[] body=unknownMapKeyBody();Class<?> type=(Class<?>)body[0];var constructor=type.getConstructor(int.class);HashMap<Object,Object> map=new HashMap<>(64);Object[] values=new Object[12];
            for(int at=0;at<values.length;at++){values[at]=new Object();map.put(constructor.newInstance(at),values[at]);}
            Object query=constructor.newInstance(7);UNKNOWN_KEY_OTHER=constructor.newInstance(7);UNKNOWN_KEY_RESULTS.clear();UNKNOWN_KEY_EQUAL_RESULTS.clear();UNKNOWN_KEY_HELPERS=UNKNOWN_KEY_BUILTINS=0;
            UNKNOWN_KEY_QUERY=EquipmentSlotGraph.prepareKeys(map,query,Map.class,"get","(Ljava/lang/Object;)Ljava/lang/Object;",185,AuditFixCheck.class);
            require(EquipmentSlotGraph.keyTargets(UNKNOWN_KEY_QUERY).size()==3,"fixture hash/equality/comparison Java targets unavailable");
            Object result=map.get(query);require(result==values[7]&&UNKNOWN_KEY_EQUAL_RESULTS.contains(Boolean.FALSE)&&UNKNOWN_KEY_EQUAL_RESULTS.contains(Boolean.TRUE)&&UNKNOWN_KEY_HELPERS>0&&UNKNOWN_KEY_BUILTINS>1,"actual colliding tree lookup did not capture natural key semantics");
            Object held=unknownMapHeld(EquipmentSlotGraph.containerRead(UNKNOWN_KEY_QUERY,result,UNKNOWN_KEY_RESULTS));UNKNOWN_KEY_QUERY=null;int executions=UNKNOWN_KEY_EXECUTIONS;
            require(unknownMapCurrent(held)==result&&UNKNOWN_KEY_EXECUTIONS==executions,"slot readback replayed hashCode/equals/compareTo or lost actual hit");
            Field id=type.getField("id"),salt=type.getField("salt");id.setInt(query,99);unknownMapStale(held,"mutable key field kept old slot semantics");id.setInt(query,7);
            byte[] bytes=(byte[])salt.get(query);bytes[0]=1;unknownMapStale(held,"mutable key array kept old hash semantics");bytes[0]=0;
            UNKNOWN_KEY_MUTATE_RECEIVER=unknownMapNode(map,result);UNKNOWN_KEY_MUTATE=()->{try{id.setInt(query,99);}catch(IllegalAccessException failure){throw new AssertionError(failure);}};
            unknownMapStale(held,"terminal value read bypassed the final key dependency recheck");id.setInt(query,7);require(UNKNOWN_KEY_MUTATE_RECEIVER==null,"fixture terminal mutation was not reached");
            var changed=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader((byte[])body[1]).accept(changed,0);for(var method:changed.methods)if(method.name.equals("hashCode"))method.instructions.insert(new org.objectweb.asm.tree.InsnNode(0));var writer=new org.objectweb.asm.ClassWriter(0);changed.accept(writer);carrierSiteRedefine0(type,writer.toByteArray());
            unknownMapStale(held,"real key method redefinition kept old semantic evidence");require(UNKNOWN_KEY_EXECUTIONS==executions,"dependency/version checks replayed an original key method");
            UNKNOWN_KEY_RESULTS.clear();UNKNOWN_KEY_EQUAL_RESULTS.clear();Object missing=constructor.newInstance(99);UNKNOWN_KEY_OTHER=constructor.newInstance(99);
            UNKNOWN_KEY_QUERY=EquipmentSlotGraph.prepareKeys(map,missing,Map.class,"get","(Ljava/lang/Object;)Ljava/lang/Object;",185,AuditFixCheck.class);Object absent=map.get(missing);require(absent==null,"actual missing colliding key unexpectedly hit");
            Object missingHeld=unknownMapHeld(EquipmentSlotGraph.containerRead(UNKNOWN_KEY_QUERY,absent,UNKNOWN_KEY_RESULTS));UNKNOWN_KEY_QUERY=null;executions=UNKNOWN_KEY_EXECUTIONS;require(unknownMapCurrent(missingHeld)==null&&UNKNOWN_KEY_EXECUTIONS==executions,"missing-key readback replayed methods or invented a slot");
        } finally {
            UNKNOWN_KEY_QUERY=null;UNKNOWN_KEY_CAPTURE=null;UNKNOWN_KEY_CALL=null;UNKNOWN_KEY_MUTATE_RECEIVER=null;UNKNOWN_KEY_MUTATE=null;UNKNOWN_KEY_RESULTS.clear();slots.set(null,previousSlots);
            for(var saved:readers.entrySet())U.putObjectVolatile(U.staticFieldBase(saved.getKey()),U.staticFieldOffset(saved.getKey()),saved.getValue());carrierSiteStop0();CARRIER_SITE_ACTIVE=false;
        }
        System.out.println("UNKNOWN_MAP_KEY_SEMANTICS_PASS production_KEY_weaving actual_HashMap_tree_hit_collision_miss String_hash_and_Integer_compare_delegates mutable_field_array_terminal_change_and_actual_redefine_refused no_hash_equals_compare_replay metadata_registration_fixture_only_no_live_Core_authorization");
    }
    private interface UnknownCarrierFunction {Object apply(Object value);}
    private interface UnknownCarrierBusiness {Object read();void write(Object value);}
    private static final class UnknownCarrierLeaf {int amount=7;Object extra;}
    private static final class UnknownCarrierValue implements UnknownCarrierFunction {
        final UnknownCarrierLeaf payload=new UnknownCarrierLeaf();
        final Object captured=new UnknownCarrierLeaf();
        final java.util.function.Supplier<Object> callback=()->{unknownCarrierCalls++;return captured;};
        final Thread borrowed=new Thread();
        public Object apply(Object value){unknownCarrierCalls++;return payload;}
        Object apply(String value){unknownCarrierCalls++;return value;}
        int amount(){unknownCarrierCalls++;return payload.amount;}
    }
    private static final class UnknownCarrierBusinessValue implements UnknownCarrierBusiness {
        Object payload;
        public Object read(){unknownCarrierCalls++;return payload;}
        public void write(Object value){unknownCarrierCalls++;payload=value;}
    }
    private static final class UnknownCarrierWrapper {Object value;UnknownCarrierWrapper(Object value){this.value=value;}}
    private static final class UnknownCarrierKey {public int hashCode(){return 1;}}
    private static int unknownCarrierCalls,unknownCarrierMutationAfter;
    private static Object unknownCarrierMutationReceiver;
    private static Runnable unknownCarrierMutation;
    /** Metadata seam only: real Java declarations, VM offsets and current volatile field reads. */
    private static Object[][] unknownCarrierFields(Object receiver)throws Exception {
        if(receiver==unknownCarrierMutationReceiver&&--unknownCarrierMutationAfter==0){
            Runnable mutation=unknownCarrierMutation;unknownCarrierMutationReceiver=null;unknownCarrierMutation=null;mutation.run();
        }
        List<Object[]> fields=new ArrayList<>();
        for(Class<?> type=receiver.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers()))continue;Class<?> fieldType=field.getType();
            int width=!fieldType.isPrimitive()?U.arrayIndexScale(Object[].class):fieldType==boolean.class||fieldType==byte.class?1:fieldType==char.class||fieldType==short.class?2:fieldType==long.class||fieldType==double.class?8:4;
            fields.add(new Object[]{field,type,field.getName(),fieldType.descriptorString(),fieldType,U.objectFieldOffset(field),width,unknownCarrierRead(receiver,field)});
        }
        return fields.toArray(Object[][]::new);
    }
    private static Object unknownCarrierRead(Object receiver,Field field){
        long offset=U.objectFieldOffset(field);Class<?> type=field.getType();
        if(!type.isPrimitive())return U.getObjectVolatile(receiver,offset);
        if(type==boolean.class)return U.getBooleanVolatile(receiver,offset);if(type==byte.class)return U.getByteVolatile(receiver,offset);
        if(type==char.class)return U.getCharVolatile(receiver,offset);if(type==short.class)return U.getShortVolatile(receiver,offset);
        if(type==int.class)return U.getIntVolatile(receiver,offset);if(type==long.class)return U.getLongVolatile(receiver,offset);
        if(type==float.class)return U.getFloatVolatile(receiver,offset);return U.getDoubleVolatile(receiver,offset);
    }
    private static long unknownCarrierOffset(Class<?> type,String name)throws Exception{return U.objectFieldOffset(type.getDeclaredField(name));}
    private static void unknownCarrierChanged(Object receiver,int after,Runnable mutation){unknownCarrierMutationReceiver=receiver;unknownCarrierMutationAfter=after;unknownCarrierMutation=mutation;}
    private static void unknownCarrierMixedImage(Runnable observe,String path){
        try{observe.run();throw new AssertionError("mixed carrier graph accepted: "+path);}
        catch(IllegalStateException expected){require(expected.getMessage().contains("PATH_CHANGED")||expected.getMessage().contains("LAYOUT_FIELD_CHANGED")||expected.getMessage().contains("MIXED_FIELD_IMAGE"),"wrong mixed-graph rejection: "+expected);}
        finally{unknownCarrierMutationReceiver=null;unknownCarrierMutation=null;}
    }
    private static void unknownCarrierGraph()throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        try {net.minecraft.server.Bootstrap.bootStrap();}
        catch(ExceptionInInitializerError unavailable) {
            Throwable cause=unavailable;while(cause.getCause()!=null)cause=cause.getCause();
            boolean network=Arrays.stream(unavailable.getStackTrace()).anyMatch(frame->frame.getClassName().equals("net.minecraftforge.network.NetworkHooks")&&frame.getMethodName().equals("init"));
            if(!network||!(cause instanceof NoSuchMethodException)||!"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage()))throw unavailable;
            // Vanilla registries and the Forge vanilla snapshot precede networking
            // in this Bootstrap body. This data-only fixture uses neither network
            // events nor the absent ModLauncher event-constructor transformation.
            require(net.minecraft.world.item.ItemStack.EMPTY!=null,"fixture registry initialization failed");
            System.out.println("GRAPH_FIXTURE_FORGE_NETWORK_NOT_INITIALIZED data_only_no_network_or_game_startup_claim");
        }
        Field policy=EquipmentPolicy.class.getDeclaredField("initialized");policy.setAccessible(true);require(!policy.getBoolean(null),"graph metadata fixture requires uninstalled equipment policy");
        Class.forName(EquipmentCarrierLayouts.class.getName(),true,EquipmentCarrierLayouts.class.getClassLoader());
        Map<Field,Object> readers=new LinkedHashMap<>();
        MethodHandle fields=MethodHandles.lookup().findStatic(AuditFixCheck.class,"unknownCarrierFields",MethodType.methodType(Object[][].class,Object.class));
        MethodHandle read=MethodHandles.lookup().findStatic(AuditFixCheck.class,"unknownCarrierRead",MethodType.methodType(Object.class,Object.class,Field.class));
        for(Class<?> type:List.of(EquipmentCarrierGraph.class,EquipmentCarrierLayouts.class))for(String name:List.of("FIELDS","READ")){
            Field field=type.getDeclaredField(name);field.setAccessible(true);readers.put(field,field.get(null));
        }
        unknownCarrierCalls=0;
        try {
            for(Field field:readers.keySet())U.putObjectVolatile(U.staticFieldBase(field),U.staticFieldOffset(field),field.getName().equals("FIELDS")?fields:read);
            UnknownCarrierValue value=new UnknownCarrierValue();ConcurrentHashMap<String,Object> map=new ConcurrentHashMap<>();map.put("payload",value);
            UnknownCarrierWrapper wrapper=new UnknownCarrierWrapper(map);EquipmentCarrierGraph.View graph=EquipmentCarrierGraph.candidate(null,wrapper);
            require(graph.contains(wrapper)&&graph.contains(map)&&graph.contains(value)&&graph.contains(value.payload),"unknown wrapper to actual CHM to mutable business fields was not retained");
            require(graph.node(value.payload).fields.stream().anyMatch(slot->slot.field().getName().equals("amount"))
                    &&!graph.contains(value.callback)&&!graph.contains(value.borrowed),"ordinary scalar storage or borrowed callback/thread boundary changed");
            map.remove("payload");require(!EquipmentCarrierGraph.candidate(null,wrapper).contains(value),"detached actual CHM business value retained ownership");map.put("payload",value);
            UnknownCarrierLeaf replacement=new UnknownCarrierLeaf();
            unknownCarrierChanged(value.payload,1,()->wrapper.value=replacement);
            unknownCarrierMixedImage(()->EquipmentCarrierGraph.candidate(null,wrapper),"custom field detached during child traversal");wrapper.value=map;
            Object[] array={value.payload};wrapper.value=array;
            unknownCarrierChanged(value.payload,1,()->array[0]=replacement);
            unknownCarrierMixedImage(()->EquipmentCarrierGraph.candidate(null,wrapper),"actual array slot detached during child traversal");wrapper.value=map;
            long table=unknownCarrierOffset(ConcurrentHashMap.class,"table");Object oldTable=U.getObjectVolatile(map,table);
            ConcurrentHashMap<String,Object> other=new ConcurrentHashMap<>();other.put("replacement",replacement);Object otherTable=U.getObjectVolatile(other,table);
            try {
                unknownCarrierChanged(map,2,()->U.putObjectVolatile(map,table,otherTable));
                unknownCarrierMixedImage(()->EquipmentCarrierGraph.candidate(null,wrapper),"layout child image versus newly read actual table field");
            } finally {U.putObjectVolatile(map,table,oldTable);}
            unknownCarrierDeque();unknownCarrierConcurrent(map,value,replacement);
            require(EquipmentPolicy.functionalCallbackMethod(value,UnknownCarrierValue.class,"apply","(Ljava/lang/Object;)Ljava/lang/Object;")
                    &&!EquipmentPolicy.functionalCallbackMethod(value,UnknownCarrierValue.class,"apply","(Ljava/lang/String;)Ljava/lang/Object;")
                    &&!EquipmentPolicy.functionalCallbackMethod(value,UnknownCarrierValue.class,"amount","()I"),"custom SAM classification borrowed authority for an ordinary overload/data method");
            UnknownCarrierBusinessValue business=new UnknownCarrierBusinessValue();business.payload=value.payload;
            require(!EquipmentPolicy.functionalCallbackMethod(business,UnknownCarrierBusinessValue.class,"read","()Ljava/lang/Object;")
                    &&!EquipmentPolicy.functionalCallbackMethod(business,UnknownCarrierBusinessValue.class,"write","(Ljava/lang/Object;)V")
                    &&EquipmentCarrierGraph.candidate(null,business).contains(value.payload),"multi-method business interface lost actual data-field coverage");
            require(unknownCarrierCalls==0,"graph/classification replayed a business getter or supplier callback");
        } finally {
            unknownCarrierMutationReceiver=null;unknownCarrierMutation=null;
            for(var saved:readers.entrySet())U.putObjectVolatile(U.staticFieldBase(saved.getKey()),U.staticFieldOffset(saved.getKey()),saved.getValue());
        }
        System.out.println("UNKNOWN_CARRIER_GRAPH_PASS actual_wrapper_CHM_mutable_fields detached_field_array_layout_images_refused Deque_ring_unused_excluded intermediate_publication_removal_preserved CHM_resize_forwarding_reservation_tree_write_reachable_union custom_SAM_overload_business_methods_distinguished no_getter_supplier_replay metadata_reader_fixture_only_no_game_authority");
    }
    private static void unknownCarrierDeque()throws Exception {
        ArrayDeque<Object> deque=new ArrayDeque<>(8);for(int at=0;at<6;at++)deque.addLast(new UnknownCarrierLeaf());for(int at=0;at<5;at++)deque.removeFirst();
        for(int at=0;at<4;at++)deque.addLast(new UnknownCarrierLeaf());
        long elements=unknownCarrierOffset(ArrayDeque.class,"elements"),headOffset=unknownCarrierOffset(ArrayDeque.class,"head"),tailOffset=unknownCarrierOffset(ArrayDeque.class,"tail");
        Object[] array=(Object[])U.getObjectVolatile(deque,elements);int head=U.getIntVolatile(deque,headOffset),tail=U.getIntVolatile(deque,tailOffset);
        require(tail<head,"actual Deque fixture did not wrap its ring");int idle=(tail+1)%array.length;Object unrelated=new UnknownCarrierLeaf();array[idle]=unrelated;
        EquipmentCarrierGraph.View graph=EquipmentCarrierGraph.candidate(null,deque);
        require(!graph.contains(unrelated)&&!graph.node(array).contains(idle),"unused actual ring capacity was classified as held payload");
        for(int at=head;at!=tail;at=(at+1)%array.length)require(graph.contains(array[at]),"wrapped actual Deque element was lost");
        Object pending=new UnknownCarrierLeaf(),removed=array[head];
        try {
            array[tail]=pending;graph=EquipmentCarrierGraph.candidate(null,deque);require(graph.contains(pending)&&graph.contains(removed)&&!graph.contains(unrelated),"addLast array publication before tail store lost payload or swept unused slots");
            array[tail]=null;array[head]=null;graph=EquipmentCarrierGraph.candidate(null,deque);require(!graph.contains(removed)&&!graph.contains(unrelated),"poll array clear before head store retained removed payload or swept unused slots");
        } finally {array[tail]=null;array[head]=removed;array[idle]=null;}
        ArrayDeque<Object> full=new ArrayDeque<>(3);Object[] fullArray=(Object[])U.getObjectVolatile(full,elements);for(int at=0;at<fullArray.length-1;at++)full.addLast(new UnknownCarrierLeaf());
        int oldTail=U.getIntVolatile(full,tailOffset);fullArray[oldTail]=pending;U.putIntVolatile(full,tailOffset,U.getIntVolatile(full,headOffset));
        try {graph=EquipmentCarrierGraph.candidate(null,full);for(Object held:fullArray)require(graph.contains(held),"full ring immediately before grow was reported empty");}
        finally {U.putIntVolatile(full,tailOffset,oldTail);fullArray[oldTail]=null;}
    }
    private static void unknownCarrierConcurrent(ConcurrentHashMap<String,Object> map,Object value,Object moved)throws Exception {
        Class<?> type=ConcurrentHashMap.class,nodeType=Class.forName("java.util.concurrent.ConcurrentHashMap$Node",false,null);
        long tableOffset=unknownCarrierOffset(type,"table"),nextOffset=unknownCarrierOffset(type,"nextTable"),controlOffset=unknownCarrierOffset(type,"sizeCtl");
        Object[] table=(Object[])U.getObjectVolatile(map,tableOffset);Object oldNext=U.getObjectVolatile(map,nextOffset);int oldControl=U.getIntVolatile(map,controlOffset);
        ConcurrentHashMap<String,Object> destination=new ConcurrentHashMap<>();destination.put("moved",moved);Object next=U.getObjectVolatile(destination,tableOffset);
        Class<?> forwardingType=Class.forName("java.util.concurrent.ConcurrentHashMap$ForwardingNode",false,null),reservationType=Class.forName("java.util.concurrent.ConcurrentHashMap$ReservationNode",false,null);
        Object forwarding=U.allocateInstance(forwardingType),reservation=U.allocateInstance(reservationType);
        U.putObjectVolatile(forwarding,unknownCarrierOffset(forwardingType,"nextTable"),next);U.putIntVolatile(forwarding,unknownCarrierOffset(nodeType,"hash"),-1);U.putIntVolatile(reservation,unknownCarrierOffset(nodeType,"hash"),-3);
        int forward=-1,reserve=-1;for(int at=0;at<table.length;at++)if(table[at]==null){if(forward<0)forward=at;else{reserve=at;break;}}require(reserve>=0,"actual CHM fixture needs two empty buckets");
        try {
            table[forward]=forwarding;table[reserve]=reservation;U.putObjectVolatile(map,nextOffset,next);U.putIntVolatile(map,controlOffset,-2);
            EquipmentCarrierGraph.View graph=EquipmentCarrierGraph.candidate(null,map);
            require(graph.contains(value)&&graph.contains(moved)&&graph.contains(forwarding)&&graph.contains(reservation),"actual CHM resize/forwarding/reservation prefix lost reachable data or structure");
            U.putObjectVolatile(map,nextOffset,null);graph=EquipmentCarrierGraph.candidate(null,map);require(graph.contains(moved),"forwarding target was lost after direct nextTable field changed");
        } finally {table[forward]=null;table[reserve]=null;U.putObjectVolatile(map,nextOffset,oldNext);U.putIntVolatile(map,controlOffset,oldControl);}
        require(!EquipmentCarrierGraph.candidate(null,map).contains(moved),"retired forwarding/nextTable path retained a detached value");
        ConcurrentHashMap<Object,Object> tree=new ConcurrentHashMap<>(64);List<Object> held=new ArrayList<>();
        for(int at=0;at<10;at++){Object payload=new UnknownCarrierLeaf();held.add(payload);tree.put(new UnknownCarrierKey(),payload);}
        Object treeBin=null;for(Object node:(Object[])U.getObjectVolatile(tree,tableOffset))if(node!=null&&node.getClass().getName().endsWith("$TreeBin"))treeBin=node;
        require(treeBin!=null,"actual colliding CHM did not produce a TreeBin");long lock=unknownCarrierOffset(treeBin.getClass(),"lockState");int original=U.getIntVolatile(treeBin,lock);
        try {U.putIntVolatile(treeBin,lock,1);EquipmentCarrierGraph.View graph=EquipmentCarrierGraph.candidate(null,tree);for(Object payload:held)require(graph.contains(payload),"TreeBin writer prefix lost existing root/first payload");}
        finally {U.putIntVolatile(treeBin,lock,original);}
    }

    /** The production composition must retain all four protections on the actual Forge dispatcher image. */
    private static void eventImageComposition()throws Exception {
        String name="net/minecraftforge/eventbus/EventBus";
        Class<?> actual=Class.forName(name.replace('/','.'),false,AuditFixCheck.class.getClassLoader());
        ClassLoader loader=actual.getClassLoader();byte[] original;
        try(var input=actual.getResourceAsStream("EventBus.class")){
            require(input!=null,"actual Forge EventBus class resource unavailable");original=input.readAllBytes();
        }
        require(new org.objectweb.asm.ClassReader(original).getClassName().equals(name),"Forge resource is not the actual EventBus definition");
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),registration=Class.forName("dev.ronova.pro.agent.EventBusBoundary"),dispatch=Class.forName("dev.ronova.pro.agent.DispatchBoundary");
        Method compose=agent.getDeclaredMethod("eventImage",ClassLoader.class,String.class,Class.class,byte[].class);compose.setAccessible(true);
        Field ready=agent.getDeclaredField("eventFaultGuarded"),installed=registration.getDeclaredField("installed"),entries=dispatch.getDeclaredField("entries");
        for(Field field:List.of(ready,installed,entries))field.setAccessible(true);
        Object oldEntries=entries.get(null);boolean oldReady=ready.getBoolean(null),oldInstalled=installed.getBoolean(null);byte[] changed;
        try {
            // Isolate unrelated declarative contracts; these four actual EventBus boundaries remain production code.
            entries.set(null,Array.newInstance(entries.getType().getComponentType(),0));
            changed=(byte[])compose.invoke(null,loader,name,actual,original);
            require(changed!=null&&changed!=original,"shared Forge dispatcher image was not composed");
            require(ready.getBoolean(null)==oldReady,"unaccepted transformed bytes published a live EventBus boundary");
            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(node,0);
            String bridge="dev/ronova/pro/bootstrap/ControlBridge",fault="dev/ronova/pro/bootstrap/EventFaultBridge";
            require(eventHookCount(node,"addToListeners","(Ljava/lang/Object;Ljava/lang/Class;Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/EventPriority;)V",bridge,"modOwnerAllowed","(Ljava/lang/Object;)Z")==1
                    &&eventHookCount(node,"addToListeners","(Ljava/lang/Object;Ljava/lang/Class;Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/EventPriority;)V",bridge,"modListenerRegistered","(Ljava/lang/Object;Ljava/lang/Object;)V")==1,
                    "registration owner gate or actual listener relation was lost during composition");
            String post="(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraftforge/eventbus/api/IEventBusInvokeDispatcher;)Z";
            require(eventHookCount(node,"post",post,bridge,"modListenerAllowed","(Ljava/lang/Object;)Z")==1,
                    "shared dispatcher composition lost or duplicated the stopped-listener gate");
            require(eventHookCount(node,"post",post,fault,"dispatch","(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z")==1
                    &&eventHookCount(node,"post",post,"net/minecraftforge/eventbus/api/IEventBusInvokeDispatcher","invoke","(Lnet/minecraftforge/eventbus/api/IEventListener;Lnet/minecraftforge/eventbus/api/Event;)V")==1,
                    "exception boundary or its original dispatcher fallback was lost during composition");
            require(eventHookCount(node,"unregister","(Ljava/lang/Object;)V",bridge,"listenerRemovalAllowed","(Ljava/lang/Object;)Z")==1,
                    "shared dispatcher composition lost or duplicated the protected listener-removal gate");
            byte[] twice=(byte[])compose.invoke(null,loader,name,actual,changed);
            require(twice==null||Arrays.equals(changed,twice),"second EventBus composition duplicated or changed an installed boundary");
        } finally {entries.set(null,oldEntries);ready.setBoolean(null,oldReady);installed.setBoolean(null,oldInstalled);}
        Class<?> reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),checker=Class.forName("jdk.internal.org.objectweb.asm.util.CheckClassAdapter");
        java.io.StringWriter diagnostics=new java.io.StringWriter();
        checker.getMethod("verify",reader,ClassLoader.class,boolean.class,java.io.PrintWriter.class).invoke(null,
                reader.getConstructor(byte[].class).newInstance((Object)changed),loader,false,new java.io.PrintWriter(diagnostics));
        require(diagnostics.toString().isEmpty(),"composed actual Forge EventBus failed bytecode dataflow verification: "+diagnostics);
        Class<?> checked=new ClassLoader(loader){
            Class<?> defineBody(){Class<?> result=defineClass(name.replace('/','.'),changed,0,changed.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked!=actual&&checked.getClassLoader()!=loader,"composed EventBus was not verified in an isolated definition");
        checked.getDeclaredConstructors();checked.getDeclaredMethods();
        System.out.println("EVENT_IMAGE_COMPOSITION_PASS actual_Forge_EventBus production_eventImage registration_listener_removal_exception_boundaries original_dispatch_fallback second_pass_idempotent ASM_and_JVM_verification no_live_EventBus_install");
    }
    private static int eventHookCount(org.objectweb.asm.tree.ClassNode node,String method,String descriptor,String owner,String called,String calledDescriptor){
        int count=0;
        for(var body:node.methods)if(body.name.equals(method)&&body.desc.equals(descriptor))for(var instruction:body.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(called)&&call.desc.equals(calledDescriptor))count++;
        return count;
    }
    private static long fileCount(String state,String part){
        String token=":"+part+"=";int begin=state.indexOf(token);require(begin>=0,"file state lacks "+part+": "+state);
        begin+=token.length();int end=state.indexOf(':',begin);return Long.parseLong(state.substring(begin,end<0?state.length():end));
    }
    public static final class FileClientEvent extends net.minecraftforge.eventbus.api.Event {public FileClientEvent(){}}
    private static void fileClientEntries()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent");
        Method files=agent.getMethod("fileBoundaryState");String before=(String)files.invoke(null);
        require(before.startsWith("NATIVE_FILE_BOUNDARY_READY")&&fileCount(before,"PENDING")==0,"file declaration coverage unavailable: "+before);
        java.nio.file.Path directory=java.nio.file.Path.of(System.getProperty("ronova.pro.audit.directory"));
        java.nio.file.Files.createDirectories(directory);java.nio.file.Path path=java.nio.file.Files.createTempFile(directory,"native-first-call-",".bin");
        try {
            try(var output=new java.io.FileOutputStream(path.toFile())){output.write(0x42);output.write(new byte[]{0x17,0x6c});}
            try(var input=new java.io.FileInputStream(path.toFile())){
                require(input.read()==0x42&&input.read()==0x17&&input.read()==0x6c&&input.read()==-1,"first native stream read lost its real result");
                require(input.getChannel().position()==3,"first native stream read duplicated its original body");
            }
            try(var file=new java.io.RandomAccessFile(path.toFile(),"rw")){
                file.seek(1);require(file.read()==0x17&&file.getFilePointer()==2,"first RAF native read result/position changed");
                file.write(0x7d);require(file.getFilePointer()==3,"first RAF native write ran more than once");
                file.setLength(2);require(file.length()==2,"native setLength/length did not use the actual descriptor");
                Throwable failure=null;try{file.seek(-1);}catch(java.io.IOException original){failure=original;}
                require(failure!=null,"original invalid native file request did not throw IOException");
            }
            String after=(String)files.invoke(null);
            require(after.startsWith("NATIVE_FILE_BOUNDARY_READY")&&fileCount(after,"PENDING")==0
                    &&fileCount(after,"TOTAL")==fileCount(before,"TOTAL")&&fileCount(after,"BOUND")>fileCount(before,"BOUND")
                    &&fileCount(after,"ARMED_VM_ENTRY")<fileCount(before,"ARMED_VM_ENTRY"),"real first calls did not publish guarded bindings: "+before+" -> "+after);
            System.out.println("FILE_FIRST_BIND_PASS before="+before+" after="+after+" actual_stream_RAF_results_positions_lengths_original_exception_no_IO_probe_at_install");
        } finally {java.nio.file.Files.deleteIfExists(path);}
        clientDispatchEntry();
    }
    private static void clientDispatchEntry()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent");
        Class<?> busClass=Class.forName("net.minecraftforge.eventbus.EventBus",true,AuditFixCheck.class.getClassLoader());
        Method ready=agent.getMethod("eventFaultGuarded",Class.class);
        require(Boolean.TRUE.equals(ready.invoke(null,busClass)),"late actual EventBus ClassPrepare was not accepted");
        var seen=new java.util.concurrent.atomic.AtomicInteger();var dispatched=new java.util.concurrent.atomic.AtomicInteger();
        RuntimeException expected=new RuntimeException("original event listener");
        var bus=net.minecraftforge.eventbus.api.BusBuilder.builder().setExceptionHandler((b,event,listeners,index,error)->{
            if(error!=expected)error.printStackTrace();
            require(error==expected,"original Forge exception handler received a different Throwable");
        }).build();
        java.util.function.Consumer<FileClientEvent> listener=event->{seen.incrementAndGet();if(seen.get()==2)throw expected;};
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,FileClientEvent.class,listener);
        var event=new FileClientEvent();
        bus.post(event,(actual,value)->{if(!(actual instanceof net.minecraftforge.eventbus.api.EventPriority))dispatched.incrementAndGet();actual.invoke(value);});
        Throwable thrown=null;try{bus.post(new FileClientEvent(),(actual,value)->{if(!(actual instanceof net.minecraftforge.eventbus.api.EventPriority))dispatched.incrementAndGet();actual.invoke(value);});}catch(RuntimeException original){thrown=original;}
        require(seen.get()==2&&dispatched.get()==2&&thrown==expected,"unselected actual EventBus dispatch/Throwable was replaced or duplicated");
        bus.unregister(listener);
        System.out.println("CLIENT_DISPATCH_PASS actual_late_Forge_EventBus_ClassPrepare accepted_exact_class original_listener_once original_Throwable handler_absent_fallback no_Core_or_GL_install_claim");
    }
    private static final class ScopedTransformBody {static int value(){return 7;}}
    /** One observed producer failure must not acquire the refusal authority of a selected/control image. */
    @SuppressWarnings("unchecked")
    private static void scopedTransformFailure()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),group=Class.forName("dev.ronova.pro.agent.ModGroupBoundary");
        Field instrumentation=agent.getDeclaredField("instrumentation"),controlFailure=agent.getDeclaredField("controlFailure"),images=agent.getDeclaredField("controlImages"),watching=agent.getDeclaredField("watching"),definitions=agent.getDeclaredField("definitions"),producers=agent.getDeclaredField("producerModules");
        Field metadata=group.getDeclaredField("metadata"),exact=group.getDeclaredField("exact"),unresolved=group.getDeclaredField("unresolved"),gaps=group.getDeclaredField("groupGaps");
        for(Field field:List.of(instrumentation,controlFailure,images,watching,definitions,producers,metadata,exact,unresolved,gaps))field.setAccessible(true);
        require(instrumentation.get(null)==null,"scoped transform failure check requires an isolated uninstalled Agent");
        Object oldFailure=controlFailure.get(null);require(oldFailure==null,"scoped transform failure check requires no prior control failure");
        String name=ScopedTransformBody.class.getName().replace('.','/');byte[] input;
        try(var resource=AuditFixCheck.class.getResourceAsStream("/"+name+".class")){
            require(resource!=null,"actual producer body bytes unavailable");input=resource.readAllBytes();
        }
        Class<?> ordinary=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){return defineClass(name.replace('/','.'),input,0,input.length);}
        }.defineBody();ordinary.getDeclaredMethods();Module module=ordinary.getModule();
        Map<Module,Set<String>> metadataRows=(Map<Module,Set<String>>)metadata.get(null),gapRows=(Map<Module,Set<String>>)gaps.get(null);
        Map<Module,Boolean> producerRows=(Map<Module,Boolean>)producers.get(null);Set<Module> exactRows=(Set<Module>)exact.get(null);
        Set<String> unresolvedRows=(Set<String>)unresolved.get(null),oldUnresolved=new HashSet<>(unresolvedRows);Set<Class<?>> watches=(Set<Class<?>>)watching.get(null);
        Object definitionMonitor=definitions.get(null);boolean oldWatch; synchronized(definitionMonitor){oldWatch=watches.contains(ordinary);}
        Set<String> oldMetadata=metadataRows.get(module);Boolean oldProducer=producerRows.get(module);boolean oldExact=exactRows.contains(module);
        Map<Module,Set<String>> oldGapRows=new IdentityHashMap<>(),oldGapValues=new IdentityHashMap<>();
        for(Module target:List.of(module,Object.class.getModule())) {
            Set<String> prior=gapRows.get(target);oldGapRows.put(target,prior);oldGapValues.put(target,prior==null?Set.of():new HashSet<>(prior));
        }
        Method producer=agent.getDeclaredMethod("producerModule",Module.class),watched=agent.getDeclaredMethod("watchedDefinition",Class.class),stopped=group.getDeclaredMethod("stopped",Module.class);
        Method failed=agent.getDeclaredMethod("failedTransform",Module.class,String.class,byte[].class,Throwable.class,boolean.class,boolean.class);
        Object controlImages=images.get(null);Method critical=controlImages.getClass().getDeclaredMethod("critical",Module.class,ClassLoader.class,String.class,Class.class);
        for(Method method:List.of(producer,watched,stopped,failed,critical))method.setAccessible(true);
        try {
            metadataRows.put(module,Set.of("audit_transform_source"));producerRows.remove(module);
            require(Boolean.TRUE.equals(producer.invoke(null,module))&&Boolean.FALSE.equals(stopped.invoke(null,module))
                    &&Boolean.FALSE.equals(watched.invoke(null,ordinary))&&Boolean.FALSE.equals(critical.invoke(controlImages,module,ordinary.getClassLoader(),name,ordinary)),
                    "ordinary source fixture accidentally acquired selected/control authority");
            byte[] ordinaryResult=(byte[])failed.invoke(null,module,name,input,new IllegalStateException("ordinary source observation"),false,false);
            require(ordinaryResult==input&&controlFailure.get(null)==null,"ordinary producer failure replaced input or poisoned the global control failure");
            require(gapRows.get(module).contains(name+":TRANSFORM_FAILED:IllegalStateException"),"ordinary producer failure lost its actual source-scoped gap");
            exactRows.add(module);boolean stoppedScope=Boolean.TRUE.equals(stopped.invoke(null,module));require(stoppedScope,"actual stopped-source predicate did not select the producer");
            refusedTransformImage(input,(byte[])failed.invoke(null,module,name,input,new UnsupportedOperationException("stopped source observation"),false,stoppedScope),"stopped source");
            require(controlFailure.get(null)==null,"stopped source failure poisoned an unrelated control image");
            exactRows.remove(module);synchronized(definitionMonitor){watches.add(ordinary);}
            boolean selectedScope=Boolean.TRUE.equals(watched.invoke(null,ordinary));
            require(selectedScope&&Boolean.FALSE.equals(stopped.invoke(null,module)),"selected-definition check borrowed stopped-source authority");
            refusedTransformImage(input,(byte[])failed.invoke(null,module,name,input,new LinkageError("selected definition observation"),false,selectedScope),"selected definition");
            require(controlFailure.get(null)==null,"selected source failure poisoned an unrelated control image");
            String controlName="java/lang/reflect/Field";byte[] controlInput;
            try(var resource=Field.class.getResourceAsStream("Field.class")){require(resource!=null,"actual control carrier bytes unavailable");controlInput=resource.readAllBytes();}
            boolean controlScope=Boolean.TRUE.equals(critical.invoke(controlImages,Field.class.getModule(),null,controlName,Field.class));require(controlScope,"actual Field control image was not critical");
            refusedTransformImage(controlInput,(byte[])failed.invoke(null,Field.class.getModule(),controlName,controlInput,new VerifyError("actual control image failure"),controlScope,controlScope),"actual control image");
            Object controlDiagnostic=controlFailure.get(null);require(controlDiagnostic instanceof String message&&message.startsWith(controlName+":VerifyError:"),"actual control failure did not retain its exact global diagnostic");
            synchronized(definitionMonitor){watches.remove(ordinary);}
            require(Boolean.FALSE.equals(watched.invoke(null,ordinary))&&Boolean.FALSE.equals(stopped.invoke(null,module)),"later ordinary observation retained a selected-source predicate");
            require(failed.invoke(null,module,name,input,new IllegalArgumentException("later ordinary observation"),false,false)==input&&controlFailure.get(null)==controlDiagnostic,
                    "ordinary observation replaced input or rewrote an existing control failure");
        } finally {
            controlFailure.set(null,oldFailure);if(oldMetadata==null)metadataRows.remove(module);else metadataRows.put(module,oldMetadata);
            if(oldProducer==null)producerRows.remove(module);else producerRows.put(module,oldProducer);
            if(oldExact)exactRows.add(module);else exactRows.remove(module);
            synchronized(definitionMonitor){if(oldWatch)watches.add(ordinary);else watches.remove(ordinary);}
            unresolvedRows.clear();unresolvedRows.addAll(oldUnresolved);
            for(Module target:oldGapRows.keySet()) {
                Set<String> prior=oldGapRows.get(target);if(prior==null)gapRows.remove(target);else {prior.clear();prior.addAll(oldGapValues.get(target));gapRows.put(target,prior);}
            }
        }
        System.out.println("SCOPED_TRANSFORM_FAILURE_PASS actual_producer_and_control_predicates ordinary_input_identity_preserved source_gap_retained stopped_and_selected_and_control_images_VM_refused only_control_failure_global isolated_metadata_restored no_Agent_install");
    }
    private static void refusedTransformImage(byte[] input,byte[] refused,String scope){
        require(refused!=null&&refused!=input,"selected transform failure preserved an executable image: "+scope);
        try {
            new ClassLoader(AuditFixCheck.class.getClassLoader()){
                Class<?> defineBody(){return defineClass(null,refused,0,refused.length);}
            }.defineBody();
            throw new AssertionError("VM accepted the refused transform image: "+scope);
        } catch(ClassFormatError expected) { }
    }

    /** Current graph reads must invalidate a reused image even when its stack head is unchanged. */
    private static void retirementReadback()throws Exception {
        Class<?> node=Class.forName("java.util.concurrent.CompletableFuture$Completion",false,null);
        Class<?> callback=Class.forName("java.util.concurrent.CompletableFuture$UniRun",false,null);
        Object target=U.allocateInstance(callback),first=U.allocateInstance(callback),last=U.allocateInstance(callback);
        Class<?> taskType=Class.forName("dev.ronova.pro.RecoveryTasks$Task");
        Constructor<?> constructor=taskType.getDeclaredConstructor(Object.class,ClassLoader.class,boolean.class,Class.class);constructor.setAccessible(true);
        Object task=constructor.newInstance(target,null,false,null);
        Field sources=taskType.getDeclaredField("completionSources");sources.setAccessible(true);
        CompletableFuture<Object> source=new CompletableFuture<>();
        @SuppressWarnings("unchecked") Set<Object> retained=(Set<Object>)sources.get(task);retained.add(source);
        Field stack=CompletableFuture.class.getDeclaredField("stack"),next=node.getDeclaredField("next");stack.setAccessible(true);next.setAccessible(true);
        Method absent=RecoveryTasks.class.getDeclaredMethod("completionStacksAbsent",taskType);absent.setAccessible(true);
        Method fields=RecoveryTasks.class.getDeclaredMethod("completionFields",taskType);fields.setAccessible(true);
        next.set(first,last);stack.set(source,first);
        require(Boolean.TRUE.equals(absent.invoke(null,task)),"ordinary callback chain was not absent");
        Object layout=fields.invoke(null,task);require(layout==fields.invoke(null,task),"exact JDK layout was rediscovered");
        next.set(first,target);next.set(target,last);
        require(Boolean.FALSE.equals(absent.invoke(null,task)),"same-head middle insertion reused an absent readback");
        next.set(first,last);next.set(target,null);
        require(Boolean.TRUE.equals(absent.invoke(null,task)),"actual detached chain did not refresh");
        stack.set(source,target);require(Boolean.FALSE.equals(absent.invoke(null,task)),"changed actual head reused readback");
        stack.set(source,first);next.set(last,first);
        require(Boolean.FALSE.equals(absent.invoke(null,task)),"cyclic actual graph claimed absence");
        next.set(last,null);stack.set(source,null);
        require(Boolean.TRUE.equals(absent.invoke(null,task)),"empty actual stack did not retire");

        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Map<Field,Object> saved=new LinkedHashMap<>();
        for(String name:List.of("certified","futureBoundaries","pendingBoundaries","boundaryCapabilities","certificationEpoch","registration","registrationEpoch")) {
            Field field=bridge.getDeclaredField(name);field.setAccessible(true);Object value=field.get(null);
            saved.put(field,value instanceof Set<?> set?new HashSet<>(set):value);
        }
        try {
            for(var entry:saved.entrySet())if(entry.getValue() instanceof Set<?>) {
                @SuppressWarnings("unchecked") Set<String> values=(Set<String>)entry.getKey().get(null);values.clear();
                if(entry.getKey().getName().equals("futureBoundaries"))values.add("future/Boundary");
                if(entry.getKey().getName().equals("certified"))values.add("ready/Boundary");
            }
            Method publish=bridge.getDeclaredMethod("publishBoundaryCapabilities");publish.setAccessible(true);publish.invoke(null);
            Method capabilities=bridge.getMethod("boundaryCapabilities");
            Object image=capabilities.invoke(null);require(image==capabilities.invoke(null),"unchanged installation image was rebuilt");
            @SuppressWarnings("unchecked") Map.Entry<Long,Set<String>> ready=(Map.Entry<Long,Set<String>>)image;
            require(ready.getValue().containsAll(Set.of("future/Boundary","ready/Boundary")),"future coverage was forced into loaded certification");
            try {ready.getValue().add("forged/Boundary");throw new AssertionError("capability image was mutable");}catch(UnsupportedOperationException expected) { }
            for(var entry:saved.entrySet())if(entry.getKey().getName().equals("pendingBoundaries")) {
                @SuppressWarnings("unchecked") Set<String> values=(Set<String>)entry.getKey().get(null);values.add("future/Boundary");
            }
            publish.invoke(null);
            @SuppressWarnings("unchecked") Map.Entry<Long,Set<String>> pending=(Map.Entry<Long,Set<String>>)capabilities.invoke(null);
            require(pending!=ready&&pending.getKey()>ready.getKey()&&!pending.getValue().contains("future/Boundary"),"in-flight/failed image retained readiness");
            Class<?> registration=Class.forName("dev.ronova.pro.bootstrap.TaskBridge$Registration",false,null);
            Constructor<?> registrationConstructor=registration.getDeclaredConstructor(Consumer.class);registrationConstructor.setAccessible(true);
            Object observer=registrationConstructor.newInstance((Consumer<Object[]>)row->{});
            for(Field field:saved.keySet())if(field.getName().equals("registration"))field.set(null,observer);
            Method commit=bridge.getMethod("withBoundaries",String[].class,long.class,Runnable.class);
            boolean[] ran={false};Runnable action=()->ran[0]=true;
            require(Boolean.FALSE.equals(commit.invoke(null,new String[]{"ready/Boundary"},ready.getKey(),action))&&!ran[0],"stale capability epoch executed a mutation");
            require(Boolean.TRUE.equals(commit.invoke(null,new String[]{"ready/Boundary"},pending.getKey(),action))&&ran[0],"current exact boundary could not execute");
        } finally {
            for(var entry:saved.entrySet())if(entry.getValue() instanceof Set<?> prior) {
                @SuppressWarnings("unchecked") Set<Object> values=(Set<Object>)entry.getKey().get(null);values.clear();values.addAll(prior);
            } else entry.getKey().set(null,entry.getValue());
        }
        System.out.println("RETIREMENT_READBACK_PASS actual_CF_head_and_middle_edges cycle_refused empty_refresh immutable_capabilities pending_image_revokes_readiness stale_commit_epoch_refused fixture_only");
    }

    // Actual isolated TxF preparation; no commit permit is constructed or granted.
    private static native void hostProcess0();
    private static native boolean nativeVmResults0();
    private static native void physicalSetup0(Class<?> bridge);
    private static native void physicalBind0(Object token);
    private static native long physicalSite0(Object token);
    private static native long physicalFrameSite0(Object token);
    private static native void physicalInvalidate0(Object token);
    private static native void physicalRelease0(Object token);
    private static native void physicalStop0();
    private static final Map<Object,Boolean> PHYSICAL_POPPED=new IdentityHashMap<>();
    private static boolean physicalPopped(Object token,boolean normal) {PHYSICAL_POPPED.put(token,normal);return true;}
    private static final class PhysicalBridge {
        static void bind(Object token) {physicalBind0(token);}
        static long site(Object token) {return physicalSite0(token);}
        static long frame(Object token) {return physicalFrameSite0(token);}
    }
    private static final class PhysicalBody {
        static Object normalToken,throwingToken;
        static final RuntimeException FAILURE=new RuntimeException("physical original");
        static void recursive(int depth,Object parent)throws Exception {
            Object token=new Object();PhysicalBridge.bind(token);
            require(PhysicalBridge.site(token)!=0,"own physical frame site unavailable");
            if(parent!=null) {
                require(PhysicalBridge.site(parent)==0,"recursive caller borrowed outer token");
                require(PhysicalBridge.frame(parent)!=0,"exact suspended parent frame unavailable");
            }
            require(PhysicalBridge.site(new Object())==0,"unwatched token described actual site");
            if(depth!=0)recursive(depth-1,token);
            long[] other={-1};Thread worker=new Thread(()->other[0]=PhysicalBridge.site(token));worker.start();worker.join();
            require(other[0]==0,"another thread borrowed physical token");
            physicalInvalidate0(token);require(PhysicalBridge.site(token)==0&&PhysicalBridge.frame(token)==0,"obsolete captured method version remained authorized");
            physicalRelease0(token);require(PhysicalBridge.site(token)==0,"released native watch remained authorized");
        }
        static void normal() {normalToken=new Object();PhysicalBridge.bind(normalToken);require(PhysicalBridge.site(normalToken)!=0,"normal watch unavailable");}
        static void throwing() {throwingToken=new Object();PhysicalBridge.bind(throwingToken);throw FAILURE;}
    }
    private static void physicalToken()throws Exception {
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        physicalSetup0(PhysicalBridge.class);
        try {
            PhysicalBody.recursive(2,null);
            PhysicalBody.normal();require(Boolean.TRUE.equals(PHYSICAL_POPPED.get(PhysicalBody.normalToken)),"normal actual FramePop did not retire token");
            try {PhysicalBody.throwing();throw new AssertionError("original throw disappeared");}
            catch(RuntimeException original) {require(original==PhysicalBody.FAILURE,"exception identity changed");}
            require(Boolean.FALSE.equals(PHYSICAL_POPPED.get(PhysicalBody.throwingToken)),"exceptional actual FramePop did not retire token");
            require(PhysicalBridge.site(PhysicalBody.normalToken)==0&&PhysicalBridge.frame(PhysicalBody.throwingToken)==0,"popped physical token remained usable");
        } finally {physicalStop0();PHYSICAL_POPPED.clear();}
        System.out.println("PHYSICAL_TOKEN_PASS actual_JVMTI_sites recursive_outer_and_other_thread_refused captured_version_mismatch_refused actual_normal_exceptional_FramePop fixture_owned_registration");
    }
    private static native void carrierSiteSetup0(Class<?> controls,Class<?> sources);
    private static native void carrierSiteStop0();
    private static native int carrierSiteDepth0(Class<?> declaring,String name,String descriptor);
    private static native long[] carrierSiteFrame0(Class<?> declaring,String name,String descriptor,int bottom);
    private static native long[] carrierSiteImage0(Class<?> declaring,String name,String descriptor);
    private static native Class<?> carrierSiteMethod0(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller);
    private static native long[] carrierSiteCapture0(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller,String callerName,String callerDescriptor,int bottom);
    private static native void carrierSiteMetadata0(boolean available);
    private static native boolean carrierSiteRegister0(Class<?> actual,byte[] bytes,Object plan);
    private static native void carrierSiteRedefine0(Class<?> actual,byte[] bytes);
    private static final Object CARRIER_SITE_ROOT=new Object();
    private static final ThreadLocal<CarrierSiteCapture> CARRIER_SITES=new ThreadLocal<>();
    private static boolean CARRIER_SITE_ACTIVE,CARRIER_SITE_OBSERVED,CARRIER_SITE_NEW_VERSION;
    private static Method CARRIER_SITE_CALL;private static String CARRIER_SITE_CALL_DESCRIPTOR;
    private static MethodHandle CARRIER_SITE_INSTRUCTION;private static Object CARRIER_SITE_WRONG_RECEIVER;
    private static long[] CARRIER_SITE_LAST,CARRIER_SITE_OLD;private static int CARRIER_SITE_BEGINS,CARRIER_SITE_ENDS,CARRIER_SITE_ENTRIES;
    private record CarrierSiteCapture(CarrierSiteCapture previous,Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments,long[] site,long[] image) { }
    private static boolean carrierReader(){return CARRIER_SITE_ACTIVE;}
    private static boolean carrierInstruction(Object plan,String selector,int location,int instruction,int opcode,int local,String owner,String name,String descriptor)throws Throwable {
        return (boolean)CARRIER_SITE_INSTRUCTION.invokeExact(plan,selector,location,instruction,opcode,local,owner,name,descriptor);
    }
    private static boolean carrierSiteVersion(long[] frame,long[] image){
        if(frame==null||frame.length!=6||image==null||image.length!=4)return false;
        for(int at=0;at<4;at++)if(frame[at+2]!=image[at])return false;return true;
    }
    private static boolean carrierSiteTuple(CarrierSiteCapture capture,Object receiver,Object[] arguments){
        if(capture==null||capture.receiver!=receiver||capture.arguments.length!=arguments.length)return false;
        for(int at=0;at<arguments.length;at++){Object left=capture.arguments[at],right=arguments[at];
            if(left instanceof Integer a&&right instanceof Integer b){if(a.intValue()!=b.intValue())return false;}
            else if(left instanceof Long a&&right instanceof Long b){if(a.longValue()!=b.longValue())return false;}
            else if(left instanceof Double a&&right instanceof Double b){if(Double.doubleToRawLongBits(a)!=Double.doubleToRawLongBits(b))return false;}
            else if(left!=right)return false;
        }return true;
    }
    private static Object carrierSiteBegin(Object parent,Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Object[] arguments){
        Class<?> caller=CARRIER_SITE_CALL.getDeclaringClass();String callerName=CARRIER_SITE_CALL.getName(),callerDescriptor=CARRIER_SITE_CALL_DESCRIPTOR;
        int bottom=carrierSiteDepth0(caller,callerName,callerDescriptor);require(bottom>0,"actual carrier caller depth unavailable");
        long[] before=carrierSiteFrame0(caller,callerName,callerDescriptor,bottom);
        if(CARRIER_SITE_OBSERVED){
            carrierSiteMetadata0(false);
            try {require(carrierSiteCapture0(receiver,symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,bottom)==null,"observer tail accepted unavailable actual image metadata");}
            finally {carrierSiteMetadata0(true);}
        }
        long[] site=carrierSiteCapture0(receiver,symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,bottom);
        require(site!=null&&site.length==6&&before!=null&&site[0]==bottom&&site[1]>before[1]&&carrierSiteVersion(site,Arrays.copyOfRange(before,2,6)),"original carrier invoke CP/BCI unavailable");
        long[] again=carrierSiteCapture0(receiver,symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,bottom);
        require(again!=site&&Arrays.equals(again,site),"carrier metadata was cached or changed while caller suspended");
        require(carrierSiteCapture0(new Object(),symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,bottom)==null,"incompatible receiver matched original dispatch");
        require(carrierSiteCapture0(receiver,symbolic,"missing",descriptor,opcode,caller,callerName,callerDescriptor,bottom)==null,"wrong selector matched original CP call");
        require(carrierSiteCapture0(receiver,symbolic,name,descriptor,opcode==185?182:185,caller,callerName,callerDescriptor,bottom)==null,"wrong opcode matched original CP call");
        require(carrierSiteFrame0(caller,"missing",callerDescriptor,bottom)==null&&carrierSiteFrame0(caller,callerName,callerDescriptor,0)==null,"wrong physical selector/depth described a carrier frame");
        CarrierSiteCapture previous=CARRIER_SITES.get();
        if(previous!=null){
            require(previous.site[0]!=site[0],"recursive physical frames shared bottom identity");
            require(carrierSiteCapture0(receiver,symbolic,name,descriptor,opcode,caller,callerName,callerDescriptor,(int)previous.site[0])==null,"recursive inner hook borrowed suspended outer invoke");
        }
        if(CARRIER_SITE_NEW_VERSION)require(!carrierSiteVersion(site,Arrays.copyOfRange(CARRIER_SITE_OLD,2,6)),"redefined caller retained old physical method image");
        Class<?> actual=carrierSiteMethod0(receiver,symbolic,name,descriptor,opcode,caller);
        long[] image=actual==null?null:carrierSiteImage0(actual,name,descriptor);
        require(image!=null&&image.length==4,"current actual Java method image unavailable");
        CarrierSiteCapture capture=new CarrierSiteCapture(previous,receiver,actual,name,descriptor,arguments.clone(),site,image);
        CARRIER_SITES.set(capture);CARRIER_SITE_LAST=site;CARRIER_SITE_BEGINS++;return capture;
    }
    private static void carrierSiteEnd(Object token,Object result){
        CarrierSiteCapture capture=CARRIER_SITES.get();require(token==capture&&result!=null,"carrier original return lost its exact call");
        CARRIER_SITES.set(capture.previous);CARRIER_SITE_ENDS++;
    }
    private static void carrierSiteEntered(Object receiver,Object[] arguments){
        CarrierSiteCapture capture=CARRIER_SITES.get();require(carrierSiteTuple(capture,receiver,arguments),"callee lost original receiver/operand tuple");
        require(receiver.getClass()==CARRIER_SITE_WRONG_RECEIVER.getClass()&&!carrierSiteTuple(capture,CARRIER_SITE_WRONG_RECEIVER,arguments),"different same-Class receiver borrowed original operands");
        Class<?> caller=CARRIER_SITE_CALL.getDeclaringClass();String callerName=CARRIER_SITE_CALL.getName();
        require(Arrays.equals(capture.site,carrierSiteFrame0(caller,callerName,CARRIER_SITE_CALL_DESCRIPTOR,(int)capture.site[0])),"callee caller no longer at exact original invoke");
        int bottom=carrierSiteDepth0(capture.declaring,capture.name,capture.descriptor);
        require(carrierSiteVersion(carrierSiteFrame0(capture.declaring,capture.name,capture.descriptor,bottom),capture.image)
                &&Arrays.equals(capture.image,carrierSiteImage0(capture.declaring,capture.name,capture.descriptor)),"callee physical definition differs from original dispatch");CARRIER_SITE_ENTRIES++;
    }
    public static final class CarrierSiteTarget {
        public Object carrierValue(int depth){
            carrierSiteEntered(this,new Object[]{depth});
            if(depth>0)try {return CARRIER_SITE_CALL.invoke(null,this,depth-1);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            return this;
        }
    }
    private static byte[] carrierSitePlainBody(){
        String body="dev/ronova/pro/CarrierSitePlain",target=CarrierSiteTarget.class.getName().replace('.','/'),bridge="dev/ronova/pro/bootstrap/ControlBridge";
        var writer=new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        writer.visit(61,0x0031,body,null,"java/lang/Object",null);
        var code=writer.visitMethod(0x0009,"call","(L"+target+";I)Ljava/lang/Object;",null,null);code.visitCode();
        code.visitVarInsn(25,0);code.visitVarInsn(25,0);code.visitLdcInsn(jdk.internal.org.objectweb.asm.Type.getType("L"+target+";"));code.visitLdcInsn("carrierValue");code.visitLdcInsn("(I)Ljava/lang/Object;");code.visitIntInsn(17,182);
        code.visitInsn(4);code.visitTypeInsn(189,"java/lang/Object");code.visitInsn(89);code.visitInsn(3);code.visitVarInsn(21,1);code.visitMethodInsn(184,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);code.visitInsn(83);
        code.visitMethodInsn(184,bridge,"beginEquipmentSlotCall","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;I[Ljava/lang/Object;)Ljava/lang/Object;",false);
        code.visitVarInsn(58,2);code.visitVarInsn(25,0);code.visitVarInsn(21,1);code.visitMethodInsn(182,target,"carrierValue","(I)Ljava/lang/Object;",false);
        code.visitVarInsn(58,3);code.visitVarInsn(25,2);code.visitVarInsn(25,3);code.visitMethodInsn(184,bridge,"endEquipmentSlotCall","(Ljava/lang/Object;Ljava/lang/Object;)V",false);
        code.visitVarInsn(25,3);code.visitInsn(176);code.visitMaxs(0,0);code.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
    private static byte[] carrierSiteChangedBody(byte[] original){
        var node=new jdk.internal.org.objectweb.asm.tree.ClassNode();new jdk.internal.org.objectweb.asm.ClassReader(original).accept(node,0);
        for(var method:node.methods)if(method.name.equals("call"))method.instructions.insert(new jdk.internal.org.objectweb.asm.tree.InsnNode(0));
        var writer=new jdk.internal.org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    /** Actual VM readers with fixture-owned admission; no live Core grant is installed. */
    private static void carrierCallSites()throws Exception {
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),sources=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null),nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        Field slots=controls.getDeclaredField("equipmentSlots");slots.setAccessible(true);Object oldSlots=slots.get(null);
        MethodType predicate=MethodType.methodType(boolean.class,Object.class,String.class,int.class,int.class,int.class,int.class,String.class,String.class,String.class);
        CARRIER_SITE_INSTRUCTION=MethodHandles.privateLookupIn(nativeControl,MethodHandles.lookup()).findStatic(nativeControl,"carrierInstruction",predicate);
        MethodType[] types={MethodType.methodType(Object.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Class.class,String.class,String.class,Object.class),
                MethodType.methodType(void.class,Object.class,Object.class,int.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,boolean.class),
                MethodType.methodType(void.class,Object.class,Object.class,String.class,String.class,Object.class),MethodType.methodType(void.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class),
                MethodType.methodType(Object.class,Object.class,Object.class,Class.class,String.class,String.class,int.class,Object[].class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class),
                MethodType.methodType(Object.class,Object.class,Class.class,String.class,String.class,Object[].class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class)};
        MethodHandle[] callbacks=new MethodHandle[types.length];for(int at=0;at<types.length;at++)callbacks[at]=types[at].returnType()==void.class?MethodHandles.empty(types[at]):MethodHandles.dropArguments(MethodHandles.constant(Object.class,CARRIER_SITE_ROOT),0,types[at].parameterArray());
        callbacks[8]=MethodHandles.lookup().findStatic(AuditFixCheck.class,"carrierSiteBegin",types[8]);callbacks[9]=MethodHandles.lookup().findStatic(AuditFixCheck.class,"carrierSiteEnd",types[9]);
        CARRIER_SITE_ACTIVE=true;carrierSiteSetup0(controls,sources);
        try {
            slots.set(null,callbacks);byte[] plain=carrierSitePlainBody();
            Class<?> caller=new ClassLoader(AuditFixCheck.class.getClassLoader()){Class<?> body(){Class<?> actual=defineClass(null,plain,0,plain.length);resolveClass(actual);return actual;}}.body();
            CARRIER_SITE_CALL=caller.getMethod("call",CarrierSiteTarget.class,int.class);CARRIER_SITE_CALL_DESCRIPTOR=MethodType.methodType(Object.class,CarrierSiteTarget.class,int.class).toMethodDescriptorString();
            require(carrierSiteImage0(caller,"missing",CARRIER_SITE_CALL_DESCRIPTOR)==null&&carrierSiteFrame0(caller,"call",CARRIER_SITE_CALL_DESCRIPTOR,0)==null,"unavailable ordinary metadata became a carrier definition");
            CarrierSiteTarget target=new CarrierSiteTarget();CARRIER_SITE_WRONG_RECEIVER=new CarrierSiteTarget();
            require(CARRIER_SITE_CALL.invoke(null,target,2)==target&&CARRIER_SITE_BEGINS==3&&CARRIER_SITE_ENDS==3&&CARRIER_SITE_ENTRIES==3&&CARRIER_SITES.get()==null,"recursive original carrier path was replayed or not drained");
            CARRIER_SITE_OLD=CARRIER_SITE_LAST;long[] image=carrierSiteImage0(caller,"call",CARRIER_SITE_CALL_DESCRIPTOR);
            carrierSiteRedefine0(caller,carrierSiteChangedBody(plain));
            require(!Arrays.equals(image,carrierSiteImage0(caller,"call",CARRIER_SITE_CALL_DESCRIPTOR)),"actual redefinition retained old current method image");
            CARRIER_SITE_NEW_VERSION=true;require(CARRIER_SITE_CALL.invoke(null,target,0)==target,"redefined original call changed return");CARRIER_SITE_NEW_VERSION=false;
            Object[] combined=carrierDelegateCombinedBody();Class<?> combinedType=(Class<?>)combined[0];
            var constructor=Class.forName("dev.ronova.pro.bootstrap.ExecutionFlow$Plan",true,null).getDeclaredConstructor(Object[][].class);constructor.setAccessible(true);Object plan=constructor.newInstance((Object)combined[2]);
            require(carrierSiteRegister0(combinedType,(byte[])combined[1],plan),"fixture actual final execution image registration failed");
            Object marker=new Object();long count=0x100000003L;double fraction=-2.5;
            EquipmentDelegateBusiness business=new EquipmentDelegateBusiness(){
                public Object route(Object value,long wide,double number){carrierSiteEntered(this,new Object[]{value,wide,number});return value;}
                public long measure(long wide,double number){throw new AssertionError("unused measure replayed");}
                public void emit(Object value){throw new AssertionError("unused emit replayed");}
            };
            CARRIER_SITE_WRONG_RECEIVER=U.allocateInstance(business.getClass());CARRIER_SITE_CALL=combinedType.getMethod("forward",EquipmentDelegateBusiness.class,Object.class,long.class,double.class);
            CARRIER_SITE_CALL_DESCRIPTOR=MethodType.methodType(Object.class,EquipmentDelegateBusiness.class,Object.class,long.class,double.class).toMethodDescriptorString();CARRIER_SITE_OBSERVED=true;
            int before=CARRIER_SITE_ENTRIES;require(CARRIER_SITE_CALL.invoke(combinedType.getConstructor().newInstance(),business,marker,count,fraction)==marker&&CARRIER_SITE_ENTRIES==before+1&&CARRIER_SITES.get()==null,"final slot/action/execution composition lost or replayed original business call");
            require(CARRIER_SITE_BEGINS==CARRIER_SITE_ENDS&&CARRIER_SITE_BEGINS==CARRIER_SITE_ENTRIES,"carrier call lifecycle did not close exactly once");
        } finally {
            slots.set(null,oldSlots);carrierSiteStop0();CARRIER_SITE_ACTIVE=false;CARRIER_SITE_OBSERVED=false;CARRIER_SITE_NEW_VERSION=false;CARRIER_SITES.remove();CARRIER_SITE_CALL=null;CARRIER_SITE_INSTRUCTION=null;
        }
        System.out.println("CARRIER_CALL_SITES_PASS actual_VM_CP_BCI recursive_outer_site_wrong_selector_opcode_incompatible_receiver_refused exact_callee_operands actual_redefine_changes_image final_slot_action_execution_observers missing_metadata_unresolved fixture_owned_registration_no_live_Core_authorization");
    }
    private static void queueLocations()throws Exception {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null);
        Class<?> queueType=Class.forName("java.util.concurrent.ForkJoinPool$WorkQueue",false,null);
        Map<Field,Object> saved=new LinkedHashMap<>();
        for(String name:List.of("certified","futureBoundaries","pendingBoundaries","boundaryCapabilities","certificationEpoch","forkQueueQuiescentEpoch")) {
            Field field=bridge.getDeclaredField(name);field.setAccessible(true);Object value=field.get(null);
            saved.put(field,value instanceof Set<?> set?new HashSet<>(set):value);
        }
        ForkJoinPool pool=new ForkJoinPool(1);
        try {
            for(var entry:saved.entrySet())if(entry.getValue() instanceof Set<?>) {
                @SuppressWarnings("unchecked") Set<String> values=(Set<String>)entry.getKey().get(null);values.clear();
                if(entry.getKey().getName().equals("certified"))values.addAll(Set.of("java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue"));
            }
            Method publish=bridge.getDeclaredMethod("publishBoundaryCapabilities");publish.setAccessible(true);publish.invoke(null);
            @SuppressWarnings("unchecked") Map.Entry<Long,Set<String>> capabilities=(Map.Entry<Long,Set<String>>)bridge.getMethod("boundaryCapabilities").invoke(null);
            for(Field field:saved.keySet())if(field.getName().equals("forkQueueQuiescentEpoch"))field.setLong(null,capabilities.getKey()); // Fixture-owned installation metadata; no Agent is installed.
            Object first=U.allocateInstance(queueType),second=U.allocateInstance(queueType);
            ForkJoinTask<?> target=ForkJoinTask.adapt(()->{}),other=ForkJoinTask.adapt(()->{});
            ForkJoinTask<?>[] firstArray=new ForkJoinTask<?>[8],secondArray=new ForkJoinTask<?>[8];firstArray[0]=target;
            Field array=queueType.getDeclaredField("array"),top=queueType.getDeclaredField("top"),queues=ForkJoinPool.class.getDeclaredField("queues");
            array.setAccessible(true);top.setAccessible(true);queues.setAccessible(true);
            array.set(first,firstArray);array.set(second,secondArray);top.setInt(first,1);
            Object image=Array.newInstance(queueType,2);Array.set(image,0,first);Array.set(image,1,second);queues.set(pool,image);
            Method snapshot=bridge.getMethod("forkQueueSnapshot",Object.class,Object.class),epoch=bridge.getMethod("forkQueueEpoch",Object.class);
            Object[] present=(Object[])snapshot.invoke(null,pool,target);
            require(Integer.valueOf(0).equals(present[1])&&((Object[])present[2]).length==1&&((Object[])present[2])[0]==first,"preobserved exact queue task was lost");
            Object[] absent=(Object[])snapshot.invoke(null,pool,other);require(Integer.valueOf(1).equals(absent[1])&&absent[0].equals(present[0]),"one seed did not cover independent task");
            Field lockField=bridge.getDeclaredField("FORK_QUEUE_LOCK");lockField.setAccessible(true);Object lock=lockField.get(null);
            Method arrayImage=bridge.getDeclaredMethod("forkArray",Object[].class);arrayImage.setAccessible(true);
            Object firstState=arrayImage.invoke(null,(Object)firstArray),secondState=arrayImage.invoke(null,(Object)secondArray);
            Method cell=bridge.getDeclaredMethod("recordForkCell",firstState.getClass(),int.class,Object.class,Object.class);cell.setAccessible(true);
            synchronized(lock) {firstArray[0]=null;top.setInt(first,0);cell.invoke(null,firstState,0,target,null);secondArray[0]=target;top.setInt(second,1);cell.invoke(null,secondState,0,null,target);}
            Object[] moved=(Object[])snapshot.invoke(null,pool,target);
            require(Integer.valueOf(0).equals(moved[1])&&((Object[])moved[2]).length==1&&((Object[])moved[2])[0]==second&&!present[0].equals(epoch.invoke(null,pool)),"moved task kept an obsolete queue readback");
            Method begin=bridge.getDeclaredMethod("beginForkQueueTransition",Object.class,boolean.class);begin.setAccessible(true);
            Object transition=begin.invoke(null,second,false);
            require(Integer.valueOf(-1).equals(((Object[])snapshot.invoke(null,pool,target))[1])&&Long.valueOf(-1).equals(epoch.invoke(null,pool)),"incomplete queue transition fabricated absence");
            Method bind=bridge.getDeclaredMethod("bindForkArray",Class.forName(bridge.getName()+"$ForkQueueImage",false,null),Object[].class);bind.setAccessible(true);
            Method findQueue=bridge.getDeclaredMethod("forkQueue",Object.class);findQueue.setAccessible(true);
            ForkJoinTask<?>[] grown=new ForkJoinTask<?>[16];grown[0]=target;
            Method finish=bridge.getDeclaredMethod("finishForkTransition",transition.getClass(),boolean.class);finish.setAccessible(true);
            synchronized(lock) {array.set(second,grown);bind.invoke(null,findQueue.invoke(null,second),grown);finish.invoke(null,transition,true);}
            require(Integer.valueOf(0).equals(((Object[])snapshot.invoke(null,pool,target))[1]),"resized actual array lost task identity");
            Object grownState=arrayImage.invoke(null,(Object)grown);
            synchronized(lock) {grown[0]=null;top.setInt(second,0);cell.invoke(null,grownState,0,target,null);}
            require(Integer.valueOf(1).equals(((Object[])snapshot.invoke(null,pool,target))[1]),"actual removal remained present");
            try {bridge.getMethod("forkQueueArrayStore",Object[].class,int.class,Object.class).invoke(null,grown,0,target);throw new AssertionError("non-JDK caller wrote through queue receipt");}
            catch(InvocationTargetException refused) {require(refused.getCause() instanceof SecurityException&&grown[0]==null,"queue caller refusal changed task cell");}
            Class<?> mutationType=Class.forName("dev.ronova.pro.bootstrap.ControlBridge$FieldMutation",false,null);
            Constructor<?> mutation=mutationType.getDeclaredConstructor(java.util.concurrent.locks.ReentrantLock.class,Object.class,Object.class);mutation.setAccessible(true);
            Method closed=mutationType.getDeclaredMethod("close");closed.setAccessible(true);
            var gate=new java.util.concurrent.locks.ReentrantLock();gate.lock();Object receipt=mutation.newInstance(gate,null,grown);
            try {
                require(Integer.valueOf(-1).equals(((Object[])snapshot.invoke(null,pool,target))[1]),"out-of-band write in flight retained absence");
                grown[0]=target;top.setInt(second,1);
            } finally {closed.invoke(receipt);}
            Object[] republished=(Object[])snapshot.invoke(null,pool,target);
            require(Integer.valueOf(0).equals(republished[1])&&((Object[])republished[2])[0]==second&&!gate.isLocked(),"existing field/array receipt did not invalidate and reseed reinsertion");
        } finally {
            pool.shutdownNow();
            for(var entry:saved.entrySet())if(entry.getValue() instanceof Set<?> prior) {
                @SuppressWarnings("unchecked") Set<Object> values=(Set<Object>)entry.getKey().get(null);values.clear();values.addAll(prior);
            } else entry.getKey().set(null,entry.getValue());
        }
        Class<?> contract=Class.forName("dev.ronova.pro.agent.JdkTaskBoundaryContract");
        Class<?> reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),checker=Class.forName("jdk.internal.org.objectweb.asm.util.CheckClassAdapter");
        for(String name:List.of("java/util/concurrent/ForkJoinPool","java/util/concurrent/ForkJoinPool$WorkQueue")) {
            byte[] original;try(var input=ClassLoader.getPlatformClassLoader().getResourceAsStream(name+".class")){original=input.readAllBytes();}
            byte[] transformed=(byte[])contract.getMethod("transform",String.class,byte[].class).invoke(null,name,original);
            require(transformed!=null,"current JDK queue image did not transform: "+name);
            java.io.StringWriter diagnostics=new java.io.StringWriter();
            checker.getMethod("verify",reader,boolean.class,java.io.PrintWriter.class).invoke(null,reader.getConstructor(byte[].class).newInstance((Object)transformed),false,new java.io.PrintWriter(diagnostics));
            require(diagnostics.toString().isEmpty(),"queue bytecode verification failed: "+name+":"+diagnostics);
        }
        System.out.println("QUEUE_LOCATIONS_PASS actual_JDK_layout seed_move_resize_remove pending_refusal stale_epoch_refused exact_caller_refused field_receipt_reinsertion transformed_queue_dataflow fixture_owned_certification_no_live_hooks");
    }

    // One AUDIT_HOST_THREAD_CREATE_CHECK DLL. thread-create uses unattached
    // starts; thread-end checks actual attachment/detachment without rerunning it.
    private static native void hostThreadCreate0(Class<?> type,Object payload);
    private static native void hostThreadEnd0();
    private static native void hostModuleLoad0();
    private static native void hostJniTable0();
    private static native void hostJvmtiOutput0();
    private static native boolean hostJniOutput0();
    private static native boolean hostMemoryRead0();
    private static native void hostWin32Allocations0();
    private static native boolean hostCrtAllocations0();
    private static native boolean hostCppPrivate0();
    private static native boolean hostTlsPrivate0();
    private static native boolean hostArenaAllocations0();
    private static native boolean hostMimalloc0(String providerPath);
    private static native boolean hostGnuCpp0(String providerPath,String classDllPath);
    private static native boolean hostCrtAligned0();
    private static native boolean privateOtherState0();
    private static native boolean privateCustomAllocatorState0(String fixtureDllPath);
    private static native boolean hostCustomAllocatorFields0(String fixtureDllPath);
    private static native boolean hostCrtBytes0(String fixtureDllPath);
    private static native boolean hostNtMemory0(String fixtureDllPath);
    private static native boolean hostNtMemoryForeign0(long address,long bytes,int mode);
    private static boolean hostNtMemoryForeign(long address,long bytes,int mode)throws Exception {
        Class<?> foreign=Class.forName(AuditFixCheck.class.getName(),true,ClassLoader.getSystemClassLoader());
        require(foreign!=AuditFixCheck.class,"NT memory foreign caller must use the actual system-loader image");
        var call=foreign.getDeclaredMethod("hostNtMemoryForeign0",long.class,long.class,int.class);call.setAccessible(true);
        return Boolean.TRUE.equals(call.invoke(null,address,bytes,mode));
    }
    private static native boolean hostCrtBytesForeign0(long address,long bytes,int mode);
    private static boolean hostCrtBytesForeign(long address,long bytes,int mode)throws Exception {
        Class<?> foreign=Class.forName(AuditFixCheck.class.getName(),true,ClassLoader.getSystemClassLoader());
        require(foreign!=AuditFixCheck.class,"CRT byte foreign caller must use the actual system-loader image");
        var call=foreign.getDeclaredMethod("hostCrtBytesForeign0",long.class,long.class,int.class);call.setAccessible(true);
        return Boolean.TRUE.equals(call.invoke(null,address,bytes,mode));
    }
    private static native void hostRawUnsafe0();
    private static native void hostModuleRelease0(String controlDll);
    private static native void hostModuleReleaseThread0(String controlDll);
    private static native void hostMemory0(String controlDll);
    private static native void hostMemoryRelease0(String controlDll);
    private static native void hostDefinitionsChanged0(ClassLoader loader);
    private static native boolean hostCngHash0();
    private static native boolean hostTransactionState0(String tempFile);
    private static native boolean hostTransactionForeign0(long actual);
    private static native boolean hostTransactionAsync0(String tempFile);
    private static native boolean hostTransactionAsyncForeign0(long file,long overlapped);
    private static boolean hostTransactionForeign(long actual)throws Exception {
        Class<?> foreign=Class.forName(AuditFixCheck.class.getName(),true,ClassLoader.getSystemClassLoader());
        require(foreign!=AuditFixCheck.class,"transaction foreign caller must use the actual system-loader image");
        var call=foreign.getDeclaredMethod("hostTransactionForeign0",long.class);call.setAccessible(true);
        return Boolean.TRUE.equals(call.invoke(null,actual));
    }
    private static boolean hostTransactionAsyncForeign(long file,long overlapped)throws Exception {
        Class<?> foreign=Class.forName(AuditFixCheck.class.getName(),true,ClassLoader.getSystemClassLoader());
        require(foreign!=AuditFixCheck.class,"asynchronous foreign caller must use the actual system-loader image");
        var call=foreign.getDeclaredMethod("hostTransactionAsyncForeign0",long.class,long.class);call.setAccessible(true);
        return Boolean.TRUE.equals(call.invoke(null,file,overlapped));
    }
    private static void hostTransactionState()throws Exception {
        var file=java.nio.file.Files.createTempFile("ronova-core-transaction-",".dat").toAbsolutePath();
        byte[] original="Ronova actual Core transaction fixture: the original file survives native synchronous rollback.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            java.nio.file.Files.write(file,original);
            require(hostTransactionState0(file.toString()),"actual KTM/TxF native lifecycle failed");
            require(Arrays.equals(original,java.nio.file.Files.readAllBytes(file)),"native transaction rollback changed the original file");
            System.out.println("HOST_TRANSACTION_FILE_PASS actual_Java_owned_temp_path original_bytes_preserved_after_native_rollback");
        } finally {java.nio.file.Files.deleteIfExists(file);}
    }
    private static void hostTransactionAsync()throws Exception {
        var file=java.nio.file.Files.createTempFile("ronova-core-async-",".dat").toAbsolutePath();
        byte[] original="Ronova actual Core asynchronous transaction: rollback preserves these original bytes.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Throwable failed=null;
        try {
            java.nio.file.Files.write(file,original);
            require(hostTransactionAsync0(file.toString()),"actual asynchronous KTM/TxF completion lifecycle failed");
            require(Arrays.equals(original,java.nio.file.Files.readAllBytes(file)),"asynchronous native rollback changed the original file");
            System.out.println("HOST_TRANSACTION_ASYNC_FILE_PASS actual_Java_owned_path original_bytes_preserved_after_native_rollback");
        } catch(Exception|Error error){failed=error;throw error;}
        finally {
            try {java.nio.file.Files.deleteIfExists(file);}
            catch(java.io.IOException cleanup){if(failed!=null)failed.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    private static void coreNativeState(String jar)throws Exception {
        var audit=java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath();
        var foreign=audit.getParent().resolve("foreign").resolve("audit-host-foreign.dll");
        java.nio.file.Files.createDirectories(foreign.getParent());
        java.nio.file.Files.copy(audit,foreign,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.load(foreign.toString());
        var finder=java.lang.module.ModuleFinder.of(java.nio.file.Path.of(jar));
        require(finder.findAll().size()==1&&finder.find("ronova_pro").isPresent(),"one actual named Core module required");
        var configuration=ModuleLayer.boot().configuration().resolve(finder,java.lang.module.ModuleFinder.of(),Set.of("ronova_pro"));
        var controller=ModuleLayer.defineModulesWithOneLoader(configuration,List.of(ModuleLayer.boot()),ClassLoader.getSystemClassLoader());
        var layer=controller.layer();var module=layer.findModule("ronova_pro").orElseThrow();
        controller.addReads(module,AuditFixCheck.class.getModule());
        controller.addOpens(module,"dev.ronova.pro",AuditFixCheck.class.getModule());
        var loader=layer.findLoader("ronova_pro");
        Class<?> mod=Class.forName("dev.ronova.pro.ProMod",false,loader),runtime=Class.forName("dev.ronova.pro.ProRuntime",false,loader);
        require(mod.getModule()==module&&runtime.getModule()==module&&mod.getClassLoader()==loader,"Core classes escaped actual module");
        var agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        try {agent.getMethod("bindModGroupCallers",Class.class,Class.class).invoke(null,mod,runtime);throw new AssertionError("foreign Core binding caller admitted");}
        catch(InvocationTargetException denied){require(denied.getCause() instanceof SecurityException,"foreign Core binding failed for another reason");}
        // Run the same real ProMod startup body used by its constructor. No
        // producer rows, NativeLibrary objects, image owners or scopes are supplied.
        var bind=mod.getDeclaredMethod("bindControlOwner");bind.setAccessible(true);bind.invoke(null);
        var bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        var producer=bridge.getDeclaredMethod("producer",Module.class);producer.setAccessible(true);
        require(Boolean.FALSE.equals(producer.invoke(null,module)),"Core was relabelled as an external producer");
        Class<?> actual=Class.forName(AuditFixCheck.class.getName(),true,loader);
        String phase=System.getProperty("ronova.pro.audit.thread.phase","");
        boolean transactions=phase.equals("transactions"),async=phase.equals("async"),bytes=phase.equals("bytes"),ntMemory=phase.equals("nt-memory");
        actual.getMethod("main",String[].class).invoke(null,(Object)new String[]{ntMemory?"core-native-nt-memory":bytes?"core-native-bytes":async?"core-native-async":transactions?"core-native-transactions":"core-native-consumers"});
        require(Boolean.FALSE.equals(producer.invoke(null,module)),"Native consumers changed Core producer identity");
        System.out.println(ntMemory?"CORE_NATIVE_NT_MEMORY_PASS actual_named_ProMod_ProRuntime startup_binding distinct_system_loader_foreign_image actual_Nt_Zw_Win32_memory_endpoints no_supplied_owner_or_producer_metadata"
                :bytes?"CORE_NATIVE_BYTES_PASS actual_named_ProMod_ProRuntime startup_binding distinct_system_loader_foreign_image actual_CRT_byte_entries no_supplied_owner_or_producer_metadata"
                :async?"CORE_NATIVE_ASYNC_PASS actual_named_ProMod_ProRuntime startup_binding actual_file_heap_overlapped_IOCP original_completion_and_cancellation foreign_image_completion_cancel_refused no_supplied_metadata"
                :transactions?"CORE_NATIVE_TRANSACTIONS_PASS actual_named_ProMod_ProRuntime startup_binding foreign_caller_refused original_transaction_lifetimes foreign_image_transaction_mutation_refused no_supplied_metadata"
                :"CORE_NATIVE_STATE_PASS actual_named_ProMod_ProRuntime startup_binding foreign_caller_refused full_native_loader_binding SDK_receipts_nonzero no_supplied_owner_or_producer_rows original_crypto_transaction_and_storage_lifetimes foreign_image_transaction_mutation_refused");
    }
    private static final class HostThreadCreatePayload { }
    private static void hostThreadCreate(){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
        String phase=System.getProperty("ronova.pro.audit.thread.phase","thread-create");
        if(phase.equals("allocator-fields")){
            require(hostCustomAllocatorFields0(System.getProperty("ronova.pro.audit.allocatorFields")),"actual custom allocator and typed field consumer failed");
            require(StorageNative.available()&&StorageNative.abi0()==3,"actual C++ storage consumer DLL unavailable");
            var file=java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"ronova-typed-identity-"+UUID.randomUUID()+".dat");
            try{
                java.nio.file.Files.createFile(file);
                try(var stream=new java.io.FileInputStream(file.toFile())){
                    require(StorageNative.identity(stream.getFD(),file.toString())!=null,"typed C++ identity changed original result");
                }
                System.out.println("HOST_TYPED_STORAGE_IDENTITY_PASS actual_private_bool_fields public_string_buffers original_result_destructor no_producer_coverage_claim");
            }catch(java.io.IOException failure){throw new AssertionError("actual typed C++ storage consumer failed",failure);}
            finally{try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException failure){throw new AssertionError("actual identity fixture close failed",failure);}}
            return;
        }
        if(phase.equals("gnu-cpp")){
            require(hostGnuCpp0(System.getProperty("ronova.pro.audit.gnuCpp"),System.getProperty("ronova.pro.audit.cppClass")),"actual GNU C++ boundary failed");
            return;
        }
        if(phase.equals("other-allocators-cpp")){
            require(hostCrtAligned0(),"actual CRT aligned-allocation boundary failed");
            require(hostMimalloc0(System.getProperty("ronova.pro.audit.mimalloc")),"actual mimalloc boundary failed");
            require(hostGnuCpp0(System.getProperty("ronova.pro.audit.gnuCpp"),System.getProperty("ronova.pro.audit.cppClass")),"actual GNU C++ boundary failed");
            return;
        }
        if(phase.equals("arena-static-object")){
            System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.jemalloc","jemalloc.dll")).toAbsolutePath().toString());
            require(hostArenaAllocations0(),"actual jemalloc arena boundary failed");
            require(StorageNative.available()&&StorageNative.abi0()==3,"actual C++ storage consumer DLL unavailable");
            try{
                var file=java.nio.file.Files.createTempFile("ronova-object-state-",".dat");
                try(var stream=new java.io.FileInputStream(file.toFile())){
                    require(StorageNative.identity(stream.getFD(),file.toString())!=null,"C++ identity object changed original result");
                }finally{java.nio.file.Files.deleteIfExists(file);}
                System.out.println("HOST_CPP_STORAGE_IDENTITY_PASS actual_Cplusplus_constructor_original_identity_destructor no_producer_SDK_coverage_claim");
            }catch(java.io.IOException failure){throw new AssertionError("actual C++ storage consumer failed",failure);}
            return;
        }
        if(phase.equals("pool-tls-cpp")){
            hostWin32Allocations0();
            require(hostCrtAllocations0(),"CRT release source admission failed");
            require(hostTlsPrivate0(),"actual dynamic TLS/FLS boundary failed");
            require(hostCppPrivate0(),"actual C++ allocation-function boundary failed");
            return;
        }
        if(phase.equals("private-allocations")){
            hostWin32Allocations0();
            require(hostCrtAllocations0(),"actual CRT allocation regression failed");
            return;
        }
        if(phase.equals("raw-unsafe")){
            hostRawUnsafe0();
            System.out.println("RAW_UNSAFE_PASS actual_sun_misc_putLong_free_reallocate actual_internal_native_putLong_setMemory0_copyMemory0_freeMemory0_reallocateMemory0 VM_JVMTI_JNI_first_slots_same_value_and_lifetime_refused_bytes_preserved ordinary_allocate_put_set_copy_grow_shrink_zero_length_NULL_conventions_paired_free actual_Dispose no_fabricated_authority");
            return;
        }
        if(phase.equals("memory-effects")){
            hostMemory0(System.getProperty("ronova.pro.control.native"));
            hostMemoryRelease0(System.getProperty("ronova.pro.control.native"));
            System.out.println("HOST_MEMORY_EFFECTS_PASS actual_IAT_GPA_write_protect_free_unmap ordinary_effects_preserved exact_control_targets_and_local_outputs_refused no_fabricated_authority");
            return;
        }
        if(phase.equals("read-memory")){
            require(hostMemoryRead0(),"Host memory read output control failed");
            return;
        }
        if(phase.equals("free-exit")){
            hostModuleReleaseThread0(System.getProperty("ronova.pro.control.native"));
            System.out.println("HOST_FREE_EXIT_PASS actual_FreeLibraryAndExitThread_IAT_GPA unattached_native_workers original_exit_codes signaled_nonreturn actual_resource_view_unmapped exact_control_borrowed_attack_mapping_preserved handles_closed temporary_files_removed no_fabricated_authority");
            return;
        }
        if(phase.equals("release-memory")){
            hostMemoryRelease0(System.getProperty("ronova.pro.control.native"));
            System.out.println("HOST_MEMORY_RELEASE_PASS actual_VirtualFree_Ex_UnmapViewOfFile_Ex_IAT_GPA current_process_pseudo_and_VM_OPERATION_only_real_alias private_decommit_reserve_recommit_zero_read_write_release_free actual_temporary_file_views_unmapped_closed_removed original_unmap_zero_transient_boost_flags VM_shadow_zero_decommit_whole_release_refused hook_thunk_whole_release_page_decommit_refused exact_control_DLL_unmap_refused mappings_bytes_protection_preserved no_control_instruction_change no_fabricated_authority");
            return;
        }
        if(phase.equals("memory")){
            hostMemory0(System.getProperty("ronova.pro.control.native"));
            System.out.println("HOST_MEMORY_PASS actual_IAT_GPA current_process_pseudo_and_VM_rights_only_real_alias private_VirtualAlloc_write_read_protection_restored exact_control_DLL_VM_shadow_hook_thunk_same_value_write_refused page_rounded_shadow_tail_protect_refused protected_local_output_aliases_refused no_control_instruction_change no_fabricated_authority");
            return;
        }
        if(phase.equals("jni-output")){
            require(hostJniOutput0(),"JNI caller output control failed");
            return;
        }
        if(phase.equals("jvmti-output")){
            hostJvmtiOutput0();
            System.out.println("JVMTI_OUTPUT_PASS actual_GetEnv live_VM_JVMTI_slots_refused_bytes_and_protection_preserved ordinary_Allocate_Deallocate optional_NULL original_INVALID_METHODID grouped_GetMethodName_no_partial_output GetStackTrace_frames_count_no_partial_output control_Deallocate_refused actual_Dispose no_fabricated_authority");
            return;
        }
        if(phase.equals("jni-table")||phase.equals("strength")){
            hostJniTable0();
            System.out.println("JNI_TABLE_PUBLICATION_PASS actual_GetEnv original_current_table_accepted private_snapshot_copied caller_table_change_not_published guarded_DefineClass_replacement_refused actual_Dispose no_fabricated_authority");
            if(phase.equals("jni-table"))return;
        }
        if(phase.equals("module-load")||phase.equals("admission")||phase.equals("strength")){
            hostModuleLoad0();
            System.out.println("HOST_MODULE_LOAD_PASS actual_A_W_ExA_ExW_imports_and_dynamic_results original_parameters_flags_actual_HMODULE actual_invalid_image_error actual_DATAFILE_IMAGE_RESOURCE_tagged_HMODULE original_resource_flags actual_loader_LastError balanced_FreeLibrary temporary_input_removed no_manufactured_producer_authority");
            if(phase.equals("strength")){
                hostModuleRelease0(System.getProperty("ronova.pro.control.native"));
                System.out.println("HOST_FREE_LIBRARY_PASS actual_import_and_dynamic_entries original_success_and_failure actual_LastError balanced_application_references exact_control_DLL_release_refused no_fabricated_producer");
            }
            if(phase.equals("admission")){
                ClassLoader loader=new ClassLoader(null) { };
                try {hostDefinitionsChanged0(loader);}finally {java.lang.ref.Reference.reachabilityFence(loader);}
                System.out.println("DEFINITIONS_REAL_APIS_PASS actual_original_RetransformClasses_RedefineClasses changed_method_execution invalid_count_preserved actual_Dispose");
            }
            return;
        }
        if(phase.equals("thread-end")){
            hostThreadEnd0();
            System.out.println("HOST_THREAD_END_PASS actual_CRT_start_userdata actual_AttachCurrentThread actual_ThreadEnd_callback actual_DetachCurrentThread GetEnv_JNI_EDETACHED native_continuation callback_child_and_detached_successor suspended_until_outer_resume actual_OS_exit handles_closed");
            return;
        }
        require(phase.equals("thread-create"),"unknown actual thread check phase: "+phase);
        var payload=new HostThreadCreatePayload();
        try {hostThreadCreate0(HostThreadCreatePayload.class,payload);}
        finally {java.lang.ref.Reference.reachabilityFence(payload);}
        System.out.println("HOST_THREAD_CREATE_PASS actual_CreateThread current_process_CreateRemoteThread current_process_CreateRemoteThreadEx actual_loaded_CRT_beginthread_beginthreadex private_dynamic_hooks native_userdata_cookie thread_id suspended_until_resume actual_thread_exit CRT_auto_close_beginthread owned_observation_handle_closed beginthreadex_handle_closed actual_CRT_errno_doserrno native_userdata_retired tagged_heap_callback_create resume_and_wait_after_walk");
    }

    @FunctionalInterface private interface RawUnsafeAction {void run()throws Exception;}
    private static final class RawUnsafeLifetimeFailure extends AssertionError {
        RawUnsafeLifetimeFailure(String operation,Throwable failure){super(operation+" did not confirm rejection; control lifetime is now unknown",failure);}
    }
    private static void rawUnsafeRefused(long address,byte[] before,String operation,RawUnsafeAction action)throws Exception {
        rawUnsafeRefused(address,before,operation,"NATIVE_MEMORY_CONTROL_WRITE_DENIED",false,action);
    }
    private static void rawUnsafeRefused(long address,byte[] before,String operation,String expected,boolean lifetime,RawUnsafeAction action)throws Exception {
        Throwable refused=null;
        try {action.run();}
        catch(InvocationTargetException wrapped){refused=wrapped.getCause();}
        catch(IllegalStateException|SecurityException failure){refused=failure;}
        boolean denied=refused instanceof IllegalStateException&&expected.equals(refused.getMessage());
        // Never inspect or dispose a possibly freed/reallocated control target.
        if(!denied&&lifetime)throw new RawUnsafeLifetimeFailure(operation,refused);
        require(denied,operation+" was not refused at the actual Native control destination: "+refused);
        for(int offset=0;offset<before.length;offset++)require(U.getByte(address+offset)==before[offset],operation+" changed first-slot bytes");
    }
    /** Invoked synchronously by the fixture JNI call while its real JVMTI environment is still live. */
    private static void rawUnsafeSlots(long vm,long api,long env)throws Exception {
        require(U.addressSize()==Long.BYTES&&vm!=0&&api!=0&&env!=0&&vm!=api&&api!=env&&vm!=env,"actual x64 VM/JVMTI/JNI slots required");
        Class<?> internal=Class.forName("jdk.internal.misc.Unsafe",false,null);
        Field singleton=internal.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        Method putLong=internal.getDeclaredMethod("putLong",Object.class,long.class,long.class);
        Method setMemory=internal.getDeclaredMethod("setMemory0",Object.class,long.class,long.class,byte.class);
        Method copyMemory=internal.getDeclaredMethod("copyMemory0",Object.class,long.class,Object.class,long.class,long.class);
        Method freeMemory=internal.getDeclaredMethod("freeMemory0",long.class);
        Method reallocateMemory=internal.getDeclaredMethod("reallocateMemory0",long.class,long.class);
        putLong.setAccessible(true);setMemory.setAccessible(true);copyMemory.setAccessible(true);freeMemory.setAccessible(true);reallocateMemory.setAccessible(true);
        require(internal.getClassLoader()==null&&Modifier.isNative(putLong.getModifiers())&&Modifier.isNative(setMemory.getModifiers())
                &&Modifier.isNative(copyMemory.getModifiers())&&Modifier.isNative(freeMemory.getModifiers())
                &&Modifier.isNative(reallocateMemory.getModifiers()),"exact observed bootstrap Unsafe native methods required");
        long first=0,second=0,fromNull=0;boolean lifetimeUnknown=false;
        try {
            first=U.allocateMemory(32);second=U.allocateMemory(32);
            require(first!=0&&second!=0&&first!=second,"actual ordinary native allocation required");
            U.setMemory(first,32,(byte)0x21);U.setMemory(second,32,(byte)0x32);
            U.putLong(first,0x1020304050607080L);
            require(U.getLong(first)==0x1020304050607080L,"ordinary sun.misc putLong changed value");
            putLong.invoke(unsafe,null,first+8,0x1122334455667788L);
            require(U.getLong(first+8)==0x1122334455667788L,"ordinary internal native putLong changed value");
            setMemory.invoke(unsafe,null,first+16,1L,(byte)0x56);
            require(U.getByte(first+16)==(byte)0x56&&U.getByte(first+17)==(byte)0x21,"ordinary native setMemory0 changed range");
            copyMemory.invoke(unsafe,null,first,null,second,32L);
            for(int offset=0;offset<32;offset++)require(U.getByte(first+offset)==U.getByte(second+offset),"ordinary native copyMemory0 changed bytes");
            setMemory.invoke(unsafe,null,first,0L,(byte)0x7f);
            copyMemory.invoke(unsafe,null,first,null,second,0L);
            U.setMemory(first,0,(byte)0x7f);U.copyMemory(first,second,0);
            for(int offset=0;offset<32;offset++)require(U.getByte(first+offset)==U.getByte(second+offset),"zero-length operation changed ordinary buffers");
            byte[] prefix=new byte[32];for(int offset=0;offset<prefix.length;offset++)prefix[offset]=U.getByte(first+offset);
            long grown=U.reallocateMemory(first,64);require(grown!=0,"ordinary public reallocation returned NULL");first=grown;
            for(int offset=0;offset<prefix.length;offset++)require(U.getByte(first+offset)==prefix[offset],"ordinary growing reallocation changed prefix");
            long shrunk=(Long)reallocateMemory.invoke(unsafe,first,16L);require(shrunk!=0,"ordinary native reallocation returned NULL");first=shrunk;
            for(int offset=0;offset<16;offset++)require(U.getByte(first+offset)==prefix[offset],"ordinary shrinking reallocation changed prefix");
            U.freeMemory(0);
            fromNull=U.reallocateMemory(0,Long.BYTES);require(fromNull!=0,"legal NULL public reallocation did not allocate");
            U.putLong(fromNull,0x123456789abcdef0L);require(U.getLong(fromNull)==0x123456789abcdef0L,"NULL-origin allocation did not retain ordinary data");
            fromNull=U.reallocateMemory(fromNull,0);require(fromNull==0,"public zero-length reallocation did not return NULL");
            for(long address:new long[]{vm,api,env}) {
                byte[] before=new byte[Long.BYTES];for(int offset=0;offset<before.length;offset++)before[offset]=U.getByte(address+offset);
                long value=U.getLong(address);require(value!=0,"actual Native first table pointer required");
                rawUnsafeRefused(address,before,"sun.misc.putLong",()->U.putLong(address,value));
                rawUnsafeRefused(address,before,"internal.native.putLong",()->putLong.invoke(unsafe,null,address,value));
                rawUnsafeRefused(address,before,"internal.native.setMemory0",()->setMemory.invoke(unsafe,null,address,1L,before[0]));
                U.putLong(first,value);long source=first;
                rawUnsafeRefused(address,before,"internal.native.copyMemory0",()->copyMemory.invoke(unsafe,null,source,null,address,(long)Long.BYTES));
                rawUnsafeRefused(address,before,"sun.misc.freeMemory","NATIVE_MEMORY_CONTROL_FREE_DENIED",true,()->U.freeMemory(address));
                rawUnsafeRefused(address,before,"internal.native.freeMemory0","NATIVE_MEMORY_CONTROL_FREE_DENIED",true,()->freeMemory.invoke(unsafe,address));
                rawUnsafeRefused(address,before,"sun.misc.reallocateMemory","NATIVE_MEMORY_CONTROL_REALLOCATE_DENIED",true,()->U.reallocateMemory(address,16));
                rawUnsafeRefused(address,before,"internal.native.reallocateMemory0","NATIVE_MEMORY_CONTROL_REALLOCATE_DENIED",true,()->reallocateMemory.invoke(unsafe,address,16L));
                // The JDK public wrapper implements size zero by calling freeMemory.
                rawUnsafeRefused(address,before,"sun.misc.reallocateMemory.zero","NATIVE_MEMORY_CONTROL_FREE_DENIED",true,()->U.reallocateMemory(address,0));
                rawUnsafeRefused(address,before,"internal.native.reallocateMemory0.zero","NATIVE_MEMORY_CONTROL_REALLOCATE_DENIED",true,()->reallocateMemory.invoke(unsafe,address,0L));
            }
            // A refused copy must close its read scope; a following ordinary copy still executes.
            U.copyMemory(first,second,Long.BYTES);
            require(U.getLong(first)==U.getLong(second),"ordinary copy after target refusal changed value");
        } catch(RawUnsafeLifetimeFailure failure) {lifetimeUnknown=true;throw failure;}
        finally {
            if(!lifetimeUnknown) {
                try {if(fromNull!=0)U.freeMemory(fromNull);}
                finally {try {if(second!=0)freeMemory.invoke(unsafe,second);}
                    finally {if(first!=0)U.freeMemory(first);}}
            }
        }
    }

    // This command loads one AUDIT_JVMTI_DISPATCH_CHECK fixture; it does not run
    // the previously completed Host import/process/token or class-hook checks.
    private static native int jvmtiDispatch0(Class<?> type,Object first,Object second,Object decoy,boolean heapOnly);
    private static native Class<?> jvmtiDispatchDefine0(ClassLoader loader,char suffix);
    private static native void jvmtiDispatchPhase0(int phase);
    private static native void jvmtiDispatchCollect0();
    private static native long[] jvmtiDispatchState0();
    private static native void jvmtiDispatchFinish0();
    private static final class JvmtiDispatchPayload {Object child,decoy;}
    private record JvmtiUnloadWitness(java.lang.ref.WeakReference<ClassLoader> loader,java.lang.ref.WeakReference<Class<?>> type) {
        boolean cleared(){return loader.get()==null&&type.get()==null;}
    }
    private static JvmtiUnloadWitness jvmtiDisposableLoader(char suffix){
        ClassLoader loader=new ClassLoader(null) { };
        Class<?> type=jvmtiDispatchDefine0(loader,suffix);
        require(type!=null&&type.getClassLoader()==loader&&type.getName().equals("dev.ronova.pro.JvmtiPayload"+suffix),
                "actual JNI DefineClass did not use the requested disposable loader and class");
        return new JvmtiUnloadWitness(new java.lang.ref.WeakReference<>(loader),new java.lang.ref.WeakReference<>(type));
    }
    private static boolean jvmtiAwaitUnload(JvmtiUnloadWitness witness,int phase,long deadline)throws InterruptedException {
        // Force GC only after DefineClass's JNI locals and this helper's strong
        // loader/class references have actually left their frames.
        jvmtiDispatchCollect0();
        while(System.nanoTime()<deadline){
            long[] state=jvmtiDispatchState0();
            require(state!=null&&state.length==5&&state[2]==0,"real ClassUnload env/JNIEnv/name dispatch mismatch: "+Arrays.toString(state));
            require(state[0]<=1&&state[1]<=1,"real ClassUnload callback dispatched more than once for one fixture class: "+Arrays.toString(state));
            long expectedSecond=phase==0?0:1;
            if(state[0]==1&&state[1]==expectedSecond&&witness.cleared())return true;
            Thread.sleep(20);
        }
        System.out.println("JVMTI_CLASS_UNLOAD_NOTRUN phase="+phase+" loaderAndClassCleared="+witness.cleared()+" actualState="+Arrays.toString(jvmtiDispatchState0()));
        return false;
    }
    private static void jvmtiDispatch()throws Exception {
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.jvmti.native")).toAbsolutePath().toString());
        var first=new JvmtiDispatchPayload();var second=new JvmtiDispatchPayload();Object decoy=new Object();
        first.child=second;first.decoy=decoy;
        boolean heapOnly=System.getProperty("ronova.pro.audit.jvmti.phase","").equals("heap-only");
        int extension=jvmtiDispatch0(JvmtiDispatchPayload.class,first,second,decoy,heapOnly);
        long[] completed=null;
        try {
            if(extension==0)return;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
            if(!jvmtiAwaitUnload(jvmtiDisposableLoader('U'),0,deadline))return;
            jvmtiDispatchPhase0(1);
            if(!jvmtiAwaitUnload(jvmtiDisposableLoader('V'),1,deadline))return;
            jvmtiDispatchPhase0(2);
            if(!jvmtiAwaitUnload(jvmtiDisposableLoader('W'),2,deadline))return;
            completed=jvmtiDispatchState0();
        } finally {
            jvmtiDispatchFinish0();
            java.lang.ref.Reference.reachabilityFence(first);java.lang.ref.Reference.reachabilityFence(second);java.lang.ref.Reference.reachabilityFence(decoy);
        }
        if(completed!=null){
            System.out.println("JVMTI_CLASS_UNLOAD_DISPATCH_PASS actual_GetExtensionEvents actual_ShortLivedLoader_DefineClass actual_ForceGarbageCollection actual_ServiceThread_env_JNIEnv_name first_callback replaced_callback actual_DisposeEnvironment actualThread="+completed[3]+" actualJNIEnv="+Long.toUnsignedString(completed[4],16));
            System.out.println("JVMTI_CLASS_UNLOAD_CLEAR_OBSERVED actual_SetExtensionEventCallback_success cleared_class_weak_refs current_callback_counts_unchanged public_deferred_queue_drain_not_exposed");
        }
    }

    private static long[] storagePrepare(RecoveryStorage.Ticket ticket) {
        long[] state=StorageNative.prepare0(ticket);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(state[0]==7&&state[1]>0&&System.nanoTime()<deadline)state=StorageNative.advance0(ticket,state[1]);
        return state;
    }
    private static void storageNative()throws Exception {
        require(StorageNative.available()&&StorageNative.abi0()==3,"actual storage DLL unavailable: "+StorageNative.reason());
        byte[] original=new byte[33001],candidate=new byte[65537];
        for(int i=0;i<original.length;i++)original[i]=(byte)(i*13);
        for(int i=0;i<candidate.length;i++)candidate[i]=(byte)(i*31+7);
        var file=java.nio.file.Files.createTempFile(java.nio.file.Path.of(".").toAbsolutePath().normalize(),"storage-2023-",".bin");
        java.nio.file.Path secondFile=null;
        try {
            java.nio.file.Files.write(file,original);
            String identity;
            try(var stream=new java.io.FileInputStream(file.toFile())){identity=StorageNative.identity(stream.getFD(),file.toString());}
            require(identity!=null,"actual isolated NTFS identity unavailable");
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            String baseline=HexFormat.of().formatHex(digest.digest(original)),expected=HexFormat.of().formatHex(digest.digest(candidate));
            var runtime=(ProRuntime)U.allocateInstance(ProRuntime.class);put(runtime,"session",UUID.randomUUID());
            var sources=new RecoverySources(runtime);
            var source=(RecoverySources.Source)U.allocateInstance(RecoverySources.Source.class);put(source,"owner",sources);put(source,"path",file);
            var owner=(RecoveryStorage)U.allocateInstance(RecoveryStorage.class);
            var descriptor=Class.forName("dev.ronova.pro.RecoveryStorage$Descriptor");var descriptorConstructor=descriptor.getDeclaredConstructors()[0];descriptorConstructor.setAccessible(true);
            var workType=Class.forName("dev.ronova.pro.RecoveryStorage$Work");var workConstructor=workType.getDeclaredConstructor(descriptor);workConstructor.setAccessible(true);
            var ticketConstructor=RecoveryStorage.Ticket.class.getDeclaredConstructor(RecoveryStorage.class,workType,RecoverySources.Source.class,RecoveryImage.Encoded.class);ticketConstructor.setAccessible(true);
            require(StorageNative.prepare0(null)[0]==0,"foreign storage request admitted");
            {
                Object description=descriptorConstructor.newInstance("STORAGE/2",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1L,"source","code",1L,identity,baseline,"0".repeat(64),baseline,UUID.randomUUID(),"win32-ntfs-txf/2",null);
                Object work=workConstructor.newInstance(description);
                try(var builder=new RecoveryImage.Builder()) {
                    builder.write(candidate);
                    try(var image=builder.finish()) {
                        var ticket=(RecoveryStorage.Ticket)ticketConstructor.newInstance(owner,work,source,image);
                        put(work,"ticket",ticket);put(work,"preparing",true);
                        try {
                            long[] state=storagePrepare(ticket);
                            require(state[0]==0,"invalid candidate preparation returned "+Arrays.toString(state));
                        } finally {
                            if(ticket.token>0){long[] retired=StorageNative.finish0(ticket,ticket.token,null);require(retired[0]==0&&retired[1]==1,"invalid candidate did not retire: "+Arrays.toString(retired));}
                        }
                    }
                }
                require(Arrays.equals(original,java.nio.file.Files.readAllBytes(file)),"unpermitted candidate changed the real file");
            }
            secondFile=java.nio.file.Files.createTempFile(file.getParent(),"storage-second-",".bin");
            java.nio.file.Files.write(secondFile,original);
            String secondIdentity;
            try(var stream=new java.io.FileInputStream(secondFile.toFile())){secondIdentity=StorageNative.identity(stream.getFD(),secondFile.toString());}
            require(secondIdentity!=null,"second actual NTFS identity unavailable");
            var tickets=new RecoveryStorage.Ticket[3];
            try(var builder=new RecoveryImage.Builder()) {
                builder.write(candidate);
                try(var image=builder.finish()) {
                    for(int i=0;i<tickets.length;i++) {
                        var actualSource=(RecoverySources.Source)U.allocateInstance(RecoverySources.Source.class);
                        put(actualSource,"owner",sources);put(actualSource,"path",i==1?secondFile:file);
                        Object description=descriptorConstructor.newInstance("STORAGE/2",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1L,"source","code",1L,i==1?secondIdentity:identity,baseline,expected,baseline,UUID.randomUUID(),"win32-ntfs-txf/2",null);
                        Object work=workConstructor.newInstance(description);
                        var actualOwner=i==2?(RecoveryStorage)U.allocateInstance(RecoveryStorage.class):owner;
                        tickets[i]=(RecoveryStorage.Ticket)ticketConstructor.newInstance(actualOwner,work,actualSource,image);
                        put(work,"ticket",tickets[i]);put(work,"preparing",true);
                    }
                    try {
                        for(int i=0;i<2;i++) {
                            long[] state=storagePrepare(tickets[i]);
                            require(state[0]==1&&tickets[i].token>0,"two-file transaction preparation returned "+Arrays.toString(state));
                        }
                        require(tickets[0].token!=tickets[1].token,"two actual transactions shared a token");
                        long[] busy=StorageNative.prepare0(tickets[2]);
                        require(busy[0]==7&&busy[1]==0&&busy[2]==170&&tickets[2].token==0,"third transaction exceeded two-slot capacity: "+Arrays.toString(busy));
                        long[] foreign=StorageNative.advance0(tickets[2],tickets[0].token);
                        require(foreign[0]==0&&foreign[2]==1168,"foreign Ticket consumed another token: "+Arrays.toString(foreign));
                        long[] foreignFinish=StorageNative.finish0(tickets[2],tickets[0].token,null);
                        require(foreignFinish[1]==0,"foreign Ticket retired another transaction: "+Arrays.toString(foreignFinish));
                        for(int i=0;i<2;i++)require(StorageNative.advance0(tickets[i],tickets[i].token)[0]==1,"foreign request removed an exact owner slot");
                        long[] live=StorageNative.maintain0();
                        require(live[0]==1&&live[2]==2,"live original owners lost their two admissions: "+Arrays.toString(live));
                    } finally {
                        try {
                            if(tickets[0].token>0) {
                                long[] retired=StorageNative.finish0(tickets[0],tickets[0].token,null);
                                require(retired[0]==2&&retired[1]==1,"first unpermitted transaction did not retire: "+Arrays.toString(retired));
                            }
                        } finally {
                            if(tickets[1].token>0) {
                                long[] retired=StorageNative.finish0(tickets[1],tickets[1].token,null);
                                require(retired[0]==2&&retired[1]==1,"second unpermitted transaction did not retire: "+Arrays.toString(retired));
                            }
                        }
                    }
                }
            }
            require(Arrays.equals(original,java.nio.file.Files.readAllBytes(file))
                    &&Arrays.equals(original,java.nio.file.Files.readAllBytes(secondFile)),"two-slot unpermitted transactions changed an original file");
            long[] maintenance=StorageNative.maintain0();require(maintenance[0]==1&&maintenance[2]==0,"native storage handles retained: "+Arrays.toString(maintenance));
            System.out.println("STORAGE_NATIVE_PASS actual_dll foreign_request_refused candidate_hash_refused multi_chunk_hash staged_readback two_actual_files two_slot_capacity third_busy foreign_ticket_token_refused exact_owner_slots_preserved no_permit_rollback originals_preserved admitted_zero handles_retired");
        } finally {
            try{if(secondFile!=null)java.nio.file.Files.deleteIfExists(secondFile);}
            finally{java.nio.file.Files.deleteIfExists(file);}
        }
    }

    // Actual JNI calls and OS permissions; no retirement/source permission is manufactured here.
    private static void fileTransfer()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> channelType=Class.forName("sun.nio.ch.FileChannelImpl",true,null);
        Method nativeTransfer=channelType.getDeclaredMethod("transferTo0",java.io.FileDescriptor.class,long.class,long.class,java.io.FileDescriptor.class);
        require(Modifier.isNative(nativeTransfer.getModifiers()),"actual transferTo0 native entry absent");nativeTransfer.setAccessible(true);
        Class<?> resources=Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",true,null);
        Field operations=resources.getDeclaredField("FILE_OPERATIONS");operations.setAccessible(true);
        ThreadLocal<?> scopes=(ThreadLocal<?>)operations.get(null);
        byte[] payload=new byte[65537],baseline=new byte[117];
        for(int i=0;i<payload.length;i++)payload[i]=(byte)(i*31+7);
        for(int i=0;i<baseline.length;i++)baseline[i]=(byte)(i*13+5);
        var sourceFile=java.nio.file.Files.createTempFile("ronova-transfer-source-",".bin");
        var targetFile=java.nio.file.Files.createTempFile("ronova-transfer-readonly-",".bin");
        try {
            java.nio.file.Files.write(sourceFile,payload);java.nio.file.Files.write(targetFile,baseline);
            try(var source=new java.io.FileInputStream(sourceFile.toFile());var readOnly=new java.io.FileInputStream(targetFile.toFile())){
                var channel=source.getChannel();channel.position(11);
                transferToSocket(channel,source.getFD(),payload,19,33001,null,scopes);
                require(channel.position()==11,"public transferTo changed source position");
                try{channel.transferTo(0,17,readOnly.getChannel());throw new AssertionError("original read-only target accepted");}
                catch(java.nio.channels.NonWritableChannelException expected){ }
                require(nativeTransferRefused(nativeTransfer,channel,source.getFD(),readOnly.getFD(),scopes),"native transferTo0 bypassed the original read-only target");
                require(java.nio.file.Files.size(targetFile)==baseline.length&&Arrays.equals(baseline,java.nio.file.Files.readAllBytes(targetFile)),"read-only target bytes or length changed");
                java.io.FileDescriptor closedSource,closedTarget;
                try(var closed=new java.io.FileInputStream(sourceFile.toFile())){closedSource=closed.getFD();}
                try(var closed=new java.io.FileInputStream(targetFile.toFile())){closedTarget=closed.getFD();}
                require(!closedSource.valid()&&!closedTarget.valid(),"closed descriptor fixture retained an OS handle");
                try{nativeTransfer(nativeTransfer,channel,closedSource,0,17,source.getFD(),scopes);throw new AssertionError("closed native source accepted");}
                catch(java.io.IOException expected){ }
                require(nativeTransferRefused(nativeTransfer,channel,source.getFD(),closedTarget,scopes),"closed native target accepted");
                require(nativeTransferRefused(nativeTransfer,channel,source.getFD(),source.getFD(),scopes),"same file descriptor accepted as a socket target");
                transferToSocket(channel,source.getFD(),payload,17,32771,nativeTransfer,scopes);
            }
            require(java.nio.file.Files.size(sourceFile)==payload.length&&Arrays.equals(payload,java.nio.file.Files.readAllBytes(sourceFile)),"source bytes or length changed");
            require(java.nio.file.Files.size(targetFile)==baseline.length&&Arrays.equals(baseline,java.nio.file.Files.readAllBytes(targetFile)),"rejected target changed after following transfer");
            System.out.println("FILE_TRANSFER_PASS actual_native public_socket_bytes_and_length native_socket_bytes_and_length readonly_target_preserved closed_source_refused closed_target_refused same_descriptor_preserved native_failure_scope_restored following_transfer_preserved");
            System.out.println("FILE_TRANSFER_SCOPE_UNCHECKED sealed_target_requires_actual_retirement_writer");
        } finally {
            try{java.nio.file.Files.deleteIfExists(targetFile);}finally{java.nio.file.Files.deleteIfExists(sourceFile);}
        }
    }
    private static long nativeTransfer(Method entry,java.nio.channels.FileChannel channel,java.io.FileDescriptor source,
            long position,long count,java.io.FileDescriptor target,ThreadLocal<?> scopes)throws Exception {
        Object previous=scopes.get();
        try{return (Long)entry.invoke(channel,source,position,count,target);}
        catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception cause)throw cause;
            if(failure.getCause() instanceof Error cause)throw cause;throw failure;
        }finally{require(scopes.get()==previous,"native transferTo0 left a file operation scope live");}
    }
    private static boolean nativeTransferRefused(Method entry,java.nio.channels.FileChannel channel,
            java.io.FileDescriptor source,java.io.FileDescriptor target,ThreadLocal<?> scopes)throws Exception {
        try{return nativeTransfer(entry,channel,source,0,17,target,scopes)<=0;}
        catch(java.io.IOException refused){return true;}
    }
    private static void transferToSocket(java.nio.channels.FileChannel source,java.io.FileDescriptor descriptor,byte[] payload,
            int position,int length,Method nativeEntry,ThreadLocal<?> scopes)throws Exception {
        Object previous=scopes.get();
        try(var listener=java.nio.channels.ServerSocketChannel.open()){
            listener.bind(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),0));
            try(var sender=java.nio.channels.SocketChannel.open(listener.getLocalAddress());var receiver=listener.accept()){
                receiver.socket().setSoTimeout(10000);
                Field fd=sender.getClass().getDeclaredField("fd");fd.setAccessible(true);
                var target=(java.io.FileDescriptor)fd.get(sender);
                var received=CompletableFuture.supplyAsync(()->{
                    try(var input=receiver.socket().getInputStream()){return input.readNBytes(length+1);}
                    catch(java.io.IOException failure){throw new CompletionException(failure);}
                });
                long transferred=0,deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(transferred<length){
                    long count=Math.min(4096,length-transferred);
                    long written=nativeEntry==null?source.transferTo(position+transferred,count,sender)
                            :nativeTransfer(nativeEntry,source,descriptor,position+transferred,count,target,scopes);
                    require(written>=0&&written<=count,"actual transfer returned an invalid length: "+written);transferred+=written;
                    require(System.nanoTime()<deadline,"actual file/socket transfer did not finish");
                    if(written==0)Thread.sleep(1);
                }
                sender.shutdownOutput();byte[] bytes=received.get(10,TimeUnit.SECONDS);
                require(transferred==length&&bytes.length==length&&Arrays.equals(bytes,Arrays.copyOfRange(payload,position,position+length)),"actual file/socket transfer changed bytes or length");
            }
        }finally{require(scopes.get()==previous,"public file/socket transfer left a file operation scope live");}
    }
    private record HostLoadObservation(long ansi,long wide,int flags) { }
    private static final List<HostLoadObservation> HOST_LOAD_OBSERVATIONS=new ArrayList<>();
    private static java.nio.file.Path hostLoadDirectory;
    private static native long hostLoadCachedCreate0(long pointer,boolean ansi);
    public static void hostLoadObserved(long ansi,long wide,int flags){HOST_LOAD_OBSERVATIONS.add(new HostLoadObservation(ansi,wide,flags));}
    public static void hostLoadNested(){System.load(hostLoadDirectory.resolve("audit-host-load-inner.dll").toString());}
    private static void hostLoad()throws Exception {
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(controls.getMethod("available").invoke(null)),"actual Windows native backend absent");
        hostLoadDirectory=java.nio.file.Path.of(System.getProperty("ronova.pro.audit.host.directory")).toAbsolutePath();
        System.load(hostLoadDirectory.resolve("audit-host-load-support.dll").toString());HOST_LOAD_OBSERVATIONS.clear();
        System.load(hostLoadDirectory.resolve("audit-host-load-normal.dll").toString());
        require(HOST_LOAD_OBSERVATIONS.size()==1,"normal JNI_OnLoad observation missing");
        HostLoadObservation retained=hostLoadObservation(0);
        HOST_LOAD_OBSERVATIONS.clear();
        System.load(hostLoadDirectory.resolve("audit-host-load-outer.dll").toString());
        require(HOST_LOAD_OBSERVATIONS.size()==2,"nested JNI_OnLoad scopes were not both observed");
        hostLoadObservation(0);hostLoadObservation(1);
        HOST_LOAD_OBSERVATIONS.clear();
        try{System.load(hostLoadDirectory.resolve("audit-host-load-fail.dll").toString());throw new AssertionError("invalid JNI_OnLoad version accepted");}
        catch(UnsatisfiedLinkError expected){ }
        require(HOST_LOAD_OBSERVATIONS.size()==1,"failed JNI_OnLoad observation missing");
        retiredHostLoad(hostLoadObservation(0));
        HOST_LOAD_OBSERVATIONS.clear();
        try{System.load(hostLoadDirectory.resolve("audit-host-load-throw.dll").toString());throw new AssertionError("throwing JNI_OnLoad accepted");}
        catch(IllegalStateException expected){require("AUDIT_HOST_LOAD_THROW".equals(expected.getMessage()),"JNI_OnLoad exception was replaced");}
        require(HOST_LOAD_OBSERVATIONS.size()==1,"throwing JNI_OnLoad observation missing");
        retiredHostLoad(hostLoadObservation(0));
        require(hostLoadCachedCreate0(retained.ansi(),true)>>>32!=0,"unrelated live ANSI entry was retired");
        require(hostLoadCachedCreate0(retained.wide(),false)>>>32!=0,"unrelated live Unicode entry was retired");
        System.out.println("HOST_LOAD_PASS actual_jdk_system_load JNI_OnLoad_A_W_imports_dynamic_children nested_load_scopes invalid_version_unloaded original_exception_preserved cached_unloaded_entries_refused unrelated_live_entries_preserved");
        System.out.println("HOST_LOAD_SCOPE_UNCHECKED DllMain_TLS_before_OS_load_return");
    }
    private static HostLoadObservation hostLoadObservation(int index){
        HostLoadObservation observation=HOST_LOAD_OBSERVATIONS.get(index);
        require(observation.ansi()!=0&&observation.wide()!=0&&observation.flags()==63,"JNI_OnLoad did not consume controlled A/W entries: "+observation.flags());return observation;
    }
    private static void retiredHostLoad(HostLoadObservation observation){
        long ansi=hostLoadCachedCreate0(observation.ansi(),true),wide=hostLoadCachedCreate0(observation.wide(),false);
        require(ansi>>>32==0&&(ansi&0xffffffffL)==126,"unloaded ANSI entry was reusable or lost its original error");
        require(wide>>>32==0&&(wide&0xffffffffL)==126,"unloaded Unicode entry was reusable or lost its original error");
    }
    private static void socketAccept()throws Exception {
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(controls.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> net=Class.forName("sun.nio.ch.Net",true,null);
        Method accept=net.getDeclaredMethod("accept",java.io.FileDescriptor.class,java.io.FileDescriptor.class,java.net.InetSocketAddress[].class);
        require(Modifier.isNative(accept.getModifiers()),"actual Net.accept native entry absent");accept.setAccessible(true);
        Field operations=Class.forName("dev.ronova.pro.bootstrap.ResourceBridge",true,null).getDeclaredField("FILE_OPERATIONS");
        operations.setAccessible(true);ThreadLocal<?> scopes=(ThreadLocal<?>)operations.get(null);
        java.io.FileDescriptor closedListener;
        try(var closed=java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.INET)){
            closed.bind(new java.net.InetSocketAddress("127.0.0.1",0));closedListener=socketDescriptor(closed);
        }
        var untouched=new java.io.FileDescriptor();
        try{nativeAccept(accept,closedListener,untouched,new java.net.InetSocketAddress[1],scopes);throw new AssertionError("closed native listener accepted");}
        catch(java.io.IOException expected){ }
        require(!untouched.valid(),"failed accept published a target handle");
        try(var listener=java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.INET)){
            listener.bind(new java.net.InetSocketAddress("127.0.0.1",0));listener.configureBlocking(false);
            Object previous=scopes.get();
            try(var peer=java.nio.channels.SocketChannel.open(listener.getLocalAddress())){
                java.nio.channels.SocketChannel accepted=null;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(accepted==null){accepted=listener.accept();if(accepted==null){require(System.nanoTime()<deadline,"public accept did not complete");Thread.sleep(1);}}
                try(var actual=accepted){socketExchange(peer,actual);}
            }
            require(scopes.get()==previous,"public accept left a file operation scope live");
            var output=new java.io.FileDescriptor();var addresses=new java.net.InetSocketAddress[1];
            try(var peer=java.nio.channels.SocketChannel.open(listener.getLocalAddress())){
                java.nio.channels.SocketChannel accepted=null;Throwable failure=null;
                try{
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);int count;
                    do{count=nativeAccept(accept,socketDescriptor(listener),output,addresses,scopes);require(System.nanoTime()<deadline,"native accept did not complete");if(count<=0)Thread.sleep(1);}while(count<=0);
                    require(output.valid()&&addresses[0]!=null,"native accept omitted its actual descriptor/address");
                    var type=Class.forName("sun.nio.ch.SocketChannelImpl",true,null);
                    var constructor=type.getDeclaredConstructor(java.nio.channels.spi.SelectorProvider.class,java.net.ProtocolFamily.class,
                            java.io.FileDescriptor.class,java.net.SocketAddress.class);constructor.setAccessible(true);
                    accepted=(java.nio.channels.SocketChannel)constructor.newInstance(listener.provider(),java.net.StandardProtocolFamily.INET,output,addresses[0]);
                    try(var actual=accepted){socketExchange(peer,actual);}
                }catch(Exception|Error thrown){failure=thrown;throw thrown;}
                finally{
                    if(accepted==null&&output.valid())try{
                        Class<?> dispatcher=Class.forName("sun.nio.ch.SocketDispatcher",true,null);var make=dispatcher.getDeclaredConstructor();make.setAccessible(true);
                        Method close=dispatcher.getDeclaredMethod("close",java.io.FileDescriptor.class);close.setAccessible(true);close.invoke(make.newInstance(),output);
                    }catch(Exception|Error cleanup){if(failure==null)throw cleanup;failure.addSuppressed(cleanup);}
                }
            }
            require(scopes.get()==previous,"native accept/data exchange left a file operation scope live");
        }
        System.out.println("SOCKET_ACCEPT_PASS actual_native closed_listener_refused output_preserved native_failure_scope_restored public_accept_duplex_bytes native_accept_actual_output_duplex_bytes");
        System.out.println("SOCKET_ACCEPT_SCOPE_UNCHECKED sealed_output_requires_actual_retirement_writer");
    }
    private static java.io.FileDescriptor socketDescriptor(Object socket)throws Exception {
        Field fd=socket.getClass().getDeclaredField("fd");fd.setAccessible(true);return (java.io.FileDescriptor)fd.get(socket);
    }
    private static int nativeAccept(Method entry,java.io.FileDescriptor listener,java.io.FileDescriptor target,
            java.net.InetSocketAddress[] address,ThreadLocal<?> scopes)throws Exception {
        Object previous=scopes.get();
        try{return (Integer)entry.invoke(null,listener,target,address);}
        catch(InvocationTargetException failure){
            if(failure.getCause() instanceof Exception cause)throw cause;
            if(failure.getCause() instanceof Error cause)throw cause;throw failure;
        }finally{require(scopes.get()==previous,"native accept left a file operation scope live");}
    }
    private static void socketExchange(java.nio.channels.SocketChannel left,java.nio.channels.SocketChannel right)throws Exception {
        left.configureBlocking(false);right.configureBlocking(false);
        byte[] request={2,5,9,17,31},response={7,11,19,37};
        socketMessage(left,right,request);socketMessage(right,left,response);
    }
    private static void socketMessage(java.nio.channels.SocketChannel source,java.nio.channels.SocketChannel target,byte[] bytes)throws Exception {
        var output=java.nio.ByteBuffer.wrap(bytes);var input=java.nio.ByteBuffer.allocate(bytes.length);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(output.hasRemaining()||input.hasRemaining()){
            if(output.hasRemaining())source.write(output);
            int received=input.hasRemaining()?target.read(input):0;require(received>=0,"accepted socket ended before its actual message");
            require(System.nanoTime()<deadline,"accepted socket data did not complete");if(received==0)Thread.sleep(1);
        }
        require(Arrays.equals(bytes,input.array()),"accepted socket changed actual message bytes");
    }
    private static void worldHeldSave(){
        String heldType="ronova_audit:held_tick",otherType="ronova_audit:other_tick";
        var pos=new net.minecraft.core.BlockPos(17,64,-31);
        var pending=new net.minecraft.world.ticks.ScheduledTick<>(heldType,pos,300L,net.minecraft.world.ticks.TickPriority.LOW,9L);
        var other=new net.minecraft.world.ticks.ScheduledTick<>(otherType,pos,220L,net.minecraft.world.ticks.TickPriority.VERY_HIGH,10L);
        var held=new net.minecraft.world.ticks.ScheduledTick<>(heldType,pos,90L,net.minecraft.world.ticks.TickPriority.HIGH,1L);
        var original=new net.minecraft.world.ticks.LevelChunkTicks<String>();original.schedule(pending);original.schedule(other);
        List<net.minecraft.world.ticks.ScheduledTick<String>> deferred=new ArrayList<>(List.of(held));
        java.util.function.Function<String,String> names=java.util.function.Function.identity();
        Map<String,String> types=Map.of(heldType,heldType,otherType,otherType);
        java.util.function.Function<String,Optional<String>> lookup=id->Optional.ofNullable(types.get(id));
        long[] times={100L,125L};var before=List.of(original.save(times[0],names),original.save(times[1],names));
        var merged=WorldControl.withHeldTicks(original,deferred);
        for(int pass=0;pass<times.length;pass++){
            long now=times[pass];var encoded=merged.save(now,names);
            require(encoded instanceof net.minecraft.nbt.ListTag,"held tick save did not return the actual NBT tick list");
            var list=(net.minecraft.nbt.ListTag)encoded;
            List<net.minecraft.world.ticks.SavedTick<String>> loaded=new ArrayList<>();
            for(int index=0;index<list.size();index++)loaded.add(net.minecraft.world.ticks.SavedTick.loadTick(list.getCompound(index),lookup)
                    .orElseThrow(()->new AssertionError("vanilla SavedTick could not decode a held tick save row")));
            require(loaded.size()==2,"held tick save duplicated the same type/position or lost the other type");
            var savedHeld=loaded.stream().filter(entry->entry.type()==heldType).findFirst()
                    .orElseThrow(()->new AssertionError("held tick type identity absent after vanilla NBT decode"));
            var savedOther=loaded.stream().filter(entry->entry.type()==otherType).findFirst()
                    .orElseThrow(()->new AssertionError("other tick type at the same position was removed"));
            require(savedHeld.pos().equals(pos)&&savedHeld.priority()==net.minecraft.world.ticks.TickPriority.HIGH&&savedHeld.delay()==90L-now,
                    "old held tick did not replace the newer same-key pending tick with its original priority and trigger-now delay");
            require(savedOther.pos().equals(pos)&&savedOther.priority()==net.minecraft.world.ticks.TickPriority.VERY_HIGH&&savedOther.delay()==220L-now,
                    "held tick save changed the other type's position, priority or trigger-now delay");
            require(original.count()==2&&original.peek()==other&&original.getAll().anyMatch(entry->entry==pending)
                    &&original.getAll().anyMatch(entry->entry==other)&&original.save(now,names).equals(before.get(pass)),
                    "held tick serialization changed or consumed the original chunk tick container");
            require(deferred.size()==1&&deferred.get(0)==held&&held.type()==heldType&&held.pos()==pos&&held.triggerTick()==90L
                    &&held.priority()==net.minecraft.world.ticks.TickPriority.HIGH&&held.subTickOrder()==1L
                    &&pending.triggerTick()==300L&&pending.priority()==net.minecraft.world.ticks.TickPriority.LOW&&pending.subTickOrder()==9L
                    &&other.triggerTick()==220L&&other.priority()==net.minecraft.world.ticks.TickPriority.VERY_HIGH&&other.subTickOrder()==10L,
                    "held tick serialization modified a captured tick or consumed the deferred list");
        }
        System.out.println("WORLD_HELD_SAVE_PASS actual_LevelChunkTicks_ScheduledTick_NBT_vanilla_SavedTick_decode same_identity_position_old_held_replaces_pending other_type_same_position_preserved priority_trigger_minus_now_preserved repeated_save_delay_recomputed original_container_ticks_deferred_unchanged data_codec_only");
    }
    @SuppressWarnings("unchecked")
    private static final class RootCandidateSource {static Object value(){return null;}}
    private static final class RootCandidateCaller {static Object get(){return RootCandidateSource.value();}}
    @SuppressWarnings("unchecked")
    private static void externalRootCandidates()throws Exception {
        Class<?> definitions=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions"),runtime=Class.forName("dev.ronova.pro.agent.ExternalCodeRuntime"),agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),group=Class.forName("dev.ronova.pro.agent.ModGroupBoundary");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null),codeSource=Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);
        Field controller=controls.getDeclaredField("instrumentationOwner"),owner=nativeControl.getDeclaredField("definitionDirectoryOwner"),directory=definitions.getDeclaredField("DIRECTORY"),scopes=definitions.getDeclaredField("sourceScopes"),metadata=group.getDeclaredField("metadata"),producers=agent.getDeclaredField("producerModules"),sourceBridge=agent.getDeclaredField("codeSourceBridge"),sourceAgent=codeSource.getDeclaredField("agent");
        for(Field field:List.of(controller,owner,directory,scopes,metadata,producers,sourceBridge,sourceAgent))field.setAccessible(true);
        Object oldController=controller.get(null),oldOwner=owner.get(null),oldScopes=scopes.get(null),oldSourceBridge=sourceBridge.get(null),oldSourceAgent=sourceAgent.get(null);
        var slot=(java.util.concurrent.atomic.AtomicReference<Object>)directory.get(null);Object oldDirectory=slot.get();
        Map<Module,Object> moduleMetadata=(Map<Module,Object>)metadata.get(null);Map<Module,Boolean> producerRows=(Map<Module,Boolean>)producers.get(null);
        Module sql=Class.forName("java.sql.Driver").getModule(),ordinary=AuditFixCheck.class.getModule();Object oldMetadata=moduleMetadata.get(sql);Boolean oldProducer=producerRows.get(sql);
        Class<?> scopesType=scopes.getType(),directoryType=oldDirectory.getClass();Constructor<?> scopeConstructor=scopesType.getDeclaredConstructor(List.class,Set.class,Set.class),directoryConstructor=directoryType.getDeclaredConstructor(List.class,List.class,List.class,List.class);
        scopeConstructor.setAccessible(true);directoryConstructor.setAccessible(true);
        Method references=definitions.getDeclaredMethod("references",String.class,byte[].class),observe=definitions.getDeclaredMethod("observeSource",ClassLoader.class,String.class,Module.class),candidate=definitions.getDeclaredMethod("executionSourcesObserved",ClassLoader.class,String.class),producer=agent.getDeclaredMethod("producerModule",Module.class);
        Method prepare=runtime.getDeclaredMethod("prepare",Module.class,ClassLoader.class,String.class,Class.class,byte[].class,boolean.class,boolean.class);
        for(Method method:List.of(references,observe,candidate,producer,prepare))method.setAccessible(true);
        String callerName=RootCandidateCaller.class.getName().replace('.','/'),sourceName=RootCandidateSource.class.getName().replace('.','/');ClassLoader loader=new ClassLoader(null){};
        byte[] bytes;try(var input=AuditFixCheck.class.getResourceAsStream("/"+callerName+".class")){bytes=Objects.requireNonNull(input).readAllBytes();}
        try{
            controller.set(null,agent);owner.set(null,definitions);sourceBridge.set(null,codeSource);sourceAgent.set(null,agent);scopes.set(null,scopeConstructor.newInstance(List.of(),Set.of(),Set.of()));slot.set(directoryConstructor.newInstance(List.of(),List.of(),List.of(),List.of()));
            Object header=references.invoke(null,callerName,bytes);require(header!=null&&((Set<?>)invoke(header,"references")).contains(sourceName),"lightweight class reference decoder lost real call owner");
            Method retain=definitions.getDeclaredMethod("referenceOnly",ClassLoader.class,String.class,Class.class,Module.class,byte[].class,byte[].class,header.getClass());retain.setAccessible(true);
            require(Boolean.FALSE.equals(candidate.invoke(null,loader,callerName)),"unrelated class inherited source state before discovery");
            retain.invoke(null,loader,callerName,null,ordinary,bytes,bytes,header);
            List<?> rows=(List<?>)invoke(slot.get(),"definitions");require(rows.size()==1,"pre-source caller was not retained for later promotion");Object definition=rows.get(0);
            Field light=definition.getClass().getDeclaredField("referenceOnly"),analysis=definition.getClass().getDeclaredField("analysis");light.setAccessible(true);analysis.setAccessible(true);
            require(light.getBoolean(definition)&&analysis.get(definition)==null,"reference-only miss eagerly built an instruction graph");
            byte[] malformed={0,0,0,0};Object skipped=prepare.invoke(null,ordinary,loader,"audit/Unrelated",null,malformed,false,false);
            require(invoke(skipped,"image")==null&&invoke(skipped,"bytes")==malformed,"noncandidate reached full class parser");
            observe.invoke(null,loader,sourceName,sql);
            require(Boolean.TRUE.equals(candidate.invoke(null,loader,callerName))&&Boolean.FALSE.equals(candidate.invoke(null,new ClassLoader(null){},"audit/Unrelated")),"later actual source did not promote retained caller, or polluted unrelated class");
            moduleMetadata.put(sql,Set.of());producerRows.put(sql,Boolean.FALSE);require(Boolean.FALSE.equals(producer.invoke(null,sql)),"empty metadata was classified as producer");
            moduleMetadata.put(sql,Set.of("audit_source"));require(Boolean.TRUE.equals(producer.invoke(null,sql))&&Boolean.TRUE.equals(producerRows.get(sql)),"early negative cache hid a later confirmed producer");
        }finally{
            slot.set(oldDirectory);scopes.set(null,oldScopes);owner.set(null,oldOwner);controller.set(null,oldController);sourceBridge.set(null,oldSourceBridge);sourceAgent.set(null,oldSourceAgent);
            if(oldMetadata==null)moduleMetadata.remove(sql);else moduleMetadata.put(sql,oldMetadata);
            if(oldProducer==null)producerRows.remove(sql);else producerRows.put(sql,oldProducer);
        }
        System.out.println("EXTERNAL_ROOT_CANDIDATES_PASS actual_header_and_reference_only_retention no_ASM_for_explicit_miss later_source_admits_prior_caller unrelated_class_fast_pass early_negative_cache_rechecked isolated_metadata_no_Native_layout");
    }
    @SuppressWarnings("unchecked")
    private static void definitionRetryLifecycle()throws Exception {
        Class<?> definitions=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions"),agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        Field controller=controls.getDeclaredField("instrumentationOwner"),directoryOwner=nativeControl.getDeclaredField("definitionDirectoryOwner"),directory=definitions.getDeclaredField("DIRECTORY");
        for(Field field:List.of(controller,directoryOwner,directory))field.setAccessible(true);
        Object oldController=controller.get(null),oldOwner=directoryOwner.get(null);
        var slot=(java.util.concurrent.atomic.AtomicReference<Object>)directory.get(null);Object oldDirectory=slot.get();
        Class<?> directoryType=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions$Directory"),referenceType=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions$Ref"),refreshType=Class.forName("dev.ronova.pro.agent.ExternalCodeDefinitions$Refresh");
        Constructor<?> row=directoryType.getDeclaredConstructor(List.class,List.class,List.class,List.class),reference=referenceType.getDeclaredConstructor(Object.class),refresh=refreshType.getDeclaredConstructor(Class.class,Module[].class);
        for(Constructor<?> constructor:List.of(row,reference,refresh))constructor.setAccessible(true);
        Method merge=definitions.getDeclaredMethod("mergeRefreshes",List.class,List.class),work=definitions.getDeclaredMethod("work",List.class,boolean.class,Set.class),run=definitions.getDeclaredMethod("run",java.lang.instrument.Instrumentation.class);
        Method continuing=directoryType.getDeclaredMethod("continuing"),active=directoryType.getDeclaredMethod("active"),actual=refreshType.getDeclaredMethod("actual"),owners=refreshType.getDeclaredMethod("owners");
        for(Method method:List.of(merge,work,run,continuing,active,actual,owners))method.setAccessible(true);
        Class<?> first=AuditFixCheck.class,second=Class.forName("java.sql.Driver");Module base=Object.class.getModule(),sql=second.getModule(),logging=Class.forName("java.util.logging.Logger").getModule();
        Object firstRef=reference.newInstance(first),secondRef=reference.newInstance(second);
        Object oldFirst=refresh.newInstance(first,new Module[]{base}),oldSecond=refresh.newInstance(second,new Module[]{sql}),newFirst=refresh.newInstance(first,new Module[]{logging});
        Thread worker=null;var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try{
            controller.set(null,agent);directoryOwner.set(null,definitions);
            slot.set(row.newInstance(List.of(),List.of(),List.of(firstRef,secondRef),List.of(oldFirst,oldSecond)));
            List<?> merged=(List<?>)merge.invoke(null,List.of(oldFirst,oldSecond),List.of(newFirst));
            Object combined=merged.stream().filter(job->{try{return actual.invoke(job)==first;}catch(Exception e){throw new IllegalStateException(e);}}).findFirst().orElseThrow();
            require(Arrays.asList((Module[])owners.invoke(combined)).containsAll(List.of(base,logging)),"refresh retry lost a prior source owner");
            work.invoke(null,merged,true,Set.of(first));Object pending=slot.get();
            require(((List<?>)continuing.invoke(pending)).equals(List.of(secondRef)),"successful refresh erased another unresolved continuation");
            List<?> pendingJobs=(List<?>)active.invoke(pending);require(pendingJobs.size()==1&&actual.invoke(pendingJobs.get(0))==second,"unresolved source owner obligation was discarded");
            slot.set(row.newInstance(List.of(),List.of(firstRef),List.of(),List.of()));
            var attempts=new java.util.concurrent.atomic.AtomicInteger();
            var api=(java.lang.instrument.Instrumentation)Proxy.newProxyInstance(AuditFixCheck.class.getClassLoader(),new Class<?>[]{java.lang.instrument.Instrumentation.class},(proxy,method,args)->{
                if(method.getName().equals("retransformClasses")){attempts.incrementAndGet();return null;}
                return method.getReturnType()==boolean.class?true:method.getName().equals("getAllLoadedClasses")?new Class<?>[0]:null;
            });
            worker=new Thread(()->{try{run.invoke(null,api);}catch(Throwable error){failure.set(error);}},"audit-definition-retry");worker.setDaemon(true);worker.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline&&failure.get()==null&&attempts.get()<2){
                java.util.concurrent.locks.LockSupport.parkNanos(2_000_000L);
            }
            require(attempts.get()>=2&&failure.get()==null&&((List<?>)continuing.invoke(slot.get())).contains(firstRef),"unresolved definition did not retry without a new change event");
        }finally{
            if(worker!=null){worker.interrupt();worker.join(5000);require(!worker.isAlive(),"retry fixture worker did not stop");}
            slot.set(oldDirectory);directoryOwner.set(null,oldOwner);controller.set(null,oldController);
        }
        System.out.println("DEFINITION_RETRY_PASS actual_worker_retries_unresolved_without_new_event prior_source_owners_merged success_removes_only_completed isolated_metadata_proxy_no_VM_retransform");
    }
    private static void nativeInstallLifecycle()throws Exception {
        Class<?> control=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);Map<Field,Object> saved=new LinkedHashMap<>();
        for(String name:List.of("available","state","installMode","installRetryable","nativeHandoffRetryable","installing","nativeLoadAttempted","nativeLoaded","nativeBoundaryInstalled","nativeImageSelector","nativeImageFinalizer","definitionDirectoryOwner")){
            Field field=control.getDeclaredField(name);field.setAccessible(true);saved.put(field,field.get(null));
        }
        Method attempt=control.getDeclaredMethod("installAttempt",java.nio.file.Path.class);attempt.setAccessible(true);
        java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("ronova-install-retry-"),empty=folder.resolve("empty-bootstrap.jar");
        try{
            for(var entry:saved.entrySet())switch(entry.getKey().getName()){
                case "available","installRetryable","nativeHandoffRetryable","installing","nativeLoadAttempted","nativeLoaded","nativeBoundaryInstalled" -> entry.getKey().setBoolean(null,false);
                case "state","installMode" -> entry.getKey().set(null,"NOT_ATTEMPTED");
            }
            attempt.invoke(null,folder.resolve("not-yet.jar"));
            require(Boolean.TRUE.equals(control.getMethod("installRetryable").invoke(null))&&"FAILED".equals(control.getMethod("installMode").invoke(null)),"pre-load IO failure was not retryable");
            try(var output=new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(empty))){}
            attempt.invoke(null,empty);
            require(Boolean.FALSE.equals(control.getMethod("installRetryable").invoke(null))&&Boolean.FALSE.equals(control.getMethod("available").invoke(null)),"absent native payload was reported ready or blindly retryable");
        }finally{
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());
            java.nio.file.Files.deleteIfExists(empty);java.nio.file.Files.deleteIfExists(folder);
        }
        System.out.println("NATIVE_INSTALL_CLASSIFICATION_PASS actual_preload_IO_retry absent_payload_permanent no_library_loaded no_native_install");
    }
    private static void stopPolicyPublication()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),group=Class.forName("dev.ronova.pro.agent.ModGroupBoundary");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),definitions=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",true,null);
        Field owner=controls.getDeclaredField("instrumentationOwner"),installed=agent.getDeclaredField("instrumentation"),stopped=controls.getDeclaredField("stoppedModules");
        owner.setAccessible(true);installed.setAccessible(true);stopped.setAccessible(true);
        Object oldOwner=owner.get(null),oldInstalled=installed.get(null),oldStopped=stopped.get(null);
        Field metadata=group.getDeclaredField("metadata"),policies=group.getDeclaredField("returns"),modes=definitions.getDeclaredField("RETURNS"),fixed=definitions.getDeclaredField("FIXED"),denied=group.getDeclaredField("denied"),exact=group.getDeclaredField("exact");
        for(Field field:List.of(metadata,policies,modes,fixed,denied,exact))field.setAccessible(true);
        Module target=Class.forName("java.sql.Driver").getModule();String[] ids={"audit_policy"};Module[] modules={target};
        Map<Module,Object> metadataRows=(Map<Module,Object>)metadata.get(null),policyRows=(Map<Module,Object>)policies.get(null);
        Map<Module,String> published=(Map<Module,String>)modes.get(null);Map<Module,UUID> identifiers=(Map<Module,UUID>)fixed.get(null);
        Object oldMetadata=metadataRows.get(target),oldPolicy=policyRows.get(target);String oldMode=published.get(target);UUID oldUuid=identifiers.get(target);
        Set<String> deniedRows=(Set<String>)denied.get(null);Set<Module> exactRows=(Set<Module>)exact.get(null);
        boolean oldDenied=deniedRows.contains(ids[0]),oldExact=exactRows.contains(target);
        var api=(java.lang.instrument.Instrumentation)Proxy.newProxyInstance(AuditFixCheck.class.getClassLoader(),new Class<?>[]{java.lang.instrument.Instrumentation.class},(proxy,method,args)->{
            if(method.getName().equals("getAllLoadedClasses"))return new Class<?>[0];
            if(method.getReturnType()==boolean.class)return true;
            if(method.getReturnType()==long.class)return 0L;return null;
        });
        Method prepare=group.getDeclaredMethod("prepare",java.lang.instrument.Instrumentation.class,String[].class,Module[].class,String.class);
        Method stop=group.getDeclaredMethod("stop",java.lang.instrument.Instrumentation.class,String[].class,Module[].class,String.class);
        prepare.setAccessible(true);stop.setAccessible(true);
        try{
            // Isolated metadata setup only; no live Agent/Core/Native control installation is granted.
            owner.set(null,agent);installed.set(null,api);metadataRows.put(target,Set.of(ids[0]));
            for(String mode:List.of("empty","invalid-id","uuid-fixed")){
                require("READY".equals(prepare.invoke(null,api,ids,modules,mode)),"prepare rejected valid selected mode");
                require(mode.equals(published.get(target))&&Arrays.asList((Module[])stopped.get(null)).contains(target),"stopped publication used stale return mode");
                Object policy=policyRows.get(target);UUID identity=identifiers.get(target);
                require("READY".equals(prepare.invoke(null,api,ids,modules,mode))&&policyRows.get(target)==policy,"repeated prepare changed policy generation");
                require(((String)stop.invoke(null,api,ids,modules,mode)).startsWith("TARGETS=")&&policyRows.get(target)==policy,"stop changed prepared policy generation");
                if(mode.equals("uuid-fixed"))require(identity!=null&&identity.equals(identifiers.get(target)),"fixed return UUID changed during handoff");
            }
            Object policy=policyRows.get(target),stoppedSnapshot=stopped.get(null);
            try{prepare.invoke(null,api,ids,modules,"bad-mode");throw new AssertionError("invalid return mode accepted");}
            catch(InvocationTargetException refused){require(refused.getCause() instanceof IllegalArgumentException,"wrong invalid-policy refusal");}
            require(policyRows.get(target)==policy&&stopped.get(null)==stoppedSnapshot,"invalid mode published a stop");
            require(!"READY".equals(prepare.invoke(null,api,new String[]{"missing_target"},modules,"empty"))&&policyRows.get(target)==policy,"invalid target changed policy");
        }finally{
            owner.set(null,oldOwner);installed.set(null,oldInstalled);stopped.set(null,oldStopped);
            if(oldMetadata==null)metadataRows.remove(target);else metadataRows.put(target,oldMetadata);
            if(oldPolicy==null)policyRows.remove(target);else policyRows.put(target,oldPolicy);
            if(oldMode==null)published.remove(target);else published.put(target,oldMode);
            if(oldUuid==null)identifiers.remove(target);else identifiers.put(target,oldUuid);
            if(!oldDenied)deniedRows.remove(ids[0]);if(!oldExact)exactRows.remove(target);
        }
        System.out.println("STOP_POLICY_PUBLICATION_PASS actual_prepare_and_publish selected_mode_before_cleanup repeated_fixed_UUID invalid_selection_unchanged isolated_metadata_no_Agent_install");
    }
    private static final class StopPolicyProbe {
        final Class<?> group;final Module target;final Method publisher;final Map<Module,String> modes;
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),callbackReturned=new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger(),active=new java.util.concurrent.atomic.AtomicInteger();
        final List<String> nativeModes=Collections.synchronizedList(new ArrayList<>());
        StopPolicyProbe(Class<?> group,Module target,Method publisher,Map<Module,String> modes){this.group=group;this.target=target;this.publisher=publisher;this.modes=modes;}
    }
    private static volatile StopPolicyProbe stopPolicyProbe;
    private static native void stopPolicySetup0(Class<?> control);
    private static native void stopPolicyCallback0(Module module);
    private static native void stopPolicyStop0(Class<?> control);
    /** Called through the real NativeControl.module JNI entry, with only its native body replaced by the fixture. */
    public static void stopPolicyNativeProbe(Module module,boolean stopped)throws Exception {
        StopPolicyProbe probe=Objects.requireNonNull(stopPolicyProbe);
        require(module==probe.target&&stopped,"native stop publication changed its exact target");
        require(!Thread.holdsLock(probe.group),"native stop publication still holds the group class monitor");
        int active=probe.active.incrementAndGet();
        try{
            require(active==1,"two publishers entered Native for the same module");
            synchronized(probe.modes){probe.nativeModes.add(probe.modes.get(module));}
            if(probe.calls.incrementAndGet()==1){probe.entered.countDown();require(probe.release.await(5,TimeUnit.SECONDS),"old native publication was not released");}
        }finally{probe.active.decrementAndGet();}
    }
    public static void stopPolicyNativeReenter(Module module)throws Exception {
        StopPolicyProbe probe=Objects.requireNonNull(stopPolicyProbe);require(module==probe.target,"native callback changed module");
        probe.publisher.invoke(null,module);
    }
    private static Object stopPolicyJavaReturn(Class<?> group,Module target,String descriptor)throws Exception {
        var node=new jdk.internal.org.objectweb.asm.tree.ClassNode();node.version=61;node.access=1;node.name="dev/ronova/pro/StopPolicyBody";node.superName="java/lang/Object";
        var method=new jdk.internal.org.objectweb.asm.tree.MethodNode(9,"value",descriptor,null,null);node.methods.add(method);
        Method suppress=group.getDeclaredMethod("suppressExternal",Module.class,jdk.internal.org.objectweb.asm.tree.MethodNode.class);suppress.setAccessible(true);suppress.invoke(null,target,method);
        var writer=new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] bytes=writer.toByteArray();
        Class<?> body=new ClassLoader(AuditFixCheck.class.getClassLoader()){Class<?> defineBody(){return defineClass(null,bytes,0,bytes.length);}}.defineBody();
        return body.getMethod("value").invoke(null);
    }
    @SuppressWarnings("unchecked")
    private static void stopPolicyConcurrency()throws Exception {
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),group=Class.forName("dev.ronova.pro.agent.ModGroupBoundary");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),definitions=Class.forName("dev.ronova.pro.bootstrap.DefinitionBridge",true,null),nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        // Initialize bookkeeping before enabling the isolated Native entry replacements.
        Class.forName("dev.ronova.pro.bootstrap.CodeSourceBridge",true,null);
        Field owner=controls.getDeclaredField("instrumentationOwner"),installed=agent.getDeclaredField("instrumentation"),stopped=controls.getDeclaredField("stoppedModules"),available=nativeControl.getDeclaredField("available");
        for(Field field:List.of(owner,installed,stopped,available))field.setAccessible(true);
        Object oldOwner=owner.get(null),oldInstalled=installed.get(null),oldStopped=stopped.get(null);boolean oldAvailable=available.getBoolean(null);
        require(!oldAvailable,"publication fixture requires an uninstalled native controller");
        Field metadata=group.getDeclaredField("metadata"),policies=group.getDeclaredField("returns"),completed=group.getDeclaredField("published"),active=group.getDeclaredField("publishing"),modes=definitions.getDeclaredField("RETURNS"),fixed=definitions.getDeclaredField("FIXED");
        for(Field field:List.of(metadata,policies,completed,active,modes,fixed))field.setAccessible(true);
        Module target=Class.forName("java.sql.Driver").getModule();String[] ids={"audit_policy"};Module[] modules={target};
        Map<Module,Object> metadataRows=(Map<Module,Object>)metadata.get(null),policyRows=(Map<Module,Object>)policies.get(null),completedRows=(Map<Module,Object>)completed.get(null),activeRows=(Map<Module,Object>)active.get(null);
        Map<Module,String> modeRows=(Map<Module,String>)modes.get(null);Map<Module,UUID> uuidRows=(Map<Module,UUID>)fixed.get(null);
        Object oldMetadata=metadataRows.get(target),oldPolicy=policyRows.get(target),oldCompleted=completedRows.get(target);String oldMode=modeRows.get(target);UUID oldUuid=uuidRows.get(target);
        require(!activeRows.containsKey(target),"publication fixture target already has a publisher");
        var api=(java.lang.instrument.Instrumentation)Proxy.newProxyInstance(AuditFixCheck.class.getClassLoader(),new Class<?>[]{java.lang.instrument.Instrumentation.class},(proxy,method,args)->{
            if(method.getName().equals("getAllLoadedClasses"))return new Class<?>[0];if(method.getReturnType()==boolean.class)return true;if(method.getReturnType()==long.class)return 0L;return null;
        });
        Method prepare=group.getDeclaredMethod("prepare",java.lang.instrument.Instrumentation.class,String[].class,Module[].class,String.class),publisher=agent.getDeclaredMethod("publishStoppedModule",Module.class);
        prepare.setAccessible(true);publisher.setAccessible(true);
        List<Thread> workers=new ArrayList<>();boolean nativeSetup=false;Object fixedGeneration=null;UUID fixedIdentity=null;
        try{
            owner.set(null,agent);installed.set(null,api);metadataRows.put(target,Set.of(ids[0]));stopPolicySetup0(nativeControl);nativeSetup=true;available.setBoolean(null,true);
            for(boolean replacePolicy:List.of(false,true)){
                StopPolicyProbe probe=new StopPolicyProbe(group,target,publisher,modeRows);stopPolicyProbe=probe;
                var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var firstResult=new java.util.concurrent.atomic.AtomicReference<Object>();var secondResult=new java.util.concurrent.atomic.AtomicReference<Object>();
                Thread first=new Thread(()->{try{firstResult.set(prepare.invoke(null,api,ids,modules,"uuid-fixed"));}catch(Throwable error){failure.compareAndSet(null,error);}},"audit-stop-old");
                Thread second=new Thread(()->{try{stopPolicyCallback0(target);probe.callbackReturned.countDown();if(replacePolicy)secondResult.set(prepare.invoke(null,api,ids,modules,"empty"));}catch(Throwable error){failure.compareAndSet(null,error);}},"audit-stop-new");
                first.setDaemon(true);second.setDaemon(true);workers.add(first);workers.add(second);
                first.start();require(probe.entered.await(5,TimeUnit.SECONDS),"old policy did not enter Native:"+failure.get());
                if(fixedGeneration!=null)require(policyRows.get(target)==fixedGeneration&&fixedIdentity.equals(uuidRows.get(target)),"same-mode prepare replaced its fixed UUID generation");
                second.start();require(probe.callbackReturned.await(5,TimeUnit.SECONDS),"native callback waited for the in-flight group publisher:"+failure.get());
                if(replacePolicy){
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(!"empty".equals(invoke(policyRows.get(target),"mode"))&&System.nanoTime()<deadline)Thread.sleep(1);
                    require("empty".equals(invoke(policyRows.get(target),"mode"))&&secondResult.get()==null,"new policy was not queued or returned READY before native completion");
                }
                probe.release.countDown();first.join(5000);second.join(5000);
                require(!first.isAlive()&&!second.isAlive()&&failure.get()==null,"publication threads failed or deadlocked:"+failure.get());
                require("READY".equals(firstResult.get())&&(!replacePolicy||"READY".equals(secondResult.get())),"completed policy drain did not return READY");
                require(probe.nativeModes.equals(List.of("uuid-fixed",replacePolicy?"empty":"uuid-fixed"))&&completedRows.get(target)==policyRows.get(target)&&!activeRows.containsKey(target),"callback/current policy was not drained in order");
                if(!replacePolicy){fixedGeneration=policyRows.get(target);fixedIdentity=uuidRows.get(target);}
            }
            Method nativeReturn=definitions.getMethod("nativeReturn",Module.class,Class.class,String.class);
            require(stopPolicyJavaReturn(group,target,"()[Ljava/lang/String;") instanceof String[] javaEmpty&&javaEmpty.length==0
                    &&nativeReturn.invoke(null,target,AuditFixCheck.class,"()[Ljava/lang/String;") instanceof String[] nativeEmpty&&nativeEmpty.length==0,"Java/native final empty policy differed");
            require("READY".equals(prepare.invoke(null,api,ids,modules,"uuid-fixed")),"fixed policy could not publish after drain");
            Object generation=policyRows.get(target);UUID identity=uuidRows.get(target);
            require("READY".equals(prepare.invoke(null,api,ids,modules,"uuid-fixed"))&&policyRows.get(target)==generation&&identity!=null&&identity.equals(uuidRows.get(target)),"same-mode prepare replaced the fixed UUID generation");
            require(identity.equals(stopPolicyJavaReturn(group,target,"()Ljava/util/UUID;"))&&identity.equals(nativeReturn.invoke(null,target,AuditFixCheck.class,"()Ljava/util/UUID;")),"Java/native fixed UUID differed after repeated handoff");
        }finally{
            StopPolicyProbe probe=stopPolicyProbe;if(probe!=null)probe.release.countDown();
            for(Thread worker:workers)if(worker.isAlive())worker.join(5000);
            require(workers.stream().noneMatch(Thread::isAlive),"publication fixture could not release its threads");
            available.setBoolean(null,false);if(nativeSetup)stopPolicyStop0(nativeControl);stopPolicyProbe=null;
            owner.set(null,oldOwner);installed.set(null,oldInstalled);stopped.set(null,oldStopped);available.setBoolean(null,oldAvailable);
            if(oldMetadata==null)metadataRows.remove(target);else metadataRows.put(target,oldMetadata);if(oldPolicy==null)policyRows.remove(target);else policyRows.put(target,oldPolicy);
            if(oldCompleted==null)completedRows.remove(target);else completedRows.put(target,oldCompleted);activeRows.remove(target);
            if(oldMode==null)modeRows.remove(target);else modeRows.put(target,oldMode);if(oldUuid==null)uuidRows.remove(target);else uuidRows.put(target,oldUuid);
        }
        System.out.println("STOP_POLICY_CONCURRENCY_PASS actual_NativeControl_JNI_entry no_group_monitor two_thread_native_callback_reentry same_generation_callback_drained latest_policy_drained Java_native_returns_match fixed_UUID_reused isolated_native_body_fixture_no_live_install");
    }
    private static volatile Object taskCaptureMonitor;
    private static volatile CountDownLatch taskCaptureEntered,taskCaptureProceed;
    private static volatile boolean taskCaptureThrow;
    public static Object[] taskCaptureProbe(Object task)throws InterruptedException {
        require(!Thread.holdsLock(taskCaptureMonitor),"task resolver still holds global owner monitor");
        if(taskCaptureThrow)throw new IllegalStateException("AUDIT_TASK_CAPTURE_FAILURE");
        taskCaptureEntered.countDown();require(taskCaptureProceed.await(5,TimeUnit.SECONDS),"capture fixture release missing");return null;
    }
    @SuppressWarnings("unchecked")
    private static void taskCaptureScope()throws Exception {
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null),owners=RecoveryTasks.class;
        Field registration=bridge.getDeclaredField("registration"),installer=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null).getDeclaredField("observerInstaller"),sinkField=owners.getDeclaredField("SINK"),listenersField=owners.getDeclaredField("LISTENERS");
        for(Field field:List.of(registration,installer,sinkField,listenersField))field.setAccessible(true);
        Consumer<Object[]> sink=(Consumer<Object[]>)sinkField.get(null);
        Class<?> registrationType=Class.forName("dev.ronova.pro.bootstrap.TaskBridge$Registration");
        Constructor<?> constructor=registrationType.getDeclaredConstructor(Consumer.class);constructor.setAccessible(true);
        Object current=constructor.newInstance(sink);Field epochField=registrationType.getDeclaredField("epoch");epochField.setAccessible(true);long epoch=epochField.getLong(current);
        Object oldRegistration=registration.get(null),oldInstaller=installer.get(null);
        Set<Object> listeners=(Set<Object>)listenersField.get(null);Set<Object> oldListeners=new HashSet<>(listeners);
        Map<Field,Object> previous=new LinkedHashMap<>();
        for(String name:List.of("publishedOwners","bridgeRegistration","bridgeRegistrationEpoch","unknownCaptureGap","capturePublications","delivery","captureState","acknowledgeCaptureLoss","observerMatches","inlineDetails","coverage")){
            Field field=owners.getDeclaredField(name);field.setAccessible(true);previous.put(field,field.get(null));
        }
        Method emit=bridge.getDeclaredMethod("emit",registrationType,int.class,Object.class,Object.class,Object.class,Object.class);emit.setAccessible(true);
        Method state=bridge.getMethod("captureState",Object.class),ack=bridge.getMethod("acknowledgeCaptureLoss",Object.class,Object.class,long.class,long.class);
        Method healthy=owners.getDeclaredMethod("checkBridge",Object.class);healthy.setAccessible(true);
        Field gap=owners.getDeclaredField("gap"),flights=owners.getDeclaredField("capturesInFlight");gap.setAccessible(true);flights.setAccessible(true);
        Class<?> publishedType=Class.forName("dev.ronova.pro.RecoveryTasks$OwnerRegistration");
        Constructor<?> publishedConstructor=publishedType.getDeclaredConstructor(owners,UUID.class,Object.class,long.class);publishedConstructor.setAccessible(true);
        Object[] selected=new Object[2],published=new Object[2];Object task=new Object(),other=new Object();UUID subject=UUID.randomUUID();
        for(int index=0;index<2;index++){
            ProRuntime runtime=(ProRuntime)U.allocateInstance(ProRuntime.class);UUID session=UUID.randomUUID();put(runtime,"session",session);put(runtime,"moduleSubjects",new IdentityHashMap<>());
            Object owner=U.allocateInstance(owners);selected[index]=owner;
            put(owner,"runtime",runtime);put(owner,"captureLock",new Object());put(owner,"captureGaps",new IdentityHashMap<>());
            put(owner,"origins",new IdentityHashMap<>());put(owner,"workerBindings",new IdentityHashMap<>());put(owner,"captureEpochs",new HashMap<>());
            put(owner,"changes",new java.util.concurrent.atomic.AtomicLong());put(owner,"events",new ConcurrentLinkedQueue<>());put(owner,"pendingCaptures",new ConcurrentLinkedQueue<>());put(owner,"pendingRoots",new IdentityHashMap<>());put(owner,"gap","");put(owner,"transientIssue","");
            published[index]=publishedConstructor.newInstance(owner,session,current,epoch);put(owner,"ownerRegistration",published[index]);
            Field origins=owners.getDeclaredField("origins");origins.setAccessible(true);((Map<Object,UUID>)origins.get(owner)).put(index==0?task:other,subject);
        }
        Thread worker=null;java.util.concurrent.atomic.AtomicReference<Throwable> workerFailure=new java.util.concurrent.atomic.AtomicReference<>();
        taskCaptureMonitor=listeners;taskCaptureEntered=new CountDownLatch(1);taskCaptureProceed=new CountDownLatch(1);taskCaptureThrow=false;
        try{
            registration.set(null,current);installer.set(null,AuditFixCheck.class);
            synchronized(listeners){listeners.clear();listeners.addAll(Arrays.asList(selected));}
            for(var entry:previous.entrySet())switch(entry.getKey().getName()){
                case "publishedOwners" -> {Object array=Array.newInstance(publishedType,2);Array.set(array,0,published[0]);Array.set(array,1,published[1]);entry.getKey().set(null,array);}
                case "bridgeRegistration" -> entry.getKey().set(null,current);
                case "bridgeRegistrationEpoch" -> entry.getKey().setLong(null,epoch);
                case "delivery" -> entry.getKey().set(null,bridge.getMethod("deliveringTo",Object.class));
                case "captureState" -> entry.getKey().set(null,state);
                case "acknowledgeCaptureLoss" -> entry.getKey().set(null,ack);
                case "observerMatches" -> entry.getKey().set(null,bridge.getMethod("observerMatches",Object.class));
                case "inlineDetails" -> entry.getKey().set(null,AuditFixCheck.class.getMethod("taskCaptureProbe",Object.class));
                case "coverage" -> entry.getKey().set(null,null);
            }
            worker=new Thread(()->{try{emit.invoke(null,current,24,task,subject,null,new Object[]{selected[0],new Object(),null});}catch(Throwable failure){workerFailure.set(failure);}},"audit-capture-worker");
            worker.start();require(taskCaptureEntered.await(5,TimeUnit.SECONDS),"actual capture probe was not reached");
            synchronized(listeners){require(flights.getInt(selected[0])==1&&flights.getInt(selected[1])==0,"capture reservation crossed owner scope");
                require(Boolean.FALSE.equals(healthy.invoke(selected[0],task)),"in-flight capture did not fence premature qualification");}
            taskCaptureProceed.countDown();worker.join(5000);require(!worker.isAlive()&&workerFailure.get()==null,"capture worker failed or hung");
            require(Boolean.TRUE.equals(healthy.invoke(selected[1],other)),"completed publication left unrelated owner fenced");
            taskCaptureThrow=true;
            emit.invoke(null,current,24,task,subject,null,new Object[]{selected[0],new Object(),null});
            require(Boolean.FALSE.equals(healthy.invoke(selected[0],task))&&Boolean.TRUE.equals(healthy.invoke(selected[1],other))
                    &&"".equals(gap.get(selected[1])),"known owner capture failure became global");
            Consumer<Object[]> throwing=row->{throw new IllegalStateException("AUDIT_SINK_FAILURE");};Object failed=constructor.newInstance(throwing);registration.set(null,failed);
            long unknownBefore=(Long)((Object[])state.invoke(null,throwing))[2];emit.invoke(null,failed,3,task,null,null,null);
            Object[] lossState=(Object[])state.invoke(null,throwing);Object[][] losses=(Object[][])lossState[4];
            require(losses.length==1&&losses[0][1]==task&&losses[0][8]==failed&&(Long)lossState[2]==unknownBefore,"sink failure lost exact task/registration scope");
            require(Boolean.TRUE.equals(ack.invoke(null,throwing,failed,lossState[1],losses[0][11])),"known failed delivery could not retire its metadata");
            registration.set(null,current);
            require(Boolean.TRUE.equals(healthy.invoke(selected[1],other)),"diagnostic dropped count poisoned unrelated task");
            Method lost=bridge.getDeclaredMethod("lost");lost.setAccessible(true);lost.invoke(null);
            require(Boolean.FALSE.equals(healthy.invoke(selected[0],task))&&Boolean.FALSE.equals(healthy.invoke(selected[1],other)),"unidentified loss was forgiven");
        }finally{
            taskCaptureProceed.countDown();if(worker!=null)worker.join(5000);
            for(var entry:previous.entrySet())entry.getKey().set(null,entry.getValue());
            synchronized(listeners){listeners.clear();listeners.addAll(oldListeners);}registration.set(null,oldRegistration);installer.set(null,oldInstaller);
            taskCaptureMonitor=null;taskCaptureThrow=false;
        }
        System.out.println("TASK_CAPTURE_SCOPE_PASS actual_receive_resolver_outside_global_monitor shared_inflight_fence_and_scoped_failure unrelated_owner_allowed_after_publication sink_loss_retained unknown_loss_still_refused isolated_metadata_no_Agent_install");
    }
    private static final class TaskCaptureHolder implements Runnable,java.util.function.Function<Object,Object> {
        Object captured;
        TaskCaptureHolder(Object captured){this.captured=captured;}
        @Override public void run(){throw new AssertionError("capture executed the callback");}
        @Override public Object apply(Object input){throw new AssertionError("capture executed the callback");}
    }
    @SuppressWarnings("unchecked")
    private static void taskLazyCapture()throws Exception {
        Class<?> owners=RecoveryTasks.class,bridge=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null);
        Class<?> registrationType=Class.forName("dev.ronova.pro.bootstrap.TaskBridge$Registration",true,null);
        Field registration=bridge.getDeclaredField("registration"),installer=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null).getDeclaredField("observerInstaller"),sinkField=owners.getDeclaredField("SINK"),listenersField=owners.getDeclaredField("LISTENERS");
        for(Field field:List.of(registration,installer,sinkField,listenersField))field.setAccessible(true);
        Consumer<Object[]> sink=(Consumer<Object[]>)sinkField.get(null);
        Constructor<?> registrationConstructor=registrationType.getDeclaredConstructor(Consumer.class);registrationConstructor.setAccessible(true);
        Object current=registrationConstructor.newInstance(sink),oldRegistration=registration.get(null),oldInstaller=installer.get(null);
        Field registrationEpoch=registrationType.getDeclaredField("epoch");registrationEpoch.setAccessible(true);long epoch=registrationEpoch.getLong(current);
        Set<Object> listeners=(Set<Object>)listenersField.get(null);Set<Object> oldListeners=new HashSet<>(listeners);
        Map<Field,Object> previous=new LinkedHashMap<>();
        for(String name:List.of("publishedOwners","bridgeRegistration","bridgeRegistrationEpoch","unknownCaptureGap","capturePublications","delivery","captureState","acknowledgeCaptureLoss","observerMatches","inlineDetails","coverage")) {
            Field field=owners.getDeclaredField(name);field.setAccessible(true);previous.put(field,field.get(null));
        }
        Field unknownField=bridge.getDeclaredField("UNKNOWN_LOSS"),droppedField=bridge.getDeclaredField("DROPPED"),lossSite=bridge.getDeclaredField("firstLossSite");
        for(Field field:List.of(unknownField,droppedField,lossSite))field.setAccessible(true);
        var unknown=(java.util.concurrent.atomic.AtomicLong)unknownField.get(null);var dropped=(java.util.concurrent.atomic.AtomicLong)droppedField.get(null);
        long priorUnknown=unknown.get(),priorDropped=dropped.get();Object priorLossSite=lossSite.get(null);
        require(priorUnknown==0,"lazy capture fixture requires an isolated observer with no unidentified loss");
        Method emit=bridge.getDeclaredMethod("emit",registrationType,int.class,Object.class,Object.class,Object.class,Object.class);emit.setAccessible(true);
        Method state=bridge.getMethod("captureState",Object.class),resolve=owners.getDeclaredMethod("resolveCaptures",int.class),healthy=owners.getDeclaredMethod("checkBridge",Object.class),routeLosses=owners.getDeclaredMethod("routeCaptureLosses");
        Method producer=owners.getDeclaredMethod("producerSubjects",Object.class,Object[].class,Object[].class,Set.class);
        for(Method method:List.of(resolve,healthy,routeLosses,producer))method.setAccessible(true);
        Field gap=owners.getDeclaredField("gap");gap.setAccessible(true);
        Class<?> publishedType=Class.forName("dev.ronova.pro.RecoveryTasks$OwnerRegistration");
        Constructor<?> publishedConstructor=publishedType.getDeclaredConstructor(owners,UUID.class,Object.class,long.class);publishedConstructor.setAccessible(true);
        Object[] selected=new Object[2],published=new Object[2];UUID sourceSubject=UUID.randomUUID();Object source=new Object(),other=new Object();
        Map<Object,Set<UUID>>[] bindings=new Map[2];ConcurrentLinkedQueue<Object>[] events=new ConcurrentLinkedQueue[2];
        ConcurrentLinkedQueue<Object>[] pending=new ConcurrentLinkedQueue[2];Map<Object,Integer>[] roots=new Map[2];
        for(int index=0;index<2;index++) {
            ProRuntime runtime=(ProRuntime)U.allocateInstance(ProRuntime.class);UUID session=UUID.randomUUID();put(runtime,"session",session);put(runtime,"moduleSubjects",new IdentityHashMap<>());
            Object owner=U.allocateInstance(owners);selected[index]=owner;
            put(owner,"runtime",runtime);put(owner,"captureLock",new Object());put(owner,"captureGaps",new IdentityHashMap<>());
            Map<Object,UUID> origins=new IdentityHashMap<>();origins.put(index==0?source:other,sourceSubject);put(owner,"origins",origins);
            bindings[index]=new IdentityHashMap<>();events[index]=new ConcurrentLinkedQueue<>();pending[index]=new ConcurrentLinkedQueue<>();roots[index]=new IdentityHashMap<>();
            put(owner,"workerBindings",bindings[index]);put(owner,"events",events[index]);put(owner,"pendingCaptures",pending[index]);put(owner,"pendingRoots",roots[index]);
            put(owner,"captureEpochs",new HashMap<>());put(owner,"changes",new java.util.concurrent.atomic.AtomicLong());put(owner,"gap","");put(owner,"transientIssue","");
            published[index]=publishedConstructor.newInstance(owner,session,current,epoch);put(owner,"ownerRegistration",published[index]);
        }
        try {
            registration.set(null,current);installer.set(null,owners);
            synchronized(listeners){listeners.clear();listeners.addAll(Arrays.asList(selected));}
            for(var entry:previous.entrySet())switch(entry.getKey().getName()) {
                case "publishedOwners" -> {Object array=Array.newInstance(publishedType,2);Array.set(array,0,published[0]);Array.set(array,1,published[1]);entry.getKey().set(null,array);}
                case "bridgeRegistration" -> entry.getKey().set(null,current);
                case "bridgeRegistrationEpoch" -> entry.getKey().setLong(null,epoch);
                case "capturePublications" -> entry.getKey().setInt(null,0);
                case "unknownCaptureGap" -> entry.getKey().set(null,"");
                case "delivery" -> entry.getKey().set(null,bridge.getMethod("deliveringTo",Object.class));
                case "captureState" -> entry.getKey().set(null,state);
                case "acknowledgeCaptureLoss" -> entry.getKey().set(null,bridge.getMethod("acknowledgeCaptureLoss",Object.class,Object.class,long.class,long.class));
                case "observerMatches" -> entry.getKey().set(null,bridge.getMethod("observerMatches",Object.class));
                case "inlineDetails" -> entry.getKey().set(null,bridge.getMethod("inlineDetails",Object.class));
                case "coverage" -> entry.getKey().set(null,null);
            }
            TaskCaptureHolder holder=new TaskCaptureHolder(source);
            emit.invoke(null,current,1,holder,null,null,new Object());
            Object consumed=holder.captured;holder.captured=null;
            require(consumed==source&&bindings[0].isEmpty()&&events[0].isEmpty()&&pending[0].size()==1,"ordinary capture resolved a provenance graph before lazy resolution");
            require(Boolean.FALSE.equals(healthy.invoke(selected[0],holder))&&Boolean.FALSE.equals(healthy.invoke(selected[1],other)),"unknown callback graph qualified a source before its owner's bounded resolution");
            Set<UUID> constructorSubjects=new HashSet<>();
            require(Boolean.TRUE.equals(producer.invoke(selected[0],holder,null,new Object[0],constructorSubjects))&&constructorSubjects.equals(Set.of(sourceSubject))
                    &&bindings[0].isEmpty()&&events[0].isEmpty(),"constructor/publication enforcement lost ENTER source after the callback moved it to a local and cleared its field");
            resolve.invoke(selected[0],1);
            require(Set.of(sourceSubject).equals(bindings[0].get(holder))&&events[0].size()==1,"real lazy resolver did not discover the captured source field");
            require(Boolean.FALSE.equals(healthy.invoke(selected[0],holder))&&Boolean.FALSE.equals(healthy.invoke(selected[1],other)),"one runtime qualified a shared root, or an unresolved owner qualified an unknown source");
            resolve.invoke(selected[1],1);
            require(Boolean.TRUE.equals(healthy.invoke(selected[0],holder))&&Boolean.TRUE.equals(healthy.invoke(selected[1],other))&&roots[0].isEmpty()&&roots[1].isEmpty(),"resolved unrelated observer retained a shared-root fence");
            events[0].clear();events[1].clear();
            emit.invoke(null,current,24,holder,sourceSubject,null,new Object[]{selected[0],new Object(),null});
            require(Boolean.FALSE.equals(healthy.invoke(selected[0],holder))&&Boolean.TRUE.equals(healthy.invoke(selected[1],other)),"owner-targeted source capture poisoned a runtime with no unresolved receipt");
            resolve.invoke(selected[0],1);events[0].clear();

            Field callable=FutureTask.class.getDeclaredField("callable");callable.setAccessible(true);
            TaskCaptureHolder futureCallback=new TaskCaptureHolder(source);FutureTask<Object> future=new FutureTask<>(futureCallback,null);
            emit.invoke(null,current,3,future,null,null,null);futureCallback.captured=null;callable.set(future,null);
            resolve.invoke(selected[0],1);resolve.invoke(selected[1],1);
            require(Set.of(sourceSubject).equals(bindings[0].get(future))&&events[0].size()==1,"cleared FutureTask callable lost the real callback source");
            events[0].clear();events[1].clear();

            Class<?> completionType=Class.forName("java.util.concurrent.CompletableFuture$UniApply",true,null),uniType=completionType.getSuperclass();
            Constructor<?> completionConstructor=completionType.getDeclaredConstructor(Executor.class,CompletableFuture.class,CompletableFuture.class,java.util.function.Function.class);completionConstructor.setAccessible(true);
            CompletableFuture<Object> completionInput=new CompletableFuture<>(),completionOutput=new CompletableFuture<>();
            TaskCaptureHolder completionCallback=new TaskCaptureHolder(source);Object completion=completionConstructor.newInstance(null,completionOutput,completionInput,completionCallback);
            emit.invoke(null,current,11,completion,null,null,null);completionCallback.captured=null;
            for(Field field:List.of(completionType.getDeclaredField("fn"),uniType.getDeclaredField("src"),uniType.getDeclaredField("dep"))) {field.setAccessible(true);field.set(completion,null);}
            resolve.invoke(selected[0],1);resolve.invoke(selected[1],1);Object completionEvent=events[0].poll();
            require(Set.of(sourceSubject).equals(bindings[0].get(completion))&&completionEvent!=null&&invoke(completionEvent,"dependency")==completionInput&&invoke(completionEvent,"output")==completionOutput,
                    "cleared CompletableFuture callback/dependency/output operands lost their actual lineage");
            events[1].clear();

            Class<?> inlineType=Class.forName("dev.ronova.pro.bootstrap.TaskBridge$Inline",true,null);
            Constructor<?> inlineConstructor=inlineType.getDeclaredConstructor(Object.class,Object.class,Object.class);inlineConstructor.setAccessible(true);
            CompletableFuture<Object> inlineInput=new CompletableFuture<>(),inlineOther=new CompletableFuture<>(),inlineOutput=new CompletableFuture<>();
            TaskCaptureHolder inlineCallback=new TaskCaptureHolder(source);Object inline=inlineConstructor.newInstance(inlineInput,inlineOther,inlineCallback),invocation=new Object();
            emit.invoke(null,current,1,inline,null,null,invocation);inlineCallback.captured=null;put(inline,"output",inlineOutput);emit.invoke(null,current,2,inline,null,null,invocation);
            for(String name:List.of("source","other","callback","output","frame","thread"))put(inline,name,null);put(inline,"closed",true);
            resolve.invoke(selected[0],2);resolve.invoke(selected[1],2);Object entered=events[0].poll(),exited=events[0].poll();
            require(Set.of(sourceSubject).equals(bindings[0].get(inline))&&entered!=null&&exited!=null&&(Integer)invoke(entered,"kind")==1&&(Integer)invoke(exited,"kind")==2
                    &&invoke(entered,"dependency")==inlineInput&&invoke(entered,"otherDependency")==inlineOther&&invoke(entered,"output")==null
                    &&invoke(exited,"output")==inlineOutput&&invoke(exited,"callbackDefinition")==inlineCallback.getClass(),"closed Inline token lost transient operands or actual ENTER/EXIT ordering");
            events[1].clear();

            TaskCaptureHolder lostCallback=new TaskCaptureHolder(source);FutureTask<Object> lostFuture=new FutureTask<>(lostCallback,null);
            emit.invoke(null,current,3,lostFuture,null,null,null);lostCallback.captured=null;callable.set(lostFuture,null);
            Method lost=bridge.getDeclaredMethod("lost",registrationType,Object[].class,Throwable.class);lost.setAccessible(true);
            Object[] lostRow={3,lostFuture,null,null,null,Thread.currentThread(),System.nanoTime(),dropped.get(),current,epoch,unknown.get()};
            lost.invoke(null,current,lostRow,new IllegalStateException("AUDIT_KNOWN_TASK_LOSS"));
            require(((Object[][])((Object[])state.invoke(null,sink))[4]).length==1&&unknown.get()==priorUnknown,"known failed delivery became unidentified before routing");
            routeLosses.invoke(null);
            resolve.invoke(selected[1],1);
            require(((Object[][])((Object[])state.invoke(null,sink))[4]).length==0&&Boolean.FALSE.equals(healthy.invoke(selected[0],lostFuture))
                    &&Boolean.TRUE.equals(healthy.invoke(selected[1],other))&&"".equals(gap.get(selected[1]))&&unknown.get()==priorUnknown,
                    "known loss forgot queued callback operands, failed ACK, or poisoned an unrelated runtime");
            resolve.invoke(selected[0],1);
            Method unidentified=bridge.getDeclaredMethod("lost");unidentified.setAccessible(true);unidentified.invoke(null);
            require(unknown.get()==priorUnknown+1&&Boolean.FALSE.equals(healthy.invoke(selected[0],holder))&&Boolean.FALSE.equals(healthy.invoke(selected[1],other)),"unidentified loss was forgiven by lazy routing");
        } finally {
            for(var entry:previous.entrySet())entry.getKey().set(null,entry.getValue());
            synchronized(listeners){listeners.clear();listeners.addAll(oldListeners);}registration.set(null,oldRegistration);installer.set(null,oldInstaller);
            unknown.set(priorUnknown);dropped.set(priorDropped);lossSite.set(null,priorLossSite);
        }
        System.out.println("TASK_LAZY_CAPTURE_PASS actual_receive_and_bounded_resolver provenance_graph_deferred shallow_callback_operands_retained FutureTask_CF_Inline_operands_survive_clear ENTER_EXIT_order constructor_source_before_effect_after_field_clear shared_pending_root_fence unrelated_owner_allowed known_loss_scoped_and_acknowledged unidentified_loss_refused isolated_metadata_no_Agent_install");
    }
    private static void containerScopeRemoval()throws Exception {
        Object target=new Object();int[] actions={0};Runnable action=()->actions[0]++;
        Map<Object,Object> plain=new HashMap<>();plain.put("key",target);
        Map<Object,Object> concurrent=new ConcurrentHashMap<>(plain);
        for(Map<Object,Object> map:List.of(plain,concurrent)){
            require(!SourceMaps.with(map,action::run)&&SourceMaps.entries(map)==null&&SourceMaps.peek(map,"key")==null
                    &&SourceMaps.revision(map)<0,"removed map scope still advertised: "+map.getClass());
            require(!SourceMaps.visit(map,new SourceMaps.Cursor(map),8,(key,value,node,cursor)->{actions[0]++;return true;}),"removed map visitor ran");
            require(map.get("key")==target,"unobserved map was mutated");
        }
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.SourceMapBridge",true,null);
        for(List<Object> list:List.of(new ArrayList<>(List.of(target)),new CopyOnWriteArrayList<>(List.of(target)))){
            for(String method:List.of("withList","withListRead"))require(Boolean.FALSE.equals(bridge.getMethod(method,Object.class,Runnable.class).invoke(null,list,action)),"removed list action ran");
            try{SourceMaps.removeListIdentity(list,target);throw new AssertionError("unobserved list removal ran");}
            catch(IllegalStateException expected){require("JDK_CONTAINER_HOOKS_REMOVED".equals(expected.getMessage()),"wrong missing-scope reason");}
            require(list.size()==1&&list.get(0)==target,"removed list alias was changed");
        }
        Class<?> edgeType=Class.forName("dev.ronova.pro.RecoveryRecords$Edge");
        Method advance=RecoveryRecords.class.getDeclaredMethod("advance",edgeType);advance.setAccessible(true);
        Method absent=RecoveryRecords.class.getDeclaredMethod("absent",edgeType,Object.class,Object.class);absent.setAccessible(true);
        Object records=U.allocateInstance(RecoveryRecords.class);
        Object[][] retired={{"ATOMIC",new java.util.concurrent.atomic.AtomicReference<>()},
                {"COPY_ON_WRITE_LIST",new CopyOnWriteArrayList<>()},{"ARRAY_LIST",new ArrayList<>()},{"ENTITY_MAP",concurrent}};
        for(Object[] row:retired){
            Object edge=U.allocateInstance(edgeType);put(edge,"kind",row[0]);put(edge,"container",row[1]);
            advance.invoke(records,edge);
            require(Boolean.FALSE.equals(absent.invoke(records,edge,row[1],target)),"unobserved absence became a fact");
            Field reason=edgeType.getDeclaredField("reason"),fact=edgeType.getDeclaredField("fact"),released=edgeType.getDeclaredField("released");
            reason.setAccessible(true);fact.setAccessible(true);released.setAccessible(true);
            require("JDK_CONTAINER_HOOKS_REMOVED".equals(reason.get(edge))&&fact.get(edge)==null&&!released.getBoolean(edge),"removed scope settled an edge");
        }
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.BackingBoundary");
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        for(Class<?> type:List.of(HashMap.class,LinkedHashMap.class,ArrayList.class,IdentityHashMap.class,AbstractCollection.class,ConcurrentHashMap.class,
                java.util.concurrent.atomic.AtomicReference.class,CopyOnWriteArrayList.class)){
            String name=type.getName().replace('.','/');byte[] bytes;
            try(var input=type.getResourceAsStream("/"+name+".class")){bytes=Objects.requireNonNull(input).readAllBytes();}
            require(transform.invoke(null,null,name,bytes)==null,"collection body still transformed: "+name);
        }
        require(actions[0]==0,"removed scope invoked an action");
        System.out.println("CONTAINER_SCOPE_REMOVAL_PASS actual_JDK_bodies_unmodified_by_BackingBoundary removed_map_list_actions_refused old_edge_absence_not_settled no_Core_or_Agent_install");
    }
    private static void sourceListFixes()throws Exception {
        Class<?> publications=Class.forName("dev.ronova.pro.SourceMaps$Publications");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null);
        for(String name:List.of("OBSERVE","CARRIER","HOLD","CHANGED")){
            Field field=publications.getDeclaredField(name);field.setAccessible(true);
            require(((Method)field.get(null)).getDeclaringClass()==controls,"source method resolved to wrong bridge: "+name);
        }
        net.minecraft.nbt.CompoundTag target=new net.minecraft.nbt.CompoundTag();target.putString("marker","equal-value");
        net.minecraft.nbt.CompoundTag neighbor=target.copy();
        require(target!=neighbor&&target.equals(neighbor),"fixture must distinguish identity from equal NBT");
        Method image=RecoveryRecords.class.getDeclaredMethod("listImage",Object.class,Object.class);image.setAccessible(true);
        List<List<net.minecraft.nbt.CompoundTag>> carriers=List.of(
                new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(target,neighbor,target)));
        for(List<net.minecraft.nbt.CompoundTag> carrier:carriers){
            String projected=(String)image.invoke(null,carrier,target);
            require(!projected.isEmpty(),"NBT projection unavailable: "+carrier.getClass().getName());
            require(SourceMaps.removeListIdentity(carrier,target),"duplicate removal not applied: "+carrier.getClass().getName());
            require(carrier.size()==1&&carrier.get(0)==neighbor,"duplicate target retained or equal neighbor damaged: "+carrier.getClass().getName());
            require(projected.equals(image.invoke(null,carrier,null)),"projection does not match all removed aliases: "+carrier.getClass().getName());
            require(!SourceMaps.removeListIdentity(carrier,target)&&carrier.get(0)==neighbor,"absent target changed neighbor: "+carrier.getClass().getName());
        }
        System.out.println("SOURCE_LIST_FIXES_PASS bridge_owner fastutil duplicate_identity preserved_equal_neighbor exact_nbt_projection JDK_container_scopes_removed");
    }
    private static void liveControlWriter()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Method writer=bridge.getDeclaredMethod("controlCaller");writer.setAccessible(true);
        require(Boolean.FALSE.equals(writer.invoke(null)),"foreign system-loader caller gained controller identity");
        Field registered=bridge.getDeclaredField("protectedTransformers");registered.setAccessible(true);
        Object[] actual=(Object[])registered.get(null);require(actual.length>0,"actual installed transformer array absent");
        Method mutation=bridge.getDeclaredMethod("controlMutationAllowed",Object.class);mutation.setAccessible(true);
        require(Boolean.FALSE.equals(mutation.invoke(null,(Object)actual)),"foreign control array mutation admitted");
        Object original=actual[0];
        try{Array.set(actual,0,null);}catch(SecurityException refused){ }
        require(actual[0]==original,"actual protected transformer array changed");
        Method raw=bridge.getDeclaredMethod("rawMemoryCaller");raw.setAccessible(true);
        require(Boolean.FALSE.equals(raw.invoke(null)),"foreign raw memory caller admitted");
        Method nativeRead=nativeControl.getDeclaredMethod("memoryReadSources0",long.class);nativeRead.setAccessible(true);
        try{nativeRead.invoke(null,0L);throw new AssertionError("foreign private native memory read admitted");}
        catch(InvocationTargetException refused){require(refused.getCause() instanceof IllegalStateException&&"ACTUAL_NATIVE_MEMORY_READER_REQUIRED".equals(refused.getCause().getMessage()),"native raw read was not refused at caller authority: "+refused.getCause());}
        long address=U.allocateMemory(8);
        try{
            U.putLong(address,0x1020304050607080L);byte[] bytes=new byte[8];long base=U.arrayBaseOffset(byte[].class);
            U.copyMemory(null,address,bytes,base,8);
            require(U.getLong(bytes,base)==0x1020304050607080L,"actual Unsafe raw-to-heap copy changed bytes");
        }finally{U.freeMemory(address);}
        System.out.println("LIVE_CONTROL_WRITER_PASS actual_native foreign_caller_refused protected_array_preserved foreign_raw_reader_refused actual_unsafe_raw_copy");
    }
    private static void systemArraycopy()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Field registered=bridge.getDeclaredField("protectedTransformers");registered.setAccessible(true);
        Object[] actual=(Object[])registered.get(null);
        require(actual.length>0&&actual[0]!=null,"actual installed transformer array absent");
        Object[] before=actual.clone();
        Method reflected=System.class.getMethod("arraycopy",Object.class,int.class,Object.class,int.class,int.class);
        MethodHandle direct=MethodHandles.publicLookup().findStatic(System.class,"arraycopy",
                MethodType.methodType(void.class,Object.class,int.class,Object.class,int.class,int.class));
        for(MethodHandle entry:new MethodHandle[]{null,direct}){
            String path=entry==null?"Method.invoke":"MethodHandle.invokeExact";
            arraycopyControlRefused(reflected,entry,actual,before,path);
            int[] source={11,23,37,49},target={101,102,103,104,105};
            arraycopyEntry(reflected,entry,source,1,target,2,2);
            require(Arrays.equals(target,new int[]{101,102,23,37,105}),path+" ordinary primitive copy changed bytes or bounds");
            Object first=new Object(),second=new Object(),neighbor=new Object();Object[] objects={neighbor,neighbor,neighbor};
            arraycopyEntry(reflected,entry,new Object[]{first,second},0,objects,1,2);
            require(objects[0]==neighbor&&objects[1]==first&&objects[2]==second,path+" ordinary reference copy changed identities");
            int[] right={1,2,3,4,5};arraycopyEntry(reflected,entry,right,0,right,1,4);
            require(Arrays.equals(right,new int[]{1,1,2,3,4}),path+" right overlap lost original source values");
            int[] left={1,2,3,4,5};arraycopyEntry(reflected,entry,left,1,left,0,4);
            require(Arrays.equals(left,new int[]{2,3,4,5,5}),path+" left overlap lost original source values");
            arraycopyEntry(reflected,entry,source,source.length,target,target.length,0);
            require(Arrays.equals(target,new int[]{101,102,23,37,105}),path+" zero-length copy changed destination");
            arraycopyFailure(reflected,entry,NullPointerException.class,null,0,target,0,0,path+" null source");
            arraycopyFailure(reflected,entry,NullPointerException.class,source,0,null,0,0,path+" null destination");
            arraycopyFailure(reflected,entry,ArrayIndexOutOfBoundsException.class,source,-1,target,0,1,path+" invalid range");
            require(Arrays.equals(target,new int[]{101,102,23,37,105}),path+" rejected parameters changed destination");
            long[] wrongPrimitive={77};
            arraycopyFailure(reflected,entry,ArrayStoreException.class,source,0,wrongPrimitive,0,1,path+" primitive type mismatch");
            require(wrongPrimitive[0]==77,path+" primitive type mismatch changed destination");
            String[] partial={"before-0","before-1","before-2"};
            arraycopyFailure(reflected,entry,ArrayStoreException.class,new Object[]{"copied",Integer.valueOf(7),"not-copied"},0,partial,0,3,path+" reference type mismatch");
            require(Arrays.equals(partial,new String[]{"copied","before-1","before-2"}),path+" reference mismatch lost original compatible-prefix behavior");
            arraycopyEntry(reflected,entry,new String[]{"following"},0,partial,1,1);
            require(Arrays.equals(partial,new String[]{"copied","following","before-2"}),path+" failed copy retained a destination mutation scope");
            int[] following={0,0};arraycopyEntry(reflected,entry,new int[]{17,29},0,following,0,2);
            require(Arrays.equals(following,new int[]{17,29}),path+" failed copy blocked a following ordinary copy");
            arraycopyControlRefused(reflected,entry,actual,before,path+" after failures");
        }
        System.out.println("SYSTEM_ARRAYCOPY_PASS actual_native_Method_invoke_MethodHandle_invokeExact protected_transformer_array_refused ordinary_primitive_reference_copy overlap_both_directions zero_length original_null_range_type_exceptions reference_compatible_prefix following_copy_and_control_denial_preserved no_fabricated_authority");
    }
    private static void arraycopyEntry(Method reflected,MethodHandle entry,Object source,int from,Object target,int to,int count)throws Exception {
        try{
            if(entry==null)reflected.invoke(null,source,from,target,to,count);
            else entry.invokeExact(source,from,target,to,count);
        }catch(Throwable failure){
            if(failure instanceof InvocationTargetException invocation)failure=invocation.getCause();
            if(failure instanceof Exception exception)throw exception;
            if(failure instanceof Error error)throw error;
            throw new AssertionError("actual System.arraycopy invocation failed",failure);
        }
    }
    private static void arraycopyControlRefused(Method reflected,MethodHandle entry,Object[] actual,Object[] before,String path)throws Exception {
        try{arraycopyEntry(reflected,entry,new Object[]{null},0,actual,0,1);}
        catch(SecurityException|IllegalStateException refused){ }
        require(actual.length==before.length,path+" actual transformer array length changed");
        for(int index=0;index<before.length;index++)require(actual[index]==before[index],path+" changed protected transformer identity at "+index);
    }
    private static void arraycopyFailure(Method reflected,MethodHandle entry,Class<? extends RuntimeException> expected,
            Object source,int from,Object target,int to,int count,String path)throws Exception {
        try{arraycopyEntry(reflected,entry,source,from,target,to,count);}
        catch(RuntimeException failure){require(expected.isInstance(failure),path+" threw "+failure.getClass().getName()+" instead of "+expected.getName());return;}
        throw new AssertionError(path+" returned without the original exception");
    }
    private static boolean opaqueNativeFixture;
    private static Object opaqueNativeAction,opaqueNativeSeen;
    private static Module[] opaqueNativeModules;
    private static long opaqueNativeToken,opaqueNativeGeneration;
    private static int opaqueNativeObserverMode;
    private static final RuntimeException OPAQUE_NATIVE_FAILURE=new RuntimeException("opaque-native-original");
    private static native boolean opaqueNativeState0(Object action,Module[] modules,Throwable original);
    private static native boolean privateNativeState0(Module[] modules);
    private static native boolean hostStaticTlsArena0();
    private static native boolean hostObjectState0();
    private static void privateNativeStateSeams(boolean includeTls){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        opaqueNativeModules=new Module[]{AuditFixCheck.class.getModule()};opaqueNativeFixture=true;
        try{
            if(includeTls){
                require(hostStaticTlsArena0(),"actual compiler TLS and arena tracker completion seam failed");
                System.out.println("PRIVATE_STATIC_TLS_ARENA_SEAMS_PASS actual_PE_template_and_current_TEB_instance_distinct independent_actual_OS_thread_TLS peer_alias_blocks_whole_image_prepare seal_cancel peer_OS_signal_retires_only_peer arena_tracker_supplied_completion_seam live_alias_blocks_reset failed_reset_preserves_epoch successful_reset_new_epoch stale_alias_pair_refused no_real_arena_control_claim no_image_unmap_claim fixture_owned_sources_no_global_install");
            }
            require(hostObjectState0(),"isolated native object SDK lifecycle seam failed");
            System.out.println("PRIVATE_OBJECT_STATE_SEAM_PASS fixture_owned_allocation production_SDK_write_borrow_destructor_gates no_game_discovery_claim");
        }
        finally{opaqueNativeFixture=false;opaqueNativeModules=null;}
    }
    private static void privateNativeState(){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        opaqueNativeModules=new Module[]{AuditFixCheck.class.getModule()};opaqueNativeFixture=true;
        try{
            require(privateNativeState0(opaqueNativeModules),"actual private native lifetime regression failed");
            System.out.println("PRIVATE_NATIVE_STATE_PASS actual_heap_returns failed_realloc_preserved same_address_new_generation read_source_known_and_unknown live_alias_and_read_lease_block_free actual_private_HeapFree writable_PE_static_spans IMAGE_seal_cancel_retire scoped_native_CPU_unknown fixture_metadata_no_global_install");
        }
        finally{opaqueNativeFixture=false;opaqueNativeModules=null;}
    }
    private static void bootstrapSearch(String original)throws Exception{
        Class<?> task=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null);
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent",false,ClassLoader.getSystemClassLoader());
        require(task.getClassLoader()==null,"TaskBridge was not loaded by Bootstrap");
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"Native handoff did not use the selected payload");
        require("INSTALLED_FULL".equals(agent.getMethod("installState").invoke(null)),"actual agent installation incomplete");
        String entry="dev/ronova/pro/bootstrap/TaskBridge.class";byte[] expected,actual;
        try(var jar=new java.util.jar.JarFile(original);var input=jar.getInputStream(jar.getJarEntry(entry))){expected=input.readAllBytes();}
        Field held=agent.getDeclaredField("bootstrap");held.setAccessible(true);
        var selected=(java.util.jar.JarFile)held.get(null);
        require(selected!=null,"actual appended JarFile is not retained");
        try(var input=selected.getInputStream(selected.getJarEntry(entry))){actual=input.readAllBytes();}
        require(Arrays.equals(expected,actual),"appended Bootstrap payload differs from the requested Unicode-path payload");
        System.out.println("BOOTSTRAP_SEARCH_PASS actual_boot_loader original_payload_match native_available installed_full original="+original);
    }
    private static final class MirrorWriteTarget { static int number=7; static Object reference=new Object(); int field; }
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    private @interface CacheMarker { int value(); }
    @CacheMarker(37) private static final class AnnotationTarget {}
    private static Field platformField(Class<?> owner,String name)throws Exception{
        return (Field)Class.forName("dev.ronova.pro.bootstrap.PlatformMetadata",true,null)
                .getMethod("declaredField",Class.class,String.class).invoke(null,owner,name);
    }
    private static java.lang.instrument.Instrumentation actualInstrumentation()throws Exception{
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent");
        Field held=agent.getDeclaredField("instrumentation");held.setAccessible(true);
        return (java.lang.instrument.Instrumentation)held.get(null);
    }
    private static void annotationCaches(boolean installed)throws Exception{
        Field dataField=platformField(Class.class,"annotationData"),typeField=platformField(Class.class,"annotationType");
        long dataOffset=U.objectFieldOffset(dataField),typeOffset=U.objectFieldOffset(typeField);
        Class<?> target=AnnotationTarget.class;
        require(U.getObject(target,dataOffset)==null,"annotation target was already cached before regression");
        require(U.getObject(CacheMarker.class,typeOffset)==null,"annotation interface was already parsed before regression");
        require(target.getAnnotations().length==1&&target.getAnnotation(CacheMarker.class).value()==37
                &&target.isAnnotationPresent(CacheMarker.class),"fresh annotation publication failed");
        require(CacheMarker.class.getAnnotation(java.lang.annotation.Retention.class).value()==java.lang.annotation.RetentionPolicy.RUNTIME,
                "annotation interface cache publication failed");
        Object first=U.getObject(target,dataOffset),annotationType=U.getObject(CacheMarker.class,typeOffset);
        require(first!=null&&annotationType!=null,"CAS did not install both annotation caches");
        Field countField=platformField(Class.class,"classRedefinedCount");long countOffset=U.objectFieldOffset(countField);
        int before=U.getInt(target,countOffset);
        if(installed){
            actualInstrumentation().retransformClasses(target);
            require(U.getInt(target,countOffset)>before,"actual retransformation did not invalidate annotation cache");
        }else U.putInt(target,countOffset,before+1);
        require(target.getAnnotations().length==1&&target.getAnnotation(CacheMarker.class).value()==37,
                "annotation cache rebuild after invalidation failed");
        Object current=U.getObject(target,dataOffset);require(current!=null&&current!=first,"stale annotation data was reused");
        denyPlatformReference(target,dataField,null,installed);
        denyPlatformReference(target,platformField(Class.class,"name"),"tampered.annotation.mirror",installed);
        denyPlatformReference(target,platformField(Class.class,"classLoader"),null,installed);
        denyPlatformReference(CacheMarker.class,typeField,null,installed);
        for(String field:new String[]{"annotations","declaredAnnotations"})
            denyPlatformReference(current,platformField(current.getClass(),field),Map.of(),installed);
        denyPlatformReference(annotationType,platformField(annotationType.getClass(),"memberTypes"),Map.of(),installed);
        require(target.getAnnotation(CacheMarker.class).value()==37,"refused poisoning changed live annotations");
        System.out.println("ANNOTATION_CACHE_PASS installed="+installed+" fresh_data_and_type_CAS invalidation_rebuild mirror_and_cache_poison_refused");
    }
    private static void denyPlatformReference(Object receiver,Field field,Object proposed,boolean installed)throws Exception{
        Class<?> control=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        long offset=U.objectFieldOffset(field);Object before=U.getObject(receiver,offset);
        require(!(boolean)control.getMethod("unsafeWriteAllowed",Object.class,long.class,Object.class,String.class)
                .invoke(null,receiver,offset,proposed,"Object"),"external Unsafe admission: "+field);
        require(!(boolean)control.getMethod("reflectFieldWrite",Field.class,Object.class,Object.class)
                .invoke(null,field,receiver,proposed),"external reflection admission: "+field);
        if(!installed)return;
        U.putObjectVolatile(receiver,offset,proposed);
        require(U.getObject(receiver,offset)==before,"actual Unsafe poisoned "+field);
        field.setAccessible(true);
        try{field.set(receiver,proposed);}catch(IllegalAccessException denied){
            require("RONOVA_PROTECTED_FIELD_WRITE_REFUSED".equals(denied.getMessage()),"reflection failed outside Ronova guard: "+denied);
        }
        require(U.getObject(receiver,offset)==before,"actual Field.set poisoned "+field);
        try{MethodHandles.lookup().unreflectSetter(field).invokeWithArguments(receiver,proposed);}
        catch(Throwable failure){throw new AssertionError("guarded setter invocation failed: "+field,failure);}
        require(U.getObject(receiver,offset)==before,"actual MethodHandle setter poisoned "+field);
    }
    private static void installedPlatformWrites()throws Exception{
        require(Boolean.TRUE.equals(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("available").invoke(null)),
                "actual premain backend required");
        Class<?> metadata=Class.forName("dev.ronova.pro.bootstrap.PlatformMetadata",true,null);
        var raw=metadata.getMethod("declaredField",Class.class,String.class);
        require(MirrorWriteTarget.number==7,"mirror class initialization failed");
        Class<?> target=MirrorWriteTarget.class;String name=target.getName();
        Field nameField=(Field)raw.invoke(null,Class.class,"name");long nameOffset=U.objectFieldOffset(nameField);
        Object originalName=U.getObject(target,nameOffset);
        try{
            U.putObjectVolatile(target,nameOffset,"tampered.mirror");
            require(U.getObject(target,nameOffset)==originalName&&target.getName().equals(name),"installed Unsafe admitted Class instance write");
        }finally{if(U.getObject(target,nameOffset)!=originalName)U.putObjectVolatile(target,nameOffset,originalName);}
        // A fresh class must still publish its normal JDK ReflectionData CAS.
        require(target.getDeclaredFields().length==3,"legitimate reflection cache publication failed");
        Field cache=(Field)raw.invoke(null,Class.class,"reflectionData");long cacheOffset=U.objectFieldOffset(cache);
        Object originalCache=U.getObject(target,cacheOffset);
        try{
            U.putObjectVolatile(target,cacheOffset,new java.lang.ref.SoftReference<>(null));
            require(U.getObject(target,cacheOffset)==originalCache,"installed Unsafe admitted external Class cache overwrite");
        }finally{if(U.getObject(target,cacheOffset)!=originalCache)U.putObjectVolatile(target,cacheOffset,originalCache);}
        Object data=((java.lang.ref.Reference<?>)originalCache).get();
        Field declared=(Field)raw.invoke(null,data.getClass(),"declaredFields");long declaredOffset=U.objectFieldOffset(declared);
        Object originalFields=U.getObject(data,declaredOffset);
        try{
            U.putObjectVolatile(data,declaredOffset,new Field[0]);
            require(U.getObject(data,declaredOffset)==originalFields&&target.getDeclaredFields().length==3,"installed Unsafe admitted ReflectionData poison");
        }finally{if(U.getObject(data,declaredOffset)!=originalFields)U.putObjectVolatile(data,declaredOffset,originalFields);}
        Field number=(Field)raw.invoke(null,target,"number");U.putInt(U.staticFieldBase(number),U.staticFieldOffset(number),23);
        require(MirrorWriteTarget.number==23,"installed mirror guard rejected real static field write");
        annotationCaches(true);
        System.out.println("INSTALLED_PLATFORM_WRITES_PASS: actual Unsafe/reflection/handles refused, all three JDK cache CAS paths and real retransformation preserved");
    }
    private static void platformControlWrites()throws Exception{
        Class<?> control=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Class<?> metadata=Class.forName("dev.ronova.pro.bootstrap.PlatformMetadata",true,null);
        var allowed=control.getMethod("unsafeWriteAllowed",Object.class,long.class,Object.class,String.class);
        var bulk=control.getMethod("unsafeMemoryAllowed",Object.class,long.class,long.class);
        var fields=metadata.getMethod("declaredFields",Class.class);
        var rawField=metadata.getMethod("declaredField",Class.class,String.class);
        Class<?> target=MirrorWriteTarget.class;
        require(MirrorWriteTarget.number==7,"disposable mirror class initialization failed");
        for(long offset:new long[]{0,8,16,24})require(!(boolean)allowed.invoke(null,target,offset,0L,"Long"),"Class header admitted at "+offset);
        for(Field field:(Field[])fields.invoke(null,Class.class))if(!Modifier.isStatic(field.getModifiers())){
            long offset=U.objectFieldOffset(field);
            require(!(boolean)allowed.invoke(null,target,offset,null,field.getType().isPrimitive()?"Long":"Object"),"Class instance field admitted: "+field.getName());
        }
        Field number=(Field)rawField.invoke(null,target,"number"),reference=(Field)rawField.invoke(null,target,"reference");
        long numberOffset=U.staticFieldOffset(number),referenceOffset=U.staticFieldOffset(reference);
        require((boolean)allowed.invoke(null,target,numberOffset,19,"Int"),"real static int rejected");
        U.putInt(U.staticFieldBase(number),numberOffset,19);require(MirrorWriteTarget.number==19,"static int write failed");
        Object next=new Object();
        require((boolean)allowed.invoke(null,target,referenceOffset,next,"Object"),"real static reference rejected");
        U.putObject(U.staticFieldBase(reference),referenceOffset,next);require(MirrorWriteTarget.reference==next,"static reference write failed");
        require((boolean)bulk.invoke(null,target,numberOffset,4L),"static field bulk range rejected");
        require(!(boolean)bulk.invoke(null,target,16L,8L),"Class hidden bulk range admitted");
        require(!(boolean)bulk.invoke(null,target,numberOffset,Long.MAX_VALUE),"overflowing mirror range admitted");
        // Poison a disposable class's real JDK cache and read declarations from
        // the production raw VM reader. No fabricated source/owner metadata.
        target.getDeclaredFields();
        Field cache=(Field)rawField.invoke(null,Class.class,"reflectionData");
        long cacheOffset=U.objectFieldOffset(cache);
        Object referenceData=U.getObject(target,cacheOffset);
        Object data=((java.lang.ref.Reference<?>)referenceData).get();
        Field declared=(Field)rawField.invoke(null,data.getClass(),"declaredFields");
        long declaredOffset=U.objectFieldOffset(declared);
        Object previous=U.getObject(data,declaredOffset);
        try{
            require(!(boolean)allowed.invoke(null,target,cacheOffset,null,"Object"),"external Class cache CAS admitted");
            require(!(boolean)allowed.invoke(null,data,declaredOffset,new Field[0],"Object"),"ReflectionData poison admitted");
            U.putObject(data,declaredOffset,new Field[0]);
            require(target.getDeclaredFields().length==0,"cache poisoning setup did not affect real reflection");
            require(((Field[])fields.invoke(null,target)).length==3,"raw VM declarations consumed poisoned cache");
        }finally{U.putObject(data,declaredOffset,previous);}
        annotationCaches(false);
        System.out.println("PLATFORM_CONTROL_WRITES_PASS: real mirror/static storage, annotation caches and poisoned ReflectionData; production admission checks, no installed-agent claim");
    }
    private static final class NoCallbacks {
        @Override public boolean equals(Object other){throw new AssertionError("target equals callback");}
        @Override public int hashCode(){throw new AssertionError("target hashCode callback");}
    }
    private enum SmallEnum { A,B,C }
    private enum LargeEnum {
        E00,E01,E02,E03,E04,E05,E06,E07,E08,E09,E10,E11,E12,E13,E14,E15,
        E16,E17,E18,E19,E20,E21,E22,E23,E24,E25,E26,E27,E28,E29,E30,E31,
        E32,E33,E34,E35,E36,E37,E38,E39,E40,E41,E42,E43,E44,E45,E46,E47,
        E48,E49,E50,E51,E52,E53,E54,E55,E56,E57,E58,E59,E60,E61,E62,E63,E64
    }
    private static void knownContainerCleanup()throws Exception{
        Object removed=new NoCallbacks(),kept=new NoCallbacks();
        for(Collection<Object> carrier:List.<Collection<Object>>of(new Vector<>(),new Stack<>(),new ConcurrentLinkedQueue<>(),
                new ConcurrentLinkedDeque<>(),new LinkedBlockingQueue<>())){
            carrier.add(removed);carrier.add(kept);carrier.add(removed);
            cleanIdentity(carrier,removed);
            Object[] remaining=carrier.toArray();
            require(remaining.length==1&&remaining[0]==kept,"mixed cleanup lost neighbor: "+carrier.getClass());
            cleanAll(carrier);require(carrier.isEmpty(),"private cleanup left entries");
        }
        EnumMap<SmallEnum,Object> map=new EnumMap<>(SmallEnum.class);map.put(SmallEnum.A,removed);map.put(SmallEnum.B,kept);map.put(SmallEnum.C,null);
        cleanIdentity(map,removed);require(map.size()==2&&map.get(SmallEnum.B)==kept&&map.containsKey(SmallEnum.C),"EnumMap null/neighbor lost");
        cleanAll(map);require(map.isEmpty(),"EnumMap clear failed");
        EnumSet<SmallEnum> small=EnumSet.allOf(SmallEnum.class);cleanIdentity(small,SmallEnum.B);
        require(small.equals(EnumSet.of(SmallEnum.A,SmallEnum.C)),"RegularEnumSet wrong bit");
        EnumSet<LargeEnum> large=EnumSet.allOf(LargeEnum.class);cleanIdentity(large,LargeEnum.E64);
        require(large.size()==64&&!large.contains(LargeEnum.E64)&&large.contains(LargeEnum.E63),"JumboEnumSet wrong word");
        cleanAll(small);cleanAll(large);require(small.isEmpty()&&large.isEmpty(),"EnumSet clear failed");
        boolean[] armed={false};
        PriorityQueue<Object> queue=new PriorityQueue<>((a,b)->{if(armed[0])throw new AssertionError("comparator invoked during retirement");return 0;});
        queue.add(kept);queue.add(removed);armed[0]=true;cleanIdentity(queue,removed);
        require(queue.size()==1&&queue.peek()==kept,"PriorityQueue tail removal failed");
        cleanAll(queue);require(queue.isEmpty(),"PriorityQueue private clear failed");
        ConcurrentLinkedQueue<Object> concurrent=new ConcurrentLinkedQueue<>();concurrent.add(removed);
        var image=KnownContainers.snapshot(concurrent);concurrent.add(kept);KnownContainers.clear(image);
        require(concurrent.size()==1&&concurrent.peek()==kept,"captured queue cleanup swallowed new publication");
        System.out.println("KNOWN_CONTAINER_CLEANUP_PASS: actual JDK containers, identity removal, nulls, bitmaps, no user callbacks, concurrent new entry preserved");
    }
    private static void cleanIdentity(Object carrier,Object selected)throws ReflectiveOperationException{
        SourceMaps.Action action=()->{
            while(true){
                var image=KnownContainers.snapshot(carrier);KnownContainers.Row match=null;
                for(var row:image.rows)if(row.key==selected||row.value==selected){match=row;break;}
                if(match==null)return;
                require(KnownContainers.remove(image,match),"direct known-container remove refused");
            }
        };
        if(KnownContainers.lockFree(carrier)||KnownContainers.blocking(carrier))require(KnownContainers.withConcurrentScope(carrier,action),"queue scope unavailable");
        else synchronized(carrier){action.run();}
    }
    private static void cleanAll(Object carrier)throws ReflectiveOperationException{
        SourceMaps.Action action=()->KnownContainers.clear(KnownContainers.snapshot(carrier));
        if(KnownContainers.lockFree(carrier)||KnownContainers.blocking(carrier))require(KnownContainers.withConcurrentScope(carrier,action),"queue scope unavailable");
        else synchronized(carrier){action.run();}
    }
    // Only the isolated C fixture owns these expected tuples. Production
    // allocation/alias/restore code runs unchanged, with no controller install.
    private static boolean bufferRestoreCaller(Object action,long token,long generation,Module[] modules,long cursor,int budget){
        return opaqueNativeFixture&&action==opaqueNativeAction&&token==opaqueNativeToken&&generation==opaqueNativeGeneration
                &&modules==opaqueNativeModules&&cursor==0&&(budget==0||budget==8);
    }
    private static boolean memoryRecoveryCaller(){return opaqueNativeFixture;}
    private static boolean opaqueNativeAddress(Object buffer,Module[] sources){
        opaqueNativeSeen=buffer;if(opaqueNativeObserverMode==1)throw OPAQUE_NATIVE_FAILURE;return opaqueNativeObserverMode!=2;
    }
    private static void opaqueNativeCreated(Object buffer,Module[] sources){
        opaqueNativeSeen=buffer;if(opaqueNativeObserverMode==1)throw OPAQUE_NATIVE_FAILURE;
    }
    private static void opaqueNativeState(){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.native")).toAbsolutePath().toString());
        opaqueNativeAction=new Object();opaqueNativeModules=new Module[]{AuditFixCheck.class.getModule()};opaqueNativeFixture=true;
        try{require(opaqueNativeState0(opaqueNativeAction,opaqueNativeModules,OPAQUE_NATIVE_FAILURE),"opaque native allocation regression failed");}
        finally{opaqueNativeFixture=false;opaqueNativeSeen=opaqueNativeAction=null;opaqueNativeModules=null;opaqueNativeToken=opaqueNativeGeneration=0;opaqueNativeObserverMode=0;}
        System.out.println("OPAQUE_NATIVE_STATE_PASS actual_JNI_views live_alias_blocks_private_free_raw_and_managed_undo no_revision_retirement_pending original_release_same_address_reuse_rejects_old_lifetime current_lease_seals_retirement opaque_exposure_blocks_undo original_result_Throwable_LastError_preserved stopped_refused unknown_span_not_registered fixture_allocation_registration_only no_Agent_or_JNI_table_install");
    }
    private static void bufferRelease()throws Exception {
        java.nio.ByteBuffer direct=java.nio.ByteBuffer.allocateDirect(32);
        direct.putLong(0,0x1020304050607080L);
        var addressField=java.nio.Buffer.class.getDeclaredField("address");
        long address=U.getLong(direct,U.objectFieldOffset(addressField));
        try{U.freeMemory(address);throw new AssertionError("foreign direct-buffer release admitted");}
        catch(IllegalStateException refused){require("MANAGED_BUFFER_ORIGINAL_RELEASE_REQUIRED".equals(refused.getMessage()),"managed release was not refused at original action");}
        require(direct.getLong(0)==0x1020304050607080L,"refused free changed live buffer");
        U.invokeCleaner(direct);
        var file=java.nio.file.Files.createTempFile("ronova-buffer-release-",".bin");
        try{
            byte[] payload=new byte[131072];payload[0]=17;payload[payload.length-1]=29;
            java.nio.file.Files.write(file,payload);
            try(var channel=java.nio.channels.FileChannel.open(file,java.nio.file.StandardOpenOption.READ)){
                for(int size:new int[]{8,4096,131072}){
                    java.nio.ByteBuffer target=java.nio.ByteBuffer.allocate(size);channel.position(0);
                    while(target.hasRemaining()&&channel.read(target)>=0){}
                    require(target.position()==size&&target.get(0)==17,"actual temporary-buffer read changed bytes");
                    if(size==payload.length)require(target.get(size-1)==29,"actual larger read changed tail");
                }
            }
        }finally{java.nio.file.Files.deleteIfExists(file);}
        System.out.println("BUFFER_RELEASE_PASS actual_native foreign_release_refused live_buffer_preserved original_cleaner temporary_file_reads");
    }
    // Actual LWJGL allocateInstance views and the UTF16 module query that blocked client startup.
    private static void bufferWrap()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> memory=Class.forName("org.lwjgl.system.MemoryUtil");
        Method publicAddress=memory.getMethod("memAddress",java.nio.Buffer.class);
        long addressOffset=U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("address"));
        long markOffset=U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("mark"));
        long limitOffset=U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("limit"));
        long capacityOffset=U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("capacity"));
        long address=U.allocateMemory(64);
        require(address!=0,"actual raw memory allocation absent");
        try {
            for(String kind:List.of("Byte","Short","Char","Int","Long","Float","Double")){
                Method wrap=memory.getMethod("mem"+kind+"Buffer",long.class,int.class);
                for(int capacity:new int[]{5,0}){
                    java.nio.Buffer view=(java.nio.Buffer)wrap.invoke(null,address,capacity);
                    String label=kind+" capacity="+capacity;
                    require(view.isDirect()&&!view.isReadOnly(),"actual LWJGL view is not writable direct: "+label);
                    require(view.capacity()==capacity&&view.limit()==capacity&&view.position()==0&&view.remaining()==capacity,
                            "actual LWJGL view retained incorrect bounds: "+label);
                    require(U.getLong(view,addressOffset)==address&&(Long)publicAddress.invoke(null,view)==address,
                            "actual LWJGL view retained an incorrect address: "+label);
                    require(U.getInt(view,markOffset)==-1,"actual LWJGL view retained its default mark: "+label);
                    if(capacity>0)bufferWrapContent(view,address,capacity-1);
                    try{U.putLong(view,addressOffset,address+8);}catch(SecurityException refused){ }
                    require(U.getLong(view,addressOffset)==address,"foreign initialized-view address rewrite admitted: "+label);
                    for(long offset:new long[]{markOffset,limitOffset,capacityOffset}){
                        int original=U.getInt(view,offset);
                        try{U.putInt(view,offset,original+1);}catch(SecurityException refused){ }
                        require(U.getInt(view,offset)==original,"foreign initialized-view metadata rewrite admitted: "+label+" offset="+offset);
                    }
                    require(view.capacity()==capacity&&view.limit()==capacity&&view.position()==0,
                            "refused metadata rewrite changed actual view bounds: "+label);
                }
            }
        }finally{U.freeMemory(address);}
        Class<?> winBase=Class.forName("org.lwjgl.system.windows.WinBase");
        long module=(Long)winBase.getMethod("GetModuleHandle",CharSequence.class).invoke(null,"kernel32.dll");
        require(module!=0,"actual WinBase UTF16 module query did not find loaded kernel32.dll");
        System.out.println("BUFFER_WRAP_PASS actual_lwjgl_3_3_1 public_byte_and_six_typed_views exact_address_mark_bounds raw_buffer_duplex zero_capacity_initialized foreign_metadata_rewrite_refused WinBase_GetModuleHandle_UTF16");
    }
    // The actual FML reader resizes Monocraft before returning MemoryUtil.memSlice to SimpleFont.
    private static void bufferFont()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> memory=Class.forName("org.lwjgl.system.MemoryUtil");
        Method byteAddress=memory.getMethod("memAddress",java.nio.ByteBuffer.class);
        long addressOffset=U.objectFieldOffset(java.nio.Buffer.class.getDeclaredField("address"));
        Class<?> helper=Class.forName("net.minecraftforge.fml.earlydisplay.STBHelper");
        byte[] original;
        try(var resource=helper.getClassLoader().getResourceAsStream("Monocraft.ttf")){
            require(resource!=null,"actual FML Monocraft.ttf resource absent");original=resource.readAllBytes();
        }
        require(original.length>200000,"actual FML font did not exercise its resize path");
        java.nio.ByteBuffer font=(java.nio.ByteBuffer)helper.getMethod("readFromClasspath",String.class,int.class).invoke(null,"Monocraft.ttf",200000);
        require(font.isDirect()&&font.position()==0&&font.remaining()==original.length&&font.capacity()==original.length,
                "actual FML font slice bounds differ from its resource");
        long fontAddress=U.getLong(font,addressOffset);
        require(fontAddress!=0,"actual FML font buffer address is zero; STB native init not called");
        require((Long)byteAddress.invoke(null,font)==fontAddress,"actual FML public font address differs from its Buffer address");
        byte[] loaded=new byte[original.length];font.get(0,loaded);
        require(Arrays.equals(original,loaded),"actual FML font bytes differ from its original resource");
        Class<?> stackType=Class.forName("org.lwjgl.system.MemoryStack");
        try(AutoCloseable stack=(AutoCloseable)stackType.getMethod("stackPush").invoke(null)){
            java.nio.IntBuffer offsets=(java.nio.IntBuffer)stackType.getMethod("mallocInt",int.class).invoke(stack,1);
            long offsetsAddress=U.getLong(offsets,addressOffset);
            require(offsetsAddress!=0,"actual font-init MemoryStack offsets address is zero; STB info not loaded");
            require(offsets.remaining()==1&&(Long)memory.getMethod("memAddress",java.nio.IntBuffer.class).invoke(null,offsets)==offsetsAddress,
                    "actual font-init MemoryStack offsets address or bounds differ");
        }
        Class<?> infoType=Class.forName("org.lwjgl.stb.STBTTFontinfo");
        int infoBytes=(Integer)infoType.getField("SIZEOF").get(null);
        require(infoBytes>0,"actual STBTTFontinfo native size is zero");
        Object info=infoType.getMethod("create").invoke(null);
        Class<?> structType=Class.forName("org.lwjgl.system.Struct");
        Class<?> pointerType=Class.forName("org.lwjgl.system.Pointer$Default");
        Object captured=U.getObject(info,U.objectFieldOffset(structType.getDeclaredField("container")));
        require(captured instanceof java.nio.ByteBuffer,"actual STBTTFontinfo.create container absent; STB native init not called");
        java.nio.ByteBuffer container=(java.nio.ByteBuffer)captured;
        long infoAddress=(Long)infoType.getMethod("address").invoke(info);
        long actualInfoAddress=U.getLong(info,U.objectFieldOffset(pointerType.getDeclaredField("address")));
        long containerAddress=U.getLong(container,addressOffset);
        require(infoAddress!=0&&actualInfoAddress!=0&&containerAddress!=0,
                "actual STBTTFontinfo address is zero; STB native init not called");
        require(infoAddress==actualInfoAddress&&infoAddress==containerAddress&&(Long)byteAddress.invoke(null,container)==containerAddress,
                "actual STBTTFontinfo address differs from its real container");
        require(container.isDirect()&&container.position()==0&&container.remaining()==infoBytes,
                "actual STBTTFontinfo container bounds differ from its native size");
        Class<?> truetype=Class.forName("org.lwjgl.stb.STBTruetype");
        boolean initialized=(Boolean)truetype.getMethod("stbtt_InitFont",infoType,java.nio.ByteBuffer.class).invoke(null,info,font);
        java.lang.ref.Reference.reachabilityFence(info);java.lang.ref.Reference.reachabilityFence(font);
        require(initialized,"actual STB did not initialize the original FML font");
        System.out.println("BUFFER_FONT_PASS actual_fml_STBHelper resized_Monocraft_original_bytes memSlice_nonzero_exact_address MemoryStack_font_offsets_pointer STBTTFontinfo_create_actual_container public_STB_init");
    }
    // Actual SRG constructors that publish the image allocation used during Window icon loading.
    private static void nativeImageSrg()throws Exception {
        Class<?> nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        require(Boolean.TRUE.equals(nativeControl.getMethod("available").invoke(null)),"actual Windows native backend absent");
        Class<?> imageType=Class.forName("com.mojang.blaze3d.platform.NativeImage");
        Class<?> formatType=Class.forName("com.mojang.blaze3d.platform.NativeImage$Format");
        Field pixels=imageType.getDeclaredField("f_84964_"),size=imageType.getDeclaredField("f_84965_");
        require(pixels.getType()==long.class&&size.getType()==long.class&&!Modifier.isStatic(pixels.getModifiers())&&!Modifier.isStatic(size.getModifiers()),
                "actual SRG NativeImage pixel allocation fields changed");
        long pixelsOffset=U.objectFieldOffset(pixels),sizeOffset=U.objectFieldOffset(size);
        Method set=imageType.getMethod("m_84988_",int.class,int.class,int.class);
        Method get=imageType.getMethod("m_84985_",int.class,int.class);
        Method width=imageType.getMethod("m_84982_"),height=imageType.getMethod("m_85084_");
        Object rgba=formatType.getField("RGBA").get(null);
        int[] colors={0xff0000ff,0xff00ff00,0xffff0000,0xffffffff};
        for(boolean explicitFormat:new boolean[]{false,true}){
            Object image=explicitFormat?imageType.getConstructor(formatType,int.class,int.class,boolean.class).newInstance(rgba,2,2,true)
                    :imageType.getConstructor(int.class,int.class,boolean.class).newInstance(2,2,true);
            try(AutoCloseable original=(AutoCloseable)image){
                require((Integer)width.invoke(image)==2&&(Integer)height.invoke(image)==2,
                        "actual SRG NativeImage dimensions changed");
                require(U.getLong(image,pixelsOffset)!=0&&U.getLong(image,sizeOffset)==16,
                        "actual SRG NativeImage allocation absent or incorrect; pixel methods not called");
                for(int i=0;i<colors.length;i++)set.invoke(image,i&1,i>>>1,colors[i]);
                for(int i=0;i<colors.length;i++)require((Integer)get.invoke(image,i&1,i>>>1)==colors[i],
                        "actual SRG NativeImage pixel readback changed at "+i);
            }
            require(U.getLong(image,pixelsOffset)==0,"actual SRG NativeImage close retained its pixel allocation");
        }
        System.out.println("NATIVE_IMAGE_SRG_PASS actual_cached_srg public_default_and_format_constructors exact_pixel_pointer_and_size rgba_2x2_public_write_read original_close_zero_pointer");
        System.out.println("DYNAMIC_TEXTURE_NOTRUN requires_actual_RenderSystem_and_GL_context");
    }
    private static void bufferWrapContent(java.nio.Buffer view,long address,int last){
        if(view instanceof java.nio.ByteBuffer buffer){
            buffer.put(0,(byte)0x31);require(U.getByte(address)==0x31,"LWJGL ByteBuffer did not write actual memory");
            U.putByte(address+last,(byte)0x52);require(buffer.get(last)==0x52,"LWJGL ByteBuffer did not read actual memory");
        }else if(view instanceof java.nio.ShortBuffer buffer){
            buffer.put(0,(short)0x1357);require(U.getShort(address)==(short)0x1357,"LWJGL ShortBuffer did not write actual memory");
            U.putShort(address+last*2L,(short)0x2468);require(buffer.get(last)==(short)0x2468,"LWJGL ShortBuffer did not read actual memory");
        }else if(view instanceof java.nio.CharBuffer buffer){
            buffer.put(0,'中');require(U.getChar(address)=='中',"LWJGL CharBuffer did not write actual memory");
            U.putChar(address+last*2L,'文');require(buffer.get(last)=='文',"LWJGL CharBuffer did not read actual memory");
        }else if(view instanceof java.nio.IntBuffer buffer){
            buffer.put(0,0x13572468);require(U.getInt(address)==0x13572468,"LWJGL IntBuffer did not write actual memory");
            U.putInt(address+last*4L,0x23456789);require(buffer.get(last)==0x23456789,"LWJGL IntBuffer did not read actual memory");
        }else if(view instanceof java.nio.LongBuffer buffer){
            buffer.put(0,0x1020304050607080L);require(U.getLong(address)==0x1020304050607080L,"LWJGL LongBuffer did not write actual memory");
            U.putLong(address+last*8L,0x1122334455667788L);require(buffer.get(last)==0x1122334455667788L,"LWJGL LongBuffer did not read actual memory");
        }else if(view instanceof java.nio.FloatBuffer buffer){
            buffer.put(0,17.25F);require(U.getFloat(address)==17.25F,"LWJGL FloatBuffer did not write actual memory");
            U.putFloat(address+last*4L,31.5F);require(buffer.get(last)==31.5F,"LWJGL FloatBuffer did not read actual memory");
        }else if(view instanceof java.nio.DoubleBuffer buffer){
            buffer.put(0,19.125);require(U.getDouble(address)==19.125,"LWJGL DoubleBuffer did not write actual memory");
            U.putDouble(address+last*8L,27.5);require(buffer.get(last)==27.5,"LWJGL DoubleBuffer did not read actual memory");
        }else throw new AssertionError("unexpected actual LWJGL view: "+view.getClass().getName());
    }
    private static class CloneBase implements Cloneable { int value=17; }
    private static final class InheritedClone extends CloneBase {
        Object duplicate()throws CloneNotSupportedException{return super.clone();}
    }
    private static final class PlainClone { Object duplicate()throws CloneNotSupportedException{return super.clone();} }
    private static class BusinessClone { public Object clone(){return "business-override";} }
    private static final class OverriddenClone extends BusinessClone { Object duplicate(){return super.clone();} }
    private enum SealedClone {
        LEFT {int marker(){return 1;}},RIGHT {int marker(){return 2;}};
        abstract int marker();
    }
    private static void cloneBoundary()throws Exception {
        SealedClone[] first=SealedClone.values(),second=SealedClone.values();
        require(SealedClone.class.isSealed()&&SealedClone.class.getPermittedSubclasses().length==2,"sealed enum permits lost during clone scan");
        require(first!=second&&first.length==2&&first[0].marker()==1&&first[1].marker()==2,"sealed enum values clone contract changed");
        InheritedClone original=new InheritedClone();Object result=original.duplicate();
        require(result instanceof InheritedClone&&result!=original&&((InheritedClone)result).value==17,"native inherited clone contract changed");
        try{new PlainClone().duplicate();throw new AssertionError("non-Cloneable accepted");}catch(CloneNotSupportedException expected){ }
        require("business-override".equals(new OverriddenClone().duplicate()),"business clone override rewritten");
        System.out.println("CLONE_BOUNDARY_PASS actual_agent_native sealed_enum_permits values_clone inherited_owner distinct_identity payload non_cloneable_exception business_override");
    }
    private static void networkPreconnect(String jar)throws Exception {
        var finder=java.lang.module.ModuleFinder.of(java.nio.file.Path.of(jar));
        require(finder.findAll().size()==1,"one actual isolated fixture module is required");
        String name=finder.findAll().iterator().next().descriptor().name();
        var configuration=ModuleLayer.boot().configuration().resolve(finder,java.lang.module.ModuleFinder.of(),Set.of(name));
        var layer=ModuleLayer.boot().defineModulesWithOneLoader(configuration,ClassLoader.getPlatformClassLoader());
        Class<?> sender=Class.forName("dev.ronova.pro.validation.WorldFixture$NetworkSender",true,layer.findLoader(name));
        Module module=sender.getModule();
        require(module.isNamed()&&module==layer.findModule(name).orElseThrow(),"sender did not load from the actual fixture module");
        Method submit=sender.getMethod("send",Object.class,Object.class);
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",true,null);
        Method current=bridge.getDeclaredMethod("currentSources");current.setAccessible(true);
        Field activeField=bridge.getDeclaredField("ACTIVE"),channelsField=bridge.getDeclaredField("CHANNELS");
        activeField.setAccessible(true);channelsField.setAccessible(true);
        Set<?> active=(Set<?>)activeField.get(null);Object channels=channelsField.get(null);
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Method producer=controls.getDeclaredMethod("producer",Module.class);producer.setAccessible(true);
        Method stopped=controls.getDeclaredMethod("modStopped",Module.class);stopped.setAccessible(true);
        require(!Boolean.TRUE.equals(producer.invoke(null,AuditFixCheck.class.getModule())),"outside consumer is a Mod producer");
        if(Boolean.TRUE.equals(stopped.invoke(null,module))){networkStoppedPromises(sender,controls);return;}
        require(!Boolean.TRUE.equals(stopped.invoke(null,module)),"preconnect fixture must remain active without bootStop");
        require(networkSources(current).length==0,"caller started inside a network scope");
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var packet=new net.minecraft.network.protocol.game.ClientboundKeepAlivePacket(7197L);
        require(!connection.isConnected(),"connection unexpectedly active before real submissions");
        submit.invoke(null,connection,packet);
        connection.send(packet);
        submit.invoke(null,connection,packet);
        require(Boolean.TRUE.equals(producer.invoke(null,module)),"actual @Mod fixture source was not registered");
        require(!Boolean.TRUE.equals(stopped.invoke(null,module)),"active fixture unexpectedly stopped");
        Field queueField=Arrays.stream(net.minecraft.network.Connection.class.getDeclaredFields())
                .filter(field->!Modifier.isStatic(field.getModifiers())&&Queue.class.isAssignableFrom(field.getType())).findFirst().orElseThrow();
        queueField.setAccessible(true);Queue<?> queue=(Queue<?>)queueField.get(connection);
        var holders=new ArrayList<>(queue);
        require(holders.size()==3&&holders.get(0)!=holders.get(1)&&holders.get(0)!=holders.get(2)&&holders.get(1)!=holders.get(2),"actual preconnect submissions did not create independent queue items");
        require(networkSources(current).length==0,"preconnect submission leaked its scope to the caller");
        var writes=Collections.synchronizedList(new ArrayList<Module[]>());
        var writeThreads=Collections.synchronizedList(new ArrayList<Thread>());
        var writeInLoop=Collections.synchronizedList(new ArrayList<Boolean>());
        var promises=Collections.synchronizedList(new ArrayList<io.netty.channel.ChannelPromise>());
        var reads=Collections.synchronizedList(new ArrayList<String>());
        var peer=new java.util.concurrent.atomic.AtomicReference<io.netty.channel.local.LocalChannel>();
        var flushes=new java.util.concurrent.atomic.AtomicInteger();
        var readCompletes=new java.util.concurrent.atomic.AtomicInteger();
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var written=new CountDownLatch(3);var received=new CountDownLatch(3);
        Thread consumer=Thread.currentThread();
        var serverLoops=new io.netty.channel.DefaultEventLoopGroup(1);
        var clientLoops=new io.netty.channel.DefaultEventLoopGroup(1);
        io.netty.channel.Channel server=null,client=null;
        try {
            server=new io.netty.bootstrap.ServerBootstrap().group(serverLoops).channel(io.netty.channel.local.LocalServerChannel.class)
                    .childHandler(new io.netty.channel.ChannelInitializer<io.netty.channel.local.LocalChannel>() {
                        protected void initChannel(io.netty.channel.local.LocalChannel channel){
                            peer.set(channel);
                            channel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter(){
                                public void channelRead(io.netty.channel.ChannelHandlerContext context,Object message){
                                    reads.add(message.getClass().getName()+"@"+System.identityHashCode(message));
                                    if(message!=packet)failure.compareAndSet(null,new AssertionError("local transport changed the submitted packet identity"));
                                    io.netty.util.ReferenceCountUtil.release(message);received.countDown();
                                }
                                public void channelReadComplete(io.netty.channel.ChannelHandlerContext context)throws Exception{
                                    readCompletes.incrementAndGet();context.fireChannelReadComplete();
                                }
                                public void exceptionCaught(io.netty.channel.ChannelHandlerContext context,Throwable error){failure.compareAndSet(null,error);context.close();}
                            });
                        }
                    }).bind(new io.netty.channel.local.LocalAddress("ronova-preconnect-"+UUID.randomUUID())).sync().channel();
            client=new io.netty.bootstrap.Bootstrap().group(clientLoops).channel(io.netty.channel.local.LocalChannel.class)
                    .handler(new io.netty.channel.ChannelInitializer<io.netty.channel.local.LocalChannel>() {
                        protected void initChannel(io.netty.channel.local.LocalChannel channel){
                            channel.pipeline().addLast(new io.netty.channel.ChannelOutboundHandlerAdapter(){
                                public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception {
                                    if(message==packet){
                                        writes.add(networkSources(current));writeThreads.add(Thread.currentThread());writeInLoop.add(context.executor().inEventLoop());promises.add(promise);
                                        promise.addListener(result->{if(!result.isSuccess())failure.compareAndSet(null,result.cause()!=null?result.cause():new AssertionError("actual local write was cancelled"));});
                                        written.countDown();
                                    }
                                    else failure.compareAndSet(null,new AssertionError("unexpected local outbound message"));
                                    context.write(message,promise);
                                }
                                public void flush(io.netty.channel.ChannelHandlerContext context)throws Exception{flushes.incrementAndGet();context.flush();}
                            });
                            channel.pipeline().addLast("packet_handler",connection);
                        }
                    }).connect(server.localAddress()).sync().channel();
            client.eventLoop().submit(()->{}).sync();
            require(connection.isConnected()&&!client.eventLoop().inEventLoop(),"real Connection was not activated on a distinct EventLoop");
            Method flush=net.minecraft.network.Connection.class.getDeclaredMethod("flushQueue");flush.setAccessible(true);
            flush.invoke(connection);
            require(queue.isEmpty(),"original Connection.flushQueue did not consume its real queue");
            require(networkSources(current).length==0,"flushQueue leaked its dispatch scope to the non-Mod caller");
            var cleared=client.eventLoop().submit(()->networkSources(current));cleared.sync();
            // This single client loop completes the original submitted writes before
            // the sentinel. LocalChannel then schedules reads on the distinct peer
            // loop; the one-thread server group drains those submitted tasks first.
            serverLoops.submit(()->{}).sync();
            if(!(written.await(10,TimeUnit.SECONDS)&&received.await(10,TimeUnit.SECONDS)))throw new AssertionError(
                    "real local EventLoop did not deliver all queued packets: writtenRemaining="+written.getCount()
                            +", receivedRemaining="+received.getCount()+", failure="+failure.get()
                            +", promises="+networkPromises(promises)+", flushes="+flushes.get()+", reads="+reads+", readCompletes="+readCompletes.get()
                            +", peer="+networkPeerState(peer.get())+", clientPending="+networkClientPending(client),failure.get());
            require(cleared.get().length==0,"EventLoop receipt scope remained after its original Runnable returned");
            Object[] remaining;synchronized(channels){remaining=active.toArray();}
            for(Object scope:remaining){
                Field sources=scope.getClass().getDeclaredField("modules");sources.setAccessible(true);
                require(!Arrays.asList((Module[])sources.get(scope)).contains(module),"completed receipt still retained this fixture source in ACTIVE");
            }
            if(failure.get()!=null)throw new AssertionError("actual local transport failed",failure.get());
            require(promises.size()==3&&promises.stream().allMatch(io.netty.channel.ChannelPromise::isSuccess),"actual local write promises did not all succeed: "+networkPromises(promises));
            require(writes.size()==3&&Arrays.asList(writes.get(0)).contains(module)&&!Arrays.asList(writes.get(1)).contains(module)
                    &&Arrays.asList(writes.get(2)).contains(module),"shared packet lost a sender or inherited a different submission's source");
            for(int i=0;i<3;i++)require(writeThreads.get(i)!=consumer&&writeInLoop.get(i),"write did not execute on the actual separate EventLoop");
            System.out.println("NETWORK_PRECONNECT_PASS actual_active_named_Mod Connection_send shared_vanilla_packet independent_queue_items nonMod_flushQueue real_LocalChannel_EventLoop deferred_sender_sources no_shared_packet_source_pollution scope_finally_and_ACTIVE_cleared");
            System.out.println("NETWORK_PRECONNECT_QUEUED_STOP_NOTRUN actual_active_to_stop_transition_requires_game_Runtime no_fabricated_stop_or_producer_authority");
        } finally {
            if(client!=null)client.close().syncUninterruptibly();
            if(server!=null)server.close().syncUninterruptibly();
            clientLoops.shutdownGracefully(0,5,TimeUnit.SECONDS).syncUninterruptibly();
            serverLoops.shutdownGracefully(0,5,TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
    private static void networkStoppedPromises(Class<?> source,Class<?> controls)throws Exception {
        // The same actual named fixture module is stopped by its configured
        // bootStop policy. Its Class identity carries that source without
        // executing a stopped sender or manufacturing a receipt/authority.
        Module[] sources=(Module[])controls.getMethod("objectSources",Object.class).invoke(null,source);
        require(Arrays.asList(sources).contains(source.getModule()),"actual stopped fixture Class lost its module source");
        var writes=new java.util.concurrent.atomic.AtomicInteger();var flushes=new java.util.concurrent.atomic.AtomicInteger();
        var channel=new io.netty.channel.embedded.EmbeddedChannel(new io.netty.channel.ChannelOutboundHandlerAdapter(){
            public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception{writes.incrementAndGet();context.write(message,promise);}
            public void flush(io.netty.channel.ChannelHandlerContext context)throws Exception{flushes.incrementAndGet();context.flush();}
        });
        var other=new io.netty.channel.embedded.EmbeddedChannel();
        try {
            Object unrelated=new Object();channel.write(unrelated);
            require(writes.get()==1&&flushes.get()==0&&channel.readOutbound()==null,"unrelated write did not remain queued before its flush");
            var foreign=other.newPromise();
            try{channel.writeAndFlush(source,foreign);throw new AssertionError("foreign channel promise was accepted by stopped-source gate");}
            catch(IllegalArgumentException expected){require(!foreign.isDone(),"stopped write changed another channel's promise");}
            var completed=channel.newPromise();completed.setSuccess();
            try{channel.writeAndFlush(source,completed);throw new AssertionError("completed promise was accepted by stopped-source gate");}
            catch(IllegalArgumentException expected){require(completed.isSuccess(),"stopped write changed an already completed promise");}
            var cancelled=channel.newPromise();require(cancelled.cancel(false),"actual promise did not enter cancelled state");
            require(channel.writeAndFlush(source,cancelled)==cancelled&&cancelled.isCancelled(),"original cancelled-promise result changed");
            require(writes.get()==1&&flushes.get()==0&&channel.readOutbound()==null,"invalid or cancelled stopped write flushed an unrelated queued message");
            var refused=channel.newPromise();channel.writeAndFlush(source,refused);
            require(refused.isDone()&&!refused.isSuccess()&&refused.cause() instanceof CancellationException
                    &&"NETWORK_OUTBOUND_REJECTED".equals(refused.cause().getMessage()),"valid stopped-source write escaped the actual outbound gate");
            require(writes.get()==1&&flushes.get()==1&&channel.readOutbound()==unrelated&&channel.readOutbound()==null,"valid stopped rejection lost the preceding unrelated write/flush");
            System.out.println("NETWORK_PROMISE_STOP_PASS actual_bootStop_named_Mod_Class_source real_EmbeddedChannel foreign_promise_unchanged completed_promise_original_exception cancelled_no_flush valid_stopped_rejected preceding_unrelated_write_flushed");
        } finally {channel.finishAndReleaseAll();other.finishAndReleaseAll();}
    }
    /** The actual early reader must retain the selected world and its durable return policy. */
    private static void startupReturnReplay()throws Exception{
        var directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of(".work"),"startup-life-").toAbsolutePath();
        var world=directory.resolve("universe/chosen");var journal=world.resolve("ronova-pro");java.nio.file.Files.createDirectories(journal);
        UUID realm=UUID.randomUUID(),session=UUID.randomUUID();String id="startup_target";
        UUID group=UUID.nameUUIDFromBytes(("ronova-mod-group/1|"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String empty=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0]));
        int version=dev.ronova.pro.persistence.JournalLineage.VERSION;
        try(var file=java.nio.channels.FileChannel.open(journal.resolve("intent-v"+version+".log"),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){
            dev.ronova.pro.persistence.JournalLineage.writeRecord(file,"HEADER\t"+version+"\t"+realm);
            for(int prior=1;prior<version;prior++)dev.ronova.pro.persistence.JournalLineage.writeRecord(file,"MIGRATED_V"+prior+"\t"+empty+"\t0");
            dev.ronova.pro.persistence.JournalLineage.writeRecord(file,"GROUP/1\tSTOP\t"+group+"\t"+session+"\t1\t"+id+"\tdefault");
            dev.ronova.pro.persistence.JournalLineage.writeRecord(file,"GROUP/1\tRETURN\t"+group+"\tempty");file.force(true);
        }
        Class<?> targets=Class.forName("dev.ronova.pro.prelaunch.RestartTargets");
        Method read=targets.getDeclaredMethod("read",java.nio.file.Path.class,List.class);read.setAccessible(true);
        Object selected=read.invoke(null,directory,List.of("--launchTarget=forgeserver","--universe","universe","--world=chosen"));
        require(world.equals(invoke(selected,"world"))&&realm.equals(invoke(selected,"realm"))
                &&Map.of(id,"empty").equals(invoke(selected,"modes")),"early restart reader lost selected world or durable non-default return policy");
        Object client=read.invoke(null,directory,List.of("--launchTarget","forgeclient","--world=chosen"));
        require(invoke(client,"world")==null&&Map.of().equals(invoke(client,"modes")),"client launch borrowed a dedicated-server return policy");
        Method mode=targets.getDeclaredMethod("mode",String.class);mode.setAccessible(true);
        for(String policy:List.of("default","null","empty","uuid-fixed","uuid-each","invalid-id"))
            require(policy.equals(mode.invoke(null,policy)),"early return parser replaced the saved policy: "+policy);
        System.out.println("STARTUP_RETURN_REPLAY_PASS actual_JournalLineage_selected_dedicated_world_STOP_RETURN_nondefault_mode client_separate six_policies_preserved no_game_restart_or_Agent_installation_claim");
    }
    /** These entity overrides deliberately omit super; no entity is constructed by the isolated check. */
    public abstract static class ProtectionSetterBody extends net.minecraft.world.entity.LivingEntity {
        public int bodyCalls,fireValue;
        public float absorptionValue;
        public Throwable originalFailure;
        protected ProtectionSetterBody(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity> type,net.minecraft.world.level.Level level){super(type,level);}
        @Override public void setAbsorptionAmount(float value){bodyCalls++;absorptionValue=value;failOriginal();}
        @Override public void setRemainingFireTicks(int value){bodyCalls++;fireValue=value;failOriginal();}
        public void m_7911_(float value){bodyCalls++;absorptionValue=value;failOriginal();}
        public void m_7311_(int value){bodyCalls++;fireValue=value;failOriginal();}
        private void failOriginal(){
            if(originalFailure instanceof RuntimeException failure)throw failure;
            if(originalFailure instanceof Error failure)throw failure;
        }
    }
    private static Object setterExpectedReceiver,setterExpectedValue;
    private static String setterExpectedOperation;
    private static boolean setterAllow;
    private static int setterGuardCalls;
    /** Ordinary test predicate for an isolated executable copy, never installed into the real Bridge. */
    public static boolean protectionSetterAllowed(Object receiver,String operation,Object value){
        require(receiver==setterExpectedReceiver&&operation.equals(setterExpectedOperation),"setter entry changed its exact receiver or operation");
        require(value!=null&&value.getClass()==setterExpectedValue.getClass()&&value.equals(setterExpectedValue),"setter entry changed its primitive argument or boxing");
        setterGuardCalls++;return setterAllow;
    }
    private static void protectionSetterBytecode()throws Exception{
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Field guards=controls.getDeclaredField("guards");guards.setAccessible(true);
        require(guards.get(null)==null,"isolated setter body check requires uninstalled Core guards");
        MethodHandles.publicLookup().findStatic(controls,"dispatchAllowed",MethodType.methodType(boolean.class,Object.class,String.class,Object.class));
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.DispatchBoundary"),entryType=Class.forName("dev.ronova.pro.agent.DispatchBoundary$Entry");
        Constructor<?> entryConstructor=entryType.getDeclaredConstructor(Class.class,String.class,String.class,boolean.class);entryConstructor.setAccessible(true);
        Field entries=boundary.getDeclaredField("entries");entries.setAccessible(true);
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,Class.class,byte[].class);transform.setAccessible(true);
        var selected=new LinkedHashMap<String,Class<?>>();var declarations=new ArrayList<Object>();
        Class<?>[] owners={net.minecraft.world.entity.LivingEntity.class,net.minecraft.world.entity.Entity.class};
        String[][] names={{"setAbsorptionAmount","m_7911_","(F)V"},{"setRemainingFireTicks","m_7311_","(I)V"}};
        for(int index=0;index<owners.length;index++){
            int matches=0;
            for(Method method:owners[index].getDeclaredMethods())if((method.getName().equals(names[index][0])||method.getName().equals(names[index][1]))
                    &&MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString().equals(names[index][2])){
                declarations.add(entryConstructor.newInstance(owners[index],method.getName(),names[index][2],false));selected.put(method.getName(),method.getParameterTypes()[0]);matches++;
            }
            require(matches==1,"actual game setter declaration missing or ambiguous: "+names[index][0]);
        }
        String originalName=AuditFixCheck.class.getName().replace('.','/')+"$ProtectionSetterBody";byte[] original;
        try(var input=AuditFixCheck.class.getClassLoader().getResourceAsStream(originalName+".class")){
            require(input!=null,"actual custom entity override bytes unavailable");original=input.readAllBytes();
        }
        // Isolate only the declarative match records. This does not exercise public declaration registration,
        // install an Agent, or grant any Core guard, source or invocation authority.
        Object previousEntries=entries.get(null),isolatedEntries=Array.newInstance(entryType,declarations.size());
        for(int index=0;index<declarations.size();index++)Array.set(isolatedEntries,index,declarations.get(index));
        byte[] changed;
        try{entries.set(null,isolatedEntries);changed=(byte[])transform.invoke(null,AuditFixCheck.class.getClassLoader(),originalName,null,original);}
        finally{entries.set(null,previousEntries);}
        require(changed!=null,"production dispatch boundary did not weave the actual entity overrides");
        var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(node,0);int hooks=0;
        for(var method:node.methods)if(selected.containsKey(method.name)){
            int guarded=0;boolean sawGuard=false;
            for(var instruction:method.instructions){
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("dispatchAllowed")){
                    require(call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESTATIC&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Z"),"setter entry hook changed its production contract");
                    var branch=call.getNext();while(branch!=null&&branch.getOpcode()<0)branch=branch.getNext();
                    var refusal=branch==null?null:branch.getNext();while(refusal!=null&&refusal.getOpcode()<0)refusal=refusal.getNext();
                    require(branch instanceof org.objectweb.asm.tree.JumpInsnNode jump&&jump.getOpcode()==org.objectweb.asm.Opcodes.IFNE
                            &&refusal!=null&&refusal.getOpcode()==org.objectweb.asm.Opcodes.RETURN,"setter refusal does not return before the original body");
                    // Only the executable copy uses this test predicate. The original hook above remains the production evidence.
                    call.owner=AuditFixCheck.class.getName().replace('.','/');call.name="protectionSetterAllowed";
                    sawGuard=true;guarded++;hooks++;
                }else if(!sawGuard&&instruction instanceof org.objectweb.asm.tree.FieldInsnNode)
                    throw new AssertionError("custom setter field effect precedes its dispatch guard: "+method.name);
                else if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESPECIAL
                        &&call.name.equals(method.name))throw new AssertionError("custom setter check unexpectedly delegates to super");
            }
            require(guarded==1,"custom setter must contain exactly one production dispatch guard: "+method.name);
        }
        require(hooks==selected.size(),"production dispatch boundary missed or duplicated a custom setter");
        // Execute only the independent setter bodies. Removing their entity superclass and unused constructors
        // avoids manufacturing a game entity or initializing Minecraft merely to check these entry instructions.
        node.superName="java/lang/Object";node.access&=~org.objectweb.asm.Opcodes.ACC_ABSTRACT;
        node.nestHostClass=null;node.nestMembers=null;node.innerClasses.clear();node.outerClass=null;node.outerMethod=null;node.outerMethodDesc=null;
        node.methods.removeIf(method->method.name.equals("<init>"));
        String alias="dev.ronova.pro.validation.CheckedProtectionSetterBody";
        var writer=new org.objectweb.asm.ClassWriter(0);
        node.accept(new org.objectweb.asm.commons.ClassRemapper(writer,new org.objectweb.asm.commons.SimpleRemapper(originalName,alias.replace('.','/'))));
        byte[] isolated=writer.toByteArray();
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(alias,isolated,0,isolated.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked.getSuperclass()==Object.class,"setter execution must remain an isolated method body");
        Field calls=checked.getField("bodyCalls"),failure=checked.getField("originalFailure");
        try{
            for(var entry:selected.entrySet()){
                Object body=U.allocateInstance(checked);Method method=checked.getMethod(entry.getKey(),entry.getValue());
                Object value=entry.getValue()==float.class?(Object)Float.valueOf(3.25F):Integer.valueOf(150);
                Field state=checked.getField(entry.getValue()==float.class?"absorptionValue":"fireValue");Object before=state.get(body);
                setterExpectedReceiver=body;setterExpectedOperation=entry.getKey();setterExpectedValue=value;
                setterAllow=false;setterGuardCalls=0;failure.set(body,new AssertionError("denied setter body must not execute"));
                method.invoke(body,value);
                require(setterGuardCalls==1&&calls.getInt(body)==0&&state.get(body).equals(before),"denied custom setter executed its original body or changed state");
                setterAllow=true;setterGuardCalls=0;failure.set(body,null);method.invoke(body,value);
                require(setterGuardCalls==1&&calls.getInt(body)==1&&state.get(body).equals(value),"allowed custom setter changed its original count, argument or effect");
                for(Throwable originalFailure:List.of(new IllegalStateException("actual_setter_runtime_failure"),new AssertionError("actual_setter_error"))){
                    int prior=calls.getInt(body);setterGuardCalls=0;failure.set(body,originalFailure);
                    try{method.invoke(body,value);throw new AssertionError("custom setter lost its original Throwable");}
                    catch(InvocationTargetException thrown){require(thrown.getCause()==originalFailure,"custom setter replaced its original Throwable");}
                    require(setterGuardCalls==1&&calls.getInt(body)==prior+1,"failing custom setter body or guard ran more or less than once");
                }
            }
            require(guards.get(null)==null,"isolated setter check installed Core protection authority");
        }finally{setterExpectedReceiver=null;setterExpectedValue=null;setterExpectedOperation=null;}
        System.out.println("PROTECTION_SETTER_BYTECODE_PASS actual_Entity_LivingEntity_contracts isolated_declaration_records_restored production_DispatchBoundary_custom_no_super_overrides receiver_operation_Float_Integer entry_void_refusal_before_effect isolated_body_allowed_once_RuntimeException_Error_identity Core_guards_uninstalled no_public_declaration_registration_Agent_installation_game_protection_or_native_override_claim");
    }
    /** Verify the real FileDispatcher method changed by the current resource transformer. */
    private static void resourceBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.ResourceBoundary");
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        String target="sun.nio.ch.FileDispatcherImpl";
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("sun.nio.ch."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();
                        if(name.equals(target)){
                            byte[] changed=(byte[])transform.invoke(null,null,name.replace('.','/'),bytes);
                            require(changed!=null,"actual FileDispatcher method was not transformed");bytes=changed;
                        }
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        Class<?> actual=Class.forName(target,false,loader);require(actual.getClassLoader()==loader,"actual FileDispatcher class was not defined");actual.getDeclaredMethods();
        System.out.println("RESOURCE_BYTECODE_VERIFY_PASS actual_JDK_FileDispatcher_duplicate JVM_verifier_only no_native_IO_runtime_claim");
        capabilityBytecode();
    }
    /** Load actual Forge handler bodies with the carrier-call wrapper; this is only a verifier check. */
    private static void carrierBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentActionBoundary");
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader");
        Method weave=boundary.getDeclaredMethod("weave",methodType,String.class);weave.setAccessible(true);
        Class<?> writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Constructor<?> writerConstructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);writerConstructor.setAccessible(true);
        String target="net.minecraftforge.items.ItemStackHandler";
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(target))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);Object node=nodeType.getConstructor().newInstance();
                        Object reader=readerType.getConstructor(byte[].class).newInstance((Object)input.readAllBytes());
                        readerType.getMethod("accept",visitor,int.class).invoke(reader,node,0);int changed=0;
                        for(Object method:(List<?>)nodeType.getField("methods").get(node)){
                            String methodName=(String)methodType.getField("name").get(method);int access=methodType.getField("access").getInt(method);
                            if(methodName.startsWith("<")||(access&(Modifier.STATIC|Modifier.ABSTRACT|Modifier.NATIVE))!=0)continue;
                            weave.invoke(null,method,"carrier-call");changed++;
                        }
                        require(changed>0,"actual handler contained no carrier bodies");Object writer=writerConstructor.newInstance(this,node);
                        nodeType.getMethod("accept",visitor).invoke(node,writer);
                        byte[] bytes=(byte[])Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray").invoke(writer);
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        Class<?> actual=Class.forName(target,false,loader);require(actual.getClassLoader()==loader,"actual handler class was not defined");actual.getDeclaredMethods();
        System.out.println("CARRIER_BYTECODE_VERIFY_PASS actual_Forge_ItemStackHandler_instance_bodies JVM_verifier_only no_action_or_game_runtime_claim");
    }
    public interface EquipmentDelegateBusiness {
        Object route(Object value,long count,double fraction);
        long measure(long count,double fraction);
        void emit(Object value);
    }
    public static final class EquipmentDelegateBody {
        public Object forward(EquipmentDelegateBusiness business,Object value,long count,double fraction){return business.route(value,count,fraction);}
        public long wide(EquipmentDelegateBusiness business,long count,double fraction){return business.measure(count,fraction);}
        public void fire(EquipmentDelegateBusiness business,Object value){business.emit(value);}
        public Object caught(EquipmentDelegateBusiness business,Object value,long count,double fraction){
            try{return business.route(value,count,fraction);}catch(RuntimeException original){return original;}
        }
    }
    /** Exercise the production wrapper around real non-SAM interface calls.
     * Core callbacks stay uninstalled; this does not fabricate equipment authority. */
    private static void carrierDelegateBytecode()throws Exception{
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Field installed=bridge.getDeclaredField("equipmentInvocations");installed.setAccessible(true);
        require(installed.get(null)==null,"delegate body fallback requires uninstalled Core invocation handles");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentActionBoundary");
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader");
        Method weave=boundary.getDeclaredMethod("weave",methodType,String.class);weave.setAccessible(true);
        Class<?> writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Constructor<?> writerConstructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);writerConstructor.setAccessible(true);
        String originalName=EquipmentDelegateBody.class.getName().replace('.','/');Object node=nodeType.getConstructor().newInstance();
        try(var input=EquipmentDelegateBody.class.getResourceAsStream("/"+originalName+".class")){
            require(input!=null,"actual delegate body bytes unavailable");
            Object reader=readerType.getConstructor(byte[].class).newInstance((Object)input.readAllBytes());readerType.getMethod("accept",visitor,int.class).invoke(reader,node,0);
        }
        int bodies=0;for(Object method:(List<?>)nodeType.getField("methods").get(node)){
            String name=(String)methodType.getField("name").get(method);if(name.startsWith("<"))continue;
            weave.invoke(null,method,"carrier-call");bodies++;
        }
        require(bodies==4,"actual delegate body selection changed");Object writer=writerConstructor.newInstance(AuditFixCheck.class.getClassLoader(),node);
        nodeType.getMethod("accept",visitor).invoke(node,writer);
        byte[] bytes=(byte[])Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray").invoke(writer);
        var actual=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(actual,0);
        String business=EquipmentDelegateBusiness.class.getName().replace('.','/');int invokes=0,observations=0;
        for(var method:actual.methods)if(!method.name.startsWith("<")){
            int original=0,observed=0;
            for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call){
                if(call.owner.equals(business)){require(call.getOpcode()==org.objectweb.asm.Opcodes.INVOKEINTERFACE,"original custom interface dispatch was replaced");original++;}
                if(call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("beginEquipmentInvocation")
                        &&call.getPrevious() instanceof org.objectweb.asm.tree.LdcInsnNode phase&&phase.cst.equals("carrier-delegate")){
                    observed++;boolean reached=false;
                    for(var next=call.getNext();next!=null;next=next.getNext()){
                        if(next.getOpcode()<0)continue;
                        if(next instanceof org.objectweb.asm.tree.MethodInsnNode target){require(target.owner.equals(business),"business call witness was followed by another invocation");reached=true;break;}
                        require(next instanceof org.objectweb.asm.tree.VarInsnNode,"business call witness lost its straight local reload segment");
                    }
                    require(reached,"business call witness lost its original invoke");
                }
            }
            require(original==1&&observed==1,"actual business call was missed or duplicated:"+method.name);invokes+=original;observations+=observed;
        }
        actual.nestHostClass=null;actual.nestMembers=null;actual.innerClasses.clear();actual.outerClass=null;actual.outerMethod=null;actual.outerMethodDesc=null;
        String alias="dev.ronova.pro.CheckedCarrierDelegateBody";var remapped=new org.objectweb.asm.ClassWriter(0);
        actual.accept(new org.objectweb.asm.commons.ClassRemapper(remapped,new org.objectweb.asm.commons.SimpleRemapper(originalName,alias.replace('.','/'))));
        byte[] isolated=remapped.toByteArray();Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(alias,isolated,0,isolated.length);resolveClass(result);return result;}
        }.defineBody();Object receiver=checked.getConstructor().newInstance();
        Method forward=checked.getMethod("forward",EquipmentDelegateBusiness.class,Object.class,long.class,double.class),wide=checked.getMethod("wide",EquipmentDelegateBusiness.class,long.class,double.class),fire=checked.getMethod("fire",EquipmentDelegateBusiness.class,Object.class);
        Object input=new Object(),output=new Object();Object[] returned={output};long count=0x100000003L,measured=0xfedcba9876543210L;double fraction=-2.5D;int[] calls=new int[3];Throwable[] failure=new Throwable[1];
        EquipmentDelegateBusiness callback=new EquipmentDelegateBusiness(){
            private void fail(){if(failure[0] instanceof RuntimeException thrown)throw thrown;if(failure[0] instanceof Error thrown)throw thrown;}
            public Object route(Object value,long actualCount,double actualFraction){calls[0]++;require(value==input&&actualCount==count&&Double.doubleToRawLongBits(actualFraction)==Double.doubleToRawLongBits(fraction),"custom route operands changed");fail();return returned[0];}
            public long measure(long actualCount,double actualFraction){calls[1]++;require(actualCount==count&&Double.doubleToRawLongBits(actualFraction)==Double.doubleToRawLongBits(fraction),"custom wide operands changed");fail();return measured;}
            public void emit(Object value){calls[2]++;require(value==input,"custom void operand changed");fail();}
        };
        require(forward.invoke(receiver,callback,input,count,fraction)==output&&calls[0]==1,"custom delegate return identity/count changed");
        returned[0]=null;require(forward.invoke(receiver,callback,input,count,fraction)==null&&calls[0]==2,"custom delegate null return/count changed");returned[0]=output;
        require(((Long)wide.invoke(receiver,callback,count,fraction))==measured&&calls[1]==1,"custom delegate wide result/count changed");
        require(fire.invoke(receiver,callback,input)==null&&calls[2]==1,"custom delegate void result/count changed");
        List<Method> methods=List.of(forward,wide,fire);Object[][] arguments={{callback,input,count,fraction},{callback,count,fraction},{callback,input}};
        for(Throwable original:List.of(new IllegalStateException("actual_custom_delegate_failure"),new AssertionError("actual_custom_delegate_error"))){
            failure[0]=original;
            for(int at=0;at<methods.size();at++){
                int before=calls[at];try{methods.get(at).invoke(receiver,arguments[at]);throw new AssertionError("custom delegate lost its original Throwable");}
                catch(InvocationTargetException thrown){require(thrown.getCause()==original,"custom delegate replaced its original Throwable");}
                require(calls[at]==before+1,"failing custom delegate ran more or less than once");
            }
        }
        RuntimeException original=new IllegalStateException("actual_custom_delegate_caught");failure[0]=original;int before=calls[0];
        require(checked.getMethod("caught",EquipmentDelegateBusiness.class,Object.class,long.class,double.class).invoke(receiver,callback,input,count,fraction)==original&&calls[0]==before+1,"delegate cleanup bypassed the original catch handler");
        Object[] combined=carrierDelegateCombinedBody();Class<?> combinedType=(Class<?>)combined[0];
        var combinedImage=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader((byte[])combined[1]).accept(combinedImage,0);
        int slotCalls=0,carrierEntries=0,delegateSites=0,executionCalls=0;
        for(var method:combinedImage.methods)if(method.name.equals("forward"))for(var instruction:method.instructions){
            if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode phase&&phase.cst.equals("carrier-delegate"))delegateSites++;
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call){
                if(call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("beginEquipmentSlotCall"))slotCalls++;
                if(call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("beginEquipmentInvocation"))carrierEntries++;
                if(call.owner.equals("dev/ronova/pro/bootstrap/CodeSourceBridge")&&call.name.equals("executionBefore"))executionCalls++;
            }
        }
        require(slotCalls==1&&carrierEntries==1&&delegateSites==0&&executionCalls>0&&((Object[][])combined[2]).length==4,
                "final Slot/Action/Execution composition duplicated or lost the original call witness");
        failure[0]=null;before=calls[0];Object combinedReceiver=combinedType.getConstructor().newInstance();
        require(combinedType.getMethod("forward",EquipmentDelegateBusiness.class,Object.class,long.class,double.class)
                .invoke(combinedReceiver,callback,input,count,fraction)==output&&calls[0]==before+1,"final combined body changed the original business invocation");
        require(installed.get(null)==null,"isolated custom delegate check installed Core authority");
        System.out.println("EQUIPMENT_CARRIER_DELEGATE_BYTECODE_PASS production_EquipmentActionBoundary_and_ControlClassWriter isolated_actual_non_SAM_interface_body original_invokes="+invokes+" observations="+observations+" reference_long_double_void_arguments_results_once original_RuntimeException_Error_and_catch_identity Bridge_uninstalled_null_token_fallback final_Slot_Action_Execution_single_witness no_live_equipment_or_native_delegation_authority_claim");
    }
    /** Final combined image and its actual emitted offsets, also consumed by
     * the Native carrier-call-site fixture without generating another matrix. */
    public static Object[] carrierDelegateCombinedBody()throws Exception{
        String originalName=EquipmentDelegateBody.class.getName().replace('.','/'),alias="dev.ronova.pro.CheckedCarrierDelegateCombinedBody";
        var renamed=new org.objectweb.asm.tree.ClassNode();
        try(var input=EquipmentDelegateBody.class.getResourceAsStream("/"+originalName+".class")){
            require(input!=null,"actual combined delegate body bytes unavailable");new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(renamed,0);
        }
        renamed.nestHostClass=null;renamed.nestMembers=null;renamed.innerClasses.clear();renamed.outerClass=null;renamed.outerMethod=null;renamed.outerMethodDesc=null;
        var remapped=new org.objectweb.asm.ClassWriter(0);renamed.accept(new org.objectweb.asm.commons.ClassRemapper(remapped,new org.objectweb.asm.commons.SimpleRemapper(originalName,alias.replace('.','/'))));
        byte[] image=remapped.toByteArray();
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode"),instructionType=Class.forName("jdk.internal.org.objectweb.asm.tree.AbstractInsnNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Constructor<?> readerConstructor=readerType.getConstructor(byte[].class),writerConstructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);writerConstructor.setAccessible(true);
        Method read=readerType.getMethod("accept",visitor,int.class),accept=nodeType.getMethod("accept",visitor),write=Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray");
        Object node=nodeType.getConstructor().newInstance();read.invoke(readerConstructor.newInstance((Object)image),node,0);
        Method slot=Class.forName("dev.ronova.pro.agent.EquipmentSlotBoundary").getDeclaredMethod("weave",String.class,methodType,boolean.class);slot.setAccessible(true);
        for(Object method:(List<?>)nodeType.getField("methods").get(node))if(methodType.getField("name").get(method).equals("forward"))slot.invoke(null,alias.replace('.','/'),method,true);
        Object writer=writerConstructor.newInstance(AuditFixCheck.class.getClassLoader(),node);accept.invoke(node,writer);image=(byte[])write.invoke(writer);
        // The real pipeline writes between passes. Re-read their recomputed
        // limits/frames before ExecutionBoundary's operand analysis as well.
        node=nodeType.getConstructor().newInstance();read.invoke(readerConstructor.newInstance((Object)image),node,0);
        Method action=Class.forName("dev.ronova.pro.agent.EquipmentActionBoundary").getDeclaredMethod("weave",methodType,String.class);action.setAccessible(true);
        for(Object method:(List<?>)nodeType.getField("methods").get(node))if(!((String)methodType.getField("name").get(method)).startsWith("<"))action.invoke(null,method,"carrier-call");
        writer=writerConstructor.newInstance(AuditFixCheck.class.getClassLoader(),node);accept.invoke(node,writer);image=(byte[])write.invoke(writer);
        node=nodeType.getConstructor().newInstance();read.invoke(readerConstructor.newInstance((Object)image),node,0);
        Map<String,Module[][]> direct=new LinkedHashMap<>();Method opcode=instructionType.getMethod("getOpcode");
        for(Object method:(List<?>)nodeType.getField("methods").get(node)){
            String name=(String)methodType.getField("name").get(method);if(name.startsWith("<"))continue;
            int count=0;for(Object instruction:(Iterable<?>)methodType.getField("instructions").get(method))if((int)opcode.invoke(instruction)>=0)count++;
            Module[][] rows=new Module[count][];Arrays.setAll(rows,ignored->new Module[0]);direct.put(name+(String)methodType.getField("desc").get(method),rows);
        }
        Method execution=Class.forName("dev.ronova.pro.agent.ExecutionBoundary").getDeclaredMethod("weave",nodeType,Map.class,Map.class,Module.class);execution.setAccessible(true);
        Object woven=execution.invoke(null,node,direct,Map.of(),AuditFixCheck.class.getModule());
        writer=writerConstructor.newInstance(AuditFixCheck.class.getClassLoader(),node);accept.invoke(node,writer);byte[] finished=(byte[])write.invoke(writer);
        Method layout=woven.getClass().getDeclaredMethod("layout");layout.setAccessible(true);Object[][] positions=(Object[][])layout.invoke(woven);
        Class<?> result=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> type=defineClass(alias,finished,0,finished.length);resolveClass(type);return type;}
        }.defineBody();return new Object[]{result,finished,positions};
    }
    public static final class SlotKeyBody implements Comparable<Object> {
        public int code,writes;public Object expected;public RuntimeException failure;
        public SlotKeyBody(int code,Object expected){this.code=code;this.expected=expected;}
        private int selected(){return code;}
        @Override public int hashCode(){if(failure!=null)throw failure;return selected();}
        @Override public boolean equals(Object other){return other==expected;}
        @Override public int compareTo(Object other){return other==expected?code:-code;}
        public int mutate(){writes++;return code;}
        public synchronized int locked(){return code;}
    }
    private static final Object SLOT_KEY_TOKEN=new Object();
    private static Object SLOT_KEY_RECEIVER,SLOT_KEY_ARGUMENT,SLOT_KEY_RESULT;private static String SLOT_KEY_NAME;
    private static Throwable SLOT_KEY_FAILURE;private static int SLOT_KEY_BEGINS,SLOT_KEY_ENDS,SLOT_KEY_READS,SLOT_KEY_UNKNOWN;
    private static Object slotKeyBytecodeBegin(Object receiver,Class<?> declaring,String name,String descriptor,Object[] arguments){
        require(receiver==SLOT_KEY_RECEIVER&&declaring==receiver.getClass()&&name.equals(SLOT_KEY_NAME),"key entry changed its actual receiver or method");
        require(arguments.length==(name.equals("hashCode")?0:1)&&(arguments.length==0||arguments[0]==SLOT_KEY_ARGUMENT),"key entry changed its actual operand");
        require(descriptor.equals(name.equals("hashCode")?"()I":name.equals("equals")?"(Ljava/lang/Object;)Z":"(Ljava/lang/Object;)I"),"key entry changed its exact descriptor");
        SLOT_KEY_BEGINS++;return SLOT_KEY_TOKEN;
    }
    private static void slotKeyBytecodeEnd(Object token,Object result){require(token==SLOT_KEY_TOKEN,"key result changed its token");SLOT_KEY_RESULT=result;SLOT_KEY_ENDS++;}
    private static void slotKeyBytecodeFailed(Object token,Throwable original){require(token==SLOT_KEY_TOKEN,"key failure changed its token");SLOT_KEY_FAILURE=original;}
    private static void slotKeyBytecodeField(Object token,Object holder,Class<?> symbolic,String name,String descriptor,Object value){require(token==SLOT_KEY_TOKEN,"key dependency changed its token");SLOT_KEY_READS++;}
    private static void slotKeyBytecodeUnknown(Object token){require(token==SLOT_KEY_TOKEN,"key side effect changed its token");SLOT_KEY_UNKNOWN++;}
    /** Production key/helper weaving around one natural Java key body. */
    public static void slotKeyBytecode()throws Exception{
        String original=SlotKeyBody.class.getName().replace('.','/'),alias="dev.ronova.pro.CheckedSlotKeyBody";
        var renamed=new org.objectweb.asm.tree.ClassNode();
        try(var input=SlotKeyBody.class.getResourceAsStream("/"+original+".class")){
            require(input!=null,"actual custom key body unavailable");new org.objectweb.asm.ClassReader(input.readAllBytes()).accept(renamed,0);
        }
        renamed.nestHostClass=null;renamed.nestMembers=null;renamed.innerClasses.clear();renamed.outerClass=null;renamed.outerMethod=null;renamed.outerMethodDesc=null;
        var remapped=new org.objectweb.asm.ClassWriter(0);renamed.accept(new org.objectweb.asm.commons.ClassRemapper(remapped,new org.objectweb.asm.commons.SimpleRemapper(original,alias.replace('.','/'))));
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Object node=nodeType.getConstructor().newInstance(),reader=readerType.getConstructor(byte[].class).newInstance((Object)remapped.toByteArray());readerType.getMethod("accept",visitor,int.class).invoke(reader,node,0);
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentSlotBoundary");Method key=boundary.getDeclaredMethod("weaveKey",String.class,methodType);key.setAccessible(true);
        Method helper=boundary.getDeclaredMethod("weave",String.class,methodType,boolean.class);helper.setAccessible(true);
        Method action=Class.forName("dev.ronova.pro.agent.EquipmentActionBoundary").getDeclaredMethod("weave",methodType,String.class);action.setAccessible(true);
        for(Object method:(List<?>)nodeType.getField("methods").get(node)){
            String name=(String)methodType.getField("name").get(method);if(name.startsWith("<"))continue;
            if(Set.of("hashCode","equals","compareTo").contains(name))key.invoke(null,alias.replace('.','/'),method);
            else helper.invoke(null,alias.replace('.','/'),method,true);
            action.invoke(null,method,"carrier-call");
        }
        Constructor<?> constructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);constructor.setAccessible(true);Object writer=constructor.newInstance(AuditFixCheck.class.getClassLoader(),node);nodeType.getMethod("accept",visitor).invoke(node,writer);
        byte[] bytes=(byte[])Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray").invoke(writer);
        var combined=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(combined,0);int keyEntries=0,delegateSites=0,originalSelected=0;
        for(var method:combined.methods)for(var instruction:method.instructions){
            if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode phase&&phase.cst.equals("carrier-delegate"))delegateSites++;
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call){
                if(call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("beginEquipmentSlotKey"))keyEntries++;
                if(method.name.equals("hashCode")&&call.owner.equals(alias.replace('.','/'))&&call.name.equals("selected"))originalSelected++;
            }
        }
        require(keyEntries==3&&delegateSites==0&&originalSelected==1,"key/helper/action composition duplicated or lost the original Java key body");
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> body(){Class<?> result=defineClass(alias,bytes,0,bytes.length);resolveClass(result);return result;}
        }.body();Object operand=new Object(),receiver=checked.getConstructor(int.class,Object.class).newInstance(37,operand);
        Method hash=checked.getMethod("hashCode"),equal=checked.getMethod("equals",Object.class),compare=checked.getMethod("compareTo",Object.class);
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);Field slots=controls.getDeclaredField("equipmentSlots");slots.setAccessible(true);Object saved=slots.get(null);
        Field invocations=controls.getDeclaredField("equipmentInvocations");invocations.setAccessible(true);require(invocations.get(null)==null,"key bytecode check requires uninstalled equipment invocation authority");
        MethodType[] types={MethodType.methodType(Object.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Class.class,String.class,String.class,Object.class),
                MethodType.methodType(void.class,Object.class,Object.class,int.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class,Object.class,Object.class,boolean.class),
                MethodType.methodType(void.class,Object.class,Object.class,String.class,String.class,Object.class),MethodType.methodType(void.class,Object.class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class),
                MethodType.methodType(Object.class,Object.class,Object.class,Class.class,String.class,String.class,int.class,Object[].class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class),
                MethodType.methodType(Object.class,Object.class,Class.class,String.class,String.class,Object[].class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class),
                MethodType.methodType(Object.class,Object.class,Class.class,String.class,String.class,Object[].class),MethodType.methodType(void.class,Object.class,Object.class),MethodType.methodType(void.class,Object.class,Throwable.class)};
        MethodHandle[] callbacks=new MethodHandle[types.length];for(int at=0;at<types.length;at++)callbacks[at]=types[at].returnType()==void.class?MethodHandles.empty(types[at]):MethodHandles.dropArguments(MethodHandles.constant(Object.class,SLOT_KEY_TOKEN),0,types[at].parameterArray());
        var lookup=MethodHandles.lookup();callbacks[1]=lookup.findStatic(AuditFixCheck.class,"slotKeyBytecodeField",types[1]);callbacks[5]=lookup.findStatic(AuditFixCheck.class,"slotKeyBytecodeUnknown",types[5]);
        callbacks[14]=lookup.findStatic(AuditFixCheck.class,"slotKeyBytecodeBegin",types[14]);callbacks[15]=lookup.findStatic(AuditFixCheck.class,"slotKeyBytecodeEnd",types[15]);callbacks[16]=lookup.findStatic(AuditFixCheck.class,"slotKeyBytecodeFailed",types[16]);
        try{
            slots.set(null,null);require((Integer)hash.invoke(receiver)==37&&Boolean.TRUE.equals(equal.invoke(receiver,operand))&&(Integer)compare.invoke(receiver,operand)==37,"uninstalled key callbacks changed natural results");
            SLOT_KEY_RECEIVER=receiver;SLOT_KEY_ARGUMENT=operand;SLOT_KEY_BEGINS=0;SLOT_KEY_ENDS=0;SLOT_KEY_READS=0;SLOT_KEY_UNKNOWN=0;SLOT_KEY_FAILURE=null;slots.set(null,callbacks);
            SLOT_KEY_NAME="hashCode";require((Integer)hash.invoke(receiver)==37&&Integer.valueOf(37).equals(SLOT_KEY_RESULT),"natural hash result changed");
            SLOT_KEY_NAME="equals";require(Boolean.TRUE.equals(equal.invoke(receiver,operand))&&Boolean.TRUE.equals(SLOT_KEY_RESULT),"natural equality result changed");
            SLOT_KEY_NAME="compareTo";require((Integer)compare.invoke(receiver,operand)==37&&Integer.valueOf(37).equals(SLOT_KEY_RESULT),"natural comparison result changed");
            require(SLOT_KEY_BEGINS==3&&SLOT_KEY_ENDS==3&&SLOT_KEY_READS>0&&SLOT_KEY_UNKNOWN==0,"natural key methods were replayed or lost their dependencies");
            RuntimeException failure=new IllegalStateException("actual_key_failure");checked.getField("failure").set(receiver,failure);SLOT_KEY_NAME="hashCode";
            try{hash.invoke(receiver);throw new AssertionError("key original failure disappeared");}catch(InvocationTargetException thrown){require(thrown.getCause()==failure&&SLOT_KEY_FAILURE==failure,"key failure identity changed");}
            checked.getField("failure").set(receiver,null);int unknown=SLOT_KEY_UNKNOWN;
            require((Integer)checked.getMethod("mutate").invoke(receiver)==37&&checked.getField("writes").getInt(receiver)==1&&SLOT_KEY_UNKNOWN>unknown,"key helper write did not invalidate its trace exactly once");
            unknown=SLOT_KEY_UNKNOWN;require((Integer)checked.getMethod("locked").invoke(receiver)==37&&SLOT_KEY_UNKNOWN>unknown,"implicit synchronized monitor kept reusable key semantics");
        }finally{slots.set(null,saved);SLOT_KEY_RECEIVER=null;SLOT_KEY_ARGUMENT=null;SLOT_KEY_RESULT=null;SLOT_KEY_FAILURE=null;}
        System.out.println("EQUIPMENT_SLOT_KEY_BYTECODE_PASS exact_custom_key_hash_equals_compare_original_results_and_Throwable_identity field_read_hooks helper_write_and_synchronized_unresolved uninstalled_callback_fallback single_KEY_Slot_Action_layer no_live_equipment_authority_claim");
    }
    /** Verify the slot read hooks on the actual game getter, without constructing an entity. */
    private static void equipmentSlotBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentSlotBoundary");
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader");
        Method weave=boundary.getDeclaredMethod("weave",methodType);weave.setAccessible(true);
        Class<?> writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Constructor<?> writerConstructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);writerConstructor.setAccessible(true);
        String target="net.minecraft.world.entity.Mob",descriptor="(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(target))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);Object node=nodeType.getConstructor().newInstance();
                        Object reader=readerType.getConstructor(byte[].class).newInstance((Object)input.readAllBytes());
                        readerType.getMethod("accept",visitor,int.class).invoke(reader,node,0);int changed=0;
                        for(Iterator<?> methods=((List<?>)nodeType.getField("methods").get(node)).iterator();methods.hasNext();){
                            Object method=methods.next();
                            String methodName=(String)methodType.getField("name").get(method),methodDescriptor=(String)methodType.getField("desc").get(method);
                            // An isolated loader changes runtime package identity. Unrelated Mob
                            // methods use package access to LivingEntity and are outside this check.
                            if(!descriptor.equals(methodDescriptor)||!methodName.equals("getItemBySlot")&&!methodName.equals("m_6844_")){methods.remove();continue;}
                            weave.invoke(null,method);changed++;
                        }
                        require(changed==1,"actual Mob getter body missing or ambiguous");Object writer=writerConstructor.newInstance(this,node);
                        nodeType.getMethod("accept",visitor).invoke(node,writer);
                        byte[] bytes=(byte[])Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray").invoke(writer);
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        Class<?> actual=Class.forName(target,false,loader);require(actual.getClassLoader()==loader,"actual Mob class was not defined");actual.getDeclaredMethods();
        System.out.println("EQUIPMENT_SLOT_BYTECODE_VERIFY_PASS actual_Mob_original_getter_isolated_body JVM_verifier_only no_slot_action_or_game_runtime_claim");
    }
    /** Actual helper bodies cover reference, primitive and void returns without inventing a getter. */
    private static void equipmentSlotHelperBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentSlotBoundary");
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Class<?> methodType=Class.forName("jdk.internal.org.objectweb.asm.tree.MethodNode"),readerType=Class.forName("jdk.internal.org.objectweb.asm.ClassReader");
        Method weave=boundary.getDeclaredMethod("weave",String.class,methodType,boolean.class);weave.setAccessible(true);
        Class<?> writerType=Class.forName("dev.ronova.pro.agent.ControlClassWriter");
        Constructor<?> writerConstructor=writerType.getDeclaredConstructor(ClassLoader.class,nodeType);writerConstructor.setAccessible(true);
        String target="net.minecraftforge.items.ItemStackHandler";
        Set<String> selected=Set.of("getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;","validateSlotIndex(I)V","getSlotLimit(I)I","isItemValid(ILnet/minecraft/world/item/ItemStack;)Z");
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.equals(target))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);Object node=nodeType.getConstructor().newInstance();
                        Object reader=readerType.getConstructor(byte[].class).newInstance((Object)input.readAllBytes());
                        readerType.getMethod("accept",visitor,int.class).invoke(reader,node,0);int changed=0;
                        for(Object method:(List<?>)nodeType.getField("methods").get(node)){
                            String methodName=(String)methodType.getField("name").get(method),descriptor=(String)methodType.getField("desc").get(method);
                            if(selected.contains(methodName+descriptor)){weave.invoke(null,name.replace('.','/'),method,true);changed++;}
                        }
                        require(changed==selected.size(),"actual ItemStackHandler helper bodies missing");Object writer=writerConstructor.newInstance(this,node);
                        nodeType.getMethod("accept",visitor).invoke(node,writer);
                        byte[] bytes=(byte[])Class.forName("jdk.internal.org.objectweb.asm.ClassWriter").getMethod("toByteArray").invoke(writer);
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        Class<?> actual=Class.forName(target,false,loader);require(actual.getClassLoader()==loader,"actual helper class was not defined");actual.getDeclaredMethods();
        System.out.println("EQUIPMENT_SLOT_HELPER_BYTECODE_VERIFY_PASS actual_Forge_ItemStackHandler_reference_primitive_void_bodies JVM_verifier_only no_native_dispatch_or_game_runtime_claim");
    }
    /** Keep the actual Forge publication frames produced by the current Mixin plugin. */
    private static void capabilityBytecode()throws Exception{
        Class<?> weaver=Class.forName("dev.ronova.pro.EquipmentCapabilityWeaver");
        Method apply=weaver.getMethod("apply",org.objectweb.asm.tree.ClassNode.class);
        Set<String> targets=Set.of("net.minecraftforge.common.capabilities.CapabilityProvider","net.minecraftforge.common.capabilities.CapabilityDispatcher","net.minecraftforge.common.util.LazyOptional");
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("net.minecraftforge.common.capabilities.")&&!name.startsWith("net.minecraftforge.common.util.LazyOptional"))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();
                        if(targets.contains(name)){
                            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,org.objectweb.asm.ClassReader.EXPAND_FRAMES);apply.invoke(null,node);
                            var writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);node.accept(writer);bytes=writer.toByteArray();
                        }
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        for(String name:targets){Class<?> actual=Class.forName(name,false,loader);require(actual.getClassLoader()==loader,"actual capability class was not defined");actual.getDeclaredMethods();}
        System.out.println("CAPABILITY_BYTECODE_VERIFY_PASS actual_Forge_provider_dispatcher_lazy_optional JVM_verifier_only no_game_capability_runtime_claim");
    }
    /** Actual transformed Optional bodies, renamed solely for an isolated JVM body check. */
    private static void capabilityCallbackBytecode()throws Exception{
        capabilityBytecode();
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Field callbacks=bridge.getDeclaredField("capabilityCallbacks");callbacks.setAccessible(true);
        require(callbacks.get(null)==null,"isolated callback body check requires the Bridge callbacks to remain uninstalled");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentOptionalBoundary");
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        byte[] original;try(var input=Optional.class.getResourceAsStream("Optional.class")){
            require(input!=null,"actual JDK Optional bytes unavailable");original=input.readAllBytes();
        }
        byte[] changed=(byte[])transform.invoke(null,null,"java/util/Optional",original);
        require(changed!=null,"actual JDK Optional callback bodies were not transformed");
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),writer=Class.forName("jdk.internal.org.objectweb.asm.ClassWriter");
        Class<?> remapper=Class.forName("jdk.internal.org.objectweb.asm.commons.Remapper"),simple=Class.forName("jdk.internal.org.objectweb.asm.commons.SimpleRemapper"),classRemapper=Class.forName("jdk.internal.org.objectweb.asm.commons.ClassRemapper");
        String checkedName="dev.ronova.pro.validation.CheckedOptional";
        Object output=writer.getConstructor(int.class).newInstance(0);
        Object rename=simple.getConstructor(String.class,String.class).newInstance("java/util/Optional",checkedName.replace('.','/'));
        Object mapping=classRemapper.getConstructor(visitor,remapper).newInstance(output,rename);
        Object input=reader.getConstructor(byte[].class).newInstance((Object)changed);reader.getMethod("accept",visitor,int.class).invoke(input,mapping,0);
        byte[] renamed=(byte[])writer.getMethod("toByteArray").invoke(output);
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(checkedName,renamed,0,renamed.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked.getClassLoader()!=null&&checked.getName().equals(checkedName),"renamed Optional must remain an isolated class body");checked.getDeclaredMethods();
        Method of=checked.getMethod("of",Object.class),emptyFactory=checked.getMethod("empty"),get=checked.getMethod("get"),isEmpty=checked.getMethod("isEmpty");
        Method accept=checked.getMethod("ifPresent",java.util.function.Consumer.class),map=checked.getMethod("map",java.util.function.Function.class),filter=checked.getMethod("filter",java.util.function.Predicate.class);
        Method flatMap=checked.getMethod("flatMap",java.util.function.Function.class),orElseGet=checked.getMethod("orElseGet",java.util.function.Supplier.class),orElseThrow=checked.getMethod("orElseThrow",java.util.function.Supplier.class);
        Object value=new Object(),mapped=new Object(),fallback=new Object(),present=of.invoke(null,value),empty=emptyFactory.invoke(null),nested=of.invoke(null,mapped);
        require(present.getClass()==checked&&empty.getClass()==checked&&get.invoke(present)==value,"actual remapped factories lost Optional value identity");
        int[] calls={0};
        java.util.function.Consumer<Object> consumer=actual->{require(actual==value,"ifPresent changed the actual callback operand");calls[0]++;};
        accept.invoke(present,consumer);accept.invoke(empty,consumer);require(calls[0]==1,"ifPresent must invoke the original callback once only for a present value");
        calls[0]=0;java.util.function.Function<Object,Object> function=actual->{require(actual==value,"map changed the actual callback operand");calls[0]++;return mapped;};
        Object mappedOptional=map.invoke(present,function),emptyMapped=map.invoke(empty,function);
        require(calls[0]==1&&get.invoke(mappedOptional)==mapped&&Boolean.TRUE.equals(isEmpty.invoke(emptyMapped)),"map changed callback count or original return");
        calls[0]=0;java.util.function.Predicate<Object> predicate=actual->{require(actual==value,"filter changed the actual callback operand");calls[0]++;return true;};
        require(filter.invoke(present,predicate)==present&&filter.invoke(empty,predicate)==empty&&calls[0]==1,"filter changed true/empty identity or callback count");
        java.util.function.Predicate<Object> rejected=actual->{require(actual==value,"filter changed the rejected callback operand");calls[0]++;return false;};
        require(Boolean.TRUE.equals(isEmpty.invoke(filter.invoke(present,rejected)))&&calls[0]==2,"filter false result changed the original empty return");
        calls[0]=0;java.util.function.Function<Object,Object> flatten=actual->{require(actual==value,"flatMap changed the actual callback operand");calls[0]++;return nested;};
        require(flatMap.invoke(present,flatten)==nested&&Boolean.TRUE.equals(isEmpty.invoke(flatMap.invoke(empty,flatten)))&&calls[0]==1,"flatMap changed the actual alias return or callback count");
        calls[0]=0;java.util.function.Supplier<Object> supplier=()->{calls[0]++;return fallback;};
        require(orElseGet.invoke(present,supplier)==value&&orElseGet.invoke(empty,supplier)==fallback&&calls[0]==1,"orElseGet changed the original value or supplier count");
        calls[0]=0;Exception returnedFailure=new Exception("actual_optional_supplier_throwable");
        java.util.function.Supplier<Exception> failureFactory=()->{calls[0]++;return returnedFailure;};
        require(orElseThrow.invoke(present,failureFactory)==value&&calls[0]==0,"present orElseThrow unexpectedly invoked its supplier");
        optionalCallbackFailure(orElseThrow,empty,failureFactory,returnedFailure,calls);
        RuntimeException originalFailure=new IllegalStateException("actual_optional_callback_failure");
        java.util.function.Consumer<Object> failedConsumer=actual->{calls[0]++;throw originalFailure;};
        java.util.function.Function<Object,Object> failedFunction=actual->{calls[0]++;throw originalFailure;};
        java.util.function.Predicate<Object> failedPredicate=actual->{calls[0]++;throw originalFailure;};
        java.util.function.Supplier<Object> failedSupplier=()->{calls[0]++;throw originalFailure;};
        optionalCallbackFailure(accept,present,failedConsumer,originalFailure,calls);
        optionalCallbackFailure(map,present,failedFunction,originalFailure,calls);
        optionalCallbackFailure(filter,present,failedPredicate,originalFailure,calls);
        optionalCallbackFailure(flatMap,present,failedFunction,originalFailure,calls);
        optionalCallbackFailure(orElseGet,empty,failedSupplier,originalFailure,calls);
        optionalCallbackFailure(orElseThrow,empty,failedSupplier,originalFailure,calls);
        require(callbacks.get(null)==null,"isolated Optional body check installed callback authority");
        System.out.println("CAPABILITY_CALLBACK_BYTECODE_PASS renamed_isolated_JVM_body_check actual_JDK_Optional_six_transformed_callback_bodies Bridge_callbacks_uninstalled original_callbacks_once values_returns_and_Throwable_identity_preserved no_actual_java_base_retransform_or_game_runtime_claim");
    }
    /** Only this package's actual JDK callback images and one ordinary worker fallback. */
    @SuppressWarnings("unchecked") private static void equipmentAsyncCarrierBytecode()throws Exception{
        Class<?> tasks=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null),controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Field installed=tasks.getDeclaredField("equipmentAsync"),callbacks=controls.getDeclaredField("capabilityCallbacks");installed.setAccessible(true);callbacks.setAccessible(true);
        require(installed.get(null)==null&&callbacks.get(null)==null,"async body check requires uninstalled Core callback handles");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.JdkTaskBoundaryContract");
        Method transform=boundary.getMethod("transform",String.class,byte[].class);
        String cf="java/util/concurrent/CompletableFuture",adapter="java/util/concurrent/Executors$RunnableAdapter",hookOwner="dev/ronova/pro/bootstrap/TaskBridge";
        String hook="invokeEquipmentCallback",hookDescriptor="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
        Set<String> required=Set.of("java/util/concurrent/FutureTask",adapter,"java/util/concurrent/ForkJoinTask$AdaptedRunnable",
                "java/util/concurrent/ForkJoinTask$AdaptedRunnableAction","java/util/concurrent/ForkJoinTask$RunnableExecuteAction","java/util/concurrent/ForkJoinTask$AdaptedCallable",
                "java/util/concurrent/ThreadPoolExecutor","java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy");
        Set<String> candidates=new TreeSet<>(required),checked=new TreeSet<>(),linked=new HashSet<>();
        for(String name:(Set<String>)boundary.getMethod("targets").invoke(null))if(name.equals(cf)||name.startsWith(cf+"$"))candidates.add(name);
        byte[] adapterBytes=null;int callbackSites=0;
        for(String name:candidates){
            Class<?> actual=Class.forName(name.replace('/','.'),false,null);
            require(actual.getClassLoader()==null&&actual.getModule()==Object.class.getModule(),"callback image must be an actual java.base class: "+name);
            byte[] original;try(var input=actual.getResourceAsStream(name.substring(name.lastIndexOf('/')+1)+".class")){
                require(input!=null,"actual JDK callback bytes unavailable: "+name);original=input.readAllBytes();
            }
            var before=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(original).accept(before,0);int expected=equipmentAsyncCallbackSites(before);
            if(expected==0){require(!required.contains(name),"required actual callback body missing: "+name);continue;}
            byte[] changed=(byte[])transform.invoke(null,name,original);require(changed!=null,"production task boundary did not transform actual callback image: "+name);
            var after=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(after,0);
            require(after.name.equals(name),"production task transform changed class identity: "+name);int hooks=0;
            for(var method:after.methods)for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals(hookOwner)){
                require(call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESTATIC,"task hook must remain static: "+name+"."+call.name);
                String selector=call.name+call.desc;
                if(linked.add(selector))try{MethodHandles.publicLookup().findStatic(tasks,call.name,MethodType.fromMethodDescriptorString(call.desc,null));}
                catch(NoSuchMethodException|IllegalAccessException failure){throw new AssertionError("production task hook cannot link: "+selector,failure);}
                if(call.name.equals(hook)){require(call.desc.equals(hookDescriptor),"actual async callback hook descriptor changed: "+name);hooks++;}
            }
            require(hooks==expected,"actual async callback sites were lost or duplicated: "+name+" "+hooks+"/"+expected);
            checked.add(name);callbackSites+=hooks;if(name.equals(adapter))adapterBytes=original;
        }
        require(checked.contains(cf)&&checked.containsAll(required)&&adapterBytes!=null,"current callback family was not transformed completely");
        System.out.println("EQUIPMENT_ASYNC_CARRIER_BYTECODE_PASS actual_current_JDK_CF_FutureTask_RunnableAdapter_ForkJoin_adapters_TPE_CallerRuns production_transform hooks_linked images="+checked.size()+" callback_sites="+callbackSites+" no_actual_java_base_installation_or_native_graph_claim");
        equipmentAsyncRunnableAdapter(adapterBytes,tasks);
        require(installed.get(null)==null&&callbacks.get(null)==null,"ordinary async fallback installed Core callback authority");
        System.out.println("EQUIPMENT_ASYNC_CARRIER_FALLBACK_PASS isolated_actual_Executors_RunnableAdapter original_fields_ctor_call real_singleThreadExecutor Core_handles_uninstalled_no_ticket Runnable_once_worker_thread result_and_ExecutionException_cause_identity no_equipment_authority_claim");
    }
    private static int equipmentAsyncCallbackSites(org.objectweb.asm.tree.ClassNode node){
        Set<String> selectors=Set.of("java/util/function/Function#apply(Ljava/lang/Object;)Ljava/lang/Object;","java/util/function/Consumer#accept(Ljava/lang/Object;)V",
                "java/util/function/Supplier#get()Ljava/lang/Object;","java/lang/Runnable#run()V","java/util/concurrent/Callable#call()Ljava/lang/Object;",
                "java/util/function/BiFunction#apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;","java/util/function/BiConsumer#accept(Ljava/lang/Object;Ljava/lang/Object;)V",
                "java/util/function/Predicate#test(Ljava/lang/Object;)Z");
        int count=0;for(var method:node.methods)if(!method.name.startsWith("<"))for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.getOpcode()==org.objectweb.asm.Opcodes.INVOKEINTERFACE&&selectors.contains(call.owner+"#"+call.name+call.desc))count++;
        return count;
    }
    /** Keep the actual adapter's fields, constructor and call instructions; change only its identity/nest metadata. */
    @SuppressWarnings("unchecked") private static void equipmentAsyncRunnableAdapter(byte[] original,Class<?> tasks)throws Exception{
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),writer=Class.forName("jdk.internal.org.objectweb.asm.ClassWriter");
        Class<?> nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode"),resultBoundary=Class.forName("dev.ronova.pro.agent.TaskResultBoundary");
        Method instrument=resultBoundary.getDeclaredMethod("instrument",nodeType);instrument.setAccessible(true);
        Object node=nodeType.getConstructor().newInstance(),input=reader.getConstructor(byte[].class).newInstance((Object)original);reader.getMethod("accept",visitor,int.class).invoke(input,node,8);
        instrument.invoke(null,node);Object output=writer.getConstructor(int.class).newInstance(3);nodeType.getMethod("accept",visitor).invoke(node,output);
        byte[] changed=(byte[])writer.getMethod("toByteArray").invoke(output);
        var actual=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(actual,0);
        require(equipmentAsyncCallbackSites(actual)==0,"isolated adapter retained the unwrapped original Runnable call");
        int hooks=0;for(var method:actual.methods)for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                &&call.owner.equals("dev/ronova/pro/bootstrap/TaskBridge")&&call.name.equals("invokeEquipmentCallback"))hooks++;
        require(hooks==1,"actual RunnableAdapter must contain one production callback hook");
        node=nodeType.getConstructor().newInstance();input=reader.getConstructor(byte[].class).newInstance((Object)changed);reader.getMethod("accept",visitor,int.class).invoke(input,node,0);
        nodeType.getField("nestHostClass").set(node,null);
        String alias="dev.ronova.pro.validation.CheckedAsyncRunnableAdapter",originalName="java/util/concurrent/Executors$RunnableAdapter";
        Class<?> remapper=Class.forName("jdk.internal.org.objectweb.asm.commons.Remapper"),simple=Class.forName("jdk.internal.org.objectweb.asm.commons.SimpleRemapper"),classRemapper=Class.forName("jdk.internal.org.objectweb.asm.commons.ClassRemapper");
        Object renamedOutput=writer.getConstructor(int.class).newInstance(0),rename=simple.getConstructor(String.class,String.class).newInstance(originalName,alias.replace('.','/'));
        Object mapping=classRemapper.getConstructor(visitor,remapper).newInstance(renamedOutput,rename);nodeType.getMethod("accept",visitor).invoke(node,mapping);
        byte[] renamed=(byte[])writer.getMethod("toByteArray").invoke(renamedOutput);
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(alias,renamed,0,renamed.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked.getClassLoader()!=null&&checked.getNestHost()==checked&&Callable.class.isAssignableFrom(checked),"actual adapter must remain an isolated Callable body");checked.getDeclaredMethods();
        Constructor<?> constructor=checked.getDeclaredConstructor(Runnable.class,Object.class);constructor.setAccessible(true);
        MethodHandles.publicLookup().findStatic(tasks,"invokeEquipmentCallback",MethodType.methodType(Object.class,Object.class,Object.class,Object.class,String.class));
        var calls=new java.util.concurrent.atomic.AtomicInteger();var worker=new java.util.concurrent.atomic.AtomicReference<Thread>();Thread caller=Thread.currentThread();Object expected=new Object();
        Callable<Object> ordinary=(Callable<Object>)constructor.newInstance((Runnable)()->{calls.incrementAndGet();worker.set(Thread.currentThread());},expected);
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try{
            require(executor.submit(ordinary).get(10,TimeUnit.SECONDS)==expected,"actual RunnableAdapter changed the original result identity");
            require(calls.get()==1&&worker.get()!=null&&worker.get()!=caller,"actual RunnableAdapter must run its original Runnable once on the worker");
            calls.set(0);worker.set(null);RuntimeException originalFailure=new IllegalStateException("actual_async_runnable_failure");
            Callable<Object> failed=(Callable<Object>)constructor.newInstance((Runnable)()->{calls.incrementAndGet();worker.set(Thread.currentThread());throw originalFailure;},expected);
            try{executor.submit(failed).get(10,TimeUnit.SECONDS);throw new AssertionError("actual async Runnable failure was lost");}
            catch(ExecutionException failure){require(failure.getCause()==originalFailure,"actual async Runnable Throwable identity changed: "+failure.getCause());}
            require(calls.get()==1&&worker.get()!=null&&worker.get()!=caller,"failing Runnable was repeated or executed on the caller");
        }finally{executor.shutdownNow();require(executor.awaitTermination(10,TimeUnit.SECONDS),"isolated adapter worker did not terminate");}
    }
    /** Actual standard composition factories and hidden bodies, with the real Bridge fallback left uninstalled. */
    @SuppressWarnings("unchecked") private static void equipmentFunctionBytecode()throws Exception{
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);Field callbacks=bridge.getDeclaredField("capabilityCallbacks");callbacks.setAccessible(true);
        require(callbacks.get(null)==null,"composition body check requires uninstalled Bridge callbacks");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentFunctionBoundary");Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        Class<?> function=equipmentFunctionAlias(java.util.function.Function.class,transform,4),consumer=equipmentFunctionAlias(java.util.function.Consumer.class,transform,2),predicate=equipmentFunctionAlias(java.util.function.Predicate.class,transform,5);
        try(var input=java.util.function.Supplier.class.getResourceAsStream("Supplier.class")){
            require(input!=null,"actual Supplier bytes unavailable");require(transform.invoke(null,null,"java/util/function/Supplier",input.readAllBytes())==null,"Supplier must not acquire a synthetic composition body");
        }
        Object input=new Object(),middle=new Object(),output=new Object();List<String> order=new ArrayList<>();
        java.util.function.Function<Object,Object> before=value->{require(value==input,"compose changed before input");order.add("before");return middle;};
        java.util.function.Function<Object,Object> finish=value->{require(value==middle,"compose changed intermediate identity");order.add("finish");return output;};
        var composed=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"compose",finish,before);
        require(composed.apply(input)==output&&order.equals(List.of("before","finish")),"Function.compose changed callback order/count/result");
        order.clear();java.util.function.Function<Object,Object> first=value->{require(value==input,"andThen changed first input");order.add("first");return middle;};
        java.util.function.Function<Object,Object> after=value->{require(value==middle,"andThen changed intermediate identity");order.add("after");return output;};
        var chained=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"andThen",first,after);
        require(chained.apply(input)==output&&order.equals(List.of("first","after")),"Function.andThen changed callback order/count/result");

        order.clear();java.util.function.Consumer<Object> accept=value->{require(value==input,"Consumer changed original value");order.add("first");};
        java.util.function.Consumer<Object> next=value->{require(value==input,"Consumer changed second value");order.add("after");};
        var accepted=(java.util.function.Consumer<Object>)equipmentFunctionCombination(consumer,"andThen",accept,next);accepted.accept(input);
        require(order.equals(List.of("first","after")),"Consumer.andThen changed callback order/count");

        java.util.function.Predicate<Object> yes=value->{require(value==input,"Predicate changed original value");order.add("yes");return true;};
        java.util.function.Predicate<Object> no=value->{require(value==input,"Predicate changed original value");order.add("no");return false;};
        order.clear();var and=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"and",no,yes);
        require(!and.test(input)&&order.equals(List.of("no")),"Predicate.and lost false short circuit");
        order.clear();and=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"and",yes,no);
        require(!and.test(input)&&order.equals(List.of("yes","no")),"Predicate.and changed second operand or result");
        order.clear();and=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"and",yes,yes);
        require(and.test(input)&&order.equals(List.of("yes","yes")),"Predicate.and changed true result/count");
        order.clear();var or=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"or",yes,no);
        require(or.test(input)&&order.equals(List.of("yes")),"Predicate.or lost true short circuit");
        order.clear();or=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"or",no,yes);
        require(or.test(input)&&order.equals(List.of("no","yes")),"Predicate.or changed second operand or result");
        order.clear();or=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"or",no,no);
        require(!or.test(input)&&order.equals(List.of("no","no")),"Predicate.or changed false result/count");
        order.clear();var negate=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"negate",yes,null);
        require(!negate.test(input)&&order.equals(List.of("yes")),"Predicate.negate changed true operand count/result");
        order.clear();negate=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"negate",no,null);
        require(negate.test(input)&&order.equals(List.of("no")),"Predicate.negate changed false operand count/result");

        RuntimeException original=new IllegalStateException("actual_composed_callback_failure");Error originalError=new AssertionError("actual_composed_callback_error");
        java.util.function.Function<Object,Object> failed=value->{order.add("failed");throw original;};
        java.util.function.Function<Object,Object> failedAfter=value->{order.add("failedAfter");throw originalError;};
        var composeFailed=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"compose",finish,failed);
        equipmentComposedFailure(()->composeFailed.apply(input),original,order,"failed");
        var chainFailed=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"andThen",failed,after);
        equipmentComposedFailure(()->chainFailed.apply(input),original,order,"failed");
        var composeLastFailed=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"compose",failedAfter,before);
        equipmentComposedFailure(()->composeLastFailed.apply(input),originalError,order,"before","failedAfter");
        var chainLastFailed=(java.util.function.Function<Object,Object>)equipmentFunctionCombination(function,"andThen",first,failedAfter);
        equipmentComposedFailure(()->chainLastFailed.apply(input),originalError,order,"first","failedAfter");
        java.util.function.Consumer<Object> failedAccept=value->{order.add("failed");throw original;},failedNext=value->{order.add("failedAfter");throw originalError;};
        var acceptFailed=(java.util.function.Consumer<Object>)equipmentFunctionCombination(consumer,"andThen",failedAccept,next);
        equipmentComposedFailure(()->acceptFailed.accept(input),original,order,"failed");
        var acceptLastFailed=(java.util.function.Consumer<Object>)equipmentFunctionCombination(consumer,"andThen",accept,failedNext);
        equipmentComposedFailure(()->acceptLastFailed.accept(input),originalError,order,"first","failedAfter");
        java.util.function.Predicate<Object> failedTest=value->{order.add("failed");throw original;};
        for(String operation:List.of("and","or","negate")){
            var testFailed=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,operation,failedTest,operation.equals("negate")?null:yes);
            equipmentComposedFailure(()->testFailed.test(input),original,order,"failed");
        }
        var andLastFailed=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"and",yes,failedTest);
        equipmentComposedFailure(()->andLastFailed.test(input),original,order,"yes","failed");
        var orLastFailed=(java.util.function.Predicate<Object>)equipmentFunctionCombination(predicate,"or",no,failedTest);
        equipmentComposedFailure(()->orLastFailed.test(input),original,order,"no","failed");
        require(callbacks.get(null)==null,"composition body check installed callback authority");
        System.out.println("EQUIPMENT_FUNCTION_COMPOSITION_BYTECODE_PASS isolated_actual_JDK_factories_and_hidden_bodies eleven_Bridge_calls callbacks_uninstalled original_order_intermediate_identity_short_circuit_RuntimeException_Error_identity_preserved no_actual_java_base_retransform_or_equipment_authority_claim");
    }
    /** The production execution weaver on one real original SAM body, with no published execution plan. */
    @SuppressWarnings("unchecked") private static void equipmentDefinedCallbackBytecode()throws Exception{
        Class<?> tasks=Class.forName("dev.ronova.pro.bootstrap.TaskBridge",true,null),controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null),nativeControl=Class.forName("dev.ronova.pro.bootstrap.NativeControl",true,null);
        Field async=tasks.getDeclaredField("equipmentAsync"),defined=tasks.getDeclaredField("equipmentDefined"),callbacks=controls.getDeclaredField("capabilityCallbacks");async.setAccessible(true);defined.setAccessible(true);callbacks.setAccessible(true);
        require(!(Boolean)nativeControl.getMethod("available").invoke(null)&&async.get(null)==null&&defined.get(null)==null&&callbacks.get(null)==null,"defined body fallback requires uninstalled native/Core callback authority");
        String originalName="java/util/concurrent/Executors$RunnableAdapter",selector="call()Ljava/lang/Object;";Class<?> original=Class.forName(originalName.replace('/','.'),false,null);byte[] bytes;
        require(original.getClassLoader()==null&&original.getModule()==Object.class.getModule(),"defined callback body must be the actual java.base RunnableAdapter");
        try(var input=original.getResourceAsStream("Executors$RunnableAdapter.class")){require(input!=null,"actual defined RunnableAdapter bytes unavailable");bytes=input.readAllBytes();}
        var before=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(before,0);int instructions=0,originalCallbacks=0;
        for(var method:before.methods)if((method.name+method.desc).equals(selector))for(var instruction:method.instructions){
            if(instruction.getOpcode()>=0)instructions++;
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.getOpcode()==org.objectweb.asm.Opcodes.INVOKEINTERFACE&&call.itf&&call.owner.equals("java/lang/Runnable")&&call.name.equals("run")&&call.desc.equals("()V"))originalCallbacks++;
        }
        require(instructions>0&&originalCallbacks==1,"actual RunnableAdapter.call must retain one original Runnable SAM site");
        Module[][] sources=new Module[instructions][];Arrays.setAll(sources,ignored->new Module[0]);
        Class<?> visitor=Class.forName("jdk.internal.org.objectweb.asm.ClassVisitor"),reader=Class.forName("jdk.internal.org.objectweb.asm.ClassReader"),writer=Class.forName("jdk.internal.org.objectweb.asm.ClassWriter"),nodeType=Class.forName("jdk.internal.org.objectweb.asm.tree.ClassNode");
        Object node=nodeType.getConstructor().newInstance(),input=reader.getConstructor(byte[].class).newInstance((Object)bytes);reader.getMethod("accept",visitor,int.class).invoke(input,node,0);
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.ExecutionBoundary");Method weave=boundary.getDeclaredMethod("weave",nodeType,Map.class,Map.class,Module.class);weave.setAccessible(true);
        Object woven=weave.invoke(null,node,Map.of(selector,sources),Map.of(),original.getModule());Method present=woven.getClass().getDeclaredMethod("present");present.setAccessible(true);
        require((Boolean)present.invoke(woven),"production execution weaver skipped the actual RunnableAdapter.call body");
        Class<?> productionWriter=Class.forName("dev.ronova.pro.agent.ControlClassWriter");Constructor<?> createWriter=productionWriter.getDeclaredConstructor(ClassLoader.class,nodeType);createWriter.setAccessible(true);
        Object output=createWriter.newInstance(null,node);nodeType.getMethod("accept",visitor).invoke(node,output);byte[] changed=(byte[])writer.getMethod("toByteArray").invoke(output);
        var actual=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(actual,0);int hooks=0;
        require(equipmentAsyncCallbackSites(actual)==0,"defined callback weaver retained the original unwrapped Runnable SAM call");
        for(var method:actual.methods)for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("dev/ronova/pro/bootstrap/CodeSourceBridge")&&call.name.equals("executionEquipmentCallback")){
            require((method.name+method.desc).equals(selector)&&call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESTATIC&&call.desc.equals("(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),"defined callback hook changed the actual original SAM site/signature");hooks++;
        }
        require(hooks==1,"actual defined RunnableAdapter must contain one execution callback hook");
        // Reparse the production writer's computed frames before the identity-only remap.
        node=nodeType.getConstructor().newInstance();input=reader.getConstructor(byte[].class).newInstance((Object)changed);reader.getMethod("accept",visitor,int.class).invoke(input,node,0);nodeType.getField("nestHostClass").set(node,null);
        String alias="dev.ronova.pro.validation.CheckedDefinedRunnableAdapter";
        Class<?> remapper=Class.forName("jdk.internal.org.objectweb.asm.commons.Remapper"),simple=Class.forName("jdk.internal.org.objectweb.asm.commons.SimpleRemapper"),classRemapper=Class.forName("jdk.internal.org.objectweb.asm.commons.ClassRemapper");
        Object renamedOutput=writer.getConstructor(int.class).newInstance(0),rename=simple.getConstructor(String.class,String.class).newInstance(originalName,alias.replace('.','/'));
        Object mapping=classRemapper.getConstructor(visitor,remapper).newInstance(renamedOutput,rename);nodeType.getMethod("accept",visitor).invoke(node,mapping);byte[] renamed=(byte[])writer.getMethod("toByteArray").invoke(renamedOutput);
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(alias,renamed,0,renamed.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked.getClassLoader()!=null&&checked.getNestHost()==checked&&Callable.class.isAssignableFrom(checked),"defined RunnableAdapter must remain an isolated original Callable body");checked.getDeclaredMethods();
        Constructor<?> constructor=checked.getDeclaredConstructor(Runnable.class,Object.class);constructor.setAccessible(true);int[] calls={0};Object expected=new Object();
        Callable<Object> ordinary=(Callable<Object>)constructor.newInstance((Runnable)()->calls[0]++,expected);
        require(ordinary.call()==expected&&calls[0]==1,"defined execution token-null fallback changed original Runnable count/result identity");
        calls[0]=0;RuntimeException originalFailure=new IllegalStateException("actual_defined_runnable_failure");
        Callable<Object> failed=(Callable<Object>)constructor.newInstance((Runnable)()->{calls[0]++;throw originalFailure;},expected);
        try{failed.call();throw new AssertionError("defined execution fallback lost the original Runnable failure");}
        catch(RuntimeException failure){require(failure==originalFailure,"defined execution fallback replaced original Throwable identity");}
        require(calls[0]==1,"defined execution fallback repeated the failing original Runnable");
        require(!(Boolean)nativeControl.getMethod("available").invoke(null)&&async.get(null)==null&&defined.get(null)==null&&callbacks.get(null)==null,"defined body fallback installed native/Core authority");
        System.out.println("EQUIPMENT_DEFINED_CALLBACK_BYTECODE_PASS production_ExecutionBoundary_weave_ControlClassWriter isolated_actual_JDK_Executors_RunnableAdapter_call original_SAM_site_once empty_method_sources native_Core_uninstalled_no_published_Plan_token_null_fallback original_callback_result_Throwable_identity no_live_defined_authority_or_game_runtime_claim");
    }
    /** Only this batch's actual Bi composition bodies; no old unary composition matrix. */
    @SuppressWarnings("unchecked") private static void equipmentBiCompositionBytecode()throws Exception{
        Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);Field callbacks=bridge.getDeclaredField("capabilityCallbacks");callbacks.setAccessible(true);
        require(callbacks.get(null)==null,"Bi composition body check requires uninstalled Bridge callbacks");
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.EquipmentFunctionBoundary");Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        Class<?> function=equipmentFunctionAlias(java.util.function.BiFunction.class,transform,1,1),consumer=equipmentFunctionAlias(java.util.function.BiConsumer.class,transform,0,2);
        Object left=new Object(),right=new Object(),middle=new Object(),output=new Object();List<String> order=new ArrayList<>();
        java.util.function.BiFunction<Object,Object,Object> first=(one,two)->{require(one==left&&two==right,"BiFunction changed original arguments");order.add("first");return middle;};
        java.util.function.Function<Object,Object> after=value->{require(value==middle,"BiFunction.andThen changed actual intermediate identity");order.add("after");return output;};
        var chained=(java.util.function.BiFunction<Object,Object,Object>)equipmentFunctionCombination(function,"andThen",first,after);
        require(chained.apply(left,right)==output&&order.equals(List.of("first","after")),"BiFunction.andThen changed callback order/count/result");
        order.clear();java.util.function.BiFunction<Object,Object,Object> nullFirst=(one,two)->{require(one==null&&two==right,"BiFunction changed null/second arguments");order.add("first");return null;};
        java.util.function.Function<Object,Object> nullAfter=value->{require(value==null,"BiFunction replaced the actual null intermediate");order.add("after");return null;};
        var nullChained=(java.util.function.BiFunction<Object,Object,Object>)equipmentFunctionCombination(function,"andThen",nullFirst,nullAfter);
        require(nullChained.apply(null,right)==null&&order.equals(List.of("first","after")),"BiFunction.andThen changed null intermediate/return/count");
        order.clear();java.util.function.BiConsumer<Object,Object> accept=(one,two)->{require(one==left&&two==right,"BiConsumer changed original arguments");order.add("first");};
        java.util.function.BiConsumer<Object,Object> next=(one,two)->{require(one==left&&two==right,"BiConsumer changed second callback arguments");order.add("after");};
        var accepted=(java.util.function.BiConsumer<Object,Object>)equipmentFunctionCombination(consumer,"andThen",accept,next);accepted.accept(left,right);
        require(order.equals(List.of("first","after")),"BiConsumer.andThen changed callback order/count/void behavior");
        order.clear();java.util.function.BiConsumer<Object,Object> nullAccept=(one,two)->{require(one==left&&two==null,"BiConsumer changed first/null arguments");order.add("first");};
        java.util.function.BiConsumer<Object,Object> nullNext=(one,two)->{require(one==left&&two==null,"BiConsumer changed second callback null argument");order.add("after");};
        var nullAccepted=(java.util.function.BiConsumer<Object,Object>)equipmentFunctionCombination(consumer,"andThen",nullAccept,nullNext);nullAccepted.accept(left,null);
        require(order.equals(List.of("first","after")),"BiConsumer.andThen changed null argument/count/void behavior");

        RuntimeException original=new IllegalStateException("actual_bi_composed_callback_failure");Error originalError=new AssertionError("actual_bi_composed_callback_error");
        java.util.function.BiFunction<Object,Object,Object> failed=(one,two)->{require(one==left&&two==right,"failed BiFunction changed original arguments");order.add("failed");throw original;};
        java.util.function.Function<Object,Object> failedAfter=value->{require(value==middle,"failed BiFunction after changed actual intermediate");order.add("failedAfter");throw originalError;};
        var chainFailed=(java.util.function.BiFunction<Object,Object,Object>)equipmentFunctionCombination(function,"andThen",failed,after);
        equipmentComposedFailure(()->chainFailed.apply(left,right),original,order,"failed");
        var chainLastFailed=(java.util.function.BiFunction<Object,Object,Object>)equipmentFunctionCombination(function,"andThen",first,failedAfter);
        equipmentComposedFailure(()->chainLastFailed.apply(left,right),originalError,order,"first","failedAfter");
        java.util.function.BiConsumer<Object,Object> failedAccept=(one,two)->{require(one==left&&two==right,"failed BiConsumer changed original arguments");order.add("failed");throw original;};
        java.util.function.BiConsumer<Object,Object> failedNext=(one,two)->{require(one==left&&two==right,"failed BiConsumer after changed original arguments");order.add("failedAfter");throw originalError;};
        var acceptFailed=(java.util.function.BiConsumer<Object,Object>)equipmentFunctionCombination(consumer,"andThen",failedAccept,next);
        equipmentComposedFailure(()->acceptFailed.accept(left,right),original,order,"failed");
        var acceptLastFailed=(java.util.function.BiConsumer<Object,Object>)equipmentFunctionCombination(consumer,"andThen",accept,failedNext);
        equipmentComposedFailure(()->acceptLastFailed.accept(left,right),originalError,order,"first","failedAfter");
        require(callbacks.get(null)==null,"Bi composition body check installed callback authority");
        System.out.println("EQUIPMENT_BI_COMPOSITION_BYTECODE_PASS isolated_actual_JDK_BiFunction_andThen_Function_BiConsumer_andThen_BiConsumer_factories_and_private_hidden_bodies four_Bridge_calls callbacks_uninstalled actual_two_arguments_intermediate_null_void_order_count_RuntimeException_Error_identity_preserved no_actual_java_base_retransform_or_equipment_authority_claim");
    }
    private static Class<?> equipmentFunctionAlias(Class<?> original,Method transform,int expectedHooks)throws Exception{
        return equipmentFunctionAlias(original,transform,expectedHooks,0);
    }
    private static Class<?> equipmentFunctionAlias(Class<?> original,Method transform,int expectedHooks,int expectedBiHooks)throws Exception{
        String originalName=original.getName().replace('.','/'),aliasName="dev.ronova.pro.validation.CheckedComposed"+original.getSimpleName(),aliasInternal=aliasName.replace('.','/');byte[] bytes;
        try(var input=original.getResourceAsStream(original.getSimpleName()+".class")){require(input!=null,"actual "+original.getName()+" bytes unavailable");bytes=input.readAllBytes();}
        byte[] changed=(byte[])transform.invoke(null,null,originalName,bytes);require(changed!=null,"actual composition interface was not transformed: "+original.getName());
        var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(changed).accept(node,0);int hooks=0,biHooks=0;
        require(node.name.equals(originalName)&&node.interfaces.isEmpty(),"actual function interface identity/layout changed");
        node.name=aliasInternal;node.interfaces.add(originalName);
        if(node.signature!=null)node.signature+="L"+originalName+(original==java.util.function.Function.class?"<TT;TR;>":original==java.util.function.BiFunction.class?"<TT;TU;TR;>":original==java.util.function.BiConsumer.class?"<TT;TU;>":"<TT;>")+";";
        for(var method:node.methods)for(var instruction:method.instructions){
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("capabilityComposed")){
                require(call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESTATIC&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),"composition hook signature changed");hooks++;
            }
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("capabilityComposedBi")){
                require(call.getOpcode()==org.objectweb.asm.Opcodes.INVOKESTATIC&&call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"),"Bi composition hook signature changed");biHooks++;
            }
            if(instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy&&indy.bsmArgs.length==3&&indy.bsmArgs[1] instanceof org.objectweb.asm.Handle implementation&&implementation.getOwner().equals(originalName)){
                indy.bsmArgs[1]=new org.objectweb.asm.Handle(implementation.getTag(),aliasInternal,implementation.getName(),implementation.getDesc(),implementation.isInterface());
                if(implementation.getTag()==org.objectweb.asm.Opcodes.H_INVOKEINTERFACE){String captured="(L"+originalName+";";require(indy.desc.startsWith(captured),"composition factory did not capture its original this");indy.desc="(L"+aliasInternal+";"+indy.desc.substring(captured.length());}
            }
        }
        require(hooks==expectedHooks&&biHooks==expectedBiHooks,"missing actual composition callback hook: "+original.getName()+" unary="+hooks+" bi="+biHooks);
        // Original method descriptors and callback targets stay JDK interfaces.
        // Only the private implementation owner and its captured this become aliases.
        var writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);byte[] renamed=writer.toByteArray();
        Class<?> checked=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            Class<?> defineBody(){Class<?> result=defineClass(aliasName,renamed,0,renamed.length);resolveClass(result);return result;}
        }.defineBody();
        require(checked.getClassLoader()!=null&&checked.isInterface()&&original.isAssignableFrom(checked),"composition alias must implement the original contract");checked.getDeclaredMethods();return checked;
    }
    @SuppressWarnings("unchecked") private static Object equipmentFunctionCombination(Class<?> alias,String operation,Object first,Object other)throws Exception{
        Class<?> contract=alias.getInterfaces()[0];Object receiver=Proxy.newProxyInstance(alias.getClassLoader(),new Class<?>[]{alias},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "equals"->Boolean.valueOf(proxy==args[0]);case "hashCode"->Integer.valueOf(System.identityHashCode(proxy));case "toString"->alias.getName();default->throw new AssertionError(method);};
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args==null?new Object[0]:args);
            if(contract==java.util.function.Function.class)return ((java.util.function.Function<Object,Object>)first).apply(args[0]);
            if(contract==java.util.function.Consumer.class){((java.util.function.Consumer<Object>)first).accept(args[0]);return null;}
            if(contract==java.util.function.BiFunction.class)return ((java.util.function.BiFunction<Object,Object,Object>)first).apply(args[0],args[1]);
            if(contract==java.util.function.BiConsumer.class){((java.util.function.BiConsumer<Object,Object>)first).accept(args[0],args[1]);return null;}
            if(contract==java.util.function.Predicate.class)return Boolean.valueOf(((java.util.function.Predicate<Object>)first).test(args[0]));throw new AssertionError(contract);
        });
        Method factory=operation.equals("negate")?alias.getMethod(operation):alias.getMethod(operation,contract==java.util.function.BiFunction.class?java.util.function.Function.class:contract);
        Object composed=operation.equals("negate")?factory.invoke(receiver):factory.invoke(receiver,other);
        require(contract.isInstance(composed)&&composed.getClass().isHidden()&&composed.getClass().getNestHost()==alias,"composition did not execute the isolated original factory/hidden body");return composed;
    }
    private static void equipmentComposedFailure(Runnable operation,Throwable original,List<String> order,String... expected){
        order.clear();try{operation.run();throw new AssertionError("composition lost the original callback failure");}catch(Throwable failure){require(failure==original,"composition replaced the original callback Throwable: "+failure);}
        require(order.equals(Arrays.asList(expected)),"composition repeated a callback or continued after failure: "+order);
    }
    private static void optionalCallbackFailure(Method method,Object receiver,Object callback,Throwable original,int[] calls)throws Exception{
        int before=calls[0];
        try{method.invoke(receiver,callback);throw new AssertionError(method.getName()+" lost the original callback exception");}
        catch(InvocationTargetException thrown){require(thrown.getCause()==original,method.getName()+" replaced the original Throwable");}
        require(calls[0]==before+1,method.getName()+" invoked the failing callback more or less than once");
    }
    /** Verify the actual transformed Netty methods without starting another game JVM. */
    private static void networkBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.NetworkBoundary");
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        Set<String> targets=Set.of("io.netty.channel.AbstractChannelHandlerContext","io.netty.channel.AbstractChannelHandlerContext$WriteTask","io.netty.util.concurrent.SingleThreadEventExecutor","io.netty.util.concurrent.RejectedExecutionHandlers$1",
                "io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.MpscChunkedArrayQueue",
                "io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue",
                "io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields","io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields","io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
                "io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueProducerFields","io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueColdProducerFields","io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueConsumerFields");
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("io.netty."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();
                        if(targets.contains(name))bytes=(byte[])transform.invoke(null,this,name.replace('.','/'),bytes);
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        for(String name:targets){Class<?> actual=Class.forName(name,false,loader);require(actual.getClassLoader()==loader,"actual transformed class was not defined");actual.getDeclaredMethods();}
        System.out.println("NETWORK_BYTECODE_VERIFY_PASS actual_Netty_write_WriteTask_executor_default_rejection_and_bounded_MPSC JVM_verifier_only no_stop_or_cleanup_runtime_claim");
        worldRegistryBytecode();
    }
    /** Verify the real registry and iterator bodies changed by the existing Mixin plugin, without a world. */
    private static void worldRegistryBytecode()throws Exception{
        Class<?> weaver=Class.forName("dev.ronova.pro.WorldRegistryWeaver");
        Method apply=weaver.getMethod("apply",org.objectweb.asm.tree.ClassNode.class);
        Method applyIterator=weaver.getMethod("applyIterator",org.objectweb.asm.tree.ClassNode.class);
        String target="it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap";
        String iterator=target+"$MapIterator";
        ClassLoader loader=new ClassLoader(AuditFixCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(!name.startsWith("it.unimi.dsi.fastutil."))return super.loadClass(name,resolve);
                synchronized(getClassLoadingLock(name)){
                    Class<?> result=findLoadedClass(name);
                    if(result==null)try(var input=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                        if(input==null)throw new ClassNotFoundException(name);byte[] bytes=input.readAllBytes();
                        if(name.equals(target)||name.equals(iterator)){
                            var node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,org.objectweb.asm.ClassReader.EXPAND_FRAMES);(name.equals(target)?apply:applyIterator).invoke(null,node);
                            var writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
                            node.accept(writer);bytes=writer.toByteArray();
                        }
                        result=defineClass(name,bytes,0,bytes.length);
                    }catch(java.io.IOException|ReflectiveOperationException failure){throw new ClassNotFoundException(name,failure);}
                    if(resolve)resolveClass(result);return result;
                }
            }
        };
        for(String name:new String[]{target,iterator}){Class<?> actual=Class.forName(name,false,loader);require(actual.getClassLoader()==loader,"actual registry class was not defined");actual.getDeclaredMethods();}
        System.out.println("WORLD_REGISTRY_BYTECODE_VERIFY_PASS actual_fastutil_rehash_remove_shift_and_iterator JVM_verifier_only no_game_mutation_claim");
    }
    /** Prepare from an unselected consumer; stop through genuine console input, then finish below. */
    public static Object networkQueuedStop(Object runtime,Class<?> sender)throws Exception {
        require(runtime!=null&&runtime.getClass().getName().equals("dev.ronova.pro.ProRuntime"),"actual game Runtime required");
        Class<?> runtimeType=runtime.getClass();Field serverField=runtimeType.getDeclaredField("server");serverField.setAccessible(true);
        Object server=serverField.get(runtime);Class<?> serverType=Class.forName("net.minecraft.server.MinecraftServer",false,runtimeType.getClassLoader());
        require(server!=null&&runtimeType.getMethod("get",serverType).invoke(null,server)==runtime,"Runtime is not the actual registered game instance");
        require(sender!=null&&sender.getName().equals("dev.ronova.pro.validation.WorldFixture$NetworkSender")&&sender.getModule().isNamed(),"actual named fixture sender required");
        Class<?> controls=Class.forName("dev.ronova.pro.bootstrap.ControlBridge",true,null);
        Method stopped=controls.getDeclaredMethod("modStopped",Module.class),invoking=controls.getDeclaredMethod("invokingSources");
        stopped.setAccessible(true);invoking.setAccessible(true);
        require(!Boolean.TRUE.equals(stopped.invoke(null,sender.getModule())),"queue submission must precede the real stop");
        require(!Arrays.asList((Module[])invoking.invoke(null)).contains(sender.getModule()),"queued-stop consumer still runs inside the selected fixture source");
        var pending=new NetworkQueuedStop(sender.getModule(),controls);
        try{
            pending.channel.pipeline().addLast(pending.loops,"actual-write",new io.netty.channel.ChannelOutboundHandlerAdapter(){
                public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception{
                    pending.forwarded.add(message);pending.scopes.add(networkSources(pending.current));context.write(message,promise);
                }
            });
            var ready=pending.executor.submit(()->{});require(ready.await(10,TimeUnit.SECONDS)&&ready.isSuccess(),"actual handler executor did not initialize");
            var entered=new CountDownLatch(1);
            pending.executor.execute(()->{entered.countDown();try{pending.release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            require(entered.await(10,TimeUnit.SECONDS),"actual handler executor did not enter its queue barrier");
            pending.result.addListener(result->{
                try{pending.other.writeAndFlush(pending.callback,pending.callbackResult);}
                catch(RuntimeException|Error refused){pending.callbackFailure.compareAndSet(null,refused);throw refused;}
            });
            pending.channel.writeAndFlush(pending.before,pending.beforeResult);
            sender.getMethod("write",Object.class,Object.class,Object.class).invoke(null,pending.channel,pending.message,pending.result);
            pending.channel.writeAndFlush(pending.after,pending.afterResult);
            require(pending.message.refCnt()==1&&!pending.result.isDone(),"original accepted write did not remain pending before stop");
            Field queueField=io.netty.util.concurrent.SingleThreadEventExecutor.class.getDeclaredField("taskQueue");queueField.setAccessible(true);
            Queue<?> queue=(Queue<?>)queueField.get(pending.executor);
            for(Object item:queue)if(item.getClass().getName().equals("io.netty.channel.AbstractChannelHandlerContext$WriteTask")){
                Field message=item.getClass().getDeclaredField("msg"),promise=item.getClass().getDeclaredField("promise");message.setAccessible(true);promise.setAccessible(true);
                if(message.get(item)==pending.message&&promise.get(item)==pending.result){require(pending.task==null,"same accepted write has multiple original tasks");pending.task=item;}
            }
            require(pending.task!=null,"real Netty WriteTask was not queued by the active fixture call");
            synchronized(pending.lock){
                Object receipt=null;
                for(var entry:pending.writes.entrySet())if(((java.lang.ref.Reference<?>)entry.getKey()).get()==pending.task)receipt=entry.getValue();
                require(receipt!=null,"queued original WriteTask has no captured real network receipt");
                Field modules=receipt.getClass().getDeclaredField("modules");modules.setAccessible(true);
                require(Arrays.asList((Module[])modules.get(receipt)).contains(pending.source),"active fixture sender was not captured on the actual WriteTask");
            }
            require(networkSources(pending.current).length==0,"accepted queue publication leaked its source scope");
            System.out.println("NETWORK_QUEUED_STOP_READY actual_active_named_fixture real_WriteTask_pending requires_real_console_ronova_pro_mod_stop_pro_fixture");
            return pending;
        }catch(Exception|Error failure){pending.close();throw failure;}
    }
    /** The selected fixture's own transformed tick cannot serve as this consumer. */
    public static void finishNetworkQueuedStop(Object prepared)throws Exception {
        require(prepared instanceof NetworkQueuedStop,"actual prepared network queue check required");var pending=(NetworkQueuedStop)prepared;
        try{
            Method stopped=pending.controls.getDeclaredMethod("modStopped",Module.class),invoking=pending.controls.getDeclaredMethod("invokingSources");stopped.setAccessible(true);invoking.setAccessible(true);
            require(!Arrays.asList((Module[])invoking.invoke(null)).contains(pending.source),"queued-stop finish still runs inside the selected fixture source");
            require(Boolean.TRUE.equals(stopped.invoke(null,pending.source)),"real console stop has not fenced the named fixture module");
            pending.release.countDown();
            var cleared=pending.executor.submit(()->networkSources(pending.current));require(cleared.await(10,TimeUnit.SECONDS)&&cleared.isSuccess(),"actual executor did not return after the original stopped WriteTask");
            pending.channel.runPendingTasks();pending.other.runPendingTasks();
            require(pending.message.refCnt()==0,"stopped original WriteTask did not release its actual ByteBuf to zero");
            require(pending.result.isDone()&&!pending.result.isSuccess()&&pending.result.cause() instanceof CancellationException
                    &&"NETWORK_OUTBOUND_REJECTED".equals(pending.result.cause().getMessage()),"stopped original WriteTask left its accepted promise unresolved");
            require(pending.beforeResult.isSuccess()&&pending.afterResult.isSuccess()&&pending.forwarded.size()==2
                    &&pending.forwarded.get(0)==pending.before&&pending.forwarded.get(1)==pending.after,"stopped task consumed or denied an unrelated queued write");
            require(pending.scopes.size()==2&&pending.scopes.stream().allMatch(modules->!Arrays.asList(modules).contains(pending.source)),"unrelated original write inherited the stopped task's receipt");
            require(pending.channel.readOutbound()==pending.before&&pending.channel.readOutbound()==pending.after&&pending.channel.readOutbound()==null,"actual outbound delivery changed the unrelated queue items");
            require(pending.callback.refCnt()==1&&!pending.callbackResult.isDone()&&pending.other.readOutbound()==null,
                    "explicitly refused promise listener changed another message or promise before ownership transfer");
            Throwable callbackFailure=pending.callbackFailure.get();
            require(callbackFailure instanceof SecurityException&&"NETWORK_REJECTION_CALLBACK_REENTRY".equals(callbackFailure.getMessage()),
                    "promise listener was not explicitly refused at cleanup reentry: "+callbackFailure);
            require(cleared.get().length==0&&networkSources(pending.current).length==0,"original WriteTask finally leaked its network scope");
            synchronized(pending.lock){
                for(Object key:pending.writes.keySet())require(((java.lang.ref.Reference<?>)key).get()!=pending.task,"completed original WriteTask retained its queue receipt");
                for(Object scope:pending.active){Field modules=scope.getClass().getDeclaredField("modules");modules.setAccessible(true);require(!Arrays.asList((Module[])modules.get(scope)).contains(pending.source),"completed original WriteTask retained an active source scope");}
            }
            for(String name:List.of("ctx","msg","promise")){Field field=pending.task.getClass().getDeclaredField(name);field.setAccessible(true);require(field.get(pending.task)==null,"original WriteTask did not recycle its actual "+name+" field");}
            System.out.println("NETWORK_QUEUED_STOP_PASS actual_active_named_fixture_to_real_Runtime_stop original_WriteTask_run ByteBuf_refCnt_zero promise_rejected receipt_recycled scope_finally unrelated_queue_preserved callback_no_borrow");
        }finally{pending.close();}
    }
    public static Throwable networkQueuedStopCallbackFailure(Object prepared){
        require(prepared instanceof NetworkQueuedStop,"actual prepared network queue check required");return ((NetworkQueuedStop)prepared).callbackFailure.get();
    }
    private static final class NetworkQueuedStop {
        final Module source;final Class<?> controls;final Method current;final Object lock;final Set<?> active;final Map<?,?> writes;
        final io.netty.util.concurrent.DefaultEventExecutorGroup loops=new io.netty.util.concurrent.DefaultEventExecutorGroup(1);
        final io.netty.util.concurrent.EventExecutor executor=loops.next();
        final io.netty.channel.embedded.EmbeddedChannel channel=new io.netty.channel.embedded.EmbeddedChannel(),other=new io.netty.channel.embedded.EmbeddedChannel();
        final io.netty.buffer.ByteBuf message=io.netty.buffer.Unpooled.buffer(1).writeByte(17),before=io.netty.buffer.Unpooled.buffer(1).writeByte(18),after=io.netty.buffer.Unpooled.buffer(1).writeByte(19),callback=io.netty.buffer.Unpooled.buffer(1).writeByte(20);
        final io.netty.channel.ChannelPromise result=channel.newPromise(),beforeResult=channel.newPromise(),afterResult=channel.newPromise(),callbackResult=other.newPromise();
        final java.util.concurrent.atomic.AtomicReference<Throwable> callbackFailure=new java.util.concurrent.atomic.AtomicReference<>();
        final CountDownLatch release=new CountDownLatch(1);final List<Object> forwarded=Collections.synchronizedList(new ArrayList<>());final List<Module[]> scopes=Collections.synchronizedList(new ArrayList<>());
        Object task;
        NetworkQueuedStop(Module source,Class<?> controls)throws Exception{
            this.source=source;this.controls=controls;Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",true,null);
            current=bridge.getDeclaredMethod("currentSources");current.setAccessible(true);
            Field monitor=bridge.getDeclaredField("CHANNELS"),scopes=bridge.getDeclaredField("ACTIVE"),queued=bridge.getDeclaredField("WRITES");monitor.setAccessible(true);scopes.setAccessible(true);queued.setAccessible(true);
            lock=monitor.get(null);active=(Set<?>)scopes.get(null);writes=(Map<?,?>)queued.get(null);
        }
        void close(){
            release.countDown();
            try{
                executor.submit(()->{}).awaitUninterruptibly(10,TimeUnit.SECONDS);channel.finishAndReleaseAll();other.finishAndReleaseAll();
                for(var bytes:List.of(message,before,after,callback))if(bytes.refCnt()>0)bytes.release(bytes.refCnt());
            }finally{loops.shutdownGracefully(0,5,TimeUnit.SECONDS).awaitUninterruptibly(10,TimeUnit.SECONDS);}
        }
    }
    private static String networkPromises(List<io.netty.channel.ChannelPromise> promises){
        synchronized(promises){return promises.stream().map(promise->"done="+promise.isDone()+"/success="+promise.isSuccess()+"/cause="+promise.cause()).toList().toString();}
    }
    private static String networkPeerState(io.netty.channel.local.LocalChannel peer){
        if(peer==null)return "not-initialized";
        try{
            Field inbound=io.netty.channel.local.LocalChannel.class.getDeclaredField("inboundBuffer"),pending=io.netty.channel.local.LocalChannel.class.getDeclaredField("readInProgress");
            inbound.setAccessible(true);pending.setAccessible(true);
            return "active="+peer.isActive()+"/autoRead="+peer.config().isAutoRead()+"/queued="+((Queue<?>)inbound.get(peer)).size()+"/readInProgress="+pending.getBoolean(peer);
        }catch(ReflectiveOperationException unavailable){return "unavailable:"+unavailable;}
    }
    private static String networkClientPending(io.netty.channel.Channel client){
        io.netty.channel.ChannelOutboundBuffer buffer=client.unsafe().outboundBuffer();return buffer==null?"closed":Integer.toString(buffer.size());
    }
    private static Module[] networkSources(Method current)throws ReflectiveOperationException {return (Module[])current.invoke(null);}
    /** Real platform transformer output with a proxy for the VM batch return only. */
    @SuppressWarnings("unchecked")
    private static void startupBatch()throws Exception {
        Class<?> agent=Class.forName("dev.ronova.pro.agent.RecoveryAgent"),reflection=Class.forName("dev.ronova.pro.agent.ReflectionWriteBoundary"),handles=Class.forName("dev.ronova.pro.agent.HandleWriteBoundary");
        Field images=agent.getDeclaredField("platformImages"),returns=agent.getDeclaredField("platformReturns"),bridge=agent.getDeclaredField("bridge"),fieldReady=agent.getDeclaredField("reflectionGuarded"),unsafeReady=agent.getDeclaredField("unsafeGuarded"),guarded=reflection.getDeclaredField("guarded"),handleInstalled=handles.getDeclaredField("INSTALLED");
        for(Field field:List.of(images,returns,bridge,fieldReady,unsafeReady,guarded,handleInstalled))field.setAccessible(true);
        Map<String,Object> actualImages=(Map<String,Object>)images.get(null);Map<String,Object> oldImages=new HashMap<>(actualImages);
        ThreadLocal<Map<String,Object>> local=(ThreadLocal<Map<String,Object>>)returns.get(null);Map<String,Object> originalReturns=local.get(),oldReturns=new HashMap<>(originalReturns);
        Set<String> installedHandles=(Set<String>)handleInstalled.get(null),oldHandles=new HashSet<>(installedHandles);
        Object oldBridge=bridge.get(null);boolean oldField=fieldReady.getBoolean(null),oldUnsafe=unsafeReady.getBoolean(null);int oldGuarded=guarded.getInt(null);
        Method batch=agent.getDeclaredMethod("retransformStartup",java.lang.instrument.Instrumentation.class,List.class,String.class,boolean.class),starting=agent.getDeclaredMethod("platformTransforming",ClassLoader.class,String.class,Class.class),returned=agent.getDeclaredMethod("platformReturned",ClassLoader.class,String.class,Class.class,byte[].class),accepted=agent.getDeclaredMethod("platformAccepted",Class.class);
        Method fieldTransform=reflection.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class),handleTransform=handles.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);
        for(Method method:List.of(batch,starting,returned,accepted,fieldTransform,handleTransform))method.setAccessible(true);
        List<Class<?>> targets=List.of(Field.class,Method.class);Map<Class<?>,byte[]> bytes=new IdentityHashMap<>();
        for(Class<?> type:targets)try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){bytes.put(type,Objects.requireNonNull(input).readAllBytes());}
        List<Class<?>[]> calls=new ArrayList<>();int[] mode={0};
        var api=(java.lang.instrument.Instrumentation)Proxy.newProxyInstance(AuditFixCheck.class.getClassLoader(),new Class<?>[]{java.lang.instrument.Instrumentation.class},(proxy,method,args)->{
            if(!method.getName().equals("retransformClasses"))throw new AssertionError("unexpected startup API:"+method.getName());
            Class<?>[] actuals=((Class<?>[])args[0]).clone();calls.add(actuals);
            for(Class<?> actual:actuals){
                String name=actual.getName().replace('.','/');starting.invoke(null,null,name,actual);
                byte[] output=(byte[])(actual==Field.class?fieldTransform:handleTransform).invoke(null,null,name,bytes.get(actual));
                require(output!=null&&output.length>4,"real platform guard output absent:"+name);
                returned.invoke(null,null,name,actual,output);
            }
            if(mode[0]==0||mode[0]==2&&(actuals.length>1||actuals[0]==Method.class))throw new java.lang.instrument.UnmodifiableClassException("audit actual batch refusal");
            return null;
        });
        try{
            bridge.set(null,null);actualImages.clear();originalReturns.clear();fieldReady.setBoolean(null,false);unsafeReady.setBoolean(null,false);
            try{batch.invoke(null,api,targets,"AUDIT_STARTUP",true);throw new AssertionError("rejected required batch returned success");}
            catch(InvocationTargetException refused){require(refused.getCause() instanceof java.lang.instrument.UnmodifiableClassException,"required batch hid its actual VM refusal");}
            require(calls.size()==1&&Arrays.equals(calls.get(0),targets.toArray(Class<?>[]::new)),"required targets were split or changed before the real batch");
            for(Object image:actualImages.values())require(Boolean.FALSE.equals(invoke(image,"accepted")),"VM-refused image was certified");
            accepted.invoke(null,Field.class);accepted.invoke(null,Method.class);
            require(!fieldReady.getBoolean(null)&&originalReturns.isEmpty(),"rejected batch retained a consumable success receipt");
            mode[0]=1;calls.clear();batch.invoke(null,api,targets,"AUDIT_STARTUP",true);
            require(calls.size()==1&&fieldReady.getBoolean(null),"successful real target batch was split or not accepted");
            for(Object image:actualImages.values())require(Boolean.TRUE.equals(invoke(image,"accepted")),"successful API return lost its exact returned image");
            mode[0]=2;calls.clear();batch.invoke(null,api,targets,"AUDIT_STARTUP_OPTIONAL",false);
            require(calls.size()==3&&calls.get(0).length==2&&calls.get(1).length==1&&calls.get(1)[0]==Field.class&&calls.get(2).length==1&&calls.get(2)[0]==Method.class,"optional batch refusal did not isolate only its loaded targets");
            require(Boolean.TRUE.equals(invoke(actualImages.get("java/lang/reflect/Field"),"accepted"))&&Boolean.FALSE.equals(invoke(actualImages.get("java/lang/reflect/Method"),"accepted")),"optional refusal skipped a recoverable loaded target or certified a refused one");
        }finally{
            actualImages.clear();actualImages.putAll(oldImages);originalReturns.clear();originalReturns.putAll(oldReturns);local.set(originalReturns);
            installedHandles.clear();installedHandles.addAll(oldHandles);guarded.setInt(null,oldGuarded);bridge.set(null,oldBridge);fieldReady.setBoolean(null,oldField);unsafeReady.setBoolean(null,oldUnsafe);
        }
        System.out.println("STARTUP_BATCH_PASS actual_Field_Method_transformer_outputs one_required_batch refused_images_unaccepted failed_receipts_discarded optional_refusal_isolates_loaded_targets proxy_VM_return_only_no_live_install");
    }
    private static void modGroupReturns(String jar,String profile)throws Exception {
        var finder=java.lang.module.ModuleFinder.of(java.nio.file.Path.of(jar));
        String name=finder.findAll().iterator().next().descriptor().name();
        var configuration=ModuleLayer.boot().configuration().resolve(finder,java.lang.module.ModuleFinder.of(),Set.of(name));
        var layer=ModuleLayer.boot().defineModulesWithOneLoader(configuration,ClassLoader.getPlatformClassLoader());
        Class<?> target=Class.forName("dev.ronova.pro.validation.WorldFixture$GroupReturns",true,layer.findLoader(name));
        java.util.function.Function<String,Object> call=method->{
            try {return target.getMethod(method).invoke(null);}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        };
        call.apply("action");
        require(Boolean.FALSE.equals(call.apply("bool"))&&Byte.valueOf((byte)0).equals(call.apply("bytes"))
                &&Character.valueOf((char)0).equals(call.apply("chars"))&&Short.valueOf((short)0).equals(call.apply("shorts"))
                &&Integer.valueOf(profile.equals("invalid-id")?-1:0).equals(call.apply("ints"))
                &&Long.valueOf(0).equals(call.apply("longs"))&&Float.valueOf(0).equals(call.apply("floats"))
                &&Double.valueOf(0).equals(call.apply("doubles")),"all primitive and void entries suppress business effects");
        require(call.apply("object")==null,"reference business object is not published");
        Object array=call.apply("array"),matrix=call.apply("matrix");
        require(profile.equals("empty")?array instanceof int[] a&&a.length==0&&matrix instanceof String[][] m&&m.length==0
                :array==null&&matrix==null,"primitive and multidimensional arrays honor the chosen policy");
        Object identity=call.apply("identity"),text=call.apply("text");
        if(profile.startsWith("uuid-")) {
            require(identity instanceof java.util.UUID&&text instanceof String,"UUID and String policies return valid identities");
            java.util.UUID.fromString((String)text);
            require(profile.equals("uuid-fixed")?identity.equals(call.apply("identity"))&&!identity.toString().equals("live")
                    :!identity.equals(call.apply("identity")),"fixed and per-call UUID semantics differ as selected");
        } else require(identity==null&&(profile.equals("empty")?"".equals(text):text==null),"identity and text honor the chosen null or empty policy");
        if(profile.equals("null"))require(call.apply("optional")==null&&call.apply("future")==null,"forced null covers reference contracts");
        else {
            require(((java.util.OptionalInt)call.apply("optional")).isEmpty()&&!((Iterator<?>)call.apply("iterator")).hasNext()
                    &&((java.util.stream.IntStream)call.apply("stream")).count()==0,"typed empty consumers stay usable");
            Future<?> future=(Future<?>)call.apply("future");
            require(future.isDone(),"Future consumer has no pending business work");
            try {require(future.get(1,TimeUnit.SECONDS)==null,"Future consumer returns an empty result");}
            catch(CancellationException|ExecutionException refused) {require(future.isDone(),"refused Future releases its consumer");}
        }
        try {target.getConstructor().newInstance();throw new AssertionError("stopped constructor returned a half object");}
        catch(InvocationTargetException expected) {
            require(expected.getCause() instanceof IllegalStateException
                    &&"RONOVA_MOD_GROUP_CREATION_REFUSED".equals(expected.getCause().getMessage()),"business constructor is refused before its prefix executes");
        }
        require(target.getField("effects").getInt(null)==0,"raw business effect count remains zero");
        require(new java.util.ArrayList<>(List.of(7)).get(0)==7,"outside shared framework remains active");
        System.out.println("MOD_GROUP_RETURN_EFFECTS_PASS profile="+profile);
    }

}
