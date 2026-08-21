# BacSurv — Domain Model (v0.3)

Constraint-based surveillance scheduling for Moroccan baccalaureate exams.
Stack-agnostic. Entities → decision variables → constraints.

**Solve scope**: one exam center per run. Teacher pool = list given to the
center — mostly own staff, possibly plus externally assigned teachers from
the directorate. The solver does not decide who is in the pool, only how
the pool is assigned to duties.

**v0.3 key change**: one time slot can hold different subjects for
different streams (confirmed for Moroccan regional/national structure).
`Session.subject` was the wrong abstraction — replaced by
**ExamSlot** (time container) + **Exam** (subject × stream held in it).

## 1. Entities

### Teacher
| Field | Notes |
|---|---|
| id, name | |
| matricule | numéro de matricule / رقم التأجير — the civil servant's staff number. Required and unique; the real identity on official lists and convocations |
| subject | taught subject |
| establishment | home school (informational — no avoidance rule) |
| gender | optional, only for mixed-pair soft preference |
| availability | set of unavailable (date, time range) — "can the teacher work?" |
| qualifications | what the teacher may do — "is the teacher allowed?" |

### TeacherQualification
Availability ≠ eligibility. Availability = time. Qualification = permission.
```
TeacherQualification
├── teacher
├── dutyRole            SURVEILLANCE | RESERVE | PERMANENCE
└── subject?            only for PERMANENCE (specialist subject)
```
Default: every teacher qualified for SURVEILLANCE and RESERVE;
PERMANENCE only for their own subject.

Solver eligibility function:
```
eligible(teacher, duty) =
    available(teacher, duty.slot)
    AND qualified(teacher, duty.role, duty.exam?.subject)
    AND no hard conflict (subject-conflict rule, overlap, ...)
```

### ExamCenter
id, name, rooms.

### Room
id, label, center.

### ExamOperation
One independent scheduling run.
- type: `REGIONAL_1BAC` | `NATIONAL_2BAC` | `NATIONAL_2BAC_RATTRAPAGE`
  (no regional rattrapage)
- dateRange
- slots

### ExamSlot (time container — NOT subject-bearing)
```
ExamSlot
├── id
├── operation
├── date
├── startTime, endTime
├── ordinalInDay
├── exams[]                 the exams held during this interval
└── reserveRequirement      reserve is slot-scoped (see below)
```
`halfDay = AM | PM` derived from startTime, used only by the
AM/PM-balance objective.

### Exam (one subject examination inside a slot)
```
Exam
├── id
├── slot
├── subject
├── stream                  filière (Sciences Exp., Arts, ...)
├── rooms[]                 rooms hosting THIS exam's candidates
├── surveillantsPerRoom     default 2 (official: ≥2)
└── permanenceCount         default 1 — per SUBJECT in the slot, not per exam:
                            streams sitting the same subject share one specialist
```
Example:
```
ExamSlot  Monday 2026-06-01  08:00–10:00
├── Exam A: History-Geography / Sciences Exp.  → rooms 1, 2, 3
└── Exam B: French / Arts                      → rooms 4, 5
```

### Duty scopes
Each role attaches to the level it actually belongs to:
| Role | Scope | References |
|---|---|---|
| SURVEILLANCE | a room of a specific exam | exam + room |
| PERMANENCE | a specific exam (its subject) | exam |
| RESERVE | the whole slot | slot only |

```
Duty
├── id
├── slot                    always set
├── exam?                   surveillance & permanence
├── room?                   surveillance only
├── role                    SURVEILLANCE | RESERVE | PERMANENCE
└── assignedTeacher         ← DECISION VARIABLE
```
No subject on Duty — `duty.exam.subject` holds it. Avoid duplicate truth.

