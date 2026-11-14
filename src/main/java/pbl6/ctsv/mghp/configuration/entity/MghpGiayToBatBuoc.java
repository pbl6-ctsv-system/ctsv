package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_giay_to_bat_buoc", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpGiayToBatBuoc {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="nhom_id", nullable=false)
    private Long nhomId;

    @Nationalized
    @Column(name="ma", nullable=false, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Nationalized
    @Column(name="loai_giay_to", nullable=true, length=128)
    private String loaiGiayTo;

    @Column(name="can_doi_chieu_ban_giay", nullable=true)
    private Boolean canDoiChieuBanGiay;

    @Column(name="tuoi_toi_da_giay_to_theo_ngay", nullable=true)
    private Integer tuoiToiDaGiayToTheoNgay;

    @Column(name="thu_tu_hien_thi", nullable=true)
    private Integer thuTuHienThi;

    @Column(name="can_ban_sao_chung_thuc", nullable=true)
    private Boolean canBanSaoChungThuc;

    @Column(name="can_dau_xac_nhan", nullable=true)
    private Boolean canDauXacNhan;

    @Nationalized
    @Column(name="noi_dung_xac_nhan", nullable=true, length=512)
    private String noiDungXacNhan;

    @Nationalized
    @Column(name="co_quan_xac_nhan", nullable=true, length=512)
    private String coQuanXacNhan;

    @Nationalized
    @Column(name="huong_dan_kiem_tra", nullable=true, columnDefinition="nvarchar(max)")
    private String huongDanKiemTra;

    @Nationalized
    @Column(name="chu_the_minh_chung", nullable=true, length=128)
    private String chuTheMinhChung;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nhom_id", insertable=false, updatable=false)
    private MghpNhomYeuCauGiayTo nhom;

    public MghpGiayToBatBuoc() {}

    public Long getId() { return id; }
    public Long getNhomId() { return nhomId; }
    public void setNhomId(Long value) { this.nhomId = value; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getLoaiGiayTo() { return loaiGiayTo; }
    public void setLoaiGiayTo(String value) { this.loaiGiayTo = value; }
    public Boolean getCanDoiChieuBanGiay() { return canDoiChieuBanGiay; }
    public void setCanDoiChieuBanGiay(Boolean value) { this.canDoiChieuBanGiay = value; }
    public Integer getTuoiToiDaGiayToTheoNgay() { return tuoiToiDaGiayToTheoNgay; }
    public void setTuoiToiDaGiayToTheoNgay(Integer value) { this.tuoiToiDaGiayToTheoNgay = value; }
    public Integer getThuTuHienThi() { return thuTuHienThi; }
    public void setThuTuHienThi(Integer value) { this.thuTuHienThi = value; }
    public Boolean getCanBanSaoChungThuc() { return canBanSaoChungThuc; }
    public void setCanBanSaoChungThuc(Boolean value) { this.canBanSaoChungThuc = value; }
    public Boolean getCanDauXacNhan() { return canDauXacNhan; }
    public void setCanDauXacNhan(Boolean value) { this.canDauXacNhan = value; }
    public String getNoiDungXacNhan() { return noiDungXacNhan; }
    public void setNoiDungXacNhan(String value) { this.noiDungXacNhan = value; }
    public String getCoQuanXacNhan() { return coQuanXacNhan; }
    public void setCoQuanXacNhan(String value) { this.coQuanXacNhan = value; }
    public String getHuongDanKiemTra() { return huongDanKiemTra; }
    public void setHuongDanKiemTra(String value) { this.huongDanKiemTra = value; }
    public String getChuTheMinhChung() { return chuTheMinhChung; }
    public void setChuTheMinhChung(String value) { this.chuTheMinhChung = value; }
    public MghpNhomYeuCauGiayTo getNhom() { return nhom; }
}
