package pbl6.ctsv.foundation.mapper;

import java.time.*;
import java.util.*;
import pbl6.ctsv.foundation.dto.FoundationDtos.*;
import pbl6.ctsv.foundation.entity.*;

public final class FoundationMapper {
    public static String id(Long id) { return id == null ? null : id.toString(); }
    private static String date(LocalDate value) { return value == null ? null : value.toString(); }
    private static String instant(OffsetDateTime value) { return value == null ? null : value.toInstant().toString(); }
    public static StudentSummary studentSummary(SinhVien s) {
        return new StudentSummary(id(s.getId()),s.getMssv(),s.getHoTen(),id(s.getLopHienTaiId()),id(s.getKhoaQuanLyHienTaiId()));
    }
    public static StudentDetail student(SinhVien s,List<QuaTrinhTheoHoc> processes) {
        return new StudentDetail(id(s.getId()),s.getMssv(),s.getHoTen(),id(s.getLopHienTaiId()),id(s.getKhoaQuanLyHienTaiId()),
            id(s.getHeThongNguonId()),s.getMaDinhDanhBenNgoai(),instant(s.getThoiDiemDongBo()),s.getTrangThaiChuanHoa(),
            processes.stream().map(p -> new StudyProcess(id(p.getId()),p.getMaQuaTrinhHoc(),id(p.getChuongTrinhDaoTaoId()),
                id(p.getKhoaHocId()),id(p.getHocKyBatDauId()),p.getTrangThaiDoiChieu(),p.getCanCuXacMinh())).toList());
    }
    public static SemesterDetail semester(HocKy h) {
        return new SemesterDetail(id(h.getId()),h.getMa(),h.getTen(),h.getSoThuTu(),date(h.getNgayBatDau()),date(h.getNgayKetThuc()),h.getTrangThaiXacMinhMoc(),h.getCanCuXacMinhMoc());
    }
    public static Map<String,Object> process(QuaTrinhTheoHoc p,List<LichSuTrangThaiSv> statuses,List<QuaTrinhThuocLop> classes) {
        return fields("processId",id(p.getId()),"code",p.getMaQuaTrinhHoc(),"programId",id(p.getChuongTrinhDaoTaoId()),"cohortId",id(p.getKhoaHocId()),
            "startSemesterId",id(p.getHocKyBatDauId()),"endSemesterId",id(p.getHocKyKetThucNguonId()),"actualStart",date(p.getThoiDiemBatDau()),
            "expectedEnd",date(p.getNgayKetThucDuKien()),"actualEnd",date(p.getNgayKetThucThucTe()),"evidence",p.getCanCuXacMinh(),
            "evidenceFileId",id(p.getTepCanCuId()),"verificationStatus",p.getTrangThaiDoiChieu(),
            "statusHistory",statuses.stream().map(s -> fields("id",id(s.getId()),"semesterId",id(s.getHocKyApDungId()),"status",s.getTrangThai(),
                "from",date(s.getNgayBatDauHieuLuc()),"to",date(s.getNgayKetThucHieuLuc()),"decisionNumber",s.getSoQdNguon(),"decisionDate",date(s.getNgayQdNguon()),"evidence",s.getCanCuXacMinh(),"verifiedAt",instant(s.getThoiDiemXacMinh()))).toList(),
            "classHistory",classes.stream().map(c -> fields("id",id(c.getId()),"classId",id(c.getLopId()),"from",date(c.getHieuLucTu()),
                "until",date(c.getHieuLucDen()),"evidence",c.getCanCuXacMinh(),"verifiedAt",instant(c.getThoiDiemXacMinh()))).toList());
    }
    public static Map<String,Object> role(PhanQuyenNguoiDung p) {
        return fields("assignmentId",id(p.getId()),"roleId",id(p.getVaiTroId()),"roleCode",p.getVaiTro().getMa(),"module",p.getMaPhanHe(),
            "unitId",id(p.getDonViId()),"effectiveFrom",instant(p.getHieuLucTu()),"effectiveUntil",instant(p.getHieuLucDen()));
    }
    private static Map<String,Object> fields(Object... values) {
        var result = new LinkedHashMap<String,Object>();
        for(int i=0;i<values.length;i+=2) result.put((String)values[i],values[i+1]);
        return result;
    }
    private FoundationMapper() {}
}
