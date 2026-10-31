package com.itx.similarproducts.infrastructure.rest;

import com.itx.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.itx.similarproducts.domain.model.ProductDetail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SimilarProductsController {

    private final GetSimilarProductsUseCase getSimilarProducts;

    public SimilarProductsController(GetSimilarProductsUseCase getSimilarProducts) {
        this.getSimilarProducts = getSimilarProducts;
    }

    @GetMapping("/product/{productId}/similar")
    public Mono<List<ProductDetail>> getSimilarProducts(@PathVariable String productId) {
        return getSimilarProducts.getSimilarProducts(productId);
    }
}
