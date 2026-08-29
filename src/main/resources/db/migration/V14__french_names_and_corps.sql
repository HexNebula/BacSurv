-- A second name for what a teacher, a subject and a filière are called, and
-- the corps a teacher belongs to.
--
-- The lists a centre works from arrive in Arabic: the ministry's own
-- « لائحة المراقبين » names its columns الاسم الكامل, رقم التأجير, مادة
-- التخصص. So `name` already holds Arabic everywhere, and it is French that
-- was missing — not the other way round.
--
-- That matters beyond the column order. `name` is not only a label: teachers
-- and épreuves store the subject and the filière as a string, and the solver
-- matches them by exact equality. The name that is compared has to stay one
-- name, so `name` keeps that job and `name_fr` is display and nothing else.
-- Nothing reads it, nothing joins on it, and a row without one is a row that
-- simply prints in Arabic.
--
-- Every column is nullable, as in V9: a centre set up before these existed
-- goes on working, and the French name is filled in when a French document
-- needs it rather than before the first teacher can be added.
alter table teacher add column name_fr varchar(200);

-- السلك — the corps a teacher belongs to: ثانوي تأهيلي, ثانوي إعدادي, ابتدائي.
--
-- Printed, never compared. A centre short of staff borrows from other
-- establishments, and the official list has to say which corps each borrowed
-- person came from. `establishment` already records the school; this records
-- its level, because the same corps in a different school is a real case and
-- neither field stands in for the other.
--
-- It reaches no rule and no constraint. Who may hold a duty is decided by the
-- pool the administrator assembles, and by the time a name is in the pool that
-- decision has been made.
alter table teacher add column corps varchar(60);

alter table center_subject add column name_fr varchar(120);
alter table center_stream add column name_fr varchar(120);
