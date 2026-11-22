package pbl6.ctsv.hocbong.kkht.scope.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Append-only HB scope snapshot; scalar FKs keep Foundation entities behind its boundary. */
@Entity
@Table(name="kkht_pham_vi_dot", schema="dbo")
public class RoundScopeVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="dot_xet_id", nullable=false)
    private Long roundId;
    @Column(name="phien_ban", nullable=false)
    private Integer revision;
    @Nationalized
    @Column(name="cach_chon", nullable=false, length=16)
    private String mode;
    @Column(name="phien_nguon", nullable=false, length=43)
    private String catalogRevision;
    @Column(name="tao_boi", nullable=false)
    private Long createdBy;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="tao_luc", nullable=false, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime createdAt;
    public Long getId() { return id; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long value) { this.roundId=value; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer value) { this.revision=value; }
    public String getMode() { return mode; }
    public void setMode(String value) { this.mode=value; }
    public String getCatalogRevision() { return catalogRevision; }
    public void setCatalogRevision(String value) { this.catalogRevision=value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long value) { this.createdBy=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
}
