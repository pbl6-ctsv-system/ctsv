package pbl6.ctsv.auth;

import java.util.UUID;

/** Public boundary for callers in the CTSV application. */
public interface AuthAccess {
    record Principal(String issuer, UUID accountId, long tokenVersion) {}
    record State(UUID accountId, boolean active, String tokenVersion, boolean ctsvAccess) {}

    Principal verifyAccessToken(String token);
    State requireCtsvAccess(Principal principal, UUID mappedAccountId);
    /** Internal worker boundary, only after durable user confirmation. Not for authenticating HTTP requests.
     * Recheck current account/CTSV access; ordinary session revocation does not withdraw accepted work.
     * Custom implementations retain the stricter existing behavior until explicitly supported.
     */
    default State requireAcceptedWorkAccess(Principal accepted, UUID mappedAccountId) {
        return requireCtsvAccess(accepted,mappedAccountId);
    }
}
