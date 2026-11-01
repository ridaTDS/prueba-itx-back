Feature: Seguridad del API
  El endpoint exige un JWT válido emitido por Keycloak.

  Background:
    * url baseUrl

  Scenario: Sin token devuelve 401 e indica el esquema Bearer
    Given path 'product', '1', 'similar'
    When method get
    Then status 401
    And match responseHeaders['WWW-Authenticate'][0] contains 'Bearer'

  Scenario: Con un token inventado devuelve 401
    Given path 'product', '1', 'similar'
    And header Authorization = 'Bearer token-falso'
    When method get
    Then status 401

  Scenario: Con un token manipulado (firma alterada) devuelve 401
    * def tamper =
      """
      function(token) {
        var parts = token.split('.');
        var signature = parts[2].split('').reverse().join('');
        return parts[0] + '.' + parts[1] + '.' + signature;
      }
      """
    * def tamperedToken = tamper(accessToken)
    * assert tamperedToken != accessToken
    Given path 'product', '1', 'similar'
    And header Authorization = 'Bearer ' + tamperedToken
    When method get
    Then status 401

  Scenario: Con un token válido devuelve 200
    Given path 'product', '1', 'similar'
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200

  Scenario: Otros métodos están denegados aunque haya token
    Given path 'product', '1', 'similar'
    And header Authorization = 'Bearer ' + accessToken
    And request {}
    When method post
    Then status 403

  Scenario: El health check es público
    Given path 'actuator', 'health'
    When method get
    Then status 200
    And match response.status == 'UP'
