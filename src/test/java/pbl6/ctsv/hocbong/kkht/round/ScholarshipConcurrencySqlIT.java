package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.api.ScholarshipScopeSource;
import pbl6.ctsv.foundation.repository.FoundationRepository;
import static org.assertj.core.api.Assertions.*;

/** HB-T024-013–021/034. Real multi-session SQL/JPA; scope roster is an explicit stub.
 * Append-only committed test records are retained in the guarded HB test DB. No DEV data or cleanup.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScholarshipConcurrencySqlIT {
    Connection fixtureLease;
    JdbcTemplate jdbc;
    EntityManager em;
    LocalContainerEntityManagerFactoryBean factory;
    TransactionTemplate tx;
    ExecutorService workers;
    final ObjectMapper json=new ObjectMapper();
    final String revision="A".repeat(43);
    @BeforeAll void setup() throws Exception {
        var ds=ScholarshipTestDatabase.migrate();jdbc=new JdbcTemplate(ds);fixtureLease=ds.getConnection();
        try(var statement=fixtureLease.createStatement();var result=statement.executeQuery("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'ctsv.hocbong.t027.concurrency-fixtures',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=5000; SELECT @r")) {
            assertThat(result.next()&&result.getInt(1)>=0).as("exclusive test fixture lease").isTrue();
        }
        factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
        factory.setPackagesToScan("pbl6.ctsv.foundation","pbl6.ctsv.hocbong.kkht.round.entity","pbl6.ctsv.hocbong.kkht.scope.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto","validate");factory.afterPropertiesSet();
        var manager=new JpaTransactionManager(factory.getObject());manager.setDataSource(ds);manager.afterPropertiesSet();
        em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());tx=new TransactionTemplate(manager);workers=Executors.newFixedThreadPool(2);
    }
    @AfterAll void close() throws Exception {if(workers!=null)workers.shutdownNow();if(factory!=null)factory.destroy();if(fixtureLease!=null)fixtureLease.close();}
    @Test void sameActorAndKeyHaveOneCreatedAndOneReplay() throws Exception {
        var f=fixture();UUID key=UUID.randomUUID();var command=command(f,"all",List.of());
        var outcomes=race(()->f.service().create(command,key,f.first()),()->f.service().create(command,key,f.first()));
        assertThat(outcomes).allSatisfy(o->assertThat(o.error()).isNull());
        assertThat(outcomes).extracting(o->((ScopeDtos.Created)o.value()).replayed()).containsExactlyInAnyOrder(false,true);
        assertThat(outcomes).extracting(o->((ScopeDtos.Created)o.value()).view().body().id()).containsOnly(((ScopeDtos.Created)outcomes.getFirst().value()).view().body().id());
        assertRoundCount(f,1);assertThat(auditCount(f)).isEqualTo(3);
    }
    @Test void differentActorsCannotCreateRoundTwoBeforeAssessmentCompletes() throws Exception {
        var f=fixture();var command=command(f,"all",List.of());
        var outcomes=race(()->f.service().create(command,UUID.randomUUID(),f.first()),()->f.service().create(command,UUID.randomUUID(),f.second()));
        assertOneFailure(outcomes,409,"ROUND_SEQUENCE_NOT_READY");assertRoundCount(f,1);assertThat(auditCount(f)).isEqualTo(3);
    }
    @Test void sameKeyDifferentIntentCannotCreateTwoRounds() throws Exception {
        var f=fixture();UUID key=UUID.randomUUID();
        var outcomes=race(()->f.service().create(command(f,"all",List.of()),key,f.first()),
            ()->f.service().create(command(f,"groups",List.of(new ScopeRules.Pair(f.cohortA(),f.major()))),key,f.first()));
        assertOneFailure(outcomes,409,"IDEMPOTENCY_KEY_REUSED");assertRoundCount(f,1);assertThat(auditCount(f)).isEqualTo(3);
    }
    @Test void twoScopeUpdatesWithOneTagHaveExactlyOneWinner() throws Exception {
        var f=fixture();var initial=tx.execute(status->f.service().create(command(f,"all",List.of()),UUID.randomUUID(),f.first()).view());
        long id=Long.parseLong(initial.body().id());
        var outcomes=race(()->f.service().update(id,new ScopeRules.Update(revision,new ScopeRules.Selection("groups",List.of(new ScopeRules.Pair(f.cohortA(),f.major())))),initial.etag(),f.first()),
            ()->f.service().update(id,new ScopeRules.Update(revision,new ScopeRules.Selection("groups",List.of(new ScopeRules.Pair(f.cohortB(),f.major())))),initial.etag(),f.second()));
        assertOneFailure(outcomes,412,"VERSION_CONFLICT");
        var current=tx.execute(status->f.service().scope(id));assertThat(current.roundEtag()).isNotEqualTo(initial.etag());assertThat(current.scope().revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.kkht_pham_vi_dot WHERE dot_xet_id=?",Long.class,id)).isEqualTo(2);
        assertThat(auditCount(f)).isEqualTo(4);assertRoundCount(f,1);
    }
    @Test void applicationFenceTimeoutDoesNotLeavePartialCreation() throws Exception {
        var f=fixture();
        try(var holder=jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            try(var statement=holder.createStatement()) {
                statement.execute("BEGIN TRANSACTION; DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'ctsv.hocbong.kkht.rounds',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1000; IF @r<0 THROW 51939,N'Cannot hold test fence',1;");
                assertThatThrownBy(()->tx.execute(status->f.service().create(command(f,"all",List.of()),UUID.randomUUID(),f.first())))
                    .isInstanceOfSatisfying(RoundFailure.class,e->{assertThat(e.status).isEqualTo(503);assertThat(e.code).isEqualTo("DEPENDENCY_UNAVAILABLE");});
            } finally {holder.rollback();}
        }
        assertRoundCount(f,0);assertThat(auditCount(f)).isZero();
    }
    Fixture fixture() {
        return tx.execute(status->{
            Integer year=null;
            for(int candidate=60;candidate<94;candidate++) {
                String code=candidate+"10";
                long existing=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hoc_ky WHERE ma=?",Long.class,code);
                if(existing==0){year=candidate;break;}
            }
            assertThat(year).as("free synthetic source period; owner may recreate dedicated test DB when exhausted").isNotNull();
            int y=2000+year;
            long term=insert("INSERT dbo.hoc_ky(ma,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc) OUTPUT INSERTED.id VALUES(?,?,?,N'verified')",year+"10",y+"-08-01",(y+1)+"-01-15");
            long first=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)","hb-race-a-"+UUID.randomUUID()+"@example.invalid");
            long second=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)","hb-race-b-"+UUID.randomUUID()+"@example.invalid");
            long role=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien'",Long.class);
            long a=insert("INSERT dbo.khoa_hoc(ma) OUTPUT INSERTED.id VALUES(N'SYN-T027-A')"),b=insert("INSERT dbo.khoa_hoc(ma) OUTPUT INSERTED.id VALUES(N'SYN-T027-B')");
            long major=insert("INSERT dbo.nganh_noi_bo(ma) OUTPUT INSERTED.id VALUES(N'SYN-T027-M')");
            ScholarshipScopeSource roster=selected->{assertThat(selected).isEqualTo(term);return new ScholarshipScopeSource.Catalog(revision,List.of(new ScholarshipScopeSource.Group(a,"SYN-A",major,"SYN-M",null,10),new ScholarshipScopeSource.Group(b,"SYN-B",major,"SYN-M",null,10)));};
            var repo=new RoundRepository(em,jdbc);var rounds=new RoundService(repo,new FoundationRepository(em,jdbc),json,Clock.systemUTC());
            var service=new ScopeService(repo,rounds,roster,new ScholarshipConfiguration().scholarshipCompletedAssessments(),List.of(),json,Clock.systemUTC());
            return new Fixture(term,a,b,major,new RoundAccess.Actor(first,null,role),new RoundAccess.Actor(second,null,role),service);
        });
    }
    List<Outcome> race(Supplier<Object> first,Supplier<Object> second) throws Exception {
        var barrier=new CyclicBarrier(2);var sessions=ConcurrentHashMap.<Integer>newKeySet();
        var one=workers.submit(()->invoke(first,barrier,sessions));var two=workers.submit(()->invoke(second,barrier,sessions));
        var results=List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        assertThat(sessions).as("distinct real SQL Server sessions").hasSize(2);return results;
    }
    Outcome invoke(Supplier<Object> action,CyclicBarrier barrier,Set<Integer> sessions) {
        try {return tx.execute(status->{
            sessions.add(jdbc.queryForObject("SELECT @@SPID",Integer.class));
            try{barrier.await(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
            return new Outcome(action.get(),null);
        });}catch(RoundFailure e){return new Outcome(null,e);}
    }
    ScopeRules.Create command(Fixture f,String mode,List<ScopeRules.Pair> pairs){return new ScopeRules.Create(f.source(),revision,new ScopeRules.Selection(mode,pairs));}
    long insert(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    long auditCount(Fixture f){return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id IN (?,?)",Long.class,f.first().userId(),f.second().userId());}
    void assertRoundCount(Fixture f,long count){assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.kkht_dot_xet r JOIN dbo.hb_anh_xa_ky_nguon m ON m.id=r.anh_xa_ky_nguon_id WHERE m.he_thong_nguon=N'CTSV' AND m.loai_ma=N'ctsv_hoc_ky_id' AND m.ma_ky_nguon=?",Long.class,""+f.source())).isEqualTo(count);}
    void assertOneFailure(List<Outcome> results,int status,String code){assertThat(results).filteredOn(o->o.error()!=null).singleElement().satisfies(o->{assertThat(o.error().status).isEqualTo(status);assertThat(o.error().code).isEqualTo(code);});assertThat(results).filteredOn(o->o.error()==null).hasSize(1);}
    record Fixture(long source,long cohortA,long cohortB,long major,RoundAccess.Actor first,RoundAccess.Actor second,ScopeService service){}
    record Outcome(Object value,RoundFailure error){}
}
