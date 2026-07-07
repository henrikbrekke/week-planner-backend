# week-planner-backend

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
