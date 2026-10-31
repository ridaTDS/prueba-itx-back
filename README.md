# Similar Products API

Solución a la prueba técnica de back-end ([enunciado original](./TEST_STATEMENT.md)).

Aplicación Spring Boot que expone en el puerto **5000** la operación acordada en
[`similarProducts.yaml`](./similarProducts.yaml):

```
GET /product/{productId}/similar  ->  200 [ProductDetail...] | 404
```

Para construir la respuesta consume las APIs existentes ([`existingApis.yaml`](./existingApis.yaml)):
primero obtiene los ids similares y después el detalle de cada uno.

## Requisitos

- Java 17 o superior
- Maven 3.9+
- Docker (sólo para los mocks y el test de rendimiento)

## Cómo ejecutarlo

```bash
# 1. Mocks e infraestructura del test
docker-compose up -d simulado influxdb grafana

# 2. Aplicación en local (puerto 5000)
mvn spring-boot:run

# 3. Comprobación rápida
curl http://localhost:5000/product/1/similar

# 4. Test de rendimiento (k6) y resultados en Grafana
docker-compose run --rm k6 run scripts/test.js
# http://localhost:3000/d/Le2Ku9NMk/k6-performance-test
```

También se puede levantar la aplicación en Docker en lugar de en local:

```bash
docker-compose up -d --build simulado influxdb grafana similar-products
```

La URL de las APIs existentes se configura con la variable `EXISTING_APIS_BASE_URL`
(por defecto `http://localhost:3001`).

### Tests

```bash
mvn test
```

- `SimilarProductsServiceTest`: orden de los resultados, omisión de productos que fallan, duplicados.
- `ExistingApisProductCatalogTest`: cliente HTTP contra `MockWebServer` (404, 500, timeouts, caché).
- `SimilarProductsControllerTest`: contrato JSON y códigos de estado.
- `SimilarProductsIntegrationTest`: extremo a extremo con los mismos casos que `mocks.json`.

## Arquitectura

Arquitectura hexagonal ligera, en un único módulo:

```
com.itx.similarproducts
├── domain                 # Modelo (ProductDetail) y excepciones de negocio
├── application
│   ├── port.in            # GetSimilarProductsUseCase
│   ├── port.out           # ProductCatalogPort
│   └── service            # SimilarProductsService
└── infrastructure
    ├── rest               # Controlador y manejo de errores
    └── client             # Adaptador WebClient a las APIs existentes + caché
```

El caso de uso no conoce HTTP ni la caché: sólo depende del puerto `ProductCatalogPort`,
lo que facilita testearlo y cambiar el origen de datos.

## Decisiones de rendimiento y resiliencia

| Problema | Decisión |
|---|---|
| Los detalles se piden uno a uno | Se piden **en paralelo** (`flatMapSequential`) conservando el **orden de similitud**. |
| 200 usuarios concurrentes | Stack **no bloqueante** (WebFlux + Reactor Netty) y pool de conexiones configurable. |
| Mismos productos pedidos una y otra vez | **Caché asíncrona Caffeine** para ids similares y detalles (TTL 10 min). |
| Ráfagas de peticiones al mismo producto | La caché guarda el *futuro* en curso: N peticiones concurrentes = **1 llamada** al upstream. |
| Productos que no existen (404) | Se omiten de la respuesta y se cachean con un TTL más corto (1 min). |
| Productos que fallan (500) | Se omiten de la respuesta y **no** se cachean, para reintentar en la siguiente petición. |
| Productos muy lentos (5 s, 50 s) | El cliente espera como máximo `product-detail-timeout` (3 s) y recibe una respuesta parcial. La llamada al upstream continúa en segundo plano (hasta `upstream-timeout`) y, al terminar, el resultado se cachea: las siguientes peticiones ya lo incluyen. |
| El producto pedido no existe | `404`. |
| Las APIs existentes no responden para los ids similares | `502 Bad Gateway`. |

Se ha preferido devolver una lista parcial antes que fallar la petición completa: para una
funcionalidad como "productos similares" es mejor mostrar algo que dejar el bloque vacío o con error.

Todos los tiempos y tamaños están en [`application.yml`](./src/main/resources/application.yml):

```yaml
existing-apis:
  base-url: ${EXISTING_APIS_BASE_URL:http://localhost:3001}
  connect-timeout: 1s
  upstream-timeout: 60s
  similar-ids-timeout: 3s
  product-detail-timeout: 3s
  max-connections: 500
  cache:
    ttl: 10m
    not-found-ttl: 1m
    max-size: 10000
```

## Posibles mejoras

- Circuit breaker (Resilience4j) por dependencia, si el upstream fuera un servicio real compartido.
- Métricas de caché y latencia del upstream con Micrometer/Prometheus.
- Generar el controlador y los DTOs a partir del contrato OpenAPI (api-first).
