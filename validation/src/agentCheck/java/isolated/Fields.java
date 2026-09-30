package isolated;

/** Unrelated data carrier for direct writer checks; never shares the policy owner's package. */
public final class Fields {
    public Object object="old";
    public boolean bool;
    public byte b;
    public char c;
    public short s;
    public int i;
    public long l;
    public float f;
    public double d;
    public static void putForeign(java.util.Map<Object,Object> map,Object key,Object value) {
        map.put(key,value);
    }
    public static boolean callForeignGuard(Object map) {
        return dev.ronova.pro.bootstrap.TaskBridge.indexRemovalAllowed(map,null,null);
    }
}
