package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

/** Internal DRL mapping for dbo.RLPhieu; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLPhieu`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlPhieu extends DrlEntity {
    @Column(name = "`LopDotID`", nullable = false)
    private Long lopDotId;

    @Column(name = "`SinhVienID`", nullable = false)
    private Long sinhVienId;

    @Column(name = "`XacNhanSV`", nullable = false, columnDefinition = "bit")
    private Boolean xacNhanSv = false;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayXNSV`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayXnSv;

    @Column(name = "`XacNhanLop`", nullable = false, columnDefinition = "bit")
    private Boolean xacNhanLop = false;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayXNLop`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayXnLop;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "`ThamDuHop`", columnDefinition = "tinyint")
    private Short thamDuHop;

    @Column(name = "`KhongDanhGia`", nullable = false, columnDefinition = "bit")
    private Boolean khongDanhGia = false;

    public Long getLopDotId() {
        return lopDotId;
    }

    public void setLopDotId(Long lopDotId) {
        this.lopDotId = lopDotId;
    }

    public Long getSinhVienId() {
        return sinhVienId;
    }

    public void setSinhVienId(Long sinhVienId) {
        this.sinhVienId = sinhVienId;
    }

    public Boolean getXacNhanSv() {
        return xacNhanSv;
    }

    public void setXacNhanSv(Boolean xacNhanSv) {
        this.xacNhanSv = xacNhanSv;
    }

    public OffsetDateTime getNgayXnSv() {
        return ngayXnSv;
    }

    public void setNgayXnSv(OffsetDateTime ngayXnSv) {
        this.ngayXnSv = ngayXnSv;
    }

    public Boolean getXacNhanLop() {
        return xacNhanLop;
    }

    public void setXacNhanLop(Boolean xacNhanLop) {
        this.xacNhanLop = xacNhanLop;
    }

    public OffsetDateTime getNgayXnLop() {
        return ngayXnLop;
    }

    public void setNgayXnLop(OffsetDateTime ngayXnLop) {
        this.ngayXnLop = ngayXnLop;
    }

    public Short getThamDuHop() {
        return thamDuHop;
    }

    public void setThamDuHop(Short thamDuHop) {
        this.thamDuHop = thamDuHop;
    }

    public Boolean getKhongDanhGia() {
        return khongDanhGia;
    }

    public void setKhongDanhGia(Boolean khongDanhGia) {
        this.khongDanhGia = khongDanhGia;
    }
}

