package com.network.network_monitor.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryInterfaceSelectionTest {

    @Test
    void detectsCommonWindowsVirtualAdaptersFromDisplayName() {
        assertThat(DiscoveryServiceImpl.isVirtualInterfaceName("eth3",
                "VMware Virtual Ethernet Adapter for VMnet8")).isTrue();
        assertThat(DiscoveryServiceImpl.isVirtualInterfaceName("eth4",
                "VirtualBox Host-Only Ethernet Adapter")).isTrue();
        assertThat(DiscoveryServiceImpl.isVirtualInterfaceName("eth5",
                "Hyper-V Virtual Ethernet Adapter")).isTrue();
    }

    @Test
    void keepsPhysicalNetworkAdapters() {
        assertThat(DiscoveryServiceImpl.isVirtualInterfaceName("wlan0",
                "MediaTek Wi-Fi 6 MT7921 Wireless LAN Card")).isFalse();
        assertThat(DiscoveryServiceImpl.isVirtualInterfaceName("eth0",
                "Realtek PCIe GbE Family Controller")).isFalse();
    }
}
