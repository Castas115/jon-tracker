#!/usr/bin/env bash
# Local CI/CD: lint + test + deploy to raspi.
# Usage: ./scripts/deploy.sh [--skip-checks] [--no-build] [--host raspi]
#   --skip-checks   skip ruff lint/format and pytest
#   --no-build      docker compose up without rebuild
#   --host <name>   ssh host (default: raspi); resolved via ~/.ssh/config

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

HOST="raspi"
REMOTE_DIR="~/jon-tracker"
SKIP_CHECKS=0
BUILD=1

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-checks) SKIP_CHECKS=1; shift ;;
        --no-build)    BUILD=0;        shift ;;
        --host)        HOST="$2";      shift 2 ;;
        -h|--help)     sed -n '2,7p' "$0"; exit 0 ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m!! %s\033[0m\n' "$*" >&2; exit 1; }

# ---- preflight ----
command -v rsync >/dev/null || fail "rsync not found"
command -v ssh   >/dev/null || fail "ssh not found"
ssh -G "$HOST" >/dev/null 2>&1 || fail "ssh host '$HOST' not configured in ~/.ssh/config"

# Prefer system tools (NixOS-friendly); fall back to uv-managed venv.
ruff_cmd()   { if command -v ruff   >/dev/null; then ruff   "$@"; else uv run ruff   "$@"; fi; }
pytest_cmd() { if command -v pytest >/dev/null; then pytest "$@"; else uv run pytest "$@"; fi; }

# ---- checks ----
if [[ $SKIP_CHECKS -eq 0 ]]; then
    log "backend: ruff check"
    (cd backend && ruff_cmd check .)

    log "backend: ruff format --check"
    (cd backend && ruff_cmd format --check .)

    if compgen -G "backend/tests/**" > /dev/null; then
        log "backend: pytest"
        (cd backend && pytest_cmd -q)
    else
        log "backend: no tests yet, skipping pytest"
    fi
else
    log "skipping checks (--skip-checks)"
fi

# ---- sanity: raspi reachable ----
log "raspi: ssh probe ($HOST)"
ssh -o ConnectTimeout=5 -o BatchMode=yes "$HOST" 'echo ok' >/dev/null \
    || fail "cannot ssh '$HOST' (run ssh-copy-id $HOST first?)"

log "raspi: docker probe"
ssh "$HOST" 'docker info >/dev/null 2>&1' \
    || fail "docker not running on raspi (check 'systemctl status docker')"

# ---- ensure remote dir ----
ssh "$HOST" "mkdir -p ${REMOTE_DIR}"

# ---- sync ----
log "rsync → $HOST:$REMOTE_DIR"
rsync -av --delete \
    --exclude '.git/' \
    --exclude '.venv/' \
    --exclude '__pycache__/' \
    --exclude 'data/' \
    --exclude 'secrets/' \
    --exclude 'node_modules/' \
    --exclude '.env' \
    ./ "$HOST:${REMOTE_DIR}/"

# ---- compose up ----
COMPOSE_CMD="docker compose up -d"
[[ $BUILD -eq 1 ]] && COMPOSE_CMD="docker compose up -d --build"

log "raspi: $COMPOSE_CMD"
ssh "$HOST" "cd ${REMOTE_DIR} && ${COMPOSE_CMD}"

log "raspi: status"
ssh "$HOST" "cd ${REMOTE_DIR} && docker compose ps"

# ---- health probe (retry up to ~20s for uvicorn bind) ----
log "raspi: health"
for i in {1..10}; do
    if ssh "$HOST" 'curl -sf --max-time 2 http://127.0.0.1:8000/health' >/dev/null 2>&1; then
        log "deploy ok ✓"
        exit 0
    fi
    sleep 2
done
fail "health check failed after 20s (check 'docker compose logs backend' on raspi)"
