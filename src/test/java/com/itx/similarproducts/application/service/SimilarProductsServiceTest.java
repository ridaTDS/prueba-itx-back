package com.itx.similarproducts.application.service;

import static org.mockito.Mockito.when;

import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SimilarProductsServiceTest {

    private static final ProductDetail SHIRT = new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true);
    private static final ProductDetail DRESS = new ProductDetail("2", "Dress", new BigDecimal("19.99"), true);
    private static final ProductDetail BLAZER = new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false);

    @Mock
    private ProductCatalogPort productCatalog;

    private SimilarProductsService service;

    @BeforeEach
    void setUp() {
        service = new SimilarProductsService(productCatalog);
    }

    @Test
    void returnsDetailsInSimilarityOrderEvenWhenResponsesArriveOutOfOrder() {
        when(productCatalog.getSimilarProductIds("4")).thenReturn(Mono.just(List.of("3", "1", "2")));
        when(productCatalog.getProductDetail("3")).thenReturn(Mono.just(BLAZER).delayElement(Duration.ofMillis(100)));
        when(productCatalog.getProductDetail("1")).thenReturn(Mono.just(SHIRT));
        when(productCatalog.getProductDetail("2")).thenReturn(Mono.just(DRESS).delayElement(Duration.ofMillis(50)));

        StepVerifier.create(service.getSimilarProducts("4"))
                .expectNext(List.of(BLAZER, SHIRT, DRESS))
                .verifyComplete();
    }

    @Test
    void skipsSimilarProductsThatDoNotExistOrFail() {
        when(productCatalog.getSimilarProductIds("5")).thenReturn(Mono.just(List.of("1", "2", "6", "7")));
        when(productCatalog.getProductDetail("1")).thenReturn(Mono.just(SHIRT));
        when(productCatalog.getProductDetail("2")).thenReturn(Mono.empty());
        when(productCatalog.getProductDetail("6"))
                .thenReturn(Mono.error(new UpstreamUnavailableException("boom", null)));
        when(productCatalog.getProductDetail("7")).thenReturn(Mono.just(BLAZER));

        StepVerifier.create(service.getSimilarProducts("5"))
                .expectNext(List.of(SHIRT, BLAZER))
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenThereAreNoSimilarProducts() {
        when(productCatalog.getSimilarProductIds("1")).thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.getSimilarProducts("1"))
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void ignoresDuplicatedIds() {
        when(productCatalog.getSimilarProductIds("4")).thenReturn(Mono.just(List.of("1", "1")));
        when(productCatalog.getProductDetail("1")).thenReturn(Mono.just(SHIRT));

        StepVerifier.create(service.getSimilarProducts("4"))
                .expectNext(List.of(SHIRT))
                .verifyComplete();
    }

    @Test
    void propagatesNotFoundWhenTheRequestedProductDoesNotExist() {
        when(productCatalog.getSimilarProductIds("99")).thenReturn(Mono.error(new ProductNotFoundException("99")));

        StepVerifier.create(service.getSimilarProducts("99"))
                .expectError(ProductNotFoundException.class)
                .verify();
    }
}
