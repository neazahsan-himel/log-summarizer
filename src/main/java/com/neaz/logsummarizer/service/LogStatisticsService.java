package com.neaz.logsummarizer.service;

import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.model.LogStatistics;
import com.neaz.logsummarizer.model.LogStatistics.Frequency;
import com.neaz.logsummarizer.util.LogLevelUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class LogStatisticsService {

    private static final int TOP_N = 5;

    public LogStatistics compute(List<LogEntry> logs) {
        Map<String, Long> levelCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getLevel().toUpperCase(),
                        Collectors.counting()
                ));

        List<LogEntry> anomalous = logs.stream()
                .filter(e -> LogLevelUtils.isAnomalous(e.getLevel()))
                .toList();

        Set<String> affectedServices = anomalous.stream()
                .map(LogEntry::getService)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Frequency> topRecurringMessages = topFrequencies(
                anomalous.stream().map(LogEntry::getMessage).toList()
        );

        List<Frequency> topErrorSignatures = topFrequencies(
                anomalous.stream().map(e -> normalizeMessage(e.getMessage())).toList()
        );

        return new LogStatistics(
                logs.size(),
                count(levelCounts, "ERROR"),
                count(levelCounts, "WARN"),
                count(levelCounts, "FATAL"),
                count(levelCounts, "INFO"),
                count(levelCounts, "DEBUG"),
                affectedServices,
                topRecurringMessages,
                topErrorSignatures
        );
    }

    private List<Frequency> topFrequencies(List<String> items) {
        return items.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .map(e -> new Frequency(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    // Collapse digit runs to # so "timed out after 3001ms" and "timed out after 5432ms"
    // map to the same signature, surfacing the structural pattern rather than specific values.
    private String normalizeMessage(String message) {
        return message.replaceAll("\\d+", "#");
    }

    private int count(Map<String, Long> counts, String level) {
        return counts.getOrDefault(level, 0L).intValue();
    }
}