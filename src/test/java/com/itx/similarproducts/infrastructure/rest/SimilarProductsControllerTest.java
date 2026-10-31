package com.itx.similarproducts.infrastructure.rest;

import static org.mockito.Mockito.when;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(SimilarProductsController.class)
class SimilarProductsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GetSimilarProductsUseCase getSimilarProducts;

    @Test
    void returnsSimilarProductsFollowingTheContract() {
        when(getSimilarProducts.getSimilarProducts("1")).thenReturn(Mono.just(List.of(
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true),
                new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false))));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("""
                        [
                          {"id":"2","name":"Dress","price":19.99,"availability":true},
                          {"id":"3","name":"Blazer","price":29.99,"availability":false}
                        ]
                        """, true);
    }

    @Test
    void returns404WhenProductDoesNotExist() {
        when(getSimilarProducts.getSimilarProducts("99")).thenReturn(Mono.error(new ProductNotFoundException("99")));

        webTestClient.get().uri("/product/99/similar")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returns502WhenUpstreamIsUnavailable() {
        when(getSimilarProducts.getSimilarProducts("1"))
                .thenReturn(Mono.error(new UpstreamUnavailableException("down", null)));

        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(502);
    }
}
