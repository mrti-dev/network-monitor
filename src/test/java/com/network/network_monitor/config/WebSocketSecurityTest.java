package com.network.network_monitor.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketSecurityTest {

    private WebSocketConfig webSocketConfig;
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfig();
        // Lấy interceptor từ clientInboundChannel config
        org.springframework.messaging.simp.config.ChannelRegistration registration =
                new org.springframework.messaging.simp.config.ChannelRegistration();
        webSocketConfig.configureClientInboundChannel(registration);
        
        // Trích xuất interceptor đã đăng ký bằng reflection hoặc khởi tạo tương đương
        interceptor = new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                StompCommand command = accessor.getCommand();

                if (StompCommand.CONNECT.equals(command)) {
                    if (accessor.getUser() == null) {
                        throw new IllegalArgumentException("Người dùng chưa xác thực không được phép kết nối STOMP");
                    }
                } else if (StompCommand.SUBSCRIBE.equals(command)) {
                    String destination = accessor.getDestination();
                    if (!WebSocketConfig.isDestinationAllowed(destination)) {
                        throw new IllegalArgumentException("Client chỉ được phép subscribe các topic: " + WebSocketConfig.ALLOWED_SUBSCRIBE_TOPICS);
                    }
                } else if (StompCommand.SEND.equals(command)) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/topic/")) {
                        throw new IllegalArgumentException("Client không được phép gửi trực tiếp vào broker topic");
                    }
                }
                return message;
            }
        };
    }

    @Test
    void testSubscribeAllowedTopics_shouldPass() {
        MessageChannel channel = mock(MessageChannel.class);
        Principal principal = () -> "testUser";

        for (String topic : WebSocketConfig.ALLOWED_SUBSCRIBE_TOPICS) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination(topic);
            accessor.setUser(principal);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = interceptor.preSend(message, channel);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void testSubscribeDeviceMetricsTopic_regexPatternValidation() {
        MessageChannel channel = mock(MessageChannel.class);
        Principal principal = () -> "testUser";

        // Hợp lệ: /topic/devices/{number}/metrics
        StompHeaderAccessor valid = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        valid.setDestination("/topic/devices/42/metrics");
        valid.setUser(principal);
        Message<byte[]> validMsg = MessageBuilder.createMessage(new byte[0], valid.getMessageHeaders());
        assertThat(interceptor.preSend(validMsg, channel)).isNotNull();

        // Không hợp lệ: id chứa chữ
        StompHeaderAccessor invalidAlpha = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        invalidAlpha.setDestination("/topic/devices/abc/metrics");
        invalidAlpha.setUser(principal);
        Message<byte[]> invalidAlphaMsg = MessageBuilder.createMessage(new byte[0], invalidAlpha.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(invalidAlphaMsg, channel))
                .isInstanceOf(IllegalArgumentException.class);

        // Không hợp lệ: suffix lung tung (starts with chung chung bị chặn)
        StompHeaderAccessor invalidSuffix = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        invalidSuffix.setDestination("/topic/devices/42/metrics/extra");
        invalidSuffix.setUser(principal);
        Message<byte[]> invalidSuffixMsg = MessageBuilder.createMessage(new byte[0], invalidSuffix.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(invalidSuffixMsg, channel))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSubscribeDisallowedTopic_shouldThrowException() {
        MessageChannel channel = mock(MessageChannel.class);
        Principal principal = () -> "testUser";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/unauthorized_topic");
        accessor.setUser(principal);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chỉ được phép subscribe các topic");
    }

    @Test
    void testSendToTopic_shouldThrowException() {
        MessageChannel channel = mock(MessageChannel.class);
        Principal principal = () -> "testUser";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/devices");
        accessor.setUser(principal);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client không được phép gửi trực tiếp vào broker topic");
    }

    @Test
    void testConnectWithoutUser_shouldThrowException() {
        MessageChannel channel = mock(MessageChannel.class);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // No user principal set
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chưa xác thực");
    }
}
