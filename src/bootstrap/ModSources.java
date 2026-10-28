package dev.ronova.pro.bootstrap;

/** Positive method-source frames for batched client provenance, never an authorization cache. */
public final class ModSources {
    private static final Module[] EMPTY=new Module[0];
    private static final StackWalker CALLER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final StackWalker HIDDEN=StackWalker.getInstance(java.util.Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE,StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> HIDDEN_CALLER=new HiddenCaller();
    private static final class HiddenCaller implements java.util.function.Function<java.util.stream.Stream<StackWalker.StackFrame>,Class<?>> {
        public Class<?> apply(java.util.stream.Stream<StackWalker.StackFrame> frames){
            var cursor=frames.iterator();while(cursor.hasNext()){Class<?> type=cursor.next().getDeclaringClass();if(type.getNestHost()!=ModSources.class)return type;}return null;
        }
    }
    private static final ThreadLocal<Frame> CURRENT=new ThreadLocal<>(),ROOT=new ThreadLocal<>();
    private static final ClassValue<Module[]> SINGLE=new ClassValue<>(){protected Module[] computeValue(Class<?> type){return new Module[]{DefinitionBridge.module(type)};}};
    private static final class Frame {Frame parent,next;Class<?> declaring;Module[] sources=EMPTY;boolean active;}
    private ModSources(){}
    public static Object enter(Class<?> declaring){
        // getCallerClass deliberately hides lambda implementation frames even with
        // SHOW_HIDDEN_FRAMES. Only an actual hidden declaring class needs this walk.
        if((declaring!=null&&declaring.isHidden()?HIDDEN.walk(HIDDEN_CALLER):CALLER.getCallerClass())!=declaring)throw new SecurityException("ACTUAL_MOD_METHOD_REQUIRED");
        Frame parent=CURRENT.get(),frame=parent==null?ROOT.get():parent.next;
        if(frame==null){frame=new Frame();if(parent==null)ROOT.set(frame);else parent.next=frame;}
        frame.parent=parent;frame.declaring=declaring;frame.active=true;Module owner=DefinitionBridge.module(declaring);
        Module[] sources=parent==null?EMPTY:parent.sources;boolean present=false;for(Module source:sources)if(source==owner){present=true;break;}
        if(!present){if(sources.length==0){sources=SINGLE.get(declaring);if(sources[0]!=owner){SINGLE.remove(declaring);sources=SINGLE.get(declaring);}}else{Module[] next=java.util.Arrays.copyOf(sources,sources.length+1);next[sources.length]=owner;sources=next;}}
        frame.sources=sources;CURRENT.set(frame);return frame;
    }
    public static void exit(Object token){
        if(!(token instanceof Frame frame)||CURRENT.get()!=frame||!frame.active)throw new SecurityException("ACTUAL_MOD_METHOD_EXIT_REQUIRED");
        if((frame.declaring.isHidden()?HIDDEN.walk(HIDDEN_CALLER):CALLER.getCallerClass())!=frame.declaring)throw new SecurityException("ACTUAL_MOD_METHOD_EXIT_REQUIRED");
        CURRENT.set(frame.parent);frame.active=false;frame.declaring=null;frame.sources=EMPTY;
    }
    static Module[] current(){Frame frame=CURRENT.get();return frame==null?EMPTY:frame.sources;}
}
