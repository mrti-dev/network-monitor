package com.network.network_monitor.exception;

/**
 * Ném khi một CIDR IPv4 không hợp lệ được truyền vào hệ thống.
 * Chứa mã lỗi máy đọc được (errorCode) và thông báo người dùng (message).
 */
public class InvalidCidrException extends RuntimeException {

    private final String errorCode;

    public InvalidCidrException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
