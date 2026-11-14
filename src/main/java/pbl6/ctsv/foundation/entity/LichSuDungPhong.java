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
@Table(name="lich_su_dung_phong", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class LichSuDungPhong {
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

    @Column(name="ma_lop_hoc_phan_nguon", nullable=true, length=128)
    private String maLopHocPhanNguon;

    @Column(name="ma_phong_tham_chieu", nullable=true, length=128)
    private String maPhongThamChieu;

    @Column(name="ngay_su_dung_nguon", nullable=true)
    private LocalDate ngaySuDungNguon;

    @Column(name="tiet_dau_nguon", nullable=true)
    private Short tietDauNguon;

    @Column(name="tiet_cuoi_nguon", nullable=true)
    private Short tietCuoiNguon;

    @Column(name="ma_lich_tham_chieu", nullable=true, length=128)
    private String maLichThamChieu;

    @Column(name="ma_nghi_bu_tham_chieu", nullable=true, length=128)
    private String maNghiBuThamChieu;

    @Column(name="co_an_nguon", nullable=true)
    private Short coAnNguon;

    @Column(name="co_hoc_truc_tuyen_nguon", nullable=true)
    private Short coHocTrucTuyenNguon;

    @Column(name="co_ngay_le_nguon", nullable=true)
    private Short coNgayLeNguon;

    @Column(name="co_lich_thi_nguon", nullable=true)
    private Short coLichThiNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public LichSuDungPhong() {}

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
    public String getMaLopHocPhanNguon() { return maLopHocPhanNguon; }
    public void setMaLopHocPhanNguon(String value) { this.maLopHocPhanNguon = value; }
    public String getMaPhongThamChieu() { return maPhongThamChieu; }
    public void setMaPhongThamChieu(String value) { this.maPhongThamChieu = value; }
    public LocalDate getNgaySuDungNguon() { return ngaySuDungNguon; }
    public void setNgaySuDungNguon(LocalDate value) { this.ngaySuDungNguon = value; }
    public Short getTietDauNguon() { return tietDauNguon; }
    public void setTietDauNguon(Short value) { this.tietDauNguon = value; }
    public Short getTietCuoiNguon() { return tietCuoiNguon; }
    public void setTietCuoiNguon(Short value) { this.tietCuoiNguon = value; }
    public String getMaLichThamChieu() { return maLichThamChieu; }
    public void setMaLichThamChieu(String value) { this.maLichThamChieu = value; }
    public String getMaNghiBuThamChieu() { return maNghiBuThamChieu; }
    public void setMaNghiBuThamChieu(String value) { this.maNghiBuThamChieu = value; }
    public Short getCoAnNguon() { return coAnNguon; }
    public void setCoAnNguon(Short value) { this.coAnNguon = value; }
    public Short getCoHocTrucTuyenNguon() { return coHocTrucTuyenNguon; }
    public void setCoHocTrucTuyenNguon(Short value) { this.coHocTrucTuyenNguon = value; }
    public Short getCoNgayLeNguon() { return coNgayLeNguon; }
    public void setCoNgayLeNguon(Short value) { this.coNgayLeNguon = value; }
    public Short getCoLichThiNguon() { return coLichThiNguon; }
    public void setCoLichThiNguon(Short value) { this.coLichThiNguon = value; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
