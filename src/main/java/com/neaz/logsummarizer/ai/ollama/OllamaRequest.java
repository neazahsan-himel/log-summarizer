package com.neaz.logsummarizer.ai.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Options options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class Options {
        @JsonProperty("num_ctx")
        private int numCtx;
    }
}