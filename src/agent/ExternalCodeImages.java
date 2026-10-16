package dev.ronova.pro.agent;

import java.lang.ref.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Actual transformer transactions, retaining instruction identities across subsequent edits. */
final class ExternalCodeImages {
    private static final Module[] NO_MODULES=new Module[0];
    private static final ClassValue<Field[]> IMAGE_FIELDS=new ClassValue<>(){
        @Override protected Field[] computeValue(Class<?> type){
            List<Field> selected=new ArrayList<>();
            for(Field field:type.getFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()){
                if(!field.trySetAccessible())throw new IllegalStateException("EXTERNAL_IMAGE_CONTROL_FIELD_UNAVAILABLE:"+field.getName());
                selected.add(field);
            }
            Field[] fields=selected.toArray(Field[]::new);
            // The lookup is class-identity based; each traversal still reads the
            // current field on its actual node and protects all reachable values.
            Object[] controls=new Object[fields.length+2];controls[0]=this;controls[1]=fields;
            System.arraycopy(fields,0,controls,2,fields.length);ControlImages.protect(controls);
            return fields;
        }
    };
    private static final ReferenceQueue<Object> DEAD=new ReferenceQueue<>();
    private static final KeyIndex<Long> IDS=new KeyIndex<>();
    private static final KeyIndex<History> TREES=new KeyIndex<>();
    private static final KeyIndex<Key> PARENTS=new KeyIndex<>();
    private static final KeyIndex<History> WRITERS=new KeyIndex<>();
    private record ImageDirectory(List<Encoded> images,Map<String,List<Encoded>> names){}
    private static volatile ImageDirectory ENCODED=new ImageDirectory(List.of(),Map.of());
    private static final Map<Module,Set<String>> GAPS=new IdentityHashMap<>();
    private static final List<Library> LIBRARIES=new ArrayList<>();
    private static final ThreadLocal<List<String>> DISPOSITION_FAILURES=new ThreadLocal<>();
    private static long sequence;
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object value,boolean stored){super(value,stored?DEAD:null);hash=System.identityHashCode(value);}
        public int hashCode(){return hash;}
        public boolean equals(Object other){return this==other||other instanceof Key key&&get()!=null&&get()==key.get();}
        public void clear(){if(ControlCaller.allowed())super.clear();}
        public boolean enqueue(){return ControlCaller.allowed()&&super.enqueue();}
    }
    /** Private identity bookkeeping, not a public mutable Map or a source holder. */
    private static final class KeyIndex<V> {
        private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        private static final class Entry<V> {
            final Key key;V value;Entry<V> next;
            Entry(Key key,V value,Entry<V> next){this.key=key;this.value=value;this.next=next;}
        }
        @SuppressWarnings("unchecked") private Entry<V>[] table=(Entry<V>[])new Entry<?>[16];
        private int size;
        private static int bucket(int hash,int length){return (hash^(hash>>>16))&(length-1);}
        private static void writer(Class<?> caller){if(caller!=ExternalCodeImages.class)throw new SecurityException("ACTUAL_CODE_IMAGE_INDEX_WRITER_REQUIRED");}
        V get(Key key){
            for(Entry<V> entry=table[bucket(key.hash,table.length)];entry!=null;entry=entry.next)
                if(entry.key.hash==key.hash&&key.equals(entry.key))return entry.value;
            return null;
        }
        void put(Key key,V value){
            writer(CALLER.getCallerClass());int at=bucket(key.hash,table.length);
            for(Entry<V> entry=table[at];entry!=null;entry=entry.next)if(entry.key.hash==key.hash&&key.equals(entry.key)){entry.value=value;return;}
            if(size>=table.length-(table.length>>>2)&&table.length<1<<30){
                @SuppressWarnings("unchecked") Entry<V>[] next=(Entry<V>[])new Entry<?>[table.length*2];
                ControlImages.protect((Object)next);
                for(Entry<V> first:table)for(Entry<V> entry=first;entry!=null;){
                    Entry<V> following=entry.next;int slot=bucket(entry.key.hash,next.length);entry.next=next[slot];next[slot]=entry;entry=following;
                }
                table=next;at=bucket(key.hash,table.length);
            }
            // Entry fields also have the Agent's exact class/image ownership.
            // Protect the object before publishing it through the backing array.
            Entry<V> entry=new Entry<>(key,value,table[at]);ControlImages.protect(entry,key);table[at]=entry;size++;
        }
        void remove(Key key){
            writer(CALLER.getCallerClass());int at=bucket(key.hash,table.length);Entry<V> previous=null;
            for(Entry<V> entry=table[at];entry!=null;entry=entry.next){
                if(entry.key==key||entry.key.hash==key.hash&&key.equals(entry.key)){
                    if(previous==null)table[at]=entry.next;else previous.next=entry.next;size--;return;
                }
                previous=entry;
            }
        }
    }
    private static final class ControlCaller {
        static boolean allowed(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames
                .map(StackWalker.StackFrame::getDeclaringClass).filter(type->type.getNestHost()!=ExternalCodeImages.class)
                .findFirst().map(type->type==RecoveryAgent.class).orElse(false));}
    }
    private static final class Ref<T> extends WeakReference<T>{
        Ref(T value){super(value);}
        public void clear(){if(ControlCaller.allowed())super.clear();}
        public boolean enqueue(){return ControlCaller.allowed()&&super.enqueue();}
    }
    private record Instruction(long id,AbstractInsnNode code,String content,Map<LabelNode,Long> labels){}
    private record Handler(long id,long start,long end,long handler,String type){}
    private record Body(MethodNode method,List<Instruction> instructions,List<Handler> handlers){}
    private record Snapshot(byte[] bytes,ClassNode node,Map<String,Body> methods,Map<String,String> metadata){
        Snapshot(byte[] bytes,ClassNode node,Map<String,Body> methods){
            this(bytes,node,methods,Map.copyOf(metadataRows(node)));protect(metadata);
        }
    }
    private record Patch(Module[] owners,Snapshot before,Snapshot after,Module[] writers){
        Patch(Module[] owners,Snapshot before,Snapshot after){this(owners,before,after,owners);}
    }
    private static final class History {
        final WeakReference<Module> module;
        Snapshot current;
        final List<Patch> patches=new ArrayList<>();
        History(Module module,Snapshot current){this.module=new Ref<>(module);this.current=current;}
    }
    private static final class Capture {
        final Object input,root;final History history;final Snapshot before;final Module[] owners,writers;
        boolean ended;
        Capture(Object input,Object root,History history,Snapshot before,Module[] owners,Module[] writers){this.input=input;this.root=root;this.history=history;this.before=before;this.owners=owners;this.writers=writers;}
    }
    private record Encoded(WeakReference<ClassLoader> loader,boolean bootstrap,String name,byte[] digest,Snapshot snapshot,List<Patch> patches){}
    private record BodyPatch(Module[] owners,Map<Long,Instruction> before,Map<Long,Instruction> after){}
    record ExecutionSources(ExternalCodeFlow.Image image,Map<String,Module[]> fields,Object[][] values){}
    private record Library(String name,WeakReference<ClassLoader> loader,boolean bootstrap,WeakReference<Module> module){}
    private ExternalCodeImages(){}
    private static List<Encoded> images(){return ENCODED.images();}
    static boolean recorded(ClassLoader loader,String name){
        if(name==null)return false;
        for(Encoded image:ENCODED.names().getOrDefault(name,List.of()))
            if(image.bootstrap()==(loader==null)&&image.loader().get()==loader)return true;
        return false;
    }
    private static void store(Encoded image){
        for(;;){
            ImageDirectory before=ENCODED;List<Encoded> images=new ArrayList<>();
            for(Encoded prior:before.images())if(prior.bootstrap()||prior.loader().get()!=null)images.add(prior);
            images.add(image);Map<String,List<Encoded>> grouped=new HashMap<>();
            for(Encoded entry:images)grouped.computeIfAbsent(entry.name(),ignored->new ArrayList<>()).add(entry);
            Map<String,List<Encoded>> names=new HashMap<>();for(var entry:grouped.entrySet())names.put(entry.getKey(),List.copyOf(entry.getValue()));
            ImageDirectory next=new ImageDirectory(List.copyOf(images),Map.copyOf(names));
            List<Object> controls=new ArrayList<>(Arrays.asList(next,next.images(),next.names(),image,image.loader()));controls.addAll(next.names().values());
            ControlImages.protect(controls.toArray());
            // Completed images are immutable. Parsing, replay, hashing and
            // Native/bridge publication happen outside this publication lock.
            synchronized(ExternalCodeImages.class){if(ENCODED!=before)continue;ENCODED=next;return;}
        }
    }
    static synchronized void expect(String name,ClassLoader loader,Module module){
        for(Library library:LIBRARIES)if(library.name().equals(name)&&library.bootstrap()==(loader==null)&&library.loader().get()==loader){if(library.module().get()!=module)throw new IllegalStateException("ASM_LIBRARY_MODULE_CHANGED");return;}
        LIBRARIES.add(new Library(name,new Ref<>(loader),loader==null,new Ref<>(module)));ControlImages.protect(LIBRARIES,IDS,IDS.table,TREES,TREES.table,PARENTS,PARENTS.table,WRITERS,WRITERS.table,ENCODED,GAPS);
    }
    private static synchronized long id(Object object){reap();Key key=new Key(object,false);Long known=IDS.get(key);if(known!=null)return known;long next=++sequence;IDS.put(new Key(object,true),next);return next;}
    private static synchronized long nextIdentity(){return ++sequence;}
    private static void reap(){for(Key key;(key=(Key)DEAD.poll())!=null;){IDS.remove(key);TREES.remove(key);PARENTS.remove(key);WRITERS.remove(key);}}
    private static Class<?> library(String name,ClassLoader loader)throws ClassNotFoundException{
        Class<?> type=Class.forName(name,false,loader);Module expected;
        synchronized(ExternalCodeImages.class){expected=LIBRARIES.stream().filter(role->role.name().equals(name)&&role.bootstrap()==(type.getClassLoader()==null)&&role.loader().get()==type.getClassLoader()).map(role->role.module().get()).findFirst().orElse(null);}
        if(expected==null||type.getModule()!=expected)throw new IllegalStateException("ACTUAL_ASM_LIBRARY_UNOBSERVED:"+name);return type;
    }
    private static Object get(Object object,String name)throws ReflectiveOperationException{return object.getClass().getField(name).get(object);}
    private static List<?> list(Object object,String name)throws ReflectiveOperationException{return (List<?>)get(object,name);}
    private static Object[] instructions(Object method)throws ReflectiveOperationException{
        Object list=get(method,"instructions");return (Object[])list.getClass().getMethod("toArray").invoke(list);
    }
    private static Object[] actualInstructions(Object method,OpcodeReader opcodes)throws ReflectiveOperationException{
        Object[] all=instructions(method),actual=new Object[all.length];int count=0;
        // InsnList.toArray may return its own cache. Never compact that external
        // array in place; this private buffer contains the same live identities.
        for(Object instruction:all)if(opcodes.read(instruction)>=0)actual[count++]=instruction;
        return count==actual.length?actual:Arrays.copyOf(actual,count);
    }
    /** Reflection discovery is local to one tree operation; every opcode is still read live. */
    private static final class OpcodeReader {
        private static final class Entry {
            final Class<?> type;final java.lang.invoke.MethodHandle method;final Entry next;
            Entry(Class<?> type,java.lang.invoke.MethodHandle method,Entry next){this.type=type;this.method=method;this.next=next;}
        }
        private Entry entries;
        int read(Object instruction)throws ReflectiveOperationException{
            Class<?> type=instruction.getClass();
            java.lang.invoke.MethodHandle method=null;
            for(Entry entry=entries;entry!=null;entry=entry.next)if(entry.type==type){method=entry.method;break;}
            if(method==null){
                method=java.lang.invoke.MethodHandles.lookup().unreflect(type.getMethod("getOpcode"))
                        .asType(java.lang.invoke.MethodType.methodType(int.class,Object.class));
                entries=new Entry(type,method,entries);
            }
            try{return (int)method.invokeExact(instruction);}
            catch(Throwable failure){throw new InvocationTargetException(failure);}
        }
    }
    /** Serializes through the actual library; never initializes an application class to infer an owner. */
    static byte[] image(Object tree){
        try{return serialize(tree);}catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_TREE_IMAGE_UNAVAILABLE",failure);}
    }
    private static byte[] serialize(Object tree)throws ReflectiveOperationException{
        ClassLoader loader=tree.getClass().getClassLoader();Class<?> node=library("org.objectweb.asm.tree.ClassNode",loader);
        if(!node.isInstance(tree))throw new IllegalArgumentException("ACTUAL_CLASS_TREE_REQUIRED");
        Class<?> visitor=Class.forName("org.objectweb.asm.ClassVisitor",false,loader),writer=library("org.objectweb.asm.ClassWriter",loader);
        Method accept=tree.getClass().getMethod("accept",visitor);
        if(accept.getDeclaringClass()!=node)throw new IllegalStateException("CUSTOM_TREE_SERIALIZATION_UNOBSERVED");
        Object output=writer.getConstructor(int.class).newInstance(0);accept.invoke(tree,output);
        return (byte[])writer.getMethod("toByteArray").invoke(output);
    }
    static synchronized void observed(Object tree,Module module){
        Snapshot snapshot=snapshot(tree);Encoded origin=null;byte[] digest=hash(canonical(snapshot.bytes()));
        List<Encoded> encodedImages=images();for(int i=encodedImages.size()-1;i>=0;i--){Encoded encoded=encodedImages.get(i);if(encoded.loader().get()==module.getClassLoader()&&encoded.bootstrap()==(module.getClassLoader()==null)&&encoded.name().equals(snapshot.node().name)&&MessageDigest.isEqual(encoded.digest(),digest)){origin=encoded;break;}}
        if(origin!=null){identities(tree,origin.snapshot().methods());snapshot=snapshot(tree);}
        History history=new History(module,snapshot);if(origin!=null)history.patches.addAll(origin.patches());TREES.put(new Key(tree,true),history);parents(tree);
    }
    private static void identities(Object tree,Map<String,Body> bodies){
        try{
            OpcodeReader opcodes=new OpcodeReader();
            for(Object method:list(tree,"methods")){
                Body original=bodies.get(get(method,"name")+String.valueOf(get(method,"desc")));if(original==null)throw new IllegalStateException("OBSERVED_TREE_METHOD_IDENTITY_CHANGED");
                Object[] code=actualInstructions(method,opcodes);
                if(code.length!=original.instructions().size())throw new IllegalStateException("OBSERVED_TREE_INSTRUCTION_IDENTITY_CHANGED");
                for(int i=0;i<code.length;i++)IDS.put(new Key(code[i],true),original.instructions().get(i).id());
                List<?> handlers=list(method,"tryCatchBlocks");if(handlers.size()!=original.handlers().size())throw new IllegalStateException("OBSERVED_TREE_HANDLER_IDENTITY_CHANGED");
                for(int i=0;i<handlers.size();i++)IDS.put(new Key(handlers.get(i),true),original.handlers().get(i).id());
            }
        }catch(ReflectiveOperationException unavailable){throw new IllegalStateException("ACTUAL_TREE_IDENTITY_RECONSTRUCTION_UNAVAILABLE",unavailable);}
    }
    private static void parents(Object tree){
        try{Key parent=new Key(tree,true);for(Object method:list(tree,"methods"))PARENTS.put(new Key(method,true),parent);for(Object field:list(tree,"fields"))PARENTS.put(new Key(field,true),parent);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("ASM_MEMBER_PARENT_UNOBSERVED",failure);}
    }
    static synchronized Object begin(Object input,Module[] owners,Module[] writers){
        reap();Object root=input;History history=TREES.get(new Key(root,false));
        if(history==null){Key parent=PARENTS.get(new Key(input,false));root=parent==null?null:parent.get();history=root==null?null:TREES.get(new Key(root,false));}
        if(history==null){
            for(Module owner:owners)gap(owner,"TRANSFORM_INPUT_SOURCE_UNOBSERVED");return null;
        }
        Snapshot before=snapshot(root);
        if(!ControlImages.sameBytes(canonical(history.current.bytes()),canonical(before.bytes())))history.patches.add(new Patch(new Module[0],history.current,before));
        Capture capture=new Capture(input,root,history,before,owners.clone(),writers.clone());ControlImages.protect(capture,capture.owners,capture.writers);return capture;
    }
    static synchronized void end(Object token,Object output,Module[] consumers){
        if(token==null)return;if(!(token instanceof Capture capture))throw new SecurityException("ACTUAL_TRANSFORM_CAPTURE_REQUIRED");
        if(capture.ended)return;capture.ended=true;
        Snapshot after;boolean wholeTree;
        try{wholeTree=library("org.objectweb.asm.tree.ClassNode",output.getClass().getClassLoader()).isInstance(output);}catch(ClassNotFoundException failure){throw new IllegalStateException(failure);}
        if(wholeTree){after=snapshot(output);TREES.put(new Key(output,true),capture.history);parents(output);}
        else after=memberResult(capture,output);
        Module[] owners=join(capture.owners,consumers);ControlImages.protect((Object)owners);
        if(!ControlImages.sameBytes(canonical(capture.before.bytes()),canonical(after.bytes())))capture.history.patches.add(new Patch(owners,capture.before,after,capture.writers));
        capture.history.current=after;parents(capture.root);
        if(output!=capture.input&&!wholeTree)PARENTS.put(new Key(output,true),new Key(capture.root,true));
    }
    private static Snapshot memberResult(Capture capture,Object output){
        try{
            ClassLoader loader=capture.root.getClass().getClassLoader();Class<?> type=library("org.objectweb.asm.tree.ClassNode",loader);
            Object projection=type.getConstructor().newInstance();Class<?> visitor=Class.forName("org.objectweb.asm.ClassVisitor",false,loader);
            type.getMethod("accept",visitor).invoke(capture.root,projection);
            String member=(get(capture.input,"desc") instanceof String descriptor&&descriptor.startsWith("("))?"methods":"fields";
            @SuppressWarnings("unchecked") List<Object> projected=(List<Object>)get(projection,member);
            List<?> originals=list(capture.root,member);int index=-1;for(int i=0;i<originals.size();i++)if(originals.get(i)==capture.input){index=i;break;}
            if(index<0)throw new IllegalStateException("TRANSFORM_MEMBER_POSITION_UNOBSERVED");projected.set(index,output);
            // Unchanged members retain their real instruction identities in the projection.
            for(String kind:List.of("methods","fields"))if(!kind.equals(member))type.getField(kind).set(projection,get(capture.root,kind));
            for(int i=0;i<projected.size();i++)if(i!=index)projected.set(i,originals.get(i));
            return snapshot(projection);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("TRANSFORM_MEMBER_RESULT_UNOBSERVED",failure);}
    }
    static synchronized void copied(Object tree,Object visitor){
        History history=TREES.get(new Key(tree,false));if(history==null)return;
        try{
            ClassLoader loader=tree.getClass().getClassLoader();Class<?> writer=library("org.objectweb.asm.ClassWriter",loader);
            if(writer.isInstance(visitor)){WRITERS.put(new Key(visitor,true),history);return;}
            Class<?> node=library("org.objectweb.asm.tree.ClassNode",loader);if(node.isInstance(visitor)){
                transfer(tree,visitor);History next=new History(history.module.get(),snapshot(visitor));next.patches.addAll(history.patches);TREES.put(new Key(visitor,true),next);parents(visitor);
            }
        }catch(ClassNotFoundException failure){throw new IllegalStateException(failure);}
    }
    private static void transfer(Object source,Object destination){
        try{
            OpcodeReader opcodes=new OpcodeReader();
            List<?> oldMethods=list(source,"methods"),newMethods=list(destination,"methods");
            if(oldMethods.size()!=newMethods.size())throw new IllegalStateException("ACTUAL_TREE_COPY_MEMBER_LAYOUT_CHANGED");
            for(int i=0;i<oldMethods.size();i++){
                Object oldMethod=oldMethods.get(i),newMethod=newMethods.get(i);Object[] originals=actualInstructions(oldMethod,opcodes),copies=actualInstructions(newMethod,opcodes);
                if(originals.length!=copies.length)throw new IllegalStateException("ACTUAL_TREE_COPY_INSTRUCTION_LAYOUT_CHANGED");
                for(int j=0;j<originals.length;j++)IDS.put(new Key(copies[j],true),id(originals[j]));
                List<?> oldHandlers=list(oldMethod,"tryCatchBlocks"),newHandlers=list(newMethod,"tryCatchBlocks");if(oldHandlers.size()!=newHandlers.size())throw new IllegalStateException("ACTUAL_TREE_COPY_HANDLER_LAYOUT_CHANGED");
                for(int j=0;j<oldHandlers.size();j++)IDS.put(new Key(newHandlers.get(j),true),id(oldHandlers.get(j)));
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_TREE_COPY_IDENTITIES_UNAVAILABLE",failure);}
    }
    /** Opaque native callbacks replace complete changed bodies; untouched bodies keep the exact input history. */
    static void nativeEncoded(ClassLoader loader,String name,byte[] original,byte[] bytes,Module[] writers){
        if(ControlImages.sameBytes(canonical(original),canonical(bytes)))return;
        Encoded prior=selected(loader,name,original);
        Snapshot before=prior==null?nativeSnapshot(original,null):prior.snapshot();
        Snapshot after=nativeSnapshot(bytes,before);
        List<Patch> patches=new ArrayList<>();if(prior!=null)patches.addAll(prior.patches());
        Module[] actual=writers.clone();patches.add(new Patch(actual,before,after,actual));
        Encoded image=new Encoded(new Ref<>(loader),loader==null,name,hash(canonical(bytes)),after,List.copyOf(patches));
        protect(image,image.digest(),actual,before,after,patches);store(image);
    }
    private static byte[] nativeMethodImage(MethodNode method){
        ClassNode node=new ClassNode();node.version=Opcodes.V17;node.access=Opcodes.ACC_PUBLIC;node.name="NativeCallbackBody";node.superName="java/lang/Object";node.methods.add(copyMethod(method));
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static Snapshot nativeSnapshot(byte[] bytes,Snapshot previous){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        Map<String,Body> bodies=new TreeMap<>();
        for(MethodNode method:node.methods){
            String key=method.name+method.desc;Body prior=previous==null?null:previous.methods().get(key);
            if(prior!=null&&ControlImages.sameBytes(canonical(nativeMethodImage(prior.method())),canonical(nativeMethodImage(method)))){bodies.put(key,prior);continue;}
            IdentityHashMap<AbstractInsnNode,Long> identities=new IdentityHashMap<>();
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=0)identities.put(instruction,nextIdentity());
            IdentityHashMap<LabelNode,Long> labels=new IdentityHashMap<>();long next=0;
            for(AbstractInsnNode instruction=method.instructions.getLast();instruction!=null;instruction=instruction.getPrevious())if(instruction.getOpcode()>=0)next=identities.get(instruction);else if(instruction instanceof LabelNode label)labels.put(label,next);
            List<Instruction> instructions=new ArrayList<>();for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=0)instructions.add(new Instruction(identities.get(instruction),instruction,content(instruction,labels),labels));
            List<Handler> handlers=new ArrayList<>();for(TryCatchBlockNode handler:method.tryCatchBlocks)handlers.add(new Handler(nextIdentity(),labels.get(handler.start),labels.get(handler.end),labels.get(handler.handler),handler.type));
            bodies.put(key,new Body(method,List.copyOf(instructions),List.copyOf(handlers)));
        }
        return new Snapshot(bytes.clone(),node,Map.copyOf(bodies));
    }
    static synchronized Module[] encoded(Object writer,byte[] bytes){
        History history=WRITERS.get(new Key(writer,false));if(history==null||history.patches.isEmpty())return new Module[0];
        Module module=history.module.get();if(module==null)return new Module[0];
        if(!ControlImages.sameBytes(canonical(history.current.bytes()),canonical(bytes))){for(Patch patch:history.patches)for(Module owner:patch.owners())gap(owner,"POST_TRANSFORM_CODE_CHANGED_UNOBSERVED");return new Module[0];}
        Encoded image=new Encoded(new Ref<>(module.getClassLoader()),module.getClassLoader()==null,history.current.node().name,hash(canonical(bytes)),history.current,List.copyOf(history.patches));
        protect(image.digest());store(image);
        Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());for(Patch patch:history.patches)sources.addAll(Arrays.asList(patch.owners()));return sources.toArray(Module[]::new);
    }
    private static Snapshot snapshot(Object tree){
        byte[] bytes=image(tree);ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        try{
            OpcodeReader opcodes=new OpcodeReader();
            Map<String,Body> bodies=new TreeMap<>();List<?> externalMethods=list(tree,"methods");
            for(int m=0;m<node.methods.size();m++){
                MethodNode method=node.methods.get(m);Object external=externalMethods.get(m);Object[] actual=actualInstructions(external,opcodes);
                AbstractInsnNode[] parsed=method.instructions.toArray();int parsedCount=0;
                for(AbstractInsnNode instruction:parsed)if(instruction.getOpcode()>=0)parsedCount++;
                if(actual.length!=parsedCount)throw new IllegalStateException("ASM_INSTRUCTION_LAYOUT_CHANGED");
                // External labels were never consumed. The parsed labels below
                // determine jump/handler destinations using the same actual
                // instruction identities, assigned once in reverse order.
                long[] instructionIds=new long[actual.length];
                for(int i=actual.length-1;i>=0;i--)instructionIds[i]=id(actual[i]);
                IdentityHashMap<LabelNode,Long> parsedLabels=new IdentityHashMap<>();long next=0;int index=actual.length;
                for(AbstractInsnNode instruction=method.instructions.getLast();instruction!=null;instruction=instruction.getPrevious())if(instruction.getOpcode()>=0)next=instructionIds[--index];else if(instruction instanceof LabelNode label)parsedLabels.put(label,next);
                Instruction[] code=new Instruction[actual.length];int atCode=0;
                for(AbstractInsnNode instruction:parsed)if(instruction.getOpcode()>=0){
                    long identity=instructionIds[atCode];String content=content(instruction,parsedLabels);
                    code[atCode++]=new Instruction(identity,instruction,content,parsedLabels);
                }
                Handler[] handlers=new Handler[method.tryCatchBlocks.size()];List<?> actualHandlers=list(external,"tryCatchBlocks");
                if(actualHandlers.size()!=method.tryCatchBlocks.size())throw new IllegalStateException("ASM_HANDLER_LAYOUT_CHANGED");
                for(int i=0;i<handlers.length;i++){TryCatchBlockNode handler=method.tryCatchBlocks.get(i);handlers[i]=new Handler(id(actualHandlers.get(i)),parsedLabels.get(handler.start),parsedLabels.get(handler.end),parsedLabels.get(handler.handler),handler.type);}
                bodies.put(method.name+method.desc,new Body(method,List.of(code),List.of(handlers)));
            }
            Snapshot snapshot=new Snapshot(bytes,node,Map.copyOf(bodies));
            Object[] roots=new Object[bodies.size()+3];roots[0]=bytes;roots[1]=node;roots[2]=snapshot.methods();int at=3;
            // Every instruction in a method retains the same parsedLabels map.
            // Protect the union once for this fresh snapshot, including each
            // nonempty method's map; do not traverse its graph per instruction.
            for(Body body:bodies.values())if(!body.instructions().isEmpty())roots[at++]=body.instructions().get(0).labels();
            protect(roots);return snapshot;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ASM_INSTRUCTION_IDENTITY_UNOBSERVED",failure);}
    }
    private static String content(AbstractInsnNode code,Map<LabelNode,Long> labels){
        String prefix=code.getOpcode()+":";
        if(code instanceof VarInsnNode value)return prefix+value.var;
        if(code instanceof IntInsnNode value)return prefix+value.operand;
        if(code instanceof TypeInsnNode value)return prefix+value.desc;
        if(code instanceof FieldInsnNode value)return prefix+value.owner+":"+value.name+":"+value.desc;
        if(code instanceof MethodInsnNode value)return prefix+value.owner+":"+value.name+":"+value.desc+":"+value.itf;
        if(code instanceof InvokeDynamicInsnNode value)return prefix+value.name+value.desc+":"+value.bsm+":"+Arrays.deepToString(value.bsmArgs);
        if(code instanceof LdcInsnNode value)return prefix+value.cst.getClass().getName()+":"+value.cst;
        if(code instanceof IincInsnNode value)return prefix+value.var+":"+value.incr;
        if(code instanceof JumpInsnNode value)return prefix+labels.get(value.label);
        if(code instanceof TableSwitchInsnNode value)return prefix+value.min+":"+value.max+":"+labels.get(value.dflt)+":"+value.labels.stream().map(labels::get).toList();
        if(code instanceof LookupSwitchInsnNode value)return prefix+value.keys+":"+labels.get(value.dflt)+":"+value.labels.stream().map(labels::get).toList();
        if(code instanceof MultiANewArrayInsnNode value)return prefix+value.desc+":"+value.dims;
        return prefix;
    }
    static byte[] transform(ClassLoader loader,String name,Class<?> type,byte[] bytes){
        return transform(loader,name,type,bytes,false);
    }
    private static Encoded selected(ClassLoader loader,String name,byte[] bytes){
        if(name==null)return null;byte[] digest=null;
        List<Encoded> candidates=ENCODED.names().getOrDefault(name,List.of());
        for(int i=candidates.size()-1;i>=0;i--){
            Encoded image=candidates.get(i);
            if(image.bootstrap()!=(loader==null)||image.loader().get()!=loader||!image.name().equals(name))continue;
            // Canonicalize the current bytes only when this exact loader/name
            // has a recorded image to compare. No match is reused by a later
            // definition or by a different byte array.
            if(digest==null)digest=hash(canonical(bytes));
            if(MessageDigest.isEqual(image.digest(),digest))return image;
        }
        return null;
    }
    private static byte[] transform(ClassLoader loader,String name,Class<?> type,byte[] bytes,boolean payload){
        Encoded selected=selected(loader,name,bytes);
        if(selected==null)return null;
        boolean stopped=selected.patches().stream().anyMatch(patch->Arrays.stream(patch.owners()).anyMatch(ModGroupBoundary::stopped));if(!stopped)return null;
        ClassNode node=new ClassNode();new ClassReader(selected.snapshot().bytes()).accept(node,0);Map<String,Body> bodies=new HashMap<>(selected.snapshot().methods());boolean changed=false;
        Map<String,Set<Integer>> replayedBodies=new HashMap<>();
        List<Patch> patches=selected.patches();for(int i=patches.size()-1;i>=0;i--){Patch patch=patches.get(i);Module owner=Arrays.stream(patch.owners()).filter(ModGroupBoundary::stopped).findFirst().orElse(null);if(owner==null)continue;
            if(patch.writers().length==0||Arrays.stream(patch.writers()).anyMatch(writer->!ModGroupBoundary.stopped(writer))){
                gap(owner,name+":FOREIGN_DEPENDENT_TRANSFORM_REPLAY_PENDING");continue;
            }
            ClassNode priorClass=patch.before().node(),appliedClass=patch.after().node();
            if(!ControlImages.sameBytes(classMetadata(priorClass),classMetadata(appliedClass))){
                if(!ControlImages.sameBytes(classMetadata(node),classMetadata(appliedClass)))gap(owner,name+":OVERLAPPING_EXTERNAL_CLASS_METADATA");
                else if(type!=null){gap(owner,name+":EXTERNAL_LIVE_CLASS_METADATA_LAYOUT_PENDING");}
                else {classMetadataFrom(priorClass,node);changed=true;}
            }
            for(var entry:patch.after().methods().entrySet()){
                String key=entry.getKey();Body after=entry.getValue(),before=patch.before().methods().get(key),current=bodies.get(key);
                if(current==null){gap(owner,name+"#"+key+":CURRENT_MEMBER_UNOBSERVED");continue;}
                MethodNode destination=node.methods.stream().filter(method->(method.name+method.desc).equals(key)).findFirst().orElse(null);
                if(destination==null){gap(owner,name+"#"+key+":CURRENT_MEMBER_UNOBSERVED");continue;}
                if(before!=null&&!ControlImages.sameBytes(methodMetadata(before.method()),methodMetadata(after.method()))){
                    if(!ControlImages.sameBytes(methodMetadata(destination),methodMetadata(after.method())))gap(owner,name+"#"+key+":OVERLAPPING_EXTERNAL_METHOD_METADATA");
                    else {
                        int access=destination.access;methodMetadataFrom(before.method(),destination);
                        if(type!=null&&access!=destination.access){destination.access=access;gap(owner,name+"#"+key+":EXTERNAL_LIVE_METHOD_MODIFIERS_PENDING");}
                        changed=true;
                    }
                }
                if(before!=null&&same(before,after))continue;
                if(before==null){
                    if(payload){
                        if(!same(current,after)){gap(owner,name+"#"+key+":OUTSIDE_EDIT_ON_ADDED_MEMBER");continue;}
                        node.methods.remove(destination);bodies.remove(key);changed=true;continue;
                    }
                    if(destination.name.startsWith("<")||(destination.access&(Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0){gap(owner,name+"#"+key+":EXTERNAL_SPECIAL_MEMBER_PENDING");continue;}
                    if(!same(current,after)){gap(owner,name+"#"+key+":OUTSIDE_EDIT_ON_ADDED_MEMBER");continue;}
                    ModGroupBoundary.suppressExternal(owner,destination);bodies.remove(key);changed=true;continue;
                }
                if(laterInstructionEdit(patches,i,key,before,after,replayedBodies.getOrDefault(key,Set.of()))){gap(owner,name+"#"+key+":OVERLAPPING_EXTERNAL_INSTRUCTION_EDIT");continue;}
                if(laterHandlerEdit(patches,i,key,before,after,replayedBodies.getOrDefault(key,Set.of()))){gap(owner,name+"#"+key+":OVERLAPPING_EXTERNAL_HANDLER_EDIT");continue;}
                Body restored=undo(current,before,after);if(restored==null){gap(owner,name+"#"+key+":OVERLAPPING_EXTERNAL_EDIT");continue;}
                if(!install(destination,restored)){gap(owner,name+"#"+key+":EXTERNAL_BRANCH_DEPENDENCY");continue;}bodies.put(key,restored);replayedBodies.computeIfAbsent(key,ignored->new HashSet<>()).add(i);changed=true;
            }
            for(String key:patch.before().methods().keySet())if(!patch.after().methods().containsKey(key)){
                if(payload&&!bodies.containsKey(key)){
                    Body original=patch.before().methods().get(key);MethodNode restored=copyMethod(original.method());
                    if(!install(restored,original)){gap(owner,name+"#"+key+":REMOVED_PAYLOAD_MEMBER_BRANCH_PENDING");continue;}
                    node.methods.add(restored);bodies.put(key,original);changed=true;
                }else gap(owner,name+"#"+key+":REMOVED_MEMBER_SCHEMA_PENDING");
            }
            Map<String,FieldNode> beforeFields=fields(patch.before().node()),afterFields=fields(patch.after().node());
            if(payload)for(var entry:afterFields.entrySet())if(!beforeFields.containsKey(entry.getKey())){
                FieldNode current=fields(node).get(entry.getKey());if(current==null)continue;
                if(!sameField(current,entry.getValue())){gap(owner,name+"#"+entry.getKey()+":OUTSIDE_EDIT_ON_ADDED_FIELD");continue;}
                node.fields.remove(current);changed=true;
            }
            for(var entry:beforeFields.entrySet()){
                FieldNode before=entry.getValue(),after=afterFields.get(entry.getKey());if(sameField(before,after))continue;
                FieldNode current=fields(node).get(entry.getKey());
                if(after==null){
                    if(current!=null){gap(owner,name+"#"+entry.getKey()+":OUTSIDE_EDIT_ON_REMOVED_FIELD");continue;}
                    if(type!=null){gap(owner,name+"#"+entry.getKey()+":EXTERNAL_REMOVED_FIELD_LAYOUT_PENDING");continue;}
                    node.fields.add(copyField(before));changed=true;continue;
                }
                if(current==null||!sameField(current,after)){gap(owner,name+"#"+entry.getKey()+":OVERLAPPING_EXTERNAL_FIELD_EDIT");continue;}
                FieldNode restored=copyField(before);
                if(type!=null){
                    // Keep the real live VM layout. Signature and annotation attributes can be restored independently.
                    if(current.access!=before.access||!Objects.equals(current.value,before.value))gap(owner,name+"#"+entry.getKey()+":EXTERNAL_LIVE_FIELD_LAYOUT_OR_VALUE_PENDING");
                    restored.access=current.access;restored.value=current.value;
                }
                if(!sameField(current,restored)){node.fields.set(node.fields.indexOf(current),restored);changed=true;}
            }
        }
        if(!changed)return null;
        // Replay still uses the original instruction identities. Defer legacy
        // subroutine expansion until prepare() has attached their source rows.
        ClassWriter writer=node.methods.stream().anyMatch(ExternalCodeRuntime::legacy)?new ClassWriter(ClassWriter.COMPUTE_MAXS):new ControlClassWriter(loader,node);
        node.accept(writer);byte[] result=writer.toByteArray();
        store(new Encoded(new Ref<>(loader),loader==null,node.name,hash(canonical(result)),new Snapshot(result,node,Map.copyOf(bodies)),patches));return result;
    }
    private static byte[] classMetadata(ClassNode node){
        ClassNode copy=new ClassNode();classMetadataFrom(node,copy);ClassWriter writer=new ClassWriter(0);copy.accept(writer);return writer.toByteArray();
    }
    private static void classMetadataFrom(ClassNode source,ClassNode destination){
        for(Field field:ClassNode.class.getFields())if(!Modifier.isStatic(field.getModifiers())&&!Set.of("methods","fields").contains(field.getName()))try{field.set(destination,field.get(source));}
        catch(IllegalAccessException unavailable){throw new IllegalStateException("ACTUAL_CLASS_METADATA_REPLAY_UNAVAILABLE",unavailable);}
    }
    private static byte[] methodMetadata(MethodNode method){
        ClassNode node=new ClassNode();node.version=Opcodes.V17;node.access=Opcodes.ACC_PUBLIC;node.name="dev/ronova/pro/MethodImage";node.superName="java/lang/Object";
        MethodNode copy=new MethodNode(Opcodes.ASM8);methodMetadataFrom(method,copy);node.methods.add(copy);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static void methodMetadataFrom(MethodNode source,MethodNode destination){
        destination.access=source.access;destination.name=source.name;destination.desc=source.desc;
        destination.signature=source.signature;destination.exceptions=source.exceptions;
        destination.parameters=source.parameters;destination.visibleAnnotations=source.visibleAnnotations;
        destination.invisibleAnnotations=source.invisibleAnnotations;destination.visibleTypeAnnotations=source.visibleTypeAnnotations;
        destination.invisibleTypeAnnotations=source.invisibleTypeAnnotations;destination.attrs=source.attrs;
        destination.annotationDefault=source.annotationDefault;
        destination.visibleAnnotableParameterCount=source.visibleAnnotableParameterCount;
        destination.visibleParameterAnnotations=source.visibleParameterAnnotations;
        destination.invisibleAnnotableParameterCount=source.invisibleAnnotableParameterCount;
        destination.invisibleParameterAnnotations=source.invisibleParameterAnnotations;
    }
    private static MethodNode copyMethod(MethodNode method){MethodNode result=new MethodNode(Opcodes.ASM8,method.access,method.name,method.desc,method.signature,method.exceptions.toArray(String[]::new));method.accept(result);return result;}
    private static Object parseTree(ClassLoader loader,byte[] bytes)throws ReflectiveOperationException {
        Class<?> type=library("org.objectweb.asm.tree.ClassNode",loader);Object tree=type.getConstructor().newInstance();
        Class<?> reader=library("org.objectweb.asm.ClassReader",loader),visitor=Class.forName("org.objectweb.asm.ClassVisitor",false,loader);
        Object input=reader.getConstructor(byte[].class).newInstance((Object)bytes);reader.getMethod("accept",visitor,int.class).invoke(input,tree,0);return tree;
    }
    /** The caller supplies the real payload origins, so an unrecorded foreign edit never disappears from provenance. */
    static synchronized Object[] mixinPayload(Object tree,Module origin,Module[] contributors,boolean bodyOnly){
        History history=TREES.get(new Key(tree,false));if(history==null||history.module.get()!=origin)return null;
        Snapshot original=snapshot(tree);if(!ControlImages.sameBytes(canonical(original.bytes()),canonical(history.current.bytes())))return null;
        Set<Module> stopped=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Module module:contributors)if(module!=origin&&ModGroupBoundary.stopped(module))stopped.add(module);
        if(stopped.isEmpty())return new Object[]{tree,contributors.clone(),Boolean.FALSE,Boolean.FALSE,Boolean.FALSE};
        for(Module module:stopped)if(history.patches.stream().noneMatch(patch->Arrays.asList(patch.owners()).contains(module))){gap(module,original.node().name+":FOREIGN_MIXIN_PATCH_UNOBSERVED");return null;}
        ClassLoader loader=origin.getClassLoader();Encoded input=new Encoded(new Ref<>(loader),loader==null,original.node().name,hash(canonical(original.bytes())),original,List.copyOf(history.patches));store(input);
        List<String> previous=DISPOSITION_FAILURES.get(),failures=new ArrayList<>();DISPOSITION_FAILURES.set(failures);
        try{
            byte[] restored=transform(loader,original.node().name,null,original.bytes(),true);
            if(restored==null||!failures.isEmpty())return null;
            boolean metadataChanged=!ControlImages.sameBytes(metadata(original.bytes()),metadata(restored));
            if(bodyOnly&&metadataChanged){for(Module module:stopped)gap(module,original.node().name+":MIXIN_PREPARED_METADATA_REPLAY_PENDING");return null;}
            Encoded image=selected(loader,original.node().name,restored);if(image==null)throw new IllegalStateException("RESTORED_MIXIN_IMAGE_UNOBSERVED");Object replacement=parseTree(tree.getClass().getClassLoader(),restored);identities(replacement,image.snapshot().methods());
            Snapshot current=snapshot(replacement);History replayed=new History(origin,current);
            for(Patch patch:history.patches)if(Arrays.stream(patch.owners()).noneMatch(ModGroupBoundary::stopped))replayed.patches.add(patch);
            TREES.put(new Key(replacement,true),replayed);parents(replacement);
            Set<Module> remaining=Collections.newSetFromMap(new IdentityHashMap<>());remaining.add(origin);
            for(Module module:contributors)if(!stopped.contains(module))remaining.add(module);
            store(new Encoded(new Ref<>(loader),loader==null,current.node().name,hash(canonical(current.bytes())),current,List.copyOf(replayed.patches)));
            return new Object[]{replacement,remaining.toArray(Module[]::new),Boolean.TRUE,metadataChanged,
                    !ControlImages.sameBytes(mixinSchedule(original.bytes()),mixinSchedule(restored))};
        }catch(ReflectiveOperationException|RuntimeException unavailable){for(Module module:stopped)gap(module,original.node().name+":FOREIGN_MIXIN_REPLAY_FAILED:"+unavailable.getClass().getSimpleName());return null;}
        finally{if(previous==null)DISPOSITION_FAILURES.remove();else DISPOSITION_FAILURES.set(previous);}
    }
    private static byte[] metadata(byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        for(MethodNode method:node.methods){method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;method.visibleLocalVariableAnnotations=null;method.invisibleLocalVariableAnnotations=null;method.maxStack=0;method.maxLocals=0;}
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static byte[] mixinSchedule(byte[] bytes){
        ClassNode original=new ClassNode();new ClassReader(bytes).accept(original,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        ClassNode selected=new ClassNode();selected.version=Opcodes.V1_8;selected.access=Opcodes.ACC_PUBLIC;
        selected.name=original.name;selected.superName="java/lang/Object";
        Set<String> annotations=Set.of("Lorg/spongepowered/asm/mixin/Mixin;","Lorg/spongepowered/asm/mixin/Pseudo;");
        if(original.visibleAnnotations!=null)selected.visibleAnnotations=original.visibleAnnotations.stream().filter(annotation->annotations.contains(annotation.desc)).toList();
        if(original.invisibleAnnotations!=null)selected.invisibleAnnotations=original.invisibleAnnotations.stream().filter(annotation->annotations.contains(annotation.desc)).toList();
        ClassWriter writer=new ClassWriter(0);selected.accept(writer);return writer.toByteArray();
    }
    static synchronized Module[] mixinSelection(Object tree,Module origin,Module[] contributors){
        History history=TREES.get(new Key(tree,false));if(history==null||history.module.get()!=origin)return contributors.clone();
        Snapshot actual=snapshot(tree);if(!ControlImages.sameBytes(canonical(actual.bytes()),canonical(history.current.bytes())))return contributors.clone();
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.add(origin);
        for(Module contributor:contributors){
            if(contributor==origin)continue;boolean observed=false;
            for(Patch patch:history.patches)if(Arrays.asList(patch.owners()).contains(contributor)){
                observed=true;if(!ControlImages.sameBytes(mixinSchedule(patch.before().bytes()),mixinSchedule(patch.after().bytes())))selected.add(contributor);
            }
            if(!observed)selected.add(contributor);
        }
        return selected.toArray(Module[]::new);
    }
    /** Sources of the installed ClassInfo constructor's header, member flags and frame data. */
    static synchronized Map<String,Module[]> mixinMetadata(Object tree,Module origin,Module[] contributors){
        History history=TREES.get(new Key(tree,false));
        if(history==null||history.module.get()!=origin)return Map.of("*",contributors.clone());
        byte[] bytes=image(tree);protect(bytes);
        Map<String,String> recorded=history.current.metadata(),current;
        if(ControlImages.sameBytes(bytes,history.current.bytes())){
            validateMetadataLayout(tree,history.current.node());current=recorded;
        }else current=metadataRows(metadataNode(tree,bytes));
        if(!current.equals(recorded))return Map.of("*",contributors.clone());
        Set<String> keys=new LinkedHashSet<>(current.keySet());List<Map<String,String>> before=new ArrayList<>(),after=new ArrayList<>();
        Set<Module> observed=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Patch patch:history.patches){
            Map<String,String> first=patch.before().metadata(),second=patch.after().metadata();before.add(first);after.add(second);keys.addAll(first.keySet());keys.addAll(second.keySet());observed.addAll(Arrays.asList(patch.owners()));
        }
        Set<Module> fallback=Collections.newSetFromMap(new IdentityHashMap<>());fallback.add(origin);
        for(Module contributor:contributors)if(!observed.contains(contributor))fallback.add(contributor);
        Map<String,Module[]> result=new LinkedHashMap<>();result.put("*",fallback.toArray(Module[]::new));
        for(String key:keys){
            Set<Module> sources=Collections.newSetFromMap(new IdentityHashMap<>());sources.addAll(fallback);
            for(int i=0;i<history.patches.size();i++)if(!Objects.equals(before.get(i).get(key),after.get(i).get(key)))
                for(Module owner:history.patches.get(i).owners())if(Arrays.asList(contributors).contains(owner))sources.add(owner);
            result.put(key,sources.toArray(Module[]::new));
        }
        return Map.copyOf(result);
    }
    private static ClassNode metadataNode(Object tree,byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        validateMetadataLayout(tree,node);protect(bytes,node);return node;
    }
    private static void validateMetadataLayout(Object tree,ClassNode node){
        try{
            OpcodeReader opcodes=new OpcodeReader();
            // Metadata consumes the fresh class/frame image, not instruction history.
            // Keep the same actual method/instruction/handler layout checks without
            // registering identities or constructing unused Body/Instruction graphs.
            List<?> externalMethods=list(tree,"methods");
            for(int m=0;m<node.methods.size();m++){
                validateMemberLayout(externalMethods.get(m),node.methods.get(m),opcodes);
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ASM_INSTRUCTION_IDENTITY_UNOBSERVED",failure);}
    }
    private static void validateMemberLayout(Object external,MethodNode method,OpcodeReader opcodes)throws ReflectiveOperationException{
        int actual=0,parsed=0;
        for(Object instruction:instructions(external))if(opcodes.read(instruction)>=0)actual++;
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=0)parsed++;
        if(actual!=parsed)throw new IllegalStateException("ASM_INSTRUCTION_LAYOUT_CHANGED");
        if(list(external,"tryCatchBlocks").size()!=method.tryCatchBlocks.size())throw new IllegalStateException("ASM_HANDLER_LAYOUT_CHANGED");
    }
    private static Map<String,String> metadataRows(ClassNode node){
        Map<String,String> rows=new LinkedHashMap<>();ClassNode header=new ClassNode();
        header.version=node.version;header.access=node.access;header.name=node.name;header.superName=node.superName;header.signature=node.signature;
        header.interfaces=new ArrayList<>(node.interfaces);header.outerClass=node.outerClass;header.nestHostClass=node.nestHostClass;
        header.nestMembers=node.nestMembers==null?null:new ArrayList<>(node.nestMembers);
        for(InnerClassNode inner:node.innerClasses)if(Objects.equals(inner.name,node.name))header.innerClasses.add(inner);
        for(FieldNode field:node.fields)if((field.access&Opcodes.ACC_SYNTHETIC)!=0&&field.name.startsWith("this$"))
            header.fields.add(new FieldNode(field.access,field.name,field.desc,null,null));
        ClassWriter writer=new ClassWriter(0);header.accept(writer);rows.put("H",Base64.getEncoder().encodeToString(writer.toByteArray()));
        for(FieldNode field:node.fields)rows.put("F"+field.name+'\u0000'+field.desc,Base64.getEncoder().encodeToString(fieldImage(field)));
        for(MethodNode method:node.methods)rows.put("M"+method.name+'\u0000'+method.desc,methodMetadataRow(method));
        return rows;
    }
    private static String methodMetadataRow(MethodNode method){
        StringBuilder image=new StringBuilder(Base64.getEncoder().encodeToString(methodMetadata(method)));
        AbstractInsnNode[] instructions=method.instructions.toArray();
        for(int i=0;i<instructions.length;i++)if(instructions[i] instanceof FrameNode frame){
            image.append('|').append(i).append(':').append(frame.type);frameValues(image,method,frame.local);frameValues(image,method,frame.stack);
        }
        return image.toString();
    }
    private static void frameValues(StringBuilder image,MethodNode method,List<Object> values){
        if(values==null){image.append(":null");return;}image.append(':').append(values.size());
        for(Object value:values)if(value instanceof LabelNode label)image.append(":L").append(method.instructions.indexOf(label));
        else {String text=String.valueOf(value);image.append(':').append(text.length()).append('/').append(text);}
    }
    static synchronized Object[] memberMetadata(Object member){
        Key parent=PARENTS.get(new Key(member,false));Object tree=parent==null?null:parent.get();History history=tree==null?null:TREES.get(new Key(tree,false));
        if(history==null||history.module.get()==null)return null;
        try{
            // The observed parent retains the actual member identities. Its
            // method/field lists determine the kind without inventing a new
            // library role for a member class that has no boundary registration.
            boolean method=false;for(Object actual:list(tree,"methods"))if(actual==member){method=true;break;}
            if(!method){
                boolean field=false;for(Object actual:list(tree,"fields"))if(actual==member){field=true;break;}
                if(!field)return null;
            }
            Module origin=history.module.get();Set<Module> contributors=Collections.newSetFromMap(new IdentityHashMap<>());contributors.add(origin);
            for(Patch patch:history.patches)contributors.addAll(Arrays.asList(patch.owners()));
            // A member constructor consumes one actual member's metadata and
            // frames. Serialize and validate that member once; ClassInfo's
            // completed header/tree observation remains the independent full
            // tree entry. Unrelated members cannot alter this selected row.
            ClassNode projection=memberImage(tree,member,method);
            String key=(method?"M":"F")+get(member,"name")+'\u0000'+get(member,"desc"),current;
            if(method){
                MethodNode parsed=projection.methods.get(0);validateMemberLayout(member,parsed,new OpcodeReader());
                current=methodMetadataRow(parsed);
            }else current=Base64.getEncoder().encodeToString(fieldImage(projection.fields.get(0)));
            if(!Objects.equals(current,history.current.metadata().get(key)))return new Object[]{origin,contributors.toArray(Module[]::new)};
            Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.add(origin);
            Set<Module> observed=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Patch patch:history.patches){
                observed.addAll(Arrays.asList(patch.owners()));
                if(!Objects.equals(patch.before().metadata().get(key),patch.after().metadata().get(key)))selected.addAll(Arrays.asList(patch.owners()));
            }
            for(Module contributor:contributors)if(!observed.contains(contributor))selected.add(contributor);
            return new Object[]{origin,selected.toArray(Module[]::new)};
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_MIXIN_MEMBER_SOURCE_UNAVAILABLE",failure);}
    }
    private static ClassNode memberImage(Object tree,Object member,boolean method)throws ReflectiveOperationException{
        ClassLoader loader=member.getClass().getClassLoader();Class<?> parent=library("org.objectweb.asm.tree.ClassNode",loader);
        Class<?> role=Class.forName("org.objectweb.asm.tree."+(method?"MethodNode":"FieldNode"),false,loader);
        // Membership in the observed parent supplies the real library role.
        // FieldNode has no independent boundary registration; do not invent one.
        if(role.getModule()!=parent.getModule())throw new IllegalStateException("ACTUAL_MEMBER_LIBRARY_MODULE_CHANGED");
        if(!role.isInstance(member))throw new IllegalStateException("ACTUAL_ASM_MEMBER_REQUIRED");
        Class<?> visitor=Class.forName("org.objectweb.asm.ClassVisitor",false,loader),writer=library("org.objectweb.asm.ClassWriter",loader);
        Method accept=member.getClass().getMethod("accept",visitor);
        if(accept.getDeclaringClass()!=role)throw new IllegalStateException("CUSTOM_MEMBER_SERIALIZATION_UNOBSERVED");
        Object output=writer.getConstructor(int.class).newInstance(0);
        // Preserve the method's real owner/version: initial frame locals and
        // uninitialized-this values belong to this class, not a dummy owner.
        writer.getMethod("visit",int.class,int.class,String.class,String.class,String.class,String[].class)
                .invoke(output,method?get(tree,"version"):Opcodes.V17,method?get(tree,"access"):Opcodes.ACC_PUBLIC,
                        method?get(tree,"name"):"dev/ronova/pro/FieldImage",method?get(tree,"signature"):null,
                        method?get(tree,"superName"):"java/lang/Object",method?list(tree,"interfaces").toArray(String[]::new):null);
        accept.invoke(member,output);writer.getMethod("visitEnd").invoke(output);
        byte[] bytes=(byte[])writer.getMethod("toByteArray").invoke(output);ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if(method?node.methods.size()!=1:node.fields.size()!=1)throw new IllegalStateException("ACTUAL_SINGLE_MEMBER_IMAGE_REQUIRED");return node;
    }
    static ExecutionSources executionSources(ClassLoader loader,String name,byte[] bytes,boolean declaredProducer){
        if(name==null)return new ExecutionSources(null,Map.of(),new Object[0][]);
        Encoded selected=selected(loader,name,bytes);
        if(selected==null&&!declaredProducer){
            // There is no source row to propagate yet. accepted() already keeps
            // these exact semantic bytes and constant-pool caller references,
            // and prepared/changed refreshes later real producer bindings.
            ClassNode header=new ClassNode();new ClassReader(bytes).accept(header,
                    ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            return new ExecutionSources(new ExternalCodeFlow.Image(new Object(),header,Map.of(),Map.of()),Map.of(),new Object[0][]);
        }
        Map<String,Module[]> fields=new LinkedHashMap<>();
        for(var entry:fieldOwners(selected).entrySet()){
            Module[] owners=executionOwners(entry.getValue());if(owners.length!=0)fields.put(entry.getKey(),owners);
        }
        List<Object[]> values=new ArrayList<>();
        for(Object[] entry:fieldValues(name,selected)){
            Module[] owners=executionOwners((Module[])entry[3]);
            if(owners.length!=0){Object[] row=entry.clone();row[3]=owners;values.add(row);}
        }
        return new ExecutionSources(executionImage(bytes,selected),Map.copyOf(fields),values.toArray(Object[][]::new));
    }
    // Execution propagation carries actual producers and the existing null
    // unknown marker. An opaque Native writer does not become a known sole
    // producer when its changed body is later woven into Java execution.
    // Framework and our own edits remain in History for replay decisions.
    private static Module[] executionOwners(Module[] owners){
        return Arrays.stream(owners).filter(owner->owner==null||RecoveryAgent.producerModule(owner)).toArray(Module[]::new);
    }
    private static ExternalCodeFlow.Image executionImage(byte[] bytes,Encoded selected){
        if(selected==null)return acceptedExecution(bytes,Map.of());Map<String,Module[][]> methods=new LinkedHashMap<>();Map<String,List<ExternalCodeFlow.ControlChange>> comparisons=new LinkedHashMap<>();
        Map<String,Module[]> fieldReads=fieldReadOwners(selected);
        for(var entry:selected.snapshot().methods().entrySet()){
            String key=entry.getKey();Body current=entry.getValue();Module[][] owners=new Module[current.instructions().size()][];
            List<BodyPatch> patches=new ArrayList<>();
            for(Patch patch:selected.patches()){
                Body before=patch.before().methods().get(key),after=patch.after().methods().get(key);if(after==null)continue;
                patches.add(new BodyPatch(patch.owners(),before==null?Map.of():index(before.instructions()),index(after.instructions())));
            }
            for(int ordinal=0;ordinal<current.instructions().size();ordinal++){
                Instruction instruction=current.instructions().get(ordinal);Module[] source=NO_MODULES;
                for(BodyPatch patch:patches){
                    Instruction applied=patch.after().get(instruction.id()),previous=patch.before().get(instruction.id());
                    if(applied!=null&&(previous==null||!previous.content().equals(applied.content()))&&instruction.content().equals(applied.content()))source=patch.owners();
                }
                owners[ordinal]=source.length==0?NO_MODULES:executionOwners(source);
            }
            for(int ordinal=0;ordinal<owners.length;ordinal++)if(current.instructions().get(ordinal).code() instanceof FieldInsnNode access&&access.owner.equals(selected.snapshot().node().name)&&(access.getOpcode()==Opcodes.GETFIELD||access.getOpcode()==Opcodes.GETSTATIC)){
                Module[] origin=fieldReads.get(access.name+'\u0000'+access.desc);if(origin!=null)owners[ordinal]=join(owners[ordinal],executionOwners(origin));
            }
            List<ExternalCodeFlow.ControlChange> changes=new ArrayList<>();
            for(ExternalCodeFlow.ControlChange change:controlChanges(selected,key,current)){
                Module[] producers=executionOwners(change.owners());
                if(producers.length!=0)changes.add(new ExternalCodeFlow.ControlChange(change.at(),change.alternatives(),producers));
            }
            methods.put(key,owners);if(!changes.isEmpty())comparisons.put(key,List.copyOf(changes));
        }
        return new ExternalCodeFlow.Image(new Object(),selected.snapshot().node(),Map.copyOf(methods),Map.copyOf(comparisons));
    }
    /** Rows refer to the final emitted instructions, including the agent's inserted instructions. */
    static ExternalCodeFlow.Image acceptedExecution(byte[] bytes,Map<String,Module[][]> observed){
        // This is a semantic analysis image, never the emitted definition.
        // Keep opcodes, labels, handlers and descriptors; line/local tables and
        // stored frames are unnecessary because both analyzers compute frames.
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);Map<String,Module[][]> rows=new LinkedHashMap<>();
        for(MethodNode method:node.methods){
            String key=method.name+method.desc;int count=0;for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()>=0)count++;
            Module[][] source=observed.get(key),result=new Module[count][];
            if(source!=null&&source.length!=count)throw new IllegalStateException("EXTERNAL_ACCEPTED_INSTRUCTION_LAYOUT_CHANGED:"+key);
            // Empty rows contain no mutable ownership data. Sharing that
            // zero-length array avoids publishing a separate control object
            // for every ordinary instruction in every accepted class.
            for(int i=0;i<count;i++)result[i]=source==null||source[i].length==0?NO_MODULES:source[i].clone();rows.put(key,result);
        }
        return new ExternalCodeFlow.Image(new Object(),node,Map.copyOf(rows),Map.of());
    }
    static void protectExecution(ExternalCodeFlow.Image analysis,Map<String,Integer> declarations){
        // The retained semantic image supplies all instruction/source queries.
        // Emitted bytes and their compact declarations are protected separately.
        protect(analysis,analysis.identity(),analysis.node(),analysis.rows(),analysis.comparisons(),declarations);
    }
    static void protectDefinitionMetadata(Object... roots){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass().getNestHost()!=ExternalCodeDefinitions.class)
            throw new SecurityException("ACTUAL_DEFINITION_METADATA_REQUIRED");
        protect(roots);
    }
    private static List<Long> destinations(Instruction instruction,Long following){
        AbstractInsnNode code=instruction.code();List<Long> result=new ArrayList<>();
        if(code instanceof JumpInsnNode jump){result.add(instruction.labels().get(jump.label));if(code.getOpcode()!=Opcodes.GOTO&&code.getOpcode()!=Opcodes.JSR)result.add(following);}
        else if(code instanceof TableSwitchInsnNode table){result.add(instruction.labels().get(table.dflt));for(LabelNode label:table.labels)result.add(instruction.labels().get(label));}
        else if(code instanceof LookupSwitchInsnNode lookup){result.add(instruction.labels().get(lookup.dflt));for(LabelNode label:lookup.labels)result.add(instruction.labels().get(label));}
        return result;
    }
    private static List<ExternalCodeFlow.ControlChange> controlChanges(Encoded image,String key,Body current){
        Map<Long,Integer> positions=new HashMap<>();for(int i=0;i<current.instructions().size();i++)positions.put(current.instructions().get(i).id(),i);
        List<ExternalCodeFlow.ControlChange> result=new ArrayList<>();
        for(Patch patch:image.patches()){
            if(patch.owners().length==0)continue;Body before=patch.before().methods().get(key),after=patch.after().methods().get(key);if(before==null||after==null)continue;
            Map<Long,Instruction> applied=index(after.instructions()),original=index(before.instructions());
            Map<Long,Long> beforeNext=following(before),afterNext=following(after);
            for(int i=0;i<before.instructions().size();i++){
                Instruction previous=before.instructions().get(i),next=applied.get(previous.id());
                boolean sameContent=next!=null&&next.content().equals(previous.content());
                if(sameContent&&Objects.equals(beforeNext.get(previous.id()),afterNext.get(previous.id())))continue;
                List<Long> oldDestinations=destinations(previous,beforeNext.get(previous.id()));
                if(oldDestinations.isEmpty()&&sameContent)oldDestinations=List.of(beforeNext.get(previous.id()));
                if(oldDestinations.isEmpty())continue;
                Integer at=positions.get(previous.id());
                if(at==null)for(int j=i+1;j<before.instructions().size();j++){at=positions.get(before.instructions().get(j).id());if(at!=null)break;}
                if(at==null){for(Module module:patch.owners())gap(module,image.name()+'#'+key+":REMOVED_CONTROL_CONTINUATION_UNOBSERVED");continue;}
                int[] alternatives=new int[oldDestinations.size()];for(int j=0;j<alternatives.length;j++){Long target=oldDestinations.get(j);alternatives[j]=target==null||target==0?-1:positions.getOrDefault(target,-1);}
                result.add(new ExternalCodeFlow.ControlChange(at,alternatives,patch.owners().clone()));
            }
            // An inserted unconditional jump has displaced the observed continuation of the original stream.
            for(int i=0;i<after.instructions().size();i++){
                Instruction added=after.instructions().get(i);if(original.containsKey(added.id())||added.code().getOpcode()!=Opcodes.GOTO)continue;Integer at=positions.get(added.id());if(at==null)continue;
                int continuation=-1;for(int j=i+1;j<after.instructions().size();j++)if(original.containsKey(after.instructions().get(j).id())){continuation=positions.getOrDefault(after.instructions().get(j).id(),-1);break;}
                result.add(new ExternalCodeFlow.ControlChange(at,new int[]{continuation},patch.owners().clone()));
            }
            // Catch-table changes alter execution even when no opcode changes.
            // Preserve the observed old routes as alternatives to the live CFG.
            if(!before.handlers().equals(after.handlers()))for(Instruction instruction:current.instructions()) {
                List<Handler> oldRoutes=routes(before,instruction.id()),newRoutes=routes(after,instruction.id());
                if(oldRoutes.equals(newRoutes))continue;
                int[] alternatives=new int[oldRoutes.size()+1];alternatives[oldRoutes.size()]=-1;
                for(int j=0;j<oldRoutes.size();j++)alternatives[j]=positions.getOrDefault(oldRoutes.get(j).handler(),-1);
                result.add(new ExternalCodeFlow.ControlChange(positions.get(instruction.id()),alternatives,patch.owners().clone()));
            }
        }
        return result;
    }
    private static Map<Long,Long> following(Body body) {
        Map<Long,Long> result=new HashMap<>();List<Instruction> code=body.instructions();
        for(int i=0;i<code.size();i++)result.put(code.get(i).id(),i+1<code.size()?code.get(i+1).id():0L);
        return result;
    }
    private static List<Handler> routes(Body body,long instruction) {
        int position=find(body.instructions(),instruction);if(position<0)return List.of();
        List<Handler> result=new ArrayList<>();
        for(Handler handler:body.handlers()) {
            int start=find(body.instructions(),handler.start()),end=handler.end()==0?body.instructions().size():find(body.instructions(),handler.end());
            if(start>=0&&position>=start&&position<end)result.add(handler);
        }
        return result;
    }
    static boolean runtimeSource(Module module){
        for(Encoded image:images())for(Patch patch:image.patches())for(Module source:patch.owners())if(source==module)return true;return false;
    }
    private static Map<String,FieldNode> fields(ClassNode node){
        Map<String,FieldNode> result=new LinkedHashMap<>();for(FieldNode field:node.fields)result.put(field.name+'\u0000'+field.desc,field);return result;
    }
    private static FieldNode copyField(FieldNode field){ClassNode node=new ClassNode();field.accept(node);return node.fields.get(0);}
    private static byte[] fieldImage(FieldNode field){
        ClassNode node=new ClassNode();node.version=Opcodes.V17;node.access=Opcodes.ACC_PUBLIC;node.name="dev/ronova/pro/FieldImage";node.superName="java/lang/Object";field.accept(node);
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static boolean sameField(FieldNode a,FieldNode b){return a!=null&&b!=null&&ControlImages.sameBytes(fieldImage(a),fieldImage(b));}
    private static Module[] join(Module[] first,Module[] second){Set<Module> result=Collections.newSetFromMap(new IdentityHashMap<>());result.addAll(Arrays.asList(first));result.addAll(Arrays.asList(second));return result.toArray(Module[]::new);}
    private static Map<String,Module[]> fieldReadOwners(Encoded image){
        Map<String,FieldNode> current=fields(image.snapshot().node());Map<String,Module[]> result=new HashMap<>();
        for(Patch patch:image.patches()){
            Map<String,FieldNode> before=fields(patch.before().node());
            for(var entry:fields(patch.after().node()).entrySet()){
                if(!sameField(before.get(entry.getKey()),entry.getValue())&&sameField(current.get(entry.getKey()),entry.getValue())&&patch.owners().length!=0)
                    result.merge(entry.getKey(),patch.owners().clone(),ExternalCodeImages::join);
            }
        }
        return result;
    }
    private static Map<String,Module[]> fieldOwners(Encoded selected){
        if(selected==null)return Map.of();Map<String,FieldNode> current=fields(selected.snapshot().node());Map<String,Module[]> result=new LinkedHashMap<>();
        for(Patch patch:selected.patches()){
            Map<String,FieldNode> before=fields(patch.before().node());
            for(var entry:fields(patch.after().node()).entrySet())if(!before.containsKey(entry.getKey())&&sameField(entry.getValue(),current.get(entry.getKey()))&&patch.owners().length!=0)result.put(entry.getKey(),patch.owners().clone());
        }return Map.copyOf(result);
    }
    private static Object[][] fieldValues(String name,Encoded selected){
        if(selected==null)return new Object[0][];Map<String,FieldNode> current=fields(selected.snapshot().node());List<Object[]> result=new ArrayList<>();
        for(int p=selected.patches().size()-1;p>=0;p--){
            Patch patch=selected.patches().get(p);Map<String,FieldNode> before=fields(patch.before().node());
            for(var entry:fields(patch.after().node()).entrySet()){
                FieldNode original=before.get(entry.getKey()),applied=entry.getValue(),active=current.get(entry.getKey());
                if(original==null||active==null||original.value==null||applied.value==null||sameConstant(original.value,applied.value)||patch.owners().length==0)continue;
                if((original.access&(Opcodes.ACC_STATIC|Opcodes.ACC_FINAL))!=(Opcodes.ACC_STATIC|Opcodes.ACC_FINAL)||(applied.access&Opcodes.ACC_STATIC)==0)continue;
                boolean initializedByCode=patch.before().node().methods.stream().filter(method->method.name.equals("<clinit>"))
                        .flatMap(method->Arrays.stream(method.instructions.toArray())).anyMatch(instruction->instruction instanceof FieldInsnNode write&&write.getOpcode()==Opcodes.PUTSTATIC&&write.owner.equals(patch.before().node().name)&&write.name.equals(original.name)&&write.desc.equals(original.desc));
                if(initializedByCode){for(Module module:patch.owners())gap(module,name+'#'+entry.getKey()+":ORIGINAL_FIELD_INITIALIZER_VALUE_UNOBSERVED");continue;}
                result.add(new Object[]{entry.getKey(),applied.value,original.value,patch.owners().clone()});
            }
        }
        Object[][] rows=result.toArray(Object[][]::new);protect((Object)rows);return rows;
    }
    private static boolean sameConstant(Object first,Object second){
        if(first instanceof Float a&&second instanceof Float b)return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);
        if(first instanceof Double a&&second instanceof Double b)return Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b);return Objects.equals(first,second);
    }
    private static boolean sameFields(ClassNode before,ClassNode after){
        return before.fields.stream().map(field->field.name+field.desc+":"+field.access+":"+field.value).toList().equals(after.fields.stream().map(field->field.name+field.desc+":"+field.access+":"+field.value).toList());
    }
    private static boolean same(Body first,Body second){return first.instructions().stream().map(code->code.id()+":"+code.content()).toList().equals(second.instructions().stream().map(code->code.id()+":"+code.content()).toList())&&first.handlers().equals(second.handlers());}
    private static boolean reordered(Body before,Body after){
        return retainedOrderChanged(before.instructions().stream().map(Instruction::id).toList(),after.instructions().stream().map(Instruction::id).toList())
                ||retainedOrderChanged(before.handlers().stream().map(Handler::id).toList(),after.handlers().stream().map(Handler::id).toList());
    }
    private static boolean retainedOrderChanged(List<Long> before,List<Long> after){
        Set<Long> common=new HashSet<>(before);common.retainAll(after);
        return !before.stream().filter(common::contains).toList().equals(after.stream().filter(common::contains).toList());
    }
    /** Current equality is not ownership: retain later writes, including ABA and deletions. */
    private static boolean laterInstructionEdit(List<Patch> patches,int at,String key,Body before,Body after,Set<Integer> replayed){
        Map<Long,Instruction> original=index(before.instructions()),applied=index(after.instructions());
        Set<Long> touched=new HashSet<>(original.keySet());touched.addAll(applied.keySet());
        touched.removeIf(id->sameInstruction(original.get(id),applied.get(id)));
        if(retainedOrderChanged(before.instructions().stream().map(Instruction::id).toList(),after.instructions().stream().map(Instruction::id).toList()))
            touched.addAll(original.keySet());
        if(touched.isEmpty())return false;
        for(int i=at+1;i<patches.size();i++)if(!replayed.contains(i)){
            Patch later=patches.get(i);Body priorBody=later.before().methods().get(key),nextBody=later.after().methods().get(key);
            Map<Long,Instruction> prior=priorBody==null?Map.of():index(priorBody.instructions());
            Map<Long,Instruction> next=nextBody==null?Map.of():index(nextBody.instructions());
            for(long id:touched)if(!sameInstruction(prior.get(id),next.get(id)))return true;
            if(priorBody!=null&&nextBody!=null&&retainedOrderChanged(
                    priorBody.instructions().stream().map(Instruction::id).filter(touched::contains).toList(),
                    nextBody.instructions().stream().map(Instruction::id).filter(touched::contains).toList()))return true;
        }
        return false;
    }
    private static boolean sameInstruction(Instruction first,Instruction second){
        return first==null?second==null:second!=null&&first.content().equals(second.content());
    }
    /** A later observed rewrite owns its handler even if it reapplied the same payload. */
    private static boolean laterHandlerEdit(List<Patch> patches,int at,String key,Body before,Body after,Set<Integer> replayed){
        Map<Long,Handler> original=handlerIndex(before),applied=handlerIndex(after);Set<Long> touched=new HashSet<>(original.keySet());touched.addAll(applied.keySet());
        touched.removeIf(id->Objects.equals(original.get(id),applied.get(id)));
        if(retainedOrderChanged(before.handlers().stream().map(Handler::id).toList(),after.handlers().stream().map(Handler::id).toList()))touched.addAll(original.keySet());
        if(touched.isEmpty())return false;
        for(int i=at+1;i<patches.size();i++)if(!replayed.contains(i)){
            Patch later=patches.get(i);Map<Long,Handler> prior=handlerIndex(later.before().methods().get(key)),next=handlerIndex(later.after().methods().get(key));
            for(long id:touched)if(!Objects.equals(prior.get(id),next.get(id)))return true;
            Body priorBody=later.before().methods().get(key),nextBody=later.after().methods().get(key);
            if(priorBody!=null&&nextBody!=null&&retainedOrderChanged(
                    priorBody.handlers().stream().map(Handler::id).filter(touched::contains).toList(),
                    nextBody.handlers().stream().map(Handler::id).filter(touched::contains).toList()))return true;
        }
        return false;
    }
    private static Map<Long,Handler> handlerIndex(Body body){Map<Long,Handler> result=new HashMap<>();if(body!=null)for(Handler handler:body.handlers())result.put(handler.id(),handler);return result;}
    private static Body undo(Body current,Body before,Body after){
        List<Instruction> draft=new ArrayList<>(current.instructions());Map<Long,Instruction> old=index(before.instructions()),applied=index(after.instructions());
        for(Instruction code:after.instructions())if(!old.containsKey(code.id())){int at=find(draft,code.id());if(at>=0){if(!draft.get(at).content().equals(code.content()))return null;draft.remove(at);}}
        for(int i=0;i<before.instructions().size();i++){
            Instruction oldCode=before.instructions().get(i),newCode=applied.get(oldCode.id());int at=find(draft,oldCode.id());
            if(newCode!=null){
                if(oldCode.content().equals(newCode.content()))continue;
                if(at<0)return null;String content=draft.get(at).content();if(!content.equals(newCode.content())&&!content.equals(oldCode.content()))return null;draft.set(at,oldCode);
            }else if(at<0){
                int insertion=-1;for(int j=i+1;j<before.instructions().size();j++){int anchor=find(draft,before.instructions().get(j).id());if(anchor>=0){insertion=anchor;break;}}
                if(insertion<0)for(int j=i-1;j>=0;j--){int anchor=find(draft,before.instructions().get(j).id());if(anchor>=0){insertion=anchor+1;break;}}
                if(insertion<0){if(!draft.isEmpty())return null;insertion=0;}draft.add(insertion,oldCode);
            }
        }
        List<Handler> handlers=new ArrayList<>(current.handlers());Map<Long,Handler> oldHandlers=new HashMap<>();for(Handler handler:before.handlers())oldHandlers.put(handler.id(),handler);
        for(Handler handler:after.handlers())if(!oldHandlers.containsKey(handler.id())){Handler found=handlers.stream().filter(value->value.id()==handler.id()).findFirst().orElse(null);if(found!=null&&!found.equals(handler))return null;handlers.remove(found);}
        List<Long> beforeHandlers=before.handlers().stream().map(Handler::id).toList();
        Map<Long,Handler> newHandlers=new HashMap<>();for(Handler handler:after.handlers())newHandlers.put(handler.id(),handler);
        for(Handler handler:before.handlers()){
            Handler appliedHandler=newHandlers.get(handler.id());if(Objects.equals(handler,appliedHandler))continue;
            int at=-1;for(int i=0;i<handlers.size();i++)if(handlers.get(i).id()==handler.id()){at=i;break;}
            if(at>=0){
                Handler found=handlers.get(at);if(!found.equals(appliedHandler)&&!found.equals(handler))return null;
                // Exception-table order chooses the first applicable catch.
                // Replacing a changed target/type must retain its actual slot.
                handlers.set(at,handler);
            }else{
                // A retained handler removed by a later writer is an overlap,
                // not permission to resurrect that writer's deleted entry.
                if(appliedHandler!=null)return null;
                int insertion=restorePosition(handlers.stream().map(Handler::id).toList(),beforeHandlers,handler.id());
                if(insertion<0)return null;handlers.add(insertion,handler);
            }
        }
        if(!restoreOrder(draft,before.instructions().stream().map(Instruction::id).toList(),after.instructions().stream().map(Instruction::id).toList(),Instruction::id)
                ||!restoreOrder(handlers,beforeHandlers,after.handlers().stream().map(Handler::id).toList(),Handler::id))return null;
        return new Body(current.method(),List.copyOf(draft),List.copyOf(handlers));
    }
    /** Reverse only the observed permutation. Foreign insertions in its span
     * remain an overlap; no neighbor's placement is guessed or overwritten. */
    private static <T> boolean restoreOrder(List<T> values,List<Long> before,List<Long> after,java.util.function.ToLongFunction<T> identity) {
        Set<Long> retained=new HashSet<>(before);retained.retainAll(after);
        List<Long> original=before.stream().filter(retained::contains).toList(),applied=after.stream().filter(retained::contains).toList();
        if(original.equals(applied))return true;
        List<Long> live=new ArrayList<>();Map<Long,T> entries=new HashMap<>();List<Integer> slots=new ArrayList<>();
        for(int i=0;i<values.size();i++){T value=values.get(i);long id=identity.applyAsLong(value);if(retained.contains(id)){live.add(id);entries.put(id,value);slots.add(i);}}
        if(live.equals(original))return true;
        if(!live.equals(applied))return false;
        int first=0,last=original.size()-1;
        while(first<original.size()&&original.get(first).equals(applied.get(first)))first++;
        while(last>first&&original.get(last).equals(applied.get(last)))last--;
        if(slots.get(last)-slots.get(first)!=last-first)return false;
        for(int i=first;i<=last;i++)values.set(slots.get(i),entries.get(original.get(i)));
        return true;
    }
    /** The original neighboring identities must identify one unambiguous insertion gap. */
    private static int restorePosition(List<Long> current,List<Long> before,long identity){
        int original=before.indexOf(identity),left=-1,right=current.size();
        for(int i=original-1;i>=0;i--){int at=current.indexOf(before.get(i));if(at>=0){left=at;break;}}
        for(int i=original+1;i<before.size();i++){int at=current.indexOf(before.get(i));if(at>=0){right=at;break;}}
        return right==left+1?right:-1;
    }
    private static Map<Long,Instruction> index(List<Instruction> values){Map<Long,Instruction> result=new HashMap<>();for(Instruction value:values)result.put(value.id(),value);return result;}
    private static int find(List<Instruction> values,long id){for(int i=0;i<values.size();i++)if(values.get(i).id()==id)return i;return -1;}
    private static boolean install(MethodNode destination,Body body){
        Map<Long,LabelNode> locations=new HashMap<>();locations.put(0L,new LabelNode());for(Instruction instruction:body.instructions())locations.put(instruction.id(),new LabelNode());
        InsnList code=new InsnList();for(Instruction instruction:body.instructions()){
            Map<LabelNode,LabelNode> labels=new IdentityHashMap<>();for(var entry:instruction.labels().entrySet()){LabelNode target=locations.get(entry.getValue());if(target!=null)labels.put(entry.getKey(),target);}
            AbstractInsnNode copied;
            try{copied=instruction.code().clone(labels);}catch(RuntimeException failure){return false;}
            if(copied instanceof JumpInsnNode jump&&jump.label==null||copied instanceof TableSwitchInsnNode table&&(table.dflt==null||table.labels.contains(null))
                    ||copied instanceof LookupSwitchInsnNode lookup&&(lookup.dflt==null||lookup.labels.contains(null)))return false;
            code.add(locations.get(instruction.id()));code.add(copied);
        }
        code.add(locations.get(0L));List<TryCatchBlockNode> handlers=new ArrayList<>();for(Handler handler:body.handlers()){
            LabelNode start=locations.get(handler.start()),end=locations.get(handler.end()),target=locations.get(handler.handler());if(start==null||end==null||target==null)return false;
            handlers.add(new TryCatchBlockNode(start,end,target,handler.type()));
        }
        destination.instructions=code;destination.tryCatchBlocks=handlers;destination.localVariables=null;destination.visibleLocalVariableAnnotations=null;destination.invisibleLocalVariableAnnotations=null;return true;
    }
    static Class<?>[] affected(Class<?>[] classes,Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(modules));Set<Class<?>> result=Collections.newSetFromMap(new IdentityHashMap<>());
        List<Encoded> images=images();
        for(Class<?> type:classes)for(Encoded image:images)if(image.bootstrap()==(type.getClassLoader()==null)&&image.loader().get()==type.getClassLoader()&&image.name().equals(type.getName().replace('.','/'))
                &&image.patches().stream().anyMatch(patch->Arrays.stream(patch.owners()).anyMatch(selected::contains))){result.add(type);break;}
        result.addAll(Arrays.asList(ExternalCodeDefinitions.affected(classes,modules)));return result.toArray(Class<?>[]::new);
    }
    static void failed(Module[] modules,String detail){for(Module module:modules)gap(module,detail);}
    static synchronized void mixinDependency(Object tree,Module origin,Module[] contributors){
        String target;try{target=String.valueOf(get(tree,"name"));}catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_MIXIN_TARGET_UNOBSERVED",failure);}
        for(Module contributor:contributors)if(contributor!=origin&&ModGroupBoundary.stopped(contributor))gap(contributor,target+":FOREIGN_MIXIN_PAYLOAD_REPLAY_PENDING");
    }
    private static void gap(Module module,String detail){
        List<String> failures=DISPOSITION_FAILURES.get();if(failures!=null)failures.add(detail);if(module==null)return;
        synchronized(GAPS){GAPS.computeIfAbsent(module,ignored->new LinkedHashSet<>()).add(detail);}
        ModGroupBoundary.externalGap(module,detail);
    }
    private static byte[] canonical(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
    private static byte[] hash(byte[] bytes){return RecoveryAgent.externalCodeDigest(bytes);}
    private static void addChildren(ArrayDeque<Object> pending,Object... values){for(Object value:values)if(value!=null)pending.add(value);}
    /** Same public reference fields as IMAGE_FIELDS, for the supported JDK17 ASM tree. */
    private static boolean asmChildren(Object value,ArrayDeque<Object> pending){
        // These are the Agent's private parsed images, never the foreign ASM
        // trees used as transform inputs. Direct field loads avoid reflective
        // JNI/Unsafe observation for each edge; every visited object is still
        // protected below before the completed image is published.
        Class<?> type=value.getClass();
        if(type!=ClassNode.class&&type!=MethodNode.class&&type!=FieldNode.class&&type!=RecordComponentNode.class
                &&type!=LocalVariableAnnotationNode.class&&type!=TypeAnnotationNode.class&&type!=AnnotationNode.class
                &&type!=FieldInsnNode.class&&type!=MethodInsnNode.class&&type!=InvokeDynamicInsnNode.class&&type!=LdcInsnNode.class
                &&type!=TypeInsnNode.class&&type!=MultiANewArrayInsnNode.class&&type!=JumpInsnNode.class
                &&type!=LookupSwitchInsnNode.class&&type!=TableSwitchInsnNode.class&&type!=FrameNode.class&&type!=LineNumberNode.class
                &&type!=LocalVariableNode.class&&type!=TryCatchBlockNode.class&&type!=InnerClassNode.class&&type!=ParameterNode.class
                &&type!=ModuleNode.class&&type!=ModuleRequireNode.class&&type!=ModuleExportNode.class&&type!=ModuleOpenNode.class
                &&type!=ModuleProvideNode.class&&type!=Attribute.class&&type!=InsnNode.class&&type!=IntInsnNode.class
                &&type!=VarInsnNode.class&&type!=IincInsnNode.class&&type!=LabelNode.class)return false;
        if(value instanceof AbstractInsnNode node)addChildren(pending,node.visibleTypeAnnotations,node.invisibleTypeAnnotations);
        if(value instanceof ClassNode node){
            addChildren(pending,node.name,node.signature,node.superName,node.interfaces,node.sourceFile,node.sourceDebug,node.module,
                    node.outerClass,node.outerMethod,node.outerMethodDesc,node.visibleAnnotations,node.invisibleAnnotations,
                    node.visibleTypeAnnotations,node.invisibleTypeAnnotations,node.attrs,node.innerClasses,node.nestHostClass,
                    node.nestMembers,node.permittedSubclassesExperimental,node.recordComponents,node.fields,node.methods);
        }else if(value instanceof MethodNode node){
            addChildren(pending,node.name,node.desc,node.signature,node.exceptions,node.parameters,node.visibleAnnotations,node.invisibleAnnotations,
                    node.visibleTypeAnnotations,node.invisibleTypeAnnotations,node.attrs,node.annotationDefault,node.visibleParameterAnnotations,
                    node.invisibleParameterAnnotations,node.instructions,node.tryCatchBlocks,node.localVariables,
                    node.visibleLocalVariableAnnotations,node.invisibleLocalVariableAnnotations);
        }else if(value instanceof FieldNode node){
            addChildren(pending,node.name,node.desc,node.signature,node.value,node.visibleAnnotations,node.invisibleAnnotations,
                    node.visibleTypeAnnotations,node.invisibleTypeAnnotations,node.attrs);
        }else if(value instanceof RecordComponentNode node){
            addChildren(pending,node.name,node.descriptor,node.signature,node.visibleAnnotations,node.invisibleAnnotations,
                    node.visibleTypeAnnotations,node.invisibleTypeAnnotations,node.attrs);
        }else if(value instanceof LocalVariableAnnotationNode node){
            addChildren(pending,node.desc,node.values,node.typePath,node.start,node.end,node.index);
        }else if(value instanceof TypeAnnotationNode node)addChildren(pending,node.desc,node.values,node.typePath);
        else if(value instanceof AnnotationNode node)addChildren(pending,node.desc,node.values);
        else if(value instanceof FieldInsnNode node)addChildren(pending,node.owner,node.name,node.desc);
        else if(value instanceof MethodInsnNode node)addChildren(pending,node.owner,node.name,node.desc);
        else if(value instanceof InvokeDynamicInsnNode node)addChildren(pending,node.name,node.desc,node.bsm,node.bsmArgs);
        else if(value instanceof LdcInsnNode node)addChildren(pending,node.cst);
        else if(value instanceof TypeInsnNode node)addChildren(pending,node.desc);
        else if(value instanceof MultiANewArrayInsnNode node)addChildren(pending,node.desc);
        else if(value instanceof JumpInsnNode node)addChildren(pending,node.label);
        else if(value instanceof LookupSwitchInsnNode node)addChildren(pending,node.dflt,node.keys,node.labels);
        else if(value instanceof TableSwitchInsnNode node)addChildren(pending,node.dflt,node.labels);
        else if(value instanceof FrameNode node)addChildren(pending,node.local,node.stack);
        else if(value instanceof LineNumberNode node)addChildren(pending,node.start);
        else if(value instanceof LocalVariableNode node)addChildren(pending,node.name,node.desc,node.signature,node.start,node.end);
        else if(value instanceof TryCatchBlockNode node)addChildren(pending,node.start,node.end,node.handler,node.type,node.visibleTypeAnnotations,node.invisibleTypeAnnotations);
        else if(value instanceof InnerClassNode node)addChildren(pending,node.name,node.outerName,node.innerName);
        else if(value instanceof ParameterNode node)addChildren(pending,node.name);
        else if(value instanceof ModuleNode node)addChildren(pending,node.name,node.version,node.mainClass,node.packages,node.requires,node.exports,node.opens,node.uses,node.provides);
        else if(value instanceof ModuleRequireNode node)addChildren(pending,node.module,node.version);
        else if(value instanceof ModuleExportNode node)addChildren(pending,node.packaze,node.modules);
        else if(value instanceof ModuleOpenNode node)addChildren(pending,node.packaze,node.modules);
        else if(value instanceof ModuleProvideNode node)addChildren(pending,node.service,node.providers);
        else if(value instanceof Attribute attribute)addChildren(pending,attribute.type);
        else if(!(value instanceof InsnNode||value instanceof IntInsnNode||value instanceof VarInsnNode
                ||value instanceof IincInsnNode||value instanceof LabelNode))return false;
        return true;
    }
    private static void protect(Object... roots){
        Set<Object> found=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Object> pending=new ArrayDeque<>();for(Object root:roots)if(root!=null)pending.add(root);
        while(!pending.isEmpty()){
            Object[] holders=new Object[256];Field[][] fields=new Field[256][];int count=0;
            while(!pending.isEmpty()&&count<holders.length){
                Object value=pending.removeFirst();if(!found.add(value))continue;
                if(value instanceof Map<?,?> map){for(var entry:map.entrySet()){if(entry.getKey()!=null)pending.add(entry.getKey());if(entry.getValue()!=null)pending.add(entry.getValue());}}
                else if(value instanceof Collection<?> collection){for(Object entry:collection)if(entry!=null)pending.add(entry);}
                else if(value instanceof Object[] array){for(Object entry:array)if(entry!=null)pending.add(entry);}
                else if(value instanceof ExternalCodeFlow.ControlChange change){pending.add(change.alternatives());pending.add(change.owners());}
                else if(value.getClass().getName().startsWith("jdk.internal.org.objectweb.asm.")&&!asmChildren(value,pending)){
                    holders[count]=value;fields[count++]=IMAGE_FIELDS.get(value.getClass());
                }
                if(value instanceof AbstractInsnNode instruction){if(instruction.getNext()!=null)pending.add(instruction.getNext());if(instruction.getPrevious()!=null)pending.add(instruction.getPrevious());}
                if(value instanceof InsnList instructions)for(AbstractInsnNode instruction:instructions.toArray())pending.add(instruction);
            }
            if(count!=0)
                for(Object[] row:RecoveryAgent.imageControlFields(count==holders.length?holders:Arrays.copyOf(holders,count),
                        count==fields.length?fields:Arrays.copyOf(fields,count)))
                    for(Object child:row)if(child!=null)pending.add(child);
        }
        RecoveryAgent.protectExternalCode(found.toArray());
    }
}
