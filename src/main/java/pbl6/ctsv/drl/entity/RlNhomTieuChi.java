package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;

/** Internal DRL mapping for dbo.RLNhomTieuChi; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLNhomTieuChi`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlNhomTieuChi extends DrlEntity {
    @Column(name = "`BieuMauID`", nullable = false)
    private Long bieuMauId;

    @Nationalized
    @Column(name = "`TenNhom`", nullable = false, length = 512)
    private String tenNhom;

    @Column(name = "`ThuTu`", nullable = false)
    private Integer thuTu;

    @Column(name = "`DiemToiDa`", nullable = false, precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemToiDa;

    public Long getBieuMauId() {
        return bieuMauId;
    }

    public void setBieuMauId(Long bieuMauId) {
        this.bieuMauId = bieuMauId;
    }

    public String getTenNhom() {
        return tenNhom;
    }

    public void setTenNhom(String tenNhom) {
        this.tenNhom = tenNhom;
    }

    public Integer getThuTu() {
        return thuTu;
    }

    public void setThuTu(Integer thuTu) {
        this.thuTu = thuTu;
    }

    public BigDecimal getDiemToiDa() {
        return diemToiDa;
    }

    public void setDiemToiDa(BigDecimal diemToiDa) {
        this.diemToiDa = diemToiDa;
    }
}