Duty generation per slot:
```
for each exam in slot:
    surveillance duties = Σ over exam.rooms of surveillantsPerRoom
for each DISTINCT SUBJECT examined in slot:
    permanence duties   = permanenceCount   (1 specialist per subject)
reserve duties = slot.reserveRequirement    (slot-wide standby pool)
```
Permanence is per subject, never per exam or per stream: the scientific
streams normally sit the same paper at the same hour and one specialist
covers all of them, while a literature stream sitting another subject needs
its own. Counting permanence per exam overstates the staff a slot needs.

### ReservePolicy (suggestion generator, NOT the requirement itself)
Official wording: reserve list *up to* 10% of surveillance personnel —
a ceiling, not a per-slot formula.
```
ReservePolicy = FIXED_COUNT | PERCENTAGE_OF_SURVEILLANCE | MANUAL

PERCENTAGE_OF_SURVEILLANCE:
    percentage = 10%          of ALL surveillance duties in the slot
    rounding   = CEIL         (across every exam in that slot)
```
Policy *suggests* `reserveRequirement`; the configured/edited value on the
slot is the truth the solver uses.

## 2. Decision model

Solver assigns exactly one teacher to each Duty. ILP view:
```
x[t, d] ∈ {0,1}   teacher t fills duty d
Σ_t x[t, d] = 1                 every duty filled
Σ_d∈slot(s) x[t, d] ≤ 1         teacher at most one duty per slot
```

## 3. Constraints

