package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;

/** Internal DRL mapping for dbo.RLBieuMau; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLBieuMau`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlBieuMau extends DrlEntity {
    @Nationalized
    @Column(name = "`MaBieuMau`", nullable = false, length = 64)
    private String maBieuMau;

    @Column(name = "`SoPhienBan`", nullable = false)
    private Integer soPhienBan;

    @Nationalized
    @Column(name = "`TenBieuMau`", nullable = false, length = 512)
    private String tenBieuMau;

    @Column(name = "`DiemToiDa`", nullable = false, precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemToiDa;

    @Nationalized
    @Column(name = "`HuongDanChung`", columnDefinition = "nvarchar(max)")
    private String huongDanChung;

    public String getMaBieuMau() {
        return maBieuMau;
    }

    public void setMaBieuMau(String maBieuMau) {
        this.maBieuMau = maBieuMau;
    }

    public Integer getSoPhienBan() {
        return soPhienBan;
    }

    public void setSoPhienBan(Integer soPhienBan) {
        this.soPhienBan = soPhienBan;
    }

    public String getTenBieuMau() {
        return tenBieuMau;
    }

    public void setTenBieuMau(String tenBieuMau) {
        this.tenBieuMau = tenBieuMau;
    }

    public BigDecimal getDiemToiDa() {
        return diemToiDa;
    }

    public void setDiemToiDa(BigDecimal diemToiDa) {
        this.diemToiDa = diemToiDa;
    }

    public String getHuongDanChung() {
        return huongDanChung;
    }

    public void setHuongDanChung(String huongDanChung) {
        this.huongDanChung = huongDanChung;
    }
}

