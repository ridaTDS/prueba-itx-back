package com.itx.similarproducts.infrastructure.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "existing-apis")
public record ExistingApisProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration upstreamTimeout,
        @NotNull Duration similarIdsTimeout,
        @NotNull Duration productDetailTimeout,
        @Positive int maxConnections,
        @NotNull Duration pendingAcquireTimeout,
        @Valid @NotNull Cache cache) {

    public record Cache(@NotNull Duration ttl, @NotNull Duration notFoundTtl, @Positive long maxSize) {
    }
}
