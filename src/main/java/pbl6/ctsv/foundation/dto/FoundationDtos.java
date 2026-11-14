package pbl6.ctsv.foundation.dto;

import java.util.List;

/** Explicit API projections; persistence entities never cross the HTTP/module boundary. */
public final class FoundationDtos {
    public record Page<T>(List<T> items, int page, int size, long totalElements) {}
    public record StudentSummary(String id, String studentCode, String fullName, String currentClassId, String managementUnitId) {}
    public record LookupItem(String id, String code, String name) {}
    public record StudyProcess(String id, String code, String programId, String cohortId, String startSemesterId, String verificationStatus, String evidence) {}
    public record StudentDetail(String id, String studentCode, String fullName, String currentClassId, String managementUnitId,
        String sourceSystemId, String sourceId, String sourceObservedAt, String normalizationStatus, List<StudyProcess> studyProcesses) {}
    public record SemesterDetail(String id, String code, String name, Integer order, String startDate, String endDate, String verificationStatus, String evidence) {}
    private FoundationDtos() {}
}
