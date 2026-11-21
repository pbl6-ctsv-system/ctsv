package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** HB-T024-007–012/017/024: contract rules, no DB or provider assumptions. */
class ScopeRulesTest {
    final ObjectMapper json=new ObjectMapper();
    final String revision="A".repeat(43);
    @Test void canonicalIntentIgnoresGroupOrderButKeepsModeSourceAndRevision() throws Exception {
        var first=ScopeRules.create(json.readTree(body("groups","[{\"cohortId\":\"10\",\"majorId\":\"2\"},{\"cohortId\":\"2\",\"majorId\":\"3\"}]")));
        var second=ScopeRules.create(json.readTree(body("groups","[{\"cohortId\":\"2\",\"majorId\":\"3\"},{\"cohortId\":\"10\",\"majorId\":\"2\"}]")));
        assertThat(ScopeRules.intentHash(first)).isEqualTo(ScopeRules.intentHash(second));
        assertThat(first.scope().groups().getFirst().cohortId()).isEqualTo(2);
        assertThat(ScopeRules.intentHash(first)).hasSize(32);
    }
    @Test void rejectsManualNumberNameAndUnknownScopeFields() throws Exception {
        invalid("{\"sourceSemesterId\":\"21\",\"number\":1,\"name\":\"abc\"}");
        invalid(body("groups","[]"));
        invalid(body("groups","[{\"cohortId\":\"2\",\"majorId\":\"3\"},{\"cohortId\":\"2\",\"majorId\":\"3\"}]"));
        invalid(body("groups","[{\"cohortId\":2,\"majorId\":\"3\"}]"));
        invalid(body("groups","[{\"cohortId\":\"02\",\"majorId\":\"3\"}]"));
        invalid(body("all","[]"));
    }
    @Test void allHasExactlyModeAndUpdateHasNoSource() throws Exception {
        var command=ScopeRules.create(json.readTree("{\"sourceSemesterId\":\"21\",\"catalogRevision\":\""+revision+"\",\"scope\":{\"mode\":\"all\"}}"));
        assertThat(command.scope().groups()).isEmpty();
        assertThatThrownBy(()->ScopeRules.update(json.readTree("{\"number\":1,\"name\":\"x\"}"))).isInstanceOf(RoundFailure.class);
    }
    @Test void keyMustBeCanonicalLowercaseUuidV4() {
        assertThat(ScopeRules.requestId("a1234567-1234-4234-8234-123456789012")).hasToString("a1234567-1234-4234-8234-123456789012");
        for(String key:new String[]{"A1234567-1234-4234-8234-123456789012","a1234567-1234-1234-8234-123456789012","x",""})
            assertThatThrownBy(()->ScopeRules.requestId(key)).isInstanceOf(RoundFailure.class);
        assertThatThrownBy(()->ScopeRules.requestId(null)).isInstanceOf(RoundFailure.class);
    }
    @Test void generatedNameUsesCanonicalAcademicYear() {
        assertThat(ScopeRules.name("2024-2025",2,1)).isEqualTo("2024/2025 - Học kỳ 2 - Đợt 1");
    }
    String body(String mode,String groups) {return "{\"sourceSemesterId\":\"21\",\"catalogRevision\":\""+revision+"\",\"scope\":{\"mode\":\""+mode+"\",\"groups\":"+groups+"}}";}
    void invalid(String body) {assertThatThrownBy(()->ScopeRules.create(json.readTree(body))).isInstanceOf(RoundFailure.class);}
}
