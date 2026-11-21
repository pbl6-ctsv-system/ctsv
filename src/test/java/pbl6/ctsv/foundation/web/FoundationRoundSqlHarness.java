package pbl6.ctsv.foundation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import pbl6.ctsv.foundation.api.FoundationUsage;

/** Test-only bridge to the package-private Foundation service and its production usage aggregation. */
public final class FoundationRoundSqlHarness {
    private final FoundationSecurity security;
    private final FoundationService service;
    private final TransactionTemplate transaction;
    private final AcademicSemesterSource source;
    private final ObjectMapper json;

    public FoundationRoundSqlHarness(EntityManager em, JdbcTemplate jdbc, PlatformTransactionManager manager,
            ObjectMapper json, AuthAccess auth, List<FoundationUsage> usages) {
        var configuration = new FoundationConfiguration();
        var repository = configuration.foundationRepository(em, jdbc);
        security = configuration.foundationSecurity(auth, jdbc);
        service = configuration.foundationService(security, repository, manager, json, usages);
        transaction = new TransactionTemplate(manager);
        source = repository;
        this.json = json;
    }

    public FoundationAccess identities() { return security; }
    public FoundationRoleAccess roles() { return security; }
    public AcademicSemesterSource source() { return source; }

    public String semesterTag(long id) {
        return transaction.execute(status -> service.semester(id).tag());
    }

    public void verifyDates(long actor, long id, int order, String tag, LocalDate start, LocalDate end) {
        var body = json.createObjectNode().put("order", order).put("startDate", start.toString())
                .put("endDate", end.toString()).put("evidence", "SYN-HB-T01");
        transaction.executeWithoutResult(status -> service.verifySemester(actor, id, tag, body));
    }
}
