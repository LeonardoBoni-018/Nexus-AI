package com.nexusai.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.nexusai", considerNestedRepositories = true)
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /** Relogio injetavel para tornar regras temporais testaveis. Instantes sempre em UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
