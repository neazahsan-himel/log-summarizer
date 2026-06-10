package com.neaz.logsummarizer.ai.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.logsummarizer.config.AiProviderConfig;
import com.neaz.logsummarizer.config.WebClientConfig;
import com.neaz.logsummarizer.exception.AiClientException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientTest {

    private MockWebServer mockWebServer;
    private OllamaClient ollamaClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GENERATE_PATH = "/api/generate";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AiProviderConfig config = new AiProviderConfig();
        config.getOllama().setBaseUrl(mockWebServer.url("/").toString().replaceAll("/$", ""));
        config.getOllama().setModel("llama3");
        config.getOllama().setTimeoutSeconds(5);

        ollamaClient = new OllamaClient(config, new WebClientConfig().webClientBuilder());
        ollamaClient.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private MockResponse jsonOk(String responseText) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("response", responseText, "done", true));
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    // --- success ---

    @Test
    void complete_successfulResponse_returnsResponseText() throws Exception {
        mockWebServer.enqueue(jsonOk("{\"summary\":\"ok\"}"));

        String result = ollamaClient.complete("some prompt");

        assertThat(result).isEqualTo("{\"summary\":\"ok\"}");
    }

    @Test
    void complete_sendsModelAndPromptInRequestBody() throws Exception {
        mockWebServer.enqueue(jsonOk("result"));

        ollamaClient.complete("test prompt");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo(GENERATE_PATH);
        String body = request.getBody().readUtf8();
        assertThat(body)
                .contains("\"model\":\"llama3\"")
                .contains("\"prompt\":\"test prompt\"")
                .contains("\"stream\":false");
    }

    @Test
    void complete_usesPostMethod() throws Exception {
        mockWebServer.enqueue(jsonOk("result"));

        ollamaClient.complete("prompt");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
    }

    // --- empty / blank response body ---

    @Test
    void complete_emptyResponseText_throwsAiClientException() throws Exception {
        mockWebServer.enqueue(jsonOk(""));

        assertThatThrownBy(() -> ollamaClient.complete("prompt"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void complete_blankResponseText_throwsAiClientException() throws Exception {
        mockWebServer.enqueue(jsonOk("   "));

        assertThatThrownBy(() -> ollamaClient.complete("prompt"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("empty response");
    }

    // --- HTTP error status codes ---

    @Test
    void complete_ollamaReturns500_throwsAiClientExceptionWithStatus() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() -> ollamaClient.complete("prompt"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("500");
    }

    @Test
    void complete_ollamaReturns503_throwsAiClientException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("Service Unavailable"));

        assertThatThrownBy(() -> ollamaClient.complete("prompt"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("503");
    }

    @Test
    void complete_ollamaReturns404_throwsAiClientException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        assertThatThrownBy(() -> ollamaClient.complete("prompt"))
                .isInstanceOf(AiClientException.class);
    }

    // --- connection failure ---

    @Test
    void complete_connectionRefused_throwsAiClientException() throws IOException {
        MockWebServer dead = new MockWebServer();
        dead.start();
        String deadUrl = dead.url("/").toString().replaceAll("/$", "");
        dead.shutdown();

        AiProviderConfig config = new AiProviderConfig();
        config.getOllama().setBaseUrl(deadUrl);
        config.getOllama().setModel("llama3");
        config.getOllama().setTimeoutSeconds(2);

        OllamaClient unreachable = new OllamaClient(config, new WebClientConfig().webClientBuilder());
        unreachable.init();

        assertThatThrownBy(() -> unreachable.complete("prompt"))
                .isInstanceOf(AiClientException.class)
                .hasMessageContaining("Failed to communicate with Ollama");
    }
}