package pbl6.ctsv.auth;

import java.nio.file.*;import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import pbl6.ctsv.mghp.application.SigningDatabase;
import pbl6.ctsv.shared.ca.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit bounded REAL probe: HTTPS/Auth/SQL + existing CA adapter + private Cloudinary.
 * Never run inherited tests here: invoke exactly #realMinutesTwoActorsStoreAndReadBothOutputs.
 * An intent file prevents accidental repeated CA signing after process interruption. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes=SigningRealIT.RealConfig.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","ctsv.files.enabled=true","ctsv.files.cloudinary.enabled=true","ctsv.files.reconciliation.enabled=false","ctsv.mghp.signing.enabled=true","ctsv.mghp.signing.worker.enabled=false","ca-dut.enabled=true","server.address=127.0.0.1"})
@EnabledIfSystemProperty(named="p11.real.enabled",matches="true")
class SigningRealIT extends SigningHttpIT {
 @Configuration(proxyBeanMethods=false) @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class RealConfig {@Bean DataSource source()throws Exception{return SigningDatabase.migrate();}}
 Path journal;
 @Override @BeforeAll void setup()throws Exception{
  String supplied=Objects.requireNonNull(System.getenv("P11_REAL_JOURNAL"),"Private journal required");
  var root=Path.of(System.getenv("LOCALAPPDATA"),"CTSV","ca-dut").toRealPath();journal=Path.of(supplied).toAbsolutePath().normalize();
  if(!journal.startsWith(root)||Files.exists(journal))throw new IllegalStateException("P11 real journal already exists or unsafe; inspect DB before any rerun");
  super.setup();
  System.setProperty("ctsv.mghp.signing.minutes-first.email","officer.e@dut.udn.vn");
  System.setProperty("ctsv.mghp.signing.decision-first.email","officer.e@dut.udn.vn");
  System.setProperty("ctsv.mghp.signing.last.email","staff.c@dut.udn.vn");
  var categories=((pbl6.ctsv.mghp.application.SigningProvider)provider); // Existing adapter is the only provider.
  var client=new CaDutClient(java.net.URI.create(System.getenv("CA_DUT_URL")),System.getenv("CA_DUT_CLIENT_ID"),System.getenv("CA_DUT_CLIENT_SECRET"),java.time.Clock.systemUTC());
  var catalog=client.categories();if(catalog.isEmpty())throw new IllegalStateException("CA category required");
  System.setProperty("ctsv.mghp.signing.category-id",catalog.getFirst().id().toString());
  pdf=Files.readAllBytes(Path.of(System.getenv("CA_DUT_TEST_PDF")));
  assertThat(CaPdfPreflight.sha256(pdf)).isEqualTo("d8d6256816ad520de5fbcbe14359d14c163f503ab93cdbe62234a0ba06798f38");
 }
 @Test void realMinutesTwoActorsStoreAndReadBothOutputs()throws Exception{
  var before=CaPdfSignatureVerifier.verify(pdf);var b=bundle(true);String doc=document(b,true);var q=prepare(doc);String request=q.body().path("id").asText(),path="/api/ctsv/yeu-cau-ky/"+request;
  Files.writeString(journal,JSON.writeValueAsString(Map.of("state","INTENT","requestId",request,"documentVersionId",doc,"database","ctsv_p11_test_20261123","sourceSha256",CaPdfPreflight.sha256(pdf))),StandardOpenOption.CREATE_NEW);
  queue(path,cv,false);worker.runRequest(Long.parseLong(request));var first=call("GET",path,null,cv,null).expect(200);
  assertThat(first.body().path("status").asText()).as("Inspect receipt/phase in DB; do not sign again").isEqualTo("waiting_next");
  long file1=first.body().path("steps").get(0).path("outputFile").path("id").asLong();
  var stored1=files.read(file1);assertThat(CaPdfSignatureVerifier.verify(stored1.bytes()).signatures()).hasSize(before.signatures().size()+1);
  Files.writeString(journal,JSON.writeValueAsString(Map.of("state","FIRST_STORED","requestId",request,"file1",file1,"hash1",stored1.sha256())));
  queue(path,pht,false);worker.runRequest(Long.parseLong(request));var last=call("GET",path,null,pht,null).expect(200);
  assertThat(last.body().path("status").asText()).as("Inspect receipt/phase in DB; do not sign again").isEqualTo("completed");
  long file2=last.body().path("steps").get(1).path("outputFile").path("id").asLong();assertThat(file2).isNotEqualTo(file1);var stored2=files.read(file2);
  assertThat(CaPdfSignatureVerifier.verify(stored2.bytes()).cryptoVerified()).isTrue();assertThat(CaPdfSignatureVerifier.verify(stored2.bytes()).signatures()).hasSize(before.signatures().size()+2);
  for(long file:new long[]{file1,file2}){
   var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://127.0.0.1:"+port+"/api/ctsv/tep/"+file+"/noi-dung")).header("Authorization","Bearer "+pht).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).containsExactly(files.read(file).bytes());
  }
  var evidence=Map.of("state","COMPLETED","requestId",request,"documentVersionId",doc,"file1",file1,"file2",file2,"hash1",stored1.sha256(),"hash2",stored2.sha256(),"signatures",before.signatures().size()+2,"real",List.of("HTTPS","AUTH11","SQL Server","CA DUT","Cloudinary authenticated"));
  Files.writeString(journal,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
  Files.createDirectories(Path.of("target/p11-evidence"));Files.writeString(Path.of("target/p11-evidence/real-minutes.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
 }
}
