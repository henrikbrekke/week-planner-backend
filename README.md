# week-planner-backend

Kotlin + Ktor REST API backend for the week planner application.

## Stack

- **Runtime**: JVM 17
- **Framework**: [Ktor](https://ktor.io/) (CIO engine)
- **Database**: PostgreSQL via [Exposed](https://github.com/JetBrains/Exposed) ORM + [HikariCP](https://github.com/brettwooldridge/HikariCP)
- **Migrations**: [Flyway](https://flywaydb.org/)
- **Auth**: JWT (access + refresh tokens) + Google OAuth
- **Build**: Gradle (Kotlin DSL)
- **Tests**: [Kotest](https://kotest.io/) + [Testcontainers](https://testcontainers.com/) (PostgreSQL)

## Architecture

```
src/main/kotlin/com/weekplanner/
  Application.kt              ← entry point, wires the dependency graph
  plugins/                    ← Ktor plugin configuration
    Database.kt               ← HikariCP + Flyway + Exposed
    Security.kt               ← JWT authentication
    Serialization.kt          ← kotlinx.serialization / JSON
    Routing.kt                ← CORS, status pages, route registration
  auth/                       ← registration, login, Google OAuth, JWT
    AuthRoutes.kt
    AuthService.kt
    AuthRepository.kt
    models/
  images/                     ← image upload, tagging, deletion
    ImageRoutes.kt
    ImageService.kt
    ImageRepository.kt
    models/
  plans/                      ← weekly plan slots (upsert, delete)
    PlanRoutes.kt
    PlanService.kt
    PlanRepository.kt
    models/
  common/                     ← shared utilities and error types
    Extensions.kt
    Errors.kt
src/main/resources/
  application.conf            ← Ktor deployment config
  db/migration/               ← Flyway SQL migrations (V1–V4)
src/test/kotlin/              ← Kotest integration tests (Testcontainers)
```

## Prerequisites

- JDK 17+
- PostgreSQL (local or Docker)
- Docker (for running integration tests via Testcontainers)

## Setup

1. Copy `.env.example` to `.env` and fill in the required values.
2. Create the database: `createdb weekplanner`
3. Run the server — Flyway will apply migrations automatically on startup.

## Environment Variables

| Variable              | Description                                     | Required        |
|-----------------------|-------------------------------------------------|-----------------|
| `DATABASE_URL`        | PostgreSQL URL (`postgresql://host:port/db`)    | Yes             |
| `JWT_ACCESS_SECRET`   | Secret for signing access tokens (1 h TTL)      | Yes             |
| `JWT_REFRESH_SECRET`  | Secret for signing refresh tokens (30 d TTL)    | Yes             |
| `GOOGLE_CLIENT_ID`    | Google OAuth client ID (leave blank to disable) | No              |
| `STORAGE_DIR`         | Directory for uploaded images                   | No (default `./storage`) |
| `PORT`                | Server port                                     | No (default `3000`)      |
| `KTOR_ENV`            | Set to `production` to enforce required secrets | No              |

## Running

```bash
# Development (reads from .env via your shell or a tool like direnv)
./gradlew run

# Production JAR
./gradlew build
java -jar build/libs/week-planner-backend-0.0.1-all.jar
```

## Testing

```bash
./gradlew test
```

Tests use Testcontainers to spin up a real PostgreSQL container — Docker must be running.

## API

All endpoints are under `/api`. Protected routes require `Authorization: ******

| Method | Path                    | Auth | Description                        |
|--------|-------------------------|------|------------------------------------|
| POST   | `/api/auth/register`    | —    | Register with email + password     |
| POST   | `/api/auth/login`       | —    | Login with email + password        |
| POST   | `/api/auth/google`      | —    | Login / register with Google token |
| POST   | `/api/auth/logout`      | ✓    | Invalidate all refresh tokens      |
| GET    | `/api/auth/me`          | ✓    | Get current user                   |
| GET    | `/api/images`           | ✓    | List images (optional `?tags=`)    |
| POST   | `/api/images`           | ✓    | Upload image (multipart)           |
| PATCH  | `/api/images/:id`       | ✓    | Update image name / tags           |
| DELETE | `/api/images/:id`       | ✓    | Delete image                       |
| GET    | `/api/plans`            | ✓    | Get week plan (`?weekStart=`)      |
| PUT    | `/api/plans/slot`       | ✓    | Upsert a plan slot                 |
| DELETE | `/api/plans/slot/:id`   | ✓    | Remove a plan slot                 |
| GET    | `/api/health`           | —    | Health check                       |
| GET    | `/uploads/:filename`    | —    | Serve uploaded images              |


Minimal Node.js backend for the Week Planner frontend.

## Features

- Email/password registration and login
- Google sign-in endpoint with ID token verification
- Bearer-token protected `auth/me`, image, and planner routes
- Multipart image uploads with simple file-backed persistence
- Weekly plan slot upsert and delete endpoints

## Getting started

### 1. Install dependencies

```bash
npm install
```

### 2. Configure environment

Copy `.env.example` to `.env` and update the values:

```bash
cp .env.example .env
```

| Variable | Description |
| --- | --- |
| `PORT` | Port for the API server. Defaults to `3000`. |
| `JWT_ACCESS_SECRET` | Secret used to sign access tokens. Required in production. |
| `JWT_REFRESH_SECRET` | Secret used to sign refresh tokens. Required in production. |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID used to verify `/api/auth/google` credentials. |
| `STORAGE_DIR` | Directory for JSON data and uploaded images. Defaults to `./storage`. |

### 3. Start the server

```bash
npm start
```

For local development with file watching:

```bash
npm run dev
```

## API

All frontend-facing endpoints are served under `/api`.

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### Images

- `GET /api/images`
- `POST /api/images`
- `PATCH /api/images/:id`
- `DELETE /api/images/:id`

Uploaded files are served from `/uploads/:filename`.

### Planner

- `GET /api/plans?weekStart=YYYY-MM-DD`
- `PUT /api/plans/slot`
- `DELETE /api/plans/slot/:id`

## Tests

Run the integration tests with:

```bash
npm test
```
