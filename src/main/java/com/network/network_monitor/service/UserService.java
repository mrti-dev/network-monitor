package com.network.network_monitor.service;

import com.network.network_monitor.dto.RegisterRequestDto;
import com.network.network_monitor.entity.User;

public interface UserService {

    /**
     * Đăng ký tài khoản người dùng mới vào hệ thống.
     *
     * @param request thông tin form đăng ký
     * @return User thực thể người dùng đã được lưu
     */
    User register(RegisterRequestDto request);

    /**
     * Kiểm tra tên đăng nhập đã tồn tại trong hệ thống chưa.
     *
     * @param username tên đăng nhập cần kiểm tra
     * @return true nếu đã tồn tại, ngược lại false
     */
    boolean existsByUsername(String username);
}
