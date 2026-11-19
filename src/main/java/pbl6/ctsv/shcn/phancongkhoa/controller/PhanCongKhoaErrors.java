package pbl6.ctsv.shcn.phancongkhoa.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;

@RestControllerAdvice(assignableTypes=PhanCongKhoaController.class)
public class PhanCongKhoaErrors {
    @ExceptionHandler(PhanCongKhoaFailure.class) ResponseEntity<ProblemDetail> known(PhanCongKhoaFailure e,HttpServletRequest request){return problem(e.status(),e.code(),e.errors(),request);}
    @ExceptionHandler(AuthFailure.class) ResponseEntity<ProblemDetail> auth(AuthFailure e,HttpServletRequest request){return problem(e.status(),e.code(),List.of(),request);}
    @ExceptionHandler(FoundationAccess.Failure.class) ResponseEntity<ProblemDetail> foundation(FoundationAccess.Failure e,HttpServletRequest request){return problem(e.status(),e.code(),List.of(),request);}
    @ExceptionHandler(PhanCongKhoaRepository.XungDot.class) ResponseEntity<ProblemDetail> conflict(PhanCongKhoaRepository.XungDot e,HttpServletRequest request){return problem("NOT_FOUND".equals(e.code())?404:409,e.code(),List.of(),request);}
    @ExceptionHandler(Exception.class) ResponseEntity<ProblemDetail> unexpected(Exception e,HttpServletRequest request){
        for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof SQLException sql){
            if(sql.getErrorCode()==1205||sql.getErrorCode()==1222||sql.getSQLState()!=null&&sql.getSQLState().startsWith("08"))return problem(503,"DEPENDENCY_UNAVAILABLE",List.of(),request);
            String message=Objects.toString(sql.getMessage(),"");
            if((sql.getErrorCode()==2627||sql.getErrorCode()==2601)&&message.contains("uq_shcn_danh_sach_khoa_phien_ban"))return problem(409,"SHCN_PHIEN_BAN_KHONG_HOP_LE",List.of(),request);
            if((sql.getErrorCode()==2627||sql.getErrorCode()==2601)&&message.contains("uq_shcn_phan_cong_khoa_lop"))return known(PhanCongKhoaFailure.invalid("cacPhanCong"),request);
        }
        return problem(500,"INTERNAL_ERROR",List.of(),request);
    }
    private ResponseEntity<ProblemDetail> problem(int status,String code,List<PhanCongKhoaFailure.FieldError> errors,HttpServletRequest request){
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),HttpStatus.valueOf(status).getReasonPhrase());
        p.setType(URI.create("about:blank"));p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("code",code);p.setProperty("traceId",UUID.randomUUID().toString());
        if(status==400)p.setProperty("errors",errors.isEmpty()?List.of(new PhanCongKhoaFailure.FieldError("request",code,"Invalid request")):errors);
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL,"private, no-store").contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
    }
}
