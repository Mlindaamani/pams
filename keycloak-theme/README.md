# PAMS Keycloak theme

Keycloakify + MUI login theme for PAMS.

## Build

Requires Node 22 and Maven with a JDK. The theme targets Keycloak 26.

```bash
npm ci
npm run build-keycloak-theme
```

The JAR is written to `dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar`.

The Compose stack builds this theme into a PAMS Keycloak image automatically. After changing theme
files, rebuild the Keycloak service:

```bash
docker compose build keycloak
docker compose up -d keycloak
```

The theme registers as `pams` and is selected by the realm's `loginTheme`.
