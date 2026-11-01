# Similar Products API

Solución a la prueba técnica de back-end ([enunciado original](./TEST_STATEMENT.md)).

Aplicación Spring Boot que expone en el puerto **5000** la operación acordada en
[`similarProducts.yaml`](./similarProducts.yaml):

```
GET /product/{productId}/similar  ->  200 [ProductDetail...] | 404
```

Para construir la respuesta consume las APIs existentes ([`existingApis.yaml`](./existingApis.yaml)):
primero obtiene los ids similares y después el detalle de cada uno.

El endpoint está protegido con **OAuth2/JWT**: exige un token emitido por **Keycloak**
(ver [Seguridad](#seguridad)).

## Requisitos

- Java 17 o 21
- Maven 3.9+
- Docker (mocks, Keycloak y test de rendimiento)

## Cómo ejecutarlo

```bash
# 1. Mocks, Keycloak e infraestructura del test
docker compose up -d simulado influxdb grafana keycloak

# 2. Aplicación en local (puerto 5000)
mvn spring-boot:run

# 3. Test de rendimiento (k6) y resultados en Grafana
#    El script obtiene un token de Keycloak antes de empezar.
docker compose run --rm k6 run scripts/test.js
# http://localhost:3000/d/Le2Ku9NMk/k6-performance-test
```

Keycloak tarda unos 20-30 segundos en arrancar la primera vez. Estará listo cuando
http://localhost:8080/realms/itx/.well-known/openid-configuration responda.

### Probar el endpoint a mano

Sin token la respuesta es `401 Unauthorized`. Para obtener un token (client credentials):

**PowerShell**
```powershell
$token = (Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/realms/itx/protocol/openid-connect/token `
  -Body @{ grant_type='client_credentials'; client_id='similar-products-client'; client_secret='similar-products-secret' }).access_token

Invoke-RestMethod http://localhost:5000/product/1/similar -Headers @{ Authorization = "Bearer $token" }
```

**bash**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/itx/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=similar-products-client \
  -d client_secret=similar-products-secret | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:5000/product/1/similar
```

También se puede levantar la aplicación en Docker en lugar de en local:

```bash
docker compose up -d --build simulado influxdb grafana keycloak similar-products
```

La URL de las APIs existentes se configura con la variable `EXISTING_APIS_BASE_URL`
(por defecto `http://localhost:3001`).

## Tests

Tres niveles, de más rápido a más completo:

| Nivel | Comando | Qué necesita |
|---|---|---|
| Unitarios (`*Test`) | `mvn test` | Nada |
| Integración (`*IT`) | `mvn verify` | Docker para `KeycloakSecurityIT` (si no hay Docker, ese test se omite) |
| End-to-end (Karate) | `mvn -f e2e/karate/pom.xml test` | La aplicación, los mocks y Keycloak arrancados |

`mvn verify` ejecuta unitarios e integración y genera la cobertura con JaCoCo en
`target/site/jacoco/index.html` (unitarios) y `target/site/jacoco-it/index.html` (integración).

### Unitarios

| Clase | Qué cubre |
|---|---|
| `SimilarProductsServiceTest` | Orden de similitud con respuestas desordenadas, omisión de productos que no existen o fallan, duplicados, lista vacía y propagación de errores. |
| `ExistingApisProductCatalogTest` | Cliente HTTP contra `MockWebServer`: ids numéricos, 404, 500, timeouts, caché, caché negativa, agrupación de peticiones concurrentes y rutas llamadas. |
| `SimilarProductsControllerTest` | Slice WebFlux: contrato JSON, 404, 502 y 401 sin token. |
| `RestExceptionHandlerTest` | Traducción de excepciones a `ProblemDetail` (404, 502). |
| `ProductDetailJsonTest` | Serialización según el contrato y lectura tolerante del upstream. |
| `ExistingApisPropertiesTest` | La configuración se valida al arrancar (fail fast). |

### Integración

| Clase | Qué cubre |
|---|---|
| `SimilarProductsIT` | Aplicación completa con un `MockWebServer` que reproduce `mocks.json`: casos normales, 404, 500, token válido, sin token, token inválido y health check. |
| `KeycloakSecurityIT` | Aplicación completa con un **Keycloak real** (Testcontainers) que importa el mismo realm: token emitido por Keycloak aceptado, firma manipulada rechazada, sin token y credenciales de cliente incorrectas. |

### End-to-end con Karate

Proyecto independiente en [`e2e/karate`](./e2e/karate) que prueba el sistema desplegado
(aplicación + simulado + Keycloak), igual que lo usaría un cliente real. Obtiene un token de
Keycloak una sola vez y lo reutiliza.

```bash
docker compose up -d simulado keycloak
mvn spring-boot:run                     # en otra terminal
mvn -f e2e/karate/pom.xml test
```

| Feature | Escenarios |
|---|---|
| `similar-products.feature` | Contrato y orden, omisión de productos 404/500, tiempo de respuesta con productos lentos y muy lentos, productos lentos servidos desde caché, 404 de producto inexistente. |
| `security.feature` | Sin token, token inventado, firma manipulada, token válido, métodos no permitidos (403) y health check público. |

Karate 1.4 necesita **Java 17 o 21** (su motor JavaScript no funciona en versiones más nuevas);
el build lo comprueba al empezar.

Si tu Maven está configurado contra un repositorio corporativo (Artifactory, Nexus…) que no
tiene Karate, fuerza Maven Central con los settings incluidos:

```bash
mvn -s e2e/karate/central-settings.xml -gs e2e/karate/central-settings.xml -f e2e/karate/pom.xml test
```

El informe queda en `e2e/karate/target/karate-reports/karate-summary.html`. Las URLs y
credenciales se pueden cambiar con `-DbaseUrl=...`, `-DkeycloakUrl=...`, `-DclientId=...` y
`-DclientSecret=...`.

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
    ├── security           # Resource server OAuth2 (JWT)
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

## Seguridad

El API es un **resource server OAuth2** (`SecurityConfig`):

- `GET /product/**` exige `Authorization: Bearer <JWT>`. Se valida la **firma** con las claves
  públicas de Keycloak (JWKS, cacheadas), la **expiración** y el **emisor** (`iss`).
- `/actuator/health` e `/actuator/info` son públicos; cualquier otra ruta se deniega.
- Stateless: sin sesión ni CSRF (no hay cookies), cada petición se autentica con su token.

Keycloak se levanta con `docker compose` e importa el realm [`keycloak/itx-realm.json`](./keycloak/itx-realm.json):

| Elemento | Valor | Uso |
|---|---|---|
| Realm | `itx` | |
| Cliente confidencial | `similar-products-client` / `similar-products-secret` | Client credentials (k6, curl, otros servicios) |
| Cliente público | `itx-mobile-store` | Login OIDC + PKCE del front |
| Usuario | `demo` / `demo` | Login en el front |
| Consola de administración | http://localhost:8080 (`admin` / `admin`) | |

Configuración (`application.yml`, sobrescribible por variables de entorno):

| Propiedad | Variable | Por defecto |
|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `KEYCLOAK_ISSUER_URI` | `http://localhost:8080/realms/itx` |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | `KEYCLOAK_JWK_SET_URI` | `http://localhost:8080/realms/itx/protocol/openid-connect/certs` |

Keycloak se configura con `KC_HOSTNAME=http://localhost:8080`, de modo que el emisor de los
tokens es el mismo tanto si se piden desde el host como desde otro contenedor (k6).

> Las credenciales del realm son de ejemplo para desarrollo local. En un entorno real irían
> en un gestor de secretos y Keycloak se ejecutaría en modo producción con HTTPS.

**Cambio en el test de k6:** como el endpoint exige token, `shared/k6/test.js` obtiene uno en
`setup()` con client credentials y lo envía en todas las peticiones. Los escenarios y la carga
no cambian.

## Posibles mejoras

- Autorización por roles o scopes (p. ej. exigir `products:read`) y validación de audiencia (`aud`).
- Circuit breaker (Resilience4j) por dependencia, si el upstream fuera un servicio real compartido.
- Métricas de caché y latencia del upstream con Micrometer/Prometheus.
- Generar el controlador y los DTOs a partir del contrato OpenAPI (api-first).
