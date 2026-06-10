package com.neaz.logsummarizer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.dto.response.SummaryResponse;
import com.neaz.logsummarizer.exception.AiClientException;
import com.neaz.logsummarizer.exception.InvalidLogRequestException;
import com.neaz.logsummarizer.service.LogSummarizerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogSummarizerController.class)
class LogSummarizerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogSummarizerService logSummarizerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String URL = "/api/summarize-logs";

    private LogSummaryRequest validRequest() {
        return LogSummaryRequest.builder()
                .logs(List.of(LogEntry.builder()
                        .timestamp("2025-10-15T10:00:05Z")
                        .level("ERROR")
                        .service("payment-service")
                        .message("DB timeout")
                        .build()))
                .build();
    }

    private SummaryResponse successResponse() {
        return SummaryResponse.builder()
                .summary("Database issue detected")
                .keyErrorSignatures(List.of("DB timeout"))
                .recommendation("Restart the DB")
                .build();
    }

    // --- 200 success ---

    @Test
    void summarizeLogs_validRequest_returns200WithBody() throws Exception {
        when(logSummarizerService.summarizeLogs(any())).thenReturn(successResponse());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Database issue detected"))
                .andExpect(jsonPath("$.key_error_signatures[0]").value("DB timeout"))
                .andExpect(jsonPath("$.recommendation").value("Restart the DB"));
    }

    @Test
    void summarizeLogs_responseContainsAllThreeFields() throws Exception {
        when(logSummarizerService.summarizeLogs(any())).thenReturn(successResponse());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.key_error_signatures").isArray())
                .andExpect(jsonPath("$.recommendation").exists());
    }

    // --- 400 bean validation ---

    @Test
    void summarizeLogs_emptyLogsList_returns400() throws Exception {
        LogSummaryRequest request = LogSummaryRequest.builder().logs(List.of()).build();

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summarizeLogs_missingLogsField_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summarizeLogs_logEntryMissingTimestamp_returns400() throws Exception {
        String body = """
                {"logs":[{"level":"ERROR","service":"svc","message":"msg"}]}
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summarizeLogs_logEntryMissingLevel_returns400() throws Exception {
        String body = """
                {"logs":[{"timestamp":"2025-10-15T10:00:05Z","service":"svc","message":"msg"}]}
                """;

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // --- 400 semantic validation (InvalidLogRequestException) ---

    @Test
    void summarizeLogs_invalidLogRequestException_returns400WithMessage() throws Exception {
        when(logSummarizerService.summarizeLogs(any()))
                .thenThrow(new InvalidLogRequestException("Invalid log level 'CRITICAL'"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid log level 'CRITICAL'"));
    }

    // --- 502 AI client error ---

    @Test
    void summarizeLogs_aiClientException_returns502WithMessage() throws Exception {
        when(logSummarizerService.summarizeLogs(any()))
                .thenThrow(new AiClientException("Ollama unreachable"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Ollama unreachable"));
    }

    // --- 500 unexpected error ---

    @Test
    void summarizeLogs_unexpectedException_returns500() throws Exception {
        when(logSummarizerService.summarizeLogs(any()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    // --- error shape ---

    @Test
    void summarizeLogs_errorResponseContainsTimestampAndPath() throws Exception {
        when(logSummarizerService.summarizeLogs(any()))
                .thenThrow(new AiClientException("down"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value(URL));
    }
}