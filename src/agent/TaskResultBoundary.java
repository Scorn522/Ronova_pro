package dev.ronova.pro.agent;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Included in the canonical JDK boundary image, not applied after its certification. */
final class TaskResultBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge",CF="java/util/concurrent/CompletableFuture";
    private static String contract(MethodInsnNode call){
        String key=call.owner+"#"+call.name+call.desc;
        return switch(key){
            case "java/util/function/Function#apply(Ljava/lang/Object;)Ljava/lang/Object;"->"function";
            case "java/util/function/Consumer#accept(Ljava/lang/Object;)V"->"consumer";
            case "java/util/function/Supplier#get()Ljava/lang/Object;"->"supplier";
            case "java/lang/Runnable#run()V"->"runnable";
            case "java/util/concurrent/Callable#call()Ljava/lang/Object;"->"callable";
            case "java/util/function/BiFunction#apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"->"bifunction";
            case "java/util/function/BiConsumer#accept(Ljava/lang/Object;Ljava/lang/Object;)V"->"biconsumer";
            case "java/util/function/Predicate#test(Ljava/lang/Object;)Z"->"predicate";
            default->null;
        };
    }
    static void instrument(ClassNode node) {
        for(MethodNode m:node.methods) {
            if(m.name.startsWith("<"))continue;
            // Guard actual user callbacks inside the JDK's own catch/completion path. Do not silently return
            // at run()/tryFire(): that would strand a Future or its dependents.
            for(AbstractInsnNode insn:m.instructions.toArray())if(insn instanceof MethodInsnNode call
                    &&(node.name.startsWith(CF)||node.name.equals("java/util/concurrent/FutureTask")||node.name.startsWith("java/util/concurrent/ForkJoinTask$")||node.name.equals("java/util/concurrent/Executors$RunnableAdapter")
                        ||node.name.equals("java/util/concurrent/ThreadPoolExecutor")||node.name.equals("java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy"))
                    &&call.getOpcode()==Opcodes.INVOKEINTERFACE
                    &&(call.owner.startsWith("java/util/function/")||call.owner.equals("java/util/concurrent/Callable")||call.owner.equals("java/lang/Runnable"))) {
                Type[] args=Type.getArgumentTypes(call.desc);int[] locals=new int[args.length];InsnList c=new InsnList();
                for(int i=args.length-1;i>=0;i--) { locals[i]=m.maxLocals;m.maxLocals+=args[i].getSize();c.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE),locals[i])); }
                String contract=contract(call);
                if(contract==null){
                    c.add(new InsnNode(Opcodes.DUP));c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"requireInvocation","(Ljava/lang/Object;)V",false));
                    for(int i=0;i<args.length;i++)c.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),locals[i]));m.instructions.insertBefore(call,c);
                }else{
                    for(int i=0;i<2;i++)c.add(i<args.length?new VarInsnNode(Opcodes.ALOAD,locals[i]):new InsnNode(Opcodes.ACONST_NULL));
                    c.add(new LdcInsnNode(contract));m.instructions.insertBefore(call,c);
                    MethodInsnNode wrapped=new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"invokeEquipmentCallback","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false);m.instructions.set(call,wrapped);
                    InsnList returned=new InsnList();Type result=Type.getReturnType(call.desc);
                    if(result.getSort()==Type.VOID)returned.add(new InsnNode(Opcodes.POP));
                    else if(result.getSort()==Type.BOOLEAN){returned.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/Boolean"));returned.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"java/lang/Boolean","booleanValue","()Z",false));}
                    if(returned.size()>0)m.instructions.insert(wrapped,returned);
                }
            }
            boolean future=node.name.equals("java/util/concurrent/FutureTask")&&m.name.equals("set")&&m.desc.equals("(Ljava/lang/Object;)V");
            boolean cf=node.name.equals(CF)&&Set.of("completeNull","completeValue","completeRelay","internalComplete").contains(m.name)
                    &&Set.of("()Z","(Ljava/lang/Object;)Z").contains(m.desc);
            boolean obtrude=node.name.equals(CF)&&m.name.equals("obtrudeValue")&&m.desc.equals("(Ljava/lang/Object;)V");
            if(!future&&!cf&&!obtrude)continue;
            int ticket=m.maxLocals++;
            InsnList c=new InsnList();LabelNode allowed=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
            c.add(new VarInsnNode(Opcodes.ALOAD,0));
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"beginTaskPublication","(Ljava/lang/Object;)Ljava/lang/Object;",false));
            c.add(new VarInsnNode(Opcodes.ASTORE,ticket));
            c.add(new VarInsnNode(Opcodes.ALOAD,ticket));
            c.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
            c.add(new JumpInsnNode(Opcodes.IF_ACMPNE,allowed));c.add(new VarInsnNode(Opcodes.ALOAD,0));
            c.add(new TypeInsnNode(Opcodes.NEW,"java/util/concurrent/CancellationException"));c.add(new InsnNode(Opcodes.DUP));
            c.add(new LdcInsnNode("RONOVA_TASK_RESULT_REFUSED"));c.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/util/concurrent/CancellationException","<init>","(Ljava/lang/String;)V",false));
            if(future) {
                c.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.name,"setException","(Ljava/lang/Throwable;)V",false));c.add(new InsnNode(Opcodes.RETURN));
            } else {
                c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,CF,"completeThrowable","(Ljava/lang/Throwable;)Z",false));
                if(obtrude) { c.add(new InsnNode(Opcodes.POP));c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,CF,"postComplete","()V",false));c.add(new InsnNode(Opcodes.RETURN)); }
                else c.add(new InsnNode(Opcodes.IRETURN));
            }
            c.add(allowed);m.instructions.insert(c);
            for(AbstractInsnNode instruction:m.instructions.toArray()) {
                boolean finish=instruction instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKEVIRTUAL
                        &&(future&&call.name.equals("finishCompletion")||obtrude&&call.name.equals("postComplete"));
                if(!finish&&instruction.getOpcode()!=Opcodes.RETURN&&instruction.getOpcode()!=Opcodes.IRETURN)continue;
                InsnList release=new InsnList();release.add(new VarInsnNode(Opcodes.ALOAD,ticket));
                release.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endTaskPublication","(Ljava/lang/Object;)V",false));
                // The denial path returns before the original body; its token has already been closed.
                if(instruction.getPrevious()!=null)m.instructions.insertBefore(instruction,release);
            }
            m.instructions.add(end);m.instructions.add(handler);
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD,ticket));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endTaskPublication","(Ljava/lang/Object;)V",false));
            m.instructions.add(new InsnNode(Opcodes.ATHROW));
            m.tryCatchBlocks.add(new TryCatchBlockNode(allowed,end,handler,null));
        }
    }
}
