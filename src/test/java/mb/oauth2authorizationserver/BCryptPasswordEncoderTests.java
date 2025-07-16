package mb.oauth2authorizationserver;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Slf4j
class BCryptPasswordEncoderTests {

    @Test
    void emptyRawPasswordDoesNotMatchPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String result = encoder.encode("password");
        assertThat(encoder.matches("", result)).isFalse();
    }

    @Test
    void $2yMatches() {
        // $2y is default version
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String result = encoder.encode("password");
        assertThat(result).isNotEqualTo("password");
        log.info("Encoded password: {}", result);
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void $2aMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A);
        String result = encoder.encode("password");
        assertThat(result).isNotEqualTo("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void $2bMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B);
        String result = encoder.encode("password");
        assertThat(result).isNotEqualTo("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void $2yUnicode() {
        // $2y is default version
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String result = encoder.encode("passw\u9292rd");
        assertThat(encoder.matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(encoder.matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2aUnicode() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A);
        String result = encoder.encode("passw\u9292rd");
        assertThat(encoder.matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(encoder.matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2bUnicode() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B);
        String result = encoder.encode("passw\u9292rd");
        assertThat(encoder.matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(encoder.matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2yNotMatches() {
        // $2y is default version
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String result = encoder.encode("password");
        assertThat(encoder.matches("bogus", result)).isFalse();
    }

    @Test
    void $2aNotMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A);
        String result = encoder.encode("password");
        assertThat(encoder.matches("bogus", result)).isFalse();
    }

    @Test
    void $2bNotMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B);
        String result = encoder.encode("password");
        assertThat(encoder.matches("bogus", result)).isFalse();
    }

    @Test
    void $2yCustomStrength() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(8);
        String result = encoder.encode("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void $2aCustomStrength() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A, 8);
        String result = encoder.encode("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void $2bCustomStrength() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 8);
        String result = encoder.encode("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @Test
    void badLowCustomStrength() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BCryptPasswordEncoder(3));
    }

    @Test
    void badHighCustomStrength() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BCryptPasswordEncoder(32));
    }

    @Test
    void customRandom() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(8, new SecureRandom());
        String result = encoder.encode("password");
        assertThat(encoder.matches("password", result)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"012345678901234567890123456789"})
    void doesntMatchInvalidEncodedValues(String encodedValue) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("password", encodedValue)).isFalse();
    }

    @Test
    void upgradeFromLowerStrength() {
        BCryptPasswordEncoder weakEncoder = new BCryptPasswordEncoder(5);
        BCryptPasswordEncoder strongEncoder = new BCryptPasswordEncoder(15);
        String weakPassword = weakEncoder.encode("password");
        String strongPassword = strongEncoder.encode("password");
        assertThat(weakEncoder.upgradeEncoding(strongPassword)).isFalse();
        assertThat(strongEncoder.upgradeEncoding(weakPassword)).isTrue();
    }

    /**
     * @see <a href="https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496">https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496</a>
     */
    @Test
    void upgradeFromNullOrEmpty() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.upgradeEncoding(null)).isFalse();
        assertThat(encoder.upgradeEncoding("")).isFalse();
    }

    /**
     * @see <a href="https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496">https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496</a>
     */
    @Test
    void upgradeFromNonBCrypt() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.upgradeEncoding("not-a-bcrypt-password"));
    }

    @Test
    void encodeNullRawPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(null));
    }

    @Test
    void matchNullRawPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.matches(null, "does-not-matter"));
    }
}