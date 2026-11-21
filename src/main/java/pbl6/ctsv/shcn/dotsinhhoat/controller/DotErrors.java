package pbl6.ctsv.shcn.dotsinhhoat.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

@RestControllerAdvice(assignableTypes=DotController.class)
public class DotErrors {
    @ExceptionHandler(DotFailure.class) ResponseEntity<ProblemDetail> known(DotFailure e,HttpServletRequest r){return problem(e.status(),e.code(),e.errors(),r);}
    @ExceptionHandler(AuthFailure.class) ResponseEntity<ProblemDetail> auth(AuthFailure e,HttpServletRequest r){return problem(e.status(),e.code(),List.of(),r);}
    @ExceptionHandler(FoundationAccess.Failure.class) ResponseEntity<ProblemDetail> identity(FoundationAccess.Failure e,HttpServletRequest r){return problem(e.status(),e.code(),List.of(),r);}
    @ExceptionHandler(Exception.class) ResponseEntity<ProblemDetail> unexpected(Exception e,HttpServletRequest r){
        for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof SQLException sql){if(sql.getErrorCode()==1205||sql.getErrorCode()==1222||sql.getSQLState()!=null&&sql.getSQLState().startsWith("08"))return problem(503,"DEPENDENCY_UNAVAILABLE",List.of(),r);}
        return problem(500,"INTERNAL_ERROR",List.of(),r);
    }
    private ResponseEntity<ProblemDetail> problem(int status,String code,List<DotFailure.FieldError> errors,HttpServletRequest r){
        String detail=switch(code){case "VALIDATION_ERROR"->"Kiểm tra thông tin đã nhập.";case "VERSION_CONFLICT"->"Đợt đã được cập nhật. Hãy đọc bản mới để đối chiếu.";case "STATE_CONFLICT"->"Đợt không còn cho phép sửa thông tin chung.";case "DEPENDENCY_UNAVAILABLE"->"Chưa kiểm tra được dữ liệu phụ thuộc.";case "FORBIDDEN"->"Bạn chưa được cấp quyền SHCN phù hợp.";case "NOT_FOUND"->"Không tìm thấy đợt SHCN.";default->"Chưa thể hoàn tất yêu cầu.";};
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),detail);p.setType(URI.create("about:blank"));p.setInstance(URI.create(r.getRequestURI()));p.setProperty("code",code);p.setProperty("traceId",UUID.randomUUID().toString());
        if(status==400)p.setProperty("errors",errors.isEmpty()?List.of(new DotFailure.FieldError("request",code,"Yêu cầu không hợp lệ.")):errors);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL,"private, no-store").contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }
}
