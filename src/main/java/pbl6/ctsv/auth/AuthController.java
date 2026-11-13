package pbl6.ctsv.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static pbl6.ctsv.auth.AuthViews.*;

@RestController
@RequestMapping(AuthController.BASE)
@ConditionalOnProperty(name="ctsv.auth.enabled",havingValue="true")
final class AuthController {
    static final String BASE="/api/xac-thuc";
    private static final String COOKIE="ctsv_refresh";
    private static final int MAX_JSON_BYTES=1024*1024;
    private final ObjectMapper input;
    private final AuthService service;
    private final AuthAdministration admin;
    private final AuthSettings settings;

    AuthController(ObjectMapper json,AuthService service,AuthAdministration admin,AuthSettings settings){
        this.input=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.service=service;
        this.admin=admin;
        this.settings=settings;
    }

    @PostMapping("/dang-nhap") ResponseEntity<TokenResponse> login(HttpServletRequest request) throws IOException {
        csrf(request);
        var body=body(request,"username","password");
        return token(service.login(text(body,"username",0),text(body,"password",0),request.getRemoteAddr()));
    }

    @PostMapping("/lam-moi-token") ResponseEntity<TokenResponse> refresh(HttpServletRequest request) throws IOException {
        csrf(request);
        empty(request);
        return token(service.refresh(cookieValue(request)));
    }

    @PostMapping("/dang-xuat") ResponseEntity<Void> logout(HttpServletRequest request) throws IOException {
        csrf(request);
        empty(request);
        var response=response(204);
        if(service.logout(cookieValue(request))) response.header(HttpHeaders.SET_COOKIE,cookie("",0));
        return response.build();
    }

    @GetMapping("/toi") ResponseEntity<AccountSelf> self(HttpServletRequest request) throws IOException {
        var principal=principal(request);
        empty(request);
        return response(200).body(service.self(principal));
    }

    @GetMapping("/noi-bo/tai-khoan/{idTaiKhoan}/trang-thai-bao-mat")
    ResponseEntity<AuthAccess.State> securityState(@PathVariable String idTaiKhoan,HttpServletRequest request) throws IOException {
        serviceCapability(request);
        empty(request);
        return response(200).body(service.state(uuid(idTaiKhoan)));
    }

    @PostMapping("/tai-khoan") ResponseEntity<AccountAdmin> create(HttpServletRequest request) throws IOException {
        var principal=principal(request);
        var b=body(request,"username","password","profileLinks");
        var result=admin.create(principal,text(b,"username",0),text(b,"password",0),links(b));
        return response(201).location(URI.create(BASE+"/tai-khoan/"+result.account().accountId()))
            .eTag(result.etag()).body(result.account());
    }

    @GetMapping("/phan-quyen") ResponseEntity<RbacView> rbac(HttpServletRequest request) throws IOException {
        var principal=principal(request);
        noBody(request);
        UUID target=null;
        if(request.getQueryString()!=null){
            if(!request.getParameterMap().keySet().equals(Set.of("accountId"))
                || request.getParameterValues("accountId").length!=1) throw AuthFailure.invalid();
            target=uuid(request.getParameter("accountId"));
        }
        return response(200).body(admin.rbac(principal,target));
    }

    @GetMapping("/tai-khoan/{idTaiKhoan}")
    ResponseEntity<AccountAdmin> read(@PathVariable String idTaiKhoan,HttpServletRequest request) throws IOException {
        var principal=principal(request);
        empty(request);
        return account(admin.read(principal,uuid(idTaiKhoan)));
    }

    @PatchMapping("/tai-khoan/{idTaiKhoan}")
    ResponseEntity<AccountAdmin> update(@PathVariable String idTaiKhoan,HttpServletRequest request) throws IOException {
        var principal=principal(request);
        return account(admin.updateLinks(principal,uuid(idTaiKhoan),header(request,"If-Match"),links(body(request,"profileLinks"))));
    }

