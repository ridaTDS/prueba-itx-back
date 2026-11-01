package com.itx.similarproducts.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void mapsProductNotFoundTo404() {
        ProblemDetail problem = handler.handleNotFound(new ProductNotFoundException("7"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Product not found: 7");
    }

    @Test
    void mapsUpstreamUnavailableTo502() {
        ProblemDetail problem = handler.handleUpstreamUnavailable(
                new UpstreamUnavailableException("Could not get similar ids for product 1", new TimeoutException()));

        assertThat(problem.getStatus()).isEqualTo(502);
        assertThat(problem.getDetail()).isEqualTo("Could not get similar ids for product 1");
    }
}
