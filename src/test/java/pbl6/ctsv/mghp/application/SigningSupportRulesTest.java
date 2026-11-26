package pbl6.ctsv.mghp.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SigningSupportRulesTest {
 @Test void incidentHasOnlyStartAndFinishTransitions(){
  assertThat(SigningSupportRules.transition("waiting","start")).isEqualTo("working");
  assertThat(SigningSupportRules.transition("working","finish")).isEqualTo("resolved");
  assertThat(SigningSupportRules.transition("working","start")).isEqualTo("working");
  assertThat(SigningSupportRules.transition("resolved","finish")).isEqualTo("resolved");
  for(String[] pair:new String[][]{{"waiting","finish"},{"resolved","start"},{"working","recover"},{"working","reopen"}})
   assertThatThrownBy(()->SigningSupportRules.transition(pair[0],pair[1])).hasMessage("SUPPORT_TRANSITION_UNAVAILABLE");
 }
}
