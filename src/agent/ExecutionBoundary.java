package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Shadow cells follow the emitted JVM instructions; observation calls do not consume business values. */
final class ExecutionBoundary {
    private static final String BRIDGE="dev/ronova/pro/bootstrap/CodeSourceBridge";
    private static final int CONSTRUCTOR_SITE=Integer.MIN_VALUE;
    private record Site(LabelNode label,int instruction){}
    private record UnsafeCall(String kind,int destination,int offset,int length){}
    private record MethodData(String selector,int locals,int[] arguments,int[][] operations,Module[][] sources,String[][] fields,boolean constructor,List<Site> sites){}
    static final class Weaving {
        final Map<String,Module[][]> owners;final IdentityHashMap<AbstractInsnNode,LabelNode> actual;private final List<MethodData> methods;
        Weaving(Map<String,Module[][]> owners,List<MethodData> methods,IdentityHashMap<AbstractInsnNode,LabelNode> actual){this.owners=Map.copyOf(owners);this.methods=List.copyOf(methods);this.actual=actual;}
        boolean present(){return !methods.isEmpty();}
        Object[][] layout(){
            Object[][] result=new Object[methods.size()][];
            for(int i=0;i<methods.size();i++){
                MethodData method=methods.get(i);int[][] sites=new int[method.sites.size()][];
                for(int j=0;j<sites.length;j++){Site site=method.sites.get(j);sites[j]=new int[]{site.label.getLabel().getOffset(),site.instruction};}
                result[i]=new Object[]{method.selector,method.locals,method.arguments,method.operations,method.sources,sites,method.fields,method.constructor};
            }
            return result;
        }
    }
    private static final class Shape extends BasicInterpreter {
        int consumed,width;boolean reference;
        Shape(){super(Opcodes.ASM8);}
        private BasicValue result(int inputs,BasicValue value){consumed=inputs;width=value==null?0:value.getSize();reference=value!=null&&value.getType()!=null&&(value.getType().getSort()==Type.OBJECT||value.getType().getSort()==Type.ARRAY);return value;}
        @Override public BasicValue newOperation(AbstractInsnNode at)throws AnalyzerException{return result(0,super.newOperation(at));}
        @Override public BasicValue unaryOperation(AbstractInsnNode at,BasicValue value)throws AnalyzerException{return result(1,super.unaryOperation(at,value));}
        @Override public BasicValue binaryOperation(AbstractInsnNode at,BasicValue first,BasicValue second)throws AnalyzerException{return result(2,super.binaryOperation(at,first,second));}
        @Override public BasicValue ternaryOperation(AbstractInsnNode at,BasicValue first,BasicValue second,BasicValue third)throws AnalyzerException{return result(3,super.ternaryOperation(at,first,second,third));}
        @Override public BasicValue naryOperation(AbstractInsnNode at,List<? extends BasicValue> values)throws AnalyzerException{return result(values.size(),super.naryOperation(at,values));}
        @Override public void returnOperation(AbstractInsnNode at,BasicValue value,BasicValue expected)throws AnalyzerException{super.returnOperation(at,value,expected);consumed=1;width=0;reference=false;}
        int[] operation(AbstractInsnNode at,Frame<BasicValue> original,int end)throws AnalyzerException{
            consumed=0;width=0;reference=false;new Frame<>(original).execute(at,this);int opcode=at.getOpcode(),operand=0;
            if(at instanceof VarInsnNode variable){
                operand=variable.var;
                if(opcode>=Opcodes.ILOAD&&opcode<=Opcodes.ALOAD){width=opcode==Opcodes.LLOAD||opcode==Opcodes.DLOAD?2:1;reference=opcode==Opcodes.ALOAD;}
                else if(opcode>=Opcodes.ISTORE&&opcode<=Opcodes.ASTORE){consumed=1;width=0;}
            }else if(at instanceof IincInsnNode increment){operand=increment.var;consumed=0;width=0;}
            if(opcode==Opcodes.POP){consumed=1;width=0;}
            if(opcode==Opcodes.POP2||opcode>=Opcodes.DUP&&opcode<=Opcodes.SWAP){consumed=0;width=0;}
            return new int[]{opcode,operand,consumed,width,end,reference?1:0};
        }
    }
    private ExecutionBoundary(){}
    static Weaving weave(ClassNode node,Map<String,Module[][]> direct,Map<String,Module[][]> owners,Module declaration){
        Map<String,Module[][]> emitted=new LinkedHashMap<>(owners);List<MethodData> methods=new ArrayList<>();IdentityHashMap<AbstractInsnNode,LabelNode> actual=new IdentityHashMap<>();
        for(MethodNode method:node.methods){
            if((method.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0||method.instructions.size()==0)continue;
            String selector=method.name+method.desc;Module[][] sources=direct.get(selector);if(sources==null)continue;
            if(ExternalCodeRuntime.legacy(method)){
                gap(declaration,sources,node.name+"."+selector+":EXTERNAL_EXECUTION_PREFIX_UNOBSERVED");continue;
            }
            MethodData planned;ConstructorFlow.Entry constructor;
            try{constructor=ConstructorFlow.entry(node,method);planned=plan(node.name,method,sources,constructor);}
            catch(AnalyzerException failure){gap(declaration,sources,node.name+"."+selector+":EXTERNAL_EXECUTION_OPERANDS_UNOBSERVED");continue;}
            if(planned==null)continue;
            Module[][] previous=owners.get(selector);if(previous==null){previous=new Module[sources.length][];Arrays.setAll(previous,ignored->new Module[0]);}
            IdentityHashMap<AbstractInsnNode,Module[]> carried=new IdentityHashMap<>();int ordinal=0;
            for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)carried.put(at,previous[ordinal++]);
            methods.add(insert(method,planned,constructor,carried,actual));
            List<Module[]> rows=new ArrayList<>();for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()>=0)rows.add(carried.getOrDefault(at,new Module[0]));
            emitted.put(selector,rows.toArray(Module[][]::new));
        }
        return new Weaving(emitted,methods,actual);
    }
    private static void gap(Module declaration,Module[][] sources,String action){
        Set<Module> actual=Collections.newSetFromMap(new IdentityHashMap<>());if(RecoveryAgent.producerModule(declaration))actual.add(declaration);
        for(Module[] source:sources)Collections.addAll(actual,source);for(Module owner:actual)ModGroupBoundary.externalGap(owner,action);
    }
    private static MethodData plan(String owner,MethodNode method,Module[][] sources,ConstructorFlow.Entry constructor)throws AnalyzerException{
        AbstractInsnNode[] code=method.instructions.toArray();List<Set<Integer>> edges=new ArrayList<>(code.length+1);for(int i=0;i<=code.length;i++)edges.add(new HashSet<>());
        Analyzer<BasicValue> analyzer=new Analyzer<>(new BasicInterpreter()){
            @Override protected void newControlFlowEdge(int from,int to){edges.get(from).add(to);}
            @Override protected boolean newControlFlowExceptionEdge(int from,int to){edges.get(from).add(to);return true;}
        };
        Frame<BasicValue>[] frames=analyzer.analyze(owner,method);int count=0;int[] ordinals=new int[code.length+1];
        for(int i=0;i<code.length;i++){ordinals[i]=count;if(code[i].getOpcode()>=0)count++;}ordinals[code.length]=count;
        if(count!=sources.length)throw new IllegalStateException("EXTERNAL_EXECUTION_INSTRUCTION_LAYOUT_CHANGED:"+method.name+method.desc);
        for(int i=0;i<code.length;i++)if(frames[i]!=null&&(edges.get(i).isEmpty()||code[i].getOpcode()==Opcodes.ATHROW))edges.get(i).add(code.length);
        BitSet[] postdominators=ExternalCodeFlow.postdominators(edges,frames);int[][] operations=new int[count][];String[][] fields=new String[count][];Shape shape=new Shape();
        for(int i=0;i<code.length;i++)if(code[i].getOpcode()>=0){
            int end=-1;
            if(frames[i]!=null&&edges.get(i).size()>1){
                BitSet proper=(BitSet)postdominators[i].clone();proper.clear(i);
                for(int candidate=proper.nextSetBit(0);candidate>=0;candidate=proper.nextSetBit(candidate+1)){
                    BitSet remaining=(BitSet)proper.clone();remaining.andNot(postdominators[candidate]);
                    if(remaining.isEmpty()){end=ordinals[candidate];break;}
                }
            }
            operations[ordinals[i]]=frames[i]==null?new int[]{code[i].getOpcode(),0,0,0,end,0}:shape.operation(code[i],frames[i],end);
            if(code[i] instanceof FieldInsnNode field)fields[ordinals[i]]=new String[]{field.name,field.desc};
            else if(code[i] instanceof MethodInsnNode call){UnsafeCall unsafe=unsafeCall(call);
                fields[ordinals[i]]=unsafe==null?new String[]{call.owner,call.name,call.desc}:new String[]{call.owner,call.name,call.desc,unsafe.kind,Integer.toString(unsafe.destination),Integer.toString(unsafe.offset),Integer.toString(unsafe.length)};}
        }
        List<Integer> arguments=new ArrayList<>();if((method.access&Opcodes.ACC_STATIC)==0)arguments.add(1);
        for(Type type:Type.getArgumentTypes(method.desc))arguments.add(type.getSize());
        return new MethodData(method.name+method.desc,method.maxLocals,arguments.stream().mapToInt(Integer::intValue).toArray(),operations,sources,fields,constructor!=null,new ArrayList<>());
    }
    private static MethodData insert(MethodNode method,MethodData plan,ConstructorFlow.Entry constructor,IdentityHashMap<AbstractInsnNode,Module[]> carried,IdentityHashMap<AbstractInsnNode,LabelNode> positions){
        AbstractInsnNode[] original=method.instructions.toArray();int token=method.maxLocals++,error=method.maxLocals++;List<Site> sites=new ArrayList<>();
        LabelNode entry=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();InsnList enter=new InsnList();
        // Object.<init> starts with an initialized receiver. Its observer must avoid
        // re-entering itself when the source machinery allocates its own objects.
        String entryMethod=method.name.equals("<init>")&&constructor==null?"executionRootEnter":"executionEnter";
        enter.add(entry);enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,entryMethod,"()Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);sites.add(new Site(entry,-1));
        int slot=0;if((method.access&Opcodes.ACC_STATIC)==0){if(constructor==null)enter.add(argument(token,slot,sites));slot++;}
        for(Type type:Type.getArgumentTypes(method.desc)){if(reference(type))enter.add(argument(token,slot,sites));slot+=type.getSize();}
        method.instructions.insert(enter);
        Set<LabelNode> catches=Collections.newSetFromMap(new IdentityHashMap<>());for(TryCatchBlockNode block:method.tryCatchBlocks)catches.add(block.handler);
        int ordinal=0;
        for(AbstractInsnNode at:original){
            if(at instanceof LabelNode label&&catches.contains(label)){
                InsnList caught=hook("executionCaught",token,ordinal,sites);method.instructions.insert(label,caught);
            }
            if(at.getOpcode()<0)continue;
            InsnList before=hook("executionBefore",token,ordinal,sites);LabelNode actual=new LabelNode();sites.add(new Site(actual,ordinal));positions.put(at,actual);
            if(at.getOpcode()==Opcodes.ARETURN)before.add(reference(token,ordinal,"executionReference",sites));
            if(at.getOpcode()>=Opcodes.IRETURN&&at.getOpcode()<=Opcodes.RETURN)before.add(exit(token,true,ordinal,sites));
            InsnList after=new InsnList();
            boolean deferred=constructor!=null&&at instanceof FieldInsnNode field&&constructor.uninitializedWrites().contains(field);
            if(deferred)after.add(hook("executionUninitializedWrite",token,ordinal,sites));
            else if(at instanceof FieldInsnNode||at.getOpcode()>=Opcodes.IALOAD&&at.getOpcode()<=Opcodes.SALOAD||at.getOpcode()>=Opcodes.IASTORE&&at.getOpcode()<=Opcodes.SASTORE)
                heap(method,at,token,ordinal,before,after,sites);
            int receiver=-1;
            if(at instanceof MethodInsnNode call&&call.name.equals("<init>"))receiver=constructorReceiver(method,call,before);
            else if(at instanceof MethodInsnNode call){UnsafeCall unsafe=unsafeCall(call);if(unsafe!=null)unsafe(method,call,unsafe,token,ordinal,before,after,sites);}
            before.add(actual);
            carry(before,carried.get(at),carried);method.instructions.insertBefore(at,before);
            if(at instanceof MethodInsnNode||at instanceof InvokeDynamicInsnNode){
                if(receiver>=0){MethodInsnNode invoked=(MethodInsnNode)at;after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new LdcInsnNode(ordinal));after.add(new VarInsnNode(Opcodes.ALOAD,receiver));after.add(new LdcInsnNode(Type.getObjectType(invoked.owner)));
                    call(after,"executionInitialized","(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Class;)V",ordinal,sites);
                    if(constructor!=null&&constructor.initialized().contains(invoked)){
                        after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new VarInsnNode(Opcodes.ALOAD,receiver));
                        call(after,"executionConstructor","(Ljava/lang/Object;Ljava/lang/Object;)V",CONSTRUCTOR_SITE,sites);
                    }
                }
                after.add(hook("executionAfterCall",token,ordinal,sites));
                String descriptor=at instanceof MethodInsnNode call?call.desc:((InvokeDynamicInsnNode)at).desc;
                if(reference(Type.getReturnType(descriptor)))after.add(reference(token,ordinal,"executionReference",sites));
            }
            if(after.size()!=0){carry(after,carried.get(at),carried);method.instructions.insert(at,after);}
            ordinal++;
        }
        if(constructor==null){
            method.instructions.add(end);method.instructions.add(handler);method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
            method.instructions.add(exit(token,false,ordinal,sites));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
            method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
        }
        return new MethodData(plan.selector,plan.locals,plan.arguments,plan.operations,plan.sources,plan.fields,plan.constructor,List.copyOf(sites));
    }
    private static boolean reference(Type type){return type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY;}
    private static UnsafeCall unsafeCall(MethodInsnNode call){
        if(call.getOpcode()!=Opcodes.INVOKEVIRTUAL||!(call.owner.equals("sun/misc/Unsafe")||call.owner.equals("jdk/internal/misc/Unsafe")))return null;
        if(call.name.equals("setMemory")&&call.desc.equals("(Ljava/lang/Object;JJB)V"))return new UnsafeCall("Byte",0,1,2);
        if(call.name.equals("setMemory")&&call.desc.equals("(JJB)V"))return new UnsafeCall("Byte",-1,0,1);
        if(call.name.equals("copyMemory")&&call.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJ)V")
                ||call.name.equals("copySwapMemory")&&call.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJJ)V"))return new UnsafeCall("Byte",2,3,4);
        if(call.name.equals("copyMemory")&&call.desc.equals("(JJJ)V")||call.name.equals("copySwapMemory")&&call.desc.equals("(JJJJ)V"))return new UnsafeCall("Byte",-1,1,2);
        Type[] arguments=Type.getArgumentTypes(call.desc);Type result=Type.getReturnType(call.desc);
        if(call.name.matches("get(?:Object|Reference|Boolean|Byte|Short|Char|Int|Long|Float|Double|Address)(?:Volatile|Acquire|Opaque|Unaligned)?")&&result.getSort()!=Type.VOID){
            String kind=switch(result.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.CHAR->"Char";case Type.SHORT->"Short";case Type.INT->"Int";case Type.FLOAT->"Float";case Type.LONG->"Long";case Type.DOUBLE->"Double";case Type.OBJECT,Type.ARRAY->"Object";default->null;};
            if(kind!=null&&arguments.length==1&&arguments[0].getSort()==Type.LONG)return new UnsafeCall(kind,-1,0,-1);
            if(kind!=null&&arguments.length==2&&arguments[0].getDescriptor().equals("Ljava/lang/Object;")&&arguments[1].getSort()==Type.LONG)return new UnsafeCall(kind,0,1,-1);
        }
        if(call.name.startsWith("put")&&arguments.length==2&&arguments[0].getSort()==Type.LONG&&result.getSort()==Type.VOID){
            String kind=switch(arguments[1].getSort()){case Type.BYTE->"Byte";case Type.CHAR->"Char";case Type.SHORT->"Short";case Type.INT->"Int";case Type.FLOAT->"Float";case Type.LONG->"Long";case Type.DOUBLE->"Double";default->null;};
            if(kind!=null)return new UnsafeCall(kind,-1,0,-1);
        }
        if(arguments.length<3||!arguments[0].getDescriptor().equals("Ljava/lang/Object;")||arguments[1].getSort()!=Type.LONG)return null;
        Type value=arguments[arguments.length-1];
        boolean put=call.name.startsWith("put")&&arguments.length==3&&result.getSort()==Type.VOID;
        boolean compare=(call.name.startsWith("compareAndSet")||call.name.startsWith("compareAndSwap")||call.name.startsWith("weakCompareAndSet"))
                &&arguments.length==4&&arguments[2].equals(value)&&result.getSort()==Type.BOOLEAN;
        boolean exchange=call.name.startsWith("compareAndExchange")&&arguments.length==4&&arguments[2].equals(value)&&result.equals(value);
        boolean atomic=call.name.startsWith("getAnd")&&arguments.length==3&&result.equals(value);
        if(!(put||compare||exchange||atomic))return null;
        String kind=switch(value.getSort()){case Type.BOOLEAN->"Boolean";case Type.BYTE->"Byte";case Type.CHAR->"Char";case Type.SHORT->"Short";case Type.INT->"Int";case Type.FLOAT->"Float";case Type.LONG->"Long";case Type.DOUBLE->"Double";case Type.OBJECT,Type.ARRAY->"Object";default->null;};
        return kind==null?null:new UnsafeCall(kind,0,1,-1);
    }
    private static void unsafe(MethodNode method,MethodInsnNode invoked,UnsafeCall operation,int token,int instruction,InsnList before,InsnList after,List<Site> sites){
        Type[] arguments=Type.getArgumentTypes(invoked.desc);Type result=Type.getReturnType(invoked.desc);int[] slots=new int[arguments.length];
        for(int i=0;i<slots.length;i++){slots[i]=method.maxLocals;method.maxLocals+=arguments[i].getSize();}
        int accessor=method.maxLocals++,receipt=method.maxLocals++,failure=method.maxLocals++,returned=method.maxLocals;method.maxLocals+=result.getSize();
        LabelNode admitted=new LabelNode(),start=new LabelNode(),end=new LabelNode(),handler=new LabelNode(),resume=new LabelNode(),denied=new LabelNode(),finished=new LabelNode();
        for(int i=slots.length-1;i>=0;i--)before.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ISTORE),slots[i]));before.add(new VarInsnNode(Opcodes.ASTORE,accessor));
        before.add(new VarInsnNode(Opcodes.ALOAD,token));before.add(new LdcInsnNode(instruction));before.add(new VarInsnNode(Opcodes.ALOAD,accessor));
        before.add(operation.destination<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,slots[operation.destination]));before.add(new VarInsnNode(Opcodes.LLOAD,slots[operation.offset]));
        before.add(operation.length<0?new InsnNode(Opcodes.LCONST_0):new VarInsnNode(Opcodes.LLOAD,slots[operation.length]));
        if(operation.length>=0||invoked.name.startsWith("get")&&!invoked.name.startsWith("getAnd"))before.add(new InsnNode(Opcodes.ACONST_NULL));else{Type value=arguments[arguments.length-1];before.add(new VarInsnNode(value.getOpcode(Opcodes.ILOAD),slots[slots.length-1]));ControlClassWriter.box(before,value);}
        if(invoked.name.equals("copyMemory")||invoked.name.equals("copySwapMemory")){
            before.add(operation.destination<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,slots[0]));
            before.add(new VarInsnNode(Opcodes.LLOAD,slots[operation.destination<0?0:1]));
        }else{before.add(new InsnNode(Opcodes.ACONST_NULL));before.add(new InsnNode(Opcodes.LCONST_0));}
        call(before,"executionUnsafeBefore","(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;JJLjava/lang/Object;Ljava/lang/Object;J)Ljava/lang/Object;",instruction,sites);
        before.add(new VarInsnNode(Opcodes.ASTORE,receipt));before.add(new VarInsnNode(Opcodes.ALOAD,receipt));before.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));before.add(new JumpInsnNode(Opcodes.IF_ACMPNE,admitted));
        if(result.getSort()!=Type.VOID){
            if(result.getSort()==Type.BOOLEAN&&(invoked.name.startsWith("compareAndSet")||invoked.name.startsWith("weakCompareAndSet")||invoked.name.startsWith("compareAndSwap")))before.add(new InsnNode(Opcodes.ICONST_0));
            else{
                before.add(operation.destination<0?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,slots[operation.destination]));before.add(new VarInsnNode(Opcodes.LLOAD,slots[operation.offset]));before.add(new LdcInsnNode(operation.kind));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","retained","(Ljava/lang/Object;JLjava/lang/String;)Ljava/lang/Object;",false));
                if(reference(result))before.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
                else{String boxed="java/lang/"+(operation.kind.equals("Int")?"Integer":operation.kind.equals("Char")?"Character":operation.kind);before.add(new TypeInsnNode(Opcodes.CHECKCAST,boxed));before.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,boxed,result.getClassName()+"Value","()"+result.getDescriptor(),false));}
            }
            before.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
        }
        before.add(new JumpInsnNode(Opcodes.GOTO,denied));before.add(admitted);before.add(new VarInsnNode(Opcodes.ALOAD,accessor));for(int i=0;i<slots.length;i++)before.add(new VarInsnNode(arguments[i].getOpcode(Opcodes.ILOAD),slots[i]));before.add(start);
        after.add(end);if(result.getSort()!=Type.VOID)after.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
        after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new LdcInsnNode(instruction));after.add(new VarInsnNode(Opcodes.ALOAD,receipt));MemoryWriteScope.applied(after,invoked.name,result,returned,arguments,slots);
        call(after,"executionUnsafeAfter","(Ljava/lang/Object;ILjava/lang/Object;Z)V",instruction,sites);after.add(new JumpInsnNode(Opcodes.GOTO,finished));
        after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new LdcInsnNode(instruction));after.add(new VarInsnNode(Opcodes.ALOAD,receipt));after.add(new VarInsnNode(Opcodes.ALOAD,failure));
        call(after,"executionUnsafeFailed","(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Throwable;)V",instruction,sites);after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));
        after.add(denied);after.add(finished);if(result.getSort()!=Type.VOID)after.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));after.add(resume);
        method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void call(InsnList code,String name,String descriptor,int instruction,List<Site> sites){
        LabelNode site=new LabelNode();code.add(site);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,descriptor,false));sites.add(new Site(site,instruction));
    }
    private static InsnList argument(int token,int slot,List<Site> sites){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new LdcInsnNode(slot));code.add(new VarInsnNode(Opcodes.ALOAD,slot));
        call(code,"executionArgument","(Ljava/lang/Object;ILjava/lang/Object;)V",-2-slot,sites);return code;
    }
    private static InsnList reference(int token,int instruction,String name,List<Site> sites){
        InsnList code=new InsnList();code.add(new InsnNode(Opcodes.DUP));code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(Opcodes.SWAP));
        code.add(new LdcInsnNode(instruction));code.add(new InsnNode(Opcodes.SWAP));call(code,name,"(Ljava/lang/Object;ILjava/lang/Object;)V",instruction,sites);return code;
    }
    private static int constructorReceiver(MethodNode method,MethodInsnNode call,InsnList before){
        Type[] types=Type.getArgumentTypes(call.desc);int[] slots=new int[types.length];
        for(int i=types.length-1;i>=0;i--){slots[i]=method.maxLocals;method.maxLocals+=types[i].getSize();before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ISTORE),slots[i]));}
        int receiver=method.maxLocals++;before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,receiver));
        for(int i=0;i<types.length;i++)before.add(new VarInsnNode(types[i].getOpcode(Opcodes.ILOAD),slots[i]));return receiver;
    }
    private static void heap(MethodNode method,AbstractInsnNode at,int token,int instruction,InsnList before,InsnList after,List<Site> sites){
        boolean field=at instanceof FieldInsnNode,write=field?at.getOpcode()==Opcodes.PUTFIELD||at.getOpcode()==Opcodes.PUTSTATIC:at.getOpcode()>=Opcodes.IASTORE;
        boolean statik=field&&(at.getOpcode()==Opcodes.GETSTATIC||at.getOpcode()==Opcodes.PUTSTATIC);
        Type type=field?Type.getType(((FieldInsnNode)at).desc):switch(at.getOpcode()){
            case Opcodes.LALOAD,Opcodes.LASTORE->Type.LONG_TYPE;case Opcodes.DALOAD,Opcodes.DASTORE->Type.DOUBLE_TYPE;case Opcodes.FALOAD,Opcodes.FASTORE->Type.FLOAT_TYPE;
            case Opcodes.AALOAD,Opcodes.AASTORE->Type.getType(Object.class);default->Type.INT_TYPE;
        };
        int value=-1,receiver=-1,index=-1;
        if(write){value=method.maxLocals;method.maxLocals+=type.getSize();before.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE),value));}
        if(!field){index=method.maxLocals++;before.add(new VarInsnNode(Opcodes.ISTORE,index));}
        if(!statik){receiver=method.maxLocals++;before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ASTORE,receiver));}
        before.add(new VarInsnNode(Opcodes.ALOAD,token));before.add(new LdcInsnNode(instruction));
        before.add(field?new LdcInsnNode(Type.getObjectType(((FieldInsnNode)at).owner)):new InsnNode(Opcodes.ACONST_NULL));
        before.add(statik?new InsnNode(Opcodes.ACONST_NULL):new VarInsnNode(Opcodes.ALOAD,receiver));
        before.add(index<0?new InsnNode(Opcodes.ICONST_M1):new VarInsnNode(Opcodes.ILOAD,index));
        call(before,"executionHeapBefore","(Ljava/lang/Object;ILjava/lang/Class;Ljava/lang/Object;I)V",instruction,sites);
        if(index>=0)before.add(new VarInsnNode(Opcodes.ILOAD,index));if(write)before.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD),value));
        if(!write&&reference(type))after.add(reference(token,instruction,"executionHeapAfter",sites));
        else{
            after.add(new VarInsnNode(Opcodes.ALOAD,token));after.add(new LdcInsnNode(instruction));after.add(write&&reference(type)?new VarInsnNode(Opcodes.ALOAD,value):new InsnNode(Opcodes.ACONST_NULL));
            call(after,"executionHeapAfter","(Ljava/lang/Object;ILjava/lang/Object;)V",instruction,sites);
        }
    }
    private static void carry(InsnList instructions,Module[] sources,IdentityHashMap<AbstractInsnNode,Module[]> carried){
        for(AbstractInsnNode at:instructions.toArray())if(at.getOpcode()>=0)carried.put(at,sources);
    }
    private static InsnList hook(String name,int token,int instruction,List<Site> sites){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new LdcInsnNode(instruction));
        LabelNode site=new LabelNode();code.add(site);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,name,"(Ljava/lang/Object;I)V",false));sites.add(new Site(site,instruction));return code;
    }
    private static InsnList exit(int token,boolean normal,int instruction,List<Site> sites){
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new InsnNode(normal?Opcodes.ICONST_1:Opcodes.ICONST_0));
        LabelNode site=new LabelNode();code.add(site);code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"executionExit","(Ljava/lang/Object;Z)V",false));sites.add(new Site(site,instruction));return code;
    }
}
