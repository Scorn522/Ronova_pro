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
    /** The Core supplies the operand from its one original consumer.accept call. */
    public static boolean callback(Object consumer,Class<?> callee,String name,String descriptor,int calleeBottomDepth,
                                   Class<?> boundary,String boundaryMethod,String boundaryDescriptor,int boundaryBottomDepth){
        core();Objects.requireNonNull(consumer,"consumer");Objects.requireNonNull(callee,"callee");
        Objects.requireNonNull(name,"name");Objects.requireNonNull(descriptor,"descriptor");Objects.requireNonNull(boundary,"boundary");
        Objects.requireNonNull(boundaryMethod,"boundaryMethod");Objects.requireNonNull(boundaryDescriptor,"boundaryDescriptor");
        return calleeBottomDepth>0&&boundaryBottomDepth>0&&NativeControl.carrierCallback(consumer,callee,name,descriptor,calleeBottomDepth,
                boundary,boundaryMethod,boundaryDescriptor,boundaryBottomDepth);
    }
}
