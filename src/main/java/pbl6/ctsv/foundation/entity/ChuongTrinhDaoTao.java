package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="chuong_trinh_dao_tao", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class ChuongTrinhDaoTao {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma", nullable=true, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Column(name="nganh_noi_bo_id", nullable=true)
    private Long nganhNoiBoId;

    @Column(name="nganh_quoc_gia_id", nullable=true)
    private Long nganhQuocGiaId;

    @Nationalized
    @Column(name="cap_dao_tao_so_nguon", nullable=true, length=512)
    private String capDaoTaoSoNguon;

    @Column(name="so_tin_chi", nullable=true, precision=28, scale=10)
    private BigDecimal soTinChi;

    @Column(name="so_hoc_ky", nullable=true, precision=28, scale=10)
    private BigDecimal soHocKy;

    @Nationalized
    @Column(name="ngon_ngu_dao_tao", nullable=true, length=128)
    private String ngonNguDaoTao;

    @Nationalized
    @Column(name="bat_dau_nguon", nullable=true, length=512)
    private String batDauNguon;

    @Nationalized
    @Column(name="ket_thuc_nguon", nullable=true, length=512)
    private String ketThucNguon;

    @Column(name="hoc_ky_bat_dau_id", nullable=true)
    private Long hocKyBatDauId;

    @Column(name="hoc_ky_ket_thuc_id", nullable=true)
    private Long hocKyKetThucId;

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

    @Nationalized
    @Column(name="nganh_noi_bo_id_goc_nguon", nullable=true, length=512)
    private String nganhNoiBoIdGocNguon;

    @Nationalized
    @Column(name="nganh_quoc_gia_id_goc_nguon", nullable=true, length=512)
    private String nganhQuocGiaIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_bat_dau_id_goc_nguon", nullable=true, length=512)
    private String hocKyBatDauIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_ket_thuc_id_goc_nguon", nullable=true, length=512)
    private String hocKyKetThucIdGocNguon;

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
    @Column(name="so_tin_chi_goc_nguon", nullable=true, length=512)
    private String soTinChiGocNguon;

    @Nationalized
    @Column(name="so_hoc_ky_goc_nguon", nullable=true, length=512)
    private String soHocKyGocNguon;

    @Nationalized
    @Column(name="ky_hieu_nguon", nullable=true, length=512)
    private String kyHieuNguon;

    @Nationalized
    @Column(name="dang_su_dung_nguon", nullable=true, length=512)
    private String dangSuDungNguon;

    @Nationalized
    @Column(name="cap_dao_tao_nguon", nullable=true, length=512)
    private String capDaoTaoNguon;

    @Nationalized
    @Column(name="ten_nganh_moi_nguon", nullable=true, length=512)
    private String tenNganhMoiNguon;

    @Nationalized
    @Column(name="loai_ctdt_nguon", nullable=true, length=512)
    private String loaiCtdtNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nganh_noi_bo_id", insertable=false, updatable=false)
    private NganhNoiBo nganhNoiBo;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nganh_quoc_gia_id", insertable=false, updatable=false)
    private NganhQuocGia nganhQuocGia;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_bat_dau_id", insertable=false, updatable=false)
    private HocKy hocKyBatDau;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ket_thuc_id", insertable=false, updatable=false)
    private HocKy hocKyKetThuc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public ChuongTrinhDaoTao() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public Long getNganhNoiBoId() { return nganhNoiBoId; }
    public void setNganhNoiBoId(Long value) { this.nganhNoiBoId = value; }
    public Long getNganhQuocGiaId() { return nganhQuocGiaId; }
    public void setNganhQuocGiaId(Long value) { this.nganhQuocGiaId = value; }
    public String getCapDaoTaoSoNguon() { return capDaoTaoSoNguon; }
    public void setCapDaoTaoSoNguon(String value) { this.capDaoTaoSoNguon = value; }
    public BigDecimal getSoTinChi() { return soTinChi; }
    public void setSoTinChi(BigDecimal value) { this.soTinChi = value; }
    public BigDecimal getSoHocKy() { return soHocKy; }
    public void setSoHocKy(BigDecimal value) { this.soHocKy = value; }
    public String getNgonNguDaoTao() { return ngonNguDaoTao; }
    public void setNgonNguDaoTao(String value) { this.ngonNguDaoTao = value; }
    public String getBatDauNguon() { return batDauNguon; }
    public void setBatDauNguon(String value) { this.batDauNguon = value; }
    public String getKetThucNguon() { return ketThucNguon; }
    public void setKetThucNguon(String value) { this.ketThucNguon = value; }
    public Long getHocKyBatDauId() { return hocKyBatDauId; }
    public void setHocKyBatDauId(Long value) { this.hocKyBatDauId = value; }
    public Long getHocKyKetThucId() { return hocKyKetThucId; }
    public void setHocKyKetThucId(Long value) { this.hocKyKetThucId = value; }
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
    public String getNganhNoiBoIdGocNguon() { return nganhNoiBoIdGocNguon; }
    public void setNganhNoiBoIdGocNguon(String value) { this.nganhNoiBoIdGocNguon = value; }
    public String getNganhQuocGiaIdGocNguon() { return nganhQuocGiaIdGocNguon; }
    public void setNganhQuocGiaIdGocNguon(String value) { this.nganhQuocGiaIdGocNguon = value; }
    public String getHocKyBatDauIdGocNguon() { return hocKyBatDauIdGocNguon; }
    public void setHocKyBatDauIdGocNguon(String value) { this.hocKyBatDauIdGocNguon = value; }
    public String getHocKyKetThucIdGocNguon() { return hocKyKetThucIdGocNguon; }
    public void setHocKyKetThucIdGocNguon(String value) { this.hocKyKetThucIdGocNguon = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public String getSoTinChiGocNguon() { return soTinChiGocNguon; }
    public void setSoTinChiGocNguon(String value) { this.soTinChiGocNguon = value; }
    public String getSoHocKyGocNguon() { return soHocKyGocNguon; }
    public void setSoHocKyGocNguon(String value) { this.soHocKyGocNguon = value; }
    public String getKyHieuNguon() { return kyHieuNguon; }
    public void setKyHieuNguon(String value) { this.kyHieuNguon = value; }
    public String getDangSuDungNguon() { return dangSuDungNguon; }
    public void setDangSuDungNguon(String value) { this.dangSuDungNguon = value; }
    public String getCapDaoTaoNguon() { return capDaoTaoNguon; }
    public void setCapDaoTaoNguon(String value) { this.capDaoTaoNguon = value; }
    public String getTenNganhMoiNguon() { return tenNganhMoiNguon; }
    public void setTenNganhMoiNguon(String value) { this.tenNganhMoiNguon = value; }
    public String getLoaiCtdtNguon() { return loaiCtdtNguon; }
    public void setLoaiCtdtNguon(String value) { this.loaiCtdtNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public NganhNoiBo getNganhNoiBo() { return nganhNoiBo; }
    public NganhQuocGia getNganhQuocGia() { return nganhQuocGia; }
    public HocKy getHocKyBatDau() { return hocKyBatDau; }
    public HocKy getHocKyKetThuc() { return hocKyKetThuc; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
