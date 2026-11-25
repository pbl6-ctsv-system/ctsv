package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.repository.FoundationRepository;
import pbl6.ctsv.foundation.repository.ScholarshipScopeRepository;
import pbl6.ctsv.hocbong.kkht.round.entity.*;
import pbl6.ctsv.hocbong.kkht.scope.entity.*;
import static org.assertj.core.api.Assertions.*;

/** Contract 0.3 SQL suite. Dedicated guarded HB DB only; every fixture/mutation rolls back.
 * Real SQL/JPA/provider/audit. Auth is represented by an already resolved synthetic actor.
 * Concurrent multi-session races remain a separate required gate; this suite never claims them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScholarshipRoundSqlIT {
    JdbcTemplate jdbc;
    EntityManager em;
    LocalContainerEntityManagerFactoryBean factory;
    TransactionTemplate tx;
    RoundRepository repo;
    RoundService rounds;
    ScholarshipScopeRepository source;
    final ObjectMapper json=new ObjectMapper();
    @BeforeAll void verifiedDatabaseAndHibernateValidation() throws Exception {
        var ds=ScholarshipTestDatabase.migrate();jdbc=new JdbcTemplate(ds);
        factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan("pbl6.ctsv.foundation","pbl6.ctsv.hocbong.kkht.round.entity","pbl6.ctsv.hocbong.kkht.scope.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto","validate");factory.afterPropertiesSet();
        var manager=new JpaTransactionManager(factory.getObject());manager.setDataSource(ds);manager.afterPropertiesSet();
        em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());tx=new TransactionTemplate(manager);
        repo=new RoundRepository(em,jdbc);var terms=new FoundationRepository(em,jdbc);
        rounds=new RoundService(repo,terms,json,Clock.systemUTC());source=new ScholarshipScopeRepository(jdbc,terms,json);
    }
    @AfterAll void close() {if(factory!=null)factory.destroy();}
    @Test void realProviderCreateReplayAndActorBoundLookup() {
        fixture(f->{
            var service=writer(repo,List.of());var command=command(f);UUID key=UUID.randomUUID();
            var created=service.create(command,key,f.actor());assertThat(created.replayed()).isFalse();
            assertThat(created.view().body().name()).isEqualTo("2094/2095 - Học kỳ 1 - Đợt 1");
            long id=Long.parseLong(created.view().body().id());
            var scope=service.scope(id);assertThat(scope.scope().groups()).hasSize(1);
            assertThat(scope.scope().groups().getFirst().studentCount()).isEqualTo(2);
            var replay=service.create(command,key,f.actor());assertThat(replay.replayed()).isTrue();assertThat(replay.view()).isEqualTo(created.view());
            assertThat(service.lookup(key,f.actor()).roundId()).isEqualTo(""+id);
            assertFailure(()->service.lookup(key,new RoundAccess.Actor(f.otherActor(),null,f.actor().roleId())),404,"NOT_FOUND");
            assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?",f.actor().userId())).isEqualTo(3);
            assertFailure(()->service.create(command,UUID.randomUUID(),f.actor()),409,"ROUND_SEQUENCE_NOT_READY");
        });
    }
    @Test void semesterWithStartOnlyCreatesReadsAndEditsScopeOnRealSql() {
        fixture(f->{
            jdbc.update("UPDATE dbo.hoc_ky SET ngay_ket_thuc=NULL,trang_thai_xac_minh_moc=N'unverified' WHERE id=?",f.source());
            var service=writer(repo,List.of());
            assertThat(rounds.sourceById(f.source()).selectable()).isTrue();
            var created=service.create(command(f),UUID.randomUUID(),f.actor()).view();
            long id=Long.parseLong(created.body().id());
            assertThat(created.body().semester().endDate()).isNull();
            assertThat(jdbc.queryForObject("SELECT is_nullable FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.hb_hoc_ky') AND name=N'ngay_ket_thuc'",Integer.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT ngay_ket_thuc FROM dbo.hb_hoc_ky WHERE id=?",java.sql.Date.class,Long.parseLong(created.body().semester().id()))).isNull();
            var scope=service.scope(id).scope();var group=scope.groups().getFirst();
            var changed=service.update(id,new ScopeRules.Update(scope.catalogRevision(),new ScopeRules.Selection("groups",List.of(
                new ScopeRules.Pair(Long.parseLong(group.cohortId()),Long.parseLong(group.majorId()))))),created.etag(),f.actor());
            assertThat(changed.body().semester().endDate()).isNull();
            assertThat(changed.etag()).isNotEqualTo(created.etag());
            assertThat(rounds.detail(id).body().semester().startDate()).isEqualTo("2094-08-01");
        });
    }
    @Test void revisionSeesSourceMembershipChangesAndNewStudentsAfterCutoffAreExcluded() {
        fixture(f->{
            var command=command(f);var before=source.catalog(f.source());assertThat(before.groups().getFirst().studentCount()).isEqualTo(2);
            jdbc.update("UPDATE dbo.sinh_vien SET dang_hien_dien_nguon=0 WHERE id=?",f.student());
            assertThat(source.catalog(f.source()).revision()).isNotEqualTo(before.revision());
            assertFailure(()->writer(repo,List.of()).create(command,UUID.randomUUID(),f.actor()),409,"SCOPE_SOURCE_CHANGED");
            assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_hoc_ky WHERE nam_hoc=N'2094-2095'")).isZero();
        });
    }
    @Test void scopeAppendChangesRootTagAndPreservesOldVersionWhileNoOpDoesNotAudit() {
        fixture(f->{
            var service=writer(repo,List.of());var created=service.create(command(f),UUID.randomUUID(),f.actor()).view();long id=Long.parseLong(created.body().id());
            var before=service.scope(id);long audit=count("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?",f.actor().userId());
            var noop=service.update(id,new ScopeRules.Update(before.scope().catalogRevision(),new ScopeRules.Selection("all",List.of())),created.etag(),f.actor());
            assertThat(noop).isEqualTo(created);assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?",f.actor().userId())).isEqualTo(audit);
            var group=before.scope().groups().getFirst();
            var next=service.update(id,new ScopeRules.Update(before.scope().catalogRevision(),new ScopeRules.Selection("groups",List.of(new ScopeRules.Pair(Long.parseLong(group.cohortId()),Long.parseLong(group.majorId()))))),created.etag(),f.actor());
            assertThat(next.etag()).isNotEqualTo(created.etag());assertThat(next.body().number()).isEqualTo(1);assertThat(next.body().name()).isEqualTo(created.body().name());
            assertThat(service.scope(id).scope().revision()).isEqualTo(2);
            assertThat(repo.scope(Long.parseLong(before.scope().id())).getMode()).isEqualTo("all");
            assertFailure(()->service.update(id,new ScopeRules.Update(before.scope().catalogRevision(),new ScopeRules.Selection("all",List.of())),created.etag(),f.actor()),412,"VERSION_CONFLICT");
        });
    }
    @Test void legacyIsReadableButCannotBeReconciledOrExtendedImplicitly() {
        fixture(f->{
            var service=writer(repo,List.of());var created=service.create(command(f),UUID.randomUUID(),f.actor()).view();long id=Long.parseLong(created.body().id());
            jdbc.update("UPDATE dbo.kkht_dot_xet SET pham_vi_hien_tai_id=NULL,ma_lenh_khoi_tao=NULL,bam_lenh_khoi_tao=NULL WHERE id=?",id);em.clear();
            assertThat(service.scope(id).scope()).isNull();
            assertFailure(()->service.create(command(f),UUID.randomUUID(),f.actor()),409,"LEGACY_ROUND_RECONCILIATION_REQUIRED");
            var current=rounds.detail(id);assertFailure(()->service.update(id,new ScopeRules.Update(command(f).catalogRevision(),new ScopeRules.Selection("all",List.of())),current.etag(),f.actor()),409,"LEGACY_ROUND_RECONCILIATION_REQUIRED");
        });
    }
    @Test void realUsageCheckBlocksScopeChangeEvenWhileDraft() {
        fixture(f->{
            var service=writer(repo,List.of());var created=service.create(command(f),UUID.randomUUID(),f.actor()).view();long id=Long.parseLong(created.body().id());
            assertFailure(()->writer(repo,List.of((round,scope)->true)).update(id,new ScopeRules.Update(command(f).catalogRevision(),new ScopeRules.Selection("all",List.of())),created.etag(),f.actor()),409,"SCOPE_LOCKED");
        });
    }
    @ParameterizedTest @ValueSource(strings={"semester.initialized","semester.source-linked","round.created"})
    void auditFaultRollsBackPeriodAliasRootScopeAndGroups(String fault) {
        long roots=count("SELECT COUNT_BIG(*) FROM dbo.kkht_dot_xet"),scopes=count("SELECT COUNT_BIG(*) FROM dbo.kkht_pham_vi_dot"),groups=count("SELECT COUNT_BIG(*) FROM dbo.kkht_nhom_pham_vi");
        assertThatThrownBy(()->fixture(f->{
            var failing=new RoundRepository(em,jdbc){@Override void persistAudit(ScholarshipAudit event){if(fault.equals(event.getAction()))event.setRoleId(-1L);super.persistAudit(event);}};
            writer(failing,List.of()).create(command(f),UUID.randomUUID(),f.actor());
        })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("fk_hb_nhat_ky_vai_tro");
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.kkht_dot_xet")).isEqualTo(roots);
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.kkht_pham_vi_dot")).isEqualTo(scopes);
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.kkht_nhom_pham_vi")).isEqualTo(groups);
    }
    @Test void pointerCannotReferToAnotherRoundsScope() {
        fixture(f->{
            var service=writer(repo,List.of());var first=service.create(command(f),UUID.randomUUID(),f.actor()).view();long id=Long.parseLong(first.body().id());
            long scope=Long.parseLong(service.scope(id).scope().id());
            var root=repo.round(id);
            long other=insert("INSERT dbo.kkht_dot_xet(hoc_ky_id,anh_xa_ky_nguon_id,so_dot,ten_dot,trang_thai,nguoi_tao_id) OUTPUT INSERTED.id VALUES(?,?,2,N'SYN legacy',N'draft',?)",root.getSemesterId(),root.getSourceMappingId(),f.actor().userId());
            assertThatThrownBy(()->jdbc.update("UPDATE dbo.kkht_dot_xet SET pham_vi_hien_tai_id=? WHERE id=?",scope,other)).hasMessageContaining("fk_kkht_dot_xet_current_scope");
        });
    }
    @ParameterizedTest @ValueSource(strings={"kkht_pham_vi_dot","kkht_nhom_pham_vi"})
    void scopeHistoryIsAppendOnlyEvenForNativeWrites(String table) {
        fixture(f->{
            writer(repo,List.of()).create(command(f),UUID.randomUUID(),f.actor());
            String sql=table.equals("kkht_pham_vi_dot")?"UPDATE dbo.kkht_pham_vi_dot SET phien_ban=phien_ban WHERE tao_boi=?":"DELETE FROM dbo.kkht_nhom_pham_vi WHERE pham_vi_id IN (SELECT id FROM dbo.kkht_pham_vi_dot WHERE tao_boi=?)";
            assertThatThrownBy(()->jdbc.update(sql,f.actor().userId())).hasMessageContaining("append-only");
        });
    }
    private ScopeRules.Create command(Fixture f) {return new ScopeRules.Create(f.source(),source.catalog(f.source()).revision(),new ScopeRules.Selection("all",List.of()));}
    private ScopeService writer(RoundRepository repository,List<RoundScopeUsage> usages) {
        return new ScopeService(repository,new RoundService(repository,new FoundationRepository(em,jdbc),json,Clock.systemUTC()),source,
            new ScholarshipConfiguration().scholarshipCompletedAssessments(),usages,json,Clock.systemUTC());
    }
    private void fixture(Consumer<Fixture> action) {
        tx.executeWithoutResult(status->{status.setRollbackOnly();
            assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hoc_ky WHERE ma=N'9410'")).as("isolated HB fixture source").isZero();
            assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_hoc_ky WHERE nam_hoc=N'2094-2095'")).isZero();
            long actor=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)","hb-scope-"+UUID.randomUUID()+"@example.invalid");
            long other=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)","hb-other-"+UUID.randomUUID()+"@example.invalid");
            long role=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien'",Long.class);
            long term=insert("INSERT dbo.hoc_ky(ma,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc) OUTPUT INSERTED.id VALUES(N'9410','2094-08-01','2095-01-15',N'verified')");
            long future=insert("INSERT dbo.hoc_ky(ma) OUTPUT INSERTED.id VALUES(N'9420')");
            long cohort=insert("INSERT dbo.khoa_hoc(ma) OUTPUT INSERTED.id VALUES(N'SYN-K94')");
            long major=insert("INSERT dbo.nganh_noi_bo(ma,ten) OUTPUT INSERTED.id VALUES(N'SYN-CNTT',N'SYN Ngành')");
            long program=insert("INSERT dbo.chuong_trinh_dao_tao(nganh_noi_bo_id) OUTPUT INSERTED.id VALUES(?)",major);
            long student=student(term,cohort,program);student(term,cohort,program);student(future,cohort,program);
            action.accept(new Fixture(term,new RoundAccess.Actor(actor,null,role),other,student));
        });
    }
    private long student(long term,long cohort,long program) {
        return insert("INSERT dbo.sinh_vien(mssv,khoa_hoc_hien_tai_id,chuong_trinh_hien_tai_id,hoc_ky_bat_dau_nguon_id) OUTPUT INSERTED.id VALUES(?,?,?,?)","SYN-"+UUID.randomUUID(),cohort,program,term);
    }
    private long insert(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    private long count(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    private void assertFailure(Runnable work,int status,String code) {assertThatThrownBy(work::run).isInstanceOfSatisfying(RoundFailure.class,e->{assertThat(e.status).isEqualTo(status);assertThat(e.code).isEqualTo(code);});}
    private record Fixture(long source,RoundAccess.Actor actor,long otherActor,long student) {}
}
