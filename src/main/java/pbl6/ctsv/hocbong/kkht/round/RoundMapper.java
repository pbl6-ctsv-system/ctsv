package pbl6.ctsv.hocbong.kkht.round;

import java.time.OffsetDateTime;
import pbl6.ctsv.hocbong.kkht.round.entity.*;

final class RoundMapper {
    static RoundDtos.SemesterDto semester(ScholarshipSemester s) {
        return new RoundDtos.SemesterDto(s.getId().toString(),s.getAcademicYear(),s.getSemesterOrder(),
            s.getStartDate().toString(),s.getEndDate().toString(),s.getStatus());
    }
    static RoundDtos.RoundDetail detail(ScholarshipRound r,ScholarshipSemester s,SourceSemesterMapping m) {
        return new RoundDtos.RoundDetail(r.getId().toString(),m.getSourceSemesterCode(),r.getNumber(),r.getName(),
            r.getStatus(),r.getCreatedBy().toString(),semester(s),instant(r.getOpensAt()),instant(r.getClosedAt()),
            instant(r.getTuitionSnapshotAt()),r.getRuleVersionId()==null?null:r.getRuleVersionId().toString());
    }
    static RoundDtos.RoundSummary summary(ScholarshipRound r,ScholarshipSemester s,SourceSemesterMapping m) {
        return new RoundDtos.RoundSummary(r.getId().toString(),m.getSourceSemesterCode(),s.getId().toString(),
            s.getAcademicYear(),s.getSemesterOrder(),r.getNumber(),r.getName(),r.getStatus());
    }
    private static String instant(OffsetDateTime value) { return value==null?null:value.toInstant().toString(); }
    private RoundMapper() {}
}
