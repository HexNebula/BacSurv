-- When the things a distribution was computed from last changed.
--
-- A solved distribution looked exactly the same whether it was produced a
-- minute ago or before somebody added four rooms, recorded an absence and
-- raised the surveillants per room. The application had no way to tell: a job
-- knows when it finished, and nothing knew when the inputs moved.
--
-- Two columns rather than one, because the two scopes differ. The pool, the
-- rooms and the catalogue belong to the centre and invalidate every session of
-- it; the timetable and the rules belong to one session. A distribution is out
-- of date when either has moved since the job finished.

alter table center add column changed_at timestamp;
alter table operation add column changed_at timestamp;

-- existing rows: taken as changed now, so a distribution solved before this
-- migration is reported as out of date rather than silently trusted
update center set changed_at = current_timestamp where changed_at is null;
update operation set changed_at = current_timestamp where changed_at is null;
