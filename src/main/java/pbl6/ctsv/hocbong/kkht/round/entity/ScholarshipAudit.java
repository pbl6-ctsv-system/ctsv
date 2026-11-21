package pbl6.ctsv.hocbong.kkht.round.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "hb_nhat_ky", schema = "dbo")
public class ScholarshipAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nguoi_dung_id", nullable = false)
    private Long userId;

    @Column(name = "vai_tro_id", nullable = false)
    private Long roleId;

    @Nationalized
    @Column(name = "loai_doi_tuong", nullable = false, length = 128)
    private String objectType;

    @Column(name = "doi_tuong_id", nullable = false)
    private Long objectId;

    @Nationalized
    @Column(name = "pham_vi", nullable = false, length = 128)
    private String scope;

    @Nationalized
    @Column(name = "hanh_dong", nullable = false, length = 128)
    private String action;

    @Nationalized
    @Column(name = "du_lieu_truoc", columnDefinition = "nvarchar(max)")
    private String beforeData;

    @Nationalized
    @Column(name = "du_lieu_sau", nullable = false, columnDefinition = "nvarchar(max)")
    private String afterData;

    @Nationalized
    @Column(name = "ly_do", columnDefinition = "nvarchar(max)")
    private String reason;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "thoi_diem", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "correlation_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID correlationId;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public Long getObjectId() { return objectId; }
    public void setObjectId(Long objectId) { this.objectId = objectId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getBeforeData() { return beforeData; }
    public void setBeforeData(String beforeData) { this.beforeData = beforeData; }
    public String getAfterData() { return afterData; }
    public void setAfterData(String afterData) { this.afterData = afterData; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
}
