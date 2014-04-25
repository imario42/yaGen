package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Georg Kohlweiss
 */
@Target({TYPE, FIELD}) @Retention(RUNTIME)
public @interface TemporalEntity {
    String historyTableName() default "";
    String historyTimestampColumnName() default "TRANSACTION_TIMESTAMP";
    String[] ignoreChangeOfColumns() default {};
}
