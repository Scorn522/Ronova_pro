package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Propagates observed contributors through the actual transformed method's operands and control edges. */
final class ExternalCodeFlow {
    record ControlChange(int at,int[] alternatives,Module[] owners){}
    private ExternalCodeFlow(){}
    private static Set<Module> set(){return Collections.newSetFromMap(new IdentityHashMap<>());}
    private static Set<Module> frozen(Set<Module> sources){return sources.isEmpty()?Set.of():Collections.unmodifiableSet(new HashSet<>(sources));}
    private static final class OriginValue implements Value {
        final BasicValue value;final Set<Module> sources,guaranteed;
        final BitSet parameters,guaranteedParameters;
        OriginValue(BasicValue value,Set<Module> sources,Set<Module> guaranteed,BitSet parameters,BitSet guaranteedParameters){
            this.value=value;this.sources=frozen(sources);this.guaranteed=frozen(guaranteed);this.parameters=(BitSet)parameters.clone();this.guaranteedParameters=(BitSet)guaranteedParameters.clone();
        }
        public int getSize(){return value.getSize();}
        public boolean equals(Object other){return other instanceof OriginValue origin&&value.equals(origin.value)&&sources.equals(origin.sources)&&guaranteed.equals(origin.guaranteed)&&parameters.equals(origin.parameters)&&guaranteedParameters.equals(origin.guaranteedParameters);}
        public int hashCode(){return (((value.hashCode()*31+sources.hashCode())*31+guaranteed.hashCode())*31+parameters.hashCode())*31+guaranteedParameters.hashCode();}
    }
    private record Summary(Set<Module> modules,BitSet parameters,BitSet guaranteedParameters){}
    private static final Summary EMPTY=new Summary(Set.of(),new BitSet(),new BitSet());
    private record Result(Module[][] rows,Summary returned){}
    private static final class MethodAnalysis {
        final Image image;final MethodNode method;final Module[][] direct;
        final Map<AbstractInsnNode,Summary> calls=new IdentityHashMap<>();
        final List<CallUse> callers=new ArrayList<>();
        Result result;boolean queued;
        MethodAnalysis(Image image,MethodNode method,Module[][] direct){this.image=image;this.method=method;this.direct=direct;}
    }
    private record CallUse(MethodAnalysis caller,AbstractInsnNode instruction){}
    record Image(Object identity,ClassNode node,Map<String,Module[][]> rows,Map<String,List<ControlChange>> comparisons){}
    record MethodTarget(Object identity,String method){}
    interface CallResolver {MethodTarget resolve(Image caller,MethodInsnNode instruction);}
    // This graph propagates normal returned values. The caller still analyzes
    // every instruction and every operand of a void call; its callee has no
    // returned value whose summary could add a dependency to that analysis.
    static boolean returnsValue(MethodInsnNode call){return call.desc.charAt(call.desc.length()-1)!='V';}
    private record MethodKey(Object identity,String method){
        public boolean equals(Object other){return other instanceof MethodKey key&&identity==key.identity&&method.equals(key.method);}
        public int hashCode(){return System.identityHashCode(identity)*31+method.hashCode();}
    }
    private static final class Origins extends Interpreter<OriginValue> {
        final BasicInterpreter types=new BasicInterpreter();
        final IdentityHashMap<AbstractInsnNode,Set<Module>> sources;
        final IdentityHashMap<AbstractInsnNode,Set<Module>> direct,conditions,control,required=new IdentityHashMap<>();
        final Map<AbstractInsnNode,Summary> calls;
        final Map<Integer,Integer> arguments=new HashMap<>();
        final IdentityHashMap<AbstractInsnNode,BitSet> parameterUses=new IdentityHashMap<>(),requiredParameters=new IdentityHashMap<>();
        final IdentityHashMap<AbstractInsnNode,BitSet> parameterControl,guaranteedParameterControl;
        Origins(IdentityHashMap<AbstractInsnNode,Set<Module>> sources,IdentityHashMap<AbstractInsnNode,Set<Module>> direct,
                IdentityHashMap<AbstractInsnNode,Set<Module>> conditions,IdentityHashMap<AbstractInsnNode,Set<Module>> control,Map<AbstractInsnNode,Summary> calls,MethodNode method,
                IdentityHashMap<AbstractInsnNode,BitSet> parameterControl,IdentityHashMap<AbstractInsnNode,BitSet> guaranteedParameterControl){
            super(Opcodes.ASM8);this.sources=sources;this.direct=direct;this.conditions=conditions;this.control=control;this.calls=calls;this.parameterControl=parameterControl;this.guaranteedParameterControl=guaranteedParameterControl;
            int local=0,index=0;if((method.access&Opcodes.ACC_STATIC)==0)arguments.put(local++,index++);
            for(Type type:Type.getArgumentTypes(method.desc)){arguments.put(local,index++);local+=type.getSize();}
        }
        private Set<Module> used(AbstractInsnNode instruction,List<? extends OriginValue> inputs){
            Set<Module> result=set();Set<Module> direct=sources.get(instruction);if(direct!=null)result.addAll(direct);
            for(OriginValue input:inputs)if(input!=null)result.addAll(input.sources);
            parameterUses.computeIfAbsent(instruction,ignored->new BitSet()).or(parameters(instruction,inputs,false));
            sources.computeIfAbsent(instruction,ignored->set()).addAll(result);return result;
        }
        private OriginValue produced(AbstractInsnNode instruction,BasicValue value,List<? extends OriginValue> inputs){
            Set<Module> origins=used(instruction,inputs),guaranteed=guaranteed(instruction,inputs);required.put(instruction,guaranteed);
            BitSet parameters=parameters(instruction,inputs,false),requiredParams=parameters(instruction,inputs,true);requiredParameters.put(instruction,requiredParams);
            return value==null?null:new OriginValue(value,origins,guaranteed,parameters,requiredParams);
        }
        private Set<Module> guaranteed(AbstractInsnNode instruction,List<? extends OriginValue> inputs){
            Set<Module> result=set();result.addAll(direct.getOrDefault(instruction,Set.of()));result.addAll(control.getOrDefault(instruction,Set.of()));
            for(OriginValue input:inputs)if(input!=null)result.addAll(input.guaranteed);return result;
        }
        private BitSet parameters(AbstractInsnNode instruction,List<? extends OriginValue> inputs,boolean guaranteed){
            BitSet result=new BitSet();BitSet control=(guaranteed?guaranteedParameterControl:parameterControl).get(instruction);if(control!=null)result.or(control);
            for(OriginValue input:inputs)if(input!=null)result.or(guaranteed?input.guaranteedParameters:input.parameters);return result;
        }
        public OriginValue newValue(Type type){BasicValue value=types.newValue(type);return value==null?null:new OriginValue(value,Set.of(),Set.of(),new BitSet(),new BitSet());}
        public OriginValue newParameterValue(boolean instance,int local,Type type){
            BasicValue value=types.newParameterValue(instance,local,type);if(value==null)return null;BitSet parameter=new BitSet();Integer index=arguments.get(local);if(index!=null)parameter.set(index);
            return new OriginValue(value,Set.of(),Set.of(),parameter,parameter);
        }
        public OriginValue newOperation(AbstractInsnNode instruction)throws AnalyzerException{return produced(instruction,types.newOperation(instruction),List.of());}
        public OriginValue copyOperation(AbstractInsnNode instruction,OriginValue value)throws AnalyzerException{return produced(instruction,types.copyOperation(instruction,value.value),List.of(value));}
        public OriginValue unaryOperation(AbstractInsnNode instruction,OriginValue value)throws AnalyzerException{return produced(instruction,types.unaryOperation(instruction,value.value),List.of(value));}
        public OriginValue binaryOperation(AbstractInsnNode instruction,OriginValue left,OriginValue right)throws AnalyzerException{return produced(instruction,types.binaryOperation(instruction,left.value,right.value),List.of(left,right));}
        public OriginValue ternaryOperation(AbstractInsnNode instruction,OriginValue first,OriginValue second,OriginValue third)throws AnalyzerException{return produced(instruction,types.ternaryOperation(instruction,first.value,second.value,third.value),List.of(first,second,third));}
        public OriginValue naryOperation(AbstractInsnNode instruction,List<? extends OriginValue> values)throws AnalyzerException{
            List<BasicValue> typesOnly=new ArrayList<>(values.size());for(OriginValue value:values)typesOnly.add(value.value);
            BasicValue value=types.naryOperation(instruction,typesOnly);Set<Module> origins=used(instruction,values),guaranteed=guaranteed(instruction,List.of());
            BitSet parameters=parameters(instruction,values,false),requiredParams=parameters(instruction,List.of(),true);Summary returned=calls.get(instruction);
            if(returned!=null){
                origins=set();origins.addAll(direct.getOrDefault(instruction,Set.of()));origins.addAll(conditions.getOrDefault(instruction,Set.of()));origins.addAll(returned.modules());guaranteed.addAll(returned.modules());parameters=parameters(instruction,List.of(),false);
                for(int argument=returned.parameters().nextSetBit(0);argument>=0;argument=returned.parameters().nextSetBit(argument+1))if(argument<values.size()){
                    OriginValue input=values.get(argument);origins.addAll(input.sources);parameters.or(input.parameters);
                }
                for(int argument=returned.guaranteedParameters().nextSetBit(0);argument>=0;argument=returned.guaranteedParameters().nextSetBit(argument+1))if(argument<values.size()){
                    OriginValue input=values.get(argument);guaranteed.addAll(input.guaranteed);requiredParams.or(input.guaranteedParameters);
                }
            }else if(!(instruction instanceof MethodInsnNode)&&!(instruction instanceof InvokeDynamicInsnNode)){
                guaranteed=guaranteed(instruction,values);requiredParams=parameters(instruction,values,true);
            }
            required.put(instruction,guaranteed);requiredParameters.put(instruction,requiredParams);return value==null?null:new OriginValue(value,origins,guaranteed,parameters,requiredParams);
        }
        public void returnOperation(AbstractInsnNode instruction,OriginValue value,OriginValue expected)throws AnalyzerException{
            types.returnOperation(instruction,value.value,expected.value);used(instruction,List.of(value));
            required.put(instruction,guaranteed(instruction,List.of(value)));
            requiredParameters.put(instruction,parameters(instruction,List.of(value),true));
        }
        public OriginValue merge(OriginValue first,OriginValue second){
            if(first==second||first.equals(second))return first;
            // Frozen value sets can serve an unchanged union/intersection directly.
            Set<Module> result;
            if(first.sources.containsAll(second.sources))result=first.sources;
            else if(second.sources.containsAll(first.sources))result=second.sources;
            else {result=set();result.addAll(first.sources);result.addAll(second.sources);}
            Set<Module> guaranteed;
            if(second.guaranteed.containsAll(first.guaranteed))guaranteed=first.guaranteed;
            else if(first.guaranteed.containsAll(second.guaranteed))guaranteed=second.guaranteed;
            else {guaranteed=set();guaranteed.addAll(first.guaranteed);guaranteed.retainAll(second.guaranteed);}
            BitSet parameters=(BitSet)first.parameters.clone();parameters.or(second.parameters);BitSet required=(BitSet)first.guaranteedParameters.clone();required.and(second.guaranteedParameters);
            BasicValue value=types.merge(first.value,second.value);
            if(first.value.equals(value)&&first.sources.equals(result)&&first.guaranteed.equals(guaranteed)
                    &&first.parameters.equals(parameters)&&first.guaranteedParameters.equals(required))return first;
            if(second.value.equals(value)&&second.sources.equals(result)&&second.guaranteed.equals(guaranteed)
                    &&second.parameters.equals(parameters)&&second.guaranteedParameters.equals(required))return second;
            return new OriginValue(value,result,guaranteed,parameters,required);
        }
    }
    static Module[][] expand(String owner,MethodNode method,Module[][] rows,List<ControlChange> comparisons){
        return analyze(owner,method,rows,comparisons,Map.of()).rows();
    }
    /** A changed or removed edge contributes to the region it changed even when no predicate remains to observe. */
    static Map<String,Module[][]> runtimeDirect(Image image){
        Map<String,Module[][]> result=new LinkedHashMap<>();
        for(MethodNode method:image.node().methods){
            String selector=method.name+method.desc;Module[][] supplied=image.rows().get(selector);if(supplied==null)continue;
            Module[][] rows=new Module[supplied.length][];for(int i=0;i<rows.length;i++)rows[i]=supplied[i].clone();result.put(selector,rows);
            List<ControlChange> changes=image.comparisons().getOrDefault(selector,List.of());if(changes.isEmpty())continue;
            AbstractInsnNode[] code=method.instructions.toArray();List<Integer> positions=new ArrayList<>();int[] ordinals=new int[code.length];Arrays.fill(ordinals,-1);
            for(int i=0;i<code.length;i++)if(code[i].getOpcode()>=0){ordinals[i]=positions.size();positions.add(i);}
            List<Set<Integer>> edges=new ArrayList<>();for(int i=0;i<=code.length;i++)edges.add(new HashSet<>());
            for(ControlChange change:changes)if(change.at()>=0&&change.at()<positions.size())
                for(int target:change.alternatives())edges.get(positions.get(change.at())).add(target<0||target>=positions.size()?code.length:positions.get(target));
            try{
                Analyzer<BasicValue> analyzer=new Analyzer<>(new BasicInterpreter()){
                    @Override protected void newControlFlowEdge(int from,int to){edges.get(from).add(to);}
                    @Override protected boolean newControlFlowExceptionEdge(int from,int to){edges.get(from).add(to);return true;}
                };
                Frame<BasicValue>[] frames=analyzer.analyze(image.node().name,method);
                for(int i=0;i<code.length;i++)if(frames[i]!=null&&(edges.get(i).isEmpty()||code[i].getOpcode()==Opcodes.ATHROW))edges.get(i).add(code.length);
                BitSet[] postdominators=postdominators(edges,frames);
                for(ControlChange change:changes){
                    if(change.at()<0||change.at()>=positions.size())continue;int branch=positions.get(change.at());if(frames[branch]==null)continue;
                    BitSet visited=new BitSet(code.length+1);ArrayDeque<Integer> pending=new ArrayDeque<>(edges.get(branch));
                    while(!pending.isEmpty()){
                        int at=pending.removeFirst();if(at==code.length||visited.get(at)||postdominators[branch].get(at))continue;visited.set(at);
                        if(frames[at]!=null&&ordinals[at]>=0){
                            Set<Module> sources=set();Collections.addAll(sources,rows[ordinals[at]]);Collections.addAll(sources,change.owners());rows[ordinals[at]]=sources.toArray(Module[]::new);
                        }
                        pending.addAll(edges.get(at));
                    }
                }
            }catch(AnalyzerException failure){
                for(ControlChange change:changes)for(Module source:change.owners())ModGroupBoundary.externalGap(source,image.node().name+"."+selector+":EXTERNAL_EXECUTION_CONTROL_UNOBSERVED");
            }
        }
        return Map.copyOf(result);
    }
    /** Follow only calls resolved by the same recorded class image; summaries describe normal returned values. */
    static Map<String,Module[][]> expandClass(ClassNode owner,Map<String,Module[][]> rows,Map<String,List<ControlChange>> comparisons){
        Image root=new Image(owner,owner,rows,comparisons);
        return expandGraph(root,List.of(root),(caller,call)->{
            if(!call.owner.equals(owner.name))return null;
            MethodNode target=owner.methods.stream().filter(candidate->candidate.name.equals(call.name)&&candidate.desc.equals(call.desc)).findFirst().orElse(null);
            boolean exact=call.getOpcode()==Opcodes.INVOKESTATIC||call.getOpcode()==Opcodes.INVOKESPECIAL
                    ||target!=null&&((target.access&(Opcodes.ACC_FINAL|Opcodes.ACC_PRIVATE))!=0||(owner.access&Opcodes.ACC_FINAL)!=0);
            return exact&&target!=null&&(target.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0?new MethodTarget(owner,call.name+call.desc):null;
        });
    }
    /** The resolver supplies actual definition identities; textual method selectors never merge definitions. */
    static Map<String,Module[][]> expandGraph(Image root,List<Image> images,CallResolver resolver){
        Map<Object,Image> definitions=new IdentityHashMap<>();Map<MethodKey,MethodNode> methods=new HashMap<>();
        for(Image image:images){
            if(definitions.put(image.identity(),image)!=null)throw new IllegalStateException("EXTERNAL_FLOW_DEFINITION_IDENTITY_REUSED");
            for(MethodNode method:image.node().methods)methods.put(new MethodKey(image.identity(),method.name+method.desc),method);
        }
        if(definitions.get(root.identity())!=root)throw new IllegalStateException("EXTERNAL_FLOW_ROOT_IMAGE_UNOBSERVED");
        // With no Module seed anywhere in the resolved graph, operand/parameter
        // propagation cannot produce one. Still validate every recorded layout.
        if(!hasContributors(images))return Map.of();
        Map<MethodKey,MethodAnalysis> analyzed=new LinkedHashMap<>();
        for(Image image:images)for(MethodNode method:image.node().methods){
            String key=method.name+method.desc;Module[][] direct=image.rows().get(key);
            if(direct!=null)analyzed.put(new MethodKey(image.identity(),key),new MethodAnalysis(image,method,direct));
        }
        ArrayDeque<MethodAnalysis> pending=new ArrayDeque<>();
        for(MethodAnalysis caller:analyzed.values()){
            for(AbstractInsnNode instruction:caller.method.instructions.toArray())if(instruction instanceof MethodInsnNode call&&returnsValue(call)){
                MethodTarget target=resolver.resolve(caller.image,call);if(target==null)continue;
                MethodKey selected=new MethodKey(target.identity(),target.method());MethodNode implementation=methods.get(selected);
                if(implementation==null||(implementation.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
                caller.calls.put(instruction,EMPTY);MethodAnalysis callee=analyzed.get(selected);
                if(callee!=null)callee.callers.add(new CallUse(caller,instruction));
            }
            caller.queued=true;pending.addLast(caller);
        }
        // Resolve each edge once for this actual graph. A new returned summary
        // wakes only its callers, including recursive callers; it never causes
        // an unrelated method to rebuild all of its call maps and frames.
        while(!pending.isEmpty()){
            MethodAnalysis current=pending.removeFirst();current.queued=false;
            String key=current.method.name+current.method.desc;
            Result next=analyze(current.image.node().name,current.method,current.direct,current.image.comparisons().getOrDefault(key,List.of()),current.calls);
            Summary before=current.result==null?EMPTY:current.result.returned();current.result=next;
            if(before.equals(next.returned()))continue;
            for(CallUse use:current.callers){
                MethodAnalysis caller=use.caller();caller.calls.put(use.instruction(),next.returned());
                if(!caller.queued){caller.queued=true;pending.addLast(caller);}
            }
        }
        Map<String,Module[][]> expanded=new LinkedHashMap<>();
        for(MethodAnalysis current:analyzed.values())if(current.image==root){
            Module[][] rows=current.result.rows();if(Arrays.stream(rows).anyMatch(row->row.length!=0))expanded.put(current.method.name+current.method.desc,rows);
        }
        return Map.copyOf(expanded);
    }
    private static boolean hasContributors(List<Image> images){
        boolean present=false;
        for(Image image:images)present|=hasContributors(image);
        return present;
    }
    static boolean hasContributors(Image image){
        boolean present=false;
        for(MethodNode method:image.node().methods){
            String key=method.name+method.desc;Module[][] rows=image.rows().get(key);if(rows==null)continue;
            int ordinal=0;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=0){
                if(ordinal>=rows.length)throw new IllegalStateException("EXTERNAL_FLOW_INSTRUCTION_LAYOUT_CHANGED");
                if(rows[ordinal++].length!=0)present=true;
            }
            if(ordinal!=rows.length)throw new IllegalStateException("EXTERNAL_FLOW_INSTRUCTION_LAYOUT_CHANGED");
            for(ControlChange change:image.comparisons().getOrDefault(key,List.of()))if(change.owners().length!=0)present=true;
        }
        return present;
    }
    private static Result analyze(String owner,MethodNode method,Module[][] rows,List<ControlChange> comparisons,Map<AbstractInsnNode,Summary> calls){
        AbstractInsnNode[] code=method.instructions.toArray();IdentityHashMap<AbstractInsnNode,Set<Module>> sources=new IdentityHashMap<>();int ordinal=0;
        IdentityHashMap<AbstractInsnNode,Set<Module>> direct=new IdentityHashMap<>(),conditions=new IdentityHashMap<>(),control=new IdentityHashMap<>();
        IdentityHashMap<AbstractInsnNode,BitSet> parameterControl=new IdentityHashMap<>(),guaranteedParameterControl=new IdentityHashMap<>();
        List<Integer> positions=new ArrayList<>();
        for(AbstractInsnNode instruction:code)if(instruction.getOpcode()>=0){
            if(ordinal>=rows.length)throw new IllegalStateException("EXTERNAL_FLOW_INSTRUCTION_LAYOUT_CHANGED");
            Set<Module> input=set();input.addAll(Arrays.asList(rows[ordinal++]));sources.put(instruction,input);positions.add(method.instructions.indexOf(instruction));
        }
        if(ordinal!=rows.length)throw new IllegalStateException("EXTERNAL_FLOW_INSTRUCTION_LAYOUT_CHANGED");
        for(ControlChange change:comparisons)if(change.at()>=0&&change.at()<positions.size())sources.get(code[positions.get(change.at())]).addAll(Arrays.asList(change.owners()));
        for(var entry:sources.entrySet()){Set<Module> copy=set();copy.addAll(entry.getValue());direct.put(entry.getKey(),copy);}
        if(code.length==0)return new Result(rows,EMPTY);
        List<Set<Integer>> edges=new ArrayList<>(code.length+1);for(int i=0;i<=code.length;i++)edges.add(new HashSet<>());
        for(ControlChange change:comparisons)if(change.at()>=0&&change.at()<positions.size())for(int target:change.alternatives())edges.get(positions.get(change.at())).add(target<0||target>=positions.size()?code.length:positions.get(target));
        Set<Module> returned=null;BitSet parameters=new BitSet(),requiredParameters=null;
        try{
            boolean changed;
            do{
                int before=size(sources)+size(conditions)+size(control)+bitSize(parameterControl)+bitSize(guaranteedParameterControl);
                Origins interpreter=new Origins(sources,direct,conditions,control,calls,method,parameterControl,guaranteedParameterControl);
                Analyzer<OriginValue> analyzer=new Analyzer<>(interpreter){
                    protected void newControlFlowEdge(int from,int to){edges.get(from).add(to);}
                    protected boolean newControlFlowExceptionEdge(int from,int to){edges.get(from).add(to);return true;}
                };
                Frame<OriginValue>[] frames=analyzer.analyze(owner,method);
                returned=null;parameters=new BitSet();requiredParameters=null;
                for(int i=0;i<code.length;i++)if(frames[i]!=null&&code[i].getOpcode()>=Opcodes.IRETURN&&code[i].getOpcode()<=Opcodes.ARETURN){
                    Set<Module> origins=interpreter.required.getOrDefault(code[i],Set.of());
                    if(returned==null){returned=set();returned.addAll(origins);}else returned.retainAll(origins);
                    parameters.or(interpreter.parameterUses.getOrDefault(code[i],new BitSet()));BitSet required=interpreter.requiredParameters.getOrDefault(code[i],new BitSet());
                    if(requiredParameters==null)requiredParameters=(BitSet)required.clone();else requiredParameters.and(required);
                }
                int exit=code.length;
                for(int i=0;i<code.length;i++)if(frames[i]!=null&&(edges.get(i).isEmpty()||code[i].getOpcode()==Opcodes.ATHROW))edges.get(i).add(exit);
                BitSet[] postdominators=postdominators(edges,frames);
                for(int branch=0;branch<code.length;branch++){
                    if(frames[branch]==null||edges.get(branch).size()<2)continue;
                    Set<Module> condition=sources.getOrDefault(code[branch],Set.of());BitSet parameterCondition=interpreter.parameterUses.getOrDefault(code[branch],new BitSet());if(condition.isEmpty()&&parameterCondition.isEmpty())continue;
                    BitSet visited=new BitSet(code.length+1);ArrayDeque<Integer> pending=new ArrayDeque<>(edges.get(branch));
                    while(!pending.isEmpty()){
                        int at=pending.removeFirst();if(at==exit||visited.get(at)||postdominators[branch].get(at))continue;visited.set(at);
                        if(code[at].getOpcode()>=0){
                            sources.computeIfAbsent(code[at],ignored->set()).addAll(condition);
                            conditions.computeIfAbsent(code[at],ignored->set()).addAll(condition);
                            control.computeIfAbsent(code[at],ignored->set()).addAll(interpreter.required.getOrDefault(code[branch],Set.of()));
                            parameterControl.computeIfAbsent(code[at],ignored->new BitSet()).or(parameterCondition);
                            guaranteedParameterControl.computeIfAbsent(code[at],ignored->new BitSet()).or(interpreter.requiredParameters.getOrDefault(code[branch],new BitSet()));
                        }
                        pending.addAll(edges.get(at));
                    }
                }
                changed=size(sources)+size(conditions)+size(control)+bitSize(parameterControl)+bitSize(guaranteedParameterControl)!=before;
            }while(changed);
        }catch(AnalyzerException|RuntimeException unavailable){
            Set<Module> affected=set();for(Module[] row:rows)affected.addAll(Arrays.asList(row));
            for(Summary summary:calls.values())affected.addAll(summary.modules());
            ExternalCodeImages.failed(affected.toArray(Module[]::new),owner+'#'+method.name+method.desc+":EXTERNAL_OPERAND_OR_CONTROL_LAYOUT_UNAVAILABLE");return new Result(rows,EMPTY);
        }
        Module[][] expanded=new Module[rows.length][];ordinal=0;
        for(AbstractInsnNode instruction:code)if(instruction.getOpcode()>=0)expanded[ordinal++]=sources.getOrDefault(instruction,Set.of()).toArray(Module[]::new);
        return new Result(expanded,new Summary(returned==null?Set.of():frozen(returned),parameters,requiredParameters==null?new BitSet():requiredParameters));
    }
    private static int size(IdentityHashMap<AbstractInsnNode,Set<Module>> sources){int result=0;for(Set<Module> row:sources.values())result+=row.size();return result;}
    private static int bitSize(IdentityHashMap<AbstractInsnNode,BitSet> sources){int result=0;for(BitSet row:sources.values())result+=row.cardinality();return result;}
    static BitSet[] postdominators(List<Set<Integer>> edges,Frame<?>[] frames){
        int exit=edges.size()-1;BitSet live=new BitSet(exit+1);live.set(exit);for(int i=0;i<exit;i++)if(frames[i]!=null)live.set(i);
        // A closed loop has no ordinary return edge. Include its nontermination in the control boundary.
        BitSet reachesExit=new BitSet(exit+1);reachesExit.set(exit);boolean changed;
        do{changed=false;for(int from=0;from<exit;from++)if(live.get(from)&&!reachesExit.get(from)&&edges.get(from).stream().anyMatch(reachesExit::get)){reachesExit.set(from);changed=true;}}while(changed);
        List<Set<Integer>> successors=new ArrayList<>(edges.size());for(int i=0;i<=exit;i++){Set<Integer> next=new HashSet<>(edges.get(i));if(live.get(i)&&!reachesExit.get(i))next.add(exit);successors.add(next);}
        BitSet[] result=new BitSet[exit+1];for(int i=0;i<=exit;i++){result[i]=(BitSet)live.clone();if(i==exit||!live.get(i)){result[i].clear();result[i].set(i);}}
        do{
            changed=false;
            for(int from=exit-1;from>=0;from--)if(live.get(from)){
                BitSet next=(BitSet)live.clone();for(int to:successors.get(from))next.and(result[to]);next.set(from);
                if(!next.equals(result[from])){result[from]=next;changed=true;}
            }
        }while(changed);
        return result;
    }
}
