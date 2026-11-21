package pbl6.ctsv.shared.ca;

import java.time.*;
import java.util.function.Function;

/** Server-only in-memory tokens. A single monitor makes refresh rotation single-flight. */
public final class CaTokenManager {
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
    private Tokens tokens;
    private Instant refreshAt;
    public CaTokenManager(Function<String,Tokens> endpoint, Clock clock) { this.endpoint=endpoint;this.clock=clock; }
    public synchronized String access() {
        if(tokens == null || !clock.instant().isBefore(refreshAt)) replace();
        return tokens.access();
    }
    public synchronized String afterUnauthorized(String rejectedAccess) {
        if(tokens != null && tokens.access().equals(rejectedAccess)) replace();
        return access();
    }
    private void replace() {
        // Never discard a rotated refresh token between two concurrent callers.
        Tokens next;
        try {next=endpoint.apply(tokens == null ? null : tokens.refresh());}
        catch(RuntimeException failure) {
            // A timed-out refresh may already have consumed its old token. Next caller must authenticate anew.
            tokens=null;refreshAt=null;throw failure;
        }
        tokens=next;
        // Conservative refresh; CA default access lifetime is 60 minutes. 401 is also handled once for GET.
        refreshAt=clock.instant().plusSeconds(300);
    }
}
