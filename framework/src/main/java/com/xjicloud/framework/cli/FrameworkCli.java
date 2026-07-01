package com.xjicloud.framework.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xjicloud.framework.agent.AdvertiseHostResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import picocli.CommandLine;

@CommandLine.Command(name = "xjicloud-framework", subcommands = {
        FrameworkCli.ModeCommand.class,
        FrameworkCli.MasterCommand.class,
        FrameworkCli.StatusCommand.class,
        FrameworkCli.ConfigCommand.class
})
public class FrameworkCli {

    public static int run(String[] args) {
        return new CommandLine(new FrameworkCli()).execute(args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[]{"status"});
    }

    @CommandLine.Command(name = "mode", subcommands = {ModeShow.class, ModeSet.class})
    static class ModeCommand {}

    @CommandLine.Command(name = "show")
    static class ModeShow implements Runnable {
        @Override
        public void run() {
            System.out.println(readYaml().getOrDefault("mode", "master"));
        }
    }

    @CommandLine.Command(name = "set")
    static class ModeSet implements Runnable {
        @CommandLine.Parameters(index = "0") String mode;
        @Override
        public void run() {
            updateYaml("mode", mode);
            System.out.println("Mode set to " + mode + ". Restart xjicloud-framework service.");
        }
    }

    @CommandLine.Command(name = "master")
    static class MasterCommand implements Runnable {
        @CommandLine.Parameters(index = "0") String url;
        @Override
        public void run() {
            updateYaml("master-url", url);
            System.out.println("Master URL set to " + url);
        }
    }

    @CommandLine.Command(name = "status")
    static class StatusCommand implements Runnable {
        @Override
        public void run() {
            Map<String, Object> y = readYaml();
            String mode = String.valueOf(y.getOrDefault("mode", "master"));
            System.out.println("mode=" + mode);
            System.out.println("master-url=" + y.getOrDefault("master-url", ""));
            System.out.println("listen-port=" + y.getOrDefault("listen-port", 9090));
            Object advertise = y.get("advertise-host");
            String resolved = AdvertiseHostResolver.resolve(advertise == null ? "" : String.valueOf(advertise));
            System.out.println("advertise-host=" + (advertise == null || String.valueOf(advertise).isBlank() ? "(auto)" : advertise));
            System.out.println("resolved-advertise-host=" + resolved);
            Object token = y.get("agent-token");
            if (token != null && !String.valueOf(token).isBlank()) {
                System.out.println("agent-token=" + mask(String.valueOf(token)));
            }
            if ("slave".equalsIgnoreCase(mode)) {
                probeMaster(y, resolved, token == null ? "" : String.valueOf(token));
            }
        }

        private static String mask(String token) {
            if (token.length() <= 8) {
                return "****";
            }
            return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
        }

        @SuppressWarnings("unchecked")
        private static void probeMaster(Map<String, Object> y, String host, String token) {
            String masterUrl = String.valueOf(y.getOrDefault("master-url", ""));
            if (masterUrl.isBlank()) {
                System.out.println("master-probe=SKIP (master-url empty)");
                return;
            }
            if (token.isBlank()) {
                System.out.println("master-probe=SKIP (agent-token empty)");
                return;
            }
            try {
                RestTemplate rt = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Agent-Token", token);
                Map<String, Object> body = Map.of(
                        "name", host,
                        "host", host,
                        "role", "CUSTOM",
                        "systemInfoJson", "{}"
                );
                String url = masterUrl.replaceAll("/$", "") + "/api/v1/agent/register";
                Map<String, Object> resp = rt.postForObject(url, new HttpEntity<>(body, headers), Map.class);
                if (resp != null && Boolean.TRUE.equals(resp.get("success"))) {
                    System.out.println("master-probe=OK (agent-token accepted, host=" + host + ")");
                } else {
                    System.out.println("master-probe=FAIL " + resp);
                }
            } catch (HttpStatusCodeException e) {
                System.out.println("master-probe=FAIL " + e.getStatusCode().value() + " " + e.getResponseBodyAsString());
                if (e.getStatusCode().value() == 401) {
                    System.out.println("hint=从端 agent-token 必须与主端 /etc/xjicloud/framework.yml 中 agent-token 完全一致");
                }
            } catch (Exception e) {
                System.out.println("master-probe=FAIL " + e.getMessage());
                System.out.println("hint=检查从端能否访问 " + masterUrl + "（防火墙/路由）");
            }
        }
    }

    @CommandLine.Command(name = "config")
    static class ConfigCommand implements Runnable {
        @Override
        public void run() {
            System.out.println("Use Framework panel at http://<host>:9090/config for runtime parameters.");
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readYaml() {
        Path path = Path.of("/etc/xjicloud/framework.yml");
        if (!Files.exists(path)) {
            return Map.of("mode", "master", "listen-port", 9090);
        }
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> root = mapper.readValue(path.toFile(), Map.class);
            Object fw = root.get("xjicloud");
            if (fw instanceof Map<?, ?> xji) {
                Object f = xji.get("framework");
                if (f instanceof Map<?, ?> fm) {
                    return new LinkedHashMap<>((Map<String, Object>) fm);
                }
            }
        } catch (IOException ignored) {
        }
        return Map.of();
    }

    static void updateYaml(String key, Object value) {
        Path path = Path.of("/etc/xjicloud/framework.yml");
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> root = Files.exists(path)
                    ? mapper.readValue(path.toFile(), Map.class)
                    : new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> xji = (Map<String, Object>) root.computeIfAbsent("xjicloud", k -> new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> fw = (Map<String, Object>) xji.computeIfAbsent("framework", k -> new LinkedHashMap<>());
            fw.put(key, value);
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
