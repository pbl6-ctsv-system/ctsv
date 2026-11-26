package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuleRulesTest {
    @Test void HB_RULE_01_preservesTypedDecimalStringsAndFiveConditions() {
        var value = RuleFixtures.command();
        assertThat(value.legalBasis()).isEqualTo("SYNTHETIC — Quy định KKHT");
        assertThat(value.effectiveFrom()).isEqualTo(LocalDate.of(2026,1,1));
        assertThat(value.effectiveTo()).isNull();
        assertThat(value.conditions().academicAverageMin()).isEqualTo("7.00");
        assertThat(value.conditions().withinStandardDuration()).isTrue();
        assertThat(value.levels().A().tuitionMultiplier()).isEqualTo("1.5000");
        assertThat(value.priority().levelOrder()).containsExactly("A","B","C");
    }

    @Test void HB_RULE_03_encodesApprovedFallbackRoundingAndStrictLastSlotWithoutRunningEngine() {
        var value=RuleFixtures.command();
        assertThat(value.priority().fallback()).isEqualTo("HIGHEST_ELIGIBLE");
        assertThat(value.tuitionBasis().rounding()).isEqualTo(new RuleDtos.Rounding("FINAL_AMOUNT",0,"HALF_UP"));
        assertThat(value.lastSlot()).isEqualTo(new RuleDtos.LastSlot("0.5000000000","GT","REMAINING_FUND"));
        assertThat(RuleFixtures.JSON.valueToTree(value.levels()).at("/B/academicAverageMax").isMissingNode()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"legalBasis","effectiveFrom","effectiveTo","conditions","levels","priority","tuitionBasis","tiePolicy","lastSlot"})
    void HB_RULE_02_rejectsMissingTopLevelField(String field) {
        var body=RuleFixtures.body();body.remove(field);invalid(body);
    }

    @ParameterizedTest @ValueSource(strings={""," ","0000-01-01","2025-02-29","2026-02-30","2026-1-01","+2026-01-01","10000-01-01"})
    void HB_RULE_02_rejectsInvalidSqlDates(String value) { var body=RuleFixtures.body();body.put("effectiveFrom",value);invalid(body); }

    @ParameterizedTest @ValueSource(strings={"0001-01-01","1582-10-10","2024-02-29","9999-12-31"})
    void HB_RULE_01_acceptsFullSqlDateDomain(String value) { var body=RuleFixtures.body();body.put("effectiveFrom",value);assertThat(RuleRules.command(body).effectiveFrom()).isEqualTo(LocalDate.parse(value)); }

    @ParameterizedTest @ValueSource(strings={"7","07.00","7.0","7.000","7,00","7.000,00","7e0","NaN","Infinity","-0.00","10.01","-1.00"})
    void HB_RULE_02_rejectsNoncanonicalOrOutOfRangeDecimal(String value) {
        var body=RuleFixtures.body();((ObjectNode)body.get("conditions")).put("academicAverageMin",value);invalid(body);
    }

    @ParameterizedTest @CsvSource({"conditions,academicAverageMin","conditions,conductScoreMin","conditions,firstAttemptGradeMin","conditions,withinStandardDuration","conditions,progressRatioMin","levels,A","priority,fallback","priority,levelOrder","priority,rankingOrder","tuitionBasis,formula","tuitionBasis,rounding","tiePolicy,mode","lastSlot,comparison","lastSlot,amountMode","lastSlot,minimumRemainderRatio"})
    void HB_RULE_02_rejectsMissingOrNullNestedField(String parent,String field) {
        var body=RuleFixtures.body();var part=(ObjectNode)body.get(parent);part.remove(field);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.get(parent)).putNull(field);invalid(body);
    }

    @ParameterizedTest @ValueSource(strings={"conditions","levels","priority","tuitionBasis","tiePolicy","lastSlot"})
    void HB_RULE_02_rejectsUnknownNestedField(String parent) { var body=RuleFixtures.body();((ObjectNode)body.get(parent)).put("forged",true);invalid(body); }

    @Test void HB_RULE_02_rejectsWrongScalarTypesAndUnknownRootMetadata() {
        var body=RuleFixtures.body();((ObjectNode)body.get("conditions")).put("academicAverageMin",7.0);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.get("conditions")).put("conductScoreMin","65");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.get("conditions")).put("conductScoreMin",65.5);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.get("conditions")).put("withinStandardDuration",false);invalid(body);
        for(String key:new String[]{"id","version","status","usedAt","actor"}) { body=RuleFixtures.body();body.put(key,"forged");invalid(body); }
    }

    @Test void HB_RULE_02_rejectsInconsistentThresholdsAndCoefficients() {
        var body=RuleFixtures.body();((ObjectNode)body.at("/levels/A")).put("academicAverageMin","8.00");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/C")).put("academicAverageMin","6.99");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/A")).put("conductScoreMin",70);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/C")).put("tuitionMultiplier","0.0000");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/C")).put("tuitionMultiplier","2.0000");invalid(body);
        body=RuleFixtures.body();body.put("effectiveTo","2025-12-31");invalid(body);
        body=RuleFixtures.body();body.put("legalBasis"," ");invalid(body);
        body=RuleFixtures.body();body.put("legalBasis","x".repeat(4001));invalid(body);
    }

    @Test void HB_RULE_03_rejectsChangedPriorityFormulaRoundingTieAndLastSlotPolicies() {
        for(String[] change:new String[][]{{"priority","fallback","NONE"},{"tuitionBasis","formula","EVAL"},{"tiePolicy","mode","SOCIAL_SCORE"},{"lastSlot","comparison","GTE"},{"lastSlot","amountMode","FULL_AWARD"},{"lastSlot","minimumRemainderRatio","0.4900000000"}}) {
            var body=RuleFixtures.body();((ObjectNode)body.get(change[0])).put(change[1],change[2]);invalid(body);
        }
        var body=RuleFixtures.body();((ObjectNode)body.at("/tuitionBasis/rounding")).put("mode","DOWN");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/tuitionBasis/rounding")).put("scale",2);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.get("priority")).putArray("levelOrder").add("C").add("B").add("A");invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/B")).put("academicAverageMax","8.99");invalid(body);
    }
    @Test void HB_RULE_02_conductThresholdsAlsoFollowStrictDescendingContract() {
        var body=RuleFixtures.body();((ObjectNode)body.at("/levels/A")).put("conductScoreMin",80);invalid(body);
        body=RuleFixtures.body();((ObjectNode)body.at("/levels/B")).put("conductScoreMin",65);invalid(body);
    }
    private void invalid(ObjectNode body) { assertThatThrownBy(()->RuleRules.command(body)).hasMessage("VALIDATION_ERROR"); }
}
