package dev.ronova.pro.bootstrap;

import java.util.*;
import java.lang.ref.*;

/** Source cells describe JVM values and their uses, never ownership inferred from a scalar's bits. */
final class ExecutionFlow {
    private static final StackWalker CALLER=StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> BRIDGE_CALLER=new BridgeCaller();
    private static final class BridgeCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){
                Class<?> type=cursor.next().getDeclaringClass();
                if(type!=ExecutionFlow.class&&type.getNestHost()!=ExecutionFlow.class)return type;
            }return null;
        }
    }
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>();
    private static final Sources NONE=new Sources(new Module[0],false);
    private static final Sources UNKNOWN=new Sources(new Module[0],true);
    private static final int CONSTRUCTOR_SITE=Integer.MIN_VALUE;
    private static final class TraceList<E> extends ArrayList<E> {
        TraceList(){
            Class<?> caller=CALLER.getCallerClass();
            if(caller!=ExecutionFlow.class&&caller!=Heap.class&&caller!=ArrayChange.class&&caller!=MemoryUse.class&&caller!=Frame.class)
                throw new SecurityException("ACTUAL_EXECUTION_METADATA_OWNER_REQUIRED");
            CodeSourceBridge.executionMetadataList(this);
        }
        void removeSpan(int from,int to){
            if(CALLER.getCallerClass()!=ExecutionFlow.class)throw new SecurityException("ACTUAL_EXECUTION_METADATA_OWNER_REQUIRED");
            super.removeRange(from,to);
        }
    }
    private static final class TraceMap<K,V> extends HashMap<K,V> {
        TraceMap(){
            Class<?> creator=CALLER.getCallerClass();
            if(creator!=ExecutionFlow.class&&creator!=Heap.class&&creator!=Method.class&&creator!=Plan.class
                    &&(FIELDS==null||creator!=FIELDS.getClass()))
                throw new SecurityException("ACTUAL_EXECUTION_METADATA_OWNER_REQUIRED");
            CodeSourceBridge.executionMetadataMap(this);
        }
    }
    static Class<?> metadataMapType(){
        if(CALLER.getCallerClass()!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_STATE_REQUIRED");
        return TraceMap.class;
    }
    private static final ReferenceQueue<Object> DEAD_HEAP=new ReferenceQueue<>();
    private static final Map<Carrier,Heap> HEAP=new TraceMap<>();
    private static final ClassValue<Map<String,FieldSlot>> FIELDS=new ClassValue<>(){
        protected Map<String,FieldSlot> computeValue(Class<?> actual){Map<String,FieldSlot> fields=new TraceMap<>();CodeSourceBridge.executionControls(fields);return fields;}
    };
    static {CodeSourceBridge.executionControls(CURRENT,NONE,NONE.modules(),UNKNOWN,UNKNOWN.modules(),DEAD_HEAP,HEAP,FIELDS);}
    private record Sources(Module[] modules,boolean unknown) {
        Sources {modules=modules.clone();CodeSourceBridge.executionControls(this,modules);}
    }
    private static final class Alias {
        WeakReference<Object> actual;Sources effects=NONE;
        Alias(){CodeSourceBridge.executionControls(this);}
    }
    private record Cell(Sources sources,int width,Alias alias){
        Cell(Sources sources,int width){this(sources,width,null);}
        Cell{CodeSourceBridge.executionControls(this);}
    }
    private static final class Carrier extends WeakReference<Object> {
        final int hash;
        Carrier(Object actual,boolean stored){super(actual,stored?DEAD_HEAP:null);hash=System.identityHashCode(actual);if(stored)CodeSourceBridge.executionControls(this);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Carrier key&&get()!=null&&get()==key.get();}
    }
    // Call under HEAP's monitor. Only a key actually inserted in the directory
    // needs queue registration and control protection; lookups retain identity.
    private static Heap heapFor(Object actual){
        Heap heap=HEAP.get(new Carrier(actual,false));
        if(heap==null){heap=new Heap();HEAP.put(new Carrier(actual,true),heap);}
        return heap;
    }
    private static final class FieldSlot {
        final WeakReference<Class<?>> declaring;final String name,descriptor;
        FieldSlot(Class<?> declaring,String name,String descriptor){this.declaring=new WeakReference<>(declaring);this.name=name;this.descriptor=descriptor;CodeSourceBridge.executionControls(this,this.declaring);}
    }
    private static final class Slot {
        Sources sources=UNKNOWN;long version;int writers;boolean overlap,watched,vmWatched;
        Revision revision;Thread recovering;
        Slot(){CodeSourceBridge.executionControls(this);}
    }
    private static final class Value {
        final Object scalar;final WeakReference<Object> reference;final boolean nil;
        Value(Object actual,boolean object){scalar=object?null:actual;reference=object&&actual!=null?new WeakReference<>(actual):null;nil=object&&actual==null;CodeSourceBridge.executionControls(this,reference);}
        boolean live(){return scalar!=null||nil||reference!=null&&reference.get()!=null;}
        Object actual(){
            if(reference==null)return scalar;Object actual=reference.get();
            if(actual==null)throw new IllegalStateException("HEAP_BASELINE_RELEASED");return actual;
        }
    }
    private record Revision(Value before,Value after,Sources previous,Revision parent){Revision{CodeSourceBridge.executionControls(this);}}
    private record UnsafeUse(Frame frame,int instruction,Object receipt,boolean reading){UnsafeUse{CodeSourceBridge.executionControls(this);}}
    private record PlatformCall(Frame frame,Call call){PlatformCall{CodeSourceBridge.executionControls(this);}}
    private static final class Recovery {
        final WeakReference<Object> carrier;final Heap holder;final Slot slot;final Object location;final long version,carrierVersion;final Sources sources;final Revision revision;
        final Set<Module> selected;
        Recovery(Object carrier,Heap holder,Slot slot,Object location,Set<Module> selected){this.carrier=new WeakReference<>(carrier);this.holder=holder;this.slot=slot;this.location=location;version=slot.version;carrierVersion=holder.version;sources=slot.sources;revision=slot.revision;
            this.selected=Collections.newSetFromMap(new IdentityHashMap<>());this.selected.addAll(selected);CodeSourceBridge.executionControls(this,this.carrier,this.selected);}
    }
    private static final class Heap {
        final Map<Object,Slot> slots=new TraceMap<>();
        final TraceList<Region> regions=new TraceList<>();final List<MemoryUse> arrayWrites=new TraceList<>(),fieldWrites=new TraceList<>();
        final List<MemoryRead> reads=new TraceList<>();
        final NavigableMap<Long,ArrayRange> arrayRanges=new TreeMap<>();final List<ArrayChange> arrayChanges=new TraceList<>();
        Sources arrayBaseline=UNKNOWN;int arrayReaders;
        long version,arrayRevision;int writers;boolean overlap;
        Heap(){CodeSourceBridge.executionControls(this,slots,regions,arrayWrites,fieldWrites,reads,arrayRanges,arrayChanges);}
    }
    private static final class ArrayImage {
        final long start,bits;final int length,offset;final Object values;
        ArrayImage(long start,int length,Object values){this.start=start;this.length=length;this.values=values;offset=0;bits=0;CodeSourceBridge.executionControls(this,values);}
        ArrayImage(long start,int length,long bits){this.start=start;this.length=length;this.bits=bits;values=null;offset=0;CodeSourceBridge.executionControls(this);}
        ArrayImage(ArrayImage source,long start,int length,int relative){
            this.start=start;this.length=length;values=source.values;
            offset=values==null?0:source.offset+relative;bits=values==null?maskImageBits(source.bits>>>(relative*Byte.SIZE),length):0;
            // This window retains the backing protected by the source constructor.
            // Only this new window is published by a slice.
            CodeSourceBridge.executionControls(this);
        }
    }
    private record ArrayRevision(ArrayImage before,ArrayImage after,Sources sources,Sources prior,ArrayRevision previous,boolean complete,boolean baseline){
        ArrayRevision(ArrayImage before,ArrayImage after,Sources sources,Sources prior,ArrayRevision previous,boolean complete){this(before,after,sources,prior,previous,complete,false);}
        ArrayRevision{CodeSourceBridge.executionControls(this);}
    }
    private static final class ArrayRange {
        final long start;final int length;ArrayRevision head;
        ArrayRange(long start,int length,ArrayRevision head){this.start=start;this.length=length;this.head=head;CodeSourceBridge.executionControls(this);}
    }
    private static final class ArrayChange {
        final Object carrier;final Heap heap;final long start,length;final Thread thread=Thread.currentThread();final ArrayChange root;
        ArrayImage before;ArrayImage[] parts;Sources written;boolean closed,overlap,changed,complete=true,images=true;
        ArrayChange(Object carrier,Heap heap,long start,long length,Sources written,ArrayChange root){this.carrier=carrier;this.heap=heap;this.start=start;this.length=length;this.written=written;this.root=root==null?this:root;CodeSourceBridge.executionControls(this);}
    }
    private static final class ArrayRecovery {
        final WeakReference<Object> carrier;final Heap heap;final ArrayRange range;final ArrayRevision head;final Set<Module> selected;
        ArrayRecovery(Object carrier,Heap heap,ArrayRange range,Set<Module> selected){this.carrier=new WeakReference<>(carrier);this.heap=heap;this.range=range;head=range.head;this.selected=Set.copyOf(selected);CodeSourceBridge.executionControls(this,this.carrier,this.selected);}
    }
    private static final class MemoryRead {
        final Object carrier;final Heap holder;final long offset,length;final Thread thread=Thread.currentThread();Sources observed;boolean closed;
        MemoryRead(Object carrier,Heap holder,long offset,long length,Sources observed){this.carrier=carrier;this.holder=holder;this.offset=offset;this.length=length;this.observed=observed;CodeSourceBridge.executionControls(this);}
    }
    private record Region(int start,int length,Sources sources){Region{CodeSourceBridge.executionControls(this);}}
    private static final class HeapUse {
        final Heap holder;final Slot slot;final Object location;final WeakReference<Object> carrier;final long version,carrierVersion;final Sources prior,written;final boolean write,readable;
        Value before;
        boolean closed,observed;ArrayChange arrayChange;MemoryRead arrayRead;boolean arrayReading;
        HeapUse(Object carrier,Heap holder,Slot slot,Object location,boolean write,Sources written){
            this.carrier=new WeakReference<>(carrier);this.holder=holder;this.slot=slot;this.location=location;this.write=write;this.written=written;prior=slot.sources;carrierVersion=holder.version;
            readable=slot.watched&&slot.writers==0&&holder.writers==0;
            if(write){if(slot.writers!=0)slot.overlap=true;slot.writers++;slot.version++;slot.sources=join(prior,written,UNKNOWN);}
            version=slot.version;CodeSourceBridge.executionControls(this,this.carrier);
        }
    }
    private static final class Prior {
        final Slot slot;final Sources sources;final long version;Prior next;
        Prior(Slot slot,Sources sources,long version){this.slot=slot;this.sources=sources;this.version=version;CodeSourceBridge.executionControls(this);}
        Slot slot(){return slot;}Sources sources(){return sources;}long version(){return version;}
    }
    private record FieldCapture(WeakReference<Object> carrier,FieldSlot field,Value before,Revision revision){
        FieldCapture{CodeSourceBridge.executionControls(this,carrier);}
    }
    private static final class MemoryUse {
        final Heap holder;final long version;final boolean broad;Sources written;final Thread thread=Thread.currentThread();Prior prior,last;boolean closed;
        int arrayStart=-1,arrayLength;boolean rangeOverlap;
        Slot capturedSlot;FieldCapture capture;
        final Frame frame=CURRENT.get();final int instruction=frame==null?-1:frame.instruction;
        HeapUse delegated;
        ArrayChange arrayChange;
        MemoryUse(Heap holder,Collection<Slot> selected,boolean broad,Sources written){
            this.holder=holder;this.broad=broad;this.written=written;
            boolean carrierBusy=holder.writers!=0;
            if(broad){if(carrierBusy)holder.overlap=true;holder.writers++;holder.version++;}version=holder.version;
            for(Slot slot:selected)select(slot,carrierBusy);
            CodeSourceBridge.executionControls(this);
        }
        MemoryUse(Heap holder,int start,int length,Slot single,boolean broad,Sources written){
            this.holder=holder;this.broad=broad;this.written=written;
            boolean carrierBusy=holder.writers!=0;
            if(broad){if(carrierBusy)holder.overlap=true;holder.writers++;holder.version++;}version=holder.version;
            if(single!=null)select(single,carrierBusy);
            else if(length<=holder.slots.size())for(int index=start,end=start+length;index<end;index++){
                Slot slot=holder.slots.get(index);if(slot!=null)select(slot,carrierBusy);
            }
            else for(var entry:holder.slots.entrySet())if(entry.getKey() instanceof Integer index&&index>=start&&index-start<length)
                select(entry.getValue(),carrierBusy);
            CodeSourceBridge.executionControls(this);
        }
        MemoryUse(Heap holder,Slot slot,Sources written,FieldCapture capture){
            this.holder=holder;version=holder.version;broad=false;this.written=written;capturedSlot=slot;this.capture=capture;
            select(slot,holder.writers!=0);CodeSourceBridge.executionControls(this);
        }
        MemoryUse(HeapUse delegated){holder=delegated.holder;version=holder.version;broad=false;written=delegated.written;this.delegated=delegated;CodeSourceBridge.executionControls(this);}
        private void select(Slot slot,boolean carrierBusy){
            Sources previous=slot.sources;if(slot.writers!=0||carrierBusy)slot.overlap=true;slot.writers++;
            slot.sources=join(previous,written);slot.version++;
            // Each selected slot already needs a protected Prior. Link those
            // records in selection order instead of allocating another list.
            Prior next=new Prior(slot,previous,slot.version);if(last==null)prior=next;else last.next=next;last=next;
        }
        void include(Slot slot){select(slot,holder.writers!=0);}
    }
    private record Control(int instruction,int end,Sources sources){Control{CodeSourceBridge.executionControls(this);}}
    private record Deferred(String name,String descriptor,Sources sources){Deferred{CodeSourceBridge.executionControls(this);}}
    private static final class Method {
        final String selector;final int locals;final int[] arguments;final int[][] operations;final Sources[] sources;final boolean constructor;final String[][] fields;final Map<Integer,Integer> sites;
        Method(Object[] row){
            selector=(String)row[0];locals=(Integer)row[1];arguments=((int[])row[2]).clone();
            int[][] original=(int[][])row[3];operations=new int[original.length][];for(int i=0;i<original.length;i++)operations[i]=original[i].clone();
            Module[][] supplied=(Module[][])row[4];sources=new Sources[supplied.length];
            for(int i=0;i<supplied.length;i++)sources[i]=supplied[i].length==0?NONE:join(new Sources(supplied[i],false));
            String[][] suppliedFields=(String[][])row[6];fields=new String[suppliedFields.length][];for(int i=0;i<fields.length;i++)fields[i]=suppliedFields[i]==null?null:suppliedFields[i].clone();
            constructor=(Boolean)row[7];
            int[][] positions=(int[][])row[5];Map<Integer,Integer> selected=new TraceMap<>();for(int[] position:positions)selected.put(position[0],position[1]);sites=Map.copyOf(selected);
            CodeSourceBridge.executionControls(this,arguments,operations,sources,fields,sites);for(int[] operation:operations)CodeSourceBridge.executionControls(operation);for(String[] field:fields)if(field!=null)CodeSourceBridge.executionControls((Object)field);
        }
    }
    static final class Plan {
        final Map<String,Method> methods;
        Plan(Object[][] rows){Map<String,Method> selected=new TraceMap<>();for(Object[] row:rows){Method method=new Method(row);selected.put(method.selector,method);}methods=Map.copyOf(selected);CodeSourceBridge.executionControls(this,methods);}
    }
    private static final class Call {
        final Frame caller;final int instruction,width;final Cell[] arguments;final Sources direct;
        Cell returned;Sources observed=NONE;boolean normal,partial;
        Call(Frame caller,int instruction,int width,Cell[] arguments,Sources direct){this.caller=caller;this.instruction=instruction;this.width=width;this.arguments=arguments;this.direct=direct;CodeSourceBridge.executionControls(this,arguments);}
    }
    private static final class Frame {
        final Frame parent;final Thread thread=Thread.currentThread();final Class<?> declaring;final Method method;final int depth;final Sources declaration,entry;final Cell[] locals;final Alias initializedReceiver;
        final List<Cell> stack=new TraceList<>();final List<Control> controls=new TraceList<>();final Call incoming;
        final List<Deferred> deferred=new TraceList<>();
        Sources active=NONE,thrown=NONE;Call call;Cell returned;Cell[] operands;HeapUse heap;int instruction=-1;boolean closed;
        Frame(Class<?> declaring,Method method,int depth,Module declaration,Call incoming){
            this.parent=CURRENT.get();this.declaring=declaring;this.method=method;this.depth=depth;this.declaration=declaration==null?NONE:new Sources(new Module[]{declaration},false);this.incoming=incoming;locals=new Cell[method.locals];
            entry=incoming==null?NONE:incoming.direct;
            int slot=0;for(int i=0;i<method.arguments.length;i++){
                int width=method.arguments[i];Cell input=incoming!=null&&i<incoming.arguments.length?incoming.arguments[i]:new Cell(UNKNOWN,width);
                Alias alias=input.alias();if(method.constructor&&i==0&&alias==null)alias=new Alias();
                locals[slot]=new Cell(input.sources(),width,alias);slot+=width;
            }
            initializedReceiver=method.constructor?locals[0].alias():null;
            CodeSourceBridge.executionControls(this,locals,stack,controls,deferred);
        }
    }
    private ExecutionFlow(){}
    private static void bridge(){
        bridge(CALLER.walk(BRIDGE_CALLER));
    }
    private static void bridge(Class<?> caller){
        if(caller!=CodeSourceBridge.class)throw new SecurityException("ACTUAL_EXECUTION_SOURCE_BRIDGE_REQUIRED");
    }
    private static Sources join(Sources... values){
        Sources first=null;boolean single=true,unknown=false;
        for(Sources value:values)if(value!=null){
            unknown|=value.unknown();if(value.modules().length==0)continue;
            if(first==null)first=value;
            if(value.modules().length!=1||first.modules().length!=1||value.modules()[0]!=first.modules()[0])single=false;
        }
        if(first==null)return unknown?UNKNOWN:NONE;
        // These cells are immutable. Reusing an identical single-module union
        // preserves identity provenance without creating another monitored map.
        if(single)return first.unknown()==unknown?first:new Sources(first.modules(),unknown);
        Set<Module> modules=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Sources value:values)if(value!=null)Collections.addAll(modules,value.modules());
        return new Sources(modules.toArray(Module[]::new),unknown);
    }
    private static Sources source(Cell value){return value==null?UNKNOWN:join(value.sources(),value.alias()==null?NONE:value.alias().effects);}
    private static void bind(Alias alias,Object actual){
        if(alias==null)return;if(alias.actual!=null&&alias.actual.get()!=actual){alias.effects=join(alias.effects,UNKNOWN);return;}
        if(alias.actual==null){alias.actual=new WeakReference<>(actual);CodeSourceBridge.executionControls(alias.actual);}
    }
    static Object plan(Object[][] rows){bridge();return new Plan(rows);}
    static Object enter(Object supplied,StackWalker.StackFrame caller,int depth,StackWalker.StackFrame parent){
        bridge();if(!(supplied instanceof Plan plan))return null;
        Method method=plan.methods.get(caller.getMethodName()+caller.getDescriptor());if(method==null||!Objects.equals(method.sites.get(caller.getByteCodeIndex()),-1))return null;
        Frame current=CURRENT.get();Call incoming=null;
        if(current!=null&&current.call!=null&&parent!=null&&parent.getDeclaringClass()==current.declaring
                &&(parent.getMethodName()+parent.getDescriptor()).equals(current.method.selector)
                &&Objects.equals(current.method.sites.get(parent.getByteCodeIndex()),current.call.instruction)
                &&current.depth==depth-1&&current.call.arguments.length==method.arguments.length){
            boolean shape=true;for(int i=0;i<method.arguments.length;i++)if(current.call.arguments[i].width()!=method.arguments[i])shape=false;
            if(shape)incoming=current.call;
        }
        Module declaration=DefinitionBridge.module(caller.getDeclaringClass());if(!TaskBridge.producer(declaration))declaration=null;
        Frame next=new Frame(caller.getDeclaringClass(),method,depth,declaration,incoming);CURRENT.set(next);return next;
    }
    private static Frame frame(Object token,StackWalker.StackFrame caller,int depth){
        if(!(token instanceof Frame frame)||frame.closed||frame.thread!=Thread.currentThread()||CURRENT.get()!=frame||frame.depth!=depth
                ||caller==null||caller.getDeclaringClass()!=frame.declaring||!(caller.getMethodName()+caller.getDescriptor()).equals(frame.method.selector))return null;
        return frame;
    }
    private static Cell pop(Frame frame){return frame.stack.remove(frame.stack.size()-1);}
    private static Sources controls(Frame frame,int instruction){
        frame.controls.removeIf(control->control.end()==instruction||control.instruction()==instruction);Sources result=NONE;for(Control control:frame.controls)result=join(result,control.sources());return result;
    }
    private static void push(Frame frame,Sources sources,int width){frame.stack.add(new Cell(sources,width));}
    private static Cell[] inputs(Frame frame,int count){Cell[] values=new Cell[count];for(int i=count-1;i>=0;i--)values[i]=pop(frame);return values;}
    private static Sources used(Cell[] values,Sources direct){Sources result=direct;for(Cell value:values)result=join(result,source(value));return result;}
    private static Frame site(Object token,int instruction,StackWalker.StackFrame caller,int depth){
        Frame frame=frame(token,caller,depth);return frame!=null&&Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),instruction)?frame:null;
    }
    static void argument(Object token,int slot,Object actual,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,-2-slot,caller,depth);if(frame==null||slot<0||slot>=frame.locals.length||frame.locals[slot]==null)return;
        Cell previous=frame.locals[slot];Alias alias=previous.alias()==null?new Alias():previous.alias();bind(alias,actual);frame.locals[slot]=new Cell(previous.sources(),previous.width(),alias);
    }
    static void constructor(Object token,Object receiver,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,CONSTRUCTOR_SITE,caller,depth);if(frame==null)return;bind(frame.initializedReceiver,receiver);
        for(Deferred write:frame.deferred){
            Class<?> actual=CodeSourceBridge.executionFieldOwner(frame.declaring,write.name(),write.descriptor());
            Sources written=join(write.sources(),UNKNOWN);frame.initializedReceiver.effects=join(frame.initializedReceiver.effects,written);
            if(actual==null||receiver==null)continue;
            Map<String,FieldSlot> fields=FIELDS.get(actual);String key=write.name()+'\u0000'+write.descriptor();FieldSlot field;
            synchronized(fields){field=fields.computeIfAbsent(key,ignored->new FieldSlot(actual,write.name(),write.descriptor()));}
            synchronized(HEAP){
                reapHeap();Heap heap=heapFor(receiver);Slot slot=heap.slots.computeIfAbsent(field,ignored->new Slot());
                slot.sources=written;slot.version++;if(slot.writers!=0)slot.overlap=true;
            }
        }
        frame.deferred.clear();
    }
    static void uninitializedWrite(Object token,int instruction,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);if(frame==null||!frame.method.constructor||frame.instruction!=instruction)return;
        String[] field=frame.method.fields[instruction];if(field!=null)frame.deferred.add(new Deferred(field[0],field[1],frame.active));
        if(frame.initializedReceiver!=null)frame.initializedReceiver.effects=join(frame.initializedReceiver.effects,frame.active,UNKNOWN);
    }
    static boolean nativeExitRequired(Object token){bridge();return token instanceof Frame frame&&!frame.closed&&CURRENT.get()==frame&&frame.method.constructor;}
    static void watchFailed(Object token){
        bridge();if(!(token instanceof Frame frame)||frame.closed||CURRENT.get()!=frame)return;
        frame.thrown=join(frame.thrown,UNKNOWN);if(frame.initializedReceiver!=null)frame.initializedReceiver.effects=join(frame.initializedReceiver.effects,UNKNOWN);close(frame,false);
    }
    static boolean popped(Object token,boolean normal){
        bridge();if(!(token instanceof Frame frame)||frame.thread!=Thread.currentThread())return false;
        if(frame.closed)return true;if(CURRENT.get()!=frame)return false;
        close(frame,normal);return true;
    }
    static void reference(Object token,int instruction,Object actual,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);if(frame==null)return;
        if(frame.returned!=null){bind(frame.returned.alias(),actual);return;}
        if(!frame.stack.isEmpty())bind(frame.stack.get(frame.stack.size()-1).alias(),actual);
    }
    static void initialized(Object token,int instruction,Object actual,Class<?> declaring,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);if(frame==null||frame.call==null||frame.call.instruction!=instruction||frame.call.arguments.length==0)return;
        Alias alias=frame.call.arguments[0].alias();if(alias==null)return;bind(alias,actual);
        Module producer=DefinitionBridge.module(declaring);Sources declaration=TaskBridge.producer(producer)?new Sources(new Module[]{producer},false):NONE;
        alias.effects=join(alias.effects,frame.call.direct,declaration,frame.call.normal&&!frame.call.partial?NONE:UNKNOWN);
    }
    static Object unsafeBefore(Object token,int instruction,Object accessor,Object receiver,long offset,long length,Object proposed,Object source,long sourceOffset,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);
        if(frame==null||frame.instruction!=instruction||frame.call==null||frame.call.instruction!=instruction)throw new IllegalStateException("ACTUAL_UNSAFE_CALL_SITE_REQUIRED");
        String[] member=frame.method.fields[instruction];
        if(member==null||member.length!=7)throw new IllegalStateException("ACTUAL_UNSAFE_CALL_DECLARATION_REQUIRED");
        if(frame.call.arguments.length>0)bind(frame.call.arguments[0].alias(),accessor);
        int destination=Integer.parseInt(member[4]);
        if(destination>=0&&destination+1<frame.call.arguments.length)bind(frame.call.arguments[destination+1].alias(),receiver);
        if(destination==2&&(member[1].equals("copyMemory")||member[1].equals("copySwapMemory")))bind(frame.call.arguments[1].alias(),source);
        Sources sources=join(frame.active,frame.call.direct);
        Object receipt=CodeSourceBridge.executionUnsafeMutation(accessor,receiver,offset,length,proposed,source,sourceOffset,member[0],member[1],member[3],Integer.parseInt(member[6])>=0,sources.modules());
        return receipt==Boolean.FALSE?Boolean.FALSE:new UnsafeUse(frame,instruction,receipt,member[1].startsWith("get")&&!member[1].startsWith("getAnd"));
    }
    static void unsafeAfter(Object token,int instruction,Object supplied,boolean written,StackWalker.StackFrame caller,int depth){
        bridge();if(supplied==null||supplied==Boolean.FALSE)return;
        if(!(supplied instanceof UnsafeUse use)||use.frame()!=token||use.instruction()!=instruction||use.frame().thread!=Thread.currentThread())throw new SecurityException("ACTUAL_UNSAFE_CALL_RECEIPT_REQUIRED");
        Frame frame=site(token,instruction,caller,depth);
        if(frame==null){use.frame().thrown=join(use.frame().thrown,UNKNOWN);CodeSourceBridge.executionUnsafeMutationEnd(use.receipt(),written);return;}
        try{
            if(written&&use.reading()&&frame.call!=null&&frame.call.instruction==instruction){
                Module[] observed=CodeSourceBridge.executionUnsafeReadSources(use.receipt());
                // Known writers survive the read, but unobserved low-level
                // writers still prevent a claim of exclusive provenance.
                frame.call.observed=join(frame.call.observed,new Sources(observed,true));
            }
        }finally{CodeSourceBridge.executionUnsafeMutationEnd(use.receipt(),written);}
    }
    static Object platformCall(Object receiver,StackWalker.StackFrame callee,StackWalker.StackFrame caller,int depth){
        bridge();if(receiver==null||callee==null||caller==null)return null;
        Frame frame=CURRENT.get();while(frame!=null&&(frame.declaring!=caller.getDeclaringClass()||frame.depth!=depth))frame=frame.parent;
        if(frame==null||frame.closed||frame.thread!=Thread.currentThread()||frame.call==null
                ||!frame.method.selector.equals(caller.getMethodName()+caller.getDescriptor())
                ||!Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),frame.call.instruction))return null;
        Call call=frame.call;int opcode=frame.method.operations[call.instruction][0];String[] method=frame.method.fields[call.instruction];
        if(opcode!=182&&opcode!=183&&opcode!=185||call.arguments.length==0||method==null||method.length<3
                ||!method[1].equals(callee.getMethodName())||!method[2].equals(callee.getDescriptor()))return null;
        Alias receiverAlias=call.arguments[0].alias();
        if(receiverAlias!=null&&receiverAlias.actual!=null&&receiverAlias.actual.get()!=receiver)return null;
        bind(receiverAlias,receiver);return new PlatformCall(frame,call);
    }
    static void platformObserved(Object token,Module[] modules){
        bridge();if(!(token instanceof PlatformCall use)||use.frame.closed||use.frame.thread!=Thread.currentThread()||use.frame.call!=use.call)return;
        // The source belongs to this exact suspended invoke instruction, never
        // to another call that happens to return an equal primitive value.
        Sources observed=new Sources(Arrays.stream(modules).filter(Objects::nonNull).toArray(Module[]::new),true);
        use.call.observed=join(use.call.observed,observed);use.frame.thrown=join(use.frame.thrown,observed);
    }
    static void heapBefore(Object token,int instruction,Class<?> symbolic,Object receiver,int index,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);if(frame==null||frame.instruction!=instruction||frame.operands==null)return;
        int opcode=frame.method.operations[instruction][0];boolean field=opcode>=178&&opcode<=181;
        boolean write=field?opcode==179||opcode==181:opcode>=79&&opcode<=86;Object holder,location;
        if(field){
            String[] member=frame.method.fields[instruction];if(member==null)return;
            Class<?> actual=CodeSourceBridge.executionFieldOwner(symbolic,member[0],member[1]);if(actual==null)return;
            holder=opcode==178||opcode==179?actual:receiver;if(holder==null)return;
            Map<String,FieldSlot> fields=FIELDS.get(actual);String key=member[0]+'\u0000'+member[1];
            synchronized(fields){location=fields.computeIfAbsent(key,ignored->new FieldSlot(actual,member[0],member[1]));}
        }else{
            if(!(opcode>=46&&opcode<=53||opcode>=79&&opcode<=86)||receiver==null||!receiver.getClass().isArray())return;
            if(index<0||index>=java.lang.reflect.Array.getLength(receiver))return;holder=receiver;location=index;
        }
        if(receiver!=null&&frame.operands.length!=0)bind(frame.operands[0].alias(),receiver);
        synchronized(HEAP){
            reapHeap();Heap heap=heapFor(holder);
            Slot slot=location instanceof Integer at?arraySlot(heap,at):heap.slots.computeIfAbsent(location,ignored->new Slot());
            frame.heap=new HeapUse(holder,heap,slot,location,write,frame.active);
            if(location instanceof Integer at){
                if(write)frame.heap.arrayChange=beginArrayChange(holder,heap,(long)at*arrayUnit(holder),arrayUnit(holder),frame.active,true);
                else {
                    long[] layout=ResourceBridge.arrayLayout(holder.getClass());
                    frame.heap.arrayRead=new MemoryRead(holder,heap,layout==null?-1:layout[0]+at*layout[1],layout==null?1:layout[1],
                            join(slot.sources,arraySources(heap,(long)at*arrayUnit(holder),arrayUnit(holder))));
                    heap.reads.add(frame.heap.arrayRead);heap.arrayReaders++;frame.heap.arrayReading=true;
                }
            }else if(write&&!frame.heap.written.unknown()&&frame.heap.written.modules().length!=0)frame.heap.before=readValue(holder,location);
        }
        if(location instanceof FieldSlot fieldSlot){
            Class<?> declaring=fieldSlot.declaring.get();boolean installed=declaring!=null&&CodeSourceBridge.executionWatchField(holder,declaring,fieldSlot.name,fieldSlot.descriptor);
            synchronized(HEAP){if(frame.heap!=null)frame.heap.slot.vmWatched|=installed;}
        }
    }
    static void fieldModified(Object holder,Class<?> declaring,String name,String descriptor,Class<?> writer,String method,String methodDescriptor,int instruction,Object supplied,Module[] contributors){
        bridge();FieldSlot field;Map<String,FieldSlot> fields=FIELDS.get(declaring);
        synchronized(fields){field=fields.get(name+'\u0000'+descriptor);}if(field==null)return;
        synchronized(HEAP){
            reapHeap();Heap heap=HEAP.get(new Carrier(holder,false));if(heap==null)return;Slot slot=heap.slots.get(field);if(slot==null)return;
            Frame current=CURRENT.get();boolean planned=supplied instanceof Plan plan&&current!=null&&plan.methods.get(method+methodDescriptor)==current.method;
            if(planned&&current.declaring==writer&&!current.closed&&current.heap!=null&&current.heap.write&&current.heap.slot==slot
                    &&Objects.equals(current.method.sites.get(instruction),current.instruction)){
                current.heap.observed=true;return;
            }
            if(slot.recovering==Thread.currentThread()&&writer==NativeControl.class&&method.equals("restoreField0"))return;
            for(int i=heap.fieldWrites.size()-1;i>=0;i--){
                MemoryUse use=heap.fieldWrites.get(i);
                if(!use.closed&&use.thread==Thread.currentThread()&&use.capturedSlot==slot&&use.capture!=null&&use.frame==current
                        &&(current==null||!current.closed&&use.instruction==current.instruction))return;
            }
            Module actual=writer==null?null:DefinitionBridge.module(writer);
            Sources written=new Sources(Arrays.stream(contributors).filter(Objects::nonNull).toArray(Module[]::new),true);if(TaskBridge.producer(actual))written=join(written,new Sources(new Module[]{actual},false));
            slot.sources=join(slot.sources,written,UNKNOWN);slot.revision=null;slot.version++;if(slot.writers!=0)slot.overlap=true;
            observeFieldReads(heap,field,written);
        }
    }
    private static Sources memorySources(Module[] contributors){
        Frame frame=CURRENT.get();if(contributors.length==0)return join(frame==null?NONE:frame.active,UNKNOWN);
        boolean unknown=Arrays.stream(contributors).anyMatch(Objects::isNull);
        return join(frame==null?NONE:frame.active,new Sources(Arrays.stream(contributors).filter(Objects::nonNull).toArray(Module[]::new),unknown));
    }
    static Object memoryBefore(Object holder,Module[] contributors){
        bridge();if(holder==null)return null;
        if(holder.getClass().isArray())return arrayMemoryBefore(holder,0,java.lang.reflect.Array.getLength(holder),true,contributors);
        synchronized(HEAP){
            reapHeap();Heap heap=HEAP.get(new Carrier(holder,false));if(heap==null)return null;
            Sources changed=memorySources(contributors);
            return new MemoryUse(heap,heap.slots.values(),true,changed);
        }
    }
    static long arrayRevision(Object holder){
        bridge();synchronized(HEAP){Heap heap=HEAP.get(new Carrier(holder,false));return heap==null?0:heap.arrayRevision;}
    }
    static Object fieldMemoryBefore(Object holder,Class<?> declaring,String name,String descriptor,Module[] contributors,boolean instruction){
        bridge();if(holder==null||declaring==null||name==null||descriptor==null)return null;
        Map<String,FieldSlot> fields=FIELDS.get(declaring);FieldSlot field;
        synchronized(fields){field=fields.computeIfAbsent(name+'\u0000'+descriptor,ignored->new FieldSlot(declaring,name,descriptor));}
        boolean watched=CodeSourceBridge.executionWatchField(holder,declaring,name,descriptor);
        synchronized(HEAP){
            reapHeap();Heap heap=heapFor(holder);
            Slot slot=heap.slots.computeIfAbsent(field,ignored->new Slot());
            slot.vmWatched|=watched;
            Frame frame=CURRENT.get();HeapUse pending=frame==null?null:frame.heap;
            if(instruction&&pending!=null&&!pending.closed&&pending.write&&pending.holder==heap&&pending.slot==slot){
                pending.before=!pending.written.unknown()&&pending.written.modules().length!=0?readValue(holder,field):null;
                return new MemoryUse(pending);
            }
            Sources written=memorySources(contributors);
            // Contributors can only add unknown, never remove it. Keep the field
            // capture for watch de-duplication and read propagation even when no
            // before/after value can be consumed by a complete revision.
            FieldCapture capture=new FieldCapture(new WeakReference<>(holder),field,written.unknown()?null:readValue(holder,field),slot.revision);
            MemoryUse use=new MemoryUse(heap,slot,written,capture);
            heap.fieldWrites.add(use);return use;
        }
    }
    static Object arrayMemoryBefore(Object holder,int start,int length,Module[] contributors){
        bridge();return arrayMemoryBefore(holder,start,length,false,contributors);
    }
    private static Object arrayMemoryBefore(Object holder,int start,int length,boolean broad,Module[] contributors){
        if(holder==null||!holder.getClass().isArray())return null;
        int size=java.lang.reflect.Array.getLength(holder);if(start<0||length<=0||start>size-length)return null;
        int unit=arrayUnit(holder);
        return arrayMemoryBefore(holder,start,length,broad,contributors,(long)start*unit,(long)length*unit,true);
    }
    private static Object arrayMemoryBefore(Object holder,int start,int length,boolean broad,Module[] contributors,long position,long count,boolean typed){
        synchronized(HEAP){
            reapHeap();Heap heap=heapFor(holder);
            // Create the single slot before entering this window so every older
            // overlapping window includes it. The locked heap is then selected
            // directly into the same Prior records, without a temporary list.
            Slot single=length==1?arraySlot(heap,start):null;
            MemoryUse use=new MemoryUse(heap,start,length,single,broad,memorySources(contributors));
            use.arrayStart=start;use.arrayLength=length;
            for(MemoryUse pending:heap.arrayWrites)if(overlaps(start,length,pending.arrayStart,pending.arrayLength)){pending.rangeOverlap=true;use.rangeOverlap=true;}
            use.arrayChange=beginArrayChange(holder,heap,position,count,use.written,typed);
            heap.arrayWrites.add(use);return use;
        }
    }
    // Primitive ranges are measured in bytes; reference ranges are actual element identities.
    private static int arrayUnit(Object array){
        Class<?> type=array.getClass().getComponentType();
        return type==long.class||type==double.class?8:type==int.class||type==float.class?4:type==short.class||type==char.class?2:1;
    }
    private static ArrayImage arrayImage(Object array,long start,int length){
        return arrayImage(array,start,length,null);
    }
    private static ArrayImage arrayImage(Object array,long start,int length,ArrayImage before){
        if(array.getClass().getComponentType().isPrimitive()){
            if(length>0&&length<=Long.BYTES)try{
                long bits=CodeSourceBridge.executionArrayBits(array,start,length);
                if(before!=null&&before.start==start&&before.length==length&&(before.values==null||before.values instanceof byte[])&&imageBits(before)==bits)return before;
                return new ArrayImage(start,length,bits);
            }catch(NativeControl.HeapImageUnavailable unavailable){return null;}
            byte[] bytes=CodeSourceBridge.executionArrayImage(array,start,length);
            return bytes==null?null:new ArrayImage(start,length,bytes);
        }
        Value[] values=new Value[length];Object[] references=(Object[])array;
        for(int i=0;i<length;i++)values[i]=new Value(references[(int)start+i],true);
        return new ArrayImage(start,length,values);
    }
    private static ArrayImage slice(ArrayImage image,long start,int length){
        if(image==null)return null;if(image.start==start&&image.length==length)return image;
        int relative=Math.toIntExact(start-image.start);
        if(relative<0||length<0||relative>image.length-length)throw new IllegalArgumentException("ARRAY_IMAGE_SLICE_OUTSIDE_RANGE");
        // Both snapshots and their backing arrays remain protected. A range
        // split needs only a window into the captured image, not another copy.
        return new ArrayImage(image,start,length,relative);
    }
    private static boolean sameImage(ArrayImage left,ArrayImage right){
        if(left==null||right==null||left.start!=right.start||left.length!=right.length)return false;
        return sameImageSlice(left,right,left.start,left.length);
    }
    private static boolean sameImageSlice(ArrayImage left,ArrayImage right,long start,int length){
        if(left==null||right==null||left.start!=start||left.length!=length)return false;
        long relative=start-right.start;if(relative<0||length<0||relative>right.length-length)return false;
        int from=(int)relative;
        if(left.values==null||left.values instanceof byte[]){
            if(right.values!=null&&!(right.values instanceof byte[]))return false;
            if(left.values instanceof byte[] bytes&&right.values instanceof byte[] other)
                return Arrays.equals(bytes,left.offset,left.offset+length,other,right.offset+from,right.offset+from+length);
            return imageBits(left)==imageBits(right,from,length);
        }
        if(!(right.values instanceof Value[] other))return false;Value[] values=(Value[])left.values;
        for(int i=0;i<length;i++)if(!sameValue(values[left.offset+i],other[right.offset+from+i]))return false;return true;
    }
    private static long maskImageBits(long bits,int length){return length==Long.BYTES?bits:bits&((1L<<(length*Byte.SIZE))-1);}
    private static long imageBits(ArrayImage image){
        return imageBits(image,0,image.length);
    }
    private static long imageBits(ArrayImage image,int from,int length){
        if(image.values==null)return maskImageBits(image.bits>>>(from*Byte.SIZE),length);
        byte[] bytes=(byte[])image.values;long bits=0;
        for(int i=0;i<length;i++)bits|=(bytes[image.offset+from+i]&0xffL)<<(i*Byte.SIZE);return bits;
    }
    private static boolean matchesArrayImage(Object array,ArrayImage expected){
        return expected!=null&&matchesArrayImage(array,expected.start,expected.length,expected);
    }
    private static boolean matchesArrayImage(Object array,long start,int length,ArrayImage expected){
        if(expected==null||expected.start!=start||expected.length!=length)return false;
        if(array.getClass().getComponentType().isPrimitive()){
            if(length>0&&length<=Long.BYTES)try{
                long bits=CodeSourceBridge.executionArrayBits(array,start,length);
                return (expected.values==null||expected.values instanceof byte[])&&bits==imageBits(expected);
            }catch(NativeControl.HeapImageUnavailable unavailable){return false;}
            byte[] bytes=CodeSourceBridge.executionArrayImage(array,start,length);if(bytes==null)return false;
            CodeSourceBridge.executionControls((Object)bytes);
            if(expected.values instanceof byte[] wanted)return Arrays.equals(bytes,0,length,wanted,expected.offset,expected.offset+length);
            return length==0&&expected.values==null&&expected.bits==0;
        }
        Value[] values=new Value[length];Object[] references=(Object[])array;
        for(int index=0;index<length;index++)values[index]=new Value(references[(int)start+index],true);
        CodeSourceBridge.executionControls((Object)values);
        if(!(expected.values instanceof Value[] wanted))return false;
        for(int index=0;index<length;index++)if(!sameValue(values[index],wanted[expected.offset+index]))return false;
        return true;
    }
    private static ArrayRevision slice(ArrayRevision head,long start,int length){
        List<ArrayRevision> history=new TraceList<>();for(ArrayRevision entry=head;entry!=null;entry=entry.previous())history.add(entry);
        ArrayRevision result=null;for(int i=history.size()-1;i>=0;i--){ArrayRevision entry=history.get(i);
            result=new ArrayRevision(slice(entry.before(),start,length),slice(entry.after(),start,length),entry.sources(),entry.prior(),result,entry.complete(),entry.baseline());}
        return result;
    }
    private static void splitArrayRange(Heap heap,long boundary){
        var entry=heap.arrayRanges.floorEntry(boundary);if(entry==null)return;ArrayRange range=entry.getValue();long end=range.start+range.length;
        if(boundary<=range.start||boundary>=end)return;
        int left=(int)(boundary-range.start),right=(int)(end-boundary);
        heap.arrayRanges.put(range.start,new ArrayRange(range.start,left,slice(range.head,range.start,left)));
        heap.arrayRanges.put(boundary,new ArrayRange(boundary,right,slice(range.head,boundary,right)));
    }
    private static Sources arraySources(Heap heap,long start,long length){
        Sources sources=NONE;long end=start+length,cursor=start;
        var first=heap.arrayRanges.floorEntry(start);long from=first==null?start:first.getKey();
        for(ArrayRange range:heap.arrayRanges.tailMap(from,true).values()){
            if(range.start>=end)break;if(range.start+range.length<=start)continue;
            if(range.start>cursor)sources=join(sources,heap.arrayBaseline);
            sources=join(sources,range.head.sources());cursor=Math.max(cursor,range.start+range.length);
        }
        if(cursor<end)sources=join(sources,heap.arrayBaseline);
        for(ArrayChange change:heap.arrayChanges)if(start<change.start+change.length&&change.start<end)sources=join(sources,change.written,UNKNOWN);
        return sources;
    }
    private static ArrayChange beginArrayChange(Object carrier,Heap heap,long start,long length,Sources written,boolean typed){
        ArrayChange root=null;long end=start+length;
        for(int i=heap.arrayChanges.size()-1;i>=0;i--){ArrayChange pending=heap.arrayChanges.get(i);
            if(pending.thread==Thread.currentThread()&&start>=pending.start&&end<=pending.start+pending.length){root=pending.root;break;}
        }
        ArrayChange change=new ArrayChange(carrier,heap,start,length,written,root);change.complete=typed;change.images=typed||carrier.getClass().getComponentType().isPrimitive();
        if(root!=null){root.written=join(root.written,written);root.complete&=typed;root.images&=change.images;}
        for(ArrayChange pending:heap.arrayChanges)if(pending.root!=change.root&&start<pending.start+pending.length&&pending.start<end){
            pending.root.overlap=true;change.root.overlap=true;
        }
        if(root==null){
            // Only the root consumes before-images. A single chunk lives in
            // the receipt; larger windows retain every exact original chunk.
            if(length>65536){change.parts=new ArrayImage[(int)((length-1)/65536+1)];CodeSourceBridge.executionControls((Object)change.parts);}
            int part=0;for(long at=start;at<end;){int count=(int)Math.min(65536,end-at);
                ArrayImage previous=null;
                if(change.images&&count<=8&&carrier.getClass().getComponentType().isPrimitive()){
                    ArrayRange range=heap.arrayRanges.get(at);
                    if(range!=null&&range.start==at&&range.length==count&&range.head.after()!=null
                            &&range.head.after().start==at&&range.head.after().length==count)previous=range.head.after();
                }
                // Reuse only after arrayImage reads and compares the actual bits.
                ArrayImage image=change.images?arrayImage(carrier,at,count,previous):null;
                if(change.parts==null)change.before=image;else change.parts[part++]=image;
                if(image==null)change.complete=false;at+=count;
            }
        }
        heap.arrayChanges.add(change);return change;
    }
    private static void refreshArraySources(Object carrier,Heap heap,long start,long length){
        int unit=arrayUnit(carrier),first=(int)(start/unit),end=(int)((start+length+unit-1)/unit);
        int previous=first;
        var floor=heap.arrayRanges.floorEntry((long)first*unit);long from=floor==null?(long)first*unit:floor.getKey();
        for(ArrayRange range:heap.arrayRanges.tailMap(from,true).values()){
            if(range.start>=(long)end*unit)break;
            // Ranges are disjoint and ordered. Rounding a boundary can repeat
            // the preceding element edge, but cannot introduce an unseen edge
            // below it; visit the same union without a temporary sorted set.
            for(int side=0;side<2;side++){
                long boundary=side==0?range.start:range.start+range.length;
                int low=(int)(boundary/unit),high=(int)((boundary+unit-1)/unit);
                if(low>previous&&low<end){
                    replaceRegion(heap,previous,low-previous,arraySources(heap,(long)previous*unit,(long)(low-previous)*unit),false);previous=low;
                }
                if(high>previous&&high<end){
                    replaceRegion(heap,previous,high-previous,arraySources(heap,(long)previous*unit,(long)(high-previous)*unit),false);previous=high;
                }
            }
        }
        if(previous<end)replaceRegion(heap,previous,end-previous,arraySources(heap,(long)previous*unit,(long)(end-previous)*unit),false);
        // A JNI element store must not rescan every previously written element.
        // Use exact keys for a narrow interval and existing entries for a sparse wide one.
        if(end-first<=heap.slots.size()){
            for(int index=first;index<end;index++){Slot slot=heap.slots.get(index);if(slot!=null)refreshArraySlot(heap,slot,index,unit);}
        }else for(var entry:heap.slots.entrySet())if(entry.getKey() instanceof Integer index&&index>=first&&index<end)
            refreshArraySlot(heap,entry.getValue(),index,unit);
    }
    private static void refreshArraySlot(Heap heap,Slot slot,int index,int unit){
        slot.sources=arraySources(heap,(long)index*unit,unit);slot.revision=null;slot.watched=true;slot.version++;
    }
    private static void finishArrayChange(ArrayChange change,boolean written){
        if(change==null||change.closed)return;change.closed=true;Heap heap=change.heap;heap.arrayChanges.remove(change);
        ArrayChange root=change.root;root.changed|=written;
        if(change!=root){root.written=join(root.written,change.written);return;}
        heap.arrayRevision++;
        for(ArrayChange pending:heap.arrayChanges)if(pending.root==root)root.overlap=true;
        long end=root.start+root.length;int part=0;
        for(long at=root.start;at<end;){int length=(int)Math.min(65536,end-at);ArrayImage before=root.parts==null?root.before:root.parts[part++],after=root.images?arrayImage(root.carrier,at,length,before):null;
            boolean same=sameImage(before,after);
            if(!written&&!root.changed&&same&&!root.overlap){at+=length;continue;}
            if(!same)root.changed=true;
            splitArrayRange(heap,at);splitArrayRange(heap,at+length);
            for(long cursor=at;cursor<at+length;){
                ArrayRange previous=heap.arrayRanges.get(cursor);Long next=heap.arrayRanges.higherKey(cursor);
                int count=previous!=null?previous.length:(int)((next==null?at+length:Math.min(next,at+length))-cursor);
                ArrayImage saved,current=slice(after,cursor,count);
                ArrayRevision history=previous==null?null:previous.head;
                Sources prior=history==null?heap.arrayBaseline:history.sources();
                boolean complete=root.complete&&!root.overlap&&written&&before!=null&&current!=null&&(history==null||sameImageSlice(history.after(),before,cursor,count));
                Sources sources=complete?root.written:join(root.written,prior,UNKNOWN);
                if(complete&&history!=null&&history.complete()&&sameSources(sources,history.sources())){
                    saved=history.before();prior=history.prior();history=history.previous();
                }else saved=before==after?current:slice(before,cursor,count);
                ArrayRevision revision=new ArrayRevision(saved,current,sources,prior,history,complete);
                // Splitting above fixed this exact physical span. Its wrapper
                // can stay; every write still installs a new immutable head,
                // which is also checked by every outstanding recovery receipt.
                if(previous==null)heap.arrayRanges.put(cursor,new ArrayRange(cursor,count,revision));else previous.head=revision;
                cursor+=count;
            }
            at+=length;
        }
        refreshArraySources(root.carrier,heap,root.start,root.length);
        if(written||root.changed||root.overlap)observeArrayReads(heap,(int)(root.start/arrayUnit(root.carrier)),
                (int)((root.start+root.length+arrayUnit(root.carrier)-1)/arrayUnit(root.carrier)-root.start/arrayUnit(root.carrier)),root.written);
        root.before=null;root.parts=null;
    }
    private static boolean overlaps(int start,int length,int other,int count){return (long)start<other+(long)count&&(long)other<start+(long)length;}
    private static Slot arraySlot(Heap heap,int index){
        Slot slot=heap.slots.get(index);if(slot!=null)return slot;
        slot=new Slot();for(Region region:heap.regions)if(index>=region.start()&&index-region.start()<region.length()){slot.sources=region.sources();slot.watched=true;break;}
        heap.slots.put(index,slot);
        for(MemoryUse pending:heap.arrayWrites)if(index>=pending.arrayStart&&index-pending.arrayStart<pending.arrayLength)pending.include(slot);
        return slot;
    }
    private static void finishRegion(MemoryUse use,boolean written,boolean serial){
        if(use.arrayStart<0)return;Heap heap=use.holder;heap.arrayWrites.remove(use);
        if(use.arrayChange!=null){finishArrayChange(use.arrayChange,written);return;}
        if(!written&&serial&&!use.rangeOverlap)return;
        Sources changed=use.written;boolean conservative=use.rangeOverlap||!serial||!written;
        if(conservative)for(Prior prior=use.prior;prior!=null;prior=prior.next)changed=join(changed,prior.sources(),prior.slot().sources,UNKNOWN);
        replaceRegion(heap,use.arrayStart,use.arrayLength,changed,conservative);
    }
    private static void replaceRegion(Heap heap,int start,int length,Sources changed,boolean conservative){
        int end=start+length,low=0,high=heap.regions.size();
        // The source partition is already sorted, disjoint and coalesced.
        // Only this interval and its two neighbours can change.
        while(low<high){int middle=(low+high)>>>1;Region region=heap.regions.get(middle);
            if(region.start()+region.length()<=start)low=middle+1;else high=middle;
        }
        int first=low,last=first;
        while(last<heap.regions.size()&&heap.regions.get(last).start()<end){
            if(conservative)changed=join(changed,heap.regions.get(last).sources(),UNKNOWN);last++;
        }
        Region left=first<last&&heap.regions.get(first).start()<start?heap.regions.get(first):null;
        Region right=first<last&&heap.regions.get(last-1).start()+heap.regions.get(last-1).length()>end?heap.regions.get(last-1):null;
        int mergedStart=start,mergedEnd=end;
        if(left!=null&&sameSources(left.sources(),changed)){mergedStart=left.start();changed=left.sources();left=null;}
        if(right!=null&&sameSources(changed,right.sources())){mergedEnd=right.start()+right.length();right=null;}
        if(left==null&&first>0){Region previous=heap.regions.get(first-1);
            if(previous.start()+previous.length()==mergedStart&&sameSources(previous.sources(),changed)){
                first--;mergedStart=previous.start();changed=previous.sources();
            }
        }
        if(right==null&&last<heap.regions.size()){Region following=heap.regions.get(last);
            if(following.start()==mergedEnd&&sameSources(changed,following.sources())){last++;mergedEnd=following.start()+following.length();}
        }
        if(left==null&&right==null&&last==first+1){Region existing=heap.regions.get(first);
            if(existing.start()==mergedStart&&existing.length()==mergedEnd-mergedStart&&sameSources(existing.sources(),changed))return;
        }
        heap.regions.removeSpan(first,last);int at=first;
        if(left!=null)heap.regions.add(at++,new Region(left.start(),start-left.start(),left.sources()));
        heap.regions.add(at++,new Region(mergedStart,mergedEnd-mergedStart,changed));
        if(right!=null)heap.regions.add(at,new Region(end,right.start()+right.length()-end,right.sources()));
    }
    private static boolean sameSources(Sources left,Sources right){
        if(left.unknown()!=right.unknown()||left.modules().length!=right.modules().length)return false;
        for(Module module:left.modules()){boolean found=false;for(Module other:right.modules())if(module==other){found=true;break;}if(!found)return false;}return true;
    }
    static Object byteMemoryBefore(Object holder,long offset,long length,Module[] contributors){
        return byteMemoryBefore(holder,offset,length,contributors,false);
    }
    static Object byteMemoryBefore(Object holder,long offset,long length,Module[] contributors,boolean reference){
        bridge();if(holder==null||length==0)return null;
        if(offset<0||length<0||length>Long.MAX_VALUE-offset)return memoryBefore(holder,contributors);
        long end=offset+length;
        if(holder.getClass().isArray()){
            long[] layout;try{layout=ResourceBridge.arrayLayout(holder.getClass());}catch(RuntimeException unavailable){return memoryBefore(holder,contributors);}
            if(layout==null)return memoryBefore(holder,contributors);
            int size=java.lang.reflect.Array.getLength(holder);long limit=layout[0]+size*layout[1];
            if(offset<layout[0]||end>limit)return memoryBefore(holder,contributors);
            int first=(int)((offset-layout[0])/layout[1]),last=(int)((end-1-layout[0])/layout[1]);
            boolean primitive=holder.getClass().getComponentType().isPrimitive();
            boolean typed=primitive||reference&&((offset-layout[0])%layout[1]==0&&length==layout[1]);
            return arrayMemoryBefore(holder,first,last-first+1,false,contributors,primitive?offset-layout[0]:first,primitive?length:last-first+1,typed);
        }
        // An exact typed field span can retain a real before/after value. Partial spans
        // and unresolved layouts continue through the existing conservative invalidation.
        java.lang.reflect.Field exact=null;
        try{exact=ResourceBridge.exactField(holder,offset,length);}catch(RuntimeException|LinkageError unavailable){}
        if(exact!=null)return fieldMemoryBefore(holder,exact.getDeclaringClass(),exact.getName(),exact.getType().descriptorString(),contributors,false);
        synchronized(HEAP){
            reapHeap();Heap heap=HEAP.get(new Carrier(holder,false));if(heap==null)return null;
            List<Slot> selected=new TraceList<>();boolean unresolved=false;
            for(var entry:heap.slots.entrySet())if(entry.getKey() instanceof FieldSlot field){
                Class<?> declaring=field.declaring.get();if(declaring==null){unresolved=true;break;}
                try{
                    var actual=declaring.getDeclaredField(field.name);
                    if(!actual.getType().descriptorString().equals(field.descriptor)){unresolved=true;break;}
                    long[] span=ResourceBridge.fieldSpan(actual,holder);if(span==null){unresolved=true;break;}
                    if(offset<span[0]+span[1]&&span[0]<end)selected.add(entry.getValue());
                }catch(NoSuchFieldException|RuntimeException unavailable){unresolved=true;break;}
            }
            Sources changed=memorySources(contributors);
            return new MemoryUse(heap,unresolved?heap.slots.values():selected,unresolved,changed);
        }
    }
    static Module[] byteMemorySources(Object holder,long offset,long length){
        bridge();if(holder==null||length<=0)return new Module[0];
        synchronized(HEAP){
            reapHeap();Heap heap=HEAP.get(new Carrier(holder,false));if(heap==null)return new Module[0];
            return byteMemorySources(holder,heap,offset,length).modules().clone();
        }
    }
    // The caller holds HEAP and the actual carrier's existing heap. Sources
    // remain immutable; only the public boundary needs a detached Module[].
    private static Sources byteMemorySources(Object holder,Heap heap,long offset,long length){
        Sources sources=UNKNOWN;boolean broad=offset<0||length>Long.MAX_VALUE-offset;long end=broad?0:offset+length;
        int first=-1,count=0;
        if(holder.getClass().isArray()&&!broad){
            long[] layout;try{layout=ResourceBridge.arrayLayout(holder.getClass());}catch(RuntimeException unavailable){layout=null;}
            if(layout==null||layout[1]<=0||offset<layout[0]||end>layout[0]+java.lang.reflect.Array.getLength(holder)*layout[1])broad=true;
            else{first=(int)((offset-layout[0])/layout[1]);count=(int)((end-1-layout[0])/layout[1])-first+1;}
        }
        for(var entry:heap.slots.entrySet()){
            boolean selected=broad;
            if(!selected&&entry.getKey() instanceof Integer index)selected=first>=0&&index>=first&&index-first<count;
            else if(!selected&&entry.getKey() instanceof FieldSlot field){
                Class<?> declaring=field.declaring.get();
                try{
                    var actual=declaring==null?null:declaring.getDeclaredField(field.name);
                    long[] span=actual!=null&&actual.getType().descriptorString().equals(field.descriptor)?ResourceBridge.fieldSpan(actual,holder):null;
                    selected=span==null||offset<span[0]+span[1]&&span[0]<end;
                }catch(NoSuchFieldException|RuntimeException unavailable){selected=true;}
            }
            if(selected)sources=join(sources,entry.getValue().sources);
        }
        for(Region region:heap.regions)if(broad||first>=0&&overlaps(first,count,region.start(),region.length()))sources=join(sources,region.sources());
        for(MemoryUse pending:heap.arrayWrites)if(broad||first>=0&&overlaps(first,count,pending.arrayStart,pending.arrayLength))sources=join(sources,pending.written,UNKNOWN);
        return sources;
    }
    static Object byteMemoryReadBefore(Object carrier,long offset,long length){
        bridge();if(carrier==null||length<=0)return null;
        synchronized(HEAP){
            reapHeap();Heap heap=heapFor(carrier);
            MemoryRead read=new MemoryRead(carrier,heap,offset,length,byteMemorySources(carrier,heap,offset,length));heap.reads.add(read);return read;
        }
    }
    static Module[] byteMemoryReadSources(Object token){
        bridge();if(!(token instanceof MemoryRead read)||read.closed||read.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_HEAP_READ_WINDOW_REQUIRED");
        synchronized(HEAP){reapHeap();read.observed=join(read.observed,byteMemorySources(read.carrier,read.holder,read.offset,read.length));return read.observed.modules().clone();}
    }
    static void byteMemoryReadEnd(Object token){
        bridge();if(token==null)return;if(!(token instanceof MemoryRead read)||read.thread!=Thread.currentThread())throw new SecurityException("ACTUAL_HEAP_READ_WINDOW_REQUIRED");
        synchronized(HEAP){if(read.closed)return;read.closed=true;read.holder.reads.remove(read);}
    }
    private static boolean readOverlaps(MemoryRead read,long offset,long length){
        return offset<0||read.offset<0||length>Long.MAX_VALUE-offset||read.length>Long.MAX_VALUE-read.offset
                ||offset<read.offset+read.length&&read.offset<offset+length;
    }
    private static void observeArrayReads(Heap heap,int start,int length,Sources written){
        if(heap.reads.isEmpty())return;
        for(MemoryRead read:heap.reads){
            long[] layout=read.carrier.getClass().isArray()?ResourceBridge.arrayLayout(read.carrier.getClass()):null;
            if(layout==null||readOverlaps(read,layout[0]+(long)start*layout[1],(long)length*layout[1]))read.observed=join(read.observed,written);
        }
    }
    private static void observeFieldReads(Heap heap,FieldSlot field,Sources written){
        if(heap.reads.isEmpty())return;Class<?> declaring=field.declaring.get();
        for(MemoryRead read:heap.reads){long[] span=null;
            try{var actual=declaring==null?null:declaring.getDeclaredField(field.name);if(actual!=null&&actual.getType().descriptorString().equals(field.descriptor))span=ResourceBridge.fieldSpan(actual,read.carrier);}
            catch(NoSuchFieldException|RuntimeException unknown){}
            if(span==null||readOverlaps(read,span[0],span[1]))read.observed=join(read.observed,written);
        }
    }
    static void memoryContributors(Object token,Module[] contributors){
        bridge();if(!(token instanceof MemoryUse use)||use.closed||use.thread!=Thread.currentThread())return;
        synchronized(HEAP){
            Sources added=memorySources(contributors);use.written=join(use.written,added);
            if(use.arrayChange!=null){use.arrayChange.written=join(use.arrayChange.written,added);use.arrayChange.root.written=join(use.arrayChange.root.written,added);}
            for(Prior prior=use.prior;prior!=null;prior=prior.next)prior.slot().sources=join(prior.slot().sources,added);
        }
    }
    static void memoryAfter(Object token,boolean written){
        bridge();if(!(token instanceof MemoryUse use)||use.closed||use.thread!=Thread.currentThread())return;
        synchronized(HEAP){
            if(use.delegated!=null){use.closed=true;if(!written)use.delegated.before=null;return;}
            use.closed=true;Heap heap=use.holder;
            boolean carrierSerial=heap.version==use.version&&(use.broad?heap.writers==1&&!heap.overlap:heap.writers==0);
            boolean regionSerial=carrierSerial;
            for(Prior prior=use.prior;prior!=null;prior=prior.next){Slot slot=prior.slot();
                boolean serial=carrierSerial&&slot.writers==1&&!slot.overlap&&slot.version==prior.version();slot.writers--;
                regionSerial&=serial;
                if(serial)slot.sources=written?use.written:prior.sources();
                else slot.sources=join(slot.sources,prior.sources(),use.written,UNKNOWN);
                if(written||!serial){
                    FieldCapture capture=use.capturedSlot==slot?use.capture:null;
                    boolean captured=serial&&written&&slot.vmWatched&&capture!=null&&capture.before()!=null&&!use.written.unknown()&&use.written.modules().length!=0;
                    Value after=captured?readValue(capture.carrier().get(),capture.field()):null;
                    if(after!=null){
                        slot.watched=true;slot.revision=revision(capture.before(),after,prior.sources(),use.written,capture.revision());
                    }else slot.revision=null;
                }
                slot.version++;if(slot.writers==0)slot.overlap=false;
            }
            finishRegion(use,written,regionSerial);
            if(written){
                // The root change already notified this interval after its
                // before/after capture. Nested writes still notify on completion.
                if(use.arrayStart>=0&&(use.arrayChange==null||use.arrayChange.root!=use.arrayChange))
                    observeArrayReads(heap,use.arrayStart,use.arrayLength,use.written);
                if(use.capture!=null)observeFieldReads(heap,use.capture.field(),use.written);
                if(use.broad)for(MemoryRead read:heap.reads)read.observed=join(read.observed,use.written,UNKNOWN);
            }
            heap.fieldWrites.remove(use);use.capture=null;use.capturedSlot=null;
            if(use.broad){heap.writers--;heap.version++;if(heap.writers==0)heap.overlap=false;}use.prior=null;use.last=null;
        }
    }
    static String retirementGap(Module module){
        bridge();int fields=0,arrays=0,spans=0,active=0;
        synchronized(HEAP){
            reapHeap();for(var entry:HEAP.entrySet()){
                if(entry.getKey().get()==null)continue;Heap heap=entry.getValue();boolean related=false;
                for(var selected:heap.slots.entrySet()){
                    Slot slot=selected.getValue();boolean contains=false;for(Module source:slot.sources.modules())if(source==module){contains=true;break;}
                    for(Revision revision=slot.revision;!contains&&revision!=null;revision=revision.parent())
                        for(Module source:revision.previous().modules())if(source==module){contains=true;break;}
                    if(!contains)continue;related=true;if(selected.getKey() instanceof FieldSlot)fields++;else arrays++;if(slot.writers!=0)active++;
                }
                for(Region region:heap.regions)for(Module source:region.sources().modules())if(source==module){related=true;spans++;break;}
                for(ArrayRange range:heap.arrayRanges.values()){
                    boolean contains=false;for(ArrayRevision revision=range.head;revision!=null&&!contains;revision=revision.previous())
                        for(Module source:join(revision.sources(),revision.prior()).modules())if(source==module){contains=true;break;}
                    if(contains){related=true;spans++;}
                }
                for(ArrayChange pending:heap.arrayChanges)for(Module source:pending.written.modules())if(source==module){related=true;active++;break;}
                for(MemoryUse pending:heap.arrayWrites)for(Module source:pending.written.modules())if(source==module){related=true;active++;break;}
                if(related&&heap.writers!=0)active++;
            }
        }
        return fields!=0||arrays!=0||spans!=0||active!=0?"EXTERNAL_HEAP_SOURCES_PENDING:fields="+fields+":arrays="+arrays+":arraySpans="+spans+":writers="+active:"";
    }
    private static boolean selected(Sources sources,Set<Module> modules){
        if(sources.unknown()||sources.modules().length==0)return false;
        for(Module source:sources.modules())if(!modules.contains(source))return false;return true;
    }
    private static boolean stoppedSources(Sources sources){
        if(sources.unknown()||sources.modules().length==0)return false;
        for(Module source:sources.modules())if(!TaskBridge.modStopped(source))return false;return true;
    }
    private static boolean containsStopped(Sources sources){
        for(Module source:sources.modules())if(TaskBridge.modStopped(source))return true;return false;
    }
    private static boolean selectedRevision(Sources sources,Revision revision,Set<Module> modules){
        for(Revision current=revision;current!=null;current=current.parent()){
            if(selected(sources,modules))return true;sources=current.previous();
        }
        return false;
    }
    private record RevisionEntry(Revision revision,Sources sources) { }
    private record RevisionPlan(Value value,Sources sources,Revision revision,boolean changed) { }
    /** Each node is one complete typed field write; retaining an outside node keeps its actual after-value. */
    private static RevisionPlan withoutSelected(Sources sources,Revision revision,Set<Module> selected){
        List<RevisionEntry> entries=new TraceList<>();Revision oldest=revision;
        for(Revision current=revision;current!=null;current=current.parent()){
            entries.add(new RevisionEntry(current,sources));sources=current.previous();oldest=current;
        }
        Value value=oldest.before();Sources previous=oldest.previous();Revision rebuilt=null;boolean changed=false;
        for(int i=entries.size()-1;i>=0;i--){
            RevisionEntry entry=entries.get(i);
            if(selected(entry.sources(),selected)){changed=true;continue;}
            Revision retained=entry.revision();rebuilt=new Revision(value,retained.after(),previous,rebuilt);
            value=retained.after();previous=entry.sources();
        }
        return new RevisionPlan(value,previous,rebuilt,changed);
    }
    static Object[][] groupValues(Module[] modules){
        bridge();Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());Collections.addAll(selected,modules);List<Object[]> rows=new TraceList<>();
        synchronized(HEAP){
            reapHeap();for(var entry:HEAP.entrySet()){
                Object carrier=entry.getKey().get();Heap heap=entry.getValue();if(carrier==null||heap.writers!=0)continue;
                if(carrier.getClass().isArray()&&heap.arrayChanges.isEmpty()&&heap.arrayReaders==0&&heap.reads.isEmpty())
                    for(ArrayRange range:heap.arrayRanges.values())if(arraySelected(range.head,selected))
                        rows.add(new Object[]{new ArrayRecovery(carrier,heap,range,selected),carrier,"arrayRange["+range.start+":"+range.length+"]"});
                for(var member:heap.slots.entrySet()){
                    Slot slot=member.getValue();Revision revision=slot.revision;
                    if(!slot.watched||slot.writers!=0||slot.overlap||revision==null||!revision.after().live()||!selectedRevision(slot.sources,revision,selected))continue;
                    String label;
                    if(member.getKey() instanceof FieldSlot field){Class<?> declaring=field.declaring.get();if(declaring==null)continue;label=declaring.getName()+'#'+field.name+field.descriptor;}
                    else if(member.getKey() instanceof Integer index)label="array["+index+"]";else continue;
                    rows.add(new Object[]{new Recovery(carrier,heap,slot,member.getKey(),selected),carrier,label});
                }
            }
        }
        return rows.toArray(Object[][]::new);
    }
    static boolean restoreValue(Object token){
        bridge();if(token instanceof ArrayRecovery array)return restoreArray(array);
        if(!(token instanceof Recovery recovery))throw new SecurityException("ACTUAL_HEAP_RECOVERY_RECEIPT_REQUIRED");
        Object carrier=recovery.carrier.get();if(carrier==null)return true;
        java.util.concurrent.locks.ReentrantLock gate=CodeSourceBridge.executionRecoveryGate(carrier);if(gate==null)return false;
        try{synchronized(HEAP){
            Slot slot=recovery.slot;Heap heap=recovery.holder;Revision revision=recovery.revision;
            if(slot.version!=recovery.version||heap.version!=recovery.carrierVersion||slot.sources!=recovery.sources||slot.revision!=revision
                    ||!slot.watched||slot.writers!=0||heap.writers!=0||slot.overlap||slot.sources.unknown()||!revision.after().live())return false;
            for(Module source:recovery.selected)if(!TaskBridge.modStopped(source))return false;
            RevisionPlan remaining=withoutSelected(slot.sources,revision,recovery.selected);
            if(!remaining.changed()||!remaining.value().live())return false;
            if(!sameValue(revision.after(),remaining.value())&&containsStopped(remaining.sources()))return false;
            Object expected=revision.after().actual(),incoming=remaining.value().actual();
            slot.recovering=Thread.currentThread();boolean restored;
            try{
                if(sameValue(revision.after(),remaining.value()))restored=sameValue(readValue(carrier,recovery.location),revision.after());
                else if(recovery.location instanceof FieldSlot field){
                    Class<?> declaring=field.declaring.get();if(declaring==null)return false;
                    java.lang.reflect.Field actual;try{actual=declaring.getDeclaredField(field.name);}catch(NoSuchFieldException absent){return false;}
                    if(!actual.getType().descriptorString().equals(field.descriptor))return false;
                    Object receiver=java.lang.reflect.Modifier.isStatic(actual.getModifiers())?null:carrier;
                    restored=CodeSourceBridge.executionRestoreField(actual,receiver,expected,incoming);
                }else if(recovery.location instanceof Integer index)restored=CodeSourceBridge.executionRestoreArray(carrier,index,expected,incoming);
                else return false;
            }finally{slot.recovering=null;}
            if(!restored||slot.version!=recovery.version||heap.version!=recovery.carrierVersion){slot.sources=join(slot.sources,recovery.sources,UNKNOWN);slot.revision=null;slot.version++;return false;}
            slot.sources=remaining.sources();slot.revision=remaining.revision();slot.version++;
            if(recovery.location instanceof Integer)heap.arrayRevision++;
            if(recovery.location instanceof Integer index)replaceRegion(heap,index,1,slot.sources,false);
            return true;
        }}finally{gate.unlock();}
    }
    private static boolean arraySelected(ArrayRevision head,Set<Module> selected){
        for(ArrayRevision revision=head;revision!=null&&revision.complete();revision=revision.previous())if(!revision.baseline()&&selected(revision.sources(),selected))return true;
        return false;
    }
    private static boolean restoreArrayImage(Object carrier,ArrayImage expected,ArrayImage incoming){
        if(expected==null||incoming==null||expected.start!=incoming.start||expected.length!=incoming.length)return false;
        if(expected.values==null||expected.values instanceof byte[])return (incoming.values==null||incoming.values instanceof byte[])
                &&CodeSourceBridge.executionRestoreArrayBytes(carrier,expected.start,imageBytes(expected),imageBytes(incoming));
        if(!(incoming.values instanceof Value[]))return false;
        Value[] prior=(Value[])expected.values,values=(Value[])incoming.values;
        Object[] before=new Object[expected.length],after=new Object[incoming.length];
        for(int i=0;i<expected.length;i++){Value previous=prior[expected.offset+i],next=values[incoming.offset+i];
            if(!previous.live()||!next.live())return false;before[i]=previous.actual();after[i]=next.actual();}
        if(!matchesArrayImage(carrier,expected))return false;
        for(int i=0;i<expected.length;i++)if(!CodeSourceBridge.executionRestoreArray(carrier,(int)expected.start+i,before[i],after[i]))return false;
        return matchesArrayImage(carrier,incoming);
    }
    private static byte[] imageBytes(ArrayImage image){
        if(image.values==null){
            byte[] bytes=new byte[image.length];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(image.bits>>>(i*Byte.SIZE));
            CodeSourceBridge.executionControls((Object)bytes);return bytes;
        }
        byte[] bytes=(byte[])image.values;
        if(image.offset==0&&image.length==bytes.length)return bytes;
        byte[] selected=Arrays.copyOfRange(bytes,image.offset,image.offset+image.length);
        CodeSourceBridge.executionControls((Object)selected);return selected;
    }
    private static boolean restoreArray(ArrayRecovery recovery){
        Object carrier=recovery.carrier.get();if(carrier==null)return true;
        java.util.concurrent.locks.ReentrantLock gate=CodeSourceBridge.executionRecoveryGate(carrier);if(gate==null)return false;
        try{synchronized(HEAP){
            Heap heap=recovery.heap;ArrayRange range=recovery.range;ArrayRevision head=recovery.head;
            if(heap.arrayRanges.get(range.start)!=range||range.head!=head||heap.writers!=0||!heap.arrayChanges.isEmpty()||heap.arrayReaders!=0||!heap.reads.isEmpty())return false;
            for(Module source:recovery.selected)if(!TaskBridge.modStopped(source))return false;
            List<ArrayRevision> history=new TraceList<>();for(ArrayRevision revision=head;revision!=null;revision=revision.previous()){
                if(!revision.complete()||revision.before()==null||revision.after()==null)break;history.add(revision);
            }
            if(history.isEmpty())return false;
            ArrayRevision oldest=history.get(history.size()-1),rebuilt=oldest.previous();ArrayImage value=oldest.before();Sources sources=oldest.prior();boolean changed=false;
            for(int i=history.size()-1;i>=0;i--){ArrayRevision entry=history.get(i);
                if(entry.baseline())continue;
                if(selected(entry.sources(),recovery.selected)){changed=true;continue;}
                rebuilt=new ArrayRevision(value,entry.after(),entry.sources(),sources,rebuilt,true);value=entry.after();sources=entry.sources();
            }
            if(!changed)return false;
            if(!sameImage(head.after(),value)&&containsStopped(sources))return false;
            boolean restored=sameImage(head.after(),value)?matchesArrayImage(carrier,range.start,range.length,head.after()):restoreArrayImage(carrier,head.after(),value);
            if(!restored){range.head=new ArrayRevision(head.before(),head.after(),join(head.sources(),UNKNOWN),head.prior(),head.previous(),false);return false;}
            // Keep an observed baseline even when no write remains, so later partial writes
            // inherit the actual restored provenance instead of the retired writer.
            range.head=rebuilt!=null?rebuilt:new ArrayRevision(value,value,sources,sources,null,true,true);
            heap.arrayRevision++;
            refreshArraySources(carrier,heap,range.start,range.length);return true;
        }}finally{gate.unlock();}
    }
    private static void reapHeap(){for(Carrier stale;(stale=(Carrier)DEAD_HEAP.poll())!=null;)HEAP.remove(stale);}
    private static Value readValue(Object carrier,Object location){
        if(carrier==null)return null;
        if(location instanceof Integer index){
            try{return new Value(java.lang.reflect.Array.get(carrier,index),!carrier.getClass().getComponentType().isPrimitive());}
            catch(RuntimeException unavailable){return null;}
        }
        if(location instanceof FieldSlot field){
            Class<?> declaring=field.declaring.get();if(declaring==null)return null;
            Object[] observed=CodeSourceBridge.executionReadField(carrier,declaring,field.name,field.descriptor);
            if(observed==null||observed.length!=1)return null;
            return new Value(observed[0],field.descriptor.charAt(0)=='L'||field.descriptor.charAt(0)=='[');
        }
        return null;
    }
    private static boolean sameValue(Value left,Value right){
        if(left==null||right==null||!left.live()||!right.live())return false;
        if(left.reference!=null||right.reference!=null||left.nil||right.nil)return left.actual()==right.actual();
        if(left.scalar instanceof Float a&&right.scalar instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(left.scalar instanceof Double a&&right.scalar instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);
        return Objects.equals(left.scalar,right.scalar);
    }
    private static Revision revision(Value before,Value after,Sources prior,Sources written,Revision previous){
        if(previous!=null&&!sameValue(previous.after(),before)){previous=null;prior=join(prior,UNKNOWN);}
        if(previous!=null&&sameSources(written,prior))return new Revision(previous.before(),after,previous.previous(),previous.parent());
        return new Revision(before,after,prior,previous);
    }
    private static void finishHeap(Frame frame,boolean success,Object reference){
        HeapUse use=frame.heap;frame.heap=null;if(use==null||use.closed)return;use.closed=true;
        synchronized(HEAP){
            Slot slot=use.slot;
            if(use.write){
                boolean serial=slot.writers==1&&!slot.overlap&&slot.version==use.version&&use.holder.writers==0&&use.holder.version==use.carrierVersion;slot.writers--;
                slot.sources=serial?(success?use.written:use.prior):join(slot.sources,use.prior,use.written,UNKNOWN);slot.version++;if(slot.writers==0)slot.overlap=false;
                if(use.arrayChange!=null)finishArrayChange(use.arrayChange,success);
                else if(success){Revision previous=slot.revision;
                    boolean fieldObserved=use.location instanceof FieldSlot&&slot.vmWatched&&use.observed;
                    boolean captured=serial&&(!(use.location instanceof FieldSlot)||fieldObserved)&&!use.written.unknown()&&use.written.modules().length!=0&&use.before!=null;
                    Value after=captured?readValue(use.carrier.get(),use.location):null;
                    if(after==null)slot.revision=null;
                    else {if(fieldObserved)slot.watched=true;slot.revision=revision(use.before,after,use.prior,use.written,previous);}
                }else if(!serial)slot.revision=null;
                if(success&&use.location instanceof FieldSlot field)observeFieldReads(use.holder,field,slot.sources);
                if(success&&frame.operands!=null)bind(frame.operands[frame.operands.length-1].alias(),reference);
            }else if(success&&!frame.stack.isEmpty()){
                boolean serial=use.readable&&slot.writers==0&&slot.version==use.version&&use.holder.writers==0&&use.holder.version==use.carrierVersion;
                Sources stored=serial?use.prior:join(use.prior,slot.sources,UNKNOWN);
                if(use.arrayRead!=null)stored=join(stored,use.arrayRead.observed,arraySources(use.holder,(long)(Integer)use.location*arrayUnit(use.arrayRead.carrier),arrayUnit(use.arrayRead.carrier)));
                int top=frame.stack.size()-1;Cell value=frame.stack.get(top);Cell result=new Cell(join(value.sources(),stored),value.width(),value.alias());frame.stack.set(top,result);
                bind(result.alias(),reference);frame.active=source(result);
            }
            if(use.arrayReading){use.holder.arrayReaders--;use.arrayReading=false;use.arrayRead.closed=true;use.holder.reads.remove(use.arrayRead);}
        }
    }
    static void heapAfter(Object token,int instruction,Object reference,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=site(token,instruction,caller,depth);if(frame==null||frame.instruction!=instruction)return;
        int opcode=frame.method.operations[instruction][0];
        if(frame.heap==null&&(opcode==178||opcode==180||opcode>=46&&opcode<=53)&&!frame.stack.isEmpty()){
            int top=frame.stack.size()-1;Cell previous=frame.stack.get(top);Cell value=new Cell(join(previous.sources(),UNKNOWN),previous.width(),previous.alias());frame.stack.set(top,value);bind(value.alias(),reference);frame.active=source(value);
        }else finishHeap(frame,true,reference);
    }
    static void before(Object token,int instruction,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=frame(token,caller,depth);if(frame==null)return;
        if(instruction<0||instruction>=frame.method.operations.length||!Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),instruction))return;
        int[] operation=frame.method.operations[instruction];int opcode=operation[0],operand=operation[1],consumed=operation[2],width=operation[3],end=operation[4];
        Sources direct=join(frame.method.sources[instruction],controls(frame,instruction),frame.entry,frame.declaration);
        finishHeap(frame,false,null);frame.instruction=instruction;frame.returned=null;frame.operands=null;
        if(opcode>=21&&opcode<=25){
            Cell value=frame.locals[operand];Alias alias=value==null?null:value.alias();if(opcode==25&&alias==null){alias=new Alias();if(value!=null)frame.locals[operand]=new Cell(value.sources(),value.width(),alias);}
            Sources source=source(value);frame.stack.add(new Cell(join(direct,source),width,alias));frame.active=join(direct,source);
        }else if(opcode>=54&&opcode<=58){
            Cell value=pop(frame);frame.active=join(direct,source(value));frame.locals[operand]=new Cell(frame.active,value.width(),value.alias());if(value.width()==2&&operand+1<frame.locals.length)frame.locals[operand+1]=null;
        }else if(opcode==132){
            Cell value=frame.locals[operand];frame.active=join(direct,source(value));frame.locals[operand]=new Cell(frame.active,1);
        }else if(opcode>=89&&opcode<=95){
            duplicate(frame,opcode,direct);
        }else if(opcode==88){
            Cell first=pop(frame);frame.active=join(direct,first.sources());if(first.width()==1)frame.active=join(frame.active,pop(frame).sources());
        }else if(opcode>=172&&opcode<=177){
            frame.returned=opcode==177?null:pop(frame);frame.active=frame.returned==null?direct:join(direct,source(frame.returned));
            if(frame.returned!=null)frame.returned=new Cell(frame.active,frame.returned.width(),frame.returned.alias());
        }else if(opcode>=182&&opcode<=186){
            Cell[] values=inputs(frame,consumed);frame.active=used(values,direct);frame.call=new Call(frame,instruction,width,values,direct);
        }else {
            Cell[] values=inputs(frame,consumed);frame.operands=values;frame.active=used(values,direct);
            if(width!=0){Alias alias=operation.length>5&&operation[5]!=0?(opcode==192&&values.length!=0?values[0].alias():new Alias()):null;frame.stack.add(new Cell(frame.active,width,alias));}
        }
        frame.thrown=frame.active;if(end>=0)frame.controls.add(new Control(instruction,end,frame.active));
    }
    private static void duplicate(Frame frame,int opcode,Sources direct){
        Cell a=pop(frame),b,c,d;List<Cell> values=new TraceList<>();
        switch(opcode){
            case 89->Collections.addAll(values,a,a);
            case 90->{b=pop(frame);Collections.addAll(values,a,b,a);}
            case 91->{b=pop(frame);if(b.width()==2)Collections.addAll(values,a,b,a);else{c=pop(frame);Collections.addAll(values,a,c,b,a);}}
            case 92->{if(a.width()==2)Collections.addAll(values,a,a);else{b=pop(frame);Collections.addAll(values,b,a,b,a);}}
            case 93->{b=pop(frame);if(a.width()==2)Collections.addAll(values,a,b,a);else{c=pop(frame);Collections.addAll(values,b,a,c,b,a);}}
            case 94->{b=pop(frame);if(a.width()==2){if(b.width()==2)Collections.addAll(values,a,b,a);else{c=pop(frame);Collections.addAll(values,a,c,b,a);}}
                else{c=pop(frame);if(c.width()==2)Collections.addAll(values,b,a,c,b,a);else{d=pop(frame);Collections.addAll(values,b,a,d,c,b,a);}}}
            case 95->{b=pop(frame);Collections.addAll(values,a,b);}
            default->throw new IllegalArgumentException("ACTUAL_STACK_COPY_OPCODE_REQUIRED");
        }
        frame.active=direct;for(Cell value:values){Sources source=join(direct,source(value));frame.stack.add(new Cell(source,value.width(),value.alias()));frame.active=join(frame.active,source);}
    }
    static void afterCall(Object token,int instruction,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=frame(token,caller,depth);if(frame==null||frame.call==null||frame.call.instruction!=instruction
                ||!Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),instruction))return;
        Call call=frame.call;frame.call=null;
        if(call.width!=0){
            Sources result=call.normal?join(call.direct,call.observed,call.returned==null?NONE:source(call.returned)):join(call.direct,call.observed,used(call.arguments,NONE),UNKNOWN);
            Alias alias=call.normal&&call.returned!=null?call.returned.alias():frame.method.operations[instruction].length>5&&frame.method.operations[instruction][5]!=0?new Alias():null;
            frame.stack.add(new Cell(result,call.width,alias));frame.active=result;
        }else frame.active=call.direct;
    }
    static void caught(Object token,int instruction,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=frame(token,caller,depth);if(frame==null||!Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),instruction))return;
        finishHeap(frame,false,null);frame.stack.clear();frame.call=null;frame.operands=null;frame.stack.add(new Cell(frame.thrown,1,new Alias()));frame.active=frame.thrown;frame.returned=null;
    }
    static void exit(Object token,boolean normal,StackWalker.StackFrame caller,int depth){
        bridge();Frame frame=frame(token,caller,depth);if(frame==null||!frame.method.sites.containsKey(caller.getByteCodeIndex()))return;
        close(frame,normal);
    }
    private static void close(Frame frame,boolean normal){
        frame.closed=true;
        if(normal&&frame.incoming!=null&&frame.parent==frame.incoming.caller&&frame.parent.call==frame.incoming){
            frame.incoming.returned=frame.returned;frame.incoming.normal=true;frame.incoming.partial=frame.method.constructor&&(frame.initializedReceiver==null||frame.initializedReceiver.actual==null);
        }
        if(!normal&&frame.incoming!=null&&frame.parent==frame.incoming.caller&&frame.parent.call==frame.incoming)
            frame.parent.thrown=join(frame.parent.thrown,frame.thrown);
        if(frame.parent==null)CURRENT.remove();else CURRENT.set(frame.parent);
        finishHeap(frame,false,null);frame.stack.clear();frame.controls.clear();frame.deferred.clear();Arrays.fill(frame.locals,null);frame.active=NONE;frame.thrown=NONE;frame.call=null;frame.returned=null;frame.operands=null;
    }
    // An absent current frame contains no state or authority to disclose. Read
    // that live thread-local first; actual frame results still require the bridge.
    static boolean hasFrame(){Frame frame=CURRENT.get();if(frame==null)return false;bridge(CALLER.getCallerClass());return true;}
    static Module[] sources(){Frame frame=CURRENT.get();if(frame==null)return new Module[0];bridge(CALLER.getCallerClass());return frame.closed?new Module[0]:frame.active.modules().clone();}
    static boolean unknown(){Frame frame=CURRENT.get();if(frame==null)return false;bridge(CALLER.getCallerClass());return !frame.closed&&frame.active.unknown();}
    static Module[] frameSources(StackWalker.StackFrame caller){
        Frame current=CURRENT.get();if(current==null)return null;
        // This entry is called directly by the real CodeSourceBridge for every
        // walked frame. Authenticate that caller without walking the same stack again.
        bridge(CALLER.getCallerClass());for(Frame frame=current;frame!=null;frame=frame.parent)
            if(!frame.closed&&frame.thread==Thread.currentThread()&&caller.getDeclaringClass()==frame.declaring
                    &&(caller.getMethodName()+caller.getDescriptor()).equals(frame.method.selector)
                    &&Objects.equals(frame.method.sites.get(caller.getByteCodeIndex()),frame.instruction))return frame.active.modules().clone();
        return null;
    }
}
