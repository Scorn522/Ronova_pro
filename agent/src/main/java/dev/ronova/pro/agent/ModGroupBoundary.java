package dev.ronova.pro.agent;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.JumpInsnNode;
import jdk.internal.org.objectweb.asm.tree.LookupSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.TableSwitchInsnNode;
import jdk.internal.org.objectweb.asm.tree.VarInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.SourceValue;

/** A whole Forge module is the unit of suppression; shared jars are intentionally included. */
final class ModGroupBoundary {
    private static final Pattern MOD_ID=Pattern.compile("^\\s*modId\\s*=\\s*['\"]([a-zA-Z0-9_.-]+)['\"]");
    private static final Pattern TABLE=Pattern.compile("^\\s*\\[\\[([^]]+)]]\\s*(?:#.*)?$");
    private static final Set<String> denied=ConcurrentHashMap.newKeySet();
    private static final Set<Module> exact=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Module,Set<String>> metadata=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicInteger methods=new AtomicInteger();
    private static final Set<String> unresolved=ConcurrentHashMap.newKeySet();
    static {
        String configured=System.getProperty("ronova.pro.bootStop","")+","+System.getenv().getOrDefault("RONOVA_PRO_BOOT_STOP","");
        for(String id:configured.split(",")) {
            id=id.trim().toLowerCase(java.util.Locale.ROOT);
            if(valid(id))denied.add(id);
        }
    }
    private ModGroupBoundary() {}
    private static boolean valid(String id) {
        return id.matches("[a-z0-9_.-]{2,64}") && !Set.of("ronova_pro","minecraft","forge","java").contains(id);
    }
    static Set<String> ids(Module module) {
        if(module==null)return Set.of();
        Set<String> known=metadata.get(module);if(known!=null)return known;
        try(InputStream in=module.getResourceAsStream("META-INF/mods.toml")) {
            if(in==null)return Set.of();
            String content=new String(in.readNBytes(1024*1024),StandardCharsets.UTF_8);
            Set<String> ids=new HashSet<>();boolean modEntry=false;
            for(String line:content.split("\\R")) {
                Matcher table=TABLE.matcher(line);
                if(table.matches()) {modEntry=table.group(1).trim().equals("mods");continue;}
                if(line.stripLeading().startsWith("[")) {modEntry=false;continue;}
                if(modEntry) {
                    Matcher matcher=MOD_ID.matcher(line);
                    if(matcher.find())ids.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
                }
            }
            if(ids.isEmpty())return Set.of();
            known=Set.copyOf(ids);metadata.put(module,known);return known;
        } catch(Exception unavailable) {return Set.of();}
    }
    static boolean stopped(Module module) {
        if(module==null)return false;
        synchronized(exact) {if(exact.contains(module))return true;}
        if(denied.isEmpty())return false;
        Set<String> owners=ids(module);
        if(Collections.disjoint(owners,denied))return false;
        if(!denied.containsAll(owners)) {
            unresolved.add(String.valueOf(module.getName())+":MULTI_MOD_MODULE_REQUIRES_ALL_IDS:"+owners);
            return false;
        }
        return true;
    }
    static byte[] transform(ClassLoader loader,Module module,String name,byte[] bytes) {
        if(loader==null||name==null||name.startsWith("dev/ronova/pro/agent/")
                ||name.startsWith("dev/ronova/pro/bootstrap/")||!stopped(module))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=false;
        for(MethodNode method:node.methods) {
            if(method.name.equals("<clinit>")) {
                method.instructions=new InsnList();method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.tryCatchBlocks.clear();method.localVariables=null;
                method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
                changed=true;continue;
            }
            if(method.name.equals("<init>")) {
                if(shortConstructor(node,method))changed=true;
                else unresolved.add(name+"#"+method.name+method.desc+":CONSTRUCTOR_PREFIX_UNCONTROLLED");
                continue;
            }
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0) {
                unresolved.add(name+"#"+method.name+method.desc+":ABSTRACT_OR_NATIVE");continue;
            }
            Type result=Type.getReturnType(method.desc);
            InsnList code=new InsnList();
            switch(result.getSort()) {
                case Type.VOID -> code.add(new InsnNode(Opcodes.RETURN));
                case Type.BOOLEAN,Type.BYTE,Type.CHAR,Type.SHORT,Type.INT -> {
                    code.add(new InsnNode(Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));
                }
                case Type.LONG -> {code.add(new InsnNode(Opcodes.LCONST_0));code.add(new InsnNode(Opcodes.LRETURN));}
                case Type.FLOAT -> {code.add(new InsnNode(Opcodes.FCONST_0));code.add(new InsnNode(Opcodes.FRETURN));}
                case Type.DOUBLE -> {code.add(new InsnNode(Opcodes.DCONST_0));code.add(new InsnNode(Opcodes.DRETURN));}
                default -> {
                    String type=result.getSort()==Type.OBJECT?result.getInternalName():"";
                    if(type.equals("java/util/Optional"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"empty","()Ljava/util/Optional;",false));
                    else if(type.equals("java/util/List")||type.equals("java/util/Collection")||type.equals("java/lang/Iterable"))
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/List","of","()Ljava/util/List;",true));
                    else if(type.equals("java/util/Set"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"of","()Ljava/util/Set;",true));
                    else if(type.equals("java/util/Map"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"of","()Ljava/util/Map;",true));
                    else if(type.equals("java/util/stream/Stream"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"empty","()Ljava/util/stream/Stream;",true));
                    else if(type.equals("java/util/concurrent/CompletableFuture")||type.equals("java/util/concurrent/CompletionStage")) {
                        code.add(new InsnNode(Opcodes.ACONST_NULL));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/concurrent/CompletableFuture","completedFuture",
                                "(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",false));
                    } else code.add(new InsnNode(Opcodes.ACONST_NULL));
                    code.add(new InsnNode(Opcodes.ARETURN));
                }
            }
            method.instructions=code;method.tryCatchBlocks.clear();method.localVariables=null;
            method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
            methods.incrementAndGet();changed=true;
        }
        if(!changed)return null;
        // Replaced bodies are straight-line returns. Existing constructors keep their original frames.
        // Recomputing unrelated Forge hierarchy here would make a mod stop depend on loader resources.
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean shortConstructor(ClassNode owner,MethodNode method) {
        try {
            SourceInterpreter interpreter=new SourceInterpreter(Opcodes.ASM8) { };
            AbstractInsnNode[] code=method.instructions.toArray();
            Frame<SourceValue>[] frames=new Analyzer<>(interpreter).analyze(owner.name,method);
            for(int index=0;index<code.length;index++)if(code[index] instanceof MethodInsnNode call
                    &&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")
                    &&(call.owner.equals(owner.superName)||call.owner.equals(owner.name))&&frames[index]!=null) {
                int arguments=Type.getArgumentTypes(call.desc).length;
                Frame<SourceValue> frame=frames[index];
                if(frame.getStackSize()<arguments+1)continue;
                SourceValue receiver=frame.getStack(frame.getStackSize()-arguments-1);
                if(receiver.insns.size()!=1||!(receiver.insns.iterator().next() instanceof VarInsnNode load)
                        ||load.getOpcode()!=Opcodes.ALOAD||load.var!=0)continue;
                boolean branch=false;
                for(int i=0;i<index;i++)if(code[i] instanceof JumpInsnNode||code[i] instanceof TableSwitchInsnNode
                        ||code[i] instanceof LookupSwitchInsnNode) {branch=true;break;}
                if(branch)continue;
                for(AbstractInsnNode next=call.getNext();next!=null;) {
                    AbstractInsnNode remove=next;next=next.getNext();method.instructions.remove(remove);
                }
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                method.tryCatchBlocks.clear();method.localVariables=null;
                method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
                return true;
            }
        } catch(AnalyzerException|RuntimeException unavailable) {return false;}
        return false;
    }
    static synchronized String stop(Instrumentation api,String[] ids,Module[] hints) {
        if(api==null)return "AGENT_UNAVAILABLE";
        String selection=validate(ids,hints);
        if(!selection.equals("READY"))return selection;
        for(int i=0;i<ids.length;i++) {
            denied.add(ids[i].trim().toLowerCase(java.util.Locale.ROOT));
            synchronized(exact){exact.add(hints[i]);}
        }
        return retransform(api);
    }
    static String validate(String[] ids,Module[] hints) {
        if(ids.length!=hints.length||ids.length==0)return "EMPTY_OR_MISMATCHED_SELECTION";
        for(int i=0;i<ids.length;i++) {
            String id=ids[i].trim().toLowerCase(java.util.Locale.ROOT);
            if(!valid(id)||hints[i]==null||hints[i]==Object.class.getModule())return "INVALID_TARGET:"+id;
            if(!ids(hints[i]).contains(id))return "MODULE_METADATA_MISMATCH:"+id;
            if(!new HashSet<>(Arrays.asList(ids)).containsAll(ids(hints[i])))
                return "MULTI_MOD_MODULE_REQUIRES_ALL_IDS:"+ids(hints[i]);
        }
        return "READY";
    }
    static synchronized String stopByIds(Instrumentation api,String[] ids) {
        if(api==null)return "AGENT_UNAVAILABLE";
        if(ids.length==0)return "EMPTY_SELECTION";
        for(String id:ids)if(!valid(id.trim().toLowerCase(java.util.Locale.ROOT)))return "INVALID_TARGET:"+id;
        for(String id:ids)denied.add(id.trim().toLowerCase(java.util.Locale.ROOT));
        return retransform(api);
    }
    private static String retransform(Instrumentation api) {
        int loaded=0,failed=0;
        for(Class<?> type:api.getAllLoadedClasses()) {
            if(type.isArray()||type.isPrimitive()||!stopped(type.getModule())
                    ||type.getName().startsWith("dev.ronova.pro.agent.")||type.getName().startsWith("dev.ronova.pro.bootstrap."))continue;
            if(!api.isModifiableClass(type)) {unresolved.add(type.getName()+":UNMODIFIABLE");failed++;continue;}
            try {api.retransformClasses(type);loaded++;}
            catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                unresolved.add(type.getName()+":"+failure.getClass().getSimpleName());failed++;
            }
        }
        return "TARGETS="+denied+";RETRANSFORMED_CLASSES="+loaded+";FAILED="+failed+";METHODS="+methods.get()+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();
    }
    static String state() {return "TARGETS="+denied+";METHODS="+methods.get()+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();}
}
