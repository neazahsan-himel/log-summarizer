package com.neaz.logsummarizer.ai;

import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.model.LogStatistics;
import com.neaz.logsummarizer.model.LogStatistics.Frequency;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptBuilderService {

    private static final String SCHEMA = """
            {
              "summary": "string",
              "key_error_signatures": ["string"],
              "recommendation": "string"
            }""";

    public String buildPrompt(List<LogEntry> logs, LogStatistics stats) {
        return """
                You are a Senior Site Reliability Engineer (SRE) analysing application logs.

                Your tasks:
                - Identify recurring failures and unusual patterns
                - Detect cascading failures and possible causal relationships
                - Infer root causes from the evidence provided
                - Provide concrete, actionable remediation recommendations

                === Log Statistics ===
                Total Logs    : %d
                Error Count   : %d
                Warning Count : %d
                Fatal Count   : %d
                Info Count    : %d
                Debug Count   : %d
                Affected Services: %s

                === Top Recurring Messages ===
                %s
                === Top Error Signatures (normalised) ===
                %s
                === Log Entries ===
                %s
                === Output Instructions ===
                Return ONLY valid JSON matching the schema below.
                Do NOT return markdown. Do NOT include explanations. Do NOT wrap the output in code fences.
                Keep the summary concise. Base all conclusions only on the evidence above.

                %s
                """.formatted(
                stats.totalLogs(),
                stats.errorCount(),
                stats.warnCount(),
                stats.fatalCount(),
                stats.infoCount(),
                stats.debugCount(),
                String.join(", ", stats.affectedServices()),
                formatFrequencies(stats.topRecurringMessages()),
                formatFrequencies(stats.topErrorSignatures()),
                formatLogEntries(logs),
                SCHEMA
        );
    }

    private String formatFrequencies(List<Frequency> frequencies) {
        if (frequencies.isEmpty()) {
            return "(none)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frequencies.size(); i++) {
            Frequency f = frequencies.get(i);
            sb.append(String.format("  %d. (×%d) %s%n", i + 1, f.count(), f.label()));
        }
        return sb.toString();
    }

    private String formatLogEntries(List<LogEntry> logs) {
        StringBuilder sb = new StringBuilder();
        for (LogEntry entry : logs) {
            sb.append(String.format("[%s] [%s] [%s] %s%n",
                    entry.getTimestamp(),
                    entry.getLevel(),
                    entry.getService(),
                    entry.getMessage()));
        }
        return sb.toString();
    }
}