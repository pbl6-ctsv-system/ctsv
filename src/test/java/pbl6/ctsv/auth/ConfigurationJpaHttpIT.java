package pbl6.ctsv.auth;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import pbl6.ctsv.foundation.persistence.RefactorDatabase;

/** POL/DOT assertions reused unchanged, with fresh synthetic fixtures on the refactor DB only. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,ConfigurationJpaHttpIT.Config.class},
    properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","server.address=127.0.0.1"})
class ConfigurationJpaHttpIT extends P05HttpIT {
    @org.junit.jupiter.api.Test void cookieLogoutUsesTheSameClockAsSyntheticAccountCreation() throws Exception {
        var account=identity(false);
        var login=send("POST","/api/xac-thuc/dang-nhap",java.util.Map.of("username",jdbc.queryForObject("SELECT TenDangNhap FROM dbo.TaiKhoan WHERE CONVERT(varchar(36),ID)=(SELECT ma_chu_the_dang_nhap FROM dbo.dinh_danh_dang_nhap WHERE nguoi_dung_id=?)",String.class,Long.parseLong(account[0])),"password",PASSWORD),null,null);
        login.status(200);
        var cookie=login.headers().firstValue("Set-Cookie").orElseThrow().split(";",2)[0];
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://127.0.0.1:"+port+"/api/xac-thuc/dang-xuat"))
            .header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1").header("Cookie",cookie).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        try(var client=java.net.http.HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()) {
            org.assertj.core.api.Assertions.assertThat(client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
        }
    }
    @TestConfiguration(proxyBeanMethods=false) static class Config extends P05HttpIT.Config {
        @Override @Bean DataSource p05Source() throws Exception { return RefactorDatabase.source(); }
    }
    @Override @BeforeAll void identities() throws Exception {
        var a=identity(true);cv=Long.parseLong(a[0]);cvToken=a[1];
        var b=identity(false);sv=Long.parseLong(b[0]);svToken=b[1];
        aliases=pbl6.ctsv.mghp.configuration.P05Fixtures.seedForJpaRefactor();
        policyM2=alias("mghp_chinh_sach",2);lockedM2=alias("mghp_phien_ban_chinh_sach",102);semester=alias("semesters",901);
    }
}
