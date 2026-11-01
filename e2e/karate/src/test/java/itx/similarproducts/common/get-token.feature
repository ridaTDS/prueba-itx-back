@ignore
Feature: Obtener un token de acceso de Keycloak (client credentials)

  Scenario: Pedir token
    Given url tokenUrl
    And form field grant_type = 'client_credentials'
    And form field client_id = clientId
    And form field client_secret = clientSecret
    When method post
    Then status 200
    And match response.token_type == 'Bearer'
    * def accessToken = response.access_token
