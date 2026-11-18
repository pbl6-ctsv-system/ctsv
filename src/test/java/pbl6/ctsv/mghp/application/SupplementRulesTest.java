package pbl6.ctsv.mghp.application;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplementRulesTest {
    private final Instant opening=Instant.parse("2026-11-17T00:00:00Z");
    private final Instant deadline=opening.plusSeconds(60);

    @Test void usesOwnInclusiveDeadlineEvenAfterRoundClosed() {
        assertTrue(SupplementRules.acceptsResponse("open","closed",opening,deadline,deadline));
        assertFalse(SupplementRules.acceptsResponse("open","closed",opening,deadline,deadline.plusNanos(1)));
        assertFalse(SupplementRules.acceptsResponse("open","open",opening,deadline,opening.minusNanos(1)));
    }

    @Test void rejectsCompletedReceivingRoundAndSecondResponse() {
        assertFalse(SupplementRules.acceptsResponse("open","completed",opening,deadline,opening));
        assertFalse(SupplementRules.acceptsResponse("responded","open",opening,deadline,opening));
        assertFalse(SupplementRules.acceptsResponse("expired","open",opening,deadline,opening));
    }

    @Test void onlyFinalItemDecisionsAllowCompletion() {
        assertTrue(SupplementRules.ready("responded",List.of("satisfied","unsatisfied","not_applicable")));
        assertFalse(SupplementRules.ready("responded",List.of("needs_verification")));
        assertFalse(SupplementRules.ready("responded",java.util.Collections.singletonList(null)));
        assertFalse(SupplementRules.ready("responded",List.of()));
        assertFalse(SupplementRules.ready("open",List.of("satisfied")));
    }
}
