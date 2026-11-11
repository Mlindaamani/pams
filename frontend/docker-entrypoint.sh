#!/bin/sh
set -eu

cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  apiBaseUrl: "${PAMS_API_BASE_URL:-}",
  authority: "${PAMS_OIDC_AUTHORITY:-http://localhost:8081/realms/pams}",
  clientId: "${PAMS_OIDC_CLIENT_ID:-pams-api}",
  scope: "${PAMS_OIDC_SCOPE:-openid profile email}"
};
EOF
