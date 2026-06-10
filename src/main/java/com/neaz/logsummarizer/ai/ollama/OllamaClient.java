package com.neaz.logsummarizer.ai.ollama;

import com.neaz.logsummarizer.ai.AiClient;
import com.neaz.logsummarizer.config.AiProviderConfig;
import com.neaz.logsummarizer.exception.AiClientException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama")
public class OllamaClient implements AiClient {

    private static final String GENERATE_PATH = "/api/generate";

    private final AiProviderConfig config;
    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    public OllamaClient(AiProviderConfig config, WebClient.Builder webClientBuilder) {
        this.config = config;
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    void init() {
        webClient = webClientBuilder
                .baseUrl(config.getOllama().getBaseUrl())
                .build();
        log.info("OllamaClient initialised — baseUrl={}, model={}",
                config.getOllama().getBaseUrl(), config.getOllama().getModel());
    }

    @Override
    public String complete(String prompt) {
        AiProviderConfig.OllamaProperties ollama = config.getOllama();

        OllamaRequest request = OllamaRequest.builder()
                .model(ollama.getModel())
                .prompt(prompt)
                .stream(false)
                .build();

        log.debug("Sending request to Ollama — model={}", ollama.getModel());

        try {
            OllamaResponse response = webClient.post()
                    .uri(GENERATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new AiClientException(
                                            "Ollama error " + clientResponse.statusCode().value() + ": " + body))))
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(ollama.getTimeoutSeconds()))
                    .block();

            if (response == null || response.getResponse() == null || response.getResponse().isBlank()) {
                throw new AiClientException("Ollama returned an empty response");
            }

            log.debug("Received response from Ollama — done={}", response.isDone());
            return response.getResponse();

        } catch (AiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AiClientException("Failed to communicate with Ollama: " + e.getMessage(), e);
        }
    }
}