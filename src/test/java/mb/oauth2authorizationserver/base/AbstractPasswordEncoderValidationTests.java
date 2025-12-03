package mb.oauth2authorizationserver.base;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A base class for other tests to perform validation of the arguments to
 * {@link PasswordEncoder} instances in a consistent way.
 *
 * @author Rob Winch
 */
public abstract class AbstractPasswordEncoderValidationTests {

    private PasswordEncoder encoder;

    protected <T extends PasswordEncoder> T getEncoder() {
        return (T) this.encoder;
    }

    protected void setEncoder(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Test
    void encodeWhenNullThenNull() {
        assertThat(this.encoder.encode(null)).isNull();
    }

    @Test
    void matchesWhenEncodedPasswordNullThenFalse() {
        assertThat(this.encoder.matches("raw", null)).isFalse();
    }

    @Test
    void matchesWhenEncodedPasswordEmptyThenFalse() {
        assertThat(this.encoder.matches("raw", "")).isFalse();
    }

    @Test
    void matchesWhenRawPasswordNullThenFalse() {
        assertThat(this.encoder.matches(null, this.encoder.encode("password"))).isFalse();
    }

    @Test
    void matchesWhenRawPasswordEmptyThenFalse() {
        assertThat(this.encoder.matches("", this.encoder.encode("password"))).isFalse();
    }
}
