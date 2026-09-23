package com.network.network_monitor.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFoundException(ResourceNotFoundException ex, RedirectAttributes redirectAttributes) {
        log.warn("Không tìm thấy tài nguyên: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/devices";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, RedirectAttributes redirectAttributes) {
        log.warn("Lỗi xung đột dữ liệu (Optimistic Locking): {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Thao tác thất bại do dữ liệu đã bị thay đổi bởi một người dùng khác. Vui lòng tải lại trang và thử lại.");
        return "redirect:/devices";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolationException(DataIntegrityViolationException ex, RedirectAttributes redirectAttributes) {
        log.warn("Lỗi toàn vẹn dữ liệu: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Thao tác thất bại do vi phạm ràng buộc dữ liệu.");
        return "redirect:/devices";
    }
    @ExceptionHandler(DuplicateResourceException.class)
    public String handleDuplicate(DuplicateResourceException ex, RedirectAttributes attributes) {
        attributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/devices";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception ex, RedirectAttributes attributes) {
        log.error("Lỗi xử lý yêu cầu", ex);
        attributes.addFlashAttribute("errorMessage", "Không thể hoàn tất thao tác. Vui lòng thử lại sau.");
        return "redirect:/devices";
    }
}
