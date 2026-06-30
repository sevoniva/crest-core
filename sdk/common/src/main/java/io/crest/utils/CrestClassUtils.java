package io.crest.utils;

// 提供当前模块复用的工具能力
public class CrestClassUtils {

    // 判断当前类型是否满足业务分类
    public static boolean isPrimitiveOrWrapper(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> objClass = obj.getClass();
        for (Class<?> primitiveWrapper : primitiveWrappers) {
            if (primitiveWrapper.isAssignableFrom(objClass)) {
                return true;
            }
        }

        return isPrimitive(objClass);
    }

    // 判断当前类型是否满足业务分类
    private static boolean isPrimitive(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        }

        String name = clazz.getName();
        for (String primitiveTypeName : primitiveTypeNames) {
            if (name.equals(primitiveTypeName)) {
                return true;
            }
        }

        return false;
    }

    private static final Class<?>[] primitiveWrappers = {
            Boolean.class, Character.class, Byte.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class
    };

    private static final String[] primitiveTypeNames = {
            "boolean", "char", "byte", "short",
            "int", "long", "float", "double"
    };

}
