package com.xjicloud.framework.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xjicloud.framework.config.FrameworkProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
            System.out.println("mode=" + y.getOrDefault("mode", "master"));
            System.out.println("master-url=" + y.getOrDefault("master-url", ""));
            System.out.println("listen-port=" + y.getOrDefault("listen-port", 9090));
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
