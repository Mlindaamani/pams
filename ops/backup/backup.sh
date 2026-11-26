#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker-compose.yml}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
BACKUP_ROOT="${BACKUP_ROOT:-$ROOT/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
MINIO_DATA_PATH="${MINIO_DATA_PATH:-/bitnami/minio/data}"
KC_DB_NAME="${KEYCLOAK_DB_NAME:-keycloak}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$BACKUP_ROOT/$STAMP"

umask 077
mkdir -p "$DEST"

if ! "${COMPOSE[@]}" ps --status running --services | grep -qx postgres; then
  echo "PostgreSQL is not running for $COMPOSE_FILE" >&2
  exit 1
fi
if ! "${COMPOSE[@]}" ps --status running --services | grep -qx minio; then
  echo "MinIO is not running for $COMPOSE_FILE" >&2
  exit 1
fi

echo "Backing up PostgreSQL to $DEST/pams.dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > "$DEST/pams.dump"

echo "Backing up Keycloak database to $DEST/keycloak.dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$1"' sh "$KC_DB_NAME" > "$DEST/keycloak.dump"

echo "Backing up MinIO objects to $DEST/minio-data.tar.gz"
"${COMPOSE[@]}" exec -T minio sh -c 'tar -czf - -C "$1" .' sh "$MINIO_DATA_PATH" > "$DEST/minio-data.tar.gz"

echo "Verifying archives"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$DEST/pams.dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$DEST/keycloak.dump"
tar -tzf "$DEST/minio-data.tar.gz" >/dev/null

(
  cd "$DEST"
  sha256sum pams.dump keycloak.dump minio-data.tar.gz > SHA256SUMS
)

cat > "$DEST/manifest.txt" <<MANIFEST
created_at=$STAMP
compose_file=$COMPOSE_FILE
git_commit=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)
postgres_database=$(grep -E '^POSTGRES_DB=' "$ROOT/.env" 2>/dev/null | tail -1 | cut -d= -f2- || true)
keycloak_database=$KC_DB_NAME
minio_data_path=$MINIO_DATA_PATH
retention_days=$RETENTION_DAYS
MANIFEST

echo "Backup complete: $DEST"
echo "Files:"
ls -lh "$DEST"

if [[ "$RETENTION_DAYS" =~ ^[0-9]+$ ]] && (( RETENTION_DAYS > 0 )); then
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -print -exec rm -rf {} +
fi
