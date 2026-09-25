package com.network.network_monitor.service.impl;

import com.network.network_monitor.dto.RegisterRequestDto;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.Role;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.repository.UserRepository;
import com.network.network_monitor.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userRepository.existsByUsername(username.trim());
    }

    @Override
    @Transactional
    public User register(RegisterRequestDto request) {
        String trimmedUsername = request.getUsername().trim();

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new DuplicateResourceException("Tên đăng nhập '" + trimmedUsername + "' đã tồn tại trong hệ thống.");
        }

        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu và mật khẩu xác nhận không khớp.");
        }

        // Vai trò: chỉ cho phép chọn VIEWER hoặc OPERATOR khi tự đăng ký (không cho tự đăng ký ADMIN)
        Role role = Role.VIEWER;
        if (request.getRole() == Role.OPERATOR) {
            role = Role.OPERATOR;
        }

        User user = User.builder()
                .fullName(request.getFullName() != null ? request.getFullName().trim() : "")
                .username(trimmedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .isDeleted(false)
                .build();

        User saved = userRepository.save(user);
        log.info("Người dùng mới đã đăng ký thành công: id={}, username={}, role={}", saved.getId(), saved.getUsername(), saved.getRole());
        return saved;
    }
}
