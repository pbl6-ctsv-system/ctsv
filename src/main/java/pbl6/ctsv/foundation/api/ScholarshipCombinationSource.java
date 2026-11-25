package pbl6.ctsv.foundation.api;

import java.util.List;

/** Read-only view of present Foundation resources in the caller's transaction. */
public interface ScholarshipCombinationSource {
    enum Kind { PROGRAMS, COHORTS, MAJORS, CURRICULA }

    /** Curriculum options use the source code as the ID. */
    record Option(String id, String code, String name, String majorId) {}

    record Page(List<Option> items, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }

    record Resolved(long programId, String programCode, String programName, long cohortId, String cohortCode,
                    long majorId, String majorCode, String curriculumCode) {}

    Page findPage(Kind kind, String q, int page, int size);

    /** Validates selection and locks the resolved sources until the caller's transaction ends. */
    Resolved resolve(long programId, long cohortId, long majorId, String curriculumCode);
}
