package pbl6.ctsv.auth;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class AuthLoginLimiterConfigurationTest {
    private static final Instant NOW=Instant.parse("2026-11-21T00:00:00Z");

    @Test void allProfilesDisableBothBucketsByDefault() {
        assertUnlimited(new AuthLoginLimiter());
        for(String profile:new String[]{"dev","test","prod"}){
            var environment=new MockEnvironment();environment.setActiveProfiles(profile);
            assertUnlimited(new AuthConfiguration().authLoginLimiter(environment));
        }
        assertUnlimited(new AuthConfiguration().authLoginLimiter(new MockEnvironment()));
    }

    @Test void explicitFalseDisablesBothBucketsOutsideDevToo() {
        var environment=new MockEnvironment().withProperty("ctsv.auth.login-rate-limit-enabled","false");
        assertUnlimited(new AuthConfiguration().authLoginLimiter(environment));
        environment.setActiveProfiles("prod");
        assertUnlimited(new AuthConfiguration().authLoginLimiter(environment));
    }

    @Test void explicitTrueCanReenableTheExistingLimiter() {
        var environment=new MockEnvironment().withProperty("ctsv.auth.login-rate-limit-enabled","true");
        assertLimited(new AuthConfiguration().authLoginLimiter(environment));
    }

    private void assertUnlimited(AuthLoginLimiter limiter) {
        assertThatCode(()->{for(int i=0;i<40;i++)limiter.check("user","127.0.0.1",NOW);}).doesNotThrowAnyException();
    }

    private void assertLimited(AuthLoginLimiter limiter) {
        for(int i=0;i<5;i++)limiter.check("user","127.0.0.1",NOW);
        assertThatThrownBy(()->limiter.check("user","127.0.0.1",NOW))
            .isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(429));
    }
}
