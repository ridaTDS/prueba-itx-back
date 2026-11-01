function fn() {
  // Valores por defecto para el entorno local (docker compose + mvn spring-boot:run).
  // Se pueden sobrescribir con -DbaseUrl=... -DkeycloakUrl=... etc.
  var config = {
    baseUrl: karate.properties['baseUrl'] || 'http://localhost:5000',
    keycloakUrl: karate.properties['keycloakUrl'] || 'http://localhost:8080',
    realm: karate.properties['realm'] || 'itx',
    clientId: karate.properties['clientId'] || 'similar-products-client',
    clientSecret: karate.properties['clientSecret'] || 'similar-products-secret'
  };
  config.tokenUrl = config.keycloakUrl + '/realms/' + config.realm + '/protocol/openid-connect/token';

  // Un único token para toda la ejecución
  var auth = karate.callSingle('classpath:itx/similarproducts/common/get-token.feature', config);
  config.accessToken = auth.accessToken;

  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 15000);
  return config;
}
