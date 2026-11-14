package pbl6.ctsv.foundation.web;
import java.net.URI;import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;import org.springframework.http.*;
import org.springframework.dao.DataAccessException;import org.springframework.transaction.TransactionException;
import pbl6.ctsv.auth.AuthFailure;
@RestControllerAdvice(assignableTypes=FoundationController.class)
class FoundationErrors {
 @ExceptionHandler(pbl6.ctsv.foundation.api.FoundationAccess.Failure.class) ResponseEntity<?> known(pbl6.ctsv.foundation.api.FoundationAccess.Failure e,HttpServletRequest r){return error(e.status(),e.code(),e.field(),r);}
 @ExceptionHandler(AuthFailure.class) ResponseEntity<?> auth(AuthFailure e,HttpServletRequest r){return error(e.status(),e.code(),"request",r);}
 @ExceptionHandler({DataAccessException.class,TransactionException.class,jakarta.persistence.PersistenceException.class})ResponseEntity<?> sql(RuntimeException e,HttpServletRequest r){
  boolean unavailable=e instanceof TransactionException;
  for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException s&&(s.getErrorCode()==1205||s.getErrorCode()==1222||s.getSQLState()!=null&&s.getSQLState().startsWith("08")))unavailable=true;
  return error(unavailable?503:500,unavailable?"DEPENDENCY_UNAVAILABLE":"INTERNAL_ERROR","request",r);
 }
 @ExceptionHandler(Exception.class)ResponseEntity<?> other(Exception e,HttpServletRequest r){return error(500,"INTERNAL_ERROR","request",r);}
 private ResponseEntity<?> error(int status,String code,String field,HttpServletRequest r){
  var p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),HttpStatus.valueOf(status).getReasonPhrase());
  p.setType(URI.create("about:blank"));p.setInstance(URI.create(r.getRequestURI()));p.setProperty("code",code);p.setProperty("traceId",UUID.randomUUID().toString());
  if(status==400)p.setProperty("errors",List.of(Map.of("field",field,"code",code,"message","Dữ liệu không hợp lệ")));
  return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
 }
}
