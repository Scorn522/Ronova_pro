package dev.ronova.pro;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Actual standard storage reached from one capability value; no container methods are invoked. */
final class EquipmentCarrierLayouts {
    record Edge(Object value,boolean storage) { }
    record Slot(Object receiver,Field field,long offset,int width,boolean structural,Object value) { }
    /** Bounds describe logical slots, not the unused capacity of the actual array. */
    record Range(Object array,int from,int to,int stride,boolean structural) { }
    /** elements is a list-value snapshot only, and must never be registered as a carrier. */
    record Layout(boolean complete,boolean shared,List<Edge> children,List<Slot> slots,List<Range> arrays,Object[] elements,List<ArrayImage> images) { }
    /** Every child read and the final relationship check use the same actual array-slot image. */
    static final class ArrayImage {
        private static final java.lang.invoke.VarHandle ELEMENT=MethodHandles.arrayElementVarHandle(Object[].class);
        final Object[] array,values;final java.util.BitSet used=new java.util.BitSet();
        ArrayImage(Object[] array){this.array=array;values=array.clone();}
        Object at(int index){used.set(index);return values[index];}
        Object[] all(){used.set(0,values.length);return values;}
        boolean current(){for(int at=used.nextSetBit(0);at>=0;at=used.nextSetBit(at+1))if(ELEMENT.getVolatile(array,at)!=values[at])return false;return true;}
    }

    private static final int LIMIT=4096;
    private static final String C="java.util.Collections$",I="java.util.ImmutableCollections$",G="com.google.common.collect.",A="java.util.concurrent.atomic.";
    private static final Set<String> JDK=Set.of(
            "java.util.ArrayList","java.util.ArrayList$SubList","java.util.LinkedList","java.util.Arrays$ArrayList",
            "java.util.HashMap","java.util.LinkedHashMap","java.util.IdentityHashMap","java.util.EnumMap","java.util.HashSet","java.util.LinkedHashSet","java.util.ArrayDeque","java.util.concurrent.ConcurrentHashMap",
            I+"ListN",I+"List12",I+"SubList",I+"SetN",I+"Set12",I+"MapN",I+"Map1",
            C+"SingletonList",C+"SingletonSet",C+"SingletonMap",C+"EmptyList",C+"EmptySet",C+"EmptyMap",C+"CopiesList",
            C+"UnmodifiableCollection",C+"UnmodifiableList",C+"UnmodifiableRandomAccessList",C+"UnmodifiableSet",C+"UnmodifiableMap",
            C+"UnmodifiableSortedSet",C+"UnmodifiableNavigableSet",C+"UnmodifiableSortedMap",C+"UnmodifiableNavigableMap",
            C+"SynchronizedCollection",C+"SynchronizedList",C+"SynchronizedRandomAccessList",C+"SynchronizedSet",C+"SynchronizedMap",
            C+"SynchronizedSortedSet",C+"SynchronizedNavigableSet",C+"SynchronizedSortedMap",C+"SynchronizedNavigableMap",
            A+"AtomicReference",A+"AtomicInteger",A+"AtomicLong",A+"AtomicBoolean",A+"AtomicReferenceArray",A+"AtomicIntegerArray",A+"AtomicLongArray",
            A+"AtomicMarkableReference",A+"AtomicStampedReference");
    private static final Set<String> GUAVA=Set.of(G+"RegularImmutableList",G+"SingletonImmutableList",G+"ImmutableList$SubList",G+"ImmutableList$ReverseImmutableList",G+"RegularImmutableAsList",
            G+"RegularImmutableSet",G+"SingletonImmutableSet",G+"RegularImmutableMap",G+"SingletonImmutableBiMap",G+"JdkBackedImmutableMap",G+"JdkBackedImmutableSet");
    private static final MethodHandle FIELDS,READ,STATIC;
    static {
        try {
            Class<?> bridge=Class.forName("dev.ronova.pro.bootstrap.CarrierBridge",false,null);var lookup=MethodHandles.publicLookup();
            FIELDS=lookup.findStatic(bridge,"fields",MethodType.methodType(Object[][].class,Object.class));
            READ=lookup.findStatic(bridge,"read",MethodType.methodType(Object.class,Object.class,Field.class));
            STATIC=lookup.findStatic(bridge,"staticValue",MethodType.methodType(Object.class,Class.class,String.class,String.class));
        }catch(ReflectiveOperationException unavailable){throw new ExceptionInInitializerError(unavailable);}
    }
    private EquipmentCarrierLayouts(){ }

