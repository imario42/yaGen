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
