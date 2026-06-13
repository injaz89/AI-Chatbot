package com.ai.studyassistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded file is not a valid PDF document.
 *
 * <p>The {@link ResponseStatus} annotation causes Spring MVC to automatically
 * respond with HTTP 415 Unsupported Media Type when this exception escapes
 * a controller, without requiring any extra {@code try/catch} in the controller.
 */
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class InvalidFileTypeException extends RuntimeException {

    /**
     * @param message a human-readable explanation (e.g. "Only PDF files are accepted.")
     */
    public InvalidFileTypeException(String message) {
        super(message);
    }

    /**
     * Use this constructor when you want to preserve the original cause
     * (e.g. an IOException during magic-byte reading).
     *
     * @param message human-readable explanation
     * @param cause   the underlying exception
     */
    public InvalidFileTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
