package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

/** Internal DRL mapping for dbo.RLPhieuDiemCT; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLPhieuDiemCT`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlPhieuDiemCt extends DrlEntity {
    @Column(name = "`PhieuID`", nullable = false)
    private Long phieuId;

    @Column(name = "`BieuMauCTID`", nullable = false)
    private Long bieuMauCtId;

    @Column(name = "`DiemCN`", precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemCn;

    @Column(name = "`DiemLop`", precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemLop;

    public Long getPhieuId() {
        return phieuId;
    }

    public void setPhieuId(Long phieuId) {
        this.phieuId = phieuId;
    }

    public Long getBieuMauCtId() {
        return bieuMauCtId;
    }

    public void setBieuMauCtId(Long bieuMauCtId) {
        this.bieuMauCtId = bieuMauCtId;
    }

    public BigDecimal getDiemCn() {
        return diemCn;
    }

    public void setDiemCn(BigDecimal diemCn) {
        this.diemCn = diemCn;
    }

    public BigDecimal getDiemLop() {
        return diemLop;
    }

    public void setDiemLop(BigDecimal diemLop) {
        this.diemLop = diemLop;
    }
}

