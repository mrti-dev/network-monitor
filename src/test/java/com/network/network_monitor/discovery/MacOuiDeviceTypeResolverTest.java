package com.network.network_monitor.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.network.network_monitor.enums.DeviceType;

class MacOuiDeviceTypeResolverTest {

    private final MacOuiDeviceTypeResolver resolver = new MacOuiDeviceTypeResolver();

    @ParameterizedTest
    @CsvSource({
            "A8:42:A1:6A:D4:93, ROUTER",
            "4c-5e-0c-12-34-56, ROUTER",
            "7C:7B:68:1A:03:88, ROUTER",
            "00:1B:54:12:34:56, SWITCH",
            "24:A4:3C:12:34:56, ACCESS_POINT",
            "00:11:32:12:34:56, SERVER",
            "00:0C:29:12:34:56, WORKSTATION",
            "9C:7F:64:C6:D7:9B, WORKSTATION"
    })
    void resolvesConfiguredOuiToDeviceType(String mac, DeviceType expected) {
        assertThat(resolver.resolve(mac)).contains(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid", "00:11:22:33:44", "02:42:AC:11:00:02", "AA:15:F1:0C:64:57", "FF:FF:FF:FF:FF:FF"})
    void leavesUnknownInvalidAndLocallyAdministeredMacUnclassified(String mac) {
        assertThat(resolver.resolve(mac)).isEmpty();
    }
}
