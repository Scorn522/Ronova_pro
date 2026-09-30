package dev.ronova.pro.agent;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Exact-layout transformer for the two Minecraft-owned fastutil ID maps and their iterators. */
public final class FastutilIndexBoundary implements Opcodes {
    private static final String LINK="it/unimi/dsi/fastutil/ints/Int2ObjectLinkedOpenHashMap";
    private static final String LONG_OPEN="it/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap";
    private static final String OPEN="it/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap";
    private static final String BRIDGE="dev/ronova/pro/bootstrap/TaskBridge";
    private static final String INDEX_DESC="(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z";
    private static final String CLEAR_DESC="(Ljava/lang/Object;)Z";
    private static final String OBJECT="Ljava/lang/Object;";
    private static final String INT_ARRAY="[I";
    private static final String VALUE_ARRAY="[Ljava/lang/Object;";
    private static final Map<String,String> STATES=new ConcurrentHashMap<>();

    private FastutilIndexBoundary() { }

    public static byte[] transform(ClassLoader loader,String name,byte[] bytes) {
        if(name==null||bytes==null)return bytes;
        String kind=kind(name);
        if(kind==null)return bytes;
        try {
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);
            if(!node.name.equals(name)||!layoutSupported(node,kind)) {
                STATES.put(name,"UNSUPPORTED: class/layout/signature mismatch");return bytes;
            }
            int changed="linked".equals(kind)?instrumentLinkedMap(node):"open".equals(kind)?instrumentOpenMap(node)
                    :"linkedIterator".equals(kind)?instrumentLinkedIterator(node):"openIterator".equals(kind)?instrumentOpenIterator(node):0;
            if(kind.equals("linked")||kind.equals("open")||kind.endsWith("Entry")) {
                int stores=IndexValueBoundary.fastutilStores(node,kind.startsWith("linked")?LINK:openOwner(name),kind.endsWith("Entry"));
                int expected=kind.endsWith("Entry")?1:kind.equals("linked")?10:7;
                if(stores!=expected)throw new IllegalStateException("VALUE_STORE_LAYOUT:"+stores+":"+expected);
                changed+=stores;
            }
            if(kind.equals("linked")||kind.equals("open"))changed+=instrumentIncoming(node);
            if(kind.equals("linked")||kind.equals("open")||kind.endsWith("Iterator"))
                changed+=instrumentMutationScopes(node,kind);
            if(changed==0){STATES.put(name,"UNSUPPORTED: no expected method transformed");return bytes;}
            ClassWriter writer=new SafeWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS,loader);
            node.accept(writer);byte[] result=writer.toByteArray();STATES.put(name,"INSTALLED");return result;
        } catch(Throwable failure) {
            STATES.put(name,"UNSUPPORTED: "+failure.getClass().getSimpleName()+":"+failure.getMessage());return bytes;
        }
    }

    public static String state(String name) { return STATES.getOrDefault(name,"NOT_SEEN"); }
    public static Map<String,String> states() { return Map.copyOf(STATES); }
    public static List<String> targets() { return List.of(LINK,OPEN,LONG_OPEN,LINK+"$MapIterator",OPEN+"$MapIterator",LONG_OPEN+"$MapIterator",LINK+"$MapEntry",OPEN+"$MapEntry",LONG_OPEN+"$MapEntry"); }
    public static boolean installed() { return STATES.containsValue("INSTALLED"); }

    private static String kind(String name) {
        if(name.equals(LINK))return "linked";
        if(name.equals(OPEN)||name.equals(LONG_OPEN))return "open";
        if(name.equals(LINK+"$MapIterator"))return "linkedIterator";
        if(name.equals(OPEN+"$MapIterator")||name.equals(LONG_OPEN+"$MapIterator"))return "openIterator";
        if(name.equals(LINK+"$MapEntry"))return "linkedEntry";
        if(name.equals(OPEN+"$MapEntry")||name.equals(LONG_OPEN+"$MapEntry"))return "openEntry";
        return null;
    }

    private static boolean layoutSupported(ClassNode n,String kind) {
        if(kind.endsWith("Entry"))return field(n,"this$0","L"+(kind.startsWith("linked")?LINK:openOwner(n.name))+";")
                &&field(n,"index","I")&&method(n,"setValue","(Ljava/lang/Object;)Ljava/lang/Object;");
        if(kind.equals("linked")||kind.equals("open")) {
            if(!field(n,"key",keyArray(n.name))||!field(n,"value",VALUE_ARRAY)||!field(n,"n","I")
                    ||!field(n,"containsNullKey","Z")
                    ||!field(n,"size","I"))return false;
            if(!method(n,"removeEntry","(I)"+OBJECT)||!method(n,"removeNullEntry","()"+OBJECT)
                    ||!method(n,"clear","()V"))return false;
            if(kind.equals("linked")&&(!field(n,"first","I")||!field(n,"last","I")
                    ||!method(n,"removeFirst","()"+OBJECT)||!method(n,"removeLast","()"+OBJECT)))return false;
            return true;
        }
        String outer=kind.equals("linkedIterator")?LINK:openOwner(n.name);
        if(!field(n,"this$0","L"+outer+";")||!method(n,"remove","()V"))return false;
        return kind.equals("linkedIterator")?field(n,"curr","I")
                :field(n,"last","I")&&field(n,"pos","I")
                &&field(n,"wrapped","L"+wrappedOwner(outer)+";");
    }

    private static int instrumentLinkedMap(ClassNode n) {
        int c=0;c+=instrumentSlotRemoval(n,"removeEntry","I",false,LINK);
        c+=instrumentNullRemoval(n,LINK);c+=instrumentClear(n);
        c+=instrumentDequeRemoval(n,"removeFirst","first",LINK);
        c+=instrumentDequeRemoval(n,"removeLast","last",LINK);return c;
    }
    private static int instrumentOpenMap(ClassNode n) {
        String owner=openOwner(n.name);int c=0;c+=instrumentSlotRemoval(n,"removeEntry","I",false,owner);
        c+=instrumentNullRemoval(n,owner);c+=instrumentClear(n);return c;
    }

    private static int instrumentIncoming(ClassNode node) {
        int count=0;
        for(MethodNode method:node.methods) {
            if(method.name.equals("insert")&&jdk.internal.org.objectweb.asm.Type.getReturnType(method.desc).getSort()==jdk.internal.org.objectweb.asm.Type.VOID) {
                InsnList check=new InsnList();LabelNode allow=new LabelNode();
                check.add(new VarInsnNode(ALOAD,0));check.add(new VarInsnNode(ALOAD,wide(node.name)?4:3));
                check.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","incomingAllowed","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                check.add(new JumpInsnNode(IFNE,allow));check.add(new InsnNode(RETURN));check.add(allow);
                method.instructions.insert(check);count++;
            }
            // These linked variants mutate the key/link arrays inline before the value store.
            if(method.name.equals("putAndMoveToFirst")||method.name.equals("putAndMoveToLast")) {
                InsnList check=new InsnList();LabelNode allow=new LabelNode();
                check.add(new VarInsnNode(ALOAD,0));check.add(new VarInsnNode(ALOAD,wide(node.name)?3:2));
                check.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","incomingAllowed","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                check.add(new JumpInsnNode(IFNE,allow));check.add(new VarInsnNode(ALOAD,0));check.add(new FieldInsnNode(GETFIELD,node.name,"defRetValue",OBJECT));check.add(new InsnNode(ARETURN));check.add(allow);
                method.instructions.insert(check);count++;
            }
            for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof MethodInsnNode call&&call.owner.equals(node.name)&&call.name.equals("insert")) {
                var args=jdk.internal.org.objectweb.asm.Type.getArgumentTypes(call.desc);
                if(args.length!=3||args[0].getSort()!=jdk.internal.org.objectweb.asm.Type.INT||!args[2].getDescriptor().equals(OBJECT)||jdk.internal.org.objectweb.asm.Type.getReturnType(method.desc).getSort()!=jdk.internal.org.objectweb.asm.Type.OBJECT)
                    throw new IllegalStateException("FASTUTIL_INSERT_LAYOUT:"+method.name);
                int value=method.maxLocals++,key=method.maxLocals;method.maxLocals+=args[1].getSize();int pos=method.maxLocals++,map=method.maxLocals++;
                InsnList check=new InsnList();LabelNode allow=new LabelNode();
                check.add(new VarInsnNode(ASTORE,value));check.add(new VarInsnNode(args[1].getOpcode(ISTORE),key));check.add(new VarInsnNode(ISTORE,pos));check.add(new VarInsnNode(ASTORE,map));
                check.add(new VarInsnNode(ALOAD,map));check.add(new VarInsnNode(ALOAD,value));check.add(new MethodInsnNode(INVOKESTATIC,"dev/ronova/pro/bootstrap/BackingBridge","incomingAllowed","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                check.add(new JumpInsnNode(IFNE,allow));check.add(new VarInsnNode(ALOAD,map));check.add(new FieldInsnNode(GETFIELD,node.name,"defRetValue",OBJECT));check.add(new InsnNode(ARETURN));check.add(allow);
                check.add(new VarInsnNode(ALOAD,map));check.add(new VarInsnNode(ILOAD,pos));check.add(new VarInsnNode(args[1].getOpcode(ILOAD),key));check.add(new VarInsnNode(ALOAD,value));
                method.instructions.insertBefore(at,check);count++;
            }
        }
        if(count==0)throw new IllegalStateException("FASTUTIL_INSERT_BOUNDARY_MISSING");return count;
    }

    /** Scope only primitive-key mutations with no application callback; compute/merge callbacks stay outside. */
    private static int instrumentMutationScopes(ClassNode node,String kind) {
        boolean iterator=kind.endsWith("Iterator");
        int count=0;
        for(MethodNode method:node.methods) {
            if((method.access&(ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE))!=0)continue;
            if(iterator?!method.name.equals("remove"):!primitiveMutation(method,node.name))continue;
            if(method.name.equals("rehash")) { scopeRehashPublication(node,method,kind);count++;continue; }
            int token=method.maxLocals++,error=method.maxLocals++;
            LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
            AbstractInsnNode[] original=method.instructions.toArray();
            InsnList head=new InsnList();head.add(new VarInsnNode(ALOAD,0));
            if(iterator)head.add(new FieldInsnNode(GETFIELD,node.name,"this$0","L"+
                    (kind.equals("linkedIterator")?LINK:openOwner(node.name))+";"));
            head.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginIndexStructure","(Ljava/lang/Object;)Ljava/lang/Object;",false));
            head.add(new VarInsnNode(ASTORE,token));head.add(start);method.instructions.insert(head);
            for(AbstractInsnNode at:original)if(at.getOpcode()>=IRETURN&&at.getOpcode()<=RETURN) {
                InsnList release=new InsnList();release.add(new VarInsnNode(ALOAD,token));
                release.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
                method.instructions.insertBefore(at,release);
            }
            method.instructions.add(end);method.instructions.add(handler);
            method.instructions.add(new VarInsnNode(ASTORE,error));method.instructions.add(new VarInsnNode(ALOAD,token));
            method.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
            method.instructions.add(new VarInsnNode(ALOAD,error));method.instructions.add(new InsnNode(ATHROW));
            method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));count++;
        }
        return count;
    }
    private static void scopeRehashPublication(ClassNode owner,MethodNode method,String kind) {
        FieldInsnNode first=null,last=null;
        String firstField=kind.equals("linked")?"link":"n";
        for(AbstractInsnNode at:method.instructions.toArray())if(at instanceof FieldInsnNode field
                &&field.getOpcode()==PUTFIELD&&field.owner.equals(owner.name)) {
            if(field.name.equals(firstField)&&first==null)first=field;
            if(field.name.equals("value")&&field.desc.equals(VALUE_ARRAY))last=field;
        }
        if(first==null||last==null)throw new IllegalStateException("FASTUTIL_REHASH_COMMIT_LAYOUT:"+owner.name);
        int token=method.maxLocals++,error=method.maxLocals++;
        LabelNode start=new LabelNode(),end=new LabelNode(),handler=new LabelNode();
        InsnList begin=new InsnList();begin.add(new VarInsnNode(ALOAD,0));
        begin.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"beginIndexStructure","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        begin.add(new VarInsnNode(ASTORE,token));begin.add(start);method.instructions.insertBefore(first,begin);
        InsnList release=new InsnList();release.add(new VarInsnNode(ALOAD,token));
        release.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
        method.instructions.insert(last,release);method.instructions.insert(last,end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(ASTORE,error));method.instructions.add(new VarInsnNode(ALOAD,token));
        method.instructions.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"endIndexMutation","(Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(ALOAD,error));method.instructions.add(new InsnNode(ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,null));
    }
    private static boolean primitiveMutation(MethodNode method,String owner) {
        String name=method.name;
        if(java.util.Set.of("insert","removeEntry","removeNullEntry","removeFirst","removeLast","clear",
                "shiftKeys","rehash","putAndMoveToFirst","putAndMoveToLast").contains(name))return true;
        String key=wide(owner)?"J":"I";
        return (name.equals("put")||name.equals("replace")||name.equals("remove"))
                &&method.desc.startsWith("("+key)
                &&(name.equals("remove")?method.desc.equals("("+key+")Ljava/lang/Object;")
                    :method.desc.equals("("+key+"Ljava/lang/Object;)Ljava/lang/Object;"));
    }

    private static int instrumentSlotRemoval(ClassNode n,String name,String arg,boolean unused,String owner) {
        MethodNode m=find(n,name,"(I)"+OBJECT);if(m==null)return 0;
        InsnList code=new InsnList();LabelNode allow=new LabelNode();
        pushSlotCheck(code,owner,1,false);code.add(new JumpInsnNode(IFNE,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"defRetValue",OBJECT));
        code.add(new InsnNode(ARETURN));addContinuation(code,allow);m.instructions.insert(code);return 1;
    }

    private static int instrumentNullRemoval(ClassNode n,String owner) {
        MethodNode m=find(n,"removeNullEntry","()"+OBJECT);if(m==null)return 0;
        InsnList code=new InsnList();LabelNode allow=new LabelNode();
        pushMap(code);code.add(new InsnNode(wide(owner)?LCONST_0:ICONST_0));boxKey(code,owner);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"n","I"));code.add(new InsnNode(AALOAD));
        callIndex(code);code.add(new JumpInsnNode(IFNE,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"defRetValue",OBJECT));code.add(new InsnNode(ARETURN));
        addContinuation(code,allow);m.instructions.insert(code);return 1;
    }

    private static int instrumentDequeRemoval(ClassNode n,String method,String indexField,String owner) {
        MethodNode m=find(n,method,"()"+OBJECT);if(m==null)return 0;
        InsnList code=new InsnList();LabelNode allow=new LabelNode();
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"size","I"));
        code.add(new JumpInsnNode(IFEQ,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,indexField,"I"));code.add(new VarInsnNode(ISTORE,1));
        pushMap(code);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"key",INT_ARRAY));
        code.add(new VarInsnNode(ILOAD,1));code.add(new InsnNode(IALOAD));boxInt(code);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ILOAD,1));code.add(new InsnNode(AALOAD));callIndex(code);
        code.add(new JumpInsnNode(IFNE,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"defRetValue",OBJECT));code.add(new InsnNode(ARETURN));
        addContinuation(code,allow);
        m.instructions.insert(code);return 1;
    }

    private static int instrumentClear(ClassNode n) {
        MethodNode m=find(n,"clear","()V");if(m==null)return 0;
        InsnList code=new InsnList();LabelNode proceed=new LabelNode();
        pushMap(code);code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"clearProtectedMap",CLEAR_DESC,false));
        code.add(new JumpInsnNode(IFEQ,proceed));code.add(new InsnNode(RETURN));addContinuation(code,proceed);
        m.instructions.insert(code);return 1;
    }

    private static int instrumentLinkedIterator(ClassNode n) {
        MethodNode m=find(n,"remove","()V");if(m==null)return 0;
        InsnList code=new InsnList();LabelNode allow=new LabelNode();
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"curr","I"));
        code.add(new JumpInsnNode(IFLT,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+LINK+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+LINK+";"));
        code.add(new FieldInsnNode(GETFIELD,LINK,"key",INT_ARRAY));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"curr","I"));code.add(new InsnNode(IALOAD));boxInt(code);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+LINK+";"));
        code.add(new FieldInsnNode(GETFIELD,LINK,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"curr","I"));code.add(new InsnNode(AALOAD));callIndex(code);
        code.add(new JumpInsnNode(IFNE,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new InsnNode(ICONST_M1));code.add(new FieldInsnNode(PUTFIELD,n.name,"curr","I"));
        code.add(new InsnNode(RETURN));
        addContinuation(code,allow);m.instructions.insert(code);return 1;
    }

    private static int instrumentOpenIterator(ClassNode n) {
        String owner=openOwner(n.name);MethodNode m=find(n,"remove","()V");if(m==null)return 0;
        InsnList code=new InsnList();LabelNode allow=new LabelNode(),nullKey=new LabelNode(),deny=new LabelNode(),wrap=new LabelNode();
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"last","I"));
        code.add(new InsnNode(ICONST_M1));code.add(new JumpInsnNode(IF_ICMPEQ,allow)); // preserve native illegal-state path.
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"last","I"));
        code.add(new LdcInsnNode(Integer.MIN_VALUE));code.add(new JumpInsnNode(IF_ICMPEQ,wrap));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"last","I"));code.add(new JumpInsnNode(IFLT,allow));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"last","I"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"n","I"));code.add(new JumpInsnNode(IF_ICMPEQ,nullKey));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"last","I"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"n","I"));code.add(new JumpInsnNode(IF_ICMPGT,allow));
        pushOpenIteratorSlotCheck(code,n.name,owner);
        code.add(new JumpInsnNode(IFEQ,deny));code.add(new JumpInsnNode(GOTO,allow));
        code.add(nullKey);code.add(new FrameNode(F_SAME,0,null,0,null));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"containsNullKey","Z"));code.add(new JumpInsnNode(IFEQ,allow));
        pushOpenIteratorNullCheck(code,n.name,owner);
        code.add(new JumpInsnNode(IFEQ,deny));code.add(new JumpInsnNode(GOTO,allow));
        code.add(wrap);code.add(new FrameNode(F_SAME,0,null,0,null));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+owner+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"this$0","L"+owner+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"wrapped","L"+wrappedOwner(owner)+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"pos","I"));
        code.add(new InsnNode(INEG));code.add(new InsnNode(ICONST_1));code.add(new InsnNode(ISUB));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,wrappedOwner(owner),wide(owner)?"getLong":"getInt",wide(owner)?"(I)J":"(I)I",false));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,owner,"get",wide(owner)?"(J)Ljava/lang/Object;":"(I)Ljava/lang/Object;",false));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"wrapped","L"+wrappedOwner(owner)+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,n.name,"pos","I"));
        code.add(new InsnNode(INEG));code.add(new InsnNode(ICONST_1));code.add(new InsnNode(ISUB));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,wrappedOwner(owner),wide(owner)?"getLong":"getInt",wide(owner)?"(I)J":"(I)I",false));boxKey(code,owner);
        // Restore bridge argument order (map,key,value) after computing the map's current value.
        code.add(new InsnNode(SWAP));
        code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"indexRemovalAllowed",INDEX_DESC,false));
        code.add(new JumpInsnNode(IFEQ,deny));code.add(new JumpInsnNode(GOTO,allow));
        code.add(deny);code.add(new FrameNode(F_SAME,0,null,0,null));
        code.add(new VarInsnNode(ALOAD,0));code.add(new InsnNode(ICONST_M1));code.add(new FieldInsnNode(PUTFIELD,n.name,"last","I"));
        code.add(new InsnNode(RETURN));
        addContinuation(code,allow);m.instructions.insert(code);return 1;
    }

    private static void pushOpenIteratorSlotCheck(InsnList code,String iterator,String owner) {
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"key",keyArray(owner)));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"last","I"));code.add(new InsnNode(wide(owner)?LALOAD:IALOAD));boxKey(code,owner);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"last","I"));code.add(new InsnNode(AALOAD));callIndex(code);
    }
    private static void pushOpenIteratorNullCheck(InsnList code,String iterator,String owner) {
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new InsnNode(wide(owner)?LCONST_0:ICONST_0));boxKey(code,owner);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,iterator,"this$0","L"+owner+";"));
        code.add(new FieldInsnNode(GETFIELD,owner,"n","I"));code.add(new InsnNode(AALOAD));callIndex(code);
    }

    private static void pushSlotCheck(InsnList code,String owner,int positionLocal,boolean ignored) {
        pushMap(code);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"key",keyArray(owner)));
        code.add(new VarInsnNode(ILOAD,positionLocal));code.add(new InsnNode(wide(owner)?LALOAD:IALOAD));boxKey(code,owner);
        code.add(new VarInsnNode(ALOAD,0));code.add(new FieldInsnNode(GETFIELD,owner,"value",VALUE_ARRAY));
        code.add(new VarInsnNode(ILOAD,positionLocal));code.add(new InsnNode(AALOAD));callIndex(code);
    }
    private static boolean wide(String owner){return owner.startsWith(LONG_OPEN);}
    private static String openOwner(String owner){return wide(owner)?LONG_OPEN:OPEN;}
    private static String keyArray(String owner){return wide(owner)?"[J":"[I";}
    private static String wrappedOwner(String owner){return wide(owner)?"it/unimi/dsi/fastutil/longs/LongArrayList":"it/unimi/dsi/fastutil/ints/IntArrayList";}
    private static void boxKey(InsnList code,String owner){code.add(new MethodInsnNode(INVOKESTATIC,wide(owner)?"java/lang/Long":"java/lang/Integer","valueOf",wide(owner)?"(J)Ljava/lang/Long;":"(I)Ljava/lang/Integer;",false));}
    private static void pushMap(InsnList code){code.add(new VarInsnNode(ALOAD,0));}
    private static void boxInt(InsnList code){code.add(new MethodInsnNode(INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false));}
    private static void callIndex(InsnList code){code.add(new MethodInsnNode(INVOKESTATIC,BRIDGE,"indexRemovalAllowed",INDEX_DESC,false));}
    private static void addContinuation(InsnList code,LabelNode label){code.add(label);code.add(new FrameNode(F_SAME,0,null,0,null));}
    private static boolean field(ClassNode n,String name,String desc){return n.fields.stream().anyMatch(f->f.name.equals(name)&&f.desc.equals(desc));}
    private static boolean method(ClassNode n,String name,String desc){return find(n,name,desc)!=null;}
    private static MethodNode find(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElse(null);}

    /** Recompute every frame after inserting branches, using the actual defining loader. */
    private static final class SafeWriter extends ClassWriter {
        private final ClassLoader loader;
        SafeWriter(int flags,ClassLoader loader){super(flags);this.loader=loader;}
        @Override protected String getCommonSuperClass(String left,String right) {
            try {
                Class<?> a=Class.forName(left.replace('/','.'),false,loader),b=Class.forName(right.replace('/','.'),false,loader);
                if(a.isAssignableFrom(b))return left;
                if(b.isAssignableFrom(a))return right;
                if(a.isInterface()||b.isInterface())return "java/lang/Object";
                do { a=a.getSuperclass(); } while(!a.isAssignableFrom(b));
                return a.getName().replace('.','/');
            } catch(ClassNotFoundException failure) { throw new IllegalStateException("FRAME_TYPE_UNAVAILABLE",failure); }
        }
    }
}
