package com.ai.studyassistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when communication with the Gemini API fails — covers
 * non-200 HTTP responses, malformed JSON replies, and empty candidates.
 *
 * <p>Maps to HTTP 502 Bad Gateway because the error originates from an
 * upstream dependency (Google's API), not from the client's request.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class GeminiApiException extends RuntimeException {

    private final int upstreamStatusCode;

    /**
     * Use when the upstream returned a non-200 status.
     *
     * @param message          human-readable description
     * @param upstreamStatusCode HTTP status code returned by Gemini
     */
    public GeminiApiException(String message, int upstreamStatusCode) {
        super(message);
        this.upstreamStatusCode = upstreamStatusCode;
    }

    /**
     * Use for parse failures or empty responses (no HTTP status to report).
     *
     * @param message human-readable description
     * @param cause   the underlying exception
     */
    public GeminiApiException(String message, Throwable cause) {
        super(message, cause);
        this.upstreamStatusCode = -1;
    }

    public int getUpstreamStatusCode() {
        return upstreamStatusCode;
    }
}
