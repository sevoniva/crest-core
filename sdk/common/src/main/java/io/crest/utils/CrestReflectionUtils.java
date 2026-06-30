package io.crest.utils;

import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Method;

// 提供当前模块复用的工具能力
public class CrestReflectionUtils {

    public static Method findMethod(Class<?> cla, String methodName) {
        Method[] methods = cla.getMethods();
        if (ArrayUtils.isEmpty(methods)) return null;
        for (Method method : methods) {
            if (method.getName().equals(methodName)){
                return method;
            }
        }
        return null;
    }
}
