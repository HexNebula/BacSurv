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
     * Privilege turns (réserve, permanence) each teacher has taken in the
     * center's earlier sessions — the newest finished job of each.
     *
     * <p>Every past session counts, not only the last. A session repays the
     * people it owes just as far as its turns reach: 29 teachers waiting and
     * 18 turns to give leaves 12 still waiting, and a query that reads only
     * the newest session cannot tell those 12 from the ones it already
     * settled. Turning these counts into a queue position is
     * OperationAssembler#carryFrom.
     */
    @Query("""
            select a.teacher.id, count(a)
            from AssignmentEntity a
            where a.teacher is not null
              and a.teacher.center.id = :centerId
              and a.role <> ma.bacsurv.domain.DutyRole.SURVEILLANCE
              and a.job.operation.id <> :excludedOperationId
              and a.job.id in (
                  select max(j.id) from SolveJob j
                  where j.status = ma.bacsurv.web.persistence.SolveJob$Status.DONE
                  group by j.operation.id)
            group by a.teacher.id
            """)
    List<Object[]> privilegeTurnsOfCenter(Long centerId, Long excludedOperationId);
}
