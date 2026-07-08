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
                You are cautious and evidence-driven. You never speculate and you never invent facts.

                Your tasks:
                - Identify recurring failures and unusual patterns
                - Detect cascading failures and possible causal relationships
                - Infer root causes ONLY when directly supported by the evidence provided
                - Provide concrete, actionable remediation recommendations

                === Evidence Rules (follow strictly) ===
                - Every statement in "summary" and "recommendation" must be traceable to at least one log entry above. Do not state anything the logs do not support.
                - If the evidence is insufficient to determine a root cause, explicitly say so in the summary (e.g. "Root cause cannot be determined from the available logs.") instead of guessing.
                - Treat any component, mechanism, or safeguard already mentioned in the logs (including but not limited to: circuit breaker, retry/retries, cache, queue, database, connection pool, load balancer, rate limiter) as ALREADY EXISTING in the system.
                - NEVER recommend implementing, adding, introducing, or building a component that already appears in the logs. If a circuit breaker "opened" or "tripped", it already exists — do not recommend implementing one.
                - Do NOT invent infrastructure, services, databases, dependencies, or architecture that is not mentioned in the logs.
                - Recommendations must focus on investigation and remediation of the observed evidence, not on implementing new systems. Prefer verbs like "Investigate", "Verify", "Inspect", and "Review" over "Implement", "Add", "Introduce", or "Build" — use those only if the logs clearly show a required safeguard is absent or has failed to prevent the incident.
                - "key_error_signatures" must list every distinct error signature present in the logs and error signatures below. Do not omit any unique error, and do not merge distinct errors together.
                - Do not hedge, editorialize, or add disclaimers beyond what these rules require. Be deterministic: given the same logs, produce the same conclusions.

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
                Keep the summary concise. Base all conclusions only on the evidence above. Do not speculate beyond it.

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