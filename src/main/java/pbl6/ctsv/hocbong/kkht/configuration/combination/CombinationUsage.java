package pbl6.ctsv.hocbong.kkht.configuration.combination;

/** Future snapshot/run consumers must pin configuration under the same transaction write fence
 * (ctsv.hocbong.combinations) and implement this lookup before enabling their write paths. */
public interface CombinationUsage {
    boolean used(long combinationId);
}
