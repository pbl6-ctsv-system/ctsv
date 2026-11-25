package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="diem_mien", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class DiemMien {
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

    @Column(name="hoc_ky_id", nullable=false)
    private Long hocKyId;

    @Column(name="hoc_phan_id", nullable=false)
    private Long hocPhanId;

    @Column(name="sinh_vien_id", nullable=false)
    private Long sinhVienId;

    @Column(name="diem_nguon", nullable=true, precision=28, scale=10)
    private BigDecimal diemNguon;

    @Nationalized
    @Column(name="loai_diem_nguon", nullable=true, length=128)
    private String loaiDiemNguon;

    @Column(name="ngay_cong_nhan_nguon", nullable=true)
    private LocalDate ngayCongNhanNguon;

    @Nationalized
    @Column(name="ghi_chu_nguon", nullable=true, length=2000)
    private String ghiChuNguon;

    @Column(name="hoc_phan_tuong_duong_id", nullable=true)
    private Long hocPhanTuongDuongId;

    @Nationalized
    @Column(name="co_hoc_lai_nguon", nullable=true, length=128)
    private String coHocLaiNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_id", insertable=false, updatable=false)
    private HocKy hocKy;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_phan_id", insertable=false, updatable=false)
    private HocPhan hocPhan;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="sinh_vien_id", insertable=false, updatable=false)
    private SinhVien sinhVien;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_phan_tuong_duong_id", insertable=false, updatable=false)
    private HocPhan hocPhanTuongDuong;

    public DiemMien() {}

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
    public Long getHocKyId() { return hocKyId; }
    public void setHocKyId(Long value) { this.hocKyId = value; }
    public Long getHocPhanId() { return hocPhanId; }
    public void setHocPhanId(Long value) { this.hocPhanId = value; }
    public Long getSinhVienId() { return sinhVienId; }
    public void setSinhVienId(Long value) { this.sinhVienId = value; }
    public BigDecimal getDiemNguon() { return diemNguon; }
    public void setDiemNguon(BigDecimal value) { this.diemNguon = value; }
    public String getLoaiDiemNguon() { return loaiDiemNguon; }
    public void setLoaiDiemNguon(String value) { this.loaiDiemNguon = value; }
    public LocalDate getNgayCongNhanNguon() { return ngayCongNhanNguon; }
    public void setNgayCongNhanNguon(LocalDate value) { this.ngayCongNhanNguon = value; }
    public String getGhiChuNguon() { return ghiChuNguon; }
    public void setGhiChuNguon(String value) { this.ghiChuNguon = value; }
    public Long getHocPhanTuongDuongId() { return hocPhanTuongDuongId; }
    public void setHocPhanTuongDuongId(Long value) { this.hocPhanTuongDuongId = value; }
    public String getCoHocLaiNguon() { return coHocLaiNguon; }
    public void setCoHocLaiNguon(String value) { this.coHocLaiNguon = value; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
    public HocKy getHocKy() { return hocKy; }
    public HocPhan getHocPhan() { return hocPhan; }
    public SinhVien getSinhVien() { return sinhVien; }
    public HocPhan getHocPhanTuongDuong() { return hocPhanTuongDuong; }
}
