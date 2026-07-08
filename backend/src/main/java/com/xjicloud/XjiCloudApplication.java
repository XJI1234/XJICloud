package com.xjicloud;

import com.xjicloud.config.StorageProperties;
import com.xjicloud.config.JwtProperties;
import com.xjicloud.config.OssProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.xjicloud.config")
@EnableConfigurationProperties({JwtProperties.class, StorageProperties.class, OssProperties.class})
public class XjiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(XjiCloudApplication.class, args);
    }
}
