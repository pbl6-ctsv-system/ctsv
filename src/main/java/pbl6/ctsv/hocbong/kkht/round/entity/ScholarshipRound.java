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

@Entity
@Table(name = "kkht_dot_xet", schema = "dbo")
public class ScholarshipRound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "hoc_ky_id", nullable = false)
    private Long semesterId;

    @Column(name = "anh_xa_ky_nguon_id", nullable = false)
    private Long sourceMappingId;

    @Column(name = "so_dot", nullable = false)
    private Integer number;

    @Nationalized
    @Column(name = "ten_dot", nullable = false, length = 512)
    private String name;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "mo_luc", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime opensAt;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "chot_luc", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime closedAt;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "moc_chot_hoc_phi", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime tuitionSnapshotAt;

    @Column(name = "quy_tac_id")
    private Long ruleVersionId;

    @Nationalized
    @Column(name = "trang_thai", nullable = false, length = 32)
    private String status;

    @Column(name = "nguoi_tao_id", nullable = false)
    private Long createdBy;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "phien_ban_ky_thuat", nullable = false, length = 8,
            columnDefinition = "rowversion", insertable = false, updatable = false)
    private byte[] version;

    public Long getId() { return id; }
    public Long getSemesterId() { return semesterId; }
    public void setSemesterId(Long semesterId) { this.semesterId = semesterId; }
    public Long getSourceMappingId() { return sourceMappingId; }
    public void setSourceMappingId(Long sourceMappingId) { this.sourceMappingId = sourceMappingId; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OffsetDateTime getOpensAt() { return opensAt; }
    public void setOpensAt(OffsetDateTime opensAt) { this.opensAt = opensAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(OffsetDateTime closedAt) { this.closedAt = closedAt; }
    public OffsetDateTime getTuitionSnapshotAt() { return tuitionSnapshotAt; }
    public void setTuitionSnapshotAt(OffsetDateTime tuitionSnapshotAt) { this.tuitionSnapshotAt = tuitionSnapshotAt; }
    public Long getRuleVersionId() { return ruleVersionId; }
    public void setRuleVersionId(Long ruleVersionId) { this.ruleVersionId = ruleVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public byte[] getVersion() { return version == null ? null : version.clone(); }
}
