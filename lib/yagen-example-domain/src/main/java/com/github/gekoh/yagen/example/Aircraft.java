package com.github.gekoh.yagen.example;

import com.github.gekoh.yagen.api.CascadeDelete;
import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.TemporalEntity;
import org.hibernate.annotations.IndexColumn;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "AIRCRAFT_UUID", nullable = false)
    @IndexColumn(name = "num", base = 1, nullable = false)
    @CascadeDelete
    private List<BoardBookEntry> boardBookEntries;

    Aircraft() {
    }

    public Aircraft(String type, String callSign) {
        this.type = type;
        this.callSign = callSign;
        this.boardBookEntries = new ArrayList<BoardBookEntry>();
    }

    public String getType() {
        return type;
    }

    public String getCallSign() {
        return callSign;
    }

    public List<BoardBookEntry> getBoardBookEntries() {
        return Collections.unmodifiableList(boardBookEntries);
    }

    public void addBoardBookEntry(BoardBookEntry boardBookEntry) {
        boardBookEntries.add(boardBookEntry);
    }

    public void removeBoardBookEntry(BoardBookEntry boardBookEntry) {
        boardBookEntries.remove(boardBookEntry);
    }
}