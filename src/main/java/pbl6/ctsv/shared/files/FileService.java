package pbl6.ctsv.shared.files;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.core.RequestContext;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;

final class FileService {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(FileService.class);
    private static final String SPECIALIST = "mghp_chuyen_vien";
    private final FoundationAccess access;
    private final ObjectProvider<PrivateFileStorage> storage;
    private final TransactionTemplate transaction;
    private final FileMetadata metadata;
    private final UploadLedger ledger;
    private final ObjectProvider<FileReadPolicy> policies;
    private final PdfSpool spool = new PdfSpool();

    FileService(FoundationAccess access, ObjectProvider<PrivateFileStorage> storage, TransactionTemplate transaction,
                FileMetadata metadata, UploadLedger ledger,ObjectProvider<FileReadPolicy> policies) {
        this.access = access;
        this.storage = storage;
        this.transaction = transaction;
        this.metadata = metadata;
        this.ledger = ledger;
        this.policies = policies;
    }

    FileMetadata.View upload(AuthAccess.Principal principal, boolean document, HttpServletRequest request) throws IOException {
        long owner = transaction.execute(status -> {
            var identity = access.authorize(principal,true,document ? SPECIALIST : null);
            if (!document && identity.studentId()==null) throw new FileProblem(403,"FORBIDDEN");
            return identity.userId();
        });
        if (request.getQueryString()!=null) throw FileProblem.invalid();
        try {
            if (request.getContentType()==null ||
                !MediaType.MULTIPART_FORM_DATA.includes(MediaType.parseMediaType(request.getContentType()))) {
                throw new FileProblem(415,"UNSUPPORTED_MEDIA_TYPE");
            }
        } catch (InvalidMediaTypeException failure) {
            throw new FileProblem(415,"UNSUPPORTED_MEDIA_TYPE");
        }
        try (var lease = spool.open()) {
            var input = receive(request,lease,PdfSpool.UPLOAD_LIMIT);
            int pages = PdfValidation.pages(lease.path);
            var remote = storage();
            UUID id = UUID.randomUUID();
            var attempt = new UploadLedger.Attempt(id,owner,remote.cloud(),remote.publicId("ctsv-mghp-"+id+".pdf"),
                document ? "document" : "evidence",input.bytes(),PdfSpool.sha256(lease.path));
            transaction.executeWithoutResult(status -> ledger.start(attempt));
            try {
                var asset = remote.upload(attempt.key(),lease.path,attempt.bytes());
                return transaction.execute(status -> {
                    var view = metadata.save(attempt,asset,pages,input.name());
                    ledger.complete(attempt,Long.parseLong(view.id()),asset);
                    return view;
                });
            } catch (RuntimeException failure) {
                try {
                    transaction.executeWithoutResult(status -> ledger.uncertain(attempt));
                } catch (RuntimeException ledgerFailure) {
                    LOG.warn("Upload state will be recovered from the pre-upload ledger");
                }
                LOG.warn("PDF upload awaiting reconciliation, attempt {}",id);
                throw failure;
            }
        }
    }

    @FunctionalInterface interface PdfResponse {
        void write(FileMetadata.Stored file, java.nio.file.Path verified) throws IOException;
    }

    void download(AuthAccess.Principal principal, long id, PdfResponse response) throws IOException {
        var file = transaction.execute(status -> {
            var identity = access.identify(principal,true);
            var entry = metadata.get(id).orElseThrow(FileProblem::notFound);
            var links = metadata.links(id,identity.studentId());
            if (!policies.orderedStream().anyMatch(policy -> policy.canRead(id,identity))) {
                // Keep the established evidence/template gate when no module grants this object.
                access.authorize(principal,false,null);
                if (!canRead(entry,links,identity)) throw FileProblem.notFound();
            }
            if (!"available".equals(entry.state()) || entry.deleted()) throw new FileProblem(409,"FILE_NOT_READY");
            return entry;
        });
        var remote = storage();
        if (!remote.cloud().equals(file.cloud()) || !"raw".equals(file.resource()) ||
            !"authenticated".equals(file.delivery()) || file.asset()==null || file.bytes()<1 ||
            file.bytes()>PdfSpool.DOWNLOAD_LIMIT || file.hash()==null || !file.hash().matches("[a-f0-9]{64}")) {
            throw FileProblem.integrity();
        }
        try (var lease = spool.open()) {
            remote.download(file.asset(),lease.path,file.bytes());
            if (Files.size(lease.path)!=file.bytes() || !PdfSpool.sha256(lease.path).equals(file.hash())) {
                throw FileProblem.integrity();
            }
            response.write(file,lease.path);
        }
    }

