package pbl6.ctsv.auth;

import java.security.KeyPairGenerator;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

class AuthTransactionIT {
    static javax.sql.DataSource source;
    static AuthCredentials credentials;
    static java.security.KeyPair keys;
    static final String PASSWORD="Synthetic rollback and expiry "+UUID.randomUUID();

    @BeforeAll static void fixtures()throws Exception {
        source=AuthTestDatabase.migrate();
        credentials=new AuthCredentials();
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);keys=generator.generateKeyPair();
    }
    AuthService service(JdbcTemplate jdbc,Clock clock){
        var jwt=new AuthJwt("issuer","aud","test",(RSAPrivateKey)keys.getPrivate(),
            Map.of("test",(RSAPublicKey)keys.getPublic()),clock);
        return new AuthService(new AuthRepository(jdbc),credentials,clock,jwt,new AuthLoginLimiter(),new JdbcTransactionManager(jdbc.getDataSource()));
    }
    String add(AuthService service){
        String user="sql.p03."+UUID.randomUUID();
        service.transaction(()->{
            service.db.create(UUID.randomUUID(),AuthCredentials.username(user),credentials.hash(PASSWORD),service.now());
            return null;
        });
        return user;
    }

    @ParameterizedTest
    @CsvSource({"true,true","true,false","false,true","false,false"})
    void refreshAndAdminRevocationSerializeInBothOrders(boolean refreshFirst,boolean reset)throws Exception {
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        var operator=normal.db.byUsername(add(normal));
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",operator.id().toString());
        var actor=normal.verifyAccessToken(normal.login(operator.username(),PASSWORD,"192.0.2.60").response().accessToken());
        var token=normal.login(add(normal),PASSWORD,"192.0.2.61");
        UUID target=token.response().accountId();
        var firstLocked=new java.util.concurrent.CountDownLatch(1);
        var secondAttempted=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var firstOnce=new java.util.concurrent.atomic.AtomicBoolean();
        var first=service(new JdbcTemplate(source){
            @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args){
                var rows=super.query(sql,mapper,args);
                if(sql.contains("WITH(UPDLOCK,HOLDLOCK)") && sql.contains("FROM dbo.TaiKhoan ")
                    && Arrays.asList(args).contains(target.toString()) && firstOnce.compareAndSet(false,true)){
                    firstLocked.countDown();
                    await(release);
                }
                return rows;
            }
        },Clock.systemUTC());
        var second=service(new JdbcTemplate(source){
            @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args){
                if(sql.contains("WITH(UPDLOCK,HOLDLOCK)") && sql.contains("FROM dbo.TaiKhoan ")
                    && Arrays.asList(args).contains(target.toString())) secondAttempted.countDown();
                return super.query(sql,mapper,args);
            }
        },Clock.systemUTC());
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String tag=new AuthAdministration(normal,Set.of(),json).read(actor,target).etag();
        java.util.function.Function<AuthService,Object> revoke=s->{
            var admin=new AuthAdministration(s,Set.of(),json);
            return reset?admin.reset(actor,target,tag,PASSWORD+" replacement","Synthetic race")
                :admin.status(actor,target,tag,false,"Synthetic race");
        };
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var one=pool.submit(()->refreshFirst?first.refresh(token.cookie()):revoke.apply(first));
            assertThat(firstLocked.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var two=pool.submit(()->refreshFirst?revoke.apply(second):second.refresh(token.cookie()));
            try{
                assertThat(secondAttempted.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }finally{release.countDown();}
            one.get(15,java.util.concurrent.TimeUnit.SECONDS);
            if(refreshFirst) two.get(15,java.util.concurrent.TimeUnit.SECONDS);
            else assertThatThrownBy(()->two.get(15,java.util.concurrent.TimeUnit.SECONDS))
                .hasCauseInstanceOf(AuthFailure.class)
                .satisfies(e->assertThat(((AuthFailure)e.getCause()).code()).isEqualTo("REFRESH_INVALID"));
        }finally{release.countDown();}
        var actual=normal.db.account(target,false);
        assertThat(actual.version()).isEqualTo(1);
        assertThat(actual.active()).isEqualTo(reset);
        assertThat(credentials.matches(reset?PASSWORD+" replacement":PASSWORD,actual.hash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND ThuHoiLuc IS NULL",Integer.class,target.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",Integer.class,target.toString())).isEqualTo(refreshFirst?2:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=? AND HanhDong='TOKENS_REVOKED'",Integer.class,target.toString())).isEqualTo(1);
    }

    static void await(java.util.concurrent.CountDownLatch latch){
        try{if(!latch.await(15,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("SQL barrier timed out");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }

    @Test void passwordResetBetweenCredentialReadAndAccountLockPreventsLoginTokenIssuance(){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        var operator=normal.db.byUsername(add(normal));
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",operator.id().toString());
        var actor=normal.verifyAccessToken(normal.login(operator.username(),PASSWORD,"192.0.2.65").response().accessToken());
        String username=add(normal);
        UUID target=normal.db.byUsername(username).id();
        var admin=new AuthAdministration(normal,Set.of(),new com.fasterxml.jackson.databind.ObjectMapper());
        String tag=admin.read(actor,target).etag();
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        var racing=service(new JdbcTemplate(source){
            @Override public <T> List<T> query(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object... args){
                var rows=super.query(sql,mapper,args);
                if(sql.contains("WHERE TenDangNhapChuanHoa=?") && Arrays.asList(args).contains(username)
                    && once.compareAndSet(false,true)) admin.reset(actor,target,tag,PASSWORD+" reset","Synthetic login race");
                return rows;
            }
        },Clock.systemUTC());
        assertThatThrownBy(()->racing.login(username,PASSWORD,"192.0.2.66"))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.code()).isEqualTo("AUTHENTICATION_FAILED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",Integer.class,target.toString())).isZero();
        assertThat(normal.login(username,PASSWORD+" reset","192.0.2.67").response().accountId()).isEqualTo(target);
    }

    @Test void publicInternalStateInterfaceRejectsWrongBindingAndFailsClosedWithoutDatabase(){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        var issued=normal.login(add(normal),PASSWORD,"192.0.2.68");
        var principal=normal.verifyAccessToken(issued.response().accessToken());
        UUID target=principal.accountId();
        assertThatThrownBy(()->normal.requireCtsvAccess(principal,UUID.randomUUID()))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status()).isEqualTo(503));
        // Active account without ctsv.access is forbidden, not an authentication success fallback.
        assertThatThrownBy(()->normal.requireCtsvAccess(principal,target))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status()).isEqualTo(403));
        var unavailable=new org.springframework.jdbc.datasource.AbstractDataSource(){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException{throw new java.sql.SQLException("Synthetic database unavailable","08001");}
            @Override public java.sql.Connection getConnection(String user,String password)throws java.sql.SQLException{return getConnection();}
        };
        var broken=service(new JdbcTemplate(unavailable),Clock.systemUTC());
        assertThatThrownBy(()->broken.requireCtsvAccess(principal,target))
            .isInstanceOfSatisfying(AuthFailure.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");});
        normal.logout(issued.cookie());
        assertThatThrownBy(()->normal.requireCtsvAccess(principal,target))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status()).isEqualTo(401));
    }

    @Test void rollbackDuringRefreshLeavesOriginalUsableAndInsertsNoSuccessor(){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        String username=add(normal);
        var r0=normal.login(username,PASSWORD,"192.0.2.10");
        String id=r0.response().accountId().toString();
        var failAudit=new JdbcTemplate(source){
            @Override public int update(String sql,Object... args){
                if(sql.contains("INSERT dbo.NhatKyAuth")) return super.update("THROW 51555,N'P03 injected audit failure',1;");
                return super.update(sql,args);
            }
        };
        var broken=service(failAudit,Clock.systemUTC());
        assertThatThrownBy(()->broken.refresh(r0.cookie())).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND SuDungLuc IS NULL",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",Integer.class,id)).isEqualTo(1);
        assertThat(normal.refresh(r0.cookie()).response().refreshExpiresAt()).isEqualTo(r0.response().refreshExpiresAt());
    }

    @Test void exactExpiryIsInvalidAndExpiredLogoutIsNoOp(){
        var jdbc=new JdbcTemplate(source);
        Instant t0=Instant.parse("2026-11-12T01:00:00Z");
        var normal=service(jdbc,Clock.fixed(t0,ZoneOffset.UTC));
        var issue=normal.login(add(normal),PASSWORD,"192.0.2.11");
        var nearExpiry=service(jdbc,Clock.fixed(issue.response().refreshExpiresAt().minusNanos(1000),ZoneOffset.UTC));
        var last=nearExpiry.refresh(issue.cookie());
        assertThat(last.response().refreshExpiresAt()).isEqualTo(issue.response().refreshExpiresAt());
        var later=service(jdbc,Clock.fixed(issue.response().refreshExpiresAt(),ZoneOffset.UTC));
        assertThatThrownBy(()->later.refresh(last.cookie()))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.code).isEqualTo("REFRESH_INVALID"));
        assertThat(later.logout(issue.cookie())).isFalse();
        assertThat(jdbc.queryForObject("SELECT PhienBanToken FROM dbo.TaiKhoan WHERE ID=?",Long.class,issue.response().accountId().toString())).isZero();
    }

    @Test void appendOnlyAuditAndGenerationGuardRejectInvalidSql(){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        var issue=normal.login(add(normal),PASSWORD,"192.0.2.12");
        String id=issue.response().accountId().toString();
        assertThatThrownBy(()->jdbc.update("UPDATE dbo.NhatKyAuth SET LyDo=N'changed' WHERE TaiKhoanDichID=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE dbo.TaiKhoan SET TrangThai=0 WHERE ID=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE dbo.RefreshToken WHERE TaiKhoanID=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(normal.state(issue.response().accountId()).active()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void passwordAndStatusChangesRollBackWhenTheirFinalAuditFails(boolean reset){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        String operator=add(normal);
        var actor=normal.db.byUsername(operator);
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",actor.id().toString());
        var admin=normal.verifyAccessToken(normal.login(operator,PASSWORD,"192.0.2.14").response().accessToken());
        var token=normal.login(add(normal),PASSWORD,"192.0.2.15");
        UUID id=token.response().accountId();
        var old=normal.db.account(id,false);
        var failAudit=new JdbcTemplate(source){
            @Override public int update(String sql,Object... args){
                String action=reset?"PASSWORD_RESET":"ACCOUNT_STATUS_CHANGED";
                if(sql.contains("INSERT dbo.NhatKyAuth") && Arrays.asList(args).contains(action))
                    return super.update("THROW 51556,N'P03 injected final audit failure',1;");
                return super.update(sql,args);
            }
        };
        var administration=new AuthAdministration(service(failAudit,Clock.systemUTC()),Set.of(),new com.fasterxml.jackson.databind.ObjectMapper());
        String tag=administration.read(admin,id).etag();
        assertThatThrownBy(()->{
            if(reset) administration.reset(admin,id,tag,PASSWORD+" changed","Synthetic test");
            else administration.status(admin,id,tag,false,"Synthetic test");
        }).isInstanceOf(DataAccessException.class);
        var actual=normal.db.account(id,false);
        assertThat(actual.active()).isTrue();
        assertThat(actual.version()).isEqualTo(old.version());
        assertThat(actual.rowVersion()).isEqualTo(old.rowVersion());
        assertThat(credentials.matches(PASSWORD,actual.hash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=? AND HanhDong='TOKENS_REVOKED'",Integer.class,id.toString())).isZero();
        assertThat(normal.refresh(token.cookie()).response().accountId()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(booleans={true,false})
    void connectionLossAtCommitDoesNotReturnTokensAndLeavesAnAtomicSqlOutcome(boolean serverCommitted){
        var jdbc=new JdbcTemplate(source);
        var normal=service(jdbc,Clock.systemUTC());
        var issue=normal.login(add(normal),PASSWORD,"192.0.2.13");
        String account=issue.response().accountId().toString();
        var uncertain=new org.springframework.jdbc.datasource.AbstractDataSource(){
            @Override public java.sql.Connection getConnection()throws java.sql.SQLException {
                var physical=source.getConnection();
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.Connection.class},(proxy,method,args)->{
                        if(method.getName().equals("commit")){
                            if(serverCommitted) physical.commit();
                            physical.close();
                            throw new java.sql.SQLException("Synthetic commit acknowledgement loss","08006");
                        }
                        try{return method.invoke(physical,args);}
                        catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                    });
            }
            @Override public java.sql.Connection getConnection(String username,String password)throws java.sql.SQLException {
                throw new java.sql.SQLFeatureNotSupportedException();
            }
        };
        var broken=service(new JdbcTemplate(uncertain),Clock.systemUTC());
        assertThatThrownBy(()->broken.refresh(issue.cookie())).isInstanceOfAny(DataAccessException.class,
            org.springframework.transaction.TransactionException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=?",Integer.class,account))
            .isEqualTo(serverCommitted?2:1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefreshToken WHERE TaiKhoanID=? AND SuDungLuc IS NOT NULL",Integer.class,account))
            .isEqualTo(serverCommitted?1:0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE TaiKhoanDichID=? AND HanhDong='REFRESH_ROTATED'",Integer.class,account))
            .isEqualTo(serverCommitted?1:0);
    }
}
