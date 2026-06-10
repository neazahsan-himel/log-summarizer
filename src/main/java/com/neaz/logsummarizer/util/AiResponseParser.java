package com.neaz.logsummarizer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiResponseParser {

    // Matches ```json ... ``` or ``` ... ``` (non-greedy, dotall)
    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private AiResponseParser() {}

    /**
     * Extracts the JSON object from an LLM response that may be:
     *   - plain JSON
     *   - JSON wrapped in a markdown code fence (```json ... ```)
     *   - JSON embedded in prose with text before/after the braces
     *
     * Falls back to returning the trimmed raw string so the caller's JSON
     * parser produces a meaningful error rather than an NPE.
     */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }

        String candidate = raw.strip();

        // Strip markdown code fence if present
        Matcher fenceMatcher = CODE_FENCE.matcher(candidate);
        if (fenceMatcher.find()) {
            candidate = fenceMatcher.group(1).strip();
        }

        // Locate outermost JSON object bounds — handles leading/trailing prose
        int start = candidate.indexOf('{');
        int end   = candidate.lastIndexOf('}');

        if (start != -1 && end > start) {
            return candidate.substring(start, end + 1);
        }

        return candidate;
    }
}