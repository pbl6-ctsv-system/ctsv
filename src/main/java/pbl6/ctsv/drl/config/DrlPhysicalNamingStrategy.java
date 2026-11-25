package pbl6.ctsv.drl.config;

import java.util.Set;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Exact quoted V17/V26 identifiers only; every other identifier delegates to Boot's default.
 * Vietnamese collation does not equate all mixed-case digraphs with their lower-case spelling.
 */
public final class DrlPhysicalNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {
    private static final Set<String> TABLES = Set.of(
            "RLBieuMau", "RLNhomTieuChi", "RLBieuMauCT",
            "RLXepLoai", "RLDot", "RLKhoaDot",
            "RLLopDot", "RLPhieu", "RLPhieuDiemCT",
            "RLHopLop", "RLVanBanKy", "RLKetQuaSV");
    private static final Set<String> COLUMNS = Set.of(
            "ID", "MaBieuMau", "SoPhienBan", "TenBieuMau",
            "DiemToiDa", "HuongDanChung", "BieuMauID", "TenNhom",
            "ThuTu", "NhomTieuChiID", "MaNoiDung", "LoaiDong",
            "TieuChiChaID", "NoiDung", "MucLuc", "DiemToiThieu",
            "TenXepLoai", "MaDot", "TenDot", "HocKyID",
            "BatDauSV", "KetThucSV", "BatDauLop", "KetThucLop",
            "BatDauKhoa", "KetThucKhoa", "TrangThai", "DotID",
            "KhoaID", "DaiDienKhoaID", "NgayGuiCTSV", "KhoaDotID",
            "LopID", "LopTruongID", "GVCNID", "NgayGuiKhoa",
            "NguoiXNCTSVID", "NgayXNCTSV", "NgayLapHoSo", "LopDotID",
            "SinhVienID", "XacNhanSV", "NgayXNSV", "XacNhanLop",
            "NgayXNLop", "ThamDuHop", "KhongDanhGia", "PhieuID",
            "BieuMauCTID", "DiemCN", "DiemLop", "BatDauHop",
            "KetThucHop", "DiaDiem", "KienNghi", "LoaiVanBan",
            "PhienBanVanBanID", "TongDiemCN", "TongDiemLop", "XepLoai",
            "DaCongBo");

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment environment) {
        if (exact(name, TABLES)) return name;
        return super.toPhysicalTableName(name, environment);
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment environment) {
        if (exact(name, COLUMNS)) return name;
        return super.toPhysicalColumnName(name, environment);
    }

    private static boolean exact(Identifier name, Set<String> names) {
        return name != null && name.isQuoted() && names.contains(name.getText());
    }
}

