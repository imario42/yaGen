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
public @interface CheckConstraint {
    String name();

    /**
     * for initially deferred constraints in PostgreSql pls see {@link #initiallyDeferred()}
     * @return declaration as to appear in ddl
     */
    String declaration();

    /**
     * when setting initiallyDeferred to true please prefix any column reference from the {@link #declaration()} with %1$s
     * just like "%1$sCOLUMN_NAME", this is because PostgreSql doesn't support deferred check constraints and
     * this functionality will be simulated with a constraint trigger that needs to reference the columns in another way
     *
     * @return true if constraint should be deferrable and initially deferred
     */
    boolean initiallyDeferred() default false;
}
