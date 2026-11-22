package pbl6.ctsv.auth;
import java.util.Set;
/** Narrow read boundary for the Foundation source-profile picker. Never grants CTSV business access. */
public interface AuthDirectoryAccess {
 Set<String> profileSources(AuthAccess.Principal actor);
}
