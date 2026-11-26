SHELL := /bin/bash
BACKEND := ./backend
MVN := $(BACKEND)/mvnw -f $(BACKEND)/pom.xml

.PHONY: up down logs test build lint format migrate api-types rls-inventory keycloak-verify keycloak-orphans backend-compile web-build web-test web-lint web-e2e stripe-test-clock k6 load-test backup-verify prod-build prod-up prod-deploy prod-rollback backup prod-backup restore prod-restore clean

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f

test:
	$(MVN) test

build:
	$(MVN) verify

lint:
	$(MVN) spotless:check

format:
	$(MVN) spotless:apply

migrate:
	docker compose --profile tools run --rm flyway

api-types:
	cd frontend && npm run api:generate

rls-inventory:
	docker compose exec -T postgres psql -U pams -d pams -P pager=off -f - < ops/postgres/rls-inventory.sql

keycloak-verify:
	ops/keycloak/verify-admin-client.sh

keycloak-orphans:
	python3 ops/keycloak/reconcile_orphan_profiles.py

backend-compile:
	docker compose exec api mvn -q -DskipTests -Dmaven.repo.local=/tmp/.m2 compile

k6:
	k6 run ops/k6/pams-smoke.js

load-test:
	k6 run ops/k6/pams-load.js

backup-verify:
	@test -n "$(BACKUP_DIR)" || (echo "Usage: make backup-verify BACKUP_DIR=/path/to/backup" >&2; exit 1)
	BACKUP_DIR=$(BACKUP_DIR) ops/backup/verify.sh

backup:
	ops/backup/backup.sh

prod-backup:
	COMPOSE_FILE=docker-compose.prod.yml ops/backup/backup.sh

restore:
	@test -n "$(BACKUP_DIR)" || (echo "Usage: make restore BACKUP_DIR=/path/to/backup" >&2; exit 1)
	BACKUP_DIR=$(BACKUP_DIR) ops/backup/restore.sh --confirm

prod-restore:
	@test -n "$(BACKUP_DIR)" || (echo "Usage: make prod-restore BACKUP_DIR=/path/to/backup" >&2; exit 1)
	COMPOSE_FILE=docker-compose.prod.yml BACKUP_DIR=$(BACKUP_DIR) ops/backup/restore.sh --confirm

prod-build:
	docker compose -f docker-compose.prod.yml build

prod-up:
	docker compose -f docker-compose.prod.yml up -d

prod-deploy:
	@test -n "$(TAG)" || (echo "Usage: make prod-deploy TAG=<immutable-tag>" >&2; exit 1)
	./ops/production/deploy.sh --tag $(TAG) --env-file $${ENV_FILE:-.env.production} --environment $${DEPLOY_ENV:-production}

prod-rollback:
	./ops/production/rollback.sh --env-file $${ENV_FILE:-.env.production} --environment $${DEPLOY_ENV:-production}

web-build:
	docker build -t pams-web:dev ./frontend

web-test:
	cd frontend && npm ci && npm test

web-lint:
	cd frontend && npm run lint && npm run format:check

web-e2e:
	cd frontend && npx playwright test

stripe-test-clock:
	set -a; source .env; set +a; STRIPE_TEST_CLOCK_E2E=true $(MVN) -Dtest=StripeTestClockIntegrationTest test

clean:
	$(MVN) clean
