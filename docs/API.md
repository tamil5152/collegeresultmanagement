# REST API reference

Base path: `/api`. All bodies are JSON. Interactive Swagger UI is available at `/swagger-ui/index.html` when the
backend runs with `SWAGGER_ENABLED=true` (it is on in the `dev` profile, off by default elsewhere).

## Conventions

**Success envelope** - every successful JSON response:

```json
{ "success": true, "message": "Department created", "data": { "...": "..." } }
```

**Paged lists** - `data` is `{ "items": [...], "page": 0, "size": 10, "totalElements": 42, "totalPages": 5 }`.
Query parameters: `page` (0-based), `size` (1-100), `search` (substring match), plus endpoint specific filters.

**Errors** - consistent shape, never a stack trace:

```json
{ "success": false, "status": 400, "code": "VALIDATION_ERROR", "message": "Validation failed",
  "fieldErrors": { "entries[0].internalMarks": "At most 2 decimal places" }, "timestamp": "2026-11-04T01:07:48Z" }
```

| Status | `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | invalid input or a business rule (e.g. marks above maximum) |
| 401 | `UNAUTHORIZED` | no / expired / tampered session, or bad credentials |
| 403 | `FORBIDDEN`, `CSRF_INVALID` | wrong role, not your assignment, or missing `X-CSRF-Token` |
| 404 | `NOT_FOUND` | missing item (also returned for another student's result) |
| 409 | `CONFLICT`, `DUPLICATE`, `DATA_INTEGRITY` | state conflict (locked marks, already published, duplicate) |
| 429 | `TOO_MANY_REQUESTS` | account temporarily locked after repeated failed logins |

## Authentication model

`POST /api/auth/login` sets an **HttpOnly** cookie `CRMS_TOKEN` holding a signed JWT (HS256, expiry
`JWT_EXPIRY_MINUTES`). The response body contains a random `csrfToken` (also embedded in the JWT). Every
**POST / PUT / PATCH / DELETE** must send it back as the `X-CSRF-Token` header; GET requests do not need it.
The JWT itself is never exposed to JavaScript. The user is re-read from the database on each request, so
deactivating an account or changing a role takes effect immediately.

```bash
# log in, keep the cookie in a jar, remember the CSRF token
CSRF=$(curl -s -c jar.txt -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Password@123"}' http://localhost:8080/api/auth/login \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["csrfToken"])')

curl -s -b jar.txt http://localhost:8080/api/admin/dashboard                       # GET: cookie only
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"code":"MEC","name":"Mechanical Engineering"}' http://localhost:8080/api/admin/departments   # POST: cookie + CSRF
```

Role areas are enforced on the server: `/api/admin/**` ADMIN only, `/api/faculty/**` FACULTY only,
`/api/student/**` STUDENT only, `/api/auth/**` any signed-in user. Any other path is denied.

## Auth (all roles)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | `{username, password}` -> `{user:{userId,username,fullName,role}, csrfToken}`; sets cookie |
| POST | `/api/auth/logout` | clears the cookie |
| GET | `/api/auth/me` | current user + `csrfToken` (used by the SPA on page load) |
| POST | `/api/auth/change-password` | `{currentPassword, newPassword}`; new password 8+ chars with a letter and digit |
| GET | `/api/health` | `{status, database}`; public, used by hosting health checks |

## Admin (`/api/admin`)

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | live counts |
| GET/POST/PUT | `/departments`, `/departments/{id}` | `{code, name}`; `PATCH /departments/{id}/active` `{active}` |
| GET/POST/PUT | `/academic-years`, `/academic-years/{id}` | `{label, startDate, endDate}`; `PATCH .../{id}/active` |
| GET/POST/PUT | `/semesters`, `/semesters/{id}` | `{academicYearId, semesterNumber(1-12), name, startDate, endDate}` |
| GET/POST/PUT | `/subjects`, `/subjects/{id}` | filters `departmentId`, `semesterNumber`; body: `departmentId, code, name, semesterNumber, credits, internalMax, externalMax, internalPass, externalPass, totalPass`; `PATCH .../{id}/active`. Max marks / pass criteria / credits are frozen once marks exist |
| GET/POST/PUT | `/examination-sessions`, `/{id}` | `{name, semesterId, startDate, endDate, status: OPEN\|CLOSED}`; faculty can enter marks only while OPEN |
| GET/POST/PUT | `/grading-rules`, `/grading-rules/{id}` | `{name, description, samplePlaceholder, includeFailedInGpa, bands:[{grade, minPercentage, gradePoint, failGrade}]}`; `POST /grading-rules/{id}/activate`. A rule used by published results is locked |
| GET/POST/PUT | `/students`, `/students/{id}` | filters `departmentId`, `semester`, `active` (1/0/-1); create needs `username, password`; update may include `password` to reset; `PATCH .../{id}/active` deactivates |
| GET/POST/PUT | `/faculty`, `/faculty/{id}` | same pattern; `employeeCode`, `designation`, `phone` |
| GET/POST/PUT/DELETE | `/assignments`, `/assignments/{id}` | `{subjectId, facultyId, semesterId}` (PUT: `{facultyId}`); DELETE blocked once marks exist |
| GET/POST/DELETE | `/enrollments`, `/enrollments/{id}` | `{studentId, subjectId, semesterId}`; `POST /enrollments/bulk` `{semesterId, departmentId}` |
| GET | `/marks` | filters `sessionId, departmentId, subjectId, status, search`; the review queue |
| POST | `/marks/review` | `{markIds:[...], action: APPROVE\|REJECT, remarks}`; only `SUBMITTED` marks; remarks required for REJECT; all-or-nothing |
| POST | `/marks/{id}/correct` | `{internalMarks, externalMarks, reason(10-500 chars)}` - the only way to change APPROVED/PUBLISHED marks; audited, recalculates and re-versions a published result |
| GET | `/results/publication-status?sessionId=&departmentId=` | readiness check (counts + blocking students) |
| POST | `/results/publish` | `{sessionId, departmentId}` - ADMIN only; atomic; rejects missing/unapproved marks |
| GET | `/results` | published results, filters `sessionId, departmentId, search` |
| GET | `/reports/department-summary?sessionId=` | pass %, average SGPA per department |
| GET | `/reports/subject-analysis?sessionId=&departmentId=` | appeared / passed / averages per subject |
| GET | `/reports/grade-distribution?sessionId=&departmentId=` | count per grade |
| GET | `/reports/marks-status?sessionId=` | marks per workflow status per department |
| GET | `/audit-logs` | filters `action`, `search` |
| GET | `/options/{departments,academic-years,semesters,subjects,faculty,students,examination-sessions}` | `[{id,label,meta}]` for drop-downs |

```bash
# review and publish
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"markIds":[64,65],"action":"APPROVE"}' http://localhost:8080/api/admin/marks/review
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"sessionId":1,"departmentId":1}' http://localhost:8080/api/admin/results/publish
# audited correction of a published mark
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"internalMarks":35,"externalMarks":41,"reason":"Re-evaluation outcome ref 2024/77"}' \
  http://localhost:8080/api/admin/marks/1/correct
```

## Faculty (`/api/faculty`)

A faculty member can only touch assignments that belong to them (otherwise `403`).

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | counts per workflow state |
| GET | `/assignments` | own subjects, each with the examination sessions of its semester |
| GET | `/assignments/{assignmentId}/students` | enrolled students |
| GET | `/assignments/{assignmentId}/sessions/{sessionId}/marks` | mark sheet incl. maximums, per-row `status`, `editable`, `reviewRemarks` |
| PUT | `/assignments/{assignmentId}/sessions/{sessionId}/marks` | save draft: `{entries:[{studentId, internalMarks, externalMarks}]}` (nulls allowed). Max 2 decimals, `0..max`; rejected if the session is closed or a row is SUBMITTED/APPROVED/PUBLISHED |
| POST | `/assignments/{assignmentId}/sessions/{sessionId}/submit` | needs both marks for every enrolled student; moves DRAFT/REJECTED to SUBMITTED |
| GET | `/submissions` | per subject/session counts: not entered, draft, submitted, approved, rejected, published |

```bash
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' -X PUT \
  -d '{"entries":[{"studentId":1,"internalMarks":30,"externalMarks":50.5}]}' \
  http://localhost:8080/api/faculty/assignments/9/sessions/3/marks
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -X POST \
  http://localhost:8080/api/faculty/assignments/9/sessions/3/submit
```

## Student (`/api/student`)

No identifier of the student is ever accepted from the client; the student is the authenticated user.

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | latest SGPA, CGPA, credits, count of published results |
| GET | `/profile` | own profile |
| GET | `/results` | own **published** results, latest first, each with SGPA and cumulative CGPA |
| GET | `/results/{resultId}` | subject-wise marks, grades, credits, SGPA, CGPA; `404` if the result is not yours |
| GET | `/results/{resultId}/marksheet` | `application/pdf` attachment |

```bash
curl -s -b jar.txt http://localhost:8080/api/student/results
curl -s -b jar.txt -o marksheet.pdf http://localhost:8080/api/student/results/1/marksheet
```
