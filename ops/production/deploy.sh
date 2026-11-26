#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT/docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-$ROOT/.env.production}"
DEPLOY_ENV="${DEPLOY_ENV:-production}"
STATE_DIR="${DEPLOY_STATE_DIR:-$ROOT/.deploy}"
TIMEOUT="${DEPLOY_TIMEOUT:-300}"
SKIP_PULL=false
TAG=""

usage() {
  cat <<'USAGE'
Usage: ops/production/deploy.sh --tag <immutable-image-tag> [options]

Options:
  --tag <tag>             Git SHA or version tag to deploy (required)
  --env-file <path>       Production env file (default: .env.production)
  --environment <name>    State key, usually staging or production
  --compose-file <path>   Compose file (default: docker-compose.prod.yml)
  --state-dir <path>      Where current/previous tags are recorded
  --timeout <seconds>     Compose readiness timeout (default: 300)
  --skip-pull             Use already-pulled local images
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) TAG="${2:?}"; shift 2 ;;
    --env-file) ENV_FILE="${2:?}"; shift 2 ;;
    --environment) DEPLOY_ENV="${2:?}"; shift 2 ;;
    --compose-file) COMPOSE_FILE="${2:?}"; shift 2 ;;
    --state-dir) STATE_DIR="${2:?}"; shift 2 ;;
    --timeout) TIMEOUT="${2:?}"; shift 2 ;;
    --skip-pull) SKIP_PULL=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [[ -z "$TAG" ]]; then
  usage >&2
  exit 1
fi
if [[ "$TAG" == "latest" || ! "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "Refusing mutable or invalid image tag: $TAG" >&2
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi
for variable in PAMS_BACKEND_IMAGE PAMS_WEB_IMAGE; do
  if ! grep -Eq "^${variable}=.+" "$ENV_FILE"; then
    echo "${variable} must be set in $ENV_FILE" >&2
    exit 1
  fi
done

compose=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
mkdir -p "$STATE_DIR"
CURRENT_FILE="$STATE_DIR/$DEPLOY_ENV.current"
PREVIOUS_FILE="$STATE_DIR/$DEPLOY_ENV.previous"
CURRENT="$(cat "$CURRENT_FILE" 2>/dev/null || true)"

deploy_tag() {
  local tag="$1"
  if [[ "$SKIP_PULL" != true ]]; then
    PAMS_IMAGE_TAG="$tag" "${compose[@]}" pull api web || return 1
  fi
  PAMS_IMAGE_TAG="$tag" "${compose[@]}" up -d --wait --wait-timeout "$TIMEOUT" || return 1
  PAMS_IMAGE_TAG="$tag" "${compose[@]}" exec -T api \
    curl -fsS http://127.0.0.1:9090/actuator/health/readiness >/dev/null || return 1
}

echo "Deploying $DEPLOY_ENV image $TAG"
if deploy_tag "$TAG"; then
  printf '%s\n' "$TAG" > "$CURRENT_FILE"
  if [[ -n "$CURRENT" && "$CURRENT" != "$TAG" ]]; then
    printf '%s\n' "$CURRENT" > "$PREVIOUS_FILE"
  fi
  echo "Deployment healthy: $DEPLOY_ENV is on $TAG"
  exit 0
fi

echo "Deployment failed: $DEPLOY_ENV is not healthy on $TAG" >&2
if [[ -n "$CURRENT" && "$CURRENT" != "$TAG" ]]; then
  echo "Rolling back to previous locally available tag $CURRENT" >&2
  SKIP_PULL=true
  if deploy_tag "$CURRENT"; then
    printf '%s\n' "$CURRENT" > "$CURRENT_FILE"
    printf '%s\n' "$TAG" > "$PREVIOUS_FILE"
    echo "Rollback healthy: $DEPLOY_ENV is back on $CURRENT" >&2
  else
    echo "Rollback failed; restore database backup and page on-call" >&2
  fi
fi
exit 1
