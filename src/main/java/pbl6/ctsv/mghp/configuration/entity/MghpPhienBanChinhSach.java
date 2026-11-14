package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_phien_ban_chinh_sach", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpPhienBanChinhSach {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="chinh_sach_id", nullable=false)
    private Long chinhSachId;

    @Column(name="phien_ban", nullable=false)
    private Integer phienBan;

    @Nationalized
    @Column(name="ten_tai_thoi_diem_chot", nullable=true, length=512)
    private String tenTaiThoiDiemChot;

    @Column(name="ty_le_giam", nullable=false, precision=12, scale=9)
    private BigDecimal tyLeGiam;

    @Nationalized
    @Column(name="ma_pham_vi", nullable=false, length=128)
    private String maPhamVi;

    @Column(name="ngay_bat_dau_hieu_luc", nullable=true)
    private LocalDate ngayBatDauHieuLuc;

    @Column(name="ngay_ket_thuc_hieu_luc", nullable=true)
    private LocalDate ngayKetThucHieuLuc;

    @Nationalized
    @Column(name="can_cu_phap_ly", nullable=true, columnDefinition="nvarchar(max)")
    private String canCuPhapLy;

    @Nationalized
    @Column(name="phien_ban_quy_tac_xet", nullable=true, length=128)
    private String phienBanQuyTacXet;

    @Nationalized
    @Column(name="thoi_han_huong", nullable=true, length=128)
    private String thoiHanHuong;

    @Column(name="uu_tien_khi_cung_muc", nullable=true)
    private Integer uuTienKhiCungMuc;

    @Nationalized
    @Column(name="trang_thai", nullable=true, length=128)
    private String trangThai;

    @Column(name="nguoi_tao_id", nullable=true)
    private Long nguoiTaoId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_khoa", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemKhoa;

    @Nationalized
    @Column(name="so_van_ban_ap_dung", nullable=true, length=128)
    private String soVanBanApDung;

    @Nationalized
    @Column(name="dieu_khoan_ap_dung", nullable=true, length=128)
    private String dieuKhoanApDung;

    @Nationalized
    @Column(name="url_can_cu", nullable=true, length=2048)
    private String urlCanCu;

    @Nationalized
    @Column(name="che_do_cap_nhat_minh_chung", nullable=true, length=128)
    private String cheDoCapNhatMinhChung;

    @Nationalized
    @Column(name="huong_dan_cap_nhat_minh_chung", nullable=true, columnDefinition="nvarchar(max)")
    private String huongDanCapNhatMinhChung;

    @Nationalized
    @Column(name="tinh_trang_xac_nhan_quy_tac", nullable=true, length=128)
    private String tinhTrangXacNhanQuyTac;

    @Column(name="tuoi_toi_thieu", nullable=true)
    private Integer tuoiToiThieu;

    @Column(name="tuoi_toi_da", nullable=true)
    private Integer tuoiToiDa;

    @Nationalized
    @Column(name="quy_tac_moc_tuoi", nullable=true, length=128)
    private String quyTacMocTuoi;

    @Column(name="yeu_cau_van_bang_thu_nhat", nullable=true)
    private Boolean yeuCauVanBangThuNhat;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    @Column(name="phien_ban_ky_thuat", nullable=false, columnDefinition="rowversion", insertable=false, updatable=false, length=8)
    private byte[] phienBanKyThuat;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chinh_sach_id", insertable=false, updatable=false)
    private MghpChinhSach chinhSach;

    public MghpPhienBanChinhSach() {}

    public Long getId() { return id; }
    public Long getChinhSachId() { return chinhSachId; }
    public void setChinhSachId(Long value) { this.chinhSachId = value; }
    public Integer getPhienBan() { return phienBan; }
    public void setPhienBan(Integer value) { this.phienBan = value; }
    public String getTenTaiThoiDiemChot() { return tenTaiThoiDiemChot; }
    public void setTenTaiThoiDiemChot(String value) { this.tenTaiThoiDiemChot = value; }
    public BigDecimal getTyLeGiam() { return tyLeGiam; }
    public void setTyLeGiam(BigDecimal value) { this.tyLeGiam = value; }
    public String getMaPhamVi() { return maPhamVi; }
    public void setMaPhamVi(String value) { this.maPhamVi = value; }
    public LocalDate getNgayBatDauHieuLuc() { return ngayBatDauHieuLuc; }
    public void setNgayBatDauHieuLuc(LocalDate value) { this.ngayBatDauHieuLuc = value; }
    public LocalDate getNgayKetThucHieuLuc() { return ngayKetThucHieuLuc; }
    public void setNgayKetThucHieuLuc(LocalDate value) { this.ngayKetThucHieuLuc = value; }
    public String getCanCuPhapLy() { return canCuPhapLy; }
    public void setCanCuPhapLy(String value) { this.canCuPhapLy = value; }
    public String getPhienBanQuyTacXet() { return phienBanQuyTacXet; }
    public void setPhienBanQuyTacXet(String value) { this.phienBanQuyTacXet = value; }
    public String getThoiHanHuong() { return thoiHanHuong; }
    public void setThoiHanHuong(String value) { this.thoiHanHuong = value; }
    public Integer getUuTienKhiCungMuc() { return uuTienKhiCungMuc; }
    public void setUuTienKhiCungMuc(Integer value) { this.uuTienKhiCungMuc = value; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String value) { this.trangThai = value; }
    public Long getNguoiTaoId() { return nguoiTaoId; }
    public void setNguoiTaoId(Long value) { this.nguoiTaoId = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemKhoa() { return thoiDiemKhoa; }
    public void setThoiDiemKhoa(OffsetDateTime value) { this.thoiDiemKhoa = value; }
    public String getSoVanBanApDung() { return soVanBanApDung; }
    public void setSoVanBanApDung(String value) { this.soVanBanApDung = value; }
    public String getDieuKhoanApDung() { return dieuKhoanApDung; }
    public void setDieuKhoanApDung(String value) { this.dieuKhoanApDung = value; }
    public String getUrlCanCu() { return urlCanCu; }
    public void setUrlCanCu(String value) { this.urlCanCu = value; }
    public String getCheDoCapNhatMinhChung() { return cheDoCapNhatMinhChung; }
    public void setCheDoCapNhatMinhChung(String value) { this.cheDoCapNhatMinhChung = value; }
    public String getHuongDanCapNhatMinhChung() { return huongDanCapNhatMinhChung; }
    public void setHuongDanCapNhatMinhChung(String value) { this.huongDanCapNhatMinhChung = value; }
    public String getTinhTrangXacNhanQuyTac() { return tinhTrangXacNhanQuyTac; }
    public void setTinhTrangXacNhanQuyTac(String value) { this.tinhTrangXacNhanQuyTac = value; }
    public Integer getTuoiToiThieu() { return tuoiToiThieu; }
    public void setTuoiToiThieu(Integer value) { this.tuoiToiThieu = value; }
    public Integer getTuoiToiDa() { return tuoiToiDa; }
    public void setTuoiToiDa(Integer value) { this.tuoiToiDa = value; }
    public String getQuyTacMocTuoi() { return quyTacMocTuoi; }
    public void setQuyTacMocTuoi(String value) { this.quyTacMocTuoi = value; }
    public Boolean getYeuCauVanBangThuNhat() { return yeuCauVanBangThuNhat; }
    public void setYeuCauVanBangThuNhat(Boolean value) { this.yeuCauVanBangThuNhat = value; }
    public byte[] getPhienBanKyThuat() { return phienBanKyThuat == null ? null : phienBanKyThuat.clone(); }
    public MghpChinhSach getChinhSach() { return chinhSach; }
}
