package com.netsentry;

import com.netsentry.DTOs.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {OAuthController.class, ImageProcessorEndpoints.class})
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException error) {
        String message = error.getReason() == null ? "Request failed." : error.getReason();
        return ResponseEntity.status(error.getStatusCode())
                .headers(error.getHeaders())
                .body(new ApiErrorResponse(error.getStatusCode().value(), message));
    }
}
