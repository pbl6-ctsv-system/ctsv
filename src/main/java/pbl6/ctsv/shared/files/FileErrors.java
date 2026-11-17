package pbl6.ctsv.shared.files;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;

@RestControllerAdvice(assignableTypes=FileController.class)
final class FileErrors {
    @ExceptionHandler(FileProblem.class)
    ResponseEntity<ProblemDetail> file(FileProblem error,HttpServletRequest request) {
        return error(error.status,error.getMessage(),request);
    }

    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<ProblemDetail> auth(AuthFailure error,HttpServletRequest request) {
        return error(error.status(),error.code(),request);
    }

    @ExceptionHandler(FoundationAccess.Failure.class)
    ResponseEntity<ProblemDetail> access(FoundationAccess.Failure error,HttpServletRequest request) {
        return error(error.status(),error.code(),request);
    }

    @ExceptionHandler({RuntimeException.class,IOException.class})
    ResponseEntity<ProblemDetail> internal(Exception error,HttpServletRequest request) {
        return error(500,"INTERNAL_ERROR",request);
    }

    private ResponseEntity<ProblemDetail> error(int status,String code,HttpServletRequest request) {
        var http=HttpStatus.valueOf(status);
        var problem=ProblemDetail.forStatusAndDetail(http,http.getReasonPhrase());
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code",code);
        problem.setProperty("traceId",UUID.randomUUID().toString());
        if (status==400) problem.setProperty("errors",List.of(Map.of("field","request","code",code,"message",http.getReasonPhrase())));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }
}
