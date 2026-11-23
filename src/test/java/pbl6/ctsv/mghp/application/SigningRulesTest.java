package pbl6.ctsv.mghp.application;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SigningRulesTest {
 @Test void bindsOnlyLockedMatchingSourceAndReadyPrivatePdf(){
  assertThatCode(()->SigningRules.bindable("locked","a".repeat(64),"a".repeat(64),true)).doesNotThrowAnyException();
  assertThatThrownBy(()->SigningRules.bindable("draft","a".repeat(64),"a".repeat(64),true)).hasMessage("DOCUMENT_SOURCE_NOT_LOCKED");
  assertThatThrownBy(()->SigningRules.bindable("locked","a".repeat(64),"b".repeat(64),true)).hasMessage("DOCUMENT_DATASET_MISMATCH");
  assertThatThrownBy(()->SigningRules.bindable("locked","a".repeat(64),"a".repeat(64),false)).hasMessage("FILE_NOT_READY");
 }
 @Test void refusesReplacementAfterDispatchButAllowsNewVersionAfterRefusal(){
  for(var s:new String[]{"queued","signing","unknown","completed","waiting_next"})
   assertThatThrownBy(()->SigningRules.replaceable(s)).hasMessage("DOCUMENT_ALREADY_USED");
  assertThatCode(()->SigningRules.replaceable("refused")).doesNotThrowAnyException();
  assertThatCode(()->SigningRules.replaceable(null)).doesNotThrowAnyException();
 }
 @Test void onlyAssignedActorAtCurrentStepCanSign(){
  assertThatCode(()->SigningRules.canAct(11,11,"waiting",1,1,true)).doesNotThrowAnyException();
  assertThatThrownBy(()->SigningRules.canAct(12,11,"waiting",1,1,true)).hasMessage("FORBIDDEN");
  assertThatThrownBy(()->SigningRules.canAct(11,11,"waiting",2,1,true)).hasMessage("SIGNING_STEP_NOT_READY");
  assertThatThrownBy(()->SigningRules.canAct(11,11,"waiting",1,1,false)).hasMessage("MINUTES_NOT_COMPLETED");
 }
 @Test void providerSuccessWithoutStoredArtifactNeverCompletesStep(){
  assertThat(SigningRules.completed(true,true,true)).isTrue();
  assertThat(SigningRules.completed(true,true,false)).isFalse();
  assertThat(SigningRules.completed(true,false,true)).isFalse();
  assertThat(SigningRules.completed(false,true,true)).isFalse();
 }
 @Test void recoveryNeverRepeatsUncertainSideEffects(){
  assertThat(SigningRules.recovery("upload_intent",false)).isEqualTo("unknown");
  assertThat(SigningRules.recovery("sign_intent",false)).isEqualTo("unknown");
  assertThat(SigningRules.recovery("download",true)).isEqualTo("download");
  assertThat(SigningRules.recovery("storage",true)).isEqualTo("download");
  assertThat(SigningRules.recovery("queued",false)).isEqualTo("queued");
 }
}
