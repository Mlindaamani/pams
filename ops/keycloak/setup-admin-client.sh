#!/usr/bin/env bash
set -euo pipefail

# Creates the PAMS Keycloak service-account client and grants realm user-management roles.
# Required environment:
#   KEYCLOAK_ADMIN_PASSWORD       master-realm admin password
# Optional:
#   KEYCLOAK_ADMIN_USER           default: admin
#   KEYCLOAK_ADMIN_CLIENT_SECRET  default: change-me-keycloak-admin-secret
#   KEYCLOAK_REALM                default: pams

KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_CLIENT_SECRET="${KEYCLOAK_ADMIN_CLIENT_SECRET:-change-me-keycloak-admin-secret}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-pams}"
KEYCLOAK_ADMIN_CLIENT_ID="${KEYCLOAK_ADMIN_CLIENT_ID:-pams-admin}"

if [[ -z "${KEYCLOAK_ADMIN_PASSWORD:-}" ]]; then
  echo "KEYCLOAK_ADMIN_PASSWORD is required" >&2
  exit 1
fi

kcadm() {
  docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@"
}

kcadm config credentials \
  --server http://localhost:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USER" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

CLIENT_ID="$(kcadm get clients -r "$KEYCLOAK_REALM" -q clientId=pams-admin --fields id --format csv --noquotes | tail -1 | tr -d '\r')"

if [[ -z "$CLIENT_ID" ]]; then
  kcadm create clients -r "$KEYCLOAK_REALM" \
    -s clientId=pams-admin \
    -s name="PAMS Admin API" \
    -s enabled=true \
    -s publicClient=false \
    -s clientAuthenticatorType=client-secret \
    -s secret="$KEYCLOAK_ADMIN_CLIENT_SECRET" \
    -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false >/dev/null
  CLIENT_ID="$(kcadm get clients -r "$KEYCLOAK_REALM" -q clientId=pams-admin --fields id --format csv --noquotes | tail -1 | tr -d '\r')"
else
  kcadm update "clients/$CLIENT_ID" -r "$KEYCLOAK_REALM" \
    -s secret="$KEYCLOAK_ADMIN_CLIENT_SECRET" \
    -s serviceAccountsEnabled=true \
    -s standardFlowEnabled=false \
    -s directAccessGrantsEnabled=false >/dev/null
fi

SA_USERNAME="service-account-${KEYCLOAK_ADMIN_CLIENT_ID}"
if ! kcadm get "users" -r "$KEYCLOAK_REALM" -q username="$SA_USERNAME" --fields id --format csv --noquotes | tail -1 | grep -q .; then
  echo "Could not resolve $SA_USERNAME service-account user" >&2
  exit 1
fi

for role in manage-users view-users query-users view-realm; do
  kcadm add-roles -r "$KEYCLOAK_REALM" --uusername "$SA_USERNAME" --cclientid realm-management --rolename "$role" >/dev/null
done

# Keycloak 26.8.2+ requires this client scope for Admin REST API access.
ADMIN_API_SCOPE_ID="$(kcadm get client-scopes -r "$KEYCLOAK_REALM" -q name=kc-admin-api --fields id --format csv --noquotes | tail -1 | tr -d '\r')"
if [[ -n "$ADMIN_API_SCOPE_ID" ]]; then
  kcadm update "clients/$CLIENT_ID/default-client-scopes/$ADMIN_API_SCOPE_ID" -r "$KEYCLOAK_REALM" >/dev/null
fi

echo "pams-admin client configured. Set KEYCLOAK_ADMIN_CLIENT_SECRET=$KEYCLOAK_ADMIN_CLIENT_SECRET in .env and restart the API."
