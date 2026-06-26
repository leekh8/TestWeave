# TestWeave — Test Case Management & PDF Reporting

> A Spring Boot web app to register API/UI test cases, run them, and generate HTML/PDF result reports.

## Why

Manual test runs scatter their results across consoles and spreadsheets. TestWeave centralizes
test cases (REST API checks and Selenium UI checks), executes them on demand, persists each run as a
result record with pass/fail status, timing, and error detail, and turns any run into a shareable
HTML or PDF report. It started as a learning project to practice Spring MVC, JPA, and report generation.

## Status

⚫ Dormant — last commit `2025-05-12` (*Refactor: Move Java backend code to dedicated directory*).
No activity for over a year. Treat as a prototype: the core flow works, but it is unpolished and unmaintained.

## Features

Implemented (verified in controllers/services):

- **Test case registration** — store API or UI test cases (`type`, `targetUrl`, `httpMethod`, `expectedRes`).
  Both a JSON REST endpoint (`POST /testcase`) and a Thymeleaf form (`/testweave/testcase/form`).
- **Test execution**
  - API tests via REST Assured: issues the request, compares the HTTP status code against the expected
    value, and extracts an error/message summary from the response.
  - UI tests via Selenium ChromeDriver: opens the target URL and verifies the page title is non-empty.
- **Result persistence** — each run is saved as a `TestResult` (PASS/FAIL, execution time in ms, timestamp,
  error message) linked to its `TestCase`.
- **Result listing & filtering** — `/testweave/testresult/list` with optional status filter and sort by
  execution time or date, plus aggregate statistics (total, pass, fail, pass rate, average time).
- **Report generation**
  - HTML report (`GET /report/{id}`) rendered inline, auto-redirecting back to the result list.
  - PDF report (`GET /report/pdf/{id}`) generated with iText 7 and served as a download.

Notes / rough edges:
- Reports are written to `src/main/resources/reports/` on disk (works locally; not container-friendly).
- ChromeDriver path is hard-coded to `src/main/resources/driver/chromedriver`, so UI tests require a
  local Chrome/driver setup.
- A `spring-boot-starter-mail` dependency is declared but no mail-sending feature is implemented.
- The repo contains a duplicated tree: source lives both at the root (`src/`) and under `backend/`
  (the result of the last "move to dedicated directory" refactor). They are identical; `backend/` is the
  intended home.

## Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.4.4 (Spring MVC, Spring Data JPA)
- **Build:** Gradle (wrapper included)
- **View:** Thymeleaf + thymeleaf-layout-dialect
- **Persistence:** Hibernate/JPA — H2 in-memory for local dev; PostgreSQL driver bundled for deployment
- **Test execution libs:** REST Assured 5.3.2 (API), Selenium 4.20.0 (UI)
- **PDF:** iText 7 (itext7-core 7.2.5)
- **Other:** Lombok, Spring Boot Mail starter (unused)
- **Deploy:** Docker (`eclipse-temurin:17-jdk`, builds and runs the jar), Render (`render.yml`, free web service)

## Run

```bash
# from the project root (or backend/)
./gradlew bootRun
```

App starts on `http://localhost:8080`.

- Main UI: `http://localhost:8080/testweave/testcase/form`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:testdb`, user `sa`)

Build a runnable jar:

```bash
./gradlew build
java -jar build/libs/*.jar
```

Container / cloud deploy uses the included `Dockerfile` (builds with Gradle then runs the jar) and
`render.yml` (Render web service, `env: docker`). The default datasource is in-memory H2; a PostgreSQL
driver is on the classpath for switching to a managed database via Spring datasource properties.

## Structure

```
backend/                              # intended project home (duplicate of root src/)
  build.gradle
  Dockerfile
  src/main/java/com/testweave/
    TestweaveApplication.java         # Spring Boot entry point
    controller/
      TestCaseController.java         # REST: register & run test cases
      TestCasePageController.java     # Thymeleaf pages: forms, lists, run+report flow
      ReportController.java           # REST: serve HTML / PDF reports
    service/
      TestCaseService.java            # execution logic (REST Assured / Selenium), stats
      ReportService.java              # HTML & PDF report generation (iText)
    domain/
      TestCase.java                   # JPA entity: a test definition
      TestResult.java                 # JPA entity: a single run result
    dto/
      TestStats.java                  # aggregate stats DTO
    repository/                       # Spring Data JPA repositories
  src/main/resources/
    application.properties            # H2 datasource, JPA, report file path
    templates/                        # Thymeleaf views (layout, forms, lists)
render.yml                            # Render deployment config
```

## Roadmap

- [ ] Collapse the duplicated root/`backend/` source trees into one canonical module.
- [ ] Replace the hard-coded ChromeDriver path with WebDriverManager / headless config so UI tests run in CI and containers.
- [ ] Persist reports to object storage (or stream them) instead of the local filesystem, for container/Render compatibility.
- [ ] Add scheduled/batch test runs and the e-mail delivery the mail starter was added for.
