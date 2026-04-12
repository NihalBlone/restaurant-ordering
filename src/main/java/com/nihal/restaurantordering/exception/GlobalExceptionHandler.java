package com.nihal.restaurantordering.exception;

import com.nihal.restaurantordering.dto.common.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        log.warn("API exception handled. path={} status={} message={}",
                request.getRequestURI(), exception.getStatus().value(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus())
                .body(buildError(exception.getStatus(), exception.getMessage(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception,
                                                                     HttpServletRequest request) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();
        log.warn("Validation failed. path={} errors={}", request.getRequestURI(), details);
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                      HttpServletRequest request) {
        List<String> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        log.warn("Constraint violation. path={} errors={}", request.getRequestURI(), details);
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception,
                                                                         HttpServletRequest request) {
        String causeMessage = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        log.warn("Data integrity violation. path={} message={}", request.getRequestURI(), causeMessage);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError(HttpStatus.CONFLICT, "Request conflicts with existing data", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unexpected exception. path={}", request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI(), List.of()));
    }

    private ApiErrorResponse buildError(HttpStatus status, String message, String path, List<String> details) {
        return ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .details(details)
                .build();
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
