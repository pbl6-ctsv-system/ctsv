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
@Table(name="khung_chuong_trinh", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class KhungChuongTrinh {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="he_thong_nguon_id", nullable=false)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ma_dinh_danh_ben_ngoai", nullable=false, length=512)
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

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @Nationalized
    @Column(name="ma_khung_nguon", nullable=true, length=128)
    private String maKhungNguon;

    @Column(name="hoc_phan_id", nullable=false)
    private Long hocPhanId;

    @Column(name="hoc_phan_duoc_thay_the_id", nullable=false)
    private Long hocPhanDuocThayTheId;

    @Column(name="hoc_ky_trong_khung_nguon", nullable=true)
    private Short hocKyTrongKhungNguon;

    @Nationalized
    @Column(name="co_tu_chon_nguon", nullable=true, length=128)
    private String coTuChonNguon;

    @Nationalized
    @Column(name="tien_quyet_do_an_nguon", nullable=true, length=128)
    private String tienQuyetDoAnNguon;

    @Nationalized
    @Column(name="phan_loai_tot_nghiep_nguon", nullable=true, length=128)
    private String phanLoaiTotNghiepNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_phan_id", insertable=false, updatable=false)
    private HocPhan hocPhan;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_phan_duoc_thay_the_id", insertable=false, updatable=false)
    private HocPhan hocPhanDuocThayThe;

     //

   //
    public KhungChuongTrinh() {}

    public Long getId() { return id; }
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
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public String getMaKhungNguon() { return maKhungNguon; }
    public void setMaKhungNguon(String value) { this.maKhungNguon = value; }
    public Long getHocPhanId() { return hocPhanId; }
    public void setHocPhanId(Long value) { this.hocPhanId = value; }
    public Long getHocPhanDuocThayTheId() { return hocPhanDuocThayTheId; }
    public void setHocPhanDuocThayTheId(Long value) { this.hocPhanDuocThayTheId = value; }
    public Short getHocKyTrongKhungNguon() { return hocKyTrongKhungNguon; }
    public void setHocKyTrongKhungNguon(Short value) { this.hocKyTrongKhungNguon = value; }
    public String getCoTuChonNguon() { return coTuChonNguon; }
    public void setCoTuChonNguon(String value) { this.coTuChonNguon = value; }
    public String getTienQuyetDoAnNguon() { return tienQuyetDoAnNguon; }
    public void setTienQuyetDoAnNguon(String value) { this.tienQuyetDoAnNguon = value; }
    public String getPhanLoaiTotNghiepNguon() { return phanLoaiTotNghiepNguon; }
    public void setPhanLoaiTotNghiepNguon(String value) { this.phanLoaiTotNghiepNguon = value; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
    public HocPhan getHocPhan() { return hocPhan; }
    public HocPhan getHocPhanDuocThayThe() { return hocPhanDuocThayThe; }
}
