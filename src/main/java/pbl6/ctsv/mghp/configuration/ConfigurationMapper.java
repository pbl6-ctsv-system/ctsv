package pbl6.ctsv.mghp.configuration;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import pbl6.ctsv.mghp.configuration.entity.*;

/** Explicit contract whitelist. Never serialize an Entity or its lazy relations. */
final class ConfigurationMapper {
    static Map<String,Object> policy(MghpPhienBanChinhSach e) {
        var m=new LinkedHashMap<String,Object>();
        m.put("id",value(e.getId()));
        m.put("policyId",value(e.getChinhSachId()));
        m.put("version",value(e.getPhienBan()));
        m.put("name",value(e.getTenTaiThoiDiemChot()));
        m.put("rate",value(e.getTyLeGiam()));
        m.put("scopeCode",value(e.getMaPhamVi()));
        m.put("effectiveFrom",value(e.getNgayBatDauHieuLuc()));
        m.put("effectiveUntil",value(e.getNgayKetThucHieuLuc()));
        m.put("legalBasis",value(e.getCanCuPhapLy()));
        m.put("ruleVersion",value(e.getPhienBanQuyTacXet()));
        m.put("benefitDuration",value(e.getThoiHanHuong()));
        m.put("tiePriority",value(e.getUuTienKhiCungMuc()));
        m.put("documentNumber",value(e.getSoVanBanApDung()));
        m.put("article",value(e.getDieuKhoanApDung()));
        m.put("sourceUrl",value(e.getUrlCanCu()));
        m.put("evidenceUpdateMode",value(e.getCheDoCapNhatMinhChung()));
        m.put("evidenceUpdateGuidance",value(e.getHuongDanCapNhatMinhChung()));
        m.put("minimumAge",value(e.getTuoiToiThieu()));
        m.put("maximumAge",value(e.getTuoiToiDa()));
        m.put("ageReferenceRule",value(e.getQuyTacMocTuoi()));
        m.put("firstDegreeRequired",value(e.getYeuCauVanBangThuNhat()));
        m.put("status",value(e.getTrangThai()));
        m.put("ruleVerificationStatus",value(e.getTinhTrangXacNhanQuyTac()));
        m.put("lockedAt",value(e.getThoiDiemKhoa()));
        return m;
    }
    static Map<String,Object> criterion(MghpTieuChiChinhSach e) {
        var m=new LinkedHashMap<String,Object>();
        m.put("id",value(e.getId()));
        m.put("code",value(e.getMa()));
        m.put("name",value(e.getTen()));
        m.put("assessmentGuidance",value(e.getHuongDanXet()));
        m.put("required",value(e.getBatBuoc()));
        m.put("conditional",value(e.getCoDieuKienApDung()));
        m.put("applicabilityGuidance",value(e.getHuongDanApDung()));
        m.put("order",value(e.getThuTuHienThi()));
        return m;
    }
    static Map<String,Object> group(MghpNhomYeuCauGiayTo e) {
        var m=new LinkedHashMap<String,Object>();
        m.put("id",value(e.getId()));
        m.put("code",value(e.getMa()));
        m.put("name",value(e.getTen()));
        m.put("minimumRequired",value(e.getSoYeuCauToiThieuDat()));
        m.put("order",value(e.getThuTuHienThi()));
        return m;
    }
    static Map<String,Object> document(MghpGiayToBatBuoc e) {
        var m=new LinkedHashMap<String,Object>();
        m.put("id",value(e.getId()));
        m.put("code",value(e.getMa()));
        m.put("name",value(e.getTen()));
        m.put("type",value(e.getLoaiGiayTo()));
        m.put("hardCopyRequired",value(e.getCanDoiChieuBanGiay()));
        m.put("maxAgeDays",value(e.getTuoiToiDaGiayToTheoNgay()));
        m.put("order",value(e.getThuTuHienThi()));
        m.put("certifiedCopyRequired",value(e.getCanBanSaoChungThuc()));
        m.put("stampRequired",value(e.getCanDauXacNhan()));
        m.put("confirmationContent",value(e.getNoiDungXacNhan()));
        m.put("confirmingAuthority",value(e.getCoQuanXacNhan()));
        m.put("reviewGuidance",value(e.getHuongDanKiemTra()));
        m.put("evidenceSubject",value(e.getChuTheMinhChung()));
        return m;
    }
    static Map<String,Object> round(MghpDotXet e,boolean manager) {
        var m=new LinkedHashMap<String,Object>();
        m.put("id",value(e.getId()));
        m.put("code",value(e.getMaDotXet()));
        m.put("semesterId",value(e.getHocKyId()));
        m.put("name",value(e.getTen()));
        m.put("description",value(e.getMoTa()));
        m.put("opensAt",value(e.getThoiDiemMo()));
        m.put("submissionDeadline",value(e.getHanNop()));
        m.put("reviewDeadline",value(e.getHanRaSoat()));
        m.put("allowRetroactive",value(e.getChoPhepTruyLinh()));
        m.put("retroactiveMode",value(e.getCheDoTruyLinh()));
        m.put("midSemesterMode",value(e.getCachXetThayDoiGiuaKy()));
        m.put("status",value(e.getTrangThai()));
        if(manager){m.put("configLockedAt",value(e.getThoiDiemKhoaCauHinh()));
            m.put("schedule",RoundTimelineRepository.plan(e.getLichTrinh()).view());
            m.put("reviewClosedAt",value(e.getThoiDiemDongXet()));m.put("reviewClosedByUserId",value(e.getNguoiDongXetId()));
            m.put("reviewSourceSha256",e.getSha256NguonDongXet());}
        return m;
    }
    static Object value(Object v) {
        if(v instanceof Long)return v.toString();
        if(v instanceof BigDecimal d)return d.toPlainString();
        if(v instanceof LocalDate d)return d.toString();
        if(v instanceof OffsetDateTime d)return d.toInstant().toString();
        return v;
    }
    private ConfigurationMapper() {}
}
