# CBMS submission verification

Do not mark a test passed unless you actually ran it.

## Database and backend

- [ ] Plain SQL schema + demo data imports into a fresh PostgreSQL database without errors
- [ ] `corebank_app` can connect and access tables/sequences
- [ ] Backend starts with `DB_PASSWORD` and `JWT_SECRET` set
- [ ] `mvn test` passes
- [ ] No private/customer data in the database SQL script being published

## Authentication and authorization

- [ ] Valid credentials login successfully
- [ ] Invalid credentials are rejected
- [ ] VIEWER cannot access privileged mutation APIs (test direct HTTP request, not just hidden UI buttons)
- [ ] MAKER can use authorized workflows, but not ADMIN user/log administration
- [ ] ADMIN can create users, change roles, disable accounts, reset passwords
- [ ] Disabled account cannot authenticate
- [ ] Registration requirement resolved: public registration or documented admin-controlled registration accepted by evaluator

## Core workflows

- [ ] Create, view, edit and search a test customer
- [ ] Create and view an account for that customer
- [ ] Post and inquire about a permitted test transaction
- [ ] Create financing application, review/approve as permitted
- [ ] Create financing facility, generate schedule before disbursement
- [ ] Disburse, repay and verify balance/status transitions
- [ ] Product → scheme → rate maintenance works with effective dates
- [ ] Dashboard, logging and audit screens load without errors
- [ ] `npm run lint` and `npm run build` pass

## GitHub repository

- [ ] `.gitignore` excludes `.env`, `node_modules`, `target`, `dist`, logs and private uploads
- [ ] `git ls-files` does not include credentials, private documents, unsanitized database data, or build artifacts
- [ ] Scan committed history, not just current working tree, for secrets
- [ ] Remove/rotate any secret that has ever been committed
- [ ] README and database instructions match the published files
- [ ] Include only sanitized, fictional demo data in `database/corebank_lite.sql`
- [ ] Repository is public and accessible in an incognito browser
- [ ] Git history contains meaningful development commits (do not fabricate history)
- [ ] Screenshots are sanitized and committed

## Email

- [ ] Public repository URL verified
- [ ] Employment form completed and attached
- [ ] Latest resume attached
- [ ] Submission email reviewed and sent to bill.hong@neurogine.com
