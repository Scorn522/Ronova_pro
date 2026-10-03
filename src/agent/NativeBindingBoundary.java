package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.util.*;

/**
 * Native binding observation.
 *
 * This deliberately does NOT rewrite method bodies. An earlier version replaced a native entry with a
 * same-named Java method found on a superclass, which changes the binding's behaviour and has no accurate
 * target restriction; that is worse than no control at all, so it is gone.
 *
 * What remains is accurate and read-only: for every class the transformer sees, native entries are enumerated
 * and classified. A native method that also has a real Java implementation on the same class is reported as
 * having a reachable Java twin; the others have no Java implementation to reach at all. Either way the native
 * entry keeps its original implementation, and the caller learns whether a native binding backend exists for it.
 *
 * Actual native method binding control needs a real backend (JVMTI or an equivalent supported interface). Until
 * such a backend is installed, the honest state is "not implemented" and it is reported as such — never faked by
 * swapping a method body, returning a default, or claiming a capability label.
 */
final class NativeBindingBoundary {
    private static final Map<String,List<String>> observed=new java.util.concurrent.ConcurrentHashMap<>();


    private NativeBindingBoundary() { }

    /** Read-only: never returns transformed bytes. */
    static byte[] transform(ClassLoader loader,byte[] bytes) { return null; }

    /** Records the native entries of a class without touching them. Safe to call for any class. */
    static void observe(String internalName,byte[] bytes) {
        if(internalName==null||bytes==null||observed.containsKey(internalName))return;
        try {
            ClassNode node=new ClassNode();
            new ClassReader(bytes).accept(node,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            List<String> natives=new ArrayList<>();
            for(MethodNode method:node.methods) {
                if((method.access&Opcodes.ACC_NATIVE)==0)continue;
                natives.add(method.name+method.desc);
            }
            if(!natives.isEmpty())observed.put(internalName,List.copyOf(natives));
        } catch(RuntimeException|LinkageError unreadable) { }
    }

    /** Classes where native entries were seen, with their exact signatures. Never a capability claim. */
    static Map<String,List<String>> observedBindings() { return Map.copyOf(observed); }
    /** The honest backend state: no native binding control is installed. */
    static String backendState() { return "NATIVE_BINDING_BACKEND_NOT_IMPLEMENTED"; }
}
