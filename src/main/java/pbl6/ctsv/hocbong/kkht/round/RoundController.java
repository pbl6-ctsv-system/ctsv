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
    private final ScopeService scopes;

    RoundController(AuthAccess auth, RoundAccess access, RoundService service, ObjectMapper json,ScopeService scopes) {
        this.auth = auth;
        this.access = access;
        this.service = service;
        this.http = new RoundHttp(json);
        this.scopes=scopes;
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

    @GetMapping("/hoc-ky-nguon/mac-dinh")
    ResponseEntity<RoundDtos.DefaultSemester> defaultSemester(HttpServletRequest request) {
        var body = access.executeConsistentRead(RoundHttp.principal(request, auth), actor -> {
            RoundHttp.getQuery(request);
            return service.defaultSemester();
        });
        return response(HttpStatus.OK).body(body);
    }

    @GetMapping("/hoc-ky-nguon/{sourceSemesterId}")
    ResponseEntity<RoundDtos.SourceSemesterDto> sourceById(
            @PathVariable("sourceSemesterId") String sourceSemesterId, HttpServletRequest request) {
        var body = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            RoundHttp.getQuery(request);
            return service.sourceById(RoundRules.id("sourceSemesterId", sourceSemesterId));
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
        var result = access.execute(RoundHttp.principal(request, auth), true, actor -> {
            RoundHttp.ifMatch(request);
            var command=ScopeRules.create(http.body(request));
            return scopes.create(command,RoundHttp.requestId(request),actor);
        });
        var view=result.view();
        return response(result.replayed()?HttpStatus.OK:HttpStatus.CREATED)
                .header("Idempotency-Replayed",Boolean.toString(result.replayed()))
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
            return scopes.update(id, ScopeRules.update(http.body(request)), ifMatch, actor);
        });
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }

    @GetMapping("/kkht/pham-vi-nguon")
    ResponseEntity<ScopeDtos.Catalog> scopeCatalog(HttpServletRequest request) {
        var result=access.executeConsistentRead(RoundHttp.principal(request,auth),actor->{
            var query=RoundHttp.getQuery(request,"sourceSemesterId","cohortId","majorId","q","page","size");
            String cohort=parameter(query,"cohortId"),major=parameter(query,"majorId");
            return scopes.catalog(RoundRules.id("sourceSemesterId",parameter(query,"sourceSemesterId")),
                cohort==null?null:RoundRules.id("cohortId",cohort),major==null?null:RoundRules.id("majorId",major),
                RoundRules.queryText(parameter(query,"q")),RoundRules.page(parameter(query,"page"),parameter(query,"size")));
        });
        return response(HttpStatus.OK).body(result);
    }

    @GetMapping(ROUNDS+"/{idDotXet}/pham-vi")
    ResponseEntity<ScopeDtos.View> scope(@PathVariable("idDotXet") String id,HttpServletRequest request) {
        var result=access.execute(RoundHttp.principal(request,auth),true,actor->{
            RoundHttp.getQuery(request);return scopes.scope(RoundRules.id("idDotXet",id));
        });
        return response(HttpStatus.OK).body(result);
    }

    @GetMapping("/kkht/lenh-tao-dot/{requestId}")
    ResponseEntity<ScopeDtos.CreationLookup> creationLookup(@PathVariable("requestId") String key,HttpServletRequest request) {
        var result=access.execute(RoundHttp.principal(request,auth),true,actor->{
            RoundHttp.getQuery(request);return scopes.lookup(ScopeRules.lookupId(key),actor);
        });
        return response(HttpStatus.OK).body(result);
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
