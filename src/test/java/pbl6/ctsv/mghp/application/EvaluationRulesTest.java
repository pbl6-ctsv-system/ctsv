package pbl6.ctsv.mghp.application;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvaluationRulesTest {
    private LocalDate day(String value){return LocalDate.parse(value);}
    @Test void inclusivePeriodsKeepGapsAndRejectOverlappingDays(){
        var a=new EvaluationRules.Interval(day("2026-09-01"),day("2026-10-31"));
        var b=new EvaluationRules.Interval(day("2026-11-02"),day("2026-12-31"));
        EvaluationRules.periods(List.of(a,b),day("2026-09-01"),day("2026-12-31"));
        assertThatThrownBy(()->EvaluationRules.periods(List.of(a,new EvaluationRules.Interval(a.to(),b.to())),a.from(),b.to()))
            .isInstanceOf(IntakeProblem.class);
        assertThatThrownBy(()->EvaluationRules.periods(List.of(a),null,b.to())).isInstanceOf(IntakeProblem.class);
    }
    @Test void finalReadinessDoesNotInferEligibilityFromMissingInformation(){
        assertThat(EvaluationRules.criterionReady("can_xac_minh","Chờ đối chiếu",true,false)).isTrue();
        assertThat(EvaluationRules.criterionReady("can_xac_minh",null,true,false)).isFalse();
        assertThat(EvaluationRules.criterionReady("chua_xet",null,true,false)).isFalse();
        assertThat(EvaluationRules.criterionReady("khong_ap_dung","Không áp dụng",true,false)).isFalse();
        assertThat(EvaluationRules.criterionReady("khong_ap_dung","Không áp dụng",true,true)).isTrue();
    }
    @Test void leapBirthdayHasNoImplicitLegalCutoff(){
        assertThat(EvaluationRules.ageReady("M3",day("2004-02-29"),null)).isFalse();
        assertThat(EvaluationRules.ageReady("M3",day("2004-02-29"),day("2026-02-27"))).isTrue();
        assertThat(EvaluationRules.ageReady("M2",null,null)).isTrue();
    }
}
