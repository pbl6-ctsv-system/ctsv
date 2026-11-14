package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="nganh_noi_bo", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class NganhNoiBo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma", nullable=true, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Column(name="nganh_quoc_gia_id", nullable=true)
    private Long nganhQuocGiaId;

    @Nationalized
    @Column(name="cap_dao_tao_nguon", nullable=true, length=512)
    private String capDaoTaoNguon;

    @Nationalized
    @Column(name="khoi_nganh_nguon", nullable=true, length=512)
    private String khoiNganhNguon;

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
    @Column(name="nganh_quoc_gia_id_goc_nguon", nullable=true, length=512)
    private String nganhQuocGiaIdGocNguon;

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
    @Column(name="trang_thai_huy_nguon", nullable=true, length=512)
    private String trangThaiHuyNguon;

    @Nationalized
    @Column(name="ky_hieu_nguon", nullable=true, length=512)
    private String kyHieuNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nganh_quoc_gia_id", insertable=false, updatable=false)
    private NganhQuocGia nganhQuocGia;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public NganhNoiBo() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public Long getNganhQuocGiaId() { return nganhQuocGiaId; }
    public void setNganhQuocGiaId(Long value) { this.nganhQuocGiaId = value; }
    public String getCapDaoTaoNguon() { return capDaoTaoNguon; }
    public void setCapDaoTaoNguon(String value) { this.capDaoTaoNguon = value; }
    public String getKhoiNganhNguon() { return khoiNganhNguon; }
    public void setKhoiNganhNguon(String value) { this.khoiNganhNguon = value; }
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
    public String getNganhQuocGiaIdGocNguon() { return nganhQuocGiaIdGocNguon; }
    public void setNganhQuocGiaIdGocNguon(String value) { this.nganhQuocGiaIdGocNguon = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public String getTrangThaiHuyNguon() { return trangThaiHuyNguon; }
    public void setTrangThaiHuyNguon(String value) { this.trangThaiHuyNguon = value; }
    public String getKyHieuNguon() { return kyHieuNguon; }
    public void setKyHieuNguon(String value) { this.kyHieuNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public NganhQuocGia getNganhQuocGia() { return nganhQuocGia; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
