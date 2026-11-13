package pbl6.ctsv.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/** Native SQL probes use private fixture schemas inside the guarded P03 database. */
class AuthDatabaseGuardsIT {
    static DataSource source;
    static KeyPair key;
    static AuthCredentials credentials;
    static final List<String> ownedSchemas=new ArrayList<>();
    static final String PASSWORD="Synthetic database probe "+UUID.randomUUID();
    @BeforeAll static void setup()throws Exception {
        source=AuthTestDatabase.migrate();
        var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);key=g.generateKeyPair();
        credentials=new AuthCredentials();
    }
    static final class Probe extends JdbcTemplate {
        final String schema;
        Probe(String schema){super(source);this.schema=schema;}
        String sql(String text){return text.replace("[dbo].","["+schema+"].").replace("dbo.",schema+".");}
        @Override public int update(String sql,Object... args){return super.update(sql(sql),args);}
        @Override public <T> T queryForObject(String sql,Class<T> type){return super.queryForObject(sql(sql),type);}
        @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){return super.queryForObject(sql(sql),type,args);}
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){return super.query(sql(sql),mapper,args);}
    }
    static Probe probe()throws Exception {
        String schema="p03_probe_"+UUID.randomUUID().toString().replace("-","");
        var raw=new JdbcTemplate(source);
        raw.execute("CREATE SCHEMA ["+schema+"]");
        ownedSchemas.add(schema);
        String ddl;
        try(var input=AuthDatabaseGuardsIT.class.getResourceAsStream("/db/migration/V6__auth_module.sql")){
            ddl=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        }
        var probe=new Probe(schema);
        // Same approved schema, constraints, guards and seed; production Flyway history is untouched.
        raw.execute(probe.sql(ddl.substring(ddl.indexOf("CREATE TABLE"))));
        return probe;
    }
    @AfterAll static void removeOnlySchemasCreatedByThisTestProcess()throws Exception {
        var jdbc=new JdbcTemplate(AuthTestDatabase.source()); // Re-check DB/instance/owner/marker before cleanup.
        var tx=new org.springframework.transaction.support.TransactionTemplate(new JdbcTransactionManager(source));
        for(String schema:ownedSchemas){
            if(!schema.matches("p03_probe_[a-f0-9]{32}")) throw new IllegalStateException("Unexpected probe schema");
            tx.executeWithoutResult(s->{
                for(String table:List.of("LienKetHoSo","TaiKhoan_VaiTro","VaiTro_ChucNang","NhatKyAuth","RefreshToken","TaiKhoan","VaiTro","ChucNang"))
                    jdbc.execute("DROP TABLE IF EXISTS ["+schema+"].["+table+"]");
                jdbc.execute("DROP SCHEMA ["+schema+"]");
            });
        }
    }
    static AuthService service(JdbcTemplate jdbc){
        var jwt=new AuthJwt("issuer","aud","test",(RSAPrivateKey)key.getPrivate(),
            Map.of("test",(RSAPublicKey)key.getPublic()),Clock.systemUTC());
        return new AuthService(new AuthRepository(jdbc),credentials,Clock.systemUTC(),jwt,new AuthLoginLimiter(),
            new JdbcTransactionManager(jdbc.getDataSource()));
    }
    static AuthAdministration admin(JdbcTemplate jdbc){
        return new AuthAdministration(service(jdbc),Set.of("SIS_MOCK"),new com.fasterxml.jackson.databind.ObjectMapper());
    }
    @Test void bootstrapAcrossTwoConnectionsCreatesExactlyOneAdminAndNeverOverwrites()throws Exception {
        var jdbc=probe();
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var one=pool.submit(()->{start.await();return admin(jdbc).bootstrap("probe.one",PASSWORD);});
            var two=pool.submit(()->{start.await();return admin(jdbc).bootstrap("probe.two",PASSWORD);});
            start.countDown();
            assertThat(List.of(one.get(20,java.util.concurrent.TimeUnit.SECONDS),two.get(20,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE HanhDong='BOOTSTRAP_ADMIN'",Integer.class)).isEqualTo(1);
        assertThat(admin(jdbc).bootstrap("probe.again",PASSWORD+" different")).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings={"INSERT dbo.TaiKhoan(","INSERT dbo.TaiKhoan_VaiTro","INSERT dbo.NhatKyAuth"})
    void bootstrapFaultAtAccountRoleOrAuditRollsBackAndCanBeRetried(String point)throws Exception {
        var fixture=probe();
        var broken=new JdbcTemplate(source){
            @Override public int update(String sql,Object... args){
                int result=super.update(fixture.sql(sql),args);
                if(sql.contains(point)) super.update("THROW 51560,N'P03 bootstrap fixture fault',1;");
                return result;
            }
            @Override public <T> T queryForObject(String sql,Class<T> type){return super.queryForObject(fixture.sql(sql),type);}
            @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){return super.queryForObject(fixture.sql(sql),type,args);}
            @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){return super.query(fixture.sql(sql),mapper,args);}
        };
        assertThatThrownBy(()->admin(broken).bootstrap("probe.admin",PASSWORD)).isInstanceOf(DataAccessException.class);
        assertThat(fixture.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan",Integer.class)).isZero();
        assertThat(fixture.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro",Integer.class)).isZero();
        assertThat(fixture.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE HanhDong='BOOTSTRAP_ADMIN'",Integer.class)).isZero();
        assertThat(admin(fixture).bootstrap("probe.admin",PASSWORD)).isTrue();
    }
    @ParameterizedTest
    @ValueSource(strings={
        "ActorID,TacNhanHeThong|NULL,NULL",
        "PhienBanTokenTruoc,PhienBanTokenSau|NULL,1",
        "PhienBanTokenTruoc,PhienBanTokenSau|1,NULL",
        "TrangThaiTruoc,TrangThaiSau|NULL,1",
        "TrangThaiTruoc,TrangThaiSau|1,NULL",
        "ThoiDiem|TODATETIMEOFFSET(SYSUTCDATETIME(),'+07:00')"
    })
    void nullableAuditChecksRejectSqlUnknownAndWrongOffsets(String variant)throws Exception {
        var jdbc=probe();
        var parts=variant.split("\\|");
        String columns="SuKienID,MaTuongQuan,HanhDong";
        String values="NEWID(),NEWID(),'RBAC_SEEDED'";
        if(!parts[0].contains("TacNhanHeThong")){columns+=",TacNhanHeThong";values+=",'migration'";}
        String sql="INSERT dbo.NhatKyAuth("+columns+","+parts[0]+") VALUES("+values+","+parts[1]+")";
        assertThatThrownBy(()->jdbc.update(sql)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest
    @ValueSource(strings={
        "TokenHash=HASHBYTES('SHA2_256','different')",
        "HetHanLuc=DATEADD(second,-1,HetHanLuc)",
        "PhienBanToken=PhienBanToken+1",
        "SuDungLuc=NULL",
        "ThuHoiLuc=NULL"
    })
    void refreshImmutableFieldsAndConsumeRevokeCannotBeRewritten(String update)throws Exception {
        var jdbc=probe();
        var admin=admin(jdbc);
        admin.bootstrap("probe.admin",PASSWORD);
        var auth=service(jdbc);
        var token=auth.login("probe.admin",PASSWORD,"192.0.2.80");
        auth.refresh(token.cookie());
        auth.logout(token.cookie());
        String id=token.response().accountId().toString();
        assertThatThrownBy(()->jdbc.update("UPDATE dbo.RefreshToken SET "+update+" WHERE TaiKhoanID=? AND SuDungLuc IS NOT NULL",id))
            .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND SuDungLuc IS NOT NULL AND ThuHoiLuc IS NOT NULL",Integer.class,id)).isEqualTo(1);
    }
    @Test void profileHistoryAuditOwnershipUniquenessAndMultiRowGuardsAreEnforced()throws Exception {
        var jdbc=probe();
        var admin=admin(jdbc);admin.bootstrap("probe.admin",PASSWORD);
        var auth=service(jdbc);
        var actor=auth.verifyAccessToken(auth.login("probe.admin",PASSWORD,"192.0.2.81").response().accessToken());
        var link=new AuthViews.ProfileLink("SIS_MOCK","student","synthetic-student-1");
        var a=admin.create(actor,"profile.a",PASSWORD,List.of(link));
        var b=admin.create(actor,"profile.b",PASSWORD,List.of());
        assertThatThrownBy(()->admin.updateLinks(actor,b.account().accountId(),b.etag(),List.of(link)))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.code()).isEqualTo("PROFILE_LINK_CONFLICT"));
        var closed=admin.updateLinks(actor,a.account().accountId(),a.etag(),List.of());
        assertThatThrownBy(()->jdbc.update("UPDATE dbo.LienKetHoSo SET KetThucLuc=NULL,NhatKyKetThucID=NULL")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE dbo.LienKetHoSo")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE dbo.TaiKhoan SET TenDangNhap=TenDangNhap+N' changed'")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan WHERE TenDangNhap LIKE N'% changed'",Integer.class)).isZero();
        // The same verified source can be linked again only after the old interval was closed.
        var now=admin.updateLinks(actor,a.account().accountId(),closed.etag(),List.of(link));
        assertThat(now.account().profileLinks()).containsExactly(link);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.LienKetHoSo",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->jdbc.update("""
            UPDATE dbo.LienKetHoSo SET NhatKyKetThucID=(
              SELECT TOP(1) ID FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=?)
            WHERE KetThucLuc IS NOT NULL
            """,b.account().accountId().toString())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(NEWID(),1)")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT dbo.VaiTro(MaVaiTro) VALUES(N'AUTH_ADMIN')")).isInstanceOf(DataAccessException.class);
    }
}
