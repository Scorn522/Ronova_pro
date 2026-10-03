package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Identifies initialization of the original receiver rather than a same-named constructor call. */
final class ConstructorFlow {
    record Entry(Set<MethodInsnNode> initialized,Set<FieldInsnNode> uninitializedWrites){}
    private ConstructorFlow(){}
    static Entry entry(ClassNode owner,MethodNode method)throws AnalyzerException{
        if(!method.name.equals("<init>")||owner.superName==null)return null;
        AbstractInsnNode marker=new InsnNode(Opcodes.NOP);AbstractInsnNode[] code=method.instructions.toArray();
        SourceInterpreter interpreter=new SourceInterpreter(Opcodes.ASM8){
            @Override public SourceValue newParameterValue(boolean instance,int local,Type type){
                return instance&&local==0?new SourceValue(1,marker):super.newParameterValue(instance,local,type);
            }
            @Override public SourceValue copyOperation(AbstractInsnNode at,SourceValue value){return value;}
        };
        List<Set<Integer>> normal=new ArrayList<>(),exceptional=new ArrayList<>();
        for(int i=0;i<code.length;i++){normal.add(new HashSet<>());exceptional.add(new HashSet<>());}
        Analyzer<SourceValue> analyzer=new Analyzer<>(interpreter){
            @Override protected void newControlFlowEdge(int from,int to){normal.get(from).add(to);}
            @Override protected boolean newControlFlowExceptionEdge(int from,int to){exceptional.get(from).add(to);return true;}
        };
        Frame<SourceValue>[] origins=analyzer.analyze(owner.name,method);
        Set<MethodInsnNode> initialized=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=0;i<code.length;i++)if(code[i] instanceof MethodInsnNode call&&call.getOpcode()==Opcodes.INVOKESPECIAL&&call.name.equals("<init>")&&origins[i]!=null){
            int argument=origins[i].getStackSize()-Type.getArgumentTypes(call.desc).length-1;
            if(argument<0)continue;SourceValue receiver=origins[i].getStack(argument);
            if(!receiver.insns.contains(marker))continue;
            if(receiver.insns.size()!=1)throw new AnalyzerException(call,"CONSTRUCTOR_INITIALIZATION_PATH_UNOBSERVED");
            initialized.add(call);
        }
        if(initialized.isEmpty())throw new AnalyzerException(method.instructions.getFirst(),"CONSTRUCTOR_INITIALIZATION_UNOBSERVED");
        BitSet prefix=new BitSet(code.length);Deque<Integer> pending=new ArrayDeque<>();pending.add(0);
        while(!pending.isEmpty()){
            int i=pending.removeFirst();if(prefix.get(i)||origins[i]==null)continue;prefix.set(i);
            if(!(code[i] instanceof MethodInsnNode call&&initialized.contains(call)))pending.addAll(normal.get(i));
            pending.addAll(exceptional.get(i));
        }
        Set<FieldInsnNode> writes=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=prefix.nextSetBit(0);i>=0;i=prefix.nextSetBit(i+1))if(code[i] instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD){
            int receiver=origins[i].getStackSize()-2;
            if(receiver>=0&&origins[i].getStack(receiver).insns.contains(marker))writes.add(field);
        }
        return new Entry(initialized,writes);
    }
}
