package com.itx.similarproducts.application.port.out;

import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida hacia el catálogo de productos existente.
 */
public interface ProductCatalogPort {

    /**
     * Ids de los productos similares, ordenados por similitud.
     * Emite {@code ProductNotFoundException} si el producto no existe y
     * {@code UpstreamUnavailableException} si el servicio no responde correctamente.
     */
    Mono<List<String>> getSimilarProductIds(String productId);

    /**
     * Detalle de un producto. Vacío si el producto no existe; error si el servicio
     * no responde correctamente o en el tiempo esperado.
     */
    Mono<ProductDetail> getProductDetail(String productId);
}
