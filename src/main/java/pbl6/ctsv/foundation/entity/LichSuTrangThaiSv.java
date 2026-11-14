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
@Table(name="lich_su_trang_thai_sv", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class LichSuTrangThaiSv {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="qua_trinh_hoc_id", nullable=true)
    private Long quaTrinhHocId;

    @Column(name="hoc_ky_ap_dung_id", nullable=true)
    private Long hocKyApDungId;

    @Nationalized
    @Column(name="trang_thai", nullable=true, length=128)
    private String trangThai;

    @Column(name="ngay_bat_dau_hieu_luc", nullable=true)
    private LocalDate ngayBatDauHieuLuc;

    @Column(name="ngay_ket_thuc_hieu_luc", nullable=true)
    private LocalDate ngayKetThucHieuLuc;

    @Column(name="he_thong_nguon_id", nullable=true)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ban_ghi_nguon_id", nullable=true, length=128)
    private String banGhiNguonId;

    @Nationalized
    @Column(name="phien_ban_nguon", nullable=true, length=512)
    private String phienBanNguon;

    @Nationalized
    @Column(name="so_qd_nguon", nullable=true, length=512)
    private String soQdNguon;

    @Column(name="ngay_qd_nguon", nullable=true)
    private LocalDate ngayQdNguon;

    @Nationalized
    @Column(name="ly_do", nullable=true, columnDefinition="nvarchar(max)")
    private String lyDo;

    @Nationalized
    @Column(name="can_cu_xac_minh", nullable=true, columnDefinition="nvarchar(max)")
    private String canCuXacMinh;

    @Column(name="nguoi_xac_minh_id", nullable=true)
    private Long nguoiXacMinhId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_xac_minh", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemXacMinh;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat_nguon", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhatNguon;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_ghi_nhan", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemGhiNhan;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="qua_trinh_hoc_id", insertable=false, updatable=false)
    private QuaTrinhTheoHoc quaTrinhHoc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="hoc_ky_ap_dung_id", insertable=false, updatable=false)
    private HocKy hocKyApDung;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_xac_minh_id", insertable=false, updatable=false)
    private NguoiDung nguoiXacMinh;

    public LichSuTrangThaiSv() {}

    public Long getId() { return id; }
    public Long getQuaTrinhHocId() { return quaTrinhHocId; }
    public void setQuaTrinhHocId(Long value) { this.quaTrinhHocId = value; }
    public Long getHocKyApDungId() { return hocKyApDungId; }
    public void setHocKyApDungId(Long value) { this.hocKyApDungId = value; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String value) { this.trangThai = value; }
    public LocalDate getNgayBatDauHieuLuc() { return ngayBatDauHieuLuc; }
    public void setNgayBatDauHieuLuc(LocalDate value) { this.ngayBatDauHieuLuc = value; }
    public LocalDate getNgayKetThucHieuLuc() { return ngayKetThucHieuLuc; }
    public void setNgayKetThucHieuLuc(LocalDate value) { this.ngayKetThucHieuLuc = value; }
    public Long getHeThongNguonId() { return heThongNguonId; }
    public void setHeThongNguonId(Long value) { this.heThongNguonId = value; }
    public String getBanGhiNguonId() { return banGhiNguonId; }
    public void setBanGhiNguonId(String value) { this.banGhiNguonId = value; }
    public String getPhienBanNguon() { return phienBanNguon; }
    public void setPhienBanNguon(String value) { this.phienBanNguon = value; }
    public String getSoQdNguon() { return soQdNguon; }
    public void setSoQdNguon(String value) { this.soQdNguon = value; }
    public LocalDate getNgayQdNguon() { return ngayQdNguon; }
    public void setNgayQdNguon(LocalDate value) { this.ngayQdNguon = value; }
    public String getLyDo() { return lyDo; }
    public void setLyDo(String value) { this.lyDo = value; }
    public String getCanCuXacMinh() { return canCuXacMinh; }
    public void setCanCuXacMinh(String value) { this.canCuXacMinh = value; }
    public Long getNguoiXacMinhId() { return nguoiXacMinhId; }
    public void setNguoiXacMinhId(Long value) { this.nguoiXacMinhId = value; }
    public OffsetDateTime getThoiDiemXacMinh() { return thoiDiemXacMinh; }
    public void setThoiDiemXacMinh(OffsetDateTime value) { this.thoiDiemXacMinh = value; }
    public OffsetDateTime getThoiDiemCapNhatNguon() { return thoiDiemCapNhatNguon; }
    public void setThoiDiemCapNhatNguon(OffsetDateTime value) { this.thoiDiemCapNhatNguon = value; }
    public OffsetDateTime getThoiDiemGhiNhan() { return thoiDiemGhiNhan; }
    public void setThoiDiemGhiNhan(OffsetDateTime value) { this.thoiDiemGhiNhan = value; }
    public QuaTrinhTheoHoc getQuaTrinhHoc() { return quaTrinhHoc; }
    public HocKy getHocKyApDung() { return hocKyApDung; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
    public NguoiDung getNguoiXacMinh() { return nguoiXacMinh; }
}
