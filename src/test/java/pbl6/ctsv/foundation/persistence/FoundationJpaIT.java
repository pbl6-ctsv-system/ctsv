package pbl6.ctsv.foundation.persistence;

import jakarta.persistence.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.entity.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false"})
@Import(FoundationJpaIT.Database.class)
class FoundationJpaIT {
    @TestConfiguration(proxyBeanMethods=false) static class Database {
        @Bean DataSource refactorSource() throws Exception { return RefactorDatabase.source(); }
    }
    @Autowired EntityManagerFactory factory;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @Test void allMappingsValidateAndEveryMappedTableCanBeRead() {
        assertThat(factory.getMetamodel().getEntities()).hasSize(38);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            for (var entity : factory.getMetamodel().getEntities())
                assertThat(em.createQuery("select e from " + entity.getName() + " e").setMaxResults(1).getResultList()).isNotNull();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(8);
    }

    @Test void jpaAndJdbcShareCommitRollbackAndRefreshAfterNativeMutation() {
        assertThat(manager).isInstanceOf(JpaTransactionManager.class);
        String code = "SYNTHETIC-JPA-" + java.util.UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var h = new HocKy(); h.setMa(code); h.setTen("Học kỳ giả lập");
            em.persist(h); em.flush(); em.refresh(h);
            assertThat(h.getPhienBan()).hasSize(8);
            assertThat(jdbc.queryForObject("SELECT ten FROM dbo.hoc_ky WHERE id=?",String.class,h.getId())).isEqualTo("Học kỳ giả lập");
            jdbc.update("UPDATE dbo.hoc_ky SET ten=N'JDBC mới' WHERE id=?",h.getId());
            em.refresh(h);
            assertThat(h.getTen()).isEqualTo("JDBC mới");
            tx.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WHERE ma=?",Integer.class,code)).isZero();
    }

    @Test void entityRelationsUseLocalIdentityAndDecimalDateRoundTrip() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var ethnicity = new DanToc(); ethnicity.setMa("SYNTHETIC-"+java.util.UUID.randomUUID()); em.persist(ethnicity);
            var student = new SinhVien(); student.setMssv("SYNTHETIC-"+java.util.UUID.randomUUID());
            student.setDanTocId(ethnicity.getId()); student.setHoTen("Sinh viên giả lập");
            student.setNgaySinh(java.time.LocalDate.of(2000,2,29)); em.persist(student); em.flush(); em.clear();
            var loaded = em.find(SinhVien.class,student.getId());
            assertThat(factory.getPersistenceUnitUtil().isLoaded(loaded,"danToc")).isFalse();
            assertThat(loaded.getDanToc().getId()).isEqualTo(ethnicity.getId());
            assertThat(loaded.getNgaySinh()).isEqualTo(java.time.LocalDate.of(2000,2,29));
            tx.setRollbackOnly();
        });
    }

    @Test void aNewRoleAssignmentCanBeProjectedImmediatelyInTheSameTransaction() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var user=new NguoiDung();user.setDiaChiEmail("SYNTHETIC-"+java.util.UUID.randomUUID()+"@example.invalid");em.persist(user);
            var role=em.createQuery("from VaiTro where ma='mghp_chuyen_vien'",VaiTro.class).getSingleResult();
            var assignment=new PhanQuyenNguoiDung();assignment.setNguoiDungId(user.getId());assignment.setVaiTroId(role.getId());assignment.setMaPhanHe("MGHP");
            var repository=new pbl6.ctsv.foundation.repository.FoundationRepository(em,jdbc);repository.save(assignment);
            var rows=repository.roles(user.getId(),true);
            assertThat(rows).hasSize(1);
            assertThat(pbl6.ctsv.foundation.mapper.FoundationMapper.role(rows.getFirst()).get("roleCode")).isEqualTo("mghp_chuyen_vien");
            tx.setRollbackOnly();
        });
    }

    @Test void mappedColumnsMatchSqlNullabilityLengthPrecisionAndIdentity() {
        for(var entity:factory.getMetamodel().getEntities()) {
            Class<?> type=entity.getJavaType();String table=type.getAnnotation(Table.class).name();
            var columns=jdbc.queryForList("SELECT c.name,c.is_nullable,c.max_length,c.precision,c.scale,c.is_identity,t.name sql_type FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.object_id=OBJECT_ID(?)","dbo."+table);
            var names=new java.util.HashSet<String>();
            for(var f:type.getDeclaredFields()) {
                var column=f.getAnnotation(Column.class);if(column==null)continue;names.add(column.name());
                var actual=columns.stream().filter(c -> column.name().equals(c.get("name"))).findFirst().orElseThrow();
                assertThat(actual.get("is_nullable")).as(table+"."+column.name()).isEqualTo(column.nullable());
                assertThat(actual.get("is_identity")).as(table+"."+column.name()).isEqualTo(f.isAnnotationPresent(GeneratedValue.class));
                if(f.getType()==String.class&&!column.columnDefinition().contains("(max)")) {
                    int bytes=((Number)actual.get("max_length")).intValue();
                    assertThat(bytes).as(table+"."+column.name()).isEqualTo(column.length()*(f.isAnnotationPresent(org.hibernate.annotations.Nationalized.class)?2:1));
                }
                if(f.getType()==java.math.BigDecimal.class) {
                    assertThat(((Number)actual.get("precision")).intValue()).isEqualTo(column.precision());
                    assertThat(((Number)actual.get("scale")).intValue()).isEqualTo(column.scale());
                }
            }
            assertThat(names).as("Complete column mapping for "+table).containsExactlyInAnyOrderElementsOf(columns.stream().map(c->c.get("name").toString()).toList());
        }
    }

    @Test void dynamicUpdatesDoNotOverwriteSourceColumnsAndDecimalIsLossless() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var h=new HocKy();h.setMa("SYNTHETIC-"+java.util.UUID.randomUUID());h.setNgayDayNguon("raw-before");em.persist(h);em.flush();
            jdbc.update("UPDATE dbo.hoc_ky SET ngay_day_nguon=N'raw-after' WHERE id=?",h.getId());
            h.setCanCuXacMinhMoc("SYNTHETIC local evidence");em.flush();em.refresh(h);
            assertThat(h.getNgayDayNguon()).isEqualTo("raw-after");
            var s=new SinhVien();s.setMssv("SYNTHETIC-"+java.util.UUID.randomUUID());em.persist(s);
            var v=new DuLieuHocVuDongBo();v.setSinhVienId(s.getId());v.setTcTyLe(new java.math.BigDecimal("123456789012345678.1234567890"));em.persist(v);em.flush();em.refresh(v);
            assertThat(v.getTcTyLe()).isEqualTo(new java.math.BigDecimal("123456789012345678.1234567890"));
            tx.setRollbackOnly();
        });
    }
}
