package pbl6.ctsv.shared.ca;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.time.*;
import java.util.Base64;
import java.util.function.Function;

/** Server-only in-memory tokens. A single monitor makes refresh rotation single-flight. */
public final class CaTokenManager {
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final ObjectReader TOKEN_JSON = new ObjectMapper().reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Tokens(String access, String refresh) {
        public Tokens {
            if (access == null || refresh == null || access.isBlank() || refresh.isBlank()
                    || access.length()>16000 || refresh.length()>16000 || !access.matches("[A-Za-z0-9._~+/\\-]+=*"))
                throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
        }
        @Override public String toString() { return "Tokens[REDACTED]"; }
    }
    private final Function<String,Tokens> endpoint;
    private final Clock clock;
    private record CachedTokens(Tokens tokens, Instant receivedAt, Instant expiresAt) {}
    private CachedTokens cached;
    public CaTokenManager(Function<String,Tokens> endpoint, Clock clock) { this.endpoint=endpoint;this.clock=clock; }
    public synchronized String access() {
        if(cached == null || !clock.instant().isBefore(cached.expiresAt().minus(REFRESH_MARGIN))) replace();
        return cached.tokens().access();
    }
    public synchronized String afterUnauthorized(String rejectedAccess) {
        if(cached != null && cached.tokens().access().equals(rejectedAccess)) replace();
        return access();
    }
    private void replace() {
        // Never discard a rotated refresh token between two concurrent callers.
        try {
            var next=endpoint.apply(cached == null ? null : cached.tokens().refresh());
            var receivedAt=clock.instant();
            if(next == null)throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
            var expiresAt=expiry(next.access());
            if(!expiresAt.isAfter(receivedAt))throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
            cached=new CachedTokens(next,receivedAt,expiresAt);
        }
        catch(RuntimeException failure) {
            // A timed-out refresh may already have consumed its old token. Next caller must authenticate anew.
            cached=null;throw failure;
        }
    }
    private static Instant expiry(String access) {
        // CA returns JWTs over authenticated HTTPS. exp is a scheduling hint, not a new authorization check.
        // Never invent a 60-minute lifetime from local receipt time if provider metadata is invalid.
        try {
            var parts=access.split("\\.",-1);
            if(parts.length!=3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty())
                throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
            var payload=TOKEN_JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            var exp=payload == null ? null : payload.get("exp");
            if(exp == null || !exp.isIntegralNumber() || !exp.canConvertToLong())
                throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
            return Instant.ofEpochSecond(exp.longValue());
        } catch(IOException | IllegalArgumentException | DateTimeException failure) {
            // Do not attach parsing exceptions: they can contain token fragments.
            throw new CaFailure("CA_AUTH_SCHEMA_INVALID");
        }
    }
}
