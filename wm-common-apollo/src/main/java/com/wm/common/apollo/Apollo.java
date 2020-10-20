package com.wm.common.apollo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * apollo配置自动注入
 * Created by Mengwei on 2019/7/19.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Apollo {
    String namespace();

    String key();

    String defaultVal() default "";
}
