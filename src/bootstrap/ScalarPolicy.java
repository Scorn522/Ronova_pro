package dev.ronova.pro.bootstrap;

/** Explicit scalar tradeoffs. This class never grants a source or a receipt. */
final class ScalarPolicy {
    private ScalarPolicy(){}
    static boolean directBufferCandidate(Object receiver){
        return receiver instanceof java.nio.Buffer&&receiver.getClass().getClassLoader()==null
                &&receiver.getClass().getName().startsWith("java.nio.Direct");
    }
}