    @PutMapping("/tai-khoan/{idTaiKhoan}/vai-tro/{idVaiTro}")
    ResponseEntity<AccountAdmin> grant(@PathVariable String idTaiKhoan,@PathVariable String idVaiTro,HttpServletRequest request) throws IOException {
        return role(idTaiKhoan,idVaiTro,request,true);
    }

    @DeleteMapping("/tai-khoan/{idTaiKhoan}/vai-tro/{idVaiTro}")
    ResponseEntity<AccountAdmin> revoke(@PathVariable String idTaiKhoan,@PathVariable String idVaiTro,HttpServletRequest request) throws IOException {
        return role(idTaiKhoan,idVaiTro,request,false);
    }

    private ResponseEntity<AccountAdmin> role(String account,String role,HttpServletRequest request,boolean grant) throws IOException {
        var principal=principal(request);
        empty(request);
        long id;
        try{
            if(!role.matches("[1-9][0-9]{0,18}")) throw AuthFailure.invalid();
            id=Long.parseLong(role);
        }catch(NumberFormatException e){throw AuthFailure.invalid();}
        return account(admin.role(principal,uuid(account),id,header(request,"If-Match"),grant));
    }

    @PutMapping("/tai-khoan/{idTaiKhoan}/trang-thai")
    ResponseEntity<AccountAdmin> status(@PathVariable String idTaiKhoan,HttpServletRequest request) throws IOException {
        var principal=principal(request);
        var b=body(request,"active","reason");
        if(!b.path("active").isBoolean()) throw AuthFailure.invalid();
        return account(admin.status(principal,uuid(idTaiKhoan),header(request,"If-Match"),b.path("active").booleanValue(),reason(b)));
    }

    @PutMapping("/tai-khoan/{idTaiKhoan}/mat-khau")
    ResponseEntity<Void> reset(@PathVariable String idTaiKhoan,HttpServletRequest request) throws IOException {
        var principal=principal(request);
        var b=body(request,"newPassword","reason");
        var result=admin.reset(principal,uuid(idTaiKhoan),header(request,"If-Match"),text(b,"newPassword",0),reason(b));
        return response(204).eTag(result.etag()).build();
    }

    private void csrf(HttpServletRequest request){
        String origin=header(request,"Origin");
        if(origin==null || !settings.origins().contains(origin) || !"1".equals(header(request,"X-CSRF-Protection")))
            throw new AuthFailure(403,"CSRF_REJECTED");
    }

    private void serviceCapability(HttpServletRequest request){
        String supplied=header(request,"X-Service-Key");
        AuthSettings.Caller matched=null;
        if(supplied!=null && supplied.length()<=4096){
            byte[] presented=supplied.getBytes(StandardCharsets.UTF_8);
            for(var caller:settings.callers().values())
                if(MessageDigest.isEqual(presented,caller.key().getBytes(StandardCharsets.UTF_8))) matched=caller;
        }
        if(matched==null) throw new AuthFailure(401,"INVALID_SERVICE_KEY");
        if(matched.capabilities()==null || !matched.capabilities().contains("auth.security-state.read"))
            throw new AuthFailure(403,"FORBIDDEN");
    }

    private AuthAccess.Principal principal(HttpServletRequest request){
        String value=header(request,"Authorization");
        if(value==null || !value.regionMatches(true,0,"Bearer ",0,7)) throw new AuthFailure(401,"UNAUTHENTICATED");
        return service.verifyAccessToken(value.substring(7));
    }

