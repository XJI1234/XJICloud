package com.xjicloud.framework.terminal;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.xjicloud.framework.node.NodeService;
import com.xjicloud.framework.ssh.SshCommandService;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final NodeService nodeService;
    private final SshCommandService sshService;

    public TerminalWebSocketHandler(NodeService nodeService, SshCommandService sshService) {
        this.nodeService = nodeService;
        this.sshService = sshService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String nodeIdStr = (String) session.getAttributes().get("nodeId");
        UUID nodeId = UUID.fromString(nodeIdStr);
        var node = nodeService.getNode(nodeId);
        Session sshSession = sshService.openShellSession(node);
        ChannelShell channel = (ChannelShell) sshSession.openChannel("shell");
        channel.setPty(true);
        channel.connect();

        session.getAttributes().put("sshSession", sshSession);
        session.getAttributes().put("channel", channel);

        InputStream in = channel.getInputStream();
        Thread reader = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                while (channel.isConnected() && session.isOpen()) {
                    int read = in.read(buffer);
                    if (read < 0) break;
                    session.sendMessage(new TextMessage(new String(buffer, 0, read)));
                }
            } catch (Exception ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
        session.getAttributes().put("reader", reader);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChannelShell channel = (ChannelShell) session.getAttributes().get("channel");
        if (channel != null && channel.isConnected()) {
            OutputStream out = channel.getOutputStream();
            out.write(message.getPayload().getBytes());
            out.flush();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ChannelShell channel = (ChannelShell) session.getAttributes().get("channel");
        Session sshSession = (Session) session.getAttributes().get("sshSession");
        if (channel != null) channel.disconnect();
        if (sshSession != null) sshSession.disconnect();
    }
}
