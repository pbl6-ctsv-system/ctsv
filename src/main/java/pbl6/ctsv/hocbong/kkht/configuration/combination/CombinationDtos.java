package pbl6.ctsv.hocbong.kkht.configuration.combination;

import java.time.LocalDate;
import java.util.List;

public final class CombinationDtos {
    private CombinationDtos() {}

    public record Command(long programId, long cohortId, long majorId, String curriculumCode,
            int standardSemesters, String programType, LocalDate effectiveFrom, LocalDate effectiveTo) {}

    public record Detail(String id, String programId, String programCode, String programName,
            String cohortId, String cohortCode, String majorId, String majorCode, String curriculumCode,
            int standardSemesters, String programType, LocalDate effectiveFrom, LocalDate effectiveTo) {}

    public record Page<T>(List<T> items, int page, int size, long totalElements) {
        public Page { items = List.copyOf(items); }
    }

    public record View(Detail body, String etag) {}
}
