package com.itx.similarproducts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

/**
 * Comprueba que el JSON cumple el contrato {@code similarProducts.yaml} y que se lee bien
 * el que devuelven las APIs existentes.
 */
@JsonTest
class ProductDetailJsonTest {

    @Autowired
    private JacksonTester<ProductDetail> json;

    @Test
    void serializesFollowingTheContract() throws IOException {
        ProductDetail product = new ProductDetail("1", "Shirt", new BigDecimal("9.99"), true);

        assertThat(json.write(product)).isStrictlyEqualToJson("""
                {"id":"1","name":"Shirt","price":9.99,"availability":true}
                """);
    }

    @Test
    void deserializesTheUpstreamPayload() throws IOException {
        ProductDetail product = json.parseObject("""
                {"id":"3","name":"Blazer","price":29.99,"availability":false}
                """);

        assertThat(product).isEqualTo(new ProductDetail("3", "Blazer", new BigDecimal("29.99"), false));
    }

    @Test
    void ignoresUnknownFieldsAddedByTheUpstream() throws IOException {
        ProductDetail product = json.parseObject("""
                {"id":"1","name":"Shirt","price":9.99,"availability":true,"brand":"ITX"}
                """);

        assertThat(product.id()).isEqualTo("1");
    }
}
