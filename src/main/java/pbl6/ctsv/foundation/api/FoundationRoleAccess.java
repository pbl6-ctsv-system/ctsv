package pbl6.ctsv.foundation.api;

import java.util.Optional;

/** Looks up local global grants for an identified user in the caller's transaction. */
public interface FoundationRoleAccess {
    record ActiveRole(long roleId) {}

    Optional<ActiveRole> findActiveGlobalRole(long userId, String moduleCode, String roleCode);
}
