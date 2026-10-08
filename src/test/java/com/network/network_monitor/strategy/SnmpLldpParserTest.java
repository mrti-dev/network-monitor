package com.network.network_monitor.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;

import com.network.network_monitor.config.SnmpProperties;
import com.network.network_monitor.dto.LldpNeighborDto;
import com.network.network_monitor.util.SnmpCredentialEncryptor;

class SnmpLldpParserTest {

    private SnmpProbingStrategy strategy;

    @BeforeEach
    void setUp() {
        SnmpCredentialEncryptor encryptor = new SnmpCredentialEncryptor();
        SnmpProperties props = new SnmpProperties();
        strategy = new SnmpProbingStrategy(encryptor, props);
    }

    @Test
    void parseLocalPortTable_mapsPortDescAndIdCorrectly() {
        // OID: 1.0.8802.1.1.2.1.3.7.1.<col>.<locPortNum>
        // col=3: portId, col=4: portDesc
        VariableBinding idVb1 = new VariableBinding(new OID("1.0.8802.1.1.2.1.3.7.1.3.1"), new OctetString("1"));
        VariableBinding descVb1 = new VariableBinding(new OID("1.0.8802.1.1.2.1.3.7.1.4.1"), new OctetString("GigabitEthernet0/1"));

        VariableBinding idVb2 = new VariableBinding(new OID("1.0.8802.1.1.2.1.3.7.1.3.2"), new OctetString("eth1"));

        Map<Integer, String> localPorts = strategy.parseLocalPortTable(List.of(idVb1, descVb1, idVb2));

        assertThat(localPorts).hasSize(2);
        // Ưu tiên desc nếu có
        assertThat(localPorts.get(1)).isEqualTo("GigabitEthernet0/1");
        // Fallback id nếu không có desc
        assertThat(localPorts.get(2)).isEqualTo("eth1");
    }

    @Test
    void parseLldpNeighbors_extractsAllNeighborFieldsFromSyntheticBindings() {
        // lldpRemTable prefix: 1.0.8802.1.1.2.1.4.1.1.<col>.<timeMark>.<locPortNum>.<remIndex>
        // timeMark=0, locPortNum=1, remIndex=1
        // col 4: chassisIdSubtype (4 = MAC)
        VariableBinding chassisSubtype = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.4.0.1.1"), new Integer32(4));
        // col 5: chassisId (MAC raw 6 bytes: 00:1A:2B:3C:4D:5E)
        byte[] macBytes = new byte[]{(byte) 0x00, (byte) 0x1A, (byte) 0x2B, (byte) 0x3C, (byte) 0x4D, (byte) 0x5E};
        VariableBinding chassisId = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.5.0.1.1"), new OctetString(macBytes));
        // col 6: portIdSubtype
        VariableBinding portSubtype = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.6.0.1.1"), new Integer32(5));
        // col 7: portId
        VariableBinding portId = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.7.0.1.1"), new OctetString("Gi0/2"));
        // col 8: portDesc
        VariableBinding portDesc = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.8.0.1.1"), new OctetString("Uplink to Switch B"));
        // col 9: sysName
        VariableBinding sysName = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.9.0.1.1"), new OctetString("Dist-Switch-02"));

        List<VariableBinding> remBindings = List.of(chassisSubtype, chassisId, portSubtype, portId, portDesc, sysName);

        // lldpRemManAddrTable: 1.0.8802.1.1.2.1.4.2.1.<col>.<timeMark>.<locPortNum>.<remIndex>.<subtype>.<len>.<ip...>
        // 1.0.8802.1.1.2.1.4.2.1.1.0.1.1.1.4.192.168.1.50
        VariableBinding manAddr = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.2.1.1.0.1.1.1.4.192.168.1.50"), new Integer32(1));
        List<VariableBinding> manAddrBindings = List.of(manAddr);

        Map<Integer, String> localPorts = Map.of(1, "GigabitEthernet0/1");

        List<LldpNeighborDto> neighbors = strategy.parseLldpNeighbors(remBindings, manAddrBindings, localPorts);

        assertThat(neighbors).hasSize(1);
        LldpNeighborDto n = neighbors.get(0);
        assertThat(n.getLocalPortNum()).isEqualTo(1);
        assertThat(n.getLocalPort()).isEqualTo("GigabitEthernet0/1");
        assertThat(n.getRemoteChassisMac()).isEqualTo("00:1a:2b:3c:4d:5e");
        assertThat(n.getRemotePortId()).isEqualTo("Gi0/2");
        assertThat(n.getRemotePortDesc()).isEqualTo("Uplink to Switch B");
        assertThat(n.getRemoteSysName()).isEqualTo("Dist-Switch-02");
        assertThat(n.getRemoteManagementIp()).isEqualTo("192.168.1.50");
    }

    @Test
    void parseLldpNeighbors_extractsManagementIpFromChassisNetworkAddressSubtype() {
        // chassisSubtype = 5 (networkAddress), value = 1 (IPv4) + 4 bytes (10.0.0.1)
        byte[] ipChassis = new byte[]{1, 10, 0, 0, 1};
        VariableBinding chassisSubtype = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.4.0.2.1"), new Integer32(5));
        VariableBinding chassisId = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.5.0.2.1"), new OctetString(ipChassis));
        VariableBinding portId = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.7.0.2.1"), new OctetString("Port-1"));
        VariableBinding sysName = new VariableBinding(
                new OID("1.0.8802.1.1.2.1.4.1.1.9.0.2.1"), new OctetString("Router-Gateway"));

        List<LldpNeighborDto> neighbors = strategy.parseLldpNeighbors(
                List.of(chassisSubtype, chassisId, portId, sysName),
                List.of(),
                Map.of(2, "eth0")
        );

        assertThat(neighbors).hasSize(1);
        LldpNeighborDto n = neighbors.get(0);
        assertThat(n.getRemoteManagementIp()).isEqualTo("10.0.0.1");
        assertThat(n.getRemoteSysName()).isEqualTo("Router-Gateway");
        assertThat(n.getLocalPort()).isEqualTo("eth0");
    }

    @Test
    void walkTable_stopsWhenOidLeavesTablePrefix() throws IOException {
        Snmp mockSnmp = mock(Snmp.class);
        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        OID rootOid = new OID("1.0.8802.1.1.2.1.4.1.1");

        // Response 1: OID hợp lệ trong bảng
        PDU respPdu1 = new PDU();
        respPdu1.add(new VariableBinding(new OID("1.0.8802.1.1.2.1.4.1.1.4.0.1.1"), new Integer32(4)));
        ResponseEvent<?> event1 = new ResponseEvent<>(mockSnmp, target.getAddress(), new PDU(), respPdu1, null);

        // Response 2: OID ngoài bảng (bảng khác)
        PDU respPdu2 = new PDU();
        respPdu2.add(new VariableBinding(new OID("1.0.8802.1.1.2.1.4.2.1.1.0.1.1"), new Integer32(1)));
        ResponseEvent<?> event2 = new ResponseEvent<>(mockSnmp, target.getAddress(), new PDU(), respPdu2, null);

        when(mockSnmp.send(any(PDU.class), any())).thenReturn((ResponseEvent) event1, (ResponseEvent) event2);

        List<VariableBinding> results = strategy.walkTable(mockSnmp, target, rootOid, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOid().toString()).isEqualTo("1.0.8802.1.1.2.1.4.1.1.4.0.1.1");
    }
}
