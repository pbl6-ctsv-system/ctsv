package pbl6.ctsv.mghp.application;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DecisionRulesTest {
 DecisionRules.Source c(long e,long p,String rate,Integer priority,String from,String to){return new DecisionRules.Source(1,2,3,"hoc_phi",e,p,e,new BigDecimal(rate),priority,LocalDate.parse(from),LocalDate.parse(to));}
 @Test void choosesOneHighestMWithoutFillingItsMissingMonths(){
  var high=c(8,80,"0.50",0,"2026-09-01","2026-10-31");var low=c(9,90,"0.25",0,"2026-09-01","2027-01-31");
  assertThat(DecisionRules.winners(List.of(low,high))).containsExactly(high);
 }
 @Test void lowerTiePriorityWinsAndStablePolicyThenEvaluationBreaksEqualPriority(){
  var a=c(8,80,"0.50",2,"2026-09-01","2026-10-31"); var b=c(9,90,"0.50",1,"2026-09-01","2027-01-31");
  assertThat(DecisionRules.winners(List.of(a,b))).containsExactly(b);
  assertThat(DecisionRules.winners(List.of(b,c(7,70,"0.50",1,"2026-09-01","2026-09-30")))).extracting(DecisionRules.Source::evaluation).containsExactly(7L);
 }
 @Test void preservesEveryVerifiedPeriodOfTheWinningMWithoutJoiningGaps(){
  var a=c(8,80,"0.50",0,"2026-09-01","2026-09-30"); var b=c(8,80,"0.50",0,"2026-11-01","2026-11-30");
  assertThat(DecisionRules.winners(List.of(b,c(9,90,"0.25",0,"2026-09-01","2027-01-31"),a))).containsExactly(a,b);
 }
 @Test void nullPriorityCannotOverrideAnExplicitPriority(){
  var a=c(8,80,"0.50",null,"2026-09-01","2026-09-30"); var b=c(9,90,"0.50",50,"2026-09-01","2026-09-30");assertThat(DecisionRules.winners(List.of(a,b))).containsExactly(b);
 }
 @Test void rejectsInvalidIntervalsAndRates(){
  assertThatThrownBy(()->DecisionRules.winners(List.of(c(8,80,"0.5",0,"2026-11-01","2026-09-30")))).isInstanceOf(IntakeProblem.class);
  assertThatThrownBy(()->DecisionRules.winners(List.of(c(8,80,"1.5",0,"2026-09-01","2026-09-30")))).isInstanceOf(IntakeProblem.class);
 }
 @Test void respectsTheApprovedInclusivePolicyRateRange(){
  var zero=c(8,80,"0",0,"2026-09-01","2026-09-30");
  assertThat(DecisionRules.winners(List.of(zero))).containsExactly(zero);
  var full=c(9,90,"1",0,"2026-09-01","2026-09-30");
  assertThat(DecisionRules.winners(List.of(zero,full))).containsExactly(full);
 }
}
