package com.network.network_monitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.network.network_monitor.dto.*;
import com.network.network_monitor.exception.DuplicateResourceException;
import com.network.network_monitor.service.*;

@WebMvcTest({DeviceWebController.class, AlertWebController.class})
class WebViewsTest {
    @Autowired MockMvc mvc;
    @MockitoBean DeviceService devices;
    @MockitoBean AlertService alerts;

    @ParameterizedTest
    @CsvSource({"-1,0,0,1", "0,101,0,100", "99,2,2,2"})
    void paginationNormalizesBoundsAndKeepsSize(int page, int size, int expectedPage, int expectedSize) throws Exception {
        when(devices.getAllDevices(any())).thenAnswer(invocation -> {
            Pageable request = invocation.getArgument(0);
            var content = request.getOffset() >= 5 ? List.<DeviceResponseDto>of() :
                    java.util.stream.IntStream.range(0, (int) Math.min(request.getPageSize(), 5 - request.getOffset()))
                            .mapToObj(i -> DeviceResponseDto.builder().id((long) i).name("test").isMonitored(true).build()).toList();
            return new PageImpl<>(content, request, 5);
        });
        when(alerts.getAllAlerts(any())).thenAnswer(invocation -> {
            Pageable request = invocation.getArgument(0);
            var content = request.getOffset() >= 5 ? List.<AlertDto>of() :
                    java.util.stream.IntStream.range(0, (int) Math.min(request.getPageSize(), 5 - request.getOffset()))
                            .mapToObj(i -> AlertDto.builder().id((long) i).deviceId(1L).isDeviceDeleted(true).deviceName("deleted").build()).toList();
            return new PageImpl<>(content, request, 5);
        });
        for (String path : List.of("devices", "alerts")) {
            var result = mvc.perform(get("/" + path).param("page", "" + page).param("size", "" + size))
                    .andExpect(status().isOk()).andReturn();
            Page<?> actual = (Page<?>) result.getModelAndView().getModel().get(path.equals("devices") ? "devicePage" : "alertPage");
            assertThat(actual.getNumber()).isEqualTo(expectedPage);
            assertThat(actual.getSize()).isEqualTo(expectedSize);
            String html = result.getResponse().getContentAsString();
            assertThat(html).doesNotContain("page=-1", "page=99");
            if (actual.getTotalPages() > 1) assertThat(html).contains("size=" + expectedSize);
            if (path.equals("alerts")) assertThat(html).doesNotContain("/devices/metrics/");
        }
    }

    @Test
    void emptyPageShowsZeroRangeAndResetsPage() throws Exception {
        when(devices.getAllDevices(any())).thenAnswer(i -> Page.empty(i.getArgument(0)));
        when(alerts.getAllAlerts(any())).thenAnswer(i -> Page.empty(i.getArgument(0)));
        for (String path : List.of("devices", "alerts")) {
            var result = mvc.perform(get("/" + path).param("page", "50"))
                    .andExpect(status().isOk()).andReturn();
            Page<?> page = (Page<?>) result.getModelAndView().getModel().get(path.equals("devices") ? "devicePage" : "alertPage");
            assertThat(page.getNumber()).isZero();
            assertThat(result.getResponse().getContentAsString()).contains("Hiển thị <span>0</span>");
        }
    }

    @ParameterizedTest
    @CsvSource({"150", "321"})
    void metricsRenderConfiguredOrDefaultThresholdAndReverseImmutableCopy(double threshold) throws Exception {
        when(devices.getDeviceFormById(1L)).thenReturn(DeviceFormDto.builder().id(1L).name("router").build());
        when(devices.getLatencyThreshold(1L)).thenReturn(threshold);
        var newest = MetricLogDto.builder().recordedAt(LocalDateTime.of(2026, 9, 23, 12, 0)).latencyMs(5.0).isReachable(true).build();
        var oldest = MetricLogDto.builder().recordedAt(LocalDateTime.of(2026, 9, 23, 11, 0)).latencyMs(0.0).isReachable(false).build();
        when(devices.getDeviceMetrics(1L)).thenReturn(List.of(newest, oldest));
        mvc.perform(get("/devices/metrics/1")).andExpect(status().isOk())
                .andExpect(model().attribute("metrics", List.of(oldest, newest)))
                .andExpect(content().string(containsString("y: " + threshold)))
                .andExpect(content().string(containsString("m.isReachable ? m.latencyMs : null")))
                .andExpect(content().string(containsString("Thất bại (Timeout)")));
    }

    @Test
    void unexpectedFailureNeverExposesSqlAndBusinessConflictIsShown() throws Exception {
        doThrow(new IllegalStateException("SQL secret constraint devices_ip")).when(devices).saveDevice(any());
        mvc.perform(post("/devices/save").param("name", "router").param("ipAddress", "10.0.0.1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", not(containsString("SQL"))));
        doThrow(new DuplicateResourceException("IP đã tồn tại.")).when(devices).saveDevice(any());
        mvc.perform(post("/devices/save").param("name", "router").param("ipAddress", "10.0.0.1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("IP đã tồn tại.")));
    }
}
