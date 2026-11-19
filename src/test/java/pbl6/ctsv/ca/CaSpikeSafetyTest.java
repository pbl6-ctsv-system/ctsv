package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import pbl6.ctsv.shared.ca.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CaSpikeSafetyTest {
    final UUID doc=UUID.randomUUID(),cert=UUID.randomUUID();final String hash="a".repeat(64);
    Receipt receipt(UUID document) {return new Receipt(UUID.randomUUID(),document,cert,"OPAQUE","officer.e@dut.udn.vn","Actual E",hash,"b".repeat(64),Instant.now(),"valid");}
    CaSigningSequence.Event upload(){return new CaSigningSequence.Event("UPLOAD_RECEIVED",0,doc,null,hash);}
    CaSigningSequence.Event dispatch(){return new CaSigningSequence.Event("SIGN_DISPATCHED",1,doc,null,hash);}
    CaSigningSequence.Result result(Receipt r,boolean correlated){return new CaSigningSequence.Result(doc,"PARTIAL",List.of(new CaSigningSequence.StepEvidence(1,r,correlated,true,true,true,"NOT_VERIFIED")),"CA_CERTIFICATE_LINK_NOT_VERIFIED");}
    @Test void downloadUsesOnlyKnownUploadedDocumentAndCorrelatedReceipt() {
        var r=receipt(doc);assertEquals(doc,CaDutRealIT.authorizedDocument(r,upload(),dispatch(),result(r,true),hash));
    }
    @Test void mismatchedReceiptCannotAuthorizeAnotherDocumentGet() {
        var r=receipt(UUID.randomUUID());assertThrows(CaFailure.class,()->CaDutRealIT.authorizedDocument(r,upload(),dispatch(),result(r,true),hash));
    }
    @Test void missingCorrelationOrChangedInputPreventsAnyDownload() {
        var r=receipt(doc);
        assertThrows(CaFailure.class,()->CaDutRealIT.authorizedDocument(r,upload(),dispatch(),result(r,false),hash));
        assertThrows(CaFailure.class,()->CaDutRealIT.authorizedDocument(r,upload(),dispatch(),result(r,true),"0".repeat(64)));
    }
}
