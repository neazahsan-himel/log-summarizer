package com.neaz.logsummarizer.controller;

import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.dto.response.SummaryResponse;
import com.neaz.logsummarizer.service.LogSummarizerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LogSummarizerController {

    private final LogSummarizerService logSummarizerService;

    @PostMapping("/summarize-logs")
    public ResponseEntity<SummaryResponse> summarizeLogs(@Valid @RequestBody LogSummaryRequest request) {
        MDC.put("requestId", UUID.randomUUID().toString());
        try {
            log.info("Received summarize-logs request with {} log entries", request.getLogs().size());
            SummaryResponse response = logSummarizerService.summarizeLogs(request);
            return ResponseEntity.ok(response);
        } finally {
            MDC.remove("requestId");
        }
    }
}