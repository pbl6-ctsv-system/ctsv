package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="qua_trinh_thuoc_lop", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class QuaTrinhThuocLop {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Column(name="qua_trinh_hoc_id", nullable=true)
    private Long quaTrinhHocId;

    @Column(name="lop_id", nullable=true)
    private Long lopId;

    @Column(name="hieu_luc_tu", nullable=true)
    private LocalDate hieuLucTu;

    @Column(name="hieu_luc_den", nullable=true)
    private LocalDate hieuLucDen;

    @Column(name="he_thong_nguon_id", nullable=true)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ban_ghi_nguon_id", nullable=true, length=128)
    private String banGhiNguonId;

    @Nationalized
    @Column(name="can_cu_xac_minh", nullable=true, columnDefinition="nvarchar(max)")
    private String canCuXacMinh;

    @Column(name="nguoi_xac_minh_id", nullable=true)
    private Long nguoiXacMinhId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_xac_minh", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemXacMinh;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dong_bo", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemDongBo;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="qua_trinh_hoc_id", insertable=false, updatable=false)
    private QuaTrinhTheoHoc quaTrinhHoc;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="lop_id", insertable=false, updatable=false)
    private LopSinhHoat lop;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="nguoi_xac_minh_id", insertable=false, updatable=false)
    private NguoiDung nguoiXacMinh;

    public QuaTrinhThuocLop() {}

    public Long getId() { return id; }
    public Long getQuaTrinhHocId() { return quaTrinhHocId; }
    public void setQuaTrinhHocId(Long value) { this.quaTrinhHocId = value; }
    public Long getLopId() { return lopId; }
    public void setLopId(Long value) { this.lopId = value; }
    public LocalDate getHieuLucTu() { return hieuLucTu; }
    public void setHieuLucTu(LocalDate value) { this.hieuLucTu = value; }
    public LocalDate getHieuLucDen() { return hieuLucDen; }
    public void setHieuLucDen(LocalDate value) { this.hieuLucDen = value; }
    public Long getHeThongNguonId() { return heThongNguonId; }
    public void setHeThongNguonId(Long value) { this.heThongNguonId = value; }
    public String getBanGhiNguonId() { return banGhiNguonId; }
    public void setBanGhiNguonId(String value) { this.banGhiNguonId = value; }
    public String getCanCuXacMinh() { return canCuXacMinh; }
    public void setCanCuXacMinh(String value) { this.canCuXacMinh = value; }
    public Long getNguoiXacMinhId() { return nguoiXacMinhId; }
    public void setNguoiXacMinhId(Long value) { this.nguoiXacMinhId = value; }
    public OffsetDateTime getThoiDiemXacMinh() { return thoiDiemXacMinh; }
    public void setThoiDiemXacMinh(OffsetDateTime value) { this.thoiDiemXacMinh = value; }
    public OffsetDateTime getThoiDiemDongBo() { return thoiDiemDongBo; }
    public void setThoiDiemDongBo(OffsetDateTime value) { this.thoiDiemDongBo = value; }
    public QuaTrinhTheoHoc getQuaTrinhHoc() { return quaTrinhHoc; }
    public LopSinhHoat getLop() { return lop; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
    public NguoiDung getNguoiXacMinh() { return nguoiXacMinh; }
}
