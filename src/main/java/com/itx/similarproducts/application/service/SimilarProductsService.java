package com.itx.similarproducts.application.service;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.application.port.out.ProductCatalogPort;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Obtiene los ids similares y resuelve el detalle de cada uno en paralelo,
 * manteniendo el orden de similitud original.
 *
 * <p>Un producto similar que no existe, falla o tarda demasiado se omite de la respuesta:
 * es preferible devolver una lista parcial a hacer fallar la página entera del cliente.
 */
@Service
public class SimilarProductsService implements GetSimilarProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SimilarProductsService.class);

    private final ProductCatalogPort productCatalog;

    public SimilarProductsService(ProductCatalogPort productCatalog) {
        this.productCatalog = productCatalog;
    }

    @Override
    public Mono<List<ProductDetail>> getSimilarProducts(String productId) {
        return productCatalog.getSimilarProductIds(productId)
                .flatMapMany(Flux::fromIterable)
                .distinct()
                // flatMapSequential: peticiones en paralelo, resultados en el orden original
                .flatMapSequential(this::findDetailSafely)
                .collectList();
    }

    private Mono<ProductDetail> findDetailSafely(String similarId) {
        return productCatalog.getProductDetail(similarId)
                .onErrorResume(error -> {
                    log.warn("Skipping similar product {}: {}", similarId, error.toString());
                    return Mono.empty();
                });
    }
}
