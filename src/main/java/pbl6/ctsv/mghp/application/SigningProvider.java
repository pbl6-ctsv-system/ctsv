package pbl6.ctsv.mghp.application;
import java.util.UUID;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;

/** Typed orchestration port; production implementation delegates to the existing P09 client. */
public interface SigningProvider {
 Certificates certificates(String email);
 Uploaded upload(CaPdfPreflight.Input input,String title,UUID category);
 Receipt sign(UUID document,UUID certificate,String email,String name,CaPdfPreflight.Box box);
 byte[] download(UUID document);
 default CertificateRequest renewCertificate(String email,String reason){throw new pbl6.ctsv.shared.ca.CaFailure("CA_NOT_CONFIGURED");}
 default java.util.List<CertificateRequest> certificateRequests(String email,String status){throw new pbl6.ctsv.shared.ca.CaFailure("CA_NOT_CONFIGURED");}
}
