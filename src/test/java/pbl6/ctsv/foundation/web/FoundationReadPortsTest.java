package pbl6.ctsv.foundation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.drl.reference.DrlReferenceData;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.AcademicSemesterSource.Page;
import pbl6.ctsv.foundation.api.AcademicSemesterSource.Semester;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess.ActiveRole;
import pbl6.ctsv.foundation.api.FoundationUsage;
import pbl6.ctsv.foundation.api.StaffReferenceSource;
import pbl6.ctsv.foundation.api.StudentReferenceSource;
import pbl6.ctsv.foundation.entity.HocKy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoundationReadPortsTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EntityManager em = mock(EntityManager.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(FoundationConfiguration.class)
        .withBean(AuthAccess.class, () -> mock(AuthAccess.class))
        .withBean(JdbcTemplate.class, () -> jdbc)
        .withBean(EntityManager.class, () -> em)
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(FoundationUsage.class, () -> mock(FoundationUsage.class))
        .withPropertyValues("ctsv.foundation.enabled=true", "ctsv.auth.origins[0]=https://ctsv.test");

    @BeforeEach
    void openCallerTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void closeCallerTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishesReadPortsAndWiresDrlReferenceData() {
        context.withUserConfiguration(DrlReferenceData.class).run(application -> assertThat(application)
            .hasSingleBean(FoundationRoleAccess.class)
            .hasSingleBean(AcademicSemesterSource.class)
            .hasSingleBean(StaffReferenceSource.class)
            .hasSingleBean(StudentReferenceSource.class)
            .hasSingleBean(DrlReferenceData.class));
    }

    @Test
    void roleLookupBindsScopeAndReturnsLocalRoleId() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(72L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ActiveRole>>any(),
                eq(301L), eq("HOC_BONG"), eq("hoc_bong_chuyen_vien")))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                assertThat(sql).contains("r.id", "p.nguoi_dung_id=?", "p.ma_phan_he=?", "r.ma=?",
                    "r.dang_hoat_dong=1", "p.don_vi_id IS NULL", "p.hieu_luc_tu<=",
                    "p.hieu_luc_den>", "SYSUTCDATETIME()", "WITH(UPDLOCK,HOLDLOCK)");
                RowMapper<ActiveRole> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(row, 0));
            });

        context.run(application -> {
            var roles = application.getBean(FoundationRoleAccess.class);
            assertThat(roles.findActiveGlobalRole(301L, "HOC_BONG", "hoc_bong_chuyen_vien"))
                .contains(new ActiveRole(72L));
            assertThat(roles.findActiveGlobalRole(302L, "HOC_BONG", "hoc_bong_chuyen_vien")).isEmpty();
        });
    }

    @Test
    void semesterPageEscapesLiteralSearchAndCountsBeforePaging() {
        TypedQuery<Object[]> pageQuery = mock();
        TypedQuery<Long> countQuery = mock();
        when(em.createQuery(anyString(), eq(HocKy.class)))
            .thenThrow(new AssertionError("The source page must select its whitelist without loading HocKy entities"));
        when(em.createQuery(anyString(), eq(Object[].class))).thenAnswer(invocation -> {
            assertThat(invocation.<String>getArgument(0).replaceAll("\\s+", " "))
                .startsWith("select h.id, h.ma, h.ten, h.namHocNguon, h.ngayBatDau, h.ngayKetThuc, "
                    + "h.trangThaiXacMinhMoc, h.dangHienDienNguon, h.phienBan from HocKy h")
                .contains("h.ma like :pattern escape '~'", "h.ten like :pattern escape '~'", "order by h.ma,h.id");
            return pageQuery;
        });
        when(em.createQuery(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            assertThat(invocation.<String>getArgument(0))
                .contains("count(h)", "h.ma like :pattern escape '~'", "h.ten like :pattern escape '~'")
                .doesNotContain("order by");
            return countQuery;
        });
        when(pageQuery.setParameter(anyString(), any())).thenReturn(pageQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(pageQuery.setFirstResult(4)).thenReturn(pageQuery);
        when(pageQuery.setMaxResults(2)).thenReturn(pageQuery);
        when(countQuery.getSingleResult()).thenReturn(7L);
        Object[] semester = {21L, "2510", null, null, LocalDate.of(2025, 9, 1), null,
            "verified", false, new byte[] {0, 0, 0, 0, 0, 0, 0, 5}};
        when(pageQuery.getResultList()).thenReturn(List.<Object[]>of(semester));

        context.run(application -> {
            Page result = application.getBean(AcademicSemesterSource.class).findPage("Kỳ~%_['", 2, 2);
            assertThat(result.page()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(7);
            assertThat(result.items()).singleElement().satisfies(item -> {
                assertThat(item.id()).isEqualTo(21L);
                assertThat(item.code()).isEqualTo("2510");
                assertThat(item.name()).isNull();
                assertThat(item.sourceAcademicYear()).isNull();
                assertThat(item.startDate()).isEqualTo(LocalDate.of(2025, 9, 1));
                assertThat(item.endDate()).isNull();
                assertThat(item.milestoneVerificationStatus()).isEqualTo("verified");
                assertThat(item.present()).isFalse();
                assertThat(item.version()).containsExactly(0, 0, 0, 0, 0, 0, 0, 5);
            });
        });
        verify(pageQuery).setParameter("pattern", "%Kỳ~~~%~_~['%");
        verify(countQuery).setParameter("pattern", "%Kỳ~~~%~_~['%");
        verify(pageQuery).setFirstResult(4);
        verify(pageQuery).setMaxResults(2);
    }

    @Test
    void datedSemesterCandidatesBindCutoffAndPageTheFullProjectionByStartDate() {
        TypedQuery<Object[]> pageQuery = mock();
        TypedQuery<Long> countQuery = mock();
        var cutoff = LocalDate.of(2026,11,20);
        when(em.createQuery(anyString(), eq(Object[].class))).thenAnswer(invocation -> {
            assertThat(invocation.<String>getArgument(0).replaceAll("\\s+", " "))
                .contains("h.id, h.ma, h.ten, h.namHocNguon, h.ngayBatDau, h.ngayKetThuc",
                    "h.trangThaiXacMinhMoc, h.dangHienDienNguon, h.phienBan",
                    "where h.ngayBatDau<=:cutoff", "order by h.ngayBatDau desc,h.ma,h.id")
                .doesNotContain("soThuTu", "select h from");
            return pageQuery;
        });
        when(em.createQuery(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            assertThat(invocation.<String>getArgument(0))
                .contains("count(h)","where h.ngayBatDau<=:cutoff").doesNotContain("order by");
            return countQuery;
        });
        when(pageQuery.setParameter("cutoff",cutoff)).thenReturn(pageQuery);
        when(countQuery.setParameter("cutoff",cutoff)).thenReturn(countQuery);
        when(pageQuery.setFirstResult(4)).thenReturn(pageQuery);
        when(pageQuery.setMaxResults(2)).thenReturn(pageQuery);
        when(countQuery.getSingleResult()).thenReturn(7L);
        Object[] row={21L,"2610",null,"raw-year",LocalDate.of(2026,9,1),null,"verified",false,new byte[8]};
        when(pageQuery.getResultList()).thenReturn(List.<Object[]>of(row));
        context.run(application -> {
            var result=application.getBean(AcademicSemesterSource.class).findStartedPage(cutoff,2,2);
            assertThat(result.page()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(7);
            assertThat(result.items()).singleElement().satisfies(value -> {
                assertThat(value.id()).isEqualTo(21);
                assertThat(value.code()).isEqualTo("2610");
                assertThat(value.name()).isNull();
                assertThat(value.sourceAcademicYear()).isEqualTo("raw-year");
                assertThat(value.startDate()).isEqualTo(LocalDate.of(2026,9,1));
                assertThat(value.endDate()).isNull();
                assertThat(value.milestoneVerificationStatus()).isEqualTo("verified");
                assertThat(value.present()).isFalse();
                assertThat(value.version()).hasSize(8);
            });
        });
    }

    @Test
    void sourceLookupReadsAndLocksOneWhitelistedSnapshot() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(23L);
        when(row.getString("ma")).thenReturn("2520");
        when(row.getString("ten")).thenReturn("Học kỳ 2");
        when(row.getString("nam_hoc_nguon")).thenReturn("Raw source year");
        when(row.getDate("ngay_bat_dau")).thenReturn(Date.valueOf("2026-01-15"));
        when(row.getString("trang_thai_xac_minh_moc")).thenReturn("verified");
        when(row.getBoolean("dang_hien_dien_nguon")).thenReturn(true);
        when(row.getBytes("phien_ban")).thenReturn(new byte[] {0, 0, 0, 0, 0, 0, 0, 6});
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Semester>>any(), eq(23L)))
            .thenAnswer(invocation -> {
                assertThat(invocation.<String>getArgument(0))
                    .contains("dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK)", "WHERE id=?")
                    .doesNotContain("so_thu_tu", "*");
                RowMapper<Semester> mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(row, 0));
            });

        context.run(application -> {
            var source = application.getBean(AcademicSemesterSource.class);
            assertThat(source.findById(23L)).hasValueSatisfying(item -> {
                assertThat(item.id()).isEqualTo(23L);
                assertThat(item.code()).isEqualTo("2520");
                assertThat(item.name()).isEqualTo("Học kỳ 2");
                assertThat(item.sourceAcademicYear()).isEqualTo("Raw source year");
                assertThat(item.startDate()).isEqualTo(LocalDate.of(2026, 1, 15));
                assertThat(item.endDate()).isNull();
                assertThat(item.milestoneVerificationStatus()).isEqualTo("verified");
                assertThat(item.present()).isTrue();
                assertThat(item.version()).containsExactly(0, 0, 0, 0, 0, 0, 0, 6);
            });
            assertThat(source.findById(999L)).isEmpty();
        });
        var order = inOrder(em, jdbc);
        order.verify(em).flush();
        order.verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Semester>>any(), eq(23L));
    }

    @Test
    void rejectsUseOutsideCallerTransactionBeforeAnyQuery() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        context.run(application -> {
            var roles = application.getBean(FoundationRoleAccess.class);
            var source = application.getBean(AcademicSemesterSource.class);
            clearInvocations(em, jdbc);
            assertThatIllegalStateException().isThrownBy(() -> roles.findActiveGlobalRole(301L, "HOC_BONG", "hoc_bong_chuyen_vien"));
            assertThatIllegalStateException().isThrownBy(() -> source.findPage(null, 0, 20));
            assertThatIllegalStateException().isThrownBy(() -> source.findStartedPage(LocalDate.of(2026,11,20),0,20));
            assertThatIllegalStateException().isThrownBy(() -> source.findById(23L));
            verifyNoInteractions(em, jdbc);
        });
    }

    @Test
    void projectionKeepsItsOwnVersionAndPageItems() {
        byte[] version = {0, 0, 0, 0, 0, 0, 0, 9};
        var source = new Semester(21L, "2510", null, null, null, null, null, false, version);
        var items = new ArrayList<>(List.of(source));
        var page = new Page(items, 0, 20, 1);
        version[7] = 3;
        source.version()[7] = 4;
        items.clear();

        assertThat(page.items()).containsExactly(source);
        assertThat(source.version()).containsExactly(0, 0, 0, 0, 0, 0, 0, 9);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
