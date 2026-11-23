package pbl6.ctsv.shared.ca;

import java.time.Instant;
import java.util.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;

/** Technical identity binding, NOT an authorization/role grant. P11 supplies approved business authority. */
public final class CaSignerBinding {
    private CaSignerBinding() {}
    /** Optional legacy metadata; no external pin is required or used as a signing gate. */
    public record Signer(String email,String name,Certificate certificate,UUID template,String expectedFingerprint) {
        public Signer(String email,String name,Certificate certificate,UUID template) {this(email,name,certificate,template,null);}
        public Signer {
            if(expectedFingerprint!=null) {
                if(!expectedFingerprint.matches("(?i)[0-9a-f]{64}"))throw new CaFailure("CA_CERTIFICATE_FINGERPRINT_INVALID");
                expectedFingerprint=expectedFingerprint.toLowerCase(Locale.ROOT);
            }
        }
    }
    public record Consent(String authorityReference,String email,UUID certificate,String inputHash,int step) {}

    public static Signer bind(String email,UUID expectedUser,UUID certificate,UUID template,
                              Certificates certs,Templates templates,Instant now) {
        email=CaDutClient.email(email);
        if(expectedUser==null || certificate==null || certs==null || templates==null
                || !email.equals(certs.email()) || !email.equals(templates.email()))throw new CaFailure("CA_BINDING_INVALID");
        var matches=certs.items().stream().filter(c->certificate.equals(c.id())).toList();
        if(matches.size()!=1)throw new CaFailure("CA_BINDING_INVALID");
        var cert=matches.getFirst();
        if(!expectedUser.equals(cert.user()) || !"active".equals(cert.status()) || !cert.valid() || !cert.hasPrivateKey()
                || cert.validFrom()==null || cert.validTo()==null || now.isBefore(cert.validFrom()) || !now.isBefore(cert.validTo())
                || cert.serialNumber()==null || cert.serialNumber().isBlank() || cert.userDisplay()==null || cert.userDisplay().isBlank())
            throw new CaFailure("CA_CERTIFICATE_NOT_READY");
        if(template!=null && templates.items().stream().filter(t->template.equals(t.id())).count()!=1)throw new CaFailure("CA_TEMPLATE_OWNER_MISMATCH");
        return new Signer(email,cert.userDisplay(),cert,template);
    }
    public static void requireDistinct(List<Signer> signers) {
        var emails=new HashSet<String>();var users=new HashSet<UUID>();var certs=new HashSet<UUID>();var serials=new HashSet<String>();
        for(var signer:signers)if(!emails.add(signer.email()) || !users.add(signer.certificate().user()) || !certs.add(signer.certificate().id()) || !serials.add(signer.certificate().serialNumber()))
            throw new CaFailure("CA_SIGNERS_NOT_DISTINCT");
    }
    public static void requireConsent(Consent consent,Signer signer,String inputHash,int step) {
        if(consent==null || consent.authorityReference()==null || consent.authorityReference().isBlank()
                || !signer.email().equals(consent.email()) || !signer.certificate().id().equals(consent.certificate())
                || !inputHash.equals(consent.inputHash()) || step!=consent.step())throw new CaFailure("CA_CONSENT_MISSING");
    }
}
