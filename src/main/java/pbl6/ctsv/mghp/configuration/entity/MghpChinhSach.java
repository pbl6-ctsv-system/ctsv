package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_chinh_sach", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpChinhSach {
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
    @Column(name="nhom_chinh_sach", nullable=true, length=128)
    private String nhomChinhSach;

    @Column(name="dang_hoat_dong", nullable=true)
    private Boolean dangHoatDong;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    public MghpChinhSach() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getNhomChinhSach() { return nhomChinhSach; }
    public void setNhomChinhSach(String value) { this.nhomChinhSach = value; }
    public Boolean getDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(Boolean value) { this.dangHoatDong = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
}
