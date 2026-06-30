package io.crest.extensions.datasource.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring 上下文工具类，提供运行时获取 Bean 和 BeanFactory 的能力
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    // 获取 bean 工厂，用来实现动态注入 bean
    // 不能使用其他类加载器加载 bean，否则会出现类未找到或类未定义异常
    public static DefaultListableBeanFactory getBeanFactory(){
        return (DefaultListableBeanFactory) getApplicationContext().getAutowireCapableBeanFactory();
    }


    /**
     * 获取当前 Spring 容器中的全部 Bean 元信息
     */
    public static List<Map<String, Object>> getAllBean() {


        List<Map<String, Object>> list = new ArrayList<>();


        String[] beans = getApplicationContext()
                .getBeanDefinitionNames();

        for (String beanName : beans) {
            Class<?> beanType = getApplicationContext()
                    .getType(beanName);

            Map<String, Object> map = new HashMap<>();

            map.put("BeanName", beanName);
            map.put("beanType", beanType);
            map.put("package", beanType.getPackage());
            list.add(map);

        }

        return list;
    }




    /**
     * 上下文对象实例
     */
    private static ApplicationContext applicationContext;


    /**
     * 保存 Spring 注入的应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取applicationContext
     *
     * @return
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    /**
     * 按 Bean 名称获取实例
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 按 Bean 类型获取实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 按 Bean 名称和类型获取实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
}
