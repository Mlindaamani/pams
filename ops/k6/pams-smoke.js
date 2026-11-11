import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8081';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'admin';

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export function setup() {
  const response = http.post(
    `${KEYCLOAK_URL}/realms/pams/protocol/openid-connect/token`,
    {
      grant_type: 'password',
      client_id: 'pams-api',
      username: USERNAME,
      password: PASSWORD,
    },
  );
  check(response, { 'token acquired': (r) => r.status === 200 });
  return { token: response.json('access_token') };
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } };
  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/purchase-requests?page=0&size=20`, null, params],
    ['GET', `${BASE_URL}/api/v1/vendors?page=0&size=20`, null, params],
    ['GET', `${BASE_URL}/api/v1/reports/spend?groupBy=department`, null, params],
  ]);
  responses.forEach((response) => {
    check(response, {
      'status is 200': (r) => r.status === 200,
      'latency under 300ms': (r) => r.timings.duration < 300,
    });
  });
  sleep(0.5);
}
