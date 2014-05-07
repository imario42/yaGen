/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
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
