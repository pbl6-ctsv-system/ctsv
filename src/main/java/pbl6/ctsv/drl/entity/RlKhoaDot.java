package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Internal DRL mapping for dbo.RLKhoaDot; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLKhoaDot`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlKhoaDot extends DrlEntity {
    @Column(name = "`DotID`", nullable = false)
    private Long dotId;

    @Column(name = "`KhoaID`", nullable = false)
    private Long khoaId;

    @Column(name = "`DaiDienKhoaID`")
    private Long daiDienKhoaId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`NgayGuiCTSV`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ngayGuiCtsv;

    public Long getDotId() {
        return dotId;
    }

    public void setDotId(Long dotId) {
        this.dotId = dotId;
    }

    public Long getKhoaId() {
        return khoaId;
    }

    public void setKhoaId(Long khoaId) {
        this.khoaId = khoaId;
    }

    public Long getDaiDienKhoaId() {
        return daiDienKhoaId;
    }

    public void setDaiDienKhoaId(Long daiDienKhoaId) {
        this.daiDienKhoaId = daiDienKhoaId;
    }

    public OffsetDateTime getNgayGuiCtsv() {
        return ngayGuiCtsv;
    }

    public void setNgayGuiCtsv(OffsetDateTime ngayGuiCtsv) {
        this.ngayGuiCtsv = ngayGuiCtsv;
    }
}

