package com.itx.similarproducts.application.port.in;

import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;
import reactor.core.publisher.Mono;

public interface GetSimilarProductsUseCase {

    /**
     * Detalle de los productos similares al indicado, ordenados por similitud.
     */
    Mono<List<ProductDetail>> getSimilarProducts(String productId);
}
