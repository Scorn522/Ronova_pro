package dev.ronova.pro.bootstrap;

/** A transformation scope suppresses only policy callbacks on the actual ASM implementation objects. */
public final class TransformScope {
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final class Scope {
        final Scope previous=CURRENT.get();final Thread thread=Thread.currentThread();final ClassLoader loader;boolean closed;
        Scope(Class<?> library){loader=library==null?null:library.getClassLoader();}
    }
    private TransformScope(){}
    public static Object enter(Class<?> library){
        Class<?> caller=CALLER.getCallerClass();if(caller!=CodeSourceBridge.class&&!ControlBridge.owns(caller))throw new SecurityException("ACTUAL_TRANSFORM_OWNER_REQUIRED");
        Scope scope=new Scope(library);CURRENT.set(scope);return scope;
    }
    public static void exit(Object token){
        Class<?> caller=CALLER.getCallerClass();if(caller!=CodeSourceBridge.class&&!ControlBridge.owns(caller))throw new SecurityException("ACTUAL_TRANSFORM_OWNER_REQUIRED");
        if(!(token instanceof Scope scope)||scope.thread!=Thread.currentThread())throw new IllegalStateException("TRANSFORM_SCOPE_ORDER_CHANGED");
        if(scope.closed)return;if(CURRENT.get()!=scope)throw new IllegalStateException("TRANSFORM_SCOPE_ORDER_CHANGED");
        scope.closed=true;CURRENT.set(scope.previous);
    }
    static boolean internal(Object receiver,Class<?> declaring){
        if(receiver==null||declaring==null)return false;
        String name=declaring.getName();
        if(name.startsWith("jdk.internal.org.objectweb.asm.")&&declaring.getClassLoader()==null&&declaring.getModule()==Object.class.getModule())return true;
        if(!name.startsWith("org.objectweb.asm."))return false;
        String module=declaring.getModule().getName();
        if(module==null||!java.util.Set.of("org.objectweb.asm","org.objectweb.asm.tree","org.objectweb.asm.commons","org.objectweb.asm.tree.analysis","org.objectweb.asm.util").contains(module))return false;
        for(Scope scope=CURRENT.get();scope!=null;scope=scope.previous)if(declaring.getClassLoader()==scope.loader&&receiver.getClass().getClassLoader()==scope.loader)return true;
        return false;
    }
}
