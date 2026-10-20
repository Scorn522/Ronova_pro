package dev.ronova.pro;

import java.lang.reflect.*;
import java.util.*;

/** Exact JDK layouts used only for direct source retirement, never application key lookup. */
final class KnownContainers {
    private KnownContainers() { }
    static boolean map(Object value){return value!=null&&(value.getClass()==TreeMap.class||value.getClass()==IdentityHashMap.class);}
    static boolean sequence(Object value){return value!=null&&(value.getClass()==LinkedList.class||value.getClass()==ArrayDeque.class);}
    static boolean set(Object value){return value!=null&&value.getClass()==TreeSet.class;}
    static boolean supported(Object value){return map(value)||sequence(value);}
    static Object setMap(Object carrier)throws ReflectiveOperationException{
        if(!set(carrier))throw new IllegalArgumentException("ACTUAL_TREESET_REQUIRED");
        Object map=get(carrier,TreeSet.class,"m",NavigableMap.class);
        if(map==null||map.getClass()!=TreeMap.class)throw new IllegalAccessException("SOURCE_TREESET_BACKING_UNSUPPORTED");return map;
    }
    static Object auxiliary(Object carrier)throws ReflectiveOperationException{
        return carrier!=null&&carrier.getClass()==TreeMap.class?get(carrier,TreeMap.class,"comparator",Comparator.class):null;
    }
    private static Class<?> nested(String name)throws ClassNotFoundException{return Class.forName(name,false,null);}
    private static Field slot(Class<?> owner,String name,Class<?> type,boolean statik)throws ReflectiveOperationException{
        Field field=PlatformAccess.declaredField(owner,name);
        if(field.getDeclaringClass()!=owner||field.getType()!=type||Modifier.isStatic(field.getModifiers())!=statik
                ||!field.trySetAccessible())throw new IllegalAccessException("SOURCE_JDK_LAYOUT_UNAVAILABLE:"+owner.getName()+"#"+name);
        return field;
    }
    private static Object get(Object value,Class<?> owner,String name,Class<?> type)throws ReflectiveOperationException{
        return slot(owner,name,type,false).get(value);
    }
    private static int integer(Object value,Class<?> owner,String name)throws ReflectiveOperationException{
        return slot(owner,name,int.class,false).getInt(value);
    }
    private static Method operation(Class<?> owner,String name,Class<?> result,Class<?>... parameters)throws ReflectiveOperationException{
        Method method=PlatformAccess.declaredMethod(owner,name,parameters);
        if(method.getDeclaringClass()!=owner||method.getReturnType()!=result||Modifier.isStatic(method.getModifiers())
                ||!method.trySetAccessible())throw new IllegalAccessException("SOURCE_JDK_OPERATION_UNAVAILABLE:"+owner.getName()+"#"+name);
        return method;
    }
    static final class Row {
        final Object key,value,node,left,right,parent,rawKey;final int position;
        Row(Object key,Object value,Object node,int position,Object left,Object right,Object parent,Object rawKey){
            this.key=key;this.value=value;this.node=node;this.position=position;this.left=left;this.right=right;this.parent=parent;this.rawKey=rawKey;
        }
    }
    static final class Image {
        final Object carrier,backing,last;final long revision;final int size,head,tail;final Object[] slots;final Row[] rows;
        Image(Object carrier,Object backing,Object last,long revision,int size,int head,int tail,Object[] slots,List<Row> rows){
            this.carrier=carrier;this.backing=backing;this.last=last;this.revision=revision;this.size=size;this.head=head;this.tail=tail;
            this.slots=slots;this.rows=rows.toArray(Row[]::new);
        }
    }
    static long revision(Object carrier)throws ReflectiveOperationException{
        if(carrier.getClass()==LinkedList.class)return Integer.toUnsignedLong(integer(carrier,AbstractList.class,"modCount"));
        if(map(carrier))return Integer.toUnsignedLong(integer(carrier,carrier.getClass(),"modCount"));
        if(carrier.getClass()==ArrayDeque.class)return 0; // Full slot/head/tail identity is the deque's actual stamp.
        throw new IllegalArgumentException("ACTUAL_KNOWN_JDK_CONTAINER_REQUIRED");
    }
    static Image snapshot(Object carrier)throws ReflectiveOperationException{
        if(!supported(carrier))throw new IllegalArgumentException("ACTUAL_KNOWN_JDK_CONTAINER_REQUIRED");
        Image image;
        if(carrier.getClass()==TreeMap.class)image=tree(carrier);
        else if(carrier.getClass()==IdentityHashMap.class)image=identity(carrier);
        else if(carrier.getClass()==LinkedList.class)image=linked(carrier);
        else image=deque(carrier);
        if(!unchanged(image))throw changed();return image;
    }
    private static Image tree(Object carrier)throws ReflectiveOperationException{
        Class<?> node=nested("java.util.TreeMap$Entry");
        Field key=slot(node,"key",Object.class,false),value=slot(node,"value",Object.class,false),
                left=slot(node,"left",node,false),right=slot(node,"right",node,false),parent=slot(node,"parent",node,false);
        Object root=get(carrier,TreeMap.class,"root",node),comparator=auxiliary(carrier);int size=integer(carrier,TreeMap.class,"size");long revision=revision(carrier);
        if(size<0)throw changed();
        var rows=new ArrayList<Row>();var pending=new ArrayDeque<Object>();var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        if(root!=null)pending.add(root);
        while(!pending.isEmpty()){
            Object current=pending.removeFirst();if(current.getClass()!=node||!seen.add(current)||rows.size()>=size)throw changed();
            Object l=left.get(current),r=right.get(current),p=parent.get(current);
            if(current==root&&p!=null||l!=null&&(l.getClass()!=node||parent.get(l)!=current)||r!=null&&(r.getClass()!=node||parent.get(r)!=current))throw changed();
            rows.add(new Row(key.get(current),value.get(current),current,rows.size(),l,r,p,null));
            if(l!=null)pending.addLast(l);if(r!=null)pending.addLast(r);
        }
        if(rows.size()!=size)throw changed();return new Image(carrier,root,comparator,revision,size,0,0,null,rows);
    }
    private static Image identity(Object carrier)throws ReflectiveOperationException{
        Object[] table=(Object[])get(carrier,IdentityHashMap.class,"table",Object[].class);
        int size=integer(carrier,IdentityHashMap.class,"size");long revision=revision(carrier);
        if(table==null||(table.length&1)!=0||size<0||size>table.length/2)throw changed();
        Object nil=slot(IdentityHashMap.class,"NULL_KEY",Object.class,true).get(null);Object[] slots=table.clone();var rows=new ArrayList<Row>();
        for(int index=0;index<slots.length;index+=2){Object key=slots[index];
            if(key!=null)rows.add(new Row(key==nil?null:key,slots[index+1],null,index,null,null,null,key));
            else if(slots[index+1]!=null)throw changed();
        }
        if(rows.size()!=size)throw changed();return new Image(carrier,table,null,revision,size,0,0,slots,rows);
    }
    private static Image linked(Object carrier)throws ReflectiveOperationException{
        Class<?> node=nested("java.util.LinkedList$Node");
        Field item=slot(node,"item",Object.class,false),next=slot(node,"next",node,false),prev=slot(node,"prev",node,false);
        Object first=get(carrier,LinkedList.class,"first",node),last=get(carrier,LinkedList.class,"last",node);
        int size=integer(carrier,LinkedList.class,"size");long revision=revision(carrier);if(size<0)throw changed();
        var rows=new ArrayList<Row>();Object before=null,current=first;
        while(current!=null){
            if(current.getClass()!=node||rows.size()>=size||prev.get(current)!=before)throw changed();
            Object after=next.get(current);rows.add(new Row(null,item.get(current),current,rows.size(),before,after,null,null));before=current;current=after;
        }
        if(rows.size()!=size||before!=last)throw changed();return new Image(carrier,first,last,revision,size,0,0,null,rows);
    }
    private static Image deque(Object carrier)throws ReflectiveOperationException{
        Object[] elements=(Object[])get(carrier,ArrayDeque.class,"elements",Object[].class);
        int head=integer(carrier,ArrayDeque.class,"head"),tail=integer(carrier,ArrayDeque.class,"tail");
        if(elements==null||elements.length==0||head<0||tail<0||head>=elements.length||tail>=elements.length)throw changed();
        Object[] slots=elements.clone();int size=tail>=head?tail-head:elements.length-head+tail;var rows=new ArrayList<Row>();
        int index=head;for(int count=0;count<size;count++){
            Object item=slots[index];if(item==null)throw changed();rows.add(new Row(null,item,null,index,null,null,null,null));
            if(++index==slots.length)index=0;
        }
        for(int count=size;count<slots.length;count++){if(slots[index]!=null)throw changed();if(++index==slots.length)index=0;}
        return new Image(carrier,elements,null,0,size,head,tail,slots,rows);
    }
    static boolean unchanged(Image image)throws ReflectiveOperationException{
        Object carrier=image.carrier;Class<?> type=carrier.getClass();
        if(revision(carrier)!=image.revision)return false;
        if(type==TreeMap.class){
            Class<?> node=nested("java.util.TreeMap$Entry");
            if(get(carrier,type,"root",node)!=image.backing||auxiliary(carrier)!=image.last||integer(carrier,type,"size")!=image.size)return false;
            Field key=slot(node,"key",Object.class,false),value=slot(node,"value",Object.class,false),left=slot(node,"left",node,false),
                    right=slot(node,"right",node,false),parent=slot(node,"parent",node,false);
            for(Row row:image.rows)if(key.get(row.node)!=row.key||value.get(row.node)!=row.value||left.get(row.node)!=row.left
                    ||right.get(row.node)!=row.right||parent.get(row.node)!=row.parent)return false;return true;
        }
        if(type==LinkedList.class){
            Class<?> node=nested("java.util.LinkedList$Node");
            if(get(carrier,type,"first",node)!=image.backing||get(carrier,type,"last",node)!=image.last||integer(carrier,type,"size")!=image.size)return false;
            Field item=slot(node,"item",Object.class,false),next=slot(node,"next",node,false),prev=slot(node,"prev",node,false);
            for(Row row:image.rows)if(item.get(row.node)!=row.value||next.get(row.node)!=row.right||prev.get(row.node)!=row.left)return false;return true;
        }
        Object[] actual;
        if(type==IdentityHashMap.class){
            if(integer(carrier,type,"size")!=image.size)return false;actual=(Object[])get(carrier,type,"table",Object[].class);
        }else{
            if(integer(carrier,type,"head")!=image.head||integer(carrier,type,"tail")!=image.tail)return false;
            actual=(Object[])get(carrier,type,"elements",Object[].class);
        }
        if(actual!=image.backing||actual.length!=image.slots.length)return false;
        for(int index=0;index<actual.length;index++)if(actual[index]!=image.slots[index])return false;return true;
    }
    static boolean remove(Image image,Row row)throws ReflectiveOperationException{
        boolean present=false;for(Row actual:image.rows)if(actual==row){present=true;break;}
        if(!present||!unchanged(image))throw changed();Object carrier=image.carrier;
        if(carrier.getClass()==TreeMap.class)operation(TreeMap.class,"deleteEntry",void.class,nested("java.util.TreeMap$Entry")).invoke(carrier,row.node);
        else if(carrier.getClass()==IdentityHashMap.class){
            // Work from the observed raw slot, including NULL_KEY, without consulting a key method or a replaceable sentinel.
            Object[] table=(Object[])image.backing;
            slot(IdentityHashMap.class,"modCount",int.class,false).setInt(carrier,(int)image.revision+1);
            slot(IdentityHashMap.class,"size",int.class,false).setInt(carrier,image.size-1);
            table[row.position]=null;table[row.position+1]=null;
            operation(IdentityHashMap.class,"closeDeletion",void.class,int.class).invoke(carrier,row.position);
        }
        else if(carrier.getClass()==LinkedList.class)operation(LinkedList.class,"unlink",Object.class,nested("java.util.LinkedList$Node")).invoke(carrier,row.node);
        else operation(ArrayDeque.class,"delete",boolean.class,int.class).invoke(carrier,row.position);
        Image after=snapshot(carrier);
        if(after.size!=image.size-1)throw new IllegalStateException("SOURCE_JDK_REMOVAL_READBACK_FAILED");
        if(map(carrier)){
            var retained=new IdentityHashMap<Object,Object>();for(Row actual:after.rows)retained.put(actual.key,actual.value);
            for(Row before:image.rows)if(before!=row&&(!retained.containsKey(before.key)||retained.get(before.key)!=before.value))
                throw new IllegalStateException("SOURCE_JDK_UNSELECTED_MAPPING_CHANGED");
        }else{
            int at=0;for(Row before:image.rows)if(before!=row&&after.rows[at++].value!=before.value)
                throw new IllegalStateException("SOURCE_JDK_UNSELECTED_ELEMENT_CHANGED");
        }
        return true;
    }
    static void clear(Image image)throws ReflectiveOperationException{
        if(!unchanged(image))throw changed();
        if(map(image.carrier))((Map<?,?>)image.carrier).clear();else ((Collection<?>)image.carrier).clear();
        if(snapshot(image.carrier).size!=0)throw new IllegalStateException("GROUP_PRIVATE_CONTAINER_CLEAR_READBACK_FAILED");
    }
    private static IllegalAccessException changed(){return new IllegalAccessException("SOURCE_JDK_CONTAINER_CHANGED_OR_LAYOUT_UNAVAILABLE");}
}
