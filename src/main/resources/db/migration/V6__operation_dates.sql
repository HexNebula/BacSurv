-- A session knows the days it runs over, so its planning grid can be shown
-- before a single épreuve has been entered. Until now the days could only be
-- read back from the slots, which is useless for a session being created.
-- Null for sessions imported before this: their days are read from the slots.
alter table operation add column starts_on date;
alter table operation add column ends_on date;
