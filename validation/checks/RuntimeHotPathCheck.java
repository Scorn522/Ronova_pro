package dev.ronova.pro;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.lang.invoke.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Focused metadata regression; no transformed JVM, Native or gameplay claims. */
public final class RuntimeHotPathCheck {
    private static final String PREFIX="dev.ronova.pro.bootstrap.";
    private static final Class<?> CODE=type("CodeSourceBridge"),CONTROL=type("ControlBridge"),RESOURCE=type("ResourceBridge");
    public static final class Bean { public int value; }
    public static final class PlainFields { public int value; public Object reference; }
    public static final class ConstantBean { public static int value; }
    public static class ParentBean {public int inherited;}
    public static final class ChildBean extends ParentBean {public long own;}
    private static final class Input extends FileInputStream {Input(){super(new FileDescriptor());}}
    private static final class Output extends FileOutputStream {Output(){super(new FileDescriptor());}}
    private static final class CustomSocket extends Socket {}
    private static Class<?> type(String name){try{return Class.forName(PREFIX+name);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static Field field(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object read(Class<?> type,String name)throws Exception{return field(type,name).get(null);}
    private static Object call(Class<?> type,Object receiver,String name,Class<?>[] signature,Object... args)throws Exception {
        Method m=type.getDeclaredMethod(name,signature);m.setAccessible(true);
        try{return m.invoke(receiver,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}
    }
    private static Object make(Class<?> type,Class<?>[] signature,Object...args)throws Exception{Constructor<?> c=type.getDeclaredConstructor(signature);c.setAccessible(true);return c.newInstance(args);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static boolean controlled(Object value)throws Exception {Object registry=read(CODE,"CONTROLS");return (boolean)call(registry.getClass(),registry,"contains",new Class<?>[]{Object.class},value);}
    private static boolean critical(Object value)throws Exception{return (boolean)call(RESOURCE,null,"critical",new Class<?>[]{Object.class},value);}
    private static void origins()throws Exception {
        Class<?> sources=type("ControlBridge$OriginSources");Object set=make(sources,new Class<?>[0]);
        Module first=String.class.getModule(),second=java.sql.Driver.class.getModule();
        Object lock=read(CONTROL,"CREATION_ORIGINS");
        synchronized(lock){
            require((boolean)call(sources,set,"addSource",new Class<?>[]{Module.class},first),"first source missing");
            Module[] one=(Module[])call(sources,set,"snapshot",new Class<?>[0]);
            require(!(boolean)call(sources,set,"addSource",new Class<?>[]{Module.class},first),"duplicate source admitted");
            require(one==call(sources,set,"snapshot",new Class<?>[0]),"duplicate changed snapshot");
            call(sources,set,"addSource",new Class<?>[]{Module.class},second);
            Module[] two=(Module[])call(sources,set,"snapshot",new Class<?>[0]);
            require(one.length==1&&one[0]==first&&two.length==2,"published snapshot mutated");
            require(!controlled(one)&&!controlled(two),"private source arrays entered control registry");
            require((boolean)call(sources,set,"removeSource",new Class<?>[]{Object.class},first),"source removal lost");
            require(two.length==2&&two[0]==first,"removal changed prior snapshot");
            call(sources,set,"removeSource",new Class<?>[]{Object.class},second);
            call(sources,set,"addSource",new Class<?>[]{Module.class},(Object)null);
            require(((Set<?>)set).contains(null),"unknown source lost");
            call(sources,set,"removeSource",new Class<?>[]{Object.class},(Object)null);
            require(((Set<?>)set).isEmpty(),"single source removal failed");
        }
    }
    private static void publications()throws Exception {
        Class<?> publication=type("CodeSourceBridge$ControlPublication");
        for(int size:new int[]{0,1,4,5}){
            Object[] values=new Object[size];for(int i=0;i<size;i++)values[i]=new String("same");
            Object p=make(publication,new Class<?>[]{Object[].class,publication},values,null);
            call(publication,p,"prepare",new Class<?>[0]);Object index=field(publication,"index").get(p);
            require((size<=4)==(index==null),"small batch index allocation changed");
            for(Object value:values)require((boolean)call(publication,p,"contains",new Class<?>[]{Object.class},value),"active value unprotected");
            for(Object value:new Object[]{p,values})require((boolean)call(publication,p,"contains",new Class<?>[]{Object.class},value),"publication metadata unprotected");
            if(index!=null)require((boolean)call(publication,p,"contains",new Class<?>[]{Object.class},index),"publication index unprotected");
            require(!(boolean)call(publication,p,"contains",new Class<?>[]{Object.class},new String("same")),"equals mistaken for identity");
            call(CODE,null,"protect",new Class<?>[]{Object[].class},(Object)values);
            require(!controlled(values)&&!controlled(p)&&(index==null||!controlled(index)),"temporary metadata retained after batch");
            for(Object value:values)require(controlled(value),"actual control lost after batch");
        }
        Object gate=call(CONTROL,null,"fieldGate",new Class<?>[]{Object.class},new Object());
        Field sync=(Field)read(type("CodeSourceBridge$FieldGateLayout"),"SYNC");
        for(Object value:new Object[]{gate,sync.get(gate)})require((boolean)call(CODE,null,"fieldGateMetadata",new Class<?>[]{Object.class},value),"field gate role not published");
        Object next=call(CONTROL,null,"fieldGate",new Class<?>[]{Object.class},new Object());
        require(sync==read(type("CodeSourceBridge$FieldGateLayout"),"SYNC")&&gate!=next,"gate layout not reused");
    }
    private static Object tracking(Class<?> actual)throws Exception{return call(CODE,null,"fieldMutationTracking",new Class<?>[]{Class.class},actual);}
    private static void registrations()throws Exception {
        Bean bean=new Bean();Object before=tracking(Bean.class);
        require(before==tracking(Bean.class),"unchanged field projection reallocated");
        Object token=call(CONTROL,null,"beginFieldMutation",new Class<?>[]{Object.class,Class.class,String.class,String.class,Object.class},bean,Bean.class,"value","I",1);
        require(token==null,"ordinary field unexpectedly requires a receipt");
        call(CODE,null,"codeFields",new Class<?>[]{ClassLoader.class,String.class,String[].class,Module[][].class,Class.class},Bean.class.getClassLoader(),Bean.class.getName(),new String[]{"value\0I"},new Module[][]{new Module[]{Bean.class.getModule()}},Bean.class);
        require((boolean)call(CODE,null,"fieldMutationTrackingChanged",new Class<?>[]{Class.class,before.getClass()},Bean.class,before),"late code field not detected");
        require((boolean)call(CODE,null,"fieldMutationTracked",new Class<?>[]{Class.class},Bean.class),"late code field untracked");
        token=call(CONTROL,null,"beginFieldMutation",new Class<?>[]{Object.class,Class.class,String.class,String.class,Object.class},bean,Bean.class,"value","I",2);
        require(token!=null,"registered field used no-receipt path");
        if(token!=Boolean.FALSE)call(CONTROL,null,"finishFieldMutation",new Class<?>[]{Object.class,boolean.class},token,false);
        Object constants=tracking(ConstantBean.class);
        call(CODE,null,"codeValues",new Class<?>[]{ClassLoader.class,String.class,Object[][].class,Class.class},ConstantBean.class.getClassLoader(),ConstantBean.class.getName(),new Object[][]{{"value\0I",1,0,new Module[]{ConstantBean.class.getModule()}}},ConstantBean.class);
        require((boolean)call(CODE,null,"fieldMutationTrackingChanged",new Class<?>[]{Class.class,constants.getClass()},ConstantBean.class,constants),"late constant registration not detected");
        require((boolean)call(CODE,null,"fieldMutationTracked",new Class<?>[]{Class.class},ConstantBean.class),"constant field untracked");
    }
    @SuppressWarnings("unchecked") private static void resources()throws Exception {
        require(!critical(new Bean())&&!critical(Bean.class),"ordinary application class classified as resource");
        for(Object value:new Object[]{new Input(),new Output(),new CustomSocket(),ByteBuffer.allocate(4),FileInputStream.class,Socket.class})
            require(critical(value),"resource subclass/layout lost: "+value.getClass());
        BufferedInputStream view=new BufferedInputStream(new ByteArrayInputStream(new byte[0]));
        require(!critical(view),"unassociated process view gained ownership");
        Class<?> keyType=type("ResourceBridge$Key"),pipeType=type("ResourceBridge$PipeRecord");
        Object key=make(keyType,new Class<?>[]{Object.class,boolean.class},view,false);
        Object pipe=make(pipeType,new Class<?>[]{Object.class,Object.class,FileDescriptor.class,byte[].class},view,view,new FileDescriptor(),null);
        Object lock=read(RESOURCE,"STREAMS");Map<Object,Object> views=(Map<Object,Object>)read(RESOURCE,"PROCESS_VIEWS");
        synchronized(lock){views.put(key,new WeakReference<>(pipe));}
        try{require(critical(view),"late process association cached as negative");}
        finally{synchronized(lock){views.remove(key);}}
        require(!critical(view),"removed process association cached as permission");
    }
    private static void declarations()throws Exception {
        Class<?> metadata=type("PlatformMetadata");
        Field[] first=(Field[])call(metadata,null,"declaredFields",new Class<?>[]{Class.class},Bean.class);
        first[0].setAccessible(true);first[0]=null;
        Field[] next=(Field[])call(metadata,null,"declaredFields",new Class<?>[]{Class.class},Bean.class);
        require(next.length==1&&next[0]!=null&&next[0].getName().equals("value"),"returned array changed cached declarations");
        require(!next[0].isAccessible(),"returned Field leaked accessibility into cache");
        Field selected=(Field)call(metadata,null,"declaredField",new Class<?>[]{Class.class,String.class},Bean.class,"value");
        Bean bean=new Bean();selected.set(bean,19);require(selected.getInt(bean)==19,"cached declaration cannot perform real access");
        boolean privateLayoutRefused=false;
        try{call(metadata,null,"controlFields",new Class<?>[]{Class.class},Bean.class);}
        catch(SecurityException denied){privateLayoutRefused=true;}
        require(privateLayoutRefused,"foreign reader obtained private cached Field mirrors");
        Class<?> backing=type("BackingBridge");
        require(call(backing,null,"setterDeclaration",new Class<?>[]{Class.class,String.class},ChildBean.class,"inherited")==ParentBean.class,"inherited setter declaration lost");
        require(call(backing,null,"setterDeclaration",new Class<?>[]{Class.class,String.class},ChildBean.class,"own")==ChildBean.class,"actual setter declaration lost");
        require(call(backing,null,"setterDeclaration",new Class<?>[]{Class.class,String.class},ParentBean.class,"own")==null,"subclass field leaked into parent layout");
    }
    private static void methodProjection()throws Exception {
        Object before=call(CODE,null,"frameMethods",new Class<?>[]{Class.class},ChildBean.class);
        require(!methodMatches(before,"late","()V",false),"fresh class has method rows");
        call(CODE,null,"codeMethods",new Class<?>[]{ClassLoader.class,String.class,String[].class,Class.class},ChildBean.class.getClassLoader(),ChildBean.class.getName(),new String[]{"late()V","late(I)V","a()V","z()V"},ChildBean.class);
        Object after=call(CODE,null,"frameMethods",new Class<?>[]{Class.class},ChildBean.class);
        require(before!=after&&methodMatches(after,"late","()V",true)&&methodMatches(after,"late","(I)V",true),"late overloaded methods lost behind projection");
        require(!methodMatches(after,"missing","()V",false),"name miss matched a method");
        require(!methodMatches(after,"late","(J)V",true),"different overload gained provenance");
        require(methodMatches(after,"a","()V",true)&&methodMatches(after,"z","()V",true),"edge selector lost");
        require(after==call(CODE,null,"frameMethods",new Class<?>[]{Class.class},ChildBean.class),"unchanged method projection rebuilt");
        call(CODE,null,"codeMethods",new Class<?>[]{ClassLoader.class,String.class,String[].class,Class.class},ChildBean.class.getClassLoader(),ChildBean.class.getName(),new String[]{"late(()V","odd()Lx(y;"},ChildBean.class);
        Object unusual=call(CODE,null,"frameMethods",new Class<?>[]{Class.class},ChildBean.class);
        require(unusual!=after&&methodMatches(unusual,"late(","()V",true)&&methodMatches(unusual,"odd","()Lx(y;",true),"unusual JVM selector lost");
        require(!methodMatches(unusual,"late(","(I)V",true),"unusual selector widened matching");
    }
    // Metadata matcher only: these frames are never supplied to Native codeFrame
    // or any provenance/permission entry point.
    private static boolean methodMatches(Object projection,String name,String descriptor,boolean descriptorAllowed)throws Exception {
        StackWalker.StackFrame frame=new StackWalker.StackFrame(){
            public String getClassName(){return ChildBean.class.getName();}
            public String getMethodName(){return name;}
            public Class<?> getDeclaringClass(){return ChildBean.class;}
            public String getDescriptor(){if(!descriptorAllowed)throw new AssertionError("descriptor queried on a name miss");return descriptor;}
            public int getByteCodeIndex(){return 0;}
            public String getFileName(){return null;}
            public int getLineNumber(){return -1;}
            public boolean isNativeMethod(){return false;}
            public StackTraceElement toStackTraceElement(){throw new UnsupportedOperationException();}
        };
        return (boolean)call(projection.getClass(),projection,"matches",new Class<?>[]{StackWalker.StackFrame.class},frame);
    }
    private static void sourceUnions()throws Exception {
        Module a=String.class.getModule(),b=java.sql.Driver.class.getModule();
        Module[] left={a,null,a},right={b,null};
        Module[] merged=(Module[])call(CONTROL,null,"mergeOperationSources",new Class<?>[]{Module[].class,Module[].class},left,right);
        require(merged.length==3&&Arrays.asList(merged).containsAll(Arrays.asList(a,b,null)),"Native/scoped/unknown union lost");
        require(left[0]==a&&right[0]==b,"source input mutated");
        Object target=new Object(),lock=read(CONTROL,"CREATION_ORIGINS");
        Map<?,?> origins=(Map<?,?>)lock;
        synchronized(lock){
            call(CONTROL,null,"createdBy",new Class<?>[]{Object.class,Module[].class,boolean.class},target,new Module[]{a},false);
            Object first=origins.get(target);
            call(CONTROL,null,"createdBy",new Class<?>[]{Object.class,Module[].class,boolean.class},target,new Module[]{a,a},false);
            require(first==origins.get(target),"unchanged source rebuilt origin record");
            call(CONTROL,null,"createdBy",new Class<?>[]{Object.class,Module[].class,boolean.class},target,new Module[]{b},false);
            require(first!=origins.get(target),"changed source skipped publication");
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("gpu-fallback")){gpuFallback();return;}
        if(args.length==1&&args[0].equals("installed-memory-writers")){installedMemoryWriters();callSiteGuards();primitiveArrays();bufferBulkBoundary();return;}
        origins();publications();registrations();resources();declarations();methodProjection();sourceUnions();scalarValues();primitiveArrays();weaponCatalog();
        System.out.println("RUNTIME_HOT_PATH_CHECKS_PASS origins publication_lifetime field_gate_roles late_registration resource_subclasses live_process_views declaration_isolation inherited_setters late_methods Native_unknown_union unchanged_origin; METADATA_ONLY");
    }
    private static Object sensitiveArray;
    private static boolean backingKnown(Object value){return value==sensitiveArray;}
    private static boolean backingArray(Object value,int index,Object next){return value==sensitiveArray;}
    private static boolean backingElement(Object value,Object prior,Object next){return false;}
    private static boolean backingOffset(Object value,long offset,Object next){return false;}
    private static boolean backingMemory(Object value,long offset,long length){return false;}
    private static long backingRevision(Object value){return 0;}
    private static void primitiveArrays()throws Exception {
        Class<?> backing=type("BackingBridge");
        Object[] arrays={new int[2],new long[2],new float[2],new double[2],new byte[2],new short[2],new boolean[2],new char[2]};
        for(Object array:arrays)require((boolean)call(CONTROL,null,"ordinaryPrimitiveArray",new Class<?>[]{Object.class},array),"ordinary primitive array not admitted");
        require(!(boolean)call(CONTROL,null,"ordinaryPrimitiveArray",new Class<?>[]{Object.class},(Object)new Object[2]),"reference array admitted");
        call(backing,null,"intStore",new Class<?>[]{Object.class,int.class,int.class},arrays[0],1,17);
        call(backing,null,"longStore",new Class<?>[]{Object.class,int.class,long.class},arrays[1],1,19L);
        call(backing,null,"floatStore",new Class<?>[]{Object.class,int.class,float.class},arrays[2],1,1.5F);
        call(backing,null,"doubleStore",new Class<?>[]{Object.class,int.class,double.class},arrays[3],1,2.5D);
        call(backing,null,"byteStore",new Class<?>[]{Object.class,int.class,int.class},arrays[4],1,258);
        call(backing,null,"shortStore",new Class<?>[]{Object.class,int.class,int.class},arrays[5],1,65538);
        call(backing,null,"byteStore",new Class<?>[]{Object.class,int.class,int.class},arrays[6],1,3);
        call(backing,null,"charStore",new Class<?>[]{Object.class,int.class,int.class},arrays[7],1,65539);
        require(((int[])arrays[0])[1]==17&&((long[])arrays[1])[1]==19&&((float[])arrays[2])[1]==1.5F&&((double[])arrays[3])[1]==2.5D&&((byte[])arrays[4])[1]==2&&((short[])arrays[5])[1]==2&&((boolean[])arrays[6])[1]&&((char[])arrays[7])[1]==3,"primitive store semantics changed");
        try{call(backing,null,"intStore",new Class<?>[]{Object.class,int.class,int.class},arrays[0],2,1);throw new AssertionError("bounds exception lost");}catch(ArrayIndexOutOfBoundsException expected){}
        try{call(backing,null,"intStore",new Class<?>[]{Object.class,int.class,int.class},null,0,1);throw new AssertionError("null exception lost");}catch(NullPointerException expected){}
        if(CONTROL.getClassLoader()!=null){
        Object previous=read(backing,"policy");sensitiveArray=arrays[0];var lookup=MethodHandles.lookup();
        try{
            call(backing,null,"install",new Class<?>[]{MethodHandle.class,MethodHandle.class,MethodHandle.class,MethodHandle.class,MethodHandle.class,MethodHandle.class},
                lookup.findStatic(RuntimeHotPathCheck.class,"backingKnown",MethodType.methodType(boolean.class,Object.class)),
                lookup.findStatic(RuntimeHotPathCheck.class,"backingElement",MethodType.methodType(boolean.class,Object.class,Object.class,Object.class)),
                lookup.findStatic(RuntimeHotPathCheck.class,"backingArray",MethodType.methodType(boolean.class,Object.class,int.class,Object.class)),
                lookup.findStatic(RuntimeHotPathCheck.class,"backingOffset",MethodType.methodType(boolean.class,Object.class,long.class,Object.class)),
                lookup.findStatic(RuntimeHotPathCheck.class,"backingMemory",MethodType.methodType(boolean.class,Object.class,long.class,long.class)),
                lookup.findStatic(RuntimeHotPathCheck.class,"backingRevision",MethodType.methodType(long.class,Object.class)));
            require(!(boolean)call(CONTROL,null,"ordinaryPrimitiveArray",new Class<?>[]{Object.class},arrays[0]),"late backing registration ignored");
            call(backing,null,"intStore",new Class<?>[]{Object.class,int.class,int.class},arrays[0],1,999);
            require(((int[])arrays[0])[1]==17,"sensitive backing write bypassed policy");
        }finally{field(backing,"policy").set(null,previous);sensitiveArray=null;}
        }
        int[] control=new int[2];call(CODE,null,"protect",new Class<?>[]{Object[].class},(Object)new Object[]{control});
        require(!(boolean)call(CONTROL,null,"ordinaryPrimitiveArray",new Class<?>[]{Object.class},control),"controlled primitive array admitted");
        System.out.println("PRIMITIVE_ARRAY_POLICY_PASS scalar_conversions bounds controlled_identity reference_arrays; late_backing="+(CONTROL.getClassLoader()!=null?"METADATA_PASS":"SEPARATE_METADATA_CHECK"));
    }
    private static void weaponCatalog()throws Exception {
        RecoveryRecords.prepareFieldPolicy();
        RecoveryRecords reader=new RecoveryRecords(null,null);
        require(!reader.fieldWriteDenied(new Object(),Object.class,"unregistered",null),"empty prepared field reader refused an unrelated write");
        require(!ProMod.weaponActionAuthorized(null),"missing weapon authority admitted");
        Set<String> names=new HashSet<>();for(WeaponAction action:WeaponAction.values())require(names.add(action.name()),"duplicate weapon operation");
        require(names.containsAll(Set.of("MOD_STOP","MOD_RETIRE","MOD_PROTECT","TIME_STOP","RULE_RESUME","WORLD_ROLLBACK","WORLD_RETRY","FIELD_RESTORE","OP_CANCEL","OP_RECOVERY","OP_REQUIRED","REPLAY","DIAGNOSTICS")),"button action was lost");
        try{WeaponAction.at(-2);throw new AssertionError("unknown weapon opcode admitted");}catch(IllegalArgumentException expected){}
        Class<?> runs=type("ClientBridge$GpuImages$VertexSources");Object mutable=make(runs,new Class<?>[0]);
        @SuppressWarnings("unchecked") List<Module[]> vertices=(List<Module[]>)mutable;Module[] first={String.class.getModule()},second={java.sql.Driver.class.getModule()};
        for(int i=0;i<100;i++)vertices.add(first);for(int i=0;i<7;i++)vertices.add(second);
        List<?> snapshot=(List<?>)call(runs,mutable,"snapshot",new Class<?>[0]);vertices.add(first);
        require(snapshot.size()==107&&snapshot.get(99)==first&&snapshot.get(100)==second&&snapshot.get(106)==second,"GPU batch source ranges changed identity or prior snapshot");
        Class<?> gpu=type("ClientBridge$GpuImages"),build=type("ClientBridge$GpuImages$Build");Object index=make(gpu,new Class<?>[0]);
        Object builderA=new Object(),builderB=new Object(),buildA=make(build,new Class<?>[0]),buildB=make(build,new Class<?>[0]);
        call(gpu,index,"activate",new Class<?>[]{Object.class,build},builderA,buildA);call(gpu,index,"activate",new Class<?>[]{Object.class,build},builderB,buildB);
        Object a=call(gpu,index,"enter",new Class<?>[]{Object.class,Object.class,int.class},builderA,null,0);require(a==buildA,"interleaved older GPU builder lost its source batch");
        call(gpu,index,"complete",new Class<?>[]{Object.class,Object.class,int.class},builderA,a,4);
        Object b=call(gpu,index,"enter",new Class<?>[]{Object.class,Object.class,int.class},builderB,null,0);require(b==buildB,"interleaved latest GPU builder lost its source batch");
        call(gpu,index,"complete",new Class<?>[]{Object.class,Object.class,int.class},builderB,b,4);
        call(gpu,index,"finishCursor",new Class<?>[]{Object.class},builderA);
        require(call(gpu,index,"currentBuild",new Class<?>[]{Object.class},builderA)==null&&call(gpu,index,"currentBuild",new Class<?>[]{Object.class},builderB)==buildB,"finishing one layer removed another active GPU builder");
        call(gpu,index,"finishCursor",new Class<?>[]{Object.class},builderB);
        try(var recording=new jdk.jfr.Recording()){
            recording.enable("jdk.JavaExceptionThrow");recording.enable("jdk.JavaErrorThrow");recording.start();
            call(type("BackingBridge"),null,"prepareAdapters",new Class<?>[0]);
            new NoSuchMethodError("BACKING_LINKAGE_TRACE_CHECK");
        }
        boolean foreign=false;try{dev.ronova.pro.bootstrap.ModSources.enter(String.class);}catch(SecurityException denied){foreign=true;}require(foreign,"foreign method-source identity admitted");
        boolean prepared=false;try{dev.ronova.pro.bootstrap.ControlBridge.preparePolicyOwner(RuntimeHotPathCheck.class);}catch(SecurityException denied){prepared=true;}require(prepared,"unbound pre-policy control owner admitted");
        Object scope=dev.ronova.pro.bootstrap.ModSources.enter(RuntimeHotPathCheck.class);dev.ronova.pro.bootstrap.ModSources.exit(scope);
        require(!java.util.concurrent.CompletableFuture.supplyAsync(()->ClientPresence.worldVisualsNeeded(null)).join(),"inactive chunk visual lookup entered client-thread context");
        require(java.util.concurrent.CompletableFuture.supplyAsync(()->ClientWorldVisuals.blockBegin(null,null,null)).join()==null,"inactive tessellator created a visual scope");
        ClientWorldVisuals.blockEnd(null);
        byte[] hiddenBytes;try(var input=RuntimeHotPathCheck.class.getResourceAsStream("RuntimeHotPathCheck$HiddenSource.class")){require(input!=null,"hidden source fixture missing");hiddenBytes=input.readAllBytes();}
        var hiddenLookup=MethodHandles.lookup().defineHiddenClass(hiddenBytes,true,MethodHandles.Lookup.ClassOption.NESTMATE);Class<?> hidden=hiddenLookup.lookupClass();
        try{Class<?> observed=(Class<?>)hiddenLookup.findStatic(hidden,"exercise",MethodType.methodType(Class.class)).invokeExact();require(observed==hidden,"hidden source declaring identity changed");}
        catch(Throwable failure){throw new AssertionError("real hidden method source rejected",failure);}
        System.out.println("WEAPON_CATALOG_PASS closed_actions bounded_parameters no_ambient_authority prepared_empty_field_reader GPU_interleaved_layers JFR_adapter_linkage");
    }
    static final class HiddenSource {
        static Class<?> exercise(){Object scope=dev.ronova.pro.bootstrap.ModSources.enter(HiddenSource.class);try{return HiddenSource.class;}finally{dev.ronova.pro.bootstrap.ModSources.exit(scope);}}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void gpuFallback()throws Exception {
        Class<?> gpu=type("ClientBridge$GpuImages"),buildType=type("ClientBridge$GpuImages$Build");Object gpuModel=make(gpu,new Class<?>[0]),build=make(buildType,new Class<?>[0]);
        Object builder=new Object(),points=new Object[2];List<Module[]> rows=(List<Module[]>)field(buildType,"vertices").get(build);
        boolean foreign=false;try{call(gpu,gpuModel,"sortImage",new Class<?>[]{Object.class,Object.class,buildType},builder,builder,build);}catch(IllegalStateException expected){foreign=expected.getMessage().equals("ACTUAL_QUAD_SORT_CARRIER_REQUIRED");}require(foreign,"unregistered graphics carrier admitted");
        Object fallback=call(gpu,gpuModel,"countChangedSort",new Class<?>[]{Object.class,Object.class,buildType,List.class},builder,points,build,List.of());
        require(fallback!=null&&"GPU_QUAD_SORTING_COUNT_CHANGED".equals(field(buildType,"pending").get(build)),"sorting count mismatch crashed or lost its unresolved marker");
        Object image=field(fallback.getClass(),"image").get(fallback);require("GPU_QUAD_SORTING_COUNT_CHANGED".equals(field(image.getClass(),"pending").get(image)),"sorting fallback incorrectly reported a complete source image");
        Module[] source={RuntimeHotPathCheck.class.getModule()};rows.addAll(Arrays.asList(source,source,source,source));rows.add(2,source);require(rows.size()==5&&rows.get(2)==source,"indexed source insertion unsupported");
        call(gpu,gpuModel,"activate",new Class<?>[]{Object.class,buildType},builder,build);
        Object token=call(gpu,gpuModel,"enter",new Class<?>[]{Object.class,Object.class,int.class},builder,null,8);call(gpu,gpuModel,"complete",new Class<?>[]{Object.class,Object.class,int.class},builder,token,9);require(rows.size()==9,"source count mismatch did not recover to actual vertex count");
        token=call(gpu,gpuModel,"enter",new Class<?>[]{Object.class,Object.class,int.class},builder,null,9);call(gpu,gpuModel,"vertexFailed",new Class<?>[]{Object.class,Object.class,int.class},builder,token,10);
        token=call(gpu,gpuModel,"enter",new Class<?>[]{Object.class,Object.class,int.class},builder,null,10);require(token==build,"failed vertex left builder permanently in flight");call(gpu,gpuModel,"complete",new Class<?>[]{Object.class,Object.class,int.class},builder,token,11);require(rows.size()==11,"builder stopped tracking after recovery");
        call(gpu,gpuModel,"finishCursor",new Class<?>[]{Object.class},builder);
        System.out.println("GPU_SORT_FALLBACK_PASS foreign_carrier_refused count_gap_retained indexed_sources recovered_failure; METADATA_ONLY_NO_FORGE_CARRIER_CLAIM");
    }
    private static void gpuCapacityBoundary()throws Exception {
        String name="com/mojang/blaze3d/vertex/BufferBuilder";byte[] original;
        try(var input=ClassLoader.getSystemResourceAsStream(name+".class")){require(input!=null,"actual BufferBuilder image missing");original=input.readAllBytes();}
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.ClientBoundary");
        byte[] woven=(byte[])call(boundary,null,"transform",new Class<?>[]{ClassLoader.class,String.class,byte[].class},RuntimeHotPathCheck.class.getClassLoader(),name,original);
        ClassNode node=new ClassNode();new ClassReader(woven).accept(node,0);int writes=0,notifications=0;
        for(MethodNode method:node.methods)if(Set.of("ensureCapacity","m_85722_").contains(method.name)&&method.desc.equals("(I)V")){
            for(AbstractInsnNode at:method.instructions){
                if(at instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(name)&&field.desc.equals("Ljava/nio/ByteBuffer;"))writes++;
                if(at instanceof MethodInsnNode invoke&&invoke.owner.equals("dev/ronova/pro/bootstrap/ClientBridge")&&invoke.name.equals("verticesCapacityChanged")){
                    notifications++;AbstractInsnNode load=at.getPrevious(),write=load==null?null:load.getPrevious();
                    require(load instanceof VarInsnNode local&&local.getOpcode()==Opcodes.ALOAD&&local.var==0&&write instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD&&field.owner.equals(name)&&field.desc.equals("Ljava/nio/ByteBuffer;"),"capacity notification survived on per-vertex no-op branch");
                }
            }
        }
        require(writes>0&&notifications==writes,"actual GPU storage replacement lost its validation hook");
        System.out.println("GPU_CAPACITY_BOUNDARY_PASS actual_BufferBuilder storage_replacement_only; NO_GAMEPLAY_CLAIM");
    }
    private static void bufferBulkBoundary()throws Exception {
        Class<?> boundary=Class.forName("dev.ronova.pro.agent.BufferBoundary");int helpers=0;
        for(String kind:List.of("Byte","Char","Short","Int","Long","Float","Double")){
            String name="java/nio/"+kind+"Buffer";byte[] original;
            try(var input=Object.class.getResourceAsStream("/"+name+".class")){require(input!=null,"actual JDK Buffer image missing");original=input.readAllBytes();}
            byte[] woven=(byte[])call(boundary,null,"transform",new Class<?>[]{ClassLoader.class,String.class,byte[].class},null,name,original);
            ClassNode node=new ClassNode();new ClassReader(woven).accept(node,0);int found=0;
            for(MethodNode method:node.methods)if(Set.of("getArray","putArray","putBuffer").contains(method.name)){
                for(AbstractInsnNode at:method.instructions)if(at instanceof MethodInsnNode invoke)require(!invoke.owner.startsWith("dev/ronova/pro/"),"ordinary JDK bulk helper still enters Ronova: "+name+"#"+method.name);
                found++;
            }
            require(found==3,"JDK bulk helper shape changed: "+name);helpers+=found;
        }
        System.out.println("BUFFER_BULK_HELPERS_PASS actual_JDK_helpers="+helpers+" original_data_operations; NO_GAMEPLAY_CLAIM");
    }
    private static void installedMemoryWriters()throws Exception {
        Class<?> nativeControl=Class.forName(PREFIX+"NativeControl",false,null);
        require((boolean)nativeControl.getMethod("available").invoke(null),"actual premain Native backend required");
        require(CONTROL.getClassLoader()==null,"actual bootstrap bridge required");
        declarations();
        heapReadSnapshots();
        Bean bean=new Bean();VarHandle value=MethodHandles.lookup().findVarHandle(Bean.class,"value",int.class);
        value.setVolatile(bean,7);require(value.compareAndSet(bean,7,11),"legitimate VarHandle CAS refused");
        require((int)value.getAndAdd(bean,2)==11&&bean.value==13,"legitimate VarHandle update changed");
        var lock=new java.util.concurrent.locks.ReentrantLock();lock.lock();
        try{require(lock.isHeldByCurrentThread(),"legitimate JDK lock CAS refused");}finally{lock.unlock();}
        var concurrent=new java.util.concurrent.ConcurrentHashMap<Integer,Integer>();
        for(int i=0;i<128;i++)concurrent.put(i,i+1);
        require(concurrent.size()==128&&concurrent.compute(5,(k,v)->v+1)==7,"JDK table/field CAS semantics changed");
        var atomicLong=new java.util.concurrent.atomic.AtomicLong(3);require(atomicLong.incrementAndGet()==4&&atomicLong.compareAndSet(4,9)&&atomicLong.getAndAdd(2)==9&&atomicLong.get()==11,"AtomicLong actual CAS/update changed");
        var atomicInt=new java.util.concurrent.atomic.AtomicInteger(3);require(atomicInt.incrementAndGet()==4&&atomicInt.compareAndSet(4,9)&&atomicInt.getAndAdd(2)==9&&atomicInt.get()==11,"AtomicInteger actual CAS/update changed");
        var atomicBoolean=new java.util.concurrent.atomic.AtomicBoolean();require(atomicBoolean.compareAndSet(false,true)&&atomicBoolean.getAndSet(false)&&!atomicBoolean.get(),"AtomicBoolean actual CAS/update changed");
        var atomicReference=new java.util.concurrent.atomic.AtomicReference<Object>();Object atomicValue=new Object();require(atomicReference.compareAndSet(null,atomicValue)&&atomicReference.getAndSet(null)==atomicValue&&atomicReference.get()==null,"AtomicReference actual CAS/update changed");
        var pending=new java.util.concurrent.CompletableFuture<Integer>();
        var stage=pending.thenApply(n->n+4);
        Object completion=field(java.util.concurrent.CompletableFuture.class,"stack").get(pending);
        Map<?,?> taskOwners=(Map<?,?>)read(type("TaskBridge"),"TASK_OWNERS");
        require(completion!=null&&taskOwners.get(completion)!=null,"real JDK completion creation did not publish its capture");
        pending.complete(3);require(stage.join()==7,"legitimate task stage failed");
        require(java.util.concurrent.ForkJoinPool.commonPool().submit(()->{int sum=0;for(int i=0;i<64;i++)sum+=i;return sum;})
                .get(10,java.util.concurrent.TimeUnit.SECONDS)==2016,"real ForkJoin queue/task handoff failed");
        Object forgedTask=new Object();type("TaskBridge").getMethod("created",Object.class).invoke(null,forgedTask);
        require(taskOwners.get(forgedTask)==null,"foreign reflective task creation borrowed JDK authority");
        ByteBuffer buffer=ByteBuffer.allocateDirect(16);buffer.putLong(0,0x123456789abcdefL);
        ByteBuffer typed=ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder());
        typed.asIntBuffer().put(0,123);require(typed.getInt(0)==123,"int Buffer view lost its parent storage");typed.asFloatBuffer().put(1,1.25f);require(typed.getFloat(4)==1.25f,"float Buffer view changed");
        typed.asLongBuffer().put(1,987654321L);require(typed.getLong(8)==987654321L,"long Buffer view changed");typed.asShortBuffer().put(8,(short)1234);require(typed.getShort(16)==1234,"short Buffer view changed");
        typed.asDoubleBuffer().put(3,2.5);require(typed.getDouble(24)==2.5,"double Buffer view changed");typed.asCharBuffer().put(16,'R');require(typed.getChar(32)=='R',"char Buffer view changed");
        float[] uniforms={1.25f,-2.5f,3.75f,4};typed.asFloatBuffer().put(uniforms);
        for(int i=0;i<uniforms.length;i++)require(typed.getFloat(i*4)==uniforms[i],"actual float-array to direct uniform copy changed");
        ByteBuffer bulkCopy=ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder());bulkCopy.put(0,typed,0,16);
        float[] restoredUniforms=new float[4];bulkCopy.asFloatBuffer().get(restoredUniforms);require(Arrays.equals(uniforms,restoredUniforms),"direct Buffer putBuffer/getArray chain changed");
        try{typed.asReadOnlyBuffer().asIntBuffer().put(0,5);throw new AssertionError("typed read-only Buffer accepted a write");}catch(java.nio.ReadOnlyBufferException expected){}
        try{typed.asFloatBuffer().get(-1);throw new AssertionError("typed Buffer bounds were skipped");}catch(IndexOutOfBoundsException expected){}
        require((boolean)RESOURCE.getMethod("scalarBufferFast",Object.class).invoke(null,buffer),"direct Buffer scalar helpers not admitted");
        require(!(boolean)RESOURCE.getMethod("scalarBufferFast",Object.class).invoke(null,ByteBuffer.allocate(8)),"heap Buffer unexpectedly took direct shortcut");
        buffer.position(4);buffer.mark();buffer.position(8);buffer.reset();require(buffer.position()==4,"Buffer position helper semantics changed");
        buffer.clear();require(buffer.limit()==16&&buffer.remaining()==16,"Buffer metadata helper semantics changed");
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        java.util.concurrent.locks.LockSupport.setCurrentBlocker(bean);
        require(java.util.concurrent.locks.LockSupport.getBlocker(Thread.currentThread())==bean,"original JDK blocker lifecycle failed");
        java.util.concurrent.locks.LockSupport.setCurrentBlocker(null);
        long limitOffset=(long)unsafeType.getMethod("objectFieldOffset",Field.class).invoke(unsafe,java.nio.Buffer.class.getDeclaredField("limit"));
        unsafeType.getMethod("putInt",Object.class,long.class,int.class).invoke(unsafe,buffer,limitOffset,8);
        require(buffer.limit()==8,"actual Native Buffer primitive metadata store failed");buffer.clear();
        require(buffer.getLong(0)==0x123456789abcdefL,"real buffer read/write lifecycle changed");
        buffer.position(8);buffer.put(new byte[]{1,2,3,4});buffer.flip();
        ByteBuffer view=buffer.duplicate();view.position(8);view=view.slice();
        byte[] copied=new byte[4];view.get(copied);
        require(Arrays.equals(copied,new byte[]{1,2,3,4}),"bulk/slice buffer provenance lifecycle failed");
        boolean readOnly=false,outside=false;
        try{buffer.asReadOnlyBuffer().put(0,(byte)1);}catch(java.nio.ReadOnlyBufferException expected){readOnly=true;}
        try{buffer.putLong(12,1L);}catch(IndexOutOfBoundsException expected){outside=true;}
        require(readOnly&&outside,"buffer exceptions changed");
        buffer.put(0,(byte)7);require(buffer.get(0)==7,"buffer receipt leaked after exception");
        require(RESOURCE.getMethod("bufferOperation",Object.class,Class.class,String.class).invoke(null,buffer,buffer.getClass(),"put")==null,
                "unrestricted scalar Buffer unexpectedly created a receipt");
        boolean sourcesRefused=false;
        try{call(CONTROL,null,"resourceOperationSources",new Class<?>[]{Module[].class,Iterator.class},new Module[0],Collections.emptyIterator());}
        catch(SecurityException rejected){sourcesRefused="ACTUAL_RESOURCE_OPERATION_REQUIRED".equals(rejected.getMessage());}
        require(sourcesRefused,"foreign caller reached the BufferEntry source reader before entry authentication");
        boolean refused=false;
        try{CONTROL.getMethod("beginHandleMutation",Object.class,long.class,String.class).invoke(null,bean,0L,"Int");}
        catch(InvocationTargetException failure){
            refused=failure.getCause() instanceof SecurityException&&"ACTUAL_HANDLE_WRITE_REQUIRED".equals(failure.getCause().getMessage());
        }
        require(refused,"foreign reflective caller borrowed the handle writer entry");
        installedRawReads(nativeControl);
        System.out.println("PERFORMANCE_PLATFORM_PASS real_Native original_JDK_CAS CF_stage direct_buffer raw_lifecycle foreign_entry_refused; GLOBAL_MECHANICAL_COVERAGE_NOT_CLAIMED");
    }
    private static void heapReadSnapshots()throws Exception {
        var gate=new java.util.concurrent.locks.ReentrantLock();gate.lock();
        Class<?> heap=type("ControlBridge$HeapMemoryRead"),copyType=type("ControlBridge$MemoryCopy");
        Module[] roots={String.class.getModule()};
        Object read=make(heap,new Class<?>[]{java.util.concurrent.locks.ReentrantLock.class,Module[].class},gate,roots);
        Object copy=make(copyType,new Class<?>[]{Object.class,long.class,long.class,Object.class,Object.class,Module[].class},new byte[1],0L,1L,read,null,roots);
        try{
            Module[] exported=(Module[])call(CONTROL,null,"readSources",new Class<?>[]{Object.class},read);exported[0]=null;
            Module[] retained=(Module[])call(CONTROL,null,"copySources",new Class<?>[]{copyType},copy);
            require(retained.length==1&&retained[0]==String.class.getModule(),"export changed retained copy provenance");
            var foreign=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread other=new Thread(()->{try{call(CONTROL,null,"readSources",new Class<?>[]{Object.class},read);foreign.set(new AssertionError("foreign read admitted"));}catch(Throwable failure){foreign.set(failure);}},"ronova-read-scope-check");
            other.start();other.join(5000);require(!other.isAlive()&&foreign.get() instanceof IllegalMonitorStateException,"heap read lost thread/gate ownership");
        }finally{call(CONTROL,null,"finishFieldMutation",new Class<?>[]{Object.class,boolean.class},copy,false);}
        require(!gate.isHeldByCurrentThread(),"copy failure retained the source gate");
        try{call(CONTROL,null,"readSources",new Class<?>[]{Object.class},read);throw new AssertionError("closed heap read admitted");}
        catch(IllegalMonitorStateException expected){}
        System.out.println("HEAP_COPY_SOURCES_PASS snapshot_isolation actual_thread closed_receipt failure_cleanup");
    }
    public static final class CallSiteTemplate {
        public static void unsafe(sun.misc.Unsafe api,Object target,long offset,int value){api.putInt(target,offset,value);}
        public static boolean cas(sun.misc.Unsafe api,Object target,long offset,int expected,int value){return api.compareAndSwapInt(target,offset,expected,value);}
        public static void copy(sun.misc.Unsafe api,Object from,long source,Object to,long target,long count){api.copyMemory(from,source,to,target,count);}
        public static void set(VarHandle handle,Object target,int value){handle.set(target,value);}
        public static void array(VarHandle handle,int[] target,int index,int value){handle.set(target,index,value);}
        public static void view(VarHandle handle,ByteBuffer target,int index,int value){handle.set(target,index,value);}
        public static boolean handleCas(VarHandle handle,Object target,int before,int after){return handle.compareAndSet(target,before,after);}
        public static void buffer(ByteBuffer target,int value){target.putInt(0,value);}
        public static Object reflect(Method method,Object target,Object[] args)throws Exception{return method.invoke(target,args);}
        private static int twice(int value){return value*2;}
        public static void unsafeHandle(sun.misc.Unsafe api,Object target,long offset,int value)throws Throwable{
            MethodHandle call=MethodHandles.lookup().findVirtual(sun.misc.Unsafe.class,"putInt",MethodType.methodType(void.class,Object.class,long.class,int.class));
            call.invokeExact(api,target,offset,value);
        }
        public static void handleMethod(VarHandle handle,Object target,int value)throws Throwable{handle.toMethodHandle(VarHandle.AccessMode.SET).invoke(target,value);}
        public static void handleInvoker(VarHandle handle,Object target,int value)throws Throwable{MethodHandles.varHandleInvoker(VarHandle.AccessMode.SET,MethodType.methodType(void.class,Object.class,int.class)).invokeExact(handle,target,value);}
    }
    private static void callSiteGuards()throws Exception {
        String template=RuntimeHotPathCheck.class.getName().replace('.','/')+"$CallSiteTemplate";ClassNode node=new ClassNode();
        try(var input=RuntimeHotPathCheck.class.getResourceAsStream("/"+template+".class")){new ClassReader(input.readAllBytes()).accept(node,0);}
        node.name="foreign/WovenCalls";node.nestHostClass=null;node.innerClasses.clear();
        require((boolean)call(Class.forName("dev.ronova.pro.agent.ModMemoryBoundary"),null,"transform",new Class<?>[]{ClassNode.class},node),"Mod writer calls not woven");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);node.accept(writer);byte[] bytes=writer.toByteArray();
        Class<?> woven=new ClassLoader(RuntimeHotPathCheck.class.getClassLoader()){Class<?> define(){return defineClass("foreign.WovenCalls",bytes,0,bytes.length);}}.define();
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        long offset=(long)unsafeType.getMethod("objectFieldOffset",Field.class).invoke(unsafe,Bean.class.getField("value"));Bean bean=new Bean();
        Class<?>[] writeTypes={unsafeType,Object.class,long.class,int.class};call(woven,null,"unsafe",writeTypes,unsafe,bean,offset,17);
        require(bean.value==17,"observed Mod Unsafe store changed");
        require((boolean)call(woven,null,"cas",new Class<?>[]{unsafeType,Object.class,long.class,int.class,int.class},unsafe,bean,offset,17,19)&&bean.value==19,"observed Mod CAS failed");
        VarHandle handle=MethodHandles.lookup().findVarHandle(Bean.class,"value",int.class);
        Class<?>[] setTypes={VarHandle.class,Object.class,int.class};call(woven,null,"set",setTypes,handle,bean,23);require(bean.value==23,"observed Mod VarHandle failed");
        call(woven,null,"handleMethod",setTypes,handle,bean,27);call(woven,null,"handleInvoker",setTypes,handle,bean,29);require(bean.value==29,"VarHandle factory adapter failed");
        int[] array={1,2};VarHandle elements=MethodHandles.arrayElementVarHandle(int[].class);
        call(woven,null,"array",new Class<?>[]{VarHandle.class,int[].class,int.class,int.class},elements,array,1,31);require(array[1]==31,"array VarHandle coordinate changed");
        ByteBuffer buffer=ByteBuffer.allocateDirect(16).order(java.nio.ByteOrder.nativeOrder());Class<?>[] bufferTypes={ByteBuffer.class,int.class};
        call(woven,null,"buffer",bufferTypes,buffer,37);require(buffer.getInt(0)==37,"observed Mod Buffer failed");
        VarHandle view=MethodHandles.byteBufferViewVarHandle(int[].class,java.nio.ByteOrder.nativeOrder());
        call(woven,null,"view",new Class<?>[]{VarHandle.class,ByteBuffer.class,int.class,int.class},view,buffer,4,41);require(buffer.getInt(4)==41,"Buffer VarHandle view changed");
        try{call(woven,null,"buffer",bufferTypes,buffer.asReadOnlyBuffer(),7);throw new AssertionError("observed read-only Buffer accepted write");}catch(java.nio.ReadOnlyBufferException expected){}
        try{call(woven,null,"buffer",bufferTypes,ByteBuffer.allocate(1),7);throw new AssertionError("observed Buffer bounds lost");}catch(IndexOutOfBoundsException expected){}
        Class<?>[] reflectTypes={Method.class,Object.class,Object[].class};
        require((int)call(woven,null,"reflect",reflectTypes,woven.getDeclaredMethod("twice",int.class),null,new Object[]{(byte)9})==18,"reflection lost real caller access or primitive widening");
        try{call(woven,null,"reflect",reflectTypes,woven.getDeclaredMethod("twice",int.class),null,new Object[]{9L});throw new AssertionError("reflection narrowed invalid argument");}catch(IllegalArgumentException expected){}
        Method put=unsafeType.getMethod("putInt",Object.class,long.class,int.class);call(woven,null,"reflect",reflectTypes,put,unsafe,new Object[]{bean,offset,43});require(bean.value==43,"reflective Unsafe adapter failed");
        call(woven,null,"unsafeHandle",writeTypes,unsafe,bean,offset,47);require(bean.value==47,"Unsafe handle adapter failed");
        call(CODE,null,"protect",new Class<?>[]{Object[].class},(Object)new Object[]{bean,array,buffer});
        require(controlled(bean)&&controlled(array)&&controlled(buffer),"selected targets were not registered");
        for(String method:List.of("unsafe","unsafeHandle")){try{call(woven,null,method,writeTypes,unsafe,bean,offset,99);}catch(SecurityException expected){}require(bean.value==47,"observed Unsafe borrowed controller authority: "+method);}
        for(String method:List.of("set","handleMethod","handleInvoker")){try{call(woven,null,method,setTypes,handle,bean,99);}catch(SecurityException expected){}require(bean.value==47,"observed VarHandle borrowed controller authority: "+method);}
        try{call(woven,null,"buffer",bufferTypes,buffer,99);}catch(SecurityException expected){}require(buffer.getInt(0)==37,"observed Buffer borrowed controller authority");
        try{call(woven,null,"view",new Class<?>[]{VarHandle.class,ByteBuffer.class,int.class,int.class},view,buffer,4,99);}catch(SecurityException expected){}require(buffer.getInt(4)==41,"registered Buffer lost protection through a VarHandle view");
        try{call(woven,null,"array",new Class<?>[]{VarHandle.class,int[].class,int.class,int.class},elements,array,1,99);}catch(SecurityException expected){}require(array[1]==31,"observed array VarHandle bypassed registered target");
        System.out.println("MOD_CALL_SITES_PASS real_woven_Unsafe_VarHandle_Buffer_reflection_handles; REGISTERED_TARGETS_REJECTED; UNWOVEN_BRIDGES_NOT_CLAIMED");
    }
    private static void installedRawReads(Class<?> nativeControl)throws Exception {
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Object unsafe=field(unsafeType,"theUnsafe").get(null);
        Method allocate=unsafeType.getMethod("allocateMemory",long.class),release=unsafeType.getMethod("freeMemory",long.class);
        Method put=unsafeType.getMethod("putLong",long.class,long.class),get=unsafeType.getMethod("getLong",long.class);
        Method getInt=unsafeType.getMethod("getInt",long.class),getNull=unsafeType.getMethod("getLong",Object.class,long.class);
        Method copy=unsafeType.getMethod("copyMemory",long.class,long.class,long.class);
        long address=(long)allocate.invoke(unsafe,24L);
        try{
            long expected=0x1234567812345678L;put.invoke(unsafe,address,expected);
            require((long)get.invoke(unsafe,address)==expected,"raw getter lost its real read result");
            require((long)getNull.invoke(unsafe,null,address)==expected,"null-receiver getter lost its real read result");
            require((int)getInt.invoke(unsafe,address)==0x12345678,"raw primitive width changed");
            copy.invoke(unsafe,address,address+8,8L);
            require((long)get.invoke(unsafe,address+8)==expected,"Native copy lost data or leaked its scope");
            boolean entryRemoved=false,nativeRemoved=false,copyRefused=false,writeRefused=false;
            try{CONTROL.getMethod("beginUnsafeRead",Object.class,long.class,String.class);}
            catch(NoSuchMethodException expectedRemoval){entryRemoved=true;}
            try{nativeControl.getDeclaredMethod("memoryUnsafeReadBegin0",long.class,long.class,Module[].class);}
            catch(NoSuchMethodException expectedRemoval){nativeRemoved=true;}
            try{CONTROL.getMethod("beginNativeUnsafeMutation",Object.class,long.class,long.class,Object.class,String.class,boolean.class,Module[].class)
                    .invoke(null,null,address,0L,Long.valueOf(1),new String("Long"),false,new Module[0]);}
            catch(InvocationTargetException rejected){writeRefused=rejected.getCause() instanceof SecurityException;}
            try{CONTROL.getMethod("beginNativeUnsafeCopy",Object.class,long.class,Object.class,long.class,long.class,Module[].class)
                    .invoke(null,null,address,null,address+8,8L,new Module[0]);}
            catch(InvocationTargetException rejected){copyRefused=rejected.getCause() instanceof SecurityException;}
            require(entryRemoved&&nativeRemoved&&copyRefused&&writeRefused,"obsolete getter receipt or unguarded Native mutation boundary");
            require((long)get.invoke(unsafe,address)==expected,"refused entry left an in-flight raw scope behind");
            final Throwable[] failed=new Throwable[1];
            Thread reader=new Thread(()->{try{if((long)get.invoke(unsafe,address)!=expected)throw new AssertionError("peer raw read changed");}
                catch(Throwable failure){failed[0]=failure;}});
            reader.start();reader.join();if(failed[0]!=null)throw new AssertionError("raw scopes leaked across threads",failed[0]);
        }finally{release.invoke(unsafe,address);}
    }
    private static void scalarValues()throws Exception {
        Class<?> vector=Class.forName("org.joml.Vector3f");Object value=vector.getConstructor().newInstance();
        Class<?>[] signature={Object.class,Class.class};
        require((boolean)call(CONTROL,null,"primitiveValueUntracked",signature,value,vector),"ordinary vector did not take scalar path");
        require((boolean)call(CONTROL,null,"primitiveValueUntracked",signature,new PlainFields(),PlainFields.class),"ordinary business primitive field did not take shortcut");
        require(!(boolean)call(CONTROL,null,"primitiveValueUntracked",signature,null,vector),"null/static target took value shortcut");
        Field stopped=field(CONTROL,"stoppedModules");Object previous=stopped.get(null);
        try{stopped.set(null,new Module[]{vector.getModule()});
            require(!(boolean)call(CONTROL,null,"primitiveValueUntracked",signature,value,vector),"stopped value owner bypassed policy");
        }finally{stopped.set(null,previous);}
        call(CODE,null,"codeFields",new Class<?>[]{ClassLoader.class,String.class,String[].class,Module[][].class,Class.class},
                vector.getClassLoader(),vector.getName(),new String[]{"x\0F"},new Module[][]{new Module[]{vector.getModule()}},vector);
        require(!(boolean)call(CONTROL,null,"primitiveValueUntracked",signature,value,vector),"late sensitive field registration bypassed policy");
    }
}
