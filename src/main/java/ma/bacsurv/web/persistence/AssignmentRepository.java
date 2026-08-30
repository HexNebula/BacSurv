package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity, Long> {

    @Query("""
            select a from AssignmentEntity a
            left join fetch a.teacher
            where a.job.id = :jobId
            order by a.dutyId
            """)
    List<AssignmentEntity> findOfJob(Long jobId);

    Optional<AssignmentEntity> findByJobIdAndDutyId(Long jobId, String dutyId);

    /**
     * Has this teacher been given a duty by a session that actually went out?
     *
     * <p>Settled sessions only, and the newest finished solve of each — the
     * same reading as the fairness queries. A trial solve is not work anybody
     * did, so a row typed by mistake and swept into one must stay deletable;
     * counting it would tell an administrator that somebody has already served
     * when nobody has, and leave the wrong name in the pool for good.
     */
    @Query("""
            select count(a) from AssignmentEntity a
            where a.teacher.id = :teacherId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and j.operation.state
                        = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
                  group by j.operation.id)
            """)
    long countServedByTeacherId(Long teacherId);

    /** Every duty row pointing at a teacher, whatever session it belongs to. */
    List<AssignmentEntity> findByTeacherId(Long teacherId);

    /**
     * The pinned assignments of the newest finished job of an operation:
     * the hand-made decisions a re-solve of that operation must respect.
     */
    @Query("""
            select a from AssignmentEntity a
            join fetch a.teacher
            where a.pinned = true
              and a.teacher is not null
              and a.job.id = (
                  select max(j.id) from SolveJob j
                  where j.operation.id = :operationId
                    and j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE)
            """)
    List<AssignmentEntity> pinnedOfOperation(Long operationId);

    void deleteByJobId(Long jobId);

    /**
     * Duties already given to each teacher by the other sessions of the same
     * school year. Only the newest finished job of each counts, so re-solving a
     * session never inflates its own history.
     *
     * <p>Of the same year, and no further. The counter starts at zero every
     * September: balancing a teacher's June against work he did two years ago
     * settles an argument nobody is having, and the pool is not even the same
     * people. Within a year it is exactly the right comparison — the régionale,
     * the nationale and the rattrapage are sat by the same staff.
     *
     * <p>And only of a <em>settled</em> operation. A finished solve is not by
     * itself something that happened: a session may be tried a dozen times
     * while its timetable is still being typed, and before the state existed
     * every one of those trials was read as duties served. An administrator
     * preparing June could make March repay turns to people who had done
     * nothing.
     */
    @Query("""
            select a.teacher.id, a.role, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.job.operation.schoolYear.id = :schoolYearId
              and a.job.operation.id <> :excludedOperationId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and j.operation.state
                        = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
                  group by j.operation.id)
            group by a.teacher.id, a.role
            """)
    List<Object[]> priorWorkloadOfYear(Long schoolYearId, Long excludedOperationId);

    /**
     * Privilege turns (réserve, permanence) each teacher has taken in the
     * earlier sessions of this school year — the newest finished job of each.
     *
     * <p>Every earlier session of the year counts, not only the last. A session repays the
     * people it owes just as far as its turns reach: 29 teachers waiting and
     * 18 turns to give leaves 12 still waiting, and a query that reads only
     * the newest session cannot tell those 12 from the ones it already
     * settled. Turning these counts into a queue position is
     * OperationAssembler#carryFrom.
     *
     * <p>Counted per role, because réserve and permanence are two queues.
     * Joined, a teacher conscripted into permanence — which is required of a
     * specialist, not offered to him — read as already served and was passed
     * over for the réserve he had never had.
     *
     * <p>Settled sessions only, for the reason given on the query above: a turn
     * taken in a trial is not a turn taken.
     */
    @Query("""
            select a.teacher.id, a.role, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.job.operation.schoolYear.id = :schoolYearId
              and a.role <> ma.bacsurv.domain.DutyRole.SURVEILLANCE
              and a.job.operation.id <> :excludedOperationId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and j.operation.state
                        = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
                  group by j.operation.id)
            group by a.teacher.id, a.role
            """)
    List<Object[]> privilegeTurnsOfYear(Long schoolYearId, Long excludedOperationId);

    /**
     * Everything each teacher of a centre ever did, across every year.
     *
     * <p>Not for fairness — that is deliberately blind past September. This is
     * for the record: the backup that has to survive losing the database, and
     * the archive that answers "what did I do in 2026-2027" years later.
     */
    @Query("""
            select a.teacher.id, a.job.operation.schoolYear.label, a.role, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.teacher.center.id = :centerId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and j.operation.state
                        = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
                  group by j.operation.id)
            group by a.teacher.id, a.job.operation.schoolYear.label, a.role
            """)
    List<Object[]> lifetimeWorkOfCenter(Long centerId);

    /**
     * Who is already standing somewhere, and when, in the settled sessions of
     * a centre — one row per duty, with the moment it occupies.
     *
     * <p>The slot is joined by reference rather than held by a foreign key,
     * because an assignment names the slot the way the solver named it. The
     * pair (operation, reference) is what identifies it, and both halves are
     * needed or a session's S1 would match every other session's S1.
     *
     * <p>Only the newest finished job of each settled session, the same reading
     * as the fairness queries: earlier trials of the same session describe
     * afternoons that were never worked.
     */
    @Query("""
            select a.teacher.id, a.teacher.name, s.date, s.startTime, s.endTime,
                   o.id, o.reference
            from AssignmentEntity a
            join a.job j
            join j.operation o
            join ExamSlotEntity s on s.operation = o and s.reference = a.slotRef
            where a.teacher is not null
              and o.center.id = :centerId
              and o.id <> :excludedOperationId
              and o.state = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
              and s.date between :from and :to
              and j.id in (
                  select max(k.id) from SolveJob k
                  where k.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and k.operation.state
                        = ma.bacsurv.web.persistence.OperationEntity$State.SETTLED
                  group by k.operation.id)
            """)
    List<Object[]> settledOccupancy(Long centerId, Long excludedOperationId,
                                    java.time.LocalDate from, java.time.LocalDate to);
}
