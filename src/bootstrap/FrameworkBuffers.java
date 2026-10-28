package dev.ronova.pro.bootstrap;

import java.lang.ref.WeakReference;
import java.nio.Buffer;
import java.util.*;

/** Controller serialization scratch and the actual Forge early-window draw. */
public final class FrameworkBuffers {
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private record Role(String name,WeakReference<ClassLoader> loader,WeakReference<Module> module){}
    private static final List<Role> ROLES=new ArrayList<>();
    private static final ThreadLocal<Draw> DRAW=new ThreadLocal<>();
    private static final ThreadLocal<Scalar> SCALAR=new ThreadLocal<>();
    private static final ThreadLocal<Scratch> SCRATCH=new ThreadLocal<>();
    private static final Set<String> SCALAR_OPERATIONS=Set.of("capacity","remaining","hasRemaining","position","limit","clear","flip","rewind","isDirect","isReadOnly",
            "put","putFloat","putInt","putDouble","putLong","putShort","putChar");
    static final class Draw {
        final Draw previous=DRAW.get();final Thread thread=Thread.currentThread();final Module[] sources;
        boolean tainted,closed;
        Draw(Module[] sources){
            this.sources=sources;
            // Every exact Draw is intrinsically controller metadata, just as
            // Scalar is. Its actual source array and thread-local holders keep
            // ordinary identity protection and late-publication checks.
            CodeSourceBridge.mutationControls(sources,DRAW,SCALAR);
        }
    }
    static final class Scalar {
        final Scalar previous=SCALAR.get();final Draw draw;final Thread thread=Thread.currentThread();
        boolean closed,checking;
        Scalar(Draw draw){this.draw=draw;}
    }
    private static final class Scratch {
        final Scratch previous=SCRATCH.get();final Thread thread=Thread.currentThread();final Class<?> vector;
        final IdentityHashMap<byte[],Boolean> arrays=new IdentityHashMap<>();boolean closed;
        Scratch(Class<?> vector){this.vector=vector;CodeSourceBridge.mutationControls(this,arrays,SCRATCH);}
    }
    private FrameworkBuffers(){}
    public static void expect(String name,ClassLoader loader,Module module){
        if(!ControlBridge.owns(CALLER.getCallerClass()))throw new SecurityException("ACTUAL_FRAMEWORK_AGENT_REQUIRED");
        synchronized(ROLES){
            for(Role role:ROLES)if(role.name.equals(name)&&role.loader.get()==loader&&role.module.get()==module)return;
            Role role=new Role(name,new WeakReference<>(loader),new WeakReference<>(module));ROLES.add(role);
            CodeSourceBridge.mutationControls(ROLES,role,role.loader,role.module);
        }
    }
    private static boolean role(Class<?> type,String name){
        if(type==null||!type.getName().equals(name))return false;
        synchronized(ROLES){for(Role role:ROLES)if(role.name.equals(name)&&role.loader.get()==type.getClassLoader()
                &&role.module.get()==type.getModule())return true;}
        return false;
    }
    private static boolean pure(Module[] sources){
        for(Module source:sources)if(source==null||ControlBridge.producer(source))return false;
        return true;
    }
    public static Object renderEnter(Class<?> declaring){
        if(CALLER.getCallerClass()!=declaring||!role(declaring,"net.minecraftforge.fml.earlydisplay.DisplayWindow"))
            throw new SecurityException("ACTUAL_EARLY_DISPLAY_RENDER_REQUIRED");
        Module[] sources=ControlBridge.invokingSources();
        Draw draw=new Draw(sources);draw.tainted=!pure(sources)||ControlBridge.sourceUnknown();DRAW.set(draw);return draw;
    }
    public static void renderExit(Object value){
        if(!role(CALLER.getCallerClass(),"net.minecraftforge.fml.earlydisplay.DisplayWindow")
                ||!(value instanceof Draw draw)||draw.thread!=Thread.currentThread()||DRAW.get()!=draw||draw.closed)
            throw new SecurityException("ACTUAL_EARLY_DISPLAY_EXIT_REQUIRED");
        draw.closed=true;if(draw.previous==null)DRAW.remove();else DRAW.set(draw.previous);
    }
    static void modEntering(Module module){
        Draw draw=DRAW.get();if(draw!=null&&ControlBridge.producer(module))draw.tainted=true;
    }
    public static void earlyBuffer(Buffer buffer,Class<?> declaring){
        if(CALLER.getCallerClass()!=declaring||!role(declaring,"net.minecraftforge.fml.earlydisplay.SimpleBufferBuilder"))
            throw new SecurityException("ACTUAL_EARLY_DISPLAY_BUFFER_REQUIRED");
        Module[] sources=ControlBridge.invokingSources();
        if(pure(sources)&&!ControlBridge.sourceUnknown())ResourceBridge.earlyBuffer(buffer);
    }
    static Object beginBuffer(Object receiver,Object[] arguments,String method){
        Draw draw=DRAW.get();
        if(draw==null||draw.closed||draw.tainted||!(receiver instanceof Buffer buffer))return null;
        if(!SCALAR_OPERATIONS.contains(method))return null;
        for(Object argument:arguments)if(!(argument instanceof Number||argument instanceof Character||argument instanceof Boolean))return null;
        if(!ResourceBridge.earlyBufferSources(buffer))return null;
        Scalar scalar=new Scalar(draw);SCALAR.set(scalar);return scalar;
    }
    static boolean finishBuffer(Object value){
        if(!(value instanceof Scalar scalar))return false;
        if(scalar.thread!=Thread.currentThread())
            throw new SecurityException("ACTUAL_EARLY_BUFFER_EXIT_REQUIRED");
        if(scalar.closed)return true;
        if(SCALAR.get()!=scalar)throw new SecurityException("ACTUAL_EARLY_BUFFER_EXIT_REQUIRED");
        restore(scalar);
        return true;
    }
    private static void restore(Scalar scalar){scalar.closed=true;if(scalar.previous==null)SCALAR.remove();else SCALAR.set(scalar.previous);}
    static Module[] bufferSources(){
        Scalar scalar=SCALAR.get();
        if(scalar==null||scalar.closed||scalar.checking||scalar.draw.closed||scalar.draw.tainted)return null;
        scalar.checking=true;
        try{
            if(ControlBridge.sourceUnknown()||!pure(ControlBridge.scopedSources())){scalar.draw.tainted=true;return null;}
            return scalar.draw.sources;
        }finally{scalar.checking=false;}
    }
    public static Object serializationEnter(Class<?> vector){
        if(!ControlBridge.owns(CALLER.getCallerClass())||!role(vector,"org.objectweb.asm.ByteVector"))
            throw new SecurityException("ACTUAL_AGENT_SERIALIZATION_REQUIRED");
        Scratch scratch=new Scratch(vector);SCRATCH.set(scratch);return scratch;
    }
    public static void serializationExit(Object value){
        if(!ControlBridge.owns(CALLER.getCallerClass())||!(value instanceof Scratch scratch)
                ||scratch.thread!=Thread.currentThread()||SCRATCH.get()!=scratch||scratch.closed)
            throw new SecurityException("ACTUAL_AGENT_SERIALIZATION_EXIT_REQUIRED");
        scratch.closed=true;scratch.arrays.clear();if(scratch.previous==null)SCRATCH.remove();else SCRATCH.set(scratch.previous);
    }
    public static byte[] generated(byte[] array,Class<?> declaring){
        Scratch scratch=SCRATCH.get();
        if(scratch!=null&&!scratch.closed&&scratch.vector==declaring&&CALLER.getCallerClass()==declaring)
            scratch.arrays.put(array,Boolean.TRUE);
        return array;
    }
    static boolean scratch(byte[] array){
        Scratch scratch=SCRATCH.get();
        // A business/control publication revokes the shortcut immediately.
        // No negative ownership/permission verdict is cached with the array.
        return scratch!=null&&!scratch.closed&&scratch.arrays.containsKey(array)
                &&!BackingBridge.known(array)&&!ControlBridge.sourcePublicationActive();
    }
}
