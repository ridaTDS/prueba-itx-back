package com.itx.similarproducts.infrastructure.rest;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import com.itx.similarproducts.domain.model.ProductDetail;
import com.itx.similarproducts.infrastructure.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(SimilarProductsController.class)
@Import(SecurityConfig.class)
class SimilarProductsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GetSimilarProductsUseCase getSimilarProducts;

    // Los tokens se simulan con mockJwt(); el decoder sólo se necesita para construir la configuración
    @MockBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient authenticatedClient;

    @BeforeEach
    void setUp() {
        authenticatedClient = webTestClient.mutateWith(mockJwt());
    }

    @Test
    void returnsSimilarProductsFollowingTheContract() {
        when(getSimilarProducts.getSimilarProducts("1")).thenReturn(Mono.just(List.of(
                new ProductDetail("2", "Dress", new BigDecimal("19.99"), true),
                new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false))));

        authenticatedClient.get().uri("/product/1/similar")
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

        authenticatedClient.get().uri("/product/99/similar")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void returns502WhenUpstreamIsUnavailable() {
        when(getSimilarProducts.getSimilarProducts("1"))
                .thenReturn(Mono.error(new UpstreamUnavailableException("down", null)));

        authenticatedClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(502);
    }

    @Test
    void returns401WithoutToken() {
        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
