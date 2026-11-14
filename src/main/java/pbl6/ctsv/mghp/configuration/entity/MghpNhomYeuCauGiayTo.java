package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_nhom_yeu_cau_giay_to", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpNhomYeuCauGiayTo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="phien_ban_chinh_sach_id", nullable=false)
    private Long phienBanChinhSachId;

    @Nationalized
    @Column(name="ma", nullable=false, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Column(name="so_yeu_cau_toi_thieu_dat", nullable=false)
    private Integer soYeuCauToiThieuDat;

    @Column(name="thu_tu_hien_thi", nullable=true)
    private Integer thuTuHienThi;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="phien_ban_chinh_sach_id", insertable=false, updatable=false)
    private MghpPhienBanChinhSach phienBanChinhSach;

    public MghpNhomYeuCauGiayTo() {}

    public Long getId() { return id; }
    public Long getPhienBanChinhSachId() { return phienBanChinhSachId; }
    public void setPhienBanChinhSachId(Long value) { this.phienBanChinhSachId = value; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public Integer getSoYeuCauToiThieuDat() { return soYeuCauToiThieuDat; }
    public void setSoYeuCauToiThieuDat(Integer value) { this.soYeuCauToiThieuDat = value; }
    public Integer getThuTuHienThi() { return thuTuHienThi; }
    public void setThuTuHienThi(Integer value) { this.thuTuHienThi = value; }
    public MghpPhienBanChinhSach getPhienBanChinhSach() { return phienBanChinhSach; }
}
