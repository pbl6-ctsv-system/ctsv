package pbl6.ctsv.auth;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

final class AuthCredentials {
    record Username(String display, String canonical) {}
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16,32,1,19456,2);
    private final String dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());

    static Username username(String raw) {
        if (raw == null || raw.codePoints().anyMatch(c -> Character.isISOControl(c) || c >= 0xD800 && c <= 0xDFFF))
            throw AuthFailure.invalid();
        String display = Normalizer.normalize(raw, Normalizer.Form.NFC).replaceAll("^ +| +$", "");
        String canonical = Normalizer.normalize(display.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        if (canonical.isEmpty() || canonical.length() > 128 || display.length() > 128) throw AuthFailure.invalid();
        return new Username(display, canonical);
    }

    static void validatePassword(String raw, boolean newPassword) {
        int count = raw == null ? 0 : raw.codePointCount(0,raw.length());
        if (count < (newPassword ? 12 : 1) || count > 1024
            || raw.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF))
            throw new AuthFailure(400,newPassword ? "PASSWORD_INVALID" : "INVALID_REQUEST");
    }

    String hash(String raw) {
        validatePassword(raw,true);
        return encoder.encode(raw);
    }

    boolean matches(String raw,String storedHash) {
        if (storedHash != null && !storedHash.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}"))
            throw AuthFailure.unavailable();
        boolean matches = encoder.matches(raw, storedHash == null ? dummyHash : storedHash);
        return storedHash != null && matches;
    }
}
