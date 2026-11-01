import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
  // Margen para esperar a que Keycloak esté listo antes de pedir el token
  setupTimeout: '120s',
  scenarios: {
    normal: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10s',
      exec: "normal",
      gracefulStop: '0s',
    },
    notFound: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10s',
      exec: "notFound",
      gracefulStop: '0s',
      startTime: '10s'
    },
    error: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10s',
      exec: "error",
      gracefulStop: '0s',
      startTime: '20s'
    },
    slow: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10s',
      exec: "slow",
      gracefulStop: '10s',
      startTime: '30s'
    },
    verySlow: {
      executor: 'constant-vus',
      vus: 200,
      duration: '10s',
      exec: "verySlow",
      gracefulStop: '30s',
      startTime: '50s'
    }
  }
};

const host = "http://host.docker.internal:5000";

// El API exige un JWT de Keycloak: se obtiene una vez con client credentials y se reutiliza
const tokenUrl = __ENV.TOKEN_URL || "http://keycloak:8080/realms/itx/protocol/openid-connect/token";
const clientId = __ENV.CLIENT_ID || "similar-products-client";
const clientSecret = __ENV.CLIENT_SECRET || "similar-products-secret";

export function setup() {
  for (let attempt = 1; attempt <= 30; attempt++) {
    const res = http.post(tokenUrl, {
      grant_type: "client_credentials",
      client_id: clientId,
      client_secret: clientSecret,
    });
    if (res.status === 200) {
      const token = JSON.parse(res.body).access_token;
      return { params: { headers: { Authorization: "Bearer " + token } } };
    }
    console.warn("Keycloak not ready (status " + res.status + "), retrying...");
    sleep(3);
  }
  throw new Error("Could not get an access token from " + tokenUrl);
}

export function normal(data) {
  http.get(host + "/product/1/similar", data.params);
  sleep(0.5);
}

export function slow(data) {
  http.get(host + "/product/2/similar", data.params);
  sleep(0.5);
}

export function verySlow(data) {
  http.get(host + "/product/3/similar", data.params);
  sleep(0.5);
}

export function notFound(data) {
  http.get(host + "/product/4/similar", data.params);
  sleep(0.5);
}

export function error(data) {
  http.get(host + "/product/5/similar", data.params);
  sleep(0.5);
}