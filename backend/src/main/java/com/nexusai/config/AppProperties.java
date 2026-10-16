package com.nexusai.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        boolean registrationEnabled,
        String corsAllowedOrigins,
        boolean demoSeed,
        int maxQueryDays,
        boolean requireSeparateApprover,
        String metricsToken,
        Ai ai) {

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public record Ai(
            String provider,
            String model,
            String apiKey,
            String baseUrl,
            int maxToolCalls,
            Duration requestTimeout,
            int maxOutputTokens,
            int maxConversationHistory,
            int rateLimitPerMinute) {

        public boolean configured() {
            return provider != null && !provider.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
