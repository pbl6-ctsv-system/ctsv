package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

class CaTokenManagerTest {
    @Test void reusesTokenAfterFiveMinutesUntilActualExpiryIsNear() {
        var clock = new CaTokenTestSupport.MutableClock();
        var calls = new AtomicInteger();
        var manager = new CaTokenManager(refresh -> new CaTokenManager.Tokens(
                CaTokenTestSupport.access(clock.instant().plusSeconds(3600), calls.incrementAndGet()), "refresh-" + calls.get()), clock);
        var first = manager.access();
        clock.advance(Duration.ofMinutes(6));
        assertEquals(first, manager.access(), "A six-minute-old token still has 54 minutes remaining");
        clock.advance(Duration.ofMinutes(48).plusSeconds(59));
        assertEquals(first, manager.access(), "Keep token when 5 minutes and 1 second remain");
        assertEquals(1, calls.get());
    }

    @Test void refreshesAtExactlyFiveMinutesAndResetsExpiryForEachNewToken() {
        var clock = new CaTokenTestSupport.MutableClock();
        var calls = new AtomicInteger();
        var refreshes = new java.util.ArrayList<String>();
        var manager = new CaTokenManager(refresh -> {
            refreshes.add(refresh);
            return new CaTokenManager.Tokens(CaTokenTestSupport.access(clock.instant().plusSeconds(3600), calls.incrementAndGet()), "r-" + calls.get());
        }, clock);
        var first = manager.access();
        clock.advance(Duration.ofMinutes(55));
        var second = manager.access();
        assertNotEquals(first, second);
        assertEquals(2, calls.get());
        assertEquals("r-1", refreshes.getLast());
        clock.advance(Duration.ofMinutes(54).plusSeconds(59));
        assertEquals(second, manager.access());
        assertEquals(2, calls.get());
        clock.advance(Duration.ofSeconds(1));
        assertNotEquals(second, manager.access());
        assertEquals("r-2", refreshes.getLast());
    }

    @Test void refreshesOnceForConcurrentCallsAtExpiryThreshold() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        var calls = new AtomicInteger();
        var manager = new CaTokenManager(refresh -> new CaTokenManager.Tokens(
                CaTokenTestSupport.access(clock.instant().plusSeconds(3600), calls.incrementAndGet()), "r-" + calls.get()), clock);
        manager.access();
        clock.advance(Duration.ofMinutes(55));
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < 24; i++) results.add(pool.submit(manager::access));
            var refreshed = results.getFirst().get();
            for (var result : results) assertEquals(refreshed, result.get());
            assertEquals(2, calls.get());
        }
    }

    @Test void rejectsMissingMalformedOrExpiredExpiryWithoutLeakingToken() {
        var clock = new CaTokenTestSupport.MutableClock();
        for (String payload : new String[]{"{}", "{\"exp\":\"later\"}", "{\"exp\":1.5}",
                "{\"exp\":9223372036854775808}", "{\"exp\":1}", "{\"exp\":" + clock.instant().getEpochSecond() + "}"}) {
            var access = CaTokenTestSupport.jwt(payload);
            var manager = new CaTokenManager(refresh -> new CaTokenManager.Tokens(access, "private-refresh"), clock);
            var failure = assertThrows(CaFailure.class, manager::access);
            assertEquals("CA_AUTH_SCHEMA_INVALID", failure.code());
            assertFalse(failure.toString().contains(access));
            assertFalse(failure.toString().contains("private-refresh"));
        }
    }

    @Test void singleFlightAndRotationAreAtomic() throws Exception {
        var clock = new CaTokenTestSupport.MutableClock();
        var calls=new AtomicInteger();var refreshUsed=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var manager=new CaTokenManager(refresh->{calls.incrementAndGet();if(refresh!=null)refreshUsed.add(refresh);return new CaTokenManager.Tokens(CaTokenTestSupport.access(clock.instant().plusSeconds(3600), calls.get()),"refresh-"+calls.get());},clock);
        var first = CaTokenTestSupport.access(clock.instant().plusSeconds(3600), 1);
        var second = CaTokenTestSupport.access(clock.instant().plusSeconds(3600), 2);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var batch=new java.util.ArrayList<Future<String>>();for(int i=0;i<24;i++)batch.add(pool.submit(manager::access));
            for(var future:batch)assertEquals(first,future.get());assertEquals(1,calls.get());
            batch.clear();for(int i=0;i<24;i++)batch.add(pool.submit(()->manager.afterUnauthorized(first)));
            for(var future:batch)assertEquals(second,future.get());assertEquals(2,calls.get());
            assertEquals(CaTokenTestSupport.access(clock.instant().plusSeconds(3600), 3),manager.afterUnauthorized(second));assertEquals(java.util.List.of("refresh-1","refresh-2"),refreshUsed);
        }
    }
    @Test void tokenObjectsAreRedacted() {
        assertFalse(new CaTokenManager.Tokens("secret-access","secret-refresh").toString().contains("secret"));
    }
    @Test void unknownRefreshDoesNotReusePotentiallyConsumedRefreshToken() {
        var clock = new CaTokenTestSupport.MutableClock();
        var calls=new AtomicInteger();var refreshes=new java.util.ArrayList<String>();
        var manager=new CaTokenManager(refresh->{refreshes.add(refresh);if(calls.incrementAndGet()==2)throw new CaFailure("CA_TIMEOUT");return new CaTokenManager.Tokens(CaTokenTestSupport.access(clock.instant().plusSeconds(3600), calls.get()),"r-"+calls.get());},clock);
        var first = manager.access();assertThrows(CaFailure.class,()->manager.afterUnauthorized(first));
        assertEquals(CaTokenTestSupport.access(clock.instant().plusSeconds(3600), 3),manager.access());assertNull(refreshes.getLast());
    }
}
