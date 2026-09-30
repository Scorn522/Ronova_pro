package dev.ronova.pro.agent;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Exact platform-image task boundaries. Unknown JDK/agent bytecode remains unsupported, never guessed. */
public final class JdkTaskBoundaryContract {
    private static final String HOOK="dev/ronova/pro/bootstrap/TaskBridge";
    private static final Set<String> COMPLETIONS=Set.of("UniApply","UniAccept","UniRun","UniWhenComplete","UniHandle",
            "UniExceptionally","UniComposeExceptionally","UniCompose","UniRelay","BiApply","BiAccept","BiRun",
            "BiRelay","OrApply","OrAccept","OrRun","CoCompletion","AsyncSupply","AsyncRun","AnyOf");
    private static final String CF="java/util/concurrent/CompletableFuture";
    private record InlineBoundary(String name,String descriptor,int source,int other,int callback,boolean isStatic) { }
    private static InlineBoundary unary(String name,String callback) {
        return new InlineBoundary(name,"(Ljava/util/concurrent/Executor;L"+callback+";)L"+CF+";",0,-1,2,false);
    }
    private static InlineBoundary binary(String name,String callback) {
        return new InlineBoundary(name,"(Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletionStage;L"+callback+";)L"+CF+";",0,2,3,false);
    }
    private static final List<InlineBoundary> INLINE=List.of(
        unary("uniApplyStage","java/util/function/Function"),unary("uniAcceptStage","java/util/function/Consumer"),
        unary("uniRunStage","java/lang/Runnable"),unary("uniWhenCompleteStage","java/util/function/BiConsumer"),
        unary("uniHandleStage","java/util/function/BiFunction"),unary("uniExceptionallyStage","java/util/function/Function"),
        unary("uniComposeExceptionallyStage","java/util/function/Function"),unary("uniComposeStage","java/util/function/Function"),
        binary("biApplyStage","java/util/function/BiFunction"),binary("biAcceptStage","java/util/function/BiConsumer"),
        binary("biRunStage","java/lang/Runnable"),binary("orApplyStage","java/util/function/Function"),
        binary("orAcceptStage","java/util/function/Consumer"),binary("orRunStage","java/lang/Runnable"),
        new InlineBoundary("uniCopyStage","(L"+CF+";)L"+CF+";",0,-1,-1,true),
        new InlineBoundary("uniAsMinimalStage","()L"+CF+"$MinimalStage;",0,-1,-1,false),
        new InlineBoundary("asyncSupplyStage","(Ljava/util/concurrent/Executor;Ljava/util/function/Supplier;)L"+CF+";",-1,-1,1,true),
        new InlineBoundary("asyncRunStage","(Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)L"+CF+";",-1,-1,1,true),
        new InlineBoundary("completedFuture","(Ljava/lang/Object;)L"+CF+";",0,-1,-1,true),
        new InlineBoundary("completedStage","(Ljava/lang/Object;)Ljava/util/concurrent/CompletionStage;",0,-1,-1,true),
        new InlineBoundary("allOf","([L"+CF+";)L"+CF+";",0,-1,-1,true),
        new InlineBoundary("anyOf","([L"+CF+";)L"+CF+";",0,-1,-1,true),
        new InlineBoundary("completeAsync","(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)L"+CF+";",0,-1,1,false),
        new InlineBoundary("orTimeout","(JLjava/util/concurrent/TimeUnit;)L"+CF+";",0,-1,-1,false),
        new InlineBoundary("completeOnTimeout","(Ljava/lang/Object;JLjava/util/concurrent/TimeUnit;)L"+CF+";",0,-1,1,false));
    private static final Set<String> REFERENCE_IMAGES=Set.of(
        "java/util/concurrent/CompletableFuture$Completion","java/util/concurrent/CompletableFuture$UniCompletion",
        "java/util/concurrent/CompletableFuture$BiCompletion","java/util/concurrent/CompletableFuture$MinimalStage",
        "java/util/concurrent/CompletableFuture$AltResult","java/util/concurrent/CompletableFuture$Timeout",
        "java/util/concurrent/CompletableFuture$DelayedCompleter","java/util/concurrent/CompletableFuture$TaskSubmitter",
        "java/util/concurrent/CompletableFuture$DelayedExecutor","java/util/concurrent/CompletableFuture$Canceller",
        "java/util/concurrent/ForkJoinPool$WorkQueue","java/util/concurrent/ForkJoinWorkerThread",
        "java/util/concurrent/ForkJoinTask$Aux",
        "java/util/concurrent/FutureTask$WaitNode","java/util/concurrent/CompletableFuture$Signaller",
        "java/util/concurrent/ForkJoinTask$AdaptedRunnable","java/util/concurrent/ForkJoinTask$AdaptedRunnableAction",
        "java/util/concurrent/ForkJoinTask$AdaptedCallable","java/util/concurrent/ForkJoinTask$RunnableExecuteAction",
        "java/util/concurrent/Executors$RunnableAdapter",
        "java/util/concurrent/ThreadPoolExecutor$Worker",
        "java/util/concurrent/ArrayBlockingQueue","java/util/concurrent/ScheduledThreadPoolExecutor$DelayedWorkQueue",
        "java/util/concurrent/locks/ReentrantLock","java/util/concurrent/locks/ReentrantLock$Sync",
        "java/util/concurrent/locks/ReentrantLock$FairSync","java/util/concurrent/locks/ReentrantLock$NonfairSync",
        "java/util/concurrent/locks/AbstractQueuedSynchronizer","java/util/concurrent/locks/AbstractOwnableSynchronizer",
        "java/util/concurrent/locks/AbstractQueuedSynchronizer$ConditionObject",
        "java/util/concurrent/LinkedBlockingQueue","java/util/concurrent/LinkedBlockingQueue$Node",
        "java/util/concurrent/atomic/AtomicInteger");
    private record Boundary(String method,String descriptor,boolean execution,int taskLocal) {}
    private static final Map<String,List<Boundary>> TARGETS=Map.of(
        "java/util/concurrent/FutureTask",List.of(new Boundary("run","()V",true,0),new Boundary("runAndReset","()Z",true,0)),
        "java/util/concurrent/ScheduledThreadPoolExecutor$ScheduledFutureTask",List.of(new Boundary("run","()V",true,0)),
        "java/util/concurrent/ForkJoinTask",List.of(new Boundary("doExec","()I",true,0),new Boundary("fork","()Ljava/util/concurrent/ForkJoinTask;",false,0)),
        "java/util/concurrent/ThreadPoolExecutor",List.of(new Boundary("execute","(Ljava/lang/Runnable;)V",false,1),
            new Boundary("runWorker","(Ljava/util/concurrent/ThreadPoolExecutor$Worker;)V",true,-1)),
        "java/util/concurrent/ScheduledThreadPoolExecutor",List.of(
            new Boundary("delayedExecute","(Ljava/util/concurrent/RunnableScheduledFuture;)V",false,1),
            new Boundary("reExecutePeriodic","(Ljava/util/concurrent/RunnableScheduledFuture;)V",false,1)),
        "java/util/concurrent/ForkJoinPool",List.of(new Boundary("externalSubmit","(Ljava/util/concurrent/ForkJoinTask;)Ljava/util/concurrent/ForkJoinTask;",false,1)),
        "java/util/concurrent/ThreadPoolExecutor$CallerRunsPolicy",List.of(
            new Boundary("rejectedExecution","(Ljava/lang/Runnable;Ljava/util/concurrent/ThreadPoolExecutor;)V",true,-1)));
    private record Images(byte[] original,byte[] instrumented,byte[] originalCanonical,byte[] instrumentedCanonical) {}
    private static final Map<String,Images> IMAGES=new ConcurrentHashMap<>();
    private static final Set<String> DIAGNOSED=ConcurrentHashMap.newKeySet();
    private static volatile boolean enabled;
    private JdkTaskBoundaryContract() {}
    public static Set<String> targets(){
        Set<String> all=new HashSet<>(TARGETS.keySet());
        all.add(CF);
        COMPLETIONS.forEach(name->all.add("java/util/concurrent/CompletableFuture$"+name));return Set.copyOf(all);
    }
    public static Set<String> certificationTargets() {
        Set<String> all=new HashSet<>(targets());all.addAll(REFERENCE_IMAGES);return Set.copyOf(all);
    }
    private static boolean completion(String name) {
        String prefix="java/util/concurrent/CompletableFuture$";
        return name.startsWith(prefix)&&COMPLETIONS.contains(name.substring(prefix.length()));
    }
    public static boolean registered(String name){return name.equals(CF)||TARGETS.containsKey(name)||REFERENCE_IMAGES.contains(name)||completion(name);}
    public static void enable(){
        enabled=true;
        // Optional reference adapters need both lock variants registered before
        // the first probe. An absent optional class does not disable task capture.
        for(String name:REFERENCE_IMAGES)try{Class.forName(name.replace('/','.'),false,null);}
        catch(ClassNotFoundException|LinkageError|SecurityException unsupported) { /* Query remains unavailable. */ }
    }
    public static boolean enabled(){return enabled;}
    public static String platformImageHash(String name) {
        if(!registered(name))throw new IllegalArgumentException("unregistered platform image");
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(images(name).originalCanonical));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static boolean verify(String name,byte[] bytes) {
        if(!registered(name)||bytes==null)return false;
        try {return Arrays.equals(images(name).instrumentedCanonical,canonical(bytes));}
        catch(RuntimeException|LinkageError unavailable){return false;}
    }
    public static byte[] transform(String name,byte[] bytes) {
        if(!registered(name)||bytes==null)return null;
        try {
            Images images=images(name);byte[] observed=canonical(bytes);
            if(Arrays.equals(images.instrumentedCanonical,observed))return null;
            if(Arrays.equals(images.originalCanonical,observed))return images.instrumented.clone();
            if(Boolean.getBoolean("ronova.pro.validation.jdkTasks")&&DIAGNOSED.add(name)) {
                try {
                    var directory=java.nio.file.Path.of("ronova","diagnostics");java.nio.file.Files.createDirectories(directory);
                    String prefix=name.replace('/','_');
                    java.nio.file.Files.write(directory.resolve(prefix+"-observed.class"),bytes);
                    java.nio.file.Files.write(directory.resolve(prefix+"-platform.class"),images.original);
                    java.nio.file.Files.write(directory.resolve(prefix+"-expected.class"),images.instrumented);
                } catch(java.io.IOException unavailable){System.err.println("RONOVA_TASK_IMAGE_DIAGNOSTIC_UNAVAILABLE "+unavailable);}
            }
            return null;
        } catch(RuntimeException|LinkageError unavailable){return null;}
    }
    private static Images images(String name){return IMAGES.computeIfAbsent(name,JdkTaskBoundaryContract::load);}
    private static Images load(String name) {
        try(InputStream in=ClassLoader.getPlatformClassLoader().getResourceAsStream(name+".class")) {
            if(in==null)throw new IllegalStateException("platform task image missing");
            byte[] original=in.readNBytes(1024*1024+1);if(original.length>1024*1024)throw new IllegalStateException("platform task image too large");
            var node=new ClassNode();new ClassReader(original).accept(node,ClassReader.EXPAND_FRAMES);
            if(!name.equals(node.name))throw new IllegalStateException("platform task image mismatch");
            // Observe original JVM reference locals before adding our own receipt/frame
            // temporaries. Instrumenting those synthetic ASTOREs recursively inflated every
            // CompletableFuture callback without observing any additional business reference.
            if(name.equals("java/util/concurrent/ForkJoinPool")||name.equals("java/util/concurrent/ForkJoinPool$WorkQueue")
                    ||name.equals(CF)||name.startsWith(CF+"$"))
                for(MethodNode method:node.methods)if(!method.name.startsWith("<")
                        &&(method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)instrumentHelperLocals(method);
            for(Boundary boundary:TARGETS.getOrDefault(name,List.of())) {
                var found=node.methods.stream().filter(m->m.name.equals(boundary.method)&&m.desc.equals(boundary.descriptor)).toList();
                if(found.size()!=1)throw new IllegalStateException("platform task ABI unavailable: "+boundary.method);
                MethodNode method=found.get(0);
                if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE|Opcodes.ACC_STATIC))!=0)
                    throw new IllegalStateException("platform task body unavailable");
                if(name.equals("java/util/concurrent/ThreadPoolExecutor")&&boundary.method.equals("runWorker"))
                    instrumentWorkerDispatch(method);
                else if(boundary.taskLocal==-1)instrumentRunnableCall(method);
                else if(boundary.execution) {
                    if(name.equals("java/util/concurrent/FutureTask")) {
                        instrumentBody(method,"java/util/concurrent/Callable","call");
                        instrumentDelegateCall(method,"java/util/concurrent/Callable","call","()Ljava/lang/Object;");
                    }
                    if(name.equals("java/util/concurrent/ForkJoinTask"))instrumentBody(method,"java/util/concurrent/ForkJoinTask","exec");
                    instrumentExecution(method);
                } else instrumentSubmission(method,boundary.taskLocal);
            }
            if(name.equals("java/util/concurrent/FutureTask"))instrumentCancellation(node);
            if(name.equals("java/util/concurrent/Executors$RunnableAdapter")) {
                MethodNode call=node.methods.stream().filter(m->m.name.equals("call")&&m.desc.equals("()Ljava/lang/Object;")).findFirst().orElseThrow();
                instrumentDelegateCall(call,"java/lang/Runnable","run","()V");
            }
            if(Set.of("java/util/concurrent/ForkJoinTask$AdaptedRunnable","java/util/concurrent/ForkJoinTask$AdaptedRunnableAction",
                    "java/util/concurrent/ForkJoinTask$RunnableExecuteAction","java/util/concurrent/ForkJoinTask$AdaptedCallable").contains(name)) {
                MethodNode exec=node.methods.stream().filter(m->m.name.equals("exec")&&m.desc.equals("()Z")).findFirst().orElseThrow();
                boolean callable=name.endsWith("$AdaptedCallable");
                instrumentDelegateCall(exec,callable?"java/util/concurrent/Callable":"java/lang/Runnable",callable?"call":"run",callable?"()Ljava/lang/Object;":"()V");
            }
            if(name.equals("java/util/concurrent/FutureTask"))instrumentWait(node,"awaitDone","(ZJ)I");
            if(name.equals("java/util/concurrent/ForkJoinTask"))instrumentWait(node,"awaitDone","(Ljava/util/concurrent/ForkJoinPool;ZZZJ)I");
            if(name.equals(CF)) {
                instrumentWait(node,"waitingGet","(Z)Ljava/lang/Object;");instrumentWait(node,"timedGet","(J)Ljava/lang/Object;");
            }
            if(name.equals("java/util/concurrent/ForkJoinPool$WorkQueue"))instrumentOwnerQueue(node);
            if(name.equals("java/util/concurrent/ForkJoinPool")) {
                MethodNode idle=node.methods.stream().filter(m->m.name.equals("awaitWork")&&m.desc.equals("(Ljava/util/concurrent/ForkJoinPool$WorkQueue;)I")).findFirst().orElseThrow();
                var parks=Arrays.stream(idle.instructions.toArray()).filter(i->i instanceof MethodInsnNode call
                        &&call.owner.equals("java/util/concurrent/locks/LockSupport")&&(call.name.equals("park")||call.name.equals("parkUntil"))).toList();
                if(parks.size()!=2)throw new IllegalStateException("owner idle service points unavailable");
                for(AbstractInsnNode park:parks)idle.instructions.insert(park,ownerQueueHook("java/util/concurrent/ForkJoinPool$WorkQueue",1));
            }
            if(Set.of("java/util/concurrent/FutureTask$WaitNode","java/util/concurrent/CompletableFuture$Signaller","java/util/concurrent/ForkJoinTask$Aux").contains(name)) {
                for(MethodNode method:node.methods)if(method.name.equals("<init>"))
                    for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN) {
                        var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"waitNode","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,hook);
                    }
            }
            if(name.equals(CF))for(InlineBoundary boundary:INLINE) {
                var found=node.methods.stream().filter(m->m.name.equals(boundary.name)&&m.desc.equals(boundary.descriptor)).toList();
                if(found.size()!=1)throw new IllegalStateException("platform CF stage ABI unavailable: "+boundary.name);
                MethodNode method=found.get(0);
                if(((method.access&Opcodes.ACC_STATIC)!=0)!=boundary.isStatic
                        ||(method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)
                    throw new IllegalStateException("platform CF stage body unavailable: "+boundary.name);
                instrumentInline(method,boundary);
            }
            if(completion(name)) {
                for(MethodNode method:node.methods) {
                    if(method.name.equals("<init>")) {
                        for(var instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN) {
                            var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"created","(Ljava/lang/Object;)V",false));
                            method.instructions.insertBefore(instruction,hook);
                        }
                    } else if((method.name.equals("tryFire")&&method.desc.equals("(I)Ljava/util/concurrent/CompletableFuture;"))
                            ||(method.name.equals("run")&&method.desc.equals("()V"))) {
                        instrumentExecution(method);
                        var mode=new InsnList();mode.add(new VarInsnNode(Opcodes.ALOAD,0));
                        if(method.name.equals("tryFire"))mode.add(new VarInsnNode(Opcodes.ILOAD,1));
                        else mode.add(new InsnNode(Opcodes.ICONST_1));
                        mode.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"completionMode","(Ljava/lang/Object;I)V",false));
                        method.instructions.insert(mode);
                    }
                }
            }
            TaskResultBoundary.instrument(node);
            var writer=new PlatformWriter();node.accept(writer);byte[] patched=writer.toByteArray();
            return new Images(original,patched,canonical(original),canonical(patched));
        } catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    private static void inlineArgument(InsnList code,int local) {
        if(local<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,local));
    }
    /** Actual reference-local stores are observed; all locals are cleared before the exit receipt. */
    private static void instrumentHelperLocals(MethodNode method) {
        var original=method.instructions.toArray();
        var references=new TreeSet<Integer>();
        int argument=(method.access&Opcodes.ACC_STATIC)==0?1:0;
        var parameters=new ArrayList<Integer>();
        for(Type type:Type.getArgumentTypes(method.desc)) {
            if(type.getSort()==Type.OBJECT||type.getSort()==Type.ARRAY) { parameters.add(argument);references.add(argument); }
            argument+=type.getSize();
        }
        for(AbstractInsnNode instruction:original)if(instruction instanceof VarInsnNode local&&local.getOpcode()==Opcodes.ASTORE)references.add(local.var);
        if(references.isEmpty())return;
        int token=method.maxLocals++,failure=method.maxLocals++;
        Type result=Type.getReturnType(method.desc);int returned=method.maxLocals;method.maxLocals+=result.getSize();
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var normal=new LabelNode();
        var entry=new InsnList();
        if((method.access&Opcodes.ACC_STATIC)==0)entry.add(new VarInsnNode(Opcodes.ALOAD,0));else entry.add(new InsnNode(Opcodes.ACONST_NULL));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"helperEnter","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(int local:parameters)entry.add(helperStore(token,local));
        for(AbstractInsnNode instruction:original) {
            if(instruction instanceof VarInsnNode local&&local.getOpcode()==Opcodes.ASTORE)
                method.instructions.insert(instruction,helperStore(token,local.var));
            else if(instruction instanceof VarInsnNode local&&local.getOpcode()>=Opcodes.ISTORE&&local.getOpcode()<=Opcodes.DSTORE) {
                var clear=new InsnList();
                if(references.contains(local.var))clear.add(helperClear(token,local.var));
                if((local.getOpcode()==Opcodes.LSTORE||local.getOpcode()==Opcodes.DSTORE)&&references.contains(local.var+1))clear.add(helperClear(token,local.var+1));
                method.instructions.insert(instruction,clear);
            }
            int op=instruction.getOpcode();
            if(op<Opcodes.IRETURN||op>Opcodes.RETURN)continue;
            var replacement=new InsnList();
            if(result.getSort()!=Type.VOID)replacement.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
            replacement.add(new JumpInsnNode(Opcodes.GOTO,normal));
            method.instructions.insertBefore(instruction,replacement);method.instructions.remove(instruction);
        }
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        helperEnd(method.instructions,token,references,-1);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(normal);
        helperEnd(method.instructions,token,references,result.getSort()==Type.OBJECT||result.getSort()==Type.ARRAY?returned:-1);
        if(result.getSort()!=Type.VOID)method.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        method.instructions.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:result.getOpcode(Opcodes.IRETURN)));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static InsnList helperStore(int token,int local) {
        var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,token));hook.add(new LdcInsnNode(local));
        hook.add(new VarInsnNode(Opcodes.ALOAD,local));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"helperLocal","(Ljava/lang/Object;ILjava/lang/Object;)V",false));return hook;
    }
    private static InsnList helperClear(int token,int local) {
        var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,token));hook.add(new LdcInsnNode(local));hook.add(new InsnNode(Opcodes.ACONST_NULL));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"helperLocal","(Ljava/lang/Object;ILjava/lang/Object;)V",false));return hook;
    }
    private static void helperEnd(InsnList code,int token,Set<Integer> references,int returned) {
        for(int local:references) { code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,local)); }
        code.add(new VarInsnNode(Opcodes.ALOAD,token));
        if(returned<0)code.add(new InsnNode(Opcodes.ACONST_NULL));else code.add(new VarInsnNode(Opcodes.ALOAD,returned));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"helperExit","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
    }
    private static void instrumentOwnerQueue(ClassNode node) {
        Map<String,String> points=Map.of("nextLocalTask","(I)Ljava/util/concurrent/ForkJoinTask;",
                "push","(Ljava/util/concurrent/ForkJoinTask;Ljava/util/concurrent/ForkJoinPool;)V",
                "topLevelExec","(Ljava/util/concurrent/ForkJoinTask;Ljava/util/concurrent/ForkJoinPool$WorkQueue;)V");
        for(var point:points.entrySet()) {
            var found=node.methods.stream().filter(m->m.name.equals(point.getKey())&&m.desc.equals(point.getValue())).toList();
            if(found.size()!=1)throw new IllegalStateException("queue owner safe point unavailable: "+point.getKey());
            MethodNode method=found.get(0);
            if(point.getKey().equals("nextLocalTask"))method.instructions.insert(ownerQueueHook(node.name));
            else for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN)
                method.instructions.insertBefore(instruction,ownerQueueHook(node.name));
        }
    }
    private static InsnList ownerQueueHook(String owner) {
        return ownerQueueHook(owner,0);
    }
    private static InsnList ownerQueueHook(String owner,int local) {
        var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,local));hook.add(new VarInsnNode(Opcodes.ALOAD,local));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD,owner,"owner","Ljava/util/concurrent/ForkJoinWorkerThread;"));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"serviceOwnedQueue","(Ljava/lang/Object;Ljava/lang/Thread;)V",false));
        return hook;
    }
    /** Observe the real stage call and its real return value; preserve the platform body and all exception handlers. */
    private static void instrumentInline(MethodNode method,InlineBoundary boundary) {
        int token=method.maxLocals++,returned=method.maxLocals++,failure=method.maxLocals++;
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var normal=new LabelNode();
        var entry=new InsnList();inlineArgument(entry,boundary.source);inlineArgument(entry,boundary.other);inlineArgument(entry,boundary.callback);
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"beginInline",
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN) {
            var replacement=new InsnList();replacement.add(new VarInsnNode(Opcodes.ASTORE,returned));
            replacement.add(new JumpInsnNode(Opcodes.GOTO,normal));
            method.instructions.insertBefore(instruction,replacement);method.instructions.remove(instruction);
        }
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"endInline","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(normal);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,token));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,returned));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"endInline","(Ljava/lang/Object;Ljava/lang/Object;)V",false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,returned));method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void instrumentSubmission(MethodNode method,int local) {
        int token=method.maxLocals++,failure=method.maxLocals++;
        Type result=Type.getReturnType(method.desc);int returned=method.maxLocals;method.maxLocals+=result.getSize();
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var normal=new LabelNode();
        var entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,local));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"beginSubmission","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));entry.add(start);
        for(AbstractInsnNode instruction:method.instructions.toArray()) {
            int op=instruction.getOpcode();if(op<Opcodes.IRETURN||op>Opcodes.RETURN)continue;
            var replacement=new InsnList();
            if(result.getSort()!=Type.VOID)replacement.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
            replacement.add(new JumpInsnNode(Opcodes.GOTO,normal));
            method.instructions.insertBefore(instruction,replacement);method.instructions.remove(instruction);
        }
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));appendSubmissionExit(method.instructions,token);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(normal);appendSubmissionExit(method.instructions,token);
        if(result.getSort()!=Type.VOID)method.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        method.instructions.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:result.getOpcode(Opcodes.IRETURN)));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void appendSubmissionExit(InsnList code,int token) {
        code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"endSubmission","(Ljava/lang/Object;)V",false));
    }
    private static void instrumentCancellation(ClassNode node) {
        var cancel=node.methods.stream().filter(method->method.name.equals("cancel")&&method.desc.equals("(Z)Z")).findFirst().orElseThrow();
        var calls=Arrays.stream(cancel.instructions.toArray()).filter(instruction->instruction instanceof MethodInsnNode call
            &&call.owner.equals("java/lang/invoke/VarHandle")&&call.name.equals("compareAndSet")
            &&call.desc.equals("(Ljava/util/concurrent/FutureTask;II)Z")).toList();
        if(calls.size()!=1)throw new IllegalStateException("platform Future cancellation CAS unavailable");
        cancel.instructions.set(calls.get(0),new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"cancelState",
            "(Ljava/lang/invoke/VarHandle;Ljava/lang/Object;II)Z",false));
    }
    private static void instrumentBody(MethodNode method,String owner,String name) {
        var calls=Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode call
                &&call.owner.equals(owner)&&call.name.equals(name)).toList();
        if(calls.size()!=1)throw new IllegalStateException("actual task invocation unavailable");
        var hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"requireInvocation","(Ljava/lang/Object;)V",false));
        hook.add(new VarInsnNode(Opcodes.ALOAD,0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"body","(Ljava/lang/Object;)V",false));
        method.instructions.insertBefore(calls.get(0),hook);
    }
    private static void instrumentDelegateCall(MethodNode method,String owner,String name,String descriptor) {
        var calls=Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor)).toList();
        if(calls.size()!=1)throw new IllegalStateException("actual delegate call unavailable");
        AbstractInsnNode call=calls.get(0);int token=method.maxLocals++,failure=method.maxLocals++,value=method.maxLocals++;
        boolean returnsValue=!descriptor.endsWith("V");
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var done=new LabelNode();var execute=new LabelNode();
        var before=new InsnList();before.add(new InsnNode(Opcodes.DUP));before.add(new VarInsnNode(Opcodes.ALOAD,0));before.add(new InsnNode(Opcodes.SWAP));
        before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"enterDelegate","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        before.add(new VarInsnNode(Opcodes.ASTORE,token));before.add(start);before.add(new VarInsnNode(Opcodes.ALOAD,token));
        before.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"blocked","(Ljava/lang/Object;)Z",false));before.add(new JumpInsnNode(Opcodes.IFEQ,execute));
        before.add(new TypeInsnNode(Opcodes.NEW,"java/util/concurrent/CancellationException"));before.add(new InsnNode(Opcodes.DUP));
        before.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/util/concurrent/CancellationException","<init>","()V",false));before.add(new InsnNode(Opcodes.ATHROW));before.add(execute);
        method.instructions.insertBefore(call,before);
        var after=new InsnList();after.add(end);if(returnsValue)after.add(new VarInsnNode(Opcodes.ASTORE,value));appendExit(after,token);
        if(returnsValue)after.add(new VarInsnNode(Opcodes.ALOAD,value));after.add(new JumpInsnNode(Opcodes.GOTO,done));
        after.add(handler);after.add(new VarInsnNode(Opcodes.ASTORE,failure));appendExit(after,token);
        after.add(new VarInsnNode(Opcodes.ALOAD,failure));after.add(new InsnNode(Opcodes.ATHROW));after.add(done);method.instructions.insert(call,after);
        method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    /** Observe the complete per-task dispatch, including beforeExecute and afterExecute.
     * Existing platform instructions and exception handlers are preserved. Neither waiting
     * for getTask nor pool-wide retirement belongs to the preceding task. This interval is
     * not a queue-reference/resource-release proof and says nothing about whether the body ran. */
    private static void instrumentWorkerDispatch(MethodNode method) {
        var calls=Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode)
            .map(i->(MethodInsnNode)i).toList();
        var runs=calls.stream().filter(c->c.getOpcode()==Opcodes.INVOKEINTERFACE&&c.owner.equals("java/lang/Runnable")
            &&c.name.equals("run")&&c.desc.equals("()V")).toList();
        var locks=calls.stream().filter(c->workerCall(c,"lock")).toList();
        var before=calls.stream().filter(c->poolCall(c,"beforeExecute","(Ljava/lang/Thread;Ljava/lang/Runnable;)V")).toList();
        var after=calls.stream().filter(c->poolCall(c,"afterExecute","(Ljava/lang/Runnable;Ljava/lang/Throwable;)V")).toList();
        var retire=calls.stream().filter(c->poolCall(c,"processWorkerExit","(Ljava/util/concurrent/ThreadPoolExecutor$Worker;Z)V")).toList();
        if(runs.size()!=1||locks.size()!=1||before.size()!=1||after.size()!=2||retire.size()!=2)
            throw new IllegalStateException("platform worker dispatch shape unavailable");
        var runReceiver=previousCode(runs.get(0));var lockReceiver=previousCode(locks.get(0));
        var beforeReceiver=previousCode(before.get(0));
        if(!(runReceiver instanceof VarInsnNode task)||task.getOpcode()!=Opcodes.ALOAD||task.var!=3
            ||!(lockReceiver instanceof VarInsnNode worker)||worker.getOpcode()!=Opcodes.ALOAD||worker.var!=1
            ||!(beforeReceiver instanceof VarInsnNode beforeTask)||beforeTask.getOpcode()!=Opcodes.ALOAD||beforeTask.var!=task.var)
            throw new IllegalStateException("platform worker receiver identity unavailable");
        int lockIndex=method.instructions.indexOf(locks.get(0)),beforeIndex=method.instructions.indexOf(before.get(0));
        int runIndex=method.instructions.indexOf(runs.get(0));
        if(lockIndex>=beforeIndex||beforeIndex>=runIndex||after.stream().anyMatch(c->method.instructions.indexOf(c)<=runIndex))
            throw new IllegalStateException("platform worker callback order unavailable");
        var unlocks=calls.stream().filter(c->workerCall(c,"unlock")).toList();
        var cleanup=unlocks.stream().filter(c->method.instructions.indexOf(c)>lockIndex).toList();
        if(unlocks.size()!=3||cleanup.size()!=2||cleanup.stream().anyMatch(c->method.instructions.indexOf(c)<=runIndex)
            ||retire.stream().anyMatch(c->method.instructions.indexOf(c)<=method.instructions.indexOf(cleanup.get(1))))
            throw new IllegalStateException("platform worker cleanup shape unavailable");
        for(var unlock:cleanup) {
            var receiver=previousCode(unlock);
            if(!(receiver instanceof VarInsnNode w)||w.getOpcode()!=Opcodes.ALOAD||w.var!=worker.var)
                throw new IllegalStateException("platform worker cleanup receiver unavailable");
        }
        var clears=Arrays.stream(method.instructions.toArray()).filter(i->i instanceof VarInsnNode v
            &&v.getOpcode()==Opcodes.ASTORE&&v.var==task.var&&previousCode(v)!=null
            &&previousCode(v).getOpcode()==Opcodes.ACONST_NULL).toList();
        if(clears.size()!=2||clears.stream().anyMatch(i->method.instructions.indexOf(i)<=runIndex))
            throw new IllegalStateException("platform worker task-clear shape unavailable");
        for(int i=0;i<2;i++)if(method.instructions.indexOf(clears.get(i))>=method.instructions.indexOf(cleanup.get(i)))
            throw new IllegalStateException("worker task clear must precede unlock");
        int token=method.maxLocals++,retirement=method.maxLocals++;
        var initialize=new InsnList();initialize.add(new InsnNode(Opcodes.ACONST_NULL));
        initialize.add(new VarInsnNode(Opcodes.ASTORE,token));initialize.add(new InsnNode(Opcodes.ACONST_NULL));
        initialize.add(new VarInsnNode(Opcodes.ASTORE,retirement));method.instructions.insert(initialize);
        var enter=new InsnList();enter.add(new VarInsnNode(Opcodes.ALOAD,0));enter.add(new VarInsnNode(Opcodes.ALOAD,task.var));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"enterDispatch","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));method.instructions.insertBefore(lockReceiver,enter);
        // Close while the certified local receiver still exists. Worker unlock/queue
        // release is a separate resource obligation, never inferred from this event.
        for(var clear:clears) {
            var close=new InsnList();close.add(new VarInsnNode(Opcodes.ALOAD,token));
            close.add(new VarInsnNode(Opcodes.ASTORE,retirement));close.add(closeDispatch(token));
            method.instructions.insertBefore(previousCode(clear),close);
        }
        for(var unlock:cleanup) {
            var released=new InsnList();released.add(new VarInsnNode(Opcodes.ALOAD,retirement));
            released.add(new InsnNode(Opcodes.ACONST_NULL));released.add(new VarInsnNode(Opcodes.ASTORE,retirement));
            released.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"retiredDispatch","(Ljava/lang/Object;)V",false));
            method.instructions.insert(unlock,released);
        }
        // The outer finally handles failures in lock/interruption/cleanup itself. Clear the
        // token before exit, so duplicate finally paths cannot emit a second dispatch exit.
        for(var exit:retire)method.instructions.insertBefore(exit,closeDispatch(token));
        instrumentRunnableCall(method);
    }
    private static boolean workerCall(MethodInsnNode c,String name) {
        return c.getOpcode()==Opcodes.INVOKEVIRTUAL&&c.owner.equals("java/util/concurrent/ThreadPoolExecutor$Worker")
            &&c.name.equals(name)&&c.desc.equals("()V");
    }
    private static boolean poolCall(MethodInsnNode c,String name,String descriptor) {
        return c.getOpcode()==Opcodes.INVOKEVIRTUAL&&c.owner.equals("java/util/concurrent/ThreadPoolExecutor")
            &&c.name.equals(name)&&c.desc.equals(descriptor);
    }
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        AbstractInsnNode previous=instruction.getPrevious();
        while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();return previous;
    }
    private static InsnList closeDispatch(int token) {
        var code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,token));
        code.add(new InsnNode(Opcodes.ACONST_NULL));code.add(new VarInsnNode(Opcodes.ASTORE,token));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"exit","(Ljava/lang/Object;)V",false));return code;
    }
    private static void instrumentRunnableCall(MethodNode method) {
        var calls=Arrays.stream(method.instructions.toArray()).filter(instruction->instruction instanceof MethodInsnNode call
            &&call.getOpcode()==Opcodes.INVOKEINTERFACE&&call.owner.equals("java/lang/Runnable")&&call.name.equals("run")&&call.desc.equals("()V")).toList();
        if(calls.size()!=1)throw new IllegalStateException("platform Runnable dispatch shape unavailable");
        AbstractInsnNode call=calls.get(0);int token=method.maxLocals++,failure=method.maxLocals++;
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var complete=new LabelNode();
        var enter=new InsnList();enter.add(new InsnNode(Opcodes.DUP));
        enter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"enter","(Ljava/lang/Object;)Ljava/lang/Object;",false));
        enter.add(new VarInsnNode(Opcodes.ASTORE,token));enter.add(start);method.instructions.insertBefore(call,enter);
        var exit=new InsnList();exit.add(end);appendExit(exit,token);exit.add(new JumpInsnNode(Opcodes.GOTO,complete));
        exit.add(handler);exit.add(new VarInsnNode(Opcodes.ASTORE,failure));appendExit(exit,token);
        exit.add(new VarInsnNode(Opcodes.ALOAD,failure));exit.add(new InsnNode(Opcodes.ATHROW));exit.add(complete);
        method.instructions.insert(call,exit);
        // Close our execution before the JDK's original afterExecute/finally exception handlers run.
        method.tryCatchBlocks.add(0,new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void instrumentExecution(MethodNode method) {
        instrumentExecution(method,"enter","exit",true);
    }
    private static void instrumentWait(ClassNode node,String name,String descriptor) {
        var matches=node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).toList();
        if(matches.size()!=1)throw new IllegalStateException("wait boundary unavailable: "+node.name+"."+name);
        instrumentExecution(matches.get(0),"beginWait","endWait",false);
    }
    private static void instrumentExecution(MethodNode method,String enterHook,String exitHook,boolean admission) {
        int token=method.maxLocals++,failure=method.maxLocals++;
        Type result=Type.getReturnType(method.desc);int returned=method.maxLocals;method.maxLocals+=result.getSize();
        var start=new LabelNode();var end=new LabelNode();var handler=new LabelNode();var normal=new LabelNode();
        var entry=new InsnList();entry.add(new VarInsnNode(Opcodes.ALOAD,0));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,enterHook,"(Ljava/lang/Object;)Ljava/lang/Object;",false));
        entry.add(new VarInsnNode(Opcodes.ASTORE,token));
        if(admission) {
        entry.add(new VarInsnNode(Opcodes.ALOAD,token));
        entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"blocked","(Ljava/lang/Object;)Z",false));
        entry.add(new JumpInsnNode(Opcodes.IFEQ,start));
        if(result.getSort()==Type.VOID)entry.add(new InsnNode(Opcodes.RETURN));
        else if(method.name.equals("doExec")&&method.desc.equals("()I")) {
            entry.add(new VarInsnNode(Opcodes.ALOAD,0));
            entry.add(new FieldInsnNode(Opcodes.GETFIELD,"java/util/concurrent/ForkJoinTask","status","I"));
            entry.add(new InsnNode(Opcodes.IRETURN));
        } else if(result.getSort()==Type.OBJECT) {
            entry.add(new InsnNode(Opcodes.ACONST_NULL));entry.add(new InsnNode(Opcodes.ARETURN));
        } else if(result.getSort()==Type.BOOLEAN) {
            entry.add(new InsnNode(Opcodes.ICONST_0));entry.add(new InsnNode(Opcodes.IRETURN));
        } else throw new IllegalStateException("unsupported fenced return shape");
        }
        entry.add(start);
        for(AbstractInsnNode instruction:method.instructions.toArray()) {
            int op=instruction.getOpcode();if(op<Opcodes.IRETURN||op>Opcodes.RETURN)continue;
            var replacement=new InsnList();
            if(result.getSort()!=Type.VOID)replacement.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE),returned));
            replacement.add(new JumpInsnNode(Opcodes.GOTO,normal));
            method.instructions.insertBefore(instruction,replacement);method.instructions.remove(instruction);
        }
        method.instructions.insert(entry);method.instructions.add(end);method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE,failure));appendExit(method.instructions,token,exitHook);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,failure));method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(normal);appendExit(method.instructions,token,exitHook);
        if(result.getSort()!=Type.VOID)method.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD),returned));
        method.instructions.add(new InsnNode(result.getSort()==Type.VOID?Opcodes.RETURN:result.getOpcode(Opcodes.IRETURN)));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/Throwable"));
    }
    private static void appendExit(InsnList code,int token) {
        appendExit(code,token,"exit");
    }
    private static void appendExit(InsnList code,int token,String hook) {
        code.add(new VarInsnNode(Opcodes.ALOAD,token));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,hook,"(Ljava/lang/Object;)V",false));
    }
    static byte[] canonical(byte[] bytes) {
        var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        // HotSpot reconstruction omits the legacy Deprecated attribute (ASM pseudo access flag).
        // Runtime annotations and every executable instruction remain part of the comparison.
        node.access&=~Opcodes.ACC_DEPRECATED;
        node.methods.forEach(method->method.access&=~Opcodes.ACC_DEPRECATED);
        node.fields.forEach(field->field.access&=~Opcodes.ACC_DEPRECATED);
        node.methods.sort(Comparator.comparing((MethodNode method)->method.name).thenComparing(method->method.desc));
        node.fields.sort(Comparator.comparing((FieldNode field)->field.name).thenComparing(field->field.desc));
        node.innerClasses.sort(Comparator.comparing(inner->inner.name));
        if(node.nestMembers!=null)node.nestMembers.sort(String::compareTo);
        var writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    private static final class PlatformWriter extends ClassWriter {
        PlatformWriter(){super(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);}
        @Override protected String getCommonSuperClass(String left,String right) {
            try {
                Class<?> a=Class.forName(left.replace('/','.'),false,null),b=Class.forName(right.replace('/','.'),false,null);
                if(a.isAssignableFrom(b))return left;if(b.isAssignableFrom(a))return right;
                if(a.isInterface()||b.isInterface())return "java/lang/Object";
                do{a=a.getSuperclass();}while(!a.isAssignableFrom(b));return a.getName().replace('.','/');
            } catch(ClassNotFoundException failure){throw new IllegalStateException("non-platform task frame type",failure);}
        }
    }
}
