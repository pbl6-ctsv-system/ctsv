package pbl6.ctsv.foundation.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read-only view of Foundation semesters in the caller's transaction. */
public interface AcademicSemesterSource {
    record Semester(long id, String code, String name, String sourceAcademicYear, LocalDate startDate,
                    LocalDate endDate, String milestoneVerificationStatus, boolean present, byte[] version) {
        public Semester {
            version = version == null ? null : version.clone();
        }

        @Override
        public byte[] version() {
            return version == null ? null : version.clone();
        }
    }

    record Page(List<Semester> items, int page, int size, long totalElements) {
        public Page {
            items = List.copyOf(items);
        }
    }

    Page findPage(String q, int page, int size);

    /** Locks the source through the end of the caller's transaction. */
    Optional<Semester> findById(long id);
}
