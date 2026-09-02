# BacSurv

Surveillance scheduling for Moroccan baccalaureate exams. Given a centre's
rooms, its teacher pool and the exam timetable, it produces a legal, fair duty
schedule — surveillance, réserve, permanence — with a constraint solver, and
keeps the load balanced across every session of the school year.

Java 21, Spring Boot and Timefold on the server; React and Tailwind for the
interface. The input file format, for the API and the command line, is
described in [samples/INPUT-FORMAT.md](samples/INPUT-FORMAT.md).

## Running it

In development, two processes:

```bash
mvn spring-boot:run                          # API on :8080
cd frontend && npm install && npm run dev     # interface on :5173
```

Vite serves the pages and forwards `/api` to Spring. Data is stored in a local
H2 file under `./data`.

To ship, one:

```bash
mvn package
```

builds the React application into the jar, so everything is served from one
port with no cross-origin setup. `-DskipFrontend=true` builds the server alone,
without needing npm.

The interface exists in the two administrative languages of Morocco, French
(default) and Arabic, and Arabic pages are laid out right to left. Interface
text lives in `frontend/src/i18n`; everything the server has to say — refusals,
reasons, validation — lives in `src/main/resources/messages*.properties` as
codes, rendered by the front end. Add both languages when adding either.

## What the application does

A centre holds rooms, a subject and filière catalogue, and a teacher pool per
school year. Inside a year it runs sessions — normale, rattrapage, candidats
libres — and each session has its own timetable: which filières sit, in which
rooms, and which épreuve at which hour.

Nothing is imported from a spreadsheet except the teachers. The timetable is
typed, because no centre has a machine-readable one to give.

A readiness list says what is still missing before a session can be
distributed, in the order it has to be done, and every step links to the screen
that fixes it. Then the session is solved, read per slot or per teacher,
adjusted by hand where the administrator disagrees, and finally **arrêtée** —
which turns its duties into history, the only thing later sessions count.

## HTTP API

The interface is a skin over this API; any other front end can use it.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` `POST` | `/api/centers`, `/{id}` | centres, their rooms, sessions and export |
| `GET` `POST` | `/api/centers/{id}/subjects`, `/streams` | the catalogue a timetable is typed from |
| `GET` `POST` | `/api/centers/{id}/teachers` | pool, absences, spreadsheet import |
| `GET` `POST` | `/api/centers/{id}/years/{yearId}` | membership of a school year, and its archive |
| `GET` `POST` | `/api/sessions/{id}/timetable` | filières, rooms, épreuves; `/copy` from another session |
| `GET` | `/api/sessions/{id}/readiness` | what is left to do, step by step |
| `POST` | `/api/sessions/{id}/settle` / `/reopen` | arrêter the distribution, or take it back |
| `GET` `POST` | `/api/operations/{id}/settings` | staffing rules, scheduling policy, solver time |
| `GET` | `/api/operations/{id}/staffing` | the pre-solve staffing check |
| `POST` | `/api/operations/{id}/solve?seconds=30` | queue a solve, returns a job |
| `GET` | `/api/jobs`, `/api/jobs/{id}` | job status |
| `GET` | `/api/jobs/{id}/schedule` | the solved schedule (409 while running) |
| `POST` | `/api/jobs/{id}/assignments/{dutyId}` | change one duty by hand, `/review` first, `/pin` to protect |

Solving runs on a background pool, so a request never waits for the solver.

## Layout

```
domain/       exams, slots, rooms, teachers, duties — no framework, no I/O
rules/        eligibility and subject-conflict configuration
application/  duty generation, validation, greedy baseline
solver/       Timefold adapter: constraints + solve, swappable engine
io/           input parsing and schedule serialisation
cli/          command line entry point
web/          Spring Boot: persistence, service, REST API
demo/         runnable demonstrations with generated data
frontend/     React interface, built into the jar at package time
```

The domain, rules and solver packages know nothing about the web or the
database; the web layer calls services and never reaches into the solver.

## Surveillance is the work, réserve and permanence are turns

Standing in a room for a whole épreuve is not the same day as being on
standby, even though all three count as one duty and are paid the same. So
BacSurv balances three things: surveillance is spread evenly, and réserve and
permanence each have **their own queue**. A teacher who has had one waits until
every colleague has had one too; then the cycle opens again.

Two queues rather than one, and the difference is not academic. Permanence
needs a specialist of the subject, so in a centre with four teachers of
الإجتماعيات those four take every permanence of that subject whether they like
it or not. Charged to a single counter, the turn they were forced to take was
the turn that would have given them réserve, and they finished the year with
three permanences and no réserve while somebody else had the reverse. Counted
apart, the scarce subject divides its permanences evenly and its specialists
still take their place in the réserve queue.

**The queues survive between sessions.** Each session is solved on its own —
rattrapage cannot be sized in advance, so nothing is planned ahead — but it
starts where the earlier sessions left off: a teacher carries, per role, how
many turns they have had beyond the colleague who has had the fewest, almost
always 0 or 1. A completed round lifts everyone together and the number falls
back to zero, so it cannot accumulate; session sizes never enter, since what is
counted is turns per teacher.

All past sessions count, not just the last, and only **settled** ones. A
session repays the people it owes only as far as its turns reach — 29 waiting
and 18 turns to give leaves 11 still waiting — and reading the newest session
alone could not tell those 11 from the ones already served. The position is
recomputed from stored assignments rather than kept as a running counter, which
is what stops it drifting.

Importing or creating a session stores the centre, its rooms and its pool, so
none of this needs a `prior` block maintained by hand. Teachers are matched by
matricule within their centre.

## Two sessions running the same hours

A centre that examines its own pupils and the candidats libres on the same
three mornings has two sessions, one building and one staff. Each is
distributed alone, neither can see the other, and both come out perfect — then
the administrator prints two lists that put the same teacher in two rooms at
eight o'clock.

Every other check asks whether one session is coherent. `SessionConflictService`
asks whether it is coherent with the ones already settled, which nobody was
asking. Rooms are held by a filière for the whole session, so a room taken by a
concurrent settled session is refused while the timetable is being typed. A
teacher is held only for the hours of a duty, so that collision exists only
once both sessions are distributed, and only where the hours genuinely overlap
— two sessions sharing a day but not an hour share nothing.

The solver is told before it starts: a settled neighbour's assignments enter as
unavailability, so the conflict is avoided rather than reported. Settling
refuses on what is left, whichever session goes first, and the readiness list
says so beforehand so the refusal never arrives as a surprise at the final
click. Drafts are ignored throughout — a session still being typed may never go
out, and stopping today's work because of a trial would be absurd.

## Staffing is limited per moment, not per session

A teacher holds one duty at a time, so an hour needing 47 people cannot run
with a pool of 45 no matter how long the solver runs. The moment, not the slot:
two papers can start together and end at different hours, which makes them two
slots running at once — counted apart they read as 12 and 12 against a pool of
45 and pass, while the centre has to put 24 people in rooms at 15:00.

A second way a schedule can fail to exist is that nobody may take a given duty
— usually a permanence whose subject has no specialist present, because the
specialists are absent that day or the centre examines a subject it does not
staff. Headcount never finds it: the hour has plenty of people, just nobody
allowed in that chair.

`StaffingCheck` looks for both before solving, and the application refuses the
run naming the hour and the requirement, or the subject with no specialist.
Left to the solver, the first appears as a wall of hard violations and the
second as a qualification violation blaming a teacher who did nothing wrong.

## Changing a schedule by hand

The solver proposes; the administrator decides. Any line of a schedule can be
reassigned, and BacSurv answers before saving anything with what the change
would break — the teacher is already on duty in that slot, is absent that day,
is not a specialist of the subject they would cover as permanence, or would be
surveilling their own subject.

A legal change also reports its cost: how many duties the new holder ends up
with, and which preferences (repeated room, repeated pair, consecutive days)
get better or worse.

An assignment can be **pinned**, and a pinned duty keeps its teacher through
the next solve while the rest of the schedule is rebuilt around it — the pin
itself survives too.

A change that breaks a rule is refused unless it is explicitly forced. Forced
or not, the schedule keeps telling the truth: the validation summary counts the
violation.

## Importing the teacher pool from a spreadsheet

The file is what an administration exports: comma, semicolon or tab separated,
UTF-8, headers in French, Arabic or English (`matricule` / `رقم التأجير` /
`N° Matricule` all match, accents and spacing ignored). Only matricule, name
and subject are required; establishment and gender are optional. See
`samples/teachers-sample.csv`.

Nothing is written until it is confirmed: the import first shows which teachers
would be added, which would change and how, which are already correct, and
which rows could not be read — with the line number and the reason. A bad row
is skipped, never the whole file, and re-importing the same file changes
nothing.

## Running from the command line

```bash
mvn -DskipTests -DskipFrontend=true package
mvn dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" ma.bacsurv.cli.SolveMain \
     samples/operation-sample.json --seconds 30
