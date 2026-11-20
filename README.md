# Core Banking Management System (CBMS)

CBMS is a web application for managing core banking records and operational workflows. It provides a React/TypeScript user interface backed by a Java/Spring REST API and PostgreSQL. The repository implements customer, account, deposit, financing, transaction, reference-data and administration workflows; it is not connected to a live banking network or external payment/trading system.

## Implemented areas

- **Customers and KYC:** customer directory and profiles; individual and corporate details; identification and document records; profile pictures; addresses, contacts, employment, relationships, KYC assessments, status history and status-change approval requests.
- **Accounts and transactions:** account applications and approval, account status requests, balance and transaction inquiry, deposits, withdrawals and transfers, reversal requests with approval, reconciliation investigations and transaction/audit views.
- **Fixed deposits:** deposit records and placement, approval, interest/accrual and payout operations, maturity processing and settlement, renewal, premature withdrawal and monitoring.
- **Financing and credit lines:** financing applications and approval, facility booking, disbursement, repayment schedules and repayments, outstanding balances, repricing and closure; collateral and vehicle records, valuations and facility assignments; credit-line limits, drawdowns, repayments, interest and monitoring.
- **Products and operations:** product, scheme, rate, system-parameter and GL-mapping maintenance; operational reports and exports; approval notifications for supported business request types.
- **Administration:** user and role administration, password changes/reset workflows, application logs, activity audit and system monitoring.
- **Islamic fixed-deposit review:** contract profiles, profit-term and ledger-mapping reviews, execution policies, documentary evidence/timeline review, and read-only readiness and external-verification reports.

### Islamic deposit limitation

The Islamic fixed-deposit interfaces and APIs currently cover configuration, documentary review and readiness assessment. The application does **not** execute Islamic investment/trading events, activate Islamic deposits, or post Islamic profit or ledger entries. The default external verification gateway is unconfigured and fails closed. These review screens do not constitute financial execution capability.

## Technology and architecture

- **Backend:** Java 17, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Jakarta Bean Validation, Spring Security, OAuth2 Resource Server/JWT, and Springdoc OpenAPI.
- **Database:** PostgreSQL (the configured JDBC driver and application configuration target PostgreSQL). JPA is configured with `spring.jpa.hibernate.ddl-auto=validate`; the application validates mapped entity schema at startup and does not automatically apply database migrations.
- **Frontend:** React 19, TypeScript 6, React Router 7, Axios, Lucide icons, and Vite 8.
- **Persistence and files:** relational application data is stored in PostgreSQL. Customer documents and profile pictures use private filesystem storage configured by `app.document.storage-path`.
- **Development integration:** Vite serves the UI on port 5173 and proxies `/api` to the backend on port 8080.

The backend is organized into controllers, DTOs, entities, repositories, services, security and configuration packages under `src/main/java/com/cbms`. The frontend lives under `frontend/src` and contains API utilities, authentication, shared components, layouts and module pages. Database scripts and repository documentation are under `database/` and `docs/`.

## Authentication and access control

The API is stateless and uses bearer JWT authentication. Passwords are handled with BCrypt. Endpoint authorization is enforced by Spring Security; the UI also adapts navigation and actions to the user's permissions. The configured application roles used throughout the API include `VIEWER`, `MAKER` and `ADMIN`, with sensitive operations further restricted by endpoint and business approval rules. Approval patterns include maker/checker requests for supported financial and maintenance operations; a role name alone does not bypass those checks.

`POST /api/auth/login` is the login endpoint. Authenticated API requests use the issued bearer token. The OpenAPI/Swagger routes are restricted to administrators. Development-only user seeding is available under the `dev-seed` Spring profile and creates users with fixed, documented-in-source demo passwords. Use that profile only in an isolated local environment; never enable it in a shared or production deployment.

## Database and migration status

The application expects a PostgreSQL database named `corebank_lite` by default, an application login named `corebank_app`, and a schema compatible with the current source. Configuration uses `DB_PASSWORD` rather than a committed password. Tests are configured to use `corebank_lite_test`.

The checked-in `database/corebank_lite.sql` is a plain-text PostgreSQL dump. The repository also contains older/other database artifacts and standalone SQL files under `database/migrations/` and `database/reference-migration/`. There is no Flyway or Liquibase migration runner configured, so these scripts are not automatically applied by the application. The dump and scripts may not represent the same schema version; provision a database from a reviewed, approved baseline and apply only the migration procedure approved for that environment.

