package com.network.network_monitor.config;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final Set<String> ALLOWED_SUBSCRIBE_TOPICS = Set.of(
            "/topic/dashboard",
            "/topic/alerts",
            "/topic/devices",
            "/topic/ports",
            "/topic/maintenance"
    );

    public static final Pattern DEVICE_METRICS_PATTERN =
            Pattern.compile("^/topic/devices/[0-9]+/metrics$");

    public static boolean isDestinationAllowed(String destination) {
        if (destination == null) return false;
        return ALLOWED_SUBSCRIBE_TOPICS.contains(destination)
                || DEVICE_METRICS_PATTERN.matcher(destination).matches();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        Principal principal = null;
                        if (request instanceof ServletServerHttpRequest servletRequest) {
                            HttpServletRequest req = servletRequest.getServletRequest();
                            principal = req.getUserPrincipal();
                        } else {
                            principal = request.getPrincipal();
                        }

                        if (principal == null) {
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            return false;
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
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
                    if (!isDestinationAllowed(destination)) {
                        throw new IllegalArgumentException("Client chỉ được phép subscribe các topic: " + ALLOWED_SUBSCRIBE_TOPICS + " hoặc pattern /topic/devices/{id}/metrics");
                    }
                } else if (StompCommand.SEND.equals(command)) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/topic/")) {
                        throw new IllegalArgumentException("Client không được phép gửi trực tiếp vào broker topic");
                    }
                }
                return message;
            }
        });
    }
}
