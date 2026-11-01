package itx.similarproducts;

import com.intuit.karate.junit5.Karate;

/**
 * Lanza todas las features de este paquete contra la aplicación arrancada.
 * El informe HTML queda en target/karate-reports/karate-summary.html.
 */
class SimilarProductsE2ETest {

    @Karate.Test
    Karate similarProducts() {
        return Karate.run("classpath:itx/similarproducts").tags("~@ignore");
    }
}
