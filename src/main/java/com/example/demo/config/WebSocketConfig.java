package com.example.demo.config;

import com.example.demo.websocket.AiWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AiWebSocketHandler aiWebSocketHandler;
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(aiWebSocketHandler, "/ws/chat")
                .setAllowedOrigins(allowedOrigins.split(","));  // 按逗号分隔配置字符串为数组，不再全放行 *
    }
}