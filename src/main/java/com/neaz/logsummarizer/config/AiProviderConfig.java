package com.neaz.logsummarizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProviderConfig {

    private String provider;
    private OllamaProperties ollama = new OllamaProperties();

    @Data
    public static class OllamaProperties {
        private String baseUrl;
        private String model;
        private int timeoutSeconds = 30;
    }
}
