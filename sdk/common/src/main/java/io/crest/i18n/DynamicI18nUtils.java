package io.crest.i18n;

import jakarta.annotation.Resource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

@Component
public class DynamicI18nUtils {


    private static ReloadableResourceBundleMessageSource messageSource;

    @Resource
    public void setMessageSource(ReloadableResourceBundleMessageSource messageSource) {
        DynamicI18nUtils.messageSource = messageSource;
    }

    // 格式化日期时间并返回统一展示值
    public static void addOrUpdate(String baseName) {
        messageSource.addBasenames(baseName);
        messageSource.setCacheSeconds(0);
        messageSource.clearCache();
    }
}
