package dev.ronova.pro.agent;

import java.util.ArrayList;
import java.util.List;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** JDK 17 resolved-node deletion and value stores, live entries, and default Map mutation paths. */
final class HashMapBoundary {
    private static final String HASH_MAP="java/util/HashMap";
    private static final String NODE="java/util/HashMap$Node";
    private static volatile String nodeStatus="NOT_SEEN";
    private static volatile String defaultStatus="NOT_SEEN";
    private static volatile boolean sourceMaps,sourceNodes;
    private static final String REMOVE_NODE_DESC="(ILjava/lang/Object;Ljava/lang/Object;ZZ)Ljava/util/HashMap$Node;";
    private static final String ALLOW_DESC="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";
    private static final String CLEAR_DESC="(Ljava/lang/Object;)Z";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static volatile String status="NOT_SEEN";
    private static volatile boolean installed;

    private HashMapBoundary() { }

    static String status() { return status; }
    static String state() { return status; }
    static boolean installed() { return installed; }
    static String nodeState() { return nodeStatus; }
    static String defaultState() { return defaultStatus; }
    static boolean sourceInstalled() { return installed&&sourceMaps&&sourceNodes; }

    static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(!HASH_MAP.equals(name)&&!NODE.equals(name)&&!"java/util/Map".equals(name)||bytes==null)return null;
        if(loader!=null)return unsupported("NOT_BOOTSTRAP_LOADER");
        try {
            ClassNode node=new ClassNode();new jdk.internal.org.objectweb.asm.ClassReader(bytes).accept(node,0);
            if("java/util/Map".equals(name)) {
                int sites=IndexValueBoundary.defaultStores(node);
                if(node.version!=Opcodes.V17||sites!=11) {defaultStatus="UNSUPPORTED_DEFAULT_LAYOUT:"+sites;return null;}
                var writer=new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
                node.accept(writer);defaultStatus="TRANSFORMED_DEFAULT_MAP_MUTATIONS";return writer.toByteArray();
            }
            if(NODE.equals(name)) {
                if(node.version!=Opcodes.V17||IndexValueBoundary.hashStores(node)!=1) {
                    nodeStatus="UNSUPPORTED_NODE_LAYOUT";return null;
                }
                sourceNodes=SourceMapBoundary.weave(node)==1;
                if(!sourceNodes)throw new IllegalStateException("SOURCE_NODE_SCOPE_LAYOUT");
                var writer=new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
                node.accept(writer);nodeStatus="TRANSFORMED_NODE_SET_VALUE";return writer.toByteArray();
            }
            if(!HASH_MAP.equals(node.name)||node.version!=Opcodes.V17)
                return unsupported("CLASS_IDENTITY_OR_VERSION:"+node.name+":"+node.version);
            var removers=node.methods.stream().filter(m->m.name.equals("removeNode")&&m.desc.equals(REMOVE_NODE_DESC)).toList();
            var clears=node.methods.stream().filter(m->m.name.equals("clear")&&m.desc.equals("()V")).toList();
            if(removers.size()!=1||clears.size()!=1)return unsupported("JDK17_METHOD_LAYOUT:"+removers.size()+":"+clears.size());
            MethodNode remove=removers.get(0),clear=clears.get(0);
            if((remove.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0
                    ||(remove.access&Opcodes.ACC_FINAL)==0
                    ||(clear.access&(Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)
                return unsupported("JDK17_METHOD_ACCESS");
            boolean removeGuarded=hasCall(remove,"indexRemovalAllowed");
            boolean clearGuarded=hasCall(clear,"clearProtectedMap");
            if(removeGuarded&&clearGuarded&&node.methods.stream().anyMatch(m->hasCall(m,"indexWriteAllowed"))) {
                installed=true;status="ALREADY_TRANSFORMED";return null;
            }
            if(!removeGuarded)weaveRemoveNode(node,remove);
            if(!clearGuarded)weaveClear(node,clear);
            if(IndexValueBoundary.hashStores(node)!=8)throw new IllegalStateException("HASHMAP_VALUE_STORE_LAYOUT");
            sourceMaps=SourceMapBoundary.weave(node)==12;
            if(!sourceMaps)throw new IllegalStateException("SOURCE_MAP_SCOPE_LAYOUT");
            var writer=new jdk.internal.org.objectweb.asm.ClassWriter(jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
                    |jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            installed=true;status="TRANSFORMED_JDK17_HASHMAP_RESOLVED_NODE_CLEAR_VALUE";
            return writer.toByteArray();
        } catch(Throwable unavailable) {
            return unsupported(unavailable.getClass().getSimpleName()+":"+String.valueOf(unavailable.getMessage()));
        }
    }

    private static void weaveRemoveNode(ClassNode owner,MethodNode method) {
        List<AbstractInsnNode> resolvedEntrySites=new ArrayList<>();
        for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof TypeInsnNode type&&type.getOpcode()==Opcodes.INSTANCEOF
                    &&type.desc.equals("java/util/HashMap$TreeNode")) {
                AbstractInsnNode prior=previousCode(instruction);
                if(prior instanceof VarInsnNode load&&load.getOpcode()==Opcodes.ALOAD&&load.var==10)
                    resolvedEntrySites.add(instruction);
            }
        if(resolvedEntrySites.size()!=1)throw new IllegalStateException("JDK17_RESOLVED_NODE_SITE:"+resolvedEntrySites.size());
        // At this point local 10 is the exact matched Node and optional value-match testing has succeeded.
        // Read its actual key/value before any unlinking; never re-run equals() or look it up by key.
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList init=new InsnList();init.add(new InsnNode(Opcodes.ACONST_NULL));
        init.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insert(init);
        AbstractInsnNode[] original=method.instructions.toArray();
        InsnList guard=new InsnList();LabelNode proceed=new LabelNode();
        guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(new VarInsnNode(Opcodes.ALOAD,10));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"beginIndexNodeRemoval",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        guard.add(new VarInsnNode(Opcodes.ASTORE,token));
        guard.add(new VarInsnNode(Opcodes.ALOAD,token));
        guard.add(new FieldInsnNode(Opcodes.GETSTATIC,"java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
        guard.add(new JumpInsnNode(Opcodes.IF_ACMPNE,proceed));
        guard.add(new InsnNode(Opcodes.ACONST_NULL));guard.add(new InsnNode(Opcodes.ARETURN));
        guard.add(proceed);guard.add(start);
        method.instructions.insertBefore(resolvedEntrySites.get(0),guard);
        for(AbstractInsnNode instruction:original)if(instruction.getOpcode()==Opcodes.ARETURN) {
            InsnList release=new InsnList();release.add(new VarInsnNode(Opcodes.ALOAD,token));
            release.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
            method.instructions.insertBefore(instruction,release);
        }
        method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,error));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,error));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }

    private static void weaveClear(ClassNode owner,MethodNode method) {
        AbstractInsnNode first=method.instructions.getFirst();
        if(first==null)throw new IllegalStateException("EMPTY_HASHMAP_CLEAR");
        LabelNode original=new LabelNode();
        InsnList guard=new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD,0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"clearProtectedMap",CLEAR_DESC,false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ,original));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(original);
        guard.add(new FrameNode(Opcodes.F_FULL,1,new Object[]{owner.name},0,new Object[0]));
        method.instructions.insertBefore(first,guard);
        method.maxStack=Math.max(method.maxStack,1);
    }

    private static boolean hasCall(MethodNode method,String name) {
        for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(BRIDGE)&&call.name.equals(name))return true;
        return false;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        for(AbstractInsnNode prior=instruction.getPrevious();prior!=null;prior=prior.getPrevious())
            if(prior.getOpcode()>=0)return prior;
        return null;
    }

    private static byte[] unsupported(String reason) {
        installed=false;
        status="UNSUPPORTED_JDK_HASHMAP_LAYOUT:"+reason;
        return null;
    }
}
