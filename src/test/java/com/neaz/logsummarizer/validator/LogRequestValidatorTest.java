package com.neaz.logsummarizer.validator;

import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.exception.InvalidLogRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogRequestValidatorTest {

    private LogRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LogRequestValidator();
    }

    private LogEntry validEntry() {
        return LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level("ERROR")
                .service("payment-service")
                .message("Database connection timed out")
                .build();
    }

    // --- success ---

    @Test
    void validate_singleValidEntry_passes() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(List.of(validEntry()))
                .build();

        assertThatNoException().isThrownBy(() -> validator.validate(request));
    }

    @Test
    void validate_exactly500Entries_passes() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            entries.add(validEntry());
        }

        assertThatNoException().isThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(entries).build()));
    }

    @Test
    void validate_allValidLevels_pass() {
        for (String level : List.of("ERROR", "WARN", "INFO", "DEBUG", "FATAL")) {
            LogEntry entry = LogEntry.builder()
                    .timestamp("2025-10-15T10:00:05Z")
                    .level(level)
                    .service("svc")
                    .message("msg")
                    .build();

            assertThatNoException().isThrownBy(() ->
                    validator.validate(LogSummaryRequest.builder().logs(List.of(entry)).build()));
        }
    }

    @Test
    void validate_lowercaseLevel_treatedAsCaseInsensitive() {
        LogEntry entry = LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level("error")
                .service("svc")
                .message("msg")
                .build();

        assertThatNoException().isThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(List.of(entry)).build()));
    }

    // --- null / empty logs ---

    @Test
    void validate_nullLogs_throwsInvalidLogRequestException() {
        LogSummaryRequest request = LogSummaryRequest.builder().logs(null).build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void validate_emptyLogs_throwsInvalidLogRequestException() {
        LogSummaryRequest request = LogSummaryRequest.builder()
                .logs(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("null or empty");
    }

    // --- size limit ---

    @Test
    void validate_501Entries_throwsInvalidLogRequestException() {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            entries.add(validEntry());
        }

        assertThatThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(entries).build()))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("Too many log entries");
    }

    // --- invalid level ---

    @Test
    void validate_unknownLevel_throwsWithLevelName() {
        LogEntry entry = LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level("CRITICAL")
                .service("auth-service")
                .message("Something happened")
                .build();

        assertThatThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(List.of(entry)).build()))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("Invalid log level")
                .hasMessageContaining("CRITICAL");
    }

    @Test
    void validate_invalidLevelReportsIndex() {
        LogEntry good = validEntry();
        LogEntry bad = LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level("TRACE")
                .service("svc")
                .message("msg")
                .build();

        assertThatThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(List.of(good, bad)).build()))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("index 1");
    }

    // --- invalid timestamp ---

    @Test
    void validate_nonIsoTimestamp_throwsWithTimestampValue() {
        LogEntry entry = LogEntry.builder()
                .timestamp("15-10-2025 10:00:05")
                .level("ERROR")
                .service("svc")
                .message("msg")
                .build();

        assertThatThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(List.of(entry)).build()))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("Invalid ISO-8601 timestamp");
    }

    @Test
    void validate_plainTextTimestamp_throwsInvalidLogRequestException() {
        LogEntry entry = LogEntry.builder()
                .timestamp("not-a-timestamp")
                .level("ERROR")
                .service("svc")
                .message("msg")
                .build();

        assertThatThrownBy(() ->
                validator.validate(LogSummaryRequest.builder().logs(List.of(entry)).build()))
                .isInstanceOf(InvalidLogRequestException.class)
                .hasMessageContaining("Invalid ISO-8601 timestamp");
    }
}