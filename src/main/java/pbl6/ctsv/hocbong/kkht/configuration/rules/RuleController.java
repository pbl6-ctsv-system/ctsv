package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundHttp;
import pbl6.ctsv.hocbong.kkht.round.RoundRules;

@RestController @RequestMapping(RuleController.BASE)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
public class RuleController {
    public static final String BASE="/api/ctsv/hoc-bong/kkht/phien-quy-tac";
    private final AuthAccess auth;
    private final RoundAccess access;
    private final RuleService service;
    private final RoundHttp http;
    RuleController(AuthAccess auth,RoundAccess access,RuleService service,ObjectMapper json) { this.auth=auth;this.access=access;this.service=service;this.http=new RoundHttp(json); }
    @GetMapping ResponseEntity<?> list(HttpServletRequest request) {
        var body=access.executeConsistentRead(RoundHttp.principal(request,auth),actor->{
            var query=RoundHttp.getQuery(request,"page","size");return service.list(RoundRules.page(RoundHttp.parameter(query,"page"),RoundHttp.parameter(query,"size")));
        });
        return response(HttpStatus.OK).body(body);
    }
    @GetMapping("/{id}") ResponseEntity<?> get(@PathVariable("id") String id,HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor->{RoundHttp.getQuery(request);return service.get(RoundRules.id("id",id));});
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }
    @PostMapping ResponseEntity<?> create(HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor->{RoundHttp.ifMatch(request);return service.create(RuleRules.command(http.body(request)),actor);});
        return response(HttpStatus.CREATED).location(URI.create(BASE+"/"+view.body().id())).eTag(view.etag()).body(view.body());
    }
    @PutMapping("/{id}") ResponseEntity<?> update(@PathVariable("id") String id,HttpServletRequest request) {
        var view=access.execute(RoundHttp.principal(request,auth),true,actor->service.update(RoundRules.id("id",id),RuleRules.command(http.body(request)),RoundHttp.ifMatch(request),actor));
        return response(HttpStatus.OK).eTag(view.etag()).body(view.body());
    }
    private static ResponseEntity.BodyBuilder response(HttpStatus status) { return ResponseEntity.status(status).cacheControl(CacheControl.noStore()); }
}
