package pbl6.ctsv.shared.files;
import pbl6.ctsv.foundation.api.FoundationAccess;

/** Module-owned object authorization, evaluated inside the authenticated caller's SQL transaction. */
public interface FileReadPolicy {
 boolean canRead(long fileId,FoundationAccess.Identity identity);
}
