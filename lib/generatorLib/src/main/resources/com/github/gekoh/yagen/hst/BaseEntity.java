package com.github.gekohl.yagen.hst;

import com.csg.cs.aura.core.domain.base.FieldConstants;
import com.csg.cs.aura.core.domain.base.IdentifiableEntity;
import com.csg.cs.aura.core.domain.base.UUIDGenerator;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * @author Georg Kohlweiss
 */
@SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
@MappedSuperclass
public abstract class BaseEntity implements IdentifiableEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BaseEntity.class);

    /**
     * Technical unique key of the row.
     */
    @Id
    @Column(name = "HST_UUID", length = FieldConstants.UUID_LEN)
    private String uuid;

    /**
    * operation on live data
    */
   @Enumerated(EnumType.STRING)
   @Column(name = "OPERATION", length = FieldConstants.ENUM_DEFAULT_LEN, nullable = false)
   private Operation operation;

    /**
     * Timestamp which defines when the entity was committed
     * (currently we use the timestamp when the first entity of a transaction was changed,
     * but this timestamp then is used for all changed entities within the same transaction)
     */
    @Type(type = "com.csg.cs.aura.core.infra.hibernate.DateTimeType")
    @Column(name = "TRANSACTION_TIMESTAMP", nullable = false)
    private DateTime transactionTimestamp;

    /**
     * Entity was invalidated at.
     */
    @Type(type = "com.csg.cs.aura.core.infra.hibernate.DateTimeType")
    @Column(name = "INVALIDATED_AT", nullable = true)
    private DateTime invalidatedAt;

    protected BaseEntity() {
        this.uuid = UUIDGenerator.next();
    }

    public BaseEntity(String liveUuid, Operation operation, DateTime transactionTimestamp, DateTime invalidatedAt) {
        this();
        setLiveUuid(liveUuid);
        this.operation = operation;
        this.transactionTimestamp = transactionTimestamp;
        this.invalidatedAt = invalidatedAt;
    }

    public String getUuid() {
        return uuid;
    }

    protected abstract void setLiveUuid(String liveUuid);

    public DateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public DateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public Operation getOperation() {
        return operation;
    }
}
