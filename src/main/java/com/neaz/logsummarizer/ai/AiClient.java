package com.neaz.logsummarizer.ai;

/**
 * Port for AI text completion. Implement this interface to add a new provider
 * (Ollama, OpenAI, Gemini, Claude, etc.) — no service-layer changes required.
 */
public interface AiClient {

    /**
     * Send a prompt and return the raw completion text.
     *
     * @param prompt the fully-constructed prompt to send
     * @return raw string response from the AI provider
     * @throws com.neaz.logsummarizer.exception.AiClientException on any transport or provider error
     */
    String complete(String prompt);
}