package pbl6.ctsv.shcn.phancongkhoa.signing;

import java.util.Set;
import java.util.UUID;
import org.springframework.core.env.Environment;
import pbl6.ctsv.shared.ca.CaDutClient;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.NguoiDungKhoa;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;

/**
 * Deployment-owned CA signing configuration for SHCN TASK-TK-02 (loaded via shcn.settings.json in DEV).
 * In TEST mode, uses the configured CA test certificate email without granting local CTSV permissions.
 */
public final class PhanCongSigningOptions {
    public record ResolvedPlan(String mode, long providerId, UUID categoryId, String signerEmail, String signerDisplayName) {}

    private final Environment env;

    public PhanCongSigningOptions(Environment env) {
        this.env = env;
    }

    public ResolvedPlan resolve(NguoiDungKhoa actorProfile) {
        String enabled = env.getProperty("ctsv.shcn.signing.enabled");
        if (!"true".equalsIgnoreCase(enabled == null ? "" : enabled.strip())) {
            throw new PhanCongKhoaFailure(503, "SIGNING_CONFIGURATION_REQUIRED");
        }
        String mode = required("mode").toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("TEST", "PRODUCTION").contains(mode)) {
            throw new PhanCongKhoaFailure(503, "SIGNING_CONFIGURATION_REQUIRED");
        }
        try {
            long providerId = Long.parseLong(required("provider-id"));
            if (providerId < 1) throw new IllegalArgumentException("provider-id");
            UUID categoryId = UUID.fromString(required("category-id"));
            String rawEmail = "TEST".equals(mode)
                    ? required("test-signer-email")
                    : (actorProfile == null ? null : actorProfile.email());
            if (rawEmail == null || rawEmail.isBlank()) {
                throw new PhanCongKhoaFailure(503, "SIGNING_CONFIGURATION_REQUIRED");
            }
            String email = CaDutClient.email(rawEmail.strip());
            String displayName = actorProfile != null && actorProfile.hoTen() != null && !actorProfile.hoTen().isBlank()
                    ? actorProfile.hoTen().strip()
                    : email;
            return new ResolvedPlan(mode, providerId, categoryId, email, displayName);
        } catch (PhanCongKhoaFailure e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PhanCongKhoaFailure(503, "SIGNING_CONFIGURATION_REQUIRED");
        }
    }

    private String required(String key) {
        String value = env.getProperty("ctsv.shcn.signing." + key);
        if (value == null || value.isBlank()) {
            throw new PhanCongKhoaFailure(503, "SIGNING_CONFIGURATION_REQUIRED");
        }
        return value.strip();
    }
}
