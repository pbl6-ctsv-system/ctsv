package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;
import pbl6.ctsv.foundation.units.entity.DonVi;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="lop_sinh_hoat", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class LopSinhHoat {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma", nullable=true, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Column(name="chuong_trinh_dao_tao_id", nullable=true)
    private Long chuongTrinhDaoTaoId;

    @Column(name="khoa_quan_ly_id", nullable=true)
    private Long khoaQuanLyId;

    @Nationalized
    @Column(name="hinh_thuc_dao_tao_nguon", nullable=true, length=512)
    private String hinhThucDaoTaoNguon;

    @Nationalized
    @Column(name="cap_dao_tao_nguon", nullable=true, length=512)
    private String capDaoTaoNguon;

    @Column(name="khoa_hoc_id", nullable=true)
    private Long khoaHocId;

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
    @Column(name="chuong_trinh_dao_tao_id_goc_nguon", nullable=true, length=512)
    private String chuongTrinhDaoTaoIdGocNguon;

    @Nationalized
    @Column(name="khoa_quan_ly_id_goc_nguon", nullable=true, length=512)
    private String khoaQuanLyIdGocNguon;

    @Nationalized
    @Column(name="khoa_hoc_id_goc_nguon", nullable=true, length=512)
    private String khoaHocIdGocNguon;

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
    @Column(name="ma_khung_nguon", nullable=true, length=512)
    private String maKhungNguon;

    @Nationalized
    @Column(name="lop_ghep_nguon", nullable=true, length=512)
    private String lopGhepNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chuong_trinh_dao_tao_id", insertable=false, updatable=false)
    private ChuongTrinhDaoTao chuongTrinhDaoTao;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="khoa_quan_ly_id", insertable=false, updatable=false)
    private DonVi khoaQuanLy;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="khoa_hoc_id", insertable=false, updatable=false)
    private KhoaHoc khoaHoc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public LopSinhHoat() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public Long getChuongTrinhDaoTaoId() { return chuongTrinhDaoTaoId; }
    public void setChuongTrinhDaoTaoId(Long value) { this.chuongTrinhDaoTaoId = value; }
    public Long getKhoaQuanLyId() { return khoaQuanLyId; }
    public void setKhoaQuanLyId(Long value) { this.khoaQuanLyId = value; }
    public String getHinhThucDaoTaoNguon() { return hinhThucDaoTaoNguon; }
    public void setHinhThucDaoTaoNguon(String value) { this.hinhThucDaoTaoNguon = value; }
    public String getCapDaoTaoNguon() { return capDaoTaoNguon; }
    public void setCapDaoTaoNguon(String value) { this.capDaoTaoNguon = value; }
    public Long getKhoaHocId() { return khoaHocId; }
    public void setKhoaHocId(Long value) { this.khoaHocId = value; }
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
    public String getChuongTrinhDaoTaoIdGocNguon() { return chuongTrinhDaoTaoIdGocNguon; }
    public void setChuongTrinhDaoTaoIdGocNguon(String value) { this.chuongTrinhDaoTaoIdGocNguon = value; }
    public String getKhoaQuanLyIdGocNguon() { return khoaQuanLyIdGocNguon; }
    public void setKhoaQuanLyIdGocNguon(String value) { this.khoaQuanLyIdGocNguon = value; }
    public String getKhoaHocIdGocNguon() { return khoaHocIdGocNguon; }
    public void setKhoaHocIdGocNguon(String value) { this.khoaHocIdGocNguon = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public String getMaKhungNguon() { return maKhungNguon; }
    public void setMaKhungNguon(String value) { this.maKhungNguon = value; }
    public String getLopGhepNguon() { return lopGhepNguon; }
    public void setLopGhepNguon(String value) { this.lopGhepNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public ChuongTrinhDaoTao getChuongTrinhDaoTao() { return chuongTrinhDaoTao; }
    public DonVi getKhoaQuanLy() { return khoaQuanLy; }
    public KhoaHoc getKhoaHoc() { return khoaHoc; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
