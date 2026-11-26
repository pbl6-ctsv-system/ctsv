package pbl6.ctsv.hocbong.kkht.round;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.hocbong.kkht.configuration.combination.CombinationController;
import pbl6.ctsv.hocbong.kkht.configuration.rules.RuleController;

@RestControllerAdvice(assignableTypes = {RoundController.class, CombinationController.class, RuleController.class})
@ConditionalOnProperty(name = "ctsv.foundation.enabled", havingValue = "true")
public class RoundErrors {
    @ExceptionHandler(RoundFailure.class)
    ResponseEntity<ProblemDetail> round(RoundFailure failure, HttpServletRequest request) {
        return problem(failure.status, failure.code, failure.field, request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(AuthFailure.class)
    ResponseEntity<ProblemDetail> auth(AuthFailure failure, HttpServletRequest request) {
        return problem(failure.status(), failure.code(), "request", request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(FoundationAccess.Failure.class)
    ResponseEntity<ProblemDetail> foundation(FoundationAccess.Failure failure, HttpServletRequest request) {
        return problem(failure.status(), failure.code(), failure.field(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> other(Exception failure, HttpServletRequest request) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (unavailable(cause)) {
                return problem(503, "DEPENDENCY_UNAVAILABLE", "request", request, HttpHeaders.EMPTY);
            }
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627)) {
                String conflict = uniqueConflict(sql.getMessage());
                if (conflict != null) {
                    return problem(409, conflict, "request", request, HttpHeaders.EMPTY);
                }
            }
            if (cause instanceof SQLException sql && sql.getErrorCode() == 51940) {
                return problem(409, "COMBINATION_OVERLAP", "effectiveFrom", request, HttpHeaders.EMPTY);
            }
            if (cause instanceof SQLException sql && sql.getErrorCode() == 51950) {
                return problem(409, "RULE_VERSION_IN_USE", "request", request, HttpHeaders.EMPTY);
            }
        }
        return problem(500, "INTERNAL_ERROR", "request", request, HttpHeaders.EMPTY);
    }

    private static boolean unavailable(Throwable cause) {
        if (cause instanceof SQLTimeoutException || cause instanceof QueryTimeoutException
                || cause instanceof DataAccessResourceFailureException
                || cause instanceof PessimisticLockingFailureException
                || cause instanceof CannotCreateTransactionException || cause instanceof TransactionTimedOutException
                || cause instanceof jakarta.persistence.QueryTimeoutException
                || cause instanceof jakarta.persistence.PessimisticLockException
                || cause instanceof jakarta.persistence.LockTimeoutException) {
            return true;
        }
        if (cause instanceof SQLException sql) {
            String state = sql.getSQLState();
            return sql.getErrorCode() == 1205 || sql.getErrorCode() == 1222
                    || state != null && (state.startsWith("08") || state.equals("40001")
                        || state.equals("HYT00") || state.equals("HYT01") || state.equals("S1T00"));
        }
        return false;
    }

    private static String uniqueConflict(String message) {
        if (message == null) {
            return null;
        }
        String constraint = message.toLowerCase(Locale.ROOT);
        if (constraint.contains("uq_hb_to_hop_key")) {
            return "COMBINATION_OVERLAP";
        }
        if (constraint.contains("uq_kkht_dot_xet_hoc_ky_so_dot")) {
            return "ROUND_EXISTS";
        }
        if (constraint.contains("uq_hb_anh_xa_ky_nguon_context")
                || constraint.contains("uq_hb_hoc_ky_nam_hoc_thu_tu_ky")) {
            return "SEMESTER_MAPPING_CONFLICT";
        }
        return null;
    }

    private static ResponseEntity<ProblemDetail> problem(int status, String code, String field,
            HttpServletRequest request, HttpHeaders headers) {
        var httpStatus = HttpStatus.valueOf(status);
        var problem = ProblemDetail.forStatusAndDetail(httpStatus, httpStatus.getReasonPhrase());
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", UUID.randomUUID().toString());
        if (status == 400) {
            problem.setProperty("errors", List.of(Map.of(
                    "field", field == null ? "request" : field,
                    "code", code, "message", "Dữ liệu không hợp lệ")));
        }
        return ResponseEntity.status(httpStatus).headers(headers).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    // Dispatcher method mismatches have no selected controller, so scope this advice by path.
    @RestControllerAdvice
    @ConditionalOnProperty(name = "ctsv.foundation.enabled", havingValue = "true")
    public static class MethodErrors {
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException failure,
                HttpServletRequest request) throws HttpRequestMethodNotSupportedException {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (!path.startsWith(RoundController.BASE + "/")) {
                throw failure;
            }
            return problem(405, "METHOD_NOT_ALLOWED", "request", request, failure.getHeaders());
        }
    }
}
