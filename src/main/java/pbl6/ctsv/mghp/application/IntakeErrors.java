package pbl6.ctsv.mghp.application;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;

@RestControllerAdvice(assignableTypes=ApplicationController.class)
class IntakeErrors {
    @ExceptionHandler(IntakeProblem.class)
    ResponseEntity<?> problem(IntakeProblem error,HttpServletRequest req) {
        return response(error.status,error.getMessage(),error.field,req);
    }
    @ExceptionHandler(FoundationAccess.Failure.class)
    ResponseEntity<?> scope(FoundationAccess.Failure error,HttpServletRequest req) {
        return response(error.status(),error.code(),error.field(),req);
    }
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<?> auth(AuthFailure error,HttpServletRequest req) {
        return response(error.status(),error.code(),"request",req);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> internal(Exception error,HttpServletRequest req) {
        var location=Arrays.stream(error.getStackTrace()).filter(frame -> frame.getClassName().startsWith("pbl6.ctsv."))
            .findFirst().map(StackTraceElement::toString).orElse("framework");
        org.slf4j.LoggerFactory.getLogger(IntakeErrors.class).warn("Intake request failed ({}) at {}",error.getClass().getSimpleName(),location);
        int status=500;
        String code="INTERNAL_ERROR";
        if(error instanceof org.springframework.transaction.CannotCreateTransactionException){status=503;code="DEPENDENCY_UNAVAILABLE";}
        for(Throwable t=error;t!=null;t=t.getCause()) if(t instanceof java.sql.SQLException e) {
            if(e.getErrorCode()==1205||e.getErrorCode()==1222||e.getSQLState()!=null&&e.getSQLState().startsWith("08")){
                status=503;code="DEPENDENCY_UNAVAILABLE";
            }
        }
        return response(status,code,"request",req);
    }
    private ResponseEntity<?> response(int status,String code,String field,HttpServletRequest req) {
        var http=HttpStatus.valueOf(status);
        var p=ProblemDetail.forStatusAndDetail(http,http.getReasonPhrase());
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("code",code);
        p.setProperty("traceId",UUID.randomUUID().toString());
        if(status==400)p.setProperty("errors",List.of(Map.of("field",field==null?"request":field,
            "code",code,"message",http.getReasonPhrase())));
        return ResponseEntity.status(http).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(p);
    }
}
