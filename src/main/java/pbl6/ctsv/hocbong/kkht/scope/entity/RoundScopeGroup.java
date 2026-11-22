package pbl6.ctsv.hocbong.kkht.scope.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Append-only HB scope snapshot; scalar FKs keep Foundation entities behind its boundary. */
@Entity
@Table(name="kkht_nhom_pham_vi", schema="dbo")
public class RoundScopeGroup {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="pham_vi_id", nullable=false)
    private Long scopeId;
    @Column(name="khoa_hoc_id", nullable=false)
    private Long cohortId;
    @Column(name="nganh_noi_bo_id", nullable=false)
    private Long majorId;
    @Nationalized
    @Column(name="ma_khoa_snapshot", nullable=true, length=128)
    private String cohortCode;
    @Nationalized
    @Column(name="ma_nganh_snapshot", nullable=true, length=128)
    private String majorCode;
    @Nationalized
    @Column(name="ten_nganh_snapshot", nullable=true, length=512)
    private String majorName;
    @Column(name="so_sv_nguon", nullable=false)
    private Integer studentCount;
    public Long getId() { return id; }
    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long value) { this.scopeId=value; }
    public Long getCohortId() { return cohortId; }
    public void setCohortId(Long value) { this.cohortId=value; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long value) { this.majorId=value; }
    public String getCohortCode() { return cohortCode; }
    public void setCohortCode(String value) { this.cohortCode=value; }
    public String getMajorCode() { return majorCode; }
    public void setMajorCode(String value) { this.majorCode=value; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String value) { this.majorName=value; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer value) { this.studentCount=value; }
}
