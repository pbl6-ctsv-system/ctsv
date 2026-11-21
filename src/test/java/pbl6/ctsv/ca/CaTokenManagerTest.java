package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

class CaTokenManagerTest {
    @Test void singleFlightAndRotationAreAtomic() throws Exception {
        var calls=new AtomicInteger();var refreshUsed=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var manager=new CaTokenManager(refresh->{calls.incrementAndGet();if(refresh!=null)refreshUsed.add(refresh);return new CaTokenManager.Tokens("access-"+calls.get(),"refresh-"+calls.get());},Clock.systemUTC());
        try(var pool=Executors.newFixedThreadPool(8)) {
            var batch=new java.util.ArrayList<Future<String>>();for(int i=0;i<24;i++)batch.add(pool.submit(manager::access));
            for(var future:batch)assertEquals("access-1",future.get());assertEquals(1,calls.get());
            batch.clear();for(int i=0;i<24;i++)batch.add(pool.submit(()->manager.afterUnauthorized("access-1")));
            for(var future:batch)assertEquals("access-2",future.get());assertEquals(2,calls.get());
            assertEquals("access-3",manager.afterUnauthorized("access-2"));assertEquals(java.util.List.of("refresh-1","refresh-2"),refreshUsed);
        }
    }
    @Test void tokenObjectsAreRedacted() {
        assertFalse(new CaTokenManager.Tokens("secret-access","secret-refresh").toString().contains("secret"));
    }
    @Test void unknownRefreshDoesNotReusePotentiallyConsumedRefreshToken() {
        var calls=new AtomicInteger();var refreshes=new java.util.ArrayList<String>();
        var manager=new CaTokenManager(refresh->{refreshes.add(refresh);if(calls.incrementAndGet()==2)throw new CaFailure("CA_TIMEOUT");return new CaTokenManager.Tokens("a-"+calls.get(),"r-"+calls.get());},Clock.systemUTC());
        assertEquals("a-1",manager.access());assertThrows(CaFailure.class,()->manager.afterUnauthorized("a-1"));
        assertEquals("a-3",manager.access());assertNull(refreshes.getLast());
    }
}
