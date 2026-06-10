package com.neaz.logsummarizer.exception;

public class InvalidLogRequestException extends RuntimeException {

    public InvalidLogRequestException(String message) {
        super(message);
    }
}