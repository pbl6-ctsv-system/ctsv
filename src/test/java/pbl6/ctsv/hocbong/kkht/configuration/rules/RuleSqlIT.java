package pbl6.ctsv.hocbong.kkht.configuration.rules;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.hocbong.kkht.configuration.rules.entity.RuleVersion;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.ScholarshipTestDatabase;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;
import static org.assertj.core.api.Assertions.*;

/** Guarded real SQL/JPA. Actor pre-resolved; no Auth HTTP or SIS/finance mocks called. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleSqlIT {
    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager em;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private RuleRepository repository;
    private RuleService service;
    @BeforeAll void setup() throws Exception {
        var ds=ScholarshipTestDatabase.migrate();jdbc=new JdbcTemplate(ds);
        factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan(RuleVersion.class.getPackageName(),ScholarshipAudit.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto","validate");
        // Match DEV's per-table metadata strategy and scope to CTSV's dbo schema.
        factory.getJpaPropertyMap().put("hibernate.default_schema","dbo");
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.jdbc_metadata_extraction_strategy","individually");factory.afterPropertiesSet();
        var manager=new JpaTransactionManager(factory.getObject());manager.setDataSource(ds);manager.afterPropertiesSet();
        tx=new TransactionTemplate(manager);em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        repository=new RuleRepository(em,jdbc);service=new RuleService(repository,RuleFixtures.JSON,Clock.systemUTC());
    }
    @AfterAll void close(){if(factory!=null)factory.destroy();}

    @Test void HB_RULE_05_realCrudKeepsVersionDecimalJsonAuditAndReadback() {
        rollback(actor->{
            long before=repository.count();var created=service.create(RuleFixtures.command(),actor);
            assertThat(created.body().status()).isEqualTo("draft");assertThat(created.body().usedAt()).isNull();
            assertThat(created.body().levels().A().tuitionMultiplier()).isEqualTo("1.5000");
            var body=RuleFixtures.body();body.put("legalBasis","SYNTHETIC — nội dung đã sửa");
            long id=Long.parseLong(created.body().id());var updated=service.update(id,RuleRules.command(body),created.etag(),actor);
            assertThat(updated.body().version()).isEqualTo(created.body().version());assertThat(updated.etag()).isNotEqualTo(created.etag());
            em.clear();assertThat(service.get(id)).isEqualTo(updated);assertThat(repository.count()).isEqualTo(before+1);
            assertThat(jdbc.queryForList("SELECT hanh_dong FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=? ORDER BY id",String.class,actor.userId())).containsExactly("rule.created","rule.updated");
            String audit=jdbc.queryForObject("SELECT du_lieu_sau FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=? AND hanh_dong=N'rule.updated'",String.class,actor.userId());
            try { assertThat(RuleFixtures.JSON.readTree(audit).at("/conditions/progressRatioMin").textValue()).isEqualTo("1.0000000000"); }catch(Exception e){throw new AssertionError(e);}
        });
    }
    @ParameterizedTest @ValueSource(strings={"0001-01-01","1582-10-10","9999-12-31"})
    void HB_RULE_10_hibernatePreservesEntireSqlDateDomain(String date) {
        rollback(actor->{var body=RuleFixtures.body();body.put("effectiveFrom",date);var result=service.create(RuleRules.command(body),actor);em.clear();assertThat(service.get(Long.parseLong(result.body().id())).body().effectiveFrom()).isEqualTo(date);});
    }
    @Test void HB_RULE_06_staleMissingAndMalformedVersionNeverWrite() {
        rollback(actor->{var created=service.create(RuleFixtures.command(),actor);long id=Long.parseLong(created.body().id());
            for(String tag:new String[]{null,"*","W/\"v1\"","\"stale\""})assertThatThrownBy(()->service.update(id,RuleFixtures.command(),tag,actor)).isInstanceOf(RoundFailure.class);
            assertThat(service.get(id)).isEqualTo(created);assertThat(auditCount(actor)).isEqualTo(1);
        });
    }
    @Test void HB_RULE_07_firstRealRoundReferencePermanentlyLocksVersionEvenAfterUnlink() {
        rollback(actor->{var created=service.create(RuleFixtures.command(),actor);long id=Long.parseLong(created.body().id());
            long round=round(actor,id);em.clear();var used=service.get(id);
            assertThat(used.body().status()).isEqualTo("used");assertThat(used.body().usedAt()).isNotNull();assertThat(used.etag()).isNotEqualTo(created.etag());
            assertThatThrownBy(()->service.update(id,RuleFixtures.command(),used.etag(),actor)).hasMessage("RULE_VERSION_IN_USE");
            jdbc.update("UPDATE dbo.kkht_dot_xet SET quy_tac_id=NULL WHERE id=?",round);em.clear();
            assertThat(service.get(id).body().usedAt()).isEqualTo(used.body().usedAt());
            assertThatThrownBy(()->service.update(id,RuleFixtures.command(),used.etag(),actor)).hasMessage("RULE_VERSION_IN_USE");
        });
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE dbo.kkht_phien_ban_quy_tac SET can_cu=N'SYN forged' WHERE id=?","DELETE FROM dbo.kkht_phien_ban_quy_tac WHERE id=?","UPDATE dbo.kkht_phien_ban_quy_tac SET trang_thai=N'draft',da_su_dung_luc=NULL WHERE id=?"})
    void HB_RULE_07_databaseAlsoRejectsUsedPayloadDeleteAndUnlock(String sql) {
        assertThatThrownBy(()->rollback(actor->{
            var rule=service.create(RuleFixtures.command(),actor);long id=Long.parseLong(rule.body().id());long round=round(actor,id);
            // Unlink first: otherwise the FK correctly preempts DELETE before the history trigger.
            jdbc.update("UPDATE dbo.kkht_dot_xet SET quy_tac_id=NULL WHERE id=?",round);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.kkht_phien_ban_quy_tac WHERE id=? AND da_su_dung_luc IS NOT NULL",Integer.class,id)).isEqualTo(1);
            jdbc.update(sql,id);
        })).hasStackTraceContaining("51950");
    }
    @Test void HB_RULE_09_failedAuditRollsBackTheRuleAndItsVersion() {
        long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.kkht_phien_ban_quy_tac",Long.class);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            var actor=actor();var failing=new RuleRepository(em,jdbc){@Override void audit(ScholarshipAudit event){event.setRoleId(-1L);super.audit(event);}};
            new RuleService(failing,RuleFixtures.JSON,Clock.systemUTC()).create(RuleFixtures.command(),actor);
        })).hasStackTraceContaining("fk_hb_nhat_ky_vai_tro");
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.kkht_phien_ban_quy_tac",Long.class)).isEqualTo(count);
    }
    @Test void HB_RULE_11_newVersionKeepsOldHistoryAndListIsServerPaged() {
        rollback(actor->{var old=service.create(RuleFixtures.command(),actor);var next=service.create(RuleFixtures.command(),actor);
            assertThat(next.body().version()).isGreaterThan(old.body().version());assertThat(service.get(Long.parseLong(old.body().id()))).isEqualTo(old);
            var page=service.list(new int[]{0,1});assertThat(page.items()).hasSize(1);assertThat(page.items().getFirst().id()).isEqualTo(next.body().id());assertThat(page.totalElements()).isEqualTo(repository.count());
        });
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE dbo.kkht_phien_ban_quy_tac SET dieu_kien_json=N'[]' WHERE id=?","UPDATE dbo.kkht_phien_ban_quy_tac SET ty_le_suat_cuoi=0.49 WHERE id=?","UPDATE dbo.kkht_phien_ban_quy_tac SET hieu_luc_den='00010101' WHERE id=?","UPDATE dbo.kkht_phien_ban_quy_tac SET phien_ban=phien_ban+1000000 WHERE id=?"})
    void HB_RULE_10_databaseRejectsInvalidJsonRatioDatesAndRenumbering(String sql) {
        assertThatThrownBy(()->rollback(actor->{var rule=service.create(RuleFixtures.command(),actor);jdbc.update(sql,Long.parseLong(rule.body().id()));})).hasStackTraceContaining(sql.contains("phien_ban=")?"51950":"ck_kkht_rule");
    }
    @Test void HB_RULE_08_twoTransactionsAllocateDistinctVersionsAndAuditExactlyOnceEach() throws Exception {
        var actor=tx.execute(status->actor());var attempts=race(()->service.create(RuleFixtures.command(),actor),()->service.create(RuleFixtures.command(),actor));
        assertThat(attempts).allMatch(a->a.failure()==null);assertThat(attempts).extracting(a->a.view().body().version()).doesNotHaveDuplicates();
        assertThat(attempts).extracting(Attempt::session).doesNotHaveDuplicates();assertThat(auditCount(actor)).isEqualTo(2);
    }
    @Test void HB_RULE_08_twoUpdatesUsingSameTagHaveOneWinnerAndOneStaleResponse() throws Exception {
        var actor=tx.execute(status->actor());var initial=tx.execute(status->service.create(RuleFixtures.command(),actor));long id=Long.parseLong(initial.body().id());
        var a=RuleFixtures.body();a.put("legalBasis","SYNTHETIC — concurrent A");var b=RuleFixtures.body();b.put("legalBasis","SYNTHETIC — concurrent B");
        var attempts=race(()->service.update(id,RuleRules.command(a),initial.etag(),actor),()->service.update(id,RuleRules.command(b),initial.etag(),actor));
        assertThat(attempts).extracting(Attempt::failure).containsExactlyInAnyOrder(null,"VERSION_CONFLICT");assertThat(auditCount(actor)).isEqualTo(2);
    }
    @Test void HB_RULE_08_usageAndEditShareFenceAndUsedVersionCannotBeReopened() throws Exception {
        var actor=tx.execute(status->actor());var initial=tx.execute(status->service.create(RuleFixtures.command(),actor));long id=Long.parseLong(initial.body().id());long round=tx.execute(status->round(actor,null));
        var attempts=race(()->service.update(id,RuleFixtures.command(),initial.etag(),actor),()->{repository.serializeWrites();jdbc.update("UPDATE dbo.kkht_dot_xet SET quy_tac_id=? WHERE id=?",id,round);em.clear();return service.get(id);});
        assertThat(attempts.get(1).failure()).isNull();assertThat(attempts.get(0).failure()).isIn(null,"VERSION_CONFLICT","RULE_VERSION_IN_USE");
        var used=tx.execute(status->service.get(id));assertThat(used.body().status()).isEqualTo("used");
        assertThatThrownBy(()->tx.execute(status->service.update(id,RuleFixtures.command(),used.etag(),actor))).hasMessage("RULE_VERSION_IN_USE");
    }
    @Test void HB_RULE_08_rawUseWithoutFenceStillPreservesCommittedStateOrRollsBackDeadlockVictim() throws Exception {
        var actor=tx.execute(status->actor());var initial=tx.execute(status->service.create(RuleFixtures.command(),actor));long id=Long.parseLong(initial.body().id());long round=tx.execute(status->round(actor,null));
        var body=RuleFixtures.body();body.put("legalBasis","SYNTHETIC — raw race edit");
        var attempts=race(()->service.update(id,RuleRules.command(body),initial.etag(),actor),()->{
            jdbc.update("UPDATE dbo.kkht_dot_xet SET quy_tac_id=? WHERE id=?",id,round);em.clear();return service.get(id);
        });
        assertThat(attempts.getFirst().failure()).isIn(null,"VERSION_CONFLICT","RULE_VERSION_IN_USE","DEPENDENCY_UNAVAILABLE");
        assertThat(attempts.get(1).failure()).isIn(null,"DEPENDENCY_UNAVAILABLE");
        assertThat(attempts).anyMatch(a->a.failure()==null);
        var current=tx.execute(status->service.get(id));
        Long linked=jdbc.queryForObject("SELECT quy_tac_id FROM dbo.kkht_dot_xet WHERE id=?",Long.class,round);
        if(attempts.get(1).failure()==null){
            assertThat(linked).isEqualTo(id);assertThat(current.body().status()).isEqualTo("used");assertThat(current.body().usedAt()).isNotNull();
            assertThatThrownBy(()->tx.execute(status->service.update(id,RuleFixtures.command(),current.etag(),actor))).hasMessage("RULE_VERSION_IN_USE");
        }else{assertThat(linked).isNull();assertThat(current.body().status()).isEqualTo("draft");assertThat(current.body().usedAt()).isNull();}
        assertThat(current.body().legalBasis()).isEqualTo(attempts.getFirst().failure()==null?"SYNTHETIC — raw race edit":initial.body().legalBasis());
        assertThat(auditCount(actor)).isEqualTo(attempts.getFirst().failure()==null?2:1);
    }
    private void rollback(Consumer<RoundAccess.Actor> work){tx.executeWithoutResult(status->{status.setRollbackOnly();work.accept(actor());});}
    private RoundAccess.Actor actor(){long id=jdbc.queryForObject("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)",Long.class,"syn-hb-rule-"+UUID.randomUUID()+"@example.invalid");long role=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien'",Long.class);return new RoundAccess.Actor(id,null,role);}
    private long round(RoundAccess.Actor actor,Long rule){
        int year=jdbc.queryForObject("SELECT COALESCE(MAX(TRY_CONVERT(int,LEFT(nam_hoc,4))),2999)+1 FROM dbo.hb_hoc_ky WHERE TRY_CONVERT(int,LEFT(nam_hoc,4))>=3000",Integer.class);if(year>9998)throw new AssertionError("Synthetic year range exhausted");
        long semester=jdbc.queryForObject("INSERT dbo.hb_hoc_ky(nam_hoc,thu_tu_ky,ngay_bat_dau,ngay_ket_thuc,trang_thai) OUTPUT INSERTED.id VALUES(?,1,CONVERT(date,?,112),NULL,N'draft')",Long.class,year+"-"+(year+1),year+"0801");
        long alias=jdbc.queryForObject("INSERT dbo.hb_anh_xa_ky_nguon(he_thong_nguon,loai_ma,ma_ky_nguon,ma_dot_nguon,hoc_ky_id,xac_nhan_boi,xac_nhan_luc) OUTPUT INSERTED.id VALUES(N'SYN_RULE',N'semester',?,NULL,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",Long.class,"SYN-"+UUID.randomUUID(),semester,actor.userId());
        return jdbc.queryForObject("DECLARE @r TABLE(id bigint); INSERT dbo.kkht_dot_xet(hoc_ky_id,anh_xa_ky_nguon_id,so_dot,ten_dot,quy_tac_id,trang_thai,nguoi_tao_id) OUTPUT INSERTED.id INTO @r VALUES(?,?,1,N'SYNTHETIC — rule use',?,N'draft',?); SELECT id FROM @r",Long.class,semester,alias,rule,actor.userId());
    }
    private long auditCount(RoundAccess.Actor actor){return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?",Long.class,actor.userId());}
    private List<Attempt> race(java.util.concurrent.Callable<RuleDtos.View> a,java.util.concurrent.Callable<RuleDtos.View> b) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var f1=pool.submit(()->attempt(a,ready,start));var f2=pool.submit(()->attempt(b,ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).as("two actual SQL transactions").isTrue();start.countDown();
            return List.of(f1.get(30,TimeUnit.SECONDS),f2.get(30,TimeUnit.SECONDS));
        }
    }
    private Attempt attempt(java.util.concurrent.Callable<RuleDtos.View> work,CountDownLatch ready,CountDownLatch start){
        return tx.execute(status->{int session=jdbc.queryForObject("SELECT @@SPID",Integer.class);ready.countDown();try{if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("race start timeout");return new Attempt(session,work.call(),null);}catch(RoundFailure e){status.setRollbackOnly();return new Attempt(session,null,e.getMessage());}catch(Exception e){
            for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&(sql.getErrorCode()==1205||sql.getErrorCode()==1222)){
                status.setRollbackOnly();return new Attempt(session,null,"DEPENDENCY_UNAVAILABLE");
            }
            throw new IllegalStateException(e);
        }});
    }
    private record Attempt(int session,RuleDtos.View view,String failure){}
}
