package io.crest.auth;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrestPermit {

    /**
     * 鉴权 EL 表达式数组。
     * 当前权限表达式只处理与运算，不处理或运算。
     *
     * @return 权限表达式数组
     */
    String[] value() default {};

    String busiFlag() default "";
}
