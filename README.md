# Finanssi 2

Monorepo for the Finanssi 2 game.

- `finanssi2-backend/` – Spring Boot backend (Java 25, Gradle, MongoDB, Firebase auth)
- `finanssi2-web/` – React frontend (Deno + Vite)

## Prerequisites

- Java 25
- Docker (for MongoDB)
- [Deno](https://deno.com/)

## Running locally

### 1. Database

```bash
cd finanssi2-backend
docker compose up -d
```

Starts MongoDB on port 27017 and mongo-express (DB admin UI) at http://localhost:8081.

### 2. Backend

Place the Firebase service account key at `finanssi2-backend/finanssi2-firebase-adminsdk.json` (not in git), then run from the `finanssi2-backend` directory:

```bash
cd finanssi2-backend
./gradlew bootRun
```

The API runs at http://localhost:8080.

### 3. Frontend

Create `finanssi2-web/.env.development` (not in git) with the Firebase web config and the backend URL:

```
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
VITE_FINANSSI_API_URL=http://localhost:8080
```

Then start the dev server:

```bash
cd finanssi2-web
deno task dev
```

The app runs at http://localhost:3000.

Other frontend tasks (run from `finanssi2-web`, defined in `deno.json` like npm scripts):

| Task | Does |
|---|---|
| ~~`deno task build`~~ | Production build – **disabled for now**: there is no Firebase production environment yet (see comment in `deno.json`) |
| `deno task typecheck` | Type-check the sources |
| `deno task lint` | Lint the sources (unused imports/variables etc.) |
| `deno task fmt` | Format the code |
| `deno task fmt:check` | Check formatting without changing files (for CI) |
