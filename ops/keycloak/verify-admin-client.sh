#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-pams}"
KEYCLOAK_ADMIN_CLIENT_ID="${KEYCLOAK_ADMIN_CLIENT_ID:-pams-admin}"
KEYCLOAK_ADMIN_CLIENT_SECRET="${KEYCLOAK_ADMIN_CLIENT_SECRET:-change-me-keycloak-admin-secret}"

TOKEN_RESPONSE="$(curl -fsS -X POST \
  "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "client_id=${KEYCLOAK_ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${KEYCLOAK_ADMIN_CLIENT_SECRET}")"

TOKEN="$(printf '%s' "${TOKEN_RESPONSE}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"

curl -fsS "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users?max=1" \
  -H "Authorization: Bearer ${TOKEN}" >/dev/null

echo "Keycloak admin client ${KEYCLOAK_ADMIN_CLIENT_ID} can read realm users."
