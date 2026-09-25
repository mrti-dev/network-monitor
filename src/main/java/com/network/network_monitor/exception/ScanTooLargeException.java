package com.network.network_monitor.exception;

/**
 * Ném khi subnet yêu cầu scan vượt quá giới hạn host cho phép.
 */
public class ScanTooLargeException extends RuntimeException {

    private final int requested;
    private final int limit;

    public ScanTooLargeException(int requested, int limit) {
        super("Subnet có " + requested + " host, vượt quá giới hạn " + limit + " địa chỉ mỗi lượt quét.");
        this.requested = requested;
        this.limit = limit;
    }

    public int getRequested() { return requested; }
    public int getLimit() { return limit; }
}
