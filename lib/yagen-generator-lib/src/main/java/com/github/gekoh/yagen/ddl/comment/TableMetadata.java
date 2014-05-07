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
package com.github.gekoh.yagen.ddl.comment;

/**
 * @author Georg Kohlweiss
 */
@SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
public class TableMetadata  implements Metadata {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TableMetadata.class);

    private String shortName;
    private String entityClassName;
    private String comment;

    TableMetadata() {
    }

    public TableMetadata(String shortName, String entityClassName, String comment) {
        this.shortName = shortName;
        this.entityClassName = entityClassName;
        this.comment = comment;
    }

    public String getShortName() {
        return shortName;
    }

    public String getEntityClassName() {
        return entityClassName;
    }

    public String getComment() {
        return comment;
    }
}
