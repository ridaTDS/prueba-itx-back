package com.itx.similarproducts.infrastructure.rest;

import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleNotFound(ProductNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ProblemDetail handleUpstreamUnavailable(UpstreamUnavailableException exception) {
        log.warn("{}: {}", exception.getMessage(), String.valueOf(exception.getCause()));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }
}
