package com.neaz.logsummarizer.ai;

import com.neaz.logsummarizer.dto.request.LogEntry;
import com.neaz.logsummarizer.model.LogStatistics;
import com.neaz.logsummarizer.model.LogStatistics.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderServiceTest {

    private PromptBuilderService promptBuilderService;

    @BeforeEach
    void setUp() {
        promptBuilderService = new PromptBuilderService();
    }

    private LogEntry errorEntry() {
        return LogEntry.builder()
                .timestamp("2025-10-15T10:00:05Z")
                .level("ERROR")
                .service("payment-service")
                .message("Database connection timed out after 3001ms")
                .build();
    }

    private LogStatistics statsWithFrequencies() {
        return new LogStatistics(
                10, 3, 2, 1, 3, 1,
                Set.of("payment-service"),
                List.of(new Frequency("Database connection timed out after 3001ms", 2)),
                List.of(new Frequency("Database connection timed out after #ms", 3))
        );
    }

    // --- statistics section ---

    @Test
    void buildPrompt_containsAllCountsFromStatistics() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("Total Logs    : 10")
                .contains("Error Count   : 3")
                .contains("Warning Count : 2")
                .contains("Fatal Count   : 1")
                .contains("Info Count    : 3")
                .contains("Debug Count   : 1");
    }

    @Test
    void buildPrompt_containsAffectedServices() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt).contains("payment-service");
    }

    // --- log entries section ---

    @Test
    void buildPrompt_containsAllLogEntryFields() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("2025-10-15T10:00:05Z")
                .contains("ERROR")
                .contains("payment-service")
                .contains("Database connection timed out after 3001ms");
    }

    @Test
    void buildPrompt_multipleEntries_allPresent() {
        LogEntry warnEntry = LogEntry.builder()
                .timestamp("2025-10-15T10:01:00Z")
                .level("WARN")
                .service("auth-service")
                .message("High memory usage detected")
                .build();
        LogStatistics stats = new LogStatistics(
                2, 1, 1, 0, 0, 0,
                Set.of("payment-service", "auth-service"),
                List.of(), List.of()
        );

        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry(), warnEntry), stats);

        assertThat(prompt)
                .contains("Database connection timed out after 3001ms")
                .contains("High memory usage detected")
                .contains("auth-service");
    }

    // --- frequency sections ---

    @Test
    void buildPrompt_containsTopRecurringMessagesWithCount() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("Database connection timed out after 3001ms")
                .contains("(×2)");
    }

    @Test
    void buildPrompt_containsTopErrorSignaturesWithCount() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("Database connection timed out after #ms")
                .contains("(×3)");
    }

    @Test
    void buildPrompt_emptyFrequencies_showsNonePlaceholder() {
        LogStatistics statsNoFreq = new LogStatistics(
                1, 1, 0, 0, 0, 0,
                Set.of("svc"),
                List.of(),
                List.of()
        );

        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsNoFreq);

        assertThat(prompt).contains("(none)");
    }

    // --- output schema section ---

    @Test
    void buildPrompt_containsJsonOutputSchema() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("\"summary\"")
                .contains("\"key_error_signatures\"")
                .contains("\"recommendation\"");
    }

    @Test
    void buildPrompt_instructsJsonOnlyOutput() {
        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), statsWithFrequencies());

        assertThat(prompt)
                .contains("Return ONLY valid JSON")
                .contains("Do NOT return markdown");
    }

    // --- frequency numbering ---

    @Test
    void buildPrompt_multipleFrequencies_numberedInOrder() {
        LogStatistics stats = new LogStatistics(
                5, 3, 2, 0, 0, 0,
                Set.of("svc"),
                List.of(
                        new Frequency("msg-a", 5),
                        new Frequency("msg-b", 3)
                ),
                List.of()
        );

        String prompt = promptBuilderService.buildPrompt(List.of(errorEntry()), stats);

        int pos1 = prompt.indexOf("1.");
        int pos2 = prompt.indexOf("2.");
        assertThat(pos1).isLessThan(pos2);
    }
}