The checked-in database archive predates Workflow Management retirement. After restoring that archive, review and apply `database/migrations/20261120_retire_workflow_management.sql` to remove the retired workflow schema and Customer Status Change routing extension before starting the current application. The migration refuses to proceed if it finds open workflow instances, workflow-routed customer status requests, or mode-history rows requiring preservation. It is not run automatically.

Before using any included database artifact, verify its provenance, schema version, role/ownership requirements, and whether its data is sanitized for your environment. Do not restore it over an existing database. Do not use production/customer data in a public repository or development environment.

## Configuration

The main configuration is in `src/main/resources/application.properties`; test overrides are in `application-test.properties`.

| Setting | Purpose | Default/configuration |
| --- | --- | --- |
| `DB_PASSWORD` | Password for the PostgreSQL application login | Required from the environment |
| `JWT_SECRET` | JWT signing secret | Required from the environment; use a strong, private value |
| `APP_LOG_PATH` | Application log file path | `logs/cbms.log` |
| `app.document.storage-path` | Private customer-document and profile-picture storage directory | `D:/cbms-private-documents` |
| `CBMS_DB_POOL_MAX`, `CBMS_DB_POOL_MIN_IDLE` | Hikari connection pool sizing | `12`, `2` |
| `CBMS_PG_CONNECT_TIMEOUT_SECONDS` | PostgreSQL connection timeout | `5` seconds |
| `CBMS_HTTP_MAX_THREADS`, `CBMS_HTTP_ACCEPT_COUNT` | Tomcat request thread and accept queue limits | `100`, `100` |
| `CBMS_FORWARDED_HEADERS_STRATEGY` | Whether forwarded headers are trusted | `none` |

The document directory must be created and protected with filesystem permissions suitable for private customer records. Set the storage path to an appropriate location for non-Windows deployments. Never commit secrets, access tokens, database exports containing real customer data, or uploaded customer documents.

## Local development

### Prerequisites

- JDK 17
- PostgreSQL and a separately provisioned database matching the current application schema
- Node.js/npm versions compatible with the locked Vite 8 toolchain
- PowerShell on Windows (commands below use the checked-in Maven Wrapper)

### Start the backend

From the repository root, set local secrets in the current PowerShell session and start Spring Boot:

```powershell
$env:DB_PASSWORD = '<local-corebank_app-password>'
$env:JWT_SECRET = '<strong-random-local-secret>'
.\mvnw.cmd spring-boot:run
```

The backend listens on port **8080** by default. Startup fails when the configured database is unavailable, required configuration is missing, or JPA entity validation finds an incompatible schema. The app does not create the schema or run the SQL scripts for you.

### Start the frontend

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The frontend's `/api` calls are proxied to `http://localhost:8080` by Vite.

### Development users

For an isolated local database only, the optional `dev-seed` profile creates `viewer`, `maker` and `admin` demo users. Their fixed passwords are present in `DevUserSeeder.java`; change/remove those credentials and never use the profile with real or shared data. Otherwise provision an administrator through the approved local bootstrap process.

## API surface

REST endpoints are rooted at `/api`. Main endpoint groups include `/auth`, `/customers`, `/account-applications`, `/accounts`, `/transactions`, `/fixed-deposits`, `/financing`, `/financing-collaterals`, `/collaterals`, `/vehicles`, `/credit-lines`, `/products`, `/schemes`, `/rates`, `/reports`, `/reconciliation`, `/notifications` and `/admin`. Access is controlled per HTTP method and route in `SecurityConfig`; not every operation is available to every role. Swagger UI and OpenAPI JSON are available through Springdoc when running, subject to administrator authorization.

## Quality checks

Run from the repository root unless noted:

```powershell
.\mvnw.cmd test
```

Run frontend checks from `frontend/`:

```powershell
npm run lint
npm run build
```

## Screenshots and further documentation

Screenshots in [`docs/screenshots`](docs/screenshots/) illustrate selected screens. Review them for personal or confidential information before publishing. Approval, schema and audit documents are available in [`docs`](docs/); audit findings describe their stated evidence and should not be treated as live-environment verification unless explicitly confirmed.

No repository license file is currently included.
