package pbl6.ctsv.mghp.configuration.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** MGHP/P05-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="mghp_dot_xet", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class MghpDotXet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma_dot_xet", nullable=false, length=128)
    private String maDotXet;

    @Column(name="hoc_ky_id", nullable=false)
    private Long hocKyId;

    @Column(name="thong_bao_dot_xet_id", nullable=true)
    private Long thongBaoDotXetId;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Nationalized
    @Column(name="mo_ta", nullable=true, columnDefinition="nvarchar(max)")
    private String moTa;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_mo", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemMo;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="han_nop", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime hanNop;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="han_ra_soat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime hanRaSoat;

    @Nationalized
    @Column(name="trang_thai", nullable=true, length=128)
    private String trangThai;

    @Column(name="phien_ban_dong", nullable=true)
    private Integer phienBanDong;

    @Column(name="nguoi_khoa_id", nullable=true)
    private Long nguoiKhoaId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_khoa", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemKhoa;

    @Column(name="nguoi_chot_hoi_dong_id", nullable=true)
    private Long nguoiChotHoiDongId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_hoi_dong_chot", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemHoiDongChot;

    @Nationalized
    @Column(name="ly_do_huy", nullable=true, columnDefinition="nvarchar(max)")
    private String lyDoHuy;

    @Column(name="nguoi_tao_id", nullable=true)
    private Long nguoiTaoId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;


    @Column(name="cho_phep_truy_linh", nullable=true)
    private Boolean choPhepTruyLinh;

    @Column(name="nguoi_khoa_cau_hinh_id", nullable=true)
    private Long nguoiKhoaCauHinhId;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_khoa_cau_hinh", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemKhoaCauHinh;

    @Nationalized
    @Column(name="che_do_truy_linh", nullable=true, length=128)
    private String cheDoTruyLinh;

    @Nationalized
    @Column(name="cach_xet_thay_doi_giua_ky", nullable=true, length=128)
    private String cachXetThayDoiGiuaKy;


    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY)
    @Column(name="phien_ban", nullable=false, columnDefinition="rowversion", insertable=false, updatable=false, length=8)
    private byte[] phienBan;

    @Embedded private RoundSchedule lichTrinh;
    @Column(name="nguoi_dong_xet_id") private Long nguoiDongXetId;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dong_xet",columnDefinition="datetimeoffset(7)") private OffsetDateTime thoiDiemDongXet;
    @Column(name="sha256_nguon_dong_xet",length=64) private String sha256NguonDongXet;
    public RoundSchedule getLichTrinh(){return lichTrinh==null?new RoundSchedule():lichTrinh;}
    public void setLichTrinh(RoundSchedule value){lichTrinh=value;}
    public Long getNguoiDongXetId(){return nguoiDongXetId;}
    public void setNguoiDongXetId(Long value){nguoiDongXetId=value;}
    public OffsetDateTime getThoiDiemDongXet(){return thoiDiemDongXet;}
    public void setThoiDiemDongXet(OffsetDateTime value){thoiDiemDongXet=value;}
    public String getSha256NguonDongXet(){return sha256NguonDongXet;}
    public void setSha256NguonDongXet(String value){sha256NguonDongXet=value;}

    public MghpDotXet() {}

    public Long getId() { return id; }
    public String getMaDotXet() { return maDotXet; }
    public void setMaDotXet(String value) { this.maDotXet = value; }
    public Long getHocKyId() { return hocKyId; }
    public void setHocKyId(Long value) { this.hocKyId = value; }
    public Long getThongBaoDotXetId() { return thongBaoDotXetId; }
    public void setThongBaoDotXetId(Long value) { this.thongBaoDotXetId = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getMoTa() { return moTa; }
    public void setMoTa(String value) { this.moTa = value; }
    public OffsetDateTime getThoiDiemMo() { return thoiDiemMo; }
    public void setThoiDiemMo(OffsetDateTime value) { this.thoiDiemMo = value; }
    public OffsetDateTime getHanNop() { return hanNop; }
    public void setHanNop(OffsetDateTime value) { this.hanNop = value; }
    public OffsetDateTime getHanRaSoat() { return hanRaSoat; }
    public void setHanRaSoat(OffsetDateTime value) { this.hanRaSoat = value; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String value) { this.trangThai = value; }
    public Integer getPhienBanDong() { return phienBanDong; }
    public void setPhienBanDong(Integer value) { this.phienBanDong = value; }
    public Long getNguoiKhoaId() { return nguoiKhoaId; }
    public void setNguoiKhoaId(Long value) { this.nguoiKhoaId = value; }
    public OffsetDateTime getThoiDiemKhoa() { return thoiDiemKhoa; }
    public void setThoiDiemKhoa(OffsetDateTime value) { this.thoiDiemKhoa = value; }
    public Long getNguoiChotHoiDongId() { return nguoiChotHoiDongId; }
    public void setNguoiChotHoiDongId(Long value) { this.nguoiChotHoiDongId = value; }
    public OffsetDateTime getThoiDiemHoiDongChot() { return thoiDiemHoiDongChot; }
    public void setThoiDiemHoiDongChot(OffsetDateTime value) { this.thoiDiemHoiDongChot = value; }
    public String getLyDoHuy() { return lyDoHuy; }
    public void setLyDoHuy(String value) { this.lyDoHuy = value; }
    public Long getNguoiTaoId() { return nguoiTaoId; }
    public void setNguoiTaoId(Long value) { this.nguoiTaoId = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
    public Boolean getChoPhepTruyLinh() { return choPhepTruyLinh; }
    public void setChoPhepTruyLinh(Boolean value) { this.choPhepTruyLinh = value; }
    public Long getNguoiKhoaCauHinhId() { return nguoiKhoaCauHinhId; }
    public void setNguoiKhoaCauHinhId(Long value) { this.nguoiKhoaCauHinhId = value; }
    public OffsetDateTime getThoiDiemKhoaCauHinh() { return thoiDiemKhoaCauHinh; }
    public void setThoiDiemKhoaCauHinh(OffsetDateTime value) { this.thoiDiemKhoaCauHinh = value; }
    public String getCheDoTruyLinh() { return cheDoTruyLinh; }
    public void setCheDoTruyLinh(String value) { this.cheDoTruyLinh = value; }
    public String getCachXetThayDoiGiuaKy() { return cachXetThayDoiGiuaKy; }
    public void setCachXetThayDoiGiuaKy(String value) { this.cachXetThayDoiGiuaKy = value; }
    public byte[] getPhienBan() { return phienBan == null ? null : phienBan.clone(); }
}
