package pbl6.ctsv.shcn.dotsinhhoat.reference;

import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;

public interface DotReference {
    Quyen permissions(FoundationAccess.Identity identity);
    void requireSemester(String code);
    Page<HocKyDTO> semesters(Query query);
}
