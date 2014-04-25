package com.github.gekoh.yagen.api;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.Column;
import java.io.Serializable;

/**
 * @author Thomas Spiegl
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public abstract class AuditInfo implements Serializable {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ModificationInfo.class);

    public static final String CREATED_AT = "created_at";
    public static final String CREATED_BY = "created_by";
    public static final String LAST_MODIFIED_AT = "last_modified_at";
    public static final String LAST_MODIFIED_BY = "last_modified_by";

    /**
     * Entity was created at.
     */
    @Type(type = "com.csg.cs.aura.core.infra.hibernate.DateTimeType")
    @Column(name = CREATED_AT, nullable = false)
    private DateTime createdAt;

    /**
     * Entity was created by.
     */
    @Column(name = CREATED_BY, length = Constants.USER_NAME_LEN, nullable = false)
    private String createdBy;

    /**
     * Entity was last modified at.
     */
    @Type(type = "com.csg.cs.aura.core.infra.hibernate.DateTimeType")
    @Column(name = LAST_MODIFIED_AT)
    private DateTime lastModifiedAt;

    /**
     * Entity was last modified by.
     */
    @Column(name = LAST_MODIFIED_BY, length = Constants.USER_NAME_LEN)
    private String lastModifiedBy;

    public AuditInfo() {
    }

    public AuditInfo(String createdBy) {
        this.createdBy = createdBy;
    }

    protected abstract DateTime getCurrentTime();

    protected abstract String getUserName();

    /**
     * Called by JPA container before sql insert.
     */
    public void prePersist() {
        this.createdAt = getCurrentTime();

        if (this.createdBy == null || this.createdBy.trim().isEmpty()) {
            this.createdBy = getUserName();
        }
    }

    /**
     * Called by JPA container before sql update.
     */
    public void preUpdate() {
        this.lastModifiedAt = getCurrentTime();
        this.lastModifiedBy = getUserName();
    }

    public DateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public DateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }
}
