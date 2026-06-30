package io.crest.traffic;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrestTraffic {
    int value() default 0;

    String api();
}
