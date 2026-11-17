package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.invalid;

@RestController
@RequestMapping("/api/ctsv")
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class ApplicationController {
    private final AuthAccess auth;
    private final FoundationAccess access;
    private final ApplicationReads reads;
    private final ApplicationService writes;
    private final InboxService inbox;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    ApplicationController(AuthAccess auth,FoundationAccess access,ApplicationReads reads,ApplicationService writes,
                          InboxService inbox,PlatformTransactionManager manager,ObjectMapper json) {
        this.auth=auth; this.access=access; this.reads=reads; this.writes=writes; this.inbox=inbox;
        tx=new TransactionTemplate(manager);
        this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @GetMapping("/mghp/toi/ngu-canh-ke-khai")
    ResponseEntity<?> form(HttpServletRequest request) {
        var principal=principal(request);
        query(request,"dot_xet_id");
        var round=id(request.getParameter("dot_xet_id"),"dot_xet_id");
        return ok(tx.execute(s -> reads.form(student(principal,true).studentId(),round)));
    }

    @GetMapping("/mghp/ho-so")
    ResponseEntity<?> list(HttpServletRequest request) {
        var principal=principal(request);
        query(request,"roundId","status","page","size");
        String raw=request.getParameter("roundId");
        Long round=raw==null?null:id(raw,"roundId");
        String state=status(request.getParameter("status"));
        int page=page(request.getParameter("page"),"page",0,0,Integer.MAX_VALUE);
        int size=page(request.getParameter("size"),"size",20,1,100);
        boolean staff=tx.execute(s -> access.authorize(principal,false,null).studentId()==null);
        return ok(tx.execute(s -> reads.list(access.authorize(principal,staff,staff?"mghp_chuyen_vien":null),round,state,page,size)));
    }

    @GetMapping("/mghp/ho-so/{idHoSo}")
    ResponseEntity<?> detail(@PathVariable String idHoSo,HttpServletRequest request) {
        var principal=principal(request);
        query(request);
        var detail=tx.execute(s -> reads.detail(reads.scoped(access.authorize(principal,true,null),id(idHoSo,"idHoSo"))));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(detail.tag()).body(detail.body());
    }

    @GetMapping("/mghp/ho-so/{idHoSo}/phien-ban/{idPhienBan}")
    ResponseEntity<?> declaration(@PathVariable String idHoSo,@PathVariable String idPhienBan,HttpServletRequest request) {
        var principal=principal(request);
        query(request);
        return ok(tx.execute(s -> reads.declaration(reads.scoped(access.authorize(principal,true,null),
            id(idHoSo,"idHoSo")),id(idPhienBan,"idPhienBan"))));
    }

    @PostMapping("/mghp/ho-so")
    ResponseEntity<?> initial(HttpServletRequest request) throws IOException { return submit(request,null); }

    @PostMapping("/mghp/ho-so/{idHoSo}/ban-nop")
    ResponseEntity<?> resubmit(@PathVariable String idHoSo,HttpServletRequest request) throws IOException {
        return submit(request,id(idHoSo,"idHoSo"));
    }

    private ResponseEntity<?> submit(HttpServletRequest request,Long id) throws IOException {
        var received=writes.received();
        var principal=principal(request);
        var body=body(request);
        var result=tx.execute(s->writes.submit(student(principal,false),id,body,request.getHeader("If-Match"),received));
        var reply=ResponseEntity.status(result.replay()?200:201).cacheControl(CacheControl.noStore());
        if(!result.replay())reply.location(URI.create("/api/ctsv/mghp/ho-so/"+result.root().id+"/phien-ban/"+result.version().id));
        return reply.body(IntakeMapping.acknowledgement(result.root(),result.version()));
    }

    @PostMapping("/mghp/ho-so/{idHoSo}/rut")
    ResponseEntity<?> withdraw(@PathVariable String idHoSo,HttpServletRequest request) throws IOException {
        return transition(request,idHoSo,false);
    }

    @PostMapping("/mghp/ho-so/{idHoSo}/mo-lai")
    ResponseEntity<?> reopen(@PathVariable String idHoSo,HttpServletRequest request) throws IOException {
        return transition(request,idHoSo,true);
    }

    private ResponseEntity<?> transition(HttpServletRequest request,String id,boolean reopen) throws IOException {
        var received=writes.received();
        var principal=principal(request);
        var input=shape(body(request),List.of("reason"));
        String reason=text(input.get("reason"),"reason",4000,true);
        return ok(tx.execute(s -> writes.transition(reopen?access.authorize(principal,true,"mghp_chuyen_vien"):student(principal,false),
            id(id,"idHoSo"),request.getHeader("If-Match"),reason,reopen,received)));
    }

    @GetMapping("/toi/thong-bao")
    ResponseEntity<?> inbox(HttpServletRequest request) {
        var principal=principal(request);
        query(request,"unreadOnly","page","size");
        String raw=request.getParameter("unreadOnly");
        if(raw!=null && !"true".equals(raw) && !"false".equals(raw))throw invalid("unreadOnly");
        int page=page(request.getParameter("page"),"page",0,0,Integer.MAX_VALUE);
        int size=page(request.getParameter("size"),"size",20,1,100);
        return ok(tx.execute(s -> inbox.list(access.identify(principal,false).userId(),Boolean.parseBoolean(raw),page,size)));
    }

    @PutMapping("/toi/thong-bao/{idThongBao}/da-doc")
    ResponseEntity<?> markRead(@PathVariable String idThongBao,HttpServletRequest request) {
        var principal=principal(request);
        query(request);
        return ok(tx.execute(s -> inbox.read(access.identify(principal,true).userId(),id(idThongBao,"idThongBao"))));
    }

    private FoundationAccess.Identity student(AuthAccess.Principal principal,boolean form) {
        FoundationAccess.Identity identity;
        try {
            identity=access.identify(principal,true);
        } catch(FoundationAccess.Failure failure) {
            if(form && failure.status()==403)throw IntakeProblem.conflict("IDENTITY_MAPPING_REQUIRED");
            throw failure;
        }
        if(identity.studentId()==null) {
            if(form)throw IntakeProblem.conflict("IDENTITY_MAPPING_REQUIRED");
            throw new IntakeProblem(403,"FORBIDDEN","request");
        }
        return identity;
    }

    private AuthAccess.Principal principal(HttpServletRequest request) {
        var headers=Collections.list(request.getHeaders("Authorization"));
        if(headers.size()!=1 || !headers.getFirst().startsWith("Bearer "))
            throw new IntakeProblem(401,"UNAUTHENTICATED","request");
        return auth.verifyAccessToken(headers.getFirst().substring(7));
    }

    private void query(HttpServletRequest request,String... allowed) {
        var keys=Set.of(allowed);
        for(var parameter:request.getParameterMap().entrySet()) {
            if(!keys.contains(parameter.getKey())||parameter.getValue().length!=1)throw invalid("query");
        }
        if(request.getContentLengthLong()>0 || request.getHeader("Transfer-Encoding")!=null)throw invalid("body");
    }

    private JsonNode body(HttpServletRequest request) throws IOException {
        if(request.getQueryString()!=null)throw invalid("query");
        try {
            if(request.getContentType()==null || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(request.getContentType())))
                throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
        } catch(InvalidMediaTypeException failure) {throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
        String encoding=request.getHeader("Content-Encoding");
        if(encoding!=null && !"identity".equalsIgnoreCase(encoding))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
        byte[] bytes=request.getInputStream().readNBytes(1_048_577);
        if(bytes.length>1_048_576)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
        try {
            return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString());
        } catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException failure){
            throw invalid("body");
        }
    }

    private ResponseEntity<?> ok(Object value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
