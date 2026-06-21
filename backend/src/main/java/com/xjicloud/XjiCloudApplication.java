package com.xjicloud;

import com.xjicloud.config.AdminProperties;
import com.xjicloud.config.CorsProperties;
import com.xjicloud.config.JwtProperties;
import com.xjicloud.config.OssProperties;
import com.xjicloud.config.RedisQueueProperties;
import com.xjicloud.config.StorageProperties;
import com.xjicloud.config.WorkerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        JwtProperties.class,
        StorageProperties.class,
        CorsProperties.class,
        OssProperties.class,
        RedisQueueProperties.class,
        WorkerProperties.class,
        AdminProperties.class
})
public class XjiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(XjiCloudApplication.class, args);
    }
}
