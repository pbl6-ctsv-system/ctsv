package pbl6.ctsv.mghp.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

final class SupplementRules {
    static final Set<String> ACTIVE=Set.of("open","responded","expired");
    private static final Set<String> FINAL=Set.of("satisfied","unsatisfied","not_applicable");

    private SupplementRules() {}

    static boolean acceptsResponse(String state,String round,Instant opens,Instant deadline,Instant received) {
        return "open".equals(state) && ("open".equals(round)||"closed".equals(round))
            && opens!=null && deadline!=null && !received.isBefore(opens) && !received.isAfter(deadline);
    }

    static boolean ready(String state,List<String> decisions) {
        return "responded".equals(state) && !decisions.isEmpty()
            && decisions.stream().allMatch(result -> result!=null && FINAL.contains(result));
    }
}
