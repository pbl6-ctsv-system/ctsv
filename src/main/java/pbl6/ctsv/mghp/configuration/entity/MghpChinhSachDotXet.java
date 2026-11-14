package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_chinh_sach_dot_xet", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpChinhSachDotXet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="dot_xet_id", nullable=false)
    private Long dotXetId;

    @Column(name="phien_ban_chinh_sach_id", nullable=false)
    private Long phienBanChinhSachId;

    @Column(name="tep_mau_don_id", nullable=true)
    private Long tepMauDonId;

    @Column(name="thu_tu_hien_thi", nullable=true)
    private Integer thuTuHienThi;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="dot_xet_id", insertable=false, updatable=false)
    private MghpDotXet dotXet;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="phien_ban_chinh_sach_id", insertable=false, updatable=false)
    private MghpPhienBanChinhSach phienBanChinhSach;

    public MghpChinhSachDotXet() {}

    public Long getId() { return id; }
    public Long getDotXetId() { return dotXetId; }
    public void setDotXetId(Long value) { this.dotXetId = value; }
    public Long getPhienBanChinhSachId() { return phienBanChinhSachId; }
    public void setPhienBanChinhSachId(Long value) { this.phienBanChinhSachId = value; }
    public Long getTepMauDonId() { return tepMauDonId; }
    public void setTepMauDonId(Long value) { this.tepMauDonId = value; }
    public Integer getThuTuHienThi() { return thuTuHienThi; }
    public void setThuTuHienThi(Integer value) { this.thuTuHienThi = value; }
    public MghpDotXet getDotXet() { return dotXet; }
    public MghpPhienBanChinhSach getPhienBanChinhSach() { return phienBanChinhSach; }
}
