package com.itx.similarproducts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Seguridad de extremo a extremo con un Keycloak real (Testcontainers) que importa el mismo realm
 * que {@code docker-compose}: los tokens se obtienen de Keycloak y la aplicación valida su firma
 * contra el JWKS publicado.
 *
 * <p>Requiere Docker; si no está disponible el test se omite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class KeycloakSecurityIT {

    private static final String REALM = "itx";
    private static final String CLIENT_ID = "similar-products-client";
    private static final String CLIENT_SECRET = "similar-products-secret";

    @Container
    static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.0")
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(MountableFile.forHostPath("keycloak/itx-realm.json"),
                    "/opt/keycloak/data/import/itx-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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
                    case "/product/1/similarids" -> json("[2]");
                    case "/product/2" -> json("{\"id\":\"2\",\"name\":\"Dress\",\"price\":19.99,\"availability\":true}");
                    default -> new MockResponse().setResponseCode(404);
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
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("existing-apis.base-url", () -> existingApis.url("/").toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakSecurityIT::issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuerUri() + "/protocol/openid-connect/certs");
    }

    @Test
    void acceptsATokenIssuedByKeycloak() throws Exception {
        String token = requestToken();

        webTestClient.get().uri("/product/1/similar")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("2");
    }

    @Test
    void rejectsATokenWithATamperedSignature() throws Exception {
        String token = requestToken();
        String[] parts = token.split("\\.");
        String tamperedSignature = new StringBuilder(parts[2]).reverse().toString();
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        webTestClient.get().uri("/product/1/similar")
                .headers(headers -> headers.setBearerAuth(tamperedToken))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRequestsWithoutToken() {
        webTestClient.get().uri("/product/1/similar")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void keycloakDoesNotIssueTokensWithWrongClientCredentials() throws Exception {
        HttpResponse<String> response = tokenRequest(CLIENT_ID, "wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private static String issuerUri() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms/" + REALM;
    }

    private static String requestToken() throws Exception {
        HttpResponse<String> response = tokenRequest(CLIENT_ID, CLIENT_SECRET);
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body()).get("access_token").asText();
    }

    private static HttpResponse<String> tokenRequest(String clientId, String clientSecret) throws Exception {
        String form = Map.of(
                        "grant_type", "client_credentials",
                        "client_id", clientId,
                        "client_secret", clientSecret)
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(issuerUri() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
