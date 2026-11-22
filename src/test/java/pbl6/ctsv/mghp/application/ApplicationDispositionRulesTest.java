package pbl6.ctsv.mghp.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApplicationDispositionRulesTest {
 @Test void onlyUnfinishedApplicationsCanReceiveAConclusion(){
  for(var state:new String[]{"submitted","processing","supplementing","supplement_responded"})
   assertThat(ApplicationDispositionRules.accepts(state)).isTrue();
  for(var state:new String[]{"approved","rejected","completed","withdrawn","reopened"})
   assertThat(ApplicationDispositionRules.accepts(state)).isFalse();
  assertThat(ApplicationDispositionRules.accepts(null)).isFalse();
 }
 @Test void aPdfRequiresContentAcceptanceAndNoUnresolvedCurrentChecks(){
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of("content","pass"))).isTrue();
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of())).isFalse();
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of("content","fail"))).isFalse();
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of("content","pass","validity","needs_verification"))).isFalse();
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of("content","pass","subject","fail"))).isFalse();
  assertThat(ApplicationDispositionRules.pdfReady(java.util.Map.of("content","pass","hard_copy","not_received"))).isTrue();
 }
 @Test void supplementGeneralTargetsMustNotPretendToModifyAPhoneOrDocument(){
  assertThat(ApplicationDispositionRules.generalTarget(null,null,null,null,null)).isTrue();
  assertThat(ApplicationDispositionRules.generalTarget(1L,null,null,null,null)).isFalse();
  assertThat(ApplicationDispositionRules.generalTarget(null,null,null,null,"phoneNumber")).isFalse();
 }
}
