package pbl6.ctsv.auth;

import java.util.UUID;

/** Public boundary for callers in the CTSV application. */
public interface AuthAccess {
    record Principal(String issuer, UUID accountId, long tokenVersion) {}
    record State(UUID accountId, boolean active, String tokenVersion, boolean ctsvAccess) {}

    Principal verifyAccessToken(String token);
    State requireCtsvAccess(Principal principal, UUID mappedAccountId);
}
