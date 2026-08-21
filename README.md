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

## Importing the teacher pool from a spreadsheet

**Centres → open a centre → import**. The file is what an administration
exports: comma, semicolon or tab separated, UTF-8, headers in French, Arabic or
English (`matricule` / `رقم التأجير` / `N° Matricule` all match, accents and
spacing ignored). Only matricule, name and subject are required;
establishment and gender are optional. See `samples/teachers-sample.csv`.

Nothing is written until it is confirmed: the import first shows which teachers
would be added, which would change and how, which are already correct, and
which rows could not be read — with the line number and the reason. A bad row
is skipped, never the whole file, and re-importing the same file changes
nothing.

## Changing a schedule by hand

The solver proposes; the administrator decides. Each line of a schedule has a
**Modifier** link: choose someone else, and BacSurv answers before saving
anything with what the change would break — the teacher is already on duty in
that slot, is absent that day, is not a specialist of the subject they would
cover as permanence, or would be surveilling their own subject. Those reasons
are returned as codes and rendered in French or Arabic like every other string.

A legal change also reports its cost: how many duties the new holder ends up
with, and which preferences (repeated room, repeated pair, consecutive days)
get better or worse.

An assignment can be **pinned** from the same page, and a pinned duty keeps its
teacher through the next solve while the rest of the schedule is rebuilt around
it — the pin itself survives too.

A change that breaks a rule is refused unless it is explicitly forced. Forced
or not, the schedule keeps telling the truth: the validation summary at the top
of the job page counts the violation.

Through the API: `POST /api/jobs/{id}/assignments/{dutyId}/review` to ask,
`POST /api/jobs/{id}/assignments/{dutyId}` to save (409 with the reasons when it
breaks a rule, `force=true` to insist), and `.../pin` to protect it.

## Surveillance is the work, réserve and permanence are turns

Standing in a room for a whole épreuve is not the same day as being on
standby, even though all three count as one duty and are paid the same. So
BacSurv balances two things instead of one: surveillance is spread evenly,
and réserve and permanence share a **single queue** — different pools, since
permanence needs a specialist of the subject, but one counter. A teacher who
has had either waits until every colleague has had a turn; then the cycle
opens again.

At the size of a real centre this comes out exactly: 192 privileges over 150
teachers gives one turn to all 150 and a second to 42 of them, nobody at
zero. Where scarcity forces a repeat — four philosophie permanences and two
specialists — the solver takes it, because the rule is a strong preference
and not a hard bar.

**The queue survives between sessions.** Each session is solved on its own —
rattrapage cannot be sized in advance, so nothing is planned ahead — but it
starts where the earlier sessions left the queue: a teacher carries how many
turns they have had beyond the colleague who has had the fewest, almost
always 0 or 1. A completed round lifts everyone together and the number falls
back to zero, so it cannot accumulate; session sizes never enter, since what
is counted is turns per teacher.

All past sessions count, not just the last. A session repays the people it
owes only as far as its turns reach — 29 waiting and 18 turns to give leaves
11 still waiting — and reading the newest session alone could not tell those
11 from the ones already served, so the rattrapage would hand turns to people
who had one in June while others finished the year with none. The position is
recomputed from stored assignments rather than kept as a running counter,
which is what stops it drifting.

## Cumulative fairness across operations

Importing an operation stores the center, its rooms and its teacher pool, not
just the file. When a second operation of the same center is solved, each
teacher's duties from the earlier operations are read back from the database
and used as their starting load, so the year's total stays balanced without
anyone maintaining a `prior` block by hand. Teachers are matched by matricule
within their center.

## Behaviour at the size of a real center

`ma.bacsurv.demo.ScaleDemoMain [seconds] [teachers] [days] [rooms]` generates a
center of a given size and reports what the solver achieved. Measured results:

| Center | Duties | Teachers | Time | Result |
| --- | --- | --- | --- | --- |
| 6 days, 20 rooms | 552 | 100 | 30s | feasible, 0 hard, load 5–6, 72 teachers with one turn and 28 with none |
| 12 days, 30 rooms | ~1630 | 150 | 60s | feasible, 0 hard, load 10–11, one full round of turns then 42 into the second |

Legality and fairness are reached quickly; extra time buys preference quality
(room repetition, pairs). At the largest size most remaining soft penalties are
arithmetically unavoidable: with 11 duties over 12 exam days, working more than
three days in a row cannot be dodged, so that preference is always "violated".

**Staffing is limited per moment, not per operation.** A teacher holds one duty
at a time, so an hour needing 47 people cannot run with a pool of 45 no matter
how long the solver runs. The moment, not the slot: two papers can start
together and end at different hours, which makes them two slots running at
once — counted apart they read as 12 and 12 against a pool of 45 and pass,
while the centre has to put 24 people in rooms at 15:00.

A second way a schedule can fail to exist is that nobody may take a given duty
— usually a permanence whose subject has no specialist present, because the
specialists are absent that day or the centre examines a subject it does not
staff. Headcount never finds it: the hour has plenty of people, just nobody
allowed in that chair.

`StaffingCheck` looks for both before solving, and the application refuses the
run naming the hour and the requirement, or the subject with no specialist —
in French and Arabic, and at `GET /api/operations/{id}/staffing`. Left to the
solver, the first appears as a wall of hard violations and the second as a
qualification violation blaming a teacher who did nothing wrong.

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

## Settings a centre can change

**Opérations → Paramètres**, or `GET /operations/{id}/settings`. They fall into
three groups that are deliberately kept apart:

**Staffing rules** — surveillants per room (default 2, never below the official
minimum of 2, and settable per room when one hall is bigger), and the reserve
requirement, either a percentage of the surveillance staff or a fixed count. A
reserve count stated in the input file still wins for that slot.

**Scheduling policy** — the maximum number of consecutive working days, with
its own strength: a preference by default, because at high density a run of
four days is unavoidable and a hard rule would make an ordinary centre
unschedulable; an académie that truly imposes a maximum can promote it. Also
the minimum rest between two duties of the same day, zero unless a centre asks
for it, and whether surveilling one's own subject is forbidden outright or
merely discouraged.

**Solver settings** — how long to search. Raising it is not a change of
procedure, which is why it does not sit with the rules.
