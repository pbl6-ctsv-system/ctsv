package pbl6.ctsv.shcn.phancongkhoa.signing;

import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import pbl6.ctsv.shared.ca.CaDutClient;
import pbl6.ctsv.shared.ca.CaFailure;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;

/** Thin SHCN-local gateway over shared CaDutClient so signing worker can be unit-tested without mocking final classes. */
public interface PhanCongSigningGateway {
    Certificates certificates(String email);
    Uploaded upload(CaPdfPreflight.Input input, String title, UUID category);
    Receipt sign(UUID documentId, UUID certificateId, String email, String signerName, CaPdfPreflight.Box box);
    byte[] download(UUID documentId);

    static PhanCongSigningGateway fromProvider(ObjectProvider<CaDutClient> clients) {
        return new PhanCongSigningGateway() {
            private CaDutClient client() {
                CaDutClient c = clients.getIfAvailable();
                if (c == null) throw new CaFailure("CA_NOT_CONFIGURED");
                return c;
            }
            @Override public Certificates certificates(String email) { return client().certificates(email); }
            @Override public Uploaded upload(CaPdfPreflight.Input input, String title, UUID category) { return client().upload(input, title, category); }
            @Override public Receipt sign(UUID documentId, UUID certificateId, String email, String signerName, CaPdfPreflight.Box box) {
                return client().sign(documentId, certificateId, email, signerName, box);
            }
            @Override public byte[] download(UUID documentId) { return client().download(docId(documentId)); }
            private UUID docId(UUID id) { return id; }
        };
    }
}
