package com.network.network_monitor.exception;

/**
 * Ném khi đã đạt giới hạn số lượt scan đồng thời.
 */
public class ScanCapacityException extends RuntimeException {

    public ScanCapacityException(int maxConcurrent) {
        super("Hệ thống đang xử lý tối đa " + maxConcurrent +
              " lượt quét đồng thời. Vui lòng thử lại sau.");
    }
}
