package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Listener {
    Class<? extends EventFilter>[] filters() default {};

    int priority() default 0;

    boolean async() default false;
}