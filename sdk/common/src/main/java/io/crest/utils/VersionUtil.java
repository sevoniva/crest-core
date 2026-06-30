package io.crest.utils;

import org.springframework.core.env.Environment;

// 提供当前模块复用的工具能力
public class VersionUtil {


    public static String getRandomVersion() {
        Environment environment = CommonBeanFactory.getBean(Environment.class);
        assert environment != null;
        return environment.getProperty("crest.version", "1.0.0");
    }

}