```

Exit codes: `0` feasible, `1` infeasible, `2` bad input.

## Behaviour at the size of a real centre

`ma.bacsurv.demo.ScaleDemoMain [seconds] [teachers] [days] [rooms]` generates a
centre of a given size and reports what the solver achieved:

| Centre | Duties | Teachers | Time | Result |
| --- | --- | --- | --- | --- |
| 6 days, 20 rooms | 552 | 100 | 30s | feasible, 0 hard, surveillance load 5–6 |
| 12 days, 30 rooms | ~1630 | 150 | 60s | feasible, 0 hard, surveillance load 10–11 |

Legality and fairness are reached quickly; extra time buys preference quality
(room repetition, pairs). At the largest size most remaining soft penalties are
arithmetically unavoidable: with 11 duties over 12 exam days, working more than
three days in a row cannot be dodged, so that preference is always "violated".

## Settings a centre can change

They fall into three groups that are deliberately kept apart.

**Staffing rules** — surveillants per room (default 2, never below the official
minimum of 2, and settable per room when one hall is bigger), and the réserve
requirement, either a percentage of the surveillance staff or a fixed count. A
réserve count stated in the input file still wins for that slot.

**Scheduling policy** — the maximum number of consecutive working days, with
its own strength: a preference by default, because at high density a run of
four days is unavoidable and a hard rule would make an ordinary centre
unschedulable; an académie that truly imposes a maximum can promote it. Also
the minimum rest between two duties of the same day, zero unless a centre asks
for it, and whether surveilling one's own subject is forbidden outright or
merely discouraged.

**Solver settings** — how long to search. Raising it is not a change of
procedure, which is why it does not sit with the rules.

## Database

Development uses H2 in PostgreSQL mode; the schema is owned by Flyway
migrations (`src/main/resources/db/migration`), never by Hibernate. Moving to a
real PostgreSQL server is a datasource URL change plus the driver — no code or
schema rewrite.

## Tests

```bash
mvn test
```

Covers duty generation, validation rules, input parsing, cross-session
conflicts, a full input-file-to-feasible-schedule run, and the web flow
(create → solve → poll → schedule → settle) against the real solver.
