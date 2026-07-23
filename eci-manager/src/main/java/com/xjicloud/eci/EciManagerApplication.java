package com.xjicloud.eci;

import com.xjicloud.eci.config.EciProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ECI 容器管理 Demo 启动入口。
 * <p>
 * 排除默认 {@link UserDetailsServiceAutoConfiguration}，本服务仅使用 Api-Key，不使用表单/内存用户。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(EciProperties.class)
public class EciManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EciManagerApplication.class, args);
    }
}
