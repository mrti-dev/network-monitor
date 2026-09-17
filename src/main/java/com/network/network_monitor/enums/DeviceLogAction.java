package com.network.network_monitor.enums;

/**
 * Hành động hệ thống được ghi nhận trong nhật ký thiết bị.
 */
public enum DeviceLogAction {

    CREATE,
    UPDATE,
    ENTER_MAINTENANCE,
    EXIT_MAINTENANCE,
    SOFT_DELETE,
    REACTIVATE,
    STATUS_CHANGE
}