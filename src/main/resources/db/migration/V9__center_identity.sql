-- A centre's official identity in the Moroccan system.
--
-- None of it reaches the solver. These are what an administration prints at
-- the head of a convocation or a room list, and what identifies the
-- establishment to the ministry: the regional academy, the provincial
-- directorate, the commune, and the ministerial reference of the centre.
--
-- Every column is nullable: a centre set up before these existed goes on
-- working, and an administrator fills them in when the paperwork needs them
-- rather than before the first room can be added.
alter table center add column academy varchar(200);
alter table center add column directorate varchar(200);
alter table center add column commune varchar(200);
alter table center add column ministerial_reference varchar(120);
