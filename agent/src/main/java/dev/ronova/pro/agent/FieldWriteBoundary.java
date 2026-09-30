package dev.ronova.pro.agent;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Selected entity state writes; resolves the declaring field, including inherited symbolic owners. */
final class FieldWriteBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static final Set<String> FIELDS=Set.of("id","f_19848_","remainingFireTicks","f_19831_","removalReason","f_146795_",
            "baseValue","cachedValue","isCanceled","canceled","hash","size","modCount","n","mask","first","last","containsNullKey",
            "count","maxFill","minN","f","threshold","loadFactor","info","key",
            "canUpdate","isAddedToWorld","dead","f_20890_","deathTime","f_20919_","value","f_135391_","remove","f_58859_");
    private static final Set<String> OWNERS=Set.of("net/minecraft/world/entity/Entity","net/minecraft/world/entity/LivingEntity",
            "net/minecraft/network/syncher/SynchedEntityData$DataItem");
    static byte[] transform(ClassLoader loader,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        // Core initializes its own policy maps before publishing guards. Its internal constructors are not
        // third-party writer sites; instrumenting them re-enters partially initialized policy classes.
        if(node.name.startsWith("dev/ronova/pro/")&&!node.name.startsWith("dev/ronova/pro/validation/"))return null;
        for(MethodNode method:node.methods) {
            boolean initializing=method.name.equals("<init>"),initialized=!initializing;
            for(AbstractInsnNode insn:method.instructions.toArray()) {
                if(initializing&&insn instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL
                        &&call.name.equals("<init>")&&(call.owner.equals(node.name)||call.owner.equals(node.superName)))
                    initialized=true;
                if(!(insn instanceof FieldInsnNode field)||(field.getOpcode()!=Opcodes.PUTFIELD&&field.getOpcode()!=Opcodes.PUTSTATIC)
                        ||(!FIELDS.contains(field.name)&&!field.desc.startsWith("L")&&!field.desc.startsWith("[")
                            &&node.name.startsWith("net/minecraft/")))continue;
                if(method.name.equals("<clinit>")&&node.name.startsWith("net/minecraft/")
                        &&!FIELDS.contains(field.name))continue;
                // A verifier-visible uninitialized this cannot be passed to a bridge. Ordinary Java constructors
                // call super/this first; all later writes, including writes to other live objects, are guarded.
                if(initializing&&!initialized&&field.getOpcode()==Opcodes.PUTFIELD)continue;
                // JDK map view caches carry no entries or backing array. Instrumenting their lazy
                // initialization can invert the SourceMaps ledger and TaskBridge helper locks.
                if((field.owner.equals("java/util/HashMap")||field.owner.equals("java/util/IdentityHashMap"))
                        &&Set.of("entrySet","keySet","values").contains(field.name))continue;
                Type type=Type.getType(field.desc);int value=method.maxLocals;method.maxLocals+=type.getSize();
                boolean statik=field.getOpcode()==Opcodes.PUTSTATIC;int receiver=statik?-1:method.maxLocals++;
                int ticket=method.maxLocals++;
                InsnList code=new InsnList();LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),skip=new LabelNode();
                code.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE),value));
                if(!statik) {code.add(new VarInsnNode(Opcodes.ASTORE,receiver));code.add(new VarInsnNode(Opcodes.ALOAD,receiver));}
                else code.add(new LdcInsnNode(Type.getObjectType(field.owner)));
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
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endFieldMutation","(Ljava/lang/Object;)V",false));
                code.add(new JumpInsnNode(Opcodes.GOTO,skip));
                code.add(handler);
                code.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endFieldMutation","(Ljava/lang/Object;)V",false));
                code.add(new InsnNode(Opcodes.ATHROW));
                code.add(skip);
                method.instructions.insertBefore(field,code);method.instructions.remove(field);changed=true;
                method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
            }
        }
        if(!changed)return null;ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
}
