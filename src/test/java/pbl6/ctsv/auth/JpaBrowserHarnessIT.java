package pbl6.ctsv.auth;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

/** Explicit browser-only host on the refactor DB; select #serve, never use this as a DEV server. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,ConfigurationJpaHttpIT.Config.class},
    properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","server.address=127.0.0.1","server.port=9445"})
class JpaBrowserHarnessIT extends ConfigurationJpaHttpIT {
    @Test void serve() throws Exception {
        Path home=Path.of(Objects.requireNonNull(System.getenv("P05_BROWSER_HOME")));
        Files.createDirectories(home);Path done=home.resolve("browser.done");
        if(Files.exists(done))throw new IllegalStateException("Use a fresh, private refactor browser directory");
        var config=new LinkedHashMap<String,Object>();config.put("origin","https://127.0.0.1:5175");config.put("password",PASSWORD);
        config.put("database",pbl6.ctsv.foundation.persistence.RefactorDatabase.NAME);config.put("semesterId",Long.toString(semester));
        config.put("policyId",Long.toString(policyM2));config.put("lockedVersionId",Long.toString(lockedM2));
        config.put("lockedVersion",jdbc.queryForObject("SELECT phien_ban FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",Integer.class,lockedM2));
        for(var entry:Map.of("cv",cv,"student",sv).entrySet())config.put(entry.getKey(),jdbc.queryForObject("SELECT t.TenDangNhap FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(varchar(36),t.ID) WHERE d.nguoi_dung_id=?",String.class,entry.getValue()));
        Files.writeString(home.resolve("credentials.json"),JSON.writeValueAsString(config));Files.writeString(home.resolve("ready.json"),"{\"ready\":true}");
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(8);
        while(!Files.exists(done)&&System.nanoTime()<deadline)Thread.sleep(250);
        assertThat(Files.exists(done)).as("Browser smoke completed").isTrue();assertThat(Files.readString(done).trim()).isEqualTo("PASS");
    }
}
