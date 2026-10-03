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
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.TypeInsnNode;
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
    private record Returns(String mode,String uuid) { }
    private static final Map<Module,Returns> returns=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Module,Set<String>> groupGaps=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Module,String> codeRetirementGaps=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Class<?>[]> batch=new ThreadLocal<>();
    static String returnMode(String mode) {
        String normalized=mode.trim().toLowerCase(java.util.Locale.ROOT);
        if(!Set.of("default","null","empty","uuid-fixed","uuid-each","invalid-id").contains(normalized))
            throw new IllegalArgumentException("INVALID_RETURN_POLICY:"+mode);
        return normalized;
    }
    private static void gap(Module module,String value) {
        unresolved.add(value);
        synchronized(groupGaps) {groupGaps.computeIfAbsent(module,ignored->ConcurrentHashMap.newKeySet()).add(value);}
    }
    static void failed(Module module,String name,Throwable failure) {gap(module,name+":TRANSFORM_FAILED:"+failure.getClass().getSimpleName());}
    static void beginBatch(Instrumentation api) { if(api!=null)batch.set(RecoveryAgent.loadedClasses()); }
    static void endBatch() {batch.remove();}
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
        if(owners.stream().anyMatch(id->!valid(id))) {
            gap(module,String.valueOf(module.getName())+":RESERVED_MOD_IN_SHARED_MODULE:"+owners);
            return false;
        }
        // A file/module is indivisible; selecting an owner includes all its declared aliases.
        denied.addAll(owners);
        synchronized(exact) {exact.add(module);}
        returns.computeIfAbsent(module,ignored->{
            Set<String> policies=new HashSet<>();for(String id:owners){String policy=System.getProperty("ronova.pro.bootReturns."+id);if(policy!=null)policies.add(returnMode(policy));}
            if(policies.size()>1)throw new IllegalArgumentException("SHARED_MODULE_RETURN_CONFLICT:"+owners);
            String mode=policies.isEmpty()?System.getProperty("ronova.pro.bootReturns","default"):policies.iterator().next();
            return new Returns(returnMode(mode),java.util.UUID.randomUUID().toString());
        });
        return true;
    }
    static byte[] transform(ClassLoader loader,Module module,String name,Class<?> actualType,byte[] bytes) {
        if(loader==null||name==null||!stopped(module))return null;
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
                boolean modCarrier=node.visibleAnnotations!=null&&node.visibleAnnotations.stream()
                        .anyMatch(annotation->annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod;"));
                if(modCarrier) {
                    // Forge still needs its inert @Mod carrier; ordinary business allocations are refused.
                    if(shortConstructor(node,method))changed=true;
                    else gap(module,name+"#"+method.name+method.desc+":MOD_CARRIER_PREFIX_UNCONTROLLED");
                } else {
                    InsnList rejected=new InsnList();
                    rejected.add(new TypeInsnNode(Opcodes.NEW,"java/lang/IllegalStateException"));
                    rejected.add(new InsnNode(Opcodes.DUP));rejected.add(new LdcInsnNode("RONOVA_MOD_GROUP_CREATION_REFUSED"));
                    rejected.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/IllegalStateException","<init>","(Ljava/lang/String;)V",false));
                    rejected.add(new InsnNode(Opcodes.ATHROW));replace(method,rejected);changed=true;
                }
                continue;
            }
            if((method.access&Opcodes.ACC_ABSTRACT)!=0)continue;
            if((method.access&Opcodes.ACC_NATIVE)!=0) {
                if(!nativeControlled(actualType,method.name,method.desc))gap(module,name+"#"+method.name+method.desc+":NATIVE_BINDING_UNCONTROLLED");continue;
            }
            suppressExternal(module,method);
            methods.incrementAndGet();changed=true;
        }
        if(!changed)return null;
        // Replaced bodies are straight-line returns. Existing constructors keep their original frames.
        // Recomputing unrelated Forge hierarchy here would make a mod stop depend on loader resources.
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static void replace(MethodNode method,InsnList code) {
        method.instructions=code;method.tryCatchBlocks.clear();method.localVariables=null;
        method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;
    }
    static void externalGap(Module module,String value){gap(module,value);}
    static void externalResolved(Module module,String value){
        synchronized(groupGaps){
            Set<String> gaps=groupGaps.get(module);if(gaps!=null)gaps.remove(value);
            if(groupGaps.values().stream().noneMatch(remaining->remaining.contains(value)))unresolved.remove(value);
        }
    }
    static void suppressExternal(Module module,MethodNode method){
        Returns policy=returns.get(module);if(policy==null)throw new IllegalStateException("STOPPED_RETURN_POLICY_REQUIRED");
    Type result=Type.getReturnType(method.desc);
    InsnList code=new InsnList();
    switch(result.getSort()) {
        case Type.VOID -> code.add(new InsnNode(Opcodes.RETURN));
        case Type.BOOLEAN,Type.BYTE,Type.CHAR,Type.SHORT,Type.INT -> {
            code.add(new InsnNode(result.getSort()==Type.INT&&policy.mode().equals("invalid-id")?Opcodes.ICONST_M1:Opcodes.ICONST_0));code.add(new InsnNode(Opcodes.IRETURN));
        }
        case Type.LONG -> {code.add(new InsnNode(Opcodes.LCONST_0));code.add(new InsnNode(Opcodes.LRETURN));}
        case Type.FLOAT -> {code.add(new InsnNode(Opcodes.FCONST_0));code.add(new InsnNode(Opcodes.FRETURN));}
        case Type.DOUBLE -> {code.add(new InsnNode(Opcodes.DCONST_0));code.add(new InsnNode(Opcodes.DRETURN));}
        default -> {
            String type=result.getSort()==Type.OBJECT?result.getInternalName():"";
            if((policy.mode().equals("uuid-fixed")||policy.mode().equals("uuid-each"))
                    &&(type.equals("java/util/UUID")||type.equals("java/lang/String"))) {
                if(policy.mode().equals("uuid-fixed")) {
                    code.add(new LdcInsnNode(policy.uuid()));
                    if(type.equals("java/util/UUID"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/UUID","fromString","(Ljava/lang/String;)Ljava/util/UUID;",false));
                } else {
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/UUID","randomUUID","()Ljava/util/UUID;",false));
                    if(type.equals("java/lang/String"))code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/util/UUID","toString","()Ljava/lang/String;",false));
                }
            } else if(policy.mode().equals("null"))code.add(new InsnNode(Opcodes.ACONST_NULL));
            else if(result.getSort()==Type.ARRAY&&policy.mode().equals("empty")) {
                String component=result.getDescriptor().substring(1);
                code.add(new InsnNode(Opcodes.ICONST_0));
                if(component.length()==1)code.add(new IntInsnNode(Opcodes.NEWARRAY,switch(component.charAt(0)) {
                    case 'Z'->Opcodes.T_BOOLEAN;case 'B'->Opcodes.T_BYTE;case 'C'->Opcodes.T_CHAR;case 'S'->Opcodes.T_SHORT;
                    case 'I'->Opcodes.T_INT;case 'J'->Opcodes.T_LONG;case 'F'->Opcodes.T_FLOAT;case 'D'->Opcodes.T_DOUBLE;
                    default->throw new IllegalArgumentException("ARRAY_COMPONENT:"+component);
                }));
                else code.add(new TypeInsnNode(Opcodes.ANEWARRAY,component.startsWith("L")?component.substring(1,component.length()-1):component));
            } else if(Set.of("java/util/Optional","java/util/OptionalInt","java/util/OptionalLong","java/util/OptionalDouble").contains(type))
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"empty","()L"+type+";",false));
            else if(type.equals("java/util/List")||type.equals("java/util/Collection")||type.equals("java/lang/Iterable"))
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/List","of","()Ljava/util/List;",true));
            else if(type.equals("java/util/Set"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"of","()Ljava/util/Set;",true));
            else if(type.equals("java/util/Map"))code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"of","()Ljava/util/Map;",true));
            else if(type.equals("java/util/Iterator")||type.equals("java/util/Enumeration"))
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Collections",type.equals("java/util/Iterator")?"emptyIterator":"emptyEnumeration","()L"+type+";",false));
            else if(Set.of("java/util/stream/Stream","java/util/stream/IntStream","java/util/stream/LongStream","java/util/stream/DoubleStream").contains(type))
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,type,"empty","()L"+type+";",true));
            else if(Set.of("java/util/concurrent/CompletableFuture","java/util/concurrent/CompletionStage","java/util/concurrent/Future").contains(type)) {
                code.add(new InsnNode(Opcodes.ACONST_NULL));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/concurrent/CompletableFuture","completedFuture",
                        "(Ljava/lang/Object;)Ljava/util/concurrent/CompletableFuture;",false));
            } else code.add(new InsnNode(Opcodes.ACONST_NULL));
            code.add(new InsnNode(Opcodes.ARETURN));
        }
    }
    replace(method,code);
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
    static synchronized String stop(Instrumentation api,String[] ids,Module[] hints,String mode) {
        if(api==null)return "AGENT_UNAVAILABLE";
        mode=returnMode(mode);
        String selection=validate(ids,hints);
        if(!selection.equals("READY"))return selection;
        for(int i=0;i<ids.length;i++) {
            denied.add(ids[i].trim().toLowerCase(java.util.Locale.ROOT));
            synchronized(exact){exact.add(hints[i]);}
            Returns old=returns.get(hints[i]);
            returns.put(hints[i],new Returns(mode,old==null?java.util.UUID.randomUUID().toString():old.uuid()));
        }
        return retransform(api,new HashSet<>(Arrays.asList(hints)));
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
    static synchronized String stopByIds(Instrumentation api,String[] ids,String mode) {
        if(api==null)return "AGENT_UNAVAILABLE";
        if(ids.length==0)return "EMPTY_SELECTION";
        for(String id:ids)if(!valid(id.trim().toLowerCase(java.util.Locale.ROOT)))return "INVALID_TARGET:"+id;
        Set<String> selected=new HashSet<>(Arrays.asList(ids));
        Map<String,Module> targets=new java.util.LinkedHashMap<>();
        for(Class<?> type:RecoveryAgent.loadedClasses()) {
            Set<String> owners=ids(type.getModule());
            if(Collections.disjoint(owners,selected))continue;
            if(!selected.containsAll(owners))return "CLIENT_SHARED_MODULE_NOT_FULLY_AUTHORIZED:"+owners;
            for(String owner:owners)targets.put(owner,type.getModule());
        }
        if(targets.isEmpty())return "MOD_GROUP_NOT_LOADED";
        return stop(api,targets.keySet().toArray(String[]::new),targets.values().toArray(Module[]::new),mode);
    }
    private static String retransform(Instrumentation api,Set<Module> selected) {
        int loaded=0,failed=0;
        Class<?>[] snapshot=batch.get();if(snapshot==null)snapshot=RecoveryAgent.loadedClasses();
        Set<Class<?>> external=Collections.newSetFromMap(new IdentityHashMap<>());
        external.addAll(Arrays.asList(ExternalCodeImages.affected(snapshot,selected.toArray(Module[]::new))));
        for(Class<?> type:snapshot) {
            if(type.isArray()||type.isPrimitive())continue;
            Module module=RecoveryAgent.logicalModule(type);boolean contribution=external.contains(type);if((!selected.contains(module)||!stopped(module))&&!contribution)continue;
            if(!api.isModifiableClass(type)) {
                if(type.isHidden()&&RecoveryAgent.definition(type)!=null)continue;
                if(contribution)ExternalCodeImages.failed(selected.toArray(Module[]::new),type.getName()+":EXTERNAL_CLASS_UNMODIFIABLE");else gap(module,type.getName()+":UNMODIFIABLE");failed++;continue;
            }
            try {api.retransformClasses(type);loaded++;}
            catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                if(contribution)ExternalCodeImages.failed(selected.toArray(Module[]::new),type.getName()+":EXTERNAL_RETRANSFORM_FAILED:"+failure.getClass().getSimpleName());else gap(module,type.getName()+":"+failure.getClass().getSimpleName());failed++;
            }
        }
        Returns policy=returns.get(selected.iterator().next());
        return "TARGETS="+denied+";RETRANSFORMED_CLASSES="+loaded+";FAILED="+failed+";METHODS="+methods.get()+";RETURNS="+policy.mode()+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();
    }
    static String state() {return "TARGETS="+denied+";METHODS="+methods.get()+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();}
    static String mode(Module module) {Returns policy=returns.get(module);return policy==null?"default":policy.mode();}
    private static boolean nativeControlled(Class<?> type,String name,String descriptor){
        if(type==null)return false;
        try{
            Class<?> control=Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null);
            for(var method:type.getDeclaredMethods())if(method.getName().equals(name)&&Type.getMethodDescriptor(method).equals(descriptor))
                return Boolean.TRUE.equals(control.getMethod("bindingControlled",java.lang.reflect.Method.class).invoke(null,method));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){return false;}return false;
    }
    private static void refreshNativeGaps(Module module){
        if(!stopped(module))return;
        var gaps=groupGaps.computeIfAbsent(module,ignored->ConcurrentHashMap.newKeySet());
        try{
            Class<?> client=Class.forName("dev.ronova.pro.bootstrap.ClientBridge",false,null);
            client.getMethod("retire",Module[].class).invoke(null,(Object)new Module[]{module});
            String retirement=String.valueOf(client.getMethod("retirementGap",Module.class).invoke(null,module));
            for(String prior:Set.copyOf(gaps))if(prior.startsWith("CLIENT_RETIREMENT_PENDING:")){gaps.remove(prior);unresolved.remove(prior);}
            if(!retirement.isEmpty())gap(module,retirement);
        }catch(ReflectiveOperationException unavailable){gap(module,"CLIENT_RETIREMENT_QUERY_UNAVAILABLE");}
        try{
            Class<?> network=Class.forName("dev.ronova.pro.bootstrap.NetworkBridge",false,null);
            network.getMethod("retire",Module[].class).invoke(null,(Object)new Module[]{module});
            String retirement=String.valueOf(network.getMethod("retirementGap",Module.class).invoke(null,module));
            for(String prior:Set.copyOf(gaps))if(prior.startsWith("NETWORK_RETIREMENT_PENDING:")){gaps.remove(prior);unresolved.remove(prior);}
            if(!retirement.isEmpty())gap(module,retirement);
        }catch(ReflectiveOperationException unavailable){gap(module,"NETWORK_RETIREMENT_QUERY_UNAVAILABLE");}
        try{
            String retirement=String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("retirementGap",Module.class).invoke(null,module));
            for(String prior:Set.copyOf(gaps))if(prior.startsWith("NATIVE_RETIREMENT_PENDING:")){gaps.remove(prior);unresolved.remove(prior);}
            if(!retirement.isEmpty())gap(module,retirement);
        }catch(ReflectiveOperationException unavailable){gap(module,"NATIVE_RETIREMENT_QUERY_UNAVAILABLE");}
        try{
            String retirement=String.valueOf(Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null).getMethod("codeRetirementGap",Module.class).invoke(null,module));
            String current=retirement.isEmpty()?"":"CODE_RETIREMENT:"+retirement,prior=codeRetirementGaps.put(module,current);
            if(prior!=null&&!prior.isEmpty()){gaps.remove(prior);unresolved.remove(prior);}
            if(!current.isEmpty())gap(module,current);
        }catch(ReflectiveOperationException unavailable){
            String current="CODE_RETIREMENT:EXTERNAL_CODE_VM_STATE_UNAVAILABLE",prior=codeRetirementGaps.put(module,current);
            if(prior!=null&&!prior.equals(current)&&!prior.isEmpty()){gaps.remove(prior);unresolved.remove(prior);}gap(module,current);
        }
        for(Class<?> type:RecoveryAgent.loadedClasses())if(!type.isArray()&&!type.isPrimitive()&&RecoveryAgent.logicalModule(type)==module){
            try{for(var method:type.getDeclaredMethods())if(java.lang.reflect.Modifier.isNative(method.getModifiers())){
                String key=type.getName().replace('.','/')+"#"+method.getName()+Type.getMethodDescriptor(method)+":NATIVE_BINDING_UNCONTROLLED";
                if(nativeControlled(type,method.getName(),Type.getMethodDescriptor(method))){gaps.remove(key);unresolved.remove(key);}
                else gap(module,key);
            }}catch(RuntimeException|LinkageError unavailable){/* Keep that exact uncontrolled declaration pending. */}
        }
    }
    static String state(Module module) {
        refreshNativeGaps(module);
        String continuation=ExternalCodeDefinitions.pending(module);
        Set<String> gaps=groupGaps.getOrDefault(module,Set.of());Returns policy=returns.get(module);
        return "MODS="+ids(module)+";STATE="+(stopped(module)?gaps.isEmpty()&&continuation.isEmpty()?"STOPPED":"STOPPED_PARTIAL":"ACTIVE")
                +";RETURNS="+(policy==null?"default":policy.mode())+";UNRESOLVED="+(gaps.size()+(continuation.isEmpty()?0:1))+";GAPS="+gaps.stream().limit(8).toList()+";FLOW_CONTINUATION="+continuation;
    }
}
