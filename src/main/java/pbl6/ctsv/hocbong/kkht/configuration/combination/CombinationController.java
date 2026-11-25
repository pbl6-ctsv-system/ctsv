package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.hocbong.kkht.round.*;

@RestController
@RequestMapping(CombinationController.BASE)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
public class CombinationController {
    public static final String BASE="/api/ctsv/hoc-bong/kkht/to-hop-dao-tao";
    private final AuthAccess auth;
    private final RoundAccess access;
    private final CombinationService service;
    private final RoundHttp http;

    CombinationController(AuthAccess auth,RoundAccess access,CombinationService service,ObjectMapper json) {
        this.auth=auth; this.access=access; this.service=service; this.http=new RoundHttp(json);
    }

    @GetMapping("/nguon")
    ResponseEntity<?> sources(HttpServletRequest request) {
        var body=access.executeConsistentRead(RoundHttp.principal(request,auth),actor -> {
            var query=RoundHttp.getQuery(request,"kind","q","page","size");
            ScholarshipCombinationSource.Kind kind;
            try { kind=ScholarshipCombinationSource.Kind.valueOf(RoundHttp.parameter(query,"kind")); }
            catch (IllegalArgumentException|NullPointerException ex) { throw RoundFailure.invalid("kind"); }
            return service.sources(kind,RoundRules.queryText(RoundHttp.parameter(query,"q")),page(query));
        });
        return response(HttpStatus.OK).body(body);
    }

    @GetMapping
    ResponseEntity<?> list(HttpServletRequest request) {
        var body=access.executeConsistentRead(RoundHttp.principal(request,auth),actor ->
                service.list(page(RoundHttp.getQuery(request,"page","size"))));
        return response(HttpStatus.OK).body(body);
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable("id") String id,HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor -> {
            RoundHttp.getQuery(request);
            return service.get(RoundRules.id("id",id));
        });
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }

    @PostMapping
    ResponseEntity<?> create(HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor -> {
            RoundHttp.ifMatch(request);
            return service.create(CombinationRules.command(http.body(request)),actor);
        });
        return response(HttpStatus.CREATED).location(URI.create(BASE+"/"+view.body().id()))
                .eTag(view.etag()).body(view.body());
    }

    @PutMapping("/{id}")
    ResponseEntity<?> update(@PathVariable("id") String id,HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor ->
                service.update(RoundRules.id("id",id),CombinationRules.command(http.body(request)),RoundHttp.ifMatch(request),actor));
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable("id") String id,HttpServletRequest request) {
        access.execute(RoundHttp.principal(request,auth),true,actor -> {
            RoundHttp.getQuery(request);
            service.delete(RoundRules.id("id",id),RoundHttp.ifMatch(request),actor);
            return null;
        });
        return response(HttpStatus.NO_CONTENT).build();
    }

    private static int[] page(java.util.Map<String,String[]> query) {
        return RoundRules.page(RoundHttp.parameter(query,"page"),RoundHttp.parameter(query,"size"));
    }
    private static ResponseEntity.BodyBuilder response(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    }
}
