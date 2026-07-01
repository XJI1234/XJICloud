package com.xjicloud.framework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FrameworkApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equals(args[0])) {
            System.exit(com.xjicloud.framework.cli.FrameworkCli.run(args));
        }
        SpringApplication.run(FrameworkApplication.class, args);
    }
}
