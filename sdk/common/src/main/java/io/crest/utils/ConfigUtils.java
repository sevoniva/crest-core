package io.crest.utils;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.Objects;

// 提供当前模块复用的工具能力
public class ConfigUtils {

    public static String configPath = "opt" + File.separator + "crest" + File.separator + "conf" + File.separator + "application.yml";

    // 读取配置并返回当前功能所需参数
    public static String getConfig(String key, String defaultValue) {
        String runtimeProperty = getRuntimeProperty(key);
        if (runtimeProperty != null) {
            return runtimeProperty;
        }
        try {
            String filePath = System.getProperty("user.home");
            filePath = filePath.replace("file:", "");
            Resource resource = new FileSystemResource(filePath + File.separator + configPath);
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(resource);

            String basePath = Objects.requireNonNull(factory.getObject()).getProperty("base-path", "");
            basePath = basePath.replaceAll("\\$\\{user.home}", System.getProperty("user.home").replaceAll("\\\\", "/"));

            String property = Objects.requireNonNull(factory.getObject()).getProperty(key, defaultValue);
            return property.replaceAll("\\$\\{base-path}", basePath);
        } catch (Exception e) {
        }
        return defaultValue;
    }

    // 格式化日期时间并返回统一展示值
    private static String getRuntimeProperty(String key) {
        String systemProperty = System.getProperty(key);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        try {
            Environment environment = CommonBeanFactory.getBean(Environment.class);
            if (environment != null) {
                String property = environment.getProperty(key);
                if (property != null && !property.isBlank()) {
                    return property;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
