package com.network.network_monitor.controller;

import com.network.network_monitor.dto.RegisterRequestDto;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Điều hướng trang đăng nhập, đăng ký và trang từ chối quyền truy cập.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/devices";
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "redirect:/devices";
        }
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterRequestDto());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegister(
            @Valid @ModelAttribute("registerForm") RegisterRequestDto form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (form.getPassword() != null && form.getConfirmPassword() != null
                && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Mật khẩu xác nhận không khớp.");
        }

        if (form.getUsername() != null && userService.existsByUsername(form.getUsername())) {
            bindingResult.rejectValue("username", "error.username", "Tên đăng nhập đã tồn tại trong hệ thống.");
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.register(form);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.");
            return "redirect:/login?registered=true";
        } catch (DuplicateResourceException ex) {
            bindingResult.rejectValue("username", "error.username", ex.getMessage());
            return "auth/register";
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý đăng ký tài khoản: ", ex);
            model.addAttribute("errorMessage", "Đã có lỗi xảy ra trong quá trình đăng ký. Vui lòng thử lại.");
            return "auth/register";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }
}
