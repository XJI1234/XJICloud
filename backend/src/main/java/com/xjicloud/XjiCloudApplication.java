package com.xjicloud;

import com.xjicloud.config.StorageProperties;
import com.xjicloud.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class})
public class XjiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(XjiCloudApplication.class, args);
    }
}
