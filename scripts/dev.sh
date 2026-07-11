#!/usr/bin/env bash
#
# dev.sh — developer helper for the glint PR-triage bot.
#
# A thin, dependency-checking wrapper around the Maven build and a local
# PostgreSQL container. Every subcommand is also reachable through the root
# Makefile; this script is what the Makefile calls under the hood and what you
# run directly for one-off tasks.
#
# Usage:
#   scripts/dev.sh <command> [args...]
#
# Commands:
#   check              Verify required tooling (Java 21, Docker) is present.
#   env                Create bot/.env from bot/.env.example if missing.
#   db-up              Start a local PostgreSQL container for development.
#   db-down            Stop and remove the local PostgreSQL container.
#   db-logs            Tail the PostgreSQL container logs.
#   db-psql            Open a psql shell inside the container.
#   build              Compile and package the app (skips tests).
#   test [args...]     Run the test suite (extra args passed to Maven).
#   run                Run the app with spring-boot:run.
#   package            Build the runnable jar (runs tests).
#   clean              Remove Maven build output.
#   help               Show this message.
#
set -euo pipefail

# ── Locations ────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BOT_DIR="${ROOT_DIR}/bot"
MVN="${BOT_DIR}/mvnw"

# ── Database defaults (override via environment) ──────────────────────────
DB_CONTAINER="${DB_CONTAINER:-glint-postgres}"
DB_IMAGE="${DB_IMAGE:-postgres:16-alpine}"
DB_NAME="${DB_NAME:-pr_triage}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
DB_PORT="${DB_PORT:-5432}"

# ── Pretty output ─────────────────────────────────────────────────────────
info()  { printf '\033[1;34m[dev]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[dev]\033[0m %s\n' "$*" >&2; }
error() { printf '\033[1;31m[dev]\033[0m %s\n' "$*" >&2; }
die()   { error "$*"; exit 1; }

have() { command -v "$1" >/dev/null 2>&1; }

# ── Commands ──────────────────────────────────────────────────────────────

cmd_check() {
    local ok=1

    if have java; then
        local ver
        ver="$(java -version 2>&1 | head -1)"
        if java -version 2>&1 | grep -Eq '"(21|22|23|24|25)'; then
            info "Java OK: ${ver}"
        else
            warn "Java 21+ required, found: ${ver}"
            ok=0
        fi
    else
        warn "java not found on PATH"
        ok=0
    fi

    if have docker; then
        info "Docker OK: $(docker --version)"
    else
        warn "docker not found (needed for 'db-up'; skip if you use an external DB)"
    fi

    if [[ -x "${MVN}" ]]; then
        info "Maven wrapper OK: ${MVN}"
    else
        warn "Maven wrapper missing or not executable: ${MVN}"
        ok=0
    fi

    [[ "${ok}" -eq 1 ]] || die "Prerequisite check failed."
    info "All required prerequisites present."
}

cmd_env() {
    local example="${BOT_DIR}/.env.example"
    local target="${BOT_DIR}/.env"
    [[ -f "${example}" ]] || die "Missing ${example}"
    if [[ -f "${target}" ]]; then
        info ".env already exists at ${target} (leaving untouched)"
    else
        cp "${example}" "${target}"
        info "Created ${target} from .env.example — edit it with your secrets."
    fi
}

cmd_db_up() {
    have docker || die "docker is required for db-up"
    if docker ps -a --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
        if docker ps --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
            info "PostgreSQL container '${DB_CONTAINER}' already running."
            return 0
        fi
        info "Starting existing container '${DB_CONTAINER}'..."
        docker start "${DB_CONTAINER}" >/dev/null
    else
        info "Creating PostgreSQL container '${DB_CONTAINER}' (${DB_IMAGE})..."
        docker run -d \
            --name "${DB_CONTAINER}" \
            -e POSTGRES_DB="${DB_NAME}" \
            -e POSTGRES_USER="${DB_USER}" \
            -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
            -p "${DB_PORT}:5432" \
            "${DB_IMAGE}" >/dev/null
    fi

    info "Waiting for PostgreSQL to accept connections..."
    # Run a real query against the target DB rather than trusting pg_isready:
    # during first-boot initdb the entrypoint runs a temporary server that
    # reports ready before POSTGRES_DB is actually created (a known race).
    for _ in $(seq 1 60); do
        if docker exec "${DB_CONTAINER}" \
            psql -U "${DB_USER}" -d "${DB_NAME}" -tAc 'SELECT 1' >/dev/null 2>&1; then
            info "PostgreSQL ready on localhost:${DB_PORT} (db=${DB_NAME}, user=${DB_USER})"
            return 0
        fi
        sleep 1
    done
    die "PostgreSQL did not become ready in time. Check: docker logs ${DB_CONTAINER}"
}

cmd_db_down() {
    have docker || die "docker is required for db-down"
    if docker ps -a --format '{{.Names}}' | grep -qx "${DB_CONTAINER}"; then
        info "Removing PostgreSQL container '${DB_CONTAINER}'..."
        docker rm -f "${DB_CONTAINER}" >/dev/null
        info "Removed."
    else
        info "No container named '${DB_CONTAINER}' to remove."
    fi
}

cmd_db_logs() {
    have docker || die "docker is required for db-logs"
    docker logs -f "${DB_CONTAINER}"
}

cmd_db_psql() {
    have docker || die "docker is required for db-psql"
    docker exec -it "${DB_CONTAINER}" psql -U "${DB_USER}" -d "${DB_NAME}"
}

cmd_build() {
    info "Building (tests skipped)..."
    (cd "${BOT_DIR}" && "${MVN}" -q clean package -DskipTests)
    info "Build complete. Jar in ${BOT_DIR}/target/"
}

cmd_test() {
    info "Running tests..."
    (cd "${BOT_DIR}" && "${MVN}" test "$@")
}

cmd_run() {
    [[ -f "${BOT_DIR}/.env" ]] || warn "No bot/.env found — run 'scripts/dev.sh env' first."
    info "Starting glint on :${PORT:-8080} (Ctrl-C to stop)..."
    (cd "${BOT_DIR}" && "${MVN}" spring-boot:run)
}

cmd_package() {
    info "Packaging runnable jar (with tests)..."
    (cd "${BOT_DIR}" && "${MVN}" clean package)
    info "Done. Jar in ${BOT_DIR}/target/"
}

cmd_clean() {
    info "Cleaning build output..."
    (cd "${BOT_DIR}" && "${MVN}" -q clean)
    info "Clean."
}

cmd_help() {
    sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# ── Dispatch ──────────────────────────────────────────────────────────────
main() {
    local cmd="${1:-help}"
    [[ $# -gt 0 ]] && shift || true
    case "${cmd}" in
        check)            cmd_check "$@" ;;
        env)              cmd_env "$@" ;;
        db-up)            cmd_db_up "$@" ;;
        db-down)          cmd_db_down "$@" ;;
        db-logs)          cmd_db_logs "$@" ;;
        db-psql)          cmd_db_psql "$@" ;;
        build)            cmd_build "$@" ;;
        test)             cmd_test "$@" ;;
        run)              cmd_run "$@" ;;
        package)          cmd_package "$@" ;;
        clean)            cmd_clean "$@" ;;
        help|-h|--help)   cmd_help ;;
        *) error "Unknown command: ${cmd}"; echo; cmd_help; exit 1 ;;
    esac
}

main "$@"
