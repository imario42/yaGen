package com.github.gekoh.yagen.example;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.UUID;

/**
 * @author Georg Kohlweiss
 */
@MappedSuperclass
public class BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BaseEntity.class);

    /**
     * technical unique identifier
     */
    @Id
    @Column(length = 36)
    private String uuid;

    protected BaseEntity() {
        uuid = UUID.randomUUID().toString();
    }

    public String getUuid() {
        return uuid;
    }
}