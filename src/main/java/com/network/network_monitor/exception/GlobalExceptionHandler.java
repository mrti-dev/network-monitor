package com.network.network_monitor.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Xử lý exception toàn cục.
 * <ul>
 *   <li>Request API (Accept: application/json) → trả JSON có errorCode và message</li>
 *   <li>Request Web (Thymeleaf) → redirect kèm flash message</li>
 *   <li>Không bao giờ để stack trace hoặc tên class nội bộ lộ ra client</li>
 * </ul>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // REST JSON error helpers
    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> apiError(HttpStatus status, String errorCode, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now());
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }

    private boolean isApiRequest(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        String contentType = req.getHeader("Content-Type");
        String uri = req.getRequestURI();
        return uri.startsWith("/api/") ||
               (accept != null && accept.contains("application/json")) ||
               (contentType != null && contentType.contains("application/json"));
    }

    // ------------------------------------------------------------------
    // CIDR validation — HTTP 400
    // ------------------------------------------------------------------

    @ExceptionHandler(InvalidCidrException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleInvalidCidr(
            InvalidCidrException ex, HttpServletRequest req) {
        log.debug("CIDR không hợp lệ [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return apiError(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage(), req.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Scan too large — HTTP 400
    // ------------------------------------------------------------------

    @ExceptionHandler(ScanTooLargeException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleScanTooLarge(
            ScanTooLargeException ex, HttpServletRequest req) {
        log.debug("Scan quá lớn: requested={}, limit={}", ex.getRequested(), ex.getLimit());
        return apiError(HttpStatus.BAD_REQUEST, "SCAN_TOO_LARGE", ex.getMessage(), req.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Scan capacity — HTTP 429
    // ------------------------------------------------------------------

    @ExceptionHandler(ScanCapacityException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleScanCapacity(
            ScanCapacityException ex, HttpServletRequest req) {
        log.warn("Đạt giới hạn scan đồng thời.");
        return apiError(HttpStatus.TOO_MANY_REQUESTS, "SCAN_CAPACITY_EXCEEDED", ex.getMessage(), req.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Validation (@Valid) — HTTP 400
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu đầu vào không hợp lệ");
        return apiError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, req.getRequestURI());
    }

    // ------------------------------------------------------------------
    // Web MVC handlers (Thymeleaf redirects)
    // ------------------------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex,
                                         RedirectAttributes redirectAttributes,
                                         HttpServletRequest req) {
        log.warn("Không tìm thấy tài nguyên: {}", ex.getMessage());
        if (isApiRequest(req)) {
            return apiError(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), req.getRequestURI());
        }
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/devices";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleOptimisticLocking(OptimisticLockingFailureException ex,
                                          RedirectAttributes redirectAttributes) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Thao tác thất bại do dữ liệu đã bị thay đổi. Vui lòng tải lại và thử lại.");
        return "redirect:/devices";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex,
                                      RedirectAttributes redirectAttributes) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Thao tác thất bại do vi phạm ràng buộc dữ liệu.");
        return "redirect:/devices";
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public Object handleDuplicate(DuplicateResourceException ex,
                                  RedirectAttributes attributes,
                                  HttpServletRequest req) {
        if (isApiRequest(req)) {
            return apiError(HttpStatus.CONFLICT, "DUPLICATE", ex.getMessage(), req.getRequestURI());
        }
        attributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/devices";
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex,
                                   RedirectAttributes attributes,
                                   HttpServletRequest req) {
        // Log với context nhưng không trả stack trace ra client
        log.error("Lỗi xử lý yêu cầu [{}]: {}", req.getRequestURI(), ex.getClass().getSimpleName(), ex);
        if (isApiRequest(req)) {
            return apiError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Không thể hoàn tất thao tác. Vui lòng thử lại sau.", req.getRequestURI());
        }
        attributes.addFlashAttribute("errorMessage", "Không thể hoàn tất thao tác. Vui lòng thử lại sau.");
        return "redirect:/devices";
    }
}
