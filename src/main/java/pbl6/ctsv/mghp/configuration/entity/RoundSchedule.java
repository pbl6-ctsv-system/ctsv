package pbl6.ctsv.mghp.configuration.entity;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.*;
/** Typed, nullable planned dates owned by the MGHP round. */
@Embeddable
public class RoundSchedule {
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="bo_sung_du_kien_tu", columnDefinition="datetimeoffset(7)") public OffsetDateTime supplementPlannedStartAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="bo_sung_du_kien_den", columnDefinition="datetimeoffset(7)") public OffsetDateTime supplementPlannedEndAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ra_soat_du_kien_tu", columnDefinition="datetimeoffset(7)") public OffsetDateTime reviewPlannedStartAt;
    @Column(name="ngay_hop_du_kien") public LocalDate councilPlannedDate;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ky_du_kien_tu", columnDefinition="datetimeoffset(7)") public OffsetDateTime signingPlannedStartAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ky_du_kien_den", columnDefinition="datetimeoffset(7)") public OffsetDateTime signingPlannedEndAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="cong_bo_du_kien_tu", columnDefinition="datetimeoffset(7)") public OffsetDateTime publicationPlannedStartAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="cong_bo_du_kien_den", columnDefinition="datetimeoffset(7)") public OffsetDateTime publicationPlannedEndAt;
}
