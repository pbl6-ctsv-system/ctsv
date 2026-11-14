package pbl6.ctsv.foundation.units.entity;

import jakarta.persistence.*;
import pbl6.ctsv.foundation.entity.HeThongNgoai;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="don_vi", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class DonVi {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma", nullable=false, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Nationalized
    @Column(name="ma_bo_phan_nguon", nullable=true, length=512)
    private String maBoPhanNguon;

    @Nationalized
    @Column(name="loai_don_vi", nullable=true, length=128)
    private String loaiDonVi;

    @Column(name="don_vi_cha_id", nullable=true)
    private Long donViChaId;

    @Column(name="dang_hoat_dong", nullable=true)
    private Boolean dangHoatDong;

    @Column(name="he_thong_nguon_id", nullable=true)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ma_dinh_danh_ben_ngoai", nullable=true, length=128)
    private String maDinhDanhBenNgoai;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat_nguon", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhatNguon;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dong_bo", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemDongBo;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;

    @Column(name="sha256_ban_ghi_nguon", nullable=true, length=64)
    private String sha256BanGhiNguon;

    @Nationalized
    @Column(name="phien_ban_anh_xa", nullable=true, length=128)
    private String phienBanAnhXa;

    @Nationalized
    @Column(name="trang_thai_chuan_hoa", nullable=true, length=128)
    private String trangThaiChuanHoa;

    @Nationalized
    @Column(name="loi_chuan_hoa", nullable=true, columnDefinition="nvarchar(max)")
    private String loiChuanHoa;

    @Nationalized
    @Column(name="luu_tru_nguon", nullable=true, length=512)
    private String luuTruNguon;

    @Nationalized
    @Column(name="is_us_nguon", nullable=true, length=512)
    private String isUsNguon;

    @Column(name="dang_hoat_dong_nguon", nullable=true)
    private Boolean dangHoatDongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="don_vi_cha_id", insertable=false, updatable=false)
    private DonVi donViCha;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public DonVi() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getMaBoPhanNguon() { return maBoPhanNguon; }
    public void setMaBoPhanNguon(String value) { this.maBoPhanNguon = value; }
    public String getLoaiDonVi() { return loaiDonVi; }
    public void setLoaiDonVi(String value) { this.loaiDonVi = value; }
    public Long getDonViChaId() { return donViChaId; }
    public void setDonViChaId(Long value) { this.donViChaId = value; }
    public Boolean getDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(Boolean value) { this.dangHoatDong = value; }
    public Long getHeThongNguonId() { return heThongNguonId; }
    public void setHeThongNguonId(Long value) { this.heThongNguonId = value; }
    public String getMaDinhDanhBenNgoai() { return maDinhDanhBenNgoai; }
    public void setMaDinhDanhBenNgoai(String value) { this.maDinhDanhBenNgoai = value; }
    public OffsetDateTime getThoiDiemCapNhatNguon() { return thoiDiemCapNhatNguon; }
    public void setThoiDiemCapNhatNguon(OffsetDateTime value) { this.thoiDiemCapNhatNguon = value; }
    public OffsetDateTime getThoiDiemDongBo() { return thoiDiemDongBo; }
    public void setThoiDiemDongBo(OffsetDateTime value) { this.thoiDiemDongBo = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public String getLuuTruNguon() { return luuTruNguon; }
    public void setLuuTruNguon(String value) { this.luuTruNguon = value; }
    public String getIsUsNguon() { return isUsNguon; }
    public void setIsUsNguon(String value) { this.isUsNguon = value; }
    public Boolean getDangHoatDongNguon() { return dangHoatDongNguon; }
    public void setDangHoatDongNguon(Boolean value) { this.dangHoatDongNguon = value; }
    public DonVi getDonViCha() { return donViCha; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
