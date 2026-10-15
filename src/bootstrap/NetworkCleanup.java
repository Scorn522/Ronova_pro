package dev.ronova.pro.bootstrap;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Exact objects reached by one original Netty write's retirement housekeeping. */
final class NetworkCleanup {
    private static final String CTX="io.netty.channel.AbstractChannelHandlerContext",WRITE=CTX+"$WriteTask";
    private static final String EXECUTOR="io.netty.util.concurrent.SingleThreadEventExecutor";
    private static final String DEFAULT_REJECT="io.netty.util.concurrent.RejectedExecutionHandlers$1";
    private static final String MPSC="io.netty.util.internal.shaded.org.jctools.queues.",MPSC_BASE=MPSC+"BaseMpscLinkedArrayQueue",MPSC_CHUNK=MPSC+"MpscChunkedArrayQueue",
            MPSC_ATOMIC_BASE=MPSC+"atomic.BaseMpscLinkedAtomicArrayQueue",MPSC_ATOMIC_CHUNK=MPSC+"atomic.MpscChunkedAtomicArrayQueue";
    private static final String SAFE_EXECUTE="(Lio/netty/util/concurrent/EventExecutor;Ljava/lang/Runnable;Lio/netty/channel/ChannelPromise;Ljava/lang/Object;Z)Z";
    private static final long CONTEXT=1,PIPELINE=2,CHANNEL=4,UNSAFE=8,OUTBOUND=16,ENTRY=32,PROMISE=64,MESSAGE=128,
            ALLOCATOR=256,METRIC=512,COUNTER=1024,CELL=2048,LOCK=4096,SYNC=8192,NODE=16384,CHUNK=32768,
            ARENA=65536,LIST=131072,SUBPAGE=262144,CACHE=524288,REGION=1048576,REGION_ENTRY=2097152,
            HANDLE=4194304,LOCAL_POOL=8388608,QUEUE=16777216,QUEUE_ARRAY=33554432,STRUCTURE=67108864,
            STRUCTURE_ARRAY=134217728,COUNTER_ARRAY=268435456,MARKER=536870912,
            CHUNK_NEIGHBOR=1073741824L,SUBPAGE_NEIGHBOR=2147483648L,ENTRY_POOL=4294967296L,
            LEAK=8589934592L,LEAK_MAP=17179869184L,LEAK_TABLE=34359738368L,LEAK_NODE=68719476736L,LEAK_COUNTER_ARRAY=137438953472L,
            LOCAL_MAP=274877906944L,LOCAL_ARRAY=549755813888L,REMOVE_MAP=1099511627776L,REMOVE_TABLE=2199023255552L;
    private record Visit(Object value,long role){}
    private record Allocation(Object chunk,long handle,int capacity,Object cache,Object nioBuffer){}
    private static final class Graph {
        final Object context,message,promise,task;
        final int phase;
        final Class<?> anchor;
        final IdentityHashMap<Object,Long> roles=new IdentityHashMap<>();
        final IdentityHashMap<Object,Allocation> allocations=new IdentityHashMap<>();
        final IdentityHashMap<Object,Integer> entrySlots=new IdentityHashMap<>();
        final ArrayDeque<Visit> pending=new ArrayDeque<>();
        int removeSlot=-1;
        Graph(Object context,Object message,Object promise,Object task,int phase,Class<?> anchor){
            this.context=context;this.message=message;this.promise=promise;this.task=task;this.phase=phase;this.anchor=anchor;
            protect(this,roles,allocations,entrySlots,pending);
        }
        void add(Object value,long role){
            if(value==null)return;long before=roles.getOrDefault(value,0L),added=role&~before;
            if(added!=0){roles.put(value,before|role);Visit visit=new Visit(value,added);protect(visit);pending.addLast(visit);}
        }
        boolean has(Object value,long role){return value!=null&&(roles.getOrDefault(value,0L)&role)!=0;}
    }
    private NetworkCleanup(){}

