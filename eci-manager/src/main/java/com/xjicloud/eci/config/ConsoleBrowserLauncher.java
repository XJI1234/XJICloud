package com.xjicloud.eci.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 服务就绪后打开本地操作台。失败只打日志，不拖垮启动。
 */
@Component
public class ConsoleBrowserLauncher implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ConsoleBrowserLauncher.class);

    @FunctionalInterface
    interface ProcessStarter {
        void start(List<String> command) throws IOException;
    }

    @FunctionalInterface
    interface WslProbe {
        boolean isWsl();
    }

    private final EciProperties properties;
    private final ProcessStarter processStarter;
    private final WslProbe wslProbe;

    @Autowired
    public ConsoleBrowserLauncher(EciProperties properties) {
        this(properties, ConsoleBrowserLauncher::startDetached, ConsoleBrowserLauncher::detectWsl);
    }

    ConsoleBrowserLauncher(EciProperties properties, ProcessStarter processStarter, WslProbe wslProbe) {
        this.properties = properties;
        this.processStarter = processStarter;
        this.wslProbe = wslProbe;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.getConsole().isOpenBrowser()) {
            return;
        }
        open(resolvePort(event));
    }

    void open(int port) {
        if (!properties.getConsole().isOpenBrowser()) {
            return;
        }
        String url = consoleUrl(port);
        List<String> command = command(wslProbe.isWsl(), url);
        try {
            processStarter.start(command);
            log.info("console browser opening {}", url);
        } catch (Exception e) {
            log.warn("open browser failed url={}: {}", url, e.getMessage());
        }
    }

    static String consoleUrl(int port) {
        return "http://127.0.0.1:" + port + "/";
    }

    static List<String> command(boolean wsl, String url) {
        if (wsl) {
            // Windows `start` treats a quoted first argument as the window title.
            // ProcessBuilder may quote the URL (it contains ':'), so pass an empty title.
            return List.of("cmd.exe", "/c", "start", "", url);
        }
        return List.of("xdg-open", url);
    }

    static boolean detectWsl() {
        if (StringUtils.hasText(System.getenv("WSL_DISTRO_NAME"))
                || StringUtils.hasText(System.getenv("WSL_INTEROP"))) {
            return true;
        }
        try {
            String version = Files.readString(Path.of("/proc/version"));
            return version.toLowerCase(Locale.ROOT).contains("microsoft");
        } catch (Exception e) {
            return false;
        }
    }

    private static void startDetached(List<String> command) throws IOException {
        new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static int resolvePort(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext web) {
            try {
                int port = web.getWebServer().getPort();
                if (port > 0) {
                    return port;
                }
            } catch (Exception ignored) {
                // fall through to environment / default
            }
        }
        Integer port = event.getApplicationContext().getEnvironment()
                .getProperty("local.server.port", Integer.class);
        if (port != null && port > 0) {
            return port;
        }
        return 8081;
    }
}
