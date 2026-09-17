package com.xjicloud.eci.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 操作台就绪后开浏览器：WSL 走 cmd.exe，失败只记日志，关闭开关则不启动进程。
 */
class ConsoleBrowserLauncherTest {

    @Test
    void disabledDoesNotStartProcess() {
        RecordingStarter starter = new RecordingStarter();
        ConsoleBrowserLauncher launcher = newLauncher(false, true, starter);

        launcher.open(8081);

        assertThat(starter.commands).isEmpty();
    }

    @Test
    void wslUsesCmdStartOnLoopback() {
        RecordingStarter starter = new RecordingStarter();
        ConsoleBrowserLauncher launcher = newLauncher(true, true, starter);

        launcher.open(8081);

        assertThat(starter.commands).containsExactly(
                List.of("cmd.exe", "/c", "start", "", "http://127.0.0.1:8081/"));
    }

    @Test
    void nonWslUsesXdgOpen() {
        RecordingStarter starter = new RecordingStarter();
        ConsoleBrowserLauncher launcher = newLauncher(true, false, starter);

        launcher.open(9090);

        assertThat(starter.commands).containsExactly(
                List.of("xdg-open", "http://127.0.0.1:9090/"));
    }

    @Test
    void processFailureDoesNotThrow() {
        RecordingStarter starter = new RecordingStarter();
        starter.toThrow = new IOException("cmd.exe missing");
        ConsoleBrowserLauncher launcher = newLauncher(true, true, starter);

        assertThatCode(() -> launcher.open(8081)).doesNotThrowAnyException();
        assertThat(starter.attempts.get()).isEqualTo(1);
    }

    private static ConsoleBrowserLauncher newLauncher(
            boolean enabled, boolean wsl, ConsoleBrowserLauncher.ProcessStarter starter) {
        EciProperties properties = new EciProperties();
        properties.getConsole().setOpenBrowser(enabled);
        return new ConsoleBrowserLauncher(properties, starter, () -> wsl);
    }

    private static final class RecordingStarter implements ConsoleBrowserLauncher.ProcessStarter {
        private final List<List<String>> commands = new ArrayList<>();
        private final AtomicInteger attempts = new AtomicInteger();
        private IOException toThrow;

        @Override
        public void start(List<String> command) throws IOException {
            attempts.incrementAndGet();
            commands.add(List.copyOf(command));
            if (toThrow != null) {
                throw toThrow;
            }
        }
    }
}
