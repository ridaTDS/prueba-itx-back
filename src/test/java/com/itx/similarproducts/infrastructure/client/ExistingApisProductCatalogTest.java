package com.itx.similarproducts.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ExistingApisProductCatalogTest {

    private MockWebServer server;
    private ExistingApisProductCatalog catalog;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        ExistingApisProperties properties = new ExistingApisProperties(
                server.url("/").toString(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                50,
                Duration.ofSeconds(1),
                new ExistingApisProperties.Cache(Duration.ofMinutes(10), Duration.ofMinutes(1), 100));
        WebClient webClient = new ExistingApisClientConfig().existingApisWebClient(WebClient.builder(), properties);
        catalog = new ExistingApisProductCatalog(webClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void parsesNumericSimilarIdsAsStrings() {
        server.enqueue(json(200, "[2,3,4]"));

        StepVerifier.create(catalog.getSimilarProductIds("1"))
                .expectNext(List.of("2", "3", "4"))
                .verifyComplete();
    }

    @Test
    void mapsSimilarIds404ToProductNotFound() {
        server.enqueue(json(404, "{\"message\":\"Product not found\"}"));

        StepVerifier.create(catalog.getSimilarProductIds("99"))
                .expectError(ProductNotFoundException.class)
                .verify();
    }

    @Test
    void mapsSimilarIdsServerErrorToUpstreamUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(catalog.getSimilarProductIds("1"))
                .expectError(UpstreamUnavailableException.class)
                .verify();
    }

    @Test
    void returnsProductDetail() {
        server.enqueue(json(200, "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}"));

        StepVerifier.create(catalog.getProductDetail("1"))
                .expectNext(new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true))
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenProductDetailIsNotFound() {
        server.enqueue(json(404, "{\"message\":\"Product not found\"}"));

        StepVerifier.create(catalog.getProductDetail("5")).verifyComplete();
    }

    @Test
    void failsWhenProductDetailReturnsServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(catalog.getProductDetail("6")).expectError().verify();
    }

    @Test
    void failsFastWhenProductDetailIsSlowButKeepsTheResultForNextRequests() throws InterruptedException {
        server.enqueue(json(200, "{\"id\":\"100\",\"name\":\"Trousers\",\"price\":49.99,\"availability\":false}")
                .setHeadersDelay(1, TimeUnit.SECONDS));

        StepVerifier.create(catalog.getProductDetail("100")).expectError().verify(Duration.ofSeconds(2));

        // La petición al upstream sigue en curso y, al terminar, queda en caché
        Thread.sleep(1500);
        StepVerifier.create(catalog.getProductDetail("100"))
                .expectNextMatches(product -> product.id().equals("100"))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void cachesProductDetailsAndCoalescesConcurrentRequests() {
        server.enqueue(json(200, "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}")
                .setBodyDelay(100, TimeUnit.MILLISECONDS));

        StepVerifier.create(catalog.getProductDetail("1").zipWith(catalog.getProductDetail("1")))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(catalog.getProductDetail("1")).expectNextCount(1).verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void doesNotCacheErrors() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(json(200, "{\"id\":\"6\",\"name\":\"Hat\",\"price\":5,\"availability\":true}"));

        StepVerifier.create(catalog.getProductDetail("6")).expectError().verify();
        // Caffeine elimina la entrada fallida al completarse el futuro; damos margen a ese callback
        Thread.sleep(50);
        StepVerifier.create(catalog.getProductDetail("6")).expectNextCount(1).verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
