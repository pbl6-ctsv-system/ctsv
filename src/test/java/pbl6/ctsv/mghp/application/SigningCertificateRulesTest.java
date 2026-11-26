package pbl6.ctsv.mghp.application;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static org.assertj.core.api.Assertions.*;
class SigningCertificateRulesTest {
 final Instant now=Instant.parse("2026-11-25T07:00:00Z");
 Certificate cert(long seconds,boolean valid,boolean key){return new Certificate(UUID.randomUUID(),UUID.randomUUID(),"SYNTHETIC","SYNTHETIC","active",now.minusSeconds(86400),now.plusSeconds(seconds),valid,key);}
 SigningCertificateRules.Observation observe(Certificate...c){return SigningCertificateRules.observe(new Certificates("signer@example.invalid",List.of(c)),now);}
 @Test void exactTenDaysIsNotWarningButUnderTenIs(){
  assertThat(observe(cert(11*86400,true,true)).state()).isEqualTo("valid");
  assertThat(observe(cert(10*86400,true,true)).state()).isEqualTo("valid");
  assertThat(observe(cert(10*86400-1,true,true)).state()).isEqualTo("soon");
  assertThat(observe(cert(0,false,true)).state()).isEqualTo("expired");
 }
 @Test void emptyOrUnusableIsNotAnInventedExpiredCertificate(){
  assertThat(observe().state()).isEqualTo("not_found");
  assertThat(observe(cert(86400,false,true)).state()).isEqualTo("not_ready");
  assertThat(observe(cert(86400,true,false)).state()).isEqualTo("not_ready");
 }
 @Test void multipleUsableCertificatesAreNotChosenArbitrarily(){
  var o=observe(cert(86400,true,true),cert(86400,true,true));assertThat(o.state()).isEqualTo("ambiguous");assertThat(o.selected()).isNull();
 }
}
