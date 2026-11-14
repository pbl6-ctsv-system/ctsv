package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_tieu_chi_chinh_sach", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpTieuChiChinhSach {
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

    @Nationalized
    @Column(name="huong_dan_xet", nullable=true, columnDefinition="nvarchar(max)")
    private String huongDanXet;

    @Column(name="bat_buoc", nullable=true)
    private Boolean batBuoc;

    @Column(name="co_dieu_kien_ap_dung", nullable=true)
    private Boolean coDieuKienApDung;

    @Nationalized
    @Column(name="huong_dan_ap_dung", nullable=true, columnDefinition="nvarchar(max)")
    private String huongDanApDung;

    @Column(name="thu_tu_hien_thi", nullable=true)
    private Integer thuTuHienThi;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="phien_ban_chinh_sach_id", insertable=false, updatable=false)
    private MghpPhienBanChinhSach phienBanChinhSach;

    public MghpTieuChiChinhSach() {}

    public Long getId() { return id; }
    public Long getPhienBanChinhSachId() { return phienBanChinhSachId; }
    public void setPhienBanChinhSachId(Long value) { this.phienBanChinhSachId = value; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getHuongDanXet() { return huongDanXet; }
    public void setHuongDanXet(String value) { this.huongDanXet = value; }
    public Boolean getBatBuoc() { return batBuoc; }
    public void setBatBuoc(Boolean value) { this.batBuoc = value; }
    public Boolean getCoDieuKienApDung() { return coDieuKienApDung; }
    public void setCoDieuKienApDung(Boolean value) { this.coDieuKienApDung = value; }
    public String getHuongDanApDung() { return huongDanApDung; }
    public void setHuongDanApDung(String value) { this.huongDanApDung = value; }
    public Integer getThuTuHienThi() { return thuTuHienThi; }
    public void setThuTuHienThi(Integer value) { this.thuTuHienThi = value; }
    public MghpPhienBanChinhSach getPhienBanChinhSach() { return phienBanChinhSach; }
}