    private boolean canRead(FileMetadata.Stored entry, FileMetadata.Links links, FoundationAccess.Identity identity) {
        if (!links.bound()) return Objects.equals(entry.owner(),identity.userId());
        if (links.ownApplication() || links.publicTemplate() && identity.studentId()!=null) return true;
        if (!links.application() && !links.template()) return false;
        if (identity.studentId()==null) return true; // Already authorized as an MGHP specialist by FoundationAccess.
        try {
            // The online token-state check above also covers dual-role accounts.
            access.authorize(identity.principal(),false,SPECIALIST);
            return true;
        } catch (FoundationAccess.Failure failure) {
            if (failure.status()==403) return false;
            throw failure;
        }
    }

    void reconcile() {
        var remote = storage.getIfAvailable();
        if (remote==null) return;
        for (var candidate : ledger.due(remote.cloud())) {
            try {
                ledger.reconcile(candidate,remote.find(candidate.key()));
            } catch (FileProblem failure) {
                ledger.defer(candidate);
                LOG.warn("Private storage reconciliation deferred");
                return;
            }
        }
    }

    private PrivateFileStorage storage() {
        var remote = storage.getIfAvailable();
        if (remote==null) throw FileProblem.unavailable();
        return remote;
    }

    private record Input(String name, long bytes) {}

    private static Input receive(HttpServletRequest request, PdfSpool.Lease lease, int limit) throws IOException {
        var parser = new JakartaServletFileUpload<>();
        parser.setHeaderCharset(StandardCharsets.UTF_8);
        parser.setMaxSize(limit+PdfSpool.OVERHEAD);
        parser.setMaxFileSize(limit);
        parser.setMaxPartHeaderSize(PdfSpool.OVERHEAD);
        try {
            long ceiling=limit+PdfSpool.OVERHEAD;
            if (request.getContentLengthLong()>ceiling) throw FileProblem.tooLarge();
            var raw = new org.apache.commons.io.input.BoundedInputStream.Builder()
                .setInputStream(request.getInputStream()).setMaxCount(ceiling+1).setPropagateClose(false).get();
            var parts = parser.getItemIterator(new RequestContext() {
                @Override public String getCharacterEncoding() { return StandardCharsets.UTF_8.name(); }
                @Override public long getContentLength() { return request.getContentLengthLong(); }
                @Override public String getContentType() { return request.getContentType(); }
                @Override public InputStream getInputStream() { return raw; }
                @Override public boolean isMultipartRelated() { return false; }
            });
            if (!parts.hasNext()) throw FileProblem.invalid();
            var part = parts.next();
            if (part.isFormField() || !"file".equals(part.getFieldName())) throw FileProblem.invalid();
            var name = label(part.getName());
            long bytes;
            try (var stream = part.getInputStream()) {
                bytes = PdfSpool.copy(stream,lease.path,limit,FileProblem.tooLarge());
            }
            if (parts.hasNext()) throw FileProblem.invalid();
            // Multipart parsers may leave the epilogue unread. Count it without storing it.
            raw.transferTo(java.io.OutputStream.nullOutputStream());
            if (raw.getCount()>ceiling) throw FileProblem.tooLarge();
            return new Input(name,bytes);
        } catch (FileUploadSizeException failure) {
            throw FileProblem.tooLarge();
        } catch (FileUploadException | IllegalArgumentException failure) {
            throw FileProblem.invalid();
        }
    }

    static String label(String input) {
        if (input==null) throw FileProblem.invalid();
        String label = input.substring(Math.max(input.lastIndexOf('/'),input.lastIndexOf((char) 92))+1)
            .replaceAll("[\\p{Cc}\\p{Cf}\\p{Cs}]","").strip();
        if (label.isEmpty() || label.length()>512 || ".".equals(label) || "..".equals(label)) throw FileProblem.invalid();
        return label;
    }
}
