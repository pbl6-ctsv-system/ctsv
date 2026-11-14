package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;
import pbl6.ctsv.foundation.units.entity.DonVi;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="phan_quyen_nguoi_dung", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class PhanQuyenNguoiDung {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="nguoi_dung_id", nullable=false)
    private Long nguoiDungId;

    @Column(name="vai_tro_id", nullable=false)
    private Long vaiTroId;

    @Nationalized
    @Column(name="ma_phan_he", nullable=true, length=128)
    private String maPhanHe;

    @Column(name="don_vi_id", nullable=true)
    private Long donViId;

    @Column(name="bao_gom_don_vi_con", nullable=true)
    private Boolean baoGomDonViCon;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="hieu_luc_tu", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime hieuLucTu;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="hieu_luc_den", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime hieuLucDen;

    @Column(name="nguoi_cap_quyen_id", nullable=true)
    private Long nguoiCapQuyenId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_dung_id", insertable=false, updatable=false)
    private NguoiDung nguoiDung;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="vai_tro_id", insertable=false, updatable=false)
    private VaiTro vaiTro;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="don_vi_id", insertable=false, updatable=false)
    private DonVi donVi;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_cap_quyen_id", insertable=false, updatable=false)
    private NguoiDung nguoiCapQuyen;

    public PhanQuyenNguoiDung() {}

    public Long getId() { return id; }
    public Long getNguoiDungId() { return nguoiDungId; }
    public void setNguoiDungId(Long value) { this.nguoiDungId = value; }
    public Long getVaiTroId() { return vaiTroId; }
    public void setVaiTroId(Long value) { this.vaiTroId = value; }
    public String getMaPhanHe() { return maPhanHe; }
    public void setMaPhanHe(String value) { this.maPhanHe = value; }
    public Long getDonViId() { return donViId; }
    public void setDonViId(Long value) { this.donViId = value; }
    public Boolean getBaoGomDonViCon() { return baoGomDonViCon; }
    public void setBaoGomDonViCon(Boolean value) { this.baoGomDonViCon = value; }
    public OffsetDateTime getHieuLucTu() { return hieuLucTu; }
    public void setHieuLucTu(OffsetDateTime value) { this.hieuLucTu = value; }
    public OffsetDateTime getHieuLucDen() { return hieuLucDen; }
    public void setHieuLucDen(OffsetDateTime value) { this.hieuLucDen = value; }
    public Long getNguoiCapQuyenId() { return nguoiCapQuyenId; }
    public void setNguoiCapQuyenId(Long value) { this.nguoiCapQuyenId = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public NguoiDung getNguoiDung() { return nguoiDung; }
    public VaiTro getVaiTro() { return vaiTro; }
    public DonVi getDonVi() { return donVi; }
    public NguoiDung getNguoiCapQuyen() { return nguoiCapQuyen; }
}
