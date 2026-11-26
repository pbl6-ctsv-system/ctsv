package pbl6.ctsv.hocbong.kkht.configuration.rules.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;
import pbl6.ctsv.hocbong.kkht.configuration.combination.entity.SqlServerLocalDateJdbcType;

@Entity @Table(name="kkht_phien_ban_quy_tac",schema="dbo") @DynamicUpdate
public class RuleVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false)
    private Long id;
    @Column(name="phien_ban",nullable=false) private Integer number;
    @Nationalized @Column(name="can_cu",nullable=false,length=4000) private String legalBasis;
    @JdbcType(SqlServerLocalDateJdbcType.class) @Column(name="hieu_luc_tu",nullable=false) private LocalDate effectiveFrom;
    @JdbcType(SqlServerLocalDateJdbcType.class) @Column(name="hieu_luc_den") private LocalDate effectiveTo;
    @Nationalized @Column(name="dieu_kien_json",nullable=false,columnDefinition="nvarchar(max)") private String conditionsJson;
    @Nationalized @Column(name="muc_abc_json",nullable=false,columnDefinition="nvarchar(max)") private String levelsJson;
    @Nationalized @Column(name="thu_tu_uu_tien_json",nullable=false,columnDefinition="nvarchar(max)") private String priorityJson;
    @Nationalized @Column(name="can_cu_hoc_phi_json",nullable=false,columnDefinition="nvarchar(max)") private String tuitionJson;
    @Nationalized @Column(name="quy_tac_bang_diem_json",nullable=false,columnDefinition="nvarchar(max)") private String tieJson;
    @Column(name="ty_le_suat_cuoi",nullable=false,precision=19,scale=10) private BigDecimal lastSlotRatio;
    @Nationalized @Column(name="cach_cap_suat_cuoi",nullable=false,length=32) private String lastSlotMode;
    @Nationalized @Column(name="trang_thai",nullable=false,length=32) private String status;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="da_su_dung_luc",insertable=false,updatable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime usedAt;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name="phien_ban_ky_thuat",nullable=false,insertable=false,updatable=false,columnDefinition="rowversion",length=8) private byte[] version;

    public Long getId(){return id;}
    public Integer getNumber(){return number;} public void setNumber(Integer v){number=v;}
    public String getLegalBasis(){return legalBasis;} public void setLegalBasis(String v){legalBasis=v;}
    public LocalDate getEffectiveFrom(){return effectiveFrom;} public void setEffectiveFrom(LocalDate v){effectiveFrom=v;}
    public LocalDate getEffectiveTo(){return effectiveTo;} public void setEffectiveTo(LocalDate v){effectiveTo=v;}
    public String getConditionsJson(){return conditionsJson;} public void setConditionsJson(String v){conditionsJson=v;}
    public String getLevelsJson(){return levelsJson;} public void setLevelsJson(String v){levelsJson=v;}
    public String getPriorityJson(){return priorityJson;} public void setPriorityJson(String v){priorityJson=v;}
    public String getTuitionJson(){return tuitionJson;} public void setTuitionJson(String v){tuitionJson=v;}
    public String getTieJson(){return tieJson;} public void setTieJson(String v){tieJson=v;}
    public BigDecimal getLastSlotRatio(){return lastSlotRatio;} public void setLastSlotRatio(BigDecimal v){lastSlotRatio=v;}
    public String getLastSlotMode(){return lastSlotMode;} public void setLastSlotMode(String v){lastSlotMode=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public OffsetDateTime getUsedAt(){return usedAt;}
    public byte[] getVersion(){return version==null?null:version.clone();}
}
