package com.neaz.logsummarizer.validator;

import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.exception.InvalidLogRequestException;
import com.neaz.logsummarizer.util.LogLevelUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class LogRequestValidator {

    private static final int MAX_ENTRIES = 500;

    public void validate(LogSummaryRequest request) {
        List<LogEntry> logs = request.getLogs();

        if (logs == null || logs.isEmpty()) {
            throw new InvalidLogRequestException("Log entries must not be null or empty");
        }

        if (logs.size() > MAX_ENTRIES) {
            throw new InvalidLogRequestException(
                    "Too many log entries: " + logs.size() + " (maximum allowed: " + MAX_ENTRIES + ")");
        }

        for (int i = 0; i < logs.size(); i++) {
            validateEntry(logs.get(i), i);
        }
    }

    private void validateEntry(LogEntry entry, int index) {
        if (!LogLevelUtils.isValid(entry.getLevel())) {
            throw new InvalidLogRequestException(
                    "Invalid log level '" + entry.getLevel() + "' at index " + index
                    + ". Must be one of: " + LogLevelUtils.VALID_LEVELS);
        }

        try {
            Instant.parse(entry.getTimestamp());
        } catch (DateTimeParseException e) {
            throw new InvalidLogRequestException(
                    "Invalid ISO-8601 timestamp '" + entry.getTimestamp() + "' at index " + index);
        }
    }
}