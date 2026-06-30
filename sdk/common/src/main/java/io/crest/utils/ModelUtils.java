package io.crest.utils;

import io.crest.model.CrestDeploymentMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// 提供当前模块复用的工具能力
public class ModelUtils {

    private static String modelValue;

    @Value("${spring.profiles.active:standalone}")
    public void setModelValue(String modelValue) {
        ModelUtils.modelValue = modelValue;
    }

    public static CrestDeploymentMode get() {
        return CrestDeploymentMode.valueOf(modelValue.toUpperCase());
    }

    // 判断当前类型是否满足业务分类
    public static boolean isDesktop() {
        return get() == CrestDeploymentMode.DESKTOP;
    }
}
