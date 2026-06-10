package com.neaz.logsummarizer.util;

import java.util.Set;

public final class LogLevelUtils {

    public static final Set<String> VALID_LEVELS = Set.of("ERROR", "WARN", "INFO", "DEBUG", "FATAL");
    public static final Set<String> ANOMALOUS_LEVELS = Set.of("ERROR", "WARN", "FATAL");

    private LogLevelUtils() {}

    public static boolean isValid(String level) {
        return level != null && VALID_LEVELS.contains(level.toUpperCase());
    }

    public static boolean isAnomalous(String level) {
        return level != null && ANOMALOUS_LEVELS.contains(level.toUpperCase());
    }
}