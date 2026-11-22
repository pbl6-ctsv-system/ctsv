package pbl6.ctsv.mghp.configuration.entity;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
/** Append-only scalar snapshot; SQL forbids UPDATE and DELETE. */
@Entity @Table(name="mghp_lich_su_lich_trinh",schema="dbo")
public class MghpLichSuLichTrinh {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id") public Long id;
    @Column(name="dot_xet_id",nullable=false) public Long roundId;
    @Column(name="lan_thay_doi",nullable=false) public Integer revision;
    @Nationalized @Column(name="loai_thay_doi",length=64,nullable=false) public String action;
    @Nationalized @Column(name="ly_do",length=4000,nullable=false) public String reason;
    @Column(name="nguoi_thao_tac_id",nullable=false) public Long actorId;
    @Column(name="nhat_ky_thao_tac_id",nullable=false) public Long auditId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_ghi_nhan",columnDefinition="datetimeoffset(7)",nullable=false) public OffsetDateTime recordedAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_mo",columnDefinition="datetimeoffset(7)") public OffsetDateTime opensAt;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="han_nop",columnDefinition="datetimeoffset(7)") public OffsetDateTime submissionDeadline;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="han_ra_soat",columnDefinition="datetimeoffset(7)") public OffsetDateTime reviewDeadline;
    @Embedded public RoundSchedule plan;
    @Column(name="nguoi_dong_xet_id") public Long reviewClosedBy;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_dong_xet",columnDefinition="datetimeoffset(7)") public OffsetDateTime reviewClosedAt;
    @Column(name="sha256_nguon_dong_xet",length=64) public String sourceSha256;
}
