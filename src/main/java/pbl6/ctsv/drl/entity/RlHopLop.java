package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Internal DRL mapping for dbo.RLHopLop; all foreign keys are scalar CTSV identities. */
@Entity
@Table(name = "`RLHopLop`", schema = "dbo")
@DynamicInsert
@DynamicUpdate
public class RlHopLop extends DrlEntity {
    @Column(name = "`LopDotID`", nullable = false)
    private Long lopDotId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`BatDauHop`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime batDauHop;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name = "`KetThucHop`", columnDefinition = "datetimeoffset(7)")
    private OffsetDateTime ketThucHop;

    @Nationalized
    @Column(name = "`DiaDiem`", length = 512)
    private String diaDiem;

    @Nationalized
    @Column(name = "`NoiDung`", columnDefinition = "nvarchar(max)")
    private String noiDung;

    @Nationalized
    @Column(name = "`KienNghi`", columnDefinition = "nvarchar(max)")
    private String kienNghi;

    public Long getLopDotId() {
        return lopDotId;
    }

    public void setLopDotId(Long lopDotId) {
        this.lopDotId = lopDotId;
    }

    public OffsetDateTime getBatDauHop() {
        return batDauHop;
    }

    public void setBatDauHop(OffsetDateTime batDauHop) {
        this.batDauHop = batDauHop;
    }

    public OffsetDateTime getKetThucHop() {
        return ketThucHop;
    }

    public void setKetThucHop(OffsetDateTime ketThucHop) {
        this.ketThucHop = ketThucHop;
    }

    public String getDiaDiem() {
        return diaDiem;
    }

    public void setDiaDiem(String diaDiem) {
        this.diaDiem = diaDiem;
    }

    public String getNoiDung() {
        return noiDung;
    }

    public void setNoiDung(String noiDung) {
        this.noiDung = noiDung;
    }

    public String getKienNghi() {
        return kienNghi;
    }

    public void setKienNghi(String kienNghi) {
        this.kienNghi = kienNghi;
    }
}

