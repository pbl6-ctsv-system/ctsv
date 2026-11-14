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
@Table(name="qua_trinh_theo_hoc", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class QuaTrinhTheoHoc {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="sinh_vien_id", nullable=false)
    private Long sinhVienId;

    @Column(name="chuong_trinh_dao_tao_id", nullable=true)
    private Long chuongTrinhDaoTaoId;

    @Column(name="khoa_hoc_id", nullable=true)
    private Long khoaHocId;

    @Column(name="hoc_ky_bat_dau_id", nullable=true)
    private Long hocKyBatDauId;

    @Column(name="hoc_ky_ket_thuc_nguon_id", nullable=true)
    private Long hocKyKetThucNguonId;

    @Nationalized
    @Column(name="ma_qua_trinh_hoc", nullable=false, length=128)
    private String maQuaTrinhHoc;

    @Nationalized
    @Column(name="khoa_doi_chieu_nguon", nullable=true, length=512)
    private String khoaDoiChieuNguon;

    @Nationalized
    @Column(name="trang_thai_doi_chieu", nullable=true, length=128)
    private String trangThaiDoiChieu;

    @Column(name="la_chinh", nullable=true)
    private Boolean laChinh;

    @Nationalized
    @Column(name="trang_thai_hien_tai", nullable=true, length=128)
    private String trangThaiHienTai;

    @Column(name="hoc_ky_ap_dung_trang_thai_id", nullable=true)
    private Long hocKyApDungTrangThaiId;

    @Column(name="thoi_diem_bat_dau", nullable=true)
    private LocalDate thoiDiemBatDau;

    @Column(name="ngay_ket_thuc_du_kien", nullable=true)
    private LocalDate ngayKetThucDuKien;

    @Column(name="ngay_ket_thuc_thuc_te", nullable=true)
    private LocalDate ngayKetThucThucTe;

    @Nationalized
    @Column(name="can_cu_xac_minh", nullable=true, columnDefinition="nvarchar(max)")
    private String canCuXacMinh;

    @Column(name="nguoi_xac_minh_id", nullable=true)
    private Long nguoiXacMinhId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_xac_minh", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemXacMinh;

    @Column(name="he_thong_nguon_id", nullable=true)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ho_so_sv_nguon_id", nullable=true, length=128)
    private String hoSoSvNguonId;

    @Nationalized
    @Column(name="nganh_nguon_id", nullable=true, length=128)
    private String nganhNguonId;

    @Nationalized
    @Column(name="khoa_hoc_nguon_id", nullable=true, length=128)
    private String khoaHocNguonId;

    @Nationalized
    @Column(name="hoc_ky_bat_dau_nguon_id", nullable=true, length=128)
    private String hocKyBatDauNguonId;

    @Column(name="tep_can_cu_id", nullable=true)
    private Long tepCanCuId;

    @Nationalized
    @Column(name="phien_ban_anh_xa", nullable=true, length=128)
    private String phienBanAnhXa;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dong_bo", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemDongBo;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    @Column(name="phien_ban", nullable=false, columnDefinition="rowversion", insertable=false, updatable=false, length=8)
    private byte[] phienBan;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="sinh_vien_id", insertable=false, updatable=false)
    private SinhVien sinhVien;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chuong_trinh_dao_tao_id", insertable=false, updatable=false)
    private ChuongTrinhDaoTao chuongTrinhDaoTao;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="khoa_hoc_id", insertable=false, updatable=false)
    private KhoaHoc khoaHoc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_bat_dau_id", insertable=false, updatable=false)
    private HocKy hocKyBatDau;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ket_thuc_nguon_id", insertable=false, updatable=false)
    private HocKy hocKyKetThucNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ap_dung_trang_thai_id", insertable=false, updatable=false)
    private HocKy hocKyApDungTrangThai;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_xac_minh_id", insertable=false, updatable=false)
    private NguoiDung nguoiXacMinh;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public QuaTrinhTheoHoc() {}

    public Long getId() { return id; }
    public Long getSinhVienId() { return sinhVienId; }
    public void setSinhVienId(Long value) { this.sinhVienId = value; }
    public Long getChuongTrinhDaoTaoId() { return chuongTrinhDaoTaoId; }
    public void setChuongTrinhDaoTaoId(Long value) { this.chuongTrinhDaoTaoId = value; }
    public Long getKhoaHocId() { return khoaHocId; }
    public void setKhoaHocId(Long value) { this.khoaHocId = value; }
    public Long getHocKyBatDauId() { return hocKyBatDauId; }
    public void setHocKyBatDauId(Long value) { this.hocKyBatDauId = value; }
    public Long getHocKyKetThucNguonId() { return hocKyKetThucNguonId; }
    public void setHocKyKetThucNguonId(Long value) { this.hocKyKetThucNguonId = value; }
    public String getMaQuaTrinhHoc() { return maQuaTrinhHoc; }
    public void setMaQuaTrinhHoc(String value) { this.maQuaTrinhHoc = value; }
    public String getKhoaDoiChieuNguon() { return khoaDoiChieuNguon; }
    public void setKhoaDoiChieuNguon(String value) { this.khoaDoiChieuNguon = value; }
    public String getTrangThaiDoiChieu() { return trangThaiDoiChieu; }
    public void setTrangThaiDoiChieu(String value) { this.trangThaiDoiChieu = value; }
    public Boolean getLaChinh() { return laChinh; }
    public void setLaChinh(Boolean value) { this.laChinh = value; }
    public String getTrangThaiHienTai() { return trangThaiHienTai; }
    public void setTrangThaiHienTai(String value) { this.trangThaiHienTai = value; }
    public Long getHocKyApDungTrangThaiId() { return hocKyApDungTrangThaiId; }
    public void setHocKyApDungTrangThaiId(Long value) { this.hocKyApDungTrangThaiId = value; }
    public LocalDate getThoiDiemBatDau() { return thoiDiemBatDau; }
    public void setThoiDiemBatDau(LocalDate value) { this.thoiDiemBatDau = value; }
    public LocalDate getNgayKetThucDuKien() { return ngayKetThucDuKien; }
    public void setNgayKetThucDuKien(LocalDate value) { this.ngayKetThucDuKien = value; }
    public LocalDate getNgayKetThucThucTe() { return ngayKetThucThucTe; }
    public void setNgayKetThucThucTe(LocalDate value) { this.ngayKetThucThucTe = value; }
    public String getCanCuXacMinh() { return canCuXacMinh; }
    public void setCanCuXacMinh(String value) { this.canCuXacMinh = value; }
    public Long getNguoiXacMinhId() { return nguoiXacMinhId; }
    public void setNguoiXacMinhId(Long value) { this.nguoiXacMinhId = value; }
    public OffsetDateTime getThoiDiemXacMinh() { return thoiDiemXacMinh; }
    public void setThoiDiemXacMinh(OffsetDateTime value) { this.thoiDiemXacMinh = value; }
    public Long getHeThongNguonId() { return heThongNguonId; }
    public void setHeThongNguonId(Long value) { this.heThongNguonId = value; }
    public String getHoSoSvNguonId() { return hoSoSvNguonId; }
    public void setHoSoSvNguonId(String value) { this.hoSoSvNguonId = value; }
    public String getNganhNguonId() { return nganhNguonId; }
    public void setNganhNguonId(String value) { this.nganhNguonId = value; }
    public String getKhoaHocNguonId() { return khoaHocNguonId; }
    public void setKhoaHocNguonId(String value) { this.khoaHocNguonId = value; }
    public String getHocKyBatDauNguonId() { return hocKyBatDauNguonId; }
    public void setHocKyBatDauNguonId(String value) { this.hocKyBatDauNguonId = value; }
    public Long getTepCanCuId() { return tepCanCuId; }
    public void setTepCanCuId(Long value) { this.tepCanCuId = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public OffsetDateTime getThoiDiemDongBo() { return thoiDiemDongBo; }
    public void setThoiDiemDongBo(OffsetDateTime value) { this.thoiDiemDongBo = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
    public byte[] getPhienBan() { return phienBan == null ? null : phienBan.clone(); }
    public SinhVien getSinhVien() { return sinhVien; }
    public ChuongTrinhDaoTao getChuongTrinhDaoTao() { return chuongTrinhDaoTao; }
    public KhoaHoc getKhoaHoc() { return khoaHoc; }
    public HocKy getHocKyBatDau() { return hocKyBatDau; }
    public HocKy getHocKyKetThucNguon() { return hocKyKetThucNguon; }
    public HocKy getHocKyApDungTrangThai() { return hocKyApDungTrangThai; }
    public NguoiDung getNguoiXacMinh() { return nguoiXacMinh; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
