# BacSurv input file format

One JSON file describes one exam operation for one center. See
`operation-sample.json` for a complete example.

Run:

```
java -cp <classpath> ma.bacsurv.cli.SolveMain <input.json> [-o output.json] [--seconds N]
```

Exit codes: `0` feasible schedule written, `1` infeasible (hard violations or
unfilled duties), `2` bad input file or usage. Default output path:
`<input>.schedule.json` next to the input file.

## Sections

### `operation`
- `id` — free-form label, echoed into the output.
- `type` — `REGIONAL_1BAC` | `NATIONAL_2BAC` | `NATIONAL_2BAC_RATTRAPAGE`.

### `defaults` (optional)
Fallbacks applied where an exam/slot omits its own value:
- `surveillantsPerRoom` (default 2 — official minimum, values below 2 are rejected)
- `permanencePerExam` (default 1)
- `reservePercentage` (default 0.10) — used to *suggest* a slot's reserve count
  when `reserveCount` is absent: `ceil(percentage × surveillance duties in slot)`.

### `rooms`
`{ "id", "label" }`. Ids must be unique; exams reference rooms by id.

### `slots`
- `id`, `date` (`yyyy-MM-dd`), `start`/`end` (`HH:mm`)
- `exams`: list of `{ id, subject, stream, rooms: [roomId...], surveillantsPerRoom?, permanenceCount? }`.
  One slot may hold different subjects for different streams.
- `reserveCount` (optional) — configured truth; overrides the percentage suggestion.

`ordinalInDay` is derived automatically from start-time order within each date.

### `teachers`
- `id`, `name?`, `subject` (must match exam subject strings exactly),
  `establishment?`, `gender?` (`MALE`/`FEMALE`, only used by the soft
  mixed-pair preference)
- `unavailable?`: list of `{ date, start?, end? }` — both times or neither
  (neither = whole day)
- `prior?`: duties carried from earlier operations of the year, e.g.
  `{ "SURVEILLANCE": 3, "RESERVE": 1 }` — feeds cumulative fairness.

Default qualifications are applied: surveillance + reserve + permanence for
the teacher's own subject.

## Validation

The parser is strict: unknown JSON fields, duplicate ids, dangling room
references, malformed dates/times and negative counts are rejected with a
message naming the offending element (exit code 2).

The solved schedule is checked by the independent `ScheduleValidator`;
the output JSON reports `feasible`, violation counts and per-teacher workload.
