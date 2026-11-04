# College Result Management System

A simple, professional examination-result system for **one college** with three roles - **Admin**, **Faculty**,
**Student**. Faculty enter marks, the admin reviews/approves and **publishes** results, students see only their own
published results and download a PDF mark sheet. Everything is backed by a real MySQL database; there are no mock APIs.

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite, React Router, Axios, React Hook Form + Zod, Lucide, Recharts (reports only), custom CSS |
| Backend | Java 21, Spring Boot 3.5, Spring Web, **Spring JDBC (`JdbcTemplate`, no JPA/Hibernate)**, Spring Security, Bean Validation, Maven, jjwt, OpenPDF, springdoc (Swagger) |
| Database | MySQL 8 (`database/schema.sql`) |
| Tests | JUnit 5, Mockito, Spring MockMvc against a real MySQL-compatible database |

```
college-result-management/
  frontend/            React app (Vite)             -> deploy on Vercel
  backend/             Spring Boot app (Maven)      -> deploy on Render / Railway (Dockerfile included)
  database/schema.sql  tables, keys, constraints    -> run on every environment
  database/seed.sql    DEVELOPMENT sample data only -> never load in production
  docs/API.md          REST reference with curl examples
  docker-compose.yml   local MySQL 8 (dev only)
  render.yaml          Render blueprint for the backend
  .github/workflows/   CI (backend tests on MySQL 8 + frontend build)
```

## 1. Features

- **Admin** - dashboard with live database counts; create/edit/deactivate students and faculty; manage departments,
  academic years, semesters, subjects, examination sessions, grading rules; assign faculty to subjects; enroll
  students; review marks (approve/reject); **publish results (admin only)**; audited **corrections**; department,
  subject, grade-distribution and submission-progress reports; audit log.
- **Faculty** - sees only assigned subjects and their enrolled students; enters internal/external marks (validated
  against the subject maximums), saves drafts, submits for approval, sees status and reviewer remarks; edits only while
  permitted (DRAFT/REJECTED and session OPEN).
- **Student** - own profile; only **published** semester results; subject-wise marks, grades, credits, SGPA and CGPA;
  printable PDF mark sheet.

## 2. Design decisions (and why)

### Authentication: JWT in an HttpOnly cookie + CSRF token
- The signed JWT (HS256, expiry enforced, secret from `JWT_SECRET`, minimum 32 bytes enforced at startup) lives in an
  **HttpOnly, Secure, SameSite** cookie, so JavaScript - including any injected script - can never read it. This is
  why nothing sensitive sits in `localStorage` or in the React bundle.
- Cookies need CSRF protection. Every state-changing request must carry an `X-CSRF-Token` header whose value is a random
  claim inside the JWT. The SPA receives it from `/auth/login` or `/auth/me` and keeps it in memory only. Because it is
  stateless this also works across origins.
- The user row is re-read on every request: deactivating an account (or changing its role) invalidates its token
  immediately, which a plain stateless JWT cannot do on its own.
- *Why not server sessions?* They would work too, but need a shared session store as soon as the backend runs more than
  one instance or restarts on free hosting; a signed stateless token avoids that.
- Brute-force protection: 5 failed logins lock a username for 15 minutes (in memory, per instance); login errors are
  generic so usernames are not revealed; passwords are BCrypt hashes.

### Authorization
Role areas are enforced by the server (`/api/admin/**`, `/api/faculty/**`, `/api/student/**`), not by React.
Faculty ownership is checked in the service layer on every marks call. Student endpoints never accept a student id: the
student is derived from the verified token, and a result id that is not theirs returns `404`.

### Marks life cycle
`DRAFT -> SUBMITTED -> APPROVED | REJECTED` (a rejected mark returns to the faculty, is edited, and goes back to
`DRAFT`) `-> PUBLISHED`. A unique key `(student, subject, examination session)` makes duplicate marks impossible at the
database level. Submission, review and publication are single transactions and each writes `audit_logs` rows.

