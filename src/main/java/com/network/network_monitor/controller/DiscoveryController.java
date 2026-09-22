package com.network.network_monitor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.network.network_monitor.dto.ScanRequestDto;
import com.network.network_monitor.dto.ScanResponseDto;
import com.network.network_monitor.service.DiscoveryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> scanNetwork(@Valid @RequestBody ScanRequestDto request) {
        ScanResponseDto data = discoveryService.scanNetwork(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("error", null);
        response.put("timestamp", LocalDateTime.now());
        response.put("path", "/api/discovery/scan");
        
        return ResponseEntity.ok(response);
    }
}
