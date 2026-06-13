package com.ai.studyassistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.Instant;

/**
 * Centralised exception → HTTP response mapper for the entire application.
 *
 * <p>Uses the RFC 7807 {@link ProblemDetail} format (built into Spring 6+)
 * so every error response follows a consistent JSON shape:
 * <pre>
 * {
 *   "type":     "https://studyassistant.local/errors/unsupported-media-type",
 *   "title":    "Unsupported Media Type",
 *   "status":   415,
 *   "detail":   "Only PDF files are accepted ...",
 *   "instance": "/api/documents/upload",
 *   "timestamp": "2024-01-01T00:00:00Z"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── InvalidFileTypeException → 415 ──────────────────────────────────────

    @ExceptionHandler(InvalidFileTypeException.class)
    public ProblemDetail handleInvalidFileType(InvalidFileTypeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setTitle("Unsupported Media Type");
        problem.setType(URI.create("https://studyassistant.local/errors/unsupported-media-type"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─── MaxUploadSizeExceededException → 413 ────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the maximum allowed upload size of 20 MB.");
        problem.setTitle("Payload Too Large");
        problem.setType(URI.create("https://studyassistant.local/errors/payload-too-large"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ─── GeminiApiException → 502 ────────────────────────────────────────────

    @ExceptionHandler(GeminiApiException.class)
    public ProblemDetail handleGeminiApiException(GeminiApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The AI service is temporarily unavailable. " + ex.getMessage());
        problem.setTitle("AI Service Error");
        problem.setType(URI.create("https://studyassistant.local/errors/ai-service-error"));
        problem.setProperty("timestamp", Instant.now());
        if (ex.getUpstreamStatusCode() > 0) {
            problem.setProperty("upstreamStatus", ex.getUpstreamStatusCode());
        }
        return problem;
    }

    // ─── Generic fallback → 500 ───────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://studyassistant.local/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
