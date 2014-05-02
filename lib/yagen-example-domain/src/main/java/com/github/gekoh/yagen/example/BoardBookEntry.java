/*
 * BoardBookEntry
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.example;

import com.github.gekoh.yagen.api.Default;
import com.github.gekoh.yagen.api.TemporalEntity;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * @author Georg Kohlweiss (F477448)
 */
@Entity
@Table(name = "BOARD_BOOK_ENTRY")
@com.github.gekoh.yagen.api.Table(shortName = "BBE")
@TemporalEntity(historyTableName = "BOARD_BOOK_ENTRY_HST")
public class BoardBookEntry extends BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BoardBookEntry.class);

    /**
     * consecutive number, usually accumulated landing count
     */
    @Basic(optional = false)
    private int num;

    /**
     * names of crew, PIC, ev. flight instructor (FI) and pax
     */
    @Basic(optional = false)
    @Column(name = "CREW_AND_PAX")
    private String crewAndPax;

    /**
     * departure airport or airfield, 4 letter ICAO code
     */
    @Basic(optional = false)
    @Column(name = "DEPARTED_FROM", length = 4)
    private String departedFrom;

    /**
     * timestamp of departure in UTC
     */
    @Basic(optional = false)
    @Type(type="org.joda.time.contrib.hibernate.PersistentDateTime")
    private DateTime departed;

    /**
     * destination airport or airfield, 4 letter ICAO code
     */
    @Basic(optional = false)
    @Column(name = "LANDED_AT", length = 4)
    private String landedAt;

    /**
     * timestamp of final touchdown in UTC
     */
    @Basic(optional = false)
    @Column(name = "LANDING_TIME")
    @Type(type="org.joda.time.contrib.hibernate.PersistentDateTime")
    private DateTime landingTime;

    /**
     * landing count made, especially for touch and go training
     */
    @Basic(optional = false)
    @Default(sqlExpression = "1")
    private int landings;

    /**
     * holds information about added fuel or oil
     */
    @Column(name = "ADDED_OPERATING_RESOURCES")
    private String addedOperatingResources;

    BoardBookEntry() {
    }

    public BoardBookEntry(int num, String crewAndPax, String departedFrom, DateTime departed, String landedAt, DateTime landingTime, int landings, String addedOperatingResources) {
        this.num = num;
        this.crewAndPax = crewAndPax;
        this.departedFrom = departedFrom;
        this.departed = departed;
        this.landedAt = landedAt;
        this.landingTime = landingTime;
        this.landings = landings;
        this.addedOperatingResources = addedOperatingResources;
    }

    public int getNum() {
        return num;
    }

    public String getCrewAndPax() {
        return crewAndPax;
    }

    public String getDepartedFrom() {
        return departedFrom;
    }

    public DateTime getDeparted() {
        return departed;
    }

    public String getLandedAt() {
        return landedAt;
    }

    public DateTime getLandingTime() {
        return landingTime;
    }

    public int getLandings() {
        return landings;
    }

    public String getAddedOperatingResources() {
        return addedOperatingResources;
    }
}