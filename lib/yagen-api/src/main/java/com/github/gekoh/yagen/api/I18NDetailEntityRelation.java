package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * @author Georg Kohlweiss
 */
@Target({METHOD, FIELD}) @Retention(RUNTIME)
public @interface I18NDetailEntityRelation {
}
