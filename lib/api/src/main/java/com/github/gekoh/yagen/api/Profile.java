package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * To be used on Entities or JoinTable relation properties when a specific generator profile should only enable
 * generation of certain tables. In this case the corresponding entity class has to be annotated with @Profile and
 * the profile name(s) as parameter(s).
 *
 * Note that this does not affect profile IDs for which no Profile annotation is specified, such profiles would still
 * include all entities and their tables.
 *
 * @author Georg Kohlweiss 
 */
@Target({TYPE, FIELD}) @Retention(RUNTIME)
public @interface Profile {
    /**
     * Profile IDs as passed to the generator through --profile-name
     * @return Profile IDs which need to include table of annotated Entity or JoinTable property
     */
    String[] value();
}
