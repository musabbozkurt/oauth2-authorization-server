package mb.oauth2authorizationserver;

import mb.oauth2authorizationserver.base.AbstractPasswordEncoderValidationTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BCryptPasswordEncoderTests extends AbstractPasswordEncoderValidationTests {

    @BeforeEach
    void setup() {
        setEncoder(new BCryptPasswordEncoder());
    }

    // gh-5548
    @Test
    void emptyRawPasswordDoesNotMatchPassword() {
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("", result)).isFalse();
    }

    @Test
    void $2yMatches() {
        // $2y is default version
        String result = getEncoder().encode("password");
        assertThat(result).isNotEqualTo("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void $2aMatches() {
        String result = getEncoder().encode("password");
        assertNotEquals("password", result);
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void $2bMatches() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B));
        String result = getEncoder().encode("password");
        assertThat(result).isNotEqualTo("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void $2yUnicode() {
        // $2y is default version
        String result = getEncoder().encode("passw\u9292rd");
        assertThat(getEncoder().matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(getEncoder().matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2aUnicode() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A));
        String result = getEncoder().encode("passw\u9292rd");
        assertThat(getEncoder().matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(getEncoder().matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2bUnicode() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B));
        String result = getEncoder().encode("passw\u9292rd");
        assertThat(getEncoder().matches("pass\u9292\u9292rd", result)).isFalse();
        assertThat(getEncoder().matches("passw\u9292rd", result)).isTrue();
    }

    @Test
    void $2yNotMatches() {
        // $2y is default version
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("bogus", result)).isFalse();
    }

    @Test
    void $2aNotMatches() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("bogus", result)).isFalse();
    }

    @Test
    void $2bNotMatches() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("bogus", result)).isFalse();
    }

    @Test
    void $2yCustomStrength() {
        setEncoder(new BCryptPasswordEncoder(8));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void $2aCustomStrength() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2A, 8));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void $2bCustomStrength() {
        setEncoder(new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 8));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
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
        setEncoder(new BCryptPasswordEncoder(8, new SecureRandom()));
        String result = getEncoder().encode("password");
        assertThat(getEncoder().matches("password", result)).isTrue();
    }

    @Test
    void doesntMatchNullEncodedValue() {
        setEncoder(new BCryptPasswordEncoder());
        assertThat(getEncoder().matches("password", null)).isFalse();
    }

    @Test
    void doesntMatchEmptyEncodedValue() {
        assertThat(getEncoder().matches("password", "")).isFalse();
    }

    @Test
    void doesntMatchBogusEncodedValue() {
        assertThat(getEncoder().matches("password", "012345678901234567890123456789")).isFalse();
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
     * @see <a href=
     * "https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496">https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496</a>
     */
    @Test
    void upgradeFromNullOrEmpty() {
        assertThat(getEncoder().upgradeEncoding(null)).isFalse();
        assertThat(getEncoder().upgradeEncoding("")).isFalse();
    }

    /**
     * @see <a href=
     * "https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496">https://github.com/spring-projects/spring-security/pull/7042#issuecomment-506755496</a>
     */
    @Test
    void upgradeFromNonBCrypt() {
        assertThatIllegalArgumentException().isThrownBy(() -> getEncoder().upgradeEncoding("not-a-bcrypt-password"));
    }

    @Test
    void upgradeWhenNoRoundsThenTrue() {
        assertThat(getEncoder().upgradeEncoding("$2a$00$9N8N35BVs5TLqGL3pspAte5OWWA2a2aZIs.EGp7At7txYakFERMue")).isTrue();
    }

    @Test
    void checkWhenNoRoundsThenTrue() {
        assertThat(getEncoder().matches("password", "$2a$00$9N8N35BVs5TLqGL3pspAte5OWWA2a2aZIs.EGp7At7txYakFERMue")).isTrue();
        assertThat(getEncoder().matches("wrong", "$2a$00$9N8N35BVs5TLqGL3pspAte5OWWA2a2aZIs.EGp7At7txYakFERMue")).isFalse();
    }

    @Test
    void encodeWhenPasswordOverMaxLengthThenThrowIllegalArgumentException() {
        String password72chars = "123456789012345678901234567890123456789012345678901234567890123456789012";
        getEncoder().encode(password72chars);

        String password73chars = password72chars + "3";
        assertThatIllegalArgumentException().isThrownBy(() -> getEncoder().encode(password73chars));
    }

    @Test
    void matchesWhenPasswordOverMaxLengthThenAllowToMatch() {
        String password71chars = "12345678901234567890123456789012345678901234567890123456789012345678901";
        String encodedPassword71chars = "$2a$10$jx3x2FaF.iX5QZ9i3O424Os2Ou5P5JrnedmWYHuDyX8JKA4Unp4xq";
        assertThat(getEncoder().matches(password71chars, encodedPassword71chars)).isTrue();

        String password72chars = password71chars + "2";
        String encodedPassword72chars = "$2a$10$oXYO6/UvbsH5rQEraBkl6uheccBqdB3n.RaWbrimog9hS2GX4lo/O";
        assertThat(getEncoder().matches(password72chars, encodedPassword72chars)).isTrue();

        // Max length is 72 bytes, however, we need to ensure backwards compatibility
        // for previously encoded passwords that are greater than 72 bytes and allow the
        // match to be performed.
        String password73chars = password72chars + "3";
        String encodedPassword73chars = "$2a$10$1l9.kvQTsqNLiCYFqmKtQOHkp.BrgIrwsnTzWo9jdbQRbuBYQ/AVK";
        assertThat(getEncoder().matches(password73chars, encodedPassword73chars)).isTrue();
    }

    /**
     * Fixes gh-18133
     *
     * @author StringManolo
     */
    @Test
    void passwordLargerThan72BytesShouldThrowIllegalArgumentException() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String singleByteChars = "a".repeat(68);
        String password72Bytes = singleByteChars + "😀";
        assertThat(password72Bytes).hasSize(70);
        assertThat(password72Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThatNoException().isThrownBy(() -> encoder.encode(password72Bytes));
        String singleByteCharsTooLong = "a".repeat(69);
        String password73Bytes = singleByteCharsTooLong + "😀";
        assertThat(password73Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(73);
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(password73Bytes)).withMessageContaining("password cannot be more than 72 bytes");
    }
}