### Publication and corrections
Publishing is **admin only**, per *(examination session, department)*, and **all-or-nothing**: it is refused unless
every enrolled subject of every active student has complete, `APPROVED` marks, and an active grading rule exists.
Results are stored as a snapshot (`results` + `result_subjects` copy code, name, credits, marks, grade), so later edits
to master data cannot rewrite history. Published marks cannot be edited by faculty. The only way to change an approved
or published mark is the admin **correction workflow**: a reason is mandatory, old/new values are kept in
`mark_corrections` and `audit_logs`, a published result is recalculated with the rule it was published under and its
`revision` number increases (shown to the student).

### Configurable result rules (nothing is hard-coded)
- Per subject: credits, internal/external maximum, **internal / external / total pass marks**.
- Grading rules (admin screen *Grading Rules*): percentage -> grade -> grade point bands, which grade is the fail grade,
  and whether failed credits count in the GPA denominator. Exactly one rule is active; results remember the rule used;
  a rule used by published results is locked (create a new one instead).
- **The seeded rule is a sample placeholder, clearly labelled as such in the UI and on the PDF. It is not an official
  regulation - replace it before real use.**
- Calculation (`ResultCalculator`, pure and unit tested, `BigDecimal` only): a subject passes only if internal, external
  *and* total minimums are met; percentage = total / maximum x 100 (HALF_UP, 2 dp); grade = highest non-fail band whose
  minimum is reached (a subject meeting its pass criteria but below every band gets the lowest passing band);
  `GPA = sum(grade point x credits) / credits` rounded HALF_UP to 2 dp. **CGPA** is computed at read time from published
  results only, counting the most recent attempt of each subject, so a correction or re-attempt flows through.

### Defaults chosen where the brief was ambiguous
- A *semester* is an instance (semester number N in academic year Y); subjects carry the curriculum semester number and
  must match when assigned/enrolled. One faculty member per subject per semester instance.
- Enrollment is explicit (admin screen *Enrollments*, with bulk enrollment by department + semester number).
- Absent/withheld students and grace marks are not modelled; both can be added as grading/mark rules later.
- Single-college deployment: no multi-tenancy.

## 3. Database

17 tables: `users` (role enum), `departments`, `academic_years`, `semesters`, `subjects`, `examination_sessions`,
`students`, `faculty`, `subject_assignments`, `student_enrollments`, `marks`, `grading_rules`, `grading_rule_bands`,
`results`, `result_subjects`, `mark_corrections`, `audit_logs`. All have primary keys, foreign keys, unique constraints,
indexes, `CHECK` constraints for ranges, and `created_at` / `updated_at`.

- `database/schema.sql` never drops anything - run it on an **empty** database.
- `database/seed.sql` is **development data only** (fictitious people, `example.test` e-mails, shared password
  `Password@123`, sample grading rule). Do not load it in production. For production create the first admin with
  `BOOTSTRAP_ADMIN_*` (section 6.2) and configure everything else in the UI.
- Time values are UTC; the connection pool sets `time_zone = '+00:00'`.

## 4. Run it locally

Prerequisites: JDK 21, Maven 3.9+, Node 22+, and MySQL 8 (or Docker).

**4.1 Database** - either with Docker

```bash
docker compose up -d db          # creates crms_dev (schema + dev seed) and an empty crms_test for the tests
```

or with your own MySQL 8:

```bash
mysql -u root -p -e "CREATE DATABASE crms_dev CHARACTER SET utf8mb4; CREATE DATABASE crms_test CHARACTER SET utf8mb4; \
  CREATE USER 'crms'@'%' IDENTIFIED BY 'crms_dev_pw'; GRANT ALL ON crms_dev.* TO 'crms'@'%'; GRANT ALL ON crms_test.* TO 'crms'@'%';"
mysql -u crms -pcrms_dev_pw crms_dev < database/schema.sql
mysql -u crms -pcrms_dev_pw crms_dev < database/seed.sql
```

