package com.neaz.logsummarizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.logsummarizer.ai.AiClient;
import com.neaz.logsummarizer.ai.PromptBuilderService;
import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.dto.response.SummaryResponse;
import com.neaz.logsummarizer.exception.AiClientException;
import com.neaz.logsummarizer.exception.InvalidLogRequestException;
import com.neaz.logsummarizer.model.LogStatistics;
import com.neaz.logsummarizer.validator.LogRequestValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogSummarizerServiceImplTest {

    @Mock private LogRequestValidator validator;
    @Mock private LogStatisticsService logStatisticsService;
    @Mock private PromptBuilderService promptBuilderService;
    @Mock private AiClient aiClient;

    private LogSummarizerServiceImpl service;

    private static final String VALID_AI_JSON =
            "{\"summary\":\"DB timeout\",\"key_error_signatures\":[\"timeout\"],\"recommendation\":\"Restart DB\"}";

    @BeforeEach
    void setUp() {
        service = new LogSummarizerServiceImpl(
                validator, logStatisticsService, promptBuilderService, aiClient, new ObjectMapper());
    }

    private LogEntry entry(String level) {
        return LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level(level)
                .service("payment-service")
                .message("Database connection timed out")
                .build();
    }

    private LogStatistics dummyStats() {
        return new LogStatistics(1, 1, 0, 0, 0, 0, Set.of("payment-service"), List.of(), List.of());
    }

    // --- success path ---

    @Test
    void summarizeLogs_anomalousLogs_returnsAiParsedResponse() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("ERROR")))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("the-prompt");
        when(aiClient.complete("the-prompt")).thenReturn(VALID_AI_JSON);

        SummaryResponse response = service.summarizeLogs(request);

        assertThat(response.getSummary()).isEqualTo("DB timeout");
        assertThat(response.getKeyErrorSignatures()).containsExactly("timeout");
        assertThat(response.getRecommendation()).isEqualTo("Restart DB");
    }

    @Test
    void summarizeLogs_aiReturnsJsonInCodeFence_stripsAndParses() {
        String fenced = "```json\n" + VALID_AI_JSON + "\n```";
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("FATAL")))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("p");
        when(aiClient.complete("p")).thenReturn(fenced);

        SummaryResponse response = service.summarizeLogs(request);

        assertThat(response.getSummary()).isEqualTo("DB timeout");
    }

    // --- no anomalies ---

    @Test
    void summarizeLogs_allInfoLogs_returnsNoAnomalyShortCircuit() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("INFO")))
                .build();

        SummaryResponse response = service.summarizeLogs(request);

        assertThat(response.getSummary()).contains("No anomalies detected");
        assertThat(response.getKeyErrorSignatures()).isEmpty();
        assertThat(response.getRecommendation()).isEqualTo("No action required.");
        verifyNoInteractions(aiClient, promptBuilderService, logStatisticsService);
    }

    @Test
    void summarizeLogs_allDebugLogs_returnsNoAnomalyShortCircuit() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("DEBUG")))
                .build();

        SummaryResponse response = service.summarizeLogs(request);

        assertThat(response.getSummary()).contains("No anomalies detected");
        verifyNoInteractions(aiClient);
    }

    // --- filtering ---

    @Test
    void summarizeLogs_mixedLevels_onlyAnomalousPassedToPromptBuilder() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("ERROR"), entry("INFO"), entry("WARN"), entry("DEBUG")))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("p");
        when(aiClient.complete("p")).thenReturn(VALID_AI_JSON);

        service.summarizeLogs(request);

        verify(promptBuilderService).buildPrompt(
                argThat(logs -> logs.size() == 2
                        && logs.stream().allMatch(l -> List.of("ERROR", "WARN").contains(l.getLevel()))),
                any());
    }

    // --- validation failures ---

    @Test
    void summarizeLogs_validatorThrows_propagatesWithoutCallingAi() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("ERROR")))
                .build();
        doThrow(new InvalidLogRequestException("Too many entries")).when(validator).validate(request);

        assertThatThrownBy(() -> service.summarizeLogs(request))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessage("Too many entries");

        verifyNoInteractions(aiClient);
    }

    // --- AI failures ---

    @Test
    void summarizeLogs_aiClientThrows_propagatesAiClientException() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("ERROR")))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("p");
        when(aiClient.complete("p")).thenThrow(new AiClientException("Ollama unreachable"));

        assertThatThrownBy(() -> service.summarizeLogs(request))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Ollama unreachable");
    }

    @Test
    void summarizeLogs_aiReturnsUnparseableJson_throwsIllegalStateException() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(entry("ERROR")))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("p");
        when(aiClient.complete("p")).thenReturn("not json at all");

        assertThatThrownBy(() -> service.summarizeLogs(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI response could not be parsed");
    }

    // --- statistics are computed over all logs, not just filtered ---

    @Test
    void summarizeLogs_statisticsComputedBeforeFiltering() {
        LogEntry errorLog = entry("ERROR");
        LogEntry infoLog  = entry("INFO");
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(errorLog, infoLog))
                .build();

        when(logStatisticsService.compute(anyList())).thenReturn(dummyStats());
        when(promptBuilderService.buildPrompt(anyList(), any())).thenReturn("p");
        when(aiClient.complete("p")).thenReturn(VALID_AI_JSON);

        service.summarizeLogs(request);

        verify(logStatisticsService).compute(argThat(logs -> logs.size() == 2));
    }
}