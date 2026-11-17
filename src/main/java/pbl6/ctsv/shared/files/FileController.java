package pbl6.ctsv.shared.files;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbl6.ctsv.auth.AuthAccess;

@RestController
@RequestMapping(FileController.ROOT)
@ConditionalOnProperty(name="ctsv.files.enabled",havingValue="true")
final class FileController {
    static final String ROOT="/api/ctsv/tep";
    private final AuthAccess auth;
    private final FileService files;

    FileController(AuthAccess auth, FileService files) {
        this.auth = auth;
        this.files = files;
    }

    @PostMapping("/minh-chung")
    ResponseEntity<FileMetadata.View> evidence(HttpServletRequest request) throws IOException {
        return uploaded(files.upload(principal(request),false,request));
    }

    @PostMapping("/van-ban")
    ResponseEntity<FileMetadata.View> document(HttpServletRequest request) throws IOException {
        return uploaded(files.upload(principal(request),true,request));
    }

    @GetMapping("/{idTep}/noi-dung")
    void download(@PathVariable String idTep, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var actor = principal(request);
        if (request.getQueryString()!=null || request.getContentLengthLong()>0 || request.getHeader("Transfer-Encoding")!=null
            || !idTep.matches("[1-9][0-9]{0,18}")) throw FileProblem.invalid();
        long id;
        try { id=Long.parseLong(idTep); }
        catch (NumberFormatException failure) { throw FileProblem.invalid(); }
        files.download(actor,id,(file,verified) -> {
            String name = FileService.label(file.name());
            response.setContentType("application/pdf");
            response.setContentLengthLong(file.bytes());
            response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                .filename(name,StandardCharsets.UTF_8).build().toString());
            Files.copy(verified,response.getOutputStream());
        });
    }

    private static ResponseEntity<FileMetadata.View> uploaded(FileMetadata.View view) {
        return ResponseEntity.created(URI.create(ROOT+"/"+view.id()+"/noi-dung")).body(view);
    }

    private AuthAccess.Principal principal(HttpServletRequest request) {
        var headers = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (headers.size()!=1 || !headers.getFirst().startsWith("Bearer ")) {
            throw new FileProblem(401,"UNAUTHENTICATED");
        }
        return auth.verifyAccessToken(headers.getFirst().substring(7));
    }
}
