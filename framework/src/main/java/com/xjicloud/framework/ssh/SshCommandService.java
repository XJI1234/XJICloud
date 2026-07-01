package com.xjicloud.framework.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.xjicloud.framework.node.ManagedNode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.stereotype.Service;

@Service
public class SshCommandService {

    public SshResult execute(ManagedNode node, String command, int timeoutSec) throws Exception {
        JSch jsch = new JSch();
        String keyPath = System.getenv("XJI_FRAMEWORK_SSH_KEY");
        if (keyPath != null && !keyPath.isBlank()) {
            jsch.addIdentity(keyPath);
        }
        Session session = jsch.getSession(node.getSshUser(), node.getHost(), node.getSshPort());
        session.setConfig(new Properties() {{
            put("StrictHostKeyChecking", "no");
        }});
        session.connect(timeoutSec * 1000);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        channel.setOutputStream(stdout);
        channel.setErrStream(stderr);
        channel.connect(timeoutSec * 1000);

        while (!channel.isClosed()) {
            Thread.sleep(200);
        }
        int exitCode = channel.getExitStatus();
        channel.disconnect();
        session.disconnect();
        return new SshResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    public Session openShellSession(ManagedNode node) throws Exception {
        JSch jsch = new JSch();
        String keyPath = System.getenv("XJI_FRAMEWORK_SSH_KEY");
        if (keyPath != null && !keyPath.isBlank()) {
            jsch.addIdentity(keyPath);
        }
        Session session = jsch.getSession(node.getSshUser(), node.getHost(), node.getSshPort());
        session.setConfig(new Properties() {{
            put("StrictHostKeyChecking", "no");
        }});
        session.connect(15000);
        return session;
    }

    public record SshResult(int exitCode, String stdout, String stderr) {}
}
