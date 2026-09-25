package com.network.network_monitor.service;

import com.network.network_monitor.dto.RegisterRequestDto;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.Role;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.repository.UserRepository;
import com.network.network_monitor.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    @Test
    void register_success_encodesPasswordAndSavesUser() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .fullName("Nguyen Van A")
                .username("nguyenvana")
                .password("Password123")
                .confirmPassword("Password123")
                .role(Role.VIEWER)
                .build();

        when(userRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("$2a$10$encodedHash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        User result = userService.register(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("nguyenvana");
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getPassword()).isEqualTo("$2a$10$encodedHash");
        assertThat(result.getRole()).isEqualTo(Role.VIEWER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getIsDeleted()).isFalse();
    }

    @Test
    void register_operatorRole_isAllowed() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .fullName("Operator User")
                .username("operator_user")
                .password("Secr3t123")
                .confirmPassword("Secr3t123")
                .role(Role.OPERATOR)
                .build();

        when(userRepository.existsByUsername("operator_user")).thenReturn(false);
        when(passwordEncoder.encode("Secr3t123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.register(dto);

        assertThat(result.getRole()).isEqualTo(Role.OPERATOR);
    }

    @Test
    void register_duplicateUsername_throwsDuplicateResourceException() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .fullName("Test")
                .username("existinguser")
                .password("pass123")
                .confirmPassword("pass123")
                .build();

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existinguser");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordMismatch_throwsIllegalArgumentException() {
        RegisterRequestDto dto = RegisterRequestDto.builder()
                .fullName("Test")
                .username("someuser")
                .password("pass123")
                .confirmPassword("passDifferent")
                .build();

        when(userRepository.existsByUsername("someuser")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không khớp");

        verify(userRepository, never()).save(any());
    }
}
