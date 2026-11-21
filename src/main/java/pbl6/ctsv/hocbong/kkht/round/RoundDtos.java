package pbl6.ctsv.hocbong.kkht.round;

import java.util.List;

/** HTTP whitelist. Foundation and persistence objects never cross this boundary. */
final class RoundDtos {
    record Groups(boolean student,boolean specialist) {}
    record DefaultSemester(SourceSemesterDto semester,String reason) {}
    record Page<T>(List<T> items,int page,int size,long totalElements) {
        Page { items=List.copyOf(items); }
    }


    record SourceSemesterDto(String sourceSemesterId,String code,String name,String academicYear,
        Integer semesterOrder,String startDate,String endDate,String scholarshipSemesterId,
        boolean selectable,String unavailableReason) {}
    record SemesterDto(String id,String academicYear,int semesterOrder,String startDate,String endDate,String status) {}
    record RoundDetail(String id,String sourceSemesterId,int number,String name,String status,String createdBy,
        SemesterDto semester,String opensAt,String closedAt,String tuitionSnapshotAt,String ruleVersionId) {}
    record RoundSummary(String id,String sourceSemesterId,String semesterId,String academicYear,int semesterOrder,
        int number,String name,String status) {}
    record View(RoundDetail body,String etag) {}
    private RoundDtos() {}
}
