package pbl6.ctsv.foundation.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only staff projections using CTSV IDs and the caller's local transaction. */
public interface StaffReferenceSource {
    /** User and current employing unit links may be absent. */
    record Staff(long id, String code, String fullName, Long userId, Long employingUnitId, String positionCode) {}

    Optional<Staff> findById(long staffId);

    /** Excludes source records marked retired or archived. */
    List<Staff> findServingStaffByUnitAndPositionCodes(long unitId, Set<String> positionCodes);

    /** Searches staff in the class's managing unit by a literal name or code fragment. */
    List<Staff> findStaffByClassManagingUnit(long classId, String query);

    /** Returns the distinct staff referenced by the homeroom assignment FK of the class and semester. */
    List<Staff> findAssignedHomeroomTeachers(long semesterId, long classId);
}
