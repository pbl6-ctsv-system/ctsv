package pbl6.ctsv.foundation.api;
/** Read-only dependency port: business modules protect facts they have already frozen. */
public interface FoundationUsage {
 boolean semesterUsed(long id);
 boolean processUsed(long id);
}
