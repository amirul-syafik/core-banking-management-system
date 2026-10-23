# PostgreSQL database setup (schema and demonstration data)

The public repository should contain `database/corebank_lite.sql`: a **plain-text SQL export including both schema and sanitized demo data**. This is not the old `corebank-lite.sql` file, which was a PostgreSQL custom-format (`PGDMP`) backup.

## 1. Export the database on the developer machine

In pgAdmin, right-click `corebank_lite` → **Backup**. Set the filename to `corebank_lite.sql`, **Format: Plain**, **Only schema: No**, **Only data: No**, **Include CREATE DATABASE: No**, and disable owner and privilege statements if available. Both schema and data will be exported.

Equivalent Windows command (adjust PostgreSQL version):

```bat
"C:\Program Files\PostgreSQL\17\bin\pg_dump.exe" ^
  -U postgres ^
  -d corebank_lite ^
  --format=plain ^
  --no-owner ^
  --no-privileges ^
  -f corebank_lite.sql
```

**Before publishing**, inspect and sanitize exported records: customer names, identification numbers, contact details, account/transaction data, `app_user` password hashes, API keys, tokens, and any other confidential data. Publish fictional demonstration records only. Never include real credentials or a production database. If you modify data, re-export and test the sanitized version.

Move the final reviewed script to `database/corebank_lite.sql` in the repository.

## 2. Import into a fresh PostgreSQL database

Create a login role named `corebank_app` with a unique strong password, and a new database `corebank_lite`. Import as the application database owner or ensure it has table, sequence, and schema permissions.

Example using Windows PowerShell (run from the repository root, assuming PostgreSQL tools are on PATH):

```powershell
createuser -U postgres -P corebank_app
createdb -U postgres -O corebank_app corebank_lite
psql -U corebank_app -d corebank_lite -v ON_ERROR_STOP=1 -f database/corebank_lite.sql
```

If the role exists, skip `createuser`. If the database exists, create a separate test database instead of overwriting it. For pgAdmin, open **Query Tool** on the empty database, load the plain SQL file and execute it; check the Messages tab for errors.

**Do not use pgAdmin Restore / `pg_restore` for this plain SQL export.**

## 3. Configure the backend

Set the `DB_PASSWORD` environment variable to the password assigned to `corebank_app` and `JWT_SECRET` to a new, sufficiently long random secret. Confirm the JDBC URL points to `jdbc:postgresql://localhost:5432/corebank_lite`. Do not commit secret values.

Start the Spring Boot backend and check schema validation. Then start the React frontend.

## 4. Verify import and demo login

```sql
SELECT current_database();
SELECT COUNT(*) FROM public.app_role;
SELECT COUNT(*) FROM public.app_user;
```

Also verify the core business tables, and test login with **demo-only credentials** provided through a safe bootstrap mechanism. Never reuse actual personal passwords. Test customer CRUD, financing workflows, and role restrictions.

## 5. Reproducibility check

Repeat the import into a fresh temporary database such as `corebank_test`, configure the backend to use it, and verify startup and the key workflows. Record any required extensions or PostgreSQL version constraints in this document.