### Hard
| # | Constraint |
|---|---|
| H1 | Every duty has exactly one teacher |
| H2 | No teacher has two duties in the same slot (covers permanence-can't-sit-exam) |
| H3 | Teacher must be available for the slot |
| H4 | Teacher must be qualified for the duty (permanence → specialist of `duty.exam.subject`) |
| H5 | Subject-conflict rule (see below) when configured HARD |
| H6 | Optional absolute cap: `maxDutiesPerTeacher` — a real administrative limit if one exists. NOT a fairness device; fairness stays soft |

### Subject-conflict rule (configurable strength, per-EXAM scope)
Scope is the duty's exam, NOT the slot. A Math teacher is only barred
from rooms of the *Math* exam — they may surveil the French exam running
in the same slot:
```
SubjectConflictConstraint
├── enabled  = true
├── strength = HARD | SOFT(penalty)      default HARD (2026 text unverified)
└── effect:
      SURVEILLANCE where teacher.subject == duty.exam.subject → forbidden
      PERMANENCE   → requires teacher.subject == duty.exam.subject (via H4)
      RESERVE      → slot-scoped, no exam ⇒ no conflict (configurable if
                     an academy forbids own-subject reserve)
```

### Soft (weighted penalties, all configurable per center/academy)
| # | Constraint | Default weight |
|---|---|---|
| S1 | Balance total workload within operation | high |
| S2 | Balance surveillance load within operation | high |
| S3+S4 | Privilege queue — réserve and permanence share one counter | high |
| S5 | Carry the unfinished round into the next session | high |
| S6 | AvoidRepeatedRoomAssignment — teacher not in same room twice | medium |
| S7 | AvoidRepeatedPair — same duo not paired twice | medium |
| S8 | Max consecutive days (e.g. 3) | medium |
| S9 | Balance AM/PM duties per teacher | low |
| S10 | Mixed-gender pair per room | low, optional |
| S11 | Don't give same teacher reserve repeatedly (pattern, e.g. consecutive) | low |

Naming: domain speaks Moroccan-exam language (`AvoidRepeatedRoomAssignment`),
solver adapter may speak solver language (MTR etc.).

### Pair definition (S7)
Unordered: `{A, B}` — `(A,B)` ≡ `(B,A)`. Pairs are formed within one
room of one exam. Rooms with 3+ surveillants use the **pairwise**
interpretation: `A+B+C` → `{A,B}, {A,C}, {B,C}`; any repeated pair is
penalized. (Full-team interpretation rejected — too weak.)

## 4. Workload accounting

All roles count equal — same pay, one load unit each.
```
Workload
├── surveillanceCount
├── reserveCount
├── permanenceCount
└── total = sum
```
Balancing objectives are independent and independently weighted (S1–S5):
total-only balance is insufficient — 5 reserves ≠ 5 surveillances in work
intensity for the same pay.

### Surveillance is the work, the other two are turns
Surveillance means standing in a room for the whole épreuve. Réserve and
permanence are lighter, and teachers experience them as a turn they are
glad to get. Equal totals can therefore still be unfair:

| | surveillance | réserve | permanence | total |
|---|---|---|---|---|
| A | 6 | 0 | 0 | 6 |
| B | 0 | 2 | 4 | 6 |

So two objectives, not one:
- **Surveillance** is spread evenly — everyone carries their share of the work.
- **Réserve and permanence share a single queue.** Different pools —
  permanence only from the subject's specialists — but one counter: whoever
  has had either waits until every colleague has had a turn, then the cycle
  opens again.

Balancing the counts is what produces the cycle: a second turn while a
colleague sits at zero costs more than levelling up.

**Teachers at zero must be counted.** A balance computed only over the
people already holding a role sees a group of one as perfectly balanced, so
six permanences to a single specialist scores exactly like two each to
three of them. The zero rows are what make the objective mean anything.

Scarcity can still force a repeat — four philosophie permanences and two
specialists leaves no choice — so this is a strong preference, never a hard
rule.

### Cumulative (cross-operation) tracking
Operations are solved independently (June ≠ July roster), so history is
carried as an offset:
```
Teacher
├── workloadByOperation { REGIONAL_1BAC, NATIONAL_2BAC, RATTRAPAGE }
└── cumulativeWorkload (derived, per role)

solve(operation N): balance (base[t] + new[t]), base = past operations
```

**The privilege queue is scoped to the session, not the year.** Rattrapage
cannot be sized in advance — its roster and its length are unknown until it
happens — so planning privileges across a whole year is planning against an
unknown. What travels between sessions is only the unfinished tail of the
last round:
```
carry(t) = privileges(t, previous session) − min over the pool
```
Almost always 0 or 1. A completed round cancels itself back to zero, so the
number cannot accumulate, and sessions of wildly different sizes (sixty
privileges in juin, eight in rattrapage) cannot distort it. A teacher absent
from the previous session counts as zero and goes to the front, which is
correct — they have not had their turn.

It is derived from the stored assignments of the previous session, never
maintained as a counter: a ledger that is incremented and decremented drifts
the first time a run is interrupted.
A teacher unavailable half the exam must not make the schedule infeasible:
that is why fairness is soft and only `maxDutiesPerTeacher` (if a real
limit exists) is hard.

## 5. Execution tracking (post-v0.1, design for it now)
The generated schedule is a plan; reality diverges (reserve replaces an
absent surveillant). Not a solver concern — execution tracking:
```
AssignmentStatus = PLANNED | CONFIRMED | REPLACED | CANCELLED | COMPLETED
```
Status lives on the assignment, never inside the solver model.

## 6. Architecture boundaries
The solver is an engine, not the domain:
```
bacsurv-domain        Teacher, ExamOperation, ExamSlot, Exam, Room, Duty
bacsurv-rules         eligibility, hard/soft constraints, weights config
bacsurv-solver        solver-specific model, score, solution mapping
bacsurv-application   generate schedule, validate schedule, manual edits
```
Swapping/comparing engines (e.g. Timefold vs OR-Tools) must not touch
domain or rules.

## 7. First coding milestone
Stop researching; build, in order:
1. Domain entities
2. Duty generator (operation → slots → exams → staffing → duties)
3. Manual assignment
4. Schedule validator (checks hard constraints + reports soft violations)

Solver comes after the validator exists — the validator defines
correctness before any engine optimizes for it.

## 8. Open questions
- [ ] Get 2026 official procedure PDFs (center-chief guide + surveillant guide)
- [ ] Own-subject RESERVE: any academy forbidding it? (default allowed)
- [ ] Does a real `maxDutiesPerTeacher` administrative limit exist?
- [ ] Stream on Teacher needed? (e.g. teachers attached to streams for
      eligibility — assumed no for now)
