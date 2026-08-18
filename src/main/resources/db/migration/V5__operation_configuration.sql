-- Configuration a centre sets for itself, in three separate groups:
-- staffing rules, scheduling policy, and technical solver settings. They are
-- kept in one row per operation but stay conceptually distinct, because an
-- administrator raising the solve time is not changing exam procedure.

create table operation_config (
    operation_id                 bigint primary key references operation (id) on delete cascade,

    -- staffing
    default_surveillants_per_room int         not null default 2,
    reserve_mode                  varchar(20) not null default 'PERCENTAGE',
    reserve_percentage            double precision not null default 0.10,
    reserve_fixed_count           int         not null default 0,

    -- scheduling policy
    max_consecutive_days          int         not null default 3,
    consecutive_days_strength     varchar(10) not null default 'SOFT',
    min_gap_minutes               int         not null default 0,
    own_subject_strength          varchar(10) not null default 'HARD',
    forbid_own_subject_reserve    boolean     not null default false,

    -- solver
    solve_seconds                 int         not null default 30
);

-- a room may need more surveillants than the operation default
alter table room add column surveillants_override int;

-- a slot whose reserve count came from the file keeps it; the others follow
-- whatever the operation's reserve rule currently says
alter table exam_slot add column reserve_explicit boolean not null default false;
