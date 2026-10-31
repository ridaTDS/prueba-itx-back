package com.itx.similarproducts.domain.model;

import java.math.BigDecimal;

/**
 * Detalle de un producto, tal y como se acordó en el contrato {@code similarProducts.yaml}.
 */
public record ProductDetail(String id, String name, BigDecimal price, boolean availability) {
}
