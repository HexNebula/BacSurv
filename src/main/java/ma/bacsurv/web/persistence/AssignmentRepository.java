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
}
