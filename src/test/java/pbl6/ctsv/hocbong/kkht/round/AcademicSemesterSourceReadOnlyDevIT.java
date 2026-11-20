package pbl6.ctsv.hocbong.kkht.round;

import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import pbl6.ctsv.foundation.repository.FoundationRepository;
import pbl6.ctsv.auth.AuthAccess;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in, fixture-free DEV read smoke. No migration, seed, persist, reset or cleanup SQL. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcademicSemesterSourceReadOnlyDevIT {
    private LocalContainerEntityManagerFactoryBean factory;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private AcademicSemesterSource source;
    private JpaTransactionManager manager;

    @BeforeAll void verifyOwnerAndOpenReadOnlyPersistence() throws Exception {
        if (!Boolean.getBoolean("hocbong.dev.readOnly"))
            throw new IllegalStateException("Explicit -Dhocbong.dev.readOnly=true is required; this is not the fixture SQL suite");
        String port=System.getenv("P00_SQL_PORT");
        if (port==null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port)>65535)
            throw new IllegalStateException("Verified DEV2022 P00_SQL_PORT is required");
        var properties=new Properties();
        properties.setProperty("integratedSecurity","true");properties.setProperty("encrypt","true");
        properties.setProperty("trustServerCertificate","true");
        DataSource dataSource=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:"+port+";databaseName=ctsv_dev;loginTimeout=5;socketTimeout=30000",properties);
        try(var connection=dataSource.getConnection();var statement=connection.createStatement();
                var result=statement.executeQuery("""
                    SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),
                           CASE WHEN SUSER_SNAME(owner_sid)=ORIGINAL_LOGIN() THEN 1 ELSE 0 END,
                           compatibility_level,collation_name
                    FROM sys.databases WHERE database_id=DB_ID()
                    """)) {
            if (!result.next() || !"ctsv_dev".equals(result.getString(1)) || !"DEV2022".equals(result.getString(2))
                    || result.getInt(3)!=1 || result.getInt(4)!=160 || !"Vietnamese_100_CI_AS_SC".equals(result.getString(5)))
                throw new IllegalStateException("Unexpected DEV database/instance/owner/compatibility/collation");
        }
        jdbc=new JdbcTemplate(dataSource);
        factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("pbl6.ctsv.foundation","pbl6.ctsv.hocbong.kkht.round.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto","validate");
        // All CTSV tables live in dbo; avoid scanning unrelated/system schemas for metadata.
        factory.getJpaPropertyMap().put("hibernate.default_schema","dbo");
        factory.afterPropertiesSet();
        manager=new JpaTransactionManager(Objects.requireNonNull(factory.getObject()));
        manager.setDataSource(dataSource);manager.afterPropertiesSet();
        EntityManager em=SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        source=new FoundationRepository(em,jdbc);
        tx=new TransactionTemplate(manager);tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    @AfterAll void closePersistenceWithoutCleaningData() {
        if (factory!=null) factory.destroy();
    }

    @Test void candidatePagesMatchLiveSourceDatesWithoutDependingOnFixedIdsOrRows() {
        tx.executeWithoutResult(status -> {
            var dates=jdbc.query("SELECT id,ngay_bat_dau FROM dbo.hoc_ky WHERE ngay_bat_dau IS NOT NULL",
                (row,index)->Map.entry(row.getLong(1),row.getDate(2).toLocalDate()));
            assertThat(dates).as("DEV needs existing dated source rows to exercise nonempty paging").isNotEmpty();
            var min=dates.stream().map(Map.Entry::getValue).min(LocalDate::compareTo).orElseThrow();
            var max=dates.stream().map(Map.Entry::getValue).max(LocalDate::compareTo).orElseThrow();
            for(var cutoff:List.of(LocalDate.of(2026,11,20),min.minusDays(1),max)) {
                var expectedIds=dates.stream().filter(row->!row.getValue().isAfter(cutoff)).map(Map.Entry::getKey).toList();
                var actualIds=new ArrayList<Long>();
                LocalDate previous=null;
                for(int page=0;;page++) {
                    var result=source.findStartedPage(cutoff,page,2);
                    assertThat(result.totalElements()).isEqualTo(expectedIds.size());
                    assertThat(result.items()).hasSizeLessThanOrEqualTo(2);
                    for(var term:result.items()) {
                        assertThat(term.startDate()).isNotNull().isBeforeOrEqualTo(cutoff);
                        if(previous!=null) assertThat(term.startDate()).isBeforeOrEqualTo(previous);
                        previous=term.startDate();actualIds.add(term.id());
                    }
                    if(((long)page+1)*2>=result.totalElements()) break;
                    assertThat(result.items()).as("source paging must make progress").isNotEmpty();
                }
                assertThat(actualIds).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expectedIds);
            }
        });
    }

    @Test void directSnapshotMatchesIndependentJdbcFieldsIncludingNullableDataAndRowversion() {
        tx.executeWithoutResult(status -> {
            var ids=jdbc.queryForList("SELECT id FROM dbo.hoc_ky ORDER BY id",Long.class);
            assertThat(ids).as("DEV needs existing source rows for a positive direct-read smoke").isNotEmpty();
            for(long id:ids) {
                jdbc.query("SELECT id,ma,ten,nam_hoc_nguon,ngay_bat_dau,ngay_ket_thuc,"
                        +"trang_thai_xac_minh_moc,dang_hien_dien_nguon,phien_ban FROM dbo.hoc_ky WHERE id=?",row -> {
                    var actual=source.findById(id).orElseThrow();
                    assertThat(actual.id()).isEqualTo(row.getLong(1));
                    assertThat(actual.code()).isEqualTo(row.getString(2));
                    assertThat(actual.name()).isEqualTo(row.getString(3));
                    assertThat(actual.sourceAcademicYear()).isEqualTo(row.getString(4));
                    assertThat(actual.startDate()).isEqualTo(date(row.getDate(5)));
                    assertThat(actual.endDate()).isEqualTo(date(row.getDate(6)));
                    assertThat(actual.milestoneVerificationStatus()).isEqualTo(row.getString(7));
                    assertThat(actual.present()).isEqualTo(row.getBoolean(8));
                    assertThat(actual.version()).containsExactly(row.getBytes(9));
                },id);
            }
        });
    }

    @Test void productionDefaultReadBoundaryRetainsSourceLocksAcrossPages() {
        var identities=mock(FoundationAccess.class);
        var roles=mock(FoundationRoleAccess.class);
        var principal=new AuthAccess.Principal("synthetic-test",UUID.randomUUID(),1);
        when(identities.identify(principal,true)).thenReturn(new FoundationAccess.Identity(1,null,principal));
        when(roles.findActiveGlobalRole(1,"HOC_BONG","hoc_bong_chuyen_vien"))
            .thenReturn(Optional.of(new FoundationRoleAccess.ActiveRole(1)));
        new RoundAccess(manager,identities,roles).executeConsistentRead(principal,actor -> {
            assertThat(jdbc.queryForObject("SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id=@@SPID",
                Integer.class)).as("production default boundary must use SQL Server SERIALIZABLE").isEqualTo(4);
            var cutoff=jdbc.queryForObject("SELECT MAX(ngay_bat_dau) FROM dbo.hoc_ky",Date.class).toLocalDate();
            assertThat(source.findStartedPage(cutoff,0,1).items()).isNotEmpty();
            assertThat(source.findStartedPage(cutoff,1,1).items()).isNotEmpty();
            Integer retainedLocks=jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.dm_tran_locks l
                WHERE l.request_session_id=@@SPID AND l.resource_database_id=DB_ID()
                  AND l.request_status='GRANT' AND l.request_mode IN ('S','RangeS-S')
                  AND ((l.resource_type='OBJECT' AND l.resource_associated_entity_id=OBJECT_ID('dbo.hoc_ky'))
                    OR EXISTS (SELECT 1 FROM sys.partitions p WHERE p.object_id=OBJECT_ID('dbo.hoc_ky')
                        AND p.hobt_id=l.resource_associated_entity_id))
                """,Integer.class);
            assertThat(retainedLocks).as("source shared/range locks must survive both page reads until transaction end")
                .isPositive();
            return null;
        });
    }

    private static LocalDate date(Date value) { return value==null?null:value.toLocalDate(); }
}
