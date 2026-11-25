package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

/** Internal DRL mapping for dbo.RLDot; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLDot`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlDot extends DrlEntity {
    @Nationalized
    @Column(name = "`MaDot`", nullable = false, length = 64)
    private String maDot;

    @Nationalized
    @Column(name = "`TenDot`", nullable = false, length = 512)
    private String tenDot;

    @Column(name = "`HocKyID`", nullable = false)
    private Long hocKyId;

    @Column(name = "`BieuMauID`", nullable = false)
    private Long bieuMauId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`BatDauSV`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime batDauSv;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`KetThucSV`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ketThucSv;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`BatDauLop`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime batDauLop;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`KetThucLop`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ketThucLop;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`BatDauKhoa`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime batDauKhoa;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`KetThucKhoa`", nullable = false, columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ketThucKhoa;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "`TrangThai`", nullable = false, columnDefinition = "tinyint")
    private Short trangThai = (short) 0;

    public String getMaDot() {
        return maDot;
    }

    public void setMaDot(String maDot) {
        this.maDot = maDot;
    }

    public String getTenDot() {
        return tenDot;
    }

    public void setTenDot(String tenDot) {
        this.tenDot = tenDot;
    }

    public Long getHocKyId() {
        return hocKyId;
    }

    public void setHocKyId(Long hocKyId) {
        this.hocKyId = hocKyId;
    }

    public Long getBieuMauId() {
        return bieuMauId;
    }

    public void setBieuMauId(Long bieuMauId) {
        this.bieuMauId = bieuMauId;
    }

    public OffsetDateTime getBatDauSv() {
        return batDauSv;
    }

    public void setBatDauSv(OffsetDateTime batDauSv) {
        this.batDauSv = batDauSv;
    }

    public OffsetDateTime getKetThucSv() {
        return ketThucSv;
    }

    public void setKetThucSv(OffsetDateTime ketThucSv) {
        this.ketThucSv = ketThucSv;
    }

    public OffsetDateTime getBatDauLop() {
        return batDauLop;
    }

    public void setBatDauLop(OffsetDateTime batDauLop) {
        this.batDauLop = batDauLop;
    }

    public OffsetDateTime getKetThucLop() {
        return ketThucLop;
    }

    public void setKetThucLop(OffsetDateTime ketThucLop) {
        this.ketThucLop = ketThucLop;
    }

    public OffsetDateTime getBatDauKhoa() {
        return batDauKhoa;
    }

    public void setBatDauKhoa(OffsetDateTime batDauKhoa) {
        this.batDauKhoa = batDauKhoa;
    }

    public OffsetDateTime getKetThucKhoa() {
        return ketThucKhoa;
    }

    public void setKetThucKhoa(OffsetDateTime ketThucKhoa) {
        this.ketThucKhoa = ketThucKhoa;
    }

    public Short getTrangThai() {
        return trangThai;
    }

    public void setTrangThai(Short trangThai) {
        this.trangThai = trangThai;
    }
}

