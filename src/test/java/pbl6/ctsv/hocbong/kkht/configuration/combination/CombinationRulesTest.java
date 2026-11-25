package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CombinationRulesTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ObjectNode valid() {
        return JSON.createObjectNode().put("programId", "1").put("cohortId", "2").put("majorId", "3")
                .put("curriculumCode", "SYN-KHUNG").put("standardSemesters", 8).put("programType", "CN")
                .put("effectiveFrom", "2024-09-01").putNull("effectiveTo");
    }

    @Test void HB_COMB_02_acceptsNullEndAndKeepsConfiguredValues() {
        var result = CombinationRules.command(valid());
        assertThat(result.programId()).isEqualTo(1);
        assertThat(result.effectiveFrom()).isEqualTo(LocalDate.of(2024,9,1));
        assertThat(result.effectiveTo()).isNull();
        assertThat(result.standardSemesters()).isEqualTo(8);
        assertThat(result.programType()).isEqualTo("CN");
    }

    @Test void HB_COMB_03_acceptsInclusiveSingleDayAndEngineer() {
        var result = CombinationRules.command(valid().put("effectiveTo", "2024-09-01").put("programType", "KS"));
        assertThat(result.effectiveTo()).isEqualTo(result.effectiveFrom());
        assertThat(result.programType()).isEqualTo("KS");
    }

    @Test void HB_COMB_01_preservesTheExactSourceCurriculumIdentity() {
        String sourceCode=" SYN-KHUNG ";
        assertThat(CombinationRules.command(valid().put("curriculumCode",sourceCode)).curriculumCode())
                .isEqualTo(sourceCode);
    }

    @ParameterizedTest @ValueSource(strings={"2024-02-30","2024-9-01","0000-01-01","+10000-01-01",""})
    void HB_COMB_03_rejectsInvalidSqlDate(String date) {
        assertThatThrownBy(() -> CombinationRules.command(valid().put("effectiveFrom", date)))
                .hasMessage("VALIDATION_ERROR");
    }

    @Test void HB_COMB_03_rejectsReverseRange() {
        assertThatThrownBy(() -> CombinationRules.command(valid().put("effectiveTo", "2024-08-31")))
                .hasMessage("VALIDATION_ERROR");
    }

    @ParameterizedTest @ValueSource(strings={"programId","cohortId","majorId"})
    void HB_COMB_06_requiresPositiveStringIds(String field) {
        assertThatThrownBy(() -> CombinationRules.command(valid().put(field, 1))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put(field, "0"))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put(field, "9223372036854775808")))
                .hasMessage("VALIDATION_ERROR");
    }

    @ParameterizedTest @ValueSource(strings={"DT","CLC","cn","CN ",""})
    void HB_COMB_02_rejectsUnapprovedProgramType(String type) {
        assertThatThrownBy(() -> CombinationRules.command(valid().put("programType", type))).hasMessage("VALIDATION_ERROR");
    }

    @Test void HB_COMB_06_rejectsUnknownFieldsMissingFieldsAndWrongTypes() {
        assertThatThrownBy(() -> CombinationRules.command(valid().put("programName", "client-forged")))
                .hasMessage("VALIDATION_ERROR");
        var missing = valid(); missing.remove("curriculumCode");
        assertThatThrownBy(() -> CombinationRules.command(missing)).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("standardSemesters", "8"))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("standardSemesters", 8.5))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("standardSemesters", 0))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("standardSemesters", 32768))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("curriculumCode", " "))).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> CombinationRules.command(valid().put("curriculumCode", "x".repeat(513))))
                .hasMessage("VALIDATION_ERROR");
    }
}
