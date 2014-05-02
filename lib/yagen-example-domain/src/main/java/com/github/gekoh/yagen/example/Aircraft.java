package com.github.gekoh.yagen.example;

import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.TemporalEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * This entity represents an aircraft
 *
 * @author Georg Kohlweiss
 */
@Entity
@Table(name = "AIRCRAFT")
@com.github.gekoh.yagen.api.Table(shortName = "AC")
@TemporalEntity(historyTableName = "AIRCRAFT_HST")
public class Aircraft extends BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Aircraft.class);

    /**
     * aircraft type
     */
    private String type;

    /**
     * engine technology
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ENGINE_TYPE", length = Constants.ENUM_DEFAULT_LEN)
    private EngineType engineType;

    /**
     * national call sign
     */
    @Column(name = "CALL_SIGN", length = 6, unique = true)
    private String callSign;

    public Aircraft(String type, String callSign) {
        this.type = type;
        this.callSign = callSign;
    }

    public String getType() {
        return type;
    }

    public String getCallSign() {
        return callSign;
    }
}