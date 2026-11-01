package com.itx.similarproducts.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * La configuración se valida al arrancar: un valor que falta o es inválido impide levantar la aplicación.
 */
class ExistingApisPropertiesTest {

    private static final List<String> VALID_PROPERTIES = List.of(
            "existing-apis.base-url=http://localhost:3001",
            "existing-apis.connect-timeout=1s",
            "existing-apis.upstream-timeout=60s",
            "existing-apis.similar-ids-timeout=3s",
            "existing-apis.product-detail-timeout=3s",
            "existing-apis.max-connections=500",
            "existing-apis.pending-acquire-timeout=5s",
            "existing-apis.cache.ttl=10m",
            "existing-apis.cache.not-found-ttl=1m",
            "existing-apis.cache.max-size=10000");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @EnableConfigurationProperties(ExistingApisProperties.class)
    static class PropertiesConfig {
    }

    @Test
    void bindsAValidConfiguration() {
        runner.withPropertyValues(VALID_PROPERTIES.toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            ExistingApisProperties properties = context.getBean(ExistingApisProperties.class);
            assertThat(properties.baseUrl()).isEqualTo("http://localhost:3001");
            assertThat(properties.productDetailTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @Test
    void failsWhenTheBaseUrlIsMissing() {
        runner.withPropertyValues(without("existing-apis.base-url")).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void failsWhenMaxConnectionsIsNotPositive() {
        List<String> properties = new ArrayList<>(List.of(without("existing-apis.max-connections")));
        properties.add("existing-apis.max-connections=0");

        runner.withPropertyValues(properties.toArray(String[]::new)).run(context ->
                assertThat(context).hasFailed());
    }

    private static String[] without(String key) {
        return VALID_PROPERTIES.stream()
                .filter(property -> !property.startsWith(key + "="))
                .toArray(String[]::new);
    }
}
