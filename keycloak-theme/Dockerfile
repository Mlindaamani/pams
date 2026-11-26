# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS theme-build

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates maven openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /theme/keycloak-theme
COPY keycloak-theme/package.json keycloak-theme/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY keycloak-theme ./
COPY shared /theme/shared
RUN --mount=type=cache,target=/root/.m2 npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:26.8.2

COPY --from=theme-build /theme/keycloak-theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar \
    /opt/keycloak/providers/pams-theme.jar

RUN /opt/keycloak/bin/kc.sh build
