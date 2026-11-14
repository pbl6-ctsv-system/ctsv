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
@Table(name="chi_tiet_khoa_phong", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class ChiTietKhoaPhong {
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

    @Column(name="ma_phieu_khoa_tham_chieu", nullable=true, length=128)
    private String maPhieuKhoaThamChieu;

    @Column(name="tu_ngay_nguon", nullable=true)
    private LocalDate tuNgayNguon;

    @Column(name="den_ngay_nguon", nullable=true)
    private LocalDate denNgayNguon;

    @Column(name="ma_phong_tham_chieu", nullable=true, length=128)
    private String maPhongThamChieu;

    @Column(name="co_khoa_sang_nguon", nullable=true)
    private Short coKhoaSangNguon;

    @Column(name="co_khoa_chieu_nguon", nullable=true)
    private Short coKhoaChieuNguon;

    @Column(name="co_khoa_toi_nguon", nullable=true)
    private Short coKhoaToiNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public ChiTietKhoaPhong() {}

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
    public String getMaPhieuKhoaThamChieu() { return maPhieuKhoaThamChieu; }
    public void setMaPhieuKhoaThamChieu(String value) { this.maPhieuKhoaThamChieu = value; }
    public LocalDate getTuNgayNguon() { return tuNgayNguon; }
    public void setTuNgayNguon(LocalDate value) { this.tuNgayNguon = value; }
    public LocalDate getDenNgayNguon() { return denNgayNguon; }
    public void setDenNgayNguon(LocalDate value) { this.denNgayNguon = value; }
    public String getMaPhongThamChieu() { return maPhongThamChieu; }
    public void setMaPhongThamChieu(String value) { this.maPhongThamChieu = value; }
    public Short getCoKhoaSangNguon() { return coKhoaSangNguon; }
    public void setCoKhoaSangNguon(Short value) { this.coKhoaSangNguon = value; }
    public Short getCoKhoaChieuNguon() { return coKhoaChieuNguon; }
    public void setCoKhoaChieuNguon(Short value) { this.coKhoaChieuNguon = value; }
    public Short getCoKhoaToiNguon() { return coKhoaToiNguon; }
    public void setCoKhoaToiNguon(Short value) { this.coKhoaToiNguon = value; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
