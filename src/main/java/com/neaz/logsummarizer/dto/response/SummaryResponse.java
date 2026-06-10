package com.neaz.logsummarizer.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponse {

    private String summary;

    @JsonProperty("key_error_signatures")
    private List<String> keyErrorSignatures;

    private String recommendation;
}