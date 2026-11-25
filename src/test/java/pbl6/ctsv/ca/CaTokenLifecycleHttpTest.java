package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP transport; CA provider and clock are controlled test doubles. No SQL or CA DUT calls. */
class CaTokenLifecycleHttpTest {
    private static final String EMPTY_CATEGORIES = "{\"count\":0,\"next\":null,\"results\":[]}";

    @Test void reusesJwtUntilFiveMinutesBeforeItsOwnExpiryAndRotatesRefreshToken() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        var issues = new AtomicInteger();
        var rotations = new AtomicInteger();
        try (var stub = new CaHttpStub()) {
            stub.handler = request -> {
                if (request.path().endsWith("token/")) {
                    issues.incrementAndGet();
                    return auth(clock, 1);
                }
                if (request.path().endsWith("refresh/")) {
                    int step = rotations.incrementAndGet();
                    assertEquals("{\"refresh\":\"refresh-" + step + "\"}", new String(request.body(), StandardCharsets.UTF_8));
                    return refresh(clock, step + 1);
                }
                return CaHttpStub.Reply.json(200, EMPTY_CATEGORIES);
            };
            try (var client = CaDutClient.forLoopbackStub(stub.origin(), "synthetic-client", "synthetic-secret", clock, 3000)) {
                assertTrue(client.categories().isEmpty());
                var first = stub.requests.getLast().authorization();
                clock.advance(Duration.ofMinutes(6));
                assertTrue(client.categories().isEmpty());
                assertEquals(first, stub.requests.getLast().authorization());
                clock.advance(Duration.ofMinutes(8).plusSeconds(59));
                assertTrue(client.categories().isEmpty());
                assertEquals(first, stub.requests.getLast().authorization());
                assertEquals(0, rotations.get());
                clock.advance(Duration.ofSeconds(1));
                assertTrue(client.categories().isEmpty());
                var second = stub.requests.getLast().authorization();
                assertNotEquals(first, second);
                assertEquals(1, rotations.get());
                clock.advance(Duration.ofMinutes(15));
                assertTrue(client.categories().isEmpty());
                assertNotEquals(second, stub.requests.getLast().authorization());
                assertEquals(2, rotations.get());
                assertEquals(1, issues.get());
            }
        }
    }

    @Test void expiredTokenRefreshRejectedFallsBackToNewClientAuthentication() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        var issues = new AtomicInteger();
        try (var stub = new CaHttpStub()) {
            stub.handler = request -> {
                if (request.path().endsWith("token/")) return auth(clock, issues.incrementAndGet());
                if (request.path().endsWith("refresh/")) return CaHttpStub.Reply.json(401, "{}");
                return CaHttpStub.Reply.json(200, EMPTY_CATEGORIES);
            };
            try (var client = CaDutClient.forLoopbackStub(stub.origin(), "synthetic-client", "synthetic-secret", clock, 3000)) {
                client.categories();
                var first = stub.requests.getLast().authorization();
                clock.advance(Duration.ofHours(2));
                client.categories();
                assertNotEquals(first, stub.requests.getLast().authorization());
                assertEquals(2, issues.get());
                assertEquals(List.of("/api/v1/auth/token/", "/api/v1/documents/categories/", "/api/v1/auth/token/refresh/",
                        "/api/v1/auth/token/", "/api/v1/documents/categories/"), stub.requests.stream().map(CaHttpStub.Request::path).toList());
            }
        }
    }

    @Test void refreshesBeforeSigningButNeverReplaysRejectedSign() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        try (var stub = new CaHttpStub()) {
            stub.handler = request -> {
                if (request.path().endsWith("token/")) return auth(clock, 1);
                if (request.path().endsWith("refresh/")) return refresh(clock, 2);
                if (request.path().endsWith("sign/")) return CaHttpStub.Reply.json(401, "{}");
                return CaHttpStub.Reply.json(200, EMPTY_CATEGORIES);
            };
            try (var client = CaDutClient.forLoopbackStub(stub.origin(), "synthetic-client", "synthetic-secret", clock, 3000)) {
                client.categories();
                clock.advance(Duration.ofMinutes(15));
                var failure = assertThrows(CaFailure.class, () -> client.sign(UUID.randomUUID(), UUID.randomUUID(),
                        "signer@example.test", "Synthetic signer", new CaPdfPreflight.Box(1, 10, 10, 100, 50)));
                assertEquals(401, failure.httpStatus());
                assertEquals(List.of("/api/v1/auth/token/", "/api/v1/documents/categories/", "/api/v1/auth/token/refresh/",
                        "/api/v1/signatures/sign/"), stub.requests.stream().map(CaHttpStub.Request::path).toList());
                assertEquals("Bearer " + CaTokenTestSupport.access(clock.instant().plusSeconds(1200), 2), stub.requests.getLast().authorization());
            }
        }
    }

    @Test void invalidRefreshedExpiryStopsBusinessCallAndNextCallAuthenticatesAnew() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        var issues = new AtomicInteger();
        try (var stub = new CaHttpStub()) {
            stub.handler = request -> {
                if (request.path().endsWith("token/")) return auth(clock, issues.incrementAndGet());
                if (request.path().endsWith("refresh/")) return CaHttpStub.Reply.json(200,
                        "{\"access\":\"" + CaTokenTestSupport.jwt("{}") + "\",\"refresh\":\"consumed-refresh\"}");
                return CaHttpStub.Reply.json(200, EMPTY_CATEGORIES);
            };
            try (var client = CaDutClient.forLoopbackStub(stub.origin(), "synthetic-client", "synthetic-secret", clock, 3000)) {
                client.categories();
                clock.advance(Duration.ofMinutes(15));
                var failure = assertThrows(CaFailure.class, client::categories);
                assertEquals("CA_AUTH_SCHEMA_INVALID", failure.code());
                assertEquals(1, stub.requests.stream().filter(request -> request.method().equals("GET")).count());
                client.categories();
                assertEquals(2, issues.get());
                assertEquals(1, stub.requests.stream().filter(request -> request.path().endsWith("refresh/")).count());
                assertEquals(2, stub.requests.stream().filter(request -> request.method().equals("GET")).count());
            }
        }
    }

    @Test void failedRefreshPreventsSigningDispatch() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        try (var stub = new CaHttpStub()) {
            stub.handler = request -> {
                if (request.path().endsWith("token/")) return auth(clock, 1);
                if (request.path().endsWith("refresh/")) return CaHttpStub.Reply.json(503, "{}");
                return CaHttpStub.Reply.json(200, EMPTY_CATEGORIES);
            };
            try (var client = CaDutClient.forLoopbackStub(stub.origin(), "synthetic-client", "synthetic-secret", clock, 3000)) {
                client.categories();
                clock.advance(Duration.ofMinutes(15));
                assertThrows(CaFailure.class, () -> client.sign(UUID.randomUUID(), UUID.randomUUID(),
                        "signer@example.test", "Synthetic signer", new CaPdfPreflight.Box(1, 10, 10, 100, 50)));
                assertEquals(0, stub.requests.stream().filter(request -> request.path().endsWith("sign/")).count());
            }
        }
    }

    private static CaHttpStub.Reply auth(CaTokenTestSupport.MutableClock clock, int sequence) {
        return CaHttpStub.Reply.json(200, "{\"success\":true,\"data\":" + tokenPair(clock, sequence) + "}");
    }
    private static CaHttpStub.Reply refresh(CaTokenTestSupport.MutableClock clock, int sequence) {
        return CaHttpStub.Reply.json(200, tokenPair(clock, sequence));
    }
    private static String tokenPair(CaTokenTestSupport.MutableClock clock, int sequence) {
        // A 20-minute provider lifetime proves CTSV does not guess the documented 60-minute default.
        return "{\"access\":\"" + CaTokenTestSupport.access(clock.instant().plusSeconds(1200), sequence)
                + "\",\"refresh\":\"refresh-" + sequence + "\"}";
    }
}
