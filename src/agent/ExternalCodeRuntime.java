package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.JSRInlinerAdapter;
import jdk.internal.org.objectweb.asm.tree.*;

/** Carries the actual instruction owner through our write/creation/task weaving. */
final class ExternalCodeRuntime {
    record Source(byte[] bytes,Module declaration,boolean hidden,ExternalCodeFlow.Image image,Map<String,Module[][]> owners,Map<String,Module[][]> direct,Map<String,List<int[]>> lines,Map<String,Module[]> fields,Object[][] values) {
        boolean present(){return !owners.isEmpty()||!fields.isEmpty()||values.length!=0;}
    }
    private ExternalCodeRuntime(){}
    static Source prepare(Module declaration,ClassLoader loader,String name,Class<?> actual,byte[] bytes){
        return prepare(declaration,loader,name,actual,bytes,false);
    }
    static Source prepare(Module declaration,ClassLoader loader,String name,Class<?> actual,byte[] bytes,boolean hidden){
        ExternalCodeImages.ExecutionSources observed=ExternalCodeImages.executionSources(loader,name,bytes);
        ExternalCodeFlow.Image image=observed.image();
        Map<String,Module[][]> owners=ExternalCodeDefinitions.expand(loader,actual,image);
        Map<String,Module[]> fields=observed.fields();
        Object[][] values=observed.values();
        boolean execution=RecoveryAgent.producerModule(declaration)||!owners.isEmpty()||!fields.isEmpty()||values.length!=0;
        Map<String,Module[][]> direct=execution&&image!=null?ExternalCodeFlow.runtimeDirect(image):Map.of();
        ClassReader reader=new ClassReader(bytes);
        if(owners.isEmpty()&&direct.isEmpty()&&reader.readUnsignedShort(6)>Opcodes.V1_6)
            return new Source(bytes,declaration,hidden,image,Map.of(),Map.of(),Map.of(),fields,values);
        ClassNode node=new ClassNode();reader.accept(node,0);Map<String,List<int[]>> sourceLines=new LinkedHashMap<>();
        for(MethodNode method:node.methods){
            String key=method.name+method.desc;Module[][] source=owners.get(key);if(source==null)source=direct.get(key);if(source==null)continue;
            List<int[]> lines=new ArrayList<>();int ordinal=0;
            for(AbstractInsnNode at:method.instructions.toArray()){
                if(at instanceof LineNumberNode line){lines.add(new int[]{ordinal,line.line});method.instructions.remove(at);}
                else if(at.getOpcode()>=0){
                    if(ordinal>=source.length)throw new IllegalStateException("EXTERNAL_SOURCE_INSTRUCTION_LAYOUT_CHANGED:"+key);
                    LabelNode label=new LabelNode();InsnList mark=new InsnList();mark.add(label);mark.add(new LineNumberNode(++ordinal,label));method.instructions.insertBefore(at,mark);
                }
            }
            if(ordinal!=source.length)throw new IllegalStateException("EXTERNAL_SOURCE_INSTRUCTION_LAYOUT_CHANGED:"+key);sourceLines.put(key,List.copyOf(lines));
        }
        // Mark the original opcodes first. The inliner copies those marks into every
        // invocation, including the replacement instructions for JSR and RET.
        boolean inlined=false;
        for(int i=0;i<node.methods.size();i++){
            MethodNode method=node.methods.get(i);if(!legacy(method))continue;
            String[] exceptions=method.exceptions.toArray(String[]::new);
            MethodNode expanded=new MethodNode(Opcodes.ASM8,method.access,method.name,method.desc,method.signature,exceptions);
            method.accept(new JSRInlinerAdapter(expanded,method.access,method.name,method.desc,method.signature,exceptions));
            // Frames described return-address values and the old control flow.
            // Legacy class versions permit their omission; later weaving recomputes them.
            for(AbstractInsnNode at:expanded.instructions.toArray())if(at instanceof FrameNode)expanded.instructions.remove(at);
            node.methods.set(i,expanded);inlined=true;
        }
        if(sourceLines.isEmpty()&&!inlined)return new Source(bytes,declaration,hidden,image,owners,direct,Map.of(),fields,values);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return new Source(writer.toByteArray(),declaration,hidden,image,owners,direct,Map.copyOf(sourceLines),fields,values);
    }
    static boolean legacy(MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.JSR||at.getOpcode()==Opcodes.RET)return true;
        return false;
    }
    static byte[] finish(ClassLoader loader,String name,Class<?> actual,Source source,byte[] bytes){
        CreationBoundary.Weaving creations=source.hidden()?CreationBoundary.defined(loader,name,bytes,true):null;
        if(!source.present()&&source.direct().isEmpty()&&creations==null){
            if(source.image()!=null){
                RecoveryAgent.publishExternalDefinition(loader,name,bytes,source.hidden());
                accepted(loader,name,actual,source,bytes,Map.of(),new String[0],null);
            }
            return bytes;
        }
        ClassNode node;if(creations!=null)node=creations.node;else{node=new ClassNode();new ClassReader(bytes).accept(node,0);}
        CreationBoundary.Weaving recorded=creations==null?CreationBoundary.recorded(bytes,node):creations;
        Map<String,Module[][]> observed=new LinkedHashMap<>(),direct=new LinkedHashMap<>();
        for(MethodNode method:node.methods){
            String key=method.name+method.desc;Module[][] owners=source.owners().get(key),raw=source.direct().get(key);if(owners==null&&raw==null)continue;
            List<Module[]> expanded=new ArrayList<>(),rawRows=new ArrayList<>();int ordinal=0;Map<Integer,List<LabelNode>> positions=new HashMap<>();
            for(AbstractInsnNode at:method.instructions.toArray()){
                if(at instanceof LineNumberNode line){ordinal=line.line;positions.computeIfAbsent(ordinal-1,ignored->new ArrayList<>()).add(line.start);method.instructions.remove(at);}
                else if(at.getOpcode()>=0){
                    expanded.add(owners!=null&&ordinal>0&&ordinal<=owners.length?owners[ordinal-1]:new Module[0]);
                    rawRows.add(raw!=null&&ordinal>0&&ordinal<=raw.length?raw[ordinal-1]:new Module[0]);
                }
            }
            for(int[] line:source.lines().getOrDefault(key,List.of())){
                for(LabelNode location:positions.getOrDefault(line[0],List.of()))method.instructions.insert(location,new LineNumberNode(line[1],location));
            }
            observed.put(key,expanded.toArray(Module[][]::new));if(raw!=null)direct.put(key,rawRows.toArray(Module[][]::new));
        }
        ExecutionBoundary.Weaving execution=ExecutionBoundary.weave(node,direct,observed,source.declaration());
        if(recorded!=null)recorded.relocate(execution.actual);
        ClassWriter writer=creations==null&&!execution.present()?new ClassWriter(0):new ControlClassWriter(loader,node);node.accept(writer);byte[] result=writer.toByteArray();
        Object plan=execution.present()?RecoveryAgent.externalExecutionPlan(execution.layout()):null;
        Map<String,Module[][]> rows=new LinkedHashMap<>();for(var entry:execution.owners.entrySet())if(Arrays.stream(entry.getValue()).anyMatch(row->row.length!=0))rows.put(entry.getKey(),entry.getValue());
        RecoveryAgent.publishExternalCode(loader,name,actual,result,rows.keySet().toArray(String[]::new),rows.values().toArray(Module[][][]::new),source.fields(),source.values(),source.hidden(),plan,source.declaration());
        if(!source.hidden())CreationBoundary.finalSites(loader,name,actual,result,recorded);
        accepted(loader,name,actual,source,result,rows,creations==null?new String[0]:creations.locations(),plan);return result;
    }
    private static void accepted(ClassLoader loader,String name,Class<?> actual,Source source,byte[] result,Map<String,Module[][]> rows,String[] creationSites,Object execution){
        // Suppression replaced the original business body; it cannot retain that body's returns.
        boolean suppressed=ModGroupBoundary.stopped(source.declaration());
        ExternalCodeFlow.Image semantic=suppressed?ExternalCodeImages.acceptedExecution(result,rows):source.image();
        ExternalCodeDefinitions.accepted(loader,name,actual,source.declaration(),result,rows,semantic,suppressed?rows:source.owners(),source.fields(),source.values(),source.hidden(),creationSites,execution);
    }
}
