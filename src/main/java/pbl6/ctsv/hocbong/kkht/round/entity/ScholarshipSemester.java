package pbl6.ctsv.hocbong.kkht.round.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "hb_hoc_ky", schema = "dbo")
public class ScholarshipSemester {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Nationalized
    @Column(name = "nam_hoc", nullable = false, length = 9)
    private String academicYear;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "thu_tu_ky", nullable = false)
    private Integer semesterOrder;

    @Column(name = "ngay_bat_dau", nullable = false)
    private LocalDate startDate;

    @Column(name = "ngay_ket_thuc", nullable = false)
    private LocalDate endDate;

    @Nationalized
    @Column(name = "trang_thai", nullable = false, length = 32)
    private String status;

    public Long getId() { return id; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
    public Integer getSemesterOrder() { return semesterOrder; }
    public void setSemesterOrder(Integer semesterOrder) { this.semesterOrder = semesterOrder; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
