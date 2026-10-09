# Finanssi 2

Monorepo for the Finanssi 2 game.

- `finanssi2-backend/` – Spring Boot backend (Java 25, Gradle, PostgreSQL, Firebase auth)
- `finanssi2-web/` – React frontend (Deno + Vite)

Current implementation status, decisions and todos:

- [Backend notes](finanssi2-backend/STATUS.md)
- [Frontend notes](finanssi2-web/STATUS.md)
- [Production infrastructure setup](infra/README.md)

Private single-player debug mode is implemented; remaining live acceptance checks are recorded in the status notes.

## Prerequisites

- Java 25
- Docker (for PostgreSQL)
- [Deno](https://deno.com/)

### Production frontend build configuration

From `finanssi2-web/`, create an untracked `.env.production` containing the Firebase web app's public settings and the HTTPS API URL:

```dotenv
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=your-project.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
VITE_FINANSSI_API_URL=https://api.finanssi.bizhop.fi
```

Run `deno task build` from that directory. The build validates required settings and writes the production app to `finanssi2-web/dist/`;
that directory contains public frontend configuration and must not contain Firebase Admin credentials.

### Backend container image

Build the Java 25 backend image from the backend directory, targeting the deployment architecture (ARM64 for the planned `t4g` host):

```bash
cd finanssi2-backend
docker build --platform linux/arm64 -t finanssi2-backend:local .
```

The image uses the Gradle wrapper version recorded by the project, excludes local credentials and build state, and runs as UID 10001.
Runtime database and Firebase credentials must be injected when the container starts; they are not part of the image. It includes
AWS's public RDS global CA bundle with a build-time checksum check; refresh this bundle from `truststore.pki.rds.amazonaws.com`
when AWS rotates its RDS CAs.
For production, activate Spring's `prod` profile. It requires Spring config-tree files under `/run/finanssi2/config/` for
`spring.datasource.url`, `spring.datasource.username` and `spring.datasource.password`; the URL must use the Aurora cluster hostname.
The profile enables hostname-verified TLS, a four-connection pool that closes idle sessions for Aurora auto-pause, and proxy-forwarded
headers. `/api/health/live` is a public liveness response that does not query the database.

## Running locally

### 1. Database

```bash
cd finanssi2-backend
docker compose up -d
```

Starts PostgreSQL on port 5432. The database uses a local Docker volume and survives application restarts. Game state and game events
use JSONB payloads; Hibernate creates and updates the schema from the Java persistence entities. For an empty database reset, run
`docker compose down -v` before starting it again. Existing databases should be reset after the profile-reference change to remove
legacy name/avatar snapshots in chat rows and JSON game state; the schema updater does not drop those old fields automatically.

### 2. Backend

Place the Firebase service account key at `finanssi2-backend/finanssi2-firebase-adminsdk.json` (not in git), then run from the `finanssi2-backend` directory:

```bash
cd finanssi2-backend
./gradlew bootRun
```

The API runs at http://localhost:8080.

Firebase Authentication must have Google and email/password providers enabled. Email/password users must verify their address before
the backend provisions a profile or allows game/chat access. Google and password sign-in share an application profile only when Firebase
has linked them to the same Firebase account. The app lets signed-in users edit their display name and upload/remove a custom avatar;
avatars are processed and stored in PostgreSQL, and game/chat entries keep the name and avatar snapshot from when they were created.
`GET /api/me` supplies the profile and debug capability used by the frontend. Backend upload limits and image validation are configured
for JPEG/PNG input up to 2 MiB; processed images are bounded JPEGs.

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
| `deno task build` | Production build; requires `.env.production` as described above |
| `deno task typecheck` | Type-check the sources |
| `deno task test` | Run API transport tests |
| `deno task lint` | Lint the sources (unused imports/variables etc.) |
| `deno task fmt` | Format the code |
| `deno task fmt:check` | Check formatting without changing files (for CI) |


## Private debug games

Debug access is disabled by default. Configure `finanssi2.debug.allowed-emails` in backend deployment configuration,
or pass it locally when starting the backend:

```bash
./gradlew bootRun --args='--finanssi2.debug.allowed-emails=you@example.com'
```

Use the exact email of a Firebase account with a verified email claim. Comma-separated addresses are trimmed and
compared case-insensitively; domains and wildcard patterns do not grant access. Restart the backend and reconnect after changes.
Keep actual accounts in local/deployment configuration.

After sign-in, an eligible account sees “Create debug game”. One human controls 2–6 seats, following the “Controlling” label for
turns and pending decisions. Optional dice apply to one command; next-card controls reorder a deck without executing the card.
Debug games are private to their owner and can be closed normally or permanently deleted with their history.
