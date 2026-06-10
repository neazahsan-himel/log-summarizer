package com.neaz.logsummarizer.model;

import java.util.List;
import java.util.Set;

public record LogStatistics(
        int totalLogs,
        int errorCount,
        int warnCount,
        int fatalCount,
        int infoCount,
        int debugCount,
        Set<String> affectedServices,
        List<Frequency> topRecurringMessages,
        List<Frequency> topErrorSignatures
) {
    public record Frequency(String label, int count) {}
}
