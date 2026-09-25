package com.network.network_monitor.controller;

import com.network.network_monitor.config.AppUserDetailsService;
import com.network.network_monitor.config.SecurityConfig;
import com.network.network_monitor.dto.RegisterRequestDto;
import com.network.network_monitor.entity.User;
import com.network.network_monitor.enums.Role;
import com.network.network_monitor.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AppUserDetailsService appUserDetailsService;

    @Test
    void getRegisterPage_returnsRegisterViewWithEmptyForm() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("registerForm"));
    }

    @Test
    void getLoginPage_returnsLoginView() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
    }

    @Test
    void postRegister_success_redirectsToLogin() throws Exception {
        when(userService.existsByUsername("newuser")).thenReturn(false);
        when(userService.register(any(RegisterRequestDto.class))).thenReturn(User.builder().id(1L).username("newuser").role(Role.VIEWER).build());

        mvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Nguyen Van B")
                        .param("username", "newuser")
                        .param("password", "Pass123456")
                        .param("confirmPassword", "Pass123456")
                        .param("role", "VIEWER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).register(any(RegisterRequestDto.class));
    }

    @Test
    void postRegister_passwordMismatch_returnsRegisterViewWithFieldError() throws Exception {
        mvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Nguyen Van B")
                        .param("username", "newuser2")
                        .param("password", "Pass123456")
                        .param("confirmPassword", "DifferentPassword")
                        .param("role", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("registerForm", "confirmPassword"));

        verify(userService, never()).register(any());
    }

    @Test
    void postRegister_usernameAlreadyExists_returnsRegisterViewWithFieldError() throws Exception {
        when(userService.existsByUsername("existing_user")).thenReturn(true);

        mvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Nguyen Van C")
                        .param("username", "existing_user")
                        .param("password", "Pass123456")
                        .param("confirmPassword", "Pass123456")
                        .param("role", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("registerForm", "username"));

        verify(userService, never()).register(any());
    }

    @Test
    void postRegister_validationViolation_returnsRegisterViewWithErrors() throws Exception {
        mvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "")
                        .param("username", "ab") // quá ngắn (<3)
                        .param("password", "123") // quá ngắn (<6)
                        .param("confirmPassword", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("registerForm", "fullName", "username", "password"));

        verify(userService, never()).register(any());
    }
}