    /** Called before Netty clears a chunk handle or detaches a flushed entry. */
    static Object capture(Object context,Object message,Object promise,Object task,int phase){
        Class<?> anchor=parent(context,CTX);if(anchor==null)return null;
        Graph graph=new Graph(context,message,promise,task,phase,anchor);
        graph.add(context,CONTEXT);graph.add(promise,PROMISE);graph.add(currentMap(anchor),LOCAL_MAP);
        if(phase==0)addMessage(graph,message);
        expand(graph);return graph;
    }
    static boolean allowed(Object context,Object message,Object promise,Object task,Class<?> boundary,String boundaryMethod,int phase,
                           Object receiver,Field field,Object value){
        if(receiver==null||field==null||Modifier.isStatic(field.getModifiers())||!field.getDeclaringClass().isInstance(receiver))return false;
        Object retained=NetworkBridge.retirementGraph();
        if(!(retained instanceof Graph graph)||graph.context!=context||graph.message!=message||graph.promise!=promise||graph.task!=task||graph.phase!=phase)return false;
        if(!boundaryAllowed(boundary,boundaryMethod,phase))return false;
        refresh(graph);
        boolean target=false;String owner=field.getDeclaringClass().getName(),name=field.getName();
        if(phase==1&&standard(receiver,graph.anchor,"io.netty.util.concurrent.DefaultPromise$CauseHolder")&&owner.equals("io.netty.util.concurrent.DefaultPromise$CauseHolder")&&name.equals("cause")&&NetworkBridge.rejectionCause(value))target=true;
        if(phase==-1&&receiver==task&&owner.equals(WRITE)&&value==null&&oneOf(name,"ctx","msg","promise"))target=true;
        if(graph.has(receiver,OUTBOUND)&&owner.equals("io.netty.channel.ChannelOutboundBuffer")){
            if(name.equals("totalPendingSize")&&value instanceof Long amount&&amount>=0)target=true;
            if(name.equals("unwritable")&&value instanceof Integer bits&&(bits&1)==0)target=true;
            if(phase==2&&oneOf(name,"flushed","nioBufferCount","nioBufferSize","inFail"))target=true;
            if(phase==2&&oneOf(name,"flushedEntry","unflushedEntry","tailEntry")&&(value==null||graph.has(value,ENTRY)))target=true;
            if(name.equals("fireChannelWritabilityChangedTask")&&standard(value,graph.anchor,"io.netty.channel.ChannelOutboundBuffer$2"))target=true;
        }
        if(phase>=0&&graph.has(receiver,PROMISE)&&owner.equals("io.netty.util.concurrent.DefaultPromise")){
            if(name.equals("result")){
                if(receiver==promise&&phase==1&&standard(value,graph.anchor,"io.netty.util.concurrent.DefaultPromise$CauseHolder"))
                    target=NetworkBridge.rejectionCause(read(value,"io.netty.util.concurrent.DefaultPromise$CauseHolder","cause"));
                if(phase==2)target=staticValue(field.getDeclaringClass(),"SUCCESS")==value||staticValue(field.getDeclaringClass(),"UNCANCELLABLE")==value;
            }else if(name.equals("notifyingListeners")&&value instanceof Boolean)target=true;
            else if(name.equals("listeners")&&value==null)target=true;
        }
        if((phase==0||phase==2)&&graph.has(receiver,MESSAGE))target|=messageField(graph,receiver,field,value);
        if(phase==2&&graph.has(receiver,ENTRY)&&owner.equals("io.netty.channel.ChannelOutboundBuffer$Entry")){
            if(oneOf(name,"msg","promise","buf","bufs")&&value==null)target=true;
            if(name.equals("next")&&(value==null||graph.has(value,ENTRY)))target=true;
            if(oneOf(name,"progress","total","pendingSize","count","cancelled"))target=true;
        }
        if(phase==2&&graph.has(receiver,UNSAFE)&&owner.equals("io.netty.channel.AbstractChannel$AbstractUnsafe")&&name.equals("inFlush0")&&value instanceof Boolean)target=true;
        if(phase==2&&graph.has(receiver,CONTEXT)&&owner.equals(CTX)&&name.equals("invokeTasks")&&standard(value,graph.anchor,CTX+"$Tasks"))target=true;
        if(phase==0||phase==2)target|=allocatorField(graph,receiver,field,value);
        if(phase==0||phase==2)target|=leakField(graph,receiver,field,value);
        return target;
    }
    static boolean boundaryAllowed(Class<?> boundary,String method,int phase){
        return NativeControl.networkCleanupBoundary(boundary,method,phase,NetworkBridge.retirementBoundaryDepth());
    }
    /** Only observes the original reject path; it does not enter a write scope. */
    static boolean submissionBoundary(Class<?> boundary,int depth){return NativeControl.networkCleanupBoundary(boundary,"safeExecute",-2,depth);}
    static boolean standardExecutor(Object executor,Class<?> base){
        return executor!=null&&base!=null&&base.getName().equals(EXECUTOR)&&parent(executor,EXECUTOR)==base&&framework(executor.getClass(),base)
                &&oneOf(executor.getClass().getName(),"io.netty.util.concurrent.DefaultEventExecutor","io.netty.channel.DefaultEventLoop","io.netty.channel.nio.NioEventLoop");
    }
    static boolean removalQueue(Object executor,Class<?> base){
        // This branch is reached only after Netty's removeTask returned true.
        // A custom Queue's boolean cannot establish removal of the real task.
        Object queue=NativeControl.networkReadField(executor,base,"taskQueue",false);
        return queue!=null&&queue.getClass()==java.util.concurrent.LinkedBlockingQueue.class;
    }
    static Object rejectionQueue(Object executor,Class<?> base){
        Object queue=NativeControl.networkReadField(executor,base,"taskQueue",false);
        if(queue!=null&&queue.getClass()==java.util.concurrent.LinkedBlockingQueue.class)return queue;
        return mpscExecutor(executor)==base&&mpscBase(queue,base)!=null?queue:null;
    }
    static Class<?> mpscExecutor(Object executor){
        Class<?> base=parent(executor,EXECUTOR);
        return standardExecutor(executor,base)&&executor.getClass().getName().equals("io.netty.channel.nio.NioEventLoop")?base:null;
    }
    /** Exact bounded implementations; unbounded queues have no original full return. */
    static Class<?> mpscBase(Object queue,Class<?> anchor){
        if(queue==null||!framework(queue.getClass(),anchor))return null;
        String concrete=queue.getClass().getName(),name=concrete.equals(MPSC_CHUNK)?MPSC_BASE:concrete.equals(MPSC_ATOMIC_CHUNK)?MPSC_ATOMIC_BASE:null;
        Class<?> base=name==null?null:parent(queue,name);
        return framework(base,anchor)?base:null;
    }
    static Class<?> mpscReadType(Object queue,Class<?> base,int stage){
        if(base==null||stage<0||stage>2)return null;
        Class<?> type=parent(queue,base.getName()+(stage==0?"ColdProducerFields":stage==1?"ProducerFields":"ConsumerFields"));
        return framework(type,base)?type:null;
    }
    static Object defaultRejectHandler(Object executor,Class<?> base){
        Object handler=NativeControl.networkReadField(executor,base,"rejectedExecutionHandler",false);
        if(!standard(handler,base,DEFAULT_REJECT))return null;
        Class<?> factory=resolve(base,"io.netty.util.concurrent.RejectedExecutionHandlers");
        return factory!=null&&framework(factory,base)&&handler==staticValue(factory,"REJECT")?handler:null;
    }
    /** The caller also checks the original array/backing policy and exact boundary. */
    static boolean arrayAllowed(Object array,int index,Object value){
        Object retained=NetworkBridge.retirementGraph();if(!(retained instanceof Graph graph)||array==null||!array.getClass().isArray()||index<0||index>=Array.getLength(array))return false;
        refresh(graph);
        if(graph.has(array,QUEUE_ARRAY)){
            if(value==null)return queueValue(graph,Array.get(array,index));
            return queueValue(graph,value)||graph.has(value,QUEUE_ARRAY|MARKER);
        }
        if(graph.has(array,COUNTER_ARRAY))return value==null||value.getClass().getName().equals("java.util.concurrent.atomic.Striped64$Cell")&&value.getClass().getClassLoader()==null;
        if(graph.has(array,LOCAL_ARRAY)){
            if(value==staticValue(resolve(graph.anchor,"io.netty.util.internal.InternalThreadLocalMap"),"UNSET"))return true;
            for(int slot:graph.entrySlots.values())if(slot==index&&standard(value,graph.anchor,"io.netty.util.Recycler$LocalPool")){graph.add(value,LOCAL_POOL);return true;}
            if(index==graph.removeSlot&&value!=null&&value.getClass().getClassLoader()==null&&value.getClass().getName().equals("java.util.Collections$SetFromMap")){
                Object map=read(value,"java.util.Collections$SetFromMap","m");if(map!=null&&map.getClass()==IdentityHashMap.class){graph.add(map,REMOVE_MAP);return true;}
            }
            return false;
        }
        if(graph.has(array,REMOVE_TABLE)){
            if((index&1)==0)return graph.entrySlots.containsKey(value);
            return value==Boolean.TRUE&&graph.entrySlots.containsKey(Array.get(array,index-1));
        }
        if(graph.has(array,LEAK_COUNTER_ARRAY))return value==null||value.getClass().getClassLoader()==null&&value.getClass().getName().equals("java.util.concurrent.ConcurrentHashMap$CounterCell");
        if(graph.has(array,LEAK_TABLE)){
            Object old=Array.get(array,index);return graph.has(old,LEAK_NODE)&&graph.has(read(old,"java.util.concurrent.ConcurrentHashMap$Node","key"),LEAK)
                    &&value==read(old,"java.util.concurrent.ConcurrentHashMap$Node","next");
        }
        if(graph.has(array,STRUCTURE_ARRAY)){
            if(array.getClass().getComponentType().isPrimitive())return value instanceof Number;
            return value==null||graph.has(value,CHUNK|SUBPAGE|LIST|STRUCTURE);
        }
        return false;
    }
    private static boolean messageField(Graph graph,Object receiver,Field field,Object value){
        String owner=field.getDeclaringClass().getName(),name=field.getName();
        if(owner.equals("io.netty.buffer.AbstractReferenceCountedByteBuf")&&name.equals("refCnt")&&value instanceof Integer bits){
            // The original CAS owns its expected value. A second current read
            // here would turn an ordinary stale CAS into a cleanup exception.
            return bits==1||bits>0&&(bits&1)==0;
        }
        if(owner.equals("io.netty.buffer.UnpooledHeapByteBuf")&&name.equals("array"))return value instanceof byte[] bytes&&bytes.length==0;
        if(owner.equals("io.netty.buffer.UnpooledDirectByteBuf")&&name.equals("buffer"))return value==null;
        if(owner.equals("io.netty.buffer.PooledByteBuf")){
            if(name.equals("handle"))return value instanceof Long handle&&handle==-1;
            if(oneOf(name,"memory","chunk","tmpNioBuf"))return value==null;
        }
        if(owner.equals("io.netty.buffer.AbstractPooledDerivedByteBuf")&&name.equals("parent"))return value==null;
        return false;
    }
    private static boolean allocatorField(Graph graph,Object receiver,Field field,Object value){
        String owner=field.getDeclaringClass().getName(),name=field.getName();
        if(graph.has(receiver,COUNTER)&&owner.equals("java.util.concurrent.atomic.Striped64")){
            if(name.equals("base")&&value instanceof Long)return true;
            if(name.equals("cellsBusy")&&value instanceof Integer busy&&(busy==0||busy==1))return true;
            if(name.equals("cells")&&value instanceof Object[] cells){graph.add(cells,COUNTER_ARRAY);return true;}
        }
        if(graph.has(receiver,COUNTER)&&owner.equals("java.util.concurrent.atomic.AtomicLong")&&name.equals("value")&&value instanceof Long)return true;
        if(graph.has(receiver,CELL)&&owner.equals("java.util.concurrent.atomic.Striped64$Cell")&&name.equals("value")&&value instanceof Long)return true;
        if(graph.has(receiver,HANDLE)&&owner.equals("io.netty.util.Recycler$DefaultHandle")&&name.equals("state")&&value instanceof Integer state&&(state==0||state==1))return true;
        if(hasRole(graph,ENTRY_POOL)&&standard(receiver,graph.anchor,"io.netty.util.Recycler$DefaultHandle")&&owner.equals("io.netty.util.Recycler$DefaultHandle")){
            if(name.equals("localPool")&&graph.has(value,LOCAL_POOL)){graph.add(receiver,HANDLE);return true;}
            if(name.equals("value")&&standard(value,graph.anchor,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")&&read(value,value.getClass().getName(),"recyclerHandle")==receiver){graph.add(receiver,HANDLE);graph.add(value,REGION_ENTRY);return true;}
        }
        if(hasRole(graph,ENTRY_POOL)&&standard(receiver,graph.anchor,"io.netty.util.Recycler$LocalPool")&&owner.equals("io.netty.util.Recycler$LocalPool")){
            if(oneOf(name,"ratioInterval","ratioCounter")&&value instanceof Integer){graph.add(receiver,LOCAL_POOL);return true;}
            if(name.equals("pooledHandles")&&value!=null&&queueClass(value.getClass().getName())&&framework(value.getClass(),graph.anchor)){graph.add(receiver,LOCAL_POOL);graph.add(value,QUEUE);return true;}
        }
        if(receiver==Thread.currentThread()&&owner.equals("io.netty.util.concurrent.FastThreadLocalThread")&&name.equals("threadLocalMap")&&hasRole(graph,ENTRY_POOL)&&standard(value,graph.anchor,"io.netty.util.internal.InternalThreadLocalMap")){graph.add(value,LOCAL_MAP);return true;}
        if(graph.has(receiver,LOCAL_MAP)&&owner.equals("io.netty.util.internal.InternalThreadLocalMap")){
            if(name.equals("indexedVariables")&&value instanceof Object[]){graph.add(value,LOCAL_ARRAY);return true;}
            if(name.equals("futureListenerStackDepth")&&value instanceof Integer depth&&depth>=0)return true;
        }
        if(hasRole(graph,ENTRY_POOL)&&standard(receiver,graph.anchor,"io.netty.util.internal.InternalThreadLocalMap")&&owner.equals("io.netty.util.internal.InternalThreadLocalMap")&&name.equals("indexedVariables")&&value instanceof Object[]){graph.add(receiver,LOCAL_MAP);graph.add(value,LOCAL_ARRAY);return true;}
        if(hasRole(graph,ENTRY_POOL)&&queueClass(receiver.getClass().getName())&&framework(receiver.getClass(),graph.anchor)&&queueField(owner,name)){
            graph.add(receiver,QUEUE);return queueFieldValue(graph,name,value);
        }
        if(standard(receiver,graph.anchor,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")&&owner.equals(receiver.getClass().getName())&&name.equals("recyclerHandle")&&graph.has(value,HANDLE)){
            Object prior=read(value,"io.netty.util.Recycler$DefaultHandle","value");if(prior==null||prior==receiver){graph.add(receiver,REGION_ENTRY);return true;}
        }
        if(standard(receiver,graph.anchor,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")&&owner.equals(receiver.getClass().getName())&&name.equals("recyclerHandle")&&standard(value,graph.anchor,"io.netty.util.Recycler$DefaultHandle")&&graph.has(read(value,"io.netty.util.Recycler$DefaultHandle","localPool"),LOCAL_POOL)){
            Object prior=read(value,"io.netty.util.Recycler$DefaultHandle","value");if(prior==null||prior==receiver){graph.add(value,HANDLE);graph.add(receiver,REGION_ENTRY);return true;}
        }
        if(graph.has(receiver,QUEUE)&&queueField(owner,name))return queueFieldValue(graph,name,value);
        if(graph.has(receiver,CHUNK)&&owner.equals("io.netty.buffer.PoolChunk")){
            if(name.equals("freeBytes")&&value instanceof Integer)return true;
            if(oneOf(name,"prev","next")&&(value==null||graph.has(value,CHUNK|CHUNK_NEIGHBOR)))return true;
            if(name.equals("parent")&&(value==null||graph.has(value,LIST)))return true;
        }
        if(graph.has(receiver,ARENA)&&owner.equals("io.netty.buffer.PoolArena")&&oneOf(name,"deallocationsSmall","deallocationsNormal")&&value instanceof Long)return true;
        if(graph.has(receiver,CHUNK_NEIGHBOR)&&owner.equals("io.netty.buffer.PoolChunk")&&oneOf(name,"prev","next")&&(value==null||graph.has(value,CHUNK|CHUNK_NEIGHBOR)))return true;
        if(graph.has(receiver,LIST)&&owner.equals("io.netty.buffer.PoolChunkList")&&name.equals("head")&&(value==null||graph.has(value,CHUNK|CHUNK_NEIGHBOR)))return true;
        if(graph.has(receiver,SUBPAGE)&&owner.equals("io.netty.buffer.PoolSubpage")){
            if(oneOf(name,"numAvail","nextAvail","bitmapIdx","doNotDestroy"))return true;
            if(oneOf(name,"prev","next")&&(value==null||graph.has(value,SUBPAGE|SUBPAGE_NEIGHBOR)))return true;
        }
        if(graph.has(receiver,SUBPAGE_NEIGHBOR)&&owner.equals("io.netty.buffer.PoolSubpage")&&oneOf(name,"prev","next")&&(value==null||graph.has(value,SUBPAGE|SUBPAGE_NEIGHBOR)))return true;
        if(graph.has(receiver,REGION_ENTRY)&&owner.equals("io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")){
            if(name.equals("chunk"))return value==null||graph.has(value,CHUNK);
            if(name.equals("nioBuffer"))return value==null||graph.has(value,STRUCTURE);
            if(oneOf(name,"handle","normCapacity"))return value instanceof Number;
        }
        if(graph.has(receiver,STRUCTURE)){
            if(owner.equals("io.netty.buffer.LongPriorityQueue")&&name.equals("array")&&value instanceof long[]){graph.add(value,STRUCTURE_ARRAY);return true;}
            if(owner.equals("io.netty.buffer.LongPriorityQueue")&&name.equals("size")&&value instanceof Integer)return true;
            if(owner.equals("io.netty.buffer.LongLongHashMap")&&oneOf(name,"array","mask")){
                if(name.equals("array")&&value instanceof long[])graph.add(value,STRUCTURE_ARRAY);return value instanceof long[]||value instanceof Integer;
            }
        }
        if(graph.has(receiver,SYNC)&&owner.equals("java.util.concurrent.locks.AbstractQueuedSynchronizer")){
            if(name.equals("state")&&value instanceof Integer)return true;
            if(oneOf(name,"head","tail")&&(value==null||jdkNode(value))){graph.add(value,NODE);return true;}
        }
        if(graph.has(receiver,SYNC)&&owner.equals("java.util.concurrent.locks.AbstractOwnableSynchronizer")&&name.equals("exclusiveOwnerThread"))return value==null||value==Thread.currentThread();
        if(graph.has(receiver,NODE)&&owner.equals("java.util.concurrent.locks.AbstractQueuedSynchronizer$Node")){
            if(oneOf(name,"prev","next")&&(value==null||jdkNode(value))){graph.add(value,NODE);return true;}
            if(name.equals("status")&&value instanceof Integer)return true;
            if(name.equals("waiter"))return value==null||value==Thread.currentThread();
        }
        return false;
    }
    private static boolean leakField(Graph graph,Object receiver,Field field,Object value){
        String owner=field.getDeclaringClass().getName(),name=field.getName();
        if(graph.has(receiver,LEAK)&&value==null&&(owner.equals("io.netty.util.ResourceLeakDetector$DefaultResourceLeak")&&name.equals("head")||owner.equals("java.lang.ref.Reference")&&name.equals("referent")))return true;
        if(graph.has(receiver,LEAK_MAP)&&owner.equals("java.util.concurrent.ConcurrentHashMap")){
            if(name.equals("baseCount")&&value instanceof Long)return true;
            if(name.equals("cellsBusy")&&value instanceof Integer busy&&(busy==0||busy==1))return true;
            if(name.equals("counterCells")&&value instanceof Object[]){graph.add(value,LEAK_COUNTER_ARRAY);return true;}
        }
        if(graph.has(receiver,CELL)&&owner.equals("java.util.concurrent.ConcurrentHashMap$CounterCell")&&name.equals("value")&&value instanceof Long)return true;
        if(graph.has(receiver,LEAK_NODE)&&owner.equals("java.util.concurrent.ConcurrentHashMap$Node")&&name.equals("next")){
            Object removed=read(receiver,owner,"next");return graph.has(read(removed,owner,"key"),LEAK)&&value==read(removed,owner,"next");
        }return false;
    }
    private static boolean hasRole(Graph graph,long role){for(long actual:graph.roles.values())if((actual&role)!=0)return true;return false;}
    private static void refresh(Graph graph){
        Object[] values=graph.roles.keySet().toArray();protect((Object)values);for(Object value:values){Visit visit=new Visit(value,graph.roles.get(value));protect(visit);graph.pending.addLast(visit);}
        expand(graph);
    }
    private static void expand(Graph graph){
        IdentityHashMap<Object,Long> visited=new IdentityHashMap<>();
        protect(visited);
        while(!graph.pending.isEmpty()){
            Visit visit=graph.pending.removeFirst();Object value=visit.value();long role=visit.role(),before=visited.getOrDefault(value,0L);role&=~before;if(role==0)continue;visited.put(value,before|role);
            if((role&CONTEXT)!=0)link(graph,value,CTX,"pipeline",PIPELINE);
            if((role&PIPELINE)!=0)link(graph,value,"io.netty.channel.DefaultChannelPipeline","channel",CHANNEL);
            if((role&CHANNEL)!=0)link(graph,value,"io.netty.channel.AbstractChannel","unsafe",UNSAFE);
            if((role&UNSAFE)!=0)link(graph,value,"io.netty.channel.AbstractChannel$AbstractUnsafe","outboundBuffer",OUTBOUND);
            if((role&OUTBOUND)!=0&&graph.phase==2){
                for(String field:new String[]{"flushedEntry","unflushedEntry","tailEntry"})link(graph,value,"io.netty.channel.ChannelOutboundBuffer",field,ENTRY);
            }
            if((role&ENTRY)!=0){
                link(graph,value,"io.netty.channel.ChannelOutboundBuffer$Entry","next",ENTRY);
                graph.add(read(value,"io.netty.channel.ChannelOutboundBuffer$Entry","promise"),PROMISE);
                addMessage(graph,read(value,"io.netty.channel.ChannelOutboundBuffer$Entry","msg"));
                link(graph,value,"io.netty.channel.ChannelOutboundBuffer$Entry","handle",HANDLE);
            }
            if((role&MESSAGE)!=0)messageChildren(graph,value);
            if((role&ALLOCATOR)!=0)link(graph,value,"io.netty.buffer.UnpooledByteBufAllocator","metric",METRIC);
            if((role&METRIC)!=0){link(graph,value,"io.netty.buffer.UnpooledByteBufAllocator$UnpooledByteBufAllocatorMetric","heapCounter",COUNTER);link(graph,value,"io.netty.buffer.UnpooledByteBufAllocator$UnpooledByteBufAllocatorMetric","directCounter",COUNTER);}
            if((role&COUNTER)!=0)link(graph,value,"java.util.concurrent.atomic.Striped64","cells",COUNTER_ARRAY);
            if((role&COUNTER_ARRAY)!=0&&value instanceof Object[] cells)for(Object cell:cells)if(jdkCell(cell))graph.add(cell,CELL);
            if((role&LOCAL_MAP)!=0){
                Object indexed=read(value,"io.netty.util.internal.InternalThreadLocalMap","indexedVariables");graph.add(indexed,LOCAL_ARRAY);
                if(indexed instanceof Object[] items&&graph.removeSlot>=0&&graph.removeSlot<items.length){Object map=read(items[graph.removeSlot],"java.util.Collections$SetFromMap","m");if(map!=null&&map.getClass()==IdentityHashMap.class)graph.add(map,REMOVE_MAP);}
            }
            if((role&REMOVE_MAP)!=0)link(graph,value,"java.util.IdentityHashMap","table",REMOVE_TABLE);
            if((role&LEAK_COUNTER_ARRAY)!=0&&value instanceof Object[] cells)for(Object cell:cells)if(cell!=null&&cell.getClass().getClassLoader()==null&&cell.getClass().getName().equals("java.util.concurrent.ConcurrentHashMap$CounterCell"))graph.add(cell,CELL);
            if((role&LEAK)!=0){
                Object set=read(value,"io.netty.util.ResourceLeakDetector$DefaultResourceLeak","allLeaks");
                if(set!=null&&set.getClass().getClassLoader()==null){
                    Object map=read(set,"java.util.Collections$SetFromMap","m");if(map==null)map=read(set,"java.util.concurrent.ConcurrentHashMap$CollectionView","map");
                    if(map!=null&&map.getClass()==java.util.concurrent.ConcurrentHashMap.class)graph.add(map,LEAK_MAP);
                }
            }
            if((role&LEAK_MAP)!=0){
                Object table=read(value,"java.util.concurrent.ConcurrentHashMap","table");graph.add(table,LEAK_TABLE);
                link(graph,value,"java.util.concurrent.ConcurrentHashMap","counterCells",LEAK_COUNTER_ARRAY);
                if(table instanceof Object[] buckets)for(Object head:buckets){Object previous=null;IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<>();protect(seen);
                    for(Object node=head;node!=null&&seen.put(node,Boolean.TRUE)==null;node=read(node,"java.util.concurrent.ConcurrentHashMap$Node","next")){
                        if(graph.has(read(node,"java.util.concurrent.ConcurrentHashMap$Node","key"),LEAK)){graph.add(node,LEAK_NODE);graph.add(previous,LEAK_NODE);break;}previous=node;
                    }
                }
            }
            if((role&LOCK)!=0)link(graph,value,"java.util.concurrent.locks.ReentrantLock","sync",SYNC);
            if((role&SYNC)!=0){link(graph,value,"java.util.concurrent.locks.AbstractQueuedSynchronizer","head",NODE);link(graph,value,"java.util.concurrent.locks.AbstractQueuedSynchronizer","tail",NODE);}
            if((role&NODE)!=0){link(graph,value,"java.util.concurrent.locks.AbstractQueuedSynchronizer$Node","prev",NODE);link(graph,value,"java.util.concurrent.locks.AbstractQueuedSynchronizer$Node","next",NODE);}
            if((role&CHUNK)!=0){
                link(graph,value,"io.netty.buffer.PoolChunk","arena",ARENA);link(graph,value,"io.netty.buffer.PoolChunk","parent",LIST);
                link(graph,value,"io.netty.buffer.PoolChunk","prev",CHUNK_NEIGHBOR);link(graph,value,"io.netty.buffer.PoolChunk","next",CHUNK_NEIGHBOR);
                link(graph,value,"io.netty.buffer.PoolChunk","pinnedBytes",COUNTER);link(graph,value,"io.netty.buffer.PoolChunk","runsAvailLock",LOCK);
                link(graph,value,"io.netty.buffer.PoolChunk","runsAvailMap",STRUCTURE);link(graph,value,"io.netty.buffer.PoolChunk","runsAvail",STRUCTURE_ARRAY);
                Object subpages=read(value,"io.netty.buffer.PoolChunk","subpages");
                if(subpages instanceof Object[] pages)for(Allocation allocation:graph.allocations.values())if(allocation.chunk()==value&&(allocation.handle()&(1L<<32))!=0){
                    int at=(int)(allocation.handle()>>>49);if(at>=0&&at<pages.length)graph.add(pages[at],SUBPAGE);
                }
                link(graph,value,"io.netty.buffer.PoolChunk","cachedNioBuffers",STRUCTURE);
            }
            if((role&ARENA)!=0){
                for(String field:new String[]{"q050","q025","q000","qInit","q075","q100"})link(graph,value,"io.netty.buffer.PoolArena",field,LIST);
                for(String field:new String[]{"activeBytesHuge","deallocationsHuge"})link(graph,value,"io.netty.buffer.PoolArena",field,COUNTER);
                link(graph,value,"io.netty.buffer.PoolArena","lock",LOCK);
            }
            if((role&LIST)!=0){link(graph,value,"io.netty.buffer.PoolChunkList","nextList",LIST);link(graph,value,"io.netty.buffer.PoolChunkList","prevList",LIST);link(graph,value,"io.netty.buffer.PoolChunkList","head",CHUNK_NEIGHBOR);}
            if((role&SUBPAGE)!=0){link(graph,value,"io.netty.buffer.PoolSubpage","prev",SUBPAGE_NEIGHBOR);link(graph,value,"io.netty.buffer.PoolSubpage","next",SUBPAGE_NEIGHBOR);link(graph,value,"io.netty.buffer.PoolSubpage","lock",LOCK);link(graph,value,"io.netty.buffer.PoolSubpage","bitmap",STRUCTURE_ARRAY);}
            if((role&SUBPAGE_NEIGHBOR)!=0)link(graph,value,"io.netty.buffer.PoolSubpage","lock",LOCK);
            if((role&CACHE)!=0)cacheChildren(graph,value);
            if((role&REGION)!=0){link(graph,value,"io.netty.buffer.PoolThreadCache$MemoryRegionCache","queue",QUEUE);entryPool(graph,value.getClass());}
            if((role&REGION_ENTRY)!=0)link(graph,value,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry","recyclerHandle",HANDLE);
            if((role&HANDLE)!=0)link(graph,value,"io.netty.util.Recycler$DefaultHandle","localPool",LOCAL_POOL);
            if((role&LOCAL_POOL)!=0)link(graph,value,"io.netty.util.Recycler$LocalPool","pooledHandles",QUEUE);
            if((role&QUEUE)!=0)queueChildren(graph,value);
            if((role&QUEUE_ARRAY)!=0&&value instanceof Object[] items)for(Object item:items){
                if(standard(item,graph.anchor,"io.netty.util.Recycler$DefaultHandle")){
                    Object entry=read(item,"io.netty.util.Recycler$DefaultHandle","value");
                    if(standard(entry,graph.anchor,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")&&read(entry,entry.getClass().getName(),"chunk")==null){graph.add(item,HANDLE);graph.add(entry,REGION_ENTRY);}
                }
            }
            if((role&STRUCTURE)!=0){
                link(graph,value,"io.netty.buffer.LongPriorityQueue","array",STRUCTURE_ARRAY);link(graph,value,"io.netty.buffer.LongLongHashMap","array",STRUCTURE_ARRAY);
            }
            if((role&STRUCTURE_ARRAY)!=0&&value instanceof Object[] items)for(Object item:items){
                if(standard(item,graph.anchor,"io.netty.buffer.PoolSubpage"))graph.add(item,SUBPAGE);
                else if(standard(item,graph.anchor,"io.netty.buffer.LongPriorityQueue"))graph.add(item,STRUCTURE);
            }
        }
    }
    private static void addMessage(Graph graph,Object value){
        if(value==null||!messageType(value.getClass(),graph.anchor))return;
        graph.add(value,MESSAGE);
    }
    private static void messageChildren(Graph graph,Object value){
        for(String owner:new String[]{"io.netty.buffer.UnpooledHeapByteBuf","io.netty.buffer.UnpooledDirectByteBuf"})link(graph,value,owner,"alloc",ALLOCATOR);
        Object chunk=read(value,"io.netty.buffer.PooledByteBuf","chunk");graph.add(chunk,CHUNK);
        Object handle=read(value,"io.netty.buffer.PooledByteBuf","handle"),capacity=read(value,"io.netty.buffer.PooledByteBuf","maxLength");
        if(handle instanceof Long number&&number>=0&&chunk!=null&&capacity instanceof Integer length&&!graph.allocations.containsKey(value)){
            Allocation allocation=new Allocation(chunk,number,length,read(value,"io.netty.buffer.PooledByteBuf","cache"),read(value,"io.netty.buffer.PooledByteBuf","tmpNioBuf"));protect(allocation);graph.allocations.put(value,allocation);
        }
        link(graph,value,"io.netty.buffer.PooledByteBuf","cache",CACHE);link(graph,value,"io.netty.buffer.PooledByteBuf","recyclerHandle",HANDLE);
        link(graph,value,"io.netty.buffer.PooledByteBuf","tmpNioBuf",STRUCTURE);
        for(String owner:new String[]{"io.netty.buffer.WrappedByteBuf","io.netty.buffer.DuplicatedByteBuf","io.netty.buffer.AbstractUnpooledSlicedByteBuf","io.netty.buffer.AbstractPooledDerivedByteBuf"})
            for(String field:new String[]{"buf","buffer","parent","rootParent"})addMessage(graph,read(value,owner,field));
        Object leak=read(value,"io.netty.buffer.SimpleLeakAwareByteBuf","leak"),tracked=read(value,"io.netty.buffer.SimpleLeakAwareByteBuf","trackedByteBuf");
        if(standard(leak,graph.anchor,"io.netty.util.ResourceLeakDetector$DefaultResourceLeak")&&tracked!=null&&messageType(tracked.getClass(),graph.anchor)){addMessage(graph,tracked);graph.add(leak,LEAK);}
    }
    private static void cacheChildren(Graph graph,Object cache){
        // Only the cache selected by this retained chunk/handle's real size class.
        for(Allocation allocation:graph.allocations.values()){
            if(allocation.cache()!=cache)continue;Object arena=read(allocation.chunk(),"io.netty.buffer.PoolChunk","arena");if(arena==null)continue;
            boolean direct=standard(arena,graph.anchor,"io.netty.buffer.PoolArena$DirectArena"),heap=standard(arena,graph.anchor,"io.netty.buffer.PoolArena$HeapArena");if(!direct&&!heap)continue;
            if(read(cache,"io.netty.buffer.PoolThreadCache",direct?"directArena":"heapArena")!=arena)continue;
            Object sizes=read(arena,"io.netty.buffer.SizeClasses","sizeIdx2sizeTab");if(!(sizes instanceof int[] table))continue;
            int index=-1;for(int at=0;at<table.length;at++)if(table[at]==allocation.capacity()){index=at;break;}if(index<0)continue;
            boolean small=(allocation.handle()&(1L<<32))!=0;
            if(!small)index-=number(read(arena,"io.netty.buffer.PoolArena","numSmallSubpagePools"));
            Object caches=read(cache,"io.netty.buffer.PoolThreadCache",small?(direct?"smallSubPageDirectCaches":"smallSubPageHeapCaches"):(direct?"normalDirectCaches":"normalHeapCaches"));
            if(caches instanceof Object[] entries&&index>=0&&index<entries.length)graph.add(entries[index],REGION);
        }
    }
    private static void entryPool(Graph graph,Class<?> type){
        Class<?> owner=parent(type,"io.netty.buffer.PoolThreadCache$MemoryRegionCache");if(owner==null)return;
        Object pool=staticValue(owner,"RECYCLER"),recycler=read(pool,"io.netty.util.internal.ObjectPool$RecyclerObjectPool","recycler");if(pool!=null)graph.add(pool,ENTRY_POOL);
        Object local=read(recycler,"io.netty.util.Recycler","threadLocal");Object at=read(local,"io.netty.util.concurrent.FastThreadLocal","index");
        if(!(at instanceof Integer index))return;
        graph.entrySlots.put(local,index);Object remove=staticValue(parent(local,"io.netty.util.concurrent.FastThreadLocal"),"variablesToRemoveIndex");if(remove instanceof Integer slot)graph.removeSlot=slot;
        Object current=currentMap(graph.anchor);graph.add(current,LOCAL_MAP);
        Object indexed=read(current,"io.netty.util.internal.InternalThreadLocalMap","indexedVariables");
        if(indexed instanceof Object[] values&&index>=0&&index<values.length&&standard(values[index],graph.anchor,"io.netty.util.Recycler$LocalPool"))graph.add(values[index],LOCAL_POOL);
    }
    private static Object currentMap(Class<?> anchor){
        Object current=read(Thread.currentThread(),"io.netty.util.concurrent.FastThreadLocalThread","threadLocalMap");if(current!=null)return current;
        Object locals=read(Thread.currentThread(),"java.lang.Thread","threadLocals"),table=read(locals,"java.lang.ThreadLocal$ThreadLocalMap","table");
        if(table instanceof Object[] entries)for(Object entry:entries){
            Object value=read(entry,"java.lang.ThreadLocal$ThreadLocalMap$Entry","value");
            // An actual instance proves this class has already initialized. Do
            // not initialize a library merely to ask whether its TL map exists.
            if(standard(value,anchor,"io.netty.util.internal.InternalThreadLocalMap")){
                Object key=staticValue(value.getClass(),"slowThreadLocalMap");if(key!=null&&read(entry,"java.lang.ref.Reference","referent")==key)return value;
            }
        }
        return null;
    }
    private static void queueChildren(Graph graph,Object queue){
        for(Class<?> type=queue.getClass();type!=null;type=type.getSuperclass()){
            if(!queueClass(type.getName())||!framework(type,graph.anchor))continue;
            for(String field:new String[]{"buffer","producerBuffer","consumerBuffer"}){
                Object buffer=read(queue,type.getName(),field);
                if(buffer instanceof Object[])graph.add(buffer,QUEUE_ARRAY);
                else if(buffer!=null&&buffer.getClass()==java.util.concurrent.atomic.AtomicReferenceArray.class)graph.add(read(buffer,"java.util.concurrent.atomic.AtomicReferenceArray","array"),QUEUE_ARRAY);
            }
            graph.add(staticValue(type,"JUMP"),MARKER);graph.add(staticValue(type,"BUFFER_CONSUMED"),MARKER);
        }
    }
    private static boolean queueValue(Graph graph,Object value){
        if(value==null)return true;if(graph.has(value,HANDLE|REGION_ENTRY))return true;
        if(standard(value,graph.anchor,"io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")){
            Object chunk=read(value,value.getClass().getName(),"chunk"),handle=read(value,value.getClass().getName(),"handle");
            if(handle instanceof Long number)for(Allocation allocation:graph.allocations.values())if(allocation.chunk()==chunk&&allocation.handle()==number){graph.add(value,REGION_ENTRY);return true;}
        }return false;
    }
    private static boolean queueField(String owner,String field){return queueClass(owner)&&oneOf(field,"producerIndex","producerLimit","consumerIndex","producerBuffer","consumerBuffer","producerMask","consumerMask");}
    private static boolean queueFieldValue(Graph graph,String field,Object value){
        if(oneOf(field,"producerIndex","producerLimit","consumerIndex","producerMask","consumerMask"))return value instanceof Long;
        if(value instanceof Object[]){graph.add(value,QUEUE_ARRAY);return true;}
        if(value!=null&&value.getClass()==java.util.concurrent.atomic.AtomicReferenceArray.class){graph.add(read(value,"java.util.concurrent.atomic.AtomicReferenceArray","array"),QUEUE_ARRAY);return true;}return false;
    }
    private static void link(Graph graph,Object value,String owner,String field,long role){Object child=read(value,owner,field);if(child!=null)graph.add(child,role);}
    private static Class<?> parent(Object value,String name){return value==null?null:parent(value.getClass(),name);}
    private static Class<?> parent(Class<?> type,String name){for(;type!=null;type=type.getSuperclass())if(type.getName().equals(name))return type;return null;}
    private static Object read(Object value,String owner,String name){
        if(value==null)return null;Class<?> type=parent(value,owner);if(type==null)return null;
        return NativeControl.networkReadField(value,type,name,false);
    }
    private static Object staticValue(Class<?> type,String name){
        return type==null?null:NativeControl.networkReadField(null,type,name,true);
    }
    private static void protect(Object... values){for(Object value:values)if(value!=null)CodeSourceBridge.contributionControl(value);}
    private static Class<?> resolve(Class<?> anchor,String name){try{return Class.forName(name,false,anchor.getClassLoader());}catch(ClassNotFoundException|LinkageError unavailable){return null;}}
    private static int number(Object value){return value instanceof Number number?number.intValue():-1;}
    private static boolean jdkCell(Object value){return value!=null&&value.getClass().getClassLoader()==null&&value.getClass().getName().equals("java.util.concurrent.atomic.Striped64$Cell");}
    private static boolean jdkNode(Object value){return value!=null&&value.getClass().getClassLoader()==null&&oneOf(value.getClass().getName(),"java.util.concurrent.locks.AbstractQueuedSynchronizer$ExclusiveNode","java.util.concurrent.locks.AbstractQueuedSynchronizer$SharedNode","java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode","java.util.concurrent.locks.AbstractQueuedSynchronizer$Node");}
    private static boolean standard(Object value,Class<?> anchor,String name){return value!=null&&value.getClass().getName().equals(name)&&framework(value.getClass(),anchor);}
    private static boolean framework(Class<?> type,Class<?> anchor){
        if(type==null||anchor==null||type.getClassLoader()!=anchor.getClassLoader())return false;
        Module module=type.getModule(),origin=anchor.getModule();if(module==origin)return true;
        if(!module.isNamed()||!origin.isNamed()||module.getLayer()!=origin.getLayer())return false;
        String expected=type.getName().startsWith("io.netty.buffer.")?"io.netty.buffer":type.getName().startsWith("io.netty.channel.")?"io.netty.transport":"io.netty.common";
        return expected.equals(module.getName());
    }
    private static boolean messageType(Class<?> type,Class<?> anchor){
        if(!framework(type,anchor))return false;
        return oneOf(type.getName(),"io.netty.buffer.UnpooledHeapByteBuf","io.netty.buffer.UnpooledUnsafeHeapByteBuf","io.netty.buffer.UnpooledDirectByteBuf","io.netty.buffer.UnpooledUnsafeDirectByteBuf","io.netty.buffer.UnpooledUnsafeNoCleanerDirectByteBuf",
                "io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledHeapByteBuf","io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeHeapByteBuf","io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledDirectByteBuf","io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeDirectByteBuf","io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeNoCleanerDirectByteBuf",
                "io.netty.buffer.PooledHeapByteBuf","io.netty.buffer.PooledUnsafeHeapByteBuf","io.netty.buffer.PooledDirectByteBuf","io.netty.buffer.PooledUnsafeDirectByteBuf","io.netty.buffer.PooledSlicedByteBuf","io.netty.buffer.PooledDuplicatedByteBuf",
                "io.netty.buffer.SlicedByteBuf","io.netty.buffer.UnpooledSlicedByteBuf","io.netty.buffer.DuplicatedByteBuf","io.netty.buffer.UnpooledDuplicatedByteBuf","io.netty.buffer.ReadOnlyByteBuf","io.netty.buffer.SimpleLeakAwareByteBuf","io.netty.buffer.AdvancedLeakAwareByteBuf","io.netty.buffer.WrappedByteBuf");
    }
    private static boolean queueClass(String name){
        return oneOf(name,"io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueProducerFields","io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueColdProducerFields","io.netty.util.internal.shaded.org.jctools.queues.BaseMpscLinkedArrayQueueConsumerFields",
                "io.netty.util.internal.shaded.org.jctools.queues.MpscChunkedArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerIndexField","io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueProducerLimitField","io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueueConsumerIndexField","io.netty.util.internal.shaded.org.jctools.queues.ConcurrentCircularArrayQueue",
                "io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueProducerFields","io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueColdProducerFields","io.netty.util.internal.shaded.org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueueConsumerFields","io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.atomic.MpscAtomicArrayQueue","io.netty.util.internal.shaded.org.jctools.queues.atomic.AtomicReferenceArrayQueue");
    }
    private static boolean oneOf(String value,String... choices){for(String choice:choices)if(choice.equals(value))return true;return false;}

    /** 0 refuses a callback/unknown frame, 1 continues, 2 is the original boundary. */
    static int frame(Class<?> type,String method,String descriptor,Class<?> boundary,String boundaryMethod,int phase){
        if(type==null||method==null||descriptor==null||boundary==null)return 0;
        if(phase==-2){
            if(type==NetworkCleanup.class&&method.equals("submissionBoundary"))return 1;
            if(type==NativeControl.class&&oneOf(method,"networkCleanupBoundary","networkCleanupBoundary0"))return 1;
            if(type==NetworkBridge.class&&oneOf(method,"writeSubmissionRejected","writeSubmissionOfferFailed","writeSubmissionOfferRejected","writeSubmissionDefaultRejected","writeSubmissionMpscRead","writeSubmissionMpscCapacity","writeSubmissionMpscFull","writeSubmissionMpscOffered"))return 1;
            if(type==boundary&&type.getName().equals(CTX)&&method.equals("safeExecute")&&descriptor.equals(SAFE_EXECUTE))return 2;
            if(type.getName().equals(DEFAULT_REJECT)&&framework(type,boundary))return method.equals("rejected")&&descriptor.equals("(Ljava/lang/Runnable;Lio/netty/util/concurrent/SingleThreadEventExecutor;)V")?1:0;
            if(framework(type,boundary)){
                if(oneOf(type.getName(),MPSC_BASE+"ColdProducerFields",MPSC_ATOMIC_BASE+"ColdProducerFields"))return method.equals("lvProducerLimit")&&descriptor.equals("()J")?1:0;
                if(oneOf(type.getName(),MPSC_BASE+"ProducerFields",MPSC_ATOMIC_BASE+"ProducerFields"))return method.equals("lvProducerIndex")&&descriptor.equals("()J")?1:0;
                if(oneOf(type.getName(),MPSC_BASE+"ConsumerFields",MPSC_ATOMIC_BASE+"ConsumerFields"))return method.equals("lvConsumerIndex")&&descriptor.equals("()J")?1:0;
                if(oneOf(type.getName(),MPSC_BASE,MPSC_ATOMIC_BASE))return method.equals("offer")&&descriptor.equals("(Ljava/lang/Object;)Z")||method.equals("offerSlowPath")&&descriptor.equals("(JJJ)I")?1:0;
                if(oneOf(type.getName(),MPSC_CHUNK,MPSC_ATOMIC_CHUNK))return method.equals("offer")&&descriptor.equals("(Ljava/lang/Object;)Z")
                        ||method.equals("getCurrentBufferCapacity")&&descriptor.equals("(J)J")||method.equals("availableInQueue")&&descriptor.equals("(JJ)J")?1:0;
            }
            if(!type.getName().equals(EXECUTOR)||!framework(type,boundary))return 0;
            return method.equals("reject")&&(descriptor.equals("()V")||descriptor.equals("(Ljava/lang/Runnable;)V"))||method.equals("offerTask")&&descriptor.equals("(Ljava/lang/Runnable;)Z")
                    ||method.equals("addTask")&&descriptor.equals("(Ljava/lang/Runnable;)V")||method.equals("execute")&&descriptor.equals("(Ljava/lang/Runnable;Z)V")
                    ||oneOf(method,"execute0","lazyExecute0","execute","lazyExecute")&&descriptor.equals("(Ljava/lang/Runnable;)V")?1:0;
        }
        if(type==boundary&&method.equals(boundaryMethod)){
            if(phase==-1&&type.getName().equals(WRITE)&&oneOf(method,"run","cancel")&&descriptor.equals("()V"))return 2;
            if(phase>=0&&type.getName().equals(CTX)&&(method.equals("write")&&descriptor.equals("(Ljava/lang/Object;ZLio/netty/channel/ChannelPromise;)V")
                    ||oneOf(method,"invokeWrite","invokeWriteAndFlush")&&descriptor.equals("(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)V")
                    ||method.equals("safeExecute")&&descriptor.equals(SAFE_EXECUTE)))return 2;
            return 0;
        }
        if(type==NetworkCleanup.class||type==Graph.class||type==Visit.class)return 1;
        if(type==NativeControl.class&&oneOf(method,"networkCleanupBoundary","networkCleanupBoundary0"))return 1;
        if(type==NetworkBridge.class&&oneOf(method,"retirementAllowed","retirementWriteAllowed","retirementFieldAllowed","retirementArrayAllowed"))return 1;
        if(type==ControlBridge.class&&oneOf(method,"networkRetirementWriteAllowed","networkRetirementOwnerAllowed","unsafeWriteAllowed","beginNativeUnsafeMutation","beginExecutedUnsafeMutation","beginFieldMutation","beginArrayMutation","fieldSetAllowed","fieldWriteAllowed","fieldAllowed","reflectionSetAllowed","reflectionSetField","arrayWriteAllowed"))return 1;
        if(type==BackingBridge.class&&oneOf(method,"nativeWriteAllowed","arrayAllowed","arrayStore","intStore","longStore","floatStore","doubleStore","charStore","shortStore","booleanStore","byteStore"))return 1;
        if(type==CodeSourceBridge.class&&oneOf(method,"executionUnsafeBefore","executionUnsafeMutation"))return 1;
        if(type==ExecutionFlow.class&&method.equals("unsafeBefore"))return 1;
        String name=type.getName();
        if(type.getClassLoader()==null&&name.equals("sun.misc.Unsafe")&&"jdk.unsupported".equals(type.getModule().getName()))return unsafeFrame(method,descriptor)?1:0;
        if(type.getClassLoader()==null&&type.getModule()==Object.class.getModule()){
            if(name.equals("jdk.internal.misc.Unsafe"))return unsafeFrame(method,descriptor)?1:0;
            if(varHandleFrame(name,method,descriptor))return 1;
            if(oneOf(name,"java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl","java.util.concurrent.atomic.AtomicLongFieldUpdater$CASUpdater","java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl","java.util.concurrent.atomic.AtomicLong","java.util.concurrent.atomic.AtomicReferenceArray"))
                return oneOf(method,"compareAndSet","weakCompareAndSet","getAndAdd","addAndGet","incrementAndGet","decrementAndGet","getAndIncrement","getAndDecrement","getAndSet","set","lazySet","setRelease")?1:0;
            if(oneOf(name,"java.util.concurrent.atomic.LongAdder","java.util.concurrent.atomic.Striped64","java.util.concurrent.atomic.Striped64$Cell"))return oneOf(method,"add","increment","decrement","longAccumulate","casBase","casCellsBusy","cas","reset","<init>")?1:0;
            if(oneOf(name,"java.util.concurrent.locks.ReentrantLock","java.util.concurrent.locks.ReentrantLock$Sync","java.util.concurrent.locks.ReentrantLock$NonfairSync","java.util.concurrent.locks.ReentrantLock$FairSync","java.util.concurrent.locks.AbstractQueuedSynchronizer","java.util.concurrent.locks.AbstractQueuedSynchronizer$Node","java.util.concurrent.locks.AbstractOwnableSynchronizer"))
                return oneOf(method,"lock","unlock","tryLock","initialTryLock","tryAcquire","tryRelease","acquire","release","compareAndSetState","setState","setExclusiveOwnerThread","enqueue","tryInitializeHead","casTail","casStatus","casNext","setPrevRelaxed","setStatusRelaxed","clearStatus","signalNext","cleanQueue")?1:0;
            if(name.equals("java.util.concurrent.ConcurrentHashMap"))return oneOf(method,"remove","replaceNode","addCount","fullAddCount","tabAt","casTabAt","setTabAt")?1:0;
            if(name.equals("java.util.concurrent.ConcurrentHashMap$KeySetView"))return method.equals("remove")?1:0;
            if(name.equals("java.util.Collections$SetFromMap"))return oneOf(method,"remove","add")?1:0;
            if(name.equals("java.util.IdentityHashMap"))return oneOf(method,"put","resize","hash","nextKeyIndex","maskNull")?1:0;
            if(name.equals("java.lang.ref.Reference"))return method.equals("clear")?1:0;
            return 0;
        }
        if(!framework(type,boundary))return 0;
        if(name.equals(CTX))return oneOf(method,"flush","invokeFlush","invokeFlush0","findContextOutbound","safeExecute","executor")?1:0;
        if(name.equals("io.netty.channel.DefaultChannelPipeline"))return oneOf(method,"decrementPendingOutboundBytes","flush")?1:0;
        if(name.equals("io.netty.channel.DefaultChannelPipeline$HeadContext"))return method.equals("flush")?1:0;
        if(name.equals("io.netty.channel.AbstractChannel$AbstractUnsafe"))return oneOf(method,"flush","flush0","handleWriteError")?1:0;
        if(name.equals("io.netty.channel.ChannelOutboundBuffer"))return oneOf(method,"decrementPendingOutboundBytes","setWritable","fireChannelWritabilityChanged","addFlush","remove","remove0","removeEntry","clearNioBuffers","safeSuccess","safeFail","failFlushed")?1:0;
        if(name.equals("io.netty.channel.ChannelOutboundBuffer$Entry"))return oneOf(method,"cancel","recycle")?1:0;
        if(name.equals(WRITE))return oneOf(method,"decrementPendingOutboundBytes","recycle")?1:0;
        if(oneOf(name,"io.netty.util.concurrent.DefaultPromise","io.netty.channel.DefaultChannelPromise","io.netty.channel.DefaultChannelProgressivePromise","io.netty.util.concurrent.DefaultProgressivePromise"))
            return oneOf(method,"tryFailure","setFailure0","setValue0","trySuccess","setSuccess0","setUncancellable","checkNotifyWaiters","notifyListeners","notifyListenersNow","notifyListeners0","notifyListener0","safeExecute","executor")||phase==1&&method.equals("setFailure")?1:0;
        if(name.equals("io.netty.util.concurrent.DefaultPromise$CauseHolder"))return phase==1&&method.equals("<init>")&&descriptor.equals("(Ljava/lang/Throwable;)V")?1:0;
        if(name.equals("io.netty.util.ReferenceCountUtil"))return oneOf(method,"release","safeRelease")||phase==2&&method.equals("retain")?1:0;
        if(name.equals("io.netty.util.internal.ReferenceCountUpdater"))return oneOf(method,"release","nonVolatileRawCnt","tryFinalRelease0","nonFinalRelease0","retryRelease0","toLiveRealRefCnt")||phase==2&&method.equals("retain")?1:0;
        if(name.equals("io.netty.buffer.AbstractReferenceCountedByteBuf"))return oneOf(method,"release","handleRelease")||phase==2&&method.equals("retain")?1:0;
        if(name.equals("io.netty.buffer.PooledByteBuf"))return oneOf(method,"deallocate","recycle")?1:0;
        if(name.equals("io.netty.buffer.UnpooledHeapByteBuf")||name.equals("io.netty.buffer.UnpooledDirectByteBuf"))return oneOf(method,"deallocate","freeArray","freeDirect")?1:0;
        if(messageType(type,boundary))return oneOf(method,"release","freeArray","freeDirect","deallocate","closeLeak")||phase==2&&method.equals("retain")?1:0;
        if(name.equals("io.netty.util.ResourceLeakDetector$DefaultResourceLeak"))return method.equals("close")&&(descriptor.equals("()Z")||descriptor.equals("(Ljava/lang/Object;)Z"))?1:0;
        if(name.equals("io.netty.buffer.UnpooledByteBufAllocator"))return oneOf(method,"decrementHeap","decrementDirect")?1:0;
        if(name.equals("io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry")&&method.equals("<init>"))return descriptor.equals("(Lio/netty/util/internal/ObjectPool$Handle;)V")?1:0;
        if(oneOf(name,"io.netty.buffer.PoolArena","io.netty.buffer.PoolArena$HeapArena","io.netty.buffer.PoolArena$DirectArena","io.netty.buffer.PoolChunk","io.netty.buffer.PoolChunkList","io.netty.buffer.PoolSubpage","io.netty.buffer.PoolThreadCache","io.netty.buffer.PoolThreadCache$MemoryRegionCache","io.netty.buffer.PoolThreadCache$MemoryRegionCache$Entry","io.netty.buffer.LongPriorityQueue","io.netty.buffer.LongLongHashMap"))
            return oneOf(method,"free","freeChunk","destroyChunk","destroy","decrementPinnedMemory","freeRun","collapseRuns","collapsePast","collapseNext","removeAvailRun","insertAvailRun","add","remove","move","move0","removeAvailRun","removeAvailRun0","setNextAvail","recycle","newEntry","cache","offer","poll","lift","sink","put","expand","index","removeAt")?1:0;
        if(name.equals("io.netty.util.Recycler$DefaultHandle")&&method.equals("<init>"))return descriptor.equals("(Lio/netty/util/Recycler$LocalPool;)V")?1:0;
        if(name.equals("io.netty.util.Recycler$LocalPool")&&method.equals("<init>"))return descriptor.equals("(III)V")?1:0;
        if(oneOf(name,"io.netty.util.Recycler$DefaultHandle","io.netty.util.Recycler$LocalPool","io.netty.util.Recycler","io.netty.util.internal.ObjectPool$RecyclerObjectPool"))return oneOf(method,"recycle","release","toAvailable","availableToClaim","claim","get","newHandle","set")?1:0;
        if(oneOf(name,"io.netty.util.Recycler$1","io.netty.util.internal.ObjectPool$RecyclerObjectPool$1","io.netty.buffer.PoolThreadCache$MemoryRegionCache$1"))return oneOf(method,"initialValue","newObject")?1:0;
        if(name.equals("io.netty.util.concurrent.FastThreadLocal"))return oneOf(method,"get","initialize","setKnownNotUnset","addToVariablesToRemove")?1:0;
        if(name.equals("io.netty.util.concurrent.FastThreadLocalThread"))return method.equals("setThreadLocalMap")?1:0;
        if(name.equals("io.netty.util.internal.InternalThreadLocalMap"))return oneOf(method,"get","fastGet","slowGet","setIndexedVariable","expandIndexedVariableTableAndSet","setFutureListenerStackDepth","<init>","newIndexedVariableTable")?1:0;
        if(queueClass(name))return oneOf(method,"offer","relaxedOffer","poll","relaxedPoll","resize","offerSlowPath","casProducerIndex","casProducerLimit","soProducerIndex","soProducerLimit","soConsumerIndex","newBufferPoll","nextBuffer","soElement","spElement","lvElement","lpElement","<init>")?1:0;
        if(oneOf(name,"io.netty.util.internal.shaded.org.jctools.util.UnsafeRefArrayAccess","io.netty.util.internal.shaded.org.jctools.util.UnsafeLongArrayAccess","io.netty.util.internal.shaded.org.jctools.queues.atomic.AtomicQueueUtil","io.netty.util.internal.PlatformDependent","io.netty.util.internal.PlatformDependent0"))return oneOf(method,"soRefElement","spRefElement","soLongElement","spLongElement","soElement","spElement","putObject","putOrderedObject","putInt","putLong","freeDirectBuffer")?1:0;
        if(name.equals("io.netty.channel.embedded.EmbeddedChannel"))return oneOf(method,"doWrite","handleOutboundMessage","outboundMessages")?1:0;
        if(name.equals("io.netty.channel.ChannelOutboundHandlerAdapter"))return method.equals("flush")?1:0;
        return 0;
    }
    private static boolean varHandleFrame(String owner,String method,String descriptor){
        String prefix="(Ljava/lang/invoke/VarHandle;Ljava/lang/Object;",access="Ljava/lang/invoke/VarHandle$AccessDescriptor;";
        if(owner.equals("java.lang.invoke.VarHandleGuards")){
            return switch(method){
                case "guard_LJJ_Z"->descriptor.equals(prefix+"JJ"+access+")Z");
                case "guard_LII_Z"->descriptor.equals(prefix+"II"+access+")Z");
                case "guard_LLL_Z"->descriptor.equals(prefix+"Ljava/lang/Object;Ljava/lang/Object;"+access+")Z");
                case "guard_LJ_J"->descriptor.equals(prefix+"J"+access+")J");
                case "guard_LI_I"->descriptor.equals(prefix+"I"+access+")I");
                case "guard_LL_L"->descriptor.equals(prefix+"Ljava/lang/Object;"+access+")Ljava/lang/Object;");
                case "guard_LJ_V"->descriptor.equals(prefix+"J"+access+")V");
                case "guard_LI_V"->descriptor.equals(prefix+"I"+access+")V");
                case "guard_LL_V"->descriptor.equals(prefix+"Ljava/lang/Object;"+access+")V");
                default->false;
            };
        }
        String kind=owner.equals("java.lang.invoke.VarHandleLongs$FieldInstanceReadWrite")?"J":owner.equals("java.lang.invoke.VarHandleInts$FieldInstanceReadWrite")?"I":owner.equals("java.lang.invoke.VarHandleReferences$FieldInstanceReadWrite")?"Ljava/lang/Object;":null;
        if(kind==null)return false;
        if(oneOf(method,"compareAndSet","weakCompareAndSet","weakCompareAndSetPlain","weakCompareAndSetRelease","weakCompareAndSetAcquire"))return descriptor.equals(prefix+kind+kind+")Z");
        if(oneOf(method,"getAndAdd","getAndAddRelease","getAndAddAcquire","getAndSet","getAndSetRelease","getAndSetAcquire"))return descriptor.equals(prefix+kind+")"+kind);
        return oneOf(method,"set","setVolatile","setRelease","setOpaque")&&descriptor.equals(prefix+kind+")V");
    }
    private static boolean unsafeFrame(String method,String descriptor){
        if(oneOf(method,"compareAndSetInt","compareAndSwapInt","weakCompareAndSetInt","weakCompareAndSetIntRelease","weakCompareAndSetIntPlain"))return descriptor.equals("(Ljava/lang/Object;JII)Z");
        if(oneOf(method,"compareAndSetLong","compareAndSwapLong","weakCompareAndSetLong","weakCompareAndSetLongRelease","weakCompareAndSetLongPlain"))return descriptor.equals("(Ljava/lang/Object;JJJ)Z");
        if(oneOf(method,"compareAndSetReference","compareAndSwapObject","weakCompareAndSetReference","weakCompareAndSetReferenceRelease"))return descriptor.equals("(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z");
        if(oneOf(method,"getAndAddInt","getAndSetInt"))return descriptor.equals("(Ljava/lang/Object;JI)I");
        if(oneOf(method,"getAndAddLong","getAndSetLong"))return descriptor.equals("(Ljava/lang/Object;JJ)J");
        if(oneOf(method,"getAndSetReference","getAndSetObject"))return descriptor.equals("(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");
        if(oneOf(method,"putInt","putIntVolatile","putIntRelease","putOrderedInt"))return descriptor.equals("(Ljava/lang/Object;JI)V");
        if(oneOf(method,"putLong","putLongVolatile","putLongRelease","putOrderedLong"))return descriptor.equals("(Ljava/lang/Object;JJ)V");
        return oneOf(method,"putReference","putReferenceVolatile","putReferenceRelease","putObject","putObjectVolatile","putOrderedObject")&&descriptor.equals("(Ljava/lang/Object;JLjava/lang/Object;)V");
    }
}
