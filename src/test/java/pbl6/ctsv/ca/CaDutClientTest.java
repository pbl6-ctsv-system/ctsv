package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

class CaDutClientTest {
    static final UUID ID=UUID.fromString("11111111-1111-4111-8111-111111111111");
    @Test void signingOmitsTemplateOverridesAndUsesProviderDefault() throws Exception {
        try(var s=new CaHttpStub()) {
            var posted=new java.util.concurrent.atomic.AtomicReference<String>();
            s.handler=r->{
                if(r.path().endsWith("token/"))return CaHttpStub.token();
                posted.set(new String(r.body(),java.nio.charset.StandardCharsets.UTF_8));
                return CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{\"id\":\""+ID+"\",\"document\":\""+ID+"\",\"certificate\":\""+ID+"\",\"certificate_serial\":\"TEST-E\",\"signer_email\":\"e@example.test\",\"signer_name\":\"Actual E\",\"document_hash\":\""+"0".repeat(64)+"\",\"signed_pdf_hash\":\""+"1".repeat(64)+"\",\"signed_at\":\"2026-11-23T00:00:00Z\",\"status\":\"valid\"}}");
            };
            for(UUID legacyOverride:new UUID[]{null,ID}){
                s.client().sign(ID,ID,"e@example.test","Actual E",legacyOverride,new CaPdfPreflight.Box(1,100,100,200,60));
                var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(posted.get());
                assertFalse(body.has("user_signature_id"));assertFalse(body.has("signature_image_base64"));
                assertEquals(ID.toString(),body.path("certificate_id").asText());assertEquals("e@example.test",body.path("signer_email").asText());
            }
        }
    }
    @Test void readsPaginatedCategoriesAndDifferentTemplateEnvelope() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->{if(r.path().endsWith("token/"))return CaHttpStub.token();
                if(r.path().contains("categories"))return CaHttpStub.Reply.json(200,"{\"count\":1,\"next\":null,\"results\":[{\"id\":\""+ID+"\",\"code\":\"quyet_dinh\",\"name\":\"Quyết định\"}]}");
                return CaHttpStub.Reply.json(200,"{\"success\":true,\"data\":[]}");};
            var c=s.client();assertEquals(ID,c.categories().getFirst().id());assertTrue(c.templates("e@example.test").items().isEmpty());
            assertEquals(1,s.requests.stream().filter(r->r.path().endsWith("token/")).count());
            assertTrue(s.requests.stream().filter(r->r.method().equals("GET")).allMatch(r->("Bearer "+CaHttpStub.ACCESS).equals(r.authorization())));
        }
    }
    @Test void rejectsForeignOriginAndWrongResourcePaginationWithoutSendingToken() throws Exception {
        try(var s=new CaHttpStub()) {
            for(String next:new String[]{"https://evil.invalid/capture",s.origin()+"/api/v1/signatures/sign/",s.origin()+"/api/v1/certificates/?user_email=other@example.test"}) {
                s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"{\"count\":1,\"results\":[],\"next\":\""+next+"\"}");
                assertThrows(CaFailure.class,()->s.client().categories());
            }
            assertTrue(s.requests.stream().noneMatch(r->r.path().contains("sign/")));
        }
    }
    @Test void refusesPlainHttpAndMissingConfig() {
        assertThrows(CaFailure.class,()->new CaDutClient(URI.create("http://example.test"),"id","secret",Clock.systemUTC()));
        assertThrows(CaFailure.class,()->new CaDutClient(URI.create("https://example.test/api/v1"),"id","secret",Clock.systemUTC()));
        assertThrows(CaFailure.class,()->new CaDutClient(URI.create("https://example.test"),"","",Clock.systemUTC()));
    }
    @Test void refreshesOneUnauthorizedGetUsingTopLevelRotation() throws Exception {
        try(var s=new CaHttpStub()) {
            var gets=new AtomicInteger();s.handler=r->{
                if(r.path().endsWith("token/"))return CaHttpStub.token();
                if(r.path().endsWith("refresh/")) {assertTrue(new String(r.body()).contains("refresh-1"));return CaHttpStub.Reply.json(200,"{\"access\":\""+CaHttpStub.ACCESS_2+"\",\"refresh\":\"refresh-2\"}");}
                return gets.incrementAndGet()==1?CaHttpStub.Reply.json(401,"{}"):CaHttpStub.Reply.json(200,"{\"count\":0,\"next\":null,\"results\":[]}");};
            assertTrue(s.client().categories().isEmpty());assertEquals(2,gets.get());
            assertEquals("Bearer "+CaHttpStub.ACCESS_2,s.requests.getLast().authorization());
        }
    }
    @Test void doesNotLogProviderBodyOrFollowRedirects() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(403,"{\"message\":\"synthetic-secret https://private/?token=SECRET\"}");
            var f=assertThrows(CaFailure.class,()->s.client().categories());assertEquals(403,f.httpStatus());assertFalse(f.toString().contains("secret"));assertNull(f.getCause());
        }
    }
    @Test void enforcesResponseLimitAndSchema() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"x".repeat(1_000_001));
            assertThrows(CaFailure.class,()->s.client().categories());
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"{\"results\":[{}]}");
            assertThrows(CaFailure.class,()->s.client().categories());
        }
    }
    @Test void expiredRefreshFallsBackToFreshClientAuthPerProviderContract() throws Exception {
        try(var s=new CaHttpStub()) {
            var auth=new AtomicInteger();var gets=new AtomicInteger();
            s.handler=r->{if(r.path().endsWith("token/")){auth.incrementAndGet();return CaHttpStub.token();}
                if(r.path().endsWith("refresh/"))return CaHttpStub.Reply.json(401,"{\"errors\":{\"code\":\"token_not_valid\"}}");
                return gets.incrementAndGet()==1?CaHttpStub.Reply.json(401,"{}"):CaHttpStub.Reply.json(200,"{\"count\":0,\"next\":null,\"results\":[]}");};
            assertTrue(s.client().categories().isEmpty());assertEquals(2,auth.get());
        }
    }
    @Test void emptyBodyAndMalformedSuccessAfterWriteAreSafeUnknownFailures() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"");
            assertThrows(CaFailure.class,()->s.client().categories());
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{}}");
            var input=CaPdfPreflight.inspect(CaPdfPreflightTest.pdf(),new CaPdfPreflight.Box(1,0,0,20,20));
            assertTrue(assertThrows(CaFailure.class,()->s.client().upload(input,"TEST",ID)).uncertain());
        }
    }
    @Test void sideEffectingPostIsNeverRetriedOn401Or500() throws Exception {
        try(var s=new CaHttpStub()) {
            for(int status:new int[]{401,500}) {
                s.requests.clear();s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(status,"{}");
                var c=s.client();assertThrows(CaFailure.class,()->c.sign(ID,ID,"e@example.test","Actual E",null,new CaPdfPreflight.Box(1,100,100,200,60)));
                assertEquals(1,s.requests.stream().filter(r->r.path().endsWith("sign/")).count());
                assertEquals(0,s.requests.stream().filter(r->r.path().endsWith("refresh/")).count());
            }
        }
    }
    @Test void transientGetRetriesAtMostThreeAttemptsWhile403AndSchemaNeverRetry() throws Exception {
        try(var s=new CaHttpStub()) {
            var calls=new AtomicInteger();s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(calls.incrementAndGet()<3?503:200,calls.get()<3?"{}":"{\"count\":0,\"next\":null,\"results\":[]}");
            assertTrue(s.client().categories().isEmpty());assertEquals(3,calls.get());
            calls.set(0);s.handler=r->{if(r.path().endsWith("token/"))return CaHttpStub.token();calls.incrementAndGet();return CaHttpStub.Reply.json(503,"{}");};
            assertThrows(CaFailure.class,()->s.client().categories());assertEquals(3,calls.get());
            calls.set(0);s.handler=r->{if(r.path().endsWith("token/"))return CaHttpStub.token();calls.incrementAndGet();return CaHttpStub.Reply.json(403,"{}");};
            assertThrows(CaFailure.class,()->s.client().categories());assertEquals(1,calls.get());
        }
    }
    @Test void rejectsControlCharactersWithoutLeakingBearerToken() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->CaHttpStub.Reply.json(200,"{\"success\":true,\"data\":{\"access\":\"private-token-\\u0001-value\",\"refresh\":\"refresh\"}}");
            var f=assertThrows(CaFailure.class,()->s.client().categories());assertFalse(f.toString().contains("private-token"));
        }
    }
    @Test void unexpectedSuccessStatusAfterPostIsUnknownNotSafeToRetry() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(200,"{}");
            assertTrue(assertThrows(CaFailure.class,()->s.client().sign(ID,ID,"e@example.test","Actual E",null,new CaPdfPreflight.Box(1,100,100,200,60))).uncertain());
        }
    }
    @Test void uploadChecksCategoryAndRetainsVersionMetadata() throws Exception {
        try(var s=new CaHttpStub()) {
            var input=CaPdfPreflight.inspect(CaPdfPreflightTest.pdf(),new CaPdfPreflight.Box(1,0,0,20,20));
            s.handler=r->r.path().endsWith("token/")?CaHttpStub.token():CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{\"id\":\""+ID+"\",\"file_hash\":\""+input.sha256()+"\",\"category\":\""+UUID.randomUUID()+"\",\"current_version\":1,\"status\":\"draft\",\"is_signed\":false}}");
            assertThrows(CaFailure.class,()->s.client().upload(input,"TEST",ID));
        }
    }
    @Test void paginationCollectsSecondSameOriginPage() throws Exception {
        try(var s=new CaHttpStub()) {
            s.handler=r->{if(r.path().endsWith("token/"))return CaHttpStub.token();var next=r.query()==null?"\""+s.origin()+"/api/v1/documents/categories/?page=2\"":"null";
                return CaHttpStub.Reply.json(200,"{\"count\":2,\"next\":"+next+",\"results\":[{\"id\":\""+UUID.randomUUID()+"\",\"code\":\"test\",\"name\":\"TEST\"}]}");};
            assertEquals(2,s.client().categories().size());
        }
    }
}
