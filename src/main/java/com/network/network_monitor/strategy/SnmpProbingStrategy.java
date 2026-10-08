package com.network.network_monitor.strategy;

import com.network.network_monitor.config.SnmpProperties;
import com.network.network_monitor.dto.LldpNeighborDto;
import com.network.network_monitor.entity.SnmpConfig;
import com.network.network_monitor.enums.SnmpInterfaceStatus;
import com.network.network_monitor.util.LldpNormalizer;
import com.network.network_monitor.util.SnmpCredentialEncryptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

/**
 * Chiến lược thăm dò SNMP v2c.
 * - Không đưa logic SNMP vào DeviceService.
 * - OID không hỗ trợ → trả null, không làm toàn bộ probe thất bại.
 * - Counter bandwidth tính delta theo thời gian, xử lý wrap/reset.
 * - Mẫu đầu tiên không đủ dữ liệu → inboundBps/outboundBps = null.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnmpProbingStrategy {

    // Standard OIDs
    private static final OID OID_SYS_UPTIME = new OID("1.3.6.1.2.1.1.3.0");
    private static final OID OID_SYS_NAME = new OID("1.3.6.1.2.1.1.5.0");
    private static final OID OID_IF_TABLE_START = new OID("1.3.6.1.2.1.2.2.1");

    // Interface sub-OIDs
    private static final String OID_IF_OPER_STATUS_PREFIX = "1.3.6.1.2.1.2.2.1.8.";
    private static final String OID_IF_DESCR_PREFIX = "1.3.6.1.2.1.2.2.1.2.";
    private static final String OID_IF_IN_OCTETS_PREFIX = "1.3.6.1.2.1.2.2.1.10.";
    private static final String OID_IF_OUT_OCTETS_PREFIX = "1.3.6.1.2.1.2.2.1.16.";

    // LLDP-MIB OIDs (IEEE 802.1AB)
    public static final OID OID_LLDP_LOC_PORT_TABLE = new OID("1.0.8802.1.1.2.1.3.7.1");
    public static final OID OID_LLDP_REM_TABLE = new OID("1.0.8802.1.1.2.1.4.1.1");
    public static final OID OID_LLDP_REM_MAN_ADDR_TABLE = new OID("1.0.8802.1.1.2.1.4.2.1");
    private static final int MAX_LLDP_WALK_RECORDS = 128;

    // Net-SNMP CPU/RAM OIDs (UCD-SNMP-MIB) — phổ biến nhất
    private static final OID OID_CPU_IDLE = new OID("1.3.6.1.4.1.2021.11.11.0");
    private static final OID OID_MEM_TOTAL_REAL = new OID("1.3.6.1.4.1.2021.4.5.0");
    private static final OID OID_MEM_AVAIL_REAL = new OID("1.3.6.1.4.1.2021.4.6.0");

    private static final int COUNTER32_MAX = 0xFFFFFFFFL > 0 ? (int) 0xFFFFFFFFL : Integer.MAX_VALUE;
    private static final long COUNTER32_WRAP = 4294967296L;

    private final SnmpCredentialEncryptor encryptor;
    private final SnmpProperties snmpProperties;

    // Cache counter để tính delta bandwidth: key = deviceId:ifIndex
    private final Map<String, CounterSnapshot> counterCache = new ConcurrentHashMap<>();

    public SnmpProbeResult probe(String ipAddress, SnmpConfig config) {
        String community;
        try {
            community = encryptor.decrypt(config.getCommunityEncrypted());
        } catch (Exception e) {
            return SnmpProbeResult.builder().reachable(false)
                    .failureReason("Lỗi giải mã credential").build();
        }

        int timeoutMs = config.getTimeoutMs() != null ? config.getTimeoutMs() : snmpProperties.getTimeoutMs();
        int retries = config.getRetries() != null ? config.getRetries() : snmpProperties.getRetries();
        int port = config.getPort() != null ? config.getPort() : 161;

        try (TransportMapping<?> transport = new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {
            transport.listen();

            CommunityTarget<UdpAddress> target = buildTarget(ipAddress, port, community, timeoutMs, retries);

            // 1. Lấy sysUptime + sysName
            Long sysUptime = null;
            String sysName = null;
            try {
                PDU pdu = buildGetPdu(OID_SYS_UPTIME, OID_SYS_NAME);
                ResponseEvent<?> resp = snmp.send(pdu, target);
                if (resp == null || resp.getResponse() == null) {
                    return SnmpProbeResult.builder().reachable(false)
                            .failureReason("Không có phản hồi SNMP (timeout)").build();
                }
                PDU response = resp.getResponse();
                sysUptime = getLongOrNull(response, OID_SYS_UPTIME);
                sysName = getStringOrNull(response, OID_SYS_NAME);
            } catch (IOException e) {
                return SnmpProbeResult.builder().reachable(false)
                        .failureReason("IOException: " + e.getMessage()).build();
            }

            // 2. CPU/RAM — optional, không fail toàn bộ nếu không hỗ trợ
            Double cpuUsage = null;
            Double memoryUsage = null;
            try {
                PDU pdu = buildGetPdu(OID_CPU_IDLE, OID_MEM_TOTAL_REAL, OID_MEM_AVAIL_REAL);
                ResponseEvent<?> resp = snmp.send(pdu, target);
                if (resp != null && resp.getResponse() != null) {
                    PDU response = resp.getResponse();
                    Long cpuIdle = getLongOrNull(response, OID_CPU_IDLE);
                    Long memTotal = getLongOrNull(response, OID_MEM_TOTAL_REAL);
                    Long memAvail = getLongOrNull(response, OID_MEM_AVAIL_REAL);
                    if (cpuIdle != null) {
                        cpuUsage = Math.max(0.0, Math.min(100.0, 100.0 - cpuIdle));
                    }
                    if (memTotal != null && memAvail != null && memTotal > 0) {
                        memoryUsage = ((double)(memTotal - memAvail) / memTotal) * 100.0;
                    }
                }
            } catch (Exception e) {
                log.debug("Device {}: CPU/RAM OID không hỗ trợ - {}", ipAddress, e.getMessage());
            }

            // 3. Interface table — walk
            List<SnmpProbeResult.InterfaceInfo> interfaces = new ArrayList<>();
            double totalInBps = 0;
            double totalOutBps = 0;
            boolean hasBandwidthData = false;
            long now = System.currentTimeMillis();

            try {
                List<Integer> ifIndexes = walkInterfaceIndexes(snmp, target, ipAddress);
                for (int ifIndex : ifIndexes) {
                    PDU pdu = buildGetPdu(
                            new OID(OID_IF_OPER_STATUS_PREFIX + ifIndex),
                            new OID(OID_IF_DESCR_PREFIX + ifIndex),
                            new OID(OID_IF_IN_OCTETS_PREFIX + ifIndex),
                            new OID(OID_IF_OUT_OCTETS_PREFIX + ifIndex)
                    );
                    ResponseEvent<?> resp = snmp.send(pdu, target);
                    if (resp == null || resp.getResponse() == null) continue;
                    PDU response = resp.getResponse();

                    Long operStatus = getLongOrNull(response, new OID(OID_IF_OPER_STATUS_PREFIX + ifIndex));
                    String ifName = getStringOrNull(response, new OID(OID_IF_DESCR_PREFIX + ifIndex));
                    Long inOctets = getLongOrNull(response, new OID(OID_IF_IN_OCTETS_PREFIX + ifIndex));
                    Long outOctets = getLongOrNull(response, new OID(OID_IF_OUT_OCTETS_PREFIX + ifIndex));

                    SnmpInterfaceStatus status = toInterfaceStatus(operStatus);

                    // Tính delta bandwidth
                    String cacheKey = config.getDevice().getId() + ":" + ifIndex;
                    if (inOctets != null && outOctets != null) {
                        CounterSnapshot prev = counterCache.get(cacheKey);
                        if (prev != null) {
                            double elapsedSec = (now - prev.timestampMs) / 1000.0;
                            if (elapsedSec > 0) {
                                long inDelta = counterDelta(inOctets, prev.inOctets);
                                long outDelta = counterDelta(outOctets, prev.outOctets);
                                if (inDelta >= 0 && outDelta >= 0) {
                                    totalInBps += (inDelta * 8.0) / elapsedSec;
                                    totalOutBps += (outDelta * 8.0) / elapsedSec;
                                    hasBandwidthData = true;
                                }
                            }
                        }
                        counterCache.put(cacheKey, new CounterSnapshot(now, inOctets, outOctets));
                    }

                    interfaces.add(SnmpProbeResult.InterfaceInfo.builder()
                            .index(ifIndex)
                            .name(ifName)
                            .status(status)
                            .inboundOctets(inOctets)
                            .outboundOctets(outOctets)
                            .build());
                }
            } catch (Exception e) {
                log.debug("Device {}: Lỗi walk interface table - {}", ipAddress, e.getMessage());
            }

            return SnmpProbeResult.builder()
                    .reachable(true)
                    .sysName(sysName)
                    .sysUptime(sysUptime)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .inboundBps(hasBandwidthData ? totalInBps : null)
                    .outboundBps(hasBandwidthData ? totalOutBps : null)
                    .interfaces(interfaces)
                    .build();

        } catch (IOException e) {
            return SnmpProbeResult.builder().reachable(false)
                    .failureReason("Lỗi khởi tạo transport: " + e.getMessage()).build();
        }
    }

    public void clearCounterCache(Long deviceId) {
        counterCache.entrySet().removeIf(e -> e.getKey().startsWith(deviceId + ":"));
    }

    // ==================== Private helpers ====================

    private CommunityTarget<UdpAddress> buildTarget(String ip, int port, String community,
                                                      int timeoutMs, int retries) {
        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(new UdpAddress(ip + "/" + port));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(timeoutMs);
        target.setRetries(retries);
        return target;
    }

    private PDU buildGetPdu(OID... oids) {
        PDU pdu = new PDU();
        pdu.setType(PDU.GET);
        for (OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }
        return pdu;
    }

    private List<Integer> walkInterfaceIndexes(Snmp snmp, CommunityTarget<UdpAddress> target,
                                                String ipAddress) throws IOException {
        PDU pdu = new PDU();
        pdu.setType(PDU.GETNEXT);
        pdu.add(new VariableBinding(new OID(OID_IF_OPER_STATUS_PREFIX)));

        List<Integer> indexes = new ArrayList<>();
        OID ifOperStatusBase = new OID(OID_IF_OPER_STATUS_PREFIX);

        for (int i = 0; i < 64; i++) {
            ResponseEvent<?> resp = snmp.send(pdu, target);
            if (resp == null || resp.getResponse() == null) break;
            PDU response = resp.getResponse();
            if (response.size() == 0) break;

            VariableBinding vb = response.get(0);
            OID returnedOid = vb.getOid();
            if (!returnedOid.startsWith(ifOperStatusBase)) break;

            String oidStr = returnedOid.toString();
            String suffix = oidStr.substring(OID_IF_OPER_STATUS_PREFIX.length());
            try {
                indexes.add(Integer.parseInt(suffix));
            } catch (NumberFormatException ignore) {
                break;
            }
            pdu = new PDU();
            pdu.setType(PDU.GETNEXT);
            pdu.add(new VariableBinding(returnedOid));
        }
        return indexes;
    }

    private Long getLongOrNull(PDU pdu, OID oid) {
        org.snmp4j.smi.Variable v = pdu.getVariable(oid);
        if (v == null || v instanceof org.snmp4j.smi.Null) return null;
        try {
            return v.toLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String getStringOrNull(PDU pdu, OID oid) {
        org.snmp4j.smi.Variable v = pdu.getVariable(oid);
        if (v == null || v instanceof org.snmp4j.smi.Null) return null;
        return v.toString();
    }

    private SnmpInterfaceStatus toInterfaceStatus(Long operStatus) {
        if (operStatus == null) return SnmpInterfaceStatus.UNKNOWN;
        return switch (operStatus.intValue()) {
            case 1 -> SnmpInterfaceStatus.UP;
            case 2 -> SnmpInterfaceStatus.DOWN;
            default -> SnmpInterfaceStatus.UNKNOWN;
        };
    }

    /**
     * Tính delta counter 32-bit, xử lý wrap (reset/wrap qua 0).
     * Trả -1 nếu xảy ra reset bất thường (current << previous/2).
     */
    private long counterDelta(long current, long previous) {
        if (current >= previous) {
            return current - previous;
        }
        // Wrap: counter đã qua 0 → delta = (MAX - prev) + current
        long delta = COUNTER32_WRAP - previous + current;
        // Nếu delta quá lớn hoặc bất thường → không tính (trả -1)
        if (delta > COUNTER32_WRAP / 2) return -1;
        return delta;
    }

    public LldpProbeResult probeLldp(String ipAddress, SnmpConfig config) {
        String community;
        try {
            community = encryptor.decrypt(config.getCommunityEncrypted());
        } catch (Exception e) {
            return LldpProbeResult.builder().reachable(false)
                    .failureReason("Lỗi giải mã credential").build();
        }

        int timeoutMs = config.getTimeoutMs() != null ? config.getTimeoutMs() : snmpProperties.getTimeoutMs();
        int retries = config.getRetries() != null ? config.getRetries() : snmpProperties.getRetries();
        int port = config.getPort() != null ? config.getPort() : 161;

        try (TransportMapping<?> transport = new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {
            transport.listen();
            CommunityTarget<UdpAddress> target = buildTarget(ipAddress, port, community, timeoutMs, retries);

            List<VariableBinding> locBindings = Collections.emptyList();
            try {
                locBindings = walkTable(snmp, target, OID_LLDP_LOC_PORT_TABLE, MAX_LLDP_WALK_RECORDS);
            } catch (Exception e) {
                log.debug("Device {}: Không thể walk lldpLocPortTable - {}", ipAddress, e.getMessage());
            }

            List<VariableBinding> remBindings = Collections.emptyList();
            try {
                remBindings = walkTable(snmp, target, OID_LLDP_REM_TABLE, MAX_LLDP_WALK_RECORDS);
            } catch (Exception e) {
                log.debug("Device {}: Không thể walk lldpRemTable - {}", ipAddress, e.getMessage());
            }

            List<VariableBinding> manAddrBindings = Collections.emptyList();
            try {
                manAddrBindings = walkTable(snmp, target, OID_LLDP_REM_MAN_ADDR_TABLE, MAX_LLDP_WALK_RECORDS);
            } catch (Exception e) {
                log.debug("Device {}: Không thể walk lldpRemManAddrTable - {}", ipAddress, e.getMessage());
            }

            if (locBindings.isEmpty() && remBindings.isEmpty()) {
                try {
                    PDU pingPdu = buildGetPdu(OID_SYS_UPTIME, OID_SYS_NAME);
                    ResponseEvent<?> resp = snmp.send(pingPdu, target);
                    if (resp == null || resp.getResponse() == null) {
                        return LldpProbeResult.builder().reachable(false)
                                .failureReason("Không có phản hồi SNMP (timeout)").build();
                    }
                    return LldpProbeResult.builder()
                            .reachable(true)
                            .lldpSupported(false)
                            .failureReason("Thiết bị không hỗ trợ LLDP-MIB")
                            .build();
                } catch (Exception e) {
                    return LldpProbeResult.builder().reachable(false)
                            .failureReason("IOException: " + e.getMessage()).build();
                }
            }

            Map<Integer, String> localPorts = parseLocalPortTable(locBindings);
            List<LldpNeighborDto> neighbors = parseLldpNeighbors(remBindings, manAddrBindings, localPorts);

            return LldpProbeResult.builder()
                    .reachable(true)
                    .lldpSupported(true)
                    .neighbors(neighbors)
                    .build();

        } catch (IOException e) {
            return LldpProbeResult.builder().reachable(false)
                    .failureReason("Lỗi khởi tạo transport: " + e.getMessage()).build();
        }
    }

    public List<VariableBinding> walkTable(Snmp snmp, CommunityTarget<UdpAddress> target, OID rootOid, int maxRows) throws IOException {
        List<VariableBinding> results = new ArrayList<>();
        PDU pdu = new PDU();
        pdu.setType(PDU.GETNEXT);
        pdu.add(new VariableBinding(rootOid));

        for (int i = 0; i < maxRows; i++) {
            ResponseEvent<?> resp = snmp.send(pdu, target);
            if (resp == null || resp.getResponse() == null) break;
            PDU response = resp.getResponse();
            if (response.size() == 0 || response.getErrorStatus() != PDU.noError) break;

            VariableBinding vb = response.get(0);
            OID returnedOid = vb.getOid();
            if (returnedOid == null || !returnedOid.startsWith(rootOid)) break;
            if (vb.getVariable() == null || vb.getVariable().isException()) break;

            results.add(vb);
            pdu = new PDU();
            pdu.setType(PDU.GETNEXT);
            pdu.add(new VariableBinding(returnedOid));
        }
        return results;
    }

    public Map<Integer, String> parseLocalPortTable(List<VariableBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> portIdMap = new HashMap<>();
        Map<Integer, String> portDescMap = new HashMap<>();
        for (VariableBinding vb : bindings) {
            OID oid = vb.getOid();
            if (oid == null || !oid.startsWith(OID_LLDP_LOC_PORT_TABLE) || oid.size() < 12) continue;
            int col = oid.get(10);
            int portNum = oid.get(11);
            String val = extractStringValue(vb.getVariable());
            if (val == null || val.isBlank()) continue;
            if (col == 3) {
                portIdMap.put(portNum, val.trim());
            } else if (col == 4) {
                portDescMap.put(portNum, val.trim());
            }
        }
        Map<Integer, String> result = new HashMap<>();
        Set<Integer> allPorts = new HashSet<>();
        allPorts.addAll(portIdMap.keySet());
        allPorts.addAll(portDescMap.keySet());
        for (Integer portNum : allPorts) {
            String desc = portDescMap.get(portNum);
            String id = portIdMap.get(portNum);
            result.put(portNum, desc != null && !desc.isBlank() ? desc : id);
        }
        return result;
    }

    public List<LldpNeighborDto> parseLldpNeighbors(List<VariableBinding> remBindings,
                                                    List<VariableBinding> manAddrBindings,
                                                    Map<Integer, String> localPorts) {
        if (remBindings == null || remBindings.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, RawNeighbor> rawMap = new LinkedHashMap<>();
        for (VariableBinding vb : remBindings) {
            OID oid = vb.getOid();
            if (oid == null || !oid.startsWith(OID_LLDP_REM_TABLE) || oid.size() < 14) continue;
            int col = oid.get(10);
            int locPortNum = oid.get(12);
            int remIndex = oid.get(13);
            String key = locPortNum + ":" + remIndex;

            RawNeighbor raw = rawMap.computeIfAbsent(key, k -> {
                RawNeighbor n = new RawNeighbor();
                n.locPortNum = locPortNum;
                n.remIndex = remIndex;
                return n;
            });

            Variable var = vb.getVariable();
            if (var == null || var.isException()) continue;

            switch (col) {
                case 4 -> {
                    try { raw.chassisSubtype = var.toInt(); } catch (Exception ignored) {}
                }
                case 5 -> raw.chassisIdVar = var;
                case 6 -> {
                    try { raw.portSubtype = var.toInt(); } catch (Exception ignored) {}
                }
                case 7 -> raw.portIdVar = var;
                case 8 -> raw.portDesc = extractStringValue(var);
                case 9 -> raw.sysName = extractStringValue(var);
            }
        }

        if (manAddrBindings != null) {
            for (VariableBinding vb : manAddrBindings) {
                OID oid = vb.getOid();
                if (oid == null || !oid.startsWith(OID_LLDP_REM_MAN_ADDR_TABLE) || oid.size() < 20) continue;
                int locPortNum = oid.get(12);
                int remIndex = oid.get(13);
                int subtype = oid.get(14);
                int len = oid.get(15);
                if (subtype == 1 && len == 4) {
                    String ip = oid.get(16) + "." + oid.get(17) + "." + oid.get(18) + "." + oid.get(19);
                    String key = locPortNum + ":" + remIndex;
                    RawNeighbor raw = rawMap.get(key);
                    if (raw != null && raw.managementIp == null) {
                        raw.managementIp = ip;
                    }
                }
            }
        }

        List<LldpNeighborDto> result = new ArrayList<>();
        for (RawNeighbor raw : rawMap.values()) {
            String localPortName = localPorts != null ? localPorts.get(raw.locPortNum) : null;
            if (localPortName == null || localPortName.isBlank()) {
                localPortName = "Port " + raw.locPortNum;
            }

            String chassisId = null;
            String chassisMac = null;
            String remoteMgmtIp = raw.managementIp;

            if (raw.chassisIdVar != null) {
                if (raw.chassisIdVar instanceof OctetString os) {
                    byte[] bytes = os.getValue();
                    if (raw.chassisSubtype != null && raw.chassisSubtype == 4) {
                        chassisMac = LldpNormalizer.macFromBytes(bytes);
                        if (chassisMac == null) {
                            chassisMac = LldpNormalizer.normalizeMac(os.toString());
                        }
                    } else if (raw.chassisSubtype != null && raw.chassisSubtype == 5) {
                        if (bytes.length == 5 && bytes[0] == 1) {
                            String parsedIp = (bytes[1] & 0xFF) + "." + (bytes[2] & 0xFF) + "." +
                                              (bytes[3] & 0xFF) + "." + (bytes[4] & 0xFF);
                            if (remoteMgmtIp == null) remoteMgmtIp = parsedIp;
                        }
                    } else {
                        if (bytes.length == 6) {
                            chassisMac = LldpNormalizer.macFromBytes(bytes);
                        } else {
                            chassisMac = LldpNormalizer.normalizeMac(os.toString());
                        }
                    }
                    chassisId = extractStringValue(os);
                } else {
                    chassisId = extractStringValue(raw.chassisIdVar);
                    chassisMac = LldpNormalizer.normalizeMac(chassisId);
                }
            }

            String remotePortId = null;
            if (raw.portIdVar != null) {
                if (raw.portSubtype != null && raw.portSubtype == 3 && raw.portIdVar instanceof OctetString os) {
                    remotePortId = LldpNormalizer.macFromBytes(os.getValue());
                }
                if (remotePortId == null) {
                    remotePortId = extractStringValue(raw.portIdVar);
                }
            }

            result.add(LldpNeighborDto.builder()
                    .localPortNum(raw.locPortNum)
                    .localPort(localPortName)
                    .remoteChassisId(chassisId)
                    .remoteChassisMac(chassisMac)
                    .remotePortId(remotePortId)
                    .remotePortDesc(raw.portDesc)
                    .remoteSysName(raw.sysName)
                    .remoteManagementIp(remoteMgmtIp)
                    .build());
        }

        return result;
    }

    public static String extractStringValue(Variable var) {
        if (var == null || var.isException()) return null;
        if (var instanceof OctetString os) {
            byte[] bytes = os.getValue();
            if (isPrintableAscii(bytes)) {
                return new String(bytes, StandardCharsets.UTF_8).trim();
            }
            return os.toHexString();
        }
        return var.toString().trim();
    }

    private static boolean isPrintableAscii(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 32 || v > 126) {
                if (v != '\r' && v != '\n' && v != '\t') {
                    return false;
                }
            }
        }
        return true;
    }

    private static class RawNeighbor {
        int locPortNum;
        int remIndex;
        Integer chassisSubtype;
        Variable chassisIdVar;
        Integer portSubtype;
        Variable portIdVar;
        String portDesc;
        String sysName;
        String managementIp;
    }

    private record CounterSnapshot(long timestampMs, long inOctets, long outOctets) {}
}

