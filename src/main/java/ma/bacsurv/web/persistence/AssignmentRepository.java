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
     * Duties already given to each teacher of a center by earlier operations.
     * Only the newest finished job of each other operation counts, so
     * re-solving an operation never inflates its own history.
     */
    @Query("""
            select a.teacher.id, a.role, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.teacher.center.id = :centerId
              and a.job.operation.id <> :excludedOperationId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                  group by j.operation.id)
            group by a.teacher.id, a.role
            """)
    List<Object[]> priorWorkloadOfCenter(Long centerId, Long excludedOperationId);

    /**
     * Privilege duties (réserve, permanence) handed out by the center's most
     * recent finished operation — the previous session, not the whole year.
     *
     * <p>Rattrapage cannot be sized in advance, so a year-long plan would be
     * planning against an unknown. Only the last session is read, and only to
     * work out who is still owed a turn; see OperationAssembler#privilegeCarry.
     */
    @Query("""
            select a.teacher.id, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.teacher.center.id = :centerId
              and a.role <> ma.bacsurv.domain.DutyRole.SURVEILLANCE
              and a.job.id = (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                    and j.operation.center.id = :centerId
                    and j.operation.id <> :excludedOperationId)
            group by a.teacher.id
            """)
    List<Object[]> privilegesOfPreviousSession(Long centerId, Long excludedOperationId);
}
