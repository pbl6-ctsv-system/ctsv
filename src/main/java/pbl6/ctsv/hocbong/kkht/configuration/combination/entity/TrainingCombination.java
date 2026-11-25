package pbl6.ctsv.hocbong.kkht.configuration.combination.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "hb_to_hop_dao_tao", schema = "dbo")
@DynamicUpdate
public class TrainingCombination {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "chuong_trinh_dao_tao_id", nullable = false)
    private Long programId;
    @Column(name = "khoa_hoc_id", nullable = false)
    private Long cohortId;
    @Column(name = "nganh_noi_bo_id", nullable = false)
    private Long majorId;
    @Nationalized @Column(name = "ma_ctdt", nullable = false, length = 128)
    private String programCode;
    @Nationalized @Column(name = "ten_ctdt", length = 512)
    private String programName;
    @Nationalized @Column(name = "khoa_tuyen_sinh", nullable = false, length = 128)
    private String cohortCode;
    @Nationalized @Column(name = "ma_nganh_tong_quat", nullable = false, length = 128)
    private String majorCode;
    @Nationalized @Column(name = "ma_khung_nguon", nullable = false, length = 512)
    private String curriculumCode;
    @Column(name = "so_ky_chuan", nullable = false)
    private Short standardSemesters;
    @Nationalized @Column(name = "loai_ctdt", nullable = false, length = 2)
    private String programType;
    @JdbcType(SqlServerLocalDateJdbcType.class)
    @Column(name = "hieu_luc_tu", nullable = false)
    private LocalDate effectiveFrom;
    @JdbcType(SqlServerLocalDateJdbcType.class)
    @Column(name = "hieu_luc_den")
    private LocalDate effectiveTo;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "phien_ban_ky_thuat", nullable = false, insertable = false, updatable = false,
            columnDefinition = "rowversion", length = 8)
    private byte[] version;

    public Long getId() { return id; }
    public Long getProgramId() { return programId; }
    public void setProgramId(Long v) { programId = v; }
    public Long getCohortId() { return cohortId; }
    public void setCohortId(Long v) { cohortId = v; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long v) { majorId = v; }
    public String getProgramCode() { return programCode; }
    public void setProgramCode(String v) { programCode = v; }
    public String getProgramName() { return programName; }
    public void setProgramName(String v) { programName = v; }
    public String getCohortCode() { return cohortCode; }
    public void setCohortCode(String v) { cohortCode = v; }
    public String getMajorCode() { return majorCode; }
    public void setMajorCode(String v) { majorCode = v; }
    public String getCurriculumCode() { return curriculumCode; }
    public void setCurriculumCode(String v) { curriculumCode = v; }
    public Short getStandardSemesters() { return standardSemesters; }
    public void setStandardSemesters(Short v) { standardSemesters = v; }
    public String getProgramType() { return programType; }
    public void setProgramType(String v) { programType = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { effectiveFrom = v; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { effectiveTo = v; }
    public byte[] getVersion() { return version == null ? null : version.clone(); }
}
