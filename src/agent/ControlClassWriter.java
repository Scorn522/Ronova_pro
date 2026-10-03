package dev.ronova.pro.agent;
import java.io.InputStream;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
/** Frame joins use actual loaded types or resources without initializing the target. */
final class ControlClassWriter extends ClassWriter {
    private record Shape(String parent,String[] interfaces,boolean contract) {}
    private final ClassLoader loader;
    private final Map<String,Shape> shapes=new HashMap<>();
    ControlClassWriter(ClassLoader loader,ClassNode node) {
        super(COMPUTE_FRAMES|COMPUTE_MAXS);this.loader=loader;
        shapes.put(node.name,new Shape(node.superName,node.interfaces.toArray(String[]::new),(node.access&Opcodes.ACC_INTERFACE)!=0));
    }
    private Shape shape(String name) {
        return shapes.computeIfAbsent(name,key->{
            Class<?> actual=loader==null?null:ExternalCodeDefinitions.initiated(loader,key);
            if(actual!=null)return shape(actual);
            try(InputStream in=loader==null?ClassLoader.getSystemResourceAsStream(key+".class"):loader.getResourceAsStream(key+".class")) {
                if(in==null){
                    // Named module loaders can resolve a type while exposing no
                    // resource stream to this writer. Resolve with the same
                    // actual loader and keep class initialization disabled.
                    try{return shape(Class.forName(key.replace('/','.'),false,loader));}
                    catch(ClassNotFoundException unavailable){throw new IllegalStateException("MISSING_FRAME_TYPE:"+key,unavailable);}
                }
                ClassReader r=new ClassReader(in);return new Shape(r.getSuperName(),r.getInterfaces(),(r.getAccess()&Opcodes.ACC_INTERFACE)!=0);
            } catch(java.io.IOException e) { throw new IllegalStateException("FRAME_TYPE_READ:"+key,e); }
        });
    }
    private static Shape shape(Class<?> actual){
        Class<?> parent=actual.getSuperclass();Class<?>[] contracts=actual.getInterfaces();String[] interfaces=new String[contracts.length];
        for(int i=0;i<contracts.length;i++)interfaces[i]=Type.getInternalName(contracts[i]);
        return new Shape(parent==null?null:Type.getInternalName(parent),interfaces,actual.isInterface());
    }
    private boolean accepts(String a,String b,Set<String> seen) {
        if(a.equals(b)||a.equals("java/lang/Object"))return true;
        if(!seen.add(b))return false;
        if(b.startsWith("["))return a.equals("java/lang/Cloneable")||a.equals("java/io/Serializable");
        Shape s=shape(b);
        if(s.parent()!=null&&accepts(a,s.parent(),seen))return true;
        for(String i:s.interfaces())if(accepts(a,i,seen))return true;
        return false;
    }
    @Override protected String getCommonSuperClass(String a,String b) {
        if(a.equals(b))return a;
        if(a.startsWith("[")&&b.startsWith("[")) {
            String x=a.substring(1),y=b.substring(1);
            if((x.startsWith("L")||x.startsWith("["))&&(y.startsWith("L")||y.startsWith("["))) {
                String c=getCommonSuperClass(component(x),component(y));return "["+(c.startsWith("[")?c:"L"+c+";");
            }
            return "java/lang/Object";
        }
        if(accepts(a,b,new HashSet<>()))return a;
        if(accepts(b,a,new HashSet<>()))return b;
        if(a.startsWith("[")||b.startsWith("[")||shape(a).contract()||shape(b).contract())return "java/lang/Object";
        do { a=shape(a).parent(); } while(a!=null&&!accepts(a,b,new HashSet<>()));
        return a==null?"java/lang/Object":a;
    }
    private static String component(String d) { return d.startsWith("L")?d.substring(1,d.length()-1):d; }
    static void box(InsnList code,Type type) {
        String owner=switch(type.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";case Type.BYTE -> "java/lang/Byte";
            case Type.CHAR -> "java/lang/Character";case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";case Type.LONG -> "java/lang/Long";
            case Type.FLOAT -> "java/lang/Float";case Type.DOUBLE -> "java/lang/Double";default -> null;
        };
        if(owner!=null)code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,owner,"valueOf","("+type.getDescriptor()+")L"+owner+";",false));
    }
}