    private JsonNode body(HttpServletRequest request,String... names) throws IOException {
        noQuery(request);
        String encoding=request.getHeader("Content-Encoding");
        if(encoding!=null && !"identity".equalsIgnoreCase(encoding)) throw new AuthFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        try {
            if(request.getContentType()==null || !MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(request.getContentType())))
                throw new AuthFailure(415,"UNSUPPORTED_MEDIA_TYPE");
        }catch(InvalidMediaTypeException e){throw new AuthFailure(415,"UNSUPPORTED_MEDIA_TYPE");}
        if(request.getContentLengthLong()>MAX_JSON_BYTES) throw new AuthFailure(413,"PAYLOAD_TOO_LARGE");
        byte[] raw=request.getInputStream().readNBytes(MAX_JSON_BYTES+1);
        if(raw.length>MAX_JSON_BYTES) throw new AuthFailure(413,"PAYLOAD_TOO_LARGE");
        try{
            String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString();
            return exact(input.readTree(json),names);
        }
        catch(java.nio.charset.CharacterCodingException e){throw AuthFailure.invalid();}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw AuthFailure.invalid();}
        finally{Arrays.fill(raw,(byte)0);}
    }

    private static JsonNode exact(JsonNode node,String... names){
        if(node==null || !node.isObject() || node.size()!=names.length) throw AuthFailure.invalid();
        for(String name:names) if(!node.hasNonNull(name)) throw AuthFailure.invalid();
        return node;
    }

    private static String text(JsonNode node,String name,int max){
        var value=node.path(name);
        if(!value.isTextual() || max>0 && value.textValue().length()>max) throw AuthFailure.invalid();
        return value.textValue();
    }

    private static List<ProfileLink> links(JsonNode node){
        JsonNode array=node.path("profileLinks");
        if(!array.isArray()) throw AuthFailure.invalid();
        var values=new ArrayList<ProfileLink>();
        for(var value:array){
            exact(value,"sourceSystem","profileType","sourceId");
            String source=keyText(value,"sourceSystem"), id=keyText(value,"sourceId");
            values.add(new ProfileLink(source,text(value,"profileType",16),id));
        }
        return List.copyOf(values);
    }

    private static String keyText(JsonNode node,String field){
        String value=text(node,field,128);
        if(value.isEmpty() || value.startsWith(" ") || value.endsWith(" ")
            || value.codePoints().anyMatch(c->Character.isISOControl(c)||c>=0xD800&&c<=0xDFFF)) throw AuthFailure.invalid();
        return value;
    }

    private static String reason(JsonNode node){
        String value=text(node,"reason",4000);
        if(value.isBlank()) throw AuthFailure.invalid();
        return value;
    }

    private static UUID uuid(String value){
        try{
            UUID id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value)) throw AuthFailure.invalid();
            return id;
        }catch(IllegalArgumentException e){throw AuthFailure.invalid();}
    }

    private static void empty(HttpServletRequest r)throws IOException{noQuery(r);noBody(r);}
    private static void noQuery(HttpServletRequest r){if(r.getQueryString()!=null) throw AuthFailure.invalid();}
    private static void noBody(HttpServletRequest r)throws IOException{if(r.getContentLengthLong()>0 || r.getInputStream().read()!=-1) throw AuthFailure.invalid();}
    private static String header(HttpServletRequest r,String name){
        var values=Collections.list(r.getHeaders(name));
        return values.size()==1?values.getFirst():null;
    }

    private static String cookieValue(HttpServletRequest r){
        String value=null;
        if(r.getCookies()!=null) for(var cookie:r.getCookies()) if(COOKIE.equals(cookie.getName())){
            if(value!=null) return null;
            value=cookie.getValue();
        }
        return value;
    }

    private ResponseEntity<TokenResponse> token(TokenIssue token){
        long ttl=Math.max(0,Duration.between(service.clock.instant(),token.response().refreshExpiresAt()).getSeconds());
        return response(200).header(HttpHeaders.SET_COOKIE,cookie(token.cookie(),ttl)).body(token.response());
    }

    private static String cookie(String value,long seconds){
        return ResponseCookie.from(COOKIE,value).httpOnly(true).secure(true).sameSite("Strict").path(BASE).maxAge(seconds).build().toString();
    }

    private static ResponseEntity.BodyBuilder response(int status){return ResponseEntity.status(status).cacheControl(CacheControl.noStore());}
    private static ResponseEntity<AccountAdmin> account(AccountResult result){return response(200).eTag(result.etag()).body(result.account());}
}
