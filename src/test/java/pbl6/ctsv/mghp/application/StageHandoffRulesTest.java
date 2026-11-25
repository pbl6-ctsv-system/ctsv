package pbl6.ctsv.mghp.application;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StageHandoffRulesTest {
    private java.util.Map<String,Object> source() {
        var facts=new HashMap<String,Object>();
        facts.put("intake","approved");facts.put("roundStatus","closed");
        return facts;
    }
    @Test void STAGE05_approvedOnlineAloneDoesNotAllowHardCopyEvaluation() {
        assertThat(new ApplicationWorkflow(null).fields(source()).get("evaluationAllowed")).isEqualTo(false);
    }
    @Test void STAGE06_sealedOnlineAllowsHardCopyEvaluation() {
        var facts=source();facts.put("onlineClosedAt","2026-11-25T04:00:00Z");
        assertThat(new ApplicationWorkflow(null).fields(facts).get("evaluationAllowed")).isEqualTo(true);
    }
    @Test void STAGE07_closedReviewDoesNotAllowFurtherEvaluation() {
        var facts=source();facts.put("onlineClosedAt","2026-11-25T04:00:00Z");facts.put("closedAt","2026-11-25T05:00:00Z");
        assertThat(new ApplicationWorkflow(null).fields(facts).get("evaluationAllowed")).isEqualTo(false);
    }
    @Test void STAGE10_legacyClosureIsReadOnlyWithoutInventingAnOnlineDate() {
        var facts=source();facts.put("closedAt","2026-11-23T05:00:00Z");
        var read=new ApplicationWorkflow(null).fields(facts);
        assertThat(read.get("evaluationAllowed")).isEqualTo(false);
        assertThat(facts).doesNotContainKey("onlineClosedAt");
    }
}
