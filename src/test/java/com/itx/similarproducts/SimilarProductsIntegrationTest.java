package com.itx.similarproducts;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Prueba de extremo a extremo con un servidor que reproduce los mocks de {@code shared/simulado/mocks.json}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsIntegrationTest {

    private static MockWebServer existingApis;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startExistingApis() throws IOException {
        existingApis = new MockWebServer();
        existingApis.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return switch (request.getPath()) {
                    case "/product/1/similarids" -> json(200, "[2,3,4]");
                    case "/product/4/similarids" -> json(200, "[1,2,5]");
                    case "/product/5/similarids" -> json(200, "[1,2,6]");
                    case "/product/1" -> json(200, "{\"id\":\"1\",\"name\":\"Shirt\",\"price\":9.99,\"availability\":true}");
                    case "/product/2" -> json(200, "{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}");
                    case "/product/3" -> json(200, "{\"id\":\"3\",\"name\":\"Blazer\",\"price\":29.99,\"availability\":false}")
                            .setBodyDelay(100, TimeUnit.MILLISECONDS);
                    case "/product/4" -> json(200, "{\"id\":\"4\",\"name\":\"Boots\",\"price\":39.99,\"availability\":true}");
                    case "/product/5" -> json(404, "{\"message\":\"Product not found\"}");
                    case "/product/6" -> new MockResponse().setResponseCode(500);
                    default -> json(404, "{\"message\":\"Product not found\"}");
                };
            }
        });
        existingApis.start();
    }

    @AfterAll
    static void stopExistingApis() throws IOException {
        existingApis.shutdown();
    }

    @DynamicPropertySource
    static void existingApisProperties(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", () -> existingApis.url("/").toString());
    }

    @Test
    void returnsSimilarProductsInOrder() {
        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0].id").isEqualTo("2")
                .jsonPath("$[1].id").isEqualTo("3")
                .jsonPath("$[2].id").isEqualTo("4");
    }

    @Test
    void omitsSimilarProductsThatAreNotFound() {
        webTestClient.get().uri("/product/4/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("1")
                .jsonPath("$[1].id").isEqualTo("2");
    }

    @Test
    void omitsSimilarProductsThatFail() {
        webTestClient.get().uri("/product/5/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void returns404ForUnknownProduct() {
        webTestClient.get().uri("/product/999/similar")
                .exchange()
                .expectStatus().isNotFound();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
