package com.network.network_monitor.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.network.network_monitor.dto.AlertProjection;
import com.network.network_monitor.entity.Alert;
import com.network.network_monitor.entity.Device;
import com.network.network_monitor.enums.AlertType;
import com.network.network_monitor.notification.AlertCreated;
import com.network.network_monitor.repository.AlertRepository;

class AlertMappingTest {
    @Test
    void publisherCarriesOnlyCommittedIdentifierAndMessage() {
        var repository = mock(AlertRepository.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any())).thenAnswer(invocation -> {
            Alert alert = invocation.getArgument(0);
            alert.setId(42L);
            return alert;
        });
        new AlertServiceImpl(repository, publisher).triggerAlert(Device.builder().id(1L).build(), "offline", AlertType.OFFLINE);
        var event = ArgumentCaptor.forClass(AlertCreated.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new AlertCreated(42L, "offline"));
    }

    @Test
    void invalidOrNullNativeEnumHasExplicitError() {
        var repository = mock(AlertRepository.class);
        var projection = mock(AlertProjection.class);
        when(projection.getId()).thenReturn(42L);
        when(projection.getAlertType()).thenReturn(null, "BROKEN");
        when(repository.findAllAlertsWithDevice(any())).thenReturn(new PageImpl<>(List.of(projection)));
        var service = new AlertServiceImpl(repository, mock(ApplicationEventPublisher.class));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.getAllAlerts(PageRequest.of(0, 20)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("42")
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }
}
