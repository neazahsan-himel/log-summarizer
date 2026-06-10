package com.neaz.logsummarizer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neaz.logsummarizer.ai.AiClient;
import com.neaz.logsummarizer.ai.PromptBuilderService;
import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.dto.response.SummaryResponse;
import com.neaz.logsummarizer.model.LogStatistics;
import com.neaz.logsummarizer.util.AiResponseParser;
import com.neaz.logsummarizer.util.LogLevelUtils;
import com.neaz.logsummarizer.validator.LogRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSummarizerServiceImpl implements LogSummarizerService {

    private final LogRequestValidator validator;
    private final LogStatisticsService logStatisticsService;
    private final PromptBuilderService promptBuilderService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public SummaryResponse summarizeLogs(LogSummaryRequest request) {
        validator.validate(request);

        List<LogEntry> anomalousLogs = filterAnomalousLogs(request.getLogs());

        if (anomalousLogs.isEmpty()) {
            log.info("No anomalous entries found in {} total log entries", request.getLogs().size());
            return SummaryResponse.builder()
                    .summary("No anomalies detected. All entries are at INFO or DEBUG level.")
                    .keyErrorSignatures(List.of())
                    .recommendation("No action required.")
                    .build();
        }

        log.info("Sending {} anomalous entries (of {} total) to AI", anomalousLogs.size(), request.getLogs().size());

        LogStatistics statistics = logStatisticsService.compute(request.getLogs());
        String prompt = promptBuilderService.buildPrompt(anomalousLogs, statistics);
        String rawJson = aiClient.complete(prompt);

        return parseResponse(rawJson);
    }

    private List<LogEntry> filterAnomalousLogs(List<LogEntry> logs) {
        return logs.stream()
                .filter(entry -> LogLevelUtils.isAnomalous(entry.getLevel()))
                .toList();
    }

    private SummaryResponse parseResponse(String rawJson) {
        String json = AiResponseParser.extractJson(rawJson);
        try {
            return objectMapper.readValue(json, SummaryResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse AI response as valid JSON");
            throw new IllegalStateException("AI response could not be parsed as a valid summary", e);
        }
    }
}