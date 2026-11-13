package pbl6.ctsv.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=AuthController.class)
final class AuthErrors {
    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<ProblemDetail> auth(AuthFailure error,HttpServletRequest request){
        return problem(error.status,error.code,error.retryAfter,request);
    }

    @ExceptionHandler({DataAccessException.class,TransactionException.class})
    ResponseEntity<ProblemDetail> database(RuntimeException error,HttpServletRequest request){
        boolean unavailable=error instanceof TransactionException;
        for(Throwable cause=error;cause!=null;cause=cause.getCause())
            if(cause instanceof SQLException sql && (sql.getErrorCode()==1222 || sql.getErrorCode()==1205
                || sql.getSQLState()!=null && sql.getSQLState().startsWith("08"))) unavailable=true;
        return unavailable?problem(503,"DEPENDENCY_UNAVAILABLE",0,request):problem(500,"INTERNAL_ERROR",0,request);
    }

    @ExceptionHandler(java.io.IOException.class)
    ResponseEntity<ProblemDetail> io(HttpServletRequest request){return problem(400,"INVALID_REQUEST",0,request);}

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request){return problem(500,"INTERNAL_ERROR",0,request);}

    private ResponseEntity<ProblemDetail> problem(int status,String code,long retry,HttpServletRequest request){
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),HttpStatus.valueOf(status).getReasonPhrase());
        p.setType(URI.create("about:blank"));
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("code",code);
        p.setProperty("traceId",UUID.randomUUID().toString());
        if(status==400) p.setProperty("errors",List.of(Map.of("field","request","code",code,"message","Invalid request")));
        var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if(retry>0) response.header(HttpHeaders.RETRY_AFTER,Long.toString(retry));
        return response.body(p);
    }
}
