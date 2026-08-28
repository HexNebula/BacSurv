-- A session had no state, so "it has a finished solve" had to stand in for
-- "it happened". Those are not the same thing, and the difference was paid for
-- twice over.
--
-- Cumulative fairness reads the newest finished job of every *other* session of
-- the centre (AssignmentRepository#priorWorkloadOfCenter). A trial solve of the
-- June nationale therefore counted as history when the régionale was solved in
-- March: turns were repaid to people who had never served. Deletion had the
-- mirror fault — removing a session took real turns out of the queue with no
-- way to tell whether they had been earned.
--
-- The state settles both. A session is a draft until an administrator says the
-- répartition is the one that goes out; only then does it count for anything,
-- and only then does it stop being deletable.
alter table operation add column state varchar(20) default 'DRAFT' not null;

-- Sessions already solved kept their history under the old rule, and their
-- teachers are owed the turns it records. Read them as settled rather than
-- silently emptying the queue on the day this migration runs.
update operation set state = 'SETTLED'
where id in (select operation_id from solve_job
             where operation_id is not null and status = 'DONE');
