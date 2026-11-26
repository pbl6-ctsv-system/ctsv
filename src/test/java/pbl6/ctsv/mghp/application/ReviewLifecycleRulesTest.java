package pbl6.ctsv.mghp.application;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReviewLifecycleRulesTest {
 @Test void onlyOnlineApprovedCanEvaluateAndFailureIsTerminal(){
  for(var status:List.of("submitted","processing","supplementing","supplement_responded","withdrawn","rejected","reopened","completed"))
   assertThat(ReviewLifecycleRules.canEvaluate(status,false)).as(status).isFalse();
  assertThat(ReviewLifecycleRules.canEvaluate("approved",false)).isTrue();
  assertThat(ReviewLifecycleRules.canEvaluate("approved",true)).isFalse();
 }
 // Status projection tests moved to ApplicationStageStatusTest under owner25/11 semantics.
}
