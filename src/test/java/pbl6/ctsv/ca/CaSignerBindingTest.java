package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import pbl6.ctsv.shared.ca.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CaSignerBindingTest {
    static final Instant NOW=Instant.parse("2026-11-19T00:00:00Z");
    static final UUID CERT=UUID.fromString("11111111-1111-4111-8111-111111111111"), USER=UUID.fromString("22222222-2222-4222-8222-222222222222");
    static Certificate cert(String status,boolean valid,boolean key,Instant until) {return new Certificate(CERT,USER,"Actual E","AB12",status,NOW.minusSeconds(10),until,valid,key);}
    static CaSignerBinding.Signer bound() {return CaSignerBinding.bind("e@example.test",USER,CERT,null,new Certificates("e@example.test",List.of(cert("active",true,true,NOW.plusSeconds(600)))),new Templates("e@example.test",List.of()),NOW);}
    @Test void bindingUsesFilteredOwnerNotClientActorAndAllowsNoTemplate() {
        var b=bound();assertEquals("Actual E",b.name());assertEquals(CERT,b.certificate().id());assertNull(b.template());
    }
    @Test void rejectsWrongOwnerCertTemplateAndNullCertificate() {
        var cs=new Certificates("e@example.test",List.of(cert("active",true,true,NOW.plusSeconds(60))));var ts=new Templates("e@example.test",List.of());
        assertThrows(CaFailure.class,()->CaSignerBinding.bind("c@example.test",USER,CERT,null,cs,ts,NOW));
        assertThrows(CaFailure.class,()->CaSignerBinding.bind("e@example.test",UUID.randomUUID(),CERT,null,cs,ts,NOW));
        assertThrows(CaFailure.class,()->CaSignerBinding.bind("e@example.test",USER,null,null,cs,ts,NOW));
        assertThrows(CaFailure.class,()->CaSignerBinding.bind("e@example.test",USER,CERT,UUID.randomUUID(),cs,ts,NOW));
    }
    @Test void rejectsExpiredRevokedSuspendedAndUnavailableKey() {
        for(var c:List.of(cert("active",false,true,NOW.plusSeconds(60)),cert("revoked",true,true,NOW.plusSeconds(60)),cert("suspended",true,true,NOW.plusSeconds(60)),cert("active",true,false,NOW.plusSeconds(60)),cert("active",true,true,NOW)))
            assertThrows(CaFailure.class,()->CaSignerBinding.bind("e@example.test",USER,CERT,null,new Certificates("e@example.test",List.of(c)),new Templates("e@example.test",List.of()),NOW));
    }
    @Test void cannotPresentSameCertificateAsTwoPeople() {
        assertThrows(CaFailure.class,()->CaSignerBinding.requireDistinct(List.of(bound(),bound())));
    }
    @Test void consentIsBoundToInputHashStepAndOwner() throws Exception {
        var b=bound();var h=CaPdfPreflight.sha256(CaPdfPreflightTest.pdf());
        var consent=new CaSignerBinding.Consent("owner-test-20261119",b.email(),b.certificate().id(),h,1);
        CaSignerBinding.requireConsent(consent,b,h,1);
        assertThrows(CaFailure.class,()->CaSignerBinding.requireConsent(consent,b,h,2));
        assertThrows(CaFailure.class,()->CaSignerBinding.requireConsent(consent,b,"0".repeat(64),1));
        assertThrows(CaFailure.class,()->CaSignerBinding.requireConsent(null,b,h,1));
    }
}
