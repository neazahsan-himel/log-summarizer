package com.neaz.logsummarizer.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @NotBlank
    private String timestamp;

    @NotBlank
    private String level;

    @NotBlank
    private String service;

    @NotBlank
    private String message;
}