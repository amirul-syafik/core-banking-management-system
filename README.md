# Core Banking Management System (CBMS)

A full-stack banking workflow demonstration built with **Java 17, Spring Boot, PostgreSQL, React, and TypeScript**. The frontend and backend are separate applications connected through REST APIs.

## Features

- JWT-based login and role-based access control (VIEWER, MAKER, ADMIN)
- Administrative user creation, role assignment, password reset and account activation
- Customer and identification management
- Account management and transactions, including inquiry, posting, reversal and reconciliation
- Financing applications, facilities, credit lines and related workflows
- Product, scheme and effective-dated rate maintenance
- Dashboard reporting, application logging and audit views

> Registration note: the current authentication API supports login. User creation is administered through User Management; public self-registration is not currently exposed. Clarify or address this assignment requirement before submitting.

## Project structure

```text
backend/          Spring Boot application source
frontend/         React + TypeScript application
pom.xml           Maven project configuration
```

## Prerequisites

- JDK 17
- Maven compatible with the included Spring Boot version
- Node.js and npm compatible with Vite 8
- PostgreSQL and pgAdmin or `psql`

## Database setup

Export a **plain-text PostgreSQL SQL script containing both schema and sanitized demonstration data** to `database/corebank_lite.sql`. Create a blank database and import the script with `psql` or pgAdmin Query Tool. See `docs/DATABASE_SETUP.md` for export and import instructions. The old `corebank-lite.sql` custom-format backup is not suitable for `psql`.

The backend expects:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/corebank_lite
spring.datasource.username=corebank_app
spring.datasource.password=${DB_PASSWORD}
app.jwt.secret=${JWT_SECRET}
```

Never commit real credentials or a database containing private information. Only publish fictional/sanitized demonstration data.

## Start the backend (Windows PowerShell)

From the project root:

```powershell
$env:DB_PASSWORD = '<your-local-database-password>'
$env:JWT_SECRET = '<your-generated-random-signing-secret>'
.\mvnw.cmd spring-boot:run
```

If Maven Wrapper is unavailable, use `mvn spring-boot:run`. The default backend port is **8080**. Generate a strong random JWT secret; do not reuse an example or commit it.

The application validates the database schema on startup (`spring.jpa.hibernate.ddl-auto=validate`), so the database SQL script must be imported first.

The document storage path in `application.properties` is Windows-specific (`D:/cbms-private-documents`). Create that directory locally or override `app.document.storage-path` for your environment.

## Start the frontend

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. The Vite development server proxies `/api` requests to `http://localhost:8080`.

## Quality checks

```powershell
# project root
.\mvnw.cmd test

# frontend/
npm run lint
npm run build
```

## Roles and authentication

- **VIEWER:** read-only access to permitted resources
- **MAKER:** permitted create/update workflows
- **ADMIN:** administrative functions and privileged workflows

Development-only user seeding exists under Spring profile `dev-seed`. **Do not enable that profile in a public or production environment:** it creates accounts with known passwords. Use User Management for controlled user provisioning.

## API

Authentication: `POST /api/auth/login`. Additional REST endpoints are implemented under `/api/`. API documentation routes are restricted to ADMIN by Spring Security.

## Screenshots

Add screenshots after checking that no customer identifiers, names, tokens, credentials or other sensitive information are visible:

```text
docs/screenshots/overview.png
docs/screenshots/customers.png
docs/screenshots/financing.png
docs/screenshots/operations.png
```

## Submission

This repository is provided for evaluation as a full-stack application assignment. See `docs/SUBMISSION_CHECKLIST.md` for the reproducible verification steps.

Copyright © 2026. All rights reserved unless a separate license is expressly provided.
