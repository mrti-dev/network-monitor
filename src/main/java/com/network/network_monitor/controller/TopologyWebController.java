package com.network.network_monitor.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.network.network_monitor.dto.DeviceResponseDto;
import com.network.network_monitor.dto.NetworkLinkDto;
import com.network.network_monitor.dto.NetworkLinkFormDto;
import com.network.network_monitor.dto.TopologyDataDto;
import com.network.network_monitor.dto.TopologyDiscoveryResultDto;
import com.network.network_monitor.service.DeviceService;
import com.network.network_monitor.service.TopologyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TopologyWebController {

    private final TopologyService topologyService;
    private final DeviceService deviceService;

    @GetMapping("/topology")
    public String viewTopology(Model model) {
        TopologyDataDto topologyData = topologyService.getTopologyData();
        List<DeviceResponseDto> devices = deviceService.getAllDevices(org.springframework.data.domain.PageRequest.of(0, 500)).getContent();

        model.addAttribute("topology", topologyData);
        model.addAttribute("devices", devices);
        model.addAttribute("linkForm", new NetworkLinkFormDto());

        return "operations/topology";
    }

    @GetMapping("/api/topology")
    @ResponseBody
    public TopologyDataDto getTopologyApi() {
        return topologyService.getTopologyData();
    }

    @PostMapping("/api/topology/links")
    @ResponseBody
    public ResponseEntity<?> createLinkApi(@Valid @RequestBody NetworkLinkFormDto form) {
        try {
            NetworkLinkDto created = topologyService.createLink(form);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/topology/links/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteLinkApi(@PathVariable Long id) {
        topologyService.deleteLink(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/api/topology/auto-discover")
    @ResponseBody
    public ResponseEntity<?> autoDiscoverApi() {
        TopologyDiscoveryResultDto res = topologyService.autoDiscoverLinksDetailed();
        int issues = res.getFailedDevices() + res.getUnsupportedDevices();
        String message;
        if (res.getCreatedCount() > 0 || res.getUpdatedCount() > 0) {
            message = String.format("Đã phát hiện %d liên kết mới, cập nhật %d liên kết (Quét %d thiết bị, %d không hỗ trợ/lỗi)",
                    res.getCreatedCount(), res.getUpdatedCount(), res.getScannedDevices(), issues);
        } else {
            message = String.format("Không tìm thấy liên kết LLDP mới từ %d thiết bị đã quét (%d không hỗ trợ/lỗi)",
                    res.getScannedDevices(), issues);
        }
        return ResponseEntity.ok(Map.of(
                "created", res.getCreatedCount(),
                "updated", res.getUpdatedCount(),
                "scanned", res.getScannedDevices(),
                "failed", issues,
                "message", message
        ));
    }

    @PostMapping("/topology/links/save")
    public String saveLinkForm(@Valid @ModelAttribute("linkForm") NetworkLinkFormDto form,
                               BindingResult result,
                               RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Dữ liệu liên kết không hợp lệ");
            return "redirect:/topology";
        }
        try {
            if (form.getId() == null) {
                topologyService.createLink(form);
                redirectAttributes.addFlashAttribute("successMessage", "Thêm liên kết mạng thành công!");
            } else {
                topologyService.updateLink(form.getId(), form);
                redirectAttributes.addFlashAttribute("successMessage", "Cập nhật liên kết mạng thành công!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/topology";
    }

    @PostMapping("/topology/links/{id}/delete")
    public String deleteLinkForm(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            topologyService.deleteLink(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa liên kết mạng!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/topology";
    }

    @PostMapping("/topology/auto-discover")
    public String autoDiscoverForm(RedirectAttributes redirectAttributes) {
        try {
            TopologyDiscoveryResultDto res = topologyService.autoDiscoverLinksDetailed();
            int totalIssue = res.getFailedDevices() + res.getUnsupportedDevices();
            if (res.getCreatedCount() > 0 || res.getUpdatedCount() > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Tự động phát hiện liên kết: %d liên kết mới, %d liên kết cập nhật (Đã quét %d thiết bị SNMP, %d không hỗ trợ/lỗi).",
                                res.getCreatedCount(), res.getUpdatedCount(), res.getScannedDevices(), totalIssue));
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Đã quét %d thiết bị SNMP (%d không hỗ trợ/lỗi). Không tìm thấy liên kết LLDP mới.",
                                res.getScannedDevices(), totalIssue));
            }
        } catch (Exception e) {
            log.error("Lỗi khi tự động phát hiện liên kết: ", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi phát hiện liên kết: " + e.getMessage());
        }
        return "redirect:/topology";
    }
}
