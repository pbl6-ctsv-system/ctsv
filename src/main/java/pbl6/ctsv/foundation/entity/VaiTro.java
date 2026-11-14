package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="vai_tro", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class VaiTro {
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
    @Column(name="mo_ta", nullable=true, columnDefinition="nvarchar(max)")
    private String moTa;

    @Column(name="dang_hoat_dong", nullable=true)
    private Boolean dangHoatDong;

    public VaiTro() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String value) { this.moTa = value; }
    public Boolean getDangHoatDong() { return dangHoatDong; }
    public void setDangHoatDong(Boolean value) { this.dangHoatDong = value; }
}
