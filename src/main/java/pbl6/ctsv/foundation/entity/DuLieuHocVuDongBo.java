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
@Table(name="du_lieu_hoc_vu_dong_bo", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class DuLieuHocVuDongBo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="sinh_vien_id", nullable=true)
    private Long sinhVienId;

    @Column(name="hoc_ky_id", nullable=true)
    private Long hocKyId;

    @Column(name="chuong_trinh_dao_tao_id", nullable=true)
    private Long chuongTrinhDaoTaoId;

    @Column(name="qua_trinh_hoc_id", nullable=true)
    private Long quaTrinhHocId;

    @Column(name="dot_dong_bo_id", nullable=true)
    private Long dotDongBoId;

    @Nationalized
    @Column(name="nam_hoc_nguon", nullable=true, length=512)
    private String namHocNguon;

    @Nationalized
    @Column(name="hoc_ky_nguon", nullable=true, length=512)
    private String hocKyNguon;

    @Column(name="hp_dang_ky", nullable=true, precision=28, scale=10)
    private BigDecimal hpDangKy;

    @Column(name="hp_bat_buoc", nullable=true, precision=28, scale=10)
    private BigDecimal hpBatBuoc;

    @Column(name="so_tc_hoc_lai", nullable=true, precision=28, scale=10)
    private BigDecimal soTcHocLai;

    @Column(name="diem_tb_hoc_tap_he_10", nullable=true, precision=28, scale=10)
    private BigDecimal diemTbHocTapHe10;

    @Column(name="diem_tb_hoc_bong", nullable=true, precision=28, scale=10)
    private BigDecimal diemTbHocBong;

    @Nationalized
    @Column(name="xep_loai_ck", nullable=true, length=128)
    private String xepLoaiCk;

    @Column(name="hp_tich_luy", nullable=true, precision=28, scale=10)
    private BigDecimal hpTichLuy;

    @Column(name="diem_tb_tich_luy", nullable=true, precision=28, scale=10)
    private BigDecimal diemTbTichLuy;

    @Column(name="diem_tb_tich_luy_he_10", nullable=true, precision=28, scale=10)
    private BigDecimal diemTbTichLuyHe10;

    @Nationalized
    @Column(name="xep_loai", nullable=true, length=128)
    private String xepLoai;

    @Column(name="kl_dang_ky", nullable=true, precision=28, scale=10)
    private BigDecimal klDangKy;

    @Column(name="kl_hoc_lai", nullable=true, precision=28, scale=10)
    private BigDecimal klHocLai;

    @Column(name="tc_khung", nullable=true, precision=28, scale=10)
    private BigDecimal tcKhung;

    @Column(name="tc_tien_do", nullable=true, precision=28, scale=10)
    private BigDecimal tcTienDo;

    @Column(name="tc_ty_le", nullable=true, precision=28, scale=10)
    private BigDecimal tcTyLe;

    @Nationalized
    @Column(name="trang_thai_lien_ket_qua_trinh", nullable=true, length=128)
    private String trangThaiLienKetQuaTrinh;

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
    @Column(name="sinh_vien_id_goc_nguon", nullable=true, length=512)
    private String sinhVienIdGocNguon;

    @Nationalized
    @Column(name="hoc_ky_id_goc_nguon", nullable=true, length=512)
    private String hocKyIdGocNguon;

    @Nationalized
    @Column(name="chuong_trinh_dao_tao_id_goc_nguon", nullable=true, length=512)
    private String chuongTrinhDaoTaoIdGocNguon;

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
    @Column(name="hp_dang_ky_goc_nguon", nullable=true, length=512)
    private String hpDangKyGocNguon;

    @Nationalized
    @Column(name="hp_bat_buoc_goc_nguon", nullable=true, length=512)
    private String hpBatBuocGocNguon;

    @Nationalized
    @Column(name="so_tc_hoc_lai_goc_nguon", nullable=true, length=512)
    private String soTcHocLaiGocNguon;

    @Nationalized
    @Column(name="diem_tb_hoc_tap_he_10_goc_nguon", nullable=true, length=512)
    private String diemTbHocTapHe10GocNguon;

    @Nationalized
    @Column(name="diem_tb_hoc_bong_goc_nguon", nullable=true, length=512)
    private String diemTbHocBongGocNguon;

    @Nationalized
    @Column(name="hp_tich_luy_goc_nguon", nullable=true, length=512)
    private String hpTichLuyGocNguon;

    @Nationalized
    @Column(name="diem_tb_tich_luy_goc_nguon", nullable=true, length=512)
    private String diemTbTichLuyGocNguon;

    @Nationalized
    @Column(name="diem_tb_tich_luy_he_10_goc_nguon", nullable=true, length=512)
    private String diemTbTichLuyHe10GocNguon;

    @Nationalized
    @Column(name="kl_dang_ky_goc_nguon", nullable=true, length=512)
    private String klDangKyGocNguon;

    @Nationalized
    @Column(name="kl_hoc_lai_goc_nguon", nullable=true, length=512)
    private String klHocLaiGocNguon;

    @Nationalized
    @Column(name="tc_khung_goc_nguon", nullable=true, length=512)
    private String tcKhungGocNguon;

    @Nationalized
    @Column(name="tc_tien_do_goc_nguon", nullable=true, length=512)
    private String tcTienDoGocNguon;

    @Nationalized
    @Column(name="tc_ty_le_goc_nguon", nullable=true, length=512)
    private String tcTyLeGocNguon;

    @Nationalized
    @Column(name="diem_ren_luyen_nguon", nullable=true, length=512)
    private String diemRenLuyenNguon;

    @Nationalized
    @Column(name="thoi_hoc_nguon", nullable=true, length=512)
    private String thoiHocNguon;

    @Nationalized
    @Column(name="ngung_hoc_nguon", nullable=true, length=512)
    private String ngungHocNguon;

    @Nationalized
    @Column(name="so_ky_ngung_nguon", nullable=true, length=512)
    private String soKyNgungNguon;

    @Nationalized
    @Column(name="ma_ky_hoc_nguon", nullable=true, length=512)
    private String maKyHocNguon;

    @Nationalized
    @Column(name="dung_hoc_nguon", nullable=true, length=512)
    private String dungHocNguon;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="sinh_vien_id", insertable=false, updatable=false)
    private SinhVien sinhVien;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_id", insertable=false, updatable=false)
    private HocKy hocKy;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chuong_trinh_dao_tao_id", insertable=false, updatable=false)
    private ChuongTrinhDaoTao chuongTrinhDaoTao;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="qua_trinh_hoc_id", insertable=false, updatable=false)
    private QuaTrinhTheoHoc quaTrinhHoc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public DuLieuHocVuDongBo() {}

    public Long getId() { return id; }
    public Long getSinhVienId() { return sinhVienId; }
    public void setSinhVienId(Long value) { this.sinhVienId = value; }
    public Long getHocKyId() { return hocKyId; }
    public void setHocKyId(Long value) { this.hocKyId = value; }
    public Long getChuongTrinhDaoTaoId() { return chuongTrinhDaoTaoId; }
    public void setChuongTrinhDaoTaoId(Long value) { this.chuongTrinhDaoTaoId = value; }
    public Long getQuaTrinhHocId() { return quaTrinhHocId; }
    public void setQuaTrinhHocId(Long value) { this.quaTrinhHocId = value; }
    public Long getDotDongBoId() { return dotDongBoId; }
    public void setDotDongBoId(Long value) { this.dotDongBoId = value; }
    public String getNamHocNguon() { return namHocNguon; }
    public void setNamHocNguon(String value) { this.namHocNguon = value; }
    public String getHocKyNguon() { return hocKyNguon; }
    public void setHocKyNguon(String value) { this.hocKyNguon = value; }
    public BigDecimal getHpDangKy() { return hpDangKy; }
    public void setHpDangKy(BigDecimal value) { this.hpDangKy = value; }
    public BigDecimal getHpBatBuoc() { return hpBatBuoc; }
    public void setHpBatBuoc(BigDecimal value) { this.hpBatBuoc = value; }
    public BigDecimal getSoTcHocLai() { return soTcHocLai; }
    public void setSoTcHocLai(BigDecimal value) { this.soTcHocLai = value; }
    public BigDecimal getDiemTbHocTapHe10() { return diemTbHocTapHe10; }
    public void setDiemTbHocTapHe10(BigDecimal value) { this.diemTbHocTapHe10 = value; }
    public BigDecimal getDiemTbHocBong() { return diemTbHocBong; }
    public void setDiemTbHocBong(BigDecimal value) { this.diemTbHocBong = value; }
    public String getXepLoaiCk() { return xepLoaiCk; }
    public void setXepLoaiCk(String value) { this.xepLoaiCk = value; }
    public BigDecimal getHpTichLuy() { return hpTichLuy; }
    public void setHpTichLuy(BigDecimal value) { this.hpTichLuy = value; }
    public BigDecimal getDiemTbTichLuy() { return diemTbTichLuy; }
    public void setDiemTbTichLuy(BigDecimal value) { this.diemTbTichLuy = value; }
    public BigDecimal getDiemTbTichLuyHe10() { return diemTbTichLuyHe10; }
    public void setDiemTbTichLuyHe10(BigDecimal value) { this.diemTbTichLuyHe10 = value; }
    public String getXepLoai() { return xepLoai; }
    public void setXepLoai(String value) { this.xepLoai = value; }
    public BigDecimal getKlDangKy() { return klDangKy; }
    public void setKlDangKy(BigDecimal value) { this.klDangKy = value; }
    public BigDecimal getKlHocLai() { return klHocLai; }
    public void setKlHocLai(BigDecimal value) { this.klHocLai = value; }
    public BigDecimal getTcKhung() { return tcKhung; }
    public void setTcKhung(BigDecimal value) { this.tcKhung = value; }
    public BigDecimal getTcTienDo() { return tcTienDo; }
    public void setTcTienDo(BigDecimal value) { this.tcTienDo = value; }
    public BigDecimal getTcTyLe() { return tcTyLe; }
    public void setTcTyLe(BigDecimal value) { this.tcTyLe = value; }
    public String getTrangThaiLienKetQuaTrinh() { return trangThaiLienKetQuaTrinh; }
    public void setTrangThaiLienKetQuaTrinh(String value) { this.trangThaiLienKetQuaTrinh = value; }
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
    public String getSinhVienIdGocNguon() { return sinhVienIdGocNguon; }
    public void setSinhVienIdGocNguon(String value) { this.sinhVienIdGocNguon = value; }
    public String getHocKyIdGocNguon() { return hocKyIdGocNguon; }
    public void setHocKyIdGocNguon(String value) { this.hocKyIdGocNguon = value; }
    public String getChuongTrinhDaoTaoIdGocNguon() { return chuongTrinhDaoTaoIdGocNguon; }
    public void setChuongTrinhDaoTaoIdGocNguon(String value) { this.chuongTrinhDaoTaoIdGocNguon = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public String getHpDangKyGocNguon() { return hpDangKyGocNguon; }
    public void setHpDangKyGocNguon(String value) { this.hpDangKyGocNguon = value; }
    public String getHpBatBuocGocNguon() { return hpBatBuocGocNguon; }
    public void setHpBatBuocGocNguon(String value) { this.hpBatBuocGocNguon = value; }
    public String getSoTcHocLaiGocNguon() { return soTcHocLaiGocNguon; }
    public void setSoTcHocLaiGocNguon(String value) { this.soTcHocLaiGocNguon = value; }
    public String getDiemTbHocTapHe10GocNguon() { return diemTbHocTapHe10GocNguon; }
    public void setDiemTbHocTapHe10GocNguon(String value) { this.diemTbHocTapHe10GocNguon = value; }
    public String getDiemTbHocBongGocNguon() { return diemTbHocBongGocNguon; }
    public void setDiemTbHocBongGocNguon(String value) { this.diemTbHocBongGocNguon = value; }
    public String getHpTichLuyGocNguon() { return hpTichLuyGocNguon; }
    public void setHpTichLuyGocNguon(String value) { this.hpTichLuyGocNguon = value; }
    public String getDiemTbTichLuyGocNguon() { return diemTbTichLuyGocNguon; }
    public void setDiemTbTichLuyGocNguon(String value) { this.diemTbTichLuyGocNguon = value; }
    public String getDiemTbTichLuyHe10GocNguon() { return diemTbTichLuyHe10GocNguon; }
    public void setDiemTbTichLuyHe10GocNguon(String value) { this.diemTbTichLuyHe10GocNguon = value; }
    public String getKlDangKyGocNguon() { return klDangKyGocNguon; }
    public void setKlDangKyGocNguon(String value) { this.klDangKyGocNguon = value; }
    public String getKlHocLaiGocNguon() { return klHocLaiGocNguon; }
    public void setKlHocLaiGocNguon(String value) { this.klHocLaiGocNguon = value; }
    public String getTcKhungGocNguon() { return tcKhungGocNguon; }
    public void setTcKhungGocNguon(String value) { this.tcKhungGocNguon = value; }
    public String getTcTienDoGocNguon() { return tcTienDoGocNguon; }
    public void setTcTienDoGocNguon(String value) { this.tcTienDoGocNguon = value; }
    public String getTcTyLeGocNguon() { return tcTyLeGocNguon; }
    public void setTcTyLeGocNguon(String value) { this.tcTyLeGocNguon = value; }
    public String getDiemRenLuyenNguon() { return diemRenLuyenNguon; }
    public void setDiemRenLuyenNguon(String value) { this.diemRenLuyenNguon = value; }
    public String getThoiHocNguon() { return thoiHocNguon; }
    public void setThoiHocNguon(String value) { this.thoiHocNguon = value; }
    public String getNgungHocNguon() { return ngungHocNguon; }
    public void setNgungHocNguon(String value) { this.ngungHocNguon = value; }
    public String getSoKyNgungNguon() { return soKyNgungNguon; }
    public void setSoKyNgungNguon(String value) { this.soKyNgungNguon = value; }
    public String getMaKyHocNguon() { return maKyHocNguon; }
    public void setMaKyHocNguon(String value) { this.maKyHocNguon = value; }
    public String getDungHocNguon() { return dungHocNguon; }
    public void setDungHocNguon(String value) { this.dungHocNguon = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public SinhVien getSinhVien() { return sinhVien; }
    public HocKy getHocKy() { return hocKy; }
    public ChuongTrinhDaoTao getChuongTrinhDaoTao() { return chuongTrinhDaoTao; }
    public QuaTrinhTheoHoc getQuaTrinhHoc() { return quaTrinhHoc; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
