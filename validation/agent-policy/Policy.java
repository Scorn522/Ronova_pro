package isolated;
public final class Policy {
    public static volatile Object denied;
    private static int privateValue;
    public static int ownReflectiveWrite() throws Exception {
        Policy.class.getDeclaredField("privateValue").setInt(null,17);return privateValue;
    }
    public static boolean field(Object receiver,Class<?> declaring,String name,Object value) { return receiver==denied; }
    public static boolean dispatch(Object receiver,String operation,Object value) { return receiver==denied&&value instanceof Number&&((Number)value).floatValue()<0; }
    public static boolean task(Object task,String operation) { return task==denied; }
}
