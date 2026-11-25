package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Internal DRL mapping for dbo.RLVanBanKy; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLVanBanKy`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlVanBanKy extends DrlEntity {
    @Column(name = "`LopDotID`", nullable = false)
    private Long lopDotId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "`LoaiVanBan`", nullable = false, columnDefinition = "tinyint")
    private Short loaiVanBan;

    @Column(name = "`PhienBanVanBanID`", nullable = false)
    private Long phienBanVanBanId;

    public Long getLopDotId() {
        return lopDotId;
    }

    public void setLopDotId(Long lopDotId) {
        this.lopDotId = lopDotId;
    }

    public Short getLoaiVanBan() {
        return loaiVanBan;
    }

    public void setLoaiVanBan(Short loaiVanBan) {
        this.loaiVanBan = loaiVanBan;
    }

    public Long getPhienBanVanBanId() {
        return phienBanVanBanId;
    }

    public void setPhienBanVanBanId(Long phienBanVanBanId) {
        this.phienBanVanBanId = phienBanVanBanId;
    }
}

