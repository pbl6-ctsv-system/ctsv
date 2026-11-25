package pbl6.ctsv.drl.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Nationalized;

/** Computed read model only. Visibility and authorization remain the caller's responsibility. */
@Entity
@Table(name = "`RLKetQuaSV`", schema = "dbo")
@Immutable
public class RlKetQuaSv {
    @Id
    @Column(name = "`PhieuID`", nullable = false)
    private Long phieuId;
    @Column(name = "`SinhVienID`", nullable = false)
    private Long sinhVienId;
    @Column(name = "`LopDotID`", nullable = false)
    private Long lopDotId;
    @Column(name = "`DotID`", nullable = false)
    private Long dotId;
    @Column(name = "`HocKyID`", nullable = false)
    private Long hocKyId;
    @Column(name = "`TongDiemCN`", precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal tongDiemCn;
    @Column(name = "`TongDiemLop`", precision = 5, scale = 2, columnDefinition = "decimal(5,2)")
    private BigDecimal tongDiemLop;
    @Nationalized
    @Column(name = "`XepLoai`", length = 64)
    private String xepLoai;
    @Column(name = "`DaCongBo`", columnDefinition = "bit")
    private Boolean daCongBo;

    protected RlKetQuaSv() {}

    public Long getPhieuId() { return phieuId; }
    public Long getSinhVienId() { return sinhVienId; }
    public Long getLopDotId() { return lopDotId; }
    public Long getDotId() { return dotId; }
    public Long getHocKyId() { return hocKyId; }
    public BigDecimal getTongDiemCn() { return tongDiemCn; }
    public BigDecimal getTongDiemLop() { return tongDiemLop; }
    public String getXepLoai() { return xepLoai; }
    public Boolean getDaCongBo() { return daCongBo; }
}

