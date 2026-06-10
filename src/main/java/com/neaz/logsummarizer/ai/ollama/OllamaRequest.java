package com.neaz.logsummarizer.ai.ollama;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OllamaRequest {

    private String model;
    private String prompt;
    private boolean stream;
}