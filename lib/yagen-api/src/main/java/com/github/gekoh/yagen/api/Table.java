package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Georg Kohlweiss
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface Table {
    String shortName() default "";
    Sequence[] additionalSequences() default {};
    CheckConstraint[] checkConstraints() default {};
    UniqueConstraint[] uniqueConstraints() default {};
    Index[] indexes() default {};
}
