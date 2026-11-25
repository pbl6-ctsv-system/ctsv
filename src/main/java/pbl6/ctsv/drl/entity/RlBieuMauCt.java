package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

/** Internal DRL mapping for dbo.RLBieuMauCT; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLBieuMauCT`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlBieuMauCt extends DrlEntity {
    @Column(name = "`NhomTieuChiID`", nullable = false)
    private Long nhomTieuChiId;

    @Nationalized
    @Column(name = "`MaNoiDung`", nullable = false, length = 64)
    private String maNoiDung;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "`LoaiDong`", nullable = false, columnDefinition = "tinyint")
    private Short loaiDong;

    @Column(name = "`TieuChiChaID`")
    private Long tieuChiChaId;

    @Nationalized
    @Column(name = "`NoiDung`", nullable = false, columnDefinition = "nvarchar(max)")
    private String noiDung;

    @Column(name = "`ThuTu`", nullable = false)
    private Integer thuTu;

    @Nationalized
    @Column(name = "`MucLuc`", length = 32)
    private String mucLuc;

    @Column(name = "`DiemToiDa`", precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal diemToiDa;

    public Long getNhomTieuChiId() {
        return nhomTieuChiId;
    }

    public void setNhomTieuChiId(Long nhomTieuChiId) {
        this.nhomTieuChiId = nhomTieuChiId;
    }

    public String getMaNoiDung() {
        return maNoiDung;
    }

    public void setMaNoiDung(String maNoiDung) {
        this.maNoiDung = maNoiDung;
    }

    public Short getLoaiDong() {
        return loaiDong;
    }

    public void setLoaiDong(Short loaiDong) {
        this.loaiDong = loaiDong;
    }

    public Long getTieuChiChaId() {
        return tieuChiChaId;
    }

    public void setTieuChiChaId(Long tieuChiChaId) {
        this.tieuChiChaId = tieuChiChaId;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public void setNoiDung(String noiDung) {
        this.noiDung = noiDung;
    }

    public Integer getThuTu() {
        return thuTu;
    }

    public void setThuTu(Integer thuTu) {
        this.thuTu = thuTu;
    }

    public String getMucLuc() {
        return mucLuc;
    }

    public void setMucLuc(String mucLuc) {
        this.mucLuc = mucLuc;
    }

    public BigDecimal getDiemToiDa() {
        return diemToiDa;
    }

    public void setDiemToiDa(BigDecimal diemToiDa) {
        this.diemToiDa = diemToiDa;
    }
}

