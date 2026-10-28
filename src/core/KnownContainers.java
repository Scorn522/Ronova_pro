package dev.ronova.pro;

import java.lang.reflect.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Exact JDK layouts used only for direct source retirement, never application key lookup. */
final class KnownContainers {
    private KnownContainers() { }
    static boolean map(Object value){return value!=null&&(value.getClass()==TreeMap.class||value.getClass()==IdentityHashMap.class||value.getClass()==EnumMap.class);}
    static boolean vector(Object value){return value!=null&&(value.getClass()==Vector.class||value.getClass()==Stack.class);}
    static boolean lockFree(Object value){return value!=null&&(value.getClass()==ConcurrentLinkedQueue.class||value.getClass()==ConcurrentLinkedDeque.class);}
    static boolean blocking(Object value){return value!=null&&value.getClass()==LinkedBlockingQueue.class;}
    static boolean sequence(Object value){return value!=null&&(value.getClass()==LinkedList.class||value.getClass()==ArrayDeque.class
            ||value.getClass()==PriorityQueue.class||vector(value)||lockFree(value)||blocking(value));}
    static boolean enumSet(Object value){return value!=null&&value.getClass().getClassLoader()==null
            &&(value.getClass().getName().equals("java.util.RegularEnumSet")||value.getClass().getName().equals("java.util.JumboEnumSet"));}
    static boolean set(Object value){return value!=null&&(value.getClass()==TreeSet.class||enumSet(value));}
    static boolean supported(Object value){return map(value)||sequence(value)||enumSet(value);}
    static Object setMap(Object carrier)throws ReflectiveOperationException{
        if(enumSet(carrier))return carrier;
        if(!set(carrier))throw new IllegalArgumentException("ACTUAL_TREESET_REQUIRED");
        Object map=get(carrier,TreeSet.class,"m",NavigableMap.class);
        if(map==null||map.getClass()!=TreeMap.class)throw new IllegalAccessException("SOURCE_TREESET_BACKING_UNSUPPORTED");return map;
    }
    static Object auxiliary(Object carrier)throws ReflectiveOperationException{
        if(carrier==null)return null;
        return carrier.getClass()==TreeMap.class?get(carrier,TreeMap.class,"comparator",Comparator.class):
                carrier.getClass()==PriorityQueue.class?get(carrier,PriorityQueue.class,"comparator",Comparator.class):null;
    }
    /** Only mutable per-container storage is registered; enum universes are shared class metadata. */
    static Object arrayBacking(Object carrier)throws ReflectiveOperationException{
        if(vector(carrier))return get(carrier,Vector.class,"elementData",Object[].class);
        if(carrier!=null&&carrier.getClass()==PriorityQueue.class)return get(carrier,PriorityQueue.class,"queue",Object[].class);
        if(carrier!=null&&carrier.getClass()==EnumMap.class)return get(carrier,EnumMap.class,"vals",Object[].class);
        return null;
    }
    /** LBQ writers use these two locks in this order; a receiver monitor does not exclude them. */
    static boolean withConcurrentScope(Object carrier,SourceMaps.Action action)throws ReflectiveOperationException{
        if(lockFree(carrier)){action.run();return true;}
        if(!blocking(carrier))throw new IllegalArgumentException("ACTUAL_CONCURRENT_QUEUE_REQUIRED");
        ReentrantLock put=queueLock(carrier,"putLock"),take=queueLock(carrier,"takeLock");
        if(put==take)throw changed();
        if(!put.tryLock())return false;
        try{if(!take.tryLock())return false;try{action.run();return true;}finally{take.unlock();}}
        finally{put.unlock();}
    }
    private static ReentrantLock queueLock(Object carrier,String name)throws ReflectiveOperationException{
        Object lock=get(carrier,LinkedBlockingQueue.class,name,ReentrantLock.class);
        if(lock==null||lock.getClass()!=ReentrantLock.class)throw changed();return (ReentrantLock)lock;
    }
    private static void requireQueueLocks(Object carrier)throws ReflectiveOperationException{
        if(!queueLock(carrier,"putLock").isHeldByCurrentThread()||!queueLock(carrier,"takeLock").isHeldByCurrentThread())
            throw new IllegalAccessException("SOURCE_BLOCKING_QUEUE_WRITE_LOCKS_REQUIRED");
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
        final Row[] links;final Class<?> enumType;final Object[] universe;final long bits;final long[] words;final Object count;
        private IdentityHashMap<Object,Row> mappings;
        Image(Object carrier,Object backing,Object last,long revision,int size,int head,int tail,Object[] slots,List<Row> rows){
            this(carrier,backing,last,revision,size,head,tail,slots,rows,null,null,null,0,null,null);
        }
        Image(Object carrier,Object backing,Object last,long revision,int size,int head,int tail,Object[] slots,List<Row> rows,
              List<Row> links,Class<?> enumType,Object[] universe,long bits,long[] words,Object count){
            this.carrier=carrier;this.backing=backing;this.last=last;this.revision=revision;this.size=size;this.head=head;this.tail=tail;
            this.slots=slots;this.rows=rows.toArray(Row[]::new);
            this.links=links==null?null:links.toArray(Row[]::new);this.enumType=enumType;this.universe=universe;this.bits=bits;this.words=words;this.count=count;
        }
        Row mapping(Object key){
            if(mappings==null){
                var index=new IdentityHashMap<Object,Row>();for(Row row:rows)index.put(row.key,row);mappings=index;
            }
            return mappings.get(key);
        }
    }
    static long revision(Object carrier)throws ReflectiveOperationException{
        if(carrier.getClass()==LinkedList.class||vector(carrier))return Integer.toUnsignedLong(integer(carrier,AbstractList.class,"modCount"));
        if(carrier.getClass()==TreeMap.class||carrier.getClass()==IdentityHashMap.class||carrier.getClass()==PriorityQueue.class)
            return Integer.toUnsignedLong(integer(carrier,carrier.getClass(),"modCount"));
        if(supported(carrier))return 0; // These layouts use a full slot, bitmap or real node identity stamp.
        throw new IllegalArgumentException("ACTUAL_KNOWN_JDK_CONTAINER_REQUIRED");
    }
    static Image snapshot(Object carrier)throws ReflectiveOperationException{
        if(!supported(carrier))throw new IllegalArgumentException("ACTUAL_KNOWN_JDK_CONTAINER_REQUIRED");
        Image image;
        if(carrier.getClass()==TreeMap.class)image=tree(carrier);
        else if(carrier.getClass()==IdentityHashMap.class)image=identity(carrier);
        else if(carrier.getClass()==LinkedList.class)image=linked(carrier);
        else if(carrier.getClass()==ArrayDeque.class)image=deque(carrier);
        else if(vector(carrier)||carrier.getClass()==PriorityQueue.class)image=arraySequence(carrier);
        else if(carrier.getClass()==EnumMap.class)image=enumMap(carrier);
        else if(enumSet(carrier))image=enumBits(carrier);
        else if(blocking(carrier))image=blockingQueue(carrier);
        else image=concurrentQueue(carrier);
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
    private static Image arraySequence(Object carrier)throws ReflectiveOperationException{
        boolean priority=carrier.getClass()==PriorityQueue.class;Class<?> owner=priority?PriorityQueue.class:Vector.class;
        Object[] elements=(Object[])arrayBacking(carrier);int size=integer(carrier,owner,priority?"size":"elementCount");
        long revision=revision(carrier);if(elements==null||size<0||size>elements.length)throw changed();
        Object[] slots=elements.clone();var rows=new ArrayList<Row>();
        for(int index=0;index<size;index++){
            if(priority&&slots[index]==null)throw changed();rows.add(new Row(null,slots[index],null,index,null,null,null,null));
        }
        for(int index=size;index<slots.length;index++)if(slots[index]!=null)throw changed();
        return new Image(carrier,elements,auxiliary(carrier),revision,size,0,0,slots,rows);
    }
    /** Read ordinal storage, never enum values(), comparator(), equals() or a supplied iterator. */
    private static Object[] enumUniverse(Object carrier,Class<?> owner,String field,Class<?> enumType)throws ReflectiveOperationException{
        Object[] universe=(Object[])get(carrier,owner,field,Enum[].class);
        if(enumType==null||!enumType.isEnum()||universe==null)throw changed();
        for(int index=0;index<universe.length;index++){
            Object value=universe[index];if(value==null||!(value instanceof Enum<?>))throw changed();Class<?> type=value.getClass();
            // Enum.ordinal is final JDK code; a constant-specific class cannot supply a getter callback here.
            if(type!=enumType&&type.getSuperclass()!=enumType||((Enum<?>)value).ordinal()!=index)throw changed();
        }
        return universe;
    }
    private static Image enumMap(Object carrier)throws ReflectiveOperationException{
        Class<?> enumType=(Class<?>)get(carrier,EnumMap.class,"keyType",Class.class);
        Object[] universe=enumUniverse(carrier,EnumMap.class,"keyUniverse",enumType),values=(Object[])arrayBacking(carrier);
        int size=integer(carrier,EnumMap.class,"size");if(values==null||values.length!=universe.length||size<0||size>values.length)throw changed();
        Object nil=slot(EnumMap.class,"NULL",Object.class,true).get(null);if(nil==null)throw changed();
        Object[] slots=values.clone(),keys=universe.clone();var rows=new ArrayList<Row>();
        for(int index=0;index<slots.length;index++)if(slots[index]!=null)
            rows.add(new Row(keys[index],slots[index]==nil?null:slots[index],null,index,null,null,null,slots[index]));
        if(rows.size()!=size)throw changed();
        return new Image(carrier,values,universe,0,size,0,0,slots,rows,null,enumType,keys,0,null,nil);
    }
    private static Image enumBits(Object carrier)throws ReflectiveOperationException{
        Class<?> owner=carrier.getClass(),enumType=(Class<?>)get(carrier,EnumSet.class,"elementType",Class.class);
        Object[] universe=enumUniverse(carrier,EnumSet.class,"universe",enumType),keys=universe.clone();
        boolean regular=owner.getName().equals("java.util.RegularEnumSet");long bits=0;long[] actual=null,words=null;int size=0;
        if(regular){
            if(keys.length>64)throw changed();bits=slot(owner,"elements",long.class,false).getLong(carrier);
            if(keys.length<64&&(bits>>>keys.length)!=0)throw changed();size=Long.bitCount(bits);
        }else{
            actual=(long[])get(carrier,owner,"elements",long[].class);
            if(keys.length<=64||actual==null||actual.length!=((keys.length+63)>>>6))throw changed();words=actual.clone();
            if((keys.length&63)!=0&&(words[words.length-1]>>>(keys.length&63))!=0)throw changed();
            for(long word:words)size+=Long.bitCount(word);if(size!=integer(carrier,owner,"size"))throw changed();
        }
        var rows=new ArrayList<Row>();for(int index=0;index<keys.length;index++)
            if(((regular?bits:words[index>>>6])&(1L<<index))!=0)rows.add(new Row(keys[index],keys[index],null,index,null,null,null,null));
        return new Image(carrier,actual,universe,0,size,0,0,null,rows,null,enumType,keys,bits,words,null);
    }
    private static AtomicInteger queueCount(Object carrier)throws ReflectiveOperationException{
        Object count=get(carrier,LinkedBlockingQueue.class,"count",AtomicInteger.class);
        if(count==null||count.getClass()!=AtomicInteger.class)throw changed();return (AtomicInteger)count;
    }
    private static Image blockingQueue(Object carrier)throws ReflectiveOperationException{
        requireQueueLocks(carrier);Class<?> node=nested("java.util.concurrent.LinkedBlockingQueue$Node");
        Field item=slot(node,"item",Object.class,false),next=slot(node,"next",node,false);
        Object first=get(carrier,LinkedBlockingQueue.class,"head",node),last=get(carrier,LinkedBlockingQueue.class,"last",node);
        AtomicInteger count=queueCount(carrier);int size=count.get(),capacity=integer(carrier,LinkedBlockingQueue.class,"capacity");
        if(first==null||first.getClass()!=node||last==null||last.getClass()!=node||item.get(first)!=null||size<0||capacity<1||size>capacity)throw changed();
        var rows=new ArrayList<Row>();var links=new ArrayList<Row>();Object previous=first,current=next.get(first);
        links.add(new Row(null,null,first,0,null,current,null,null));
        while(current!=null){
            if(current.getClass()!=node||rows.size()>=size)throw changed();Object value=item.get(current),after=next.get(current);
            if(value==null||after==current)throw changed();Row row=new Row(null,value,current,rows.size(),previous,after,null,null);
            rows.add(row);links.add(row);previous=current;current=after;
        }
        if(rows.size()!=size||previous!=last)throw changed();
        return new Image(carrier,first,last,0,size,capacity,0,null,rows,links,null,null,0,null,count);
    }
    private static Object concurrentFirst(Object head,Class<?> node,Field prev)throws ReflectiveOperationException{
        if(head==null||head.getClass()!=node)throw changed();if(prev==null)return head;
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());Object current=head;
        for(;;){if(current.getClass()!=node||!seen.add(current))throw changed();Object before=prev.get(current);if(before==null)return current;current=before;}
    }
    private static Image concurrentQueue(Object carrier)throws ReflectiveOperationException{
        Class<?> owner=carrier.getClass(),node=nested(owner.getName()+"$Node");boolean deque=owner==ConcurrentLinkedDeque.class;
        Field item=slot(node,"item",Object.class,false),next=slot(node,"next",node,false),prev=deque?slot(node,"prev",node,false):null;
        Field headSlot=slot(owner,"head",node,false),tailSlot=slot(owner,"tail",node,false);
        Object head=headSlot.get(carrier),tail=tailSlot.get(carrier);if(tail==null||tail.getClass()!=node)throw changed();
        Object current=concurrentFirst(head,node,prev);var rows=new ArrayList<Row>();var links=new ArrayList<Row>();
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(current!=null){
            if(headSlot.get(carrier)!=head||tailSlot.get(carrier)!=tail||current.getClass()!=node||!seen.add(current))throw changed();
            Object value=item.get(current),after=next.get(current),before=prev==null?null:prev.get(current);
            if(after==current||before==current)throw changed();Row row=new Row(null,value,current,rows.size(),before,after,null,null);
            links.add(row);if(value!=null)rows.add(row);current=after;
        }
        return new Image(carrier,head,tail,0,rows.size(),0,0,null,rows,links,null,null,0,null,null);
    }
    private static boolean sameSlots(Object[] actual,Object backing,Object[] captured){
        if(actual!=backing||actual==null||actual.length!=captured.length)return false;
        for(int index=0;index<actual.length;index++)if(actual[index]!=captured[index])return false;return true;
    }
    private static boolean sameUniverse(Image image,Class<?> owner,String typeField,String universeField)throws ReflectiveOperationException{
        if(get(image.carrier,owner,typeField,Class.class)!=image.enumType)return false;
        Object[] actual=enumUniverse(image.carrier,owner,universeField,image.enumType);
        return sameSlots(actual,image.last,image.universe);
    }
    private static boolean sameLinks(Image image,Class<?> node,boolean deque)throws ReflectiveOperationException{
        Field item=slot(node,"item",Object.class,false),next=slot(node,"next",node,false),prev=deque?slot(node,"prev",node,false):null;
        for(Row row:image.links)if(item.get(row.node)!=row.value||next.get(row.node)!=row.right||prev!=null&&prev.get(row.node)!=row.left)return false;return true;
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
        if(vector(carrier)||type==PriorityQueue.class){
            if(integer(carrier,vector(carrier)?Vector.class:type,vector(carrier)?"elementCount":"size")!=image.size||auxiliary(carrier)!=image.last)return false;
            return sameSlots((Object[])arrayBacking(carrier),image.backing,image.slots);
        }
        if(type==EnumMap.class){
            return integer(carrier,type,"size")==image.size&&slot(type,"NULL",Object.class,true).get(null)==image.count
                    &&sameUniverse(image,type,"keyType","keyUniverse")&&sameSlots((Object[])arrayBacking(carrier),image.backing,image.slots);
        }
        if(enumSet(carrier)){
            if(!sameUniverse(image,EnumSet.class,"elementType","universe"))return false;
            if(image.words==null)return slot(type,"elements",long.class,false).getLong(carrier)==image.bits;
            long[] actual=(long[])get(carrier,type,"elements",long[].class);
            if(actual!=image.backing||integer(carrier,type,"size")!=image.size||actual.length!=image.words.length)return false;
            for(int index=0;index<actual.length;index++)if(actual[index]!=image.words[index])return false;return true;
        }
        if(blocking(carrier)){
            requireQueueLocks(carrier);Class<?> node=nested("java.util.concurrent.LinkedBlockingQueue$Node");
            return get(carrier,type,"head",node)==image.backing&&get(carrier,type,"last",node)==image.last
                    &&integer(carrier,type,"capacity")==image.head&&queueCount(carrier)==image.count&&((AtomicInteger)image.count).get()==image.size
                    &&sameLinks(image,node,false);
        }
        if(lockFree(carrier)){
            Class<?> node=nested(type.getName()+"$Node");boolean deque=type==ConcurrentLinkedDeque.class;
            if(get(carrier,type,"head",node)!=image.backing||get(carrier,type,"tail",node)!=image.last)return false;
            Object first=concurrentFirst(image.backing,node,deque?slot(node,"prev",node,false):null);
            return image.links.length!=0&&first==image.links[0].node&&sameLinks(image,node,deque);
        }
        Object[] actual;
        if(type==IdentityHashMap.class){
            if(integer(carrier,type,"size")!=image.size)return false;actual=(Object[])get(carrier,type,"table",Object[].class);
        }else{
            if(integer(carrier,type,"head")!=image.head||integer(carrier,type,"tail")!=image.tail)return false;
            actual=(Object[])get(carrier,type,"elements",Object[].class);
        }
        return sameSlots(actual,image.backing,image.slots);
    }
    static String removalGap(Image image,Row row){
        // Removing the last heap leaf performs no comparison. Other positions may require target code even for natural order.
        return image.carrier.getClass()==PriorityQueue.class&&row.position!=image.size-1?"SOURCE_PRIORITY_QUEUE_REHEAPIFY_CALLBACK_REQUIRED":"";
    }
    private static boolean removeConcurrent(Image image,Row row)throws ReflectiveOperationException{
        Class<?> owner=image.carrier.getClass(),node=nested(owner.getName()+"$Node");
        Field item=slot(node,"item",Object.class,false);
        VarHandle value=MethodHandles.privateLookupIn(node,MethodHandles.lookup()).findVarHandle(node,"item",Object.class);
        if(!value.compareAndSet(row.node,row.value,null)){
            if(item.get(row.node)!=null)throw changed();return false;
        }
        if(owner==ConcurrentLinkedDeque.class)operation(owner,"unlink",void.class,node).invoke(image.carrier,row.node);
        // A CLQ tombstone is its native logical deletion; keeping links avoids touching a concurrent producer's new node.
        if(item.get(row.node)!=null)throw new IllegalStateException("SOURCE_CONCURRENT_QUEUE_REMOVAL_READBACK_FAILED");return true;
    }
    record Removal(int count,Image after) { boolean removed(){return count!=0;} }
    /** Mechanical stage only; the caller supplies this operation's freshly
     * selected positions while holding the actual Vector writer monitor. */
    static Image compactVectorObserved(Image image,int first,boolean[] selected,int removed)throws ReflectiveOperationException{
        if(!vector(image.carrier)||!Thread.holdsLock(image.carrier))throw new IllegalAccessException("ACTUAL_VECTOR_MONITOR_REQUIRED");
        if(first<0||first>image.rows.length-selected.length)throw new IllegalArgumentException("ACTUAL_VECTOR_RANGE_REQUIRED");
        int expected=0;for(boolean item:selected)if(item)expected++;
        // Source inspection can run other observers. Recheck every actual slot,
        // including unselected rows and spare capacity, before any write.
        if(removed<=0||removed!=expected||!unchanged(image))throw changed();
        Object[] elements=(Object[])image.backing;int write=first,end=first+selected.length;
        slot(AbstractList.class,"modCount",int.class,false).setInt(image.carrier,(int)image.revision+1);
        for(int read=first;read<image.size;read++){
            if(read<end&&selected[read-first])continue;
            if(write!=read)elements[write]=image.slots[read];write++;
        }
        Arrays.fill(elements,write,image.size,null);
        slot(Vector.class,"elementCount",int.class,false).setInt(image.carrier,write);
        Image after=snapshot(image.carrier);
        if(after.backing!=image.backing||after.size!=image.size-removed)throw new IllegalStateException("SOURCE_VECTOR_BATCH_READBACK_FAILED");
        int at=0;
        for(int before=0;before<image.size;before++){
            if(before>=first&&before<end&&selected[before-first])continue;
            if(after.rows[at++].value!=image.rows[before].value)throw new IllegalStateException("SOURCE_JDK_UNSELECTED_ELEMENT_CHANGED");
        }
        return after;
    }
    /** The verified readback is also the next traversal image; it is never an ownership decision. */
    static Removal removeObserved(Image image,Row row)throws ReflectiveOperationException{
        boolean present=false;for(Row actual:image.rows)if(actual==row){present=true;break;}
        if(!present||!unchanged(image))throw changed();Object carrier=image.carrier;
        if(!removalGap(image,row).isEmpty())return new Removal(0,null);
        if(lockFree(carrier))return new Removal(removeConcurrent(image,row)?1:0,null);
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
        else if(carrier.getClass()==ArrayDeque.class)operation(ArrayDeque.class,"delete",boolean.class,int.class).invoke(carrier,row.position);
        else if(vector(carrier))operation(Vector.class,"removeElementAt",void.class,int.class).invoke(carrier,row.position);
        else if(carrier.getClass()==PriorityQueue.class){
            ((Object[])image.backing)[row.position]=null;
            slot(PriorityQueue.class,"size",int.class,false).setInt(carrier,image.size-1);
            slot(PriorityQueue.class,"modCount",int.class,false).setInt(carrier,(int)image.revision+1);
        }else if(carrier.getClass()==EnumMap.class){
            ((Object[])image.backing)[row.position]=null;slot(EnumMap.class,"size",int.class,false).setInt(carrier,image.size-1);
        }else if(enumSet(carrier)){
            if(image.words==null)slot(carrier.getClass(),"elements",long.class,false).setLong(carrier,image.bits&~(1L<<row.position));
            else{
                ((long[])image.backing)[row.position>>>6]&=~(1L<<row.position);
                slot(carrier.getClass(),"size",int.class,false).setInt(carrier,image.size-1);
            }
        }else if(blocking(carrier))operation(LinkedBlockingQueue.class,"unlink",void.class,nested("java.util.concurrent.LinkedBlockingQueue$Node"),
                nested("java.util.concurrent.LinkedBlockingQueue$Node")).invoke(carrier,row.node,row.left);
        else throw new IllegalArgumentException("ACTUAL_KNOWN_JDK_CONTAINER_REQUIRED");
        Image after=snapshot(carrier);
        if(after.size!=image.size-1)throw new IllegalStateException("SOURCE_JDK_REMOVAL_READBACK_FAILED");
        if(map(carrier)){
            for(Row before:image.rows)if(before!=row){
                Row retained=after.mapping(before.key);
                if(retained==null||retained.value!=before.value)throw new IllegalStateException("SOURCE_JDK_UNSELECTED_MAPPING_CHANGED");
            }
        }else{
            int at=0;for(Row before:image.rows)if(before!=row&&after.rows[at++].value!=before.value)
                throw new IllegalStateException("SOURCE_JDK_UNSELECTED_ELEMENT_CHANGED");
        }
        return new Removal(1,after);
    }
    static boolean clear(Image image)throws ReflectiveOperationException{
        if(lockFree(image.carrier)){
            // Only the captured identities were classified private. A later offer is never swept into this clear.
            for(Row row:image.rows)removeConcurrent(image,row);
            return snapshot(image.carrier).size==0;
        }
        if(!unchanged(image))throw changed();
        if(map(image.carrier))((Map<?,?>)image.carrier).clear();else ((Collection<?>)image.carrier).clear();
        if(snapshot(image.carrier).size!=0)throw new IllegalStateException("GROUP_PRIVATE_CONTAINER_CLEAR_READBACK_FAILED");
        return true;
    }
    private static IllegalAccessException changed(){return new IllegalAccessException("SOURCE_JDK_CONTAINER_CHANGED_OR_LAYOUT_UNAVAILABLE");}
}
