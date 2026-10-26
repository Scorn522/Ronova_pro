package dev.ronova.pro;

import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.lang.invoke.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;

/** Focused metadata regression; no transformed JVM, Native or gameplay claims. */
public final class RuntimeHotPathCheck {
    private static final String PREFIX="dev.ronova.pro.bootstrap.";
    private static final Class<?> CODE=type("CodeSourceBridge"),CONTROL=type("ControlBridge"),RESOURCE=type("ResourceBridge");
    public static final class Bean { public int value; }
    public static final class ConstantBean { public static int value; }
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
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("installed-memory-writers")){installedMemoryWriters();return;}
        origins();publications();registrations();resources();
        System.out.println("RUNTIME_HOT_PATH_CHECKS_PASS origins publication_lifetime field_gate_roles late_registration resource_subclasses live_process_views; METADATA_ONLY");
    }
    private static void installedMemoryWriters()throws Exception {
        Class<?> nativeControl=Class.forName(PREFIX+"NativeControl",false,null);
        require((boolean)nativeControl.getMethod("available").invoke(null),"actual premain Native backend required");
        require(CONTROL.getClassLoader()==null,"actual bootstrap bridge required");
        Bean bean=new Bean();VarHandle value=MethodHandles.lookup().findVarHandle(Bean.class,"value",int.class);
        value.setVolatile(bean,7);require(value.compareAndSet(bean,7,11),"legitimate VarHandle CAS refused");
        require((int)value.getAndAdd(bean,2)==11&&bean.value==13,"legitimate VarHandle update changed");
        var lock=new java.util.concurrent.locks.ReentrantLock();lock.lock();
        try{require(lock.isHeldByCurrentThread(),"legitimate JDK lock CAS refused");}finally{lock.unlock();}
        require(java.util.concurrent.CompletableFuture.completedFuture(3).thenApply(n->n+4).join()==7,"legitimate task stage failed");
        ByteBuffer buffer=ByteBuffer.allocateDirect(16);buffer.putLong(0,0x123456789abcdefL);
        require(buffer.getLong(0)==0x123456789abcdefL,"real buffer read/write lifecycle changed");
        boolean refused=false;
        try{CONTROL.getMethod("beginHandleMutation",Object.class,long.class,String.class).invoke(null,bean,0L,"Int");}
        catch(InvocationTargetException failure){
            refused=failure.getCause() instanceof SecurityException&&"ACTUAL_HANDLE_WRITE_REQUIRED".equals(failure.getCause().getMessage());
        }
        require(refused,"foreign reflective caller borrowed the handle writer entry");
        System.out.println("INSTALLED_MEMORY_WRITERS_PASS real_Native VarHandle_CAS JDK_lock CF_stage direct_buffer foreign_caller_refused; NO_GAMEPLAY_CLAIM");
    }
}
