# job-scheduler
A small cron-style job scheduler with a REST API: register jobs on a cron schedule, execute them, and query run history (status, duration, timestamps).

## Tech Stack

- Java 17
- Spring Boot 4.1 (Web, Data JPA, Validation, Scheduling)
- H2 in-memory database (no external DB install required)
- Lombok
- JUnit 5 + Mockito + MockMvc

## Prerequisites

- JDK 17+
- No Maven install needed — the project ships the Maven Wrapper (`mvnw` / `mvnw.cmd`)
- No database install needed — it uses an in-memory H2 database that's created fresh on every startup

## Architecture

The application consists of:

- Job Management – CRUD APIs for managing scheduled jobs.
- Dynamic Scheduler – Registers, updates and removes scheduled tasks at runtime using Spring's TaskScheduler.
- Execution History – Records every execution with timestamps, duration and status.
- REST API – Exposes job management and execution history endpoints.

## 1. Install / Build

From the `jobscheduler` project directory:

```bash
# Windows
mvnw.cmd clean install

# macOS / Linux
./mvnw clean install
```

This compiles the code, runs the test suite, and packages the app into `target/jobscheduler-0.0.1-SNAPSHOT.jar`.

To build without running tests:

```bash
mvnw.cmd clean install -DskipTests
```

## 2. Run the Application

Pick one:

```bash
# Option A: via the Maven wrapper (recommended for development)
mvnw.cmd spring-boot:run

# Option B: run the packaged jar after `mvn install`
java -jar target/jobscheduler-0.0.1-SNAPSHOT.jar

# Option C: run/debug JobschedulerApplication.java directly from your IDE
```

Once started, the app is listening on **http://localhost:8080**. You'll see a log line like:

```
Started JobschedulerApplication in X seconds
```

Data lives only in memory — every restart starts from an empty database.

### Inspecting the database

The H2 web console is enabled at **http://localhost:8080/h2-console**. Use these connection settings (they match `application.yml`):

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:jobscheduler` |
| User Name | `sa` |
| Password | *(leave blank)* |

## 3. Run the Tests

```bash
mvnw.cmd test
```

This runs the full suite (service layer, scheduler engine, and REST controllers via `MockMvc`) — no running app or database needed, everything is mocked/in-process.

## 4. Hitting the API

With the app running (`mvnw.cmd spring-boot:run`), use `curl`, Postman, or any HTTP client against `http://localhost:8080`.

> **Windows PowerShell note:** PowerShell aliases `curl` to `Invoke-WebRequest`, which doesn't understand the flags below. Either run these from Git Bash / WSL, or call `curl.exe` explicitly in PowerShell.

### Job endpoints (`/api/jobs`)

| Method | Path | Description |
|---|---|---|
| POST | `/api/jobs` | Create a job |
| GET | `/api/jobs` | List all jobs |
| GET | `/api/jobs/{id}` | Get a job by id |
| PUT | `/api/jobs/{id}` | Update a job |
| DELETE | `/api/jobs/{id}` | Delete a job |

**Create a job** (cron expressions are Spring's 6-field format: `second minute hour day month weekday`):

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Every Minute Demo Job",
        "cronExpression": "0 * * * * *",
        "status": "ACTIVE"
      }'
```

Response (`201 Created`):

```json
{
  "id": 1,
  "name": "Every Minute Demo Job",
  "cronExpression": "0 * * * * *",
  "status": "ACTIVE",
  "createdAt": "2026-07-20T13:00:00",
  "updatedAt": "2026-07-20T13:00:00"
}
```

`status` is optional on create and defaults to `ACTIVE`. Watch the app logs — once a job is `ACTIVE`, it fires on its own schedule (no extra call needed) and you'll see lines like:

```
Scheduled job 'Every Minute Demo Job' (id=1) with cron '0 * * * * *'
Job 'Every Minute Demo Job' (id=1) execution started (executionId=1)
Job 'Every Minute Demo Job' (id=1) execution SUCCESS (executionId=1)
```

**List all jobs:**

```bash
curl http://localhost:8080/api/jobs
```

**Get a job by id:**

```bash
curl http://localhost:8080/api/jobs/1
```

**Update a job** (e.g. deactivate it so it stops firing):

```bash
curl -X PUT http://localhost:8080/api/jobs/1 \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Every Minute Demo Job",
        "cronExpression": "0 * * * * *",
        "status": "INACTIVE"
      }'
```

**Delete a job:**

```bash
curl -X DELETE http://localhost:8080/api/jobs/1
```

### Execution history endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/jobs/{jobId}/executions` | History for one job (most recent first) |
| GET | `/api/executions/{id}` | A single execution record |
| GET | `/api/executions` | Full execution history across all jobs |

```bash
# History for job 1
curl http://localhost:8080/api/jobs/1/executions

# A single execution record
curl http://localhost:8080/api/executions/1

# Everything
curl http://localhost:8080/api/executions
```

### Error responses

Invalid input (blank name, bad cron expression) returns `400` with field errors; missing jobs/executions return `404`:

```json
{
  "timestamp": "2026-07-20T13:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Job not found with id: 99",
  "fieldErrors": null
}
```
