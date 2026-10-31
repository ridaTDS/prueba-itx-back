package com.itx.similarproducts.infrastructure.client;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.exception.ProductNotFoundException;
import com.itx.similarproducts.domain.exception.UpstreamUnavailableException;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Adaptador hacia las APIs existentes ({@code existingApis.yaml}).
 *
 * <p>Decisiones de rendimiento y resiliencia:
 * <ul>
 *   <li><b>Caché asíncrona (Caffeine)</b>: se cachea la petición en curso, no sólo el resultado, de modo
 *       que N peticiones concurrentes al mismo producto generan una única llamada al upstream.</li>
 *   <li><b>Caché negativa</b>: un 404 de detalle se recuerda durante un tiempo más corto.
 *       Los errores (5xx, timeouts) no se cachean, así se reintentan en la siguiente petición.</li>
 *   <li><b>Dos timeouts</b>: el cliente de nuestro API sólo espera {@code product-detail-timeout},
 *       pero la llamada al upstream continúa hasta {@code upstream-timeout}. Cuando termina, el resultado
 *       queda en caché y las siguientes peticiones lo sirven de inmediato.</li>
 * </ul>
 */
@Component
public class ExistingApisProductCatalog implements ProductCatalogPort {

    private static final ParameterizedTypeReference<List<String>> ID_LIST = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;
    private final ExistingApisProperties properties;
    private final AsyncCache<String, List<String>> similarIdsCache;
    private final AsyncCache<String, Optional<ProductDetail>> productDetailCache;

    public ExistingApisProductCatalog(WebClient existingApisWebClient, ExistingApisProperties properties) {
        this.webClient = existingApisWebClient;
        this.properties = properties;
        this.similarIdsCache = Caffeine.newBuilder()
                .expireAfterWrite(properties.cache().ttl())
                .maximumSize(properties.cache().maxSize())
                .buildAsync();
        this.productDetailCache = Caffeine.newBuilder()
                .expireAfter(new DetailExpiry(properties.cache().ttl(), properties.cache().notFoundTtl()))
                .maximumSize(properties.cache().maxSize())
                .buildAsync();
    }

    @Override
    public Mono<List<String>> getSimilarProductIds(String productId) {
        return Mono.defer(() -> Mono.fromFuture(
                        similarIdsCache.get(productId, (id, executor) -> fetchSimilarIds(id).toFuture()), true))
                .timeout(properties.similarIdsTimeout())
                .onErrorMap(error -> !(error instanceof ProductNotFoundException),
                        error -> new UpstreamUnavailableException(
                                "Could not get similar ids for product " + productId, error));
    }

    @Override
    public Mono<ProductDetail> getProductDetail(String productId) {
        return Mono.defer(() -> Mono.fromFuture(
                        productDetailCache.get(productId, (id, executor) -> fetchProductDetail(id).toFuture()), true))
                .timeout(properties.productDetailTimeout())
                .mapNotNull(optional -> optional.orElse(null));
    }

    private Mono<List<String>> fetchSimilarIds(String productId) {
        return webClient.get()
                .uri("/product/{productId}/similarids", productId)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(),
                        response -> Mono.error(new ProductNotFoundException(productId)))
                .bodyToMono(ID_LIST)
                .defaultIfEmpty(List.of());
    }

    private Mono<Optional<ProductDetail>> fetchProductDetail(String productId) {
        return webClient.get()
                .uri("/product/{productId}", productId)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                        return response.releaseBody().thenReturn(Optional.<ProductDetail>empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.<Optional<ProductDetail>>createError();
                    }
                    return response.bodyToMono(ProductDetail.class).map(Optional::of);
                });
    }

    /**
     * Expiración distinta para productos encontrados y no encontrados.
     */
    private record DetailExpiry(Duration ttl, Duration notFoundTtl) implements Expiry<String, Optional<ProductDetail>> {

        @Override
        public long expireAfterCreate(String key, Optional<ProductDetail> value, long currentTime) {
            return (value.isPresent() ? ttl : notFoundTtl).toNanos();
        }

        @Override
        public long expireAfterUpdate(String key, Optional<ProductDetail> value, long currentTime,
                                      long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(String key, Optional<ProductDetail> value, long currentTime,
                                    long currentDuration) {
            return currentDuration;
        }
    }
}
