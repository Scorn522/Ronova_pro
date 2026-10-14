package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Focused source-list regression and historical metadata checks; neither certifies gameplay. */
public final class AuditFixCheck {
    private static final sun.misc.Unsafe U;
    static {try {var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);U=(sun.misc.Unsafe)field.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static void put(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void require(boolean yes,String message) {if(!yes)throw new AssertionError(message);}
    private static Object invoke(Object receiver,String name)throws Exception {var m=receiver.getClass().getDeclaredMethod(name);m.setAccessible(true);try{return m.invoke(receiver);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}}
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("source-list-fixes")){sourceListFixes();return;}
        if(args.length==1&&args[0].equals("live-control-writer")){liveControlWriter();return;}
        if(args.length==1&&args[0].equals("clone-boundary")){cloneBoundary();return;}
        if(args.length==1&&args[0].equals("buffer-release")){bufferRelease();return;}
        if(args.length==1&&args[0].equals("buffer-wrap")){bufferWrap();return;}
        if(args.length==1&&args[0].equals("buffer-font")){bufferFont();return;}
        if(args.length==1&&args[0].equals("native-image-srg")){nativeImageSrg();return;}
        if(args.length==1&&args[0].equals("file-transfer")){fileTransfer();return;}
        if(args.length==1&&args[0].equals("socket-accept")){socketAccept();return;}
        if(args.length==1&&args[0].equals("host-load")){hostLoad();return;}
        if(args.length==1&&args[0].equals("jvmti-dispatch")){jvmtiDispatch();return;}
        if(args.length==1&&args[0].equals("host-thread-create")){hostThreadCreate();return;}
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

    // Actual isolated TxF preparation; no commit permit is constructed or granted.
    private static native void hostProcess0();

    // One AUDIT_HOST_THREAD_CREATE_CHECK DLL. thread-create uses unattached
    // starts; thread-end checks actual attachment/detachment without rerunning it.
    private static native void hostThreadCreate0(Class<?> type,Object payload);
    private static native void hostThreadEnd0();
    private static native void hostModuleLoad0();
    private static native void hostDefinitionsChanged0(ClassLoader loader);
    private static final class HostThreadCreatePayload { }
    private static void hostThreadCreate(){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
        String phase=System.getProperty("ronova.pro.audit.thread.phase","thread-create");
        if(phase.equals("module-load")||phase.equals("admission")){
            hostModuleLoad0();
            System.out.println("HOST_MODULE_LOAD_PASS actual_A_W_ExA_ExW_imports_and_dynamic_results original_parameters_flags_actual_HMODULE actual_invalid_image_error actual_DATAFILE_IMAGE_RESOURCE_tagged_HMODULE original_resource_flags actual_loader_LastError balanced_FreeLibrary temporary_input_removed no_manufactured_producer_authority");
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

    private static void storageNative()throws Exception {
        require(StorageNative.available()&&StorageNative.abi0()==3,"actual storage DLL unavailable: "+StorageNative.reason());
        byte[] original=new byte[33001],candidate=new byte[65537];
        for(int i=0;i<original.length;i++)original[i]=(byte)(i*13);
        for(int i=0;i<candidate.length;i++)candidate[i]=(byte)(i*31+7);
        var file=java.nio.file.Files.createTempFile(java.nio.file.Path.of(".").toAbsolutePath().normalize(),"storage-2023-",".bin");
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
            for(boolean valid:new boolean[]{false,true}) {
                Object description=descriptorConstructor.newInstance("STORAGE/2",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1L,"source","code",1L,identity,baseline,valid?expected:"0".repeat(64),baseline,UUID.randomUUID(),"win32-ntfs-txf/2",null);
                Object work=workConstructor.newInstance(description);
                try(var builder=new RecoveryImage.Builder()) {
                    builder.write(candidate);
                    try(var image=builder.finish()) {
                        var ticket=(RecoveryStorage.Ticket)ticketConstructor.newInstance(owner,work,source,image);
                        put(work,"ticket",ticket);put(work,"preparing",true);
                        long[] state=StorageNative.prepare0(ticket);
                        try {
                            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                            while(state[0]==7&&state[1]>0&&System.nanoTime()<deadline)state=StorageNative.advance0(ticket,state[1]);
                            require(state[0]==(valid?1:0),"candidate preparation returned "+Arrays.toString(state));
                        } finally {
                            if(ticket.token>0){long[] retired=StorageNative.finish0(ticket,ticket.token,null);require(retired[0]==(valid?2:0)&&retired[1]==1,"unpermitted transaction did not retire: "+Arrays.toString(retired));}
                        }
                    }
                }
                require(Arrays.equals(original,java.nio.file.Files.readAllBytes(file)),"unpermitted candidate changed the real file");
            }
            long[] maintenance=StorageNative.maintain0();require(maintenance[0]==1&&maintenance[2]==0,"native storage handles retained: "+Arrays.toString(maintenance));
            System.out.println("STORAGE_NATIVE_PASS actual_dll foreign_request_refused candidate_hash_refused multi_chunk_hash staged_readback no_permit_rollback original_preserved handles_retired");
        } finally {java.nio.file.Files.deleteIfExists(file);}
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
                new ArrayList<>(List.of(target,neighbor,target)),
                new CopyOnWriteArrayList<>(List.of(target,neighbor,target)),
                new it.unimi.dsi.fastutil.objects.ObjectArrayList<>(List.of(target,neighbor,target)));
        for(List<net.minecraft.nbt.CompoundTag> carrier:carriers){
            String projected=(String)image.invoke(null,carrier,target);
            require(!projected.isEmpty(),"NBT projection unavailable: "+carrier.getClass().getName());
            require(SourceMaps.removeListIdentity(carrier,target),"duplicate removal not applied: "+carrier.getClass().getName());
            require(carrier.size()==1&&carrier.get(0)==neighbor,"duplicate target retained or equal neighbor damaged: "+carrier.getClass().getName());
            require(projected.equals(image.invoke(null,carrier,null)),"projection does not match all removed aliases: "+carrier.getClass().getName());
            require(!SourceMaps.removeListIdentity(carrier,target)&&carrier.get(0)==neighbor,"absent target changed neighbor: "+carrier.getClass().getName());
        }
        System.out.println("SOURCE_LIST_FIXES_PASS bridge_owner array_list copy_on_write fastutil duplicate_identity preserved_equal_neighbor exact_nbt_projection");
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
}
