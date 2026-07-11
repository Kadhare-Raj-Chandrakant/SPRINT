# ══════════════════════════════════════════════════════════════════════
# Makefile for glint — the PR-triage bot.
#
# A convenience layer over the Maven wrapper (bot/mvnw) and scripts/dev.sh.
# Run `make help` (or just `make`) to see available targets.
# ══════════════════════════════════════════════════════════════════════

# Use bash with strict flags for every recipe line.
SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c

ROOT_DIR := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
BOT_DIR  := $(ROOT_DIR)/bot
DEV      := $(ROOT_DIR)/scripts/dev.sh
MVN      := $(BOT_DIR)/mvnw

# Maven flags: allow `make test MVNFLAGS=-Dtest=FooTest`
MVNFLAGS ?=

.DEFAULT_GOAL := help

# ── Meta ──────────────────────────────────────────────────────────────
.PHONY: help
help: ## Show this help
	@echo "glint — make targets:"
	@echo
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "Examples:"
	@echo "  make setup            # env file + build + local database"
	@echo "  make dev              # db-up then run"
	@echo "  make test MVNFLAGS=-Dtest=TokenServiceTest"

.PHONY: check
check: ## Verify prerequisites (Java 21, Docker, Maven wrapper)
	@$(DEV) check

.PHONY: env
env: ## Create bot/.env from .env.example if missing
	@$(DEV) env

# ── Build / test ──────────────────────────────────────────────────────
.PHONY: build
build: ## Compile and package, skipping tests
	@$(DEV) build

.PHONY: package
package: ## Build the runnable jar (runs tests)
	@$(DEV) package

.PHONY: test
test: ## Run the test suite (pass MVNFLAGS=... for extra Maven args)
	@$(DEV) test $(MVNFLAGS)

.PHONY: clean
clean: ## Remove Maven build output
	@$(DEV) clean

# ── Run ───────────────────────────────────────────────────────────────
.PHONY: run
run: ## Run the app (spring-boot:run)
	@$(DEV) run

.PHONY: dev
dev: db-up run ## Start the local database, then run the app

# ── Database (local Docker PostgreSQL) ────────────────────────────────
.PHONY: db-up
db-up: ## Start a local PostgreSQL container
	@$(DEV) db-up

.PHONY: db-down
db-down: ## Stop and remove the local PostgreSQL container
	@$(DEV) db-down

.PHONY: db-logs
db-logs: ## Tail the PostgreSQL container logs
	@$(DEV) db-logs

.PHONY: db-psql
db-psql: ## Open a psql shell in the container
	@$(DEV) db-psql

# ── Composite ─────────────────────────────────────────────────────────
.PHONY: setup
setup: check env build db-up ## One-shot: check tools, make .env, build, start DB
	@echo
	@echo "Setup complete. Next:"
	@echo "  1. Edit bot/.env with your GitHub App + LLM secrets."
	@echo "  2. Place the GitHub App key at bot/certs/github-app.pem."
	@echo "  3. Run: make run   (or 'make dev')"
