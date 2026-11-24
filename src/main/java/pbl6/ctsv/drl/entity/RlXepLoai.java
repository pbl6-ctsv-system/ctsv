package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;

/** Internal DRL mapping for dbo.RLXepLoai; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLXepLoai`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlXepLoai extends DrlEntity {
    @Column(name = "`BieuMauID`", nullable = false)
    private Long bieuMauId;

    @Column(name = "`DiemToiThieu`", nullable = false, precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemToiThieu;

    @Nationalized
    @Column(name = "`TenXepLoai`", nullable = false, length = 64)
    private String tenXepLoai;

    public Long getBieuMauId() {
        return bieuMauId;
    }

    public void setBieuMauId(Long bieuMauId) {
        this.bieuMauId = bieuMauId;
    }

    public BigDecimal getDiemToiThieu() {
        return diemToiThieu;
    }

    public void setDiemToiThieu(BigDecimal diemToiThieu) {
        this.diemToiThieu = diemToiThieu;
    }

    public String getTenXepLoai() {
        return tenXepLoai;
    }

    public void setTenXepLoai(String tenXepLoai) {
        this.tenXepLoai = tenXepLoai;
    }
}

