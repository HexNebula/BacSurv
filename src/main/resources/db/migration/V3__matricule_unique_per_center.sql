-- A matricule identifies a civil servant, but a teacher row belongs to one
-- center: the same person can serve center A one year and center B the next.
-- Making the matricule globally unique made the second import silently reuse
-- the first center's rows, leaving the new center with an empty pool.
-- Uniqueness therefore belongs to the pair (center, matricule).

alter table teacher drop constraint uq_teacher_matricule;
alter table teacher add constraint uq_teacher_matricule unique (center_id, matricule);
