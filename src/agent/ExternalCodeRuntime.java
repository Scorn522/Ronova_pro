package dev.ronova.pro.agent;

import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Keeps actual definition/image provenance without an execution Plan or shadow operand stack. */
final class ExternalCodeRuntime {
    record Source(byte[] bytes,Module declaration,boolean hidden,ExternalCodeFlow.Image image,
            Map<String,Module[][]> owners,Map<String,Module[][]> direct,Map<String,List<int[]>> lines,
            Map<String,Module[]> fields,Object[][] values,ClassNode draft){
        boolean present(){return !fields.isEmpty()||values.length!=0;}
    }
    private ExternalCodeRuntime(){}
    static Source prepare(Module declaration,ClassLoader loader,String name,Class<?> actual,byte[] bytes){return prepare(declaration,loader,name,actual,bytes,false);}
    static Source prepare(Module declaration,ClassLoader loader,String name,Class<?> actual,byte[] bytes,boolean hidden){return prepare(declaration,loader,name,actual,bytes,hidden,true);}
    static Source prepare(Module declaration,ClassLoader loader,String name,Class<?> actual,byte[] bytes,boolean hidden,boolean candidate){
        if(!candidate||!ExternalCodeImages.recorded(loader,name))
            return new Source(bytes,declaration,hidden,null,Map.of(),Map.of(),Map.of(),Map.of(),new Object[0][],null);
        ExternalCodeImages.ExecutionSources observed=ExternalCodeImages.executionSources(loader,name,bytes,true);
        return new Source(bytes,declaration,hidden,observed.image(),observed.image()==null?Map.of():observed.image().rows(),Map.of(),Map.of(),observed.fields(),observed.values(),null);
    }
    static boolean legacy(MethodNode method){
        for(AbstractInsnNode at:method.instructions.toArray())if(at.getOpcode()==Opcodes.JSR||at.getOpcode()==Opcodes.RET)return true;
        return false;
    }
    static byte[] finish(ClassLoader loader,String name,Class<?> actual,Source source,byte[] bytes){
        CreationBoundary.Weaving creations=source.hidden()?CreationBoundary.defined(loader,name,bytes,true):null;
        byte[] result=bytes;
        if(creations!=null){ClassWriter writer=new ControlClassWriter(loader,creations.node);creations.node.accept(writer);result=writer.toByteArray();}
        Map<String,Module[]> roots=methodRoots(source.owners());
        // One source union per emitted method. This is a whole-Mod root on an
        // actual body, not an instruction permission or an execution receipt.
        Module[][][] contributors=new Module[roots.size()][][];int at=0;
        for(Module[] modules:roots.values())contributors[at++]=new Module[][]{modules};
        RecoveryAgent.publishExternalCode(loader,name,actual,result,roots.keySet().toArray(String[]::new),contributors,
                source.fields(),source.values(),source.hidden(),null,source.declaration());
        RecoveryAgent.publishMethodRoots(loader,name,actual,roots);
        boolean suppressed=ModGroupBoundary.stopped(source.declaration());
        ExternalCodeDefinitions.accepted(loader,name,actual,source.declaration(),result,suppressed?result:source.bytes(),
                Map.of(),suppressed?ExternalCodeImages.acceptedExecution(result,Map.of()):source.image(),Map.of(),
                source.fields(),source.values(),source.hidden(),creations==null?new String[0]:creations.locations(),null);
        return result;
    }
    static Map<String,Module[]> methodRoots(Map<String,Module[][]> methods){
        Map<String,Module[]> roots=new LinkedHashMap<>();
        for(var entry:methods.entrySet()){
            Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Module[] row:entry.getValue())if(row==null)modules.add(null);else Collections.addAll(modules,row);
            if(!modules.isEmpty())roots.put(entry.getKey(),modules.toArray(Module[]::new));
        }
        return roots;
    }
}
