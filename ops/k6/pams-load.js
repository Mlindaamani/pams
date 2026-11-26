import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8081';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'admin';
const CLIENT_ID = __ENV.CLIENT_ID || 'pams-cli';
const CLIENT_SECRET = __ENV.CLIENT_SECRET || 'change-me-pams-cli-secret';

const PEAK_VUS = Number(__ENV.PEAK_VUS || 50);
const HEADROOM_VUS = Number(__ENV.HEADROOM_VUS || Math.ceil(PEAK_VUS * 1.5));
const RAMP_UP = __ENV.RAMP_UP || '2m';
const PEAK_HOLD = __ENV.PEAK_HOLD || '5m';
const RAMP_HEADROOM = __ENV.RAMP_HEADROOM || '1m';
const HEADROOM_HOLD = __ENV.HEADROOM_HOLD || '3m';
const RAMP_DOWN = __ENV.RAMP_DOWN || '1m';

export const options = {
  scenarios: {
    read_paths: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: PEAK_VUS },
        { duration: PEAK_HOLD, target: PEAK_VUS },
        { duration: RAMP_HEADROOM, target: HEADROOM_VUS },
        { duration: HEADROOM_HOLD, target: HEADROOM_VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const response = http.post(
    `${KEYCLOAK_URL}/realms/pams/protocol/openid-connect/token`,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      username: USERNAME,
      password: PASSWORD,
    },
  );
  check(response, { 'token acquired': (r) => r.status === 200 });
  return { token: response.json('access_token') };
}

export default function (data) {
  const params = {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'tenant-read' },
  };
  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/purchase-requests?page=0&size=20`, null, params],
    ['GET', `${BASE_URL}/api/v1/vendors?page=0&size=20`, null, params],
    ['GET', `${BASE_URL}/api/v1/budgets?page=0&size=20`, null, params],
    ['GET', `${BASE_URL}/api/v1/reports/spend?groupBy=department`, null, params],
  ]);
  responses.forEach((response) => {
    check(response, {
      'status is 200': (r) => r.status === 200,
      'latency under 500ms': (r) => r.timings.duration < 500,
    });
  });
  sleep(0.5);
}
