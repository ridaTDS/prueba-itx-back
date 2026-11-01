Feature: GET /product/{productId}/similar
  Detalle de los productos similares a uno dado, ordenados por similitud.
  Los datos son los de los mocks de simulado (shared/simulado/mocks.json).

  Background:
    * url baseUrl
    * configure headers = { Authorization: '#("Bearer " + accessToken)' }
    * def productDetail = { id: '#string', name: '#string', price: '#number', availability: '#boolean' }

  Scenario: Devuelve el detalle de los similares respetando el contrato y el orden
    Given path 'product', '1', 'similar'
    When method get
    Then status 200
    And match header Content-Type contains 'application/json'
    And match response == '#[3] productDetail'
    And match response[*].id == ['2', '3', '4']
    And match response[0] == { id: '2', name: 'Dress', price: 19.99, availability: true }
    And match response[1] == { id: '3', name: 'Blazer', price: 29.99, availability: false }
    And match response[2] == { id: '4', name: 'Boots', price: 39.99, availability: true }

  Scenario Outline: Omite los similares que no se pueden obtener (<reason>)
    Given path 'product', '<productId>', 'similar'
    When method get
    Then status 200
    And match each response == productDetail
    And match response[*].id == <expectedIds>

    Examples:
      | productId | expectedIds | reason                         |
      | 4         | ['1', '2']  | el producto 5 no existe (404)  |
      | 5         | ['1', '2']  | el producto 6 falla (500)      |

  Scenario: Responde rápido aunque algún similar sea lento
    # 2 -> [3 (100 ms), 100 (1 s), 1000 (5 s)]: el lento se omite si aún no está en caché
    Given path 'product', '2', 'similar'
    When method get
    Then status 200
    And assert responseTime < 4000
    And match response[*].id contains ['3', '100']

  Scenario: Responde rápido aunque los similares sean muy lentos
    # 3 -> [100 (1 s), 1000 (5 s), 10000 (50 s)]
    Given path 'product', '3', 'similar'
    When method get
    Then status 200
    And assert responseTime < 4000
    And match response[*].id contains '100'

  Scenario: Los similares lentos aparecen cuando ya están en caché
    Given path 'product', '2', 'similar'
    When method get
    Then status 200
    # Damos tiempo a que termine la llamada en segundo plano al producto 1000 (5 s)
    * java.lang.Thread.sleep(6000)
    Given path 'product', '2', 'similar'
    When method get
    Then status 200
    And match response[*].id == ['3', '100', '1000']
    And assert responseTime < 1000

  Scenario: Devuelve 404 si el producto no existe
    Given path 'product', '999', 'similar'
    When method get
    Then status 404
    And match response.status == 404
    And match response.detail contains '999'
