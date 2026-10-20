# Relay monorepo task runner. `make help` lists targets.
# Windows: run from Git Bash or WSL.

SHELL := /bin/bash
COMPOSE := docker compose -f docker/docker-compose.yml --env-file .env
PY ?= python3
API_URL ?= http://localhost:8080
WORLD_URL ?= http://localhost:9210
TOKEN ?= relay-demo-token

.DEFAULT_GOAL := help
.PHONY: help up down logs ps db world sidecar api worker web build test test-backend \
        test-frontend test-sidecar fmt lint smoke drill nl-eval validate-pack hashes clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------- stack
up: .env ## Build and start the full stack in Docker
	$(COMPOSE) up -d --build
	@echo "API $(API_URL)   console http://localhost:3000   world $(WORLD_URL)"

down: ## Stop the stack (keeps the database volume)
	$(COMPOSE) down

logs: ## Tail all service logs
	$(COMPOSE) logs -f --tail=100

ps: ## Show service status
	$(COMPOSE) ps

.env:
	cp .env.example .env

# ---------------------------------------------------------------- local processes
db: .env ## Start only Postgres in Docker
	$(COMPOSE) up -d postgres

world: ## Run the mock world on :9210 (foreground)
	$(PY) scripts/mock_world.py --port 9210

sidecar: ## Run the AI sidecar on :8090 (foreground)
	cd ai-sidecar && $(PY) -m uvicorn app.main:app --port 8090 --reload

api: ## Run the backend API (profile api) on :8080
	cd backend && ./gradlew bootRun --args='--spring.profiles.active=api'

worker: ## Run a worker JVM (profile worker, no HTTP)
	cd backend && WORKER_ONLY=true ./gradlew bootRun --args='--spring.profiles.active=worker'

web: ## Run the console dev server on :3000
	npm --workspace frontend run dev

# ---------------------------------------------------------------- build & test
build: ## Build backend jar, console and sidecar image
	cd backend && ./gradlew bootJar
	npm --workspace frontend run build

test: test-backend test-frontend test-sidecar ## Run every test suite

test-backend: ## JUnit 5 + Testcontainers (needs Docker)
	cd backend && ./gradlew test

test-frontend: ## Vitest + RTL
	npm --workspace frontend run test

test-sidecar: ## pytest
	cd ai-sidecar && $(PY) -m pytest -q

fmt: ## Apply formatters (google-java-format, prettier, black/ruff)
	cd backend && ./gradlew spotlessApply
	npm --workspace frontend run format
	cd ai-sidecar && $(PY) -m black app tests && $(PY) -m ruff check --fix app tests

lint: ## Static checks
	cd backend && ./gradlew spotlessCheck checkstyleMain spotbugsMain
	npm --workspace frontend run lint && npm --workspace frontend run typecheck
	cd ai-sidecar && $(PY) -m ruff check app tests && $(PY) -m mypy app

# ---------------------------------------------------------------- verification
smoke: ## Run the provided smoke test against a running stack
	$(PY) scripts/smoke_test.py --url $(API_URL) --token $(TOKEN) --world $(WORLD_URL)

drill: ## Kill-and-resume drill (EVALUATION_GUIDE §2) + duplication_check.py
	API_URL=$(API_URL) WORLD_URL=$(WORLD_URL) TOKEN=$(TOKEN) bash docker/drill.sh

nl-eval: ## NL compiler eval over data/nl_eval.jsonl (Good To Have)
	curl -sf -X POST $(API_URL)/workflows/compile/eval -H "Authorization: Bearer $(TOKEN)" | $(PY) -m json.tool

validate-pack: ## Validate the pack fixtures are intact
	$(PY) scripts/validate_pack.py

hashes: ## Print SHA-256 of every reference file (compare with docs/reqs/README.md)
	sha256sum docs/reqs/*.md data/* scripts/*

clean: ## Remove build outputs
	cd backend && ./gradlew clean || true
	rm -rf frontend/.next frontend/coverage
