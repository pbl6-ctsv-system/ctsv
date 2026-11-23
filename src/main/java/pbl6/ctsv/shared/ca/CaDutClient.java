package pbl6.ctsv.shared.ca;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.function.Function;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;

/** CA DUT v1 adapter. No lifecycle/delete methods and no automatic retry of side-effecting POST. */
public final class CaDutClient implements AutoCloseable {
    private final URI origin;
    private final HttpClient http;
    private final ObjectMapper json=new ObjectMapper();
    private final String clientId, clientSecret;
    private final CaTokenManager tokens;
    private final int timeoutMs;
    public CaDutClient(URI origin,String clientId,String clientSecret,Clock clock) {
        this(origin,clientId,clientSecret,clock,30_000,false);
    }
    private CaDutClient(URI origin,String clientId,String clientSecret,Clock clock,int timeoutMs,boolean stub) {
        if(origin == null || origin.getHost() == null || origin.getUserInfo()!=null || origin.getQuery()!=null || origin.getFragment()!=null
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))
                || !("https".equals(origin.getScheme()) || stub && "http".equals(origin.getScheme()) && "127.0.0.1".equals(origin.getHost())))
            throw new CaFailure("CA_ORIGIN_INVALID");
        if(clientId == null || clientSecret == null || clientId.isBlank() || clientSecret.isBlank()) throw new CaFailure("CA_CONFIG_MISSING");
        this.origin=URI.create(origin.getScheme()+"://"+origin.getRawAuthority());this.clientId=clientId;this.clientSecret=clientSecret;this.timeoutMs=timeoutMs;
        http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).followRedirects(HttpClient.Redirect.NEVER).build();
        tokens=new CaTokenManager(this::token,clock);
    }
    /** Only plain HTTP loopback test servers; never disables HTTPS certificate validation. */
    public static CaDutClient forLoopbackStub(URI origin,String id,String secret,Clock clock,int timeoutMs) {
        if(!"127.0.0.1".equals(origin.getHost()) || !"http".equals(origin.getScheme())) throw new CaFailure("CA_STUB_ORIGIN_INVALID");
        return new CaDutClient(origin,id,secret,clock,timeoutMs,true);
    }
    private CaTokenManager.Tokens token(String refresh) {
        var payload=refresh==null ? Map.of("client_id",clientId,"client_secret",clientSecret) : Map.of("refresh",refresh);
        var reply=exchange("POST",origin.resolve(refresh==null?"/api/v1/auth/token/":"/api/v1/auth/token/refresh/"),encode(payload),"application/json",null,1_000_000,false);
        if(refresh!=null && reply.status==401)return token(null);
        if(reply.status!=200)throw new CaFailure("CA_AUTH_REJECTED",reply.status,false);
        var root=parse(reply);var data=refresh==null?data(root):root;
        return new CaTokenManager.Tokens(text(data,"access"),text(data,"refresh"));
    }
    public List<Category> categories() {
        return pages("/api/v1/documents/categories/",n->new Category(uuid(n,"id"),text(n,"code"),text(n,"name")));
    }
    public Certificates certificates(String email) {
        var normalized=email(email);
        return new Certificates(normalized,pages("/api/v1/certificates/?user_email="+query(normalized)+"&status=active",n->
                new Certificate(uuid(n,"id"),uuid(n,"user"),text(n,"user_display"),text(n,"serial_number"),text(n,"status"),instant(n,"valid_from"),instant(n,"valid_to"),bool(n,"is_valid"),bool(n,"has_private_key"))));
    }
    public Templates templates(String email) {
        var normalized=email(email);var root=data(parse(get(origin.resolve("/api/v1/signatures/user-signatures/external/?email="+query(normalized)),1_000_000)));
        if(!root.isArray() || root.size()>1000)throw new CaFailure("CA_SCHEMA_INVALID");
        var items=new ArrayList<Template>();for(var n:root)items.add(new Template(uuid(n,"id"),text(n,"name"),bool(n,"is_default")));
        return new Templates(normalized,items);
    }
    private <T> List<T> pages(String route,Function<JsonNode,T> convert) {
        var start=origin.resolve(route);var next=start;var visited=new HashSet<URI>();var result=new ArrayList<T>();
        while(next!=null) {
            if(!sameOrigin(next) || !start.getPath().equals(next.getPath()) || !sameFilter(start,next) || !visited.add(next) || visited.size()>50)
                throw new CaFailure("CA_PAGINATION_INVALID");
            var root=parse(get(next,1_000_000));var items=root.get("results");
            if(!root.path("count").canConvertToInt() || root.path("count").asInt()<0 || items==null || !items.isArray() || !root.has("next"))throw new CaFailure("CA_SCHEMA_INVALID");
            for(var n:items) { result.add(convert.apply(n));if(result.size()>1000)throw new CaFailure("CA_RESPONSE_LIMIT"); }
            var link=root.get("next");
            if(!link.isNull() && (!link.isTextual() || link.asText().isBlank()))throw new CaFailure("CA_SCHEMA_INVALID");
            try { next=link.isNull()?null:origin.resolve(link.asText()); } catch(IllegalArgumentException e){throw new CaFailure("CA_PAGINATION_INVALID");}
        }
        return List.copyOf(result);
    }
    private boolean sameFilter(URI a,URI b) {return filters(a).equals(filters(b));}
    private Map<String,String> filters(URI uri) {
        var values=new HashMap<String,String>();
        if(uri.getRawQuery()!=null) for(var part:uri.getRawQuery().split("&")) {
            var kv=part.split("=",2);var key=URLDecoder.decode(kv[0],StandardCharsets.UTF_8);
            if(!key.equals("page")) {if(values.put(key,kv.length<2?"":URLDecoder.decode(kv[1],StandardCharsets.UTF_8))!=null)throw new CaFailure("CA_PAGINATION_INVALID");}
        }
        return values;
    }
    private boolean sameOrigin(URI uri) {
        return Objects.equals(origin.getScheme(),uri.getScheme()) && Objects.equals(origin.getRawAuthority(),uri.getRawAuthority()) && uri.getFragment()==null && uri.getUserInfo()==null;
    }
    private Reply get(URI uri,int max) {
        var access=tokens.access();boolean refreshed=false;
        for(int attempt=1;attempt<=3;attempt++) {
            Reply reply;
            try {reply=exchange("GET",uri,null,null,access,max,false);}
            catch(CaFailure failure) {
                if(attempt==3 || !Set.of("CA_TIMEOUT","CA_TRANSPORT_FAILURE").contains(failure.code()))throw failure;
                backoff(attempt);continue;
            }
            if(reply.status==200)return reply;
            if(reply.status==401 && !refreshed && attempt<3) {access=tokens.afterUnauthorized(access);refreshed=true;continue;}
            var failure=error(reply,false);
            if(attempt==3 || !Set.of(500,502,503,504).contains(reply.status) || failure.code().equals("CA_STORAGE_NO_SUCH_KEY"))throw failure;
            backoff(attempt);
        }
        throw new CaFailure("CA_RETRY_EXHAUSTED");
    }
    private void backoff(int attempt) {
        try {Thread.sleep(attempt*1000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(251));}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new CaFailure("CA_INTERRUPTED");}
    }
    public byte[] download(UUID document) {
        Objects.requireNonNull(document);var reply=get(origin.resolve("/api/v1/documents/"+document+"/view-file/"),CaPdfPreflight.MAX_BYTES);
        if(!reply.type.toLowerCase(Locale.ROOT).startsWith("application/pdf"))throw new CaFailure("CA_DOWNLOAD_NOT_PDF",reply.status,false);
        return reply.body.clone();
    }
    private Reply post(String route,byte[] body,String type) {
        // Reauthentication/retry of GET is safe; a POST is dispatched exactly once.
        var reply=exchange("POST",origin.resolve(route),body,type,tokens.access(),1_000_000,true);
        if(reply.status!=201)throw error(reply,reply.status>=500 || reply.status>=200 && reply.status<300);
        return reply;
    }
    public Uploaded upload(CaPdfPreflight.Input input,String title,UUID category) {
        Objects.requireNonNull(input);Objects.requireNonNull(category);
        if(title==null || title.isBlank() || title.length()>200 || title.contains("\r") || title.contains("\n"))throw new CaFailure("CA_UPLOAD_INVALID");
        String boundary="ctsv-"+UUID.randomUUID();var out=new ByteArrayOutputStream();
        try {
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n"+title+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\n"+category+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"ctsv-test.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(input.bytes());out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }catch(IOException e){throw new CaFailure("CA_UPLOAD_INVALID");}
        var reply=post("/api/v1/documents/",out.toByteArray(),"multipart/form-data; boundary="+boundary);
        UUID knownDocument=null;
        try {
        var root=data(parse(reply));
        knownDocument=uuid(root,"id");
        if(!root.path("current_version").canConvertToInt() || root.path("current_version").asInt()<1)throw new CaFailure("CA_SCHEMA_INVALID");
        var uploaded=new Uploaded(knownDocument,hash(root,"file_hash"),uuid(root,"category"),root.get("current_version").asInt(),text(root,"status"),bool(root,"is_signed"));
        if(!uploaded.fileHash().equalsIgnoreCase(input.sha256()))throw new CaFailure("CA_UPLOAD_HASH_MISMATCH",201,true);
        if(!category.equals(uploaded.category()) || uploaded.signed())throw new CaFailure("CA_UPLOAD_RECEIPT_MISMATCH",201,true);
        return uploaded;
        }catch(CaFailure failure){throw new CaFailure(failure.code(),201,true,knownDocument);}
    }
    /** Compatibility overload: template overrides are no longer sent by CTSV. */
    public Receipt sign(UUID document,UUID certificate,String signerEmail,String signerName,UUID ignoredTemplate,CaPdfPreflight.Box box) {
        return sign(document,certificate,signerEmail,signerName,box);
    }
    public Receipt sign(UUID document,UUID certificate,String signerEmail,String signerName,CaPdfPreflight.Box box) {
        if(document==null || certificate==null || box==null || signerName==null || signerName.isBlank())throw new CaFailure("CA_SIGN_INVALID");
        var payload=new LinkedHashMap<String,Object>();payload.put("document_id",document);payload.put("certificate_id",certificate);payload.put("signer_email",email(signerEmail));payload.put("signer_name",signerName);
        payload.put("page_number",box.page());payload.put("pos_x",box.x());payload.put("pos_y",box.y());payload.put("width",box.width());payload.put("height",box.height());
        var reply=post("/api/v1/signatures/sign/",encode(payload),"application/json");
        try {
        var n=data(parse(reply));
        return new Receipt(uuid(n,"id"),uuid(n,"document"),uuid(n,"certificate"),text(n,"certificate_serial"),email(text(n,"signer_email")),text(n,"signer_name"),hash(n,"document_hash"),hash(n,"signed_pdf_hash"),instant(n,"signed_at"),text(n,"status"));
        }catch(CaFailure failure){throw new CaFailure(failure.code(),201,true);}
    }
    private record Reply(int status,String type,byte[] body) {}
    private Reply exchange(String method,URI uri,byte[] body,String type,String access,int max,boolean sideEffect) {
        if(!sameOrigin(uri))throw new CaFailure("CA_ORIGIN_INVALID");
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs)).header("Accept","application/json, application/pdf");
        if(access!=null)builder.header("Authorization","Bearer "+access);
        if(type!=null)builder.header("Content-Type",type);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
        var future=http.sendAsync(builder.build(),info->new LimitedBody(max));
        try {var r=future.get(timeoutMs,TimeUnit.MILLISECONDS);return new Reply(r.statusCode(),r.headers().firstValue("Content-Type").orElse(""),r.body());}
        catch(InterruptedException e){future.cancel(true);Thread.currentThread().interrupt();throw new CaFailure("CA_INTERRUPTED",0,sideEffect);}
        catch(TimeoutException e){future.cancel(true);throw new CaFailure("CA_TIMEOUT",0,sideEffect);}
        catch(ExecutionException e){future.cancel(true);Throwable cause=e;for(int depth=0;cause!=null&&depth<12;depth++,cause=cause.getCause())if(cause instanceof CaFailure f)throw new CaFailure(f.code(),0,sideEffect);throw new CaFailure("CA_TRANSPORT_FAILURE",0,sideEffect);}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int max;private final ByteArrayOutputStream out=new ByteArrayOutputStream();private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        LimitedBody(int max){this.max=max;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> chunks){for(var b:chunks){if(b.remaining()>max-out.size()){subscription.cancel();result.completeExceptionally(new CaFailure("CA_RESPONSE_LIMIT"));return;}byte[] part=new byte[b.remaining()];b.get(part);out.writeBytes(part);}subscription.request(1);}
        public void onError(Throwable t){result.completeExceptionally(t);}
        public void onComplete(){result.complete(out.toByteArray());}
    }
    private CaFailure error(Reply reply,boolean uncertain) {
        // Allowlisted diagnostic only; never echo arbitrary CA errors or URLs/tokens.
        var raw=new String(reply.body,StandardCharsets.UTF_8);
        return new CaFailure(raw.contains("NoSuchKey")?"CA_STORAGE_NO_SUCH_KEY":"CA_PROVIDER_REJECTED",reply.status,uncertain);
    }
    private byte[] encode(Object value){try{return json.writeValueAsBytes(value);}catch(IOException e){throw new CaFailure("CA_REQUEST_INVALID");}}
    private JsonNode parse(Reply reply){if(!reply.type.toLowerCase(Locale.ROOT).contains("json"))throw new CaFailure("CA_SCHEMA_INVALID",reply.status,false);try{return json.readTree(reply.body);}catch(IOException e){throw new CaFailure("CA_SCHEMA_INVALID",reply.status,false);}}
    private JsonNode data(JsonNode root){if(root==null || !root.path("success").isBoolean() || !root.path("success").asBoolean() || !root.hasNonNull("data"))throw new CaFailure("CA_SCHEMA_INVALID");return root.get("data");}
    private String text(JsonNode n,String key){var v=n.get(key);if(v==null || !v.isTextual() || v.asText().isBlank() || v.asText().length()>16000)throw new CaFailure("CA_SCHEMA_INVALID");return v.asText();}
    private UUID uuid(JsonNode n,String key){try{return UUID.fromString(text(n,key));}catch(IllegalArgumentException e){throw new CaFailure("CA_SCHEMA_INVALID");}}
    private Instant instant(JsonNode n,String key){try{return Instant.parse(text(n,key));}catch(java.time.format.DateTimeParseException e){throw new CaFailure("CA_SCHEMA_INVALID");}}
    private boolean bool(JsonNode n,String key){if(!n.path(key).isBoolean())throw new CaFailure("CA_SCHEMA_INVALID");return n.get(key).asBoolean();}
    private String hash(JsonNode n,String key){var h=text(n,key);if(!h.matches("(?i)[a-f0-9]{64}"))throw new CaFailure("CA_SCHEMA_INVALID");return h.toLowerCase(Locale.ROOT);}
    private static String query(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static String email(String value){if(value==null || !value.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"))throw new CaFailure("CA_IDENTITY_INVALID");return value.toLowerCase(Locale.ROOT);}
    @Override public String toString(){return "CaDutClient[credentials REDACTED]";}
    @Override public void close(){http.close();}
}
