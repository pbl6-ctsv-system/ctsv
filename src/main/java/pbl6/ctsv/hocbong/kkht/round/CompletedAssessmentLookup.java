package pbl6.ctsv.hocbong.kkht.round;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

/** T06.2 must supply the latest effective full-pipeline run, excluding newer unfinished runs.
 * Both methods execute in the caller transaction and protect run/history reads until commit.
 * All assessment writers must use the ctsv.hocbong.kkht.rounds transaction applock (or equivalent
 * documented SQL fencing), not an eventually consistent/read-committed cache.
 */
public interface CompletedAssessmentLookup {
    record GroupKey(long cohortId,long majorId) {}
    record Proof(long roundId,long runId,long scopeVersionId,OffsetDateTime completedAt) {}
    Optional<Proof> latestCompleted(long roundId);
    /** Only groups in effective, fully completed runs, never superseded draft selections. */
    Set<GroupKey> assessedGroups(long semesterId);
}
