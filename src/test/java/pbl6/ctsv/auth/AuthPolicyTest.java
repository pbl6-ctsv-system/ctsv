package pbl6.ctsv.auth;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthPolicyTest {
    @Test void passwordAndUsernameLimitsAreCheckedBeforeHashing(){
        AuthCredentials.validatePassword("x".repeat(12),true);
        AuthCredentials.validatePassword("😀".repeat(1024),true);
        assertThatThrownBy(()->AuthCredentials.validatePassword("😀".repeat(1025),true)).isInstanceOf(AuthFailure.class);
        AuthCredentials.validatePassword("x",false);
        assertThatThrownBy(()->AuthCredentials.validatePassword("",false)).isInstanceOf(AuthFailure.class);
        assertThat(AuthCredentials.username("x".repeat(128)).canonical()).hasSize(128);
        assertThatThrownBy(()->AuthCredentials.username("x".repeat(129))).isInstanceOf(AuthFailure.class);
    }
    @Test void usernameUsesNfcAndRootCaseAndPreservesAccents() {
        assertThat(AuthCredentials.username("  TE\u0301st ").canonical()).isEqualTo("tést");
        assertThat(AuthCredentials.username("test").canonical()).isNotEqualTo("tést");
        assertThatThrownBy(() -> AuthCredentials.username("a\tb")).isInstanceOf(AuthFailure.class);
    }

    @Test void passwordsPreserveWhitespaceAndCountCodePoints() {
        var credentials = new AuthCredentials();
        String password = " 😀".repeat(6);
        String hash = credentials.hash(password);
        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(credentials.matches(password, hash)).isTrue();
        assertThat(credentials.matches(password.strip(), hash)).isFalse();
        assertThatThrownBy(() -> credentials.hash("😀".repeat(11))).isInstanceOf(AuthFailure.class);
    }

    @Test void rateLimitsPairAndIpAndResetsAtBoundary() {
        var limiter = new AuthLoginLimiter(true);
        Instant t = Instant.parse("2026-11-12T00:00:00Z");
        for (int i=0;i<5;i++) limiter.check("user", "192.0.2.10", t);
        assertThatThrownBy(() -> limiter.check("user", "192.0.2.10", t.plusMillis(1)))
            .isInstanceOfSatisfying(AuthFailure.class, f -> assertThat(f.retryAfter).isEqualTo(900));
        limiter.check("user", "192.0.2.10", t.plusSeconds(900));
        for (int i=0;i<30;i++) limiter.check("user"+i, "192.0.2.11", t);
        assertThatThrownBy(() -> limiter.check("other", "192.0.2.11", t)).isInstanceOf(AuthFailure.class);
    }
}
