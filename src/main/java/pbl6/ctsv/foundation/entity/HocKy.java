package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="hoc_ky", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class HocKy {
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
    @Column(name="ngay_day_nguon", nullable=true, length=512)
    private String ngayDayNguon;

    @Nationalized
    @Column(name="tuan_dau_nguon", nullable=true, length=512)
    private String tuanDauNguon;

    @Nationalized
    @Column(name="so_tuan_nguon", nullable=true, length=512)
    private String soTuanNguon;

    @Column(name="so_thu_tu", nullable=true)
    private Integer soThuTu;

    @Column(name="ngay_bat_dau", nullable=true)
    private LocalDate ngayBatDau;

    @Column(name="ngay_ket_thuc", nullable=true)
    private LocalDate ngayKetThuc;

    @Nationalized
    @Column(name="trang_thai_xac_minh_moc", nullable=true, length=128)
    private String trangThaiXacMinhMoc;

    @Nationalized
    @Column(name="can_cu_xac_minh_moc", nullable=true, columnDefinition="nvarchar(max)")
    private String canCuXacMinhMoc;

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
    @Column(name="ngay_dau_nguon", nullable=true, length=512)
    private String ngayDauNguon;

    @Nationalized
    @Column(name="ma_nganh_nguon", nullable=true, length=512)
    private String maNganhNguon;

    @Nationalized
    @Column(name="nam_hoc_nguon", nullable=true, length=512)
    private String namHocNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    @Column(name="phien_ban", nullable=false, columnDefinition="rowversion", insertable=false, updatable=false, length=8)
    private byte[] phienBan;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public HocKy() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getNgayDayNguon() { return ngayDayNguon; }
    public void setNgayDayNguon(String value) { this.ngayDayNguon = value; }
    public String getTuanDauNguon() { return tuanDauNguon; }
    public void setTuanDauNguon(String value) { this.tuanDauNguon = value; }
    public String getSoTuanNguon() { return soTuanNguon; }
    public void setSoTuanNguon(String value) { this.soTuanNguon = value; }
    public Integer getSoThuTu() { return soThuTu; }
    public void setSoThuTu(Integer value) { this.soThuTu = value; }
    public LocalDate getNgayBatDau() { return ngayBatDau; }
    public void setNgayBatDau(LocalDate value) { this.ngayBatDau = value; }
    public LocalDate getNgayKetThuc() { return ngayKetThuc; }
    public void setNgayKetThuc(LocalDate value) { this.ngayKetThuc = value; }
    public String getTrangThaiXacMinhMoc() { return trangThaiXacMinhMoc; }
    public void setTrangThaiXacMinhMoc(String value) { this.trangThaiXacMinhMoc = value; }
    public String getCanCuXacMinhMoc() { return canCuXacMinhMoc; }
    public void setCanCuXacMinhMoc(String value) { this.canCuXacMinhMoc = value; }
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
    public String getNgayDauNguon() { return ngayDauNguon; }
    public void setNgayDauNguon(String value) { this.ngayDauNguon = value; }
    public String getMaNganhNguon() { return maNganhNguon; }
    public void setMaNganhNguon(String value) { this.maNganhNguon = value; }
    public String getNamHocNguon() { return namHocNguon; }
    public void setNamHocNguon(String value) { this.namHocNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public byte[] getPhienBan() { return phienBan == null ? null : phienBan.clone(); }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
