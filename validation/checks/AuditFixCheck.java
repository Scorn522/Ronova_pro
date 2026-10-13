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
        if(args.length==1&&args[0].equals("file-transfer")){fileTransfer();return;}
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