    /** Exact identities only: a custom subclass is not a trusted framework frame. */
    static boolean known(Class<?> type){
        if(type==null)return false;String name=type.getName();
        if(JDK.contains(name))return type.getClassLoader()==null&&type.getModule()==Object.class.getModule();
        if(GUAVA.contains(name))return type.getClassLoader()==com.google.common.collect.ImmutableList.class.getClassLoader()
                &&type.getModule()==com.google.common.collect.ImmutableList.class.getModule();
        return type==net.minecraft.core.NonNullList.class;
    }
    private static Class<?> base(Class<?> type){for(Class<?> at=type;at!=null;at=at.getSuperclass())if(known(at))return at;return null;}
    static Layout layout(Object value){
        if(value==null||base(value.getClass())==null)return null;
        Builder result=new Builder(value);Item[] sequence=result.container(value,false);
        Object[] elements=null;if(sequence!=null){elements=new Object[sequence.length];for(int at=0;at<sequence.length;at++)elements[at]=sequence[at].value;}
        List<Slot> slots=new ArrayList<>();for(Meta meta:result.metadata.values()){
            meta.stable();for(Row row:meta.rows)if(row.recorded)slots.add(new Slot(meta.receiver,row.field,row.offset,row.width,!row.payload,row.value));
        }
        for(ArrayImage image:result.images.values())if(!image.current())throw unsupported("CHANGED_ARRAY_SLOT");
        return new Layout(result.complete,result.shared,List.copyOf(result.edges),List.copyOf(slots),List.copyOf(result.ranges),elements,List.copyOf(result.images.values()));
    }
    private static Object[][] fields(Object receiver){
        try{return (Object[][])FIELDS.invokeExact(receiver);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_CARRIER_FIELDS_UNAVAILABLE",failure);}
    }
    private static Object read(Object receiver,Field field){
        try{return (Object)READ.invokeExact(receiver,field);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_CARRIER_READ_UNAVAILABLE",failure);}
    }
    private static Object staticValue(Class<?> declaring,String name,String descriptor){
        try{return (Object)STATIC.invokeExact(declaring,name,descriptor);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException("EQUIPMENT_CARRIER_STATIC_UNAVAILABLE",failure);}
    }
    private static IllegalStateException unsupported(String reason){return new IllegalStateException("EQUIPMENT_CARRIER_LAYOUT_UNRESOLVED:"+reason);}
    private static Class<?> declaring(Class<?> type,String name){for(Class<?> at=type;at!=null;at=at.getSuperclass())if(at.getName().equals(name))return at;throw unsupported(name);}
    private static boolean jdk(Class<?> type,String name){return type!=null&&type.getName().equals(name)&&type.getClassLoader()==null&&type.getModule()==Object.class.getModule();}
    private static boolean guava(Class<?> type,String name){return type!=null&&type.getName().equals(name)&&type.getClassLoader()==com.google.common.collect.ImmutableList.class.getClassLoader()&&type.getModule()==com.google.common.collect.ImmutableList.class.getModule();}
    private static boolean named(String name,String... choices){for(String choice:choices)if(name.equals(choice))return true;return false;}
    private static boolean listName(String name){return named(name,"java.util.ArrayList","java.util.ArrayList$SubList","java.util.LinkedList","java.util.Arrays$ArrayList",I+"ListN",I+"List12",I+"SubList",C+"SingletonList",C+"EmptyList",C+"CopiesList",
            C+"UnmodifiableList",C+"UnmodifiableRandomAccessList",C+"SynchronizedList",C+"SynchronizedRandomAccessList",G+"RegularImmutableList",G+"SingletonImmutableList",G+"ImmutableList$SubList",G+"ImmutableList$ReverseImmutableList",G+"RegularImmutableAsList","net.minecraft.core.NonNullList");}
    private static int number(Object value){if(value instanceof Integer integer)return integer;throw unsupported("INTEGER_FIELD");}
    private static int bound(int size){if(size<0||size>LIMIT)throw unsupported("LOGICAL_SIZE:"+size);return size;}
    private static boolean samePrimitive(Class<?> type,Object first,Object second){
        if(type==boolean.class)return first instanceof Boolean a&&second instanceof Boolean b&&a.booleanValue()==b.booleanValue();
        if(type==byte.class)return first instanceof Byte a&&second instanceof Byte b&&a.byteValue()==b.byteValue();
        if(type==short.class)return first instanceof Short a&&second instanceof Short b&&a.shortValue()==b.shortValue();
        if(type==char.class)return first instanceof Character a&&second instanceof Character b&&a.charValue()==b.charValue();
        if(type==int.class)return first instanceof Integer a&&second instanceof Integer b&&a.intValue()==b.intValue();
        if(type==long.class)return first instanceof Long a&&second instanceof Long b&&a.longValue()==b.longValue();
        if(type==float.class)return first instanceof Float a&&second instanceof Float b&&Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(type==double.class)return first instanceof Double a&&second instanceof Double b&&Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
        return false;
    }
    static boolean sameValue(Class<?> type,Object first,Object second){return type.isPrimitive()?samePrimitive(type,first,second):first==second;}

    private static final class Row {
        final Field field;final long offset;final int width;final Object value;boolean payload,recorded;
        Row(Object[] data){
            if(data==null||data.length!=8||!(data[0] instanceof Field actual)||data[1]!=actual.getDeclaringClass()||!(data[2] instanceof String name)||!name.equals(actual.getName())
                    ||!(data[3] instanceof String descriptor)||!descriptor.equals(actual.getType().descriptorString())||data[4]!=actual.getType()||!(data[5] instanceof Long position)||position<0
                    ||!(data[6] instanceof Integer bytes)||bytes<=0||Modifier.isStatic(actual.getModifiers()))throw unsupported("FIELD_METADATA");
            field=actual;offset=position;width=bytes;value=data[7];
        }
    }
    private static final class Meta {
        final Object receiver;final Row[] rows;
        Meta(Object receiver){
            this.receiver=receiver;Object[][] actual=fields(receiver);if(actual==null)throw unsupported("NULL_FIELDS");rows=new Row[actual.length];Class<?> standard=base(receiver.getClass());
            for(int at=0;at<actual.length;at++){
                Row row=new Row(actual[at]);if(!row.field.getDeclaringClass().isInstance(receiver))throw unsupported("FIELD_RECEIVER");
                // A subclass's own fields remain for the main graph to classify.
                row.recorded=standard==null||row.field.getDeclaringClass().isAssignableFrom(standard);rows[at]=row;
            }
        }
        Row row(String owner,String... names){
            Class<?> type=declaring(receiver.getClass(),owner);Row found=null;
            for(Row row:rows)if(row.field.getDeclaringClass()==type&&named(row.field.getName(),names)){if(found!=null)throw unsupported("AMBIGUOUS_FIELD:"+owner);found=row;}
            if(found==null)throw unsupported("FIELD:"+owner+":"+names[0]);return found;
        }
        Object get(String owner,String... names){return row(owner,names).value;}
        void stable(){for(Row row:rows)if(row.recorded){Object current=read(receiver,row.field);if(row.field.getType().isPrimitive()){if(!samePrimitive(row.field.getType(),row.value,current))throw unsupported("CHANGED_SCALAR:"+row.field.getName());}else if(row.value!=current)throw unsupported("CHANGED_REFERENCE:"+row.field.getName());}}
    }
    private record Item(Object value,Meta meta,Row field,Object array,int index) { }

    private static final class Builder {
        final Object root;
        final IdentityHashMap<Object,Meta> metadata=new IdentityHashMap<>();
        final IdentityHashMap<Object[],ArrayImage> images=new IdentityHashMap<>();int arraySlots;
        final IdentityHashMap<Object,Boolean> visited=new IdentityHashMap<>(),storage=new IdentityHashMap<>(),payload=new IdentityHashMap<>();
        final List<Edge> edges=new ArrayList<>();final List<Range> ranges=new ArrayList<>();boolean complete=true,shared;
        Builder(Object root){this.root=root;}
        Meta meta(Object object){
            Meta before=metadata.get(object);if(before!=null)return before;
            if(metadata.size()>=LIMIT)throw unsupported("STRUCTURE_CAPACITY");Meta result=new Meta(object);metadata.put(object,result);return result;
        }
        void store(Object object){if(object!=null&&object!=root&&storage.put(object,Boolean.TRUE)==null){if(edges.size()>=LIMIT)throw unsupported("EDGE_CAPACITY");edges.add(new Edge(object,true));}}
        void data(Object object){if(object!=null&&object!=root&&payload.put(object,Boolean.TRUE)==null){if(edges.size()>=LIMIT)throw unsupported("EDGE_CAPACITY");edges.add(new Edge(object,false));}}
        void range(Object array,int from,int to,int stride,boolean structural){
            if(array==null||!array.getClass().isArray()||stride<=0||from<0||to<from||to>java.lang.reflect.Array.getLength(array))throw unsupported("ARRAY_RANGE");
            // Empty shared arrays have no instance payload or writable slots.
            if(java.lang.reflect.Array.getLength(array)==0)return;store(array);
            if(!ranges.isEmpty()){
                Range before=ranges.get(ranges.size()-1);
                if(before.array==array&&before.stride==stride&&before.structural==structural&&stride==1&&(before.to==from||to==before.from)){
                    ranges.set(ranges.size()-1,new Range(array,Math.min(before.from,from),Math.max(before.to,to),stride,structural));return;
                }
            }
            if(ranges.size()>=LIMIT)throw unsupported("RANGE_CAPACITY");ranges.add(new Range(array,from,to,stride,structural));
        }
        Object[] array(Object value){if(!(value instanceof Object[] actual))throw unsupported("REFERENCE_ARRAY");return actual;}
        ArrayImage image(Object[] actual){
            ArrayImage before=images.get(actual);if(before!=null)return before;
            if(actual.length>LIMIT*16-arraySlots)throw unsupported("ARRAY_SLOT_CAPACITY");arraySlots+=actual.length;
            ArrayImage result=new ArrayImage(actual);images.put(actual,result);return result;
        }
        Object entry(Object[] actual,int index){return image(actual).at(index);}
        Object[] entries(Object[] actual){return image(actual).all();}
        Item[] arrayItems(Object value,int count){
            Object[] array=array(value);bound(count);if(count>array.length)throw unsupported("ARRAY_LOGICAL_SIZE");
            if(array.length!=0)store(array);if(count==0)range(array,0,0,1,false);
            Object[] snapshot=count==0?null:image(array).values;
            Item[] items=new Item[count];for(int at=0;at<count;at++)items[at]=new Item(snapshot[at],null,null,array,at);return items;
        }
        Item fieldItem(Meta owner,String declaring,String... names){Row row=owner.row(declaring,names);return new Item(row.value,owner,row,null,-1);}
        void observe(Item item){if(item.array!=null&&entry((Object[])item.array,item.index)!=item.value)throw unsupported("MIXED_ARRAY_SLOT");}
        void emit(Item[] items){for(Item item:items){observe(item);data(item.value);if(item.field!=null)item.field.payload=true;else range(item.array,item.index,item.index+1,1,false);}}
        void child(Object object,boolean setKeys){
            if(object==null)return;Class<?> base=base(object.getClass());
            if(base==null){complete=false;data(object);return;}if(sharedValue(object,base))return;store(object);
            if(base!=object.getClass()){complete=false;data(object);}
            container(object,setKeys);
        }
        Item[] container(Object object,boolean setKeys){
            if(object==null)return null;Class<?> type=base(object.getClass());if(type==null){complete=false;data(object);return null;}
            if(type!=object.getClass())complete=false;
            if(sharedValue(object,type)){if(object==root)shared=true;return listName(type.getName())?new Item[0]:null;}
            if(visited.put(object,Boolean.TRUE)!=null)return null;if(visited.size()>LIMIT)throw unsupported("CONTAINER_CAPACITY");
            String name=type.getName();Meta value=meta(object);
            if(listName(name)){Item[] items=list(object,type,new IdentityHashMap<>());if(items==null){complete=false;return null;}emit(items);return items;}
            if(named(name,"java.util.HashMap","java.util.LinkedHashMap")){hashMap(value,setKeys);return null;}
            if(name.equals("java.util.concurrent.ConcurrentHashMap")){concurrentMap(value,setKeys);return null;}
            if(name.equals("java.util.ArrayDeque")){deque(value);return null;}
            if(name.equals("java.util.IdentityHashMap")){identityMap(value,setKeys);return null;}
            if(name.equals("java.util.EnumMap")){enumMap(value,setKeys);return null;}
            if(named(name,"java.util.HashSet","java.util.LinkedHashSet")){Object map=value.get("java.util.HashSet","map");
                if(map==null||!jdk(map.getClass(),"java.util.HashMap")&&!jdk(map.getClass(),"java.util.LinkedHashMap"))throw unsupported("SET_BACKING");child(map,true);return null;}
            if(named(name,I+"Set12",C+"SingletonSet",G+"SingletonImmutableSet")){
                if(name.equals(I+"Set12")){Item first=fieldItem(value,name,"e0"),second=fieldItem(value,name,"e1");emit(new Item[]{first});if(second.value!=immutableEmpty())emit(new Item[]{second});}
                else emit(new Item[]{fieldItem(value,name,"element")});return null;
            }
            if(named(name,I+"SetN",G+"RegularImmutableSet")){
                Object[] elements=array(value.get(name,"elements"));int count=0;
                range(elements,0,elements.length,1,true);for(Object entry:entries(elements))if(entry!=null){data(entry);count++;}
                if(name.equals(I+"SetN")&&count!=number(value.get(name,"size")))throw unsupported("SET_SIZE");
                if(name.equals(G+"RegularImmutableSet")){Object table=value.get(name,"table");if(table!=null){Object[] actual=array(table);range(actual,0,actual.length,1,true);for(Object entry:entries(actual))if(entry!=null)data(entry);}}
                return null;
            }
            if(named(name,I+"Map1",C+"SingletonMap",G+"SingletonImmutableBiMap")){
                String key=name.equals(I+"Map1")?"k0":name.equals(C+"SingletonMap")?"k":"singleKey",field=name.equals(I+"Map1")?"v0":name.equals(C+"SingletonMap")?"v":"singleValue";
                data(value.get(name,key));emit(new Item[]{fieldItem(value,name,field)});return null;
            }
            if(name.equals(I+"MapN")){immutableMap(value);return null;}
            if(name.equals(G+"RegularImmutableMap")){guavaMap(value);return null;}
            if(name.equals(G+"JdkBackedImmutableMap")){child(value.get(name,"delegateMap"),false);guavaEntriesList(value.get(name,"entries"));return null;}
            if(name.equals(G+"JdkBackedImmutableSet")){child(value.get(name,"delegate"),true);child(value.get(name,"delegateList"),false);return null;}
            if(name.startsWith(C+"Unmodifiable")||name.startsWith(C+"Synchronized")){
                boolean map=name.contains("Map"),sync=name.startsWith(C+"Synchronized");String owner=C+(sync?"Synchronized":"Unmodifiable")+(map?"Map":"Collection");
                child(value.get(owner,map?"m":"c"),setKeys);return null;
            }
            if(name.startsWith(A)){
                if(named(name,A+"AtomicReference",A+"AtomicInteger",A+"AtomicLong",A+"AtomicBoolean")){Row actual=value.row(name,"value");actual.payload=true;if(name.equals(A+"AtomicReference"))data(actual.value);return null;}
                if(named(name,A+"AtomicReferenceArray",A+"AtomicIntegerArray",A+"AtomicLongArray")){
                    Object actual=value.get(name,"array");int length=java.lang.reflect.Array.getLength(actual);range(actual,0,length,1,false);
                    if(name.equals(A+"AtomicReferenceArray"))for(Object entry:entries(array(actual)))data(entry);return null;
                }
                if(named(name,A+"AtomicMarkableReference",A+"AtomicStampedReference")){
                    Object pair=value.get(name,"pair");if(pair==null||!jdk(pair.getClass(),name+"$Pair"))throw unsupported("ATOMIC_PAIR");store(pair);Meta p=meta(pair);
                    emit(new Item[]{fieldItem(p,name+"$Pair","reference")});p.row(name+"$Pair",name.equals(A+"AtomicMarkableReference")?"mark":"stamp").payload=true;return null;
                }
            }
            throw unsupported(name);
        }
        Item[] list(Object object,Class<?> type,IdentityHashMap<Object,Boolean> path){
            if(path.put(object,Boolean.TRUE)!=null||path.size()>LIMIT)throw unsupported("LIST_CYCLE");
            try{
                if(sharedValue(object,type)){if(object==root)shared=true;return new Item[0];}
                if(type!=object.getClass()){complete=false;data(object);}Meta value=meta(object);String name=type.getName();
                if(name.equals("java.util.ArrayList")){return arrayItems(value.get(name,"elementData"),bound(number(value.get(name,"size"))));}
                if(name.equals("java.util.Arrays$ArrayList")){Object[] array=array(value.get(name,"a"));return arrayItems(array,array.length);}
                if(named(name,I+"ListN",G+"RegularImmutableList")){
                    Object[] array=array(value.get(name,name.equals(I+"ListN")?"elements":"array"));return arrayItems(array,array.length);
                }
                if(name.equals(I+"List12")){Item first=fieldItem(value,name,"e0"),second=fieldItem(value,name,"e1");return second.value==immutableEmpty()?new Item[]{first}:new Item[]{first,second};}
                if(named(name,C+"SingletonList",G+"SingletonImmutableList"))return new Item[]{fieldItem(value,name,"element")};
                if(name.equals(C+"EmptyList")){if(object==root)shared=true;return new Item[0];}
                if(name.equals(C+"CopiesList")){int count=bound(number(value.get(name,"n")));Item item=fieldItem(value,name,"element");Item[] result=new Item[count];for(int at=0;at<count;at++)result[at]=item;return result;}
                if(name.equals("java.util.LinkedList")){
                    return linkedList(value);
                }
                if(named(name,"java.util.ArrayList$SubList",I+"SubList",G+"ImmutableList$SubList")){
                    Object parent=value.get(name,name.equals(G+"ImmutableList$SubList")?"this$0":"root");int offset=number(value.get(name,"offset")),count=bound(number(value.get(name,name.equals(G+"ImmutableList$SubList")?"length":"size")));
                    Item[] all=delegateList(parent,path);if(all==null)return null;if(offset<0||offset>all.length-count)throw unsupported("SUBLIST_RANGE");
                    Item[] result=new Item[count];System.arraycopy(all,offset,result,0,count);return result;
                }
                if(name.equals(G+"ImmutableList$ReverseImmutableList")){
                    Item[] all=delegateList(value.get(name,"forwardList"),path);if(all==null)return null;Item[] result=new Item[all.length];for(int at=0;at<all.length;at++)result[at]=all[all.length-1-at];return result;
                }
                if(name.equals(G+"RegularImmutableAsList"))return delegateList(value.get(name,"delegateList"),path);
                if(named(name,C+"UnmodifiableList",C+"UnmodifiableRandomAccessList",C+"SynchronizedList",C+"SynchronizedRandomAccessList")){
                    String prefix=C+(name.startsWith(C+"Synchronized")?"Synchronized":"Unmodifiable");Object delegate=value.get(prefix+"List","list");
                    if(delegate!=value.get(prefix+"Collection","c"))throw unsupported("WRAPPER_LIST_ALIAS");return delegateList(delegate,path);
                }
                if(name.equals("net.minecraft.core.NonNullList")){
                    // The main graph discards EMPTY/global sentinels; a custom
                    // mutable default is still held even when the list is empty.
                    emit(new Item[]{fieldItem(value,name,"defaultValue","f_122774_")});return delegateList(value.get(name,"list","f_122773_"),path);
                }
                return null;
            }finally{path.remove(object);}
        }
        Item[] delegateList(Object object,IdentityHashMap<Object,Boolean> path){
            if(object==null)throw unsupported("NULL_LIST_BACKING");Class<?> type=base(object.getClass());
            if(type==null||!listName(type.getName())){complete=false;data(object);return null;}if(sharedValue(object,type))return new Item[0];store(object);return list(object,type,path);
        }
        boolean sharedValue(Object object,Class<?> type){
            String name=type.getName();if(named(name,C+"EmptyList",C+"EmptySet",C+"EmptyMap"))return true;
            if(name.equals(G+"RegularImmutableList"))return object==staticValue(type,"EMPTY","Lcom/google/common/collect/ImmutableList;");
            if(name.equals(G+"RegularImmutableSet"))return object==staticValue(type,"EMPTY","Lcom/google/common/collect/RegularImmutableSet;");
            if(name.equals(G+"RegularImmutableMap"))return object==staticValue(type,"EMPTY","Lcom/google/common/collect/ImmutableMap;");
            if(named(name,I+"ListN",I+"SetN",I+"MapN")){
                Class<?> owner=immutableOwner();
                if(name.equals(I+"ListN"))return object==staticValue(owner,"EMPTY_LIST","Ljava/util/ImmutableCollections$ListN;")||object==staticValue(owner,"EMPTY_LIST_NULLS","Ljava/util/ImmutableCollections$ListN;");
                return name.equals(I+"SetN")?object==staticValue(owner,"EMPTY_SET","Ljava/util/ImmutableCollections$SetN;"):object==staticValue(owner,"EMPTY_MAP","Ljava/util/ImmutableCollections$MapN;");
            }
            return false;
        }
        Class<?> immutableOwner(){try{return Class.forName("java.util.ImmutableCollections",false,null);}catch(ClassNotFoundException unavailable){throw new IllegalStateException(unavailable);}}
        Object immutableEmpty(){
            return staticValue(immutableOwner(),"EMPTY","Ljava/lang/Object;");
        }
        Item[] linkedList(Meta value){
            bound(number(value.get("java.util.LinkedList","size")));
            List<Item> logical=new ArrayList<>();IdentityHashMap<Object,Boolean> forward=new IdentityHashMap<>();
            Object first=value.get("java.util.LinkedList","first"),last=value.get("java.util.LinkedList","last");
            for(Object node=first;node!=null;){
                if(!jdk(node.getClass(),"java.util.LinkedList$Node")||forward.put(node,Boolean.TRUE)!=null||forward.size()>LIMIT)throw unsupported("LINKED_LIST_NEXT");
                store(node);Meta entry=meta(node);logical.add(fieldItem(entry,"java.util.LinkedList$Node","item"));node=entry.get("java.util.LinkedList$Node","next");
            }
            // Original link/unlink bodies update head, tail, prev and size in
            // separate stores. Retain their actual reachable nodes throughout.
            List<Object> pending=new ArrayList<>();if(first!=null)pending.add(first);if(last!=null)pending.add(last);
            IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
            for(int at=0;at<pending.size();at++){
                Object node=pending.get(at);if(seen.put(node,Boolean.TRUE)!=null)continue;
                if(!jdk(node.getClass(),"java.util.LinkedList$Node")||seen.size()>LIMIT)throw unsupported("LINKED_LIST_NODE");store(node);Meta entry=meta(node);
                if(!forward.containsKey(node))emit(new Item[]{fieldItem(entry,"java.util.LinkedList$Node","item")});
                Object next=entry.get("java.util.LinkedList$Node","next"),previous=entry.get("java.util.LinkedList$Node","prev");
                if(next!=null&&!seen.containsKey(next))pending.add(next);if(previous!=null&&!seen.containsKey(previous))pending.add(previous);
                if(pending.size()>LIMIT*2)throw unsupported("LINKED_LIST_CAPACITY");
            }
            Item[] result=new Item[logical.size()];for(int at=0;at<result.length;at++)result[at]=logical.get(at);return result;
        }
        void hashMap(Meta value,boolean keys){
            Object table=value.get("java.util.HashMap","table");bound(number(value.get("java.util.HashMap","size")));List<Object> pending=new ArrayList<>();
            if(table!=null){Object[] buckets=array(table);range(buckets,0,buckets.length,1,true);for(Object head:entries(buckets))if(head!=null)pending.add(head);}
            if(base(value.receiver.getClass())==java.util.LinkedHashMap.class){Object head=value.get("java.util.LinkedHashMap","head"),tail=value.get("java.util.LinkedHashMap","tail");if(head!=null)pending.add(head);if(tail!=null)pending.add(tail);}
            IdentityHashMap<Object,Boolean> nodes=new IdentityHashMap<>();
            for(int at=0;at<pending.size();at++){
                Object node=pending.get(at);if(nodes.put(node,Boolean.TRUE)!=null)continue;
                Class<?> type=node.getClass();if(!jdk(type,"java.util.HashMap$Node")&&!jdk(type,"java.util.LinkedHashMap$Entry")&&!jdk(type,"java.util.HashMap$TreeNode"))throw unsupported("MAP_NODE");
                if(nodes.size()>LIMIT)throw unsupported("MAP_NODE_CAPACITY");store(node);Meta entry=meta(node);
                data(entry.get("java.util.HashMap$Node","key"));if(!keys)emit(new Item[]{fieldItem(entry,"java.util.HashMap$Node","value")});
                enqueue(pending,nodes,entry.get("java.util.HashMap$Node","next"));
                if(!jdk(type,"java.util.HashMap$Node")){
                    enqueue(pending,nodes,entry.get("java.util.LinkedHashMap$Entry","before"));enqueue(pending,nodes,entry.get("java.util.LinkedHashMap$Entry","after"));
                    if(jdk(type,"java.util.HashMap$TreeNode"))for(String field:new String[]{"parent","left","right","prev"})enqueue(pending,nodes,entry.get("java.util.HashMap$TreeNode",field));
                }
            }
        }
        void enqueue(List<Object> pending,IdentityHashMap<Object,Boolean> seen,Object value){if(value!=null&&!seen.containsKey(value)){if(pending.size()>=LIMIT*8)throw unsupported("LINK_CAPACITY");pending.add(value);}}
        void deque(Meta value){
            String owner="java.util.ArrayDeque";Object[] array=array(value.get(owner,"elements"));
            int head=number(value.get(owner,"head")),tail=number(value.get(owner,"tail"));
            if(array.length==0||head<0||head>=array.length||tail<0||tail>=array.length)throw unsupported("DEQUE_INDEX");
            Object tailValue=entry(array,tail);
            // The original bodies publish/clear array slots separately from their head/tail stores.
            // head == tail with an occupied slot is the full ring immediately before grow().
            int count=bound(head==tail&&tailValue!=null?array.length:tail>=head?tail-head:array.length-head+tail);store(array);
            if(count==0)range(array,head,head,1,false);
            Item[] items=new Item[count];int index=head;
            for(int at=0;at<count;at++){
                Object item=entry(array,index);
                items[at]=new Item(item,null,null,array,index);if(++index==array.length)index=0;
            }
            emit(items);
            if(count!=array.length&&tailValue!=null)emit(new Item[]{new Item(tailValue,null,null,array,tail)});
            int beforeHead=head==0?array.length-1:head-1;
            if(count!=array.length&&beforeHead!=tail){Object pending=entry(array,beforeHead);if(pending!=null)emit(new Item[]{new Item(pending,null,null,array,beforeHead)});}
        }
        void concurrentMap(Meta value,boolean keys){
            String owner="java.util.concurrent.ConcurrentHashMap",nodeOwner=owner+"$Node";
            Object table=value.get(owner,"table"),next=value.get(owner,"nextTable");
            // Resize/compute callbacks observe the real intermediate stores. Retain both published
            // tables and every forwarding target, without replaying or waiting for the original body.
            List<Object> pending=new ArrayList<>();if(table!=null)pending.add(table);if(next!=null)pending.add(next);
            IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
            for(int at=0;at<pending.size();at++){
                Object node=pending.get(at);if(seen.put(node,Boolean.TRUE)!=null)continue;
                if(seen.size()>LIMIT)throw unsupported("CONCURRENT_MAP_NODE_CAPACITY");
                Class<?> type=node.getClass();
                if(type.isArray()){
                    if(!jdk(type.getComponentType(),nodeOwner))throw unsupported("CONCURRENT_MAP_TABLE_TYPE");
                    Object[] buckets=array(node);range(buckets,0,buckets.length,1,true);
                    for(Object head:entries(buckets))enqueue(pending,seen,head);continue;
                }
                if(jdk(type,owner+"$ForwardingNode")){
                    store(node);Meta forwarding=meta(node);enqueue(pending,seen,forwarding.get(owner+"$ForwardingNode","nextTable"));continue;
                }
                if(jdk(type,owner+"$ReservationNode")){
                    store(node);Meta reservation=meta(node);enqueue(pending,seen,reservation.get(nodeOwner,"next"));continue;
                }
                if(!jdk(type,nodeOwner)&&!jdk(type,owner+"$TreeNode")&&!jdk(type,owner+"$TreeBin"))throw unsupported("CONCURRENT_MAP_NODE");
                store(node);Meta entry=meta(node);
                if(jdk(type,owner+"$TreeBin")){
                    enqueue(pending,seen,entry.get(owner+"$TreeBin","root"));enqueue(pending,seen,entry.get(owner+"$TreeBin","first"));continue;
                }
                Object key=entry.get(nodeOwner,"key"),payload=entry.get(nodeOwner,"val");
                if(number(entry.get(nodeOwner,"hash"))<0)throw unsupported("CONCURRENT_MAP_NODE_HASH");
                data(key);if(!keys)emit(new Item[]{fieldItem(entry,nodeOwner,"val")});
                enqueue(pending,seen,entry.get(nodeOwner,"next"));
                if(jdk(type,owner+"$TreeNode"))for(String link:new String[]{"parent","left","right","prev"})enqueue(pending,seen,entry.get(owner+"$TreeNode",link));
            }
        }
        void identityMap(Meta value,boolean keys){
            bound(number(value.get("java.util.IdentityHashMap","size")));Object[] table=array(value.get("java.util.IdentityHashMap","table"));if((table.length&1)!=0)throw unsupported("IDENTITY_TABLE");range(table,0,table.length,1,true);
            Object empty=staticValue(java.util.IdentityHashMap.class,"NULL_KEY","Ljava/lang/Object;");
            for(int at=0;at<table.length;at+=2){Object key=entry(table,at),entry=entry(table,at+1);if(key==null)continue;
                if(key!=empty)data(key);if(!keys)data(entry);
            }
        }
        void enumMap(Meta value,boolean keys){
            Object[] values=array(value.get("java.util.EnumMap","vals"));bound(values.length);
            Object universe=value.get("java.util.EnumMap","keyUniverse"),keyType=value.get("java.util.EnumMap","keyType");
            if(!(universe instanceof Enum<?>[] actual)||actual.length!=values.length||!(keyType instanceof Class<?>))throw unsupported("ENUM_MAP_LAYOUT");
            bound(number(value.get("java.util.EnumMap","size")));
            Object nullValue=staticValue(java.util.EnumMap.class,"NULL","Ljava/lang/Object;");
            // vals is this map's actual data array. The VM-wide enum universe
            // remains borrowed; it is never registered as an owned array.
            range(values,0,values.length,1,false);
            for(int at=0;at<values.length;at++){Object entry=entry(values,at);if(entry!=null){data(actual[at]);if(!keys&&entry!=nullValue)data(entry);}}
        }
        void immutableMap(Meta value){
            Object[] table=array(value.get(I+"MapN","table"));if((table.length&1)!=0)throw unsupported("IMMUTABLE_MAP_TABLE");range(table,0,table.length,1,true);int count=0;
            for(int at=0;at<table.length;at+=2){Object key=entry(table,at),payload=entry(table,at+1);if(key!=null){count++;data(key);data(payload);}else if(payload!=null)throw unsupported("IMMUTABLE_MAP_PAIR");}
            if(count!=number(value.get(I+"MapN","size")))throw unsupported("IMMUTABLE_MAP_SIZE");
        }
        void guavaEntry(Object object){
            if(object==null)throw unsupported("GUAVA_ENTRY_NULL");Class<?> type=object.getClass();
            if(!guava(type,G+"ImmutableEntry")&&!guava(type,G+"ImmutableMapEntry")&&!guava(type,G+"ImmutableMapEntry$NonTerminalImmutableMapEntry"))throw unsupported("GUAVA_ENTRY");
            store(object);Meta entry=meta(object);data(entry.get(G+"ImmutableEntry","key"));emit(new Item[]{fieldItem(entry,G+"ImmutableEntry","value")});
        }
        void guavaMap(Meta value){
            Object[] entries=array(value.get(G+"RegularImmutableMap","entries"));bound(entries.length);range(entries,0,entries.length,1,true);for(Object entry:entries(entries))guavaEntry(entry);
            Object table=value.get(G+"RegularImmutableMap","table");if(table!=null){
                Object[] actual=array(table);range(actual,0,actual.length,1,true);List<Object> pending=new ArrayList<>();for(Object entry:entries(actual))if(entry!=null)pending.add(entry);IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();
                for(int at=0;at<pending.size();at++){
                    Object entry=pending.get(at);if(seen.put(entry,Boolean.TRUE)!=null)continue;if(seen.size()>LIMIT)throw unsupported("GUAVA_BUCKET_CAPACITY");guavaEntry(entry);
                    if(guava(entry.getClass(),G+"ImmutableMapEntry$NonTerminalImmutableMapEntry"))enqueue(pending,seen,meta(entry).get(G+"ImmutableMapEntry$NonTerminalImmutableMapEntry","nextInKeyBucket"));
                }
            }
        }
        void guavaEntriesList(Object object){
            if(object==null)throw unsupported("GUAVA_ENTRY_LIST");Class<?> type=base(object.getClass());if(type==null||!listName(type.getName()))throw unsupported("GUAVA_ENTRY_LIST_TYPE");store(object);
            Item[] entries=list(object,type,new IdentityHashMap<>());if(entries==null)throw unsupported("GUAVA_ENTRY_LIST_LAYOUT");
            for(Item item:entries){observe(item);if(item.field!=null)item.field.payload=false;else range(item.array,item.index,item.index+1,1,true);guavaEntry(item.value);}
        }
    }
}
