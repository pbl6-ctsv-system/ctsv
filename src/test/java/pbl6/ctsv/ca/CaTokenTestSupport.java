package pbl6.ctsv.ca;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic scheduling metadata only; these tokens are never accepted by a real provider. */
final class CaTokenTestSupport {
    static String access(Instant expiresAt, int sequence) {
        return jwt("{\"exp\":" + expiresAt.getEpochSecond() + ",\"jti\":\"synthetic-" + sequence + "\"}");
    }

    static String jwt(String payload) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString("synthetic-signature".getBytes(StandardCharsets.UTF_8));
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-25T03:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
