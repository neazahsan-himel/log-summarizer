package com.neaz.logsummarizer.service;

import com.neaz.logsummarizer.dto.request.LogSummaryRequest;
import com.neaz.logsummarizer.dto.response.SummaryResponse;

public interface LogSummarizerService {

    SummaryResponse summarizeLogs(LogSummaryRequest request);
}