package dev.ronova.pro.bootstrap;

import java.lang.reflect.Field;
import java.util.Objects;

/** Actual VM declarations and values for the Core's already selected carrier objects. */
public final class CarrierBridge {
    private CarrierBridge(){}
    private static void core(){
        if(!ControlBridge.coreWriter())throw new SecurityException("ACTUAL_CARRIER_CORE_READER_REQUIRED");
    }
    /** Field, declaring Class, name, descriptor, type, offset, width, current value. */
    public static Object[][] fields(Object receiver){
        core();Objects.requireNonNull(receiver,"receiver");
        Object[][] fields=NativeControl.carrierFields(receiver);
        if(fields==null)throw new IllegalStateException("CARRIER_INSTANCE_FIELDS_UNAVAILABLE");
        return fields;
    }
    public static Object read(Object receiver,Field field){
        core();Objects.requireNonNull(receiver,"receiver");Objects.requireNonNull(field,"field");
        Object[] value=NativeControl.carrierRead(receiver,field);
        if(value==null||value.length!=1)throw new IllegalStateException("CARRIER_FIELD_READ_UNAVAILABLE");
        return value[0];
    }
    /** Actual Java declaration before the original invocation; unknown dispatch stays unresolved. */
    public static Class<?> method(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,Class<?> caller){
        core();Objects.requireNonNull(symbolic,"symbolic");Objects.requireNonNull(name,"name");
        Objects.requireNonNull(descriptor,"descriptor");Objects.requireNonNull(caller,"caller");
        return NativeControl.carrierMethod(receiver,symbolic,name,descriptor,opcode,caller);
    }
    /** Fresh current Java definition: Method, ConstMethod, pool, code length; no method is invoked. */
    public static long[] methodImage(Class<?> declaring,String name,String descriptor){
        core();Objects.requireNonNull(declaring,"declaring");Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");
        return NativeControl.carrierMethodImage(declaring,name,descriptor);
    }
    /** Fresh read-only physical identity: bottom depth, BCI, Method, ConstMethod, pool, code length. */
    public static long[] frame(Class<?> declaring,String name,String descriptor,int bottomDepth){
        core();Objects.requireNonNull(declaring,"declaring");Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");
        return bottomDepth>0?NativeControl.carrierFrame(declaring,name,descriptor,bottomDepth):null;
    }
    /** Same frame tuple at the original invoke immediately after this actual instrumentation hook. */
    public static long[] callSite(Object receiver,Class<?> symbolic,String name,String descriptor,int opcode,
                                 Class<?> caller,String callerMethod,String callerDescriptor,int callerBottomDepth){
        core();Objects.requireNonNull(symbolic,"symbolic");Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");
        Objects.requireNonNull(caller,"caller");Objects.requireNonNull(callerMethod,"callerMethod");Objects.requireNonNull(callerDescriptor,"callerDescriptor");
        return callerBottomDepth>0?NativeControl.carrierCallSite(receiver,symbolic,name,descriptor,opcode,caller,callerMethod,callerDescriptor,callerBottomDepth):null;
    }
    /** Exact initialized platform sentinels; this does not enumerate a static payload graph. */
    public static Object staticValue(Class<?> declaring,String name,String descriptor){
        core();Objects.requireNonNull(declaring,"declaring");Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");
        Object[] value=NativeControl.carrierStaticValue(declaring,name,descriptor);
        if(value==null||value.length!=1)throw new IllegalStateException("CARRIER_STATIC_FIELD_READ_UNAVAILABLE");
        return value[0];
    }
    /** Current physical frame identity, counted from the bottom, including hidden frames. */
    public static int callbackDepth(Class<?> declaring,String name,String descriptor){
        core();Objects.requireNonNull(declaring,"declaring");Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");
        return NativeControl.carrierCallbackDepth(declaring,name,descriptor);
    }
    /** The Core supplies its original functional operand; every physical business edge is read from the current VM. */
    public static boolean callback(Object callback,Class<?> callee,String name,String descriptor,int calleeBottomDepth,
                                   Class<?> boundary,String boundaryMethod,String boundaryDescriptor,int boundaryBottomDepth){
        core();Objects.requireNonNull(callback,"callback");Objects.requireNonNull(callee,"callee");
        Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");Objects.requireNonNull(boundary,"boundary");
        Objects.requireNonNull(boundaryMethod,"boundaryMethod");Objects.requireNonNull(boundaryDescriptor,"boundaryDescriptor");
        return calleeBottomDepth>0&&boundaryBottomDepth>0&&NativeControl.carrierCallback(callback,callee,name,descriptor,calleeBottomDepth,
                boundary,boundaryMethod,boundaryDescriptor,boundaryBottomDepth);
    }
}
