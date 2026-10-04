package dev.ronova.pro.agent;

import java.io.InputStream;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Observes only a verified NEW receiver after its matching constructor returns successfully. */
final class CreationBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile Set<String> definedTasks;
    private record Site(String method,LabelNode label) { }
    private record Observed(String method,int instruction){}
    private record Emission(java.lang.ref.WeakReference<byte[]> bytes,byte[] image,List<Observed> sites){}
    private static final List<Emission> EMISSIONS=new ArrayList<>();
    private record IdentitySite(MethodInsnNode call,FieldInsnNode field,boolean nullable) { }
    static final class Weaving {
        final ClassNode node;private final List<Site> sites;private final IdentityHashMap<AbstractInsnNode,Site> calls=new IdentityHashMap<>();private final IdentityHashMap<Site,LabelNode> relocated=new IdentityHashMap<>();
        Weaving(ClassNode node,List<Site> sites){
            this.node=node;this.sites=List.copyOf(sites);
            for(Site site:sites){AbstractInsnNode at=site.label.getNext();while(at!=null&&at.getOpcode()<0)at=at.getNext();if(at!=null)calls.put(at,site);}
        }
        void relocate(Map<AbstractInsnNode,LabelNode> positions){
            for(var entry:calls.entrySet()){
                LabelNode replacement=positions.get(entry.getKey());if(replacement!=null)relocated.put(entry.getValue(),replacement);
            }
        }
        String[] locations(){return sites.stream().map(site->site.method+":"+relocated.getOrDefault(site,site.label).getLabel().getOffset()).toArray(String[]::new);}
    }
    static Weaving recorded(byte[] bytes,ClassNode node){
        List<Observed> observed=null;synchronized(EMISSIONS){
            EMISSIONS.removeIf(entry->entry.bytes.get()==null);
            for(int i=EMISSIONS.size()-1;i>=0;i--){Emission entry=EMISSIONS.get(i);if(entry.bytes.get()==bytes&&ControlImages.sameBytes(entry.image,bytes)){observed=entry.sites;break;}}
        }
        if(observed==null)return null;List<Site> sites=new ArrayList<>();
        for(Observed position:observed){
            MethodNode method=node.methods.stream().filter(candidate->(candidate.name+candidate.desc).equals(position.method)).findFirst().orElse(null);if(method==null)return null;
            AbstractInsnNode selected=null;int ordinal=0;
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0){if(ordinal++==position.instruction){selected=at;break;}}
            if(selected==null)return null;LabelNode label=new LabelNode();method.instructions.insertBefore(selected,label);sites.add(new Site(position.method,label));
        }
        return new Weaving(node,sites);
    }
    private static void emitted(byte[] bytes,Weaving weaving){
        List<Observed> sites=new ArrayList<>();
        for(MethodNode method:weaving.node.methods){int ordinal=0;for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0){
            Site site=weaving.calls.get(at);if(site!=null)sites.add(new Observed(site.method,ordinal));ordinal++;
        }}
        Emission entry=new Emission(new java.lang.ref.WeakReference<>(bytes),bytes.clone(),List.copyOf(sites));
        ControlImages.protect(EMISSIONS,entry,entry.bytes,entry.image,entry.sites);for(Observed site:sites)ControlImages.protect(site);
        synchronized(EMISSIONS){EMISSIONS.removeIf(prior->prior.bytes.get()==null||prior.bytes.get()==bytes);EMISSIONS.add(entry);}
    }
    static void finalSites(ClassLoader loader,String name,Class<?> actual,byte[] bytes,Weaving weaving){
        if(weaving==null)return;sites(loader,name,actual,Arrays.asList(weaving.locations()));emitted(bytes,weaving);
    }
    static void transferred(byte[] bytes,Weaving weaving){
        if(weaving!=null)emitted(bytes,weaving);
    }
    private static void sites(ClassLoader loader,String name,Class<?> actual,List<String> sites) {
        try {
            Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null)
                .getMethod("registerCreationSites",ClassLoader.class,String.class,String[].class,Class.class)
                .invoke(null,loader,name.replace('/','.'),sites.toArray(String[]::new),actual);
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("CREATION_SITE_REGISTRY",failure); }
    }
    static byte[] transform(ClassLoader loader,String name,Class<?> actual,byte[] bytes,boolean observeInputs) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        Weaving weaving=weave(loader,name,node,observeInputs,Set.of());
        if(weaving==null){sites(loader,name,actual,List.of());return null;}
        try{
            ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();
            sites(loader,name,actual,Arrays.asList(weaving.locations()));emitted(result,weaving);return result;
        }catch(RuntimeException failure){
            sites(loader,name,actual,List.of());System.err.println("RONOVA_CREATION_UNOBSERVED:"+name+":"+failure.getClass().getSimpleName());return null;
        }
    }
    static Weaving defined(ClassLoader loader,String name,byte[] bytes,boolean observeInputs){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return weave(loader,name,node,observeInputs,definedTasks());
    }
    private static Set<String> definedTasks(){
        Set<String> current=definedTasks;if(current!=null)return current;
        synchronized(CreationBoundary.class){
            if(definedTasks!=null)return definedTasks;
            try{String[] selectors=(String[])Class.forName("dev.ronova.pro.bootstrap.TaskBridge",false,null).getMethod("definedTaskMethods").invoke(null);
                Set<String> next=Set.copyOf(Arrays.asList(selectors));ControlImages.protect(next);definedTasks=next;return next;
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_DEFINED_TASK_CONTRACTS_UNAVAILABLE",failure);}
        }
    }
    private static Weaving weave(ClassLoader loader,String name,ClassNode node,boolean observeInputs,Set<String> tasks){
        var pending=new ArrayList<Site>();var hierarchy=new HashMap<String,Boolean>();
        Map<MethodNode,List<IdentitySite>> identities=new IdentityHashMap<>();
        // An original call cannot masquerade as the observer inserted below, even in a mixed method.
                for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())
            if(i instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&(call.name.equals("creationObserved")||call.name.equals("creationFailed")||call.name.startsWith("producer")
                    ||call.name.equals("definedTaskEnter")||call.name.equals("definedTaskExit"))) {
                return null;
            }
        try {
            for(MethodNode method:node.methods) {
                if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
                var instructions=method.instructions.toArray();boolean relevant=false;Set<String> allocations=null;
                for(AbstractInsnNode instruction:instructions)if(instruction instanceof TypeInsnNode allocation&&allocation.getOpcode()==Opcodes.NEW){
                    if(allocations==null)allocations=new HashSet<>();allocations.add(allocation.desc);
                }
                if(allocations==null)continue;
                // A creation site must later resolve to an actual NEW in this
                // method. super()/this() alone cannot produce such a site and
                // do not require reading their uninitiated resource ancestry.
                for(AbstractInsnNode i:instructions)if(i instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")&&allocations.contains(call.owner)
                        &&(observeInputs||entity(call.owner,loader,node,hierarchy))) { relevant=true;break; }
                if(!relevant)continue;
                SourceInterpreter interpreter=new SourceInterpreter(Opcodes.ASM8) {
                    @Override public SourceValue copyOperation(AbstractInsnNode instruction,SourceValue value) { return value; }
                };
                Frame<SourceValue>[] frames=new Analyzer<>(interpreter).analyze(node.name,method);
                identities.put(method,identitySites(method,instructions,frames,loader,node,hierarchy));
                for(int i=0;i<instructions.length;i++) {
                    if(!(instructions[i] instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL||!call.name.equals("<init>")
                            ||frames[i]==null)continue;
                    Type[] arguments=Type.getArgumentTypes(call.desc);Frame<SourceValue> frame=frames[i];
                    SourceValue receiver=frame.getStack(frame.getStackSize()-arguments.length-1);
                    if(receiver.insns.size()!=1||!(receiver.insns.iterator().next() instanceof TypeInsnNode allocation)
                            ||allocation.getOpcode()!=Opcodes.NEW||!allocation.desc.equals(call.owner))continue;
                    if(!observeInputs&&!entity(call.owner,loader,node,hierarchy))continue;
                    int[] slots=new int[arguments.length];
                    for(int n=0;n<arguments.length;n++) { slots[n]=method.maxLocals;method.maxLocals+=arguments[n].getSize(); }
                    int object=method.maxLocals++;
                    InsnList before=new InsnList();
                    for(int n=arguments.length-1;n>=0;n--)before.add(new VarInsnNode(arguments[n].getOpcode(Opcodes.ISTORE),slots[n]));
                    before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,object));
                    for(int n=0;n<arguments.length;n++)before.add(new VarInsnNode(arguments[n].getOpcode(Opcodes.ILOAD),slots[n]));
                    LabelNode begin=new LabelNode(),end=new LabelNode(),failed=new LabelNode(),done=new LabelNode();before.add(begin);
                    method.instructions.insertBefore(call,before);
                    LabelNode site=new LabelNode();InsnList after=new InsnList();after.add(end);after.add(new VarInsnNode(Opcodes.ALOAD,object));after.add(site);
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"creationObserved","(Ljava/lang/Object;)V",false));
                    after.add(new JumpInsnNode(Opcodes.GOTO,done));after.add(failed);after.add(new InsnNode(Opcodes.DUP));
                    LabelNode failureSite=new LabelNode();after.add(failureSite);
                    after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"creationFailed","(Ljava/lang/Throwable;)V",false));after.add(new InsnNode(Opcodes.ATHROW));after.add(done);
                    method.instructions.insert(call,after);method.tryCatchBlocks.add(0,new TryCatchBlockNode(begin,end,failed,null));
                    pending.add(new Site(method.name+method.desc,site));pending.add(new Site(method.name+method.desc,failureSite));
                }
            }
            if(observeInputs) {
                for(MethodNode method:node.methods)if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0)
                    for(AbstractInsnNode instruction:method.instructions.toArray()) {
                        if(instruction instanceof MethodInsnNode call&&resourceFactory(call)) {
                            LabelNode site=new LabelNode();InsnList after=new InsnList();after.add(new InsnNode(Opcodes.DUP));after.add(site);
                            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"creationObserved","(Ljava/lang/Object;)V",false));
                            method.instructions.insert(call,after);pending.add(new Site(method.name+method.desc,site));
                        } else if(instruction.getOpcode()==Opcodes.NEWARRAY||instruction.getOpcode()==Opcodes.ANEWARRAY||instruction.getOpcode()==Opcodes.MULTIANEWARRAY) {
                            LabelNode site=new LabelNode();InsnList after=new InsnList();after.add(new InsnNode(Opcodes.DUP));after.add(site);
                            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"creationObserved","(Ljava/lang/Object;)V",false));
                            method.instructions.insert(instruction,after);pending.add(new Site(method.name+method.desc,site));
                        } else if(method.name.equals("<clinit>")&&instruction.getOpcode()==Opcodes.RETURN) {
                            LabelNode site=new LabelNode();InsnList before=new InsnList();before.add(new LdcInsnNode(Type.getObjectType(node.name)));before.add(site);
                            before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"creationObserved","(Ljava/lang/Object;)V",false));
                            method.instructions.insertBefore(instruction,before);pending.add(new Site(method.name+method.desc,site));
                        }
                    }
                for(MethodNode method:node.methods) {
                    Type result=Type.getReturnType(method.desc);
                    boolean factory=result.getSort()==Type.OBJECT&&entity(result.getInternalName(),loader,node,hierarchy);
                    producer(method,pending,factory,identities.getOrDefault(method,List.of()),descriptor->producerType(descriptor)
                            ||descriptor.startsWith("L")&&entity(Type.getType(descriptor).getInternalName(),loader,node,hierarchy));
                }
            }
            for(MethodNode method:node.methods)if(tasks.contains(method.name+method.desc)
                    &&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))==0)definedTask(node.name,method,pending);
            if(pending.isEmpty())return null;
            return new Weaving(node,pending);
        } catch(AnalyzerException|RuntimeException failure) {
            System.err.println("RONOVA_CREATION_UNOBSERVED:"+name+":"+failure.getClass().getSimpleName());return null;
        }
    }
    private static void definedTask(String owner,MethodNode method,List<Site> sites){
        AbstractInsnNode[] original=method.instructions.toArray();int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode enterSite=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new LdcInsnNode(method.name+method.desc));enter.add(enterSite);
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"definedTaskEnter","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(new VarInsnNode(Opcodes.ALOAD,token));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"blocked","(Ljava/lang/Object;)Z",false));enter.add(new JumpInsnNode(Opcodes.IFEQ,start));
        enter.add(DefinitionBoundary.stoppedReturn(owner,Type.getReturnType(method.desc)));enter.add(start);method.instructions.insert(enter);sites.add(new Site(method.name+method.desc,enterSite));
        for(AbstractInsnNode at:original)if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)method.instructions.insertBefore(at,definedTaskExit(method,token,sites));
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
        method.instructions.add(definedTaskExit(method,token,sites));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList definedTaskExit(MethodNode method,int token,List<Site> sites){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,token));
        LabelNode site=new LabelNode();code.add(site);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"definedTaskExit","(Ljava/lang/Object;)V",false));sites.add(new Site(method.name+method.desc,site));return code;
    }
    private static boolean producerType(String descriptor) {
        if(descriptor.length()==1)return "ZBCSIJFD".contains(descriptor);
        if(descriptor.equals("Ljava/util/UUID;"))return true;
        if(!descriptor.startsWith("L"))return false;
        return descriptor.equals("Lnet/minecraft/nbt/CompoundTag;")||descriptor.equals("Lnet/minecraft/world/entity/Entity;")
                ||!descriptor.startsWith("Ljava/")&&!descriptor.startsWith("Ljavax/")&&!descriptor.startsWith("Ljdk/")
                    &&!descriptor.startsWith("Lnet/minecraft/")&&!descriptor.startsWith("Lnet/minecraftforge/");
    }
    private static void producer(MethodNode method,List<Site> sites,boolean factory,List<IdentitySite> identities,java.util.function.Predicate<String> observedType) {
        if(method.name.startsWith("<")||(method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0)return;
        boolean allocates=factory||sites.stream().anyMatch(site->site.method.equals(method.name+method.desc));
        List<AbstractInsnNode> reads=new ArrayList<>();
        for(AbstractInsnNode i:method.instructions.toArray()) {
            if(i instanceof FieldInsnNode f&&(f.getOpcode()==Opcodes.GETFIELD||f.getOpcode()==Opcodes.GETSTATIC)&&observedType.test(f.desc)
                    &&(allocates||f.desc.startsWith("L")&&!f.desc.equals("Ljava/util/UUID;")))reads.add(i);
            else if(i instanceof MethodInsnNode call&&(call.owner.equals("java/util/Map")||call.owner.equals("java/util/HashMap")||call.owner.equals("java/util/concurrent/ConcurrentHashMap"))
                    &&call.name.equals("get")&&call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))reads.add(i);
        }
        if(!factory&&reads.isEmpty()&&sites.stream().noneMatch(site->site.method.equals(method.name+method.desc)))return;
        int receiver=method.maxLocals++,key=method.maxLocals++;
        int result=method.maxLocals;method.maxLocals+=2;
        Map<FieldInsnNode,Integer> fieldReceivers=new IdentityHashMap<>();
        for(AbstractInsnNode read:reads) {
            int readReceiver=receiver;
            if(read instanceof FieldInsnNode field&&identities.stream().anyMatch(site->site.field==field)) {
                readReceiver=method.maxLocals++;fieldReceivers.put(field,readReceiver);
            }
            InsnList before=new InsnList();
            if(read instanceof MethodInsnNode)before.add(new VarInsnNode(Opcodes.ASTORE,key));
            if(read instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC)before.add(new LdcInsnNode(Type.getObjectType(field.owner)));
            else before.add(new InsnNode(Opcodes.DUP));
            before.add(new VarInsnNode(Opcodes.ASTORE,readReceiver));
            if(read instanceof MethodInsnNode)before.add(new VarInsnNode(Opcodes.ALOAD,key));
            method.instructions.insertBefore(read,before);
            Type readType=read instanceof FieldInsnNode field?Type.getType(field.desc):Type.getType(Object.class);
            InsnList after=new InsnList();after.add(new VarInsnNode(readType.getOpcode(Opcodes.ISTORE),result));
            after.add(new VarInsnNode(Opcodes.ALOAD,readReceiver));
            if(read instanceof FieldInsnNode field) {
                after.add(new LdcInsnNode(Type.getObjectType(field.owner)));after.add(new LdcInsnNode(field.name));after.add(new LdcInsnNode(field.desc));
            }
            after.add(new VarInsnNode(readType.getOpcode(Opcodes.ILOAD),result));ControlClassWriter.box(after,readType);
            LabelNode site=new LabelNode();after.add(site);
            after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,read instanceof FieldInsnNode?"producerFieldInput":"producerInput",
                    read instanceof FieldInsnNode?"(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V":"(Ljava/lang/Object;Ljava/lang/Object;)V",false));
            after.add(new VarInsnNode(readType.getOpcode(Opcodes.ILOAD),result));
            method.instructions.insert(read,after);sites.add(new Site(method.name+method.desc,site));
        }
        for(IdentitySite identity:identities)if(fieldReceivers.containsKey(identity.field)) {
            int object=method.maxLocals++,uuid=method.maxLocals++;
            InsnList before=new InsnList();before.add(new VarInsnNode(Opcodes.ASTORE,uuid));before.add(new VarInsnNode(Opcodes.ASTORE,object));
            before.add(new VarInsnNode(Opcodes.ALOAD,object));before.add(new VarInsnNode(Opcodes.ALOAD,uuid));method.instructions.insertBefore(identity.call,before);
            InsnList after=new InsnList();after.add(new VarInsnNode(Opcodes.ALOAD,object));after.add(new VarInsnNode(Opcodes.ALOAD,fieldReceivers.get(identity.field)));
            after.add(new LdcInsnNode(Type.getObjectType(identity.field.owner)));after.add(new LdcInsnNode(identity.field.name));
            after.add(new VarInsnNode(Opcodes.ALOAD,uuid));after.add(new InsnNode(identity.nullable?Opcodes.ICONST_1:Opcodes.ICONST_0));
            LabelNode site=new LabelNode();after.add(site);after.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerIdentityAssigned",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;Z)V",false));
            method.instructions.insert(identity.call,after);sites.add(new Site(method.name+method.desc,site));
        }
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode enterSite=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList enter=new InsnList();enter.add(enterSite);enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerEnter","()Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);
        if((method.access&Opcodes.ACC_STATIC)==0) {
            enter.add(new VarInsnNode(Opcodes.ALOAD,0));LabelNode receiverSite=new LabelNode();enter.add(receiverSite);
            enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerReceiver","(Ljava/lang/Object;)V",false));
            sites.add(new Site(method.name+method.desc,receiverSite));
        }
        if(factory) {
            int local=(method.access&Opcodes.ACC_STATIC)==0?1:0;
            for(Type argument:Type.getArgumentTypes(method.desc)) {
                if((argument.getSort()==Type.OBJECT||argument.getSort()==Type.ARRAY)&&!factoryContext(argument.getDescriptor())) {
                    enter.add(new VarInsnNode(Opcodes.ALOAD,local));LabelNode argumentSite=new LabelNode();enter.add(argumentSite);
                    enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerArgument","(Ljava/lang/Object;)V",false));
                    sites.add(new Site(method.name+method.desc,argumentSite));
                }
                local+=argument.getSize();
            }
            LabelNode gateSite=new LabelNode(),allowed=new LabelNode();enter.add(gateSite);
            enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerExecutionAllowed","()Z",false));
            enter.add(new JumpInsnNode(Opcodes.IFNE,allowed));enter.add(new InsnNode(Opcodes.ACONST_NULL));enter.add(new InsnNode(Opcodes.ARETURN));enter.add(allowed);
            sites.add(new Site(method.name+method.desc,gateSite));
        }
        method.instructions.insert(enter);sites.add(new Site(method.name+method.desc,enterSite));
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN) {
            if(factory&&at.getOpcode()==Opcodes.ARETURN) {
                InsnList filter=new InsnList();LabelNode site=new LabelNode();filter.add(site);
                filter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerResult","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                filter.add(new TypeInsnNode(Opcodes.CHECKCAST,Type.getReturnType(method.desc).getInternalName()));method.instructions.insertBefore(at,filter);
                sites.add(new Site(method.name+method.desc,site));
            }
            method.instructions.insertBefore(at,producerExit(token));
        }
        method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
        method.instructions.add(producerExit(token));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    private static boolean resourceFactory(MethodInsnNode call) {
        if(Type.getReturnType(call.desc).getSort()!=Type.OBJECT)return false;
        return call.owner.equals("java/util/concurrent/Executors")&&call.name.startsWith("new")
                ||call.owner.equals("java/lang/invoke/MethodHandles")&&call.name.equals("privateLookupIn")
                ||call.name.equals("open")&&(call.owner.equals("java/nio/channels/FileChannel")
                ||call.owner.equals("java/nio/channels/SocketChannel")||call.owner.equals("java/nio/channels/ServerSocketChannel")
                ||call.owner.equals("java/nio/channels/DatagramChannel"))
                ||call.owner.equals("java/lang/ProcessBuilder")&&call.name.equals("start")&&call.desc.equals("()Ljava/lang/Process;");
    }
    private static boolean factoryContext(String descriptor) {
        return Set.of("Lnet/minecraft/world/level/Level;","Lnet/minecraft/server/level/ServerLevel;",
                "Lnet/minecraft/server/MinecraftServer;","Ljava/lang/String;","Ljava/util/UUID;").contains(descriptor);
    }
    private static List<IdentitySite> identitySites(MethodNode method,AbstractInsnNode[] code,Frame<SourceValue>[] frames,
            ClassLoader loader,ClassNode owner,Map<String,Boolean> hierarchy) {
        List<IdentitySite> sites=new ArrayList<>();
        for(int i=0;i<code.length;i++)if(code[i] instanceof MethodInsnNode call&&Set.of("setUUID","m_20084_").contains(call.name)
                &&call.desc.equals("(Ljava/util/UUID;)V")&&entity(call.owner,loader,owner,hierarchy)&&frames[i]!=null) {
            var frame=frames[i];var input=frame.getStack(frame.getStackSize()-1);var receiver=frame.getStack(frame.getStackSize()-2);
            if(input.insns.size()!=1||!(input.insns.iterator().next() instanceof FieldInsnNode field)
                    ||!field.desc.equals("Ljava/util/UUID;")||field.getOpcode()!=Opcodes.GETFIELD&&field.getOpcode()!=Opcodes.GETSTATIC
                    ||receiver.insns.size()!=1||!(receiver.insns.iterator().next() instanceof TypeInsnNode allocation)||allocation.getOpcode()!=Opcodes.NEW)continue;
            boolean nullable=false;
            if(method.tryCatchBlocks.isEmpty()&&Type.getReturnType(method.desc).getSort()==Type.OBJECT
                    &&Arrays.stream(code).filter(n->n instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&entity(t.desc,loader,owner,hierarchy)).count()==1)
                for(int j=0;j<i;j++)if(code[j] instanceof JumpInsnNode jump&&(jump.getOpcode()==Opcodes.IFNULL||jump.getOpcode()==Opcodes.IFNONNULL)
                        &&frames[j]!=null&&frames[j].getStack(frames[j].getStackSize()-1).insns.equals(Set.of(field))) {
                    AbstractInsnNode nullPath=jump.getOpcode()==Opcodes.IFNULL?jump.label:jump.getNext();
                    if(nullReturn(nullPath)&&dominates(method,jump,allocation)) {nullable=true;break;}
                }
            sites.add(new IdentitySite(call,field,nullable));
        }
        return sites;
    }
    private static boolean nullReturn(AbstractInsnNode node) {
        while(node!=null&&node.getOpcode()<0)node=node.getNext();
        if(node==null||node.getOpcode()!=Opcodes.ACONST_NULL)return false;node=node.getNext();
        while(node!=null&&node.getOpcode()<0)node=node.getNext();return node!=null&&node.getOpcode()==Opcodes.ARETURN;
    }
    private static boolean dominates(MethodNode method,AbstractInsnNode gate,AbstractInsnNode target) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<AbstractInsnNode,Boolean>());var queue=new ArrayDeque<AbstractInsnNode>();
        queue.add(method.instructions.getFirst());
        while(!queue.isEmpty()) {
            var node=queue.removeFirst();if(node==gate||!seen.add(node))continue;if(node==target)return false;
            int op=node.getOpcode();if(op==Opcodes.ATHROW||op>=Opcodes.IRETURN&&op<=Opcodes.RETURN)continue;
            if(node instanceof JumpInsnNode jump) {queue.add(jump.label);if(op==Opcodes.GOTO)continue;}
            if(node instanceof TableSwitchInsnNode sw) {queue.add(sw.dflt);queue.addAll(sw.labels);continue;}
            if(node instanceof LookupSwitchInsnNode sw) {queue.add(sw.dflt);queue.addAll(sw.labels);continue;}
            if(node.getNext()!=null)queue.add(node.getNext());
        }
        return true;
    }
    private static InsnList producerExit(int token) {
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"producerExit","(Ljava/lang/Object;)V",false));return code;
    }
    private static boolean entity(String type,ClassLoader loader,ClassNode current,Map<String,Boolean> cache) {
        if(type.equals("net/minecraft/world/entity/Entity"))return true;
        if(type.startsWith("java/")||type.startsWith("jdk/")||type.equals("java/lang/Object"))return false;
        Boolean cached=cache.get(type);if(cached!=null)return cached;
        List<String> path=new ArrayList<>();Set<String> visiting=new HashSet<>();boolean result=false;
        while(type!=null){
            if(type.equals("net/minecraft/world/entity/Entity")){result=true;break;}
            if(type.startsWith("java/")||type.startsWith("jdk/")||type.equals("java/lang/Object"))break;
            Boolean known=cache.get(type);if(known!=null){result=known;break;}
            if(!visiting.add(type))break;path.add(type);
            if(type.equals(current.name)){type=current.superName;continue;}
            if(loader==null)break;
            Class<?> actual=ExternalCodeDefinitions.initiated(loader,type);
            if(actual!=null){
                for(Class<?> ancestor=actual;ancestor!=null;ancestor=ancestor.getSuperclass())
                    if(ancestor.getName().equals("net.minecraft.world.entity.Entity")){result=true;break;}
                break;
            }
            try(InputStream input=loader.getResourceAsStream(type+".class")) {type=input==null?null:new ClassReader(input).getSuperName();}
            catch(java.io.IOException|RuntimeException unavailable){return false;}
        }
        // The cache belongs only to this weave and its exact initiating loader.
        // Walk the full parent chain without a depth cutoff or Java recursion.
        for(String observed:path)cache.put(observed,result);return result;
    }
}
