package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Internal DRL mapping for dbo.RLLopDot; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLLopDot`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlLopDot extends DrlEntity {
    @Column(name = "`KhoaDotID`", nullable = false)
    private Long khoaDotId;

    @Column(name = "`LopID`", nullable = false)
    private Long lopId;

    @Column(name = "`LopTruongID`")
    private Long lopTruongId;

    @Column(name = "`GVCNID`")
    private Long gvcnId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayGuiKhoa`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayGuiKhoa;

    @Column(name = "`NguoiXNCTSVID`")
    private Long nguoiXnCtsvId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayXNCTSV`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayXnCtsv;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayLapHoSo`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayLapHoSo;

    public Long getKhoaDotId() {
        return khoaDotId;
    }

    public void setKhoaDotId(Long khoaDotId) {
        this.khoaDotId = khoaDotId;
    }

    public Long getLopId() {
        return lopId;
    }

    public void setLopId(Long lopId) {
        this.lopId = lopId;
    }

    public Long getLopTruongId() {
        return lopTruongId;
    }

    public void setLopTruongId(Long lopTruongId) {
        this.lopTruongId = lopTruongId;
    }

    public Long getGvcnId() {
        return gvcnId;
    }

    public void setGvcnId(Long gvcnId) {
        this.gvcnId = gvcnId;
    }

    public OffsetDateTime getNgayGuiKhoa() {
        return ngayGuiKhoa;
    }

    public void setNgayGuiKhoa(OffsetDateTime ngayGuiKhoa) {
        this.ngayGuiKhoa = ngayGuiKhoa;
    }

    public Long getNguoiXnCtsvId() {
        return nguoiXnCtsvId;
    }

    public void setNguoiXnCtsvId(Long nguoiXnCtsvId) {
        this.nguoiXnCtsvId = nguoiXnCtsvId;
    }

    public OffsetDateTime getNgayXnCtsv() {
        return ngayXnCtsv;
    }

    public void setNgayXnCtsv(OffsetDateTime ngayXnCtsv) {
        this.ngayXnCtsv = ngayXnCtsv;
    }

    public OffsetDateTime getNgayLapHoSo() {
        return ngayLapHoSo;
    }

    public void setNgayLapHoSo(OffsetDateTime ngayLapHoSo) {
        this.ngayLapHoSo = ngayLapHoSo;
    }
}

