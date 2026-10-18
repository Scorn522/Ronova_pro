package dev.ronova.pro.agent;

import java.lang.ref.WeakReference;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

/** Binds emitted instruction images to actual VM classes before resolving a cross-class call. */
final class ExternalCodeDefinitions {
    private record Directory(List<Definition> definitions,List<Ref<Class<?>>> changes,List<Ref<Class<?>>> continuing,List<Refresh> active){}
    private static final java.util.concurrent.atomic.AtomicReference<Directory> DIRECTORY=
            new java.util.concurrent.atomic.AtomicReference<>(new Directory(List.of(),List.of(),List.of(),List.of()));
    private static final java.util.concurrent.atomic.AtomicReference<Thread> WORKER=new java.util.concurrent.atomic.AtomicReference<>();
    private static final Method LOADED=loadedMethod();
    private static final Method COMPARE=compareMethod();
    private static final ThreadLocal<BootstrapLookup> BOOTSTRAP_LOOKUP=new ThreadLocal<>();
    private static final ThreadLocal<Resolution> RESOLUTION=new ThreadLocal<>();
    private record SourceScope(Ref<ClassLoader> loader,boolean bootstrap,String name,Ref<Module> declaration,Set<String> methods){}
    private record SourceScopes(List<SourceScope> sources,Set<String> candidates,Set<String> methods){}
    private static volatile SourceScopes sourceScopes=new SourceScopes(List.of(),Set.of(),Set.of());
    /** Header/reference retention for misses, without an ASM reader or instruction graph. */
    record ReferenceHeader(String name,Map<String,Integer> declarations,int concreteMethods,Set<String> references,Set<String> calls){}
    private static final class Candidate {
        final Definition definition;
        final Candidate next;
        Candidate(Definition definition,Candidate next){this.definition=definition;this.next=next;}
    }
    private static final class DefinitionIndex {
        final Candidate[] named,hidden;
        DefinitionIndex(List<Definition> definitions){
            int capacity=16;while(capacity<definitions.size()&&capacity<1<<30)capacity<<=1;
            named=new Candidate[capacity];hidden=new Candidate[capacity];
            for(int position=0;position<definitions.size();position++){
                Definition definition=definitions.get(position);
                Class<?> actual=definition.hidden?definition.actual.get():null;
                if(definition.hidden&&actual==null)continue;
                Candidate[] table=definition.hidden?hidden:named;
                int hash=definition.hidden?System.identityHashCode(actual):definition.name.hashCode();
                int at=(hash^(hash>>>16))&(capacity-1);
                // Newer images are examined first, including bucket collisions.
                table[at]=new Candidate(definition,table[at]);
            }
            ControlImages.protect(this,named,hidden);
        }
        Candidate candidates(Class<?> actual){
            boolean isHidden=actual.isHidden();Candidate[] table=isHidden?hidden:named;
            int hash=isHidden?System.identityHashCode(actual):actual.getName().replace('.','/').hashCode();
            return table[(hash^(hash>>>16))&(table.length-1)];
        }
    }
    private static final class BootstrapClassIndex {
        final Class<?>[] classes;
        final int[] named,next;
        BootstrapClassIndex(Class<?>[] classes){
            int capacity=16;while(capacity<classes.length&&capacity<1<<30)capacity<<=1;
            this.classes=classes;named=new int[capacity];next=new int[classes.length];
            for(int i=0;i<classes.length;i++){
                Class<?> actual=classes[i];if(actual.getClassLoader()!=null||actual.isHidden())continue;
                int hash=actual.getName().hashCode(),at=(hash^(hash>>>16))&(capacity-1);
                next[i]=named[at];named[at]=i+1;
            }
            ControlImages.protect(this,classes,named,next);
        }
        Class<?> find(String name){
            int hash=name.hashCode(),at=(hash^(hash>>>16))&(named.length-1);
            for(int entry=named[at];entry!=0;entry=next[entry-1]){Class<?> actual=classes[entry-1];if(actual.getName().equals(name))return actual;}
            return null;
        }
    }
    private static final class BootstrapLookup {
        final List<Definition> definitions;
        final Map<MethodNode,ExternalReceiverFlow.Layout> receivers;
        final BootstrapLookup indexOwner;
        BootstrapClassIndex classes;
        DefinitionIndex index;
        BootstrapLookup(List<Definition> definitions){this(definitions,null);}
        BootstrapLookup(List<Definition> definitions,BootstrapLookup previous){
            this.definitions=definitions;
            this.receivers=previous==null?new IdentityHashMap<>():previous.receivers;
            this.indexOwner=previous==null?this:previous.indexOwner;
            ControlImages.protect(this,this.receivers);
        }
        Candidate candidates(Class<?> actual){
            // This index describes the same immutable definition list across
            // the batch. Actual class/bytecode bindings remain live per graph.
            if(indexOwner.index==null)indexOwner.index=new DefinitionIndex(definitions);
            return indexOwner.index.candidates(actual);
        }
        Class<?> find(String name){
            if(classes==null)classes=new BootstrapClassIndex(RecoveryAgent.bootstrapClasses());
            return classes.find(name);
        }
    }
    private record Refresh(Class<?> actual,Module[] owners){}
    private static final class Ref<T> extends WeakReference<T> {
        Ref(T value){super(value);}
        private static boolean writer(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames->frames
                .map(StackWalker.StackFrame::getDeclaringClass).filter(type->type!=Ref.class).findFirst()
                .map(type->type.getNestHost()==ExternalCodeDefinitions.class||type==RecoveryAgent.class).orElse(false));}
        public void clear(){if(writer())super.clear();}
        public boolean enqueue(){return writer()&&super.enqueue();}
    }
    private static final class Definition {
        final Ref<ClassLoader> loader;final Ref<Module> declaration;final boolean bootstrap,hidden,boundHidden;final String name;
        private volatile ExternalCodeFlow.Image analysis;
        final byte[] semanticBytes;
        final Map<String,Integer> declarations;final int declarationCount;
        final Map<String,Module[][]> applied;final Set<String> references,calls;final byte[] bytes;final Ref<byte[]> emitted;volatile Ref<Class<?>> actual;
        final Map<String,Module[]> fields;final Object[][] values;
        final String[] creationSites;
        final Object execution;
        final int concreteMethods;final boolean contributors,referenceOnly;
        Definition(ClassLoader loader,String name,Class<?> actual,Module declaration,ExternalCodeFlow.Image image,ExternalCodeFlow.Image analysis,byte[] semanticBytes,Map<String,Module[][]> applied,byte[] bytes,Map<String,Module[]> fields,Object[][] values,boolean hidden,String[] creationSites,Object execution){
            this(loader,name,actual,declaration,image,analysis,semanticBytes,applied,bytes,fields,values,hidden,creationSites,execution,null);
        }
        Definition(ClassLoader loader,String name,Class<?> actual,Module declaration,ExternalCodeFlow.Image image,ExternalCodeFlow.Image analysis,byte[] semanticBytes,Map<String,Module[][]> applied,byte[] bytes,Map<String,Module[]> fields,Object[][] values,boolean hidden,String[] creationSites,Object execution,ReferenceHeader reference){
            this.loader=new Ref<>(loader);this.declaration=new Ref<>(declaration);this.bootstrap=loader==null;this.hidden=hidden;this.boundHidden=hidden&&actual!=null;this.name=name;this.actual=new Ref<>(actual);this.analysis=analysis;this.applied=applied;this.emitted=new Ref<>(bytes);this.bytes=bytes.clone();
            this.semanticBytes=analysis==null?semanticBytes.clone():null;
            // The emitted tree is used only for method declarations. Retain
            // those exact headers, not a second complete protected instruction
            // graph beside the semantic graph used for source propagation.
            // Temporary String-keyed declaration tables carry no recovery
            // source. Do not route each header through the observed Map writer.
            Map<String,Integer> headers=new TreeMap<>();
            if(reference==null)for(MethodNode method:image.node().methods)headers.put(method.name+method.desc,method.access&0xffff);
            else headers.putAll(reference.declarations());
            this.declarations=Map.copyOf(headers);this.declarationCount=reference==null?image.node().methods.size():reference.declarations().size();
            Map<String,Module[]> declared=new LinkedHashMap<>();for(var field:fields.entrySet())declared.put(field.getKey(),field.getValue().clone());this.fields=Map.copyOf(declared);
            this.values=new Object[values.length][];for(int i=0;i<values.length;i++){this.values[i]=values[i].clone();this.values[i][3]=((Module[])values[i][3]).clone();}
            this.creationSites=creationSites.clone();
            this.execution=execution;
            this.concreteMethods=reference==null?(int)image.node().methods.stream().filter(method->(method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0).count():reference.concreteMethods();
            this.contributors=analysis!=null&&ExternalCodeFlow.hasContributors(analysis);
            this.referenceOnly=reference!=null;
            Set<String> referenced=new LinkedHashSet<>(),calls=new LinkedHashSet<>();
            if(reference!=null){referenced.addAll(reference.references());calls.addAll(reference.calls());}
            else if(analysis!=null){
                for(MethodNode method:analysis.node().methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call){
                    referenced.add(call.owner);
                    calls.add(call.owner+'#'+call.name+call.desc);
                }
            }else{
                // Constant-pool method references are a conservative superset
                // of the ordinary calls. No mutable ASM graph is retained for
                // a zero-seed definition until a real binding needs its body.
                var reader=new jdk.internal.org.objectweb.asm.ClassReader(this.semanticBytes);char[] buffer=new char[reader.getMaxStringLength()];
                for(int i=1;i<reader.getItemCount();i++){
                    int at=reader.getItem(i);if(at==0)continue;int tag=reader.readByte(at-1);if(tag!=10&&tag!=11)continue;
                    String owner=reader.readClass(at,buffer);referenced.add(owner);
                    int member=reader.getItem(reader.readUnsignedShort(at+2));calls.add(owner+'#'+reader.readUTF8(member,buffer)+reader.readUTF8(member+2,buffer));
                }
            }
            this.references=Set.copyOf(referenced);this.calls=Set.copyOf(calls);
            List<Object> controls=new ArrayList<>(Arrays.asList(DIRECTORY,WORKER,LOADED,COMPARE,this,this.loader,this.declaration,this.actual,this.bytes,this.semanticBytes,this.emitted,this.applied,this.references,this.calls,this.fields,this.values,this.creationSites,this.declarations));
            for(Module[][] rows:this.applied.values()){controls.add(rows);Collections.addAll(controls,rows);}
            for(Module[] owners:this.fields.values())controls.add(owners);for(Object[] row:this.values){controls.add(row);controls.add(row[3]);}
            ControlImages.protect(controls.toArray());
            ExternalCodeImages.protectDefinitionMetadata(this.declarations,this.references,this.calls);
            if(analysis!=null)ExternalCodeImages.protectExecution(analysis,this.declarations);
        }
        boolean loader(ClassLoader candidate){return bootstrap==(candidate==null)&&loader.get()==candidate;}
        ExternalCodeFlow.Image analysis(){
            ExternalCodeFlow.Image current=analysis;if(current!=null)return current;
            // Parsing/protection may load helper classes. Never hold a definition
            // monitor while doing it; only the completed immutable image is published.
            ExternalCodeFlow.Image prepared=ExternalCodeImages.acceptedExecution(semanticBytes,Map.of());
            ExternalCodeImages.protectExecution(prepared,declarations);
            synchronized(this){if(analysis==null)analysis=prepared;return analysis;}
        }
    }
    private record Bound(Definition definition,Class<?> actual,Set<String> methods,ExternalCodeFlow.Image graph){}
    private record Target(ExternalCodeFlow.Image image,String method){}
    // These keys run during native Unsafe installation. Record's generated
    // ObjectMethods uses handle getters, which would observe our own key reads.
    private record Member(Class<?> actual,int access,boolean unique){
        public boolean equals(Object other){return other instanceof Member key&&actual==key.actual&&access==key.access&&unique==key.unique;}
        public int hashCode(){return (Objects.hashCode(actual)*31+access)*31+Boolean.hashCode(unique);}
    }
    private record MemberQuery(String method,boolean selection){
        public boolean equals(Object other){return other instanceof MemberQuery key&&selection==key.selection&&Objects.equals(method,key.method);}
        public int hashCode(){return Objects.hashCode(method)*31+Boolean.hashCode(selection);}
    }
    private record SelectionQuery(String method,Member resolved){
        public boolean equals(Object other){return other instanceof SelectionQuery key&&Objects.equals(method,key.method)&&Objects.equals(resolved,key.resolved);}
        public int hashCode(){return Objects.hashCode(method)*31+Objects.hashCode(resolved);}
    }
    private record CallQuery(String owner,String name,String descriptor,int opcode,boolean contract,Set<ExternalReceiverFlow.Receiver> receivers){
        public boolean equals(Object other){return other instanceof CallQuery key&&opcode==key.opcode&&contract==key.contract
                &&Objects.equals(owner,key.owner)&&Objects.equals(name,key.name)&&Objects.equals(descriptor,key.descriptor)&&Objects.equals(receivers,key.receivers);}
        public int hashCode(){
            int hash=Objects.hashCode(owner);hash=hash*31+Objects.hashCode(name);hash=hash*31+Objects.hashCode(descriptor);
            hash=hash*31+opcode;hash=hash*31+Boolean.hashCode(contract);return hash*31+Objects.hashCode(receivers);
        }
    }
    /** Read indices live for exactly one expand call, including nested calls. */
    private static final class Resolution {
        final Map<ClassLoader,Map<String,Class<?>>> initiated=new IdentityHashMap<>();
        final Map<ClassNode,Map<String,MethodNode>> methods=new IdentityHashMap<>();
        final Map<Class<?>,Map<String,Integer>> declarations=new IdentityHashMap<>();
        final Map<Class<?>,Map<MemberQuery,Member>> members=new IdentityHashMap<>();
        final Map<Class<?>,Map<SelectionQuery,Member>> selections=new IdentityHashMap<>();
        final Map<Object,Map<CallQuery,Target>> calls=new IdentityHashMap<>();
        final Map<Class<?>,Map<String,Bound>> bodies=new IdentityHashMap<>();
    }
    private ExternalCodeDefinitions(){}
    private static Method loadedMethod(){
        try{Method method=ClassLoader.class.getDeclaredMethod("findLoadedClass",String.class);method.setAccessible(true);return method;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static Method compareMethod(){
        try{return Class.forName("dev.ronova.pro.bootstrap.NativeControl",false,null)
                .getMethod("compareDefinitionReference",java.util.concurrent.atomic.AtomicReference.class,Object.class,Object.class);}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static boolean compareDirectory(Directory expected,Directory next){return compare(DIRECTORY,expected,next);}
    private static boolean startWorker(Thread next){return compare(WORKER,null,next);}
    private static boolean compare(java.util.concurrent.atomic.AtomicReference<?> slot,Object expected,Object next){
        try{return Boolean.TRUE.equals(COMPARE.invoke(null,slot,expected,next));}
        catch(java.lang.reflect.InvocationTargetException failure){
            Throwable cause=failure.getCause();if(cause instanceof RuntimeException error)throw error;if(cause instanceof Error error)throw error;
            throw new IllegalStateException("EXTERNAL_DEFINITION_DIRECTORY_CAS",cause);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("EXTERNAL_DEFINITION_DIRECTORY_CAS",failure);}
    }
    static ReferenceHeader references(String expected,byte[] bytes){
        if(bytes==null)return null;
        try(var input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))){
            if(input.readInt()!=0xcafebabe)return null;
            input.readUnsignedShort();input.readUnsignedShort();int count=input.readUnsignedShort();
            String[] strings=new String[count];int[] classes=new int[count],owners=new int[count],members=new int[count],memberNames=new int[count],memberDescriptors=new int[count];
            for(int i=1;i<count;i++)switch(input.readUnsignedByte()){
                case 1->strings[i]=input.readUTF();
                case 7->classes[i]=input.readUnsignedShort();
                case 10,11->{owners[i]=input.readUnsignedShort();members[i]=input.readUnsignedShort();}
                case 12->{memberNames[i]=input.readUnsignedShort();memberDescriptors[i]=input.readUnsignedShort();}
                case 3,4,9,17,18->input.skipNBytes(4);
                case 5,6->{input.skipNBytes(8);i++;}
                case 8,16,19,20->input.skipNBytes(2);
                case 15->input.skipNBytes(3);
                default->{return null;}
            }
            input.readUnsignedShort();String name=strings[classes[input.readUnsignedShort()]];
            if(name==null||expected!=null&&!expected.equals(name))return null;
            input.readUnsignedShort();input.skipNBytes(2L*input.readUnsignedShort());
            int fields=input.readUnsignedShort();for(int i=0;i<fields;i++){input.skipNBytes(6);skipAttributes(input);}
            int methods=input.readUnsignedShort(),concrete=0;Map<String,Integer> declarations=new TreeMap<>();
            for(int i=0;i<methods;i++){
                int access=input.readUnsignedShort();String method=strings[input.readUnsignedShort()],descriptor=strings[input.readUnsignedShort()];
                if(method==null||descriptor==null||declarations.put(method+descriptor,access)!=null)return null;
                if((access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)concrete++;
                skipAttributes(input);
            }
            skipAttributes(input);if(input.available()!=0)return null;
            Set<String> references=new LinkedHashSet<>(),calls=new LinkedHashSet<>();
            for(int i=1;i<count;i++)if(owners[i]!=0){String reference=strings[classes[owners[i]]],method=strings[memberNames[members[i]]],descriptor=strings[memberDescriptors[members[i]]];if(reference==null||method==null||descriptor==null)return null;references.add(reference);calls.add(reference+'#'+method+descriptor);}
            return new ReferenceHeader(name,Map.copyOf(declarations),concrete,Set.copyOf(references),Set.copyOf(calls));
        }catch(java.io.IOException|IndexOutOfBoundsException malformed){return null;}
    }
    private static void skipAttributes(java.io.DataInputStream input)throws java.io.IOException{
        int count=input.readUnsignedShort();for(int i=0;i<count;i++){input.readUnsignedShort();input.skipNBytes(Integer.toUnsignedLong(input.readInt()));}
    }
    static void referenceOnly(ClassLoader loader,String name,Class<?> actual,Module declaration,byte[] bytes,byte[] semanticBytes,ReferenceHeader reference){
        if(reference==null||name==null||!name.equals(reference.name()))return;
        // The exact input and method-reference names remain available for a
        // later real producer binding. No emitted VM layout or source row is
        // claimed for this lightweight directory entry.
        Definition observed=new Definition(loader,name,actual,declaration,null,null,semanticBytes,Map.of(),bytes,Map.of(),new Object[0][],false,new String[0],null,reference);
        publish(observed,true);
    }
    private static void observeSource(ClassLoader loader,String name,Module declaration){
        for(;;){
            SourceScopes before=sourceScopes;List<SourceScope> sources=new ArrayList<>();boolean present=false;
            Set<String> contracts=name==null?Set.of():sourceContracts(loader,name);
            for(SourceScope source:before.sources()){
                if(!source.bootstrap()&&source.loader().get()==null||source.declaration().get()==null)continue;
                if(source.bootstrap()==(loader==null)&&source.loader().get()==loader&&source.name().equals(name)&&source.declaration().get()==declaration){
                    Set<String> merged=new HashSet<>(source.methods());merged.addAll(contracts);
                    sources.add(new SourceScope(source.loader(),source.bootstrap(),source.name(),source.declaration(),Set.copyOf(merged)));present=true;
                }else sources.add(source);
            }
            if(!present&&name!=null)sources.add(new SourceScope(new Ref<>(loader),loader==null,name,new Ref<>(declaration),contracts));
            Set<String> candidates=new HashSet<>(),methods=new HashSet<>();for(SourceScope source:sources){candidates.add(source.name());methods.addAll(source.methods());}
            List<Definition> definitions=definitions();boolean changed;
            do{
                changed=false;
                for(Definition definition:definitions)if(!candidates.contains(definition.name)&&(!Collections.disjoint(definition.references,candidates)||!Collections.disjoint(definition.calls,methods)))changed|=candidates.add(definition.name);
            }while(changed);
            if((present||name==null)&&sources.size()==before.sources().size()&&candidates.equals(before.candidates())&&methods.equals(before.methods()))return;
            SourceScopes next=new SourceScopes(List.copyOf(sources),Set.copyOf(candidates),Set.copyOf(methods));
            List<Object> controls=new ArrayList<>(Arrays.asList(next,next.sources(),next.candidates(),next.methods()));
            for(SourceScope source:sources){controls.add(source);controls.add(source.loader());controls.add(source.declaration());controls.add(source.methods());}
            ControlImages.protect(controls.toArray());
            synchronized(ExternalCodeDefinitions.class){if(sourceScopes!=before)continue;sourceScopes=next;return;}
        }
    }
    private static Set<String> sourceContracts(ClassLoader loader,String name){
        List<Definition> definitions=definitions();Definition source=null;
        for(int i=definitions.size()-1;i>=0;i--){Definition candidate=definitions.get(i);if(candidate.loader(loader)&&candidate.name.equals(name)){source=candidate;break;}}
        if(source==null)return Set.of();
        Set<String> names=new HashSet<>();names.add(name);Class<?> actual=source.actual.get();
        if(actual!=null){
            Set<Class<?>> visited=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Class<?>> pending=new ArrayDeque<>();pending.add(actual);
            while(!pending.isEmpty()){
                Class<?> type=pending.removeFirst();if(!visited.add(type))continue;names.add(type.getName().replace('.','/'));
                Class<?> parent=type.getSuperclass();if(parent!=null)pending.add(parent);Collections.addAll(pending,type.getInterfaces());
            }
        }
        Set<String> methods=new HashSet<>();
        for(String member:source.declarations.keySet())if(!member.startsWith("<")&&member.charAt(member.length()-1)!='V')
            for(String owner:names)methods.add(owner+'#'+member);
        return Set.copyOf(methods);
    }
    static boolean executionSourcesObserved(ClassLoader loader,String name){
        return name!=null&&ExternalCodeImages.recorded(loader,name);
    }
    static boolean executionSourcesObserved(Module declaration,ClassLoader loader,String name,ReferenceHeader reference){
        if(RecoveryAgent.producerModule(declaration)||executionSourcesObserved(loader,name))return true;
        return false;
    }
    static void moduleObserved(Module module){
        // Actual Module registration is the source root; no getter-call graph is expanded.
    }
    static void accepted(ClassLoader loader,String name,Class<?> actual,Module declaration,byte[] bytes,byte[] semanticBytes,Map<String,Module[][]> rows,ExternalCodeFlow.Image semantic,Map<String,Module[][]> applied,Map<String,Module[]> fields,Object[][] values,boolean hidden,String[] creationSites,Object execution){
        if(name==null)return;
        ClassNode header=new ClassNode();new jdk.internal.org.objectweb.asm.ClassReader(bytes).accept(header,
                jdk.internal.org.objectweb.asm.ClassReader.SKIP_CODE|jdk.internal.org.objectweb.asm.ClassReader.SKIP_DEBUG|jdk.internal.org.objectweb.asm.ClassReader.SKIP_FRAMES);
        ExternalCodeFlow.Image image=new ExternalCodeFlow.Image(new Object(),header,Map.of(),Map.of());
        if(!image.node().name.equals(name))throw new IllegalStateException("EXTERNAL_DEFINITION_IMAGE_NAME_CHANGED");
        if(semantic==null)semantic=image;
        if(!semantic.node().name.equals(name))throw new IllegalStateException("EXTERNAL_DEFINITION_SEMANTIC_IMAGE_CHANGED");
        ExternalCodeFlow.Image retained=ExternalCodeFlow.hasContributors(semantic)?semantic:null;
        Definition replacement=new Definition(loader,name,actual,declaration,image,retained,semanticBytes,applied,bytes,fields,values,hidden,creationSites,execution);
        publish(replacement,true);
    }
    private static Directory directory(List<Definition> definitions,List<Ref<Class<?>>> changes,List<Ref<Class<?>>> continuing,List<Refresh> active){
        Directory next=new Directory(definitions,changes,continuing,active);
        // Build and protect before publication. A registry monitor around this
        // callback would invert the heap-source lock during lazy class loading.
        ControlImages.protect(DIRECTORY,WORKER,COMPARE,next,definitions,changes,continuing,active);return next;
    }
    private static boolean publish(Definition replacement,boolean emitted){
        for(;;){
            Directory before=DIRECTORY.get();List<Definition> definitions=new ArrayList<>(before.definitions());
            if(!emitted&&definitions.stream().anyMatch(entry->entry.boundHidden&&entry.actual.get()==replacement.actual.get()))return false;
            // An emitted replacement is not yet a VM acceptance. Retain the last matching version.
            definitions.removeIf(entry->emitted&&entry.loader(replacement.loader.get())&&entry.name.equals(replacement.name)&&entry.hidden==replacement.hidden
                    &&(replacement.hidden?!entry.boundHidden&&entry.emitted.get()==replacement.emitted.get():ControlImages.sameBytes(entry.bytes,replacement.bytes))
                    ||!entry.bootstrap&&entry.loader.get()==null||entry.boundHidden&&entry.actual.get()==null
                    ||entry.hidden&&!entry.boundHidden&&entry.emitted.get()==null);
            definitions.add(replacement);
            Directory after=directory(List.copyOf(definitions),before.changes(),before.continuing(),before.active());
            if(compareDirectory(before,after)){
                return true;
            }
        }
    }
    static void defined(Class<?> actual,byte[] bytes){
        if(actual==null||!actual.isHidden())return;
        List<Definition> definitions=definitions();
        for(Definition definition:definitions)if(definition.boundHidden&&definition.actual.get()==actual)return;
        Definition emitted=null;
        for(int i=definitions.size()-1;i>=0;i--){Definition candidate=definitions.get(i);
            if(candidate.hidden&&!candidate.boundHidden&&candidate.emitted.get()==bytes&&candidate.loader(actual.getClassLoader())&&ControlImages.sameBytes(candidate.bytes,bytes)){emitted=candidate;break;}
        }
        if(emitted==null){ModGroupBoundary.externalGap(RecoveryAgent.logicalModule(actual),actual.getName()+":EXTERNAL_HIDDEN_DEFINITION_UNRESOLVED");return;}
        // One emitted image may define several distinct hidden classes. Give each class its own graph identity.
        ClassNode header=new ClassNode();
        new jdk.internal.org.objectweb.asm.ClassReader(emitted.bytes).accept(header,
                jdk.internal.org.objectweb.asm.ClassReader.SKIP_CODE|jdk.internal.org.objectweb.asm.ClassReader.SKIP_DEBUG|jdk.internal.org.objectweb.asm.ClassReader.SKIP_FRAMES);
        ExternalCodeFlow.Image image=new ExternalCodeFlow.Image(new Object(),header,Map.of(),Map.of());
        Definition accepted=new Definition(actual.getClassLoader(),emitted.name,actual,RecoveryAgent.logicalModule(actual),image,null,
                emitted.semanticBytes==null?emitted.bytes:emitted.semanticBytes,Map.of(),emitted.bytes,emitted.fields,emitted.values,true,emitted.creationSites,null);
        Map<String,Module[]> roots=ExternalCodeRuntime.methodRoots(emitted.analysis==null?Map.of():emitted.analysis.rows());
        RecoveryAgent.bindExternalDefinition(actual,bytes,roots,accepted.fields,accepted.values,accepted.creationSites);
        if(publish(accepted,false))changed(actual);
    }
    static void prepared(Class<?> actual){
        if(actual==null||actual.isHidden())return;
        String name=actual.getName().replace('.','/');
        boolean observed=false,source=RecoveryAgent.producerModule(RecoveryAgent.logicalModule(actual));
        for(Definition entry:definitions())if(!entry.hidden&&entry.loader(actual.getClassLoader())&&entry.name.equals(name)){
            Class<?> prior=entry.actual.get();if(prior!=null&&prior!=actual)continue;
            Ref<Class<?>> accepted=new Ref<>(actual);ControlImages.protect(accepted);entry.actual=accepted;observed=true;source|=entry.contributors;
        }
        if(observed&&executionSourcesObserved(actual.getClassLoader(),actual.getName().replace('.','/')))changed(actual);
    }
    static void changed(Class<?> actual){
        if(WORKER.get()==null)return;
        if(!eligible(actual))return;
        Ref<Class<?>> next=new Ref<>(actual);ControlImages.protect(next);
        for(;;){
            Directory before=DIRECTORY.get();
            if(before.changes().stream().anyMatch(pending->pending.get()==actual))return;
            List<Ref<Class<?>>> changes=new ArrayList<>(before.changes());changes.add(next);
            Directory after=directory(before.definitions(),List.copyOf(changes),before.continuing(),before.active());
            if(compareDirectory(before,after)){java.util.concurrent.locks.LockSupport.unpark(WORKER.get());return;}
        }
    }
    static void start(Instrumentation api){
        if(WORKER.get()!=null)return;
        Thread next=new Thread(null,()->run(api),"Ronova external code continuation",0,false);
        next.setDaemon(true);next.setContextClassLoader(null);ControlImages.protect(DIRECTORY,WORKER,next);
        if(!startWorker(next))return;
        for(Definition definition:definitions())changed(definition.actual.get());
        next.start();
    }
    private static boolean eligible(Class<?> actual){
        if(actual==null||actual.isArray()||actual.isPrimitive()||actual.getClassLoader()==null)return false;
        return actual.getClassLoader()!=RecoveryAgent.class.getClassLoader()||actual.getModule()!=RecoveryAgent.class.getModule()
                ||!Objects.equals(actual.getProtectionDomain().getCodeSource(),RecoveryAgent.class.getProtectionDomain().getCodeSource());
    }
    private static void run(Instrumentation api){
        long retryDelay=100_000_000L,retryAt=0;
        Set<Class<?>> reported=Collections.newSetFromMap(new IdentityHashMap<>());
        for(;;){
            Directory before=DIRECTORY.get();
            if(before.changes().isEmpty()){
                if(before.continuing().isEmpty())java.util.concurrent.locks.LockSupport.park(ExternalCodeDefinitions.class);
                else {
                    long remaining=retryAt-System.nanoTime();
                    if(remaining>0)java.util.concurrent.locks.LockSupport.parkNanos(ExternalCodeDefinitions.class,remaining);
                    else remaining=0;
                    if(remaining==0)before=DIRECTORY.get();
                    else {if(Thread.interrupted())return;continue;}
                }
                if(Thread.interrupted())return;
                if(before.changes().isEmpty()&&before.continuing().isEmpty())continue;
            }
            boolean discovery=!before.changes().isEmpty();
            List<Ref<Class<?>>> pending=new ArrayList<>(before.continuing());
            for(Ref<Class<?>> next:before.changes())if(pending.stream().noneMatch(prior->prior.get()==next.get()))pending.add(next);
            Directory taken=directory(before.definitions(),List.of(),List.copyOf(pending),before.active());
            if(!compareDirectory(before,taken))continue;
            Set<Class<?>> changes=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Ref<Class<?>> change:taken.continuing()){Class<?> actual=change.get();if(actual!=null)changes.add(actual);}
            Set<Class<?>> completed=Collections.newSetFromMap(new IdentityHashMap<>());
            List<Refresh> pendingJobs=taken.active();
            try{
                List<Refresh> jobs=refreshes(changes,completed);
                // An unavailable body may emit no new job. Its old source
                // owners still have an unresolved publication obligation.
                pendingJobs=mergeRefreshes(pendingJobs,jobs);
                work(pendingJobs,false,Set.of());
                for(Refresh job:jobs){
                    String gap=job.actual().getName()+":EXTERNAL_CALLER_REFRESH_PENDING";
                    for(Module owner:job.owners())ModGroupBoundary.externalGap(owner,gap);
                    try{
                        if(!api.isModifiableClass(job.actual()))throw new UnmodifiableClassException(job.actual().getName());
                        api.retransformClasses(job.actual());
                        if(!refreshed(job.actual()))throw new IllegalStateException("EXTERNAL_CALLER_FLOW_NOT_INSTALLED");
                        for(Module owner:job.owners())ModGroupBoundary.externalResolved(owner,gap);
                        completed.add(job.actual());reported.remove(job.actual());
                    }catch(UnmodifiableClassException|RuntimeException|LinkageError failure){
                        // A failed refresh retains its exact continuation and owner obligations.
                        if(reported.add(job.actual()))System.err.println("RONOVA_EXTERNAL_CALLER_REFRESH_FAILED:"+job.actual().getName()+":"+failure.getClass().getSimpleName());
                    }
                }
            }catch(RuntimeException|LinkageError failure){
                // The batch never published all of its pending jobs. Retain
                // its seed continuations as well as any prior owner ledger.
                completed.clear();
                for(Class<?> actual:changes){
                    Module owner=RecoveryAgent.logicalModule(actual);
                    if(RecoveryAgent.producerModule(owner))ModGroupBoundary.externalGap(owner,actual.getName()+":EXTERNAL_FLOW_CONTINUATION_PENDING");
                }
            }finally{
                for(Class<?> actual:completed)ModGroupBoundary.externalResolved(RecoveryAgent.logicalModule(actual),actual.getName()+":EXTERNAL_FLOW_CONTINUATION_PENDING");
                for(Refresh prior:taken.active())if(completed.contains(prior.actual()))
                    for(Module owner:prior.owners())ModGroupBoundary.externalResolved(owner,prior.actual().getName()+":EXTERNAL_CALLER_REFRESH_PENDING");
                work(pendingJobs,true,completed);reported.removeAll(completed);
            }
            if(DIRECTORY.get().continuing().isEmpty()){retryDelay=100_000_000L;retryAt=0;}
            else {
                if(discovery)retryDelay=100_000_000L;
                retryAt=System.nanoTime()+retryDelay;
                retryDelay=Math.min(5_000_000_000L,retryDelay*2);
            }
        }
    }
    private static List<Refresh> mergeRefreshes(List<Refresh> prior,List<Refresh> current){
        Map<Class<?>,Set<Module>> owners=new IdentityHashMap<>();List<Class<?>> order=new ArrayList<>();
        for(List<Refresh> batch:List.of(prior,current))for(Refresh job:batch){
            Set<Module> selected=owners.get(job.actual());
            if(selected==null){selected=Collections.newSetFromMap(new IdentityHashMap<>());owners.put(job.actual(),selected);order.add(job.actual());}
            Collections.addAll(selected,job.owners());
        }
        List<Refresh> combined=new ArrayList<>();
        for(Class<?> actual:order){Refresh job=new Refresh(actual,owners.get(actual).toArray(Module[]::new));ControlImages.protect(job,job.owners());combined.add(job);}
        return List.copyOf(combined);
    }
    private static void work(List<Refresh> jobs,boolean finished,Set<Class<?>> completed){
        for(;;){
            Directory before=DIRECTORY.get();
            List<Ref<Class<?>>> pending=before.continuing();
            if(finished)pending=pending.stream().filter(reference->reference.get()!=null&&!completed.contains(reference.get())).toList();
            List<Refresh> active=jobs.stream().filter(job->!completed.contains(job.actual())).toList();
            Directory after=directory(before.definitions(),before.changes(),pending,active);
            if(compareDirectory(before,after))return;
        }
    }
    private static List<Definition> definitions(){return DIRECTORY.get().definitions();}
    static ControlClassWriter.Shape frameShape(ClassLoader loader,String name){
        if(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass()!=ControlClassWriter.class)
            throw new SecurityException("ACTUAL_FRAME_WRITER_REQUIRED");
        ControlClassWriter.Shape selected=null;
        for(Definition definition:definitions())if(!definition.hidden&&definition.loader(loader)&&definition.name.equals(name)){
            var reader=new jdk.internal.org.objectweb.asm.ClassReader(definition.bytes);
            ControlClassWriter.Shape header=new ControlClassWriter.Shape(reader.getSuperName(),reader.getInterfaces(),(reader.getAccess()&Opcodes.ACC_INTERFACE)!=0);
            if(selected!=null&&(!Objects.equals(selected.parent(),header.parent())||selected.contract()!=header.contract()
                    ||!Arrays.equals(selected.interfaces(),header.interfaces())))return null;
            selected=header;
        }
        return selected;
    }
    private static List<Refresh> refreshes(Set<Class<?>> changes,Set<Class<?>> completed){
        List<Definition> definitions=definitions();
        Resolution outer=RESOLUTION.get();RESOLUTION.set(new Resolution());
        BootstrapLookup previous=BOOTSTRAP_LOOKUP.get();BOOTSTRAP_LOOKUP.set(new BootstrapLookup(definitions));
        try{return refreshes(changes,definitions,completed);}
        finally{
            if(previous==null)BOOTSTRAP_LOOKUP.remove();else BOOTSTRAP_LOOKUP.set(previous);
            if(outer==null)RESOLUTION.remove();else RESOLUTION.set(outer);
        }
    }
    private static List<Refresh> refreshes(Set<Class<?>> changes,List<Definition> definitions,Set<Class<?>> completed){
        Set<Class<?>> candidates=Collections.newSetFromMap(new IdentityHashMap<>());candidates.addAll(changes);
        Set<Module> batchOwners=Collections.newSetFromMap(new IdentityHashMap<>());Set<String> changedMethods=new HashSet<>();
        for(Class<?> changed:changes){
            Module declaration=RecoveryAgent.logicalModule(changed);if(RecoveryAgent.producerModule(declaration))batchOwners.add(declaration);
            String name=changed.getName().replace('.','/');
            for(SourceScope source:sourceScopes.sources())if(source.bootstrap()==(changed.getClassLoader()==null)&&source.loader().get()==changed.getClassLoader()&&source.name().equals(name))changedMethods.addAll(source.methods());
            for(Definition definition:definitions)if(definition.actual.get()==changed){
                addOwners(batchOwners,definition.applied);if(definition.contributors)addOwners(batchOwners,definition.analysis().rows());
                for(Module[] owners:definition.fields.values())Collections.addAll(batchOwners,owners);
                for(Object[] value:definition.values)Collections.addAll(batchOwners,(Module[])value[3]);
            }
        }
        // A real producer override may be invoked through its actual interface
        // or parent declaration. Match the method selector, not Object's common
        // constructor owner, before retaining those callers as candidates.
        if(!changedMethods.isEmpty())for(Definition definition:definitions){
            Class<?> actual=definition.actual.get();if(eligible(actual)&&!Collections.disjoint(definition.calls,changedMethods))candidates.add(actual);
        }
        // Invert the same actual initiated-class/hierarchy edges once for this
        // immutable batch. Repeated full directory scans made a long chain
        // quadratic, and restarted private loader lookups at every iteration.
        Map<Class<?>,Set<Class<?>>> readers=new IdentityHashMap<>();
        Map<Class<?>,Set<Class<?>>> ancestors=new IdentityHashMap<>();
        for(Definition definition:definitions){
            Class<?> actual=definition.actual.get();if(!eligible(actual))continue;
            for(String reference:definition.references){
                Class<?> owner=initiated(actual.getClassLoader(),reference);if(owner==null)continue;
                Set<Class<?>> hierarchy=ancestors.get(owner);
                if(hierarchy==null){
                    hierarchy=Collections.newSetFromMap(new IdentityHashMap<>());
                    ArrayDeque<Class<?>> pending=new ArrayDeque<>();pending.add(owner);
                    while(!pending.isEmpty()){
                        Class<?> member=pending.removeFirst();if(!hierarchy.add(member))continue;
                        Class<?> parent=member.getSuperclass();if(parent!=null)pending.add(parent);
                        Collections.addAll(pending,member.getInterfaces());
                    }
                    ancestors.put(owner,hierarchy);
                }
                for(Class<?> member:hierarchy)readers.computeIfAbsent(member,ignored->Collections.newSetFromMap(new IdentityHashMap<>())).add(actual);
            }
        }
        ArrayDeque<Class<?>> propagation=new ArrayDeque<>(candidates);
        while(!propagation.isEmpty())for(Class<?> actual:readers.getOrDefault(propagation.removeFirst(),Set.of()))
            if(candidates.add(actual))propagation.addLast(actual);
        for(;;){
            Directory before=DIRECTORY.get();List<Ref<Class<?>>> pending=new ArrayList<>(before.continuing());
            for(Class<?> actual:candidates){
                if(pending.stream().anyMatch(reference->reference.get()==actual))continue;
                Ref<Class<?>> next=new Ref<>(actual);ControlImages.protect(next);pending.add(next);
            }
            Directory after=directory(before.definitions(),before.changes(),List.copyOf(pending),before.active());
            if(compareDirectory(before,after))break;
        }
        List<Refresh> jobs=new ArrayList<>();Map<Class<?>,Bound> bound=new IdentityHashMap<>();Set<Class<?>> unavailable=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Class<?> actual:candidates){
            try{
            // A pre-source miss has no published Native image yet. Promote
            // this exact actual caller through the existing transform before
            // asking codeVersion to bind it, rather than treating null as clean.
            boolean promote=false;String actualName=actual.getName().replace('.','/');List<Definition> currentDefinitions=definitions();
            for(int i=currentDefinitions.size()-1;i>=0;i--){Definition definition=currentDefinitions.get(i);
                if(definition.actual.get()==actual&&definition.loader(actual.getClassLoader())&&definition.name.equals(actualName)){promote=definition.referenceOnly;break;}
            }
            if(promote){
                try{RecoveryAgent.promoteExternalDefinition(actual);}
                catch(UnmodifiableClassException|RuntimeException|LinkageError failure){
                    ModGroupBoundary.externalGap(RecoveryAgent.logicalModule(actual),actual.getName()+":EXTERNAL_REFERENCE_PROMOTION_PENDING");
                    jobs.add(pendingRefresh(actual,batchOwners));
                    continue;
                }
                BOOTSTRAP_LOOKUP.set(new BootstrapLookup(definitions()));bound.clear();unavailable.clear();
                ModGroupBoundary.externalResolved(RecoveryAgent.logicalModule(actual),actual.getName()+":EXTERNAL_REFERENCE_PROMOTION_PENDING");
            }
            Bound current=bind(actual,bound,unavailable);
            if(current==null){
                Module owner=RecoveryAgent.logicalModule(actual);
                if(RecoveryAgent.producerModule(owner))ModGroupBoundary.externalGap(owner,actual.getName()+":EXTERNAL_FLOW_DEFINITION_UNRESOLVED");
                jobs.add(pendingRefresh(actual,batchOwners));
                continue;
            }
            ModGroupBoundary.externalResolved(RecoveryAgent.logicalModule(actual),actual.getName()+":EXTERNAL_FLOW_DEFINITION_UNRESOLVED");
            Map<String,Module[][]> rows=expand(actual.getClassLoader(),actual,current.graph());
            if(sameRows(current.definition().applied,rows,actual)){completed.add(actual);continue;}
            Set<Module> owners=Collections.newSetFromMap(new IdentityHashMap<>());
            addOwners(owners,current.definition().applied);addOwners(owners,rows);
            Refresh job=new Refresh(actual,owners.toArray(Module[]::new));ControlImages.protect(job,job.owners());jobs.add(job);
            }catch(RuntimeException|LinkageError failure){
                // One unresolved helper must not discard the already-known
                // source batch when another candidate completed successfully.
                jobs.add(pendingRefresh(actual,batchOwners));
            }
        }
        return List.copyOf(jobs);
    }
    private static Refresh pendingRefresh(Class<?> actual,Set<Module> owners){
        Refresh job=new Refresh(actual,owners.toArray(Module[]::new));ControlImages.protect(job,job.owners());return job;
    }
    private static boolean related(Class<?> owner,Set<Class<?>> candidates){
        if(owner==null)return false;
        Set<Class<?>> visited=Collections.newSetFromMap(new IdentityHashMap<>());ArrayDeque<Class<?>> hierarchy=new ArrayDeque<>();hierarchy.add(owner);
        while(!hierarchy.isEmpty()){
            Class<?> current=hierarchy.removeFirst();if(!visited.add(current))continue;if(candidates.contains(current))return true;
            Class<?> parent=current.getSuperclass();if(parent!=null)hierarchy.addLast(parent);Collections.addAll(hierarchy,current.getInterfaces());
        }
        return false;
    }
    private static boolean refreshed(Class<?> actual){
        BootstrapLookup previous=BOOTSTRAP_LOOKUP.get();BOOTSTRAP_LOOKUP.set(new BootstrapLookup(definitions()));
        try{
            Bound current=bind(actual,new IdentityHashMap<>(),Collections.newSetFromMap(new IdentityHashMap<>()));
            return current!=null&&sameRows(current.definition().applied,expand(actual.getClassLoader(),actual,current.graph()),actual);
        }finally{if(previous==null)BOOTSTRAP_LOOKUP.remove();else BOOTSTRAP_LOOKUP.set(previous);}
    }
    private static void addOwners(Set<Module> owners,Map<String,Module[][]> rows){for(Module[][] method:rows.values())for(Module[] site:method)owners.addAll(Arrays.asList(site));}
    private static boolean sameRows(Map<String,Module[][]> first,Map<String,Module[][]> second,Class<?> actual){
        // Before a hidden class is defined, expansion cannot subtract its own
        // declaration. After binding, expandGraph does. That same Module is
        // already supplied by the live VM frame and ExecutionFlow.declaration;
        // its redundant recorded row does not require rewriting the class.
        Module declaration=RecoveryAgent.logicalModule(actual);
        if(!RecoveryAgent.producerModule(declaration))declaration=null;
        Set<String> methods=new LinkedHashSet<>();methods.addAll(first.keySet());methods.addAll(second.keySet());
        for(String method:methods){
            Module[][] left=first.get(method),right=second.get(method);
            boolean leftEmpty=externalRowsEmpty(left,declaration),rightEmpty=externalRowsEmpty(right,declaration);
            if(leftEmpty||rightEmpty){if(leftEmpty!=rightEmpty)return false;continue;}
            if(left.length!=right.length)return false;
            for(int i=0;i<left.length;i++){
                if(!sameExternalRow(left[i],right[i],declaration)||!sameExternalRow(right[i],left[i],declaration))return false;
            }
        }
        return true;
    }
    private static boolean externalRowsEmpty(Module[][] rows,Module declaration){
        if(rows!=null)for(Module[] row:rows)for(Module owner:row)if(declaration==null||owner!=declaration)return false;
        return true;
    }
    private static boolean sameExternalRow(Module[] first,Module[] second,Module declaration){
        for(Module owner:first){
            if(declaration!=null&&owner==declaration)continue;
            boolean present=false;for(Module candidate:second)if(candidate==owner){present=true;break;}
            if(!present)return false;
        }
        return true;
    }
    static String pending(Module module){
        Directory current=DIRECTORY.get();
        for(Refresh job:current.active())for(Module owner:job.owners())if(owner==module)return "EXTERNAL_CALLER_REFRESH_PENDING:"+job.actual().getName();
        for(Ref<Class<?>> change:current.continuing()){String gap=pending(change.get(),module,current.definitions());if(!gap.isEmpty())return gap;}
        for(Ref<Class<?>> change:current.changes()){String gap=pending(change.get(),module,current.definitions());if(!gap.isEmpty())return gap;}
        return "";
    }
    private static String pending(Class<?> actual,Module module,List<Definition> definitions){
        if(actual==null)return "";
        if(RecoveryAgent.logicalModule(actual)==module)return "EXTERNAL_DEFINITION_CONTINUATION_PENDING:"+actual.getName();
        for(Definition definition:definitions)if(definition.actual.get()==actual
                &&(containsOwner(definition.applied,module)||definition.contributors&&containsOwner(definition.analysis().rows(),module)))
            return "EXTERNAL_CALLER_REFRESH_PENDING:"+actual.getName();
        return "";
    }
    private static boolean containsOwner(Map<String,Module[][]> rows,Module module){
        for(Module[][] method:rows.values())for(Module[] site:method)for(Module owner:site)if(owner==module)return true;
        return false;
    }
    private static boolean executionSourcesObserved(ClassLoader loader,Class<?> actual,ExternalCodeFlow.Image root){
        if(executionSourcesObserved(loader,root.node().name)||actual!=null&&RecoveryAgent.producerModule(RecoveryAgent.logicalModule(actual)))return true;
        Set<String> candidates=sourceScopes.candidates(),methods=sourceScopes.methods();
        for(MethodNode method:root.node().methods)for(AbstractInsnNode instruction:method.instructions.toArray())
            if(instruction instanceof MethodInsnNode call&&(candidates.contains(call.owner)||methods.contains(call.owner+'#'+call.name+call.desc)))return true;
        return false;
    }
    static Map<String,Module[][]> expand(ClassLoader loader,Class<?> actual,ExternalCodeFlow.Image root){
        if(root==null)return Map.of();
        if(!ExternalCodeFlow.hasContributors(root)&&!executionSourcesObserved(loader,actual,root))return Map.of();
        // A graph resolves against one real bootstrap class snapshot. Enumerating
        // the entire VM for every descriptor stalls installation; a later graph
        // gets a fresh snapshot, including classes loaded since this analysis.
        BootstrapLookup previous=BOOTSTRAP_LOOKUP.get();
        Resolution outer=RESOLUTION.get(),resolution=new Resolution();
        BootstrapLookup lookup=new BootstrapLookup(previous==null?definitions():previous.definitions,previous);BOOTSTRAP_LOOKUP.set(lookup);
        RESOLUTION.set(resolution);
        // Resolve actual initiated classes, receiver contracts and accepted VM
        // bodies once. A separate selector-only "possible source" graph neither
        // establishes ownership nor supplies an effect, and made every ordinary
        // class load traverse the same entire definition directory twice.
        try{return expandGraph(loader,actual,root,resolution);}
        finally{
            if(outer==null)RESOLUTION.remove();else RESOLUTION.set(outer);
            if(previous==null)BOOTSTRAP_LOOKUP.remove();else BOOTSTRAP_LOOKUP.set(previous);
        }
    }
    private static Map<String,Module[][]> expandGraph(ClassLoader loader,Class<?> actual,ExternalCodeFlow.Image root,Resolution resolution){
        Map<Object,ExternalCodeFlow.Image> images=new IdentityHashMap<>();images.put(root.identity(),root);
        Map<Object,Set<String>> reachable=new IdentityHashMap<>();
        ArrayDeque<Target> pending=new ArrayDeque<>();
        for(MethodNode method:root.node().methods)if(root.rows().containsKey(method.name+method.desc))
            includeMethod(new Target(root,method.name+method.desc),images,reachable,pending);
        Map<Object,ClassLoader> loaders=new IdentityHashMap<>();loaders.put(root.identity(),loader);
        Map<Object,Class<?>> classes=new IdentityHashMap<>();if(actual!=null)classes.put(root.identity(),actual);
        Map<Class<?>,Bound> bound=new IdentityHashMap<>();Set<Class<?>> unavailable=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Class<?>,Map<String,Integer>> declarations=resolution.declarations;
        Map<Object,Map<AbstractInsnNode,Target>> calls=new IdentityHashMap<>();
        while(!pending.isEmpty()){
            Target selected=pending.removeFirst();ExternalCodeFlow.Image caller=selected.image();MethodNode method=declared(caller,selected.method());
            Map<MethodInsnNode,Set<ExternalReceiverFlow.Receiver>> receivers=null;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode call&&ExternalCodeFlow.returnsValue(call)){
                Target target=resolve(caller,call,root,loaders,classes,bound,unavailable,declarations,null);
                if(target==null&&(call.getOpcode()==Opcodes.INVOKEVIRTUAL||call.getOpcode()==Opcodes.INVOKEINTERFACE)){
                    // Static, special and final dispatch already have an exact
                    // target. Compute frames only when an actual receiver can
                    // resolve a remaining value-producing virtual call.
                    if(receivers==null)receivers=ExternalReceiverFlow.calls(loaders.get(caller.identity()),classes.get(caller.identity()),caller,method,BOOTSTRAP_LOOKUP.get().receivers);
                    Set<ExternalReceiverFlow.Receiver> exact=receivers.get(call);
                    if(exact!=null)target=resolve(caller,call,root,loaders,classes,bound,unavailable,declarations,exact);
                }
                if(target==null||!target.image().rows().containsKey(target.method()))continue;
                calls.computeIfAbsent(caller.identity(),ignored->new IdentityHashMap<>()).put(instruction,target);includeMethod(target,images,reachable,pending);
            }
        }
        // A callee's unrelated methods cannot affect this call's return. Keep the
        // complete declaration image for resolution, but analyze only reached bodies.
        List<ExternalCodeFlow.Image> graph=new ArrayList<>();graph.add(root);
        for(ExternalCodeFlow.Image image:images.values())if(image!=root){
            Map<String,Module[][]> rows=new LinkedHashMap<>();Map<String,List<ExternalCodeFlow.ControlChange>> comparisons=new LinkedHashMap<>();
            for(String method:reachable.get(image.identity())){
                rows.put(method,image.rows().get(method));
                if(image.comparisons().containsKey(method))comparisons.put(method,image.comparisons().get(method));
            }
            graph.add(new ExternalCodeFlow.Image(image.identity(),image.node(),Map.copyOf(rows),Map.copyOf(comparisons)));
        }
        Map<String,Module[][]> result=ExternalCodeFlow.expandGraph(root,List.copyOf(graph),(caller,call)->{
            Target target=calls.getOrDefault(caller.identity(),Map.of()).get(call);return target==null?null:new ExternalCodeFlow.MethodTarget(target.image().identity(),target.method());
        });
        // The declaring module is already observed by the real VM frame. Store external additions only.
        Module declaration=actual==null?null:RecoveryAgent.logicalModule(actual);
        if(declaration==null||!RecoveryAgent.producerModule(declaration))return result;
        Map<String,Module[][]> external=new LinkedHashMap<>();
        for(var entry:result.entrySet()){
            Module[][] rows=new Module[entry.getValue().length][];
            for(int i=0;i<rows.length;i++)rows[i]=Arrays.stream(entry.getValue()[i]).filter(owner->owner!=declaration).toArray(Module[]::new);
            if(Arrays.stream(rows).anyMatch(row->row.length!=0))external.put(entry.getKey(),rows);
        }
        return Map.copyOf(external);
    }
    private static void includeMethod(Target target,Map<Object,ExternalCodeFlow.Image> images,Map<Object,Set<String>> reachable,ArrayDeque<Target> pending){
        images.putIfAbsent(target.image().identity(),target.image());
        if(reachable.computeIfAbsent(target.image().identity(),ignored->new LinkedHashSet<>()).add(target.method()))pending.addLast(target);
    }
    static Class<?>[] affected(Class<?>[] actualClasses,Module[] modules){
        BootstrapLookup previous=BOOTSTRAP_LOOKUP.get();BOOTSTRAP_LOOKUP.set(new BootstrapLookup(definitions()));
        try{return affectedClasses(actualClasses,modules);}
        finally{if(previous==null)BOOTSTRAP_LOOKUP.remove();else BOOTSTRAP_LOOKUP.set(previous);}
    }
    private static Class<?>[] affectedClasses(Class<?>[] actualClasses,Module[] modules){
        Set<Module> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(Arrays.asList(modules));
        Set<Class<?>> affected=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Class<?>,Bound> bound=new IdentityHashMap<>();Set<Class<?>> unavailable=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Class<?> actual:actualClasses){
            if(actual.isArray()||actual.isPrimitive())continue;
            Bound current=bind(actual,bound,unavailable);if(current==null)continue;
            Map<String,Module[][]> rows=expand(actual.getClassLoader(),actual,current.graph());
            boolean found=false;for(Module[][] method:rows.values())for(Module[] site:method)for(Module owner:site)if(selected.contains(owner)){found=true;break;}
            if(found)affected.add(actual);
        }
        return affected.toArray(Class<?>[]::new);
    }
    private static Target resolve(ExternalCodeFlow.Image caller,MethodInsnNode call,ExternalCodeFlow.Image root,
            Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable,Map<Class<?>,Map<String,Integer>> declarations,Set<ExternalReceiverFlow.Receiver> receivers){
        Resolution resolution=RESOLUTION.get();
        if(resolution==null)return resolveCall(caller,call,root,loaders,classes,bound,unavailable,declarations,receivers);
        CallQuery query=new CallQuery(call.owner,call.name,call.desc,call.getOpcode(),call.itf,receivers);
        Map<CallQuery,Target> calls=resolution.calls.computeIfAbsent(caller.identity(),ignored->new HashMap<>());
        Target known=calls.get(query);if(known!=null)return known;
        Target target=resolveCall(caller,call,root,loaders,classes,bound,unavailable,declarations,receivers);
        // A missing initiated class may appear while a graph is being built.
        // Reuse only resolutions tied to a bound implementation or this root's
        // exact current instruction image.
        if(target!=null)calls.put(query,target);return target;
    }
    private static Target resolveCall(ExternalCodeFlow.Image caller,MethodInsnNode call,ExternalCodeFlow.Image root,
            Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable,Map<Class<?>,Map<String,Integer>> declarations,Set<ExternalReceiverFlow.Receiver> receivers){
        String key=call.name+call.desc;Class<?> calling=classes.get(caller.identity());boolean self=call.owner.equals(caller.node().name);
        if(self){
            if(call.itf!=((caller.node().access&Opcodes.ACC_INTERFACE)!=0))return null;
            MethodNode method=declared(caller,key);
            boolean exactReceiver=receivers!=null&&receivers.stream().allMatch(receiver->receiver.self()||calling!=null&&receiver.actual()==calling);
            boolean exact=method!=null&&(call.getOpcode()==Opcodes.INVOKESTATIC&&(method.access&Opcodes.ACC_STATIC)!=0
                    ||call.getOpcode()==Opcodes.INVOKESPECIAL&&(method.access&Opcodes.ACC_STATIC)==0
                    ||(call.getOpcode()==Opcodes.INVOKEVIRTUAL||call.getOpcode()==Opcodes.INVOKEINTERFACE)&&(method.access&Opcodes.ACC_STATIC)==0
                    &&((method.access&(Opcodes.ACC_FINAL|Opcodes.ACC_PRIVATE))!=0
                    ||exactReceiver||(calling==null?(caller.node().access&Opcodes.ACC_FINAL)!=0:Modifier.isFinal(calling.getModifiers()))));
            if(exact&&concrete(method)){
                if(caller==root)return new Target(caller,key);
                if(calling!=null)return target(new Member(calling,method.access,true),key,root,loaders,classes,bound,unavailable);
            }
        }
        // A hidden self reference cannot be replaced by an ordinary class with the same source name.
        if(self&&calling==null)return inherited(caller,call,root,loaders,classes,bound,unavailable,declarations,receivers!=null&&receivers.stream().allMatch(ExternalReceiverFlow.Receiver::self));
        Class<?> owner=self?calling:initiated(loaders.get(caller.identity()),call.owner);if(owner==null||call.itf!=owner.isInterface())return null;
        Member resolved=lookup(owner,key,false,root,classes,declarations);if(resolved==null)return null;
        if(call.getOpcode()==Opcodes.INVOKESPECIAL){
            if((resolved.access()&Opcodes.ACC_STATIC)!=0)return null;
            if(call.name.equals("<init>"))return !owner.isInterface()&&resolved.actual()==owner?target(resolved,key,root,loaders,classes,bound,unavailable):null;
            Class<?> parent=calling==null?caller.node().superName==null?null:initiated(loaders.get(caller.identity()),caller.node().superName):calling.getSuperclass();
            Class<?> start=!owner.isInterface()&&parent!=null&&owner.isAssignableFrom(parent)&&(caller.node().access&Opcodes.ACC_SUPER)!=0?parent:owner;
            Member selected=lookup(start,key,true,root,classes,declarations);
            return selected==null?null:target(selected,key,root,loaders,classes,bound,unavailable);
        }
        if(call.getOpcode()!=Opcodes.INVOKESTATIC&&call.getOpcode()!=Opcodes.INVOKEVIRTUAL&&call.getOpcode()!=Opcodes.INVOKEINTERFACE)return null;
        boolean statik=(resolved.access()&Opcodes.ACC_STATIC)!=0;
        boolean exact=call.getOpcode()==Opcodes.INVOKESTATIC?statik:!statik
                &&((resolved.access()&(Opcodes.ACC_FINAL|Opcodes.ACC_PRIVATE))!=0||Modifier.isFinal(owner.getModifiers()));
        if(exact)return target(resolved,key,root,loaders,classes,bound,unavailable);
        if(statik||receivers==null)return null;
        Target common=null;
        for(ExternalReceiverFlow.Receiver receiver:receivers){
            Target selected;
            if(receiver.self())selected=definedReceiver(caller,call,owner,resolved,root,loaders,classes,bound,unavailable,declarations);
            else {
                Class<?> actual=receiver.actual();if(actual==null||!owner.isAssignableFrom(actual))return null;
                Member method=select(actual,key,resolved,root,classes,declarations);
                if(method==null||call.getOpcode()==Opcodes.INVOKEINTERFACE&&(method.access()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PRIVATE))==0)return null;
                selected=target(method,key,root,loaders,classes,bound,unavailable);
            }
            if(selected==null)return null;
            if(common==null)common=selected;
            else if(common.image().identity()!=selected.image().identity()||!common.method().equals(selected.method()))return null;
        }
        return common;
    }
    private static Target definedReceiver(ExternalCodeFlow.Image caller,MethodInsnNode call,Class<?> owner,Member resolved,ExternalCodeFlow.Image root,
            Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable,Map<Class<?>,Map<String,Integer>> declarations){
        if((resolved.access()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))==0)return null;
        ClassLoader loader=loaders.get(caller.identity());Class<?> parent=caller.node().superName==null?null:initiated(loader,caller.node().superName);
        boolean compatible=parent!=null&&owner.isAssignableFrom(parent);
        for(String name:caller.node().interfaces){Class<?> contract=initiated(loader,name);if(contract!=null&&owner.isAssignableFrom(contract))compatible=true;}
        if(!compatible)return null;
        String key=call.name+call.desc;MethodNode method=declared(caller,key);
        if(method!=null&&(method.access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))==0){
            if(call.getOpcode()==Opcodes.INVOKEINTERFACE&&(method.access&Opcodes.ACC_PUBLIC)==0)return null;
            if(!concrete(method))return null;
            Class<?> calling=classes.get(caller.identity());
            return caller==root?new Target(caller,key):calling==null?null:target(new Member(calling,method.access,true),key,root,loaders,classes,bound,unavailable);
        }
        ArrayDeque<Class<?>> interfaces=new ArrayDeque<>();
        for(Class<?> actual=parent;actual!=null;actual=actual.getSuperclass()){
            int access=access(actual,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0&&(access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))==0){
                if(call.getOpcode()==Opcodes.INVOKEINTERFACE&&(access&Opcodes.ACC_PUBLIC)==0)return null;
                return target(new Member(actual,access,true),key,root,loaders,classes,bound,unavailable);
            }
            Collections.addAll(interfaces,actual.getInterfaces());
        }
        for(String name:caller.node().interfaces){Class<?> actual=initiated(loader,name);if(actual==null||!actual.isInterface())return null;interfaces.addLast(actual);}
        Member inherited=maximum(interfaces,key,true,root,classes,declarations);
        return inherited==null?null:target(inherited,key,root,loaders,classes,bound,unavailable);
    }
    private static boolean samePackage(Class<?> first,Class<?> second){return first.getClassLoader()==second.getClassLoader()&&first.getPackageName().equals(second.getPackageName());}
    private static Member select(Class<?> receiver,String key,Member resolved,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        Resolution resolution=RESOLUTION.get();if(resolution==null)return selectMember(receiver,key,resolved,root,classes,declarations);
        SelectionQuery query=new SelectionQuery(key,resolved);
        Map<SelectionQuery,Member> members=resolution.selections.computeIfAbsent(receiver,ignored->new HashMap<>());
        if(members.containsKey(query))return members.get(query);
        Member selected=selectMember(receiver,key,resolved,root,classes,declarations);members.put(query,selected);return selected;
    }
    private static Member selectMember(Class<?> receiver,String key,Member resolved,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        if((resolved.access()&Opcodes.ACC_PRIVATE)!=0)return resolved;
        List<Class<?>> hierarchy=new ArrayList<>();for(Class<?> actual=receiver;actual!=null;actual=actual.getSuperclass())hierarchy.add(actual);
        Collections.reverse(hierarchy);boolean active=resolved.actual().isInterface(),open=(resolved.access()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))!=0;
        Member selected=null;
        for(Class<?> actual:hierarchy){
            int access=access(actual,key,root,classes,declarations);if(access==-2)return null;
            if(actual==resolved.actual()){active=true;selected=resolved;continue;}
            if(!active||access<0||(access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))!=0)continue;
            if(open||samePackage(actual,resolved.actual())){
                selected=new Member(actual,access,true);open|=(access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))!=0;
            }
        }
        if(selected!=null)return selected;
        ArrayDeque<Class<?>> interfaces=new ArrayDeque<>();for(Class<?> actual:hierarchy)Collections.addAll(interfaces,actual.getInterfaces());
        return maximum(interfaces,key,true,root,classes,declarations);
    }
    private static Target inherited(ExternalCodeFlow.Image caller,MethodInsnNode call,ExternalCodeFlow.Image root,
            Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable,Map<Class<?>,Map<String,Integer>> declarations,boolean exactReceiver){
        String key=call.name+call.desc;int opcode=call.getOpcode();boolean contract=(caller.node().access&Opcodes.ACC_INTERFACE)!=0;
        if(declared(caller,key)!=null||call.name.equals("<init>")||call.name.equals("<clinit>"))return null;
        if(opcode==Opcodes.INVOKESTATIC&&contract||opcode==Opcodes.INVOKEINTERFACE
                ||opcode==Opcodes.INVOKEVIRTUAL&&!exactReceiver&&(caller.node().access&Opcodes.ACC_FINAL)==0)return null;
        if(opcode!=Opcodes.INVOKESTATIC&&opcode!=Opcodes.INVOKESPECIAL&&opcode!=Opcodes.INVOKEVIRTUAL)return null;
        ClassLoader loader=loaders.get(caller.identity());Class<?> parent=contract?null:caller.node().superName==null?null:initiated(loader,caller.node().superName);
        if(!contract&&caller.node().superName!=null&&parent==null)return null;
        ArrayDeque<Class<?>> interfaces=new ArrayDeque<>();
        for(Class<?> actual=parent;actual!=null;actual=actual.getSuperclass()){
            int access=access(actual,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0)return ((access&Opcodes.ACC_STATIC)!=0)==(opcode==Opcodes.INVOKESTATIC)
                    ?target(new Member(actual,access,true),key,root,loaders,classes,bound,unavailable):null;
            Collections.addAll(interfaces,actual.getInterfaces());
        }
        if(opcode==Opcodes.INVOKESTATIC)return null;
        if(contract){
            int access=access(Object.class,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0&&(access&Opcodes.ACC_PUBLIC)!=0&&(access&Opcodes.ACC_STATIC)==0)
                return target(new Member(Object.class,access,true),key,root,loaders,classes,bound,unavailable);
        }
        for(String name:caller.node().interfaces){Class<?> actual=initiated(loader,name);if(actual==null||!actual.isInterface())return null;interfaces.addLast(actual);}
        Member selected=maximum(interfaces,key,true,root,classes,declarations);
        return selected==null?null:target(selected,key,root,loaders,classes,bound,unavailable);
    }
    private static int access(Class<?> actual,String key,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        if(actual==classes.get(root.identity())){MethodNode method=declared(root,key);return method==null?-1:method.access;}
        if(!declarations.containsKey(actual)){
            String[] headers=RecoveryAgent.externalCodeDeclarations(actual);Map<String,Integer> methods=null;
            if(headers!=null){methods=new TreeMap<>();for(int i=0;i<headers.length;i+=3)methods.put(headers[i]+headers[i+1],Integer.parseUnsignedInt(headers[i+2],16));}
            declarations.put(actual,methods);
        }
        Map<String,Integer> methods=declarations.get(actual);return methods==null?-2:methods.getOrDefault(key,-1);
    }
    private static Member lookup(Class<?> owner,String key,boolean selection,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        Resolution resolution=RESOLUTION.get();if(resolution==null)return lookupMember(owner,key,selection,root,classes,declarations);
        MemberQuery query=new MemberQuery(key,selection);
        Map<MemberQuery,Member> members=resolution.members.computeIfAbsent(owner,ignored->new HashMap<>());
        if(members.containsKey(query))return members.get(query);
        Member selected=lookupMember(owner,key,selection,root,classes,declarations);members.put(query,selected);return selected;
    }
    private static Member lookupMember(Class<?> owner,String key,boolean selection,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        for(Class<?> actual=owner;actual!=null;actual=actual.getSuperclass()){
            int access=access(actual,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0&&(!selection||(access&Opcodes.ACC_STATIC)==0))return new Member(actual,access,true);
        }
        if(owner.isInterface()){
            int access=access(Object.class,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0&&(access&Opcodes.ACC_PUBLIC)!=0&&(access&Opcodes.ACC_STATIC)==0)return new Member(Object.class,access,true);
        }
        ArrayDeque<Class<?>> interfaces=new ArrayDeque<>();
        for(Class<?> actual=owner;actual!=null;actual=actual.getSuperclass())Collections.addAll(interfaces,actual.getInterfaces());
        return maximum(interfaces,key,selection,root,classes,declarations);
    }
    private static Member maximum(ArrayDeque<Class<?>> interfaces,String key,boolean selection,ExternalCodeFlow.Image root,Map<Object,Class<?>> classes,Map<Class<?>,Map<String,Integer>> declarations){
        Set<Class<?>> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        List<Member> methods=new ArrayList<>();
        while(!interfaces.isEmpty()){
            Class<?> actual=interfaces.removeFirst();if(!visited.add(actual))continue;Collections.addAll(interfaces,actual.getInterfaces());
            int access=access(actual,key,root,classes,declarations);if(access==-2)return null;
            if(access>=0&&(access&(Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))==0)methods.add(new Member(actual,access,true));
        }
        Member selected=null;boolean ambiguous=false;
        for(Member method:methods){
            boolean shadowed=false;
            for(Member child:methods)if(child.actual()!=method.actual()&&method.actual().isAssignableFrom(child.actual())){shadowed=true;break;}
            if(shadowed||(method.access()&Opcodes.ACC_ABSTRACT)!=0)continue;
            if(selected==null)selected=method;else ambiguous=true;
        }
        if(selected!=null&&!ambiguous)return selected;
        // Resolution may choose an arbitrary interface declaration; that does not identify a callable body.
        return selection||methods.isEmpty()?null:new Member(methods.get(0).actual(),methods.get(0).access(),false);
    }
    private static Target target(Member method,String key,ExternalCodeFlow.Image root,Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable){
        if(!method.unique()||(method.access()&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)return null;
        if(method.actual()==classes.get(root.identity()))return concrete(declared(root,key))?new Target(root,key):null;
        Bound selected=bindMethod(method.actual(),key,bound,unavailable);
        Integer access=selected==null?null:selected.definition().declarations.get(key);
        return selected!=null&&selected.methods().contains(key)&&access!=null&&(access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0?include(selected,key,loaders,classes):null;
    }
    private static Target include(Bound selected,String key,Map<Object,ClassLoader> loaders,Map<Object,Class<?>> classes){
        ExternalCodeFlow.Image image=selected.graph();loaders.put(image.identity(),selected.actual().getClassLoader());classes.put(image.identity(),selected.actual());return new Target(image,key);
    }
    private static MethodNode declared(ExternalCodeFlow.Image image,String key){
        Resolution resolution=RESOLUTION.get();
        if(resolution!=null){
            Map<String,MethodNode> methods=resolution.methods.get(image.node());
            if(methods==null){
                // Keep this lookup within the current resolution. Its String
                // keys need no observed HashMap mutation/source bookkeeping.
                methods=new TreeMap<>();for(MethodNode method:image.node().methods)methods.put(method.name+method.desc,method);
                resolution.methods.put(image.node(),methods);
            }
            return methods.get(key);
        }
        for(MethodNode method:image.node().methods)if((method.name+method.desc).equals(key))return method;return null;
    }
    private static boolean concrete(MethodNode method){return method!=null&&(method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0;}
    private static Bound bindMethod(Class<?> actual,String key,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable){
        Resolution resolution=RESOLUTION.get();if(resolution==null)return bind(actual,bound,unavailable);
        Map<String,Bound> bodies=resolution.bodies.computeIfAbsent(actual,ignored->new TreeMap<>());
        if(bodies.containsKey(key))return bodies.get(key);
        BootstrapLookup lookup=BOOTSTRAP_LOOKUP.get();if(lookup==null)lookup=new BootstrapLookup(definitions());
        String name=actual.getName().replace('.','/');Map<String,Integer> headers=resolution.declarations.get(actual);
        Bound result=null;
        for(Candidate selected=lookup.candidates(actual);selected!=null;selected=selected.next){
            Definition candidate=selected.definition;
            if(!candidate.loader(actual.getClassLoader())||candidate.hidden!=actual.isHidden()
                    ||(candidate.hidden?candidate.actual.get()!=actual:!candidate.name.equals(name)))continue;
            Class<?> prior=candidate.actual.get();if(prior!=null&&prior!=actual)continue;
            if(!candidate.declarations.containsKey(key)||headers!=null&&!sameDeclarations(candidate,headers))continue;
            String[] matched=RecoveryAgent.externalCodeMethodVersion(actual,candidate.bytes,key);
            if(matched==null||matched.length!=1||!key.equals(matched[0]))continue;
            // Rows are immutable candidate input. Only a reached and separately
            // matched body is admitted; unrelated rows never become reachable.
            result=makeBound(candidate,actual,Set.of(key),candidate.analysis().rows().keySet());break;
        }
        bodies.put(key,result);return result;
    }
    private static Bound bind(Class<?> actual,Map<Class<?>,Bound> bound,Set<Class<?>> unavailable){
        if(bound.containsKey(actual))return bound.get(actual);if(unavailable.contains(actual))return null;
        Definition definition=null;String[] matched=null;String name=actual.getName().replace('.','/');
        BootstrapLookup lookup=BOOTSTRAP_LOOKUP.get();if(lookup==null)lookup=new BootstrapLookup(definitions());
        Resolution resolution=RESOLUTION.get();Map<String,Integer> headers=resolution==null?null:resolution.declarations.get(actual);
        for(Candidate selected=lookup.candidates(actual);selected!=null;selected=selected.next){
            Definition candidate=selected.definition;if(!candidate.loader(actual.getClassLoader())||candidate.hidden!=actual.isHidden()
                    ||(candidate.hidden?candidate.actual.get()!=actual:!candidate.name.equals(name)))continue;
            Class<?> prior=candidate.actual.get();if(prior!=null&&prior!=actual)continue;
            // codeVersion0 rejects a declaration-shape mismatch before returning
            // any selectors. Reuse headers already read for this graph's member
            // lookup to omit those impossible candidates; a possible match still
            // requires the original native bytecode/constant/handler comparison.
            if(headers!=null&&!sameDeclarations(candidate,headers))continue;
            String[] selectors=RecoveryAgent.externalCodeVersion(actual,candidate.bytes);if(selectors==null)continue;
            if(definition==null||selectors.length>matched.length){definition=candidate;matched=selectors;}
            // A full current-class match includes the declaration shape. No
            // older candidate can exceed all of its concrete methods; ties
            // already prefer this newest image. This result is not retained
            // for a later graph or definition change.
            if(selectors.length==candidate.concreteMethods)break;
        }
        if(definition==null){unavailable.add(actual);return null;}
        Set<String> methods=Set.copyOf(Arrays.asList(matched));
        Bound result=makeBound(definition,actual,methods,methods);bound.put(actual,result);return result;
    }
    private static Bound makeBound(Definition definition,Class<?> actual,Set<String> methods,Set<String> rowKeys){
        Map<String,Module[][]> rows=new LinkedHashMap<>();
        Module declaration=RecoveryAgent.logicalModule(actual);
        boolean primary=RecoveryAgent.producerModule(declaration)&&!ModGroupBoundary.stopped(declaration);
        ExternalCodeFlow.Image analysis=definition.analysis();
        for(String key:rowKeys){
            Module[][] source=analysis.rows().get(key);if(source==null)continue;
            if(!primary){rows.put(key,source);continue;}
            Module[][] owned=new Module[source.length][];
            for(int i=0;i<source.length;i++){
                Set<Module> origins=Collections.newSetFromMap(new IdentityHashMap<>());origins.addAll(Arrays.asList(source[i]));origins.add(declaration);owned[i]=origins.toArray(Module[]::new);
            }
            rows.put(key,owned);
        }
        ExternalCodeFlow.Image graph=new ExternalCodeFlow.Image(analysis.identity(),analysis.node(),Map.copyOf(rows),analysis.comparisons());
        return new Bound(definition,actual,methods,graph);
    }
    private static boolean sameDeclarations(Definition definition,Map<String,Integer> actual){
        if(definition.declarationCount!=actual.size())return false;
        for(var method:definition.declarations.entrySet()){
            Integer access=actual.get(method.getKey());
            // ASM's pseudo access bits describe attributes, not the u2 method
            // flags used by the native image parser and GetMethodModifiers.
            if(!Objects.equals(access,method.getValue()))return false;
        }
        return true;
    }
    static Class<?> initiated(ClassLoader loader,String name){
        String binary=name.replace('/','.');
        if(loader!=null){
            Resolution resolution=RESOLUTION.get();Map<String,Class<?>> observed=resolution==null?null:resolution.initiated.get(loader);
            Class<?> known=observed==null?null:observed.get(binary);if(known!=null)return known;
            try{
                Class<?> actual=(Class<?>)LOADED.invoke(loader,binary);
                // An initiating loader cannot rebind an already initiated name
                // to another Class. Do not memoize misses or initiate new types.
                if(actual!=null&&resolution!=null){
                    if(observed==null){observed=new HashMap<>();resolution.initiated.put(loader,observed);}
                    observed.put(binary,actual);
                }
                return actual;
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("ACTUAL_INITIATED_CLASS_LOOKUP_FAILED",failure);}
        }
        BootstrapLookup lookup=BOOTSTRAP_LOOKUP.get();if(lookup!=null)return lookup.find(binary);
        for(Class<?> actual:RecoveryAgent.loadedClasses())if(actual.getClassLoader()==null&&actual.getName().equals(binary)&&!actual.isHidden())return actual;
        return null;
    }
}
