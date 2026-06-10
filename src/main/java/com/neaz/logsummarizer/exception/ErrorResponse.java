package com.neaz.logsummarizer.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ErrorResponse {

    int status;
    String error;
    String message;
    String path;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp;
}