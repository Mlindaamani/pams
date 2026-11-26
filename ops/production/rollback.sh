#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/.env.production}"
DEPLOY_ENV="${DEPLOY_ENV:-production}"
STATE_DIR="${DEPLOY_STATE_DIR:-$ROOT/.deploy}"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker-compose.prod.yml}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --environment) DEPLOY_ENV="${2:?}"; shift 2 ;;
    --state-dir) STATE_DIR="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    -h|--help)
      echo "Usage: ops/production/rollback.sh [--environment staging|production] [--env-file path]"
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

PREVIOUS_FILE="$STATE_DIR/$DEPLOY_ENV.previous"
if [[ ! -s "$PREVIOUS_FILE" ]]; then
  echo "No previous tag recorded for $DEPLOY_ENV in $STATE_DIR" >&2
  exit 1
fi
TAG="$(cat "$PREVIOUS_FILE")"
echo "Rolling $DEPLOY_ENV back to $TAG"
ENV_FILE="$ENV_FILE" DEPLOY_ENV="$DEPLOY_ENV" DEPLOY_STATE_DIR="$STATE_DIR" \
  COMPOSE_FILE="$COMPOSE_FILE" "$ROOT/ops/production/deploy.sh" --tag "$TAG"
