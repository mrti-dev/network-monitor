package com.network.network_monitor.util;

import com.network.network_monitor.exception.InvalidCidrException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Kiểm thử toàn diện CidrValidator theo yêu cầu giai đoạn 3.
 */
class CidrValidatorTest {

    // ------------------------------------------------------------------
    // Valid CIDR — normalisation
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "192.168.1.5/24,  192.168.1.0/24",   // host bit stripped
        "10.0.0.1/8,      10.0.0.0/8",
        "172.16.100.50/30,172.16.100.48/30",
        "192.168.1.0/24,  192.168.1.0/24",   // already normalised
        "10.0.0.1/32,     10.0.0.1/32",       // /32 single host
        "192.168.0.0/31,  192.168.0.0/31",   // /31 RFC 3021
        "203.0.113.0/24,  203.0.113.0/24",
    })
    void normalisesHostBitsToNetworkAddress(String input, String expected) {
        assertThat(CidrValidator.normalise(input.trim())).isEqualTo(expected.trim());
    }

    // ------------------------------------------------------------------
    // Invalid octets > 255
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "192.168.1.256/24",  // octet > 255
        "300.0.0.1/24",      // octet > 255 in first position
        "192.168.999.1/24",
        "0.0.0.300/8",
    })
    void rejectsOctetGreaterThan255(String cidr) {
        assertThatThrownBy(() -> CidrValidator.normalise(cidr))
            .isInstanceOf(InvalidCidrException.class)
            .hasMessageContaining("255");
    }

    // ------------------------------------------------------------------
    // Prefix boundary tests
    // ------------------------------------------------------------------

    @Test
    void accepts_prefix_24() {
        assertThat(CidrValidator.normalise("192.168.1.0/24")).isEqualTo("192.168.1.0/24");
    }

    @Test
    void accepts_prefix_30() {
        // /30: 2 usable hosts (startIp=.1, endIp=.2)
        assertThat(CidrValidator.normalise("10.0.0.4/30")).isEqualTo("10.0.0.4/30");
    }

    @Test
    void accepts_prefix_31() {
        // RFC 3021: both addresses usable
        String normalised = CidrValidator.normalise("192.168.1.1/31");
        assertThat(normalised).isEqualTo("192.168.1.0/31");
        assertThat(CidrValidator.hostCount(31)).isEqualTo(2);
    }

    @Test
    void accepts_prefix_32() {
        // Single host
        String normalised = CidrValidator.normalise("10.0.0.5/32");
        assertThat(normalised).isEqualTo("10.0.0.5/32");
        assertThat(CidrValidator.hostCount(32)).isEqualTo(1);
    }

    @Test
    void rejects_prefix_0() {
        // /0 means 2^32 addresses — exceeds limit; rejected at validator level
        assertThatThrownBy(() -> CidrValidator.normalise("0.0.0.0/0"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_PREFIX_RANGE");
    }

    @Test
    void rejects_prefix_negative() {
        assertThatThrownBy(() -> CidrValidator.normalise("10.0.0.0/-1"))
            .isInstanceOf(InvalidCidrException.class);
    }

    @Test
    void rejects_prefix_33() {
        assertThatThrownBy(() -> CidrValidator.normalise("10.0.0.0/33"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_PREFIX_RANGE");
    }

    @Test
    void rejects_prefix_non_numeric() {
        assertThatThrownBy(() -> CidrValidator.normalise("192.168.1.0/abc"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_PREFIX_NAN");
    }

    // ------------------------------------------------------------------
    // Hostname must not be accepted (no InetAddress.getByName resolution)
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost/24",
        "google.com/24",
        "example/8",
    })
    void rejectsHostnameInsteadOfIpv4(String cidr) {
        assertThatThrownBy(() -> CidrValidator.normalise(cidr))
            .isInstanceOf(InvalidCidrException.class);
    }

    // ------------------------------------------------------------------
    // Leading zeros rejected (potential octal ambiguity)
    // ------------------------------------------------------------------

    @Test
    void rejectsLeadingZeroInOctet() {
        assertThatThrownBy(() -> CidrValidator.normalise("010.0.0.1/24"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_LEADING_ZERO");
    }

    // ------------------------------------------------------------------
    // Missing parts
    // ------------------------------------------------------------------

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> CidrValidator.normalise(""))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_BLANK");
    }

    @Test
    void rejectsMissingPrefix() {
        assertThatThrownBy(() -> CidrValidator.normalise("192.168.1.0"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_NO_PREFIX");
    }

    @Test
    void rejectsTooFewOctets() {
        assertThatThrownBy(() -> CidrValidator.normalise("192.168/24"))
            .isInstanceOf(InvalidCidrException.class)
            .extracting(e -> ((InvalidCidrException) e).getErrorCode())
            .isEqualTo("CIDR_BAD_IP");
    }

    // ------------------------------------------------------------------
    // hostCount helper
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "24, 254",
        "30, 2",
        "31, 2",
        "32, 1",
        "25, 126",
        "16, 65534",
    })
    void hostCountMatchesExpected(int prefix, int expected) {
        assertThat(CidrValidator.hostCount(prefix)).isEqualTo(expected);
    }
}
