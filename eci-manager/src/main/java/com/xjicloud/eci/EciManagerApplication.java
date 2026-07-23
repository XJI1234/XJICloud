package com.xjicloud.eci;

import com.xjicloud.eci.config.EciProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(EciProperties.class)
public class EciManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EciManagerApplication.class, args);
    }
}
