package dev.ronova.pro.agent;
import java.lang.instrument.Instrumentation;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Declared defining classes and real overrides, with operation/value-aware receiver decisions. */
final class DispatchBoundary {
    private record Entry(Class<?> owner,String method,String descriptor,boolean arguments) {}
    private static volatile Entry[] entries=new Entry[0];
    private static final Set<String> transformed=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static synchronized boolean declare(Class<?> owner,String method,String descriptor) {
        return declare(owner,method,descriptor,false);
    }
    static synchronized boolean declare(Class<?> owner,String method,String descriptor,boolean arguments) {
        Entry e=new Entry(Objects.requireNonNull(owner),Objects.requireNonNull(method),Objects.requireNonNull(descriptor),arguments);
        for(int i=0;i<entries.length;i++) {
            Entry entry=entries[i];
            if(entry.owner!=owner||!entry.method.equals(method)||!entry.descriptor.equals(descriptor))continue;
            if(!arguments||entry.arguments)return false;
            Entry[] next=entries.clone();next[i]=e;ControlImages.protect(next,e);entries=next;return true;
        }
        Entry[] next=Arrays.copyOf(entries,entries.length+1);next[next.length-1]=e;
        ControlImages.protect(next,e);entries=next;return true;
    }
    static int declaredTargets() { return entries.length; }
    static List<String> transformedEntries() { return List.copyOf(transformed); }
    static List<String> missingPrimaryEntries() {
        var missing=new ArrayList<String>();
        for(Entry entry:entries) {
            Class<?> owner=entry.owner;
            // An abstract declaration supplies the override contract, but has no body to weave.
            boolean abstractDefinition=false;
            for(var method:owner.getDeclaredMethods())if(method.getName().equals(entry.method)
                    &&java.lang.invoke.MethodType.methodType(method.getReturnType(),method.getParameterTypes()).descriptorString().equals(entry.descriptor)) {
                abstractDefinition=java.lang.reflect.Modifier.isAbstract(method.getModifiers());break;
            }
            if(abstractDefinition)continue;
            String exact=System.identityHashCode(owner.getClassLoader())+":"+owner.getName().replace('.','/')
                    +"#"+entry.method+entry.descriptor;
            if(!transformed.contains(exact))missing.add(exact);
        }
        return List.copyOf(missing);
    }
    static boolean primaryCoverageComplete() {return entries.length>0&&missingPrimaryEntries().isEmpty();}
    static byte[] transform(ClassLoader loader,String name,Class<?> actual,byte[] bytes) {
        if(name==null||entries.length==0)return null;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode m:node.methods) {
            if((m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)continue;
            boolean statik=(m.access&Opcodes.ACC_STATIC)!=0;
            Entry entry=null;
            for(Entry e:entries)if(e.method.equals(m.name)&&e.descriptor.equals(m.desc)
                    &&(!statik||e.arguments&&e.owner.getName().replace('.','/').equals(name)&&e.owner.getClassLoader()==loader)
                    &&matches(e.owner,loader,name,actual,node)) { entry=e;break; }
            if(entry==null)continue;
            // A foreign body can contain a bridge call in dead code. Our retained input
            // image precedes this pass, so always place the real guard at the entry.
            Type[] args=Type.getArgumentTypes(m.desc);
            Type result=Type.getReturnType(m.desc);
            boolean lifeGetter=!statik&&Set.of("getHealth","m_21223_","isAlive","m_6084_","isDeadOrDying","m_21224_").contains(m.name);
            if(lifeGetter&&args.length==0&&(result.getSort()==Type.FLOAT||result.getSort()==Type.BOOLEAN)) {
                InsnList c=new InsnList();LabelNode original=new LabelNode();
                c.add(new VarInsnNode(Opcodes.ALOAD,0));c.add(new LdcInsnNode(m.name));
                c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","lifeResult","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",false));
                c.add(new InsnNode(Opcodes.DUP));c.add(new JumpInsnNode(Opcodes.IFNULL,original));
                String boxed=result.getSort()==Type.FLOAT?"java/lang/Float":"java/lang/Boolean";
                c.add(new TypeInsnNode(Opcodes.CHECKCAST,boxed));
                c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,boxed,result.getSort()==Type.FLOAT?"floatValue":"booleanValue",result.getSort()==Type.FLOAT?"()F":"()Z",false));
                c.add(new InsnNode(result.getSort()==Type.FLOAT?Opcodes.FRETURN:Opcodes.IRETURN));
                c.add(original);c.add(new InsnNode(Opcodes.POP));m.instructions.insert(c);
                transformed.add(System.identityHashCode(loader)+":"+name+"#"+m.name+m.desc);changed=true;continue;
            }
            if(!entry.arguments&&result.getSort()!=Type.VOID&&result.getSort()!=Type.BOOLEAN&&result.getSort()!=Type.OBJECT&&result.getSort()!=Type.ARRAY)
                throw new IllegalStateException("UNSUPPORTED_DISPATCH_CONTRACT:"+m.desc);
            InsnList c=new InsnList();LabelNode allowed=new LabelNode();
            int payload=-1;
            if(entry.arguments) {
                payload=m.maxLocals++;c.add(new LdcInsnNode(args.length));c.add(new TypeInsnNode(Opcodes.ANEWARRAY,"java/lang/Object"));
                int local=statik?0:1;
                for(int i=0;i<args.length;i++) {
                    c.add(new InsnNode(Opcodes.DUP));c.add(new LdcInsnNode(i));
                    c.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),local));ControlClassWriter.box(c,args[i]);
                    c.add(new InsnNode(Opcodes.AASTORE));local+=args[i].getSize();
                }
                c.add(new VarInsnNode(Opcodes.ASTORE,payload));
            }
            receiver(c,statik,node);c.add(new LdcInsnNode(m.name));
            if(entry.arguments)c.add(new VarInsnNode(Opcodes.ALOAD,payload));
            else if(args.length==0)c.add(new InsnNode(Opcodes.ACONST_NULL));
            else { c.add(new VarInsnNode(args[0].getOpcode(Opcodes.ILOAD),1));ControlClassWriter.box(c,args[0]); }
            c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","dispatchAllowed","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Z",false));
            c.add(new JumpInsnNode(Opcodes.IFNE,allowed));
            if(result.getSort()==Type.BOOLEAN) {c.add(new InsnNode(Opcodes.ICONST_0));c.add(new InsnNode(Opcodes.IRETURN));}
            else if(result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY) {
                if(entry.arguments) {
                    receiver(c,statik,node);c.add(new LdcInsnNode(m.name));c.add(new VarInsnNode(Opcodes.ALOAD,payload));
                    c.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/ronova/pro/bootstrap/ControlBridge","dispatchDeniedResult","(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",false));
                    c.add(new TypeInsnNode(Opcodes.CHECKCAST,result.getInternalName()));
                } else c.add(new InsnNode(Opcodes.ACONST_NULL));
                c.add(new InsnNode(Opcodes.ARETURN));
            } else if(result.getSort()==Type.VOID)c.add(new InsnNode(Opcodes.RETURN));
            else {
                c.add(new InsnNode(result.getSort()==Type.LONG?Opcodes.LCONST_0:result.getSort()==Type.DOUBLE?Opcodes.DCONST_0:result.getSort()==Type.FLOAT?Opcodes.FCONST_0:Opcodes.ICONST_0));
                c.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
            }
            c.add(allowed);m.instructions.insert(c);
            transformed.add(System.identityHashCode(loader)+":"+name+"#"+m.name+m.desc);changed=true;
        }
        if(!changed)return null;
        ControlClassWriter writer=new ControlClassWriter(loader,node);node.accept(writer);return writer.toByteArray();
    }
    private static void receiver(InsnList code,boolean statik,ClassNode owner) {
        code.add(statik?new LdcInsnNode(Type.getObjectType(owner.name)):new VarInsnNode(Opcodes.ALOAD,0));
    }
    private static boolean matches(Class<?> owner,ClassLoader loader,String name,Class<?> actual,ClassNode node) {
        if(actual!=null)return owner.isAssignableFrom(actual);
        if(owner.getName().replace('.','/').equals(name))return owner.getClassLoader()==loader;
        try { return node.superName!=null&&owner.isAssignableFrom(Class.forName(node.superName.replace('/','.'),false,loader)); }
        catch(ClassNotFoundException|LinkageError unavailable) { return false; }
    }
    static void retransformDeclared(Instrumentation api) {
        for(Class<?> type:RecoveryAgent.loadedClasses()) {
            if(type.isArray()||type.isPrimitive()||!api.isModifiableClass(type))continue;
            boolean wanted=false;for(Entry e:entries)if(e.owner.isAssignableFrom(type)) { wanted=true;break; }
            if(wanted)try { api.retransformClasses(type); }
            catch(java.lang.instrument.UnmodifiableClassException e) { throw new IllegalStateException("DISPATCH_INSTALL_FAILED:"+type.getName(),e); }
        }
    }
}
