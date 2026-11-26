#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker-compose.yml}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
BACKUP_DIR="${BACKUP_DIR:-${1:-}}"

if [[ -z "$BACKUP_DIR" || ! -d "$BACKUP_DIR" ]]; then
  echo "Usage: BACKUP_DIR=/path/to/backup ops/backup/verify.sh" >&2
  exit 1
fi
if [[ ! -f "$BACKUP_DIR/manifest.txt" ]]; then
  echo "Missing $BACKUP_DIR/manifest.txt" >&2
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

echo "Verifying PostgreSQL archives"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$BACKUP_DIR/pams.dump"
"${COMPOSE[@]}" exec -T postgres sh -c 'pg_restore -l >/dev/null' < "$BACKUP_DIR/keycloak.dump"

echo "Verifying MinIO archive"
tar -tzf "$BACKUP_DIR/minio-data.tar.gz" >/dev/null

echo "Backup verified: $BACKUP_DIR"
cat "$BACKUP_DIR/manifest.txt"
