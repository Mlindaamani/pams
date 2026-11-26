#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker-compose.yml}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
MINIO_DATA_PATH="${MINIO_DATA_PATH:-/bitnami/minio/data}"
KC_DB_NAME="${KEYCLOAK_DB_NAME:-keycloak}"

CONFIRM=false
if [[ "${1:-}" == "--confirm" ]]; then
  CONFIRM=true
  shift
elif [[ "${2:-}" == "--confirm" ]]; then
  CONFIRM=true
fi

if [[ "$CONFIRM" != true ]]; then
  echo "Usage: BACKUP_DIR=/path/to/backup ${BASH_SOURCE[0]} --confirm" >&2
  echo "This is destructive: it replaces PostgreSQL, Keycloak and MinIO data." >&2
  exit 1
fi

BACKUP_DIR="${1:-${BACKUP_DIR:-}}"

if [[ -z "$BACKUP_DIR" || ! -d "$BACKUP_DIR" ]]; then
  echo "BACKUP_DIR is required and must exist" >&2
  exit 1
fi
for file in pams.dump keycloak.dump minio-data.tar.gz SHA256SUMS; do
  if [[ ! -f "$BACKUP_DIR/$file" ]]; then
    echo "Missing $BACKUP_DIR/$file" >&2
    exit 1
  fi
done

echo "Verifying checksums"
(cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS)
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$BACKUP_DIR/pams.dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$BACKUP_DIR/keycloak.dump"
tar -tzf "$BACKUP_DIR/minio-data.tar.gz" >/dev/null

echo "Stopping application services"
"${COMPOSE[@]}" stop api web keycloak minio >/dev/null 2>&1 || true

echo "Restoring PostgreSQL application database"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges' < "$BACKUP_DIR/pams.dump"

echo "Restoring Keycloak database"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --clean --if-exists --no-owner --no-privileges' sh "$KC_DB_NAME" < "$BACKUP_DIR/keycloak.dump"

echo "Restoring MinIO objects"
"${COMPOSE[@]}" run --rm --no-deps -T --user 0:0 --entrypoint sh minio -c 'rm -rf "${1:?}"/* "${1:?}"/.[!.]* "${1:?}"/..?* 2>/dev/null || true; tar -xzf - -C "$1"' sh "$MINIO_DATA_PATH" < "$BACKUP_DIR/minio-data.tar.gz"

echo "Starting services"
"${COMPOSE[@]}" up -d

cat <<'DONE'
Restore complete.

Verify:
  1. docker compose ps
  2. API readiness: curl -fsS http://localhost:9090/actuator/health/readiness
  3. Sign in with a known tenant account and inspect requests, attachments and branding.
  4. Check /admin/system for outbox backlog and dead letters.
DONE
