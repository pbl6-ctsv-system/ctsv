package pbl6.ctsv.mghp.application;

import org.junit.jupiter.api.Test;
import pbl6.ctsv.shared.ca.CaFailure;
import static org.assertj.core.api.Assertions.*;

class SigningFailurePolicyTest {
 @Test void knownUserPdf400HasVietnameseGuidance(){
  var r=SigningFailurePolicy.classify("upload",new CaFailure("CA_FILE_TOO_LARGE",400,false));
  assertThat(r.userCorrectable()).isTrue();assertThat(r.publicMessage()).contains("PDF");assertThat(r.httpStatus()).isEqualTo(400);
 }
 @Test void serverCategory400StillNeedsAdmin(){
  var r=SigningFailurePolicy.classify("upload",new CaFailure("CA_CATEGORY_INVALID",400,false));
  assertThat(r.userCorrectable()).isFalse();assertThat(r.publicMessage()).contains("quản trị");assertThat(r.apiPath()).isEqualTo("/api/v1/documents/");
 }
 @Test void noSuchKeyKeepsStageHttpAndSafeCode(){
  var r=SigningFailurePolicy.classify("download",new CaFailure("CA_STORAGE_NO_SUCH_KEY",404,false));
  assertThat(r.code()).isEqualTo("CA_STORAGE_NO_SUCH_KEY");assertThat(r.stage()).isEqualTo("download");assertThat(r.httpStatus()).isEqualTo(404);assertThat(r.userCorrectable()).isFalse();
 }
 @Test void arbitraryExceptionOrProviderCodeNeverBecomesPublicOrPersistedText(){
  for(var e:new RuntimeException[]{new IllegalStateException("Bearer SECRET token=https://private.invalid/"),new CaFailure("UNTRUSTED secret=SECRET",500,true)}){
   var r=SigningFailurePolicy.classify("storage",e);assertThat(r.toString()).doesNotContain("SECRET","Bearer","private.invalid");assertThat(r.code()).isEqualTo("SIGNING_INTERNAL_ERROR");
  }
 }
 @Test void signTimeoutIsPreservedAsUncertainWithoutClaimingFailedAtProvider(){
  var r=SigningFailurePolicy.classify("sign_intent",new CaFailure("CA_TIMEOUT",0,true));
  assertThat(r.uncertain()).isTrue();assertThat(r.apiMethod()).isEqualTo("POST");assertThat(r.apiPath()).isEqualTo("/api/v1/signatures/sign/");assertThat(r.userCorrectable()).isFalse();
 }
 @Test void sqlFailureAfterDispatchKeepsEffectiveUncertaintyForReplay(){
  var failure=new org.springframework.dao.DataAccessResourceFailureException("SYNTHETIC database unavailable");
  for(String phase:new String[]{"upload_intent","sign_intent"})assertThat(SigningFailurePolicy.classify(phase,failure).uncertain()).isTrue();
  assertThat(SigningFailurePolicy.classify("database",failure).uncertain()).isFalse();
 }
}
