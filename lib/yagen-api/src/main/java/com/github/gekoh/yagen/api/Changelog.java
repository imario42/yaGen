package com.github.gekoh.yagen.api;/*
 * Changelog
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Georg Kohlweiss (F477448)
 */
@Target({TYPE, FIELD}) @Retention(RUNTIME)
public @interface Changelog {
    Class changelogEntity();
    String timelineViewName() default "";
    String changelogQueryString();
}
