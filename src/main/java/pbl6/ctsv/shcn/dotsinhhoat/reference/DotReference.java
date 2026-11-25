package pbl6.ctsv.shcn.dotsinhhoat.reference;

import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;

public interface DotReference {
    Quyen permissions(FoundationAccess.Identity identity);
    void requireSemester(String code);
    NhomLopDTO groups(String code);
    default SemesterAudienceResolver.Projection projection(String code){throw pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure.unavailable();}
    void requireAudiences(String code,java.util.List<DoiTuong> values);
    Page<HocKyDTO> semesters(Query query);
    Page<LopTheoKyDTO> classes(String semester,String type,String selector,Query query);
    Page<SinhVienTheoKyDTO> students(String semester,String classroom,Query query);
}
