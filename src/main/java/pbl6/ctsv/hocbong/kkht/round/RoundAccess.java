package pbl6.ctsv.hocbong.kkht.round;

import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;

final class RoundAccess {
    private static final String MODULE = "HOC_BONG";
    private static final String SPECIALIST_ROLE = "hoc_bong_chuyen_vien";

    record Actor(long userId, Long studentId, Long roleId) {}

    private final TransactionTemplate transaction;
    private final TransactionTemplate consistentRead;
    private final FoundationAccess identities;
    private final FoundationRoleAccess roles;

    RoundAccess(PlatformTransactionManager manager, FoundationAccess identities, FoundationRoleAccess roles) {
        this.transaction = new TransactionTemplate(manager);
        this.consistentRead = new TransactionTemplate(manager);
        this.consistentRead.setReadOnly(true);
        this.consistentRead.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.identities = identities;
        this.roles = roles;
    }

    <T> T execute(AuthAccess.Principal principal, boolean specialistRequired, Function<Actor, T> action) {
        return execute(transaction, principal, specialistRequired, action);
    }

    <T> T executeConsistentRead(AuthAccess.Principal principal, Function<Actor, T> action) {
        return execute(consistentRead, principal, true, action);
    }

    private <T> T execute(TransactionTemplate template, AuthAccess.Principal principal,
                         boolean specialistRequired, Function<Actor, T> action) {
        return template.execute(status -> {
            var identity = identities.identify(principal, true);
            Long roleId = roles.findActiveGlobalRole(identity.userId(), MODULE, SPECIALIST_ROLE)
                    .map(FoundationRoleAccess.ActiveRole::roleId)
                    .orElse(null);
            if (specialistRequired && roleId == null) {
                throw new RoundFailure(403, "FORBIDDEN");
            }
            return action.apply(new Actor(identity.userId(), identity.studentId(), roleId));
        });
    }
}
