package io.crest.utils;

import io.micrometer.common.util.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
/**
 * Spring Bean 获取工具，用于非托管对象访问容器 Bean
 */
public class CommonBeanFactory implements ApplicationContextAware {
    /**
     * 当前应用的 Spring 上下文
     */
    private static ApplicationContext context;

    /**
     * 创建 Bean 工厂工具实例
     */
    public CommonBeanFactory() {
    }

    /**
     * 保存 Spring 注入的应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        context = ctx;
    }

    /**
     * 按 Bean 名称获取容器对象
     */
    public static Object getBean(String beanName) {
        try {
            return context != null && !StringUtils.isBlank(beanName) ? context.getBean(beanName) : null;
        } catch (BeansException e) {
            return null;
        }
    }

    /**
     * 按类型获取容器对象
     */
    public static <T> T getBean(Class<T> className) {
        try {
            return context != null && className != null ? context.getBean(className) : null;
        } catch (BeansException e) {
            return null;
        }
    }
    /**
     * 返回当前应用上下文
     */
    public static ApplicationContext getApplicationContext() {
        return context;
    }

    /**
     * 获取指定类型的代理 Bean
     */
    public static <T> T proxy(Class<T> className) {
        try {
            return context != null && className != null ? context.getBean(className) : null;
        } catch (BeansException e) {
            return null;
        }
    }
}
