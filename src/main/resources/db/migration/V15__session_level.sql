-- The year a session examines, held rather than inferred.
--
-- It used to be read off the type: régional meant first year, anything else
-- meant second. That worked while there were three session types, and stopped
-- working the moment there were four.
--
-- Candidats libres sit the régionale and the nationale in the same year, so
-- when they fail they sit both rattrapages. Their regional rattrapage runs on
-- its own days, before the national one they take alongside the scolarisés —
-- a first-year session in the middle of a second-year season. A rule that
-- reads « rattrapage, therefore 2BAC » gets it wrong, and gets it wrong
-- quietly: the filière picker simply offers the wrong list.
--
-- The deeper reason to store it is that the derivation was written twice, once
-- in Java and once in the frontend, and the two had to be remembered together.
-- A session now states its level and both sides read it.
alter table operation add column level varchar(10);

update operation set level = case
    when type like 'REGIONAL%' then 'BAC1'
    else 'BAC2'
end;

alter table operation alter column level set not null;