**4.2 Backend** (port 8080) - the `dev` profile supplies local defaults and enables Swagger:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
# health: http://localhost:8080/api/health        Swagger: http://localhost:8080/swagger-ui/index.html
```

**4.3 Frontend** (port 5173) - Vite proxies `/api` to the backend, so the cookie stays first-party:

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

### Development test accounts (seed data only)

All passwords: **`Password@123`**

| Role | Username(s) | Notes |
|---|---|---|
| Admin | `admin` | |
| Faculty | `fac.cse1` (Dr. Anita Rao), `fac.cse2` (Prof. Ravi Kumar), `fac.ece1` (Dr. Meena Iyer) | |
| Student | `cs2024001` ... `cs2024006` (CSE), `ec2024001` ... `ec2024004` (ECE) | |

### Try the whole flow (about 3 minutes)
1. **Admin -> Results Publication**: choose *End Semester Examination - Dec 2024* + *CSE* -> **Publish**. Repeat for
   *May 2025*. (Semester 1 and 2 marks are seeded as already approved.)
2. **Student** `cs2024001`: dashboard shows SGPA/CGPA; *Semester Results* -> details -> **Download mark sheet**.
3. **Faculty** `fac.cse1` -> *Marks Entry*: CS303, session *Dec 2025*; enter marks (try 41 for internal: blocked),
   **Save draft**, **Submit for approval**.
4. **Admin -> Marks Review**: filter *Submitted*, approve (or reject with remarks and watch it return to the faculty).
   Semester 3 cannot be published until every subject of every student is approved - the page tells you what blocks it.
5. **Admin -> Marks Review -> Correct** on a published mark to see the audited correction and the revision on the result.

## 5. Configuration reference

### Backend environment variables
| Variable | Required | Purpose |
|---|---|---|
| `DB_URL` | yes | JDBC URL, e.g. `jdbc:mysql://host:3306/crms?sslMode=VERIFY_IDENTITY` |
| `DB_USERNAME`, `DB_PASSWORD` | yes | database credentials (least privilege: DML on the app schema) |
| `JWT_SECRET` | yes | signing secret, **at least 32 bytes** (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | yes | comma separated **exact** frontend origin(s), e.g. `https://your-app.vercel.app`. Also required when `/api` is proxied, because the browser still sends an `Origin` header; a mismatch returns 403 |
| `PORT` | no (8080) | injected by Render/Railway |
| `COOKIE_SECURE` | no (`true`) | keep `true` on HTTPS |
| `COOKIE_SAMESITE` | no (`Lax`) | `Lax` when `/api` is proxied by the frontend host; `None` (with `COOKIE_SECURE=true`) for direct cross-site calls |
| `JWT_EXPIRY_MINUTES` | no (60) | session lifetime |
| `DB_POOL_SIZE` | no (10) | HikariCP maximum pool size - keep below your provider's connection limit |
| `COLLEGE_NAME` | no | printed on mark sheets |
| `SWAGGER_ENABLED` | no (`false`) | expose API docs |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` (`_EMAIL`, `_NAME`) | first deploy | creates the first admin only if none exists; remove afterwards |
| `SPRING_PROFILES_ACTIVE` | dev only | `dev` enables local defaults - **never set in production** |

Production start-up fails fast (with a clear message) if `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` or a valid `JWT_SECRET`
is missing. A template is in `backend/.env.example`.

### Frontend environment variables
Only non-secret values may use `VITE_`; they are public in the bundle.

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `/api` | API base. Leave unset to call the relative `/api` (Vite proxy in dev, Vercel rewrite in prod). Set an absolute URL only for direct cross-origin calls. |
| `VITE_DEV_API_TARGET` | `http://localhost:8080` | dev proxy target (read by `vite.config.js`) |

## 6. Testing

```bash
cd backend && mvn test            # 96 tests: unit + integration
cd frontend && npm run build
```

- **Unit** - `ResultCalculatorTest` (boundaries, each pass criterion, rounding, GPA/CGPA, re-attempts),
  `JwtAndLoginSecurityTest` (tamper/expiry/secret/lockout), `FacultyMarksServiceTest` (Mockito: max marks, ownership,
  closed session, locked rows, completeness).
- **Integration** (real database, wiped and reloaded from `schema.sql` + `seed.sql` before each test) - repositories and
  constraints; authentication, CSRF, cookie flags, deactivation; **role matrix**; marks validation and workflow;
  **publication gating, calculations, corrections and audit**; **student data isolation**; admin CRUD; reports.
- Integration tests need a MySQL-compatible database whose name ends in **`_test`** (it is wiped!). Configure with
  `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` (defaults: `jdbc:mysql://localhost:3306/crms_test`, `crms`,
  `crms_dev_pw` - exactly what `docker-compose.yml` creates). If no database is reachable they are **skipped with a
  message**; with `REQUIRE_DB_TESTS=true` (set in CI) they fail instead.
- GitHub Actions (`.github/workflows/ci.yml`) runs the backend suite against a `mysql:8.0` service and builds the frontend.

## 7. Deployment

You need three things: a managed **MySQL 8** database, the **backend** (Render or Railway), and the **frontend**
(Vercel). Do them in this order.

> **Free tiers.** Free plans on any provider can sleep when idle (the first request after a pause can take a minute),
> have CPU/RAM/connection/storage limits, and may change or end. Nothing here promises a provider is or stays free -
> check current terms, and use paid plans for anything that matters.

### 7.1 Database (managed MySQL 8)
1. Create a MySQL 8 instance with a provider that offers **automated backups and point-in-time recovery** (for example
   AWS RDS/Aurora MySQL, Google Cloud SQL, Azure Database for MySQL, DigitalOcean/Aiven managed MySQL, or Railway MySQL
   for small deployments). It must support InnoDB foreign keys and `CHECK` constraints (MySQL 8.0.16+). Pick a region
   close to the backend.
2. Create the database and a dedicated application user (not root):
   ```sql
   CREATE DATABASE crms CHARACTER SET utf8mb4;
   CREATE USER 'crms_app'@'%' IDENTIFIED BY '<strong password>';
   GRANT SELECT, INSERT, UPDATE, DELETE ON crms.* TO 'crms_app'@'%';
   ```
   Run the schema once with an administrative user (the app user does not need DDL rights):
   ```bash
   mysql -h <host> -P <port> -u <admin-user> -p --ssl-mode=VERIFY_IDENTITY crms < database/schema.sql
   ```
   **Do not run `seed.sql`.**
3. **SSL**: use TLS to the database. Put it in the JDBC URL: `?sslMode=VERIFY_IDENTITY` (verifies certificate and
   host; the provider's CA must be trusted by the JVM - add it to a truststore if the provider uses a private CA) or
   at least `?sslMode=REQUIRED`. Never use `useSSL=false` outside local development.
4. **Pooling**: HikariCP is built in (`DB_POOL_SIZE`, default 10, `max-lifetime` 25 min). Keep
   pool size x instances below the provider's connection limit (small plans are often 20-60).
5. **Backups**: enable automated daily backups with a retention you can live with, test a restore at least once, and
   take a manual snapshot before every schema change. Exam results are records you cannot recreate.

*Schema changes later:* there is no migration tool in this version; apply additive `ALTER TABLE` scripts manually
(and keep them in the repo under `database/`). Consider Flyway/Liquibase if the schema starts to change often.

### 7.2 Backend on Render (Docker)
1. Push this repository to GitHub. In Render choose **New -> Blueprint** and select the repo (it reads `render.yaml`),
   or **New -> Web Service**: runtime *Docker*, root directory `backend`, Dockerfile `./Dockerfile`, health check path
   `/api/health`.
2. Set the environment variables from section 5: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (the blueprint
   generates one), `CORS_ALLOWED_ORIGINS` (your Vercel URL - you can fill it in after step 7.3), `COOKIE_SECURE=true`,
   `COOKIE_SAMESITE=Lax`, `COLLEGE_NAME`, and for the first start `BOOTSTRAP_ADMIN_USERNAME` /
   `BOOTSTRAP_ADMIN_PASSWORD`.
3. Deploy, then open `https://<service>.onrender.com/api/health` -> `{"status":"UP","database":"UP"}`.
4. Sign in once, then **delete the `BOOTSTRAP_ADMIN_*` variables** and change the password under the key icon.

### 7.2 (alternative) Backend on Railway
New project -> *Deploy from GitHub repo* -> set the service **root directory to `backend`** (it builds the Dockerfile) ->
add the same variables as above (Railway provides `PORT`) -> generate a public domain -> check `/api/health`.
If you use Railway's MySQL plugin, build `DB_URL` from its host/port/database variables and keep TLS settings per
Railway's documentation.

### 7.3 Frontend on Vercel
1. **Add New Project**, import the repo, set **Root Directory** to `frontend` (framework: Vite; build `npm run build`;
   output `dist`).
2. Open `frontend/vercel.json` and replace `REPLACE-WITH-YOUR-BACKEND-HOST` with your backend host
   (e.g. `crms-backend.onrender.com`), commit and redeploy. The rewrite sends `/api/*` to the backend, so the browser sees
   a single origin and the HttpOnly cookie is first-party (`SameSite=Lax`, no third-party-cookie problems).
3. Set `CORS_ALLOWED_ORIGINS` on the backend to the exact Vercel origin (`https://<project>.vercel.app` and any custom
   domain) and redeploy/restart it.
4. Visit the site, sign in, and run the checklist below.

*Direct mode (no rewrite)*: set `VITE_API_BASE_URL=https://<backend>/api` on Vercel and on the backend
`COOKIE_SAMESITE=None`, `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS=<frontend origin>`. Browsers that block third-party
cookies will then break login, which is why the rewrite is the recommended setup.

### 7.4 After deploying
- [ ] `/api/health` is UP and HTTPS is used everywhere (Render/Vercel terminate TLS; HSTS is sent by Spring over HTTPS).
- [ ] You can log in, refresh the page and stay logged in; logout works.
- [ ] `BOOTSTRAP_ADMIN_*` removed, admin password changed, `SWAGGER_ENABLED` is not `true`.
- [ ] Create departments, years, semesters, subjects, **your real grading rule (and activate it)**, faculty, students,
      assignments, enrollments, examination session.
- [ ] Database backups verified.

## 8. Security summary
BCrypt password hashes; HttpOnly + Secure + SameSite cookie; CSRF header check; server-side role and ownership checks;
bean validation on every request body; parameterised SQL only (search wildcards escaped, sort fields are fixed);
CORS limited to configured origins; secrets only in environment variables (none in the frontend bundle); `server.error`
details disabled; security headers (`X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`); audit log for
logins, account changes, marks creation/modification/submission/approval/rejection/correction, publication and rule
changes; deactivated accounts lose access immediately.

## 9. Known limitations
- Login throttling is in memory (per instance). Use a shared store or a WAF if you run several instances.
- The PDF uses the built-in Helvetica font: names with non-Latin scripts will not render; embed a Unicode font if needed.
- No e-mail/password-reset flow: the admin resets passwords. No parent portal, attendance, online exams, payments (out of scope).
- Marks entry does not model absent/withheld students or grace marks.
- Schema migrations are manual SQL (section 7.1).
- The Dockerfile, `render.yaml` and the GitHub workflow were written to standard practice but could not be executed in
  the authoring sandbox (no Docker daemon / GitHub runner); the Maven build, tests and frontend were run there. The
  integration tests ran against **MariaDB 10.11** with MySQL 8's default strict `sql_mode`; CI runs them on real MySQL 8.

API reference: [`docs/API.md`](docs/API.md) - Swagger UI when `SWAGGER_ENABLED=true`.
