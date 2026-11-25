package pbl6.ctsv.foundation.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentMatchers;
import pbl6.ctsv.foundation.api.FoundationAccess.Failure;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource.Kind;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource.Option;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource.Resolved;
import pbl6.ctsv.foundation.repository.ScholarshipCombinationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** HB-COMB-01. Provider logic with mocked persistence; SQL and locking are verified separately. */
class ScholarshipCombinationSourceTest {
    private final EntityManager em = mock();
    private final JdbcTemplate jdbc = mock();
    private final TypedQuery<Object[]> options = mock();
    private final TypedQuery<Long> count = mock();
    private final ScholarshipCombinationRepository source = new ScholarshipCombinationRepository(em, jdbc);

    @BeforeEach
    void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void frameworkLookupUsesDistinctSourceCodeAndLiteralSearch() {
        pageRows(1, new Object[]{"KT%_~[01", "KT%_~[01", null, null});

        var page = source.findPage(Kind.CURRICULA, " %_~[ ", 2, 5);

        assertThat(page.items()).containsExactly(new Option("KT%_~[01", "KT%_~[01", null, null));
        assertThat(page.total()).isEqualTo(1);
        verify(em).createQuery(argThat(query -> query.contains("select distinct e.maKhungNguon")
            && query.contains("from KhungChuongTrinh") && query.contains("e.dangHienDienNguon=true")
            && query.endsWith("order by e.maKhungNguon")), eq(Object[].class));
        verify(em).createQuery(argThat(query -> query.contains("count(distinct e.maKhungNguon)")), eq(Long.class));
        verify(options).setParameter("pattern", "%~%~_~~~[%");
        verify(count).setParameter("pattern", "%~%~_~~~[%");
        verify(options).setFirstResult(10);
        verify(options).setMaxResults(5);
    }

    @Test
    void programLookupPreservesBigintIdsAndReturnsTheProgramsMajor() {
        pageRows(3, new Object[]{9_007_199_254_740_993L, "KS-CNTT", "Kỹ sư CNTT", 42L});

        var page = source.findPage(Kind.PROGRAMS, null, 0, 20);

        assertThat(page.items()).containsExactly(new Option("9007199254740993", "KS-CNTT", "Kỹ sư CNTT", "42"));
        assertThat(page.total()).isEqualTo(3);
        verify(em).createQuery(argThat(query -> query.contains("from ChuongTrinhDaoTao")
            && query.contains("e.nganhNoiBoId") && query.endsWith("order by e.ma,e.id")), eq(Object[].class));
    }

    @Test
    void lookupUsesTheHttpUnicodeCodePointLimit() {
        String letter = new String(Character.toChars(0x10400));
        String maxSearch = letter.repeat(128);
        pageRows(1, new Object[]{42L, "CNTT", "Công nghệ thông tin", null});

        assertThat(source.findPage(Kind.MAJORS, maxSearch, 0, 20).total()).isEqualTo(1);
        verify(options).setParameter("pattern", "%" + maxSearch + "%");
        assertThatThrownBy(() -> source.findPage(Kind.MAJORS, maxSearch + letter, 0, 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lookupRejectsUnboundedPagingBeforeQuerying() {
        assertThatThrownBy(() -> source.findPage(Kind.MAJORS, null, Integer.MAX_VALUE, 100))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source.findPage(Kind.COHORTS, null, 0, 101))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(em, jdbc);
    }

    @Test
    void resolvesLocalRowsAndAnIndependentlySelectedFramework() throws SQLException {
        program("KS-CNTT", "Kỹ sư CNTT", 42L);
        sourceCode("khoa_hoc", 32L, "K24");
        sourceCode("nganh_noi_bo", 42L, "CNTT");
        sourceCode("khung_chuong_trinh", "K2024-7", "K2024-7");

        assertThat(source.resolve(22L, 32L, 42L, "K2024-7"))
            .isEqualTo(new Resolved(22L, "KS-CNTT", "Kỹ sư CNTT", 32L, "K24", 42L, "CNTT", "K2024-7"));
        verify(em).flush();
    }

    @Test
    void rejectsAMajorThatTheProgramDoesNotBelongTo() throws SQLException {
        program("KS-CNTT", null, 43L);
        sourceCode("khoa_hoc", 32L, "K24");
        sourceCode("nganh_noi_bo", 42L, "CNTT");
        sourceCode("khung_chuong_trinh", "K2024-7", "K2024-7");

        assertInvalidSource("majorId");
    }

    @Test
    void rejectsMissingOrBlankSourceKeysWithTheFieldThatFailed() throws SQLException {
        assertInvalidSource("programId");
        program("KS-CNTT", null, 42L);
        assertInvalidSource("cohortId");
        sourceCode("khoa_hoc", 32L, "K24");
        sourceCode("nganh_noi_bo", 42L, "\t");
        assertInvalidSource("majorId");
        sourceCode("nganh_noi_bo", 42L, "CNTT");
        assertInvalidSource("curriculumCode");
    }

    @Test
    void databaseFailureReachesCallerInsteadOfReturningAnEmptyPage() {
        var failure = new DataAccessResourceFailureException("synthetic source failure");
        when(em.createQuery(anyString(), eq(Object[].class))).thenThrow(failure);

        assertThatThrownBy(() -> source.findPage(Kind.COHORTS, null, 0, 20)).isSameAs(failure);
    }

    @Test
    void queriesRequireACallersTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> source.findPage(Kind.PROGRAMS, null, 0, 20))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> source.resolve(22L, 32L, 42L, "K2024-7"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(em, jdbc);
    }

    private void pageRows(long total, Object[] row) {
        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(options);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        when(options.setFirstResult(anyInt())).thenReturn(options);
        when(options.setMaxResults(anyInt())).thenReturn(options);
        when(options.getResultList()).thenReturn(List.<Object[]>of(row));
        when(count.getSingleResult()).thenReturn(total);
    }

    private void program(String code, String name, Long majorId) throws SQLException {
        ResultSet row = mock();
        when(row.getString("ma")).thenReturn(code);
        when(row.getString("ten")).thenReturn(name);
        when(row.getObject("nganh_noi_bo_id", Long.class)).thenReturn(majorId);
        when(jdbc.query(argThat(query -> locksPresentSource(query, "chuong_trinh_dao_tao")),
                ArgumentMatchers.<RowMapper<Object>>any(), eq(22L)))
            .thenAnswer(call -> List.of(call.<RowMapper<Object>>getArgument(1).mapRow(row, 0)));
    }

    private void sourceCode(String table, Object key, String code) {
        when(jdbc.queryForList(argThat(query -> locksPresentSource(query, table)), eq(String.class), eq(key)))
            .thenReturn(List.of(code));
    }

    private static boolean locksPresentSource(String query, String table) {
        return query != null && query.contains("FROM dbo." + table + " WITH(HOLDLOCK)")
            && query.contains("dang_hien_dien_nguon=1");
    }

    private void assertInvalidSource(String field) {
        assertThatThrownBy(() -> source.resolve(22L, 32L, 42L, "K2024-7"))
            .isInstanceOfSatisfying(Failure.class, failure -> {
                assertThat(failure.status()).isEqualTo(400);
                assertThat(failure.code()).isEqualTo("INVALID_COMBINATION_SOURCE");
                assertThat(failure.field()).isEqualTo(field);
            });
    }
}
