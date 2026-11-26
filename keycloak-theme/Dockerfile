FROM node:22-bookworm-slim AS theme-build

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates maven openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /theme
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:26.8.2

COPY --from=theme-build /theme/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar \
    /opt/keycloak/providers/pams-theme.jar

RUN /opt/keycloak/bin/kc.sh build
