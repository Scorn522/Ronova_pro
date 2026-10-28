package dev.ronova.pro.agent;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Selected entity state writes; resolves the declaring field, including inherited symbolic owners. */
final class FieldWriteBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/ControlBridge";
    private record MutationContext(List<TryCatchBlockNode> catches,boolean initialized){}
    private static final Set<String> FIELDS=Set.of("id","f_19848_","remainingFireTicks","f_19831_","removalReason","f_146795_",
            "baseValue","cachedValue","isCanceled","canceled","hash","size","modCount","n","mask","first","last","containsNullKey",
            "count","maxFill","minN","f","threshold","loadFactor","info","key",
            "canUpdate","isAddedToWorld","dead","f_20890_","deathTime","f_20919_","value","f_135391_","remove","f_58859_");
    private static final Set<String> OWNERS=Set.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/LivingEntity",
            "net/minecraft/network/syncher/SynchedEntityData$DataItem");
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(!transform(null,node))return null;
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    static boolean transform(Module module,ClassNode node) {
        boolean changed=false;
        // Core initializes its own policy maps before publishing guards. Its internal constructors are not
        // third-party writer sites; instrumenting them re-enters partially initialized policy classes.
        if(module!=null&&ModGroupBoundary.ids(module).contains("ronova_pro"))return false;
        boolean modWriter=RecoveryAgent.producerModule(module);
        for(MethodNode method:node.methods) {
            ConstructorFlow.Entry constructor;
            try{constructor=ConstructorFlow.entry(node,method);}
            catch(jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException failure){
                throw new IllegalStateException("FIELD_CONSTRUCTOR_RECEIVER_UNOBSERVED:"+node.name+"#"+method.desc,failure);
            }
            AbstractInsnNode[] original=method.instructions.toArray();
            List<TryCatchBlockNode> catches=List.copyOf(method.tryCatchBlocks);
            int[] starts=null,ends=null;
            Map<MutationContext,LabelNode> handlers=null;
            // These slots are live only for one guarded write. Reusing them also
            // avoids WIDE local instructions in large registry initializers.
            int value=method.maxLocals,receiver=value+2,ticket=value+3;
            for(int position=0;position<original.length;position++) {
                AbstractInsnNode insn=original[position];
                if(!(insn instanceof FieldInsnNode field)||(field.getOpcode()!=Opcodes.PUTFIELD&&field.getOpcode()!=Opcodes.PUTSTATIC))continue;
                if(!modWriter&&!sensitive(field))continue;
                // Only the actual uninitialized receiver is ineligible for a bridge call.
                // Writes to existing objects remain guarded even before this/super initialization.
                if(constructor!=null&&constructor.uninitializedWrites().contains(field))continue;
                boolean initialized=constructor==null||!constructor.uninitializedSites().contains(insn);
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
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","finishFieldMutation","(Ljava/lang/Object;Z)V",false));
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
                if(type.getSort()!=Type.OBJECT&&type.getSort()!=Type.ARRAY){
                    LabelNode guarded=new LabelNode();
                    code.add(new InsnNode(Opcodes.DUP));code.add(new LdcInsnNode(Type.getObjectType(field.owner)));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"primitiveValueUntracked","(Ljava/lang/Object;Ljava/lang/Class;)Z",false));
                    code.add(new JumpInsnNode(Opcodes.IFEQ,guarded));code.add(new InsnNode(Opcodes.POP));
                    code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,ticket));
                    code.add(new JumpInsnNode(Opcodes.GOTO,start));code.add(guarded);
                }
                code.add(new LdcInsnNode(Type.getObjectType(field.owner)));code.add(new LdcInsnNode(field.name));
                code.add(new LdcInsnNode(field.desc));
                code.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),value));ControlClassWriter.box(code,type);
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","beginFieldMutation",
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
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                code.add(skip);
                method.instructions.insertBefore(field,code);method.instructions.remove(field);changed=true;
                method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,null));
            }
            // SourceInterpreter in the following creation stage needs room
            // for DUP2 + declaring Class + selector before frames are emitted.
            // All sites reuse these extra slots; they do not accumulate.
            if(handlers!=null)method.maxStack+=4;
            if(handlers!=null)for(var entry:handlers.entrySet()) {
                LabelNode handler=entry.getValue(),end=new LabelNode();
                method.instructions.add(handler);
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","finishFieldMutation","(Ljava/lang/Object;Z)V",false));
                method.instructions.add(new InsnNode(Opcodes.ATHROW));method.instructions.add(end);
                // A shared cleanup still rethrows through exactly the original
                // ordered catch/finally regions that covered the failed write.
                for(TryCatchBlockNode block:entry.getKey().catches())method.tryCatchBlocks.add(new TryCatchBlockNode(handler,end,block.handler,block.type));
            }
        }
        return changed;
    }
    private static boolean sensitive(FieldInsnNode field){
        if(field.owner.startsWith("dev/ronova/pro/"))return true;
        if(OWNERS.contains(field.owner))return FIELDS.contains(field.name)
                ||Set.of("uuid","f_19820_","stringUUID","f_19821_","entityData","f_19804_","level","f_19853_").contains(field.name);
        return field.owner.equals("net/minecraft/world/entity/ai/attributes/AttributeInstance")
                ||field.owner.equals("net/minecraft/world/item/ItemStack")
                ||field.owner.equals("net/minecraft/world/entity/player/Inventory")
                ||field.owner.equals("net/minecraft/world/food/FoodData")
                ||field.owner.equals("net/minecraft/world/effect/MobEffectInstance")
                ||field.owner.startsWith("net/minecraft/world/level/storage/PrimaryLevelData")
                ||field.owner.startsWith("net/minecraft/world/level/storage/DerivedLevelData")
                ||field.owner.equals("net/minecraft/network/syncher/SynchedEntityData");
    }
}
