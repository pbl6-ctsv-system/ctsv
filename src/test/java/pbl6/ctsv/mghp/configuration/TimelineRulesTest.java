package pbl6.ctsv.mghp.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** TL-01/03/04: schedule input is distinct from actual events and business configuration. */
class TimelineRulesTest {
    private final ObjectMapper json = new ObjectMapper();

    ObjectNode round() throws Exception {
        return (ObjectNode) json.readTree("""
            {"code":"SYNTHETIC-TL","semesterId":"1","name":"Đợt hỗ trợ học phí",
             "description":null,"opensAt":"2026-11-01T00:00:00Z",
             "submissionDeadline":"2026-11-10T00:00:00Z","reviewDeadline":"2026-11-20T00:00:00Z",
             "allowRetroactive":true,
             "retroactiveMode":"theo_phap_luat","midSemesterMode":"khoang_ngay_chuyen_vien_xac_nhan",
             "policyVersions":[]}
            """);
    }

    ObjectNode schedule() throws Exception {
        return (ObjectNode) json.readTree("""
            {"supplementPlannedStartAt":null,"supplementPlannedEndAt":null,
             "reviewPlannedStartAt":null,"councilPlannedDate":null,
             "signingPlannedStartAt":null,"signingPlannedEndAt":null,
             "publicationPlannedStartAt":null,"publicationPlannedEndAt":null}
            """);
    }

    @Test void legacyInputStillWorks() throws Exception {
        assertDoesNotThrow(() -> RoundRules.validate(round()));
    }

    @Test void nullablePlanIsAcceptedWithoutInventingActualEvents() throws Exception {
        var input=round();input.set("schedule",schedule());
        assertDoesNotThrow(() -> RoundRules.validate(input));
    }

    @Test void plannedSigningCanOverlapPublicationWithoutHardWindow() throws Exception {
        var input=round();var plan=schedule();
        plan.put("signingPlannedStartAt","2026-11-18T00:00:00Z");
        plan.put("signingPlannedEndAt","2026-11-25T00:00:00Z");
        plan.put("publicationPlannedStartAt","2026-11-20T00:00:00Z");
        plan.put("publicationPlannedEndAt","2026-11-28T00:00:00Z");
        plan.put("councilPlannedDate","2026-11-17");input.set("schedule",plan);
        assertDoesNotThrow(() -> RoundRules.validate(input));
    }

    @Test void invertedPlanAndActualInjectionAreRejected() throws Exception {
        var input=round();var plan=schedule();
        plan.put("signingPlannedStartAt","2026-11-25T00:00:00Z");
        plan.put("signingPlannedEndAt","2026-11-18T00:00:00Z");input.set("schedule",plan);
        assertThrows(ConfigFailure.class,() -> RoundRules.validate(input));
        plan=schedule();plan.put("reviewClosedAt","2026-11-01T00:00:00Z");input.set("schedule",plan);
        assertThrows(ConfigFailure.class,() -> RoundRules.validate(input));
    }

    @Test void plannedInstantCannotSilentlyLoseSqlPrecision() throws Exception {
        var input=round();var plan=schedule();plan.put("reviewPlannedStartAt","2026-11-01T00:00:00.000000001Z");
        input.set("schedule",plan);assertThrows(ConfigFailure.class,() -> RoundRules.validate(input));
    }
}
