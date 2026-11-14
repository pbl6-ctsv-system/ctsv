package pbl6.ctsv.mghp.configuration;

import java.util.Map;
import java.time.*;
import java.math.BigDecimal;
import pbl6.ctsv.mghp.configuration.entity.*;

/** Typed assignments for INSERT only. Aggregate updates keep their SQL Server CAS fence. */
final class ConfigurationEntities {
    static Object create(String table,Map<String,Object> values) {
        return switch(table) {
            case "mghp_chinh_sach" -> mghp_chinh_sach(values);
            case "mghp_phien_ban_chinh_sach" -> mghp_phien_ban_chinh_sach(values);
            case "mghp_tieu_chi_chinh_sach" -> mghp_tieu_chi_chinh_sach(values);
            case "mghp_nhom_yeu_cau_giay_to" -> mghp_nhom_yeu_cau_giay_to(values);
            case "mghp_giay_to_bat_buoc" -> mghp_giay_to_bat_buoc(values);
            case "mghp_dot_xet" -> mghp_dot_xet(values);
            case "mghp_chinh_sach_dot_xet" -> mghp_chinh_sach_dot_xet(values);
            default -> throw new IllegalArgumentException("Not a configuration entity: "+table);
        };
    }
    private static MghpChinhSach mghp_chinh_sach(Map<String,Object> values) {
        var entity=new MghpChinhSach();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "ma" -> entity.setMa((String)v);
                case "ten" -> entity.setTen((String)v);
                case "nhom_chinh_sach" -> entity.setNhomChinhSach((String)v);
                case "dang_hoat_dong" -> entity.setDangHoatDong((Boolean)v);
                case "thoi_diem_tao" -> entity.setThoiDiemTao((OffsetDateTime)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpPhienBanChinhSach mghp_phien_ban_chinh_sach(Map<String,Object> values) {
        var entity=new MghpPhienBanChinhSach();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "chinh_sach_id" -> entity.setChinhSachId((Long)v);
                case "phien_ban" -> entity.setPhienBan((Integer)v);
                case "ten_tai_thoi_diem_chot" -> entity.setTenTaiThoiDiemChot((String)v);
                case "ty_le_giam" -> entity.setTyLeGiam((BigDecimal)v);
                case "ma_pham_vi" -> entity.setMaPhamVi((String)v);
                case "ngay_bat_dau_hieu_luc" -> entity.setNgayBatDauHieuLuc(date(v));
                case "ngay_ket_thuc_hieu_luc" -> entity.setNgayKetThucHieuLuc(date(v));
                case "can_cu_phap_ly" -> entity.setCanCuPhapLy((String)v);
                case "phien_ban_quy_tac_xet" -> entity.setPhienBanQuyTacXet((String)v);
                case "thoi_han_huong" -> entity.setThoiHanHuong((String)v);
                case "uu_tien_khi_cung_muc" -> entity.setUuTienKhiCungMuc((Integer)v);
                case "trang_thai" -> entity.setTrangThai((String)v);
                case "nguoi_tao_id" -> entity.setNguoiTaoId((Long)v);
                case "thoi_diem_tao" -> entity.setThoiDiemTao((OffsetDateTime)v);
                case "thoi_diem_khoa" -> entity.setThoiDiemKhoa((OffsetDateTime)v);
                case "so_van_ban_ap_dung" -> entity.setSoVanBanApDung((String)v);
                case "dieu_khoan_ap_dung" -> entity.setDieuKhoanApDung((String)v);
                case "url_can_cu" -> entity.setUrlCanCu((String)v);
                case "che_do_cap_nhat_minh_chung" -> entity.setCheDoCapNhatMinhChung((String)v);
                case "huong_dan_cap_nhat_minh_chung" -> entity.setHuongDanCapNhatMinhChung((String)v);
                case "tinh_trang_xac_nhan_quy_tac" -> entity.setTinhTrangXacNhanQuyTac((String)v);
                case "tuoi_toi_thieu" -> entity.setTuoiToiThieu((Integer)v);
                case "tuoi_toi_da" -> entity.setTuoiToiDa((Integer)v);
                case "quy_tac_moc_tuoi" -> entity.setQuyTacMocTuoi((String)v);
                case "yeu_cau_van_bang_thu_nhat" -> entity.setYeuCauVanBangThuNhat((Boolean)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpTieuChiChinhSach mghp_tieu_chi_chinh_sach(Map<String,Object> values) {
        var entity=new MghpTieuChiChinhSach();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "phien_ban_chinh_sach_id" -> entity.setPhienBanChinhSachId((Long)v);
                case "ma" -> entity.setMa((String)v);
                case "ten" -> entity.setTen((String)v);
                case "huong_dan_xet" -> entity.setHuongDanXet((String)v);
                case "bat_buoc" -> entity.setBatBuoc((Boolean)v);
                case "co_dieu_kien_ap_dung" -> entity.setCoDieuKienApDung((Boolean)v);
                case "huong_dan_ap_dung" -> entity.setHuongDanApDung((String)v);
                case "thu_tu_hien_thi" -> entity.setThuTuHienThi((Integer)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpNhomYeuCauGiayTo mghp_nhom_yeu_cau_giay_to(Map<String,Object> values) {
        var entity=new MghpNhomYeuCauGiayTo();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "phien_ban_chinh_sach_id" -> entity.setPhienBanChinhSachId((Long)v);
                case "ma" -> entity.setMa((String)v);
                case "ten" -> entity.setTen((String)v);
                case "so_yeu_cau_toi_thieu_dat" -> entity.setSoYeuCauToiThieuDat((Integer)v);
                case "thu_tu_hien_thi" -> entity.setThuTuHienThi((Integer)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpGiayToBatBuoc mghp_giay_to_bat_buoc(Map<String,Object> values) {
        var entity=new MghpGiayToBatBuoc();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "nhom_id" -> entity.setNhomId((Long)v);
                case "ma" -> entity.setMa((String)v);
                case "ten" -> entity.setTen((String)v);
                case "loai_giay_to" -> entity.setLoaiGiayTo((String)v);
                case "can_doi_chieu_ban_giay" -> entity.setCanDoiChieuBanGiay((Boolean)v);
                case "tuoi_toi_da_giay_to_theo_ngay" -> entity.setTuoiToiDaGiayToTheoNgay((Integer)v);
                case "thu_tu_hien_thi" -> entity.setThuTuHienThi((Integer)v);
                case "can_ban_sao_chung_thuc" -> entity.setCanBanSaoChungThuc((Boolean)v);
                case "can_dau_xac_nhan" -> entity.setCanDauXacNhan((Boolean)v);
                case "noi_dung_xac_nhan" -> entity.setNoiDungXacNhan((String)v);
                case "co_quan_xac_nhan" -> entity.setCoQuanXacNhan((String)v);
                case "huong_dan_kiem_tra" -> entity.setHuongDanKiemTra((String)v);
                case "chu_the_minh_chung" -> entity.setChuTheMinhChung((String)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpDotXet mghp_dot_xet(Map<String,Object> values) {
        var entity=new MghpDotXet();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "ma_dot_xet" -> entity.setMaDotXet((String)v);
                case "hoc_ky_id" -> entity.setHocKyId((Long)v);
                case "thong_bao_dot_xet_id" -> entity.setThongBaoDotXetId((Long)v);
                case "ten" -> entity.setTen((String)v);
                case "mo_ta" -> entity.setMoTa((String)v);
                case "thoi_diem_mo" -> entity.setThoiDiemMo((OffsetDateTime)v);
                case "han_nop" -> entity.setHanNop((OffsetDateTime)v);
                case "han_ra_soat" -> entity.setHanRaSoat((OffsetDateTime)v);
                case "trang_thai" -> entity.setTrangThai((String)v);
                case "phien_ban_dong" -> entity.setPhienBanDong((Integer)v);
                case "nguoi_khoa_id" -> entity.setNguoiKhoaId((Long)v);
                case "thoi_diem_khoa" -> entity.setThoiDiemKhoa((OffsetDateTime)v);
                case "nguoi_chot_hoi_dong_id" -> entity.setNguoiChotHoiDongId((Long)v);
                case "thoi_diem_hoi_dong_chot" -> entity.setThoiDiemHoiDongChot((OffsetDateTime)v);
                case "ly_do_huy" -> entity.setLyDoHuy((String)v);
                case "nguoi_tao_id" -> entity.setNguoiTaoId((Long)v);
                case "thoi_diem_tao" -> entity.setThoiDiemTao((OffsetDateTime)v);
                case "thoi_diem_cap_nhat" -> entity.setThoiDiemCapNhat((OffsetDateTime)v);
                case "huong_dan_ke_khai" -> entity.setHuongDanKeKhai((String)v);
                case "cho_phep_truy_linh" -> entity.setChoPhepTruyLinh((Boolean)v);
                case "nguoi_khoa_cau_hinh_id" -> entity.setNguoiKhoaCauHinhId((Long)v);
                case "thoi_diem_khoa_cau_hinh" -> entity.setThoiDiemKhoaCauHinh((OffsetDateTime)v);
                case "che_do_truy_linh" -> entity.setCheDoTruyLinh((String)v);
                case "cach_xet_thay_doi_giua_ky" -> entity.setCachXetThayDoiGiuaKy((String)v);
                case "huong_dan_can_cu_nop_muon" -> entity.setHuongDanCanCuNopMuon((String)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static MghpChinhSachDotXet mghp_chinh_sach_dot_xet(Map<String,Object> values) {
        var entity=new MghpChinhSachDotXet();
        for(var entry:values.entrySet()) {
            Object v=entry.getValue();
            switch(entry.getKey()) {
                case "dot_xet_id" -> entity.setDotXetId((Long)v);
                case "phien_ban_chinh_sach_id" -> entity.setPhienBanChinhSachId((Long)v);
                case "tep_mau_don_id" -> entity.setTepMauDonId((Long)v);
                case "thu_tu_hien_thi" -> entity.setThuTuHienThi((Integer)v);
                default -> throw new IllegalArgumentException("Unmapped configuration field: "+entry.getKey());
            }
        }
        return entity;
    }
    private static LocalDate date(Object v) { return v==null?null:v instanceof java.sql.Date d?d.toLocalDate():(LocalDate)v; }
    private ConfigurationEntities() {}
}
