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
@Table(name="he_thong_ngoai", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class HeThongNgoai {
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
    @Column(name="loai_he_thong", nullable=true, length=128)
    private String loaiHeThong;

    @Nationalized
    @Column(name="che_do_ket_noi", nullable=true, length=128)
    private String cheDoKetNoi;

    @Nationalized
    @Column(name="url_goc", nullable=true, length=2048)
    private String urlGoc;

    @Nationalized
    @Column(name="tham_chieu_thong_tin_xac_thuc", nullable=true, length=2048)
    private String thamChieuThongTinXacThuc;

    @Column(name="dang_hoat_dong", nullable=true)
    private Boolean dangHoatDong;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;

    public HeThongNgoai() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getLoaiHeThong() { return loaiHeThong; }
    public void setLoaiHeThong(String value) { this.loaiHeThong = value; }
    public String getCheDoKetNoi() { return cheDoKetNoi; }
    public void setCheDoKetNoi(String value) { this.cheDoKetNoi = value; }
    public String getUrlGoc() { return urlGoc; }
    public void setUrlGoc(String value) { this.urlGoc = value; }
    public String getThamChieuThongTinXacThuc() { return thamChieuThongTinXacThuc; }
    public void setThamChieuThongTinXacThuc(String value) { this.thamChieuThongTinXacThuc = value; }
    public Boolean getDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(Boolean value) { this.dangHoatDong = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
}
