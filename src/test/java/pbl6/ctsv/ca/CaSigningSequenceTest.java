package pbl6.ctsv.ca;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import pbl6.ctsv.shared.ca.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CaSigningSequenceTest {
    static final UUID DOC=UUID.fromString("33333333-3333-4333-8333-333333333333"), CAT=UUID.randomUUID();
    final ObjectMapper json=new ObjectMapper();
    byte[] original,first,second;CaHttpStub server;CaDutClient client;
    final List<CaSigningSequence.Event> events=new ArrayList<>();
    final AtomicInteger uploads=new AtomicInteger(),signs=new AtomicInteger();
    String download="PDF";boolean timeoutSign,wrongReceipt,wrongInput2,badUpload,invalidCert;
    CaSignerBinding.Signer e,c;
    @BeforeEach void setup() throws Exception {
        original=CaPdfPreflightTest.pdf();first=CaPdfSignatureVerifierTest.signed(original,101);second=CaPdfSignatureVerifierTest.signed(first,202);
        e=signer("e@example.test",101);c=signer("c@example.test",202);server=new CaHttpStub();client=server.client();server.handler=this::reply;
        e=new CaSignerBinding.Signer(e.email(),e.name(),e.certificate(),null,CaPdfSignatureVerifier.verify(first).signatures().getLast().fingerprint());
        c=new CaSignerBinding.Signer(c.email(),c.name(),c.certificate(),null,CaPdfSignatureVerifier.verify(second).signatures().getLast().fingerprint());
    }
    static CaSignerBinding.Signer signer(String email,int serial) {
        var cert=new Certificate(UUID.nameUUIDFromBytes(email.getBytes()),UUID.nameUUIDFromBytes((email+"user").getBytes()),"Actual "+serial,Integer.toHexString(serial),"active",Instant.now().minusSeconds(600),Instant.now().plusSeconds(600),true,true);
        return new CaSignerBinding.Signer(email,cert.userDisplay(),cert,null);
    }
    CaHttpStub.Reply reply(CaHttpStub.Request r) {
        if(r.path().endsWith("token/"))return CaHttpStub.token();
        if(r.path().contains("certificates/")) {
            var s=r.query().contains("e%40")?e:c;var t=s.certificate();
            return CaHttpStub.Reply.json(200,"{\"count\":1,\"next\":null,\"results\":[{\"id\":\""+t.id()+"\",\"user\":\""+t.user()+"\",\"user_display\":\""+t.userDisplay()+"\",\"serial_number\":\""+t.serialNumber()+"\",\"status\":\""+(invalidCert?"revoked":"active")+"\",\"valid_from\":\""+t.validFrom()+"\",\"valid_to\":\""+t.validTo()+"\",\"is_valid\":true,\"has_private_key\":true}]}");
        }
        if(r.path().contains("user-signatures"))return CaHttpStub.Reply.json(200,"{\"success\":true,\"data\":[]}");
        if(r.path().equals("/api/v1/documents/")) {
            uploads.incrementAndGet();assertTrue(new String(r.body(),java.nio.charset.StandardCharsets.ISO_8859_1).contains("name=\"category\""));
            return CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{\"id\":\""+DOC+"\",\"file_hash\":\""+(badUpload?"0".repeat(64):CaPdfPreflight.sha256(original))+"\",\"category\":\""+CAT+"\",\"current_version\":1,\"status\":\"draft\",\"is_signed\":false}}");
        }
        if(r.path().endsWith("sign/")) {
            int step=signs.incrementAndGet();try {
                if(timeoutSign)Thread.sleep(3500);
                var req=json.readTree(r.body());var s=step==1?e:c;
                assertEquals(s.email(),req.path("signer_email").asText());assertEquals(s.certificate().id().toString(),req.path("certificate_id").asText());
                var input=step==1?original:first;var output=step==1?first:second;
                return CaHttpStub.Reply.json(201,"{\"success\":true,\"data\":{\"id\":\""+UUID.randomUUID()+"\",\"document\":\""+DOC+"\",\"certificate\":\""+(wrongReceipt?UUID.randomUUID():s.certificate().id())+"\",\"certificate_serial\":\""+s.certificate().serialNumber()+"\",\"signer_email\":\""+s.email()+"\",\"signer_name\":\""+s.name()+"\",\"document_hash\":\""+(step==2&&wrongInput2?CaPdfPreflight.sha256(original):CaPdfPreflight.sha256(input))+"\",\"signed_pdf_hash\":\""+CaPdfPreflight.sha256(output)+"\",\"signed_at\":\""+Instant.now()+"\",\"status\":\"valid\",\"signed_file_url\":\"https://untrusted.invalid/?token=never-follow\"}}");
            }catch(Exception ex){throw new RuntimeException(ex);}
        }
        if(r.path().endsWith("view-file/")) {
            if(download.startsWith("STORAGE"))return CaHttpStub.Reply.json(download.endsWith("404")?404:500,"{\"message\":\"NoSuchKey private signed token\"}");
            if(download.equals("JSON"))return CaHttpStub.Reply.json(200,"{\"success\":false}");
            byte[] pdf=signs.get()==1?first:second;if(download.equals("TRUNCATED"))pdf=Arrays.copyOf(pdf,100);if(download.equals("HASH"))pdf=original;
            return new CaHttpStub.Reply(200,"application/pdf",pdf);
        }
        return CaHttpStub.Reply.json(404,"{}");
    }
    List<CaSigningSequence.Step> steps(){return List.of(new CaSigningSequence.Step(e,new CaPdfPreflight.Box(1,100,100,200,60)),new CaSigningSequence.Step(c,new CaPdfPreflight.Box(1,100,200,200,60)));}
    CaSigningSequence.Result run(){return new CaSigningSequence(client,Clock.systemUTC()).run(CaPdfPreflight.inspect(original,steps().getFirst().box()),"Synthetic TEST",CAT,steps(),(s,h,i)->new CaSignerBinding.Consent("owner-test",s.email(),s.certificate().id(),h,i),events::add);}
    @AfterEach void stop(){server.close();}
    @Test void twoStepsUseSameDocumentVerifiedPriorBytesAndDistinctCerts() {
        var result=run();assertEquals("COMPLETED",result.status());assertEquals(2,signs.get());assertEquals(1,uploads.get());
        assertTrue(result.steps().stream().allMatch(s->s.caReportedSuccess()&&s.pdfRetrieved()&&s.hashVerified()&&s.cryptoVerified()));
        assertEquals(CaPdfPreflight.sha256(first),result.steps().get(1).receipt().documentHash());
        assertEquals("UPLOAD_DISPATCHED",events.getFirst().type());assertTrue(events.stream().anyMatch(e->e.type().equals("SIGN_DISPATCHED")));
    }
    @Test void storageErrorPreservesReceiptAndStopsBeforeSecondSigner() {
        download="STORAGE";var result=run();assertEquals("PARTIAL",result.status());assertEquals("CA_STORAGE_NO_SUCH_KEY",result.failureCode());assertEquals(1,signs.get());
        var evidence=result.steps().getFirst();assertTrue(evidence.caReportedSuccess());assertFalse(evidence.pdfRetrieved());assertFalse(evidence.hashVerified());assertFalse(evidence.cryptoVerified());
    }
    @Test void missingStoredPdf404AlsoStopsBeforeSecondSigner() {
        download="STORAGE404";var result=run();assertEquals("PARTIAL",result.status());assertEquals("CA_STORAGE_NO_SUCH_KEY",result.failureCode());assertEquals(1,signs.get());
        assertTrue(result.steps().getFirst().caReportedSuccess());assertFalse(result.steps().getFirst().pdfRetrieved());
    }
    @Test void jsonTruncatedOrWrongHashCannotAdvanceToSecondSigner() {
        for(String fault:List.of("JSON","TRUNCATED","HASH")) {signs.set(0);download=fault;var result=run();assertNotEquals("COMPLETED",result.status());assertEquals(1,signs.get());assertFalse(result.steps().getFirst().cryptoVerified());}
    }
    @Test void lostResponseIsUnknownWithoutAnyPostRetry() {
        timeoutSign=true;var result=run();assertEquals("UNKNOWN",result.status());assertEquals(1,signs.get());assertEquals(1,uploads.get());
    }
    @Test void mismatchReceiptOrNextInputHashIsNotCompleted() {
        wrongReceipt=true;assertNotEquals("COMPLETED",run().status());assertEquals(1,signs.get());
        wrongReceipt=false;wrongInput2=true;signs.set(0);assertNotEquals("COMPLETED",run().status());assertEquals(2,signs.get());
    }
    @Test void uploadHashMismatchStopsBeforeSigning() {
        badUpload=true;var result=run();assertNotEquals("COMPLETED",result.status());assertEquals(0,signs.get());
    }
    @Test void revokedCertificateOrMissingConsentDoesNotSendSign() {
        invalidCert=true;assertNotEquals("COMPLETED",run().status());assertEquals(0,signs.get());
        invalidCert=false;var result=new CaSigningSequence(client,Clock.systemUTC()).run(CaPdfPreflight.inspect(original,steps().getFirst().box()),"TEST",CAT,steps(),(s,h,i)->null,events::add);
        assertNotEquals("COMPLETED",result.status());assertEquals(0,signs.get());
    }
    @Test void localIntentWriteFailurePreventsExternalSideEffect() {
        var result=new CaSigningSequence(client,Clock.systemUTC()).run(CaPdfPreflight.inspect(original,steps().getFirst().box()),"TEST",CAT,steps(),(s,h,i)->new CaSignerBinding.Consent("owner-test",s.email(),s.certificate().id(),h,i),event->{throw new java.io.IOException("fixture-disk-failure");});
        assertNotEquals("COMPLETED",result.status());assertEquals(0,uploads.get());assertEquals(0,signs.get());
    }
    @Test void opaqueProviderSerialMustNotPreventUploadOrInventX509CertificateLink() {
        var t=e.certificate();e=new CaSignerBinding.Signer(e.email(),e.name(),new Certificate(t.id(),t.user(),t.userDisplay(),"TEST-OFFICER.E-SERIAL",t.status(),t.validFrom(),t.validTo(),true,true),null);
        var result=run();assertEquals(1,uploads.get());assertEquals(1,signs.get());
        assertEquals("CA_CERTIFICATE_LINK_NOT_VERIFIED",result.failureCode());assertTrue(result.steps().getFirst().caReportedSuccess());
    }
    @Test void externallyPinnedCertificateFingerprintAllowsOpaqueSerialWithoutGuessing() {
        var t=e.certificate();var fp=CaPdfSignatureVerifier.verify(first).signatures().getLast().fingerprint();
        e=new CaSignerBinding.Signer(e.email(),e.name(),new Certificate(t.id(),t.user(),t.userDisplay(),"OPAQUE-E",t.status(),t.validFrom(),t.validTo(),true,true),null,fp);
        assertEquals("COMPLETED",run().status());assertEquals(2,signs.get());
    }
    @Test void failedReceiptJournalIsUnknownAndNeverDispatchesSecondSigner() {
        var result=new CaSigningSequence(client,Clock.systemUTC()).run(CaPdfPreflight.inspect(original,steps().getFirst().box()),"TEST",CAT,steps(),(s,h,i)->new CaSignerBinding.Consent("owner-test",s.email(),s.certificate().id(),h,i),event->{if(event.type().equals("SIGN_RECEIVED"))throw new java.io.IOException("fixture-disk-failure");events.add(event);});
        assertEquals("UNKNOWN",result.status());assertEquals(1,signs.get());assertEquals(1,uploads.get());
    }
    @Test void signedPdfFromDifferentSourceCannotAdvanceDespiteMatchingProviderHashes() throws Exception {
        first=CaPdfSignatureVerifierTest.signed(CaPdfPreflightTest.pdf(),101);second=CaPdfSignatureVerifierTest.signed(first,202);
        e=new CaSignerBinding.Signer(e.email(),e.name(),e.certificate(),null,CaPdfSignatureVerifier.verify(first).signatures().getLast().fingerprint());
        c=new CaSignerBinding.Signer(c.email(),c.name(),c.certificate(),null,CaPdfSignatureVerifier.verify(second).signatures().getLast().fingerprint());
        var result=run();assertEquals("CA_REVISION_MISMATCH",result.failureCode());assertEquals(1,signs.get());
    }
    @Test void stepsAreSnapshottedBeforeConsentCallbackCanChangeCallerList() {
        var mutable=new ArrayList<>(steps());
        var result=new CaSigningSequence(client,Clock.systemUTC()).run(CaPdfPreflight.inspect(original,mutable.getFirst().box()),"TEST",CAT,mutable,(s,h,i)->{
            mutable.set(1,new CaSigningSequence.Step(e,new CaPdfPreflight.Box(1,100,200,200,60)));return new CaSignerBinding.Consent("owner-test",s.email(),s.certificate().id(),h,i);
        },events::add);
        assertEquals("COMPLETED",result.status());assertEquals(2,signs.get());
    }
    @Test void forgedInputMetadataCannotCauseUpload() {
        var forged=new CaPdfPreflight.Input(original,"0".repeat(64),0,0,0,0,0,0,0);
        var result=new CaSigningSequence(client,Clock.systemUTC()).run(forged,"TEST",CAT,steps(),(s,h,i)->new CaSignerBinding.Consent("owner-test",s.email(),s.certificate().id(),h,i),events::add);
        assertNotEquals("COMPLETED",result.status());assertEquals(0,uploads.get());
    }
    @Test void hexLookingProviderSerialIsNotIndependentProofOfCertificateLink() {
        e=new CaSignerBinding.Signer(e.email(),e.name(),e.certificate(),null);
        assertEquals("CA_CERTIFICATE_LINK_NOT_VERIFIED",run().failureCode());assertEquals(1,signs.get());
    }
    @Test void twoSignersCannotPinTheSameRealCertificateFingerprint() {
        c=new CaSignerBinding.Signer(c.email(),c.name(),c.certificate(),null,e.expectedFingerprint());
        var result=run();assertNotEquals("COMPLETED",result.status());assertEquals(0,uploads.get());assertEquals(0,signs.get());
    }
    @Test void uploadMismatchRetainsKnownDocumentForReconciliation() {
        badUpload=true;assertEquals(DOC,run().document());assertEquals(0,signs.get());
    }
}
