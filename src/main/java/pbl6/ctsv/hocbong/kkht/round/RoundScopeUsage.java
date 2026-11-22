package pbl6.ctsv.hocbong.kkht.round;

/** Future budget/snapshot/run consumers register real usage checks in the caller transaction.
 * Their writes acquire ctsv.hocbong.kkht.rounds transaction applock before root/source locks,
 * and protect usage through commit, so starting use cannot race with replacing the scope.
 */
public interface RoundScopeUsage {
    boolean used(long roundId,long scopeVersionId);
}
