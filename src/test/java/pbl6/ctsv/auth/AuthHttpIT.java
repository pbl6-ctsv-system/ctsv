package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.time.Duration;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,AuthHttpIT.TestDataSource.class},
    properties={"ctsv.auth.enabled=true","server.address=127.0.0.1","server.forward-headers-strategy=none"})
@ActiveProfiles({"local","test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthHttpIT {
    static final String BASE="/api/xac-thuc";
    static final String ORIGIN="https://web.mghp.test";
    static final String PASSWORD="Synthetic Auth "+UUID.randomUUID();
    static final String KEY_READ=UUID.randomUUID().toString(),KEY_DENIED=UUID.randomUUID().toString();
    static final ObjectMapper JSON=new ObjectMapper();
    static final String ADMIN="p03.admin."+UUID.randomUUID();
    static SSLContext testTls;
    static SSLContext proxyTls;
    static final java.util.concurrent.atomic.AtomicReference<String> SQL_FAULT=new java.util.concurrent.atomic.AtomicReference<>();
    static final class FaultJdbc extends JdbcTemplate {
        FaultJdbc(DataSource source){super(source);}
        @Override public int update(String sql,Object... args){
            int rows=super.update(sql,args);
            String fault=SQL_FAULT.get();
            if(fault!=null && sql.contains(fault)) super.update("THROW 51561,N'P03 synthetic SQL fault',1;");
            return rows;
        }
    }
    @LocalServerPort int port;
    @Autowired AuthAdministration admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @Autowired AuthAccess access;
    String adminToken;

    @TestConfiguration(proxyBeanMethods=false)
    static class TestDataSource {
        @Bean DataSource testDataSource() throws Exception{return AuthTestDatabase.migrate();}
        @Bean FaultJdbc testJdbcTemplate(DataSource source){return new FaultJdbc(source);}
    }

    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r)throws Exception {
        Path keyStore=Files.createTempDirectory("p03-tls-test-").resolve("test.p12");
        keyStore.getParent().toFile().deleteOnExit();
        keyStore.toFile().deleteOnExit();
        String storePassword=UUID.randomUUID().toString();
        var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString(),
            "-genkeypair","-keyalg","RSA","-keysize","2048","-alias","auth-test","-dname","CN=127.0.0.1",
            "-ext","SAN=ip:127.0.0.1","-validity","2","-storetype","PKCS12","-keystore",keyStore.toString(),
            "-storepass:env","P03_SYNTHETIC_TLS_PASSWORD","-noprompt");
        command.environment().put("P03_SYNTHETIC_TLS_PASSWORD",storePassword);
        var child=command.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){
            child.destroyForcibly();
            throw new IllegalStateException("Test TLS certificate generation timed out");
        }
        if(child.exitValue()!=0) throw new IllegalStateException("Test TLS certificate generation failed");
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(keyStore)){store.load(input,storePassword.toCharArray());}
        KeyStore trust=KeyStore.getInstance("PKCS12");
        trust.load(null,null);
        trust.setCertificateEntry("auth-test",store.getCertificate("auth-test"));
        var trustFactory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trust);
        testTls=SSLContext.getInstance("TLS");
        testTls.init(null,trustFactory.getTrustManagers(),null);
        var keyFactory=javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        keyFactory.init(store,storePassword.toCharArray());
        proxyTls=SSLContext.getInstance("TLS");
        proxyTls.init(keyFactory.getKeyManagers(),null,null);
        r.add("server.ssl.enabled",()->true);
        r.add("server.ssl.key-store",()->keyStore.toUri().toString());
        r.add("server.ssl.key-store-type",()->"PKCS12");
        r.add("server.ssl.key-store-password",()->storePassword);
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var key=gen.generateKeyPair();
        Path privateKey=pem("PRIVATE KEY",key.getPrivate().getEncoded()),publicKey=pem("PUBLIC KEY",key.getPublic().getEncoded());
        r.add("ctsv.auth.issuer",()->"https://auth.ctsv.test");
        r.add("ctsv.auth.audience",()->"ctsv");
        r.add("ctsv.auth.signing-kid",()->"test-current");
        r.add("ctsv.auth.private-key",privateKey::toString);
        r.add("ctsv.auth.public-keys[test-current]",publicKey::toString);
        r.add("ctsv.auth.origins[0]",()->ORIGIN);
        r.add("ctsv.auth.trusted-sources[0]",()->"SIS_MOCK");
        r.add("ctsv.auth.callers[ctsv].key",()->KEY_READ);
        r.add("ctsv.auth.callers[ctsv].capabilities[0]",()->"auth.security-state.read");
        r.add("ctsv.auth.callers[other].key",()->KEY_DENIED);
    }

    static Path pem(String label,byte[] data)throws Exception {
        Path path=Files.createTempFile("p03-synthetic-auth-", ".pem");
        path.toFile().deleteOnExit();
        Files.writeString(path,"-----BEGIN "+label+"-----\n"+Base64.getEncoder().encodeToString(data)+"\n-----END "+label+"-----");
        return path;
    }

    @BeforeAll void actor()throws Exception {
        admin.bootstrap(ADMIN,PASSWORD);
        String username=jdbc.queryForObject("""
            SELECT TOP(1) a.TenDangNhap FROM dbo.TaiKhoan a JOIN dbo.TaiKhoan_VaiTro ar ON ar.TaiKhoanID=a.ID
            JOIN dbo.VaiTro r ON r.ID=ar.VaiTroID WHERE r.MaVaiTro=N'AUTH_ADMIN' ORDER BY a.ThoiDiemTao
            """,String.class);
        // Test account from a previous Maven process uses a different password. Generate an isolated test admin.
        if(!ADMIN.equals(username)){
            UUID id=UUID.randomUUID();
            var login=AuthCredentials.username(ADMIN);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan WHERE TenDangNhapChuanHoa=?",Integer.class,login.canonical())==0){
                jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",
                    id.toString(),login.display(),login.canonical(),new AuthCredentials().hash(PASSWORD));
                jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",id.toString());
            }
        }
        adminToken=login(ADMIN,PASSWORD).json().path("accessToken").asText();
    }

    @BeforeEach void isolateLimiterFixture(){
        // Each specification case starts with independent limiter state (AU-N14).
        // Exercise the real limiter; never mock or relax its production thresholds.
        var limiter=context.getBean(AuthLoginLimiter.class);
        synchronized(limiter){
            for(String field:List.of("ips","pairs"))
                ((Map<?,?>)Objects.requireNonNull(org.springframework.test.util.ReflectionTestUtils.getField(limiter,field))).clear();
        }
    }

    record Reply(int status,HttpHeaders headers,String body) {
        JsonNode json()throws Exception{return JSON.readTree(body);}
        String tag(){return headers.firstValue("ETag").orElseThrow();}
        String cookie(){return headers.firstValue("Set-Cookie").map(s->s.split(";",2)[0]).orElseThrow();}
        void expect(int wanted){assertThat(status).as("HTTP response status").isEqualTo(wanted);}
        void error(int wanted,String code)throws Exception{
            expect(wanted);
            assertThat(json().path("code").asText()).isEqualTo(code);
            assertThat(json().path("traceId").asText()).isNotBlank();
            assertThat(headers.firstValue("Cache-Control")).contains("no-store");
            assertThat(headers.firstValue("Set-Cookie")).isEmpty();
        }
    }

    Reply send(String method,String path,Object body,String bearer,String tag,String cookie,Map<String,String> headers)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+BASE+path)).timeout(Duration.ofSeconds(15));
        headers.forEach(request::header);
        if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        if(tag!=null) request.header("If-Match",tag);
        if(cookie!=null) request.header("Cookie",cookie);
        if(body!=null) request.header("Content-Type","application/json");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        try(var client=HttpClient.newBuilder().sslContext(testTls).build()){
            var reply=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            return new Reply(reply.statusCode(),reply.headers(),reply.body());
        }
    }
    Map<String,String> csrf(){return Map.of("Origin",ORIGIN,"X-CSRF-Protection","1");}
    Reply login(String user,String password)throws Exception{
        var response=send("POST","/dang-nhap",Map.of("username",user,"password",password),null,null,null,csrf());
        response.expect(200);
        assertThat(response.json().path("expiresIn").asInt()).isEqualTo(900);
        assertThat(response.headers.firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers.firstValue("Set-Cookie").orElseThrow())
            .contains("HttpOnly","Secure","SameSite=Strict","Path=/api/xac-thuc").doesNotContain("Domain=");
        assertThat(response.headers.firstValue("Set-Cookie").orElseThrow()).doesNotContain("JSESSIONID");
        return response;
    }
    record User(String name,String id,String tag){}
    User user(List<Map<String,String>> links)throws Exception {
        String name="sv."+UUID.randomUUID();
        var response=send("POST","/tai-khoan",Map.of("username",name,"password",PASSWORD,"profileLinks",links),adminToken,null,null,Map.of());
        response.expect(201);
        String id=response.json().path("accountId").asText();
        assertThat(response.headers.firstValue("Location")).contains(BASE+"/tai-khoan/"+id);
        return new User(name,id,response.tag());
    }
    Reply state(User user,String key)throws Exception {
        return send("GET","/noi-bo/tai-khoan/"+user.id+"/trang-thai-bao-mat",null,null,null,null,key==null?Map.of():Map.of("X-Service-Key",key));
    }
    int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}

    @Test void sharedDatasourceLoginSelfStateAndServiceAuthorization()throws Exception{
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
        assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class);
        var user=user(List.of());
        var login=login(user.name,PASSWORD);
        String jwt=login.json().path("accessToken").asText();
        assertThat(login.body).doesNotContain(login.cookie().split("=",2)[1]);
        var me=send("GET","/toi",null,jwt,null,null,Map.of());me.expect(200);
        assertThat(me.json().path("accountId").asText()).isEqualTo(user.id);
        assertThat(me.json().has("profileLinks")).isFalse();
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND DATALENGTH(TokenHash)=32",user.id)).isEqualTo(1);
        state(user,null).error(401,"INVALID_SERVICE_KEY");
        state(user,KEY_DENIED).error(403,"FORBIDDEN");
        send("GET","/noi-bo/tai-khoan/not-a-uuid/trang-thai-bao-mat",null,null,null,null,Map.of()).error(401,"INVALID_SERVICE_KEY");
        send("GET","/noi-bo/tai-khoan/not-a-uuid/trang-thai-bao-mat",null,null,null,null,Map.of("X-Service-Key",KEY_DENIED)).error(403,"FORBIDDEN");
        var current=state(user,KEY_READ);current.expect(200);
        assertThat(current.json().path("tokenVersion").textValue()).isEqualTo("0");
        assertThat(current.json().path("ctsvAccess").asBoolean()).isFalse();
        assertThatThrownBy(()->access.requireCtsvAccess(access.verifyAccessToken(jwt),UUID.randomUUID()))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(503));
        send("GET","/toi",null,"invalid",null,null,Map.of()).error(401,"UNAUTHENTICATED");
        send("GET","/phan-quyen",null,jwt,null,null,Map.of()).error(403,"FORBIDDEN");
        send("GET","/noi-bo/tai-khoan/"+UUID.randomUUID()+"/trang-thai-bao-mat",null,null,null,null,Map.of("X-Service-Key",KEY_READ)).error(404,"NOT_FOUND");
    }

    @Test void rotationPreservesAbsoluteDeadlineAndReuseRevokesOnlyItsAccount()throws Exception{
        var a=user(List.of());var b=user(List.of());
        var r0=login(a.name,PASSWORD);var device=login(a.name,PASSWORD);var other=login(b.name,PASSWORD);
        var r1=send("POST","/lam-moi-token",null,null,null,r0.cookie(),csrf());r1.expect(200);
        assertThat(r1.json().path("refreshExpiresAt")).isEqualTo(r0.json().path("refreshExpiresAt"));
        assertThat(r1.cookie()).isNotEqualTo(r0.cookie());
        var r2=send("POST","/lam-moi-token",null,null,null,r1.cookie(),csrf());r2.expect(200);
        assertThat(r2.json().path("refreshExpiresAt")).isEqualTo(r0.json().path("refreshExpiresAt"));
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",a.id)).isEqualTo(4);
        send("POST","/lam-moi-token",null,null,null,r0.cookie(),csrf()).error(401,"REFRESH_REUSED");
        send("POST","/lam-moi-token",null,null,null,r1.cookie(),csrf()).error(401,"REFRESH_INVALID");
        send("POST","/lam-moi-token",null,null,null,device.cookie(),csrf()).error(401,"REFRESH_INVALID");
        assertThat(state(a,KEY_READ).json().path("tokenVersion").textValue()).isEqualTo("1");
        assertThat(state(b,KEY_READ).json().path("tokenVersion").textValue()).isEqualTo("0");
        send("POST","/lam-moi-token",null,null,null,other.cookie(),csrf()).expect(200);
    }

    @Test void twoRealRefreshRequestsHaveOneSuccessAndOneReuse()throws Exception{
        var a=user(List.of());var r0=login(a.name,PASSWORD);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{start.await();return send("POST","/lam-moi-token",null,null,null,r0.cookie(),csrf());});
            var second=pool.submit(()->{start.await();return send("POST","/lam-moi-token",null,null,null,r0.cookie(),csrf());});
            start.countDown();
            var one=first.get(20,java.util.concurrent.TimeUnit.SECONDS);var two=second.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(one.status,two.status)).containsExactlyInAnyOrder(200,401);
            (one.status==401?one:two).error(401,"REFRESH_REUSED");
        }
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",a.id)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND ThuHoiLuc IS NOT NULL",a.id)).isEqualTo(2);
    }

    @Test void usedCookieCanLogoutAndOldCookieCannotClearNewGeneration()throws Exception{
        var a=user(List.of());var r0=login(a.name,PASSWORD);
        var secondDevice=login(a.name,PASSWORD);
        var b=user(List.of());var other=login(b.name,PASSWORD);
        var r1=send("POST","/lam-moi-token",null,null,null,r0.cookie(),csrf());r1.expect(200);
        var logout=send("POST","/dang-xuat",null,null,null,r0.cookie(),csrf());logout.expect(204);
        assertThat(logout.headers.firstValue("Set-Cookie").orElseThrow()).contains("Max-Age=0","Path=/api/xac-thuc");
        send("POST","/lam-moi-token",null,null,null,r1.cookie(),csrf()).error(401,"REFRESH_INVALID");
        send("POST","/lam-moi-token",null,null,null,secondDevice.cookie(),csrf()).error(401,"REFRESH_INVALID");
        send("POST","/lam-moi-token",null,null,null,other.cookie(),csrf()).expect(200);
        assertThat(state(b,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("0");
        var newLogin=login(a.name,PASSWORD);
        var noop=send("POST","/dang-xuat",null,null,null,r0.cookie(),csrf());noop.expect(204);
        assertThat(noop.headers.firstValue("Set-Cookie")).isEmpty();
        send("GET","/toi",null,newLogin.json().path("accessToken").asText(),null,null,Map.of()).expect(200);
    }

    @Test void profileHistoryRoleNoOpAndAggregatePreconditions()throws Exception{
        var link=Map.of("sourceSystem","SIS_MOCK","profileType","student","sourceId",UUID.randomUUID().toString());
        var a=user(List.of(link));var b=user(List.of());
        var initial=send("GET","/tai-khoan/"+a.id,null,adminToken,null,null,Map.of());initial.expect(200);
        assertThat(initial.json().path("profileLinks")).hasSize(1);
        var conflict=send("PATCH","/tai-khoan/"+b.id,Map.of("profileLinks",List.of(link)),adminToken,b.tag,null,Map.of());
        conflict.error(409,"PROFILE_LINK_CONFLICT");
        var noChange=send("PATCH","/tai-khoan/"+a.id,Map.of("profileLinks",List.of(link)),adminToken,a.tag,null,Map.of());
        noChange.expect(200);assertThat(noChange.tag()).isEqualTo(a.tag);
        var change=send("PATCH","/tai-khoan/"+a.id,Map.of("profileLinks",List.of()),adminToken,a.tag,null,Map.of());
        change.expect(200);
        assertThat(count("SELECT COUNT(*) FROM dbo.LienKetHoSo WHERE TaiKhoanID=? AND KetThucLuc IS NOT NULL",a.id)).isEqualTo(1);
        var rbac=send("GET","/phan-quyen",null,adminToken,null,null,Map.of());rbac.expect(200);
        assertThat(rbac.json().path("accountRoles")).isEmpty();
        String role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",String.class);
        String path="/tai-khoan/"+a.id+"/vai-tro/"+role;
        send("PUT",path,null,adminToken,null,null,Map.of()).error(428,"PRECONDITION_REQUIRED");
        send("PUT",path,null,adminToken,a.tag,null,Map.of()).error(412,"VERSION_CONFLICT");
        var grant=send("PUT",path,null,adminToken,change.tag(),null,Map.of());grant.expect(200);
        var repeat=send("PUT",path,null,adminToken,grant.tag(),null,Map.of());repeat.expect(200);assertThat(repeat.tag()).isEqualTo(grant.tag());
        assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isTrue();
        var assigned=send("GET","/phan-quyen?accountId="+a.id,null,adminToken,null,null,Map.of());assigned.expect(200);
        assertThat(assigned.json().path("accountRoles")).hasSize(1);
        var revoke=send("DELETE",path,null,adminToken,grant.tag(),null,Map.of());revoke.expect(200);
        var repeatedRevoke=send("DELETE",path,null,adminToken,revoke.tag(),null,Map.of());repeatedRevoke.expect(200);
        assertThat(repeatedRevoke.tag()).isEqualTo(revoke.tag());
        assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isFalse();
    }

    @Test void lockUnlockAndPasswordResetRevokeCurrentTokens()throws Exception{
        var user=user(List.of());
        var login=login(user.name,PASSWORD);
        String jwt=login.json().path("accessToken").asText();
        String account="/tai-khoan/"+user.id;
        var lock=send("PUT",account+"/trang-thai",Map.of("active",false,"reason","Test lock"),adminToken,user.tag,null,Map.of());
        lock.expect(200);
        assertThat(state(user,KEY_READ).json().path("active").asBoolean()).isFalse();
        send("GET","/toi",null,jwt,null,null,Map.of()).error(401,"TOKEN_REVOKED");
        send("POST","/dang-nhap",Map.of("username",user.name,"password",PASSWORD),null,null,null,csrf()).error(401,"AUTHENTICATION_FAILED");
        var unlock=send("PUT",account+"/trang-thai",Map.of("active",true,"reason","Test unlock"),adminToken,lock.tag(),null,Map.of());
        unlock.expect(200);
        var noOpUnlock=send("PUT",account+"/trang-thai",Map.of("active",true,"reason","Repeated unlock"),adminToken,unlock.tag(),null,Map.of());
        noOpUnlock.expect(200);assertThat(noOpUnlock.tag()).isEqualTo(unlock.tag());
        assertThat(state(user,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("1");
        var second=login(user.name,PASSWORD);
        String next=PASSWORD+" updated";
        var reset=send("PUT",account+"/mat-khau",Map.of("newPassword",next,"reason","Test reset"),adminToken,unlock.tag(),null,Map.of());
        reset.expect(204);assertThat(reset.tag()).isNotEqualTo(unlock.tag());
        send("POST","/lam-moi-token",null,null,null,second.cookie(),csrf()).error(401,"REFRESH_INVALID");
        send("POST","/dang-nhap",Map.of("username",user.name,"password",PASSWORD),null,null,null,csrf()).error(401,"AUTHENTICATION_FAILED");
        login(user.name,next);
    }

    @Test void csrfAndValidationRejectBeforeMutation()throws Exception{
        var data=Map.of("username","missing","password",PASSWORD);
        send("POST","/dang-nhap",data,null,null,null,Map.of()).error(403,"CSRF_REJECTED");
        send("POST","/dang-nhap",data,null,null,null,Map.of("Origin","https://attacker.test","X-CSRF-Protection","1")).error(403,"CSRF_REJECTED");
        send("POST","/dang-nhap",Map.of("username","missing","password",PASSWORD,"unexpected",1),null,null,null,csrf()).error(400,"INVALID_REQUEST");
        send("POST","/dang-nhap",data,null,null,null,csrf()).error(401,"AUTHENTICATION_FAILED");
        send("POST","/lam-moi-token",null,null,null,null,csrf()).error(401,"REFRESH_INVALID");
        String unknown="ctsv_refresh="+Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        send("POST","/lam-moi-token",null,null,null,unknown,csrf()).error(401,"REFRESH_INVALID");
        for(String cookie:Arrays.asList(null,unknown)){
            var noop=send("POST","/dang-xuat",null,null,null,cookie,csrf());noop.expect(204);
            assertThat(noop.headers.firstValue("Set-Cookie")).isEmpty();
        }
        send("POST","/dang-xuat",Map.of("a","b"),null,null,null,csrf()).error(400,"INVALID_REQUEST");
    }

    @Test void corsPreflightAllowsOnlyConfiguredOriginAndCredentialHeaders()throws Exception{
        var allowed=send("OPTIONS","/dang-nhap",null,null,null,null,Map.of("Origin",ORIGIN,
            "Access-Control-Request-Method","POST","Access-Control-Request-Headers","Content-Type,X-CSRF-Protection"));
        allowed.expect(200);
        assertThat(allowed.headers.firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
        assertThat(allowed.headers.firstValue("Access-Control-Allow-Credentials")).contains("true");
        var denied=send("OPTIONS","/dang-nhap",null,null,null,null,Map.of("Origin","https://other.test",
            "Access-Control-Request-Method","POST"));
        denied.expect(403);
        assertThat(denied.headers.firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test void twoAdminWritersWithTheSameTagCannotBothCommit()throws Exception{
        var user=user(List.of());
        String path="/tai-khoan/"+user.id;
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{start.await();return send("PUT",path+"/trang-thai",
                Map.of("active",false,"reason","Concurrent status change"),adminToken,user.tag,null,Map.of());});
            var second=pool.submit(()->{start.await();return send("PUT",path+"/mat-khau",
                Map.of("newPassword",PASSWORD+" reset","reason","Concurrent password change"),adminToken,user.tag,null,Map.of());});
            start.countDown();
            var a=first.get(25,java.util.concurrent.TimeUnit.SECONDS);
            var b=second.get(25,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(List.of(a.status,b.status)).contains(412).anyMatch(status->status==200 || status==204);
            (a.status==412?a:b).error(412,"VERSION_CONFLICT");
        }
        assertThat(state(user,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=? AND HanhDong='TOKENS_REVOKED'",user.id)).isEqualTo(1);
    }

    @Test void inactiveRoleCannotBeGrantedAndBootstrapDoesNotOverwriteExistingAdmin()throws Exception{
        assertThat(admin.bootstrap("other."+UUID.randomUUID(),PASSWORD)).isFalse();
        var a=user(List.of());
        String code="P03_INACTIVE_"+UUID.randomUUID();
        jdbc.update("INSERT dbo.VaiTro(MaVaiTro,TrangThai) VALUES(?,0)",code);
        String role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",String.class,code);
        send("PUT","/tai-khoan/"+a.id+"/vai-tro/"+role,null,adminToken,a.tag,null,Map.of()).error(409,"ROLE_UNAVAILABLE");
        assertThat(state(a,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings={"grant","revoke","links","lock","reset"})
    void allAdminWritesRequireCurrentAggregatePreconditions(String operation)throws Exception {
        var a=user(List.of());
        String base="/tai-khoan/"+a.id;
        String role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",String.class);
        String path=switch(operation){case "grant","revoke"->base+"/vai-tro/"+role;case "links"->base;case "lock"->base+"/trang-thai";default->base+"/mat-khau";};
        String method=operation.equals("revoke")?"DELETE":operation.equals("links")?"PATCH":"PUT";
        Object body=switch(operation){case "links"->Map.of("profileLinks",List.of());case "lock"->Map.of("active",false,"reason","Synthetic precondition");case "reset"->Map.of("newPassword",PASSWORD+" new","reason","Synthetic precondition");default->null;};
        send(method,path,body,adminToken,null,null,Map.of()).error(428,"PRECONDITION_REQUIRED");
        var change=send("PATCH",base,Map.of("profileLinks",List.of(Map.of("sourceSystem","SIS_MOCK","profileType","student","sourceId",UUID.randomUUID().toString()))),adminToken,a.tag,null,Map.of());
        change.expect(200);
        int before=count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=?",a.id);
        send(method,path,body,adminToken,a.tag,null,Map.of()).error(412,"VERSION_CONFLICT");
        assertThat(count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=?",a.id)).isEqualTo(before);
        assertThat(state(a,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("1");
    }

    @ParameterizedTest
    @CsvSource({
        "login,INSERT dbo.RefreshToken", "login,INSERT dbo.NhatKyAuth",
        "refresh,UPDATE dbo.RefreshToken SET SuDungLuc", "refresh,INSERT dbo.RefreshToken", "refresh,INSERT dbo.NhatKyAuth",
        "logout,UPDATE dbo.TaiKhoan SET PhienBanToken", "logout,UPDATE dbo.RefreshToken SET ThuHoiLuc", "logout,INSERT dbo.NhatKyAuth",
        "grant,INSERT dbo.TaiKhoan_VaiTro", "grant,INSERT dbo.NhatKyAuth",
        "revoke,DELETE dbo.TaiKhoan_VaiTro", "revoke,INSERT dbo.NhatKyAuth",
        "links,INSERT dbo.LienKetHoSo", "links,INSERT dbo.NhatKyAuth",
        "lock,UPDATE dbo.TaiKhoan SET TrangThai", "lock,INSERT dbo.NhatKyAuth",
        "reset,UPDATE dbo.TaiKhoan SET MatKhauHash", "reset,INSERT dbo.NhatKyAuth",
        "create,INSERT dbo.TaiKhoan(", "create,INSERT dbo.NhatKyAuth"
    })
    void sqlFaultsReturnSanitizedErrorsWithoutCookieOrPartialMutation(String operation,String point)throws Exception {
        var a=user(List.of());
        String base="/tai-khoan/"+a.id;
        String role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",String.class);
        if(operation.equals("revoke")){
            var grant=send("PUT",base+"/vai-tro/"+role,null,adminToken,a.tag,null,Map.of());grant.expect(200);
            a=new User(a.name,a.id,grant.tag());
        }
        var token=login(a.name,PASSWORD);
        int audits=count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=?",a.id);
        String newName="fault."+UUID.randomUUID();
        Reply response;
        SQL_FAULT.set(point);
        try{
            response=switch(operation){
                case "login"->send("POST","/dang-nhap",Map.of("username",a.name,"password",PASSWORD),null,null,null,csrf());
                case "refresh"->send("POST","/lam-moi-token",null,null,null,token.cookie(),csrf());
                case "logout"->send("POST","/dang-xuat",null,null,null,token.cookie(),csrf());
                case "grant"->send("PUT",base+"/vai-tro/"+role,null,adminToken,a.tag,null,Map.of());
                case "revoke"->send("DELETE",base+"/vai-tro/"+role,null,adminToken,a.tag,null,Map.of());
                case "links"->send("PATCH",base,Map.of("profileLinks",List.of(Map.of("sourceSystem","SIS_MOCK","profileType","student","sourceId",UUID.randomUUID().toString()))),adminToken,a.tag,null,Map.of());
                case "lock"->send("PUT",base+"/trang-thai",Map.of("active",false,"reason","Synthetic fault"),adminToken,a.tag,null,Map.of());
                case "reset"->send("PUT",base+"/mat-khau",Map.of("newPassword",PASSWORD+" new","reason","Synthetic fault"),adminToken,a.tag,null,Map.of());
                default->send("POST","/tai-khoan",Map.of("username",newName,"password",PASSWORD,"profileLinks",List.of()),adminToken,null,null,Map.of());
            };
        }finally{SQL_FAULT.set(null);}
        response.error(500,"INTERNAL_ERROR");
        assertThat(response.body).doesNotContain("51561","SQLServer","synthetic SQL",PASSWORD,"accessToken");
        assertThat(state(a,KEY_READ).json().path("tokenVersion").asText()).isEqualTo(operation.equals("revoke")?"1":"0");
        assertThat(state(a,KEY_READ).json().path("active").asBoolean()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=?",a.id)).isEqualTo(audits);
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND SuDungLuc IS NULL AND ThuHoiLuc IS NULL",a.id)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM dbo.LienKetHoSo WHERE TaiKhoanID=?",a.id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=?",a.id)).isEqualTo(operation.equals("revoke")?1:0);
        assertThat(count("SELECT COUNT(*) FROM dbo.TaiKhoan WHERE TenDangNhapChuanHoa=?",newName)).isZero();
        assertThat(send("GET",base,null,adminToken,null,null,Map.of()).tag()).isEqualTo(a.tag);
        send("POST","/lam-moi-token",null,null,null,token.cookie(),csrf()).expect(200);
    }

    @Test void inactiveRolesAndFunctionsNeverGrantEffectiveCtsvAccess()throws Exception {
        var a=user(List.of());
        String code="P03_EFFECTIVE_"+UUID.randomUUID();
        jdbc.update("INSERT dbo.VaiTro(MaVaiTro) VALUES(?)",code);
        String role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",String.class,code);
        jdbc.update("INSERT dbo.VaiTro_ChucNang SELECT ?,ID FROM dbo.ChucNang WHERE MaChucNang=N'ctsv.access'",role);
        var grant=send("PUT","/tai-khoan/"+a.id+"/vai-tro/"+role,null,adminToken,a.tag,null,Map.of());grant.expect(200);
        assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isTrue();
        jdbc.update("UPDATE dbo.VaiTro SET TrangThai=0 WHERE ID=?",role);
        assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isFalse();
        jdbc.update("UPDATE dbo.VaiTro SET TrangThai=1 WHERE ID=?",role);
        try{
            jdbc.update("UPDATE dbo.ChucNang SET TrangThai=0 WHERE MaChucNang=N'ctsv.access'");
            assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isFalse();
            var token=login(a.name,PASSWORD);
            var me=send("GET","/toi",null,token.json().path("accessToken").asText(),null,null,Map.of());me.expect(200);
            assertThat(me.json().path("functions")).isEmpty();
        }finally{jdbc.update("UPDATE dbo.ChucNang SET TrangThai=1 WHERE MaChucNang=N'ctsv.access'");}
        assertThat(state(a,KEY_READ).json().path("ctsvAccess").asBoolean()).isTrue();
    }

    @Test void logoutFirstBlocksRefreshAndDoesNotRequireAnyAccessToken()throws Exception {
        var a=user(List.of());var token=login(a.name,PASSWORD);
        send("POST","/dang-xuat",null,null,null,token.cookie(),csrf()).expect(204);
        send("POST","/lam-moi-token",null,null,null,token.cookie(),csrf()).error(401,"REFRESH_INVALID");
        assertThat(state(a,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=? AND HanhDong='TOKENS_REVOKED'",a.id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void realHttpLimiterRejectsSixthPairOrThirtyFirstIpAttempt(boolean sameUsername)throws Exception {
        int limit=sameUsername?5:30;
        for(int i=0;i<limit;i++)
            send("POST","/dang-nhap",Map.of("username",sameUsername?"unknown.pair":"unknown.ip."+i,"password",PASSWORD),null,null,null,csrf())
                .error(401,"AUTHENTICATION_FAILED");
        var denied=send("POST","/dang-nhap",Map.of("username",sameUsername?"unknown.pair":"unknown.ip.next","password",PASSWORD),null,null,null,csrf());
        denied.error(429,"AUTH_RATE_LIMITED");
        assertThat(Long.parseLong(denied.headers.firstValue("Retry-After").orElseThrow())).isBetween(1L,900L);
    }

    @Test void lostHttpsResponseAfterRotationCommitCannotBeRecoveredByReplayingOldCookie()throws Exception {
        var a=user(List.of());var token=login(a.name,PASSWORD);
        var forwarded=new java.util.concurrent.atomic.AtomicInteger();
        var committedStatus=new java.util.concurrent.atomic.AtomicInteger();
        var completed=new java.util.concurrent.CountDownLatch(1);
        var handlerFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var proxy=com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        proxy.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(proxyTls));
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            proxy.setExecutor(executor);
            proxy.createContext("/drop",exchange->{
                try{
                    if(forwarded.incrementAndGet()==1){
                        var reply=send("POST","/lam-moi-token",null,null,null,token.cookie(),csrf());
                        committedStatus.set(reply.status);
                        // Drop all response bytes after the real provider committed; never forward the new cookie.
                    }
                }catch(Throwable e){handlerFailure.set(e);}
                finally{exchange.close();completed.countDown();}
            });
            proxy.start();
            try(var client=HttpClient.newBuilder().sslContext(testTls).build()){
                var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+proxy.getAddress().getPort()+"/drop"))
                    .timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.noBody()).build();
                assertThatThrownBy(()->client.send(request,HttpResponse.BodyHandlers.ofString())).isInstanceOf(java.io.IOException.class);
            }finally{proxy.stop(0);}
            assertThat(completed.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(handlerFailure.get()).isNull();
            assertThat(committedStatus.get()).isEqualTo(200);
        }
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",a.id)).isEqualTo(2);
        send("POST","/lam-moi-token",null,null,null,token.cookie(),csrf()).error(401,"REFRESH_REUSED");
        assertThat(state(a,KEY_READ).json().path("tokenVersion").asText()).isEqualTo("1");
        assertThat(count("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND ThuHoiLuc IS NOT NULL",a.id)).isEqualTo(2);
    }

    @Test void canonicalUsernameConflictsAndUntrustedProfileFieldsAreRejected()throws Exception {
        String suffix=UUID.randomUUID().toString();
        var original=send("POST","/tai-khoan",Map.of("username","  TE\u0301ST."+suffix+"  ","password",PASSWORD,"profileLinks",List.of()),adminToken,null,null,Map.of());
        original.expect(201);
        send("POST","/tai-khoan",Map.of("username","tést."+suffix,"password",PASSWORD,"profileLinks",List.of()),adminToken,null,null,Map.of()).error(409,"USERNAME_CONFLICT");
        send("POST","/tai-khoan",Map.of("username","test."+suffix,"password",PASSWORD,"profileLinks",List.of()),adminToken,null,null,Map.of()).expect(201);
        for(var link:List.of(Map.of("sourceSystem","UNTRUSTED","profileType","student","sourceId","synthetic"),
            Map.of("sourceSystem","SIS_MOCK","profileType","student","sourceId","person@example.invalid")))
            send("POST","/tai-khoan",Map.of("username","bad."+UUID.randomUUID(),"password",PASSWORD,"profileLinks",List.of(link)),adminToken,null,null,Map.of()).error(400,"INVALID_REQUEST");
    }
}
