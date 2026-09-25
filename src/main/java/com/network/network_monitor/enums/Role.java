package com.network.network_monitor.enums;

/**
 * Vai trò phân quyền người dùng trong hệ thống.
 * <ul>
 *   <li>ADMIN    – toàn quyền: quản lý device, toggle, xóa, scan, xem</li>
 *   <li>OPERATOR – điều hành: scan, toggle monitoring, xem; không xóa</li>
 *   <li>VIEWER   – chỉ đọc: xem thiết bị, cảnh báo, số liệu</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    OPERATOR,
    VIEWER
}