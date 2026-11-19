package pbl6.ctsv.hocbong.kkht.round.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;

@Entity
@Table(name = "hb_anh_xa_ky_nguon", schema = "dbo")
public class SourceSemesterMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Nationalized
    @Column(name = "he_thong_nguon", nullable = false, length = 128)
    private String sourceSystem;

    @Nationalized
    @Column(name = "loai_ma", nullable = false, length = 128)
    private String sourceKind;

    @Nationalized
    @Column(name = "ma_ky_nguon", nullable = false, length = 128)
    private String sourceSemesterCode;

    @Nationalized
    @Column(name = "ma_dot_nguon", length = 128)
    private String sourceRoundCode;

    @Column(name = "hoc_ky_id", nullable = false)
    private Long semesterId;

    @Column(name = "xac_nhan_boi", nullable = false)
    private Long confirmedBy;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "xac_nhan_luc", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime confirmedAt;

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }
    public String getSourceSemesterCode() { return sourceSemesterCode; }
    public void setSourceSemesterCode(String sourceSemesterCode) { this.sourceSemesterCode = sourceSemesterCode; }
    public String getSourceRoundCode() { return sourceRoundCode; }
    public void setSourceRoundCode(String sourceRoundCode) { this.sourceRoundCode = sourceRoundCode; }
    public Long getSemesterId() { return semesterId; }
    public void setSemesterId(Long semesterId) { this.semesterId = semesterId; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
