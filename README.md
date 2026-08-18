# BacSurv

Surveillance scheduling for Moroccan baccalaureate exams: given a center's
rooms, exam slots and teacher pool, it produces a legal, fair duty schedule
(surveillance, réserve, permanence) using a constraint solver.

The rules it encodes are written down in [MODEL.md](MODEL.md); the input file
format is described in [samples/INPUT-FORMAT.md](samples/INPUT-FORMAT.md).

## Running the web application

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080>: import an operation JSON file, launch a
solve, watch it finish, read the schedule per slot and the workload per
teacher. Data is stored in a local H2 file under `./data`.

The interface exists in the two administrative languages of Morocco, French
(default) and Arabic, switchable from the header or with `?lang=fr` / `?lang=ar`;
the choice is remembered in a cookie. Arabic pages are laid out right to left.
User-facing text lives in `src/main/resources/messages*.properties` — never
hardcode it in a template, and add both languages when adding a page.

## Running from the command line

```bash
mvn -DskipTests package
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" ma.bacsurv.cli.SolveMain \
     samples/operation-sample.json --seconds 30
```

Exit codes: `0` feasible, `1` infeasible, `2` bad input.

## HTTP API

The browser pages are a skin over this API — any other front end can use it.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/operations` | list imported operation files |
| `POST` | `/api/operations` | import a file (multipart `file`, or a JSON body) |
| `POST` | `/api/operations/{id}/solve?seconds=30` | queue a solve, returns a job |
| `GET` | `/api/jobs` / `/api/jobs/{id}` | job status |
| `GET` | `/api/jobs/{id}/schedule` | the solved schedule (409 while running) |

Solving runs on a background pool, so a request never waits for the solver.

## Layout

```
domain/       exams, slots, rooms, teachers, duties — no framework, no I/O
rules/        eligibility and subject-conflict configuration
application/  duty generation, validation, greedy baseline
solver/       Timefold adapter: constraints + solve, swappable engine
io/           input parsing and schedule serialisation
cli/          command line entry point
web/          Spring Boot: persistence, service, REST API, pages
demo/         runnable demonstrations with generated data
```

The domain, rules and solver packages know nothing about the web or the
database; the web layer calls services and never reaches into the solver.

## Database

Development uses H2 in PostgreSQL mode; the schema is owned by Flyway
migrations (`src/main/resources/db/migration`), never by Hibernate. Moving to
a real PostgreSQL server is a datasource URL change plus the driver — no code
or schema rewrite.

## Tests

```bash
mvn test
```

Covers duty generation, validation rules, input parsing, a full
input-file-to-feasible-schedule run, and the web flow (import → solve → poll →
schedule) against the real solver.
