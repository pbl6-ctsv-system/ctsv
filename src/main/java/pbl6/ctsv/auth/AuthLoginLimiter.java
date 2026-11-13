package pbl6.ctsv.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** A bounded process-local limiter for the single CTSV backend. Use socket peer address. */
final class AuthLoginLimiter {
    private record Pair(String username,String ip) {}
    private record Window(long until,int attempts) {}
    private final Map<String,Window> ips = new HashMap<>();
    private final Map<Pair,Window> pairs = new HashMap<>();
    private long nextCleanup;
    synchronized void check(String username, String ip, Instant now) {
        long seconds = now.getEpochSecond();
        if (seconds >= nextCleanup) {
            ips.values().removeIf(w -> w.until <= seconds);
            pairs.values().removeIf(w -> w.until <= seconds);
            nextCleanup = seconds + 60;
        }
        Pair key = new Pair(username, ip);
        Window byIp = current(ips.get(ip), seconds);
        Window byPair = current(pairs.get(key), seconds);
        long blockedUntil = Math.max(byIp.attempts >= 30 ? byIp.until : 0,
                                    byPair.attempts >= 5 ? byPair.until : 0);
        if (blockedUntil > seconds) throw new AuthFailure(429,"AUTH_RATE_LIMITED",blockedUntil-seconds);
        if ((!ips.containsKey(ip) && ips.size() >= 10000)
            || (!pairs.containsKey(key) && pairs.size() >= 50000)) throw AuthFailure.unavailable();
        ips.put(ip, new Window(byIp.until, byIp.attempts+1));
        pairs.put(key, new Window(byPair.until, byPair.attempts+1));
    }
    private Window current(Window window,long seconds) {
        return window == null || window.until <= seconds ? new Window(seconds+900,0) : window;
    }
}
