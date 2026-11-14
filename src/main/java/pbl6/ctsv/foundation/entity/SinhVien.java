package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;
import pbl6.ctsv.foundation.units.entity.DonVi;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="sinh_vien", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class SinhVien {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="nguoi_dung_id", nullable=true)
    private Long nguoiDungId;

    @Nationalized
    @Column(name="mssv", nullable=false, length=64)
    private String mssv;

    @Nationalized
    @Column(name="ho_ten", nullable=true, length=512)
    private String hoTen;

    @Nationalized
    @Column(name="dia_chi_email", nullable=true, length=254)
    private String diaChiEmail;

    @Nationalized
    @Column(name="so_dien_thoai", nullable=true, length=64)
    private String soDienThoai;

    @Column(name="ngay_sinh", nullable=true)
    private LocalDate ngaySinh;

    @Nationalized
    @Column(name="gioi_tinh", nullable=true, length=128)
    private String gioiTinh;

    @Column(name="noi_sinh_id", nullable=true)
    private Long noiSinhId;

    @Column(name="dan_toc_id", nullable=true)
    private Long danTocId;

    @Column(name="quoc_tich_id", nullable=true)
    private Long quocTichId;

    @Nationalized
    @Column(name="so_cccd", nullable=true, length=64)
    private String soCccd;

    @Column(name="lop_hien_tai_id", nullable=true)
    private Long lopHienTaiId;

    @Column(name="khoa_quan_ly_hien_tai_id", nullable=true)
    private Long khoaQuanLyHienTaiId;

    @Column(name="khoa_hoc_hien_tai_id", nullable=true)
    private Long khoaHocHienTaiId;

    @Column(name="chuong_trinh_hien_tai_id", nullable=true)
    private Long chuongTrinhHienTaiId;

    @Nationalized
    @Column(name="trang_thai_hoc_tap_nguon", nullable=true, length=512)
    private String trangThaiHocTapNguon;

    @Nationalized
    @Column(name="cap_dao_tao_nguon", nullable=true, length=512)
    private String capDaoTaoNguon;

    @Column(name="hoc_ky_ap_dung_nguon_id", nullable=true)
    private Long hocKyApDungNguonId;

    @Column(name="hoc_ky_bat_dau_nguon_id", nullable=true)
    private Long hocKyBatDauNguonId;

    @Column(name="hoc_ky_ket_thuc_nguon_id", nullable=true)
    private Long hocKyKetThucNguonId;

    @Nationalized
    @Column(name="ton_giao", nullable=true, length=128)
    private String tonGiao;

    @Nationalized
    @Column(name="dia_chi_thuong_tru", nullable=true, columnDefinition="nvarchar(max)")
    private String diaChiThuongTru;

    @Nationalized
    @Column(name="dia_chi_tam_tru", nullable=true, columnDefinition="nvarchar(max)")
    private String diaChiTamTru;

    @Nationalized
    @Column(name="ten_ngan_hang", nullable=true, length=512)
    private String tenNganHang;

    @Nationalized
    @Column(name="so_tai_khoan_nh", nullable=true, length=64)
    private String soTaiKhoanNh;

    @Nationalized
    @Column(name="chu_tai_khoan_nh", nullable=true, length=128)
    private String chuTaiKhoanNh;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_xoa", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemXoa;

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
    @Column(name="noi_sinh_id_goc_nguon", nullable=true, length=512)
    private String noiSinhIdGocNguon;

    @Nationalized
    @Column(name="dan_toc_id_goc_nguon", nullable=true, length=512)
    private String danTocIdGocNguon;

    @Nationalized
    @Column(name="quoc_tich_id_goc_nguon", nullable=true, length=512)
    private String quocTichIdGocNguon;

    @Nationalized
    @Column(name="lop_hien_tai_id_goc_nguon", nullable=true, length=512)
    private String lopHienTaiIdGocNguon;

    @Nationalized
    @Column(name="khoa_quan_ly_hien_tai_id_goc_nguon", nullable=true, length=512)
    private String khoaQuanLyHienTaiIdGocNguon;

    @Nationalized
    @Column(name="khoa_hoc_hien_tai_id_goc_nguon", nullable=true, length=512)
    private String khoaHocHienTaiIdGocNguon;

    @Nationalized
    @Column(name="chuong_trinh_hien_tai_id_goc_nguon", nullable=true, length=512)
    private String chuongTrinhHienTaiIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_ap_dung_nguon_id_goc_nguon", nullable=true, length=512)
    private String hocKyApDungNguonIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_bat_dau_nguon_id_goc_nguon", nullable=true, length=512)
    private String hocKyBatDauNguonIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_ket_thuc_nguon_id_goc_nguon", nullable=true, length=512)
    private String hocKyKetThucNguonIdGocNguon;

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

    @Column(name="lan_sua_doi_danh_ba", nullable=true)
    private Long lanSuaDoiDanhBa;

    @Nationalized
    @Column(name="ma_ho_so_nguon", nullable=true, length=512)
    private String maHoSoNguon;

    @Nationalized
    @Column(name="ngay_nhap_hoc_nguon", nullable=true, length=512)
    private String ngayNhapHocNguon;

    @Nationalized
    @Column(name="he_dao_tao_nguon", nullable=true, length=512)
    private String heDaoTaoNguon;

    @Nationalized
    @Column(name="ngay_luu_nguon", nullable=true, length=512)
    private String ngayLuuNguon;

    @Nationalized
    @Column(name="loai_luu_nguon", nullable=true, length=512)
    private String loaiLuuNguon;

    @Column(name="hoc_ky_han_hoc_nguon_id", nullable=true)
    private Long hocKyHanHocNguonId;

    @Nationalized
    @Column(name="hoc_ky_han_hoc_nguon_id_goc_nguon", nullable=true, length=512)
    private String hocKyHanHocNguonIdGocNguon;

    @Nationalized
    @Column(name="clc_nguon", nullable=true, length=512)
    private String clcNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_dung_id", insertable=false, updatable=false)
    private NguoiDung nguoiDung;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="noi_sinh_id", insertable=false, updatable=false)
    private DiaPhuong noiSinh;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="dan_toc_id", insertable=false, updatable=false)
    private DanToc danToc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="quoc_tich_id", insertable=false, updatable=false)
    private QuocTich quocTich;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="lop_hien_tai_id", insertable=false, updatable=false)
    private LopSinhHoat lopHienTai;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="khoa_quan_ly_hien_tai_id", insertable=false, updatable=false)
    private DonVi khoaQuanLyHienTai;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="khoa_hoc_hien_tai_id", insertable=false, updatable=false)
    private KhoaHoc khoaHocHienTai;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chuong_trinh_hien_tai_id", insertable=false, updatable=false)
    private ChuongTrinhDaoTao chuongTrinhHienTai;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ap_dung_nguon_id", insertable=false, updatable=false)
    private HocKy hocKyApDungNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_bat_dau_nguon_id", insertable=false, updatable=false)
    private HocKy hocKyBatDauNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ket_thuc_nguon_id", insertable=false, updatable=false)
    private HocKy hocKyKetThucNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_han_hoc_nguon_id", insertable=false, updatable=false)
    private HocKy hocKyHanHocNguon;

    public SinhVien() {}

    public Long getId() { return id; }
    public Long getNguoiDungId() { return nguoiDungId; }
    public void setNguoiDungId(Long value) { this.nguoiDungId = value; }
    public String getMssv() { return mssv; }
    public void setMssv(String value) { this.mssv = value; }
    public String getHoTen() { return hoTen; }
    public void setHoTen(String value) { this.hoTen = value; }
    public String getDiaChiEmail() { return diaChiEmail; }
    public void setDiaChiEmail(String value) { this.diaChiEmail = value; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String value) { this.soDienThoai = value; }
    public LocalDate getNgaySinh() { return ngaySinh; }
    public void setNgaySinh(LocalDate value) { this.ngaySinh = value; }
    public String getGioiTinh() { return gioiTinh; }
    public void setGioiTinh(String value) { this.gioiTinh = value; }
    public Long getNoiSinhId() { return noiSinhId; }
    public void setNoiSinhId(Long value) { this.noiSinhId = value; }
    public Long getDanTocId() { return danTocId; }
    public void setDanTocId(Long value) { this.danTocId = value; }
    public Long getQuocTichId() { return quocTichId; }
    public void setQuocTichId(Long value) { this.quocTichId = value; }
    public String getSoCccd() { return soCccd; }
    public void setSoCccd(String value) { this.soCccd = value; }
    public Long getLopHienTaiId() { return lopHienTaiId; }
    public void setLopHienTaiId(Long value) { this.lopHienTaiId = value; }
    public Long getKhoaQuanLyHienTaiId() { return khoaQuanLyHienTaiId; }
    public void setKhoaQuanLyHienTaiId(Long value) { this.khoaQuanLyHienTaiId = value; }
    public Long getKhoaHocHienTaiId() { return khoaHocHienTaiId; }
    public void setKhoaHocHienTaiId(Long value) { this.khoaHocHienTaiId = value; }
    public Long getChuongTrinhHienTaiId() { return chuongTrinhHienTaiId; }
    public void setChuongTrinhHienTaiId(Long value) { this.chuongTrinhHienTaiId = value; }
    public String getTrangThaiHocTapNguon() { return trangThaiHocTapNguon; }
    public void setTrangThaiHocTapNguon(String value) { this.trangThaiHocTapNguon = value; }
    public String getCapDaoTaoNguon() { return capDaoTaoNguon; }
    public void setCapDaoTaoNguon(String value) { this.capDaoTaoNguon = value; }
    public Long getHocKyApDungNguonId() { return hocKyApDungNguonId; }
    public void setHocKyApDungNguonId(Long value) { this.hocKyApDungNguonId = value; }
    public Long getHocKyBatDauNguonId() { return hocKyBatDauNguonId; }
    public void setHocKyBatDauNguonId(Long value) { this.hocKyBatDauNguonId = value; }
    public Long getHocKyKetThucNguonId() { return hocKyKetThucNguonId; }
    public void setHocKyKetThucNguonId(Long value) { this.hocKyKetThucNguonId = value; }
    public String getTonGiao() { return tonGiao; }
    public void setTonGiao(String value) { this.tonGiao = value; }
    public String getDiaChiThuongTru() { return diaChiThuongTru; }
    public void setDiaChiThuongTru(String value) { this.diaChiThuongTru = value; }
    public String getDiaChiTamTru() { return diaChiTamTru; }
    public void setDiaChiTamTru(String value) { this.diaChiTamTru = value; }
    public String getTenNganHang() { return tenNganHang; }
    public void setTenNganHang(String value) { this.tenNganHang = value; }
    public String getSoTaiKhoanNh() { return soTaiKhoanNh; }
    public void setSoTaiKhoanNh(String value) { this.soTaiKhoanNh = value; }
    public String getChuTaiKhoanNh() { return chuTaiKhoanNh; }
    public void setChuTaiKhoanNh(String value) { this.chuTaiKhoanNh = value; }
    public OffsetDateTime getThoiDiemXoa() { return thoiDiemXoa; }
    public void setThoiDiemXoa(OffsetDateTime value) { this.thoiDiemXoa = value; }
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
    public String getNoiSinhIdGocNguon() { return noiSinhIdGocNguon; }
    public void setNoiSinhIdGocNguon(String value) { this.noiSinhIdGocNguon = value; }
    public String getDanTocIdGocNguon() { return danTocIdGocNguon; }
    public void setDanTocIdGocNguon(String value) { this.danTocIdGocNguon = value; }
    public String getQuocTichIdGocNguon() { return quocTichIdGocNguon; }
    public void setQuocTichIdGocNguon(String value) { this.quocTichIdGocNguon = value; }
    public String getLopHienTaiIdGocNguon() { return lopHienTaiIdGocNguon; }
    public void setLopHienTaiIdGocNguon(String value) { this.lopHienTaiIdGocNguon = value; }
    public String getKhoaQuanLyHienTaiIdGocNguon() { return khoaQuanLyHienTaiIdGocNguon; }
    public void setKhoaQuanLyHienTaiIdGocNguon(String value) { this.khoaQuanLyHienTaiIdGocNguon = value; }
    public String getKhoaHocHienTaiIdGocNguon() { return khoaHocHienTaiIdGocNguon; }
    public void setKhoaHocHienTaiIdGocNguon(String value) { this.khoaHocHienTaiIdGocNguon = value; }
    public String getChuongTrinhHienTaiIdGocNguon() { return chuongTrinhHienTaiIdGocNguon; }
    public void setChuongTrinhHienTaiIdGocNguon(String value) { this.chuongTrinhHienTaiIdGocNguon = value; }
    public String getHocKyApDungNguonIdGocNguon() { return hocKyApDungNguonIdGocNguon; }
    public void setHocKyApDungNguonIdGocNguon(String value) { this.hocKyApDungNguonIdGocNguon = value; }
    public String getHocKyBatDauNguonIdGocNguon() { return hocKyBatDauNguonIdGocNguon; }
    public void setHocKyBatDauNguonIdGocNguon(String value) { this.hocKyBatDauNguonIdGocNguon = value; }
    public String getHocKyKetThucNguonIdGocNguon() { return hocKyKetThucNguonIdGocNguon; }
    public void setHocKyKetThucNguonIdGocNguon(String value) { this.hocKyKetThucNguonIdGocNguon = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public Long getLanSuaDoiDanhBa() { return lanSuaDoiDanhBa; }
    public void setLanSuaDoiDanhBa(Long value) { this.lanSuaDoiDanhBa = value; }
    public String getMaHoSoNguon() { return maHoSoNguon; }
    public void setMaHoSoNguon(String value) { this.maHoSoNguon = value; }
    public String getNgayNhapHocNguon() { return ngayNhapHocNguon; }
    public void setNgayNhapHocNguon(String value) { this.ngayNhapHocNguon = value; }
    public String getHeDaoTaoNguon() { return heDaoTaoNguon; }
    public void setHeDaoTaoNguon(String value) { this.heDaoTaoNguon = value; }
    public String getNgayLuuNguon() { return ngayLuuNguon; }
    public void setNgayLuuNguon(String value) { this.ngayLuuNguon = value; }
    public String getLoaiLuuNguon() { return loaiLuuNguon; }
    public void setLoaiLuuNguon(String value) { this.loaiLuuNguon = value; }
    public Long getHocKyHanHocNguonId() { return hocKyHanHocNguonId; }
    public void setHocKyHanHocNguonId(Long value) { this.hocKyHanHocNguonId = value; }
    public String getHocKyHanHocNguonIdGocNguon() { return hocKyHanHocNguonIdGocNguon; }
    public void setHocKyHanHocNguonIdGocNguon(String value) { this.hocKyHanHocNguonIdGocNguon = value; }
    public String getClcNguon() { return clcNguon; }
    public void setClcNguon(String value) { this.clcNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public NguoiDung getNguoiDung() { return nguoiDung; }
    public DiaPhuong getNoiSinh() { return noiSinh; }
    public DanToc getDanToc() { return danToc; }
    public QuocTich getQuocTich() { return quocTich; }
    public LopSinhHoat getLopHienTai() { return lopHienTai; }
    public DonVi getKhoaQuanLyHienTai() { return khoaQuanLyHienTai; }
    public KhoaHoc getKhoaHocHienTai() { return khoaHocHienTai; }
    public ChuongTrinhDaoTao getChuongTrinhHienTai() { return chuongTrinhHienTai; }
    public HocKy getHocKyApDungNguon() { return hocKyApDungNguon; }
    public HocKy getHocKyBatDauNguon() { return hocKyBatDauNguon; }
    public HocKy getHocKyKetThucNguon() { return hocKyKetThucNguon; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
    public HocKy getHocKyHanHocNguon() { return hocKyHanHocNguon; }
}
