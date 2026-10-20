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
import dev.ronova.pro.launch.ModMetadata;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.InsnList;
import jdk.internal.org.objectweb.asm.tree.InsnNode;
import jdk.internal.org.objectweb.asm.tree.LabelNode;
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
    static final String MOD_CONTAINER="net/minecraftforge/fml/javafmlmod/FMLModContainer";
    private static volatile boolean creationGuarded;
    private static final Set<String> denied=ConcurrentHashMap.newKeySet();
    private static final Set<Module> exact=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Module> protectedGroups=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<String> bootProtected=ConcurrentHashMap.newKeySet();
    private static final Map<String,Set<String>> fileOwners=new ConcurrentHashMap<>();
    private static final Map<Module,String> moduleFiles=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Module,Set<String>> metadata=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicInteger methods=new AtomicInteger();
    private static final Set<String> unresolved=ConcurrentHashMap.newKeySet();
    record Returns(String mode,String fixedUuid) {
        Returns(String mode){this(mode,mode.equals("uuid-fixed")?java.util.UUID.randomUUID().toString():null);}
    }
    private static final Map<Module,Returns> returns=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final class Publication {
        final Thread owner=Thread.currentThread();boolean pending;
    }
    private static final Map<Module,Publication> publishing=new IdentityHashMap<>();
    private static final Map<Module,Returns> published=new IdentityHashMap<>();
    private static final Map<Module,Set<String>> groupGaps=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Module,String> codeRetirementGaps=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ThreadLocal<Class<?>[]> batch=new ThreadLocal<>();
    static String returnMode(String mode) {
        if(mode==null)throw new IllegalArgumentException("INVALID_RETURN_POLICY:null");
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
        String protectedIds=System.getProperty("ronova.pro.bootProtect","")+","+System.getenv().getOrDefault("RONOVA_PRO_BOOT_PROTECT","");
        for(String raw:protectedIds.split(",")){String id=raw.trim().toLowerCase(java.util.Locale.ROOT);if(valid(id))bootProtected.add(id);}
        String index=System.getProperty("ronova.pro.modFiles","");
        if(!index.isEmpty())try(var reader=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(index),StandardCharsets.UTF_8)){
            java.util.Properties files=new java.util.Properties();files.load(reader);
            for(String file:files.stringPropertyNames()){
                Set<String> owners=new HashSet<>();for(String id:files.getProperty(file).split(","))if(id.matches("[a-z0-9_.-]{2,64}"))owners.add(id);
                fileOwners.put(file,Set.copyOf(owners));
            }
        }catch(java.io.IOException unavailable){throw new IllegalStateException("PRELAUNCH_MOD_FILES_UNAVAILABLE",unavailable);}
    }
    static void protectControls(){ControlImages.protect(denied,exact,protectedGroups,bootProtected,fileOwners,moduleFiles,metadata,returns,
            publishing,published,groupGaps,codeRetirementGaps,unresolved,batch,methods);}
    private ModGroupBoundary() {}
    private static boolean valid(String id) {
        return id.matches("[a-z0-9_.-]{2,64}") && !Set.of("ronova_pro","minecraft","forge","java").contains(id);
    }
    static Set<String> ids(Module module) {
        if(module==null)return Set.of();
        Set<String> known=metadata.get(module);if(known!=null)return known;
        // A shared unnamed module is a loader-wide identity, not a Mod file.
        // Its resource lookup can return another Mod's metadata.
        if(!module.isNamed())return Set.of();
        try(InputStream in=module.getResourceAsStream("META-INF/mods.toml")) {
            if(in==null)return Set.of();
            Set<String> ids=ModMetadata.ids(in);
            if(ids.isEmpty())return Set.of();
            return rememberIds(module,ids);
        } catch(Exception unavailable) {return Set.of();}
    }
    private static Set<String> rememberIds(Module module,Set<String> observed){
        synchronized(metadata){
            Set<String> prior=metadata.get(module);
            if(prior!=null&&prior.containsAll(observed))return prior;
            Set<String> combined=new HashSet<>(observed);if(prior!=null)combined.addAll(prior);
            Set<String> known=Set.copyOf(combined);metadata.put(module,known);return known;
        }
    }
    static void observeDefinition(Module module,java.security.ProtectionDomain domain){
        if(module==null||!module.isNamed()||domain==null||domain.getCodeSource()==null)return;
        String file=sourceFile(domain.getCodeSource().getLocation());if(file==null)return;
        Set<String> owners=fileOwners.get(file);
        if(owners==null)try{
            var path=java.nio.file.Path.of(file);boolean archive=java.nio.file.Files.isRegularFile(path);
            Set<String> discovered=ModMetadata.ids(path);owners=Set.copyOf(discovered);
            // A successfully read archive can have no Mod aliases. Reuse that
            // fact instead of recursively opening it for every class definition.
            // Failed reads and early Module reader misses remain retryable.
            if(archive||!owners.isEmpty()){
                Set<String> prior=fileOwners.putIfAbsent(file,owners);if(prior!=null)owners=prior;
            }
        }catch(java.io.IOException|RuntimeException unavailable){return;}
        if(owners.isEmpty())return;
        moduleFiles.putIfAbsent(module,file);
        owners=rememberIds(module,owners);
        if(!Collections.disjoint(owners,bootProtected))protectDiscovered(module);
        if(stopped(module))RecoveryAgent.publishStoppedModule(module);
    }
    private static String sourceFile(java.net.URL source){
        if(source==null)return null;
        try{
            if(source.getProtocol().equals("file"))return java.nio.file.Path.of(source.toURI()).toAbsolutePath().normalize().toString();
            String path=source.getPath();
            int bang=path.indexOf("!/");if(bang>=0)path=path.substring(0,bang);
            // A jar:file URI must retain escaping until Path decodes it. A '+',
            // space or encoded '#' in the physical filename is not form data.
            if(path.startsWith("file:"))return java.nio.file.Path.of(java.net.URI.create(path)).toAbsolutePath().normalize().toString();
            path=java.net.URLDecoder.decode(path.replace("+","%2B"),StandardCharsets.UTF_8);
            if(source.getProtocol().equals("union")){
                int union=path.lastIndexOf('#');
                if(union>=0&&path.substring(union+1).matches("[0-9]+"))path=path.substring(0,union);
            }
            if(path.matches("^/[A-Za-z]:/.*"))path=path.substring(1);
            return java.nio.file.Path.of(path).toAbsolutePath().normalize().toString();
        }catch(java.net.URISyntaxException|RuntimeException unavailable){return null;}
    }
    static Module[] modules(String[] requested){
        Set<String> selected=new HashSet<>();if(requested!=null)for(String id:requested)if(id!=null)selected.add(id.trim().toLowerCase(java.util.Locale.ROOT));
        Set<Module> result=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(metadata){for(var entry:metadata.entrySet())if(!Collections.disjoint(entry.getValue(),selected))result.add(entry.getKey());}
        for(Class<?> type:RecoveryAgent.loadedClasses())if(!Collections.disjoint(ids(type.getModule()),selected))result.add(type.getModule());
        return result.toArray(Module[]::new);
    }
    static boolean protectedModule(Module module){
        if(module==null)return false;
        synchronized(protectedGroups){if(protectedGroups.contains(module))return true;}
        if(Collections.disjoint(ids(module),bootProtected))return false;
        protectDiscovered(module);return true;
    }
    private static void protectDiscovered(Module module){
        if(ids(module).stream().anyMatch(id->!valid(id))){gap(module,"RESERVED_MOD_IN_PROTECTED_SHARED_MODULE:"+ids(module));return;}
        synchronized(protectedGroups){if(!protectedGroups.add(module))return;}
        try{RecoveryAgent.publishProtectedModules(new Module[]{module});}
        catch(RuntimeException failure){synchronized(protectedGroups){protectedGroups.remove(module);}throw failure;}
    }
    static String protect(Instrumentation api,String[] ids,Module[] hints){
        if(api==null)return "AGENT_UNAVAILABLE;FAILED=1";
        String validation=validate(ids,hints);if(!validation.equals("READY"))return validation+";FAILED=1";
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(hints));
        RecoveryAgent.publishProtectedModules(selected.toArray(Module[]::new));
        synchronized(protectedGroups){protectedGroups.addAll(selected);}
        int failures=0;
        for(Class<?> type:RecoveryAgent.loadedClasses())if(selected.contains(type.getModule())&&!type.isArray()&&!type.isPrimitive()){
            if(!api.isModifiableClass(type)){gap(type.getModule(),type.getName()+":PROTECTED_CLASS_UNMODIFIABLE");failures++;continue;}
            try{api.retransformClasses(type);}catch(UnmodifiableClassException|RuntimeException|LinkageError unavailable){gap(type.getModule(),type.getName()+":PROTECT_TRANSFORM_UNAVAILABLE");failures++;}
        }
        return (failures==0?"PROTECTED":"PROTECTED_PARTIAL")+";FAILED="+failures;
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
        returns.computeIfAbsent(module,ignored->{
            Set<String> policies=new HashSet<>();for(String id:owners){String policy=System.getProperty("ronova.pro.bootReturns."+id);if(policy!=null)policies.add(returnMode(policy));}
            if(policies.size()>1)throw new IllegalArgumentException("SHARED_MODULE_RETURN_CONFLICT:"+owners);
            String mode=policies.isEmpty()?System.getProperty("ronova.pro.bootReturns","default"):policies.iterator().next();
            return new Returns(returnMode(mode));
        });
        // Publish the complete policy before this module can take the exact fast path.
        // A file/module is indivisible; selecting an owner includes all its declared aliases.
        denied.addAll(owners);
        synchronized(exact) {exact.add(module);}
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
                rejectConstructor(method);changed=true;
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
    static byte[] container(ClassLoader loader,String name,byte[] bytes){
        if(!MOD_CONTAINER.equals(name))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(node.fields.stream().noneMatch(field->field.name.equals("modClass")&&field.desc.equals("Ljava/lang/Class;")))throw new IllegalStateException("ACTUAL_FORGE_MOD_CLASS_UNAVAILABLE");
        for(MethodNode method:node.methods)if(method.name.equals("constructMod")&&method.desc.equals("()V")){
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("modOwnerAllowed")){creationGuarded=true;return null;}
            InsnList gate=new InsnList();LabelNode allowed=new LabelNode();gate.add(new VarInsnNode(Opcodes.ALOAD,0));
            gate.add(new jdk.internal.org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD,node.name,"modClass","Ljava/lang/Class;"));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","modOwnerAllowed","(Ljava/lang/Object;)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));gate.add(new InsnNode(Opcodes.RETURN));gate.add(allowed);method.instructions.insert(gate);
            ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);creationGuarded=true;return writer.toByteArray();
        }
        throw new IllegalStateException("ACTUAL_FORGE_CONSTRUCT_MOD_ENTRY_UNAVAILABLE");
    }
    private static void rejectConstructor(MethodNode method){
        InsnList rejected=new InsnList();rejected.add(new TypeInsnNode(Opcodes.NEW,"java/lang/IllegalStateException"));
        rejected.add(new InsnNode(Opcodes.DUP));rejected.add(new LdcInsnNode("RONOVA_MOD_GROUP_CREATION_REFUSED"));
        rejected.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/IllegalStateException","<init>","(Ljava/lang/String;)V",false));
        rejected.add(new InsnNode(Opcodes.ATHROW));replace(method,rejected);
    }
    /** Every real body reads the current group policy, including private/bridge/synthetic bodies. */
    static byte[] entries(ClassLoader loader,Module module,String name,byte[] bytes){
        if(loader==null||!RecoveryAgent.producerModule(module))return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0||method.instructions.size()==0)continue;
            boolean exists=false;for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call
                    &&call.owner.equals("dev/ronova/pro/bootstrap/ControlBridge")&&call.name.equals("modMethodAllowed")){exists=true;break;}
            if(exists)continue;
            InsnList gate=new InsnList();LabelNode allowed=new LabelNode();gate.add(new LdcInsnNode(Type.getObjectType(node.name)));
            if((method.access&Opcodes.ACC_STATIC)==0&&!method.name.equals("<init>"))gate.add(new VarInsnNode(Opcodes.ALOAD,0));
            else gate.add(new InsnNode(Opcodes.ACONST_NULL));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","modMethodAllowed","(Ljava/lang/Class;Ljava/lang/Object;)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFNE,allowed));
            if(method.name.equals("<init>")){
                MethodNode refusal=new MethodNode();rejectConstructor(refusal);gate.add(refusal.instructions);
            }else{
                Type result=Type.getReturnType(method.desc);
                switch(result.getSort()){
                    case Type.VOID -> gate.add(new InsnNode(Opcodes.RETURN));
                    case Type.BOOLEAN,Type.BYTE,Type.CHAR,Type.SHORT,Type.INT -> {gate.add(new InsnNode(Opcodes.ICONST_0));gate.add(new InsnNode(Opcodes.IRETURN));}
                    case Type.LONG -> {gate.add(new InsnNode(Opcodes.LCONST_0));gate.add(new InsnNode(Opcodes.LRETURN));}
                    case Type.FLOAT -> {gate.add(new InsnNode(Opcodes.FCONST_0));gate.add(new InsnNode(Opcodes.FRETURN));}
                    case Type.DOUBLE -> {gate.add(new InsnNode(Opcodes.DCONST_0));gate.add(new InsnNode(Opcodes.DRETURN));}
                    default -> {gate.add(new InsnNode(Opcodes.ACONST_NULL));gate.add(new InsnNode(Opcodes.ARETURN));}
                }
            }
            gate.add(allowed);method.instructions.insert(gate);changed=true;
        }
        if(!changed)return null;
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
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
    String mode=policy.mode();
    Type result=Type.getReturnType(method.desc);
    InsnList code=new InsnList();
    switch(result.getSort()) {
        case Type.VOID -> code.add(new InsnNode(Opcodes.RETURN));
        case Type.BOOLEAN,Type.BYTE,Type.CHAR,Type.SHORT,Type.INT -> {
            code.add(new InsnNode(result.getSort()==Type.INT&&mode.equals("invalid-id")?Opcodes.ICONST_M1:Opcodes.ICONST_0));
            code.add(new InsnNode(Opcodes.IRETURN));
        }
        case Type.LONG -> {code.add(new InsnNode(Opcodes.LCONST_0));code.add(new InsnNode(Opcodes.LRETURN));}
        case Type.FLOAT -> {code.add(new InsnNode(Opcodes.FCONST_0));code.add(new InsnNode(Opcodes.FRETURN));}
        case Type.DOUBLE -> {code.add(new InsnNode(Opcodes.DCONST_0));code.add(new InsnNode(Opcodes.DRETURN));}
        default -> {
            String type=result.getSort()==Type.OBJECT?result.getInternalName():"";
            if(mode.equals("null"))code.add(new InsnNode(Opcodes.ACONST_NULL));
            else if((mode.equals("uuid-fixed")||mode.equals("uuid-each"))
                    &&(type.equals("java/util/UUID")||type.equals("java/lang/String"))) {
                code.add(new LdcInsnNode(mode));
                code.add(policy.fixedUuid()==null?new InsnNode(Opcodes.ACONST_NULL):new LdcInsnNode(policy.fixedUuid()));
                code.add(new InsnNode(type.equals("java/lang/String")?Opcodes.ICONST_1:Opcodes.ICONST_0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/DefinitionBridge","returnIdentifier",
                        "(Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/Object;",false));
                code.add(new TypeInsnNode(Opcodes.CHECKCAST,type));
            } else if(mode.equals("empty")&&(type.equals("java/lang/String")||type.equals("java/lang/CharSequence")))
                code.add(new LdcInsnNode(""));
            else if(result.getSort()==Type.ARRAY&&!mode.equals("empty"))code.add(new InsnNode(Opcodes.ACONST_NULL));
            else if(result.getSort()==Type.ARRAY) {
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
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/DefinitionBridge","returnFuture",
                        "()Ljava/util/concurrent/CompletableFuture;",false));
            } else code.add(new InsnNode(Opcodes.ACONST_NULL));
            code.add(new InsnNode(Opcodes.ARETURN));
        }
    }
    replace(method,code);
    }
    static String prepare(Instrumentation api,String[] ids,Module[] hints,String mode) {
        if(api==null)return "AGENT_UNAVAILABLE";
        mode=returnMode(mode);
        ids=ids==null?null:ids.clone();hints=hints==null?null:hints.clone();
        String selection=validate(ids,hints);
        if(!selection.equals("READY"))return selection;
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(hints));
        Map<Module,Returns> policies=new IdentityHashMap<>();for(Module module:selected)policies.put(module,new Returns(mode));
        synchronized(ModGroupBoundary.class) {
            for(Module module:selected) {
                Returns previous=returns.get(module);
                // Repeating the same selection/publication does not start a new policy generation.
                if(previous!=null&&previous.mode().equals(mode))policies.put(module,previous);
            }
            returns.putAll(policies);
            for(Module module:selected)denied.addAll(ids(module));
            synchronized(exact){exact.addAll(selected);}
        }
        // Bind every selected policy before the first task/native stopped publication.
        RecoveryAgent.publishGroupGate(selected.toArray(Module[]::new));
        return publishSelected(selected);
    }
    static String stop(Instrumentation api,String[] ids,Module[] hints,String mode) {
        ids=ids==null?null:ids.clone();hints=hints==null?null:hints.clone();
        String prepared=prepare(api,ids,hints,mode);
        if(!prepared.equals("READY"))return prepared;
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(hints));
        for(int i=0;i<ids.length;i++) {
            denied.add(ids[i].trim().toLowerCase(java.util.Locale.ROOT));
            synchronized(exact){exact.add(hints[i]);}
        }
        return retransform(api,selected);
    }
    /** Claim the exact module; a native callback must never wait for its current publisher. */
    static Returns beginPublication(Module module) {
        synchronized(ModGroupBoundary.class) {
            Publication active=publishing.get(module);if(active!=null){active.pending=true;return null;}
            Returns policy=policy(module);published.remove(module);publishing.put(module,new Publication());return policy;
        }
    }
    static Returns finishPublication(Module module,Returns completed) {
        synchronized(ModGroupBoundary.class) {
            Publication active=publishing.get(module);
            if(active==null||active.owner!=Thread.currentThread())throw new IllegalStateException("MOD_GROUP_PUBLICATION_OWNER_CHANGED");
            Returns current=policy(module);boolean pending=active.pending;active.pending=false;
            if(current!=completed||pending)return current;
            published.put(module,current);publishing.remove(module);ModGroupBoundary.class.notifyAll();return null;
        }
    }
    static void failedPublication(Module module) {
        synchronized(ModGroupBoundary.class) {
            Publication active=publishing.get(module);
            if(active!=null&&active.owner==Thread.currentThread()){publishing.remove(module);ModGroupBoundary.class.notifyAll();}
        }
    }
    private static String publishSelected(Set<Module> selected) {
        for(Module module:selected)RecoveryAgent.publishStoppedModule(module);
        for(;;) {
            Set<Module> retry=Collections.newSetFromMap(new IdentityHashMap<>());
            synchronized(ModGroupBoundary.class) {
                boolean pending=false;
                for(Module module:selected)if(published.get(module)!=returns.get(module)) {
                    pending=true;Publication activePublication=publishing.get(module);Thread owner=activePublication==null?null:activePublication.owner;
                    // A reentrant control call cannot wait for the native call whose callback it is servicing.
                    if(owner==Thread.currentThread())return "MOD_GROUP_PUBLICATION_PENDING";
                    if(owner==null)retry.add(module);
                }
                if(!pending)return "READY";
                if(retry.isEmpty())try{ModGroupBoundary.class.wait();}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return "MOD_GROUP_PUBLICATION_INTERRUPTED";}
            }
            for(Module module:retry)RecoveryAgent.publishStoppedModule(module);
        }
    }
    static String validate(String[] ids,Module[] hints) {
        if(ids==null||hints==null||ids.length!=hints.length||ids.length==0)return "EMPTY_OR_MISMATCHED_SELECTION";
        Set<String> selected=new HashSet<>();
        for(String raw:ids) {
            if(raw==null)return "INVALID_TARGET:null";
            String id=raw.trim().toLowerCase(java.util.Locale.ROOT);
            if(!valid(id))return "INVALID_TARGET:"+id;
            selected.add(id);
        }
        for(int i=0;i<ids.length;i++) {
            String id=ids[i].trim().toLowerCase(java.util.Locale.ROOT);
            if(hints[i]==null||hints[i]==Object.class.getModule())return "INVALID_TARGET:"+id;
            if(!hints[i].isNamed())return "SHARED_UNNAMED_MODULE_UNRESOLVED:"+id;
            if(ids(hints[i]).stream().anyMatch(owner->!valid(owner)))return "RESERVED_MOD_IN_SHARED_MODULE:"+ids(hints[i]);
            if(!ids(hints[i]).contains(id))return "MODULE_METADATA_MISMATCH:"+id;
            if(!selected.containsAll(ids(hints[i])))
                return "MULTI_MOD_MODULE_REQUIRES_ALL_IDS:"+ids(hints[i]);
        }
        return "READY";
    }
    static String stopByIds(Instrumentation api,String[] ids,String mode) {
        if(api==null)return "AGENT_UNAVAILABLE";
        mode=returnMode(mode);
        if(ids==null||ids.length==0)return "EMPTY_SELECTION";
        ids=ids.clone();
        Set<String> selected=new HashSet<>();
        for(String raw:ids) {
            if(raw==null)return "INVALID_TARGET:null";
            String id=raw.trim().toLowerCase(java.util.Locale.ROOT);
            if(!valid(id))return "INVALID_TARGET:"+id;
            selected.add(id);
        }
        java.util.ArrayList<String> aliases=new java.util.ArrayList<>();java.util.ArrayList<Module> targets=new java.util.ArrayList<>();
        for(Module module:modules(ids)) {
            Set<String> owners=ids(module);
            if(Collections.disjoint(owners,selected))continue;
            if(!selected.containsAll(owners))return "CLIENT_SHARED_MODULE_NOT_FULLY_AUTHORIZED:"+owners;
            for(String owner:owners){aliases.add(owner);targets.add(module);}
        }
        if(targets.isEmpty())return "MOD_GROUP_NOT_LOADED";
        return stop(api,aliases.toArray(String[]::new),targets.toArray(Module[]::new),mode);
    }
    static String protectByIds(Instrumentation api,String[] ids){
        if(api==null)return "AGENT_UNAVAILABLE;FAILED=1";
        if(ids==null||ids.length==0)return "EMPTY_SELECTION;FAILED=1";
        for(String raw:ids)if(raw==null||!valid(raw.trim().toLowerCase(java.util.Locale.ROOT)))return "INVALID_TARGET:"+raw+";FAILED=1";
        Set<String> selected=new HashSet<>();for(String id:ids)selected.add(id.trim().toLowerCase(java.util.Locale.ROOT));
        java.util.ArrayList<String> aliases=new java.util.ArrayList<>();java.util.ArrayList<Module> targets=new java.util.ArrayList<>();
        for(Module module:modules(ids)){
            Set<String> owners=ids(module);
            if(!selected.containsAll(owners))return "CLIENT_SHARED_MODULE_NOT_FULLY_AUTHORIZED:"+owners+";FAILED=1";
            for(String owner:owners){aliases.add(owner);targets.add(module);}
        }
        if(targets.isEmpty())return "MOD_GROUP_NOT_LOADED;FAILED=1";
        return protect(api,aliases.toArray(String[]::new),targets.toArray(Module[]::new));
    }
    private static String retransform(Instrumentation api,Set<Module> selected) {
        int loaded=0,failed=0;
        for(;;) {
        Map<Module,Returns> policies=new IdentityHashMap<>();
        synchronized(ModGroupBoundary.class){for(Module module:selected)policies.put(module,policy(module));}
        Class<?>[] snapshot=batch.get();if(snapshot==null)snapshot=RecoveryAgent.loadedClasses();
        Set<Class<?>> external=Collections.newSetFromMap(new IdentityHashMap<>());
        external.addAll(Arrays.asList(ExternalCodeImages.affected(snapshot,selected.toArray(Module[]::new))));
        for(Class<?> type:snapshot) {
            if(type.isArray()||type.isPrimitive())continue;
            Module module=RecoveryAgent.logicalModule(type);boolean contribution=external.contains(type);if((!selected.contains(module)||!stopped(module))&&!contribution)continue;
            Module[] contributionSources=contribution?ExternalCodeImages.sources(type):new Module[0];
            if(contribution&&!selected.contains(module)){
                boolean bound=ExternalCodeDefinitions.sourceContinuing(type);
                for(Module source:contributionSources)if(source!=null&&selected.contains(source)){
                    String detail=type.getName()+":EXTERNAL_OLD_FRAME_SOURCE_UNOBSERVED";
                    if(bound)externalResolved(source,detail);else gap(source,detail);
                }
            }
            if(!api.isModifiableClass(type)) {
                if(type.isHidden()&&RecoveryAgent.definition(type)!=null)continue;
                if(contribution)ExternalCodeImages.failed(selected.toArray(Module[]::new),type.getName()+":EXTERNAL_CLASS_UNMODIFIABLE");else gap(module,type.getName()+":UNMODIFIABLE");failed++;continue;
            }
            try {
                api.retransformClasses(type);loaded++;
                if(contribution&&!ExternalCodeDefinitions.sourceAccepted(type)){
                    for(Module source:contributionSources)if(source!=null&&selected.contains(source))
                        gap(source,type.getName()+":EXTERNAL_ACCEPTED_DEFINITION_SOURCE_UNOBSERVED");
                }else if(contribution)for(Module source:contributionSources)if(source!=null&&selected.contains(source))
                    externalResolved(source,type.getName()+":EXTERNAL_ACCEPTED_DEFINITION_SOURCE_UNOBSERVED");
            }
            catch(UnmodifiableClassException|RuntimeException|LinkageError failure) {
                if(contribution)ExternalCodeImages.failed(selected.toArray(Module[]::new),type.getName()+":EXTERNAL_RETRANSFORM_FAILED:"+failure.getClass().getSimpleName());else gap(module,type.getName()+":"+failure.getClass().getSimpleName());failed++;
            }
        }
        boolean current=true;
        synchronized(ModGroupBoundary.class){for(Module module:selected)if(policy(module)!=policies.get(module)||published.get(module)!=policies.get(module)){current=false;break;}}
        if(current) {
            Returns policy=policies.get(selected.iterator().next());
            return "TARGETS="+denied+";RETRANSFORMED_CLASSES="+loaded+";FAILED="+failed+";METHODS="+methods.get()+";RETURNS="+policy.mode()+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();
        }
        String publication=publishSelected(selected);if(!publication.equals("READY"))return publication;
        }
    }
    static String state() {return "TARGETS="+denied+";METHODS="+methods.get()+";MOD_CONSTRUCTION_GATE="+(creationGuarded?"INSTALLED":"WAITING_FOR_FORGE_CONTAINER")+";UNRESOLVED="+unresolved.size()+";GAPS="+unresolved.stream().limit(8).toList();}
    static Returns policy(Module module) {
        Returns policy=returns.get(module);
        if(policy==null)throw new IllegalStateException("STOPPED_RETURN_POLICY_REQUIRED");
        return policy;
    }
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
        String registrations;
        try{registrations=String.valueOf(Class.forName("dev.ronova.pro.bootstrap.ControlBridge",false,null).getMethod("modRegistrationState",Module.class).invoke(null,module));}
        catch(ReflectiveOperationException unavailable){registrations="REGISTRATION_PARTIAL=QUERY_UNAVAILABLE";}
        boolean registrationGap=registrations.contains("PARTIAL");
        return "MODS="+ids(module)+";STATE="+(stopped(module)?gaps.isEmpty()&&continuation.isEmpty()&&!registrationGap?"STOPPED":"STOPPED_PARTIAL":protectedModule(module)?gaps.isEmpty()?"PROTECTED":"PROTECTED_PARTIAL":"ACTIVE")
                +";RETURNS="+(policy==null?"default":policy.mode())+";UNRESOLVED="+(gaps.size()+(continuation.isEmpty()?0:1)+(registrationGap?1:0))+";GAPS="+gaps.stream().limit(8).toList()+";FLOW_CONTINUATION="+continuation+";"+registrations;
    }
}
