package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="nguoi_dung", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class NguoiDung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="dia_chi_email", nullable=false, length=254)
    private String diaChiEmail;

    @Nationalized
    @Column(name="ma_bam_mat_khau", nullable=true, length=128)
    private String maBamMatKhau;

    @Nationalized
    @Column(name="ho_ten", nullable=true, length=512)
    private String hoTen;

    @Nationalized
    @Column(name="so_dien_thoai", nullable=true, length=64)
    private String soDienThoai;

    @Column(name="dang_hoat_dong", nullable=true)
    private Boolean dangHoatDong;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dang_nhap_gan_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemDangNhapGanNhat;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_xoa", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemXoa;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    @Column(name="phien_ban", nullable=false, columnDefinition="rowversion", insertable=false, updatable=false, length=8)
    private byte[] phienBan;

    public NguoiDung() {}

    public Long getId() { return id; }
    public String getDiaChiEmail() { return diaChiEmail; }
    public void setDiaChiEmail(String value) { this.diaChiEmail = value; }
    public String getMaBamMatKhau() { return maBamMatKhau; }
    public void setMaBamMatKhau(String value) { this.maBamMatKhau = value; }
    public String getHoTen() { return hoTen; }
    public void setHoTen(String value) { this.hoTen = value; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String value) { this.soDienThoai = value; }
    public Boolean getDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(Boolean value) { this.dangHoatDong = value; }
    public OffsetDateTime getThoiDiemDangNhapGanNhat() { return thoiDiemDangNhapGanNhat; }
    public void setThoiDiemDangNhapGanNhat(OffsetDateTime value) { this.thoiDiemDangNhapGanNhat = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
    public OffsetDateTime getThoiDiemXoa() { return thoiDiemXoa; }
    public void setThoiDiemXoa(OffsetDateTime value) { this.thoiDiemXoa = value; }
    public byte[] getPhienBan() { return phienBan == null ? null : phienBan.clone(); }
}
