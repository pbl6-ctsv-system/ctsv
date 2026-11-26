package pbl6.ctsv.ca;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.shared.ca.*;
import static org.assertj.core.api.Assertions.*;

class CaDutRenewalTest {
 static final UUID ID=UUID.fromString("33333333-3333-4333-8333-333333333333");
 String item(String status){return "{\"id\":\""+ID+"\",\"user\":\""+ID+"\",\"user_display\":\"SYNTHETIC E\",\"request_type\":\"renew\",\"status\":\""+status+"\",\"common_name\":\"SYNTHETIC E\",\"valid_days\":365,\"admin_comment\":\"CA synthetic\",\"created_at\":\"2026-11-25T07:00:00Z\",\"processed_at\":"+(status.equals("pending")?"null":"\"2026-11-25T07:05:00Z\"")+"}";}
 @Test void renewSendsOnlyRenewConfiguredEmailAndReason()throws Exception{
  try(var s=new CaHttpStub()){
   s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":"+item("pending")+"}");
   var result=s.client().renewCertificate("E@EXAMPLE.TEST","Đề nghị gia hạn synthetic");assertThat(result.id()).isEqualTo(ID);assertThat(result.status()).isEqualTo("pending");
   var posted=new ObjectMapper().readTree(s.requests.stream().filter(r->r.path().contains("requests/")).findFirst().orElseThrow().body());
   assertThat(posted.path("request_type").asText()).isEqualTo("renew");assertThat(posted.path("signer_email").asText()).isEqualTo("e@example.test");assertThat(posted.path("reason").asText()).isEqualTo("Đề nghị gia hạn synthetic");assertThat(posted.size()).isEqualTo(3);
  }
 }
 @Test void requestQueriesRequireCorrectStatusAndRenewFilter()throws Exception{
  try(var s=new CaHttpStub()){
   s.handler=r->{if(r.path().endsWith("token/"))return CaHttpStub.token();String status=r.query().contains("status=approved")?"approved":r.query().contains("status=rejected")?"rejected":"pending";return CaHttpStub.Reply.json(200,"{\"count\":1,\"next\":null,\"results\":["+item(status)+"]}");};
   var c=s.client();for(String status:List.of("pending","approved","rejected")){var requests=c.certificateRequests("e@example.test",status);assertThat(requests).hasSize(1);assertThat(requests.getFirst().status()).isEqualTo(status);}
   assertThat(s.requests.stream().filter(r->r.method().equals("GET"))).allSatisfy(r->{assertThat(r.query()).contains("user_email=e%40example.test","request_type=renew");});
   int before=s.requests.size();assertThatThrownBy(()->c.certificateRequests("e@example.test","active")).isInstanceOf(CaFailure.class);assertThat(s.requests).hasSize(before);
  }
 }
 @Test void renewalPostIsNotRepeatedOnRejectionOrLostResult()throws Exception{
  try(var s=new CaHttpStub()){
   for(int status:List.of(400,500)){
    s.requests.clear();s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(status,"{\"success\":false,\"message\":\"synthetic failure\"}");
    assertThatThrownBy(()->s.client().renewCertificate("e@example.test","Synthetic")).isInstanceOf(CaFailure.class);
    assertThat(s.requests.stream().filter(r->r.path().contains("requests/")&&r.method().equals("POST")).count()).isEqualTo(1);
   }
  }
 }
 @Test void malformedSuccessIsUncertainAndNeverBecomesApproved()throws Exception{
  try(var s=new CaHttpStub()){
   s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{}}");
   assertThatThrownBy(()->s.client().renewCertificate("e@example.test","Synthetic")).isInstanceOfSatisfying(CaFailure.class,e->assertThat(e.uncertain()).isTrue());
  }
 }
 @Test void certificateRequestCommentsNeverCarryTokensOrDeliveryUrls()throws Exception{
  try(var s=new CaHttpStub()){
   String row=item("rejected").replace("CA synthetic","Bearer SECRET https://private.invalid/?token=SECRET");
   s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"{\"count\":1,\"next\":null,\"results\":["+row+"]}");
   var result=s.client().certificateRequests("e@example.test","rejected").getFirst();assertThat(result.adminComment()).doesNotContain("SECRET","private.invalid","Bearer");
  }
 }
 @Test void provider400UsesWhitelistedFieldCodesWithoutRawBody()throws Exception{
  try(var s=new CaHttpStub()){
   for(var e:Map.of("file","CA_FILE_TOO_LARGE","category","CA_CATEGORY_INVALID","certificate_id","CA_CERTIFICATE_INVALID").entrySet()){
    s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(400,"{\"success\":false,\"errors\":{\""+e.getKey()+"\":[\"File quá lớn. SECRET https://private.invalid/\"]}}");
    assertThatThrownBy(()->s.client().sign(ID,ID,"e@example.test","SYNTHETIC",new CaPdfPreflight.Box(1,0,0,20,20))).isInstanceOfSatisfying(CaFailure.class,f->{assertThat(f.code()).isEqualTo(e.getValue());assertThat(f.toString()).doesNotContain("SECRET","private.invalid");});
   }
  }
 }
}
