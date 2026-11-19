package pbl6.ctsv.shared.ca;

import java.time.Instant;
import java.util.*;

/** Whitelisted fields only. No access token, delivery URL or raw provider body in public DTOs. */
public final class CaProviderDtos {
    private CaProviderDtos() {}
    public record Category(UUID id, String code, String name) {}
    public record Certificate(UUID id, UUID user, String userDisplay, String serialNumber,
                              String status, Instant validFrom, Instant validTo, boolean valid, boolean hasPrivateKey) {}
    public record Template(UUID id, String name, boolean isDefault) {}
    public record Certificates(String email, List<Certificate> items) { public Certificates {items=List.copyOf(items);} }
    public record Templates(String email, List<Template> items) { public Templates {items=List.copyOf(items);} }
    public record Uploaded(UUID id,String fileHash,UUID category,int currentVersion,String status,boolean signed) {}
    public record Receipt(UUID id, UUID document, UUID certificate, String certificateSerial,
                          String signerEmail, String signerName, String documentHash, String signedPdfHash,
                          Instant signedAt, String status) {}
}
