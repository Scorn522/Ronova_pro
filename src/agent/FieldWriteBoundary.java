package dev.ronova.pro.agent;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Selected entity state writes; resolves the declaring field, including inherited symbolic owners. */
final class FieldWriteBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private record MutationContext(List<TryCatchBlockNode> catches,boolean initialized){}
    private static final Set<String> FIELDS=Set.of("id","f_19848_","remainingFireTicks","f_19831_","removalReason","f_146795_",
            "baseValue","cachedValue","isCanceled","canceled","hash","size","modCount","n","mask","first","last","containsNullKey",
            "count","maxFill","minN","f","threshold","loadFactor","info","key",
            "canUpdate","isAddedToWorld","dead","f_20890_","deathTime","f_20919_","value","f_135391_","remove","f_58859_");
    private static final Set<String> OWNERS=Set.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/LivingEntity",
            "net/minecraft/network/syncher/SynchedEntityData$DataItem");
    private static final Set<String> SOURCE_CARRIERS=Set.of("java/util/concurrent/atomic/AtomicReference","java/util/concurrent/CopyOnWriteArrayList");
    private static final Set<String> INSTALLED_CARRIERS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean sourceCarrier(String name){return name!=null&&SOURCE_CARRIERS.contains(name);}
    static boolean sourceCarriersInstalled(){return INSTALLED_CARRIERS.containsAll(SOURCE_CARRIERS);}
    static byte[] transform(ClassLoader loader,byte[] bytes) {return transform(loader,null,bytes);}
    static byte[] transform(ClassLoader loader,Module module,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        boolean carrier=loader==null&&sourceCarrier(node.name);
        if(carrier&&node.version!=Opcodes.V17)throw new IllegalStateException("SOURCE_CARRIER_LAYOUT_UNAVAILABLE:"+node.name);
        if(carrier)return sourceCarrier(loader,node);
        // Core initializes its own policy maps before publishing guards. Its internal constructors are not
        // third-party writer sites; instrumenting them re-enters partially initialized policy classes.
        if(module!=null&&ModGroupBoundary.ids(module).contains("ronova_pro"))return null;
        for(MethodNode method:node.methods) {
            boolean initializing=method.name.equals("<init>"),initialized=!initializing;
            AbstractInsnNode[] original=method.instructions.toArray();
            List<TryCatchBlockNode> catches=List.copyOf(method.tryCatchBlocks);
            int[] starts=null,ends=null;
            Map<MutationContext,LabelNode> handlers=null;
            // These slots are live only for one guarded write. Reusing them also
            // avoids WIDE local instructions in large registry initializers.
            int value=method.maxLocals,receiver=value+2,ticket=value+3;
            for(int position=0;position<original.length;position++) {
                AbstractInsnNode insn=original[position];
                if(initializing&&insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                        &&call.name.equals("<init>")&&(call.owner.equals(node.name)||call.owner.equals(node.superName)))
                    initialized=true;
                if(!(insn instanceof FieldInsnNode field)||(field.getOpcode()!=Opcodes.PUTFIELD&&field.getOpcode()!=Opcodes.PUTSTATIC))continue;
                if(carrier&&(!field.owner.equals(node.name)||!field.name.equals(node.name.endsWith("/AtomicReference")?"value":"array")))continue;
                // A verifier-visible uninitialized this cannot be passed to a bridge. Ordinary Java constructors
                // call super/this first; all later writes, including writes to other live objects, are guarded.
                if(initializing&&!initialized&&field.getOpcode()==Opcodes.PUTFIELD)continue;
                // JDK map view caches carry no entries or backing array. Instrumenting their lazy
                // initialization can invert the SourceMaps ledger and TaskBridge helper locks.
                if((field.owner.equals("java/util/HashMap")||field.owner.equals("java/util/IdentityHashMap"))
                        &&Set.of("entrySet","keySet","values").contains(field.name))continue;
                Type type=Type.getType(field.desc);boolean statik=field.getOpcode()==Opcodes.PUTSTATIC;
                method.maxLocals=value+4;
                if(handlers==null){
                    handlers=new LinkedHashMap<>();starts=new int[catches.size()];ends=new int[catches.size()];
                    for(int i=0;i<catches.size();i++){starts[i]=method.instructions.indexOf(catches.get(i).start);ends[i]=method.instructions.indexOf(catches.get(i).end);}
                }
                List<TryCatchBlockNode> covering=null;
                for(int i=0;i<catches.size();i++)if(starts[i]<=position&&position<ends[i]){
                    if(covering==null)covering=new ArrayList<>();covering.add(catches.get(i));
                }
                MutationContext context=new MutationContext(covering==null?List.of():List.copyOf(covering),initialized);
                LabelNode handler=handlers.get(context);
                if(handler==null){handler=new LabelNode();handlers.put(context,handler);}
                InsnList code=new InsnList();LabelNode start=new LabelNode(),end=new LabelNode(),skip=new LabelNode();
                if(statik){
                    boolean own=field.owner.equals(node.name);
                    String valueType=type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY?"Ljava/lang/Object;":field.desc;
                    code.add(new InsnNode(type.getSize()==2?Opcodes.DUP2:Opcodes.DUP));
                    if(!own)code.add(new LdcInsnNode(Type.getObjectType(field.owner)));
                    code.add(new LdcInsnNode(field.name+'\u0000'+field.desc));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,own?"beginOwnStaticFieldMutation":"beginStaticFieldMutation",
                            "("+valueType+(own?"":"Ljava/lang/Class;")+"Ljava/lang/String;)Ljava/lang/Object;",false));
                    code.add(new VarInsnNode(Opcodes.ASTORE,ticket));code.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                    code.add(new JumpInsnNode(Opcodes.IFNULL,skip));code.add(start);
                    code.add(new FieldInsnNode(Opcodes.PUTSTATIC,field.owner,field.name,field.desc));code.add(end);
                    code.add(new VarInsnNode(Opcodes.ALOAD,ticket));code.add(new InsnNode(Opcodes.ICONST_1));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                    LabelNode done=new LabelNode();code.add(new JumpInsnNode(Opcodes.GOTO,done));code.add(skip);
                    code.add(new InsnNode(type.getSize()==2?Opcodes.POP2:Opcodes.POP));code.add(done);
                    method.instructions.insertBefore(field,code);method.instructions.remove(field);changed=true;
                    method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,null));continue;
                }
                code.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE),value));
                if(!statik) {
                    code.add(new VarInsnNode(Opcodes.ASTORE,receiver));code.add(new VarInsnNode(Opcodes.ALOAD,receiver));
                    // Null is the static boundary marker. Preserve PUTFIELD's
                    // null-receiver failure before invoking that shared boundary.
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/util/Objects","requireNonNull","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                }
                else code.add(new InsnNode(Opcodes.ACONST_NULL));
                code.add(new LdcInsnNode(Type.getObjectType(field.owner)));code.add(new LdcInsnNode(field.name));
                code.add(new LdcInsnNode(field.desc));
                code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),value));ControlClassWriter.box(code,type);
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"beginFieldMutation",
                        "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",false));
                code.add(new VarInsnNode(Opcodes.ASTORE,ticket));
                code.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                code.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
                code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,skip));
                code.add(start);
                if(!statik)code.add(new VarInsnNode(Opcodes.ALOAD,receiver));
                code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),value));
                code.add(new FieldInsnNode(field.getOpcode(),field.owner,field.name,field.desc));
                code.add(end);
                code.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                code.add(new InsnNode(Opcodes.ICONST_1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                code.add(skip);
                method.instructions.insertBefore(field,code);method.instructions.remove(field);changed=true;
                method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,null));
            }
            if(handlers!=null)for(var entry:handlers.entrySet()) {
                LabelNode handler=entry.getValue(),end=new LabelNode();
                method.instructions.add(handler);
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                method.instructions.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(end);
                // A shared cleanup still rethrows through exactly the original
                // ordered catch/finally regions that covered the failed write.
                for(TryCatchBlockNode block:entry.getKey().catches())method.tryCatchBlocks.add(new TryCatchBlockNode(handler,end,block.handler,block.type));
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);
        byte[] result=writer.toByteArray();if(carrier)INSTALLED_CARRIERS.add(node.name);return result;
    }
    /** Plain AtomicReference.set and COW setArray do not pass through Unsafe. */
    private static byte[] sourceCarrier(ClassLoader loader,ClassNode node){
        String source="dev/ronova/pro/bootstrap/SourceMapBridge";int stores=0;
        String slot=node.name.endsWith("/AtomicReference")?"value":"array";
        for(MethodNode method:node.methods){
            AbstractInsnNode[] original=method.instructions.toArray();
            for(AbstractInsnNode at:original){
                if(method.name.equals("<init>")&&at.getOpcode()==Opcodes.RETURN){
                    InsnList born=new InsnList();born.add(new VarInsnNode(Opcodes.ALOAD,0));
                    born.add(new MethodInsnNode(Opcodes.INVOKESTATIC,source,"born","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(at,born);
                }
                if(!(at instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.PUTFIELD||!field.owner.equals(node.name)||!field.name.equals(slot))continue;
                int value=method.maxLocals++,receiver=method.maxLocals++,scope=method.maxLocals++,ticket=method.maxLocals++,written=method.maxLocals++,error=method.maxLocals++;
                LabelNode start=new LabelNode(),write=new LabelNode(),end=new LabelNode(),failed=new LabelNode(),resume=new LabelNode();InsnList code=new InsnList();
                code.add(new VarInsnNode(Opcodes.ASTORE,value));code.add(new VarInsnNode(Opcodes.ASTORE,receiver));
                code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new InsnNode(Opcodes.ICONST_1));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,source,"enter","(Ljava/lang/Object;Z)Ljava/lang/Object;",false));code.add(new VarInsnNode(Opcodes.ASTORE,scope));
                code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,ticket));code.add(new InsnNode(Opcodes.ICONST_0));code.add(new VarInsnNode(Opcodes.ISTORE,written));code.add(start);
                code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,source,"carrierRegistered","(Ljava/lang/Object;)Z",false));code.add(new JumpInsnNode(Opcodes.IFEQ,write));
                code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new LdcInsnNode(Type.getObjectType(field.owner)));code.add(new LdcInsnNode(field.name));code.add(new LdcInsnNode(field.desc));code.add(new VarInsnNode(Opcodes.ALOAD,value));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"beginFieldMutation","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",false));code.add(new VarInsnNode(Opcodes.ASTORE,ticket));
                code.add(new VarInsnNode(Opcodes.ALOAD,ticket));code.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ,end));
                code.add(write);code.add(new VarInsnNode(Opcodes.ALOAD,receiver));code.add(new VarInsnNode(Opcodes.ALOAD,value));code.add(new FieldInsnNode(Opcodes.PUTFIELD,field.owner,field.name,field.desc));
                code.add(new InsnNode(Opcodes.ICONST_1));code.add(new VarInsnNode(Opcodes.ISTORE,written));code.add(end);
                code.add(new VarInsnNode(Opcodes.ALOAD,scope));code.add(new VarInsnNode(Opcodes.ALOAD,ticket));code.add(new VarInsnNode(Opcodes.ILOAD,written));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"finishSourceCarrier","(Ljava/lang/Object;Ljava/lang/Object;Z)V",false));code.add(new JumpInsnNode(Opcodes.GOTO,resume));
                code.add(failed);code.add(new VarInsnNode(Opcodes.ASTORE,error));code.add(new VarInsnNode(Opcodes.ALOAD,scope));code.add(new VarInsnNode(Opcodes.ALOAD,ticket));code.add(new VarInsnNode(Opcodes.ALOAD,error));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"failedSourceCarrier","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Throwable;)V",false));code.add(new VarInsnNode(Opcodes.ALOAD,error));code.add(new InsnNode(Opcodes.ATHROW));code.add(resume);
                method.instructions.insertBefore(at,code);method.instructions.remove(at);method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,failed,null));stores++;
            }
        }
        if(stores==0)throw new IllegalStateException("SOURCE_CARRIER_WRITE_UNAVAILABLE:"+node.name);
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();INSTALLED_CARRIERS.add(node.name);return result;
    }
}
