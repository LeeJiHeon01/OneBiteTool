package com.example.aicook.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String code, String message) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ErrorBody(exception.code(), exception.getMessage()));
    }

    // The request body may hold allergy data, so neither the body nor the parser
    // message (which can quote it) is logged or echoed back.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("INVALID_BODY", "요청 형식이 올바르지 않아요."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorBody> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("MISSING_PARAMETER", exception.getParameterName() + " 값이 필요해요."));
    }

}
