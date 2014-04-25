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
public @interface IntervalPartitioning {
    String columnName() default "partition_date";
    String interval() default "numtoyminterval(1, 'MONTH')";
    String startPartitionLessThanValue() default "";
    boolean enableRowMovement() default false;

    /**
     * If set to true the global primary key will be converted to a local unique index.
     * In this case no foreign key to this entity will be created as it would then need to be a compound key
     * which is not configured as such in jpa.
     *
     * @return
     */
    boolean useLocalPK() default false;
}
