-- Which level a filière belongs to.
--
-- 1BAC and 2BAC run different filières, and a name can belong to both — a
-- centre has Sciences expérimentales in each, sitting different papers with
-- different candidates. Without the level, planning a 2BAC session offers the
-- 1BAC filières as well, and the administrator picks the wrong one.
--
-- The column is filled from how each filière has actually been used rather
-- than left empty to be typed in: a nullable level would read as "both" on
-- every existing entry, every picker would go on showing everything, and the
-- filter would be decoration. What has never been used at all is called BAC2,
-- the common case, and is corrected on screen in one click.
alter table center_stream add column level varchar(10);

update center_stream cs
set level = 'BAC1'
where exists (select 1
              from operation_stream os
                       join operation o on os.operation_id = o.id
              where os.name = cs.name
                and o.center_id = cs.center_id
                and o.type = 'REGIONAL_1BAC');

update center_stream cs
set level = 'BAC2'
where cs.level is null
  and exists (select 1
              from operation_stream os
                       join operation o on os.operation_id = o.id
              where os.name = cs.name
                and o.center_id = cs.center_id
                and o.type <> 'REGIONAL_1BAC');

update center_stream set level = 'BAC2' where level is null;

alter table center_stream alter column level set not null;

-- One name may now exist once per level, and only once per level.
alter table center_stream drop constraint uq_center_stream;
alter table center_stream add constraint uq_center_stream unique (center_id, name, level);
