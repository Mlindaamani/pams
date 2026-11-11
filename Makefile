SHELL := /bin/bash
BACKEND := ./backend
MVN := $(BACKEND)/mvnw -f $(BACKEND)/pom.xml

.PHONY: up down logs test build lint format migrate openapi web-build web-test web-lint web-e2e stripe-test-clock tempo k6 prod-build prod-up clean

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

openapi:
	curl -fsS http://localhost:8080/v3/api-docs -o openapi.json

tempo:
	docker compose up -d tempo

k6:
	k6 run ops/k6/pams-smoke.js

prod-build:
	docker compose -f docker-compose.prod.yml build

prod-up:
	docker compose -f docker-compose.prod.yml up -d

web-build:
	docker compose build web

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
