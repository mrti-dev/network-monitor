package com.network.network_monitor.util;

import com.network.network_monitor.exception.InvalidCidrException;

/**
 * Validates and normalises IPv4 CIDR notation without relying on
 * InetAddress.getByName (which can resolve hostnames) or silently
 * converting parse errors to 0.0.0.0.
 *
 * <p>Behaviour for edge-case prefixes:
 * <ul>
 *   <li>/32 – single host; yields exactly that one address as both network and host</li>
 *   <li>/31 – point-to-point link (RFC 3021); yields both addresses (no net/broadcast)</li>
 *   <li>/0  – rejected; exceeds the 254-host scan limit enforced separately</li>
 * </ul>
 */
public final class CidrValidator {

    /** Minimum prefix supported (we reject /0 because it implies 2^32 hosts). */
    public static final int MIN_PREFIX = 1;
    /** Maximum prefix (single host). */
    public static final int MAX_PREFIX = 32;

    private CidrValidator() {}

    /**
     * Parses and validates a CIDR string, returning the normalised network address + prefix.
     *
     * @param cidr raw input, e.g. "192.168.1.5/24"
     * @return normalised form, e.g. "192.168.1.0/24"
     * @throws InvalidCidrException if the input is syntactically or semantically invalid
     */
    public static String normalise(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new InvalidCidrException("CIDR_BLANK", "CIDR không được để trống");
        }

        int slashIdx = cidr.indexOf('/');
        if (slashIdx < 0) {
            throw new InvalidCidrException("CIDR_NO_PREFIX", "CIDR thiếu prefix (ví dụ: 192.168.1.0/24)");
        }

        String ipPart = cidr.substring(0, slashIdx).trim();
        String prefixPart = cidr.substring(slashIdx + 1).trim();

        // --- Parse prefix ---
        int prefix;
        try {
            prefix = Integer.parseInt(prefixPart);
        } catch (NumberFormatException e) {
            throw new InvalidCidrException("CIDR_PREFIX_NAN", "Prefix không phải số nguyên: " + prefixPart);
        }
        if (prefix < MIN_PREFIX || prefix > MAX_PREFIX) {
            throw new InvalidCidrException("CIDR_PREFIX_RANGE",
                    "Prefix phải trong khoảng " + MIN_PREFIX + "–" + MAX_PREFIX + ", nhận được: " + prefix);
        }

        // --- Parse IPv4 address strictly ---
        int ipInt = parseStrictIpv4(ipPart);

        // --- Normalise: apply network mask ---
        int mask = (prefix == 32) ? 0xFFFFFFFF : (0xFFFFFFFF << (32 - prefix));
        int networkInt = ipInt & mask;

        return intToIpv4(networkInt) + "/" + prefix;
    }

    /**
     * Parses a dotted-decimal IPv4 address.  Rejects leading zeros, hostnames,
     * and octets outside 0–255.
     */
    public static int parseStrictIpv4(String ip) {
        String[] octets = ip.split("\\.", -1);
        if (octets.length != 4) {
            throw new InvalidCidrException("CIDR_BAD_IP",
                    "Địa chỉ IP phải có đúng 4 octet, nhận được: " + ip);
        }
        int val = 0;
        for (String octetStr : octets) {
            if (octetStr.isEmpty() || !octetStr.chars().allMatch(Character::isDigit)) {
                throw new InvalidCidrException("CIDR_BAD_OCTET",
                        "Octet không hợp lệ (không phải số): '" + octetStr + "' trong " + ip);
            }
            // Reject leading zeros (e.g. "01" could be interpreted as octal)
            if (octetStr.length() > 1 && octetStr.charAt(0) == '0') {
                throw new InvalidCidrException("CIDR_LEADING_ZERO",
                        "Octet không được có số 0 đầu: '" + octetStr + "'");
            }
            int octet = Integer.parseInt(octetStr);
            if (octet < 0 || octet > 255) {
                throw new InvalidCidrException("CIDR_OCTET_RANGE",
                        "Mỗi octet phải trong khoảng 0–255, nhận được: " + octet);
            }
            val = (val << 8) | octet;
        }
        return val;
    }

    /** Converts a 32-bit integer to dotted-decimal IPv4 string. */
    public static String intToIpv4(int val) {
        return ((val >> 24) & 0xFF) + "." +
               ((val >> 16) & 0xFF) + "." +
               ((val >> 8)  & 0xFF) + "." +
               ( val        & 0xFF);
    }

    /**
     * Returns the number of host addresses in the subnet.
     * <ul>
     *   <li>/32 → 1 (the host itself)</li>
     *   <li>/31 → 2 (RFC 3021 point-to-point)</li>
     *   <li>/30 → 2 usable hosts</li>
     *   <li>/24 → 254 usable hosts</li>
     * </ul>
     */
    public static int hostCount(int prefix) {
        if (prefix == 32) return 1;
        if (prefix == 31) return 2;
        // 2^(32-prefix) - 2  (subtract network + broadcast)
        return (1 << (32 - prefix)) - 2;
    }
}
