package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Georg Kohlweiss
 */
@Target({METHOD, FIELD}) @Retention(RUNTIME)
public @interface UniqueConstraint {
    String name();
    // either use custom declaration
    String declaration() default "";
    // or specify set of columns to be unique
    String[] columnNames() default {};
    boolean usingLocalIndex() default false;
    boolean initiallyDeferred() default false;
}
