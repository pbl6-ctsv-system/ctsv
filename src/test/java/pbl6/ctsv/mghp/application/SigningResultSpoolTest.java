package pbl6.ctsv.mghp.application;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbl6.ctsv.shared.ca.CaFailure;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.Receipt;
import static org.assertj.core.api.Assertions.*;

class SigningResultSpoolTest {
 @TempDir Path root;
 final Clock clock=Clock.fixed(Instant.parse("2026-11-25T07:00:00Z"),ZoneOffset.UTC);
 final UUID operation=UUID.randomUUID();
 final byte[] bytes="%PDF-SYNTHETIC-SPOOL-ONLY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 SigningResultSpool.Context context(){return new SigningResultSpool.Context(operation,41,42,43,44,"chuyen_vien","synthetic.cv","Biên bản synthetic","Đợt synthetic",1,"a".repeat(64));}
 Receipt receipt(){return new Receipt(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"SYNTHETIC","signer@example.invalid","Synthetic signer","a".repeat(64),CaPdfPreflight.sha256(bytes),clock.instant(),"valid");}
 @Test void receiptAndFailureSurviveNewInstanceBeforeSqlCanCommit(){
  var spool=new SigningResultSpool(root,clock);spool.start(context());var receipt=receipt();spool.receipt(operation,receipt);
  spool.failure(operation,SigningFailurePolicy.classify("download",new CaFailure("CA_STORAGE_NO_SUCH_KEY",404,false)));
  var restarted=new SigningResultSpool(root,clock);var entry=restarted.read(operation).orElseThrow();
  assertThat(entry.receipt()).isEqualTo(receipt);assertThat(entry.hadError()).isTrue();assertThat(entry.error().code()).isEqualTo("CA_STORAGE_NO_SUCH_KEY");
  assertThat(entry.context().account()).isEqualTo("synthetic.cv");assertThat(restarted.pdf(operation)).isEmpty();
 }
 @Test void downloadedOutputPersistsAndErrorFileIsKeptEvenAfterCompletion()throws Exception{
  var spool=new SigningResultSpool(root,clock);spool.start(context());spool.receipt(operation,receipt());spool.pdf(operation,bytes);
  spool.failure(operation,SigningFailurePolicy.classify("storage",new IllegalStateException("SECRET SHOULD NOT BE SAVED")));
  spool.completed(operation,true);
  var restarted=new SigningResultSpool(root,clock);assertThat(restarted.pdf(operation).orElseThrow()).isEqualTo(bytes);
  assertThat(restarted.read(operation).orElseThrow().completed()).isTrue();
  assertThatCode(()->restarted.delete(operation)).doesNotThrowAnyException();
  assertThat(restarted.pdf(operation)).isEmpty();assertThat(restarted.read(operation).orElseThrow().deleted()).isTrue();
  assertThat(Files.readString(root.resolve(operation+".json"))).doesNotContain("SECRET SHOULD NOT BE SAVED");
 }
 @Test void successfulNeverFailedOutputOnlyCleansAfterCloudAndSqlCommit(){
  var spool=new SigningResultSpool(root,clock);spool.start(context());spool.receipt(operation,receipt());spool.pdf(operation,bytes);
  assertThatThrownBy(()->spool.completed(operation,false)).hasMessage("SIGNING_RESULT_NOT_COMMITTED");
  assertThat(spool.pdf(operation)).isPresent();spool.completed(operation,true);
  assertThat(spool.pdf(operation)).isEmpty();assertThat(spool.read(operation)).isEmpty();
 }
 @Test void sameOperationNeverOverwritesDifferentPdfOrContext(){
  var spool=new SigningResultSpool(root,clock);spool.start(context());spool.pdf(operation,bytes);spool.pdf(operation,bytes);
  assertThatThrownBy(()->spool.pdf(operation,"different".getBytes())).hasMessage("SIGNING_SPOOL_CONTENT_CONFLICT");
  var c=context();var conflicting=new SigningResultSpool.Context(operation,999,c.request(),c.step(),c.actor(),c.role(),c.account(),c.title(),c.round(),c.version(),c.inputHash());
  assertThatThrownBy(()->spool.start(conflicting)).hasMessage("SIGNING_SPOOL_CONTEXT_CONFLICT");
  assertThat(spool.pdf(operation).orElseThrow()).isEqualTo(bytes);
 }
 @Test void adminMayDeleteBeforeCloudSqlAndRepeatedDeleteIsSafe(){
  var spool=new SigningResultSpool(root,clock);spool.start(context());spool.pdf(operation,bytes);spool.delete(operation);spool.delete(operation);
  assertThat(spool.pdf(operation)).isEmpty();assertThat(spool.read(operation).orElseThrow().deleted()).isTrue();
 }
 @Test void matchingHashAloneDoesNotClaimPdfSignatureVerification(){
  var spool=new SigningResultSpool(root,clock);spool.start(context());spool.receipt(operation,receipt());spool.pdf(operation,bytes);
  assertThat(spool.verified(operation)).isFalse();spool.verified(operation,CaPdfPreflight.sha256(bytes));
  assertThat(new SigningResultSpool(root,clock).verified(operation)).isTrue();
  spool.delete(operation);assertThat(spool.verified(operation)).isFalse();
 }
 @Test void nonDirectoryRootAndUnownedFileAreNotAccepted()throws Exception{
  Path file=root.resolve("not-a-directory");Files.writeString(file,"existing");
  assertThatThrownBy(()->new SigningResultSpool(file,clock)).hasMessage("SIGNING_SPOOL_UNAVAILABLE");
  var spool=new SigningResultSpool(root.resolve("safe"),clock);
  assertThatThrownBy(()->spool.pdf(operation,bytes)).hasMessage("SIGNING_SPOOL_NOT_FOUND");
 }
}
