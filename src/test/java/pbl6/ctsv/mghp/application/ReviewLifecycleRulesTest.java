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
 @Test void progressDistinguishesApprovalFromSelectionAndPublication(){
  assertThat(ReviewLifecycleRules.status("approved",null,false,null)).isEqualTo("approved_v1");
  assertThat(ReviewLifecycleRules.status("approved","can_xac_minh",false,null)).isEqualTo("approved_v1");
  assertThat(ReviewLifecycleRules.status("approved","dat",false,null)).isEqualTo("approved_v2");
  assertThat(ReviewLifecycleRules.status("approved","dat",true,null)).isEqualTo("awaiting_council");
  assertThat(ReviewLifecycleRules.status("approved","khong_dat",false,null)).isEqualTo("hard_copy_failed");
  assertThat(ReviewLifecycleRules.status("approved","dat",true,"fully_selected")).isEqualTo("selected");
  assertThat(ReviewLifecycleRules.status("approved","dat",true,"not_selected")).isEqualTo("not_selected");
  assertThat(ReviewLifecycleRules.status("completed",null,false,null)).isEqualTo("completed");
  assertThat(ReviewLifecycleRules.status("rejected","dat",true,"fully_selected")).isEqualTo("rejected");
 }
}
