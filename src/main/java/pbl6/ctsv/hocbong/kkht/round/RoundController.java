package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbl6.ctsv.auth.AuthAccess;

import static pbl6.ctsv.hocbong.kkht.round.RoundHttp.parameter;

@RestController
@RequestMapping(RoundController.BASE)
@ConditionalOnProperty(name = "ctsv.foundation.enabled", havingValue = "true")
class RoundController {
    static final String BASE = "/api/ctsv/hoc-bong";
    private static final String ROUNDS = "/kkht/dot-xet";

    private final AuthAccess auth;
    private final RoundAccess access;
    private final RoundService service;
    private final RoundHttp http;

    RoundController(AuthAccess auth, RoundAccess access, RoundService service, ObjectMapper json) {
        this.auth = auth;
        this.access = access;
        this.service = service;
        this.http = new RoundHttp(json);
    }

    @GetMapping("/toi/nhom-chuc-nang")
    ResponseEntity<RoundDtos.Groups> groups(HttpServletRequest request) {
        var body = access.execute(RoundHttp.principal(request, auth), false, actor -> {
            RoundHttp.getQuery(request);
            return new RoundDtos.Groups(actor.studentId() != null, actor.roleId() != null);
        });
        return response(HttpStatus.OK).body(body);
    }

    @GetMapping("/hoc-ky-nguon")
    ResponseEntity<RoundDtos.Page<RoundDtos.SourceSemesterDto>> sources(HttpServletRequest request) {
        var body = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            var query = RoundHttp.getQuery(request, "q", "page", "size");
            return service.sources(RoundRules.queryText(parameter(query, "q")),
                    RoundRules.page(parameter(query, "page"), parameter(query, "size")));
        });
        return response(HttpStatus.OK).body(body);
    }

    @GetMapping(ROUNDS)
    ResponseEntity<RoundDtos.Page<RoundDtos.RoundSummary>> list(HttpServletRequest request) {
        var body = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            var query = RoundHttp.getQuery(request, "semesterId", "sourceSemesterId", "number", "page", "size");
            String semester = parameter(query, "semesterId");
            String source = parameter(query, "sourceSemesterId");
            if (semester != null && source != null) {
                throw RoundFailure.invalid("sourceSemesterId");
            }
            Long semesterId = semester == null ? null : RoundRules.id("semesterId", semester);
            Long sourceId = source == null ? null : RoundRules.id("sourceSemesterId", source);
            return service.list(semesterId, sourceId, roundNumber(parameter(query, "number")),
                    RoundRules.page(parameter(query, "page"), parameter(query, "size")));
        });
        return response(HttpStatus.OK).body(body);
    }

    @PostMapping(ROUNDS)
    ResponseEntity<RoundDtos.RoundDetail> create(HttpServletRequest request) {
        var view = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            RoundHttp.ifMatch(request);
            return service.create(RoundRules.create(http.body(request)), actor);
        });
        return response(HttpStatus.CREATED)
                .location(URI.create(BASE + ROUNDS + "/" + view.body().id()))
                .eTag(view.etag())
                .body(view.body());
    }

    @GetMapping(ROUNDS + "/{idDotXet}")
    ResponseEntity<RoundDtos.RoundDetail> detail(
            @PathVariable("idDotXet") String idDotXet, HttpServletRequest request) {
        var view = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            RoundHttp.getQuery(request);
            return service.detail(RoundRules.id("idDotXet", idDotXet));
        });
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }

    @PutMapping(ROUNDS + "/{idDotXet}")
    ResponseEntity<RoundDtos.RoundDetail> update(
            @PathVariable("idDotXet") String idDotXet, HttpServletRequest request) {
        var view = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            long id = RoundRules.id("idDotXet", idDotXet);
            String ifMatch = RoundHttp.ifMatch(request);
            return service.update(id, RoundRules.update(http.body(request)), ifMatch, actor);
        });
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }

    private static Integer roundNumber(String value) {
        if (value == null) {
            return null;
        }
        long number = RoundRules.id("number", value);
        if (number > Integer.MAX_VALUE) {
            throw RoundFailure.invalid("number");
        }
        return (int) number;
    }

    private static ResponseEntity.BodyBuilder response(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON);
    }
}
