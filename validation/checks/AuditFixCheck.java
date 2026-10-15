package dev.ronova.pro;

import java.lang.invoke.*;
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
        if(args.length==1&&args[0].equals("network-bytecode")){networkBytecode();return;}
        if(args.length==3&&args[0].equals("mod-group-returns")){modGroupReturns(args[1],args[2]);return;}
        if(args.length==2&&args[0].equals("network-preconnect")){networkPreconnect(args[1]);return;}
        if(args.length==1&&args[0].equals("source-list-fixes")){sourceListFixes();return;}
        if(args.length==1&&args[0].equals("live-control-writer")){liveControlWriter();return;}
        if(args.length==1&&args[0].equals("system-arraycopy")){systemArraycopy();return;}
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

    // Actual isolated TxF preparation; no commit permit is constructed or granted.
    private static native void hostProcess0();

    // One AUDIT_HOST_THREAD_CREATE_CHECK DLL. thread-create uses unattached
    // starts; thread-end checks actual attachment/detachment without rerunning it.
    private static native void hostThreadCreate0(Class<?> type,Object payload);
    private static native void hostThreadEnd0();
    private static native void hostModuleLoad0();
    private static native void hostJniTable0();
    private static native void hostJvmtiOutput0();
    private static native boolean hostJniOutput0();
    private static native boolean hostMemoryRead0();
    private static native void hostRawUnsafe0();
    private static native void hostModuleRelease0(String controlDll);
    private static native void hostModuleReleaseThread0(String controlDll);
    private static native void hostMemory0(String controlDll);
    private static native void hostMemoryRelease0(String controlDll);
    private static native void hostDefinitionsChanged0(ClassLoader loader);
    private static final class HostThreadCreatePayload { }
    private static void hostThreadCreate(){
        System.load(java.nio.file.Path.of(System.getProperty("ronova.pro.audit.thread.native")).toAbsolutePath().toString());
        String phase=System.getProperty("ronova.pro.audit.thread.phase","thread-create");
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
    /** Verify the actual transformed Netty methods without starting another game JVM. */
    private static void networkBytecode()throws Exception{
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.NetworkBoundary");
        Method transform=boundary.getDeclaredMethod("transform",ClassLoader.class,String.class,byte[].class);transform.setAccessible(true);
        Set<String> targets=Set.of("io.netty.channel.AbstractChannelHandlerContext","io.netty.channel.AbstractChannelHandlerContext$WriteTask");
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
        System.out.println("NETWORK_BYTECODE_VERIFY_PASS actual_Netty_write_and_WriteTask JVM_verifier_only no_stop_or_cleanup_runtime_claim");
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
