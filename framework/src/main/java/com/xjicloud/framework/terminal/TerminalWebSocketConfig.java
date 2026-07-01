package com.xjicloud.framework.terminal;

import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler handler;

    public TerminalWebSocketConfig(TerminalWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/terminal/{nodeId}")
                .addInterceptors(new HttpSessionHandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.http.server.ServerHttpResponse response,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            Map<String, Object> attributes
                    ) {
                        String path = request.getURI().getPath();
                        String nodeId = path.substring(path.lastIndexOf('/') + 1);
                        attributes.put("nodeId", nodeId);
                        return true;
                    }
                })
                .setAllowedOrigins("*");
    }
}
